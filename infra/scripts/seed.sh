#!/usr/bin/env bash
# Aplica os dados fictícios (infra/seed/*.sql, em ordem do nome) no PostgreSQL do docker-compose local.
# Pré-requisito: ambiente de pé e back-end rodado ao menos uma vez (migrações). Idempotente.
set -euo pipefail
cd "$(dirname "$0")/.."
dc() { docker compose -f docker-compose.yml "$@"; }
psql_() { dc exec -T postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1 -tA "$@"' sh "$@"; }

psql_ -c 'select 1' >/dev/null 2>&1 || { echo "PostgreSQL local não está de pé. Suba o ambiente: docker compose -f infra/docker-compose.yml up -d --wait" >&2; exit 1; }
[ "$(psql_ -c "select to_regclass('public.organization_feature') is not null")" = t ] \
  || { echo "Esquema sem migração. Rode o back-end antes: ./backend/mvnw -f backend/pom.xml spring-boot:run -Dspring-boot.run.profiles=local" >&2; exit 1; }

for f in seed/*.sql; do
  echo "Aplicando $f"
  dc exec -T postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1 --single-transaction -q' < "$f"
done
echo "Organizações fictícias: $(psql_ -c "select count(distinct organization_id) from organization_feature")"
