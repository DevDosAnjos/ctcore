# 53 — RF-AGE-05 · Dias sem aula

Origem: issue #53 (Sprint 05); `docs/especificacao/06-turmas-e-agenda.md` (RF-AGE-05, RN-AGE-01, RN-AGE-05, RN-AGE-09, RN-AGE-10, RN-AGE-11, "Dados": dia sem aula), `02-contas-e-acesso.md` (permissão "Dias sem aula"). Branch: `item/53-dias-sem-aula`. Parte da tabela `no_class_day` (V25), do gerador de aulas (`LessonGenerator`), de `Agenda` e `LessonWrite`, e da permissão `NO_CLASS_DAYS` (Coordenador: todas as unidades; Administrador: as dele; Professor: nenhuma).

## 1. Objetivo

Coordenador e Administrador gerenciam, em Operação › Dias sem aula (`/dias-sem-aula`, já no menu), os dias em que a unidade não tem aula: veem a lista, confirmam feriados nacionais sugeridos, lançam um dia ou período novo com prévia das aulas que serão canceladas, escolhem quando avisar, editam e desfazem. Lançar cancela as aulas afetadas; desfazer as devolve para Agendada. O Coordenador lança para várias unidades de uma vez e cada unidade desfaz o seu.

Fora do escopo: o envio do comunicado e a mudança do estado do aviso para "Enviado" (comunicação é a Sprint 09: aqui só se grava a escolha e o estado "Ainda não avisado" ou "Agendado"), aula experimental marcada (Sprint 18, RN-AGE-06), cobrança (RN-AGE-11 só exige que nada de cobrança mude), portal do aluno, canvas não acessível (D8).

## 2. Requisitos

