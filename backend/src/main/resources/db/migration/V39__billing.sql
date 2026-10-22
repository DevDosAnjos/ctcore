-- RF-COB, RN-COB-03/05/12/16: charges (the amount composition is stored), payments (one confirmed payment per charge) and receipts (number per unit without gaps, public code).
-- Nothing is deleted: the runtime role has no DELETE on any of these tables, and payment and receipt have no UPDATE either. The charge status stored is only the one that
-- changes by action; Pending, Due today, Overdue and Paid late are computed from the date and the payment (never stored).
-- Revert (manual, no data to lose outside these objects): drop function public_receipt(text); alter table enrollment_modality drop column next_charge_ref, drop column next_charge_due_on;
-- drop table receipt; drop table receipt_counter; drop table payment; drop table charge;
create table charge (
    id uuid primary key,
    organization_id uuid not null references organization(id),
    unit_id uuid not null,
    student_id uuid not null,
    payer_guardian_id uuid,
    type text not null check (type in ('MONTHLY', 'ENROLLMENT_FEE', 'ONE_OFF', 'INSTALLMENT', 'READJUSTMENT_DIFF')),
    enrollment_modality_id uuid,
    enrollment_start_id uuid,
    plan_id uuid,
    benefit_id uuid,
    reference_month date check (reference_month is null or extract(day from reference_month) = 1),
    description text check (description is null or char_length(btrim(description)) between 1 and 80),
    due_on date not null,
    tolerance_days int not null default 0 check (tolerance_days >= 0),
    plan_cents bigint not null default 0 check (plan_cents >= 0),
    benefit_cents bigint not null default 0 check (benefit_cents >= 0),
    amount_cents bigint not null check (amount_cents >= 0),
    status text not null default 'OPEN' check (status in ('OPEN', 'IN_REVIEW', 'PAID', 'CANCELED', 'RENEGOTIATED', 'REFUNDED')),
    origin_charge_id uuid,
    origin text,
    reason text,
    created_by_account_id uuid references account(id),
    created_at timestamptz not null default now(),
    unique (organization_id, id),
    foreign key (organization_id, unit_id) references unit(organization_id, id),
    foreign key (organization_id, student_id) references student(organization_id, id),
    foreign key (organization_id, payer_guardian_id) references guardian(organization_id, id),
    foreign key (organization_id, enrollment_modality_id) references enrollment_modality(organization_id, id),
    foreign key (organization_id, enrollment_start_id) references enrollment_start(organization_id, id),
    foreign key (organization_id, plan_id) references plan(organization_id, id),
    foreign key (organization_id, benefit_id) references benefit(organization_id, id),
    foreign key (organization_id, origin_charge_id) references charge(organization_id, id)
);
-- RN-COB-05: one monthly charge per modality of the enrollment and reference month, in any status (a canceled one is not recreated).
create unique index charge_monthly_once on charge (enrollment_modality_id, reference_month) where type = 'MONTHLY';
-- RN-MAT-19: never two fees for the same enrollment start.
create unique index charge_fee_once on charge (enrollment_start_id) where type = 'ENROLLMENT_FEE';
create index charge_by_unit on charge (organization_id, unit_id, status, due_on);
create index charge_by_student on charge (organization_id, student_id);

create table payment (
    id uuid primary key,
    organization_id uuid not null references organization(id),
    charge_id uuid not null,
    student_id uuid not null,
    unit_id uuid not null,
    amount_cents bigint not null check (amount_cents >= 0),
    fee_passed_cents bigint not null default 0 check (fee_passed_cents >= 0),
    method text not null check (method in ('PIX_MP', 'CREDIT_CARD', 'DEBIT_CARD', 'BOLETO', 'PIX_KEY', 'CASH', 'CARD_MACHINE', 'TRANSFER')),
    origin text not null check (origin in ('MANAGEMENT', 'PORTAL', 'AUTO_DEBIT')),
    paid_at timestamptz not null,
    registered_by_account_id uuid references account(id),
    observation text check (observation is null or char_length(observation) <= 200),
    external_id text,
    status text not null default 'CONFIRMED' check (status in ('CONFIRMED', 'REFUNDED')),
    created_at timestamptz not null default now(),
    unique (organization_id, id),
    foreign key (organization_id, charge_id) references charge(organization_id, id),
    foreign key (organization_id, student_id) references student(organization_id, id),
    foreign key (organization_id, unit_id) references unit(organization_id, id)
);
-- RNF-12: the same charge never has two confirmed payments, and a provider notice repeated in the unit is refused.
create unique index payment_one_confirmed on payment (charge_id) where status = 'CONFIRMED';
create unique index payment_external_once on payment (unit_id, external_id) where external_id is not null;

