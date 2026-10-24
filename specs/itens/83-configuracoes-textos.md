# 83 — RF-COM-03 · Configurações › Textos

Origem: issue #83 (Sprint 09); `docs/especificacao/13-comunicacao-e-notificacoes.md` (RF-COM-03, RN-COM-12; RN-COM-04, RN-COM-11 e RN-COM-13 como contexto), `01-organizacao-e-isolamento.md` (padrão "modelo da plataforma", só o Coordenador altera). Branch: `item/83-configuracoes-textos`. Segue o desenho de #70 (`Terms`, `TermsController`, `TermsPage`, `V38`). Parte do registro de e-mails da #78 (`EmailTemplate`, `EmailService`), do envio de lembretes da #79 (`ReminderSender`) e do compositor da #82 (`announcementTemplates.ts`). Os canvas `ConfigTextos.dc.html` e `EmailBase.dc.html` estão inacessíveis ao agente (D8).

## 1. Objetivo

O Coordenador edita, em Configurações › Textos, o assunto e o texto dos modelos que a organização pode mudar (RN-COM-12): os três lembretes de cobrança, as boas-vindas e o texto inicial de cada tipo de comunicado. Cada modelo tem campos automáticos, prévia com dados de exemplo e "Voltar ao texto da plataforma". O Administrador consulta. O texto salvo passa a valer nos lembretes enviados e no texto inicial do compositor de comunicados.

Fora do escopo: e-mails de segurança, de conta, recibo, cobrança gerada, reajuste e aviso de bloqueio (não editáveis, D1); o envio das boas-vindas ao aluno (nasce com o convite do portal, Sprint 10, D3); o link de pagamento real (D4); histórico de versões do texto (D5); modelo da plataforma editável pelo painel (RF-PNL-07); edição por unidade (D2).

## 2. Requisitos

- R1 (RN-COM-12, D1): existe um catálogo fixo de modelos editáveis: `REMINDER_BEFORE`, `REMINDER_DUE`, `REMINDER_OVERDUE`, `WELCOME` e `ANNOUNCEMENT_CANCELED_CLASS`, `ANNOUNCEMENT_TEACHER_CHANGE`, `ANNOUNCEMENT_SCHEDULE_CHANGE`, `ANNOUNCEMENT_NO_CLASS_DAY`, `ANNOUNCEMENT_GENERAL`. Cada um declara os campos automáticos que aceita. Nenhum outro e-mail é editável.
- R2 (RF-COM-03, D2): `GET /api/v1/organization/texts` devolve `canManage` e, por modelo, `key`, `group`, `fields`, `platform` (assunto e texto da plataforma) e `custom` (assunto e texto da organização, ou nulo), `updatedAt` e `updatedBy` do texto próprio.
- R3 (RF-COM-03, RN-COM-12): `PUT /api/v1/organization/texts/{key}` com `subject` e `body` grava o texto próprio do modelo (substitui o anterior) e responde 200 com o modelo. Chave fora do catálogo: 404.
- R4 (RN-COM-12): o servidor recusa (400, campo `subject` ou `body`) campo automático desconhecido ou que o modelo não aceita (ex.: `{valor}` em boas-vindas), chaves `{` `}` sem fecho, assunto vazio ou com mais de 150 caracteres, texto vazio ou com mais de 2.000 caracteres, e texto igual ao da plataforma (nesse caso a ação certa é voltar ao padrão). O texto de comunicado aceita o formato mínimo da RN-COM-05 (link só http/https).
- R5 (RF-COM-03): `DELETE /api/v1/organization/texts/{key}` apaga o texto próprio ("Voltar ao texto da plataforma") e responde 204; sem texto próprio responde 204 sem efeito.
- R6 (RN-COM-12, D4): `{nome}` (do destinatário), `{valor}`, `{vencimento}`, `{unidade}`, `{descricao}` (a cobrança) e `{link_pagamento}` valem para os lembretes; `{nome}` e `{unidade}` para as boas-vindas; `{unidade}` e `{turma}`, `{data}`, `{horario}`, `{periodo}`, `{motivo}` conforme o tipo, para os comunicados. `{link_pagamento}` sai vazio enquanto não houver pagamento online.
- R7 (RN-COM-12): o lembrete automático e o manual (`ReminderSender`) usam o texto próprio do modelo, quando há, com os campos preenchidos; sem texto próprio, o e-mail sai como hoje (texto da plataforma, mesmas palavras). O valor dos campos é escapado como o resto do e-mail. O aviso de bloqueio não muda.
- R8 (RN-COM-04, RN-COM-12): `GET /api/v1/announcements/templates` (quem tem `ANNOUNCEMENTS`) devolve, por tipo de comunicado, o título e o texto em uso (o próprio da organização ou o da plataforma); o compositor da #82 passa a usá-los em vez dos textos do `pt-BR.json`, preenchendo os campos que já conhece.
- R9 (RNF-11, auditoria): salvar e voltar ao padrão gravam `texts.updated` e `texts.reset` na auditoria (chave, assunto e texto antes e depois); nada de dado de pessoa.
- R10 (permissão, isolamento): ver exige `ORGANIZATION_SETTINGS`; alterar exige acesso completo (Coordenador); Administrador vê e não altera; Professor e portal recebem 403; uma organização nunca vê nem usa o texto da outra (código e RLS).
- R11 (RF-COM-03, D6): a tela Configurações › Textos (`/configuracoes/textos`) lista os modelos por grupo (Lembretes, Boas-vindas, Comunicados); cada um abre com editor (assunto e texto), botões dos campos automáticos que inserem o campo, prévia com dados de exemplo no modelo base do e-mail, "Salvar" e "Voltar ao texto da plataforma" (com confirmação), e a marca "Texto próprio" ou "Texto da plataforma".
- R12 (padrão de tela): a tela tem carregando, vazio, erro e sem permissão; para quem só vê, o editor fica desabilitado; funciona a partir de 360 px, passa no axe nos dois temas, e todo texto vem de `pt-BR.json`; item de menu "Textos" em Configurações, com `ORGANIZATION_SETTINGS`.
- R13 (RNF-32): `docs/openapi.yaml` e os handlers MSW refletem as três rotas de textos e a de modelos de comunicado, com as mesmas validações.

