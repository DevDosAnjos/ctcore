# 72 — RF-COB-01 · Financeiro › Cobranças (lista)

Origem: issue #72 (Sprint 08), com o bloco "Encaixe herdado da Sprint 06" no fim do corpo; `docs/especificacao/10-cobrancas.md` (RF-COB-01, RN-COB-07, seções "Dados" e "Situações da cobrança"), `02-contas-e-acesso.md` (linhas "Cobranças e pagamentos" e "Alunos"). Branch: `item/72-cobrancas-lista`. Parte das tabelas `charge`/`payment` (V39, #71), de `Students.list` e `StudentController` (#58), de `QueryPage`/`DataTable` e de `menuCatalog.ts`, que já traz o item "collections" apontando para `/cobrancas`. Telas do canvas (`Mensalidades.dc.html`, `GMCobrancas.dc.html`) inacessíveis ao agente: o dono confere no PR.

Dividido em **duas partes** (tarefas T1 a T8 passam de seis); a numeração é única. A Parte 2 só começa com a 1 concluída. Commit e PR são um só (`#72: RF-COB-01 · Financeiro › Cobranças (lista)`, `Closes #72`), no fim da Parte 2.

| Parte | Entrega | Requisitos | Tarefas |
| --- | --- | --- | --- |
| 1 | Rota `GET /charges`, situação calculada, filtros, totais, encaixe da lista de alunos, isolamento | R1 a R11 | T1 a T4 |
| 2 | Tela, cartões no celular, filtros, rodapé, coluna na lista de alunos, MSW | R12 a R16 | T5 a T8 |

## 1. Objetivo

Coordenador e Administrador veem em Financeiro › Cobranças as cobranças das unidades ao alcance deles, paginadas, com aluno e foto, referência, valor final, vencimento, situação (cor e texto) e meio quando paga; filtram pelos atalhos Em atraso, Vence hoje, Em conferência e Pagas no mês e por período, situação, tipo, modalidade e turma; o rodapé mostra os totais do que está filtrado. A lista de alunos ganha a situação financeira e o filtro Em atraso, lidos das cobranças (encaixe da Sprint 06), nunca para o Professor.

