-- RF-MOD-02, RN-GRA-01, RN-MOD-01: default graduation system per modality (global, read-only for the application), organization options and organization levels.
-- Revert (manual, no data loss outside these objects): drop table graduation_level; alter table organization_modality drop column graduation_enabled, drop column require_origin_proof; drop table modality_default_level;
create table modality_default_level (
    modality_id uuid not null references modality(id),
    position int not null check (position >= 0),
    name text not null check (char_length(btrim(name)) between 1 and 40),
    color text not null check (color ~ '^#[0-9A-Fa-f]{6}$'),
    secondary_color text check (secondary_color ~ '^#[0-9A-Fa-f]{6}$'),
    max_degree int not null check (max_degree between 0 and 10),
    min_age int check (min_age between 0 and 120),
    max_age int check (max_age between 0 and 120),
    kids boolean not null,
    check (min_age is null or max_age is null or min_age <= max_age),
    primary key (modality_id, position)
);

-- Jiu-Jitsu: IBJJF system (kids belts up to 15 years, then adult belts).
insert into modality_default_level (modality_id, position, name, color, secondary_color, max_degree, min_age, max_age, kids)
select m.id, v.position, v.name, v.color, v.secondary_color, v.max_degree, v.min_age, v.max_age, v.kids
from modality m, (values
    (0, 'Cinza e Branca', '#808080', '#FFFFFF', 4, 4, 15, true),
    (1, 'Cinza', '#808080', null, 4, 4, 15, true),
    (2, 'Cinza e Preta', '#808080', '#000000', 4, 4, 15, true),
    (3, 'Amarela e Branca', '#FFD700', '#FFFFFF', 4, 7, 15, true),
    (4, 'Amarela', '#FFD700', null, 4, 7, 15, true),
    (5, 'Amarela e Preta', '#FFD700', '#000000', 4, 7, 15, true),
    (6, 'Laranja e Branca', '#FF8C00', '#FFFFFF', 4, 10, 15, true),
    (7, 'Laranja', '#FF8C00', null, 4, 10, 15, true),
    (8, 'Laranja e Preta', '#FF8C00', '#000000', 4, 10, 15, true),
    (9, 'Verde e Branca', '#228B22', '#FFFFFF', 4, 13, 15, true),
    (10, 'Verde', '#228B22', null, 4, 13, 15, true),
    (11, 'Verde e Preta', '#228B22', '#000000', 4, 13, 15, true),
    (12, 'Branca', '#FFFFFF', null, 4, 16, null, false),
    (13, 'Azul', '#0000CD', null, 4, 16, null, false),
    (14, 'Roxa', '#800080', null, 4, 16, null, false),
    (15, 'Marrom', '#8B4513', null, 4, 18, null, false),
    (16, 'Preta', '#000000', null, 6, 19, null, false),
    (17, 'Coral', '#FF4040', '#000000', 0, 31, null, false),
    (18, 'Vermelha', '#DC143C', null, 0, 41, null, false)
) as v(position, name, color, secondary_color, max_degree, min_age, max_age, kids)
where m.name = 'Jiu-Jitsu';

-- Only the platform changes the defaults: the default privileges give the runtime role CRUD on new tables, so take write access back.
do $$
declare grantee_name text;
begin
    for grantee_name in select distinct grantee from information_schema.role_table_grants
        where table_schema = 'public' and table_name = 'modality_default_level' and privilege_type in ('INSERT', 'UPDATE', 'DELETE')
          and grantee <> current_user and grantee <> 'PUBLIC' loop
        execute format('revoke insert, update, delete on modality_default_level from %I', grantee_name);
    end loop;
end $$;

alter table organization_modality
    add column graduation_enabled boolean not null default false,
    add column require_origin_proof boolean not null default false;

create table graduation_level (
    id uuid primary key,
    organization_id uuid not null references organization(id),
    modality_id uuid not null references modality(id),
    position int not null check (position >= 0),
    name text not null check (char_length(btrim(name)) >= 1),
    color text not null check (color ~ '^#[0-9A-Fa-f]{6}$'),
    secondary_color text check (secondary_color ~ '^#[0-9A-Fa-f]{6}$'),
    max_degree int not null check (max_degree between 0 and 10),
    min_age int check (min_age between 0 and 120),
    max_age int check (max_age between 0 and 120),
    kids boolean not null,
    check (min_age is null or max_age is null or min_age <= max_age),
    constraint graduation_level_position unique (organization_id, modality_id, position) deferrable initially deferred
);
create unique index graduation_level_name on graduation_level (organization_id, modality_id, lower(name));
create index graduation_level_scope on graduation_level (organization_id, modality_id);

alter table graduation_level enable row level security;
alter table graduation_level force row level security;
create policy organization_scope on graduation_level
    using (organization_id = nullif(current_setting('app.organization_id', true), '')::uuid)
    with check (organization_id = nullif(current_setting('app.organization_id', true), '')::uuid);

-- Organizations that already have a modality active receive the default.
insert into graduation_level (id, organization_id, modality_id, position, name, color, secondary_color, max_degree, min_age, max_age, kids)
select gen_random_uuid(), o.organization_id, d.modality_id, d.position, d.name, d.color, d.secondary_color, d.max_degree, d.min_age, d.max_age, d.kids
from organization_modality o join modality_default_level d on d.modality_id = o.modality_id
where o.active;
