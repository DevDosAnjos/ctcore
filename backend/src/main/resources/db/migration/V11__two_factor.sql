-- RN-ACE-17, RN-ACE-18, RN-ACE-21: duas etapas (TOTP), códigos de reserva, bilhete de login e confirmação de ação crítica.
-- Desfazer: nova migração que remove estas quatro tabelas (nenhuma outra depende delas).
create table account_two_factor (
    account_id uuid primary key references account(id) on delete cascade,
    secret_encrypted text not null,
    enabled_at timestamptz,
    last_used_step bigint,
    created_at timestamptz not null default now()
);

create table account_backup_code (
    id uuid primary key default gen_random_uuid(),
    account_id uuid not null references account(id) on delete cascade,
    code_hash text not null,
    used_at timestamptz,
    created_at timestamptz not null default now()
);
create index account_backup_code_account on account_backup_code (account_id);

create table login_ticket (
    id uuid primary key default gen_random_uuid(),
    account_id uuid not null references account(id) on delete cascade,
    token_hash text not null unique,
    expires_at timestamptz not null,
    used_at timestamptz,
    failed_attempts int not null default 0,
    created_at timestamptz not null default now()
);

create table reauth_token (
    id uuid primary key default gen_random_uuid(),
    account_id uuid not null references account(id) on delete cascade,
    session_id uuid not null references account_session(id) on delete cascade,
    action text not null check (action in ('TRANSFER_COORDINATION', 'CANCEL_SUBSCRIPTION', 'FULL_EXPORT',
        'DISCONNECT_MERCADO_PAGO', 'ANONYMIZE_STUDENT', 'DEACTIVATE_UNIT')),
    token_hash text not null unique,
    expires_at timestamptz not null,
    used_at timestamptz,
    created_at timestamptz not null default now()
);

alter table login_attempt drop constraint login_attempt_result_check;
alter table login_attempt add constraint login_attempt_result_check
    check (result in ('SUCCESS', 'WRONG_PASSWORD', 'LOCKED', 'IP_LIMITED', 'CHALLENGE_FAILED', 'RESET_REQUEST', 'WRONG_CODE'));

-- Escopo da conta (app.account_id), como a V6; o bilhete também é achado pelo hash dele (app.invite_code_hash).
alter table account_two_factor enable row level security;
alter table account_two_factor force row level security;
create policy account_scope on account_two_factor
    using (account_id = nullif(current_setting('app.account_id', true), '')::uuid)
    with check (account_id = nullif(current_setting('app.account_id', true), '')::uuid);

alter table account_backup_code enable row level security;
alter table account_backup_code force row level security;
create policy account_scope on account_backup_code
    using (account_id = nullif(current_setting('app.account_id', true), '')::uuid)
    with check (account_id = nullif(current_setting('app.account_id', true), '')::uuid);

alter table reauth_token enable row level security;
alter table reauth_token force row level security;
create policy account_scope on reauth_token
    using (account_id = nullif(current_setting('app.account_id', true), '')::uuid)
    with check (account_id = nullif(current_setting('app.account_id', true), '')::uuid);

alter table login_ticket enable row level security;
alter table login_ticket force row level security;
create policy account_scope on login_ticket
    using (account_id = nullif(current_setting('app.account_id', true), '')::uuid
        or token_hash = nullif(current_setting('app.invite_code_hash', true), ''))
    with check (account_id = nullif(current_setting('app.account_id', true), '')::uuid);
