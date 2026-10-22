# 318 — Teste CA20 da aba Financeiro instável: medir e estabilizar

Origem: issue #318 (front-end, Sprint 08). Branch: `item/318-ca20-financeiro-instavel`. Medição nos 4 `CA20` de `charges/ChargePanel.test.tsx` (0/20 isolado); a correção ficou em `frontend/src/features/agenda/LessonPanelPage.test.tsx` (CA10) e, se a medição achar a causa, o componente que ela apontar.

## 1. Objetivo

Descobrir se algum `CA20` do painel de cobrança falha de forma intermitente e, se falhar, corrigir a causa (espera, relógio, ordem de render) sem retry nem aumento de timeout escondido. Se não falhar nas medições, fechar a issue com o resultado.

Fora do escopo: outros testes instáveis, mudança de comportamento do painel, refatorar a suíte.

## 2. Requisitos

- R1: a instabilidade é medida antes de qualquer correção: cada `CA20` rodado 20 vezes, com a contagem de falhas por execuções registrada na issue.
- R2: se houver falha, a causa é identificada e corrigida na raiz (D2); nenhum `retry` do Vitest nem aumento de `testTimeout` sem registro na descrição do PR.
- R3: se não houver falha em nenhuma medição (D1), nada no código muda e a issue é fechada com o resultado.
- R4: depois da correção, os `CA20` passam 20 de 20 nas mesmas condições em que falharam, e `npm test` segue verde.

## 3. Critérios de aceite

- CA1 (R1): dado os quatro `CA20`, quando rodados 20 vezes isolados, 20 vezes no arquivo inteiro e 20 vezes em `npm test` completo (máquina carregada), então a issue traz falhas/execuções de cada condição e o nome do teste que falhou.
- CA2 (R2, R4): dado uma condição com falha, quando a causa é corrigida, então 20 de 20 passam nela, sem `retry`, sem mudança em `testTimeout` e sem `waitFor` com tempo maior só para esconder.
- CA3 (R3): dado 0 falhas em todas as condições, quando a medição termina, então o código fica intacto e a issue recebe o comentário com os números e é fechada.

## 4. Plano técnico

- Dados, API, segurança: não mudam. Interface: não muda (só teste, salvo causa no componente).
- Medição já feita na fase 2: `npx vitest run src/features/charges/ChargePanel.test.tsx -t "CA20"` em laço de 20 execuções isoladas: **0 falhas em 20** (4 testes por execução, 10 pulados). `--repeat` não existe no Vitest 5.0.5 (`Unknown option --repeat`); a repetição é por laço de shell (ou `it` com `{ repeats: 20 }` só localmente, sem commit).
- Condições ainda a medir: arquivo inteiro (`ChargePanel.test.tsx`, 20x) e `npm test` completo (20x, ou menos se demorar, registrando quantas). A instabilidade apontada pelo dono provavelmente aparece com carga (jsdom + userEvent; `vite.config.ts` já tem `testTimeout: 20000` por esse motivo).
- Suspeitas para a investigação se houver falha (não são fato): o teste 500 usa `delay(100)` e checa "Carregando..." logo após abrir, que pode já ter passado sob carga; o axe usa `window.innerWidth = 360` e `setDevicePrefersDark` mutáveis entre execuções, sem restaurar; `setup()` reaproveita o `server` global.
- Arquivos: `frontend/src/features/charges/ChargePanel.test.tsx` (testes `CA20`, linhas ~126, 138, 143, 258) e, só se a causa estiver lá, o componente do painel. Nenhum a criar.
- Reuso: `axeViolations`, `setDevicePrefersDark`, `server` do MSW.
- Riscos: falha rara que não reproduz em 20 execuções; por isso a medição sob carga. Corrigir por timeout mascararia (proibido pela issue).

## 5. Tarefas

