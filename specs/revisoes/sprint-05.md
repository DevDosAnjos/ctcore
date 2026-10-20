# Revisão da Sprint 05 · Turmas e agenda

### Rodada 1 — AJUSTAR — 2026-10-20, estado a94b1b6ce0e26aab2addd97a33679fd0ffcb7cde

#### Resumo e veredito

A sprint entregou turmas (lista, painel, janela), agenda semanal, painel da aula, edição e cancelamento, aula avulsa e dias sem aula, com 5 migrações (V24 a V28), 11 caminhos novos no OpenAPI e geração diária das 8 semanas. Os 8 itens têm rodada `OK`, PR único com `Closes #N`, squash e `ci-gate` verde. O clone limpo passa (back-end 134 + 365 testes, regras de cobertura atendidas; front 682 testes, 96,87% de linhas; e2e verde). Resultado `AJUSTAR`: a especificação não recebeu nenhuma das decisões que mudam comportamento (R05-1, repete R04-2) e deve ser atualizada antes do PR `dev`→`main`. Os demais ajustes podem seguir na Sprint 06. O dono decide D1 e D2 e olha as telas no navegador (Ações 1 a 3).

#### Estado revisado

| | |
|---|---|
| `dev` | a94b1b6ce0e26aab2addd97a33679fd0ffcb7cde (igual a `origin/dev`, pasta limpa) |
| `main` | 97992f8c3117127dcdc718b83b2cb846af193df1 |
| Ferramentas | Java 21.0.12 para o Maven (o `JAVA_HOME` da máquina é 25), Node 24.21.0, Docker 29.8.1 |
| Diff | `git diff main...dev --stat`: 96 arquivos, +13428 −39 |
| PRs da sprint | #275 (#46), #276 (#47), #277 (#48), #278 (#49), #279 (#50), #280 (#51), #281 (#52), #282 (#53) |

Pré-condições: `dev` limpa e igual à origem; nenhum PR aberto contra `dev`; as 8 issues do marco fechadas e "feito" no `specs/backlog.md`; nenhuma issue com `depende de você` nesta sprint; `gh auth` válido (DevDosAnjos); Docker ativo. Restam no remoto `origin/item/46-...` a `53-...` e `origin/ajustes/liberar-sprint-05` (ver R05-9); os itens já foram mergeados por squash, nada está em aberto.

#### Trilha de processo

| Item | PR | Verificação | Entrega | Observação |
|---|---|---|---|---|
| #46 RF-TUR-01 | #275, squash, 1 commit, `Closes #46`, `ci-gate` verde | Rodada 1 OK | sim | sem desvio de processo |
| #47 RF-TUR-02 | #276, idem | Rodada 1 OK | sim | |
| #48 RF-TUR-03 | #277, idem | Rodada 1 OK | sim | |
| #49 RF-AGE-01 | #278, idem | Rodada 1 OK | sim | |
| #50 RF-AGE-02 | #279, idem | Rodada 1 OK | sim | |
| #51 RF-AGE-03 | #280, idem | Rodada 1 OK | sim | |
| #52 RF-AGE-04 | #281, idem | Rodada 1 OK | sim | |
| #53 RF-AGE-05 | #282, idem | Rodada 1 OK | sim | |

Evidência: `gh pr view` de cada PR (7 checks e `ci-gate` em `SUCCESS`, título `#N: <título da issue>`, `Closes #N` no corpo); `grep` das seções em `specs/itens/46..53-*.md` (seis seções, Construção, `### Rodada 1 — OK`, Entrega, 0 tarefas desmarcadas). Nenhum item passou de uma rodada. Nenhum PR de outra sprint fechou issue desta, nem o contrário.

#### "Pronto quando"

**Do marco**

| Bullet | Status | Evidência |
|---|---|---|
| Não existe registro de presença em nenhuma tela nem tabela | PASSOU | `grep -i` de presença, frequência, attendance e check-in nas migrações V24 a V28, em `agenda/`, `trainingclass/` e nas telas só acha comentários que afirmam a ausência; `ClassesPage.test.tsx` CA4, `AgendaPage.test.tsx` CA12 e `NoClassDaysPage.test.tsx` CA19 conferem os arquivos de tradução |
| Sempre existem 8 semanas de aulas à frente | PASSOU | `LessonGenerator` (56 dias, índice único, idempotente) e `LessonGenerationJob` (cron 03:00 UTC com ShedLock); `LessonGenerationIT` CA1, CA2 e CA4 passaram no `verify`. Limite: o ambiente local semeado fica sem aulas até o cron (R05-5) |
| Mudar o horário da turma não desfaz uma aula alterada à mão | PASSOU | `LessonGenerationIT` CA4 ("mudar o horário refaz só as futuras agendadas"), colunas `manually_edited` e `original_*` (V26), `LessonWrite.update` |
| O substituto de outra unidade perde acesso aos alunos da turma no dia seguinte à aula | PASSOU em parte (D2) | `AgendaLessonIT` CA4 ("substituto de outra unidade abre só no dia da aula; fuso da unidade") prova o corte no painel da aula. Alunos por turma só existem na Sprint 07; o painel mostra só contagens (0) |
| Todos os itens do "Pronto quando" geral valem para cada item | ver tabela abaixo | |

**De todo item (plano de sprints)**

| Regra | Status | Evidência ou motivo |
|---|---|---|
| Regras citadas com teste | PASSOU em parte | Os testes citam `CA<n>`, não o código RN. Aparecem por nome RF-TUR/AGE-01 a 05, RN-AGE-01, 02, 03, 06, 08, RN-PRO-06 e RN-TUR-03. Sem citação direta: RN-AGE-04, 05, 07, 09, 11, 12, 13 e RN-TUR-01, 02, 04, 05, 06 (exercidas por CAs, rastreáveis só pelo arquivo do item). Nenhuma é de cobrança, pagamento, assinatura, inativação ou isolamento (R05-2) |
| Cobertura 80%, 100% nos pacotes críticos | PASSOU | `verify`: "All coverage checks have been met"; front 96,87% de linhas. Pacotes críticos não mudaram na sprint |
| Rota nova com teste de isolamento entre organizações e entre perfis | PASSOU | `AgendaLessonIT` CA5 e CA7, `TrainingClassWriteIT` CA8 e CA9 (RLS com `ctcore_app`), `NoClassDay*IT`, `LessonGenerationIT` CA2 |
| OpenAPI, implementação e MSW batendo | PASSOU | 11 caminhos novos em `docs/openapi.yaml` (`/agenda*`, `/no-class-days*`, `/classes*`); `OpenApiContractTest` e `ContractRoutesTest` dentro do `verify` verde; `agenda.msw.test.ts`, `classes.msw.test.ts`, `noClassDays.msw.test.ts` |
| Auditoria em toda escrita | PASSOU | `class.created/updated`, desativação da turma, `lesson.created/updated/cancelled/generated/regenerated`, `no_class_day.created/updated/deleted`; sem senha, token nem link de grupo (`TrainingClassWriteIT` CA1 e CA10) |
| Centavos, idempotência, lotes tudo ou nada | PASSOU (parte aplicável) | Sem valor financeiro; geração idempotente por índice único; dia sem aula em várias unidades em uma transação (`NoClassDayWriteIT`) |
| Quatro estados, 360 px e axe nos dois temas | PASSOU (por proxy) | testes de tela com largura 360 e axe nos dois temas; jsdom não mede contraste nem layout (Ações 1 a 3) |
| Textos só nos arquivos de tradução | PASSOU | `npm run lint` (regra de texto literal) exit 0. Ressalva: mensagens de validação do servidor em português (`LessonWrite`) e `notice_status` gravado como "Ainda não avisado" no banco; a API expõe código (`NOT_NOTIFIED`) e a tela traduz (R05-9) |
| Recurso novo desligado por organização | não se aplica nesta sprint | a especificação não prevê chave por organização para turmas e agenda |
| Notas de versão | não se aplica nesta sprint | não há processo de notas de versão definido para esta entrega |

#### Clone limpo

Clone novo de `origin/dev` no scratchpad, projeto Compose `ctcorerev5`, portas padrão livres, só README e "Comandos". Removido ao fim.

