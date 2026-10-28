# 388 — Teste e2e "gestão recusa e confirma comprovantes (light)" estoura 30 s na suíte inteira

Origem: issue #388 (front-end, Sprint 10; vista na verificação do #89), mesmo formato da #386. Branch: `item/388-e2e-comprovantes-30s`.

## 1. Objetivo

Confirmar se `frontend/e2e/comprovantes.spec.ts` (`@critical`, claro e escuro) ainda estoura 30 s dentro da suíte inteira (`npm run e2e`) e, havendo falha, achar e corrigir a causa. A causa provável já foi corrigida na #386 (mergeada em `dev` depois de a #388 ser aberta): `chooseLink` esperava o diálogo por 3 s fixos em 5 das 6 chamadas por teste (cerca de 15 s dos 30 s). Este item mede o estado atual; sem falha, o código não muda e a issue fecha com os números e com a referência à #386.

Fora do escopo: outros testes e2e, mudar a tela de conferência, refatorar os helpers do spec.

## 2. Requisitos

- R1: o teste passa 20 de 20 dentro da suíte inteira (`npm run e2e`, workers em paralelo, `--retries=0`), medido no estado atual de `dev` (D1).
- R2: havendo falha, o passo lento é achado pelo trace e a causa corrigida na raiz (esperar o estado final); nenhum `retry` e nenhum timeout maior.
- R3: sem falha, o código não muda e a issue fecha com os números e a ligação com a #386 (D2).

## 3. Critérios de aceite

- CA1 (R1): dado o estado atual do spec, quando `npm run e2e` roda 20 vezes, então `comprovantes.spec.ts` passa em todas (light e dark) e o resultado fica registrado com falhas/execuções.
- CA2 (R2): dada uma falha, quando a causa é corrigida, então 20 de 20 de novo, sem `retry`, sem timeout maior, sem espera maior só para esconder.
- CA3 (R3): dado 0 falhas, quando a medição termina, então nenhum arquivo de código muda e a issue recebe os números e fecha.

## 4. Plano técnico

