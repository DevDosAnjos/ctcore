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
| Cobertura (JaCoCo): relatório em `backend/target/site/jacoco/index.html`; o `verify` falha abaixo de 80% de linhas, ou de 100% nos pacotes `billing`, `payment`, `subscription`, `deactivation` e `isolation` | `./backend/mvnw -f backend/pom.xml verify` |
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
| `DB_RUNTIME_USER` | `ctcore_app` |
| `DB_RUNTIME_PASSWORD` | `ctcore_app_dev` |
| `DB_MIGRATION_USER` | valor de `POSTGRES_USER` |
| `DB_MIGRATION_PASSWORD` | valor de `POSTGRES_PASSWORD` |

Antes de iniciar a aplicação ou sua imagem, execute `bash infra/scripts/prepare-runtime-db.sh` da raiz, com PostgreSQL do compose ativo. O script é idempotente e funciona com volumes existentes, sem apagá-los; cria runtime sem superusuário/BYPASSRLS, propriedade ou associação administrativa, e prepara grants existentes e futuros. Ao personalizar credenciais, exporte as variáveis para o script e o Java; este não carrega `infra/.env`. Para a imagem, passe as mesmas variáveis com `docker run -e`. O seed continua administrativo e idempotente.

`isolation/OrganizationPrincipal` é o contrato da autenticação futura: organização, unidade selecionada e unidades autorizadas são verificadas pelo servidor. Headers não concedem escopo. Recursos autenticados em `/api/v1/` sem esse principal recebem 403; infraestrutura pública permanece disponível. `OrganizationContext` dura a requisição; `OrganizationTransactions` usa contexto local à transação e impede troca dentro dela. Sem escopo, leituras ficam vazias e escritas são negadas. Provisionamento interno continua sem HTTP; exposição de escritas depende da auditoria da #15.

## Teste-modelo de isolamento e permissão (RNF-05, RNF-31)

Toda rota nova tem teste de isolamento entre organizações e entre perfis. O molde é `isolation/RouteIsolationModelIT` (classe base, banco real, papel restrito com RLS ativa); `RouteIsolationModelExampleIT` é o exemplo funcional. Para uma rota nova:

1. Crie `<Rota>IsolationIT extends RouteIsolationModelIT` e traga o controller (ou o contexto real) com `@Import`.
2. Implemente os pontos de extensão: `read(alvo)` e `write(alvo)` (requisições com os ids do alvo na URL), `readMissing()` (ids inexistentes), `allowedRoles()` e `deniedRoles()`, `state(alvo)` (estado do recurso lido com `adminJdbc()`) e, se preciso, `secret(alvo)`. `tenant()` cria organização e unidade; `login(alvo, papel)` autentica com o principal da organização.
3. Rode `./backend/mvnw -f backend/pom.xml verify -Dit.test=<Rota>IsolationIT`.

