# 416 — Teste visual: referências das telas e comparação lado a lado com o canvas

Origem: issue #416 (Sprint 10.5 · Identidade visual), `docs/design/README.md`. Branch: `item/416-teste-visual-referencias`. Depois da #415 (feita).

## 1. Objetivo

Ter um teste automático que abre as telas do sistema com os dados fictícios do MSW, tira o print em duas larguras e nos dois temas e reprova quando o visual muda sem ninguém ter pedido (uma cor, um tamanho). Ter também uma página que põe, para cada tela, o print do sistema ao lado do print da tela do canvas, para o dono revisar no fim da sprint.

Fora do escopo: comparar pixel a pixel com o canvas (dados e textos reais diferem); trocar telas para os componentes novos (issues de tela); regra, rota ou texto de negócio. Cada issue de tela da sprint (#417 a #423) acrescenta seus casos.

## 2. Requisitos

- R1: `npm run test:visual` roda o projeto `visual` do Playwright, separado do `e2e` (`npm run e2e` e `e2e:full` não o executam) (D1).
- R2: para cada caso, o teste abre a rota com os dados do MSW em 1440 × 1000 e 390 × 844, nos temas claro e escuro, e compara com a referência por `toHaveScreenshot`.
- R3: o print é estável: data e hora fixas, fuso da unidade, animações desligadas, cursor escondido, fontes do próprio app carregadas antes do print (D3).
- R4: as referências ficam no Git em `frontend/visual/referencias/`, sem sufixo de sistema, e são geradas e conferidas só no container `mcr.microsoft.com/playwright:v<versão>-noble` (a mesma do CI) (D2).
- R5: um caso por tela, em `frontend/visual/casos.ts`, ligado ao nome da tela do canvas quando existe (`Alunos` ↔ `/alunos`); a vitrine `/dev/vitrine` (#415) e as rotas atuais com tela no canvas entram (D4).
- R6: `npm run visual:canvas` gera `docs/design/prints/lado-a-lado/index.html` (pasta já ignorada pelo Git) com, para cada caso ligado ao canvas, o print do sistema (1440, claro; escuro quando o canvas tem a versão escura) ao lado do print do canvas (D5).
- R7: o `README` do front explica `test:visual`, `visual:canvas`, como atualizar as referências (`--update-snapshots`) e quando.
- R8: o CI roda `test:visual` num job próprio nos PRs que mexem em `frontend/`, na mesma imagem do `e2e`, e o job entra no `needs` do `ci-gate` (D6).
- R9: o teste reprova quando um componente muda de cor ou de tamanho (caso de prova registrado neste arquivo).
- R10: a regressão do front continua verde (`lint`, `typecheck`, `build`, `test:cov`, `e2e`).

## 3. Critérios de aceite

- CA1 (R1): dado `playwright.config.ts`, quando se roda `npm run e2e:full`, então nenhum arquivo de `frontend/visual/` executa; `npm run test:visual -- --list` lista só casos de `visual/`.
- CA2 (R2): dado cada caso de `casos.ts`, quando `test:visual` roda, então existe um teste por combinação largura × tema (4 por caso) com viewport 1440 × 1000 ou 390 × 844 e `colorScheme` claro ou escuro.
- CA3 (R3): dado o mesmo caso rodado duas vezes seguidas no container, então as duas passam sem diferença; o relógio da página está em 29/10/2026 12:00 em `America/Sao_Paulo` e `document.fonts.ready` resolvido antes do print.
- CA4 (R4): dado `npm run test:visual:docker -- --update-snapshots`, então as referências são gravadas em `frontend/visual/referencias/` com nomes `<caso>-<1440|390>-<claro|escuro>.png`; rodar `test:visual` fora do container não é o caminho documentado e o README avisa.
- CA5 (R5): dado `casos.ts`, então cada caso tem `nome`, `rota`, `perfil` (login do MSW), `canvas` (nome da tela ou ausente) e `fullPage`; um teste de unidade (Vitest) confere que todo `canvas` citado existe em `docs/design/canvas/indice.json` e que toda `rota` tem `<Route path>` correspondente em `App.tsx`.
- CA6 (R6): dado que as referências e os prints do canvas existem, quando se roda `npm run visual:canvas`, então `lado-a-lado/index.html` abre, com uma linha por caso ligado ao canvas, as duas imagens lado a lado e o título da tela; caso sem tela no canvas (vitrine) não entra; falta de uma imagem aparece como "sem print" e não quebra a página.
- CA7 (R7): dado o README do front, então tem os dois comandos na tabela de comandos e a seção "Teste visual" com o quando atualizar (mudança visual pedida pela issue; nunca para fazer o teste passar sem olhar a imagem).
- CA8 (R8): dado um PR que mexe só em `frontend/`, então o job `visual` roda e o `ci-gate` lista `visual` em `needs`; PR sem mudança em `frontend/` pula o job e o `ci-gate` continua verde (`skipped`).
- CA9 (R9): dado o caso de prova (troca temporária do token `--primary` e da altura do `Button` `md`), quando `test:visual` roda, então reprova nos casos afetados; revertida a troca, passa.
- CA10 (R10): dada a regressão completa do front, então passa.

## 4. Plano técnico

- Dados, API, segurança: não mudam. Interface: nenhuma tela nova. Infra: um job de CI.
- Arquivos (vistos no repositório): `frontend/playwright.config.ts` (testDir `./e2e`, fica como está); `frontend/package.json` (scripts); `frontend/README.md` (tabela de comandos, seção "Ponta a ponta"); `.github/workflows/ci.yml` (job `e2e` como molde, linhas 80 a 112; `ci-gate` na linha 162); `frontend/scripts/canvas-shot.mjs` (gera `docs/design/prints/<Tela>.png`, aceita nomes como argumentos); `frontend/e2e/support/msw.ts` e `e2e/portal-inicio.spec.ts` (login pela tela `/entrar` com `ana@exemplo.com`/`segredo123`, `termo@exemplo.com`; navegação por `history.pushState`, porque recarregar perde a sessão simulada); `frontend/src/App.tsx` (rotas e `/dev/vitrine`, só em DEV); `docs/design/canvas/indice.json`; `.gitignore` (já ignora `docs/design/prints/`).
- A criar:
  - `frontend/playwright.visual.config.ts`: projeto `visual`, `testDir ./visual`, `snapshotPathTemplate: "{snapshotDir}/{arg}{ext}"`, `snapshotDir ./visual/referencias`, `webServer` igual ao do e2e (porta 5174 para não colidir), `expect.toHaveScreenshot { animations: "disabled", caret: "hide", maxDiffPixelRatio: 0.001 }`, `locale pt-BR`, `timezoneId America/Sao_Paulo`, `retries 0`.
  - `frontend/visual/casos.ts`: lista tipada dos casos. Inicial (a construção confere cada rota abrindo-a e descarta a que exigir outro perfil ou dado que o MSW não tem, anotando em "Construção"): `vitrine` (`/dev/vitrine`, sem login, `fullPage`), `inicio` (`/`, canvas `Main`), `alunos` (`/alunos`, `Alunos`), `professores` (`Professores`), `agenda` (`Agenda`), `dias-sem-aula` (`DiasSemAula`), `turmas` (`Turmas`), `planos` (`/planos`, `Planos`), `beneficios` (`BeneficiosLista`), `cobrancas` (`/cobrancas`, `Mensalidades`), `cobrancas-atrasados` (`Atrasados`), `cobrancas-conferencia` (`EmConferencia`), `cobrancas-recebimentos` (`RecebimentosDia`), `cobrancas-previa` (`Fechamento`), `pedidos` (`PedidosPortal`), `comunicados` (`Comunicados`), `comunicados-novo` (`ComunicadoNovo`), `configuracoes-organizacao` (`ConfigAcademia`), `configuracoes-unidades` (`ConfigUnidades`), `configuracoes-unidade` (`ConfigUnidade`), `configuracoes-modalidades` (`ConfigModalidades`), `configuracoes-faixas-etarias` (`ConfigFaixas`), `configuracoes-usuarios` (`ConfigUsuarios`), `configuracoes-termos` (`ConfigTermos`), `configuracoes-textos` (`ConfigTextos`), `conta` (`MinhaConta`), `entrar` (`/entrar`, sem login, sem canvas). Caso com versão escura no canvas ganha `canvasEscuro` (`Main` → `MainEscuro`, `Mensalidades` → `CobrancasEscuro`).
  - `frontend/visual/telas.visual.ts`: para cada caso e combinação, fixa o relógio (`page.clock.setFixedTime`), emula o tema (`page.emulateMedia({ colorScheme })`), faz o login pela tela `/entrar` quando o caso tem `perfil`, navega por `pushState`, espera o `h1` ou o `data-vitrine`, `document.fonts.ready` e `toHaveScreenshot(nome)`.
  - `frontend/visual/casos.test.ts` (Vitest, CA5; confirmar que o `include` do Vitest alcança a pasta, senão ficar em `src/dev/`).
  - `frontend/scripts/visual-canvas.mjs`: lê `visual/casos.ts` (via `tsx`/import já usado em `scripts` ou um `casos.json` gerado; escolher o que não puxar dependência nova), chama `canvas-shot.mjs` com os nomes que faltam, copia as referências 1440 e os prints do canvas para `docs/design/prints/lado-a-lado/` e escreve `index.html` (HTML simples, sem dependência, `max-width` 100% nas imagens, rolagem horizontal contida).
  - `frontend/scripts/visual-docker.sh`: lê a versão de `@playwright/test` do lockfile, roda `docker run --rm` da imagem `mcr.microsoft.com/playwright:v<versão>-noble` com o repositório montado só leitura, copia `frontend/` (sem `node_modules`) para uma pasta de trabalho do container, `npm ci`, roda `playwright test -c playwright.visual.config.ts "$@"` e devolve `visual/referencias` (e o relatório em falha) para a pasta do host. Sem volume nomeado; no fim `docker rmi` da imagem se o script a baixou (AGENTS, "Docker local").
  - `frontend/visual/referencias/*.png`: geradas pelo script.
- Scripts em `package.json`: `test:visual` (`playwright test -c playwright.visual.config.ts`), `test:visual:docker` (`bash scripts/visual-docker.sh`), `visual:canvas` (`node scripts/visual-canvas.mjs`).
- CI: job `visual` copiando o `e2e` (imagem, `npm ci`, passo que confere a tag), `if: needs.changes.outputs.frontend == 'true'`, `npm run test:visual`, envia `frontend/visual/` de diferenças como artefato em falha; `ci-gate.needs` ganha `visual`. `infra/scripts/changed-areas.test.sh` não muda.
- Reuso: login e navegação de `e2e/portal-inicio.spec.ts`; `canvas-shot.mjs`; `indice.json`; a vitrine da #415; o passo de conferência da tag da imagem do job `e2e`.
- Riscos: (1) referências geradas fora do container não batem com o CI (CA4 e README); (2) PNGs aumentam o repositório (uns 27 casos × 4 ≈ 108 arquivos; manter 1440 × 1000 sem `fullPage` salvo na vitrine; olhar o tamanho total e, se passar de uns 15 MB, cortar casos); (3) dado do MSW com data relativa ao dia ou aleatório faz o print oscilar: o relógio fixo resolve o que vem de `Date`, o resto vira ajuste do caso (máscara `mask:` de um trecho, anotado); (4) `e2e:full` não pode pegar a pasta `visual/` (config separada resolve); (5) o eslint e o `tsc` precisam alcançar `visual/` e `scripts/` (conferir `eslint.config.js` e `tsconfig.node.json`); (6) o Docker no Windows pode ser lento no `npm ci` por rodar numa cópia.

## 5. Tarefas

- [x] T1: configuração e scripts: `playwright.visual.config.ts`, scripts do `package.json`, `visual/casos.ts` com a vitrine e a tela `/entrar`, `visual/telas.visual.ts` — cobre CA1, CA2, CA3 — prova: `npm run test:visual -- --list` (lista 4 testes por caso) e `npm run e2e:full -- --list` (não lista `visual/`)
- [x] T2: `scripts/visual-docker.sh` e geração das referências da vitrine e de `/entrar`; segunda rodada igual sem diferença — cobre CA3, CA4 — prova: `npm run test:visual:docker -- --update-snapshots` e depois `npm run test:visual:docker`
- [x] T3: os demais casos de `casos.ts` (rotas atuais com tela no canvas, login por perfil), abrindo cada rota para descartar a que não carrega; `casos.test.ts` — cobre CA2, CA5 — prova: `npx vitest run visual` e `npm run test:visual:docker -- --update-snapshots` seguido de `npm run test:visual:docker`
- [x] T4: `scripts/visual-canvas.mjs` e `.gitignore` conferido — cobre CA6 — prova: `npm run visual:canvas` e abrir `docs/design/prints/lado-a-lado/index.html` (dois temas onde o canvas tem; "sem print" quando falta)
- [x] T5: job `visual` no `ci.yml` e `ci-gate` — cobre CA8 — prova: `docker run --rm -v "$PWD":/repo -w /repo rhysd/actionlint:latest` no `ci.yml` (imagem removida depois) e `bash infra/scripts/changed-areas.test.sh`
- [x] T6: README do front (tabela de comandos e seção "Teste visual") — cobre CA7 — prova: conferência do texto contra os três scripts de `package.json`
- [x] T7: caso de prova (trocar `--primary` em `src/index.css` e a altura do `Button` `md`, ver o teste reprovar, reverter, ver passar) registrado em "Construção", e regressão do front — cobre CA9, CA10 — prova: `npm run test:visual:docker` (falha e depois passa) e `npm run lint && npm run typecheck && npm run build && npm run test:cov && npm run e2e`

## 6. Decisões

- D1: o projeto `visual` mora num arquivo de configuração próprio (`playwright.visual.config.ts`), não como segundo projeto do `playwright.config.ts`. Motivo: `e2e:full` roda `playwright test` com todos os projetos e passaria a executar o visual, que só vale no container. Alternativa descartada: projeto no mesmo arquivo com `--project` em todos os scripts.
- D2: referências no Git, sem sufixo de plataforma, geradas só no container do CI e atualizadas por `test:visual:docker -- --update-snapshots`. Motivo: o texto da issue manda a mesma imagem do CI para não variar entre máquinas. Alternativa descartada: guardar como artefato do CI (impede revisar a mudança no PR).
- D3: data fixa 29/10/2026 12:00 em `America/Sao_Paulo` (fuso da unidade, não o da JVM/UTC, conforme o AGENTS) por `page.clock.setFixedTime`, que não para os temporizadores, então o MSW e o React continuam funcionando. Tolerância `maxDiffPixelRatio` 0.001, para absorver serrilhado de fonte sem deixar passar mudança de cor ou tamanho de peça.
- D4: casos iniciais são só as rotas que já existem e têm tela no canvas, mais a vitrine e `/entrar`; as telas de portal (sem tela de 1440 no canvas, que é de gestão) e as janelas/painéis ficam para as issues de tela da sprint, que acrescentam seus casos. Rota que não abrir com os dados do MSW ou que precisar de ação (clicar para abrir janela) sai desta rodada e é citada em "Construção".
- D5 (muda o comportamento da revisão do dono): o lado a lado usa as referências do Git (1440, claro; escuro onde o canvas tem `MainEscuro`/`CobrancasEscuro`) e os prints do canvas gerados na hora; não sobe o sistema. Motivo: é o mesmo material que o teste compara, sem precisar de Docker na hora de revisar. Alternativa descartada: tirar prints novos do sistema a cada `visual:canvas` (varia entre máquinas). O print de 390 não entra: o canvas tem só telas de 1440 (e o menu/barra componentes).
- D6: job `visual` separado do `e2e`, na mesma imagem e com o mesmo passo de conferência da tag; roda também no PR para `main` (já roda tudo). Motivo: falha visual não deve esconder nem ser escondida pela falha funcional e o tempo de cada um fica separado. Alternativa descartada: rodar dentro do job `e2e`.
- D7: sem dependência nova: HTML do lado a lado escrito por template de texto no script. Alternativa descartada: biblioteca de relatório.
- Suposição: o `webServer` do visual usa `npm run dev` (MSW ligado), como o e2e, e `VITE_USE_MSW` não é definido. Se o container demorar para subir o Vite, aumenta-se o `timeout` do `webServer`; não muda o resto.

## Construção

- T1 — arquivos: `frontend/playwright.visual.config.ts`, `frontend/visual/casos.ts`, `frontend/visual/telas.visual.ts`, `frontend/package.json`, `frontend/tsconfig.node.json` — prova: `npm run test:visual -- --list` → 108 testes (27 casos × 4); `npm run e2e:full -- --list` → 0 linhas com `visual`
- T2 — arquivos: `frontend/scripts/visual-docker.sh` — prova: `npm run test:visual:docker -- --update-snapshots` e depois duas vezes `npm run test:visual:docker` → 8 passed (vitrine e entrar) na primeira prova; imagem baixada removida ao fim pelo script
- T3 — arquivos: `frontend/visual/casos.ts`, `frontend/visual/casos.test.ts`, `frontend/visual/referencias/*.png` (108, 5,3 MB) — prova: `npx vitest run visual` → 28 passed; `test:visual:docker -- --update-snapshots` e duas rodadas seguintes → 108 passed em cada, sem diferença
- T4 — arquivos: `frontend/scripts/visual-canvas.mjs` — prova: `npm run visual:canvas` → gera `docs/design/prints/lado-a-lado/index.html` com os pares claro e escuro (Main e Mensalidades); `.gitignore` já cobre `docs/design/prints/`
- T5 — arquivos: `.github/workflows/ci.yml` (job `visual`, `ci-gate.needs`) — prova: actionlint (`rhysd/actionlint:latest`, imagem removida depois) sem avisos; `bash infra/scripts/changed-areas.test.sh` → ok
- T6 — arquivos: `frontend/README.md` (3 linhas na tabela de comandos e seção "Teste visual") — prova: conferência do texto contra os scripts do `package.json`
- T7 — caso de prova: `--primary` de `#1B1B1F` para `#B91C1C` e `Button` `md` de `h-10` para `h-12` → `test:visual:docker -g "vitrine|alunos|entrar"`: 12 failed; revertido: 12 passed. Regressão: `npm run lint && npm run typecheck && npm run build && npm run test:cov && npm run e2e` → verde (176 arquivos, 1544 testes; e2e 81 passed)

Regressão: acima, sem piora em relação à linha de base.
Desvios: (1) o teste espera o login pelo título "Bem-vindo ao ctcore" e, depois do `pushState`, que ele suma; espera também os esqueletos "Carregando" sumirem (só nas telas com login) e uma pausa fixa de 300 ms (comentário `ponytail:` no código); sem isso, 27 casos oscilavam entre rodadas. (2) `casos.ts` ficou com 27 casos: todos os do plano abriram com o perfil `ana@exemplo.com` (Coordenador) e nenhum foi descartado. (3) `vite.config.ts` só ganhou um comentário no `exclude` do Vitest (`visual/casos.test.ts` roda no Vitest; `*.visual.ts` não casa). (4) `AGENTS.md`: comandos do teste visual acrescentados. (5) o CI do job `visual` só poderá ser provado no PR.
Não é do item: nada.

## Verificação

### Rodada 1 — OK — 2026-10-29, estado 0f54ff9aa1a73ba07a981c9fc458e8848dfcac16

| Critério | Status | Evidência |
|---|---|---|
| CA1 | PASSOU | `npm run test:visual -- --list` → 108 testes em 1 arquivo; `npm run e2e:full -- --list` → 87 testes, 0 linhas com `visual` |
| CA2 | PASSOU | 27 casos × 4 = 108; `telas.visual.ts` usa viewport 1440x1000/390x844 e `colorScheme` claro/escuro |
| CA3 | PASSOU | `test:visual:docker` contra as referências do Git: 108 passed, sem diferença; `page.clock.setFixedTime(2026-10-29T12:00-03:00)`, `timezoneId America/Sao_Paulo`, `document.fonts.ready` no código |
| CA4 | PASSOU | referências `<caso>-<largura>-<tema>.png` em `visual/referencias` (5,3 MB), sem sufixo de sistema; README avisa contra rodar fora do container; imagem Playwright não ficou na máquina |
| CA5 | PASSOU | `npx vitest run visual` → 28 passed (canvas em `indice.json`, rota em `App.tsx`) |
| CA6 | PASSOU | `npm run visual:canvas` → `index.html` com 27 seções (25 casos + 2 escuros), 0 "sem print"; vitrine e entrar fora; ausência de imagem tratada no código; pasta ignorada pelo Git |
| CA7 | PASSOU | README: 3 linhas na tabela e seção "Teste visual" com quando atualizar |
| CA8 | PASSOU (por leitura) | job `visual` com `if: frontend == 'true'`, na imagem do e2e com conferência de tag; `visual` em `ci-gate.needs`, e o gate aceita `skipped`. Execução real só no PR |
| CA9 | PASSOU (registro) | caso de prova registrado em "Construção" (12 failed, revertido 12 passed); não repetido |
| CA10 | PASSOU | lint, typecheck, build, `test:cov` (176 arquivos, 1544 testes) e `e2e` (81 passed) verdes |

Comandos: os acima, todos com sucesso.
Achados:
- sugestão, código, `frontend/visual/telas.visual.ts` — espera fixa de 300 ms (já marcada `ponytail:`); observar o job no CI quanto a oscilação.
- sugestão, código, `.github/workflows/ci.yml` job `visual` — roda em container como root com `npm ci`; se o CI mostrar falha de permissão ou de cache, ajustar.

## Entrega

- O que muda: teste visual de 27 casos (vitrine, entrar e telas autenticadas) em 1440x1000 e 390x844, claro e escuro, com 108 referências PNG em `frontend/visual/referencias`. Roda em container Playwright fixo (`npm run test:visual:docker`), com relógio fixo em 29/10/2026 12:00 no fuso de São Paulo. `npm run visual:canvas` gera o comparativo lado a lado em `docs/design/prints/lado-a-lado/` (pasta ignorada pelo Git). Job `visual` no CI, incluído no `ci-gate`.
- Verificação: `test:visual` lista 108 testes; `test:visual:docker` passa 108 em duas rodadas seguidas sem diferença; caso de prova (`--primary` e `Button` alterados) reprova 12 casos e volta a passar ao reverter. Lint, typecheck, build, `test:cov` (1544 testes) e `e2e` (81 passed) verdes. O `e2e` não inclui nenhum teste `visual`.
- Decisões: o teste espera o login sumir, os esqueletos "Carregando" sumirem e 300 ms fixos (marcado com `ponytail:`), senão 27 casos oscilavam. Todos os casos usam o perfil fictício `ana@exemplo.com` (Coordenador).
- Limitações: o job `visual` do CI só será provado no PR; o CI roda como root no container e pode precisar de ajuste de permissão ou cache. A espera fixa de 300 ms pode gerar oscilação.

### Correção da entrega
- Falha do PR #426 (jobs `e2e` e `visual`, passo "Conferir versão do Playwright"): o `sed` passou a achar duas linhas `image:` no `ci.yml` (e2e e visual) e a comparação virou "1.64.1" contra "1.64.1\n1.64.1". Corrigido com `| head -n1` nos dois passos — arquivos: `.github/workflows/ci.yml` — prova: o mesmo `sed` com `head -n1` → `1.64.1`, igual ao lockfile; o `ci-gate` caiu só por consequência desses dois jobs. Execução real só no PR.
Regressão: nenhum código do front mudou nesta correção; a regressão do front segue a registrada acima. Não rodei o CI localmente.

### Rodada 2 — OK — 2026-10-29, estado ad49a1efba45ccc4afb7244287fde8858c769c59

Rodada depois da "Correção da entrega" (falha do PR #426 no passo "Conferir versão do Playwright"). Desde a rodada 1 (estado 0f54ff9) o diff toca só `.github/workflows/ci.yml` (dois `| head -n1`), este arquivo e `specs/backlog.md`; nenhum código do front mudou.

| Critério | Status | Evidência |
|---|---|---|
| CA1 a CA7, CA9, CA10 | PASSOU (mantido da rodada 1) | nada do front ou dos scripts mudou |
| CA8 | PASSOU (por leitura) | `ci.yml` linhas 102 e 135: `sed ... \| head -n1` nos jobs `e2e` e `visual`; o `sed` local devolve `1.64.1`, igual a `@playwright/test` no `package-lock.json`; `visual` segue em `ci-gate.needs`; `bash infra/scripts/changed-areas.test.sh` ok. Execução real só no PR |

Comandos: `sed` do passo contra o `ci.yml` com `head -n1` → `1.64.1`; `changed-areas.test.sh` → ok; `git diff 0f54ff9 <estado>` conferido.
Achado obrigatório da rodada 1: nenhum existia. Achados:
- sugestão, código, `.github/workflows/ci.yml` passo "Conferir versão do Playwright" — as duas imagens do arquivo estão na mesma versão, então `head -n1` basta; se um dia divergirem, o passo só confere a do `e2e`.
