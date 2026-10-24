-- RF-NOT-01 (RN-NOT-01..04, #84): notificacao por pessoa (vinculo) e a preferencia "receber tudo" do Coordenador.
-- Sem limpeza automatica neste item (D7).
-- Revert (manual): drop table notification_preference; drop table notification;
create table notification (
    id uuid primary key,
    organization_id uuid not null references organization(id),
    link_id uuid not null,
    unit_id uuid,
    type text not null check (type in ('PAYMENT_RECEIVED', 'HIGH_BENEFIT', 'STUDENT_MAJORITY', 'EMAIL_PROBLEM', 'INVITE_PENDING',
        'CLOSURE_REQUEST', 'MP_DISCONNECTED', 'SUBSCRIPTION_OVERDUE')),
    ref_type text,
    ref_id uuid,
    params jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now(),
    read_at timestamptz,
    foreign key (organization_id, link_id) references account_link(organization_id, id)
);
-- R4: a mesma notificacao nao se repete para a mesma pessoa.
create unique index notification_once on notification (link_id, type, ref_id) where ref_id is not null;
create index notification_link_created on notification (link_id, created_at desc);

create table notification_preference (
    organization_id uuid not null references organization(id),
    link_id uuid not null,
    receive_all boolean not null default false,
    updated_at timestamptz not null default now(),
    primary key (organization_id, link_id),
    foreign key (organization_id, link_id) references account_link(organization_id, id)
);

alter table notification enable row level security;
alter table notification force row level security;
create policy organization_scope on notification
    using (organization_id = nullif(current_setting('app.organization_id', true), '')::uuid)
    with check (organization_id = nullif(current_setting('app.organization_id', true), '')::uuid);

alter table notification_preference enable row level security;
alter table notification_preference force row level security;
create policy organization_scope on notification_preference
    using (organization_id = nullif(current_setting('app.organization_id', true), '')::uuid)
    with check (organization_id = nullif(current_setting('app.organization_id', true), '')::uuid);

-- Sem DELETE para o papel operacional (as default privileges o dao).
do $$
declare grantee_name text;
begin
    for grantee_name in select distinct grantee from information_schema.role_table_grants
        where table_schema = 'public' and table_name in ('notification', 'notification_preference') and privilege_type = 'DELETE'
          and grantee <> current_user and grantee <> 'PUBLIC' loop
        execute format('revoke delete on notification, notification_preference from %I', grantee_name);
    end loop;
end $$;

-- RN-NOT-02: o e-mail urgente entra nos modelos permitidos (lista da V43 mais NOTIFICATION_URGENT).
alter table email_message drop constraint email_message_template_check;
alter table email_message add constraint email_message_template_check
    check (template in ('INVITE', 'PASSWORD_RESET', 'PASSWORD_CHANGED', 'NEW_ACCESS', 'COORDINATION_TRANSFERRED',
                        'EMAIL_CHANGE', 'EMAIL_CHANGED', 'GUARDIAN_UNLINKED', 'STUDENT_MAJORITY', 'PAYMENT_CONFIRMED',
                        'CHARGE_CREATED', 'PLAN_RATE_ADJUSTED', 'READJUSTMENT_DIFF', 'ANNOUNCEMENT',
                        'REMINDER_BEFORE', 'REMINDER_DUE', 'REMINDER_OVERDUE', 'BLOCK_WARNING', 'NOTIFICATION_URGENT'));
