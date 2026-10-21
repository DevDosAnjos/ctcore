# 66 — RF-BEN-03 · Encerrar benefício

Origem: issue #66 (Sprint 07), com o bloco "Encaixe herdado da Sprint 04" (RN-PRO-08); `docs/especificacao/07-planos-e-beneficios.md` (RF-BEN-03, RN-BEN-01, 04), `05-professores.md` (RN-PRO-08). Branch: `item/66-encerrar-beneficio`. Constrói sobre #64 (`benefit`, `Benefits`, `BenefitsPage`), #65 (`BenefitWrite`, `GrantBenefitDialog`) e #45 (`TeacherDeactivation`). Canvas `BeneficioEncerrar.dc.html` inacessível ao agente.

## 1. Objetivo

Coordenador (e Administrador nas unidades dele) encerra um benefício ativo, informando o motivo, pela tela de benefícios e pela ficha do aluno. Além disso, o desconto de professor passa a ser identificável (benefício comum marcado com o professor) e inativar o professor encerra esse desconto.

Fora do escopo: aplicar ou parar de aplicar o benefício na cobrança (Sprint 08); notificações; reabrir benefício encerrado.

## 2. Requisitos

- R1 (RF-BEN-03, D1): `POST /api/v1/benefits/{id}/end` com `reason` encerra o benefício: `status` `ENDED`, `ended_at` agora, `end_reason` o motivo; devolve 200 com a `BenefitRow` (status `ENDED`).
- R2 (RF-BEN-03, D2): `reason` de 2 a 200 caracteres, sem espaços nas pontas; violação: 400 com o campo e nada muda.
- R3 (RN-BEN-04): encerrar não altera nenhuma outra linha além de `benefit`; o encerramento vale a partir da próxima cobrança gerada (a Sprint 08 respeita).
- R4 (D3): benefício já `ENDED`: 409. Benefício vencido (`EXPIRED`, ainda `ACTIVE` na tabela) pode ser encerrado.
- R5 (permissão, D4): `BENEFITS` conferido no servidor: Coordenador e Administrador; Professor, Aluno e Responsável 403; sem sessão 401. Administrador só encerra benefício em unidade do alcance dele (403 fora). Benefício inexistente ou de outra organização: 404.
- R6 (auditoria): encerrar grava `benefit.ended` (entidade `benefit`, unidade do benefício, antes `status ACTIVE`, depois `status ENDED` e `endReason`), sem dado pessoal; recusas não auditam.
- R7 (concorrência, D5): a linha é travada (`for update`); dois encerramentos simultâneos resultam em um sucesso e um 409.
- R8 (RN-PRO-08, D6): `benefit.teacher_id` (opcional) marca o benefício como desconto de professor; `POST /benefits` aceita `teacherId` opcional, de professor ativo da organização (senão 404); o preview não muda.
- R9 (RN-PRO-08, D6): inativar o professor (`TeacherDeactivation.deactivate`) encerra, na mesma transação, os benefícios `ACTIVE` com o `teacher_id` dele (`end_reason` "Professor inativado"), gravando `benefit.ended` de cada um; sem benefício, nada muda.
- R10 (RF-BEN-03, D7): a janela "Encerrar benefício" (`EndBenefitDialog`) mostra aluno, modalidade e benefício, pede o motivo, confirma com R1, mostra erro do servidor no campo, atualiza a tabela (e a ficha, quando aberta nela); abre pela ação "Encerrar" nas linhas ativas de `/beneficios` e, na ficha, por modalidade com benefício ativo; quatro estados, 360 px, axe nos dois temas, textos em `pt-BR.json`; ausente para quem não tem `BENEFITS` e na ficha do Professor. A janela de conceder ganha o campo opcional "Desconto de professor" (R8).
- R11 (contrato): `docs/openapi.yaml` (rota nova, `BenefitEndInput`, `teacherId` em `BenefitInput`), handlers MSW e seed refletem o servidor.
- R12 (D8): notas de versão só se o arquivo existir; senão a linha vai na descrição do PR.

## 3. Critérios de aceite

