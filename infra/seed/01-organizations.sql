-- Dados fictícios (item #12). Idempotente: nunca altera o que já existe (on conflict do nothing).
-- Organização n = md5('ctcore-seed-org-' || n)::uuid; a tabela de organizações vem na #13.
insert into organization_feature (organization_id, feature_key, enabled)
select md5('ctcore-seed-org-' || o.n)::uuid, f.key, f.enabled
from (values
    (1, 'online_payments', true),
    (1, 'whatsapp_button', true),
    (1, 'advanced_reports', false),
    (2, 'online_payments', false),
    (2, 'whatsapp_button', true),
    (3, 'online_payments', true),
    (3, 'advanced_reports', true),
    (3, 'whatsapp_button', false)
) as f(n, key, enabled)
join (values (1), (2), (3)) as o(n) on o.n = f.n
on conflict do nothing;
