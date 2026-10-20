-- Synthetic plans (RF-PLA-01): three active and one inactive per seeded organization.
insert into plan (id, organization_id, unit_id, modality_id, name, name_key, value_cents, weekly_frequency, valid_other_units, status)
select md5('ctcore-seed-plan-' || o.n || '-' || p.k)::uuid, md5('ctcore-seed-org-' || o.n)::uuid, md5('ctcore-seed-unit-' || o.n)::uuid,
       m.id, p.name, p.name_key, p.value_cents, p.freq, p.other, p.status
from generate_series(1, 3) as o(n)
cross join (values (1, 'Jiu-Jitsu 2x', 'jiu-jitsu 2x', 'Jiu-Jitsu', 15000, 2, false, 'ACTIVE'),
                   (2, 'Jiu-Jitsu Livre', 'jiu-jitsu livre', 'Jiu-Jitsu', 20000, null, true, 'ACTIVE'),
                   (3, 'Judô 3x', 'judo 3x', 'Judô', 18000, 3, false, 'ACTIVE'),
                   (4, 'Judô Antigo', 'judo antigo', 'Judô', 12000, 2, false, 'INACTIVE'))
       as p(k, name, name_key, modality, value_cents, freq, other, status)
join modality m on m.name = p.modality
on conflict do nothing;
