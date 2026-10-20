-- RF-TUR-01, RN-TUR-01/02/05/06: classes of a unit, their weekly schedule and assistant teachers. No attendance data (RN-TUR-06).
-- Revert (manual, no data loss outside these objects): drop table training_class_assistant; drop table training_class_schedule; drop table training_class;
create table training_class (
    id uuid primary key,
    organization_id uuid not null references organization(id),
    unit_id uuid not null,
    name text not null check (char_length(btrim(name)) between 2 and 80),
    name_key text not null,
    modality_id uuid not null references modality(id),
    age_range_id uuid references age_range(id),
    capacity int check (capacity between 1 and 200),
    head_teacher_id uuid not null,
    group_link text,
    status text not null default 'ACTIVE' check (status in ('ACTIVE', 'INACTIVE')),
    created_at timestamptz not null default now(),
    unique (organization_id, id),
    foreign key (organization_id, unit_id) references unit(organization_id, id),
    foreign key (organization_id, head_teacher_id) references teacher(organization_id, id)
);
-- RN-TUR-01: the name is unique in the unit while the class is active.
create unique index training_class_unit_name on training_class (unit_id, name_key) where status = 'ACTIVE';
create index training_class_org_name on training_class (organization_id, name_key, id);
create index training_class_head on training_class (organization_id, head_teacher_id);

create table training_class_schedule (
    organization_id uuid not null,
    class_id uuid not null,
    weekday int not null check (weekday between 1 and 7),
    starts_at time not null,
    ends_at time not null,
    check (ends_at > starts_at),
    primary key (class_id, weekday),
    foreign key (organization_id, class_id) references training_class(organization_id, id)
);

create table training_class_assistant (
    organization_id uuid not null,
    class_id uuid not null,
    teacher_id uuid not null,
    primary key (class_id, teacher_id),
    foreign key (organization_id, class_id) references training_class(organization_id, id),
    foreign key (organization_id, teacher_id) references teacher(organization_id, id)
);
create index training_class_assistant_teacher on training_class_assistant (organization_id, teacher_id);

alter table training_class enable row level security;
alter table training_class force row level security;
create policy organization_scope on training_class
    using (organization_id = nullif(current_setting('app.organization_id', true), '')::uuid)
    with check (organization_id = nullif(current_setting('app.organization_id', true), '')::uuid);

alter table training_class_schedule enable row level security;
alter table training_class_schedule force row level security;
create policy organization_scope on training_class_schedule
    using (organization_id = nullif(current_setting('app.organization_id', true), '')::uuid)
    with check (organization_id = nullif(current_setting('app.organization_id', true), '')::uuid);

alter table training_class_assistant enable row level security;
alter table training_class_assistant force row level security;
create policy organization_scope on training_class_assistant
    using (organization_id = nullif(current_setting('app.organization_id', true), '')::uuid)
    with check (organization_id = nullif(current_setting('app.organization_id', true), '')::uuid);
