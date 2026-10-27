-- RF-COB-05 (RN-COB-13, RN-COB-19, RN-INA-02, #87): Pix proof sent through the portal and decided by the management; days a charge spent in review postpone its block date.
-- A proof is never deleted (the runtime role has no DELETE). The file key never goes to the audit log.
-- Revert (manual): drop table payment_proof; alter table charge drop column block_pause_days; and restore notification_type_check (list in V49) and
-- email_message_template_check (list in V49) without 'PAYMENT_PROOF' and 'PROOF_REFUSED'.
create table payment_proof (
    id uuid primary key,
    organization_id uuid not null references organization(id),
    charge_id uuid not null,
    student_id uuid not null,
    unit_id uuid not null,
    file_key text not null,
    content_type text not null,
    sent_by_account_id uuid not null references account(id),
    sent_at timestamptz not null default now(),
    status text not null default 'PENDING' check (status in ('PENDING', 'CONFIRMED', 'REFUSED')),
    decided_at timestamptz,
    decided_by_account_id uuid references account(id),
    refusal_reason text check (refusal_reason is null or char_length(refusal_reason) <= 200),
    payment_id uuid,
    unique (organization_id, id),
    check ((status = 'PENDING') = (decided_at is null)),
    check ((status = 'REFUSED') = (refusal_reason is not null)),
    check ((status = 'CONFIRMED') = (payment_id is not null)),
    foreign key (organization_id, charge_id) references charge(organization_id, id),
    foreign key (organization_id, student_id) references student(organization_id, id),
    foreign key (organization_id, unit_id) references unit(organization_id, id),
    foreign key (organization_id, payment_id) references payment(organization_id, id)
);
-- Race guard (R2): two simultaneous sends leave a single pending proof per charge.
create unique index payment_proof_one_pending on payment_proof (charge_id) where status = 'PENDING';
create index payment_proof_list on payment_proof (organization_id, unit_id, status, sent_at);

alter table payment_proof enable row level security;
alter table payment_proof force row level security;
create policy organization_scope on payment_proof
    using (organization_id = nullif(current_setting('app.organization_id', true), '')::uuid)
    with check (organization_id = nullif(current_setting('app.organization_id', true), '')::uuid);

-- Same technique as V33: take DELETE back from every grantee other than the owner.
do $$
declare grantee_name text;
begin
    for grantee_name in select distinct grantee from information_schema.role_table_grants
        where table_schema = 'public' and table_name = 'payment_proof' and privilege_type = 'DELETE'
          and grantee <> current_user and grantee <> 'PUBLIC' loop
        execute format('revoke delete on payment_proof from %I', grantee_name);
    end loop;
end $$;

-- RN-INA-02 (D5): days the charge spent in review, added to the block date.
alter table charge add column block_pause_days int not null default 0 check (block_pause_days >= 0);

alter table notification drop constraint notification_type_check;
alter table notification add constraint notification_type_check
    check (type in ('PAYMENT_RECEIVED', 'HIGH_BENEFIT', 'STUDENT_MAJORITY', 'EMAIL_PROBLEM', 'INVITE_PENDING',
        'CLOSURE_REQUEST', 'MP_DISCONNECTED', 'SUBSCRIPTION_OVERDUE', 'PORTAL_REQUEST', 'PAYMENT_PROOF'));

alter table email_message drop constraint email_message_template_check;
alter table email_message add constraint email_message_template_check
    check (template in ('INVITE', 'PASSWORD_RESET', 'PASSWORD_CHANGED', 'NEW_ACCESS', 'COORDINATION_TRANSFERRED',
                        'EMAIL_CHANGE', 'EMAIL_CHANGED', 'GUARDIAN_UNLINKED', 'STUDENT_MAJORITY', 'PAYMENT_CONFIRMED',
                        'CHARGE_CREATED', 'PLAN_RATE_ADJUSTED', 'READJUSTMENT_DIFF', 'ANNOUNCEMENT',
                        'REMINDER_BEFORE', 'REMINDER_DUE', 'REMINDER_OVERDUE', 'BLOCK_WARNING', 'NOTIFICATION_URGENT', 'WELCOME', 'PORTAL_REQUEST',
                        'PROOF_REFUSED'));
