# 5 — Logs estruturados e métricas de monitoramento

Origem: issue #5 (Sprint 00 · Fundação), RNF-11 e RNF-18 (parte técnica) em `docs/especificacao/00-visao-geral.md`. Branch: `item/5-logs-metricas`.

## 1. Objetivo

Dar ao back-end logs em JSON, sem dado sensível e guardados por 30 dias (RNF-11), com o identificador da requisição em cada linha, e expor métricas e verificação de saúde num formato que um serviço de monitoramento consiga ler (erros, lentidão, fila de e-mail, falhas de webhook). Serve a todos os itens seguintes, que passam a logar sem se preocupar com vazamento, e à #193 (alertas).

Fora do escopo: ligar alertas ou contratar monitoramento (RNF-16, RNF-18, #193); fila de e-mail real (#19) e webhook real (#96), que só passam a alimentar as métricas que este item cria; hospedagem e retenção na infraestrutura (#192); painel ou tela; auditoria (#17).

## 2. Requisitos

- R1: os logs saem em JSON, uma linha por evento, com data/hora, nível, logger, mensagem e `traceId` da requisição (D1, D2).
- R2: nenhum log contém senha, token, CPF completo ou dado de cartão, na mensagem, nos argumentos ou no texto de exceção/pilha (RNF-11) (D3).
- R3: o `traceId` do log é o mesmo do cabeçalho `X-Trace-Id` e do corpo do erro da API, e não vaza para a requisição seguinte da mesma thread (D2).
- R4: a aplicação nunca registra cabeçalhos (`Authorization`, `Cookie`), corpo ou query string de requisição (RNF-11).
- R5: os logs são gravados também em arquivo com rotação diária e guarda de 30 dias (RNF-11) (D4).
- R6: `/actuator/health` responde a saúde da aplicação e do banco, com `liveness` e `readiness`, sem expor detalhes (D5).
- R7: `/actuator/prometheus` expõe as métricas de erros e lentidão das requisições (`http.server.requests`, com faixas de tempo), JVM e pool de conexões (D6).
- R8: existem as métricas `ctcore.email.queue.pending` (fila de e-mail) e `ctcore.webhook.failures` (falhas de webhook), valendo 0 até os itens #19 e #96 as alimentarem (D7).
- R9: só `health` e `prometheus` ficam expostos; as demais rotas do Actuator (`env`, `beans`, `heapdump`, `metrics`…) respondem 404 (D5).
- R10: as métricas e a saúde ficam fora de `/api/v1` e fora do OpenAPI, numa porta de gestão própria; sem dado pessoal em nome ou rótulo de métrica (D5, D8).
- R11: `backend/README.md` documenta formato e retenção dos logs, a regra de não logar dado sensível, as rotas de saúde e métricas e a porta de gestão.

## 3. Critérios de aceite

- CA1 (R1): dado o log de uma requisição que passou por `TraceIdFilter`, quando uma linha é emitida, então é um JSON válido com campos de data/hora, nível, logger, mensagem e `traceId`.
- CA2 (R2): dados os logs com senha (`password=...`), token (`Bearer ...`, `token=...`), CPF completo (com e sem pontuação) e número de cartão na mensagem, num argumento e na mensagem de uma exceção com pilha, quando o JSON é gerado, então nenhum desses valores aparece no texto final.
- CA3 (R2): dado um log legítimo sem dado sensível (inclui um número curto, como `id=12345`, e um UUID), quando o JSON é gerado, então o texto sai intacto.
- CA4 (R3): dada uma requisição a uma rota de teste que loga, quando responde, então o `traceId` da linha é igual ao `X-Trace-Id` da resposta; e uma segunda requisição na mesma thread não carrega o `traceId` da primeira.
- CA5 (R3): dado um erro 500 provocado na rota de teste, quando responde, então o `traceId` do corpo do erro, o do cabeçalho e o da linha de log de erro são o mesmo.
- CA6 (R4): dada uma requisição com `Authorization`, `Cookie` e query string com segredo, quando passa pela aplicação (inclusive com erro 401, 403 e 500), então nenhum log contém esses valores.
- CA7 (R5): dada a configuração padrão, quando lida, então o arquivo de log tem nome fixo configurável, padrão de rotação com data diária e `max-history` igual a 30, em formato JSON.
- CA8 (R6): dado o contexto com banco (Testcontainers), quando se chama `/actuator/health`, `/actuator/health/liveness` e `/actuator/health/readiness`, então todos respondem 200 com `status: UP` e sem campo de detalhes; com o banco parado, `readiness` responde 503.
- CA9 (R7): dada a aplicação que atendeu uma requisição com sucesso e uma com erro 500, quando se lê `/actuator/prometheus`, então a resposta traz `http_server_requests_seconds` com o `status` 200 e 500, e as faixas de tempo (`_bucket`).
- CA10 (R8): dada a aplicação recém-iniciada, quando se lê `/actuator/prometheus`, então `ctcore_email_queue_pending` e `ctcore_webhook_failures_total` aparecem com valor 0; depois de `webhookFailed()` e `emailQueue(3)` os valores são 1 e 3.
- CA11 (R9): dado qualquer pedido a `/actuator/env`, `/actuator/beans`, `/actuator/heapdump`, `/actuator/metrics` e `/actuator/loggers`, quando respondido, então o resultado é 404.
- CA12 (R10): dado o contexto subido com a porta de gestão separada, quando se chama `/actuator/health` na porta principal, então não é atendido (404 ou 401, nunca 200); `/api/v1` continua exigindo autenticação; e a saída do Prometheus não contém UUID, e-mail nem CPF em nome ou rótulo.
- CA13 (R11): dado o `backend/README.md`, quando se lê, então traz formato e retenção dos logs, a regra de dado sensível, as rotas de saúde e métricas, a porta de gestão e como usar as duas métricas de negócio.

## 4. Plano técnico

- Dados: não muda (sem migração; a saúde só consulta o banco).
- API: não muda `/api/v1` nem `docs/openapi.yaml`. Novas rotas operacionais fora do contrato, na porta de gestão: `GET /actuator/health[/liveness|/readiness]` e `GET /actuator/prometheus` (D5, D8). Erros e paginação não se aplicam.
- Interface: não muda.
- Segurança: rotas de gestão só na porta de gestão (não publicada pelo `docker-compose`/proxy), sem detalhes de saúde, sem `env`/`beans`/`heapdump`/`loggers`. O filtro de segurança já protege tudo por padrão (`SecurityConfig`); liberar só `EndpointRequest.to("health", "prometheus")` e isentar essas rotas do redirecionamento HTTPS, para a sonda interna funcionar sem TLS. Mascaramento de dado sensível no próprio formatador do log (D3), cobrindo mensagem, argumentos, exceção e pilha. Sem dado pessoal em rótulo: `http.server.requests` usa o padrão da rota (`/api/v1/x/{id}`), nunca o valor. Isolamento entre organizações e entre perfis: não se aplica, pois as rotas não têm dado de organização nem perfil; o teste de "rota nova" é o CA11/CA12 (nada além do liberado responde).
- Arquivos: a alterar (existem): `backend/pom.xml` (acrescentar `spring-boot-starter-actuator` e `micrometer-registry-prometheus`, ambos gerenciados pelo BOM do Boot, sem versão), `backend/src/main/resources/application.yml` (formato estruturado, arquivo com rotação, endpoints, porta de gestão, histograma de `http.server.requests`), `backend/src/main/resources/application-local.yml` (se o log local deve voltar a texto legível, D2), `backend/src/main/java/br/com/ctcore/api/error/TraceIdFilter.java` (colocar e limpar `traceId` no MDC; ajustar o comentário "Logs correlacionados: #5"), `backend/src/main/java/br/com/ctcore/config/SecurityConfig.java` (liberar as duas rotas e isentá-las de HTTPS), `backend/README.md`, `.gitignore` (pasta de logs, se faltar). A criar: `backend/src/main/java/br/com/ctcore/logging/SensitiveDataMasker.java` (regras de mascaramento e o `StructuredLoggingJsonMembersCustomizer` que as aplica), `backend/src/main/java/br/com/ctcore/monitoring/OperationalMetrics.java` (D7); testes em `backend/src/test/java/br/com/ctcore/`: `logging/SensitiveDataMaskerTest`, `logging/LogOutputTest`, `monitoring/ActuatorEndpointsTest` (sem banco, `RANDOM_PORT`), `monitoring/HealthIT` (com `PostgresIT`) e `monitoring/OperationalMetricsTest`.
- Reuso: `TraceIdFilter` (o identificador já existe), `SecurityConfig`, `PostgresIT` para a saúde com banco, `SessionCookieCsrfTest` como modelo de contexto sem banco (propriedades `ctcore.scheduler.enabled=false` e exclusão das autoconfigurações de banco); `ApiExceptionHandler` já loga erros com a exceção, e é o caminho que o mascaramento da pilha protege. Registro de logs estruturados nativo do Spring Boot 3.5 (`logging.structured.format`), sem dependência de encoder de terceiros.
- Riscos: (1) a API de personalização de membros do log estruturado do Boot 3.5 deve ser confirmada na construção; se não alcançar a pilha, a alternativa é um `ThrowableProxyConverter`/converter do Logback com as mesmas regras (o teste CA2 é o que pega). (2) Mascarar por padrão pode ter falso positivo (número longo qualquer); o CA3 trava os casos legítimos mais comuns (id curto, UUID, centavos). (3) Se o actuator na porta separada não herdar o filtro de segurança como previsto, as rotas ficam 401 ou abertas demais; CA11 e CA12 percebem. (4) Sondas do `docker-compose`/`infra` ainda não existem para o back-end; nada a quebrar. (5) Arquivo de log em disco acumula no desenvolvimento: pasta ignorada pelo git. (6) O `traceId` precisa estar no MDC também quando o erro é tratado fora do `ApiExceptionHandler` (ex.: 401/403 em `SecurityErrorHandlers`); o filtro tem ordem mais alta, então cobre.

## 5. Tarefas

- [x] T1: acrescentar as dependências do Actuator e do registro Prometheus no `pom.xml`, e a configuração base em `application.yml` (endpoints expostos só `health` e `prometheus`, probes, porta de gestão, detalhes ocultos, histograma de `http.server.requests`) — cobre CA8 (parte), CA11 — prova: `./backend/mvnw -f backend/pom.xml -q compile` e `ActuatorEndpointsTest`.
- [x] T2: criar `SensitiveDataMasker` com as regras (senha, token e `Bearer`, CPF com e sem pontuação, número de cartão de 13 a 19 dígitos com ou sem separador) e ligá-lo ao formato JSON, para mensagem, argumentos e pilha — cobre CA2, CA3 — prova: `SensitiveDataMaskerTest` e `LogOutputTest` (RNF-11).
- [x] T3: formato estruturado em `application.yml` (console e arquivo), `traceId` no MDC em `TraceIdFilter` (com limpeza no fim), arquivo com rotação diária e `max-history: 30`, `.gitignore` da pasta de logs — cobre CA1, CA4, CA5, CA6, CA7 — prova: `LogOutputTest` (rota de teste que loga e que falha, com cabeçalhos e query com segredo) e teste das propriedades.
- [x] T4: liberar `health` e `prometheus` em `SecurityConfig`, fora do HTTPS obrigatório, e provar que nada mais do Actuator nem do restante é atendido — cobre CA11, CA12 — prova: `ActuatorEndpointsTest`.
- [x] T5: criar `OperationalMetrics` (`ctcore.email.queue.pending`, `ctcore.webhook.failures`) e provar a saída em `/actuator/prometheus` com erros 200 e 500 — cobre CA9, CA10 — prova: `OperationalMetricsTest` e `ActuatorEndpointsTest`.
- [x] T6: saúde com banco real e com banco indisponível — cobre CA8 — prova: `HealthIT` com Docker ativo.
- [x] T7: documentar no `backend/README.md` formato, retenção, regra de dado sensível, rotas, porta de gestão e uso das métricas de negócio — cobre CA13 — prova: leitura do arquivo e `./backend/mvnw -f backend/pom.xml verify` verde.

## 6. Decisões

- D1: logs estruturados pelo suporte nativo do Spring Boot 3.5 (formato `logstash`). Motivo: já vem no Boot, sem dependência nova. Alternativa: `logstash-logback-encoder`, descartada por ser dependência a mais para o mesmo resultado.
- D2: JSON em console e arquivo em todos os ambientes, inclusive o local (o perfil `local` não muda o formato). Motivo: o que se testa é o que roda; o log legível por pessoa vem de ferramentas como `jq`. Alternativa: texto legível no perfil `local`, descartada por esconder diferença entre ambientes; pode voltar se incomodar.
- D3 (muda o comportamento): o mascaramento é aplicado na saída do log, por regras fixas (senha, token, CPF completo, cartão), e troca o valor por `[REDACTED]` por inteiro, sem manter últimos dígitos. Motivo: RNF-11 proíbe CPF completo; mascarar tudo é mais seguro e mais simples de testar que mascarar parcial. Alternativa: mostrar os últimos dígitos, descartada por não haver necessidade operacional hoje. Cada item que logar um dado novo e sensível acrescenta a regra e o teste.
- D4: arquivo `logs/ctcore.log` (pasta configurável por `LOG_DIR`), rotação diária e guarda de 30 dias na própria aplicação. Motivo: cumpre RNF-11 sem hospedagem (que só existe na #192). Alternativa: só console, descartada por deixar a guarda de 30 dias sem dono. Suposição: na hospedagem, a #192 decide se o arquivo continua ou se o coletor da plataforma assume os 30 dias; se mudar, ajusta-se a configuração sem tocar no código.
- D5 (muda o comportamento): só `health` e `prometheus` são expostos; saúde sem detalhes; `metrics`, `env`, `beans`, `loggers` e `heapdump` ficam fechados. Motivo: o monitoramento (#193) precisa de saúde e do formato Prometheus, e o resto é superfície de ataque. Alternativa: expor `metrics` JSON também, descartada por duplicar o Prometheus.
- D6: formato das métricas é Prometheus (Micrometer). Motivo: padrão de fato que quase todo serviço de monitoramento lê, sem citar fornecedor (regra do `AGENTS.md`). Alternativa: formato de um fornecedor, descartada pela regra.
- D7: "fila de e-mail" e "falhas de webhook" viram duas métricas de negócio criadas agora (gauge `ctcore.email.queue.pending`, contador `ctcore.webhook.failures`) num bean `OperationalMetrics` com `emailQueue(long)` e `webhookFailed()`, valendo 0 até as #19 e #96 chamarem. Motivo: a issue pede as métricas expostas agora, mas fila e webhook só existem depois; assim a #193 já tem o nome para montar alerta, e os itens futuros só chamam o bean. Alternativa: deixar para os itens #19 e #96 criarem, descartada porque a issue #5 pede a métrica pronta. Erros e lentidão vêm de `http.server.requests` (e `status`, percentis por histograma), sem código próprio.
- D8: Actuator em porta de gestão própria (`management.server.port`, padrão 8081), fora de `/api/v1` e do OpenAPI. Motivo: a porta não é publicada para a internet, e o contrato do OpenAPI é só da API de negócio. Alternativa: mesma porta com autenticação por token, descartada por exigir segredo e item de credencial que ainda não existe. Suposição: a hospedagem (#192) manterá a porta de gestão só na rede interna; se não, a #192 coloca autenticação na frente.
- Suposição: o item não registra `organizationId` no log porque o contexto de organização só nasce na #14; a #14 acrescenta o campo no MDC.
- Suposição: o Docker está disponível para `HealthIT`; sem ele o CA8 fica com a parte do banco sem prova executada. A esteira de CI do PR vem da #10 e ainda não existe, então "CI verde" não se aplica a este item.

## Construção

- T1 — arquivos: `backend/pom.xml`, `backend/src/main/resources/application.yml` — prova: `./backend/mvnw -f backend/pom.xml test` → `ActuatorEndpointsTest` verde (CA11)
- T2 — arquivos: `logging/SensitiveDataMasker.java`, `logging/SensitiveDataMaskerTest.java` — prova: `mvnw test` → verde (CA2, CA3; também no console real em `LogOutputTest`)
- T3 — arquivos: `application.yml`, `api/error/TraceIdFilter.java`, `.gitignore`, `logging/LogOutputTest.java`, `monitoring/TestProbes.java` — prova: `mvnw test` → `LogOutputTest` 5 de 5 (CA1, CA4–CA7)
- T4 — arquivos: `config/SecurityConfig.java`, `monitoring/ActuatorEndpointsTest.java` — prova: `mvnw test` → verde (CA11, CA12)
- T5 — arquivos: `monitoring/OperationalMetrics.java`, `OperationalMetricsTest.java`, `ActuatorEndpointsTest.java` — prova: `mvnw test` → verde (CA9, CA10)
- T6 — arquivos: `monitoring/HealthIT.java`, `application.yml` (grupo readiness inclui `db`) — prova: `./backend/mvnw -f backend/pom.xml verify` → `HealthIT` verde com Docker (CA8, readiness 503 com banco parado)
- T7 — arquivos: `backend/README.md` — prova: leitura; `verify` → BUILD SUCCESS (CA13)

Regressão: `./backend/mvnw -f backend/pom.xml verify` → 36 testes rápidos e 15 de integração, 0 falhas, BUILD SUCCESS.
Desvios:
- Segurança: em vez de `EndpointRequest.to("health","prometheus")`, `SecurityConfig` libera `/actuator/**` e tira `/actuator/` da exigência de HTTPS (matcher por caminho). Motivo: com `EndpointRequest`, endpoint não exposto (`env`, etc.) caía em 401/302 e não em 404 (CA11). Só `health` e `prometheus` têm handler, então o resto dá 404. `requiresInsecure()` foi descartado porque redireciona HTTPS para HTTP.
- Nomes do JSON `logstash` do Boot: `logger_name` e `traceId` (campo de primeiro nível, vindo do MDC), não `logger`.
- `management.endpoint.health.validate-group-membership: false`, porque o grupo `readiness` inclui `db` e os testes sem banco o excluem.
- Testes com `RANDOM_PORT` usam `management.server.port=0`; `ActuatorEndpointsTest` usa `@AutoConfigureObservability` (o teste do Boot desliga a exportação de métricas por padrão).
- `HealthIT` usa contêiner próprio (não `PostgresIT`), pois derruba o banco.
Não é do item: nenhum.

## Verificação

### Rodada 1 — OK — 2026-10-15, estado cc21494ca34b2bf05567298079738fbabaa348c3

| Critério | Status | Evidência |
|---|---|---|
| CA1 | PASSOU | `LogOutputTest.jsonWithTraceId`: linhas JSON com `@timestamp`, `level`, `logger_name`, `message`, `traceId` |
| CA2 | PASSOU | `SensitiveDataMaskerTest.masksSensitive` e `LogOutputTest.errorTrace` (pilha) |
| CA3 | PASSOU | `SensitiveDataMaskerTest.keepsLegit`, `LogOutputTest.masksInOutput` |
| CA4 | PASSOU | `LogOutputTest.jsonWithTraceId` (traceId = `X-Trace-Id`, distinto na 2ª requisição) |
| CA5 | PASSOU | `LogOutputTest.errorTrace` |
| CA6 | PASSOU | `LogOutputTest.noRequestDataInLogs` (401, 403, 500) |
| CA7 | PASSOU | `LogOutputTest.fileConfig` e `application.yml` (`max-history: 30`, `%d{yyyy-MM-dd}`) |
| CA8 | PASSOU | `HealthIT` com Docker: 200 UP sem detalhes; readiness 503 com banco parado |
| CA9 | PASSOU | `ActuatorEndpointsTest.prometheus` |
| CA10 | PASSOU | `ActuatorEndpointsTest.prometheus` (0 e depois 3 e 1) |
| CA11 | PASSOU | `ActuatorEndpointsTest.onlyHealthAndPrometheus` |
| CA12 | PASSOU | `ActuatorEndpointsTest.mainPortClosed` e `prometheus` (sem UUID, e-mail, CPF) |
| CA13 | PASSOU | leitura de `backend/README.md`, seção "Logs e monitoramento" |

Comandos: `./backend/mvnw -f backend/pom.xml verify` → BUILD SUCCESS; 15 testes de integração, 0 falhas (inclui `HealthIT`); testes rápidos verdes.
Achados:
- sugestão, código, `SecurityConfig` — `/actuator/**` fica `permitAll` na porta principal; hoje dá 404 por não haver handler, mas depende de `management.server.port` separado. Se a porta de gestão for igualada à principal, as rotas abrem sem autenticação (documentado em D8; risco aceito).

## Entrega

- Logs em JSON (console e `logs/ctcore.log`, rotação diária, 30 dias), com `traceId` por requisição e mascaramento de senha, token, CPF e cartão; sem corpo, cabeçalho ou parâmetro de requisição nos logs.
- Actuator em porta de gestão própria (8081): só `health` (sem detalhes, readiness inclui banco) e `prometheus`; o resto responde 404 e a porta principal não serve `/actuator`.
- Métricas de negócio `ctcore.email.queue.pending` e `ctcore.webhook.failures` em `OperationalMetrics`, valendo 0 até as #19 e #96 as usarem; erros e lentidão vêm de `http.server.requests`.
- Verificação: `./backend/mvnw -f backend/pom.xml verify` verde (rodada 1 OK), incluindo `HealthIT` com Docker.
- Limitações: `/actuator/**` é `permitAll` e depende da porta de gestão separada (D8); a #192 deve mantê-la só na rede interna. CI do PR ainda não existe (#10).
