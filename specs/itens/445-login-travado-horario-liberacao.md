# 445 — Login travado devolve o horário de liberação (429)

Origem: issue #445 (Sprint 10.5 · Identidade visual); RN-ACE-10 (`docs/especificacao/02-contas-e-acesso.md`), relatório M37 (LoginTravadoM, `LoginTravado`), plano F1-09. Branch: `item/445-login-travado-horario-liberacao`.

## 1. Objetivo

Quando o e-mail (ou o IP) está travado por excesso de tentativas, o login responde 429 sem dizer até quando, e a tela mostra uma faixa vermelha genérica com "Entrar" ainda ativo. O item faz o 429 do login trazer o instante de liberação (`retryAt`) e a tela mostrar o aviso do canvas: cadeado, "Tente de novo às 21h47 ou crie uma nova senha", "Entrar" desabilitado até o horário e o botão "Criar nova senha" (leva a `/esqueci-senha`).

Fora do escopo: 429 do segundo passo (código de duas etapas), da troca de senha e do pedido de link de nova senha (continuam sem horário); o limite e a duração da trava (RN-ACE-10 não muda); o visual além do aviso.

## 2. Requisitos

- R1: O 429 `TOO_MANY_REQUESTS` de `POST /api/v1/auth/login` (trava de e-mail ou de IP) traz `retryAt`, o instante em que a trava acaba, em ISO-8601 UTC (D1, D3).
- R2: `retryAt` depende só das tentativas registradas, igual para e-mail existente ou não; a resposta não revela se o e-mail existe (RN-ACE-11).
- R3: No 429 com `retryAt`, a tela de login mostra o aviso do canvas com o horário local ("às 21h47") e o botão "Criar nova senha", que leva a `/esqueci-senha`.
- R4: Enquanto `retryAt` estiver no futuro, "Entrar" fica desabilitado; ao passar o horário volta a ficar ativo e o aviso de trava sai.
- R5: 429 sem `retryAt` (ou com valor inválido) mostra o texto genérico de hoje e "Entrar" ativo.
- R6: Contrato (`docs/openapi.yaml`) e MSW de acordo: `limite@exemplo.com` devolve `retryAt` (D2).
- R7: Textos em pt-BR, cores só pelos tokens de aviso (`Banner` tom `warning`); a tela segue passando no axe nos dois temas e com os quatro estados.

## 3. Critérios de aceite

- CA1 (R1): dado um e-mail com 5 senhas erradas seguidas, quando faz login de novo, então 429 `TOO_MANY_REQUESTS` com campo `retryAt` igual ao instante da 5ª falha + 15 minutos.
- CA2 (R1): dado um IP com 20 falhas na janela de 15 minutos, quando faz login com e-mail sem trava, então 429 com `retryAt` no futuro, no máximo 15 minutos adiante.
- CA3 (R2): dado e-mail existente e e-mail inexistente com o mesmo histórico de tentativas, quando fazem login, então os dois recebem 429 com a mesma forma e o mesmo `retryAt`.
- CA4 (R3): dado 429 com `retryAt` futuro, quando a tela recebe, então aparece o aviso "Muitas tentativas. Por segurança, este e-mail fica travado por 15 minutos. Tente de novo às HHhMM ou crie uma nova senha." e o botão "Criar nova senha" com destino `/esqueci-senha`.
- CA5 (R4): dado o aviso com `retryAt` futuro, quando o horário passa (relógio simulado), então "Entrar" volta a ficar ativo e o aviso some; antes disso "Entrar" está desabilitado.
- CA6 (R5): dado 429 sem `retryAt`, quando a tela recebe, então mostra "Muitas tentativas. Espere alguns minutos e tente de novo.", "Entrar" ativo e sem "Criar nova senha".
- CA7 (R6): dado o MSW, quando faz login com `limite@exemplo.com`, então 429 com `retryAt` futuro; com senha `limite` e outro e-mail, 429 sem `retryAt`.
- CA8 (R7): dada a tela de login travada a 390 e 1440 px, nos dois temas, então o axe não acusa violação e o aviso usa o tom de aviso.

## 4. Plano técnico

