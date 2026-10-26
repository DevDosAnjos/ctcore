-- RF-MAT-04 (RN-MAT-16, RN-MAT-18, #86): requests the student (or the financial guardian) makes through the portal: class change, new modality, plan change and closure.
-- A request never changes the enrollment by itself. The runtime role has no DELETE.
-- Revert (manual): drop table portal_request; and restore notification_type_check (list in V47) and email_message_template_check (list in V48) without 'PORTAL_REQUEST'.
create table portal_request (
    id uuid primary key,
    organization_id uuid not null references organization(id),
    student_id uuid not null,
    unit_id uuid not null,
    kind text not null check (kind in ('CLASS_CHANGE', 'NEW_MODALITY', 'PLAN_CHANGE', 'CLOSURE')),
    enrollment_modality_id uuid,
    target_class_id uuid,
    target_plan_id uuid,
    target_modality_id uuid references modality(id),
    target_unit_id uuid,
    reason text check (reason in ('CITY_CHANGE', 'FINANCIAL', 'SCHEDULE', 'INJURY', 'DISSATISFACTION', 'OTHER')),
    note text check (char_length(note) <= 500),
    status text not null default 'OPEN' check (status in ('OPEN', 'APPROVED', 'REFUSED', 'EXPIRED', 'CLOSED')),
    requested_by_account_id uuid not null references account(id),
    ip text not null,
    created_at timestamptz not null default now(),
    decided_at timestamptz,
    decided_by_account_id uuid references account(id),
    response text check (char_length(response) <= 500),
    unique (organization_id, id),
    check ((kind = 'NEW_MODALITY') = (enrollment_modality_id is null)),
    check ((kind = 'CLASS_CHANGE') = (target_class_id is not null)),
    check ((kind = 'PLAN_CHANGE') = (target_plan_id is not null)),
    check ((kind = 'NEW_MODALITY') = (target_modality_id is not null and target_unit_id is not null)),
    check ((kind = 'CLOSURE') = (reason is not null)),
    check ((status = 'OPEN') = (decided_at is null)),
    foreign key (organization_id, student_id) references student(organization_id, id),
    foreign key (organization_id, unit_id) references unit(organization_id, id),
    foreign key (organization_id, enrollment_modality_id) references enrollment_modality(organization_id, id),
    foreign key (organization_id, target_class_id) references training_class(organization_id, id),
    foreign key (organization_id, target_plan_id) references plan(organization_id, id),
    foreign key (organization_id, target_unit_id) references unit(organization_id, id)
);
-- Race guard (R3): two simultaneous equal requests leave a single open row.
create unique index portal_request_open_modality on portal_request (enrollment_modality_id, kind) where status = 'OPEN' and enrollment_modality_id is not null;
create unique index portal_request_open_new_modality on portal_request (student_id, target_unit_id, target_modality_id) where status = 'OPEN' and kind = 'NEW_MODALITY';
create index portal_request_list on portal_request (organization_id, unit_id, status, created_at);
create index portal_request_student on portal_request (organization_id, student_id, created_at);

alter table portal_request enable row level security;
alter table portal_request force row level security;
create policy organization_scope on portal_request
    using (organization_id = nullif(current_setting('app.organization_id', true), '')::uuid)
    with check (organization_id = nullif(current_setting('app.organization_id', true), '')::uuid);

-- Same technique as V33: take DELETE back from every grantee other than the owner.
do $$
declare grantee_name text;
begin
    for grantee_name in select distinct grantee from information_schema.role_table_grants
        where table_schema = 'public' and table_name = 'portal_request' and privilege_type = 'DELETE'
          and grantee <> current_user and grantee <> 'PUBLIC' loop
        execute format('revoke delete on portal_request from %I', grantee_name);
    end loop;
end $$;

alter table notification drop constraint notification_type_check;
alter table notification add constraint notification_type_check
    check (type in ('PAYMENT_RECEIVED', 'HIGH_BENEFIT', 'STUDENT_MAJORITY', 'EMAIL_PROBLEM', 'INVITE_PENDING',
        'CLOSURE_REQUEST', 'MP_DISCONNECTED', 'SUBSCRIPTION_OVERDUE', 'PORTAL_REQUEST'));

alter table email_message drop constraint email_message_template_check;
alter table email_message add constraint email_message_template_check
    check (template in ('INVITE', 'PASSWORD_RESET', 'PASSWORD_CHANGED', 'NEW_ACCESS', 'COORDINATION_TRANSFERRED',
                        'EMAIL_CHANGE', 'EMAIL_CHANGED', 'GUARDIAN_UNLINKED', 'STUDENT_MAJORITY', 'PAYMENT_CONFIRMED',
                        'CHARGE_CREATED', 'PLAN_RATE_ADJUSTED', 'READJUSTMENT_DIFF', 'ANNOUNCEMENT',
                        'REMINDER_BEFORE', 'REMINDER_DUE', 'REMINDER_OVERDUE', 'BLOCK_WARNING', 'NOTIFICATION_URGENT', 'WELCOME', 'PORTAL_REQUEST'));
