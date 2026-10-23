# Revisão da Sprint 08 · Cobranças

### Rodada 1 — AJUSTAR — 2026-10-23, estado 23dcb1a6ab9032f7f4345542775aa098fcedacbe

#### Resumo e veredito

A Sprint 08 entregou a geração diária de cobranças com primeira cobrança na matrícula, a lista, o painel (cancelar e renegociar), a prévia da geração, o pagamento no balcão com recibo em PDF e página pública, a cobrança avulsa com geração adiantada e os recebimentos do dia (issues #71 a #77), mais a medição de dois testes instáveis (#318 e #320). As 9 issues do marco estão fechadas por PR de squash com `ci-gate` verde. O clone limpo passou: back-end 149 + 638 testes, 98,30% de linhas e 100% em `billing`, `payment` e `isolation`; front 1075 testes, 96,76% de linhas e 100% em `features/payments`; e2e 35; imagens construídas; seed idempotente. Os cinco bullets específicos do marco passam, um deles (bolsa de 100%) só em parte: "segue Ativo" não tem asserção (R08-7). Não há BLOQUEIA. Há nove AJUSTAR (R08-1 a R08-9), e a maioria é texto de especificação, de issue e de teste: 17 decisões "muda o comportamento" sem a especificação ajustada (R08-4), encaixes da sprint sem destino nas issues da Sprint 09 e o A4 da Sprint 07 nunca criado (R08-5), `ChargeRenegotiationIT.invalid` com causa confirmada e fixa (R08-1), o item #77 mesclado com o `verify` vermelho (R08-2), o padrão de teste de tela instável que reapareceu e falhou no meu `test:cov` (R08-3) e o gate de 100% do front que não alcança `features/charges` (R08-6). O dono decide quatro coisas (D1 a D4). Resultado: `AJUSTAR`.

#### Estado revisado

| | |
|---|---|
| `dev` | `23dcb1a6ab9032f7f4345542775aa098fcedacbe` (igual a `origin/dev`; clone novo) |
| `main` | `8807a09d50c1bf11c4553c2ee470376fdc4e3456` (PR #313 da Sprint 07) |
| Data | 2026-10-23 |
| Ferramentas | Java 21.0.12 (build; o padrão da máquina é o 25.0.4), Node 24.21.0, Docker 29.8.1, gitleaks 8.30.1 (a mesma do CI) |
| Diff | `git diff origin/main...origin/dev`: 157 arquivos, 13.694 inserções, 191 remoções (back-end main: 32 arquivos, 2.147 linhas; back-end test: 34 arquivos, 4.089 linhas; front: 71 arquivos; `.github`: 2 arquivos; `backend/pom.xml`: 1 linha, o PDFBox 3.0.5) |
| PRs de item | #314 (#71), #315 (#72), #316 (#73), #322 (#318), #323 (#320), #324 (#74), #325 (#75), #326 (#76), #327 (#77) |
| PRs fora de item | #317 (ci-gate estrito e cache do Playwright), #319 e #321 (`aguardar-ci.sh` e skills), #328 (repositório público e regras do gitleaks), #329 (teste da agenda); #312 (ajustes da revisão da Sprint 07) é anterior ao marco |

Pré-condições: o checkout de trabalho do agente estava em `ajustes/seguranca-repo-publico` e a `dev` local atrás da origem; por isso a revisão rodou só em clone novo de `origin/dev` (o mesmo método da Sprint 07). Sem PR aberto contra `dev` (o branch de ajustes não tem PR). As 9 issues do marco estão fechadas e como "feito" no `specs/backlog.md`; nenhuma tem a etiqueta `depende de você`; todas foram abertas por `DevDosAnjos`. Sobras de branch (apagar é do dono): `item/75-…`, `item/76-…`, `item/77-…`, `ajustes/teste-agenda-semana`, `ajustes/seguranca-repo-publico` (este com 1 commit de `AGENTS.md` ainda fora de `dev`).

#### Trilha de processo

Todos os PRs têm base `dev`, título `#N: <título da issue>`, `Closes #N`, um commit de squash em `dev` (nenhum merge commit entre `main` e `dev`) e `ci-gate` verde (8 checks `SUCCESS`, ou 5 pulados por pasta e 3 `SUCCESS` nos PRs só de teste ou de infra). Os 9 arquivos de item têm as seis seções, ao menos uma rodada de verificação `OK`, "Entrega" e nenhuma tarefa aberta, com uma exceção: a T3 do `320-…` segue marcada como aberta (é da fase de entrega, fechada pelo PR).

| Item | PR | Verificação | Observação |
|---|---|---|---|
| #71 Geração e primeira cobrança | #314 | Rodada 1 OK | dividido em 5 partes e 22 tarefas num PR só (o maior item da sprint: 56 KB de arquivo); a primeira regressão falhou em `PublicReceiptIT.unknownAndForever` (corrigido no teste) e em `S3FileStorageIT` (`ConnectException`, de ambiente) |
| #72 Lista de cobranças | #315 | Rodada 1 OK | `e2e` do PR falhou em `recuperar-senha.spec.ts`, fora do diff; virou a #320 |
| #73 Painel da cobrança | #316 | Rodada 1 CORRIGIR_CODIGO, 2 OK | CA20 da aba Financeiro falhou 2 de 2 no `test:cov` (corrigido no teste); `e2e` registrado como 30 na verificação e 34 na construção |
| #74 Prévia da geração | #324 | Rodada 1 OK | |
| #75 Pagamento no balcão | #325 | Rodada 1 OK | nova dependência PDFBox 3.0.5 (Apache 2.0), aprovada no `osv` do CI |
| #76 Avulsa e adiantadas | #326 | Rodada 1 OK | |
| #77 Recebimentos do dia | #327 | Rodada 1 OK | **o `verify` da construção reprovou em `ChargeRenegotiationIT.invalid` e o item foi verificado e mesclado assim** (R08-2); o CI do PR ficou bloqueado por limite de cobrança da conta (comentário no PR) e passou depois, na reexecução |
| #318 CA20 instável | #322 | Rodada 1 OK | o `CA20` do painel deu 0/20; o instável medido foi o CA10 da agenda (1/20), corrigido com `delay(100)` |
| #320 Recuperar senha e2e | #323 | Rodada 1 OK | 0/20, nada corrigido; causa não encontrada (ver "Testes instáveis") |

Desvios de processo: nenhum merge sem rodada `OK` e nenhum PR sem `ci-gate`; mas a regressão completa vermelha do #77 não impediu a rodada `OK` (R08-2). Fora de item: 5 PRs (#317, #319, #321, #328, #329), todos do dono ou pedidos por ele, com `ci-gate`. A trilha do #71 teve uma única rodada de verificação para 22 tarefas, contra as 8 rodadas do #63 na Sprint 07 (S7 daquela revisão: manter o item pequeno; o #71 não manteve).

#### "Pronto quando"

**Do marco** (descrição do marco 9, `gh api repos/DevDosAnjos/ctcore/milestones/9`)

| Bullet | Status | Evidência |
|---|---|---|
| Rodar a geração duas vezes no mesmo dia não cria nada repetido | PASSOU | `MonthlyGenerationIT` CA11 (duas rodadas criam uma só; cancelada do mês não é recriada; "o banco recusa duas do mesmo mês", índice único de `V39`) e `ChargeGenerationJobIT` CA11 (duas execuções simultâneas, 12 modalidades, nenhuma falha); no `verify` do clone: 638 IT, 0 falhas |
| Concluir uma matrícula cria a cobrança do primeiro mês cheio (e a taxa, se ligada) numa transação só | PASSOU | `EnrollmentConclusionIT` CA18, CA19 (mensalidade cheia de R$ 150,00 e taxa de R$ 50,00, vencimento hoje, tolerância 5) e CA23 (gatilho de banco temporário na tabela `charge` faz a taxa falhar e desfaz a matrícula e a mensalidade); `FirstChargesIT` CA18 a CA20 |
| Aluno com bolsa de 100% não recebe mensalidade e continua Ativo | PASSOU, com ressalva | sem mensalidade: `MonthlyGenerationIT` CA12, `FirstChargesIT` CA18 (o ponteiro de geração fica) e `EnrollmentConclusionIT` CA19 (`charges` vazio). "Continua Ativo": nenhum teste afirma o `status` da matrícula ou da modalidade depois de uma bolsa de 100% (R08-7); o comportamento decorre de a geração não tocar no status |
| Mudar um plano não altera nenhuma cobrança já gerada sem a escolha explícita do Administrador | PASSOU | `RepricingIT` CA25 (`KEEP`, a paga e a vencida não mudam; só `ADJUST` recalcula as abertas futuras) e CA26 (padrão falso não gera diferença); troca agendada vale na primeira mensalidade (`MonthlyGenerationIT` CA13); a composição é gravada na cobrança (`plan_cents`, `benefit_cents`, D2 da #71) |
| Fluxo ponta a ponta "matrícula com primeira cobrança" na suíte | PASSOU | `frontend/e2e/matricula.spec.ts:142`, `@critical Matrícula com primeira cobrança`; `npm run e2e`: 35 passaram |
| Todos os itens do "Pronto quando" geral valem para cada item | ver tabela seguinte | |

Resultado do marco: 5 de 5 bullets específicos passaram (um com ressalva); o bullet geral está abaixo (3 pontos com ressalva).

**De todo item (plano de sprints)**

| Regra | Status | Evidência ou "não se aplica nesta sprint: motivo" |
|---|---|---|
| Regras citadas com ao menos um teste | PASSOU | os testes citam os "CA" e não o código da regra (S2 da Sprint 07 segue valendo); varrido por comportamento: RN-COB-01 a 06, 12 (`MonthlyGenerationIT`, `ChargesIT`), RN-COB-09 e 10 (`ChargeRenegotiationIT`, `ChargeCancelIT`), RN-COB-11 (`OneOffChargeIT.leavesMonthlyAlone`), RN-COB-12 e 16 (`PaymentIT`, `PaymentReceiptIT`, `ReceiptPdfTest`), RN-MAT-05, 07, 19 (`FirstChargesIT`), RN-PLA-05 (`RepricingIT`), RN-BEN-04 (`MonthlyGenerationIT` CA12). Sem teste: o aviso por e-mail do reajuste (RN-PLA-04) e o e-mail "Cobrança gerada" foram adiados para a #78 (D9 da #71, D1 da #74), mas a #78 não os lista no "Pronto quando" (R08-5) |
| Cobertura mínima 80% e 100% nos pacotes críticos | PASSOU, com ressalva | back-end 98,30% de linhas (7284 linhas; 0 não cobertas em `billing` com 623, `payment` com 214 e `isolation` com 64), "All coverage checks have been met"; front 96,76% de linhas (93,33% de instruções), `test:cov` exit 0 com `--maxWorkers=2`; `features/payments` 100% de linhas. Ressalva: `features/billing` não existe e as telas de cobrança vivem em `features/charges`, com 97,67% de linhas e **fora do piso de 100%** (R08-6, D2) |
| Rotas novas com teste de isolamento entre organizações e entre perfis | PASSOU | 13 rotas novas no contrato; `PaymentIsolationIT` (inclui RLS do PDF e numeração por unidade), `ChargesIsolationIT`, `ChargeActionsIsolationIT`, `NextChargeIsolationIT`, `OneOffChargeIsolationIT`, `DailyReceiptsIsolationIT`, `PublicReceiptIT` (404 uniforme), e os `permissions` dos ITs de cada rota; RLS conferida em `V39` a `V41` |
| OpenAPI, implementação e MSW batendo | PASSOU | `OpenApiContractTest` e `ContractRoutesTest` dentro do `verify`; `billing.msw.test.ts`, `charges.msw.test.ts`, `charge-actions.msw.test.ts`, `chargePreview.msw.test.ts`, `payment.msw.test.ts`, `oneOffCharge.msw.test.ts`, `dailyReceipts.msw.test.ts` no `test:cov` |
| Auditoria em toda escrita | PASSOU | `charge.created`, `charge.canceled`, `charge.renegotiated`, `charge.next_due_changed`, `charge.generation_adjusted`, `plan.repriced`, `payment.registered`; testes de auditoria conferem que não há CPF nem e-mail no `after_data` (`EnrollmentConclusionIT` CA19, `PaymentIT.audit`); o e-mail do recibo não vai para a auditoria (só `receiptRequested`) |
| Valores em centavos, operações idempotentes, lotes tudo ou nada | PASSOU | colunas `bigint` com `check >= 0` (`V39`); nenhum `double` ou `float` em `billing` e `payment` fora das coordenadas do PDF; geração por unidade tudo ou nada (`ChargeGenerationJobIT` CA15), reajuste tudo ou nada (`RepricingIT` CA27), conclusão tudo ou nada (CA23); idempotência da avulsa por `requestId` (`OneOffChargeIT.idempotent`) e do pagamento por trava de linha com 409 na repetição (`PaymentIT.raceAndExternalId`) |
| Quatro estados, 360 px e axe nos dois temas | PASSOU por proxy; NÃO CONFERIDO em navegador real | todas as telas e janelas novas têm teste de estados e axe claro e escuro a `innerWidth = 360` (jsdom); contraste real e rolagem horizontal real não foram medidos; os canvases estavam inacessíveis ao agente em todos os itens (ver "Ações do dono") |
| Textos só nos arquivos de tradução | PASSOU | `npm run lint` (regra de texto literal) exit 0 |
| Recurso novo desligado por organização | não se aplica nesta sprint | nenhum item pede recurso liberado aos poucos (D8 da #72, D9 da #73) |
| Notas de versão | NÃO CONFERIDO (sem arquivo) | o repositório continua sem arquivo de notas; os itens dizem "só se existir" (S4 da Sprint 07, sem decisão) |

#### Clone limpo

Clone de `origin/dev@23dcb1a` no scratchpad. As portas padrão estavam ocupadas por um projeto que não é meu (`revctcore7-*`, mais `competent_neumann`), então usei `COMPOSE_PROJECT_NAME=revs08` e `POSTGRES_PORT=25432`, `STORAGE_PORT=28333`, `MAILPIT_SMTP_PORT=21025`, `MAILPIT_WEB_PORT=28025`. Etapas pesadas uma de cada vez; o back-end local (`spring-boot:run`) ficou de pé durante o `verify` por falha do meu script (não o encerrei antes), então o tempo do `verify` não é limpo.

| Passo | Comando | Resultado real |
|---|---|---|
| ambiente | `docker compose up -d --wait` (de `infra/`, sem `.env`) | 3 serviços saudáveis em 38 s |
| migrações e papel | `bash infra/scripts/prepare-runtime-db.sh` e `spring-boot:run -Dspring-boot.run.profiles=local` | readiness `{"status":"UP"}`; "now at version v41" (41 migrações; eram 38) |
| seed (2x, mais uma 3ª para contar) | `bash infra/scripts/seed.sh` | saída 0 nas três; 18 arquivos de seed (eram 17); 3 organizações; 309 linhas nas tabelas antes e 309 depois da 3ª execução (eram 300); 3 cobranças e 1 pagamento semeados |
| back-end | `./backend/mvnw -f backend/pom.xml verify` (Java 21) | BUILD SUCCESS em 13 min 24 s; 149 testes rápidos e 638 de integração, 0 falhas, 0 ignorados; "All coverage checks have been met"; 98,30% de linhas |
| front-end | `npm ci` (35 s), `lint` (34 s), `typecheck` (25 s), `build` (24 s) | exit 0 nos quatro; `npm ci`: 0 vulnerabilidades |
| front-end, testes | `npm run test:cov` | **1ª execução (paralelismo padrão): exit 1**, 1 falha em 1075: `AdvanceChargesDialog.test.tsx:98`, "estados: carregando, …" (R08-3); 2ª e 3ª execuções com `--maxWorkers=2`: exit 0, 124 arquivos, 1075 testes, 96,76% de linhas (93,33% de instruções), 5 min 32 s; a mesma suíte sem cobertura com `TZ=Pacific/Kiritimati`: 1075 de 1075 |
| e2e | `npm run e2e` | 35 passaram em 1,3 min (eram 33) |
| imagens | `docker build` do back-end (218 s) e do front-end (17 s) | construídas sem erro; **não subi os contêineres para provar** (uid, readiness, `/` 200), diferente da Sprint 07: ver "Limitações" |
| recomeço | `down -v` e subir de novo | não repetido nesta rodada |

Limpeza: contêineres, rede `revs08_default` e volumes `revs08_postgres-data` e `revs08_storage-data` removidos com `docker compose down -v`; imagens `revs08-backend` e `revs08-frontend` removidas pelo nome; a lista de imagens, volumes e redes é idêntica à de antes (conferida com `diff` contra o que gravei no início); os dois processos Java do `spring-boot:run` encerrados pelo número. Um segundo disparo do meu script (lançado por engano com o caminho dos logs errado) foi encerrado pelo número do processo. Intocados: `revctcore7-*`, `competent_neumann`, os volumes `ctcore_*` e `revctcore7_*`, as imagens do projeto. Baixei o `gitleaks.exe` 8.30.1 e fiz clones (comum e espelho) no scratchpad; foram apagados ao fim.

Comparação com a linha de base (Sprint 07, rodada 1): back-end 143 + 534 passou a 149 + 638 (cresceu), cobertura 98,06% passou a 98,30% (melhorou), `verify` 10 min 21 s passou a 13 min 24 s (piorou 30%, com a suíte 18% maior e o back-end local de pé durante a medição); front 961 testes a 96,57% passou a 1075 testes a 96,76% de linhas (instruções 92,89% para 93,33%): a cobertura subiu; e2e 33 passou a 35; `test:cov` 3 min 45 s passou a 4 min 42 s (1ª execução, com a falha) e 5 min 32 s (com `--maxWorkers=2`).

Passos do README que não bastaram: nenhum. O S5 da Sprint 07 está resolvido (o README da raiz cita `COMPOSE_PROJECT_NAME`).

#### CI e proteções

| Item | Estado real | Evidência |
|---|---|---|
| `ci-gate` exigido em `dev` | sim | `gh api repos/DevDosAnjos/ctcore/branches/dev/protection`: `contexts: ["ci-gate"]`, `strict: false`, push forçado e exclusão bloqueados, sem exigir revisão (o agente mescla) |
| `ci-gate` exige tudo | sim | `needs: [changes, backend, frontend, e2e, image-backend, image-frontend, security]` (o job `security` traz gitleaks, a prova das regras próprias e o OSV); só `success` ou `skipped` passa; `.result` nulo reprova |
| regra de `main` | sim | ruleset 25019105 `protege-main`, ativo |
| últimos runs | `Publish` em `dev`: 8 de 8 `success`; `CI` verde nos 9 PRs de item | `gh run list --branch dev`, `gh pr view` |
| workflows | mudaram | `ci.yml` (#317 e #328): cache do Playwright, `ci-gate` estrito, imagens em paralelo, prova das regras do gitleaks; `publish.yml`: timeout de 10 para 20 min. Ver R08-8 sobre o cache |
| exceções de vulnerabilidade | 2, ambas com motivo e validade | `osv-scanner.toml`: `GHSA-j9f9-w8pj-32f8` e `GHSA-pc63-qcmh-9cmg`, até 2027-01-15 (sem mudança) |
| dependências | 1 nova | `org.apache.pdfbox:pdfbox:3.0.5` (Apache 2.0, sem dependência nativa), com `osv` verde no CI; nada mais mudou em `pom.xml`, `package.json`, lock e Dockerfiles |
| segredos e variáveis da publicação | não existem (`gh secret list` e `gh variable list` vazios) | limitação aceita: até a Sprint 22 não há hospedagem |
| repositório | **público** | `gh api repos/DevDosAnjos/ctcore`: `visibility: public`; `secret_scanning` e `push_protection` ligados; validade de segredo e padrões não-provedor desligados; Dependabot de segurança desligado; 0 alertas de secret scanning |
| permissões do repositório | 2 colaboradores com push além do dono | `itallovidal` e `lpatros` (`gh api …/collaborators`); `dev` não exige revisão. Ver R08-12 |

**Mudanças fora dos itens (#328 e #329)**: coerentes. O #328 acrescenta a seção "Repositório público" ao `AGENTS.md`, o `.gitleaks.toml` (estende as regras padrão e soma quatro: Mercado Pago, chave secreta do Turnstile, senha em URL de conexão, senha literal em configuração), a prova `gitleaks-rules.test.sh` no job `security` (exigido pelo `ci-gate`) e uma linha de decisão em `docs/plano-de-sprints.md`. Rodei a prova com o gitleaks 8.30.1 do CI: 20 de 20 casos passam (o script chama `python3`, que não existe nesta máquina Windows; usei um calço de 3 linhas em `node` que não está no repositório: R08-13). O #329 (teste da agenda) não toca em código de produto. A regra "só a conta do dono instrui o agente" é coerente com o que encontrei (ver R08-12).

#### Achados

- **R08-1 · AJUSTAR · teste** — local: `backend/src/test/java/br/com/ctcore/billing/ChargeRenegotiationIT.java` linhas 102, 103, 108, 124 (e `ChargeActionsBase.java:46`)
  - esperado: teste que não depende da hora do dia em que roda; a regra do servidor é "firstDueOn não pode ser anterior a hoje" no fuso da unidade (`ChargeActions.java:214`).
  - observado: o teste monta as datas com `LocalDate.now()`. `CtcoreApplication` tem um bloco `static` que força o fuso padrão da JVM para UTC ("D4: toda data e hora em UTC"), e ele roda também nos testes (`MigrationIT:73` até afirma isso). Então `LocalDate.now()` do teste é a data em UTC, e a unidade de teste usa o fuso padrão `America/Sao_Paulo`. Entre 21:00 e 24:00 de Brasília a data UTC já é a de amanhã: `LocalDate.now().minusDays(1)` (linha 108) vira "hoje" para o servidor, que o aceita (201 em vez de 400) e o `invalid` reprova.
  - evidência: **causa confirmada por leitura de código; a falha em si não foi reproduzida** (rodei às 17:38 de Brasília, fora da janela). O experimento que parecia reproduzir o fuso (`JAVA_TOOL_OPTIONS=-Duser.timezone=Pacific/Kiritimati`, fuso aplicado como mostra o `user.timezone` no relatório do failsafe) passou 5 de 5, e isso é coerente com a causa: o bloco `static` devolve a JVM a UTC e o fuso externo não muda nada. A suspeita registrada no #77 ("o teste usa o fuso da JVM") estava na direção certa, mas o fuso da JVM não é o da máquina, é sempre UTC. O CI (UTC) falha pelo mesmo motivo, todo dia, de 00:00 a 03:00 UTC; o run do #327 passou porque rodou às 16:43 de Brasília.
  - efeito: `ci-gate` vermelho de modo intermitente à noite, em qualquer PR, e `verify` local vermelho (como no #77).
  - proposta: A1 (issue no formato das #318 e #320).
- **R08-2 · AJUSTAR · processo** — local: `specs/itens/77-recebimentos-do-dia.md` ("Construção", "Verificação", "Entrega"), PR #327
  - esperado: item só mescla com a regressão completa verde, ou com o vermelho tratado antes (AGENTS.md: o CI exige `verify`; o plano de sprints pede regressão limpa).
  - observado: o `verify` do #77 reprovou em `ChargeRenegotiationIT.invalid`; o agente rodou `jacoco:report jacoco:check` à mão sobre o `jacoco.exec` da execução interrompida para mostrar a cobertura, registrou a falha como "não é do item" e a rodada 1 saiu `OK` ("regressão completa não repetida"). O CI do PR só ficou verde depois, em outro horário.
  - evidência: texto do item; comentário do PR #327 (limite de cobrança da conta bloqueou o CI na primeira vez).
  - efeito: a `dev` foi mesclada sabendo de um teste vermelho, sem issue e sem dono.
  - proposta: A1 cobre o teste; acrescentar à skill de verificação (e ao `AGENTS.md`, se o dono quiser) que falha na regressão completa gera issue no formato das #318 e #320 antes da rodada `OK`, mesmo quando a falha é anterior ao item.
- **R08-3 · AJUSTAR · teste** — local: `frontend/src/features/charges/AdvanceChargesDialog.test.tsx:98`, `OneOffChargeDialog.test.tsx` (teste "estados da busca"), `frontend/src/features/payments/PublicReceiptPage.test.tsx`
  - esperado: `npm run test:cov` verde em toda execução (o CI roda esse comando e o `ci-gate` depende dele).
  - observado: na minha 1ª execução do `test:cov` (paralelismo padrão) 1 teste de 1075 falhou: `expect(await screen.findByText("Carregando...")).toBeInTheDocument()` em `AdvanceChargesDialog.test.tsx:98`. O handler responde com `await delay(50)` depois de `await user.type(...)`; sob carga o `userEvent` leva mais de 50 ms, a resposta chega antes da primeira consulta e o texto transitório já sumiu. É a mesma classe das duas correções anteriores: CA20 do painel (#73, corrigido com promessa controlada) e CA10 da agenda (#318, corrigido com `delay(100)`). O `delay(50)` com `findByText("Carregando...")` existe hoje em três arquivos; `OneOffChargeDialog.test.tsx` é o que o #76 viu falhar em 2 de 4 rodadas.
  - evidência: `logs` do meu `test:cov` (exit 1, 280 s); duas execuções com `--maxWorkers=2` passaram (332 s) e uma suíte sem cobertura em outro fuso também. A causa em `OneOffChargeDialog` e em `PublicReceiptPage` é provável pelo padrão, **não medida**.
  - efeito: o `ci-gate` pode reprovar por teste de tela sem relação com o PR; cada correção por arquivo deixou os outros com o mesmo defeito.
  - proposta: A2 (issue no formato das #318 e #320, corrigindo o padrão, não um arquivo: reter a resposta com uma promessa controlada pelo teste, como no #73).
- **R08-4 · AJUSTAR · especificação** — local: `docs/especificacao/` (`07`, `09`, `10`, `11`), decisões dos itens #71 a #77
  - esperado: "Mudou uma regra, muda primeiro a especificação" (AGENTS.md).
  - observado: os itens marcam 17 decisões como "muda o comportamento" (#71: 6, #72: 1, #73: 3, #74: 2, #75: 3, #76: 1, #77: 1) e `git diff origin/main...origin/dev -- docs/especificacao` é vazio. Mesmo achado do R07-2. As que mais pesam: só `COLLECTIONS` completo registra pagamento e o Professor não registra (#71 D4); valor final zero não gera mensalidade (D6, já na `10`, RN-COB-04); a primeira cobrança sai também ao adicionar modalidade a aluno ativo (D11); regra de reajuste "ainda não pagas" e diferença com vencimento em 7 dias (D13, suposição); alterar valor e vencimento é uma renegociação que cria cobrança nova do tipo parcela (#73 D4); "Administrador" em RN-COB-09 e 10 vale também para o Coordenador (#73, suposição); PDF do recibo aberto por link com o código, com nome e CPF completos (#75 D1); e-mail do recibo com link e não anexo (#75 D2); data do pagamento com hora do dia e sem limite para o passado (#75 D4); `unitId` na avulsa (#76 D1); quem tem leitura de `COLLECTIONS` vê os recebimentos do dia (#77 D3); situação financeira do aluno conta a avulsa em atraso (#72 D5, que difere da regra de inativação, RN-COB-11).
  - efeito: quem ler a especificação espera comportamento que o código não tem, e as sprints de inativação (12) e de pagamentos online (11) partem de regras que só existem em arquivos de item.
  - proposta: A3, depois das respostas de D1 a D3.
- **R08-5 · AJUSTAR · issues** — local: issues #78, #79, #104, #111 e a lista de "ajustes depois do PR" da Sprint 07
  - esperado: o que a sprint deixou para depois está, com "Pronto quando", na issue de destino (padrão do R06-3 e do R07-3, aceito pelo dono).
  - observado: nenhuma issue tem o bloco "Encaixe herdado da Sprint 08". Sem destino claro: (a) e-mail de aviso do reajuste de plano e da "diferença de reajuste" (RN-PLA-04 e RN-PLA-05, D9 da #71 e D5 da #75) e e-mail "Cobrança gerada" (D1 da #74): a #78 só cita os nomes dentro do texto da RN-COM-11, sem "Pronto quando"; (b) mover o e-mail "Pagamento confirmado" (modelo nascido na #75, D6) para o registro de e-mails automáticos, também na #78; (c) ligar "Enviar lembrete" no painel da cobrança, hoje desabilitado com o motivo escrito (D2 da #73): a #79 só cita a tela Em atraso; (d) ligar "Estornar" no painel: a #104 diz "no painel da cobrança", suficiente; (e) o painel "Precisa de atenção" ler `openDebt` (sugestão (c) da #71): nenhuma issue; (f) Sprint 12 ignorar `ENROLLMENT_FEE` e `ONE_OFF` no prazo (D11 da #71, D3 da #76): a #111 já traz isso na RN-INA-02 e na RN-INA-05, suficiente. E o **A4 da Sprint 07** (limpar comprovante de graduação enviado e não usado, "novo item ou parte da Sprint 13") não virou issue nem aparece em nenhuma: perdeu-se.
  - evidência: busca por "Encaixe herdado da Sprint 08", "origin-proof", "comprovante de graduação", "abandon" nas 196 issues; `gh issue view 78 79 104 111`.
  - efeito: os e-mails de cobrança podem sair da Sprint 09 sem teste; o comprovante abandonado fica sem dono e fora da anonimização da Sprint 13.
  - proposta: A4.
- **R08-6 · AJUSTAR · teste/qualidade** — local: `frontend/vite.config.ts:26-29`
  - esperado: "100% das regras de cobrança, pagamentos, assinatura, inativação e isolamento" (AGENTS.md), no front em `src/features/{billing,payments,subscription,deactivation}`.
  - observado: `features/billing` não existe. Toda a interface de cobrança (lista, painel, prévia, avulsa, adiantadas, recebimentos do dia) está em `features/charges`, que não tem piso de 100%. Cobertura de linhas de `charges` neste clone: 97,67% (`ChangeDueDialog` 90%, `ChargesPage` 92,85%, `CancelNextDialog` 94,73%, entre outros); `payments` está em 100%. O verificador do #72 já registrou "pasta nova charges fora do piso".
  - efeito: a regra de 100% descrita não alcança as telas de cobrança, que são onde mora a regra de dinheiro no front; o `ci-gate` não reprovaria um PR que a derrubasse.
  - proposta: A5, depois de D2.
- **R08-7 · AJUSTAR · teste (baixo)** — local: `MonthlyGenerationIT` CA12, `FirstChargesIT` CA18, `EnrollmentConclusionIT` CA19
  - esperado: o bullet "e continua Ativo" do marco tem uma asserção.
  - observado: os três testes afirmam "sem mensalidade" e o ponteiro, não o `status` de `enrollment_modality`/matrícula/aluno depois da bolsa de 100%.
  - efeito: uma mudança futura que inative quem não gera cobrança passaria nos testes.
  - proposta: A6.
- **R08-8 · AJUSTAR · CI (médio)** — local: `.github/workflows/ci.yml`, job `e2e`, passos do cache do Playwright
  - esperado: o cache de #317 reduz o tempo do `e2e`.
  - observado: o CI roda só em `pull_request`, então as caches nascem em `refs/pull/N/merge` e só aquele PR as lê; a `dev` nunca tem cache e todo PR novo começa sem ele (`gh api …/actions/caches`: todas as chaves `playwright-Linux-1.64.1` são de `refs/pull/327`, `328`, `329`). O passo `npx playwright install --with-deps chromium` (apt) levou de 22 s a **695 s** nos últimos runs (22, 23, 25, 62, 408, 561 e 695 s), e o job `e2e` do #327 durou 12 min 43 s, contra `timeout-minutes: 15`. O cache só vale para a segunda execução do mesmo PR.
  - evidência: `gh run view 40013609235`; durações do `e2e` nos últimos 12 PRs: 67 s a 763 s.
  - efeito: o `e2e` tem 2 min de folga num dia ruim do espelho do apt; é o que o dono viu como "job fantasma" e "CI lento".
  - proposta: A7.
- **R08-9 · AJUSTAR · infra (baixo)** — local: `infra/scripts/aguardar-ci.sh`
  - esperado: o aviso de "tempo estourou" e o de "já há watcher" levam a um resultado recuperável.
  - observado: reconstruí o episódio do #327 pelo código e pelo CI, sem o log da sessão. O CI do #327 levou 12 min 56 s (passo do Playwright, R08-8), acima dos 10 min máximos de um comando em primeiro plano; o tempo que estourou foi o da ferramenta, não o `CI_LIMITE` do script (2400 s), e o processo do watcher seguiu vivo. A segunda chamada achou a pasta de trava com um pid vivo e saiu com código 4, como projetado. Não achei erro na lógica de trava (`mkdir` atômico, `kill -0`, limpeza no `EXIT`, refeita se o pid está morto). Os defeitos reais: o resultado de um watcher que perdeu o terminal não fica em lugar nenhum (só no stdout que ninguém lê), e o código 4 não diz como ler esse resultado nem como encerrar o watcher; o cabeçalho diz "rodar em segundo plano" mas nada impede o primeiro plano.
  - efeito: o agente fica sem saber se o CI passou e sem saída que não seja esperar ou matar o processo à mão.
  - proposta: A8 (gravar o desfecho em `$TMPDIR/aguardar-ci-pr<N>.resultado` e imprimi-lo, com o pid, no código 4).
- **R08-10 · SUGESTÃO · segurança (público)** — local: histórico do GitHub, `refs/pull/204/head` e `refs/pull/198/head`
  - observado: `gitleaks git --log-opts=--all` num clone comum (dev e branches, 124 commits): "no leaks found". Num clone espelho que traz também os `refs/pull/*` (293 commits): 2 achados. (1) `cfd54bf`, arquivo `segredo-falso.txt`, regra `github-pat`, no PR #204 ("DESCARTAVEL prova segredo", fechado sem merge, commit "teste: segredo falso", usado para provar o gitleaks do CI em 15/10); (2) `6d5758a`, `LogOutputTest.java:87`, regra `generic-api-key`, a cabeça do PR #198 antes do squash (o `.gitleaksignore` cobre só o hash do squash `7be7dd1`). Pelo nome do arquivo e do commit, os dois parecem fictícios; o valor do primeiro tem o formato de um token do GitHub e está em ref pública permanente. Alertas do secret scanning: 0.
  - efeito: baixo se o token for inventado; se não for, está exposto desde 15/10.
  - proposta: ver "Ações do dono" 3.
- **R08-11 · SUGESTÃO · dados fictícios** — local: testes e fixtures (`backend/src/test`, `frontend/src`)
  - observado: o `AGENTS.md` pede domínios `.example` e `.test`. Os dados de teste usam também `x.com` (378 ocorrências), `exemplo.com` (317), `b.com` e `dominio.com`, que são domínios registrados; a Sprint 08 acrescentou 32 de `exemplo.com` e 5 de `x.com`. Os seeds (`infra/seed`) e as issues e PRs não têm e-mail real nem CPF fora dos conhecidos de teste (11144477735, 12345678909, 39053344705, 52998224725), nem telefone real; nenhuma issue, PR ou comentário tem e-mail, CPF ou telefone fora do padrão (varredura de 196 issues e 133 PRs).
  - efeito: um teste que mande e-mail de verdade para esses domínios chegaria a terceiros; hoje o envio é simulado e não vi caso. Não conferi caso a caso.
  - proposta: A9 (opcional).
- **R08-12 · SUGESTÃO · segurança (público)** — local: PRs #215, #216, #217; colaboradores
  - observado: todas as 196 issues e 130 de 133 PRs são de `DevDosAnjos`. Os 3 PRs de outra conta são de `itallovidal` (#215 correções da revisão da Sprint 00, #216 o PR da Sprint 00 para `main`, #217 falso positivo do gitleaks), de 15/10, mesclados; nenhum comentário de outra conta; nenhum pede mudança no comportamento do agente. O repositório tem 2 colaboradores com permissão de escrita: `itallovidal` e `lpatros`. Dois commits na `dev` têm e-mail de autor real das pessoas que os fizeram (um pessoal e um de empresa), por serem metadados de commit e não texto do repositório.
  - proposta: ver "Ações do dono" 4.
- **R08-13 · SUGESTÃO · infra** — local: `infra/scripts/gitleaks-rules.test.sh`
  - observado: o script chama `python3` para ler o JSON; na máquina do dono (Windows, git-bash) não existe `python3`, então o comando do `AGENTS.md` ("Prova: bash infra/scripts/gitleaks-rules.test.sh") reprova localmente no primeiro caso por erro de ambiente. No CI (Ubuntu) funciona.
  - proposta: A9 (opcional): ler o JSON com `grep -o '"RuleID": *"[^"]*"'` ou com `jq`, como o próprio `ci-gate` já usa.
- **S1 · SUGESTÃO · branches** — apagar os branches mesclados (`item/75-…`, `item/76-…`, `item/77-…`, `ajustes/teste-agenda-semana`) e decidir o `ajustes/seguranca-repo-publico`; é do dono.
- **S2 · SUGESTÃO · teste** — os testes seguem citando "CA" e não o código da regra (S2 da Sprint 07, sem mudança). `@DisplayName` com RN-COB-xx deixaria a varredura conferível.
- **S3 · SUGESTÃO · código** — `Payments.register`: a repetição devolve 409 (`CHARGE_NOT_PAYABLE`) e não o mesmo recibo; quem repete depois de um corte de rede não vê o número do recibo. Cumpre "idempotente" no sentido de não duplicar; devolver o recibo existente seria mais amigável. Registrado nas decisões do #71 (D3).
- **S4 · SUGESTÃO · processo** — sem arquivo de notas de versão (S4 da Sprint 07 sem decisão); os itens da sprint dizem "só se existir" ou põem a linha na descrição do PR.
- **S5 · SUGESTÃO · código** — achados pequenos que os verificadores já registraram e ficaram abertos: o selo "Em atraso/Em dia" da ficha olha só as 100 cobranças mais recentes (#73 desvio 6); `RegisterPaymentDialog` abre com "enviar recibo por e-mail" marcado mesmo sem e-mail do pagador (#75 desvio 6); mês formatado sem `lib/format` (#76); `ReceiptPdf.safe` troca caractere fora do WinAnsi por "?" (#75).

#### Testes instáveis

| Teste | Causa | Precisa de issue? |
|---|---|---|
| `ChargeRenegotiationIT.invalid` | confirmada por leitura de código: o `static` de `CtcoreApplication` força a JVM em UTC e o teste compara a data UTC com a data da unidade (São Paulo); falha de 21:00 a 24:00 de Brasília, também no CI. A falha em si não foi reproduzida (R08-1) | **sim**, A1 |
| `OneOffChargeDialog.test.tsx` (falhou em 2 de 4 rodadas do `test:cov` no #76) | provável, não medida: `delay(50)` + `findByText("Carregando...")` depois de `user.type`, o mesmo padrão que falhou no meu `test:cov` em `AdvanceChargesDialog.test.tsx:98`. No meu clone o `OneOffChargeDialog` passou nas 3 execuções | **sim**, A2 (junto com o `AdvanceChargesDialog`) |
| `AdvanceChargesDialog.test.tsx:98` | o mesmo padrão; falhou na minha 1ª execução do `test:cov`, passou nas outras duas com `--maxWorkers=2` | **sim**, A2 |
| padrão de data relativa a hoje | nos ITs: `LocalDate.now()` (UTC) em 17 arquivos de teste; só `ChargeRenegotiationIT:108` compara contra o "hoje" da unidade e rejeita o dia anterior, e os outros usam folga de dias (`minusDays(40)`, `plusDays(5)`) ou calculam no fuso da unidade (`EnrollmentConclusionIT:161`, `RepricingIT:168`). `BenefitGrantIT:197` (`validUntil = hoje`) só falharia para unidade em fuso atrás de UTC, que não existe nos testes. No front: nada falhou com `TZ=Pacific/Kiritimati` (suíte inteira, sem cobertura); não contei os arquivos que usam `Date`. O padrão do #329 (agenda) não se repete na sprint | só o `invalid` (A1) |
| `S3FileStorageIT` (`ConnectException`, #70 e #71) | `Testcontainers` do SeaweedFS com `Wait.forHttp("/status")`; falha de ambiente ao subir o contêiner sob carga; não ocorreu no meu `verify` | não por ora; reabrir se aparecer no CI |
| `e2e/recuperar-senha.spec.ts` (#320) | **não encontrada**: o rastro da falha no CI (#72) mostra "Informe o seu e-mail." na tela, ou seja, o envio saiu com o campo vazio e nenhum `POST /auth/password-reset` foi feito, o que combina com `fill` antes da página estar pronta (o servidor de dev do Vite frio no CI) e não com tempo de espera. A #320 mediu 0/20 localmente e fechou sem correção. No meu `e2e` completo passou (35 de 35) | não agora; se voltar no CI, abrir com o rastro do Playwright |

#### Camada técnica

Lidos por inteiro: `Payments`, `ChargeActions` (renegociação e pontos de permissão), `MonthlyGeneration` (consultas e ponteiro, em parte), `PublicReceiptController`, `ClockConfig`, `CtcoreApplication`, `S3FileStorageIT` (cabeçalho), `ChargeRenegotiationIT.invalid`, `.github/workflows/ci.yml`, `aguardar-ci.sh`, `.gitleaks.toml`, a prova das regras e as seis seções de decisão de #71 a #77; 25 testes pelo nome e `@DisplayName`; varredura por padrão no restante do diff (dinheiro, permissões, SQL, fornecedor, texto literal). Não é leitura linha a linha das 13 mil linhas.

- Correção: sem erro de lógica encontrado. Pontos conferidos: pagamento com `for update` na cobrança e `receipt_counter` com `on conflict do update` (número consecutivo, sem lacuna se a transação falha); `amountCents` do corpo diferente do valor da cobrança dá 422; renegociação tudo ou nada, centavos da divisão na primeira parcela, vencimento anterior a hoje no fuso da unidade recusado; geração com ponteiro por modalidade e `for update` contra execução dupla.
- Segurança de acesso: `COLLECTIONS` conferido no servidor em toda rota; unidade ou cobrança de outra organização dá 404; leitura sem acesso completo recusada em registrar, cancelar e renegociar; o recibo público devolve só as iniciais do pagador, e o PDF do link (nome e CPF completos, sem sessão, código de 80 bits) é a decisão D1 do #75, que pede resposta do dono (D1 abaixo); a função `public_receipt` ficou com o `EXECUTE` padrão de `PUBLIC` (sugestão do verificador do #71, sem efeito hoje).
- Migrações: `V39` a `V41` não editam migração antiga; RLS ligada e forçada nas tabelas novas; `DELETE` e `UPDATE` retirados onde cabe; o `check (status <> 'CANCELED' or canceled_at is not null)` ficou de fora da `V40` (desvio 1 da #73, fixtures antigos), então o banco aceita `CANCELED` sem data de cancelamento.
- Sem fornecedor em regra: nenhuma linha acrescentada em `backend/src/main`, `frontend/src`, `docs` e `infra` cita fornecedor de pagamento (o Mercado Pago só aparece em `.gitleaks.toml` e na prova do gitleaks, que a #328 pediu).
- Excesso: nada que mereça ajuste. `ponytail:` novos: `ChargeActions.read` (série de renegociações encadeadas perde o vínculo do grupo anterior). O #71 concentrou 22 tarefas em um item (S7 da Sprint 07).

#### Desvios e decisões herdados dos itens

| Item | Desvio ou decisão | Classe | Destino |
|---|---|---|---|
| #71 D4 | só `COLLECTIONS` completo registra pagamento; o Professor não | muda requisito | R08-4, A3 |
| #71 D5 | geração por ponteiro (`next_charge_ref`, `next_charge_due_on`) | técnico | registrar |
| #71 D6 | valor final zero não gera mensalidade | consistente com RN-COB-04 | A3 (uma linha) |
| #71 D9 e #75 D5 | aviso por e-mail do reajuste adiado para a #78 | muda requisito | R08-5, A4 |
| #71 D11 | primeira cobrança também ao adicionar modalidade a aluno ativo | muda requisito | R08-4, A3 |
| #71 D12 | mês seguinte como referência quando o vencimento cai no mês da primeira cobrança (suposição) | suposição | D3 |
| #71 D13 | regra do reajuste (abertas futuras e pagas de mês futuro; diferença em 7 dias) | muda requisito | A3 |
| #72 D5 | situação financeira conta a avulsa em atraso | muda requisito | D4 |
| #73 D2 | Registrar pagamento, Enviar lembrete e Estornar desabilitados até os itens que os entregam | técnico, com encaixe | R08-5, A4 |
| #73 D4 | alterar valor e vencimento é renegociação com cobrança nova do tipo parcela | muda requisito | A3 |
| #73 sup. | "Administrador" (RN-COB-09 e 10) vale também para o Coordenador | suposição | D3 |
| #73 desvio 1 | sem o `check` de `CANCELED` na `V40` | técnico | registrar |
| #74 D3, D4 | trocar vencimento dentro do mês; cancelar grava a cobrança como cancelada com motivo | muda requisito | A3 |
| #75 D1, D2 | PDF por link com nome e CPF completos; e-mail com link e não anexo | muda requisito | D1, A3 |
| #75 D4 | data do pagamento no passado sem limite | técnico | registrar |
| #76 D1, D5 | avulsa leva `unitId`; valor de R$ 0,01 a R$ 100.000,00 | muda requisito | A3 |
| #77 D3 | quem tem leitura de `COLLECTIONS` vê os recebimentos do dia | muda requisito | A3 |
| #318, #320 | medição e correção de teste instável, sem retry nem timeout | processo | R08-3 |

#### Ajustes propostos

**A1 · Corrigir `ChargeRenegotiationIT.invalid`** (resolve R08-1 e R08-2) — antes do PR
- Tipo: nova issue, no formato das #318 e #320 (marco: Sprint 09 ou a mesma Sprint 08 reaberta, a critério do dono; etiqueta `back-end`).
- O que mudar e onde: nas linhas 102, 103, 108, 124 de `ChargeRenegotiationIT.java` e em `ChargeActionsBase.java:46`, trocar `LocalDate.now()` por `LocalDate.now(ZoneId.of("America/Sao_Paulo"))` (o fuso da unidade de teste), ou por um método `today(unitId)` que leia `unit.timezone` como já faz `EnrollmentConclusionIT:161`. Medida antes e depois: rodar o teste com o relógio entre 21:00 e 24:00 de Brasília (ou com `Clock` fixo injetado) e registrar falhas por execuções. Incluir no texto da issue a regra do R08-2 (falha vermelha na regressão completa vira issue antes da rodada `OK`).
- Como provar: `./backend/mvnw -f backend/pom.xml verify -Dit.test=ChargeRenegotiationIT` verde a qualquer hora.
- Tamanho: pequeno.

**A2 · Estabilizar o padrão `delay(50)` + `findByText("Carregando...")`** (resolve R08-3) — antes do PR
- Tipo: nova issue, no formato das #318 e #320 (marco: Sprint 09; etiqueta `front-end`).
- O que mudar e onde: `AdvanceChargesDialog.test.tsx:94-98`, `OneOffChargeDialog.test.tsx` ("estados da busca") e `PublicReceiptPage.test.tsx`: reter a resposta com uma promessa controlada pelo teste e liberá-la depois de afirmar "Carregando...", como na correção do #73. Medir antes e depois: o arquivo 20 vezes com `--maxWorkers=2` e a suíte completa com o paralelismo padrão, registrando falhas por execuções.
- Como provar: `npm run test:cov` (paralelismo padrão) verde em 5 execuções seguidas.
- Tamanho: pequeno.

**A3 · Atualizar a especificação** (resolve R08-4) — antes do PR, depois de D1 a D4
- Tipo: ajuste de especificação (sem código).
- O que mudar e onde: `10-cobrancas.md` (permissão de registrar pagamento, primeira cobrança ao adicionar modalidade, renegociação por cobrança nova, reajuste e diferença, recibo por link e e-mail com link, cobrança avulsa por unidade, recebimentos do dia por acesso de leitura, situação financeira do aluno) e as linhas correspondentes de `07`, `09` e `02`; uma linha por decisão, o texto saindo do arquivo do item.
- Como provar: `git diff docs/especificacao` mostra os trechos; nenhuma mudança de código.
- Tamanho: pequeno a médio.

**A4 · Encaixes da Sprint 09 e do A4 da Sprint 07** (resolve R08-5) — antes da Sprint 09 começar
- Tipo: ajuste de issue, sem issue nova (exceto o A4 antigo).
- Blocos para colar:
  - fim da #78: "Encaixe herdado da Sprint 08. Pronto quando: [ ] e-mail de aviso do reajuste de plano e da diferença de reajuste ao aluno (RN-PLA-04, RN-PLA-05), com teste; [ ] e-mail 'Cobrança gerada' (RN-COM-11), com teste; [ ] o modelo 'Pagamento confirmado' (nascido na #75, sem alterar o contrato) passa para o registro de e-mails automáticos."
  - fim da #79: "Encaixe herdado da Sprint 08. Pronto quando: [ ] 'Enviar lembrete' do painel da cobrança (hoje desabilitado, D2 da #73) passa a funcionar para cobrança aberta, com teste de perfil e de isolamento."
  - A4 da Sprint 07, ainda sem issue: "Remover comprovantes de graduação de origem enviados e não usados em 24 h (tarefa agendada que lista `origin-proof-*` sem referência em `student_graduation.proof_file`). Pronto quando: [ ] arquivo sem referência com mais de 24 h é removido; [ ] arquivo referenciado nunca é removido; [ ] a tarefa grava auditoria de sistema sem a chave do arquivo." Marco sugerido: Sprint 13 (LGPD).
- Como provar: `gh issue view 78 79` mostram os blocos; busca por "origin-proof" acha a nova issue.
- Tamanho: pequeno.

**A5 · Piso de 100% para as telas de cobrança** (resolve R08-6) — depois de D2
- Tipo: ajuste de código de teste e de configuração, em PR próprio.
- O que mudar e onde: se D2 for "sim", acrescentar `"src/features/charges/**": { lines: 100 }` em `frontend/vite.config.ts` e cobrir as linhas que faltam (hoje 97,67% em `charges`: `ChangeDueDialog`, `ChargesPage`, `CancelNextDialog`, `AdvanceChargesDialog`, `OneOffChargeDialog`); atualizar a linha de comandos do `AGENTS.md` e o comentário do `vite.config.ts`.
- Como provar: `npm run test:cov` exit 0 com o novo piso; mexer em uma linha de `charges` sem teste reprova.
- Tamanho: pequeno a médio.

**A6 · Afirmar "continua Ativo"** (resolve R08-7) — depois do PR
- Tipo: ajuste de teste.
- O que mudar e onde: em `MonthlyGenerationIT` CA12 e `EnrollmentConclusionIT` CA19, depois da bolsa de 100%, acrescentar a asserção do `status` da modalidade (`ACTIVE`) e do aluno.
- Como provar: os dois ITs passam; trocar a asserção por `INACTIVE` os reprova.
- Tamanho: pequeno.

**A7 · Cache do Playwright que valha** (resolve R08-8) — depois do PR
- Tipo: ajuste de CI, em PR próprio.
- O que mudar e onde: `.github/workflows/ci.yml`: ou rodar o workflow também em `push` para `dev` (só o job que grava o cache), para que os PRs leiam a cache da base, ou tirar o cache e baixar só o Chromium sem `--with-deps`, ou fixar a imagem. Registrar o tempo do passo antes e depois em pelo menos 5 PRs.
- Como provar: o passo `playwright install` de um PR novo fica abaixo de 60 s em 5 runs seguidos.
- Tamanho: pequeno.

**A8 · `aguardar-ci.sh` recuperável** (resolve R08-9) — depois do PR
- Tipo: ajuste de script de infra.
- O que mudar e onde: gravar o desfecho (código e última linha) em `$TMPDIR/aguardar-ci-pr<N>.resultado` no `trap EXIT`; no código 4, imprimir o pid, o caminho do resultado e `kill <pid>`; acrescentar ao cabeçalho "nunca em primeiro plano".
- Como provar: rodar duas vezes contra um PR com CI longo; a segunda mostra o pid e o arquivo; depois do fim da primeira, o arquivo traz o código.
- Tamanho: pequeno.

**A9 · Limpezas opcionais** (R08-11 e R08-13) — depois do PR
- Tipo: ajuste de teste e de script.
- O que mudar e onde: trocar `x.com`, `exemplo.com`, `b.com`, `dominio.com` por `.example` e `.test` nos testes e fixtures (mecânico, 700 ocorrências); trocar o `python3` de `gitleaks-rules.test.sh` por `jq` ou `grep`.
- Como provar: `git grep` não acha os domínios; a prova do gitleaks roda na máquina do dono.
- Tamanho: pequeno (mecânico).

Ordem sugerida: A1, A2, A4 (as issues), A3 (depois de D1 a D4), A5 (depois de D2), A6, A7, A8, A9.

#### Decisões do dono

**D1 · O PDF do recibo pode ser aberto por qualquer pessoa que tenha o link, com nome e CPF completos de quem pagou?** — recomendada: manter, como a RN-COB-16 diz ("o link não expira"), mas só enquanto o código for de 80 bits sem listagem, e escrever isso na especificação — motivo: o pagador sem conta precisa abrir o link do e-mail e o texto da regra pede nome e CPF — alternativa: mascarar o CPF no PDF público (`***.456.789-**`) e deixar o completo só para quem tem sessão; contraria o texto literal da regra, mas expõe menos dado pessoal num link que pode ser repassado.

**D2 · O piso de 100% de cobertura do front vale também para `features/charges`?** — recomendada: sim, acrescentar a pasta ao piso (A5) — motivo: a `features/billing` do `AGENTS.md` não existe e a regra de cobrança do front vive em `charges`; o piso hoje não alcança as telas de dinheiro — alternativa: manter o piso só em `payments` e deixar a lacuna escrita no `AGENTS.md`.

**D3 · "Administrador" nas regras de renegociar e cancelar cobrança (RN-COB-09 e 10) inclui o Coordenador?** — recomendada: sim, como o código faz (suposição do #73, igual à matriz da `02`) e escrever na especificação — motivo: o Coordenador gere a unidade e já tem `COLLECTIONS` completo — alternativa: só o Administrador; muda a verificação de perfil em `ChargeActions` e os testes.

**D4 · A situação financeira do aluno (Em dia, Em atraso) conta a cobrança avulsa em atraso?** — recomendada: sim, como a #72 fez, e escrever que a inativação da Sprint 12 usa outra regra (sem avulsa, RN-COB-11) — motivo: a lista de cobranças mostra a avulsa em atraso, e o aluno não deve aparecer "Em dia" ao lado dela — alternativa: excluir a avulsa; a lista de alunos e a de cobranças passam a divergir.

#### Ações do dono

1. Subir o ambiente (`docker compose -f infra/docker-compose.yml up -d --wait`, `bash infra/scripts/prepare-runtime-db.sh`, back-end com perfil `local`, `bash infra/scripts/seed.sh`) e, em `frontend/`, `npm run dev`. Abrir `http://localhost:5173`, entrar como Coordenador, temas claro e escuro, larguras 360 px e 1280 px, e olhar contra o canvas (os agentes não os alcançaram em nenhum item):
   - Financeiro › Cobranças (`/cobrancas`): filtros, atalhos, totais do rodapé, cartões no celular, situações e cores.
   - Painel da cobrança: dados, histórico, pagamento, ações (as três desabilitadas com o motivo), cancelar e renegociar; aba Financeiro da ficha do aluno.
   - Prévia da geração (`/cobrancas/previa`): grupos por vencimento, "Não serão cobrados", trocar vencimento, cancelar, aplicar benefício.
   - Janela Registrar pagamento (balcão e etapa 5 da matrícula), PDF do recibo (`PublicReceipt` e o arquivo) e a página `/recibo/:codigo`.
   - Nova cobrança avulsa e Gerar próximas mensalidades; Recebimentos do dia.
   - Contraste de texto e de estados em cada tela nos dois temas; zoom de 200%; tom dos textos em pt-BR.
2. Decidir D1 a D4.
3. Conferir se o token do arquivo `segredo-falso.txt` (PR #204, fechado) é mesmo inventado. Se for, nada a fazer além de, se quiser, acrescentar a impressão digital `6d5758a6e5bfe8d17aef8de259b7772484294642:backend/src/test/java/br/com/ctcore/logging/LogOutputTest.java:generic-api-key:87` ao `.gitleaksignore`. Se não for inventado, revogar no GitHub; apagar o PR ou o commit não basta.
4. Conferir `itallovidal` e `lpatros` (colaboradores com escrita) e os 3 PRs de `itallovidal` (#215 a #217): confirmar que são contas conhecidas. Decidir se `dev` passa a exigir revisão enquanto o repositório está público.
5. Aceitar A1, A2 e A4 (issues e texto de issue) antes do PR; A3 e A5 depois de D1 a D4.
6. Com o veredito `APROVAR` e os ajustes resolvidos: abrir o PR de `dev` para `main` com o método "Merge" (nunca squash) e, depois de revisar, dizer "sim" para liberar a Sprint 09.
7. Opcional: apagar os branches de S1.

#### Limitações e o que não foi conferido

- Contraste real, 360 px sem rolagem horizontal, foco e teclado, zoom e axe em navegador real: NÃO CONFERIDO, só por proxy (jsdom). Os canvases não foram lidos por nenhum item da sprint.
- Tela e PDF contra o canvas e tom dos textos: NÃO CONFERIDO (olho humano).
- Imagens: construídas (218 s e 17 s), mas **não subi os contêineres** para provar uid, readiness e as respostas HTTP, como a Sprint 07 fez; removi as imagens antes de perceber a omissão. Os `docker build` do CI passam em todos os PRs.
- Recomeço (`down -v` e subir de novo): não repetido.
- `e2e:full`: não rodado (só no PR para `main`).
- `gitleaks` no histórico: rodei com a mesma versão do CI; o OSV-Scanner só no CI (verde em todos os PRs).
- O `verify` rodou com o back-end local de pé ao lado (falha do meu script), então o tempo de 13 min 24 s não é limpo.
- `ChargeRenegotiationIT.invalid`: a causa foi confirmada por leitura de código e a falha não foi reproduzida (não mudei o relógio da máquina). A causa de `OneOffChargeDialog` e de `PublicReceiptPage` é provável pelo padrão, não medida.
- O episódio do `aguardar-ci.sh` no #327 foi reconstruído pelo código e pelo CI, sem o log da sessão.
- Só olhei a rodada 1 do `test:cov` falhando 1 vez; não fiz as 20 execuções que as #318 e #320 pedem. Isso é parte do trabalho do A2.
- Notas de versão: NÃO CONFERIDO (sem arquivo).
- Varredura de dados pessoais em issues e PRs foi por padrões (e-mail, CPF formatado, telefone com DDD), não leitura de todos os textos.
- Rastreabilidade por código de regra nos testes: por comportamento, não por texto (S2).

#### Riscos para a próxima sprint

- Sprint 09 (comunicação): recebe de volta os três e-mails de cobrança, o aviso de reajuste, o "Pagamento confirmado" e o "Enviar lembrete" do painel (R08-5, A4). A #78 e a #79 precisam do bloco antes de começar.
- O `ci-gate` pode reprovar à noite por `ChargeRenegotiationIT.invalid` (R08-1) e por qualquer teste de tela do padrão `delay(50)` (R08-3) até as issues A1 e A2 serem feitas.
- O `e2e` tem pouca folga no CI (R08-8): um dia de apt lento reprova um PR por tempo.
- Sprint 10 (portal): baixa o recibo pelo mesmo endereço do PDF público (#92, RF-POR-05); a resposta de D1 define o que o PDF mostra.
- Sprint 11 (pagamentos online): o pagamento hoje é um por cobrança, sem a tabela de ligação para pagar várias de uma vez (RN-COB-14, D3 da #71); `actions.refund` e o botão Estornar estão desabilitados à espera da #104; a idempotência por `external_id` já existe em `payment` (`PaymentIT.raceAndExternalId`).
- Sprint 12 (inativação): a geração e a primeira cobrança já separam `ENROLLMENT_FEE` e `ONE_OFF` por tipo; a #111 precisa filtrar por tipo e a situação financeira da lista (D4) não é a mesma regra. A tolerância de 5 dias vive em `charge.tolerance_days`.
- Dívida com prazo: `osv-scanner.toml` vence em 2027-01-15; o `ponytail:` do modelo de termo fixo vive até o RF-PNL-07; o repositório volta a ser privado antes da Sprint 22 (trava do `AGENTS.md`).
- Nenhuma issue `depende de você` trava a Sprint 09.

---

### Rodada 2 — APROVAR — 2026-10-23, estado 5e2d54f

Confere só os ajustes da rodada 1 e as respostas do dono. Não repete o clone limpo: o que mudou é teste, texto, o PDF do recibo e o piso de cobertura, e cada PR passou no `ci-gate` completo (back-end `verify` com JaCoCo, front `test:cov` com piso, e2e, onde a pasta mudou).

#### Resumo e veredito

Os ajustes A1 a A5 foram feitos, cada um em PR próprio para `dev`, com `ci-gate` verde e merge por squash; A6 a A8 viraram issues no marco da Sprint 09, sem correção, como o dono pediu; A9 ficou de fora, por decisão do dono. As quatro decisões estão aplicadas (D1 com código, D2 a D4 na especificação e no piso). Não há achado BLOQUEIA nem AJUSTAR novo. Dois achados pequenos desta rodada ficaram registrados (N1, corrigido no próprio PR; N2, sugestão). Dependem do dono as ações 1, 3 e 4 da rodada 1 (telas contra o canvas, token do PR #204, colaboradores). Resultado: `APROVAR`. O PR de `dev` para `main` é do dono.

#### Estado revisado

| | |
|---|---|
| `dev` | `5e2d54f` (igual a `origin/dev`) |
| `main` | `8807a09d50c1bf11c4553c2ee470376fdc4e3456` (sem mudança) |
| PRs desta rodada | #332 (#331), #334 (#333), #336 (encaixes), #337 (especificação), #339 (#338), #341 (#340), #345 (backlog das issues #342 a #344); #330 é a rodada 1 |
| Issues | #331, #333, #338 e #340 fechadas no marco "Sprint 08 · Cobranças"; #335 (Sprint 13) e #342, #343, #344 (Sprint 09) abertas |

#### Ajustes conferidos

| Ajuste | Achado | Como foi feito | Evidência | Resultado |
|---|---|---|---|---|
| A1 | R08-1, R08-2 | issue #331, PR #332: `today()` no fuso da unidade em `ChargeActionsBase`; `ChargeRenegotiationIT` com `Clock` fixo em 22:30 de Brasília (já é 23/10 em UTC); regra do R08-2 e do fuso da unidade no `AGENTS.md` | **a falha foi reproduzida e some**: com o relógio fixo e as datas antigas, `invalid` reprovou (400 esperado, 201 recebido, `firstDueOn` 2026-10-22); com `today()` passam os 14 testes de `ChargeRenegotiationIT`, `ChargeCancelIT`, `ChargePanelIT` e `ChargeActionsIsolationIT`; `ci-gate` verde. Isso confirma a causa do R08-1, que na rodada 1 estava só por leitura de código | FEITO |
| A2 | R08-3 | issue #333, PR #334: `gate()` em `src/test/gate.ts` e resposta retida em `AdvanceChargesDialog`, `OneOffChargeDialog`, `ChargePanel` e `LessonPanelPage` | os cinco arquivos 20 de 20 sem falha; `npm run test:cov` (paralelismo padrão) verde no PR. Ver N1: o `LessonPanelPage` ainda falhou uma vez depois, corrigido no #341 | FEITO, com N1 |
| A3 | R08-4, D1 a D4 | PR #337: `10-cobrancas.md` (RN-COB-02, 04, 09, 10, 11, 12, 16, nova RN-COB-22, RF-COB-02, 03, 08, tabela de situações), `09-matricula.md` (RN-MAT-05, 07, 17), `07-planos-e-beneficios.md` (RN-PLA-05) | `git diff` do PR: 3 arquivos de especificação; as decisões "muda o comportamento" da rodada 1 estão escritas; D3 (Coordenador) em RN-COB-09 e 10; D4 na RN-COB-22 | FEITO |
| A4 | R08-5 | blocos "Encaixe herdado da Sprint 08" nas issues #78 e #79; issue nova #335 (Sprint 13, comprovante de graduação abandonado, A4 da Sprint 07); PR #336 no `specs/backlog.md` | `gh issue view 78 79` traz o bloco (1 ocorrência cada); #335 no marco da Sprint 13 com o "Pronto quando" da rodada 1 | FEITO |
| A5 | R08-6, D2 | issue #340, PR #341: `"src/features/charges/**": { lines: 100 }` no `vite.config.ts`, `AGENTS.md` e comentário; testes de Esc, envio em branco, envio repetido e falha na troca de vencimento; `retry` opcional em `ModalityDialog` (saem dois `retry={() => {}}` mortos) | as 394 instruções de `features/charges` cobertas na medição; o piso reprova quando só um arquivo roda ("Coverage for lines (40.4%) does not meet src/features/charges/** threshold"); `npm run test:cov`: 124 arquivos, 1079 testes, exit 0; sem `v8 ignore` | FEITO |
| A6 | R08-7 | issue #342, Sprint 09, sem correção | `gh issue view 342` | ISSUE ABERTA |
| A7 | R08-8 | issue #343, Sprint 09, sem correção | `gh issue view 343` | ISSUE ABERTA |
| A8 | R08-9 | issue #344, Sprint 09, sem correção | `gh issue view 344` | ISSUE ABERTA |
| A9 | R08-11, R08-13 | fora, por decisão do dono | segue como sugestão | NÃO FEITO (decidido) |
| D1 | decisão sobre o PDF do recibo | issue #338, PR #339: o PDF do link público imprime o CPF mascarado (`***.456.789-**`; CPF fora do padrão vira `***`); rota nova `GET /api/v1/receipts/{code}/pdf` com sessão e CPF completo; painel e janela de pagamento usam a rota com sessão; RN-COB-16 já ajustada (#337) | `ReceiptPdfTest.maskedCpf`, `PaymentReceiptIT` (público mascarado e sem os 11 dígitos; rota com sessão com o completo; 401, 403 do Professor, 404 de outra organização, de outra unidade e de código desconhecido, com o mesmo corpo), `ChargePanel.test.tsx`, `payment.msw.test.ts`, e2e `pagamento.spec.ts`; OpenAPI com a rota nova; `OpenApiContractTest` e `ContractRoutesTest` passam; `ci-gate` verde com `payment` em 100% no JaCoCo | FEITO |
| D2, D3, D4 | decisões de piso, perfil e situação financeira | A5 (D2), RN-COB-09 e 10 (D3), RN-COB-22 (D4) | acima | FEITO |

Processo conferido: cada PR tem base `dev`, título no formato `#N: <título da issue>` com `Closes #N` (ou `docs:` nos de texto) e um commit de squash; nenhum push forçado, rebase, reset ou stash. Os PRs #336 e #337 foram mesclados com o CI de antes do #334 (o `strict` do `dev` é falso e eram só texto). O PR #339 precisou de um segundo commit depois do primeiro push (o e2e `pagamento.spec.ts` ainda esperava o endereço público do recibo): visto e corrigido antes do CI terminar, com o e2e do arquivo rodado localmente.

#### Achados novos

- **N1 · corrigido no PR #341 · teste** — `LessonPanelPage.test.tsx`, `CA10`. No `test:cov` do #341 o teste convertido no #333 falhou uma vez: `findByText("Carregando...")` não achou o texto. Causa: a resposta retida era registrada com `server.use` depois do `render`; sob carga o pedido da aula já tinha usado a resposta padrão. Corrigido com `setup({ respond })`, que registra a resposta retida antes do `render`; o arquivo passou 10 de 10 e o `test:cov` completo seguinte, mais o do CI, ficaram verdes. Os outros testes convertidos registram a resposta retida antes do pedido (o pedido só sai quando se digita, ou entra pelo `setup`). Não fiz 20 execuções do `test:cov` inteiro; se a classe voltar, vale a issue no formato das #318 e #320.
- **N2 · SUGESTÃO · código** — `GrantBenefitDialog.tsx` (benefícios) ainda passa `retry={() => {}}` com `state="ready"`, o mesmo código morto que saiu das duas janelas de cobrança; `benefits` não está no piso de 100%, então não entrou no #340. Pode sair junto com o próximo toque no arquivo.

#### Decisões do dono

D1 a D4 foram respondidas e aplicadas (ver "Ajustes conferidos"). Nenhuma decisão nova.

#### Ações do dono

1. Conferir as telas contra o canvas, nos dois temas, a 360 px e a 1280 px (ação 1 da rodada 1). O painel e a janela de pagamento agora abrem o PDF pela rota com sessão: conferir que o CPF completo aparece no PDF aberto com a sessão e mascarado no aberto pela página `/recibo/:codigo`.
2. Confirmar se o token de `segredo-falso.txt` (PR #204, fechado) é inventado; se não for, revogar no GitHub (ação 3 da rodada 1). Opcional: acrescentar ao `.gitleaksignore` a impressão digital do commit `6d5758a…` (PR #198).
3. Conferir os colaboradores `itallovidal` e `lpatros` e os PRs #215 a #217 (ação 4 da rodada 1); decidir se `dev` passa a exigir revisão enquanto o repositório é público.
4. Abrir o PR de `dev` para `main` com o método "Merge" (nunca squash) e, depois de revisar, dizer "sim" para liberar a Sprint 09.
5. Opcional: apagar os branches mesclados (`item/75-…`, `item/76-…`, `item/77-…`, `item/331-…`, `item/333-…`, `item/338-…`, `item/340-…`, e os `docs/…` e `ajustes/…` já mesclados).

#### Limitações e o que não foi conferido

- Não repeti o clone limpo (`verify` completo, `test:cov`, e2e, imagens, seed): o que mudou foi teste, texto, o PDF do recibo e o piso, e cada PR passou no `ci-gate` completo (o #339 com o `verify` do back-end e o e2e do front; o #341 com o `test:cov` e o novo piso).
- O PDF do recibo foi conferido por teste (texto extraído do PDF); o PDF aberto num leitor, o layout e a rota com sessão no navegador real não foram vistos.
- A causa do R08-1 virou fato (reproduzida com relógio fixo); a das instabilidades de tela é da mesma classe medida nas #318 e #320, sem contagem em 20 execuções do `test:cov` completo.
- As ações do dono 1, 3 e 4 da rodada 1 seguem abertas.

#### Riscos para a próxima sprint

- A Sprint 09 recebe, além dos encaixes da rodada 1 (#78, #79), as issues #342, #343 e #344.
- O piso de 100% em `features/charges` vale daqui em diante: qualquer PR que mexa na pasta precisa de teste para a linha nova.
- O instável de tela pode voltar em outro arquivo (`delay` curto ou `server.use` depois do `render`); a regra do `AGENTS.md` manda abrir a issue antes da rodada `OK`.
- O recibo tem duas rotas de PDF (pública mascarada e com sessão completa): o portal (Sprint 10, #92) deve usar a com sessão para o aluno logado.

---

Ajustes A1 a A5 e D1 a D4 aplicados nos PRs #332, #334, #336, #337, #339 e #341; A6 a A8 abertos como #342, #343 e #344; A9 fora por decisão do dono.
