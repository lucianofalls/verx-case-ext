#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
# Honor optional published-port overrides without printing credentials.
if [ -f .env ]; then
    set -a
    . ./.env
    set +a
fi
for port in "${TRANSACTION_PORT:-8081}" "${CONSOLIDATION_PORT:-8082}"; do
    attempt=0
    until curl -fsS "http://localhost:$port/actuator/health" | grep -q '"status":"UP"'; do
        attempt=$((attempt + 1))
        if [ "$attempt" -ge 30 ]; then
            echo "Service on port $port did not become healthy" >&2
            exit 1
        fi
        sleep 2
    done
    echo "Service on port $port: UP"
done
docker compose exec -T postgres psql -U cashflow_admin -d cashflow \
    < infrastructure/local/tests/verify-database.sql
