-- Synthetic announcements (RF-COM-01, #81) in the first seeded organization: two sent (one for a class, one for the whole organization) and one
-- scheduled for the unit. Authors are the seeded coordinator and teacher accounts. No reads are seeded (the portal records them, Sprint 10).
insert into announcement (id, organization_id, unit_id, target_kind, class_id, type, title, body, channels, scheduled_for, sent_at, recipient_count, author_account_id)
select md5('ctcore-seed-announcement-' || a.k)::uuid, md5('ctcore-seed-org-1')::uuid, a.unit, a.kind, a.class, a.type, a.title, a.body, '{EMAIL,PORTAL}',
       a.scheduled::timestamptz, a.sent::timestamptz, a.recipients, md5('ctcore-seed-account-' || a.author)::uuid
from (values
    (1, md5('ctcore-seed-unit-1')::uuid, 'CLASS', md5('ctcore-seed-class-1-1')::uuid, 'CANCELED_CLASS', 'Aula de sexta cancelada', 'Sem aula nesta sexta (texto fictício).', null, '2026-10-20T13:00:00Z', 12, 2),
    (2, null::uuid, 'ORGANIZATION', null::uuid, 'GENERAL', 'Recesso de fim de ano', 'Aviso geral fictício.', null, '2026-10-22T12:00:00Z', 40, 1),
    (3, md5('ctcore-seed-unit-1')::uuid, 'UNIT', null::uuid, 'SCHEDULE_CHANGE', 'Novo horário a partir de novembro', 'Horários fictícios.', '2026-10-30T12:00:00Z', null, null, 1)
) as a(k, unit, kind, class, type, title, body, scheduled, sent, recipients, author)
on conflict do nothing;