- Dados: não muda (`login_attempt` já guarda hora e resultado).
- API: `POST /auth/login`, 429: o corpo `Error` ganha em `fields` o item `{field: "retryAt", message: "<instante ISO-8601 UTC>"}`, o mesmo mecanismo do `reason` do convite (D1). Não muda o código HTTP nem o código de erro. Rota pública, sem permissão nova.
- Interface: `LoginPage` guarda `lockedUntil` (instante); aviso `Banner` tom `warning` com texto com o horário (`formatTime`); botão secundário "Criar nova senha" (`Link` para `/esqueci-senha`); `setTimeout` até `lockedUntil` limpa o aviso e reabilita "Entrar" (limpa ao desmontar). Os quatro estados da tela não mudam.
- Segurança: o horário sai só das tentativas, igual para qualquer e-mail (R2); nenhum dado novo de conta. Sem auditoria nova (nada altera dado).
- Arquivos (vistos):
  - `backend/src/main/java/br/com/ctcore/identity/LoginAttempts.java`: novo `Instant releaseAt(Status status, String email, String ip)` (e-mail: última falha dentre as que travam + janela; IP: a `ipMaxFailures`-ésima falha mais recente + janela); `emailLocked` fica como está.
  - `backend/src/main/java/br/com/ctcore/identity/AuthService.java` (~linhas 93-100, login com senha): passar o `retryAt` (ISO) como `detail` de `IdentityException(TOO_MANY_REQUESTS, ...)`.
  - `backend/src/main/java/br/com/ctcore/api/error/ApiExceptionHandler.java` (`identity`): se o código é `TOO_MANY_REQUESTS` e há `detail`, acrescentar `Field("retryAt", detail)`.
  - `backend/src/test/java/br/com/ctcore/identity/AuthRoutesIT.java` (~linhas 170-195) e `LoginLockoutTest.java`: casos de CA1 a CA3.
  - `docs/openapi.yaml` (`/auth/login`, 429, ~linha 40): descrever `fields[retryAt]`.
  - `docs/especificacao/02-contas-e-acesso.md` (RN-ACE-10): uma frase dizendo que o 429 do login informa o horário de liberação.
  - `frontend/src/mocks/handlers.ts` (`/auth/login`, ~linha 1834): `limite@exemplo.com` devolve 429 com `fields: [{field: "retryAt", ...}]` (agora + 15 min); a senha `limite` com outro e-mail segue sem `retryAt` (D2). Conferir se o helper `error(...)` aceita `fields`.
  - `frontend/src/features/auth/LoginPage.tsx`: ler `err.fields` no ramo 429.
  - `frontend/src/i18n/pt-BR.json` (`auth.errors`, novo `auth.createPassword`): texto `locked` com `{{time}}`.
  - `frontend/src/features/auth/LoginPage.test.tsx` (CA4 existente, ~linha 73, segue valendo para o caso sem `retryAt`): testes novos de CA4 a CA6, CA8; CA7 em teste do MSW (arquivo a criar, no padrão de `frontend/src/mocks/twofactor.msw.test.ts`).
  - `frontend/visual/casos.ts` (`login-travado`, linha 78): usar o e-mail `limite@exemplo.com`; regerar só a referência desse caso e conferir `npm run visual:canvas` (`LoginTravado`, 1440 e 390 px, pt-BR); divergência restante entra como `limiteCanvas` com motivo e a issue que a remove.
- Reuso: `formatTime` (`frontend/src/lib/format.ts`), `Banner`, `Button`, `Link`, `ApiError.fields`, `IdentityException.detail`, `LoginAttempts`, helpers do `AuthRoutesIT`.
- Riscos: relógio do navegador diferente do servidor (o aviso some cedo ou tarde; se o servidor ainda travar, volta o 429 com novo horário); o caso visual `login-travado` muda de texto e de referência.

## 5. Tarefas

