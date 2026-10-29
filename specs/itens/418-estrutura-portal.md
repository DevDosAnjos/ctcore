# 418 — Estrutura do portal do aluno: barra com a marca, abas e versão de celular

Origem: issue #418 (Sprint 10.5 · Identidade visual), telas `PortalNav`, `PortalInicio`, `PortalEstados`, `PortalMTopo`, `PortalMTabs`, `PortalMInicio`, `PortalMEstados`, `PortalReduzido`, `PortalMReduzido` em `docs/design/canvas`. Branch: `item/418-estrutura-portal`. Depois da #415 e da #417 (feitas). Parte do que a #88 deixou: `PortalLayout`, `usePortalShell`, `RequireFullPortal`, rota `GET /portal/shell`.

## 1. Objetivo

Deixar a moldura do portal (aluno adulto e responsável) igual ao canvas: barra escura com a marca da academia, abas, sino, tema e conta, rodapé com unidade, endereço e WhatsApp, e a versão de celular com barra de cima e abas embaixo com ícones. A cor principal da organização aparece nos pontos do canvas (logo, destaque) com contraste AA. A faixa do acesso reduzido ganha o visual do canvas. Quem usa: Aluno e Responsável.

Fora do escopo: conteúdo das páginas do portal (#423); regra de acesso reduzido por inativação e bloqueio (Sprint 12; aqui só o visual da faixa que a #88 já alimenta com `reduced`); contador na aba "Minhas informações" (o canvas mostra um número estático, sem regra no sistema); rota, permissão e termo (não mudam).

## 2. Requisitos

- R1: o `PortalLayout` desktop (a partir de 768 px) tem barra escura (`bg-sidebar`) com logo e nome da organização, abas Início, Agenda, Financeiro, Avisos (com contador) e Minhas informações, sino, alternar tema e conta, e a aba atual destacada com `aria-current="page"`.
- R2: o rodapé mostra nome da organização, unidade, endereço, WhatsApp e "feito com ctcore"; o que a unidade não tem cadastrado não aparece (D1, D2).
- R3: no celular (até 767 px, a partir de 360 px) a barra de cima tem a marca (logo e nome) e o sino, e as abas ficam embaixo com ícone e nome curto: Início, Agenda, Financeiro, Avisos, Eu (D6).
- R4: a cor principal da organização é usada no logo (quadrado com as iniciais quando não há imagem) e no destaque da aba atual e do contador; só se a cor tem contraste AA com o texto branco (`passesContrast`); sem cor, ou com cor reprovada, usa a cor padrão `--brand` (D3).
- R5: o cabeçalho das páginas do portal é o `PageHeader` da #415 (título em caixa alta, subtítulo pequeno), com um único h1 por página (D4).
- R6: no portal reduzido, Agenda e Avisos seguem escondidas (desktop e celular) e a faixa de acesso reduzido tem o visual do canvas (faixa no topo, ícone de cadeado), sem mudar o texto nem a regra (D5).
- R7: os estados da moldura (carregando, erro com "Tentar de novo", sem permissão) aparecem dentro da moldura nova, sem quebrar a barra de abas; o estado vazio continua sendo das páginas.
- R8: a moldura usa só tokens (sem hex nem classe de cor fixa; a cor da organização entra por variável CSS), textos de `pt-BR.json`, funciona de 360 a 1440 px e passa no axe nos dois temas; o teclado percorre logo, abas, sino, conta e tema.
- R9: os casos da moldura do portal entram no teste visual (#416) para aluno e responsável, desktop e celular.
- R10 (para o rodapé, D1): `GET /portal/shell` passa a devolver `unit` (`name`, `address`, `whatsapp`, campos nulos quando não cadastrados, ou `unit` nulo) e continua considerando só os alunos da conta (RN-POR-01); `docs/openapi.yaml` e o MSW refletem.

## 3. Critérios de aceite

- CA1 (R1): dado Aluno a 1024 px, quando o portal abre, então a barra tem fundo `bg-sidebar`, logo e nome da organização, as cinco abas na ordem do card, o sino, o tema e a conta; em `/portal/financeiro` só Financeiro tem `aria-current="page"`.
- CA2 (R1): dados 2 avisos não lidos, então a aba Avisos mostra o contador 2 (acima de 99 mostra 99+, zero some) e o nome acessível da aba diz a contagem.
- CA3 (R2): dado `unit` com nome, endereço e WhatsApp, então o rodapé mostra organização, unidade, endereço, WhatsApp e "feito com ctcore"; dado `unit` com campos nulos, o campo some sem deixar separador sobrando; dado `unit` nulo, só organização e "feito com ctcore".
- CA4 (R3): dado 390 px, então a barra de cima mostra marca e sino, as abas ficam fixas embaixo com 5 itens (Início, Agenda, Financeiro, Avisos, Eu) com ícone, e o conteúdo não fica escondido atrás delas (padding inferior).
- CA5 (R4): dado `color` claro reprovado no contraste (ex.: `#F5E663`), então o destaque usa `--brand`, não a cor; dado `color` escuro aprovado (ex.: `#1E3A8A`), então logo e destaque usam essa cor; dado `color` nulo ou fora do formato `#RRGGBB`, usa `--brand`; o teste calcula a razão com `contrastRatio` e exige 4,5 ou mais em todo caso renderizado.
- CA6 (R4): dado `logoUrl` nulo ou imagem quebrada, então aparece o quadrado com as iniciais do nome da organização, sem imagem quebrada.
- CA7 (R5): dada cada página do portal que usa `PortalLayout`, então existe exatamente um h1, vindo de `PageHeader`; um teste varre `features/portal/*Page.tsx` e reprova `<h1` fora de `PageHeader`.
- CA8 (R6): dado `reduced` verdadeiro, então Agenda e Avisos não aparecem nas abas (desktop e celular), o sino some, a faixa aparece no topo com ícone e o texto de antes, e abrir `/portal/agenda` leva ao Início (testes atuais de `RequireFullPortal` seguem verdes).
- CA9 (R7): dado shell carregando, com erro (o botão "Tentar de novo" refaz a chamada) e com 403, então cada estado aparece no lugar do conteúdo, com barra e abas visíveis.
- CA10 (R8): dado o código novo ou alterado, quando `tokens.test.ts` e o lint rodam, então não há hex, classe de paleta fixa nem texto literal; o axe não aponta violação nos temas claro e escuro, em 1440 e 360 px (jsdom mais visual).
- CA11 (R8): dada a moldura, então Tab percorre logo, abas, sino, conta e tema nessa ordem, e o link "Ir para o conteúdo" move o foco ao `main`.
- CA12 (R9): dado `visual/casos.ts`, então há casos do portal para Aluno e Responsável, em 1440 e 390, claro e escuro, ligados a `PortalNav`/`PortalInicio` e `PortalMTopo`/`PortalMTabs`.
- CA13 (R10): dado Aluno com unidade com endereço e telefone, quando `GET /portal/shell`, então `unit` traz nome, endereço e WhatsApp daquela unidade; dado conta da organização A, nada da organização B nem de aluno de outra conta aparece; gestão e Professor recebem 403, sem sessão 401; `OpenApiContractTest` e `ContractRoutesTest` passam.
- CA14 (todos): dada a regressão (`verify` do back-end, `lint`, `typecheck`, `build`, `test:cov`, `e2e`), então passa, inclusive `PortalLayout.test.tsx` e `portal-barra.spec.ts`.

## 4. Plano técnico

- Dados: não muda (sem migração; lê `unit.name`, `unit.address`, `unit.phone`). Seed não muda.
- API: `GET /api/v1/portal/shell` ganha `unit: { name, address, whatsapp } | null`. Unidade escolhida: a da primeira modalidade ativa do primeiro aluno ligado à conta (ordem por nome do aluno e `enrollment_modality.id`, para ser estável); sem modalidade ativa (reduzido ou sem matrícula), `null`. Só leitura, não audita. Erros e permissão não mudam. É dado público da academia, sem dado de pessoa.
- Interface: moldura compartilhada em `PortalLayout`; estados `LoadingState`, `ErrorState`, `ForbiddenState` já existem; 360 a 1440 px; dois temas. A cor da organização entra por `style={{ "--org": cor }}` só quando `isHexColor` e `passesContrast`, e as classes usam `bg-[var(--org,var(--brand))]` (sem hex).
- Arquivos vistos no repositório:
  - `backend/src/main/java/br/com/ctcore/student/PortalShell.java`: `View` ganha `Unit unit`; `view(who)` busca a unidade (SQL com `:org` e `:acc`, usando `LINKED`) dentro de `OrganizationTransactions`.
  - `backend/src/main/java/br/com/ctcore/student/PortalStudentController.java`: rota `/shell` (conferir se serializa o `View` direto; se sim, não muda).
  - `docs/openapi.yaml` (`PortalShell`, linha ~6416): campo `unit`, nulo permitido.
  - `frontend/src/features/portal/usePortalShell.ts`: `PortalShellData` ganha `unit`.
  - `frontend/src/features/portal/PortalLayout.tsx`: hoje barra clara com `border-t-4`, abas em `NavLink` e `main max-w-3xl`; passa a compor `PortalTabs`, `PortalFooter` e `OrgMark`, mantém os estados, a faixa de reduzido e `ChooseLinkTrigger`, `AccountLink`, `ThemeToggle`.
  - `frontend/src/features/portal/RequireFullPortal.tsx`, `frontend/src/features/account/AccountFrame.tsx` (ramo de portal) e `frontend/src/App.tsx` (`Home`): já usam a moldura; não mudam.
  - Páginas `PortalHomePage`, `PortalAgendaPage`, `PortalAnnouncementsPage`, `PortalCardPage`, `PortalFinancePage`, `PortalInfoPage`, `PortalRequestsPage`, `PortalTermPage`: trocam o `<h1>` próprio por `PageHeader` onde houver (troca mecânica, mesmo texto, D4).
  - `frontend/src/mocks/handlers.ts` (handler de `/portal/shell`): devolve `unit`; `frontend/src/i18n/pt-BR.json` (`portalShell`): `tabsShort.me`, `footer.*`, `skip`.
  - Testes ajustados: `PortalLayout.test.tsx`, `PortalHomePage.test.tsx`, `PortalStates.test.tsx` e os que contam cabeçalhos; `backend/src/test/java/br/com/ctcore/student/PortalShellIT.java`.
- A criar: `frontend/src/features/portal/PortalTabs.tsx` (abas desktop e celular, ícones `lucide-react`, contador), `PortalFooter.tsx`, `OrgMark.tsx` (logo ou iniciais, cor aprovada), testes de cada um, `portalPages.test.ts` (varre h1 e tokens, CA7 e CA10), casos em `frontend/visual/casos.ts` e logins de aluno e responsável no MSW se faltarem.
- Reuso: `passesContrast` e `isHexColor` (`features/organization/contrastRatio.ts`), `Avatar`, `Badge`, `PageHeader` e `Banner` da #415, tokens `sidebar*` e `brand` da #414, `ThemeToggle`, `AccountLink`, `axeViolations`, padrão de `MobileTabs` e `Topbar` da #417.
- Segurança: `unit` só é lida depois do portão de perfil e termo que a rota já tem; SQL só com parâmetros, em `OrganizationTransactions.execute` (RLS); a resposta não traz dado de aluno. A cor só entra no estilo no formato `#RRGGBB`.
- Riscos: testes de `Home` e `AccountFrame` contam cabeçalhos e textos do portal e podem quebrar; abas fixas a 360 px podem cobrir o fim da página (padding inferior); referências visuais do portal ainda não existem (geradas no container); `unit.phone` pode não ser WhatsApp (D2).

## 5. Tarefas

- [x] T1: `PortalShell.view` devolve `unit`; `PortalShellIT` com unidade, isolamento e perfis; OpenAPI — cobre CA13 — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test=PortalShellIT -Dtest='OpenApiContractTest,ContractRoutesTest' -Dsurefire.failIfNoSpecifiedTests=false -Djacoco.skip=true` (Docker ativo)
- [x] T2: `usePortalShell` com `unit`, handler MSW e textos `portalShell` — cobre CA3 (dados) — prova: em `frontend/`, `npm test -- portal-shell.msw usePortalShell`
- [x] T3: `OrgMark`, `PortalTabs` (desktop e celular, ícones, contador) e `PortalFooter` — cobre CA2, CA3, CA5, CA6 — prova: `npm test -- OrgMark PortalTabs PortalFooter`
- [x] T4: `PortalLayout` compondo as peças (barra escura, barra de cima do celular, "Ir para o conteúdo", faixa de reduzido, estados) e ajuste de `PortalLayout.test.tsx` — cobre CA1, CA4, CA8, CA9, CA11 — prova: `npm test -- PortalLayout RequireFullPortal`
- [x] T5: `PageHeader` nas páginas do portal, teste de varredura de h1 e tokens, axe nos dois temas — cobre CA7, CA10 — prova: `npm test -- portalPages tokens a11y` e `npm run typecheck`
- [x] T6: casos do portal em `visual/casos.ts`, referências geradas no container e regressão completa — cobre CA12, CA14 — prova: `npm run test:visual:docker -- --update-snapshots`; `npm run lint && npm run typecheck && npm run build && npm run test:cov && npm run e2e`; `./backend/mvnw -f backend/pom.xml verify`

## 6. Decisões

- D1 (muda o comportamento): o rodapé pede unidade, endereço e WhatsApp, que o shell não traz; a rota `/portal/shell` ganha `unit` em vez de nova rota. Alternativa: mostrar só a organização, descartada porque o card pede os três.
- D2: o WhatsApp do rodapé vem de `unit.phone` (a unidade só tem esse campo de telefone). Suposição: se o telefone da unidade não for WhatsApp, o rótulo fica impreciso; um campo próprio seria decisão de uma sprint de configuração, não deste item.
- D3 (muda o comportamento): cor da organização reprovada no contraste AA não é aplicada e usa o `--brand`. Hoje a configuração já recusa cor reprovada (`ColorField`, RNF-22), então é defesa contra dado antigo. Alternativa: escurecer a cor automaticamente, descartada por mudar a cor escolhida.
- D4: o `PageHeader` entra só onde a página já tinha título; não se escreve subtítulo novo (como a D7 da #417).
- D5: a regra de acesso reduzido (`reduced`) é da #88 e não muda; só o visual da faixa. O estado "bloqueado por atraso" do canvas `PortalReduzido` fica para a Sprint 12, que cuida do dado.
- D6: o corte do celular continua em `md` (768 px), igual à #88 e à #417; a aba "Minhas informações" vira "Eu" só no celular, como no canvas.
- D7: o contador do canvas na aba "Minhas informações" não é implementado: o card lista contador só em Avisos e não há regra no sistema.
- Suposição: o `Banner` da #415 serve para a faixa de reduzido; se não servir, mantém-se a marcação atual com as classes novas.

## Construção

- T1 — arquivos: `backend/.../student/PortalShell.java` (`View.unit`, `Unit`, consulta `unit(who)`), `PortalShellIT.java` (teste `unit`), `docs/openapi.yaml` — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test=PortalShellIT -Dtest='OpenApiContractTest,ContractRoutesTest' ...` → PortalShellIT 7/7, contrato 7/7, BUILD SUCCESS
- T2 — arquivos: `usePortalShell.ts`, `mocks/handlers.ts`, `mocks/portal-shell.msw.test.ts`, `i18n/pt-BR.json` (`tabsShort`, `footer`, `skip`, `unread`, `navBottom`) — prova: `npm test -- portal-shell.msw usePortalShell` → 5/5
- T3 — arquivos: `OrgMark.tsx`, `PortalTabs.tsx`, `PortalFooter.tsx` e testes (`OrgMark.test.tsx` cobre OrgMark e PortalTabs, `PortalFooter.test.tsx`); token `--brand-foreground` em `index.css` — prova: `npm test -- OrgMark PortalTabs PortalFooter` → 11/11
- T4 — arquivos: `PortalLayout.tsx`, `PortalLayout.test.tsx`; e2e `portal-barra.spec.ts` e `portal-minhas-informacoes.spec.ts` (a 360 px valem as abas de baixo, com "Eu") — prova: `npm test -- PortalLayout RequireFullPortal` → verde (19 testes do layout)
- T5 — arquivos: 8 páginas `Portal*Page.tsx` (`PageHeader`), `portalPages.test.ts` — prova: `npm test -- portalPages tokens a11y` → verde; `npm run typecheck` → ok; axe dos dois temas em jsdom (`PortalLayout.test`) e no navegador (e2e a 360 e 1024 px)
- T6 — arquivos: `visual/casos.ts` (6 casos do portal), login `aluno@exemplo.com` no MSW, 24 referências novas em `visual/referencias/portal-*` — prova: `npm run test:visual:docker -- --update-snapshots` → 144 passaram; nenhuma referência antiga mudou; imagem do Playwright já existia e a lista de imagens ficou igual
Regressão: `./backend/mvnw -f backend/pom.xml verify` → 861 testes e 162 de unidade, 0 falhas, BUILD SUCCESS (JaCoCo ok); `npm run lint && npm run typecheck && npm run build` → ok; `npm run test:cov` → 185 arquivos, 1639 testes, ok; `npm run e2e` → 81 passaram. Sem falha anterior.
Desvios:
- D2 do plano: a unidade já tem a coluna `whatsapp` (V3), então `unit.whatsapp` vem dela e não de `unit.phone`.
- `unit` fica nulo também no portal reduzido (sem modalidade ativa), como o plano prevê; no TS ele é opcional só para não mexer em testes antigos que montam a moldura sem ele.
- Nome acessível da aba Avisos: sem `aria-label` (o axe `label-content-name-mismatch` reprovou); o contador tem texto só para leitor de tela ("2 não lidos"). O sino mantém `aria-label` "Avisos, N não lidos".
- Abas de baixo têm `aria-label` "Portal, abas", distinto da barra ("Portal"), por serem duas navegações no DOM; o e2e a 360 px passou a esperar "Eu".
- Iniciais do logo saem por CSS (`data-initials`), para não entrarem no nome do link da marca.
- Novo token `--brand-foreground` (branco nos dois temas), porque `primary-foreground` escurece no tema escuro e a cor da organização é aprovada contra branco.
- Faixa de acesso reduzido mantém a marcação própria com ícone de cadeado (não usa `Banner`: o papel `status` e o texto não mudam).
Não é do item: nada.

## Verificação

### Rodada 1 — OK — 2026-10-29, estado 20e622d0f1db4ce19a87cd712ef3e52ad2858ad0

| Critério | Status | Evidência |
|---|---|---|
| CA1, CA2 | PASSOU | `PortalLayout.test.tsx`, `OrgMark.test.tsx` (abas, aria-current, contador 99+/zero) verdes; e2e `portal-barra` verde |
| CA3 | PASSOU | `PortalFooter.test.tsx` verde; leitura de `PortalFooter.tsx` (filter(Boolean) sem separador sobrando) |
| CA4 | PASSOU | `PortalTabs.tsx` (5 abas fixas md:hidden, ícones), rodapé com `pb-20`; e2e a 360 px verde |
| CA5, CA6 | PASSOU | `OrgMark.tsx` (isHexColor + passesContrast, iniciais no erro); `OrgMark.test.tsx` verde |
| CA7 | PASSOU | `portalPages.test.ts` verde |
| CA8, CA9, CA11 | PASSOU | `PortalLayout.test.tsx` e `RequireFullPortal` verdes; e2e `portal-avisos` reduzido verde |
| CA10 | PASSOU | lint ok; `portalPages.test.ts` (sem cor fixa); axe em `PortalLayout.test` e e2e |
| CA12 | PASSOU | `visual/casos.ts` com 6 casos e 24 referências no diff (não reexecutado o visual no container) |
| CA13 | PASSOU | `PortalShellIT` 7/7, `OpenApiContractTest` 4/4, `ContractRoutesTest` ok, BUILD SUCCESS |
| CA14 | PASSOU | lint, typecheck, build ok; `test:cov` 185 arquivos/1639 testes; `e2e` 81 passaram. `verify` completo do back-end não reexecutado (só IT do item e contrato; Construção registra 861 testes verdes) |

Comandos: `mvnw verify -Dit.test=PortalShellIT -Dtest='OpenApiContractTest,ContractRoutesTest'` ok; `npm run lint && npm run typecheck` ok; `npx vitest run src/features/portal src/mocks/portal-shell.msw.test.ts` 189/189; `npm run build` ok; `npm run test:cov` ok; `npm run e2e` 81/81.
Instáveis: nenhum.
Achados:
- sugestão, código, `PortalLayout.tsx` — o sino e o link "Início" não são problemas; nenhuma ressalva além de: o `.visual` comparado só pelos PNGs gerados, sem reexecução nesta rodada.

## Entrega

- O que muda: barra superior escura com a marca da organização (`OrgMark`; a cor da organização só entra quando passa no contraste), abas de baixo no celular (`PortalTabs`, a partir de 360 px, com "Eu"), rodapé (`PortalFooter`), faixa de acesso reduzido com marcação própria e as oito páginas `Portal*Page` com `PageHeader`. Back-end: o shell do portal passa a devolver `unit` (o `whatsapp` vem da coluna da V3). Novo token `--brand-foreground`.
- Verificação: rodada 1 OK (estado 20e622d). Back-end: `PortalShellIT` 7/7, contrato 7/7 e `verify` completo registrado na construção (861 testes, sem falha). Front: lint, typecheck, build, `test:cov` (1639 testes), e2e 81/81 e 24 referências visuais novas do portal, sem referência antiga alterada.
- Decisões: `unit` fica nulo no portal reduzido (sem modalidade ativa), como o plano prevê; a aba Avisos não tem `aria-label` (o axe reprovou), e o contador tem só texto para leitor de tela; as abas de baixo têm `aria-label` "Portal, abas"; as iniciais do logo saem por CSS (`data-initials`).
- Limitações: o `verify` completo do back-end não foi reexecutado na verificação, só o recorte do item e o contrato; a comparação visual não foi reexecutada na rodada 1. O CI do PR roda a suíte completa.