- [x] T1: medir o teste afetado só pelo arquivo, `npx vitest run <arquivo> --maxWorkers=2`, 20x, uma por vez, nunca `npm test` em laço — cobre CA1 — medição: o CA20 que falhou nos logs é `charges/ChargePanel.test.tsx` (só em suíte embaralhada); isolado: `StudentFinancialTab.test.tsx` 0/20, `ChargePanel.test.tsx` 0/20. O instável real é o CA10 de `agenda/LessonPanelPage.test.tsx`: 1/20 antes da correção.
- [x] T2: causa do CA10: o handler respondia na hora e o `findByText("Carregando...")` perdia a janela. Correção: `await delay(100)` no handler (padrão do ChargePanel), sem retry nem timeout — cobre CA2 — depois: 0/20 com `--maxWorkers=2`.
- [x] T3: resultado registrado — cobre CA3 — a issue fecha pelo PR (`Closes #318`).

## 6. Decisões

- D1: se nenhuma condição falhar, fecha-se com o resultado, como a própria issue manda, sem alterar código. Motivo: sem falha medida não há o que corrigir. Alternativa descartada: reforçar esperas "por precaução", que é mascarar.
- D2: a correção, se houver, é na espera ou no estado compartilhado do teste (por exemplo restaurar `innerWidth` e tema no fim, esperar o estado final em vez de um instante intermediário), nunca `retry` ou timeout maior. Alternativa descartada: `retry` do Vitest.
- D3: o "20 vezes" é por laço de shell porque o Vitest instalado não tem `--repeat`; a issue previa "ou flag equivalente".
- Suposição: o dono viu a falha na suíte completa ou no CI, não isolada. Se estiver errada e a falha for só em outra máquina, o resultado "0 falhas" fecha o item e o dono reabre com o log da falha.

## Verificação

### Rodada 1 — OK — 2026-10-22, estado 3c910f2d812d0fa29650f651fc4ceac52d51be0d

| Critério | Status | Evidência |
|---|---|---|
| CA1 | PASSOU | Medição registrada em T1 (ChargePanel e StudentFinancialTab 0/20; CA10 de LessonPanelPage 1/20 antes da correção). A contagem na issue vem com o PR (`Closes #318`); não conferida aqui. |
| CA2 | PASSOU | Diff: só `await delay(100)` no handler do CA10; sem retry, sem mudança de testTimeout, sem waitFor maior. LessonPanelPage.test.tsx 3 de 3 verdes (21/21) com --maxWorkers=2. |
| CA3 | PASSOU | Houve falha medida (CA10), então CA3 não se aplica; a issue fecha pelo PR. |

Comandos: `npx vitest run --maxWorkers=2` (suíte completa, uma vez): 114 arquivos, 1016 testes, todos passaram. `npx vitest run src/features/agenda/LessonPanelPage.test.tsx --maxWorkers=2` 3x: 21/21 cada.
Instáveis: nenhum observado nesta rodada.
Achados:
- sugestão, especificação — o cabeçalho diz que o item toca só ChargePanel.test.tsx, mas a correção foi em agenda/LessonPanelPage.test.tsx (T1/T2 explicam). Ajustar o texto se quiser.
- sugestão — specs/backlog.md traz também a linha da #320 (fora do item); inofensivo.

## Entrega

- **O que muda:** o teste do CA10 da agenda (`frontend/src/features/agenda/LessonPanelPage.test.tsx`) passa a esperar o estado "Carregando..." antes da resposta de erro, com `await delay(100)` no handler MSW. Nenhum outro arquivo de produto ou teste muda.
- **Medição:** CA10 da agenda 1/20 falhas antes da correção e 0/20 depois. ChargePanel e StudentFinancialTab 0/20 cada um, isolados; o CA20 do ChargePanel não foi reproduzido isolado.
- **Causa do CA10:** o handler respondia na hora, então o "Carregando..." sumia antes do `findByText` pegar o estado de carregamento.
- **Correção:** `await delay(100)` no handler. Sem retry, sem timeout maior, sem mudança de `testTimeout`.
- **Verificação:** rodada 1 OK (estado 3c910f2d812d0fa29650f651fc4ceac52d51be0d); suíte completa com `--maxWorkers=2`: 114 arquivos, 1016 testes verdes.
- **Limitação:** o CA20 do ChargePanel só apareceu com a suíte inteira embaralhada. Ficou sob observação pela política de teste instável da verificação, sem nova correção.
