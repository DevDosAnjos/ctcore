-- Synthetic age ranges (RF-MOD-04): the three defaults plus two extras for the seeded organizations.
insert into age_range (id, organization_id, name, name_key, min_age, max_age)
select md5('ctcore-seed-age-' || n || '-' || d.name_key)::uuid, md5('ctcore-seed-org-' || n)::uuid, d.name, d.name_key, d.min_age, d.max_age
from generate_series(1, 3) as n
cross join (values ('Infantil', 'infantil', 4, 12), ('Juvenil', 'juvenil', 13, 17), ('Adulto', 'adulto', 18, null),
                   ('Master', 'master', 35, null), ('Kids competição', 'kids competicao', 4, 12)) as d(name, name_key, min_age, max_age)
on conflict do nothing;
