# 91 — RF-POR-04 · Portal: Agenda

Origem: issue #91 (Sprint 10 · Portal do aluno); `docs/especificacao/14-portal-do-aluno.md` (RF-POR-04, RN-POR-01, RN-POR-02, RN-POR-04, RN-POR-05, RN-POR-06, RN-POR-08, RN-POR-09), `06-turmas-e-agenda.md` (RN-AGE-01, RN-AGE-03, RN-AGE-04, RN-AGE-05, RN-AGE-12), `13-comunicacao-e-notificacoes.md` (tipos de comunicado). Branch: `item/91-portal-agenda`. Parte do que a #88, a #89 e a #90 deixaram: `PortalLayout`, `PortalShell.reduced`, `PortalStudents.cards`, `PortalTerms.requireAccepted`, `PortalHome` (consulta de aulas e `ROW_CLASS`), `PortalShell.REACH`, `PortalTag`, `PortalSectionPage` (marcador provisório da aba Agenda) e `RequireFullPortal`. Os canvas citados na issue não são lidos pelo agente (D9).

## 1. Objetivo

Dar ao aluno e ao responsável a aba Agenda do portal: as aulas do aluno escolhido numa semana, em grade (segunda a domingo) no computador e em lista do dia no celular, só consulta. A tela distingue turma principal, outros horários, aula avulsa, aula alterada (resumo e "Ver aviso"), aula cancelada ("Cancelada" e o motivo), dia sem aula ("Sem aula" e o motivo) e aula que já passou; alterna "Só minha turma" e "Todos os meus horários" e troca de semana.

