# 51 — RF-AGE-03 · Editar e cancelar aula

Origem: issue #51 (Sprint 05); `docs/especificacao/06-turmas-e-agenda.md` (RF-AGE-03, RN-AGE-04 a RN-AGE-08, "Dados"), `docs/especificacao/05-professores.md` (RN-PRO-06), `docs/especificacao/13-comunicacao-e-notificacoes.md` (RN-COM-03, canais). Branch: `item/51-editar-cancelar-aula`. Parte do painel da aula (#50: `Agenda.lesson`, `LessonDetail`, `canEdit`/`canCancel`, botões desativados) e da tabela `lesson` (V25).

## 1. Objetivo

Coordenador, Administrador e Professor com permissão abrem, a partir do painel da aula, as janelas "Editar aula" (data, horário e professor, valendo só esta aula ou esta e as seguintes; trocar o professor marca substituição sem mudar o titular) e "Cancelar aula" (com motivo e escolha dos canais do comunicado). O servidor confere de novo quem pode (RN-AGE-07/08) e grava com auditoria.

Fora do escopo: criar o comunicado e enviá-lo (Sprint 09; aqui só se escolhem os canais e o texto pronto aparece), aulas experimentais reais (Sprint 18; a regra RN-AGE-06 já existe, mas `trialClasses` vale 0), aula avulsa (#52), dias sem aula (#53), refazer aulas quando o horário da turma muda (já em #48). Não há recurso liberado aos poucos.

## 2. Requisitos

- R1 (RN-AGE-04): `PUT /api/v1/agenda/lessons/{id}` recebe `scope` (`THIS` ou `FOLLOWING`), `date`, `startsAt`, `endsAt`, `teacherId` e `reason` (opcional) e devolve o `LessonDetail` atualizado.
- R2 (RN-AGE-04, D1): `THIS` muda só a aula do id. `FOLLOWING` aplica horário e professor à aula e às seguintes da mesma turma (data maior, ainda não iniciadas, não canceladas, não concluídas), e desloca a data de cada uma pelo mesmo número de dias que a data desta mudou; tudo ou nada.
- R3 (RN-AGE-04, RN-PRO-06, D2): o professor informado é da organização, ativo e ensina a modalidade da turma (pode ser de outra unidade). Se for diferente do titular, `substitute = true`; se for o titular, `substitute = false`. O titular da turma nunca muda.
- R4 (RN-AGE-04, RN-AGE-02): toda aula editada fica com `manually_edited = true`, para o refazer de horários da turma (#48) não a apagar.
- R5 (RN-AGE-07, RN-AGE-08, D3): o servidor confere com `Agenda.can`: Professor só com `AGENDA_EDIT` e aula sua (titular, auxiliar ou professor da aula, com a janela do substituto de RN-PRO-06); cancelada não se edita; concluída só por Coordenador ou Administrador, em até 30 dias do término, e só o professor (data e horário não mudam), com `reason` obrigatório.
- R6 (validação): `endsAt` depois de `startsAt`; o novo início de uma aula não iniciada não fica no passado no fuso da unidade; duas aulas da mesma turma no mesmo dia e início (índice `lesson_class_date_start`) dão 409; campos obrigatórios e formatos inválidos dão 400.
- R7 (RN-AGE-05): `POST /api/v1/agenda/lessons/{id}/cancel` recebe `reason` (`TEACHER_UNAVAILABLE`, `HOLIDAY`, `ACADEMY_EVENT`, `OTHER`), `reasonText` (obrigatório, até 200 caracteres, só em `OTHER`), `channels` (`EMAIL`, `WHATSAPP`; o portal é sempre incluído) e `trialDecision`; marca a aula como cancelada e devolve o `LessonDetail`. Só cancela aula agendada ou em andamento (concluída ou já cancelada: 409).
- R8 (RN-AGE-06): se a aula tem aula experimental marcada (`trialClasses` > 0) e `trialDecision` não vier (`REBOOK` ou `CANCEL_TRIALS`), o cancelamento é recusado com 422; com a decisão, o ponto único de experimentais a executa (hoje não faz nada).
- R9 (RN-AGE-05, D4): "comunicado pronto": a janela mostra os canais (portal fixo, e-mail, WhatsApp só se a turma tem grupo) e, após cancelar, o texto pronto do aviso; o envio é da Sprint 09. Os canais escolhidos ficam na auditoria do cancelamento.
- R10 (motivo, D5): migração acrescenta `lesson.cancel_reason_code`; `cancel_reason` guarda o texto livre de `OTHER`. A tela traduz o código; aula cancelada por dia sem aula (RN-AGE-01) continua mostrando o texto de `cancel_reason`.
- R11 (auditoria): editar grava `lesson.updated` e cancelar grava `lesson.cancelled`, com antes e depois, escopo, quantidade de aulas, motivo e canais, sem dado pessoal além de ids.
- R12 (permissão e isolamento): `AGENDA_EDIT` conferida no servidor na unidade da aula; Aluno e Responsável 403; sem sessão 401; aula inexistente, de outra organização, de unidade fora do alcance ou fora da visibilidade do Professor 404; id inválido 400; sem permissão de editar 403; organização só da sessão, com RLS de `lesson`.
- R13 (telas): no painel, "Editar aula" e "Cancelar aula" abrem janelas quando `canEdit`/`canCancel` (não mais desativados); sucesso atualiza o painel e a agenda; quatro estados nas janelas (enviando, erro com "Tentar de novo", sem permissão, aula não encontrada), 360 px sem rolagem horizontal, axe nos dois temas, textos em `pt-BR.json`, cor nunca é a única indicação.
- R14 (contrato): `docs/openapi.yaml` com as duas rotas e handlers MSW no formato do servidor.
- R15 (RN-AGE-13, RN-TUR-06): nada novo cita lista de espera, presença ou frequência.

## 3. Critérios de aceite

- CA1 (R1, R4): dado aula futura da turma, quando o Coordenador muda data e horário com `THIS`, então 200 com os novos valores, só essa aula muda e `manually_edited` fica verdadeiro.
- CA2 (R2): dado quatro aulas semanais, quando edita a segunda com `FOLLOWING` movendo a data em +1 dia e o horário, então a segunda, a terceira e a quarta mudam (+1 dia, novo horário), a primeira não, e uma aula cancelada ou concluída entre elas não muda; se uma delas colidir, 409 e nenhuma muda.
- CA3 (R3): dado professor de outra unidade que ensina a modalidade, quando é informado, então `substitute` verdadeiro, `head` traz o titular e a turma segue com o mesmo titular; informar o titular volta `substitute` a falso; professor inativo, de outra organização ou que não ensina a modalidade dá 422 (campo `teacherId`).
- CA4 (R5): dado Professor com `AGENDA_EDIT` em aula da turma dele, então edita; sem a permissão, ou em aula alheia, 403 ou 404 conforme a visibilidade; substituto só edita no dia da aula; aula cancelada: 409; concluída há 5 dias: Professor 409, Administrador troca só o professor com `reason`; sem `reason` 400; concluída há 40 dias: 409; concluída com data ou horário diferentes: 400.
- CA5 (R6): dado `endsAt` antes de `startsAt`, início no passado, data mal formada, `scope` inválido ou professor ausente, então 400 com o campo; dado mesmo dia e início de outra aula da turma, 409.
- CA6 (R7): dado aula agendada, quando cancela com cada motivo, então 200, `status` `CANCELLED`, `cancelReason` conforme o motivo; `OTHER` sem texto ou com mais de 200 caracteres: 400; `reasonText` com motivo diferente de `OTHER`: ignorado; canal inválido: 400; aula concluída ou já cancelada: 409; Professor com `AGENDA_EDIT` cancela a aula dele.
- CA7 (R8): dado `trialClasses` > 0 (regra testada de forma unitária, pois o ponto único vale 0), quando cancela sem `trialDecision`, então 422; com `REBOOK` ou `CANCEL_TRIALS`, cancela e chama o ponto de experimentais.
- CA8 (R9, R11): dado cancelamento com `EMAIL` e `WHATSAPP`, então a auditoria `lesson.cancelled` traz motivo e os canais (portal incluído); editar grava `lesson.updated` com antes e depois e escopo; a auditoria não traz senha nem token; falha no meio não deixa auditoria nem alteração.
- CA9 (R12): dado Aluno e Responsável, então 403; sem sessão 401; id inválido 400; aula de B pela sessão de A, 404 (e a aula de B segue intacta); Administrador de A em unidade fora do alcance 404; Professor sem `AGENDA_EDIT` 403; com `ctcore_app` sem escopo nenhuma linha de `lesson` de B é visível ou alterável.
- CA10 (R10): dado aula cancelada por `HOLIDAY`, então `cancelReasonCode` vem na lista e no painel e a tela mostra o texto traduzido; aula cancelada por dia sem aula continua com o texto.
- CA11 (R13): dado o painel, quando abre "Editar aula" e salva (só esta; esta e as seguintes), então a janela fecha e o painel e a agenda mostram o novo horário e o selo "Substituição" ao trocar o professor; ao abrir "Cancelar aula", escolher motivo e canais, a janela pede o texto em "Outro" e, ao confirmar, o painel mostra "Cancelada", o motivo e o texto do aviso pronto; erro 409 e 422 aparecem na janela com a mensagem.
- CA12 (R13): dado carregando, erro 500 com "Tentar de novo", 403 e 404, então cada um aparece com seu texto; 360 px sem rolagem horizontal; axe sem violações nos temas claro e escuro; botões desativados quando `canEdit`/`canCancel` forem falsos.
- CA13 (R14): `OpenApiContractTest` e `ContractRoutesTest` passam com as rotas novas; teste MSW cobre as duas no formato do servidor.
- CA14 (R15): grep do esquema, do corpo e de `pt-BR.json` sem lista de espera, presença ou frequência.
- CA15 (R13): ponta a ponta `@critical` da Agenda estendido: o Coordenador abre o painel, edita o horário e cancela outra aula.

## 4. Plano técnico

- Dados: migração `V26__lesson_cancel_reason_code.sql` (a criar): `alter table lesson add column cancel_reason_code text` com `check` nos quatro códigos e comentário de reversão manual no padrão das anteriores; sem tabela nova, a RLS de V25 vale; sem seed novo.
- API: `PUT /api/v1/agenda/lessons/{id}` (200 `LessonDetail`; 400, 401, 403, 404, 409, 422) e `POST /api/v1/agenda/lessons/{id}/cancel` (mesmos códigos); cookie como as demais; `cancelReasonCode` entra em `Lesson` e `LessonDetail`. Entram em `docs/openapi.yaml` no mesmo PR.
- Back-end (pacote `br.com.ctcore.agenda`): a criar `LessonWrite.java` (`update`, `cancel`, records de entrada, auditoria com `AuditService`/`AuditEntry` no padrão de `TrainingClassWrite`, transação única por `OrganizationTransactions`); alterar `Agenda.java` (extrair o carregamento por id de `lesson` num método comum com a regra de visibilidade, sem mudar a leitura; reusar `can`, `status`, `substituteWindow`; incluir `cancelReasonCode`); alterar `AgendaController.java` (`@PutMapping`, `@PostMapping("/lessons/{id}/cancel")`, `HttpServletRequest` para o IP). O professor é validado por consulta a `teacher` (ativo) e `teacher_modality`; `FOLLOWING` roda num `update` só, com colisão tratada como 409 pela violação do índice único. O ponto único de experimentais é `Agenda.trialClasses` mais um método `resolveTrials(...)` sem efeito até a Sprint 18.
- Interface: a criar em `frontend/src/features/agenda` `EditLessonDialog.tsx`, `CancelLessonDialog.tsx`, `useLessonActions.ts` e testes; alterar `LessonPanelPage.tsx` (botões abrem as janelas), `useLesson.ts` e `useAgenda.ts` (tipo `cancelReasonCode`, invalidar consultas), `LessonCard.tsx` se mostra o motivo, `mocks/handlers.ts` (+ `mocks/agenda.msw.test.ts`), `i18n/pt-BR.json` (`agenda.edit.*`, `agenda.cancel.*`), `e2e/agenda.spec.ts`. Reusa `ClassFormDialog.tsx` como padrão de janela, `states`, `Button`, `modalityHue`. As opções de professor vêm de `teachers` em `/agenda` (já traz os da unidade) ou de rota existente de professores por modalidade, a confirmar na construção; o servidor recusa com 422 o que não servir.
- Segurança: perfil, organização e alcance só da sessão; `Agenda.can` reconferido na gravação (o front é só dica); parâmetros ligados; 404 sem revelar existência; sem dado pessoal na auditoria; `reasonText` limitado a 200 caracteres e exibido como texto, nunca HTML.
- Reuso: `Agenda.status/can/substituteWindow`, `Permissions.grant`, `OrganizationTransactions`, `AuditService`, `ApiStatusException`, `ApiValidationException`, `AgendaBase` (testes), `LessonGenerator` (não muda; o refazer de #48 já respeita `manually_edited`).
- Riscos: (1) `FOLLOWING` mexe em várias linhas: mesma transação, testado com colisão; (2) o gerador diário (RN-AGE-01) pode recriar a aula na data e hora antigas de uma aula deslocada: conferir em teste (D6); (3) fuso: o início no passado é medido no fuso da unidade; (4) "ensina a modalidade" depende de `teacher_modality`, percebido pelo teste do CA3.

## 5. Tarefas

- [x] T1: migração V26 e `cancelReasonCode` em `Lesson`/`LessonDetail`, com extração do carregamento por id em `Agenda` — cobre CA10 (servidor) — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test='AgendaLessonIT,AgendaIT'` (nada quebra) e Flyway aplicado.
- [x] T2: `LessonWrite.update` e a rota `PUT` (escopo, professor, substituição, `manually_edited`, validações, janela de concluída, auditoria, 409 em colisão, gerador sem recriar a data antiga) — cobre CA1, CA2, CA3, CA4, CA5, CA8 (edição), CA9 (edição) — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test=AgendaLessonEditIT` e unitário das regras.
- [x] T3: `LessonWrite.cancel` e a rota `POST` (motivo, canais, RN-AGE-06, auditoria), com unitário da regra de experimentais — cobre CA6, CA7, CA8 (cancelamento), CA9 (cancelamento), CA14 — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test=AgendaLessonCancelIT` e `test -Dtest='LessonWrite*Test'`.
- [x] T4: OpenAPI das duas rotas e handlers MSW com teste — cobre CA13 — prova: `./backend/mvnw -f backend/pom.xml test -Dtest='OpenApiContractTest,ContractRoutesTest'` e `npx vitest run src/mocks` em `frontend/`.
- [x] T5: `EditLessonDialog`, `useLessonActions`, textos `agenda.edit.*`, ligação no painel — cobre CA11 (edição), CA12 — prova: `npx vitest run src/features/agenda` (quatro estados, 360 px, axe nos dois temas).
- [x] T6: `CancelLessonDialog`, textos `agenda.cancel.*`, motivo traduzido na lista e no painel — cobre CA10 (tela), CA11 (cancelamento), CA12 — prova: `npx vitest run src/features/agenda`.
- [x] T7: estender o e2e `@critical` da Agenda — cobre CA15 — prova: `npm run e2e` em `frontend/`.
- [x] T8: regressão completa (não cobre critério: confere cobertura e os 100% nos pacotes exigidos) — prova: `./backend/mvnw -f backend/pom.xml verify` e, em `frontend/`, `npm run lint && npm run typecheck && npm run build && npm run test:cov && npm run e2e`.

## 6. Decisões

- D1 (muda o comportamento): "esta e as seguintes" desloca a data das seguintes pelo mesmo número de dias da mudança desta, preservando o ritmo semanal, e aplica o novo horário e professor a todas; atinge só aulas da turma ainda não iniciadas, sem cancelar nem concluir. Alternativa: aplicar só horário e professor, sem data; descartada porque o card diz que a edição muda a data.
- D2 (muda o comportamento): a validação "o substituto ensina a modalidade" (RN-PRO-06), deixada para este item por #50, é feita ao gravar: professor ativo da organização que ensina a modalidade; sem exigir a unidade. Alternativa: exigir a unidade; descartada porque RN-PRO-06 admite outra unidade.
- D3 (muda o comportamento): aula concluída (até 30 dias) só tem o professor corrigido, por Coordenador ou Administrador, com motivo obrigatório e auditoria (RN-AGE-08); data e horário não mudam. Alternativa: liberar tudo; descartada pelo texto "corrige professor ou substituto".
- D4 (muda o comportamento): como o módulo de comunicados é da Sprint 09, "abre o comunicado pronto" vira: escolha de canais na janela, texto pronto mostrado após cancelar e canais gravados na auditoria; nada é enviado e "Avisar turma" segue desativado. Alternativa: criar a tabela de comunicados agora; descartada por pertencer à Sprint 09. Suposição: o texto pronto sai de `pt-BR.json` com turma, data e motivo; se o dono quiser outro texto, é ajuste de tradução.
- D5: o motivo vira código em coluna nova (`cancel_reason_code`) para a tela traduzir sem texto no servidor; `cancel_reason` guarda só o texto livre de "Outro". Alternativa: gravar o texto em português; descartada por ferir "textos de tela em tradução".
- D6: o gerador diário não pode recriar a aula na data e hora antigas depois de uma edição; se recriar, o ajuste entra em T2 com teste, sem mudar a regra RN-AGE-01.
- D7: edição é `PUT` e cancelamento `POST .../cancel`, como `classes` (`PUT /{id}`, `POST /{id}/deactivate`); sem controle de versão (`If-Match`): vale a última gravação, pois a aula não tem versão e o risco é baixo. Alternativa: `If-Match`; descartada por exigir coluna e tratamento que o card não pede.
- D8: o canvas (`AulaEditar.dc.html`, `AulaCancelar.dc.html`) não está acessível aqui; as janelas seguem os padrões do projeto (#48) e o dono confere o layout no PR.
- D9: o cancelamento é só da aula do id; cancelar várias aulas é da RF-AGE-05 (#53).

## Construção

- T1 — arquivos: `V26__lesson_cancel_reason_code.sql`, `Agenda.java` (`Loaded`, `load`, `cancelReasonCode`, `resolveTrials`) — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test='AgendaLesson*IT,AgendaIT,LessonGenerationIT,TrainingClass*IT'` → 47 testes, 0 falhas; Flyway aplicado pelos IT.
- T2 — arquivos: `LessonWrite.java` (update), `AgendaController.java` (PUT), `LessonGenerator.java` (não recria slot original), `ErrorCode.UNPROCESSABLE` + `messages.properties`, `AgendaBase.java` (limpa `teacher_modality`), `AgendaLessonEditIT.java` — prova: `verify -Dit.test=AgendaLessonEditIT` → 6 testes, 0 falhas (CA1–CA5, CA8, CA9, D6).
- T3 — arquivos: `LessonWrite.java` (cancel), `AgendaController.java` (POST), `AgendaLessonCancelIT.java`, `LessonWriteTest.java` — prova: `verify -Dit.test=AgendaLessonCancelIT -Dtest='LessonWrite*Test'` → 2 IT + 1 unitário, 0 falhas (CA6–CA10, CA14 sem termos proibidos).
- T4 — arquivos: `docs/openapi.yaml` (PUT, POST, `LessonEditInput`, `LessonCancelInput`, `cancelReasonCode`, `UNPROCESSABLE`), `mocks/handlers.ts`, `mocks/agenda.msw.test.ts` — prova: `test -Dtest='OpenApiContractTest,ContractRoutesTest'` → 7 testes ok; `npx vitest run src/mocks/agenda.msw.test.ts` → 11 ok.
- T5 — arquivos: `EditLessonDialog.tsx`, `useLessonActions.ts`, `LessonPanelPage.tsx`, `pt-BR.json` (`agenda.edit.*`), testes em `LessonPanelPage.test.tsx` — prova: `npx vitest run src/features/agenda` → 34 ok (quatro estados, 360 px, axe nos dois temas).
- T6 — arquivos: `CancelLessonDialog.tsx`, `LessonCard.tsx` (`cancelReasonText`), `useAgenda.ts`, `pt-BR.json` (`agenda.cancel.*`, `agenda.cancelReasons`, aviso pronto no painel) — prova: mesma execução, 34 ok.
- T7 — arquivos: `frontend/e2e/agenda.spec.ts` — prova: `npm run e2e` → 27 passed.
- T8 — prova abaixo.

Regressão: `./backend/mvnw -f backend/pom.xml verify` → 346 testes, 0 falhas, BUILD SUCCESS (JaCoCo ok); em `frontend/`: `npm run lint` ok, `npm run typecheck` ok, `npm run build` ok, `npm run test:cov` → 73 arquivos, 637 testes, limites de cobertura ok, `npm run e2e` → 27 passed. Sem falha anterior a citar.
Desvios:
- Nova coluna `original_date`/`original_starts_at` em V26 (além de `cancel_reason_code`): guarda o horário gerado da aula editada, para o gerador diário não recriá-la (D6, risco 2). `LessonGenerator` ganhou um `not exists`.
- Novo código de erro `UNPROCESSABLE` (422), pois a API não tinha 422; entrou no enum, em `messages.properties` e no enum do OpenAPI. A resposta 422 está declarada inline nas duas rotas (o teste de convenções fixa 10 respostas reutilizáveis).
- `FOLLOWING` atualiza linha a linha (ordem conforme o sentido do deslocamento), não num único `update`, para o índice único não acusar colisão passageira; tudo na mesma transação.
- Opções de professor da janela vêm de `GET /agenda?unitId=` (professores ativos da unidade da aula); o servidor valida o resto (422).
- `AgendaBase.clean` passou a apagar `teacher_modality` (necessário aos novos testes).
- O mock MSW guarda edições por id de aula em memória (`lessonEdits`); `FOLLOWING` no mock só edita a aula do id.
- Isolamento com `ctcore_app` sem escopo não ganhou teste novo: a RLS de `lesson` é a da V25 e não mudou.
Não é do item: nenhum.

## Verificação

### Rodada 1 — OK — 2026-10-19, estado 9162f53c83ec5df3a691c79daf5371fa88f88301

| Critério | Status | Evidência |
|---|---|---|
| CA1 | PASSOU | `AgendaLessonEditIT.thisLesson` no `verify` (346 testes, 0 falhas) |
| CA2 | PASSOU | `AgendaLessonEditIT.following` (inclui colisão e aulas canceladas/concluídas) |
| CA3 | PASSOU | `thisLesson`/`validation` (substitute, titular, 422 em `teacherId`); lido `checkTeacher` |
| CA4 | PASSOU | `AgendaLessonEditIT.states` e `access` |
| CA5 | PASSOU | `AgendaLessonEditIT.validation`; lido `clean` |
| CA6 | PASSOU | `AgendaLessonCancelIT.reasons` e `rules` |
| CA7 | PASSOU | `LessonWriteTest` (regra unitária); `requireTrialDecision` lido |
| CA8 | PASSOU | auditoria conferida em `thisLesson`, `reasons`, e falha sem auditoria (linhas 98 e 223) |
| CA9 | PASSOU | 403/401/400/404 em `access`; RLS com `ctcore_app` coberto por `AgendaLessonIT` (leitura, política da V25) |
| CA10 | PASSOU | `reasons` (cancelReasonCode na lista); front em `LessonPanelPage.test.tsx` |
| CA11 | PASSOU | `LessonPanelPage.test.tsx` (vitest 637 ok) |
| CA12 | PASSOU | mesmos testes (quatro estados, 360 px, axe) |
| CA13 | PASSOU | `OpenApiContractTest`/`ContractRoutesTest` dentro do `verify`; `agenda.msw.test.ts` |
| CA14 | PASSOU | sem termos proibidos no `pt-BR.json` (grep vazio); IT de cancelamento |
| CA15 | PASSOU | `npm run e2e` → 27 passed |

Comandos: `./backend/mvnw -f backend/pom.xml verify` → 346 testes, 0 falhas, BUILD SUCCESS; em `frontend/`: `npm run lint`, `typecheck`, `build`, `test:cov` (73 arquivos, 637 testes) ok; `npm run e2e` → 27 passed.
Achados:
- sugestão, código, `AgendaLessonEditIT`/`AgendaLessonCancelIT` — CA9 pede "alterável" com `ctcore_app` sem escopo; só a leitura é provada (em `AgendaLessonIT`). Acrescentar um `update` sem escopo que afete 0 linhas.
- sugestão, especificação — a migração V26 cria também `original_date`, `original_starts_at` e índice (desvio declarado em "Construção"; coerente com D6).

## Entrega

- Edição (PUT) e cancelamento (POST) da aula do id, com escopo "esta aula" ou "esta e as seguintes" na edição; motivo de cancelamento com código (V26), decisão sobre experimentais, auditoria e erro 422 novo (`UNPROCESSABLE`). Front: janelas de editar e cancelar no painel da aula, textos em pt-BR, quatro estados, 360 px e axe nos dois temas.
- Verificação (rodada 1, OK): `verify` do back-end com 346 testes e 0 falhas; no front lint, typecheck, build, `test:cov` (637 testes) e `e2e` (27) verdes.
- Decisões: colunas `original_date`/`original_starts_at` para o gerador diário não recriar a aula editada; "esta e as seguintes" atualiza linha a linha na mesma transação; no mock MSW, o escopo "seguintes" edita só a aula do id.
- Limitação: o teste de `update` sem escopo com `ctcore_app` (CA9) não foi acrescentado; a RLS de `lesson` é a da V25 e não mudou.

## Revisão pelo dono (20/10/2026)

- D1 mantida e acrescida: antes de confirmar "esta e as seguintes", a janela mostra quantas aulas já alteradas à mão serão substituídas (`followingEdited` em `GET /agenda/lessons/{id}`). O professor da aula (troca e correção) ensina a modalidade da aula, inclusive o titular (RN-PRO-09).
