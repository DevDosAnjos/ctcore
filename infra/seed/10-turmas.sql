-- Synthetic classes (RF-TUR-01): three per seeded organization, one inactive. No students are created (enrollment comes in Sprint 07).
insert into training_class (id, organization_id, unit_id, name, name_key, modality_id, age_range_id, capacity, head_teacher_id, status)
select md5('ctcore-seed-class-' || o.n || '-' || c.k)::uuid, md5('ctcore-seed-org-' || o.n)::uuid, md5('ctcore-seed-unit-' || o.n)::uuid,
       c.name, c.name_key, m.id, a.id, c.capacity, md5('ctcore-seed-teacher-' || o.n || '-' || c.head)::uuid, c.status
from generate_series(1, 3) as o(n)
cross join (values (1, 'Jiu-Jitsu Adulto', 'jiu-jitsu adulto', 'Jiu-Jitsu', 'adulto', 30, 1, 'ACTIVE'),
                   (2, 'Judô Infantil', 'judo infantil', 'Judô', 'infantil', 20, 2, 'ACTIVE'),
                   (3, 'Jiu-Jitsu Avançado', 'jiu-jitsu avancado', 'Jiu-Jitsu', null, null, 1, 'INACTIVE'))
       as c(k, name, name_key, modality, age_key, capacity, head, status)
join modality m on m.name = c.modality
left join age_range a on a.organization_id = md5('ctcore-seed-org-' || o.n)::uuid and a.name_key = c.age_key
on conflict do nothing;

insert into training_class_schedule (organization_id, class_id, weekday, starts_at, ends_at)
select md5('ctcore-seed-org-' || o.n)::uuid, md5('ctcore-seed-class-' || o.n || '-' || s.k)::uuid, s.weekday, s.starts_at::time, s.ends_at::time
from generate_series(1, 3) as o(n)
cross join (values (1, 1, '19:00', '20:00'), (1, 3, '19:00', '20:00'), (2, 2, '18:00', '19:00'), (2, 4, '18:00', '19:00'),
                   (3, 5, '20:00', '21:30')) as s(k, weekday, starts_at, ends_at)
on conflict do nothing;

insert into training_class_assistant (organization_id, class_id, teacher_id)
select md5('ctcore-seed-org-' || o.n)::uuid, md5('ctcore-seed-class-' || o.n || '-1')::uuid, md5('ctcore-seed-teacher-' || o.n || '-2')::uuid
from generate_series(1, 3) as o(n)
on conflict do nothing;
