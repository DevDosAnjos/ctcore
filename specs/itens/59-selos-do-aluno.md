# 59 — RF-ALU-02 · Selos do aluno

Origem: issue #59 (Sprint 06); `docs/especificacao/08-alunos-e-responsaveis.md` (RF-ALU-02, RN-ALU-09, RN-ALU-10, "Dados: selos calculados"). Branch: `item/59-selos-do-aluno`. Parte da lista de alunos (#58: `Students.list`, `StudentsPage`) e do cadastro (#54: `student`, `guardian`, `student_guardian`, `email_problem_at`).

## 1. Objetivo

A gestão vê, na lista de Pessoas › Alunos, os selos de cada aluno, calculados na hora a partir dos dados, e filtra a lista por selo. Hoje há dado para três dos seis selos: "Cadastro incompleto" (RN-ALU-09), "E-mail com problema" e "Aniversário hoje". "Termo pendente", "Bolsista" e "Novo" dependem de termo, benefício e matrícula, que não existem (Sprint 07) e ficam como encaixe (D1).

Fora do escopo: selos "Termo pendente" (termo, #70/#67, Sprint 10), "Bolsista" (benefício, #65), "Novo" (matrícula, #63/#67); o efeito financeiro de RN-ALU-09 (mensalidade sem Pix, cartão ou boleto e só pagamento manual no balcão, Sprint 08 em diante); o item no "Precisa de atenção" (painel de atenção, Início, Sprint 14 ou posterior); selos no cabeçalho da ficha (#60); notas de versão (Sprint 15).

## 2. Requisitos

- R1 (RF-ALU-02, D2): cada aluno da lista `GET /api/v1/students` traz `seals`, lista (possivelmente vazia) de códigos entre `INCOMPLETE_REGISTRATION`, `EMAIL_PROBLEM` e `BIRTHDAY_TODAY`, calculados na consulta, sem coluna gravada, em ordem fixa (a dessa frase).
- R2 (RN-ALU-09, D3): `INCOMPLETE_REGISTRATION` quando falta CPF ou e-mail de quem paga: o próprio aluno se for adulto; o responsável financeiro se for menor (menor sem responsável financeiro também o recebe). Ao completar o dado, o selo some na leitura seguinte.
- R3 (RN-ALU-10): `EMAIL_PROBLEM` quando o e-mail de quem paga (mesma pessoa de R2) está marcado como com problema (`email_problem_at` preenchido).
- R4 (RF-ALU-02, D4): `BIRTHDAY_TODAY` quando dia e mês de `birth_date` são os de hoje pela data de referência do sistema (`Clock`); nascido em 29/02 é aniversariante em 28/02 nos anos não bissextos.
- R5 (RF-ALU-01 "filtros por selos", D5): filtro opcional `seal` (um código) em `GET /students`, combinado com os demais por E; código desconhecido dá 400. Mantém alcance, permissão e isolamento de #58.
- R6 (dado mínimo): os selos são códigos; a resposta continua sem CPF, e-mail, telefone, endereço e contato de emergência.
- R7 (tela): a lista mostra os selos como etiquetas junto ao nome (texto, não só cor), tem filtro "Selo" em `StudentFilters`, textos em `pt-BR.json`, e mantém os quatro estados, 360 px e axe nos dois temas.
- R8 (contrato): `docs/openapi.yaml` (campo `seals` em `StudentRow`, parâmetro `seal`), tipos e handler MSW refletem o formato; o seed tem alunos para exercitar os selos.

## 3. Critérios de aceite

- CA1 (R1, R4): dado aluno que faz aniversário hoje e outro que não, quando a gestão lista, então só o primeiro traz `BIRTHDAY_TODAY`; aluno de 29/02 traz o selo em 28/02 de ano não bissexto e em 29/02 de ano bissexto, e não em 01/03.
- CA2 (R2): dado adulto sem CPF, adulto sem e-mail, menor cujo responsável financeiro tem e-mail e CPF, e menor sem responsável financeiro, então o adulto sem CPF, o adulto sem e-mail e o menor sem financeiro trazem `INCOMPLETE_REGISTRATION` e o menor com financeiro completo não; ao gravar o CPF faltante do adulto, a leitura seguinte não traz mais o selo.
- CA3 (R2): dado menor cujo CPF ou e-mail próprios são nulos e o financeiro é completo, então sem o selo (dado da criança é opcional).
- CA4 (R3): dado adulto com `email_problem_at` e menor cujo responsável financeiro tem `email_problem_at`, então ambos trazem `EMAIL_PROBLEM`; aluno com problema apenas no e-mail do aluno menor não traz.
- CA5 (R1): dado aluno com mais de um selo, então `seals` vem na ordem `INCOMPLETE_REGISTRATION`, `EMAIL_PROBLEM`, `BIRTHDAY_TODAY`; sem selo, lista vazia.
- CA6 (R5): dado vários alunos, quando filtra por `seal=INCOMPLETE_REGISTRATION` (sozinho e com `q`, `unitId`, `ageRangeId`), então só voltam os que têm o selo e `total` bate; `seal=BOLSISTA` (desconhecido) dá 400; Administrador continua só com os da unidade dele.
- CA7 (R5): dado Professor, Aluno e Responsável, quando chamam `GET /students?seal=...`, então 403; sem sessão, 401; dado duas organizações, o selo e o filtro nunca revelam aluno da outra.
- CA8 (R6): dado aluno com CPF, e-mail, telefone, endereço e emergência, então o texto bruto da resposta não os contém (como #58).
- CA9 (R7): dado a tela, então cada selo aparece como texto em etiqueta junto ao nome, o filtro "Selo" refaz a consulta, "Limpar filtros" o limpa, e a tela passa no axe a 360 px nos dois temas com os quatro estados.
- CA10 (R8): `OpenApiContractTest` e `ContractRoutesTest` passam; teste MSW cobre `seals` e `seal`; `SeedDataIT` passa.
- CA11 (R2, R3, R7): e2e `@critical` "Coordenador filtra alunos por selo": abre Pessoas › Alunos, filtra por "Cadastro incompleto" e vê só o aluno esperado com a etiqueta.

## 4. Plano técnico

- Dados: não muda o esquema nem há migração. Lê `student`, `student_guardian` (`financial`), `guardian` (`cpf`, `email_problem_at`, `account_id`) e `account.email` do responsável (e-mail do responsável vive na conta, `V6`). Seed: acrescentar em `infra/seed/12-alunos.sql` (`on conflict do nothing`) só o que faltar: aluno sem CPF, aluno com `email_problem_at`; aniversariante de hoje não cabe em seed fixo, fica no teste.
- API: o mesmo `GET /api/v1/students` ganha `seal` (enum de 3 valores) e `seals` em `StudentRow`; 400 para valor desconhecido. Sem rota nova. Reutiliza o envelope e os erros de #58.
- Back-end: em `br.com.ctcore.student.Students.list`, acrescentar `seals` ao `Row`, calculados em SQL com expressões em um ponto só (`INCOMPLETE`, `EMAIL_PROBLEM`, `BIRTHDAY` como fragmentos reutilizados no `select` e no filtro, para as duas leituras nunca divergirem); quem paga = `s` se idade >= 18, senão o responsável `financial` por `left join` lateral; aniversário por mês/dia contra `:today` do `Clock` já usado na lista, com o 29/02 tratado em SQL. A enum de selos fica num tipo só (`StudentSeal`, a criar) para o controller validar o parâmetro; os selos ainda sem dado entram quando seus itens chegarem. `StudentController.java`: parâmetro `seal`. Não grava dado nem auditoria (só leitura).
- Interface: alterar `frontend/src/features/students/{types.ts,StudentsPage.tsx,StudentFilters.tsx,useStudents.ts,StudentsPage.test.tsx}`, `mocks/handlers.ts` e `mocks/students.msw.test.ts`, `i18n/pt-BR.json`; etiqueta com o `Badge` que o projeto já usa; selos dentro da coluna Nome (sempre visível), sem nova coluna configurável. Quatro estados, 360 px e axe como em #58.
- Segurança: permissão `STUDENT_MANAGE`, alcance e organização do principal, como em #58; filtro por parâmetro ligado e enum validada; resposta só com códigos (e-mail e CPF faltando ou com problema são sensíveis só pelo estado, nunca pelo valor). LGPD: nada de dado pessoal novo.
- Riscos: (1) aluno menor sem financeiro e o selo "Cadastro incompleto" para ele: depende de D3; (2) `count(*)` com o filtro por selo precisa do mesmo join que a lista, para `total` não divergir (CA6); (3) fuso do `Clock` decide o "hoje"; o mesmo de `MajorityNoticeJob`, a conferir na construção.

## 5. Tarefas

Uma parte só.

- [x] T1: `StudentSeal`, cálculo dos selos e filtro `seal` em `Students.list`, parâmetro em `StudentController`, e testes em `StudentListIT` (ou `StudentSealsIT`) para aniversário e 29/02, incompleto por adulto e por menor, e-mail com problema, ordem, filtro, 400, perfis, sem dado pessoal e isolamento — cobre CA1 a CA8 — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test=StudentListIT -Dtest=NoSuchTest -Dsurefire.failIfNoSpecifiedTests=false -Djacoco.skip=true` (ajustar o nome se criar classe nova).
- [x] T2: OpenAPI (`seals`, `seal`), tipos, handler MSW com teste e seed (aluno sem CPF e aluno com e-mail com problema) — cobre CA10 — prova: `./backend/mvnw -f backend/pom.xml test -Dtest='OpenApiContractTest,ContractRoutesTest'`, `verify -Dit.test=SeedDataIT` (mesmos flags de T1) e `npx vitest run src/mocks` em `frontend/`.
- [x] T3: etiquetas de selo na lista e filtro "Selo" em `StudentFilters`, textos pt-BR, quatro estados, 360 px e axe — cobre CA9 — prova: `npx vitest run src/features/students` em `frontend/`.
- [x] T4: e2e `@critical` "Coordenador filtra alunos por selo" (acrescentar em `frontend/e2e/alunos.spec.ts`) e regressão completa (não cobre critério: confere cobertura e os 100% dos pacotes exigidos) — cobre CA11 — prova: `./backend/mvnw -f backend/pom.xml verify` e, em `frontend/`, `npm run lint && npm run typecheck && npm run build && npm run test:cov && npm run e2e`.

## 6. Decisões

- D1 (muda o comportamento): entregam-se só os selos com dado hoje (Cadastro incompleto, E-mail com problema, Aniversário hoje). "Termo pendente", "Bolsista" e "Novo" ficam para #70/#67/Sprint 10, #65 e #63/#67; o enum cresce quando cada um chegar. Alternativa: criar aqui campos provisórios de termo, bolsa e matrícula; descartada por invadir a Sprint 07 e gerar migração conflitante. Como em #58 (D1), a issue só fica inteira depois desses itens; o PR usa `Closes #59` pelo AGENTS.md.
- D2: selos calculados na leitura, sem coluna nem tabela (a especificação diz "selos calculados"); estado sempre coerente com o dado. Alternativa: gravar e atualizar por evento; descartada por poder divergir.
- D3 (muda o comportamento): "quem paga" é o aluno adulto, ou o responsável financeiro do menor; menor sem responsável financeiro fica "Cadastro incompleto", porque não há CPF nem e-mail de quem pague. Suposição: a especificação (RN-ALU-01) não detalha o caso do menor; se o dono preferir não marcar menor sem financeiro, só muda um predicado.
- D4: aniversário usa a data de referência do sistema (`Clock`); 29/02 comemora em 28/02 nos anos não bissextos. Alternativa: 01/03; descartada por ser a convenção menos comum no Brasil, é só trocar a constante.
- D5 (muda o comportamento): o filtro por selo (RF-ALU-01) entra aqui por ser o que #58 deixou para #59; um selo por vez (a especificação não pede combinação de selos). Alternativa: vários selos; descartada por não haver pedido, vira ajuste simples se o dono quiser.
- D6: efeito financeiro de RN-ALU-09 e item no "Precisa de atenção" ficam para a cobrança (Sprint 08 em diante) e o painel de atenção; este item só entrega o selo e o filtro. Sugestão de novo item, se o painel não os cobrir: "Precisa de atenção: cadastro incompleto e e-mail com problema".
- D7: o canvas `Alunos.dc.html` não foi aberto (como em #58 D7); etiqueta de selo segue o `Badge` do projeto e o dono confere o visual no PR.

## Construção

- T1 — arquivos: `backend/.../student/StudentSeal.java` (novo), `Students.java`, `StudentController.java`, `StudentSealsIT.java` (novo) — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test='StudentSealsIT,StudentListIT' -Dtest=NoSuchTest -Dsurefire.failIfNoSpecifiedTests=false -Djacoco.skip=true` → passou (12 testes)
- T2 — arquivos: `docs/openapi.yaml`, `frontend/src/features/students/types.ts`, `frontend/src/mocks/handlers.ts`, `frontend/src/mocks/students.msw.test.ts`, `infra/seed/12-alunos.sql`, `SeedDataIT.java` (4 para 6 alunos) — prova: `OpenApiContractTest,ContractRoutesTest` → passou; `verify -Dit.test=SeedDataIT` → passou; `npx vitest run src/mocks` → 127 passaram
- T3 — arquivos: `StudentsPage.tsx`, `StudentFilters.tsx`, `pt-BR.json`, `StudentsPage.test.tsx` — prova: `npx vitest run src/features/students` → 16 passaram (axe 360 px nos dois temas com selos na lista)
- T4 — arquivos: `frontend/e2e/alunos.spec.ts` — prova: `npm run lint && npm run typecheck && npm run build && npm run test:cov && npm run e2e` → passou (cobertura 93,99%, 30 e2e); `./backend/mvnw -f backend/pom.xml verify` → passou (JaCoCo incluso)
Regressão: backend `verify` e frontend completo verdes, sem piora.
Desvios: (1) menor com responsável financeiro sempre tem CPF e e-mail (colunas `not null` em `guardian` e `account`), então o selo de menor só ocorre sem financeiro; o teste de "responsável sem e-mail" foi retirado por ser impossível no esquema. (2) No mock MSW o aluno adulto inicial ficou sem e-mail (`email: null`), para o e2e CA11 ter um aluno com "Cadastro incompleto". (3) Não existe componente `Badge`; a etiqueta usa o mesmo `span` com borda do projeto (`plans.table.inactiveBadge`). (4) Teste dos selos em `StudentSealsIT` (classe nova), com `Clock` espionado.
Não é do item: nenhum.

## Verificação

### Rodada 1 — OK — 2026-10-20, estado edbcbb0e1e2dea282a184138eb07f4a196991584

| Critério | Status | Evidência |
|---|---|---|
| CA1 | PASSOU | `StudentSealsIT.birthday` (28/02 não bissexto, 29/02 bissexto, 01/03 não) |
| CA2, CA3 | PASSOU | `StudentSealsIT.incomplete`; sem financeiro pela D3; dado próprio do menor ignorado |
| CA4, CA5 | PASSOU | `StudentSealsIT.emailProblemAndOrder` |
| CA6 | PASSOU | `StudentSealsIT.filter` (q, unitId, ageRangeId, 400, Administrador, total) |
| CA7, CA8 | PASSOU | `StudentSealsIT.permissionsIsolationMinimal` |
| CA9 | PASSOU | `StudentsPage.test.tsx` (143 testes em students+mocks passaram, axe incluso) |
| CA10 | PASSOU | `OpenApiContractTest,ContractRoutesTest` verdes; `SeedDataIT` verde no `verify` completo; teste MSW verde |
| CA11 | PASSOU | e2e `@critical`: 30 passaram |

Comandos: `./backend/mvnw -f backend/pom.xml verify` → 430 testes, BUILD SUCCESS (JaCoCo incluso); `StudentSealsIT` isolado → 5 verdes; contrato → verde; frontend `lint`, `typecheck`, `test:cov` (756 passaram, 94,01%), `e2e` (30 passaram) → verdes.
Achados:
- sugestão, código, `StudentSealsIT` `@AfterEach` (`delete from account where email like '%.example'`) — rodado em conjunto com `SeedDataIT`/`StudentListIT` na mesma seleção, falhou por restos de `account_link`/e-mails duplicados (5 erros); no `verify` completo e isolado passa. Tornar a limpeza independente da ordem.

## Entrega

- O que muda: a lista de alunos devolve selos calculados na leitura (aniversário, cadastro incompleto, e-mail com problema) e aceita o filtro `seal` (um selo por vez, enum validada); a tela mostra os selos como etiquetas de texto junto ao nome e ganha o filtro "Selo". OpenAPI, MSW e seed atualizados.
- Verificação: rodada 1 OK. `verify` do back-end (430 testes, JaCoCo) e `lint`, `typecheck`, `test:cov` (94%), `e2e` (30) do front-end verdes.
- Decisões: selos sem coluna nem tabela (D2); efeito financeiro e painel de atenção ficam para itens posteriores (D6); o dono confere o visual da etiqueta no PR (D7).
- Limitações: `StudentSealsIT` depende da ordem quando rodado junto de `SeedDataIT`/`StudentListIT` na mesma seleção; passa no `verify` completo e isolado.

Refs #59
