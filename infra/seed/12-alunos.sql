-- Synthetic students (RF-ALU-01) of the first seeded organization: two adults, two minors and the guardians; one guardian has both children.
-- Guardian accounts are invited (no password); CPFs are the usual valid test numbers.
insert into account (id, email, name, status)
select md5('ctcore-seed-account-' || g.n)::uuid, g.email, g.name, 'INVITED'
from (values (6, 'responsavel1@ficticia-1.example', 'Responsável Um Fictício'),
             (7, 'responsavel2@ficticia-1.example', 'Responsável Dois Fictício')) as g(n, email, name)
on conflict do nothing;

insert into account_link (id, account_id, organization_id, role, status, starts_on)
select md5('ctcore-seed-link-' || (g.n + 2))::uuid, md5('ctcore-seed-account-' || g.n)::uuid, md5('ctcore-seed-org-1')::uuid, 'GUARDIAN', 'PENDING', date '2026-01-01'
from (values (6), (7)) as g(n)
on conflict do nothing;

insert into guardian (id, organization_id, account_id, name, cpf, phone)
select md5('ctcore-seed-guardian-' || g.n)::uuid, md5('ctcore-seed-org-1')::uuid, md5('ctcore-seed-account-' || (g.n + 5))::uuid, g.name, g.cpf, g.phone
from (values (1, 'Responsável Um Fictício', '98765432100', '11900000011'),
             (2, 'Responsável Dois Fictício', '12345678909', '11900000012')) as g(n, name, cpf, phone)
on conflict do nothing;

insert into student (id, organization_id, registered_unit_id, name, name_key, birth_date, cpf, email, phone,
                     postal_code, street, number, district, city, state, majority_notice_at)
select md5('ctcore-seed-student-' || s.n)::uuid, md5('ctcore-seed-org-1')::uuid, md5('ctcore-seed-unit-1')::uuid, s.name, s.name_key, s.birth_date::date, s.cpf, s.email, s.phone,
       '01001000', 'Rua Fictícia', s.n::text, 'Bairro Fictício', 'Cidade Fictícia 1', 'SP', null
from (values (1, 'Aluno Adulto Um Fictício', 'aluno adulto um ficticio', '1990-03-10', '52998224725', 'aluno1@ficticia-1.example', '11900000001'),
             (2, 'Aluna Adulta Dois Fictícia', 'aluna adulta dois ficticia', '1985-07-21', '11144477735', 'aluno2@ficticia-1.example', '11900000002'),
             (3, 'Aluno Menor Três Fictício', 'aluno menor tres ficticio', '2014-05-02', null, null, null),
             (4, 'Aluna Menor Quatro Fictícia', 'aluna menor quatro ficticia', '2016-11-15', null, null, null)) as s(n, name, name_key, birth_date, cpf, email, phone)
on conflict do nothing;

-- Guardian 1 has both minors (the financial one in each); guardian 2 also has the third student.
insert into student_guardian (organization_id, student_id, guardian_id, financial)
select md5('ctcore-seed-org-1')::uuid, md5('ctcore-seed-student-' || l.s)::uuid, md5('ctcore-seed-guardian-' || l.g)::uuid, l.financial
from (values (3, 1, true), (4, 1, true), (3, 2, false)) as l(s, g, financial)
on conflict do nothing;

-- RF-ALU-02: um adulto sem CPF (selo "Cadastro incompleto") e um com e-mail marcado com problema (selo "E-mail com problema").
insert into student (id, organization_id, registered_unit_id, name, name_key, birth_date, cpf, email, phone,
                     postal_code, street, number, district, city, state, email_problem_at)
select md5('ctcore-seed-student-' || s.n)::uuid, md5('ctcore-seed-org-1')::uuid, md5('ctcore-seed-unit-1')::uuid, s.name, s.name_key, s.birth_date::date, s.cpf, s.email, s.phone,
       '01001000', 'Rua Fictícia', s.n::text, 'Bairro Fictício', 'Cidade Fictícia 1', 'SP', s.problem::timestamptz
from (values (5, 'Aluno Sem Cpf Cinco Fictício', 'aluno sem cpf cinco ficticio', '1992-08-05', null, 'aluno5@ficticia-1.example', '11900000005', null),
             (6, 'Aluna Email Problema Seis Fictícia', 'aluna email problema seis ficticia', '1988-12-01', '39053344705', 'aluno6@ficticia-1.example', '11900000006', '2026-01-05T12:00:00Z')) as s(n, name, name_key, birth_date, cpf, email, phone, problem)
on conflict do nothing;
