# backend

API do ctcore: Java 21, Spring Boot 3, Maven, Flyway e PostgreSQL com row-level security. A API REST fica em `/api/v1` e segue o OpenAPI de `../docs/`.

## Requisitos

- Java 21. O Maven vem junto pelo wrapper (`./mvnw`; no Windows, `mvnw.cmd`).
- Docker ativo: os testes de integração (`*IT`) sobem um PostgreSQL 16 com Testcontainers, e rodar a aplicação usa o banco do `../infra/`.

## Comandos

Todos a partir da raiz do repositório.

| O que | Comando |
| --- | --- |
| Compilar | `./backend/mvnw -f backend/pom.xml compile` |
| Testes rápidos (`*Test`, sem Docker) | `./backend/mvnw -f backend/pom.xml test` |
| Tudo: testes rápidos e de integração | `./backend/mvnw -f backend/pom.xml verify` |
| Rodar com o perfil `local` | `docker compose -f infra/docker-compose.yml up -d --wait` e depois `./backend/mvnw -f backend/pom.xml spring-boot:run -Dspring-boot.run.profiles=local` |

O perfil `local` só desliga o redirecionamento para HTTPS; fora dele a aplicação exige HTTPS (confia em `X-Forwarded-Proto`).

## Variáveis de ambiente

Os padrões servem ao ambiente local de `../infra/` (valores de desenvolvimento, não são segredo). Em qualquer outro ambiente, defina por variável de ambiente.

| Variável | Padrão |
| --- | --- |
| `DB_HOST` | `localhost` |
| `POSTGRES_PORT` | `5432` |
| `POSTGRES_DB` | `ctcore` |
| `POSTGRES_USER` | `ctcore` |
| `POSTGRES_PASSWORD` | `ctcore_dev` |

## Padrões que todo item segue

- Migrações em `src/main/resources/db/migration` (`V<n>__nome.sql`); nunca editar uma migração já mergeada, corrigir em uma nova. `id uuid primary key default gen_random_uuid()`, datas em `timestamptz` (UTC), valores em centavos (`bigint`).
- Todo erro da API tem o corpo `{ code, message, changed, fields, traceId }` (`api/error`); mensagens em `messages.properties`.
- Tudo é protegido por padrão (`config/SecurityConfig`); senhas com Argon2id; mudanças de dado exigem o token CSRF (cookie `XSRF-TOKEN`, cabeçalho `X-XSRF-TOKEN`).
- Testes: `*Test` rodam no surefire; `*IT` (com Docker) rodam no failsafe, na fase `verify`.

## Tarefas agendadas e recursos

**Agendador.** `@Scheduled` do Spring, com trava no PostgreSQL (ShedLock, tabela `shedlock`, relógio do banco): com vários servidores, só um executa cada tarefa por disparo. A trava expira sozinha (`lockAtMostFor`, padrão 10 min) se o servidor cair. Propriedade `ctcore.scheduler.enabled` (padrão `true`): em `false` não há agendamento nem trava (testes sem banco, ou servidor que não deve rodar tarefas).

Criar uma tarefa com trava (o `name` é único por tarefa; use fuso explícito no cron):

```java
@Component
class DailyJob {
    @Scheduled(cron = "0 0 3 * * *", zone = "America/Sao_Paulo")
    @SchedulerLock(name = "daily-job", lockAtMostFor = "PT30M")
    void run() { /* ... */ }
}
```

O agendador padrão tem um thread: ao ter mais de uma tarefa real, ajuste `spring.task.scheduling.pool.size`.

**Recursos por organização** (`features/FeatureFlags`, tabela `organization_feature`). Sem linha, o recurso está desligado (RN-PNL-07). Cada recurso tem uma constante de chave (`[a-z][a-z0-9-]*`, até 64) no item que o cria; não há cache, a mudança vale na próxima consulta.

```java
static final String ONLINE_PAYMENT = "online-payment";

if (featureFlags.isEnabled(organizationId, ONLINE_PAYMENT)) { /* ... */ }
featureFlags.setEnabled(organizationId, ONLINE_PAYMENT, true); // só a rota do Dono, com auditoria
```

## Logs e monitoramento

**Formato e retenção (RNF-11).** Os logs saem em JSON (formato `logstash` do Spring Boot), uma linha por evento, no console e no arquivo `logs/ctcore.log` (pasta pela variável `LOG_DIR`; ignorada pelo git). O arquivo gira todo dia (`ctcore-AAAA-MM-DD.N.log.gz`) e guarda 30 dias. Cada linha traz `@timestamp`, `level`, `logger_name`, `message` e `traceId`, o mesmo do cabeçalho `X-Trace-Id` e do corpo do erro. Para ler no terminal: `jq . logs/ctcore.log`.

**Dado sensível nunca vai para o log.** `logging/SensitiveDataMasker` troca por `[REDACTED]`, na mensagem, nos argumentos, no MDC e na pilha de exceção: senha, token (`token=`, `Bearer ...`), CPF completo e número de cartão. A aplicação também nunca loga cabeçalhos, corpo nem query string de requisição. Ao logar um dado novo e sensível, acrescente a regra em `SensitiveDataMasker` e o teste em `SensitiveDataMaskerTest`.

**Saúde e métricas.** Ficam na porta de gestão (`MANAGEMENT_PORT`, padrão `8081`), fora de `/api/v1` e do OpenAPI; essa porta não deve ser publicada para a internet. Só estas rotas existem; as demais do Actuator respondem 404.

| Rota | O que dá |
| --- | --- |
| `/actuator/health` | saúde da aplicação, sem detalhes |
| `/actuator/health/liveness` | processo vivo |
| `/actuator/health/readiness` | pronta para atender (inclui o banco; 503 se cair) |
| `/actuator/prometheus` | métricas em formato Prometheus: `http_server_requests_seconds_*` (erros por `status`, lentidão por `_bucket`), JVM e pool de conexões |

**Métricas de negócio** (`monitoring/OperationalMetrics`, valem 0 até alguém chamar). Injete o bean e chame: `emailQueue(long pendentes)` informa a fila de e-mail (`ctcore_email_queue_pending`); `webhookFailed()` conta uma falha de webhook (`ctcore_webhook_failures_total`). Nunca ponha dado pessoal em nome ou rótulo de métrica.
