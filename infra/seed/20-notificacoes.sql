-- Synthetic notifications (RF-NOT-01, #84) for the seeded coordinator (link 1) and administrator (link 3) of the first organization:
-- today, this week and one older than 7 days; some read. Fictitious names and values only.
insert into notification (id, organization_id, link_id, unit_id, type, ref_type, ref_id, params, created_at, read_at)
select md5('ctcore-seed-notification-' || n.k)::uuid, md5('ctcore-seed-org-1')::uuid, md5('ctcore-seed-link-' || n.link)::uuid, n.unit, n.type, n.ref_type,
       md5('ctcore-seed-notification-ref-' || n.k)::uuid, n.params::jsonb, now() - n.age::interval, case when n.is_read then now() end
from (values
    (1, 3, md5('ctcore-seed-unit-1')::uuid, 'PAYMENT_RECEIVED', 'charge', '{"studentName":"Aluno Fictício","amountCents":15000}', '1 hour', false),
    (2, 3, md5('ctcore-seed-unit-1')::uuid, 'EMAIL_PROBLEM', 'student', '{}', '3 days', false),
    (3, 3, md5('ctcore-seed-unit-1')::uuid, 'STUDENT_MAJORITY', 'student', '{"studentName":"Aluna Fictícia"}', '5 days', true),
    (4, 1, null::uuid, 'HIGH_BENEFIT', 'benefit', '{"studentName":"Aluno Fictício"}', '2 hours', false),
    (5, 1, null::uuid, 'INVITE_PENDING', 'invite', '{"personName":"Convidado Fictício"}', '4 days', false),
    (6, 1, null::uuid, 'INVITE_PENDING', 'invite', '{"personName":"Convidada Antiga Fictícia"}', '12 days', false)
) as n(k, link, unit, type, ref_type, params, age, is_read)
on conflict do nothing;
