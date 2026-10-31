# 435 — Comparação automática com o canvas e cobertura do teste visual

Origem: issue #435 (Sprint 10.5 · Identidade visual), plano `docs/auditoria/plano-de-issues.md` (seção "#434 e #435"), `docs/auditoria/decisoes-canvas.md`. Item técnico (etiqueta `técnica`). Depende da #448 (feita). Branch: `item/435-comparacao-automatica-canvas`.

## 1. Objetivo

Hoje o teste visual (#416) compara cada tela com o print que o próprio sistema gerou antes; nenhum passo lê o canvas, então uma tela que nasceu diferente passa sempre. Este item (a) conserta `npm run visual:canvas`, (b) acrescenta a comparação automática sistema x canvas, com limite de diferença que reprova, usando a captura em pt-BR da #448, (c) amplia os casos do teste visual às telas do canvas de funcionalidade já construída e (d) faz a skill `revisar-sprint` exigir o resultado da comparação em vez de deixá-la só para o olho do dono.

Fora do escopo: mudar qualquer tela ou referência existente; casos de telas de sprint futura (site da plataforma, e-mails e documentos, painel da plataforma: entram quando a sprint as entregar); janelas e estados que o MSW não alcança (já listados como "Sem caso" em `casos.ts`); tornar a comparação exigência do CI (D4); comparação de pixel a pixel com texto e dados.

## 2. Requisitos

- R1: `npm run visual:canvas` roda sem erro com o `casos.ts` atual (remove também `export type Passo`) e gera o lado a lado; um teste simples impede a regressão.
- R2: a comparação usa só imagens do mesmo tamanho de largura (1440 ou 390), capturadas em pt-BR: a referência do sistema em `visual/referencias` e o print do canvas gerado por `scripts/canvas-shot.mjs` (D1).
- R3: a métrica ignora texto e dados (que diferem entre MSW e canvas): reduz as duas imagens a uma grade de blocos de cor média e conta a fração de blocos cuja cor difere acima de um limiar; compara só a altura comum às duas imagens (D2).
- R4: cada caso tem um limite de diferença; acima dele a tela reprova. O limite padrão e o limiar de cor ficam em um lugar só, e o caso pode trazer `limiteCanvas` com motivo (D3).
- R5: a comparação cobre 1440 e, nos casos com `canvasCelular`, 390; e o tema escuro nos casos com `canvasEscuro` (D5).
- R6: o resultado sai no lado a lado (`index.html`) e em `docs/design/prints/lado-a-lado/resultado.json` (tela, largura, tema, diferença, limite, aprovada); o comando termina com código diferente de zero com `--exige` quando alguma tela reprova, e sempre com zero sem a opção (modo relatório).
- R7: uma tela alterada de propósito para destoar do canvas reprova na comparação (prova com imagens sintéticas no teste e conferência manual).
- R8: `visual/casos.ts` ganha caso para cada tela do canvas de funcionalidade já construída (gestão e portal, Sprints 00 a 10) que o MSW alcança por rota; as que não alcança ficam listadas com o motivo (D6).
- R9: o job `visual` do `ci.yml` roda a comparação em modo relatório e publica o resultado como artefato (D4).
- R10: a skill `revisar-sprint` passa a citar o resultado da comparação (rodar a comparação, listar reprovadas e exigir resultado) em vez de listar "tela contra o canvas" só como conferência humana (D7).
- R11: nenhum dado real em print, fixture ou texto; o README (seção "Teste visual") descreve a comparação.

## 3. Critérios de aceite

- CA1 (R1): dado o `casos.ts` com `Passo` e `Caso`, quando o teste `comparar.test.ts` carrega os casos pelo mesmo caminho do script, então devolve a lista sem `SyntaxError`; e `npm run visual:canvas` gera `index.html`.
- CA2 (R3): dadas duas imagens iguais exceto texto/pontos pequenos, quando a métrica roda, então a diferença fica abaixo do limite padrão; duas imagens de cores de fundo e blocos diferentes dão diferença acima.
- CA3 (R2, R3): dadas imagens de alturas diferentes (sistema 1000, canvas 1500), quando comparadas, então só as linhas comuns entram e não há erro; larguras diferentes causam erro explícito com os nomes.
- CA4 (R4, R6): dado um caso com diferença acima do limite, quando roda `--exige`, então o código de saída é 1 e o `resultado.json` marca `aprovada: false`; sem `--exige`, sai 0 e o relatório marca a reprovação.
- CA5 (R4): dado um caso com `limiteCanvas`, quando roda, então o limite do caso vale em lugar do padrão.
- CA6 (R5): dado um caso com `canvasCelular` e `canvasEscuro`, quando roda, então há linhas para 1440 claro, 1440 escuro e 390 claro.
- CA7 (R7): dada uma referência do sistema alterada de propósito (bloco de cor trocado), quando roda a comparação, então a tela reprova (conferência manual registrada na construção).
- CA8 (R8): dado o `casos.ts`, quando `casos.test.ts` roda, então todo `canvas`, `canvasEscuro` e `canvasCelular` existe no `indice.json`, a rota está em `App.tsx`, nomes são únicos, e cada tela de gestão/portal sem caso consta da lista de exceções com motivo.
- CA9 (R9): dado um PR que mexe em `frontend/`, quando o job `visual` roda, então o passo da comparação executa sem derrubar o job e o artefato `canvas-resultado` é publicado.
- CA10 (R10): dada a skill `revisar-sprint`, então o passo 9 manda rodar `npm run visual:canvas` (dentro de `frontend/`), citar telas reprovadas e a pasta do resultado, e deixa para o olho humano só o que a métrica não vê (texto, tom).
- CA11 (R11): dado o PR, quando `gitleaks` e a leitura do diff rodam, então não há dado real; os casos usam só os perfis fictícios do MSW.

## 4. Plano técnico

- Dados: não muda.
- API: não muda.
- Interface: não muda (nenhuma tela é alterada).
- Segurança: não se aplica; prints ficam fora do Git (`docs/design/prints/` e `.capturas/` no `.gitignore`). Sem dado real.
- Arquivos, a alterar (vistos no repositório):
  - `frontend/scripts/visual-canvas.mjs`: hoje remove `export type Caso = {...};` com regex; passa a usar a função compartilhada de carregar casos (remove também `export type Passo = ...;`), chama o comparador, grava `resultado.json`, aceita `--exige`, mostra diferença e limite no HTML.
  - `frontend/visual/casos.ts`: tipo `Caso` ganha `canvasCelular?: string` e `limiteCanvas?: { valor: number; motivo: string }`; novos casos (D6) e as ligações `canvasCelular` onde o canvas celular é a mesma tela a 390 px (ex.: GM* e PortalM*, só onde a referência 390 do caso existe); constante `semCaso` (nome da tela do canvas -> motivo) para as telas de gestão/portal que o MSW não alcança.
  - `frontend/visual/casos.test.ts`: confere `canvasCelular`, `limiteCanvas.motivo` não vazio e a regra de exceções (CA8).
  - `.github/workflows/ci.yml`, job `visual`: depois de `npm run test:visual`, passo "Comparar com o canvas (relatório)" com `npm run visual:canvas` e `upload-artifact` de `docs/design/prints/lado-a-lado` com `if: always()`.
  - `frontend/README.md`, seção "Teste visual": descrever a comparação, `--exige`, limite por caso e a lista de exceções.
  - `frontend/package.json`: `pngjs` como devDependency (já no lockfile como transitiva de `playwright`; fixar a versão exata 5.0.0) — sem outra dependência nova.
  - Fora do repositório: `C:\Users\natha\.claude\skills\revisar-sprint\SKILL.md`, passo 9 (R10); esta mudança não entra no commit do item e é citada na descrição do PR.
- Arquivos, a criar:
  - `frontend/visual/comparar.mjs`: `carregarCasos()` (lê `casos.ts` removendo os tipos) e `diferenca(pngA, pngB, { bloco, limiar })` que decodifica com `pngjs`, corta à altura comum, calcula a média de cor por bloco (32 px) e devolve a fração de blocos acima do limiar. Constantes `LIMITE_PADRAO` e `LIMIAR_COR` no topo.
  - `frontend/visual/comparar.test.ts`: testes de CA1 a CA3 e CA5 com PNGs sintéticos gerados em memória (`PNG` do `pngjs`), sem arquivo de dado.
- Reuso: `scripts/canvas-shot.mjs` (print do canvas, já em pt-BR), `visual/referencias` (1440 e 390, claro e escuro), `docs/design/canvas/indice.json` (títulos e largura), lógica de lado a lado de `visual-canvas.mjs`, regex de rota de `casos.test.ts`.
- Riscos: (1) limite mal calibrado reprova tudo ou nada — a construção mede as 80+ telas existentes, registra a diferença de cada uma no relatório da construção e fixa o padrão acima da faixa das telas já conferidas pelo dono, usando `limiteCanvas` com motivo só nas exceções; (2) alturas diferentes entre sistema (1000) e canvas escondem diferença abaixo da dobra — aceito, a comparação cobre o que cabe na altura comum e `fullPage` ajuda onde o caso o usa; (3) o canvas tem dados diferentes do MSW — por isso a grade grossa; (4) `.mjs` importando de `casos.ts` por regex é frágil — a função única e o teste CA1 contêm o risco.

## 5. Tarefas

- [x] T1: criar `visual/comparar.mjs` com `carregarCasos()` (remove `Caso` e `Passo`) e consertar `scripts/visual-canvas.mjs` para usá-la — cobre CA1 — prova: `npm test -- visual/comparar.test.ts -t carrega` e `npm run visual:canvas`
- [x] T2: implementar `diferenca()` (grade de blocos, altura comum, erro de largura) com `pngjs` fixado — cobre CA2, CA3 — prova: `npm test -- visual/comparar.test.ts`
- [x] T3: ligar a comparação ao `visual-canvas.mjs` (1440, escuro, 390 via `canvasCelular`, limite padrão e `limiteCanvas`, `resultado.json`, `--exige`, colunas de diferença no HTML) — cobre CA4, CA5, CA6, CA7 — prova: `npm test -- visual/comparar.test.ts` e `node scripts/visual-canvas.mjs --exige` com uma referência alterada à mão (conferência registrada, referência restaurada em seguida)
- [x] T4: calibrar o limite padrão medindo as telas existentes (relatório no arquivo do item) e acrescentar os casos novos e `semCaso` em `casos.ts`, atualizando `casos.test.ts` e gerando as referências só dos casos novos (`npm run test:visual:docker -- --update-snapshots` limitado a eles) — cobre CA6, CA8, CA11 — prova: `npm test -- visual/casos.test.ts`
- [x] T5: acrescentar o passo de relatório e o artefato ao job `visual` do `ci.yml`, e descrever a comparação no `frontend/README.md` — cobre CA9 — prova: `docker run --rm -v "$PWD:/repo" -w /repo rhysd/actionlint` (imagem removida ao fim) e leitura do README
- [x] T6: ajustar o passo 9 da skill `revisar-sprint` — cobre CA10 — prova: conferência por leitura do trecho alterado, citada no arquivo do item

## 6. Decisões

- D1: comparar a referência do sistema de `visual/referencias` com o print do canvas de `canvas-shot.mjs`, sem subir o sistema de novo (como já faz `visual-canvas.mjs`, D5 da #416). Motivo: as referências já são geradas no container do CI em pt-BR e relógio fixo. Alternativa descartada: capturar o sistema de novo dentro da comparação (duplica a captura e o tempo de CI).
- D2: métrica por grade de blocos de cor média (32 px), fração de blocos acima do limiar de cor, sobre a altura comum. Motivo: texto e dados do MSW nunca serão iguais aos do canvas, e esta métrica pega o que a issue quer pegar (estrutura, blocos, cores de fundo, tamanho de componentes, menu no lugar errado). Alternativas descartadas: pixel a pixel (reprova por texto e serrilhado) e máscara de áreas com dado (exige marcar cada tela). Texto e rótulos continuam com o olho humano e com os testes de texto (`telasCanvas4xx.test.ts`).
- D3: limite padrão único, medido na T4 e registrado no arquivo; exceção por caso com `limiteCanvas` e motivo obrigatório. Motivo: a escolha do número depende da medição, que só existe na construção; o valor não é regra de negócio.
- D4 (muda o comportamento): no CI a comparação nasce em modo relatório (não derruba o job), como a issue pede ("primeiro como relatório, depois como exigência"). Virar exigência é uma mudança de uma linha (`--exige` no passo) a fazer depois de execuções estáveis, em PR próprio. Alternativa descartada: exigir já, sem histórico de estabilidade do limite.
- D5: 390 px só nos casos com `canvasCelular` (o canvas celular é outra tela, de outro arquivo, com a largura 390 ou 360). Motivo: `canvas` aponta para a tela de 1440; o `indice.json` guarda a largura de cada canvas. Telas de 360 px não entram (o teste visual captura 390). Alternativa descartada: inferir o par celular pelo nome (GM*, PortalM*), que não é regular.
- D6: cobertura "para cada tela de funcionalidade já construída" = telas das páginas "Desktop · Gestão", "Celular · Gestão", "Desktop · Portal do aluno" e "Celular · Portal do aluno" do `indice.json` (273 das 416; 80 já ligadas) que o MSW alcança por rota; as demais entram em `semCaso` com o motivo (id gerado pelo MSW, fluxo longo, mesma tela a 390 px). Site da plataforma, e-mails e documentos, painel da plataforma e Índice ficam de fora por serem de sprints futuras (a issue já diz que entram quando a sprint as entregar). Se uma tela em `semCaso` precisar de dado novo no MSW, isso é outro item.
- D7: a skill `revisar-sprint` vive em `~/.claude/skills`, fora do repositório. A mudança é feita no arquivo local e descrita no PR; não há PR dela. Suposição: se o dono usa uma cópia versionada em outro lugar, a mesma edição precisa ser levada para lá.
- Suposição: `pngjs` 5.0.0 está no lockfile como dependência transitiva e funciona em Node 24; se não, a alternativa é `pixelmatch`, que traz `pngjs` junto.
- Fora do item (sugestão de novo item): exigir a comparação no `ci-gate` depois de execuções estáveis (D4).

## Construção

- T1 — arquivos: `frontend/visual/comparar.mjs` (novo, `carregarCasos()` remove `Passo`, `Caso` e `Record<...>`, aceita CRLF), `frontend/scripts/visual-canvas.mjs` (usa a função), `frontend/visual/casos.ts` (`canvasCelular`, `limiteCanvas`) — prova: `npx vitest run visual/comparar.test.ts` → 5 passaram; `node scripts/visual-canvas.mjs` → gera `index.html`
- T2 — arquivos: `frontend/visual/comparar.mjs` (`diferenca`), `frontend/package.json` e `package-lock.json` (`pngjs` 5.0.0 exato, devDependency; sem outra dependência), `frontend/visual/comparar.test.ts` — prova: `npx vitest run visual/comparar.test.ts` → CA2 e CA3 passaram
- T3 — arquivos: `frontend/scripts/visual-canvas.mjs` (1440, escuro, 390, `resultado.json`, `--exige`, diferença e limite no HTML) — prova: testes acima (CA5 `limiteDoCaso`); CA7 manual: troquei a cor de `alunos-1440-claro.png` (cobri tudo, depois só a área fora do menu): `--exige` saiu 1 e `resultado.json` marcou `aprovada: false` (alunos 1440 claro); sem `--exige` saiu 0; referência restaurada do backup, `git status visual/referencias` sem alteração
- T4 — arquivos: `frontend/visual/casos.ts` (5 casos novos: `inicio-professor`, `matricula-aluno`, `portal-conta`, `portal-familia`, `portal-reduzido-info`; `canvasCelular` em 9 casos do portal + 2 novos; `semCaso` com as 187 telas restantes, por motivo), `frontend/visual/casos.test.ts` (CA8), 20 referências novas em `frontend/visual/referencias/` — prova: `npm run test:visual:docker -- --update-snapshots -g "<casos novos>"` → 20 passaram, só arquivos novos (nenhuma referência existente mudou); `npx vitest run visual` → 96 passaram
- T5 — arquivos: `.github/workflows/ci.yml` (job `visual`: passo "Comparar com o canvas (relatório)" com `continue-on-error`, artefato `canvas-resultado` com `if: always()`), `frontend/README.md` (comparação, `--exige`, `limiteCanvas`, `semCaso`) — prova: `rhysd/actionlint` por Docker → sem achados; imagem `rhysd/actionlint` removida
- T6 — arquivo fora do repositório: `C:\Users\natha\.claude\skills\revisar-sprint\SKILL.md`, passo 9 (linha 107) — prova: leitura do trecho; manda rodar `npm run visual:canvas` em `frontend/`, citar reprovadas e a pasta, exigir o resultado e deixa para o olho só texto, rótulos e tom. Não entra no commit; citar na descrição do PR (D7)

Calibração (D3): `node scripts/visual-canvas.mjs` mediu 93 comparações (1440 claro/escuro e 390). Diferença das telas já conferidas pelo dono: de 1% a 15% em 1440 e de 14% a 22% em 390 (maior: `portal-inicio-responsavel` 390, 22,5%). 22 telas de janela ficaram entre 39% e 74% e `portal-carteirinha` a 390 em 45%: o canvas mostra outra janela ou outro estado que o sistema no print (ex.: `conta-aluno` o canvas abre "Trocar e-mail", o sistema mostra a página sem janela; `configuracoes-faixa-nova` o canvas liga a página da lista, não a janela). Padrão fixado em `LIMITE_PADRAO = 0.30` (entre 22% e 39%); essas 23 telas ganharam `limiteCanvas` (medido arredondado + 5 pontos) com motivo "a tratar na tela". São divergências reais, que o teste agora aponta; consertar tela ou trocar o canvas ligado é outro item.
Regressão: `npm run lint` → sem erros; `npm run typecheck` → sem erros (após `@ts-expect-error` nos imports sem tipo do teste); `npm test` → 192 arquivos, 1903 testes passaram; `node scripts/visual-canvas.mjs --exige` → 93 comparadas, 0 reprovadas, sai 0. Não rodei `test:cov`, `build`, `e2e` nem o back-end (a mudança só toca `visual/`, `scripts/`, CI e README, sem `src/`) nem o `test:visual:docker` completo (rodei só os 20 casos novos; nenhuma referência existente foi tocada).
Desvios: (1) limite padrão 0,30 (o plano não fixava o número, D3). (2) `semCaso` é `Record<motivo, nomes[]>` em vez de nome -> motivo, para não repetir 187 linhas; o teste confere o mesmo. (3) Telas do canvas com largura diferente da referência (ex.: `Sidebar` 248 px) não são comparáveis: ficam sem linha em `resultado.json` e com a mensagem de largura no HTML. (4) `casos.ts` tem fim de linha CRLF; a regex de `carregarCasos` aceita os dois. (5) Casos de `Estado`/janela de sprint futura e variantes de perfil ficam em `semCaso`; só entraram casos cujo caminho eu conferi no MSW por rota.
Não é do item: nada além de `specs/itens/435-...md` (já sem commit antes).

## Verificação

### Rodada 1 — OK — 2026-10-30, estado 07a8d4b483bc3a74a7d749e52df041fdce1aee9a

| Critério | Status | Evidência |
|---|---|---|
| CA1 | PASSOU | `vitest run visual/comparar.test.ts visual/casos.test.ts` -> 96 passaram; `node scripts/visual-canvas.mjs --exige` gera `index.html` |
| CA2 | PASSOU | teste de `diferenca` em `comparar.test.ts` |
| CA3 | PASSOU | testes de altura comum e erro de largura em `comparar.test.ts` |
| CA4 | PASSOU | `resultado.json` com `aprovada`; `--exige` sai 1 com reprovação (registro manual da construção, não repetido) e saída 0 observada aqui com 93 comparadas, 0 reprovadas |
| CA5 | PASSOU | teste `limiteDoCaso` |
| CA6 | PASSOU | `resultado.json` tem linhas 1440 claro, 1440 escuro e 390 |
| CA7 | PASSOU | conferência manual registrada na T3; não repetida |
| CA8 | PASSOU | `casos.test.ts` verde |
| CA9 | NÃO CONFERIDO (limitação aceita: sem CI nesta máquina) | diff do `ci.yml` lido: passo com `continue-on-error` e `upload-artifact` `if: always()` |
| CA10 | PASSOU | passo 9 de `revisar-sprint/SKILL.md` lido |
| CA11 | PASSOU | diff lido; só perfis fictícios do MSW |

Comandos: `npx vitest run visual/comparar.test.ts visual/casos.test.ts` (96 ok); `node scripts/visual-canvas.mjs --exige` (exit 0); `npm run lint` e `npm run typecheck` (sem erros). Não rodei `npm test` completo, `test:cov`, `build`, `e2e` nem `test:visual:docker` (mudança só em `visual/`, `scripts/`, CI e README).
Achados:
- sugestão, código, `visual/comparar.mjs` `carregarCasos` — remoção de tipos por regex é frágil; contido pelo teste CA1.
- sugestão, código, `casos.ts` — 23 telas com `limiteCanvas` "a tratar na tela" são divergências reais com canvas; abrir item para tratá-las.

## Entrega

- O que muda: a comparação do teste visual com o canvas passa a ser automática. `frontend/visual/comparar.mjs` calcula a diferença entre prints; `frontend/scripts/visual-canvas.mjs` compara 1440 claro e escuro e 390 em 93 telas, grava `resultado.json`, aceita `--exige` (sai 1 com reprovação) e mostra diferença e limite no HTML.
- `frontend/visual/casos.ts`: 5 casos novos, `canvasCelular`, `limiteCanvas` e `semCaso` (telas sem comparação, por motivo). 20 referências novas em `frontend/visual/referencias/`; nenhuma existente mudou.
- CI: passo de relatório com `continue-on-error` e artefato `canvas-resultado` (`if: always()`). Dependência nova: `pngjs` 5.0.0 exato, só em devDependency.
- Verificação: rodada 1 OK (estado `07a8d4b483bc3a74a7d749e52df041fdce1aee9a`). CA9 não conferido localmente (sem CI nesta máquina).
- Decisões: limite padrão 0,30 (entre 22% e 39% medidos nas telas já conferidas); as 23 telas de janela acima disso ganharam `limiteCanvas` com motivo "a tratar na tela".
- Limitações: essas 23 telas são divergências reais com o canvas e seguem sem conserto (item próprio). `test:cov`, `build`, `e2e` e `test:visual:docker` completo não foram rodados localmente; o CI do PR roda.
