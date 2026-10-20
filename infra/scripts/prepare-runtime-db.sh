#!/usr/bin/env bash
# Antes da primeira migração; também funciona com volume existente. Somente desenvolvimento local.
# ponytail: o papel recebe CRUD em todas as tabelas de public, inclusive flyway_schema_history (default privileges não excluem uma tabela). Separar quando a #192 provisionar o banco de produção.
set -euo pipefail
cd "$(dirname "$0")/.."
docker compose -f docker-compose.yml exec -T \
  -e DB_RUNTIME_USER="${DB_RUNTIME_USER:-ctcore_app}" \
  -e DB_RUNTIME_PASSWORD="${DB_RUNTIME_PASSWORD:-ctcore_app_dev}" postgres sh -eu -c '
    [ "$DB_RUNTIME_USER" != "$POSTGRES_USER" ] || { echo "Runtime e migração precisam de usuários diferentes." >&2; exit 1; }
    psql -X -q -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1 \
      -v runtime_user="$DB_RUNTIME_USER" -v runtime_password="$DB_RUNTIME_PASSWORD"
  ' <<'SQL'
select format('create role %I login nosuperuser nocreatedb nocreaterole noinherit nobypassrls', :'runtime_user')
where not exists (select 1 from pg_roles where rolname = :'runtime_user') \gexec
select set_config('ctcore.runtime_role', :'runtime_user', false);
do $$
declare runtime_oid oid := (select oid from pg_roles where rolname = current_setting('ctcore.runtime_role'));
begin
    if exists (select 1 from pg_roles where oid = runtime_oid and (rolsuper or rolbypassrls or rolcreatedb or rolcreaterole))
       or exists (select 1 from pg_auth_members where member = runtime_oid)
       or exists (select 1 from pg_class where relowner = runtime_oid and relnamespace = 'public'::regnamespace)
       or exists (select 1 from pg_namespace where nspowner = runtime_oid)
       or exists (select 1 from pg_database where datdba = runtime_oid) then
        raise exception 'Runtime role must not own database objects or belong to another role';
    end if;
end $$;
select format('alter role %I login nosuperuser nocreatedb nocreaterole noinherit nobypassrls password %L',
              :'runtime_user', :'runtime_password') \gexec
select format('grant connect on database %I to %I', current_database(), :'runtime_user') \gexec
grant usage on schema public to :"runtime_user";
grant select, insert, update, delete on all tables in schema public to :"runtime_user";
select format('revoke insert, update, delete on modality from %I', :'runtime_user') where to_regclass('public.modality') is not null \gexec
select format('revoke insert, update, delete on modality_default_level from %I', :'runtime_user') where to_regclass('public.modality_default_level') is not null \gexec
select format('revoke delete on student from %I', :'runtime_user') where to_regclass('public.student') is not null \gexec
select format('revoke delete on student_guardian from %I', :'runtime_user') where to_regclass('public.student_guardian') is not null \gexec
alter default privileges in schema public grant select, insert, update, delete on tables to :"runtime_user";
SQL
