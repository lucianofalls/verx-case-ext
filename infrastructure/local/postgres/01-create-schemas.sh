#!/bin/sh
set -eu
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
  --set=transaction_password="$TRANSACTION_DB_PASSWORD" \
  --set=consolidation_password="$CONSOLIDATION_DB_PASSWORD" <<'SQL'
CREATE ROLE transaction_app LOGIN PASSWORD :'transaction_password';
CREATE ROLE consolidation_app LOGIN PASSWORD :'consolidation_password';
REVOKE ALL ON DATABASE cashflow FROM PUBLIC;
GRANT CONNECT ON DATABASE cashflow TO transaction_app, consolidation_app;
REVOKE ALL ON SCHEMA public FROM PUBLIC;
CREATE SCHEMA transactions AUTHORIZATION transaction_app;
CREATE SCHEMA consolidation AUTHORIZATION consolidation_app;
REVOKE ALL ON SCHEMA transactions FROM PUBLIC;
REVOKE ALL ON SCHEMA consolidation FROM PUBLIC;
ALTER ROLE transaction_app IN DATABASE cashflow SET search_path = transactions;
ALTER ROLE consolidation_app IN DATABASE cashflow SET search_path = consolidation;
SQL