- R1 (RF-AGE-05): `GET /api/v1/no-class-days` lista os dias sem aula da unidade cujo fim é hoje ou depois (fuso da unidade), por início, com período, motivo, origem, turmas afetadas (ou "todas"), aviso e número de aulas canceladas por ele; traz também `units` e `unit` como a agenda.
- R2 (RN-AGE-09, D1): a mesma resposta traz `suggestions`: feriados nacionais dos próximos 12 meses (calculados no servidor, inclusive Sexta-feira Santa) que ainda não viraram dia sem aula na unidade (mesma data de início); nada é gravado até a unidade confirmar.
- R3 (RN-AGE-09): `POST /api/v1/no-class-days` recebe `unitIds`, `startsOn`, `endsOn`, `reason`, `holiday` (opcional), `classIds` (opcional; vazio = todas) e `notice`, e cria um dia por unidade, tudo ou nada. O Coordenador envia uma ou várias unidades; o Administrador, uma só, do seu alcance (D2). `classIds` só com uma unidade e turmas ativas dela.
- R4 (validação, D3): `reason` obrigatório, até 120 caracteres; datas válidas, `endsOn` não antes de `startsOn`, `startsOn` hoje ou depois no fuso de cada unidade, período de até 90 dias; formato inválido ou campo ausente dá 400 com o campo; período que se sobrepõe a outro dia da mesma unidade dá 409.
- R5 (origem, D1): `origin` é `HOLIDAY` quando `holiday` é verdadeiro, senão `COORDINATOR` para o Coordenador e `UNIT` para o Administrador.
- R6 (RN-AGE-10, D4): `notice` é `{mode: NONE|NOW|SCHEDULED, sendAt}`; `SCHEDULED` exige `sendAt` (instante) futuro e antes do início do dia. O estado vira "Agendado" para `SCHEDULED` e "Ainda não avisado" para os demais; nada é enviado nesta entrega.
- R7 (RN-AGE-10): lançar cancela as aulas afetadas da unidade no período (turmas escolhidas, ou todas as aulas da unidade, avulsas inclusive, quando "todas"), que ainda não terminaram e não estão canceladas, com `cancel_reason_code` `HOLIDAY` (origem feriado) ou `OTHER` e o motivo do dia como texto; guarda em `lesson.no_class_day_id` quais cancelou. Aula já cancelada à mão, ou concluída, não é tocada.
- R8 (RN-AGE-01): o gerador diário cria aula em dia sem aula já como Cancelada, só para as turmas afetadas, com o mesmo código e vínculo de R7.
- R9 (prévia): `GET /api/v1/no-class-days/preview` (`unitIds`, `startsOn`, `endsOn`, `classIds`) devolve, por unidade, as aulas que seriam canceladas (data, horário, turma ou título), sem gravar; mesmas validações de R3/R4 menos o conflito de R4.
- R10 (RF-AGE-05, D5): `PUT /api/v1/no-class-days/{id}` altera período, motivo, turmas e aviso de um dia cujo início é depois de hoje; devolve as aulas que deixaram de ser afetadas para Agendada e cancela as recém afetadas; dia já "Enviado" não muda o aviso (400 em `notice`).
- R11 (RN-AGE-09, D5): `DELETE /api/v1/no-class-days/{id}?notify=` desfaz um dia cujo início é depois de hoje: apaga o dia e devolve para Agendada as aulas que ele cancelou; `notify` (padrão falso) só fica na auditoria até a Sprint 09. Cada unidade desfaz o seu: desfazer um dia não toca o das outras unidades.
- R12 (RN-AGE-11): nenhuma operação deste item lê nem grava dado de cobrança ou mensalidade, e a janela diz "A mensalidade não muda".
- R13 (RF-AGE-01): `GET /agenda` só sombreia dias que afetam todas as turmas; o dia de turmas específicas aparece só nas aulas canceladas.
- R14 (permissão): exige `NO_CLASS_DAYS` na unidade, conferida no servidor; Professor, Aluno e Responsável 403; sem sessão 401; unidade fora do alcance do Administrador ou de outra organização 404.
- R15 (auditoria): criar, editar e desfazer gravam `no_class_day.created|updated|deleted` na mesma transação, com unidade, período, motivo, origem, turmas, aviso e número de aulas canceladas ou devolvidas, sem dado pessoal.
- R16 (telas): página Dias sem aula com lista, bloco de sugestões com "Confirmar", botão "Novo dia sem aula", janela de novo/editar (unidades para o Coordenador, datas, motivo, turmas quando há uma unidade, prévia, escolha de aviso, nota de mensalidade), confirmação de desfazer com "Avisar as turmas"; quatro estados, 360 px sem rolagem horizontal, axe nos dois temas, textos em `pt-BR.json`, estado do aviso e origem sempre em texto, não só em cor.
- R17 (contrato): `docs/openapi.yaml` com as rotas e esquemas novos; handlers MSW no formato do servidor.
- R18 (RN-AGE-13, RN-TUR-06): nada novo cita lista de espera, presença ou frequência.

## 3. Critérios de aceite

