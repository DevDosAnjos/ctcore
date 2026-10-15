# infra

Ambiente local, scripts e publicação do ctcore.

- `docker-compose.yml`: PostgreSQL, armazenamento compatível com S3 (SeaweedFS) e caixa de e-mail falsa (Mailpit).
- `.env.example`: portas e credenciais do PostgreSQL só de desenvolvimento. O compose já usa esses valores como padrão; copie para `.env` só para mudar algo.

Como subir, parar e limpar: README da raiz.
