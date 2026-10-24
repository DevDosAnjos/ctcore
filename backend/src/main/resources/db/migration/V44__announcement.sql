-- RF-COM-01 (#81): comunicados e leituras. announcement guarda o comunicado (agendado ou enviado); announcement_read guarda uma leitura por aluno (RN-COM-08).
-- announcement_read: sem UPDATE nem DELETE para o papel operacional; announcement: sem UPDATE (a #82 acrescenta o que precisar); DELETE só cancela o agendado (regra do servidor).
-- Revert (manual): drop table announcement_read; drop table announcement;
create table announcement (
    id uuid primary key,
    organization_id uuid not null references organization(id),
    unit_id uuid,
    target_kind text not null check (target_kind in ('CLASS', 'MODALITY', 'UNIT', 'ORGANIZATION')),
    class_id uuid,
    modality_id uuid references modality(id),
    type text not null check (type in ('CANCELED_CLASS', 'TEACHER_CHANGE', 'SCHEDULE_CHANGE', 'NO_CLASS_DAY', 'GENERAL')),
    title text not null check (char_length(title) between 1 and 120),
    body text not null check (char_length(body) <= 2000),
    channels text[] not null default '{}',
    scheduled_for timestamptz,
    sent_at timestamptz,
    recipient_count int check (recipient_count >= 0),
    author_account_id uuid not null references account(id),
    created_at timestamptz not null default now(),
    unique (organization_id, id),
    foreign key (organization_id, unit_id) references unit(organization_id, id),
    foreign key (organization_id, class_id) references training_class(organization_id, id),
    constraint announcement_target_check check (
        (target_kind = 'CLASS' and class_id is not null and modality_id is null and unit_id is not null)
        or (target_kind = 'MODALITY' and modality_id is not null and class_id is null)
        or (target_kind = 'UNIT' and unit_id is not null and class_id is null and modality_id is null)
        or (target_kind = 'ORGANIZATION' and unit_id is null and class_id is null and modality_id is null)),
    constraint announcement_state_check check (
        (scheduled_for is not null and sent_at is null and recipient_count is null)
        or (sent_at is not null and recipient_count is not null))
);
create index announcement_by_date on announcement (organization_id, (coalesce(sent_at, scheduled_for)) desc);
create index announcement_by_class on announcement (organization_id, class_id);

create table announcement_read (
    organization_id uuid not null references organization(id),
    announcement_id uuid not null,
    student_id uuid not null,
    account_id uuid not null references account(id),
    opened_at timestamptz not null default now(),
    primary key (announcement_id, student_id),
    foreign key (organization_id, announcement_id) references announcement(organization_id, id),
    foreign key (organization_id, student_id) references student(organization_id, id)
);

do $$
declare t text; grantee_name text;
begin
    foreach t in array array['announcement', 'announcement_read'] loop
        execute format('alter table %I enable row level security', t);
        execute format('alter table %I force row level security', t);
        execute format($p$create policy organization_scope on %I
            using (organization_id = nullif(current_setting('app.organization_id', true), '')::uuid)
            with check (organization_id = nullif(current_setting('app.organization_id', true), '')::uuid)$p$, t);
        for grantee_name in select distinct grantee from information_schema.role_table_grants
            where table_schema = 'public' and table_name = t and privilege_type in ('DELETE', 'UPDATE')
              and grantee <> current_user and grantee <> 'PUBLIC' loop
            if t = 'announcement' then
                execute format('revoke update on %I from %I', t, grantee_name);
            else
                execute format('revoke delete, update on %I from %I', t, grantee_name);
            end if;
        end loop;
    end loop;
end $$;
