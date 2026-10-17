# Revisão da Sprint 02 · Contas, login, convites e sessões

### Rodada 1 — AJUSTAR — 2026-10-17, estado 07a2fe04e091cdd39af0677ed8d8fd63a547bee8

#### Resumo e veredito

A Sprint 02 entregou a identidade inteira: conta única, vínculos, convites, sessões no servidor, bloqueio de login, recuperação de senha e troca de senha (back-end, V6 a V9, 9 rotas em `/api/v1/auth`), o serviço de e-mail com os quatro e-mails de conta e as telas de login, convite, "esqueci minha senha", nova senha, escolha de organização e área e área indisponível (com MSW). As 9 issues do marco estão fechadas, cada uma por um PR de squash com `ci-gate` verde e rodada `OK`. O clone limpo passou em tudo, inclusive 360 px e axe com contraste em navegador real nas 6 telas públicas.

Veredito: **AJUSTAR**, sem BLOQUEIA. Antes do PR `dev`→`main` devem entrar três ajustes pequenos: códigos de regra nos nomes dos testes do back-end (recorrência do R00-5 e do R01-3), a especificação atualizada com as decisões que mudaram regra (`AGENTS.md`: "mudou uma regra, muda primeiro a especificação") e o limite por IP no pedido de link de nova senha (RNF-10), que o contrato e a decisão D6 do item #22 dão como existente e o servidor não tem. O dono precisa decidir três pontos (D1 a D3): o critério do marco que pede o ponta a ponta da troca de senha, o desafio invisível nomeado no front e a regra de sessão de 12 horas.

#### Estado revisado

| | |
|---|---|
| `dev` | `07a2fe04e091cdd39af0677ed8d8fd63a547bee8` (igual a `origin/dev`, pasta limpa) |
| `main` | `17e08541f69d33a535f4cb939821b3dc88fe32ab` (`origin/main`, Sprint 01; não existe branch `main` local) |
| Ferramentas | Java 21.0.12, Node 24.21.0, Docker 29.8.1, data 2026-10-17 |
| Diff | `git diff origin/main...dev --stat`: 122 arquivos, +8165 −38 (back-end `identity` e `mail`, V6 a V9, OpenAPI, front `features/auth`, MSW, e2e, seed 03, 9 arquivos de item) |
| PRs da sprint | #234 (#18), #235 (#19), #236 (#20), #237 (#21), #238 (#22), #239 (#23), #240 (#24), #241 (#25), #242 (#231); fora de item: #233 `docs: libera a Sprint 02 no backlog`, #232 `docs: item #231 no backlog` |

Pré-condições: `dev` limpa e igual a `origin/dev`; nenhuma branch `item/*` local ou remota; nenhum PR aberto contra `dev`; `gh auth` válido; Docker ativo. Marco com 9 issues fechadas, nenhuma com a etiqueta `depende de você`; no `specs/backlog.md` as 9 linhas estão "feito". Nenhum PR da sprint fechou issue de outro marco e nenhum de outra sprint fechou issue desta. Sem item da Sprint 03 em `dev` (parada de sprint respeitada).

#### Trilha de processo

| Item | PR | Verificação | Entrega | Observação |
|---|---|---|---|---|
| #18 Conta, vínculo, sessão e convite | #234, squash (1 commit), `ci-gate` SUCCESS, `Closes #18` | Rodada 1 CORRIGIR_ESPEC, Rodada 2 OK | sim | a correção alterou `docs/plano-de-sprints.md` (ver R02-7) |
| #19 E-mail e e-mails de conta | #235, squash, `ci-gate` SUCCESS, `Closes #19` | Rodada 1 OK | sim | |
| #20 Tela de login | #236, squash, `ci-gate` SUCCESS, `Closes #20` | Rodada 1 OK | sim | |
| #21 Criar senha pelo convite | #237, squash, `ci-gate` SUCCESS, **sem `Closes`** | Rodada 1 OK | sim | issue fechada à mão às 21:09:03Z, depois do merge do #242 (R02-8) |
| #22 Recuperação de senha | #238, squash (2 commits no PR), `ci-gate` SUCCESS, `Closes #22` | Rodada 1 OK | sim | seções do arquivo sem número ("## Verificação", "## Entrega"), só forma |
| #23 Escolha de organização e área | #239, squash, `ci-gate` SUCCESS, `Closes #23` | Rodada 1 OK | sim | idem |
| #24 Último acesso | #240, squash, `ci-gate` SUCCESS, `Closes #24` | Rodada 1 OK | sim | |
| #25 Área indisponível | #241, squash, `ci-gate` SUCCESS, `Closes #25` | Rodada 1 OK | sim | |
| #231 Timeouts nos jobs do CI | #242, squash (2 commits no PR), `ci-gate` SUCCESS, `Closes #231` | Rodada 1 NAO_CONFERIDO, Rodada 2 OK | sim | |

Conferido com `gh pr view` (base `dev`, `ci-gate`, número de commits), `gh pr list --state merged` (datas e `Closes`), `grep -n '^## \|^### Rodada'` e `grep -c '- \[ \]'` nos 9 arquivos de item: seis seções, "Construção", ao menos uma rodada `OK`, "Entrega", 0 tarefas abertas. Nenhum item passou de duas rodadas. Nenhum merge antes da verificação (rodada `OK` em todos). Título de PR igual a `#N: <título da issue>` nos 9. Os 9 PRs entraram em sequência, um por vez, em 17/10 das 17:36Z às 21:08Z.

#### "Pronto quando"

**Do marco** (`gh api repos/{owner}/{repo}/milestones/3`)

