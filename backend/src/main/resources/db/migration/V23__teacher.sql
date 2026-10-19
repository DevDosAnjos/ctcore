-- RF-PRO-01, RN-PRO-01/03/04/07: teachers of the organization, the units where they teach and the graduation they hold per modality.
-- Revert (manual, no data loss outside these objects): drop table teacher_modality; drop table teacher_unit; drop table teacher; drop function teacher_modality_check();
create table teacher (
    id uuid primary key,
    organization_id uuid not null references organization(id),
    name text not null check (char_length(btrim(name)) between 2 and 80),
    name_key text not null,
    photo text,
    email text,
    phone text,
    bio text check (char_length(bio) <= 300),
    directory_consent boolean not null default false,
    consent_declared_by_management boolean not null default false,
    status text not null default 'ACTIVE' check (status in ('ACTIVE', 'INACTIVE')),
    account_id uuid references account(id),
    created_at timestamptz not null default now(),
    unique (organization_id, id)
);
-- One registration per account per organization (RN-PRO-07).
create unique index teacher_org_account on teacher (organization_id, account_id) where account_id is not null;
create index teacher_org_name on teacher (organization_id, name_key, id);

create table teacher_unit (
    organization_id uuid not null,
    teacher_id uuid not null,
    unit_id uuid not null,
    primary key (teacher_id, unit_id),
    foreign key (organization_id, teacher_id) references teacher(organization_id, id),
    foreign key (organization_id, unit_id) references unit(organization_id, id)
);
create index teacher_unit_unit on teacher_unit (organization_id, unit_id);

create table teacher_modality (
    organization_id uuid not null,
    teacher_id uuid not null,
    modality_id uuid not null references modality(id),
    level_id uuid references graduation_level(id) on delete set null,
    degree int not null default 0 check (degree between 0 and 10),
    primary key (teacher_id, modality_id),
    foreign key (organization_id, teacher_id) references teacher(organization_id, id)
);

-- RN-PRO-03: the level belongs to the same organization and modality, and the degree stays within the level maximum.
create function teacher_modality_check() returns trigger language plpgsql as $$
declare lvl graduation_level%rowtype;
begin
    if new.level_id is null then return new; end if;
    select * into lvl from graduation_level where id = new.level_id;
    if not found or lvl.organization_id <> new.organization_id or lvl.modality_id <> new.modality_id then
        raise exception 'graduation level does not belong to the organization modality' using errcode = '23514';
    end if;
    if new.degree > lvl.max_degree then
        raise exception 'degree above the level maximum' using errcode = '23514';
    end if;
    return new;
end $$;
create trigger teacher_modality_check before insert or update on teacher_modality
    for each row execute function teacher_modality_check();

alter table teacher enable row level security;
alter table teacher force row level security;
create policy organization_scope on teacher
    using (organization_id = nullif(current_setting('app.organization_id', true), '')::uuid)
    with check (organization_id = nullif(current_setting('app.organization_id', true), '')::uuid);

alter table teacher_unit enable row level security;
alter table teacher_unit force row level security;
create policy organization_scope on teacher_unit
    using (organization_id = nullif(current_setting('app.organization_id', true), '')::uuid)
    with check (organization_id = nullif(current_setting('app.organization_id', true), '')::uuid);

alter table teacher_modality enable row level security;
alter table teacher_modality force row level security;
create policy organization_scope on teacher_modality
    using (organization_id = nullif(current_setting('app.organization_id', true), '')::uuid)
    with check (organization_id = nullif(current_setting('app.organization_id', true), '')::uuid);
