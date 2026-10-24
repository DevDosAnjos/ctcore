# 84 — RF-NOT-01 · Painel do sino (notificações)

Origem: issue #84 (Sprint 09); `docs/especificacao/13-comunicacao-e-notificacoes.md` (RF-NOT-01, RN-NOT-01 a RN-NOT-04); encaixe herdado da Sprint 07 (benefício acima de 50%, RN-BEN-06, notifica o Coordenador). Branch: `item/84-painel-do-sino`. Parte do envio de e-mail da #78 (`EmailService`, `EmailTemplate`), do aviso dos 18 anos (`MajorityNoticeJob`), de `EmailProblems`, de `Payments.register` e de `BenefitWrite`. Os canvas `Notificacoes.dc.html` e `Topbar.dc.html` estão inacessíveis ao agente (D9).

## 1. Objetivo

A gestão passa a ter o sino na barra superior com o contador de não lidas e o painel de notificações: grupos Hoje e Esta semana, filtros Todas e Não lidas, a ação que resolve cada uma, "Marcar todas como lidas" e o estado "Tudo lido". O servidor guarda as notificações por pessoa (vínculo), decide quem recebe (RN-NOT-03), grava as que já têm fato no sistema (RN-NOT-04) e manda e-mail na hora só para as urgentes (RN-NOT-02).

Fora do escopo: o resumo diário por e-mail com o "Precisa de atenção" e a opção de desligá-lo (a lista "Precisa de atenção" ainda não existe, D3); os tipos de notificação cujo fato ainda não existe (pagamento recusado, comprovante, pedido do portal, aula experimental, autocadastro, turma sem professor, pedido de exclusão de dados): cada um entra no catálogo com o item que cria o fato (D2); notificação para o Professor (nenhum tipo atual é de turma, D4); notificação no portal do aluno; empurrar em tempo real (a tela consulta de tempos em tempos, D6).

## 2. Requisitos

