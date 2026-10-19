-- RF-MOD-03, RN-MOD-02/03: which of the organization's active modalities each unit offers. Rows survive the organization deactivating a modality (D3).
-- Revert (manual, no data loss outside this object): drop table unit_modality;
create table unit_modality (
    organization_id uuid not null references organization(id),
    unit_id uuid not null references unit(id),
    modality_id uuid not null references modality(id),
    offered boolean not null,
    updated_at timestamptz not null default now(),
    primary key (unit_id, modality_id)
);

alter table unit_modality enable row level security;
alter table unit_modality force row level security;
create policy organization_scope on unit_modality
    using (organization_id = nullif(current_setting('app.organization_id', true), '')::uuid)
    with check (organization_id = nullif(current_setting('app.organization_id', true), '')::uuid);
