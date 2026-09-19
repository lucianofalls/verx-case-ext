#!/bin/sh
set -eu

MOCK_IDP_PORT="${MOCK_IDP_PORT:-18080}"
CLIENT_ID="${CLIENT_ID:-cashflow-cli}"
CLIENT_SECRET="${CLIENT_SECRET:-cashflow-secret}"

curl -fsS --resolve "mock-idp:${MOCK_IDP_PORT}:127.0.0.1" \
  -u "${CLIENT_ID}:${CLIENT_SECRET}" -X POST "http://mock-idp:${MOCK_IDP_PORT}/default/token" \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  --data-urlencode 'grant_type=client_credentials' \
  --data-urlencode 'scope=transactions:write transactions:read balances:read'
