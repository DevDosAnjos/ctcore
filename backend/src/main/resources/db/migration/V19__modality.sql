-- RF-MOD-01, RN-MOD-01..04: catalog of martial arts (global, read-only for the application), what each organization offers and the requests for new ones.
-- Revert (manual, no data loss outside these objects): drop table modality_request; drop table organization_modality; drop table modality;
create table modality (
    id uuid primary key default gen_random_uuid(),
    name text not null unique check (btrim(name) <> '')
);
insert into modality (name) values
    ('Jiu-Jitsu'), ('Judô'), ('Muay Thai'), ('Boxe'), ('Karatê'), ('Wrestling'), ('MMA'),
    ('Taekwondo'), ('Capoeira'), ('Kickboxing'), ('Luta Livre'), ('Kung Fu'), ('Krav Magá'), ('Defesa pessoal');

-- Only the platform changes the catalog: the default privileges give the runtime role CRUD on new tables, so take write access back.
do $$
declare grantee_name text;
begin
    for grantee_name in select distinct grantee from information_schema.role_table_grants
        where table_schema = 'public' and table_name = 'modality' and privilege_type in ('INSERT', 'UPDATE', 'DELETE')
          and grantee <> current_user and grantee <> 'PUBLIC' loop
        execute format('revoke insert, update, delete on modality from %I', grantee_name);
    end loop;
end $$;

create table organization_modality (
    organization_id uuid not null references organization(id),
    modality_id uuid not null references modality(id),
    active boolean not null,
    updated_at timestamptz not null default now(),
    primary key (organization_id, modality_id)
);

create table modality_request (
    id uuid primary key,
    organization_id uuid not null references organization(id),
    requested_by uuid not null references account(id),
    name text not null check (btrim(name) <> ''),
    note text,
    status text not null default 'OPEN' check (status in ('OPEN', 'ADDED', 'DISMISSED')),
    created_at timestamptz not null default now()
);
create unique index modality_request_open_name on modality_request (organization_id, lower(name)) where status = 'OPEN';

alter table organization_modality enable row level security;
alter table organization_modality force row level security;
create policy organization_scope on organization_modality
    using (organization_id = nullif(current_setting('app.organization_id', true), '')::uuid)
    with check (organization_id = nullif(current_setting('app.organization_id', true), '')::uuid);
alter table modality_request enable row level security;
alter table modality_request force row level security;
create policy organization_scope on modality_request
    using (organization_id = nullif(current_setting('app.organization_id', true), '')::uuid)
    with check (organization_id = nullif(current_setting('app.organization_id', true), '')::uuid);
