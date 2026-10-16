# infra

Ambiente local, scripts e publicação do ctcore.

- `docker-compose.yml`: PostgreSQL, armazenamento compatível com S3 (SeaweedFS) e caixa de e-mail falsa (Mailpit).
- `.env.example`: portas e credenciais do PostgreSQL só de desenvolvimento. O compose já usa esses valores como padrão; copie para `.env` só para mudar algo.
- `scripts/seed.sh` e `seed/`: dados fictícios no PostgreSQL local (`bash infra/scripts/seed.sh`, depois de subir o ambiente e rodar o back-end uma vez). Para acrescentar dados, crie `seed/NN-nome.sql` (aplicados em ordem do nome, `on conflict do nothing`).

Como subir, parar e limpar: README da raiz.

Antes de iniciar o back-end, execute `bash infra/scripts/prepare-runtime-db.sh`. Prepara o papel operacional de forma idempotente, inclusive com volumes existentes, sem remover dados. `DB_RUNTIME_USER`/`DB_RUNTIME_PASSWORD` são separados das credenciais administrativas de migração/seed. Ao personalizar, exporte as variáveis para o script e o Java; detalhes em `backend/README.md`.
