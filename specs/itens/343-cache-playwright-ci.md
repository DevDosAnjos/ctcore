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
- CA4 (R4, R5): dado 5 runs seguidos de PRs novos, então o tempo de iniciar o container (pull da imagem) mais a conferência fica abaixo de 60 s em todos, e o `e2e` fecha em menos de 7 min; os tempos de antes (22 s a 695 s) e de depois entram na Construção/Verificação.
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
- [ ] T3: abrir o PR, ler no log o tempo do passo de container e do `e2e`, e registrar tempos antes (da issue) e depois nos 5 primeiros PRs seguintes (este e os próximos 4, na verificação/entrega) — cobre CA4 — prova: `gh run view <id> --json jobs` com `startedAt/completedAt` por passo.

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

## Medição do CA4 (CI)

Limite: 5 PRs. Medido: 1. Faltam 4. CA4 segue NÃO CONFERIDO até fechar; a verificação não dá `OK` antes.

| PR | Run | Inicializar containers | Conferência de versão | `npm run e2e` | Job `e2e` inteiro | CI |
|---|---|---|---|---|---|---|
| #361 | 40087704834 | 33 s | 0 s | 42 s | 89 s | verde |

Critério por PR: container + conferência abaixo de 60 s (33 s: passou) e `e2e` abaixo de 7 min (89 s: passou). Antes (issue): 22 s a 695 s.
