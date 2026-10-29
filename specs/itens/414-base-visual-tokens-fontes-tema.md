# 414 — Base visual: tokens de cor, fontes e tema escuro do canvas

Origem: issue #414 (Sprint 10.5 · Identidade visual), tabela de tokens de `docs/design/README.md`. Branch: `item/414-base-visual-tokens-fontes-tema`.

## 1. Objetivo

Trocar a base cinza padrão do shadcn/ui pela identidade do canvas (cores, fontes, raios, sombras e tamanho base) nos dois temas, num lugar só: `frontend/src/index.css`. Quem usa o sistema passa a ver a nova identidade em todas as telas sem que componente ou tela mude.

Fora do escopo: componentes e telas (#415 em diante); texto, regra ou rota. `Button`, `Card` etc. só herdam os tokens.

## 2. Requisitos

- R1: `:root` e `.dark` trazem os valores da tabela de tokens de `docs/design/README.md` (fundo, cartão, texto, secundário, borda, campo, destaque, botão principal, anel de foco, destrutivo) (D1, D2).
- R2: existem variáveis dos seis tons de situação (`ok`, `warn`, `danger`, `info`, `neutral`, `acc`), fundo e texto, nos dois temas, expostas ao Tailwind (D3).
- R3: o menu lateral tem `--sidebar`, `--sidebar-foreground` e `--sidebar-accent`, com valores iguais nos dois temas.
- R4: Barlow Condensed (500, 600, 700) e IBM Plex Sans (400, 500, 600) são servidas pelo `@fontsource`, sem link externo; `--font-sans` é IBM Plex Sans e `--font-heading` é Barlow Condensed, usada em h1 a h3 (D4).
- R5: raio base 8 px, cartão 12 px, janela 16 px; sombras de janela, painel e menu suspenso; tamanho base de texto 14 px (D5).
- R6: contraste AA (4,5:1) para texto e texto secundário sobre fundo e cartão, e para o texto de cada tom sobre o seu fundo, nos dois temas (D6).
- R7: `ThemeToggle` continua alternando e lembrando o tema.

## 3. Critérios de aceite

- CA1 (R1): dado `index.css`, quando um teste lê `:root` e `.dark`, então cada token tem exatamente o valor da tabela do `docs/design/README.md` e os dois blocos têm as mesmas chaves.
- CA2 (R2): dado `index.css`, quando o teste lê os tons, então os seis tons têm fundo e texto nos dois temas com os valores da tabela, e cada um está mapeado em `@theme inline` (`bg-ok`, `text-ok-foreground`).
- CA3 (R3): dado `index.css`, então `--sidebar`, `--sidebar-foreground` e `--sidebar-accent` valem `#17171A`, `#D8D4CB` e `#2B2B30` em `:root` e em `.dark`.
- CA4 (R4): dado o build (`npm run build`), então `dist` contém os arquivos de fonte dos pesos pedidos e nenhum arquivo de `dist` cita `fonts.googleapis.com` nem `fonts.gstatic.com`; `--font-sans` começa por "IBM Plex Sans" e `--font-heading` por "Barlow Condensed".
- CA5 (R5): dado `index.css`, então `--radius` é 0.5rem, `--radius-card` 0.75rem, `--radius-window` 1rem, `--text-base` 0.875rem e existem `--shadow-window`, `--shadow-panel` e `--shadow-menu` nos dois temas.
- CA6 (R6): dados os pares texto/fundo, quando o teste calcula a razão, então todos têm 4,5:1 ou mais nos dois temas (o texto apagado fica fora do teste, D6).
- CA7 (R6): dada a casca renderizada, quando o axe roda nos dois temas, então não há violação (teste de acessibilidade já existente continua verde).
- CA8 (R7): dado o tema, quando a pessoa aciona o botão, então alterna e a escolha persiste ao recarregar (testes de `theme.test.tsx` seguem verdes).

## 4. Plano técnico

- Dados e API: não mudam.
- Interface: só tokens e fontes; telas herdam. Larguras e estados não mudam. Os dois temas.
- Segurança: não se aplica.
- Arquivos:
  - alterar `frontend/src/index.css` (existe: `:root`, `.dark`, `@theme inline`, `@layer base`): trocar valores, acrescentar tons, `sidebar`, sombras, raios, `--font-heading`, mapear no `@theme inline` (`--color-ok`, `--color-ok-foreground` etc., `--color-sidebar*`, `--font-heading`, `--radius-card`, `--radius-window`, `--shadow-*`) e regra de base `h1, h2, h3 { font-family: var(--font-heading) }`.
  - alterar `frontend/src/main.tsx` (existe): importar `@fontsource/barlow-condensed/latin-{500,600,700}.css` e `@fontsource/ibm-plex-sans/latin-{400,500,600}.css`.
  - alterar `frontend/package.json` e `package-lock.json`: dependências `@fontsource/barlow-condensed` e `@fontsource/ibm-plex-sans`.
  - alterar `frontend/src/theme/theme.test.tsx` (existe: lê o CSS e calcula contraste): valores da tabela (CA1 a CA3, CA5) e pares de contraste novos (CA6).
  - registrar no arquivo do item, na construção, a tabela dos pares com a razão (exigida pelo "Pronto quando").
- Reuso: o leitor de tokens e a função `contrast` de `theme.test.tsx`; `ThemeProvider`, `ThemeToggle` e o script do `index.html` ficam como estão.
- Riscos: o teste de tokens exige as mesmas chaves nos dois blocos (sombras e raios se repetem nos dois); o teste de contraste lê só valores hex (manter cores em hex); mudar `ring` e `destructive` pode afetar prints de e2e (não há comparação visual ainda, #416).

## 5. Tarefas

- [x] T1: instalar `@fontsource/barlow-condensed` e `@fontsource/ibm-plex-sans` e importar os pesos em `main.tsx` — cobre CA4 — prova: `npm run build` em `frontend/` e `grep -rl "fonts.googleapis\|fonts.gstatic" dist` sem resultado.
- [x] T2: reescrever `index.css`: tokens claro/escuro, tons, menu lateral, fontes, raios, sombras, tamanho base, mapa do Tailwind e regra dos títulos — cobre CA1, CA2, CA3, CA4, CA5 — prova: `npx vitest run src/theme/theme.test.tsx`.
- [x] T3: ampliar `theme.test.tsx` com a tabela de valores e os pares de contraste (inclui os tons) nos dois temas — cobre CA1, CA2, CA3, CA5, CA6 — prova: `npx vitest run src/theme/theme.test.tsx`.
- [x] T4: conferir axe, alternância de tema e regressão do front — cobre CA7, CA8 — prova: `npm run lint && npm run typecheck && npm run build && npm run test:cov && npm run e2e`.

## 6. Decisões

- D1: mapeamento no shadcn/ui: `background` = fundo da página; `card` e `popover` = cartão; `foreground` e `card-foreground` = texto; `primary` = botão principal (`#1B1B1F` com `#FFFFFF` no claro; `#ECE9E2` com `#1D1D21` no escuro); `secondary`, `muted` e `accent` = fundo suave (`#FAF8F4` / `#19191C`); `muted-foreground` = texto secundário; `border` = borda; `input` = borda de campo (`#D9D4C9` / `#45454C`). Motivo: os nomes do shadcn/ui são mantidos para os componentes atuais não quebrarem. Alternativa descartada: renomear tokens.
- D2: a tabela não traz anel de foco nem destrutivo; `ring` = cor de link e destaque (`#B23C24` / `#F08A70`) e `destructive` = texto do tom `danger` (`#9C2516` / `#F4A08F`) com `destructive-foreground` `#FFFFFF` / `#1D1D21`. Extras do README entram como `--brand` (`#C8462C`), `--surface-soft`, `--divider` e `--subtle` (texto apagado). Motivo: coerência com a tabela e contraste AA. Alternativa descartada: manter o azul e o vermelho antigos, que destoam do canvas.
- D3: nomes dos tons: `--ok` (fundo) e `--ok-foreground` (texto), e igual para os demais; vira `bg-ok text-ok-foreground`. Motivo: segue o padrão `x` / `x-foreground` do shadcn/ui.
- D4: `--font-heading` só é aplicada a h1 a h3 pela regra de base (família, peso 600, `letter-spacing: 0.02em`); a caixa alta e os tamanhos (38, 22 e 34 px) ficam para os componentes (#415 e #417). Motivo: caixa alta global mudaria telas ainda não redesenhadas.
- D5: `--text-base` passa a 0.875rem (14 px), `--text-sm` a 0.8125rem (13 px) e entra `--text-xs` 0.75rem; `html` continua em 16 px para não mexer nos `rem` dos componentes. Sombras, iguais nos dois temas e lidas do canvas (valores mais usados): janela `0 24px 64px rgba(23,23,26,.3)`, painel `-16px 0 48px rgba(23,23,26,.18)`, menu suspenso `0 16px 40px rgba(23,23,26,.16)`.
- D6: o texto apagado (`#8C877D` / `#8F8A80`) não chega a 4,5:1 sobre o fundo; fica fora do teste de AA e a #415 não deve usá-lo para texto essencial.
- Suposição: tamanhos e sombras vêm da leitura do canvas (`Main.dc.html`, `Topbar.dc.html`); se o dono quiser outros valores, só mudam as variáveis.

## Construção

- T1 — arquivos: `frontend/package.json`, `package-lock.json`, `frontend/src/main.tsx` — prova: `npm run build` → ok, 6 arquivos woff2 (Barlow Condensed 500/600/700, IBM Plex Sans 400/500/600) em `dist/assets`; `grep -rl "fonts.googleapis\|fonts.gstatic" dist` → sem resultado
- T2 — arquivos: `frontend/src/index.css` — prova: `npx vitest run src/theme/theme.test.tsx` → 43 passam
- T3 — arquivos: `frontend/src/theme/theme.test.tsx` (tabela de 33 tokens nos dois temas, raios/sombras/fontes, mapa do Tailwind, contraste dos seis tons, sidebar, ring) — prova: mesmo comando → 43 passam
- T4 — prova: `npm run lint && npm run typecheck && npm run test:cov && npm run e2e` → 160 arquivos/1440 testes passam, cobertura ok, e2e 81 passam (axe incluído)
Regressão: acima, build ok; sem comparação com linha de base por falha nova (nenhuma falha).
Desvios: nenhum. Pares de contraste: todos os tons (texto sobre fundo), foreground/muted-foreground sobre card, sidebar-foreground sobre sidebar e ring sobre fundo, nos dois temas, todos >= 4,5:1 (teste `pares texto/fundo`). Texto apagado (`subtle`) fora, conforme D6.
Não é do item: `specs/backlog.md` já alterado antes.

### Correção da rodada 1

- achado corrigido (tabela dos pares de contraste com a razão) — arquivos: este arquivo — prova: razões calculadas com a mesma função `contrast` do teste, lendo `frontend/src/index.css` (script no scratchpad, fora do repositório); todas >= 4,5:1; mesmos pares do teste `pares texto/fundo`; `npx vitest run src/theme/theme.test.tsx` não mudou (43 passam).

| Par (texto sobre fundo) | Claro | Escuro |
|---|---|---|
| ok-foreground sobre ok | 6.55:1 | 7.46:1 |
| warn-foreground sobre warn | 6.10:1 | 7.98:1 |
| danger-foreground sobre danger | 6.11:1 | 6.80:1 |
| info-foreground sobre info | 8.00:1 | 7.88:1 |
| neutral-foreground sobre neutral | 7.64:1 | 8.51:1 |
| acc-foreground sobre acc | 6.22:1 | 6.95:1 |
| foreground sobre card | 17.17:1 | 13.86:1 |
| muted-foreground sobre card | 5.63:1 | 6.69:1 |
| sidebar-foreground sobre sidebar | 12.10:1 | 12.10:1 |
| ring sobre background | 5.27:1 | 7.51:1 |
| foreground sobre background | 15.35:1 | 15.17:1 |
| card-foreground sobre card | 17.17:1 | 13.86:1 |
| popover-foreground sobre popover | 17.17:1 | 13.86:1 |
| primary-foreground sobre primary | 17.17:1 | 13.86:1 |
| secondary-foreground sobre secondary | 16.19:1 | 14.47:1 |
| muted-foreground sobre background | 5.03:1 | 7.32:1 |
| muted-foreground sobre muted | 5.31:1 | 6.98:1 |
| accent-foreground sobre accent | 16.19:1 | 14.47:1 |
| destructive sobre background | 6.98:1 | 9.00:1 |
| destructive-foreground sobre destructive | 7.81:1 | 8.22:1 |

Texto apagado (`subtle`) fora, conforme D6. Código não mudou nesta rodada; regressão da construção continua valendo.

## Verificação

### Rodada 1 — CORRIGIR_CODIGO — 2026-10-29, estado b83f77b612a74d70b9f74f12a3a831f9a6f2e082

| Critério | Status | Evidência |
|---|---|---|
| CA1 | PASSOU | valores de `index.css` conferidos à mão contra `docs/design/README.md` (iguais) e `theme.test.tsx` 43 passam; mesmas chaves nos dois blocos |
| CA2 | PASSOU | seis tons com fundo/texto nos dois temas iguais à tabela; `--color-<tom>` e `-foreground` em `@theme inline`; teste passa |
| CA3 | PASSOU | `#17171A`, `#D8D4CB`, `#2B2B30` em `:root` e `.dark` |
| CA4 | PASSOU | `npm run build` ok; 6 woff2 (Barlow Condensed 500/600/700, IBM Plex Sans 400/500/600) em `dist/assets`; grep por `fonts.googleapis`/`fonts.gstatic` em `dist` sem resultado; `--font-sans`/`--font-heading` com as famílias pedidas |
| CA5 | PASSOU | raios 0.5/0.75/1rem, `--text-base` 0.875rem, três sombras nos dois blocos |
| CA6 | PASSOU | teste `pares texto/fundo` passa nos dois temas (tons, foreground/muted sobre card, sidebar, ring); a tabela com as razões não está registrada (ver achado) |
| CA7 | PASSOU | `npm run e2e` 81 passam (axe nos dois temas incluído) |
| CA8 | PASSOU | testes de `theme.test.tsx` verdes |

Comandos: `npx vitest run src/theme/theme.test.tsx` 43 passam; `npm run lint` ok; `npm run typecheck` ok; `npm run build` ok; `npm run test:cov` 160 arquivos/1440 testes, limiares ok; `npm run e2e` 81 passam. Sem falha, sem instável.
Achados:
- obrigatório, código, seção "Construção" deste arquivo — esperado: a issue #414 ("Pronto quando") e o plano técnico exigem a tabela com os pares de contraste e a razão de cada um registrada no arquivo do item; observado: a Construção só lista quais pares foram testados e diz ">= 4,5:1", sem a razão de nenhum; evidência: leitura da seção Construção; corrigir: calcular a razão de cada par nos dois temas (a função `contrast` de `theme.test.tsx`) e registrar a tabela em "Construção".

### Rodada 2 — OK — 2026-10-29, estado 0d49161af6d31e50ccce35d0062d86fe003738aa

| Critério | Status | Evidência |
|---|---|---|
| CA1 a CA5 | PASSOU | mantido da rodada 1; único arquivo alterado desde então é este (`git diff --stat` do estado b83f77b: 1 arquivo, +48) |
| CA6 | PASSOU | teste `pares texto/fundo` verde; tabela com as razões agora registrada em "Construção", todas >= 4,5:1, mesmos pares do teste |
| CA7, CA8 | PASSOU | mantido da rodada 1 (código inalterado) |

Comandos: `npx vitest run src/theme/theme.test.tsx` 43 passam; demais comandos da regressão não repetidos porque nenhum código mudou desde a rodada 1.
Achados:
- achado obrigatório da rodada 1 (tabela de razões): corrigido.

## Entrega

- O que muda: `frontend/src/index.css` passa a trazer a identidade do canvas: tokens claro e escuro (D1, D2), seis tons de situação com fundo e texto (`bg-ok text-ok-foreground` etc.), menu lateral (`--sidebar*`), raios 8/12/16 px, sombras de janela, painel e menu, texto base de 14 px e `--font-heading` nos títulos h1 a h3. `frontend/src/main.tsx` importa Barlow Condensed (500, 600, 700) e IBM Plex Sans (400, 500, 600) pelo `@fontsource`, sem link externo; as duas dependências estão em `frontend/package.json`.
- Verificação: rodada 2 OK (estado 0d49161). `theme.test.tsx` com 43 testes passando (valores da tabela, mesmas chaves nos dois temas, mapa do Tailwind, pares de contraste); `lint`, `typecheck`, `build`, `test:cov` e `e2e` (81, com axe nos dois temas) passaram. Razões de contraste dos pares na seção "Construção".
- Decisões: mapeamento no shadcn/ui (D1), `ring` e `destructive` a partir dos tons de link e de perigo (D2), negrito e caixa alta dos títulos ficam para #415 e #417 (D4), texto apagado fora do teste de AA (D6).
- Limitações: o texto apagado (`--subtle`, `#8C877D` / `#8F8A80`) não chega a 4,5:1 e não deve ser usado para texto essencial até a #415. Não há comparação visual das telas ainda (#416).
