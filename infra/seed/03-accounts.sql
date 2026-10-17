-- Synthetic accounts; every active account has the fictitious password SenhaFicticia1. Preserve existing rows.
insert into account (id, email, name, password_hash, status)
select md5('ctcore-seed-account-' || e.n)::uuid, e.email, e.name,
    case when e.status = 'INVITED' then null
        else '$argon2id$v=19$m=16384,t=2,p=1$HyKJGnngQROpZzh2nlMONw$zvbuk5wuwiBDrXm2zBFeCmu1izBtZds3IpHe88GgwBE' end,
    e.status
from (values
    (1, 'coordenador@ficticia-1.example', 'Coordenador Fictício', 'ACTIVE'),
    (2, 'professor@ficticia-1.example', 'Professor Fictício', 'ACTIVE'),
    (3, 'multiplo@ficticia.example', 'Conta com Dois Vínculos Fictícia', 'ACTIVE'),
    (4, 'convidado@ficticia-1.example', 'Convidado Fictício', 'INVITED'),
    (5, 'desativado@ficticia-1.example', 'Desativado Fictício', 'DEACTIVATED')
) as e(n, email, name, status)
on conflict do nothing;

insert into account_link (id, account_id, organization_id, role, status, starts_on)
select md5('ctcore-seed-link-' || l.n)::uuid, md5('ctcore-seed-account-' || l.account_n)::uuid,
    md5('ctcore-seed-org-' || l.org_n)::uuid, l.role, l.status, date '2026-01-01'
from (values
    (1, 1, 1, 'COORDINATOR', 'ACTIVE'),
    (2, 2, 1, 'TEACHER', 'ACTIVE'),
    (3, 3, 1, 'ADMINISTRATOR', 'ACTIVE'),
    (4, 3, 2, 'STUDENT', 'ACTIVE'),
    (5, 4, 1, 'ADMINISTRATOR', 'PENDING'),
    (6, 5, 1, 'ADMINISTRATOR', 'ENDED')
) as l(n, account_n, org_n, role, status)
on conflict do nothing;

insert into account_link_unit (link_id, organization_id, unit_id)
select md5('ctcore-seed-link-2')::uuid, md5('ctcore-seed-org-1')::uuid, md5('ctcore-seed-unit-1')::uuid
on conflict do nothing;
