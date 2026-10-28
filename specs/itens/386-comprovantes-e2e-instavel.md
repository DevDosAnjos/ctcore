# 386 — Teste e2e comprovantes instável: medir e estabilizar

Origem: issue #386 (front-end, Sprint 10), mesmo formato da #318, #320 e #385. Branch: `item/386-comprovantes-e2e-instavel`.

## 1. Objetivo

Medir quantas vezes `frontend/e2e/comprovantes.spec.ts` (item #87, `@critical`, claro e escuro) falha em repetição, com e sem carga, e, havendo falha, achar o passo que passa de 30 s e corrigir a causa sem mascarar. Sem falha medida, o código não muda e a issue fecha com os números.

Fora do escopo: outros testes e2e, mudar o comportamento da tela de conferência, refatorar os utilitários dos demais specs.

## 2. Requisitos

- R1: a taxa de falha é medida antes de qualquer correção, com `--repeat-each=20 --workers=1 --retries=0`, em duas condições (sem carga e com carga, D1); cada uma registra falhas em execuções e o passo que estourou.
- R2: havendo falha, o passo lento é achado pelo trace e a causa é corrigida na raiz (esperar o estado final, não o instante); nenhum `retry` e nenhum aumento de timeout sem registro na descrição do PR.
- R3: sem falha em nenhuma condição, o código não muda e a issue é fechada com os números (D2).
- R4: depois da correção, o teste passa em todas as execuções nas condições em que falhou, e `npm run e2e` segue verde.

## 3. Critérios de aceite

- CA1 (R1): dado o teste, quando rodado com `--repeat-each=20 --workers=1 --retries=0` sem carga e com carga, então a issue e o PR trazem falhas/execuções de cada condição e o passo que estourou, se houve.
- CA2 (R2, R4): dada uma condição com falha, quando a causa é corrigida, então 0 falhas na mesma quantidade de execuções, sem retry, sem timeout maior e sem espera maior só para esconder.
- CA3 (R3): dado 0 falhas nas duas condições, quando a medição termina, então nenhum arquivo de código muda e a issue recebe os números e fecha.
- CA4 (R4): dado o estado final, quando roda `npm run lint && npm run typecheck && npm run e2e`, então passam.

## 4. Plano técnico

- Dados, API, segurança, interface: não mudam (só teste, salvo causa no componente).
- Medição (de dentro de `frontend/`, o `webServer` do Playwright sobe o `npm run dev`): `npx playwright test e2e/comprovantes.spec.ts --repeat-each=20 --workers=1 --retries=0`. Carga: o mesmo comando com `./backend/mvnw -f backend/pom.xml verify` rodando ao mesmo tempo (a condição em que a falha foi vista); se Docker não estiver ativo, usar outra carga de CPU equivalente e registrar qual. O trace fica em `retain-on-failure` (`playwright.config.ts`); ler com `npx playwright show-trace`.
- Estado atual visto em `frontend/e2e/comprovantes.spec.ts` (118 linhas): um único teste por tema, sem `test.setTimeout`, logo vale o padrão de 30 s para o fluxo inteiro. O fluxo faz login, 3 trocas de vínculo para o aluno e 3 de volta à gestão (`chooseLink`), 4 chamadas `call` por `fetch` no navegador, 5 execuções de axe (`violations` injeta `axe.min.js` com `addScriptTag` a cada chamada) e 2 diálogos. O timeout de 30 s é do teste inteiro, não de um passo.
- Suspeitas para a investigação (não são fato): (1) o tempo somado dos passos, com as 5 injeções de axe, chega perto de 30 s sob carga, sem nenhum passo isolado travado; (2) `chooseLink` espera o diálogo por `waitFor({ timeout: 3000 })` e, se ele não aparece, clica em "Trocar organização ou área": sob carga, o diálogo que aparece depois dos 3 s faz o clique abrir um segundo seletor ou perder o estado; (3) `open()` navega por `pushState` e `popstate`, sem esperar a rota carregar antes do próximo `call`, e o `call` do aluno depende da sessão já trocada; (4) corrida entre o `await expect(page).not.toHaveURL(/\/portal\/termo$/)` e a gravação do aceite, já corrigida uma vez na #87.
- Correções possíveis, conforme a causa: injetar o axe uma vez por página (checar `globalThis.axe` antes de `addScriptTag`) para cortar tempo real; trocar a espera por tempo do `chooseLink` por espera do estado (diálogo visível ou botão de troca visível); esperar o estado final antes de cada `call`. `test.setTimeout` maior só se a medição mostrar que o fluxo legítimo passa de 30 s, com o número no PR (D3).
- Arquivos: `frontend/e2e/comprovantes.spec.ts` (visto; `violations`, `open`, `call`, `chooseLink` e o `test`) e, só se a causa estiver lá, o componente da tela de conferência ou `frontend/src/mocks/handlers.ts`. Nenhum a criar. Os helpers são locais deste spec; não há reuso em `e2e/support` (só `msw.ts`).
- Riscos: falha rara que não reproduz sem carga (por isso as duas condições); "corrigir" por timeout mascararia (proibido pela issue); a carga do `verify` do back-end é pesada e longa, rodar uma vez, não em laço.

## 5. Tarefas

- [x] T1: medir sem carga e com carga antes de mexer em código e registrar falhas/execuções e o passo que estourou (trace) — cobre CA1 — prova: `cd frontend && npx playwright test e2e/comprovantes.spec.ts --repeat-each=20 --workers=1 --retries=0`, sem e com o `verify` do back-end ao lado.
- [x] T2: se houve falha, ler o trace, achar o passo e corrigir na raiz; se não houve, não alterar código — cobre CA2 ou CA3 — prova: o mesmo comando de T1, 0 falhas em 20 nas condições em que falhava.
- [x] T3: rodar `npm run lint && npm run typecheck && npm run e2e` e deixar os números da medição no resumo da construção, para a descrição do PR (`Closes #386`) e o comentário na issue, citando qualquer aumento de espera — cobre CA4 (e CA3 se não houve falha) — prova: `cd frontend && npm run lint && npm run typecheck && npm run e2e`.

## 6. Decisões

- D1: duas condições, sem carga e com carga do `verify` do back-end, porque a falha original só apareceu com carga. Alternativa descartada: medir só sem carga, que repetiria o 0/20 da #320 sem reproduzir o caso.
- D2: sem falha em nenhuma condição, fecha-se com os números, sem código, como a #320. Alternativa descartada: reforçar esperas "por precaução".
- D3: a correção, havendo falha, é esperar estado final ou cortar trabalho repetido (axe injetado uma vez); `test.setTimeout` maior só com medição mostrando que o fluxo legítimo passa de 30 s, registrada no PR (a issue permite, com registro). Nunca `retries`.
- Suposição: a falha foi de tempo total do teste sob carga (timeout de 30 s em 1 variação). Se não reproduzir, vale D2 e o dono reabre com o log.

## Construção

- T1 — arquivos: nenhum — prova: `cd frontend && npx playwright test e2e/comprovantes.spec.ts --repeat-each=20 --workers=1 --retries=0` → sem carga 0 falhas em 40 execuções (20 por tema, 10,8 min, ~16 s por execução); com carga (o `verify` do back-end caiu em 49 s no `failsafe` com "TestEngine junit-jupiter failed to discover tests", então não serviu de carga; usada carga equivalente: 12 laços de CPU em `node`, um por núcleo) 0 falhas em 40 (16,6 min, ~25 s por execução, perto dos 30 s). Primeira tentativa com carga foi cortada pelo meu `timeout` (código 143), sem falha de teste. Sem carga e em `--workers=1` não reproduziu; reproduziu em `npm run e2e` (workers em paralelo): 1 falha, "Test timeout of 30000ms exceeded", comprovantes (light), 80 passaram.
- T2 — arquivos: `frontend/e2e/comprovantes.spec.ts` (`chooseLink`) — causa pelo trace: `chooseLink` esperava o diálogo por `waitFor({ timeout: 3000 })` e, como após o login só o primeiro diálogo aparece, as outras 5 chamadas por teste gastavam 3 s cada (~15 s fixos dos 30 s, visto nas chamadas de ~3040 ms do trace). Correção: `modal.or(swap).first().waitFor()` e clica em "Trocar" só se o diálogo não estiver visível; sem retry, sem timeout maior. Prova: mesmo comando → sem carga 40/40 (3,3 min); com carga de CPU 40/40 (8,8 min).
- T3 — arquivos: nenhum além do T2 — prova: `cd frontend && npm run lint && npm run typecheck && npm run e2e` → verde, 81 passaram (3,3 min), contra 1 falha antes da correção.
Regressão: só o spec de e2e mudou; lint, typecheck e `e2e` completos verdes. Back-end e `test:cov` não rodaram (sem mudança neles).
Desvios: carga do `verify` do back-end substituída por laços de CPU (o `verify` falhou por conta própria, ver T1); a falha original foi reproduzida só no `npm run e2e` completo, não nas repetições isoladas. Sem aumento de espera nem de timeout; a espera por tempo de 3 s sai.
Não é do item: `verify` do back-end falha em `failsafe` ("failed to discover tests") nesta máquina, sem relação com o item; vale uma investigação à parte.

## Verificação

### Rodada 1 — OK — 2026-10-27, estado acdbbc305cc99bb840bd015533ce0fa6a2d4bc06

| Critério | Status | Evidência |
|---|---|---|
| CA1 | PASSOU | medição registrada em T1 (0/40 sem carga, 0/40 com carga de CPU, 1 falha no `npm run e2e` completo); passo lento achado: espera de 3 s em `chooseLink`. Carga trocada por CPU, com o motivo registrado |
| CA2 | PASSOU | diff: espera pelo estado (`modal.or(swap)`), sem retry nem timeout maior; repetição por mim: `--repeat-each=3 --workers=1 --retries=0` 6/6 |
| CA3 | NÃO CONFERIDO (não se aplica) | houve falha medida, vale CA2 |
| CA4 | PASSOU | `npm run lint`, `typecheck` sem erro; `npm run e2e` 81 passaram (2,9 min) |

Comandos: lint, typecheck, e2e do spec (3x por tema, 6 passaram), `npm run e2e` (81 passaram)
Achados:
- sugestão, código, `chooseLink` — `modal.isVisible()` logo após o `or().first().waitFor()` é instantâneo; se o diálogo abrir entre os dois passos, o clique em "Trocar" falha. Janela pequena, sem falha vista

## Entrega

O que muda: só `frontend/e2e/comprovantes.spec.ts`, no `chooseLink`. A falha vinha de a espera do diálogo ser fixa em 3 s: após o login só o primeiro diálogo aparece, então as outras 5 chamadas por teste gastavam 3 s cada (cerca de 15 s dos 30 s do teste). Agora o teste espera o estado (`modal.or(swap).first().waitFor()`) e clica em "Trocar" só se o diálogo não estiver visível. Sem `retry`, sem mudança de timeout, sem espera maior.

Medição (CA1), antes da correção: `--repeat-each=20 --workers=1 --retries=0`, sem carga 0/40 e com carga de CPU 0/40; `npm run e2e` completo 1 falha ("Test timeout of 30000ms exceeded", comprovantes no tema claro). Depois: sem carga 40/40, com carga 40/40, e `npm run e2e` 81 passaram.

Verificado: `npm run lint`, `npm run typecheck` e `npm run e2e` verdes. Regressão desta entrega: a da verificação (base `dev` sem commits novos, então não rodou de novo).

Limitações: a falha reproduziu só no `npm run e2e` completo, não nas repetições isoladas, então a carga usada na medição (laços de CPU, no lugar do `verify` do back-end, que caiu em `failsafe` com "failed to discover tests") não é a mesma do caso original. Achado sem ação: `modal.isVisible()` logo após o `or().first().waitFor()` é instantâneo; se o diálogo abrir entre os dois passos, o clique em "Trocar" falha. Janela pequena, sem falha vista. O `verify` do back-end falhando em `failsafe` nesta máquina não é do item.