| Passo | Comando | Resultado real |
|---|---|---|
| ambiente | `docker compose up -d --wait` (em `infra/`, sem `.env`) | postgres, storage e mailbox saudáveis em 31 s; `prepare-runtime-db.sh` ok |
| back-end | `./backend/mvnw -f backend/pom.xml verify` (Java 21) | BUILD SUCCESS em 6 min 11 s; 134 testes rápidos e 365 de integração, 0 falhas, 1 ignorado (`AgendaLessonIT.statusAndFlags`, R05-3); "All coverage checks have been met" |
| front-end | `npm ci`, `lint`, `typecheck`, `build` | exit 0 (25 s, 9 s, 9 s, 12 s) |
| front-end | `npm run test:cov` | 1ª execução, em paralelo com o `verify`: FALHOU 1 de 682 (`TwoFactorLogin.test.tsx:155`, heading "Bem-vindo ao ctcore" não achado); 2ª execução isolada: 76 arquivos, 682 testes, 94,02% de instruções, 96,87% de linhas, 1 min 43 s (R05-4) |
| e2e | `npm run e2e` | exit 0 em 52 s |
| seed (3x) | `bash infra/scripts/seed.sh`, com o jar no perfil `local` | exit 0 nas três; contagens finais: 9 turmas, 15 horários, 0 aulas, 3 dias sem aula (R05-5) |
| imagens | `docker build` das duas | back-end: `uid=10001`, readiness `UP` na porta de gestão com banco e `TWO_FACTOR_ENCRYPTION_KEY`; front-end (nginx na 8080): 200 em `/` e `/conta`, 404 em `/api/v1/auth/session`; sem `Content-Security-Policy`, `nosniff` e `X-Frame-Options` (mantido, R04-7) |
| recomeço | `down -v` | feito; o novo `up` NÃO foi repetido (nenhum arquivo de `infra/` além dos dois seeds mudou na sprint: `git diff main...dev --stat -- infra`) |

Limpeza: projeto `ctcorerev5` removido (`down -v`), contêineres de prova com `--rm`, imagens `ctcore-backend` e `ctcore-frontend` removidas pelo nome (`docker images` igual ao inicial), volumes `ctcore_postgres-data` e `ctcore_storage-data` do dono presentes; o contêiner `competent_neumann` e a imagem `postgres:17` do dono ficaram intocados.

Comparação com a Sprint 04 (126 + 298 testes e 97,53% no back-end; 540 testes e 96,7% no front; 23 e2e): back-end 134 + 365 (cresceu); front 682 testes e 96,87% (melhorou); `verify` em 6 min 11 s (abaixo dos 7 min 15 s a 11 min 58 s da Sprint 04). A falha única do front ocorreu sob carga, como em R04-5.
Passos do README que não bastaram: nenhum dos passos falhou, mas README e `backend/README.md` não dizem que a agenda só ganha aulas pela tarefa das 03:00 UTC (R05-5).

#### CI e proteções

| Item | Estado real | Evidência |
|---|---|---|
| `ci-gate` exigido em `dev` | sim; push forçado e exclusão bloqueados; `enforce_admins` falso | `gh api repos/:owner/:repo/branches/dev/protection` |
| regra de `main` | ruleset `protege-main` ativo: `deletion`, `non_fast_forward`, `pull_request`, `required_status_checks` | `gh api repos/:owner/:repo/rulesets/25019105` |
| últimos runs em `dev` | `Publish` com sucesso nos 8 commits da sprint (ex.: run 39353149662 em a94b1b6); `ci.yml` roda nos PRs, 8 de 8 com `ci-gate` verde | `gh run list --branch dev`, `gh pr view` |
| `permissions:` e pinagem | `.github/` sem mudança na sprint (`git diff main...dev --stat -- .github` vazio); `permissions:` no topo do `ci.yml`; ações fixadas por versão (`checkout@v7.0.1`, `setup-java@v6.1.0`, `setup-node@v7.1.0`, `upload-artifact@v7.0.2`) | `grep` em `ci.yml` |
| exceções de vulnerabilidade | `GHSA-j9f9-w8pj-32f8` e `GHSA-pc63-qcmh-9cmg`, ambas até 2027-01-15, com motivo; sem mudança na sprint | `osv-scanner.toml` |
| segredos e variáveis do fluxo | `gh secret list` e `gh variable list` vazios; `PUBLISH_REGISTRY*` ausentes, publicação pulada | NÃO CONFERIDO (limitação aceita: sem hospedagem até a Sprint 22) |

#### Achados

- **R05-1 · AJUSTAR · especificação** — local: `docs/especificacao/` (sem mudança na sprint: `git diff main...dev --numstat -- docs/especificacao` vazio) e as decisões "(muda o comportamento)" de #46 D2 e D6; #47 D4 e D5; #48 D3, D4 e D6; #49 D1, D4 e D5; #50 D1 e D2; #51 D1 a D4; #52 D1 e D3; #53 D1, D2, D4 e D5.
  - esperado: `AGENTS.md`: "Mudou uma regra, muda primeiro a especificação".
  - observado: cerca de 25 decisões que mudam comportamento só existem nos arquivos dos itens. Repete R04-2.
  - evidência: `grep -n "muda o comportamento" specs/itens/4[6-9]-*.md specs/itens/5[0-3]-*.md`; diff de `docs/` sem nenhum arquivo de `especificacao/`.
  - efeito: a especificação deixa de descrever o produto (Professor sem acesso à lista de turmas; feriados nacionais calculados no código; desfazer só antes do dia; Administrador sem lote).
  - proposta: A1.

- **R05-2 · AJUSTAR · teste** — local: `backend/src/test/java/br/com/ctcore/{agenda,trainingclass}`, `frontend/src/features/{agenda,classes,noClassDays}`.
  - esperado: A4 de R04-4 (citar o código RN no teste) e "todo código de regra citado na issue tem ao menos um teste".
  - observado: os testes citam `CA<n>`. Doze códigos das issues não aparecem por nome (RN-AGE-04, 05, 07, 09, 11, 12, 13; RN-TUR-01, 02, 04, 05, 06). As regras são exercidas pelos CAs, mas só se acham pelo arquivo do item.
  - evidência: laço de `grep` por código em `backend/src/test`, `frontend/src` e `frontend/e2e` (0 arquivos para os doze).
  - efeito: rastreabilidade só indireta; nenhuma regra crítica envolvida.
  - proposta: A3.

- **R05-3 · AJUSTAR · teste** — local: `AgendaLessonIT.java:114` (`statusAndFlags`).
  - esperado: teste determinístico do estado "em andamento".
  - observado: `assumeTrue` com a hora real em America/Sao_Paulo entre 1 e 21; fora disso o trecho `IN_PROGRESS` é ignorado. No `verify` desta revisão: "Skipped: 1" (hora local 22 h). O PR #282 foi mergeado às 22 h 23 (BRT), então o trecho pode ter sido pulado também no CI daquele PR.
  - evidência: `[WARNING] Tests run: 5, ..., Skipped: 1 ... AgendaLessonIT`.
  - efeito: CA2 sem prova numa parte do dia.
  - proposta: A4.

- **R05-4 · AJUSTAR · teste** — local: `frontend/src/features/auth/TwoFactorLogin.test.tsx:155`.
  - esperado: suíte estável.
  - observado: 1 de 682 falhou sob carga (junto do `verify`); sozinha passou. Mesmo padrão de R04-5, em outro arquivo, o que sugere causa geral (tempo do `findBy`/`getByRole` com 76 arquivos e jsdom criado 76 vezes).
  - efeito: o `ci-gate` pode reprovar um PR por flake.
  - proposta: A4.

- **R05-5 · AJUSTAR · operação/documentação** — local: `infra/seed/11-agenda.sql`, `README.md`, `backend/README.md`.
  - esperado: seguir o README e abrir a Agenda com aulas.
  - observado: depois do `seed.sh` há 9 turmas e 0 aulas; as aulas só nascem pela tarefa das 03:00 UTC (ou ao criar/editar uma turma). O comentário do seed diz "Lessons come from the daily job"; nenhum README cita a tarefa nem como disparar.
  - evidência: `psql`: `select count(*) from lesson` = 0 depois de 3 seeds com o back-end rodando.
  - efeito: quem revisa a tela no navegador (Ações 1 e 2) vê a agenda vazia e pode concluir que a sprint falhou.
  - proposta: A5.

- **R05-6 · AJUSTAR · API** — local: `backend/src/main/java/br/com/ctcore/teacher/Teachers.java:185-191`.
  - esperado: A3 de R04-3 e #43 R1: o painel do professor traz `classes` (titular e auxiliar) e `upcomingClasses`.
  - observado: `upcomingClasses` é sempre `List.of()` e `headClasses` só lista a titularidade, enquanto `classCount` conta titular e auxiliar. Nenhuma das issues #46 a #53 cita o painel do professor.
  - efeito: contagem e lista do mesmo professor divergem; "próximas aulas" vazio com a agenda já existente.
  - proposta: A6.