- [x] T1: Back-end: `LoginAttempts.releaseAt`, `AuthService` e `ApiExceptionHandler` levam `retryAt`; testes de CA1 a CA3 em `AuthRoutesIT` e `LoginLockoutTest` — cobre CA1, CA2, CA3 — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test='AuthRoutesIT' -Dtest='LoginLockoutTest' -Dsurefire.failIfNoSpecifiedTests=false`
- [x] T2: Contrato, especificação e MSW: `docs/openapi.yaml`, RN-ACE-10 e handler de login com `retryAt` para `limite@exemplo.com` — cobre CA7 — prova: `npm test -- login.msw` (de dentro de `frontend/`, arquivo a criar) e `./backend/mvnw -f backend/pom.xml test -Dtest='OpenApiContractTest,ContractRoutesTest'`
- [x] T3: Front: `LoginPage` com aviso, horário, "Entrar" desabilitado até `retryAt`, "Criar nova senha"; textos pt-BR; testes que falham sem a correção — cobre CA4, CA5, CA6, CA8 — prova: `npm test -- LoginPage` (de dentro de `frontend/`)
- [x] T4: Visual: ajustar o caso `login-travado`, regerar só a referência dele e rodar a comparação com o canvas — não cobre critério: regra do AGENTS para tela alterada (capturas em `.capturas/445/`) — prova: `npm run test:visual:docker -- --update-snapshots -g "login-travado"` e `npm run visual:canvas` (de dentro de `frontend/`)

## 6. Decisões

- D1 (muda o comportamento): `retryAt` vai em `fields` do corpo de erro, como o `reason` do convite, em ISO-8601 UTC, em vez de campo novo no tipo `Error` (que mexeria em todos os erros da API). Alternativa: campo `retryAt` no nível do `Error`; descartada pelo alcance.
- D2: no MSW o gatilho do horário é o e-mail `limite@exemplo.com` (como pede a issue); a senha `limite` com outro e-mail continua devolvendo 429 sem horário, porque testes e o caso visual atuais dependem dela e ela serve de prova do caso genérico.
- D3: o horário é o instante em que a trava acaba, não "agora + 15 min"; no IP é quando a falha mais antiga que ainda conta sai da janela (a partir daí o IP volta abaixo do limite).
- D4: só o login com senha devolve `retryAt` (código de duas etapas, troca de senha e pedido de link ficam como estão), porque a issue trata da tela de login.
- D5: horário mostrado com `formatTime` (fuso padrão do sistema, formato "21h47" do canvas), não o fuso do navegador, para casar com o resto do sistema.
- Suposição: o texto do aviso com "15 minutos" fixo segue a RN-ACE-10; se a duração virar configurável, o texto deve passar a vir do contrato.

## Construção

- T1 — arquivos: `LoginAttempts.java` (`releaseAt`), `AuthService.java`, `ApiExceptionHandler.java`, `AuthRoutesIT.java` (`lockoutReleaseTime`) — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test='AuthRoutesIT' -Dtest='LoginLockoutTest' -Dsurefire.failIfNoSpecifiedTests=false` → LoginLockoutTest 3 ok, AuthRoutesIT 17 ok, BUILD SUCCESS
- T2 — arquivos: `docs/openapi.yaml`, `docs/especificacao/02-contas-e-acesso.md`, `frontend/src/mocks/handlers.ts`, `frontend/src/mocks/login.msw.test.ts` (novo) — prova: `npm test -- login.msw` → 2 ok; `OpenApiContractTest,ContractRoutesTest` → 7 ok
- T3 — arquivos: `LoginPage.tsx`, `pt-BR.json` (`auth.createPassword`, `auth.errors.locked`), `LoginPage.test.tsx` (CA4/5/6/8) — prova: `npm test -- LoginPage login.msw` → 21 ok; lint e typecheck limpos
- T4 — arquivos: `frontend/visual/casos.ts`, 4 referências `login-travado-*.png`; capturas em `.capturas/445/` — prova: `npm run test:visual:docker -- --update-snapshots -g "login-travado"` → 4 ok; `npm run visual:canvas` → 95 comparadas, 0 reprovadas
Regressão: backend só os testes do item e de contrato (não rodei `verify` completo); front `npm run test:cov` → 193 arquivos, 1920 testes ok; lint e typecheck ok. `e2e` e `build` não rodados (CI).
Desvios: tom do Banner é `warn` (o plano dizia `warning`; é o nome real do token). A captura 390 px e o canvas escuro não foram salvos em `.capturas/445/` (só 1440 claro). Divergências restantes com o canvas, de outras issues da 10.5 (identidade visual): ícone de cadeado (o Banner usa triângulo), "Muitas tentativas." em negrito, botão "Criar nova senha" largo abaixo de "Entrar" (hoje dentro do aviso), subtítulo e nota "A trava vale só para este e-mail", logo e textos do painel; não registrei `limiteCanvas` novo porque `visual:canvas` não reprovou.

