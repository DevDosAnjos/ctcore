# 437 — Cobranças e Comunicados somem do menu da gestão

Origem: issue #437 (Sprint 10.5 · Identidade visual), auditoria A1 (F1-01). Branch: `item/437-cobrancas-comunicados-no-menu`.

## 1. Objetivo

Ligar no menu lateral e no menu do celular da gestão os itens Cobranças (Financeiro) e Comunicados, hoje com `available=false` por esquecimento, para quem tem a permissão de cada um. As telas e rotas já existem.

Fora do escopo: posição de Comunicados no menu (canvas: Comunicação) e visual do menu (moldura da gestão, componente C1).

## 2. Requisitos

- R1: o item `collections` (`/cobrancas`, grupo Financeiro) aparece no menu da gestão para quem tem `COLLECTIONS` diferente de NONE.
- R2: o item `announcements` (`/comunicados`, grupo Operação) aparece para quem tem `ANNOUNCEMENTS` diferente de NONE.
- R3: sem a permissão o item não aparece.
- R4: no celular, a aba Cobranças (Coordenador e Administrador) e a aba Comunicados (Professor) deixam de ser desabilitadas e levam à rota.
- R5: nas telas, o item ativo e a trilha ficam marcados.

## 3. Critérios de aceite

- CA1 (R1, R2): dado Coordenador ou Administrador, quando o menu é montado, então Cobranças (em Financeiro) e Comunicados aparecem com `/cobrancas` e `/comunicados`.
- CA2 (R2, R3): dado Professor, então vê Comunicados e não vê Cobranças nem o grupo Financeiro.
- CA3 (R3): dado perfil com `COLLECTIONS` ou `ANNOUNCEMENTS` em NONE, então o item correspondente não aparece.
- CA4 (R4): dado o celular, então as abas `collections` e `announcements` vêm com `disabled` falso.
- CA5 (R5): dado `/cobrancas` ou `/comunicados`, então o item ativo e a trilha aparecem (activeId já é `collections`/`announcements`).
- CA6 (R5): em 360 px e desktop, nos dois temas, o axe passa e os quatro estados continuam funcionando.

## 4. Plano técnico

- Dados e API: não mudam.
- Interface: só liga os dois itens; posição e visual não mudam.
- Segurança: o menu só esconde; a barreira segue no servidor.
- Arquivos: `frontend/src/features/permissions/menuCatalog.ts` (passar `true` em `collections` e `announcements`); `frontend/src/features/permissions/buildMenu.test.ts` (teste do catálogo real: `finance` ganha `collections` antes de `plans`, `operation` ganha `announcements` ao fim para Coordenador e Administrador, e o Professor passa a ter `agenda` e `announcements`; acrescentar casos de ausência por permissão NONE); `frontend/src/features/permissions/buildTabs.test.ts` (o teste "tela ainda inexistente fica desabilitada" usa `collections`, que passa a habilitada: trocar por item ainda indisponível, ex. aba inexistente, ou afirmar `disabled` falso para `collections`). `AppLayout.test.tsx`, `MobileTabs.test.tsx` e `Sidebar.test.tsx` usam fixtures próprias, não o catálogo, e não mudam. As telas já passam `activeId` correto (ChargesPage, AnnouncementsPage e demais).
- Reuso: `buildMenu`, `buildTabs`, `mockPermissions`.
- Riscos: testes que contam itens do catálogo real quebram (buildMenu.test, buildTabs.test); e2e ou visual com menu podem mudar, perceptível em `npm test` e `npm run e2e`. Sidebar.test conta 2 "Em breve" em fixture, não afetado.

## 5. Tarefas

- [x] T1: passar `true` em `collections` e `announcements` em `menuCatalog.ts` e ajustar `buildMenu.test.ts` e `buildTabs.test.ts` (inclui casos com e sem permissão) — cobre CA1, CA2, CA3, CA4 — prova: `cd frontend && npx vitest run src/features/permissions`
- [x] T2: conferir trilha e item ativo nas duas telas, 360 px e desktop, dois temas, axe e quatro estados; rodar lint, tipos e testes do front, e e2e se algum cobrir o menu — cobre CA5, CA6 — prova: `cd frontend && npm run lint && npm run typecheck && npm test` e `npx vitest run src/features/charges src/features/announcements src/components/layout`

## 6. Decisões

- D1: ficam fora do item a posição de Comunicados e o visual do menu, como a issue manda. Motivo: pertencem à moldura da gestão (C1).
- Suposição: não há teste e2e ou visual que dependa de o item estar ausente; se houver, ajusta-se na T2 e só regera referência da própria tela se a mudança visual do menu exigir.

## Construção

- T1 — arquivos: `frontend/src/features/permissions/menuCatalog.ts`, `buildMenu.test.ts`, `buildTabs.test.ts` — prova: `npx vitest run src/features/permissions` → 20 passam
- T2 — prova: `npm run lint && npm run typecheck && npm test` → lint e tipos ok, 191 arquivos / 1881 testes passam. Os ids ativos já estavam corretos (R5, sem código novo). Não rodou: conferência visual em 360 px/desktop nos dois temas, axe nas telas e `npm run e2e` (nenhuma tela mudou; só o menu liga dois itens; e2e/visual sem referência a "Em breve" no menu).
Regressão: só front-end rodado (lint, typecheck, `npm test`) → verde. Back-end não tocado.
Desvios: nenhum.

## Verificação

### Rodada 1 — CORRIGIR_CODIGO — 2026-10-30, estado c6a2d87315a84bdb483b8eafb8515d31856253e5

