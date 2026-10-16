# 13 — Organização, unidade, configurações e limites no banco

Origem: issue #13; `docs/especificacao/01-organizacao-e-isolamento.md`, Dados e RN-ORG-04/05/10/16; `docs/especificacao/00-visao-geral.md` §11; `docs/plano-de-sprints.md`, Sprint 01 e definição de pronto. Branch: `item/13-organization-unit-settings-limits`.

## 1. Objetivo

Preparar a persistência de organizações e unidades, seus valores iniciais e limites, para os módulos seguintes. Materializar a academia piloto e as outras duas organizações sintéticas já identificadas pelo seed.

Fora do escopo: rotas e telas de configuração, seletor e texto “academia” da interface, contas e vínculos de usuários, cobrança da assinatura, catálogo completo de modalidades/graduação, conteúdo jurídico de termos, operações de suporte e integrações reais. O fluxo genérico da issue aplica-se às camadas efetivamente alteradas (D1).

## 2. Requisitos

- R1: Cada criação de organização deve persistir, atomicamente, uma primeira unidade e suas configurações, sem deixar organização parcialmente inicializada (RN-ORG-04; D2).
- R2: Persistir por organização os limites de alunos, usuários da gestão, unidades e módulos, distinguindo limites comerciais informativos dos máximos operacionais de 10 unidades e 200 usuários da gestão; acima dos máximos operacionais exige suporte (RN-ORG-05; D3).
- R3: Configurações de faixas etárias, graduação, termos e prazos pertencem à organização; vencimentos, taxas, lembretes, permissões, dados de pagamento e links pertencem à unidade; planos, turmas e cobranças futuros usarão esse vínculo, sem criar suas tabelas agora (RN-ORG-10).
- R4: Inicializar e salvar os padrões descritos no plano técnico; mudanças futuras nos padrões só afetam novas inicializações, preservando valores previamente persistidos, inclusive após reaplicar o seed (RN-ORG-16).
- R5: Dados das novas tabelas devem ter integridade referencial, validações dos intervalos especificados e isolamento no PostgreSQL por organização; leituras internas recebem organização explicitamente (AGENTS.md; D4).
- R6: O script de dados deve criar a academia piloto e completar as três organizações fictícias com IDs determinísticos, sem dados reais e sem duplicação ou sobrescrita (issue #13 e convenção #12).

## 3. Critérios de aceite

- CA1 (R1): Ao inicializar uma organização válida, existem uma unidade inicial ativa, configurações da organização e da unidade e limites; uma falha em qualquer inserção reverte a operação inteira (teste identificado RN-ORG-04).
- CA2 (R2): Ao ler os limites salvos, os máximos operacionais são 10 e 200 e os limites comerciais não configurados representam ausência de limite, sem bloquear alunos ou módulos; a regra interna de capacidade aceita 9→10 e 199→200, recusa 10→11 e 200→201 sem autorização de suporte e permite a exceção explicitamente autorizada (teste RN-ORG-05; D3).
- CA3 (R3): Duas unidades da mesma organização compartilham os prazos e faixas etárias da organização, mas possuem configurações de vencimento e pagamento independentes; associar configurações à unidade de outra organização falha por chave composta (teste RN-ORG-10).
- CA4 (R4): A primeira inicialização salva todos os padrões da tabela de Dados; alterar um padrão usado na inicialização e criar outra organização mantém a anterior intacta e aplica o novo valor à segunda; o seed não sobrescreve valores personalizados (teste RN-ORG-16).
- CA5 (R5): O banco recusa prazo de inativação fora de 15–90, limite experimental fora de 1–3, vencimento fora de 1–28, valores financeiros negativos, nomes vazios e referências inexistentes/cruzadas; as validações internas recusam entradas inválidas antes de gravar e preservam a atomicidade.
- CA6 (R5): Com papel PostgreSQL sem superusuário nem BYPASSRLS, organização A só lê/altera A; tentativas de inserir/alterar referências de B falham, e sem contexto nenhuma linha de negócio é retornada ou escrita. Leitura interna exige organização, com SQL parametrizado e filtro explícito além da política RLS.
- CA7 (R6): Aplicar todos os seeds duas vezes produz as mesmas três organizações e suas unidades/configurações; a piloto mantém `md5('ctcore-seed-org-1')::uuid`, e o conteúdo personalizado continua intacto. Os testes existentes de recursos por organização continuam passando.

## 4. Plano técnico

### Dados

Criar nova migração `backend/src/main/resources/db/migration/V3__organizations_units_settings_limits.sql` (a criar), sem editar V1/V2 já entregues. Usar UUID, nomes SQL em inglês, `timestamptz` para instantes, `bigint` em centavos para taxas e FKs/índices por organização. Tabelas a criar:

- `organization`: identidade, nome, referências opcionais de logo e cor, documento do titular e data de entrada; não inventar contas/coordenador ou situação financeira antes dos módulos responsáveis.
- `unit`: organização, nome, nome curto único, endereço e coordenadas opcionais até o cadastro completo, contatos opcionais, CNPJ opcional, fuso `America/Sao_Paulo` e situação ativa/desativada; chave única `(organization_id, id)` para referências compostas.
- `organization_settings` e `unit_settings`: uma configuração por proprietário, valores concretos persistidos, com `organization_id` também na configuração da unidade e FK composta para impedir cruzamento.
- `organization_limits`: uma linha por organização com limites de alunos, usuários, unidades e módulos e máximos operacionais; ausência de limite comercial não vira zero nem bloqueio.

Valores iniciais obrigatórios, da tabela de configurações de `01-organizacao-e-isolamento.md`:

| Organização | Valor inicial |
|---|---|
| Faixas etárias | Infantil 4–12, Juvenil 13–17, Adulto 18+ (máximo aberto) |
| Inativação por atraso | 30 dias, permitido 15–90 |
| Histórico de ex-aluno | 12 meses |
| Guarda de experimental não convertida | 6 meses |
| Experimentais por pessoa/unidade | 2, permitido 1–3 |
| Treinar em outra unidade | Depende do plano |
| Comprovante de graduação de origem | Desligado por modalidade; ponto de encaixe sem catálogo fictício |
| Modalidades/graduação e termos | Ponto de encaixe para o catálogo e modelos versionados dos módulos correspondentes, sem texto jurídico inventado (D5) |

| Unidade | Valor inicial |
|---|---|
| Vencimentos permitidos | 1, 5, 10, 15, exclusivamente 1–28 |
| Matrícula | Taxa desligada e valor zero |
| Repasse por meio de pagamento | Zero para todos os meios suportados, sem fornecedor no nome dos campos |
| Chave Pix e conta de pagamento | Não cadastrada / não conectada, sem credenciais |
| Lembretes | 3 dias antes, no dia, 3 e 10 dias depois |
| Permissões do professor | Quatro ligadas, com representação que possa receber os nomes do módulo de acesso |
| Diretório | Desligado |
| Links de experimental e autocadastro | Ligados |

Campos estruturados pequenos podem usar JSONB validado; não usar um cadastro genérico de chave/valor sem tipos ou defaults recalculados na leitura. Intervalos simples também são CHECKs do PostgreSQL. Não criar tabelas dos módulos futuros só para preencher relações vazias.

### Servidor e segurança

Criar o pacote `backend/src/main/java/br/com/ctcore/organization/` com o mínimo de tipos e persistência necessários: inicialização transacional, leitura por organização e regra de capacidade. Reutilizar Spring JDBC (`JdbcClient` já usado por `features/FeatureFlags.java`), transações Spring e relógio injetável. Não adicionar dependência ou camada genérica de repositórios.

Inicialização deve receber dados da primeira unidade; não inventar endereço ou conta. Validações e contagens têm organização explícita; qualquer futura chamada de criação adicional deve combinar capacidade com gravação numa transação com lock por organização, para não ultrapassar 10 sob concorrência. A regra de 200 é testada com contagem fornecida, sem inventar tabela de usuários. A autorização de exceção não é parâmetro HTTP e só poderá vir do fluxo de suporte autorizado quando existir.

Aplicar ENABLE/FORCE RLS às novas tabelas, usando a técnica já provada em `RowLevelSecurityIT`: `nullif(current_setting('app.organization_id', true), '')::uuid`; na raiz `organization`, comparar o próprio `id`. Contexto em teste/transação usa `set_config(..., true)`, sem vazar para conexão reutilizada. Testar com papel sem bypass, nunca considerar teste como superusuário prova de RLS. A integração com sessão autenticada e aplicação geral da política pertence à #14.

Não expor escrita operacional antes do serviço de auditoria da sprint. Migração e seed são provisionamento técnico, sem ator autenticado; não criar auditoria substituta nem disponibilizar setters HTTP sem registro. Chamadas operacionais futuras devem integrar auditoria na mesma transação antes de serem expostas (D1). Não coletar saúde, senha, token ou credencial de pagamento.

### Seed e compatibilidade

Criar `infra/seed/02-organization-unit-settings.sql` (a criar), em transação conforme `infra/scripts/seed.sh`, com os três IDs `md5('ctcore-seed-org-' || n)::uuid` já usados por `01-organizations.sql`; criar unidades com identificadores também determinísticos. Usar `on conflict do nothing` e completar registros faltantes sem atualizar existentes. A execução de `01-organizations.sql` precede a nova tabela de dados no seed; não introduzir FK em `organization_feature` que quebre essa ordem ou os testes existentes nesta fase. Não mudar V2.

Ampliar `backend/src/test/java/br/com/ctcore/db/SeedDataIT.java`, preservando suas provas, e criar testes unitários e de integração no pacote `organization` (a criar), reutilizando `db/PostgresIT.java`. A preparação/limpeza dos novos testes deve respeitar a instância compartilhada de PostgreSQL e não contaminar testes existentes. A recuperação da migração é correção por nova migração e backup; não apagar dados nem editar a migração depois de mergeada.

### Camadas que não mudam e riscos

API/OpenAPI, MSW e interface: não mudam, pois nenhuma rota/tela é criada; não há texto de tela, axe ou novo E2E aplicável. Não há recurso novo liberado progressivamente nem mudança visível que peça nota para academias. Autenticação e autorização HTTP permanecem inalteradas. O principal risco é RLS passar despercebida em conexões de superusuário e o seed perder personalizações; CA6/CA7 cobrem ambos. Não adicionar FK a recursos existentes sem migração e adaptação completa dos seus chamadores/fixtures.

## 5. Tarefas

- [x] T1: Criar migração aditiva com tabelas, valores iniciais, integridade e políticas RLS — cobre CA3, CA4, CA5, CA6 — prova: integração PostgreSQL real para defaults, CHECKs, FKs e papel sem bypass, incluindo migração sobre V1/V2.
- [x] T2: Implementar inicialização transacional e leitura com organização explícita, reusando JDBC e relógio, sem entrada HTTP — cobre CA1, CA3, CA4, CA5, CA6 — prova: testes de sucesso, rollback provocado, parâmetros inválidos, snapshots de padrões e leitura cruzada negada.
- [x] T3: Persistir limites e implementar regra mínima de capacidade com distinção entre limite comercial e operacional — cobre CA2 — prova: testes RN-ORG-05 com limites exatos, excesso, exceção de suporte e ausência de bloqueio comercial.
- [x] T4: Completar seed determinístico da piloto e das outras organizações e ampliar SeedDataIT — cobre CA1, CA4, CA7 — prova: reaplicação, preservação de customizações e vínculo correto de unidades/configurações; manter provas anteriores.
- [x] T5: Executar regressão e conferir rastreabilidade RN-ORG-04/05/10/16 — cobre CA1 a CA7 — prova: `./backend/mvnw -f backend/pom.xml verify`, incluindo contratos existentes e cobertura mínima de 80%; registrar resultados e limpeza de recursos descartáveis criados na prova conforme AGENTS.md.

## 6. Decisões

- D1: A entrega explícita é de banco/servidor. A lista genérica de fluxo não antecipa telas, contas, seletor, rotas e auditoria compartilhada ainda inexistentes; essas camadas não se alteram aqui. Alternativa descartada: inventar CRUD autenticado ou auditoria provisória para este item de fundação. Nenhuma escrita operacional sem auditoria será exposta.
- D2: A inicialização interna recebe organização e primeira unidade e grava tudo na mesma transação; é o caminho reutilizável para o cadastro futuro. Alternativa descartada: trigger que inventa dados públicos da primeira unidade ou endpoint público de criação antecipado.
- D3: RN-ORG-05 é teto operacional, com exceção de suporte; limites comerciais persistidos são informativos na v1, conforme §11 da visão geral. Não inventar teto de alunos ou módulos, usar ausência de limite. Alternativa descartada: bloquear matrícula por um plano futuro ou criar usuários só para contar 200.
- D4: RLS nasce nas tabelas deste item, com contexto explícito nas provas; #14 integrará o contexto seguro da aplicação. Alternativa descartada: entregar tabelas novas sem proteção ou declarar que superusuário demonstra isolamento.
- D5: Catálogos, modelos jurídicos, contas e assinatura são pontos de encaixe, não entidades fictícias. Referências não configuradas permanecem ausentes; a estrutura permite posterior preenchimento no escopo correto. Alternativa descartada: inventar termos legais, coordenador, assinatura ou catálogo de graduação aqui.
- Suposição: as três organizações sintéticas identificadas no seed existente continuam úteis; a primeira representa a piloto, sem corresponder a dados reais. Se a piloto real exigir outras informações, elas serão preenchidas pelo fluxo autorizado de implantação, sem alterar IDs ou sobrescrever configurações pelo seed.

## Construção

- T1 — arquivos: `V3__organizations_units_settings_limits.sql`, `OrganizationsIT.java` — prova: `backend\mvnw.cmd -f backend/pom.xml verify` → PostgreSQL 16 real aplicou V1/V2/V3; defaults, CHECKs, referências compostas e RLS nas cinco tabelas passaram. Papel de prova sem superusuário/BYPASSRLS: leitura e atualização próprias, referências cruzadas recusadas e ausência de contexto sem leitura/escrita; configuração local não persiste após rollback.
- T2 — arquivos: `organization/Organizations.java`, `OrganizationsIT.java`, `OrganizationsTest.java` — mesma prova → inicialização transacional, rollback por unidade duplicada e por falha provocada na inserção final, validação prévia, leitura filtrada, padrões persistidos e duas unidades com dados independentes passaram (RN-ORG-04/10/16). Reutilizados JDBC, transação Spring e Clock.
- T3 — arquivos: V3, `Organizations.java`, `OrganizationsTest.java` — mesma prova → ausência de limite comercial persistida, tetos 10/200, capacidade independente por recurso, fronteiras 9→10/199→200, excesso, exceção explícita e contagens negativas passaram (RN-ORG-05).
- T4 — arquivos: `infra/seed/02-organization-unit-settings.sql`, `db/SeedDataIT.java` — mesma prova → sete testes de seed passaram, incluindo três organizações completas, piloto determinística, reaplicação e preservação integral dos valores personalizados; provas de recursos existentes preservadas.
- T5 — arquivos: testes acima, `AGENTS.md`, `specs/projeto.md` — prova: regressão final abaixo; registrados os novos dados e o caráter interno do provisionamento. `git diff --check` sem erros.

Regressão: `backend\mvnw.cmd -f backend/pom.xml verify` → **BUILD SUCCESS**, 73 testes rápidos e 27 de integração, sem falhas nem pulos; contratos existentes passaram. JaCoCo: 343/356 linhas (96,35%) no total e 56/56 (100%) no pacote `organization`; gate de cobertura aprovado. Log local ignorado pelo Git: `backend/target/item-13-verify.log`. A linha de base de testes aprovados foi preservada.

Regressão da interface, em `frontend/`: `npm.cmd run lint`, `npm.cmd run typecheck`, `npm.cmd run build`, `npm.cmd run test:cov`, `npm.cmd run e2e` → todos passaram; 121 testes, 97,83% de cobertura de linhas e um E2E crítico aprovado. Não houve alteração na interface.

Desvios: usados `mvnw.cmd` e `npm.cmd` no PowerShell; `npm.ps1` é bloqueado pela política local. Ajustada a leitura opcional à API disponível do JdbcClient (`listOfRows().stream().findFirst()`) após a primeira compilação. O nome curto é único globalmente, conforme RN-ORG-15, e os UUIDs têm default do banco conforme convenção existente. Acrescentados os meios de pagamento já descritos nas especificações, todos com taxa inicial zero. Nenhuma mudança de requisito. O redirecionamento `*>` do PowerShell fez o invólucro reportar saída 1 por stderr nativo mesmo com Maven registrando BUILD SUCCESS; os relatórios finais de todas as suítes e do JaCoCo confirmam os resultados acima.

Limpeza: Testcontainers removeu os containers de PostgreSQL e Ryuk; `docker ps` vazio ao final. Nenhuma imagem descartável adicional foi criada; as imagens permanentes de integração foram mantidas. Papel, trigger e função de prova foram removidos pelos testes. Não foram executados builds de imagens nem scanners de segurança de CI nesta construção.

Não é do item: nenhuma alteração não identificada. Preservadas as mudanças pré-existentes de preparação em `specs/backlog.md` e `specs/projeto.md` e a especificação do item. Sem commit, push ou troca de branch.

## Verificação

### Rodada 1 — OK — 2026-10-16, estado 084e559f19379d6419ad932c0e2770abd68fd9b8

Verificação delegada a agente novo, independente da especificação e construção. Fontes: issue #13 obtida por `gh issue view 13 --json title,body,milestone`, AGENTS.md, este item, regras em docs/especificacao e Git. Base atualizada por `git fetch origin dev`; merge-base `2813e997cd3862270e012ee28dbaddcc4bd4aca9`. Diff completo dessa base ao estado examinado. Snapshot repetido após as provas retornou o mesmo estado.

| Critério | Status | Evidência |
|---|---|---|
| CA1 | PASSOU | OrganizationsIT: initializesDefaultsAndSnapshots, rollsBack e rollsBackLateFailure passaram; primeira unidade ativa e todas as configurações/limites persistidos, falhas inicial e final sem registros parciais. |
| CA2 | PASSOU | OrganizationsTest.capacity passou: 9/199 aceitos, 10/200 e superiores recusados sem suporte, aceitos com autorização; limites persistidos 10/200 e comerciais nulos conferidos em initializesDefaultsAndSnapshots. |
| CA3 | PASSOU | OrganizationsIT.settingsOwnershipAndConstraints passou: duas unidades ligadas à mesma configuração organizacional, vencimentos/Pix próprios e FK composta rejeitando associação cruzada. |
| CA4 | PASSOU | initializesDefaultsAndSnapshots conferiu todos os padrões e preservação de 30 dias na primeira organização após inicialização da segunda com 45; SeedDataIT.organizationSettingsAreCompleteAndPreserved preservou o conteúdo integral após personalização e reaplicação. |
| CA5 | PASSOU | OrganizationsTest.validatesProvisioningBeforeWriting e OrganizationsIT.settingsOwnershipAndConstraints passaram: intervalos, nomes, UUIDs ausentes, valores negativos e referências inexistentes/cruzadas recusados; rollback exercitado separadamente. |
| CA6 | PASSOU | OrganizationsIT.rowLevelSecurity passou nas cinco tabelas com papel NOSUPERUSER/NOBYPASSRLS: leitura/alteração próprias, ocultação de B, escrita cruzada negada, ausência de contexto sem leitura/escrita e contexto local limpo após rollback. Organizations.java usa parâmetros e filtro explícito nas três leituras internas; unitSettings de B para unidade de A retorna vazio no teste. |
| CA7 | PASSOU | Sete testes de SeedDataIT e cinco de FeatureFlagsIT passaram: três organizações completas, piloto com ID determinístico, reaplicação estável e preservação de personalizações; V1/V2/V3 aplicadas no PostgreSQL 16.4 real. |

Comandos e resultados observados:

- `git status --short`, inspeção dos novos arquivos, `git merge-base HEAD origin/dev`, `git diff <base> <estado>` e `git diff --check`: apenas arquivos declarados do item/preparação; sem erro de whitespace. Nenhum segredo identificado nos arquivos novos.
- Snapshot oficial: a invocação direta do script PowerShell foi recusada pela política de execução; o Bash do sistema não encontrou o caminho Windows. A alternativa oficial `git-snapshot.sh` executada com `C:\Program Files\Git\bin\bash.exe` produziu o estado acima, sem alterar política, índice verdadeiro ou arquivos.
- `backend\mvnw.cmd -f backend/pom.xml verify`: saída 0, BUILD SUCCESS; 73 testes rápidos e 27 de integração, zero falhas/erros/pulos. Inclui 2 OrganizationsTest, 5 OrganizationsIT e 7 SeedDataIT. Relatórios observados em backend/target/surefire-reports e backend/target/failsafe-reports. Gate JaCoCo aprovado; XML em backend/target/site/jacoco/jacoco.xml: 343/356 linhas (96,35%) no total, 56/56 (100%) em organization. Regressão prescrita preservada, sem falha a comparar com a base.
- Em frontend: `npm.cmd run lint`, `npm.cmd run typecheck`, `npm.cmd run build`, `npm.cmd run test:cov`, `npm.cmd run e2e`: todos concluíram com sucesso; 121 testes, 97,83% de linhas e 1 E2E crítico aprovado. Avisos de canvas do jsdom não causaram falhas.
- `docker ps --format '{{.ID}} {{.Names}}'` após Maven: vazio. Testcontainers removeu os containers PostgreSQL/Ryuk; imagens permanentes de integração mantidas. Não houve build de imagem nem imagem descartável adicional nesta rodada.

Comparação com o card: R1–R6 cobrem a entrega explícita de banco, piloto e RN-ORG-04/05/10/16. D1 delimita justificadamente o fluxo genérico: sem rota/tela nova, não há alteração de contrato/MSW/interface nem escrita operacional exposta. Provisionamento interno prepara a integração futura com auditoria, conforme AGENTS.md. Sem achado obrigatório de especificação.

Achados obrigatórios: nenhum. Sugestões: nenhuma.

Limites da prova: scanners de segurança do CI, builds de imagens e e2e:full não executados; não houve alteração de interface, infraestrutura de imagens ou dependências. A integração com contexto autenticado e auditoria operacional continua a cargo dos itens responsáveis antes de expor escritas.

## Entrega

Persistência de organização, primeira unidade, configurações e limites com inicialização transacional, padrões salvos e isolamento RLS nas cinco tabelas. Seed idempotente completa a piloto e outras duas organizações sintéticas sem sobrescrever personalizações. Inclui o registro da revisão da Sprint 00 e liberação da Sprint 01 pelo dono.

Verificação independente: rodada 1 OK, estado 084e559f19379d6419ad932c0e2770abd68fd9b8; comparação na entrega só acrescentou o registro dessa rodada. Back-end: 100 testes aprovados, cobertura total de 96,35% e 100% em organization. Interface: lint, tipos, build, 121 testes, cobertura de 97,83% e E2E crítico aprovados. Base origin/dev conferida sem avanço desde a verificação.

Limitações: provisionamento interno, sem novas rotas ou telas; contexto autenticado e auditoria operacional entram nos itens responsáveis antes de expor escrita. Scanners de segurança e build da imagem serão conferidos no CI do PR; e2e:full não foi executado. Nenhuma alteração de produto feita na fase de entrega.

Closes #13