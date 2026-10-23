# 333 — Testes de tela com "Carregando..." instáveis

Origem: issue #333 (front-end, Sprint 08), achado R08-3 de `specs/revisoes/sprint-08.md`. Branch: `item/333-carregando-instavel`.

## 1. Objetivo

Trocar `delay(50)` e `delay(100)` por uma resposta retida pelo teste nos testes que afirmam o estado transitório "Carregando...", sem retry nem timeout maior. Fora do escopo: código de produto.

## 2. Requisitos

- R1: o handler espera uma promessa controlada pelo teste (`gate()`), a asserção de "Carregando..." vem antes de liberar.
- R2: nenhum `retry`, `testTimeout` ou `waitFor` com tempo maior.

## 3. Critérios de aceite

- CA1 (R1): `AdvanceChargesDialog`, `OneOffChargeDialog`, `ChargePanel`, `LessonPanelPage` e `PublicReceiptPage` passam 20 de 20.
- CA2 (R1): `npm run test:cov` com o paralelismo padrão passa.
- CA3 (R2): o diff não toca em `vite.config.ts` nem acrescenta retry.

## 4. Plano técnico

`frontend/src/test/gate.ts` (novo, 6 linhas) e quatro arquivos de teste. `PublicReceiptPage.test.tsx` não muda: afirma o estado de forma síncrona logo depois do `render`, sem corrida.

## 5. Tarefas

- [x] T1: `gate()` e uso nos quatro arquivos — cobre CA1, CA3
- [x] T2: medir — cobre CA1, CA2 — prova: os cinco arquivos 20 vezes → 0 falhas em 20; `npm run test:cov` (padrão) → 124 arquivos, 1075 testes, exit 0

## 6. Decisões

- D1: a medição de antes é a da revisão (1 falha em 1 execução do `test:cov` padrão, `AdvanceChargesDialog.test.tsx:98`); isolado e com `--maxWorkers=2` o defeito não aparece, por isso o conserto vale pelo padrão e não pela contagem.
