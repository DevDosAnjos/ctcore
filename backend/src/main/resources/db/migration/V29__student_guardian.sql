-- RF-ALU-01, RN-ALU-01..11, RN-RSP-01..07: students of the organization, their guardians (one record per person per organization) and the links.
-- No health data by construction (RN-LGP-02). Students and links are never deleted (RN-ALU-08): the runtime role has no DELETE on them.
-- Revert (manual, no data to lose outside these objects): drop table student_guardian; drop table guardian; drop table student; drop function student_guardian_limit(); drop function unlink_student_guardian(uuid, uuid); restore email_message_template_check without GUARDIAN_UNLINKED and STUDENT_MAJORITY (V18 text);
create table student (
    id uuid primary key,
    organization_id uuid not null references organization(id),
    registered_unit_id uuid not null,
    name text not null check (char_length(btrim(name)) between 2 and 80),
    name_key text not null,
    birth_date date not null,
    cpf text check (cpf ~ '^[0-9]{11}$'),
    email text,
    phone text,
    postal_code text not null,
    street text not null,
    number text not null,
    complement text,
    district text not null,
    city text not null,
    state text not null,
    photo text,
    gender text check (gender in ('MALE', 'FEMALE', 'OTHER', 'PREFER_NOT_TO_SAY')),
    referral text check (referral in ('INSTAGRAM', 'REFERRAL', 'DIRECTORY', 'WALK_BY', 'OTHER')),
    emergency_name text,
    emergency_relationship text,
    emergency_phone text,
    account_id uuid references account(id),
    email_problem_at timestamptz,
    majority_notice_at timestamptz,
    created_at timestamptz not null default now(),
    unique (organization_id, id),
    foreign key (organization_id, registered_unit_id) references unit(organization_id, id),
    constraint student_emergency_all_or_none check (
        (emergency_name is null and emergency_relationship is null and emergency_phone is null)
        or (emergency_name is not null and emergency_relationship is not null and emergency_phone is not null))
);
create unique index student_org_cpf on student (organization_id, cpf) where cpf is not null;
create index student_org_name on student (organization_id, name_key, id);
create index student_org_name_birth on student (organization_id, name_key, birth_date);
create index student_org_unit on student (organization_id, registered_unit_id);

create table guardian (
    id uuid primary key,
    organization_id uuid not null references organization(id),
    account_id uuid not null references account(id),
    name text not null check (char_length(btrim(name)) between 2 and 80),
    cpf text not null check (cpf ~ '^[0-9]{11}$'),
    phone text not null,
    email_problem_at timestamptz,
    created_at timestamptz not null default now(),
    unique (organization_id, id),
    unique (organization_id, account_id),
    unique (organization_id, cpf)
);

create table student_guardian (
    organization_id uuid not null references organization(id),
    student_id uuid not null,
    guardian_id uuid not null,
    financial boolean not null default false,
    created_at timestamptz not null default now(),
    primary key (student_id, guardian_id),
    foreign key (organization_id, student_id) references student(organization_id, id),
    foreign key (organization_id, guardian_id) references guardian(organization_id, id)
);
create unique index student_guardian_financial on student_guardian (student_id) where financial;
create index student_guardian_guardian on student_guardian (organization_id, guardian_id);

-- RN-RSP-02: never more than two guardians per student. The service also locks the student row, so concurrent links serialize.
create function student_guardian_limit() returns trigger language plpgsql as $$
begin
    if (select count(*) from student_guardian where student_id = new.student_id) >= 2
       and not exists (select 1 from student_guardian where student_id = new.student_id and guardian_id = new.guardian_id) then
        raise exception 'student already has two guardians' using errcode = '23514';
    end if;
    return new;
end $$;
create trigger student_guardian_limit before insert on student_guardian
    for each row execute function student_guardian_limit();

alter table student enable row level security;
alter table student force row level security;
create policy organization_scope on student
    using (organization_id = nullif(current_setting('app.organization_id', true), '')::uuid)
    with check (organization_id = nullif(current_setting('app.organization_id', true), '')::uuid);
alter table guardian enable row level security;
alter table guardian force row level security;
create policy organization_scope on guardian
    using (organization_id = nullif(current_setting('app.organization_id', true), '')::uuid)
    with check (organization_id = nullif(current_setting('app.organization_id', true), '')::uuid);
alter table student_guardian enable row level security;
alter table student_guardian force row level security;
create policy organization_scope on student_guardian
    using (organization_id = nullif(current_setting('app.organization_id', true), '')::uuid)
    with check (organization_id = nullif(current_setting('app.organization_id', true), '')::uuid);

-- Same technique as V19: take DELETE back from every grantee other than the owner (default privileges gave it to the runtime role).
do $$
declare grantee_name text; table_name_ text;
begin
    for table_name_ in select unnest(array['student', 'student_guardian']) loop
        for grantee_name in select distinct grantee from information_schema.role_table_grants
            where table_schema = 'public' and table_name = table_name_ and privilege_type = 'DELETE'
              and grantee <> current_user and grantee <> 'PUBLIC' loop
            execute format('revoke delete on %I from %I', table_name_, grantee_name);
        end loop;
    end loop;
end $$;

-- RN-RSP-05 + RN-ALU-08: the runtime role cannot DELETE from student_guardian; unlinking goes through this one function,
-- which removes one link of the session organization and nothing else (the history stays in audit_log).
create function unlink_student_guardian(p_student uuid, p_guardian uuid) returns integer
language sql security definer set search_path = public as $$
    with removed as (
        delete from student_guardian
        where student_id = p_student and guardian_id = p_guardian
          and organization_id = nullif(current_setting('app.organization_id', true), '')::uuid
        returning 1)
    select count(*)::integer from removed
$$;

-- The two e-mails of this item (RN-RSP-05 neutral notice, RN-RSP-07 majority notice) join the allowed templates.
alter table email_message drop constraint email_message_template_check;
alter table email_message add constraint email_message_template_check
    check (template in ('INVITE', 'PASSWORD_RESET', 'PASSWORD_CHANGED', 'NEW_ACCESS', 'COORDINATION_TRANSFERRED',
                        'EMAIL_CHANGE', 'EMAIL_CHANGED', 'GUARDIAN_UNLINKED', 'STUDENT_MAJORITY'));
