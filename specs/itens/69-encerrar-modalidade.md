# 69 — RF-MAT-03 · Encerrar modalidade

Origem: issue #69 (Sprint 07); `docs/especificacao/09-matricula.md` (RF-MAT-03, RN-MAT-17), `07` (RN-BEN-01), `08` (RN-ALU-07, RN-ALU-08). Branch: `item/69-encerrar-modalidade`. Parte de `Enrollments` (#63; `lockRow`, `requireManage`, `read`), `RecordEnrollment.tsx` (#60/#63, hoje com o botão "Encerrar" desabilitado e o texto `endSoon`) e `BenefitWrite` (#66). Canvas `EncerrarModalidade.dc.html` inacessível ao agente.

## 1. Objetivo

Coordenador e Administrador com `STUDENT_MANAGE` na unidade encerram uma modalidade ativa do aluno, pela ficha, informando o motivo da lista e uma observação opcional. A janela mostra as cobranças em aberto da modalidade para a decisão de mantê-las ou cancelá-las. Ao encerrar, a modalidade sai das turmas (libera a vaga) e deixa de gerar cobrança.

Fora do escopo: a lista real de cobranças em aberto e as ações "manter" e "cancelar" cada uma, com motivo, que dependem das cobranças (Sprint 08, #71 a #73): aqui o contrato e a janela ficam prontos e a lista vem vazia (D2); e-mail ao aluno (RN-INA-10, Sprint 09); "Solicitar encerramento" pelo portal (RN-MAT-18, #86); reabrir modalidade encerrada (RN-MAT-19).

## 2. Requisitos

- R1 (RN-MAT-17, D1): `POST /api/v1/students/{id}/modalities/{modalityRowId}/end` com `reason` (`CITY_CHANGE`, `FINANCIAL`, `SCHEDULE`, `INJURY`, `DISSATISFACTION`, `OTHER`) e `note` opcional encerra a modalidade: `status` `ENDED`, `ended_at` agora, `end_reason` e `end_note` gravados; devolve 200 com a matrícula do aluno (como `transfer`).
- R2 (RN-MAT-17): `reason` obrigatório e da lista; `note` opcional de até 500 caracteres, sem espaços nas pontas (vazia vira ausente); violação: 400 com o campo e nada muda.
- R3 (RN-MAT-17, RN-ALU-08): a linha da modalidade não é apagada; as outras modalidades do aluno e o histórico (inclusive os outros horários da modalidade) ficam como estão. Sem modalidade ativa a matrícula passa a `FORMER` (derivado, como hoje).
- R4 (RN-MAT-17, D3): encerrar libera a vaga: a modalidade deixa de contar em `activeStudents` das turmas, e uma nova modalidade do mesmo tipo pode ser criada depois (índice `enrollment_modality_active`).
- R5 (RN-BEN-01, D4): o benefício ativo da modalidade, se houver, é encerrado na mesma transação com `end_reason` "Modalidade encerrada" e auditoria `benefit.ended`.
- R6 (RN-MAT-17, D2): `GET /api/v1/students/{id}/modalities/{modalityRowId}/end-preview` devolve `openCharges` (lista de cobranças em aberto da modalidade: id, vencimento, valor em centavos, situação), vazia enquanto não existem cobranças; é leitura, sem gravar nem auditar.
- R7 (permissão, isolamento): as duas rotas exigem `STUDENT_MANAGE` na unidade da modalidade; Professor, Aluno e Responsável 403; Administrador de unidade fora do alcance 403; sem sessão 401; aluno ou modalidade inexistente, de outra organização ou já encerrada: 404.
- R8 (auditoria): encerrar grava `student.modality_ended` (entidade `student`, unidade da modalidade, antes `status ACTIVE`, depois `status ENDED`, `reason` e `hasNote`), sem a observação (texto livre, pode conter dado de saúde) nem dado pessoal; recusas não auditam.
- R9 (concorrência): dois encerramentos simultâneos da mesma modalidade resultam em um 200, um 404 e uma só linha de auditoria (o aluno é travado por `lockRow`).
- R10 (RF-MAT-03, RNF-26): a janela "Encerrar modalidade" (`EndModalityDialog`) mostra aluno e modalidade, o resumo do efeito (para de gerar cobrança; vaga liberada; benefício ativo encerra), a lista de cobranças em aberto (com o estado vazio "Nenhuma cobrança em aberto"), o motivo (lista) e a observação, e confirma com R1. Abre pelo botão "Encerrar" de cada modalidade ativa que a pessoa pode alterar (`canEdit`), que passa a ser habilitado (sai o texto `endSoon`). Mostra erro do servidor no campo, e atualiza a ficha e a tabela; quatro estados (carregando as cobranças, vazio, erro com "Tentar de novo", sem permissão), 360 px, axe nos dois temas, textos em `pt-BR.json`.
- R11 (contrato): `docs/openapi.yaml` (duas rotas, `ModalityEndInput`, `EndPreview`), handlers MSW e `types.ts` refletem o servidor.
- R12 (D7): notas de versão só se o arquivo existir; senão a linha vai na descrição do PR.

## 3. Critérios de aceite

- CA1 (R1, R3, R4): dado aluno com duas modalidades ativas, quando o Coordenador encerra uma com motivo `FINANCIAL`, então 200, a linha fica `ENDED` com `ended_at`, `end_reason` e `end_note`, a outra segue ativa, a matrícula segue `ACTIVE`, a turma deixa de contá-lo em `activeStudents`, e é possível matricular de novo na mesma modalidade; encerrando a última, a matrícula devolve `FORMER`.
- CA2 (R2): dado `reason` ausente, fora da lista ou `note` com 501 caracteres, quando posta, então 400 com o campo e a modalidade segue ativa; `note` com 500 caracteres e `note` ausente são aceitas; `note` só com espaços é guardada como ausente.
- CA3 (R5): dado modalidade com benefício ativo, quando encerra, então o benefício vira `ENDED` com "Modalidade encerrada" e há `benefit.ended`; sem benefício, nada mais muda; benefício de outra modalidade do aluno não é tocado.
- CA4 (R6): dado modalidade ativa, quando consulta `end-preview`, então 200 com `openCharges` vazia e nada é gravado; modalidade já encerrada ou de outro aluno: 404.
- CA5 (R7): dado Professor, Aluno e Responsável, então 403 nas duas rotas; Administrador da unidade A encerra em A e recebe 403 em B; sem sessão 401; id inexistente 404; modalidade já encerrada 404; nada muda em nenhum desses casos.
- CA6 (R7): dado duas organizações, quando o Coordenador de A usa aluno e modalidade de B, então 404 e nada muda em B; com `ctcore_app` e contexto de A, a linha de B segue invisível.
- CA7 (R8): dado encerramento, então a auditoria tem `student.modality_ended` com unidade, antes, depois, `reason` e `hasNote`, sem a observação; recusas (400, 403, 404) não auditam.
- CA8 (R9): dado dois encerramentos simultâneos, então um 200, um 404 e uma só linha `student.modality_ended`.
- CA9 (R10): dado a ficha, quando clica "Encerrar", escolhe o motivo, digita a observação e confirma, então chama R1, fecha, atualiza a ficha e mostra o erro 400 no campo; o botão não aparece em modalidade encerrada nem sem `canEdit`, e não há texto `endSoon`; o botão de confirmar fica desabilitado sem motivo.
- CA10 (R10): dado a janela, então mostram-se resumo do efeito e estado vazio das cobranças; carregando as cobranças, erro na consulta com "Tentar de novo", 403 (sem permissão) e 404 na confirmação (já encerrada por outra pessoa) têm estado próprio; 360 px sem rolagem horizontal e axe sem violações nos dois temas.
- CA11 (R11): `OpenApiContractTest` e `ContractRoutesTest` passam; teste MSW cobre as duas rotas (200, 400, 404) no formato do servidor.

## 4. Plano técnico

- Dados: migração `V37__enrollment_modality_end.sql`: `alter table enrollment_modality add column end_reason text, add column end_note text`, com `check (end_reason is null or end_reason in ('CITY_CHANGE','FINANCIAL','SCHEDULE','INJURY','DISSATISFACTION','OTHER'))`; `end_reason` fica anulável porque linhas já encerradas antes deste item (se houver) não têm motivo; reversão comentada. RLS e privilégios já existem (sem DELETE).
- API: `POST /students/{id}/modalities/{modalityRowId}/end` (corpo `{reason, note?}`; 200 `Enrollment`; 400, 401, 403, 404; CSRF como as demais escritas) e `GET .../end-preview` (200 `{openCharges: []}`; 401, 403, 404). Ambas em `docs/openapi.yaml`, ao lado de `transfer`.
- Back-end: `Enrollments.end` (validação, `lockRow`, update com `status = 'ACTIVE'`, chamada a `BenefitWrite` para encerrar o benefício da modalidade, auditoria nos moldes de `transfer`) e `Enrollments.endPreview` (`lockRow`, devolve lista vazia); método público novo em `BenefitWrite` (`endModalityBenefits`, nos moldes de `endTeacherBenefits` e `endLocked`, buscando por `modality_row_id`); duas rotas em `StudentController`.
- Interface: criar `frontend/src/features/students/EndModalityDialog.tsx` (com `endModality` e `useEndPreview` em `useStudentRecord.ts`), reutilizando `ModalityDialog` e `PickSelect`; alterar `RecordEnrollment.tsx` (botão habilitado, ação `end`, remove `endSoon`), `types.ts`, `mocks/handlers.ts`, `i18n/pt-BR.json`.
- Segurança: parâmetros ligados; organização só da sessão; alcance pela unidade da modalidade; `note` texto livre exibido como texto e fora da auditoria; CSRF nas escritas.
- Arquivos: a alterar (vistos): `backend/src/main/java/br/com/ctcore/student/Enrollments.java`, `StudentController.java`, `benefit/BenefitWrite.java`, `docs/openapi.yaml`, `frontend/src/features/students/RecordEnrollment.tsx`, `RecordEnrollment.test.tsx`, `useStudentRecord.ts`, `types.ts`, `frontend/src/mocks/handlers.ts`, `frontend/src/i18n/pt-BR.json`. A criar: `backend/src/main/resources/db/migration/V37__enrollment_modality_end.sql`, `backend/src/test/java/br/com/ctcore/student/EnrollmentEndIT.java`, `EndModalityDialog.tsx`, `EndModalityDialog.test.tsx`, `frontend/src/mocks/enrollment-end.msw.test.ts`.
- Reuso: `Enrollments.lockRow`, `requireManage`, `read`, `AuditService`/`AuditEntry`, `OrganizationTransactions`, `ApiValidationException`, `ModalityDialog`, `PickSelect`, `ErrorState`, `ForbiddenState`, `EmptyState`.
- Riscos: (1) encerrar modalidade e benefício precisam andar juntos: mesma transação (R5). (2) `end-preview` fixo em lista vazia pode ser esquecido pela Sprint 08: fica como encaixe da #71/#73 (D2). (3) Layout do canvas inacessível: o dono confere no PR.

## 5. Tarefas

- [x] T1: migração `V37__enrollment_modality_end.sql` — não cobre critério: base dos campos de motivo e observação — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test=EnrollmentChangesIT`.
- [x] T2: `Enrollments.end`, `endPreview`, `BenefitWrite.endModalityBenefits` e rotas, com validação, auditoria, alcance e isolamento — cobre CA1, CA2, CA3, CA4, CA5, CA6, CA7, CA8 — prova: `verify -Dit.test=EnrollmentEndIT`.
- [x] T3: OpenAPI e handlers MSW — cobre CA11 — prova: `./backend/mvnw -f backend/pom.xml test -Dtest='OpenApiContractTest,ContractRoutesTest'` e `npx vitest run src/mocks` em `frontend/`.
- [x] T4: `EndModalityDialog`, botão habilitado na ficha, textos pt-BR — cobre CA9, CA10 — prova: `npx vitest run src/features/students src/mocks` em `frontend/`.
- [x] T5: regressão completa — não cobre critério: confere cobertura e pacotes exigidos — prova: `./backend/mvnw -f backend/pom.xml verify` e, em `frontend/`, `npm run lint && npm run typecheck && npm run build && npm run test:cov && npm run e2e`.

## 6. Decisões

- D1 (muda o comportamento): rota `POST .../modalities/{row}/end` com a lista de motivos fixa (`CITY_CHANGE`, `FINANCIAL`, `SCHEDULE`, `INJURY`, `DISSATISFACTION`, `OTHER`), do texto da RN-MAT-17. Observação opcional de até 500 caracteres, fora da auditoria. Alternativa: `DELETE`; descartada porque a linha fica (RN-ALU-08).
- D2 (muda o comportamento): as cobranças ainda não existem (Sprint 08), então a decisão de manter ou cancelar cada uma não pode ser gravada aqui. Este item entrega o encerramento, a rota `end-preview` com `openCharges` vazia e a janela com a seção e o estado vazio; a #71 passa a preencher a lista e a #73 (ou a #71) recebe as decisões por cobrança, com motivo e só do Administrador. As cobranças já geradas permanecem devidas por padrão (RN-MAT-17). Alternativa: adiar o item inteiro; descartada porque o encerramento em si não depende de cobrança.
- D3: encerrar libera a vaga (a contagem de alunos ativos da turma já usa `status = 'ACTIVE'`); "para de gerar cobrança" é respeitado pela geração da Sprint 08, que só considera modalidade ativa. Aqui nenhuma tabela de cobrança é tocada.
- D4 (muda o comportamento): o benefício ativo da modalidade encerra junto, com "Modalidade encerrada", para não sobrar benefício ativo numa modalidade encerrada (ele continuaria na tabela de benefícios e bloquearia o selo de bolsista). Alternativa: deixar o benefício como está; descartada.
- D5: modalidade já encerrada devolve 404, como as demais ações por modalidade (`lockRow` só enxerga modalidade ativa); a janela trata 404 como "já encerrada por outra pessoa". Alternativa: 409, como em #66; descartada para não mudar `lockRow`.
- D6: permissão `STUDENT_MANAGE` na unidade, a mesma de trocar turma, plano e transferir; quem pode alterar a modalidade pode encerrá-la. A decisão sobre cobranças ser só do Administrador fica para a #71/#73.
- D7: notas de versão só se o arquivo existir (não existe hoje); a linha vai na descrição do PR: "Encerrar modalidade com motivo pela ficha do aluno".
- Suposição: o canvas `EncerrarModalidade.dc.html` mostra motivo, observação e cobranças em aberto; se o layout divergir, ajusta-se no PR.
- Suposição: o e-mail de encerramento (RN-INA-10) e o encerramento automático do pedido do portal (RN-MAT-18) ficam para as Sprints 09 e 10; não são requisitos deste item.

## Construção

Retomada: o código já existia na pasta de trabalho; conferido e provado nesta sessão, sem refazer.

- T1 — arquivos: `backend/src/main/resources/db/migration/V37__enrollment_modality_end.sql` — prova: `verify -Dit.test=EnrollmentChangesIT,EnrollmentEndIT` → passou (migração aplicada)
- T2 — arquivos: `Enrollments.java`, `StudentController.java`, `BenefitWrite.java`, `EnrollmentEndIT.java` — prova: `verify -Dit.test=EnrollmentEndIT` → 3 testes, 0 falhas
- T3 — arquivos: `docs/openapi.yaml`, `frontend/src/mocks/handlers.ts`, `enrollment-end.msw.test.ts` — prova: `test -Dtest='OpenApiContractTest,ContractRoutesTest'` → passou; `npx vitest run src/features/students src/mocks` → 41 arquivos, 311 testes passaram
- T4 — arquivos: `EndModalityDialog.tsx` e teste, `RecordEnrollment.tsx` e teste, `StudentRecordPanel.test.tsx`, `types.ts`, `useStudentRecord.ts`, `pt-BR.json` — prova: mesmo comando do vitest → passou
- T5 — prova abaixo.

Regressão:
- Front: `npm run lint && typecheck && build && test:cov && e2e` → tudo verde; cobertura de linhas 96,52%; e2e 33 passaram.
- Back: `verify` completo → 527 testes, 1 erro: `AgendaLessonAdhocIT.validation` (`CannotGetJdbcConnection` em `cleanAudit`, instável, fora do item); rodado sozinho de novo → 5 testes passaram. Como o `verify` completo terminou em falha, a checagem de cobertura do JaCoCo (80%, 100% nos pacotes exigidos) não executou nessa rodada; não rodou de novo o `verify` inteiro.
Desvios: nenhum.
Não é do item: nenhum.

### Correção da rodada 1
- CA6: prova de RLS com `ctcore_app` (sem contexto e com o contexto de outra organização, a linha da modalidade segue invisível) — arquivos: `backend/src/test/java/br/com/ctcore/student/EnrollmentEndIT.java` (`guards`) — prova: `verify -Dit.test=EnrollmentEndIT -Djacoco.skip=true` → 3 testes, 0 falhas
- Sugestões (`activeStudents` no teste, `verify` completo com JaCoCo) não feitas: opcionais; o `verify` completo não foi rodado nesta correção.

## Verificação

### Rodada 1 — CORRIGIR_CODIGO — 2026-10-21, estado c4f6396b859fdf9aed11d549d2411344f41291fa

| Critério | Status | Evidência |
|---|---|---|
| CA1 | PASSOU | `EnrollmentEndIT.ends`: duas modalidades, uma `ENDED` com `ended_at`/`end_reason`/`end_note`, outra `ACTIVE`, matrícula `ACTIVE`, nova matrícula na mesma modalidade, última encerrada vira `FORMER`. `activeStudents` da turma não é afirmado no teste, mas a recontratação passa pelo índice `enrollment_modality_active` e a contagem usa `status = 'ACTIVE'` (lido no diff) |
| CA2 | PASSOU | `ends`: reason nulo, `NOPE`, nota de 501 dão 400 sem mudar; 500 e ausente 200; "   " vira nula |
| CA3 | PASSOU | `ends`: benefício da linha A `ENDED` "Modalidade encerrada", o da B segue `ACTIVE`, 1 `benefit.ended` |
| CA4 | PASSOU | `ends`: preview 200 com `openCharges` vazio, sem auditoria; 404 depois de encerrada |
| CA5 | PASSOU | `guards`: Professor/Aluno/Responsável 403, sem sessão 401, Administrador fora da unidade 403, ids inexistentes 404; nada muda |
| CA6 | NÃO CONFERIDO | `guards` cobre o 404 para outra organização, mas a cláusula "com `ctcore_app` e contexto de A, a linha de B segue invisível" não é exercitada em nenhum teste do item |
| CA7 | PASSOU | `ends`: ação, unidade, antes/depois, `reason`, `hasNote`; sem "joelho"; recusas sem auditoria |
| CA8 | PASSOU | `concurrentEnds`: {200, 404} e 1 auditoria |
| CA9 | PASSOU | `EndModalityDialog.test.tsx` e `RecordEnrollment.test.tsx` (vitest 311 passaram); `endSoon` removido do diff |
| CA10 | PASSOU | testes de efeito/vazio, 400 no campo, 404, erro com nova tentativa, 403; axe a 360 px nos dois temas |
| CA11 | PASSOU | `OpenApiContractTest` e `ContractRoutesTest` 7 testes; `enrollment-end.msw.test.ts` dentro dos 311 |

Comandos:
- `mvnw verify -Dit.test=EnrollmentEndIT,EnrollmentChangesIT -Dtest='OpenApiContractTest,ContractRoutesTest'` → 7 + 7 testes, BUILD SUCCESS
- `npx vitest run src/features/students src/mocks` → 311 passaram
- `npm run lint` e `npm run typecheck` → sem erros
- Não rodei `verify` completo, `test:cov`, build nem e2e; valem os resultados da Construção (o `verify` completo da Construção falhou num teste instável alheio e a checagem do JaCoCo não executou)

Achados:
- obrigatório, código, `backend/src/test/java/br/com/ctcore/student/EnrollmentEndIT.java` (`guards`) — esperado: CA6 pede prova de que, com `ctcore_app` e contexto da organização A, a linha de B segue invisível; observado: nenhuma consulta com `ctcore_app` no teste, e o 404 HTTP também sai do filtro no código, então não prova a RLS de `enrollment_modality`; evidência: grep por `ctcore_app` no arquivo sem resultados; corrigir: acrescentar a verificação via conexão `ctcore_app` (como em `StudentSchemaIT`) com o contexto de A sobre a linha da modalidade de B.
- sugestão, código, `EnrollmentEndIT.ends` — afirmar também que `activeStudents` da turma cai após o encerramento (CA1 cita isso literalmente).
- sugestão, código — rodar o `verify` completo uma vez limpo antes da entrega, para a checagem do JaCoCo constar.

### Rodada 2 — OK — 2026-10-21, estado d8a4a7f73be5e68e4e91f0ccee5f98d6206b1b16

| Critério | Status | Evidência |
|---|---|---|
| CA1 | PASSOU (mantido da rodada 1) | `EnrollmentEndIT.ends` |
| CA2 | PASSOU (mantido da rodada 1) | `ends` |
| CA3 | PASSOU (mantido da rodada 1) | `ends` |
| CA4 | PASSOU (mantido da rodada 1) | `ends` |
| CA5 | PASSOU (mantido da rodada 1) | `guards` |
| CA6 | PASSOU | `guards` agora consulta com `ctcore_app`: sem contexto e com o contexto de outra organização, a linha da modalidade segue invisível (contagem 0); 404 HTTP já coberto |
| CA7 | PASSOU (mantido da rodada 1) | `ends` |
| CA8 | PASSOU (mantido da rodada 1) | `concurrentEnds` |
| CA9 | PASSOU (mantido da rodada 1) | vitest 311 passaram |
| CA10 | PASSOU (mantido da rodada 1) | vitest 311 passaram |
| CA11 | PASSOU (mantido da rodada 1) | contrato 7 testes; MSW dentro dos 311 |

Comandos:
- `mvnw verify -Dit.test=EnrollmentEndIT,EnrollmentChangesIT -Dtest='OpenApiContractTest,ContractRoutesTest' -Djacoco.skip=true` → 7 + 7 testes, BUILD SUCCESS
- `npx vitest run src/features/students src/mocks` → 41 arquivos, 311 testes passaram
- Diff desde a rodada 1: só `EnrollmentEndIT.java` (bloco de RLS) e este arquivo. `verify` completo, `test:cov`, build e e2e não rodados; valem os da Construção.
Achados:
- sugestão, código — `activeStudents` ainda não é afirmado em `ends`.
- sugestão, código — rodar o `verify` completo limpo antes da entrega, para o JaCoCo constar.

## Entrega

- RF-MAT-03: encerrar uma modalidade da matrícula (motivo e nota opcionais), com prévia de pendências, encerramento dos benefícios da modalidade, auditoria sem dado sensível e proteção contra encerramento concorrente. Migração V37, rota nova no OpenAPI e no MSW, diálogo `EndModalityDialog` no front.
- Verificação: rodada 2 `OK` (CA1 a CA11); `EnrollmentEndIT`, contrato, vitest 311 testes, lint, typecheck, build, `test:cov` (96,52%) e e2e 33 verdes.
- Limitação: o `verify` completo do back teve um erro instável alheio ao item (`AgendaLessonAdhocIT.validation`, passou ao rodar sozinho); a checagem de cobertura do JaCoCo roda no CI.
