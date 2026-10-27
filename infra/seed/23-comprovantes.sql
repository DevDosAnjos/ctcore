-- Synthetic payment proof (RF-COB-05, #87) of the first seeded organization, unit 1: student 2 sent a Pix proof for a one-off charge ("Kimono"), which is
-- in review. The file key points to a file that does not exist in storage (the management list shows "image unavailable"). Fixed ids and
-- "on conflict do nothing" make the seed repeatable; the charge only moves to IN_REVIEW while it is still open.
insert into charge (id, organization_id, unit_id, student_id, type, description, due_on, tolerance_days, plan_cents, benefit_cents, amount_cents, status, origin, notified_at)
select md5('ctcore-seed-charge-4')::uuid, md5('ctcore-seed-org-1')::uuid, md5('ctcore-seed-unit-1')::uuid, md5('ctcore-seed-student-2')::uuid,
       'ONE_OFF', 'Kimono', date '2026-11-10', 5, 0, 0, 9000, 'IN_REVIEW', 'MANAGEMENT', now()
where exists (select 1 from student where id = md5('ctcore-seed-student-2')::uuid)
on conflict do nothing;

insert into payment_proof (id, organization_id, charge_id, student_id, unit_id, file_key, content_type, sent_by_account_id, sent_at, status)
select md5('ctcore-seed-payment-proof-1')::uuid, md5('ctcore-seed-org-1')::uuid, c.id, c.student_id, c.unit_id,
       'seed-payment-proof-1.png', 'image/png', md5('ctcore-seed-account-1')::uuid, timestamptz '2026-10-21 12:00:00+00', 'PENDING'
from charge c
where c.id = md5('ctcore-seed-charge-4')::uuid
  and exists (select 1 from account where id = md5('ctcore-seed-account-1')::uuid)
on conflict do nothing;
