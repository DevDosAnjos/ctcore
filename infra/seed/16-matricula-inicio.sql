-- Synthetic enrollment starts (RF-MAT-01, #67) over the enrollments of 14-matriculas.sql (first seeded organization, unit 1): no fee, second charge on day 10.
-- And one origin graduation (first Jiu-Jitsu level, with proof) for seeded student 1, registered by the first seeded account.
insert into enrollment_start (id, organization_id, enrollment_id, unit_id, concluded_at, fee_cents, fee_exempt_reason, second_due_on)
select md5('ctcore-seed-enrollment-start-' || e.n)::uuid, md5('ctcore-seed-org-1')::uuid, md5('ctcore-seed-enrollment-' || e.n)::uuid,
       md5('ctcore-seed-unit-1')::uuid, e.concluded_at::timestamptz, e.fee, e.reason, e.second_due_on::date
from (values (1, '2026-01-10T12:00:00Z', 0, null, '2026-02-10'),
             (2, '2026-01-10T12:00:00Z', 5000, null, '2026-02-10'),
             (3, '2026-02-02T12:00:00Z', 0, 'Bolsa da turma', '2026-03-10')) as e(n, concluded_at, fee, reason, second_due_on)
on conflict do nothing;

insert into student_graduation (id, organization_id, student_id, modality_id, level_id, degree, achieved_on, origin, proof_file, has_proof, registered_by_account_id)
select md5('ctcore-seed-student-graduation-1')::uuid, md5('ctcore-seed-org-1')::uuid, md5('ctcore-seed-student-1')::uuid, l.modality_id, l.id, 1,
       date '2025-06-01', true, null, false, md5('ctcore-seed-account-1')::uuid
from graduation_level l
where l.organization_id = md5('ctcore-seed-org-1')::uuid and l.position = 0
  and l.modality_id = (select id from modality where name = 'Jiu-Jitsu')
on conflict do nothing;
