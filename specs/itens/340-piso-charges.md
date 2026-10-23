# 340 — Piso de 100% de linhas em src/features/charges

Origem: issue #340 (front-end, Sprint 08), D2 do dono e R08-6 de `specs/revisoes/sprint-08.md`. Branch: `item/340-piso-charges`.

## 1. Objetivo

Colocar `src/features/charges/**` no piso de 100% de linhas do `test:cov`, cobrindo o que faltava (97,67%), sem `v8 ignore`.

## 2. Requisitos

- R1: `"src/features/charges/**": { lines: 100 }` em `frontend/vite.config.ts`; `AGENTS.md` e o comentário citam `charges`.
- R2: testes para as linhas sem cobertura: fechar com Esc, envio em branco, envio repetido enquanto grava e falha do servidor na troca de vencimento.
- R3: sem código morto para chegar ao número: o `retry={() => {}}` das janelas de avulsa e de adiantadas sai (`retry` passa a ser opcional em `ModalityDialog`).

## 3. Critérios de aceite

- CA1 (R1, R2): `npm run test:cov` passa com o novo piso (todas as 394 instruções de `features/charges` cobertas na medição).
- CA2 (R1): rodar só um arquivo de `charges` com cobertura reprova o piso (provado: "Coverage for lines (40.4%) does not meet src/features/charges/** threshold").
- CA3 (R3): nenhum `v8 ignore` no diff.

## 4. Plano técnico

Quatro arquivos de teste ganham um caso cada (`AdvanceChargesDialog`, `ChargePanel`, `ChargePreviewPage`, `ChargesPage`); `ModalityPickers.tsx` torna `retry` opcional e só mostra o erro com nova tentativa quando há `retry`; duas janelas deixam de passar um `retry` que nunca é usado (`state="ready"`).

## 5. Tarefas

- [x] T1: medir as linhas sem cobertura (relatório `json`) — cobre CA1
- [x] T2: testes e remoção do `retry` morto — cobre CA1, CA3
- [x] T3: piso, `AGENTS.md` e comentário — cobre CA2

## 6. Decisões

- D1: `GrantBenefitDialog` (benefícios) tem o mesmo `retry={() => {}}`; não mexi porque a pasta não está no piso e o item é de `charges`.
- D2 (achado na medição, corrigido aqui): no `npm run test:cov` desta branch o `CA10` de `LessonPanelPage.test.tsx` (convertido no #333) falhou 1 vez: o `server.use` da resposta retida vinha depois do `render`, e sob carga o pedido da aula já tinha usado a resposta padrão. A resposta retida passa a entrar por `setup({ respond })`, antes do `render`. Medição: o arquivo 10 vezes → 0 falhas. Os outros testes convertidos no #333 registram a resposta retida antes do pedido (o pedido só sai ao digitar, ou entra pelo `setup`).
