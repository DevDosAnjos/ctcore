# 30 — RF-ORG-03 · Assistente de nova unidade

Origem: issue #30 (Sprint 03); `docs/especificacao/01-organizacao-e-isolamento.md` (RF-ORG-03, RN-ORG-14, RN-ORG-05, RN-ORG-15). Branch: `item/30-assistente-nova-unidade`.

## 1. Objetivo

Permitir ao Coordenador criar uma unidade nova pela tela Configurações › Unidades, num assistente curto (nome, endereço, contato e convite opcional de Administrador), com o endereço guardado com latitude e longitude para o diretório. A API cria a unidade com configurações padrão e nome curto gerado, tudo ou nada, respeitando o limite de 10 unidades.

Fora do escopo: etapa de modalidades oferecidas (D2), checklist "Primeiros passos" (RF-CAD-05), mapa interativo (D3), editar a unidade (#31), desativar (#34), tela de Usuários e convite de Administrador já existente na organização (#37).

## 2. Requisitos

- R1: `POST /api/v1/units` cria uma unidade da organização do vínculo ativo e devolve `UnitSummary` (201), com `active = true`, `city` e `shortNameAdjusted = false` (RF-ORG-03).
- R2: só o Coordenador cria unidade (linha `UNITS` da tabela de permissões); os demais perfis recebem 403 e sem login 401 (D1).
- R3: o nome é obrigatório (até 120 caracteres); endereço (CEP, logradouro, cidade), telefone, e-mail e WhatsApp são opcionais e validados quando enviados (D4).
- R4: o nome curto é gerado do nome por `ShortName.generate`, único na plataforma, e o nome inicial entra no histórico `unit_short_name_history` (RN-ORG-15, D7).
- R5: a unidade guarda latitude e longitude: enviadas pelo cliente (ajuste manual) ou, se ausentes e com CEP, obtidas pela porta `MapLocator`; sem resultado, ficam vazias, sem impedir o cadastro (RN-ORG-14, D3).
- R6: a unidade nasce com `unit_settings` padrão e fuso `America/Sao_Paulo` (D8).
- R7: o limite de 10 unidades por organização é conferido na mesma transação, sob trava da organização; no limite a resposta é 409 `UNIT_LIMIT_REACHED` e nada é criado (RN-ORG-05, D5).
- R8: o convite do Administrador é opcional (nome e e-mail); depois de criada a unidade, `InviteService.invite` convida como `ADMINISTRATOR` vinculado só a essa unidade; a falha do convite não desfaz a unidade e é informada na resposta (D6).
- R9: a criação grava na auditoria `unit.create` (depois, sem dado sensível) na mesma transação.
- R10: a tela é um assistente em três etapas (Unidade, Endereço, Contato e convite) com validação por etapa, quatro estados, 360 px, axe nos dois temas, textos em pt-BR; no limite de 10 unidades o botão "Nova unidade" fica desativado com o motivo.
- R11: `docs/openapi.yaml` e os handlers MSW trazem a rota nova.

## 3. Critérios de aceite

- CA1 (R1): dado Coordenador, quando envia `POST /api/v1/units` com `name` válido, então 201 com `id`, `name`, `shortName` gerado, `active = true`, `administrators` vazio e `activeStudents` 0, e a unidade aparece em `GET /api/v1/units`.
- CA2 (R2): dado Administrador, Professor ou Aluno, quando chama `POST`, então 403 e nenhuma unidade é criada; sem login, 401.
- CA3 (R2): dado Coordenador da organização A, quando cria, então a unidade pertence à A; um corpo com `organizationId` é ignorado.
- CA4 (R3): dado nome vazio ou só espaços, ou com mais de 120 caracteres, e-mail sem formato válido, CEP com formato inválido, ou latitude fora de -90..90 / longitude fora de -180..180, então 400 `VALIDATION_ERROR` com o campo, e nada é criado.
- CA5 (R4): dado nome "Academia Leão — Tijuca" e já existindo `academia-leao-tijuca`, então a nova recebe `academia-leao-tijuca-2`; o nome curto inicial está em `unit_short_name_history` com `replaced_at` nulo e `UnitShortNames.resolve` o devolve.
- CA6 (R5): dado CEP `01001-000` sem coordenadas, então a unidade guarda a latitude e a longitude da porta; dado CEP desconhecido, então 201 com coordenadas vazias; dado latitude e longitude enviadas, então valem as enviadas e a porta não é chamada.
- CA7 (R6): dado unidade criada, então existe `unit_settings` com os padrões do banco e `timezone = America/Sao_Paulo`.
- CA8 (R7): dado organização com 10 unidades, quando cria a 11ª, então 409 `UNIT_LIMIT_REACHED` e continua com 10; dado duas criações simultâneas com 9 unidades, então só uma é criada.
- CA9 (R8): dado convite com nome e e-mail, então depois do 201 há convite pendente `ADMINISTRATOR` vinculado só à nova unidade, o e-mail é enviado pela porta e a resposta traz `invite: SENT`.
- CA10 (R8): dado e-mail que já é Administrador ativo da organização (ou convite recusado), então a unidade é criada e a resposta traz `invite: FAILED`; dado sem convite, `invite: NONE`; dado só o e-mail sem nome (ou o inverso), então 400.
- CA11 (R9): dado criação feita, então há uma linha de auditoria `unit.create` com nome, nome curto e cidade, sem senha, token, dado de cartão nem e-mail do convidado; dado criação recusada (403, 400, 409), então não há linha.
- CA12 (R9): dado falha ao gravar qualquer parte (unidade, configurações, histórico ou auditoria), então nada permanece (tudo ou nada).
- CA13 (R10): dado a tela `/configuracoes/unidades/nova`, então mostra as três etapas com Voltar e Avançar, só avança com a etapa válida, mostra o resumo do convite e, ao concluir, volta à lista com a unidade nova; mostra carregando ao enviar, erro com nova tentativa, e "sem permissão" para Administrador.
- CA14 (R10): dado 360 px, então não há rolagem horizontal da página; axe sem violações nos dois temas.
- CA15 (R10): dado lista com 10 unidades, então "Nova unidade" fica desativado com o texto do limite; dado menos de 10, abre o assistente.
- CA16 (R10): dado 409 `UNIT_LIMIT_REACHED` ou convite `FAILED`, então a tela mostra a mensagem correspondente em pt-BR (no segundo caso, a unidade criada e o aviso do convite).
- CA17 (R11): `OpenApiContractTest` e `ContractRoutesTest` passam com a rota.
- CA18 (R2): dado Administrador no menu, então não vê "Nova unidade" e a rota do assistente mostra "sem permissão".

## 4. Plano técnico

- Dados: migração `V14__unit_postal_code.sql` com `unit.postal_code text` (CEP só dígitos) e reversão no arquivo. `latitude`, `longitude`, `address`, `email`, `phone`, `whatsapp`, `city` e `timezone` já existem em `unit` (V3, V13). Sem tabela nova; RLS de `unit`, `unit_settings` e `unit_short_name_history` já cobre. O endereço continua texto livre (`address` = logradouro e número; bairro dentro dele), como em #29 (D6 lá).
- API (`docs/openapi.yaml`): `POST /api/v1/units`, corpo `NewUnit { name, postalCode?, address?, city?, latitude?, longitude?, phone?, email?, whatsapp?, administrator? { name, email } }`; 201 `NewUnitResult { unit: UnitSummary, invite: NONE|SENT|FAILED }`; 400 `VALIDATION_ERROR`; 401; 403; 409 `UNIT_LIMIT_REACHED`. `UnitSummary` não muda; endereço e coordenadas só voltam na edição da #31.
- Back-end (`br.com.ctcore.organization`): `UnitList.create` (ou `UnitCreation`, a criar, se `UnitList` ficar grande): `requireFull`; valida na borda; `transactions.execute` trava `organization` com `select ... for update`, conta unidades e chama `Organizations.requireUnitCapacity(count, false)` (a `IllegalStateException` vira `UNIT_LIMIT_REACHED`); gera o nome curto com `ShortName.generate` conferindo `unit` e `unit_short_name_history`; resolve coordenadas (enviadas, ou `MapLocator.locate(cep)`); insere `unit`, `unit_settings` e a linha do nome na história; grava `unit.create` via `AuditService`. Fora da transação, se `administrator` veio, `InviteService.invite(new Inviter(who.accountId(), who.role(), Set.of()), org, email, name, ADMINISTRATOR, Set.of(unitId), null)`; `IdentityException` ou `AccessDeniedException` viram `invite = FAILED`. `UnitController` ganha `@PostMapping`. `ErrorCode` ganha `UNIT_LIMIT_REACHED(CONFLICT)` e `messages.properties` a chave `error.UNIT_LIMIT_REACHED`.
- Segurança: permissão no servidor (`Permission.UNITS == FULL`); organização sempre a do principal; validação de formato na borda; auditoria sem e-mail nem dado sensível; CEP só dígitos; coordenadas validadas por faixa; teste de isolamento entre organizações e entre perfis na rota nova (`RouteIsolationModelIT`/molde de `UnitRouteIT`).
- Interface (`frontend/src/features/units/`): `NewUnitPage.tsx` (rota `/configuracoes/unidades/nova` em `ManagementLayout`/`RequireArea` `UNITS`, registrar em `App.tsx`), três etapas com o `Stepper` existente e campos de `components/fields`; `useUnits.ts` ganha `createUnit`; `UnitsPage.tsx` ganha o botão "Nova unidade" (desativado com 10 unidades). Latitude e longitude ficam em "Ajustar coordenadas" (campos numéricos) na etapa de endereço (D3). Textos em `pt-BR.json` (`units.new.*`, erro `UNIT_LIMIT_REACHED`). `mocks/handlers.ts`: `POST /units` por papel, com limite e convite. Canvas `UnidadeNova.dc.html` não acessível (ver Suposição).
- Arquivos a alterar (vistos): `backend/src/main/java/br/com/ctcore/organization/UnitList.java`, `.../UnitController.java`, `.../api/error/ErrorCode.java`, `docs/openapi.yaml`, `frontend/src/App.tsx`, `frontend/src/features/units/UnitsPage.tsx`, `frontend/src/features/units/useUnits.ts`, `frontend/src/mocks/handlers.ts`, `frontend/src/i18n/pt-BR.json`, `infra/seed/04-unidades.sql` (só se o CEP entrar na semente). A criar: `V14__unit_postal_code.sql`, `NewUnitPage.tsx`, testes `UnitCreateIT`, `UnitCreateRouteIT`, `NewUnitPage.test.tsx`, `e2e/nova-unidade.spec.ts` `@critical`.
- Reuso: `ShortName`, `UnitShortNames`, `Organizations.requireUnitCapacity`, `MapLocator`, `InviteService`/`Inviter`, `AuditService`, `OrganizationTransactions`, `ApiValidationException`/`ApiStatusException`, `Stepper`, `states`, `Card`, `fields`.
- Riscos: corrida de duas criações com 9 unidades (travada por `for update` na organização, coberta por teste concorrente); convite fora da transação deixa unidade sem Administrador se falhar (informado em `invite = FAILED`; o Coordenador convida depois em #37); `InviteService.invite` abre a própria transação (por isso roda depois do commit); cobertura de `isolation` continua 100%.

## 5. Tarefas

- [x] T1: migração `V14__unit_postal_code.sql` e `ErrorCode.UNIT_LIMIT_REACHED` com mensagem pt-BR — não cobre critério: base de R3, R7 e CA8, conferida em T3 — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test='UnitCreateIT,SeedDataIT'`.
- [x] T2: validação do corpo (nome, CEP, e-mail, telefone, faixas de latitude e longitude, convite completo) — cobre CA4, CA10 (400) — prova: `./backend/mvnw -f backend/pom.xml test -Dtest='NewUnitValidationTest'` e `UnitCreateIT`.
- [x] T3: `UnitList.create` com trava, limite, nome curto e história, coordenadas, `unit_settings`, auditoria e tudo ou nada — cobre CA1, CA3, CA5, CA6, CA7, CA8, CA11, CA12 — prova: `verify -Dit.test=UnitCreateIT`.
- [x] T4: convite opcional depois do commit, com `SENT`, `FAILED` e `NONE` — cobre CA9, CA10 — prova: `verify -Dit.test=UnitCreateIT`.
- [x] T5: `POST` no `UnitController` e erros 400/401/403/409 — cobre CA1, CA2 — prova: `verify -Dit.test=UnitCreateRouteIT`.
- [x] T6: teste de isolamento entre organizações e entre perfis na rota nova — cobre CA2, CA3 — prova: `verify -Dit.test=UnitCreateRouteIT`.
- [x] T7: OpenAPI e teste de contrato — cobre CA17 — prova: `./backend/mvnw -f backend/pom.xml test -Dtest='OpenApiContractTest,ContractRoutesTest'`.
- [x] T8: front base: handlers MSW, `createUnit`, textos `units.new.*` — cobre CA16 (base) — prova: `npx vitest run src/mocks src/features/units`.
- [x] T9: `NewUnitPage` (três etapas, quatro estados, 360 px, axe), botão e limite na `UnitsPage`, rota em `App.tsx` — cobre CA13, CA14, CA15, CA16, CA18 — prova: `npx vitest run src/features/units`.
- [x] T10: e2e `@critical` do Coordenador (criar com convite) e do Administrador (sem acesso) — cobre CA13, CA18 — prova: `npm run e2e`.
- [x] T11: regressão do item — não cobre critério: confere que nada quebrou e a cobertura — prova: `./backend/mvnw -f backend/pom.xml verify` e, em `frontend/`, `npm run lint && npm run typecheck && npm run build && npm run test:cov && npm run e2e`.

## 6. Decisões

- D1 (muda o comportamento): `POST /api/v1/units`, organização sempre a do vínculo ativo; só o Coordenador cria, conforme a linha `UNITS` da tabela de permissões, mesmo princípio do item 29. Alternativa descartada: deixar o Administrador criar unidade, que contraria a tabela e o limite de unidades por plano.
- D2 (muda o comportamento): a etapa "modalidades oferecidas" não entra neste item, porque o catálogo de modalidades e a oferta por unidade só existem nas #38 e #40 (Sprint 04). O assistente fica com três etapas e a #40 acrescenta a quarta. Alternativa descartada: guardar texto livre de modalidades, que seria jogado fora.
- D3: o mapa interativo depende do fornecedor da Sprint 22; por isso "ajustável no mapa" vira campos de latitude e longitude no assistente, e a geocodificação usa a porta `MapLocator` (versão simulada por CEP). Sem resultado, a unidade é criada sem coordenadas (o diretório só mostra unidades com coordenadas, quando existir). Alternativa descartada: bloquear o cadastro sem coordenadas.
- D4: só o nome é obrigatório; endereço, contato e convite são opcionais, para o assistente ser curto e o resto ir para os "Primeiros passos" (RF-ORG-03). O CEP ganha coluna própria (`postal_code`) para a geocodificação, e o resto do endereço segue texto livre em `address`. Alternativa descartada: extrair o CEP de `address`.
- D5: o limite de 10 unidades responde 409 com código próprio `UNIT_LIMIT_REACHED` (mensagem clara na tela), conferido sob trava da linha da organização (`for update`). A exceção "só pelo suporte" (RN-ORG-05) não tem rota ainda (`supportAuthorized` = false).
- D6 (muda o comportamento): o convite do Administrador é enviado depois do commit da unidade; se falhar (conta já ligada, e-mail recusado), a unidade permanece e a resposta traz `invite = FAILED`. Alternativa descartada: desfazer a unidade, que perderia o cadastro por um erro de e-mail e obrigaria reabrir o assistente. Convite a quem já é Administrador da organização fica para a #37.
- D7: o nome curto inicial entra no histórico no momento da criação, fechando a pendência apontada na construção do #29 (o nome original só entrava na história no primeiro ajuste).
- D8: o fuso é o padrão do banco (`America/Sao_Paulo`); não há campo de fuso no assistente. As demais configurações da unidade usam os padrões de `unit_settings`.
- D9: o recurso não é liberado aos poucos por organização (não está na lista da Sprint 03), então não há flag; não existe arquivo de notas de versão no repositório, então não há nota a atualizar.
- Suposição: o canvas `UnidadeNova.dc.html` não está acessível; as etapas seguem o texto da issue e os padrões existentes. Se divergir, só a tela e os textos mudam.
- Suposição: o validador de telefone/WhatsApp/e-mail é o já usado em `OrganizationProfile`/`format.ts` (#9, #28); se não houver validador reaproveitável, entra uma validação mínima de formato em T2.

## Construção

Retomada: parte do back-end e do front já estava na pasta de trabalho sem tarefas marcadas; conferida contra o código e as provas antes de marcar.

- T1 — arquivos: `V14__unit_postal_code.sql`, `ErrorCode.java`, `messages.properties` — prova: `verify -Dit.test='UnitCreateIT,SeedDataIT'` → verde
- T2 — arquivos: `NewUnit.java`, `NewUnitValidationTest.java` — prova: `NewUnitValidationTest` e `UnitCreateIT` → verde
- T3 — arquivos: `UnitCreation.java` (serviço próprio, como o plano permite), `UnitCreateIT.java` (11 testes, inclui concorrência e tudo ou nada) — prova: `verify -Dit.test=UnitCreateIT` → 11 verdes
- T4 — arquivos: `UnitCreation.java` (convite após o commit) — prova: `UnitCreateIT` → verde
- T5 — arquivos: `UnitController.java`, `UnitBase.java` — prova: `UnitCreateRouteIT` → verde
- T6 — arquivos: `UnitCreateRouteIT.java` — prova: `UnitCreateRouteIT` → 2 verdes
- T7 — arquivos: `docs/openapi.yaml` — prova: `test -Dtest='OpenApiContractTest,ContractRoutesTest'` → verde
- T8 — arquivos: `useUnits.ts`, `mocks/handlers.ts`, `pt-BR.json` — prova: `npx vitest run src/mocks src/features/units` → 37 verdes
- T9 — arquivos: `NewUnitPage.tsx`, `NewUnitPage.test.tsx`, `UnitsPage.tsx`, `App.tsx` — prova: `npx vitest run src/features/units` → verde (inclui axe nos dois temas a 360 px)
- T10 — arquivos: `e2e/nova-unidade.spec.ts` — prova: `npm run e2e` → 12 verdes
- T11 — regressão abaixo.

Regressão: `./backend/mvnw -f backend/pom.xml verify` → 164 testes de integração verdes, "All coverage checks have been met", BUILD SUCCESS. Em `frontend/`: `npm run lint && typecheck && build && test:cov && e2e` → 318 testes verdes (45 arquivos), cobertura 94,6% (linhas 96,9%), e2e 12 verdes.
Desvios: `UnitCreation` é classe própria em vez de `UnitList.create`; corrigido erro de digitação em `useUnits.ts` (`Loaded ={`).
Não é do item: nenhum.

## Verificação

### Rodada 1 — OK — 2026-10-17, estado 168adc8b98ec1b5e813653d4c7016ce7452f4833

| Critério | Status | Evidência |
|---|---|---|
| CA1, CA3 | PASSOU | `UnitCreateIT.creates` verde (11 testes do IT) |
| CA2, CA18 (servidor) | PASSOU | `UnitCreateRouteIT` (2 verdes): perfis e organizações |
| CA4, CA10 (400) | PASSOU | `NewUnitValidationTest` (4) e `UnitCreateIT.invalid` |
| CA5, CA6, CA7 | PASSOU | `UnitCreateIT.shortName`, `sameNameAcrossOrganizations`, `coordinates` |
| CA8 | PASSOU | `UnitCreateIT.limit` e `concurrent` |
| CA9, CA10 | PASSOU | `UnitCreateIT.invite`, `inviteFails` |
| CA11, CA12 | PASSOU | `UnitCreateIT.audited`, `allOrNothing` (trigger força falha da auditoria) |
| CA13, CA15, CA16, CA18 (tela) | PASSOU | `NewUnitPage.test.tsx` (8 casos) e e2e `@critical` nova-unidade |
| CA14 | PASSOU | axe nos dois temas a 360 px declarado em `NewUnitPage.test.tsx`; teste verde (não reaberto linha a linha) |
| CA17 | PASSOU | `verify` completo inclui `OpenApiContractTest` e `ContractRoutesTest` |

Comandos: `./backend/mvnw -f backend/pom.xml verify` → 164 IT verdes, "All coverage checks have been met", BUILD SUCCESS. Em `frontend/`: `npm run lint && typecheck && build && test:cov` → 45 arquivos, 318 testes verdes, linhas 96,91%; `npm run e2e` → 12 verdes.
Achados:
- sugestão, código, `UnitCreation.invite` — só captura `IdentityException | AccessDeniedException`; falha de envio de e-mail por outra exceção devolveria 500 depois de a unidade já ter sido criada, em vez de `invite: FAILED`.

## Entrega

RF-ORG-03: assistente de nova unidade em três etapas (Unidade, Endereço, Contato e convite) e `POST` de criação de unidade, com migração `V14` (CEP), limite de 10 unidades, nome curto único por organização, coordenadas, convite opcional após o commit e auditoria `unit.create` sem dado sensível. Contrato em `docs/openapi.yaml` e handlers MSW atualizados.

Verificação: `verify` do back-end (164 IT verdes, cobertura ok); no front, lint, typecheck, build, `test:cov` (318 testes, 96,9% de linhas) e e2e (12 verdes).

Limitação conhecida (sugestão da verificação): `UnitCreation.invite` só captura `IdentityException | AccessDeniedException`; outra falha no envio do e-mail devolveria 500 após a unidade criada, em vez de `invite: FAILED`.
