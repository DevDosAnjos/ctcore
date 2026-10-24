# 80 — RF-COB-09 · Configurações › Unidade › Lembretes

Origem: issue #80 (Sprint 09); `docs/especificacao/10-cobrancas.md` (RF-COB-09, RN-COB-17, RN-COB-19), `01-organizacao-e-isolamento.md` (RF-ORG-04, padrões da unidade) e `13-comunicacao-e-notificacoes.md` (RN-COM-13). Branch: `item/80-configuracoes-lembretes`. Parte do motor da #79 (`ReminderJob`, que já lê `unit_settings.reminders_enabled`, `reminders_hour` e `reminder_days`) e da tela `UnitSettingsPage`, que já tem um cartão "Lembretes" só com o campo de dias. A tela `LembretesAuto.dc.html` do canvas está inacessível: o dono confere o layout (D4).

## 1. Objetivo

A gestão (Coordenador; Administrador nas suas unidades) liga e desliga os lembretes automáticos de cobrança da unidade, escolhe a hora de envio e os dias em relação ao vencimento, numa seção "Lembretes" própria em Configurações › Unidade, e vê o que é fixo (aviso de bloqueio, canal). Hoje só os dias são editáveis; liga/desliga e hora existem no banco desde a #79, mas sem tela nem API.

