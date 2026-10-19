# Revisão da Sprint 04 · Modalidades, faixas etárias, graduação e professores

### Rodada 1 — AJUSTAR — 2026-10-19, estado c9d549b92a954762feec6759284bdefa7726546d

#### Resumo e veredito

A Sprint 04 entregou as 8 issues do marco (#38 a #45): catálogo e ativação de modalidades, graduação por modalidade com editor, modalidades da unidade, faixas etárias, lista, painel, cadastro/edição e inativação de professor. Todas fechadas por PR de squash com `ci-gate` verde e rodada `OK` registrada. O clone limpo passou em tudo (back-end 422 testes, front 540, e2e 23 de 23, imagens, seed idempotente).

Veredito: **AJUSTAR**, com **1 BLOQUEIA**. O primeiro "Pronto quando" do marco ("com graduação desligada numa modalidade, nenhuma tela mostra faixa para ela") falha na lista e no painel de professores: o nível e o grau do professor continuam aparecendo depois que o Coordenador desliga a graduação (R04-1, conferido por leitura do código e ausência de teste; não executei o cenário no app). O terceiro bullet (modalidade em uso não é desativada) só é provado com um duplo de teste, porque a porta `ModalityUsage` devolve 0 até as Sprints 05 e 07, e nenhuma issue dessas sprints carrega a troca (R04-3). A especificação não foi atualizada com 19 decisões que mudam comportamento (R04-2). O dono decide D1 e D2.

#### Estado revisado

| | |
|---|---|
| `dev` | `c9d549b92a954762feec6759284bdefa7726546d` (igual a `origin/dev`, pasta limpa) |
| `main` | `1e9e8d546d9d3057fe6af252c222ae27b4955878` (`origin/main`; não existe branch `main` local) |
| Ferramentas | Java 25.0.4 no PATH (o `verify` compila com `release 21`), Node 24.21.0, Docker 29.8.1, data 2026-10-19 |
| Diff | `git diff origin/main...dev --stat`: 95 arquivos, +11354 −27 (back-end `modality`, `graduation`, `agerange`, `teacher`, migrações V19 a V23, `InviteService`/`InviteRules`, OpenAPI +672 linhas, front `features/{modalities,age-ranges,teachers}`, MSW, 3 e2e novos, seeds 05 a 09, 8 arquivos de item). Workflows, `osv-scanner.toml`, `pom.xml`, `package.json` e Dockerfiles sem mudança; `docs/especificacao/` sem mudança |
| PRs | #263 (#38), #264 (#39), #265 (#40), #266 (#41), #267 (#42), #268 (#43), #269 (#44), #270 (#45). Fora de item: #262 (`docs: libera a Sprint 04`, anterior ao marco) |

Pré-condições: `dev` limpa e igual a `origin/dev`; nenhum PR aberto contra `dev`; `gh auth` válido (conta `DevDosAnjos`); Docker 29.8.1 ativo. Marco com 8 de 8 issues fechadas, nenhuma com `depende de você`; no `specs/backlog.md` as 8 linhas estão "feito". Nenhum PR fechou issue de outro marco. Ressalva: 3 branches remotas `item/43-...`, `item/44-...`, `item/45-...` já mergeadas (R04-8). Criei a branch local `revisao/sprint-04` só para gravar este arquivo, sem commit.

#### Trilha de processo

| Item | PR | Verificação | Entrega | Observação |
|---|---|---|---|---|
| #38 Configurações › Modalidades | #263, squash, `ci-gate` SUCCESS, `Closes #38` | Rodada 1 CORRIGIR_CODIGO, rodada 2 OK, rodada 3 OK (após a entrega) | sim | 3 rodadas (limite). 4 commits no PR (squash). Primeiros runs do CI no PR: um `failure` (39253970129) e um `cancelled`; o último `success` |
| #39 Graduação por modalidade | #264, squash, SUCCESS, `Closes` | Rodada 1 OK | sim | |
| #40 Modalidades da unidade | #265, squash, SUCCESS, `Closes` | Rodada 1 OK | sim | |
| #41 Faixas etárias | #266, squash, SUCCESS, `Closes` | Rodada 1 OK | sim | |
| #42 Professores (lista) | #267, squash, SUCCESS, `Closes` | Rodada 1 OK | sim | |
| #43 Painel do professor | #268, squash, SUCCESS, `Closes` | Rodada 1 OK | sim | |
| #44 Cadastro e edição | #269, squash, SUCCESS, `Closes` | Rodada 1 OK | sim | |
| #45 Inativar professor | #270, squash, SUCCESS, `Closes` | Rodada 1 OK | sim | |

Conferido por script: título `#N: <título da issue>` nos 8 PRs; corpo com `Closes #N` nos 8; as 6 seções, "Construção", "Verificação", "Entrega" e todas as tarefas marcadas em cada arquivo de item. Nenhum merge antes de rodada `OK` (a rodada 3 do #38 é posterior à correção de entrega, dentro do mesmo PR). Nenhuma correção fora de PR de item. Nenhum item com mais de 3 rodadas.

#### "Pronto quando"

**Do marco**

| Bullet | Status | Evidência |
|---|---|---|
| Com graduação desligada numa modalidade, nenhuma tela mostra faixa para ela | **NÃO PASSOU** | `Teachers.java:137-138` lê `teacher_modality` com `left join graduation_level` e não consulta `organization_modality.graduation_enabled`; `TeachersPage.tsx:52` e `TeacherPanelPage.tsx:111` mostram `m.level`/`m.degree` sempre que vier preenchido. `grep` por "desligad" em `backend/src/test/.../teacher` não acha teste. O cadastro só grava nível quando a graduação está ligada (`TeacherWrite.java:248`), mas desligar depois não limpa nem oculta (R04-1) |
| Telefone e e-mail do professor nunca saem em resposta pública da API | PASSOU | Não existe rota pública de professor nesta sprint (D5 de #42: diretório e horários públicos são das Sprints 18 e 20). `Teachers.Row` (lista) não tem os campos; `TeacherIT.java:185` e `:262` afirmam `doesNotContain("prof.secreto", "98888", "\"email\"", "\"phone\"")` na lista; `:250-251` mostram o contato só no painel autenticado. `TeacherIT` rodou no `verify` (296 ITs, 0 falhas) |
| Modalidade em uso não é desativada e a tela mostra o que impede | NÃO CONFERIDO | A recusa 409 com contagens existe e é testada com duplo (`ModalityIT` CA6, rodou verde), mas a implementação real é `ModalityUsage.None`, sempre 0, até as Sprints 05 e 07 (`ModalityUsage.java`, comentário `ponytail:`). Não é "limitação aceita" do `AGENTS.md`; o destino do critério precisa de decisão (D1) e a troca não está em nenhuma issue (R04-3) |
| Todos os itens do "Pronto quando" geral valem para cada item | ver tabela abaixo | |

**De todo item (plano de sprints)**

| Regra | Status | Evidência ou "não se aplica nesta sprint: motivo" |
|---|---|---|
| Regras citadas na issue com teste | NÃO PASSOU (formal) | Há testes por critério de aceite ("CA6: ..."), mas só `RN-PRO-02`, `RN-PRO-04` e `RN-PRO-05` aparecem citados. Sem citação em teste: RN-MOD-01 a 07, RN-GRA-01, RN-ORG-17, RN-PRO-01, 03, 07, 08 (R04-4). RN-PRO-08 não tem teste nem como ter até a Sprint 07 (D5 de #43) |
| Cobertura mínima 80% e 100% nos pacotes críticos | PASSOU | JaCoCo no clone: 3397 de 3483 linhas (97,53%), "All coverage checks have been met". Front: linhas 96,7%, instruções do conjunto 94,4%, limite cumprido (`npm run test:cov` exit 0). Os pacotes críticos de front (`billing`, `payments`, `subscription`, `deactivation`) não existem ainda: não se aplica nesta sprint |
| Rotas novas com teste de isolamento entre organizações e entre perfis | PASSOU | Todo grupo de rotas novo tem teste: `AgeRangeIT.isolation`, `GraduationIT.isolation`, `ModalityIT.isolationAndAudit`, `UnitModalityIT.isolationAndAudit`, `TeacherIT.isolation/detailIsolation`, `TeacherWriteIT.isolation`, `TeacherAccessIT.permissionsAndIsolation`, `TeacherDeactivationIT.permissionsAndIsolation`; RLS nas 8 tabelas com `enable` + `force row level security` (V19 a V23) e testes `rlsIsolatesOrganizationTables`, `rlsIsolatesLevels`. Catálogo global `modality` sem escrita para o papel operacional, provado por `ModalityCatalogIT` ("permission denied") |
| OpenAPI, implementação e MSW batendo | PASSOU | `OpenApiContractTest` e `ContractRoutesTest` dentro do `verify` verdes; `docs/openapi.yaml` +672 linhas; `*.msw.test.ts` (ex.: `age-ranges.msw.test.ts`) comparam handlers com o contrato |
| Auditoria em toda escrita | PASSOU | `grep audit.record`: `age_range.created/updated/deleted`, `modality.activated/deactivated/requested`, `modality.graduation_updated`, unit_modality (`UnitModalities.java:64`), `teacher.created/updated/photo_updated/photo_removed/access_granted/invite_resent/deactivated`. Testes `isolationAndAudit` verificam sem dado pessoal |
| Quatro estados, 360 px e axe nos dois temas | PASSOU (por proxy) | Testes de tela `CA8`/`CA9`/`CA11` rodam `axeViolations` com `innerWidth = 360` nos dois temas, com lista e janela aberta (`TeachersPage`, `ModalitiesPage`, `AgeRangesPage`); estados carregando/erro/403 testados. É jsdom: contraste e rolagem horizontal real não são medidos (ver "O que só um navegador prova") |
| Textos de tela só nos arquivos de tradução | PASSOU | `npm run lint` (regra de texto literal) exit 0 no clone |
| Recurso novo desligado por organização | não se aplica nesta sprint | O marco e as 8 issues não declaram recurso liberado aos poucos |
| Notas de versão | não se aplica nesta sprint | Não há academia em produção até a Sprint 24 e o repositório não tem arquivo de notas |

#### Clone limpo

Clone novo de `origin/dev` (`c9d549b`) no scratchpad (removido ao fim), projeto Compose `ctcorerev`, portas padrão livres, só README e "Comandos".

| Passo | Resultado real |
|---|---|
| ambiente | `docker compose up -d --wait`: postgres, storage e mailbox saudáveis em 39 s; `prepare-runtime-db.sh` criou `ctcore_app` (exit 0) |
| back-end | `verify`: BUILD SUCCESS em 11 min 58 s (rodando em paralelo com a cadeia do front); 126 testes rápidos e 296 de integração, 0 falhas, 0 ignorados; JaCoCo 3397/3483 linhas (97,53%); "All coverage checks have been met" |
| front-end | `npm ci`, `lint`, `typecheck`, `build`: exit 0. `test:cov`: **1ª execução falhou** em `TransferCoordinationDialog.test.tsx` ("Unable to find role=button name Transferir coordenação"; 539 de 540), rodada junto do `verify`; o arquivo sozinho passou (6 de 6) e a 2ª execução completa passou: 66 arquivos, 540 testes, linhas 96,7%, em 3 min 1 s. `npm run e2e`: 23 de 23 `@critical` em 59 s |
| seed (3x) | `seed.sh` exit 0 nas três (01 a 09), depois de subir o jar com perfil `local` (readiness `UP`); contagens iguais: 3 organizações, 3 unidades, 5 contas, 9 professores, 9 modalidades de organização, 57 níveis, 15 faixas etárias, 6 modalidades de unidade |
| imagens | `ctcore-backend`: `uid=10001`, readiness `UP` na porta 8081 com banco e `TWO_FACTOR_ENCRYPTION_KEY` (README atual funciona). `ctcore-frontend`: 200 em `/` e `/conta`, 404 em `/api/v1/auth/session`; **sem** `Content-Security-Policy`, `X-Content-Type-Options`, `X-Frame-Options` nem `Referrer-Policy` (R04-7, mantido) |
| recomeço | `down -v` e novo `up --wait` em 23 s, sem estado escondido; `down -v` final; volumes `ctcore_postgres-data` e `ctcore_storage-data` do dono presentes; as duas imagens do projeto e `curlimages/curl` (baixada só para a prova) removidas pelo nome; `docker images` igual ao de antes |

Comparação com a Sprint 03 rodada 2 (back-end 361 testes, 97,39%; front 429 testes, 96,56%; e2e 15): back-end 422 testes e 97,53% (melhorou); front 540 testes e 96,7% (melhorou); e2e 23 (cresceu). Tempo do `verify` 11 min 58 s contra 5 min 47 s a 8 min 10 s antes: parte vem de 61 ITs a mais e parte de ter rodado em paralelo com o front; não tratei como regressão, mas vale acompanhar. A falha única do front aconteceu sob carga (R04-5).

Passos do README que não bastaram: nenhum. Observação: o `seed.sh` e o `prepare-runtime-db.sh` usam o projeto Compose padrão da pasta; ao usar `COMPOSE_PROJECT_NAME` diferente é preciso exportá-lo também nesses scripts (só relevante para revisões paralelas, não para quem segue o README).

#### CI e proteções

| Item | Estado real | Evidência |
|---|---|---|
| `ci-gate` exigido em `dev` | sim | `gh api repos/DevDosAnjos/ctcore/branches/dev/protection`: `required_status_checks.contexts = ["ci-gate"]`, `strict: false`; push forçado e exclusão bloqueados; sem revisão de PR obrigatória (o agente faz o merge, como manda o `AGENTS.md`) |
| regra de `main` | sim | Ruleset `25019105 protege-main` ativo em `refs/heads/main`: `deletion`, `non_fast_forward`, `pull_request`, `required_status_checks`. Repositório permite squash, merge e rebase (a restrição ao "Merge" para `main` é do processo, não do GitHub) |
| últimos runs de `ci.yml` e `publish.yml` | verdes | `ci.yml` roda só em PR: os 8 PRs da sprint têm `success` (ex.: 39254890909, 39263466876, 39293761679). `Publish` em `dev`: 8 runs `success` (39255894972 a 39294957227), um por merge. O PR #263 teve um run `failure` (39253970129) e um `cancelled` antes do verde |
| `permissions:` e pinagem de ações | mantido da Sprint 03 | `git diff origin/main...dev -- .github` vazio: workflows não mudaram na sprint |
| exceções de vulnerabilidade | 2, sem mudança | `osv-scanner.toml`: `GHSA-j9f9-w8pj-32f8` e `GHSA-pc63-qcmh-9cmg`, ambas `ignoreUntil = 2027-01-15`, com motivo (Spring 6.2 sem correção; SSE e `XsltView` não usados) |
| segredos e variáveis do fluxo de publicação | não existem | `gh secret list` e `gh variable list` vazios: `PUBLISH_REGISTRY*` ausentes, publicação pulada; limitação aceita (Sprints 22 e 23, issue #192) |

#### Achados

- **R04-1 · BLOQUEIA · dados/API** — local: `backend/src/main/java/br/com/ctcore/teacher/Teachers.java:137-138`, `TeachersPage.tsx:52`, `TeacherPanelPage.tsx:111`; marco "Pronto quando" bullet 1 e RF-MOD-02.
  - esperado: com a graduação desligada na modalidade, nenhuma tela mostra faixa (nível/grau do professor) para ela.
  - observado: a consulta de `ModalityRef` não filtra por `organization_modality.graduation_enabled`. Professor cadastrado com Jiu-Jitsu, Roxa, 2º grau enquanto a graduação estava ligada continua exibindo "Roxa 2º grau" na lista e no painel depois que o Coordenador desliga (`Graduation.java:94` só atualiza `graduation_enabled`; não toca `teacher_modality`). Nenhum teste de `teacher` cobre "graduação desligada".
  - evidência: leitura dos trechos acima e `grep -rn "desligad\|graduation" backend/src/test/java/br/com/ctcore/teacher` sem resultado sobre o cenário. Não executei o cenário no app.
  - efeito: critério do marco violado; o dado também reaparece sozinho se a graduação for religada, o que pode ser desejado, mas nesse caso a tela deve ocultar e não apagar.
  - proposta: A1.

- **R04-2 · AJUSTAR · especificação** — local: `docs/especificacao/04-modalidades-e-graduacao.md` e `05-professores.md` (sem nenhuma mudança na sprint).
  - esperado: `AGENTS.md`: "Mudou uma regra, muda primeiro a especificação".
  - observado: 19 decisões marcadas "muda o comportamento" nos itens não estão na especificação: #38 D2 (só o Coordenador ativa e desativa), D3 (recusa sem "desativar assim mesmo"), D5 (permissão `ORGANIZATION_SETTINGS` e Administrador só consulta); #39 D3 (só o Coordenador edita), D7 (desligar mantém os níveis; ligar sem nível é recusado; última gravação vence); #40 D2 (Coordenador e Administrador marcam pela linha `UNIT_SETTINGS`), D6 (desmarcar na unidade não é recusado com turma ou aluno); #41 D3 (apagar faixa sem restrição), D6 (Administrador só consulta); #42 D2 (lista abre em "Todas"), D5 (consentimento guardado sem uso público), D7 (alcance do Administrador); #43 D2 (contato visível à gestão), D3 (unidades marcadas ao dar acesso); #44 D3 (graduação só quando ligada), D4 (Administrador gerencia só suas unidades), D5 (declaração obrigatória); #45 D3 (Administrador só inativa se todas as unidades forem dele; P1 ainda sem resposta do dono), D4 (sem motivo). Também ficam fora do texto de `04`: o padrão IBJJF exato (idades e graus, suposição do #39 D5) e os limites de validação (nome 2 a 40, 30 níveis, grau 0 a 10).
  - evidência: `git diff origin/main...dev --stat -- docs/especificacao` vazio; `grep` dos itens.
  - efeito: o texto de regra contradiz o código (ex.: RN-MOD-02 não diz quem ativa por unidade; RN-PRO-05 não diz o alcance do Administrador); a próxima sprint herda sem fonte.
  - proposta: A2.

- **R04-3 · AJUSTAR · especificação/operação** — local: `ModalityUsage.None`, `Teachers.CLASS_COUNT`, `Teachers.headClasses`, `ClassRef`/`UpcomingClass` vazios, RN-PRO-08.
  - esperado: pontos de encaixe da Sprint 04 ligados pelas Sprints 05 e 07 ("Pontos de encaixe" do marco cita só RN-PRO-05 e RN-PRO-06).
  - observado: `gh issue list --search` por `ModalityUsage`, `headClasses` e `RN-MOD-06` não acha nenhuma issue aberta; `RN-PRO-08` não está em nenhuma issue. `RN-PRO-05` aparece em #48 e #50 (turmas sem professor ativo), mas nenhuma issue manda trocar `classCount`, `headClasses`, as seções "turmas" e "próximas aulas" do painel, nem o impedimento de desativar modalidade. É o mesmo ponto que A8 da Sprint 03 deixou sem ação (#46 e #67 não citam `UnitDeactivationBlockers`).
  - efeito: se esquecidos, a regra nunca recusa (D3 de #38 admite o risco) e o bullet 3 do marco continua sem prova.
  - proposta: A3 e D1.

- **R04-4 · AJUSTAR · teste** — local: `backend/src/test`, `frontend/src`.
  - esperado: `AGENTS.md` e "Pronto quando": "todo código de regra citado na issue tem ao menos um teste", conferível pelo código.
  - observado: os testes se nomeiam por critério de aceite (CA1...), não por regra. Citados em teste: só RN-PRO-02, RN-PRO-04 e RN-PRO-05. Citados nas issues e sem citação em teste: RN-MOD-01, 02, 03, 04, 05, 06, 07; RN-GRA-01; RN-ORG-17; RN-PRO-01, 03, 07, 08. Pela leitura dos nomes, a maioria é exercida (ex.: CA6 = RN-MOD-06, CA7 = RN-MOD-04); RN-PRO-08 não tem como ser até a Sprint 07 (D5 de #43). Nenhuma é de cobrança, pagamento, assinatura, inativação ou isolamento, por isso não é BLOQUEIA (RN-PRO-05, de inativação, está citada). Repete o A5 da Sprint 03.
  - proposta: A4.

- **R04-5 · AJUSTAR · teste** — local: `frontend/src/features/organization/TransferCoordinationDialog.test.tsx:51`.
  - observado: `findByRole("button", { name: "Transferir coordenação" })` expirou uma vez com o `verify` do back-end rodando em paralelo (tela ainda em "Carregando..."); passou sozinho e na reexecução completa. Teste da Sprint 03; instabilidade por tempo limite curto sob carga, mesma classe do R03-12 (`EmailServiceIT.requeue`; este não falhou nesta rodada e não foi alterado: `git diff` em `email/` vazio).
  - efeito: pode derrubar `ci-gate` de um PR sem culpa do item.
  - proposta: A5.

- **R04-6 · SUGESTÃO · dados** — local: `V23__teacher.sql:37` (`level_id ... on delete set null`) e `Graduation.java` (editor grava o sistema inteiro).
  - observado: apagar um nível no editor zera o nível do professor que o usava sem aviso e sem auditoria no professor (a lista passa a mostrar só a modalidade). Aceitável hoje; vale um aviso na janela do editor quando o nível estiver em uso por professor ou, depois, por aluno.

- **R04-7 · SUGESTÃO · segurança** — mantido do R03-7: imagem do front sem `Content-Security-Policy`, `nosniff`, `X-Frame-Options` e `Referrer-Policy` (`curl -I` na imagem reconstruída). Sem issue própria; destino final em #192 (Sprint 22). Não há hospedagem até lá (limitação aceita).

- **R04-8 · SUGESTÃO · operação** — mantido do R03-9: `origin/item/43-...`, `44-...`, `45-...` não foram apagadas (as de #38 a #42 foram); `delete_branch_on_merge` está `false`. Apagar pelo nome ou ligar a opção (dono).

- **R04-9 · SUGESTÃO · técnica** — `Graduation`: "última gravação vence" (D7 de #39) e `unit_modality`/`age_range` sem `If-Match` (D2 de #41, D4 de #40) divergem do RF-ORG-08 só para organização e unidade; aceitável. `InviteService` ganhou ramos `teacherAccess` por parâmetro booleano (`invite`, `resend`, `cancel`); funciona e tem teste, mas a próxima regra de convite deve extrair uma estratégia em vez de acrescentar outro booleano.

Não encontrei problema em: segredo exposto, dado pessoal em log ou auditoria, permissão só no front, citação de fornecedor em código de regra (`grep` por nomes de fornecedor no diff só acha ocorrências antigas fora do que a sprint tocou), migração antiga editada (V19 a V23 são novas), `@Disabled`/`skip` no diff.

#### Reconferência do que a Sprint 03 deixou para esta

| Item | Status | Evidência |
|---|---|---|
| A6 · teste de isolamento por rota nova | PASSOU | Ver tabela "De todo item": todos os grupos de rotas têm teste. A guarda automática segue inexistente (sugestão) |
| A7 · cabeçalhos de segurança do front | MANTIDO | R04-7 |
| A8 · impedimento por contagem real em #46 e #67 | MANTIDO | R04-3 |
| A9 · `EmailServiceIT.requeue` instável | MANTIDO (não falhou) | 296 ITs verdes; sem mudança de código |
| R03-9 · branches mergeadas | MANTIDO | R04-8 |
| R03-13 · "erros de campo são 400" na especificação | MANTIDO | `grep VALIDATION_ERROR docs/especificacao` sem resultado |

#### Desvios e decisões herdados dos itens

| Item | Desvio ou decisão | Classe | Destino |
|---|---|---|---|
| #38 | D2, D5: só o Coordenador ativa/desativa; Administrador só consulta | muda comportamento | ajustar especificação (A2); dono confirma D2 |
| #38 | D3: sem "desativar assim mesmo" | muda comportamento | A2 |
| #38 | D4: pedido de modalidade fica em `modality_request`, sem e-mail | técnico | registrar; RF-PNL-05 (Sprint 15) lê |
| #38 | D7: catálogo global sem RLS, sem escrita para o papel operacional | técnico, segurança | registrar; provado por `ModalityCatalogIT` |
| #39 | D1, D5: só Jiu-Jitsu tem padrão; conteúdo IBJJF e limites são suposição do agente | exceção que exige o dono (regra de negócio) | D3 |
| #39 | D3, D7: só o Coordenador edita; desligar mantém níveis; última gravação vence | muda comportamento | A2 |
| #40 | D2, D6: Administrador marca na sua unidade; desmarcar com uso não é recusado | muda comportamento | A2; D6 revisitar na Sprint 05 |
| #41 | D3, D6: apagar faixa sem restrição; Administrador só consulta | muda comportamento | A2 |
| #42 | D2, D5, D7 | muda comportamento | A2 |
| #43 | D2, D3, D5 (RN-PRO-08 sem teste) | muda comportamento / dívida | A2, A3 |
| #44 | D3, D4, D5, D9 (modalidade opcional, unidade obrigatória) | muda comportamento | A2 |
| #45 | D3: regra do Administrador (P1 ainda sem resposta do dono) | exceção que exige o dono | D2 |
| todos | canvas `.dc.html` inacessível ao agente (D5 a D10 de cada item) | sugestão sem dono | olho humano do dono no PR `dev`→`main` (Ação 1) |
| #38 a #45 | limites de validação e telefone nacional (suposições) | técnico aceitável | registrar |

#### Ajustes propostos

**A1 · Ocultar nível e grau do professor quando a graduação da modalidade está desligada** (resolve R04-1) — antes do PR
- Tipo: corrigir no item #42 (`Teachers`), com o #43 que reusa a mesma consulta.
- O que mudar e onde: em `Teachers.java:137`, juntar `organization_modality` (`graduation_enabled`) e devolver `levelId`, `level` e `degree` nulos quando desligada; `TeacherFormDialog` já não envia nível nesse caso. Opcional: manter o dado gravado para a religação.
- Como provar: IT em `TeacherIT` ou `TeacherWriteIT`: professor com nível, desligar a graduação via `PUT /modalities/{id}/graduation`, `GET /teachers` e `GET /teachers/{id}` sem `level`; religar e ver de volta (ou o comportamento decidido). Mais um teste de tela no `TeachersPage.test.tsx`.
- Tamanho: pequeno.

**A2 · Atualizar a especificação com as decisões que mudam comportamento** (resolve R04-2) — antes do PR
- Tipo: ajuste de especificação (a especificação muda primeiro).
- O que mudar e onde: em `04-modalidades-e-graduacao.md`: RN-MOD-02 (Coordenador ativa; Administrador e Coordenador marcam por unidade), RN-MOD-06 (sem "desativar assim mesmo"; recusa só na organização, não na unidade), RN-GRA-01 (só o Coordenador liga/edita; desligar mantém níveis; ligar exige ao menos um nível; conteúdo do padrão IBJJF e limites de validação conforme D3), RF-MOD-04 (Administrador só consulta; apagar faixa livre até turmas usarem). Em `05-professores.md`: alcance do Administrador (vê professor com ao menos uma unidade dele; inativa só se todas as unidades forem dele; gerencia só as suas unidades), contato visível à gestão no painel, consentimento com declaração da gestão, graduação obrigatória só com graduação ligada, lista abre em "Todas". Acrescentar a linha de erros de campo 400 (R03-13).
- Como provar: `grep` dos códigos e conferência cruzada com os itens; sem código.
- Tamanho: médio (texto).

**A3 · Levar os pontos de encaixe às issues das Sprints 05 e 07** (resolve R04-3) — antes de abrir a Sprint 05 (pode seguir depois do PR)
- Tipo: ajuste de issue (texto), com o dono.
- O que mudar: #46/#47/#48: trocar `ModalityUsage.activeClasses`, `Teachers.CLASS_COUNT`, `Teachers.headClasses`, as listas de turmas e próximas aulas do painel, a entrada em "Precisa de atenção"; matrícula (#7x, RF-MAT-01): trocar `ModalityUsage.activeStudents`; RF-BEN-03 (#7x): encerrar o benefício ao encerrar o vínculo de professor (RN-PRO-08) com teste; ambos com "Pronto quando" que prove a recusa da desativação com uso real.
- Como provar: texto da issue citando o arquivo e o teste que passa a valer.
- Tamanho: pequeno.

**A4 · Citar o código da regra nos testes da sprint** (resolve R04-4) — depois do PR (Sprint 05, primeiro PR ou ajuste fora de item)
- Tipo: ajuste fora de item.
- O que mudar: acrescentar o código RN ao `@DisplayName` do teste que já a exerce (RN-MOD-02 a 07, RN-GRA-01, RN-ORG-17, RN-PRO-01, 03, 07); RN-PRO-08 fica registrada como pendente até A3.
- Como provar: `grep -rE "RN-(MOD|GRA|PRO)-[0-9]+" backend/src/test frontend/src` cobre todos os códigos das issues.
- Tamanho: pequeno.

**A5 · Estabilizar `TransferCoordinationDialog.test.tsx`** (resolve R04-5) — depois do PR
- Tipo: ajuste fora de item.
- O que mudar: aguardar o fim do carregamento com `waitFor`/`findBy` de tempo maior (ou `vi.setConfig({ testTimeout, asyncUtilTimeout })` global moderado) nos testes que abrem a tela por `findByRole` logo após o `render`.
- Como provar: `npm run test:cov` 3 vezes seguidas em paralelo com `verify`.
- Tamanho: pequeno.

Ordem sugerida: A2 e A1 (juntos, antes do PR `dev`→`main`), depois A3; A4 e A5 na Sprint 05.

#### Decisões do dono

**D1 · O bullet "Modalidade em uso não é desativada e a tela mostra o que impede" vale agora ou passa a valer nas Sprints 05 e 07?** — recomendada: aceitar a regra e a tela como entregues com o duplo de teste e transferir a prova com dado real para a issue de matrícula (#7x) e para #46/#48 (A3) — motivo: sem turmas e alunos não há como provar de outro jeito e o agente já deixou a porta pronta. Alternativa: manter o critério aberto até a Sprint 07.

**D2 · Administrador pode inativar professor que ensina também em unidade de outro administrador?** (P1, ainda sem resposta, #45 D3) — recomendada: manter "só se todas as unidades forem dele; senão usar Editar" — motivo: não derruba o trabalho de outra unidade e é reversível por Editar. Alternativa: só o Coordenador inativa professor compartilhado.

**D3 · Aceitar o padrão IBJJF e os limites de validação escolhidos pelo agente (#39 D5) ou fornecer a lista oficial?** — recomendada: aceitar e registrar na especificação (A2), corrigindo por migração nova se o dono achar algum valor errado — motivo: a especificação só cita "IBJJF completo" sem tabela; só o dono sabe a fonte que quer.

#### Ações do dono

1. Subir o ambiente (`docker compose -f infra/docker-compose.yml up -d --wait`, `bash infra/scripts/prepare-runtime-db.sh`, back-end com perfil `local`, `bash infra/scripts/seed.sh`, `npm run dev` em `frontend/`) e abrir `http://localhost:5173`. Com o Coordenador do seed, nos temas claro e escuro, a 360 px e em tela larga, olhar contra os canvas `ConfigModalidades`, `ModalidadeGraduacao`, `ConfigUnidade` (seção modalidades), `ConfigFaixas`, `Professores`, `ProfessorPainel`, `ProfessorNovo` e `ProfessorInativar` (o agente não teve acesso a nenhum): layout, ordem das seções, tom dos textos, contraste, ausência de rolagem horizontal, foco e teclado nas janelas.
2. Reproduzir R04-1 à mão: em Configurações › Modalidades, abrir a graduação de Jiu-Jitsu, desligar; abrir Pessoas › Professores e o painel de um professor de Jiu-Jitsu com faixa. Se ainda mostrar a faixa, o achado está confirmado.
3. Responder D1, D2 e D3.
4. Revisar e, se quiser, apagar as branches remotas `item/43-...`, `item/44-...`, `item/45-...` e ligar "Automatically delete head branches" (R04-8).
5. Com o veredito `APROVAR` e A1 e A2 resolvidos: abrir o PR de `dev` para `main` com o método "Merge" (nunca squash) e, depois de revisar, dizer "sim" para liberar a Sprint 05. Esta revisão não libera a Sprint 05.

#### Limitações e o que não foi conferido

- Canvas, tom dos textos, contraste real, rolagem horizontal a 360 px, foco e teclado: só por pessoa (Ação 1); os testes usam axe em jsdom, que não mede contraste nem layout.
- `e2e:full`, gitleaks, OSV-Scanner e `actionlint` locais não rodados; o CI os roda nos PRs e passou. Workflows e `osv-scanner.toml` não mudaram na sprint.
- Publicação, hospedagem e conexões reais: NÃO CONFERIDO (limitação aceita, Sprints 22 e 23).
- Não acionei as skills `/code-review`, `/security-review` e `ponytail:ponytail-audit` no fork; fiz a leitura dirigida dos pontos de risco (controllers e permissões de `teacher`, convite de professor em `InviteService`, migrações V19 a V23 com RLS e revogação do catálogo, upload de foto, auditoria de cada escrita, `prepare-runtime-db.sh`). Nenhuma outra falha de segurança encontrada nessa leitura, que não é exaustiva (`TeacherWrite`, `Graduation` e as telas foram lidos por trechos).
- R04-1 foi conferido por leitura do código e dos testes, não por execução; a Ação 2 fecha isso.
- O `verify` rodou 12 min com a cadeia do front em paralelo; os tempos não são comparáveis com a linha de base.

#### Riscos para a próxima sprint

- **Turmas (Sprint 05):** a troca de `classCount`, `headClasses`, seções do painel, "Precisa de atenção" (RN-PRO-05) e `ModalityUsage.activeClasses` depende de A3; sem ela a regra de desativação da modalidade e da inativação de professor continua inerte. Escolha de titular só entre professores ativos e que ensinam a modalidade; a graduação do professor passa a depender de A1 se a tela de turma exibir faixa.
- **Matrícula (Sprint 07):** `ModalityUsage.activeStudents`, "Exigir comprovante da graduação de origem" (`requireOriginProof`, só guardado) e RN-PRO-08 (benefício de professor encerra com o vínculo) precisam de teste quando nascerem.
- **Sprints 18 e 20:** `directoryConsent` e a declaração da gestão estão guardados e sem uso; a regra pública (apresentação, nome, foto e graduação só com consentimento; nunca telefone e e-mail) tem de nascer com teste de resposta pública. A foto fica em `storage` sem URL assinada pública definida.
- **Dívida com prazo:** exceções OSV até 2027-01-15; `ponytail:` em `ModalityUsage.None`; cabeçalhos de segurança do front na #192.
- **Issues `depende de você`:** nenhuma trava a Sprint 05.
- **Decisão herdada:** D2 (P1) respondida pelo dono em 19/10/2026: confirmada a opção recomendada, que a #45 já implementa; P1 dada como respondida no backlog.

#### Itens para as próximas sprints

- **D1 (decisão do dono, 19/10/2026):** o critério "Modalidade em uso não é desativada e a tela mostra o que impede" passa a valer quando `ModalityUsage` tiver dados reais: turmas ativas na Sprint 05 (`ModalityUsage.activeClasses`, junto com `Teachers.CLASS_COUNT` e `Teachers.headClasses`) e alunos ativos na Sprint 07 (`ModalityUsage.activeStudents`). Nessas sprints a recusa de desativar precisa de teste com dado real. Sem issue nova.
- **D3 (decisão do dono, 19/10/2026):** padrão IBJJF e limites do #39 aceitos; registrados em `04` (RN-GRA-01).
- **D2 (decisão do dono, 19/10/2026):** P1 respondida com a opção recomendada, já implementada na #45 (Coordenador inativa qualquer professor; Administrador só se todas as unidades forem dele); P1 dada como respondida no backlog.
- **Risco para a Sprint 05 (R04-1):** quem exibir a faixa do professor em turmas deve reaproveitar `Teachers` ou repetir o filtro por `graduation_enabled`; senão o bloqueio reabre.
- **Achados R04-4 a R04-9 (seguem abertos, sem bloqueio):** R04-4 (código RN nos testes, A4), R04-5 (teste instável do front, A5), R04-6 (`level_id` com `on delete set null` e editor que grava o sistema inteiro), R04-7 (cabeçalhos de segurança do front, #192), R04-8 (branches mergeadas sem apagar), R04-9 (última gravação vence, sem `If-Match`). Detalhes em "Achados" da rodada 1, mantidos na rodada 2.

RESULTADO: AJUSTAR — 1 BLOQUEIA (R04-1: faixa do professor aparece com a graduação desligada, contra o 1º "Pronto quando" do marco) e 2 ajustes antes do PR para `main` (A1 e A2); clone limpo passou em tudo.


### Rodada 2 — APROVAR — 2026-10-19, estado f2b1ad382039379a21ae665e09a1517a66e2c486

#### Resumo e veredito

Reconferência de A1, A2 e R04-1 em `dev@f2b1ad3` (PR #271, "Ajustes da revisão da Sprint 04", squash, `ci-gate` SUCCESS; run `Publish` 39303306270 verde). R04-1 está corrigido no servidor e provado por teste de integração; A2 está na especificação; a correção extra do PUT (`TeacherWrite.keepHiddenLevels`) está correta. Clone limpo verde. Os dois bullets do marco que dependem de turmas e alunos (modalidade em uso, D1) seguem "não conferíveis até as Sprints 05 e 07" por decisão do dono e não reprovam. Veredito: **APROVAR**, no sentido de "pode abrir o PR de `dev` para `main`". Não libera a Sprint 05.

Pré-condições: `dev` igual a `origin/dev`; nenhum PR aberto contra `dev`; só o relatório não rastreado na pasta; proteção de `dev` inalterada (`ci-gate`). As branches `ajustes/revisao-sprint-04` (local e remota) e `revisao/sprint-04` (local) existem e não foram tocadas (apagar pelo nome é do dono, junto com R04-8).

#### Reconferência dos achados

| Achado | Status | Evidência |
|---|---|---|
| R04-1 (BLOQUEIA) · faixa do professor com graduação desligada | **PASSOU (corrigido)** | `Teachers.java:137-146`: a consulta da lista e do painel junta `organization_modality` e só liga `graduation_level` quando `coalesce(om.graduation_enabled, false)`; sem nível, `levelId`, `level` e `degree` saem nulos (sem linha em `organization_modality` também oculta). `TeacherIT.graduationOff` (RF-MOD-02) alterna ligado, desligado, ligado e confere `level`, `degree` e `levelId` na lista e no painel, e que o dado continua gravado. Rodou no `verify`. `grep` de `teacher_modality`: só `Teachers`, `TeacherWrite`, a migração V23 e o seed 09, então não há outro leitor que exiba a faixa; o front só mostra o que o servidor devolve |
| A1 · ocultar nível e grau | PASSOU | Desenho escolhido: ocultar e preservar (D9 em `specs/itens/42-...md`), sem migração e sem mudar contrato |
| Correção extra do PUT · `keepHiddenLevels` | **PASSOU** | `TeacherWrite.java:134-135, 271-275`: `checkModalities` zera nível e grau com a graduação desligada (e recusa 400 se o pedido trouxer nível); `keepHiddenLevels` devolve, para cada modalidade sem nível no pedido, a linha anterior (nível e grau crus, lidos por `currentModalities`, que não filtra). Com a graduação ligada o pedido sempre traz nível (senão 400 "escolha o nível"), então o ramo só age com a graduação desligada. Sem isso, editar o professor nesse estado apagaria o dado. `TeacherWriteIT.updateKeepsHiddenLevel`: desliga, edita o nome com `levelId = null`, vê `level` nulo na resposta e o `level_id` original no banco, religa e vê "Azul" e grau 2. A auditoria `teacher.updated` grava o antes cru e o depois já com o nível preservado: coerente. Caso limite aceitável: tirar a modalidade e pô-la de volta no mesmo PUT, com a graduação desligada, também preserva |
| A2 · especificação atualizada (R04-2) | **PASSOU** | `git diff c9d549b dev -- docs/especificacao`: `04` RN-MOD-02, RN-MOD-06, RN-MOD-07 (limites, três faixas padrão, só o Coordenador edita), RN-GRA-01 (só Jiu-Jitsu com padrão e 19 níveis aceitos pelo dono, limites, desligar mantém, ligar exige nível, última gravação vence), RF-MOD-04; `05` RN-PRO-02 a 05 e RF-PRO-01 (alcance do Administrador, contato só no painel, declaração, graduação só com graduação ligada, inativação sem motivo); `00` RNF-24 com o 400 `VALIDATION_ERROR` (fecha R03-13). Não achei decisão da lista de R04-2 que continue fora do texto |
| R04-3 (pontos de encaixe) | MANTIDO como D1 | Por decisão do dono (19/10/2026) o bullet passa a valer com dado real nas Sprints 05 (`ModalityUsage.activeClasses`, `Teachers.CLASS_COUNT`, `Teachers.headClasses`) e 07 (`ModalityUsage.activeStudents`). Não reprova |
| R04-4, R04-5, R04-6, R04-7, R04-8, R04-9 | MANTIDO da rodada 1 | Fora do que o PR #271 tocou. R04-5 não se repetiu (540 de 540 verdes) |

#### "Pronto quando" do marco

| Bullet | Status |
|---|---|
| Com graduação desligada numa modalidade, nenhuma tela mostra faixa para ela | **PASSOU** no servidor (`TeacherIT.graduationOff`); confirmação a olho na Ação 1 |
| Telefone e e-mail do professor nunca saem em resposta pública da API | PASSOU (mantido; `TeacherIT` verde) |
| Modalidade em uso não é desativada e a tela mostra o que impede | NÃO CONFERÍVEL até as Sprints 05 e 07 (D1 do dono). Regra e tela provadas com duplo (`ModalityIT` CA6) |

#### Clone limpo, rodada 2

Clone novo de `dev@f2b1ad3` no scratchpad (projeto Compose `ctcorerev2`), removido ao fim.

| Passo | Resultado real |
|---|---|
| ambiente | `docker compose up -d --wait`: três serviços saudáveis |
| back-end | `verify` BUILD SUCCESS em 7 min 15 s: 126 testes rápidos e 298 de integração (rodada 1: 296; +2 = `graduationOff` e `updateKeepsHiddenLevel`), 0 falhas, 0 ignorados; JaCoCo 3400 de 3486 linhas (97,53%); "All coverage checks have been met" |
| front-end | `lint`, `typecheck`, `build` exit 0; `test:cov` 66 arquivos, 540 testes, linhas 96,7%; `npm run e2e` 23 de 23 em 42 s. O front não mudou no PR #271 |
| seed e imagens | NÃO REPETIDO: `infra/seed`, Dockerfiles e `pom.xml` sem diferença desde `c9d549b`, que passou na rodada 1 |
| limpeza | `down -v` do projeto `ctcorerev2`; diretório removido; `docker images` igual ao de antes; volumes `ctcore_postgres-data` e `ctcore_storage-data` do dono presentes |

#### CI e proteções

`ci-gate` segue como único check exigido em `dev`. No PR #271: `backend`, `image-backend`, `security` e `ci-gate` pass; `frontend`, `e2e` e `image-frontend` pulados pela regra de pasta (nenhum arquivo do front mudou). `git diff c9d549b dev -- .github osv-scanner.toml` vazio.

#### Achados novos e decisões

Nenhum BLOQUEIA, AJUSTAR ou decisão do dono pendente. Observação: o D9 foi acrescentado ao arquivo do #42 depois da entrega, como ajuste de revisão, e diz isso.

#### Ações do dono

1. Confirmar R04-1 no navegador: Configurações › Modalidades, desligar a graduação de Jiu-Jitsu, abrir Pessoas › Professores e o painel de um professor de Jiu-Jitsu com faixa (sem faixa agora); religar e ver a faixa de volta.
2. Ação 1 da rodada 1 (telas contra os canvas, temas, 360 px, teclado) segue só de pessoa.
3. Abrir o PR de `dev` para `main` com o método "Merge" (nunca squash); dizer "sim" para liberar a Sprint 05 depois de revisar. Opcional: apagar as branches citadas acima e as `item/43`, `44`, `45`.

#### Riscos para a próxima sprint

Os da rodada 1 valem. Acréscimo: quem exibir faixa de professor na Sprint 05 deve reaproveitar `Teachers` ou repetir o filtro por `graduation_enabled`, senão reabre R04-1; e qualquer outro caminho que escreva `teacher_modality` deve repetir a preservação de `keepHiddenLevels`.

RESULTADO: APROVAR — R04-1, A1, A2 e a correção extra do PUT conferidos e verdes no clone limpo (298 ITs, 540 testes de front, 23 e2e); bullet de modalidade em uso não conferível até as Sprints 05 e 07 por decisão do dono.
