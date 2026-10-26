# 368 — Testes e2e poderem sobrescrever respostas do MSW por teste (worker.use)

Origem: issue #368 (front-end, Sprint 10) e os CA2, CA3 e CA4 da #362 (`specs/itens/362-barra-360px.md`), aceitos lá como limitação. Branch: `item/368-e2e-msw-worker-use`.

## 1. Objetivo

Dar ao e2e um jeito de trocar a resposta de um endpoint do MSW só durante um teste, sem mexer nos handlers padrão e sem vazar para o teste seguinte, e usá-lo para provar a 360 px a barra superior da gestão com o sino em `99+`, com nome de unidade longo e com erro ao carregar as unidades. Para quem escreve testes e2e.

Fora do escopo: mudar handlers padrão, o sino, o seletor de unidades ou o layout (se a prova revelar estouro, vira achado, não parte deste item); sobrescrever em `npm run dev` fora de teste; testes Vitest (já usam `server.use`).

## 2. Requisitos

- R1: o teste e2e sobrescreve a resposta (método, caminho do contrato, status e corpo) de um endpoint só naquele teste.
- R2: a sobrescrita não altera `src/mocks/handlers.ts` e é desfeita ao fim do teste (`resetHandlers`); o teste seguinte vê o padrão.
- R3: a sobrescrita pode ser registrada antes de a página carregar (vale para a carga inicial) ou com a página já aberta.
- R4: o gancho no navegador existe só em desenvolvimento com MSW ligado; a build de produção não o contém.
- R5: existem e passam a 360 px os casos: sino `99+`, nome de unidade longo e erro ao carregar as unidades, todos sem rolagem horizontal (CA2 a CA4 da #362).
- R6: o uso está documentado em `frontend/README.md`.
- R7: `npm run e2e` verde em 10 execuções seguidas.

## 3. Critérios de aceite

- CA1 (R1, R2): dado um teste que sobrescreve `GET /notifications/unread-count`, quando a tela lê o contador, então vê o valor sobrescrito; e o teste seguinte, no mesmo arquivo, vê o valor padrão do handler.
- CA2 (R3): dado uma sobrescrita registrada antes de `page.goto`, quando a página carrega, então a primeira resposta do endpoint já é a sobrescrita.
- CA3 (R4): dado `npm run build`, então `dist/` não contém o gancho (`__mswOverride`).
- CA4 (R5): dado o sino com contagem 100 ou mais, quando a barra é medida a 360 px, então mostra `99+` e `scrollWidth <= innerWidth`.
- CA5 (R5): dado `GET /units/reachable` com duas unidades de nome de 80 caracteres, quando medido a 360 px, então o seletor mostra as unidades e `scrollWidth <= innerWidth`.
- CA6 (R5): dado `GET /units/reachable` com 500, quando medido a 360 px, então o alerta de erro das unidades aparece e `scrollWidth <= innerWidth`.
- CA7 (R6): `frontend/README.md` descreve o uso, com exemplo.
- CA8 (R7): `npm run e2e` passa 10 vezes seguidas.

## 4. Plano técnico

- Dados, API, interface, segurança: não mudam (o contrato OpenAPI não muda; a sobrescrita só vale no MSW do navegador em dev).
- Arquivos (vistos): `frontend/src/mocks/browser.ts` (hoje só `setupWorker(...handlers)`), `frontend/src/main.tsx` (`start()` chama `worker.start` dentro do `if` de DEV), `frontend/playwright.config.ts` (sobe `npm run dev`; o estado do mock vive na página), `frontend/e2e/matricula.spec.ts` (modelo de `open`, `noOverflow` e login), `frontend/src/features/notifications/useNotifications.ts` (lê `/notifications/unread-count`), `frontend/src/features/units/UnitScopeProvider.tsx` (lê `/units/reachable` ao montar), `frontend/README.md` (seção "Ponta a ponta").
- A criar: `frontend/e2e/support/msw.ts` (fixture) e `frontend/e2e/barra-360px.spec.ts` (casos da #362 e prova de isolamento).
- Desenho:
  - `browser.ts` exporta `installE2eHook(worker)`: antes de tudo aplica a fila `window.__mswQueue` (preenchida por `addInitScript`) e define `window.__mswOverride({ method, path, status, body })`, que chama `worker.use(http[method](`/api/v1${path}`, () => HttpResponse.json(body, { status })))`, e `window.__mswReset()`, que chama `worker.resetHandlers()`.
  - `main.tsx` chama `installE2eHook(worker)` logo após `worker.start`, dentro do `if` de DEV existente (já exclui produção; R4).
  - `e2e/support/msw.ts` estende `test` com a fixture `msw.override(spec)`: `page.addInitScript` empurra a spec em `window.__mswQueue` (vale na carga) e, se a página já carregou, chama `__mswOverride` por `page.evaluate`. No fim do teste chama `__mswReset` (try/catch, a página pode ter fechado). Exporta `test` e `expect`.
  - Casos de unidades e sino: registrar a sobrescrita antes do `page.goto("/entrar")` e só então fazer login com `ana@exemplo.com`, porque o login e o estado do mock vivem na página e se perdem em recarga; a navegação interna usa `history.pushState`, como em `matricula.spec.ts`.
- Reuso: `open` e `noOverflow` de `matricula.spec.ts` (copiar as duas linhas); handlers `GET /notifications/unread-count`, `GET /notifications` e `GET /units/reachable` já existem em `handlers.ts`.
- Riscos: se a barra estourar em algum dos três casos, é bug real deixado pela #362: registrar como achado e corrigir só o mínimo no layout, dizendo na construção. `browser.ts` não é importado pelos testes de unidade: conferir que `test:cov` segue em 80% (se entrar no cálculo, cobrir `installE2eHook` com um teste simples).

## 5. Tarefas

- [x] T1: criar `installE2eHook` em `browser.ts` e chamá-lo em `main.tsx` (DEV) — cobre CA3 — prova: `cd frontend && npm run typecheck && npm run build && grep -r "__mswOverride" dist` (sem resultado)
- [x] T2: criar `e2e/support/msw.ts` (fixture `msw` com `override` e reset ao fim) — cobre CA1, CA2 (infraestrutura dos testes da T3) — prova: `cd frontend && npm run lint && npm run typecheck`
- [x] T3: em `e2e/barra-360px.spec.ts`, par de testes de isolamento (um sobrescreve o contador, o seguinte vê o padrão) e um teste com a sobrescrita registrada antes do `goto` — cobre CA1, CA2 — prova: `cd frontend && npx playwright test e2e/barra-360px.spec.ts -g "isolamento|antes da carga"`
- [x] T4: os três casos a 360 px (sino `99+`, nome longo, erro das unidades) com medição de rolagem horizontal — cobre CA4, CA5, CA6 — prova: `cd frontend && npx playwright test e2e/barra-360px.spec.ts`
- [x] T5: documentar em `frontend/README.md` (seção "Ponta a ponta") com exemplo e a regra de registrar antes do login — cobre CA7 — prova: conferência de leitura do trecho
- [x] T6: `npm run e2e` 10 vezes seguidas e regressão do front; registrar na construção — cobre CA8 — prova: `cd frontend && npm run e2e` (x10), depois `npm run lint && npm run typecheck && npm run test:cov`

## 6. Decisões

- D1: gancho `window.__mswOverride` no navegador, chamado pelo Playwright, em vez de expor `http`/`HttpResponse` ao teste: funções não atravessam o `page.evaluate`, e a spec `{ método, caminho, status, corpo }` cobre os casos pedidos. Alternativa descartada: `page.route`, que não usa o MSW nem o `worker.use` que a issue pede.
- D2: o registro por `addInitScript` (fila lida na partida) existe porque o login e o estado do mock se perdem em recarga; sobrescrever antes do `goto` é o único jeito de provar o erro de `/units/reachable`, que é lido ao montar.
- D3: o reset ao fim do teste é feito pela fixture; como cada teste usa página nova, o isolamento já é natural, e o reset explícito atende ao `resetHandlers` da issue.
- D4: os três casos ficam em arquivo novo, sem `@critical` (rodam no PR para `main`), pois a #362 já protege a carga no `@critical`. Se o dono quiser os três em todo PR, basta a marca.
- Suposição: o sino lê `GET /notifications/unread-count` (visto em `useNotifications.ts`) e o seletor lê `GET /units/reachable` (visto em `UnitScopeProvider.tsx`); se o corpo esperado diferir do que o handler devolve, a sobrescrita copia o formato do handler.

## Construção

- T1 — arquivos: `frontend/src/mocks/browser.ts`, `frontend/src/main.tsx` — prova: `npm run typecheck && npm run build && grep -r __mswOverride dist` → verde, 0 ocorrências
- T2 — arquivos: `frontend/e2e/support/msw.ts` — prova: `npm run lint && npm run typecheck` → verde
- T3 — arquivos: `frontend/e2e/barra-360px.spec.ts` — prova: `npx playwright test e2e/barra-360px.spec.ts` → 6 passam (isolamento, antes da carga)
- T4 — mesmo arquivo — prova: idem → sino 99+, nome longo e erro das unidades sem rolagem horizontal; nenhum estouro, layout intocado
- T5 — arquivos: `frontend/README.md` (seção "Ponta a ponta", exemplo e regra do login) — prova: leitura do trecho
- T6 — prova: `npm run e2e` x10 → 36 passaram em todas; `barra-360px.spec.ts` x10 → 10 verdes
Regressão (front): `npm run lint && npm run typecheck && npm run test:cov` → verde, 1176 testes, 93,5% de linhas. Back-end não alterado, não rodado.
Desvios: (1) a fixture espera `window.__mswOverride` antes de aplicar na página já aberta (o MSW sobe de forma assíncrona); (2) o parâmetro da fixture se chama `provide`, pois `use` dispara a regra de hooks do ESLint; (3) conforme D4 os casos não têm `@critical`, então os 10 `npm run e2e` não os incluem; foram rodados à parte 10 vezes. `browser.ts` já está fora da cobertura.

## Verificação

### Rodada 1 — OK — 2026-10-26, estado 332f78c1ea049c57358cf1ccd8a04d6e1ee22314

| Critério | Status | Evidência |
|---|---|---|
| CA1 | PASSOU | `barra-360px.spec.ts` "isolamento" (4321 no primeiro, valor diferente no seguinte): 6 passam, 10 execuções |
| CA2 | PASSOU | teste "antes da carga": primeira leitura já é 777 |
| CA3 | PASSOU | `npm run build` verde; `grep -rl __mswOverride dist` sem resultado |
| CA4 | PASSOU | sino `99+` a 360 px, `scrollWidth <= innerWidth` |
| CA5 | PASSOU | duas unidades de 80 caracteres, seletor visível, sem rolagem horizontal |
| CA6 | PASSOU | 500 em `/units/reachable`, alerta visível, sem rolagem horizontal |
| CA7 | PASSOU | `frontend/README.md`, seção "Ponta a ponta", com exemplo e regra do login |
| CA8 | PASSOU | `npm run e2e` x10: 36 passaram em todas; `barra-360px.spec.ts` (sem `@critical`, fora do `e2e`) x10: 6 passaram em todas |

Comandos: `npm run lint && npm run typecheck && npm run build` verde; `npm run e2e` x10 verde; `npx playwright test e2e/barra-360px.spec.ts` x11 verde; `npm run test:cov` verde (1176 testes, 93,51% de linhas). Back-end não tocado, não rodado.
Achados:
- sugestão, código, `frontend/e2e/barra-360px.spec.ts` — o teste "sino 99+" não confere o texto `99+` fora do nome acessível do botão; aceitável, só observação.

## Entrega

O que muda: `window.__mswOverride` (gancho só de teste, fora do build de produção) para trocar a resposta de um endpoint do MSW por teste; fixture `msw` em `frontend/e2e/support/msw.ts` com registro antes da carga e reset ao fim do teste; `frontend/e2e/barra-360px.spec.ts` prova a barra da gestão a 360 px (sino em `99+`, unidade com nome longo, erro ao carregar unidades) sem rolagem horizontal; seção "Ponta a ponta" em `frontend/README.md`.

Verificação: rodada 1 OK (estado `332f78c1ea049c57358cf1ccd8a04d6e1ee22314`). `npm run e2e` x10 com 36 passando em todas; `barra-360px.spec.ts` x10 verde; lint, typecheck, build e `test:cov` (1176 testes, 93,51% de linhas) verdes. Back-end não tocado.

Decisões que o revisor precisa saber: os três casos da barra ficam fora do `@critical` (D4), então só rodam no PR para `main`; a sobrescrita registra a resposta antes do login, porque o estado do mock se perde em recarga (D2).

Limitações: o `99+` é conferido pelo nome acessível do botão, não pelo texto visível (achado de sugestão da verificação, aceito).

Base: `dev` não avançou desde a branch; a regressão da verificação vale.
