#!/bin/sh
set -eu

echo "[reconciliation] comparing transaction source of truth with daily_balance"

mismatches="$(docker compose exec -T postgres   psql -U cashflow_admin -d cashflow -At -v ON_ERROR_STOP=1 <<'SQL'
WITH expected AS (
    SELECT
        merchant_id,
        business_date,
        currency,
        SUM(CASE WHEN transaction_type = 'CREDIT' THEN amount ELSE 0 END)::numeric(19,4) AS total_credits,
        SUM(CASE WHEN transaction_type = 'DEBIT' THEN amount ELSE 0 END)::numeric(19,4) AS total_debits,
        SUM(CASE WHEN transaction_type = 'CREDIT' THEN amount ELSE -amount END)::numeric(19,4) AS balance
    FROM transactions.financial_transaction
    GROUP BY merchant_id, business_date, currency
),
actual AS (
    SELECT merchant_id, business_date, currency, total_credits, total_debits, balance
    FROM consolidation.daily_balance
),
diff AS (
    SELECT
        COALESCE(e.merchant_id, a.merchant_id) AS merchant_id,
        COALESCE(e.business_date, a.business_date) AS business_date,
        COALESCE(e.currency, a.currency) AS currency,
        e.total_credits AS expected_credits,
        a.total_credits AS actual_credits,
        e.total_debits AS expected_debits,
        a.total_debits AS actual_debits,
        e.balance AS expected_balance,
        a.balance AS actual_balance
    FROM expected e
    FULL OUTER JOIN actual a
      ON a.merchant_id = e.merchant_id
     AND a.business_date = e.business_date
     AND a.currency = e.currency
    WHERE e.merchant_id IS NULL
       OR a.merchant_id IS NULL
       OR e.total_credits IS DISTINCT FROM a.total_credits
       OR e.total_debits IS DISTINCT FROM a.total_debits
       OR e.balance IS DISTINCT FROM a.balance
)
SELECT COUNT(*) FROM diff;
SQL
)"

if [ "$mismatches" != "0" ]; then
  echo "[reconciliation] ERROR - found $mismatches divergent merchant/date/currency positions" >&2
  docker compose exec -T postgres     psql -U cashflow_admin -d cashflow -v ON_ERROR_STOP=1 <<'SQL' >&2
WITH expected AS (
    SELECT merchant_id, business_date, currency,
           SUM(CASE WHEN transaction_type = 'CREDIT' THEN amount ELSE 0 END)::numeric(19,4) AS total_credits,
           SUM(CASE WHEN transaction_type = 'DEBIT' THEN amount ELSE 0 END)::numeric(19,4) AS total_debits,
           SUM(CASE WHEN transaction_type = 'CREDIT' THEN amount ELSE -amount END)::numeric(19,4) AS balance
    FROM transactions.financial_transaction
    GROUP BY merchant_id, business_date, currency
)
SELECT COALESCE(e.merchant_id, a.merchant_id) merchant_id,
       COALESCE(e.business_date, a.business_date) business_date,
       COALESCE(e.currency, a.currency) currency,
       e.total_credits expected_credits, a.total_credits actual_credits,
       e.total_debits expected_debits, a.total_debits actual_debits,
       e.balance expected_balance, a.balance actual_balance
FROM expected e
FULL OUTER JOIN consolidation.daily_balance a
  ON a.merchant_id=e.merchant_id
 AND a.business_date=e.business_date
 AND a.currency=e.currency
WHERE e.merchant_id IS NULL OR a.merchant_id IS NULL
   OR e.total_credits IS DISTINCT FROM a.total_credits
   OR e.total_debits IS DISTINCT FROM a.total_debits
   OR e.balance IS DISTINCT FROM a.balance
ORDER BY 1,2,3;
SQL
  exit 1
fi

echo "[reconciliation] PASS - source transactions and daily_balance are consistent"