O que cada cenário prova: leitura cruzada (A lê id da B: mesma resposta de id inexistente e nenhum dado da B); alteração cruzada (A altera B: negado e B inalterada); anônimo 401 e sem organização 403, sem efeito; perfil negado 403 sem gravação; perfil permitido grava só na própria organização; `X-Organization-Id` e `organizationId` forjados não mudam o resultado. Os papéis do exemplo são sintéticos até as permissões reais (#27): então só o ponto de extensão dos papéis muda. Retirar o `@PreAuthorize`, o filtro ou a RLS deve fazer um cenário falhar (o exemplo mostra o caso sem `@PreAuthorize`).

## Padrões que todo item segue

- Migrações em `src/main/resources/db/migration` (`V<n>__nome.sql`); nunca editar uma migração já mergeada, corrigir em uma nova. `id uuid primary key default gen_random_uuid()`, datas em `timestamptz` (UTC), valores em centavos (`bigint`).
- Todo erro da API tem o corpo `{ code, message, changed, fields, traceId }` (`api/error`); mensagens em `messages.properties`.
- Tudo é protegido por padrão (`config/SecurityConfig`); senhas com Argon2id; sessão no cookie `SESSION` (o nome do contrato). Mudanças de dado exigem o token CSRF: toda resposta, mesmo um 401 sem sessão, garante o cookie `XSRF-TOKEN` (`config/SpaCsrfTokenRequestHandler`), e o SPA devolve o valor dele no cabeçalho `X-XSRF-TOKEN`.
- Contrato: `OpenApiContractTest` confere erros e páginas contra `docs/openapi.yaml`; `ContractRoutesTest` exige que toda rota de controller esteja sob `/api/v1` e em `paths`, com o mesmo método, e que o cookie e o cabeçalho de CSRF do contrato sejam os do servidor.
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

## Auditoria (RN-AUD-01 a RN-AUD-03)

`audit/AuditService.record(AuditEntry)` é o único caminho de gravação de `audit_log`. Toda operação que altera dado chama o serviço **dentro da mesma transação** (`OrganizationTransactions.execute`): se a operação desfaz, o registro desfaz; sem transação aberta o serviço lança erro. A organização vem de `OrganizationContext` (nunca de parâmetro); só autor `PLATFORM_SUPPORT` ou `PLATFORM_OWNER` grava sem organização.

```java
audit.record(new AuditEntry(new AuditActor(AuditActor.Type.ACCOUNT, accountId, "ADMIN"),
        "student.update", "student", id, unitId, before, after, motivo, ip));
```

Passe o objeto inteiro em `before`/`after`: o serviço guarda só os campos alterados (criação: só `after`; exclusão: só `before`) e remove chaves de senha, token, segredo, chave de API e cartão em qualquer nível (`AuditSanitizer`; chave nova e sensível exige acrescentar a regra e o teste). A tabela é só de inclusão (gatilho recusa update, delete e truncate, também para o usuário de migração) e tem row-level security. O log técnico traz só ação, entidade e id.

## Arquivos por organização (RN-ORG-03)

Para guardar ou entregar um arquivo, injete `storage/FileStorage` e chame `put(nome, bytes, contentType)`, `readUrl(nome)` (URL assinada que expira em 5 minutos) ou `delete(nome)`. A organização vem só do `OrganizationContext`; o objeto fica em `org/<organizationId>/<nome>`. O nome é relativo à pasta (sem `/`, `\`, `..`, controle; até 200 caracteres) e arquivo vazio ou acima de 5 MB é recusado. Outra organização não encontra o arquivo (`FileStorage.NotFound`) e, sem organização no contexto, tudo é negado. Não há URL pública permanente: guarde só o nome e gere a URL a cada leitura.

| Propriedade (`ctcore.storage.*`) | Variável | Padrão (compose) |
| --- | --- | --- |
| `endpoint` | `STORAGE_ENDPOINT` | `http://localhost:${STORAGE_PORT:8333}` |
| `bucket` | `STORAGE_BUCKET` | `ctcore` |
| `region` | `STORAGE_REGION` | `us-east-1` |
| `access-key` / `secret-key` | `STORAGE_ACCESS_KEY` / `STORAGE_SECRET_KEY` | `dev` / `dev-secret` (fictícios; credenciais em `infra/storage/s3.json`) |
| `url-expiry` | `STORAGE_URL_EXPIRY` | `5m` |

O bucket é criado ao subir; se o armazenamento estiver fora do ar, a aplicação sobe e registra um aviso.

## Conexões externas (portas e versões simuladas)

Cada conexão externa é uma interface em `br.com.ctcore.integration`; nenhuma regra cita fornecedor (RNF-39). Hoje só existem a versão simulada e a desligada; os adaptadores reais (únicos que podem citar fornecedor, em `integration/real/`) vêm nas Sprints 22 e 23.

| Porta | Simulada | Desligada (`isEnabled()` falso) |
| --- | --- | --- |
| `PaymentGateway` | cobrança em memória, idempotente | operações lançam `IllegalStateException`: consulte `isEnabled()` antes |
| `EmailSender`, `SmsSender`, `PushSender` | grava na Caixa de saída, devolve `SENT` | devolve `NOT_SENT`, nada é gravado |
| `ChallengeVerifier` | sempre aprova | aprova |
| `MapLocator` | coordenadas fixas para alguns CEPs, vazio para os demais | vazio |
| `UsageMetrics` | não envia nada | não faz nada |

**Configuração.** Uma chave por conexão, `ctcore.integrations.<conexão>.mode` (ou a variável de ambiente `CTCORE_INTEGRATIONS_<CONEXÃO>_MODE`), com `simulated` (padrão) ou `off`:

`ctcore.integrations.payment.mode`, `.email.mode`, `.sms.mode`, `.push.mode`, `.challenge.mode`, `.map.mode`, `.metrics.mode`.

`real` e qualquer outro valor derrubam a subida com mensagem que cita a chave e os valores aceitos; não há troca silenciosa para a simulada. Quem usa uma conexão consulta `isEnabled()` e segue o "Sem esta conexão" da própria funcionalidade.

**Caixa de saída** (`Outbox`). Guarda e-mail, SMS e push simulados (tipo, destinatário, assunto ou texto, data/hora do `Clock`; o SMS traz o código de confirmação). Em memória: some no reinício, não é compartilhada entre instâncias, guarda no máximo 1000 mensagens (as mais antigas saem) e só é preenchida no modo simulado. O log registra só tipo e id, nunca o texto.

**Simulador de pagamento.** `createCharge(chave, centavos)` é idempotente (mesma chave, mesma cobrança; outra quantia com a mesma chave lança `IllegalArgumentException`). Estados: `PENDING`, `APPROVED`, `REFUSED`, `EXPIRED`, `REFUNDED`, `DISPUTED`. `approve`, `refuse` e `expire` saem de `PENDING`; `refund` e `dispute` saem de `APPROVED`; outra transição lança `IllegalStateException` sem mudar o estado; cobrança inexistente lança `NoSuchElementException`. `resendNotice` repete o evento atual (id, estado, valor) sem alterá-lo.

## Imagem Docker

`docker build -t ctcore-backend backend` (Dockerfile multi-stage, jar sem testes, JRE 21, usuário sem privilégio). Sobe só com variáveis de ambiente: `docker run --rm -e DB_HOST=host.docker.internal -p 8080:8080 -p 8081:8081 ctcore-backend` com o PostgreSQL do compose no ar. A API fica em `8080`; `/actuator/health` e `/actuator/health/readiness` ficam na porta de gestão `8081` (D8), que não deve ser publicada para a internet. O CI constrói esta imagem quando a etapa `backend` roda. Publicação: README da raiz.