Fora do escopo: escolha de canais gravada (push não existe, D2); textos editáveis dos lembretes (RN-COM-12, #83); mudar o motor de envio (#79); o aviso de bloqueio, que é fixo e não se configura (RN-COB-17); ponta a ponta (D5).

## 2. Requisitos

- R1: `GET /units/{id}` devolve em `settings` os campos `remindersEnabled` (boolean) e `remindersHour` (inteiro de 0 a 23, hora da unidade) além de `reminderDays`.
- R2: `PUT /units/{id}` exige `settings.remindersEnabled` e `settings.remindersHour`, grava os dois em `unit_settings` e recusa hora fora de 0 a 23 ou ausente (400, campo `settings.remindersHour`).
- R3: a alteração de qualquer dos três campos de lembrete grava `unit.update` na auditoria com antes e depois, e entra no controle de versão (`If-Match`) como as demais.
- R4: só quem tem `UNIT_SETTINGS` completo na unidade altera; Administrador de outra unidade e outra organização continuam recusados (403 e 404), como no restante de `PUT /units/{id}`.
- R5: a tela traz o cartão "Lembretes" com: interruptor "Enviar lembretes automáticos"; campo de hora (0 a 23, texto "horário da unidade"); campo de dias (o atual, de -30 a 60, até 8, sem repetição); texto com o padrão (3 dias antes, no dia, 3 e 10 dias depois); e o aviso fixo 5 dias antes do bloqueio, apenas informativo (D1, D2).
- R6: com o interruptor desligado, hora e dias ficam desabilitados e o texto avisa que nenhum lembrete automático sai, mas que o aviso de bloqueio e o envio manual continuam (D1 da #79: o manual não depende do interruptor).
- R7: a tela mantém os quatro estados, funciona a partir de 360 px, passa no axe nos dois temas e todos os textos vêm de `pt-BR.json`; o diálogo de conflito de edição lista os campos novos.
- R8: `docs/openapi.yaml` e os handlers MSW refletem `remindersEnabled` e `remindersHour` e as mesmas validações.

## 3. Critérios de aceite

- CA1 (R1): dado uma unidade recém-criada, quando `GET /units/{id}`, então `remindersEnabled = true`, `remindersHour = 9` e `reminderDays = [-3,0,3,10]`.
- CA2 (R2, R3): dado Coordenador, quando `PUT` com `remindersEnabled = false` e `remindersHour = 14`, então 200 devolve os novos valores, o banco os tem, a versão sobe e a auditoria `unit.update` traz `remindersEnabled` e `remindersHour` com antes e depois.
- CA3 (R2): dado `remindersHour` 24, -1 ou ausente, ou `remindersEnabled` ausente, quando `PUT`, então 400 `VALIDATION_ERROR` no campo e nada é gravado.
- CA4 (R2): dado `PUT` que só muda os dias de lembrete, então `remindersEnabled` e `remindersHour` do corpo valem (substituição completa) e o motor da #79 usa os novos dias na próxima passada (`ReminderJobIT` continua verde).
- CA5 (R4): dado Administrador de outra unidade, Professor e portal, quando `PUT`, então 403; dado unidade de outra organização, então 404 (nos moldes de `UnitSettingsRouteIT`).
- CA6 (R3): dado `PUT` sem nenhuma mudança, então a versão não sobe e nada é auditado.
- CA7 (R5): dado a página aberta, então o cartão "Lembretes" mostra interruptor, hora e dias com os valores da unidade, o texto do padrão e o aviso fixo de bloqueio.
- CA8 (R5): dado hora "25" ou vazia, ou dias inválidos, quando salvar, então a mensagem de erro aparece junto do campo, o botão não envia e o foco vai ao campo com erro.
- CA9 (R6): dado interruptor desligado, então hora e dias ficam desabilitados e o aviso de "nenhum lembrete automático" aparece; ao ligar de novo, voltam editáveis com os valores anteriores.
- CA10 (R5, R2): dado alterações no cartão, quando salvar, então o `PUT` leva `remindersEnabled`, `remindersHour` e `reminderDays` e a tela mostra a confirmação.
- CA11 (R7): dado a lista de campos do conflito (409), então "Lembretes automáticos" e "Hora dos lembretes" aparecem nas diferenças.
- CA12 (R7): a tela passa no axe nos dois temas, cabe em 360 px e mostra carregando, erro e sem permissão (vazio: "Todas as unidades" ou sem unidade, como já existe).
- CA13 (R8): o MSW responde e valida `PUT /units/{id}` como o servidor (hora 0 a 23 obrigatória) e `OpenApiContractTest` e `ContractRoutesTest` passam.

## 4. Plano técnico

- Dados: não muda. `unit_settings.reminders_enabled` e `reminders_hour` (padrão `true` e 9, check 0 a 23) existem na `V43__charge_reminders.sql`; `infra/seed/` não precisa mudar (colunas com padrão).
- API: `PUT /units/{id}` e `GET /units/{id}` ganham os dois campos (nada de rota nova, logo o teste de rota é o do `PUT` existente). Entrada obrigatória, saída sempre presente. Erros como hoje: 400, 401, 403, 404, 409, 428. Quem pode: igual a hoje (D3).
- Interface: o cartão "Lembretes" de `UnitSettingsPage.tsx` é reescrito (D1); continua dentro do mesmo formulário e do mesmo botão Salvar. Estados já tratados por `Detail` e `Content`.
- Segurança: permissão no servidor já existe em `UnitSettings.update` (`UNIT_SETTINGS` `FULL`); validação da hora no servidor (`UnitUpdate.Settings.validated`) e no check do banco; auditoria sem dado sensível.
- Arquivos (todos vistos):
  - `backend/src/main/java/br/com/ctcore/organization/UnitUpdate.java`: `Settings` ganha `Boolean remindersEnabled, Integer remindersHour`; `validated()` exige os dois e a hora entre 0 e 23 (campo `settings.remindersHour`).
  - `backend/src/main/java/br/com/ctcore/organization/UnitSettings.java`: `Settings` ganha os dois campos; `SELECT` traz `s.reminders_enabled, s.reminders_hour` no fim da lista dos campos de `unit_settings` e `detail(...)` ajusta os índices das colunas (`teacher_permissions`, `directory_enabled`, ..., `u.version`); o `update unit_settings` grava as colunas; `flat(...)` ganha `remindersEnabled` e `remindersHour` (auditoria e versão).
  - `docs/openapi.yaml`: esquemas `UnitDetail.settings` (~linha 4016) e `UnitUpdate.settings` (~4052) ganham os campos (`required`, hora com `minimum: 0`, `maximum: 23`); descrição do `PUT` cita a hora.
  - `frontend/src/features/units/useUnitSettings.ts`: `UnitSettingsData` e o tipo do corpo do PUT ganham os campos.
  - `frontend/src/features/units/UnitSettingsPage.tsx`: `Draft`, `toDraft`, `toBody`, validação (`problems`), `differences`, e o cartão "Lembretes" (reusa `Check` e `Text`).
  - `frontend/src/i18n/pt-BR.json`: grupo `unitSettings` ganha `remindersEnabled`, `remindersEnabledHelp`, `remindersHour`, `remindersHourHelp`, `remindersHourInvalid`, `remindersOff`, `remindersDefault`, `remindersBlockNotice`, `remindersChannel`.
  - `frontend/src/mocks/handlers.ts`: `defaultUnitSettings` (~linha 290) e o `PUT /units/{id}` (~1857) ganham os campos e a validação.
  - Testes a alterar: `backend/src/test/java/br/com/ctcore/organization/UnitSettingsIT.java`, `UnitSettingsRouteIT.java`, `UnitUpdateValidationTest.java`, `OrganizationsIT.java` (corpos de `PUT` e as asserções de `settings`) e `frontend/src/features/units/UnitSettingsPage.test.tsx`. A criar: nenhum arquivo de código novo.
- Reuso: `UnitSettings.update` (permissão, `If-Match`, auditoria, `LastChange`), `UnitUpdate.days`/`required`, `Check`/`Text`/`Card` da própria página, `EditConflictDialog`, `ReminderJob` (consome as colunas, sem mudança), `UnitBase` e `UnitSettingsRouteIT` para os testes.
- Riscos: (1) `detail(...)` lê as colunas por índice: errar um índice quebra `GET` e `PUT`, percebido por `UnitSettingsIT`. (2) Campo novo obrigatório no `PUT` quebra qualquer chamador que o omita (testes e MSW); vistos acima. (3) Canvas inacessível (D4). (4) `ReminderJobIT` lê as colunas direto no banco e não deve mudar.

## 5. Tarefas

- [x] T1: servidor — `UnitUpdate.Settings` (campos e validação 0 a 23), `UnitSettings` (`Settings`, `SELECT`, `detail`, `update`, `flat`) e ajuste dos testes de unidade existentes — cobre CA1, CA2, CA3, CA4, CA6 — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test='UnitSettingsIT' -Dtest='UnitUpdateValidationTest' -Dsurefire.failIfNoSpecifiedTests=false`
- [x] T2: permissão e isolamento nas rotas com o corpo novo e contrato — `UnitSettingsRouteIT`, `OrganizationsIT` e `docs/openapi.yaml` — cobre CA5, CA13 (servidor) — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test='UnitSettingsRouteIT,OrganizationsIT' -Dtest=NoTest -Dsurefire.failIfNoSpecifiedTests=false` e `./backend/mvnw -f backend/pom.xml test -Dtest='OpenApiContractTest,ContractRoutesTest'`
- [x] T3: front — tipos em `useUnitSettings.ts`, handlers MSW com a validação e textos em `pt-BR.json` — cobre CA13 (MSW) — prova: em `frontend/`, `npx vitest run src/features/units/UnitSettingsPage.test.tsx` (os testes de MSW da página usam o handler) e `npm run typecheck`
- [x] T4: front — cartão "Lembretes" em `UnitSettingsPage.tsx` (interruptor, hora, dias, avisos, validação, diferenças do conflito) e testes de tela com axe — cobre CA7, CA8, CA9, CA10, CA11, CA12 — prova: em `frontend/`, `npx vitest run src/features/units/UnitSettingsPage.test.tsx`; ao fim `./backend/mvnw -f backend/pom.xml verify` e, em `frontend/`, `npm run lint && npm run typecheck && npm run build && npm run test:cov && npm run e2e`

## 6. Decisões

- D1 (muda o comportamento): a seção "Lembretes" continua dentro de Configurações › Unidade (RF-ORG-04 lista "lembretes" entre as configurações da unidade e já existe um cartão assim), sem rota própria. Liga/desliga é um interruptor geral da unidade e "desligar um lembrete" é tirar o dia da lista, que segue como campo de dias (já validado de -30 a 60, até 8). Alternativa: um interruptor por lembrete para os quatro dias padrão, descartada porque a lista de dias já permite dias fora do padrão e duplicaria a regra.
- D2 (muda o comportamento): canais. A RN-COB-17 fala em "canais", mas o único canal real é o e-mail (push não existe; o SMS é só do aviso de bloqueio, fixo). A tela mostra o e-mail como canal, sem interruptor e sem coluna nova, e o aviso fixo de bloqueio como informação. Quando o push existir, a escolha de canais entra num item próprio. Alternativa: gravar flags de canal agora, descartada por ser dado sem efeito.
- D3: quem altera é o mesmo de `PUT /units/{id}`: Coordenador e Administrador da unidade (`UNIT_SETTINGS` completo); a especificação não separa uma permissão só para lembretes.
- D4: o canvas `LembretesAuto.dc.html` não pôde ser aberto; a tela segue os componentes da página atual e a especificação. O dono confere o layout na revisão da sprint.
- D5: sem ponta a ponta nem recurso desligado por organização: é configuração de gestão sobre regra existente, como a D10 da #79; a prova é integração e tela. Notas de versão ainda não existem (RF-AJU-04, Sprint 21); nada a atualizar.
- D6 (muda o comportamento): os dois campos novos são obrigatórios no `PUT` (a rota é substituição completa). Alternativa: opcionais mantendo o valor atual, descartada para não abrir exceção à regra de substituição completa já documentada no OpenAPI.
- Suposição: a hora é sempre na hora da unidade (o `ReminderJob` já compara com o fuso da unidade); se o dono quiser a hora de Brasília fixa, só o texto de ajuda muda.

## Construção

- T1 — arquivos: `organization/UnitUpdate.java`, `organization/UnitSettings.java`, `UnitUpdateValidationTest`, `UnitBase`, `UnitSettingsIT` (teste novo `reminders` cobre CA1, CA2, CA3, CA4, CA6) — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test='UnitSettingsIT' -Dtest='UnitUpdateValidationTest' -Dsurefire.failIfNoSpecifiedTests=false` → 8 + 9 testes verdes (o `verify` parcial só falha no JaCoCo, por rodar poucos testes)
- T2 — arquivos: `docs/openapi.yaml` (`UnitSettingsView` e `UnitUpdateSettings`); `UnitSettingsRouteIT` e `OrganizationsIT` não precisaram mudar (usam `UnitBase.payload()`, que já leva os campos) — prova: `verify -Dit.test='UnitSettingsRouteIT,OrganizationsIT'` → 8 verdes; `test -Dtest='OpenApiContractTest,ContractRoutesTest'` → 7 verdes
- T3 — arquivos: `useUnitSettings.ts`, `mocks/handlers.ts` (padrão e validação do `PUT`), `mocks/unit-settings.msw.test.ts` (casos 24, -1, ausente), `EnrollmentWizardPage.test.tsx` (fixture), `i18n/pt-BR.json` — prova: `npx vitest run src/features/units/UnitSettingsPage.test.tsx src/mocks/unit-settings.msw.test.ts` e `npm run typecheck` → verdes
- T4 — arquivos: `UnitSettingsPage.tsx` (cartão, validação, diferenças do conflito, foco), `UnitSettingsPage.test.tsx` (5 testes novos, axe nos dois temas a 360 px) — prova: `npx vitest run src/features/units/UnitSettingsPage.test.tsx` → 28 verdes
Regressão: `./backend/mvnw -f backend/pom.xml verify` → 689 testes, 0 falhas; BUILD FAILURE só pelo JaCoCo: pacote `billing` com 0,99 de linhas (exige 1,00), uma linha não coberta em `ChargeNoticeJob` (#78), arquivo que este item não tocou. Frontend: `npm run lint`, `typecheck`, `build`, `test:cov` (linhas 96,94%) e `npm run e2e` (35 verdes) → ok.
Desvios: (1) `SELECT` leva `reminders_enabled, reminders_hour` depois de `u.version` (colunas 34 e 35), em vez de no meio, para não reindexar `detail(...)`. (2) Com Salvar desabilitado o Enter não envia o formulário; o foco no campo com erro (CA8) vem de um `onKeyDown` do Enter no formulário, mantendo o botão desabilitado como no resto da página. (3) Chave extra `remindersEnabledField` ("Lembretes automáticos") para o diálogo de conflito, distinta do rótulo do interruptor.
Não é do item: falha de cobertura de `ChargeNoticeJob` em `billing` (acima), anterior a este item.

## Verificação

### Rodada 1 — OK — 2026-10-24, estado 1bff1c7641100aba21d1ac0d3089fbff76371981

| Critério | Status | Evidência |
|---|---|---|
| CA1, CA2, CA3, CA4, CA6 | PASSOU | `UnitSettingsIT.reminders` e `UnitUpdateValidationTest.reminderSwitch` no `verify` completo |
| CA5 | PASSOU | `UnitSettingsRouteIT` (403/404) verde no `verify` completo, com o corpo novo de `UnitBase.payload()` |
| CA7, CA8, CA9, CA10, CA11, CA12 | PASSOU | `UnitSettingsPage.test.tsx` (inclui axe nos dois temas a 360 px) verde; diff lido: cartão, validação, diferenças do conflito e foco batem |
| CA13 | PASSOU | `unit-settings.msw.test.ts` verde; `OpenApiContractTest` e `ContractRoutesTest` dentro do `verify` |

Comandos: `./backend/mvnw -f backend/pom.xml verify` → 689 testes, 0 falhas, BUILD SUCCESS (JaCoCo passou nesta rodada); frontend: `npm run lint`, `typecheck`, `build` ok; `test:cov` 1107 testes verdes (linhas 96,94%); `npm run e2e` 35 verdes; `vitest` de units, msw e enrollment-wizard 106 verdes.
Achados:
- sugestão, código, `UnitSettingsPage.tsx` (`onKeyDown` do Enter) — o foco no campo com erro só ocorre com Enter; clique no botão desabilitado não move o foco. Aceitável pelo desvio 2 registrado.
- sugestão, código, `UnitSettingsPage.tsx` — a validação de hora aceita "09"; harmless, o servidor recebe `Number`.
Instáveis: nenhum. Falha de JaCoCo em `billing` da construção não reapareceu (oscilação conhecida, #353).

## Entrega

- O que muda: `GET` e `PUT /units/{id}` expõem e gravam `remindersEnabled` e `remindersHour` (0 a 23; obrigatórios no `PUT`, que é substituição completa). A auditoria `unit.update` e o `If-Match` cobrem os dois campos. O cartão "Lembretes" de Configurações › Unidade ganha interruptor, hora, dias, texto do padrão, aviso fixo de bloqueio e as diferenças no conflito de edição. `docs/openapi.yaml` e os handlers MSW batem com o servidor.
- Verificação: rodada 1 OK (estado `1bff1c76`). Back-end: `verify` com 689 testes verdes e JaCoCo passando. Front-end: lint, typecheck, build, `test:cov` (linhas 96,94%) e `e2e` (35) verdes; axe nos dois temas a 360 px.
- Decisões que mudam comportamento: D1 (a seção continua em Unidade, sem rota própria), D2 (o canal é só o e-mail, mostrado sem interruptor), D6 (os dois campos são obrigatórios no `PUT`). Desvios: colunas novas no fim do `SELECT`; com Salvar desabilitado, o foco no campo com erro só vem pelo Enter.
- Limitações: o canvas `LembretesAuto.dc.html` não foi aberto (D4), então o layout fica para o dono conferir na revisão da sprint; sem ponta a ponta (D5). A falha de cobertura de `billing` (#352) é anterior e não é deste item.
