-- RF-MAT-05, RN-MAT-14: published versions of the enrollment and image-use terms of an organization. A published version never changes (RN-MAT-12 cites it): the runtime role has no UPDATE and no DELETE.
-- The platform default text (version 0) is virtual and has no row; the first publication is version 1.
-- Revert (manual, no data to lose outside this object): drop table terms_version;
create table terms_version (
    id uuid primary key,
    organization_id uuid not null references organization(id),
    kind text not null check (kind in ('ENROLLMENT', 'IMAGE_USE')),
    version int not null check (version > 0),
    body text not null check (char_length(btrim(body)) between 20 and 20000),
    reaccept boolean not null,
    published_by_account_id uuid not null references account(id),
    published_at timestamptz not null default now(),
    -- Race guard: two simultaneous publications of the same kind cannot share a number (the code answers 409).
    unique (organization_id, kind, version)
);

alter table terms_version enable row level security;
alter table terms_version force row level security;
create policy organization_scope on terms_version
    using (organization_id = nullif(current_setting('app.organization_id', true), '')::uuid)
    with check (organization_id = nullif(current_setting('app.organization_id', true), '')::uuid);

-- Same technique as V34: take UPDATE and DELETE back from every grantee other than the owner.
do $$
declare grantee_name text;
begin
    for grantee_name in select distinct grantee from information_schema.role_table_grants
        where table_schema = 'public' and table_name = 'terms_version' and privilege_type in ('DELETE', 'UPDATE')
          and grantee <> current_user and grantee <> 'PUBLIC' loop
        execute format('revoke update, delete on terms_version from %I', grantee_name);
    end loop;
end $$;
