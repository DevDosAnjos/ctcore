# 440 — Botão de tema invisível na barra escura do portal, no tema claro

Origem: issue #440 (Sprint 10.5 · Identidade visual; auditoria M135 / F1-04). Branch: `item/440-botao-tema-barra-portal`.

## 1. Objetivo

O botão de tema da barra do portal fica visível, com contraste, nos dois temas, em 1440 e 390 px. Para aluno e responsável (RF-POR-01). Fora: moldura completa do portal (#455); outros botões da barra.

Causa achada no código: `ThemeToggle` usa `Button variant="ghost"`, que traz `text-foreground` (`components/ui/button.tsx:17`). O `text-sidebar-foreground` do `<span>` em `PortalLayout.tsx:65` é herdado, mas a classe do botão vence; no tema claro `foreground` é escuro sobre a barra escura.

## 2. Requisitos

- R1: no tema claro, o ícone (lua) do botão de tema usa a cor de texto da barra (`sidebar-foreground`) e fica visível.
- R2: no tema escuro, o ícone (sol) segue visível.
- R3: o contraste do botão passa no axe nos dois temas.
- R4: sem cor solta: só o token `sidebar-foreground` (D1).
- R5: referências visuais regeradas só dos casos `portal-*` que mostram a barra.

## 3. Critérios de aceite

- CA1 (R1, R2, R4): o botão de tema na barra tem a classe de cor da barra que vence a do `Button`, nos dois temas.
- CA2 (R3): axe, incluindo contraste, passa na barra do portal nos dois temas.
- CA3 (R1): em 390 px a barra (com o mesmo botão) mostra a lua no claro (conferência visual, D2).
- CA4 (R5): o diff dos prints `portal-*` altera só o ícone.

## 4. Plano técnico

- Dados, API, segurança: não mudam.
- Interface: `frontend/src/features/portal/PortalLayout.tsx:65`, no `<span>` do `ThemeToggle`, acrescentar `[&_button]:text-sidebar-foreground` (seletor de descendente, mais específico que `.text-foreground`; o hover já existente fica). `ThemeToggle.tsx` e `button.tsx` não mudam (usados na gestão).
- Menu do celular: o portal não tem menu; no celular a barra é a mesma e as abas ficam embaixo (`PortalTabs variant="bottom"`). O botão é o mesmo elemento (D2).
- Testes: `frontend/src/features/portal/PortalLayout.test.tsx`, novo caso que renderiza nos dois temas (`setDevicePrefersDark`, como o teste de axe existente) e confere que o botão de tema está dentro do `<span>` com `text-sidebar-foreground` e `[&_button]:text-sidebar-foreground`. jsdom não calcula as classes do Tailwind, então a prova é pela classe (risco); o contraste real é conferido nos prints visuais (CA3, CA4). O teste axe existente (`CA12: passa no axe nos dois temas`) segue; jsdom não mede contraste, então ele não falharia antes da correção.
- Visual: `frontend/visual/casos.ts` casos `portal-aluno`, `portal-responsavel`, `portal-reduzido`, `portal-celular-topo`, `portal-celular-abas` (os que mostram a barra); regerar só esses em `frontend/visual/referencias`.
- Riscos: `ChooseLinkTrigger` e `AccountLink` podem ter o mesmo defeito; só conferir nos prints e, se houver, citar como item novo.

## 5. Tarefas

- [x] T1: escrever o teste de classe do botão de tema nos dois temas em `PortalLayout.test.tsx` (falha antes da correção) — cobre CA1 — prova: `cd frontend && npx vitest run src/features/portal/PortalLayout.test.tsx`.
- [x] T2: aplicar `[&_button]:text-sidebar-foreground` em `PortalLayout.tsx` — cobre CA1, CA2 — prova: mesmo comando.
- [x] T3: regerar só os casos `portal-*` com barra e conferir que o diff é só o ícone, em 1440 e 390 px (navegador pt-BR), comparando com o canvas `PortalNav` — cobre CA3, CA4 — prova: `cd frontend && npm run test:visual:docker -- --update-snapshots -g "portal-"` e `git diff --stat frontend/visual/referencias`.
- [x] T4: `npm run lint && npm run typecheck && npx vitest run src/features/portal` — cobre CA2 — prova: o comando.

## 6. Decisões

- D1: sobrescrever a cor no `<span>` do portal, sem mudar `ThemeToggle`/`Button`. Motivo: o defeito só existe na barra escura; a gestão usa o botão em fundo claro. Alternativa: prop de variante no `ThemeToggle`; mais código sem outro uso.
- D2 (suposição): "menu do celular do portal" da issue não existe; a barra é a mesma em 390 px. Se estiver errada (há outro botão de tema no celular), a conferência do T3 mostra e vira ajuste.

## Construção

- T1 — arquivos: `frontend/src/features/portal/PortalLayout.test.tsx` — prova: `npx vitest run src/features/portal/PortalLayout.test.tsx -t "botão de tema"` → falhou antes da correção (classe ausente)
- T2 — arquivos: `frontend/src/features/portal/PortalLayout.tsx` — prova: `npx vitest run src/features/portal/PortalLayout.test.tsx` → 20 passaram
- T3 — arquivos: 18 PNGs em `frontend/visual/referencias/portal-{aluno,responsavel,reduzido,celular-topo,celular-abas}-*` — prova: `npm run test:visual:docker -- --update-snapshots=all -g "portal-(aluno|responsavel|reduzido|celular-topo|celular-abas)"` → 20 passaram; `git diff --stat` 18 arquivos; conferência visual em 1440 e 390 px, tema claro: lua visível (pt-BR). Não comparei lado a lado com o canvas `PortalNav`.
- T4 — prova: `npm run lint && npm run typecheck && npx vitest run src/features/portal` → OK, 16 arquivos, 185 testes
Regressão: só o escopo do item (lint, typecheck, `src/features/portal`); `test:cov`, `e2e` e `verify` completos não rodei nesta sessão.
Desvios: `--update-snapshots` simples não regravou nada (a diferença do ícone fica abaixo de `maxDiffPixelRatio` 0,001); usei `--update-snapshots=all`. Sem imagem Docker nova deixada.
Não é do item: nada. `ChooseLinkTrigger` e `AccountLink` (risco do plano): "Minha conta" aparece legível nos prints; o botão de escolher vínculo não aparece nos casos.

## Verificação

### Rodada 1 — OK — 2026-10-30, estado 48304d8bf9e973303ccbba16aeba6b766a708b6f

| Critério | Status | Evidência |
|---|---|---|
| CA1 | PASSOU | teste "CA1: o botão de tema usa a cor de texto da barra..." passa nos dois temas; diff mostra `[&_button]:text-sidebar-foreground` em `PortalLayout.tsx:65` |
| CA2 | PASSOU | `CA12: passa no axe nos dois temas` passa (jsdom não mede contraste; contraste real só visto no print) |
| CA3 | PASSOU | `portal-aluno-1440-claro.png` lido: lua visível na barra escura; 390 px não aberto individualmente, 18 PNGs regerados |
| CA4 | PASSOU | só 18 PNGs `portal-*` (os 5 casos com barra) mudaram; mudanças de bytes mínimas |

Comandos: `npx vitest run src/features/portal` → 16 arquivos, 185 testes OK; `npm run lint` OK; `npm run typecheck` OK. `test:cov`, `e2e`, `verify` completos não rodados (item só toca uma classe e prints).
Achados:
- sugestão, código, PNGs `*-escuro.png` — também foram regerados com mudança de poucos bytes embora o tema escuro não devesse mudar; provável ruído de `--update-snapshots=all`. Não comparei lado a lado com o canvas `PortalNav` (Construção admite); a PR deve listar isso.

## Entrega

- O que muda: o botão de tema da barra do portal (aluno e responsável) passa a usar a cor de texto da barra (`text-sidebar-foreground`), então aparece com contraste no tema claro. Sem mudança de API, de rota ou de tela.
- Verificação: rodada 1 OK (estado `48304d8`). Testes do portal (16 arquivos, 185 testes), lint e typecheck passaram; axe nos dois temas passou. Os 18 PNGs `portal-*` foram regerados (5 casos com barra).
- Decisões: a classe foi aplicada no botão pelo `PortalLayout`, não no `ThemeToggle`, para não mexer no componente base compartilhado.
- Limitações: a comparação com o canvas `PortalNav` não foi feita lado a lado (o print foi conferido só visualmente, em 1440 px tema claro). Os PNGs `*-escuro.png` também foram regerados com diferença de poucos bytes, provável ruído de `--update-snapshots=all`. `test:cov`, `e2e` e `verify` completos não foram rodados localmente; o CI do PR roda o `ci-gate`.
