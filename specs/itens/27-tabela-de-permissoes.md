# 27 — Tabela de permissões aplicada no servidor

Origem: issue #27 (Sprint 03); `docs/especificacao/02-contas-e-acesso.md` (seção "Permissões"; RN-ACE-05; RF-ACE-06, RF-ACE-08), `00-visao-geral.md` (RNF-05, RNF-31). Branch: `item/27-tabela-de-permissoes`.

## 1. Objetivo

Dar ao servidor uma única fonte da tabela de permissões da gestão (Coordenador, Administrador, Professor), por perfil, por unidade e pelas quatro permissões do Professor por unidade, para toda rota futura perguntar "este vínculo pode isto, nesta unidade?" em vez de decidir por conta própria. Dar ao front o menu da gestão montado pelo perfil, a partir de uma rota que devolve as permissões efetivas do vínculo ativo.

Fora do item: rotas e telas de cada área (cada item futuro chama a tabela); filtro de campos do aluno para o Professor (sem CPF, endereço, valores) e escopo por turma "T" (nascem com alunos e turmas; aqui a tabela só diz que o escopo é "suas turmas"); portal (Aluno e Responsável); tela de edição das quatro permissões do Professor (#31); papéis da plataforma.

## 2. Requisitos

- R1 (tabela, RNF-05): existe no servidor uma tabela de permissões com as 28 linhas "Área / ação" da especificação para Coordenador, Administrador e Professor, com o nível (`FULL`, `READ`, `OWN`, `NOTICE`, `NONE`) e o escopo (todas as unidades, só as suas unidades `U`, só as suas turmas `T`) de cada célula, idêntica à da especificação.
- R2 (unidade): a decisão "pode?" recebe a unidade da operação: Administrador e Professor só podem nas unidades do vínculo ativo; Coordenador em qualquer unidade da própria organização. Unidade fora do alcance é negada (D2).
- R3 (Professor `*`): as células marcadas `*` (ver contatos, editar e cancelar aulas, enviar comunicado, ver aulas experimentais) valem só se a permissão da unidade estiver ligada em `unit_settings.teacher_permissions`; desligada, equivale a `NONE` naquela unidade (D3).
- R4 (servidor): uma operação de verificação (`require`) recusa com 403 `FORBIDDEN` e nenhum efeito, e vale para qualquer rota; a decisão usa só o vínculo ativo da sessão, nunca dado enviado pelo cliente (RNF-05).
- R5 (rota): `GET /api/v1/permissions` devolve as permissões efetivas do vínculo ativo (código e nível de cada linha), para o menu; sem sessão 401; vínculo de portal ou sem vínculo ativo recebe tudo `NONE` (D4).
- R6 (isolamento): a rota e a leitura de `teacher_permissions` respeitam organização e unidade (filtro e RLS); nunca se lê configuração de unidade de outra organização.
- R7 (front, menu): o menu da gestão é montado a partir da resposta de R5: só aparecem as áreas com nível diferente de `NONE`, agrupadas; o front não é a única barreira (D5).
- R8 (front, estados): a montagem do menu tem carregando, erro (com "Tentar de novo") e sem permissão (perfil sem nenhuma área mostra a tela de área indisponível), funciona a partir de 360 px, passa no axe nos dois temas e todo texto está em `pt-BR.json`.
- R9 (contrato): a rota entra em `docs/openapi.yaml` e nos handlers MSW, idênticos à implementação.
- R10 (sem auditoria): a rota só lê; nada é gravado na auditoria, e isso é afirmado em teste (D6).

## 3. Critérios de aceite

- CA1 (R1): dado o teste parametrizado com uma cópia literal da tabela da especificação (`permission-table.csv` de teste), quando cada célula (28 linhas x 3 perfis) é consultada, então nível e escopo batem; mudar uma célula no código reprova o teste.
- CA2 (R2): dado Administrador das unidades U1 e U2, quando a operação é na U3 (mesma organização), então é negada; em U1, permitida (nível `FULL`).
- CA3 (R2): dado Coordenador, quando a operação é em qualquer unidade da própria organização, então é permitida; em unidade de outra organização, negada.
- CA4 (R3): dado Professor da U1 com "ver contatos" ligada, então "Alunos: contatos e emergência" é `READ` na U1; desligada, `NONE`; as outras três permissões são independentes entre si.
- CA5 (R3): dado Professor com U1 ligada e U2 desligada, quando `require` na U2, então 403; na U1, ok.
- CA6 (R4): dado Professor, quando chama uma rota de teste protegida por `require(COLLECTIONS)`, então 403 `FORBIDDEN`, nada gravado; Administrador da unidade, 2xx; cabeçalho `X-Organization-Id` e parâmetro forjados não mudam o resultado.
- CA7 (R5): dado Coordenador, quando chama `GET /api/v1/permissions`, então vem `FULL`/`READ` conforme a tabela (ex.: `ALL_UNITS_CONSOLIDATED` = `READ`, `ORGANIZATION_SETTINGS` = `FULL`); Administrador: `ORGANIZATION_SETTINGS` = `READ`, `UNITS` = `NONE`, `SUBSCRIPTION` = `NOTICE`; Professor: `COLLECTIONS` = `NONE`, `REPORTS` = `OWN`.
- CA8 (R5): dado Professor com uma permissão ligada em ao menos uma das suas unidades, então o código correspondente vem diferente de `NONE`; desligada em todas, `NONE`.
- CA9 (R5): dado sem sessão, então 401; dado Aluno ou vínculo de portal, ou sessão sem vínculo ativo, então 200 com todos `NONE`.
- CA10 (R6): teste-modelo de isolamento (`RouteIsolationModelIT`) ligado à rota: usuário da organização A não obtém nem é afetado por `teacher_permissions` da B, mesmo com a unidade da B forjada; anônimo 401; perfil de portal sem acesso.
- CA11 (R7): dado cada perfil da tabela, quando o menu é montado a partir de uma resposta de permissões, então só há grupos e itens com nível diferente de `NONE`, na ordem do catálogo (ex.: Professor não vê Financeiro nem Configurações; Administrador não vê Unidades).
- CA12 (R7): dado item do catálogo cuja tela ainda não existe, então ele não aparece em nenhum perfil (D7); o Início aparece para os três.
- CA13 (R8): dado a requisição de permissões carregando, então o menu mostra `LoadingState`; em erro, `ErrorState` com "Tentar de novo"; sem nenhuma área, a tela de área indisponível; menu a 360 px (aberto pelo botão) e axe sem violações nos dois temas.
- CA14 (R9): `OpenApiContractTest` e `ContractRoutesTest` passam com a rota; o handler MSW devolve o mesmo formato por perfil.
- CA15 (R10): chamar a rota não cria registro em `audit_log`.
- CA16 (R7, R8): ponta a ponta `@critical`: Coordenador e Professor do mock veem o Início no menu; Professor do mock não vê "Financeiro" nem "Configurações" (D9).

## 4. Plano técnico

- **Dados.** Sem migração: `unit_settings.teacher_permissions boolean[4]` já existe (V3) com RLS. Ordem do vetor (D3): comunicado, editar e cancelar aulas, ver contatos, ver aulas experimentais (ordem da especificação). Seed não muda.
- **Back-end** (pacote novo `br.com.ctcore.permission`, a criar):
  - `Permission` (enum das 28 linhas, ex.: `STUDENT_VIEW`, `STUDENT_CONTACTS`, `COLLECTIONS`, `ORGANIZATION_SETTINGS`, `UNITS`, `SUBSCRIPTION`, `AUDIT`…), `Access` (`FULL, READ, OWN, NOTICE, NONE`), `Scope` (`ALL, UNITS, CLASSES`) e `Grant(Access, Scope)`.
  - `PermissionTable`: mapa estático `Role` x `Permission` -> `Grant`, mais, para as quatro permissões do Professor, o índice em `teacher_permissions`. Fonte única; usa o `Role` de `identity/Role.java` (existente).
  - `Permissions` (`@Service`): `grant(AccountPrincipal, Permission, UUID unitId)` e `require(AccountPrincipal, Permission, UUID unitId)` (lança a exceção que vira 403 `FORBIDDEN`, mecanismo de `api/error` existente); `effective(AccountPrincipal)` para a rota. Lê `teacher_permissions` pela `OrganizationTransactions` (RLS ativa); unidade só entra se estiver em `AccountPrincipal.authorizedUnitIds()` (R2); Coordenador usa escopo `ALL` e a RLS limita à organização.
  - `PermissionController` (`GET /api/v1/permissions`): usa o `AccountPrincipal` da sessão (`SessionAuthenticationFilter`, existente); sem vínculo ativo ou papel de portal: tudo `NONE`.
  - Reuso: `Role`, `AccountPrincipal`, `OrganizationPrincipal`, `OrganizationTransactions`, `ApiExceptionHandler`/`ErrorCode.FORBIDDEN`, molde `RouteIsolationModelIT` (item #15) para o teste de isolamento.
  - Testes: `PermissionTableTest` + `src/test/resources/permission-table.csv` (cópia literal da tabela, CA1), `PermissionsIT` (CA2 a CA5, banco real), `PermissionControllerIT` (CA6 a CA10, CA15; estende `RouteIsolationModelIT`).
- **API / OpenAPI.** `docs/openapi.yaml`: `GET /permissions` (cookie de sessão, 200 com `PermissionsView { permissions: { <código>: Access } }`, 401). `Permission` e `Access` entram como enums no schema. Teste de contrato: `./backend/mvnw -f backend/pom.xml test -Dtest='OpenApiContractTest,ContractRoutesTest'`.
- **Front-end** (`frontend/src/features/permissions/`, a criar):
  - `usePermissions` (consulta `GET /permissions` do vínculo ativo, refeita ao trocar de vínculo), `menuCatalog.ts` (grupos e itens, cada item com a `Permission`, o `href` e `available`; Início `available: true`, demais `false` até a tela existir, D7), `buildMenu(permissions, catalog)` pura.
  - `ManagementLayout.tsx`: envolve o conteúdo com `AppLayout` (`components/layout/AppLayout.tsx`, existente, aceita `NavGroup[]`), com os quatro estados de `QueryPage`/`components/states`. `Home` em `App.tsx` passa a usar o layout quando o papel é de gestão; manter o gatilho `ChooseLinkTrigger` acessível (acrescentar prop opcional no `AppLayout` se preciso).
  - `pt-BR.json`: `menu.*` (nomes de grupo e item). `mocks/handlers.ts`: `GET /permissions` por papel do vínculo ativo, com as contas de mock existentes (conferir qual é Coordenador e qual Professor antes; acrescentar conta só se faltar).
  - Testes: `buildMenu.test.ts` (CA11, CA12), `ManagementLayout.test.tsx` (CA13, axe nos dois temas, 360 px), `e2e/menu-permissoes.spec.ts` `@critical` (CA16). Cobertura do front >= 80%.
- **Segurança.** O servidor decide só pelo vínculo ativo da sessão; unidade e organização vêm do principal, nunca do cliente (RNF-05); resposta idêntica para qualquer negativa, sem nomear recurso. A rota não devolve dado de configuração, só níveis.
- **Riscos.** Tabela duplicada entre servidor e front (o front não a tem; só consome níveis). Coordenador sem unidades listadas no vínculo (escopo `ALL` não depende delas). Permissão de Professor lida a cada pedido (uma consulta curta por `unit_settings`); cache fica para quando medir.

## 5. Tarefas

- [x] T1: `Permission`, `Access`, `Scope`, `Grant` e `PermissionTable` com as 28 linhas x 3 perfis, mais `permission-table.csv` de teste — cobre CA1 — prova: `./backend/mvnw -f backend/pom.xml test -Dtest=PermissionTableTest`.
- [x] T2: `Permissions` (`grant`, `require`, `effective`) com leitura de `teacher_permissions` e checagem de unidade — cobre CA2, CA3, CA4, CA5 — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test=PermissionsIT`.
- [x] T3: `PermissionController` `GET /api/v1/permissions`, rota de teste com `require` e teste de isolamento/perfil pelo molde — cobre CA6, CA7, CA8, CA9, CA10, CA15 — prova: `-Dit.test=PermissionControllerIT`.
- [x] T4: OpenAPI e teste de contrato — cobre CA14 — prova: `-Dtest='OpenApiContractTest,ContractRoutesTest'`.
- [x] T5: Handler MSW, `usePermissions`, `menuCatalog`, `buildMenu`, textos `menu.*` — cobre CA11, CA12, CA14 — prova: `npx vitest run src/features/permissions`.
- [x] T6: `ManagementLayout` com os quatro estados, uso em `Home`, axe e 360 px — cobre CA13 — prova: `npx vitest run src/features/permissions/ManagementLayout`.
- [x] T7: e2e `@critical` do menu por perfil, conforme o CA16 ajustado (Início para Coordenador e Professor; Professor sem "Financeiro" e "Configurações") — cobre CA16 — prova: `npm run e2e`.
- [x] T8: Regressão do item (existe para fechar a definição de pronto) — prova: `./backend/mvnw -f backend/pom.xml verify` e, em `frontend/`, `npm run lint && npm run typecheck && npm run build && npm run test:cov && npm run e2e`.

## 6. Decisões

- D1 (muda o produto): a tabela da especificação é implementada como está, célula a célula, em código (não em banco): ela é regra de produto fixa por perfil, não configuração da organização. A única parte configurável é a das quatro permissões do Professor, que já vive em `unit_settings`. Alternativa descartada: tabela em banco editável, sem requisito que a peça.
- D2 (muda o produto): para Administrador e Professor, "U" e "T" valem nas unidades do vínculo ativo (`account_link_unit`); Coordenador vale em todas as unidades da organização. O recorte "T" (turmas do Professor) só é declarado pela tabela aqui; quem filtra por turma é cada item de turma/agenda/aluno.
- D3 (muda o produto): ordem do vetor `teacher_permissions`: comunicado, editar e cancelar aulas, ver contatos, ver aulas experimentais (a ordem do texto da especificação; o vetor nasceu na #13 sem ordem documentada). Suposição: se a #31 usar outra, ela e a tabela mudam juntas.
- D4: rota única `GET /api/v1/permissions` (não ampliar `/auth/session`): evita mexer no contrato de login e permite ao menu recarregar ao trocar de vínculo. Para o Professor, cada permissão `*` aparece como ligada se estiver ligada em ao menos uma unidade do vínculo (só mostra o item; o `require` por unidade segue valendo). Portal e sem vínculo ativo: tudo `NONE`.
- D5: o menu apenas esconde; a barreira é o `require` no servidor (RNF-05), e endereço digitado fora do perfil cai em `RequireArea` (#25) e no 403.
- D6: a rota não grava auditoria porque só lê (regra do AGENTS vale para ação que altera dado).
- D7: o catálogo do menu traz todas as áreas da tabela, mas cada item nasce `available: false` e só vira `true` no item que entrega a tela, para o menu nunca apontar para endereço inexistente. Hoje só o Início aparece. Alternativa descartada: mostrar tudo e deixar cair em 404.
- D8: sem recurso liberado por organização e sem notas de versão: nada visível às academias além do menu do Início, que não muda de conteúdo.
- D9 (ajuste, pedido da construção): o CA16 exigia o Coordenador ver "Configurações", o que contrariava a D7 e o CA12 (item sem tela não aparece em nenhum perfil). Vale a D7: o CA16 passa a exigir só o Início para Coordenador e Professor e a ausência de "Financeiro" e "Configurações" para o Professor. A checagem de que o Coordenador vê "Configurações" muda para o primeiro item que entregar uma tela de Configurações, que passa a `available: true` e acrescenta esse caso ao e2e. A cobertura do Coordenador por `GET /permissions` segue no CA7. O item não muda o que o produto faz.
- Suposição: o canvas do menu não está acessível ao agente; nomes e agrupamento seguem a coluna "Área / ação" (Operação, Financeiro, Pessoas, Configurações…). Se o canvas divergir, só `menuCatalog.ts` e `pt-BR.json` mudam.

## Construção

- T1 — arquivos: backend/src/main/java/br/com/ctcore/permission/{Access,Scope,Grant,Permission,PermissionTable}.java, backend/src/test/resources/permission-table.csv, PermissionTableTest — prova: `./backend/mvnw -f backend/pom.xml test -Dtest=PermissionTableTest` → 2 testes ok
- T2 — arquivos: permission/Permissions.java, PermissionsIT (7 testes) — prova: `verify -Dit.test=PermissionsIT` → 7 ok
- T3 — arquivos: permission/PermissionController.java, isolation/OrganizationScopeFilter.java (a rota /permissions não abre contexto de organização, como /auth, para sessão sem vínculo e portal receberem 200 e não 403), PermissionRouteIT (molde #15 sobre rota com require(COLLECTIONS), 7 testes), PermissionControllerIT (2 testes) — prova: `verify -Dit.test=Permission*IT` → 16 ok
- T4 — arquivos: docs/openapi.yaml (GET /permissions; schemas Access, Permission, PermissionsView) — prova: `test -Dtest=OpenApiContractTest,ContractRoutesTest` → 7 ok
- T5 — arquivos: frontend/src/features/permissions/{types,menuCatalog,buildMenu,usePermissions}.ts, buildMenu.test.ts, src/mocks/{permissions.ts,handlers.ts}, pt-BR.json (menu.*) — prova: `npx vitest run src/features/permissions` → ok
- T6 — arquivos: features/permissions/ManagementLayout.tsx e teste, components/layout/AppLayout.tsx (prop headerExtra), App.tsx (Home da gestão usa o layout), ajuste de AreaUnavailablePage.test.tsx e ChooseLinkModal.test.tsx (handler de /permissions) — prova: `npx vitest run src/features/permissions/ManagementLayout` → 6 ok
- T7 — arquivos: frontend/e2e/menu-permissoes.spec.ts — prova: `npm run e2e` → 9 passed (inclui o do menu). Coordenador e Professor veem o Início; Professor não vê Financeiro nem Configurações. Pedido de ajuste anterior (CA16) atendido pela D9.

Regressão: `./backend/mvnw -f backend/pom.xml verify` → BUILD SUCCESS, 133 testes de integração ok, cobertura JaCoCo ok; em frontend/: `npm run lint`, `typecheck`, `build`, `test:cov` (38 arquivos, 269 testes, limites de cobertura ok) e `npm run e2e` (9 @critical) → tudo ok. Sem falha anterior.
Desvios: (1) a rota /permissions foi excluída do OrganizationScopeFilter (plano não previa); (2) dependências de Permissions com @Lazy, como o resto do projeto, para o ContractRoutesTest subir sem banco; (3) leitura das células da tabela: "●" = FULL, "○" = READ, e "ver" do Professor (contatos, aulas experimentais) e "consulta T" (graduação) = READ; "Alunos: ver lista e ficha" do Professor = FULL/T; células de Administrador sem "U" (Configurações da organização, Assinatura) têm escopo ALL.
Não é do item: nada.
Pedido de ajuste: CA16 (e R7/D7) — CA16 exige que o Coordenador veja "Configurações" no menu, mas a D7 e o CA12 mandam que item sem tela não apareça em nenhum perfil, e hoje nenhum item de Configurações tem tela. Sugestão: no CA16, trocar por "Coordenador vê o Início e Professor não vê Financeiro nem Configurações", ou mover o e2e do Coordenador para o primeiro item que entregar uma tela de Configurações.

## Verificação

### Rodada 1 — OK — 2026-10-17, estado 015e8988f2f02192e02aa3b4e91e4fe8740c1a1a

| Critério | Status | Evidência |
|---|---|---|
| CA1 | PASSOU | PermissionTableTest (2 testes) no `verify`; CSV conferido célula a célula com a tabela da especificação (28 linhas) |
| CA2, CA3 | PASSOU | PermissionsIT (7 testes ok) |
| CA4, CA5 | PASSOU | PermissionsIT |
| CA6 | PASSOU | PermissionRouteIT (7 ok): Professor 403 FORBIDDEN sem gravação, Administrador 2xx; molde #15 cobre cabeçalho/parâmetro forjados |
| CA7, CA9, CA15 | PASSOU | PermissionControllerIT (2 ok): níveis por perfil, 401 anônimo, portal/sem vínculo tudo NONE, `audit_log` inalterado |
| CA8 | PASSOU | PermissionsIT (efetivas do Professor) e PermissionControllerIT |
| CA10 | PASSOU | PermissionRouteIT estende RouteIsolationModelIT |
| CA11, CA12 | PASSOU | buildMenu.test.ts no `test:cov` |
| CA13 | PASSOU | ManagementLayout.test.tsx (6 ok): carregando, erro com "Tentar de novo", sem área, 360 px, axe claro e escuro |
| CA14 | PASSOU | OpenApiContractTest e ContractRoutesTest dentro do `verify`; rota, enums Access e Permission no openapi.yaml; handler MSW por papel |
| CA16 | PASSOU | `npm run e2e`: 9 passed, inclui menu-permissoes (ajustado pela D9) |

Comandos: `./backend/mvnw -f backend/pom.xml verify` → BUILD SUCCESS, 133 testes, cobertura ok; em frontend/: `npm run lint`, `typecheck`, `build`, `test:cov` (95,6% instruções; limites ok), `npm run e2e` (9 passed) → todos ok. Sem regressão.
Achados:
- sugestão, código, OrganizationScopeFilter — a exclusão de `/permissions` é exata (`equals`); está documentada e coerente com o desvio (1) da Construção.
- sugestão, código, Permissions.flagsOf — monta o SQL por concatenação de `where` (valores constantes internos, sem entrada do cliente); parâmetros já são ligados. Sem risco hoje.

## Entrega

- Tabela de permissões por perfil, módulo e escopo aplicada no servidor (pacote `permission`), com rota `GET /api/v1/permissions` (níveis por perfil; portal e sem vínculo recebem tudo `NONE`), no `docs/openapi.yaml` e no handler MSW.
- Front: menu montado a partir das permissões (`buildMenu`, `ManagementLayout`) com os quatro estados, 360 px e axe nos dois temas; item sem tela não aparece em nenhum perfil.
- Verificação: `verify` do back-end (133 testes de integração, JaCoCo ok) e `lint`, `typecheck`, `build`, `test:cov`, `e2e` do front, todos verdes (rodada 1, OK).
- Decisões: `/permissions` fica fora do `OrganizationScopeFilter` (comparação exata); leitura das células da tabela conforme desvio (3) da Construção; o e2e do Coordenador segue a D9.
- Limitações: nenhum item de Configurações tem tela ainda, então ele não aparece no menu de nenhum perfil.
