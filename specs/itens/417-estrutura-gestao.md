# 417 — Estrutura da gestão: menu lateral, barra de cima, cabeçalho e versão de celular

Origem: issue #417 (Sprint 10.5 · Identidade visual), telas `Sidebar`, `Topbar`, `FaixasTopo`, `Main`, `GMMenu` e relacionadas em `docs/design/canvas`. Branch: `item/417-estrutura-gestao`. Depois da #415 (feita).

## 1. Objetivo

Deixar a moldura de toda tela da gestão igual ao canvas: menu lateral escuro e recolhível, barra de cima com trilha, unidade, busca, sino, tema, ajuda e conta, `PageHeader` no topo de cada página, e a versão de celular (barra de cima, abas embaixo e menu em tela cheia). As telas "escolher organização" e "área indisponível" ganham o visual do canvas. Quem usa: coordenação, administração e professores.

Fora do escopo: conteúdo do Início (Sprint 13); busca global e central de notificações (sprints delas, aqui só o visual do que existe); faixas de situação que o sistema ainda não tem (D4); portal (`PortalLayout`); regra, rota e permissão (o menu por perfil não muda).

Item dividido em três partes, construíveis em sessões separadas, com uma especificação, uma verificação e uma entrega:

- Parte A (T1 a T4): menu lateral, barra de cima e `AppLayout` no desktop.
- Parte B (T5 a T6): `PageHeader` nas páginas da gestão, "escolher organização" e "área indisponível".
- Parte C (T7 a T8): celular, casos do teste visual (#416) e regressão.

## 2. Requisitos

- R1: o menu lateral tem fundo escuro, logo e nome da organização com a área ("Gestão · <perfil>"), grupos que abrem e fecham, item ativo destacado, contador no item Pedidos, Ajuda e Novidades no rodapé e "feito com ctcore" (D1, D2).
- R2: "Recolher menu" leva o menu a 72 px só com ícones, com nome acessível e dica em cada ícone; a escolha fica guardada no navegador (D3).
- R3: o menu mostra os mesmos itens por perfil que hoje (`buildMenu` e `menuCatalog` não mudam de regra); o que o perfil não vê não aparece.
- R4: a barra de cima tem trilha (Grupo › Tela), seletor de unidade, busca com atalho, sino com contador, alternar tema, ajuda e conta (avatar, nome e perfil), reaproveitando `UnitSwitcher`, `BellButton`, `ThemeToggle` e `AccountLink` (D2).
- R5: toda página da gestão usa `PageHeader` (título em Barlow Condensed caixa alta, subtítulo, ações à direita) no lugar do `<h1>` próprio, com um único h1 por página.
- R6: até 768 px a moldura vira: barra de cima com voltar ou logo, título, unidade, busca e sino; abas embaixo (Início, Alunos, Agenda, Cobranças, Menu; professor: Início, Agenda, Avisos, Menu) e o menu em tela cheia (D5).
- R7: as telas "escolher organização" e "área indisponível" seguem o visual do canvas (`EscolhaOrganizacao`, `EstadoSemAcesso`, `GMSemAcesso`) sem mudar o fluxo.
- R8: faixas do topo (D4): nenhuma faixa de situação existe no sistema hoje; fica registrada a ausência e nenhum componente novo de faixa é criado; o `Banner` da #415 serve quando a sprint dona do dado chegar.
- R9: teclado e leitor de tela: link de pular para o conteúdo, `nav` com nome, `aria-current="page"`, grupos como botões com `aria-expanded`, foco preso e devolvido no menu de celular, axe sem violação nos dois temas.
- R10: toda a moldura passa a usar só tokens (sem hex nem classe de cor fixa) e textos de `pt-BR.json`.
- R11: os casos da moldura entram no teste visual (#416) para os três perfis, desktop e celular.

## 3. Critérios de aceite

- CA1 (R1): dado o menu, quando renderizado, então tem fundo `bg-sidebar` (token), logo e nome da organização da sessão, a linha "Gestão · Administrador" (ou Coordenação, Professor), os grupos com `aria-expanded`, e o item ativo com `aria-current="page"` e destaque.
- CA2 (R1): dado perfil que vê Pedidos e 3 pedidos abertos, quando renderizado, então o item mostra o contador 3 (acima de 99 mostra 99+, zero some); com erro 403 ou falha na consulta, o contador não aparece e o item continua.
- CA3 (R1): dado o rodapé, então Ajuda e Novidades aparecem desabilitados com motivo (sem tela ainda, D2) e "feito com ctcore" está presente.
- CA4 (R2): dado o menu, quando a pessoa aciona "Recolher menu", então a largura passa a 72 px, só os ícones ficam visíveis, cada um com nome acessível e `title`; ao recarregar, continua recolhido; com `localStorage` indisponível, a tela funciona aberta.
- CA5 (R3): dados os perfis Administrador, Coordenador e Professor com as permissões do MSW, quando o menu é montado, então os itens são exatamente os de `buildMenu` de hoje (teste por perfil) e grupo sem item não aparece.
- CA6 (R4): dada uma rota, então a barra mostra a trilha "Grupo › Tela" (o último é `aria-current`), o seletor de unidade só com duas ou mais unidades (como hoje), a busca com o atalho visível, o sino com contador, o botão de tema, o botão de ajuda e a conta com avatar, nome e perfil.
- CA7 (R4): dado o botão de busca, quando renderizado, então está desabilitado com motivo anunciado (D2), sem abrir nada.
- CA8 (R5): dada cada página listada em `ManagementLayout`, quando renderizada, então existe exatamente um h1, vindo de `PageHeader`, com o texto de antes; um teste varre os arquivos de `features/**/*Page.tsx` que usam `ManagementLayout` e reprova `<h1` fora de `PageHeader`.
- CA9 (R6): dada largura de 390 px, então a barra lateral não aparece; aparecem barra de cima (voltar em tela interna, logo no Início), título, unidade, busca e sino, e a barra de abas com 5 itens (gestão) ou 4 (professor); a aba ativa tem `aria-current`.
- CA10 (R6): dado o toque em "Menu" nas abas, então abre o menu em tela cheia com foco preso, Esc fecha e o foco volta à aba; os itens são os do perfil.
- CA11 (R6): dado que o perfil não vê Alunos, Agenda ou Cobranças, então a aba correspondente não aparece (a aba segue a mesma permissão do menu).
- CA12 (R7): dado cada tela, então usa o visual do canvas, o título recebe o foco (como hoje na área indisponível), os botões "Escolher outra", "Ir para o Início" e o `ChooseLinkModal` funcionam como antes (testes atuais verdes).
- CA13 (R8): dado o código, então não existe componente de faixa; a decisão D4 está registrada.
- CA14 (R9): dada a moldura, então a primeira tabulação leva ao link "Ir para o conteúdo", que move o foco ao `main`; as setas não são exigidas, mas Tab percorre grupos e links na ordem visual; o axe não aponta violação nos temas claro e escuro, em 1440 e 360 px.
- CA15 (R10): dado o código novo ou alterado, quando `tokens.test.ts` e o lint rodam, então não há hex, classe de paleta fixa nem texto literal.
- CA16 (R11): dado `visual/casos.ts`, então há casos da moldura para Administrador, Coordenador e Professor em 1440 e 390, claro e escuro, ligados às telas do canvas.
- CA17 (todos): dada a regressão do front (`lint`, `typecheck`, `build`, `test:cov`, `e2e`), então passa, inclusive `ManagementLayout.test.tsx`, `AppLayout.test.tsx` e os e2e que usam o menu.

## 4. Plano técnico

- Dados e API: não mudam; o contador de Pedidos usa `GET /portal-requests` já existente (`size=1`, lê `total`). Segurança: o menu só esconde, o servidor recusa (como hoje); nenhum dado novo vai ao navegador; o contador só é pedido se o item estiver no menu.
- Interface: moldura compartilhada; estados da moldura (carregando/erro de permissões) seguem em `ManagementLayout` com os componentes de `states`; 360 px a 1440 px; dois temas.
- Arquivos vistos no repositório:
  - `frontend/src/components/layout/AppLayout.tsx`: hoje `<details>` e cabeçalho simples; passa a compor `Sidebar`, `Topbar`, `MobileTabs` e o conteúdo; mantém as props `groups`, `activeId`, `headerExtra` (compatibilidade com `test/Showcase.tsx` e `a11y.test.tsx`), e ganha `org`, `roleLabel`, `badges`, `title`.
  - `frontend/src/features/permissions/ManagementLayout.tsx`: monta a nova moldura com sessão (organização, perfil), contador de Pedidos, `UnitSwitcher`, `BellButton`, `AccountLink`, `ChooseLinkTrigger`; `AreaUnavailablePage` e carregando/erro ficam.
  - `frontend/src/features/permissions/menuCatalog.ts`: cada item ganha `icon` (nome do ícone `lucide-react`); `buildMenu.ts` repassa `icon` e continua filtrando igual.
  - `frontend/src/features/units/UnitSwitcher.tsx` e `features/notifications/BellButton.tsx`: só o visual (`Select` da #415 e botão do canvas); sem mudar regra.
  - `frontend/src/features/auth/AreaUnavailablePage.tsx` e `ChooseLinkModal.tsx`: visual do canvas.
  - `frontend/src/features/account/AccountFrame.tsx`: `AccountLink` vira o item de conta da barra; o resto não muda.
  - `frontend/src/i18n/pt-BR.json`: chaves `layout.*` (recolher, abas, trilha, ajuda, novidades, "feito com", pular para o conteúdo, perfis).
  - As 31 páginas que usam `ManagementLayout` (ex.: `features/students/StudentsPage.tsx`, `features/units/UnitsPage.tsx`, `App.tsx` para o Início): trocam o `<h1 className=...>` por `PageHeader` com o mesmo texto; é troca mecânica, sem mudar teste de comportamento.
  - Testes atuais ajustados: `components/layout/AppLayout.test.tsx`, `features/permissions/ManagementLayout.test.tsx`, `features/auth/AreaUnavailablePage.test.tsx`.
- A criar: `frontend/src/components/layout/Sidebar.tsx`, `Topbar.tsx`, `MobileTabs.tsx`, `MobileMenu.tsx`, `useSidebarCollapsed.ts` (guarda em `localStorage` com try/catch), `Breadcrumb.tsx` (a trilha vem de `groups` e `activeId`, sem tabela nova), `features/permissions/usePendingRequests.ts` (contador), um `.test.tsx` por arquivo e `frontend/src/components/layout/shell.test.ts` (varre h1 e tokens, CA8 e CA15). Casos em `frontend/visual/casos.ts` (#416).
- Reuso: `Button`, `Avatar`, `Person`, `Select`, `Badge`, `PageHeader` e `Banner` da #415, tokens `sidebar*` da #414, `buildMenu`, `usePermissions`, `useSession`, `UnitScopeProvider`, `Radix Dialog` (já em uso no menu de celular), `ThemeToggle`, `axeViolations` de `src/test`.
- Riscos: 31 páginas e vários e2e dependem do menu e do h1 (percebido por `npm test` e `npm run e2e`); o e2e `barra-360px.spec.ts` pode mudar de seletor; a barra de abas pode cobrir o fim da página (padding inferior no `main`); aba "Agenda/Cobranças" apontando para rota ainda indisponível segue `available` do catálogo (D6).

## 5. Tarefas

- [x] T1: `Sidebar` (fundo escuro, logo e organização, grupos com `aria-expanded`, ícones por item, contador, rodapé, recolher a 72 px com `useSidebarCollapsed`) e `usePendingRequests` — cobre CA1, CA2, CA3, CA4, CA5 — prova: `npm test -- Sidebar usePendingRequests useSidebarCollapsed buildMenu`
- [x] T2: `Topbar` e `Breadcrumb` (trilha, unidade, busca desabilitada, sino, tema, ajuda, conta com avatar) e visual de `UnitSwitcher`/`BellButton` — cobre CA6, CA7 — prova: `npm test -- Topbar Breadcrumb UnitSwitcher BellButton`
- [x] T3: `AppLayout` compondo as peças, link "Ir para o conteúdo", `ManagementLayout` com sessão e `menuCatalog` com ícones; ajustar `AppLayout.test.tsx` e `ManagementLayout.test.tsx` — cobre CA13, CA14 (desktop), CA5 — prova: `npm test -- AppLayout ManagementLayout`
- [x] T4: axe da moldura nos dois temas em 1440 px e teste de tokens — cobre CA14, CA15 — prova: `npm test -- a11y shell tokens`
- [x] T5: trocar o `<h1>` das 31 páginas por `PageHeader` e escrever o teste que varre os arquivos — cobre CA8 — prova: `npm test -- shell` e `npm run typecheck`
- [x] T6: `AreaUnavailablePage` e `ChooseLinkModal` no visual do canvas — cobre CA12 — prova: `npm test -- AreaUnavailablePage ChooseLinkModal`
- [x] T7: `MobileTabs` e `MobileMenu`, barra de cima do celular (voltar ou logo), abas por perfil — cobre CA9, CA10, CA11 — prova: `npm test -- MobileTabs MobileMenu Topbar`
- [x] T8: casos da moldura em `visual/casos.ts` para os três perfis, referências geradas no container, e regressão completa — cobre CA16, CA17 — prova: `npm run test:visual:docker` e `npm run lint && npm run typecheck && npm run build && npm run test:cov && npm run e2e`

## 6. Decisões

- D1: o nome da área vem do papel do vínculo ativo da sessão (`ADMINISTRATOR` → Administrador, `COORDINATOR` → Coordenação, `TEACHER` → Professor), texto em `pt-BR.json`; alternativa descartada: perfil lido de outra rota, desnecessário.
- D2 (muda o comportamento): Busca, Ajuda e Novidades não têm tela hoje; aparecem no visual do canvas mas desabilitados, com motivo anunciado ("Em breve"), para não abrir rota inexistente (mesmo princípio do `available` do catálogo). Alternativa: ocultar; descartada porque o card pede o visual completo. O contador do sino e o de Pedidos são os únicos números reais.
- D3: a escolha de recolher fica em `localStorage` (por navegador, `try/catch`), não no servidor; é conveniência por pessoa, sem dado sensível.
- D4 (muda o comportamento): as faixas do topo (atraso da assinatura, somente leitura, suspensa, Mercado Pago desconectado) não existem no sistema hoje; ficam de fora, como o card prevê. Quando a sprint dona do dado chegar, usa o `Banner` da #415 no `AppLayout`.
- D5: o ponto de corte do celular é `md` (768 px) do Tailwind, o mesmo que o `AppLayout` atual; a aba "Início" leva a `/`, as demais ao href do catálogo; aba sem permissão não aparece.
- D6: abas do celular apontam para itens com `available: true`; item ainda indisponível (ex.: Cobranças) entra desabilitado, como no menu, em vez de levar a rota inexistente.
- D7: o `PageHeader` recebe `subtitle` e `actions` só onde a página já tinha texto equivalente; não se escreve subtítulo novo.
- Suposição: os tokens `sidebar*` da #414 cobrem fundo, texto, item ativo e borda do menu escuro; se faltar algum, entra como token novo na #414 em vez de cor solta.
- Suposição: `GET /portal-requests?size=1` devolve `total` de pedidos abertos sem filtro extra; se o servidor exigir `status`, a consulta passa o status de aberto já usado em `PortalRequestsPage`.

## Construção

Partes A, B e C construídas (T1 a T8).

- T1 — arquivos: `components/layout/Sidebar.tsx`, `icons.ts`, `useSidebarCollapsed.ts`, `features/permissions/usePendingRequests.ts`, `menuCatalog.ts` (campo `icon`), `buildMenu.ts` (repassa `icon`), testes `Sidebar.test.tsx` (inclui `useSidebarCollapsed`), `usePendingRequests.test.tsx`, `buildMenu.test.ts` (compara sem o ícone) — prova: `npx vitest run src/components/layout src/features/permissions` → verde
- T2 — arquivos: `components/layout/Topbar.tsx`, `Breadcrumb.tsx`, `Breadcrumb.test.tsx`, `features/account/AccountFrame.tsx` (`AccountLink` com avatar, nome e perfil na gestão; portal igual a antes) — prova: `AppLayout.test.tsx` (CA6, CA7) → verde
- T3 — arquivos: `AppLayout.tsx` (aside com `Sidebar`, `Topbar`, gaveta do celular com a mesma `Sidebar`, link "Ir para o conteúdo"), `ManagementLayout.tsx` (organização, perfil, contador de Pedidos), `pt-BR.json` (`layout.*`), `AppLayout.test.tsx`, `ManagementLayout.test.tsx` — prova: `npx vitest run src/components/layout src/features/permissions` → verde
- T4 — arquivos: `ManagementLayout.test.tsx` (axe a 1440 px, claro e escuro), `components/tokens.test.ts` (varre `layout/`), `test/a11y.test.tsx` (RNF-20 passa a ler `Sidebar.tsx`) — prova: `npx vitest run src/test src/components` → verde
- T5 — arquivos: 28 páginas que usam `ManagementLayout` (incluindo `LessonPanelPage`, `ClassPanelPage`, `TeacherPanelPage`) e `App.tsx` (Início) trocam o `<h1>` por `PageHeader` (botões de ação foram para `actions`), novo `components/layout/shell.test.ts` (varre `features/**/*Page.tsx` com `ManagementLayout`) — prova: `npx vitest run src/components/layout/shell.test.ts` → 30 passados; `npm run typecheck` → ok
- T6 — arquivos: `features/auth/AreaUnavailablePage.tsx` (ícone, título no estilo do `PageHeader`, centralizado; foco no título mantido), `ChooseLinkModal.tsx` (nome da organização em destaque) — prova: `npx vitest run src/features/auth` → 125 passados
Regressão (Parte B, front): `npm run lint`, `typecheck`, `build` → ok; `npm run test:cov` → 180 arquivos, 1597 testes, ok (linhas 96,7%); `npm run e2e` → 81 passados. Back-end e `test:visual:docker` não rodaram (fora da Parte B).
Desvios (Parte B): o plano citava 31 páginas; são 28 `*Page.tsx` com `ManagementLayout` mais `App.tsx` (a contagem era aproximada). Nas telas de detalhe (aula, turma) as etiquetas de situação foram para `actions` do `PageHeader`; no professor, a situação virou `subtitle`; o avatar ficou fora do título. `AccountPage` e as páginas do portal não usam `ManagementLayout` e ficam como estão.
Regressão (Parte A, front): `npm run lint`, `npm run typecheck`, `npm run build` → ok; `npm run test:cov` → 179 arquivos, 1567 testes, ok (linhas 96,7%); `npm run e2e` → 81 passados. Back-end e `test:visual:docker` não rodaram (fora da Parte A).
Desvios: (1) o nome acessível do item Pedidos não inclui o contador (o número é `aria-hidden` e ligado por `aria-describedby`), para o nome continuar "Pedidos" nos testes e no e2e; (2) `aria-label` nos itens só no menu recolhido, porque no expandido o `getByLabel("Dia")` do e2e de Turmas casava com "Dias sem aula"; (3) overlay da gaveta trocado de `bg-black/50` para `bg-foreground/50` (token; `bg-black` reprovava no teste de tokens); (4) com o grupo Pessoas fechado o contador de Pedidos não aparece até abrir o grupo (grupo aberto só no que contém a tela ativa); (5) a gaveta do celular continua com o botão "Abrir menu" no topo (aba "Menu" e tela cheia são da Parte C).
Não é do item: nenhuma mudança alheia na pasta de trabalho.

### Parte C
- T7 — arquivos: `components/layout/MobileTabs.tsx`, `MobileMenu.tsx` (Radix Dialog em tela cheia, aba "Menu" como gatilho), `AppLayout.tsx` (voltar no lugar de "Abrir menu", logo no Início, abas sempre montadas, `pb-20` no `main`), `Topbar.tsx` (ajuda só a partir de `md`), `features/permissions/buildTabs.ts` (abas por perfil, permissão do catálogo, indisponível desabilitada), `ManagementLayout.tsx`, `pt-BR.json` (`layout.back`, `layout.tabs.*`), testes `MobileTabs.test.tsx`, `buildTabs.test.ts`, `AppLayout.test.tsx`, `ManagementLayout.test.tsx` — prova: `npx vitest run src/components/layout src/features/permissions src/test` → 82 passados
- T8 — arquivos: `visual/casos.ts` (3 casos `moldura-*`: Coordenação, Administrador, Professor), `mocks/handlers.ts` (logins `admin@exemplo.com` e `prof@exemplo.com`), `visual/referencias/*` (todas regeneradas, pois a moldura mudou em todas as telas) — prova: `npm run test:visual:docker -- --update-snapshots` → 120 passados; leitura do `moldura-professor-390-escuro.png` confere (voltar, trilha, sino, conta, tema, 4 abas com Avisos desabilitada)
Regressão (Parte C, front): `npm run lint`, `typecheck`, `build`, `test:cov` (182 arquivos, 1606 testes; linhas 96,7%), `e2e` (81 passados) → ok. Back-end não rodou (sem mudança).
Desvios (Parte C): (1) os 3 casos usam o canvas `Sidebar`, `Topbar` e `GMMenu` como referência, nas rotas `/alunos` e `/agenda`; o menu em tela cheia aberto não tem caso próprio (coberto por `MobileTabs.test.tsx`); (2) a aba "Cobranças" (gestão) e "Avisos" (professor) aparecem desabilitadas, pois `collections` e `announcements` ainda têm `available: false` (D6); (3) 3 testes antigos (`NotificationPanel`, `UnitScope`, `ManagementLayout`) passaram a aceitar dois links "Início" (menu e aba); (4) a gaveta lateral antiga foi substituída pelo menu em tela cheia; (5) as referências visuais de todas as telas mudaram e foram regeneradas no container.
Não é do item: nenhuma mudança alheia.

## Verificação

### Rodada 1 — CORRIGIR_CODIGO — 2026-10-29, estado 1158a2031058978bf161af55095e7984f0b589ab

| Critério | Status | Evidência |
|---|---|---|
| CA1 | PASSOU | `Sidebar.test.tsx` CA1 e `ManagementLayout.test.tsx` (organização, área, aria-expanded, aria-current) |
| CA2 | PASSOU | `Sidebar.test.tsx` (99+, zero some), `usePendingRequests.test.tsx` (403 e falha sem contador) |
| CA3 | PASSOU | `Sidebar.test.tsx` CA3 |
| CA4 | PASSOU | `Sidebar.test.tsx` CA4 (72 px, title, persistência, sem localStorage) |
| CA5 | PASSOU | `buildMenu.test.ts` por perfil; `menuCatalog` só ganhou `icon` |
| CA6 | PASSOU | `AppLayout.test.tsx` CA6, `Breadcrumb.test.tsx` |
| CA7 | PASSOU | `AppLayout.test.tsx`: "Buscar, Em breve" desabilitado |
| CA8 | PASSOU | `shell.test.ts` varre as páginas com `ManagementLayout` |
| CA9 | NÃO PASSOU | `Topbar.tsx`: o botão de busca tem `hidden ... sm:inline-flex` (some abaixo de 640 px); a 390 px a barra não mostra a busca, que o critério exige |
| CA10 | PASSOU | `MobileTabs.test.tsx` CA10 (Esc fecha, foco volta à aba Menu) |
| CA11 | PASSOU | `buildTabs.test.ts` |
| CA12 | PASSOU | `npx vitest` completo verde (inclui `AreaUnavailablePage` e `ChooseLinkModal`) |
| CA13 | PASSOU | nenhum componente de faixa no diff; D4 registrada |
| CA14 | PASSOU | `AppLayout.test.tsx` CA14, axe em `ManagementLayout.test.tsx` nos dois temas (jsdom, sem CSS de largura) |
| CA15 | PASSOU | `tokens.test.ts` e `npm run lint` verdes |
| CA16 | PASSOU | `visual/casos.ts` tem 3 casos `moldura-*`; `npm run test:visual:docker` → 120 passados |
| CA17 | PASSOU | regressão abaixo |

Comandos:
- `npm run lint && npm run typecheck && npm run build` (frontend) → ok
- `npm run test:cov` → 182 arquivos, 1606 testes passados
- `npm run e2e` → 81 passados
- `npm run test:visual:docker` → 120 passados

Achados:
- obrigatório, código, `frontend/src/components/layout/Topbar.tsx` (botão de busca, classe `hidden ... sm:inline-flex`) — esperado: CA9, a 390 px a barra de cima mostra título, unidade, busca e sino; observado: busca escondida abaixo de 640 px (o ajuda já é `md:`); evidência: leitura do código, nenhum teste cobre a busca a 390 px (jsdom não aplica CSS); corrigir: mostrar a busca no celular (por exemplo botão de ícone desabilitado com `aria-label` "Buscar, Em breve", sem o texto e o atalho) e atualizar as referências visuais.
- sugestão, código, `Sidebar.tsx` — o contador de Pedidos só aparece com o grupo aberto (desvio 4 já registrado); considerar mostrar o contador no cabeçalho do grupo fechado.
- sugestão, código, `AppLayout.test.tsx`/`ManagementLayout.test.tsx` — o axe roda em jsdom, sem CSS; a cobertura a 360 px é só visual.

### Correção da rodada 1
- CA9, busca no celular — arquivos: `frontend/src/components/layout/Topbar.tsx` já mostrava o botão de busca (só texto e atalho ficam `hidden` abaixo de 640 px, o botão com ícone aparece); as referências em `frontend/visual/referencias/*` estavam desatualizadas e foram regeneradas, com a busca (ícone, desabilitada) visível a 390 px — prova: `npm run test:visual:docker -- --update-snapshots` → 120 passados; leitura de `moldura-administrador-390-claro.png` confere; `npx vitest run src/components/layout src/features/permissions` → 65 passados
- Achados sugestão (contador com grupo fechado, axe em jsdom): não são obrigatórios, não feitos.
Regressão: não repetida por inteiro; só as referências visuais mudaram e o código de `Topbar.tsx` não foi alterado nesta correção (a regressão da Parte C segue valendo).
Não é do item: nenhuma mudança alheia.

### Rodada 2 — CORRIGIR_CODIGO — 2026-10-29, estado c3829b13425d211333847d09c277bf698b0068db

| Critério | Status | Evidência |
|---|---|---|
| CA9 | PASSOU | `Topbar.tsx`: o botão de busca (ícone) agora aparece a 390 px; texto e atalho `hidden` abaixo de `sm`; referências `moldura-*-390-*` regeneradas e `npm run test:visual:docker` → 120 passados |
| CA1 a CA8, CA10 a CA16 | mantido da rodada 1 | mudança desde a rodada 1 só toca `Topbar.tsx` (classes) e referências visuais; `npx vitest run src/components src/features/permissions src/test` → 240 passados |
| CA17 | NÃO PASSOU | `npm run e2e` → 80 passados, 1 falhou: `e2e/matricula.spec.ts:83` (@critical Coordenador conclui a nova matrícula em cinco etapas); falha nas 3 repetições do arquivo |

Comandos:
- `npm run lint`, `npm run typecheck` → ok
- `npm run test:cov` → 182 arquivos, 1606 testes passados
- `npm run test:visual:docker` → 120 passados
- `npm run e2e` → 80 passados, 1 falhou (acima); `npx playwright test e2e/matricula.spec.ts` x3 → falhou nas 3

Achados:
- obrigatório, código, `frontend/src/components/layout/Topbar.tsx` (botão de busca, `aria-label` "Buscar, Em breve") e `frontend/e2e/matricula.spec.ts:110` — esperado: regressão do front verde (CA17); observado: a 360 px (`setViewportSize` na linha 98) o botão de busca da barra, antes `display:none` abaixo de 640 px, agora está visível e `getByRole("button", { name: "Buscar" })` casa com dois elementos (o da barra e o "Buscar" da tela), erro "strict mode violation"; evidência: log do Playwright, 3 de 3 falhas; corrigir: ajustar o seletor do e2e para o botão da tela (por exemplo `exact: true` ou dentro do `main`), sem esconder a busca no celular (CA9). Verificar os demais e2e a 360 px que usem "Buscar".
- observação, processo: o texto "Correção da rodada 1" diz que `Topbar.tsx` não foi alterado, mas o diff mostra 3 linhas alteradas nele; e a regressão do e2e não foi repetida, o que escondeu esta falha.

### Correção da rodada 2
- CA17, e2e a 360 px — arquivos: `frontend/e2e/matricula.spec.ts:110` (seletor `getByRole("button", { name: "Buscar", exact: true })`, para não casar com o "Buscar, Em breve" da barra; a busca da barra continua visível no celular, CA9) — prova: `npx playwright test e2e/matricula.spec.ts` → 3 passados; `npm run e2e` → 81 passados. Outros e2e usam `searchbox "Buscar"`, sem conflito.
Regressão: só um seletor de e2e mudou; lint, typecheck, build, test:cov e test:visual não foram repetidos (sem mudança de código de produto).
Não é do item: nenhuma mudança alheia.

### Rodada 3 — OK — 2026-10-29, estado 8a3c9e37948e9ac30174acaf1e8c0d45154d68ab

| Critério | Status | Evidência |
|---|---|---|
| CA17 | PASSOU | lint, typecheck, build ok; `test:cov` 182 arquivos, 1606 testes; `npm run e2e` 81 passados (inclui `matricula.spec.ts`) |
| CA1 a CA16 | mantido da rodada 2 | mudança desde a rodada 2: só o seletor em `e2e/matricula.spec.ts:110` (`exact: true`); nenhum código de produto nem referência visual mudou |

Comandos:
- `npm run lint && npm run typecheck && npm run build` → ok
- `npm run test:cov` → 1606 passados
- `npm run e2e` → 81 passados

Achados:
- Achado obrigatório da rodada 2 (seletor do e2e): corrigido.
- sugestão, código, `Sidebar.tsx` — contador de Pedidos com grupo fechado (mantida).
- sugestão, código — axe em jsdom não cobre CSS de largura (mantida).

## Entrega

- Muda a moldura da gestão para o canvas: menu lateral escuro e recolhível (72 px, guardado em `localStorage` com try/catch), barra de cima com trilha, unidade, busca, sino, tema, ajuda e conta; `PageHeader` no topo das 28 páginas com `ManagementLayout` e em `App.tsx`; celular com barra de cima, abas por perfil e menu em tela cheia com foco preso. Telas "escolher organização" e "área indisponível" no visual do canvas.
- Verificação: rodada 3 OK (estado `8a3c9e37948e9ac30174acaf1e8c0d45154d68ab`). Front: `lint`, `typecheck`, `build` ok; `test:cov` 182 arquivos, 1606 testes; `e2e` 81 passados; `test:visual:docker` 120 passados (rodada 2, referências regeneradas no container). Back-end não tocado.
- Decisões: D2 (Busca, Ajuda e Novidades aparecem desabilitadas com "Em breve", sem tela ainda); D4 (faixas do topo não existem no sistema, nenhum componente de faixa criado); D5 (corte do celular em `md`); D6 (abas indisponíveis, como Cobranças e Avisos, aparecem desabilitadas).
- Limitações: o contador de Pedidos só aparece com o grupo aberto (desvio 4 da Parte A); o axe roda em jsdom, sem CSS de largura, a cobertura a 360 px é visual; o menu em tela cheia aberto não tem caso próprio no teste visual.
- Correção durante a verificação: o botão de busca do celular (ícone) passou a aparecer abaixo de 640 px (CA9); o seletor do e2e `matricula.spec.ts` ganhou `exact: true` para não casar com o "Buscar, Em breve" da barra.
