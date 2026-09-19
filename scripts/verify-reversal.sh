#!/bin/sh
set -eu

: "${TOKEN:?TOKEN is required}"

TRANSACTION_URL="${TRANSACTION_URL:-http://localhost:8081}"
CONSOLIDATION_URL="${CONSOLIDATION_URL:-http://localhost:8082}"
MERCHANT_ID="${MERCHANT_ID:-REVERSAL-$(date +%s)}"
BUSINESS_DATE="${BUSINESS_DATE:-2026-09-18}"
OCCURRED_AT="${OCCURRED_AT:-2026-09-18T19:00:00Z}"

echo "[reversal] creating original CREDIT 40.0000"
original="$(curl -fsS -X POST "$TRANSACTION_URL/v1/transactions"   -H "Authorization: Bearer $TOKEN"   -H "Content-Type: application/json"   -H "Idempotency-Key: original-$MERCHANT_ID"   -d "{
    \"merchantId\":\"$MERCHANT_ID\",
    \"type\":\"CREDIT\",
    \"amount\":\"40.0000\",
    \"currency\":\"BRL\",
    \"description\":\"original transaction\",
    \"occurredAt\":\"$OCCURRED_AT\"
  }")"
original_id="$(printf '%s' "$original" | python3 -c 'import json,sys; print(json.load(sys.stdin)["transactionId"])')"

echo "[reversal] creating compensating reversal"
reversal="$(curl -fsS -X POST "$TRANSACTION_URL/v1/transactions/$original_id/reversals"   -H "Authorization: Bearer $TOKEN"   -H "Content-Type: application/json"   -H "Idempotency-Key: reversal-$MERCHANT_ID"   -d "{
    \"occurredAt\":\"$OCCURRED_AT\",
    \"description\":\"reversal test\"
  }")"

printf '%s' "$reversal" | python3 -c '
import json,sys
original_id=sys.argv[1]
data=json.load(sys.stdin)
assert data["type"]=="DEBIT", data
assert data["amount"]=="40.0000", data
assert data["reversalOfTransactionId"]==original_id, data
' "$original_id"

echo "[reversal] waiting for compensating projection"
attempt=0
while [ "$attempt" -lt 30 ]; do
  body="$(curl -sS     -H "Authorization: Bearer $TOKEN"     "$CONSOLIDATION_URL/v1/merchants/$MERCHANT_ID/daily-balances/$BUSINESS_DATE?currency=BRL" || true)"
  if printf '%s' "$body" | python3 -c '
import json,sys
try:
    d=json.load(sys.stdin)
    ok=float(d["totalCredits"])==40.0 and float(d["totalDebits"])==40.0 and float(d["balance"])==0.0
except Exception:
    ok=False
raise SystemExit(0 if ok else 1)
' >/dev/null 2>&1; then
    echo "[reversal] PASS - immutable compensating transaction produced balance 0"
    exit 0
  fi
  attempt=$((attempt + 1))
  sleep 1
done

echo "[reversal] ERROR - reversal did not converge in daily balance" >&2
exit 1
