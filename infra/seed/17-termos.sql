-- Synthetic published terms (RF-MAT-05, #70): version 1 of the enrollment terms of the first seeded organization, published by the first seeded account.
insert into terms_version (id, organization_id, kind, version, body, reaccept, published_by_account_id)
values (md5('ctcore-seed-terms-1')::uuid, md5('ctcore-seed-org-1')::uuid, 'ENROLLMENT', 1,
        'Termo de matrícula fictício: o aluno ou o responsável declara conhecer o regulamento da academia e concorda com as condições de pagamento, frequência e uso das instalações.',
        false, md5('ctcore-seed-account-1')::uuid)
on conflict do nothing;
