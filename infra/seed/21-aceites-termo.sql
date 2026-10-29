-- Synthetic term acceptance (RN-MAT-11, RN-MAT-12, #85): seeded student 1 accepted version 1 of the enrollment term and granted image use,
-- and confirmed nothing else. Records are insert-only; fixed ids and "on conflict do nothing" make the seed repeatable.
insert into term_acceptance (id, organization_id, student_id, kind, event, version, account_id, accepted_at, ip)
select md5('ctcore-seed-term-acceptance-' || k.kind)::uuid, md5('ctcore-seed-org-1')::uuid, md5('ctcore-seed-student-1')::uuid,
       k.kind, 'ACCEPTED', 1, md5('ctcore-seed-account-1')::uuid, timestamptz '2026-01-12 12:00:00+00', '192.0.2.10'
from (values ('ENROLLMENT'), ('IMAGE_USE')) as k(kind)
where exists (select 1 from enrollment where id = md5('ctcore-seed-enrollment-1')::uuid)
on conflict do nothing;
