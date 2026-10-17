-- RN-ACE-01..14, 19, 20: one account per person, links to organizations, invites, resets, sessions, login attempts.
create table account (
    id uuid primary key default gen_random_uuid(),
    email text not null check (btrim(email) <> ''),
    name text,
    phone text,
    password_hash text,
    status text not null check (status in ('ACTIVE', 'INVITED', 'DEACTIVATED')),
    created_at timestamptz not null default now()
);
create unique index account_email_unique on account (lower(email));

create table account_link (
    id uuid primary key default gen_random_uuid(),
    account_id uuid not null references account(id),
    organization_id uuid not null references organization(id),
    role text not null check (role in ('COORDINATOR', 'ADMINISTRATOR', 'TEACHER', 'STUDENT', 'GUARDIAN')),
    status text not null check (status in ('PENDING', 'ACTIVE', 'ENDED')),
    starts_on date,
    ends_on date,
    unique (account_id, organization_id, role),
    unique (organization_id, id)
);
create index account_link_account on account_link (account_id);

create table account_link_unit (
    link_id uuid not null,
    organization_id uuid not null,
    unit_id uuid not null,
    primary key (link_id, unit_id),
    foreign key (organization_id, link_id) references account_link(organization_id, id),
    foreign key (organization_id, unit_id) references unit(organization_id, id)
);

create table account_invite (
    id uuid primary key default gen_random_uuid(),
    account_id uuid not null references account(id),
    link_id uuid not null,
    organization_id uuid not null references organization(id),
    code_hash text not null unique,
    expires_at timestamptz not null,
    used_at timestamptz,
    canceled_at timestamptz,
    created_at timestamptz not null default now(),
    sent_by uuid references account(id),
    resend boolean not null default false,
    foreign key (organization_id, link_id) references account_link(organization_id, id)
);
create index account_invite_account on account_invite (account_id, created_at);

create table password_reset (
    id uuid primary key default gen_random_uuid(),
    account_id uuid not null references account(id),
    code_hash text not null unique,
    expires_at timestamptz not null,
    used_at timestamptz,
    canceled_at timestamptz,
    created_at timestamptz not null default now()
);
create index password_reset_account on password_reset (account_id);

create table account_session (
    id uuid primary key default gen_random_uuid(),
    account_id uuid not null references account(id),
    token_hash text not null unique,
    active_link_id uuid references account_link(id),
    device text,
    ip text,
    created_at timestamptz not null default now(),
    last_used_at timestamptz not null default now(),
    expires_at timestamptz not null
);
create index account_session_account on account_session (account_id);

create table login_attempt (
    id bigint generated always as identity primary key,
    email text not null,
    ip text,
    occurred_at timestamptz not null,
    result text not null check (result in ('SUCCESS', 'WRONG_PASSWORD', 'LOCKED', 'IP_LIMITED', 'CHALLENGE_FAILED'))
);
create index login_attempt_email on login_attempt (email, occurred_at desc);
create index login_attempt_ip on login_attempt (ip, occurred_at desc);

-- Three scopes: organization of the context, account of the transaction, invite code of the transaction.
alter table account_link enable row level security;
alter table account_link force row level security;
create policy identity_scope on account_link
    using (organization_id = nullif(current_setting('app.organization_id', true), '')::uuid
        or account_id = nullif(current_setting('app.account_id', true), '')::uuid
        or exists (select 1 from account_invite i where i.link_id = account_link.id
            and i.code_hash = nullif(current_setting('app.invite_code_hash', true), '')))
    with check (organization_id = nullif(current_setting('app.organization_id', true), '')::uuid
        or account_id = nullif(current_setting('app.account_id', true), '')::uuid);

alter table account_invite enable row level security;
alter table account_invite force row level security;
create policy identity_scope on account_invite
    using (organization_id = nullif(current_setting('app.organization_id', true), '')::uuid
        or account_id = nullif(current_setting('app.account_id', true), '')::uuid
        or code_hash = nullif(current_setting('app.invite_code_hash', true), ''))
    with check (organization_id = nullif(current_setting('app.organization_id', true), '')::uuid
        or account_id = nullif(current_setting('app.account_id', true), '')::uuid);

alter table account_link_unit enable row level security;
alter table account_link_unit force row level security;
create policy identity_scope on account_link_unit
    using (organization_id = nullif(current_setting('app.organization_id', true), '')::uuid
        or exists (select 1 from account_link l where l.id = account_link_unit.link_id))
    with check (organization_id = nullif(current_setting('app.organization_id', true), '')::uuid);
