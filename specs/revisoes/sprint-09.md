# Revisão da Sprint 09 · Comunicação, lembretes e notificações

### Rodada 1 — AJUSTAR — 2026-10-24, estado 57124bd8923f9194b97fa01ac4d1d79b17936d0a

#### Resumo e veredito

A Sprint 09 entregou os e-mails automáticos com preferência de envio (#78), a lista de cobranças em atraso com lembrete automático, manual e aviso de bloqueio (#79), a configuração dos lembretes (#80), a lista e o novo comunicado com dia sem aula e "Avisar turma" (#81, #82), os textos editáveis (#83) e o sino de notificações (#84), mais cinco itens de ajuste (#342, #343, #344, #352, #358). As 12 issues do marco estão fechadas por PR de squash com `ci-gate` verde. O clone limpo passou no back-end (162 + 750 testes, 98,40% de linhas, 100% em `billing`, `payment` e `isolation`), no front (1176 testes, 96,80% de linhas), nas imagens e no seed. Os quatro bullets específicos do marco passam. Há dois BLOQUEIA: o `e2e` `@critical` `matricula.spec.ts:83` reprovou em 3 de 7 execuções completas no clone limpo, por um estouro real de 14 px na barra superior a 360 px que o sino da #84 causou (R09-1); e a #352 foi mesclada sem Construção, Verificação nem Entrega no arquivo do item (R09-2). Há cinco AJUSTAR, a maioria de texto: 26 decisões "muda o comportamento" sem a especificação ajustada e três pontos em que o código diverge de uma regra (R09-3), encaixes da sprint sem destino (R09-4), exceção inteira em log (R09-5), README (R09-6) e o arquivo da #343 sem fechamento (R09-7). O dono decide uma coisa (D1) e confere as telas contra o canvas. Resultado: `AJUSTAR`.

#### Estado revisado

| | |
|---|---|
| `dev` | `57124bd8923f9194b97fa01ac4d1d79b17936d0a` (igual a `origin/dev`; clone novo no scratchpad, removido ao fim) |
| `main` | `0a6a59d3d9602d5ce1075017f0a3553e5e9a9bf5` (PR #348 da Sprint 08) |
| Data | 2026-10-24 |
| Ferramentas | Java 21.0.12 (build; o padrão da máquina é o 25.0.4), Node 24.21.0, Docker 29.8.1 |
| Diff | `git diff origin/main...origin/dev`: 178 arquivos, 13.545 inserções, 152 remoções (back-end main: 48 arquivos, 2.894 linhas; back-end test: 49 arquivos, 3.842 linhas; front `src/features`: 44 arquivos; mocks: 9 arquivos; `docs/openapi.yaml`: 590 linhas; `specs`: 13 arquivos; `.github`: 1 arquivo; `backend/pom.xml` e `frontend/package.json`: sem mudança) |
| PRs de item | #350 (#78), #351 (#79), #353 (#352), #354 (#80), #355 (#81), #356 (#82), #357 (#83), #359 (#84), #360 (#342), #361 e #366 (#343), #364 (#344), #365 (#358) |
| PRs fora de item | #345 (backlog A6 a A8), #346 (revisão da Sprint 08, rodada 2), #347 (`.gitleaksignore`), #349 (libera a Sprint 09); #337 e #336 (especificação e encaixes da Sprint 08) e os PRs #331 a #341 são da Sprint 08 |

Pré-condições:
- `dev` limpa e igual a `origin/dev`; nenhum PR aberto contra `dev`; o remoto só tem `main` e `dev` (`git ls-remote --heads origin`: 2). O `git branch -a` lista branches `item/*` e `docs/*` por referência de rastreamento velha (sem `fetch --prune`) e 14 branches locais já mergeados (`item/331-…`, `item/333-…`, `item/338-…`, `item/340-…`, `item/352-…`, `item/77-…`, `ajustes/seguranca-repo-publico`, `docs/…`). Nenhum é trabalho em andamento. Apagar é do dono.
- As 12 issues do marco estão `CLOSED` e como "feito" no `specs/backlog.md`. Nenhuma tem a etiqueta `depende de você`. Todas foram abertas por `DevDosAnjos`. O #363 (CA4 do #343) está aberto e é da Sprint 10.
- Nenhuma issue de outro marco foi fechada por PR desta sprint; #366 tem `Refs #343, #363`, sem `Closes`.
- `gh auth status` válido (conta `DevDosAnjos`); Docker ativo.

#### Trilha de processo

Todos os PRs têm base `dev`, título `#N: <título da issue>`, um commit de squash em `dev` (sem merge commit entre `main` e `dev`: `git log --merges origin/main..origin/dev` vazio) e `ci-gate` `SUCCESS` (8 checks verdes, ou 3 a 5 pulados por pasta nos PRs só de teste, infra ou documentação). Todos têm `Closes #N`, exceto #366.

| Item | PR | Verificação | Entrega | Observação |
|---|---|---|---|---|
| #78 E-mails automáticos | #350 | Rodada 1 OK | sim | duas partes (A e B); 17 critérios; só `lint`, `typecheck` e o teste de mock do front rodados (sem tela) |
| #79 Cobranças em atraso | #351 | Rodada 1 OK | sim | duas partes; o desvio do `UPDATE` ausente em `charge_reminder` foi resolvido por trava e índice único |
| #352 Cobertura de `billing` | #353 | **nenhuma** | **não** | arquivo só com as seis seções de especificação e T1 marcada; sem Construção, Verificação nem Entrega (R09-2) |
| #80 Lembretes (configuração) | #354 | Rodada 1 OK | sim | a construção registrou o `billing` em 0,99, que é o #352 |
| #81 Comunicados (lista) | #355 | Rodada 1 OK | sim | |
| #82 Novo comunicado | #356 | Rodada 1 OK | sim | três partes, 10 tarefas, uma rodada de verificação |
| #83 Textos | #357 | Rodada 1 OK | sim | |
| #84 Sino | #359 | Rodada 1 OK | sim | trouxe o estouro de 360 px (R09-1) e abriu o #358 |
| #342 Bolsa de 100% segue Ativa | #360 | Rodada 1 OK | sim | fecha o R08-7 |
| #343 Playwright em imagem | #361, #366 | Rodadas 1 e 2 `NAO_CONFERIDO`, sem `OK` | **não** | fechado por decisão do dono de 24/10 com 1 de 5 medições; CA4 passou para o #363 (R09-7) |
| #344 `aguardar-ci.sh` órfão | #364 | Rodada 1 OK | sim | |
| #358 `AuditServiceIT` instável | #365 | Rodada 1 OK | sim | |

Desvios de processo: (a) #352 sem verificação (BLOQUEIA); (b) #343 mesclada antes de uma rodada `OK`, com decisão do dono registrada na issue #363 (autor `DevDosAnjos`) e no arquivo do item; (c) #366 é um segundo PR com título `#343: …` para uma issue já fechada, só para registrar medição e decisão em arquivo; (d) a #84 achou o `4111` do #358 na construção e o transformou em issue, como o `AGENTS.md` manda. Nenhum item teve mais de uma rodada `OK`; a #343 teve duas `NAO_CONFERIDO`. Nenhum merge ocorreu com `ci-gate` vermelho.

#### "Pronto quando"

**Do marco** (descrição do marco 10, `gh api repos/DevDosAnjos/ctcore/milestones/10`)

| Bullet | Status | Evidência |
|---|---|---|
| Um comunicado de teste chega na Caixa de saída, e o responsável recebe pelo filho menor | PASSOU | `AnnouncementCreateIT` CA6/CA15 (envio agora: 201, e-mail por endereço na `email_message`, pulado a quem desligou) e `AnnouncementRecipientsIT` CA2/CA3 (turma com menor e dois responsáveis: 4 pessoas e 3 e-mails; responsável de dois filhos conta uma vez); `verify` do clone: 750 ITs, 0 falhas |
| O Professor não consegue escolher turma de outro professor nem a unidade inteira | PASSOU | `AnnouncementTargetsIT` CA1 (só as duas turmas dele, sem unidades nem modalidades) e `AnnouncementCreateIT` CA11; código lido em `AnnouncementTargets.resolve`: turma de outro dá 403, `UNIT` e `MODALITY` dão 403 para `TEACHER`, `ORGANIZATION` só para o Coordenador |
| Lembretes param no minuto do pagamento | PASSOU | `ReminderRaceIT` CA8 (cobrança paga numa transação aberta enquanto a tarefa roda: a trava da linha faz o lembrete esperar e não sair depois do commit) e `ReminderJobIT` CA7 (paga, cancelada, renegociada, estornada ou em conferência não recebe) |
| Fluxo ponta a ponta "cancelamento de aula com aviso à turma" na suíte | PASSOU, com ressalva | `frontend/e2e/comunicados.spec.ts`, `@critical Cancelamento de aula com aviso à turma`: passou em todas as execuções completas do clone (a única falha foi `matricula.spec.ts:83`, R09-1). Ressalva: cancelar a aula não envia nada; o aviso sai pelo botão "Avisar turma", que abre o formulário preenchido (D8 da #82, decisão que muda o comportamento) |
| Todos os itens do "Pronto quando" geral valem para cada item | NÃO PASSOU em um ponto | 360 px sem rolagem horizontal falha na barra superior (R09-1); o resto está na tabela seguinte |

Resultado do marco: 4 de 5 bullets passaram.

**De todo item (plano de sprints)**

| Regra | Status | Evidência ou "não se aplica nesta sprint: motivo" |
|---|---|---|
| Regras citadas com ao menos um teste | PASSOU, com ressalva | os testes citam os "CA" e não o código da regra (S2 da Sprint 07 segue valendo); varrido por comportamento: RN-COB-17 a 20 (`ReminderJobIT`, `BlockWarningIT`, `ChargeReminderSendIT`), RN-COM-01, 03, 05 a 09 (`AnnouncementTargetsIT`, `AnnouncementCreateIT`, `AnnouncementRecipientsIT`, `AnnouncementsIT.reads`), RN-COM-11 a 14 (`EmailRegistryIT`, `EmailBouncesIT`, `TextModelsIT`), RN-NOT-02 a 04 (`NotificationsIT.urgentEmail`, `NotificationsTest`), RN-AGE-10 (`NoClassDayNoticeIT`). Sem teste porque não foi implementado: editar comunicado agendado (RN-COM-06), "Encerramento solicitado" como destinatário (RN-COM-02, S1 da #82), resumo diário por e-mail (RN-NOT-01, P3 do backlog). Ver R09-3 e R09-4 |
| Cobertura mínima 80% e 100% nos pacotes críticos | PASSOU | back-end 98,40% de linhas (8.379 linhas), 0 não cobertas em `billing` (876), `payment` (239) e `isolation` (64); `subscription` e `deactivation` ainda não existem; "All coverage checks have been met". Front: `test:cov` exit 0, 136 arquivos, 1176 testes, 96,80% de linhas (93,51% de instruções), pisos de 100% em `billing`, `charges`, `payments`, `subscription` e `deactivation` atendidos pelo `vite.config.ts` |
| Rotas novas com teste de isolamento entre organizações e entre perfis | PASSOU | 12 rotas novas (`/announcements*`, `/notifications*`, `/charges/overdue`, `/charges/reminders`, `/auth/email-preferences`, `/announcements/templates` e os modelos de texto): `AnnouncementsRouteIT`, `AnnouncementCreateRouteIT`, `AnnouncementSchemaIT` (RLS), `ChargeReminderIsolationIT`, `EmailPreferencesIsolationIT`, `NotificationsIT` (isolamento com RLS e perfis), `TextModelsIT` CA11 e CA12; RLS `enable` e `force` lidas em `V43` a `V47` |
| OpenAPI, implementação e MSW batendo | PASSOU | `OpenApiContractTest` e `ContractRoutesTest` dentro do `verify`; `announcements.msw.test.ts`, `announcements-send.msw.test.ts`, `charge-reminders.msw.test.ts`, `email-preferences.msw.test.ts`, `notifications.msw.test.ts`, `texts.msw.test.ts`, `unit-settings.msw.test.ts` no `test:cov` |
| Auditoria em toda escrita | PASSOU, com ressalva | gravam auditoria: preferência de e-mail, lembrete manual, criar, imagem e cancelar comunicado, modelos de texto (antes e depois), "marcar todas" e "receber tudo". Efeitos de sistema não gravam (lembrete automático, aviso de cobrança, envio do comunicado agendado, notificação publicada), por D9 da #78, D9 da #79, D10 da #82; o histórico é `charge_reminder`, `email_message` e `notification`. O texto do comunicado, os endereços e a senha não vão para a auditoria |
| Valores em centavos, operações idempotentes, lotes tudo ou nada | PASSOU | lembrete automático uma só vez por índice único `charge_reminder_once` e por trava (`ReminderRaceIT`); aviso de cobrança reivindicado por `update … where notified_at is null`; `POST /charges/reminders` numa transação com 404 para o pedido inteiro; criar dia sem aula com aviso desfaz tudo no limite diário. Esta sprint não grava valor novo em centavos |
| Quatro estados, 360 px e axe nos dois temas | PASSOU por proxy; NÃO PASSOU em 360 px real na barra; NÃO CONFERIDO em navegador de pessoa | todas as telas e janelas novas têm teste de estados e axe claro e escuro a `innerWidth = 360` (jsdom); a sonda no Chromium mostrou `scrollWidth` 374 contra 360 na barra superior de toda tela da gestão durante "Carregando unidades..." (R09-1); contraste real e layout contra o canvas não foram medidos (Ações do dono) |
| Textos só nos arquivos de tradução | PASSOU | `npm run lint` exit 0 (regra de texto literal) |
| Recurso novo desligado por organização | não se aplica nesta sprint | nenhum item pede liberação gradual (D10 da #78, D12 da #82, D10 da #84); a unidade desliga os lembretes por `reminders_enabled` |
| Notas de versão | NÃO CONFERIDO (sem arquivo) | o repositório segue sem arquivo de notas; RF-AJU-04 é da Sprint 21 e os itens dizem "só se existir" (S4 da Sprint 07, sem decisão) |

#### Clone limpo

Clone de `origin/dev@57124bd` no scratchpad, com `COMPOSE_PROJECT_NAME=revs09` (as portas 5432, 8333, 1025 e 8025 estavam livres; havia um projeto `revctcore7-*` e o contêiner `competent_neumann` que não são meus e não toquei). Back-end com Java 21.

| Passo | Comando | Resultado real |
|---|---|---|
| ambiente | `docker compose up -d --wait` (de `infra/`, sem `.env`) | 3 serviços saudáveis em cerca de 25 s |
| banco | `bash infra/scripts/prepare-runtime-db.sh` | exit 0 |
| back-end | `./backend/mvnw -f backend/pom.xml verify` | BUILD SUCCESS em 10 min 39 s; 162 testes rápidos e 750 de integração, 0 falhas, 0 ignorados; "All coverage checks have been met"; 98,40% de linhas |
| migrações e readiness | `spring-boot:run -Dspring-boot.run.profiles=local` | "Successfully applied 47 migrations … now at version v47" (eram 41); `/actuator/health/readiness` `{"status":"UP"}` na porta 8081 |
| seed (3x e uma 4ª e 5ª) | `bash infra/scripts/seed.sh` | exit 0 nas cinco; 20 arquivos de seed (eram 18); 3 organizações; 4 comunicados, 6 notificações, 3 cobranças; 326 linhas após a 1ª e 327 após a 2ª, a diferença sendo só a tabela `shedlock` (o back-end estava de pé); com o back-end parado, a 5ª execução não mudou nenhuma tabela (diff das contagens vazio). Observação: o `seed.sh` usa `docker compose` sem nome de projeto, então com `COMPOSE_PROJECT_NAME` diferente é preciso exportá-lo (não afeta quem segue o README) |
| front-end | `npm ci`, `lint`, `typecheck`, `test:cov`, `build` | todos exit 0 (25 s, 23 s, 18 s, 283 s, 19 s); 136 arquivos e 1176 testes; 96,80% de linhas |
| e2e | `npx playwright install chromium`; `npm run e2e` | **36 testes** (eram 35); 7 execuções completas: 4 verdes (36 passaram) e 3 vermelhas, todas em `matricula.spec.ts:83` (`@critical Coordenador conclui a nova matrícula em cinco etapas`, `scrollWidth <= innerWidth` a 360 px; uma delas com 2 falhas, o segundo nome não foi lido). O teste sozinho passou 3 de 3. R09-1 |
| imagens | `docker build -t ctcore-backend:rev09 backend` e `ctcore-frontend:rev09 frontend` | as duas construídas; back-end roda como UID 10001 (`ctcore`), readiness `UP` em `:8081` com o banco do compose, `/actuator/health` na `:8080` devolve 404; front-end como UID 101, `/` 200, `/alunos` 200, `/api/x` 404. Etiquetas `rev09` removidas; `docker images` voltou ao estado de antes (as `ctcore-*:latest` do dono não foram tocadas) |
| recomeço | `docker compose down -v` e `up -d --wait` de novo | volumes `revs09_*` removidos, 3 serviços saudáveis de novo, `prepare-runtime-db.sh` exit 0; `down -v` final; nada de `revs09` ficou |

Comparação com a linha de base (Sprint 08): melhorou em testes (back-end 149 + 638 → 162 + 750; front 1075 → 1176; e2e 35 → 36), cobertura (98,30% → 98,40% no back-end; 96,76% → 96,80% no front) e tempo do `verify` (13 min 24 s, não limpo, → 10 min 39 s); migrações 41 → 47; seed 18 → 20 arquivos. Piorou em estabilidade do `e2e`: 3 de 7 execuções completas vermelhas, contra 0 na Sprint 08 (R09-1).

Passos do README que não bastaram: nenhum. O README de raiz e a seção "Comandos" do `AGENTS.md` levam ao ambiente de pé, ao seed e às suítes. A ressalva do `e2e` local é a mesma do `AGENTS.md` (`npx playwright install chromium` na primeira vez).

#### CI e proteções

| Item | Estado real | Evidência |
|---|---|---|
| `ci-gate` exigido em `dev` | sim | `gh api repos/DevDosAnjos/ctcore/branches/dev/protection`: `required_status_checks.contexts = ["ci-gate"]`, `strict: false`, `allow_force_pushes: false`, `allow_deletions: false`, `enforce_admins: false` |
| regra de `main` | conforme | ruleset `protege-main` (id 25019105, ativo, sem bypass): `deletion`, `non_fast_forward`, pull request com merge `["merge"]` apenas (sem squash), `ci-gate` exigido. A rota clássica de proteção de `main` devolve 404, que é esperado quando a regra é ruleset |
| últimos runs de `ci.yml` e `publish.yml` em `dev` | verdes | `Publish` em `dev`: oito runs seguidos `success` (de `c748f45` a `57124bd`). O `ci.yml` roda em PR: o run mais recente (`40095126916`, branch `item/343-fechar-ca4`) e os de `item/358-…` (`40093207930`) e `item/344-…` (`40090311740`) estão `success`; PRs #350 a #366 com `ci-gate` `SUCCESS` e jobs pulados só onde a pasta manda |
| `permissions:` e pinagem de ações | conforme | `permissions:` no topo do `ci.yml`; ações fixadas por versão exata (`checkout@v7.0.1`, `setup-node@v7.1.0`, `setup-java@v6.1.0`, `upload-artifact@v7.0.2`); `ci-gate` exige `success` ou `skipped` de `changes, backend, frontend, e2e, image-backend, image-frontend, security` e reprova resultado vazio; o único diff de workflow da sprint é o `e2e` na imagem `mcr.microsoft.com/playwright:v1.64.1-noble` com passo de conferência da versão contra o lockfile (#343) |
| gitleaks | verde no CI; NÃO CONFERIDO localmente | o job `security` passou em todos os PRs; não há binário do gitleaks nesta máquina. Varredura própria por padrão de segredo em `backend/src/main`, `frontend/src`, `infra` e `.github`: só dois valores fictícios do MSW (segredo TOTP de exemplo e `token-de-confirmacao`), anteriores a esta sprint |
| exceções de vulnerabilidade (`osv-scanner.toml`) | 2, com motivo e validade | `GHSA-j9f9-w8pj-32f8` e `GHSA-pc63-qcmh-9cmg`, ambas até 2027-01-15, "sem correção na 6.2.x"; nenhuma nova nesta sprint; `pom.xml` e `package.json` sem mudança (nenhuma dependência nova) |
| segredos e variáveis do fluxo de publicação (só nomes) | faltam, como previsto | `gh secret list` e `gh variable list` vazios; `PUBLISH_REGISTRY`, `PUBLISH_REGISTRY_USER` e `PUBLISH_REGISTRY_TOKEN` não existem e a publicação é pulada (limitação aceita até a #192) |
| visibilidade do repositório | `public` | esperado até antes da Sprint 22 (trava do `AGENTS.md`) |

#### Achados

- **R09-1 · BLOQUEIA · front / teste** — local: `frontend/src/features/permissions/ManagementLayout.tsx:30-36` (barra superior), `frontend/src/features/notifications/BellButton.tsx` (botão `min-w-11`), `frontend/e2e/matricula.spec.ts:99-103`
  - esperado: telas "funcionando a partir de 360 px" e sem rolagem horizontal (`AGENTS.md`); regressão completa `e2e` verde (`AGENTS.md`: falha na regressão vira issue antes da rodada `OK`).
  - observado: com o sino da #84, o conjunto à direita da barra (`UnitSwitcher` em "Carregando unidades..." com 73 px, sino com 44 px, "Minha conta", alternar tema) passa de 360 px por 14 px enquanto as unidades carregam. Numa sonda de 40 cargas de `/alunos/nova-matricula` a 360 px, 5 deram `scrollWidth` 374 contra 360, sempre com o texto "Carregando unidades..." (cabeçalho `["Carregando unidades...:73","Notificações:44","Minha conta:63","Usar tema escuro:44"]`); as outras 35 deram 360. O `@critical` `matricula.spec.ts:83` mede isso logo após `setViewportSize` e reprovou em 3 de 7 execuções completas do `npm run e2e`.
  - evidência: `npx playwright test e2e/zz-probe.spec.ts --workers=1` (arquivo temporário, descartado com o clone), log `PROBE 9/12/20 … "sw":374 … "bad":["DIV.ml-auto flex items-center gap-2 right=374", …]`; `npm run e2e`: `1 failed, 35 passed` (2 vezes), `2 failed, 34 passed` (1 vez) e `36 passed` (4 vezes); o teste isolado 3 de 3 verde. No CI, o `e2e` do #84 (PR #359) e do #343 (PR #361) passou, mas o CI não é prova de ausência de intermitência.
  - efeito: a gestão no celular mostra rolagem horizontal por instantes em toda tela, a cada carga; o PR `dev`→`main` roda `e2e:full` e pode reprovar ao acaso.
  - proposta: A1.

- **R09-2 · BLOQUEIA · processo** — local: `specs/itens/352-cobertura-aviso-cobranca.md` (23 linhas, seis seções e T1 marcada, sem "Construção", "Verificação" nem "Entrega"), PR #353
  - esperado: todo item tem Construção, ao menos uma `### Rodada N — OK` e Entrega (plano de sprints, "Pronto quando" de todo item; trilha de processo).
  - observado: o item foi construído e entregue sem verificação registrada; o PR #353 tem `ci-gate` verde (5 checks, 3 pulados) e `Closes #352`.
  - evidência: `grep -nE '^(## |### Rodada)'` no arquivo só devolve as seis seções. Evidência independente desta revisão de que o conteúdo está certo: no `verify` do clone, `billing` tem 876 linhas cobertas e 0 perdidas, e `ChargeNoticeIT` roda dentro dos 750 ITs verdes.
  - efeito: contraria a regra do processo; o risco real é baixo (só teste, sem código de produto), por isso o conserto é pequeno.
  - proposta: A2.

- **R09-3 · AJUSTAR · especificação** — local: `docs/especificacao/` (`06`, `09`, `10`, `13`, `02`), decisões dos itens #78 a #84, `git diff origin/main...origin/dev --stat -- docs/especificacao` vazio
  - esperado: "Mudou uma regra, muda primeiro a especificação" (`AGENTS.md`); desvio que muda requisito volta para a especificação.
  - observado: 26 decisões rotuladas "muda o comportamento" (#78: D3, D4, D6, D7; #79: D1 a D5; #80: D1, D2, D6; #81: D1, D2; #82: D1, D2, D3, D5, D7, D8; #83: D1, D2, D4; #84: D1, D2, D3) e nenhuma linha de `docs/especificacao` mudou. É a mesma classe do R08-4. Três delas divergem do texto de uma regra e merecem linha própria: (1) RN-COM-06 diz "agendado pode ser editado ou cancelado até sair" e só o cancelamento existe; (2) RN-COM-02 inclui a modalidade com "Encerramento solicitado" e a situação ainda não existe (S1 da #82); (3) RN-COB-17 diz "ligar, desligar, canais, horário" por unidade e a tela tem um interruptor geral, a hora e os dias, sem escolha de canal (D1 e D2 da #80); fora essas, o CA5 do texto da #81 ("os dois primeiros… não o terceiro") foi contrariado a favor de R3 e D2 (o arquivo do item registra o desvio, mas o texto do critério ficou).
  - evidência: `grep -c "muda o comportamento"` por arquivo de item; leitura de `docs/especificacao/13-comunicacao-e-notificacoes.md` (RN-COM-01 a 14, RN-NOT-01 a 04) e `10-cobrancas.md` (RN-COB-17 a 20).
  - efeito: quem ler a especificação do produto não encontra o que o produto faz (quem vê a lista, janela do aviso de 10 dias, pagador como destinatário, bounce marcando a organização, recibo parado em endereço com problema, limite diário por unidade com limite próprio para a organização, comunicado do dia sem aula, audiência por tipo de notificação).
  - proposta: A3.

- **R09-4 · AJUSTAR · processo** — local: `specs/backlog.md` seção "Encaixes para as próximas sprints" (não há bloco da Sprint 09), issues #176, #122, #86, #87, #96, #85 e as que criam cada fato
  - esperado: o que a sprint deixa para depois está, com "Pronto quando", na issue que o fará (padrão que o A4 da Sprint 08 criou).
  - observado: ficaram sem destino em issue: (a) editar comunicado agendado (RN-COM-06) e limpar imagens de comunicado enviadas e não usadas (D11 da #82); (b) `EmailBounces.register` e `EmailOpens.register` não têm rota e o provedor real "os chamará na Sprint 22", mas a #176 só diz "retorno de e-mail que volta" e a #177 não cita a regra do SMS por e-mail não aberto (RN-COB-17, D4 da #79): até lá o SMS sai sempre que há telefone; (c) o resumo diário por e-mail e a opção de desligá-lo (RN-NOT-01) estão só na pendência P3 e deveriam estar na #122; (d) os tipos de notificação sem produtor (pagamento recusado, comprovante, pedido do portal, aula experimental, autocadastro, turma sem professor, pedido de exclusão, pedido de encerramento, Mercado Pago desconectado, assinatura em atraso) entram "com o item que cria o fato" (D2 da #84) e nenhuma dessas issues cita `Notifications.publish`; (e) `{link_pagamento}` sai vazio até a Sprint 11 (D4 da #83) e o envio das boas-vindas só vem com o convite do portal (D3 da #83); (f) a gravação de `announcement_read` é do portal (D5 da #81): a #93 cita "Marcar todos como lidos" mas não diz que abrir grava a leitura por aluno.
  - evidência: `gh issue view` por busca de "Notifications.publish", "EmailBounces", "EmailOpens", "link_pagamento", "resumo diário", "RN-COM-06" nos corpos das issues abertas: nenhum acerto fora das citadas.
  - efeito: o item de cada sprint seguinte não sabe que herda o gancho; mesma classe do R08-5.
  - proposta: A4.

- **R09-5 · AJUSTAR · segurança** — local: `billing/ChargeNoticeJob.java:65`, `plan/PlanWrite.java:195`, `communication/AnnouncementSendJob.java:54`, `notification/InvitePendingJob.java:61` (`LOG.error(…, e)`)
  - esperado: log sem dado pessoal (RNF-11, `AGENTS.md` "auditoria sem senha, token…"); a verificação da #78 já sugeriu registrar só a classe da exceção.
  - observado: os quatro caminhos novos imprimem a exceção inteira (pilha e mensagem). `Notifications.publish` faz o certo (`cause={}` com `getClass().getSimpleName()`). Uma violação de restrição do PostgreSQL carrega `Key (…)=(valor)`, que pode ser o endereço de e-mail.
  - evidência: `grep` das linhas acima; sem caminho de exploração externo (o log não é público), por isso é AJUSTAR e não BLOQUEIA.
  - efeito: dado pessoal possível em log de erro.
  - proposta: A5.

- **R09-6 · AJUSTAR · documentação** — local: `README.md` linha da tabela do `frontend` ("100% em `src/features/{billing,payments,subscription,deactivation}`")
  - esperado: o README dizer o mesmo que `AGENTS.md` e `frontend/vite.config.ts` (que inclui `charges`, por causa da #340).
  - observado: o README não lista `charges`.
  - evidência: `vite.config.ts:26-30` tem as cinco pastas; `AGENTS.md` também.
  - efeito: quem lê o README acha que as telas de cobrança não têm piso de 100%.
  - proposta: A6.

- **R09-7 · AJUSTAR · processo** — local: `specs/itens/343-cache-playwright-ci.md` (sem `OK` e sem "Entrega"; critério CA4 ainda diz "5 runs"), PR #366
  - esperado: item fechado tem rodada `OK` e "Entrega"; critério alterado volta para o texto.
  - observado: o #343 foi mesclado (#361) e fechado com duas rodadas `NAO_CONFERIDO`; o dono decidiu em 24/10 aceitar a medição de um PR e levar o CA4 ao #363 (Sprint 10). A decisão está registrada na issue #363 (autor `DevDosAnjos`) e em "Decisão do dono" no arquivo. Evidência da medição vista por mim: run `40087704834`, "Initialize containers" 33 s (10:53:46 a 10:54:19), `npm run e2e` 42 s (10:54:26 a 10:55:08), job `e2e` inteiro 89 s (10:53:44 a 10:55:13), os dois limites do CA4 (container abaixo de 60 s, `e2e` abaixo de 7 min) respeitados para esse PR. O arquivo não tem "Entrega" e o CA4 do texto não reflete a decisão.
  - efeito: só de registro; a decisão é legítima.
  - proposta: A6.

#### Sugestões (sem dono)

- S1 (#78): `PlanRateNoticeIT` e `EmailBouncesIT` não afirmam a captura de log que o CA16 cita; `PUT /auth/email-preferences` com corpo ausente ou mal formado não foi conferido.
- S2 (#79): `charge.reminder_sent` também grava `NO_ADDRESS` e `EMAIL_PROBLEM`; o texto de Entrega diz "tudo ou nada" para o envio manual, o que vale para a validação do pedido (404 do pedido inteiro, uma transação), mas o D5 diz que cada cobrança tem o seu resultado.
- S3 (#80): o foco no campo com erro só vem pelo Enter; a hora "09" é aceita.
- S4 (#81): `Announcements.requireAnnouncements` confere o acesso geral, não por unidade do registro; o recorte por perfil cobre o caso.
- S5 (#82): 409 de `noClassDayId` cai na mensagem genérica; criar dia sem aula com aviso numa unidade que já tem 20 comunicados no dia desfaz o dia inteiro; o envio na requisição tem teto de 2.000 destinatários sem garantia de latência (S2 da #82).
- S6 (#83): falta teste dedicado de que `BLOCK_WARNING` ignora texto próprio (hoje por construção, `TextModel.reminder` devolve nulo).
- S7 (#343): o passo de conferência da versão lê o próprio `ci.yml` com `sed`; uma segunda linha `image: mcr…playwright` quebraria a comparação de forma obscura.
- S8: `LOG.error` e e-mails dentro da transação do envio manual de lembretes (`OverdueCharges.send` envia com as linhas travadas por `for update`): aceitável no teto de 100 cobranças; vale revisar se o envio real da Sprint 22 for lento.
- S9: sobras locais de branch (14) e referências remotas velhas (`git fetch --prune`): do dono.

#### Camada técnica

Leitura do diff de `main...dev` em back-end, migrações `V42` a `V47`, `docs/openapi.yaml`, `.github`, `infra` e `README`/`AGENTS.md`, mais leitura dirigida de `Notifications`, `AnnouncementTargets`, `AnnouncementText`, `AnnouncementWrite.uploadImage`, `EmailService`, `OverdueCharges.send`, `BellButton` e `ManagementLayout`.

- Correção: sem erro de lógica encontrado nos trechos lidos. Testes: nenhum `@Disabled`, `skip`, `fixme` ou `only` no diff; a única espera fixa é o `Thread.sleep(50)` do laço de `ReminderRaceIT`, que espera a trava e não é asserção temporal. O teste de 360 px do `e2e` mede sem esperar o carregamento das unidades, o que expõe o R09-1.
- Segurança: permissão no servidor em toda rota nova (`requireAnnouncements`, `requireCollections`, `requireManagement`; Professor só turma sua, Administrador só o alcance dele, `ORGANIZATION` só do Coordenador); RLS `enable`/`force` com política por `app.organization_id` em `announcement`, `announcement_read`, `announcement_class`, `text_model`, `charge_reminder`, `sms_log`, `notification` e `notification_preference`; `notification` e `announcement_read` sem `UPDATE`/`DELETE` indevidos para o papel operacional; gatilho `announcement_sent_is_final` (V45) protege o comunicado enviado do papel operacional; o texto do e-mail é escapado (`HtmlUtils.htmlEscape`) e o assunto perde `\r\n`; links do comunicado só `http`/`https`; imagem do comunicado só a guardada na organização, 5 MB, formato validado; notificação guarda só valores mínimos (nunca CPF, e-mail ou telefone) e `publish` registra só a classe da exceção. Pendente: R09-5.
- Dados: migrações novas (V42 a V47) sem editar as antigas; todas com `Revert (manual)` no comentário. `email_message` ganhou `organization_id` sem RLS (D8 da #78): a tabela já não tinha RLS, nenhuma rota a lê e o `EmailRetryJob` atravessa organizações; o isolamento está na consulta que decide `SKIPPED` e em `EmailProblems.mark`. Fica registrado como técnico aceitável, a revisitar quando o provedor da Sprint 22 chamar `EmailBounces.register` por webhook (R-risco abaixo).
- Tarefas agendadas: as cinco novas (`ChargeNoticeJob`, `ReminderJob`, `AnnouncementSendJob`, `InvitePendingJob`, `MajorityNoticeJob`) têm `@SchedulerLock`.
- Excesso: nada que justifique achado. Quatro comentários `ponytail:` novos, todos com teto e caminho de troca (ver "Riscos").

#### Desvios e decisões herdados dos itens

Resumo por classe (o detalhe de cada uma está no arquivo do item, seção "Decisões" e "Desvios"):

| Item | Desvio ou decisão | Classe | Destino |
|---|---|---|---|
| #78 D3, D4 | "Cobrança gerada" sai até 10 dias antes do vencimento, ao pagador; sem link de pagamento | muda requisito | A3 |
| #78 D6, D7 | bounce marca o endereço na organização; recibo para endereço com problema devolve `NO_ADDRESS` | muda requisito | A3 |
| #78 D8 | `email_message.organization_id` sem RLS | técnico (com ressalva) | registrar; revisitar na Sprint 22 |
| #79 D1, D2 | motor dos lembretes por unidade; lembrete só no dia exato, sem recuperar dia perdido | muda requisito | A3 |
| #79 D3, D4, D5 | aviso de bloqueio só em unidade com conta conectada; SMS sempre até o provedor registrar abertura; envio manual de qualquer cobrança aberta, com `RECENT` de 24 h | muda requisito | A3, A4 |
| #80 D1, D2, D6 | seção dentro de Unidade; canal só e-mail; campos obrigatórios no `PUT` | muda requisito | A3 |
| #81 D1, D2 | leitura por aluno; quem vê e quem cancela; CA5 do texto contrariado | muda requisito | A3 |
| #81 D9 | cancelar apaga a linha | técnico | registrar |
| #82 D1, D2, D3, D5 | regras de destino, "pessoas" da prévia, modalidade exige unidade, limite de 20 por dia por unidade e por organização | muda requisito | A3 |
| #82 D7, D8 | comunicado do dia sem aula (um por linha de dia); cancelar aula não envia, o aviso sai por "Avisar turma" | muda requisito | A3 |
| #82 D9, D11, S2 | envio na requisição até 2.000 destinatários; imagem enviada antes do formulário e nunca limpa | técnico | A4 (limpeza), registrar |
| #83 D1, D2, D4 | catálogo fixo de nove modelos, texto por organização, `{descricao}` e `{link_pagamento}` | muda requisito | A3, A4 |
| #84 D1, D2, D3 | audiência por tipo, catálogo só dos tipos com fato, sem resumo diário | muda requisito | A3, A4 |
| #84 D5 a D8 | ação não marca como lida; consulta a cada 60 s; sem limpeza; fuso da unidade | técnico | registrar |
| #84 sugestão | convite expirado e não usado continua gerando aviso | exceção que exige o dono | D1 |
| #342 | "aluno ativo" é ter ao menos uma modalidade ativa; asserção nos dois ITs | técnico | fecha o R08-7 |
| #343 | imagem do Playwright fixada; conferência de versão por `sed` no `ci.yml`; CA4 em 1 de 5 | processo / decisão do dono | R09-7 |
| #344, #358 | `aguardar-ci.sh` com resultado recuperável; `4111` do carimbo de hora | técnico | registrar |
| #352 | só teste; causa é cobertura por acaso do agendador | técnico | R09-2 |

#### Ajustes propostos

**A1 · Barra superior a 360 px durante "Carregando unidades..."** (resolve R09-1) — antes do PR
- Tipo: novo item, no formato das #318 e #320 (medir antes e depois).
- Texto de issue proposto: título "Barra superior estoura 360 px enquanto as unidades carregam (`matricula.spec.ts:83` intermitente)"; etiqueta `front-end`; marco Sprint 09 (reaberto) ou o que o dono escolher, desde que antes do PR `dev`→`main`. O que entregar: a barra de `ManagementLayout` passa a caber em 360 px em todos os estados (carregando unidades, com sino com contador `99+`, com várias unidades, com "Minha conta" por extenso); o `UnitSwitcher` em carregamento ocupa no máximo a largura do estado carregado (esqueleto de largura fixa pequena ou sem texto) e o conjunto à direita usa `min-w-0`/`flex-wrap` ou esconde o rótulo de "Minha conta" abaixo de 400 px, sem tirar o alvo de 44 px. Regras: "funcionando a partir de 360 px". Pronto quando: [ ] a sonda de 40 cargas a 360 px dá `scrollWidth <= innerWidth` em 40 de 40; [ ] `npm run e2e` verde em 10 execuções seguidas; [ ] teste de tela (jsdom não mede layout) trocado por uma asserção do Playwright logo após `setViewportSize` e depois de esperar as unidades, para a barra ficar coberta; [ ] axe nos dois temas segue verde; [ ] registrar antes e depois (falhas por execuções) no arquivo do item.
- Como provar: `npx playwright test e2e/matricula.spec.ts --repeat-each=30` verde a 360 px; `npm run test:cov` e `npm run e2e` verdes.
- Tamanho: pequeno.

**A2 · Verificar a #352 depois do fato** (resolve R09-2) — antes do PR
- Tipo: corrigir no item 352 (registro, sem código novo).
- O que mudar e onde: acrescentar "Construção" (o que a T1 fez: um caso a mais em `ChargeNoticeIT`) e rodar a fase de verificação sobre o estado atual de `dev`, registrando `### Rodada 1 — OK` e "Entrega" em `specs/itens/352-cobertura-aviso-cobranca.md`. Material pronto: no clone limpo, `ChargeNoticeJob` e o pacote `billing` fecham com 0 linhas perdidas dentro do `verify` completo.
- Como provar: `./backend/mvnw -f backend/pom.xml verify -Dit.test=ChargeNoticeIT` com 0 linhas perdidas em `ChargeNoticeJob`; o arquivo com as seções que o processo exige.
- Tamanho: pequeno.

**A3 · Atualizar a especificação com as decisões da Sprint 09** (resolve R09-3) — antes do PR
- Tipo: ajuste de especificação (sem código), em PR próprio como o #337.
- O que mudar e onde: `13-comunicacao-e-notificacoes.md` (RN-COM-01, 02, 06, 07, 08, 09, 11, 12, 13, 14 e RN-NOT-01 a 04: quem vê a lista de comunicados e quem cancela; leitura por aluno; limite diário por unidade e por organização; comunicado do dia sem aula; "Avisar turma" no lugar de envio automático ao cancelar; audiência por tipo, "receber tudo" por vínculo; catálogo de nove modelos editáveis e campos; bounce e recibo parado; "pessoas" da prévia); `10-cobrancas.md` (RN-COB-17 a 20: lembrete no dia exato, aviso de bloqueio só com conta conectada, SMS contado por aluno e sem registro de abertura até o provedor, envio manual e `RECENT`); `06-turmas-e-agenda.md` (RN-AGE-10: o aviso do dia sem aula); `02-contas-e-acesso.md` se a permissão de enviar comunicado do Professor mudar o texto. Três linhas explícitas de diferença entre regra e produto: RN-COM-06 (editar agendado: "adiado, ver novo item"), RN-COM-02 ("Encerramento solicitado" entra com a Sprint 12) e RN-COB-17 (canais: só e-mail; desligar um lembrete é tirar o dia). Corrigir também o texto do CA5 do `81-comunicados-lista.md`.
- Como provar: `git diff docs/especificacao` mostra os trechos; nenhum código muda.
- Tamanho: pequeno a médio.

**A4 · Encaixes da Sprint 09 nas issues seguintes** (resolve R09-4) — antes de a Sprint 10 começar
- Tipo: ajuste de issue, mais um item novo. Blocos propostos (colar no fim do corpo, como na Sprint 08):
  - fim da #176 (Sprint 22): "Encaixe herdado da Sprint 09. Pronto quando: [ ] o webhook do provedor chama `EmailBounces.register` (marca o endereço 'Com problema', RN-COM-14, RN-ALU-10) e `EmailOpens.register` (`email_message.opened_at`), com teste de assinatura inválida e de isolamento; [ ] `email_message` com `organization_id` e sem RLS tem a leitura do webhook restrita ao contexto da organização da mensagem."
  - fim da #177: "Encaixe herdado da Sprint 09. Pronto quando: [ ] o SMS do aviso de bloqueio passa a respeitar 'o aluno não abriu os e-mails anteriores' (RN-COB-17, D4 da #79) com o registro real de abertura; [ ] o limite de 3 por aluno e mês (`sms_log`) segue valendo."
  - fim da #122 (Sprint 13): "Encaixe herdado da Sprint 09 (P3). Pronto quando: [ ] resumo diário por e-mail com o 'Precisa de atenção' e opção de desligá-lo por pessoa (RN-NOT-01), com teste."
  - fim da #93 (Sprint 10): "Encaixe herdado da Sprint 09. Pronto quando: [ ] abrir um aviso grava `announcement_read` por aluno (RN-COM-08; no menor, a leitura do responsável); [ ] 'Marcar todos como lidos' não grava; [ ] a contagem de leitores da lista de comunicados passa a ter dados."
  - issues que criam cada fato de notificação (#86, #87, #96 e as de pedido de encerramento, assinatura em atraso, Mercado Pago desconectado, aula experimental, autocadastro, exclusão de dados, turma sem professor): "Encaixe herdado da Sprint 09. Pronto quando: [ ] publica a notificação do tipo X por `Notifications.publish` depois do commit (RN-NOT-03, RN-NOT-04), com teste do destinatário e do e-mail urgente quando for o caso."
  - fim das issues de pagamento online da Sprint 11 e do convite do portal (#85/#149): "Pronto quando: [ ] `{link_pagamento}` dos lembretes e do e-mail de cobrança gerada passa a ter valor; [ ] as boas-vindas passam a sair (modelo `WELCOME` da #83)."
- Novo item: título "Editar comunicado agendado e limpar imagens de comunicado não usadas"; o que entregar: `PUT /announcements/{id}` para comunicado agendado (RN-COM-06: não mexe em enviado) com auditoria e o gatilho `announcement_sent_is_final` intacto; tarefa agendada que remove imagem `announcement-*` sem referência há mais de 24 h, com auditoria de sistema; regras: RN-COM-06; Pronto quando: [ ] agendado editado muda título, texto, destino, canais e horário e recalcula a prévia; [ ] enviado devolve 409; [ ] imagem referenciada nunca é removida; marco sugerido: Sprint 13 (junto da limpeza do A4 da Sprint 07, #335), a critério do dono.
- Também: acrescentar ao `specs/backlog.md` um bloco "Sprint 09, comunicação (#78 a #84)" na seção de encaixes, apontando para esses blocos.
- Como provar: `gh issue view` de cada uma mostra o bloco; `grep` por "Notifications.publish", "EmailBounces" e "RN-NOT-01" acha as issues.
- Tamanho: pequeno.

**A5 · Log sem a exceção inteira** (resolve R09-5) — pode seguir na Sprint 10
- Tipo: corrigir nos itens #78 e #82/#84.
- O que mudar e onde: trocar `LOG.error("… org={} …", …, e)` por `LOG.error("… org={} cause={}", …, e.getClass().getSimpleName())` em `ChargeNoticeJob.java:65`, `PlanWrite.java:195`, `AnnouncementSendJob.java:54` e `InvitePendingJob.java:61`; um teste de log por caminho que afirme que o endereço de e-mail não aparece (o `EmailRegistryIT` e o `ChargeNoticeIT` já têm captura).
- Como provar: `verify -Dit.test='ChargeNoticeIT,PlanRateNoticeIT,AnnouncementSendJobIT,InvitePendingJobIT'` verde com a asserção nova.
- Tamanho: pequeno.

**A6 · README e arquivo da #343** (resolve R09-6 e R09-7) — antes do PR
- Tipo: ajuste de documentação.
- O que mudar e onde: `README.md` tabela do `frontend`: `src/features/{billing,charges,payments,subscription,deactivation}`; `specs/itens/343-cache-playwright-ci.md`: acrescentar "Entrega" (o que mudou no `ci.yml`, a medição de um PR, a decisão do dono e o #363) e trocar o texto do CA4 para refletir "1 de 5 aceito; o resto no #363".
- Como provar: `git diff` dos dois arquivos.
- Tamanho: pequeno.

Ordem sugerida: A1, A2, A3, A6 (antes do PR `dev`→`main`); A4 (antes de a Sprint 10 começar, pode ser no mesmo PR de A3); A5 (na Sprint 10).

#### Decisões do dono

**D1 · Convite expirado e não usado continua gerando aviso no sino?** — recomendada: sim, manter — motivo: o aviso existe para o Administrador reenviar o convite (RN-NOT-04 fala em "convite não aceito em 7 dias") e um convite que expirou continua sem ser aceito; hoje ele leva à Configurações › Usuários, onde o reenvio acontece — alternativa: parar o aviso quando o convite expira, o que exigiria o `InvitePendingJob` filtrar por `expires_at` e a #84 ganhar o teste.

#### Ações do dono

1. Conferir as telas contra os canvases (o agente não conseguiu abri-los em nenhum item, D4 da #80, D8 da #81, D13 da #82, D8 da #83, D9 da #84): `cd frontend && npm run dev` e abrir `http://localhost:5173/entrar` (E-mail `ana@exemplo.com`, senha `segredo123`, o simulado MSW). Tema claro e escuro, largura 360 px (DevTools) e 1280 px. Telas: `/comunicados` (filtros, linha Agendado e Enviado, cancelar), `/comunicados/novo` (destino, tipo, prévia "para N pessoas", canais, agendar, imagem), `/configuracoes/textos` (editar, prévia, "Voltar ao texto da plataforma"), `/configuracoes/unidade` (cartão "Lembretes": interruptor, hora, dias), `/cobrancas/atrasados` (lista, "Enviar lembrete", WhatsApp), painel de uma cobrança (histórico de lembretes) e o sino na barra (contador, grupos Hoje e Esta semana, "Receber tudo" para a Coordenação). Olhar: alinhamento com o desenho, tom dos textos, contraste, alvo de toque de 44 px, rolagem horizontal.
2. Na barra superior, a 360 px, recarregar `/alunos/nova-matricula` algumas vezes e olhar se a página rola na horizontal por um instante (R09-1).
3. Apagar as branches locais já mergeadas e rodar `git fetch --prune` (S9).
4. Com o veredito `APROVAR` e os ajustes A1, A2, A3 e A6 resolvidos: abrir o PR de `dev` para `main` com o método "Merge" (nunca squash) e, depois de revisar, dizer "sim" para liberar a Sprint 10.

#### Limitações e o que não foi conferido

- Layout contra o canvas, contraste real nos dois temas e foco/teclado em navegador real: NÃO CONFERIDO; por proxy (jsdom e axe) PASSOU. Os canvases estavam inacessíveis ao agente em todos os itens.
- gitleaks local: NÃO CONFERIDO (sem binário nesta máquina); o job `security` do CI passou em todos os PRs.
- `e2e` dentro da imagem do Playwright do CI: NÃO CONFERIDO aqui (rodei o Chromium local no Windows); a medição do CA4 para o PR #361 foi lida do run `40087704834`.
- Segredos de publicação e hospedagem: NÃO CONFERIDO (limitação aceita, até a #192).
- Envio real de e-mail e SMS, abertura de e-mail, retorno de e-mail e push: NÃO CONFERIDO (limitação aceita: conexões reais só nas Sprints 22 e 23); tudo prova contra a porta simulada.
- Notas de versão: NÃO CONFERIDO (não existe arquivo, RF-AJU-04 é da Sprint 21).
- O nome do segundo teste que falhou na execução com duas falhas do `e2e` não foi lido (a saída foi resumida); as outras seis execuções vermelhas ou verdes foram todas em `matricula.spec.ts:83`.

#### Riscos para a próxima sprint

- Sprint 10 (portal): lê `announcement_read` (a #93 precisa gravar a leitura por aluno; `AnnouncementsIT.reads` prova a contagem), desenha o formato de texto do comunicado (`**negrito**`, `- lista`, `[texto](https://…)`, D6 da #82), mostra o comunicado fixado até `pinned_until`, manda as boas-vindas pelo convite do portal (modelo `WELCOME` pronto) e herda a barra superior (R09-1) para o menu do portal. O #363 (CA4 do #343) conta 4 PRs que rodem o `e2e`.
- Sprint 11 (pagamentos online): `{link_pagamento}` sai vazio hoje; "Pagamento recusado" e "Comprovante para conferir" precisam publicar notificação; o aviso de bloqueio e o número "dias até o bloqueio" só aparecem com conta conectada (D3 da #79).
- Sprint 12 (inativação): "Encerramento solicitado" entra na lista `RECEIVING` do `AnnouncementRecipients` (S1 da #82) e o pedido de encerramento publica a notificação urgente.
- Sprint 13: o resumo diário (P3) e a limpeza de imagens e de comprovantes.
- Sprint 22: `EmailBounces.register` e `EmailOpens.register` sem rota; `email_message` sem RLS; a prévia de remetente no domínio da plataforma (RN-COM-10); a regra do SMS por e-mail não aberto.
- Dívidas com prazo: `osv-scanner.toml` vence em 2027-01-15; `ponytail:` do modelo de termo fixo (RF-PNL-07); quatro `ponytail:` novos (falha do fornecedor não repetida; janela e periodicidade fixas do aviso; dia perdido dos lembretes não recuperado; "unidade mais antiga" por nome em `Notifications.list`); o repositório volta a ser privado antes da Sprint 22 (trava do `AGENTS.md`).
- Nenhuma issue `depende de você` trava a Sprint 10.
