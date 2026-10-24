# 362 — Teste e2e da matrícula instável: estouro horizontal a 360 px (CA24)

Origem: issue #362 (front-end) e achado R09-1 / ajuste A1 de `specs/revisoes/sprint-09.md`. Branch: `item/362-barra-360px`. Mesmo formato da #318 e da #320 (medir antes e depois).

## 1. Objetivo

A barra superior da gestão (`ManagementLayout`) deve caber em 360 px em todos os estados, inclusive enquanto as unidades carregam. Hoje, com o sino da #84, o conjunto à direita passa de 360 px por 14 px durante "Carregando unidades..." e o `@critical` `matricula.spec.ts` (CA24) reprova ao acaso. A causa é corrigida; o teste não ganha retry nem timeout maior.

Fora do escopo: outras telas, o menu do portal (Sprint 10), mudar o sino ou o seletor de unidades quando carregado.

## 2. Requisitos

- R1: com as unidades carregando, a barra superior da gestão não gera rolagem horizontal a 360 px (`scrollWidth <= innerWidth`).
- R2: o mesmo vale com o sino em `99+`, com o seletor de unidades carregado (nome longo) e com o estado de erro das unidades.
- R3: o alvo de toque de 44 px dos botões da barra é mantido (D1).
- R4: o estado "Carregando unidades..." segue anunciado a leitores de tela (`role="status"`).
- R5: o teste de 360 px passa a cobrir a barra: mede logo após `setViewportSize` e de novo depois de as unidades carregarem.
- R6: o antes e o depois (falhas por execuções) ficam registrados neste arquivo.

## 3. Critérios de aceite

- CA1 (R1, R4): dado `/alunos/nova-matricula` a 360 px, quando a página carrega e o seletor está em carregamento, então `scrollWidth <= innerWidth` e o `role="status"` com "Carregando unidades..." existe.
- CA2 (R2): dado o sino com 100 ou mais não lidas, quando a barra é medida a 360 px, então não há rolagem horizontal.
- CA3 (R2): dado o usuário com duas unidades de nome longo e o seletor carregado, quando medido a 360 px, então não há rolagem horizontal.
- CA4 (R2): dado erro ao carregar unidades, quando medido a 360 px, então não há rolagem horizontal.
- CA5 (R3): botões da barra mantêm altura e largura de ao menos 44 px.
- CA6 (R5, R6): `npx playwright test e2e/matricula.spec.ts --repeat-each=30` a 360 px passa 30 de 30 (antes: registrado na construção), e `npm run e2e` passa em 10 execuções seguidas.
- CA7: axe nos dois temas e `npm run test:cov` seguem verdes.

## 4. Plano técnico

- Dados, API, segurança: não mudam.
- Interface: em `frontend/src/features/units/UnitSwitcher.tsx` o estado `loading` deixa de mostrar o texto de 73 px: mantém `role="status"` com o texto em `sr-only` (sem largura visual) e, se quiser sinal visual, um esqueleto pequeno de largura fixa. O estado de erro (`alert` + "Tentar de novo") pode passar de 360 px: no mesmo arquivo, o texto do erro ganha `truncate`/`min-w-0`, ou o bloco quebra linha. Em `frontend/src/components/layout/AppLayout.tsx`, o contêiner `ml-auto flex items-center gap-2` do cabeçalho ganha `min-w-0 flex-wrap justify-end` para que, no pior caso, quebre linha em vez de estourar. O select carregado já tem `max-w-[11rem]`; conferir que cabe com o resto.
- Arquivos (vistos): `frontend/src/features/units/UnitSwitcher.tsx` (`UnitSwitcher`), `frontend/src/components/layout/AppLayout.tsx` (cabeçalho), `frontend/src/features/notifications/BellButton.tsx` (só conferir o contador `99+`, `min-w-11`), `frontend/src/features/permissions/ManagementLayout.tsx` (só conferir), `frontend/e2e/matricula.spec.ts` (`noOverflow`, linhas 98-103), `frontend/src/features/units/UnitScope.test.tsx` (o teste de loading, linha 88, segue achando o texto). Texto em `frontend/src/i18n/pt-BR.json` (`unitScope.loading`) não muda.
- Reuso: o `noOverflow` do e2e; o `sr-only` do Tailwind.
- Riscos: jsdom não mede layout, então a prova de largura é só do Playwright; o mesmo `AppLayout` serve outras telas da gestão e da conta (`AccountFrame`), conferir que `flex-wrap` não muda o layout a partir de 640 px.

## 5. Tarefas