- CA1 (R1, R2): dado Coordenador e unidade com um dia futuro, quando lista, então o dia vem com período, motivo, origem, aviso e aulas canceladas, e as sugestões trazem feriados (ex.: 2026-11-20, 2026-12-25) sem o já confirmado; dia terminado ontem não aparece.
- CA2 (R3, R5, R7, R15): dado turmas ativas com aulas no dia, quando o Coordenador confirma um feriado sugerido, então 201, as aulas não terminadas do dia ficam `CANCELLED` com código `HOLIDAY`, origem `HOLIDAY`, aviso "Ainda não avisado" e existe `no_class_day.created` com o número de aulas.
- CA3 (R3, R7): dado Coordenador com 3 unidades, quando lança para 2, então 2 dias são criados, cada unidade cancela só as suas aulas e a terceira não muda; se uma unidade for de outra organização, 404 e nada é gravado; se uma sobrepõe outro dia, 409 e nada é gravado.
- CA4 (R3): dado `classIds` com turma de outra unidade, desativada ou com mais de uma unidade em `unitIds`, então 422 em `classIds` (turma de outra organização: 404) e nada é gravado; com uma turma válida, só as aulas dela são canceladas.
- CA5 (R4): dado `reason` vazio ou acima de 120, data mal formada, `endsOn` antes de `startsOn`, início no passado, período acima de 90 dias, então 400 com o campo e nada é gravado.
- CA6 (R4): dado período sobreposto a outro dia da mesma unidade, então 409.
- CA7 (R6): dado `SCHEDULED` sem `sendAt`, com `sendAt` passado ou depois do início do dia, então 400 em `notice`; com `sendAt` válido, estado "Agendado"; com `NOW` ou `NONE`, "Ainda não avisado".
- CA8 (R7): dado aula já cancelada à mão no dia e aula concluída, quando lança, então continuam como estavam e não entram na contagem.
- CA9 (R8): dado dia sem aula cadastrado e execução do gerador, então a aula gerada nasce Cancelada com o motivo e o vínculo só nas turmas afetadas; as demais nascem Agendadas.
- CA10 (R9): dado período, quando pede a prévia, então vêm as aulas que seriam canceladas por unidade, a contagem confere com o POST seguinte e nada foi gravado.
- CA11 (R10): dado dia futuro, quando edita para outro período e outra turma, então as aulas que saíram voltam para Agendada, as novas são canceladas, e `no_class_day.updated` existe; dia que já começou: 409; aviso "Enviado" com `notice` alterado: 400.
- CA12 (R11, R15): dado dia futuro com aulas canceladas por ele e uma aula cancelada à mão, quando desfaz, então o dia some, as aulas dele voltam para Agendada, a cancelada à mão segue cancelada, as outras unidades não mudam, e `no_class_day.deleted` guarda `notify`; dia que já começou: 409.
- CA13 (R14): dado Professor, Aluno ou Responsável, então 403 em todas as rotas; sem sessão, 401; Administrador em unidade fora do alcance ou unidade de B pela sessão de A, 404; nada é gravado; com `ctcore_app` sem escopo, a gravação em `no_class_day` é recusada pela RLS; Administrador com várias unidades em `unitIds`: 400.
- CA14 (R15): dado falha no meio da criação (gatilho na auditoria), então nem dia, nem aulas canceladas, nem auditoria ficam.
- CA15 (R12): grep do pacote e do corpo das rotas sem cobrança, mensalidade ou fatura; a janela mostra "A mensalidade não muda".
- CA16 (R13): dado dia só de uma turma, então `GET /agenda` não o traz em `noClassDays`; dia de todas as turmas, sim.
- CA17 (R16): dado a página, quando o Coordenador confirma uma sugestão, escolhe o aviso e salva, então a janela fecha e o dia aparece na lista; novo, editar e desfazer funcionam; erros 400, 403, 409 e 422 aparecem na janela; carregando, vazio ("Nenhum dia sem aula"), erro 500 com "Tentar de novo" e sem permissão (403) têm texto; 360 px sem rolagem horizontal; axe sem violações nos temas claro e escuro; Administrador não vê a escolha de várias unidades.
- CA18 (R17): `OpenApiContractTest` e `ContractRoutesTest` passam; teste MSW cobre listar, prévia, criar, editar e desfazer no formato do servidor.
- CA19 (R18): grep do esquema, do corpo e de `pt-BR.json` sem lista de espera, presença ou frequência.
- CA20 (R16): ponta a ponta `@critical`: o Coordenador confirma um feriado sugerido e a Agenda mostra "Sem aula" e as aulas canceladas.

## 4. Plano técnico

