# Revisão da Sprint 00 · Fundação

### Rodada 1 — AJUSTAR — 2026-10-15, estado 1fa305419a1e139dfac1f5f4774debcc0abd3f94

#### Resumo e veredito

A Sprint 00 entregou o monorepo, o ambiente local, o back-end base (Spring Boot, Flyway, erros, OpenAPI sem rotas, agendador, logs, métricas, portas simuladas), o front-end base (tema, textos, componentes, formatos), a esteira de CI, as imagens com publicação preparada e o script de dados fictícios. As 12 issues do marco estão fechadas, cada uma por PR de squash com `Closes #N`, e todos os itens têm rodada de verificação `OK`.

Veredito: **AJUSTAR**, sem bloqueio. Três ajustes pequenos devem entrar antes do PR `dev` para `main`: um teste do back-end que fica vermelho conforme o caminho do clone (e uma métrica que expõe o caminho do disco), a documentação que ficou desatualizada e a decisão do dono sobre a regra de `main` (o `ci-gate` não é exigido nela). O resto pode seguir na Sprint 01.

O dono precisa decidir 4 pontos (seção "Decisões do dono") e olhar a aplicação no navegador (seção "Ações do dono").

#### Estado revisado

| | |
|---|---|
| `dev` | `1fa305419a1e139dfac1f5f4774debcc0abd3f94` (igual a `origin/dev`, pasta limpa) |
| `main` | `d6dbff156a0d02232189e5a414f7d738f185e74c` (só o commit inicial do README; nada da sprint foi para `main`) |
| Ferramentas | Java 21.0.12.1, Node v24.21.0, Docker 29.8.1 (Windows 11, Docker Desktop) |
| Diff | `git diff origin/main...origin/dev --stat`: 206 arquivos, 17.671 inserções, 1 remoção |
| PRs da sprint | #194 (#1), #195 (#2), #196 (#3), #197 (#4), #198 (#5), #199 (#6), #200 (#7), #201 (#8), #202 (#9), #203 e #208 (#10), #209 e #213 (#11), #214 (#12) |
| Pré-condições | `dev` limpa e igual a `origin/dev`; sem branch `item/*` (só `dev` e `main` no remoto); sem PR aberto; 12 issues fechadas e 12 linhas "feito" no `specs/backlog.md`; nenhuma issue de outro marco fechada; `gh auth` válido; Docker ativo. Issues `depende de você` do marco: nenhuma (as 18 abertas são das Sprints 22 a 25) |

#### Trilha de processo

Todos os PRs: `dev` como base, um commit (squash), título `#N: <título da issue>`, `Closes #N`. Itens 1 a 9 entraram antes de existir CI (sem `ci-gate` no PR, o que o processo aceita). `ci-gate` verde em #203, #208, #209, #213 e #214 (conferido com `gh pr view`). Todos os arquivos de item têm as seis seções, "Construção", uma `### Rodada 1 — OK`, "Entrega" e todas as tarefas marcadas (`grep` de `- [ ]` dá 0 em todos).

| Item | PR | Verificação | Entrega | Observação |
|---|---|---|---|---|
| #1 Monorepo e ambiente local | #194, squash, sem CI (anterior à #10) | Rodada 1 OK | sim | clone limpo não repetido na época; repetido por esta revisão |
| #2 Back-end Spring Boot | #195, squash, sem CI | Rodada 1 OK | sim | |
| #3 OpenAPI | #196, squash, sem CI | Rodada 1 OK | sim | |
| #4 Agendador | #197, squash, sem CI | Rodada 1 OK | sim | |
| #5 Logs e métricas | #198, squash, sem CI | Rodada 1 OK | sim | |
| #6 Portas simuladas | #199, squash, sem CI | Rodada 1 OK | sim | |
| #7 Front-end base | #200, squash, sem CI | Rodada 1 OK | sim | |
| #8 Componentes base | #201, squash, sem CI | Rodada 1 OK | sim ("Refs #8" no texto da Entrega; o PR traz `Closes #8`) | branch `item/8-componentes-base`, mas o texto da especificação cita `item/8-estados-de-tela` |
| #9 Formatos e WhatsApp | #202, squash, sem CI | Rodada 1 OK | sim | |
| #10 Esteira de CI | #203 (código) e #208 (registro), squash, `ci-gate` verde | Rodada 1 OK, **depois** do merge do código | sim | **dois PRs; código entrou em `dev` antes da verificação** (R00-2) |
| #11 Branches e publicação | #209 (código) e #213 (registro), squash, `ci-gate` verde | Rodada 1 OK, **depois** do merge do código | sim | **dois PRs; código entrou em `dev` antes da verificação** (R00-2) |
| #12 Dados fictícios | #214, squash, `ci-gate` verde | Rodada 1 OK | sim | |

Provas de CI que a verificação aceitou "por registro", reconferidas agora com `gh pr view`: #204 (segredo falso) `security` e `ci-gate` em falha; #205 (teste do back-end quebrado) `backend` e `ci-gate` em falha; #206 (teste do front quebrado) `frontend` e `ci-gate` em falha; #207 (só `docs/`) `backend` e `frontend` pulados e `ci-gate` verde; #211 (Dockerfile quebrado) `ci-gate` em falha; #212 (só `frontend/`) `backend` pulado e `ci-gate` verde. Todos fechados, sem branch remoto sobrando.