-- Last receipt number of each unit: updated in the payment transaction, so the row lock serializes the payments of the unit (no gap, no repeat).
create table receipt_counter (
    unit_id uuid primary key,
    organization_id uuid not null references organization(id),
    last_number int not null,
    foreign key (organization_id, unit_id) references unit(organization_id, id)
);

create table receipt (
    id uuid primary key,
    organization_id uuid not null references organization(id),
    payment_id uuid not null unique,
    unit_id uuid not null,
    number int not null check (number >= 1),
    code text not null check (code ~ '^[A-Z0-9]{16}$'),
    created_at timestamptz not null default now(),
    foreign key (organization_id, payment_id) references payment(organization_id, id),
    foreign key (organization_id, unit_id) references unit(organization_id, id),
    unique (unit_id, number),
    unique (code)
);

-- RN-MAT-07, RN-COB-01: pointer of the next monthly charge of the modality (null on rows before this item).
alter table enrollment_modality add column next_charge_ref date, add column next_charge_due_on date;

do $$
declare t text;
begin
    foreach t in array array['charge', 'payment', 'receipt_counter', 'receipt'] loop
        execute format('alter table %I enable row level security', t);
        execute format('alter table %I force row level security', t);
        execute format($p$create policy organization_scope on %I
            using (organization_id = nullif(current_setting('app.organization_id', true), '')::uuid)
            with check (organization_id = nullif(current_setting('app.organization_id', true), '')::uuid)$p$, t);
    end loop;
end $$;

-- Same technique as V33/V31: take DELETE (and UPDATE on payment and receipt) back from every grantee other than the owner.
do $$
declare grantee_name text; table_name_ text;
begin
    for table_name_ in select unnest(array['charge', 'payment', 'receipt_counter', 'receipt']) loop
        for grantee_name in select distinct grantee from information_schema.role_table_grants
            where table_schema = 'public' and table_name = table_name_ and privilege_type = 'DELETE'
              and grantee <> current_user and grantee <> 'PUBLIC' loop
            execute format('revoke delete on %I from %I', table_name_, grantee_name);
        end loop;
    end loop;
    for table_name_ in select unnest(array['payment', 'receipt']) loop
        for grantee_name in select distinct grantee from information_schema.role_table_grants
            where table_schema = 'public' and table_name = table_name_ and privilege_type = 'UPDATE'
              and grantee <> current_user and grantee <> 'PUBLIC' loop
            execute format('revoke update on %I from %I', table_name_, grantee_name);
        end loop;
    end loop;
end $$;

-- RN-COB-16: the public receipt page has no session. This function (owner rights, the owner bypasses RLS, as active_organization_ids) is the only way in,
-- and returns just what the page shows: unit, number, reference, amount, date and the initials of who paid.
create function public_receipt(p_code text)
returns table (unit_name text, number integer, reference text, amount_cents bigint, paid_at timestamptz, payer_initials text)
language sql stable security definer set search_path = public, pg_temp as $$
    select u.name, r.number,
           case when c.type = 'MONTHLY' and c.reference_month is not null
                then m.name || ' · ' || (array['jan','fev','mar','abr','mai','jun','jul','ago','set','out','nov','dez'])[extract(month from c.reference_month)::int]
                     || '/' || extract(year from c.reference_month)::int
                else coalesce(c.description, '') end,
           p.amount_cents, p.paid_at,
           upper(left(w[1], 1)) || '.' || case when cardinality(w) > 1 then ' ' || upper(left(w[cardinality(w)], 1)) || '.' else '' end
    from receipt r
    join payment p on p.id = r.payment_id
    join charge c on c.id = p.charge_id
    join unit u on u.id = r.unit_id
    join student s on s.id = c.student_id
    left join enrollment_modality em on em.id = c.enrollment_modality_id
    left join modality m on m.id = em.modality_id
    left join guardian g on g.id = c.payer_guardian_id
    cross join lateral regexp_split_to_array(btrim(coalesce(g.name, s.name)), '\s+') w
    where r.code = p_code
$$;