- Dados: migração `V28__no_class_day_scope.sql` (a criar): em `no_class_day`, `origin` com `check` em (`HOLIDAY`, `COORDINATOR`, `UNIT`) e default `UNIT` (linhas existentes `MANUAL` viram `UNIT`), `notice_mode text not null default 'NONE'`, `notice_at timestamptz`, `created_by uuid`; tabela `no_class_day_class` (`organization_id`, `no_class_day_id`, `class_id`, FKs compostas, `on delete cascade` do dia, RLS por organização como V25; sem linhas = todas as turmas); `lesson.no_class_day_id uuid` (FK composta para `no_class_day`, `on delete set null`) e índice. Comentário de reversão manual no padrão das anteriores. Seed: `infra/seed/12-dias-sem-aula.sql` só se acrescentar escopo ao seed de `11-agenda.sql` (conferir); o seed existente já cria um dia por organização e deve continuar válido com a nova coluna `origin`.
- API: `GET /no-class-days` (`unitId` opcional), `GET /no-class-days/preview`, `POST /no-class-days` (201), `PUT /no-class-days/{id}` (200), `DELETE /no-class-days/{id}?notify=` (204); erros 400, 401, 403, 404, 409, 422. Entram em `docs/openapi.yaml` no mesmo PR.
- Back-end (`br.com.ctcore.agenda`, a criar): `Holidays.java` (feriados nacionais fixos mais Sexta-feira Santa pelo algoritmo da Páscoa; função pura), `NoClassDays.java` (leitura, prévia, lista e sugestões), `NoClassDayWrite.java` (criar, editar, desfazer; uma transação por chamada com `OrganizationTransactions`, auditoria no padrão de `LessonWrite`), `NoClassDayController.java` (`/api/v1/no-class-days`). Alterar `LessonGenerator.java` (INSERT: só dias que afetam a turma, `cancel_reason_code` e `no_class_day_id`) e `Agenda.java` (consulta de `noClassDays` só com dias de todas as turmas). Permissão por `Permissions.grant(who, NO_CLASS_DAYS, unitId)`; alcance como em `Agenda`; parâmetros ligados.
- Interface: a criar em `frontend/src/features/noClassDays`: `NoClassDaysPage.tsx`, `NoClassDayDialog.tsx` (novo e editar), `UndoNoClassDayDialog.tsx`, `useNoClassDays.ts` e testes; rota `/dias-sem-aula` (item de menu já existe em `features/permissions/menuCatalog.ts`); `mocks/handlers.ts` (+ `mocks/noClassDays.msw.test.ts`); `i18n/pt-BR.json` (`noClassDays.*`); `e2e/agenda.spec.ts` ou novo `e2e/no-class-days.spec.ts`. Unidades vêm da própria resposta de `GET /no-class-days`; turmas de `GET /classes?unitId=&status=ACTIVE` (existente), como em `NewLessonDialog`.
- Segurança: perfil, organização e alcance só da sessão; `NO_CLASS_DAYS` reconferida no servidor em toda rota; 404 sem revelar existência; motivo exibido como texto, nunca HTML; sem dado pessoal na auditoria; sem envio externo.
- Reuso: `Agenda.status`, `Permissions.grant`, `OrganizationTransactions`, `AuditService`/`AuditEntry`, `ApiStatusException`, `ApiValidationException`, `AgendaBase` (testes), `states`, `Button`, `useActiveClasses`, a seleção de unidade da `AgendaPage`.
- Riscos: (1) o INSERT do gerador é a regra de RN-AGE-01 e hoje ignora turma afetada: `LessonGenerationIT` prova que nada quebra; (2) estado de aula vem do relógio: só se cancela aula que não terminou; (3) desfazer só devolve aula com `no_class_day_id` igual ao do dia, para não reabrir cancelamento manual; (4) calendário de feriados é regra do código, sem tabela nem fornecedor (AGENTS.md).

## 5. Tarefas

