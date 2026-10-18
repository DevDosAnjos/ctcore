-- RF-ORG-02, RN-ORG-15: city of the unit, "short name adjusted once" flag and the names a unit has used.
-- Revert (manual, no data loss outside these objects):
--   drop table unit_short_name_history; alter table unit drop column short_name_adjusted, drop column city;
alter table unit add column city text;
alter table unit add column short_name_adjusted boolean not null default false;

-- Every short name a unit has used is reserved for it forever (D4). replaced_at is null while the name is the current one;
-- the primary key makes the reservation unique across organizations (the insert fails even when RLS hides the other row).
create table unit_short_name_history (
    short_name text primary key check (btrim(short_name) <> ''),
    organization_id uuid not null,
    unit_id uuid not null,
    replaced_at timestamptz,
    foreign key (organization_id, unit_id) references unit(organization_id, id)
);
create index unit_short_name_history_unit on unit_short_name_history (organization_id, unit_id);

alter table unit_short_name_history enable row level security;
alter table unit_short_name_history force row level security;
create policy organization_scope on unit_short_name_history
    using (organization_id = nullif(current_setting('app.organization_id', true), '')::uuid)
    with check (organization_id = nullif(current_setting('app.organization_id', true), '')::uuid);