- R1 (RN-NOT-04, D2): existe um catálogo fixo de tipos de notificação: `PAYMENT_RECEIVED`, `HIGH_BENEFIT`, `STUDENT_MAJORITY`, `EMAIL_PROBLEM`, `INVITE_PENDING` (têm fato hoje) e os urgentes `CLOSURE_REQUEST`, `MP_DISCONNECTED`, `SUBSCRIPTION_OVERDUE` (o fato nasce depois; o mecanismo já existe, R5).
- R2 (RN-NOT-03, D1): quem recebe é decidido no servidor: tipo de unidade vai aos Administradores com acesso àquela unidade; tipo da organização (`HIGH_BENEFIT`, `INVITE_PENDING` e os urgentes) vai aos Coordenadores; o Coordenador com "receber tudo" ligado recebe também os de unidade; o Professor não recebe nenhum dos tipos atuais; vínculo inativo e portal nunca recebem.
- R3 (RN-NOT-04): um pagamento registrado gera `PAYMENT_RECEIVED`; conceder benefício acima de 50% (`BenefitWrite.high`) gera `HIGH_BENEFIT`; o aluno que completa 18 anos (`MajorityNoticeJob`) gera `STUDENT_MAJORITY`; e-mail marcado "Com problema" (`EmailProblems.mark`) gera `EMAIL_PROBLEM`; convite de acesso sem aceite há 7 dias gera `INVITE_PENDING`, uma vez por convite.
- R4: a mesma notificação não é gravada duas vezes para a mesma pessoa (mesmo tipo e mesmo objeto de origem); falha ao gravar notificação nunca desfaz o fato que a gerou.
- R5 (RN-NOT-02): tipo urgente, além da notificação, manda e-mail na hora a quem a recebe; os demais tipos não mandam e-mail. O e-mail não leva dado de pessoa além do necessário (sem nome de aluno) nem vai a log.
- R6 (RN-NOT-01, RF-NOT-01): `GET /api/v1/notifications?filter=all|unread` devolve as notificações do vínculo ativo dos últimos 7 dias (hoje e os 6 dias anteriores, no fuso da unidade), do mais novo ao mais velho, cada uma com `id`, `type`, `group` (`TODAY` ou `WEEK`), `createdAt`, `readAt`, objeto de origem (`refType`, `refId`) e `params` (valores para o texto), e o total de não lidas (`unreadCount`, de qualquer data).
- R7 (RN-NOT-01): `GET /api/v1/notifications/unread-count` devolve só o contador, barato para a barra superior.
- R8 (RF-NOT-01): `POST /api/v1/notifications/read-all` marca como lidas todas as do vínculo ativo e responde 200 com quantas mudaram; repetir responde 0.
- R9 (RN-NOT-03, D1): `GET` e `PUT /api/v1/notifications/preferences` leem e gravam `receiveAll`; só o Coordenador altera (outros perfis de gestão: 403 no `PUT`; `GET` devolve `canReceiveAll = false`).
- R10 (RNF-11): `read-all` e a mudança de preferência gravam `notification.read_all` e `notification.preferences_updated` na auditoria, sem texto de notificação nem dado de pessoa; a criação automática não audita (não é ação de pessoa).
- R11 (permissão, isolamento): toda rota exige perfil de gestão (Coordenador, Administrador, Professor); cada pessoa só lê e marca as suas; uma organização nunca vê nem recebe notificação de outra (código e RLS); portal e sem sessão: 403 e 401.
- R12 (RF-NOT-01, D5): a barra superior da gestão mostra o sino com o contador (99+ acima de 99, some em zero); abre o painel com grupos Hoje e Esta semana, filtros Todas e Não lidas (com contagem), texto e hora relativa de cada notificação, não lida destacada, e a ação que resolve (botão que leva à tela do objeto); "Marcar todas como lidas"; estado "Tudo lido" quando não há não lidas no filtro; o Coordenador vê a chave "Receber tudo".
- R13 (padrão de tela): o painel tem carregando, vazio, erro (com tentar de novo) e sem permissão (perfil sem sino, não mostra o sino); funciona a partir de 360 px, passa no axe nos dois temas, abre e fecha pelo teclado devolvendo o foco ao sino, e todo texto vem de `pt-BR.json`.
- R14 (RNF-32): `docs/openapi.yaml` e os handlers MSW refletem as cinco rotas.

## 3. Critérios de aceite