## 3. Critérios de aceite

- CA1 (R1, R2): dado Coordenador de organização sem texto próprio, quando `GET /organization/texts`, então 9 modelos, cada um com `platform` preenchido, `custom` nulo e a lista de campos do R6; e a lista não contém e-mail de segurança, de conta, recibo nem aviso de bloqueio.
- CA2 (R3): dado Coordenador, quando `PUT` em `REMINDER_DUE` com assunto e texto válidos, então 200 devolve o modelo com `custom`, `updatedBy` e `updatedAt`, e um novo `GET` o traz; um segundo `PUT` substitui o primeiro.
- CA3 (R3): dado chave `PASSWORD_RESET` ou inexistente, quando `PUT`, `DELETE` ou leitura da chave, então 404 e nada é gravado.
- CA4 (R4): dado texto com `{cpf}`, com `{valor}` em `WELCOME`, com `{nome` sem fecho, assunto vazio, assunto de 151 caracteres, texto vazio ou de 2.001 caracteres, quando `PUT`, então 400 `VALIDATION_ERROR` no campo certo e nada é gravado.
- CA5 (R4): dado texto idêntico ao da plataforma, quando `PUT`, então 400 no campo `body`; dado link `javascript:` em modelo de comunicado, então 400.
- CA6 (R5): dado texto próprio, quando `DELETE`, então 204 e o `GET` volta com `custom` nulo; `DELETE` repetido também responde 204.
- CA7 (R6, R7): dado texto próprio "Olá, {nome}: {descricao} de {valor} vence em {vencimento} ({unidade}). {link_pagamento}" em `REMINDER_BEFORE` e uma cobrança aberta de aluno adulto, quando o lembrete é enviado, então a mensagem na Caixa de saída traz o nome, a descrição, o valor em reais, a data e o nome da unidade e termina sem link; um nome com `<b>` sai escapado.
- CA8 (R7): dado organização sem texto próprio, quando o lembrete é enviado, então o assunto e o texto são os mesmos de antes do item (`ReminderSenderTest`, `ChargeReminderSendIT` e `ReminderJobIT` continuam verdes); e o aviso de bloqueio ignora qualquer texto próprio.
- CA9 (R7, R10): dado texto próprio na organização A, quando sai lembrete da organização B, então B usa o texto da plataforma.
- CA10 (R8): dado Coordenador com texto próprio de `ANNOUNCEMENT_GENERAL`, quando `GET /announcements/templates`, então o tipo `GENERAL` vem com o texto próprio e os demais com o da plataforma; Professor com `ANNOUNCEMENTS` também lê; portal recebe 403.
- CA11 (R10): dado Administrador, quando `GET`, então 200 com `canManage = false`; quando `PUT` ou `DELETE`, então 403. Professor e portal: 403 em `GET`, `PUT` e `DELETE`.
- CA12 (R10): dado organização B, quando `GET`, `PUT` ou `DELETE`, então só enxerga e mexe nos seus textos; consulta direta ao banco no contexto de A não retorna linha de B (RLS).
- CA13 (R9): dado `PUT` e `DELETE` válidos, então há `texts.updated` e `texts.reset` na auditoria com chave e antes/depois e sem e-mail nem dado de pessoa.
- CA14 (R11): dado Coordenador na tela, então os modelos aparecem em três grupos com a marca de origem; ao abrir um, a prévia mostra o texto com dados de exemplo e muda ao digitar; clicar num campo o insere no texto; "Salvar" envia o `PUT` e mostra confirmação.
- CA15 (R11): dado texto próprio, quando clicar em "Voltar ao texto da plataforma" e confirmar, então o `DELETE` é enviado e o editor mostra o texto da plataforma; cancelar a confirmação não envia nada.
- CA16 (R11): dado erro 400 do servidor, então a mensagem aparece junto do campo e o foco vai a ele.
- CA17 (R12): dado Administrador, então o editor fica desabilitado e sem "Salvar"; dado Professor, a tela mostra "sem permissão"; carregando, erro (com tentar de novo) e vazio (catálogo sem itens) aparecem; axe passa nos dois temas a 360 px.
- CA18 (R8, R11): dado texto próprio de `ANNOUNCEMENT_CANCELED_CLASS` com `{turma}`, quando abrir o compositor com esse tipo, então título e texto iniciais vêm do modelo próprio e `{turma}`, `{data}`, `{horario}` são preenchidos com o que a tela sabe (marcador entre colchetes para o que não sabe, como hoje).
- CA19 (R13): `OpenApiContractTest` e `ContractRoutesTest` passam; o MSW valida como o servidor (campo desconhecido, tamanhos, texto igual ao da plataforma).

