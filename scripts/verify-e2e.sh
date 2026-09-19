#!/bin/sh
set -eu

TRANSACTION_URL="${TRANSACTION_URL:-http://localhost:8081}"
CONSOLIDATION_URL="${CONSOLIDATION_URL:-http://localhost:8082}"
TOKEN="${TOKEN:?Set TOKEN with transactions:write transactions:read balances:read}"

MERCHANT_ID="${MERCHANT_ID:-E2E-$(date +%s)}"
BUSINESS_DATE="${BUSINESS_DATE:-2026-09-18}"
OCCURRED_AT="${OCCURRED_AT:-2026-09-18T15:00:00Z}"

post_transaction() {
  type="$1"
  amount="$2"
  key="$3"

  curl -fsS -X POST "${TRANSACTION_URL}/v1/transactions" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H 'Content-Type: application/json' \
    -H "Idempotency-Key: ${key}" \
    -d "{
      \"merchantId\": \"${MERCHANT_ID}\",
      \"type\": \"${type}\",
      \"amount\": \"${amount}\",
      \"currency\": \"BRL\",
      \"description\": \"E2E validation\",
      \"occurredAt\": \"${OCCURRED_AT}\"
    }"
}

echo "[phase2] creating CREDIT 100.0000"
credit_json="$(post_transaction CREDIT 100.0000 "e2e-credit-${MERCHANT_ID}")"

echo "[phase2] creating DEBIT 25.0000"
debit_json="$(post_transaction DEBIT 25.0000 "e2e-debit-${MERCHANT_ID}")"

printf '%s' "$credit_json" | python3 -c '
import json,sys
d=json.load(sys.stdin)
assert d["merchantId"]
assert d["type"]=="CREDIT"
assert d["amount"]=="100.0000"
'

printf '%s' "$debit_json" | python3 -c '
import json,sys
d=json.load(sys.stdin)
assert d["merchantId"]
assert d["type"]=="DEBIT"
assert d["amount"]=="25.0000"
'

balance_url="${CONSOLIDATION_URL}/v1/merchants/${MERCHANT_ID}/daily-balances/${BUSINESS_DATE}?currency=BRL"
attempt=0
while :; do
  attempt=$((attempt + 1))
  status="$(curl -sS -o /tmp/e2e-balance.json -w '%{http_code}' \
    -H "Authorization: Bearer ${TOKEN}" "$balance_url")"

  if [ "$status" = "200" ]; then
    break
  fi

  if [ "$attempt" -ge 60 ]; then
    echo "[phase2] balance did not converge. last HTTP=${status}" >&2
    cat /tmp/e2e-balance.json >&2 || true
    exit 1
  fi

  sleep 1
done

python3 - <<'PY'
import json
from decimal import Decimal

with open("/tmp/e2e-balance.json", encoding="utf-8") as f:
    d = json.load(f)

assert Decimal(str(d["totalCredits"])) == Decimal("100.0000"), d
assert Decimal(str(d["totalDebits"])) == Decimal("25.0000"), d
assert Decimal(str(d["balance"])) == Decimal("75.0000"), d

print("[phase2] PASS")
print("merchantId=", d["merchantId"])
print("businessDate=", d["businessDate"])
print("totalCredits=", d["totalCredits"])
print("totalDebits=", d["totalDebits"])
print("balance=", d["balance"])
PY
