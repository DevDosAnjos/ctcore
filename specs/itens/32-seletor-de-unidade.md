# 32 — RF-ORG-05 · Seletor de unidade e "Todas as unidades"

Origem: issue #32 (Sprint 03); `docs/especificacao/01-organizacao-e-isolamento.md` (RF-ORG-05, RN-ORG-04, RN-ORG-08, RN-ORG-09), `02-contas-e-acesso.md` (linha "Todas as unidades"). Branch: `item/32-seletor-de-unidade`.

## 1. Objetivo

Dar à gestão um seletor de unidade na barra superior, visível quando o usuário alcança duas ou mais unidades. O Coordenador também escolhe "Todas as unidades" (só consulta); o Administrador escolhe entre as suas, sem visão somada. A escolha vale para as telas da gestão e substitui o seletor provisório da tela Configurações › Unidade (#31).

Fora do escopo: Início consolidado, financeiro e relatórios somados (itens próprios das sprints seguintes; aqui só existe a escolha e o contexto que eles vão ler); lista de unidades para outros fins (#29); desativar unidade (#34).

## 2. Requisitos

- R1 (RF-ORG-05): `GET /api/v1/units/reachable` devolve as unidades ativas ao alcance do vínculo (id, nome, nome curto) e `allUnits` (booleano): Coordenador, todas as ativas da organização e `allUnits=true`; Administrador e Professor, só as unidades do vínculo e `allUnits=false`; portal e anônimo não acessam (D1).
- R2 (RN-ORG-04, RF-ORG-05): a barra superior da gestão mostra o seletor só com duas ou mais unidades ao alcance; com uma, nada aparece.
- R3 (RN-ORG-08, RN-ORG-09): o Coordenador vê no seletor a opção "Todas as unidades"; Administrador e Professor nunca a veem.
- R4 (RN-ORG-08): em "Todas as unidades" nenhuma tela de cadastro ou alteração opera: Configurações › Unidade pede para escolher uma unidade e não mostra o formulário; o servidor continua exigindo o id da unidade em toda alteração (nenhuma rota nova altera dado).
- R5 (RN-ORG-09): o Administrador escolhe entre as suas unidades; o servidor não devolve unidade de que ele não participa nem a opção somada.
- R6 (escolha): a escolha fica guardada no navegador por vínculo; valor guardado que não esteja mais ao alcance é descartado, voltando ao padrão: "Todas" para o Coordenador, a primeira unidade para os demais (D2, D3).
- R7 (tela): seletor com os quatro estados (carregando, erro com nova tentativa, sem permissão e vazio sem seletor), a partir de 360 px, axe nos dois temas, textos em `pt-BR.json`.
- R8 (#31): Configurações › Unidade passa a usar a unidade escolhida no topo e perde o seletor próprio e o `?unidade=`.
- R9 (isolamento, RNF-31): a rota só enxerga unidades da organização do vínculo ativo; `organizationId` do cliente é ignorado.
- R10 (contrato): rota em `docs/openapi.yaml` e no handler MSW, idênticos à implementação.

## 3. Critérios de aceite

- CA1 (R1, R3): dado Coordenador de organização com 3 unidades ativas e 1 inativa, quando chama `GET /units/reachable`, então 200 com as 3 ativas e `allUnits=true`.
- CA2 (R1, R5): dado Administrador de 2 das 3 unidades, então vêm só as 2 e `allUnits=false`; dado Professor de 1 unidade, vem só essa.
- CA3 (R1, R9): dado Coordenador de outra organização, então nenhuma unidade da primeira aparece; `organizationId` na consulta é ignorado.
- CA4 (R1): dado portal do aluno ou sem sessão, então 403 e 401.
- CA5 (R2): dado usuário com 1 unidade, quando abre a gestão, então não há seletor; com 2, há.
- CA6 (R3): dado Coordenador com 2 unidades, então o seletor lista "Todas as unidades" e as 2; dado Administrador com 2, lista só as 2.
- CA7 (R4): dado Coordenador em "Todas as unidades", quando abre Configurações › Unidade, então vê o pedido para escolher uma unidade e nenhum campo editável; ao escolher uma, o formulário daquela unidade aparece.
- CA8 (R6): dado escolha guardada de unidade que saiu do alcance, quando a gestão abre, então usa o padrão; escolha válida é mantida ao recarregar.
- CA9 (R7): dado falha da rota, então a barra mostra erro com "Tentar de novo" sem derrubar a página; 403 não mostra seletor; sem violações do axe nos dois temas em 360 px.
- CA10 (R8): dado Administrador com 2 unidades, quando troca a unidade no topo em Configurações › Unidade, então o formulário recarrega com a outra unidade.
- CA11 (R10): o teste de contrato (`OpenApiContractTest`, `ContractRoutesTest`) e o teste MSW passam com a rota.

## 4. Plano técnico

- Dados: não muda (usa `unit` e o vínculo da sessão).
- API: `GET /units/reachable` devolve `{ allUnits, units: [{id, name, shortName}] }`; 401 anônimo, 403 portal. Coordenador: unidades ativas da organização (consulta no contexto de RLS); demais: unidades do vínculo (`unitIds`) filtradas por `unit.active` e pela organização. `allUnits` vem de `Permissions.grant(ALL_UNITS_CONSOLIDATED) != NONE`. Somente leitura: sem auditoria.
- Interface: `UnitScopeProvider` (contexto e `useUnitScope`) dentro de `ManagementLayout`, carrega a rota e guarda a escolha em `localStorage` por `activeLinkId` (com try/catch), com valor `"all"` ou id. `UnitSwitcher` (select nativo com rótulo acessível) em `headerExtra`, ao lado de `ChooseLinkTrigger`. `UnitSettingsPage` lê a unidade do contexto e, em `"all"`, mostra o aviso (R4). O Início mostra a abrangência escolhida em uma linha, sem dados somados. Chaves novas `unitScope.*` em `pt-BR.json`.
- Segurança: permissão conferida no servidor; o seletor só esconde, e a barreira de cada dado continua por rota e unidade (#27). A escolha no navegador não é confiável e nenhuma rota a lê.
- Arquivos (vistos): `backend/src/main/java/br/com/ctcore/organization/UnitController.java`, `UnitSettings.java`, `docs/openapi.yaml`, `frontend/src/features/permissions/ManagementLayout.tsx`, `frontend/src/features/units/UnitSettingsPage.tsx` e seu teste, `frontend/src/mocks/handlers.ts`, `frontend/src/i18n/pt-BR.json`, `frontend/src/App.tsx`. A criar: `UnitScope.java` (organization), `UnitScopeProvider.tsx` e `UnitSwitcher.tsx` em `frontend/src/features/units/`, e os testes.
- Reuso: `AccountPrincipal`, `Permissions`, `OrganizationTransactions`, `api`, `ChooseLinkTrigger` como modelo de item de cabeçalho, componentes de `components/states`, `SessionView.links[].unitIds`.
- Riscos: confirmar na construção que `SessionService` preenche `unitIds` para Administrador e Professor (o Coordenador pode vir vazio); os testes da #31 que usam `?unidade=` precisam ser ajustados.

## 5. Tarefas

- [x] T1: serviço e rota `GET /units/reachable` com regras por papel e organização — cobre CA1, CA2, CA3, CA4 — prova: teste unitário e `UnitReachableIT` (isolamento entre organizações e perfis) com `./backend/mvnw -f backend/pom.xml verify`
- [x] T2: rota no `docs/openapi.yaml` e handler MSW com os mesmos papéis — cobre CA11 — prova: `./backend/mvnw -f backend/pom.xml test -Dtest='OpenApiContractTest,ContractRoutesTest'` e teste MSW em `npm test`
- [x] T3: `UnitScopeProvider` com escolha guardada, descarte do inválido e padrão por papel — cobre CA8 — prova: teste do provider em `npm test`
- [x] T4: `UnitSwitcher` na barra superior, regra de visibilidade, "Todas as unidades" e quatro estados — cobre CA5, CA6, CA9 — prova: teste de tela com axe nos dois temas em 360 px
- [x] T5: Configurações › Unidade usa o contexto, remove seletor e `?unidade=`, aviso em "Todas" — cobre CA7, CA10 — prova: `UnitSettingsPage.test.tsx` ajustado
- [x] T6: textos `unitScope.*` e linha de abrangência no Início — não cobre critério: exigência de textos em tradução e de mostrar o contexto — prova: `npm run lint && npm run typecheck`
- [x] T7: regressão completa e cobertura — não cobre critério: portão do CI — prova: `verify` do back-end e `npm run test:cov && npm run e2e`

## 6. Decisões

- D1 (muda o comportamento): rota nova própria, aberta aos três papéis da gestão, em vez de reutilizar `/units/mine`. Motivo: `/units/mine` é da linha `UNIT_SETTINGS` e nega o Professor, mas o seletor serve a quem alcança duas ou mais unidades. Alternativa descartada: abrir `/units/mine`, que mudaria a regra da #31.
- D2: a escolha fica só no navegador (`localStorage` por vínculo), sem coluna nem preferência no servidor. Motivo: é conveniência de cada pessoa e o servidor decide o acesso por rota. Alternativa descartada: guardar no servidor, que exigiria tabela e auditoria sem necessidade.
- D3 (muda o comportamento): o padrão do Coordenador é "Todas as unidades"; o dos demais, a primeira unidade em ordem de nome. Motivo: o Coordenador abre no Início consolidado (canvas `InicioCoordenador`). Alternativa: primeira unidade para todos.
- D4: unidades inativas não entram no seletor. Motivo: não há operação possível nelas (RN-ORG-11 só mantém o histórico).
- Suposição: o Professor tem seletor se estiver vinculado a duas ou mais unidades, pois a especificação diz "unidades ao alcance do usuário". Se o dono quiser só Coordenador e Administrador, basta negar o papel na rota.
- Suposição: Início, financeiro e relatórios somados ainda não existem; aqui "Todas as unidades" só muda o contexto e bloqueia as telas de alteração existentes.

## Construção

- T1 — arquivos: `backend/.../organization/UnitScope.java` (novo), `UnitController.java`, `backend/src/test/.../organization/UnitReachableIT.java` (novo) — prova: `./backend/mvnw -f backend/pom.xml verify` → 177 testes, 0 falhas (UnitReachableIT: 2 testes)
- T2 — arquivos: `docs/openapi.yaml`, `frontend/src/mocks/handlers.ts`, `frontend/src/mocks/reachable.msw.test.ts` (novo) — prova: `-Dtest='OpenApiContractTest,ContractRoutesTest'` → 4 e 3 testes, 0 falhas; `npx vitest run src/mocks` → passou
- T3 — arquivos: `frontend/src/features/units/UnitScopeProvider.tsx` (novo), `UnitScope.test.tsx` (novo, CA8) — prova: `npx vitest run src` → 344 passaram
- T4 — arquivos: `UnitSwitcher.tsx` (novo), `ManagementLayout.tsx`, `UnitScope.test.tsx` (CA5, CA6, CA9, axe claro e escuro a 360 px) — prova: `npx vitest run src` → 344 passaram
- T5 — arquivos: `UnitSettingsPage.tsx`, `useUnitSettings.ts` (sai `useMyUnits`), `UnitSettingsPage.test.tsx` (CA7, CA10), `e2e/configuracao-unidade.spec.ts` — prova: `npx vitest run src` → 344 passaram
- T6 — arquivos: `frontend/src/i18n/pt-BR.json` (`unitScope.*`, sai `unitSettings.choose`), `App.tsx` (linha de abrangência) — prova: `npm run lint && npm run typecheck` → sem erros
- T7 — prova: `verify` do back-end → BUILD SUCCESS (JaCoCo ok); `npm run build`, `npm run test:cov` (344 testes, 95% de linhas), `npm run e2e` → 13 passaram
Regressão: sem falhas; nada que passava passou a falhar.
Desvios: (1) o front usa só `/units/reachable` para as unidades (não lê `unitIds` da sessão, que vem vazio no mock); (2) a opção "Todas as unidades" só existe com 2 ou mais unidades ao alcance, para não deixar o Coordenador de 1 unidade preso sem poder editar; (3) a rota `/units/mine` continua no servidor (contrato da #31), mas o front deixou de usá-la; (4) os testes de UnitsPage, NewUnitPage e OrganizationSettingsPage ganharam um handler de `/units/reachable`, pois o seletor agora carrega nessas telas; (5) o e2e da #31 perdeu o passo do seletor próprio.
Não é do item: nenhum.

## Verificação

### Rodada 1 — OK — 2026-10-18, estado d0705800dd8ae5eb86d39e72c98ecb25ec769f34

| Critério | Status | Evidência |
|---|---|---|
| CA1 | PASSOU | `UnitReachableIT.reachable` (3 ativas, inativa fora, allUnits=true); `verify` 177 testes, 0 falhas |
| CA2 | PASSOU | mesmo teste: Administrador 2 unidades e Professor 1, allUnits=false |
| CA3 | PASSOU | mesmo teste: outra organização só vê a sua; `organizationId` ignorado |
| CA4 | PASSOU | `UnitReachableIT.denied`: 401 anônimo; 403 aluno, responsável e sem vínculo |
| CA5 | PASSOU | `UnitScope.test.tsx` CA5 (npm test: 344 passaram) |
| CA6 | PASSOU | `UnitScope.test.tsx` CA6 |
| CA7 | PASSOU | `UnitSettingsPage.test.tsx` CA7 |
| CA8 | PASSOU | `UnitScope.test.tsx` CA8 |
| CA9 | PASSOU | `UnitScope.test.tsx` CA9 (estados e axe claro/escuro a 360 px) |
| CA10 | PASSOU | `UnitSettingsPage.test.tsx` CA10 |
| CA11 | PASSOU | `ContractRoutesTest`/`OpenApiContractTest` dentro do `verify`; `reachable.msw.test.ts` dentro do `npm test` |

Comandos: `./backend/mvnw -f backend/pom.xml verify` BUILD SUCCESS (177 testes, JaCoCo ok); em `frontend/`: `npm run lint`, `typecheck`, `build` sem erros; `npm test` 344 passaram; `npm run test:cov` linhas 97,18%; `npm run e2e` 13 passaram.
Achados:
- sugestão, código, `UnitReachableIT` linha `all.path("units").get(0).fieldNames().next()` — asserção sem valor (só confirma que há um campo); trocar por conferir id, name e shortName.

## Entrega

- Nova rota `GET /api/v1/units/reachable` (Administrador, Coordenador e Professor) lista as unidades ativas ao alcance e se há "Todas as unidades"; isolada por organização e por perfil, no OpenAPI e no MSW.
- Seletor de unidade no layout da gestão, com o contexto guardado no navegador por vínculo; "Todas as unidades" bloqueia as telas de alteração. Textos em pt-BR.
- Verificação: `verify` do back-end (177 testes, JaCoCo ok); front: lint, typecheck, build, `test:cov` (344 testes, 97% de linhas) e e2e (13) verdes.
- Decisões: rota própria em vez de reutilizar `/units/mine`; padrão do Coordenador é "Todas as unidades"; "Todas" só aparece com 2 ou mais unidades.
- Limitação: Início, financeiro e relatórios somados ainda não existem; "Todas" só muda o contexto.