- CA1 (R1, R2): dado Administrador da unidade A, Administrador da unidade B, Coordenador e Professor, quando surge `PAYMENT_RECEIVED` da unidade A, então só o Administrador de A tem a notificação; com o Coordenador em "receber tudo" ele também a tem; o Professor e o Administrador de B não.
- CA2 (R2): dado `HIGH_BENEFIT` ou `INVITE_PENDING`, então todos os Coordenadores ativos da organização recebem e nenhum Administrador; vínculo inativo não recebe.
- CA3 (R3): dado pagamento registrado no balcão, quando `Payments.register` conclui, então o Administrador da unidade da cobrança tem `PAYMENT_RECEIVED` com `refId` da cobrança; pagamento que falha na validação não gera nada.
- CA4 (R3): dado benefício de 60% concedido, então o Coordenador tem `HIGH_BENEFIT`; benefício de 50% ou de 30% não gera nada.
- CA5 (R3): dado aluno que completa 18 anos, quando `MajorityNoticeJob` roda, então o Administrador da unidade de cadastro tem `STUDENT_MAJORITY`; rodar de novo não duplica.
- CA6 (R3): dado e-mail de aluno marcado "Com problema", então o Administrador da unidade tem `EMAIL_PROBLEM`; marcar de novo o mesmo endereço não duplica.
- CA7 (R3): dado convite criado há 7 dias sem uso nem cancelamento, quando a tarefa diária roda, então os Coordenadores têm `INVITE_PENDING`; convite usado, cancelado ou com menos de 7 dias não gera; a tarefa repetida não duplica.
- CA8 (R4): dado fato que se repete (mesmo tipo e objeto), então há uma só linha por pessoa; dado erro ao gravar a notificação dentro do pagamento, então o pagamento continua registrado.
- CA9 (R5): dado `SUBSCRIPTION_OVERDUE`, então cada Coordenador tem a notificação e uma mensagem na Caixa de saída com assunto próprio, sem nome de aluno; dado `PAYMENT_RECEIVED`, então nenhum e-mail sai.
- CA10 (R6): dado vínculo com 3 notificações de hoje, 2 de 3 dias atrás e 1 de 9 dias atrás, quando `GET /notifications`, então 5 itens, 3 `TODAY` e 2 `WEEK`, ordenados do mais novo; `filter=unread` só traz as não lidas; `unreadCount` conta a de 9 dias atrás também; o limite de "hoje" usa o fuso da unidade (teste perto da meia-noite de Brasília).
- CA11 (R7): dado 4 não lidas, quando `GET /notifications/unread-count`, então `{ "count": 4 }`; depois de `read-all`, 0.
- CA12 (R8): dado 4 não lidas, quando `POST /notifications/read-all`, então 200 com `changed = 4`, `readAt` preenchido e a auditoria `notification.read_all`; repetido responde `changed = 0`; as notificações de outra pessoa não mudam.
- CA13 (R9, R10): dado Coordenador, quando `PUT /notifications/preferences` com `receiveAll = true`, então 200, o valor persiste, passa a receber os de unidade a partir daí e há `notification.preferences_updated`; dado Administrador, `PUT` responde 403 e `GET` traz `canReceiveAll = false`; `receiveAll` que não seja booleano responde 422.
- CA14 (R11): dado portal (aluno ou responsável) e pedido sem sessão, quando qualquer rota, então 403 e 401; dado a organização B, então nenhuma rota devolve ou altera linha da A e uma consulta direta ao banco no contexto de A não retorna linha de B (RLS); dado Professor, então 200 com lista vazia.
- CA15 (R12): dado painel com notificações de hoje e da semana, então aparecem os dois grupos com títulos; "Não lidas" mostra só as não lidas; o botão de cada notificação leva à tela do objeto; "Marcar todas como lidas" envia `POST read-all`, zera o contador e mostra "Tudo lido"; contador acima de 99 mostra "99+" e em zero o sino fica sem número.
- CA16 (R12): dado Coordenador, então "Receber tudo" aparece e ao mudar envia o `PUT`; dado Administrador, a chave não aparece.
- CA17 (R13): dado painel carregando, vazio (nenhuma notificação), com erro (tentar de novo) e perfil sem permissão (sem sino), então cada estado aparece; Esc fecha o painel e o foco volta ao sino; axe passa nos dois temas a 360 px.
- CA18 (R14): `OpenApiContractTest` e `ContractRoutesTest` passam; o MSW responde como o servidor (filtro, contador, `read-all`, preferência, 403 do `PUT` para Administrador).

## 4. Plano técnico

