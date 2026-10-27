# 385 — Teste LessonPanelPage instável no test:cov: medir e estabilizar

Origem: issue #385 (front-end, Sprint 10; trava a regressão do front antes da #95), mesmo formato da #318 e da #320. Branch: `item/385-lesson-panel-instavel`.

## 1. Objetivo

Medir quantas vezes `frontend/src/features/agenda/LessonPanelPage.test.tsx` falha em repetição e dentro do `test:cov` completo e, havendo falha, corrigir a causa (espera, tempo limite ou estado compartilhado) sem mascarar. Sem falha medida, nada no código muda e a issue fecha com os números.

Fora do escopo: outros testes instáveis (#386, #388), mudar o comportamento do painel de aula, refatorar a suíte.

## 2. Requisitos

- R1: a taxa de falha é medida antes de qualquer correção, em três condições: o arquivo isolado repetido, o arquivo na suíte `npm test` e o `test:cov` completo; cada condição registra falhas em execuções, e o nome do teste que falhou.
- R2: havendo falha, a causa é achada e corrigida na raiz (D2); nenhum `retry`, nenhum aumento de `testTimeout` ou de `waitFor` sem registro na descrição do PR.
- R3: sem falha em nenhuma condição (D1), o código não muda e a issue é fechada com o resultado.
- R4: depois da correção, o arquivo passa em todas as execuções nas condições em que falhou e `npm run test:cov` segue verde, com os pisos de cobertura.

## 3. Critérios de aceite

- CA1 (R1): dado o arquivo, quando rodado 20 vezes isolado, 20 vezes com `--maxWorkers=2` e o `test:cov` completo repetido ao menos 3 vezes, então a issue e o PR trazem falhas/execuções de cada condição e o nome do teste que falhou.
- CA2 (R2, R4): dada uma condição com falha, quando a causa é corrigida, então 0 falhas na mesma quantidade de execuções, sem `retry`, sem mudança em `testTimeout` e sem espera maior só para esconder.
- CA3 (R3): dado 0 falhas em todas as condições, quando a medição termina, então o código fica intacto e a issue recebe os números e fecha.
- CA4 (R4): dado o estado final, quando roda `npm run lint && npm run typecheck && npm run test:cov`, então passam, com 80% geral e 100% nas pastas de regra.

## 4. Plano técnico

- Dados, API, segurança, interface: não mudam (só teste, salvo causa no componente).
- Medição: o Vitest instalado não tem `--repeat` (visto na #318); repetir por laço de shell, uma execução por vez: `for i in $(seq 20); do npx vitest run src/features/agenda/LessonPanelPage.test.tsx --maxWorkers=2 || echo FALHOU $i; done` (de dentro de `frontend/`). Suíte inteira: `npm run test:cov`, 3 vezes ou mais, registrando o teste que falhou.
- Estado atual visto: `frontend/vite.config.ts` tem `testTimeout: 20000`; o CA10 de carregando já usa `gate()` (`@/test/gate`) em vez de depender de tempo; os testes montam `<App />` inteiro com `setup()`, que muda `window.innerWidth` e `server.use` (handlers acumulam por teste, resetados pelo `src/test/setup.ts`, a conferir).
- Suspeitas para a investigação (não são fato): (1) `window.innerWidth = opts.width ?? 1280` e `setDevicePrefersDark` mudam estado global; o `setup()` reajusta a largura, mas o tema escuro do axe pode vazar para o teste seguinte se não for restaurado; (2) testes que abrem o diálogo "Editar aula" esperam `findByRole("option", { name: "João Pereira" })`, que depende da rota de professores respondida sob carga; (3) `findBy*` com 1000 ms padrão (`asyncUtilTimeout`) em renderização do `<App />` inteiro pode estourar sob a carga do `test:cov` (cobertura v8 deixa o jsdom mais lento) — o `testTimeout` de 20 s não vale para `findBy`; (4) uma requisição pendente de um teste respondendo no teste seguinte.
- Arquivos: `frontend/src/features/agenda/LessonPanelPage.test.tsx` (visto, 493 linhas; `setup`, `ready`, os `it` de CA9 a CA12) e, só se a causa estiver lá, `frontend/src/test/setup.ts`, `frontend/src/test/gate.ts` ou o componente do painel. Nenhum a criar.
- Reuso: `gate`, `axeViolations`, `setDevicePrefersDark`, `server` do MSW.
- Riscos: falha rara que não reproduz isolada e só aparece sob carga do `test:cov` completo; por isso as três condições. Corrigir por timeout mascararia (proibido pela issue). Não rodar a suíte completa em laço longo (carga da máquina).

## 5. Tarefas

- [x] T1: medir as três condições do CA1 antes de mexer em código e registrar falhas/execuções e o teste que falhou — cobre CA1 — prova: laço de 20 de `npx vitest run src/features/agenda/LessonPanelPage.test.tsx --maxWorkers=2` e `npm run test:cov` 3x (de dentro de `frontend/`).
- [x] T2: se houve falha, achar a causa (espera, tempo ou estado compartilhado) e corrigir no teste ou na utilidade apontada; se não houve, não alterar código e seguir para T3 — cobre CA2 ou CA3 — prova: o mesmo laço, 0 falhas nas condições em que falhava.
- [x] T3: rodar `npm run lint && npm run typecheck && npm run test:cov` e deixar os números da medição no resumo da construção (e no PR, citando qualquer aumento de espera) — cobre CA4 — prova: `npm run lint && npm run typecheck && npm run test:cov`.

## 6. Decisões

- D1: sem falha em nenhuma condição, fecha-se com o resultado, como a issue manda, sem código. Motivo: sem falha medida não há o que corrigir. Alternativa descartada: reforçar esperas "por precaução".
- D2: a correção, havendo falha, é na espera por estado final, no isolamento do estado global (largura, tema, handlers) ou numa causa do componente; nunca `retry` ou timeout maior. Se só um aumento de espera resolver, registra-se a medição e o motivo na descrição do PR (a issue permite, com registro).
- D3: o "em repetição" é por laço de shell porque o Vitest instalado não tem `--repeat`.
- Suposição: a falha foi vista no `test:cov` completo sob carga (1240 de 1241). Se não reproduzir em nenhuma condição, vale D1 e o dono reabre com o log da falha.

## Construção

- T1 — arquivos: nenhum — prova: laço de 20 de `npx vitest run src/features/agenda/LessonPanelPage.test.tsx --maxWorkers=2` → 0/20 falhas; mesmo arquivo sem `--maxWorkers`: 1/20 falhou (e mais 3/40 numa segunda série), sempre `CA10: carregando, erro com Tentar de novo, sem permissão e aula não encontrada` (`findByText("Carregando...")` achava o texto e o `toBeInTheDocument` falhava em ~40 ms); `npm run test:cov` 3x → 0/3 falhas (1367 testes). A condição `npm test` não foi medida à parte (o `test:cov` roda a mesma suíte).
- T2 — causa: "Carregando..." aparece também na sessão e no carregamento da rota; o `findByText` resolvia no primeiro e o elemento saía antes da asserção. Arquivos: `frontend/src/features/agenda/LessonPanelPage.test.tsx` (segundo `gate()` `requested`, liberado pelo handler da aula; o teste espera a requisição e só então afirma "Carregando..."). Sem retry, sem mudança de timeout. Prova: laço de 40 execuções do arquivo → 0/40 falhas (antes 3/40).
- T3 — prova: `npm run lint && npm run typecheck && npm run test:cov` → verdes, 1367 testes passaram, pisos de cobertura atendidos.
Regressão: a do T3 (lint, typecheck, test:cov) → verde. Back-end, build e e2e não rodaram (mudança só em teste do front).
Desvios: a falha apareceu isolada, sem carga, e não no `test:cov`; o plano esperava o contrário.

## Verificação

### Rodada 1 — OK — 2026-10-27, estado 7fd24fbf8f61d5b43610e427a9913cb660fe788d

| Critério | Status | Evidência |
|---|---|---|
| CA1 | PASSOU | medição registrada em "Construção" (isolado 0/20 com `--maxWorkers=2`, 1/20 e 3/40 sem; `test:cov` 0/3), com o nome do teste CA10; nesta rodada, 0/20 com `--maxWorkers=2` e 0/20 sem. Issue/PR ainda a receber os números (fase 5) |
| CA2 | PASSOU | `requested` gate no CA10, sem retry nem mudança de timeout (diff só toca o teste); 0/40 na construção, 0/20 aqui |
| CA3 | PASSOU (não se aplica) | houve falha medida, vale CA2 |
| CA4 | PASSOU | lint e typecheck exit 0; `test:cov` exit 0, 159 arquivos, 1367 testes |

Comandos: `npm run lint && npm run typecheck` ok; laço 20x `vitest run LessonPanelPage.test.tsx --maxWorkers=2` 0 falhas; laço 20x sem `--maxWorkers` 0 falhas; `npm run test:cov` verde.
Achados:
- sugestão, código, LessonPanelPage.test.tsx CA10 — `requested.release()` roda também em recarregamentos; inofensivo (gate idempotente presumido).

## Entrega

O que muda: só o teste `frontend/src/features/agenda/LessonPanelPage.test.tsx`, no CA10 (carregando, erro, sem permissão, aula não encontrada). A falha vinha de o `findByText("Carregando...")` achar o texto da sessão ou do carregamento da rota, que sai antes da asserção. Agora o teste espera a requisição da aula (gate `requested`) e só então afirma "Carregando...". Sem código de produto, sem `retry`, sem mudança de `testTimeout` nem de espera.

Medição (CA1), antes da correção e depois: isolado com `--maxWorkers=2`, 0/20 na construção e 0/20 na verificação; isolado sem `--maxWorkers`, 1/20 e 3/40 em duas séries antes, 0/40 e 0/20 depois; `test:cov` completo, 0/3 antes e verde na verificação (159 arquivos, 1367 testes). O teste que falhou, em todas as falhas, foi o CA10.

Verificado: `npm run lint`, `npm run typecheck` e `npm run test:cov` verdes, com os pisos de cobertura. Regressão desta entrega: a da verificação (base `dev` sem commits novos, então não rodou de novo).

Limitações: a condição `npm test` não foi medida à parte (o `test:cov` roda a mesma suíte). Back-end, build e e2e não rodaram (mudança só em teste do front). A falha reproduzia sem carga; sob carga do `test:cov` não apareceu nas medições. Achado sem ação: `requested.release()` também roda em recarregamentos; inofensivo com o gate idempotente.
