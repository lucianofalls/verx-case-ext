#!/bin/sh
set -eu

PROFILE="${1:-baseline}"

case "${PROFILE}" in
  baseline)
    RATE="${RATE:-50}"
    DURATION="${DURATION:-5m}"
    ;;
  peak)
    RATE="${RATE:-75}"
    DURATION="${DURATION:-2m}"
    ;;
  stress)
    RATE="${RATE:-100}"
    DURATION="${DURATION:-1m}"
    ;;
  *)
    echo "Perfil inválido: ${PROFILE}. Use baseline, peak ou stress." >&2
    exit 2
    ;;
esac

if ! command -v k6 >/dev/null 2>&1; then
  echo "k6 não encontrado no PATH." >&2
  echo "Instale o k6 ou execute o workflow GitHub Actions de performance." >&2
  exit 127
fi

if [ -z "${TOKEN:-}" ]; then
  TOKEN="$(./scripts/get-local-token.sh | python3 -c 'import json,sys; print(json.load(sys.stdin)["access_token"])')"
  export TOKEN
fi

mkdir -p build/performance
STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
RESULT="build/performance/k6-${PROFILE}-${RATE}rps-${STAMP}.json"
LOG="build/performance/k6-${PROFILE}-${RATE}rps-${STAMP}.log"

echo "Perfil: ${PROFILE}"
echo "Taxa: ${RATE} req/s"
echo "Duração: ${DURATION}"
echo "Resultado JSON: ${RESULT}"

RATE="${RATE}" DURATION="${DURATION}" TRANSACTION_URL="${TRANSACTION_URL:-http://localhost:8081}" CONSOLIDATION_URL="${CONSOLIDATION_URL:-http://localhost:8082}" TOKEN="${TOKEN}" k6 run   --summary-export="${RESULT}"   performance-tests/k6/daily-balance.js | tee "${LOG}"

echo
echo "Teste concluído."
echo "JSON: ${RESULT}"
echo "Log:  ${LOG}"