Fora do escopo: a tela Avisos e o destaque do aviso aberto por "Ver aviso" (#93: aqui só o link com `?aviso=`); ver a agenda sem internet e a cópia local (RN-POR-11 da agenda é da Sprint 21, D8); presença, lista de espera, nome ou contato do professor (RN-POR-06, RN-POR-09); qualquer ação sobre a aula (marcar presença, cancelar, pedir troca); o link "Ver agenda completa" no Início, se não existir hoje.

## 2. Requisitos

- R1 (RF-POR-04, RN-POR-02): `GET /api/v1/portal/schedule` devolve, para o aluno escolhido, as aulas da semana (segunda a domingo) que ele pode ver: aulas de turma em que está matriculado, como turma principal ou outro horário, e aulas avulsas visíveis para a unidade ou para uma turma dele (mesma regra de alcance da #89, D1). Cada aula traz id, data, início, término, título, modalidade, unidade, tipo (`MAIN`, `OTHER`, `ADHOC`) e se já passou.
- R2 (RF-POR-04, D2): `scope=ALL` (padrão, "Todos os meus horários") traz turma principal, outros horários e avulsas; `scope=MAIN` ("Só minha turma") traz só as aulas da turma principal de cada modalidade ativa. O filtro é do servidor.
- R3 (RF-POR-04, RN-AGE-05): aula cancelada vem com `status` `CANCELLED`, `cancelReasonCode` (`TEACHER_UNAVAILABLE`, `HOLIDAY`, `ACADEMY_EVENT`, `OTHER`) e `cancelReason` (texto livre, só no `OTHER` ou copiado do dia sem aula); a tela mostra "Cancelada" e o motivo.
- R4 (RF-POR-04, RN-AGE-04, D3): aula alterada (editada à mão ou com substituto) vem com `changed` verdadeiro e o resumo em dados: `originalDate` e `originalStartsAt` (de onde saiu, quando mudou de data ou horário) e `substitute`. Sem nome nem contato do professor. A tela monta o texto do resumo ("Era em [data] às [hora]", "Professor substituto").
- R5 (RF-POR-04, D4): a aula alterada ou cancelada traz `noticeId` quando há comunicado enviado do tipo adequado que alcança o aluno e a turma da aula; a tela mostra "Ver aviso" levando a `/portal/avisos?aviso=<id>`. Sem comunicado, sem o link.
- R6 (RF-POR-04, RN-AGE-09): dia sem aula que vale para o aluno (unidade de uma modalidade ativa dele e, se o dia tem turmas, uma turma dele) vem em `noClassDays` com data, motivo e unidade; a tela marca o dia com "Sem aula" e o motivo, na grade e na lista.
- R7 (RF-POR-04, RN-AGE-03): "já passou" é calculado no servidor pelo relógio do fuso da unidade da aula; a tela distingue a aula passada (texto "Já aconteceu", sem depender só da cor).
- R8 (RF-POR-04, RN-POR-01, RN-POR-05): `studentId` opcional, padrão o primeiro cartão; aluno não ligado à conta ou de outra organização é 404; a resposta traz a lista de alunos ligados (id, nome, `self`) para o responsável escolher; gestão e Professor recebem 403, sem sessão 401, termo pendente 403 `TERM_PENDING`.
- R9 (RF-POR-04, D5): `week` opcional (qualquer data `yyyy-MM-dd`; o servidor usa a segunda-feira dela), padrão a semana de hoje no fuso da unidade de cadastro do aluno; data inválida é 400. A resposta traz `weekStart`, `weekEnd`, `today` e `timezone`.
- R10 (RN-POR-08, D6): com o portal reduzido (inativo e ex-aluno) a rota responde 200 com `reduced` verdadeiro e sem aulas nem dias sem aula; a tela já não abre (rota dentro de `RequireFullPortal`).
- R11 (RF-POR-04, D7): a tela `/portal/agenda` (aluno em `?aluno=`, semana em `?semana=`, escopo em `?escopo=`) mostra grade de 7 colunas a partir de 768 px e lista do dia abaixo disso, com abas dos dias da semana; botões "Semana anterior", "Hoje" e "Próxima semana"; alternância entre "Só minha turma" e "Todos os meus horários"; seletor de aluno quando há mais de um; legenda dos tipos de aula.
- R12 (RN-POR-09, RN-POR-06): a resposta e a tela não trazem presença, frequência, progresso, lista de espera, nome ou contato do professor, nem dado financeiro.
- R13 (RF-POR-08, padrão de tela): carregando, vazio (semana sem aulas), erro com "Tentar de novo" e sem permissão; a partir de 360 px, axe nos dois temas e todo texto em `pt-BR.json`.
- R14 (RNF-32): `docs/openapi.yaml` e o handler MSW refletem a rota nova.

## 3. Critérios de aceite

- CA1 (R1, R2): dado aluno com a turma principal de Jiu-Jitsu (terça), outro horário de Jiu-Jitsu (quinta) e uma aula avulsa da unidade, quando `GET /portal/schedule?week=<semana>`, então vêm as três com `kind` `MAIN`, `OTHER` e `ADHOC`; aula de turma em que ele não está, avulsa restrita a outra turma e aula de outra unidade não vêm.
- CA2 (R2): dado o mesmo aluno, quando `scope=MAIN`, então só a aula da turma principal; `scope` inválido é 400.
- CA3 (R3): dado aula cancelada com motivo `HOLIDAY` e outra com `OTHER` e texto, então vêm `CANCELLED` com o código e, na segunda, o texto; a tela mostra "Cancelada" e o motivo traduzido.
- CA4 (R4): dado aula movida de terça 19:00 para quarta 20:00 e aula com substituto, então `changed` é verdadeiro, `originalDate`/`originalStartsAt` trazem a origem da primeira e `substitute` verdadeiro na segunda; nenhum campo de professor na resposta; a tela mostra "Alterada" e o resumo.
- CA5 (R5): dado aula alterada com comunicado enviado `SCHEDULE_CHANGE` ou `TEACHER_CHANGE` da turma, e aula cancelada com `CANCELED_CLASS` ou comunicado de dia sem aula, então `noticeId` é o mais recente enviado; comunicado agendado (não enviado), de outra turma ou de tipo `GENERAL` não conta; sem comunicado `noticeId` é nulo e a tela não mostra "Ver aviso"; o link leva a `/portal/avisos?aviso=<id>`.
- CA6 (R6): dado dia sem aula da unidade para todas as turmas, e outro só para uma turma que o aluno não frequenta, então só o primeiro vem em `noClassDays` com motivo e unidade; a tela mostra "Sem aula" e o motivo no dia, na grade e na lista.
- CA7 (R7): dado aula de hoje que terminou e outra que ainda não, em unidade de fuso diferente do UTC, perto da virada do dia (`Pacific/Kiritimati`), então `past` vale pelo relógio do fuso da unidade; a tela mostra "Já aconteceu" só na passada.
- CA8 (R8): dado responsável com dois filhos, então `students` traz os dois (e "Você", se treina), o padrão é o primeiro cartão e `studentId` do segundo devolve a agenda dele; o responsável não pagador do menor também a vê.
- CA9 (R8): dado `studentId` de outra conta da mesma organização ou da organização B, então 404, sem distinguir; nada de outra conta ou organização na resposta; Administrador, Coordenador e Professor recebem 403; sem sessão, 401; termo pendente, 403 `TERM_PENDING`.
- CA10 (R9): dado `week` numa quarta, então `weekStart` é a segunda e `weekEnd` o domingo; sem `week`, a semana de hoje no fuso da unidade de cadastro (teste com `Pacific/Kiritimati` no domingo/segunda); `week` inválida é 400; uma aula no domingo e outra na segunda seguinte caem em semanas diferentes.
- CA11 (R10): dado aluno inativo com portal reduzido, então 200 com `reduced` verdadeiro e listas vazias; abrir `/portal/agenda` na tela leva ao Início (`RequireFullPortal`).
- CA12 (R12): dado qualquer resposta, então não há campo de professor, presença, frequência, progresso, lista de espera nem cobrança, e a tela não mostra esse texto.
- CA13 (R11): dado viewport de 1024 px, então a tela mostra a grade da semana com 7 colunas, aulas por horário e dias sem aula sombreados com texto; dado 360 px, a lista do dia com abas dos dias, começando em hoje (ou na segunda, se a semana não tem hoje), e "Sem aula" no dia marcado; sem rolagem horizontal da página.
- CA14 (R11): dado clicar em "Próxima semana", "Semana anterior" e "Hoje", então a leitura é refeita para a semana pedida e `?semana=` muda; dado alternar "Só minha turma", então refaz com `scope=MAIN`; dado trocar de aluno no seletor, então refaz com o aluno; a alternância e o seletor mantêm a semana.
- CA15 (R13): dado a tela carregando, com erro ("Tentar de novo" refaz a leitura), com 403, e semana sem aulas, então cada estado aparece; sem rolagem horizontal a 360 px e axe sem violação nos dois temas.
- CA16 (R14): `OpenApiContractTest` e `ContractRoutesTest` passam com a rota nova; o handler MSW responde no formato do OpenAPI, inclusive 403 para a gestão, aluno com aula alterada, cancelada, avulsa e dia sem aula, e responsável com dois filhos.

## 4. Plano técnico

- Dados: não muda (sem migração nem seed). Só leitura de `lesson`, `training_class`, `unit`, `modality`, `enrollment_modality`, `enrollment_modality_class`, `no_class_day`, `no_class_day_class`, `announcement`, `announcement_class`, `student`. Rota de leitura: não grava nem audita.
- API: `GET /api/v1/portal/schedule?studentId=<uuid>&week=<yyyy-MM-dd>&scope=ALL|MAIN`, só Aluno e Responsável, depois de `terms.requireAccepted`. Resposta `PortalSchedule`: `reduced`, `students [{id, name, self}]`, `studentId`, `scope`, `weekStart`, `weekEnd`, `today` (date), `timezone`, `lessons [{id, date, startsAt, endsAt, title, kind (MAIN|OTHER|ADHOC), status (SCHEDULED|CANCELLED), cancelReasonCode|null, cancelReason|null, changed, originalDate|null, originalStartsAt|null, substitute, past, noticeId|null, modality {id,name}, unit {id,name}}]`, `noClassDays [{date, reason, unit {id,name}}]` (um item por dia da semana coberto). Erros: 400 (`week` ou `scope` inválidos), 401, 403 (`FORBIDDEN`, `TERM_PENDING`), 404 (`NOT_FOUND`). Com `reduced`, as listas vêm vazias.
  - Consulta de aulas: parte da de `PortalHome.lessons` (alcance `ACTIVE_ROW`, `ROW_CLASS`, `visibility_class_id`), trocando a janela "ainda não terminou, 3 primeiras" por `lesson_date between :start and :end`. Tipo: avulsa (`class_id is null`) é `ADHOC`; `MAIN` quando `em.primary_class_id = l.class_id`; senão `OTHER`. `scope=MAIN` filtra para o `MAIN`. `past`: `(l.lesson_date + l.ends_at) <= (now at time zone u.timezone)`. `changed` é `l.substitute or l.manually_edited` (igual ao Início); `original_*` vêm das colunas da V26.
  - `noticeId` (D4): para aula não avulsa alterada, o comunicado enviado mais recente (`sent_at is not null`) de tipo `SCHEDULE_CHANGE` ou `TEACHER_CHANGE`; para cancelada, `CANCELED_CLASS` ou, se a aula tem `no_class_day_id`, o comunicado desse dia (`announcement.no_class_day_id`); em todos, alcançando a turma da aula (`target_kind = 'CLASS' and class_id = l.class_id` ou `CLASSES` em `announcement_class`) e o aluno (`PortalShell.REACH` sobre a matrícula ativa na unidade da aula); aula avulsa: sem `noticeId`. Subconsulta correlacionada com `order by sent_at desc, id limit 1`.
  - Dias sem aula: `no_class_day` da organização, com `starts_on <= :end and ends_on >= :start`, `unit_id` de uma matrícula ativa do aluno, e (sem linha em `no_class_day_class` ou com uma das turmas da matrícula ativa na unidade). Expandido por dia da semana no serviço.
  - Semana e relógio: `week` default `today` pelo fuso de `student.registered_unit_id` → `unit.timezone` (como a #90); segunda = `date.with(TemporalAdjusters.previousOrSame(MONDAY))`; `Clock` injetado.
- Interface: `usePortalSchedule(studentId, week, scope)` (a criar, `useResource`/estilo do `usePortalHome`, sem cópia local, D8); `PortalAgendaPage` (a criar) dentro de `PortalLayout`: título, seletor de aluno (`select`, como `PortalCardPage`), alternância "Só minha turma"/"Todos os meus horários" (grupo de botões com `aria-pressed`), navegação de semana, legenda, e os quatro estados; `PortalAgendaGrid` (a criar, tabela de 7 colunas por horário de início, visível de `md` para cima) e `PortalAgendaDayList` (a criar, abas dos dias e lista do dia, abaixo de `md`), ambas com `PortalLessonItem` (a criar) que desenha tags (`PortalTag`: Principal, Outro horário, Avulsa, Alterada, Cancelada, Já aconteceu), título, horário, modalidade e unidade, resumo da alteração, motivo do cancelamento e o link "Ver aviso". As duas visões ficam no DOM por CSS (`hidden md:block` / `md:hidden`) só se o teste de acessibilidade não duplicar conteúdo; senão uma escolha por `matchMedia` (decidir na construção, preferir a escolha por `matchMedia` para não duplicar texto no axe e no leitor de tela). Rota: trocar o `PortalSectionPage tab="schedule"` em `App.tsx` por `PortalAgendaPage` (continua dentro de `RequireFullPortal`). Datas com `agendaDates.ts` (`weekDays`, `addDays`, `mondayOf`), reusado da gestão. Textos no grupo `portalAgenda` de `pt-BR.json`.
- Segurança: perfil, vínculo e termo conferidos no servidor (`PortalStudents.cards`, `requireAccepted`, `PortalShell.reduced`); SQL só com parâmetros dentro de `OrganizationTransactions.execute` (RLS); `studentId` desconhecido é 404 sem distinguir de outra conta; sem nome ou contato do professor, sem dado financeiro nem de presença; texto livre (motivo) mostrado como texto, nunca como HTML.
- Arquivos a criar: `backend/src/main/java/br/com/ctcore/student/PortalSchedule.java` (serviço `view(who, studentId, week, scope)`); `backend/src/test/java/br/com/ctcore/student/PortalScheduleIT.java`; `frontend/src/features/portal/usePortalSchedule.ts`, `PortalAgendaPage.tsx`, `PortalAgendaGrid.tsx`, `PortalAgendaDayList.tsx`, `PortalLessonItem.tsx`, `PortalAgendaPage.test.tsx`; `frontend/src/mocks/portal-schedule.msw.test.ts`; `frontend/e2e/portal-agenda.spec.ts`.
- Arquivos a alterar (vistos no repositório): `backend/.../student/PortalStudentController.java` (`GET /schedule`, parâmetros `studentId`, `week`, `scope`); `backend/.../student/PortalHome.java` (expor `ACTIVE_ROW` e `ROW_CLASS` como package-private para reuso, sem mudar resultado; `PortalHomeIT` como rede); `docs/openapi.yaml` (`/portal/schedule`, `PortalSchedule`); `frontend/src/App.tsx` (rota `/portal/agenda`); `frontend/src/features/portal/PortalSectionPage.tsx` (a aba `schedule` deixa de ser provisória); `frontend/src/mocks/handlers.ts` (`/portal/schedule`); `frontend/src/i18n/pt-BR.json` (`portalAgenda`).
- Reuso: `PortalStudents.cards`, `PortalTerms.requireAccepted`, `PortalShell.reduced` e `PortalShell.REACH`, consulta e constantes de aula de `PortalHome`, `PortalLayout`, `PortalTag`, `Card`, `LoadingState`, `ErrorState`, `ForbiddenState`, `EmptyState`, `formatDate`, `agendaDates.ts` e os rótulos de dias da semana e de motivo de cancelamento do grupo `agenda` de `pt-BR.json` (conferir os nomes das chaves na construção).
- Riscos: (1) `manually_edited` pode marcar `changed` numa aula só editada sem mudar nada: o resumo cai no texto genérico "Aula alterada" quando `originalDate`, `originalStartsAt` e `substitute` não explicam. (2) A subconsulta de `noticeId` por aula pode pesar numa semana cheia; limite natural (poucas dezenas de aulas) e índice `announcement_by_class`; se lenta, ler os comunicados da semana de uma vez e casar em memória. (3) Comunicado "mais recente" pode não ser o da alteração desta aula (não há vínculo aula-comunicado no banco): aceito, D4. (4) Fusos diferentes entre unidades: `past` é por unidade da aula; `today` e a semana padrão são da unidade de cadastro. (5) Duas visões (grade e lista) duplicadas no DOM confundem o axe e o leitor de tela: usar `matchMedia`.

## 5. Tarefas

- [x] T1: `PortalSchedule` (aulas da semana com tipo, cancelada, alterada, `past`, `noticeId`, dias sem aula, `students`, `scope`, `week`, reduzido), expor as constantes de `PortalHome`, rota `GET /portal/schedule` e `/portal/schedule` no OpenAPI — cobre CA1, CA2, CA3, CA4, CA5, CA6, CA7, CA8, CA9, CA10, CA11 (servidor), CA12, CA16 (servidor) — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test='PortalScheduleIT,PortalHomeIT' -Dtest='OpenApiContractTest,ContractRoutesTest' -Dsurefire.failIfNoSpecifiedTests=false -Djacoco.skip=true` (Docker ativo)
- [x] T2: textos `portalAgenda` em `pt-BR.json`, handler MSW de `/portal/schedule` e `usePortalSchedule` — cobre CA16 (MSW) — prova: em `frontend/`, `npm test -- portal-schedule.msw.test.ts`
- [x] T3: `PortalLessonItem`, `PortalAgendaGrid` e `PortalAgendaDayList` (tags, resumo, motivo, "Ver aviso", "Sem aula", "Já aconteceu") — cobre CA3 (tela), CA4 (tela), CA5 (tela), CA6 (tela), CA7 (tela), CA13 — prova: em `frontend/`, `npm test -- PortalAgendaPage`
- [x] T4: `PortalAgendaPage` (seletor de aluno, alternância de escopo, navegação de semana, legenda, quatro estados) e rota em `App.tsx`; ajuste dos testes que contam o marcador da aba — cobre CA8 (tela), CA11 (rota), CA12 (tela), CA14, CA15 — prova: em `frontend/`, `npm test -- PortalAgendaPage PortalLayout`
- [x] T5: ponta a ponta a 360 px e a 1024 px (aluno com aula alterada e cancelada, "Ver aviso", dia sem aula, troca de semana e de escopo, responsável trocando de aluno, portal reduzido sem a aba) com axe nos dois temas — cobre CA11, CA13, CA14, CA15 (ponta a ponta) — prova: em `frontend/`, `npm run e2e -- portal-agenda`
- [x] T6: regressão completa — não cobre critério: confere que nada do resto quebrou (`PortalHome` mexido) — prova: `./backend/mvnw -f backend/pom.xml verify`; depois, em `frontend/`, `npm run lint && npm run typecheck && npm run build && npm run test:cov && npm run e2e`

## 6. Decisões

- D1: a agenda do aluno usa a mesma regra de alcance da #89 (turma principal, outros horários da matrícula ativa na unidade e avulsas da unidade toda ou da turma do aluno), para a aba e o Início nunca divergirem. Alternativa: regra nova só da agenda, descartada por duplicar.
- D2 (muda o comportamento): o padrão é "Todos os meus horários"; "Só minha turma" mostra apenas a turma principal de cada modalidade ativa (sem outros horários nem avulsas). O card não diz o padrão; o padrão amplo evita esconder aula. Alternativa: padrão "Só minha turma", descartada por esconder horários e avulsas na primeira abertura. → RF-POR-04
- D3: o "resumo" da aula alterada é dado estruturado (de onde saiu e se tem substituto), montado como texto na tela, sem o nome do professor (RN-POR-06 manda ocultar contato do professor; o nome fica fora por prudência até o dono decidir). Não há resumo gravado no banco e criar um mudaria a gestão, fora do item. Alternativa: mostrar o nome do substituto, descartada por não estar no card.
- D4 (muda o comportamento): "Ver aviso" aponta para o comunicado enviado mais recente do tipo certo que alcança a turma da aula (`SCHEDULE_CHANGE`/`TEACHER_CHANGE` para alterada; `CANCELED_CLASS` ou o do dia sem aula para cancelada), pois não há vínculo gravado entre a aula e o comunicado. Pode apontar para um aviso de outra alteração da mesma turma; sem comunicado, sem o link. O link é `/portal/avisos?aviso=<id>`: a tela de Avisos e o destaque são da #93 (hoje a rota é o marcador provisório). Alternativa: gravar o vínculo aula-comunicado, descartada por mexer na gestão e exigir migração. → RF-POR-04
- D5: a semana é segunda a domingo (como a gestão), o padrão é a semana de hoje no fuso da unidade de cadastro e o aluno navega por qualquer semana; o servidor não limita a janela, e semana sem aula mostra o estado vazio. Alternativa: limitar às 8 semanas geradas, descartada por esconder semanas passadas que já existem.
- D6: com o portal reduzido a rota responde 200 vazia (como o Início), e a tela fica atrás de `RequireFullPortal`, que já existe para `/portal/agenda`; o servidor não recusa com 403 para não quebrar a leitura de quem perdeu a modalidade durante a sessão.
- D7: grade a partir de 768 px (`md`) e lista do dia abaixo; no celular, abas dos dias da semana e começa em hoje. A escolha entre as duas é feita por `matchMedia`, para não duplicar o conteúdo no DOM. O estado (aluno, semana, escopo) fica na URL (`?aluno=`, `?semana=`, `?escopo=`), mesmo padrão da carteirinha; não é gravado.
- D8: a agenda sem internet (RN-POR-11, "última versão da agenda") fica para a Sprint 21 junto do service worker; esta entrega não grava cópia local, diferente da carteirinha. O card #91 não a pede. Alternativa: copiar o padrão `localStorage` da #90, descartada por escopo e por o PWA da Sprint 21 tratar todas as telas de uma vez.
- D9: os canvas citados na issue não são acessíveis ao agente; o layout segue `PortalLayout`, a agenda da gestão e a especificação, e o dono confere o visual na revisão da sprint (como a D9 da #89 e da #90).
- D10: sem recurso desligado por organização (parte básica do portal) e sem nota de versão (o arquivo não existe no repositório); a descrição do PR leva: "Agenda do aluno no portal, com semana em grade ou lista do dia, aulas alteradas, canceladas e dias sem aula".
- Suposição: a conta com `unit.timezone` sempre preenchido e `student.registered_unit_id` sempre apontando para unidade existente (como na #90). Se estiver errada, a semana padrão cai no fuso padrão `America/Sao_Paulo`.
- Suposição: as chaves de dia da semana e de motivo de cancelamento do grupo `agenda` de `pt-BR.json` servem para o portal; se não, o grupo `portalAgenda` ganha as próprias.

## Construção

- T1 — arquivos: `backend/.../student/PortalSchedule.java` (novo), `PortalStudentController.java` (`GET /schedule`), `PortalHome.java` (`ACTIVE_ROW` e `ROW_CLASS` package-private), `docs/openapi.yaml` (`/portal/schedule`, `PortalSchedule`), `PortalScheduleIT.java` (novo, 6 testes) — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test='PortalScheduleIT,PortalHomeIT' -Dtest='OpenApiContractTest,ContractRoutesTest' -Dsurefire.failIfNoSpecifiedTests=false -Djacoco.skip=true` → 7 rápidos + 13 de integração, 0 falhas.
- T2 — arquivos: `frontend/src/i18n/pt-BR.json` (`portalAgenda`), `mocks/handlers.ts` (`/portal/schedule`), `features/portal/usePortalSchedule.ts`, `mocks/portal-schedule.msw.test.ts` — prova: `npm test -- portal-schedule.msw.test.ts` → 4 passaram.
- T3 — arquivos: `PortalLessonItem.tsx`, `PortalAgendaGrid.tsx`, `PortalAgendaDayList.tsx`, `PortalAgendaPage.test.tsx` — prova: `npm test -- PortalAgendaPage` → passou (junto da T4).
- T4 — arquivos: `PortalAgendaPage.tsx`, `App.tsx` (rota), `PortalSectionPage.tsx` (aba `schedule` removida), `PortalAgendaPage.test.tsx` — prova: `npm test -- PortalAgendaPage PortalLayout` → 27 passaram (os testes do `PortalLayout` não precisaram de ajuste).
- T5 — arquivos: `frontend/e2e/portal-agenda.spec.ts` — prova: `npm run e2e -- portal-agenda` → 5 passaram (360 px e 1024 px, claro e escuro, reduzido).
- T6 — Regressão: `./backend/mvnw -f backend/pom.xml verify` → 823 testes, 0 falhas, BUILD SUCCESS (JaCoCo incluso); em `frontend/`, `npm run lint && npm run typecheck && npm run build && npm run test:cov` → verdes (153 arquivos, 1310 testes, linhas 96,73%); `npm run e2e` → 60 passaram.

Desvios:
- A escolha entre grade e lista ficou no hook `useWide` dentro de `PortalAgendaPage.tsx` (por `matchMedia`, como o plano mandava), sem arquivo próprio.
- `noticeId` de aula cancelada por dia sem aula casa pelo `no_class_day_id` da aula sem exigir alcance por turma (o comunicado do dia pode ser da unidade); o alcance sobre o aluno (`PortalShell.REACH`) continua exigido.
- Testes de tela e MSW usam semanas fixas; o MSW devolve a semana pedida, mas o teste de navegação do componente usa uma resposta fixa (`weekStart` não acompanha `week`).
- O e2e do portal reduzido abre `/portal/agenda` por `history.pushState` (um `page.goto` recarrega o app e perde a sessão do MSW).

Não é do item: nenhuma mudança fora do escopo encontrada.

## Verificação

### Rodada 1 — OK — 2026-10-27, estado ea55f3ac94679c4ab4e7f2456b845bf4dfaa52dd

| Critério | Status | Evidência |
|---|---|---|
| CA1, CA2 | PASSOU | `PortalScheduleIT` (kindsAndScope): tipos, turma alheia, scope=MAIN, scope inválido 400 |
| CA3, CA4, CA5 | PASSOU | `PortalScheduleIT` (cancelledChangedNotice, noClassDayNotice, inclui GENERAL e agendado); `PortalAgendaPage.test.tsx` na suíte do front (153 arquivos, 1310 testes) |
| CA6 | PASSOU | `PortalScheduleIT` (noClassDays); e2e `portal-agenda` |
| CA7, CA10 | PASSOU | `PortalScheduleIT` (weekAndZone, Pacific/Kiritimati, week inválida 400) |
| CA8, CA9, CA11 | PASSOU | `PortalScheduleIT` (access: 404, 403, 401, TERM_PENDING, reduzido); e2e do reduzido |
| CA12 | PASSOU | recorde `Lesson` e `View` sem campo de professor, presença ou cobrança (lido no diff) |
| CA13, CA14, CA15 | PASSOU | e2e `portal-agenda` a 360 e 1024 px, claro e escuro, axe, troca de semana/escopo/aluno; testes de tela |
| CA16 | PASSOU | `OpenApiContractTest` e `ContractRoutesTest` dentro do `verify`; `portal-schedule.msw.test.ts` |

Comandos:
- `./backend/mvnw -f backend/pom.xml verify` → 823 testes, 0 falhas, BUILD SUCCESS (JaCoCo incluso)
- em `frontend/`: `npm run lint && npm run typecheck && npm run build && npm run test:cov` → verdes, 153 arquivos, 1310 testes
- `npm run e2e` → 60 passaram
Instáveis: nenhum.
Achados:
- sugestão, código, `PortalSchedule.noClassDays` — dois dias sem aula da mesma unidade sobrepostos no mesmo dia geram dois itens; a tela os mostra ambos, aceitável.

## Entrega

Muda: a agenda do portal em `/portal/agenda` mostra a semana do aluno (ou do escopo) com aulas, cancelamentos, avisos de dia sem aula e aulas agendadas. Em 768 px ou mais é uma grade com a lista do dia abaixo; no celular, abas dos dias da semana começando em hoje. Back-end: `GET /portal/schedule` (`PortalSchedule`), que só expõe tipo, horário, turma e situação da aula, sem professor, presença ou cobrança. Contrato em `docs/openapi.yaml` e handlers MSW.

Verificação: rodada 1 `OK` (estado `ea55f3ac`). `verify` com 823 testes; front com lint, typecheck, build, `test:cov` (1310 testes) e `e2e` (60 passaram); axe e layout a 360 e 1024 px nos dois temas.

Decisões: semana de segunda a domingo, padrão a semana de hoje no fuso da unidade, navegação livre por semana e o servidor não limita a janela (D5). Grade ou lista escolhidas por `matchMedia`, sem duplicar o conteúdo no DOM; aluno, semana e escopo ficam na URL (D7).

Limitações: dois dias sem aula da mesma unidade sobrepostos no mesmo dia geram dois itens na tela (achado aceito).
