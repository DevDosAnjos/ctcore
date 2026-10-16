# Revisão da Sprint 01 · Organização, unidades, isolamento e auditoria

### Rodada 1 — AJUSTAR — 2026-10-16, estado c538683b3858c9e4662a98b90c1efd675ef8bce0

#### Resumo e veredito

A Sprint 01 entregou só back-end e banco: organização, unidade, configurações e limites (V3), contexto de organização com filtro e row-level security (V4 e pacote `isolation`), o molde de teste de isolamento, arquivos por organização com URL assinada (S3) e o serviço de auditoria só de inclusão (V5). As 5 issues do marco estão fechadas, cada uma por um PR de squash com `Closes #N`, `ci-gate` verde e rodada `OK`. Não há rota, tela nem OpenAPI novo, o que o plano permite.

Veredito: **AJUSTAR**, sem bloqueio. Três ajustes pequenos devem entrar antes do PR `dev` para `main`: o teste do Prometheus continua vermelho em clone cujo caminho tem UUID (R00-1, já apontado na Sprint 00 e não corrigido), documentação de armazenamento desatualizada e códigos de regra ausentes no nome de dois testes. O dono ainda precisa decidir o `ci-gate` no ruleset de `main` (D1 da Sprint 00, sem resposta no repositório) e olhar a pasta de resíduos deste review (Ações do dono 1).

#### Estado revisado

| | |
|---|---|
| `dev` | `c538683b3858c9e4662a98b90c1efd675ef8bce0` (igual a `origin/dev`, pasta limpa) |
| `main` | `78676ca4fc0a2697a6a63dacd2c6b74e467af25e` (Sprint 00) |
| Ferramentas | Java 21.0.12 (o projeto usa 21), Node 24.21.0, Docker 29.8.1, data 2026-10-16 |
| Diff | `git diff origin/main...dev --stat`: 54 arquivos, +2971 −97 (não existe branch `main` local; usei `origin/main`) |
| PRs da sprint | #221 (#13), #222 (#14), #224 (#15), #225 (#16), #226 (#17); fora de item: #223 `docs: ajustes dos itens 13 e 14`, #220 registro da revisão da Sprint 00, #218 e #219 (AGENTS.md, Docker local) |

