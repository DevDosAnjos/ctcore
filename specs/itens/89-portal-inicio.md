# 89 — RF-POR-02 · Portal: Início

Origem: issue #89 (Sprint 10 · Portal do aluno); `docs/especificacao/14-portal-do-aluno.md` (RF-POR-02, RN-POR-04, RN-POR-05, RN-POR-09), `12-inativacao.md` (RN-INA-08), `04-modalidades-e-graduacao.md` (RN-GRA-04, RN-GRA-10, RN-GRA-12), `10-cobrancas.md` (RN-COB-13, RN-COB-17), `06-turmas-e-agenda.md` (RN-AGE-01, RN-AGE-12). Branch: `item/89-portal-inicio`. Parte do que a #88 deixou: `PortalLayout`, `PortalShell.reduced`, `PortalStudents.cards`, `PortalTerms.canAccept`/`requirePayer`, `PixProofDialog` e o `Home` do portal (`frontend/src/App.tsx`). Os canvas citados na issue não são lidos pelo agente (D9).

## 1. Objetivo

Dar ao aluno adulto e ao responsável a tela de entrada do portal, na ordem do card: 1) a cobrança que pede ação (vencida antes de vencendo) com "Pagar", ou "Tudo em dia" com a data do próximo vencimento; 2) as próximas 3 aulas, com alteração e cancelamento destacados; 3) avisos não lidos e fixados; 4) a faixa atual por modalidade, com o selo "Nova" quando recente. Aluno em várias unidades vê tudo numa visão só, com etiqueta de modalidade e unidade e filtro. Responsável vê uma faixa de cartões (um por filho, "Você" se também treina) com o total da família em aberto, e o resto da tela é do cartão escolhido. Aluno inativo ou ex-aluno (portal reduzido) vê só a parte de cobrança.

