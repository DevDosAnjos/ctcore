# 4 — Agendador de tarefas com trava e recursos por organização

Origem: issue #4 (Sprint 00 · Fundação). Branch: `item/4-agendador-tarefas`.

## 1. Objetivo

Entregar duas bases do back-end: (a) agendador de tarefas em que, com vários servidores no ar, só um executa cada tarefa por vez (`00-visao-geral.md` §9, RF-INA-03); (b) mecanismo de recursos (feature flags) ligados por organização, que nascem desligados e podem ser ligados ou desligados sem novo deploy (RN-PNL-07). Serve às tarefas diárias (#111, cobranças, lembretes) e às telas de recursos da Sprint 15.

Fora do escopo: tarefa de negócio real, tela e rota para ligar recursos (Sprint 15), auditoria (#17), tabela de organização e RLS (#13, #14), logs e métricas das tarefas (#5), CI (#10).

## 2. Requisitos

- R1: o agendador está ligado na aplicação: método com `@Scheduled` (cron com fuso explícito) executa no horário (D1).
- R2: com a trava, uma tarefa agendada executa em um servidor só por disparo, mesmo com várias instâncias da aplicação contra o mesmo banco (D2).
- R3: a trava expira sozinha se o servidor que a tem cair, e a tarefa volta a rodar no disparo seguinte (D2).
- R4: falha dentro de uma tarefa não derruba o agendador nem deixa a trava presa: a trava é solta e a próxima execução ocorre.
- R5: o agendador pode ser desligado por propriedade (`ctcore.scheduler.enabled`, padrão ligado) para testes e para um servidor que não deva rodar tarefas (D3).
- R6: existe a tabela de recursos por organização (migração), com uma linha por organização e recurso e a data da última mudança (D4).
- R7: `FeatureFlags.isEnabled(organizationId, key)` devolve `false` quando não há linha (recurso novo nasce desligado, RN-PNL-07) e o valor gravado quando há (D5).
- R8: `FeatureFlags.setEnabled(organizationId, key, enabled)` liga ou desliga para uma organização; a mudança vale na próxima consulta, sem reiniciar nem novo deploy (RN-PNL-07) (D6).
- R9: o recurso de uma organização não afeta outra (isolamento entre organizações no mecanismo).
- R10: chave de recurso inválida (fora de `[a-z][a-z0-9-]*`, até 64 caracteres) ou organização nula é recusada com erro de argumento, sem gravar.
- R11: `backend/README.md` documenta a propriedade `ctcore.scheduler.enabled`, e como criar uma tarefa com trava e um recurso.

## 3. Critérios de aceite

- CA1 (R1, R5): dado o contexto de teste com o agendador ligado e uma tarefa de teste com cron a cada segundo, quando se espera alguns segundos, então ela executa ao menos uma vez; com `ctcore.scheduler.enabled=false`, não executa e não há bean de agendamento.
- CA2 (R2): dado o PostgreSQL do Testcontainers e dois contextos (duas instâncias) com a mesma tarefa de execução longa, quando o horário dispara nas duas ao mesmo tempo, então só uma conta a execução naquele disparo.
- CA3 (R3): dado um registro de trava com `lock_until` no futuro, deixado por "servidor caído", quando passa o `lockAtMostFor` (ou o teste move `lock_until` para o passado), então a tarefa executa de novo.
- CA4 (R4): dada uma tarefa de teste que lança exceção no primeiro disparo, quando o segundo disparo chega, então ela executa de novo e a trava está solta entre os dois.
- CA5 (R6): dado o banco migrado, quando se consulta o catálogo, então a tabela de recursos e a tabela da trava existem (V2 com `success = true`).
- CA6 (R7): dada uma organização sem linha, quando se chama `isEnabled`, então o resultado é `false`.
- CA7 (R8): dada uma organização com recurso ligado, quando `setEnabled(org, key, false)` é chamado, então a próxima `isEnabled` devolve `false` na mesma instância e em outra instância da aplicação, sem reinício; repetir a mesma chamada não duplica linha e atualiza a data da mudança.
- CA8 (R9): dadas as organizações A e B, quando o recurso é ligado só para A, então `isEnabled(A)` é `true` e `isEnabled(B)` é `false`; o mesmo recurso em outra chave não muda.
- CA9 (R10): dada uma chave `"Invalid Key"`, vazia, com mais de 64 caracteres ou organização nula, quando `setEnabled` ou `isEnabled` é chamado, então lança `IllegalArgumentException` e nada é gravado.
- CA10 (R11): dado o `backend/README.md`, quando se lê, então traz a propriedade e o exemplo de tarefa com trava e de recurso.

## 4. Plano técnico

- Dados: migração `backend/src/main/resources/db/migration/V2__scheduler_lock_and_features.sql` (a criar): tabela `shedlock` (`name varchar(64) primary key`, `lock_until timestamptz not null`, `locked_at timestamptz not null`, `locked_by varchar(255) not null`) e tabela `organization_feature` (`organization_id uuid not null`, `feature_key varchar(64) not null`, `enabled boolean not null`, `updated_at timestamptz not null default now()`, `primary key (organization_id, feature_key)`). Sem chave estrangeira para organização e sem RLS, porque a tabela de organização é da #13 e a política de acesso é da #14: a #13 acrescenta a chave estrangeira e a #14 decide a política (a tabela é de configuração da plataforma, escrita pela equipe e não por quem usa a organização). Migração nova, V1 intocada; não destrutiva.
- API: não muda (sem rota; a rota de ligar recursos é da Sprint 15 e entra no OpenAPI nessa hora).
- Interface: não muda.
- Segurança: sem rota e sem dado pessoal. `setEnabled` é método interno: quando a rota da Sprint 15 existir, ela confere o perfil Dono no servidor e grava auditoria (#17); isso é regra daquela rota, citada aqui para não se perder. Chave validada por padrão fixo e consulta parametrizada (`JdbcClient`). A trava usa o relógio do banco, para servidores com horas diferentes não furarem a trava.
- Arquivos: a alterar (existem): `backend/pom.xml` (ShedLock: `shedlock-spring` e `shedlock-provider-jdbc-template`, versão fixada e confirmada no Maven Central, pois o BOM do Spring Boot não gerencia), `backend/README.md`, `backend/src/test/java/br/com/ctcore/security/SessionCookieCsrfTest.java` (sobe sem banco: passa `ctcore.scheduler.enabled=false`). A criar: `backend/src/main/java/br/com/ctcore/scheduling/SchedulerConfig.java` (`@EnableScheduling`, `@EnableSchedulerLock(defaultLockAtMostFor = "PT10M")`, bean `LockProvider` com `usingDbTime()`, condicionado à propriedade); `backend/src/main/java/br/com/ctcore/features/FeatureFlags.java` (`isEnabled`, `setEnabled`, usa `JdbcClient`); a migração acima; testes em `backend/src/test/java/br/com/ctcore/`: `scheduling/SchedulerLockIT` (duas instâncias, expiração, falha, desligado) e `features/FeatureFlagsIT` (e a validação de chave em teste rápido `features/FeatureFlagKeyTest`, se a validação ficar em método estático).
- Reuso: `PostgresIT` (base Testcontainers) e `JdbcClient`/`DataSource` já presentes via `spring-boot-starter-data-jpa`; padrões de migração da #2 (uuid, `timestamptz`); `Clock` (`ClockConfig`) para a data da mudança em `setEnabled` (usar `Instant` do `Clock`, não `now()` no código).
- Riscos: (1) `MigrationIT` confere só a V1; conferir que a V2 não o quebra. (2) Sem organização cadastrada (#13) a linha pode apontar para organização inexistente até a chave estrangeira existir; mitigação: ninguém chama `setEnabled` antes da #13 além dos testes. (3) Duas instâncias no teste: o segundo contexto precisa de porta própria e do mesmo banco; se ficar pesado, simular a segunda instância com um segundo `LockProvider` sobre o mesmo `DataSource`, que prova a mesma trava. (4) Uma consulta ao banco por verificação de recurso: sem cache até a #112 mostrar necessidade; cache traria atraso na mudança (conflita com R8). (5) O agendador padrão do Spring tem um thread; tarefas longas atrasam outras: ajustar `spring.task.scheduling.pool.size` quando houver mais de uma tarefa real.

## 5. Tarefas

- [x] T1: acrescentar ao `pom.xml` as dependências do ShedLock (versão fixada, confirmada no Maven Central) — preparação, sem critério próprio — prova: `./backend/mvnw -f backend/pom.xml -q compile`.
- [x] T2: criar `V2__scheduler_lock_and_features.sql` com as duas tabelas — cobre CA5 — prova: `MigrationIT` continua verde e o teste de CA5 confere a V2 e as tabelas.
- [x] T3: criar `SchedulerConfig` (agendamento, `LockProvider` com relógio do banco, propriedade `ctcore.scheduler.enabled`) e ajustar `SessionCookieCsrfTest` — cobre CA1 — prova: `SchedulerLockIT` (executa; desligado não executa) e `SessionCookieCsrfTest` verde.
- [x] T4: testes da trava com tarefa de teste anotada com `@Scheduled` e `@SchedulerLock`: duas instâncias, expiração e falha — cobre CA2, CA3, CA4 (RNF-30) — prova: `SchedulerLockIT` verde com Docker ativo.
- [x] T5: criar `FeatureFlags` (consulta, gravação com upsert, validação de chave e organização) — cobre CA6, CA7, CA8, CA9 — prova: `FeatureFlagsIT` e teste rápido de validação (RNF-29, RNF-30).
- [x] T6: documentar no `backend/README.md` a propriedade, como criar tarefa com trava e como declarar e consultar um recurso — cobre CA10 — prova: leitura do arquivo e `./backend/mvnw -f backend/pom.xml verify` verde.

## 6. Decisões

- D1: agendador do próprio Spring (`@Scheduled`), com `zone` no cron quando a regra pedir fuso. Motivo: já vem no Spring Boot, sem dependência nem processo extra. Alternativa: Quartz, descartada por trazer tabelas e configuração demais para tarefas diárias.
- D2: trava em tabela no PostgreSQL com ShedLock, expiração por `lockAtMostFor` e relógio do banco. Motivo: o banco já é compartilhado pelos servidores, a trava expira se o servidor cair e é conhecida e testada. Alternativa: `pg_advisory_xact_lock`, sem dependência nem tabela, descartada porque prende uma conexão e uma transação durante toda a tarefa e não tem expiração visível; se o ShedLock der problema, troca-se sem mudar quem cria tarefas.
- D3: propriedade `ctcore.scheduler.enabled`, padrão ligado. Motivo: testes e um servidor sem tarefas precisam desligar. Alternativa: sempre ligado, descartada porque os testes de contexto passariam a disparar tarefas.
- D4: recursos numa tabela própria `organization_feature` (organização, chave, ligado), não numa coluna ou JSON da organização. Motivo: a tabela da organização é da #13 e recurso novo não pede migração da organização. Alternativa: coluna JSON na organização, descartada por depender da #13.
- D5 (muda o comportamento): recurso sem linha para a organização está desligado, e não existe padrão global ligado. Motivo: RN-PNL-07 (recursos novos nascem desligados). Alternativa: valor padrão por recurso no código, descartada porque nenhum recurso exige hoje; entra no item que criar o primeiro recurso que nasça ligado, se existir.
- D6: a chave do recurso é um texto livre validado (`[a-z][a-z0-9-]*`, até 64), com uma constante em código por recurso, definida pelo item que o cria. Motivo: sem lista central, o mecanismo existe antes de qualquer recurso. Consequência: chave digitada errada lê como desligada, sem erro; a constante evita o erro no uso interno. Alternativa: enum central, descartado porque ficaria vazio agora e viraria ponto de conflito entre itens.
- D7: sem auditoria e sem rota neste item. Motivo: não há ação do usuário que altere recurso até a Sprint 15 e o serviço de auditoria é da #17; a rota que chamar `setEnabled` grava a auditoria (regra do `AGENTS.md`). Alternativa: gravar auditoria já aqui, descartada por depender da #17.
- Suposição: Docker está disponível para `SchedulerLockIT` e `FeatureFlagsIT`; sem ele CA2 a CA9 ficam sem prova executada.
- Suposição: o dono do recurso é a equipe da plataforma (Dono), como diz `17-painel-da-plataforma.md`; se o Coordenador também puder ligar recursos, muda a regra de permissão da rota da Sprint 15, não este mecanismo.

## Construção

- T1 — arquivos: `backend/pom.xml` — prova: `./backend/mvnw -f backend/pom.xml verify` → compila e resolve ShedLock 6.10.0 (BUILD SUCCESS)
- T2 — arquivos: `backend/src/main/resources/db/migration/V2__scheduler_lock_and_features.sql` — prova: `MigrationIT` verde (4 testes) e `FeatureFlagsIT.v2Applied` verde
- T3 — arquivos: `scheduling/SchedulerConfig.java`, `SessionCookieCsrfTest.java` (`ctcore.scheduler.enabled=false`), `scheduling/SchedulerDisabledTest.java` — prova: `SchedulerLockIT.scheduledTaskRuns`, `SchedulerDisabledTest`, `SessionCookieCsrfTest` verdes
- T4 — arquivos: `scheduling/SchedulerLockIT.java` — prova: `SchedulerLockIT` 4 testes verdes com Docker (CA2 e CA3 com dois `LockProvider` sobre o mesmo banco, conforme risco 3; CA4 com a tarefa que falha no 1º disparo e executa de novo)
- T5 — arquivos: `features/FeatureFlags.java`, `features/FeatureFlagsIT.java` — prova: `FeatureFlagsIT` 5 testes verdes (CA5 a CA9)
- T6 — arquivos: `backend/README.md` — prova: leitura do arquivo e `verify` verde
Regressão: `./backend/mvnw -f backend/pom.xml verify` → BUILD SUCCESS, 25 testes rápidos e 14 de integração, sem falhas.
Desvios: (1) ShedLock 6.10.0, não 7.x: a 7.x é para Spring 7; a 6.x é a linha do Spring Boot 3.5. (2) "Desligado não executa" ficou em `SchedulerDisabledTest` (sem banco), não em `SchedulerLockIT`. (3) CA2 e CA3 provados com dois `LockProvider` no mesmo banco, não com dois contextos (risco 3 do plano). (4) `FeatureFlags` recebe `JdbcClient` com `@Lazy`, para a aplicação subir nos testes sem banco. (5) `FeatureFlagKeyTest` não foi criado: a validação é coberta por `FeatureFlagsIT.invalid`.
Não é do item: nada.

## Verificação

### Rodada 1 — OK — 2026-10-15, estado 79e82f1c0aab9bfe25582c6ff719c8dc8ec66382

| Critério | Status | Evidência |
|---|---|---|
| CA1 | PASSOU | `SchedulerLockIT.scheduledTaskRuns` e `SchedulerDisabledTest` verdes (desligado: tarefa não executa, sem `LockProvider`) |
| CA2 | PASSOU | `SchedulerLockIT.onlyOneInstanceGetsLock`: dois `LockProvider` no mesmo banco, só um obtém a trava (desvio aceito, risco 3) |
| CA3 | PASSOU | `SchedulerLockIT.staleLockExpires`: com `lock_until` no passado a trava é obtida de novo |
| CA4 | PASSOU | `SchedulerLockIT.failureReleasesLock`: tarefa que falha no 1º disparo executa de novo |
| CA5 | PASSOU | `FeatureFlagsIT.v2Applied`; `MigrationIT` verde |
| CA6 | PASSOU | `FeatureFlagsIT.defaultOff` |
| CA7 | PASSOU | `FeatureFlagsIT.toggle` (sem duplicar, data atualizada; sem cache, mesmo banco) |
| CA8 | PASSOU | `FeatureFlagsIT.isolation` |
| CA9 | PASSOU | `FeatureFlagsIT.invalid` |
| CA10 | PASSOU | `backend/README.md` traz propriedade, tarefa com trava e recurso |

Comandos: `./backend/mvnw -f backend/pom.xml verify` (Docker ativo) → BUILD SUCCESS, 25 testes rápidos e 14 de integração, 0 falhas.
Achados:
- sugestão, código, `SchedulerLockIT` CA2/CA3 — provam o `LockProvider`, não o `@Scheduled` em duas instâncias nem a re-execução da tarefa após expirar.
- sugestão, código, `FeatureFlagsIT.toggle` — CA7 "em outra instância" não é exercido explicitamente (equivale ao mesmo banco, sem cache).
- sugestão, código, `FeatureFlagsIT.toggle` — `Thread.sleep(10)` pode ser frágil se o relógio tiver resolução baixa.

## Entrega

- Agendador com trava distribuída (ShedLock sobre PostgreSQL, tabela criada na migração V2): com vários servidores, só um executa cada tarefa por vez; desligado por propriedade; trava expira e é liberada em falha.
- Recursos (feature flags) por organização: nascem desligados, ligar/desligar sem deploy, sem cache, com isolamento entre organizações e validação de entrada.
- Verificação: `./backend/mvnw -f backend/pom.xml verify` com Docker, 25 testes rápidos e 14 de integração, 0 falhas (rodada 1 OK).
- Limitações: testes de trava provam o `LockProvider`, não o `@Scheduled` em duas instâncias; sem tela nem rota (Sprint 15); tabela de organização e RLS ficam para #13 e #14.
