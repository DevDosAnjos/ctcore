# 444 — Gerar códigos de reserva sem a confirmação de senha e código

Origem: issue #444 (Sprint 10.5 · Identidade visual); RF-ACE-04, RN-ACE-18, RN-ACE-21 (`docs/especificacao/02-contas-e-acesso.md`), decisão 3 de `docs/auditoria/decisoes-canvas.md`, relatório M45 (CodigosReserva). Branch: `item/444-codigos-reserva-sem-confirmacao`.

## 1. Objetivo

Hoje "Ver códigos de reserva novos" (Minha conta) abre uma janela que pede senha e código de duas etapas e só então mostra os 10 códigos. O canvas mostra os códigos direto e a especificação não pede a confirmação (RN-ACE-21 não lista esta ação). O item faz a rota `POST /api/v1/auth/two-factor/backup-codes` aceitar só a sessão autenticada e a janela gerar e mostrar os códigos ao abrir.

Fora do escopo: o visual da janela (grade 2x5, formato `4821-9037`, "Copiar", "Baixar .txt", caixa âmbar) é da #490; o `ReauthDialog` e as ações críticas da RN-ACE-21 não mudam; desligar duas etapas continua pedindo senha e código.

Correção de fato: a issue diz que o endpoint "exige o token de ação crítica"; no código ele exige `password` e `code` no corpo (não usa `reauthToken`). A mudança é a mesma: deixar de exigir confirmação.

## 2. Requisitos

- R1: `POST /api/v1/auth/two-factor/backup-codes` não recebe corpo nem exige senha, código ou token de reautenticação; a sessão autenticada basta e responde 200 com 10 códigos novos.
- R2: Gerar invalida os códigos de reserva anteriores (não valem mais no login); os novos valem uma única vez.
- R3: Sem as duas etapas ligadas a rota responde 409; sem sessão, 401; sem CSRF, recusa. Só mexe na conta de quem chama (isolamento entre organizações e perfis).
- R4: A geração é auditada (`account.backup_codes_regenerated`) sem gravar os códigos.
- R5: "Ver códigos de reserva novos" gera e mostra os 10 códigos ao abrir a janela, sem campo de senha nem de código; a janela mantém os estados carregando, erro, sem permissão e a confirmação "Já guardei" antes de concluir (RN-ACE-18).
- R6: Contrato (`docs/openapi.yaml`), handler MSW e especificação ficam de acordo com R1 (D2).

## 3. Critérios de aceite

- CA1 (R1, R2): dado `duas@exemplo.com`-like com duas etapas ligadas, quando faz POST na rota sem corpo, então 200 com 10 códigos distintos dos anteriores e só os 10 novos existem na tabela.
- CA2 (R2): dado os códigos antigos e os novos, quando tenta entrar com um código antigo, então é recusado; com um novo, entra uma vez e o mesmo código não vale de novo.
- CA3 (R3): dado conta sem duas etapas, quando faz POST, então 409; sem sessão 401; sem cabeçalho CSRF recusa.
- CA4 (R3): dado perfis COORDINATOR, ADMINISTRATOR, TEACHER, STUDENT e GUARDIAN de uma organização com duas etapas ligadas e as de outra organização, quando cada um chama a rota, então só os códigos da própria conta mudam e os da outra ficam intactos.
- CA5 (R4): dado a geração, quando se lê `audit_log`, então há 1 evento `account.backup_codes_regenerated` e nenhum dado dele contém os códigos.
- CA6 (R5): dado a conta com duas etapas, quando abre "Ver códigos de reserva novos", então aparecem 10 códigos sem pedir senha nem código, e abrir uma vez faz uma só chamada (inclusive em StrictMode).
- CA7 (R5): dado erro 500, 403 ou 429 na chamada, quando a janela abre, então mostra erro com "Tentar de novo", "Você não tem acesso" ou o aviso de muitas tentativas; a janela passa no axe a 360 px nos dois temas.

## 4. Plano técnico

