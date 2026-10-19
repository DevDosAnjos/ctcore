-- Synthetic active modalities (RF-MOD-01) for the seeded organizations.
insert into organization_modality (organization_id, modality_id, active)
select md5('ctcore-seed-org-' || n)::uuid, m.id, true
from generate_series(1, 3) as n
join modality m on m.name in ('Jiu-Jitsu', 'Judô', 'Muay Thai')
on conflict do nothing;
