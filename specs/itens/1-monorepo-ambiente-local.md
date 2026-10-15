# 1 — Estrutura do monorepo e ambiente local

Origem: issue #1 (Sprint 00 · Fundação). Branch: `item/1-monorepo-ambiente-local`.

## 1. Objetivo

Deixar o monorepo com as quatro pastas previstas, cada uma com um README curto, e um `docker compose up` que sobe PostgreSQL, armazenamento compatível com S3 e caixa de e-mail falsa para o desenvolvimento local. O README da raiz explica como subir o ambiente com um comando. É a base para os demais itens da Sprint 00.

Fora do escopo: projeto Spring Boot (#2), projeto Vite/React (#7), CI (#10), hospedagem e publicação (#11, #192), criação de buckets e esquema do banco (#13, #16), dados fictícios (#12).

## 2. Requisitos

- R1: existem `backend/`, `frontend/`, `infra/` e `docs/`, cada uma com um `README.md` curto que diz o que a pasta contém.
- R2: `infra/docker-compose.yml` define os serviços PostgreSQL, armazenamento compatível com S3 e caixa de e-mail falsa (D1, D2).
- R3: `docker compose up` (a partir de `infra/`) sobe os três serviços saudáveis, sem configuração manual (D3).
- R4: o PostgreSQL e o armazenamento persistem dados em volumes nomeados e usam credenciais só de desenvolvimento, lidas de `infra/.env.example` (D3).
- R5: o `AGENTS.md` traz a regra de branches e a estrutura do monorepo.
- R6: o README da raiz diz como subir, parar e limpar o ambiente local e lista as portas e endereços de cada serviço.
- R7: `AGENTS.md` e README são atualizados quando muda como rodar, testar ou publicar; neste item, a seção "Comandos" do `AGENTS.md` passa a trazer o comando de subir o ambiente.

## 3. Critérios de aceite

- CA1 (R1): dado o repositório após o item, quando se lista a raiz, então `backend/`, `frontend/`, `infra/` e `docs/` existem e cada uma tem `README.md` não vazio.
- CA2 (R2): dado `infra/docker-compose.yml`, quando se roda `docker compose -f infra/docker-compose.yml config`, então sai sem erro e lista os serviços `postgres`, `storage` e `mailbox`.
- CA3 (R3): dado o Docker disponível e nenhum `.env` criado, quando se roda `docker compose up -d --wait` em `infra/`, então os três serviços ficam `healthy`.
- CA4 (R3): dado o ambiente no ar, quando se conecta ao PostgreSQL na porta publicada com o usuário de desenvolvimento, então `select 1` responde.
- CA5 (R3): dado o ambiente no ar, quando se chama o endpoint de saúde do armazenamento e se abre a interface da caixa de e-mail, então ambos respondem 200.
- CA6 (R3): dado o ambiente no ar, quando se envia um e-mail SMTP de teste para a porta SMTP da caixa, então ele aparece na interface web dela.
- CA7 (R4): dado o ambiente com dados gravados, quando se roda `docker compose down` e depois `up`, então os dados continuam; com `down -v` são apagados.
- CA8 (R4): dado o repositório, quando se busca por segredos, então só existem valores de desenvolvimento em `infra/.env.example` e nenhum `.env` real está versionado (`.gitignore` o exclui).
- CA9 (R5): dado o `AGENTS.md`, quando se lê, então há a estrutura do monorepo e a regra de branches (já presentes; conferir e manter).
- CA10 (R6, R7): dado um clone limpo, quando se segue só o README da raiz, então o ambiente sobe com um comando e as portas documentadas batem com as do compose; a seção "Comandos" do `AGENTS.md` traz esse comando.

## 4. Plano técnico

- Dados: não muda (o esquema vem na #2/#13). Só sobe o PostgreSQL com um banco de desenvolvimento vazio.
- API: não muda.
- Interface: não muda.
- Segurança: credenciais apenas de desenvolvimento, em `infra/.env.example`; `.env` ignorado no Git; portas publicadas só em `127.0.0.1`; imagens com versão fixa (nada de `latest`); nenhum segredo real no repositório.
- Arquivos: a alterar (existem): `README.md`, `AGENTS.md` (seção "Comandos"; a estrutura e a regra de branches já estão). A criar: `backend/README.md`, `frontend/README.md`, `infra/README.md`, `docs/README.md`, `infra/docker-compose.yml`, `infra/.env.example`, `.gitignore` na raiz (`.env`, e o que os itens seguintes precisarem entra neles). `docs/` já existe com `especificacao/` e `plano-de-sprints.md`, sem README. `backend/` e `frontend/` não existem e precisam de um arquivo para o Git versioná-las (o README basta).
- Reuso: nada no código; imagens oficiais publicadas (ver D1, D2).
- Riscos: porta em uso na máquina (portas configuráveis por variável no `.env.example`); Docker ausente na máquina do agente impede CA3 a CA7 (a prova fica registrada como não executada, não como aprovada); variável de versão de imagem desatualizada (fixar versão exata e conferir que existe no registro).

## 5. Tarefas

- [x] T1: criar `backend/README.md`, `frontend/README.md`, `infra/README.md` e `docs/README.md`, cada um com poucas linhas (o que a pasta tem e como se liga ao resto) — cobre CA1 — prova: `ls` das quatro pastas mostra o README de cada.
- [x] T2: criar `infra/docker-compose.yml` com `postgres`, `storage` e `mailbox`, volumes nomeados, healthcheck em cada serviço, portas em `127.0.0.1` e variáveis vindas do `.env` com padrão — cobre CA2, CA3, CA4, CA5, CA6, CA7 — prova: `docker compose -f infra/docker-compose.yml config`, depois `up -d --wait`, `pg_isready`/`select 1`, `curl` no health do storage e na interface da caixa, e-mail SMTP de teste, `down` e `up` com os dados preservados.
- [x] T3: criar `infra/.env.example` (credenciais e portas só de desenvolvimento) e `.gitignore` na raiz ignorando `.env` — cobre CA8 — prova: `git check-ignore infra/.env` retorna o caminho; `git ls-files | grep -i '\.env$'` vazio.
- [x] T4: atualizar o `README.md` da raiz (subir com um comando, parar, limpar, tabela de portas e endereços) e a seção "Comandos" do `AGENTS.md` com o comando de subir o ambiente; conferir estrutura e regra de branches — cobre CA9, CA10 — prova: leitura contra o compose; seguir o README em pasta limpa (ou `git clone` local) e subir o ambiente.

## 6. Decisões

- D1: armazenamento compatível com S3 local = MinIO (imagem oficial, versão fixa). Motivo: é o padrão para S3 local, tem health endpoint e console. Alternativa: LocalStack (mais pesado, mais serviços do que o necessário). Vale só para o ambiente local; o `AGENTS.md` proíbe citar fornecedor nas regras de conexão externa, e isto não é regra de negócio.
- D2: caixa de e-mail falsa = Mailpit (imagem oficial, versão fixa; SMTP e interface web). Motivo: leve e com API HTTP para teste automatizado. Alternativa: MailHog (sem manutenção).
- D3: portas publicadas apenas em `127.0.0.1`, credenciais de desenvolvimento em `infra/.env.example`, copiado para `.env` só se alguém quiser mudar; o compose usa os mesmos valores como padrão para o comando funcionar sem copiar nada. Motivo: "um comando" sem passo manual. Alternativa: exigir `cp .env.example .env` antes (um passo a mais).
- D4: o comando documentado roda de dentro de `infra/` (`docker compose up -d --wait`) e também da raiz com `-f infra/docker-compose.yml`. Motivo: a issue pede `docker compose up` e o compose mora em `infra/`. Alternativa: compose na raiz, descartada por contrariar a estrutura da issue.
- D5: PostgreSQL com versão principal 16 (imagem oficial `postgres`, tag fixa). Motivo: a especificação não fixa versão; 16 é estável e compatível com Spring Boot 3 e Testcontainers. Alternativa: 17.
- D6: não criar bucket nem esquema neste item. Motivo: pertencem à #16 (arquivos por organização) e à #2/#13 (banco).
- Suposição: o Docker está instalado na máquina que construir e verificar; se não estiver, CA3 a CA7 ficam sem prova executada e o item não pode ser dado como verificado. Se a especificação 1.2 exigir uma versão específica de PostgreSQL, D5 muda.

## Construção

- T1 — arquivos: `backend/README.md`, `frontend/README.md`, `infra/README.md`, `docs/README.md` — prova: `ls` das quatro pastas → cada uma com README não vazio.
- T2 — arquivos: `infra/docker-compose.yml` — prova: `docker compose config --services` → postgres, storage, mailbox; `up -d --wait` sem `.env` → três serviços healthy; `psql -c "select 1"` → 1; `curl :8333/` e `:8333/status` → 200, `curl :8025/` → 200; e-mail SMTP de teste por `curl smtp://127.0.0.1:1025` apareceu em `/api/v1/messages` da Mailpit; tabela criada, `down` + `up` → dado mantido; `down -v` + `up` → tabela não existe mais.
- T3 — arquivos: `infra/.env.example`, `.gitignore` (já existia com `.env`/`.env.*` e `!.env.example`) — prova: `git check-ignore infra/.env` → `infra/.env`; `git ls-files | grep -i '\.env$'` → vazio.
- T4 — arquivos: `README.md` (seção "Ambiente local"), `AGENTS.md` (seção "Comandos") — prova: leitura contra o compose (portas 5432, 8333, 1025, 8025 batem); o comando documentado foi o usado em T2. Não rodou: clone limpo separado; o ambiente foi subido da própria pasta de trabalho, sem `.env`.
Regressão: não há testes anteriores (repositório só com documentos); nada a comparar.
Desvios:
- D1 (MinIO) inviável: `minio/minio` não existe mais no Docker Hub (`pull access denied`) e `quay.io/minio/minio` responde 401 para as versões testadas. Usado SeaweedFS `chrislusf/seaweedfs:3.80` (`server -s3`, porta 8333), com healthcheck em `http://127.0.0.1:8333/status`. Sem console web; a variável `STORAGE_PORT` substitui `MINIO_PORT`/`MINIO_CONSOLE_PORT`.
- O S3 do SeaweedFS ficou sem autenticação (as variáveis `AWS_ACCESS_KEY_ID`/`AWS_SECRET_ACCESS_KEY` não surtiram efeito: `GET /` anônimo lista buckets), então não há credencial do armazenamento no `.env.example`. Só vale para o desenvolvimento local, em 127.0.0.1; a #16 define bucket e acesso. Documentado no README da raiz.
- Imagens: `postgres:16.4`, `axllent/mailpit:v1.20.5`.
- Docker Desktop estava parado; foi iniciado nesta sessão.
Não é do item: `AGENTS.md` já tinha a seção "Issues" sem commit (da fase 1).

## Verificação

### Rodada 1 — OK — 2026-10-15, estado c268273a9c6389c88f669c202dd6cd1c2cd5fdc1

| Critério | Status | Evidência |
|---|---|---|
| CA1 | PASSOU | `backend/`, `frontend/`, `infra/`, `docs/` têm `README.md` com conteúdo |
| CA2 | PASSOU | `docker compose config --services` → mailbox, postgres, storage |
| CA3 | PASSOU | `down -v` e `up -d --wait` sem `.env` → três serviços Healthy |
| CA4 | PASSOU | `psql -U ctcore -d ctcore -c "select 1"` → 1 |
| CA5 | PASSOU | curl `:8333/status` 200, `:8333/` 200, `:8025/` 200 |
| CA6 | PASSOU | e-mail via `curl smtp://127.0.0.1:1025` apareceu em `/api/v1/messages` (total 1) |
| CA7 | PASSOU | tabela criada; `down` + `up` → dado 7 mantido; `down -v` + `up` → tabela não existe |
| CA8 | PASSOU | `git check-ignore infra/.env` → `infra/.env`; `git ls-files \| grep '\.env$'` vazio; só valores de dev no `.env.example` |
| CA9 | PASSOU | `AGENTS.md` traz a estrutura do monorepo e a regra de branches |
| CA10 | PASSOU | portas 5432/8333/1025/8025 do README batem com o compose; "Comandos" do `AGENTS.md` traz o comando. Clone limpo não repetido (mesmo comando usado em CA3) |

Comandos: os acima, todos com o resultado indicado; ambiente derrubado com `down -v` ao fim. Regressão: não há testes anteriores.
Achados:
- sugestão, código, `infra/docker-compose.yml` serviço `storage` — S3 sem autenticação (desvio D1 documentado); a #16 deve definir credencial e bucket.
- sugestão, código, `README.md` — linha em branco dupla antes de "Ambiente local".

## Entrega

- Estrutura do monorepo (`backend/`, `frontend/`, `infra/`, `docs/`, cada uma com README) e ambiente local com um comando: `docker compose up -d --wait` em `infra/` sobe PostgreSQL 16.4, armazenamento compatível com S3 e caixa de e-mail falsa (Mailpit), com portas só em 127.0.0.1 e dados em volume.
- Verificado: CA1 a CA10 passaram na rodada 1 (subida sem `.env`, `select 1`, e-mail SMTP recebido, persistência entre `down`/`up` e limpeza com `down -v`).
- Decisão: SeaweedFS no lugar do MinIO (imagem do MinIO indisponível); o S3 local fica sem autenticação, só em 127.0.0.1. A #16 define bucket e credencial.
- Limitação: clone limpo não repetido; sem testes automatizados (repositório só tinha documentos).
- `AGENTS.md` inclui a seção "Comandos" atualizada e a seção "Issues" que vinha da fase 1.

Closes #1
