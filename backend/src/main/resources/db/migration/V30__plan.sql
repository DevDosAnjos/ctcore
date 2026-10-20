-- RF-PLA-01, RN-PLA-01/02/03/06: plans of a unit. Value in cents; no lessons-taken control (RN-PLA-06). Value history comes in #57.
-- Revert (manual, no data loss outside this object): drop table plan;
create table plan (
    id uuid primary key,
    organization_id uuid not null references organization(id),
    unit_id uuid not null,
    modality_id uuid not null references modality(id),
    name text not null check (char_length(btrim(name)) between 2 and 60),
    name_key text not null,
    value_cents bigint not null check (value_cents > 0),
    weekly_frequency int check (weekly_frequency in (2, 3)),
    valid_other_units boolean not null default false,
    status text not null default 'ACTIVE' check (status in ('ACTIVE', 'INACTIVE')),
    created_at timestamptz not null default now(),
    unique (organization_id, id),
    foreign key (organization_id, unit_id) references unit(organization_id, id)
);
-- RN-PLA-01: one plan per modality and name in the unit while active.
create unique index plan_unit_modality_name on plan (unit_id, modality_id, name_key) where status = 'ACTIVE';
create index plan_org_unit on plan (organization_id, unit_id, status);

alter table plan enable row level security;
alter table plan force row level security;
create policy organization_scope on plan
    using (organization_id = nullif(current_setting('app.organization_id', true), '')::uuid)
    with check (organization_id = nullif(current_setting('app.organization_id', true), '')::uuid);
