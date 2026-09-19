#!/bin/sh
set -eu
cd "$(dirname "$0")/.."

generate_secret() {
    openssl rand -hex 24
}

umask 077

if [ ! -e .env ]; then
    (set -C; {
        printf 'POSTGRES_PASSWORD=%s\n' "$(generate_secret)"
        printf 'TRANSACTION_DB_PASSWORD=%s\n' "$(generate_secret)"
        printf 'CONSOLIDATION_DB_PASSWORD=%s\n' "$(generate_secret)"
        printf 'PGADMIN_DEFAULT_PASSWORD=%s\n' "$(generate_secret)"
    } > .env)
    echo 'Created local .env with generated credentials (ignored by Git).'
    exit 0
fi

if ! grep -q '^PGADMIN_DEFAULT_PASSWORD=' .env; then
    printf 'PGADMIN_DEFAULT_PASSWORD=%s\n' "$(generate_secret)" >> .env
    echo 'Added PGADMIN_DEFAULT_PASSWORD to existing .env.'
else
    echo '.env already exists; preserved.'
fi
