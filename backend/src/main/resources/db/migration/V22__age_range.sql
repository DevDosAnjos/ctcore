-- RF-MOD-04, RN-MOD-07, RN-ORG-17: age ranges are administrative labels of the organization (default Infantil 4-12, Juvenil 13-17, Adulto 18+).
-- Revert (manual, no data loss outside this object): drop table age_range;
create table age_range (
    id uuid primary key,
    organization_id uuid not null references organization(id),
    name text not null check (btrim(name) <> ''),
    name_key text not null,
    min_age smallint not null check (min_age between 0 and 120),
    max_age smallint check (max_age between 0 and 120),
    check (max_age is null or max_age >= min_age),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);
-- Guard against races; the code answers 409 before reaching it.
create unique index age_range_org_name_key on age_range (organization_id, name_key);

alter table age_range enable row level security;
alter table age_range force row level security;
create policy organization_scope on age_range
    using (organization_id = nullif(current_setting('app.organization_id', true), '')::uuid)
    with check (organization_id = nullif(current_setting('app.organization_id', true), '')::uuid);

insert into age_range (id, organization_id, name, name_key, min_age, max_age)
select gen_random_uuid(), o.id, d.name, d.name_key, d.min_age, d.max_age
from organization o
cross join (values ('Infantil', 'infantil', 4, 12), ('Juvenil', 'juvenil', 13, 17), ('Adulto', 'adulto', 18, null)) as d(name, name_key, min_age, max_age);
