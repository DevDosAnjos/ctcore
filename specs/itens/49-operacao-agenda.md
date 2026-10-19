# 49 — RF-AGE-01 · Operação › Agenda

Origem: issue #49 (Sprint 05); `docs/especificacao/06-turmas-e-agenda.md` (RF-AGE-01, RN-AGE-01, RN-AGE-03, RN-AGE-08, RN-AGE-13, "Dados"). Branch: `item/49-operacao-agenda`. Parte de `training_class` e `ClassScheduleChange` (#48), da permissão `AGENDA_VIEW`/`AGENDA_EDIT` (`PermissionTable`) e do menu "agenda" → `/agenda` (já em `menuCatalog.ts`).

## 1. Objetivo

Hoje não existem aulas: só turmas com horários semanais. Este item cria a aula no banco, gera sozinho 8 semanas de aulas a partir das turmas ativas e entrega a tela Operação › Agenda (grade da semana no computador, lista do dia no celular, atalhos Hoje e Esta semana, filtros por professor e modalidade) para Coordenador, Administrador e Professor. Também ativa o ponto `ClassScheduleChange` de #48 e a "próxima aula" do painel da turma.

Fora do escopo: painel da aula (#50), editar, cancelar e substituir aula (#51; aqui só o cálculo de "pode editar/cancelar" de RN-AGE-08), aula avulsa (#52), cadastrar, desfazer e avisar dias sem aula e feriados (#53; aqui só a tabela e a leitura), número de alunos e aulas experimentais (Sprints 07 e 18), comunicados (Sprint 09), notas de versão (Sprint 15). Não há recurso liberado aos poucos nesta sprint.

## 2. Requisitos

- R1 (RN-AGE-01): tarefa diária (agendador com trava, #4) mantém 8 semanas (56 dias, contando hoje, no fuso de cada unidade) de aulas para toda turma `ACTIVE` de unidade ativa, uma por horário semanal da turma (`training_class_schedule`); é idempotente (rodar duas vezes não duplica) e nunca altera aula já existente.
- R2 (RN-AGE-01): aula gerada em data coberta por um dia sem aula da unidade nasce `Cancelada`, com o motivo do dia como motivo do cancelamento.
- R3 (RN-AGE-01, D3): criar turma (#48) gera logo as aulas das 8 semanas dela, sem esperar a tarefa diária.
- R4 (RN-AGE-02, D4): `ClassScheduleChange.changed` passa a refazer as aulas futuras (a partir de hoje, no fuso da unidade) da turma: apaga as que estão `Agendada`, não foram alteradas à mão e não estão canceladas, e gera as do novo horário; aula alterada à mão ou cancelada é mantida.
- R5 (RN-AGE-03): a situação da aula é calculada na leitura pelo relógio no fuso da unidade: antes do início `SCHEDULED`, do início (inclusive) ao término (exclusive) `IN_PROGRESS`, depois `COMPLETED`; `CANCELLED` só existe se alguém (ou o dia sem aula) cancelou e vale sobre qualquer horário. Nenhuma situação calculada é gravada.
- R6 (RF-AGE-01): `GET /api/v1/agenda?from=&to=&unitId=&teacherId=&modalityId=` devolve as aulas do período (datas `yyyy-MM-dd`, `to` ≥ `from`, no máximo 31 dias) e os dias sem aula da unidade; cada aula traz id, data, início, término, situação, motivo do cancelamento (se cancelada), turma, unidade, modalidade, professor da aula, marca de substituição e `canEdit`/`canCancel`. Também devolve `units` do alcance, a `unit` escolhida (sem `unitId`, a primeira por nome do alcance) e as opções de filtro `teachers` e `modalities` da unidade.
- R7 (RF-AGE-01): filtros por professor (professor da aula) e por modalidade; combináveis; id de outra organização ou desconhecido devolve lista vazia, sem erro e sem vazar existência.
- R8 (RN-AGE-08, D5): `canCancel` é falso para aula concluída e para cancelada; `canEdit` é falso para aula cancelada; para aula concluída é falso para Professor e, para Administrador e Coordenador, só é verdadeiro nos 30 dias seguintes ao término; Professor só tem `canEdit`/`canCancel` verdadeiro com `AGENDA_EDIT` efetivo e em aula de turma dele (titular ou auxiliar) ou em que é o professor da aula.
- R9 (permissão): `AGENDA_VIEW` conferida no servidor: Coordenador vê todas as unidades; Administrador só as dele (unidade fora do alcance: 404); Professor vê só as aulas das turmas dele (titular ou auxiliar, via `teacher.account_id`) ou em que é o professor da aula; Aluno e Responsável 403; sem sessão 401. Parâmetro inválido (datas, período maior que 31 dias, UUID) dá 400 com o campo.
- R10 (isolamento): organização só da sessão; filtro no código e RLS nas tabelas novas `lesson` e `no_class_day`; a tarefa diária roda cada organização no seu escopo e nunca mistura dados.
- R11 (auditoria): a geração grava `lesson.generated` por organização e execução com a quantidade criada e a origem (tarefa diária ou turma criada), só quando criou aulas; a regeneração por mudança de horário grava `lesson.regenerated` com turma, apagadas e criadas; sem dado pessoal.
- R12 (RN-AGE-13, RN-TUR-06): nenhum campo, coluna ou texto novo cita lista de espera, presença ou frequência.
- R13 (tela, desktop): `/agenda` mostra a grade da semana (segunda a domingo por colunas, horários por linhas), cada aula com turma, professor e cor da modalidade; aula cancelada riscada com o rótulo "Cancelada"; dia sem aula sombreado com "Sem aula" e o motivo; aula com selo "Substituição" quando `substitute`; atalhos "Hoje" e "Esta semana"; setas para semana anterior e próxima; filtros por professor e modalidade; seletor de unidade quando há mais de uma.
- R14 (tela, celular): até 767 px a mesma página mostra a lista do dia, com setas para dia anterior e seguinte, os mesmos atalhos, filtros e marcações (riscada com "Cancelada", "Sem aula" com motivo).
- R15 (tela, geral): quatro estados (carregando, vazio — "Nenhuma aula neste período", erro com "Tentar de novo", sem permissão), 360 px sem rolagem horizontal da página, axe nos dois temas, textos em `pt-BR.json`, cor nunca é a única indicação (turma e modalidade também em texto).
- R16 (RF-TUR-02): `TrainingClasses.nextClass` devolve a próxima aula não cancelada da turma (data e horário) a partir de agora no fuso da unidade, e o painel da turma a mostra; sem aula, continua nulo.
- R17 (contrato e dados): `docs/openapi.yaml` e os handlers MSW refletem a rota; migração Flyway cria `lesson` e `no_class_day` com RLS; o seed ganha um dia sem aula e continua válido (`SeedDataIT`).

## 3. Critérios de aceite

- CA1 (R1): dado organização com turma ativa de dois horários e turma desativada, quando a tarefa roda, então existe uma aula por horário em cada data dos próximos 56 dias que cai nesses dias da semana, com o professor titular, e nenhuma da turma desativada; rodar de novo não cria nada (contagem igual); no dia seguinte (relógio avançado) só entra o dia novo.
- CA2 (R1, R10): dado duas organizações em fusos diferentes, quando a tarefa roda, então "hoje" de cada unidade é o dela e cada aula fica na organização certa; com `ctcore_app`, nenhuma linha de outra organização é vista.
- CA3 (R2): dado dia sem aula da unidade cobrindo uma data futura, quando as aulas são geradas, então a aula daquela data nasce `CANCELLED` com o motivo do dia, e as demais datas ficam `SCHEDULED`.
- CA4 (R3): dado Coordenador que cria uma turma pelo `POST /classes`, então as aulas das 8 semanas dela existem logo após a resposta.
- CA5 (R4): dado turma com aulas, uma aula futura marcada como alterada à mão e outra cancelada, quando o horário da turma muda (`PUT`), então as demais aulas futuras `Agendada` passam para o novo horário, a alterada à mão e a cancelada permanecem, as passadas e as de hoje já iniciadas não mudam, e há `lesson.regenerated`; `PUT` com os mesmos horários não mexe em nenhuma aula.
- CA6 (R5): dado relógio fixado e unidade em `America/Sao_Paulo`, então aula às 19:00–20:00 é `SCHEDULED` às 18:59, `IN_PROGRESS` às 19:00 e às 19:59, `COMPLETED` às 20:00; uma unidade em outro fuso muda a situação no instante dela; aula cancelada é `CANCELLED` em qualquer horário.
- CA7 (R6, R7): dado aulas na semana, quando chama `GET /agenda` com `from`/`to` de uma semana, então devolve só as aulas do período, ordenadas por data e início, com os campos de R6, os dias sem aula do período com motivo, `units`, `unit`, `teachers` e `modalities`; com `teacherId` ou `modalityId` só as que batem, com os dois só as que batem nos dois; com id de outra organização lista vazia.
- CA8 (R8): dado aulas passada (concluída há 5 dias), concluída há 40 dias, futura e cancelada, então Administrador tem `canEdit` verdadeiro e `canCancel` falso na primeira, `canEdit` falso na segunda, verdadeiros na futura e ambos falsos na cancelada; Professor tem ambos falsos na concluída; Professor sem `AGENDA_EDIT`, ou em aula de turma de outro professor, tem ambos falsos mesmo na futura; Professor com `AGENDA_EDIT` em aula da turma dele tem ambos verdadeiros.
- CA9 (R9): dado Aluno e Responsável, então 403; sem sessão 401; Administrador de A com `unitId` de unidade de B: 404 e sem `unitId` recebe a primeira unidade dele; Coordenador vê todas as unidades; Professor só vê as aulas das turmas dele ou em que é professor da aula; `from` inválido, `to` antes de `from` e período de 32 dias dão 400 com o campo.
- CA10 (R10): dado duas organizações, então `unitId`, `teacherId` e `modalityId` de B chamados pela sessão de A não devolvem nada de B; nenhuma linha de `lesson` ou `no_class_day` de B é visível pela sessão de A nem pelo papel `ctcore_app` sem escopo.
- CA11 (R11): dado geração que criou aulas, então há um registro `lesson.generated` por organização com a contagem; execução que não cria nada não grava; mudança de horário grava `lesson.regenerated`; nenhum registro tem dado pessoal.
- CA12 (R12): dado o esquema do banco, o corpo bruto da resposta e `pt-BR.json`, então nenhuma tabela, coluna, campo ou texto novo menciona lista de espera, presença ou frequência.
- CA13 (R13): dado a página em 1280 px com aulas de duas modalidades, uma aula cancelada, um dia sem aula e uma substituição, então vê a grade com turma, professor e modalidade (texto e cor) em cada aula, a cancelada riscada com "Cancelada", a coluna do dia sombreada com "Sem aula" e o motivo, o selo "Substituição"; "Hoje" e "Esta semana" voltam à semana atual; as setas trocam a semana e pedem o novo período; escolher professor ou modalidade refaz a consulta com o filtro; com mais de uma unidade aparece o seletor.
- CA14 (R14): dado 360 px, então vê só o dia atual em lista, as setas trocam o dia (e cruzam a semana sem nova tela), "Hoje" volta ao dia atual, "Esta semana" leva à semana atual, e cancelada e "Sem aula" aparecem como no desktop; dia sem aulas mostra o estado vazio.
- CA15 (R15): dado carregando, vazio, erro 500 (com "Tentar de novo" que refaz) e 403, então cada um aparece com seu texto; a página não rola na horizontal a 360 px e o axe não tem violações nos temas claro e escuro.
- CA16 (R16): dado turma com aulas futuras, quando abre o painel, então "próxima aula" mostra a primeira aula não cancelada com término depois de agora; sem aulas ou só canceladas, continua vazio.
- CA17 (R17): `OpenApiContractTest` e `ContractRoutesTest` passam com a rota nova; testes MSW cobrem `/agenda` no formato do servidor; `SeedDataIT` passa com o seed novo.
- CA18 (R6, R13): ponta a ponta `@critical` "Coordenador abre a Agenda": entra por Operação › Agenda, vê a grade da semana com ao menos uma aula e uma aula cancelada ou "Sem aula", troca de semana e volta por "Hoje".

## 4. Plano técnico

- Dados: migração `V25__lesson_agenda.sql` (a criar): `lesson` (id, organization_id, unit_id, class_id nulo para a avulsa de #52, `lesson_date`, `starts_at`, `ends_at`, `teacher_id`, `substitute` boolean, `cancelled` boolean, `cancel_reason`, `staff_note`, `manually_edited` boolean, `created_at`; FKs compostas por organização como `training_class`; índice único parcial `(class_id, lesson_date, starts_at)` onde `class_id is not null`; índice `(organization_id, unit_id, lesson_date)`) e `no_class_day` (id, organization_id, unit_id, `starts_on`, `ends_on`, `reason`, `origin`, `notice_status` padrão "Ainda não avisado", `created_at`), ambas com RLS forçada como em V24; reversão manual no cabeçalho (`drop table`). A situação calculada não é gravada. Função `SECURITY DEFINER` para a tarefa listar organizações ativas sem escopo (a tabela `organization` tem RLS forçada); a construção confere que o dono da função ignora RLS e a cobre com teste. Seed novo `infra/seed/11-agenda.sql` (`on conflict do nothing`): um dia sem aula por unidade semeada; aulas vêm da tarefa.
- API: `GET /agenda` (200 `Agenda`; 400, 401, 403, 404), cookie como as demais, sem escrita; entra em `docs/openapi.yaml` no mesmo PR com `Agenda`, `AgendaLesson`, `NoClassDay`. Sem rota de escrita.
- Back-end, pacote novo `br.com.ctcore.agenda` (a criar): `LessonGenerator` (gera por turma ou organização, idempotente com `on conflict do nothing`, fuso por `unit.timezone`, usa `Clock` de `ClockConfig`, `OrganizationTransactions.provision`), `LessonGenerationJob` (`@Scheduled` diário + `@SchedulerLock`, no padrão de `mail/EmailRetryJob`), `Agenda` (consulta, situação por relógio, `canEdit`/`canCancel`, alcance como em `TrainingClasses.list`) e `AgendaController` (`GET /api/v1/agenda`, `AGENDA_VIEW` via `Permissions.effective`). A alterar: `trainingclass/ClassScheduleChange.java` (hoje vazio, passa a regenerar), `TrainingClassWrite.create` (chama o gerador, R3), `TrainingClasses.nextClass` (hoje devolve nulo, R16). Auditoria por `AuditService`/`AuditEntry` como `TrainingClassWrite`; a construção confere como registrar ator de sistema na tarefa. Reusa `Permission.AGENDA_VIEW/AGENDA_EDIT`, `PermissionTable`, `ApiValidationException`, `ApiStatusException`.
- Interface: a criar `frontend/src/features/agenda/AgendaPage.tsx`, `AgendaWeekGrid.tsx`, `AgendaDayList.tsx`, `AgendaFilters.tsx`, `useAgenda.ts` e testes; a alterar `App.tsx` (rota `/agenda`), `mocks/handlers.ts` (+ `agenda.msw.test.ts`), `i18n/pt-BR.json` (`agenda.*`), `ClassPanelPage.tsx` (só se a próxima aula precisar de ajuste). Cor da modalidade: a tabela `modality` não tem cor; a cor é derivada do id da modalidade por uma paleta fixa no front (D2). Semana de segunda a domingo. Reusa os padrões de `ClassesPage.tsx`/`ClassFilters.tsx` (estados, filtros) e os componentes de `components/`.
- Segurança: organização, perfil, unidades e professor vêm do principal; parâmetros ligados; Professor filtrado no servidor, nunca só no front; sem dado pessoal na resposta além do nome do professor; `canEdit`/`canCancel` são dicas para #51, que confere de novo ao gravar.
- Riscos: (1) listar organizações sem escopo exige a função de banco (R10); (2) fuso: "hoje" e 56 dias são por unidade, não pelo servidor; (3) volume: 8 semanas × turmas × horários por organização, inserção em lote por turma; (4) regerar não pode tocar aula alterada à mão (a coluna `manually_edited` nasce aqui para #51 gravar).

## 5. Tarefas

Duas partes: servidor (T1 a T4) e tela (T5 a T7).

- [x] T1: migração `V25` (`lesson`, `no_class_day`, RLS, função de organizações), seed `11-agenda.sql`, `LessonGenerator` e `LessonGenerationJob`, gatilho em `TrainingClassWrite.create` e auditoria da geração, com testes (`LessonGenerationIT`: 56 dias, idempotência, fuso, desativada, dia sem aula cancela, isolamento e RLS) — cobre CA1, CA2, CA3, CA4, CA10, CA11 — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test=LessonGenerationIT` e `-Dit.test=SeedDataIT`.
- [x] T2: `ClassScheduleChange` regenera as aulas futuras sem tocar nas alteradas à mão ou canceladas, com auditoria — cobre CA5, CA11 — prova: `verify -Dit.test=LessonGenerationIT`.
- [x] T3: `Agenda` e `AgendaController` (`GET /agenda`, situação por relógio, filtros, alcance por perfil, `canEdit`/`canCancel`) com testes (`AgendaIT`: período, filtros, perfis, Professor próprio, 400, 404, isolamento, sem lista de espera nem presença; teste unitário da situação e de RN-AGE-08) — cobre CA6, CA7, CA8, CA9, CA10, CA12 — prova: `verify -Dit.test=AgendaIT` e `./backend/mvnw -f backend/pom.xml test -Dtest='Lesson*Test,Agenda*Test'`.
- [x] T4: `TrainingClasses.nextClass` com a próxima aula e teste; OpenAPI (`/agenda`, `Agenda`, `AgendaLesson`, `NoClassDay`) e handlers MSW com teste — cobre CA16, CA17 — prova: `./backend/mvnw -f backend/pom.xml test -Dtest='OpenApiContractTest,ContractRoutesTest'`, `verify -Dit.test=TrainingClassWriteIT` e `npx vitest run src/mocks` em `frontend/`.
- [x] T5: `useAgenda`, `AgendaPage`, `AgendaWeekGrid` e `AgendaFilters`, rota `/agenda`, textos `agenda.*`, quatro estados, atalhos e filtros — cobre CA13, CA15 — prova: `npx vitest run src/features/agenda` (inclui axe nos dois temas).
- [x] T6: `AgendaDayList` (celular, 360 px, setas de dia, "Hoje", "Esta semana") e teste de tela — cobre CA14, CA15 — prova: `npx vitest run src/features/agenda`.
- [x] T7: e2e `@critical` "Coordenador abre a Agenda" e regressão completa (a regressão não cobre critério: confere cobertura e os 100% nos pacotes exigidos) — cobre CA18 — prova: `./backend/mvnw -f backend/pom.xml verify` e, em `frontend/`, `npm run lint && npm run typecheck && npm run build && npm run test:cov && npm run e2e`.

## 6. Decisões

- D1 (muda o comportamento): `lesson` e `no_class_day` nascem aqui. O card pede "dia sem aula já cadastrado gera a aula como Cancelada" e a grade mostra "Sem aula", e nada hoje guarda esses dados; cadastrar e desfazer dias sem aula continua em #53. Sem tabela de turmas afetadas, o dia sem aula vale para todas as turmas da unidade (padrão da spec); #53 acrescenta o recorte. Alternativa: esperar #53; descartada porque a leitura do card não ficaria testável.
- D2: a cor da modalidade vem de uma paleta fixa no front, escolhida pelo id da modalidade, porque a tabela não tem cor e o card não pede cadastro de cor. Alternativa: coluna de cor na modalidade; descartada por ser regra de catálogo da plataforma. Suposição: se o dono quiser escolher cores, vira item novo.
- D3: as aulas da turma recém-criada são geradas na hora (R3), para a agenda não ficar vazia até a tarefa da noite. Alternativa: só a tarefa diária; descartada por parecer defeito ao usuário.
- D4 (muda o comportamento): o contrato de RN-AGE-02 de #48 passa a valer: mudar horário apaga e refaz só aulas futuras `Agendada`, não canceladas e não alteradas à mão; aula de hoje já iniciada não é tocada. O comunicado "Avisar a turma" segue só registrado (Sprint 09).
- D5 (muda o comportamento): RN-AGE-08 aparece como `canEdit`/`canCancel` na resposta: concluída nunca é cancelada; só Administrador e Coordenador editam concluída, até 30 dias; aula cancelada não é editável (precisa de regra de reabrir, que a spec não traz). Suposição: a spec diz "Administrador"; o Coordenador, que tem a mesma permissão `AGENDA_EDIT` completa, segue o mesmo limite. Se só o Administrador deve corrigir, muda o `canEdit` do Coordenador. A gravação em si, com motivo e auditoria, é de #51.
- D6: a situação de RN-AGE-03 é calculada na leitura, não gravada, para não depender de tarefa de atualização nem divergir do relógio; só o cancelamento é dado.
- D7: a rota única `GET /agenda` devolve também unidades, filtros e dias sem aula, para não depender das permissões de outras telas (`CLASSES_MANAGE`, professores, modalidades), que Professor não tem. Sem `unitId`, usa a primeira unidade do alcance por nome.
- D8: Professor vê as aulas das turmas dele (titular ou auxiliar) e as em que é o professor da aula (substituto), conforme o escopo "turmas" da tabela de permissões; o vínculo é `teacher.account_id`.
- D9: RN-AGE-13 não gera tela nem campo: a agenda não tem lista de espera nem controla vagas; é conferida por teste de ausência (CA12). Acima da capacidade só há o aviso já existente na turma.
- D10: a geração diária grava auditoria por organização e execução, não por aula, para não encher a trilha; só quando cria aulas.
- D11: os canvas `Agenda.dc.html` e `GMAgenda.dc.html` não estão acessíveis aqui; a tela segue os padrões do projeto e o dono confere o layout no PR (como #46/D8 e #48/D9).

## Construção

- T1 — arquivos: `backend/src/main/resources/db/migration/V25__lesson_agenda.sql`, `infra/seed/11-agenda.sql`, `agenda/LessonGenerator.java`, `agenda/LessonGenerationJob.java`, `trainingclass/TrainingClassWrite.java` (gatilho R3), testes `agenda/AgendaBase.java`, `agenda/LessonGenerationIT.java`, limpeza de `lesson`/`no_class_day` em `SeedDataIT` e nos IT de turma — prova: `verify -Dit.test='LessonGenerationIT,AgendaIT'` e `-Dit.test='TrainingClass*IT,SeedDataIT'` → 8 e 38 testes, 0 falhas
- T2 — arquivos: `trainingclass/ClassScheduleChange.java` (regenera futuras agendadas, não editadas nem canceladas; `lesson.regenerated`) — prova: `LessonGenerationIT.createAndRegenerate` → passou
- T3 — arquivos: `agenda/Agenda.java`, `agenda/AgendaController.java`, `agenda/AgendaIT.java`, `agenda/AgendaRulesTest.java` — prova: `verify -Dit.test=AgendaIT` (4) e `test -Dtest=AgendaRulesTest` (4) → passou
- T4 — arquivos: `TrainingClasses.nextClass`, `docs/openapi.yaml` (`/agenda`, `Agenda`, `AgendaLesson`, `NoClassDay`), `mocks/handlers.ts`, `mocks/agenda.msw.test.ts`, `TrainingClassDetailIT.nextClass` — prova: `test -Dtest='OpenApiContractTest,ContractRoutesTest'` (7), `TrainingClassDetailIT` (8), `npx vitest run src/mocks/agenda.msw.test.ts` (4) → passou
- T5 — arquivos: `features/agenda/{useAgenda.ts,agendaDates.ts,AgendaPage.tsx,AgendaWeekGrid.tsx,AgendaFilters.tsx,LessonCard.tsx}`, `App.tsx` (rota `/agenda` para Coordenador, Administrador e Professor), `menuCatalog.ts` (agenda disponível), `i18n/pt-BR.json` (`agenda.*`), `buildMenu.test.ts` — prova: `npx vitest run src/features/agenda` → 15 passaram (axe claro/escuro e grade)
- T6 — arquivos: `features/agenda/AgendaDayList.tsx`, testes de 360 px em `AgendaPage.test.tsx` — prova: `npx vitest run src/features/agenda` → passou
- T7 — arquivos: `frontend/e2e/agenda.spec.ts` — prova: `npm run e2e` → 27 passaram

Regressão: `./backend/mvnw -f backend/pom.xml verify` → 130 testes rápidos e 333 de integração, 0 falhas, JaCoCo passou; em `frontend/`: `npm run lint`, `npm run typecheck`, `npm run build` ok, `npm run test:cov` → 72 arquivos, 611 testes ok, `npm run e2e` → 27 ok. Sem linha de base pior.
Desvios: (1) `ClassScheduleChange.changed` ganhou `AuditActor actor, String ip` para auditar `lesson.regenerated` com o ator da sessão; o `verify` de `TrainingClassWriteIT` foi ajustado. (2) A geração audita por `ScopedAudit` (a tarefa diária não tem contexto de organização). (3) A função de banco chama-se `active_organization_ids()` e fica com execução pública (só devolve ids de organizações com unidade ativa); o dono da função ignora a RLS e há teste com `ctcore_app`. (4) Um teste de login com duas etapas (`TwoFactorLogin.test.tsx`) falhou por lentidão numa rodada, passou sozinho e na rodada seguinte: instável sob carga, sem relação com o item. (5) O simulado (MSW) trata hoje como dia sem aula, para o e2e sempre ver "Sem aula".
Não é do item: nenhum.

## Verificação

### Rodada 1 — OK — 2026-10-19, estado 97f1ad266d7732e50218b738ce126b1ef1dde6ca

| Critério | Status | Evidência |
|---|---|---|
| CA1, CA2, CA3, CA4, CA11 | PASSOU | `LessonGenerationIT` (geração 56 dias, idempotência, fuso, desativada, dia sem aula, isolamento/RLS, auditoria) dentro do `verify`: 333 IT, 0 falhas |
| CA5 | PASSOU | `LessonGenerationIT.createAndRegenerate`; código de `ClassScheduleChange` lido (só apaga futura, não cancelada e não editada; `changed` só chamado quando o horário muda) |
| CA6, CA8 | PASSOU | `AgendaRulesTest` (4 testes) no `verify`; lógica de `Agenda.status/can` conferida contra RN-AGE-03/08 |
| CA7, CA9, CA10, CA12 | PASSOU | `AgendaIT` no `verify`, 0 falhas |
| CA13, CA14, CA15 | PASSOU | `npx vitest run src/features/agenda src/mocks` → 18 arquivos, 113 testes ok (inclui axe, 360 px, quatro estados) |
| CA16 | PASSOU | `TrainingClassDetailIT.nextClass` no `verify` |
| CA17 | PASSOU | `OpenApiContractTest`, `ContractRoutesTest`, `SeedDataIT` no `verify`; `agenda.msw.test.ts` ok |
| CA18 | PASSOU | `e2e/agenda.spec.ts` ok (2 execuções) |

Comandos:
- `./backend/mvnw -f backend/pom.xml verify` → BUILD SUCCESS, 333 IT, 0 falhas, JaCoCo ok.
- `npm run lint && typecheck && build` ok; `npm run test:cov` → 72 arquivos, 611 testes ok, cobertura 94,65% linhas (na primeira rodada, sob carga do `verify` em paralelo, `TeacherFormDialog.test.tsx` estourou 20 s; passou sozinho e na repetição completa: instável, não é do item).
- `npm run e2e` → 26 de 27 sob carga; a falha (`configuracao-unidade.spec.ts`) passou ao reexecutar com `agenda.spec.ts` (2 de 2): instabilidade por carga.

Achados:
- sugestão, código, `Agenda.java` — o professor da aula e a turma só aparecem por join interno com `training_class`; aula avulsa (#52, `class_id` nulo) ficará fora da agenda até #52 ajustar o join.
- sugestão, código, `TrainingClasses.nextClass` — usa nomes qualificados de `java.time` em linha; importar.

RESULTADO: OK

## Entrega

- Operação › Agenda (RF-AGE-01): geração diária de aulas (56 dias, idempotente, fuso da unidade), regeneração ao mudar o horário da turma (só aulas futuras, não canceladas e não editadas), API de agenda com permissões e isolamento (RLS), tela com lista por dia e grade semanal nos quatro estados, e próxima aula na janela da turma.
- Verificação: `verify` do back-end (333 IT, JaCoCo ok), lint, tipos, build, `test:cov` (611 testes) e `e2e` (27) verdes; Rodada 1 OK.
- Decisões: auditoria da geração por `ScopedAudit`; função `active_organization_ids()` com execução pública (só devolve ids); MSW trata o dia como sem aula.
- Limitações: aula avulsa (#52) ficará fora da agenda até o join ser ajustado; testes de front instáveis sob carga, sem relação com o item.