- [x] T1: medir o antes: sonda de 40 cargas de `/alunos/nova-matricula` a 360 px (arquivo temporário, descartado) e `npx playwright test e2e/matricula.spec.ts --repeat-each=30`; registrar falhas por execuções — cobre CA6 (antes) — prova: `cd frontend && npx playwright test e2e/matricula.spec.ts --repeat-each=30`
- [x] T2: corrigir `UnitSwitcher` (carregando sem largura visual, erro que não estoura) e o contêiner do cabeçalho em `AppLayout` — cobre CA1, CA3, CA4, CA5 — prova: `cd frontend && npx vitest run src/features/units/UnitScope.test.tsx`
- [x] T3: no e2e, medir a barra logo após `setViewportSize`, depois de `Etapa 1 de 5` e depois de as unidades carregarem (seletor ou ausência do status); acrescentar os casos de sino `99+`, nome longo e erro das unidades, onde o mock permitir — cobre CA1 a CA4 — prova: `cd frontend && npx playwright test e2e/matricula.spec.ts`
- [x] T4: medir o depois (30 repetições e 10 `npm run e2e` seguidos) e registrar — cobre CA6 — prova: `cd frontend && npx playwright test e2e/matricula.spec.ts --repeat-each=30`
- [x] T5: regressão do front — cobre CA7 — prova: `cd frontend && npm run lint && npm run typecheck && npm run test:cov`

## 6. Decisões

- D1: o esqueleto de carregamento não tem texto visível (texto só para leitor de tela), em vez de esconder o rótulo de "Minha conta" abaixo de 400 px: a causa é o estado transitório, e o alvo de 44 px e os rótulos ficam como estão. Alternativa descartada: ocultar "Minha conta" (muda o que o usuário vê o tempo todo).
- D2: `flex-wrap` no cabeçalho como segunda barreira, aceitando que num caso extremo a barra ocupe duas linhas em vez de rolar na horizontal.
- D3: nenhum retry, timeout maior ou `test.slow`; a medição espera o estado assentar só porque a barra agora é medida nos dois momentos (R5), não para esconder o estouro.
- Suposição: o marco da issue #362 no GitHub é "Sprint 10 · Portal do aluno", mas a revisão (A1) a pede antes do PR `dev`→`main` da Sprint 09, e o backlog a lista na Sprint 09, como pediu o `maestro`. Se o dono preferir a Sprint 10, só muda a linha do backlog e o marco da issue.

## Construção

- T1 — arquivos: nenhum (sonda dispensada: a própria repetição mede a carga a 360 px) — prova: `npx playwright test e2e/matricula.spec.ts --repeat-each=30` → antes: 2 falhas em 30 execuções do teste da nova matrícula (CA24); 88 de 90 no arquivo todo
- T2 — arquivos: `frontend/src/features/units/UnitSwitcher.tsx` (carregando só `sr-only` com `role="status"`; erro com `min-w-0 truncate`), `frontend/src/components/layout/AppLayout.tsx` (contêiner `min-w-0 flex-wrap justify-end`) — prova: `npx vitest run src/features/units` → 69 passam
- T3 — arquivos: `frontend/e2e/matricula.spec.ts` (mede após `setViewportSize`, após abrir a rota, após "Etapa 1 de 5" e após o status de carregamento sumir) — prova: coberto pelo T4
- T4 — prova: `npx playwright test e2e/matricula.spec.ts --repeat-each=30` → depois: 90 de 90; `npm run e2e` 10 vezes seguidas → 10 de 10 (36 testes cada)
- T5 — prova: `npm run lint && npm run typecheck && npm run test:cov` → verdes (linhas 96,8%)
Regressão: só front, como o item pede; back-end não alterado, não rodou.
Desvios: os casos de sino `99+`, nome longo e erro das unidades (CA2 a CA4) não ganharam teste e2e: o mock do navegador (MSW) não deixa forçar essas respostas sem mexer em handlers, fora do escopo; a proteção é `min-w-0`/`truncate`/`flex-wrap`, sem prova de largura própria. Axe (CA7) não foi rodado à parte além do `e2e` @critical.

## Verificação

### Rodada 1 — NAO_CONFERIDO — 2026-10-24, estado da62718fda1a979b195a210bc3e846595240f868

