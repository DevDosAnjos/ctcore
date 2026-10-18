-- RN-ACE-15: pedido de troca de e-mail, pendente até o link ser confirmado.
-- Desfazer: nova migração com `drop table account_email_change`.
create table account_email_change (
    id uuid primary key default gen_random_uuid(),
    account_id uuid not null references account(id) on delete cascade,
    new_email text not null check (btrim(new_email) <> ''),
    code_hash text not null unique,
    expires_at timestamptz not null,
    used_at timestamptz,
    canceled_at timestamptz,
    created_at timestamptz not null default now()
);
create index account_email_change_account on account_email_change (account_id, created_at desc);

alter table account_email_change enable row level security;
alter table account_email_change force row level security;
create policy account_scope on account_email_change
    using (account_id = nullif(current_setting('app.account_id', true), '')::uuid)
    with check (account_id = nullif(current_setting('app.account_id', true), '')::uuid);

-- Os dois e-mails novos entram na lista de modelos permitidos (como a V16 fez para a transferência de coordenação).
alter table email_message drop constraint email_message_template_check;
alter table email_message add constraint email_message_template_check
    check (template in ('INVITE', 'PASSWORD_RESET', 'PASSWORD_CHANGED', 'NEW_ACCESS', 'COORDINATION_TRANSFERRED',
        'EMAIL_CHANGE', 'EMAIL_CHANGED'));
