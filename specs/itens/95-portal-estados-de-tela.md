# 95 — RF-POR-08 · Portal: estados de tela

Origem: issue #95 (Sprint 10 · Portal do aluno); `docs/especificacao/14-portal-do-aluno.md` (RF-POR-08); padrão de tela de `frontend/src/components/states` (RNF-25). Branch: `item/95-portal-estados-de-tela`. Parte do que os itens #88 a #94 deixaram: cada página do portal já usa `LoadingState`, `ErrorState`, `ForbiddenState` e `EmptyState`; este item fecha o que ficou sem estado e prova o conjunto com um teste só. Os canvas citados na issue (Estado: carregando, vazio, erro) não são lidos pelo agente; valem os componentes de `states.tsx`, que já os implementam.

## 1. Objetivo

Garantir que toda tela do portal do aluno e do responsável, e todo bloco dela que busca dados por conta própria, mostre carregando, vazio quando couber, erro com "Tentar de novo" e sem permissão, e provar isso com um teste que percorre as rotas do portal. Hoje dois pontos falham em silêncio: o resumo de pedidos de Minhas informações e o cartão de confirmação de modalidade.

Fora do escopo: back-end, OpenAPI, MSW e migração (nenhuma rota nova, nenhum dado novo); telas de gestão; mudar o texto ou o visual dos estados que já existem.

## 2. Requisitos

- R1 (RF-POR-08): cada rota do portal (`/portal`, `/portal/agenda`, `/portal/financeiro`, `/portal/avisos`, `/portal/carteirinha`, `/portal/minhas-informacoes`, `/portal/pedidos`, `/portal/termo`) mostra "carregando" enquanto busca, "erro" com o botão "Tentar de novo" quando a busca falha (e o botão refaz a busca) e "sem permissão" quando o servidor responde 403, sem renderizar conteúdo protegido.
- R2 (RF-POR-08): cada rota cuja lista pode ser vazia mostra o vazio com título e texto próprios, e não uma tela em branco.
- R3 (RF-POR-08, D1): `PortalRequestsSummary` (Minhas informações) mostra carregando, erro com "Tentar de novo" e sem permissão, no lugar de ficar sem conteúdo enquanto busca ou quando falha.
- R4 (RF-POR-08, D2): `ModalityConfirmations` mostra erro com "Tentar de novo" quando a busca de pendências falha; sem pendência continua sem cartão (D2) e durante a busca não mostra nada.
- R5 (RF-POR-08): os estados novos funcionam a partir de 360 px, passam no axe nos dois temas e têm todo texto em `pt-BR.json`.
- R6 (RF-POR-08, RNF-25): um teste percorre todas as rotas de R1 e falha se uma delas perder um dos estados.

## 3. Critérios de aceite

- CA1 (R1): dado cada rota do portal com a busca principal pendente, quando a tela abre, então aparece o estado "carregando" (`role="status"`).
- CA2 (R1): dado cada rota com a busca principal respondendo 500, quando a tela abre, então aparece o erro com "Tentar de novo"; quando o servidor passa a responder bem e o botão é clicado, então o conteúdo aparece.
- CA3 (R1): dado cada rota com a busca principal respondendo 403, quando a tela abre, então aparece "sem permissão" e nenhum dado da resposta.
- CA4 (R2): dado `/portal/agenda`, `/portal/avisos`, `/portal/pedidos`, `/portal/carteirinha` e `/portal/minhas-informacoes` com resposta sem itens (sem aula, sem aviso, sem aluno ligado, sem modalidade), quando a tela abre, então aparece o vazio da própria tela; dado `/portal/financeiro`, as seções sem cobrança mostram o vazio de cada seção.
- CA5 (R3): dado Minhas informações com os dados principais bons e a rota de pedidos pendente, então o cartão "Pedidos à academia" mostra "carregando"; com 500, mostra o erro com "Tentar de novo" que refaz só essa busca; com 403, "sem permissão"; com lista vazia, mostra o texto `portalInfo.requests.empty`.
- CA6 (R4): dado qualquer tela do portal e a rota `/portal/modality-confirmations` com 500, então aparece o erro com "Tentar de novo" e o botão refaz a busca; com lista vazia, nada aparece no lugar do cartão; com pendência, o cartão aparece como hoje.
- CA7 (R5): dado o erro e o carregando do CA5 e do CA6 em 360 px e nos dois temas, então não há rolagem horizontal e o axe não aponta violação.
- CA8 (R6): dado o teste de R6, quando uma rota deixa de renderizar carregando, erro com o botão, sem permissão ou vazio (onde couber), então o teste reprova.