- Dados: não muda (`account_backup_code`, `newBackupCodes` já apagam os antigos).
- API: `POST /auth/two-factor/backup-codes` sem corpo, mesma resposta `BackupCodes`; erros 401, 409, 429 não cabem mais por senha/código (manter 401 sessão, 403, 409; o 429 sai se nenhum limitador restar).
- Interface: `BackupCodesDialog` sem `codes` dispara a geração ao abrir (efeito com `useRef` contra a dupla execução do StrictMode), mostra carregando, depois `BackupCodesPanel`; `failed` -> `ErrorState` com retentar; `forbidden` -> `ForbiddenState`; 429 -> `ErrorState` genérico (D3). Some o formulário de senha/código.
- Segurança: conferida no servidor pela sessão (`@AuthenticationPrincipal`); o front não é barreira. Efeito aceito: sessão roubada passa a poder trocar os códigos (decisão 3 do dono). Auditoria sem os códigos.
- Arquivos (vistos):
  - `backend/src/main/java/br/com/ctcore/identity/TwoFactorController.java`: `backupCodes` perde `@RequestBody PasswordCodeRequest`.
  - `backend/src/main/java/br/com/ctcore/identity/TwoFactorService.java`: `regenerate(accountId, ip)` sem `confirm`; manter `requireEnabled`; ajustar o javadoc. `PasswordCodeRequest` segue usado por `disable`.
  - `docs/openapi.yaml` (`/auth/two-factor/backup-codes`, ~linha 196): tirar `requestBody` e 401 por senha/código; resumo e descrição.
  - `frontend/src/mocks/handlers.ts` (~linha 1908): handler sem corpo; 401 se sem sessão; 409 se `!current.twoFactor.enabled`; devolve `backupCodes()`.
  - `frontend/src/features/auth/BackupCodesDialog.tsx`: `BackupCodesDialog`; remover estados `password`, `code`, `errors`, `submit` com formulário e imports sem uso (`Field`, `Input`, `CodeField`, `FormEvent`).
  - `frontend/src/i18n/pt-BR.json` (`twoFactor.codes`): remover `regenIntro`, `password`, `code`, `submit`, `sending`, `invalidPassword`, `invalidCode`, `required` se sem uso; acrescentar texto de carregando se faltar.
  - Testes a ajustar: `backend/src/test/java/br/com/ctcore/identity/TwoFactorServiceIT.java` (`regenerate`), `TwoFactorAuditIT.java` (linha 29), `TwoFactorLoginIT.java` (linha 66), `TwoFactorIsolationIT.java` (linha 71, `ROUTES`), `frontend/src/features/auth/TwoFactorDialogs.test.tsx` (`BackupCodesDialog (CA22)`), `frontend/src/mocks/twofactor.msw.test.ts` (linhas 36-39).
  - `docs/especificacao/02-contas-e-acesso.md`: RF-ACE-04 e RN-ACE-21 (D2).
- Reuso: `BackupCodesPanel`, `ErrorState`, `ForbiddenState`, `api`, `newBackupCodes`, `AuthBase` e helpers dos `*IT`.
- Riscos: a geração ao abrir invalida os códigos antigos mesmo sem o usuário concluir (aceito pelo canvas, D1); caso visual `conta-codigos-reserva` (`frontend/visual/casos.ts`, clica "Gerar códigos de reserva novos") pode mudar de rótulo/estado; se mudar, regerar só a referência desse caso e manter o `limiteCanvas` da #490.

## 5. Tarefas

