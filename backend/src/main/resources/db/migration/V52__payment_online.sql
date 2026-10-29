-- RF-PAG (#96, RN-PAG-03/04/10..13, RN-COB-15): online payment attempt, its charges, provider notices, duplicate payments and reconciliation alerts.
-- Nothing is deleted: the runtime role has no DELETE on any of these tables. No card data is stored: the provider response keeps only id, state and amount.
-- Revert (manual, no data to lose outside these objects): drop function payment_attempt_owner(text); drop table payment_alert; drop table payment_duplicate;
-- drop table payment_notice; drop table payment_attempt_charge; drop table payment_attempt; and restore notification_type_check (list in V50) without 'PAYMENT_REFUSED'.
create table payment_attempt (
    id uuid primary key,
    organization_id uuid not null references organization(id),
    unit_id uuid not null,
    method text not null check (method in ('PIX', 'CREDIT_CARD', 'DEBIT_CARD', 'BOLETO')),
    amount_cents bigint not null check (amount_cents > 0),
    fee_cents bigint not null default 0 check (fee_cents >= 0),
    provider_id text not null unique,
    status text not null default 'CREATED' check (status in ('CREATED', 'APPROVED', 'REFUSED', 'EXPIRED', 'REFUNDED', 'DISPUTED')),
    provider_response jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now(),
    resolved_at timestamptz,
    unique (organization_id, id),
    foreign key (organization_id, unit_id) references unit(organization_id, id)
);
create index payment_attempt_pending on payment_attempt (created_at) where status = 'CREATED';
create index payment_attempt_by_unit on payment_attempt (organization_id, unit_id, created_at);

create table payment_attempt_charge (
    organization_id uuid not null references organization(id),
    attempt_id uuid not null,
    charge_id uuid not null,
    amount_cents bigint not null check (amount_cents >= 0),
    primary key (attempt_id, charge_id),
    foreign key (organization_id, attempt_id) references payment_attempt(organization_id, id),
    foreign key (organization_id, charge_id) references charge(organization_id, id)
);

-- organization_id is null for a notice whose attempt is unknown (the caller has no session, so nothing says whose it is).
create table payment_notice (
    id uuid primary key,
    organization_id uuid references organization(id),
    provider_id text not null,
    content jsonb not null,
    received_at timestamptz not null default now(),
    processed_at timestamptz,
    result text check (result in ('APPLIED', 'DUPLICATE', 'IGNORED', 'UNKNOWN', 'FAILED'))
);
create index payment_notice_by_provider on payment_notice (provider_id);

-- RN-COB-15: money received twice for one charge. A second payment row is impossible (payment_one_confirmed), so the surplus lives here.
create table payment_duplicate (
    id uuid primary key,
    organization_id uuid not null references organization(id),
    charge_id uuid not null,
    attempt_id uuid not null,
    unit_id uuid not null,
    amount_cents bigint not null check (amount_cents > 0),
    method text not null,
    provider_id text not null,
    status text not null default 'OPEN' check (status in ('OPEN', 'REFUNDED', 'CREDITED')),
    detected_at timestamptz not null default now(),
    unique (attempt_id, charge_id),
    foreign key (organization_id, charge_id) references charge(organization_id, id),
    foreign key (organization_id, attempt_id) references payment_attempt(organization_id, id),
    foreign key (organization_id, unit_id) references unit(organization_id, id)
);

create table payment_alert (
    id uuid primary key,
    organization_id uuid not null references organization(id),
    unit_id uuid not null,
    attempt_id uuid not null,
    kind text not null check (kind in ('DIVERGENCE', 'WEBHOOK_FAILURE')),
    detail text not null,
    created_at timestamptz not null default now(),
    resolved_at timestamptz,
    foreign key (organization_id, attempt_id) references payment_attempt(organization_id, id),
    foreign key (organization_id, unit_id) references unit(organization_id, id)
);
-- An open alert is not repeated for the same attempt and kind.
create unique index payment_alert_open_once on payment_alert (attempt_id, kind) where resolved_at is null;

do $$
declare t text;
begin
    foreach t in array array['payment_attempt', 'payment_attempt_charge', 'payment_duplicate', 'payment_alert'] loop
        execute format('alter table %I enable row level security', t);
        execute format('alter table %I force row level security', t);
        execute format($p$create policy organization_scope on %I
            using (organization_id = nullif(current_setting('app.organization_id', true), '')::uuid)
            with check (organization_id = nullif(current_setting('app.organization_id', true), '')::uuid)$p$, t);
    end loop;
end $$;
alter table payment_notice enable row level security;
alter table payment_notice force row level security;
-- Rows of the organization are visible only inside its scope; a notice of an unknown attempt may be written (organization null) but never read back.
create policy organization_scope on payment_notice
    using (organization_id = nullif(current_setting('app.organization_id', true), '')::uuid)
    with check (organization_id is null or organization_id = nullif(current_setting('app.organization_id', true), '')::uuid);

-- Same technique as V39: take DELETE back from every grantee other than the owner.
do $$
declare grantee_name text; table_name_ text;
begin
    for table_name_ in select unnest(array['payment_attempt', 'payment_attempt_charge', 'payment_notice', 'payment_duplicate', 'payment_alert']) loop
        for grantee_name in select distinct grantee from information_schema.role_table_grants
            where table_schema = 'public' and table_name = table_name_ and privilege_type = 'DELETE'
              and grantee <> current_user and grantee <> 'PUBLIC' loop
            execute format('revoke delete on %I from %I', table_name_, grantee_name);
        end loop;
    end loop;
end $$;

-- The provider notice has no session. This function (owner rights, as public_receipt in V39) is the only way in and returns just the owner organization.
create function payment_attempt_owner(p_provider_id text)
returns uuid
language sql stable security definer set search_path = public, pg_temp as $$
    select organization_id from payment_attempt where provider_id = p_provider_id
$$;

alter table notification drop constraint notification_type_check;
alter table notification add constraint notification_type_check
    check (type in ('PAYMENT_RECEIVED', 'HIGH_BENEFIT', 'STUDENT_MAJORITY', 'EMAIL_PROBLEM', 'INVITE_PENDING',
        'CLOSURE_REQUEST', 'MP_DISCONNECTED', 'SUBSCRIPTION_OVERDUE', 'PORTAL_REQUEST', 'PAYMENT_PROOF', 'PAYMENT_REFUSED'));