## 4. Plano técnico

- Dados: migração `V46__text_model.sql` (a criar; a última é `V45`): tabela `text_model (id, organization_id, key, subject, body, updated_by_account_id, updated_at)`, `unique (organization_id, key)`, `key` com `check` do catálogo, `subject` de 1 a 150 e `body` de 1 a 2.000; RLS no padrão `organization_scope` da `V38` (`enable` e `force`). Diferente da `V38`, o papel operacional tem UPDATE e DELETE (o texto é regravado e apagado; o antes e depois fica na auditoria). Não há seed (sem texto próprio é o padrão); `infra/seed/` não muda.
- API: `GET /organization/texts`, `PUT /organization/texts/{key}`, `DELETE /organization/texts/{key}` e `GET /announcements/templates`. Erros 400, 401, 403, 404. O catálogo e os textos da plataforma ficam no servidor (D2); defaults em `messages.properties` sob `text.<CHAVE>.subject` e `.body`, lidos sem argumentos (o `MessageSource` não interpreta `{campo}` quando não há argumentos).
- Interface: página nova `TextsPage` em `/configuracoes/textos` (lista por grupo e editor, no desenho de `TermsPage`); compositor da #82 troca a fonte dos textos iniciais.
- Segurança: permissão no servidor em toda rota (R10); o texto vai a e-mail já escapado (`EmailService` escapa tudo) e o formato dos comunicados passa por `AnnouncementText.validate`; os valores dos campos (nome, descrição) nunca são tratados como modelo (substituição em uma passada, sem reavaliar o resultado); texto do e-mail não vai a log.
- Arquivos:
  - `backend/src/main/resources/db/migration/V46__text_model.sql` (a criar).
  - `backend/src/main/java/br/com/ctcore/mail/TextModel.java` (a criar): enum do catálogo (chave, grupo, campos aceitos).
  - `backend/src/main/java/br/com/ctcore/mail/TextModels.java` (a criar): serviço (lista, `save`, `reset`, `resolve(org, model)`, validação de campos e preenchimento em uma passada) com auditoria, no desenho de `terms/Terms`.
  - `backend/src/main/java/br/com/ctcore/mail/TextModelController.java` (a criar): as três rotas de `/organization/texts`.
  - `backend/src/main/java/br/com/ctcore/mail/EmailService.java`: novo `sendText(UUID org, String to, EmailTemplate template, String subject, String plainBody)`; o `store` passa a aceitar assunto e corpo prontos (o corpo vindo de modelo é escapado como os argumentos de hoje).
  - `backend/src/main/java/br/com/ctcore/billing/ReminderSender.java`: `send(...)` consulta `TextModels.resolve` para `REMINDER_*`, preenche os campos (nome do destinatário, unidade da cobrança, `{link_pagamento}` vazio) e usa `sendText`; sem texto próprio segue por `email.send` como hoje; `BLOCK_WARNING` não muda.
  - `backend/src/main/java/br/com/ctcore/communication/AnnouncementController.java` e `AnnouncementTargets.java` (ou classe pequena nova ao lado): `GET /announcements/templates` com a permissão `ANNOUNCEMENTS`.
  - `backend/src/main/resources/messages.properties`: textos da plataforma dos 9 modelos (os três lembretes com as mesmas palavras de `mail.reminder-*`, trocando `{0}`, `{1}`, `{2}` por `{descricao}`, `{valor}`, `{vencimento}`; os cinco comunicados copiados de `announcements.new.templates` no `pt-BR.json`, com `{turma}` etc.; boas-vindas nova).
  - `docs/openapi.yaml`: quatro rotas e esquemas `TextModel`, `TextModelUpdate`, `AnnouncementTemplates`.
  - `frontend/src/features/texts/TextsPage.tsx`, `useTexts.ts`, `TextsPage.test.tsx` (a criar); `frontend/src/App.tsx` (rota), `features/permissions/menuCatalog.ts` (item "texts" em `settings`), `i18n/pt-BR.json` (grupo `texts` e `menu.texts`; sai o grupo `announcements.new.templates`).
  - `frontend/src/features/announcements/announcementTemplates.ts`, `NewAnnouncementPage.tsx` e seus testes: a fonte dos textos passa a ser `GET /announcements/templates` (novo hook), trocando `{campo}` pelos valores.
  - `frontend/src/mocks/handlers.ts` (as quatro rotas, validação igual à do servidor) e `frontend/src/mocks/texts.msw.test.ts` (a criar).
  - Testes de back-end a criar em `backend/src/test/java/br/com/ctcore/mail/`: `TextModelsTest` (validação de campos e preenchimento), `TextModelsIT` (rotas, perfis, isolamento, RLS, auditoria); alterar `ReminderSenderTest` ou criar `ReminderCustomTextIT` (CA7 a CA9) e o teste de `GET /announcements/templates`.