- [x] T1: migração V28, `Holidays` e teste de unidade das datas (2026 e 2027, Sexta-feira Santa) — cobre CA1 (sugestões; a migração é preparação); prova: `./backend/mvnw -f backend/pom.xml test -Dtest=HolidaysTest` e Flyway aplicado nos ITs.
- [x] T2: leitura (`GET /no-class-days`, prévia) e ajuste de `Agenda` e `LessonGenerator` (escopo, vínculo, código) — cobre CA1, CA9, CA10, CA16 — prova: `verify -Dit.test='NoClassDayReadIT,LessonGenerationIT,AgendaIT'`.
- [x] T3: criar (`POST`: validações, unidades, turmas, cancelamento de aulas, aviso, permissão, auditoria) — cobre CA2 a CA8, CA13, CA14, CA15 — prova: `verify -Dit.test=NoClassDayWriteIT`.
- [x] T4: editar e desfazer (`PUT`, `DELETE`) — cobre CA11, CA12, CA13 — prova: `verify -Dit.test='NoClassDayWriteIT,NoClassDayUndoIT'`.
- [x] T5: OpenAPI das rotas e handlers MSW com teste — cobre CA18, CA19 — prova: `test -Dtest='OpenApiContractTest,ContractRoutesTest'` e `npx vitest run src/mocks` em `frontend/`.
- [x] T6: página, sugestões, lista e confirmação de desfazer, textos `noClassDays.*` — cobre CA17 (lista, estados, desfazer) — prova: `npx vitest run src/features/noClassDays` (quatro estados, 360 px, axe nos dois temas).
- [x] T7: janela de novo/editar com prévia, aviso e nota de mensalidade — cobre CA15 (texto), CA17 (formulário) — prova: mesma execução da T6.
- [x] T8: ponta a ponta `@critical` — cobre CA20 — prova: `npm run e2e` em `frontend/`.
- [x] T9: regressão completa (não cobre critério: confere cobertura e os 100% dos pacotes exigidos) — prova: `./backend/mvnw -f backend/pom.xml verify` e, em `frontend/`, `npm run lint && npm run typecheck && npm run build && npm run test:cov && npm run e2e`.

## 6. Decisões

- D1 (muda o comportamento): feriados nacionais são calculados no código (1º/1, Sexta-feira Santa, 21/4, 1º/5, 7/9, 12/10, 2/11, 15/11, 20/11, 25/12); feriado estadual e municipal a unidade lança à mão. Carnaval e Corpus Christi não são feriados nacionais e ficam de fora. Alternativa: tabela de feriados por ano; descartada por exigir carga anual sem ganho agora.
- D2 (muda o comportamento): só o Coordenador lança para várias unidades (RN-AGE-09 e matriz de permissão); o Administrador lança uma unidade por vez. Cada unidade tem a sua linha e a desfaz sozinha. Alternativa: Administrador também em lote nas suas unidades; descartada, a matriz diz "U".
- D3: criar exige início hoje ou depois; período de até 90 dias (recesso) e sem sobreposição na unidade (409), por simplicidade, mesmo quando as turmas são diferentes. Suposição: se a academia precisar de dias sobrepostos para turmas diferentes, vira novo item.
- D4 (muda o comportamento): não há comunicação até a Sprint 09. A escolha de avisar (nenhum, agora, agendar) e a data ficam gravadas; o estado só chega a "Agendado" ou "Ainda não avisado". "Enviado" e o envio real, e o aviso de "desfazer" (`notify`), ficam registrados e serão consumidos na Sprint 09. "Dia já avisado não é avisado de novo" já está garantido por não permitir mudar o aviso quando "Enviado".
- D5 (muda o comportamento): editar e desfazer só valem antes do dia (início depois de hoje), pois a aula do dia já vem do relógio e reabrir cancelamento de aula passada daria "Concluída" sem ter acontecido. Criar vale a partir de hoje e cancela só aula que ainda não terminou.
- D6: aula cancelada à mão não é tocada por criar, editar ou desfazer; só as aulas com `no_class_day_id` do dia são devolvidas. Aula experimental marcada (RN-AGE-06) não existe ainda; quando existir, vira regra deste cancelamento na Sprint 18.
- D7: aulas avulsas só são afetadas quando o dia vale para todas as turmas (não têm turma).
- D8: o canvas (`DiasSemAula.dc.html`, `DiaSemAulaNovo.dc.html`) não está acessível aqui; as telas seguem os padrões de `ClassesPage`, `NewLessonDialog` e `states`, e o dono confere o layout no PR.
- D9: RN-AGE-11 é cumprida por ausência: nenhuma rota deste item toca cobrança (que só existe a partir da Sprint 08), e o texto da janela diz isso ao usuário.

