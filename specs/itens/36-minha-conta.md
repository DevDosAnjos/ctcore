# 36 — RF-ACE-04 · Minha conta: senha, e-mail, duas etapas e sessões

Origem: issue #36 (Sprint 03); `docs/especificacao/02-contas-e-acesso.md` (RF-ACE-04, RN-ACE-15, RN-ACE-18, RN-ACE-20). Branch: `item/36-minha-conta`.

## 1. Objetivo

Uma tela "Minha conta" para qualquer pessoa logada (gestão e portal, mesma tela): trocar a senha, trocar o e-mail com confirmação, ligar e desligar as duas etapas, gerar códigos de reserva novos e ver e encerrar as sessões ativas. No servidor, trocar senha, ligar/desligar duas etapas e códigos de reserva já existem (#18, #26); faltam a troca de e-mail e a lista/encerramento de sessões. A tela junta tudo e traz o fluxo de ponta a ponta "Troca de senha encerrando as outras sessões".

Fora do escopo: a área do portal em si (Sprint 14: o portal usa esta mesma rota `/conta` quando existir); editar nome e telefone; notificação por e-mail de sessão encerrada; mostrar IP bruto das sessões; notas de versão (só existem na Sprint 15, #142).

## 2. Requisitos

- R1 (RF-ACE-04, RN-ACE-20): a tela troca a senha estando logado (senha atual, nova com as regras da RN-ACE-08, repetição) usando `POST /auth/password` existente; as outras sessões são encerradas e a atual permanece.
- R2 (RF-ACE-04): senha atual errada na troca de senha conta como falha do login (RN-ACE-10): 5 seguidas travam o e-mail e vale o limite por IP (D3).
- R3 (RN-ACE-15): `POST /auth/email-change` com `newEmail`, `password` (e `code` se as duas etapas estiverem ligadas, D2) registra um pedido pendente e envia o link de confirmação ao endereço novo, válido por 24 horas. O e-mail da conta não muda neste passo.
- R4 (RN-ACE-15): novo pedido cancela o anterior pendente da conta; no máximo 3 pedidos por hora por conta (D5).
- R5 (RN-ACE-15, RN-ACE-11): a resposta ao pedido é a mesma (202) quando o endereço novo já pertence a outra conta, e nesse caso nada é enviado; endereço igual ao atual ou mal formado responde 400.
- R6 (RN-ACE-15): `POST /auth/email-change/{code}/confirm`, chamado por quem está logado na conta dona do pedido, troca o e-mail; link vencido, usado, cancelado, de outra conta ou cujo endereço passou a ser de outra conta responde 410 `EMAIL_CHANGE_INVALID` e nada muda. O código é de uso único.
- R7 (RN-ACE-15): ao confirmar, o endereço antigo recebe um aviso de que o e-mail da conta foi trocado (D4).
- R8 (RF-ACE-04, RN-ACE-18): a tela liga as duas etapas (QR/segredo, código, 10 códigos de reserva exibidos uma vez), desliga (senha + código; negado a quem é obrigado, RN-ACE-17) e gera códigos de reserva novos (senha + código) usando as rotas existentes; sem rota nova.
- R9 (RF-ACE-04): `GET /auth/sessions` lista as sessões da própria conta: id, aparelho (rótulo, não o texto bruto), cidade aproximada quando conhecida, início, último uso e se é a atual.
- R10 (RF-ACE-04): `DELETE /auth/sessions/{id}` encerra uma sessão da própria conta que não seja a atual; sessão de outra conta ou inexistente responde 404; a atual responde 409 `SESSION_CURRENT` (para a atual há "Sair").
- R11 (RF-ACE-04): `DELETE /auth/sessions` encerra todas as outras sessões da conta e mantém a atual.
- R12 (auditoria): pedido de troca de e-mail, troca confirmada, sessão encerrada e "encerrar outras" gravam auditoria sem senha, token, código nem endereço novo completo (D6).
- R13 (isolamento): toda rota nova age só sobre a conta da sessão; não há parâmetro de conta; sem sessão responde 401.
- R14: a tela é a rota `/conta`, alcançável por qualquer perfil logado (gestão e portal), com os quatro estados (carregando, vazio da lista de sessões, erro com "Tentar de novo", sem permissão/sessão expirada), a partir de 360 px, axe nos dois temas, textos em `pt-BR.json`; a tela de confirmação de e-mail é `/confirmar-email/:code`.
- R15: `docs/openapi.yaml` e os handlers MSW refletem as rotas novas e batem com a implementação.

## 3. Critérios de aceite

- CA1 (R1): dado login em dois aparelhos, quando a pessoa troca a senha na tela com a senha atual correta e uma nova válida, então aparece a confirmação, a outra sessão deixa de valer (401) e a atual segue; e-mail de aviso é enviado (já existe).
- CA2 (R1, R2): dado senha atual errada, senha nova fraca ou conhecida, ou repetição diferente, então a tela mostra o erro no campo e nada muda; após 5 senhas atuais erradas seguidas a rota responde 429 `TOO_MANY_REQUESTS`.
- CA3 (R3): dado senha correta e `newEmail` livre, quando `POST /auth/email-change`, então 202, um pedido pendente com validade de 24 horas, um e-mail com link ao endereço novo, e `account.email` inalterado.
- CA4 (R3): dado senha errada, ou duas etapas ligadas e código ausente/errado, então 400/401 conforme o contrato existente de `/reauth`, nada é criado nem enviado.
- CA5 (R4): dado pedido pendente, quando a pessoa faz outro, então o primeiro link passa a responder 410; no quarto pedido na mesma hora, 429.
- CA6 (R5): dado `newEmail` de outra conta, então 202 igual ao do CA3, nenhum e-mail enviado e nada gravado; `newEmail` igual ao atual (sem diferenciar maiúsculas) ou mal formado, 400.
- CA7 (R6, R7): dado o link válido aberto por quem está logado na conta dona, quando confirma, então `account.email` vira o novo, o pedido fica usado, o login passa a valer com o novo e não com o antigo, e o endereço antigo recebe o aviso; usar o mesmo link de novo responde 410.
- CA8 (R6): dado link vencido (após 24 horas), cancelado, de outra conta, ou endereço que outra conta passou a usar antes da confirmação, então 410 `EMAIL_CHANGE_INVALID`, e-mail da conta inalterado; sem sessão, 401 e a tela leva ao login e volta ao link (RN-ACE-16).
- CA9 (R8): dado a tela, quando liga as duas etapas, desliga (conta não obrigada), gera códigos novos, então os fluxos usam as rotas existentes, os códigos aparecem uma vez e os antigos deixam de valer; a conta obrigada (Coordenador) não vê "Desligar".
- CA10 (R9): dado 3 sessões da conta, então `GET /auth/sessions` devolve 3, uma com `current = true`, sem token, hash nem IP; sessão expirada não aparece; sessão de outra conta nunca aparece.
- CA11 (R10): dado uma sessão de outro aparelho, quando `DELETE /auth/sessions/{id}`, então 204 e aquela sessão responde 401; id de sessão de outra conta ou inexistente, 404 e a outra conta não é afetada; id da atual, 409 e a sessão segue.
- CA12 (R11): dado 3 sessões, quando `DELETE /auth/sessions`, então só a atual sobrevive; as de outras contas ficam intactas.
- CA13 (R12): dado cada uma das ações do R12, então há registro de auditoria com ator, ação e IP, em cada organização com vínculo ativo, sem os dados proibidos.
- CA14 (R13): dado sem sessão, quando chama cada rota nova, então 401; conta A não vê nem encerra sessão da conta B (teste de isolamento entre contas e organizações); pedido de e-mail da conta A não é confirmável pela conta B.
- CA15 (R14): dado a tela `/conta` em 360 px, então sem rolagem horizontal, axe sem violações nos dois temas, estados de carregando, erro (com "Tentar de novo"), lista de sessões vazia (só a atual) e sessão expirada (volta ao login) exibidos; vale para perfil de gestão e para perfil de portal (Aluno/Responsável) no teste de tela.
- CA16 (R1, R14): ponta a ponta Playwright `@critical` "Troca de senha encerrando as outras sessões": entra, abre Minha conta, troca a senha, a lista de sessões mostra só a atual e a outra sessão é negada.
- CA17 (R15): `OpenApiContractTest` e `ContractRoutesTest` passam; testes MSW cobrem as rotas novas no mesmo formato do servidor.

## 4. Plano técnico

- **Dados.** `V18__account_email_change.sql` (a criar; conferir o próximo número livre): tabela `account_email_change` (`id`, `account_id` fk, `new_email`, `code_hash` único, `expires_at`, `used_at`, `canceled_at`, `created_at`) com RLS no escopo da conta (`app.account_id`), como `reauth_token` na V11. Reversão: nova migração com `drop table`. Sessões usam `account_session` existente (sem coluna nova; `device` guarda o User-Agent e vira rótulo por `DeviceLabel`). Seed: não muda.
- **API** (`/api/v1/auth`, todas com cookie de sessão e CSRF; contrato em `docs/openapi.yaml`): `POST /email-change` (202), `POST /email-change/{code}/confirm` (204), `GET /sessions` (200, lista), `DELETE /sessions/{id}` (204/404/409), `DELETE /sessions` (204). Erros novos em `api/error/ErrorCode.java` com `messages.properties`: `EMAIL_CHANGE_INVALID` (410), `SESSION_CURRENT` (409). Reuso de `INVALID_CREDENTIALS`, `INVALID_TWO_FACTOR_CODE`, `TOO_MANY_REQUESTS`. O `TwoFactorGateFilter` não precisa liberar as rotas novas: quem é obrigado e ainda não configurou as duas etapas só chega às rotas já liberadas.
- **Back-end** (`br.com.ctcore.identity`): `EmailChangeService.java` (a criar: pedido, cancelamento do anterior, limite de 3/hora, confirmação com troca em uma transação e checagem de unicidade `lower(email)`); `AccountSessions` (a criar, ou métodos novos em `SessionService`: `list(accountId, currentId)`, `endOne(accountId, id, currentId)`, reaproveitando `endAll(accountId, except)` já existente); `AuthController` ganha as rotas de sessão e de e-mail (já tem `/password`, `/session`); `AccountMailer`/`EmailAccountMailer` ganham `emailChange(to, code)` e `emailChanged(to)`; `mail/EmailTemplate` ganha `EMAIL_CHANGE` (link, segredo) e `EMAIL_CHANGED`, com textos em `messages.properties`. Auditoria por `AccountAudit.record` (ações `account.email_change_requested`, `account.email_changed`, `account.session_ended`, `account.sessions_ended` já usada em `AuthService.change`). `TwoFactorService.confirm` (existente, pacote privado) fornece senha + código + trava do login; usado pelo pedido de e-mail. `AuthService.change` passa a checar `attempts.check` e registrar `WRONG_PASSWORD`/`SUCCESS` (R2). Código de confirmação gerado por `Codes.random()` e guardado só como `Codes.hash`.
- **Testes back-end:** `EmailChangeIT` (CA3 a CA8), `SessionListIT` (CA10 a CA12), ajuste de `AuthRoutesIT` (CA2: trava), `AccountRoutesIsolationIT` (CA14; seguir `AuthIsolationIT`/`TwoFactorIsolationIT`), auditoria em `AccountAuditIT` ou nos ITs acima (CA13), `EmailChangeServiceTest` rápido para limite e validade com `Clock`.
- **Segurança.** Conta sempre vem do principal da sessão, nunca de parâmetro; link de e-mail é segredo de uso único, com hash e validade; resposta ao pedido não revela se o endereço existe (R5); senha (e código) exigidos para trocar o e-mail, o vetor de recuperação da conta; auditoria sem o endereço novo completo (D6); sessões nunca devolvem token, hash nem IP.
- **Front-end:** a criar em `frontend/src/features/account/`: `AccountPage.tsx` (rota `/conta`; seções Senha, E-mail, Duas etapas, Sessões), `ChangePasswordForm.tsx` (reusa `PasswordFields`/`PasswordRules`), `ChangeEmailForm.tsx`, `TwoFactorSection.tsx` (reusa `TwoFactorSetupDialog`, `BackupCodesDialog`, `CodeField`, `ReauthDialog`/diálogo senha+código existentes), `SessionsSection.tsx` (lista, "Encerrar", "Encerrar todas as outras" com `ConfirmDialog`), `useAccount.ts` (TanStack Query, já usado no projeto), `ConfirmEmailPage.tsx` (rota `/confirmar-email/:code` dentro de `RequireSession`, para o login devolver ao link) e testes. A alterar: `App.tsx` (rotas), o menu/cabeçalho que lista os itens (`components/layout/AppLayout.tsx` recebe os grupos; achar onde são montados e acrescentar "Minha conta"), `mocks/handlers.ts` (+ teste MSW), `i18n/pt-BR.json`. Reuso: `Card`, `states/*`, `ConfirmDialog`, `RelativeDate`, `lib/api.ts`. Estados: carregando e erro por seção, vazio da lista de sessões, sem sessão pelo `RequireSession` existente.
- **Riscos.** Descobrir cedo se `TwoFactorService.confirm` serve ao pedido de e-mail sem registrar `SUCCESS` indevido; `GET /auth/sessions` sob `forAccount` (RLS de `account_session` não existe hoje, conferir na construção que a consulta sempre filtra por `account_id`); o e2e depende de duas sessões no mock (usar dois contextos do Playwright); o nome da próxima migração pode conflitar com outra em andamento (conferir ao criar).

## 5. Tarefas

Duas partes, cada uma construível em uma sessão: servidor (T1 a T6) e tela (T7 a T11).

- [x] T1: migração `V18__account_email_change.sql`, códigos `EMAIL_CHANGE_INVALID` e `SESSION_CURRENT` com mensagens, templates `EMAIL_CHANGE` e `EMAIL_CHANGED` e métodos em `AccountMailer` — não cobre critério: base de CA3 a CA8 e CA11 — prova: `./backend/mvnw -f backend/pom.xml compile`.
- [x] T2: `EmailChangeService` e rotas `POST /auth/email-change` e `.../{code}/confirm` com auditoria — cobre CA3, CA4, CA5, CA6, CA7, CA8, CA13 — prova: `verify -Dit.test='EmailChangeIT'` e `-Dtest=EmailChangeServiceTest`.
- [x] T3: sessões: `GET /auth/sessions`, `DELETE /auth/sessions/{id}`, `DELETE /auth/sessions`, com auditoria — cobre CA10, CA11, CA12, CA13 — prova: `verify -Dit.test='SessionListIT'`.
- [x] T4: trava por senha atual errada em `AuthService.change` — cobre CA2 (parte servidor) — prova: `verify -Dit.test='AuthRoutesIT'`.
- [x] T5: teste de isolamento e de acesso sem sessão para as rotas novas — cobre CA14 — prova: `verify -Dit.test='AccountRoutesIsolationIT'`.
- [x] T6: OpenAPI (cinco rotas, esquemas, erros) e handlers MSW — cobre CA17 — prova: `./backend/mvnw -f backend/pom.xml test -Dtest='OpenApiContractTest,ContractRoutesTest'` e `npx vitest run src/mocks`.
- [x] T7: `AccountPage` com seções Senha e E-mail (formulários, erros, confirmação), `ConfirmEmailPage`, rota e item de menu, textos pt-BR — cobre CA1, CA2, CA3, CA7, CA8 (lado tela) — prova: `npx vitest run src/features/account`.
- [x] T8: seções Duas etapas e Sessões da tela (reuso dos diálogos existentes, "Encerrar", "Encerrar todas as outras") — cobre CA9, CA11, CA12 (lado tela) — prova: `npx vitest run src/features/account`.
- [x] T9: estados, 360 px e axe nos dois temas, para perfil de gestão e de portal — cobre CA15 — prova: `npx vitest run src/features/account`.
- [x] T10: e2e `@critical` "Troca de senha encerrando as outras sessões" — cobre CA16 — prova: `npm run e2e` em `frontend/`.
- [x] T11: regressão completa — não cobre critério: confere o "Pronto quando" (cobertura e 100% em `identity`/auth conforme CI) — prova: `./backend/mvnw -f backend/pom.xml verify` e, em `frontend/`, `npm run lint && npm run typecheck && npm run build && npm run test:cov && npm run e2e`.

## 6. Decisões

- D1: uma só tela `/conta` serve gestão e portal (o card tem dois canvas; o portal é a mesma aplicação responsiva). As rotas são de `/auth` e dependem só da conta, não do vínculo ativo. Alternativa descartada: telas separadas por área. Os canvas `MinhaConta.dc.html` e `PortalConta.dc.html` não estão acessíveis ao agente; o layout segue os padrões do projeto.
- D2 (muda o comportamento): o pedido de troca de e-mail exige a senha e, com duas etapas ligadas, o código. Motivo: o e-mail é o canal de recuperação da conta; sessão roubada não deve bastar. Alternativa: exigir só a sessão (mais simples, mais fraco). A troca de senha mantém só a senha atual, como já está.
- D3 (muda o comportamento): senha atual errada na troca de senha passa pela mesma trava do login (RN-ACE-10). Motivo: sem isso, uma sessão roubada permite adivinhar a senha sem limite. Alternativa: sem limite (estado atual).
- D4: o aviso ao endereço antigo sai ao confirmar a troca, que é quando "vale" (RN-ACE-15). Alternativa: avisar também no pedido, para dar chance de reagir antes; fica como melhoria se o dono quiser.
- D5: limite de 3 pedidos de troca de e-mail por hora por conta, contado na própria tabela. Motivo: evitar uso do envio como canal de spam; o número segue o padrão de reenvios do projeto (RN-ACE-13). Alternativa: sem limite.
- D6: a auditoria da troca de e-mail registra só a ação e o IP, sem endereços completos. Motivo: é dado pessoal na conta e a auditoria da conta não guarda antes/depois (padrão de `AccountAudit`). Alternativa: gravar antes e depois mascarados.
- D7 (muda o comportamento): a sessão atual não pode ser encerrada por `DELETE /auth/sessions/{id}` (409); para ela existe "Sair". Alternativa: permitir e limpar o cookie.
- D8: a confirmação do e-mail exige estar logado na conta dona; quem abre o link sem sessão faz login e volta (RN-ACE-16). Motivo: o link sozinho não deve trocar um e-mail. Alternativa: link público de uso único.
- D9: a lista de sessões mostra o rótulo do aparelho e a cidade aproximada (hoje sempre vazia, `NoIpLocator`), nunca o IP. Alternativa: mostrar o IP (dado pessoal sem necessidade).
- Suposição: a troca de e-mail não encerra as outras sessões (só a troca de senha encerra, RN-ACE-20). Se o dono quiser encerrar, é uma linha na confirmação.
- Suposição: nome e telefone não são editáveis nesta tela, porque o card não os cita.

## Construção

Parte 1 de 2 (servidor, T1 a T6) e parte 2 de 2 (tela, T7 a T11) concluídas.

- T1 — arquivos: `backend/src/main/resources/db/migration/V18__account_email_change.sql`, `api/error/ErrorCode.java`, `api/error/ApiExceptionHandler.java`, `identity/IdentityException.java`, `mail/EmailTemplate.java`, `identity/AccountMailer.java`, `identity/EmailAccountMailer.java`, `messages.properties` — prova: `./backend/mvnw -f backend/pom.xml compile` → ok; a migração é exercida em todos os `*IT` (Flyway sobe até a V18).
- T2 — arquivos: `identity/EmailChangeService.java`, `identity/AuthController.java` (`POST /auth/email-change`, `POST /auth/email-change/{code}/confirm`), `EmailChangeIT` (6 testes: CA3 a CA8, CA13) — prova: `verify -Dit.test='EmailChangeIT,...'` → 27 testes, 0 falhas.
- T3 — arquivos: `identity/SessionService.java` (`list`, `endOne`, `endOthers`, com `IpLocator` e `AccountAudit`), `AuthController.java` (3 rotas), `SessionListIT` (CA10 a CA13) — prova: mesma execução → verde.
- T4 — arquivos: `identity/AuthService.java` (`change` checa `attempts.check` e registra `WRONG_PASSWORD`/`SUCCESS`), `AuthRoutesIT.changePasswordLockout` (CA2: 5 erros, 429, destrava após 15 min) — prova: mesma execução → verde (`AuthRoutesIT` 16 testes).
- T5 — arquivos: `AccountRoutesIsolationIT` (401 sem sessão nas 5 rotas, CSRF, e A x B nos cinco perfis; Coordenador com duas etapas ligadas por causa do `TwoFactorGateFilter`) — prova: mesma execução → verde.
- T6 — arquivos: `docs/openapi.yaml` (5 rotas, `EmailChangeRequest`, `AccountSession`, erros `EMAIL_CHANGE_INVALID` e `SESSION_CURRENT`), `frontend/src/mocks/handlers.ts` (também ganhou `POST /auth/password`, que não tinha handler), `frontend/src/mocks/account.msw.test.ts` — prova: `./backend/mvnw -f backend/pom.xml test -Dtest='OpenApiContractTest,ContractRoutesTest'` → 7 testes verdes; `npx vitest run src/mocks` → 9 arquivos, 37 testes verdes.

Regressão (parte do servidor): `./backend/mvnw -f backend/pom.xml verify` → BUILD SUCCESS, 211 testes, 0 falhas, JaCoCo ok. `npm run lint` e `npm run typecheck` em `frontend/` → ok. Front-end completo (`test:cov`, `build`, `e2e`) fica para a T11.

Desvios:
- `EmailChangeServiceTest` (rápido) não foi criado: o serviço depende de banco; limite de 3/hora e validade de 24 h estão provados no `EmailChangeIT` com o relógio controlável.
- V18 também altera `email_message_template_check` para aceitar `EMAIL_CHANGE` e `EMAIL_CHANGED` (sem isso o envio falhava; mesmo ajuste da V16).
- `AccountMailer.emailChange` e `emailChanged` são `default` vazios, como `newAccess`, para não quebrar o duplo de teste do `InviteIT`.
- Foi criado o código interno `SESSION_NOT_FOUND` (vira 404 `NOT_FOUND`) em `IdentityException`.
- O handler MSW de `/auth/password` não existia e foi criado aqui (a T7 depende dele).
- Risco do plano resolvido: `TwoFactorService.confirm` serve ao pedido de e-mail; o `SUCCESS` só é registrado com senha (e código) certos, igual aos outros usos. `account_session` não tem RLS: `list`, `endOne` e `endAll` sempre filtram por `account_id`.
Não é do item: nenhum.

Parte 2 (tela):
- T7 — arquivos: `frontend/src/features/account/{AccountFrame,AccountPage,ChangePasswordForm,ChangeEmailForm,ConfirmEmailPage,useAccount}.ts(x)`, `App.tsx` (rotas `/conta` e `/confirmar-email/:code` dentro de `RequireSession`), `features/permissions/ManagementLayout.tsx` e cabeçalho do portal (link "Minha conta"), `i18n/pt-BR.json` (bloco `account`), `lib/api.ts` (202 sem corpo é sucesso) e `api.test.ts` — prova: `npx vitest run src/features/account` → verde.
- T8 — arquivos: `TwoFactorSection.tsx` (liga, desliga, códigos novos; reusa `TwoFactorSetupDialog` e `BackupCodesDialog`; `DisableDialog` novo), `SessionsSection.tsx` (Encerrar; Encerrar todas as outras com `ConfirmDialog`) — prova: mesma execução → verde.
- T9 — arquivos: `AccountPage.test.tsx` (20 testes: carregando, erro com "Tentar de novo", vazio, 401 volta ao login, axe nos dois temas para Coordenador e para Aluno/Responsável com viewport de 360 px) — prova: `npx vitest run src/features/account` → 20 testes verdes.
- T10 — arquivos: `frontend/e2e/minha-conta.spec.ts` — prova: `npm run e2e` → 14 testes verdes, incluindo o novo.
- T11 — prova: `npm run lint`, `npm run typecheck`, `npm run build` → ok; `npm run test:cov` → 53 arquivos, 411 testes verdes, cobertura 94% de linhas; `npm run e2e` → 14 verdes; `./backend/mvnw -f backend/pom.xml verify` → sem falha (relatórios: 335 testes, 0 falhas; JaCoCo sem reprovação).

Desvios da parte 2:
- O e2e não usa dois contextos do Playwright: o MSW vive em cada aba e não compartilha estado. A "outra sessão" é a listada pelo mock; o fato de ela passar a dar 401 está provado no back-end (`SessionListIT`, `AuthRoutesIT`).
- A confirmação do e-mail é um botão "Confirmar troca" (não automática), para o link não ser gasto por releitura/StrictMode; o código segue de uso único.
- "Minha conta" entrou como link no cabeçalho (gestão e portal), não como item do catálogo de menu, que exige uma permissão por item.
- `lib/api.ts` tratava só 204 como corpo vazio e quebrava com o 202 do pedido de troca de e-mail; corrigido com teste.
- Sem TanStack Query no projeto: `useAccount.ts` segue o padrão de `useOrganization.ts` (fetch com estado).
- Texto de tela "Minha conta" no portal depende da área do portal (Sprint 14); aqui o portal ganhou só o cabeçalho mínimo.
Não é do item: nenhum.

## Verificação

### Rodada 1 — NAO_CONFERIDO — 2026-10-18, estado c5390fa545c28e74aa778db69ff1c01ea7f91eaf

| Critério | Status | Evidência |
|---|---|---|
| CA1 | NÃO CONFERIDO | lado tela: `AccountPage.test.tsx` e e2e verdes; lado servidor (outra sessão 401, aviso) depende de `AuthRoutesIT`/`SessionListIT`, que não rodaram (Docker parado) |
| CA2 | NÃO CONFERIDO | tela verde em `vitest`; trava 429 só em `AuthRoutesIT.changePasswordLockout` (não rodou). Código de `AuthService.change` lido: `attempts.check` + `record` presentes |
| CA3 a CA8 | NÃO CONFERIDO | `EmailChangeIT` exige Docker; `EmailChangeService` lido e coerente com os critérios |
| CA9 | PASSOU | `AccountPage.test.tsx` (20 testes) verde em `npm run test:cov` |
| CA10 a CA14 | NÃO CONFERIDO | `SessionListIT`, `AccountRoutesIsolationIT`, auditoria dependem de Docker; `list/endOne/endOthers` lidos, filtram sempre por `account_id` |
| CA15 | PASSOU | testes de estados, 360 px e axe nos dois temas (gestão e portal) verdes |
| CA16 | PASSOU | `npm run e2e`: 14 verdes, incluindo `@critical troca de senha encerrando as outras sessões` |
| CA17 | PASSOU | `./backend/mvnw -f backend/pom.xml test` (123 testes, inclui `OpenApiContractTest` e `ContractRoutesTest`) BUILD SUCCESS; `account.msw.test.ts` verde na suíte do front |

Comandos:
- `docker ps` → daemon indisponível (`dockerDesktopLinuxEngine` não encontrado); os `*IT` não puderam rodar.
- `./backend/mvnw -f backend/pom.xml test` → 123 testes, 0 falhas.
- `npm run lint && npm run typecheck && npm run test:cov` (frontend) → ok; cobertura linhas 96,74%.
- `npm run build && npm run e2e` → ok, 14 verdes.
- Relatórios antigos em `backend/target/failsafe-reports` são de 16/10, não valem para este estado.

Achados:
- Nenhum achado obrigatório de código ou especificação no que foi lido (`EmailChangeService`, V18, rotas do `AuthController`, `AuthService.change`, `SessionService`).
- sugestão, código, `EmailChangeServiceTest` não criado (desvio já registrado); limite e validade só têm prova no IT.
- sugestão, código, `AuthController.java` imports fora de ordem (`java.util.List` entre `jakarta`).

Falta para fechar: subir o Docker e rodar `./backend/mvnw -f backend/pom.xml verify` (ou `-Dit.test='EmailChangeIT,SessionListIT,AuthRoutesIT,AccountRoutesIsolationIT'`), conferindo CA1 a CA8 e CA10 a CA14 e os 100% de `identity`/`isolation` do JaCoCo.

### Rodada 2 — OK — 2026-10-18, estado 39eac5302a08946cc013838ab7e40b4d722ee3b9

Desde a rodada 1 (estado c5390fa) só mudou este arquivo (`git diff --stat`: 1 arquivo); código idêntico. Docker agora disponível.

| Critério | Status | Evidência |
|---|---|---|
| CA1 a CA2 | PASSOU | `AuthRoutesIT` (16 testes, inclui `changePasswordLockout`) e `SessionListIT` verdes no `verify`; tela mantida da rodada 1 |
| CA3 a CA8 | PASSOU | `EmailChangeIT` 6 testes, 0 falhas |
| CA9 | PASSOU (mantido da rodada 1) | `AccountPage.test.tsx` |
| CA10 a CA12 | PASSOU | `SessionListIT` 3 testes, 0 falhas |
| CA13 | PASSOU | auditoria exercida em `EmailChangeIT` e `SessionListIT` |
| CA14 | PASSOU | `AccountRoutesIsolationIT` 2 testes, 0 falhas |
| CA15, CA16 | PASSOU (mantido da rodada 1) | vitest e `npm run e2e` |
| CA17 | PASSOU | `OpenApiContractTest`/`ContractRoutesTest` dentro dos 123 testes unitários verdes |

Comandos:
- `./backend/mvnw -f backend/pom.xml verify` → BUILD SUCCESS; 123 testes unitários e 211 de integração, 0 falhas; "All coverage checks have been met" (JaCoCo, incluindo 100% nos pacotes exigidos).
- Front-end não reexecutado: nenhuma mudança desde a rodada 1.

Achados:
- Nenhum obrigatório.
- sugestão, código, `EmailChangeServiceTest` rápido não criado (desvio registrado; coberto pelo IT).
- sugestão, código, `AuthController.java` imports fora de ordem.

## Entrega

Tela "Minha conta" (`/conta`) com troca de senha (com bloqueio por tentativas), troca de e-mail por link de uso único (24 h, 3 pedidos por hora), duas etapas (ligar, desligar, códigos novos) e lista de sessões (encerrar uma ou todas as outras). Cinco rotas novas em `/api/v1/auth`, no OpenAPI e nos handlers MSW; migração V18; toda ação grava auditoria.

Verificação: `verify` do back-end verde (123 unitários e 211 de integração, JaCoCo ok); front-end com lint, typecheck, build, `test:cov` (94%) e `e2e` (14) verdes; testes de isolamento entre organizações e perfis nas cinco rotas.

Decisões e limitações: a troca de e-mail não encerra as outras sessões (só a de senha); a lista mostra rótulo do aparelho e cidade aproximada, nunca o IP; nome e telefone não são editáveis aqui; "Minha conta" é link no cabeçalho, não item do catálogo de menu; o portal ganhou só o cabeçalho mínimo (área completa na Sprint 14). A base `dev` não avançou desde a branch.