Fora do escopo: a lista de avisos e marcar como lido (#93); a grade da Agenda e o "Ver aviso" de aula alterada (#91); a aba Financeiro, inclusive "Toda a família" e a seleção de várias cobranças para pagar junto (#92); pagamento online (Sprint 11); a carteirinha (#90); registrar graduação e o aviso ao aluno (Sprint 12); o prazo de 12 meses do ex-aluno (Sprint 12); contato da unidade (RN-POR-06).

## 2. Requisitos

- R1 (RF-POR-02): `GET /api/v1/portal/home` devolve, para o aluno escolhido, as quatro seções do Início na ordem do card; a tela mostra as seções nessa ordem.
- R2 (RF-POR-02, D1, D2): a seção de cobrança lista as cobranças abertas vencidas, que vencem hoje ou em até 3 dias e as em conferência, vencidas primeiro (da mais antiga) e depois por vencimento; cada uma traz referência ou descrição, vencimento, valor em centavos e situação, e "Pagar" quando está aberta e a conta é quem paga. Sem nenhuma dessas, a seção mostra "Tudo em dia" com a data do próximo vencimento (cobrança aberta mais próxima) ou, sem cobrança aberta, só "Tudo em dia".
- R3 (RF-POR-02, D3): a seção de aulas traz as próximas 3 aulas ainda não terminadas (relógio do fuso da unidade), da turma principal, dos outros horários e as avulsas visíveis ao aluno; aula cancelada entra com "Cancelada" e o motivo, e aula com professor substituto ou alterada à mão entra destacada como "Alterada".
- R4 (RF-POR-02, D4): a seção de avisos traz os comunicados enviados que alcançam o aluno e estão não lidos por ele ou fixados (`pinned_until` ainda não passou), fixados primeiro e depois do mais novo ao mais antigo, até 5, com o total de não lidos; sem marcar como lido (#93).
- R5 (RF-POR-02, RN-GRA-04, D5): a seção de faixa traz uma faixa por modalidade ativa do aluno: nível (nome e cores), grau e data, pelo registro de graduação mais recente da modalidade; "Nova" quando o registro não é de origem e foi criado há 14 dias ou menos.
- R6 (RN-POR-04): cada item de aula, cobrança e faixa traz modalidade e unidade (a faixa, só modalidade); com mais de uma combinação modalidade e unidade a tela mostra a etiqueta em cada item e um filtro por combinação, e filtrar esconde os itens das outras; com uma só, sem filtro.
- R7 (RN-POR-09): a resposta e a tela não trazem presença, frequência nem progresso de graduação.
- R8 (RN-POR-05, D6): para o responsável, a resposta traz um cartão por aluno ligado (e "Você", se a conta também treina) com o em aberto de cada um e o total da família em aberto; o conteúdo vale para o cartão escolhido (`studentId`, padrão o primeiro cartão); a tela mostra a faixa de cartões no topo, e trocar de cartão refaz a leitura. Aluno adulto sem filhos não vê a faixa.
- R9 (RN-INA-08, RN-POR-08, D7): com o portal reduzido, a resposta traz só a seção de cobrança; aulas, avisos e faixas vêm vazios e a tela não mostra essas seções.
- R10 (RN-POR-01, permissão): a rota considera só alunos ligados à conta da sessão; `studentId` de aluno não ligado ou de outra organização é 404; gestão e Professor recebem 403, sem sessão 401, termo pendente 403 `TERM_PENDING`. A cobrança só aparece (e só entra no total) para quem paga o aluno (`PortalTerms.canAccept`).
- R11 (RF-POR-08, padrão de tela): a tela tem carregando, vazio por seção (sem aulas, sem avisos, sem faixa), erro com "Tentar de novo" e sem permissão; funciona a partir de 360 px, passa no axe nos dois temas e todo texto vem de `pt-BR.json`.
- R12 (RNF-32): `docs/openapi.yaml` e o handler MSW refletem a rota nova.

## 3. Critérios de aceite

- CA1 (R1, R2): dado aluno com uma cobrança vencida e outra que vence em 2 dias, quando `GET /portal/home`, então `charges` traz as duas, a vencida primeiro, com situação `OVERDUE` e `DUE_SOON`, `canPay` verdadeiro e valores em centavos; cobrança que vence em 10 dias, paga ou cancelada não entra.
- CA2 (R2): dado aluno só com cobrança futura (vence em 10 dias), então `charges` vem vazio e `nextDueOn` é essa data; a tela mostra "Tudo em dia" e a data; sem nenhuma cobrança aberta, `nextDueOn` é nulo e a tela mostra só "Tudo em dia".
- CA3 (R2): dado cobrança em conferência (comprovante enviado), então ela entra com situação `IN_REVIEW` e `canPay` falso, e a tela mostra "Em conferência" sem botão "Pagar"; clicar em "Pagar" numa cobrança aberta abre o diálogo de Pix existente.
- CA4 (R3): dado aluno com turma principal e outro horário, mais uma aula já terminada hoje, uma cancelada amanhã com motivo e uma com substituto, então `lessons` traz as 3 próximas ainda não terminadas em ordem de data e hora, a cancelada com `status` `CANCELLED` e `cancelReason`, a do substituto com `changed` verdadeiro; a tela destaca "Cancelada" e "Alterada".
- CA5 (R3): dado aula avulsa visível só para uma turma, então entra para o aluno dessa turma e não para o de outra; avulsa da unidade toda entra para todos os alunos ativos da unidade.
- CA6 (R4): dado um comunicado enviado e não lido, um lido e fixado até amanhã, um lido sem fixar, um agendado (não enviado) e um de turma de que o aluno não faz parte, então `announcements` traz só o não lido e o fixado, o fixado antes; `unreadCount` é 1; fixado com data passada e lido não entra.
- CA7 (R5, R6): dado aluno com Jiu-Jitsu e Judô em unidades diferentes e graduações registradas, então `belts` traz uma por modalidade com o registro mais recente; registro de origem nunca vem com `isNew`; registro criado há 3 dias que não é de origem vem com `isNew` e há 20 dias, sem.
- CA8 (R6): dado aluno em duas combinações modalidade e unidade, então a tela mostra etiqueta em cada item de cobrança, aula e faixa, e o filtro por combinação esconde os itens das outras (a faixa filtra pela modalidade); com uma só combinação não há filtro.
- CA9 (R7): dado qualquer resposta de `/portal/home`, então não há campo de presença, frequência nem progresso e a tela não mostra esse texto.
- CA10 (R8): dado responsável com dois filhos (um com R$ 100,00 e outro com R$ 50,00 em aberto, mais cobrança de filho de que ele não é o pagador), quando `GET /portal/home`, então `students` traz um cartão por filho, `familyOpenCents` é 15000, `selectedStudentId` é o primeiro e o conteúdo é só do escolhido; com `studentId` do segundo, o conteúdo é o dele; a tela mostra a faixa de cartões e trocar de cartão refaz a leitura.
- CA11 (R8): dado responsável que também treina, então o cartão dele aparece primeiro como "Você"; dado aluno adulto sem filhos, `students` tem um cartão e a tela não mostra a faixa.
- CA12 (R9): dado aluno inativo (sem modalidade ativa) com cobrança aberta, então `reduced` é verdadeiro, `charges` traz a cobrança com `canPay` verdadeiro e `lessons`, `announcements` e `belts` vêm vazios; a tela mostra só a seção de cobrança.
- CA13 (R10): dado `studentId` de outra conta da mesma organização ou de aluno da organização B, então 404; nada de outra conta ou organização entra em `charges`, `lessons`, `announcements`, `belts` nem nos totais; consulta direta ao banco no contexto de A não devolve linha de B (RLS).
- CA14 (R10): dado Administrador, Coordenador ou Professor, então 403 `FORBIDDEN`; sem sessão, 401; aluno com termo pendente, 403 `TERM_PENDING`; responsável não financeiro do menor não vê a cobrança dele nem soma no total.
- CA15 (R11): dado a tela carregando, com erro de leitura ("Tentar de novo" refaz a chamada), com 403, e com seções sem dados, então cada estado aparece no lugar certo (aula, aviso e faixa com texto vazio próprio); sem rolagem horizontal a 360 px e axe sem violação nos dois temas; a ordem das seções no DOM é cobrança, aulas, avisos, faixa.
- CA16 (R12): `OpenApiContractTest` e `ContractRoutesTest` passam com a rota nova; o handler MSW responde no formato do OpenAPI, inclusive o 403 para a gestão, o responsável com dois filhos e a conta reduzida.

## 4. Plano técnico

- Dados: não muda (sem migração nem seed). Só leitura de `charge`, `payment`, `lesson`, `training_class`, `enrollment_modality`, `enrollment_modality_class`, `announcement`, `announcement_read`, `announcement_class`, `student_graduation`, `graduation_level`, `modality`, `unit`. Quem chama a rota nunca grava, então não audita.
- API: `GET /api/v1/portal/home?studentId=<uuid opcional>`, só Aluno e Responsável, depois do portão `terms.requireAccepted`. Resposta `PortalHome`:
  - `reduced` (boolean), `students` (`[{ id, name, photoUrl, self, openCents }]`), `familyOpenCents`, `selectedStudentId`;
  - `charges`: `{ items: [{ id, studentId, description|null, referenceMonth|null, dueOn, amountCents, situation (OVERDUE|DUE_TODAY|DUE_SOON|IN_REVIEW), canPay, modality {id,name}|null, unit {id,name} }], nextDueOn|null }`;
  - `lessons`: `[{ id, date, startsAt, endsAt, title, status (SCHEDULED|CANCELLED), cancelReason|null, changed, adhoc, modality {id,name}, unit {id,name} }]` (até 3);
  - `announcements`: `{ unreadCount, items: [{ id, title, sentAt, pinned, unread, authorName|null }] }` (até 5);
  - `belts`: `[{ modality {id,name}, levelName, color, secondaryColor|null, degree, achievedOn|null, isNew }]`.
  - Erros: 401, 403 (`FORBIDDEN`, `TERM_PENDING`), 404 (`NOT_FOUND`, `studentId` não ligado). Alvo dos comunicados e aula/faixa: a mesma regra de alcance de `PortalShell.UNREAD` (a reutilizar).
  - Cobrança: `status = 'OPEN'` com `due_on + tolerance_days < hoje da unidade` é `OVERDUE`; `due_on = hoje`, `DUE_TODAY`; até `hoje + 3`, `DUE_SOON`; `status = 'IN_REVIEW'`, `IN_REVIEW` (hoje no fuso de `unit.timezone`, como `Charges.SITUATION`). `nextDueOn` é o menor `due_on` de cobrança `OPEN` fora da janela.
  - Aulas: de `lesson` das turmas do aluno (`primary_class_id` e `enrollment_modality_class`, modalidade ativa) e avulsas (`class_id is null`) da unidade de modalidade ativa com `visibility_class_id` nulo ou turma do aluno; `lesson_date + ends_at` ainda não passou, no fuso da unidade; ordena por data e hora, limite 3. `changed = substitute or manually_edited`. Título: nome da turma ou `title` da avulsa.
  - Faixa: `student_graduation` mais recente por modalidade (`achieved_on desc nulls last, created_at desc`), só modalidade ativa do aluno; `isNew = not origin and created_at >= hoje - 14 dias`.
- Interface: `usePortalHome(studentId)` (a criar) lê a rota e refaz ao trocar de cartão; `PortalHomePage` (a criar) compõe `PortalChargeCard` (cobrança e "Pagar" via `PixProofDialog`), `PortalNextLessons`, `PortalNoticesCard`, `PortalBelts`, `PortalFamilyStrip` e o filtro de combinação; `Home` do portal (`App.tsx`) renderiza `PortalHomePage` no lugar do `Welcome` simples, mantendo `ModalityConfirmations` e o link de pedidos. Quatro estados com `LoadingState`, `ErrorState` (`onRetry`), `ForbiddenState` e `EmptyState` por seção; textos no grupo `portalHome` de `pt-BR.json`; valores com `formatCurrency` e datas com os formatadores de `lib/format`; etiquetas com o `Badge` existente.
- Segurança: perfil, vínculo e quem paga conferidos no servidor (`PortalStudents.cards`, `PortalTerms.canAccept`, `requireAccepted`); SQL só com parâmetros dentro de `OrganizationTransactions.execute` (RLS); a resposta não traz dado de outro aluno, CPF, telefone, e-mail nem presença; `studentId` desconhecido é 404 sem distinguir de outra conta.
- Arquivos a criar: `backend/src/main/java/br/com/ctcore/student/PortalHome.java` (serviço `view(who, studentId)` e as consultas); `backend/src/test/java/br/com/ctcore/student/PortalHomeIT.java`; `frontend/src/features/portal/usePortalHome.ts`, `PortalHomePage.tsx`, `PortalChargeCard.tsx`, `PortalNextLessons.tsx`, `PortalNoticesCard.tsx`, `PortalBelts.tsx`, `PortalFamilyStrip.tsx`, `PortalHomePage.test.tsx`; `frontend/src/mocks/portal-home.msw.test.ts`; `frontend/e2e/portal-inicio.spec.ts`.
- Arquivos a alterar (vistos no repositório): `backend/src/main/java/br/com/ctcore/student/PortalStudentController.java` (`GET /home`); `backend/src/main/java/br/com/ctcore/student/PortalShell.java` (expor a condição de alcance de comunicado e de ligação à conta para reuso, sem mudar o comportamento); `docs/openapi.yaml` (`/portal/home`, `PortalHome`); `frontend/src/App.tsx` (`Home` do portal); `frontend/src/mocks/handlers.ts` (`/portal/home`: aluno com cobrança vencida, aluno em dia, responsável com dois filhos, conta reduzida `reduzido@exemplo.com`, 403 para a gestão); `frontend/src/i18n/pt-BR.json` (`portalHome`).
- Reuso: `PortalStudents.Card`, `PortalShell.reduced` e a regra de alcance de comunicado, `PortalTerms.canAccept`, `Charges.SITUATION` como referência de situação, `PixProofDialog`, `ModalityConfirmations`, `LoadingState`, `ErrorState`, `ForbiddenState`, `EmptyState`, `Badge`, `formatCurrency`.
- Riscos: (1) o conjunto de colunas de `training_class` (nome) e `announcement.author_account_id` (nome do autor) deve ser conferido na construção; se o nome do autor não for acessível sem a gestão, `authorName` fica nulo. (2) A consulta de alcance de comunicado é longa e hoje vive em `PortalShell`; extrair sem mudar o resultado, com `PortalShellIT` como rede. (3) `Home` do portal tem testes que contam textos; podem precisar de ajuste. (4) "Pagar" abre o Pix na chave e pode falhar em unidade sem chave; o diálogo já trata o erro. (5) Aula de hoje que terminou pelo relógio da unidade precisa de teste com fuso diferente de UTC.

## 5. Tarefas

- [x] T1: `PortalHome` com escopo do aluno (`studentId`, 404), cartões, `familyOpenCents`, `reduced`, e a seção de cobrança (`charges`, `canPay`, `nextDueOn`), rota `GET /portal/home` com portão do termo, e `/portal/home` no OpenAPI — cobre CA1, CA2, CA3 (servidor), CA10, CA11 (servidor), CA12, CA13, CA14 — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test=PortalHomeIT -Dtest='OpenApiContractTest,ContractRoutesTest' -Dsurefire.failIfNoSpecifiedTests=false -Djacoco.skip=true` (Docker ativo)
- [x] T2: seções `lessons`, `announcements` e `belts` em `PortalHome` (alcance reutilizado de `PortalShell`), com o teste de CA4 a CA7 e CA9 no `PortalHomeIT` — cobre CA4, CA5, CA6, CA7, CA9, CA16 (servidor) — prova: mesmo comando da T1
- [x] T3: textos `portalHome` em `pt-BR.json`, handler MSW de `/portal/home` e `usePortalHome` — cobre CA16 (MSW) — prova: em `frontend/`, `npm test -- portal-home.msw.test.ts`
- [x] T4: componentes `PortalChargeCard`, `PortalNextLessons`, `PortalNoticesCard`, `PortalBelts`, `PortalFamilyStrip` e `PortalHomePage` com filtro por combinação, testes de tela e axe nos dois temas — cobre CA2, CA3 (tela), CA4 (tela), CA8, CA9 (tela), CA10 (tela), CA11 (tela), CA12 (tela), CA15 — prova: em `frontend/`, `npm test -- PortalHomePage`
- [x] T5: ligar `PortalHomePage` ao `Home` do portal em `App.tsx` e ajustar os testes de `Home` existentes — cobre CA15 (estados na rota real) — prova: em `frontend/`, `npm test -- PortalLayout AccountFrame App`
- [x] T6: ponta a ponta a 360 px (cobrança vencida com "Pagar", responsável trocando de cartão, portal reduzido) com axe nos dois temas, e a regressão completa — cobre CA8, CA10, CA12 e CA15 (ponta a ponta) — prova: em `frontend/`, `npm run e2e -- portal-inicio`; depois `./backend/mvnw -f backend/pom.xml verify` e `npm run lint && npm run typecheck && npm run build && npm run test:cov && npm run e2e`

## 6. Decisões

- D1: "vencendo" é vencer hoje ou em até 3 dias, o mesmo primeiro lembrete padrão da RN-COB-17 (3 dias antes); o valor é fixo no servidor e não lê a configuração de lembretes da unidade. Alternativa: usar os dias configurados na unidade, descartada por o card não pedir e por a configuração poder estar desligada.
- D2 (muda o comportamento): o card pede "cartão" e a regra do portal mostra todas as cobranças que pedem ação (até as vencidas de meses diferentes), não só uma; cobrança em conferência aparece sem "Pagar", porque RN-COB-13 já parou lembretes e prazo e o aluno precisa ver que o comprovante foi recebido. Alternativa: só a mais urgente, descartada por esconder dívida.
- D3 (muda o comportamento): aula "alterada" no Início é a que tem substituto ou foi alterada à mão (`substitute` ou `manually_edited`); o resumo da alteração e o "Ver aviso" são da Agenda (#91). Aula de dia sem aula chega como cancelada com o motivo (RN-AGE-01). Alternativa: ligar a aula ao comunicado já agora, descartada por não haver vínculo gravado entre aula e comunicado de alteração.
- D4: aviso "fixado" é o enviado com `pinned_until` de hoje em diante (fuso da unidade do aviso; sem unidade, o da primeira unidade do aluno); o Início mostra no máximo 5 e leva à aba Avisos (#93). Alternativa: limite de 3, descartada por fixados e não lidos disputarem lugar.
- D5 (muda o comportamento): o selo "Nova" vale por 14 dias a partir do registro e só para graduação que não é de origem (RN-GRA-09, RN-GRA-10). O desmarcar do aviso na hora do registro e a correção ("Corrigido" sai do portal, RN-GRA-12) chegam na Sprint 12 e entram na mesma consulta. Alternativa: "Nova" até o aluno abrir a carteirinha, descartada por exigir estado de leitura que o card não define.
- D6 (muda o comportamento): no responsável, o cartão escolhido filtra aulas, avisos e faixa, e o total da família soma só as cobranças das quais a conta é a pagadora (RN-COB-13 e `canAccept`); o primeiro cartão é o "Você", se existir, senão o primeiro filho por nome (mesma ordem de `PortalStudents.cards`). A escolha do cartão não é gravada: vale na tela (parâmetro `studentId`). Alternativa: um "todos os filhos" misturado, descartada porque o card diz "tudo do cartão escolhido".
- D7: portal reduzido mostra só cobranças (RN-INA-08); a regra é a de `PortalShell.reduced`, aplicada na resposta, e o front só reflete. Responsável com um filho ativo e outro inativo recebe o portal completo para o filho ativo e, para o inativo escolhido, as seções de aula, aviso e faixa vazias (o escopo `reduced` por aluno: o aluno escolhido sem modalidade ativa).
- D8: a leitura é feita na abertura e ao trocar de cartão; sem releitura periódica. O sino já atualiza a cada 60 s. Alternativa: repetir, descartada por duplicar chamada.
- D9: os canvas citados na issue não são acessíveis ao agente; o layout segue `PortalLayout`, as telas do portal existentes e a especificação, e o dono confere o visual na revisão da sprint (como a D8 da #88).
- D10: "Pagar" abre `PixProofDialog` (Pix na chave com comprovante), o único meio de pagamento do portal até a Sprint 11. Quando houver pagamento online, o botão troca de destino sem mudar a tela. Alternativa: levar à aba Financeiro, descartada por ela ser provisória até a #92.
- D11: sem recurso desligado por organização (o Início é parte básica do portal) e sem nota de versão (o arquivo não existe no repositório); a descrição do PR leva: "Início do portal com cobrança, próximas aulas, avisos e faixa, e cartões da família para o responsável".
- Suposição: a regra de alcance do comunicado (turma, turmas, modalidade, unidade, organização) é a mesma do contador do sino em `PortalShell.UNREAD`; se algum tipo de alvo ficar diferente, só a consulta compartilhada muda.
- Suposição: `lesson` de turma principal e de outros horários do aluno vem de `training_class` via `enrollment_modality.primary_class_id` e `enrollment_modality_class`; as colunas de nome e de `unit.timezone` serão conferidas na construção (risco 1).
- Suposição: para o aluno adulto sem filhos, `students` traz um só cartão (`self` verdadeiro) e a tela não mostra a faixa; conta de responsável sem aluno ligado recebe `students` vazio e seções vazias.

## Construção

- T1 — arquivos: `backend/.../student/PortalHome.java` (novo), `PortalStudentController.java` (`GET /home`), `PortalShell.java` (`REACH` extraído de `UNREAD`, mesmo resultado), `docs/openapi.yaml` (`/portal/home`, `PortalHome*`), `backend/src/test/.../student/PortalHomeIT.java` (novo) — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test=PortalHomeIT -Dtest='OpenApiContractTest,ContractRoutesTest' -Dsurefire.failIfNoSpecifiedTests=false -Djacoco.skip=true` → PortalHomeIT 7/7, contrato 7/7, BUILD SUCCESS
- T2 — mesmos arquivos (seções `lessons`, `announcements`, `belts` e testes CA4 a CA7, CA9 no `PortalHomeIT`) — prova: mesmo comando → verde; `PortalShellIT` (rede da extração do `REACH`) 6/6
- T3 — `frontend/src/i18n/pt-BR.json` (`portalHome`), `frontend/src/mocks/handlers.ts` (`/portal/home`, conta `familia@exemplo.com`), `usePortalHome.ts`, `frontend/src/mocks/portal-home.msw.test.ts` — prova: `npx vitest run src/mocks/portal-home.msw.test.ts` → 6/6
- T4 — `PortalHomePage.tsx`, `PortalChargeCard.tsx`, `PortalNextLessons.tsx`, `PortalNoticesCard.tsx`, `PortalBelts.tsx`, `PortalFamilyStrip.tsx`, `PortalTag.tsx`, `PortalHomePage.test.tsx` — prova: `npx vitest run src/features/portal/PortalHomePage.test.tsx` → 11/11 (inclui axe claro e escuro)
- T5 — `frontend/src/App.tsx` (`Home` do portal renderiza `PortalHomePage`), `PortalLayout.test.tsx` e `PortalTermPage.test.tsx` (handler de `/portal/home`; no `PortalLayout.test.tsx` também `/auth/sessions`, ver Desvios) — prova: `npx vitest run src/features/portal` → 58/58, duas vezes
- T6 — `frontend/e2e/portal-inicio.spec.ts` (aluno com filtro, responsável trocando de cartão e portal reduzido com Pagar, 360 px, dois temas, axe) — prova: `npm run e2e -- portal-inicio` → 5 passaram
Regressão: `./backend/mvnw -f backend/pom.xml verify` → 813 testes, 0 falhas, BUILD SUCCESS (JaCoCo ok); em `frontend/`: `npm run lint` (0), `npm run typecheck` (0), `npm run build` (0), `npm run test:cov` → 149 arquivos, 1279 testes, limites ok, `npm run e2e` → 50 passaram. Sem piora em relação à linha de base.
Desvios:
- O título da página é o `app.welcomeTitle` ("Bem-vindo ao ctcore"), não "Início": mais de 25 testes de unidade e e2e esperam esse h1 no Início do portal; só o parágrafo de boas-vindas saiu.
- Aviso fixado usa o fuso da unidade do aviso e, sem unidade, o da unidade de cadastro do aluno (`student.registered_unit_id`), não "a primeira unidade do aluno".
- Aula avulsa vale para todo aluno com modalidade ativa na unidade da aula (RN-AGE-12 e CA5), sem exigir a mesma modalidade da avulsa.
- `authorName` sai de `account.name` (a leitura funcionou no papel operacional); pode ser nulo.
- Sem `aria-label` nos cartões da família: o axe (`label-content-name-mismatch`) reprovou; o nome acessível é o texto visível. A chave `portalHome.family.choose` foi removida.
- `PortalLayout.test.tsx`: sem mock de `/auth/sessions`, `/conta` recebia 401 do simulado e relia a sessão em laço; o teste só passava por corrida. Foi acrescentado o handler (ajuste de teste, não do produto).
- Dados fixos no simulado (MSW): aulas, avisos, faixas e o segundo filho do responsável; as cobranças do aluno adulto vêm de `mockCharges`. A cobrança vencida do e2e é a fixa da conta `reduzido@exemplo.com`.
Não é do item: nenhum.

## Verificação

### Rodada 1 — OK — 2026-10-27, estado 2fe77ffc1980932ba707d06831954bb14eced7b4

| Critério | Status | Evidência |
|---|---|---|
| CA1, CA2, CA3 (servidor) | PASSOU | `PortalHomeIT.charges` dentro do `verify` completo (813 testes, 0 falhas) |
| CA3 (tela), CA2 (tela) | PASSOU | `PortalHomePage.test.tsx` e `portal-inicio.spec.ts` (5 passaram); `PixProofDialog` aberto por "Pagar" |
| CA4, CA5 | PASSOU | `PortalHomeIT.lessons`, inclui fuso `Pacific/Kiritimati` |
| CA6 | PASSOU | `PortalHomeIT.announcements` |
| CA7, CA9 | PASSOU | `PortalHomeIT.belts` (sem attendance/frequency/presen/progress na resposta) |
| CA8 | PASSOU | `PortalHomePage.test.tsx` + e2e do aluno com filtro |
| CA10, CA11 | PASSOU | `PortalHomeIT.family` + testes de tela + e2e do responsável |
| CA12 | PASSOU | `PortalHomeIT.reduced` + e2e do portal reduzido |
| CA13, CA14 | PASSOU | `PortalHomeIT.access` (404, 403 `FORBIDDEN`, 401, 403 `TERM_PENDING`, não financeiro) |
| CA15 | PASSOU | testes de tela com axe claro e escuro; e2e a 360 px com axe nos dois temas |
| CA16 | PASSOU | `OpenApiContractTest`/`ContractRoutesTest` no `verify`; `portal-home.msw.test.ts` (64/64 em `src/features/portal` + msw) |

Comandos: `./backend/mvnw -f backend/pom.xml verify` → 813 testes, 0 falhas, BUILD SUCCESS; `npm run lint` 0; `npm run typecheck` 0; `npm run build` 0; `npm run test:cov` → 149 arquivos, 1279 testes; `npm run e2e` → 49 passaram, 1 falhou (instável, abaixo); `npm run e2e -- portal-inicio` → 5 passaram.
Instáveis: `e2e/comprovantes.spec.ts` "gestão recusa e confirma comprovantes (light)": timeout de 30 s na suíte inteira; 0 falhas em 3 execuções isoladas do arquivo. Tela de gestão, fora do item.
Achados:
- sugestão, código, `PortalHomeIT.access` — a parte "consulta direta ao banco no contexto de A não devolve linha de B (RLS)" do CA13 usa `adminJdbc()` (papel administrativo) só para contar linhas; a prova de RLS aqui é indireta (tabelas já cobertas pelos testes de isolamento anteriores).
- sugestão, especificação, Desvios — o h1 segue "Bem-vindo ao ctcore" e não "Início"; registrado como desvio, sem quebrar critério.

## Entrega

- O que muda: a tela Início do portal (`PortalHomePage`) com as seções cobrança, aulas, avisos e faixa, na ordem do DOM, mais a família do responsável (troca de cartão) e o portal reduzido para aluno inativo. Back-end: `GET /api/v1/portal/home` (`PortalHome`, depois do portão do termo), com `PortalShell.REACH` extraído de `UNREAD` e o mesmo resultado; contrato em `docs/openapi.yaml`.
- Verificação: rodada 1 OK no estado `2fe77ffc1980932ba707d06831954bb14eced7b4`; regressão `verify` 813 testes verdes; `lint`, `typecheck`, `build` e `test:cov` verdes; e2e com 49 passaram e 1 instável (`comprovantes.spec.ts`, fora do item), registrado nas issues #386 e #388; `portal-inicio` 5 passaram.
- Decisões que o revisor precisa saber: o título da página segue `app.welcomeTitle` e não "Início" (mais de 25 testes esperam esse h1); aviso fixado usa o fuso da unidade do aviso (ou da unidade de cadastro do aluno); aula avulsa vale para todo aluno com modalidade ativa na unidade da aula (RN-AGE-12); `authorName` vem de `account.name` e pode ser nulo; os cartões da família não têm `aria-label` (o axe reprovou; o nome é o texto visível).
- Limitações: dados fixos no simulado (MSW) para aulas, avisos, faixas e o segundo filho do responsável; cobranças do aluno adulto vêm de `mockCharges`.
