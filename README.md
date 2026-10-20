# ctcore

Plataforma de gestão para academias de artes marciais.

## Estrutura

Monorepo (decisão do dono em 13/10/2026, no lugar do compose com submódulos da especificação 1.2):

```
backend/    Java 21 + Spring Boot 3 + Maven
frontend/   TypeScript + React + Vite
infra/      docker-compose, scripts e publicação
docs/       especificação 1.2 e OpenAPI
```


## Ambiente local

Precisa do Docker com Compose. Sem criar `.env`, de dentro de `infra/`:

```
cd infra
docker compose up -d --wait     # sobe e espera ficarem saudáveis
docker compose down             # para, mantendo os dados
docker compose down -v          # para e apaga os dados (volumes)
```

**Atenção aos dados locais.** O compose fixa o nome de projeto `ctcore` (`name: ctcore` em `infra/docker-compose.yml`), então os volumes são `ctcore_postgres-data` e `ctcore_storage-data`, e **qualquer clone do repositório usa os mesmos volumes**. `docker compose down -v` apaga esses dados. Para clonar o repositório, revisar ou fazer uma prova sem tocar nos seus dados, dê outro nome ao projeto (`docker compose -p <outro-nome> ...` ou `COMPOSE_PROJECT_NAME=<outro-nome>`; os volumes passam a ser `<outro-nome>_*`) e só então use `down -v`. Nunca rode `down -v` sem esse nome em outro clone.

Da raiz: `docker compose -f infra/docker-compose.yml up -d --wait`. Para mudar portas ou credenciais, copie `infra/.env.example` para `infra/.env`.

Antes de iniciar o back-end, execute `bash infra/scripts/prepare-runtime-db.sh` (idempotente, preserva volumes existentes). A aplicação usa `DB_RUNTIME_USER=ctcore_app` / `DB_RUNTIME_PASSWORD=ctcore_app_dev`; Flyway usa `DB_MIGRATION_USER` / `DB_MIGRATION_PASSWORD`, com fallback para `POSTGRES_USER` / `POSTGRES_PASSWORD`. Valores padrão são somente de desenvolvimento. Ao personalizar, exporte as variáveis para o script e para o Java (que não carrega `infra/.env`); para a imagem, use as mesmas variáveis com `docker run -e`. Nunca use a credencial administrativa no runtime. Detalhes: `backend/README.md`.

### Dados fictícios

Com o ambiente de pé e o back-end rodado ao menos uma vez (ele aplica as migrações): `bash infra/scripts/seed.sh`. Aplica `infra/seed/*.sql` em ordem do nome, só no PostgreSQL local, sem duplicar nem sobrescrever (pode rodar de novo). Item que cria tabela acrescenta `infra/seed/NN-nome.sql` com `on conflict do nothing`. Para zerar tudo: `docker compose down -v`.

**Aulas na Agenda (RN-AGE-01).** O seed cria turmas, mas nenhuma aula: as aulas nascem pela tarefa diária do back-end, que roda às 03:00 UTC e mantém 8 semanas à frente, e também na hora quando uma turma é criada ou tem os horários alterados pela tela (só as aulas dessa turma). Para ver a Agenda com aulas logo depois do seed, com o back-end rodando: em Operação › Turmas, crie uma turma (ou edite os horários de uma turma do seed e salve). A Agenda mostra as 8 semanas a partir de hoje. Dia sem aula do seed (daqui a 10 dias) cancela as aulas do dia quando elas são geradas.

| Serviço | Para que serve | Endereço (só em 127.0.0.1) |
| --- | --- | --- |
| `postgres` | PostgreSQL 16 | `localhost:5432` · banco, usuário `ctcore`, senha `ctcore_dev` |
| `storage` | Arquivos compatíveis com S3 (SeaweedFS) | `http://localhost:8333` · saúde em `/status` · credenciais fictícias de desenvolvimento em `infra/storage/s3.json` |
| `mailbox` | Caixa de e-mail falsa (Mailpit) | SMTP `localhost:1025` · interface `http://localhost:8025` |

## Front-end

Precisa do Node 24. De dentro de `frontend/`:

```
npm ci               # instala as dependências
npm run dev          # sobe em http://localhost:5173 (MSW ligado; /api vai para o back-end em :8080)
npm run build        # constrói
npm run lint         # lint, com a regra de texto literal
npm run typecheck    # tipos
npm test             # testes (Vitest); com cobertura: npm test -- --coverage
```

Detalhes em `frontend/README.md`.

## CI (GitHub Actions)

O workflow `CI` (`.github/workflows/ci.yml`) roda em todo PR para `dev` e `main`. O status a exigir é `ci-gate`: falha se qualquer etapa falhar ou for cancelada; etapa pulada por pasta conta como verde.

| Etapa | Roda quando | O que exige |
| --- | --- | --- |
| `backend` | `backend/` muda | `mvnw verify`: build, testes `*Test` e `*IT`, contrato OpenAPI, cobertura de linhas 80% (100% nos pacotes `billing`, `payment`, `subscription`, `deactivation`, `isolation`) |
| `frontend` | `frontend/` muda | lint, tipos, build, Vitest com axe nos dois temas, cobertura de linhas 80% (100% em `src/features/{billing,payments,subscription,deactivation}`) |
| `e2e` | `frontend/` muda | Playwright: fluxos `@critical`; no PR para `main`, a suíte completa |
| `security` | sempre | gitleaks (segredos no histórico do PR; falso positivo só por impressão digital em `.gitleaksignore`, com o motivo) e OSV-Scanner (dependências Maven e npm com severidade alta, crítica ou sem nota; filtro em `infra/scripts/osv-high.sh`, provado por `osv-high.test.sh` no próprio job) |

