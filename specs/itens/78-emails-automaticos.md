# 78 — E-mails automáticos e preferências de envio

Origem: issue #78 (Sprint 09); `docs/especificacao/13-comunicacao-e-notificacoes.md` (RN-COM-11, RN-COM-13, RN-COM-14), `08-alunos-e-responsaveis.md` (RN-ALU-10) e `07-planos-e-beneficios.md` (RN-PLA-04, RN-PLA-05). Branch: `item/78-emails-automaticos`.

## 1. Objetivo

Dar ao servidor o registro dos e-mails automáticos ao aluno e ao responsável, a regra de quem pode desligar o quê e o tratamento do e-mail que volta. Em cima disso entram os e-mails que a Sprint 08 deixou para esta issue: "Cobrança gerada", aviso de reajuste de plano, "Diferença de reajuste" e o "Pagamento confirmado" (que passa para o registro sem mudar o contrato). Serve às telas e aos envios das outras issues da Sprint 09 (#79, #82) e ao portal (Sprint 10).

Fora do item: telas (o canvas "Comunicado da academia" é o texto do modelo; a tela é da #82); a escolha de remetente por unidade (RN-COM-10); modelos editáveis (RN-COM-12, #83); lembretes (#79 e #80); comunicados, agendamento e leitura (#81, #82); os demais e-mails da RN-COM-11 (bloqueio, reativação, encerramento, termo pendente, aulas, graduação, pedidos, débito automático, recusa de pagamento ou de comprovante): cada um nasce com o item que cria o fato que o dispara; a tela do portal onde o aluno liga ou desliga os comunicados (Sprint 10); a devolução real (bounce) vinda do provedor (Sprint 22).

## 2. Requisitos

- R1 (RN-COM-11): `EmailTemplate` passa a ser o registro: cada modelo declara se é de envio obrigatório (`ALWAYS`) ou desligável (`ANNOUNCEMENT`), e entram os modelos `CHARGE_CREATED`, `PLAN_RATE_ADJUSTED`, `READJUSTMENT_DIFF` e `ANNOUNCEMENT` (D1, D2).
- R2 (RN-COM-11, D1 da #74): toda cobrança em aberto é avisada por e-mail ao pagador (o responsável financeiro do menor; o próprio aluno adulto), uma vez, quando falta no máximo 10 dias para o vencimento ou na criação, o que vier depois; a "Diferença de reajuste" usa o modelo `READJUSTMENT_DIFF` (D3, D4).
- R3 (RN-PLA-04, D9 da #71): ao mudar o valor de um plano, cada aluno com modalidade ativa no plano recebe, pelo pagador, o aviso com o valor novo e a partir de quando vale (a próxima cobrança gerada); sem mudança de valor, nada é enviado.
- R4 (RN-COM-13): o aluno ou responsável desliga e liga só os comunicados da academia, por organização (vínculo); a preferência começa ligada; qualquer outro modelo é enviado sempre, mesmo com os comunicados desligados.
- R5 (RN-COM-13, D5): a preferência é lida e alterada por `GET` e `PUT /api/v1/auth/email-preferences`, no vínculo ativo da sessão; só vínculo de portal (aluno ou responsável) usa; a alteração é auditada.
- R6 (RN-COM-14): mensagem com falha é tentada de novo (já existe, #19) e o que vale para os modelos novos continua valendo; mensagem devolvida (bounce) fica `BOUNCED`, nunca é reenviada e marca o endereço "Com problema" na organização (RN-ALU-10) (D6).
- R7 (RN-ALU-10, D6): endereço marcado "Com problema" não recebe mais e-mail automático da organização: a mensagem é registrada como `SKIPPED`, sem envio.
- R8 (D7): o "Pagamento confirmado" passa a usar o registro (grava a organização e segue R7) sem mudar o que `Payments` devolve (`SENT`, `NO_ADDRESS` ou `FAILED`).
- R9 (RNF-11): log, métrica e auditoria nunca trazem destinatário nem texto dos novos envios.
- R10 (regra do projeto): a rota nova está no OpenAPI, tem handler MSW, e teste de isolamento entre organizações e entre perfis.

## 3. Critérios de aceite

- CA1 (R1): cada valor de `EmailTemplate` tem assunto e texto em `messages.properties` e uma categoria; só `ANNOUNCEMENT` é desligável; os modelos já existentes seguem com a mesma chave e o mesmo comportamento.
- CA2 (R2): dado aluno adulto com cobrança em aberto vencendo em 5 dias, quando a tarefa de avisos roda, então uma mensagem `CHARGE_CREATED` vai ao e-mail do aluno com valor e vencimento, e `charge.notified_at` fica preenchido; rodar de novo não envia outra.
- CA3 (R2): dado aluno menor com responsável financeiro, então o aviso vai ao e-mail do responsável e não ao do aluno.
- CA4 (R2): dada cobrança em aberto vencendo daqui a 30 dias (adiantada), então nada é enviado hoje e ela é avisada quando faltarem 10 dias.
- CA5 (R2): dada cobrança cancelada ou paga antes da tarefa, então nada é enviado; cobrança anterior à migração não é avisada.
- CA6 (R2): dada "Diferença de reajuste" criada, então o aviso usa o modelo `READJUSTMENT_DIFF` com a descrição e o vencimento (7 dias).
- CA7 (R3): dado plano com 3 alunos ativos (um menor), quando o valor muda de R$ 150,00 para R$ 170,00, então saem 3 mensagens `PLAN_RATE_ADJUSTED` aos pagadores certos com o valor novo; mudar só o nome do plano não envia nada; a operação que falha (transação desfeita) não envia nada.
- CA8 (R4, R5): dado vínculo de aluno, quando `GET /auth/email-preferences`, então `announcements` é verdadeiro; `PUT` com falso persiste e grava uma linha de auditoria sem dado pessoal.
- CA9 (R4): dado vínculo com comunicados desligados, quando se envia `ANNOUNCEMENT` ao e-mail dele nessa organização, então a mensagem é `SKIPPED` (motivo `OPT_OUT`) e não chega à `Outbox`; `CHARGE_CREATED`, `PAYMENT_CONFIRMED` e `PASSWORD_CHANGED` para o mesmo endereço seguem chegando.
- CA10 (R4): o mesmo e-mail com vínculo em outra organização que não desligou continua recebendo comunicados dessa outra organização.
- CA11 (R5, R10): perfil de gestão (Coordenador, Administrador, Professor) e pedido sem sessão recebem 403 e 401; a conta que desliga na organização A não altera o vínculo da B (teste de isolamento); valor que não seja booleano responde 422.
- CA12 (R6): dada mensagem `SENT` a um aluno, quando o bounce é registrado, então ela fica `BOUNCED`, o aluno aparece com `email_problem_at` e há auditoria `student.email_problem_marked`; registrar de novo não duplica.
- CA13 (R6): falha de envio (exceção do `EmailSender`) continua só incrementando tentativas e nunca marca o endereço como problema.
- CA14 (R7): dado endereço "Com problema", quando qualquer modelo da organização é enviado a ele, então a mensagem é `SKIPPED` (motivo `EMAIL_PROBLEM`), sem `Outbox`; depois de `EmailProblems.clear`, volta a receber.
- CA15 (R8): `Payments` continua devolvendo `SENT` com envio normal, `FAILED` com falha de envio e `NO_ADDRESS` para endereço ausente ou "Com problema"; os testes de #75 passam sem mudar o que afirmam.
- CA16 (R9): nos testes de CA2, CA7 e CA12 os logs capturados não contêm endereço nem texto.
- CA17 (R10): `OpenApiContractTest` e `ContractRoutesTest` passam com a rota nova; o teste MSW da rota passa.

## 4. Plano técnico

- **Dados.** Migração `V42__email_registry.sql` (a criar; a última é `V41`): em `email_message`, coluna `organization_id uuid references organization(id)` anulável (e-mails de conta continuam sem organização), `template_check` ganha `CHARGE_CREATED`, `PLAN_RATE_ADJUSTED`, `READJUSTMENT_DIFF`, `ANNOUNCEMENT`, e `status` ganha `SKIPPED` e `BOUNCED` (o motivo de `SKIPPED` vai em `last_error`: `OPT_OUT` ou `EMAIL_PROBLEM`); em `account_link`, `announcement_email boolean not null default true`; em `charge`, `notified_at timestamptz` (adicionar com `default now()` e `not null`, depois remover o default e o `not null`, para as cobranças que já existem nascerem como avisadas, sem `update` sob RLS) e índice parcial `where notified_at is null`. Reversão manual comentada no topo, como a V41. `email_message` continua sem RLS: a tarefa de reenvio é de sistema, lê todas as organizações, e nenhuma rota lê a tabela (D8). `infra/seed/`: conferir se algum `NN-*.sql` insere cobrança e, se inserir, preencher `notified_at`.
- **Registro e regras de envio (`br.com.ctcore.mail`).** `EmailTemplate` (existe) ganha o campo `Kind { ALWAYS, ANNOUNCEMENT }` e os quatro modelos novos. `EmailService` (existe) ganha `send(UUID organizationId, String to, EmailTemplate, Object... args)`: grava `organization_id` e, antes de enviar, decide `SKIPPED` por `OPT_OUT` (modelo `ANNOUNCEMENT` e `account_link.announcement_email = false` da conta de mesmo e-mail na organização) ou por `EMAIL_PROBLEM` (R7); consulta ao banco no contexto da organização com `OrganizationTransactions.provision` quando não houver contexto. `send(to, ...)` sem organização continua como está (e-mails de conta). `EmailRetryJob` (existe) passa a apagar também `SKIPPED` e `BOUNCED` antigos, e não toca nesses status. `messages.properties` (existe): `mail.charge-created.*`, `mail.plan-rate-adjusted.*`, `mail.readjustment-diff.*`, `mail.announcement.*`.
- **Destinatário (`br.com.ctcore.student`).** `PayerEmail` (a criar): `forStudent(UUID org, UUID studentId)` devolve o e-mail do responsável financeiro do menor ou o do aluno adulto, ou vazio; segue o critério de pagador de `Students.PAYER` (existe, privado). `EmailBounces` (a criar): `register(UUID messageId)` marca a mensagem `BOUNCED` e chama `EmailProblems.mark(organizationId, email)` (existe, já audita); o provedor real chama isto na Sprint 22.
- **Cobrança gerada (`br.com.ctcore.billing`).** `ChargeNoticeJob` (a criar), no molde de `ChargeGenerationJob` (existe): `@Scheduled` a cada 15 minutos com `@SchedulerLock(name = "billing-charge-notices")`, percorre `active_organization_ids()` e as unidades, e em `OrganizationTransactions.provision` seleciona `charge` com `notified_at is null and status = 'OPEN' and due_on <= hoje da unidade + 10 dias`, envia pelo `EmailService` (`READJUSTMENT_DIFF` para o tipo de mesmo nome, `CHARGE_CREATED` para os demais; pagador pelo `payer_guardian_id` da cobrança ou o aluno) e grava `notified_at`; endereço ausente ou "Com problema" também grava `notified_at`, sem mensagem. Um erro em uma cobrança vai ao log sem dado pessoal e não impede as outras. Sem auditoria: é efeito de sistema, não ação sobre dado de negócio (D9).
- **Reajuste (`br.com.ctcore.plan`).** `PlanWrite.update` (existe, linhas ~135 a 170): dentro da transação já existente coleta, quando o valor mudou, os e-mails dos pagadores (`PayerEmail`) dos alunos com `enrollment_modality` ativa no plano e, depois do commit, envia `PLAN_RATE_ADJUSTED` com nome do plano e valor novo. A cobrança da diferença não envia nada aqui: é avisada pelo `ChargeNoticeJob`.
- **Recibo (`br.com.ctcore.payment`).** `ReceiptMailer.send` (existe) recebe a organização, usa a sobrecarga nova do `EmailService` e traduz `SKIPPED` por `EMAIL_PROBLEM` em `NO_ADDRESS`; `Payments` (existe, linha ~102) passa `who.organizationId()`. O contrato devolvido não muda (D7).
- **Preferência (`br.com.ctcore.identity`).** `EmailPreferences` (a criar): lê e grava `account_link.announcement_email` do vínculo ativo da sessão com `AccountTransactions.forAccount` (existe), recusa vínculo de gestão (403) e grava auditoria `account.email_preferences_changed` (`AccountAudit`, existe, ou `ScopedAudit`); `AuthController` (existe, `@RequestMapping("/api/v1/auth")`) ganha `GET` e `PUT /email-preferences` com corpo `{ "announcements": boolean }`.
- **API/OpenAPI.** `docs/openapi.yaml`: `/auth/email-preferences` (GET, PUT), respostas 200, 401, 403, 422. As demais mudanças não têm rota.
- **Interface.** Não muda (tela do portal na Sprint 10). `frontend/src/mocks/handlers.ts` (existe) ganha o handler da rota nova, com teste `email-preferences.msw.test.ts` (a criar) no molde de `account.msw.test.ts`.
- **Segurança.** Permissão no servidor: só vínculo de portal altera a própria preferência; o e-mail do destinatário nunca vai a log, métrica ou auditoria (RNF-11). Risco: a consulta de problema e de preferência precisa de contexto da organização por causa do RLS; sem ele, a consulta devolve vazio e a mensagem seria enviada. O teste de CA9 e CA14 cobre.
- **Reutiliza.** `EmailService`, `EmailRetryJob`, `EmailProblems`, `OrganizationTransactions`, `AccountTransactions`, `ChargeGenerationJob` (molde), `ShedLock`, `Outbox`, testes `EmailServiceIT`, `BillingFixtures`.
- **Riscos.** (1) Mensagens de cobrança duplicadas se dois nós rodarem a tarefa: a trava `SchedulerLock` e `notified_at` gravado na mesma transação do envio cobrem. (2) Envio antes do commit: o `ChargeNoticeJob` grava `notified_at` e a linha de e-mail na mesma transação, então ou os dois ou nenhum.
- **Doc.** `backend/README.md`: parágrafo curto em "E-mail" com categorias, `SKIPPED`, `BOUNCED` e o gancho de bounce. Notas de versão: não existem ainda (RF-AJU-04, Sprint 21); nada a atualizar.

## 5. Tarefas

Item dividido em duas partes, cada uma construível em uma sessão: **Parte A** (T1 a T4: registro, preferência, bounce) e **Parte B** (T5 a T7: os envios novos).

- [x] T1: migração `V42__email_registry.sql` e ajuste de `SeedDataIT` e seeds, se precisarem — preparação, dá o lugar de gravar R1 a R7 — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test=MigrationIT+SeedDataIT`
- [x] T2: `EmailTemplate` com categoria e modelos novos, `EmailService.send` com organização, `OPT_OUT` e `EMAIL_PROBLEM`, textos, limpeza no `EmailRetryJob` — cobre CA1, CA9, CA10, CA14, CA16 — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test=EmailRegistryIT` (a criar) e `EmailServiceIT` sem edição
- [x] T3: `EmailBounces.register` e `PayerEmail` — cobre CA12, CA13 — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test=EmailBouncesIT` (a criar)
- [x] T4: `EmailPreferences`, rotas em `AuthController`, `docs/openapi.yaml` e handler MSW com teste — cobre CA8, CA11, CA17 — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test=EmailPreferencesIT+EmailPreferencesIsolationIT` (a criar), `./backend/mvnw -f backend/pom.xml test -Dtest='OpenApiContractTest,ContractRoutesTest'` e, em `frontend/`, `npx vitest run src/mocks/email-preferences.msw.test.ts`
- [x] T5: `ChargeNoticeJob` (Cobrança gerada e Diferença de reajuste) — cobre CA2, CA3, CA4, CA5, CA6, CA16 — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test=ChargeNoticeIT` (a criar)
- [x] T6: aviso de reajuste em `PlanWrite.update` — cobre CA7, CA16 — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test=PlanRateNoticeIT` (a criar) e `RepricingIT` sem edição
- [x] T7: `ReceiptMailer` e `Payments` no registro, `backend/README.md` e regressão — cobre CA15 — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test='Payment*IT'`; ao fim `./backend/mvnw -f backend/pom.xml verify` com JaCoCo verde

## 6. Decisões

- D1: o registro é o próprio enum `EmailTemplate`, com a categoria em cada modelo, e não uma tabela. Motivo: a lista de modelos e a regra "sempre chega" são regra do produto (RN-COM-13), mudam com código e teste, e nenhuma organização as edita (RN-COM-12 é outra issue). Alternativa: tabela de modelos por organização, descartada por antecipar a #83.
- D2: entram agora só os modelos que o card pede (cobrança gerada, reajuste, diferença, e o comunicado como modelo); os demais da RN-COM-11 entram com o item que cria o gatilho, para não haver modelo sem uso nem teste. O modelo `ANNOUNCEMENT` existe porque o canvas "Comunicado da academia" é desta issue e a regra de desligar (RN-COM-13) não se prova sem um modelo desligável; texto rico, imagem e fixação são da #82. Alternativa: cadastrar todos os modelos agora, descartada.
- D3 (muda o comportamento): "Cobrança gerada" sai quando faltam até 10 dias para o vencimento, a mesma janela da geração diária (RN-COB-02), e não no instante em que a linha é criada. Motivo: a cobrança adiantada de 12 meses (#76) não deve mandar 12 e-mails de uma vez, e cada cobrança é avisada uma só vez. Alternativa: avisar sempre na criação.
- D4 (muda o comportamento): o pagador é quem recebe (responsável financeiro do menor, aluno adulto), igual à cobrança e ao recibo (RN-RSP-02); o outro responsável não recebe a cobrança. A cobrança de valor zero não existe (a geração a pula), então não há aviso para ela. Sem link de pagamento no e-mail por enquanto: o portal é da Sprint 10 e o pagamento online da 11; o texto mostra valor, descrição e vencimento. Alternativa: link para o portal, descartada por não existir destino.
- D5: a preferência fica no vínculo (`account_link`), não no aluno: é por academia, vale para o responsável com vários alunos na mesma organização, e a rota usa o vínculo ativo da sessão, sem identificador na URL. Rota em `/auth/...`, junto das de conta e sessão, porque o portal ainda não tem prefixo próprio. Alternativa: coluna em `student`, descartada porque o responsável não é o aluno.
- D6 (muda o comportamento): bounce marca o endereço na organização da mensagem e a gestão passa a vê-lo "Com problema" (a notificação ao sino é da #84). Falha de envio (exceção, `NOT_SENT`) não marca nada: só a devolução. O registro do bounce é um método de serviço (`EmailBounces.register`) provado por teste; ele não tem rota, o provedor real o chamará na Sprint 22.
- D7 (muda o comportamento): o recibo para endereço "Com problema" deixa de ser enviado e o pagamento devolve `NO_ADDRESS`. Motivo: RN-ALU-10 manda parar os envios, e a D6 da #75 deixou esta decisão para a #78; RN-COM-13 trata da escolha do aluno e continua valendo (recibo nunca é desligável). O recibo segue disponível no link e no portal. Alternativa: continuar enviando ao endereço com problema.
- D8: `email_message` ganha `organization_id` sem RLS, porque `EmailRetryJob` atravessa organizações e nada a lê por rota; o isolamento fica na consulta que decide `SKIPPED` e em `EmailProblems.mark`, ambas no contexto da organização. Alternativa: RLS com exceção para o papel do job, descartada por complexidade sem rota que a justifique.
- D9: os envios automáticos não geram linha de auditoria própria (o histórico é a `email_message` e o `notified_at`), como a D6 da #19; a mudança de preferência e a marcação de "Com problema" são auditadas.
- D10: o recurso não nasce desligado por organização: os avisos são regra existente do produto (RN-PLA-04, RN-COM-11) e a sprint não o descreve como liberado aos poucos. Suposição: se o dono quiser liberar por organização, o `ChargeNoticeJob` passa a filtrar por um recurso em `feature_flag`.
- Suposição: o limite de 10 dias da D3 e os 15 minutos da tarefa não são regra da especificação; trocar um deles muda só a constante do `ChargeNoticeJob`.

## Construção

Parte A (T1 a T4) e Parte B (T5 a T7) feitas.

- T1 — arquivos: `backend/src/main/resources/db/migration/V42__email_registry.sql`, `infra/seed/18-cobrancas.sql` (`notified_at = now()`) — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test='MigrationIT,SeedDataIT'` → 22 testes, 0 falhas
- T2 — arquivos: `mail/EmailTemplate.java`, `mail/EmailService.java`, `mail/EmailRetryJob.java`, `messages.properties`, `EmailRegistryIT` (novo) — prova: `verify -Dit.test='EmailRegistryIT,EmailServiceIT'` → 14 testes, 0 falhas (`EmailServiceIT` sem edição)
- T3 — arquivos: `student/EmailBounces.java`, `student/PayerEmail.java`, `EmailBouncesIT` (novo) — prova: `verify -Dit.test=EmailBouncesIT` → 3 testes, 0 falhas
- T4 — arquivos: `identity/EmailPreferences.java`, `identity/AuthController.java`, `docs/openapi.yaml`, `frontend/src/mocks/handlers.ts`, `EmailPreferencesIT`, `EmailPreferencesIsolationIT`, `frontend/src/mocks/email-preferences.msw.test.ts` — prova: `verify -Dit.test='EmailPreferencesIT,EmailPreferencesIsolationIT' -Dtest='OpenApiContractTest,ContractRoutesTest'` → 4 + 7 testes, 0 falhas; `npx vitest run src/mocks/email-preferences.msw.test.ts` → 2 testes, 0 falhas
Regressão (Parte A): `./backend/mvnw -f backend/pom.xml verify` → 651 testes, 0 falhas, JaCoCo "All coverage checks have been met"; em `frontend/`: `npm run lint && npm run typecheck && npm run test:cov` → verdes (linhas 96,9%). `build` e `e2e` do front não rodados nesta parte (sem mudança de tela). Linha de base não piorou.
Desvios: (1) `EmailService` ganhou um segundo construtor (o de 7 argumentos permanece, para `EmailServiceIT` seguir sem edição; o de 8 recebe `OrganizationTransactions`). (2) Valor não booleano em `PUT /auth/email-preferences` responde 422 por checagem manual (`Object announcements`), porque a validação padrão do projeto responderia 400. (3) A auditoria da preferência usa `ScopedAudit` com `before`/`after` `{announcements}`. (4) Nesta sessão não se mexeu em `backend/README.md` nem em `AGENTS.md` (T7).
Não é do item: nada.

### Parte B
- T5 — arquivos: `billing/ChargeNoticeJob.java`, `billing/ChargeNoticeIT` (novo) — prova: `verify -Dit.test=ChargeNoticeIT` → 7 testes, 0 falhas (CA2 a CA6, CA16 e erro isolado por cobrança)
- T6 — arquivos: `plan/PlanWrite.java` (construtor ganha `PayerEmail` e `EmailService`; aviso depois do commit), `plan/PlanRateNoticeIT` (novo) — prova: `verify -Dit.test='PlanRateNoticeIT,RepricingIT,PlanWriteIT'` → 1 + 4 + 10 testes, 0 falhas (`RepricingIT` e `PlanWriteIT` sem edição)
- T7 — arquivos: `payment/ReceiptMailer.java`, `payment/Payments.java`, `ReceiptEmailIT` (ajuste de assinatura nos mocks e teste novo `emailProblem`), `backend/README.md` (seção E-mail) — prova: `verify -Dit.test='Payment*IT,ReceiptEmailIT'` → ReceiptEmailIT 4, PaymentIT 9, PaymentIsolationIT 2, PaymentReceiptIT 6, 0 falhas
Regressão (Parte B): `./backend/mvnw -f backend/pom.xml verify` → 660 testes, 0 falhas, JaCoCo "All coverage checks have been met" (100% em `billing`). Front-end não mudou nesta parte; o resultado da Parte A vale. Linha de base não piorou.
Desvios (Parte B): (5) `ReceiptEmailIT` teve de mudar a assinatura nos três `verify`/`doThrow` (`send` ganhou a organização no primeiro argumento); o que ele afirma não mudou. (6) `ChargeNoticeJob` usa `fixedDelay` de 15 min (não `cron`) e reivindica a cobrança com `update ... where notified_at is null` antes de enviar, o que dá a mesma garantia de uma vez só sem `for update`. (7) A descrição sem texto próprio (mensalidade) sai como "Mensalidade MM/yyyy", fixa no código por ser argumento do e-mail e não texto de tela. (8) Sem `AGENTS.md` a atualizar: nenhum comando ou estrutura nova.
Não é do item: nada.

## Verificação

### Rodada 1 — OK — 2026-10-23, estado 3122056881b067a9c53d62a65f92bad818841f7f

| Critério | Status | Evidência |
|---|---|---|
| CA1 | PASSOU | `EmailRegistryIT` (CA1); `EmailServiceIT` sem edição dentro do `verify` |
| CA2 | PASSOU | `ChargeNoticeIT` (CA2), `notified_at` e segunda execução sem envio |
| CA3 | PASSOU | `ChargeNoticeIT` (menor, e-mail do responsável); código usa `coalesce(ga.email, s.email)` com `payer_guardian_id` |
| CA4 | PASSOU | `ChargeNoticeIT` (adiantada 30 dias) |
| CA5 | PASSOU | `ChargeNoticeIT`; filtro `status = 'OPEN'`; migração marca as existentes com `now()` |
| CA6 | PASSOU | `ChargeNoticeIT` (READJUSTMENT_DIFF) |
| CA7 | PASSOU | `PlanRateNoticeIT` (3 mensagens, nome só, transação desfeita); `RepricingIT` e `PlanWriteIT` sem edição |
| CA8 | PASSOU | `EmailPreferencesIT` |
| CA9 | PASSOU | `EmailRegistryIT` (OPT_OUT, sem Outbox, demais modelos chegam) |
| CA10 | PASSOU | `EmailRegistryIT` (outra organização) |
| CA11 | PASSOU | `EmailPreferencesIsolationIT` (403, 401, isolamento, 422) |
| CA12 | PASSOU | `EmailBouncesIT` |
| CA13 | PASSOU | `EmailBouncesIT` |
| CA14 | PASSOU | `EmailRegistryIT` (EMAIL_PROBLEM e `clear`) |
| CA15 | PASSOU | `ReceiptEmailIT` (4), `PaymentIT`, `PaymentReceiptIT` no `verify`; mudou só a assinatura nos mocks, o que afirmam não mudou |
| CA16 | PASSOU | log capturado em `ChargeNoticeIT` e `EmailRegistryIT`; ver sugestão 1 |
| CA17 | PASSOU | `OpenApiContractTest`, `ContractRoutesTest` no `verify`; vitest `email-preferences.msw.test.ts` 2 testes |

Comandos:
- `./backend/mvnw -f backend/pom.xml verify` -> 660 testes (150 rápidos + ITs), 0 falhas, JaCoCo "All coverage checks have been met", BUILD SUCCESS (igual à linha de base da construção).
- `npx vitest run src/mocks/email-preferences.msw.test.ts` -> 2 passaram.
- `npm run lint` e `npm run typecheck` em `frontend/` -> sem erros.
- Não rodados: `build`, `test:cov`, `e2e` do front (só `handlers.ts` e um teste de mock mudaram; sem tela).

Instáveis: nenhum.

Achados:
- sugestão, código, `PlanRateNoticeIT` e `EmailBouncesIT` — CA16 cita captura de log também nos testes de CA7 e CA12; só `ChargeNoticeIT` e `EmailRegistryIT` capturam. O código novo desses dois caminhos só loga no erro; vale uma asserção de log.
- sugestão, código, `ChargeNoticeJob.runOnce` e `PlanWrite.notifyRate` — `LOG.error(..., e)` imprime a pilha e a mensagem da exceção, que numa violação de constraint do banco pode trazer o valor de `to_email`. Registrar só a classe da exceção mantém RNF-11.
- sugestão, código, `AuthController` — `PUT` com corpo ausente ou mal formado deve ser conferido; o OpenAPI promete 422 só para valor não booleano (comportamento fora do pedido do CA11, não conferido).

## Entrega

- **O que muda:** registro de e-mails por categoria (`EmailTemplate`, com `ANNOUNCEMENT` como único desligável); avisos automáticos de cobrança gerada (até 10 dias do vencimento, uma vez por cobrança, ao pagador), de diferença de reajuste e de reajuste de plano (ao pagador de cada aluno ativo); preferência de comunicados por vínculo (`GET`/`PUT /api/v1/auth/email-preferences`, auditada); registro de devolução (`EmailBounces.register`) que marca o endereço "Com problema" na organização; envio bloqueado para endereço com problema e para comunicado desligado; recibo para endereço com problema passa a devolver `NO_ADDRESS` (D7).
- **Como foi verificado:** rodada 1 da verificação, estado `3122056881b067a9c53d62a65f92bad818841f7f`: 17 critérios passaram; `./backend/mvnw -f backend/pom.xml verify` com 660 testes, 0 falhas e JaCoCo verde; `npx vitest run src/mocks/email-preferences.msw.test.ts` com 2 testes; lint e typecheck do front verdes.
- **Decisões que o revisor precisa saber:** D3 (aviso na janela de 10 dias, não na criação), D4 (pagador é quem recebe), D6 (bounce marca a organização), D7 (recibo para endereço com problema não sai), D10 (recurso não nasce desligado por organização).
- **Limitações:** o provedor real de bounce só chega na Sprint 22 (hoje o registro é método de serviço provado por teste, sem rota). Não há link de pagamento no e-mail de cobrança (portal da Sprint 10). Não rodados nesta entrega: `build`, `test:cov` e `e2e` do front (sem mudança de tela).
- **Achados abertos da verificação (sugestões, não bloqueiam):** (1) `PlanRateNoticeIT` e `EmailBouncesIT` não afirmam a captura de log que o CA16 cita; (2) `ChargeNoticeJob.runOnce` e `PlanWrite.notifyRate` registram a exceção inteira, que numa violação de constraint pode trazer `to_email` (RNF-11); convém trocar por só a classe da exceção; (3) corpo ausente ou mal formado em `PUT /auth/email-preferences` não foi conferido.
