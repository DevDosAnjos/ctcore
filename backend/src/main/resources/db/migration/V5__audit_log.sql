-- RN-AUD-01/02: append-only audit trail, isolated per organization (RNF-06).
create table audit_log (
    id uuid primary key default gen_random_uuid(),
    organization_id uuid references organization(id),
    unit_id uuid,
    actor_type text not null check (actor_type in
        ('ACCOUNT', 'SYSTEM', 'PORTAL', 'SITE', 'PLATFORM_SUPPORT', 'PLATFORM_OWNER')),
    actor_id uuid,
    actor_profile text,
    action text not null check (btrim(action) <> ''),
    entity_type text not null check (btrim(entity_type) <> ''),
    entity_id text not null check (btrim(entity_id) <> ''),
    before_data jsonb,
    after_data jsonb,
    reason text,
    ip text,
    occurred_at timestamptz not null,
    foreign key (organization_id, unit_id) references unit(organization_id, id),
    check (organization_id is not null or actor_type in ('PLATFORM_SUPPORT', 'PLATFORM_OWNER'))
);

create index audit_log_org_time on audit_log (organization_id, occurred_at desc);
create index audit_log_org_entity on audit_log (organization_id, entity_type, entity_id);

create function audit_log_append_only() returns trigger language plpgsql as $$
begin
    raise exception 'audit_log is append-only (%)', tg_op using errcode = 'insufficient_privilege';
end $$;

create trigger audit_log_no_change before update or delete on audit_log
    for each row execute function audit_log_append_only();
create trigger audit_log_no_truncate before truncate on audit_log
    for each statement execute function audit_log_append_only();

alter table audit_log enable row level security;
alter table audit_log force row level security;
-- Covers select, update and delete visibility, so the append-only trigger (not a silent no-op) refuses changes.
create policy audit_read on audit_log
    using (organization_id = nullif(current_setting('app.organization_id', true), '')::uuid);
create policy audit_insert on audit_log for insert
    with check (organization_id is not distinct from nullif(current_setting('app.organization_id', true), '')::uuid);
