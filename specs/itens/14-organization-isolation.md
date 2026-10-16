# 14 — Isolamento entre organizações: contexto, filtro e row-level security

Origem: issue #14; `docs/especificacao/01-organizacao-e-isolamento.md` (RN-ORG-01/02), `00-visao-geral.md` (RNF-06) e `docs/plano-de-sprints.md`. Branch: `item/14-organization-isolation`.

## 1. Objetivo

Entregar contexto confiável de organização/unidade por requisição e aplicá-lo nas consultas e na transação PostgreSQL, impedindo leitura ou alteração cruzada mesmo com identificadores adivinhados. Completar a fundação da #13 e proteger também as feature flags existentes.

Fora do escopo: login, sessões e cadastro de vínculos da Sprint 02; seletor e visão consolidada; CRUD público; serviço de auditoria da #15; interfaces e integrações externas. O fluxo genérico de telas/rotas da issue só se aplica quando houver essas entregas (D1).

## 2. Requisitos

- R1: RNF-06 e RN-ORG-02: operações de negócio são isoladas simultaneamente por filtro explícito no código e RLS no PostgreSQL.
- R2: RN-ORG-01: toda tabela de negócio identifica sua organização (a própria `organization` usa `id`); dados operacionais identificam também a unidade; todas as seis tabelas existentes por organização ficam protegidas (D2).
- R3: Contexto por requisição vem exclusivamente de identidade autenticada confiável do servidor, com organização e unidade autorizadas, sem confiar em identificadores fornecidos isoladamente pelo cliente (D3).
- R4: Sem organização definida, leituras de negócio retornam vazio e escritas são recusadas; uma operação nunca troca seu contexto para a organização recebida como argumento (D4).
- R5: Organização na sessão do banco é definida na mesma conexão/transação da consulta e removida após conclusão ou rollback; contexto da requisição não vaza entre threads, requisições ou conexões reutilizadas.
- R6: A credencial operacional não possui superusuário, BYPASSRLS nem propriedade que contorne as políticas; migrações/provisionamento administrativo ficam separados do acesso operacional (D5).
- R7: Cada RN/RNF citado tem teste nominal; cobertura global permanece em 80% e o pacote de isolamento alcança 100%, preservando contratos e comportamentos não alterados.

## 3. Critérios de aceite

- CA1 (R1/R2): dadas duas organizações sintéticas, consultas filtradas e SQL direto sob a credencial operacional veem somente a organização corrente nas seis tabelas; select por ID de B sob A não retorna dados.
- CA2 (R1/R2/R4): sob A, insert de B é recusado por RLS; update/delete de B não alteram linhas, e tentar transferir uma linha de A para B é recusado por WITH CHECK; sem contexto, select não retorna linhas e nenhuma escrita se completa.
- CA3 (R3): identidade autenticada com organização A e unidade autorizada estabelece esse contexto; cabeçalhos, parâmetros ou corpo alegando B não o substituem; unidade não autorizada ou de B é recusada sem revelar dados.
- CA4 (R3/R4): principal anônimo não autentica uma rota protegida (401); principal autenticado sem escopo confiável não acessa dados de organização (403 na fronteira HTTP); rota de infraestrutura pública continua funcionando. Principal de perfil distinto não ganha escopo por sua autoridade textual.
- CA5 (R4): em contexto A, `Organizations.settings/limits(B)` e `unitSettings(B, ...)` não devolvem B, tampouco mudam a sessão; feature flag de B não é lida nem alterada sob A; consulta sem contexto permanece vazia. Unidade alheia ao escopo não é acessível.
- CA6 (R5): em pool com uma conexão, executar A, B, sem contexto e uma operação com exceção não deixa contexto anterior; requisições concorrentes e uma requisição após falha mantêm escopos independentes; tentar substituir escopo em transação já iniciada falha.
- CA7 (R6): conexão operacional real prova `rolsuper=false`, `rolbypassrls=false`, ausência de propriedade/bypass nas tabelas; migrações executam com credencial separada, seed continua idempotente e inicialização interna da #13 continua atômica.
- CA8 (R7): `verify` passa, testes nomeiam RNF-06, RN-ORG-01 e RN-ORG-02, JaCoCo atinge os limites, e os testes de contrato continuam passando sem criar rota de produto para demonstração.

