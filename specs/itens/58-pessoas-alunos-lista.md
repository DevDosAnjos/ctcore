# 58 — RF-ALU-01 · Pessoas › Alunos (lista e filtros)

Origem: issue #58 (Sprint 06); `docs/especificacao/08-alunos-e-responsaveis.md` (RF-ALU-01, RN-ALU-07, "Dados"), `02-contas-e-acesso.md` (linhas "Alunos" da tabela de permissões) e o encaixe "ficha do aluno" de `specs/backlog.md`. Branch: `item/58-pessoas-alunos-lista`. Parte de `student` e `Students` (#54), de `age_range` (#41), do desenho de `GET /teachers` e `TeachersPage` (#42) e de `QueryPage`/`DataTable`.

## 1. Objetivo

Em Pessoas › Alunos, a gestão (Coordenador e Administrador) vê os alunos da organização em lista paginada, com foto e nome, unidade de cadastro, idade e faixa etária, e filtra por nome, unidade e faixa etária. Cada usuário escolhe quais colunas ver. O Administrador vê só os alunos da unidade dele (RN-ALU-07); o Coordenador, todos.

O que a lista mostra hoje é o que existe hoje: o aluno ainda não tem matrícula (#63), selos (#59) nem cobranças (Sprint 08). Os campos e filtros que dependem deles ficam como encaixe (D1) e não são entregues aqui.

Fora do escopo (encaixes): modalidades com turma, graduação, filtros por modalidade, turma e nível, filtros rápidos Ativos e Ex-alunos e o escopo do Administrador por modalidade na unidade (RN-ALU-07 completa), e a visão do Professor (lista simples) — todos em #63/#67; selos e o filtro por selos (#59); situação financeira e filtros rápidos Em atraso e Bloqueados (cobrança, Sprint 08+); ficha em painel (#60: o nome ainda não leva a lugar nenhum); notas de versão (Sprint 15).

## 2. Requisitos

- R1 (RF-ALU-01, D3): `GET /api/v1/students` devolve, no envelope `{items, page, size, total}`, os alunos da organização da sessão ao alcance de quem chama, cada um com `id`, `name`, `photoUrl` (URL temporária assinada ou nulo), `birthDate`, `age`, `minor`, `ageRange` (`id`, `name` ou nulo), `registeredUnit` (`id`, `name`) e `createdAt`; ordenados por nome (sem acento nem caixa) e depois id; aceita `page`, `size` (25, 50 ou 100, padrão 25) e `sort=name`.
- R2 (RF-ALU-01): filtros opcionais `q` (parte do nome, sem diferença de acento nem de maiúsculas), `unitId` e `ageRangeId`; combinam entre si (E); sem filtro devolve todos; `ageRangeId` devolve quem tem hoje idade dentro da faixa (`min_age` até `max_age`, sem teto quando nulo); id mal formado dá 400 e faixa de outra organização dá 404.
- R3 (RN-ALU-07, D2): o Coordenador alcança todos os alunos; o Administrador só os de `registered_unit_id` dentro das unidades dele; `unitId` fora do alcance dele devolve `items` vazio. O aluno aparece uma vez só, com a unidade de cadastro.
- R4 (permissão): `STUDENT_MANAGE` conferido no servidor: Coordenador e Administrador; Professor, Aluno e Responsável recebem 403 (D4); sem sessão 401.
- R5 (isolamento e dado mínimo): organização sempre a da sessão (corpo e URL nunca a definem); a resposta nunca traz CPF, e-mail, telefone, endereço nem contato de emergência; a foto sai só como URL temporária.
- R6 (colunas por usuário, D5): a tela tem a coluna Nome (foto e nome) sempre visível e as colunas Unidade, Idade, Faixa etária e Cadastrado em, que cada usuário liga ou desliga; a escolha fica guardada por conta no navegador e a lista abre com todas ligadas quando não há escolha.
- R7 (tela): `/alunos` mostra a lista, os filtros nome, unidade e faixa etária (limpar filtros) e a paginação; tem os quatro estados, funciona a partir de 360 px, passa no axe nos dois temas, tem todos os textos em `pt-BR.json`, avatar com iniciais quando não há foto; o item "Alunos" do menu fica disponível para quem tem `STUDENT_MANAGE` (D4).
- R8 (contrato e dados fictícios): `docs/openapi.yaml` e os handlers MSW refletem a rota e o formato; o seed `12-alunos.sql` basta para a lista (acrescentar só o que faltar para testar filtros, como alunos em duas unidades).

## 3. Critérios de aceite

- CA1 (R1): dado organização com alunos, quando o Coordenador faz `GET /students`, então 200 com `items` ordenados por nome (acento e caixa ignorados) e id, cada um com os campos de R1, `total`, `page` e `size` corretos; `size` fora de 25/50/100, `page` negativa e `sort` inválido dão 400.
- CA2 (R1): dado aluno com foto, então `photoUrl` é URL temporária; sem foto, nulo; `age` e `minor` batem com a data de nascimento e com a data de referência do sistema (o dia em que completa 18 anos já é adulto).
- CA3 (R2): dado vários alunos, quando filtra por `q` ("joao" acha "João"), `unitId` e `ageRangeId`, sozinhos e combinados, então só voltam os que atendem a todos; faixa sem teto pega todos acima do mínimo; `ageRangeId` inexistente ou de outra organização dá 404; valor que não é UUID dá 400.
- CA4 (R3): dado Administrador da unidade A e alunos cadastrados em A e em B, quando lista, então só os de A; `unitId=B` devolve `items` vazio e `total` 0; o Coordenador vê A e B e filtra por qualquer uma; Administrador sem unidade vê lista vazia.
- CA5 (R4): dado Professor, Aluno e Responsável, quando chamam `GET /students`, então 403; sem sessão, 401.
- CA6 (R5): dado aluno com CPF, e-mail, telefone, endereço e emergência gravados, então o texto bruto da resposta não contém nenhum deles nem as chaves `cpf`, `email`, `phone`, `street` e `emergencyName`.
- CA7 (R5): dado duas organizações com alunos de mesmo nome, então cada uma só vê os seus; um `unitId` ou `ageRangeId` de outra organização não revela nada (vazio ou 404); a leitura com o papel `ctcore_app` sem contexto devolve 0 linhas de `student`.
- CA8 (R6): dado a tela aberta, então Nome sempre aparece; desligar "Idade" some a coluna e a escolha volta ao reabrir a tela com a mesma conta, mas não vale para outra conta; sem armazenamento disponível a tela abre com todas as colunas.
- CA9 (R7): dado a tela em 360 px, então sem rolagem horizontal da página e axe sem violações nos dois temas; estados carregando, vazio (sem alunos, e outro texto com "Limpar filtros" quando o filtro não acha ninguém), erro com "Tentar de novo" e sem permissão; trocar filtro ou página refaz a consulta; foto ausente mostra as iniciais.
- CA10 (R7): dado o menu, então "Alunos" aparece e leva a `/alunos` para Coordenador e Administrador e não aparece para Professor.
- CA11 (R8): `OpenApiContractTest` e `ContractRoutesTest` passam com a rota; teste MSW cobre a rota no formato do servidor; `SeedDataIT` passa.
- CA12 (R1, R2, R7): ponta a ponta Playwright `@critical` "Coordenador filtra a lista de alunos": abre Pessoas › Alunos, vê a lista, filtra por nome e vê só o aluno esperado.

## 4. Plano técnico

- **Dados.** Não muda o esquema: lê `student` (índice `(organization_id, name_key, id)` de `V29`), `unit` e `age_range` (`V22`). Sem migração nova. Seed: conferir `infra/seed/12-alunos.sql`; só se faltar aluno em duas unidades ou faixa para os testes, acrescentar nele (`on conflict do nothing`).
- **API** (cookie como as demais): `GET /api/v1/students` (200 `StudentList` de `StudentRow`; 400, 401, 403, 404). Rota nova no `docs/openapi.yaml` no mesmo PR, ao lado das de `/students` já existentes. Reutiliza `br.com.ctcore.api.page.PageRequest`/`PageResponse`.
- **Back-end.** Em `br.com.ctcore.student`: acrescentar `Students.list(who, filtros, pageRequest)` (consulta com `JdbcClient` em `OrganizationTransactions.execute`; `requireAccess` já confere `STUDENT_MANAGE`; alcance do Administrador por `who.units()` em `registered_unit_id`, como `Students.detail`; busca por `name_key like` com `Modalities.normalize`, parâmetros ligados; faixa por `age_range`; idade e `minor` calculadas com a mesma regra e o mesmo relógio (`Clock`) que `StudentRegistration` e `MajorityNoticeJob` já usam, a conferir na construção; URL da foto como em `Students.detail` com `FileStorage`) e a rota em `StudentController.java`. Ponto único para a Sprint 07 trocar o escopo por modalidade (D2): o predicado do alcance fica em um método só.
- **Interface.** A criar `frontend/src/features/students/StudentsPage.tsx`, `StudentFilters.tsx`, `useStudents.ts` (`fetchStudents` e opções de filtro, como `useTeachers.ts`), `useStudentColumns.ts` (colunas escolhidas por conta, `localStorage` com `try/catch`, chave por conta) e testes. A alterar: `frontend/src/features/students/types.ts` (`StudentRow`, `StudentList`), `App.tsx` (rota `/alunos`), `features/permissions/menuCatalog.ts` (`students` com `available = true` e permissão `STUDENT_MANAGE`, D4; ajustar `buildMenu.test.ts`), `mocks/handlers.ts` (+ teste em `mocks/students.msw.test.ts`, que existe por #54), `i18n/pt-BR.json`. Reutiliza `QueryPage`, `DataTable`, `ManagementLayout`, o avatar com iniciais de `TeachersPage` (extrair para componente comum só se a repetição passar de duas, senão copiar) e as opções de `GET /units/reachable` e da lista de faixas etárias.
- **Segurança.** Organização, perfil e alcance vêm do principal; filtros são parâmetros ligados; sem CPF, e-mail, telefone, endereço nem emergência no DTO (minimização, LGPD); foto só por URL temporária; a rota só lê, não grava auditoria nem log com dado pessoal. Recusa por alcance de `unitId` é lista vazia, sem revelar existência. A preferência de colunas não guarda dado pessoal.
- **Riscos.** (1) A lista de hoje é por unidade de cadastro; #63 muda o alcance para modalidade na unidade (RN-ALU-07), por isso o predicado fica isolado. (2) Aluno sem faixa que o cubra mostra "—" e some do filtro por faixa; é regra de dados, não erro. (3) Faixas etárias podem se sobrepor: `ageRange` da linha é a de maior `min_age` entre as que cobrem a idade (D6).

## 5. Tarefas

Uma parte só: servidor (T1), contrato (T2), tela (T3) e ponta a ponta (T4).

- [x] T1: `Students.list` e a rota `GET /students` em `StudentController` com ordem, paginação, filtros, alcance e foto, e `StudentListIT` (lista, filtros, alcance Coordenador e Administrador, perfis, sem dado pessoal, isolamento e RLS) — cobre CA1 a CA7 — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test=StudentListIT -Dtest=NoSuchTest -Dsurefire.failIfNoSpecifiedTests=false -Djacoco.skip=true`.
- [x] T2: OpenAPI (operação e esquemas `StudentRow`, `StudentList`), tipos, handler MSW e teste, e ajuste do seed se faltar caso de teste — cobre CA11 — prova: `./backend/mvnw -f backend/pom.xml test -Dtest='OpenApiContractTest,ContractRoutesTest'`, `verify -Dit.test=SeedDataIT` (mesmos flags de T1) e `npx vitest run src/mocks` em `frontend/`.
- [x] T3: `StudentsPage`, `StudentFilters`, `useStudents`, `useStudentColumns`, rota, menu disponível, quatro estados, paginação, colunas por usuário, textos pt-BR; 360 px e axe nos dois temas — cobre CA8, CA9, CA10 — prova: `npx vitest run src/features/students src/features/permissions` em `frontend/`.
- [x] T4: e2e `@critical` "Coordenador filtra a lista de alunos" (`frontend/e2e/alunos.spec.ts`) e regressão completa (a regressão não cobre critério: confere cobertura e os 100% dos pacotes exigidos) — cobre CA12 — prova: `./backend/mvnw -f backend/pom.xml verify` e, em `frontend/`, `npm run lint && npm run typecheck && npm run build && npm run test:cov && npm run e2e`.

## 6. Decisões

- D1 (muda o comportamento): a lista entrega só o que tem dado hoje. Modalidades com turma, graduação, filtros por modalidade, turma e nível, filtros rápidos Ativos, Em atraso, Bloqueados e Ex-alunos (com contagem), situação financeira e selos dependem de matrícula (#63), cobrança (Sprint 08 em diante) e selos (#59), que o plano põe depois deste item; o plano manda deixar o encaixe e a sprint seguinte ligá-lo (`docs/plano-de-sprints.md`). Alternativa: criar aqui tabelas de matrícula e situação; descartada por invadir #63 e gerar migrações conflitantes. Ligar cada campo é do item citado no "Fora do escopo"; o dono confere no PR que a issue só fica inteira depois deles.
- D2 (muda o comportamento): até existir matrícula, o alcance do Administrador é a unidade de cadastro (`registered_unit_id`), a mesma regra provisória de #54 (D2). RN-ALU-07 completa (aluno aparece na unidade onde tem modalidade) entra com #63. Alternativa: mostrar a todo Administrador; descartada por violar "o Administrador só altera o que é da sua unidade".
- D3: lista paginada pelo envelope `PageResponse` (25/50/100, padrão 25), ordenada por nome; sem filtro de situação, traz todos os alunos (ainda não há situação). Alternativa: sem paginação; descartada porque alunos crescem com a organização.
- D4 (muda o comportamento): a rota usa `STUDENT_MANAGE` (Coordenador e Administrador) e o Professor recebe 403 e não vê o item de menu. A "lista simples" do Professor (nome, ativo ou bloqueado, `02`) depende de turma e matrícula e vem com #63/#67 (canvas `ProfessorAlunos`), junto da troca da permissão do menu para `STUDENT_VIEW`. Alternativa: devolver lista vazia ao Professor; descartada por mostrar tela vazia que parece erro.
- D5 (muda o comportamento): "colunas configuráveis por usuário" guardadas por conta no navegador (`localStorage`), sem tabela nem rota de preferência. Não segue o usuário entre aparelhos. Alternativa: preferência no servidor; descartada por criar tabela, rota, RLS e auditoria para um ajuste de tela sem dado sensível; vira item próprio se o dono quiser sincronizar entre aparelhos.
- D6: `ageRange` da linha é a faixa de maior `min_age` que cobre a idade (desempate por nome); o filtro `ageRangeId` usa os limites da faixa escolhida, sem depender dessa escolha.
- D7: o canvas `Alunos.dc.html`, `EstadoSemResultados.dc.html` e `GMAlunos.dc.html` (cartões no celular) não foram abertos; a tela segue os padrões do projeto (tabela em cartão, filtros no topo, tabela responsiva como `TeachersPage`) e o dono confere o layout no PR. Suposição: se o canvas exigir cartões no celular, entra como ajuste de tela sem mudar a API.
- Suposição: o nome do aluno não leva a lugar nenhum até a ficha (#60); se a ficha vier antes de o item fechar, o link entra nela.

## Construção

- T1 — arquivos: `backend/.../student/Students.java` (`list`, `Row`, `reachPredicate`), `StudentController.java` (`GET /students`), `backend/src/test/.../student/StudentListIT.java` (7 testes) — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test=StudentListIT -Dtest=NoSuchTest -Dsurefire.failIfNoSpecifiedTests=false -Djacoco.skip=true` → 7 testes, 0 falhas.
- T2 — arquivos: `docs/openapi.yaml` (`GET /students`, `StudentRow`, `StudentList`), `frontend/src/features/students/types.ts`, `frontend/src/mocks/handlers.ts`, `frontend/src/mocks/students.msw.test.ts` — prova: `OpenApiContractTest,ContractRoutesTest` → 7 testes, 0 falhas; `verify -Dit.test=SeedDataIT` (mesmos flags) → 14 testes, 0 falhas; `npx vitest run src/mocks` → 126 testes passam. Seed `12-alunos.sql` não mudou (ver Desvios).
- T3 — arquivos: `frontend/src/features/students/{StudentsPage,StudentFilters,useStudents,useStudentColumns}.ts(x)`, `StudentsPage.test.tsx`, `App.tsx`, `features/permissions/menuCatalog.ts`, `buildMenu.test.ts`, `i18n/pt-BR.json` — prova: `npx vitest run src/features/students src/features/permissions` → 26 testes passam (inclui axe a 360 px nos dois temas).
- T4 — arquivos: `frontend/e2e/alunos.spec.ts` — prova: `npm run e2e` → 29 passam.

Regressão: `./backend/mvnw -f backend/pom.xml verify` → 425 testes, 0 falhas, JaCoCo (80% e 100% dos pacotes exigidos) passou, BUILD SUCCESS; em `frontend/`: `npm run lint`, `npm run typecheck`, `npm run build` ok, `npm run test:cov` → 82 arquivos, 754 testes passam, sem reprovar limite; `npm run e2e` → 29 passam.
Desvios: (1) o seed não ganhou aluno em segunda unidade: a organização semeada tem uma unidade só, e os 4 alunos já cobrem faixas e nome; a segunda unidade é provada no `StudentListIT` e no MSW. (2) a coluna Nome não é ordenável: a rota só ordena por nome crescente. (3) `useStudentFilterOptions` lê `/age-ranges` (já existente) para as faixas. (4) o item "Alunos" passou de `STUDENT_VIEW` para `STUDENT_MANAGE` (D4), então o Professor deixa de ver o grupo "Pessoas"; `buildMenu.test.ts` foi ajustado.
Não é do item: nenhum.

## Verificação

### Rodada 1 — OK — 2026-10-20, estado 2bebe4fd53b5f5b21746851bd68a998fa03f12b6

| Critério | Status | Evidência |
|---|---|---|
| CA1 | PASSOU | `StudentListIT` (lists, paging): ordem, campos, total/page/size, 400 para size, page e sort inválidos |
| CA2 | PASSOU | `StudentListIT.lists`: foto com URL, sem foto nulo, 18 anos adulto, 17 anos e 364 dias menor |
| CA3 | PASSOU | `StudentListIT.filters`, `overlappingRanges`: q, unitId, faixa sem teto, combinados, 404 e 400 |
| CA4 | PASSOU | `StudentListIT` alcance: Administrador só da unidade, `unitId` alheia vazia com total 0, sem unidade vazia, Coordenador vê tudo |
| CA5 | PASSOU | `StudentListIT.permissions`: 403 para perfis sem permissão, 401 sem sessão |
| CA6 | PASSOU | `StudentListIT.minimal`: texto bruto sem os segredos nem as chaves |
| CA7 | PASSOU | `StudentListIT.isolation`: mesmo nome em duas organizações, unitId vazio, faixa 404, `ctcore_app` sem contexto |
| CA8 | PASSOU | `StudentsPage.test.tsx` (vitest, 152 testes passam) e leitura de `useStudentColumns.ts` (chave por conta, try/catch) |
| CA9 | PASSOU | `StudentsPage.test.tsx`: quatro estados, axe a 360 px nos dois temas, iniciais |
| CA10 | PASSOU | `buildMenu.test.ts` passa |
| CA11 | PASSOU | `OpenApiContractTest,ContractRoutesTest` 7 testes; `SeedDataIT` 14 testes; `src/mocks` passa |
| CA12 | PASSOU | `npx playwright test e2e/alunos.spec.ts` → 1 passou |

Comandos: `StudentListIT` 7/7; `OpenApiContractTest,ContractRoutesTest` 7/7; `SeedDataIT` 14/14; `npx vitest run src/features/students src/features/permissions src/mocks` 152/152; `npm run lint` e `npm run typecheck` sem erro; e2e alunos 1/1. Regressão completa (`verify`, `test:cov`, `e2e`) não reexecutada; vale o relato da Construção (425, 754 e 29 testes).
Achados:
- sugestão, código, `Students.list` — o `ageRange` calculado na consulta repete a expressão de idade várias vezes; legível, mas poderia ficar numa subconsulta.

## Entrega

Lista de alunos em Pessoas › Alunos: `GET /api/v1/students` paginada (25/50/100) com busca, unidade e faixa etária, alcance por unidade para o Administrador, 403 ao Professor, isolamento por organização; tela com os quatro estados, colunas configuráveis (guardadas no navegador), OpenAPI, MSW e e2e.

Verificação: rodada 1 OK (12 critérios). Regressão: back-end `verify` 425 testes, front-end `test:cov` 754 testes, `e2e` 29, lint, typecheck e build ok.

Para o dono conferir no PR: D1 (modalidade, turma, graduação, filtros rápidos, situação financeira e selos ficam para #63, Sprint 08 e #59, então a issue #58 só fica inteira depois deles; o PR segue o AGENTS.md com `Closes #58`), D2, D4 (Professor sem acesso até #63/#67), D5 e D7 (layout do canvas não aberto).
