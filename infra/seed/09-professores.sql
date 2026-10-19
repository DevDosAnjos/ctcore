-- Synthetic teachers (RF-PRO-01): three per seeded organization, one inactive; the first of organization 1 uses the seeded teacher account.
insert into teacher (id, organization_id, name, name_key, email, phone, directory_consent, consent_declared_by_management, status, account_id)
select md5('ctcore-seed-teacher-' || o.n || '-' || t.k)::uuid, md5('ctcore-seed-org-' || o.n)::uuid, t.name, t.name_key,
       t.k || '.professor' || o.n || '@ficticia.example', '+55 11 90000-000' || t.k, t.k = 1, t.k = 1, t.status,
       case when o.n = 1 and t.k = 1 then md5('ctcore-seed-account-2')::uuid end
from generate_series(1, 3) as o(n)
cross join (values (1, 'João Fictício', 'joao ficticio', 'ACTIVE'), (2, 'Maria Fictícia', 'maria ficticia', 'ACTIVE'),
                   (3, 'Carlos Fictício', 'carlos ficticio', 'INACTIVE')) as t(k, name, name_key, status)
on conflict do nothing;

insert into teacher_unit (organization_id, teacher_id, unit_id)
select md5('ctcore-seed-org-' || o.n)::uuid, md5('ctcore-seed-teacher-' || o.n || '-' || t.k)::uuid, md5('ctcore-seed-unit-' || o.n)::uuid
from generate_series(1, 3) as o(n) cross join generate_series(1, 3) as t(k)
on conflict do nothing;

-- Jiu-Jitsu with the purple belt (when the organization has the level) for everyone; Judô without graduation for the second teacher.
insert into teacher_modality (organization_id, teacher_id, modality_id, level_id, degree)
select md5('ctcore-seed-org-' || o.n)::uuid, md5('ctcore-seed-teacher-' || o.n || '-' || t.k)::uuid, m.id, g.id, 1
from generate_series(1, 3) as o(n) cross join generate_series(1, 3) as t(k)
join modality m on m.name = 'Jiu-Jitsu'
left join graduation_level g on g.organization_id = md5('ctcore-seed-org-' || o.n)::uuid and g.modality_id = m.id and g.name = 'Roxa'
on conflict do nothing;

insert into teacher_modality (organization_id, teacher_id, modality_id)
select md5('ctcore-seed-org-' || o.n)::uuid, md5('ctcore-seed-teacher-' || o.n || '-2')::uuid, m.id
from generate_series(1, 3) as o(n) join modality m on m.name = 'Judô'
on conflict do nothing;
