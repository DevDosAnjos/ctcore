-- RN-MAT-11 to RN-MAT-14, RN-LGP-01: acceptance of the enrollment term and the image-use term (grant and revoke), and the short confirmation of a new modality.
-- Insert-only: the runtime role has no UPDATE and no DELETE, so a record never changes (it cites the text version).
-- Revert (manual): drop table modality_confirmation; drop table term_acceptance; and restore email_message_template_check without 'WELCOME' (list in V47).
create table term_acceptance (
    id uuid primary key,
    organization_id uuid not null references organization(id),
    student_id uuid not null,
    kind text not null check (kind in ('ENROLLMENT', 'IMAGE_USE')),
    event text not null check (event in ('ACCEPTED', 'REVOKED')),
    version int not null check (version >= 0),
    account_id uuid not null references account(id),
    accepted_at timestamptz not null default now(),
    ip text not null,
    check (event = 'ACCEPTED' or kind = 'IMAGE_USE'),
    foreign key (organization_id, student_id) references student(organization_id, id)
);
-- Race guard: two simultaneous acceptances of the same term version leave a single row (the code answers 200 to the loser).
create unique index term_acceptance_enrollment_version on term_acceptance (student_id, version) where kind = 'ENROLLMENT';
create index term_acceptance_student on term_acceptance (organization_id, student_id, kind, accepted_at);

create table modality_confirmation (
    id uuid primary key,
    organization_id uuid not null references organization(id),
    enrollment_modality_id uuid not null,
    student_id uuid not null,
    account_id uuid not null references account(id),
    plan_id uuid not null,
    plan_value_cents bigint not null check (plan_value_cents >= 0),
    confirmed_at timestamptz not null default now(),
    ip text not null,
    unique (enrollment_modality_id),
    foreign key (organization_id, enrollment_modality_id) references enrollment_modality(organization_id, id),
    foreign key (organization_id, student_id) references student(organization_id, id),
    foreign key (organization_id, plan_id) references plan(organization_id, id)
);

alter table term_acceptance enable row level security;
alter table term_acceptance force row level security;
create policy organization_scope on term_acceptance
    using (organization_id = nullif(current_setting('app.organization_id', true), '')::uuid)
    with check (organization_id = nullif(current_setting('app.organization_id', true), '')::uuid);
alter table modality_confirmation enable row level security;
alter table modality_confirmation force row level security;
create policy organization_scope on modality_confirmation
    using (organization_id = nullif(current_setting('app.organization_id', true), '')::uuid)
    with check (organization_id = nullif(current_setting('app.organization_id', true), '')::uuid);

-- Same technique as V34 and V38: take UPDATE and DELETE back from every grantee other than the owner.
do $$
declare grantee_name text; table_name_ text;
begin
    foreach table_name_ in array array['term_acceptance', 'modality_confirmation'] loop
        for grantee_name in select distinct grantee from information_schema.role_table_grants
            where table_schema = 'public' and table_name = table_name_ and privilege_type in ('DELETE', 'UPDATE')
              and grantee <> current_user and grantee <> 'PUBLIC' loop
            execute format('revoke update, delete on %I from %I', table_name_, grantee_name);
        end loop;
    end loop;
end $$;

-- RN-MAT-11: the welcome e-mail enters the allowed templates (V47 list plus WELCOME).
alter table email_message drop constraint email_message_template_check;
alter table email_message add constraint email_message_template_check
    check (template in ('INVITE', 'PASSWORD_RESET', 'PASSWORD_CHANGED', 'NEW_ACCESS', 'COORDINATION_TRANSFERRED',
                        'EMAIL_CHANGE', 'EMAIL_CHANGED', 'GUARDIAN_UNLINKED', 'STUDENT_MAJORITY', 'PAYMENT_CONFIRMED',
                        'CHARGE_CREATED', 'PLAN_RATE_ADJUSTED', 'READJUSTMENT_DIFF', 'ANNOUNCEMENT',
                        'REMINDER_BEFORE', 'REMINDER_DUE', 'REMINDER_OVERDUE', 'BLOCK_WARNING', 'NOTIFICATION_URGENT', 'WELCOME'));
