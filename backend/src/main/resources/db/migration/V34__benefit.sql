-- RF-BEN-01, RN-BEN-02/03: benefits (percent scholarship or amount discount) of an enrollment modality, with reason, who decided, who granted and validity.
-- At most one active benefit per enrollment modality; benefits are ended, never deleted (the runtime role has no DELETE). "Expired" is computed on read from valid_until.
-- Revert (manual, no data to lose outside this object): drop table benefit;
create table benefit (
    id uuid primary key,
    organization_id uuid not null references organization(id),
    enrollment_modality_id uuid not null,
    student_id uuid not null,
    unit_id uuid not null,
    type text not null check (type in ('PERCENT', 'AMOUNT')),
    value bigint not null,
    reason text not null check (char_length(btrim(reason)) between 2 and 200),
    decided_by text not null check (char_length(btrim(decided_by)) between 2 and 80),
    granted_by_account_id uuid not null references account(id),
    valid_until date,
    status text not null check (status in ('ACTIVE', 'ENDED')),
    ended_at timestamptz,
    end_reason text,
    created_at timestamptz not null default now(),
    unique (organization_id, id),
    check ((type = 'PERCENT' and value between 1 and 100) or (type = 'AMOUNT' and value > 0)),
    check ((status = 'ENDED') = (ended_at is not null)),
    foreign key (organization_id, enrollment_modality_id) references enrollment_modality(organization_id, id),
    foreign key (organization_id, student_id) references student(organization_id, id),
    foreign key (organization_id, unit_id) references unit(organization_id, id)
);
create unique index benefit_active_modality on benefit (enrollment_modality_id) where status = 'ACTIVE';
create index benefit_org_unit on benefit (organization_id, unit_id, status);
create index benefit_org_student on benefit (organization_id, student_id);

alter table benefit enable row level security;
alter table benefit force row level security;
create policy organization_scope on benefit
    using (organization_id = nullif(current_setting('app.organization_id', true), '')::uuid)
    with check (organization_id = nullif(current_setting('app.organization_id', true), '')::uuid);

-- Same technique as V33: take DELETE back from every grantee other than the owner.
do $$
declare grantee_name text;
begin
    for grantee_name in select distinct grantee from information_schema.role_table_grants
        where table_schema = 'public' and table_name = 'benefit' and privilege_type = 'DELETE'
          and grantee <> current_user and grantee <> 'PUBLIC' loop
        execute format('revoke delete on benefit from %I', grantee_name);
    end loop;
end $$;
