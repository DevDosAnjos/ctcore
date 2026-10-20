# 56 — RF-PLA-02 · Tabela de planos

Origem: issue #56 (Sprint 06); `docs/especificacao/07-planos-e-beneficios.md` (RF-PLA-02, RN-PLA-02, RN-PLA-03, RN-PLA-06), `02-contas-e-acesso.md` (permissão `PLANS`). Branch: `item/56-tabela-de-planos`. Constrói sobre #55 (tabela `plan`, `Plans`, `PlansPage`) e segue o padrão de #46 (`TrainingClasses`, `ClassesPage`).

## 1. Objetivo

A gestão abre a tabela de planos pelo botão "Ver planos" do cartão de #55 e vê, por plano, modalidade, nome, valor, frequência semanal, alunos usando e situação, com filtro por modalidade. Coordenador vê as unidades todas; Administrador, as dele.

Fora do escopo: criar, editar e desativar plano e histórico de valores (#57), contagem real de alunos usando (Sprint 07), benefícios (Sprint 07), migração nova (a tabela `plan` já existe).

## 2. Requisitos

- R1 (RF-PLA-02, D1): `GET /api/v1/plans` devolve, no envelope `{items, page, size, total}`, os planos da organização da sessão com `id`, `unit` (id e nome), `modality` (id e nome), `name`, `valueCents`, `weeklyFrequency` (2, 3 ou nulo = livre), `validOtherUnits`, `studentsUsing` e `status`, ordenados por modalidade e nome.
- R2 (RF-PLA-02, D2): filtros opcionais `modalityId`, `unitId`, `status` (`ACTIVE` ou `INACTIVE`) e `q` (parte do nome, sem diferença de acento ou maiúsculas), combináveis; `status` inválido dá 400.
- R3 (RN-PLA-03): o valor sai em centavos inteiros e a tela o formata em reais.
- R4 (RN-PLA-02): a frequência é só informativa: nenhuma coluna, campo ou texto do item controla ou mostra aulas feitas, presença ou frequência de comparecimento; "livre" aparece quando nula.
- R5 (D3): `studentsUsing` vem de um ponto único que devolve 0 até a Sprint 07.
- R6 (permissão e alcance, D4): `PLANS` conferido no servidor: Coordenador e Administrador acessam; Professor, Aluno e Responsável 403; sem sessão 401. Administrador só vê unidades dele; `unitId` fora do alcance dá lista vazia. Organização sempre a da sessão.
- R7 (isolamento): filtro de organização no código e RLS no banco; teste de isolamento entre organizações e entre perfis.
- R8 (tela, D5): `/planos/lista` mostra a tabela com paginação e o filtro por modalidade (e unidade e situação); o botão "Ver planos" de `/planos` passa a levar a ela; quatro estados, 360 px, axe nos dois temas, textos em `pt-BR.json`. Desativado mostra o selo "Desativado". Sem edição aqui (RN-PLA-06 fica para #57).
- R9 (contrato): `docs/openapi.yaml` e handlers MSW refletem a rota; seed existente (#55) basta.
- R10 (auditoria): a rota só lê, então não grava auditoria (D6).

## 3. Critérios de aceite

- CA1 (R1, R3): dado planos na organização, quando o Coordenador faz `GET /plans`, então 200 com `items` ordenados por modalidade e nome, cada um com unidade, modalidade, `valueCents` inteiro, `weeklyFrequency`, `studentsUsing` 0 e `status`; `total`, `page` e `size` corretos e `size` fora de 25/50/100 dá 400.
- CA2 (R2): dado planos de duas modalidades, unidades e situações, quando filtra por `modalityId`, `unitId`, `status` e `q` ("judo" acha "Judô"), sozinhos e combinados, então só voltam os que atendem a todos; `status=XYZ` dá 400.
- CA3 (R4, R5): dado o esquema, o corpo da resposta e `pt-BR.json` (chaves de `plans`), então nada menciona presença ou aulas feitas; plano sem frequência vem com `weeklyFrequency` nulo e a tela mostra "Livre".
- CA4 (R6): dado Administrador com a unidade A e planos em A e B, quando lista, então só vê os de A; `unitId` de B dá `items` vazio; Professor, Aluno e Responsável recebem 403; sem sessão 401.
- CA5 (R7): dado planos de duas organizações, quando o Coordenador de A lista, então não vê os de B; com `ctcore_app` e contexto de A, consulta direta a `plan` não enxerga B.
- CA6 (R8): dado a tela em `/planos/lista`, quando abre, então vê a tabela (modalidade, nome, valor em reais, frequência, alunos usando, situação) e, ao escolher uma modalidade, a consulta é refeita só com ela; em 360 px sem rolagem horizontal da página e axe sem violações nos dois temas.
- CA7 (R8): dado a tela, então carregando mostra o estado de carregamento; sem planos mostra vazio (e outro texto com "Limpar filtros" quando o filtro não acha nada); erro mostra "Tentar de novo"; Professor vê a tela de sem permissão; plano desativado traz o selo.
- CA8 (R8, R9): dado `/planos`, quando clica em "Ver planos", então vai a `/planos/lista`; `OpenApiContractTest` e `ContractRoutesTest` passam; teste MSW cobre a rota no formato do servidor.

## 4. Plano técnico

- Dados: não muda (`plan` de `V30`). Consulta com junção a `unit` e `modality` pelos ids; ordem por `modality.name`, `plan.name_key`.
- API: `GET /api/v1/plans?page&size&q&modalityId&unitId&status` (200 `PageResponse` de `PlanRow`; 400, 401, 403). Entra em `docs/openapi.yaml`. Usa `PageRequest`/`PageResponse` como `TrainingClasses`. `studentsUsing` por método único (hoje 0).
- Back-end: alterar `plan/Plans.java` (método `list`, reaproveitando `requireAccess` e o cálculo de alcance do `summary`, extraído para método privado) e `plan/PlanController.java` (`@GetMapping`).
- Interface: criar `frontend/src/features/plans/PlansTablePage.tsx`, `PlanFilters.tsx` e testes; alterar `usePlans.ts` (`fetchPlans`), `PlansPage.tsx` (botão "Ver planos" vira link ativo), `App.tsx` (rota `/planos/lista` em `RequireArea` Coordenador e Administrador), `mocks/handlers.ts` (+ teste MSW) e `i18n/pt-BR.json`. Usa `QueryPage`/`DataTable`, como `ClassesPage`; opções de modalidade de `GET /modalities` (só ativas) e de unidade de `/units/reachable`.
- Segurança: perfil e alcance vêm do principal; parâmetros ligados; sem dado pessoal na resposta; Professor nunca vê plano.
- Arquivos: a alterar (vistos): `Plans.java`, `PlanController.java`, `PlansPage.tsx`, `usePlans.ts`, `App.tsx`, `mocks/handlers.ts`, `pt-BR.json`, `docs/openapi.yaml`; a criar: `PlanListIT.java`, `PlansTablePage.tsx`, `PlanFilters.tsx`, `PlansTablePage.test.tsx`, `plansList.msw.test.ts`.
- Reuso: `PageRequest`/`PageResponse`, `QueryPage`, `DataTable`, `ClassFilters` como modelo, `formatMoney` (usado em `PlansPage`), `ManagementLayout`, `Modalities.normalize` para o `q`.
- Riscos: (1) #57 acrescenta ação de editar na mesma tabela; a coluna de ações entra lá. (2) `studentsUsing` fixo em 0 até a Sprint 07. (3) Canvas `Planos.dc.html` inacessível: o dono confere o layout no PR.

## 5. Tarefas

Uma parte só.

- [x] T1: `Plans.list` e `GET /plans` com permissão, alcance, paginação, filtros e `PlanListIT` (lista, ordem, filtros, 400, Administrador por unidade, perfis, isolamento com RLS, sem presença no esquema e na resposta) — cobre CA1 a CA5 — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test=PlanListIT`.
- [x] T2: OpenAPI (`GET /plans`, `PlanRow`, `PlanList`) e handler MSW com teste — cobre CA8 — prova: `./backend/mvnw -f backend/pom.xml test -Dtest='OpenApiContractTest,ContractRoutesTest'` e `npx vitest run src/mocks` em `frontend/`.
- [x] T3: `PlansTablePage`, `PlanFilters`, `fetchPlans`, rota, link em "Ver planos", textos pt-BR, quatro estados, 360 px e axe — cobre CA3 (textos), CA6, CA7, CA8 (link) — prova: `npx vitest run src/features/plans` em `frontend/`.
- [x] T4: regressão completa (não cobre critério: confere cobertura e os pacotes exigidos) — prova: `./backend/mvnw -f backend/pom.xml verify` e, em `frontend/`, `npm run lint && npm run typecheck && npm run build && npm run test:cov && npm run e2e`.

## 6. Decisões

- D1: lista com paginação padrão do projeto (25/50/100), como as demais tabelas. Alternativa: devolver tudo; descartada porque a organização pode ter muitos planos e o padrão já existe.
- D2: além do filtro por modalidade pedido no card, a tabela aceita unidade, situação e busca por nome, porque o Coordenador vê várias unidades e a lista inclui desativados. Alternativa: só modalidade; descartada por deixar a lista inútil em organização com várias unidades.
- D3: `studentsUsing` zero em ponto único até a Sprint 07 (matrícula), como em #55.
- D4 (muda o comportamento): mesma permissão `PLANS` e mesmo alcance do resumo de #55 (Administrador só as unidades dele; fora do alcance, lista vazia e não erro). Alternativa: 404; descartada por não expor se a unidade existe.
- D5: a tabela fica em `/planos/lista` e a página `/planos` continua sendo a dos dois cartões (D1 de #55). Alternativa: tabela dentro de `/planos`; descartada porque o cartão "Ver planos" já a aponta como tela própria.
- D6: sem auditoria nem notas de versão: só leitura; a gravação entra em #57.
- Suposição: o canvas `Planos.dc.html` não está acessível; se a tabela do canvas tiver outras colunas além das do card, ajusta-se no PR.

## Construção

- T1 — arquivos: `backend/.../plan/Plans.java` (`list`, `reach` extraído do `summary`), `PlanController.java` (`GET /plans`), `PlanListIT.java` — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test=PlanListIT` → 4 testes, 0 falhas
- T2 — arquivos: `docs/openapi.yaml` (`/plans`, `PlanRow`, `PlanList`), `frontend/src/mocks/handlers.ts` (rota e `mockPlans` com id, nome, modalidade e frequência), `mocks/plans.msw.test.ts` — prova: `./backend/mvnw -f backend/pom.xml test -Dtest='OpenApiContractTest,ContractRoutesTest'` → 7 testes ok; `npx vitest run src/mocks` → ok
- T3 — arquivos: `features/plans/PlansTablePage.tsx`, `PlanFilters.tsx`, `PlansTablePage.test.tsx`, `usePlans.ts` (`fetchPlans`, `usePlanFilterOptions`), `PlansPage.tsx` (link) e `PlansPage.test.tsx`, `App.tsx`, `features/classes/ClassFilters.tsx` (`Filter` exportado), `i18n/pt-BR.json` — prova: `npx vitest run src/features/plans src/mocks` → 21 arquivos, 137 testes ok
- T4 — ver Regressão.

Regressão: `./backend/mvnw -f backend/pom.xml verify` → 408 testes, 0 falhas, JaCoCo ok; em `frontend/`: `npm run lint`, `typecheck`, `build` ok; `npm run test:cov` → 719 testes ok (cobertura exigida atendida); `npm run e2e` → 28 passaram.
Desvios: teste MSW da rota entrou em `mocks/plans.msw.test.ts` (já existente) e não em `plansList.msw.test.ts`; `Filter` de `ClassFilters.tsx` foi exportado para reuso em `PlanFilters`.
Não é do item: nenhum.

## Verificação

### Rodada 1 — OK — 2026-10-20, estado dcc9679940b46ec270c031d9d0870563d7ab1b89

| Critério | Status | Evidência |
|---|---|---|
| CA1 | PASSOU | `PlanListIT.list` (ordem, campos, total/page/size, size=10 dá 400); verify do back-end verde |
| CA2 | PASSOU | `PlanListIT.filters` (modalidade, unidade, situação, q sem acento, combinados, `status=XYZ` 400) |
| CA3 | PASSOU | `PlanListIT.list` (sem "attend/presen/lesson", frequência nula); teste de `pt-BR.json` em `PlansTablePage.test.tsx`; "Livre" na tela |
| CA4 | PASSOU | `PlanListIT.permissions` (Administrador só a unidade dele, `unitId` alheio vazio, Professor/Aluno/Responsável 403, anônimo 401) |
| CA5 | PASSOU | `PlanListIT.isolation` (outra organização e consulta direta com `ctcore_app`) |
| CA6 | PASSOU | `PlansTablePage.test.tsx` (colunas, filtro por modalidade, axe a 360 px nos dois temas) |
| CA7 | PASSOU | `PlansTablePage.test.tsx` (vazio, Limpar filtros, erro, Professor sem permissão, selo); carregando coberto pelo `QueryPage` compartilhado (ver sugestão) |
| CA8 | PASSOU | `PlansPage.test.tsx` (link para `/planos/lista`); `OpenApiContractTest` 4 ok, `ContractRoutesTest` 3 ok; `plans.msw.test.ts` |

Comandos: `./backend/mvnw -f backend/pom.xml verify` → exit 0, `PlanListIT` 4 testes sem falha; em `frontend/`: `npm run lint`, `typecheck`, `build`, `test:cov` → ok (linhas 96,98%); `npx vitest run src/features/plans src/mocks` → ok; `npm run e2e` → 28 passaram.
Achados:
- sugestão, código, `frontend/src/features/plans/PlansTablePage.test.tsx` — o estado "carregando" da tabela não é asserido (só se espera ele sumir); o `PlansPage.test.tsx` cobre o da página de cartões.
- sugestão, código, `Plans.list` — `studentsUsing` é chamado por linha dentro da consulta; hoje devolve 0, mas na Sprint 07 deve virar contagem agrupada para evitar N+1.

## Entrega

- Novo `GET /api/v1/plans` (paginado; filtros por modalidade, unidade, situação e busca por nome) e tela `/planos/lista` com a tabela de planos, ligada ao cartão "Ver planos" de `/planos`.
- Mesma permissão e alcance do resumo de #55; fora do alcance a lista vem vazia. Isolamento por organização e por perfil coberto em `PlanListIT`; contrato em `docs/openapi.yaml` e handlers MSW.
- Verificado: `verify` do back-end verde, lint/typecheck/build/test:cov/e2e do front verdes (rodada 1 OK).
- Limitações: `studentsUsing` é 0 até a Sprint 07 (deve virar contagem agrupada); estado "carregando" da tabela não é asserido em teste próprio.