## 4. Plano técnico

- Dados: não muda.
- API: não muda. Nenhuma rota nova; `docs/openapi.yaml` e handlers MSW ficam como estão.
- Interface: `PortalRequestsSummary` e `ModalityConfirmations` ganham os estados que faltam, com os componentes de `@/components/states`. As demais telas já têm os estados (conferido em `PortalHomePage`, `PortalAgendaPage`, `PortalFinancePage`, `PortalAnnouncementsPage`, `PortalCardPage`, `PortalInfoPage`, `PortalRequestsPage`, `PortalTermPage` e `PortalLayout`) e só ganham o teste de conjunto. Se o teste achar outra lacuna, ela é corrigida na T3.
- Segurança: não muda; o 403 continua vindo do servidor e o componente só exibe, sem receber conteúdo protegido.
- Arquivos:
  - `frontend/src/features/portal/PortalSettings.tsx`: `PortalRequestsSummary` passa a tratar `mine.status` (`loading`, `forbidden`, `error` com `reload`); a lista e o vazio ficam para `ready`.
  - `frontend/src/features/portal/ModalityConfirmations.tsx`: a busca guarda a falha de carga e um `tick` para refazer; falha mostra `ErrorState` dentro do `Card`; hoje `() => undefined` engole o erro.
  - `frontend/src/i18n/pt-BR.json`: sem chave nova; reutiliza `states.*` e `errors.generic.*` dos componentes de estado e as chaves existentes `portalInfo.requests.*` e `portalConfirm.*`.
  - A criar: `frontend/src/features/portal/PortalStates.test.tsx` (teste de conjunto de R6), no padrão de `PortalCardPage.test.tsx` (`App` dentro de `MemoryRouter`, `SessionProvider`, `ThemeProvider`, `server.use`, `axeViolations`, `setDevicePrefersDark`).
  - Testes existentes a ajustar: `PortalInfoPage.test.tsx` (CA5) e `ModalityConfirmations.test.tsx` (CA6).
- Reuso: `LoadingState`, `ErrorState`, `ForbiddenState`, `EmptyState` (`components/states`); `useResource` (`features/students/useStudentRecord.ts`, já devolve `status` e `reload`); `src/test/portalShell.ts`, `src/test/axe.ts`, `src/test/device.ts`; `src/test/screen-states.test.tsx` como referência do padrão de teste de estados.
- Riscos: `ModalityConfirmations` é montado em `App.tsx` e aparece em toda tela autenticada; um erro visível ali não pode quebrar o login nem as telas de gestão. A T2 confere que os testes das telas existentes continuam passando; se a busca falhar fora do portal, o bloco só mostra o erro com o botão, sem tirar o resto da tela.

## 5. Tarefas

- [x] T1: `PortalRequestsSummary` com carregando, erro com "Tentar de novo" e sem permissão; ajustar `PortalInfoPage.test.tsx` — cobre CA5, CA7 — prova: `npm test -- PortalInfoPage` em `frontend/`
- [x] T2: `ModalityConfirmations` com erro e "Tentar de novo"; ajustar `ModalityConfirmations.test.tsx` — cobre CA6, CA7 — prova: `npm test -- ModalityConfirmations`
- [x] T3: criar `PortalStates.test.tsx` que percorre as oito rotas do portal para carregando, erro com retry, sem permissão e vazio; corrigir qualquer lacuna que ele revelar — cobre CA1, CA2, CA3, CA4, CA8 — prova: `npm test -- PortalStates`
- [ ] T4: regressão do front (`lint`, `typecheck`, `test:cov` com 80%) — não cobre critério: confere que nada do portal nem das telas de gestão quebrou — prova: `npm run lint && npm run typecheck && npm run test:cov`

## 6. Decisões