- CA1 (R1, R3): dado benefício ativo, quando o Coordenador faz `POST /benefits/{id}/end` com motivo válido, então 200, `status` `ENDED`, `ended_at` e `end_reason` gravados, a modalidade fica sem ativo e nada fora de `benefit` muda; depois disso é possível conceder outro sem `replaceExisting`.
- CA2 (R2): dado motivo vazio, de 1 ou de 201 caracteres ou corpo ausente, quando posta, então 400 com o campo e o benefício segue ativo; 2 e 200 caracteres são aceitos.
- CA3 (R4): dado benefício encerrado, quando encerra de novo, então 409; dado benefício vencido, então encerra com 200.
- CA4 (R5): dado Professor, Aluno e Responsável, então 403; sem sessão 401; Administrador da unidade A encerra em A e recebe 403 em B; id inexistente 404.
- CA5 (R5): dado duas organizações, quando o Coordenador de A usa o id de benefício de B, então 404 e nada muda em B; com `ctcore_app` e contexto de A, B segue invisível.
- CA6 (R6): dado encerramento, então a auditoria tem `benefit.ended` com unidade, antes e depois; recusas (400, 403, 404, 409) não auditam.
- CA7 (R7): dado dois encerramentos simultâneos do mesmo benefício, então um 200 e um 409, e uma só linha de auditoria.
- CA8 (R8): dado `teacherId` de professor ativo, quando concede, então `benefit.teacher_id` fica gravado; professor inativo ou de outra organização: 404 e nada é gravado.
- CA9 (R9): dado professor com benefício de professor ativo e outro benefício comum de aluno, quando o Coordenador inativa o professor, então só o de professor vira `ENDED` com "Professor inativado", com `benefit.ended` auditado; professor sem benefício inativa como antes; benefício já encerrado não é tocado.
- CA10 (R10): dado `/beneficios` e a ficha, quando clica "Encerrar", digita o motivo e confirma, então chama R1, fecha, atualiza a tabela e a ficha e mostra o erro do servidor no campo; a ação não aparece em benefício encerrado ou vencido na tabela e some para quem não tem `BENEFITS`.
- CA11 (R10): dado a janela, então enviando, erro com "Tentar de novo", sem permissão e vazio (benefício já encerrado por outra pessoa, 409) têm estado próprio; 360 px sem rolagem horizontal e axe sem violações nos dois temas.
- CA12 (R11): `OpenApiContractTest` e `ContractRoutesTest` passam; teste MSW cobre a rota nova (200, 400, 409, 404) e `teacherId`.

## 4. Plano técnico

- Dados: migração `V35__benefit_teacher.sql`: `alter table benefit add column teacher_id uuid`, FK composta `(organization_id, teacher_id)` para `teacher`, índice parcial `(teacher_id) where status = 'ACTIVE'`; reversão comentada. RLS e privilégios de `benefit` já existem (sem DELETE).
- API: `POST /api/v1/benefits/{id}/end` (corpo `{reason}`; 200 `BenefitRow`; 400, 401, 403, 404, 409; CSRF como as demais escritas) e `teacherId` opcional em `BenefitInput`. Entram em `docs/openapi.yaml`.
- Back-end: `benefit/BenefitWrite.end` (permissão por `Plans.requireAccess`; linha buscada com `for update`, alcance pela unidade do benefício como em `target`, 404/403/409, update, auditoria como em `grant`); `BenefitWrite.grant` valida `teacherId` e grava `teacher_id`; método público de `BenefitWrite` chamado por `teacher/TeacherDeactivation.deactivate` dentro da transação existente, com auditoria por benefício; rota no `BenefitController`.
- Interface: criar `frontend/src/features/benefits/EndBenefitDialog.tsx` (com `endBenefit` em `useGrantBenefit.ts`), reutilizando `ModalityDialog`; alterar `BenefitsPage.tsx` (ação "Encerrar" nas linhas ativas com `BENEFITS`), `students/RecordEnrollment.tsx` (ação por modalidade; se a ficha ainda não expõe o benefício ativo da modalidade, a construção registra o desvio e deixa só a tabela), `GrantBenefitDialog.tsx` (campo opcional de professor com a lista de `useTeachers`), `mocks/handlers.ts`, `pt-BR.json`.
- Segurança: parâmetros ligados; organização só da sessão; alcance pela unidade do benefício; `reason` texto livre exibido como texto; auditoria sem dado pessoal.
- Arquivos: a alterar (vistos): `BenefitController.java`, `BenefitWrite.java`, `TeacherDeactivation.java`, `docs/openapi.yaml`, `BenefitsPage.tsx`, `GrantBenefitDialog.tsx`, `useGrantBenefit.ts`, `RecordEnrollment.tsx`, `mocks/handlers.ts`, `pt-BR.json`, `BenefitsPage.test.tsx`, `TeacherDeactivationIT.java`; a criar: `V35__benefit_teacher.sql`, `BenefitEndIT.java`, `EndBenefitDialog.tsx`, `EndBenefitDialog.test.tsx`, `benefits-end.msw.test.ts`.
- Reuso: `AuditService`/`AuditEntry`, `OrganizationTransactions`, `Plans.requireAccess`, `Benefits.find`, `ApiValidationException`, `ErrorCode.CONFLICT`, `ModalityDialog`, `useTeachers`.
- Riscos: (1) inativar o professor e encerrar o desconto precisam andar juntos: mesma transação (R9). (2) A coluna nova herda a RLS de `benefit` (por organização), sem mudança. (3) Layout do canvas inacessível: o dono confere no PR.

