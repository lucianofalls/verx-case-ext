#!/bin/sh
set -eu

TRANSACTION_URL="${TRANSACTION_URL:-http://localhost:8081}"
CONSOLIDATION_URL="${CONSOLIDATION_URL:-http://localhost:8082}"
TOKEN="${TOKEN:?Set TOKEN with transactions:write transactions:read balances:read}"

MERCHANT_ID="${MERCHANT_ID:-RESILIENCE-$(date +%s)}"
BUSINESS_DATE="${BUSINESS_DATE:-2026-09-18}"
OCCURRED_AT="${OCCURRED_AT:-2026-09-18T16:00:00Z}"
IDEMPOTENCY_KEY="resilience-${MERCHANT_ID}"

echo "[phase3] stopping consolidation-service"
docker compose stop consolidation-service >/dev/null

echo "[phase3] recording transaction while consolidation is DOWN"
response_file="/tmp/resilience-transaction.json"
status="$(curl -sS -o "$response_file" -w '%{http_code}' -X POST "${TRANSACTION_URL}/v1/transactions" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H 'Content-Type: application/json' \
  -H "Idempotency-Key: ${IDEMPOTENCY_KEY}" \
  -d "{
    \"merchantId\": \"${MERCHANT_ID}\",
    \"type\": \"CREDIT\",
    \"amount\": \"10.0000\",
    \"currency\": \"BRL\",
    \"description\": \"Resilience validation\",
    \"occurredAt\": \"${OCCURRED_AT}\"
  }")"

if [ "$status" != "201" ] && [ "$status" != "200" ]; then
  echo "[phase3] transaction API failed while consolidation was down: HTTP $status" >&2
  cat "$response_file" >&2 || true
  docker compose start consolidation-service >/dev/null || true
  exit 1
fi

transaction_id="$(python3 -c 'import json; print(json.load(open("/tmp/resilience-transaction.json"))["transactionId"])')"

echo "[phase3] confirming transaction is readable"
curl -fsS \
  -H "Authorization: Bearer ${TOKEN}" \
  "${TRANSACTION_URL}/v1/transactions/${transaction_id}" >/dev/null

echo "[phase3] waiting for event to reach RabbitMQ backlog"
attempt=0
ready=0
while [ "$attempt" -lt 20 ]; do
  attempt=$((attempt + 1))
  ready="$(docker compose exec -T rabbitmq rabbitmqctl list_queues name messages_ready 2>/dev/null \
    | awk '$1=="financial.transactions.recorded" {print $2}' \
    | tail -n 1)"
  ready="${ready:-0}"
  if [ "$ready" -ge 1 ] 2>/dev/null; then
    break
  fi
  sleep 1
done

if [ "$ready" -lt 1 ] 2>/dev/null; then
  echo "[phase3] no queued event observed while consolidation was down" >&2
  docker compose start consolidation-service >/dev/null || true
  exit 1
fi

echo "[phase3] queued messages observed: $ready"
echo "[phase3] starting consolidation-service"
docker compose start consolidation-service >/dev/null

attempt=0
until curl -fsS "${CONSOLIDATION_URL}/actuator/health" | grep -q '"status":"UP"'; do
  attempt=$((attempt + 1))
  if [ "$attempt" -ge 60 ]; then
    echo "[phase3] consolidation-service did not recover" >&2
    exit 1
  fi
  sleep 1
done

balance_url="${CONSOLIDATION_URL}/v1/merchants/${MERCHANT_ID}/daily-balances/${BUSINESS_DATE}?currency=BRL"
attempt=0
while :; do
  attempt=$((attempt + 1))
  code="$(curl -sS -o /tmp/resilience-balance.json -w '%{http_code}' \
    -H "Authorization: Bearer ${TOKEN}" "$balance_url")"

  if [ "$code" = "200" ]; then
    break
  fi

  if [ "$attempt" -ge 60 ]; then
    echo "[phase3] balance did not converge after recovery. last HTTP=$code" >&2
    cat /tmp/resilience-balance.json >&2 || true
    exit 1
  fi
  sleep 1
done

python3 - <<'PY'
import json
from decimal import Decimal

with open("/tmp/resilience-balance.json", encoding="utf-8") as f:
    d = json.load(f)

assert Decimal(str(d["totalCredits"])) == Decimal("10.0000"), d
assert Decimal(str(d["totalDebits"])) == Decimal("0"), d
assert Decimal(str(d["balance"])) == Decimal("10.0000"), d

print("[phase3] PASS - transaction accepted during outage and balance converged after recovery")
print("merchantId=", d["merchantId"])
print("balance=", d["balance"])
PY