Pré-condições: `dev` limpa e igual a `origin/dev`; nenhuma branch `item/*` local ou remota; nenhum PR aberto contra `dev`; `gh auth` válido; Docker ativo (Server 29.8.1). Marco com 5 issues fechadas, nenhuma com a etiqueta `depende de você`. Backlog: linhas 13 a 17 "feito". Nenhum PR da sprint fechou issue de outro marco, e nenhum PR de outra sprint fechou issue desta (os PRs #221 a #226 fecham só o próprio número). Nada de Sprint 02 em `dev` (parada de sprint respeitada).

#### Trilha de processo

| Item | PR | Verificação | Entrega | Observação |
|---|---|---|---|---|
| #13 Organização, unidade, configurações e limites | #221, squash (1 commit), `ci-gate` SUCCESS, `Closes #13` | Rodada 1 OK | sim | |
| #14 Isolamento: contexto, filtro e RLS | #222, squash, `ci-gate` SUCCESS, `Closes #14` | Rodada 1 OK | sim | ajuste posterior fora do PR (#223) |
| #15 Teste-modelo de isolamento e permissão | #224, squash, `ci-gate` SUCCESS, `Closes #15` | Rodada 1 OK | sim | |
| #16 Arquivos por organização | #225, squash (3 commits no PR, 1 em `dev`), `ci-gate` SUCCESS, `Closes #16` | Rodada 1 OK e Rodada 2 OK | sim | duas execuções de CI falharam antes da verde (runs 38934038647 e 38934771522, no mesmo branch); 2 rodadas, abaixo do limite de 3 |
| #17 Serviço único de auditoria | #226, squash, `ci-gate` SUCCESS, `Closes #17` | Rodada 1 OK | sim | |

Conferido por mim com `gh pr view` (título, base `dev`, `Closes`, `ci-gate`), `git log --format='%h %p %s'` (cada merge com um pai só) e `grep` nos arquivos de item: as seis seções, "Construção", ao menos uma `### Rodada N — OK`, "Entrega", 0 tarefas abertas (`- [ ]`) nos cinco itens. Nenhum item mergeou antes de verificar (rodadas com data de 16/10 e PRs depois). Sem merge sem verificação. Título dos PRs igual `#N: <título da issue>`.

Desvio de processo: o PR #223 (`docs: ajustes dos itens 13 e 14`, sem `Closes`) mexeu em `AGENTS.md`, `infra/scripts/prepare-runtime-db.sh` e no arquivo do item #14 depois do merge dos PRs #221 e #222. Registrado em R01-8 (sugestão).

Execuções do `ci.yml` em `dev`/PRs: PR #226 `changes`, `security`, `backend`, `image-backend` SUCCESS e `frontend`, `e2e`, `image-frontend` SKIPPED (regra de pasta: o PR não toca em `frontend/`), `ci-gate` SUCCESS. Run mais recente em `dev` (`gh run list --branch dev`): `Publish` success em todos os pushes (c538683, 21815fe, b532cdf, a030429, 9220d44, 5074095, 2813e99, 491d36c).

#### "Pronto quando"

**Do marco** (`gh api repos/{owner}/{repo}/milestones/2`)

| Bullet | Status | Evidência |
|---|---|---|
| Para cada rota, um teste prova que a organização A não lê nem altera nada da B | PASSOU (sem rota real ainda; ver R01-4) | `docs/openapi.yaml` tem `paths: {}` e nenhum `@RestController` em `src/main` (só `ApiExceptionHandler`); portanto não há rota sem teste. O molde existe e roda: `RouteIsolationModelIT` e `RouteIsolationModelExampleIT` passaram no meu `verify` (48 ITs, 0 falhas), com leitura cruzada, alteração cruzada, anônimo 401, sem organização 403, perfil negado 403 e cabeçalho forjado. Nada impede hoje que uma rota futura entre sem o teste (R01-4) |
| O banco recusa uma consulta sem organização definida | PASSOU | No banco migrado do zero, conectado como `ctcore_app` (rolsuper=f, rolbypassrls=f), sem `app.organization_id`: `select count(*)` de `organization`, `unit_settings` e `audit_log` = 0; com o contexto da organização 1 do seed: `organization` 1 e `unit` 1. Testes: `OrganizationIsolationIT`, `RowLevelSecurityIT`, `OrganizationsIT.rowLevelSecurity`, `AuditServiceIT.rowLevelSecurity` |
| Não existe rota nem tela que edite ou apague um registro de auditoria | PASSOU | Nenhum controller em `src/main`; sem tela (front não mudou: `git diff origin/main...dev -- frontend` vazio). Além disso o banco recusa: como `ctcore_app`, `delete from audit_log` e como `ctcore` (migração) `delete` e `truncate` falham com `audit_log is append-only (DELETE/TRUNCATE)` (saída observada); `AuditServiceIT.appendOnly` cobre update, delete e truncate |
| Todos os itens do "Pronto quando" geral valem para cada item | PASSOU, com ressalva R01-3 | tabela abaixo |

**De todo item** (`docs/plano-de-sprints.md`, "Pronto quando (vale para todo item)")

| Regra | Status | Evidência ou "não se aplica nesta sprint: motivo" |
|---|---|---|
| Código de regra citado tem ao menos um teste | PASSOU, com rastreabilidade parcial | `grep` nos testes: RN-ORG-01, 02, 04, 05, 10, 16 (como "04/16"), RNF-05, RNF-06, RNF-31, RN-AUD-01/02/03 aparecem no nome ou no comentário. RN-ORG-03 (`S3FileStorageIT`) e RN-LGP-11 (`AuditServiceIT`, caso CA9) têm teste, mas sem o código escrito (R01-3) |
| Cobertura mínima de 80%; 100% nas regras críticas | PASSOU | Meu `verify`: JaCoCo 547 de 570 linhas (95,96%), "All coverage checks have been met". Pacote `isolation` 61/61 (100%), o único pacote crítico que passou a existir; `audit` 58/60, `organization` 55/55, `storage` 73/81. Front: 97,83% de linhas. Pacote `billing`, `payment`, `subscription`, `deactivation`: não existem ainda. Confirmado que o padrão `br.com.ctcore.isolation*` do `pom.xml` pega o pacote real (R00-17 resolvido) |
| Rota nova com teste de isolamento entre organizações e entre perfis | não se aplica nesta sprint: nenhuma rota nova; o molde (#15) é a entrega | ver R01-4 |
| OpenAPI, implementação e MSW batem | não se aplica nesta sprint: sem rota, o contrato não muda | `ContractRoutesTest` e `OpenApiContractTest` passaram no `verify`; `docs/` e `frontend/` sem diff |
| Auditoria em toda escrita | PASSOU com ressalva (R01-9) | O serviço existe e o `Organizations.initialize` e o `FileStorage` (únicos códigos que gravam) ainda não o chamam; a decisão D1/D5 dos itens #13 e #17 os trata como provisionamento interno sem ator e sem rota. Nenhuma escrita exposta |
| Quatro estados, 360 px e axe nos dois temas | não se aplica nesta sprint: sem tela | `git diff origin/main...dev -- frontend` vazio. `npm run test:cov` (axe em jsdom) continua verde |
| Textos nos arquivos de tradução | não se aplica nesta sprint: sem texto de tela | `npm run lint` (regra de texto literal) verde |
| Recurso novo nasce desligado por organização | não se aplica nesta sprint: nenhum recurso liberado aos poucos | |
| Notas de versão | não se aplica nesta sprint: mudança invisível para as academias | |
| Fluxos críticos da sprint (tabela do plano) | não se aplica nesta sprint: a tabela do plano não lista fluxo para a Sprint 01 | |

#### Clone limpo

Clone de `origin/dev` (c538683) em pasta nova do scratchpad e, para o back-end, em uma pasta de caminho curto (`C:\ct`), pelo motivo de R01-1. O ambiente do "recomeço" usou outro nome de projeto do Compose e outras portas, porque o nome do projeto no `docker-compose.yml` é fixo (`ctcore`) e os volumes `ctcore_postgres-data` e `ctcore_storage-data` são do dono (R01-10).

| Passo | Comando | Resultado real |
|---|---|---|
| ambiente | `docker compose up -d --wait` (de dentro de `infra/`, sem `.env`) | 3 serviços saudáveis em 21 s (projeto isolado) |
| runtime do banco | `bash infra/scripts/prepare-runtime-db.sh` | ok; papel `ctcore_app` sem superusuário e sem BYPASSRLS |
| migrações | imagem `ctcore-backend` com `DB_HOST=host.docker.internal` | "Successfully applied 5 migrations ... now at version v5"; `readiness` 200 |
| seed (2x) | `bash infra/scripts/seed.sh` | contagens iguais nas duas execuções: organization 3, unit 3, organization_settings 3, unit_settings 3, organization_limits 3, organization_feature 8 |
| back-end, caminho longo | `./backend/mvnw -f backend/pom.xml verify` | **FALHOU**: 1 de 79 testes rápidos (`ActuatorEndpointsTest.prometheus:70`), BUILD FAILURE (R01-1) |
| back-end, caminho curto | idem, em `C:\ct` | **BUILD SUCCESS**, 79 testes rápidos e 48 de integração, 0 falhas, 0 pulos, cobertura 547/570 linhas (95,96%), "All coverage checks have been met", 201 s |
| front-end | `npm ci` 43 s, `lint` 14 s, `typecheck` 13 s, `test:cov` 44 s, `build` 17 s, `e2e` 27 s | todos com código 0; 19 arquivos, 121 testes, cobertura de linhas 97,83% (instruções 96,56%); 1 teste E2E |
| imagens | `docker build` das duas | back-end: uid 10001 (`ctcore`), `:18081/actuator/health/readiness` 200 `{"status":"UP"}` com o banco, `/actuator/health` na porta da API 404; front-end: uid 101 (nginx), `/` 200, `/api/v1/x` 404 |
| runtime e RLS no banco real | `psql` como `ctcore_app` | ver "Pronto quando" do marco |
| recomeço | `down -v` do projeto isolado | volumes `ctcorerev_*` removidos; volumes do dono preservados |

Comparação com a linha de base: back-end 79 testes rápidos e 48 de integração, igual ao registrado no item #17 (Sprint 00 terminou com 65 e 20; cresceu). Front-end 121 testes e 97,83%, igual ao registrado no item #13 (Sprint 00: 87 testes e 96,86%). Cobertura de linhas do back-end 95,96% (Sprint 00: 95,59%). Nada piorou. Tempo do `verify` 201 s contra cerca de 100 s na Sprint 00 (mais ITs com Testcontainers).

Passos do README que não bastaram: o README manda `prepare-runtime-db.sh` antes do back-end e ele funcionou do zero. O README, linha 39, ainda diz que o armazenamento é "sem autenticação" (R01-2). O README não avisa que uma segunda cópia do repositório na mesma máquina divide os volumes do dono (R01-10).

#### CI e proteções

| Item | Estado real | Evidência |
|---|---|---|
| `ci-gate` exigido em `dev` | sim | `gh api repos/DevDosAnjos/ctcore/branches/dev/protection`: contexto `ci-gate`; push forçado e exclusão bloqueados; `enforce_admins` false; sem exigência de PR/revisão |
| regra de `main` | ruleset `protege-main` ativo: sem exclusão, sem push não-fast-forward, PR obrigatório só com método "merge" (0 aprovações). **Não exige `ci-gate`** | `gh api repos/.../rulesets/25019105`. Mesmo estado do D1 da Sprint 00 (R01-7) |
| métodos de merge do repositório | merge, squash e rebase permitidos | `gh api repos/...`; `dev` aceita squash como manda o `AGENTS.md`; o ruleset de `main` só deixa "merge" |
| últimos runs de `ci.yml` e `publish.yml` | verdes; jobs pulados só onde a regra de pasta manda | CI do PR #226: run 38938518497; `Publish` em `dev`: run 38939132180 success |
| `permissions:` e pinagem | `contents: read` nos dois workflows; ações fixadas por versão (`actions/checkout@v7.0.1`, `setup-java@v6.1.0`, `setup-node@v7.1.0`, `upload-artifact@v7.0.2`); `secrets.*` só em `publish.yml`. `.github/` sem diff nesta sprint (`git diff origin/main...dev -- .github` vazio) | leitura dos arquivos |
| exceções de vulnerabilidade | 2 em `osv-scanner.toml`: `GHSA-j9f9-w8pj-32f8` e `GHSA-pc63-qcmh-9cmg`, ambas com motivo e `ignoreUntil = 2027-01-15`; 1 impressão digital em `.gitleaksignore` (LogOutputTest, com motivo) | arquivos; a data cai depois do fim da Sprint 02 e da 03 |
| segredos e variáveis do fluxo de publicação | nenhum existe (`gh secret list` e `gh variable list` vazios), coerente com "publicação pulada sem chaves" | `gh secret list`; `gh variable list` |

#### Achados

**AJUSTAR (antes do PR para `main`)**

- **R01-1 · AJUSTAR · teste e segurança** — local: `backend/src/test/java/br/com/ctcore/monitoring/ActuatorEndpointsTest.java:70` (`prometheus`) e métricas `disk_free_bytes`/`disk_total_bytes` (mesmo R00-1 da Sprint 00).
  - esperado: a suíte passa em qualquer pasta; `/actuator/prometheus` não expõe caminho de instalação.
  - observado: no clone dentro do scratchpad (caminho com UUID), `verify` falhou: `Tests run: 79, Failures: 1`, `ActuatorEndpointsTest.prometheus:70`. No clone de caminho curto, BUILD SUCCESS. A proposta A1 da Sprint 00 não foi aplicada: `grep disk backend/src/main/resources` não acha nada.
  - evidência: `be.log` do clone longo (linhas 195, 1006, 1747, 1750); `be2.log` do curto.
  - efeito: quem clonar em pasta com padrão de UUID vê vermelho; o CI do GitHub não pega. A métrica continua mostrando o caminho no porto de gestão.
  - proposta: A1.

- **R01-2 · AJUSTAR · especificação e documentação** — local: `README.md` linha 39 (`storage ... sem autenticação`); `specs/itens/16-...md` D5.
  - esperado: documento diz o que o código faz.
  - observado: o item #16 acrescentou `infra/storage/s3.json` (credenciais `dev`/`dev-secret`) e o `-s3.config` no compose porque o SeaweedFS sem identidades recusa requisição assinada; o README da raiz e o D5 do item ainda falam em "sem autenticação". O desvio está só no texto "Desvios" e na sugestão da rodada 1 do item.
  - evidência: `git diff origin/main...dev -- infra/docker-compose.yml infra/storage/s3.json`; `grep -n "^- D5" specs/itens/16-*.md`.
  - efeito: instrução desatualizada para o próximo item que mexer no armazenamento.
  - proposta: A2.

- **R01-3 · AJUSTAR · teste (rastreabilidade)** — local: `backend/src/test/java/br/com/ctcore/storage/S3FileStorageIT.java` (5 testes, nenhum com `RN-ORG-03`) e `audit/AuditServiceIT.java:47` (`CA2, CA5, CA9`, sem `RN-LGP-11`).
  - esperado: R00-5 pediu que os testes citem o código da regra no nome; a issue diz "cada um vira ao menos um teste".
  - observado: os testes existem e exercitam a regra, mas a varredura por código não os acha.
  - evidência: `grep -rE "RN-ORG-03|RN-LGP-11" backend/src/test` vazio; `RN-ORG-03` só aparece no Javadoc de `FileStorage`.
  - efeito: a próxima revisão e quem audita perdem a rastreabilidade.
  - proposta: A3.

**AJUSTAR (pode seguir na Sprint 02)**

- **R01-4 · AJUSTAR · teste** — local: `RouteIsolationModelIT` e o milestone ("para cada rota, um teste").
  - esperado: a promessa de teste por rota é verificada por máquina, não só por convenção.
  - observado: nada falha se uma rota nova entrar sem `*IsolationIT`; `ContractRoutesTest` confere rota x OpenAPI, não rota x teste de isolamento. Hoje é vazio (0 rotas), mas a Sprint 02 cria as primeiras (login, convite).
  - evidência: leitura de `ContractRoutesTest` e do README do back-end (o molde é manual: "estenda esta classe").
  - efeito: o primeiro item que esquecer o teste só é pego na revisão.
  - proposta: A4.

**SUGESTÃO**

- **R01-5 · SUGESTÃO · segurança** — o papel `ctcore_app` tem `UPDATE` em `flyway_schema_history` (`has_table_privilege` = `t`, observado) porque o script concede CRUD a todas as tabelas. Já está como `ponytail:` em `prepare-runtime-db.sh` (#192). Aceitável em ambiente local; a #192 deve separar o papel. Default de credenciais de desenvolvimento em `application.yml` (`ctcore_app_dev`, `dev`/`dev-secret`) fora do perfil `local` amplia o R00-11 da Sprint 00: variável esquecida em produção usa a senha de desenvolvimento.
- **R01-6 · SUGESTÃO · segurança** — `FileStorage.put(name, bytes, contentType)` grava o tipo de conteúdo que o chamador passar, sem lista permitida; a URL assinada entrega esse tipo. O item que expuser envio (logo, anexos) deve restringir o tipo e `Content-Disposition`. O `OrganizationScopeFilter` só vale para `/api/v1/` (com barra); rotas fora desse prefixo e `/api/v1` exato passam sem escopo. Hoje fecha por padrão (autenticação obrigatória e RLS devolve vazio), mas vale um teste que garanta que não haverá rota autenticada fora de `/api/v1`.
- **R01-7 · DONO · CI** — o ruleset `protege-main` ainda não exige o `ci-gate` (D1 da Sprint 00 sem resposta). O PR `dev` para `main` roda a suíte completa, mas pode ser mergeado com ela vermelha ou em andamento. Ver Decisões do dono D1.
- **R01-8 · SUGESTÃO · processo** — o PR #223 alterou `AGENTS.md`, `prepare-runtime-db.sh` e o arquivo do item #14 fora do PR do item, sem `Closes`, depois do merge (motivo declarado no corpo do PR: tirar descrição do módulo do AGENTS, trocar a referência da auditoria e registrar a limitação do papel). O `specs/backlog.md` ainda diz "193 issues: 12 fechadas e 181 abertas (conferido em 16/10/2026)"; hoje são 17 fechadas.
- **R01-9 · SUGESTÃO · API e dados** — `Organizations.initialize` e o armazenamento de arquivos gravam dado sem chamar `AuditService`; a regra "toda ação que altera dado grava na auditoria" fica pendente nos itens que os expõem (cadastro da organização na Sprint 16, uploads, #30). Ver "Riscos".
- **R01-10 · SUGESTÃO · operação** — o compose fixa `name: ctcore`; qualquer segundo clone na mesma máquina divide os volumes do dono, e `docker compose down -v` (que o README manda para "apagar os dados") apaga os dados do dono. O README poderia avisar de `COMPOSE_PROJECT_NAME`. `seed.sh` termina com "Organizações fictícias: N" contando `organization_feature`, não `organization`. O papel `ctcore_app` dos testes (`PostgresIT`) é criado à mão e não pelo `prepare-runtime-db.sh`, então o script em si não é testado.
- **R01-11 · SUGESTÃO · teste** — `S3FileStorageIT:72` usa `Thread.sleep(2500)` para esperar a URL expirar; pode ficar frágil em máquina lenta. `FeatureFlagsIT:43` já era do R00-15.

#### Desvios e decisões herdados dos itens

| Item | Desvio ou decisão | Classe | Destino |
|---|---|---|---|
| #13 | D1: sem rota/tela/auditoria nesta fase (provisionamento interno) | técnico aceitável; ressalva R01-9 | registrar |
| #13 | nome curto único globalmente (RN-ORG-15); UUID com default do banco; acrescentados meios de pagamento com taxa zero | técnico aceitável | registrar |
| #13 | `requireUnitCapacity`/`requireManagementUserCapacity` são funções estáticas; nenhuma restrição no banco impede a 11ª unidade | técnico aceitável | risco para a #30 (Sprint 03) |
| #14 | `MigrationIT` e `HealthIT` precisaram separar credenciais; prova MockMvc com rota de teste | técnico aceitável | registrar |
| #14 | runtime com papel próprio (`DB_RUNTIME_*`) e script `prepare-runtime-db.sh`; passo novo no README | muda comportamento do ambiente local; já refletido no README e no AGENTS.md | registrar |
| #15 | exemplo devolve só `{unitId}`; sem `@PreAuthorize` por rota paralela | técnico aceitável | registrar |
| #16 | `infra/storage/s3.json` e `-s3.config` no compose; checksum `WHEN_REQUIRED`; sem variáveis `STORAGE_*` no `.env.example` | muda requisito (D5) | ajustar especificação (A2) |
| #16 | exclusão do `netty-nio-client` na dependência S3 | técnico aceitável | registrar |
| #17 | política de leitura vale para select/update/delete para o gatilho dar erro; truncate pelo runtime sem privilégio; `SeedDataIT.clean` desliga o gatilho só no teste; `AuditSanitizer` classe própria | técnico aceitável | registrar |
| #17 | D4: sem organização no contexto só autor da plataforma grava, com organização vazia | muda comportamento, já em D4 do item | registrar; ver "Riscos" |
| #17 | remoção por nome de chave, não por valor; chave nova sensível exige regra e teste | técnico aceitável | dívida (R01-3 de teste futuro) |
| Rodadas | sugestão da rodada 1 do #16: atualizar D5; duas sugestões de código do #16 (teste do bean, ramo 404 duplicado) | sugestão sem dono | dívida; D5 vai em A2 |

#### Ajustes propostos

**A1 · Teste do Prometheus independente do caminho** (resolve R01-1) — antes do PR
- Tipo: corrigir no item #5 (defeito de item feito).
- O que mudar e onde: `application.yml`, `management.metrics.enable.disk: false` (ou excluir `disk.free` e `disk.total`) e, se o dono quiser manter as métricas de disco, filtrar as linhas `disk_*` antes das três varreduras em `ActuatorEndpointsTest.prometheus`.
- Como provar: `./backend/mvnw -f backend/pom.xml verify` num clone em pasta cujo caminho tenha um UUID; `curl :8081/actuator/prometheus | grep -c disk_` igual a 0.
- Tamanho: pequeno.

**A2 · Atualizar o texto do armazenamento** (resolve R01-2) — antes do PR
- Tipo: ajuste de especificação e documentação.
- O que mudar: `README.md` linha 39: trocar "sem autenticação, só para desenvolvimento" por "credenciais fictícias de desenvolvimento em `infra/storage/s3.json`"; `specs/itens/16-...md` D5: "credenciais fictícias do compose, em `infra/storage/s3.json`; em produção só por variável de ambiente (#192)".
- Como provar: reler o README seguindo só ele e chamando o armazenamento com e sem credenciais.
- Tamanho: pequeno.

**A3 · Códigos de regra nos nomes dos testes** (resolve R01-3) — antes do PR
- Tipo: corrigir nos itens #16 e #17.
- O que mudar: `@DisplayName("RN-ORG-03: ...")` nos testes de `S3FileStorageIT` e `RN-LGP-11` no teste CA9 de `AuditServiceIT`; sem mudar o corpo.
- Como provar: `grep -rE "RN-ORG-03|RN-LGP-11" backend/src/test` acha os dois.
- Tamanho: pequeno.

**A4 · Guarda que obriga teste de isolamento por rota** (resolve R01-4) — depois do PR (no primeiro item da Sprint 02 que criar rota)
- Tipo: novo item pequeno, sugerido junto de #18.
- Texto de issue proposto: **"Guarda de teste de isolamento por rota"**. Entregar: um teste que lista as rotas do contexto da aplicação (as mesmas de `ContractRoutesTest`) e falha se alguma não aparecer em uma subclasse de `RouteIsolationModelIT` (por mapa explícito rota -> classe), com lista curta de exceções públicas justificadas (login, convite). Regra citada: RNF-31. Pronto quando: remover o teste de uma rota existente faz a guarda falhar; rotas públicas justificadas ficam listadas. Marco sugerido: Sprint 02.
- Tamanho: pequeno.

Ordem sugerida: A1, A2, A3 (um único PR de correção para `dev` antes do PR `dev`→`main`), depois A4 na Sprint 02.

#### Decisões do dono

**D1 · Exigir o `ci-gate` também no ruleset `protege-main`?** — recomendada: sim — motivo: no PR `dev`→`main` roda a suíte completa (`e2e:full`), e sem o status exigido o merge pode ocorrer com o CI vermelho ou ainda rodando; `main` é do dono, o agente não mexe. Alternativa: manter e conferir o `ci-gate` à mão a cada PR de sprint.

**D2 · Apagar a pasta `C:\ct` (clone temporário de caminho curto, que a revisão criou e não conseguiu remover)?** — recomendada: sim — motivo: o bloqueio de segurança do ambiente impediu a remoção automática de uma pasta fora do scratchpad e a revisão não contorna isso. É só um clone do `dev`.

#### Ações do dono

1. Apagar `C:\ct` (D2) e, se quiser, a pasta `c` dentro do scratchpad da revisão (`C:\Users\natha\AppData\Local\Temp\claude\D--VSCode-Workspace-ctcore\1dc3be7b-012c-4124-a729-4578b2cfab11\scratchpad\c`, que tem `node_modules`).
2. O banco local `ctcore_postgres-data` do dono recebeu a migração V5 (auditoria): a revisão subiu o back-end uma vez contra o Compose normal (que estava parado) antes de passar para um projeto isolado. Os contêineres `ctcore-*` voltaram a ficar parados; os dados não foram apagados. Nada a fazer, só saber.
3. Decidir D1 (ruleset de `main`).
4. Com o resultado `APROVAR` e A1, A2 e A3 resolvidos: abrir o PR de `dev` para `main` com o método "Merge" (nunca squash) e, depois de revisar, dizer "sim" para liberar a Sprint 02.

Não há tela nesta sprint, então não há conferência de navegador, 360 px, tema nem axe para o dono.

#### Limitações e o que não foi conferido

- Navegador real, axe real, 360 px, contraste: não se aplica nesta sprint (sem tela). Os testes de tela da Sprint 00 continuam verdes no clone.
- `e2e:full`, gitleaks, OSV-Scanner e `actionlint` locais: não rodei (o CI roda; `security` SUCCESS no PR #226).
- Publicação e hospedagem: NÃO CONFERIDO (limitação aceita), conforme `AGENTS.md`.
- A skill `/code-review` e a `/security-review` não foram acionadas separadamente; a leitura dirigida cobriu `V3` a `V5`, `isolation`, `audit`, `organization`, `storage`, `SecurityConfig`, `application.yml`, `prepare-runtime-db.sh`, `docker-compose.yml`, `pom.xml` e os testes principais. `ponytail-audit` não foi acionada; nada a registrar de excesso de código além do `ponytail:` do script.
- Teste de concorrência da 11ª unidade e do lock por organização: não existe (a regra é só uma função estática hoje).
- O recomeço (`down -v`) foi provado no projeto isolado `ctcorerev`, não no `ctcore` do dono.

#### Riscos para a próxima sprint

- **Sprint 02 (contas, login, convites)**: primeiras rotas reais. O molde de isolamento é manual (R01-4). As tabelas de conta e vínculo são entre organizações por natureza; a RLS por `app.organization_id` precisa de um caminho (consulta antes de a organização estar definida, escolha de organização, RN-ACE) sem enfraquecer a política. `OrganizationScopeFilter` exige `OrganizationPrincipal` em todo `/api/v1/` autenticado; a rota de login/convite precisa ser pública no `SecurityConfig` (hoje `anyRequest().authenticated()`; o comentário cita a #18).
- **Auditoria**: `AuditService` exige transação aberta (`Propagation.MANDATORY`). Todo item da Sprint 02 em diante que grave dado precisa chamá-lo; o `Organizations.initialize` e o armazenamento ainda não chamam (R01-9). O D4 do item #17 deixa ações de formulário público sem organização negadas até que o item dessa rota decida.
- **RN-ORG-05 (10 unidades, 200 usuários)**: só funções estáticas; a #30 (assistente de nova unidade, Sprint 03) e a #37 devem combinar a regra com a gravação sob lock por organização.
- **LGPD (Sprint 13)**: `audit_log` é append-only e tem chave estrangeira para `organization` e `unit`; valores antes/depois guardam dado pessoal (nome, CPF). Exclusão ou anonimização de titular e remoção de organização não têm caminho hoje. Isso é regra de negócio do dono a decidir antes da Sprint 13.
- **Hospedagem (#192/#193, Sprint 22)**: separar o papel do banco (R01-5), garantir que a porta 8081 fique na rede interna (R00-10), credenciais por variável de ambiente sem padrão de desenvolvimento (R00-11, R01-5), usuário de migração diferente do administrador.
- **Exceções OSV com validade 2027-01-15**: reavaliar quando sair Spring 6.2.20 ou migrar para o Boot 4; cai depois das Sprints 02 e 03.
- Nenhuma issue `depende de você` trava a Sprint 02.
