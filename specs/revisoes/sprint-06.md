# Revisão da Sprint 06 · Planos, alunos e responsáveis

### Rodada 1 — AJUSTAR — 2026-10-20, estado 4124826b77f287901ae744f1788292cf7f53ec75

#### Resumo e veredito

A sprint entregou, em 9 itens (#54 a #62), o cadastro de aluno e responsáveis no servidor, planos (resumo, tabela e janela), a lista de alunos com selos, a ficha em painel lateral com a quinta permissão do Professor ("ver ficha do aluno"), a edição dos dados pessoais e o cartão de aniversariantes, com 4 migrações (V29 a V32) e a trilha de processo completa. O clone limpo passa (back-end 135 + 450 testes, 97,80% de linhas; front 811 testes, 97,01%; e2e 31; imagens e recomeço do ambiente ok). Não há BLOQUEIA. O resultado é `AJUSTAR` por dois ajustes que devem entrar antes do PR `dev`→`main`: a especificação não recebeu nenhuma das decisões que mudam comportamento (R06-1, repete R05-1) e as peças que a #58, #59 e #60 deixaram como encaixe não estão no texto das issues de destino (R06-3). A quinta chave do Professor (D2) está certa e testada, mas o lado "Professor positivo" da ficha só existe como encaixe e a "lista simples" do Professor não existe em lugar nenhum. O dono decide D1 a D4 e olha as telas (Ações 1 a 6).

#### Estado revisado

| | |
|---|---|
| `dev` | `4124826b77f287901ae744f1788292cf7f53ec75` (igual a `origin/dev`, pasta limpa antes do relatório) |
| `main` | `3f6e282e98d5fe604a7030de966dce1a675e7a20` (`Sprint 05 · Turmas e agenda (#288)`) |
| Ferramentas | Java 21.0.12 (o padrão da máquina é o 25; o clone rodou com o 21), Node 24.21.0, Docker 29.8.1 |
| Diff | `git diff origin/main...dev --stat`: 118 arquivos, +12238 −71 |
| PRs da sprint | #291 (#54), #292 (#55), #293 (#56), #294 (#57), #295 (#58), #296 (#59), #297 (#60), #298 (#61), #299 (#62); fora dos itens: #289 (libera a Sprint 06) e #290 (R05-17) |
| Pré-condições | `dev` = `origin/dev`; sem `item/*` local ou remoto; sem PR aberto contra `dev`; 9 de 9 issues do marco fechadas e "feito" no `specs/backlog.md`; nenhuma issue com `depende de você` no marco; `gh auth` ok; Docker ativo |

#### Trilha de processo

Todos os PRs têm um commit de squash em `dev` (nenhum merge commit entre `3f6e282` e `dev`), título `#N: <título da issue>`, `Closes #N` e `ci-gate` e os 7 checks em `SUCCESS` no PR que foi mesclado. Todas as tarefas estão marcadas, as seis seções existem e há ao menos uma rodada `OK`.

| Item | PR | Verificação | Entrega | Observação |
|---|---|---|---|---|
| #54 Cadastro de aluno e responsáveis (servidor) | #291, `ci-gate` verde | Rodada 1 CORRIGIR_ESPEC, Rodada 2 OK | sim | duas rodadas; D13 e D14 voltaram à especificação do item |
| #55 RF-PLA-01 Planos e benefícios | #292, verde | Rodada 1 OK | sim | |
| #56 RF-PLA-02 Tabela de planos | #293, verde | Rodada 1 OK | sim | |
| #57 RF-PLA-03 Janela de plano | #294, verde | Rodada 1 OK | sim | |
| #58 RF-ALU-01 Lista de alunos | #295, verde | Rodada 1 OK | sim | fechada com a lista incompleta (D1); regressão completa não reexecutada na verificação (vale a Construção; o CI do PR cobre) |
| #59 RF-ALU-02 Selos | #296, verde | Rodada 1 OK | sim | fechada com 3 de 6 selos (D1) |
| #60 RF-ALU-03 Ficha | #297, verde | Rodada 1 OK | sim | fechada com 3 de 6 abas com conteúdo (D1); inclui a quinta chave |
| #61 RF-ALU-04 Editar dados pessoais | #298, verde | Rodada 1 OK | sim | |
| #62 RF-ALU-06 Aniversariantes | #299, verde | Rodada 1 OK, Rodada 2 OK | sim | o 1º run do CI do PR reprovou (`StudentBirthdaysIT.clean`, run 39488210545), corrigido dentro do PR (run 39490002482 verde); a Rodada 2 ficou depois da seção Entrega, no mesmo PR |

Desvios de processo: nenhum merge sem verificação, nenhuma correção fora do PR do item, nenhum item com mais de três rodadas. Ressalva: #58, #59 e #60 foram fechados por `Closes` com parte do texto da issue ainda por entregar; isso está declarado nos arquivos dos itens, mas o `specs/backlog.md` mostra "feito" e as issues de destino não carregam o que falta (R06-3).

#### "Pronto quando"

**Do marco**

| Bullet | Status | Evidência |
|---|---|---|
| Um menor não é gravado sem responsável | PASSOU | `StudentRegistrationIT` (CA8, menor sem responsável: 400 no campo `guardians`, nada gravado); `StudentEditIT` (nascimento que torna menor exige responsável); `StudentGuardiansIT` (desvincular o último: 409 `MINOR_NEEDS_GUARDIAN`). Todos dentro do `verify` (450 ITs, 0 falhas) |
| Dois cadastros com o mesmo CPF não existem na mesma organização | PASSOU | índice único `student_org_cpf` (V29); `StudentRegistrationIT` CA4 (409 com id, nome e foto; outra organização cria); `StudentEdit.update` trata a corrida por `DuplicateKeyException`; `StudentSchemaIT` |
| O Administrador da unidade A vê, mas não altera, a modalidade do aluno na unidade B | NÃO CONFERIDO | o aluno ainda não tem modalidade (matrícula é a #63, Sprint 07). Hoje o alcance é a unidade de cadastro: o Administrador da A não vê o aluno cadastrado na B (404, `StudentRecordIT.adminReach`, `StudentListIT`). Não está no "Pronto quando" da #63. Ver D1 e A2 |
| O Professor não vê planos nem benefícios | PASSOU (benefícios: ainda sem rota) | Professor 403 em `PlanListIT`, `PlanSummaryIT` e `PlanWriteIT` (todas as rotas de plano); `PermissionTableTest` (29 linhas, `PLANS` e `BENEFITS` sem acesso do Professor); a ficha do Professor não tem campo de plano ou benefício (`StudentRecordsTest`). Rotas de benefício só existem na #64 (Sprint 07) |
| "Pronto quando" geral em cada item | ver tabela abaixo | |

**De todo item (plano de sprints)**

| Regra | Status | Evidência ou "não se aplica nesta sprint: motivo" |
|---|---|---|
| Regras citadas com teste | PASSOU em parte | todas são exercidas pelos critérios de aceite, mas 12 códigos não aparecem por nome em nenhum teste: RN-ALU-01, 02, 03, 05, 06, 07; RN-LGP-03; RN-ORG-13; RN-PLA-01, 02, 03; RN-RSP-01 (`grep` em `backend/src/test`, `frontend/src` e `frontend/e2e`). Repete R05-2; ver R06-5 |
| Cobertura mínima 80% e 100% nos pacotes críticos | PASSOU | back-end 97,80% de linhas (JaCoCo, `verify`: "All coverage checks have been met"); `isolation` 100%, `plan` 100%, `permission` 100%, `student` 96,53%. Front 97,01% de linhas. Pacotes `billing`, `payment`, `subscription`, `deactivation` e `src/features/{billing,payments,subscription,deactivation}` ainda não existem: não se aplica nesta sprint |
| Rotas novas com teste de isolamento entre organizações e entre perfis | PASSOU, com 1 lacuna | planos: `PlanListIT`, `PlanSummaryIT` CA5, `PlanWriteIT` CA10 e CA11 (RLS com `ctcore_app`); alunos: `StudentListIT`, `StudentRecordIT`, `StudentBirthdaysIT`, `StudentEditIT` CA9, `StudentGuardiansIT` CA20/21, `StudentRegistrationIT` CA20/21. Lacuna: `GET /portal/students` só testa perfis (`PortalStudentsIT` CA17), sem caso de outra organização (R06-7) |
| OpenAPI, implementação e MSW batem | PASSOU | `OpenApiContractTest` e `ContractRoutesTest` dentro do `verify`; testes MSW `students`, `studentRecord`, `studentEdit`, `birthdays`, `plans`, `unit-settings`; `TeacherPermissions.studentRecord` obrigatório no contrato (`docs/openapi.yaml:2940`) |
| Auditoria em toda escrita | PASSOU | `student.created`, `student.updated`, `student.photo_*`, `student.guardian_linked/financial_changed/unlinked/invite_resent`, `plan.created/updated/activated/deactivated`, `student.majority_notice` e `email_problem` (ator sistema), `unit.update` com `teacher.studentRecord`. Leituras (resumo, lista, ficha, histórico, aniversariantes) não gravam, por decisão registrada. `StudentRegistrationIT` CA22 prova que CPF, e-mail, telefone e endereço não vão para a auditoria; no `student.updated` só vão `name`, `birthDate`, `gender` e `referral` com valores, o resto como "alterado" |
| Valores em centavos, idempotência, lotes | PASSOU (centavos); idempotência e lotes não se aplicam | `plan.value_cents bigint` com `check`, histórico em centavos, front sem ponto flutuante (`reaisToCents`, `formatMoney`). Sem operação financeira nem lote nesta sprint |
| Quatro estados, 360 px e axe nos dois temas | PASSOU (por proxy) | testes de tela com 360 px e axe nos dois temas (`StudentsPage`, `StudentRecordPanel`, `StudentGuardiansTab`, `StudentEditDialog`, `BirthdaysCard`, `PlansPage`, `PlansTablePage`, `PlanFormDialog`); jsdom não mede contraste nem layout (Ações 1 a 6) |
| Textos só nos arquivos de tradução | PASSOU | `npm run lint` (regra de texto literal) exit 0 no clone; `grep` de acentuados em `features/students` e `features/plans` sem ocorrência fora de teste e `pt-BR.json`. Ressalva pré-existente: 10 telas exibem a mensagem de campo que vem do servidor em português (R06-12) |
| Recurso novo desligado por organização | PASSOU | a quinta chave nasce `false` (V32, padrão e linhas existentes; `UnitSettingsIT`, `UnitSettingsPage.test.tsx`). Cadastro e planos não são recurso liberado aos poucos (#54 D12) |
| Notas de versão | NÃO CONFERIDO | não há arquivo nem tela de notas no repositório (#57 D10, #58, #60 D9: Sprint 15/21). Nada visível a academias foi publicado (não há hospedagem até a Sprint 22, limitação aceita) |

#### Clone limpo

Clone de `origin/dev@4124826` no scratchpad, seguindo o README da raiz e "Comandos" do `AGENTS.md`. O compose fixa `name: ctcore`, então um clone usa os volumes do dono (`ctcore_postgres-data`, `ctcore_storage-data`); troquei para `-p ctcorerev` antes de rodar qualquer coisa (R06-8).

| Passo | Comando | Resultado real |
|---|---|---|
| ambiente | `docker compose -p ctcorerev up -d --wait` (de `infra/`, sem `.env`) | 3 serviços saudáveis em 27 s |
| migrações e papel | `bash infra/scripts/prepare-runtime-db.sh` e `spring-boot:run -Dspring-boot.run.profiles=local` | readiness `UP`; 32 migrações aplicadas com sucesso (V1 a V32) |
| seed (2x) | `bash infra/scripts/seed.sh` | 1ª e 2ª execução iguais: 6 alunos, 12 planos, 2 responsáveis, 3 organizações, 9 turmas |
| back-end | `./backend/mvnw -f backend/pom.xml verify` (Java 21) | BUILD SUCCESS em 8 min 30 s; 135 testes rápidos e 450 de integração, 0 falhas, 0 ignorados; "All coverage checks have been met"; 97,80% de linhas |
| front-end | `npm ci` (51 s), `lint`, `typecheck` (juntos 75 s), `test:cov`, `build`, `e2e` | lint, tipos e build exit 0; `test:cov` 89 arquivos, 811 testes, 97,01% de linhas (93,89% de instruções), 154 s; `e2e` 31 testes passaram em 1,1 min (inclui `minha-conta.spec.ts`, rodado sem carga paralela) |
| imagens | `docker build` do back-end (35 s) e do front-end (23 s) | back-end com usuário `uid=10001(ctcore)`, readiness `{"status":"UP"}` na porta 8081 com o banco; front-end `/` 200, `/alunos` 200, `/api/x` 404; `GET /api/v1/students` sem sessão redireciona para HTTPS (302, igual a `/teachers`; o 401 é provado nos ITs) |
| recomeço | `down -v` e subir de novo | 3 serviços saudáveis em 27 s, sem estado escondido |

Limpeza: contêineres `ctcorerev-*` removidos; volumes `ctcorerev_*` removidos com `down -v`; imagens `ctcorerev-backend` e `ctcorerev-frontend` removidas pelo nome (a lista de imagens ficou igual à de antes); os volumes `ctcore_postgres-data` e `ctcore_storage-data` do dono ficaram intocados (conferido com `docker volume ls`); o contêiner `competent_neumann` (`postgres:16.4-alpine`, de 18/10) não é meu e não foi tocado; clone apagado ao fim. O cache de build do Docker (7,9 GB) já existia e não é atribuível a esta revisão.

Comparação com a linha de base (Sprint 05, rodada 2: 134 + 367 testes, 684 no front a 96,88%, 28 e2e, `verify` em 9 min 42 s): back-end 135 + 450 (cresceu), cobertura 97,80% (Sprint 04: 97,53%), front 811 testes a 97,01% (melhorou), e2e 31 (cresceu), `verify` 8 min 30 s (melhorou). Nada piorou.

Passos do README que não bastaram: nenhum para o ambiente, o seed, o back-end, o front e as imagens. Faltou apenas o aviso do nome de projeto fixo (R06-8). O `up` sem `-p` num clone sobe em cima dos volumes do dono.

#### CI e proteções

| Item | Estado real | Evidência |
|---|---|---|
| `ci-gate` exigido em `dev` | sim | `gh api repos/.../branches/dev/protection`: `contexts: ["ci-gate"]`, `strict: false`, push forçado e exclusão bloqueados, sem exigir revisão (o agente mescla, como o `AGENTS.md` manda) |
| regra de `main` | sim | ruleset 25019105 `protege-main`, ativo, em `refs/heads/main`: `deletion`, `non_fast_forward`, `pull_request`, `required_status_checks` (sem proteção clássica de branch, só o ruleset) |
| métodos de merge do repositório | squash, merge e rebase permitidos | `gh api repos/...`; o método é escolhido em cada PR: squash em `dev`, "Merge" no PR para `main` (dono) |
| últimos runs | `Publish` em `dev`: 8 de 8 `success` (run 39491305071 é o do #299); `CI` dos PRs #54 a #62: todos verdes no PR mesclado | `gh run list`; falhas na sprint: 1 (run 39488210545, #62), corrigida no PR |
| workflows | sem mudança na sprint | `git log 3f6e282..dev -- .github`: 0 commits; `permissions:` de topo e ações fixadas por versão (`actions/checkout@v7.0.1` etc.) como na revisão anterior; mantido da Sprint 05 |
| exceções de vulnerabilidade | 2, ambas com motivo e validade | `osv-scanner.toml`: `GHSA-j9f9-w8pj-32f8` e `GHSA-pc63-qcmh-9cmg`, as duas até 2027-01-15. A data cai por volta da Sprint 12 ou 13: reavaliar antes. `.gitleaksignore`: 3 linhas |
| segredos e variáveis da publicação | não existem (nenhum nome listado por `gh secret list` e `gh variable list`) | limitação aceita: até a Sprint 22 não há hospedagem; a publicação "pula sem chaves" e `Publish` passa |

#### Achados

Ordenados por gravidade. Não há BLOQUEIA.

- **R06-1 · AJUSTAR · especificação** — local: `docs/especificacao/` (0 commits em `3f6e282..dev`), arquivos de item 54 a 62, seção "Decisões"
  - esperado: toda decisão que muda comportamento volta para a especificação antes do PR (mesma regra de R04-2 e R05-1).
  - observado: nenhum arquivo de `docs/especificacao/` mudou na sprint. Decisões "muda o comportamento" sem texto na especificação: #54 D2 (alcance do Administrador pela unidade de cadastro), D3 (duplicado mostra só nome e foto, de qualquer unidade), D4 (ao desligar o financeiro, o outro vira financeiro), D5 (convite do responsável sai na hora do vínculo), D13/D14; #55 D5, D6 e #56 D4 (`PLANS` vale para o resumo de benefícios; unidade fora do alcance dá lista vazia); #57 D1, D2 (unidade e modalidade do plano não mudam), D4, D6 (desativar e reativar como ações; reativar só sem homônimo); #58 D1, D2, D4, D5 (colunas por usuário só no navegador); #59 D1, D3 (menor sem financeiro é "Cadastro incompleto"), D4 (29/02 comemora em 28/02), D5 (um selo por vez no filtro); #60 D1, D4, D5 (a ficha do Professor omite nascimento, selos, papel financeiro e acesso), D6 (histórico sem antes e depois); #61 D3; #62 D1 (Coordenador sem cartão), D2, D5 ("hoje" é a data do servidor).
  - evidência: `git diff origin/main...dev --stat -- docs/especificacao` vazio; `git log 3f6e282..dev -- docs/especificacao` vazio.
  - efeito: a especificação passa a contradizer o produto em pontos que a Sprint 07 vai herdar (alcance do Administrador, selos, aniversário, ficha do Professor).
  - proposta: A1.

- **R06-2 · AJUSTAR · especificação** — local: `specs/projeto.md:19`; `frontend/src/mocks/permissions.ts:4`; `backend/src/test/java/br/com/ctcore/permission/PermissionsIT.java:73`
  - esperado: a pendência R05-15, que a decisão do dono de 20/10 mandou tratar "junto da quinta chave", resolvida.
  - observado: `projeto.md:19` ainda diz "quatro permissões ligáveis por unidade"; o comentário do mock diz "as quatro permissões"; o `@DisplayName` do teste diz "as quatro permissões do Professor" (o corpo do teste já percorre cinco chaves).
  - evidência: `grep -rn "quatro permiss"`.
  - efeito: texto contradiz código e especificação `02` (cinco chaves).
  - proposta: A1 (mesmo PR de documentação).

- **R06-3 · AJUSTAR · processo** — local: texto das issues #63, #65, #67, #70, #85, #108 a #110, #72 a #75; `specs/backlog.md`, "Encaixes para as próximas sprints"
  - esperado: cada peça que a #58, #59, #60 e #62 deixou como encaixe consta, com "Pronto quando", no texto da issue de destino; o backlog diz que o encaixe é do item de destino.
  - observado: das peças pedidas, só duas têm texto no destino: `ModalityUsage.activeStudents` e a RN-PRO-08, no fim de #63 e #66 (herdadas da Sprint 04 e 05). Faltam, e o `specs/backlog.md` também não as lista:
    - `StudentRecords.teacherReach` (positivo do Professor: aluno da turma abre, de outra turma não): ausente em #63 e em #67. Item 60 D4 sugeriu "acrescentar esse encaixe ao corpo da #63", e isso não foi feito. O mesmo vale para o filtro de matrícula ativa e o `teacherReach` de `GET /students/birthdays` (#62 D2 e D6) e para o handler MSW do Professor, que devolve `[]`.
    - o alcance do Administrador por modalidade na unidade (RN-ALU-07 completa; `Students.reachPredicate`): #63 não cita RN-ALU-07.
    - modalidades com turma, graduação, filtros por modalidade, turma e nível, filtros rápidos Ativos e Ex-alunos com contagem, e a "visão do professor" (`ProfessorAlunos.dc.html`, lista simples): em nenhuma issue de destino. #67 não cita a lista.
    - situação financeira e filtros rápidos Em atraso e Bloqueados: #72 (RF-COB-01) trata das cobranças, não da lista de alunos; Bloqueados é inativação (#109 a #111).
    - selos: "Termo pendente" só aparece como regra em #85 (RN-MAT-11, Sprint 10), não em #70 nem em #67, apesar de `StudentSeal.java` apontar "#70"; "Bolsista" não aparece em #65; "Novo" não aparece em #63 nem em #67.
    - ficha: Matrícula e modalidades (#109 e #110 citam só o canvas), Financeiro (#73 cita só o canvas), Graduações (#108 cita a aba). Situação no cabeçalho, botão Cobrar, próximo vencimento, pendências e as ações Trocar turma, Trocar plano, Conceder benefício, Transferir e Encerrar não têm "Pronto quando" próprio.
  - evidência: `gh issue view 63/65/67/70` e busca por `ficha`, `Encaixe`, `teacherReach`, `Termo pendente`, `Bolsista` nos corpos das issues abertas; `StudentRecords.java:73` (`teacherReach` devolve sempre `false`); item 60 D4.
  - efeito: o agente da Sprint 07 só enxerga o que está na issue; as peças somem e as issues #58, #59 e #60 ficam fechadas sem dono para o resto.
  - proposta: A2 (sem issue nova; bloco por issue).

- **R06-4 · AJUSTAR · dados** — local: `config/ClockConfig.java:13` (`Clock.systemUTC()`); `Students.java` (`sealParams`, `AGE`, `birthdays`, `list`), `StudentRecords`, `InviteRules.adult`
  - esperado: datas "exibidas no fuso da unidade" (`specs/projeto.md` §4); o `PlanWrite` já usa o fuso da unidade para `starts_on`.
  - observado: idade, `minor`, selo "Aniversário hoje" e o cartão de aniversariantes usam `LocalDate.now(clock)` com o relógio em UTC. De 21h a 23h59 em Brasília a "data de hoje" já é a de amanhã: o cartão e o selo mostram os aniversariantes de amanhã e a idade e a maioridade avançam à noite. #62 D5 registrou "relógio do servidor", mas não que ele é UTC.
  - evidência: `ClockConfig` e `grep "LocalDate.now(clock)"` (7 ocorrências); os testes usam `LocalDate.now(ZoneOffset.UTC)`, então não detectam.
  - efeito: defeito visível por 3 horas por dia, no piloto em `America/Sao_Paulo`. Sem vazamento de dado.
  - proposta: A3 (depois do PR).

- **R06-5 · AJUSTAR · teste** — local: testes de `student/`, `plan/` e telas
  - esperado: código RN citado na issue aparece no teste (como a Sprint 05 corrigiu em A3).
  - observado: 12 códigos sem citação por nome: RN-ALU-01, 02, 03, 05, 06, 07; RN-LGP-03; RN-ORG-13; RN-PLA-01, 02, 03; RN-RSP-01. Todos são exercidos por critérios de aceite (`StudentRegistrationIT.validates`, `lookup`, `cpfExists`; `PlanWriteIT`; `StudentGuardiansIT`), mas só se acham pelo arquivo do item. RN-ALU-07 é regra de alcance entre unidades, então vale o rigor de isolamento.
  - evidência: `grep -rl <código> backend/src/test frontend/src frontend/e2e`.
  - efeito: rastreabilidade (RNF-29) só por documento.
  - proposta: A4 (depois do PR). Nenhum dos 12 é de cobrança, pagamento, assinatura ou inativação.

- **R06-6 · AJUSTAR · teste** — local: `StudentSealsIT.java` (`clean`), `StudentListIT.java:36` (`clean`)
  - esperado: limpeza independente da ordem dos ITs.
  - observado: `StudentSealsIT.clean` apaga `account` sem apagar antes `account_invite`, `account_session`, `account_link_unit` e `account_link`, que é exatamente o defeito corrigido em `StudentBirthdaysIT` no #299; o item 59 já registrou a falha em seleção avulsa (5 erros com `SeedDataIT` e `StudentListIT`). `StudentListIT.clean` só apaga `student`, e quebra se sobrar `student_guardian`.
  - evidência: item 59 (Verificação, achados); item 62 (Entrega, "Correção da entrega"); leitura dos dois `clean`. No `verify` completo e no CI os dois passam (`StudentSealsIT` 5/5 nesta revisão).
  - efeito: instabilidade por ordem de execução; hoje não reprova o CI.
  - proposta: A4 (depois do PR, primeiro da Sprint 07 ou junto de R06-5).

- **R06-7 · AJUSTAR · teste** — local: `PortalStudentsIT` (CA17); `GET /api/v1/portal/students`
  - esperado: "toda rota nova tem teste de isolamento entre organizações e entre perfis" (`AGENTS.md`).
  - observado: o teste cobre perfis (outro responsável não vê, gestão 403, sem sessão 401), mas não há caso com aluno de outra organização. O código filtra por `s.organization_id = :org` e a RLS cobre, então não vi falha.
  - evidência: leitura de `PortalStudents.cards` e do teste.
  - efeito: regra do projeto sem o teste que a prova.
  - proposta: A4.

- **R06-8 · AJUSTAR · operação** — local: `infra/docker-compose.yml:2` (`name: ctcore`); README, "Ambiente local"; skill de revisão
  - esperado: um clone para revisão ou teste não toca os dados do dono.
  - observado: o nome de projeto fixo faz qualquer clone usar `ctcore_postgres-data` e `ctcore_storage-data`; o README ensina `down -v`, e seguir o roteiro de revisão ao pé da letra (`down -v` no clone) apagaria os dados do dono. Evitei com `-p ctcorerev`.
  - evidência: `docker volume ls` e `docker inspect` do 1º `up` mostraram o volume `ctcore_postgres-data` montado.
  - efeito: risco de perda de dados locais; a regra "nunca apagar esses volumes" só está no `AGENTS.md`.
  - proposta: A5 (depois do PR): aviso no README e orientação de `-p` (ou `COMPOSE_PROJECT_NAME`) para clones.

- **R06-9 · AJUSTAR · front** — local: `frontend/src/features/students/StudentRecordPanel.tsx`, `RecordGuardians.tsx`
  - esperado: a ficha do Professor (quando abrir, com #63) não oferece ações que ele não pode fazer.
  - observado: o painel mostra as abas Responsáveis e Histórico e, em Responsáveis, "Tornar financeiro", "Desvincular", "Reenviar convite" e "Vincular responsável" para qualquer perfil; para o Professor `financial` e `access` vêm ausentes, e os botões de escrita aparecem (o servidor recusa com 403, sem vazamento). O botão "Editar" já é condicionado a `STUDENT_MANAGE`.
  - evidência: leitura de `RecordGuardians.tsx` (botões sem condição de perfil) e de `StudentRecordPanel.tsx` (`canEdit` só no cabeçalho).
  - efeito: hoje nenhum efeito (o Professor não abre ficha); vira defeito quando a #63 ligar o alcance.
  - proposta: A2 (entra no bloco da #63).

- **R06-10 · SUGESTÃO · teste** — local: `frontend/e2e/minha-conta.spec.ts`, `frontend/playwright.config.ts`
  - o spec falhou sob carga paralela (o `verify` rodando junto; item 61) e passou sozinho. Nesta revisão rodou sem carga e passou; em CI não houve falha de e2e na sprint (a falha única foi de back-end, #62), e há `retries: 1` em CI. A espera é o `heading` "Bem-vindo ao ctcore" após o login, com o limite padrão de 5 s, enquanto o Vite compila a primeira página. Se quiser blindar: `expect: { timeout: 10_000 }` no `playwright.config.ts`. Fica para a Sprint 07 ou nunca; não é ajuste desta sprint.

- **R06-11 · SUGESTÃO · dados** — local: `infra/seed/13-planos.sql`: os 12 planos fictícios não têm linha em `plan_value_history` (a V31 só cria a linha inicial dos planos que já existiam na migração). Quando a cobrança (Sprint 08) ler o histórico, os planos do seed ficarão sem valor vigente. Acrescentar as linhas ao seed.

- **R06-12 · SUGESTÃO · API e front** — local: `StudentRegistration.exists` e `StudentEditDialog.tsx:172`: a recusa por duplicidade devolve id, nome e foto do aluno existente dentro de `fields[]` (`field: id`, `name`, `photoUrl`), uso improvisado do campo; 10 telas exibem `f.message` do servidor (texto em português vindo do Java, padrão que vem das sprints anteriores). Um campo `existing` no erro e códigos de mensagem resolvidos no `pt-BR.json` seriam mais limpos. Sem urgência.

- **R06-13 · SUGESTÃO · auditoria** — local: `StudentGuardians.java` (`student.guardian_*`) grava a auditoria com `unit_id` nulo, ao contrário de `student.updated` (unidade de cadastro). Quando a tela de auditoria (#114) filtrar por unidade para o Administrador, essas ações não aparecerão para ele. Gravar a unidade de cadastro do aluno.

- **R06-14 · SUGESTÃO · segurança** — mantidas da Sprint 05, sem mudança nesta sprint: nginx do front sem `Content-Security-Policy`, `nosniff`, `X-Frame-Options` e `Referrer-Policy` (R05-9; item #192), `active_organization_ids()` sem `REVOKE EXECUTE` de `PUBLIC`, trava de concorrência nas recusas de modalidade (R05-11). Novas, de leitura: `StudentEdit.apply` grava nome e nascimento em claro em `before`/`after` da auditoria (o endpoint de histórico os omite, D6); `MajorityNoticeJob` loga a exceção inteira (`LOG.error(..., e)`), que pode trazer parâmetros de SQL.

#### Camada técnica

Não acionei `/code-review`, `/security-review` nem `ponytail-audit` como skills nesta rodada; reproduzi o essencial lendo o diff com o roteiro de `references/checklist-sprint.md`. Lido por inteiro: `Students`, `StudentRecords`, `StudentController`, `StudentEdit`, `PermissionTable`, `Permissions`, `StudentGuardians.attach`, `PortalStudents`; migrações V29, V30, V31 e V32; `prepare-runtime-db.sh`; `StudentRecordsTest`, `StudentRecordIT.teacherKey`, `PermissionsIT.teacherFlagsAreIndependent`; `StudentRecordPanel`, `RecordGuardians`, `usePlans`. Não é varredura exaustiva das 12 mil linhas.

- Migrações: V32 troca a restrição de `teacher_permissions` para 5 posições, acrescenta `false` às linhas de 4 e muda o padrão; `unit_settings` já tinha RLS desde a V3 (política `organization_scope`), então a chave nova herda o isolamento. V29 e V30/V31: RLS ligada e forçada nas 5 tabelas novas (`student`, `guardian`, `student_guardian`, `plan`, `plan_value_history`), chaves estrangeiras compostas por organização, `DELETE` retirado do papel de execução em `student` e `student_guardian` (RN-ALU-08), `UPDATE`/`DELETE` retirados do histórico de valores, e o desvínculo por função `security definer` com `search_path` fixo e filtro pela organização da sessão. Nenhuma migração já aplicada foi editada.
- Quinta chave do Professor (D2): ver abaixo.
- Segurança de acesso: toda rota de aluno e de plano confere a permissão no servidor (`requireAccess` por `STUDENT_MANAGE`; ficha por `STUDENT_RECORD` para o Professor e `STUDENT_MANAGE` para a gestão; `PLANS`); alcance do Administrador fora da unidade dá o mesmo 404 de id inexistente; leitura de foto só por URL assinada; entradas de foto passam por `LogoImage.parse` e a organização do arquivo vem do contexto.
- Sem fornecedor em regra: nenhuma linha acrescentada na sprint cita fornecedor (`git diff -U0` e `grep`).

#### Foco desta revisão

**(1) D2, a quinta chave do Professor, e R05-15**

| Ponto | Status | Evidência |
|---|---|---|
| Vetor de 4 para 5 e migração | PASSOU | `V32__teacher_permission_student_record.sql`: remove a restrição antiga, `teacher_permissions || false` onde `cardinality = 4`, novo padrão `{true,true,true,true,false}`, nova restrição `cardinality = 5` sem nulos; aplicada em banco vazio (32 migrações) |
| RLS | PASSOU | a chave vive em `unit_settings`, com RLS da V3; `UnitSettingsIT` (isolamento entre organizações) no `verify` |
| `PermissionTable` e `Permissions` | PASSOU | `STUDENT_RECORD` com `ALL_FULL`, `UNIT_FULL`, `CLASS_FULL` e `TEACHER_FLAG` índice 4; `PermissionTableTest` (29 linhas e CSV) e `PermissionsIT.teacherFlagsAreIndependent` (cada chave liga sozinha, as outras viram `NONE`) e `teacherFlagPerUnit` (U1 ligada, U2 desligada) |
| Tela de configurações da unidade | PASSOU | `TEACHER_KEYS` com `studentRecord`; `UnitSettingsPage.test.tsx` (padrão desligado, o PUT leva `studentRecord: true`); `UnitSettingsIT` (400 sem a chave, gravação e auditoria `teacher.studentRecord`) |
| OpenAPI e MSW | PASSOU | `TeacherPermissions.studentRecord` obrigatório (`docs/openapi.yaml:2940-2946`); enum `Permission` com `STUDENT_RECORD`; handler com `studentRecord: false`; testes MSW |
| Isolamento entre perfis | PASSOU | `StudentRecordIT.profiles` (Aluno 403, Responsável 403, anônimo 401, Professor 403 no histórico); `teacherKey` (sem a chave: 403 até para id inexistente; com a chave: 404; aluno de outra organização: 404) |
| Isolamento entre organizações | PASSOU | `StudentRecordIT.record` e `history` (outra organização 404), RLS com `ctcore_app` |
| Chave desligada: o Professor não abre a ficha | PASSOU | 403, `StudentRecordIT.teacherKey` |
| Chave desligada: o Professor continua vendo a lista simples | NÃO PASSOU (ainda não existe) | a "lista simples" dos alunos da turma ou da aula do Professor não existe: `GET /students` dá 403 ao Professor (#58 D4), a turma e o painel da turma não são do Professor (Sprint 05 D2) e `teacherReach` devolve sempre `false`. A especificação `02` diz que ele continua vendo. Está só no item 58 D4 ("vem com #63/#67") e não nas issues (R06-3) |
| Chave ligada: a ficha nunca mostra valores, plano, benefício, CPF, endereço nem situação financeira | PASSOU hoje, sem trava para o futuro | `RecordView` não tem campo de valor, plano, benefício nem situação; `assemble` corta CPF, endereço, nascimento, selos, `financial` e `access` (`StudentRecordsTest.teacherCut`, também sem contatos); contatos e emergência só com "ver contatos". A ficha do Professor nunca abre hoje (404 sem matrícula). Risco: o corte é por omissão e o teste confere nomes de campos conhecidos; um campo novo em `RecordView` (plano, mensalidade, situação) passa sem teste. A #63 precisa de um teste que falhe com campo novo fora da lista permitida (A2) |
| `teacherReach` por unidade | encaixe | a chave vale por unidade (`PermissionsIT.teacherFlagPerUnit`), mas `StudentRecords.record` consulta `effective(...)` (ao menos uma unidade). O `teacherReach` real da #63 deve conferir a chave e "ver contatos" na unidade da turma que alcança o aluno, não em qualquer unidade (A2) |
| R05-15 | NÃO PASSOU | ainda diz "quatro" em `specs/projeto.md:19`, `mocks/permissions.ts:4` e `PermissionsIT.java:73` (R06-2) |

**(2) Encaixes** — resultado em R06-3 e em A2. Resumo para o dono: a #63 tem o encaixe de `ModalityUsage.activeStudents` (com "Pronto quando") e não tem o de `StudentRecords.teacherReach` nem o teste positivo do Professor. As demais peças (modalidades com turma, graduação, filtros rápidos, situação financeira, selos Termo pendente, Bolsista e Novo) não aparecem, com "Pronto quando", em nenhuma issue de destino. A proposta está em A2, em blocos prontos para colar, sem issue nova.

**(3) Regras da sprint** (#54 a #62)

- Dado pessoal ou financeiro exposto a perfil indevido: nenhum achado. Lista de alunos sem CPF, e-mail, telefone, endereço nem emergência (`StudentListIT.minimal`); busca por CPF e recusa por duplicidade só com id, nome e foto (RN-ALU-06); aniversariantes sem CPF, e-mail, endereço nem nascimento, com telefone só do WhatsApp e, para o Professor, só com "ver contatos"; portal só com id, nome e foto; histórico sem antes e depois; auditoria de criação sem CPF, e-mail, telefone nem endereço.
- Isolamento entre organizações: passou nas duas camadas (filtro por `organization_id` em todo SQL lido e RLS forçada), com teste por rota; uma lacuna de teste no portal (R06-7).
- Auditoria em toda escrita: passou (tabela acima); ver R06-13 e R06-14 (sugestões).
- Centavos: passou. Texto fixo fora do `pt-BR.json`: passou no front (lint e `grep`); ver R06-12.
- Casos de borda de aniversário: 29/02 em 28/02 nos anos não bissextos tem teste (`StudentSealsIT.birthday` e `StudentBirthdaysIT.today`, anos 2024 e 2025); o fuso é R06-4.
- Planos e benefícios: criar e editar por perfil e unidade, histórico só acrescenta, nome único por unidade e modalidade enquanto ativo (índice parcial), valor de 1 a 99.999.999 centavos (restrição de banco); benefícios e alunos usando devolvem zero até a Sprint 07 (D3 de #55; ponto único).

**(4) Instabilidade**

- `S3FileStorageIT.refusesExpiredAndTamperedUrls` (R05-17): tratado no #290 (`ad00f0a`): a URL passou a vencer em 4 s e o teste espera o vencimento a partir da emissão (5,5 s); só teste. Confirmado: o diff é só do teste e a classe passou nesta revisão (5 testes, 0 falhas, no `verify` completo, 8 min 30 s com carga leve). Sem outra falha de CI nesse teste nos 9 PRs da sprint.
- `minha-conta.spec.ts`: ver R06-10 (SUGESTÃO). Não merece ajuste agora.
- `StudentSealsIT.clean`: ver R06-6 (AJUSTAR, pode ir no começo da Sprint 07; hoje não reprova o CI).

**(5) Branches**

`git fetch --prune` removeu as referências remotas `origin/item/54` a `origin/item/62` (já apagadas no GitHub; `git ls-remote --heads origin` confirma). Existem hoje no remoto: `dev`, `main`, `ajustes/liberar-sprint-06` (PR #289, MERGED) e `ajustes/s3-teste-instavel` (PR #290, MERGED). As duas `ajustes/*` também existem como branches locais (`c4a1e0d` e `1b54110`) e aparecem "à frente" de `dev` só porque o merge foi por squash. Não apaguei nada; apagar é decisão do dono (Ação 7).

**(6) Telas** — lista em "Ações do dono".

#### Desvios e decisões herdados dos itens

| Item | Desvio ou decisão | Classe | Destino |
|---|---|---|---|
| #54 | D2 alcance do Administrador pela unidade de cadastro até a matrícula | muda requisito | ajustar especificação (R06-1); D1 do dono sobre o critério do marco |
| #54 | D3, D4, D5, D13, D14 (duplicado, financeiro automático, convite na hora, 400 e não 409, maioridade em UTC) | muda comportamento | ajustar especificação |
| #54 | D6 a D12 (tabela `guardian`, marca de maioridade, bounce simulado, telefone brasileiro) | técnico aceitável | registrar |
| #55, #56 | D5 e D6 (resumo respeita unidade; `PLANS` cobre o resumo de benefícios); D4 (lista vazia fora do alcance) | muda comportamento | ajustar especificação |
| #57 | D1, D2, D4, D6 (quem edita, unidade e modalidade imutáveis, "treinar em outra unidade", ativar e desativar) | muda comportamento | ajustar especificação |
| #57 | D10 sem arquivo de notas de versão no repositório | técnico | registrar; conferir na Sprint 15 |
| #58 | D1 lista incompleta, D2 alcance por unidade, D4 Professor 403, D5 colunas no navegador | muda comportamento | ajustar especificação e encaixes (R06-1, R06-3) |
| #58, #59, #60, #61, #62 | D7/D8 "canvas não aberto" | exceção que exige o dono | DONO: Ação 1 a 6 |
| #59 | D1 três de seis selos, D3 menor sem financeiro incompleto, D4 29/02 em 28/02, D5 um selo por vez | muda comportamento | ajustar especificação; D3 do dono (29/02) |
| #60 | D1 abas vazias, D4 Professor 404 até a #63, D5 nascimento e selos cortados, D6 histórico sem valores | muda comportamento | ajustar especificação; D4 do dono (corte) |
| #60 | D3 chave `studentRecord` (decisão do dono de 20/10, já na especificação) | já decidido | nada |
| #61 | D3 maioridade exige responsável, D1 unidade de cadastro não editável, D4 e-mail da conta só com a #67 | muda comportamento / técnico | ajustar especificação |
| #62 | D1 Coordenador sem cartão, D2 Professor pelo `teacherReach`, D5 "hoje" do servidor | muda comportamento | D2 do dono; ajustar especificação; R06-4 |
| #62 | sugestão de rodada: handler MSW do Professor devolve `[]` até a #63 | sugestão sem dono | dívida, vai no bloco da #63 (A2) |
| #58 a #62 | sugestões de rodada: ver `ageRange` repetindo a expressão de idade (#58), Professor positivo (#60) | sugestão sem dono | dívida |

#### Ajustes propostos

**A1 · Levar as decisões da Sprint 06 para a especificação e fechar R05-15** (resolve R06-1, R06-2) — antes do PR
- Tipo: ajuste de especificação.
- O que mudar e onde: `docs/especificacao/08-alunos-e-responsaveis.md` (RN-ALU-04 e RN-ALU-07: alcance provisório pela unidade de cadastro até a matrícula; RN-ALU-09: menor sem responsável financeiro é "Cadastro incompleto"; RF-ALU-02: selos entregues por etapa, 29/02 comemora em 28/02, filtro por um selo; RF-ALU-03: a ficha do Professor omite também nascimento, selos, papel financeiro e acesso, histórico sem valores; RF-ALU-06: só Administrador e Professor, "hoje" no fuso da unidade, contato do responsável para menor; RN-RSP: financeiro automático e convite na hora do vínculo), `07` (planos: `PLANS` cobre o resumo de benefícios, unidade e modalidade imutáveis, ativar e desativar, histórico de valor), `02` (alcance do resumo fora da unidade: vazio, não 404). `specs/projeto.md:19` ("cinco permissões ligáveis por unidade"), `frontend/src/mocks/permissions.ts:4` e o `@DisplayName` de `PermissionsIT.java:73` ("as cinco permissões").
- Como provar: `git diff` só de texto e do nome do teste; `./backend/mvnw -f backend/pom.xml test -Dtest=PermissionsIT` não é necessário (só muda o `@DisplayName`); o teste de contrato não muda.
- Tamanho: pequeno a médio (texto).

**A2 · Encaixes nas issues de destino** (resolve R06-3 e R06-9) — antes do PR (ou antes de liberar a Sprint 07)
- Tipo: ajuste de issue existente (acrescentar um bloco "Encaixe herdado da Sprint 06" ao fim do corpo, como já foi feito em #63 e #66); sem issue nova.
- Texto proposto:

  **#63 (Matrícula no servidor), acrescentar:**
  > ## Encaixe herdado da Sprint 06
  > - `StudentRecords.teacherReach` (hoje devolve sempre `false`): o Professor alcança o aluno que está em turma ativa dele (titular ou auxiliar, como o painel do professor da #43), com a chave "ver ficha do aluno" e a de "ver contatos" conferidas **na unidade da turma**, não em qualquer unidade. O mesmo ponto alimenta `GET /students/birthdays` (filtro de matrícula ativa).
  > - `Students.reachPredicate`, `Students.detail` e `Students.list` passam do alcance por unidade de cadastro para modalidade na unidade (RN-ALU-07 completa): a lista de uma unidade mostra só quem tem modalidade nela; o Administrador da unidade A vê a modalidade do aluno na B e não a altera.
  > - Pronto quando: [ ] Professor com a chave ligada abre a ficha de aluno da turma dele e recebe 404 para aluno de outra turma, 403 se a chave estiver desligada na unidade da turma (mesmo ligada em outra), com testes de integração; [ ] a ficha do Professor nunca traz valores em reais, plano, benefício, mensalidade, CPF, endereço nem situação financeira: teste que reprova qualquer campo de `RecordView` fora de uma lista permitida para o Professor; [ ] o Administrador da unidade A vê, mas não altera, a modalidade do aluno na unidade B (critério do marco da Sprint 06), com teste; [ ] aniversariantes do Professor trocam o handler MSW de `[]` pelo recorte real e o servidor aplica matrícula ativa.
  > - Lista de alunos (RF-ALU-01, canvas `Alunos`, `ProfessorAlunos`): colunas modalidades com turma e graduação (a graduação vem da #105/#106), filtros por modalidade, turma e nível, filtros rápidos Ativos e Ex-alunos com contagem, e a lista simples do Professor (nome e situação dos alunos da turma ou da aula, sem depender de chave, `02`). Pronto quando: [ ] cada coluna e cada filtro com teste de integração e de tela; [ ] Professor com a chave desligada continua vendo a lista simples.
  > - Selo "Novo" (até 30 dias da matrícula) e a ação "Concluir matrícula" criando a data de matrícula que o selo lê. Pronto quando: [ ] `StudentSeal` ganha `NEW`, na lista, na ficha e no filtro, com teste de 30 e 31 dias.
  > - Ficha (RF-ALU-03): abas Matrícula e modalidades e Resumo (modalidades com turma e faixa, próximo vencimento, pendências), ações Trocar turma, Trocar plano, Transferir e Encerrar, e a ficha do Professor sem as abas Responsáveis e Histórico nem os botões de escrita (hoje `RecordGuardians` os mostra a todo perfil).

  **#65 (Conceder benefício), acrescentar:** selo "Bolsista" (bolsa ativa, RN-BEN-05 e `99-de-para` RN-ALU-08) na lista, na ficha e no filtro por selo, e a ação "Conceder benefício" na ficha. Pronto quando: [ ] `StudentSeal` ganha `SCHOLARSHIP`; teste com benefício ativo, encerrado e vencido.

  **#67 (Nova matrícula), acrescentar:** a data de matrícula e a "unidade onde tem modalidade" passam a existir; se o aceite do termo (RN-MAT-12) já grava o que o selo "Termo pendente" lê, entregar o selo aqui; senão deixar para a #85. Pronto quando: [ ] decisão registrada no arquivo do item.

  **#85 (Aceite do termo no portal), acrescentar:** selo "Termo pendente" na lista, na ficha e no filtro (RN-MAT-11, já descrito no texto). Pronto quando: [ ] `StudentSeal` ganha `TERM_PENDING`; sai do selo ao aceitar; teste. (Corrigir também o comentário de `StudentSeal.java`, que aponta "#70".)

  **#72 a #75 e #109 a #111 (cobranças e inativação), acrescentar:** na lista de alunos, coluna e filtro por situação financeira e filtros rápidos Em atraso (Sprint 08) e Bloqueados (Sprint 12, #109 a #111); na ficha, a aba Financeiro, o botão Cobrar e a situação no cabeçalho (RF-ALU-03), sempre fora da ficha do Professor. Pronto quando: [ ] cada um com teste e com a ficha do Professor sem eles.
- Como provar: `gh issue view` mostra o bloco em cada issue; `specs/backlog.md`, seção "Encaixes", aponta para elas.
- Tamanho: pequeno (texto), mas mexe em 8 a 10 issues.

**A3 · Data de referência no fuso da unidade** (resolve R06-4) — depois do PR (item de correção no início da Sprint 07)
- Tipo: corrigir no item #59/#62.
- O que mudar: `Students` (`sealParams`, `AGE`, `birthdays`, `list`), `StudentRecords` e `InviteRules.adult` leem a data no fuso da unidade de cadastro (como `PlanWrite` faz para `starts_on`); idade e `minor` idem.
- Como provar: `StudentSealsIT` e `StudentBirthdaysIT` com relógio às 23h30 de Brasília (02h30 UTC do dia seguinte): o aniversariante é o de "hoje" em Brasília; teste do `minor` na véspera do 18º aniversário à noite.
- Tamanho: pequeno.

**A4 · Testes: citar o código e limpar o que depende de ordem** (resolve R06-5, R06-6, R06-7) — depois do PR
- Tipo: corrigir nos itens #54 a #62 (só teste).
- O que mudar: `@DisplayName` com os 12 códigos (RN-ALU-01, 02, 03, 05, 06, 07; RN-LGP-03; RN-ORG-13; RN-PLA-01, 02, 03; RN-RSP-01); `StudentSealsIT.clean` e `StudentListIT.clean` na ordem das tabelas dos demais ITs de aluno (`account_invite`, `account_session`, `account_link_unit`, `account_link`, `student_guardian`, `guardian`, `student`, `account`); caso de outra organização em `PortalStudentsIT`.
- Como provar: `verify -Dit.test='Student*IT,PortalStudentsIT,SeedDataIT'` com `SeedDataIT` antes e depois; `grep` dos códigos.
- Tamanho: pequeno.

**A5 · Aviso do nome de projeto do compose** (resolve R06-8) — depois do PR
- Tipo: ajuste de documentação (README, `infra/`) e, fora do repositório, da skill de revisão.
- O que mudar: README, "Ambiente local": um aviso de que o projeto se chama `ctcore` e que `down -v` apaga os volumes do dono; para clones e provas, `docker compose -p <nome>` ou `COMPOSE_PROJECT_NAME`.
- Como provar: ler o README; `docker compose -p x config` mostra volumes `x_*`.
- Tamanho: pequeno.

Ordem sugerida: A1, A2 (antes do PR); A3, A4, A5 (depois, na ordem; A3 e A4 no começo da Sprint 07).

Antes do PR `dev`→`main`: A1 e A2. Podem seguir na Sprint 07: A3, A4, A5 e as sugestões R06-10 a R06-14.

#### Decisões do dono

**D1 · O critério do marco "o Administrador da unidade A vê, mas não altera, a modalidade do aluno na unidade B" pode ser provado na #63 (Sprint 07), e não nesta sprint?** — recomendada: sim, aceitar e acrescentá-lo ao "Pronto quando" da #63 (A2) — motivo: o aluno só tem modalidade com a matrícula; hoje o alcance é a unidade de cadastro e o Administrador da A não vê o aluno da B (mais restritivo, nunca mais permissivo) — alternativa: liberar a leitura da ficha a todo Administrador da organização já agora, o que contraria RN-ALU-07 ("a lista de uma unidade mostra só quem tem modalidade nela").

**D2 · O Coordenador não vê o cartão "Aniversariantes de hoje" (#62 D1): manter?** — recomendada: manter e registrar em `08` (RF-ALU-06 cita só Administrador e Professor) — motivo: a especificação do Início do Coordenador (RF-INI-01) não o cita — alternativa: liberar ao Coordenador com todas as unidades.

**D3 · O aniversário de 29/02 é comemorado em 28/02 nos anos não bissextos (#59 D4): manter?** — recomendada: manter — motivo: a especificação não define; é a convenção adotada nos testes e trocar é uma constante — alternativa: 01/03.

**D4 · A ficha do Professor omite também data de nascimento (só a idade) e selos (#60 D5), além do que `08` já lista: manter?** — recomendada: manter e registrar em `08` — motivo: selos de cadastro e e-mail revelam situação de cobrança, que a especificação proíbe; é a leitura mais restritiva — alternativa: mostrar os selos que não tocam em cobrança (aniversário).

#### Ações do dono

Passos exatos, só o que uma pessoa faz. A parte visual não foi conferida contra o canvas (os agentes não o abriram: #55 D8, #58 D7, #59 D7, #60 D8):

1. `cd frontend && npm run dev`, abrir `http://localhost:5173/entrar`; entrar com `ana@exemplo.com` / `segredo123` (Coordenador, organização "Academia Alfa", dados simulados pelo MSW). Para cada tela abaixo olhar em tema claro e escuro, em 360 px e em 1280 px, e navegar só pelo teclado (Tab, Enter, Esc, setas nas abas), comparando com o canvas `Planos.dc.html`, `PlanosRegras.dc.html`, `Alunos.dc.html`, `GMAlunos.dc.html`, `AlunoDetalhes.dc.html`, `AlunoResponsaveis.dc.html`, `AlunoHistorico.dc.html`, `GMFicha.dc.html`. O link do menu recarrega a página e perde a sessão simulada: abrir os endereços pelo menu depois de entrar ou usar a navegação interna.
2. Financeiro › Planos (`/planos`): os dois cartões (planos e benefícios) e os números; Financeiro › Planos › tabela (`/planos/lista`): filtros, paginação, estado vazio, desativados, e a janela de plano (novo, editar, desativar e reativar; o bloco de impacto e as escolhas da RN-PLA-05; o valor em reais com vírgula). Em 360 px: sem rolagem horizontal da página.
3. Pessoas › Alunos (`/alunos`): lista, busca, filtros de unidade, faixa etária e selo, "Escolher colunas" (a escolha volta ao recarregar), selos como texto, foto ou iniciais; estados vazio, sem resultado e erro; em 360 px conferir se o canvas pede cartões (`GMAlunos`), porque a tela atual é tabela responsiva.
4. Ficha (`/alunos/:id`, clicar no nome): cabeçalho, WhatsApp, "Editar dados" (janela completa, foto, CPF e telefone com máscara), as 6 abas (3 delas vazias de propósito), Responsáveis (reenviar convite, tornar financeiro, desvincular com motivo, vincular), Histórico; tela cheia em 360 px, fechar volta para a lista.
5. Configurações › Unidade (`/configuracoes/unidade`): a opção "Ver ficha do aluno" desligada por padrão, ao lado das outras quatro, e o texto de ajuda.
6. Início com o cartão "Aniversariantes de hoje": o Coordenador não o vê (D2). Entrar com `varios@exemplo.com` / `segredo123` e escolher o vínculo de Administrador (Academia Alfa) ou de Professor (Academia Beta; o cartão aparece vazio até a #63). Conferir o botão WhatsApp e a mensagem para o aluno e para o responsável.
7. Limpeza do remoto, se quiser: apagar `ajustes/liberar-sprint-06` e `ajustes/s3-teste-instavel` (PR #289 e #290 já mesclados, conteúdo já em `dev`) e, no local, as duas branches de mesmo nome. Não apaguei nada.
8. Responder D1 a D4; com A1 e A2 feitos e um `APROVAR` numa rodada 2: abrir o PR de `dev` para `main` com o método "Merge" (nunca squash) e, depois de revisar a sprint, dizer "sim" para liberar a Sprint 07.

#### Limitações e o que não foi conferido

- Layout, contraste, 360 px sem rolagem, foco e teclado reais, e fidelidade ao canvas: só por proxy (jsdom, axe nos dois temas, `min-h-11`) e por teste; nenhum canvas foi aberto. Fica nas Ações 1 a 6. Não criei teste novo; o que fecharia a lacuna de 360 px e de contraste é um e2e Playwright `@critical` de largura 360 com axe real (`@axe-core/playwright`) em `/alunos`, na ficha e em `/planos/lista`; só proposta, sem criação.
- `/code-review`, `/security-review` e `ponytail-audit` não foram acionados como skills; leitura dirigida do diff (ver "Camada técnica").
- Notas de versão: não conferidas (não há onde registrá-las ainda).
- Publicação e hospedagem (`PUBLISH_*`, segredos): NÃO CONFERIDO (limitação aceita, até a Sprint 22).
- A matrícula, as cobranças, o desafio invisível e o e-mail real (bounce): fora desta sprint (encaixes; limitação aceita para conexões externas até as Sprints 22 e 23).
- Não rodei a suíte `e2e:full` (só `@critical`, que é o que o PR para `dev` exige); no PR para `main` o CI roda tudo.
- Segunda rodada do `verify` não repetida: o resultado é de uma execução.

#### Riscos para a próxima sprint

- A Sprint 07 herda o alcance do Administrador (`Students.reachPredicate`, `detail`, `list`), o `teacherReach` (ficha e aniversariantes), `ModalityUsage.activeStudents`, os selos que faltam, as colunas e filtros da lista e as abas da ficha. Sem A2, nada disso está no texto das issues.
- A `StudentSeal` só cresce com cada item; o enum e a ordem de `seals` são contrato (o front e o OpenAPI as leem).
- O cálculo de data em UTC (R06-4) piora com a #63 (vencimentos, "Novo" em 30 dias, marca de maioridade em UTC): corrigir antes de a matrícula e a cobrança dependerem dele.
- `plan_value_history` ainda não tem leitor; o seed não tem a linha inicial (R06-11). A cobrança (Sprint 08) lê "valor vigente na data".
- `osv-scanner.toml`: as duas exceções vencem em 2027-01-15, perto da Sprint 12 ou 13.
- `depende de você`: nenhuma issue do marco; as das Sprints 22 a 25 seguem sem efeito aqui.
- Pendências das revisões anteriores que continuam: R05-9 (cabeçalhos do nginx, item #192), R05-11 (trava de concorrência nas recusas de modalidade e de professor), R05-13, R05-16.

#### Decisões do dono de 20/10/2026 (depois da rodada 1)

- **D1:** sim. O critério do marco "o Administrador da unidade A vê, mas não altera, a modalidade do aluno na unidade B" é provado na #63, e o bullet entrou no "Pronto quando" dela.
- **D2:** manter o Coordenador sem o cartão de aniversariantes (RF-ALU-06, `08`).
- **D3:** manter o aniversário de 29/02 em 28/02 nos anos não bissextos (RF-ALU-02, `08`).
- **D4:** manter o corte de nascimento e selos na ficha do Professor (RF-ALU-03, `08`).
- **A1..A5 aplicados no PR `ajustes/revisao-sprint-06`:**
  - **A1:** `08` (RN-ALU-04, 07, 09, RN-RSP-05, 06, RF-ALU-01, 02, 03, 06), `07` (RN-PLA-06, RF-PLA-01, RF-PLA-03) e `02` (planos e resumo de benefícios; a lista simples do Professor só existe com a matrícula). R05-15 fechada: "cinco permissões" em `specs/projeto.md:19`, no comentário de `mocks/permissions.ts` e no `@DisplayName` de `PermissionsIT`.
  - **A2:** blocos "Encaixe herdado da Sprint 06" ao fim do corpo das issues #63, #65, #67, #70, #85, #72, #73 e #109, sem issue nova (a #63 já tinha o das Sprints 04 e 05). `specs/backlog.md` aponta para elas. Fica de fora a #110 (Inativar, reativar): nada da sprint cai nela.
  - **A3:** a data de hoje de idade, menor, selo "Aniversário hoje" e cartão de aniversariantes é a do fuso da unidade (`Students`: `TODAY` em SQL por `u.timezone`); teste `StudentSealsIT.referenceDateIsTheUnitDate` a 23h30 de Brasília e em Tóquio, visto falhar antes. Continuam em UTC (registrados no bloco da #63 como encaixe): `StudentRegistration.personal` (validação de nascimento no cadastro e na edição), `InviteService` e `MajorityNoticeJob`.
  - **A4:** código RN nos `@DisplayName` (RN-ALU-01, 02, 03, 05, 06, 07; RN-LGP-03; RN-ORG-13; RN-PLA-01, 02, 03; RN-RSP-01); `StudentSealsIT`, `StudentListIT` e `PortalStudentsIT` com a limpeza completa (convite, sessão, vínculo, responsável, aluno, conta), independentes da ordem; `PortalStudentsIT.otherOrganization` para `GET /portal/students`.
  - **A5:** aviso no README da raiz e no `backend/README.md` sobre o nome de projeto `ctcore` e os volumes compartilhados por qualquer clone (`-p <outro-nome>` antes de `down -v`).
- **Achados remanescentes (sem ação, para a Sprint 07 em diante):** R06-10 (limite do `expect` em `minha-conta.spec.ts`), R06-11 (seed de planos sem linha em `plan_value_history`), R06-12 (erro de duplicidade usa `fields` para id, nome e foto), R06-13 (auditoria `student.guardian_*` sem `unit_id`), R06-14 (cabeçalhos do nginx, `REVOKE` de `PUBLIC`, trava de concorrência, nome e nascimento em claro no `before/after` de `student.updated`, `MajorityNoticeJob` loga a exceção inteira) e as datas em UTC citadas em A3.



#### Itens para as próximas sprints

Mantidos sem issue nova (decisão do dono de 20/10/2026); os blocos "Encaixe herdado da Sprint 06" já estão no fim das issues #63, #65, #67, #70, #72, #73, #85 e #109 (`specs/backlog.md`, "Encaixes para as próximas sprints").

- **Ao começar a Sprint 07:** dividir a #63 (matrícula no servidor), que ficou grande para um item só: acumula quatro entregas independentes (matrícula e alcance por modalidade, lista de alunos com modalidades e filtros, selo Novo, abas da ficha, datas no fuso da unidade). Decisão do dono ao liberar a sprint.
- **Ao especificar a #65:** definir o selo "Bolsista" (S1). A RN-BEN-05 trata da bolsa de 100% e não o define; citar RF-ALU-02 e `99-de-para` (`G:RN-ALU-08`).
- **Ao especificar a #63:** corrigir o bloco (S2): ele diz "sem as abas Responsáveis e Histórico" para o Professor, mas só o Histórico é da gestão (`08`, RF-ALU-03: o Professor vê os responsáveis, sem papel financeiro nem acesso). Incluir no bloco "Datas" o `StudentGuardians.java:249` (S8), que ainda usa o UTC como `StudentRegistration.personal`, `InviteService` e `MajorityNoticeJob`.
- **Teste (S3):** teste de unidade de cadastro com uma organização de duas unidades (hoje cada organização do teste de A3 tem uma só, e trocar a unidade de cadastro não reprova `StudentSealsIT.referenceDateIsTheUnitDate`).
- **Ficha e Início (S5):** próximo vencimento e pendências no Resumo da ficha, e os itens "cadastro incompleto" e "e-mail com problema" no "Precisa de atenção", hoje sem "Pronto quando" em issue de destino; ficam na especificação (`08`, `15`) e entram ao especificar a ficha com cobrança (#73) e o Início (#123/#124).
- **Sugestões da rodada 1, sem ação (R06-10 a R06-14):** limite do `expect` em `minha-conta.spec.ts`; seed de planos sem linha em `plan_value_history`; erro de duplicidade usa `fields` para id, nome e foto; auditoria `student.guardian_*` sem `unit_id`; cabeçalhos do nginx, `REVOKE` de `PUBLIC` em `active_organization_ids()`, trava de concorrência nas recusas, nome e nascimento em claro no `before/after` de `student.updated` e `MajorityNoticeJob` logando a exceção inteira.

### Rodada 2 — APROVAR — 2026-10-20, estado 9a444fa7c50a7c54f59b5db776c1d2c9e240bb7a

#### Resumo e veredito

Escopo desta rodada: só o PR #300 (`ajustes/revisao-sprint-06`, squash `9a444fa` em `dev`) e o texto acrescentado às issues #63, #65, #67, #70, #72, #73, #85 e #109. Conferi de forma independente. Não há BLOQUEIA nem erro no que o PR e as issues mudaram que impeça o PR de `dev` para `main`. A1, A3, A4 e A5 passaram; A2 passou com duas lacunas e duas imprecisões de texto, registradas abaixo como sugestão (não exigem novo ajuste). Significa "pode abrir o PR de `dev` para `main`" (método Merge), não "Sprint 07 liberada".

Pré-condições: `dev` igual a `origin/dev` (`9a444fa`); a única mudança na pasta é este relatório (sem commit); nenhum PR aberto contra `dev`; branches `ajustes/*` mantidos, nada apagado. PR #300: `changes`, `security`, `backend`, `frontend`, `e2e`, `image-backend`, `image-frontend` e `ci-gate` em `SUCCESS`; 1 commit; squash em 2026-10-20T22:05:23Z; título sem `#N` (ajuste de revisão sem issue, como na Sprint 05).

#### Reconferência

| Ajuste | Status | Evidência observada |
|---|---|---|
| A1 · D2 (Coordenador sem o cartão) | PASSOU | `08` RF-ALU-06: "O Coordenador não tem este cartão". Código: `StudentRecords.birthdays` dá 403 a quem não é Administrador nem Professor; `App.tsx:60` só mostra `BirthdaysCard` a `ADMINISTRATOR` e `TEACHER` |
| A1 · D3 (29/02 em 28/02) | PASSOU | `08` RF-ALU-02 diz o mesmo que o SQL de `BIRTHDAY_TODAY` em `Students.java` (0229 vale em 0228 só se o ano não é bissexto); `StudentSealsIT.birthday` cobre 28/02, 29/02 e 01/03 |
| A1 · D4 (ficha do Professor) | PASSOU | `08` RF-ALU-03: sem nascimento (só idade), selos, papel financeiro e acesso dos responsáveis; Histórico só da gestão, sem valores antes e depois. `StudentRecords.assemble` corta exatamente esses campos; `history` exige `requireAccess` (gestão) e a consulta não traz antes e depois |
| A1 · demais decisões em `02`, `07`, `08` | PASSOU | `git diff HEAD~1 HEAD -- docs/especificacao`: RN-ALU-04, 07, 09; RN-RSP-05, 06; RN-PLA-06; RF-PLA-01, 03; RF-ALU-01, 02, 03, 06; linha de planos e lista simples em `02`. Batem com as decisões da rodada 1 |
| A1 · "cinco permissões" (R05-15) | PASSOU | `specs/projeto.md:19`, comentário de `frontend/src/mocks/permissions.ts:4` e `@DisplayName` de `PermissionsIT:73` dizem "cinco". `grep -rn "quatro permiss"` só acha registros históricos (`specs/itens/27`, `31`, relatórios) |
| A3 · fuso da unidade | PASSOU, com ressalva do teste (abaixo) | `Students.java`: `TODAY` em SQL por `u.timezone` (selo, idade, faixa) e `today(zone)` no Java (menor); `detail` lê o fuso da unidade de cadastro |
| A4 · 12 códigos RN nos testes | PASSOU | `grep -rl` em `backend/src/test`: RN-ALU-01, 02, 03, 05, 06, 07; RN-LGP-03; RN-ORG-13; RN-PLA-01, 02, 03; RN-RSP-01, cada um com 1 arquivo ou mais |
| A4 · limpezas sem ordem | PASSOU | `StudentSealsIT`, `StudentListIT` e `PortalStudentsIT` apagam convite, sessão, vínculo de unidade, vínculo, responsável, aluno e conta no `@AfterEach`. `verify` completo no clone passou com as três juntas, 0 falhas |
| A4 · `PortalStudentsIT.otherOrganization` | PASSOU | dois tenants, uma conta com filho em cada; o principal da organização A só vê o filho da A, o da B só o da B, e conta sem aluno na organização recebe `[]` |
| A5 · aviso do nome de projeto | PASSOU, com sugestão | README da raiz e `backend/README.md` dizem que `name: ctcore` fixa os volumes `ctcore_postgres-data` e `ctcore_storage-data` para todo clone e mandam usar `-p <outro-nome>`. Provei: `docker compose -p revctcore2 up -d --wait` criou `revctcore2_*` com os três serviços saudáveis; `COMPOSE_PROJECT_NAME=revenv` também sobrepõe o `name:`; `down -v` com `-p` removeu só os `revctcore2_*`; os `ctcore_*` ficaram intactos |

**A3, mutações** (clone temporário de `9a444fa`, Java 21, `-Dit.test=StudentSealsIT`). Linha de base: 6 testes, 0 falhas.

| Mutação | Resultado |
|---|---|
| M1: `TODAY` em SQL voltado para `'UTC'` | 1 falha: `referenceDateIsTheUnitDate` reprova |
| M3: `today(zone)` do Java voltado para UTC (marca de menor) | 1 falha: `referenceDateIsTheUnitDate` reprova |
| M2: fuso tirado de outra unidade da organização, em vez da unidade de cadastro | 0 falhas: o teste NÃO reprova. Cada tenant do teste tem uma só unidade, então "outra unidade da organização" é a mesma |

Conclusão: o teste protege contra o retorno ao UTC, mas não contra trocar a unidade de cadastro por outra (S3).

**O que ainda usa UTC.** Confirmado no código: `StudentRegistration.java:116` (`LocalDate.now(clock)` para `adult` no cadastro e na edição; `ClockConfig` devolve `Clock.systemUTC()`), `InviteService.java:82` (`now().toLocalDate()`), `MajorityNoticeJob.java:74-78` (cron e SQL em UTC). Também `StudentGuardians.java:249` (`LocalDate.now(clock)`). O bloco da #63 ("Datas") cita `StudentRegistration.personal`, `InviteService` e `MajorityNoticeJob`, com "Pronto quando" às 23h30 de Brasília; não cita `StudentGuardians:249` (S8). Efeito hoje: perto da meia-noite a lista (fuso da unidade) e o cadastro (UTC) podem discordar sobre adulto ou menor; é o que o bloco da #63 já manda corrigir.

#### Issues: corpo atual e histórico de edição

Li o corpo atual de cada uma (`gh api`) e o histórico de edição (GraphQL `userContentEdits`). Nas oito, a versão anterior é prefixo exato da atual: o bloco "Encaixe herdado da Sprint 06" foi acrescentado ao fim, o resto não mudou e não há duplicação (na #63 o bloco das Sprints 04 e 05 continua e o da Sprint 06 vem depois, sem repetir `activeStudents`).

Códigos citados nos blocos, conferidos em `docs/especificacao/`:

| Código | Existe | Sentido |
|---|---|---|
| RN-MAT-11 (#70, #85) | sim, `09`:34 | bate: termo aceito no portal, selo "Termo pendente" até o aceite |
| RN-MAT-12 (#67) | sim, `09`:35 | bate: o aceite guarda conta, versão, data, hora e IP |
| RN-BEN-05 (#65) | sim, `07`:31 | número existe, sentido só em parte: "bolsa de 100% não gera mensalidade"; não define o selo "Bolsista". O selo vem de RF-ALU-02 e do `99-de-para` (`G:RN-ALU-08`: bolsista é quem tem bolsa ativa). S1 |

Peças deixadas pela #58, #59 e #60 e o destino:

| Peça | Destino com "Pronto quando" |
|---|---|
| modalidades com turma, filtros por modalidade, turma e nível, Ativos, Ex-alunos, lista simples do Professor | #63 |
| graduação na lista | #63 cita, mas "vem da #105/#106" (S4) |
| Em atraso, situação financeira | #72 |
| Bloqueados, situação por modalidade | #109 |
| selo Novo | #63 |
| selo Bolsista | #65 |
| selo Termo pendente | #85 (e #70 corrige o comentário de `StudentSeal.java`) |
| `teacherReach` e alcance do Administrador (RN-ALU-07) | #63 |
| aba Matrícula e modalidades, ações da ficha | #63 |
| aba Financeiro, Cobrar, situação no cabeçalho | #73 e #109 |
| aba Graduações | #108 (já cobria a aba) |
| **próximo vencimento e pendências no Resumo da ficha** (#60 D1) | **nenhuma**: a proposta da rodada 1 os pôs na #63 e o texto aplicado os omitiu |
| **efeito financeiro de RN-ALU-09 e itens "cadastro incompleto" e "e-mail com problema" no "Precisa de atenção"** (#59 D6; RN-ALU-10) | **nenhuma**: a tabela de `15` os lista, mas #123 e #124 só citam RF-INI-02 e RF-INI-03 |

Nenhuma é regra de cobrança, pagamento, assinatura, inativação ou isolamento, e as duas estão no texto da especificação (`08`, `15`), então não se perdem; entram como sugestão (S5).

**A #63 ficou grande demais? Sugestão: sim, para um item só.** O texto original (RN-MAT-01, 02, 04, 15, 19, 20 e RN-ORG-12) já é um item cheio. Os blocos somam quatro entregas independentes: (1) alcance do Professor e do Administrador, que mexe em permissão e isolamento; (2) lista de alunos (colunas, filtros, lista simples); (3) selo Novo e "Concluir matrícula"; (4) abas e ações da ficha, mais a correção de datas. O corpo passou de 3.389 para 6.767 caracteres (inclui o bloco das Sprints 04 e 05). Pelo `AGENTS.md` é um PR por item, e a trava de alcance merece revisão própria. Proposta, sem criar issue: ao começar a Sprint 07, o `especificar` pode dividir em #63 (matrícula e alcance por modalidade, com `teacherReach` e o teste de RN-ALU-07) e um item "lista e ficha com a matrícula" (colunas, filtros, lista simples, Novo, abas, datas no fuso da unidade). O dono decide.

#### Clone limpo (rodada 2)

Clone novo de `origin/dev@9a444fa` no scratchpad; Java 21.0.12, Docker 29.8.1, Node 24.21.0.

| Passo | Resultado real |
|---|---|
| back-end | `verify`: BUILD SUCCESS em 4 min 46 s; 135 rápidos e 452 de integração, 0 falhas, 0 ignorados; "All coverage checks have been met" (rodada 1: 135 e 450; os 2 novos são `referenceDateIsTheUnitDate` e `otherOrganization`) |
| ambiente | com `-p revctcore2` e portas 25432, 28333, 21025 e 28025: postgres, storage e mailbox saudáveis |
| front-end, imagens, seed, recomeço | NÃO repetidos: o PR #300 só mudou um comentário em `frontend/src/mocks/permissions.ts`, dois READMEs e documentação; `frontend`, `e2e`, `image-backend` e `image-frontend` verdes no PR |

Limpeza: projeto `revctcore2` removido com `down -v` (volumes e rede só dele), clone apagado, nenhuma imagem construída. Intocados: `competent_neumann` (não é meu) e os volumes `ctcore_postgres-data` e `ctcore_storage-data`.

#### Achados remanescentes (sugestões; sem novo ajuste)

- **S1 (#65):** RN-BEN-05 não define o selo "Bolsista". Citar RF-ALU-02 e `99-de-para` (`G:RN-ALU-08`) e dizer "benefício ativo" (RN-BEN-02 também prevê desconto em reais).
- **S2 (#63):** "a ficha do Professor sem as abas Responsáveis e Histórico" contradiz `08` RF-ALU-03 e o código: o Professor vê os responsáveis (nome e, com "ver contatos", e-mail e telefone) sem papel financeiro e acesso; só o Histórico é da gestão. O "Pronto quando" ("sem botão de escrita") não depende da frase, mas ela pode induzir a remover a aba. Uma linha de edição.
- **S3 (teste):** `referenceDateIsTheUnitDate` não distingue a unidade de cadastro de outra unidade da organização (M2 sobrevive). Acrescentar uma segunda unidade com outro fuso e um aluno cadastrado em cada.
- **S4 (#63):** o "Pronto quando" pede teste de "cada coluna", mas a de graduação depende da #105/#106 (Sprint 12). Dizer que só entra com a graduação, ou passá-la para a #106.
- **S5 (issues):** pôr "próximo vencimento e pendências" do Resumo na #73 (ou #63) e os itens "cadastro incompleto" e "e-mail com problema" do "Precisa de atenção" na #123 e #124, cada um com "Pronto quando".
- **S6 (especificação):** em `08` RF-ALU-03 a frase do Professor ficou entre "Abas:" e a lista de abas ("Abas: Para o Professor a ficha omite..."). Mover para antes de "Cabeçalho" ou depois da lista.
- **S7 (README):** o aviso do nome de projeto não diz que as portas (5432, 8333, 1025, 8025 em `127.0.0.1`) são as mesmas com `-p`; com o ambiente do dono de pé, o clone também precisa de `POSTGRES_PORT`, `STORAGE_PORT`, `MAILPIT_SMTP_PORT` e `MAILPIT_WEB_PORT` (ou `infra/.env`). Provei com essas variáveis.
- **S8 (datas):** `StudentGuardians.java:249` também usa o UTC; incluir no bloco "Datas" da #63.
- Mantidos da rodada 1, sem ação: R06-10 a R06-14.

#### Decisões do dono

Nenhuma nova. Dividir ou não a #63 é do dono ao liberar a Sprint 07.

#### Ações do dono

1. Abrir o PR de `dev` para `main` com o método "Merge" (nunca squash).
2. Depois de revisar a sprint, dizer "sim" para liberar a Sprint 07.
3. As Ações 1 a 6 da rodada 1 (olhar as telas no navegador) continuam valendo; nada nesta rodada as altera.