## 5. Tarefas

- [x] T1: migração `V35__benefit_teacher.sql` — não cobre critério: base do encaixe RN-PRO-08 — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test=BenefitGrantIT`.
- [x] T2: `BenefitWrite.end` e rota, com validação, 409, travamento, auditoria, alcance e isolamento — cobre CA1, CA2, CA3, CA4, CA5, CA6, CA7 — prova: `verify -Dit.test=BenefitEndIT`.
- [x] T3: `teacherId` na concessão e encerramento na inativação do professor — cobre CA8, CA9 — prova: `verify -Dit.test='BenefitGrantIT,BenefitEndIT,TeacherDeactivationIT'`.
- [x] T4: OpenAPI e handlers MSW — cobre CA12 — prova: `test -Dtest='OpenApiContractTest,ContractRoutesTest'` e `npx vitest run src/mocks` em `frontend/`.
- [x] T5: `EndBenefitDialog`, ação em `/beneficios` e na ficha, campo de professor na concessão, textos pt-BR — cobre CA10, CA11 — prova: `npx vitest run src/features/benefits src/features/students` em `frontend/`.
- [x] T6: regressão completa — não cobre critério: confere cobertura e pacotes exigidos — prova: `./backend/mvnw -f backend/pom.xml verify` e, em `frontend/`, `npm run lint && npm run typecheck && npm run build && npm run test:cov && npm run e2e`.

## 6. Decisões

- D1: rota `POST /benefits/{id}/end` com corpo de motivo, porque o benefício não é apagado nem editado (o papel do banco não tem DELETE). Alternativa: `DELETE`; descartada porque o registro fica.
- D2: motivo de 2 a 200 caracteres, como o motivo da concessão; guardado em `end_reason` e na auditoria.
- D3: encerrar benefício já encerrado é 409, convenção de `TeacherDeactivation`. Vencido pode ser encerrado porque ainda ocupa o lugar do único ativo.
- D4: mesma permissão `BENEFITS` e alcance de #65 (403 fora da unidade na escrita).
- D5: concorrência por `for update` na linha do benefício, sem chave de idempotência.
- D6 (muda o comportamento): hoje nada identifica um "desconto de professor" (benefício comum, RN-PRO-08 e RN-BEN-08). Marca-se com `teacher_id` opcional na concessão; só os benefícios marcados são encerrados ao inativar o professor. Alternativas descartadas: inferir pelo texto do motivo (frágil) ou criar um tipo de benefício (contraria RN-BEN-08, que o quer comum). Benefícios já existentes não têm marca e ficam como estão.
- D7: o motivo do encerramento não aparece na tabela (fica no banco e na auditoria), para não ampliar `BenefitRow`. Suposição: o canvas pode ter só a tela de benefícios; ajusta-se no PR.
- D8: notas de versão só se o arquivo existir (não existe hoje); a linha vai na descrição do PR: "Encerrar benefício com motivo; desconto de professor encerra ao inativar o professor".
- Suposição: se o `SeedDataIT` precisar de um benefício de professor, acrescenta-se em `infra/seed/15-beneficios.sql` com `on conflict do nothing`.

## Construção

- T1 — arquivos: `backend/src/main/resources/db/migration/V35__benefit_teacher.sql` — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test='BenefitEndIT,BenefitGrantIT,TeacherDeactivationIT'` → 19 testes, 0 falhas (migração aplicada pelo Flyway)
- T2 — arquivos: `benefit/BenefitWrite.java` (`end`, `endLocked`, `EndInput`), `benefit/BenefitController.java` (`POST /{id}/end`), `BenefitEndIT.java` (novo) — prova: mesmo comando → `BenefitEndIT` 6 testes, 0 falhas (CA1–CA7)
- T3 — arquivos: `BenefitWrite.java` (`teacherId` em `Input`, validação 404, `endTeacherBenefits`), `teacher/TeacherDeactivation.java` (chama `endTeacherBenefits` na transação) — prova: mesmo comando → `BenefitEndIT.teacherDiscount`, `BenefitGrantIT` e `TeacherDeactivationIT` sem falhas (CA8, CA9)
- T4 — arquivos: `docs/openapi.yaml`, `frontend/src/mocks/handlers.ts`, `frontend/src/mocks/benefits-end.msw.test.ts` (novo) — prova: `./backend/mvnw -f backend/pom.xml test -Dtest='OpenApiContractTest,ContractRoutesTest'` → sem falhas; `npx vitest run src/mocks` → 166 passados
- T5 — arquivos: `EndBenefitDialog.tsx` e `.test.tsx` (novos), `useGrantBenefit.ts`, `BenefitsPage.tsx` e `.test.tsx`, `GrantBenefitDialog.tsx` e `.test.tsx`, `i18n/pt-BR.json` — prova: `npx vitest run src/features/benefits src/features/students src/mocks` → 288 passados
- T6 — ver regressão abaixo.