- **R05-7 · DONO · regra de negócio** — local: #40 D6, deixado para "revisitar na Sprint 05".
  - observado: não encontrei checagem que recuse desmarcar uma modalidade na unidade com turma ativa dela (`grep` de `unit_modality` com delete sem resultado nos Java); #48 D6 só impede escolher modalidade não oferecida ao criar ou editar a turma. Uma turma pode seguir ativa com modalidade que a unidade deixou de oferecer.
  - proposta: D1.

- **R05-8 · SUGESTÃO · API** — `LessonWrite.update` com escopo `FOLLOWING` regrava horário e professor de todas as aulas seguintes ainda `Agendada`, inclusive as já trocadas à mão (decisão #51 D1). Registrar na especificação junto de A1.

- **R05-9 · SUGESTÃO · várias**
  - nginx do front sem `Content-Security-Policy`, `nosniff`, `X-Frame-Options`, `Referrer-Policy` (mantido de R03-7 e R04-7).
  - `origin/item/46-...` a `53-...` seguem no remoto (mantido de R04-8); `origin/ajustes/liberar-sprint-05` tem um commit "docs: libera a Sprint 05 no backlog" fora de `dev` (a liberação entrou pelo #274).
  - `active_organization_ids()` (V25, `security definer`) não revoga `EXECUTE` de `PUBLIC`; devolve só ids e foi decisão consciente, mas revogar de `PUBLIC` e conceder a `ctcore_app` reduz a superfície.
  - `notice_status` guarda texto em português; melhor guardar o código e traduzir na tela.
  - Sem controle de versão na edição de aula (#51 D7): a última gravação vence.

#### Desvios e decisões herdados dos itens

| Item | Desvio ou decisão | Classe | Destino |
|---|---|---|---|
| #46 | D2 lista traz desativadas com selo; D6 Professor sem lista (usa a Agenda) | muda comportamento | A1 |
| #46 | D7 contadores de #34, #38, #42, #43, #45 trocados por contagem real (`TrainingClassIT.realCounts`) | técnico | registrar; resta R05-6 |
| #46 a #53 | D8 a D11 e semelhantes: canvas `.dc.html` inacessível ao agente | sugestão sem dono | olho humano do dono (Ações 1 a 3) |
| #47 | D4 Desativar turma só sem aluno principal, sem reativar; D5 `CLASSES_MANAGE` | muda comportamento | A1 |
| #48 | D3 turma desativada não é editável; D4 professor inativo recusado; D6 modalidade precisa ser da unidade | muda comportamento | A1; ligado a D1 |
| #49 | D1 dia sem aula vale para toda a unidade nesta entrega; D4 refazer só aulas futuras `Agendada`; D5 `canEdit` e `canCancel` (Coordenador também edita concluída até 30 dias) | muda comportamento | A1 |
| #50 | D1 janela do substituto só no painel, não na lista; D2 botões só com `canEdit`/`canCancel` | muda comportamento | A1 |
| #51 | D1 "esta e as seguintes" desloca datas; D2 a D4 | muda comportamento | A1 |
| #52 | D1 aula avulsa sem `class_id` e com `visibility_class_id`; D3 permissão do Professor | muda comportamento | A1 |
| #53 | D1 feriados nacionais no código; D2 só o Coordenador faz lote; D4 aviso só gravado; D5 desfazer só antes do dia | muda comportamento | A1; D4 consumido na Sprint 09 |
| #47, #48, #50, #51, #53 | "Avisar turma", aviso de cancelamento e de dia sem aula só registrados | técnico (encaixe) | Sprint 09 (RN-AGE-10) |
| #46, #47, #50 | `activeStudents` e `trialClasses` valem 0 | técnico (encaixe) | Sprints 07 e 18 |

Especificação x código: não achei porta, nome ou versão que o código contradiga; o desvio é de omissão (R05-1).

#### Ajustes propostos

**A1 · Levar as decisões da sprint para a especificação** (resolve R05-1, R05-8) — antes do PR
- Tipo: ajuste de especificação.
- O que mudar e onde: `docs/especificacao/06-turmas-e-agenda.md` (RF-TUR-01 a 03, RF-AGE-01 a 05, RN-TUR-03 e 05, RN-AGE-01 a 12, "Dados"), `05-professores.md` (RN-PRO-05 e 06) e `02-contas-e-acesso.md` (quem vê a lista de turmas; "Dias sem aula"): as decisões marcadas "muda o comportamento" na tabela acima.
- Como provar: `grep` dos códigos e conferência cruzada com os itens; sem código.
- Tamanho: médio (texto).

**A2 · Decidir D1 e, se for o caso, recusar desmarcar modalidade com turma ativa** (resolve R05-7) — depois do PR (início da Sprint 06)
- Tipo: decisão do dono; se "recusar", corrigir no item #40.
- Como provar: IT que cria turma ativa e tenta desmarcar a modalidade da unidade.
- Tamanho: pequeno.

**A3 · Citar o código da regra nos testes** (resolve R05-2) — depois do PR
- Tipo: ajuste fora de item; acrescentar o RN ao `@DisplayName` ou ao nome do teste que já o exerce.
- Como provar: `grep -rE "RN-(AGE|TUR|PRO)-[0-9]+" backend/src/test frontend/src` cobre os códigos das issues.
- Tamanho: pequeno.

**A4 · Tornar os testes determinísticos** (resolve R05-3, R05-4) — depois do PR
- Tipo: ajuste fora de item. Injetar `Clock` fixo em `AgendaLessonIT.statusAndFlags` e tirar o `assumeTrue`; no front, aumentar `asyncUtilTimeout` global ou aguardar com `findBy` em `TwoFactorLogin.test.tsx` (e em `TransferCoordinationDialog.test.tsx`, R04-5).
- Como provar: `verify` sem "Skipped" em qualquer hora; `npm run test:cov` três vezes em paralelo com o `verify`.
- Tamanho: pequeno.

**A5 · Agenda com aulas depois do seed** (resolve R05-5) — depois do PR (ideal antes da revisão visual)
- Tipo: corrigir no item #49. Opção mínima: documentar no `backend/README.md` e no README da raiz que a tarefa roda às 03:00 UTC e como disparar. Opção melhor: o perfil `local` (ou o `seed.sh`) dispara a geração uma vez; `LessonGenerationJob.runOnce()` já existe.
- Como provar: clone limpo, só o README, abrir a Agenda com aulas.
- Tamanho: pequeno.

**A6 · Painel do professor com turmas (titular e auxiliar) e próximas aulas** (resolve R05-6) — depois do PR (primeiro da Sprint 06)
- Tipo: novo item (ou ajuste fora de item no #43).
- Texto de issue proposto: "Painel do professor: turmas e próximas aulas". Entregar: `classes` com titular e auxiliar coerentes com `classCount`; `upcomingClasses` com as próximas aulas (titular, auxiliar ou substituto) da agenda. Regras: RN-PRO-05, RN-PRO-06. Pronto quando: teste de API com professor titular, auxiliar e substituto; a tela do painel mostra a lista; isolamento entre organizações. Marco sugerido: Sprint 06.
- Tamanho: pequeno.

Ordem sugerida: A1 (antes do PR); depois A5, A4, A6, A3, A2.

#### Decisões do dono

**D1 · Uma unidade pode deixar de oferecer uma modalidade que ainda tem turma ativa?** — recomendada: recusar, listando as turmas (como RN-MOD-06 faz na organização) — motivo: evita turma ativa numa modalidade fora da oferta, o que #48 D6 já proíbe ao criar ou editar — alternativa: aceitar e só avisar.

**D2 · O bullet "O substituto de outra unidade perde acesso aos alunos da turma no dia seguinte" vale agora só para o painel da aula?** — recomendada: aceitar como entregue no painel e transferir a prova com alunos reais para a issue de matrícula (Sprint 07) — motivo: alunos por turma só existem lá — alternativa: segurar o PR até lá (não recomendo).

#### Ações do dono

1. Subir o ambiente (`docker compose -f infra/docker-compose.yml up -d --wait`, `bash infra/scripts/prepare-runtime-db.sh`, back-end no perfil `local`, `bash infra/scripts/seed.sh`, `npm run dev` em `frontend/`). A agenda fica vazia até as 03:00 UTC (R05-5): crie ou edite uma turma em Operação › Turmas para gerar as 8 semanas dela.
2. Abrir `http://localhost:5173`, no menu Operação: Turmas, painel de uma turma, janela de turma, Agenda, painel de uma aula, editar e cancelar aula, nova aula avulsa e Dias sem aula; nos temas claro e escuro, a 360 px e a 1280 px. Conferir contra os canvas (`Turmas`, `TurmaPainel`, `TurmaEditar`, `Agenda`, `GMAgenda`, `AgendaPainel`, `AulaEditar`, `AulaCancelar`, `AulaAvulsa`, `DiasSemAula`, `DiaSemAulaNovo`), sem rolagem horizontal, contraste, foco e teclado. Os agentes não puderam abrir os canvas.
3. Conferir o tom dos textos pt-BR das janelas de cancelamento e de dia sem aula ("A mensalidade não muda").
4. Responder D1 e D2 e autorizar A1 (a especificação muda primeiro).
5. Com o veredito `APROVAR` numa rodada 2 e A1 resolvido: abrir o PR de `dev` para `main` com o método "Merge" (nunca squash) e, depois de revisar, dizer "sim" para liberar a Sprint 06.

#### Limitações e o que não foi conferido

- Camada técnica: não acionei `/code-review`, `/security-review` nem `ponytail-audit` como skills; li o diff dirigido (migrações V24 a V28, `LessonGenerator`, `LessonGenerationJob`, `LessonWrite.update`, controllers e auditoria, `ModalityUsage`, `Teachers`) e confirmei por testes e `grep`. Não é varredura exaustiva das 13 mil linhas.
- Contraste, 360 px sem rolagem, axe real, foco e teclado: só por proxy (jsdom). Proposta, sem criar: um teste Playwright `@critical` de `/agenda` a 360 px nos dois temas com `@axe-core/playwright`.
- Canvas inacessível aos agentes: tela contra o desenho só por pessoa.
- Hospedagem, publicação e segredos de publicação: NÃO CONFERIDO (limitação aceita, até a Sprint 22).
- Recomeço (`down -v` e novo `up`): não repetido, ver "Clone limpo".

#### Riscos para a próxima sprint

- Pontos de encaixe prontos: `ClassScheduleChange` e "Avisar a turma" (Sprint 09); `activeStudents` e "Ver alunos" (Sprint 07); `trialClasses` e RN-AGE-06 (Sprint 18); `visibilityClass` da aula avulsa para o portal (Sprint 10); aviso de dia sem aula "Enviado" (Sprint 09).
- A Sprint 06 (planos, alunos e responsáveis) herda A6, a decisão D1 e a janela de acesso do substituto (D2) para a matrícula da Sprint 07.
- Exceções com validade: duas do `osv-scanner.toml`, até 2027-01-15.
- Issues `depende de você`: nenhuma nesta sprint.
- A geração diária depende do cron das 03:00 UTC com ShedLock; falha numa organização só gera log (`LOG.error`), sem alerta; vale métrica ou alarme quando houver observabilidade real (Sprint 22).

#### Itens para as próximas sprints

- **D1 (decisão do dono, 19/10/2026):** recusar. Desmarcar uma modalidade numa unidade com turma ativa dessa modalidade é recusado com 409 `UNIT_MODALITY_IN_USE`, listando as turmas. Aplicado no PR de ajuste (RN-MOD-06 e RF-MOD-03 reescritas em `04`; `UnitModalities.refuseIfClasses`; OpenAPI, MSW e tela). Alunos só impedem a desativação na organização.
- **D2 (decisão do dono, 19/10/2026):** o bullet "substituto de outra unidade perde o acesso aos alunos no dia seguinte" fica aceito como entregue no painel da aula (`Agenda.substituteWindow`, 404 fora do dia). A prova com alunos reais fica para a Sprint 07 (alunos por turma).
- **A3 da Sprint 04 (pontos de encaixe nas issues de matrícula e RF-BEN-03): NÃO aplicado.** `gh issue view` de #66 (RF-BEN-03 · Encerrar benefício) e #67 (RF-MAT-01 · Nova matrícula em etapas) não cita `ModalityUsage.activeStudents` nem RN-PRO-08. Falta: #67 trocar `ModalityUsage.activeStudents` e `TrainingClassDetail` alunos/`blockingStudents` pela contagem real e provar a recusa de `MODALITY_IN_USE`, `UNIT_HAS_BLOCKERS` e `HAS_PRIMARY_STUDENTS` com aluno real; #66 encerrar o desconto de professor ao encerrar o vínculo (RN-PRO-08) com teste. Sem issue nova: o texto das issues existentes é o dono quem edita.
- **Decisões da Sprint 05 levadas à especificação que o dono deve conferir (A1):** (1) RN-AGE-08, o Coordenador também corrige a aula concluída até 30 dias, como o Administrador (a especificação dizia só Administrador, #49 D5); (2) RF-TUR-01/02, o Professor não acessa a lista nem o painel da turma (#46 D6, #47 D5); (3) RN-TUR-05, titular e auxiliar não precisam ensinar a modalidade (#48 D4); (4) RN-AGE-09, lista fixa de feriados nacionais, sem Carnaval e Corpus Christi (#53 D1); (5) RN-AGE-09, só o Coordenador lança dia sem aula em lote, o Administrador uma unidade por vez (#53 D2); (6) RN-AGE-10, editar e desfazer dia sem aula só antes do dia (#53 D5); (7) RN-AGE-04, "esta e as seguintes" regrava horário e professor inclusive de aulas já trocadas à mão e desloca as datas pelo mesmo número de dias (#51 D1, R05-8); (8) RN-AGE-08, aula concluída só tem o professor corrigido (#51 D3); (9) RN-AGE-12, aula avulsa sem turma, com visibilidade opcional (#52 D1); (10) RF-AGE-02, substituto só abre a aula no dia (404 fora, #50 D1).
- **Ajustes aplicados em `ajustes/revisao-sprint-05`:** A1 (especificação `02`, `04`, `05`, `06`; sem código), A2 (D1), A3 (código RN nos `@DisplayName`: RN-AGE-04, 05, 07, 09, 12, 13 e RN-TUR-01, 02, 04, 05, 06 no back-end; RN-AGE-11 no teste de tela de dias sem aula), A4 (`AgendaLessonIT` com relógio fixo e sem `assumeTrue`; `TwoFactorLogin.test.tsx` com `findBy` e `asyncUtilTimeout` de 10 s), A5 (README da raiz, `backend/README.md` e comentário do seed), A6 (painel do professor com `classes` de titular e auxiliar e `upcomingClasses` da agenda; `classId` nulo na aula avulsa, único ajuste de contrato). Sem migração.
- **Sugestões da rodada 2, sem bloqueio (para a Sprint 06 em diante, sem issue nova):**
  - **Trava na recusa do A2:** `UnitModalities.refuseIfClasses` consulta as turmas ativas e grava em seguida, sem trava entre a recusa e a criação de uma turma ativa da mesma modalidade na unidade; duas requisições simultâneas podem deixar uma turma ativa com modalidade desmarcada. Fechar com `select ... for update` na linha de `unit_modality` (ou na unidade) tanto aqui quanto em `TrainingClassWrite`.
  - **Lacunas do teste do A6 (`TeacherIT.panelClassesAndUpcoming`):** o corte `limit 10` não tem teste; o isolamento entre organizações só confere a ausência de `upcomingClasses` na resposta, não o 404 explícito; a aula em que o titular foi substituído sai das próximas aulas dele (decisão implícita, sem teste).
  - **R05-9, mantidas:** nginx do front sem `Content-Security-Policy`, `nosniff`, `X-Frame-Options` e `Referrer-Policy` (repete R03-7 e R04-7; item #192); `active_organization_ids()` (V25, `security definer`) sem `REVOKE EXECUTE` de `PUBLIC` e `GRANT` a `ctcore_app`; `notice_status` guarda texto em português em vez de código; edição de aula sem controle de versão (última gravação vence, #51 D7); branches `item/*` e `ajustes/liberar-sprint-05` no remoto (limpas junto deste PR, quando existirem).
- **Opções de configuração por academia, ainda NÃO decididas (para o dono decidir na sprint indicada, sem issue nova):**
  - **Sprint 07 (#63/#67):** a capacidade da turma poder bloquear a matrícula acima do limite, por turma, em vez de só avisar (RN-TUR-02, RN-AGE-13).
  - **Sprint 06:** permissão do Professor por unidade "ver ficha do aluno" (já decidida, ver D2 de 20/10/2026 abaixo; implementação junto da ficha, #60).
  - **Futuro, baixa prioridade:** prazo de correção de aula concluída por unidade (hoje 30 dias, RN-AGE-08); janela do substituto (RN-PRO-06); Professor criar aula avulsa para outro professor (RN-AGE-07).
- **Pendências das rodadas 3 e 4, mantidas sem issue nova (decisão do dono de 20/10/2026):**
  - **Começo da Sprint 06:** tratar a instabilidade de `S3FileStorageIT.refusesExpiredAndTamperedUrls` (R05-17), que falhou no CI dos PRs #50 e #286 e passou ao reexecutar o job; não faz parte de nenhum item de produto.
  - **Junto da quinta chave da Sprint 06 (ficha do aluno, #60):** corrigir "quatro permissões ligáveis por unidade" em `specs/projeto.md:19` (R05-15). O comentário do mock e o nome de `PermissionsIT:73` dizem "quatro" e hoje isso é verdade, porque só quatro estão implementadas.
  - **Sugestões sem ação, para a Sprint 06 em diante:** R05-10 (`followingEdited` sem `organization_id` explícito; a RLS cobre), R05-11 (recusas de retirar modalidade da unidade e do professor sem trava de concorrência), R05-13 (auxiliar que deixou de ensinar a modalidade sai da escolha na edição da turma sem aviso) e R05-16 (o MSW de `followingEdited` conta as edições da sessão e não olha `substitute`; o back-end é a referência).


### Rodada 2 — APROVAR — 2026-10-20, estado 48805507b62d1023adf8675a62bc4fae29b2c1d3

#### Resumo e veredito

`dev@4880550` (PR #283, squash, `ci-gate` e os 7 checks em `SUCCESS`). Todos os achados AJUSTAR e BLOQUEIA da rodada 1 estão resolvidos com evidência (tabela abaixo), D1 e D2 estão registrados e aplicados, e o clone limpo passa com 0 testes ignorados. Não há BLOQUEIA nem AJUSTAR abertos. Significa "pode abrir o PR de `dev` para `main`" (método Merge), não "Sprint 06 liberada". Pendem só as Ações do dono (olhar as telas) e a conferência das dez decisões que passaram à especificação.

Pré-condições: `dev` igual a `origin/dev`; a única mudança na pasta é este relatório (sem commit, como pedido); nenhum PR aberto contra `dev`; branches locais `ajustes/revisao-sprint-05` e `revisao/sprint-05` e remotos `item/46..53-*` mantidos, nada apagado.

#### Reconferência dos achados da rodada 1

| Achado | Ajuste | Status | Evidência observada |
|---|---|---|---|
| R05-1 | A1 | PASSOU | `git diff a94b1b6 dev -- docs/especificacao`: `02` (linha "Turmas: ver lista e painel, criar, editar e desativar"), `04` (RN-MOD-06, RF-MOD-03), `05` (RN-PRO-05, 06), `06` (RN-TUR-01, 03, 05; RN-AGE-02, 04, 05, 07 a 10, 12; RF-TUR-01, 02; RF-AGE-01, 02). As dez decisões listadas em "Itens para as próximas sprints" aparecem uma a uma no texto. A lista de feriados de RN-AGE-09 bate com `Holidays.of` (10 feriados; Carnaval e Corpus Christi fora). R05-8 ficou registrado em RN-AGE-04 |
| R05-7 | A2 (D1) | PASSOU | ver abaixo |
| R05-2 | A3 | PASSOU | `grep` de cada código das issues #46 a #53 em `backend/src/test`, `frontend/src` e `frontend/e2e`: os 20 códigos citados nas issues (RN-AGE-01 a 09, 11 a 13; RN-PRO-05, 06; RN-TUR-01 a 06) têm 1 arquivo ou mais. RN-AGE-10 não está nas issues e segue sem teste citado por nome (o envio é da Sprint 09) |
| R05-3 | A4 | PASSOU | ver abaixo |
| R05-4 | A4 | PASSOU (por observação) | `TwoFactorLogin.test.tsx` com `findBy` nas duas asserções e `asyncUtilTimeout` de 10 s. `npm run test:cov` rodou em paralelo com o `verify` (4 min, mais lento que na rodada 1): 76 arquivos, 684 testes, 0 falhas |
| R05-5 | A5 | PASSOU | README da raiz e `backend/README.md` dizem 03:00 UTC, 8 semanas, geração na hora ao criar ou editar horários de turma e `runOnce()` sem rota. Conferi no código: `TrainingClassWrite.java:155` (`lessons.forClass` ao criar) e `:186` com `ClassScheduleChange.java:47` (`generateClass` ao mudar horários). O comentário do seed bate. Não repeti o `seed.sh` (só mudou um comentário SQL) |
| R05-6 | A6 | PASSOU | ver abaixo |

**A2, 409 `UNIT_MODALITY_IN_USE` (R05-7).** `UnitModalities.refuseIfClasses` só roda quando a unidade oferece e o pedido desmarca; consulta turmas `ACTIVE` da modalidade na unidade e devolve cada uma em `fields` (`class`), por nome. Código novo em `ErrorCode`, `messages.properties`, OpenAPI (409 no PUT e enum) e MSW. Teste `UnitModalityIT.withdrawRefusedWithActiveClasses`: 409 com `Judô Adulto` e `Judô Kids` em ordem; turma `INACTIVE`, de outra modalidade, de outra unidade e de outra organização não impedem; a oferta continua `true` e não grava auditoria `unit.modality.withdrawn`; perfis: Administrador da unidade 409 (mesma regra), Professor 403, Coordenador de outra organização 404; depois de desativar as turmas, desmarcar dá 200. Isolamento entre organizações e entre perfis cobertos. Ressalva (SUGESTÃO): checagem e gravação ficam na mesma transação, sem trava; criar uma turma no instante em que se desmarca pode passar. Janela estreita, sem consequência financeira.

**A4, sem `assumeTrue`.** `grep assumeTrue|Assumptions|@Disabled` em `backend/src/test`: nenhum. `AgendaLessonIT` usa `@MockitoSpyBean Clock` e `doReturn` de meio-dia em São Paulo; `Agenda` recebe o mesmo `Clock`. `verify` completo: 134 rápidos e 367 de integração, 0 ignorados (na rodada 1 havia 1).

**A6 e a dúvida "o teste foi visto falhar?"** O teste foi escrito junto com a implementação, então testei por mutação num clone temporário: voltei `Teachers.java` e `UnitModalities.java` para `a94b1b6` (só titular, `upcomingClasses` vazio, sem recusa) e rodei `TeacherIT`, `UnitModalityIT` e `AgendaLessonIT`. Resultado: `TeacherIT.panelClassesAndUpcoming:141` FALHA (a lista traz só `Judô Adulto`, falta a turma de auxiliar); `UnitModalityIT.withdrawRefusedWithActiveClasses:112` FALHA (200 em vez de 409); e, trocando o relógio do teste para 23:30, `AgendaLessonIT.statusAndFlags:119` FALHA (prova que o relógio fixo governa o resultado). Os testes provam o comportamento novo e não passam com a implementação antiga (`headClasses` e `List.of()`). O que cobrem: titular e auxiliar coerentes com `classCount` (comparado pelo tamanho da lista da listagem), turma inativa fora, ordem por nome, `role`, próximas aulas do titular, do auxiliar e do substituto, aula cancelada e passada fora, aula avulsa com `classId` nulo e título. Lacunas leves (SUGESTÃO): o corte `limit 10` não tem teste; a aula em que o titular foi substituído não aparece para ele (decisão implícita, sem teste); o isolamento entre organizações só confere a ausência de `upcomingClasses` na resposta, não o 404 explícito.

#### Clone limpo (rodada 2)

Clone novo de `origin/dev@4880550` no scratchpad, projeto Compose `ctcorerev5b`, só README e "Comandos".

| Passo | Resultado real |
|---|---|
| ambiente | `docker compose up -d --wait` sem `.env`: postgres, storage e mailbox saudáveis em 33 s; `prepare-runtime-db.sh` ok |
| back-end | `verify` (Java 21): BUILD SUCCESS em 9 min 42 s (parte em paralelo com o front); 134 + 367 testes, 0 falhas, 0 ignorados; "All coverage checks have been met" |
| front-end | `npm ci`, `lint`, `typecheck`, `build` exit 0 (2 min no conjunto); `test:cov` 76 arquivos, 684 testes, 94,04% de instruções, 96,88% de linhas (rodada 1: 682 e 96,87%); `e2e` 28 testes, 55 s |
| imagens | NÃO repetido: `.github/`, Dockerfiles e `infra/` (fora um comentário de seed) não mudaram desde a rodada 1; `image-backend` e `image-frontend` verdes no PR #283 |
| seed | NÃO repetido (só mudou um comentário SQL) |
| recomeço | `down -v` feito; novo `up` não repetido, mesmo motivo |

Limpeza: projeto `ctcorerev5b` removido com `down -v`, clone apagado, imagens iguais ao inventário inicial (sem `docker build`). Intocados: `competent_neumann`, `postgres:17`, volumes `ctcore_postgres-data` e `ctcore_storage-data`; um volume anônimo de hash já existia e não é meu.
Comparação: o `verify` ficou mais lento (9 min 42 s contra 6 min 11 s) porque rodei o front junto; é carga, não regressão.

#### CI e proteções

PR #283: `changes`, `security`, `backend`, `frontend`, `e2e`, `image-backend`, `image-frontend` e `ci-gate` em `SUCCESS`; squash em 2026-10-20T02:26:45Z. O título não tem `#N` porque é ajuste de revisão sem issue (o `AGENTS.md` não exige issue para ajuste). O `Publish` do commit estava em andamento na hora da consulta. Proteções de `dev` e `main`, `.github/`, `osv-scanner.toml`, `pom.xml` e `package.json`: sem mudança desde a rodada 1 (`git diff a94b1b6 dev --stat` vazio nesses caminhos); mantido da rodada 1.

#### Decisões D1 e D2

- D1 (recusar): aplicado em especificação (RN-MOD-06, RF-MOD-03), API, OpenAPI, MSW, tela (`UnitModalitiesSection` lista as turmas; teste de tela com axe) e teste de integração. PASSOU.
- D2 (aceitar no painel da aula; prova com alunos na Sprint 07): coerente com `AgendaLessonIT` CA4 e com RF-AGE-02 atualizado. O bullet do marco fica "PASSOU em parte, aceito pelo dono"; a prova com alunos reais segue para a matrícula (Sprint 07).

#### Conferência pendente do dono (não reprova)

As dez decisões de "Itens para as próximas sprints" estão no texto da especificação como foram propostas; falta o dono confirmar que são o que ele quer. Continua pendente, e não é falha do agente: o texto das issues #66 e #67 não cita `ModalityUsage.activeStudents`, RN-PRO-08 nem as recusas com aluno real (A3 da Sprint 04); sem isso a Sprint 07 pode esquecer os encaixes.

#### Achados remanescentes

Nenhum BLOQUEIA nem AJUSTAR. SUGESTÕES mantidas: as de R05-9 (cabeçalhos do nginx, branches `item/*` e `ajustes/liberar-sprint-05` no remoto, `REVOKE` de `PUBLIC` em `active_organization_ids()`, `notice_status` em português, sem versão na edição de aula) e duas novas: trava na recusa de A2; lacunas leves do teste de A6.

#### Ajustes propostos

Nenhum antes do PR. Para a Sprint 06, as SUGESTÕES acima, se o dono quiser.

#### Ações do dono

1. Olhar as telas (Ações 1 a 3 da rodada 1). A Agenda segue vazia depois do seed: siga o README, "Aulas na Agenda" (criar ou editar uma turma em Operação › Turmas).
2. Conferir as dez decisões de "Itens para as próximas sprints" e o texto das issues #66 e #67.
3. Abrir o PR de `dev` para `main` com o método "Merge" (nunca squash). Depois de revisar a sprint, dizer "sim" para liberar a Sprint 06.

#### Decisões do dono de 20/10/2026 (depois da rodada 2)

Decisões 1, 4, 5, 6, 8, 9 e 10 e as decisões menores da A1: aceitas como estão. Mudaram três, no PR `ajustes/decisoes-sprint-05`:

- **D3 (titular e auxiliar ensinam a modalidade da turma):** a RN-TUR-05 foi reescrita (`06`), e passou a existir a RN-PRO-09 em `05` (o professor só dá aula nas modalidades cadastradas; tirar uma modalidade em que é titular ou auxiliar de turma ativa é recusado). Servidor: criar ou editar turma com titular ou auxiliar que não ensina a modalidade dá 400 no campo (`headTeacherId` ou `assistantIds`), e a edição de turma antiga com titular que não ensina exige troca; `PUT /teachers/{id}` que tira a modalidade de quem é titular ou auxiliar de turma ativa dá 409 `TEACHER_MODALITY_IN_USE` com as turmas em `fields`. O substituto, a correção do professor da aula (RN-AGE-08) e a aula avulsa já exigiam que o professor ensine a modalidade (RN-PRO-06, D2 de #51); o que mudou foi tirar a exceção do titular da turma em `LessonWrite.checkTeacher`. O seed já cumpria a regra (cada professor ensina Jiu-Jitsu e o segundo também Judô); o `SeedDataIT` agora prova isso. Tela: a janela da turma só lista professores da unidade que ensinam a modalidade escolhida e limpa a escolha ao trocar; a tela de editar professor mostra as turmas que impedem a retirada. Contrato: `modalityIds` por professor em `GET /classes/options` e o 409 em `PUT /teachers/{id}`. Sem migração.
- **D7 (janela "esta e as seguintes"):** a regra da RN-AGE-04 continua; a janela de editar aula mostra, antes de confirmar, quantas aulas já alteradas à mão serão substituídas. Campo novo `followingEdited` em `GET /agenda/lessons/{id}` (OpenAPI e MSW atualizados; teste de isolamento entre organizações em `AgendaLessonIT.followingEdited`).
- **D2 (ficha do aluno do Professor): SÓ ESPECIFICAÇÃO.** "Ver ficha do aluno" é a quinta permissão do Professor por unidade (padrão desligada), em `02` (matriz, lista de permissões, Pronto quando), `00`, `01`, `08` (RF-ALU-03) e `15` (RF-INI-03). A implementação (quinta chave no vetor `teacher_permissions`, migração, tela de configurações da unidade, `PermissionTable`, OpenAPI, testes) NÃO entrou na Sprint 05 e vai para a Sprint 06, junto da ficha (#60); o encaixe está em `specs/backlog.md`, "Encaixes para as próximas sprints". O Professor continua sem lista nem painel de turma.


### Rodada 3 — APROVAR — 2026-10-20, estado c1aef916c9c5e3bc33841d298243b1edaf6520bc

#### Resumo e veredito

Escopo: só o PR #285 (`ajustes/decisoes-sprint-05`, squash `c1aef91` em `dev`, estado `MERGED`; `changes`, `security`, `backend`, `frontend`, `e2e`, `image-backend`, `image-frontend` e `ci-gate` em `SUCCESS`). Reconferi sem usar o relato de quem construiu: li o diff do commit e rodei testes num clone novo de `origin/dev@c1aef91`, com mutação. Não há BLOQUEIA nem AJUSTAR. Só SUGESTÕES novas. Significa "pode abrir o PR de `dev` para `main`" (método Merge), não "Sprint 06 liberada". A rodada 3 não repetiu o clone completo (`verify` e `test:cov` inteiros): o CI do PR cobre a suíte; rodei os testes que o PR tocou. Decisão do dono registrada: a lista simples do Professor mantém foto e faixa no Início (RF-INI-03); nada muda em `02` e `15`.

Pré-condições: `dev` igual a `origin/dev`; a única mudança na pasta é este relatório (sem commit); `docker info` ok; nenhum PR aberto contra `dev`; nenhum branch apagado.

#### (1) D3: titular e auxiliar ensinam a modalidade

| Ponto | Status | Evidência observada |
|---|---|---|
| 400 em `headTeacherId` e `assistantIds` | PASSOU | `TrainingClassWrite.activeTeachers` agora exige `teacher_modality` com a modalidade da turma; `checkReferences` devolve o campo certo nos dois. `TrainingClassWriteIT.teachersMustTeachTheModality` cobre criar (titular e auxiliar) e editar (titular e auxiliar) |
| turma antiga com titular que não ensina exige troca | PASSOU | mesmo teste: apaga `teacher_modality` do titular, `PUT` dá 400 `headTeacherId`; trocando o titular, 200. Tela: `headCannotTeach` (`ClassFormDialog.tsx:53,58`) distingue "inativo" de "não ensina" |
| 409 `TEACHER_MODALITY_IN_USE`, só turmas ativas | PASSOU | `TeacherWrite.refuseDroppedModalities`: `status = 'ACTIVE'`, titular ou auxiliar (`training_class_assistant`), só modalidades retiradas, ordem por `name_key, id`, erro antes do `update` (nada muda, sem auditoria). `TeacherWriteIT.removingTaughtModalityIsRefused`: lista `Judô Adulto`, `Judô Kids` (titular e auxiliar); turma `INACTIVE` fora; manter e acrescentar vale; Beto (titular de Judô Kids) trocando Judô por Boxe dá 409; desativando as turmas, 200; vínculo e auditoria intactos |
| tela: lista filtrada e limpa ao trocar | PASSOU | `ClassFormDialog.tsx`: `teachers = unitTeachers.filter(modalityIds.includes(modalityId))`; `pickModality` limpa titular e filtra auxiliares; `pickUnit` só mantém se a modalidade continua na unidade. Testes de tela e `classes.msw.test.ts` passam |
| tela do professor mostra as turmas | PASSOU | `TeacherFormDialog.tsx` lista os `fields` `class`; teste `RN-PRO-09` (com axe, como o resto do arquivo) passa |
| critério da aula (RN-AGE-08, avulsa, substituto) | PASSOU | `LessonWrite.checkTeacher` sem a exceção do titular, usado na avulsa (`create`) e na edição (`update`, inclusive correção de aula concluída e substituto). `AgendaLessonEditIT.headMustTeachTheModality`: titular edita (200), perde a modalidade, 422 em `teacherId` |
| casos de borda | PASSOU | professor sem modalidade: `coalesce(... '{}')` devolve `modalityIds: []`, nunca é opção e o servidor o recusa (o teste usa "Fora"); turma desativada não impede o 409 e já não é editável; auxiliar e titular juntos: os dois entram na consulta (`Judô Kids` tem Beto titular e Ana auxiliar); troca de modalidade da turma: o servidor valida com a modalidade nova |
| mutação | PASSOU | no clone, tirei o filtro de modalidade de `activeTeachers`: `TrainingClassWriteIT.teachersMustTeachTheModality` FALHA. O teste não passa sem a regra |

#### (2) D7: `followingEdited`

| Ponto | Status | Evidência observada |
|---|---|---|
| mesmo corte de `LessonWrite` | PASSOU | `Agenda.followingEdited`: mesma turma, `lesson_date > data da aula` e `Agenda.status(...) == SCHEDULED` com o mesmo `now` (relógio injetado, fuso da unidade); `LessonWrite.update(FOLLOWING)` usa `lesson_date > oldDate`, `id <> :id` e o mesmo `status`; a conta só soma as que têm `manually_edited` |
| contagem | PASSOU | `AgendaLessonIT.followingEdited`: 2 (entre 5 seguintes e 1 passada: a não editada, a cancelada e a de outra turma ficam fora); aula avulsa e a última da série dão 0; a mesma aula em outra organização dá 404. Mutação: tirei `and manually_edited`; o teste FALHA em `:65` |
| isolamento | PASSOU (ressalva) | o 404 entre organizações vem de `agenda.load` antes da conta; a consulta nova só usa o `class_id` já carregado e a RLS vale (sem `organization_id` explícito, ver R05-10) |
| OpenAPI e MSW | PASSOU | `followingEdited` em `LessonDetail` (`required`, `integer`, `minimum: 0`); `Agenda.LessonDetail` e `useLesson.ts` iguais; o MSW conta as edições seguintes não canceladas da turma. Teste de tela: sem aviso em "só esta", aviso com contagem em "esta e as seguintes"; singular e plural no `pt-BR.json`. `OpenApiContractTest` e `ContractRoutesTest` passam |

#### (3) D2: só especificação

PASSOU. `git show --stat c1aef91`: nenhuma migração, nenhum `PermissionTable`, nenhum arquivo de `features/settings`, `Permissions` nem `UnitSettings`; o vetor `teacher_permissions` segue com 4 posições (`V3`) e o backlog registra a implementação na Sprint 06 (#60). Reconciliação: `00` (cinco permissões, ficha desligada por padrão), `01` (quatro primeiras ligadas, ficha desligada), `02` (matriz com "lista simples ... sempre, sem chave" e "abrir a ficha ... T*"; lista de cinco permissões; critério de "Pronto quando"), `08` RF-ALU-03 e `15` RF-INI-03 (lista simples com foto e faixa; abrir a ficha depende da permissão), `06` RF-AGE-02 (lista simples sem permissão). Não achei contradição entre eles. Resta uma linha histórica em `99-de-para-codigos.md` (R05-12).

#### (4) Fixtures e e2e

PASSOU. Nenhum teste foi enfraquecido: os acréscimos são `insert into teacher_modality` nas fixtures (`AgendaBase.klass`, `LessonGenerationIT`, `TrainingClassWriteIT.teacherOf`), porque a regra nova exige a ligação; nenhuma asserção foi removida ou afrouxada (nos testes só há linhas acrescentadas, fora a troca de `Ana Souza` por `João Pereira` no e2e e a de `modalities[0]` por `find("Judô")` nos testes MSW, que apontam o mesmo alvo sob a regra nova). O e2e `turmas.spec.ts` escolhe Judô e João Pereira (que ensina Judô nos mocks); rodei: 3 testes `@critical` passam. `SeedDataIT` ganhou a asserção de que o seed já cumpre a regra. Testes direcionados no clone: front 26 arquivos, 242 testes, 0 falhas (classes, professores, agenda, mocks); back `TeacherWriteIT`, `TrainingClassWriteIT`, `AgendaLessonIT`, `AgendaLessonEditIT`, `LessonGenerationIT`, `SeedDataIT`, `OpenApiContractTest`, `ContractRoutesTest`: 0 falhas, 0 ignorados.

#### (5) Especificação e código dizem o mesmo

| Regra | Status | Observação |
|---|---|---|
| RN-TUR-05 | PASSOU | 400 com o campo; titular que deixou de ensinar exige troca; opções filtradas. Bate com `TrainingClassWrite` e a tela |
| RN-AGE-04 | PASSOU | o texto novo da janela ("quantas aulas já alteradas à mão") bate com `followingEdited` e `EditLessonDialog`; o corte "ainda não iniciadas" é o mesmo do `LessonWrite` |
| RN-AGE-08 | PASSOU | "professor ou substituto corrigido também ensina a modalidade" bate com `checkTeacher` (usado na correção de aula concluída) |
| RN-PRO-06 | PASSOU | continua "substituto de qualquer unidade que ensine a modalidade"; sem exceção de titular |
| RN-PRO-09 (nova) | PASSOU | bate com `refuseDroppedModalities` e com o OpenAPI (409 em `PUT /teachers/{id}`, enum e `fields` `class`) |

#### Achados novos

Todos SUGESTÃO; nenhum impede o PR.

- **R05-10 (API):** `Agenda.followingEdited` não filtra por `organization_id` (o `LessonWrite` filtra). A RLS cobre e o id vem de aula já carregada; para seguir "filtro no código e RLS", acrescentar `organization_id = :org`.
- **R05-11 (dados):** `refuseDroppedModalities` não trava a consulta de turmas ativas; criar uma turma no mesmo instante pode passar. Mesma ressalva da A2 (janela estreita, sem efeito financeiro).
- **R05-12 (especificação):** `99-de-para-codigos.md`, linha `G:RF-USU-04`, ainda diz "quatro permissões do Professor"; com a quinta (D2) fica desatualizada.
- **R05-13 (front):** ao editar turma antiga, o auxiliar que deixou de ensinar a modalidade sai da escolha sem aviso (o titular tem mensagem, `headCannotTeach`). Mostrar uma linha de aviso, se o dono quiser.
- **R05-14 (teste e regra):** não há teste que prove que `followingEdited` é igual ao que `update(FOLLOWING)` realmente substitui, e o MSW conta só as edições registradas na sessão (não olha aula já iniciada). Além disso, RN-PRO-09 só protege titular e auxiliar: um substituto já gravado em aula futura que depois deixa de ensinar a modalidade não é recusado nem listado. Decisão de regra para o dono, se quiser.

#### Clone e limpeza

Clone de `origin/dev@c1aef91` no scratchpad, só para testes direcionados e duas mutações (apagado ao fim). Docker: nenhum contêiner nem imagem de projeto criado; `docker images` igual ao de antes. Intocados os volumes `ctcore_postgres-data` e `ctcore_storage-data`.

#### Ações do dono

1. Olhar no navegador: Turmas, janela de turma ao trocar a modalidade (a lista de professores deve mudar e limpar a escolha); Professores, editar professor tirando uma modalidade com turma ativa (deve listar as turmas); Agenda, editar aula com "esta e as seguintes" depois de editar uma aula seguinte à mão (deve avisar a contagem).
2. Abrir o PR de `dev` para `main` com o método "Merge" (nunca squash). Depois de revisar a sprint, dizer "sim" para liberar a Sprint 06.

#### Decisão do dono sobre a R05-14 e a R05-12 (20/10/2026, depois da rodada 3)

- **R05-14: SIM, proteger o substituto já gravado em aula futura.** Em "esta e as seguintes" (RN-AGE-04), a aula seguinte que já tem substituto gravado mantém o professor e a marca de substituição; data e horário mudam como nas demais, e as demais recebem o professor da edição. Achado no código antes de escrever a regra: `substitute` só é gravado por uma edição, que também marca `manually_edited`, então a aula com substituto já contava em `followingEdited`; o que faltava era a regravação do professor em `LessonWrite.update` (`teacher_id` e `substitute` eram sobrescritos em todas as seguintes) e, por robustez, o contador contar também `substitute` sem `manually_edited`. Aplicado em `ajustes/r05-14`: `06` RN-AGE-04, `LessonWrite` (`Row.keepSubstitute`), `Agenda.followingEdited` (`manually_edited or substitute`), OpenAPI (descrição, sem campo novo), texto da janela em `pt-BR.json`; testes `AgendaLessonEditIT.followingKeepsSubstitutesAndCountMatches` (a contagem é igual às aulas já alteradas que a edição regrava ou preserva, no mesmo cenário) e `AgendaLessonIT.followingEdited`.
- **R05-12: corrigida.** `99-de-para-codigos.md`, linha `G:RF-USU-04`: "cinco permissões do Professor por unidade", com "ver ficha do aluno" desligada por padrão. As demais ocorrências de "quatro" em `00`, `01` e `02` já descrevem as quatro primeiras mais a quinta.
- **Achados remanescentes (só registrados, sem ação):** R05-10 (`followingEdited` sem `organization_id` explícito; a RLS cobre), R05-11 (recusa de retirar modalidade sem trava de concorrência, o mesmo ponto do A2) e R05-13 (auxiliar que deixou de ensinar a modalidade sai da escolha na edição da turma sem aviso). Seguem como SUGESTÃO para a Sprint 06 em diante.


### Rodada 4 — APROVAR — 2026-10-20, estado 5f4677c083c756a2d773d6441d7fe7439db9639e

#### Resumo e veredito

Escopo: só o PR #286 (`ajustes/r05-14`, squash `5f4677c` em `dev`, estado `MERGED`; `changes`, `security`, `backend`, `frontend`, `e2e`, `image-backend`, `image-frontend` e `ci-gate` em `SUCCESS`; `Publish` em `dev` verde). Reconferi pelo diff e por testes num clone novo de `5f4677c`, com cinco mutações. Não há BLOQUEIA nem erro no que o PR mudou. Veredito: **APROVAR** (pode abrir o PR de `dev` para `main` com "Merge"; não significa Sprint 06 liberada). Nesta rodada não rodei o clone completo (`verify` e `test:cov` inteiros): o CI do PR cobre a suíte; rodei os testes que o PR tocou. Pré-condições: `dev` igual a `origin/dev`; a única mudança na pasta é este relatório (sem commit); Docker 29.8.1 ativo; nenhum branch apagado, nenhum PR aberto por mim.

#### Pontos conferidos

| Ponto | Status | Evidência observada |
|---|---|---|
| substituto gravado em aula futura é preservado em "esta e as seguintes" | PASSOU | `LessonWrite.update`: `Row.keepSubstitute` vem de `substitute` na consulta das seguintes (a aula editada entra com `false`); o `update` usa `teacher_id = case when :keep then teacher_id else :teacher end` e o mesmo para `substitute`; data e horário mudam em todas, `manually_edited` e `original_*` seguem gravados. `AgendaLessonEditIT.followingKeepsSubstitutesAndCountMatches`: a aula com Beto (substituta) continua com Beto e `substitute`, as outras recebem Caio, canceladas, de outra turma e passadas ficam intactas |
| `followingEdited` conta `manually_edited` ou `substitute` | PASSOU | `Agenda.followingEdited`: `(manually_edited or substitute)`, mesmo corte (`lesson_date > data`, status `SCHEDULED`, mesmo `now`). `AgendaLessonIT.followingEdited`: 3 (inclui a com substituto sem `manually_edited`); a última da série dá 1 |
| a contagem é igual ao que a edição regrava ou preserva | PASSOU | o teste novo compara `followingEdited` (2) com o conjunto marcado (`edited`, `covered`) e com as aulas realmente tocadas (`original_date` não nulo) que estavam marcadas (2); a aula simples (`plain`) é regravada mas não conta, como a regra manda ("alteradas à mão") |
| RN-AGE-04 e código dizem o mesmo | PASSOU | `06` RN-AGE-04: professor vale para todas menos as com substituto gravado; data e horário mudam em todas; janela conta a aula com substituto como alterada à mão. Bate com `LessonWrite`, `Agenda.followingEdited`, OpenAPI (`PUT /agenda/lessons/{id}` e `followingEdited`, sem campo novo) e `pt-BR.json`/`LessonPanelPage.test.tsx` (texto "atingidas ... o substituto fica") |
| "cinco permissões" no de-para e demais ocorrências | PASSOU | `99`, `G:RF-USU-04`: "cinco permissões ... a quinta ... desligada por padrão". `grep` em `docs`, `frontend/src`, `backend/src`, `specs`: `00` ("Cinco permissões") e `01` ("As quatro primeiras ligadas ... ficha desligada") coerentes. Restam "quatro" em `specs/projeto.md:19`, `frontend/src/mocks/permissions.ts:4` e `PermissionsIT:73`; ver achados remanescentes |
| testes | PASSOU | no clone: `AgendaLessonEditIT` 8/8, `AgendaLessonIT` 6/6, `OpenApiContractTest` e `ContractRoutesTest` 7/7, 0 falhas |
| mutação | PASSOU | todas reprovam: (M1) `keep` sempre falso, `followingKeepsSubstitutesAndCountMatches:96` falha; (M2) `keep` sempre verdadeiro, 3 falhas (`states`, `thisLesson`, o teste novo); (M3) corte sem `substitute`, `AgendaLessonIT.followingEdited:67` falha (esperava 3); (M4) corte só `substitute`, os dois testes falham; (M5) seguintes sem filtro de status, `following` e o teste novo falham. Nenhuma mutação sobreviveu |

#### Achados novos

Nenhum BLOQUEIA ou AJUSTAR. Só o que segue, como SUGESTÃO.

#### Achados remanescentes (sem exigir ajuste)

- **R05-10, R05-11, R05-13:** continuam como na rodada 3 (SUGESTÃO).
- **R05-15 (especificação, SUGESTÃO):** `specs/projeto.md:19` ainda diz "quatro permissões ligáveis por unidade" para o Professor; com a quinta (D2) fica desatualizada. O comentário de `frontend/src/mocks/permissions.ts:4` e o nome do teste `PermissionsIT:73` ("as quatro permissões") são verdadeiros hoje (só quatro estão implementadas) e mudam com a Sprint 06 (#60).
- **R05-16 (teste, SUGESTÃO):** o MSW de `followingEdited` conta as edições da sessão e não olha o campo `substitute` nem aula já iniciada; o back-end é a referência, e as duas contagens coincidem porque toda substituição passa por uma edição. Sem ação.
- **R05-17 (CI, SUGESTÃO, fora deste PR):** a falha de `S3FileStorageIT.refusesExpiredAndTamperedUrls` no CI (já vista no #50) não é parte do #286 e não foi reproduzida aqui (não rodei esse teste); neste PR o `backend` ficou `SUCCESS`. Registrado como instabilidade conhecida, sem ajuste exigido.

#### Clone e limpeza

Clone de `origin/dev@5f4677c` no scratchpad, só para os testes direcionados e as cinco mutações (todas revertidas com `git checkout` no clone, que foi apagado ao fim). Nenhum contêiner nem imagem de projeto criado; os de Testcontainers saem sozinhos. Volumes `ctcore_postgres-data` e `ctcore_storage-data` intocados. Nenhuma alteração em código, especificação ou branches do repositório.

#### Ações do dono

1. Abrir o PR de `dev` para `main` com o método "Merge" (nunca squash). Depois de revisar a sprint, dizer "sim" para liberar a Sprint 06.
2. Opcional, no navegador: Agenda, editar uma aula já substituída com "esta e as seguintes" e conferir que o aviso diz "atingidas ... o substituto fica" e que a aula com substituto mantém o professor.
