alter table organization_feature enable row level security;
alter table organization_feature force row level security;
create policy organization_scope on organization_feature
    using (organization_id = nullif(current_setting('app.organization_id', true), '')::uuid)
    with check (organization_id = nullif(current_setting('app.organization_id', true), '')::uuid);