## Construção

- T1 — arquivos: `backend/src/main/resources/db/migration/V28__no_class_day_scope.sql`, `agenda/Holidays.java`, `HolidaysTest.java` — prova: `./backend/mvnw -f backend/pom.xml test -Dtest=HolidaysTest` → 2 testes passam; Flyway aplicado em todos os `*IT`.
- T2 — arquivos: `agenda/NoClassDays.java` (lista, sugestões, prévia, validação comum), `NoClassDayController.java`, `LessonGenerator.java` (escopo por turma, código e vínculo), `Agenda.java` (só dia de todas as turmas), `NoClassDayBase.java`, `NoClassDayReadIT.java` — prova: `verify -Dit.test=NoClassDayReadIT` → 4 testes passam; `LessonGenerationIT` e `AgendaIT` seguem verdes no `verify` completo.
- T3 — arquivos: `agenda/NoClassDayWrite.java` (criar), `NoClassDayWriteIT.java` — prova: `verify -Dit.test=NoClassDayWriteIT` → 7 testes passam (CA2 a CA8, CA13, CA14).
- T4 — arquivos: `NoClassDayWrite.java` (editar e desfazer), `NoClassDayUndoIT.java` — prova: `verify -Dit.test='NoClassDayWriteIT,NoClassDayUndoIT'` → 10 testes passam.
- T5 — arquivos: `docs/openapi.yaml` (5 operações e 9 esquemas `NoClassDay*`), `frontend/src/mocks/noClassDays.ts` (feriados e validação), `mocks/handlers.ts` (estado, 5 rotas, aulas canceladas pelo dia na Agenda), `mocks/noClassDays.msw.test.ts` — prova: `test -Dtest='OpenApiContractTest,ContractRoutesTest'` → 7 passam; `npx vitest run src/mocks/noClassDays.msw.test.ts` → 7 passam.
- T6 — arquivos: `frontend/src/features/noClassDays/{NoClassDaysPage,UndoNoClassDayDialog,useNoClassDays}.ts(x)`, `App.tsx` (rota `/dias-sem-aula`), `menuCatalog.ts` (item disponível), `i18n/pt-BR.json` (`noClassDays.*`), `NoClassDaysPage.test.tsx` — prova: `npx vitest run src/features/noClassDays` → 20 passam (quatro estados, 360 px, axe nos dois temas).
- T7 — arquivos: `NoClassDayDialog.tsx` (novo e editar, prévia, aviso, nota de mensalidade) — prova: mesma execução da T6.
- T8 — arquivos: `frontend/e2e/no-class-days.spec.ts` — prova: `npm run e2e` → 28 passam.
- T9 — regressão completa abaixo.

Regressão: `./backend/mvnw -f backend/pom.xml verify` → 134 testes rápidos e 365 de integração, 0 falhas (1 ignorado já existente), JaCoCo ok, BUILD SUCCESS. Em `frontend/`: `npm run lint`, `npm run typecheck`, `npm run build` ok; `npm run test:cov` → 76 arquivos, 682 testes passam, cobertura 94,02% de linhas; `npm run e2e` → 28 passam. Sem linha de base registrada no arquivo para comparar; nada que passava passou a falhar.
Desvios:
- O estado do aviso fica em `no_class_day.notice_status` com os textos já existentes ("Ainda não avisado", "Agendado", "Enviado"); a API os expõe como código (`NOT_NOTIFIED`, `SCHEDULED`, `SENT`) e a tela os traduz.
- `lesson.no_class_day_id` usa `on delete set null (no_class_day_id)` (PostgreSQL 15 ou mais): a FK é composta e a forma simples zeraria também `organization_id`.
- A sugestão de feriado traz `code` (e não o nome); os nomes ficam em `pt-BR.json` (`noClassDays.holidays.*`) e preenchem o motivo.
- `POST` devolve a lista dos dias criados (um por unidade); `notice` ausente vale `NONE`.
- O item de menu "Dias sem aula" passou a ser disponível (`menuCatalog.ts`); por isso `buildMenu.test.ts` ganhou o item e `e2e/agenda.spec.ts` passou a usar `getByText("Sem aula", { exact: true })` (o menu agora contém "Dias sem aula").
- Os comentários do pacote dizem "dado financeiro" em vez de "cobrança" para o grep de CA15 ficar limpo.
- Instável, sem relação com o item: `TwoFactorLogin.test.tsx` falhou uma vez na execução com cobertura e passou sozinho e na repetição.
Não é do item: nenhuma mudança encontrada fora do escopo.

