-- Synthetic announcement read (RF-POR-06, #93): the seeded student 2 read announcement 2 ("Recesso de fim de ano"), so the management list shows one reader.
-- "on conflict do nothing" makes the seed repeatable.
insert into announcement_read (organization_id, announcement_id, student_id, account_id, opened_at)
select md5('ctcore-seed-org-1')::uuid, md5('ctcore-seed-announcement-2')::uuid, s.id, coalesce(s.account_id, md5('ctcore-seed-account-1')::uuid), timestamptz '2026-10-23 12:00:00+00'
from student s
where s.id = md5('ctcore-seed-student-2')::uuid
  and exists (select 1 from announcement where id = md5('ctcore-seed-announcement-2')::uuid)
  and exists (select 1 from account where id = coalesce(s.account_id, md5('ctcore-seed-account-1')::uuid))
on conflict do nothing;
