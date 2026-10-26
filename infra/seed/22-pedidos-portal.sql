-- Synthetic portal requests (RF-MAT-04, #86) of the first seeded organization: student 1 asks for a plan change on Jiu-Jitsu and student 3 asks for
-- a new modality (Jiu-Jitsu). Both are open and change nothing in the enrollment. Fixed ids and "on conflict do nothing" make the seed repeatable.
insert into portal_request (id, organization_id, student_id, unit_id, kind, enrollment_modality_id, target_plan_id, target_modality_id, target_unit_id, note, status, requested_by_account_id, ip, created_at)
select md5('ctcore-seed-portal-request-' || r.k)::uuid, md5('ctcore-seed-org-1')::uuid, md5('ctcore-seed-student-' || r.student)::uuid, md5('ctcore-seed-unit-1')::uuid, r.kind,
       case when r.kind = 'NEW_MODALITY' then null else md5('ctcore-seed-enrollment-modality-1')::uuid end,
       case when r.kind = 'PLAN_CHANGE' then md5('ctcore-seed-plan-1-2')::uuid end,
       case when r.kind = 'NEW_MODALITY' then (select id from modality where name = 'Jiu-Jitsu') end,
       case when r.kind = 'NEW_MODALITY' then md5('ctcore-seed-unit-1')::uuid end,
       r.note, 'OPEN', md5('ctcore-seed-account-1')::uuid, '192.0.2.10', timestamptz '2026-10-20 12:00:00+00'
from (values (1, 1, 'PLAN_CHANGE', 'Quero treinar sem limite de dias.'), (2, 3, 'NEW_MODALITY', null)) as r(k, student, kind, note)
where exists (select 1 from enrollment_modality where id = md5('ctcore-seed-enrollment-modality-1')::uuid)
  and exists (select 1 from account where id = md5('ctcore-seed-account-1')::uuid)
on conflict do nothing;
