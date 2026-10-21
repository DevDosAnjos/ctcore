# Revisão da Sprint 07 · Matrícula e benefícios

### Rodada 1 — AJUSTAR — 2026-10-21, estado 219f40538fbad1009953f6e88117b148c387f05d

#### Resumo e veredito

A Sprint 07 entregou o servidor de matrícula (modalidades, plano, turma, transferência, retorno), a tabela, a concessão e o encerramento de benefícios, a Nova matrícula em etapas, os avisos de faixa etária e capacidade, o encerramento de modalidade e Configurações › Termos (issues #63 a #70, todas fechadas por PR de squash com `ci-gate` verde). O clone limpo passou inteiro (back-end 143 + 534 testes, front 961 testes, e2e 33, imagens, recomeço) e os três bullets específicos do marco passam. Não há BLOQUEIA. Há três AJUSTAR que valem antes do PR por serem só texto de issue e de especificação: RN-MAT-12 e RN-LGP-01 foram citadas na #67, não têm teste e a issue de destino (#85) não as carrega; a especificação não diz o que o código faz em três pontos (uma modalidade ativa por tipo, aviso de capacidade, aceite do Administrador na RN-BEN-08); e as Sprints 08 e 09 não carregam o que a 07 deixou para elas (primeira cobrança, plano agendado, cobranças em aberto no encerramento). O dono decide duas coisas (D1 e D2). Resultado: `AJUSTAR`.

#### Estado revisado

| | |
|---|---|
| `dev` | `219f40538fbad1009953f6e88117b148c387f05d` (igual a `origin/dev`, pasta limpa) |
| `main` | `c92ab992b794ccbc1a678fb56b8d90c0b2bb5a8a` (PR #302 da Sprint 06) |
| Data | 2026-10-21 |
| Ferramentas | Java 21.0.12 (build; o padrão da máquina é o 25.0.4), Node 24.21.0, Docker 29.8.1 |
| Diff | `git diff origin/main...dev`: 149 arquivos, 14.910 inserções, 225 remoções (back-end main: 39 arquivos, 3.024 linhas; front: 43 arquivos; `.github`, `pom.xml`, `package.json` e Dockerfiles: nenhuma mudança) |
| PRs da sprint | #304 (#63), #305 (#64), #306 (#65), #307 (#66), #308 (#67), #309 (#68), #310 (#69), #311 (#70); fora de item: #303 (libera a Sprint 07 no backlog) |

Pré-condições: `dev` limpa e igual à origem; sem PR aberto contra `dev`; sem branch `item/*`; `gh` válido (conta `DevDosAnjos`); Docker ativo. As 8 issues do marco (#63 a #70) estão fechadas e no `specs/backlog.md` como "feito"; nenhuma tem a etiqueta `depende de você`; nenhuma issue de outro marco foi fechada por PR da sprint. Sobra o branch `ajustes/liberar-sprint-07` (local e remoto, PR #303 já mesclado por squash): ver S1.

#### Trilha de processo

Todos os PRs têm base `dev`, título `#N: <título da issue>`, `Closes #N`, um commit de squash em `dev` (`git log c92ab99..dev`: 9 commits, nenhum merge commit) e 8 checks em `SUCCESS` (`gh pr view --json statusCheckRollup`). Os 8 arquivos de item têm as seis seções, "Construção", "Verificação" com ao menos uma rodada `OK`, "Entrega" e zero tarefas abertas.

| Item | PR | Verificação | Entrega | Observação |
|---|---|---|---|---|
| #63 Matrícula no servidor | #304, verde | 8 rodadas: CORRIGIR_CODIGO, OK, OK, CORRIGIR_CODIGO, OK, OK, OK, OK | sim | mais de três rodadas (duas de correção); dividido em seis partes por decisão do dono na revisão da Sprint 06 (D1); 26 tarefas |
| #64 Tabela de benefícios | #305, verde | Rodada 1 OK, 2 OK, 3 OK | sim | a Rodada 3 vem depois da "Entrega" ("Construção (continuação)"), no mesmo PR |
| #65 Conceder benefício | #306, verde | Rodada 1 OK | sim | |
| #66 Encerrar benefício | #307, verde | Rodada 1 OK, 2 OK | sim | |
| #67 Nova matrícula em etapas | #308, verde | Rodada 1 NAO_CONFERIDO, 2 OK | sim | RN-MAT-12 e RN-LGP-01 adiadas sem destino (R07-1) |
| #68 Avisos de faixa e capacidade | #309, verde | Rodada 1 NAO_CONFERIDO, 2 OK | sim | |
| #69 Encerrar modalidade | #310, verde | Rodada 1 CORRIGIR_CODIGO, 2 OK | sim | cobranças em aberto adiadas (R07-3) |
| #70 Configurações › Termos | #311, verde | Rodada 1 OK | sim | a descrição do PR relata 2 erros de `S3FileStorageIT` na máquina de verificação (armazenamento fora do ar); nesta revisão o `verify` completo passou |

Desvios de processo: nenhum merge sem verificação `OK`, nenhuma correção fora do PR do item, nenhum PR sem `ci-gate`. Ressalvas: #63 passou de três rodadas (autorizada por D1); "notas de versão" só aparecem em 3 das 8 descrições de PR (#306, #307, #310) porque o repositório não tem arquivo de notas (S4).

#### "Pronto quando"

**Do marco** (descrição do marco 8, `gh api repos/DevDosAnjos/ctcore/milestones/8`)

| Bullet | Status | Evidência |
|---|---|---|
| Não existe tela nem API que aceite dois benefícios ativos na mesma modalidade da matrícula | PASSOU | índice único parcial `benefit_active_modality` em `V34__benefit.sql:27` (vale para qualquer caminho, inclusive a conclusão da matrícula, que chama `BenefitWrite.grant` com `replaceExisting=false`); `BenefitGrantIT` (`replace` CA2: 409 sem confirmação, `concurrent` CA11: duas concessões simultâneas deixam um ativo) no `verify` verde |
| Concluir a matrícula deixa o aluno Ativo e envia o convite do portal | PASSOU, com ressalva | `EnrollmentConclusionIT.invite` (CA12): adulto recebe convite (`SENT`, e-mail `INVITE` gravado), `enrollment.status` `ACTIVE`; menor não recebe novo convite porque os responsáveis foram convidados ao serem ligados (`StudentGuardians.java:106`, D6 da #67); falha de envio não desfaz a matrícula (`FAILED`, mostrada na tela final; existem funções de reenvio em `InviteService`, não verifiquei a tela que as chama). Ressalva: aluno adulto sem e-mail termina `NO_EMAIL` sem convite; o texto do marco não diz o que fazer |
| Encerrar uma modalidade exige motivo da lista | PASSOU | `V37` (restrição com os 6 motivos), `EnrollmentEndIT` CA1 a CA4 (corpo inválido dá 400 e nada muda), `EndModalityDialog.test.tsx` |
| Todos os itens do "Pronto quando" geral valem para cada item desta sprint | ver tabela seguinte | |

Resultado do marco: 3 de 3 bullets específicos passaram; o bullet geral está na tabela abaixo (2 pontos com ressalva).

**De todo item (plano de sprints)**

| Regra | Status | Evidência ou "não se aplica nesta sprint: motivo" |
|---|---|---|
| Regras citadas com ao menos um teste | NÃO PASSOU (2 códigos) | Os testes citam "CA", não o código da regra, então varri o comportamento: RN-MAT-12 e RN-LGP-01 (citadas na #67) não têm teste nem implementação (D4 da #67 as manda para a #85, que não as cita: R07-1). Sem referência literal, mas com comportamento coberto: RN-BEN-05 (`BenefitGrantIT.grant`, bolsa de 100%), RN-BEN-08 (`replace`, D4), RN-MAT-08 (`invite`), RN-GRA-09 e RN-MAT-09 (`EnrollmentConclusionIT.origin`, `originRules`). RN-BEN-09 não tem comportamento próprio (é benefício comum). S2 |
| Cobertura mínima 80% e 100% nos pacotes críticos | PASSOU | back-end 98,06% de linhas (6270/6394), "All coverage checks have been met"; pacotes `billing`, `payment`, `subscription`, `deactivation`, `isolation`: 0 linhas não cobertas (`isolation`: 64 cobertas, os demais não existem ainda); front 96,57% de linhas (92,89% de instruções), `test:cov` exit 0; `src/features/{billing,payments,subscription,deactivation}` não existem ainda |
| Rotas novas com teste de isolamento entre organizações e entre perfis | PASSOU | `BenefitGrantIT.permissions` e `isolation`, `BenefitEndIT`, `BenefitListIT`, `EnrollmentIT`, `EnrollmentChangesIT`, `EnrollmentConclusionIT.profiles` e `isolation`, `ClassWarningsIT` CA7, `EnrollmentEndIT` CA5 e CA6, `StudentSimpleListIT`, `TermsIT` CA5 e CA6; RLS forçada e política `organization_scope` nas 8 tabelas novas (V33, V34, V36, V38), conferida nas migrações |
| OpenAPI, implementação e MSW batendo | PASSOU | `OpenApiContractTest` e `ContractRoutesTest` dentro do `verify`; `docs/openapi.yaml` +772 linhas; `enrollment-wizard.msw.test.ts` e demais testes de handler no `test:cov` |
| Auditoria em toda escrita | PASSOU | `benefit.granted` e `benefit.ended` (`BenefitWrite`), `terms.published` (sem o texto), `student.origin_proof_uploaded`, encerramento de modalidade sem a observação; testes de auditoria nos ITs; nenhum campo de senha, token ou cartão |
| Quatro estados, 360 px e axe nos dois temas | PASSOU por proxy; NÃO CONFERIDO em navegador real | as 10 telas e janelas novas têm teste com axe nos dois temas e a 360 px (jsdom); jsdom não mede contraste real nem rolagem horizontal real: ver "Ações do dono" |
| Textos só nos arquivos de tradução | PASSOU | `npm run lint` (regra de texto literal) exit 0; `pt-BR.json` +363 linhas; nenhum rótulo literal no diff |
| Recurso novo desligado por organização | não se aplica nesta sprint | a sprint não tem recurso liberado aos poucos (nenhum item pede) |
| Notas de versão | NÃO CONFERIDO (sem arquivo) | o repositório não tem arquivo de notas; os itens dizem "só se existir" e 3 de 8 descrições de PR trazem a linha (S4) |

#### Clone limpo

Clone de `origin/dev@219f405` no scratchpad. O compose fixa `name: ctcore` e as portas padrão estavam ocupadas por um projeto que não é meu (`revctcore7-*`), então usei `-p revs07` e as variáveis `POSTGRES_PORT=25432`, `STORAGE_PORT=28333`, `MAILPIT_SMTP_PORT=21025`, `MAILPIT_WEB_PORT=28025`, mais `COMPOSE_PROJECT_NAME=revs07` para os scripts (que chamam `docker compose -f` sem `-p`).

| Passo | Comando | Resultado real |
|---|---|---|
| ambiente | `docker compose -p revs07 up -d --wait` (de `infra/`, sem `.env`) | 3 serviços saudáveis em 22 s |
| migrações e papel | `bash infra/scripts/prepare-runtime-db.sh` e `spring-boot:run -Dspring-boot.run.profiles=local` | readiness `{"status":"UP"}`; "now at version v38" (38 migrações aplicadas) |
| seed (2x, mais uma 3ª para contar) | `bash infra/scripts/seed.sh` | saída 0 nas três; 17 arquivos de seed; 6 alunos, 12 planos, 5 benefícios, 4 matrículas, 3 organizações; total de linhas das tabelas: 300 antes e 300 depois da 3ª execução |
| back-end | `./backend/mvnw -f backend/pom.xml verify` (Java 21) | BUILD SUCCESS em 10 min 21 s; 143 testes rápidos e 534 de integração, 0 falhas, 0 ignorados; "All coverage checks have been met"; 98,06% de linhas |
| front-end | `npm ci` (28 s), `lint` e `typecheck` (60 s), `build` (26 s), `test:cov`, `e2e` | lint, tipos e build exit 0; `test:cov` 108 arquivos, 961 testes, 96,57% de linhas (92,89% de instruções), 3 min 45 s; `e2e` 33 testes passaram em 1,1 min; `npm ci`: 0 vulnerabilidades |
| imagens | `docker build` do back-end (44 s) e do front-end (57 s) | back-end `uid=10001(ctcore)`, readiness `UP` na porta 8081 com o banco (precisa de `TWO_FACTOR_ENCRYPTION_KEY`, como o README manda), `/actuator/health` na 8080 dá 404, `GET /api/v1/students` sem sessão dá 302 (HTTPS; o 401 é provado nos ITs); front-end `/` 200, `/alunos` 200, `/api/x` 404 |
| recomeço | `down -v` e subir de novo | 3 serviços saudáveis em 27 s, sem estado escondido |

Limpeza: contêineres `revs07-*` e `revs07-be/fe` removidos, rede `revs07net` removida, volumes `revs07_*` removidos com `down -v`, imagens `revs07-backend` e `revs07-frontend` removidas pelo nome (lista de imagens igual à de antes, conferida com `docker images -q`), processos Java do `spring-boot:run` encerrados. Intocados: `revctcore7-*`, `competent_neumann`, os volumes `ctcore_*` e `revctcore7_*`. Clone apagado ao fim.

Comparação com a linha de base (Sprint 06, rodada 2): back-end 135 + 450 passou a 143 + 534 (cresceu), cobertura 97,80% passou a 98,06% (melhorou), `verify` 8 min 30 s passou a 10 min 21 s (piorou 22%; nesta rodada rodei `npm ci`, `lint` e `build` em paralelo ao final do `verify`, então a medição não é limpa, e a suíte cresceu 13%); front 811 testes a 97,01% passou a 961 testes a 96,57% de linhas (instruções 93,89% para 92,89%): a cobertura caiu 0,44 ponto, ainda longe do mínimo (S3); e2e 31 passou a 33.

Passos do README que não bastaram: o README da raiz diz "Antes de iniciar o back-end, execute `prepare-runtime-db.sh`", mas não diz que `seed.sh` e `prepare-runtime-db.sh` usam `docker compose -f` sem `-p` (com o ambiente do dono de pé sob outro nome, eles falam com o projeto `ctcore`), então para isolar um clone é preciso `COMPOSE_PROJECT_NAME` (S5, já era o S7 da Sprint 06 em outra forma). Nenhum outro.

#### CI e proteções

| Item | Estado real | Evidência |
|---|---|---|
| `ci-gate` exigido em `dev` | sim | `gh api repos/DevDosAnjos/ctcore/branches/dev/protection`: `contexts: ["ci-gate"]`, `strict: false`, push forçado e exclusão bloqueados, sem exigir revisão (o agente mescla) |
| regra de `main` | sim | ruleset 25019105 `protege-main`, ativo |
| últimos runs | `Publish` em `dev`: 9 de 9 `success` (run 39661070676 é o do #311); `CI` verde nos 8 PRs (8 checks `SUCCESS` cada) | `gh run list --branch dev`, `gh pr view` |
| workflows | sem mudança na sprint | `git diff origin/main...dev --stat -- .github`: vazio; `permissions:` e ações fixadas como na Sprint 06 (mantido) |
| exceções de vulnerabilidade | 2, ambas com motivo e validade | `osv-scanner.toml`: `GHSA-j9f9-w8pj-32f8` e `GHSA-pc63-qcmh-9cmg`, até 2027-01-15 (reavaliar até a Sprint 12) |
| dependências | nenhuma mudança | `pom.xml`, `package.json`, lock e Dockerfiles fora do diff |
| segredos e variáveis da publicação | não existem (`gh secret list` e `gh variable list` vazios) | limitação aceita: até a Sprint 22 não há hospedagem |

#### Achados

- **R07-1 · AJUSTAR · teste/processo** — local: issue #67, `specs/itens/67-nova-matricula-em-etapas.md` D4, issue #85
  - esperado: "Todo código de regra citado na issue tem ao menos um teste" (AGENTS.md); regra adiada tem de aparecer, com "Pronto quando", na issue que a recebe.
  - observado: a #67 cita RN-MAT-12 e RN-LGP-01 (aceite com conta, versão, data, hora e IP; uso de imagem em caixa separada) e foi fechada sem teste nem código para elas. A D4 manda as duas para a #85, mas o corpo da #85 cita só RN-POR-03, RN-MAT-11 e RN-MAT-13. `gh issue list` com busca por "RN-LGP-01|RN-MAT-12" acha só a #67.
  - evidência: `gh issue list --state all --json number,body` filtrado: #67; `grep` nos testes: nenhuma ocorrência.
  - efeito: a regra de LGPD do aceite pode chegar à Sprint 10 sem dono; o Pronto quando da #67 diz o contrário do que foi feito.
  - proposta: A1.
- **R07-2 · AJUSTAR · especificação** — local: `docs/especificacao/09-matricula.md` RN-MAT-01 e RN-MAT-17; `06-turmas-e-agenda.md` RN-TUR-02; `07-planos-e-beneficios.md` RN-BEN-08
  - esperado: "Mudou uma regra, muda primeiro a especificação" (AGENTS.md).
  - observado: só `08` RN-ALU-07 foi atualizada (D11 da #63). Seguem sem ajuste decisões marcadas "muda o comportamento": #63 D3 (no máximo uma modalidade ativa de cada tipo por aluno, índice `enrollment_modality_active`; a RN-MAT-01 diz "várias modalidades e unidades"); #68 D1 (avisa quando esta matrícula faria a turma passar da capacidade; a RN-TUR-02 diz "acima dela"); #65 D4 (o "Administrador escolhe qual fica" da RN-BEN-08 virou a confirmação da substituição); #69 D2 (RN-MAT-17: a decisão por cobrança ainda não existe); #67 D3 (concluir sem criar cobrança).
  - evidência: `git diff origin/main...dev -- docs/especificacao`: 1 arquivo, 2 linhas.
  - efeito: quem ler a especificação espera comportamento que o código não tem; D3 da #63 recusa um cenário que a especificação permite (mesma modalidade em duas unidades).
  - proposta: A2 e D2.
- **R07-3 · AJUSTAR · especificação/issues** — local: issues #71, #73, #84 (Sprints 08 e 09)
  - esperado: o que a Sprint 07 deixou para depois está, com "Pronto quando", na issue de destino (padrão do R06-3, aceito pelo dono em 20/10).
  - observado: nenhuma issue de destino carrega: a leitura de `enrollment_start` (taxa, isenção e data da segunda cobrança, V36) para criar a primeira cobrança e liberar "Registrar pagamento agora" (desabilitado em `StepDone.tsx`, D3 da #67); a aplicação de `next_plan_id` e o limpar depois (D10 da #63); o benefício valendo "a partir da próxima cobrança" (RN-BEN-04); a lista `openCharges` do `end-preview` e a decisão por cobrança com motivo, só do Administrador (D2 da #69); o débito antigo no retorno do aluno (D5 da #63) e a taxa de matrícula já cobrada (D7 da #67). A #84 cita RN-NOT-04 (que inclui "benefício alto"), mas sem um "Pronto quando" para o disparo da RN-BEN-06.
  - evidência: `gh issue view 71/73/84` e busca por `enrollment_start|openCharges|next_plan_id|nextPlan|end-preview` nas 400 issues: nenhum resultado fora da #67 e das #63 a #70.
  - efeito: a Sprint 08 pode entregar cobrança sem ligar a matrícula, e o fluxo de matrícula segue incompleto no uso.
  - proposta: A3.
- **R07-4 · DONO · operação** — ver D1.
- **R07-5 · AJUSTAR · segurança/dados (baixo)** — local: `OriginProof.store`, D8 da #67
  - esperado: dado pessoal enviado e não usado não fica sem dono.
  - observado: o comprovante da graduação é enviado antes da conclusão; se a matrícula não conclui, o arquivo fica no armazenamento, sem referência e sem limpeza. Registrado como "sem limpeza automática" no item.
  - evidência: `specs/itens/67-...md`, "Desvios".
  - efeito: arquivo com documento do aluno fora de qualquer registro, e fora do alcance da anonimização (RF-ALU-05, Sprint 13).
  - proposta: A4 (depois do PR; sem caminho de acesso indevido, a chave é protegida por prefixo do aluno e da organização, `OriginProof.valid`).
- **S1 · SUGESTÃO** — branches `ajustes/liberar-sprint-07` (local e remoto) já mesclados (PR #303); apagar é do dono.
- **S2 · SUGESTÃO · teste** — os testes novos citam "CA", não o código da regra. Citar RN-BEN-05, RN-BEN-08, RN-MAT-08, RN-GRA-09 e RN-MAT-09 no `@DisplayName` correspondente deixaria a varredura de rastreabilidade conferível.
- **S3 · SUGESTÃO · teste** — cobertura de linhas do front caiu de 97,01% para 96,57%, instruções de 93,89% para 92,89%. Sem ação; acompanhar.
- **S4 · SUGESTÃO · processo** — não há arquivo de notas de versão e 5 de 8 PRs não têm a linha; criar `docs/notas-de-versao.md` (ou decidir que a descrição do PR basta) evita a lacuna quando a primeira academia usar.
- **S5 · SUGESTÃO · documentação** — README: dizer que `seed.sh` e `prepare-runtime-db.sh` usam `docker compose -f` sem `-p` e que, para um clone isolado, é preciso `COMPOSE_PROJECT_NAME` e as quatro variáveis de porta.
- **S6 · SUGESTÃO · código** — `StepDone.tsx` comenta "até a #75" para o pagamento no balcão; a D3 da #67 diz #71. Corrigir o número no comentário.
- **S7 · SUGESTÃO · processo** — #63 teve 8 rodadas (2 de correção) com 26 tarefas em um PR; o dono já aceitou a divisão em seis partes (D1). Para a próxima sprint, manter o item pequeno.

#### Camada técnica

Lidos por inteiro: `V33` a `V38`, `BenefitWrite`, `Terms`, `TermsController`, `OriginProof`, `EnrollmentConclusion` (conclusão, prévia, avisos), `Enrollments` (pontos de permissão), 15 testes de integração pelo nome e `@DisplayName`, `StepDone.tsx`; varredura por padrão no restante do diff (permissões, SQL, fornecedor, texto literal). Não é leitura linha a linha das 14 mil linhas.

- Correção: sem erro de lógica encontrado. Pontos conferidos: concorrência por `for update` na linha da modalidade e do benefício (CA11, CA8); conclusão grava tudo ou nada em uma transação (`atomic`); `high` usa "estritamente maior que metade"; `effectiveFrom` conta 10 dias de antecedência (D6 da #65, constante em um ponto só).
- Segurança de acesso: todas as rotas novas conferem permissão no servidor (`students.requireAccess`, `STUDENT_MANAGE` por unidade, `BENEFITS`, `ORGANIZATION_SETTINGS`); unidade ou aluno de outra organização dá 404 antes do alcance; leitura de termo é do Administrador, escrita só do Coordenador (`TermsIT` CA5); a chave do comprovante só vale com o prefixo do aluno (`OriginProof.valid`); nenhum SQL por concatenação de entrada (parâmetros nomeados; a única concatenação é o `for update of em`, fixo).
- Migrações: V33 a V38 não editam migração antiga; RLS ligada e forçada nas 8 tabelas novas; `DELETE` retirado em `enrollment`, `enrollment_modality`, `benefit`, `student_graduation`, `enrollment_start`; `UPDATE` e `DELETE` retirados em `terms_version`; chaves compostas por organização. Exceção conhecida e registrada (P2 do backlog): `student_graduation.level_id` e `modality_id` usam chave simples (`modality` é catálogo da plataforma; `graduation_level` não tem `unique (organization_id, id)`); a organização é filtrada no código e pela RLS.
- Sem fornecedor em regra: nenhuma linha acrescentada em `backend/src/main`, `frontend/src`, `docs` e `infra` cita fornecedor (`git diff -U0` filtrado).
- Excesso: nada que mereça ajuste. `ponytail:` novos: `Terms` (modelo fixo versão 0 até o RF-PNL-07).

#### Desvios e decisões herdados dos itens

| Item | Desvio ou decisão | Classe | Destino |
|---|---|---|---|
| #63 D3 | uma modalidade ativa de cada tipo por aluno | muda requisito | R07-2, D2 |
| #63 D10 | troca de plano vale na próxima cobrança (`next_plan_id`) | técnico, com encaixe | A3 |
| #63 D11, D12 | alcance do Administrador por modalidade (ativa ou encerrada); transferência exige alcance nos dois lados | muda requisito | já em `08` RN-ALU-07; D12 em A2 (uma linha) |
| #64 D5, #65 D5 | notificação de benefício alto adiada para o módulo de notificações | muda requisito | A3 (#84) |
| #65 D4 | "Administrador escolhe qual fica" vira a confirmação da substituição | muda requisito | A2 |
| #66 D6 | desconto de professor marcado por `teacher_id` na concessão | técnico | registrar |
| #67 D3 | concluir matrícula sem criar cobrança; taxa e segunda data guardadas | muda requisito | A3 e D1 |
| #67 D4 | RN-MAT-12 e RN-LGP-01 e selo "Termo pendente" adiados | muda requisito | R07-1, A1 |
| #67 D5 | teto de 60 dias para a segunda cobrança (suposição) | técnico | registrar |
| #68 D1 | avisar quando a matrícula faria a turma passar da capacidade | muda requisito | A2 |
| #69 D2, D4 | decisão sobre cobranças em aberto adiada; benefício ativo encerra junto | muda requisito | A3 e A2 |
| #70 D1, D3, D6 | modelo da plataforma como versão 0 virtual; reaceite obrigatório; Administrador só lê | técnico/consistente com `01` e `09` | registrar |
| #70 D4 | limites de 20 a 20.000 caracteres e recusa de texto idêntico | técnico | registrar |

#### Ajustes propostos

**A1 · Dar destino a RN-MAT-12 e RN-LGP-01** (resolve R07-1) — antes do PR
- Tipo: ajuste de issue (sem código).
- O que mudar e onde: na #85, acrescentar `RN-MAT-12` e `RN-LGP-01` ao "Regras e requisitos" e ao "Pronto quando" (cada código com teste); acrescentar ao fim da #85 um bloco "Encaixe herdado da Sprint 07": gravar o aceite com conta, versão do termo (a de `terms_version`, consulta `Terms.current`), data, hora e IP; uso de imagem como caixa separada e revogável; selo "Termo pendente" (`StudentSeal`, comentário já deixado na #70); o aluno nasce sem aceite (`termPending: true`). Na #67, marcar no corpo que os dois códigos passaram para a #85.
- Como provar: `gh issue view 85` mostra os dois códigos; busca por "RN-LGP-01" acha a #85.
- Tamanho: pequeno.

**A2 · Atualizar a especificação** (resolve R07-2) — antes do PR
- Tipo: ajuste de especificação.
- O que mudar e onde:
  - `09` RN-MAT-01: acrescentar "O aluno tem no máximo uma modalidade ativa de cada tipo; a transferência leva a modalidade para outra unidade" (depende de D2).
  - `06` RN-TUR-02: "Avisa quando a turma está acima da capacidade ou quando a matrícula em andamento a faria passar dela".
  - `07` RN-BEN-08: "Ao dar um benefício a quem já tem outro, a janela pede a confirmação de substituir; confirmar fica com o novo e cancelar com o atual".
  - `07` RN-BEN-01 ou `09` RN-MAT-17: "Encerrar a modalidade encerra o benefício ativo dela".
  - `09` RN-MAT-17 e RF-MAT-03: "Enquanto não houver cobrança (Sprint 08), a janela mostra a lista de cobranças em aberto vazia; a decisão por cobrança entra com o painel da cobrança".
- Como provar: `git diff docs/especificacao` mostra os trechos; nenhuma mudança de código.
- Tamanho: pequeno.

**A3 · Encaixes das Sprints 08 e 09** (resolve R07-3) — antes da Sprint 08 começar; pode seguir depois do PR
- Tipo: ajuste de issue, sem issue nova.
- Blocos para colar:
  - fim da #71: "Encaixe herdado da Sprint 07. Pronto quando: [ ] a primeira cobrança lê `enrollment_start` (taxa, `fee_exempt_reason`, `second_due_on`) e cria a taxa de matrícula e a segunda sugestão (RN-MAT-05, RN-MAT-07, RN-MAT-19); [ ] a geração aplica `enrollment_modality.next_plan_id` e o limpa (D10 da #63); [ ] a geração só considera modalidade `ACTIVE`, aplica o benefício válido na data (RN-BEN-04) e ignora o vencido; [ ] 'Registrar pagamento agora' da etapa 5 passa a funcionar (`StepDone.tsx`); [ ] o retorno do aluno mostra o débito antigo (D5 da #63); [ ] a taxa 'já cobrada' passa a ler as cobranças (D7 da #67)."
  - fim da #73: "Encaixe herdado da Sprint 07. Pronto quando: [ ] `end-preview` da modalidade devolve `openCharges`; [ ] o Administrador decide manter ou cancelar cada cobrança, com motivo, no encerramento (RN-MAT-17, RN-COB-10), com teste de perfil."
  - fim da #84: "Pronto quando: [ ] benefício acima de 50% (RN-BEN-06) notifica o Coordenador, com teste; o filtro 'Benefícios altos' já existe (#64)."
- Como provar: `gh issue view 71 73 84` mostra os blocos.
- Tamanho: pequeno.

**A4 · Limpar comprovante abandonado** (resolve R07-5) — depois do PR
- Tipo: novo item ou parte da Sprint 13 (LGPD).
- Texto proposto: "Remover comprovantes de graduação de origem enviados e não usados em 24 h (tarefa agendada que lista `origin-proof-*` sem referência em `student_graduation.proof_file`). Pronto quando: [ ] arquivo sem referência com mais de 24 h é removido; [ ] arquivo referenciado nunca é removido; [ ] a tarefa grava auditoria de sistema sem a chave do arquivo." Marco sugerido: Sprint 13.
- Tamanho: pequeno.

Ordem sugerida: A1, A2 (depois de D2), A3, A4.

#### Decisões do dono

**D1 · Abrir o PR de `dev` para `main` antes da Sprint 08, sabendo que a matrícula concluída ainda não cria cobrança?** — recomendada: sim, abrir agora — motivo: o marco já diz que a primeira cobrança é da Sprint 08, não existe hospedagem até a Sprint 22 (`main` não é publicada), a #67 (D3) só pediu que o dono decidisse na revisão, e segurar a 07 obrigaria a revisar duas sprints juntas — alternativa: segurar o PR até a Sprint 08 estar revisada, se quiser que `main` nunca tenha um fluxo de matrícula sem cobrança.

**D2 · Um aluno pode ter a mesma modalidade ativa em duas unidades (ex.: Jiu-Jitsu na A e na B)?** — recomendada: manter a regra do código (no máximo uma por tipo) e escrevê-la na RN-MAT-01 — motivo: a transferência leva a modalidade e a graduação é por modalidade e aluno, então duas linhas da mesma modalidade confundiriam a graduação (D3 da #63) — alternativa: permitir uma por unidade; troca o índice `enrollment_modality_active` para `(student_id, modality_id, unit_id)` e exige decidir a qual unidade pertence a graduação.

#### Ações do dono

1. Subir o ambiente (`docker compose -f infra/docker-compose.yml up -d --wait`, `bash infra/scripts/prepare-runtime-db.sh`, back-end com perfil `local`, `bash infra/scripts/seed.sh`) e, em `frontend/`, `npm run dev`. Abrir `http://localhost:5173`, entrar como Coordenador, temas claro e escuro, largura 360 px (e 1280 px), e olhar:
   - Pessoas › Alunos › Nova matrícula (`/alunos/nova-matricula`): as 5 etapas contra o canvas `MatriculaAluno`, `MatriculaModalidades`, `MatriculaVencimento`, `MatriculaResumo`, `MatriculaConcluida`; sem rolagem horizontal; foco e teclado; "Registrar pagamento agora" desabilitado com a dica; aviso de faixa etária e de capacidade na etapa 2.
   - Ficha de um aluno com matrícula: seção Matrícula, janelas Trocar turma, Trocar plano, Transferir e Encerrar modalidade (`EncerrarModalidade`), "Conceder benefício" e selo Bolsista.
   - Financeiro › Benefícios (`/beneficios`): filtros, janelas Conceder (`BeneficioConceder`) e Encerrar.
   - Configurações › Termos (`/configuracoes/termos` ou o caminho do menu): editor, versões, escolha de reaceite sem valor padrão (`ConfigTermos`).
   - Contraste de texto e de estados em cada tela nos dois temas; zoom de 200%; tom dos textos em pt-BR.
2. Decidir D1 e D2.
3. Pedir/aceitar A1 a A3 (só texto de issue e de especificação) antes do PR.
4. Com o veredito `APROVAR` e os ajustes resolvidos: abrir o PR de `dev` para `main` com o método "Merge" (nunca squash) e, depois de revisar, dizer "sim" para liberar a Sprint 08.
5. Opcional: apagar os branches `ajustes/liberar-sprint-07` (local e remoto) e os contêineres `revctcore7-*` (não são desta revisão; conferir de quem são).

#### Limitações e o que não foi conferido

- Contraste real, 360 px sem rolagem horizontal, foco e teclado, zoom e axe em navegador real: NÃO CONFERIDO, só por proxy (jsdom). Sugestão de teste que fecha a lacuna, sem criar: um `@critical` Playwright por tela nova com `AxeBuilder` nos dois temas a 360 px (hoje `npm run e2e` cobre 33 fluxos; `e2e:full` não foi rodado, é do PR para `main`).
- Tela contra o canvas e tom dos textos: NÃO CONFERIDO (olho humano). Os arquivos de item registram que os canvases `.dc.html` não estavam acessíveis em #64, #65, #69 e #70.
- Publicação, backup e hospedagem: NÃO CONFERIDO (limitação aceita, Sprint 22).
- `gitleaks` e OSV-Scanner: só no CI (verdes nos 8 PRs); `npm ci` não achou vulnerabilidade.
- Tempo de `verify` não comparável com a linha de base (rodei etapas do front em paralelo ao final).
- `e2e:full`: não rodado (só no PR para `main`).
- A varredura de rastreabilidade foi por código nos testes e por comportamento; os testes novos citam "CA" (S2).

#### Riscos para a próxima sprint

- A Sprint 08 recebe de volta: `enrollment_start` (V36), `next_plan_id`, tela final da matrícula sem pagamento, `openCharges` vazio, benefício "a partir da próxima cobrança", débito antigo no retorno. Nada disso está nas issues #71 a #74 (A3).
- Estrutura que a Sprint 08 pode quebrar: a taxa de matrícula "já cobrada" usa "teve modalidade na unidade" (D7 da #67) até existir cobrança para consultar.
- Dívida com prazo: `osv-scanner.toml` vence em 2027-01-15 (Sprint 12 ou 13). O `ponytail:` do modelo de termo fixo (`Terms`) vive até o RF-PNL-07.
- Sprint 10 (portal): precisa de RN-MAT-12 e RN-LGP-01 na #85 (A1), do selo "Termo pendente" e de `Terms.current` (já existe).
- Sprint 09: RN-BEN-06 (benefício alto notifica o Coordenador) sem "Pronto quando" na #84 (A3).
- Sprint 12: P2 do backlog (nível em uso por graduação de aluno de origem) e a nulidade de `level` e `blocked` na lista simples (D13 e D14 da #63).
- Nenhuma issue `depende de você` trava a Sprint 08.

---

Ajustes A1 a A3 aplicados no PR #312; D1 e D2 respondidas pelo dono. A4 fica para depois do PR dev→main.
