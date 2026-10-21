-- RF-MAT, RN-MAT-01/02/04/19: one enrollment per student in the organization and its modalities (unit, main class, plan, due day), each with its own status.
-- Enrollments and modalities are never deleted (RN-ALU-08): the runtime role has no DELETE on them. The extra classes of a modality are configuration and can be replaced.
-- Revert (manual, no data to lose outside these objects): drop table enrollment_modality_class; drop table enrollment_modality; drop table enrollment;
create table enrollment (
    id uuid primary key,
    organization_id uuid not null references organization(id),
    student_id uuid not null,
    enrolled_at timestamptz not null,
    reopened_at timestamptz,
    created_at timestamptz not null default now(),
    unique (organization_id, id),
    unique (organization_id, student_id),
    foreign key (organization_id, student_id) references student(organization_id, id)
);

create table enrollment_modality (
    id uuid primary key,
    organization_id uuid not null references organization(id),
    enrollment_id uuid not null,
    student_id uuid not null,
    unit_id uuid not null,
    modality_id uuid not null references modality(id),
    primary_class_id uuid not null,
    plan_id uuid not null,
    next_plan_id uuid,
    due_day int not null check (due_day between 1 and 28),
    status text not null check (status in ('ACTIVE', 'ENDED')),
    started_at timestamptz not null,
    ended_at timestamptz,
    created_at timestamptz not null default now(),
    unique (organization_id, id),
    check ((status = 'ENDED') = (ended_at is not null)),
    foreign key (organization_id, enrollment_id) references enrollment(organization_id, id),
    foreign key (organization_id, student_id) references student(organization_id, id),
    foreign key (organization_id, unit_id) references unit(organization_id, id),
    foreign key (organization_id, primary_class_id) references training_class(organization_id, id),
    foreign key (organization_id, plan_id) references plan(organization_id, id),
    foreign key (organization_id, next_plan_id) references plan(organization_id, id)
);
-- RN-MAT-01 (D3): one active modality of each kind per student.
create unique index enrollment_modality_active on enrollment_modality (student_id, modality_id) where status = 'ACTIVE';
create index enrollment_modality_unit on enrollment_modality (organization_id, unit_id, status);
create index enrollment_modality_by_class on enrollment_modality (primary_class_id);
create index enrollment_modality_student on enrollment_modality (organization_id, student_id);

create table enrollment_modality_class (
    organization_id uuid not null,
    modality_row_id uuid not null,
    class_id uuid not null,
    primary key (modality_row_id, class_id),
    foreign key (organization_id, modality_row_id) references enrollment_modality(organization_id, id),
    foreign key (organization_id, class_id) references training_class(organization_id, id)
);
create index enrollment_modality_class_by_class on enrollment_modality_class (class_id);

alter table enrollment enable row level security;
alter table enrollment force row level security;
create policy organization_scope on enrollment
    using (organization_id = nullif(current_setting('app.organization_id', true), '')::uuid)
    with check (organization_id = nullif(current_setting('app.organization_id', true), '')::uuid);
alter table enrollment_modality enable row level security;
alter table enrollment_modality force row level security;
create policy organization_scope on enrollment_modality
    using (organization_id = nullif(current_setting('app.organization_id', true), '')::uuid)
    with check (organization_id = nullif(current_setting('app.organization_id', true), '')::uuid);
alter table enrollment_modality_class enable row level security;
alter table enrollment_modality_class force row level security;
create policy organization_scope on enrollment_modality_class
    using (organization_id = nullif(current_setting('app.organization_id', true), '')::uuid)
    with check (organization_id = nullif(current_setting('app.organization_id', true), '')::uuid);

-- Same technique as V29: take DELETE back from every grantee other than the owner.
do $$
declare grantee_name text; table_name_ text;
begin
    for table_name_ in select unnest(array['enrollment', 'enrollment_modality']) loop
        for grantee_name in select distinct grantee from information_schema.role_table_grants
            where table_schema = 'public' and table_name = table_name_ and privilege_type = 'DELETE'
              and grantee <> current_user and grantee <> 'PUBLIC' loop
            execute format('revoke delete on %I from %I', table_name_, grantee_name);
        end loop;
    end loop;
end $$;
