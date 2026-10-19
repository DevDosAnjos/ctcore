# 38 — RF-MOD-01 · Configurações › Modalidades

Origem: issue #38 (Sprint 04); `docs/especificacao/04-modalidades-e-graduacao.md` (RF-MOD-01, RN-MOD-01 a RN-MOD-04, RN-MOD-06) e tabela de permissões em `02-contas-e-acesso.md`. Branch: `item/38-configuracoes-modalidades`.

## 1. Objetivo

A tela Configurações › Modalidades e a API por trás dela. O catálogo da plataforma (14 modalidades iniciais) vira dado no banco; a organização ativa e desativa as que oferece, em dois blocos ("Suas modalidades" e "Disponíveis para ativar"); e, se a sua modalidade não está no catálogo, envia um pedido à equipe da plataforma pelo botão "Não achei minha modalidade". Quem usa: o Coordenador (altera) e o Administrador (só consulta). As demais sprints (#39 graduação, #40 unidade, turmas, planos) partem da modalidade ativa criada aqui.

Fora do escopo: graduação por modalidade e seu editor (#39); modalidades oferecidas pela unidade (#40); o painel da plataforma que lê e atende os pedidos e edita o catálogo (RF-PNL-05, Sprint 15); uso da modalidade em turmas, planos e alunos (sprints seguintes); notas de versão (só existem na Sprint 15, #142).

## 2. Requisitos

- R1 (RN-MOD-01): o catálogo da plataforma traz as 14 modalidades iniciais (Jiu-Jitsu, Judô, Muay Thai, Boxe, Karatê, Wrestling, MMA, Taekwondo, Capoeira, Kickboxing, Luta Livre, Kung Fu, Krav Magá, Defesa pessoal), iguais para todas as organizações.
- R2 (RF-MOD-01, D1): `GET /api/v1/modalities` devolve, para a organização da sessão, todas as modalidades do catálogo com `id`, `name`, `active` e `canManage`; a tela as separa em "Suas modalidades" (ativas) e "Disponíveis para ativar" (as demais), ambas em ordem alfabética.
- R3 (RN-MOD-02, D2): `PUT /api/v1/modalities/{id}/activation` (corpo `{"active": true|false}`) ativa ou desativa a modalidade para a organização; só o Coordenador. Repetir o mesmo valor é idempotente (200, sem nova auditoria).
- R4 (RN-MOD-06, D3): desativar recusa com 409 `MODALITY_IN_USE` quando houver turma ativa ou aluno com a modalidade ativa na organização; a tela diz quantos. O Administrador e as demais áreas veem a recusa pela mesma regra; o histórico não se perde (a modalidade só deixa de estar ativa).
- R5 (RN-MOD-04, D4): `POST /api/v1/modalities/requests` (corpo `name` de 2 a 60 caracteres, `note` opcional até 300) grava um pedido para a plataforma com organização, autor, data e situação `OPEN`, e responde 201; só o Coordenador. O mesmo nome (sem diferenciar maiúsculas) já pedido pela organização e ainda `OPEN` responde 409 `REQUEST_ALREADY_OPEN`; nome que já existe no catálogo responde 409 `ALREADY_IN_CATALOG`.
- R6 (RN-MOD-04): `GET /api/v1/modalities/requests` lista os pedidos da organização (nome, data, situação) para a tela mostrar "Pedidos enviados".
- R7 (permissão, D5): toda rota confere no servidor `ORGANIZATION_SETTINGS`: Coordenador altera (`FULL`), Administrador só lê (`READ`, `canManage = false`), Professor, Aluno e Responsável recebem 403, sem sessão 401. A organização vem sempre da sessão.
- R8 (RNF auditoria): ativar, desativar e pedir modalidade gravam auditoria (`modality.activated`, `modality.deactivated`, `modality.requested`) com ator, organização e modalidade ou nome pedido, sem dados pessoais.
- R9 (isolamento): a ativação e os pedidos são por organização, no código e com row-level security; o catálogo é global e somente leitura para a aplicação.
- R10: a tela `/configuracoes/modalidades` (novo item "Modalidades" no grupo Configurações do menu) mostra os dois blocos, o botão "Não achei minha modalidade" (janela com nome e observação) e, por linha, "Ativar" ou "Desativar" só com `canManage`. Tem os quatro estados (carregando, vazio, erro com "Tentar de novo", sem permissão), funciona a partir de 360 px, passa no axe nos dois temas e tem todos os textos em `pt-BR.json`.
- R11: `docs/openapi.yaml` e os handlers MSW refletem as rotas novas e batem com a implementação; o seed de dados fictícios ativa algumas modalidades nas organizações de teste.

## 3. Critérios de aceite

- CA1 (R1, R9): dado o banco migrado, então o catálogo tem exatamente as 14 modalidades com nome único, e o papel operacional da aplicação não consegue alterá-lo (insert, update e delete negados).
- CA2 (R2): dado Coordenador de organização sem modalidades ativas, quando `GET /modalities`, então 14 itens `active = false` e `canManage = true`; após ativar duas, essas aparecem `active = true`, em ordem alfabética.
- CA3 (R3): dado Coordenador, quando ativa Judô, então 200, `active = true`, auditoria `modality.activated`; repetir devolve 200 sem nova auditoria; desativar devolve `active = false` com `modality.deactivated`.
- CA4 (R3, R7): dado Administrador, Professor, Aluno ou Responsável, quando chama `PUT activation` ou `POST requests`, então 403 e nada muda; Administrador no `GET` recebe 200 com `canManage = false`; Professor, Aluno e Responsável no `GET` recebem 403; sem sessão, 401.
- CA5 (R3): dado `id` inexistente no catálogo, quando ativa, então 404; corpo sem `active`, 400.
- CA6 (R4): dado uma modalidade com turma ativa ou aluno ativo (simulado pela porta `ModalityUsage` no teste), quando o Coordenador desativa, então 409 `MODALITY_IN_USE` com as contagens e a modalidade segue ativa; sem uso, desativa.
- CA7 (R5): dado Coordenador, quando pede "Sambo" com observação, então 201, pedido `OPEN` da organização e auditoria `modality.requested`; nome com menos de 2 ou mais de 60 caracteres, ou observação com mais de 300, 400; pedir "sambo" de novo com o primeiro aberto, 409 `REQUEST_ALREADY_OPEN`; pedir "judô" (ou "Judo"), 409 `ALREADY_IN_CATALOG`.
- CA8 (R6): dado dois pedidos da organização A e um da B, quando A faz `GET requests`, então vê só os dois dela, do mais novo para o mais antigo; Administrador também lista (leitura).
- CA9 (R9): dado Coordenadores de duas organizações, quando cada um ativa a mesma modalidade, então as ativações e os pedidos de uma não aparecem na outra (API e consulta direta com a outra organização no contexto de RLS).
- CA10 (R8): dado cada escrita, então existe auditoria com ator, ação e alvo, sem e-mail, senha ou token.
- CA11 (R10): dado a tela em 360 px, então sem rolagem horizontal, axe sem violações nos dois temas, estados de carregando, vazio (catálogo sem itens), erro com "Tentar de novo" e sem permissão exibidos; Coordenador vê "Ativar"/"Desativar" e o botão "Não achei minha modalidade"; Administrador vê as listas sem esses controles; "Suas modalidades" vazio mostra texto orientando a ativar uma.
- CA12 (R10): dado a janela "Não achei minha modalidade", quando o nome está vazio ou curto, então o erro aparece no campo e nada é enviado; com dados válidos fecha, avisa "Pedido enviado" e o pedido aparece em "Pedidos enviados"; 409 mostra a mensagem do servidor no campo.
- CA13 (R4, R10): dado `MODALITY_IN_USE` ao desativar, então a tela explica quantas turmas e alunos impedem e a modalidade permanece em "Suas modalidades".
- CA14 (R11): `OpenApiContractTest` e `ContractRoutesTest` passam; testes MSW cobrem as rotas novas no formato do servidor; `SeedDataIT` aplica o novo arquivo de seed.
- CA15 (todos): ponta a ponta Playwright `@critical` "Coordenador ativa uma modalidade e pede uma nova": entra, abre Modalidades, ativa Jiu-Jitsu (vai para "Suas modalidades"), envia pedido e o vê em "Pedidos enviados".

## 4. Plano técnico

- **Dados.** Migração `V19__modality.sql` (a próxima depois de `V18__account_email_change.sql`): `modality` (global, sem `organization_id`, sem RLS; `id uuid` chave primária e `name text` único com `check (btrim(name) <> '')`) com as 14 linhas do R1 inseridas na própria migração; `organization_modality` (`organization_id`, `modality_id` referência a `modality`, `active boolean`, `updated_at`; chave primária `(organization_id, modality_id)`; RLS `organization_scope` como em `V13`); `modality_request` (`id`, `organization_id`, `requested_by` conta, `name`, `note`, `status` `OPEN`/`ADDED`/`DISMISSED` com check, `created_at`; índice único parcial `(organization_id, lower(name)) where status = 'OPEN'`; RLS igual). O papel operacional só recebe `select` em `modality`: a migração faz `revoke insert, update, delete on modality from` o papel de execução, se ele existir (conferir como `V1` e `prepare-runtime-db.sh` concedem; a concessão padrão do script cobre tabelas novas, então o `revoke` precisa ficar num passo que rode depois, ou o teste CA1 vira a prova de que a regra vale; ver Riscos). Revert comentado no topo, como `V13`. Seed: `infra/seed/05-modalidades.sql` ativa Jiu-Jitsu, Judô e Muay Thai nas organizações fictícias (`on conflict do nothing`).
- **API** (`/api/v1/modalities`, cookie de sessão e CSRF como nas demais): `GET /modalities` (200, lista `{id, name, active, canManage}`), `PUT /modalities/{id}/activation` (200, a linha atualizada; 404, 409 `MODALITY_IN_USE`), `GET /modalities/requests` (200), `POST /modalities/requests` (201; 400, 409 `REQUEST_ALREADY_OPEN`, 409 `ALREADY_IN_CATALOG`). Erros novos em `api/error/ErrorCode.java` com `messages.properties`. Rotas declaradas em `docs/openapi.yaml` no mesmo PR.
- **Back-end:** pacote a criar `br.com.ctcore.modality`: `Modalities.java` (lista, ativação, pedidos; confere `Permissions.grant(who, Permission.ORGANIZATION_SETTINGS, null)`; escreve dentro da transação da organização como `OrganizationProfile`), `ModalityController.java`, `ModalityUsage.java` (interface com `activeClasses(modalityId)` e `activeStudents(modalityId)` e classe `None` que devolve 0, no padrão de `organization/UnitDeactivationBlockers.java`; a versão real entra com turmas e matrícula nas Sprints 05 e 07) e auditoria por `audit/AuditService` com `AuditEntry` como em `UnitDeactivation`. Normalização do nome do pedido: sem acento e sem diferença de maiúsculas na comparação com o catálogo (`java.text.Normalizer`).
- **Interface:** a criar `frontend/src/features/modalities/`: `ModalitiesPage.tsx` (rota `/configuracoes/modalidades` em `ManagementLayout`, mesmo padrão de `UnitsPage.tsx` e `OrganizationSettingsPage.tsx`), `RequestModalityDialog.tsx`, `useModalities.ts` (TanStack Query, como `useUnits.ts`) e testes. A alterar: `App.tsx` (rota), `features/permissions/menuCatalog.ts` (item `modalities` com `ORGANIZATION_SETTINGS` no grupo `settings`, após `organizationSettings`) e `buildMenu.test.ts`, `mocks/handlers.ts` e teste MSW, `i18n/pt-BR.json` (bloco `modalities` e rótulo do menu). Reuso: `Card`, `states/*`, `ConfirmDialog`, formulários e validação de campo já usados em `InviteUserDialog.tsx`. Canvas `ConfigModalidades.dc.html` não acessível ao agente (D6).
- **Segurança.** Organização e perfil vêm do principal da sessão, nunca do corpo (RNF-05); filtro por organização no código mais RLS nas duas tabelas por organização; o catálogo é global, mas só a plataforma o altera; `name` e `note` do pedido são texto livre exibido depois no painel: guardar como texto puro, validar tamanho no servidor e escapar no front (React já escapa); auditoria sem dado pessoal; `modality_request.requested_by` guarda só o id da conta.
- **Riscos.** (1) Concessão do papel operacional: `prepare-runtime-db.sh` dá `select, insert, update, delete` em todas as tabelas, inclusive `modality`; CA1 prova se a proteção do catálogo vale, e se não valer o `revoke` precisa entrar no script ou na migração (o script é de infra: alterar só o necessário e registrar em "Construção"). (2) Sem turmas e alunos hoje, o R4 só é provado com a porta `ModalityUsage`; quando as sprints seguintes criarem as tabelas, devem trocar `None` pela contagem real (nota para #46 e #7x). (3) A decisão de o Administrador consultar sem alterar segue a tabela de permissões (`○`) e a regra RN-MOD-02; se o dono quiser o contrário, muda só o `Access` exigido.

## 5. Tarefas

Uma parte só, em duas etapas: servidor (T1 a T4) e tela (T5 a T7).

- [x] T1: migração `V19__modality.sql` (catálogo global com as 14 modalidades, `organization_modality`, `modality_request`, RLS, revert comentado) e seed `infra/seed/05-modalidades.sql` — cobre CA1, CA14 (seed) — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test='RowLevelSecurityIT,SeedDataIT,ModalityCatalogIT'` (o último a criar).
- [x] T2: `Modalities`, `ModalityUsage` (+ `None`), `ModalityController`, `ErrorCode` e mensagens — cobre CA2, CA3, CA4, CA5, CA6, CA7, CA8 — prova: `verify -Dit.test='ModalityListIT,ModalityActivationIT,ModalityRequestIT'` (a criar).
- [x] T3: testes de isolamento entre organizações e entre perfis, sem sessão, e auditoria sem dados proibidos — cobre CA4, CA9, CA10 — prova: `verify -Dit.test=ModalityIsolationIT` (a criar).
- [x] T4: OpenAPI (quatro operações, esquemas, erros) e handlers MSW — cobre CA14 — prova: `./backend/mvnw -f backend/pom.xml test -Dtest='OpenApiContractTest,ContractRoutesTest'` e `npx vitest run src/mocks` em `frontend/`.
- [x] T5: `ModalitiesPage` com os dois blocos, quatro estados, ações conforme `canManage`, "Pedidos enviados", rota, item de menu e textos pt-BR — cobre CA11, CA13 — prova: `npx vitest run src/features/modalities src/features/permissions`.
- [x] T6: janela "Não achei minha modalidade" com validação de campo; 360 px e axe nos dois temas — cobre CA11, CA12 — prova: `npx vitest run src/features/modalities`.
- [x] T7: e2e `@critical` "Coordenador ativa uma modalidade e pede uma nova" e regressão completa — cobre CA15; a regressão não cobre critério: confere o "Pronto quando" (cobertura e 100% nos pacotes exigidos) — prova: `./backend/mvnw -f backend/pom.xml verify` e, em `frontend/`, `npm run lint && npm run typecheck && npm run build && npm run test:cov && npm run e2e`.

## 6. Decisões

- D1: a API lista o catálogo inteiro com `active` por organização, em vez de duas listas, porque a tela só precisa separar por `active` e fica uma consulta. Alternativa: uma rota por bloco.
- D2 (muda o comportamento): só o Coordenador ativa e desativa modalidades (RN-MOD-02: "O Coordenador ativa modalidades para a organização"). Alternativa: Administrador também ativar para as suas unidades; descartada porque a escolha por unidade é a #40.
- D3 (muda o comportamento): desativar a modalidade é recusado se houver turma ativa ou aluno com ela ativa (RN-MOD-06), sem ofertar "desativar assim mesmo". Enquanto turmas e matrículas não existem, a regra roda sobre a porta `ModalityUsage` que devolve 0 (mesmo padrão de `UnitDeactivationBlockers`). Suposição: as sprints 05 e 07 trocam a implementação; se esquecerem, a regra nunca recusa.
- D4: o pedido "Não achei minha modalidade" fica gravado em `modality_request` com situação `OPEN` e é a fonte que o painel da plataforma (RF-PNL-05, Sprint 15) lerá; nenhum e-mail nem notificação é enviado agora, porque o painel ainda não existe. "A equipe inclui no catálogo para todos" (RN-MOD-04) fica fora: é do painel. Alternativa: enviar e-mail à equipe; descartada por não haver destinatário definido.
- D5 (muda o comportamento): a tela usa `ORGANIZATION_SETTINGS` (Coordenador altera; Administrador só consulta; Professor sem acesso) em vez de uma permissão nova, porque a tabela de permissões não tem linha "Modalidades" e as modalidades são da organização, como as configurações dela. Alternativa: nova linha na tabela (mudaria a especificação e as 28 linhas). Se o dono quiser o Administrador alterando, muda só o `Access` exigido.
- D6: o canvas `ConfigModalidades.dc.html` não está acessível ao agente; o layout e os textos seguem os padrões do projeto e o texto do card, e o dono confere no PR.
- D7: o catálogo vive em tabela global sem RLS, populada pela migração e só alterável pela plataforma (painel, Sprint 15); nenhuma rota de escrita do catálogo existe nesta sprint.
- Suposição: o limite de 2 a 60 caracteres para o nome e 300 para a observação do pedido não vem da especificação; se estiver errado, só muda a validação.


## Construção

- T1 — arquivos: `backend/src/main/resources/db/migration/V19__modality.sql`, `infra/seed/05-modalidades.sql`, `infra/scripts/prepare-runtime-db.sh` (revoke de escrita em `modality` após o grant em massa), `SeedDataIT` (limpeza das tabelas novas e teste do seed), `ModalityCatalogIT` — prova: `verify -Dit.test='ModalityCatalogIT,ModalityIT,SeedDataIT,RowLevelSecurityIT'` → 16 testes, 0 falhas
- T2 — arquivos: `backend/.../modality/{Modalities,ModalityUsage,ModalityController}.java`, `ErrorCode`, `messages.properties`, `ModalityIT` — prova: mesmo comando → passou
- T3 — arquivos: `ModalityIT` (isolamento entre organizações e perfis, 401, auditoria sem dados pessoais); `OrganizationBase` e `Tenant` ficaram `public` para o teste do outro pacote reutilizar — prova: mesmo comando → passou
- T4 — arquivos: `docs/openapi.yaml` (quatro operações, esquemas `Modality` e `ModalityRequest`, três códigos no enum de erro), `frontend/src/mocks/handlers.ts`, `mocks/modalities.msw.test.ts` — prova: `test -Dtest='OpenApiContractTest,ContractRoutesTest'` → 7 testes ok; `npx vitest run src/mocks` → ok
- T5 — arquivos: `frontend/src/features/modalities/{ModalitiesPage,useModalities}.tsx|ts`, `App.tsx`, `menuCatalog.ts`, `buildMenu.test.ts`, `pt-BR.json` — prova: `npx vitest run src/features/modalities src/features/permissions` → ok
- T6 — arquivos: `RequestModalityDialog.tsx`, `ModalitiesPage.test.tsx` (360 px e axe nos dois temas) — prova: `npx vitest run src/features/modalities` → ok
- T7 — arquivos: `frontend/e2e/modalidades.spec.ts` — prova: `npm run e2e` → 16 passaram

Regressão: `./backend/mvnw -f backend/pom.xml verify` → 242 testes, 0 falhas, JaCoCo ok; em `frontend/`, `npm run lint && npm run typecheck && npm run build && npm run test:cov && npm run e2e` → tudo ok (linhas 96,65%).
Desvios: (1) os testes do servidor ficaram em `ModalityCatalogIT` e `ModalityIT` (cinco casos), em vez de quatro arquivos, com a mesma cobertura dos CA; (2) `prepare-runtime-db.sh` ganhou uma linha, e a migração faz o `revoke` em loop sobre os papéis que receberam grant (o nome do papel operacional é configurável); (3) o Docker Desktop estava parado e foi iniciado para os `*IT`; (4) o nome acessível do botão "Ativar/Desativar" vem de texto visível + `sr-only` (mesmo padrão de Unidades).
Não é do item: nenhum.


## Verificação

### Rodada 1 — CORRIGIR_CODIGO — 2026-10-19, estado d178e56997feba314de6101f95c00b18d255fe91

| Critério | Status | Evidência |
|---|---|---|
| CA1 | PASSOU | `ModalityCatalogIT` (14 nomes únicos; insert/update/delete negados ao `ctcore_app`) dentro do `verify` |
| CA2 | PASSOU | `ModalityIT.listAndActivate` |
| CA3 | PASSOU | `ModalityIT.listAndActivate` (idempotência e auditorias contadas) |
| CA4 | PASSOU | `ModalityIT.permissions` (401, 403 por perfil, Administrador lê com `canManage = false`) |
| CA5 | PASSOU | `ModalityIT.listAndActivate` (404 e 400) |
| CA6 | PASSOU | `ModalityIT.inUse` (409 com contagens, segue ativa; sem uso desativa) |
| CA7 | PASSOU | `ModalityIT.requestRules` |
| CA8 | PASSOU | `ModalityIT.isolationAndAudit` (ordem e Administrador) |
| CA9 | NÃO PASSOU | API isolada comprovada; a "consulta direta com a outra organização no contexto de RLS" não existe (ver achado) |
| CA10 | PASSOU | `ModalityIT.isolationAndAudit` (sem `@`, `password`, `token`) |
| CA11 | PASSOU | `ModalitiesPage.test.tsx` (blocos, quatro estados, Administrador, axe a 360 px nos dois temas); `npm run test:cov` ok |
| CA12 | PASSOU | `ModalitiesPage.test.tsx` (CA12) |
| CA13 | PASSOU | `ModalitiesPage.test.tsx` (CA13) |
| CA14 | PASSOU | `OpenApiContractTest`/`ContractRoutesTest` dentro do `verify`; `modalities.msw.test.ts`; `SeedDataIT` (9 ativações) |
| CA15 | PASSOU | `npm run e2e`: `modalidades.spec.ts` passou (16 de 16) |

Comandos: `./backend/mvnw -f backend/pom.xml verify` → 242 testes, 0 falhas, BUILD SUCCESS (JaCoCo ok); em `frontend/`: `npm run lint && npm run typecheck && npm run build && npm run test:cov` → ok (linhas 96,65%); `npm run e2e` → 16 passaram.
Achados:
- obrigatório, código, `backend/src/test/java/br/com/ctcore/modality/ModalityIT.java` (`isolationAndAudit`) e `RowLevelSecurityIT` — esperado: CA9 pede prova de RLS por consulta direta com o papel operacional e a outra organização no contexto; observado: o teste conta linhas só com `adminJdbc()` (superusuário, ignora RLS) e `RowLevelSecurityIT` cobre apenas `organization_feature`; as políticas de `organization_modality` e `modality_request` não são exercitadas, e `Modalities.list` filtra também no código, então o RLS poderia estar errado sem teste falhar; evidência: leitura dos dois arquivos; corrigir: acrescentar teste com conexão `ctcore_app`, `set_config('app.organization_id', <org A>)` e `select`/`insert`/`update` nas duas tabelas vendo só linhas de A e negando escrita com `organization_id` de B (e sem contexto nenhuma linha).
- sugestão, código, `ModalityIT.inUse` — as contagens são conferidas por `contains("2","5")` na resposta inteira; assertar `fields` `activeClasses`/`activeStudents` fica mais preciso.

### Correção da rodada 1
- CA9 (obrigatório): prova de RLS com o papel `ctcore_app` nas duas tabelas por organização (sem contexto nenhuma linha; com contexto de A só linhas de A; update/delete de B afetam 0; insert com organização de B negado) — arquivos: `backend/src/test/java/br/com/ctcore/modality/ModalityCatalogIT.java` (`rlsIsolatesOrganizationTables`) — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test='ModalityCatalogIT,ModalityIT'` → 7 testes, 0 falhas, JaCoCo ok
- Sugestão (feita): `ModalityIT.inUse` agora confere os campos `activeClasses` = 2 e `activeStudents` = 5 em `fields` — mesma prova → passou
Regressão: só testes mudaram, nenhum código de produção; a regressão completa da construção (242 testes, front-end) segue válida e não foi repetida.

### Rodada 2 — OK — 2026-10-19, estado 798f76babbd5c26fda689cc3be9f178d812937d6

| Critério | Status | Evidência |
|---|---|---|
| CA1 | PASSOU (mantido da rodada 1) | `ModalityCatalogIT` volta a passar nesta rodada |
| CA2 a CA8, CA10 | PASSOU (mantido da rodada 1) | `ModalityIT` 5 testes, 0 falhas; só mudou a asserção de `inUse` |
| CA9 | PASSOU | `ModalityCatalogIT.rlsIsolatesOrganizationTables` usa `ctcore_app`: sem contexto 0 linhas, com contexto de A só A, update/delete de B afetam 0, insert com B negado ("row-level security"), nas duas tabelas |
| CA11 a CA13, CA15 | PASSOU (mantido da rodada 1) | nenhum arquivo do front-end mudou desde a rodada 1 |
| CA14 | PASSOU (mantido da rodada 1) | `SeedDataIT` 9 testes ok; contrato ok na fase de testes rápidos (126 testes) |

Comandos: `./backend/mvnw -f backend/pom.xml verify -Dit.test='ModalityCatalogIT,ModalityIT,SeedDataIT,RowLevelSecurityIT'` → 126 testes rápidos e 17 de integração, 0 falhas, BUILD SUCCESS. Diff desde a rodada 1: só `ModalityCatalogIT`, `ModalityIT` e o arquivo do item (nenhum código de produção), então a regressão completa e o front-end seguem como na rodada 1.
Achados: nenhum obrigatório; a sugestão da rodada 1 foi atendida.


## Entrega

- Catálogo global de 14 modalidades (migração V19, somente leitura para o papel operacional), ativação por organização, lista com ativar/desativar e pedido de nova modalidade em Configurações › Modalidades.
- Desativar modalidade com turmas ou alunos ativos devolve 409 com as contagens; ações gravam auditoria sem dados pessoais; RLS provada com o papel `ctcore_app` nas duas tabelas por organização.
- Verificação: rodada 2 OK. `verify` do back-end (242 testes) e, no front-end, lint, typecheck, build, `test:cov` (96,65% de linhas) e `e2e` (16 passaram).
- Decisão D5: a tela usa `ORGANIZATION_SETTINGS` (Coordenador altera, Administrador só consulta). D6: o canvas não estava acessível; o dono confere o layout no PR.
- Limitação: limites de 2 a 60 caracteres (nome) e 300 (observação) são suposição, ajustáveis só na validação.
- Falha das verificações do PR #263 (`modality_request_requested_by_fkey` em `ReauthIT`/`AccountIT`) corrigida: `ModalityIT` e `ModalityCatalogIT` agora limpam `modality_request` e `organization_modality`; `verify` completo com 243 testes, 0 falhas (rodada 3 OK).

### Correção da entrega
- `ReauthIT`, `AccountIT` e outros quebravam com `modality_request_requested_by_fkey`: `ModalityIT` e `ModalityCatalogIT` deixavam linhas em `modality_request` e `organization_modality` no banco compartilhado — arquivos: `backend/src/test/java/br/com/ctcore/modality/ModalityIT.java`, `ModalityCatalogIT.java` (`@AfterEach` apaga as duas tabelas) — prova: `./backend/mvnw -f backend/pom.xml verify` → 243 testes, 0 falhas, BUILD SUCCESS
Regressão: só testes mudaram; front-end não repetido.

### Rodada 3 — OK — 2026-10-19, estado 8c1c468a276bbeff56d225641b7d616ddaa558fc

| Critério | Status | Evidência |
|---|---|---|
| CA1 a CA10, CA14 | PASSOU (mantido das rodadas 1 e 2) | `verify` completo passa de novo; só testes mudaram (`@AfterEach` em `ModalityIT` e `ModalityCatalogIT`) |
| CA11 a CA13, CA15 | PASSOU (mantido da rodada 1) | nenhum arquivo do front-end mudou desde a rodada 2 |

Comandos: `./backend/mvnw -f backend/pom.xml verify` → 243 testes de integração, 0 falhas, BUILD SUCCESS (a falha do PR #263, `modality_request_requested_by_fkey` em `ReauthIT`/`AccountIT`, não se repete). Diff desde a rodada 2: dois testes (limpeza de `modality_request` e `organization_modality`), `specs/backlog.md` e este arquivo; nenhum código de produção.
Achados: nenhum.