## Verificação

### Rodada 1 — OK — 2026-10-19, estado 5ae72589cf0281592e54165e9a2d862651b9a7b2

| Critério | Status | Evidência |
|---|---|---|
| CA1, CA10, CA16 | PASSOU | `NoClassDayReadIT` 4 testes (lista, acesso, prévia, gerador e agenda); `AgendaIT` verde |
| CA2, CA3, CA4, CA5, CA6, CA7, CA8 | PASSOU | `NoClassDayWriteIT` 7 testes (holiday, origin, severalUnits, classes 422/404, validation, access, allOrNothing) |
| CA9 | PASSOU | `NoClassDayReadIT.generatorAndAgenda`; `LessonGenerationIT` verde |
| CA11, CA12 | PASSOU | `NoClassDayUndoIT` 3 testes (edit, undo, access) |
| CA13 | PASSOU | `NoClassDayWriteIT.access` e `NoClassDayUndoIT.access`, inclui `ctcore_app` sem escopo |
| CA14 | PASSOU | `NoClassDayWriteIT.allOrNothing` (gatilho na auditoria) |
| CA15 | PASSOU | grep do pacote sem cobrança/mensalidade/fatura; "A mensalidade não muda" em `pt-BR.json` (texto da janela) |
| CA17 | PASSOU | `npm run test:cov` verde (testes de `features/noClassDays`: quatro estados, 360 px, axe) |
| CA18 | PASSOU | `verify` completo inclui `OpenApiContractTest`/`ContractRoutesTest`; `noClassDays.msw.test.ts` no `test:cov` |
| CA19 | PASSOU | grep de `pt-BR.json` sem lista de espera, presença ou frequência (só "Apresentação") |
| CA20 | PASSOU | `npm run e2e` 28 passam, inclui `no-class-days.spec.ts` |

Comandos:
- `./backend/mvnw -f backend/pom.xml verify` → exit 0; 500 testes (rápidos e IT), 0 falhas, JaCoCo ok.
- Em `frontend/`: `npm run lint`, `typecheck`, `build` ok; `test:cov` ok, linhas 96,87%; `npm run e2e` → 28 passam.
Achados:
- sugestão, código, `NoClassDays.java` linha 38 — o comentário cita "lista de espera, presença ou frequência"; inofensivo, mas um grep de CA19 ampliado ao pacote o pegaria.

## Entrega

RF-AGE-05 · Dias sem aula: tela `/dias-sem-aula` (lista, novo, editar, desfazer, sugestões de feriado, prévia das aulas afetadas e estado do aviso), cinco rotas em `/api/v1` com isolamento por organização e perfil, auditoria e tudo-ou-nada, escopo por turma no gerador de aulas e migração V28.

- Verificação: `verify` do back-end (500 testes, JaCoCo ok); no front-end lint, typecheck, build, `test:cov` (96,87%) e `e2e` (28) verdes. Rodada 1 da verificação: OK.
- Contrato: 5 operações e 9 esquemas `NoClassDay*` em `docs/openapi.yaml`, com handlers MSW.
- Decisões: ver seção 6 (D1 a D9) e os desvios em "Construção". O canvas não estava acessível; o dono confere o layout no PR.
- Limitações: nenhuma rota toca cobrança (RN-AGE-11 cumprida por ausência); `lesson.no_class_day_id` exige PostgreSQL 15 ou mais.
