# 441 — Revogar uso de imagem no portal sem confirmação

Origem: issue #441 (Sprint 10.5 · Identidade visual; auditoria M153 / F1-05). Branch: `item/441-revogar-imagem-confirmacao`.

## 1. Objetivo

No portal (Minhas informações, cartão Termos), "Revogar uso de imagem" passa a abrir uma janela de confirmação, como no canvas `PortalRevogarImagem`; a revogação só acontece ao confirmar. Para aluno e responsável (RF-POR-07, RN-LGP-01). Fora: "Conceder uso de imagem" (não muda); estilo final de janela e botão destrutivo (#460, #451); dado de uso de imagem concedido no MSW (#449).

## 2. Requisitos

- R1: clicar em "Revogar uso de imagem" abre a janela e não chama a API.
- R2: a janela tem título "Revogar uso de imagem", o texto do efeito, o bloco "Resumo" com 3 itens e os botões "Cancelar" e "Revogar", com os textos do canvas.
- R3: "Revogar" chama a API uma vez e atualiza o cartão; a janela fecha.
- R4: "Cancelar", Esc e fechar não chamam a API; o foco volta ao botão que abriu.
- R5: "Conceder uso de imagem" segue chamando direto, sem janela.
- R6: textos nos arquivos pt-BR; "Revogar" em estilo destrutivo (D2).
- R7: se a API falhar, a mensagem de erro do cartão aparece (comportamento atual) e a janela fecha (D3).

## 3. Critérios de aceite

- CA1 (R1, R4): com uso concedido, clicar em "Revogar uso de imagem" mostra o diálogo e nenhuma chamada `PUT .../image-use` ocorre; clicar em "Cancelar" fecha sem chamada.
- CA2 (R2): o diálogo mostra título, texto do efeito, "Resumo" com 3 itens (o primeiro com a data de hoje) e os dois botões.
- CA3 (R3): "Revogar" faz uma chamada com `{ granted: false }` e o cartão passa a "Não concedido".
- CA4 (R4): Esc fecha sem chamada e o foco volta a "Revogar uso de imagem".
- CA5 (R5): "Conceder uso de imagem" chama a API na hora, sem diálogo.
- CA6 (R7): API com erro: aparece "failed" do cartão.
- CA7: axe passa com a janela aberta, nos dois temas; conferência visual em 1440 e 390 px contra o canvas.

## 4. Plano técnico

- Dados, API, segurança, OpenAPI, MSW: não mudam (a rota `setPortalImageUse` já existe; permissão e auditoria já estão no servidor).
- Interface: `frontend/src/features/portal/PortalSettings.tsx`, `PortalTermsAccepted`: o botão, quando `imageUse.granted`, abre estado `confirming`; um `ConfirmDialog` (`components/ConfirmDialog.tsx`: Modal com resumo, "Cancelar", botão de confirmação; fecha só se `onConfirm` não lançar; foco volta ao gatilho pelo Modal/Radix) com `title`, `summary`, `summaryItems`, `confirmLabel` "Revogar" e `onConfirm` chamando `toggle`. `toggle` precisa relançar o erro para o diálogo ficar aberto? Não: D3 mantém a janela fechando, então `toggle` continua engolindo o erro e mostrando `failed`.
- i18n: `frontend/src/i18n/pt-BR.json`, `portalInfo.terms`: chaves `revokeTitle`, `revokeSummary` (texto do efeito), `revokeItems` ou 3 chaves (1º com `{{date}}` de hoje via `formatDate`), `revokeConfirm` "Revogar". Textos exatos do canvas: "A academia deixa de usar fotos e vídeos em que você aparece a partir de hoje. O que já foi publicado antes não é apagado automaticamente: se quiser, peça à unidade."; itens: `O uso de imagem fica "Revogado em {{date}}".`, "Nada mais muda na sua matrícula nem no portal.", "Você pode autorizar de novo quando quiser.".
- Testes: `frontend/src/features/portal/PortalInfoPage.test.tsx`, ajustar o teste de revogação (linha ~235-258, que hoje clica e espera a chamada) e acrescentar casos de CA1, CA3, CA4, CA5, CA6; axe com a janela aberta nos dois temas no mesmo arquivo, se houver helper (senão em `PortalInfoPage.test.tsx` como os testes de axe existentes).
- Visual: janela aberta não tem caso em `frontend/visual/casos.ts` e o MSW do aluno não tem uso concedido (#449): comparação com o canvas feita à mão com fixture temporária, sem mexer nos prints de referência (D4).
- Riscos: `ConfirmDialog` usa botão padrão, não destrutivo (D2); sem `confirmVariant`, o estilo final vem da #451.

## 5. Tarefas

- [x] T1: escrever os testes de CA1, CA3, CA4, CA5, CA6 em `PortalInfoPage.test.tsx` (falham antes da correção) — prova: `cd frontend && npx vitest run src/features/portal/PortalInfoPage.test.tsx`.
- [x] T2: chaves pt-BR e `ConfirmDialog` em `PortalSettings.tsx` — cobre CA1 a CA6 — prova: mesmo comando.
- [x] T3: axe com a janela aberta, nos dois temas — cobre CA7 — prova: mesmo comando.
- [x] T4: conferir em 1440 e 390 px, navegador pt-BR, contra `PortalRevogarImagem` (fixture temporária), e listar divergências para o PR — cobre CA7 — prova: capturas locais, `node scripts/canvas-shot.mjs PortalRevogarImagem` em `frontend/`.
- [x] T5: `npm run lint && npm run typecheck && npx vitest run src/features/portal` — prova: o comando.

## 6. Decisões

- D1 (muda o comportamento): revogar passa a exigir confirmação em janela; antes era direto. Origem: issue e canvas.
- D2: usar o `ConfirmDialog` existente, com botão de confirmação padrão; o estilo destrutivo vermelho do canvas vem da #451 (a issue manda usar o que existe). Alternativa: prop `destructive` no `ConfirmDialog` agora; invade o escopo da #451/#460.
- D3: erro da API não mantém a janela aberta; ela fecha e o cartão mostra a mensagem, como hoje (o `toggle` já trata o erro). Alternativa: relançar o erro e manter aberta; mais mudança sem pedido.
- D4 (suposição): não regerar prints de referência, pois nenhum caso visual mostra o estado "concedido" nem a janela (#449). Se a #449 já estiver feita ao construir, acrescentar o caso e regerar só ele.
- D5: "Revogado em {{date}}" usa a data de hoje no fuso da unidade pelo formatador existente (`formatDate`); se ele usar o fuso do navegador, anotar na construção.

## Construção

- T1 — arquivos: `frontend/src/features/portal/PortalInfoPage.test.tsx` (teste de revogação ajustado; casos de Cancelar/Esc com foco, Conceder direto, erro da API) — prova: `npx vitest run src/features/portal/PortalInfoPage.test.tsx` → falhou antes do código (diálogo ausente), passa depois.
- T2 — arquivos: `frontend/src/features/portal/PortalSettings.tsx`, `frontend/src/i18n/pt-BR.json` (`portalInfo.terms.revoke*`), `frontend/src/components/ConfirmDialog.tsx` e `frontend/src/components/ui/summary-box.tsx` (prop opcional de rótulo "Resumo") — prova: mesmo comando → passa.
- T3 — arquivos: `PortalInfoPage.test.tsx` (axe com a janela aberta, claro e escuro) — prova: mesmo comando → passa.
- T4 — prova: `node scripts/canvas-shot.mjs PortalRevogarImagem` e captura do app (MSW, aluno, navegador pt-BR, 1440 e 390 px, relógio em 08/10/2026), conferidas pelo maestro. Em 390 px a janela cabe sem rolagem horizontal (o canvas não tem versão de celular desta janela). Divergências para a descrição do PR:
  - Título em caixa alta no canvas, em caixa mista no app: janela base (#460).
  - "Revogar" é contorno vermelho no canvas e botão escuro cheio no app: botão destrutivo (#451, D2).
  - "Cancelar" é texto sem borda no canvas e botão com borda no app: janela base (#460).
  - Marcadores do resumo: chevron no canvas, seta no app (`SummaryBox`, componente compartilhado, fora do item).
  - Largura da janela: 560 px no canvas, cerca de 512 px no app (#460).
  - O fundo (página Minhas informações) difere do canvas: é a tela do portal (#493), fora do item.
  - Resolvido neste item: texto, rótulo "Resumo", três linhas do resumo e data "Revogado em 08/10/2026" batem com o canvas.
- T5 — prova: `npm run lint && npm run typecheck && npx vitest run src/features/portal src/components` → lint e tipos ok; 47 arquivos, 398 testes passaram.
Regressão completa (`verify`, `test:cov`, `e2e`): não rodada nesta sessão.
Desvios: o canvas tem o rótulo "Resumo" acima dos itens e o `SummaryBox` não tinha; acrescentada a prop opcional `label` (e `summaryLabel` no `ConfirmDialog`), sem mudar os usos existentes. Conceder usa um GET de versão antes do PUT (já era assim); o teste o mocka. D5: `formatDate` usa o fuso padrão da unidade (`DEFAULT_TZ`), sem fuso do navegador.

## Verificação

### Rodada 1 — NAO_CONFERIDO — 2026-10-30, estado working-tree (sem TREE fixado)

| Critério | Status | Evidência |
|---|---|---|
| CA1 | PASSOU | teste "Cancelar e Esc fecham sem chamada" e o de revogação (`image` indefinido antes de confirmar); `npx vitest run src/features/portal src/components` 398 ok |
| CA2 | PASSOU | teste de revogação confere título, "Resumo", 3 itens e data; textos em pt-BR.json batem com a especificação |
| CA3 | PASSOU | `image` igual a `{ granted: false }` e cartão "Não concedido" |
| CA4 | PASSOU | teste de Esc: janela fecha, sem chamada, foco volta ao botão |
| CA5 | PASSOU | teste "Conceder ... sem janela" |
| CA6 | PASSOU | teste de API 500 mostra "Não foi possível salvar. Tente de novo." e fecha a janela |
| CA7 (axe) | PASSOU | dois testes axe, claro e escuro, sem violações |
| CA7 (visual 1440/390 vs canvas) | NÃO CONFERIDO | não refeito nesta verificação; só o relato da Construção (T4). Sem limitação aceita no AGENTS.md |

Comandos: `npm run lint` ok; `npm run typecheck` ok; `npx vitest run src/features/portal src/components` 47 arquivos/398 testes ok; testes dos demais arquivos com SummaryBox/ConfirmDialog ok. Regressão completa (verify, test:cov, e2e) não rodada.
Achados:
- sugestão, código, `PortalSettings.tsx` — `formatDate(new Date())` na data de hoje; conferido que usa o fuso padrão, aceitável.
- sugestão, processo — rodar `test:cov` (cobertura 80%) antes do PR.

### Rodada 2 — NAO_CONFERIDO — 2026-10-30, estado ce8c54943eb2af3c8c5be12b430be089df5d8d69

| Critério | Status | Evidência |
|---|---|---|
| CA1 a CA6 | PASSOU | mantido da rodada 1; diff lido, testes reexecutados: `npx vitest run src/features/portal src/components` 398 ok |
| CA7 (axe, claro e escuro) | PASSOU | dois testes axe com a janela aberta, sem violações |
| CA7 (visual 1440/390 vs canvas) | NÃO CONFERIDO | só o relato da Construção (T4); não refeito aqui. Sem limitação aceita no AGENTS.md |

Comandos: `npm run lint` ok; `npm run typecheck` ok; vitest acima ok. Regressão completa (verify, test:cov, e2e) não rodada.
Achados:
- sugestão, processo — rodar `test:cov` antes do PR; o `SummaryBox` ganhou `div` + `ul` (mudança compartilhada), os testes dos usos existentes passaram.

### Decisão do dono — CA7 aceito — 2026-10-30
O dono aceitou a comparação feita na T4 (capturas do app e do canvas em 1440 e 390 px, pt-BR, com as divergências listadas na Construção) como prova do CA7. O verificador não refaz capturas de navegador; o CA7 fica conferido por essa decisão. Resultado efetivo da verificação: OK, sem achado obrigatório; a entrega segue com a lista de divergências na descrição do PR.

### Rodada 3 — CORRIGIR_CODIGO — 2026-10-30, estado cfa06dd58fcb66f6920e944271037fce8e7f4ac6

| Critério | Status | Evidência |
|---|---|---|
| CA1 a CA6 | PASSOU | mantido da rodada 2; `npm run test:cov` 191 arquivos/1890 testes ok |
| CA7 (axe, claro e escuro) | PASSOU | mantido da rodada 2; dentro do `test:cov` |
| CA7 (visual 1440/390 vs canvas) | PASSOU | aceito pelo dono em 30/10/2026 ("Decisão do dono — CA7 aceito"); o verificador não refez capturas |

Comandos: `npm run lint` ok; `npm run typecheck` ok; `npm run test:cov` ok (1890 testes, limites de cobertura atendidos, sem erro de limiar); `npm run e2e`: 74 passaram, 7 falharam (logs em `%TEMP%/v441/`). Regressão do back-end (`verify`) não rodada: o item só toca o front.
Instáveis: `agenda.spec.ts` "Coordenador abre a Agenda", `comprovantes.spec.ts` "gestão recusa e confirma comprovantes (light)", `configuracao-unidade.spec.ts` "Coordenador edita e Professor vê a área indisponível": estouraram 30 s na rodada completa e passaram nas 4 execuções isoladas (`--workers=2`); arquivos que o item não toca. Uma reexecução isolada de cada, não três; tratar como instáveis sob carga.
Achados:
- obrigatório, código, `frontend/e2e/portal-minhas-informacoes.spec.ts` linhas 57-58 — esperado: e2e `@critical` verde; o teste revoga pelo fluxo novo (clicar em "Revogar uso de imagem", depois confirmar em "Revogar" na janela); observado: o teste clica em "Revogar uso de imagem" e espera "Não concedido" sem confirmar; falha nas 4 variantes (360 e 1024 px, claro e escuro) com `getByText('Não concedido')` não encontrado; evidência: `npm run e2e`, 4 falhas nesse arquivo; corrigir: após o clique, confirmar no diálogo (botão "Revogar" dentro do `dialog`) antes de esperar "Não concedido". Regressão causada pelo item (D1); item não é mesclado com e2e vermelho.
- sugestão, processo — as 3 instáveis acima merecem item próprio no backlog se repetirem.

## Construção (correção)

### Correção da rodada 3
- e2e `@critical` revoga pelo fluxo novo (abre a janela "Revogar uso de imagem", confirma em "Revogar", espera a janela sumir e "Não concedido") — arquivos: `frontend/e2e/portal-minhas-informacoes.spec.ts` — prova: `npx playwright test e2e/portal-minhas-informacoes.spec.ts` → 6 passaram (360 e 1024 px, claro e escuro).
Regressão: o e2e completo não foi refeito nesta sessão; só o arquivo corrigido (as 7 falhas da rodada 3 eram 4 deste arquivo e 3 instáveis de outros arquivos, que o item não toca). Teste de unidade/lint/tipos não mudaram (só o spec e2e foi alterado).

### Rodada 4 — OK — 2026-10-30, estado 6d8e73dcf5501a8ce80f69c76b71453e0bf8c788

| Critério | Status | Evidência |
|---|---|---|
| CA1 a CA6 | PASSOU | mantido da rodada 3; o diff desde então só toca o e2e |
| CA7 (axe) | PASSOU | mantido da rodada 3 |
| CA7 (visual) | PASSOU | aceito pelo dono em 30/10/2026; mantido |

Comandos: `npx playwright test e2e/portal-minhas-informacoes.spec.ts` 6 passaram; `npm run lint` ok; `npm run typecheck` ok; `npm run e2e` 81 passaram, 0 falharam (inclui as 3 instáveis da rodada 3, que passaram desta vez). Testes de unidade e `test:cov` não rodados de novo: nada de código de produto mudou desde a rodada 3 (`git diff` só mostra o spec e2e e este arquivo).
Achado da rodada 3 (e2e `portal-minhas-informacoes.spec.ts`): corrigido; o teste abre a janela, confirma em "Revogar" e espera "Não concedido".
Achados:
- sugestão, processo — descrição do PR deve listar as divergências do canvas da Construção T4.

## Entrega

- O que muda: em "Minhas informações" (cartão Termos), "Revogar uso de imagem" abre a janela "Revogar uso de imagem" com o texto do efeito, o bloco "Resumo" (3 itens, o primeiro com a data de hoje) e os botões "Cancelar" e "Revogar". A API só é chamada ao confirmar. "Conceder uso de imagem" segue direto.
- Verificação: rodada 4, OK, estado 6d8e73dcf5501a8ce80f69c76b71453e0bf8c788. Testes de unidade do portal, axe nos dois temas, `npm run lint`, `npm run typecheck` e `npm run e2e` (81 passaram) e comparação visual com o canvas em 1440 e 390 px, aceita pelo dono.
- Decisões: D2 (botão "Revogar" com estilo padrão; o vermelho destrutivo vem da #451), D3 (erro da API fecha a janela e mostra a mensagem do cartão), D4 (sem regerar prints de referência).
- Limitações: a regressão completa (`verify`, `test:cov`) não foi rerodada nesta entrega; a base não avançou desde a verificação.
- Divergências do canvas (listadas na Construção, T4): título em caixa mista e "Cancelar" com borda (#460); "Revogar" sem contorno vermelho (#451); marcador do resumo e largura da janela (#460, componente compartilhado); fundo da página (#493).
