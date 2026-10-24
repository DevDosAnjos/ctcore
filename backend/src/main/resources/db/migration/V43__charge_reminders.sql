-- RF-COB-06, RN-COB-17, RN-COB-20 (#79): lembretes de cobrança. A unidade liga ou desliga os automáticos e escolhe a hora; charge_reminder é o histórico
-- (e a idempotência dos automáticos); sms_log conta os SMS por aluno (limite mensal); email_message guarda quando o e-mail foi aberto.
-- Sem DELETE nem UPDATE para o papel operacional em charge_reminder e sms_log.
-- Revert (manual): drop table sms_log; drop table charge_reminder; alter table email_message drop column opened_at; restore the template check of email_message (V42 text);
-- alter table unit_settings drop column reminders_hour, drop column reminders_enabled.
alter table unit_settings add column reminders_enabled boolean not null default true,
    add column reminders_hour smallint not null default 9 check (reminders_hour between 0 and 23);

alter table email_message add column opened_at timestamptz;
alter table email_message drop constraint email_message_template_check;
alter table email_message add constraint email_message_template_check
    check (template in ('INVITE', 'PASSWORD_RESET', 'PASSWORD_CHANGED', 'NEW_ACCESS', 'COORDINATION_TRANSFERRED',
                        'EMAIL_CHANGE', 'EMAIL_CHANGED', 'GUARDIAN_UNLINKED', 'STUDENT_MAJORITY', 'PAYMENT_CONFIRMED',
                        'CHARGE_CREATED', 'PLAN_RATE_ADJUSTED', 'READJUSTMENT_DIFF', 'ANNOUNCEMENT',
                        'REMINDER_BEFORE', 'REMINDER_DUE', 'REMINDER_OVERDUE', 'BLOCK_WARNING'));

create table charge_reminder (
    id uuid primary key,
    organization_id uuid not null references organization(id),
    charge_id uuid not null,
    kind text not null check (kind in ('SCHEDULED', 'BLOCK_WARNING', 'MANUAL')),
    offset_days int,
    channel text not null check (channel in ('EMAIL', 'SMS')),
    status text not null check (status in ('SENT', 'SKIPPED')),
    reason text check (reason in ('NO_ADDRESS', 'EMAIL_PROBLEM', 'NO_PHONE', 'SMS_LIMIT')),
    email_message_id uuid references email_message(id),
    sent_by_account_id uuid references account(id),
    created_at timestamptz not null default now(),
    unique (organization_id, id),
    foreign key (organization_id, charge_id) references charge(organization_id, id)
);
create unique index charge_reminder_once on charge_reminder (charge_id, kind, coalesce(offset_days, 0), channel) where kind <> 'MANUAL';
create index charge_reminder_by_charge on charge_reminder (organization_id, charge_id, created_at desc);

create table sms_log (
    id uuid primary key,
    organization_id uuid not null references organization(id),
    student_id uuid not null,
    kind text not null check (kind in ('BLOCK_WARNING')),
    created_at timestamptz not null default now(),
    foreign key (organization_id, student_id) references student(organization_id, id)
);
create index sms_log_by_student on sms_log (organization_id, student_id, created_at);

do $$
declare t text; grantee_name text;
begin
    foreach t in array array['charge_reminder', 'sms_log'] loop
        execute format('alter table %I enable row level security', t);
        execute format('alter table %I force row level security', t);
        execute format($p$create policy organization_scope on %I
            using (organization_id = nullif(current_setting('app.organization_id', true), '')::uuid)
            with check (organization_id = nullif(current_setting('app.organization_id', true), '')::uuid)$p$, t);
        for grantee_name in select distinct grantee from information_schema.role_table_grants
            where table_schema = 'public' and table_name = t and privilege_type in ('DELETE', 'UPDATE')
              and grantee <> current_user and grantee <> 'PUBLIC' loop
            execute format('revoke delete, update on %I from %I', t, grantee_name);
        end loop;
    end loop;
end $$;
