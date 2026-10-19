# 41 — RF-MOD-04 · Configurações › Faixas etárias

Origem: issue #41 (Sprint 04); `docs/especificacao/04-modalidades-e-graduacao.md` (RF-MOD-04, RN-MOD-07, "Faixa etária"), `01-organizacao-e-isolamento.md` (padrão Infantil 4–12, Juvenil 13–17, Adulto 18+; RN-ORG-10, RN-ORG-17) e a linha `ORGANIZATION_SETTINGS` da tabela de permissões. Branch: `item/41-faixas-etarias`. Segue o desenho de #38 (`Modalities`, `ModalitiesPage`, `V19`).

## 1. Objetivo

Em Configurações › Faixas etárias, o Coordenador cria, renomeia, ajusta idades e apaga as faixas etárias da organização; o Administrador as vê. Elas são administrativas (organizam turmas, aula experimental e diretório) e a idade do aluno continua calculada pelo nascimento. Cada organização nasce com Infantil 4–12, Juvenil 13–17 e Adulto 18+.

Fora do escopo: usar a faixa em turmas, aula experimental e diretório e o aviso "fora da faixa" (Sprints 05, 06, 18 e 20; aqui só se guardam as faixas com idade mínima e máxima); categorias de competição no cadastro do aluno (RN-ORG-17 as exclui); notas de versão (Sprint 15, #142). RN-MOD-03 e RN-MOD-06 da issue já estão entregues em #38/#40 e não mudam aqui; RN-MOD-05 vale como regra de modelagem: variações ficam no nome da faixa, sem cadastro extra.

## 2. Requisitos

- R1 (RF-MOD-04, RN-MOD-07): `GET /api/v1/age-ranges` devolve as faixas da organização da sessão com `id`, `name`, `minAge`, `maxAge` (nulo = sem limite) e `canManage`, ordenadas por idade mínima e depois por nome.
- R2 (RN-MOD-07, RN-ORG-17, D7): `POST /api/v1/age-ranges` cria faixa com nome (2 a 40 caracteres), idade mínima (0 a 120) e idade máxima opcional (da mínima a 120); responde 201 com a faixa.
- R3 (RN-ORG-17, D2): `PUT /api/v1/age-ranges/{id}` renomeia e muda as idades com as mesmas validações; responde 200 com a faixa.
- R4 (RN-ORG-17, D3): `DELETE /api/v1/age-ranges/{id}` apaga a faixa; responde 204.
- R5 (RN-MOD-05, D4): o nome é único na organização, sem diferença de acento nem de maiúsculas (409 `AGE_RANGE_NAME_TAKEN`); faixas podem se sobrepor em idade.
- R6 (D1): toda organização nova nasce com as três faixas padrão, e as organizações existentes recebem as mesmas na migração.
- R7 (permissão, D6): `ORGANIZATION_SETTINGS` conferido no servidor: leitura para Coordenador e Administrador; escrita só com acesso `FULL` (Coordenador); Administrador recebe 403 na escrita, Professor e portal 403 em tudo, sem sessão 401.
- R8 (isolamento): faixas são sempre da organização da sessão; id de outra organização ou inexistente responde 404; filtro no código e row-level security na tabela nova.
- R9 (auditoria): criar, alterar e apagar gravam `age_range.created`, `age_range.updated` e `age_range.deleted` (ator, perfil, faixa, antes e depois), sem dado pessoal; alteração sem mudança não grava.
- R10 (tela): `/configuracoes/faixas-etarias` lista as faixas (nome e idades em texto como "4 a 12 anos" e "18 anos ou mais") e, com `canManage`, permite criar, editar e apagar (com confirmação); tem os quatro estados, funciona a partir de 360 px, passa no axe nos dois temas, tem todos os textos em `pt-BR.json`, entra no menu de Configurações com `ORGANIZATION_SETTINGS` e mostra só leitura sem `canManage`.
- R11 (contrato): `docs/openapi.yaml` e os handlers MSW refletem as quatro rotas; o seed de dados fictícios acrescenta faixas extras às organizações de teste.

## 3. Critérios de aceite

- CA1 (R1, R6): dado organização nova (criada por `Organizations.initialize`), quando `GET /age-ranges`, então Infantil 4–12, Juvenil 13–17, Adulto 18+ (`maxAge` nulo), nessa ordem, e `canManage = true` para o Coordenador.
- CA2 (R2, R5): dado Coordenador, quando cria "Master" 35 sem máximo, então 201 e a faixa aparece na lista; nome repetido ("infantil", "INFANTIL") dá 409 e nada muda; "Kids" 4–12 sobrepondo a Infantil é aceita.
- CA3 (R2, R3): dado entrada inválida (nome vazio ou com 1 ou 41 caracteres, mínima negativa ou acima de 120, máxima menor que a mínima, corpo sem nome ou sem mínima), quando cria ou altera, então 400 com o campo e nada muda.
- CA4 (R3, R9): dado faixa existente, quando renomeia e muda as idades, então 200 com os novos valores e auditoria `age_range.updated` com antes e depois; reenviar os mesmos valores devolve 200 sem nova auditoria; renomear para o nome de outra faixa dá 409.
- CA5 (R4, R9): dado faixa existente, quando apaga, então 204, ela some da lista e há auditoria `age_range.deleted`; apagar de novo dá 404.
- CA6 (R7): dado Administrador, quando lê, então 200 com `canManage = false`; quando cria, altera ou apaga, 403 e nada muda; Professor e portal, 403 em tudo; sem sessão, 401.
- CA7 (R8): dado usuário da organização A com o id de faixa da B, quando altera ou apaga, então 404 e a B segue idêntica; duas organizações com a faixa "Adulto" não se veem (API e consulta direta com o papel `ctcore_app` e o contexto de RLS: sem contexto 0 linhas, com contexto só as da própria, insert com outra organização negado).
- CA8 (R9): dado cada escrita, então a auditoria tem ator, ação e alvo, sem e-mail, senha ou token.
- CA9 (R10): dado a tela em 360 px, então sem rolagem horizontal e axe sem violações nos dois temas; estados carregando, vazio (todas apagadas, com botão de criar para quem pode), erro com "Tentar de novo" e sem permissão; Coordenador cria, edita e apaga e a lista reflete sem recarregar; erro 409 mostra a mensagem no campo nome; Administrador vê a lista sem botões de escrita.
- CA10 (R10): dado o menu de Configurações, então "Faixas etárias" aparece para Coordenador e Administrador e não para Professor.
- CA11 (R11): `OpenApiContractTest` e `ContractRoutesTest` passam com as quatro rotas; testes MSW cobrem as rotas no formato do servidor; `SeedDataIT` aplica o novo seed.
- CA12 (todos): ponta a ponta Playwright `@critical` "Coordenador cria uma faixa etária": abre Configurações › Faixas etárias, cria uma faixa e a vê na lista ao voltar à tela.

## 4. Plano técnico

- **Dados.** Migração `V22__age_range.sql` (depois de `V21__unit_modality.sql`): `age_range` (`id uuid` pk, `organization_id` referência a `organization`, `name text` com `check (btrim(name) <> '')`, `name_key text` com o nome normalizado gravado pelo código via `Modalities.normalize`, `min_age smallint not null check (between 0 and 120)`, `max_age smallint null check (between 0 and 120)`, `check (max_age is null or max_age >= min_age)`, `created_at`, `updated_at`); índice único `(organization_id, name_key)` como trava contra corrida (o código dá o 409 antes); RLS `organization_scope` como em `V19`; revert comentado no topo; `insert ... select` das três faixas padrão para cada organização existente. Seed `infra/seed/08-faixas-etarias.sql` (`on conflict do nothing`) com as três faixas padrão (o seed cria organizações direto no SQL, sem `initialize`) mais "Master" e "Kids competição" nas organizações fictícias; `SeedDataIT` limpa a tabela nova (antes de `organization`) e testa o seed.
- **API** (`/api/v1/age-ranges`, cookie e CSRF como as demais): `GET` (200 `{canManage, items:[{id,name,minAge,maxAge}]}`; 401, 403), `POST` (201; 400, 401, 403, 409), `PUT /{id}` (200; 400, 401, 403, 404, 409), `DELETE /{id}` (204; 401, 403, 404). Sem `If-Match` (D2). Código de erro novo `AGE_RANGE_NAME_TAKEN` (409) em `ErrorCode`. Rotas em `docs/openapi.yaml` no mesmo PR.
- **Back-end.** Pacote novo `br.com.ctcore.agerange`, a criar: `AgeRanges.java` (lista, cria, altera, apaga; `permissions.grant(who, Permission.ORGANIZATION_SETTINGS, null)` com a dupla `access`/`requireFull` de `Modalities`; escreve em `OrganizationTransactions`; audita com `AuditService`/`AuditEntry`; validação com `ApiValidationException`) e `AgeRangeController.java`. A alterar: `organization/Organizations.initialize` para inserir as três faixas padrão (R6). Não há conferência de dependentes ao apagar (D3).
- **Interface.** A criar `frontend/src/features/age-ranges/AgeRangesPage.tsx`, `AgeRangeDialog.tsx`, `useAgeRanges.ts` (`useState/useEffect`, como `useModalities.ts`) e testes. A alterar `App.tsx` (rota `/configuracoes/faixas-etarias`), `permissions/menuCatalog.ts` (item `ageRanges` com `ORGANIZATION_SETTINGS`, depois de `modalities`; ajustar `buildMenu.test.ts` se depender da contagem), `mocks/handlers.ts` (+ teste MSW) e `i18n/pt-BR.json` (título, menu, textos, erros). Reuso: `Card`, `states/*`, padrão de diálogo e de estados de `ModalitiesPage`/`RequestModalityDialog`. Canvas `ConfigFaixas.dc.html` não acessível ao agente (D5).
- **Segurança.** Organização e perfil vêm do principal da sessão; o corpo nunca define organização; `id` do caminho é validado contra a organização; entrada validada no servidor.
- **Riscos.** (1) Organização criada por caminho diferente de `Organizations.initialize` nasceria sem faixas; conferir por `grep` os pontos de criação. (2) Quando turmas existirem (Sprint 05), apagar faixa em uso precisará de regra; aqui nada a referencia.

## 5. Tarefas

Uma parte só: servidor (T1 a T3) e tela (T4 e T5).

- [x] T1: migração `V22__age_range.sql`, padrão em `Organizations.initialize`, seed `infra/seed/08-faixas-etarias.sql` e ajuste de `SeedDataIT` — cobre CA1 (padrão), CA7 (RLS), CA11 (seed) — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test='SeedDataIT,AgeRangeIT'`.
- [x] T2: `AgeRanges`, `AgeRangeController` e `ErrorCode.AGE_RANGE_NAME_TAKEN` com permissão, validação, auditoria e testes de integração (padrão, criar, validar, nome repetido, alterar, idempotência, apagar, perfis, isolamento com RLS, auditoria sem dado pessoal) — cobre CA1 a CA8 — prova: `verify -Dit.test=AgeRangeIT` (a criar).
- [x] T3: OpenAPI (quatro operações e esquemas) e handlers MSW com teste — cobre CA11 — prova: `./backend/mvnw -f backend/pom.xml test -Dtest='OpenApiContractTest,ContractRoutesTest'` e `npx vitest run src/mocks` em `frontend/`.
- [x] T4: `AgeRangesPage`, `AgeRangeDialog`, rota e item de menu, quatro estados, confirmação de apagar, erro 409 no campo, textos pt-BR; 360 px e axe nos dois temas — cobre CA9, CA10 — prova: `npx vitest run src/features/age-ranges src/features/permissions`.
- [x] T5: e2e `@critical` "Coordenador cria uma faixa etária" (`frontend/e2e/faixas-etarias.spec.ts`) e regressão completa (a regressão não cobre critério: confere cobertura e 100% nos pacotes exigidos) — cobre CA12 — prova: `./backend/mvnw -f backend/pom.xml verify` e, em `frontend/`, `npm run lint && npm run typecheck && npm run build && npm run test:cov && npm run e2e`.

## 6. Decisões

- D1: a organização nasce com as três faixas padrão do quadro "Configurações da organização" (`01`), inseridas em `Organizations.initialize` e, para as existentes, na migração. Alternativa: criar sob demanda na primeira leitura; descartada porque, ao apagar todas, elas voltariam sozinhas.
- D2: sem `If-Match` (RF-ORG-08 o pede para organização e unidade): cada faixa é registro independente e de baixa concorrência; vale a última gravação. Alternativa: versão por faixa; descartada por custo sem ganho.
- D3 (muda o comportamento): o Coordenador pode apagar qualquer faixa, pois nada a referencia ainda (RN-ORG-17: "cria, renomeia ou apaga"). Quando turmas, aula experimental e diretório passarem a usá-la (Sprints 05, 18, 20), essas issues definem o que acontece com o vínculo (a faixa da turma é opcional, então ela pode ficar sem faixa). Alternativa: recusar apagar faixa em uso; não aplicável hoje.
- D4: o nome é único por organização sem acento e sem diferença de maiúsculas, e as faixas podem se sobrepor (variações como "Kids competição" ficam no nome, RN-MOD-05). Alternativa: proibir sobreposição; descartada porque a especificação só pede idades mínima e máxima e o aviso "fora da faixa" não impede nada.
- D5: o canvas `ConfigFaixas.dc.html` não está acessível; a tela segue os padrões do projeto (lista em cartão, diálogo de criar/editar, confirmação de apagar) e o dono confere o layout no PR.
- D6 (muda o comportamento): o Administrador vê as faixas e não altera, seguindo a linha `ORGANIZATION_SETTINGS` da tabela de permissões (Coordenador total, Administrador leitura) e "só o Coordenador altera" as configurações da organização. Alternativa: Administrador também escreve; descartada por contrariar a tabela.
- D7: idade máxima opcional (nula = "ou mais", como "Adulto 18+"); limites de 0 a 120 anos e nome de 2 a 40 caracteres são escolha deste item, na linha do que `Modalities` faz com nomes. Se estiver errado, muda só a validação.
- Suposição: não há outro caminho de criação de organização além de `Organizations.initialize`; se houver, ele recebe a mesma inserção.

## Construção

- T1 — arquivos: `backend/src/main/resources/db/migration/V22__age_range.sql`, `backend/.../organization/Organizations.java`, `infra/seed/08-faixas-etarias.sql`, `backend/src/test/.../db/SeedDataIT.java` — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test='SeedDataIT,AgeRangeIT'` → 16 testes, 0 falhas.
- T2 — arquivos: `backend/.../agerange/AgeRanges.java`, `AgeRangeController.java`, `ErrorCode.java`, `messages.properties`, `backend/src/test/.../agerange/AgeRangeIT.java` (6 testes: CA1 a CA8, com RLS por `ctcore_app`) — prova: mesmo comando → verde.
- T3 — arquivos: `docs/openapi.yaml` (4 operações, `AgeRange`, `AgeRangeBody`, `AgeRangeList`, `AGE_RANGE_NAME_TAKEN`), `frontend/src/mocks/handlers.ts`, `frontend/src/mocks/age-ranges.msw.test.ts` — prova: `./backend/mvnw -f backend/pom.xml test -Dtest='OpenApiContractTest,ContractRoutesTest'` → 7 testes, 0 falhas; `npx vitest run src/mocks` → verde.
- T4 — arquivos: `frontend/src/features/age-ranges/{AgeRangesPage,AgeRangeDialog}.tsx`, `useAgeRanges.ts`, `AgeRangesPage.test.tsx`, `App.tsx`, `permissions/menuCatalog.ts`, `buildMenu.test.ts`, `i18n/pt-BR.json` — prova: `npx vitest run src/features/age-ranges src/features/permissions` → verde (13 testes da tela, incluindo axe nos dois temas a 360 px e menu por perfil).
- T5 — arquivos: `frontend/e2e/faixas-etarias.spec.ts` — prova: `npm run e2e` → 19 passaram, incluindo "Coordenador cria uma faixa etária".
Regressão: `./backend/mvnw -f backend/pom.xml verify` → 262 testes, 0 falhas, JaCoCo ok, BUILD SUCCESS; em `frontend/`, `npm run lint && npm run typecheck && npm run build && npm run test:cov && npm run e2e` → tudo verde (cobertura global: linhas 96,64%).
Desvios: (1) a mensagem de falha ao apagar aparece na página, não na janela de confirmação, e a janela fecha (com a janela aberta o Radix esconde o restante da tela dos leitores de tela); (2) `AGE_RANGE_NAME_TAKEN` ganhou mensagem pt-BR em `messages.properties`, o que o plano não citava.
Não é do item: nenhum.

## Verificação

### Rodada 1 — OK — 2026-10-19, estado 3c81b3c81285d37389ee146c1d2abff4ca2e8427

| Critério | Status | Evidência |
|---|---|---|
| CA1 | PASSOU | `AgeRangeIT.defaults` (verify: 262 testes, 0 falhas) |
| CA2 | PASSOU | `AgeRangeIT.createAndValidate` (Master 201, "infantil/INFANTIL/Infântil" 409, Kids sobreposta 201) |
| CA3 | PASSOU | `AgeRangeIT.createAndValidate` (9 entradas inválidas em POST e PUT, 400, contagem e valores intactos) |
| CA4 | PASSOU | `AgeRangeIT.update` (antes/depois, idempotência sem nova auditoria, 409, 404) |
| CA5 | PASSOU | `AgeRangeIT.remove` |
| CA6 | PASSOU | `AgeRangeIT.permissions` (Administrador 200/canManage=false e 403 na escrita; Professor, aluno e responsável 403; anônimo 401) |
| CA7 | PASSOU | `AgeRangeIT.isolation` (404 entre organizações; RLS com `ctcore_app`: sem contexto 0 linhas, com contexto só as próprias, insert negado) |
| CA8 | PASSOU | `AgeRangeIT.update` (auditoria sem "@", password, token); ator/ação/alvo em `AgeRanges` |
| CA9 | PASSOU | `AgeRangesPage.test.tsx` (estados, criar/editar/apagar, 409 no campo, Administrador só leitura, axe nos dois temas a 360 px) |
| CA10 | PASSOU | `AgeRangesPage.test.tsx` CA10 e `buildMenu.test.ts`; `menuCatalog.ts` com `ORGANIZATION_SETTINGS` |
| CA11 | PASSOU | `OpenApiContractTest`/`ContractRoutesTest` e `SeedDataIT` dentro do verify; `age-ranges.msw.test.ts` dentro do `test:cov` |
| CA12 | PASSOU | `npm run e2e`: 19 passaram, incluindo "Coordenador cria uma faixa etária" |

Comandos:
- `./backend/mvnw -f backend/pom.xml verify` → 262 testes, 0 falhas, JaCoCo ok, BUILD SUCCESS.
- em `frontend/`: `npm run lint && npm run typecheck && npm run build && npm run test:cov && npm run e2e` → exit 0; linhas 96,64%; e2e 19 de 19.
- Único ponto de criação de organização: `Organizations.initialize` (grep de `insert into organization`), que recebeu as faixas padrão; seeds criam organizações em SQL e o `08-faixas-etarias.sql` cobre.

Achados:
- sugestão, código, `AgeRangeController.Body` — `minAge` com tipo errado (ex.: texto) depende do tratamento global de JSON ilegível; não foi exercitado por teste do item.

## Entrega

- Faixas etárias da organização (RF-MOD-04): migração V22 com RLS, API em `/api/v1` (listar, criar, editar, apagar) com permissão no servidor, auditoria sem dado sensível e faixas padrão na criação da organização; tela Configurações › Faixas etárias com os quatro estados, axe nos dois temas e menu por perfil; contrato no OpenAPI, handlers MSW, seed `08` e e2e.
- Verificação: `verify` do back-end (262 testes, JaCoCo ok) e `lint`, `typecheck`, `build`, `test:cov`, `e2e` do front-end (19 de 19) verdes; a base não avançou, então a regressão não foi repetida.
- Decisões: a falha ao apagar aparece na página (não na janela de confirmação); `AGE_RANGE_NAME_TAKEN` ganhou mensagem pt-BR.
- Limitação: `minAge` com tipo errado depende do tratamento global de JSON ilegível, sem teste próprio.
