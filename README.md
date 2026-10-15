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

Da raiz: `docker compose -f infra/docker-compose.yml up -d --wait`. Para mudar portas ou credenciais, copie `infra/.env.example` para `infra/.env`.

| Serviço | Para que serve | Endereço (só em 127.0.0.1) |
| --- | --- | --- |
| `postgres` | PostgreSQL 16 | `localhost:5432` · banco, usuário `ctcore`, senha `ctcore_dev` |
| `storage` | Arquivos compatíveis com S3 (SeaweedFS) | `http://localhost:8333` · saúde em `/status` · sem autenticação, só para desenvolvimento |
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
| `security` | sempre | gitleaks (segredos no histórico do PR) e OSV-Scanner (dependências Maven e npm com severidade alta ou crítica) |

`docs/openapi.yaml`, `.github/` e `infra/scripts/changed-areas*` contam como mudança do back-end e do front-end; no PR para `main` rodam todas. A seleção é de `infra/scripts/changed-areas.sh` (prova local: `bash infra/scripts/changed-areas.test.sh`).

Local, o equivalente: `./backend/mvnw -f backend/pom.xml verify` e, em `frontend/`, `npm run lint && npm run typecheck && npm run build && npm run test:cov && npm run e2e`.

## Branches

- `dev` — branch de desenvolvimento e branch padrão. Todo código é escrito aqui: cada issue sai de `dev` num branch próprio e volta para `dev` por PR.
- `main` — produção. Só recebe o `dev` por PR com o método "Merge", ao fim de cada sprint, depois da revisão completa da sprint pelo dono. Ninguém faz commit direto em `main`.

## Planejamento

O trabalho está nas issues deste repositório, um marco (milestone) por sprint, na ordem da especificação 1.2.

- Especificação (regra de cada código): `docs/especificacao/`
- Ordem, qualidade e definição de pronto: `docs/plano-de-sprints.md`
- Regras para os agentes: `AGENTS.md`
