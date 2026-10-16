create table organization (
    id uuid primary key default gen_random_uuid(),
    name text not null check (btrim(name) <> ''),
    logo_url text,
    color text,
    holder_document text,
    joined_at timestamptz not null default now()
);

create table unit (
    id uuid primary key default gen_random_uuid(),
    organization_id uuid not null references organization(id),
    name text not null check (btrim(name) <> ''),
    short_name text not null check (btrim(short_name) <> ''),
    address text,
    latitude numeric check (latitude between -90 and 90),
    longitude numeric check (longitude between -180 and 180),
    email text,
    phone text,
    whatsapp text,
    cnpj text,
    timezone text not null default 'America/Sao_Paulo',
    active boolean not null default true,
    unique (organization_id, id),
    unique (short_name)
);

create table organization_settings (
    organization_id uuid primary key references organization(id),
    child_name text not null default 'Infantil' check (btrim(child_name) <> ''),
    youth_name text not null default 'Juvenil' check (btrim(youth_name) <> ''),
    adult_name text not null default 'Adulto' check (btrim(adult_name) <> ''),
    child_min_age integer not null default 4 check (child_min_age >= 0),
    child_max_age integer not null default 12 check (child_max_age >= child_min_age),
    youth_min_age integer not null default 13 check (youth_min_age > child_max_age),
    youth_max_age integer not null default 17 check (youth_max_age >= youth_min_age),
    adult_min_age integer not null default 18 check (adult_min_age > youth_max_age),
    adult_max_age integer check (adult_max_age >= adult_min_age),
    deactivation_days integer not null default 30 check (deactivation_days between 15 and 90),
    former_student_retention_months integer not null default 12 check (former_student_retention_months > 0),
    trial_retention_months integer not null default 6 check (trial_retention_months > 0),
    trial_limit integer not null default 2 check (trial_limit between 1 and 3),
    cross_unit_training text not null default 'plan' check (cross_unit_training in ('plan', 'allowed', 'denied')),
    -- Empty maps reserve per-modality requirements and future catalog/version identifiers.
    graduation_requirements jsonb not null default '{}' check (jsonb_typeof(graduation_requirements) = 'object'),
    modality_settings jsonb not null default '{}' check (jsonb_typeof(modality_settings) = 'object'),
    terms_versions jsonb not null default '{}' check (jsonb_typeof(terms_versions) = 'object')
);

create table unit_settings (
    organization_id uuid not null,
    unit_id uuid primary key,
    due_days integer[] not null default '{1,5,10,15}' check (
        cardinality(due_days) > 0 and array_position(due_days, null) is null
        and 1 <= all(due_days) and 28 >= all(due_days)),
    enrollment_fee_enabled boolean not null default false,
    enrollment_fee_cents bigint not null default 0 check (enrollment_fee_cents >= 0),
    cash_fee_cents bigint not null default 0 check (cash_fee_cents >= 0),
    pix_fee_cents bigint not null default 0 check (pix_fee_cents >= 0),
    bank_slip_fee_cents bigint not null default 0 check (bank_slip_fee_cents >= 0),
    credit_card_fee_cents bigint not null default 0 check (credit_card_fee_cents >= 0),
    debit_card_fee_cents bigint not null default 0 check (debit_card_fee_cents >= 0),
    automatic_debit_fee_cents bigint not null default 0 check (automatic_debit_fee_cents >= 0),
    manual_pix_fee_cents bigint not null default 0 check (manual_pix_fee_cents >= 0),
    terminal_card_fee_cents bigint not null default 0 check (terminal_card_fee_cents >= 0),
    transfer_fee_cents bigint not null default 0 check (transfer_fee_cents >= 0),
    pix_key text,
    payment_account_reference text,
    reminder_days integer[] not null default '{-3,0,3,10}' check (array_position(reminder_days, null) is null),
    teacher_permissions boolean[] not null default '{true,true,true,true}' check (
        cardinality(teacher_permissions) = 4 and array_position(teacher_permissions, null) is null),
    directory_enabled boolean not null default false,
    trial_link_enabled boolean not null default true,
    self_registration_link_enabled boolean not null default true,
    foreign key (organization_id, unit_id) references unit(organization_id, id)
);

create table organization_limits (
    organization_id uuid primary key references organization(id),
    students integer check (students >= 0),
    management_users integer check (management_users >= 0),
    units integer check (units >= 0),
    modules text[] check (array_position(modules, null) is null),
    operational_units integer not null default 10 check (operational_units = 10),
    operational_management_users integer not null default 200 check (operational_management_users = 200)
);

alter table organization enable row level security;
alter table organization force row level security;
create policy organization_scope on organization
    using (id = nullif(current_setting('app.organization_id', true), '')::uuid)
    with check (id = nullif(current_setting('app.organization_id', true), '')::uuid);

do $$
declare table_name text;
begin
    foreach table_name in array array['unit', 'organization_settings', 'unit_settings', 'organization_limits'] loop
        execute format('alter table %I enable row level security', table_name);
        execute format('alter table %I force row level security', table_name);
        execute format('create policy organization_scope on %I using
            (organization_id = nullif(current_setting(''app.organization_id'', true), '''')::uuid)
            with check (organization_id = nullif(current_setting(''app.organization_id'', true), '''')::uuid)', table_name);
    end loop;
end $$;
