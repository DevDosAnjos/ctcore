-- Synthetic pilot and tenants; preserve every previously saved value.
insert into organization (id, name)
select md5('ctcore-seed-org-' || n)::uuid,
    case when n = 1 then 'Academia Piloto Fictícia' else 'Academia Fictícia ' || n end
from generate_series(1, 3) as n
on conflict do nothing;

insert into unit (id, organization_id, name, short_name)
select md5('ctcore-seed-unit-' || n)::uuid, md5('ctcore-seed-org-' || n)::uuid, 'Unidade Principal', 'ficticia-' || n
from generate_series(1, 3) as n
on conflict do nothing;

insert into organization_settings (organization_id)
select md5('ctcore-seed-org-' || n)::uuid from generate_series(1, 3) as n
on conflict do nothing;

insert into unit_settings (organization_id, unit_id)
select md5('ctcore-seed-org-' || n)::uuid, md5('ctcore-seed-unit-' || n)::uuid from generate_series(1, 3) as n
on conflict do nothing;

insert into organization_limits (organization_id)
select md5('ctcore-seed-org-' || n)::uuid from generate_series(1, 3) as n
on conflict do nothing;
