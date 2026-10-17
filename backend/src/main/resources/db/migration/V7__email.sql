-- RNF-14 (#19): every e-mail is stored before it is sent; RN-ACE-22: known devices per account.
-- Neither table is organization data (like account_session): no organization_id, no RLS, no route reads them.
create table email_message (
    id uuid primary key default gen_random_uuid(),
    to_email text not null,
    template text not null check (template in ('INVITE', 'PASSWORD_RESET', 'PASSWORD_CHANGED', 'NEW_ACCESS')),
    subject text not null,
    -- Cleared once sent, and on final failure of templates that carry a code (D3).
    body text,
    status text not null check (status in ('PENDING', 'SENT', 'FAILED', 'DISABLED')),
    attempts integer not null default 0,
    next_attempt_at timestamptz,
    last_error text,
    created_at timestamptz not null default now(),
    sent_at timestamptz
);
create index email_message_due on email_message (status, next_attempt_at);

create table account_device (
    account_id uuid not null references account(id) on delete cascade,
    device_hash text not null,
    first_seen_at timestamptz not null,
    last_seen_at timestamptz not null,
    primary key (account_id, device_hash)
);