- Reuso: `Terms` (permissão com `Permissions.grant`, `OrganizationTransactions.execute`, `AuditService.record`, tratamento de `Kind` desconhecido como 404), `AnnouncementText.validate`, `EmailLayout`/`EmailService`, `ReminderSender` e suas fixtures, `TermsPage`/`useTerms`/`terms.msw.test.ts` como molde da tela e do MSW, `OrganizationBase` para os `*IT`.
- Riscos: (1) mudar a fonte dos textos do compositor quebra os testes da #82 (`NewAnnouncementPage.test.tsx`, MSW de comunicados): ajustados na T7. (2) O texto da plataforma dos lembretes duplica as palavras de `mail.reminder-*`: o `ReminderSenderTest` e o `ReminderJobIT` apontam divergência (CA8). (3) A coluna da unidade na cobrança e o nome do destinatário no `SELECT` do `ReminderSender` precisam ser conferidos na construção. (4) `ConfigTextos.dc.html` e `EmailBase.dc.html` não foram abertos (D8).

## 5. Tarefas

Parte A — servidor (T1 a T4). Parte B — interface (T5 a T7). A construção pode ser feita em sessões separadas por parte.

- [x] T1: migração `V46__text_model.sql`, `TextModel`, `TextModels` (lista, `save`, `reset`, `resolve`, validação e preenchimento) e textos da plataforma em `messages.properties` — cobre CA1, CA4, CA5, CA7 (preenchimento) — prova: `./backend/mvnw -f backend/pom.xml test -Dtest='TextModelsTest'`
- [x] T2: `TextModelController` com as três rotas, permissão, isolamento, auditoria — cobre CA2, CA3, CA6, CA11, CA12, CA13 — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test='TextModelsIT' -Dtest=NoTest -Dsurefire.failIfNoSpecifiedTests=false`
- [x] T3: `EmailService.sendText` e `ReminderSender` com o texto próprio — cobre CA7, CA8, CA9 — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test='ReminderCustomTextIT,ChargeReminderSendIT,ReminderJobIT' -Dtest='ReminderSenderTest' -Dsurefire.failIfNoSpecifiedTests=false`
- [x] T4: `GET /announcements/templates` e `docs/openapi.yaml` das quatro rotas — cobre CA10, CA19 (servidor) — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test='AnnouncementTemplatesIT' -Dtest='OpenApiContractTest,ContractRoutesTest' -Dsurefire.failIfNoSpecifiedTests=false`
- [x] T5: front — `useTexts`, handlers MSW, `texts.msw.test.ts`, menu, rota e textos em `pt-BR.json` — cobre CA19 (MSW) — prova: em `frontend/`, `npx vitest run src/mocks/texts.msw.test.ts src/features/permissions/buildMenu.test.ts` e `npm run typecheck`
- [x] T6: front — `TextsPage` (grupos, editor, campos, prévia, salvar, voltar ao padrão, estados, permissão) e testes com axe — cobre CA14, CA15, CA16, CA17 — prova: em `frontend/`, `npx vitest run src/features/texts/TextsPage.test.tsx`
- [x] T7: front — compositor de comunicados usa `GET /announcements/templates` e saem os textos iniciais do `pt-BR.json` — cobre CA18 — prova: em `frontend/`, `npx vitest run src/features/announcements`; ao fim `./backend/mvnw -f backend/pom.xml verify` e, em `frontend/`, `npm run lint && npm run typecheck && npm run build && npm run test:cov && npm run e2e`

## 6. Decisões

- D1 (muda o comportamento): o que é editável é um catálogo fixo: lembretes (antes, no dia, depois), boas-vindas e o texto inicial dos cinco tipos de comunicado. Fora dele ficam os e-mails de conta e segurança (RN-COM-12), o recibo e os avisos de cobrança gerada, reajuste e diferença (são avisos do sistema, não "lembretes") e o aviso de bloqueio (fixo pela RN-COB-17 e pelo item #80). Alternativa: tornar todos os e-mails de pagamento editáveis, descartada por a RN-COM-12 listar só lembretes, comunicados e boas-vindas.
- D2 (muda o comportamento): o texto próprio é da organização, igual para todas as unidades (como os termos); o texto da plataforma é fixo no servidor, sem edição pelo painel (RF-PNL-07 é posterior). Alternativa: texto por unidade, descartada por não estar na RN-COM-12 nem no RF-COM-03.
- D3: as boas-vindas são editáveis e prévia-áveis agora, mas nenhum envio as usa até o convite do portal (Sprint 10); a lacuna é a mesma do D2 da #78 (modelo nasce com o gatilho) e foi aceita porque a RN-COM-12 e o card pedem a tela agora. Se o dono preferir esconder o modelo até haver envio, basta tirar `WELCOME` do catálogo (uma linha e o texto da plataforma).
- D4 (muda o comportamento): `{descricao}` entra como campo extra dos lembretes (sem ele o lembrete perderia a descrição, "Mensalidade 10/2026", que o e-mail de hoje mostra). `{link_pagamento}` é aceito e sai vazio enquanto não houver pagamento online (Sprint 11; D4 da #78), com aviso na tela. Alternativa para o link: recusar o campo até lá, descartada para a organização não precisar reescrever o texto depois.
- D5: sem histórico de versões nem exigência de aceite: o texto próprio é um só, regravado; o antes e depois fica na auditoria. Alternativa: versões imutáveis como nos termos, descartada porque nada cita uma versão do e-mail enviado.
- D6: a prévia roda no navegador, com dados de exemplo fictícios (aluno "Maria Souza", R$ 150,00, a unidade da organização) e dentro de um quadro que imita o modelo base do e-mail; não há rota de prévia. Alternativa: prévia pelo servidor, descartada por duplicar chamada a cada tecla.
- D7: comunicados: os textos iniciais dos cinco tipos saem do `pt-BR.json` e passam a vir do servidor (R8), para existir uma só fonte para "texto da plataforma" e "voltar ao padrão". O Professor só lê (rota de modelos com `ANNOUNCEMENTS`), nunca edita. Campos de comunicado extras (`{turma}` etc.) são os que o compositor já preenche.
- D8: os canvas `ConfigTextos.dc.html` e `EmailBase.dc.html` não puderam ser abertos; a tela segue `TermsPage` e a especificação, e o dono confere o layout na revisão da sprint (como D4 da #80).
- D9: sem ponta a ponta nem recurso desligado por organização (configuração de gestão sobre regra existente, como a D10 da #79); notas de versão ainda não existem (RF-AJU-04, Sprint 21).
- Suposição: a unidade usada em `{unidade}` nos lembretes é a da cobrança. Se a cobrança não guardar a unidade, usa-se a da matrícula do aluno; muda só a consulta do `ReminderSender`.

## Construção

- T1 — arquivos: `backend/src/main/resources/db/migration/V46__text_model.sql`, `mail/TextModel.java`, `mail/TextModels.java`, `messages.properties` (textos `text.<CHAVE>.*`), `communication/AnnouncementText.java` (classe e `validate` públicos, para a validação de link dos comunicados) — prova: `./backend/mvnw -f backend/pom.xml test -Dtest='TextModelsTest'` → 4 testes, 0 falhas.
- T2 — arquivos: `mail/TextModelController.java`, `mail/TextModelsIT.java` — prova: `verify -Dit.test='TextModelsIT' -Dtest=NoTest -Dsurefire.failIfNoSpecifiedTests=false` → 7 testes, 0 falhas (o JaCoCo do pacote `billing` reprova em rodadas parciais; passa na regressão completa).
- T3 — arquivos: `mail/EmailService.java` (`sendText`, `store` dividido em `insert`), `billing/ReminderSender.java` (texto próprio, nome do destinatário e unidade da cobrança no SELECT), `billing/ReminderBase.java` e `ReminderJobIT.java` (construtor novo), `billing/ReminderCustomTextIT.java` — prova: `verify -Dit.test='ReminderCustomTextIT,ChargeReminderSendIT,ReminderJobIT' -Dtest='ReminderSenderTest'` → 16 testes, 0 falhas.
- T4 — arquivos: `communication/AnnouncementController.java` (`GET /announcements/templates`), `docs/openapi.yaml` (4 rotas e esquemas `TextModel*`, `AnnouncementTemplates`), `communication/AnnouncementTemplatesIT.java` — prova: `verify -Dit.test='AnnouncementTemplatesIT' -Dtest='OpenApiContractTest,ContractRoutesTest'` → 8 testes, 0 falhas.
- T5 — arquivos: `frontend/src/features/texts/useTexts.ts`, `mocks/handlers.ts`, `mocks/texts.msw.test.ts`, `features/permissions/menuCatalog.ts`, `buildMenu.test.ts`, `App.tsx`, `i18n/pt-BR.json` — prova: `npx vitest run src/mocks/texts.msw.test.ts src/features/permissions/buildMenu.test.ts` → 9 testes; `npm run typecheck` → limpo.
- T6 — arquivos: `frontend/src/features/texts/TextsPage.tsx`, `TextsPage.test.tsx` — prova: `npx vitest run src/features/texts/TextsPage.test.tsx` → 10 testes (inclui axe a 360 px nos dois temas).
- T7 — arquivos: `features/announcements/announcementTemplates.ts`, `NewAnnouncementPage.tsx`, `NewAnnouncementPage.test.tsx`, `types.ts`, `useAnnouncements.ts`, `i18n/pt-BR.json` (sai `announcements.new.templates`; entra o marcador `[unidade]`) — prova: `npx vitest run src/features/announcements` → 23 testes, 0 falhas.

Regressão: `./backend/mvnw -f backend/pom.xml verify` → 739 testes, 0 falhas, BUILD SUCCESS (JaCoCo 80% e 100% dos pacotes críticos atendidos); em `frontend/`: `npm run lint && npm run typecheck && npm run build` → verdes; `npm run test:cov` → 134 arquivos, 1162 testes, 0 falhas, cobertura 93,46% de linhas; `npm run e2e` → 36 testes `@critical`, 0 falhas. Sem linha de base registrada para comparar; nada que já falhava foi encontrado.
Desvios: (1) o assunto dos modelos de comunicado vale até 120 caracteres (e não 150), porque vira o título do comunicado, limitado a 120 pela `V44`; os demais seguem 150 (400 acima disso, igual ao CA4). (2) `AnnouncementText` virou pública (só para `TextModels` reutilizar a validação de link). (3) O botão "Editar/Ver" de cada modelo usa `aria-label` com o nome do modelo (em vez de texto só para leitor de tela). (4) `GET /announcements/templates` devolve `{ templates: { TIPO: { title, body } } }`.
Falha anterior: nenhuma.
Não é do item: nenhum.

## Verificação

### Rodada 1 — OK — 2026-10-24, estado 1cfc665da4955f8b5cd468e61934304c4533e7ea

| Critério | Status | Evidência |
|---|---|---|
| CA1 | PASSOU | TextModelsIT.catalog; verify verde |
| CA2, CA6 | PASSOU | TextModelsIT.saveAndReset |
| CA3 | PASSOU | TextModelsIT.unknownKey |
| CA4, CA5 | PASSOU | TextModelsIT.validation + TextModelsTest |
| CA7 | PASSOU | ReminderCustomTextIT.customText (inclui nome com `<b>` escapado) |
| CA8 | PASSOU (parcial) | ReminderCustomTextIT.platformText, ReminderSenderTest, ChargeReminderSendIT, ReminderJobIT verdes; "aviso de bloqueio ignora texto próprio" só por construção (`TextModel.reminder` devolve nulo), sem teste dedicado |
| CA9 | PASSOU | ReminderCustomTextIT.platformText |
| CA10 | PASSOU | AnnouncementTemplatesIT.templates |
| CA11 | PASSOU | TextModelsIT.permissions |
| CA12 | PASSOU | TextModelsIT.isolation (RLS com ctcore_app) |
| CA13 | PASSOU | TextModelsIT.audit |
| CA14-CA17 | PASSOU | TextsPage.test.tsx (10 testes, axe 360 px nos dois temas); test:cov verde |
| CA18 | PASSOU | NewAnnouncementPage.test.tsx (23 testes em announcements) |
| CA19 | PASSOU | OpenApiContractTest, ContractRoutesTest dentro do verify; texts.msw.test.ts |

Comandos: ./backend/mvnw -f backend/pom.xml verify → 739 testes, 0 falhas, BUILD SUCCESS; frontend: npm run lint, typecheck, build → verdes; npm run test:cov → 134 arquivos, 1162 testes, 0 falhas, 93,45% linhas; npm run e2e → 36 passed.
Instáveis: nenhum.
Achados:
- sugestão, código, backend/src/test/java/br/com/ctcore/billing/ReminderCustomTextIT.java — acrescentar teste de que BLOCK_WARNING ignora texto próprio (parte final do CA8).
- sugestão, especificação, desvio 1 da Construção (assunto de comunicado até 120) está coerente com a V44 e a API; sem ação.

## Entrega

RF-COM-03: a Coordenação edita, em Configurações › Textos, o assunto e o texto dos modelos da organização (três lembretes de cobrança, boas-vindas e o texto inicial de cada tipo de comunicado), com campos automáticos, prévia e "Voltar ao texto da plataforma". O Administrador só consulta. O texto salvo passa a valer nos lembretes enviados e no compositor de comunicados. Back-end: `V46__text_model.sql`, `TextModel`, `TextModels`, `TextModelController`, `GET /announcements/templates`, `EmailService.sendText`. Front-end: `features/texts/` e a entrada no menu.

Verificação: Rodada 1 OK (estado 1cfc665). Regressão `verify` (739 testes) e `npm run lint/typecheck/build/test:cov/e2e` verdes.

Decisões: o assunto de comunicado vale até 120 caracteres (título limitado pela V44); `AnnouncementText` ficou pública para reutilizar a validação de link; o botão de cada modelo tem `aria-label` com o nome do modelo.

Limitações: a verificação registrou como sugestão um teste dedicado para "aviso de bloqueio ignora texto próprio" (hoje garantido por construção, `TextModel.reminder` devolve nulo); não bloqueia a entrega.
