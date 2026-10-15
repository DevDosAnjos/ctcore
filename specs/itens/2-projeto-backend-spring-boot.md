# 2 — Projeto back-end: Spring Boot, Flyway e erros da API

Origem: issue #2 (Sprint 00 · Fundação). Branch: `item/2-projeto-backend-spring-boot`.

## 1. Objetivo

Criar o projeto `backend/` (Java 21, Spring Boot 3, Maven) que sobe contra o PostgreSQL do ambiente local, aplica as migrações com Flyway e já nasce com os padrões que todos os itens seguintes copiam: UUID, datas em UTC, formato único de erro da API (código, mensagem, o que foi ou não alterado), senha com Argon2 e cookie de sessão protegido com proteção contra envio forjado (CSRF). Serve a todos os itens de back-end.

Fora do escopo: rotas de negócio e esqueleto do OpenAPI (#3), agendador (#4), logs estruturados e métricas (#5), portas externas (#6), CI e cobertura mínima de 80% no PR (#10), esquema de organização e RLS de verdade (#13, #14), login, sessão e conta (#18). Aqui só se prova que a infraestrutura (migração, RLS no PostgreSQL real, cookie, CSRF, hash) funciona.

## 2. Requisitos

- R1: `backend/` é um projeto Maven Spring Boot 3 em Java 21 que compila e roda com `./mvnw` (D1, D2).
- R2: Flyway aplica as migrações de `db/migration` ao subir a aplicação, no PostgreSQL configurado por variáveis de ambiente com padrão igual ao do `infra/docker-compose.yml`; migração já aplicada com conteúdo alterado faz a subida falhar (validação do Flyway ligada).
- R3: identificadores são UUID (tipo `uuid` no banco, `java.util.UUID` no código), gerados pelo banco (D3).
- R4: datas e horas são guardadas e trafegadas em UTC: colunas `timestamptz`, JVM e Hibernate em UTC, JSON em ISO-8601 com `Z`; existe um `Clock` injetável para as regras (D4).
- R5: toda resposta de erro da API tem o mesmo corpo JSON com `code`, `message`, `changed` (booleano: se algo foi alterado), `fields` (lista de campo e mensagem, vazia quando não se aplica) e `traceId`; detalhe interno (pilha, SQL) nunca vai no corpo (D5, RNF-24).
- R6: o tratamento único cobre validação (400), corpo ilegível (400), não autenticado (401), sem permissão (403), rota inexistente (404), método não permitido (405), conflito de dados (409) e erro inesperado (500), todos no formato de R5; 403 e 404 de recurso não revelam se o recurso existe (D5).
- R7: (RNF-04) a aplicação só atende por HTTPS: requisição com `X-Forwarded-Proto: http` é redirecionada para HTTPS e as respostas trazem `Strict-Transport-Security`; o perfil `local` desliga só o redirecionamento (D6).
- R8: (RNF-04) senhas são codificadas com Argon2id por um `PasswordEncoder` único; o hash gravado começa com `$argon2id$` e a verificação aceita a senha certa e recusa a errada.
- R9: (RNF-07) o cookie de sessão tem `HttpOnly`, `Secure` e `SameSite=Lax`, e requisição que altera dado (POST, PUT, PATCH, DELETE) sem token CSRF válido é recusada com 403 no formato de R5 (D7).
- R10: (RNF-30) existe teste de integração com PostgreSQL real via Testcontainers que sobe a aplicação, confirma que as migrações foram aplicadas e prova que row-level security isola linhas por organização na sessão do banco.
- R11: (RNF-29) as classes de regra criadas aqui (conversão de erros, codificação de senha) têm teste unitário; cada código da issue (RNF-04, RNF-07, RNF-29, RNF-30) tem ao menos um teste.
- R12: `backend/README.md` e a seção "Comandos" do `AGENTS.md` dizem como compilar, testar e rodar o back-end.

## 3. Critérios de aceite

- CA1 (R1): dado o repositório, quando se roda `./mvnw -f backend/pom.xml verify` com Docker ativo, então o build termina com sucesso, em Java 21.
- CA2 (R2): dado o ambiente local no ar (`docker compose up -d --wait`), quando se roda a aplicação com o perfil `local`, então ela sobe sem erro e a tabela `flyway_schema_history` do banco tem a migração V1 com `success = true`.
- CA3 (R2): dado um banco com a V1 aplicada, quando o conteúdo da V1 é alterado e a aplicação sobe, então a subida falha por checksum diferente (teste de integração).
- CA4 (R3): dado o banco migrado, quando se cria uma linha em tabela de teste com `id uuid default gen_random_uuid()` e se lê pelo código como `UUID`, então o valor volta como `UUID` válido e diferente a cada linha.
- CA5 (R4): dado o banco migrado, quando se grava e lê um `timestamptz` pela aplicação, então o instante é o mesmo e o JSON serializa com sufixo `Z`; `ZoneId.systemDefault()` é UTC e o `Clock` injetado é UTC.
- CA6 (R5, R6): dado um controlador de teste, quando se envia entrada que viola validação, então a resposta é 400 com `code`, `message`, `changed = false`, `fields` com o campo e a mensagem e `traceId` preenchido.
- CA7 (R6): dado um controlador de teste que lança erro inesperado, quando é chamado, então a resposta é 500 no formato de R5, com `message` genérica e sem pilha, nome de classe ou SQL no corpo.
- CA8 (R6): dado um controlador de teste que lança conflito de dados (violação de unicidade), quando é chamado, então a resposta é 409 no formato de R5 com `changed = false`.
- CA9 (R6): dado um pedido sem autenticação a uma rota protegida, uma rota inexistente e um método não permitido, quando são chamados, então as respostas são 401, 404 e 405, cada uma no formato de R5.
- CA10 (R6): dado um usuário autenticado de teste sem o papel exigido, quando chama rota restrita, então a resposta é 403 no formato de R5, e o corpo do 403 é igual ao do 404 de recurso inexistente no que diz respeito a não revelar existência (mesma `message` genérica para "não encontrado ou sem acesso").
- CA11 (R7): dado o perfil padrão (sem `local`), quando chega requisição com `X-Forwarded-Proto: http`, então a resposta é redirecionamento para `https://`; com `https`, a resposta traz `Strict-Transport-Security`.
- CA12 (R8): dado o `PasswordEncoder`, quando se codifica uma senha, então o hash começa com `$argon2id$`, `matches` é verdadeiro para a senha certa e falso para a errada, e dois hashes da mesma senha são diferentes.
- CA13 (R9): dado um login simulado no teste que cria sessão, quando se lê o `Set-Cookie` da sessão, então ele contém `HttpOnly`, `Secure` e `SameSite=Lax`.
- CA14 (R9): dado um usuário autenticado, quando faz POST sem token CSRF, então a resposta é 403 no formato de R5 e nada é alterado; com o token CSRF válido, o POST é aceito.
- CA15 (R10): dado o PostgreSQL do Testcontainers, quando duas linhas de organizações diferentes são gravadas em tabela com RLS e a sessão do banco define a organização A, então a consulta (feita por papel que não é dono da tabela) devolve só a linha de A; sem organização definida, devolve zero linhas; o `INSERT` de linha da organização B com a sessão em A é recusado.
- CA16 (R11): dado o conjunto de testes, quando se confere o mapa de códigos da issue, então RNF-04 (CA11, CA12), RNF-07 (CA13, CA14), RNF-29 (CA6, CA7, CA12, testes unitários) e RNF-30 (CA3, CA15) têm ao menos um teste cada, nomeados com o código no nome ou na anotação `@DisplayName`.
- CA17 (R12): dado um clone limpo, quando se segue só `backend/README.md`, então é possível compilar, testar e rodar a aplicação; a seção "Comandos" do `AGENTS.md` traz os comandos de build e de testes do back-end.

## 4. Plano técnico

- Dados: migração `backend/src/main/resources/db/migration/V1__baseline.sql` (a criar), sem tabelas de negócio (essas são da #13); serve para provar o Flyway. Regras de dados que ficam como padrão para os itens seguintes: `id uuid primary key default gen_random_uuid()` (nativo do PostgreSQL 16, sem extensão), datas em `timestamptz`, valores em centavos (`bigint`). Migração nunca é editada depois de mergeada; correção vai em nova migração. A V1 não é destrutiva e não precisa de reversão. A prova de RLS usa tabela criada só no teste (esquema do Testcontainers descartável), nunca em migração de produção.
- API: sem rota de negócio. Cria o formato de erro único e a base de segurança. Corpo de erro: `{ "code": "VALIDATION_ERROR", "message": "...", "changed": false, "fields": [{"field": "...", "message": "..."}], "traceId": "..." }`. Códigos iniciais (constantes em inglês, mensagens em pt-BR no arquivo `messages.properties`, nunca no código): `VALIDATION_ERROR`, `MALFORMED_REQUEST`, `UNAUTHENTICATED`, `FORBIDDEN`, `NOT_FOUND`, `METHOD_NOT_ALLOWED`, `CONFLICT`, `INTERNAL_ERROR`. `changed` é `false` por padrão; uma exceção de negócio pode informar `true` quando a operação alterou parte do dado (para lote que grava tudo ou nada, será sempre `false`). `traceId` vem do identificador de requisição (gerado por filtro simples; a correlação de logs fica na #5).
- Interface: não muda.
- Segurança: Spring Security com tudo protegido por padrão (nenhuma rota pública criada aqui); `AuthenticationEntryPoint` e `AccessDeniedHandler` escrevem o formato de R5; CSRF ligado com `CookieCsrfTokenRepository` (cookie `XSRF-TOKEN` legível pelo SPA) e SPA lendo o token e enviando em cabeçalho; cookie de sessão por `server.servlet.session.cookie.http-only/secure/same-site`; HTTPS por `server.forward-headers-strategy=framework`, `requiresSecure` e HSTS, desligando só o redirecionamento no perfil `local`; `Argon2PasswordEncoder` (Spring Security, com BouncyCastle) em um bean único; segredos só por variável de ambiente (nada no repositório além dos valores de desenvolvimento já presentes em `infra/.env.example`); mensagens de erro sem detalhe interno, detalhe só no log; consultas parametrizadas. Login, política de senha, limite de tentativas e demais controles de sessão ficam na #18. Sem dado pessoal tratado neste item.
- Arquivos: a alterar (existem): `backend/README.md`, `AGENTS.md` (seção "Comandos"), `.gitignore` (já ignora `backend/target/`). A criar em `backend/`: `pom.xml`, `mvnw`, `mvnw.cmd`, `.mvn/wrapper/maven-wrapper.properties`; `src/main/java/br/com/ctcore/CtcoreApplication.java`; `config/ClockConfig.java` (Clock UTC), `config/SecurityConfig.java`, `api/error/ApiError.java`, `api/error/ApiExceptionHandler.java`, `api/error/ErrorCode.java`, `api/error/SecurityErrorHandlers.java`, `api/error/TraceIdFilter.java`; `src/main/resources/application.yml`, `application-local.yml`, `messages.properties`, `db/migration/V1__baseline.sql`; testes em `src/test/java/br/com/ctcore/`: `api/error/ApiExceptionHandlerTest` (MockMvc com controlador de teste), `security/PasswordEncoderTest`, `security/SessionCookieCsrfTest`, `security/HttpsTest`, `db/MigrationIT`, `db/RowLevelSecurityIT`, base `PostgresIT` com Testcontainers.
- Reuso: PostgreSQL do `infra/docker-compose.yml` (porta 5432, usuário e senha de desenvolvimento do `infra/.env.example`) para rodar localmente; nada do código existe ainda. Dependências: spring-boot-starter-web, -validation, -security, -data-jpa, -test, spring-security-test, flyway-core e flyway-database-postgresql, driver postgresql, bcprov (Argon2), testcontainers (postgresql, junit-jupiter, via BOM do Spring Boot).
- Riscos: Maven não está instalado na máquina (só Java 21): o wrapper (`mvnw`) precisa ser gerado ou baixado; sem Docker os testes de integração (CA2, CA3, CA15 e os que usam Testcontainers) não rodam e ficam como não executados, não aprovados; `Secure` em cookie não é enviado em HTTP puro, então testes usam MockMvc com requisição segura; Hibernate e `gen_random_uuid()` exigem PostgreSQL 13 ou mais (a imagem local é 16.4); versões das dependências devem ser fixadas pelo BOM do Spring Boot (confirmar que a versão existe no Maven Central).

## 5. Tarefas

- [x] T1: criar o projeto Maven em `backend/` (`pom.xml` com Spring Boot 3, Java 21, dependências listadas, `mvnw` e `mvnw.cmd`), classe `CtcoreApplication` e `application.yml` com datasource por variável de ambiente (padrão do compose), `spring.jpa.hibernate.ddl-auto=validate`, Flyway com `validate-on-migrate`, UTC e Jackson em ISO-8601 — cobre CA1, CA4, CA5 — prova: `./mvnw -f backend/pom.xml -q compile` termina sem erro.
- [x] T2: criar `V1__baseline.sql` (sem tabela de negócio, só o marco do Flyway) e `ClockConfig` (Clock UTC); fixar `user.timezone=UTC` na inicialização — cobre CA2, CA5 — prova: `MigrationIT` confirma V1 com sucesso em `flyway_schema_history`, e o teste de data confere UTC e `Z` no JSON.
- [x] T3: criar a base de testes de integração `PostgresIT` (Testcontainers PostgreSQL 16) e `MigrationIT` com o caso de checksum alterado — cobre CA2, CA3, CA4 — prova: `./mvnw -f backend/pom.xml verify` (Docker ativo) passa em `MigrationIT`.
- [x] T4: criar `RowLevelSecurityIT`: tabela de teste com `organization_id`, `ENABLE`/`FORCE ROW LEVEL SECURITY`, política por `current_setting('app.organization_id')`, papel sem dono da tabela; provar isolamento, sessão sem organização e `INSERT` cruzado recusado — cobre CA15 — prova: `RowLevelSecurityIT` passa.
- [x] T5: criar o formato de erro: `ApiError`, `ErrorCode`, `ApiExceptionHandler` (`@RestControllerAdvice`), `TraceIdFilter`, textos em `messages.properties`, e `ApiExceptionHandlerTest` com controlador de teste cobrindo validação, corpo ilegível, conflito, erro inesperado e 404/405 — cobre CA6, CA7, CA8, CA9 (404, 405) — prova: `ApiExceptionHandlerTest` passa.
- [x] T6: criar `SecurityConfig` com tudo protegido, `SecurityErrorHandlers` (401 e 403 no formato de R5), CSRF por cookie, HTTPS com HSTS e redirecionamento (desligado no perfil `local`), cookie de sessão protegido, bean `Argon2PasswordEncoder`; testes `HttpsTest`, `SessionCookieCsrfTest`, `PasswordEncoderTest` — cobre CA9 (401), CA10, CA11, CA12, CA13, CA14 — prova: os três testes passam.
- [x] T7: conferir o mapa de códigos RNF-04, RNF-07, RNF-29 e RNF-30 nos testes (nome ou `@DisplayName` com o código) — cobre CA16 — prova: `grep -r "RNF-0[47]\|RNF-29\|RNF-30" backend/src/test` mostra ao menos um teste por código.
- [x] T8: atualizar `backend/README.md` (como compilar, testar, rodar com o perfil `local`, variáveis de ambiente, exigência de Docker para os testes) e a seção "Comandos" do `AGENTS.md` (build e testes do back-end) — cobre CA17 — prova: seguir o README e rodar `./mvnw -f backend/pom.xml verify`.

## 6. Decisões

- D1: Maven Wrapper (`mvnw`) no repositório. Motivo: a máquina só tem Java 21, e o wrapper dá a mesma versão do Maven para todos e para o CI da #10. Alternativa: exigir Maven instalado.
- D2: pacote raiz `br.com.ctcore`, grupo Maven `br.com.ctcore`, artefato `ctcore-backend`, Spring Boot 3.x estável mais recente confirmado no Maven Central na construção. Motivo: a especificação não define; é nome neutro e sem fornecedor. Alternativa: `com.ctcore`.
- D3: UUID gerado pelo banco com `gen_random_uuid()` (v4). Motivo: sem extensão no PostgreSQL 16, sem código de geração. Alternativa: UUIDv7 gerado na aplicação, melhor para índice, descartada por exigir biblioteca ou código próprio sem necessidade medida; se a #112 (desempenho) mostrar problema, troca-se por nova migração.
- D4: datas em UTC por `timestamptz` e `Clock` injetável. Motivo: RN do documento (`00-visao-geral.md` §9: UTC no banco, fuso da unidade só na exibição). A conversão para o fuso da unidade fica para o item que a exibe.
- D5: corpo de erro com `code`, `message`, `changed`, `fields`, `traceId`. Motivo: a issue pede "código, mensagem, o que foi ou não alterado" e a RNF-24 pede mensagem que diz o que aconteceu e o que foi alterado; `fields` e `traceId` são acréscimos para validação e suporte. Acesso negado e recurso inexistente usam a mesma mensagem genérica para não revelar existência. Alternativa: RFC 7807 (`application/problem+json`), descartada porque a issue define campos próprios; o contrato fica documentado no OpenAPI pela #3.
- D6: HTTPS é exigido na própria aplicação (redirecionamento e HSTS), confiando em `X-Forwarded-Proto`, porque a terminação TLS será do proxy da hospedagem (Sprint 22) e hoje não há hospedagem. O perfil `local` desliga só o redirecionamento para rodar em `localhost`. Alternativa: deixar tudo ao proxy, descartada por não ser testável aqui.
- D7 (muda o comportamento): toda requisição que altera dado exige token CSRF; o SPA lê o cookie `XSRF-TOKEN` e o devolve em cabeçalho `X-XSRF-TOKEN`. Cookie de sessão com `SameSite=Lax` (não `Strict`), para o link de e-mail (convite, recuperação de senha) abrir já logado. Motivo: RNF-07 e uso real dos links por e-mail. Alternativa: `SameSite=Strict`, que obrigaria novo login ao abrir links de e-mail.
- D8: o back-end usa JPA (Hibernate) além do Flyway, com `ddl-auto=validate`. Motivo: stack padrão do Spring Boot e necessária para fixar o fuso do Hibernate; o esquema vem só das migrações. Alternativa: apenas JDBC, que adiaria a escolha para um item de negócio.
- D9: a prova de RLS deste item é feita com tabela criada no teste; a política real (variável de sessão, papel da aplicação) é desenhada na #14. A variável usada aqui (`app.organization_id`) é só do teste e pode mudar lá.
- Suposição: Docker está disponível na máquina que construir e verificar; se não estiver, CA2, CA3, CA4 e CA15 ficam sem prova executada e o item não pode ser dado como verificado. Suposição: Argon2 com parâmetros padrão do Spring Security (`Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8()`) é aceitável; se a plataforma quiser parâmetros próprios, ajusta-se em um bean só.

## Construção

- T1 — arquivos: `backend/pom.xml`, `backend/mvnw`, `mvnw.cmd`, `.mvn/wrapper/maven-wrapper.properties`, `CtcoreApplication.java`, `application.yml` — prova: `./backend/mvnw -f backend/pom.xml -q compile` → sem erro
- T2 — arquivos: `db/migration/V1__baseline.sql`, `config/ClockConfig.java`, UTC fixado em `CtcoreApplication` (inicializador estático) e `application.yml` — prova: `MigrationIT` (v1Applied, utcEverywhere) → passou
- T3 — arquivos: `db/PostgresIT.java`, `db/MigrationIT.java` — prova: `./backend/mvnw -f backend/pom.xml verify` → 4 testes de `MigrationIT` passaram (CA2, CA3, CA4, CA5)
- T4 — arquivos: `db/RowLevelSecurityIT.java` — prova: `verify` → passou (isolamento, sem organização = 0 linhas, insert cruzado recusado)
- T5 — arquivos: `api/error/{ApiError,ErrorCode,ApiExceptionHandler,TraceIdFilter,SecurityErrorHandlers}.java`, `messages.properties`, `ApiExceptionHandlerTest` — prova: `verify` → 8 testes passaram
- T6 — arquivos: `config/SecurityConfig.java`, `application-local.yml`, `HttpsTest`, `SessionCookieCsrfTest`, `PasswordEncoderTest` — prova: `verify` → 2 + 2 + 1 testes passaram
- T7 — prova: `grep -rhoE "RNF-0[47]|RNF-29|RNF-30" backend/src/test` → RNF-04 (3), RNF-07 (2), RNF-29 (3), RNF-30 (2)
- T8 — arquivos: `backend/README.md`, `AGENTS.md` (Comandos) — prova: `verify` → BUILD SUCCESS; `spring-boot:run -Dspring-boot.run.profiles=local` contra o compose subiu e `flyway_schema_history` tem versão 1 com `success = t` (CA2)

Regressão: `./backend/mvnw -f backend/pom.xml verify` → 13 testes rápidos e 5 de integração, 0 falhas, BUILD SUCCESS (linha de base: não havia código nem testes antes do item).
Desvios: Spring Boot 3.5.16 e Maven 3.9.16 (confirmados no Maven Central). `bcprov-jdk18on` fixado em 1.86 no `pom.xml` porque o BOM do Spring Boot não gerencia essa dependência. `SessionCookieCsrfTest` sobe a aplicação inteira sem banco (autoconfiguração de DataSource, JPA e Flyway excluída), sem exigir Docker. O wrapper foi gerado com `mvn wrapper:wrapper` (modo `only-script`, sem JAR). `SecurityConfig.passwordEncoder()` é público para o teste unitário.
Observação para a #18: com `CsrfTokenRequestAttributeHandler` e token adiado, o cookie `XSRF-TOKEN` só é emitido quando o token é lido numa resposta; o login ou uma rota de sessão deve garantir isso para o SPA.
Não é do item: nada.

## Verificação

### Rodada 1 — OK — 2026-10-15, estado e51c9b0907b4df7a3009c3680cbd309e5c07573c

| Critério | Status | Evidência |
|---|---|---|
| CA1 | PASSOU | `./backend/mvnw -f backend/pom.xml verify` → BUILD SUCCESS (Java 21, Docker ativo) |
| CA2 | PASSOU | `MigrationIT.v1Applied`; no banco do compose `flyway_schema_history` tem versão 1 com `success = t` |
| CA3 | PASSOU | `MigrationIT.changedMigrationFailsValidation` (FlywayValidateException) |
| CA4 | PASSOU | `MigrationIT.uuidGeneratedByDatabase` |
| CA5 | PASSOU | `MigrationIT.utcEverywhere` (instante, `Z`, JVM e Clock em UTC) |
| CA6 | PASSOU | `ApiExceptionHandlerTest` (validação 400 com fields e traceId) |
| CA7 | PASSOU | `ApiExceptionHandlerTest` (500 genérico) |
| CA8 | PASSOU | `ApiExceptionHandlerTest` (409, changed=false) |
| CA9 | PASSOU | `ApiExceptionHandlerTest` (401, 404, 405) |
| CA10 | PASSOU | `ApiExceptionHandlerTest` (403 e 404 com mesma mensagem); `messages.properties` idênticas |
| CA11 | PASSOU | `HttpsTest` (redirecionamento e HSTS) |
| CA12 | PASSOU | `PasswordEncoderTest` |
| CA13 | PASSOU | `SessionCookieCsrfTest.sessionCookieFlags` (servidor real) |
| CA14 | PASSOU | `SessionCookieCsrfTest.csrfRequired` |
| CA15 | PASSOU | `RowLevelSecurityIT` (papel não dono, sem org = 0, insert cruzado recusado) |
| CA16 | PASSOU | grep: RNF-04 (3), RNF-07 (2), RNF-29 (3), RNF-30 (2) |
| CA17 | PASSOU | `backend/README.md` e seção "Comandos" do `AGENTS.md` trazem compilar, testar e rodar; comando `verify` confirmado |

Comandos: `docker ps` (compose no ar); `./backend/mvnw -f backend/pom.xml verify` → 13 testes rápidos (8+2+1+2) e 5 de integração (4+1), 0 falhas, BUILD SUCCESS; `psql` em `flyway_schema_history` → V1 success.
Achados:
- sugestão, código, `ApiExceptionHandler` — tipo de mídia não suportado (415) e parâmetro ausente (400) caem no handler genérico e viram 500; tratar quando a primeira rota de negócio surgir.

## Entrega

- Projeto Spring Boot 3.5 (Java 21, Maven com wrapper) em `backend/`, com Flyway (V1 baseline), UTC em toda a pilha, erros da API padronizados (`ApiError` com `traceId`, 403 e 404 sem diferença de mensagem), HTTPS/HSTS, cookie de sessão seguro, CSRF e bcrypt, e row-level security testada com papel não dono.
- Verificação: `./backend/mvnw -f backend/pom.xml verify` com 13 testes rápidos e 5 de integração, 0 falhas; CA1 a CA17 passaram (rodada 1, OK).
- Decisões: `bcprov-jdk18on` fixado em 1.86 (o BOM não gerencia); `SessionCookieCsrfTest` sobe sem banco.
- Limitações: 415 e parâmetro ausente ainda caem no handler genérico (tratar com a primeira rota de negócio); o cookie `XSRF-TOKEN` só sai quando o token é lido, a #18 deve garantir isso no login.