Regressão: `./backend/mvnw -f backend/pom.xml verify` → 506 testes, 0 falhas, "All coverage checks have been met"; em `frontend/`: `npm run lint`, `typecheck`, `build` sem erro; `npm run test:cov` → 902 passados (101 arquivos), limites atendidos; `npm run e2e` → 32 passados. Sem piora em relação à linha de base.
Desvios: (1) a ação "Encerrar" na ficha do aluno (`RecordEnrollment`) não foi feita: `GET /students/{id}/enrollment` não expõe o benefício ativo da modalidade, e o plano prevê deixar só a tabela nesse caso; fica para o dono decidir se a ficha passa a expor `activeBenefit`. (2) O motivo da inativação do professor é gravado com o ator da sessão, como na auditoria da inativação. (3) Arquivos `.ts/.tsx/.json/.yaml` alterados mantêm CRLF como já estavam.
Não é do item: nada.

## Verificação

### Rodada 1 — OK — 2026-10-21, estado 446fc0a60015cc6e6f5796a091e48bd8faf941fd

| Critério | Status | Evidência |
|---|---|---|
| CA1 | PASSOU | `BenefitEndIT.ends` no `verify` completo |
| CA2 | PASSOU | `BenefitEndIT.validation` |
| CA3 | PASSOU | `BenefitEndIT.ends`/`expired` |
| CA4 | PASSOU | `BenefitEndIT.permissionsAndIsolation` |
| CA5 | PASSOU | `BenefitEndIT.permissionsAndIsolation` (404 entre organizações); `end` usa `organization_id` da sessão e RLS da tabela |
| CA6 | PASSOU | auditoria `benefit.ended` em `endLocked`, só após o update; `BenefitEndIT.ends` |
| CA7 | PASSOU | `BenefitEndIT.concurrent` (`for update` + `status = 'ACTIVE'` no update) |
| CA8 | PASSOU | `BenefitEndIT.teacherDiscount` |
| CA9 | PASSOU | `BenefitEndIT.teacherDiscount` + `TeacherDeactivationIT` |
| CA10 | PASSOU (ressalva) | `BenefitsPage.test`/`EndBenefitDialog.test`; ação só em `ACTIVE` na tabela. Ação na ficha não feita (desvio previsto no plano técnico, ver sugestão) |
| CA11 | PASSOU | `EndBenefitDialog.test` (enviando, erro/Tentar de novo, 403, 409 vazio, axe 360 px nos dois temas) |
| CA12 | PASSOU | `verify` inclui `OpenApiContractTest`/`ContractRoutesTest`; `benefits-end.msw.test.ts` no `test:cov` |