## Verificação

### Rodada 1 — NAO_CONFERIDO — 2026-10-31, estado a18963e59284e998dfb8587662c76f24df16a270

| Critério | Status | Evidência |
|---|---|---|
| CA1 | PASSOU | `AuthRoutesIT.lockoutReleaseTime` (17 ok no arquivo): `retryAt` = 5ª falha + 15 min |
| CA2 | PASSOU | mesmo teste: `retryAt` do IP no futuro, <= 15 min, = 1ª falha da janela + 15 min |
| CA3 | PASSOU | mesmo teste: `ana@x.com` e `ghost@x.com` com a mesma forma e `retryAt` |
| CA4 | PASSOU | `LoginPage.test.tsx` (21 ok com `login.msw`); aviso, "Criar nova senha" -> `/esqueci-senha` visíveis na captura 1440 |
| CA5 | PASSOU | `LoginPage.test.tsx`, relógio simulado |
| CA6 | PASSOU | `LoginPage.test.tsx`, 429 sem `retryAt` |
| CA7 | PASSOU | `login.msw.test.ts` |
| CA8 | NÃO CONFERIDO | axe nos testes de LoginPage passa, mas a comparação com o canvas a 390 px não tem imagem (ver achado) |

Comandos:
- `npx vitest run LoginPage.test.tsx login.msw.test.ts --maxWorkers=2` -> 21 ok; `npm run lint` e `npm run typecheck` limpos
- `./backend/mvnw verify -Dit.test=AuthRoutesIT -Dtest='LoginLockoutTest,OpenApiContractTest,ContractRoutesTest'` -> LoginLockoutTest 3, OpenApiContractTest 4, ContractRoutesTest 3, AuthRoutesIT 17, BUILD SUCCESS
- Não rodei `verify` completo, `test:cov` completo, `e2e`, `build` (o builder registrou `test:cov` 1920 ok; resto fica para o CI)

Achados:
- obrigatório (NAO_CONFERIDO, não é defeito do código), `.capturas/445/` — esperado (AGENTS.md, regra de canvas): `<tela>-<largura>-sistema.png` e `-canvas.png` em 1440 e 390 px; observado: só `login-travado-1440-{sistema,canvas}.png` e `login-travado-390-sistema.png`; falta `login-travado-390-canvas.png`, e `docs/design/prints/lado-a-lado/` só tem 1440 claro; corrigir: gerar a captura 390 do canvas (`npm run visual:canvas`) e salvá-la, para o verificador comparar. O canvas só existe em tema claro (único par em lado-a-lado), então o escuro não é exigido.
- sugestão, código, `frontend/visual/casos.ts` — divergências vistas no 1440 (cadeado vs triângulo, "Muitas tentativas." em negrito, "Criar nova senha" largo fora do aviso, subtítulo, nota "A trava vale só para este e-mail", logo/painel) não estão como `limiteCanvas` com motivo e issue (Autonomia 4); registrar, e listá-las na descrição do PR.
- sugestão, código, `LoginAttempts.releaseAt` — `.single()` de `max(...)` e do `offset` assume que o estado vem de `check` na mesma hora; correto hoje, mas corrida com limpeza de tentativas lançaria exceção (500).

Instáveis: nenhum.

## Construção (continuação)