## 4. Plano técnico

### Dados e transações

- Existem V1/V2/V3 em `backend/src/main/resources/db/migration/`; não reescrevê-las. Criar V4 para ENABLE/FORCE RLS e políticas USING/WITH CHECK de `organization_feature`, reaproveitando `nullif(current_setting('app.organization_id', true), '')::uuid` da V3. Não adicionar FK retroativa de feature flags: seeds e testes anteriores permitem flags antes do provisionamento; isso é outro assunto. `shedlock` e histórico Flyway são infraestrutura global, não tabelas de negócio.
- A V3 já protege `organization`, `unit`, `organization_settings`, `unit_settings` e `organization_limits`. Preservar FKs compostas organização/unidade; não criar tabelas só para provar o item. Recuperação de V4 por migração corretiva posterior, sem apagar dados nem desabilitar isolamento como rollback operacional.
- Centralizar no pacote `br.com.ctcore.isolation` (a criar) a instalação do contexto e a execução transacional com `set_config(..., true)` parametrizado na conexão vinculada à transação Spring. Cobrir transação existente e impedir troca de organização dentro dela; nenhum setter aceita um tenant arbitrário da consulta. Não depender de sessão persistente nem de open-in-view. Preferir as APIs transacionais já disponíveis, sem nova biblioteca ou framework genérico de repositórios.
- `Organizations.java` existe e hoje redefine `app.organization_id` a cada chamada: separar provisionamento interno explícito das leituras operacionais, que devem obedecer ao contexto e manter WHERE por organização e unidade. `FeatureFlags.java` existe e já filtra por organização, mas não instala contexto transacional: integrá-lo ao mesmo mecanismo, mantendo validação, padrão desligado e upsert idempotente.
- Provisionamento de organização nova não pode se tornar um bypass operacional. Continuar interno, transacional e sem HTTP; qualquer chamada sob contexto existente não pode substituí-lo por outro. Escritas internas existentes continuam aguardando integração com auditoria antes da exposição operacional (#15).

### Segurança e API

- `SecurityConfig.java` e `SecurityErrorHandlers.java` existem: integrar filtro após autenticação que usa contrato mínimo de principal com organização corrente e unidade autorizada, a criar em `isolation`; limpar em finally. Não implementar autenticação nem aceitar `X-Organization-Id` como prova de vínculo. Sem principal compatível, negar acesso aos recursos de negócio; não bloquear actuator/fluxos públicos futuros só por falta de tenant.
- Escopo sem unidade representa somente acesso organizacional; não habilita implicitamente consulta ou escrita operacional em qualquer unidade. A unidade selecionada precisa pertencer à organização e ao escopo autenticado; conferir isso no servidor e nos filtros dos acessos atuais a `unit_settings`. Regras completas de perfis e seleção ficam nos itens próprios.
- API de produto não muda: nenhuma rota nova. Testar o filtro com controller exclusivo de teste e principal sintético, cobrindo acesso por perfil/escopo; não publicar endpoint de prova. `docs/openapi.yaml` e MSW não mudam porque não há contrato HTTP novo. Reutilizar 401/403 existentes, sem logar identidade ou detalhes sensíveis.
- A conexão atual em `application.yml` usa POSTGRES_USER, que no compose é administrador. Separar credenciais de runtime e de Flyway, com papel operacional sem privilégios administrativos. Criar script idempotente de preparação local (a criar em `infra/scripts/`) que funcione também com volume existente, sem removê-lo, sem senha real no repositório e sem imprimir segredos. Não conceder ao runtime associação ao papel administrativo. Dar somente os grants necessários nas tabelas, inclusive `shedlock`, mantendo Flyway com credencial administrativa separada.

### Alcance e provas

- Alterar conforme necessário arquivos existentes: `backend/src/main/resources/application.yml`, `infra/.env.example`, `backend/README.md`, `README.md`, `infra/README.md` e testes `db/PostgresIT.java`, `db/RowLevelSecurityIT.java`, `organization/OrganizationsIT.java`, `organization/OrganizationsTest.java`, `features/FeatureFlagsIT.java`, `db/SeedDataIT.java`. Estes testes não podem continuar provando segurança só com conexão superusuária: separar conexão administrativa de fixtures e conexão operacional usada pelo serviço.
- Criar testes unitários e IT em `backend/src/test/java/br/com/ctcore/isolation/`, migrando a prova da tabela fictícia para as tabelas reais onde couber. Preservar fixtures sintéticas e testes de rollback da #13; ajustar chamadores de Organizations/FeatureFlags encontrados pela busca antes da alteração. Seeds existentes em `infra/seed/01-organizations.sql`, `02-organization-unit-settings.sql` e `infra/scripts/seed.sh` continuam administrativos e idempotentes; nova tabela não é necessária.
- Interface: não muda; tradução, quatro estados e axe não se aplicam sem tela nova. Auditoria: nenhuma ação operacional nova é exposta, não antecipar o serviço da #15. Não há dados pessoais novos, senhas de usuário, upload ou pagamento; continuam scanners do CI.
- Riscos: bypass por credencial administrativa, contexto alterado por leitura legada, perda de contexto em transação ou vazamento pelo pool; testes com banco real e credencial restrita devem detectar todos. Documentar preparação de volume existente e variáveis para execução da imagem sem mudar suas permissões de forma destrutiva.

## 5. Tarefas

- [x] T1: Criar contrato do principal/escopo confiável e filtro de requisição, integrar com SecurityConfig e erros existentes — cobre CA3/CA4/CA6 — prova: testes unitários/MockMvc de principal válido, anônimo, sem escopo, unidade inválida, tentativa por cabeçalho, concorrência e limpeza após exceção.
- [x] T2: Criar mecanismo transacional de contexto e impedir substituição de organização em transação existente — cobre CA2/CA5/CA6 — prova: testes de contexto ausente, transação aninhada, sucesso/rollback e pool de uma conexão contra PostgreSQL real.
- [x] T3: Criar V4 e separar papel operacional de credencial de migração, com preparação idempotente para ambiente local e atualização da configuração/documentação — cobre CA1/CA2/CA7 — prova: IT de permissões efetivas, políticas nas seis tabelas e preparação repetida sem perda de dados.
- [x] T4: Integrar Organizations e FeatureFlags ao contexto, preservar filtros explícitos e provisionamento interno atômico; atualizar todos os chamadores/testes afetados — cobre CA1/CA2/CA5/CA7 — prova: OrganizationsTest/IT e FeatureFlagsIT com A/B, unidade externa, falta de contexto, upsert e rollback.
- [x] T5: Consolidar testes de isolamento em tabelas reais, usando runtime restrito, fixtures administrativas e seed idempotente; nomear os três códigos da issue — cobre CA1/CA2/CA3/CA5/CA6/CA7 — prova: testes IT do pacote isolation, RowLevelSecurityIT e SeedDataIT.
- [x] T6: Rodar regressão do back-end e conferir cobertura/contrato e documentação de execução local/imagem — cobre CA4/CA8 — prova: `./backend/mvnw -f backend/pom.xml verify` (Windows: `backend/mvnw.cmd -f backend/pom.xml verify`), incluindo testes OpenApiContractTest/ContractRoutesTest e JaCoCo 80% global/100% isolation. Provar ambiente local com credencial restrita e seed repetido; registrar limpeza de qualquer recurso Docker descartável usado.

## 6. Decisões

- D1: Este item é infraestrutura de back-end. Não criar rota/tela/handler fictício para cumprir os passos genéricos da issue; alternativa descartada: ampliar contrato sem caso de uso.
- D2: `organization.id` já identifica a própria organização; não duplicar organization_id nessa raiz. Incluir organization_feature porque armazena estado por organização; excluir shedlock e histórico de migração por serem globais. Alternativa: tratar toda tabela indistintamente, sem correspondência com RN-ORG-01.
- D3: A autenticação ainda será implementada na Sprint 02. Entregar contrato tipado de principal confiável e teste com identidade sintética, negando escopo ausente; alternativa descartada: confiar em header/body ou antecipar login e vínculos. Essa integração deverá ser consumida pela autenticação futura; nenhuma rota de negócio existente exige migração de cliente.
- D4 (muda o comportamento): Leituras internas antes recebiam qualquer UUID e instalavam esse tenant; agora argumento nunca concede escopo. Leituras sem contexto retornam vazio, escrita sem contexto ou cruzada é negada; provisionamento interno explícito permanece separado e não acessível por HTTP. Escolha necessária para RN-ORG-02; alternativa descartada: confiar na disciplina de cada chamador.
- D5: Usar conexão operacional sem superusuário/BYPASSRLS e migração administrativa separada; alternativa descartada: demonstrar RLS apenas com SET ROLE em teste mantendo a aplicação como superusuária. Preparação local idempotente é necessária para volumes já existentes e não exige serviço pago.
- D6: Não criar serviço de auditoria, seleção de unidade, matriz completa de perfis ou visão consolidada neste item. Não expor novas escritas operacionais até integrar auditoria; esses módulos têm itens próprios. As alterações de segurança são internas, sem notas de versão para academias nesta fase.

## Construção

Execução delegada pelo maestro, na branch `item/14-organization-isolation`, sem commit ou push. O arquivo deste item já existia sem commit ao iniciar a fase; seu conteúdo de especificação foi preservado.

- T1 — arquivos: `isolation/OrganizationPrincipal`, `OrganizationContext`, `OrganizationScopeFilter`, `config/SecurityConfig`, `OrganizationContextTest` e `OrganizationIsolationIT`. Prova: `backend/mvnw.cmd -f backend/pom.xml verify` → passou; MockMvc cobre anônimo, principal sem escopo, perfis distintos, claims válidas, unidade não autorizada/externa, header/parâmetro sem efeito, requisições concorrentes e limpeza após erro. Controller existe somente no teste.
- T2 — arquivos: `isolation/OrganizationTransactions` e `OrganizationIsolationIT`. Prova: mesmo `verify` → passou com PostgreSQL real, credencial restrita e pool de uma conexão: contexto ausente, transação aninhada, recusa de substituição de tenant, sucesso, rollback, concorrência e ausência de vazamento ao reutilizar conexão.
- T3 — arquivos: V4, `application.yml`, `infra/scripts/prepare-runtime-db.sh`, `infra/.env.example`, READMEs, `AGENTS.md` e `specs/projeto.md`. Prova: mesmo `verify` → passou, incluindo login operacional real sem superusuário/BYPASSRLS nem propriedade das tabelas. `"C:/Program Files/Git/bin/bash.exe" infra/scripts/prepare-runtime-db.sh` executado repetidamente contra o volume local existente → sucesso, sem remoção de dados. Papel descartável privilegiado foi recusado e permaneceu privilegiado até sua remoção explícita; nenhuma demissão silenciosa de papel preexistente.
- T4 — arquivos: `Organizations`, `FeatureFlags`, respectivos ITs e chamadores de teste. Prova: mesmo `verify` → `OrganizationsTest` 2/2, `OrganizationsIT` 5/5, `FeatureFlagsIT` 5/5 passaram. Mantidos provisionamento atômico, defaults, validação, rollback tardio e upsert; leituras sob outro tenant ou sem unidade selecionada não concedem acesso.
- T5 — arquivos: `PostgresIT`, `RowLevelSecurityIT`, `SeedDataIT`, `MigrationIT`, `HealthIT`, `Scopes` e testes de isolamento. Prova: mesmo `verify` → passou; fixtures/migrações usam administrador separado, serviços usam runtime restrito, provas de RLS cobrem as seis tabelas reais e nomeiam RNF-06, RN-ORG-01 e RN-ORG-02. A tabela fictícia de RLS foi retirada. `seed.sh` executado duas vezes localmente → 3 organizações em ambas; `SeedDataIT` 7/7 passou com idempotência e preservação das configurações.
- T6 — prova: `backend/mvnw.cmd -f backend/pom.xml verify` → BUILD SUCCESS, 74 testes rápidos + 29 IT, zero falhas/erros/ignorados; inclui `OpenApiContractTest` e `ContractRoutesTest`. JaCoCo: 408/421 linhas (96,91%) global; 60/60 (100%) em `isolation`; todos os limites passaram. Regressão frontend: `npm run lint && npm run typecheck && npm run build && npm run test:cov && npm run e2e` → sucesso; 121 testes, 97,83% de linhas e 1 E2E crítico. Sem alterações de contrato, MSW ou UI.

Prova local de execução: ambiente compose saudável; aplicação empacotada iniciada em perfil local nas portas de prova 18080/18081 com credenciais padrão separadas; readiness retornou HTTP 200 / UP. Conexão real `ctcore_app` retornou `rolsuper=false`, `rolbypassrls=false` e zero organizações sem contexto; o processo Java usou conexões desse papel. A aplicação de prova foi encerrada. `docker compose -f infra/docker-compose.yml down` removeu somente os três containers locais e a rede criados nesta fase, preservando os volumes. Papel `ctcore_14_privileged_proof` removido; Testcontainers removeu seus containers; nenhuma imagem descartável adicional foi construída/baixada.

Regressão: os comandos completos acima passaram. Saídas em `%TEMP%/ctcore-14-verify3.log` e `%TEMP%/ctcore-14-frontend.log`; relatórios em `backend/target/{surefire-reports,failsafe-reports,site/jacoco}` e `frontend/coverage`.

Desvios: `MigrationIT` e `HealthIT` também precisaram separar fixtures/credenciais após a mudança de configuração. A prova pública MockMvc usa rota exclusiva de teste, pois os endpoints reais de saúde ficam na porta de gestão (cobertos pelo `HealthIT`). Falhas intermediárias de fixture (`enabled` obrigatório), caminho no MockMvc e configuração de credenciais do `HealthIT` foram corrigidas antes da regressão final. Não foi necessário alterar requisitos, decisões ou contrato.

Não é do item: nenhuma mudança externa identificada. Não executado localmente: build de imagem, gitleaks e OSV-Scanner; permanecem no CI do PR. Sem hospedagem/publicação, conforme limitação aceita. Autenticação real e auditoria operacional continuam nos itens responsáveis; nenhuma escrita operacional nova foi exposta.

## Verificação

### Rodada 1 — OK — 2026-10-16, estado a44824730a92266ae59443fe12fa3c1f0bd8871a

Revisão independente em subagente novo, sem participação na construção. Fonte: issue #14, especificação do item, regras citadas, AGENTS.md e diff integral entre o merge-base `50740954b3b38438254b6081cfaaa7b62fde56a5` de HEAD/origin/dev e o estado acima. `git fetch origin dev` executado; snapshot obtido pelo script Bash da skill com índice temporário e reconfirmado antes deste registro. A execução do script PowerShell foi recusada pela política local; não se alterou essa política.

| Critério | Status | Evidência |
|---|---|---|
| CA1 | PASSOU | `OrganizationsIT.rowLevelSecurity` e `RowLevelSecurityIT.isolatesRealFlags` passaram com login operacional real nas seis tabelas; `OrganizationIsolationIT.filtersAndTransactions` passou para filtros de organização/unidade. V3/V4 mantêm USING por organização; consultas dos serviços mantêm WHERE explícito. |
| CA2 | PASSOU | Mesmos ITs exercitam insert cruzado e sem contexto recusado por RLS, update invisível e transferência recusada por WITH CHECK, ausência de linhas e escrita sem contexto. Políticas USING/WITH CHECK inspecionadas nas seis tabelas também regem delete. |
| CA3 | PASSOU | `OrganizationIsolationIT.requestScope` passou com identidade sintética, header/parâmetro de B sem efeito e unidades não autorizadas/externas recusadas; filtro e contexto lidos integralmente, sem consumo de corpo/cabeçalhos/parâmetros para conceder escopo. |
| CA4 | PASSOU | `requestScope` passou: anônimo 401, autenticado sem principal confiável 403, dois perfis com escopo e infraestrutura pública; `HealthIT` passou na porta de gestão real. |
| CA5 | PASSOU | `filtersAndTransactions`, `FeatureFlagsIT` e `OrganizationsIT` passaram: B e unidade alheia ficam inacessíveis sob A; flags cruzadas não são lidas/escritas; ausência de contexto não concede escopo. Métodos não instalam tenant recebido como argumento. |
| CA6 | PASSOU | `OrganizationContextTest` e `OrganizationIsolationIT` passaram: pool de uma conexão, A/B/sem escopo, rollback, transação aninhada, recusa de troca em transação, requisições concorrentes e limpeza após falha. |
| CA7 | PASSOU | `verify` passou com runtime separado de Flyway, rollback inicial/tardio e `SeedDataIT` idempotente. Script de preparação executado duas vezes sobre volume local existente; seed duas vezes manteve 3 organizações. Login real `ctcore_app` mostrou `rolsuper=false`, `rolbypassrls=false`, zero objetos próprios em public e zero organizações sem contexto. Script inspecionado também recusa propriedade/associação privilegiada. |
| CA8 | PASSOU | `backend/mvnw.cmd -f backend/pom.xml verify`: BUILD SUCCESS, 74 testes rápidos e 29 IT, zero falhas/erros/ignorados. Nomes RNF-06/RN-ORG-01/RN-ORG-02 presentes nos testes executados; contratos passaram. JaCoCo: 408/421 linhas (96,91%) global e 60/60 (100%) isolation; todos os limites passaram. Sem rota de produto ou mudança no contrato/MSW. |

Comandos e resultados:

- `backend/mvnw.cmd -f backend/pom.xml verify` — sucesso; relatórios observados em `backend/target/surefire-reports`, `backend/target/failsafe-reports` e `backend/target/site/jacoco/jacoco.xml`.
- Em `frontend/`, `npm.cmd run lint`, `npm.cmd run typecheck`, `npm.cmd run build`, `npm.cmd run test:cov`, `npm.cmd run e2e` — todos passaram; 121 testes, 97,83% de linhas, 1 E2E crítico. A primeira tentativa com `npm` foi recusada pela política de scripts do PowerShell; a execução real usou o launcher `.cmd`.
- `bash -n infra/scripts/prepare-runtime-db.sh` — passou (Bash do Git instalado).
- `docker compose -f infra/docker-compose.yml up -d --wait postgres` — saudável; `prepare-runtime-db.sh` duas vezes e `seed.sh` duas vezes — sucesso. Consultas administrativas e login TCP operacional confirmaram privilégios, ausência de propriedade e leitura vazia sem contexto.
- `git diff --check` e `git diff --check <merge-base> <TREE>` — sem erros; somente avisos de normalização LF/CRLF do Git.
- Limpeza: `docker compose -f infra/docker-compose.yml down` removeu o container `ctcore-postgres-1` e a rede criados nesta revisão, preservando volumes. Testcontainers removeu seus containers; `docker ps` final vazio. Nenhuma imagem de prova construída ou baixada por comando desta revisão.

Comparação com o card: R1–R7 cobrem o objetivo e os três códigos citados. A ausência de CRUD/UI/MSW novos corresponde ao escopo de infraestrutura documentado em D1/D3/D6; autenticação real e auditoria permanecem nos itens próprios, sem nova escrita exposta. Nenhum requisito obrigatório contraditório identificado.

Achados obrigatórios: nenhum. Sugestões: nenhuma.

Não executados nesta revisão: build de imagem, gitleaks e OSV-Scanner (verificações adicionais do CI). Sem hospedagem conforme limitação aceita; integração com autenticação real futura deve consumir o contrato `OrganizationPrincipal` documentado.

## Entrega

Contexto autenticado de organização/unidade, filtros explícitos e contexto local de transação impedem acesso cruzado. A V4 estende RLS às feature flags; runtime PostgreSQL usa papel restrito separado de Flyway, com preparação local idempotente documentada.

Verificação independente: rodada 1 OK, snapshot a44824730a92266ae59443fe12fa3c1f0bd8871a. Regressão registrada: 74 testes rápidos e 29 IT; cobertura global 96,91% e isolation 100%; frontend com lint, tipos, build, 121 testes, cobertura 97,83% e E2E crítico aprovados. Snapshot da entrega comparado sem mudanças de produto posteriores; base origin/dev não avançou.

Autenticação real e auditoria operacional permanecem nos itens próprios; nenhuma rota ou tela nova. Build da imagem e scanners ficam no CI do PR. Merge por squash em dev somente com verificações verdes.

Closes #14
