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

## Branches

- `dev` — branch de desenvolvimento e branch padrão. Todo código é escrito aqui: cada issue sai de `dev` num branch próprio e volta para `dev` por PR.
- `main` — produção. Só recebe o `dev` por PR com o método "Merge", ao fim de cada sprint, depois da revisão completa da sprint pelo dono. Ninguém faz commit direto em `main`.

## Planejamento

O trabalho está nas issues deste repositório, um marco (milestone) por sprint, na ordem da especificação 1.2.

- Especificação (regra de cada código): `docs/especificacao/`
- Ordem, qualidade e definição de pronto: `docs/plano-de-sprints.md`
- Regras para os agentes: `AGENTS.md`
