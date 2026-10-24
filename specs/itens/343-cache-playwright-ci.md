# 343 — Cache do Playwright que valha para PR novo e instalação do Chromium estável

Origem: issue #343 (Sprint 09), achado R08-8 da revisão da Sprint 08. Branch: `item/343-cache-playwright-ci`.

## 1. Objetivo

O passo de instalação do Chromium no job `e2e` do CI deixa de depender do espelho do apt e do cache por PR, que nunca é lido por PR novo, e fica abaixo de 60 s; a folga do `e2e` contra o timeout passa de 2 min para mais de 8 min. Fora do escopo: mudar testes e2e, outros jobs, o `aguardar-ci.sh` (#344).

## 2. Requisitos

- R1: o job `e2e` roda na imagem oficial do Playwright com a mesma versão de `@playwright/test`, sem `playwright install` e sem `--with-deps` (D1).
- R2: o CI reprova com mensagem clara se a versão de `@playwright/test` em `frontend/package-lock.json` diferir da versão da imagem (D2).
- R3: o `ci-gate` continua exigindo todos os jobs (sem mudança na lista `needs`).
- R4: o passo de preparo do Chromium de um PR novo leva menos de 60 s em 5 runs seguidos, e o `e2e` termina com folga maior que 8 min contra o `timeout-minutes` (D3).
- R5: o tempo do passo antes e depois fica registrado em pelo menos 5 PRs.
- R6: a mudança está descrita no `AGENTS.md` e no `README.md` (jobs e limites).

## 3. Critérios de aceite

- CA1 (R1): dado o PR do item, quando o `e2e` roda, então não há passo `playwright install` no workflow e o job passa.
- CA2 (R2): dado a versão da imagem diferente da do lockfile, quando o passo de conferência roda, então o job falha citando as duas versões; com versões iguais, passa.
- CA3 (R3): dado o workflow, então `ci-gate` lista `changes, backend, frontend, e2e, image-backend, image-frontend, security` e o gate fica verde no PR.
- CA4 (R4, R5): 1 de 5 aceito; o resto na #363. (Texto original: dado 5 runs seguidos de PRs novos, o tempo de iniciar o container mais a conferência fica abaixo de 60 s em todos, e o `e2e` fecha em menos de 7 min.)
- CA5 (R6): `AGENTS.md` e `README.md` não citam mais `playwright install`/cache como parte do CI e citam a imagem fixada.

## 4. Plano técnico

- Dados, API, interface, segurança de aplicação: não mudam.
- `.github/workflows/ci.yml`, job `e2e` (visto): acrescentar `container: image: mcr.microsoft.com/playwright:v<versão atual do lockfile, 1.64.1>-noble`; remover os passos `pw` (id), `actions/cache`, `playwright install --with-deps` e `install-deps`; manter `setup-node`, `npm ci`, execução e upload do relatório; incluir passo de conferência de versão (R2): lê `node -p "require('@playwright/test/package.json').version"` e compara com a versão extraída de `${{ job.container.image }}`, falhando com `::error::`. Manter `timeout-minutes: 15` (a folga vem do tempo menor, D3).
- `README.md` linhas ~63-72 (vistas): descrever que o e2e roda na imagem do Playwright e que, ao atualizar `@playwright/test`, a tag da imagem muda junto no `ci.yml`.
- `AGENTS.md` linha 80 (vista): uma frase com o mesmo.
- `frontend/README.md`: não muda (comando local `npx playwright install chromium` segue valendo).
- Riscos: o job em container roda `actions/checkout` e `setup-node` dentro dele (suportado); Dependabot/atualização de `@playwright/test` sem trocar a tag é pego pela conferência (R2); o pull da imagem (~1,5 GB) vem do MCR e conta no tempo, por isso CA4 mede o passo "Inicializar containers".

## 5. Tarefas

- [x] T1: editar o job `e2e` em `.github/workflows/ci.yml` (container, remoção dos passos de instalação e cache, conferência de versão) — cobre CA1, CA2, CA3 — prova: `docker run --rm -v "$PWD:/w" rhysd/actionlint:1.7.7 -color /w/.github/workflows/ci.yml` (imagem removida depois) e `bash infra/scripts/changed-areas.test.sh`.
- [x] T2: atualizar `README.md` e `AGENTS.md` — cobre CA5 — prova: `grep -n "playwright install" README.md AGENTS.md` sem referência ao CI.
- [x] T3: abrir o PR, ler no log o tempo do passo de container e do `e2e`, e registrar tempos antes (da issue) e depois nos 5 primeiros PRs seguintes (este e os próximos 4, na verificação/entrega) — cobre CA4 — prova: `gh run view <id> --json jobs` com `startedAt/completedAt` por passo.

## 6. Decisões

- D1 (muda o comportamento do CI): escolhida a imagem fixada `mcr.microsoft.com/playwright:vX-noble`. Motivo: elimina o apt e o cache por PR de uma vez, sem workflow extra em `push` para `dev` (que gastaria minutos e exigiria manter o cache aquecido). Alternativas: rodar o CI em `push` para `dev` só para gravar cache (segue dependendo do apt em cache frio e do espelho); `install chromium` sem `--with-deps` (as bibliotecas do sistema podem faltar).
- D2: a conferência de versão (R2) é um passo no próprio workflow, sem script novo. Motivo: a tag da imagem e o lockfile andam juntos e um desencontro daria erro obscuro do Playwright. Alternativa: derivar a tag do lockfile (impossível, `container:` não aceita saída de passo do mesmo job).
- D3: `timeout-minutes` do `e2e` fica em 15; a folga ">8 min" é consequência de o job fechar em menos de 7 min, medida em CA4. Se o job não fechar em menos de 7 min por outro motivo, registrar na Verificação em vez de baixar o limite.
- Suposição: o runner `ubuntu-latest` consegue baixar a imagem do MCR em menos de 60 s; se não, CA4 reprova e a alternativa é cache em `push` para `dev`.

## Construção

- T1 — arquivos: `.github/workflows/ci.yml` — prova: actionlint 1.7.7 → rc=0 (imagem removida); `bash infra/scripts/changed-areas.test.sh` → ok; passo de conferência rodado localmente: versões iguais passa, tag trocada para 1.60.0 falha com `::error::` citando as duas versões.
- T2 — arquivos: `README.md`, `AGENTS.md` — prova: `grep -n "playwright install" README.md AGENTS.md` → só o comando local (`npx playwright install chromium`, AGENTS.md linha 79), sem referência ao CI.
- T3 — não marcada: exige abrir o PR e medir o CI (fases 4/5).
Regressão: mudança só de CI/documentação, sem código de produto; `verify`, `test:cov` e `e2e` não rodados (o e2e na imagem só é provado no CI do PR).
Desvios: a conferência de versão (R2) não pode ler `${{ job.container.image }}` (actionlint: a propriedade não existe no contexto `job.container`); o passo extrai a tag com `sed` da linha `image:` do próprio `ci.yml` via `$GITHUB_WORKSPACE`.
Não é do item: nada.

### Correção necessária (ajuste A6 da revisão da Sprint 09, R09-6 e R09-7)
- R09-6 — arquivos: `README.md` (tabela do `frontend`: piso de 100% agora lista `src/features/{billing,charges,payments,subscription,deactivation}`, igual a `AGENTS.md` e `frontend/vite.config.ts`) — prova: `grep -n "billing,charges" README.md` → 1 linha.
- R09-7 — arquivos: este arquivo (texto do CA4 trocado para "1 de 5 aceito; o resto na #363", conforme "Decisão do dono"; registro de Construção/Correção acrescentado) — prova: leitura do arquivo. Verificação e Entrega ficam para as fases 4 e 5.
Regressão: só documentação, sem código; nada rodado.

## Verificação

### Rodada 1 — NAO_CONFERIDO — 2026-10-24, estado af91d39e20155aea003aec1f4cc0b211f25e3b0b

| Critério | Status | Evidência |
|---|---|---|
| CA1 | NÃO CONFERIDO | o ci.yml não tem `playwright install` nem cache (diff lido); "o job passa" só se prova no CI do PR, que ainda não existe |
| CA2 | PASSOU (parcial) | lockfile e imagem em 1.64.1; passo lê o lockfile e a tag do ci.yml ($GITHUB_WORKSPACE existe após o checkout) e falha com `::error::` citando as duas versões; execução do passo no CI não vista (construção diz tê-lo rodado local) |
| CA3 | PASSOU | `needs` do ci-gate não está no diff (inalterado); actionlint 1.7.7 rc=0 |
| CA4 | NÃO CONFERIDO | exige 5 PRs com tempos reais; T3 segue desmarcada; sem PR não há medida |
| CA5 | PASSOU | `grep "playwright install"`: só o comando local (AGENTS.md:79, frontend/README.md:16); AGENTS.md e README.md citam a imagem fixada |

Comandos: actionlint 1.7.7 via Docker → rc=0 (imagem removida); `bash infra/scripts/changed-areas.test.sh` → ok. Regressão (verify, test:cov, e2e) não rodada: mudança só de CI e documentação.
Achados:
- sugestão, código, ci.yml passo de conferência — o sed lê o próprio ci.yml; funciona, mas se alguém adicionar outro `image: mcr...playwright` o resultado vira multilinha e a comparação falha de forma obscura.
Pendência: CA1 e CA4 (e T3) só fecham com o PR aberto e os tempos medidos; não é limitação aceita no AGENTS.md. Repetir a verificação depois do PR.

### Rodada 2 — NAO_CONFERIDO — 2026-10-24, estado 308146cd60853125c6aa8fb00f8dcd10445ee555

Mudança desde a rodada 1: só o arquivo do item (a rodada 1 foi gravada nele); ci.yml, AGENTS.md e README.md iguais. Não há PR aberto (`gh pr list --head item/343-cache-playwright` vazio) nem commit.

| Critério | Status | Evidência |
|---|---|---|
| CA1 | NÃO CONFERIDO | sem `playwright install` nem cache no ci.yml; "o job passa" só no CI do PR, que não existe |
| CA2 | mantido da rodada 1 | PASSOU (parcial) |
| CA3 | mantido da rodada 1 | PASSOU |
| CA4 | NÃO CONFERIDO | exige tempos de 5 PRs; T3 desmarcada |
| CA5 | mantido da rodada 1 | PASSOU |

Comandos: `git write-tree` do estado atual e `gh pr list`; nada novo a rodar.
Achados: nenhum obrigatório novo; a sugestão da rodada 1 (sed lendo o próprio ci.yml) segue.
Pendência: CA1, CA4 e T3 só fecham com o PR aberto e medido. Não é limitação aceita no AGENTS.md; a entrega abre o PR e a verificação é repetida depois.

### Rodada 3 — OK — 2026-10-24, estado 7bd1b121f6a7313461c21e76c6b0ed9b166cefcd

Escopo: ajuste A6 (só documentação). Mudança desde a rodada 2: `README.md` (linha 68) e, neste arquivo, o texto do CA4 e o registro de Correção.

| Critério | Status | Evidência |
|---|---|---|
| README alinhado a AGENTS.md/vite.config.ts | PASSOU | `grep "billing,charges"`: README.md:68 e AGENTS.md:80 com a mesma lista; `frontend/vite.config.ts` linhas 26-30 têm billing, charges, payments, subscription, deactivation a 100% |
| CA4 reescrito conforme a decisão do dono | PASSOU | CA4 diz "1 de 5 aceito; o resto na #363", coerente com "Decisão do dono" e a tabela de medição (#361: 33 s, 89 s) |
| CA1, CA2, CA3, CA5 | mantido das rodadas 1 e 2 | ci.yml e AGENTS.md inalterados; CA1 coberto pela medição do CI do PR #361 (verde) |

Comandos: `git diff`, `grep`, leitura do `vite.config.ts`; regressão não rodada (só documentação, nenhum código tocado).
Achados: nenhum obrigatório; a sugestão da rodada 1 (sed no próprio ci.yml) segue.

## Medição do CA4 (CI)

Limite: 5 PRs. Medido: 1 (#361). Os demais ficam na #363.

| PR | Run | Inicializar containers | Conferência de versão | `npm run e2e` | Job `e2e` inteiro | CI |
|---|---|---|---|---|---|---|
| #361 | 40087704834 | 33 s | 0 s | 42 s | 89 s | verde |
| #364 | 40090311740 | sem medição (job e2e skipped, sem mudança no front-end) | — | — | — | verde |
| #365 | 40093207930 | sem medição (job e2e skipped, sem mudança no front-end) | — | — | — | verde |

Critério por PR: container + conferência abaixo de 60 s (33 s: passou) e `e2e` abaixo de 7 min (89 s: passou). Antes (issue): 22 s a 695 s.

## Decisão do dono (24/10/2026)

O dono aceitou fechar o #343 com a medição do #361. Os PRs #364 e #365 não rodaram o `e2e` e não contam. O CA4 continua na #363, que conta só PRs que rodem o `e2e`, até completar 5 (1 de 5 hoje). Se uma medição futura estourar o limite, a #363 vira prioridade, com a alternativa da D1 (cache em `push` para `dev`).

## Entrega

Ajuste A6 da revisão da Sprint 09 (R09-6 e R09-7), só documentação.

- O que muda: `README.md` (tabela do `frontend` lista `billing,charges,payments,subscription,deactivation` a 100%, igual a `AGENTS.md` e `frontend/vite.config.ts`). Neste arquivo, o CA4 virou "1 de 5 aceito; o resto na #363", conforme a decisão do dono, e foram registradas as rodadas 2 e 3.
- Verificação: rodada 3 OK (estado `7bd1b121`), com `grep` do README e do AGENTS e leitura do `vite.config.ts`. Regressão não rodada: nenhum código tocado e a base não avançou desde a verificação.
- Decisões: CA4 fechado com a medição do #361 (container e conferência 33 s; job `e2e` 89 s; CI verde), por decisão do dono em 24/10/2026. #364 e #365 não rodaram o `e2e` e não contam.
- Limitações: o CA4 segue como medição em aberto na #363, até 5 PRs com `e2e` medidos.
- A issue #343 já está fechada; este PR a referencia com `Refs #343`.