- Dados: migração `V47__notification.sql` (a criar; a última é `V46`): tabela `notification (id, organization_id, link_id, unit_id null, type, ref_type, ref_id, params jsonb, created_at, read_at)`, uma linha por pessoa (vínculo); `check` do catálogo em `type`; índice único parcial `(link_id, type, ref_id)` onde `ref_id is not null` (R4); índice `(link_id, created_at desc)`; chave estrangeira `(organization_id, link_id)` para `account_link`. Tabela `notification_preference (organization_id, link_id, receive_all boolean, updated_at)`, chave `(organization_id, link_id)`. Nas duas, RLS no padrão `organization_scope` da `V46` (`enable` e `force`); papel operacional com SELECT, INSERT e UPDATE (leitura marca `read_at`; sem DELETE nem limpeza automática neste item, D7). Seed: `infra/seed/NN-notification.sql` (a criar; `NN` é o próximo número livre) com algumas notificações fictícias, `on conflict do nothing`.
- API: `GET /notifications`, `GET /notifications/unread-count`, `POST /notifications/read-all`, `GET` e `PUT /notifications/preferences`, todas sobre o vínculo ativo da sessão. Erros 401, 403, 422. Quem chama: perfis de gestão.
- Interface: sino na barra superior (`ManagementLayout.headerExtra`) com contador consultado a cada 60 s; painel lateral (`SidePanel`) com filtros, grupos e ação por tipo; chave "Receber tudo" para o Coordenador.
- Segurança: o vínculo vem da sessão, nunca do corpo; permissão conferida em toda rota; `params` carrega só o necessário para o texto (nome do aluno ou da pessoa, valor em centavos), nunca CPF, e-mail ou telefone; texto da notificação e destinatário do e-mail urgente não vão a log nem a métrica; o e-mail urgente passa pelo registro da #78 (respeita "Com problema").
- Arquivos:
  - `backend/src/main/resources/db/migration/V47__notification.sql` (a criar).
  - `backend/src/main/java/br/com/ctcore/notification/NotificationType.java` (a criar): enum do catálogo (audiência `UNIT_ADMINS` ou `COORDINATORS`, urgente sim ou não).
  - `backend/src/main/java/br/com/ctcore/notification/Notifications.java` (a criar): `publish(org, unitId, type, refType, refId, params)` (resolve destinatários por R2, grava com `on conflict do nothing`, manda e-mail se urgente, engole e registra falha sem log de dado, R4), `list`, `unreadCount`, `readAll`, `preferences`, `setReceiveAll` (com `Permissions`, `OrganizationTransactions`, `AuditService`, no desenho de `terms/Terms`).
  - `backend/src/main/java/br/com/ctcore/notification/NotificationController.java` (a criar): as cinco rotas.
  - `backend/src/main/java/br/com/ctcore/notification/InvitePendingJob.java` (a criar): tarefa diária com `@SchedulerLock`, no desenho de `MajorityNoticeJob`.
  - `backend/src/main/java/br/com/ctcore/mail/EmailTemplate.java` e `backend/src/main/resources/messages.properties`: modelo `NOTIFICATION_URGENT` (sempre enviado, texto genérico por tipo, sem dado de pessoa).
  - `backend/src/main/java/br/com/ctcore/payment/Payments.java` (`register`), `benefit/BenefitWrite.java` (concessão com `high`), `student/MajorityNoticeJob.java` (`notify`), `student/EmailProblems.java` (`mark`): chamam `Notifications.publish` depois do fato, fora do caminho que desfaz a transação.
  - `docs/openapi.yaml`: cinco rotas e esquemas `Notification`, `NotificationList`, `NotificationPreferences`.
  - `frontend/src/features/notifications/BellButton.tsx`, `NotificationPanel.tsx`, `useNotifications.ts`, `notificationActions.ts`, `NotificationPanel.test.tsx` (a criar); `frontend/src/features/permissions/ManagementLayout.tsx` (sino em `headerExtra`); `frontend/src/i18n/pt-BR.json` (grupo `notifications`); `frontend/src/mocks/handlers.ts` e `frontend/src/mocks/notifications.msw.test.ts` (a criar).
  - Testes de back-end a criar em `backend/src/test/java/br/com/ctcore/notification/`: `NotificationsTest` (destinatários, audiência, grupos), `NotificationsIT` (rotas, perfis, isolamento, RLS, auditoria, e-mail urgente), `InvitePendingJobIT`; os `*IT` de pagamento, benefício, maioridade e e-mail com problema ganham um caso cada (CA3 a CA6, CA8).