Cada job tem limite de tempo (`timeout-minutes`, logo após `runs-on`): `changes` e `ci-gate` 5 min; `security`, `frontend`, `image-backend` e `image-frontend` 10 min; `backend` e `e2e` 15 min; em `publish.yml`, `changes` e `image` 10 min. Se um job passar a estourar, ajuste o `timeout-minutes` dele em `.github/workflows/ci.yml` ou `.github/workflows/publish.yml`.

`docs/openapi.yaml`, `.github/` e `infra/scripts/changed-areas*` contam como mudança do back-end e do front-end; no PR para `main` rodam todas. A seleção é de `infra/scripts/changed-areas.sh` (prova local: `bash infra/scripts/changed-areas.test.sh`).

Local, o equivalente: `./backend/mvnw -f backend/pom.xml verify` e, em `frontend/`, `npm run lint && npm run typecheck && npm run build && npm run test:cov && npm run e2e`.

## Imagens e Publicação

Cada pasta tem o seu `Dockerfile` (multi-stage): `backend/` gera a imagem do back-end (JRE 21, usuário sem privilégio, API em `8080`, gestão e health em `8081`, configuração só por variável de ambiente) e `frontend/` gera a do front-end (nginx em `8080`, só o `dist`; `/` e rotas do app devolvem `index.html`, `/api` não é servido).

```
docker build -t ctcore-backend backend
docker build -t ctcore-frontend frontend
docker compose -f infra/docker-compose.yml up -d --wait postgres
docker run --rm -e DB_HOST=host.docker.internal -e TWO_FACTOR_ENCRYPTION_KEY="$(openssl rand -base64 32)" -p 8080:8080 -p 8081:8081 ctcore-backend   # curl localhost:8081/actuator/health
docker run --rm -p 8082:8080 ctcore-frontend                                                # http://localhost:8082
```

`TWO_FACTOR_ENCRYPTION_KEY` (32 bytes em base64) é obrigatória fora do perfil `local`; gere uma com `openssl rand -base64 32`. Perder a chave invalida os segredos das duas etapas já guardados. No Linux, em vez de `host.docker.internal`, use `--network host` e dispense o `DB_HOST`. Outras variáveis: `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`, `POSTGRES_PORT`, `MANAGEMENT_PORT`.

O `CI` constrói a imagem do back-end quando a etapa `backend` roda e a do front-end quando a `frontend` roda; as duas entram no `ci-gate`.

### Publicação

O workflow `Publish` (`.github/workflows/publish.yml`) roda em push para `dev` e `main` e em disparo manual. Constrói a imagem de cada pasta alterada (no disparo manual, as duas) e, havendo chaves, envia com as etiquetas `<branch>` e o SHA do commit (sem `latest`). **Sem as chaves o envio é pulado, o run termina verde e o resumo diz "publicação pulada: faltam chaves".** Hoje não há registro: o fluxo está pronto e fica assim até a #192.

| Nome | Tipo | Conteúdo |
| --- | --- | --- |
| `PUBLISH_REGISTRY` | variável | endereço do registro com o caminho, ex.: `registro.exemplo.com/ctcore`; as imagens saem como `<valor>/ctcore-backend` e `<valor>/ctcore-frontend` |
| `PUBLISH_REGISTRY_USER` | segredo | usuário do registro |
| `PUBLISH_REGISTRY_TOKEN` | segredo | token ou senha do registro |

Para ligar: cadastre os três em Settings, Secrets and variables, Actions; se o registro pedir, acrescente `packages: write` em `permissions` do `publish.yml`. Para testar: Actions, Publish, Run workflow, num branch descartável; o resumo do run lista as etiquetas enviadas. Sem registro de verdade, o mesmo script roda contra um registro local: `docker run -d -p 5000:5000 registry:2`, depois `PUBLISH_REGISTRY=localhost:5000/teste PUBLISH_REGISTRY_USER=x PUBLISH_REGISTRY_TOKEN=x bash infra/scripts/publish-image.sh frontend dev` e `curl localhost:5000/v2/_catalog`.

Fora deste passo, e da #192: escolher o registro, publicar em ambientes, aprovação humana de `main` e backup (RNF-17, #193).

## Branches

- `dev` — branch de desenvolvimento e branch padrão. Todo código é escrito aqui: cada issue sai de `dev` num branch próprio e volta para `dev` por PR.
- `main` — produção. Só recebe o `dev` por PR com o método "Merge", ao fim de cada sprint, depois da revisão completa da sprint pelo dono. Ninguém faz commit direto em `main`.

## Planejamento

O trabalho está nas issues deste repositório, um marco (milestone) por sprint, na ordem da especificação 1.2.

- Especificação (regra de cada código): `docs/especificacao/`
- Ordem, qualidade e definição de pronto: `docs/plano-de-sprints.md`
- Regras para os agentes: `AGENTS.md`
