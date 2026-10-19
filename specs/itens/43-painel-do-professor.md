# 43 — RF-PRO-02 · Painel do professor

Origem: issue #43 (Sprint 04); `docs/especificacao/05-professores.md` (RF-PRO-02, RN-PRO-02, RN-PRO-08, "Dados"). Branch: `item/43-painel-do-professor`. Parte do modelo `teacher` criado em #42 (`V23`, `Teachers`, `TeachersPage`) e do convite de #37 (`StaffUsers`, `InviteService`).

## 1. Objetivo

A gestão abre um professor da lista e vê o painel dele: foto, apresentação, graduação por modalidade, contato (e-mail e telefone), unidades, turmas (titular e auxiliar), próximas aulas e situação do acesso. Do painel, o Coordenador e o Administrador dão acesso ao sistema (envia o convite e cria o vínculo de Professor), reenviam o convite pendente e abrem o WhatsApp do professor.

Fora do escopo: janela de cadastro e edição (#44) e inativar (#45), cujos botões aparecem no painel mas só são ligados nesses itens; turmas e aulas reais (Sprint 05; aqui as listas vêm vazias); benefícios (Sprint 07; ver D5).

## 2. Requisitos

- R1 (RF-PRO-02): `GET /api/v1/teachers/{id}` devolve `id`, `name`, `photoUrl`, `bio`, `email`, `phone`, `directoryConsent`, `status`, `modalities` (id, nome, nível e grau), `units` (id e nome), `classes` (titular e auxiliar), `upcomingClasses` e `access` (`NONE`, `PENDING`, `ACTIVE`, `ENDED`); `classes` e `upcomingClasses` vêm vazias até a Sprint 05 (D1).
- R2 (RN-PRO-04, permissão): só quem tem `TEACHERS` lê o painel (Coordenador e Administrador); Professor, Aluno e Responsável recebem 403 e sem sessão 401. O Administrador só abre professor com ao menos uma unidade dele; fora disso, 404. Professor de outra organização, 404. O contato só existe nesta rota autenticada e nunca na lista (#42) (D2).
- R3 (RN-PRO-02): `POST /api/v1/teachers/{id}/access` com `unitIds` opcional (padrão: todas as unidades do professor que o chamador alcança; precisam ser unidades do professor, ativas e, para o Administrador, dele) envia o convite ao e-mail do cadastro, cria o vínculo `TEACHER` pendente nessas unidades e liga o cadastro à conta (`teacher.account_id`); o cadastro continua separado da conta (D3, D4).
- R4 (RN-PRO-02): recusas do convite: professor sem e-mail 400; professor inativo 409; acesso já pendente ou ativo 409; conta do e-mail já ligada a outro cadastro da organização 409; limite de usuários de gestão da organização, como em #37.
- R5 (RN-PRO-02): `POST /api/v1/teachers/{id}/access/resend` reenvia o convite quando `access = PENDING`; nos demais casos 409.
- R6 (auditoria): dar acesso e reenviar gravam auditoria (ator, professor, unidades), sem código de convite.
- R7 (RN-PRO-07): se o e-mail já pertence a uma conta com vínculo de aluno na organização, o convite reaproveita a conta (mesma conta, dois vínculos).
- R8 (RN-PRO-08): o painel não cria nem mexe em benefício; a regra "encerrar o vínculo encerra o desconto" fica para quando o benefício existir (D5).
- R9 (tela): `/professores/:id` mostra o painel com cabeçalho (avatar ou iniciais, nome, situação), apresentação, contato, graduações, unidades, turmas, próximas aulas e situação do acesso; ações Editar e Inativar (desativadas até #44/#45, D6), Dar acesso (com escolha de unidades) ou Reenviar convite, e WhatsApp (botão existente, só com telefone). A linha da lista leva ao painel. Quatro estados, 360 px, axe nos dois temas, textos em `pt-BR.json`.
- R10 (contrato e dados): `docs/openapi.yaml` e handlers MSW refletem as três rotas; o seed de #42 cobre os casos (professor com e sem e-mail, com e sem conta).

## 3. Critérios de aceite

- CA1 (R1): dado professor com duas modalidades, duas unidades, e-mail e telefone, quando o Coordenador faz `GET /teachers/{id}`, então 200 com todos os campos de R1, graduação por modalidade, `classes` e `upcomingClasses` vazias.
- CA2 (R2): dado Administrador de A, quando abre professor só de unidades de B, então 404; professor com ao menos uma unidade de A abre e mostra todas as unidades; Professor, Aluno e Responsável 403; sem sessão 401; id de outra organização 404 e, com `ctcore_app`, a consulta direta não enxerga a linha.
- CA3 (R2): dado a lista de #42, então a resposta da lista continua sem `email` e `phone`.
- CA4 (R3, R6, R7): dado professor ativo com e-mail e sem conta, quando o Coordenador dá acesso, então existe vínculo `TEACHER` `PENDING` nas unidades escolhidas, `teacher.account_id` preenchido, e-mail de convite enviado e registro de auditoria; `access` passa a `PENDING`. Dado e-mail de conta que já é aluno da organização, então a conta é reaproveitada (sem conta nova) e os dois vínculos coexistem.
- CA5 (R3): dado Administrador, quando escolhe unidade fora do alcance dele (403) ou fora das unidades do professor (400), então nada é criado; sem `unitIds`, usa as unidades do professor que o chamador alcança.
- CA6 (R4): dado professor sem e-mail, então 400; inativo, 409; com acesso `PENDING` ou `ACTIVE`, 409; e-mail de conta já ligada a outro cadastro de professor da organização, 409; organização no limite de usuários de gestão, erro de limite como em #37.
- CA7 (R5, R6): dado `access = PENDING`, quando reenvia, então novo e-mail, convite antigo invalidado e auditoria gravada; com `NONE`, `ACTIVE` ou `ENDED`, 409; Administrador sem alcance, 404.
- CA8 (R8): RN-PRO-08: nenhuma rota deste item cria ou altera benefício; registrado em D5 e conferido na verificação por leitura.
- CA9 (R9): dado o painel em 360 px, então sem rolagem horizontal e axe sem violações nos dois temas; estados carregando, erro com "Tentar de novo", sem permissão e professor não encontrado; sem foto mostra iniciais; turmas e próximas aulas mostram o texto de vazio; Editar e Inativar desativados; Dar acesso aparece com `access` `NONE`/`ENDED`, Reenviar convite com `PENDING`, nenhum dos dois com `ACTIVE`; WhatsApp só com telefone, pelo componente existente; clicar numa linha da lista abre o painel.
- CA10 (R10): `OpenApiContractTest` e `ContractRoutesTest` passam; testes MSW cobrem as três rotas; `SeedDataIT` passa.
- CA11 (R3, R9): ponta a ponta `@critical` "Coordenador dá acesso a um professor": abre a lista, entra no painel de professor com e-mail e sem acesso, dá acesso e vê a situação "Convite enviado".

## 4. Plano técnico

- Dados: sem migração de estrutura (`teacher`, `teacher_unit`, `teacher_modality` e `account_id` existem em `V23`). Seed `infra/seed/09-professores.sql` pode ganhar um professor com e-mail sem acesso e outro sem e-mail (`on conflict do nothing`).
- API (cookie e CSRF como as demais): `GET /api/v1/teachers/{id}` (200 `TeacherDetail`; 401, 403, 404); `POST /api/v1/teachers/{id}/access` com corpo `{unitIds?}` (200 `TeacherDetail`; 400, 401, 403, 404, 409); `POST /api/v1/teachers/{id}/access/resend` (204; 401, 403, 404, 409). Entram em `docs/openapi.yaml` no mesmo PR.
- Back-end: em `br.com.ctcore.teacher`, acrescentar a `Teachers.java` o método `get` (reusa unidades, modalidades e `photoUrl`; mesma regra de alcance da lista) e criar `TeacherAccess.java` (dar acesso e reenviar), chamando `InviteService.invite/resend` como `StaffUsers.invite/resend` (mesma capacidade de usuários de gestão, `requireActiveUnits` e `InviteRules.allowed`), dentro de `OrganizationTransactions.execute`; auditoria por `AuditService` (`teacher.access_granted`, `teacher.invite_resent`). Alterar `TeacherController.java` (três rotas) e `docs/openapi.yaml` (esquema `TeacherDetail`). Turmas e aulas: listas vazias fixas em um só ponto, trocadas na Sprint 05.
- Interface: criar `frontend/src/features/teachers/TeacherPanelPage.tsx`, `GrantAccessDialog.tsx` (escolha de unidades, reaproveita `features/users/UnitChecks.tsx`) e teste; alterar `useTeachers.ts` (detalhe e ações), `TeachersPage.tsx` (linha leva ao painel), `App.tsx` (`/professores/:id`), `mocks/handlers.ts` (+ teste MSW) e `i18n/pt-BR.json`. Reusa `QueryPage`/estados padrão, `WhatsAppButton` e os padrões de `UsersPage`.
- Segurança: organização, perfil e alcance vêm do principal; `TEACHERS` conferido no servidor em todas as rotas; telefone e e-mail só nesta rota autenticada (RN-PRO-04); parâmetros ligados; sem código de convite em log nem auditoria.
- Reuso: `Teachers` (alcance, fotos), `InviteService`, `StaffUsers` como modelo, `AuditService`, `WhatsAppButton`, `UnitChecks`.
- Riscos: (1) dois convites simultâneos ao mesmo professor: o índice único `teacher_org_account` e a trava protegem; um recebe 409. (2) conta já ligada como Professor por convite de #37 sem cadastro de professor: resulta em 409 `ALREADY_LINKED`; ligar a cadastro existente fica fora. (3) `access` no painel e na lista usa a mesma junção de #42.

## 5. Tarefas

Uma parte só: servidor (T1, T2), contrato (T3), tela (T4, T5).

- [x] T1: `Teachers.get` e rota `GET /teachers/{id}` com alcance, 404 e testes de integração (campos, contato só no painel, perfis, Administrador por unidade, isolamento com RLS) — cobre CA1, CA2, CA3 — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test=TeacherIT -Dtest=NoSuchTest -Dsurefire.failIfNoSpecifiedTests=false -Djacoco.skip=true`.
- [x] T2: `TeacherAccess` (dar acesso e reenviar), rotas, auditoria e testes de integração (convite, conta de aluno reaproveitada, unidades, recusas, limite, reenvio, perfis, isolamento) — cobre CA4 a CA7 — prova: `verify -Dit.test=TeacherAccessIT` (a criar).
- [x] T3: OpenAPI (três operações, `TeacherDetail`), handlers MSW com teste e ajuste do seed — cobre CA10 — prova: `./backend/mvnw -f backend/pom.xml test -Dtest='OpenApiContractTest,ContractRoutesTest'`, `verify -Dit.test=SeedDataIT` e `npx vitest run src/mocks` em `frontend/`.
- [x] T4: `TeacherPanelPage`, `GrantAccessDialog`, `useTeachers`, rota, link na lista, textos pt-BR, quatro estados, 360 px e axe — cobre CA9 (e CA8 por leitura) — prova: `npx vitest run src/features/teachers`.
- [x] T5: e2e `@critical` "Coordenador dá acesso a um professor" (`frontend/e2e/professores.spec.ts`) e regressão completa (a regressão não cobre critério: confere cobertura e os 100% nos pacotes exigidos) — cobre CA11 — prova: `./backend/mvnw -f backend/pom.xml verify` e, em `frontend/`, `npm run lint && npm run typecheck && npm run build && npm run test:cov && npm run e2e`.

## 6. Decisões

- D1: turmas (titular e auxiliar) e próximas aulas não existem até a Sprint 05; o painel já tem as seções e a API já devolve as listas, vazias. Alternativa: ocultar as seções; descartada porque o card as pede.
- D2 (muda o comportamento): o painel mostra e-mail e telefone à gestão autenticada (Coordenador e Administrador do alcance); RN-PRO-04 proíbe só a saída pública e a lista. Alternativa: contato só na edição; descartada porque o card pede contato no painel.
- D3 (muda o comportamento): "Dar acesso" cria o vínculo nas unidades escolhidas na janela, padrão todas as unidades do professor que o chamador alcança (Coordenador: todas); o Administrador só marca unidades dele. Alternativa: sempre todas as unidades do professor; descartada porque RN-PRO-02 fala em "unidades marcadas".
- D4: a cadeia de convite é a de #37 (mesmo serviço, mesma capacidade de usuários de gestão); quem aceita cria a senha como qualquer usuário. Um vínculo `TEACHER` já criado por #37 sem cadastro de professor não é ligado automaticamente. Alternativa: ligar por e-mail igual; descartada por risco de ligar a pessoa errada.
- D5: RN-PRO-08 não tem como ser implementada nem testada aqui, porque benefício só nasce na Sprint 07. Este item não toca benefício; quando existir, o encerramento do vínculo de professor passa a encerrar o benefício, e o teste dessa regra entra no item de benefícios. No "Pronto quando" (cada código com teste), CA8 é conferido por leitura. Alternativa: criar já uma tabela de benefício; descartada por fugir do escopo.
- D6: Editar e Inativar aparecem desativados no painel até #44 e #45. Alternativa: omitir os botões; descartada para o painel já ter o desenho do card.
- D7: o canvas `ProfessorPainel.dc.html` não está acessível; a tela segue os padrões do projeto e o dono confere o layout no PR (como D8 de #42).
- Suposição: a gestão não precisa de ação "cancelar convite" aqui (o card não a pede); se precisar, é item novo.

## Construção

- T1 — arquivos: `backend/.../teacher/Teachers.java` (`get`, `find`, `detail`, `refs` extraído da lista), `TeacherController.java` (`GET /teachers/{id}`), `TeacherIT.java` (+3 testes: campos e contato, perfis e alcance, RLS) — prova: `verify -Dit.test=TeacherIT` → 10 testes, 0 falhas.
- T2 — arquivos: `teacher/TeacherAccess.java` (dar acesso e reenviar), `TeacherController.java` (duas rotas), `identity/InviteService.java` (`inviteTeacher`, `resendTeacher`), `identity/InviteRules.java` (`teacherAccess`), `InviteRulesTest.java`, `TeacherAccessIT.java` (7 testes) — prova: `verify -Dit.test=TeacherAccessIT` → 7 testes, 0 falhas.
- T3 — arquivos: `docs/openapi.yaml` (três operações, `TeacherDetail`), `frontend/src/mocks/handlers.ts`, `teachers.msw.test.ts` (+3 testes); seed `09-professores.sql` não mudou (já tem professor com e-mail sem conta, com conta e inativo; `SeedDataIT` passa no `verify`) — prova: `OpenApiContractTest,ContractRoutesTest` → 7 testes ok; `npx vitest run src/mocks` → ok.
- T4 — arquivos: `TeacherPanelPage.tsx`, `GrantAccessDialog.tsx`, `TeacherPanelPage.test.tsx` (14 testes, inclui axe 360 px nos dois temas), `useTeachers.ts`, `TeachersPage.tsx` (nome vira link), `TeachersPage.test.tsx` (+1), `App.tsx`, `i18n/pt-BR.json` — prova: `npx vitest run src/features/teachers` → ok. CA8 por leitura: nenhuma rota nova toca benefício.
- T5 — arquivos: `frontend/e2e/professores.spec.ts` (+1 `@critical`) — prova: `npm run e2e` → 21 passaram.
Regressão: `./backend/mvnw -f backend/pom.xml verify` → 280 testes, 0 falhas, BUILD SUCCESS (JaCoCo ok); em `frontend/`: `npm run lint`, `typecheck`, `build` ok; `npm run test:cov` → 65 arquivos, 512 testes, cobertura 94,63% de linhas; `npm run e2e` → 21 ok.
Desvios: (1) a cascata de convites (RN-ACE-05, `InviteRules.allowed`) só deixa o Coordenador convidar Administrador, e `InviteIT` a prova; o plano mandava reusar `InviteService.invite/resend`. Para o Coordenador dar acesso a professor (RN-PRO-02), criei `InviteRules.teacherAccess` e os caminhos `InviteService.inviteTeacher/resendTeacher`, sem mudar a cascata nem os testes existentes. (2) O seed não mudou (já cobre os casos). (3) O link do painel é o nome na linha da lista, não a linha inteira (acessibilidade).
Não é do item: nenhum.

## Verificação

### Rodada 1 — OK — 2026-10-19, estado 42b97e9bfd0bb0ae7d4283ea9010530127ac05fe

| Critério | Status | Evidência |
|---|---|---|
| CA1 | PASSOU | `TeacherIT` (10 testes ok no `verify`) |
| CA2 | PASSOU | `TeacherIT` perfis, alcance e RLS |
| CA3 | PASSOU | `TeacherIT` (lista sem contato) |
| CA4 | PASSOU | `TeacherAccessIT.grants` e `reusesStudentAccount` |
| CA5 | PASSOU | `TeacherAccessIT.units`, `permissionsAndIsolation` |
| CA6 | PASSOU | `TeacherAccessIT.refusals`, `limit` |
| CA7 | PASSOU | `TeacherAccessIT.resends` |
| CA8 | PASSOU | leitura do diff: nenhuma rota toca benefício |
| CA9 | PASSOU | `TeacherPanelPage.test.tsx` (inclui axe 360 px nos dois temas); `test:cov` ok |
| CA10 | PASSOU | `verify` completo (contrato, `SeedDataIT`); testes MSW no `test:cov` |
| CA11 | PASSOU | e2e `@critical` "Coordenador dá acesso a um professor" (21 passaram) |

Comandos: `./backend/mvnw -f backend/pom.xml verify` → 280 testes, 0 falhas, BUILD SUCCESS; em `frontend/`, `npm run lint && typecheck && build && test:cov && e2e` → 65 arquivos, 512 testes, 94,63% de linhas, e2e 21 ok.
Achados:
- sugestão, código, `TeacherAccess.grant` — o e-mail de convite sai dentro da transação (`InviteService.invite` chama `mailer`) antes do `update teacher set account_id`; falha posterior reverteria o banco mas o e-mail já teria saído. Risco baixo.
- sugestão, código, `InviteService.inviteTeacher`/`InviteRules.teacherAccess` — desvio 1 registrado em "Construção" (Coordenador convida Professor fora da cascata RN-ACE-05); coerente com RN-PRO-02, `InviteIT` segue passando.

## Entrega

- Painel do professor (`GET /teachers/{id}`) com contato visível à gestão, seções de turmas e próximas aulas vazias até a Sprint 05, e ação "Dar acesso" / reenviar convite (RN-PRO-02) com unidades escolhidas.
- Verificação: `verify` do back-end (280 testes), `lint`, `typecheck`, `build`, `test:cov` (94,63%) e `e2e` (21) verdes; rodada 1 OK.
- Decisões: D2 e D3 mudam comportamento (ver seção 6); o Coordenador convida professor por caminho próprio, fora da cascata RN-ACE-05. RN-PRO-08 conferida por leitura (benefício só na Sprint 07). Editar e Inativar desativados até #44 e #45.
- Limitação: o canvas do painel não estava acessível; o dono confere o layout no PR. O e-mail do convite sai antes do vínculo ser gravado (risco baixo).