| Bullet | Status | Evidência |
|---|---|---|
| A mensagem de erro do login é idêntica para e-mail inexistente e senha errada | PASSOU | `AuthRoutesIT.identicalLoginFailures` compara status, código e texto de senha errada, e-mail inexistente, convite não aceito e conta desativada (`401\|INVALID_CREDENTIALS\|E-mail ou senha incorretos.`). Prova minha no back-end rodando do clone: `POST /api/v1/auth/login` com `coordenador@`, `desativado@`, `convidado@ficticia-1.example` (seed) e e-mail inexistente devolveram o mesmo corpo (menos `traceId`) e 401 |
| Trocar a senha derruba as outras sessões no mesmo minuto (teste ponta a ponta) | PASSOU no servidor; ponta a ponta de tela NÃO PASSOU, transferido para a #36 (ver R02-7 e D1) | `AuthRoutesIT.changePassword` (duas sessões em MockMvc: a outra passa de 200 para 401 logo após a troca, a atual continua) e `passwordReset`; `AuthIsolationIT.passwordChangeDoesNotTouchOthers`. Não há tela de troca de senha logado nesta sprint (é a #36, Sprint 03). A redefinição por link encerra as sessões e há e2e Playwright dela (`recuperar-senha.spec.ts`, contra MSW) que não prova o encerramento |
| Convite vencido, usado ou cancelado mostra a explicação e o caminho para pedir outro | PASSOU | `pt-BR.json` linhas 81 a 87: "Este convite venceu. Os convites valem por 7 dias.", "já foi usado", "cancelado por quem convidou você" e "Peça um novo convite a quem convidou você."; e2e `convite.spec.ts` (convite vencido); `InvitePage.test.tsx`. O "caminho" é texto (D7 do #21); a tela de reenvio é a RF-ACE-06 (Sprint 03). Link de senha inválido tem o botão "Pedir novo link" |
| Fluxos ponta a ponta "convite e criação de senha" e "troca de senha encerrando sessões" na suíte | PASSOU o primeiro; o segundo NÃO PASSOU (transferido, D1) | `npm run e2e` no clone: 7 de 7 `@critical` em 19,2 s, incluindo `@critical Convite e criação de senha`. Todos rodam contra MSW, sem back-end (`playwright.config.ts`, D5 da Sprint 00). "Troca de senha encerrando as outras sessões" foi movida para a Sprint 03 no plano (`docs/plano-de-sprints.md` linha 108) e consta no "Pronto quando" das issues #18 e #36 |
| Todos os itens do "Pronto quando" geral valem para cada item | PASSOU, com os ajustes R02-1, R02-3, R02-4 e R02-6 | tabela abaixo |

**De todo item** (`docs/plano-de-sprints.md`, "Pronto quando (vale para todo item)")

| Regra | Status | Evidência ou "não se aplica nesta sprint: motivo" |
|---|---|---|
| Código de regra citado tem ao menos um teste | NÃO PASSOU na rastreabilidade | Varredura dos códigos das 9 issues contra `backend/src/test`, `frontend/src`, `frontend/e2e`: sem nenhuma ocorrência RN-ACE-02, 04, 05, 06, 07, 09, 10, 12, 13, 14, 19, 20, 22, RF-ACE-07, RNF-14, RNF-28, RNF-35, RNF-37. Os testes existem e citam os critérios (`CA1` a `CA18`), não o código da regra; no back-end a única citação é `RN-ACE-01` em `SeedDataIT`. Só `RN-ACE-10` (login), `13`, `14`, `19`, `20` e `22` são de segurança de conta; nenhum é regra crítica do `AGENTS.md` (cobrança, pagamento, assinatura, inativação, isolamento), então AJUSTAR (R02-1) |
| Cobertura mínima de 80%; 100% nas regras críticas | PASSOU | Back-end: JaCoCo 1271 de 1322 linhas (96,14%), "All coverage checks have been met". Pacote `isolation` 64/64 (100%); `identity` 603/630 (95,7%); `mail` 89/90. Front: linhas 98,84%, instruções 97%. Pacotes `billing`, `payment`, `subscription`, `deactivation`: não existem ainda |
| Rota nova com teste de isolamento entre organizações e entre perfis | PASSOU em parte (R02-4) | `AuthIsolationIT` (6 testes: vínculo de B dá 404, perfil escolhe o próprio vínculo, RLS com escopo de B, recuperação e último acesso de A não tocam B). Sem teste de isolamento próprio: `GET/POST /auth/invitations/{code}[/accept]`, `POST /auth/logout`, `POST /auth/password-reset` (pedido). Nada impede uma rota nova sem teste (A4 da Sprint 01 não foi feito) |
| OpenAPI, implementação e MSW batem | PASSOU em parte (R02-6) | `OpenApiContractTest` e `ContractRoutesTest` passaram no `verify`; as 9 rotas do controller estão no `docs/openapi.yaml`. MSW cobre 7 das 9 (faltam `/auth/logout` e `/auth/password`, sem tela que as use); `msw.test.ts` só confere handler contra OpenAPI, não o inverso |
| Auditoria em toda escrita | PASSOU com ressalva (R02-9) | `InviteIT`, `AuthRoutesIT.changePassword`/`passwordReset` conferem linhas de `audit_log` sem senha, token ou código. Login, logout, último acesso e `account_device` não auditam (D5 do #18: a tentativa de acesso é o registro); conta sem vínculo ativo não gera linha (D5) |
| Quatro estados, 360 px e axe nos dois temas | PASSOU para as telas públicas; NÃO CONFERIDO para modal e `/sem-acesso` em navegador | Navegador real (Chromium via Playwright do clone, largura 360, `axe-core` do clone com `wcag2a/aa/21aa` incluindo contraste), tema claro e escuro, em `/entrar`, `/esqueci-senha`, `/convite/valido`, `/convite/vencido`, `/redefinir-senha/valido`, `/redefinir-senha/vencido`: rolagem horizontal 0 px e 0 violações nas 12 combinações. O modal de escolha e `/sem-acesso` exigem sessão e ficam só com os testes jsdom (`window.innerWidth = 360`, sem contraste) |
| Textos nos arquivos de tradução | PASSOU | `npm run lint` (regra de texto literal) verde no clone; `pt-BR.json` +100 linhas |
| Recurso novo nasce desligado por organização | não se aplica nesta sprint: nenhum recurso liberado aos poucos | |
| Notas de versão | não se aplica nesta sprint: não há academia usando; o piloto é a Sprint 24 e não existe arquivo de notas de versão | |
| Fluxos críticos da sprint (tabela do plano) | PASSOU para "Convite e criação de senha" (e2e na suíte); "Troca de senha encerrando as outras sessões" foi movido para a Sprint 03 (D1) | `frontend/e2e/convite.spec.ts` |

#### Clone limpo

Clone novo de `origin/dev` no scratchpad (caminho com UUID), seguindo o README da raiz e a seção "Comandos" do `AGENTS.md`. O projeto Compose foi `ctcorerev` (via `COMPOSE_PROJECT_NAME`), por causa do R01-10 abaixo.

| Passo | Comando | Resultado real |
|---|---|---|
| ambiente | `docker compose up -d --wait` (em `infra/`) | postgres, storage e mailbox saudáveis em 32 s |
| papel do banco | `bash infra/scripts/prepare-runtime-db.sh` | `ctcore_app` criado |
| seed (2x) | `bash infra/scripts/seed.sh` ×2 (depois de subir o back-end uma vez) | 01, 02 e 03 aplicados nas duas vezes; contagens iguais: 3 organizações, 5 contas, 6 vínculos |
| back-end | `./backend/mvnw -f backend/pom.xml -B verify` | BUILD SUCCESS em 344 s: 90 testes rápidos e 95 de integração, 0 falhas; JaCoCo 96,14% de linhas e "All coverage checks have been met" |
| front-end | `npm ci`, `lint`, `typecheck`, `test:cov`, `build`, `e2e` | `npm ci` 30 s (0 vulnerabilidades); lint 17 s; typecheck 27 s; 227 testes em 33 arquivos, linhas 98,84%; build 18 s; e2e 7 de 7 em 23 s |
| back-end vivo | `java -jar` com perfil `local` | `/actuator/health/readiness` UP na porta 8081; `/api/v1/auth/session` sem cookie 401; login sem token CSRF 403; cabeçalhos `X-Content-Type-Options` e `Cache-Control: no-store` presentes |
| imagens | `docker build -t ctcore-backend backend` e `ctcore-frontend frontend` | back-end levou 8 min 8 s (sem cache); usuário `uid=10001(ctcore)`; readiness UP; front-end 200 em `/` e `/entrar`, 404 em `/api/v1/auth/session` |
| recomeço | `docker compose down -v`, subir, contar tabelas, `down -v` | 0 tabelas no banco novo; sem estado escondido |

Comparação com a linha de base (Sprint 01: back-end 79 rápidos + 48 de integração, 95,96%; front 121 testes, 97,83%): melhorou (185 testes no back-end, 96,14%; 227 no front, 98,84%). Nada piorou.

Passos do README que não bastaram: nenhum para chegar ao resultado. Atenção: o compose fixa `name: ctcore`; subir em um segundo clone usa os volumes `ctcore_postgres-data` e `ctcore_storage-data` do dono, e o `down -v` do README os apagaria (R01-10, mantido). Subi primeiro nesse projeto por engano (só `up` e `prepare-runtime-db.sh`, idempotente), derrubei sem `-v` e refiz tudo em `ctcorerev`. Os volumes do dono continuam presentes e a máquina voltou à lista de imagens de antes (diff vazio).

Limpeza: contêineres `rev-be` e `rev-fe`, imagens `ctcore-backend` e `ctcore-frontend` (não existiam antes), projeto `ctcorerev` com volumes e rede removidos; processos `java` e `vite` encerrados. Ficou o diretório do clone no scratchpad.

#### CI e proteções

| Item | Estado real | Evidência |
|---|---|---|
| `ci-gate` exigido em `dev` | sim | `gh api repos/{owner}/{repo}/branches/dev/protection`: contexts `["ci-gate"]`, push forçado `false` (`enforce_admins` falso) |
| regra de `main` | `ci-gate` agora exigido; só merge; sem push forçado nem exclusão | ruleset 25019105 `protege-main` (ativo, `refs/heads/main`): `required_status_checks` com `ci-gate`, `pull_request` com `allowed_merge_methods: ["merge"]`, `non_fast_forward`, `deletion`. A D1 das revisões anteriores está resolvida |
| últimos runs de `publish.yml` em `dev` | verdes | `gh run list --branch dev`: Publish success em 07a2fe0, 6355795, fc774ae, a4f0547, e19bcd9, 1db2dc5, fcd749b, 593fe45 |
| `ci.yml` | verde nos 9 PRs | `ci-gate` SUCCESS em #234 a #242; jobs pulados só por regra de pasta |
| `permissions:` e pinagem | inalteradas | `ci.yml` com `contents: read`; o diff da sprint em `.github` só acrescenta `timeout-minutes` (8 linhas no `ci.yml`, 2 no `publish.yml`) |
| exceções de vulnerabilidade | 2, ambas com motivo e validade futura | `osv-scanner.toml`: GHSA-j9f9-w8pj-32f8 e GHSA-pc63-qcmh-9cmg, `ignoreUntil = 2027-01-15` |
| gitleaks | 1 exceção, de teste | `.gitleaksignore`: `LogOutputTest.java:generic-api-key:87` |
| segredos e variáveis do fluxo de publicação | não existem (esperado) | `gh secret list` e `gh variable list` vazios; `PUBLISH_*` só são exigidos na #192 (limitação aceita) |

#### Achados

**AJUSTAR (antes do PR para `main`)**

- **R02-1 · AJUSTAR · teste (rastreabilidade)** — local: `backend/src/test/java/br/com/ctcore/identity/*IT.java`, `mail/EmailServiceIT.java`, `frontend` (RF-ACE-07).
  - esperado: "todo código de regra citado na issue tem ao menos um teste"; o R00-5 e o R01-3 já pediram o código da regra no nome do teste para a varredura achá-lo.
  - observado: o back-end de identidade tem 6 arquivos de teste de integração e nenhuma citação de RN-ACE (salvo `RN-ACE-01` em `SeedDataIT`); os `@DisplayName` falam em `CA1`, `CA16`. RF-ACE-07 (#24) não aparece em nenhum teste.
  - evidência: `grep -rhoE "R[NF]{1,2}-[A-Z]+-[0-9]+" backend/src/test | sort | uniq -c` não lista RN-ACE-02, 04 a 07, 09, 10, 12 a 14, 19, 20, 22; os testes que cobrem cada uma estão mapeados nas tabelas de verificação dos itens.
  - efeito: a próxima revisão e quem audita perdem a ligação regra, teste; é a terceira sprint com o mesmo achado.
  - proposta: A1.

- **R02-2 · AJUSTAR · especificação** — local: `docs/especificacao/02-contas-e-acesso.md` (RN-ACE-10, RN-ACE-19, RN-ACE-22, RF-ACE-01 a 08) e `00-visao-geral.md`.
  - esperado: `AGENTS.md`: "Mudou uma regra, muda primeiro a especificação".
  - observado: `git diff origin/main...dev -- docs/especificacao` é vazio, mas os itens registram decisões que mudam o comportamento, sem passar pela especificação: #18 D3 (RN-ACE-19 diz "quando a área aberta é a gestão"; o código dá 12 horas a quem tem qualquer vínculo de gestão, em qualquer área) e D4 (limite por IP = 20 falhas em 15 min, a regra não dá número); #19 D4 e D5 (o primeiro acesso não gera "Novo acesso"; cidade "local não identificado"); #20 D2 e D3 (contato de suporte por variável de ambiente; contato da unidade só como componente); #21 D1 e D2; #22 D1 e D2 (a validade do link só aparece ao enviar a senha, 410); #23 D2 e D4 (área derivada do papel; modal que não fecha sem escolha); #25 D1 e D2.
  - evidência: `grep -n "^- D[0-9]* (muda" specs/itens/{18..25}-*.md`; leitura de `02-contas-e-acesso.md` linhas 33 a 49.
  - efeito: a especificação deixa de dizer o que o produto faz; a Sprint 03 (permissões, duas etapas, Minha conta) vai ler regra antiga.
  - proposta: A2 (com D3 do dono para a regra de sessão).

- **R02-3 · AJUSTAR · segurança** — local: `AuthService.requestReset` (`backend/src/main/java/br/com/ctcore/identity/AuthService.java`), `docs/openapi.yaml` linhas 94 a 105, item #22 D6.
  - esperado: RNF-10 ("formulários públicos com limite por IP e desafio invisível") e a D6 do item #22 ("o 429 do pedido ... o limite é por e-mail/IP (RN-ACE-10)").
  - observado: `requestReset` só chama `requireChallenge` (que na versão simulada sempre aprova) e não consulta `LoginAttempts`; o contrato de `POST /auth/password-reset` não tem 429 e nenhum teste de `AuthRoutesIT` o espera para o pedido. A tela trata um 429 que o servidor nunca devolve. O aceite de convite, ao contrário, consulta `attempts.check` (`InviteService` linha 244).
  - evidência: `grep -n "attempts.check" backend/src/main/java/br/com/ctcore/identity/*.java` acha só `AuthService.login` e `InviteService`; leitura do método.
  - efeito: hoje, com o desafio simulado, qualquer um dispara e-mails de recuperação sem limite para qualquer endereço (envio de lixo e custo quando o e-mail real entrar na Sprint 22), e o texto do item afirma o contrário.
  - proposta: A3.

**AJUSTAR (pode seguir na Sprint 03)**

- **R02-4 · AJUSTAR · teste** — local: `RouteIsolationModelIT`, `ContractRoutesTest`, `AuthIsolationIT` (R01-4 da Sprint 01, "mantido" e não resolvido).
  - esperado: marco da Sprint 01 e RNF-31: "para cada rota, a organização A não lê nem altera a B e cada perfil só alcança o que deve", verificado por máquina; a A4 da Sprint 01 pedia a guarda "no primeiro item da Sprint 02 que criar rota".
  - observado: a guarda não existe e nenhuma issue a prevê (busca por "guarda isolamento por rota" nas issues não acha); a Sprint 02 criou as 9 primeiras rotas e quatro delas (`GET /auth/invitations/{code}`, `POST .../accept`, `POST /auth/logout`, `POST /auth/password-reset`) não têm teste de isolamento de organização; a Sprint 03 cria dezenas de rotas de organização.
  - efeito: a primeira rota de organização sem teste só seria pega por revisão humana.
  - proposta: A4.

- **R02-5 · AJUSTAR · segurança (front-end e cabeçalhos)** — local: `frontend/nginx.conf` (só `Cache-Control`), `SecurityConfig` (`CookieCsrfTokenRepository.withHttpOnlyFalse()`).
  - esperado: telas com segredo na URL (`/convite/<código>`, `/redefinir-senha/<código>`) e a tela de login atrás de cabeçalhos mínimos.
  - observado: o nginx do front não envia CSP, `frame-ancestors`/`X-Frame-Options`, `Referrer-Policy` nem `X-Content-Type-Options`; o cookie `XSRF-TOKEN` sai só com `Path=/` (sem `Secure` nem `SameSite`, observado em `curl -sI`). O contêiner do back-end envia `nosniff` e `no-store`, e o HSTS está configurado em `SecurityConfig`.
  - evidência: `grep -n add_header frontend/nginx.conf`; `curl -sI localhost:8080/api/v1/auth/session` no back-end do clone.
  - efeito: a tela de login pode ser embutida em outro site (clickjacking) quando houver hospedagem; o código de convite só está protegido pela política padrão do navegador. Sem ambiente publicado hoje.
  - proposta: A5.

- **R02-6 · AJUSTAR · API (MSW)** — local: `frontend/src/mocks/handlers.ts`, `msw.test.ts`.
  - esperado: "O OpenAPI e os handlers MSW batem com a implementação".
  - observado: faltam handlers para `POST /auth/logout` e `POST /auth/password`; o teste só confere se cada handler existe no OpenAPI, não se cada rota do OpenAPI tem handler.
  - efeito: a primeira tela que usar sair ou trocar senha (#36) falha em teste e em `npm run dev` sem aviso do contrato.
  - proposta: A6 (junto da #36).

**DONO**

- **R02-7 · DONO · processo e marco** — local: descrição do marco "Sprint 02", `docs/plano-de-sprints.md` linha 108, PR #234, item #18 D10.
  - esperado: o critério "Trocar a senha derruba as outras sessões no mesmo minuto (teste ponta a ponta)" e o fluxo "troca de senha encerrando sessões" na suíte.
  - observado: o agente moveu o fluxo para a Sprint 03 editando o plano dentro do PR #234 e registrou a pendência ao dono (D10: "é decisão do dono"). As issues #18 e #36 hoje dizem que o ponta a ponta vai para a #36, mas a descrição do marco no GitHub continua pedindo os dois fluxos na Sprint 02. A prova existente é de servidor (MockMvc, duas sessões).
  - efeito: o critério do marco não é provado como está escrito; o plano e o marco divergem.
  - proposta: D1.

- **R02-10 · DONO · fornecedor** — local: `frontend/src/features/auth/Turnstile.tsx`, `ForgotPasswordPage.tsx`, `LoginPage.tsx`, `frontend/.env.example`.
  - esperado: `AGENTS.md`: "Conexões externas só pela porta e pela versão simulada até as Sprints 22 e 23; nenhuma regra cita fornecedor"; `NoVendorNamesTest` (RNF-39) garante isso só no back-end.
  - observado: o front carrega `https://challenges.cloudflare.com/turnstile/v0/api.js` quando `VITE_TURNSTILE_SITE_KEY` está definida; sem a chave (padrão) nada carrega. O nome do fornecedor está nos arquivos, na tela e na especificação (RN-ACE-10, RNF-10 citam "Turnstile").
  - efeito: é a única conexão externa direta do navegador antes da Sprint 22, desligada por padrão; nada quebra hoje.
  - proposta: D2.

**SUGESTÃO**

- **R02-8 · SUGESTÃO · processo** — a issue #21 foi fechada à mão (conta `DevDosAnjos`, 21:09:03Z, depois do merge do #242) porque o corpo do PR #237 não tem `Closes #21`; o PR e o commit têm o título correto. Padrão: `Closes #N` no corpo de todo PR de item.
- **R02-9 · SUGESTÃO · dados** — `account`, `account_session`, `password_reset`, `login_attempt`, `email_message` e `account_device` não têm RLS (V6, V7): são da conta, não da organização, e a busca de sessão é feita antes de saber a conta. O filtro no código é a única camada nelas; `account_link`, `account_invite` e `account_link_unit` têm RLS com os três escopos. Vale um comentário na migração dizendo isso e, quando possível, política por `app.account_id` em `password_reset` e `account_device`.
- **R02-11 · SUGESTÃO · segurança** — (a) o limite de login é "ler e depois gravar" sem trava: requisições paralelas passam do 5º erro; (b) `request.getRemoteAddr()` com `server.forward-headers-strategy: framework` não lê `X-Forwarded-For`; atrás de proxy o IP de todos é o do proxy e as 20 falhas por IP viram bloqueio global; resolver na #192 (Sprint 22); (c) o aviso de "Novo acesso" e o pedido de recuperação enviam e-mail de forma síncrona, o tempo de resposta pode distinguir conta existente de inexistente (RN-ACE-11); (d) a troca de senha logada não conta tentativas da senha atual (limite aceito no #18).
- **R02-12 · SUGESTÃO · operação** — R01-10 mantido (o compose fixa `name: ctcore` e o `down -v` do README apaga o banco do dono ao rodar em segundo clone); a build da imagem do back-end levou 8 min 8 s sem cache (o CI tem cache de camadas, o clone não).
- **R02-13 · SUGESTÃO · documentação** — `specs/backlog.md` linha 3 ainda diz "194 issues: 17 fechadas e 177 abertas (conferido em 17/10/2026)"; hoje são 26 fechadas.
- **R02-14 · SUGESTÃO · dados** — `email_message` e `account_device` guardam e-mail e aparelho sem prazo de retenção; entra na decisão de LGPD da Sprint 13 (o corpo do e-mail com código é apagado após o envio, `EmailService`, conferido).

#### Desvios e decisões herdados dos itens

| Item | Desvio ou decisão | Classe | Destino |
|---|---|---|---|
| #18 D1, D10 | só back-end; ponta a ponta da troca de senha vai para a #36 e o plano muda para a Sprint 03 | muda critério do marco | DONO (D1) |
| #18 D2 | lista local de senhas comuns (146 linhas) em vez de base de vazamentos | técnico aceitável | registrar; porta de vazamento na Sprint 22 se o dono quiser |
| #18 D3 | sessão de 12 h para qualquer conta com vínculo de gestão, 30 dias só para portal | muda requisito | AJUSTAR especificação (A2) e DONO (D3) |
| #18 D4 | 5 erros em 15 min bloqueiam o e-mail; 20 falhas por IP | muda requisito (número novo) | AJUSTAR especificação (A2) |
| #18 D5 | login e logout não auditam; conta sem vínculo não gera auditoria | técnico aceitável | registrar (ressalva R02-9 contra a regra "toda ação que altera dado grava na auditoria"); revisitar no item de Minha conta |
| #18 D6, D8 | isolamento das rotas de `/auth` adaptado; `PUT /auth/session/link` antecipada | técnico aceitável | registrar |
| #19 D4, D5 | primeiro acesso não avisa; cidade "local não identificado" até a Sprint 22 | muda produto | AJUSTAR especificação (A2); adaptador de localização na Sprint 22 |
| #20 D2, D3 | suporte por variáveis do front; contato da unidade fica como componente | muda produto | AJUSTAR especificação (A2); exibição nas telas autenticadas (Sprint 03 em diante) |
| #20 R5 | script do Turnstile direto no front com chave | exceção que exige o dono | DONO (D2) |
| #21 D1, D2 | `GET /auth/invitations/{code}` devolve nome, e-mail e validade; conta existente só informa a senha atual | muda produto e contrato | AJUSTAR especificação (A2) |
| #21 D7 | "pedir outro convite" é só texto | muda produto | registrar; a tela de reenvio é a RF-ACE-06 |
| #22 D1, D2 | mensagem única; validade do link só ao enviar a senha (410) | muda produto | AJUSTAR especificação (A2) |
| #22 D6 | "o 429 do pedido ... vale para qualquer e-mail" | decisão contraditória com o servidor | corrigir (A3) |
| #23 D1, D2, D4 | `LinkView.organizationName`; área derivada do papel; modal sem fechar | muda contrato e produto | AJUSTAR especificação (A2) |
| #25 D1, D2 | tela no mesmo endereço, sem redirecionar | muda produto | AJUSTAR especificação (A2) |
| #231 | timeouts por job | técnico aceitável | registrar; sem achado |

Sugestões acumuladas das rodadas de Verificação (para a lista de dívidas): contagem de tentativas na senha atual da troca logada (R02-11d); conferir que a #36 e a #37 assumem os "Pronto quando" de interface que o #18 deixou como "não se aplica" (a #36 já traz o e2e).

Texto de critério que o código contradiz: `docs/especificacao/02-contas-e-acesso.md` RN-ACE-19 (área aberta x vínculo de gestão) e o item #22 D6 (429 no pedido). Fora isso, a OpenAPI bate com os 9 controllers (`ContractRoutesTest`).

#### Ajustes propostos

**A1 · Códigos de regra nos nomes dos testes** (resolve R02-1) — antes do PR
- Tipo: corrigir nos itens #18 a #25.
- O que mudar e onde: acrescentar o código da regra ao `@DisplayName` dos testes de `AuthRoutesIT`, `InviteIT`, `AccountIT`, `AuthIsolationIT`, `NewAccessIT`, `EmailServiceIT`, `LoginLockoutTest`, `PasswordPolicyTest`, `InviteRulesTest` (ex.: `RN-ACE-10: ...`, `RN-ACE-20: ...`, `RN-ACE-13`, `RN-ACE-14`, `RN-ACE-19`, `RN-ACE-22`, `RN-ACE-05` a `07`, `RN-ACE-09`, `RN-ACE-12`, `RN-ACE-02`, `RN-ACE-04`, `RNF-14`) e `RF-ACE-07` no teste de último acesso do front e do back-end; sem mudar o corpo.
- Como provar: `grep -rhoE "RN-ACE-[0-9]+" backend/src/test | sort -u` lista todos os códigos das issues #18 a #25; `verify` verde.
- Tamanho: pequeno.

**A2 · Especificação atualizada com as decisões da sprint** (resolve R02-2) — antes do PR
- Tipo: ajuste de especificação (a especificação muda primeiro; depois o código segue o texto).
- O que mudar: em `docs/especificacao/02-contas-e-acesso.md`: RN-ACE-10 (acrescentar "20 falhas por IP em 15 minutos"), RN-ACE-19 (o prazo é decidido pelos vínculos da conta, conforme D3 do dono), RN-ACE-22 (primeiro aparelho não avisa; cidade "local não identificado" até haver localizador), RF-ACE-01 (contato de suporte), RF-ACE-02 (conta existente informa a senha atual; a tela mostra nome, e-mail e validade), RF-ACE-03 (validade do link só ao enviar), RF-ACE-05 (área pelo papel; modal obrigatório no login), RF-ACE-08 (tela no mesmo endereço). Trechos exatos nas decisões dos arquivos de item; nada de código muda.
- Como provar: reler as RN/RF com o comportamento observado nos testes; `git diff -- docs/especificacao` mostra só texto.
- Tamanho: pequeno.

**A3 · Limite por IP no pedido de link de nova senha** (resolve R02-3) — antes do PR
- Tipo: corrigir no item #22 (back-end e contrato).
- O que mudar e onde: `AuthService.requestReset` passa a registrar a tentativa em `login_attempt` (novo resultado, ou o já existente com e-mail e IP) e a recusar com `TOO_MANY_REQUESTS` acima de um limite por IP e por e-mail (por exemplo 5 pedidos por e-mail e 20 por IP em 15 minutos, como propriedades de `ctcore.security`); `docs/openapi.yaml` ganha a resposta 429 em `/auth/password-reset`; o MSW e a tela já tratam o 429. Alternativa: corrigir a D6 do #22 para dizer que não há limite e levar o limite para a Sprint 22 (nesse caso, decisão do dono e registro na especificação).
- Como provar: teste novo em `AuthRoutesIT` (N+1 pedidos devolvem 429 para qualquer e-mail, existente ou não) e `ContractRoutesTest`/`OpenApiContractTest` verdes.
- Tamanho: pequeno.

**A4 · Guarda de teste de isolamento por rota** (resolve R02-4) — depois do PR (primeiro item da Sprint 03, antes da #27)
- Tipo: novo item, pequeno. Título: "Guarda de teste de isolamento por rota".
- Texto de issue proposto: entregar um teste que lista as rotas do contexto da aplicação (as mesmas de `ContractRoutesTest`) e falha se alguma não constar num mapa explícito rota para classe de teste de isolamento (subclasse de `RouteIsolationModelIT` ou `AuthIsolationIT`), com lista curta de exceções públicas justificadas; incluir os testes que faltam hoje para `GET/POST /auth/invitations/{code}[/accept]`, `POST /auth/logout` e `POST /auth/password-reset`. Regra citada: RNF-31. Pronto quando: remover o teste de uma rota existente faz a guarda falhar; as rotas públicas ficam listadas com o motivo. Marco sugerido: Sprint 03.

**A5 · Cabeçalhos de segurança do front e cookie XSRF** (resolve R02-5) — depois do PR (junto da #192 ou como item pequeno na Sprint 03)
- Tipo: novo item. Entregar: em `frontend/nginx.conf`, `Content-Security-Policy` (com o domínio do desafio só quando houver chave), `frame-ancestors 'none'`, `X-Content-Type-Options: nosniff`, `Referrer-Policy: no-referrer` ao menos em `/convite/*` e `/redefinir-senha/*`; no back-end, `Secure` e `SameSite=Lax` no cookie `XSRF-TOKEN`. Pronto quando: `curl -I` na imagem do front mostra os cabeçalhos; o login continua funcionando.
- Tamanho: pequeno.

**A6 · Handlers MSW de logout e troca de senha, e teste no sentido contrário** (resolve R02-6) — depois do PR, na #36
- Tipo: corrigir/estender o item #36. O que mudar: handlers de `POST /auth/logout` e `POST /auth/password` em `handlers.ts`; em `msw.test.ts`, exigir que toda operação do OpenAPI tenha handler (ou esteja numa lista de exceções com motivo).
- Tamanho: pequeno.

Ordem sugerida: A1, A2 e A3 num único PR de correção para `dev` (fora de item, como o #227 da Sprint 01), depois o PR `dev`→`main`; A4 a A6 na Sprint 03.

#### Decisões do dono

**D1 · O ponta a ponta "Troca de senha encerrando as outras sessões" fica na Sprint 03 (#36)?** — recomendada: sim — motivo: não existe tela de troca de senha logado antes da #36; a prova de servidor com duas sessões já passa e o plano, a #18 e a #36 já dizem isso. Se sim, ajustar a descrição do marco "Sprint 02" no GitHub (bullets 2 e 4) para o texto do plano, para o critério ser julgável. Alternativa: manter na Sprint 02 e criar a tela de troca de senha agora (aumenta a sprint).

**D2 · Aceitar o carregamento direto do script do desafio invisível no front (`Turnstile.tsx`), desligado por padrão, até a Sprint 22?** — recomendada: sim, com a regra escrita no `AGENTS.md`/especificação de que o nome do fornecedor só aparece nesse componente e na configuração — motivo: sem a chave nada é carregado; mover para uma "porta" do front antes da Sprint 22 não reduz risco. Alternativa: trocar o nome do componente e da variável por termos neutros (`Challenge`, `VITE_CHALLENGE_SITE_KEY`) e deixar o fornecedor só no adaptador real.

**D3 · A sessão de 12 horas vale para toda conta que tenha algum vínculo de gestão (como o #18 D3 implementou), ou só quando a área aberta é a gestão (texto atual da RN-ACE-19)?** — recomendada: manter o implementado (por conta) e atualizar a RN-ACE-19 — motivo: o servidor não conhece a "área aberta" (é escolha do front) e o critério por conta é o mais conservador; a consequência é que um coordenador que também é responsável por um aluno nunca tem 30 dias. Alternativa: guardar a área na sessão e aplicar o prazo por área (exige mudar a sessão a cada troca de área).

#### Ações do dono

1. Decidir D1, D2 e D3.
2. Abrir `http://localhost:5173` com `npm run dev` (em `frontend/`, MSW ligado) e olhar contra o canvas, nos temas claro e escuro e em 360 px de largura: `/entrar` (códigos de teste: ver `frontend/README.md`), `/esqueci-senha`, `/convite/valido`, `/convite/existente`, `/convite/vencido`, `/convite/usado`, `/convite/cancelado`, `/redefinir-senha/valido`, `/redefinir-senha/vencido`; depois entrar com uma conta de dois vínculos e olhar o modal de escolha de organização e área, e `/sem-acesso`. O agente só provou ausência de rolagem horizontal e de violação de acessibilidade nas 6 telas públicas; não consegue comparar com o canvas nem ler o tom dos textos.
3. Se quiser, apagar o diretório do clone da revisão no scratchpad (`...\scratchpad\clone`, tem `node_modules`).
4. Com o resultado `APROVAR` (A1, A2 e A3 resolvidos) e D1 decidida: abrir o PR de `dev` para `main` com o método "Merge" (nunca squash) e, depois de revisar, dizer "sim" para liberar a Sprint 03.

#### Limitações e o que não foi conferido

- Canvas contra as telas e tom dos textos: só uma pessoa; a limitação do canvas está registrada nos itens (D5 do #21, D2 do #25).
- Modal de escolha de organização e `/sem-acesso` em navegador real, 360 px e contraste: NÃO CONFERIDO (precisam de sessão; ficam com jsdom, sem contraste). Foco, teclado e zoom: NÃO CONFERIDO fora dos testes dos itens.
- `e2e:full`, gitleaks, OSV-Scanner e `actionlint` locais: não rodei; o CI roda (`security` e `ci-gate` SUCCESS nos 9 PRs). `npm audit` do clone: 0 vulnerabilidades.
- Publicação, hospedagem, e-mail e desafio reais, Gmail e Outlook (RNF-28): NÃO CONFERIDO (limitação aceita, Sprints 22 e 23; `AGENTS.md`, "Limitações aceitas").
- Os ponta a ponta do Playwright rodam contra MSW, sem back-end: a integração real entre tela e `/api/v1/auth` não tem teste automatizado (o back-end é provado por MockMvc). Não é regressão (decisão D5 da Sprint 00), mas é a lacuna que o fluxo "troca de senha encerrando sessões" da #36 deveria fechar.
- As skills `/code-review`, `/security-review` e `ponytail-audit` não foram acionadas separadamente; reproduzi o essencial lendo o diff: `SecurityConfig`, `AuthController`, `AuthService`, `SessionService`, `SessionAuthenticationFilter`, `SessionCookie`, `LoginAttempts`, `Codes`, `AccountTransactions`, `InviteService` (cascata e aceite), `EmailService`/`EmailAccountMailer`, `V6` e `V7`, `application.yml`, `nginx.conf`, workflows e `osv-scanner.toml`. Não li linha a linha `NewAccessNotifier`, `ScopedAudit`, `LinkService`, as telas além de `Turnstile` e os testes além dos nomes. Excesso de código: nada a registrar.
- Teste de concorrência do bloqueio de login: não existe (R02-11a).

#### Riscos para a próxima sprint

- **Sprint 03** (duas etapas, permissões, Minha conta, Usuários, Configurações): a #26 precisa decidir onde entra o segundo fator no `AuthService.login` e na redefinição por link; a #27 aplica permissão por rota e vai usar `AccountPrincipal.role`/`unitIds`; a #36 deve trazer o e2e de troca de senha, os handlers MSW de logout e senha (A6) e a lista de sessões; a #37 chama `InviteService` (hoje só serviço, sem rota; a cascata de convite é testada em serviço) e precisa do teste de isolamento por rota (A4).
- **Auditoria**: login, logout e último acesso não auditam (D5 do #18); a #36 (sessões, troca de e-mail) deve decidir se encerrar sessão e trocar e-mail auditam por conta sem vínculo.
- **Sprint 22 (#192, #193)**: IP do cliente atrás de proxy (R02-11b), cabeçalhos do front (A5), chave do desafio e e-mail real (Cloudflare e envio reais), localizador por IP, `PUBLISH_*`, credenciais sem padrão de desenvolvimento, papel de banco separado (R01-5 e R00-11, mantidos).
- **Sprint 13 (LGPD)**: `account`, `email_message`, `account_device`, `login_attempt` guardam e-mail, IP e aparelho sem retenção definida (R02-14).
- **Exceções OSV com validade 2027-01-15**: reavaliar na migração para o Boot 4 ou com o Spring 6.2.20.
- Nenhuma issue `depende de você` trava a Sprint 03.
- Decisões herdadas: sessão por conta (D3 do #18), área derivada do papel (D2 do #23), sem plataforma em `account_link` (o painel da plataforma, Sprint 15, acrescenta o papel).

---

### Rodada 2 — APROVAR — 2026-10-17, estado 5b7f358e4b632d66444d1f155eb58ba526eceaca

Diff lido: `git diff 07a2fe0 dev` (16 arquivos, +99 −55), um único PR de squash, #243 "Ajustes da revisão da Sprint 02 (A1 a A3)", fora de item e sem `Closes`, como a rodada 1 propôs. `ci-gate` SUCCESS no PR (7 jobs: changes, security, backend, frontend, e2e, image-backend, image-frontend) e `Publish` success em `dev` no mesmo SHA. `main` segue em `17e0854` (Sprint 01). Proteção de `dev` inalterada: `ci-gate` exigido, push forçado `false`.

#### Reconferência dos achados da rodada 1

| Achado | Status | Evidência observada |
|---|---|---|
| R02-1 códigos de regra nos testes | PASSOU (resolvido) | `grep -rhoE "R[NF]{1,2}-[A-Z]+-[0-9]+" backend/src/test frontend/src frontend/e2e`: agora aparecem RN-ACE-02, 03, 04, 05, 06, 07, 09, 10, 11, 12, 13, 14, 16, 19, 20, 22 e RF-ACE-01, 02, 03, 05, 07, 08, só mudaram nomes (`@DisplayName`) e ajustes de teste, sem mexer em corpo. Restam sem citação RNF-28, 35 e 37, que a issue cita como critérios de e-mail em cliente real e de processo (RNF-28 é limitação aceita, Sprints 22 e 23); RNF-14 aparece. Não é regra crítica do `AGENTS.md` |
| R02-2 especificação | PASSOU (resolvido) | `git diff 07a2fe0 dev -- docs/especificacao`: RN-ACE-10 (20 falhas por IP em 15 min, vale para o pedido de link), RN-ACE-19 (prazo pelos vínculos da conta, decisão D3), RN-ACE-22, RF-ACE-01, 02, 03, 05 e 08 batem com o que os testes e o código fazem |
| R02-3 limite no pedido de link | PASSOU (resolvido) | `AuthService.requestReset` consulta `attempts.resetLimited` e grava `RESET_REQUEST`; migração `V10` ajusta o `check`; `application.yml` ganha `reset-max-per-email: 5`; OpenAPI ganha 429 e a descrição do limite; teste novo `AuthRoutesIT.resetRequestsAreLimited` (e-mail existente e inexistente, 5 passam, o 6º dá 429 idêntico, 16 min depois volta a valer). Prova minha com o jar do clone: 5 pedidos 200, 6º e 7º `429 TOO_MANY_REQUESTS`; e-mail inexistente de outra conta ainda 200. `RESET_REQUEST` fica fora de `check` de login (a consulta só olha `SUCCESS` e `WRONG_PASSWORD`), então não trava o login |
| R02-4, R02-5, R02-6 | mantidos | A4 a A6 foram previstos para a Sprint 03; nada mudou em `RouteIsolationModelIT`, `nginx.conf` nem `handlers.ts` |
| R02-7 / D1 | resolvido | Descrição do marco no GitHub agora diz "provado no servidor com duas sessões; o ponta a ponta de tela fica na Sprint 03, #36" e "o de 'troca de senha encerrando sessões' vai para a #36" (`gh api repos/{owner}/{repo}/milestones/3`) |
| R02-10 / D2 | resolvido | `AGENTS.md`, "Limitações aceitas": registra o carregamento direto do desafio, desligado sem chave, nome do fornecedor só em `Turnstile.tsx` e na configuração |
| D3 | resolvido | mantido por conta, `RN-ACE-19` atualizada |
| R02-8, 9, 11 a 14 | mantidos (sugestões) | não tocados |

#### Clone limpo (rodada 2)

Clone novo de `origin/dev` no scratchpad (`...\scratchpad\r2\clone`), projeto Compose `ctcorerev` (por causa do R01-10), seguindo o README e "Comandos".

| Passo | Resultado real |
|---|---|
| ambiente | `docker compose up -d --wait`: postgres, storage e mailbox saudáveis em 32 s; papel `ctcore_app` criado |
| back-end | `verify`: BUILD SUCCESS em 2 min 47 s; 90 testes rápidos e 96 de integração (eram 95), 0 falhas; JaCoCo 1283 de 1334 linhas (96,18%), "All coverage checks have been met"; `isolation` 64/64; `identity` 615/642; `mail` 89/90 |
| front-end | `npm ci`, `lint`, `typecheck`, `test:cov`, `build`, `e2e`: 227 testes em 33 arquivos, linhas 98,84%; e2e 7 de 7 `@critical` (13,4 s); cadeia inteira em 1 min 41 s |
| seed (2x) | 01, 02 e 03 aplicados nas duas vezes, exit 0; contagens iguais: 3 organizações, 5 contas, 6 vínculos |
| back-end vivo | readiness UP; limite do pedido de link visto acima |
| imagens | `ctcore-backend` (build em 31 s com cache) com `uid=10001(ctcore)` e readiness UP na porta de gestão; `ctcore-frontend` com 200 em `/` e em `/entrar` e 404 em `/api/v1/auth/session` |
| recomeço | `down -v` sem sobrar volume do projeto `ctcorerev`; `docker images` igual ao de antes; volumes `ctcore_postgres-data` e `ctcore_storage-data` do dono presentes |

Comparação com a rodada 1 (185 testes no back-end, 96,14%; 227 no front, 98,84%): 186 testes no back-end, 96,18%; front igual. Nada piorou.

Limpeza: contêineres `rev-be` e `rev-fe`, imagens `ctcore-backend` e `ctcore-frontend` e projeto `ctcorerev` removidos. Para encerrar o back-end do clone usei `taskkill /IM java.exe`, que encerra todo processo `java` da máquina, não só o meu (erro de método meu; se alguma ferramenta Java do dono caiu, é só reabri-la). Ficou o diretório do clone no scratchpad.

#### Achados novos

Nenhum BLOQUEIA nem AJUSTAR. Sugestões, sobre o código novo da #243:

- **R02-15 · SUGESTÃO · teste** — `AuthRoutesIT.resetRequestsAreLimited` prova o limite por e-mail (5); o limite por IP (20) de `LoginAttempts.resetLimited` não tem asserção própria. Um teste com 21 e-mails diferentes e o mesmo IP fecharia a RN-ACE-10.
- **R02-16 · SUGESTÃO · segurança** — o limite por e-mail do pedido de link permite que qualquer pessoa gaste os 5 pedidos de um e-mail alheio e impeça a vítima de pedir o link por 15 minutos (o e-mail já enviado continua válido). Aceitável para este estágio; o desafio real da Sprint 22 reduz o risco.
- **R02-17 · SUGESTÃO · código** — o limite por IP do pedido de link reutiliza `ip-max-failures` (nome de "falhas de login"); uma propriedade própria deixaria a configuração mais clara.

#### "Pronto quando" do marco (texto atual do marco)

| Bullet | Status | Evidência |
|---|---|---|
| Mensagem de erro do login idêntica | PASSOU | mantido da rodada 1; `AuthRoutesIT.identicalLoginFailures` passou no `verify` |
| Trocar a senha derruba as outras sessões (provado no servidor com duas sessões) | PASSOU | `AuthRoutesIT.changePassword` e `AuthIsolationIT` passaram no `verify`; a tela fica na #36 como o marco agora diz |
| Convite vencido, usado ou cancelado mostra explicação e caminho | PASSOU | mantido; `npm run test:cov` e e2e verdes |
| Fluxo ponta a ponta "convite e criação de senha" na suíte | PASSOU | `@critical Convite e criação de senha` no `npm run e2e`, 7 de 7 |
| "Pronto quando" geral em cada item | PASSOU, com R02-4 e R02-6 pendentes para a Sprint 03 | rastreabilidade resolvida (R02-1), cobertura confirmada acima |

Total: 5 de 5 bullets do marco passaram.

#### Decisões e ações do dono

- D1, D2 e D3: decididas e refletidas no marco, no `AGENTS.md` e na especificação.
- Ainda de pessoa (sem mudança): olhar as telas no navegador contra o canvas (lista e passos na rodada 1, "Ações do dono", item 2); modal de escolha e `/sem-acesso` em 360 px e contraste continuam NÃO CONFERIDO em navegador real.
- Pode abrir o PR de `dev` para `main` com o método "Merge" (nunca squash). Depois de revisar a sprint, o dono diz "sim" para liberar a Sprint 03.

#### Ordem sugerida para a Sprint 03

Primeiro o item A4 (guarda de isolamento por rota), antes da #27; A5 junto da #192 ou como item pequeno; A6 dentro da #36.

**Resultado da rodada 2: APROVAR** — A1, A2 e A3 resolvidos e verificados no clone limpo; sem BLOQUEIA e sem AJUSTAR antes do PR; restam só A4 a A6 e sugestões, para a Sprint 03.