- Reuso: `Terms` (permissão e transação), `AuditService`/`ScopedAudit`, `OrganizationTransactions`, `EmailService`/`EmailTemplate` (#78), `SidePanel`, `ManagementLayout`, `OrganizationBase` para os `*IT`, `MajorityNoticeJob` como molde da tarefa diária, fuso da unidade (`unit.timezone`) já usado na `MajorityNoticeJob`.
- Riscos: (1) `Payments.register` e `BenefitWrite` ficam mais acoplados; a chamada fica depois do fato e em `try/catch` (CA8). (2) Cobertura: o pacote `payment` exige 100%, então o ramo novo precisa de teste (CA3, CA8). (3) Rotas de destino de cada tipo (aluno, cobrança, convite) dependem do que o `App.tsx` já tem; conferir na construção e usar a tela de lista quando não houver tela do objeto. (4) Colunas de `account_link` (situação ativa) e de unidade da cobrança conferidas na construção. (5) `Notificacoes.dc.html` e `Topbar.dc.html` não foram abertos (D9).

## 5. Tarefas

Parte A — servidor (T1 a T4). Parte B — interface e fechamento (T5 a T7). A construção pode ser feita em sessões separadas por parte.

- [x] T1: migração `V47__notification.sql`, `NotificationType` e `Notifications.publish` (destinatários, deduplicação, falha engolida) — cobre CA1, CA2, CA8 — prova: `./backend/mvnw -f backend/pom.xml test -Dtest='NotificationsTest'`
- [x] T2: `NotificationController` e os métodos de leitura (`list`, `unreadCount`, `readAll`, preferências) com permissão, isolamento e auditoria — cobre CA10, CA11, CA12, CA13, CA14 — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test='NotificationsIT' -Dtest=NoTest -Dsurefire.failIfNoSpecifiedTests=false`
- [x] T3: produtores: `Payments.register`, `BenefitWrite`, `MajorityNoticeJob`, `EmailProblems.mark` e `InvitePendingJob` — cobre CA3, CA4, CA5, CA6, CA7, CA8 — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test='InvitePendingJobIT,NotificationProducersIT' -Dtest=NoTest -Dsurefire.failIfNoSpecifiedTests=false` (mais os `*IT` existentes de pagamento, benefício, maioridade e e-mail com problema)
- [x] T4: e-mail urgente (`NOTIFICATION_URGENT`) e `docs/openapi.yaml` das cinco rotas — cobre CA9, CA18 (servidor) — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test='NotificationsIT' -Dtest='OpenApiContractTest,ContractRoutesTest' -Dsurefire.failIfNoSpecifiedTests=false`
- [x] T5: front — `useNotifications`, handlers MSW, `notifications.msw.test.ts` e textos em `pt-BR.json` — cobre CA18 (MSW) — prova: em `frontend/`, `npx vitest run src/mocks/notifications.msw.test.ts` e `npm run typecheck`
- [x] T6: front — `BellButton`, `NotificationPanel` (grupos, filtros, ação, marcar todas, Tudo lido, Receber tudo, estados) no `ManagementLayout`, com testes e axe — cobre CA15, CA16, CA17 — prova: em `frontend/`, `npx vitest run src/features/notifications src/features/permissions/ManagementLayout.test.tsx`
- [x] T7: seed `infra/seed/NN-notification.sql` e regressão completa — não cobre critério: exigência do AGENTS.md (seed por tabela nova) e da definição de pronto — prova: `bash infra/scripts/seed.sh`; `./backend/mvnw -f backend/pom.xml verify` e, em `frontend/`, `npm run lint && npm run typecheck && npm run build && npm run test:cov && npm run e2e`

## 6. Decisões

- D1 (muda o comportamento): a regra de quem recebe (RN-NOT-03) vira audiência por tipo: de unidade vai ao Administrador com acesso à unidade, da organização vai ao Coordenador; "receber tudo" é uma preferência por vínculo, desligada por padrão, e só o Coordenador a muda. Alternativa: todo Administrador receber tudo da organização, descartada por contrariar "Administradores da unidade".
- D2 (muda o comportamento): o catálogo traz só os tipos cujo fato já existe, mais os três urgentes (para o e-mail na hora ter mecanismo e teste); pagamento recusado, comprovante, pedido do portal, aula experimental, autocadastro, turma sem professor e pedido de exclusão de dados entram, cada um, com o item que cria o fato. "Turma sem professor ativo" não acontece hoje, porque `TeacherDeactivation` recusa desativar o titular de turma. Alternativa: criar os tipos sem produtor, descartada por serem código morto.
- D3 (muda o comportamento): o resumo diário por e-mail e a opção de desligá-lo (RN-NOT-01) ficam para o item que criar o "Precisa de atenção"; o sino e o contador da RN-NOT-01 entram agora. Registrar como sugestão de novo item. Alternativa: montar o resumo agora com os dados do sino, descartada por não haver ainda a lista de pendências que ele deve trazer.
- D4: o Professor está dentro do mecanismo (recebe lista vazia, sem erro) mas nenhum tipo atual é de turma; os tipos de turma e a regra "conforme as suas permissões e nada financeiro" entram com seus fatos. Suposição: se o dono quiser o Professor receber já algo (ex.: mudança de turma), vira novo tipo com produtor.
- D5: a ação que resolve é o botão de cada notificação que leva à tela do objeto (aluno, cobrança, convite, Configurações › Usuários); abrir a ação não marca como lida (só "Marcar todas"; não há rota de marcar uma), porque o card pede só a marcação em massa. Alternativa: marcar uma ao clicar, descartada por exigir rota e auditoria que o card não pede.
- D6: o contador é consultado a cada 60 s e ao abrir o painel; sem WebSocket nem envio em tempo real. Alternativa: tempo real, descartada por não estar no card e por não haver essa infraestrutura.
- D7: não há limpeza automática de notificações antigas; a lista mostra só os últimos 7 dias e o contador conta todas as não lidas. Alternativa: apagar com 90 dias, descartada por ser retenção de dados, que o card não define; vira item se o volume pedir.
- D8: o fuso de "hoje" e "esta semana" é o da unidade da notificação, ou o da unidade mais antiga da organização nas da organização; não é a data da JVM (regra do AGENTS.md).
- D9: os canvas `Notificacoes.dc.html` e `Topbar.dc.html` não puderam ser abertos; a tela segue `SidePanel`, as telas existentes e a especificação, e o dono confere o layout na revisão da sprint (como D8 da #83).
- D10: sem ponta a ponta nem recurso desligado por organização (sino é parte básica da gestão); notas de versão ainda não existem (RF-AJU-04, Sprint 21).
- Suposição: a unidade do pagamento recebido é a da cobrança; do e-mail com problema, a de cadastro do aluno ou do responsável. Se a cobrança não guardar a unidade, usa-se a da matrícula do aluno; muda só a consulta do produtor.

## Construção

- T1 — arquivos: `backend/src/main/resources/db/migration/V47__notification.sql`, `notification/NotificationType.java`, `notification/Notifications.java` (`publish`), `NotificationsTest` — prova: `./backend/mvnw -f backend/pom.xml test -Dtest='NotificationsTest'` → 3 testes passam (catálogo, grupos no fuso de Brasília perto da meia-noite, falha engolida). Destinatários, deduplicação e RLS provados também em `NotificationsIT`.
- T2 — arquivos: `notification/NotificationController.java`, métodos de leitura em `Notifications`, `NotificationsIT` — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test='NotificationsIT' -Dtest=NoTest -Dsurefire.failIfNoSpecifiedTests=false` → 5 testes passam (destinatários, lista/contador/read-all com auditoria, preferências, perfis e isolamento com RLS, e-mail urgente).
- T3 — arquivos: `payment/Payments.java`, `benefit/BenefitWrite.java`, `student/MajorityNoticeJob.java`, `student/EmailProblems.java`, `notification/InvitePendingJob.java`; testes `InvitePendingJobIT` (novo) e um caso em `PaymentIT`, `BenefitGrantIT`, `MajorityNoticeJobIT`, `EmailRegistryIT`; `StudentDatesIT`/`MajorityNoticeJobIT` com o construtor novo — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test='InvitePendingJobIT,PaymentIT,BenefitGrantIT,MajorityNoticeJobIT,EmailRegistryIT,StudentDatesIT,NotificationsIT' -Dtest=NoTest -Dsurefire.failIfNoSpecifiedTests=false` → 35 testes passam.
- T4 — arquivos: `mail/EmailTemplate.java` (`NOTIFICATION_URGENT`), `messages.properties`, `V47` (modelo entra no `check` de `email_message`), `docs/openapi.yaml` (cinco rotas, `NotificationType`, `Notification`, `NotificationList`, `NotificationPreferences`) — prova: `./backend/mvnw -f backend/pom.xml test -Dtest='OpenApiContractTest,ContractRoutesTest,NotificationsTest'` → 10 testes passam; e-mail urgente em `NotificationsIT`.
- T5 — arquivos: `frontend/src/features/notifications/useNotifications.ts`, `frontend/src/mocks/handlers.ts`, `frontend/src/mocks/notifications.msw.test.ts`, `frontend/src/i18n/pt-BR.json` — prova: `npx vitest run src/mocks/notifications.msw.test.ts src/mocks/reachable.msw.test.ts` → 7 passam; `npm run typecheck` sem erro.
- T6 — arquivos: `features/notifications/BellButton.tsx`, `NotificationPanel.tsx`, `notificationActions.ts`, `NotificationPanel.test.tsx`, `features/permissions/ManagementLayout.tsx` — prova: `npx vitest run src/features/notifications src/features/permissions` → 20 passam (grupos, filtros, ação, marcar todas, Tudo lido, 99+, Receber tudo, quatro estados, Esc devolve o foco ao sino, axe nos dois temas a 360 px).
- T7 — arquivos: `infra/seed/20-notificacoes.sql`, `SeedDataIT` (caso novo e `notification` nas duas listas de limpeza) — prova: `SeedDataIT` e `EmailRegistryIT` → 25 passam (o seed é aplicado pelo `SeedDataIT`, como no CI); `bash infra/scripts/seed.sh` não rodou: não há ambiente local de pé nesta sessão.

Regressão: `./backend/mvnw -f backend/pom.xml verify` → 750 testes, 0 falhas, JaCoCo sem regra violada (BUILD SUCCESS, na terceira rodada). `npm run lint && npm run typecheck && npm run build && npm run test:cov` → lint, tipos e build sem erro; 136 arquivos e 1176 testes passam; cobertura de linhas 96,8%. `npm run e2e` → 36 passam. A linha de base não piorou.

Desvios:
- Rotas de erro: o contrato do item pede 422 para `receiveAll` que não seja booleano; o servidor responde 422 (`UNPROCESSABLE`), diferente do 400 de validação das outras rotas, como a especificação manda.
- D8: a tabela `unit` não tem data de criação; "a unidade mais antiga" das notificações da organização virou a primeira por nome curto (comentário `ponytail:` em `Notifications.list`).
- Painel: a lista é pedida inteira (`GET /notifications`) e o filtro "Não lidas" é aplicado no cliente, para a contagem das duas abas sair da mesma resposta; o servidor segue aceitando `filter=unread` (provado no `NotificationsIT` e no MSW).
- D5: `refType = guardian` (e-mail com problema de responsável) leva à lista de alunos, por não haver tela do responsável; tipos urgentes levam a Configurações › Organização até existirem as telas deles.
- `PostgresIT` ganhou dois ajudantes de teste (`linked`, `cleanLinked`) para criar gestor com vínculo e unidade reais.
- `specs/backlog.md`: pendência P3 (resumo diário por e-mail, D3), "nenhum" em "Trava qual item".
- `bash infra/scripts/seed.sh` não foi executado (sem ambiente local).

Falha anterior / instável: na primeira rodada completa do `verify` (já com o item), `AuditServiceIT.writesDiffWithoutSecrets` falhou porque um carimbo de hora do log (`07:19:52.4254111Z`) contém a sequência "4111" que o teste proíbe no log; sem relação com o item, rodado de novo sozinho e na regressão completa seguinte passou. Registrado como teste instável por coincidência de hora; pelo AGENTS.md cabe virar issue no formato das #318 e #320.

Não é do item: nada além dos arquivos acima.

## Verificação

### Rodada 1 — OK — 2026-10-24, estado 966ebec54da9e19e7777eb53d048a89df04e9c18

| Critério | Status | Evidência |
|---|---|---|
| CA1, CA2 | PASSOU | `NotificationsIT` (destinatários, audiência, vínculo inativo) dentro do `verify` completo |
| CA3, CA4, CA5, CA6, CA8 | PASSOU | casos novos em `PaymentIT`, `BenefitGrantIT`, `MajorityNoticeJobIT`, `EmailRegistryIT`; produtores chamam `publish` depois do commit, com falha engolida (lido no diff) |
| CA7 | PASSOU | `InvitePendingJobIT` |
| CA9 | PASSOU | e-mail urgente em `NotificationsIT`; só tipos urgentes enviam (`publish`) |
| CA10, CA11, CA12 | PASSOU | `NotificationsIT` e `NotificationsTest` (grupos no fuso, meia-noite de Brasília) |
| CA13, CA14 | PASSOU | `NotificationsIT` (preferências, 403/401/422, RLS, Professor com lista vazia); RLS e política na V47 conferidas |
| CA15, CA16, CA17 | PASSOU | `NotificationPanel.test.tsx` (grupos, filtros, 99+, Receber tudo, quatro estados, Esc, axe nos dois temas a 360 px); rotas de destino existem no `App.tsx` |
| CA18 | PASSOU | `OpenApiContractTest`, `ContractRoutesTest` no `verify`; `notifications.msw.test.ts` na suíte do front |

Comandos:
- `./backend/mvnw -f backend/pom.xml verify` → 750 testes, 0 falhas, BUILD SUCCESS (JaCoCo sem regra violada).
- em `frontend/`: `npm run lint && npm run typecheck && npm run build && npm run test:cov && npm run e2e` → tudo verde; 136 arquivos, 1176 testes; linhas 96,8%; e2e 36 passam.
- `bash infra/scripts/seed.sh` não rodado (sem ambiente local); o seed é aplicado pelo `SeedDataIT`, que passou.

Achados:
- sugestão, código, `AuditServiceIT.writesDiffWithoutSecrets` — falhou uma vez na construção por coincidência de "4111" no carimbo de hora do log; passou nesta rodada. Pelo AGENTS.md (regressão vermelha vira issue no formato das #318 e #320), abrir issue para o teste instável.
- sugestão, código, `InvitePendingJob` — convite expirado e não usado continua gerando aviso; confirmar com o dono se é o desejado.

Instáveis: `AuditServiceIT.writesDiffWithoutSecrets` (1 falha na construção, 0 nesta rodada; causa: carimbo de hora com "4111").

## Entrega

O sino da barra da gestão mostra o contador de não lidas (99+ acima de 99, some em zero) e abre o painel com grupos Hoje e Esta semana, filtros Todas e Não lidas, a ação que leva ao objeto de cada notificação, "Marcar todas como lidas", o estado "Tudo lido" e, para o Coordenador, "Receber tudo". O servidor guarda as notificações por vínculo, decide o destinatário (RN-NOT-03), grava as que já têm fato no sistema (RN-NOT-04) e manda e-mail na hora só para as urgentes (RN-NOT-02). Produtores: `Payments.register`, `BenefitWrite` (benefício acima de 50%), `MajorityNoticeJob`, `EmailProblems`, `EmailRegistry` e o convite pendente (`InvitePendingJob`); a publicação ocorre depois do commit, com falha engolida.

Verificação: rodada 1 `OK` no estado 966ebec. `verify` do back-end com 750 testes verdes; no front, lint, typecheck, build, `test:cov` e e2e verdes (linhas 96,8%).

Decisões: o resumo diário por e-mail e o "Precisa de atenção" ficam fora (D3); os tipos sem fato hoje entram com o item que criar o fato (D2); sem notificação para o Professor (D4); a tela consulta de tempos em tempos, sem tempo real (D6).

Limitações: convite expirado e não usado continua gerando aviso (confirmar com o dono se é o desejado); `AuditServiceIT.writesDiffWithoutSecrets` falhou uma vez na construção por coincidência de "4111" no carimbo de hora (instável, sem falha na rodada 1).