- [x] T1: Back-end: controller e `regenerate` sem senha/código; atualizar `TwoFactorServiceIT.regenerate` (200 sem corpo, antigos invalidados, 401 sem sessão, 409 sem duas etapas), `TwoFactorAuditIT`, `TwoFactorLoginIT` e `TwoFactorIsolationIT` (chamada sem corpo; perfis e organização) — cobre CA1, CA2, CA3, CA4, CA5 — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test='TwoFactor*IT' -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false`
- [x] T2: Contrato e MSW: `docs/openapi.yaml` e handler sem corpo; ajustar `twofactor.msw.test.ts` — cobre CA1 (lado MSW) — prova: `npm test -- twofactor.msw` e `./backend/mvnw -f backend/pom.xml test -Dtest='OpenApiContractTest,ContractRoutesTest'`
- [x] T3: Front: `BackupCodesDialog` gera ao abrir, sem formulário; textos pt-BR; reescrever os testes de `BackupCodesDialog (CA22)` (10 códigos sem senha, uma só chamada, 500/403/429, axe 360 px nos dois temas) — cobre CA6, CA7 — prova: `npm test -- TwoFactorDialogs`
- [x] T4: Especificação: acrescentar em RF-ACE-04 que ver códigos novos não pede senha e em RN-ACE-21 que gerar códigos de reserva não é ação crítica — não cobre critério: mantém a especificação de acordo com o contrato (R6) — prova: conferência de `grep -n "códigos de reserva" docs/especificacao/02-contas-e-acesso.md`
- [x] T5: Visual: conferir `conta-codigos-reserva` no teste visual; regerar só esse caso se mudou — não cobre critério: regra do AGENTS para tela alterada — prova: `npm run test:visual:docker -- -g "conta-codigos-reserva"` (de dentro de `frontend/`)

## 6. Decisões

- D1 (muda o comportamento): gerar na abertura da janela, sem passo intermediário "Gerar"/"Cancelar", como o canvas (decisão 3 do dono). Alternativa descartada: manter um botão de confirmar sem senha; o canvas mostra os códigos direto.
- D2 (muda o comportamento): a especificação já não exige a confirmação; ainda assim, deixar explícito em RF-ACE-04 e RN-ACE-21 que gerar códigos não é ação crítica, para ninguém religar a exigência por engano. Alternativa: não mexer na especificação.
- D3: 429 não existe mais nesta rota (sem tentativa de senha para contar); se o servidor devolver 429 de outro limitador, cai em `ErrorState` genérico. Alternativa: texto próprio de muitas tentativas.
- D4: a chamada vai sem corpo; o servidor ignora corpo enviado por cliente antigo (sem `@RequestBody`).
- Suposição: o botão da tela de Minha conta já abre a janela só com duas etapas ligadas (`TwoFactorSection`); se houver caminho sem isso, a rota responde 409 e a janela mostra erro.

## Construção

- T1 — arquivos: TwoFactorController, TwoFactorService (`regenerate(accountId, ip)`), TwoFactorServiceIT, TwoFactorAuditIT, TwoFactorLoginIT, TwoFactorIsolationIT — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test='TwoFactor*IT' -Dtest=NONE` → 17 testes, 0 falhas
- T2 — arquivos: docs/openapi.yaml (sem requestBody, 401/409), frontend/src/mocks/handlers.ts, twofactor.msw.test.ts — prova: `npm test -- twofactor.msw` → 3 passam; `OpenApiContractTest,ContractRoutesTest` → 7 passam
- T3 — arquivos: BackupCodesDialog.tsx, pt-BR.json (saiu regenIntro, password, code, invalidPassword, invalidCode, submit e sending de codes; `required` fica, usado em TwoFactorSection), TwoFactorDialogs.test.tsx, AccountPage.test.tsx — prova: `npm test -- TwoFactorDialogs AccountPage` → 35 passam; typecheck e lint limpos
- T4 — arquivos: docs/especificacao/02-contas-e-acesso.md (RF-ACE-04, RN-ACE-21) — prova: leitura do diff
- T5 — arquivos: visual/referencias/conta-codigos-reserva-*.png (4) — prova: `npm run test:visual:docker -- -g conta-codigos-reserva` falhou antes (esperado, a janela agora mostra os códigos), `--update-snapshots` regerou e passou 4/4; imagem 390 claro conferida (10 códigos, sem formulário)
Regressão: `npm run test:cov` → 192 arquivos, 1915 testes passam; `./backend/mvnw -f backend/pom.xml verify` → 862 testes, 0 falhas, BUILD SUCCESS. Não rodei `e2e` (Playwright) nem a comparação `visual:canvas`.
Desvios: CA2 (login com código antigo recusado) segue coberto pelo teste existente que checa que o hash antigo some da tabela e pelo `backupCodeLogin`; sem teste novo de login com código antigo. Handlers MSW da conta de teste dos testes de tela usam `server.use` porque a sessão do teste não altera o estado `current` do mock (409 sem duas etapas).
Não é do item: nenhum.

## Verificação

### Rodada 1 — CORRIGIR_CODIGO — 2026-10-31, estado 218d171a06dff3998d8f294afe26668ee8e83c0b

| Critério | Status | Evidência |
|---|---|---|
| CA1 | PASSOU | `TwoFactorServiceIT.regenerate`: 200 sem corpo, 10 códigos distintos dos antigos, só 10 na tabela |
| CA2 | PASSOU | hash antigo some da tabela (`regenerate`); `TwoFactorLoginIT.backupCodeLogin` usa o código novo uma vez e o repetido dá 401. Sem teste de login com código antigo (desvio declarado), mas o hash ausente prova |
| CA3 | PASSOU | 401 sem sessão (`regenerate`, `anonymousAndCsrf`), 403 sem CSRF (`anonymousAndCsrf`, ROUTES inclui a rota), 409 sem duas etapas (`accountsStaySeparate`) |
| CA4 | NÃO PASSOU | ver achado 1 |
| CA5 | PASSOU | `TwoFactorAuditIT`: 1 evento `account.backup_codes_regenerated`; teste existente checa que os códigos não aparecem |
| CA6 | PASSOU | `TwoFactorDialogs.test`: 10 itens, sem campos de senha/código, 1 chamada em StrictMode com corpo vazio; `AccountPage.test` |
| CA7 | PASSOU | testes de 429/500 (Tentar de novo), 403 (sem acesso) e axe a 360 px nos dois temas passam |