Fora do escopo: painel da cobrança e suas ações (RF-COB-02, outros itens da sprint), registrar pagamento, enviar lembrete, renegociar, cancelar e estornar; prévia da geração (já em #71); filtro Bloqueados (#109); notas de versão (sem mudança visível para academias fora de Financeiro, que só passa a ter conteúdo agora; a sprint as consolida); ponta a ponta (a lista não é fluxo crítico).

## 2. Requisitos

- R1: `GET /api/v1/charges` devolve página de cobranças com aluno (id, nome, foto), unidade, tipo, modalidade, mês de referência, descrição, valor final em centavos, vencimento, situação calculada e, quando paga, meio e data do pagamento.
- R2: a situação é calculada pela data da unidade, nunca digitada: Pendente, Vence hoje, Em atraso (passou do vencimento mais a tolerância), Em conferência, Paga, Paga com atraso, Cancelada, Renegociada, Estornada (D1, D2).
- R3: só Coordenador e Administrador acessam (permissão COLLECTIONS); o Administrador vê só as unidades dele, o Coordenador todas; unidade fora do alcance dá lista vazia, não erro; Professor, Aluno e Responsável recebem 403.
- R4: filtros rápidos `quick` = OVERDUE, DUE_TODAY, IN_REVIEW, PAID_MONTH; PAID_MONTH são pagamentos confirmados no mês corrente do fuso da unidade (D3).
- R5: filtros por período de vencimento (`from`, `to`), situação (`status`), tipo (`type`), modalidade (`modalityId`), turma (`classId`, principal ou outro horário da matrícula), unidade (`unitId`) e busca pelo nome do aluno (`q`), combinados por "e".
- R6: modalidade, turma ou unidade inexistente na organização dá 404; valor de enum ou data inválidos, `from` depois de `to` e tamanho de página fora de 25/50/100 dão 400.
- R7: a resposta traz `counts` dos quatro atalhos (valem para o alcance e a unidade escolhida, sem os outros filtros) e `totals` (quantidade e soma em centavos de tudo o que está filtrado, não só da página) (D4).
- R8: ordenação por `dueOn` (padrão, decrescente), `amount` ou `student`, com desempate por id; só leitura, sem auditoria.
- R9: RN-COB-07: o valor devolvido é sempre o valor final gravado; atraso não soma juros nem multa, e o contrato não tem campo para isso.
- R10: `GET /students` ganha `financialStatus` (`OVERDUE` ou `CURRENT`) por aluno, filtro `financial=OVERDUE` e `counts.overdue`; vale para quem tem acesso à lista (D5).
- R11: a situação financeira nunca sai em resposta para o Professor: a lista de alunos o recusa e as respostas que ele alcança (lista simples e ficha) não trazem o campo.
- R12: a tela `/cobrancas` (Coordenador e Administrador) mostra as colunas de R1, situação com cor e texto (nunca só cor), filtros de R4 e R5 no endereço (RNF-27) e rodapé com os totais de R7.
- R13: a tela tem os quatro estados (carregando, vazio, erro, sem permissão) e funciona a partir de 360 px, com cartões no celular e axe limpo nos dois temas.
- R14: todos os textos em `pt-BR.json`; datas no fuso da unidade e valores em reais a partir dos centavos pelos formatadores existentes.
- R15: a lista de alunos mostra a coluna opcional "Situação financeira" e o atalho "Em atraso" com contagem.
- R16: o OpenAPI (`/charges`, parâmetros novos de `/students`) e os handlers MSW batem com a implementação.

## 3. Critérios de aceite

- CA1 (R1): dado uma mensalidade paga por Pix com aluno com foto, quando o Administrador da unidade lista, então a linha traz aluno, foto, modalidade, mês, valor, vencimento, situação PAID, meio PIX_MP e data do pagamento.
- CA2 (R2): dado cobranças abertas com vencimento ontem (tolerância 0), hoje, amanhã e ontem com tolerância 3, quando lista, então saem OVERDUE, DUE_TODAY, PENDING e PENDING; e no dia seguinte ao fim da tolerância, OVERDUE.
- CA3 (R2): dado cobrança paga no vencimento e outra paga depois do vencimento mais a tolerância, quando lista, então PAID e PAID_LATE; cobranças `IN_REVIEW`, `CANCELED`, `RENEGOTIATED` e `REFUNDED` saem com a situação gravada, e o "hoje" é o do fuso da unidade (unidade em fuso diferente muda a situação na virada do dia).
- CA4 (R3): dado Administrador de A e cobranças das unidades A e B, quando lista, então só as de A; com `unitId` de B, lista vazia com 200; o Coordenador vê A e B.
- CA5 (R3): dado Professor, Aluno ou Responsável (e anônimo), quando chama `GET /charges`, então 403 (401 sem sessão).
- CA6 (R8): dado 30 cobranças, quando pede `size=25` e `page=2`, então 5 linhas, total 30 e a ordem por vencimento decrescente; `sort=amount,asc` e `sort=student,asc` ordenam; `sort=x,asc` dá 400.
- CA7 (R4): dado uma cobrança em cada situação, quando usa cada `quick`, então OVERDUE traz as em atraso, DUE_TODAY as de hoje, IN_REVIEW as em conferência e PAID_MONTH as pagas (em dia ou com atraso) no mês corrente da unidade, não as pagas em mês anterior nem as estornadas.
- CA8 (R5): dado cobranças variadas, quando filtra por `from`/`to`, `status`, `type`, `modalityId`, `classId`, `q` e combinações, então só as que atendem a todos; cobrança sem modalidade (taxa, avulsa) não passa pelo filtro de modalidade nem de turma; turma como outro horário da matrícula casa.
- CA9 (R6): dado `modalityId`, `classId` ou `unitId` de outra organização ou inexistente, quando lista, então 404; `status=X`, `type=X`, `from=2026-13-01`, `from` maior que `to` e `size=30` dão 400.
- CA10 (R7): dado filtros que deixam 3 cobranças de R$ 100,00, R$ 150,00 e R$ 50,00 em 2 páginas de 2, quando lista, então `totals` é 3 e 30000 em qualquer página; `counts` não muda ao aplicar `status` ou `type`, mas muda com `unitId`.
- CA11 (R9): dado cobrança de R$ 120,00 vencida há 40 dias, quando lista, então `amountCents` é 12000 e o `totals` soma 12000; a resposta não tem campo de juros nem multa.
- CA12 (R10): dado aluno com cobrança em atraso, outro só com cobrança paga e outro sem cobrança, quando lista alunos, então `OVERDUE`, `CURRENT`, `CURRENT`; `financial=OVERDUE` traz só o primeiro e `counts.overdue` é 1; cobrança cancelada ou em conferência não deixa o aluno em atraso.
- CA13 (R11): dado Professor, quando chama `GET /students`, então 403; quando abre a lista simples e a ficha, então a resposta não contém `financialStatus` (nenhuma chave com esse nome).
- CA14 (R3, R10): dado Administrador, quando lista alunos com `financial=OVERDUE`, então só vê alunos do alcance dele; o Administrador de outra organização não vê nem conta as cobranças da primeira (isolamento por organização na lista de cobranças e na de alunos, com RLS ligada no teste).
- CA15 (R12): dado cobranças carregadas, quando abre `/cobrancas?quick=OVERDUE&pagina=2`, então a consulta reflete o endereço, o atalho aparece ativo (`aria-pressed`) com a contagem, o rodapé mostra quantidade e total em reais, e trocar filtro volta à página 1.
- CA16 (R12): dado situação de cada tipo, quando renderiza, então cada situação aparece com texto traduzido e cor, e o meio só nas pagas.
- CA17 (R13): dado lista sem resultado, erro 500, 403 e carregamento, quando renderiza, então aparecem vazio (com "limpar filtros" quando há filtro), erro com tentar de novo, sem permissão e carregando; o menu de Professor não mostra Cobranças e a rota o leva a `/sem-acesso`.
- CA18 (R13): dado largura de 360 px, quando renderiza, então a lista aparece em cartões sem rolagem horizontal da página, e `axe` não acusa violação nos temas claro e escuro.
- CA19 (R14): dado o código da tela, quando roda `npm run lint`, então não há texto literal, e a data de vencimento é a da unidade.
- CA20 (R15): dado aluno em atraso, quando abre Pessoas › Alunos, então a coluna opcional mostra "Em atraso" e o atalho "Em atraso (N)" filtra.
- CA21 (R16): dado o contrato, quando roda `OpenApiContractTest` e `ContractRoutesTest`, então passam, e o handler MSW de `/charges` e o de `/students` respondem no formato do contrato (teste de contrato do MSW).

## 4. Plano técnico

- Dados: não muda tabela. Uma migração só se o plano de consulta exigir índice novo (hoje `charge_by_unit (organization_id, unit_id, status, due_on)` e `charge_by_student` bastam para a ordem de grandeza da RNF-01; risco abaixo). Situação calculada em SQL, não gravada (V39 já diz isso).
- API: `GET /api/v1/charges` (a criar em `ChargeController`). Query: `q`, `unitId`, `quick`, `from`, `to`, `status`, `type`, `modalityId`, `classId`, `page`, `size`, `sort`. Resposta `ChargeList { items[], page, size, total, counts{overdue,dueToday,inReview,paidMonth}, totals{count,amountCents} }`; item `{ id, student{id,name,photoUrl}, unit{id,name}, type, modality{id,name}|null, referenceMonth|null, description|null, amountCents, dueOn, status, payment{method,paidAt}|null }`. Erros 400/401/403/404 como em `/students`. `GET /students`: acrescenta `financial` na query, `financialStatus` em `Row` e `overdue` em `Counts`.
- Interface: página `/cobrancas` com `QueryPage`; filtros e atalhos em `ChargeFilters`; rodapé com totais; cartões abaixo de `md`; coluna e atalho novos na lista de alunos.
- Segurança: `permissions.effective(who).get(COLLECTIONS) == NONE` lança `AccessDeniedException` (403); alcance do Administrador por `who.units()` (mesmo padrão de `Students.list`); organização sempre do principal e RLS por baixo; filtros por parâmetro nomeado (nunca concatenar texto do usuário, `q` escapado como em `Students.list`); foto por `Students.photoUrl`. Sem dado novo gravado, sem auditoria. `financialStatus` só na lista de alunos (STUDENT_MANAGE), fora de `StudentSimple` e `StudentRecords`.
- Arquivos (vistos): `backend/.../billing/ChargeController.java` (nova rota), `backend/.../student/Students.java` (`list`, `Row`, `Counts`, `Filter`, `rows`), `backend/.../student/StudentController.java` (parâmetro `financial`), `docs/openapi.yaml` (`/charges`, `/students`), `frontend/src/App.tsx` (rota dentro de `RequireArea` Coordenador/Administrador), `frontend/src/features/permissions/menuCatalog.ts` (item "collections" já existe: só conferir), `frontend/src/features/students/{StudentsPage.tsx,useStudentColumns.ts,StudentFilters.tsx,useStudents.ts,types.ts}`, `frontend/src/mocks/handlers.ts`, `frontend/src/i18n/pt-BR.json`. A criar: `backend/.../billing/Charges.java` (consulta, registros de resposta, `ChargeSituation` como constante SQL pública para a lista de alunos reaproveitar), `frontend/src/features/charges/{ChargesPage.tsx,ChargeFilters.tsx,ChargeCards.tsx,useCharges.ts,types.ts}`, testes (`ChargesIT`, `ChargesIsolationIT`, `ChargesPage.test.tsx`, `charges.msw.test.ts`).
- Reuso: `PageRequest`/`PageResponse`, `QueryPage`/`DataTable`, `ManagementLayout`, estados de `components/states`, formatadores de `lib/format`, `Students.photoUrl`, `today(unit)` de `MonthlyGeneration` (mesma conta de fuso, em SQL por `at time zone u.timezone`), `Modalities.normalize` para a busca.
- Riscos: (1) lista de uma organização grande sem índice no filtro por situação calculada: medir com o volume de referência e só então criar índice; (2) cobertura de 100% do pacote `billing` (JaCoCo): `Charges` entra nesse piso, então todo ramo precisa de teste; (3) o filtro de turma duplica a regra de `Students.list`, manter igual; (4) `DataTable` talvez não tenha modo cartão: se não tiver, `ChargeCards` cobre só esta tela, sem mudar o componente comum.

## 5. Tarefas

Parte 1 — back-end

- [x] T1: `Charges.list` com situação calculada em SQL, alcance, paginação, ordenação e rota `GET /charges`; caminho no `docs/openapi.yaml` — cobre CA1, CA2, CA3, CA4, CA5, CA6, CA11 — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test=ChargesIT -Dtest=NoTest -Dsurefire.failIfNoSpecifiedTests=false`
- [x] T2: filtros (`quick`, período, situação, tipo, modalidade, turma, unidade, busca), validações 400/404, `counts` e `totals` — cobre CA7, CA8, CA9, CA10 — prova: mesma classe `ChargesIT` (casos de filtro)
- [x] T3: encaixe em `Students.list`: `financialStatus`, `financial=OVERDUE`, `counts.overdue`, parâmetro no controller e no OpenAPI — cobre CA12, CA13 — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test=StudentsFinancialIT -Dtest=NoTest -Dsurefire.failIfNoSpecifiedTests=false`
- [x] T4: testes de isolamento entre organizações e perfis nas duas rotas, com RLS, e contrato — cobre CA14, CA21 (lado servidor) — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test=ChargesIsolationIT` e `./backend/mvnw -f backend/pom.xml test -Dtest='OpenApiContractTest,ContractRoutesTest'`

Parte 2 — front-end

- [x] T5: tipos, `useCharges` (query da tela para a rota), `ChargesPage` com colunas, situação com cor e texto, rota `/cobrancas`, textos em `pt-BR.json` — cobre CA16, CA19 — prova: `npx vitest run src/features/charges/ChargesPage.test.tsx` em `frontend/`
- [x] T6: `ChargeFilters` (atalhos com `aria-pressed` e contagem, período, situação, tipo, modalidade, turma, busca) e rodapé de totais — cobre CA15 — prova: `npx vitest run src/features/charges`
- [x] T7: cartões no celular, quatro estados, acesso por perfil (menu e rota) e axe nos dois temas a 360 px — cobre CA17, CA18 — prova: `npx vitest run src/features/charges src/features/permissions`
- [x] T8: handlers MSW de `/charges` e dos parâmetros novos de `/students`, coluna "Situação financeira" e atalho "Em atraso" na lista de alunos — cobre CA20, CA21 — prova: `npx vitest run src/mocks src/features/students/StudentsPage.test.tsx`

## 6. Decisões

- D1: Paga com atraso quando a data do pagamento no fuso da unidade passa de `due_on + tolerance_days`, igual à regra de Em atraso; assim quem pagou dentro da tolerância sai Paga. Alternativa: comparar só com `due_on`; descartada por contradizer Em atraso.
- D2: "Vence hoje" e "Em atraso" valem só para cobrança aberta (`OPEN`); "Pendente" é a aberta com vencimento depois de hoje. Cobrança aberta de vencimento ontem com tolerância ainda aberta é Pendente (a tolerância adia o atraso, não o "Vence hoje").
- D3: "Pagas no mês" usa o mês de `paid_at` no fuso da unidade, não o mês de vencimento. Alternativa: mês do vencimento; descartada porque o atalho serve para ver o que entrou no mês.
- D4: `counts` ignora os demais filtros (só alcance e unidade), como em `Students.list` (R20 da #58); `totals` respeita todos os filtros. Totais: quantidade e soma em centavos, sem separar por situação.
- D5 (muda o comportamento): a situação financeira do aluno é `OVERDUE` quando há cobrança `OPEN` em atraso de qualquer tipo, avulsa inclusa; senão `CURRENT`. A inativação por atraso (Sprint 12) tem a própria regra e exclui avulsa (RN-COB-11); aqui a lista informa o mesmo que a tela de cobranças mostra em atraso. Alternativa: excluir avulsa; descartada para o aluno não aparecer sem atraso enquanto a cobrança dele aparece em atraso.
- D6: a ordem padrão é vencimento decrescente (as mais recentes e futuras primeiro); o atalho Em atraso não muda a ordem. Alternativa: crescente.
- D7: sem ponta a ponta Playwright: a lista é leitura e não é fluxo crítico (`@critical`); a cobertura é de integração e tela.
- D8: sem recurso liberado por organização: a sprint não o pede e a tela só lê.
- Suposição: o fuso vem de `unit.timezone` (coluna vista em `MonthlyGeneration.today`). Se a coluna tiver outro nome ou for opcional, a conta usa o fuso padrão da organização.
- Suposição: as telas do canvas não foram vistas; layout de colunas e cartões segue o padrão da lista de alunos e o dono confere no PR.

## Construção

Parte 1 (back-end) concluída em sessão anterior; Parte 2 (front-end, T5 a T8) concluída nesta sessão.

- T1 — arquivos: `backend/.../billing/Charges.java` (novo), `billing/ChargeController.java` (rota `GET /charges`), `student/Students.java` (`photoUrl` público), `docs/openapi.yaml` (`/charges`, `ChargeRow`, `ChargeList`, `ChargeSituation`), `backend/src/test/.../billing/ChargesIT.java` — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test=ChargesIT -Dtest=NoTest -Dsurefire.failIfNoSpecifiedTests=false` → 10 testes, 0 falhas
- T2 — filtros, validações 400/404, `counts` e `totals` em `Charges.list` (mesma classe e mesmo `ChargesIT`) — prova: mesmo comando → passou
- T3 — arquivos: `student/Students.java` (`Row.financialStatus`, `Counts.overdue`, `Filter.financial`, constantes `OVERDUE` e `FINANCIAL` reaproveitando `Charges.overdue`), `student/StudentController.java` (`financial`), `docs/openapi.yaml` (`/students`, `StudentRow`, `StudentList`), `StudentsFinancialIT.java` (novo), `StudentRecordsTest.java` (construtor de `Row` e `financialStatus` na lista de campos ausentes do Professor) — prova: `verify -Dit.test=StudentsFinancialIT -Dtest=StudentRecordsTest` → passou
- T4 — arquivos: `billing/ChargesIsolationIT.java` (novo; organizações, perfis, filtro, RLS sem filtro no SQL) — prova: `verify -Dit.test=ChargesIsolationIT -Dtest='OpenApiContractTest,ContractRoutesTest'` → passou
Regressão: `./backend/mvnw -f backend/pom.xml verify` (ITs, com JaCoCo 80% e 100% nos pacotes protegidos) → 590 testes, 0 falhas, BUILD SUCCESS; `./backend/mvnw -f backend/pom.xml test` → verde. Front-end não tocado nesta parte, não rodou.
Desvios: filtro de modalidade confere `organization_modality` (404 se a organização não tem a modalidade); `Students.photoUrl` passou a público para `Charges` reutilizar; o filtro de turma duplica o de `Students.list`, como o risco 3 previa. Sem migração: os índices atuais bastaram para a prova (risco 1 não medido com volume de referência).
Não é do item: nenhum.

### Parte 2 (front-end)

- T5 — arquivos: `frontend/src/features/charges/{types.ts,useCharges.ts,ChargeCards.tsx,ChargesPage.tsx,ChargesPage.test.tsx}`, `App.tsx` (rota `/cobrancas` na área Coordenador/Administrador), `i18n/pt-BR.json` (`charges.*`, meios de pagamento Pix/cartão/boleto) — prova: `npx vitest run src/features/charges/ChargesPage.test.tsx` → 10 testes, 0 falhas
- T6 — arquivos: `features/charges/ChargeFilters.tsx`, rodapé de totais em `ChargesPage.tsx` (mesmo teste) — prova: `npx vitest run src/features/charges` → passou
- T7 — arquivos: `ChargeCard` (cartões abaixo de `md`), quatro estados, Professor levado a `/sem-acesso`, axe a 360 px nos dois temas (mesmo teste) — prova: `npx vitest run src/features/charges src/features/permissions` → passou
- T8 — arquivos: `mocks/handlers.ts` (`GET /charges`, `financial`, `counts.overdue` e `financialStatus` em `/students`), `mocks/charges.msw.test.ts` (novo), `features/students/{StudentsPage.tsx,StudentFilters.tsx,useStudentColumns.ts,types.ts}` (coluna opcional "Situação financeira" e atalho "Em atraso (N)"), `StudentsPage.test.tsx` (CA20), `students.msw.test.ts` (`counts` ganhou `overdue`), fixtures de `StudentRow` em `StudentEditDialog.test.tsx` e `StudentRecordPanel.test.tsx` — prova: `npx vitest run src/mocks src/features/students/StudentsPage.test.tsx` → passou
Regressão (Parte 2): em `frontend/`, `npm run lint && npm run typecheck && npm run build && npm run test:cov` → verdes (59 arquivos de teste nos diretórios tocados, 430 testes na rodada parcial, cobertura 96,69% de linhas); `npm run e2e` → 34 passados. Back-end não foi tocado nesta sessão (regressão da Parte 1 acima). `e2e:full` e a conferência visual contra o canvas não rodaram (o canvas é inacessível ao agente).
Desvios: `QueryPage` ganhou dois props opcionais e compatíveis, `cards` (lista em cartões abaixo de `md`, tabela escondida por CSS) e `footer` (recebe a página carregada), porque o corpo da lista é montado dentro dele; o plano previa não mexer no componente comum (risco 4). Os totais do rodapé viajam em `Page.counts` (`totalCount`, `totalCents`). Em jsdom o CSS não é aplicado, então tabela e cartões coexistem no DOM dos testes. Cores da situação usam só os tokens existentes (sem token novo); contraste real não medido (jsdom).
Não é do item: nenhum.

## Verificação

### Rodada 1 — OK — 2026-10-22, estado ff6433748be4d8b8f151fa65b7bfcf8b2b27e785

| Critério | Status | Evidência |
|---|---|---|
| CA1 a CA14 | PASSOU | `ChargesIT`, `ChargesIsolationIT`, `StudentsFinancialIT`, `StudentRecordsTest` dentro do `verify` (590 testes, 0 falhas); `Charges.java` lido: situação em SQL por fuso da unidade, alcance, filtros, 400/404, counts/totals, sem campo de juros |
| CA15 a CA20 | PASSOU | `ChargesPage.test.tsx`, `StudentsPage.test.tsx` dentro de `npm run test:cov` (988 testes, 0 falhas); `npm run lint` verde (sem texto literal); `formatDate` trata data de calendário sem deslocar fuso |
| CA21 | PASSOU | `OpenApiContractTest`/`ContractRoutesTest` no `verify`; `charges.msw.test.ts` e `students.msw.test.ts` no `test:cov` |

Comandos: `./backend/mvnw -f backend/pom.xml verify` → BUILD SUCCESS, 590 testes (JaCoCo ok); em `frontend/`: `npm run lint && typecheck && build && test:cov` → verdes, 988 testes, linhas 96,69%; `npm run e2e` → 34 passados.
Achados:
- sugestão, código, `Charges.java` FROM — `left join payment ... status = 'CONFIRMED'` duplicaria linha se uma cobrança tivesse dois pagamentos confirmados; hoje improvável, vale um índice único parcial ou garantia na #73.
- sugestão, código — contraste das cores de situação e conferência visual contra o canvas não medidos (jsdom); o dono confere no PR. Cobertura do 100% de `features/{billing,...}` não afetada (pasta nova `charges` fora do piso).

## Entrega

- O que muda: lista de cobranças em `/cobrancas` (Coordenador e Administrador), com filtros, situação calculada no servidor pelo fuso da unidade, totais, cartões no celular e quatro estados; `GET /charges` no contrato; coluna "Situação financeira" e atalho "Em atraso (N)" em Alunos (`GET /students` com `financial` e `counts.overdue`).
- Verificado: `verify` do back-end (590 testes, JaCoCo ok) e `lint`, `typecheck`, `build`, `test:cov` (988 testes, 96,69% de linhas) e `e2e` (34 passados) do front-end, na rodada 1 da verificação.
- Decisões: `QueryPage` ganhou os props opcionais `cards` e `footer`; `Students.photoUrl` ficou público para `Charges` reutilizar.
- Limitações: contraste das cores de situação e conferência visual contra o canvas não medidos (jsdom); `e2e:full` não rodou. Achado de código aberto para a #73: `left join payment` pode duplicar linha se houver dois pagamentos confirmados.
