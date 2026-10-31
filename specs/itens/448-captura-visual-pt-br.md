# 448 — Técnica · Captura do teste visual com o navegador em pt-BR (antes da Fase 2)

Origem: issue #448 (Sprint 10.5 · Identidade visual), plano F1-12, `docs/auditoria/decisoes-canvas.md` itens 17 e 18. Branch: `item/448-captura-visual-pt-br`.

## 1. Objetivo

Fazer a captura do teste visual e a captura usada para comparar com o canvas mostrarem os textos nativos em português ("dd/mm/aaaa"; "Escolher arquivo / Nenhum arquivo escolhido"), em vez de "dd/mm/yyyy" e "Choose File / No file chosen". Hoje o Chromium "headless shell" do Playwright ignora `locale` para esses textos. Item técnico, sem mudança de produto; destrava a #435 e as issues de componente da Fase 2.

Fora do escopo: mudar qualquer tela, regerar referências de casos sem campo nativo, o comparativo automático (#435).

## 2. Requisitos

- R1: `frontend/playwright.visual.config.ts` usa o Chromium completo (`channel: "chromium"`, novo headless) e mantém `locale: "pt-BR"` (D1).
- R2: no container do CI (imagem `mcr.microsoft.com/playwright:v<versão>-noble`), o canal `chromium` existe e os textos nativos saem em pt-BR; se não saírem, o idioma é forçado por `--lang=pt-BR` e `LANG`/`LANGUAGE` (D2).
- R3: `frontend/visual/idioma.visual.ts` renderiza com `page.setContent` um `<input type="date">` e um `<input type="file">` e compara com a referência `idioma-pt-br.png`; reprova se o navegador voltar ao inglês.
- R4: `frontend/scripts/canvas-shot.mjs` abre o navegador com a mesma configuração de idioma (D3), e o `frontend/README.md` (seção "Teste visual") diz "capture sempre em pt-BR".
- R5 (ajustado, D5): todas as referências de `frontend/visual/referencias` são regeradas com o Chromium completo; o diff das 9 com campo nativo (`cobrancas`, `cobrancas-recebimentos`, `comunicados`, `comunicados-novo`, `agenda-aula-avulsa`, `cobranca-avulsa`, `dia-sem-aula-novo`, `beneficio-conceder`, `professor-novo`) é o texto nativo mais o antialiasing, e o das demais é só antialiasing do texto (mesmo layout, mesmas cores).
- R6: nenhum dado real no repositório.

## 3. Critérios de aceite

- CA1 (R1, R3): dado o container do CI, quando roda `npm run test:visual:docker`, então `idioma.visual.ts` passa e `idioma-pt-br.png` mostra "dd/mm/aaaa" e "Escolher arquivo / Nenhum arquivo escolhido".
- CA2 (R3): dado um navegador em inglês (ex.: sem `channel: "chromium"`), quando o teste de idioma roda contra a referência, então ele falha.
- CA3 (R4): dado `node scripts/canvas-shot.mjs <Tela>` de uma tela do canvas com data ou arquivo, quando gera o print, então os textos nativos aparecem em português.
- CA4 (R5, ajustado, D5): dada a suíte rodada contra as referências antigas antes de regerar, quando medida, então nenhum caso difere em mais de 0,10 dos pixels e nenhum difere em posição, cor ou tamanho de elemento (só borda de letra); e, dadas as referências regeradas, quando `npm run test:visual:docker` roda sem atualizar, então toda a suíte passa e as imagens dos 9 casos com campo nativo mostram os textos em pt-BR.
- CA5 (R2): dado o job `visual` do CI e o `visual-docker.sh`, quando rodam, então usam o mesmo canal e idioma e o `ci-gate` fica verde.
- CA6 (R4, R6): dado o README, quando lido, então traz a orientação de capturar em pt-BR; `bash infra/scripts/gitleaks-rules.test.sh` segue passando.

## 4. Plano técnico

- Dados / API / Segurança: não mudam.
- Interface: nenhuma tela muda; só a captura.
- Arquivos:
  - `frontend/playwright.visual.config.ts`: em `use`, trocar `...devices["Desktop Chrome"]` mantendo-o e acrescentar `channel: "chromium"` (e `launchOptions.args: ["--lang=pt-BR"]` se o container exigir, D2).
  - `frontend/visual/idioma.visual.ts` (a criar): um teste, `setContent` com os dois inputs, `toHaveScreenshot("idioma-pt-br.png")`; `testMatch` já pega `*.visual.ts`.
  - `frontend/visual/referencias/idioma-pt-br.png` (a criar, gerada no container) e todas as referências da pasta, regeradas (R5, D5; PNGs, não contam como arquivos de código).
  - `frontend/scripts/visual-docker.sh`: acrescentar `-e LANG=pt_BR.UTF-8 -e LANGUAGE=pt_BR` ao `docker run` só se D2 pedir.
  - `.github/workflows/ci.yml`, job `visual`: `env` com `LANG`/`LANGUAGE` só se D2 pedir (a imagem `v1.64.1-noble` já é a do job e2e; nada mais muda).
  - `frontend/scripts/canvas-shot.mjs`: `chromium.launch({ channel: "chromium", args: ["--lang=pt-BR"] })` e `newPage({ locale: "pt-BR", ... })`.
  - `frontend/scripts/visual-canvas.mjs`: conferir se abre navegador; se abrir, mesma configuração.
  - `frontend/README.md`, seção "Teste visual" (linhas ~81-88): "capture sempre em pt-BR" e menção ao `idioma.visual.ts`.
- Reuso: `playwright.visual.config.ts`, `visual-docker.sh` e a imagem do CI existentes.
- Riscos: o Chromium completo renderiza o texto com antialiasing diferente do headless shell e muda as 324 referências (medido na construção; ver D4 e D5). O risco passa a ser revisar um diff grande: mitigado por medir o diff antes de regerar (T2) e abrir os 9 casos com campo nativo um a um (T3). O PR fica grande em binários, mas sem código a mais.

## 5. Tarefas

- [x] T1: Medir no container: aplicar `channel: "chromium"` no config, criar `idioma.visual.ts`, rodar `npm run test:visual:docker -- idioma` e conferir os textos; se saírem em inglês, aplicar D2 (`--lang`, `LANG`, `LANGUAGE` no `visual-docker.sh` e no job `visual`) — cobre CA1, CA2, CA5 — prova: `cd frontend && npm run test:visual:docker -- --update-snapshots idioma` e abrir `idioma-pt-br.png`.
- [x] T2 (refeita, D5): Medir antes de regerar: rodar a suíte inteira contra as referências antigas e guardar o relatório (razão de pixels por caso, em `test-results/`); conferir que o maior diff é no máximo 0,10 e abrir as imagens de diff de uma amostra (um caso por tela de tamanho diferente, claro e escuro, 1440 e 390) para ver que só as bordas das letras mudam — cobre CA4 — prova: `cd frontend && npm run test:visual:docker` (esperado: reprova; o que importa é a medida).
- [x] T3 (refeita, D5): Regerar todas as referências com o Chromium completo, abrir uma a uma as dos 9 casos com campo nativo (comparando com a anterior) e rodar a suíte de novo sem atualizar — cobre CA4 — prova: `cd frontend && npm run test:visual:docker -- --update-snapshots=all`, depois `npm run test:visual:docker` (tudo verde) e `git status --short frontend/visual/referencias`.
- [x] T4: Ajustar `canvas-shot.mjs` (e `visual-canvas.mjs`, se abrir navegador) para pt-BR — cobre CA3 — prova: `cd frontend && node scripts/canvas-shot.mjs ComunicadoNovo` e abrir o print em `docs/design/prints/`.
- [x] T5: Atualizar a seção "Teste visual" do `frontend/README.md` — cobre CA6 — prova: leitura do trecho e `bash infra/scripts/gitleaks-rules.test.sh`.

## 6. Decisões

- D1: `channel: "chromium"` com `locale: "pt-BR"`, como a issue testou; alternativa descartada: Edge (não existe na imagem do CI).
- D2: forçar `--lang`/`LANG`/`LANGUAGE` só se T1 mostrar inglês no container; evita configuração sem necessidade. Alternativa: instalar o pacote de idioma na imagem (mais pesado, só se o resto falhar).
- D3: `canvas-shot.mjs` roda no host do dono (Windows), onde o teste da issue mostrou pt-BR com o canal `chromium`; usa o mesmo canal e `--lang=pt-BR` para não depender do idioma do sistema.
- D4: se o Chromium completo mudar renderização de casos sem campo nativo, não regerar tudo em silêncio: tentar neutralizar (ex.: mesmas flags de fonte/antialias) e, se não der, registrar na construção e devolver ao `maestro`. Resultado: a suposição estava errada (324 de 329 mudaram; flags de fonte sem efeito); resolvido pelo ajuste D5.
- D5 (ajuste, pedido da construção em "Pedido de ajuste"): escolhida a opção (a), regerar todas as referências neste PR. Motivo: a issue quer que a captura do teste visual seja a mesma que se compara com o canvas, em pt-BR; as opções (b) e (c) deixariam as telas em inglês na referência (b) ou dependem de um mecanismo que não existe para o headless shell (c), e manteriam dois navegadores com aparências diferentes. O custo é um diff grande de PNGs, controlado por T2 (medida antes) e T3 (revisão dos 9 casos). Fica no item porque o `channel: "chromium"` é o que a issue pede e as referências precisam acompanhá-lo; não é mudança de produto. Se a medida de T2 mostrar diferença de layout, cor ou posição (não só borda de letra), parar e devolver ao `maestro`. A descrição do PR diz que todas as referências foram regeradas e por quê. Não muda o comportamento do produto.

## Construção

- T1 — arquivos: `frontend/playwright.visual.config.ts` (`channel: "chromium"`, `launchOptions.args: ["--lang=pt-BR"]`), `frontend/visual/idioma.visual.ts`, `frontend/visual/referencias/idioma-pt-br.png`, `frontend/scripts/visual-docker.sh` (`-e LANG=pt_BR.UTF-8 -e LANGUAGE=pt_BR`), `.github/workflows/ci.yml` (job `visual`: `env` LANG/LANGUAGE) — prova: `npm run test:visual:docker -- --update-snapshots=all idioma` (a forma sem `=all` do plano não funciona: o Playwright 1.64 lê `idioma` como o modo) → só `channel` deu inglês (D2 aplicado); com `--lang` + LANG/LANGUAGE a imagem mostra "dd/mm/aaaa" e "Escolher arquivo / Nenhum arquivo escolhido"; `--lang` sozinho (sem LANG) reprova contra a referência (CA2 observado); com os dois, passa.
- T4 — arquivos: `frontend/scripts/canvas-shot.mjs` (`channel: "chromium"`, `--lang=pt-BR`, `locale: "pt-BR"`); `visual-canvas.mjs` não abre navegador, nada a mudar — prova: `node scripts/canvas-shot.mjs ComunicadoNovo` → print com "dd/mm/aaaa".
- T5 (não marcada) — `frontend/README.md`, seção "Teste visual", item "Capture sempre em pt-BR" acrescentado; `bash infra/scripts/gitleaks-rules.test.sh` não rodou: "gitleaks não encontrado" nesta máquina.
- T2 — `npm run test:visual:docker` (suíte inteira, sem atualizar): 324 reprovados e 5 aprovados de 329. Diff mostra todo o texto da página com antialiasing diferente (0,01 a 0,05 dos pixels, acima da tolerância de 0,001). A causa é o `channel: "chromium"` em si: sem `--lang` e sem LANG o resultado é igual (alunos 390 claro, 6322 px). Tentei neutralizar (D4): `--font-render-hinting=none --disable-lcd-text` (5826 px) e `--font-render-hinting=full --disable-lcd-text` (5826 px), sem efeito. T2 e T3 não marcadas.
Regressão: não rodada além do visual (parada por D4).
Desvios: comando da prova com `--update-snapshots=all`.
Não é do item: nada.

Pedido de ajuste: R5 e CA4 — o Chromium completo muda a renderização de texto de todas as 324 referências (não só dos 9 casos com campo nativo), contrariando a suposição de D4. Opções para o dono/fase 2: (a) regerar todas as referências neste PR, revisando que o diff é só antialiasing; (b) manter o headless shell nas telas e usar o Chromium completo só em `idioma.visual.ts` e na captura do canvas (projeto separado no config), regerando só os 9 casos com um mecanismo que os mostre em pt-BR; (c) outro caminho para o idioma nativo no headless shell.

### Construção após o ajuste D5

- T2 (refeita) — `npm run test:visual:docker` contra as referências antigas: 324 reprovados e 5 aprovados de 329; maior diff 0,05 dos pixels (limite do item: 0,10). Abertas as imagens de diff de `alunos` 1440 claro, `alunos` 390 escuro, `comunicados-novo` 1440 escuro e `moldura-professor` 390 escuro: só bordas de letra e o texto nativo mudam; layout, cores e tamanhos iguais. Relatório em `frontend/test-results/` (fora do Git).
- T3 (refeita) — `npm run test:visual:docker -- --update-snapshots=all` → 329 regeradas e passando; depois `npm run test:visual:docker` sem atualizar → 329 passed (13,2 min). `git status --short frontend/visual/referencias`: 328 alteradas (M) e 1 nova (`idioma-pt-br.png`). Abertas as 9 do campo nativo (1440 claro): `cobrancas`, `cobrancas-recebimentos`, `comunicados`, `comunicados-novo`, `agenda-aula-avulsa`, `cobranca-avulsa`, `dia-sem-aula-novo`, `beneficio-conceder`, `professor-novo`: data em "dd/mm/aaaa" e arquivo em "Escolher arquivo / Nenhum arquivo escolhido" (`comunicados-novo`); `professor-novo` usa botão próprio "Escolher foto" (sem texto nativo visível).
- T5 continua não marcada: `bash infra/scripts/gitleaks-rules.test.sh` → "gitleaks não encontrado" nesta máquina (não é limitação aceita do AGENTS.md); o CI roda o gitleaks no PR. O texto do README está feito.
Regressão: só o teste visual (329 verdes); back-end e demais suítes do front não rodam, pois só config, script, README e PNGs mudaram. `npm run lint/typecheck/test:cov/e2e` não rodados.
Desvios: nenhum novo.
Não é do item: nada.

### Fechamento da construção

- T5 — `frontend/README.md` (seção "Teste visual": "capture sempre em pt-BR" e menção ao `idioma.visual.ts`) — prova: leitura do trecho; `bash infra/scripts/gitleaks-rules.test.sh` não rodou nesta máquina (gitleaks ausente); o dono respondeu "sim" para seguir sem a prova local, que fica para o gitleaks do CI do PR.
Regressão: visual 329 verdes (ver acima); demais suítes não rodadas (só config, script, README e PNGs mudaram).
Desvios: prova local do gitleaks fica para o CI, autorizado pelo dono.
Não é do item: nada.

## Verificação

### Rodada 1 — NAO_CONFERIDO — 2026-10-30, estado fa974eea8215bef7474c8c5780e4e53df876a933

| Critério | Status | Evidência |
|---|---|---|
| CA1 | PASSOU | `npm run test:visual:docker` no container do CI: 329 passed, incluindo `idioma.visual.ts`; `idioma-pt-br.png` aberta: "dd/mm/aaaa" e "Escolher arquivo / Nenhum arquivo escolhido" |
| CA2 | NÃO CONFERIDO | não reexecutado (só o relato da construção: `--lang` sem LANG reprova contra a referência); falta de evidência própria |
| CA3 | PASSOU | `node scripts/canvas-shot.mjs ComunicadoNovo`; print aberto mostra "dd/mm/aaaa" |
| CA4 | PASSOU (parte final) | suíte sem atualizar: 329 passed (12,8 min). A medida antes de regerar (T2) vem só do relato da construção, não reexecutável (referências já regeradas) |
| CA5 | PASSOU | `visual-docker.sh` e job `visual` do `ci.yml` usam o mesmo canal (config), `LANG=pt_BR.UTF-8` e `LANGUAGE=pt_BR`; o `ci-gate` só se vê no PR |
| CA6 | NÃO CONFERIDO | README traz "Capture sempre em pt-BR" (lido no diff); `bash infra/scripts/gitleaks-rules.test.sh` não rodou: gitleaks ausente na máquina (não é limitação aceita do AGENTS.md; o "sim" do dono citado na construção não foi visto por esta verificação) |

Comandos: `npm run test:visual:docker` (329 passed); `node scripts/canvas-shot.mjs ComunicadoNovo` (ok); `which gitleaks` (ausente). Lint, typecheck, test:cov e e2e não rodados: o diff só toca config do Playwright visual, script, workflow, README e PNGs. `visual-canvas.mjs` não abre navegador, nada a mudar.
Achados:
- sugestão, código, `frontend/README.md` — sem outros achados; diff lido sem problemas (config, script, CI e teste coerentes com o plano, nenhum dado real).
Instáveis: nenhum.

### Rodada 2 — NAO_CONFERIDO — 2026-10-30, estado fba30d3764e7970dd7a97e057953204d12287039

Mudança desde a rodada 1 (`git diff fa974ee fba30d3`): só a seção "Verificação" deste arquivo; config, scripts, workflow, README, teste e PNGs idênticos. Os critérios mantêm o status da rodada 1, sem reexecutar a suíte visual (13 min) por não haver o que mudou.

| Critério | Status | Evidência |
|---|---|---|
| CA1 | PASSOU (mantido da rodada 1) | 329 passed no container do CI; `idioma-pt-br.png` com os textos em pt-BR |
| CA2 | NÃO CONFERIDO | sem evidência própria de que o teste reprova em navegador em inglês; só o relato da construção |
| CA3 | PASSOU (mantido da rodada 1) | print de `ComunicadoNovo` com "dd/mm/aaaa" |
| CA4 | PASSOU (parte final, mantido) | suíte sem atualizar: 329 passed; medida anterior (T2) só pelo relato da construção |
| CA5 | PASSOU (mantido) | mesmo canal, `LANG` e `LANGUAGE` em `visual-docker.sh` e no job `visual`; `ci-gate` só no PR |
| CA6 | NÃO CONFERIDO | README traz "Capture sempre em pt-BR"; `which gitleaks` segue ausente, então `bash infra/scripts/gitleaks-rules.test.sh` não rodou (não é limitação aceita do AGENTS.md; `.gitleaks.toml` não foi tocado pelo item) |

Comandos: `which gitleaks` (ausente); `git diff fa974ee fba30d3 --stat` (só o arquivo do item).
Achados: nenhum obrigatório.
Para fechar: instalar o gitleaks e rodar `bash infra/scripts/gitleaks-rules.test.sh`; e rodar `npm run test:visual:docker -- idioma` com o `channel` ou o `--lang` removidos temporariamente para ver o teste reprovar (CA2). Ou o dono registrar que aceita o CI como prova.
Instáveis: nenhum.

### Rodada 3 — OK — 2026-10-30, estado 400c1c96c8e881c76c11addcaea7320914833982

Mudança desde a rodada 2 (`git diff fba30d3 400c1c9 --stat`): só a seção "Verificação" deste arquivo; config, scripts, workflow, README, teste e PNGs idênticos. Resposta do dono à parada, recebida pelo `maestro`: "sim" — o gitleaks do CI do PR cobre o CA6 e o CA2 fica com a evidência da construção (prova local dispensada).

| Critério | Status | Evidência |
|---|---|---|
| CA1 | PASSOU (mantido da rodada 1) | 329 passed no container do CI; `idioma-pt-br.png` com os textos em pt-BR |
| CA2 | PASSOU (evidência da construção, aceita pelo dono) | construção: `--lang` sem LANG reprova contra a referência; com os dois passa. Não reexecutado aqui |
| CA3 | PASSOU (mantido da rodada 1) | print de `ComunicadoNovo` com "dd/mm/aaaa" |
| CA4 | PASSOU (mantido) | suíte sem atualizar: 329 passed; medida T2 (maior diff 0,05 de 0,10) pelo relato da construção |
| CA5 | PASSOU (mantido) | mesmo canal, `LANG` e `LANGUAGE` em `visual-docker.sh` e no job `visual`; `ci-gate` só no PR |
| CA6 | PASSOU (README) / prova do gitleaks no CI do PR, aceito pelo dono | README traz "Capture sempre em pt-BR"; `gitleaks-rules.test.sh` não rodou local (gitleaks ausente), `.gitleaks.toml` não foi tocado pelo item. O gitleaks do CI deve estar verde antes do merge |

Comandos: `git diff fba30d3 400c1c9 --stat` (só o arquivo do item).
Achados: nenhum obrigatório.
Instáveis: nenhum.

## Entrega

Muda: o teste visual e a captura do canvas passam a usar o Chromium completo (`channel: "chromium"`) em pt-BR, com `--lang=pt-BR` e `LANG`/`LANGUAGE` no container e no job `visual` (D2 aplicada). Entram o teste `frontend/visual/idioma.visual.ts` (campos nativos de data e arquivo), a regeneração de todas as referências de `frontend/visual/referencias` (D5) e a seção "Teste visual" do `frontend/README.md`. Sem tela alterada e sem mudança de produto.

Verificação: rodada 3, OK (estado 400c1c9). Suíte visual no container do CI: 329 passed; `idioma-pt-br.png` com "dd/mm/aaaa" e "Escolher arquivo / Nenhum arquivo escolhido"; `canvas-shot.mjs ComunicadoNovo` com o texto nativo em pt-BR.

Decisões: regerar todas as referências (324 mudaram por antialiasing do texto), em vez de só as 9 com campo nativo (D5). A medida de T2 (maior diferença 0,05, limite 0,10) e a revisão dos 9 casos vieram da construção, não foram reexecutadas aqui.

Limitações: a prova local do `gitleaks-rules.test.sh` não rodou (gitleaks ausente na máquina); a cobertura fica com o gitleaks do CI do PR, aceita pelo dono. A prova de que o teste reprova em inglês (CA2) é a da construção. Item técnico sem tela no canvas: não há comparação de tela na descrição do PR.
