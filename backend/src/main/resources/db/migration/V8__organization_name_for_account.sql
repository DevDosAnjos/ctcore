-- RF-ACE-05: the account scope may read (only read) the organizations of its own active links, to show their names.
create policy organization_of_account_link on organization for select
    using (exists (select 1 from account_link l
        where l.organization_id = organization.id and l.status = 'ACTIVE'
        and l.account_id = nullif(current_setting('app.account_id', true), '')::uuid));
