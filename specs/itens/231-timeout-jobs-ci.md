# 231 — Limites de tempo (timeout) nos jobs do CI

Origem: issue #231 (Sprint 02); RNF-35, RNF-36 e RNF-37 (esteira de CI, `docs/especificacao/00-visao-geral.md`). Branch: `item/231-timeout-jobs-ci`.

## 1. Objetivo

Impedir que um job travado do GitHub Actions consuma minutos por horas (padrão de 360 min), dando a cada job um `timeout-minutes`. Fora do item: mudar o que qualquer job faz, ordem, `needs`, gatilhos ou permissões.

## 2. Requisitos

- R1: cada job de `.github/workflows/ci.yml` tem `timeout-minutes`: `changes` e `ci-gate` 5; `security`, `frontend`, `image-backend` e `image-frontend` 10; `backend` e `e2e` 15.
- R2: cada job de `.github/workflows/publish.yml` tem `timeout-minutes`: `changes` e `image` 10.
- R3: nada além do `timeout-minutes` muda nos workflows.
- R4: o README diz onde ajustar o limite se um job passar a estourar.
- R5: um PR de prova roda a esteira inteira dentro dos limites, com `ci-gate` verde.

## 3. Critérios de aceite

- CA1 (R1, R2): dado os dois arquivos, então todo job sob `jobs:` tem `timeout-minutes` com o valor acima (conferência por `grep`/script curto de YAML na verificação).
- CA2 (R3): o diff dos workflows contém apenas linhas `timeout-minutes` adicionadas.
- CA3 (R4): o README, na seção "CI (GitHub Actions)", cita `timeout-minutes` e os dois arquivos.
- CA4 (R5): o PR do item termina com `ci-gate` verde, nenhum job cancelado por tempo.

## 4. Plano técnico

- **Dados / API / Front-end / Segurança.** Não mudam.
- **Infra.** Acrescentar `timeout-minutes: N` logo após `runs-on` em cada job de `.github/workflows/ci.yml` (`changes`, `backend`, `frontend`, `e2e`, `security`, `image-backend`, `image-frontend`, `ci-gate`) e `.github/workflows/publish.yml` (`changes`, `image`). Nota: `changes` aciona `.github/` como mudança de back-end e front-end, então o PR de prova roda todos os jobs.
- **Documentação.** `README.md`, seção "CI (GitHub Actions)": uma frase com a tabela de limites e onde mudar (`timeout-minutes` no job, em `ci.yml` e `publish.yml`).
- **Riscos.** Limite apertado reprovar run legítimo: conferir a duração dos últimos runs (`gh run list`) antes de fechar; se algum passar de ~70% do limite, registrar em Decisões.

## 5. Tarefas

- [x] T1: Adicionar `timeout-minutes` aos jobs de `ci.yml` e `publish.yml` — cobre CA1, CA2 — prova: `grep -c timeout-minutes` (8 e 2) e `git diff` só com essas linhas.
- [x] T2: Documentar no README onde ajustar — cobre CA3 — prova: leitura da seção.
- [x] T3: Conferir duração dos runs recentes contra os limites — cobre risco — prova: `gh run list`/`gh run view`.
- [ ] T4: PR de prova com `ci-gate` verde — cobre CA4 — prova: `gh pr checks`.

## 6. Decisões

- D1: os valores são os da issue, sem alteração. Suposição: bastam para os runs atuais; T3 confere.
- D2: o único PR do item é o PR de prova (o item mexe em `.github/`, que já dispara todos os jobs); não se abre PR extra.

## 7. Construção

- T1: `.github/workflows/ci.yml` (8 jobs) e `.github/workflows/publish.yml` (2 jobs) com `timeout-minutes` após `runs-on`. Prova: `grep -c timeout-minutes` = 8 e 2; `git diff --numstat` = 8/0 e 2/0 (só linhas adicionadas); valores conferidos job a job (changes 5, backend 15, frontend 10, e2e 15, security 10, image-backend 10, image-frontend 10, ci-gate 5; publish: changes 10, image 10). Validação por parser YAML não rodou (Python ausente na máquina).
- T2: `README.md`, seção "CI (GitHub Actions)": parágrafo com a tabela de limites e onde ajustar (`ci.yml` e `publish.yml`). Prova: leitura.
- T3: `gh run list --limit 15`: CI de 2 a 6 min no total (maior: 20:35 a 20:41, ~6 min 18 s), Publish de 1 a 2 min; todos bem abaixo dos limites por job (nenhum passa de 70%). Uma falha de CI (19:49) é anterior e sem relação (duração 2 min 40 s, não por tempo).
- T4: não marcada: o PR de prova é aberto na fase 5 (`gh pr checks`).
- Regressão: item só altera YAML de workflow e README; nada de back-end/front-end rodou localmente. A prova real é o CI do PR.

## 8. Verificação

### Rodada 1 — NAO_CONFERIDO — 39e6df46e7dbbf945893fc49c6cc1a2c05df2fa3

| Critério | Status | Evidência |
|---|---|---|
| CA1 | PASSOU | awk sobre `jobs:`: ci.yml changes 5, backend 15, frontend 10, e2e 15 (visto no diff; o nome com dígito escapou do padrão do awk), security 10, image-backend 10, image-frontend 10, ci-gate 5; publish.yml changes 10, image 10. 8 e 2 jobs, todos com limite |
| CA2 | PASSOU | `git diff --numstat` dos workflows: 8/0 e 2/0; as únicas linhas alteradas são `timeout-minutes` (contagem de outras linhas = 0) |
| CA3 | PASSOU | README, seção "CI (GitHub Actions)": parágrafo cita `timeout-minutes`, `.github/workflows/ci.yml` e `.github/workflows/publish.yml` com a tabela de limites |
| CA4 | NÃO CONFERIDO | exige o PR aberto na fase 5 (`gh pr checks`); não há PR ainda e não é limitação aceita no AGENTS.md |

Comandos: awk/grep sobre os dois YAML e `git diff HEAD` (resultados acima). Parser YAML não rodou (Python ausente); estrutura conferida pelo diff, que só insere uma linha após `runs-on` com a indentação do job. Testes de back-end/front-end não rodados: o item só altera YAML e README; a regressão real é o CI do PR.

Achados obrigatórios: nenhum. Sugestões: 0.

Resultado: NAO_CONFERIDO — só falta CA4, que só pode ser conferido com o PR de prova (fase 5); refazer a rodada com `gh pr checks` depois de abrir o PR.