Comandos: `./backend/mvnw -f backend/pom.xml verify` → exit 0 (JaCoCo ok); em `frontend/`: `npm run lint && npm run typecheck && npm run test:cov` → exit 0 (linhas 97,27%). `build` e `e2e` não reexecutados; mantidos do relato da Construção.
Achados:
- sugestão, especificação/dono — a ficha do aluno (`RecordEnrollment`) segue sem a ação "Encerrar" (R10/CA10 citam a ficha); decidir se `GET /students/{id}/enrollment` passa a expor `activeBenefit` em item próprio.
- sugestão, código — `TeacherDeactivation` encerra os benefícios do professor sem checar alcance do Administrador; é o comportamento pedido (R9), apenas registrar.

### Rodada 2 — OK — 2026-10-21, estado 709bf54b8aa166a921dc3c6649f9a3a9daa884f0

| Critério | Status | Evidência |
|---|---|---|
| CA1 a CA12 | PASSOU (mantido da rodada 1) | desde o estado da rodada 1 só mudou `EnrollmentChangesIT.clean` (teste), `specs/backlog.md` e este arquivo; nenhum código de produção |

Comandos: `./backend/mvnw -f backend/pom.xml verify -Dit.test='StaffUsersInviteIT,StaffUsersDeactivateIT,EnrollmentChangesIT,BenefitEndIT,TeacherDeactivationIT'` → 140 testes rápidos e 26 de integração, 0 falhas, BUILD SUCCESS. Regressão completa e front não reexecutados (nada mudou no front nem em produção); mantidos da rodada 1 e do relato da correção da entrega.
Achados:
- sugestão, código — `EnrollmentChangesIT.clean` apaga por `email like '%.example'`, coerente com as linhas vizinhas; sem ação.

## Entrega

- Encerra benefício ativo com motivo por `POST /benefits/{id}/end` (auditoria `benefit.ended`, isolamento por organização e perfil, concorrência 200 + 409); o desconto de professor (`teacherId`, V35) encerra junto com a inativação do professor, na mesma transação.
- Front: `EndBenefitDialog` em `/beneficios` (só para benefício ativo), quatro estados, 360 px e axe nos dois temas; OpenAPI e MSW atualizados.
- Verificação: `verify` do back-end (506 testes, JaCoCo ok); lint, typecheck, build, `test:cov` (902 testes) e e2e (32) verdes.
- Notas de versão: não existe arquivo; linha: "Encerrar benefício com motivo; desconto de professor encerra ao inativar o professor".
- Limitação: a ação "Encerrar" na ficha do aluno não foi feita (`GET /students/{id}/enrollment` não expõe o benefício ativo); decisão do dono sobre expor `activeBenefit` em item próprio.

### Correção da entrega
- `backend` do PR #307 reprovou: `EnrollmentChangesIT.clean` falhava (`account_invite`/`account_session` ainda apontando para contas `*.example` deixadas por `StaffUsers*IT` na ordem do CI); limpeza do teste incompleta, sem relação com a regra do item — arquivos: `backend/src/test/java/br/com/ctcore/student/EnrollmentChangesIT.java` (apaga `account_invite`, `account_session`, `password_reset` antes de `account`, como `EmailProblemsIT`) — prova: `verify -Dit.test='StaffUsersInviteIT,StaffUsersDeactivateIT,EnrollmentChangesIT,BenefitEndIT'` → 20 testes, 0 falhas (reproduzia 4 erros antes); `./backend/mvnw -f backend/pom.xml verify` → 506 testes, 0 falhas, cobertura ok.
