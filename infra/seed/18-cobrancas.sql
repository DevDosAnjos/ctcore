-- Synthetic charges (RN-COB, #71) over the enrollments of 14-matriculas.sql (first seeded organization, unit 1): student 1 has a paid October charge
-- with its payment and receipt (public code SEEDRECEIPT00001) and an open November charge; student 2 has an open November charge. Modality 1 has the
-- next-charge pointer on December. Amounts come from the plan of the modality.
insert into charge (id, organization_id, unit_id, student_id, type, enrollment_modality_id, plan_id, reference_month, due_on, tolerance_days, plan_cents, benefit_cents, amount_cents, status, origin, notified_at)
select md5('ctcore-seed-charge-' || c.k)::uuid, md5('ctcore-seed-org-1')::uuid, md5('ctcore-seed-unit-1')::uuid, md5('ctcore-seed-student-' || c.student)::uuid,
       'MONTHLY', md5('ctcore-seed-enrollment-modality-' || c.modality)::uuid, p.id, c.ref::date, c.due::date, 5, p.value_cents, 0, p.value_cents, c.status, 'GENERATION', now()
from (values (1, 1, 1, 1, '2026-10-01', '2026-10-05', 'PAID'),
             (2, 1, 1, 1, '2026-11-01', '2026-11-05', 'OPEN'),
             (3, 2, 2, 2, '2026-11-01', '2026-11-05', 'OPEN')) as c(k, student, modality, plan, ref, due, status)
join plan p on p.id = md5('ctcore-seed-plan-1-' || c.plan)::uuid
on conflict do nothing;

insert into payment (id, organization_id, charge_id, student_id, unit_id, amount_cents, method, origin, paid_at, registered_by_account_id)
select md5('ctcore-seed-payment-1')::uuid, md5('ctcore-seed-org-1')::uuid, c.id, c.student_id, c.unit_id, c.amount_cents, 'CASH', 'MANAGEMENT',
       timestamptz '2026-10-04T15:00:00Z', md5('ctcore-seed-account-1')::uuid
from charge c
where c.id = md5('ctcore-seed-charge-1')::uuid
on conflict do nothing;

insert into receipt (id, organization_id, payment_id, unit_id, number, code)
select md5('ctcore-seed-receipt-1')::uuid, md5('ctcore-seed-org-1')::uuid, p.id, p.unit_id, 1, 'SEEDRECEIPT00001'
from payment p
where p.id = md5('ctcore-seed-payment-1')::uuid
on conflict do nothing;

insert into receipt_counter (unit_id, organization_id, last_number)
values (md5('ctcore-seed-unit-1')::uuid, md5('ctcore-seed-org-1')::uuid, 1)
on conflict do nothing;

update enrollment_modality
set next_charge_ref = date '2026-12-01', next_charge_due_on = date '2026-12-05'
where id = md5('ctcore-seed-enrollment-modality-1')::uuid and next_charge_ref is null;
