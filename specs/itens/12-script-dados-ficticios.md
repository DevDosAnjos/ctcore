# 12 — Script de dados fictícios

Origem: issue #12 (Sprint 00 · Fundação), `docs/especificacao/00-visao-geral.md` §9 (ambiente de teste só com dados fictícios, criados por script) e RNF-01/RNF-38 (organização de referência). Branch: `item/12-script-dados-ficticios`.

## 1. Objetivo

Entregar o script que popula o banco local com dados fictícios, repetível e sem tocar em dado real, para o desenvolvimento e para as telas e testes das sprints seguintes. Hoje o banco só tem `organization_feature` e `shedlock` (a tabela de organizações vem na #13), então o script cria um conjunto pequeno de organizações fictícias sobre o que existe e deixa o modo de acrescentar dados a cada item que criar tabelas.

Fora do escopo: a organização de referência (10 unidades, 2.000 alunos, 5 anos de cobranças, 200 organizações), que a issue coloca "a partir da Sprint 13" e depende de tabelas que ainda não existem; medição de desempenho (RNF-38, #112); dados para ambiente publicado (não há, até a #192).

## 2. Requisitos

- R1: um comando único, `bash infra/scripts/seed.sh`, aplica os dados fictícios no PostgreSQL do `docker-compose` local (D1).
- R2: o script cria 3 organizações fictícias com identificadores fixos e com recursos (`organization_feature`) em estados variados (D2).
- R3: o script é idempotente: rodar duas vezes não duplica nem altera dados já existentes (D3).
- R4: os dados são sintéticos e reproduzíveis: mesmos identificadores e valores a cada execução, sem cópia de produção (`00-visao-geral.md` §9).
- R5: o script só atua no banco do `docker-compose` local e para com mensagem clara se o ambiente não estiver de pé ou o esquema não estiver migrado (D1).
- R6: cada item futuro que cria tabela acrescenta o seu arquivo em `infra/seed/`, aplicados em ordem; o modo de fazer isso está documentado (D4).
- R7: `README.md` e `AGENTS.md` (Comandos) dizem como rodar o script (issue: "Pronto quando").

## 3. Critérios de aceite

- CA1 (R1, R2): dado o ambiente de pé e o back-end já migrado, quando se roda `bash infra/scripts/seed.sh`, então existem 3 organizações fictícias (identificadores fixos) em `organization_feature`, cada uma com ao menos um recurso, com ligados e desligados.
- CA2 (R3): dado o script já rodado, quando roda de novo, então a contagem de linhas e o conteúdo não mudam e o comando termina com sucesso.
- CA3 (R3): dado um recurso de organização fictícia que alguém alterou depois do primeiro run, quando o script roda de novo, então o valor alterado é mantido.
- CA4 (R4): dado um banco vazio migrado em duas execuções independentes, quando o script roda em cada uma, então os identificadores e valores são os mesmos.
- CA5 (R5): dado o PostgreSQL parado, quando se roda o script, então ele sai com código diferente de zero e diz para subir o ambiente; dado o esquema sem a tabela `organization_feature` (back-end nunca rodou), então sai com código diferente de zero e diz para rodar o back-end antes.
- CA6 (R6): dado um novo arquivo `infra/seed/NN-nome.sql`, quando o script roda, então ele é aplicado depois dos de número menor, na ordem do nome.
- CA7 (R7): dado o `README.md` e o `AGENTS.md`, quando se lê, então trazem o comando e o pré-requisito (back-end migrado).

## 4. Plano técnico

- Dados: não há migração. Os dados entram por arquivos SQL em `infra/seed/`, com `on conflict do nothing`. Identificadores determinísticos: `md5('ctcore-seed-org-' || n)::uuid` para n = 1..3.
- API: não muda.
- Interface: não muda.
- Segurança: só dados sintéticos, sem pessoa real, e-mail real ou segredo. O script usa o contêiner `postgres` do compose local (`docker compose exec`), nunca uma URL externa; a credencial é a de desenvolvimento já em `infra/.env.example`.
- Arquivos: existem e mudam: `README.md`, `AGENTS.md` (Comandos), `infra/README.md`. A criar: `infra/scripts/seed.sh` (verifica o contêiner e a tabela, aplica `infra/seed/*.sql` em ordem do nome com `psql -v ON_ERROR_STOP=1 --single-transaction`), `infra/seed/01-organizations.sql` (3 organizações e seus recursos), `backend/src/test/java/br/com/ctcore/db/SeedDataIT.java`.
- Reuso: `docker-compose.yml` (serviço `postgres`, com `psql` dentro; não exige `psql` instalado na máquina); `PostgresIT` (Testcontainers) e `organization_feature` (V2) para o teste; padrão de script e de teste `.sh` de `infra/scripts/changed-areas.sh`.
- Riscos: (1) o teste lê `infra/seed/` a partir de `backend/`; o caminho relativo quebra se a estrutura mudar: o teste falha e avisa. (2) Os arquivos de seed ficam parados quando uma migração futura muda a tabela: o `SeedDataIT` roda no CI e reprova o PR, forçando o ajuste junto. (3) Chaves fixas de `organization_feature` ainda sem a tabela de organizações (#13): quando a #13 criar a chave estrangeira, o seed da organização tem de vir antes (a ordem do nome cuida disso, D4).

## 5. Tarefas

- [x] T1: criar `infra/seed/01-organizations.sql` com 3 organizações fictícias (n = 1..3) e 2 a 3 recursos cada, em ligado e desligado, `on conflict do nothing` — cobre CA1, CA3, CA4 — prova: `SeedDataIT`.
- [x] T2: criar `backend/src/test/java/br/com/ctcore/db/SeedDataIT.java`: aplica os arquivos de `infra/seed/` em ordem do nome sobre o banco migrado do `PostgresIT`; confere as 3 organizações e os recursos (CA1), a segunda aplicação sem mudança de contagem (CA2), a alteração mantida (CA3), a mesma lista após limpar a tabela e reaplicar (CA4) e a ordem de aplicação com um arquivo extra temporário (CA6) — cobre CA1, CA2, CA3, CA4, CA6 — prova: `./backend/mvnw -f backend/pom.xml verify`.
- [x] T3: criar `infra/scripts/seed.sh` (pré-checagens de CA5, aplicação em ordem, resumo com quantas organizações existem) e provar na mão — cobre CA2, CA5 — prova: com `docker compose -f infra/docker-compose.yml up -d --wait` e o back-end rodado uma vez, `bash infra/scripts/seed.sh` duas vezes termina com sucesso; com `docker compose stop postgres` sai com código diferente de zero e diz para subir o ambiente; em banco sem `organization_feature` sai com código diferente de zero.
- [x] T4: atualizar `README.md`, `AGENTS.md` (Comandos) e `infra/README.md` com o comando, o pré-requisito e o jeito de acrescentar `infra/seed/NN-nome.sql` — cobre CA7 — prova: leitura dos arquivos.

## 6. Decisões

- D1: o script é shell sobre `docker compose exec postgres psql`, não código Java nem ferramenta nova. Motivo: não exige `psql` na máquina, não adiciona dependência, só alcança o banco local e roda igual no Windows (Git Bash) e no CI. Alternativa: componente Spring em perfil `seed`, descartada por ligar o seed ao ciclo da aplicação e precisar de código de produção para um dado só de desenvolvimento.
- D2: com o esquema atual, "criar organizações" é criar 3 identificadores de organização com recursos em `organization_feature`, a única tabela por organização que existe. Motivo: a tabela de organizações é da #13; inventá-la aqui seria ir além do item. Quando a #13 chegar, ela acrescenta `infra/seed/` com as linhas reais da organização e das unidades. Alternativa: criar uma tabela provisória, descartada por duplicar a #13.
- D3: idempotência por `on conflict do nothing`, nunca `do update`. Motivo: preserva o que o desenvolvedor mudou à mão e nunca apaga nem sobrescreve. Alternativa: apagar e recriar, descartada por destruir dado de quem está testando. Para zerar tudo, continua valendo `docker compose down -v`.
- D4: o volume da organização de referência (10 unidades, 2.000 alunos, 5 anos de cobranças, 200 organizações) fica para a Sprint 13, como a própria issue diz; cada item posterior que cria tabelas acrescenta o seu `infra/seed/NN-nome.sql`, e o `SeedDataIT` roda no CI para que um seed desatualizado reprove o PR. Motivo: não dá para gerar o que as tabelas ainda não guardam. Alternativa: um gerador único feito de uma vez na Sprint 13, descartada por esconder quebra de seed por semanas.
- D5 (muda o comportamento): o script não faz parte de nenhum fluxo automático (subir o ambiente, iniciar o back-end): é comando explícito do desenvolvedor. Motivo: dado fictício nunca deve entrar por acidente em outro banco, e o ambiente que vem do compose continua vazio. Alternativa: rodar na inicialização do perfil `local`, descartada por esse motivo.
- Suposição: o back-end é rodado ao menos uma vez antes do script, pois é ele que aplica as migrações (Flyway). Se o dono preferir que o script rode sozinho em banco vazio, é preciso trazer as migrações para o script; fica fora deste item.

## Construção

- T1 — arquivos: `infra/seed/01-organizations.sql` — prova: `SeedDataIT` → 5 testes passam
- T2 — arquivos: `backend/src/test/java/br/com/ctcore/db/SeedDataIT.java` — prova: `./backend/mvnw -f backend/pom.xml verify` → 20 testes, BUILD SUCCESS
- T3 — arquivos: `infra/scripts/seed.sh` — prova: à mão, com compose local: duas execuções seguidas → sucesso, 3 organizações, 8 linhas; postgres parado → saída 1 e manda subir o ambiente; tabela `organization_feature` renomeada → saída 1 e manda rodar o back-end (tabela restaurada e linhas do seed removidas depois)
- T4 — arquivos: `README.md`, `AGENTS.md`, `infra/README.md` — prova: leitura dos arquivos
Regressão: `./backend/mvnw -f backend/pom.xml verify` → 20 testes, 0 falhas, JaCoCo ok. Front-end não tocado, não rodou.
Desvios: nenhum.

## Verificação

### Rodada 1 — OK — 2026-10-15, estado 017d87121f6f48c8a4f35fa10060e6df72f660f5

| Critério | Status | Evidência |
|---|---|---|
| CA1 | PASSOU | `seed.sh` no compose local: 3 organizações, 8 linhas; `SeedDataIT.createsOrganizations` passa |
| CA2 | PASSOU | `seed.sh` duas vezes, rc=0, 8 linhas; `SeedDataIT.idempotent` |
| CA3 | PASSOU | `SeedDataIT.keepsChanges`; SQL usa `on conflict do nothing` |
| CA4 | PASSOU | `SeedDataIT.reproducible`; ids por `md5` fixo |
| CA5 | PASSOU | postgres parado: rc=1 e manda subir o ambiente; tabela renomeada: rc=1 e manda rodar o back-end (tabela restaurada depois) |
| CA6 | PASSOU | `SeedDataIT.order` (arquivo extra temporário); script usa glob ordenado |
| CA7 | PASSOU | leitura do diff de `README.md`, `AGENTS.md`, `infra/README.md` |

Comandos: `./backend/mvnw -f backend/pom.xml verify` → sucesso, SeedDataIT 5 testes, 0 falhas; `bash infra/scripts/seed.sh` x2 → rc=0; cenários de CA5 → rc=1. Front-end não tocado, não rodou.
Achados:
- sugestão, código, `infra/scripts/seed.sh` — o glob `seed/*.sql` sem arquivos passaria o literal ao loop; falha com erro de arquivo, aceitável.

## Entrega

- Acrescenta `bash infra/scripts/seed.sh`: aplica `infra/seed/*.sql` em ordem do nome no PostgreSQL do compose local, em transação única e idempotente (`on conflict do nothing`).
- `infra/seed/01-organizations.sql` cria 3 organizações fictícias de identificador fixo, com recursos ligados e desligados em `organization_feature`.
- Falha com código diferente de zero e orientação se o PostgreSQL estiver parado ou se o back-end ainda não migrou o esquema.
- Verificação: `SeedDataIT` (5 testes) e `./backend/mvnw -f backend/pom.xml verify` (20 testes, JaCoCo ok); script rodado à mão duas vezes e nos cenários de erro. Front-end não tocado.
- Limitações: o volume da organização de referência fica para a Sprint 13; as organizações reais e unidades entram com a #13. O script não roda em nenhum fluxo automático.
