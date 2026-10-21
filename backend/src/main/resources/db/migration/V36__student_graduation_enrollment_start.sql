-- RF-MAT-01, RN-GRA-09, RN-MAT-05/07: graduations of a student (the origin one comes with the enrollment; #105 and #106 reuse the table) and the start of an
-- enrollment per unit (fee to charge or exemption, second charge date), which Sprint 08 reads to create the first charge.
-- Graduations and enrollment starts are never deleted (RN-ALU-08): the runtime role has no DELETE on them.
-- Revert (manual, no data to lose outside these objects): drop table enrollment_start; drop table student_graduation;
create table student_graduation (
    id uuid primary key,
    organization_id uuid not null references organization(id),
    student_id uuid not null,
    modality_id uuid not null references modality(id),
    level_id uuid not null references graduation_level(id),
    degree int not null check (degree between 0 and 10),
    achieved_on date,
    origin boolean not null,
    proof_file text,
    has_proof boolean not null,
    registered_by_account_id uuid not null references account(id),
    created_at timestamptz not null default now(),
    unique (organization_id, id),
    check (has_proof = (proof_file is not null)),
    foreign key (organization_id, student_id) references student(organization_id, id)
);
create index student_graduation_student on student_graduation (organization_id, student_id, modality_id);

create table enrollment_start (
    id uuid primary key,
    organization_id uuid not null references organization(id),
    enrollment_id uuid not null,
    unit_id uuid not null,
    concluded_at timestamptz not null,
    fee_cents bigint not null check (fee_cents >= 0),
    fee_exempt_reason text check (fee_exempt_reason is null or char_length(btrim(fee_exempt_reason)) between 3 and 200),
    second_due_on date not null,
    created_at timestamptz not null default now(),
    unique (organization_id, id),
    foreign key (organization_id, enrollment_id) references enrollment(organization_id, id),
    foreign key (organization_id, unit_id) references unit(organization_id, id)
);
create index enrollment_start_enrollment on enrollment_start (organization_id, enrollment_id, unit_id);

alter table student_graduation enable row level security;
alter table student_graduation force row level security;
create policy organization_scope on student_graduation
    using (organization_id = nullif(current_setting('app.organization_id', true), '')::uuid)
    with check (organization_id = nullif(current_setting('app.organization_id', true), '')::uuid);
alter table enrollment_start enable row level security;
alter table enrollment_start force row level security;
create policy organization_scope on enrollment_start
    using (organization_id = nullif(current_setting('app.organization_id', true), '')::uuid)
    with check (organization_id = nullif(current_setting('app.organization_id', true), '')::uuid);

-- Same technique as V33: take DELETE back from every grantee other than the owner.
do $$
declare grantee_name text; table_name_ text;
begin
    for table_name_ in select unnest(array['student_graduation', 'enrollment_start']) loop
        for grantee_name in select distinct grantee from information_schema.role_table_grants
            where table_schema = 'public' and table_name = table_name_ and privilege_type = 'DELETE'
              and grantee <> current_user and grantee <> 'PUBLIC' loop
            execute format('revoke delete on %I from %I', table_name_, grantee_name);
        end loop;
    end loop;
end $$;
