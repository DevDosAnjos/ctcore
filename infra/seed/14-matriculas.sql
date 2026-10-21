-- Synthetic enrollments (RF-MAT, #63) of the first seeded organization: three active students (one with two modalities), one former student
-- (every modality ended) and two students without enrollment (5 and 6 of 12-alunos.sql). Classes come from 10-turmas.sql, plans from 13-planos.sql.
insert into enrollment (id, organization_id, student_id, enrolled_at)
select md5('ctcore-seed-enrollment-' || s.n)::uuid, md5('ctcore-seed-org-1')::uuid, md5('ctcore-seed-student-' || s.n)::uuid, s.enrolled_at::timestamptz
from (values (1, '2026-01-10T12:00:00Z'), (2, '2026-01-10T12:00:00Z'), (3, '2026-02-02T12:00:00Z'), (4, '2025-06-01T12:00:00Z')) as s(n, enrolled_at)
on conflict do nothing;

insert into enrollment_modality (id, organization_id, enrollment_id, student_id, unit_id, modality_id, primary_class_id, plan_id, due_day, status, started_at, ended_at)
select md5('ctcore-seed-enrollment-modality-' || e.k)::uuid, md5('ctcore-seed-org-1')::uuid, md5('ctcore-seed-enrollment-' || e.student)::uuid,
       md5('ctcore-seed-student-' || e.student)::uuid, md5('ctcore-seed-unit-1')::uuid, m.id, md5('ctcore-seed-class-1-' || e.class)::uuid,
       md5('ctcore-seed-plan-1-' || e.plan)::uuid, e.due_day, e.status, e.started_at::timestamptz, e.ended_at::timestamptz
from (values (1, 1, 'Jiu-Jitsu', 1, 1, 5, 'ACTIVE', '2026-01-10T12:00:00Z', null),
             (2, 2, 'Jiu-Jitsu', 1, 2, 5, 'ACTIVE', '2026-01-10T12:00:00Z', null),
             (3, 2, 'Judô', 2, 3, 5, 'ACTIVE', '2026-01-10T12:00:00Z', null),
             (4, 3, 'Judô', 2, 3, 10, 'ACTIVE', '2026-02-02T12:00:00Z', null),
             (5, 4, 'Jiu-Jitsu', 1, 1, 10, 'ENDED', '2025-06-01T12:00:00Z', '2026-03-01T12:00:00Z'))
       as e(k, student, modality, class, plan, due_day, status, started_at, ended_at)
join modality m on m.name = e.modality
on conflict do nothing;
