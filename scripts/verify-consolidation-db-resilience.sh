#!/bin/sh
set -eu

: "${TOKEN:?TOKEN is required}"

TRANSACTION_URL="${TRANSACTION_URL:-http://localhost:8081}"
CONSOLIDATION_URL="${CONSOLIDATION_URL:-http://localhost:8082}"
MERCHANT_ID="${MERCHANT_ID:-DB-OUTAGE-$(date +%s)}"
BUSINESS_DATE="${BUSINESS_DATE:-2026-09-18}"
OCCURRED_AT="${OCCURRED_AT:-2026-09-18T18:00:00Z}"

restore_db_login() {
  docker compose exec -T postgres     psql -U cashflow_admin -d cashflow -v ON_ERROR_STOP=1     -c "ALTER ROLE consolidation_app LOGIN" >/dev/null 2>&1 || true
}
trap restore_db_login EXIT INT TERM

echo "[phase-db] disabling consolidation database login and terminating its sessions"
docker compose exec -T postgres   psql -U cashflow_admin -d cashflow -v ON_ERROR_STOP=1   -c "ALTER ROLE consolidation_app NOLOGIN; SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE usename='consolidation_app' AND pid <> pg_backend_pid();"   >/dev/null

echo "[phase-db] recording transaction while consolidation DB access is unavailable"
response="$(curl -fsS -X POST "$TRANSACTION_URL/v1/transactions"   -H "Authorization: Bearer $TOKEN"   -H "Content-Type: application/json"   -H "Idempotency-Key: db-outage-$MERCHANT_ID"   -d "{
    \"merchantId\":\"$MERCHANT_ID\",
    \"type\":\"CREDIT\",
    \"amount\":\"12.0000\",
    \"currency\":\"BRL\",
    \"description\":\"database outage recovery test\",
    \"occurredAt\":\"$OCCURRED_AT\"
  }")"

transaction_id="$(printf '%s' "$response" | python3 -c 'import json,sys; print(json.load(sys.stdin)["transactionId"])')"
test -n "$transaction_id"

# Keep the database unavailable beyond the 5s Hikari acquisition timeout so at least one
# consumer attempt can fail, then restore access before the bounded retry budget is exhausted.
sleep 6

echo "[phase-db] restoring consolidation database login"
restore_db_login
trap - EXIT INT TERM

echo "[phase-db] waiting for balance convergence after database recovery"
attempt=0
while [ "$attempt" -lt 40 ]; do
  body="$(curl -sS     -H "Authorization: Bearer $TOKEN"     "$CONSOLIDATION_URL/v1/merchants/$MERCHANT_ID/daily-balances/$BUSINESS_DATE?currency=BRL" || true)"
  if printf '%s' "$body" | python3 -c '
import json,sys
try:
    data=json.load(sys.stdin)
    ok=float(data.get("totalCredits","0"))==12.0 and float(data.get("balance","0"))==12.0
except Exception:
    ok=False
raise SystemExit(0 if ok else 1)
' >/dev/null 2>&1; then
    echo "[phase-db] PASS - transaction survived consolidation DB outage and projection converged"
    exit 0
  fi
  attempt=$((attempt + 1))
  sleep 1
done

echo "[phase-db] ERROR - projection did not recover after database access was restored" >&2
docker compose logs --no-color consolidation-service >&2 || true
exit 1