### Correção da rodada 1
- Captura 390 px do canvas — arquivos: `frontend/visual/casos.ts` (`canvasCelular: "LoginTravadoM"`), `.capturas/445/login-travado-390-canvas.png` — prova: `npm run visual:canvas` → 96 comparadas, 0 reprovadas; `login-travado` 1440 = 3,2% e 390 = 13,5% (limite 30%). Escuro: o canvas só existe em tema claro.
- `LoginAttempts.releaseAt` sem `.single()` (corrida com limpeza) — arquivos: `LoginAttempts.java` (`optional()`, sem linha cai em agora + janela) — prova: `verify -Dit.test=AuthRoutesIT -Dtest=LoginLockoutTest` → 3 + 17 ok, BUILD SUCCESS
- Sugestão `limiteCanvas`: não aplicada; a comparação passa com o limite padrão (30%), então não há exceção a registrar. As divergências visuais (cadeado, negrito, botão largo, subtítulo, nota, painel) seguem listadas em Desvios para a descrição do PR.
Regressão: sem mudança de código além de `releaseAt`; testes do item rodados acima.

### Rodada 2 — OK — 2026-10-31, estado 57f75a6ea2b1a9b25f1dd76600dc2feac0453352

| Critério | Status | Evidência |
|---|---|---|
| CA1 a CA3 | PASSOU | `AuthRoutesIT` 17 ok e `LoginLockoutTest` 3 ok após a mudança em `releaseAt` |
| CA4 a CA7 | PASSOU (mantido da rodada 1, reconferido) | `LoginPage.test.tsx` + `login.msw.test.ts` 21 ok |
| CA8 | PASSOU | axe nos testes de LoginPage; `.capturas/445/` agora tem 1440 e 390 (sistema e canvas); abri as de 390: aviso em tom de aviso, "Entrar" desabilitado, "Criar nova senha" presente; divergências visuais já listadas em Desvios (cadeado, negrito, botão largo, logo), dentro do limite da comparação (390 = 13,5%) |

Comandos:
- `./backend/mvnw verify -Dit.test=AuthRoutesIT -Dtest='LoginLockoutTest,OpenApiContractTest,ContractRoutesTest'` -> 10 + 17 ok, BUILD SUCCESS
- `npx vitest run LoginPage.test.tsx login.msw.test.ts --maxWorkers=2` -> 21 ok
- Não rodei `verify` completo, `e2e`, `build` (CI, Autonomia 2)

Achados: achado obrigatório da rodada 1 (captura 390 do canvas) resolvido.
- sugestão: listar as divergências visuais com o canvas na descrição do PR.

Instáveis: nenhum.

## Entrega

- O que muda: o 429 de `POST /auth/login` (trava de e-mail ou de IP) traz `retryAt` em `fields` (ISO-8601 UTC), calculado em `LoginAttempts.releaseAt`. A tela de login mostra o aviso com o horário local, deixa "Entrar" desabilitado até lá e oferece "Criar nova senha" (`/esqueci-senha`). Contrato (`docs/openapi.yaml`), RN-ACE-10 e MSW (`limite@exemplo.com`) acompanham.
- Verificação: rodada 2 OK (estado 57f75a6). `LoginLockoutTest` 3 ok, `AuthRoutesIT` 17 ok, `OpenApiContractTest` e `ContractRoutesTest` ok; `LoginPage` + `login.msw` 21 ok; `test:cov` 1920 ok; lint e typecheck limpos; `visual:canvas` sem reprovação.
- Decisões: D1 a D5 do item (`fields` em vez de campo novo; MSW só com o e-mail `limite@exemplo.com`; horário é o fim da trava; só o login com senha; `formatTime` no fuso do sistema). O tom do aviso é `warn`, que é o nome real do token.
- Limitações: `verify` completo, `e2e` e `build` ficam para o CI. Divergências com o canvas que ficam para outras issues da 10.5 (identidade visual): cadeado (o Banner usa triângulo), "Muitas tentativas." em negrito, botão "Criar nova senha" largo abaixo de "Entrar" (hoje dentro do aviso), subtítulo e nota "A trava vale só para este e-mail", logo e painel. Sem `limiteCanvas` novo, porque `visual:canvas` não reprovou.
- Capturas: `.capturas/445/` tem 1440 e 390 px, sistema e canvas, em pt-BR; o canvas só existe no tema claro.
