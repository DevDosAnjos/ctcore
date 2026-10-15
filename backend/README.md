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
