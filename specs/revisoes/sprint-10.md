# Revisão da Sprint 10 · Portal do aluno

### Rodada 1 — AJUSTAR — 2026-10-28, estado aad58ec77419df74c7576b99968439c43d059c6b

#### Resumo e veredito

A Sprint 10 entregou o aceite do termo com uso de imagem e confirmação de modalidade (#85), os pedidos do portal e a tela da gestão (#86), os comprovantes de Pix em conferência (#87), a barra do portal (#88), Início (#89), carteirinha (#90), Agenda (#91), Financeiro (#92), Avisos (#93), Minhas informações (#94) e os estados de tela (#95), mais seis itens de manutenção (#363, #368, #385, #386, #388, #390). As 17 issues do marco estão fechadas, cada uma por um PR de squash com `ci-gate` verde. O clone limpo passou no back-end (162 + 859 testes, 98,47% de linhas, 100% em `billing`, `payment` e `isolation`), no `e2e` (81 testes, 3 execuções verdes), nas imagens, no seed e no recomeço. Há dois BLOQUEIA: o `test:cov` do front reprovou em 2 de 4 execuções completas por um teste instável de `UnitSettingsPage` (R10-1), e um teste de back-end (`ChargeNoticeIT`) volta a depender da data a partir de 2026-10-31, segundo o próprio arquivo do item #390 (R10-2). Há um AJUSTAR de especificação (36 decisões que mudam comportamento sem a especificação ajustada, R10-3) e duas perguntas ao dono (D1, D2). Resultado: `AJUSTAR`.

#### Estado revisado

| | |
|---|---|
| `dev` | `aad58ec77419df74c7576b99968439c43d059c6b` (igual a `origin/dev`; clone novo no scratchpad, removido ao fim) |
| `main` | `0d8e16b9aec204022e2b98506bd1be574a431f65` (PR #380 da Sprint 09) |
| Data | 2026-10-28 |
| Ferramentas | Java 21.0.12 (build; o padrão da máquina é o 25.0.4), Node 24.21.0, Docker 29.8.1 |
| Diff | `git diff origin/main...dev --stat`: 232 arquivos, 21.778 inserções, 286 remoções (back-end main: 48 arquivos, 3.448 linhas; back-end test: 30 arquivos, 4.481 linhas; front `src`: 116 arquivos, 9.227 linhas; `docs/openapi.yaml`: 1.494 linhas; `backend/pom.xml`: 7 linhas; migrações V48 a V51; seeds 21 a 24). `.github`, `osv-scanner.toml`, `.gitleaksignore` e `docs/especificacao`: sem mudança |
| PRs da sprint | #381 (#368), #382 (#85), #383 (#86), #384 (#87), #387 (#88), #389 (#89), #391 (#363), #392 (#90), #393 (#91), #394 (#92), #395 (#93), #396 (#94), #397 (#385), #398 (#95), #399 (#386), #400 (#388), #401 (#390) |

Pré-condições:
- `dev` limpa e igual a `origin/dev`; nenhum PR aberto contra `dev`; o remoto só tem `main` e `dev` (`git ls-remote --heads origin`). O `git branch -a` lista seis branches `item/*` locais já mergeados por squash (`item/331-…`, `333-…`, `338-…`, `340-…`, `352-…`, `77-…`), `ajustes/seguranca-repo-publico`, `docs/*` e uma referência de rastreamento velha `origin/item/390-…`. Nenhum é trabalho em andamento (SUGESTÃO R10-8).
- As 17 issues do marco estão `CLOSED` e como "feito" no `specs/backlog.md`; nenhuma tem a etiqueta `depende de você`. Cada PR traz `Closes #N` do seu item e nenhum fechou issue de outra sprint.
- `gh auth status` válido (`DevDosAnjos`); Docker ativo.

#### Trilha de processo

Todos os PRs têm base `dev`, título `#N: <título da issue>`, `Closes #N`, um único commit de squash em `dev` (`git log --merges origin/main..dev` vazio; 17 commits) e `ci-gate` `SUCCESS` sem check em `FAILURE`.

| Item | PR | Verificação | Entrega | Observação |
|---|---|---|---|---|
| #368 `worker.use` no e2e | #381 | Rodada 1 OK | sim | |
| #85 Aceite do termo | #382 | Rodada 1 OK | sim | 5 decisões que mudam comportamento |
| #86 Pedidos do portal | #383 | Rodada 1 OK | sim | 7 decisões que mudam comportamento |
| #87 Comprovantes | #384 | Rodada 1 CORRIGIR_CODIGO, Rodada 2 OK | sim | |
| #88 Barra e navegação | #387 | Rodada 1 OK | sim | |
| #89 Início | #389 | Rodada 1 OK | sim | PR com 2 commits, 1 de squash |
| #363 Medição do CA4 da #343 | #391 | Rodada 1 OK | sim | só documentação; fecha o R09-7 (5 de 5 medidos, job de 105 s a 118 s) |
| #90 Carteirinha | #392 | Rodada 1 OK | sim | |
| #91 Agenda | #393 | Rodada 1 OK | sim | |
| #92 Financeiro | #394 | Rodada 1 OK | sim | |
| #93 Avisos | #395 | Rodada 1 OK | sim | |
| #94 Minhas informações | #396 | Rodada 1 NAO_CONFERIDO, 2 e 3 OK | sim | três rodadas (no limite) |
| #385 LessonPanelPage instável | #397 | Rodada 1 OK | sim | |
| #95 Estados de tela | #398 | Rodada 1 CORRIGIR_CODIGO, Rodada 2 OK | sim | |
| #386 e2e comprovantes | #399 | Rodada 1 OK | sim | |
| #388 e2e comprovantes 30 s | #400 | Rodada 1 OK | sim | só medição; não reproduziu |
| #390 `ChargeNoticeIT.minor` | #401 | Rodada 1 OK | sim | deixa uma limitação com data (R10-2) |

Todos os 17 arquivos têm as seis seções, Construção, ao menos uma rodada `OK`, Entrega e nenhuma tarefa aberta. Desvios de processo: nenhum (nenhum merge sem rodada `OK`, nenhuma correção fora do PR do item).

#### "Pronto quando"

**Do marco** (descrição do marco 11)

| Bullet | Status | Evidência |
|---|---|---|
| O portal não mostra nenhuma tela antes do aceite do termo | PASSOU | `terms.requireAccepted(who)` presente em todas as rotas de `/portal` fora o aceite e o uso de imagem (`PortalStudentController`, `PortalRequests`, `PaymentProofs.saveAll`/`instructions`, `PortalReceiptController`, `ModalityConfirmations`); `TERM_PENDING` testado em `PortalHomeIT`, `PortalCardIT`, `PortalFinanceIT`, `PortalScheduleIT`, `PortalAnnouncementsIT`, `PortalInfoIT`, `PortalProfileIT`, `PortalRequestControllerIT`, `PaymentProofControllerIT`; e2e `portal-termo.spec.ts` `@critical` verde (3 de 3); `verify` do clone: 859 ITs, 0 falhas |
| Nenhuma resposta da API do portal traz dado de aluno não vinculado à conta | PASSOU, com ressalva | leitura de `PortalTerms.requireAccepted`/`pending`, de `ReceiptFiles.portalPdf` (organização e `terms.pays` conferidos, 404 caso contrário) e dos ITs por rota no `verify` verde. Não li cada consulta de `Portal*` uma a uma: a prova é o conjunto de ITs de isolamento entre contas, organizações e perfis |
| Um pedido do portal nunca altera a matrícula sem ação da gestão | PASSOU, com ressalva (D1) | troca de turma, nova modalidade e troca de plano ficam "Aberto" e só mudam por Aprovar da gestão (`PortalRequestControllerIT`, `PortalRequestsIT`, `PortalRequestBase*`). Ressalva: o pedido de encerramento é encerrado pelo sistema no 8º dia (`PortalRequestJob`, `CLOSE_DAYS = 8`, `PortalRequestClosureIT`), como manda a RN-MAT-18; isso é uma alteração da matrícula sem ação da gestão e contraria a leitura literal do bullet |
| Um comprovante de Pix na chave pausa o prazo e, recusado, o prazo volta de onde parou | PASSOU | `ProofBlockDateIT` (contador `block_pause_days`), `PaymentProofsIT`, `PaymentProofControllerIT`, `PortalMultiProofIT`; e2e `comprovantes.spec.ts` `@critical` verde (3 de 3, light e dark) |
| Todos os itens do "Pronto quando" geral valem para cada item | NÃO PASSOU em um ponto | a regressão completa do front (`test:cov`) reprova ao acaso (R10-1); o resto está na tabela seguinte |

Resultado do marco: 4 de 5 bullets passaram (o 3º com ressalva, D1).

**De todo item (plano de sprints)**

| Regra | Status | Evidência ou "não se aplica nesta sprint: motivo" |
|---|---|---|
| Regras citadas com ao menos um teste | PASSOU | dos 32 códigos RN/RF/RNF citados nas issues #85 a #95, todos aparecem em pelo menos um arquivo de teste ou e2e (`grep` de cada código em `backend/src/test`, `frontend/src`, `frontend/e2e`: nenhum com zero ocorrência). Melhora sobre a Sprint 09, em que os testes citavam só "CA" |
| Cobertura mínima 80% e 100% nos pacotes críticos | PASSOU | back-end 98,47% de linhas (9.637 linhas); 0 linhas não cobertas em `billing`, `payment` e `isolation` (`jacoco.csv`); "All coverage checks have been met". Front `test:cov` exit 0 em 2 de 4 execuções (R10-1): 160 arquivos, 1405 testes, linhas 96,68%, pisos de 100% atendidos nas execuções verdes. `subscription` e `deactivation` ainda não existem |
| Rotas novas com teste de isolamento entre organizações e entre perfis | PASSOU, com ressalva | rotas novas em `/portal/*`, `/portal-requests`, `/payment-proofs`; ITs de rota por item (`PortalRequestBaseAccess`, `PortalTermControllerIT`, `PaymentProofControllerIT`, `PortalMultiProofIT` etc.) e RLS nas quatro tabelas novas (V48 a V51: `enable` e `force row level security`, política `organization_scope`, `update`/`delete` retirados das tabelas só de inserção). Ressalva: não abri cada IT para conferir um a um os casos de perfil |
| OpenAPI, implementação e MSW batendo | PASSOU | `OpenApiContractTest` e `ContractRoutesTest` dentro do `verify`; `docs/openapi.yaml` +1.494 linhas; testes `*.msw.test.ts` no `test:cov` |
| Auditoria em toda escrita | PASSOU, com ressalva | os itens registram auditoria de aceite, revogação, pedido, aprovação, recusa, comprovante, contato, foto e e-mail (via `AuditService`); efeitos de sistema (expirar e encerrar pelo job) gravam como `SYSTEM`. Ressalva: "marcar todos como lidos" e abrir aviso gravam marca de leitura/dispensa, tratadas como leitura e não como alteração de dado (D3 e D4 da #93) |
| Valores em centavos, operações idempotentes, lotes tudo ou nada | PASSOU | aceite repetido devolve 200 sem gravar (D8 da #85); `payment_one_confirmed`; envio de comprovante para várias cobranças numa transação (todas em conferência ou nenhuma, `saveAll`); centavos em `PlanChoice.valueCents` |
| Quatro estados, 360 px e axe nos dois temas | PASSOU por proxy; NÃO CONFERIDO em navegador de pessoa | #95 fecha os quatro estados de todas as telas; `e2e` `@critical` com axe claro e escuro e `scrollWidth <= innerWidth` a 360 px nas telas do portal (81 testes verdes em 3 execuções, `barra-360px.spec.ts` inclusive). Contraste real e leitura contra o canvas dependem de pessoa (seção 9) |
| Textos só nos arquivos de tradução | PASSOU | `npm run lint` exit 0 no clone |
| Recurso novo desligado por organização | não se aplica nesta sprint | cada item registra a decisão (D9 a D11): o portal é parte básica e a sprint não pede liberação gradual |
| Notas de versão | NÃO CONFERIDO (sem arquivo) | o repositório segue sem arquivo de notas; RF-AJU-04 é da Sprint 21 |

#### Clone limpo

Clone de `origin/dev@aad58ec` no scratchpad, `COMPOSE_PROJECT_NAME=revs10`. Havia contêineres parados `revctcore7-*` e `competent_neumann` que não são meus e não toquei.

| Passo | Comando | Resultado real |
|---|---|---|
| ambiente | `docker compose up -d --wait` (de `infra/`, sem `.env`) | 3 serviços saudáveis |
| banco | `bash infra/scripts/prepare-runtime-db.sh` | exit 0 |
| back-end | `./backend/mvnw -f backend/pom.xml verify` | BUILD SUCCESS em 15 min 42 s; 162 testes rápidos e 859 de integração, 0 falhas, 0 ignorados; "All coverage checks have been met"; 98,47% de linhas |
| migrações e readiness | `spring-boot:run -Dspring-boot.run.profiles=local` | "now at version v51" (eram 47); `/actuator/health/readiness` `{"status":"UP"}` na `:8081` |
| seed (2x e uma 3ª) | `bash infra/scripts/seed.sh` | exit 0; 24 arquivos de seed (eram 20); com o back-end parado, a 3ª execução não mudou as contagens de `term_acceptance` (2), `portal_request` (2), `payment_proof` (1), `charge` (4), `announcement_read` (1) |
| front-end | `npm ci`, `lint`, `typecheck`, `build` | exit 0 em todos |
| front-end | `npm run test:cov` (4 execuções completas) | 160 arquivos, 1405 testes; **2 vermelhas, 2 verdes** (R10-1): `UnitSettingsPage.test.tsx` CA17 estourou 20 s (na 1ª junto com CA18) e na 4ª; o arquivo sozinho passou 3 de 3 (19 a 23 s por arquivo); linhas 96,68% |
| e2e | `npx playwright install chromium`; `npm run e2e` (3 vezes) | **81 testes** (eram 36), 3 de 3 verdes, 2,7 a 2,8 min |
| imagens | `docker build` das duas; `docker run` | back-end como UID 10001 (`ctcore`), readiness `UP` na `:8081` com a chave `TWO_FACTOR_ENCRYPTION_KEY` do README, `/actuator/health` na `:8080` devolve 404; front-end como UID 101, `/` 200, `/alunos` 200, `/api/x` 404. Etiquetas `rev10` removidas; `docker images -q` igual ao de antes; `ctcore-*:latest` intactas |
| recomeço | `down -v` e `up -d --wait` de novo | 3 serviços saudáveis, `prepare-runtime-db.sh` exit 0, `down -v` final; nenhum contêiner nem volume `revs10` restou |

Comparação com a linha de base (Sprint 09): melhorou em testes (back-end 162 + 750 → 162 + 859; front 1176 → 1405; e2e 36 → 81), cobertura de back-end (98,40% → 98,47%) e estabilidade do `e2e` (3 de 7 vermelhas → 0 de 3). Piorou em: tempo do `verify` (10 min 39 s → 15 min 42 s, máquina possivelmente mais ocupada), cobertura de linhas do front (96,80% → 96,68%, ainda acima do piso) e estabilidade do `test:cov` (R10-1).

Passos do README que não bastaram: nenhum. Na primeira tentativa de subir a imagem do back-end eu esqueci `TWO_FACTOR_ENCRYPTION_KEY` e a porta 8080 do front; ambas estão no README (linhas 86 a 90) e funcionaram ao seguir o texto.

#### CI e proteções

| Item | Estado real | Evidência |
|---|---|---|
| `ci-gate` exigido em `dev` | sim | `gh api repos/DevDosAnjos/ctcore/branches/dev/protection`: `required_status_checks.contexts = ["ci-gate"]`, `allow_force_pushes = false` |
| regra de `main` | conforme | ruleset `protege-main` (id 25019105, ativo) |
| últimos runs | verdes | `Publish` em `dev`: 6 runs seguidos `success` (de `aeab90e` a `aad58ec`, o mais recente `40622622207`); `ci.yml` dos últimos PRs `success` (`40621674404` #390, `40613593646` #388, `40607537160` #386, `40598074801` #95, `40591655476` #385) |
| `permissions:`, pinagem, `ci-gate` | conforme, sem mudança | `git diff origin/main...dev -- .github` vazio; o fluxo revisado na Sprint 09 segue valendo |
| gitleaks | verde no CI; NÃO CONFERIDO localmente | job `security` verde nos PRs; varredura própria de padrão de segredo no diff: só a senha de teste `segredo123` do MSW e dos ITs, valor fictício |
| exceções de vulnerabilidade (`osv-scanner.toml`) | 2, sem mudança | `GHSA-j9f9-w8pj-32f8` e `GHSA-pc63-qcmh-9cmg`, ambas até 2027-01-15 |
| dependência | 1 mudança | `backend/pom.xml` (#94): `dependencyManagement` de `guava` 33.7.2-jre por `GHSA-xxph-c9ww-hj94`; `dependency:tree` mostra o guava só com escopo `test` |
| segredos e variáveis do fluxo de publicação (só nomes) | faltam, como previsto | `gh secret list` e `gh variable list` vazios; publicação pulada (limitação aceita até a #192) |
| visibilidade do repositório | `public` | esperado até antes da Sprint 22 |

#### Achados

- **R10-1 · BLOQUEIA · teste / front** — local: `frontend/src/features/units/UnitSettingsPage.test.tsx` (CA17 "o Coordenador altera dados, vencimentos, taxas e permissões e salva em centavos", linha 102; CA18)
  - esperado: regressão completa (`test:cov`) verde (`AGENTS.md`: falha na regressão vira issue no formato das #318 e #320 antes de a sprint seguir; o PR `dev`→`main` roda tudo).
  - observado: 4 execuções completas no clone limpo, 2 vermelhas. Na 1ª, CA17 "Test timed out in 20000ms" e CA18 falhou em cascata; na 4ª, só CA17. O arquivo isolado passou 3 de 3, mas leva 19 a 23 s no total, ou seja, o CA17 anda perto do limite de 20 s quando a suíte inteira roda em paralelo. O arquivo não foi tocado pela sprint (não consta em `git diff origin/main...dev`).
  - evidência: `npm run test:cov` (`cov.log`: `Tests 2 failed | 1403 passed`; 2ª e 3ª: `1405 passed`; 4ª: `1 failed | 1404 passed`); `npx vitest run src/features/units/UnitSettingsPage.test.tsx` 3 de 3 verdes.
  - efeito: o `test:cov` do CI pode reprovar o PR `dev`→`main` ao acaso; os itens da sprint que passaram por ele tiveram sorte ou mais folga de CPU.
  - proposta: A1.

- **R10-2 · BLOQUEIA (condicional à data) · teste / back-end** — local: `backend/src/test/java/br/com/ctcore/billing/BillingFixtures.java:69` (`due_on` fixo em `2026-11-10`) e `ChargeNoticeIT.java`; arquivo `specs/itens/390-charge-notice-minor-instavel.md`, seção Entrega
  - esperado: teste que não depende da data da JVM (`AGENTS.md`: "Teste não compara a data da JVM com a data da unidade").
  - observado: o próprio item #390 registra que, "a partir de 2026-10-31, a janela sem responsável volta a afetar `minor` e `errorIsolated`", e deixa a correção "em item próprio", que não existe no backlog. Confirmei só a leitura do fixture e da Entrega; não reproduzi adiantando o relógio.
  - evidência: `BillingFixtures.java:69` (`date '2026-11-10'`); `ChargeNoticeIT.java:36` usa `Clock.fixed(2026-10-05)` só no job de teste, enquanto o agendador real usa o relógio do sistema.
  - efeito: a partir de 3 dias depois desta revisão o `verify` do back-end tende a voltar a reprovar de vez em quando, o que bloquearia o PR `dev`→`main`.
  - proposta: A2.

- **R10-3 · AJUSTAR · especificação** — local: `specs/itens/85` a `94` (36 decisões marcadas "muda o comportamento": #85: 5, #86: 7, #87: 5, #88: 2, #89: 4, #90: 2, #91: 2, #92: 3, #93: 2, #94: 4) contra `docs/especificacao/` (sem nenhuma mudança na sprint)
  - esperado: "Mudou uma regra, muda primeiro a especificação" (`AGENTS.md`).
  - observado: nenhum arquivo de `docs/especificacao` foi alterado. Exemplos que alteram regra ou sentido: termo pendente só para aluno com matrícula (#85 D2); uso de imagem não bloqueia (#85 D3); responsável não financeiro só lê e não pede nem envia comprovante (#86 D1, #87 D1); cobrança em conferência continua visível no Início, sem "Pagar" (#89 D2); recibo do portal com CPF mascarado (#92 D5); "Marcar todos como lidos" grava dispensa e não leitura (#93 D3); e-mail de contato do cadastro acompanha a troca de e-mail da conta (#94 D3); débito automático só informativo (#92 D3).
  - efeito: a especificação deixa de dizer o que o código faz; a Sprint 11 (pagamento online) herda D1/D2 da #87 e D2/D3 da #92.
  - proposta: A3, no mesmo formato do #374 da Sprint 09.

- **R10-4 · DONO · especificação** — ver D1 e D2.

- **R10-5 · SUGESTÃO · teste** — o `e2e` `@critical` do portal roda sempre contra MSW; não há ponta a ponta contra o back-end real nesta sprint (consistente com as sprints anteriores e com o `AGENTS.md`).

- **R10-6 · SUGESTÃO · front** — a cobertura de linhas do front caiu 0,12 ponto (96,80% → 96,68%) e a de ramos aparece em 87,14%; sem risco hoje.

- **R10-7 · SUGESTÃO · processo** — 11 itens de portal registram "os canvas citados não são acessíveis ao agente" (D8/D9). Nenhum pixel foi comparado com o canvas; ver seção 9.

- **R10-8 · SUGESTÃO · processo** — seis branches locais `item/*` já mergeados, `ajustes/seguranca-repo-publico`, `docs/*` e a referência velha `origin/item/390-…` (apagar é do dono; `git fetch --prune`).

#### Desvios e decisões herdados dos itens

| Item | Desvio ou decisão | Classe | Destino |
|---|---|---|---|
| #85 D2/D3/D4/D5/D6 | termo pendente só com matrícula; imagem não bloqueia; `TERM_PENDING` do servidor; "modalidade nova" definida por `created_at`; boas-vindas na conclusão | muda requisito | A3 |
| #86 D1 a D7 | quem pede; permissão `STUDENT_MANAGE`; um pedido aberto por modalidade; Aprovar marca na hora; encerramento sem Aprovar; cobranças depois do pedido canceladas; prazos pela data local | muda requisito | A3 |
| #86 D5/D7 | o sistema encerra no 8º dia | exceção do dono | D1 |
| #87 D1 a D5 | quem envia; só unidade com chave Pix; permissão de cobranças; confirmar usa a data do envio; pausa por `block_pause_days` | muda requisito | A3 |
| #88 D2/D3 | sino do portal conta avisos; "reduzido" calculado do que existe | muda requisito | A3 |
| #89 D2/D3/D5/D6 | cobrança em conferência sem "Pagar"; aula alterada; "Nova" por 14 dias; total da família só do que a conta paga | muda requisito | A3 |
| #90 D1/D2 | selo Ativo/Inativo e carteirinha para o responsável não pagador | muda requisito | A3 |
| #91 D2/D4 | padrão "Todos os meus horários"; "Ver aviso" aponta para o comunicado mais recente do tipo | muda requisito | A3 |
| #91 D3, #93 D7, #94 D7 | nome do professor oculto na Agenda, mas mostrado como autor do aviso e em "treinos por modalidade" | exceção do dono | D2 |
| #92 D2/D3/D5 | pagar junto; débito automático informativo; recibo com CPF mascarado | muda requisito | A3 (a Sprint 11 herda) |
| #93 D3/D4 | "Marcar todos como lidos" é dispensa; abrir é `POST` | muda requisito | A3 |
| #94 D2 a D5 | edição por qualquer responsável; e-mail de contato acompanha a conta; ações de dados visíveis e desabilitadas; portal reduzido | muda requisito | A3 |
| #90 D3 | carteirinha sem internet só em `localStorage` (Sprint 21 cuida do resto) | técnico aceitável | registrar |
| #91 D8, #88 D4 | agenda sem internet e abas provisórias, resolvidas depois | técnico aceitável | registrar |
| #95 D1 a D4 | estados como bloco; vazio sem cartão em `ModalityConfirmations` | técnico aceitável | registrar |
| #390 Limitação | data fixa `2026-11-10` no fixture | sugestão com prazo | A2 |
| #363 | CA4 da #343 aprovado, D1 (cache em `push`) descartada | técnico aceitável | registrar |

#### Ajustes propostos

**A1 · Estabilizar o `UnitSettingsPage.test.tsx` CA17 no `test:cov`** (resolve R10-1) — antes do PR
- Tipo: novo item (formato das #385 e #386).
- O que mudar e onde: `frontend/src/features/units/UnitSettingsPage.test.tsx`, CA17 (linha 102): medir o tempo gasto (o arquivo inteiro leva cerca de 20 s), achar o passo lento (cadeia de `userEvent.type` em muitos campos ou `findBy` em cascata) e trocar por `fireEvent`/espera por estado, sem aumentar o timeout para esconder.
- Como provar: `npm run test:cov` completo 10 vezes sem falha; isolado com `--maxWorkers=2` e sem; medir antes e depois.
- Tamanho: pequeno.
- Texto de issue proposto: título "Teste UnitSettingsPage CA17 instável no test:cov: medir e estabilizar"; entregar a medição e a correção só do teste; "Pronto quando": 10 de 10 execuções de `test:cov` verdes; marco: Sprint 10 (antes do PR para `main`).

**A2 · Tirar a data fixa de `BillingFixtures`** (resolve R10-2) — antes do PR
- Tipo: novo item.
- O que mudar e onde: `BillingFixtures.charge` passa a calcular `due_on` a partir de hoje no fuso da unidade (ou cada IT passa o vencimento), e `ChargeNoticeIT` deixa de depender da janela do agendador real (desligar o `@Scheduled` nesse IT ou usar o relógio fixo também no agendador).
- Como provar: `ChargeNoticeIT` verde com o relógio da JVM adiantado (`-Duser.timezone`/`faketime` ou a data alterada no teste) para 2026-11-01 e 2026-12-01; `verify` completo.
- Tamanho: pequeno.
- Texto de issue proposto: título "Teste ChargeNoticeIT e BillingFixtures dependem da data de hoje: remover o vencimento fixo de 2026-11-10"; "Pronto quando": `ChargeNoticeIT` verde em qualquer data; marco: Sprint 10.

**A3 · Ajustar a especificação com as decisões da Sprint 10** (resolve R10-3) — antes do PR (ou logo depois, sem bloquear)
- Tipo: ajuste de especificação (item no formato do #374).
- O que mudar e onde: `docs/especificacao/09-matricula.md` (RN-MAT-11 a 18), `14-portal-do-aluno.md` (RN-POR-01 a 08), `13-cobrancas*.md`/`15`, `03-auditoria-e-lgpd.md`, conforme a tabela da seção anterior; incluir quem pede/aceita/envia (aluno adulto e responsável financeiro), o que o responsável não financeiro faz, recibo mascarado, dispensa de aviso, e-mail de contato.
- Como provar: cada decisão "muda o comportamento" aponta a regra ajustada; `grep` das decisões D* das #85 a #94 contra o texto novo.
- Tamanho: médio.

**A4 · Decisões do dono** — antes do PR
- Responder D1 e D2 abaixo e, conforme a resposta, ajustar a descrição do marco ou a especificação.

**A5 · Limpar branches** (R10-8) — depois do PR; é do dono.

Ordem sugerida: A1, A2, A4, A3 antes do PR `dev`→`main`; A5 em qualquer momento.

#### Decisões do dono

- **D1.** O bullet "Um pedido do portal nunca altera a matrícula sem ação da gestão" convive com a RN-MAT-18 (o sistema encerra no 8º dia o "Solicitar encerramento"). Opção recomendada: aceitar que o bullet vale para troca de turma, nova modalidade e troca de plano (RN-MAT-16) e corrigir o texto do marco para "pedido de troca ou nova modalidade nunca altera a matrícula sem ação da gestão; o de encerramento segue a RN-MAT-18". Motivo: o comportamento está na especificação 1.2 e testado (`PortalRequestClosureIT`).
- **D2.** O nome do professor: oculto na Agenda (#91 D3), mas mostrado como autor do aviso (#93 D7) e em "treinos por modalidade" (#94 D7); e-mail, telefone e WhatsApp nunca saem. Opção recomendada: mostrar o nome em todas as telas (a RN-POR-06 oculta o contato, não o nome) e ajustar a Agenda. Motivo: tira a inconsistência e o card da #94 pede o nome.

#### Ações do dono

1. Abrir o PR `dev`→`main` com o método "Merge" só depois de A1 e A2 mescladas e de D1/D2 respondidas.
2. Conferir no navegador, com `docker compose up -d --wait`, `bash infra/scripts/prepare-runtime-db.sh`, o back-end local e `npm run dev` em `frontend/`, em 360 px e nos temas claro e escuro: `/portal/termo` (aceite com e sem uso de imagem), `/portal` (Início, cartões da família), `/portal/agenda` (grade em 768 px e lista abaixo), `/portal/financeiro` (pagar junto, recibo), `/portal/avisos`, `/portal/carteirinha` (selo e cópia sem rede), `/portal/pedidos`, minhas informações; e na gestão `/cobrancas/conferencia` e `/pedidos`. Olhar contra os canvas `PortalTermo`, `PortalMTermo`, `PortalInfo`, `PortalPedidos`, `PortalMInfo` e os demais citados nas issues #85 a #95, contraste e foco por teclado.
3. Decidir sobre a limpeza de branches (A5).

#### O que só um navegador ou uma pessoa prova

- Por proxy: 360 px sem rolagem horizontal e axe claro/escuro (Playwright em Chromium, 81 testes verdes em 3 execuções) e jsdom nos testes de tela.
- Dependem de pessoa: comparação com o canvas (nenhum item pôde acessar os canvas), contraste real das cores da organização na faixa da carteirinha, leitura de PDF/comprovante no navegador do aluno, zoom e foco com teclado, comportamento real do cache `localStorage` da carteirinha sem rede.
- Teste a propor, sem criar: ponta a ponta do aluno enviando o comprovante pela aba Financeiro contra o back-end real com Pix configurado (hoje só `comprovantes.spec.ts` da gestão e ITs).

#### Riscos para a sprint seguinte

- Sprint 11 (pagamentos online) herda: "Pagar" abre hoje o `PixProofDialog` (#89 D10, #92 D2) e deve trocar de destino sem mudar a tela; o cartão de débito automático é só informativo (#92 D3); o recibo do portal sai mascarado (#92 D5).
- Sprint 12 (inativação): "reduzido" é calculado do que existe (#88 D3); a regra completa (bloqueio por atraso, reativação, 12 meses do ex-aluno) e o selo "Nova" com correção (#89 D5, #94 D7) entram na mesma consulta.
- Sprint 13: "Baixar meus dados" e "Excluir minha conta" estão visíveis e desabilitados (#94 D4); o "Precisa de atenção" lerá `/pedidos` (#86 D8).
- Sprint 21: copia local de agenda e do resto do portal e o app instalável (#90 D3, #91 D8).
- Prazo: exceções do `osv-scanner.toml` vencem em 2027-01-15; o override do guava no `pom.xml` deve sair quando o `swagger-parser` atualizar (#94).
- Gatilho de data: R10-2 (a partir de 2026-10-31).
- `depende de você`: nenhuma issue do marco; o repositório é público e a trava da Sprint 22 segue valendo.

---

### Rodada 2 — APROVAR — 2026-10-28, estado c5a4b096c83f67e512afc7612c81f6b74f1b944e

#### Resumo e veredito

Desde a rodada 1 (`aad58ec`) entraram quatro PRs de squash na `dev`, todos com `Closes #N`, base `dev`, 1 commit e `ci-gate` `SUCCESS`: #406 (#402, A2), #408 (#403, A1), #409 (#404, A3 e D1/D2) e #410 (#405, D2). As quatro issues estão no marco e fechadas, com "feito" no `specs/backlog.md`, e cada arquivo de item tem Rodada 1 `OK` e Entrega. Nenhum achado BLOQUEIA resta. O clone limpo de `c5a4b09` passou em tudo, e o `test:cov` passou 2 de 2 (na rodada 1 reprovou 2 de 4). Sobra uma sugestão, sem ajuste na Sprint 10, que segue na Sprint 11 pela #407. Resultado: `APROVAR`, isto é, pode abrir o PR `dev`→`main` com "Merge"; não é liberação da Sprint 11.

Pré-condições: `dev` limpa e igual a `origin/dev` (`c5a4b09`); `main` em `0d8e16b`; nenhum PR aberto contra `dev`; o remoto só tem `main` e `dev`; os branches `item/*` locais são de itens já mergeados; `gh auth` válido; Docker 29.8.1 ativo; Java 21.0.12 (build), Node 24.21.0.

#### Reconferência dos achados da rodada 1

| Achado | Status agora | Evidência |
|---|---|---|
| R10-1 `test:cov` instável (UnitSettingsPage CA17) | PASSOU, com ressalva (vira R10-9, SUGESTÃO) | #408 trocou `user.type`/`user.clear` por `fireEvent.change` e `userEvent.setup({ delay: null })`; clone limpo: `npm run test:cov` 2 de 2 verdes (160 arquivos, 1405 testes, linhas 96,68%, 294 s). Ressalva abaixo |
| R10-2 data fixa em `BillingFixtures`/`ChargeNoticeIT` | PASSOU | #406: `FAR_DUE = 2100-01-10`, relógio do job em `2100-01-05`; `grep 2026` nos arquivos tocados vazio (item #402); o agendador real nunca vê esse vencimento, então o teste não depende mais da data da JVM. `verify` do clone: 860 ITs verdes em 2026-10-28; não adiantei o relógio da máquina |
| R10-3 especificação sem as decisões | PASSOU | #409: diff de `docs/especificacao` em `03`, `05`, `09`, `10`, `12`, `13`, `14` com RN-MAT-11 a 18, RN-COB-13/14/16, RN-INA-08, RN-COM-08, RN-AUD-02, RN-POR-03/05/06a/08, RF-POR-01 a 07; `docs/openapi.yaml` ganha `teacherName`. Item #404 CA1 a CA9 PASSOU na verificação |
| D1 (encerramento no 8º dia) | PASSOU | a descrição do marco 11 diz agora "Um pedido de troca de turma, de plano ou de nova modalidade nunca altera a matrícula sem ação da gestão; o de encerramento segue a RN-MAT-18" (`gh api .../milestones/11`) |
| D2 (nome do professor) | PASSOU | #410: `PortalSchedule` junta `teacher` (`lesson.teacher_id` é `not null`, V25) e devolve só `teacherName` (o substituto, quando há); `PortalScheduleIT.teacherNameOnly`; MSW, `PortalLessonItem.tsx` e `pt-BR.json` alinhados; RN-POR-06a nova e nota em RN-PRO-04 |
| R10-8 branches velhos | mantido | seis `item/*` locais, `ajustes/seguranca-repo-publico`, `docs/*` e três referências velhas `origin/item/40x-…` (`git fetch --prune`); é do dono |

#### "Pronto quando" do marco (texto novo, 5 bullets)

| Bullet | Status | Evidência |
|---|---|---|
| Nenhuma tela antes do aceite do termo | PASSOU (mantido da rodada 1) | sem mudança em `terms.requireAccepted`; `verify` do clone verde (860 ITs); `e2e` `portal-termo.spec.ts` verde |
| Nenhuma resposta traz dado de aluno não vinculado | PASSOU (mantido, com a ressalva da rodada 1) | ITs de isolamento no `verify` verde. O diff novo (#405) só junta o nome do professor da aula do aluno matriculado |
| Pedido de troca/plano/nova modalidade não altera a matrícula sem a gestão; encerramento segue a RN-MAT-18 | PASSOU | texto do marco corrigido (D1); `PortalRequestClosureIT` e `PortalRequestControllerIT` verdes no clone |
| Comprovante de Pix pausa o prazo e, recusado, o prazo volta | PASSOU (mantido) | `ProofBlockDateIT`, `PaymentProofsIT`; e2e `comprovantes.spec.ts` verde |
| "Pronto quando" geral vale para cada item | PASSOU, com a ressalva R10-9 | parágrafo seguinte |

Resultado do marco: 5 de 5 (o 5º com a ressalva R10-9).

Regras de todo item, no que mudou: cobertura de back-end 98,45% (149 linhas não cobertas de 9.637; 0 em `billing`, `payment` e `isolation`; "All coverage checks have been met"); front 96,68% de linhas, pisos de 100% atendidos. Rota nova: nenhuma (um campo a mais em `/portal/schedule`; `docs/openapi.yaml` atualizado; `OpenApiContractTest` e `ContractRoutesTest` dentro do `verify`). Auditoria, centavos, tradução e recurso por organização: não se aplicam nos quatro PRs (teste, texto e um campo de leitura). Rastreabilidade: a RN-POR-06a, nova, não é citada pelo nome em teste algum (`grep` vazio); o `PortalScheduleIT.teacherNameOnly` a cobre pelo comportamento (SUGESTÃO R10-10). Quatro estados, 360 px e axe: `e2e` 81 de 81 em 2 execuções, sem tela nova. As demais linhas seguem como na rodada 1.

#### Clone limpo (Rodada 2)

Clone de `dev@c5a4b09` no scratchpad, `COMPOSE_PROJECT_NAME=revs10b`; os contêineres e volumes `revctcore7-*` e `competent_neumann` continuam parados e não são meus; não toquei.

| Passo | Resultado real | Rodada 1 |
|---|---|---|
| ambiente + `prepare-runtime-db.sh` | 3 serviços saudáveis; exit 0 | igual |
| `verify` do back-end | BUILD SUCCESS em 14 min 30 s; 162 rápidos + 860 de integração, 0 falhas, 0 ignorados; "All coverage checks have been met"; 98,45% de linhas | 162 + 859, 15 min 42 s, 98,47% |
| migrações e readiness | "now at version v51"; `/actuator/health/readiness` `UP` na `:8081` | igual |
| seed 2x | exit 0 nas duas; 24 arquivos; contagens `term_acceptance` 2, `portal_request` 2, `payment_proof` 1, `charge` 4 | iguais |
| `npm ci`, `lint`, `typecheck`, `build` | exit 0 em todos | igual |
| `npm run test:cov` | 2 execuções, 160 arquivos, 1405 testes, 0 falhas (294 s a primeira); linhas 96,68% | 2 vermelhas em 4 |
| `npm run e2e` | 81 de 81 em 2 execuções (2,3 e 2,2 min) | 81, 3 verdes |
| imagens | back-end com UID 10001, readiness `UP` na `:8081`, `/actuator/health` na `:8080` 404; front-end `/` 200, `/alunos` 200, `/api/x` 404; etiquetas `rev10b` removidas e `docker images -q` igual ao de antes | igual |
| recomeço | `down -v`, `up -d --wait`, `prepare-runtime-db.sh`, `down -v`: nenhum contêiner nem volume `revs10b` restou; clone removido | igual |

Comparação com a rodada 1: back-end com um teste a mais e cerca de 1 min mais rápido; cobertura de linhas praticamente igual; `test:cov` sem falha nas duas execuções. Passos do README que não bastaram: nenhum.

#### CI e proteções

`ci-gate` exigido em `dev` e `allow_force_pushes = false` (`gh api .../branches/dev/protection`); `Publish` em `dev`: 8 últimos runs `success` (o mais recente `40790372758` em `c5a4b09`); `ci-gate` `SUCCESS` nos PRs #406, #408, #409 e #410. `git diff aad58ec dev -- .github osv-scanner.toml .gitleaksignore backend/pom.xml frontend/package.json infra` vazio: nada novo em workflows, exceções (as duas `GHSA-…` seguem até 2027-01-15), dependências e infra. `gh secret list` e `gh variable list` vazios (publicação pulada, limitação aceita até a #192). Repositório `public`, esperado até antes da Sprint 22.

#### Achados novos

- **R10-9 · SUGESTÃO · teste / front** — local: `specs/itens/403-unit-settings-ca17-instavel.md` (desvio do dono, 28/10/2026) e issue #407 (Sprint 11, aberta)
  - esperado: `test:cov` estável sem depender de folga de CPU (CA4 da #403: 10 de 10).
  - observado: o CA17 caiu de 6,6-7,4 s para 0,8-3,4 s isolado, mas o dono trocou o CA4 por "CA17 20 de 20 isolado" e "suíte com `--maxWorkers=2` verde"; com o paralelismo padrão a #403 mediu 8, 10 e 5 timeouts de 20 s em outros arquivos numa máquina saturada. A causa geral foi para a #407. Nesta rodada, com a máquina livre, 2 de 2 verdes.
  - efeito: o `test:cov` do PR `dev`→`main` (que roda tudo) pode reprovar ao acaso em máquina mais lenta; o `ci-gate` dos quatro PRs desta rodada passou.
  - proposta: sem ajuste na Sprint 10; segue pela #407 (Sprint 11). Se o PR `dev`→`main` reprovar só por timeout de 20 s, reexecutar o job e registrar na #407.

- **R10-10 · SUGESTÃO · teste** — a RN-POR-06a, nova, não aparece no nome de nenhum teste; o `PortalScheduleIT.teacherNameOnly` cobre o comportamento. Acrescentar o código ao `@DisplayName` quando a Sprint 11 tocar o arquivo.

R10-5, R10-6 e R10-7 (sugestões da rodada 1) seguem como estavam; R10-4 está encerrado (D1 e D2 respondidas e aplicadas).

#### Ajustes propostos

- **A1, A2, A3 e A4: concluídos** (#408, #406, #409 e decisão do dono aplicada em #409 e #410).
- **A5** (limpar branches, R10-8): feita pelo agente a pedido do dono, depois do PR de registro; remoto já limpo, branches locais integradas apagadas.
- **A6** (R10-9, sugestão): sem ajuste na Sprint 10; na Sprint 11, pela #407, que já existe com "Pronto quando" de 10 execuções seguidas. Acréscimo sugerido: registrar se o `ci-gate` do PR `dev`→`main` reprovou por timeout.

Antes do PR `dev`→`main`: nada. Depois: A5 e A6.

#### Decisões do dono

Decisões do dono de 28/10/2026, todas já aplicadas:

- **D1:** opção recomendada. O bullet vale para troca de turma, nova modalidade e troca de plano; o encerramento segue a RN-MAT-18. Descrição do marco 11 corrigida (#409).
- **D2:** opção recomendada. O nome do professor aparece em todas as telas do portal; e-mail, telefone e WhatsApp nunca saem. Agenda ajustada (#410, RN-POR-06a).
- **Desvio do CA4 da #403:** o CA4 "`test:cov` 10 de 10" foi trocado por "CA17 20 de 20 isolado" e "suíte com `--maxWorkers=2` verde"; a causa geral do `test:cov` instável em máquina saturada foi para a #407 (Sprint 11).

#### Ações do dono

1. Abrir o PR `dev`→`main` com o método "Merge" (nunca squash).
2. Conferir no navegador, como na rodada 1 (Ações do dono, item 2), e agora também `/portal/agenda` em 360 px e no tema escuro: o nome do professor aparece em cada aula (e o substituto na aula alterada), sem foto, telefone nem e-mail.
3. Decidir sobre a limpeza de branches (A5).

#### O que só um navegador ou uma pessoa prova

Igual à rodada 1: comparação com o canvas (nenhum item pôde acessar os canvas), contraste real e foco por teclado, zoom, cache `localStorage` da carteirinha sem rede. Proxy: 81 testes `e2e` com axe nos dois temas e `scrollWidth <= innerWidth` a 360 px (2 execuções verdes). Teste a propor, sem criar: ponta a ponta do aluno enviando o comprovante contra o back-end real com Pix configurado.

#### Itens para as próximas sprints (sem issue nova)

- **#407 (Sprint 11):** causa geral do `test:cov` instável em máquina lenta (R10-9); registrar se o `ci-gate` do PR `dev`→`main` reprovar por timeout de 20 s.
- **R10-5:** ponta a ponta do aluno contra o back-end real, quando houver Pix configurado.
- **R10-6:** cobertura de ramos do front em 87,14%; acompanhar.
- **R10-7:** comparação com o canvas, que nenhum item conseguiu acessar.
- **R10-10:** acrescentar a RN-POR-06a ao `@DisplayName` de `PortalScheduleIT.teacherNameOnly` quando a Sprint 11 tocar o arquivo.

#### Riscos para a sprint seguinte

Os da rodada 1 continuam (Sprint 11 herda "Pagar" via `PixProofDialog`, débito automático informativo e recibo mascarado), com duas mudanças: o `test:cov` instável é agora a #407, na Sprint 11, e a especificação já diz o que o portal faz (RN-MAT-11 a 18, RN-POR-06a). O gatilho de data de 2026-10-31 (R10-2) deixou de existir. Prazo: exceções do `osv-scanner.toml` vencem em 2027-01-15. `depende de você`: nenhuma issue do marco.