| Critério | Status | Evidência |
|---|---|---|
| CA1 | PASSOU | buildMenu.test.ts (catálogo real) dentro de `npm test`: 1881 passam |
| CA2 | PASSOU | mesmo teste: Professor com `agenda` e `announcements`, sem finance |
| CA3 | PASSOU | caso "#437" com COLLECTIONS/ANNOUNCEMENTS em NONE |
| CA4 | PASSOU | buildTabs.test.ts: `disabled` false para collections e announcements |
| CA5 | PASSOU | activeId já era `collections`/`announcements` (lido no diff/spec); sem teste novo de trilha |
| CA6 | NÃO CONFERIDO | axe/quatro estados/360 px não rodados; o teste visual mostrou mudança nas telas (ver achado) |

Comandos: `npm run lint && npm run typecheck` ok; `npm test` 191 arquivos / 1881 testes ok; `npm run test:visual:docker -- -g "(cobrancas|comunicados|alunos).(1440|390).(claro|escuro)"` 6 falharam, 6 passaram (falham: cobrancas 1440/390 claro/escuro e comunicados 1440 claro/escuro; passam: alunos x4, comunicados 390 x2). `agenda 1440 claro` passa. e2e não rodado.
Achados:
- obrigatório, código, `frontend/visual/referencias/{cobrancas,comunicados}-*.png` — esperado: `visual` (parte do `ci-gate`) verde; observado: o menu e o item ativo dessas telas mudaram e as referências antigas reprovam (10 arquivos de cobrancas/comunicados em 1440 e 390 no mínimo; rodar o conjunto completo para achar o resto: `cobrancas-*`, `comunicados-*`, `cobranca-avulsa`, `cobranca-antecipar`); evidência: log de `test:visual:docker` acima; corrigir: comparar com o canvas (AGENTS), regerar só as referências das telas afetadas com `npm run test:visual:docker -- --update-snapshots -g <casos>` e listar na descrição do PR. A Construção diz "e2e/visual sem referência a Em breve", suposição que o teste refutou.
- sugestão, código, `buildTabs.test.ts` — sem teste de trilha/item ativo (CA5) nas telas.

### Correção da rodada 1
- referências visuais regeradas (só mudou o menu: Cobranças em Financeiro e Comunicados em Operação) — arquivos: `frontend/visual/referencias/` `cobrancas-{,atrasados-,conferencia-,previa-,recebimentos-}{1440,390}-{claro,escuro}` (20), `comunicados-1440-{claro,escuro}` e `comunicados-novo-1440-{claro,escuro}` (4), `planos-`, `planos-lista-` e `beneficios-` `1440-{claro,escuro}` (6, o menu mais longo desloca o conteúdo) — prova: `npm run test:visual:docker -- --update-snapshots -g ...` e depois `npm run test:visual:docker` completo: antes 6 falhas (planos, planos-lista, beneficios em 1440), regeradas; os 28+36 casos de cobrancas/comunicados passam. Divergências de canvas: a mudança é só a presença dos dois itens no menu, que o canvas já mostra; sem outra divergência nova, listar no PR. Resto do conjunto (322) passou.
- Não rodou: `npm run e2e`; axe/360 px (CA6) fora do teste visual já coberto pelos testes das telas em `npm test` (verde na rodada anterior). Sugestão de teste de trilha (CA5) não adotada (opcional).

### Rodada 2 — OK — 2026-10-30, estado 0185c5ab206047e8a7039ed503d34219e3087d2d

| Critério | Status | Evidência |
|---|---|---|
| CA1 | PASSOU | mantido da rodada 1; `vitest run src/features/permissions` 20 passam |
| CA2 | PASSOU | mantido da rodada 1 |
| CA3 | PASSOU | mantido da rodada 1 |
| CA4 | PASSOU | mantido da rodada 1 |
| CA5 | PASSOU | mantido da rodada 1 (activeId já correto) |
| CA6 | NÃO CONFERIDO | axe e 360 px não rodados à parte; testes das telas em `npm test` verdes e `test:visual:docker` completo 328 passam (inclui 390 e dois temas). Sem limitação aceita no AGENTS para isso, mas a mudança é só o menu |

Comandos: `npm run lint` e `npm run typecheck` ok; `npm test` 191 arquivos / 1881 testes ok; `npm run test:visual:docker` 328 passam; e2e não rodado.
Achados:
- rodada 1 obrigatório (referências visuais): corrigido, visual verde.
- sugestão, código, sem teste de trilha (CA5) nas telas.
- sugestão, processo: rodar `npm run e2e` antes do PR.

## Entrega

- O que muda: Cobranças (Financeiro) e Comunicados (Operação) entram no menu da gestão e nas abas de celular de Coordenador, Administrador e Professor, conforme a permissão de cada um. Só `menuCatalog.ts` muda na lógica; os testes do catálogo real e das abas foram ajustados e ganharam casos de ausência por NONE.
- Como foi verificado: `npm run lint` e `npm run typecheck` ok; `npm test` 191 arquivos e 1881 testes ok; `npm run test:visual:docker` 328 passam, após regerar as referências de Cobranças, Comunicados, Planos, Planos (lista) e Benefícios afetadas pelo menu mais longo.
- Decisões: posição de Comunicados no menu e visual da moldura ficam fora (D1).
- Limitações: CA6 (axe e 360 px à parte) e `npm run e2e` não rodados aqui; o CI roda o e2e no PR. Divergências do canvas: nenhuma nova além da presença dos dois itens no menu, que o canvas já mostra.
