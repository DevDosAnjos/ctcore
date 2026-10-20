# 52 — RF-AGE-04 · Nova aula avulsa

Origem: issue #52 (Sprint 05); `docs/especificacao/06-turmas-e-agenda.md` (RF-AGE-04, RN-AGE-12, "Dados": aula e aula avulsa), `05-professores.md` (RN-PRO-06). Branch: `item/52-nova-aula-avulsa`. Parte da tabela `lesson` (V25, V26), de `Agenda`, `LessonWrite` e da janela `EditLessonDialog` (#51).

## 1. Objetivo

Coordenador, Administrador e Professor com permissão criam, na Agenda, uma aula avulsa (sem turma): título, modalidade, data, horário, professor e visibilidade (uma turma da unidade ou todas as turmas da unidade). A aula avulsa aparece na agenda, abre no painel da aula e pode ser editada e cancelada como as demais.

Fora do escopo: portal do aluno (não existe; esta entrega só grava e expõe a visibilidade para o portal ler, RN-AGE-12), aula recorrente, comunicado de aula nova, dias sem aula (#53), "Ver turma" e número de alunos para aula sem turma.

## 2. Requisitos

- R1 (RF-AGE-04): `POST /api/v1/agenda/lessons` recebe `unitId`, `title`, `modalityId`, `date`, `startsAt`, `endsAt`, `teacherId` e `visibilityClassId` (opcional; ausente = todas as turmas da unidade) e devolve 201 com o `LessonDetail` da aula criada.
- R2 (RN-AGE-12, D1): a aula avulsa não tem turma (`class_id` nulo) e guarda título, modalidade e, se houver, a turma de visibilidade; `Lesson` e `LessonDetail` passam a trazer `class` nulo, `title` e (no detalhe) `visibilityClass` (nulo = todas as turmas da unidade).
- R3 (RN-AGE-12): a turma de visibilidade é turma ativa da mesma unidade da aula.
- R4 (validação): `title` obrigatório, até 80 caracteres; `modalityId` ativa na organização e oferecida pela unidade; `endsAt` depois de `startsAt`; início não fica no passado no fuso da unidade; formatos inválidos e campos ausentes dão 400 com o campo.
- R5 (RN-PRO-06, D2): o professor é da organização, ativo e ensina a modalidade escolhida (422 no campo `teacherId`); `substitute` é sempre falso (não há titular).
- R6 (RN-AGE-07, D3): exige `AGENDA_EDIT` na unidade, conferida no servidor; o Professor só cria aula em que ele mesmo é o professor (403 se outro) e só em unidade do seu alcance.
- R7 (aula avulsa nas leituras, D4): agenda (`GET /agenda`), filtro por modalidade e painel (`GET /agenda/lessons/{id}`) incluem a aula avulsa (hoje exigem turma); no painel de aula avulsa `head` é nulo, `activeStudents` 0, `trialClasses` 0, `groupLink` nulo, `canViewClass` falso; para o Professor a aula avulsa é "sua" se ele é o professor da aula.
- R8 (#51 sobre avulsa, D5): editar aula avulsa muda data, horário e professor com `THIS` (`FOLLOWING` dá 400), valida o professor pela modalidade da aula, mantém `substitute` falso; cancelar funciona igual às demais.
- R9 (auditoria): criar grava `lesson.created` (id da aula, unidade, título, modalidade, data, horário, professor, turma de visibilidade) na mesma transação, sem dado pessoal além de ids.
- R10 (isolamento): organização só da sessão; Aluno e Responsável 403; sem sessão 401; unidade, modalidade, professor ou turma de outra organização, ou unidade fora do alcance do Administrador, dão 404 sem revelar existência (para `teacherId` e `modalityId` de outra organização, 422 no campo, como R5); RLS de `lesson` vale (V25).
- R11 (telas): botão "Nova aula avulsa" na Agenda, visível quando há unidade e permissão, abre a janela com título, modalidade, data, início, término, professor e visibilidade (todas as turmas da unidade ou uma turma); sucesso fecha a janela e a agenda mostra a aula; lista e painel mostram o título no lugar do nome da turma e a marca "Avulsa"; quatro estados nas janelas e no painel, 360 px sem rolagem horizontal, axe nos dois temas, textos em `pt-BR.json`, cor nunca é a única indicação.
- R12 (contrato): `docs/openapi.yaml` com a rota nova e os campos novos; handler MSW no formato do servidor.
- R13 (RN-AGE-13): nada novo cita lista de espera, presença ou frequência.

## 3. Critérios de aceite

- CA1 (R1, R2, R9): dado Coordenador, quando cria aula avulsa válida para toda a unidade, então 201, `class` nulo, `title`, `modality`, `teacher`, `visibilityClass` nulo, `substitute` falso, status Agendada, e a auditoria `lesson.created` existe com os ids.
- CA2 (R2, R3): dado turma ativa da unidade em `visibilityClassId`, então a aula vem com `visibilityClass` igual à turma; turma desativada, de outra unidade ou inexistente: 422 em `visibilityClassId`; de outra organização: 404.
- CA3 (R4): dado `title` vazio ou com mais de 80 caracteres, modalidade inativa ou não oferecida pela unidade, `endsAt` antes de `startsAt`, início no passado, data ou horário mal formados, então 400 (ou 422 para a modalidade) com o campo e nada é gravado.
- CA4 (R5): dado professor inativo, de outra organização ou que não ensina a modalidade, então 422 em `teacherId`; professor de outra unidade que ensina a modalidade é aceito.
- CA5 (R6): dado Professor com `AGENDA_EDIT` criando aula com ele mesmo, então 201; com outro professor, 403; sem `AGENDA_EDIT`, 403; Administrador em unidade fora do alcance, 404.
- CA6 (R7): dado aula avulsa criada, então `GET /agenda` a lista (também com filtro da modalidade e do professor) com `title` e `class` nulo, e o painel a abre com os valores de R7; o Professor que é o professor da aula a vê e a edita; outro Professor da unidade não a vê (404 no painel); aulas de turma seguem iguais.
- CA7 (R8): dado aula avulsa, quando edita data, horário e professor com `THIS`, então 200, `substitute` falso; com `FOLLOWING`, 400; quando cancela, então `CANCELLED`.
- CA8 (R10): dado Aluno e Responsável, 403; sem sessão, 401; unidade de B pela sessão de A, 404; nenhuma aula é criada nesses casos; com `ctcore_app` sem escopo, a inserção em `lesson` é recusada pela RLS.
- CA9 (R9): dado falha no meio da criação, então nem aula nem auditoria ficam.
- CA10 (R11): dado a Agenda, quando clica em "Nova aula avulsa", preenche e salva, então a janela fecha e a aula aparece na agenda com o título e a marca "Avulsa"; erros 400, 403 e 422 aparecem na janela; o painel da aula avulsa abre sem "Ver turma" e sem erro.
- CA11 (R11): dado carregando, erro 500 com "Tentar de novo", 403 (sem permissão) e vazio (unidade sem modalidade ou sem professor para escolher), então cada estado aparece com seu texto; 360 px sem rolagem horizontal; axe sem violações nos temas claro e escuro; botão ausente para quem não tem permissão.
- CA12 (R12): `OpenApiContractTest` e `ContractRoutesTest` passam; teste MSW cobre a criação e a leitura no formato do servidor.
- CA13 (R13): grep do esquema, do corpo e de `pt-BR.json` sem lista de espera, presença ou frequência.
- CA14 (R11): ponta a ponta `@critical` da Agenda estendido: o Coordenador cria uma aula avulsa e a vê na agenda.

## 4. Plano técnico

- Dados: migração `V27__lesson_adhoc.sql` (a criar): `lesson` ganha `title text`, `modality_id uuid` (FK `modality`) e `visibility_class_id uuid` (FK composta `(organization_id, visibility_class_id)` para `training_class`); `check` de que com `class_id` nulo há `title` e `modality_id`, e com `class_id` preenchido os três ficam nulos; comentário de reversão manual no padrão das anteriores. Sem tabela nova (RLS de V25 vale); seed novo `infra/seed/NN-aula-avulsa.sql` só se houver convenção para a tabela `lesson` (conferir na construção).
- API: `POST /api/v1/agenda/lessons` (201 `LessonDetail`; 400, 401, 403, 404, 422); `Lesson` e `LessonDetail` com `class` anulável, `title`, `visibilityClass`; entra em `docs/openapi.yaml` no mesmo PR.
- Back-end (`br.com.ctcore.agenda`): alterar `LessonWrite.java` (`create`, `CreateInput`, auditoria `lesson.created` no padrão de `update`; `update` passa a tratar aula sem turma: `FOLLOWING` 400, sem `classId`, `checkTeacher` com a modalidade da aula e sem titular, `substitute` falso); alterar `Agenda.java` (`view` e `load` com `left join` em `training_class` e modalidade por `coalesce(l.modality_id, c.modality_id)`, `mine` de avulsa = professor da aula, novos campos nos records, valores de R7); alterar `AgendaController.java` (`@PostMapping("/lessons")`, status 201). Uma transação por chamada (`OrganizationTransactions`); parâmetros ligados.
- Interface: a criar em `frontend/src/features/agenda` `NewLessonDialog.tsx` (padrão de `EditLessonDialog.tsx`) e teste; alterar `AgendaPage.tsx` (botão), `LessonCard.tsx` e `LessonPanelPage.tsx` (título, marca "Avulsa", sem "Ver turma"/grupo quando `class` nulo), `useAgenda.ts`/`useLesson.ts` (tipos), `useLessonActions.ts` (criar, invalidar consultas), `mocks/handlers.ts` (+ `mocks/agenda.msw.test.ts`), `i18n/pt-BR.json` (`agenda.new.*`, `agenda.adhoc`), `e2e/agenda.spec.ts`. Modalidades e professores vêm de `GET /agenda` (já trazem os da unidade); turmas da visibilidade de `GET /classes?unitId=&status=ACTIVE` (existente). `EditLessonDialog`/`CancelLessonDialog` usam `lesson.title` quando `class` é nulo.
- Segurança: perfil, organização e alcance só da sessão; `AGENDA_EDIT` reconferida no servidor; Professor só para si; 404 sem revelar existência; título exibido como texto, nunca HTML; sem dado pessoal na auditoria.
- Reuso: `Agenda.status/can/substituteWindow`, `Permissions.grant`, `OrganizationTransactions`, `AuditService`/`AuditEntry`, `ApiStatusException`, `ApiValidationException`, `AgendaBase` (testes), `states`, `Button`, `modalityHue`.
- Riscos: (1) trocar `join` por `left join` pode mudar a leitura das aulas de turma: os `AgendaIT` e `AgendaLessonIT` existentes provam; (2) o gerador diário e o refazer de horários da turma (#48) só tratam `class_id` não nulo: conferir em teste que não tocam a avulsa; (3) início no passado medido no fuso da unidade.

## 5. Tarefas

- [x] T1: migração V27 e leitura da aula avulsa em `Agenda` (`left join`, campos novos, `mine`, R7) — cobre CA6 (leitura) — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test='AgendaLesson*IT,AgendaIT,LessonGenerationIT,TrainingClass*IT'` (nada quebra) e Flyway aplicado.
- [x] T2: `LessonWrite.create` e a rota `POST` (validações, professor, visibilidade, permissão, auditoria, 201) — cobre CA1 a CA5, CA8, CA9 — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test=AgendaLessonAdhocIT`.
- [x] T3: edição e cancelamento de aula avulsa em `LessonWrite.update` (`THIS`, professor pela modalidade, sem titular) — cobre CA6 (edição pelo professor), CA7 — prova: `verify -Dit.test='AgendaLessonAdhocIT,AgendaLessonEditIT,AgendaLessonCancelIT'`.
- [x] T4: OpenAPI da rota e dos campos novos e handler MSW com teste — cobre CA12, CA13 — prova: `./backend/mvnw -f backend/pom.xml test -Dtest='OpenApiContractTest,ContractRoutesTest'` e `npx vitest run src/mocks` em `frontend/`.
- [x] T5: `NewLessonDialog`, botão na Agenda, textos `agenda.new.*`, criar em `useLessonActions` — cobre CA10, CA11 — prova: `npx vitest run src/features/agenda` (quatro estados, 360 px, axe nos dois temas).
- [x] T6: título, marca "Avulsa" e ausência de "Ver turma" na lista, no painel e nas janelas de editar e cancelar — cobre CA10 (painel), CA11 — prova: `npx vitest run src/features/agenda`.
- [x] T7: estender o e2e `@critical` da Agenda — cobre CA14 — prova: `npm run e2e` em `frontend/`.
- [x] T8: regressão completa (não cobre critério: confere cobertura e os 100% dos pacotes exigidos) — prova: `./backend/mvnw -f backend/pom.xml verify` e, em `frontend/`, `npm run lint && npm run typecheck && npm run build && npm run test:cov && npm run e2e`.

## 6. Decisões

- D1 (muda o comportamento): a visibilidade fica em `visibility_class_id` e a aula avulsa não tem `class_id`, para o gerador, o índice de colisão e o refazer de horários da turma não a tratarem como aula de turma. O portal (sem sessão futura definida aqui) lê `visibilityClass` para mostrar a aula só àquela turma ou a todas da unidade. Na Gestão a agenda da unidade mostra todas as avulsas. Alternativa: `class_id` preenchido quando há turma; descartada por se confundir com aula gerada da turma.
- D2: modalidade da avulsa deve estar ativa na organização e ser oferecida pela unidade (mesma lista de `GET /agenda`); a turma de visibilidade não precisa ser da mesma modalidade. Professor: ativo e que ensina a modalidade escolhida, de qualquer unidade (RN-PRO-06); aula avulsa não tem titular, então `substitute` é falso. Alternativa: exigir a unidade do professor; descartada como em #51 (D2).
- D3 (muda o comportamento): quem cria é quem tem `AGENDA_EDIT` na unidade (Administrador, Coordenador, Professor com a permissão); o Professor só cria aula em que ele é o professor. O card diz "mesmo formulário de edição", que usa `AGENDA_EDIT`; a restrição ao Professor segue RN-AGE-07. Alternativa: Professor livre para escolher o professor; descartada por RN-AGE-07.
- D4: avulsa aparece na agenda e no painel (que exigiam turma); no painel, campos de turma vêm vazios, sem "Ver turma" e sem grupo. `activeStudents` 0 e `trialClasses` 0 até haver alunos por aula (Sprints futuras). Suposição: se o dono quiser alunos convidados na avulsa, vira item novo.
- D5: editar avulsa só vale para uma aula (`THIS`), pois não há série; cancelar segue RN-AGE-05 sem aulas experimentais.
- D6: o canvas (`AulaAvulsa.dc.html`) não está acessível aqui; a janela segue o padrão de `EditLessonDialog` e o dono confere o layout no PR. Sem comunicado ao criar (nada na issue).
- D7: criação é `POST /agenda/lessons` (coleção), como `classes`; sem idempotência por chave (aula única, sem efeito financeiro), a colisão não existe porque o índice único só vale com turma.

## Construção

- T1 — arquivos: `backend/src/main/resources/db/migration/V27__lesson_adhoc.sql`, `agenda/Agenda.java` (left join, `title`, `visibilityClass`, `mine` e visibilidade da avulsa, valores de R7) — prova: `verify -Dit.test='AgendaLesson*IT,AgendaIT,LessonGenerationIT,TrainingClass*IT'` → 0 falhas; Flyway aplicado nos ITs.
- T2 — arquivos: `agenda/LessonWrite.java` (`create`, `CreateInput`), `agenda/AgendaController.java` (POST 201), `AgendaLessonAdhocIT.java` (5 testes: CA1 a CA5, CA8 e CA9, este com gatilho que falha na auditoria) — prova: `verify -Dit.test=AgendaLessonAdhocIT` → 5 de 5.
- T3 — arquivos: `LessonWrite.update` (avulsa: FOLLOWING 400, sem titular, `substitute` falso); testes no `AgendaLessonAdhocIT` — prova: `verify -Dit.test='AgendaLesson*IT,AgendaIT'` → 22 de 22.
- T4 — arquivos: `docs/openapi.yaml` (POST `/agenda/lessons`, `LessonCreateInput`, `class` anulável, `title`, `visibilityClass`), `frontend/src/mocks/handlers.ts`, `frontend/src/mocks/agenda.msw.test.ts` — prova: `test -Dtest='OpenApiContractTest,ContractRoutesTest'` → 7 de 7; `npx vitest run src/mocks` → 96 de 96; CA13: nada de lista de espera, presença ou frequência em `agenda.new.*`, no esquema nem no corpo.
- T5 — arquivos: `NewLessonDialog.tsx`, `AgendaPage.tsx` (botão por `AGENDA_EDIT`), `useLessonActions.ts` (`createLesson`, `useActiveClasses`), `pt-BR.json` (`agenda.new.*`), `NewLessonDialog.test.tsx` — prova: `npx vitest run src/features/agenda` → 49 de 49 (estados, 360 px, axe claro e escuro).
- T6 — arquivos: `LessonCard.tsx` (`lessonName`, marca Avulsa), `LessonPanelPage.tsx`, `EditLessonDialog.tsx`, `CancelLessonDialog.tsx`, `useAgenda.ts`, `useLesson.ts` — prova: mesma execução da T5.
- T7 — arquivos: `frontend/e2e/agenda.spec.ts` — prova: `npm run e2e` → 27 de 27.
- T8 — regressão abaixo.

Regressão: `./backend/mvnw -f backend/pom.xml verify` → 351 testes, 0 falhas, BUILD SUCCESS (JaCoCo ok); em `frontend/`: `npm run lint`, `typecheck` e `build` ok; `test:cov` ok (linhas 96,9%); `e2e` 27 de 27.
Desvios: (1) turma de visibilidade inexistente dá 404, não 422 (CA2): com RLS e filtro por organização o servidor não distingue inexistente de outra organização e não revela existência; turma desativada ou de outra unidade dá 422. (2) A janela trata o 403 de `GET /classes` (Professor) oferecendo só "Todas as turmas da unidade", em vez do estado sem permissão; o 403 da criação aparece como texto na janela. (3) Sem seed novo: não há convenção de seed para `lesson` (as aulas vêm do gerador). (4) `AgendaLessonAdhocIT` apaga do `audit_log` as linhas `lesson.*` que gravou, porque `AgendaLessonEditIT` e `AgendaLessonCancelIT` contam essas ações no banco inteiro. (5) A mensagem 422 de professor passou a dizer "modalidade da aula".
Não é do item: nada.

## Verificação

### Rodada 1 — OK — 2026-10-19, estado 8def250dfbced732a50b6421922258f6d041edac

| Critério | Status | Evidência |
|---|---|---|
| CA1 a CA9 | PASSOU | `mvnw verify` completo: 351 testes, 0 falhas, BUILD SUCCESS; `AgendaLessonAdhocIT` cobre criação, visibilidade, validações, professor, permissão, isolamento/RLS e atomicidade; leitura do código de `LessonWrite.create`/V27 confere |
| CA10, CA11 | PASSOU | `test:cov` 655 de 655 (`NewLessonDialog.test`, `LessonPanelPage.test`, `AgendaPage.test`: estados, 360 px, axe) |
| CA12 | PASSOU | `OpenApiContractTest`/`ContractRoutesTest` dentro do `verify`; `agenda.msw.test.ts` no `test:cov` |
| CA13 | PASSOU | sem lista de espera, presença ou frequência no diff |
| CA14 | PASSOU | `npm run e2e` 27 de 27 |

Comandos: `mvnw verify` ok; `npm run lint`, `typecheck` ok; `npm run test:cov` (1ª execução: 1 falha em `auth/TwoFactorLogin.test.tsx`, fora do item; reexecutada isolada 13/13 e `test:cov` completo 655/655, linhas 96,9%); `npm run e2e` 27/27. `build` não reexecutado (mantido do relato da construção).
Achados:
- sugestão, especificação, CA2 — turma de visibilidade inexistente dá 404 (desvio 1 registrado) em vez de 422; coerente com R10, ajustar o texto do CA2.
- sugestão, código, `AgendaLessonAdhocIT` apaga `audit_log` de `lesson.*` para não quebrar outros ITs (desvio 4); frágil, melhor contar por id nos outros ITs.

## Entrega

- `POST /api/v1/agenda/lessons` cria aula avulsa (turma opcional, `title`, `visibilityClass`), com auditoria, isolamento por organização/RLS e atomicidade; migração V27; OpenAPI e MSW atualizados.
- Front: janela "Nova aula avulsa" (botão por `AGENDA_EDIT`), marca "Avulsa" no cartão, painel, edição e cancelamento adaptados.
- Verificação: `mvnw verify` 351 testes sem falhas; lint, typecheck, build, `test:cov` (96,9%) e e2e 27/27.
- Desvios: turma de visibilidade inexistente dá 404 (não 422); `AgendaLessonAdhocIT` limpa `audit_log` de `lesson.*` que gravou (frágil, melhorar depois).
