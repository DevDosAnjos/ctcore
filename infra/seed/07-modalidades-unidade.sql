-- Synthetic modalities offered by the seeded units (RF-MOD-03): Jiu-Jitsu and Judô in every seeded unit.
insert into unit_modality (organization_id, unit_id, modality_id, offered)
select u.organization_id, u.id, m.id, true
from unit u
join modality m on m.name in ('Jiu-Jitsu', 'Judô')
where u.organization_id in (select md5('ctcore-seed-org-' || n)::uuid from generate_series(1, 3) as n)
on conflict do nothing;
