# 45 — RF-PRO-04 · Inativar professor

Origem: issue #45 (Sprint 04); `docs/especificacao/05-professores.md` (RF-PRO-04, RN-PRO-05, RN-PRO-08), P1 do backlog e a linha `TEACHERS` da tabela de permissões. Branch: `item/45-inativar-professor`. Parte de `Teachers`/`TeacherAccess`/`TeacherWrite` (#42 a #44), do painel `TeacherPanelPage` (botão "Inativar" hoje desativado, chave `teachers.panel.deactivate`) e do fim de vínculo de `LinkService`/`InviteService` (#37).

## 1. Objetivo

A gestão inativa um professor depois de ver quais turmas dele ficarão sem titular. Inativar muda a situação para `INACTIVE`, desativa o vínculo de acesso e encerra as sessões (RN-PRO-05); o histórico e o cadastro continuam.

Fora do escopo: reativar professor; turmas, "Precisa de atenção" e escolhas de turma e substituição (Sprint 05 e seguintes: não existem hoje); desconto de professor (RN-PRO-08, Sprint 08 em diante); tirar só as unidades do Administrador (já existe em `PUT /teachers/{id}`, #44); notas de versão (#142).

## 2. Requisitos

- R1 (RF-PRO-04): `GET /api/v1/teachers/{id}/deactivation` devolve, sem alterar nada, `headClasses` (turmas em que o professor é titular, como `ClassRef`) e `canDeactivate`. Hoje `headClasses` é sempre vazio, porque turmas só nascem na Sprint 05; o cálculo fica num ponto único (`Teachers.headClasses`) para a Sprint 05 trocar (como `CLASS_COUNT`, D1).
- R2 (RN-PRO-05): `POST /api/v1/teachers/{id}/deactivate` muda `status` para `INACTIVE` e responde 200 com `TeacherDetail`. Professor já inativo dá 409.
- R3 (RN-PRO-05, D2): se o professor tem conta ligada, o vínculo de Professor na organização é encerrado (`ACTIVE` por `LinkService.endInScope`; `PENDING` por `InviteService.cancel`, o convite é cancelado) e, terminado o commit, as sessões que dependiam dele acabam (`LinkService.sessionsAfter`). A conta, os vínculos em outras organizações e o `account_id` do cadastro ficam.
- R4 (P1, D3, muda o comportamento): o Coordenador inativa qualquer professor da organização. O Administrador só inativa se todas as unidades do professor (ativas ou não) estiverem entre as dele; senão `canDeactivate = false` e o `POST` dá 403 (para sair do alcance dele, usa Editar, #44).
- R5 (permissão): as duas rotas exigem `TEACHERS` no servidor: Coordenador e Administrador; Professor, Aluno e Responsável 403; sem sessão 401. Administrador sem alcance sobre o professor recebe 404, como em #43.
- R6 (isolamento): organização só da sessão; professor de outra organização dá 404; nada de outra organização é alterado (RLS).
- R7 (auditoria): inativar grava `teacher.deactivated` (ator, professor, situação antes e depois, quantidade de turmas sem titular e se havia vínculo encerrado), sem e-mail nem telefone; o fim do vínculo grava a auditoria própria de `LinkService`/`InviteService`.
- R8 (RN-PRO-05): professor inativo continua na lista com situação "Inativo" e filtrável (#42), pode ser editado (#44), não recebe novo convite (`TeacherAccess.grant` já dá 409) e some de qualquer escolha futura (nenhuma tela de escolha existe ainda).
- R9 (tela): no painel, "Inativar" fica ativo só para quem tem `TEACHERS` e com o professor ativo; abre janela de confirmação que lista as turmas que ficarão sem titular (ou diz que nenhuma) e avisa que o acesso será encerrado quando houver conta; "Inativar" confirma e atualiza o painel; Administrador fora do alcance vê o aviso "só o Coordenador inativa; tire suas unidades em Editar" e não confirma. Estados carregando e erro com "Tentar de novo", 360 px, axe nos dois temas, textos em `pt-BR.json`.
- R10 (contrato e dados): `docs/openapi.yaml` e os handlers MSW refletem as rotas e o esquema; o seed continua válido. Sem migração.

## 3. Critérios de aceite

- CA1 (R1): dado Coordenador e professor ativo, quando chama `GET /teachers/{id}/deactivation`, então 200 com `headClasses` (lista) e `canDeactivate = true`; nada muda no banco.
- CA2 (R2): dado professor ativo, quando o Coordenador faz `POST /deactivate`, então 200 com `status INACTIVE`, e `GET /teachers?status=INACTIVE` o traz; repetir o `POST` dá 409.
- CA3 (R3): dado professor com acesso `ACTIVE`, quando inativa, então o vínculo vira `ENDED`, a sessão que só tinha esse vínculo acaba, a conta e `teacher.account_id` ficam; com acesso `PENDING`, o vínculo vira `ENDED` e o convite não vale mais; sem conta, só a situação muda; `access` no detalhe passa a refletir vínculo encerrado.
- CA4 (R4): dado professor com unidades de A e B, quando o Administrador de A pede `GET /deactivation`, então `canDeactivate = false` e o `POST` dá 403 sem alterar nada; com todas as unidades dele, 200; o Coordenador inativa o mesmo professor com 200.
- CA5 (R5): dado Professor, Aluno e Responsável, então 403 nas duas rotas; sem sessão 401; Administrador sem alcance 404.
- CA6 (R6): dado duas organizações, então professor de outra organização dá 404 nas duas rotas e nenhuma linha dela muda (RLS conferido com `ctcore_app`).
- CA7 (R7): dado inativar, então há um registro `teacher.deactivated` com antes e depois, sem e-mail nem telefone, e o registro de fim de vínculo quando havia vínculo.
- CA8 (R8): dado professor inativo, quando se edita (`PUT`) funciona; `POST /access` dá 409.
- CA9 (R9): dado o painel de professor ativo, então "Inativar" abre a janela com a lista de turmas (ou "Nenhuma turma ficará sem titular"), confirmar atualiza o painel para "Inativo" e fecha a janela; professor inativo ou usuário sem `TEACHERS` não tem o botão ativo; Administrador fora do alcance vê o aviso e sem confirmar; erro da pré-visualização mostra "Tentar de novo"; 409 e 403 do servidor aparecem na janela; 360 px sem rolagem horizontal e axe sem violações nos dois temas.
- CA10 (R10): `OpenApiContractTest` e `ContractRoutesTest` passam; testes MSW cobrem as duas rotas; `SeedDataIT` passa.
- CA11 (R2, R9): ponta a ponta `@critical` "Coordenador inativa um professor": abre o painel, clica em Inativar, confirma e vê a situação "Inativo".

## 4. Plano técnico

- **Dados.** Não muda a estrutura (`teacher.status` já aceita `INACTIVE`, `V23__teacher.sql`). Sem migração nem seed novo.
- **API** (cookie e CSRF como as demais; rotas no OpenAPI no mesmo PR): `GET /teachers/{id}/deactivation` (200 `TeacherDeactivationPreview {headClasses[ClassRef], canDeactivate}`; 401, 403, 404) e `POST /teachers/{id}/deactivate` (200 `TeacherDetail`; 401, 403, 404, 409). Sem corpo.
- **Back-end.** Em `br.com.ctcore.teacher`: a criar `TeacherDeactivation.java` (`preview` e `deactivate`; `OrganizationTransactions.execute`; trava da organização `select ... for update` como em `TeacherAccess.grant`; `Teachers.requireAccess` e `Teachers.find` para permissão e alcance; alcance total do Administrador por `teacher_unit` contra `who.units()`; `LinkService.endInScope`/`sessionsAfter` e `InviteService.cancel` com `Inviter` como em `TeacherAccess`; `AuditService` com `AuditEntry`). A alterar: `Teachers.java` (`headClasses`, ponto único vazio, e `Detail.classes` pode usá-lo), `TeacherController.java` (duas rotas). Fim de sessões depois do commit, como em `StaffUsers.deactivate`.
- **Interface.** A criar `frontend/src/features/teachers/DeactivateTeacherDialog.tsx` (e teste). A alterar `TeacherPanelPage.tsx` (botão ativo e abertura), `useTeachers.ts` (pré-visualização e inativar), `mocks/handlers.ts` (+ teste MSW), `i18n/pt-BR.json`, `TeacherPanelPage.test.tsx` (hoje espera "Inativar" desativado). Reusa `Modal` e o padrão de `GrantAccessDialog.tsx`.
- **Segurança.** Organização, perfil e alcance vêm do principal; `TEACHERS` conferido em toda rota; parâmetros ligados; sem dado de contato na auditoria; 403 do Administrador sem alcance total não vaza dados (a pré-visualização só devolve `canDeactivate`).
- **Riscos.** (1) Inativar com vínculo `ACTIVE` mexe em sessões de outra organização: `sessionsAfter` roda depois do commit. (2) `InviteService.cancel` e `LinkService` conferem o alcance de quem chama: confirmar na construção que passam para Coordenador e para Administrador com todas as unidades. (3) Corrida entre duas inativações: a trava da organização e o 409 cobrem.

## 5. Tarefas

- [x] T1: `TeacherDeactivation` (`preview`, `deactivate`), `Teachers.headClasses`, rotas no `TeacherController` e `TeacherDeactivationIT` (a criar): situação, vínculo `ACTIVE` e `PENDING`, sem conta, 409, regra do Administrador, perfis, isolamento, auditoria, edição e convite em professor inativo — cobre CA1 a CA8 — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test='TeacherDeactivationIT,TeacherIT,TeacherAccessIT' -Dtest=NoSuchTest -Dsurefire.failIfNoSpecifiedTests=false -Djacoco.skip=true`.
- [x] T2: OpenAPI (rotas e `TeacherDeactivationPreview`) e handlers MSW com teste — cobre CA10 — prova: `./backend/mvnw -f backend/pom.xml test -Dtest='OpenApiContractTest,ContractRoutesTest' -Djacoco.skip=true`, `verify -Dit.test=SeedDataIT` e `npx vitest run src/mocks` em `frontend/`.
- [x] T3: `DeactivateTeacherDialog`, `useTeachers`, botão ativo no painel, textos pt-BR, estados, 360 px e axe — cobre CA9 — prova: `npx vitest run src/features/teachers` em `frontend/`.
- [x] T4: e2e `@critical` "Coordenador inativa um professor" em `frontend/e2e/professores.spec.ts` — cobre CA11 — prova: `npm run e2e` em `frontend/`.
- [x] T5: regressão completa (cobertura e 100% dos pacotes exigidos) — não cobre critério: confirma que nada quebrou — prova: `./backend/mvnw -f backend/pom.xml verify` e, em `frontend/`, `npm run lint && npm run typecheck && npm run build && npm run test:cov && npm run e2e`.

## 6. Decisões

- D1: não há turmas até a Sprint 05, então a lista "ficarão sem titular" é sempre vazia hoje; a rota, o esquema e a janela já nascem prontos e a Sprint 05 troca só `Teachers.headClasses`. A entrada em "Precisa de atenção" também é da Sprint 05. Alternativa: adiar o item; descartada porque o card e a regra de acesso (vínculo e sessões) valem agora.
- D2: o `account_id` do cadastro e a conta ficam depois de inativar, para o histórico e para uma reativação futura; só o vínculo de Professor é encerrado. Alternativa: soltar a conta; descartada por perder o histórico e impedir reativar sem novo convite.
- D3 (muda o comportamento): decisão da P1, opção recomendada pelo backlog (o dono ainda não respondeu): o Administrador só inativa se todas as unidades do professor forem dele; o Coordenador inativa em qualquer caso (mesma regra do D5 de #37). Fora do alcance, a tela explica e manda usar Editar para tirar só as unidades dele. Se o dono decidir outra coisa, muda a validação de R4.
- D4 (muda o comportamento): inativar não pede motivo nem desfaz sozinho; confirmação simples na janela. Alternativa: campo de motivo; descartada por não estar na especificação.
- D5: o canvas `ProfessorInativar.dc.html` não está acessível; a janela segue os padrões do projeto e o dono confere o layout no PR (como D10 de #44).
- Suposição: "turmas em que era titular" usa `ClassRef.role = HEAD` (de #43). Se a Sprint 05 modelar de outro jeito, muda só `Teachers.headClasses`.

## Construção

- T1 — arquivos: `backend/.../teacher/TeacherDeactivation.java` (novo), `Teachers.java` (`headClasses`), `TeacherController.java` (duas rotas), `identity/InviteService.java` (`cancelTeacher`), `TeacherDeactivationIT.java` (novo) — prova: `verify -Dit.test='TeacherDeactivationIT,TeacherIT,TeacherAccessIT' ...` → 23 testes, 0 falhas
- T2 — arquivos: `docs/openapi.yaml` (2 rotas e `TeacherDeactivationPreview`), `frontend/src/mocks/handlers.ts`, `teachers.msw.test.ts` — prova: `OpenApiContractTest,ContractRoutesTest` → verde; `verify -Dit.test=SeedDataIT` → 11 testes, 0 falhas; `npx vitest run src/mocks` → 70 testes passam
- T3 — arquivos: `DeactivateTeacherDialog.tsx` (novo), `useTeachers.ts`, `TeacherPanelPage.tsx`, `TeacherPanelPage.test.tsx`, `i18n/pt-BR.json` — prova: `npx vitest run src/features/teachers` → 21 testes no painel passam (inclui axe claro e escuro a 360 px)
- T4 — arquivos: `frontend/e2e/professores.spec.ts` — prova: `npm run e2e` → 23 passam, incluindo "Coordenador inativa um professor"
- T5 — prova: `./backend/mvnw -f backend/pom.xml verify` → BUILD SUCCESS, 296 testes, JaCoCo sem violação; em `frontend/`, `npm run lint && typecheck && build && test:cov && e2e` → exit 0, 540 testes de unidade e 23 e2e

Regressão: sem falha nova; nada que passava passou a falhar.
Desvios: (1) risco 2 do plano confirmado: `InviteService.cancel` recusava Coordenador (só aceitava convite de Administrador), então foi criado `InviteService.cancelTeacher` (mesma regra de `grantTeacher`/`resendTeacher`, `InviteRules.teacherAccess`); `cancel` continua igual. (2) `Detail.classes` passou a usar `Teachers.headClasses` (opcional no plano). (3) O botão de confirmar usa o estilo padrão: o `Button` do projeto não tem variante destrutiva.
Não é do item: nenhum.

## Verificação

### Rodada 1 — OK — 2026-10-19, estado 333ece6e920248d7e916806e679b4e84fd5596fc

| Critério | Status | Evidência |
|---|---|---|
| CA1 | PASSOU | `TeacherDeactivationIT.deactivates`: GET 200, `headClasses` vazio, `canDeactivate` true, status segue ACTIVE |
| CA2 | PASSOU | mesmo teste: POST 200 INACTIVE, filtro `status=INACTIVE` traz, repetir dá 409 |
| CA3 | PASSOU | `endsActiveLink` (vínculo ENDED, sessão removida, conta e `account_id` ficam) e `endsPendingLink` (convite cancelado, sem conta só muda situação) |
| CA4 | PASSOU | `administratorReach`: canDeactivate false e 403 sem alterar; com todas as unidades 200; Coordenador 200 |
| CA5 | PASSOU | `permissionsAndIsolation`: Professor/Aluno/Responsável 403, anônimo 401, Administrador sem alcance 404 |
| CA6 | PASSOU | mesmo teste: outra organização 404, RLS com `ctcore_app` atualiza 0 linhas |
| CA7 | PASSOU | `endsActiveLink`: um `teacher.deactivated` sem e-mail/telefone e um `link.ended`; `endsPendingLink`: `invite.canceled` |
| CA8 | PASSOU | `inactiveRules`: PUT 200, grant 409 |
| CA9 | PASSOU | vitest `src/features/teachers` (inclui axe e 360 px, conforme testes do painel); código do diálogo cobre lista, aviso de alcance, erros 409/403 e "Tentar de novo" |
| CA10 | PASSOU | `OpenApiContractTest,ContractRoutesTest` e `SeedDataIT` verdes; vitest `src/mocks` verde |
| CA11 | PASSOU | `npm run e2e`: 23 passam, incluindo "Coordenador inativa um professor" |

Comandos:
- `mvnw verify -Dit.test='TeacherDeactivationIT,TeacherIT,TeacherAccessIT,SeedDataIT' -Dtest='OpenApiContractTest,ContractRoutesTest' -Djacoco.skip=true` → BUILD SUCCESS, 34 ITs, 0 falhas
- `npx vitest run src/features/teachers src/mocks` → 116 testes passam
- `npm run lint`, `npm run typecheck`, `npm run build` → sem erro
- `npm run test:cov` → 540 testes passam
- `npm run e2e` → 23 passam
- Não reexecutado: `mvnw verify` completo com JaCoCo (a construção registra 296 testes, sem violação).

Achados:
- sugestão, código, `TeacherDeactivation.deactivate` — `accessEnded` na auditoria é calculado de `t.access()`; funciona, mas poderia derivar de `ended != null || PENDING` para não duplicar a condição.
- sugestão, código, `TeacherDeactivation.inReach` — a consulta de unidades roda também na pré-visualização de professor inativo; custo irrelevante.

## Entrega

- Muda: Administrador e Coordenador inativam um professor pelo painel (RF-PRO-04), com pré-visualização das turmas em que era titular e aviso de alcance. Duas rotas novas (`GET .../deactivation-preview`, `POST .../deactivate`), encerra vínculo/convite, grava auditoria sem dado pessoal, 409 ao repetir, 403/404 por perfil e organização.
- Verificação: rodada 1 OK. ITs do professor e do seed, contrato OpenAPI, 540 testes de unidade, lint, tipos, build e e2e (23) verdes; `verify` completo com JaCoCo registrado na construção (296 testes).
- Decisões: Administrador só inativa se todas as unidades do professor forem suas (P1); `InviteService.cancelTeacher` criado porque `cancel` recusava Coordenador; layout do canvas não acessível, dono confere no PR.
- Limitações: turmas "titular" usam `ClassRef.role = HEAD`; botão de confirmar sem variante destrutiva.