Comandos: `mvnw verify -Dit.test='TwoFactor*IT'` 17 testes, 0 falhas; `OpenApiContractTest,ContractRoutesTest` 7 passam; vitest TwoFactorDialogs/AccountPage/twofactor.msw 38 passam; `npm run typecheck` e `lint` limpos; `npm run test:cov` 192 arquivos, 1915 testes passam; `mvnw verify` completo 862 testes, 0 falhas, BUILD SUCCESS. Não rodei `e2e` nem `visual:canvas`/`test:visual:docker`; as referências PNG alteradas não foram conferidas por mim (CI as valida).
Instáveis: nenhum.
Achados:
- obrigatório, código, `backend/src/test/java/br/com/ctcore/identity/TwoFactorIsolationIT.java` `accountsStaySeparate` — esperado (CA4): perfis COORDINATOR, ADMINISTRATOR, TEACHER, STUDENT e GUARDIAN com duas etapas ligadas chamam a rota e só os códigos da própria conta mudam, os da outra organização ficam intactos; observado: os perfis nunca ligam as duas etapas, o loop só afirma 409 na rota; o caminho 200 por perfil e a troca só dos próprios códigos não são exercitados (a asserção final dos 10 códigos de B é satisfeita sem nada ter sido gerado); evidência: leitura do teste (linhas ~64-77); corrigir: no loop, ligar as duas etapas de cada perfil (`activate`), chamar a rota com 200, conferir que os códigos antigos dele saíram e os 10 de B (e dos demais perfis) seguem iguais.
- sugestão, código, `pt-BR.json` `twoFactor.codes.required` — texto "Preencha a senha e o código." continua só porque `TwoFactorSection` usa; ok, sem ação.

### Correção da rodada 1
- achado 1 (CA4, `TwoFactorIsolationIT.accountsStaySeparate`) — no loop, cada perfil liga as duas etapas (`activate`), chama a rota com 200, os 10 hashes mudam e os hashes de B e dos perfis anteriores ficam iguais; no fim, os de B conferem com o início — arquivos: backend/src/test/java/br/com/ctcore/identity/TwoFactorIsolationIT.java — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test='TwoFactor*IT' -Dtest=NONE` → 17 testes, 0 falhas, BUILD SUCCESS
Regressão: só teste de back-end alterado; os 17 `TwoFactor*IT` passam, sem mudança de código de produção desde a regressão completa da rodada anterior (862 testes, 1915 no front).

### Rodada 2 — OK — 2026-10-31, estado 24c32028edca86f81632d3ffcf27f7d13d379b32

| Critério | Status | Evidência |
|---|---|---|
| CA1, CA2, CA3, CA5, CA6, CA7 | PASSOU | mantido da rodada 1 (diff desde o estado da rodada 1 toca só `TwoFactorIsolationIT` e este arquivo; código de produção igual) |
| CA4 | PASSOU | `TwoFactorIsolationIT.accountsStaySeparate`: cada perfil (COORDINATOR, ADMINISTRATOR, TEACHER, STUDENT, GUARDIAN) liga as duas etapas, chama a rota com 200 e 10 códigos, seus hashes mudam por inteiro, e os hashes da organização B e dos perfis anteriores ficam iguais; no fim os de B conferem com o início |

Comandos: `mvnw verify -Dit.test='TwoFactor*IT' -Dtest=NONE` -> 17 testes, 0 falhas, BUILD SUCCESS (Isolation 3, Service 3, Login 6, Audit 1, Gate 4). Regressão completa não repetida: nenhuma mudança de produção desde a rodada 1 (862 back-end, 1915 front).
Instáveis: nenhum.
Achados: nenhum obrigatório. Sugestão da rodada 1 (`required` em pt-BR.json) sem ação.

## Entrega

- **O que muda:** a rota de regenerar códigos de reserva passa a aceitar chamada sem corpo, sem senha e sem código de dois fatores; os 10 códigos antigos saem da tabela e os novos ficam. A janela da Minha conta abre já com os códigos, sem formulário nem botão "Gerar", como o canvas (decisão 3 do dono). Textos de senha e código saíram do pt-BR.
- **Especificação:** RF-ACE-04 e RN-ACE-21 deixam explícito que gerar códigos de reserva não é ação crítica (D2).
- **Verificação:** rodada 2 OK. 17 testes `TwoFactor*IT` passam (incluindo o isolamento por perfil corrigido na rodada 1); `OpenApiContractTest,ContractRoutesTest` passam; vitest dos arquivos tocados passa; typecheck e lint limpos; `test:cov` 1915 testes e `mvnw verify` 862 testes, BUILD SUCCESS. As 4 referências `conta-codigos-reserva-*` foram regeneradas pelo teste visual com MSW.
- **Decisões:** D1 a D4 do arquivo (sem passo intermediário, 429 fora desta rota, corpo ignorado de cliente antigo).
- **Limitações:** sem teste novo de login com código antigo (CA2, coberto pelo hash removido); `e2e` e a comparação `visual:canvas` não rodaram localmente, ficam para o CI.