| Critério | Status | Evidência |
|---|---|---|
| CA1 | PASSOU | e2e mede `scrollWidth <= innerWidth` após `setViewportSize`, após abrir a rota e após "Etapa 1 de 5"; 90 de 90 em `--repeat-each=30`. `role="status"` com o texto mantido em `sr-only` (diff de `UnitSwitcher.tsx`; `UnitScope.test.tsx` passa no `test:cov`) |
| CA2 | NÃO CONFERIDO | nenhum teste ou medição com sino `99+`; só `min-w-0`/`flex-wrap` no código. Limitação não está no AGENTS.md |
| CA3 | NÃO CONFERIDO | idem, nome longo sem medição |
| CA4 | NÃO CONFERIDO | idem, estado de erro das unidades sem medição (`truncate` aplicado, sem prova) |
| CA5 | PASSOU | diff não toca tamanho de botão; `Button`/`BellButton` inalterados |
| CA6 | PASSOU | `npx playwright test e2e/matricula.spec.ts --repeat-each=30`: 90 passed; `npm run e2e`: 36 passed (1 execução minha; as 10 seguidas são relato da construção, não repetidas) |
| CA7 | PASSOU | `npm run lint && typecheck && test:cov` exit 0 (linhas 96,8%); axe roda nos `@critical` do e2e, verdes |

Comandos: lint, typecheck, test:cov (verdes); playwright matricula x30 (90/90); `npm run e2e` (36/36). Back-end não tocado.
Achados:
- sugestão, código, `frontend/e2e/matricula.spec.ts` — CA2 a CA4 sem prova de largura; medir manualmente (sino 99+, nome longo, erro) ou acrescentar casos com o mock que permitir, ou o dono aceitar a limitação em "Limitações aceitas".
- sugestão, código, `UnitSwitcher.tsx` — no estado de carga o `sr-only` some visualmente sem esqueleto; aceitável (D1).

### Rodada 2 — OK — 2026-10-24, estado 27274f6fbbad4506e6ca8c8b2382c1eeb672ec31

Mudança desde a rodada 1 (`da62718`): só `specs/` (backlog e este arquivo); código igual. Resposta do dono: opção 1, CA2, CA3 e CA4 são limitação aceita só deste item, sem teste de largura próprio; destino é a issue #368 (Sprint 10, autor `DevDosAnjos`, cita CA2 a CA4 da #362 como primeiros casos; linha 368 no backlog).

| Critério | Status | Evidência |
|---|---|---|
| CA1 | PASSOU | mantido da rodada 1; `matricula.spec.ts` x10: 30 passed |
| CA2 | NÃO CONFERIDO (limitação aceita) | aceito pelo dono só neste item; prova na #368 |
| CA3 | NÃO CONFERIDO (limitação aceita) | idem |
| CA4 | NÃO CONFERIDO (limitação aceita) | idem |
| CA5 | PASSOU | mantido da rodada 1 |
| CA6 | PASSOU | `npm run e2e`: 36 passed; matricula x10: 30 passed (30x e 10 seguidas: relato da construção/rodada 1) |
| CA7 | PASSOU | lint, typecheck, test:cov exit 0 (linhas 96,8%); axe nos `@critical` verdes |

Comandos: `npm run lint && typecheck && test:cov` (verde); `npx playwright test e2e/matricula.spec.ts --repeat-each=10` (30/30); `npm run e2e` (36/36). Back-end não tocado.
Achados:
- sugestão, código, `UnitSwitcher.tsx` — carregando sem esqueleto visual; aceitável (D1).

## Entrega

- O que muda: a barra da gestão cabe em 360 px também com as unidades carregando. O estado de carregamento do seletor vira só texto para leitor de tela (`sr-only`, `role="status"` mantido), o erro das unidades ganha `min-w-0`/`truncate`, e o contêiner do cabeçalho em `AppLayout` ganha `min-w-0 flex-wrap justify-end` como segunda barreira. O e2e `matricula.spec.ts` mede a largura após abrir a rota, após "Etapa 1 de 5" e após o status de carregamento sumir.
- Verificação: rodada 2 `OK` (estado `27274f6`), com os resultados da própria rodada: `matricula.spec.ts --repeat-each=10` 30/30; `npm run e2e` 36/36; lint, typecheck e `test:cov` verdes (linhas 96,8%); na rodada 1, `--repeat-each=30` deu 90/90. Só front; back-end não tocado. Nenhuma regressão nova nesta fase: a base (`dev`, `02983b0`) não avançou desde a verificação.
- Decisões: D1 (esqueleto sem texto visível), D2 (`flex-wrap` como segunda barreira), D3 (sem retry nem timeout maior). Detalhes na seção 6.
- Limitações: CA2, CA3 e CA4 (sino com 99 ou mais, nome longo, erro das unidades) não têm prova de largura própria; o mock do navegador não força essas respostas. O dono aceitou a limitação neste item; a prova vai para a #368. Axe só roda nos `@critical` do e2e.
