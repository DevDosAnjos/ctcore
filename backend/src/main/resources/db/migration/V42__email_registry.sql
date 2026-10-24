-- RN-COM-11, RN-COM-13, RN-COM-14, RN-ALU-10 (#78): the e-mail registry. A message keeps its organization (null for account e-mails), may be SKIPPED
-- (reason in last_error: OPT_OUT or EMAIL_PROBLEM) or BOUNCED; the link keeps the announcements choice; the charge keeps when its notice was sent.
-- email_message stays without RLS: the retry job reads every organization and no route reads the table (D8).
-- Revert (manual): alter table charge drop column notified_at; alter table account_link drop column announcement_email;
-- alter table email_message drop column organization_id; restore both checks of email_message (V41 text for template, V7 text for status).
alter table email_message add column organization_id uuid references organization(id);

alter table email_message drop constraint email_message_template_check;
alter table email_message add constraint email_message_template_check
    check (template in ('INVITE', 'PASSWORD_RESET', 'PASSWORD_CHANGED', 'NEW_ACCESS', 'COORDINATION_TRANSFERRED',
                        'EMAIL_CHANGE', 'EMAIL_CHANGED', 'GUARDIAN_UNLINKED', 'STUDENT_MAJORITY', 'PAYMENT_CONFIRMED',
                        'CHARGE_CREATED', 'PLAN_RATE_ADJUSTED', 'READJUSTMENT_DIFF', 'ANNOUNCEMENT'));
alter table email_message drop constraint email_message_status_check;
alter table email_message add constraint email_message_status_check
    check (status in ('PENDING', 'SENT', 'FAILED', 'DISABLED', 'SKIPPED', 'BOUNCED'));

alter table account_link add column announcement_email boolean not null default true;

-- Charges that already exist are born as notified (default now()), then the column goes back to null-able without default.
alter table charge add column notified_at timestamptz not null default now();
alter table charge alter column notified_at drop default;
alter table charge alter column notified_at drop not null;
create index charge_to_notify on charge (organization_id, due_on) where notified_at is null;