- Dados, API, segurança, interface: não mudam (só teste).
- Medição, de dentro de `frontend/`: laço de shell com uma execução completa por vez, `for i in $(seq 20); do npm run e2e -- --retries=0 || echo FALHOU $i; done`, registrando o teste e o passo que falhou (trace em `retain-on-failure`, `npx playwright show-trace`). São 20 rodadas de ~3 min (cerca de 1 h): rodar em segundo plano. Vale também o `--repeat-each` do spec isolado com `--workers=1` como referência (a #386 mediu 0/40).
- Estado atual visto: `frontend/e2e/comprovantes.spec.ts` já usa `modal.or(swap).first().waitFor()` em `chooseLink` (da #386); não há `test.setTimeout` nem retry no spec. Resíduo conhecido da #386: `modal.isVisible()` logo após o `waitFor` é instantâneo; se o diálogo abrir entre os dois passos, o clique em "Trocar" falha. Só vira correção se a medição mostrar falha nesse ponto.
- Correções possíveis, só conforme a causa medida: trocar o `isVisible()` por espera de estado; injetar o axe uma vez por página (checar `globalThis.axe` antes de `addScriptTag`); esperar o estado final antes de cada `call`.
- Arquivos: `frontend/e2e/comprovantes.spec.ts` (visto; `chooseLink`, `violations`, `open`, `call`), alterado só se houver falha. Nenhum a criar.
- Riscos: falha rara que só aparece com a suíte inteira em paralelo (não reproduz isolada, como na #386); 20 rodadas são longas, rodar em segundo plano e não deixar outra carga pesada ao lado sem registrar.

## 5. Tarefas

- [x] T1: medir `npm run e2e` 20 vezes no estado atual e registrar falhas/execuções e o passo que estourou, se houve — cobre CA1 — prova: o laço acima, de dentro de `frontend/`.
- [x] T2: se houve falha, ler o trace e corrigir na raiz; se não, não alterar código — cobre CA2 ou CA3 — prova: o mesmo laço, 0 falhas em 20.
- [x] T3: rodar `npm run lint && npm run typecheck`, e deixar os números no resumo da construção, para o PR (`Closes #388`) e o comentário na issue, citando a #386 — cobre CA3 (e CA2 se houve correção) — prova: `cd frontend && npm run lint && npm run typecheck`.

## 6. Decisões

- D1: medir no estado atual de `dev`, que já tem a correção da #386, porque a #388 (aberta em 27/10 às 03:37Z) descreve o mesmo teste e a mesma causa que a #386 corrigiu (mergeada depois). Alternativa descartada: reproduzir antes na versão anterior (já feito na #386: 1 falha no `npm run e2e` completo).
- D2: sem falha em 20 de 20, fecha-se com os números e a referência à #386, sem código; a issue permite só correção na causa, nunca `retry` nem timeout maior. Alternativa descartada: reforçar esperas "por precaução".
- Suposição: a falha da #388 é a da #386. Se falhar na medição, vale R2, e o resíduo do `isVisible()` é o primeiro suspeito.

## Construção

- T1 — arquivos: nenhum — prova: laço de 20 × `npm run e2e -- --retries=0` em `frontend/`, estado atual de `dev` (com a #386) → 0 falhas em 20 execuções; 81 testes passaram em todas as rodadas (1,6 a 3,0 min cada); `comprovantes.spec.ts` passou 40 de 40 (20 × light e dark). Tempo mais alto do spec: 27,7 s (seguintes: 24,9 s e 23,7 s), ou seja, sem estouro, mas com folga pequena sob 30 s com a suíte inteira em paralelo.
- T2 — arquivos: nenhum — não houve falha, código não alterado (CA3, D2).
- T3 — arquivos: nenhum — prova: `cd frontend && npm run lint && npm run typecheck` → passou (saída 0).
Regressão: sem código alterado; a medição acima é a própria regressão e-2-e (20 rodadas verdes).
Desvios: nenhum.
Não é do item: nenhum (só `specs/backlog.md` e este arquivo, da fase anterior).

## Verificação

### Rodada 1 — OK — 2026-10-27, estado f3c913c85a878f4ad90aed853684cdcbee69eeaa

| Critério | Status | Evidência |
|---|---|---|
| CA1 | PASSOU | construção registra 20 de 20 rodadas verdes (40 de 40 do spec); nesta verificação, 1 rodada própria de `npm run e2e -- --retries=0`: 81 passed (2,2 min). As 20 rodadas não foram repetidas aqui (cerca de 1 h); aceitas pelo registro da construção |
| CA2 | PASSOU (não aplicável) | sem falha, sem correção; `retries` só `CI ? 1 : 0` no playwright.config (anterior ao item), sem `setTimeout` no spec |
| CA3 | PASSOU | `git diff --stat`: só o arquivo do item e `specs/backlog.md` (sem diferença no índice temporário); nenhum código mudou |

Comandos: `npm run lint && npm run typecheck` (frontend) sem erro; `npm run e2e -- --retries=0` 81 passed. Issue #388 (autor DevDosAnjos) confere com a especificação.
Achados:
- sugestão, código, `frontend/e2e/comprovantes.spec.ts` — folga pequena: maior tempo 27,7 s na construção sob 30 s; acompanhar.

## Entrega

Medição, sem alteração de código: a falha da #388 não reproduziu em `dev` com a correção da #386. Em 20 execuções completas de `npm run e2e -- --retries=0` (suíte inteira, em paralelo), `comprovantes.spec.ts` passou 40 de 40 (light e dark), sem falha nem `retry`. Maior tempo do spec: 27,7 s, abaixo dos 30 s, com folga pequena; acompanhar.

Verificação: rodada 1 OK (estado `f3c913c8`), com 81 testes passando em uma execução própria; `npm run lint` e `npm run typecheck` sem erro.

Decisões: sem código alterado, conforme D2 (fecha com os números e a referência à #386). Resíduo do `isVisible()` em `chooseLink` (#386) não foi alterado: a medição não mostrou falha nesse ponto.

Limitações: a medição de 20 rodadas foi feita na construção; a verificação não as repetiu. A folga de 27,7 s pode voltar a aproximar de 30 s sob carga maior.