#### "Pronto quando"

**Do marco** (descrição do marco `Sprint 00 · Fundação`, 6 bullets)

| Bullet | Status | Evidência |
|---|---|---|
| Um PR com teste quebrado, cobertura abaixo de 80% ou segredo no código é barrado pelo CI | PASSOU (cobertura só pelo mecanismo) | Teste quebrado: #205 e #206, `ci-gate` em falha. Segredo: #204, `security` em falha. Cobertura: meu `verify` imprimiu "All coverage checks have been met" (regra JaCoCo ativa, `pom.xml` com 0,80 de linhas) e o `vite.config.ts` tem `coverage.thresholds`; o caso negativo (mínimo subido, "Rule violated") está só no registro do item #10, **não reproduzi** |
| Uma tela de exemplo passa no axe nos dois temas e funciona a 360 px | PASSOU por proxy; navegador real parcial (R00-3) | `npm run test:cov` no clone limpo: 19 arquivos, 87 testes verdes, incluindo `a11y.test.tsx` (axe em jsdom, `color-contrast` desligado, contraste por teste de tokens, 360 px por ausência de largura fixa). Prova extra minha, fora do repositório: Playwright + axe-core reais em `npm run dev`, tela `/`, temas claro e escuro, 360 e 1280 px: 0 violações em todas as combinações e `scrollWidth` igual à largura (sem rolagem horizontal). A `Showcase` (componentes) não tem rota e continua só por proxy |
| Um merge em `dev` publica sozinho no ambiente de teste | NÃO CONFERIDO (limitação aceita) | Não há hospedagem até a Sprint 22. O fluxo está pronto: `publish.yml` rodou em `push` para `dev` (runs 38793065504, 38794199930, 38795722148, todos success) e em `workflow_dispatch` (38793147139), construiu a imagem e gravou "publicação pulada: faltam chaves"; `gh secret list` e `gh variable list` vazios. Transferido para a #192 ("`dev` e `main` publicam sozinhos") |
| A restauração de um backup do ambiente de teste foi feita e cronometrada | NÃO CONFERIDO (limitação aceita) | Sem hospedagem nem backup. Está na #192 ("Backup diário ligado e uma restauração feita com sucesso", RNF-17), mas a #192 não diz "cronometrada" (ver A6) |
| Um e-mail disparado no ambiente de teste aparece na Caixa de saída, e nenhum módulo de regra importa código de fornecedor | PASSOU no que existe; "ambiente de teste" NÃO CONFERIDO (limitação aceita) | `SimulatedMessagingTest.emailSmsAndPushLandInTheOutbox` e `OutboxTest` verdes no meu `verify` (Caixa em memória, sem tela nem rota; a tela é de item futuro). Mailpit do compose recebe SMTP (item #1). Fornecedor: `NoVendorNamesTest` verde e meu `grep` (Mercado Pago, Twilio, SendGrid, Turnstile, Cloudflare, Firebase, Sentry, Stripe etc.) em `backend/src`, `frontend/src`, `.github`, `infra/scripts`, `pom.xml` sem achado |
| Todos os itens do "Pronto quando" geral valem para cada item desta sprint | PASSOU com ressalvas | Tabela abaixo. Ressalvas: R00-3 (navegador real) e R00-5 (códigos sem teste rotulado) |

**De todo item (plano de sprints)**

| Regra | Status | Evidência ou "não se aplica nesta sprint: motivo" |
|---|---|---|
| Regras citadas têm teste; cobertura não caiu | PASSOU, com rastreabilidade parcial | Cobertura medida por mim: back-end 282 de 295 linhas (95,59%), front-end 96,86% de linhas (95,25% de instruções), igual ao registrado no item #10. Busca dos códigos das issues nos testes: RNF-04, 07, 11, 23, 24, 29, 30, 32, 35, 36, 37, 39, 40, 41 e 42 aparecem em testes ou configuração; RNF-16 e 18 foram para a #193 e RNF-17 para a #192 (declarado nas issues). RNF-19, 20, 21, 25, 26, 27, 33 e 34 existem como teste, mas sem o código escrito no teste (R00-5) |
| Cobertura 100% nas regras críticas | não se aplica nesta sprint: não existe pacote de cobrança, pagamento, assinatura, inativação nem isolamento | A regra de 100% por pacote está configurada no `pom.xml` e no `vite.config.ts`, mas sem efeito até haver classes; nomes de pacote são suposição (#10 D4) |
| Toda rota nova tem teste de isolamento entre organizações e entre perfis | não se aplica nesta sprint: `docs/openapi.yaml` tem `paths: {}` e o back-end não tem controlador de negócio | `organization_feature` e `FeatureFlagsIT.isolation` cobrem só o filtro por organização no código; RLS dessa tabela é da #14 |
| OpenAPI e MSW batem com a implementação | não se aplica nesta sprint: sem rotas; verificado o que existe | `OpenApiContractTest` verde (erro e página reais contra o schema); `msw.test.ts` verde com lista base de handlers vazia, como o contrato |
| Toda ação que altera dado grava auditoria | não se aplica nesta sprint: nenhuma ação de usuário altera dado; o serviço é da #17 | `FeatureFlags.setEnabled` não tem chamador nem rota; a rota da Sprint 15 deve auditar (item #4 D7) |
| Quatro estados, 360 px, axe nos dois temas | PASSOU por proxy (ver bullet do marco) | `states.test.tsx`, `screen-states.test.tsx`, `a11y.test.tsx` (Showcase e janelas abertas); 360 px real só na tela `/` |
| Textos nos arquivos de tradução | PASSOU | `npm run lint` (regra `i18next/no-literal-string`) sem erro no clone limpo; `tone.test.ts` verde |
| Recurso novo nasce desligado por organização | PASSOU | `FeatureFlagsIT.defaultOff` e `toggle` verdes (sem linha = desligado) |
| Notas de versão | não se aplica nesta sprint: nada visível para as academias | |

#### Clone limpo

Clone de `origin/dev` (SHA acima) em pasta nova. O `name: ctcore` fixo do compose faria um segundo clone reaproveitar contêineres e volumes do ambiente do dono; por isso usei `COMPOSE_PROJECT_NAME=revisao` e `.env` com portas 25432, 28333, 21025 e 28025 (a 5432 estava ocupada pelo PostgreSQL do dono, que não foi tocado e continuou de pé).

| Passo | Comando | Resultado real |
|---|---|---|
| ambiente | `docker compose up -d --wait` | 3 serviços saudáveis em 22 s (postgres 16.4, storage, mailbox) |
| back-end | `./backend/mvnw -f backend/pom.xml verify` | **primeiro clone (caminho longo com UUID): FALHOU** 1 de 65 testes rápidos (`ActuatorEndpointsTest.prometheus`, ver R00-1). **Segundo clone, caminho curto: BUILD SUCCESS**, 65 testes rápidos, 20 de integração, 0 falhas, cobertura de linhas 95,59%, "All coverage checks have been met", cerca de 1 min 40 s |
| front-end | `npm ci`, `lint`, `typecheck`, `test:cov`, `build`, `e2e` | todos com código 0: `npm ci` 42 s, `test:cov` 19 arquivos e 87 testes, cobertura 95,25% (instruções), 96,86% (linhas), `build` 15 s, `e2e` 1 teste Chromium em 8,5 s. O `e2e` rodou sem eu instalar o Chromium; a máquina pode já tê-lo, então "primeira vez" fica NÃO CONFERIDO |
| `npm audit --audit-level=high` | | 0 vulnerabilidades |
| imagens | `docker build` das duas, `docker run` | back-end: ok (cache de camadas), uid 10001 (`ctcore`), `GET :8081/actuator/health/readiness` 200 `{"status":"UP"}` com o banco, `:8081/actuator/env` e `/metrics` 404, `:8080/actuator/health` 404. Front-end: uid 101 (nginx), `/` 200, `/qualquer` 200, `/api/v1/x` 404 |
| seed (2x) | `bash infra/scripts/seed.sh` | 2 execuções com sucesso; contagem igual: 3 organizações, 8 linhas em `organization_feature` (5 ligadas); Flyway V1 e V2 com `success = t`. Precisei subir o back-end antes (pela imagem) para migrar |
| recomeço | `down -v` e `up -d --wait` | 3 serviços saudáveis de novo e `organization_feature` não existe (sem estado escondido) |

Comparação com a linha de base (os números dos arquivos de item): igual. 65 testes rápidos e 20 de integração (item #12), 87 testes de front (item #9 e #10), cobertura 282/295 e 96,86% (item #10). Nada piorou.

Passos do README da raiz que não bastaram (R00-4): (a) não existe seção de back-end com o comando de subir (`spring-boot:run -Dspring-boot.run.profiles=local`), mas o seed exige o back-end rodado uma vez; só o `AGENTS.md`, o `backend/README.md` e a mensagem de erro do `seed.sh` dizem como; (b) o `npm run e2e` do bloco "Local, o equivalente" não cita `npx playwright install chromium`; (c) o README manda copiar `.env.example` para mudar portas, mas não avisa que o nome fixo `ctcore` do compose faz dois clones dividirem contêineres e volumes.

Desfeito: contêineres, volumes, imagens e rede do projeto `revisao` removidos; clones temporários removidos ao final.

#### CI e proteções

| Item | Estado real | Evidência |
|---|---|---|
| `ci-gate` exigido em `dev` | sim | `gh api repos/DevDosAnjos/ctcore/branches/dev/protection`: `required_status_checks.contexts = ["ci-gate"]`, `strict: false`, `allow_force_pushes: false`, `allow_deletions: false`, **`enforce_admins: false`**, sem revisão obrigatória |
| Regra de `main` | ruleset `protege-main` (id 25019105, ativo, sem bypass): bloqueia exclusão e push não fast-forward; PR obrigatório só com método `merge`, 0 aprovações. **Não exige `ci-gate`** | `gh api .../rulesets/25019105`. Casa com o `AGENTS.md` (Merge, nunca squash); falta o status exigido (D1) |
| Métodos de merge do repositório | merge, squash e rebase liberados; branch não apaga sozinho | `gh repo view --json ...`; o que força squash em `dev` e Merge em `main` é só a regra do agente (e o ruleset em `main`) |
| Últimos runs em `dev` | `publish.yml`: 38795722148 (1fa3054), 38794199930 (05e13fa), 38793147139 (dispatch), 38793065504, todos success. `ci.yml` só roda em PR; os PRs #203, #208, #209, #213 e #214 estão verdes | `gh run list --branch dev` |
| `permissions:` e pinagem | `permissions: contents: read` nos dois workflows; ações fixadas por **tag** de versão (checkout v7.0.1, setup-java v6.1.0, setup-node v7.1.0, upload-artifact v7.0.2), não por SHA; sem segredo em evento de PR; `publish.yml` só em `push` e dispatch | leitura dos arquivos |
| `ci-gate` | reprova `failure` e `cancelled`; aceita qualquer outro resultado, inclusive `skipped` em `security` | `ci.yml` job `ci-gate` (R00-6) |
| gitleaks e OSV | gitleaks 8.30.1 e osv-scanner v2.7.0 baixados com `curl` sem conferir checksum; filtro de severidade por `jq` conta como 0 o que não tem nota CVSS numérica; sem `.gitleaks.toml` (sem exceções) | `ci.yml` job `security` (R00-6) |
| Exceções de vulnerabilidade | 2, em `osv-scanner.toml`: `GHSA-j9f9-w8pj-32f8` (SSE com fragmentos, não usado) e `GHSA-pc63-qcmh-9cmg` (XsltView, não usada), ambas Spring Framework 6.2 sem correção fora do Boot 4, **ignoreUntil 2027-01-15**, com motivo | `osv-scanner.toml` (D2) |
| Segredos e variáveis do fluxo de publicação | `PUBLISH_REGISTRY` (variável), `PUBLISH_REGISTRY_USER` e `PUBLISH_REGISTRY_TOKEN` (segredos): **nenhum existe**; o fluxo pula o envio e termina verde | `gh secret list`, `gh variable list` vazios |
| Dependências | 35 de 35 do `package.json` com faixa `^`/`~` (o `package-lock.json` fixa); Maven por BOM do Boot, `bcprov` fixado em 1.86, ShedLock 6.10.0, parser e validador só em escopo `test`; sem `dependabot.yml` | `package.json`, `pom.xml` |

#### Achados

Ordenados por gravidade.

**AJUSTAR**

- **R00-1 · AJUSTAR · teste e segurança** — local: `backend/src/test/java/br/com/ctcore/monitoring/ActuatorEndpointsTest.java` (`prometheus`, linha 70) e métrica `disk_free_bytes`/`disk_total_bytes`.
  - esperado: a suíte passa em qualquer pasta; as métricas não expõem dado de ambiente (o teste existe justamente para provar que `/actuator/prometheus` não vaza UUID, e-mail nem CPF).
  - observado: no primeiro clone (pasta dentro do scratchpad, com um UUID no caminho) `verify` falhou: `doesNotContainPattern("[0-9a-f]{8}-...")` achou o UUID dentro de `disk_free_bytes{path="C:\\Users\\...\\<uuid>\\...\\clone\\backend\\."}`. No clone de caminho curto passou. No contêiner a métrica mostra `path="/app/."`. O Actuator publica o caminho absoluto da pasta de trabalho.
  - evidência: `target/surefire-reports/br.com.ctcore.monitoring.ActuatorEndpointsTest.txt` do primeiro clone; segundo clone, `Tests run: 65, Failures: 0` e `Tests run: 20, Failures: 0`.
  - efeito: o teste fica vermelho para quem clonar numa pasta cujo caminho contenha um UUID ou algo que case com o padrão (o CI do GitHub não, por isso o `ci-gate` ficou verde); a métrica revela o caminho de instalação na porta de gestão.
  - proposta: A1.

- **R00-2 · AJUSTAR · processo** — local: itens #10 (PRs #203 e #208) e #11 (PRs #209 e #213), `specs/itens/10-esteira-ci.md` e `11-...md`, seção "Entrega".
  - esperado: `AGENTS.md`: "um único PR" por item; fluxo do kit: verificar antes de entregar.
  - observado: nos dois itens o código entrou em `dev` por um primeiro PR (#203 às 18:16Z, #209 às 19:20Z) antes da verificação, e um segundo PR "de regularização" (#208 às 18:26Z, #213 às 19:28Z) registrou verificação e entrega. O item #10 diz "autorizada pelo dono"; o #11 não registra autorização. Motivo plausível: a prova de CI exigia um PR com o workflow já em `dev` (T6 do #10 e `workflow_dispatch` do #11), e o agente fez PRs descartáveis (#204 a #207, #210 a #212).
  - evidência: `gh pr list --state merged`, `git log --format='%h %p %s'` (cada merge com um pai só).
  - efeito: quebra a regra de um PR por item e deixa a verificação posterior ao merge; hoje todos têm rodada OK, então não bloqueia. O risco é virar hábito em itens que mexem em CI.
  - proposta: A5.

- **R00-3 · AJUSTAR · teste** — local: `specs/itens/7` D8, `8` D6, `9` D7, `10` D5; `frontend/e2e/smoke.spec.ts`, `frontend/playwright.config.ts`.
  - esperado: os itens #7, #8 e #9 adiaram para a #10 "a conferência em navegador real" (axe com contraste, 360 px, 44 px, zoom).
  - observado: a #10 entregou um único teste `@critical` (abre o app e troca o tema) em Chromium Desktop. Não há axe, largura de 360 px nem zoom em navegador real. Ninguém ficou com a promessa. Como prova extra, rodei fora do repositório Playwright e axe-core reais na tela `/`: 0 violações nos dois temas, 360 e 1280 px, sem rolagem horizontal. Mas `/` é a única tela com rota; a `Showcase` e os componentes só passam em jsdom.
  - evidência: `frontend/e2e/smoke.spec.ts` (um teste); saída do meu script (`light 360 ... violations= []`, idem `dark`).
  - efeito: contraste real, alvos de 44 px e 360 px dos componentes seguem sem prova até uma tela real aparecer (Sprint 02).
  - proposta: A3.

- **R00-4 · AJUSTAR · especificação** — documentação que o código contradiz ou que não reproduz o ambiente:
  - `specs/projeto.md` linha 3: "o repositório tem só documentos, sem código" (já há back-end, front-end, CI).
  - `specs/backlog.md` linha 3: "193 issues, nenhuma fechada" (12 fechadas).
  - `specs/itens/11-...md`: CA2 e T2 dizem `curl localhost:8080/actuator/health`; a imagem expõe a gestão em 8081 (registrado como desvio, mas o critério não foi corrigido).
  - `specs/itens/8-...md`: cita o branch `item/8-estados-de-tela` e arquivos (`SidePanel.test.tsx`) que não existem; `specs/itens/1-...md` D1 ainda diz MinIO (o desvio para SeaweedFS está registrado).
  - `README.md` da raiz: sem passo de back-end, sem instalação do Chromium para o `e2e`, sem aviso de `COMPOSE_PROJECT_NAME` (ver "Clone limpo").
  - proposta: A2.

- **R00-6 · AJUSTAR · CI** — local: `.github/workflows/ci.yml` jobs `security` e `ci-gate`.
  - esperado: a esteira barra o que o plano promete (RNF-35) e não depende de download sem verificação.
  - observado: (a) `gitleaks` e `osv-scanner` são baixados por `curl` e executados sem checar o hash; (b) o filtro `jq` do OSV usa `(.max_severity | tonumber? // 0) >= 7`, então um aviso sem nota CVSS numérica passa em silêncio; (c) `ci-gate` só reprova `failure` e `cancelled` (já registrado como sugestão na verificação do #10); (d) ações fixadas por tag, não por SHA; (e) sem `dependabot.yml`.
  - evidência: `ci.yml` linhas do job `security` e `ci-gate`.
  - efeito: superfície de cadeia de suprimentos e um furo possível no portão de vulnerabilidade, sem efeito imediato (repositório privado, sem segredos de produção).
  - proposta: A4.

**DONO**

- **R00-7 · DONO · CI** — `protege-main` não exige `ci-gate`. Ver D1.
- **R00-8 · DONO · segurança** — exceções OSV até 2027-01-15. Ver D2.
- **R00-9 · DONO · operação** — critérios do marco que o ambiente não prova (publicar em `dev` e backup cronometrado). Ver D3.

**SUGESTÃO**

- **R00-10 · SUGESTÃO · segurança** — `SecurityConfig` libera `/actuator/**` (`permitAll`) e o tira da exigência de HTTPS também na porta principal; hoje responde 404 só porque o Actuator usa outra porta (confirmei: `:8080/actuator/health` 404, `:8081` 200). Se a hospedagem (#192) juntar as portas, abre sem autenticação. Item #5 D8 já registra; a #192 deve deixar a porta 8081 só na rede interna.
- **R00-11 · SUGESTÃO · segurança** — `application.yml` traz senha e usuário do banco de desenvolvimento como valor padrão (`${POSTGRES_PASSWORD:ctcore_dev}`) também fora do perfil `local`; em produção, variável esquecida tenta a senha de dev em vez de falhar. Propor, na #192, falhar sem variável fora do perfil `local`.
- **R00-12 · SUGESTÃO · segurança** — `frontend/nginx.conf` sem cabeçalhos de segurança (CSP, `X-Content-Type-Options`, `Referrer-Policy`, `frame-ancestors`); imagens base por tag flutuante (`eclipse-temurin:21-jdk`, `node:24-alpine`, `nginxinc/nginx-unprivileged:stable-alpine`) e não por versão ou digest.
- **R00-13 · SUGESTÃO · segurança** — com `forward-headers-strategy: framework` o HTTPS vem de `X-Forwarded-Proto`; se a aplicação ficar alcançável sem o proxy, o cabeçalho pode ser forjado. Só a #192 pode garantir que o proxy é o único caminho.
- **R00-14 · SUGESTÃO · API** — `PaymentGateway` desligado lança `IllegalStateException` nas operações e `ChallengeVerifier` desligado devolve `true`; RNF-41 pede "sem erro para quem usa" e o "Sem esta conexão". Decisão registrada no item #6 (D3); os itens de cada funcionalidade devem consultar `isEnabled()`. Vale lembrar na Sprint 11 e nas de formulário público.
- **R00-5 · SUGESTÃO · teste** — os testes de RNF-19, 20, 21, 25, 26, 27, 33 e 34 não trazem o código no `@DisplayName`/`describe` (os de RNF-23, 29, 30, 32, 42 trazem). Convenção para os itens seguintes: citar o código no nome do teste; ajuda a varredura de rastreabilidade e a revisão.
- **R00-15 · SUGESTÃO · teste** — `Thread.sleep(10)` em `FeatureFlagsIT:43` e `Thread.sleep(2500)` em `SchedulerDisabledTest:50` (este é espera negativa; aceitável); `SchedulerLockIT` prova o `LockProvider`, não o `@Scheduled` em duas instâncias.
- **R00-16 · SUGESTÃO · front** — a busca da `QueryPage` faz uma requisição e um `pushState` por tecla (já apontado na verificação do #8); `frontend/package.json` sem `engines` para Node 24; "há N dias" sem plural do i18n.
- **R00-17 · SUGESTÃO · dados** — a regra de 100% nos pacotes `billing`, `payment`, `subscription`, `deactivation`, `isolation` e nas pastas `src/features/...` é sem efeito hoje (nenhuma classe casa). Quando o primeiro desses pacotes surgir, conferir no PR que o padrão do `pom.xml` o pega (o item #10 provou o padrão com `br.com.ctcore.integration`).

#### Desvios e decisões herdados dos itens

| Item | Desvio ou decisão | Classe | Destino |
|---|---|---|---|
| #1 | MinIO indisponível; SeaweedFS `3.80` sem autenticação, só em 127.0.0.1 | técnico aceitável (exceção de segurança só de desenvolvimento) | registrar; #16 define bucket e credencial; texto D1 da especificação desatualizado (A2) |
| #2 | D7: CSRF em toda escrita e cookie `SameSite=Lax` (muda comportamento) | muda comportamento, já na especificação do item | registrar |
| #2 | D6: HTTPS e HSTS na própria aplicação confiando em `X-Forwarded-Proto` | técnico aceitável | R00-13 |
| #2 | `XSRF-TOKEN` só sai quando o token é lido; o login (#18) deve garantir | sugestão sem dono | dívida para a #18 |
| #3 | D6: `page` a partir de 1 e `size` só 25, 50 ou 100, 400 se inválido (muda comportamento) | muda comportamento, já registrada | registrar; as telas (#8 `QueryPage`) usam o mesmo contrato |
| #3 | `Page.items` aberto; cada rota compõe | sugestão | dívida; cada rota declara `items` |
| #4 | D5: recurso sem linha = desligado; `organization_feature` sem FK nem RLS; sem auditoria (D7) | técnico aceitável com prazo | #13 (FK) e #14 (RLS); rota da Sprint 15 audita |
| #4 | ShedLock 6.10.0 (Spring Boot 3.5), provas no `LockProvider` | técnico aceitável | registrar |
| #5 | D3: mascarar o valor inteiro (`[REDACTED]`), D5: só `health` e `prometheus`, D8: porta de gestão 8081 (mudam comportamento, registrados) | muda comportamento, já na especificação | registrar; R00-10 |
| #5 | `/actuator/**` `permitAll`; sem `organizationId` no MDC até a #14 | exceção que exige atenção do dono na #192 | R00-10; #14 acrescenta o campo |
| #6 | D2: `real` recusado na subida até as Sprints 22 e 23; D3: porta desligada neutra, exceto `PaymentGateway` (lança) | muda comportamento, registrado | registrar; R00-14; Sprint 22 precisa trocar a recusa de `real` |
| #6 | D4: Caixa de saída em memória, 1.000 mensagens | técnico aceitável | dívida para quem criar a tela da Caixa |
| #7 | D8, #8 D6, #9 D7: navegador real adiado para a #10 | muda critério de prova, mas a #10 não cumpriu | R00-3 / A3 |
| #7 | Handlers MSW vazios (OpenAPI sem rotas); tokens em hexadecimal; chave de tradução ausente só lança fora de produção | técnico aceitável | registrar |
| #8 | D4: confirmação digitada é opção do componente; as quatro ações de RNF-26 serão ligadas nas telas | técnico aceitável | itens de tela das ações |
| #8 | `testTimeout: 20000` no Vitest | técnico aceitável | registrar |
| #9 | D3: "há N dias" de 2 a 30 dias (suposição, muda comportamento), D6: WhatsApp desligado pela plataforma por contexto React ainda sem fonte | muda comportamento, registrado como suposição | o primeiro item que expuser a configuração liga o contexto; confirmar o limite de 30 dias com o dono quando houver tela |
| #10 | D4: 100% por pacote configurado sem efeito; D7: `ci-gate` só trava de verdade se exigido na proteção | muda critério, registrado | R00-17; D7 atendido em `dev` (R00-7 para `main`) |
| #10 | Exceções OSV até 2027-01-15; Spring Boot 3.5 | exceção que exige o dono | D2 |
| #10 | Código entrou antes da verificação (#203) | processo | R00-2 |
| #11 | Health do back-end em 8081, não 8080 (CA2) | técnico aceitável, critério a corrigir | A2 |
| #11 | `publish.yml` pula o envio sem as três chaves; sem `latest`, etiquetas branch e SHA; registro ainda não escolhido | técnico aceitável | #192 |
| #12 | Seed só com `organization_feature` (a tabela da organização é da #13); volume de referência fica para a Sprint 13 | técnico aceitável, previsto na issue | #13 acrescenta o seu `infra/seed/NN-nome.sql` |

#### Ajustes propostos

**A1 · Teste do Prometheus independente do caminho e sem expor o disco** (resolve R00-1) — antes do PR
- Tipo: corrigir no item #5.
- O que mudar e onde: em `application.yml`, `management.metrics.enable.disk: false` (ou excluir `disk.free` e `disk.total`), e em `ActuatorEndpointsTest.prometheus` manter as três varreduras. Se o dono quiser manter as métricas de disco, filtrar as linhas `disk_*` antes do `doesNotContainPattern`.
- Como provar: `./backend/mvnw -f backend/pom.xml verify` num clone cujo caminho contém um UUID (como o do scratchpad); `curl :8081/actuator/prometheus | grep -c disk_` dá 0.
- Tamanho: pequeno.

**A2 · Atualizar texto desatualizado** (resolve R00-4) — antes do PR
- Tipo: ajuste de especificação e documentação.
- O que mudar e onde: `specs/projeto.md` linha 3 (estado atual: monorepo com back-end, front-end, CI e imagens; sem hospedagem); `specs/backlog.md` linha 3 (12 issues fechadas, a Sprint 00 inteira "feito"); `specs/itens/11-...md` CA2 e T2 (health na 8081); `specs/itens/8-...md` (branch e arquivos de teste); `specs/itens/1-...md` D1 (SeaweedFS no lugar de MinIO); `README.md` da raiz: seção de back-end com `./backend/mvnw -f backend/pom.xml spring-boot:run -Dspring-boot.run.profiles=local`, `npx playwright install chromium` antes do `npm run e2e`, e uma linha sobre `COMPOSE_PROJECT_NAME` quando houver mais de um clone.
- Como provar: reler o README seguindo só ele num clone novo.
- Tamanho: pequeno.

**A3 · Prova em navegador real da base visual** (resolve R00-3) — depois do PR, na Sprint 01 ou antes da primeira tela da Sprint 02
- Tipo: novo item.
- Texto de issue proposto. Título: "Playwright: axe, 360 px e alvos de toque em navegador real". O que entregar: rota de desenvolvimento (só `import.meta.env.DEV`) que monta a `Showcase`; teste `@critical` que abre a `Showcase` e a tela `/` nos temas claro e escuro, a 360 e a 1280 px, roda axe-core com `color-contrast` ligado e falha em violação, confere `scrollWidth <= largura`, tamanho mínimo de 44 px nos alvos de toque e zoom de 200% sem quebra. Regras citadas: RNF-19, RNF-20, RNF-34, RNF-36. Pronto quando: o teste falha se um token de cor perder contraste ou se um componente tiver largura fixa maior que 360 px; roda no `ci-gate`; os textos e a documentação dos itens #7, #8 e #9 deixam de dizer "navegador real fica para a #10". Marco sugerido: Sprint 01. Tamanho: pequeno a médio.

**A4 · Endurecer a esteira** (resolve R00-6) — depois do PR, até o primeiro item de rota da Sprint 02
- Tipo: corrigir no item #10 (novo item pequeno).
- O que mudar e onde: `ci.yml`: conferir `sha256` do gitleaks e do osv-scanner antes de executar; no `jq`, tratar nota ausente como alta (ou usar o rótulo de severidade) e listar os avisos sem nota; no `ci-gate`, exigir `success` ou `skipped` apenas onde a regra de pasta permite e `success` em `changes` e `security`; fixar ações por SHA com a tag em comentário; avaliar `dependabot.yml` para Maven, npm e GitHub Actions.
- Como provar: PR descartável com um aviso OSV sem CVSS e outro com hash trocado; `ci-gate` vermelho.
- Tamanho: pequeno.

**A5 · Fluxo para item que só se prova com CI** (resolve R00-2) — depois do PR
- Tipo: decisão do dono (D4) e, se aceita, ajuste do `AGENTS.md` e do processo.
- O que mudar: em itens cuja prova depende de workflow já em `dev`, abrir o PR do item, rodar a verificação com a esteira do próprio PR (PRs descartáveis só para os casos negativos) e fazer o merge só depois da rodada OK, sem PR de regularização. Se não for possível, registrar a exceção no `AGENTS.md` e exigir a autorização do dono por escrito no arquivo do item (o #11 não a registra).
- Tamanho: pequeno.

**A6 · Texto da #192** (apoia R00-9) — antes de começar a Sprint 22
- Tipo: ajuste de issue.
- O que mudar: acrescentar à #192 "restauração testada e **cronometrada**" e "um merge em `dev` publica sozinho no ambiente de teste e um e-mail disparado ali aparece na Caixa de saída" (o bullet que o marco da Sprint 00 não pôde provar). Opcional: falhar sem variável fora do perfil `local` (R00-11) e manter a porta 8081 interna (R00-10, R00-13).
- Tamanho: pequeno.

Ordem sugerida: A1 e A2 (antes do PR), D1 (decisão em paralelo), depois A3, A4, A5, A6.

#### Decisões do dono

**D1 · Exigir o `ci-gate` também no ruleset `protege-main`?** — recomendada: sim, acrescentar a regra "Require status checks to pass" com `ci-gate` — motivo: o PR `dev` para `main` é onde roda a suíte completa (`e2e:full`); sem o status exigido, o merge pode acontecer com o CI vermelho ou ainda rodando. `main` é do dono, o agente não mexe. Alternativa: manter e conferir o `ci-gate` à mão em cada PR de sprint.

**D2 · Aceitar as duas exceções OSV (Spring Framework 6.2, `GHSA-j9f9-w8pj-32f8` e `GHSA-pc63-qcmh-9cmg`) até 2027-01-15?** — recomendada: aceitar (os recursos afetados, SSE com fragmentos e `XsltView`, não são usados) e criar um lembrete (issue) para revisar antes da data: sai Spring 6.2.20 ou migra-se para o Boot 4. Sem isso, o CI de todo PR passa a falhar na data. Alternativa: migrar agora para o Boot 4, descartada por custo sem ganho.

**D3 · Aceitar que "publicar em `dev` sozinho" e "backup cronometrado" só sejam provados na #192?** — recomendada: sim, conforme a limitação aceita do `AGENTS.md` (sem hospedagem até a Sprint 22), com A6 para escrever o critério na #192. Alternativa: segurar o PR para `main` até haver hospedagem, descartada porque o plano já moveu isso de propósito.

**D4 · Para itens que só provam o CI depois do merge, o agente pode mergear antes da verificação?** — recomendada: não; abrir o PR, verificar nele e mergear depois, com PRs descartáveis só para os casos negativos (A5). Alternativa: permitir com autorização escrita do dono no arquivo do item.

#### Ações do dono

1. Abrir `http://localhost:5173` (de dentro de `frontend/`: `npm ci` e `npm run dev`), nos temas claro e escuro, a 360 px de largura (ferramentas do desenvolvedor, modo celular). Olhar o título "Bem-vindo ao ctcore", o botão de tema e a ausência de rolagem horizontal. Conferir o tom do texto e o contraste a olho nu; a revisão automática só mediu contraste por tokens e axe.
2. Rodar `bash infra/scripts/seed.sh` depois de subir o ambiente e o back-end uma vez (`./backend/mvnw -f backend/pom.xml spring-boot:run -Dspring-boot.run.profiles=local`) e abrir `http://localhost:8025` (Mailpit) para ver que a caixa de e-mail falsa está de pé.
3. Responder D1 a D4. Se concordar com D1, ajustar o ruleset `protege-main` em Settings, Rules.
4. Com o ajuste A1 e A2 feitos e uma nova rodada de revisão com resultado `APROVAR`: abrir o PR de `dev` para `main` com o método "Merge" (nunca squash) e, depois de revisar, dizer "sim" para liberar a Sprint 01.

#### Limitações e o que não foi conferido

- Hospedagem, publicação automática em `dev`, backup e restauração cronometrada: NÃO CONFERIDO (limitação aceita, até a Sprint 22; #192 e #193).
- Navegador real para a `Showcase` e para os componentes: não há rota; só a tela `/` foi conferida (fora do repositório). Zoom de 200% e foco por teclado em navegador real: NÃO CONFERIDO.
- Tela contra o canvas e tom dos textos: olho humano (Ações do dono 1).
- Caso negativo de cobertura abaixo de 80% (CI vermelho): só pelo registro do item #10, não reproduzido.
- `npm run e2e` numa máquina sem Chromium: NÃO CONFERIDO (o meu ambiente já podia ter o navegador).
- Skills `/code-review`, `/security-review` e `ponytail:ponytail-audit` não foram acionadas neste fork; a camada técnica foi feita por leitura dirigida pela lista de conferência (`SecurityConfig`, `application.yml`, migrações V1 e V2, workflows, Dockerfiles, `nginx.conf`, `pom.xml`, `package.json`, `osv-scanner.toml`) e por execução. Excesso de código e de dependência: sem achado evidente (a `integration/` tem uma classe simulada por porta, cada uma com teste); uma auditoria dedicada pode ser pedida.
- Linux e CI real: o clone foi no Windows 11 com Docker Desktop; o `ci.yml` foi lido, não executado por mim (os runs dos PRs foram consultados).

#### Riscos para a próxima sprint

Pontos de encaixe e quem os liga:
- `organization_feature` não tem chave estrangeira nem RLS: a #13 cria a tabela de organização e a FK; a #14 acrescenta RLS a ela e o `organizationId` no MDC dos logs; o `seed` ganha as linhas reais da organização e das unidades (`infra/seed/NN-nome.sql`).
- Auditoria: a #17 cria o serviço; `FeatureFlags.setEnabled` não audita e só ganha rota na Sprint 15, que deve chamar o serviço.
- Arquivos: o S3 local (SeaweedFS) está sem autenticação; a #16 define bucket e credencial.
- Cookie `XSRF-TOKEN` só sai quando o token é lido: a #18 (login) deve garantir.
- `ctcore.integrations.*.mode=real` é recusado na subida: as Sprints 22 e 23 trocam a recusa ao criar os adaptadores; a Caixa de saída é em memória e sem tela.
- MSW com lista vazia e `Page.items` aberto: cada rota nova declara seu contrato, handler e `items`.
- Contexto `WhatsAppProvider` sem fonte de dado: o primeiro item que expuser a configuração da plataforma o liga.
- 100% de cobertura em pacotes críticos sem efeito até existirem; se os itens usarem outros nomes de pacote, ajustar `pom.xml` e `vite.config.ts` no mesmo PR (R00-17).

Dívidas com prazo: exceções OSV vencem em 2027-01-15 (D2); não há comentário `ponytail:` no código.

Pré-requisitos da Sprint 01: o isolamento nas duas camadas (#13 a #15) é a trava de todas as sprints seguintes; a primeira rota nova passa a exigir teste de isolamento e de perfil no mesmo PR. A prova em navegador real (A3) deve estar pronta antes da primeira tela (Sprint 02).

Issues `depende de você` que passam a travar algo: nenhuma nesta sprint. As da Sprint 22 (#176 a #182, #192, #193) só travam a hospedagem e as conexões reais, depois do produto completo.
