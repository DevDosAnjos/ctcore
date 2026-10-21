-- Synthetic benefits (RF-BEN-01, #64) over the enrollment modalities of 14-matriculas.sql (first seeded organization):
-- common scholarship, high scholarship, high amount discount, one expired and one ended. Granted by the first seeded account.
insert into benefit (id, organization_id, enrollment_modality_id, student_id, unit_id, type, value, reason, decided_by, granted_by_account_id, valid_until, status, ended_at, end_reason)
select md5('ctcore-seed-benefit-' || b.k)::uuid, md5('ctcore-seed-org-1')::uuid, em.id, em.student_id, em.unit_id, b.type, b.value, b.reason, b.decided_by,
       md5('ctcore-seed-account-1')::uuid, b.valid_until::date, b.status, b.ended_at::timestamptz, b.end_reason
from (values (1, 1, 'PERCENT', 30, 'Aluno de projeto social', 'Mestre Fictício', null, 'ACTIVE', null, null),
             (2, 2, 'PERCENT', 60, 'Atleta da equipe de competição', 'Mestre Fictício', null, 'ACTIVE', null, null),
             (3, 3, 'AMOUNT', 12000, 'Situação financeira da família', 'Direção Fictícia', null, 'ACTIVE', null, null),
             (4, 4, 'PERCENT', 20, 'Promoção de matrícula', 'Direção Fictícia', '2026-02-28', 'ACTIVE', null, null),
             (5, 5, 'PERCENT', 10, 'Convênio encerrado', 'Direção Fictícia', null, 'ENDED', '2026-03-01T12:00:00Z', 'Aluno saiu'))
       as b(k, em_k, type, value, reason, decided_by, valid_until, status, ended_at, end_reason)
join enrollment_modality em on em.id = md5('ctcore-seed-enrollment-modality-' || b.em_k)::uuid
on conflict do nothing;
