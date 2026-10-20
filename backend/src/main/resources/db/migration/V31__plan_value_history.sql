-- RF-PLA-03, RN-PLA-03: value history of a plan (append-only) and a ceiling on the plan value.
-- Revert (manual, no data loss outside these objects): drop table plan_value_history; alter table plan drop constraint plan_value_cents_max;
alter table plan add constraint plan_value_cents_max check (value_cents <= 99999999);

create table plan_value_history (
    id uuid primary key default gen_random_uuid(),
    organization_id uuid not null references organization(id),
    plan_id uuid not null,
    value_cents bigint not null check (value_cents > 0 and value_cents <= 99999999),
    starts_on date not null,
    created_by uuid,
    created_at timestamptz not null default now(),
    foreign key (organization_id, plan_id) references plan(organization_id, id)
);
create index plan_value_history_plan on plan_value_history (plan_id, starts_on);

-- One initial line per plan that already exists (current value, starting on the day it was created).
insert into plan_value_history (organization_id, plan_id, value_cents, starts_on)
    select organization_id, id, value_cents, created_at::date from plan;

alter table plan_value_history enable row level security;
alter table plan_value_history force row level security;
create policy organization_scope on plan_value_history
    using (organization_id = nullif(current_setting('app.organization_id', true), '')::uuid)
    with check (organization_id = nullif(current_setting('app.organization_id', true), '')::uuid);

-- Same technique as V19: append-only, so take UPDATE and DELETE back from every grantee other than the owner.
do $$
declare grantee_name text;
begin
    for grantee_name in select distinct grantee from information_schema.role_table_grants
        where table_schema = 'public' and table_name = 'plan_value_history' and privilege_type in ('UPDATE', 'DELETE')
          and grantee <> current_user and grantee <> 'PUBLIC' loop
        execute format('revoke update, delete on plan_value_history from %I', grantee_name);
    end loop;
end $$;