- D1: o resumo de pedidos de Minhas informações ganha os estados como bloco, sem derrubar a tela inteira. Motivo: a busca é própria do bloco (`useMyRequests`) e o resto da aba já carregou. Alternativa descartada: levar o estado para `usePortalInfo` e juntar tudo num pedido só, o que mexeria em back-end e contrato.
- D2: `ModalityConfirmations` sem pendência continua sem cartão, e só a falha da busca vira erro. Motivo: o cartão é um aviso opcional que aparece em toda tela; um vazio visível em todas seria ruído e contraria o desenho do #86. Alternativa descartada: mostrar "Nada a confirmar".
- D3: item só de front-end e de teste. Motivo: a regra pede estados de tela; os passos de migração, rota, OpenAPI, MSW e auditoria da issue não se aplicam porque nenhum dado ou rota muda. "Recurso novo nasce desligado" e "Notas de versão" também não se aplicam: não há recurso novo e a correção não muda o uso normal.
- D4: os canvas citados na issue não são consultados (mesma decisão D9 do item 94); a conformidade é com os componentes de estado já existentes.
- Suposição: as rotas de R1 já têm os quatro estados, como o código mostra; se o teste da T3 achar outra falha, ela entra na T3 sem mudar requisito.

## Construção

- T1 — arquivos: `frontend/src/features/portal/PortalSettings.tsx`, `PortalInfoPage.test.tsx` (carregando, 403, erro com retry e axe nos dois temas) — prova: `npx vitest run PortalInfoPage` → 21 passaram
- T2 — arquivos: `frontend/src/features/portal/ModalityConfirmations.tsx`, `ModalityConfirmations.test.tsx` (erro, axe, retry) — prova: `npx vitest run PortalInfoPage ModalityConfirmations` → 22 passaram
- T3 — arquivos: `frontend/src/features/portal/PortalStates.test.tsx` (8 rotas: carregando, erro com retry, 403 sem dado, vazio) — prova: `npx vitest run PortalStates` → 31 passaram, 4 rodadas seguidas estáveis. O teste não achou lacuna nas telas.
- T4 — NÃO marcada. `npm run lint` e `npm run typecheck` passam. `npm run test:cov`: 1ª rodada 13 falhas e 2ª rodada 5 falhas, todas `Test timed out in 20000ms` (agenda, auth, charges, classes, organization, teachers, units, students) sob a carga da suíte inteira; `ChargePanel`, `OverduePage` e `UnitSettingsPage` passam isoladas (59 testes). Limite de 80% de cobertura não foi lido, porque a rodada terminou vermelha. Falha de tempo limite instável, não do item; precisa de nova rodada em máquina menos carregada.
Regressão: `npm run test:cov` → 5 falhas por tempo limite (acima); sem linha de base registrada para comparar.
Desvios: a rota inicial do portal é `/` (e não `/portal`, como diz R1); o teste usa `/`. A rota `/portal/pedidos` tem como busca principal `/portal/students`, e `/portal/termo` tem `/portal/term`. CA4 "sem aluno ligado" em Minhas informações virou "sem modalidade" (`portalInfo.emptyTitle`), único vazio que a tela tem.
Não é do item: nenhum.

### Nova rodada da regressão (T4)
- `npm run lint && npm run typecheck` → passam. `npm run test:cov -- --maxWorkers=2` → 159 arquivos passaram, 1 falhou (1402 testes passaram, 1 falhou): `src/features/agenda/LessonPanelPage.test.tsx` > "CA10: carregando, erro com Tentar de novo, sem permissão e aula não encontrada" (`findByText("Carregando...")` devolve elemento que sai do documento). Os tempos limite da rodada anterior não se repetiram com 2 workers.
- A falha reproduz isolada (`npx vitest run LessonPanelPage`) e também com `ModalityConfirmations.tsx` na versão de `HEAD` (provado e restaurado), portanto é anterior ao item e não é dele. Arquivos do item não tocam `features/agenda`.
- Limite de 80% de cobertura não foi lido: a rodada terminou vermelha. T4 continua por marcar; precisa de issue no formato das #318 e #320 para a falha de `LessonPanelPage` (não criada por este agente).
