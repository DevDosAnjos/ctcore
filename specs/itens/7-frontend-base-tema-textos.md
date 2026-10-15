# 7 — Projeto front-end, tema claro e escuro e textos em pt-BR

Origem: issue #7 (Sprint 00 · Fundação), RNF-21, RNF-24 e RNF-33 em `docs/especificacao/00-visao-geral.md`. Branch: `item/7-frontend-base`.

## 1. Objetivo

Criar o projeto `frontend/` (Vite, React, TypeScript, Tailwind, shadcn/ui, Radix, MSW, Vitest, Testing Library, axe) com tema claro e escuro que segue o aparelho e tem botão de troca, e com todos os textos de tela em arquivos de tradução pt-BR. É a base sobre a qual #8, #9 e as telas das sprints seguintes são construídas.

Fora do escopo: componentes base e padrões de tela (#8); campos e formatos brasileiros e botão de WhatsApp (#9); esteira de CI (#10, por isso o "CI verde" do "Pronto quando" vale a partir dela); telas de negócio, rotas reais e login; handlers MSW de rotas de negócio (o OpenAPI de `docs/openapi.yaml` ainda é só o esqueleto da #3); Playwright e PWA.

## 2. Requisitos

- R1: `frontend/` é um projeto Vite + React + TypeScript (modo estrito) com Tailwind, shadcn/ui e Radix, e sobe com `npm run dev`, compila com `npm run build` e roda os testes com `npm test` (D1, D2).
- R2: MSW está ligado em desenvolvimento e nos testes, com handlers que seguem o OpenAPI de `docs/openapi.yaml` (D3).
- R3: tokens de cor e de tipografia existem como variáveis CSS, com valores para tema claro e para escuro, consumidos pelo Tailwind e pelo shadcn/ui (RNF-21).
- R4: o tema inicial segue o aparelho (`prefers-color-scheme`) e há um botão para trocar entre claro e escuro; a escolha da pessoa vale nas próximas visitas e não causa flash do tema errado ao carregar (RNF-21) (D4).
- R5: todo texto de tela vem de arquivos de tradução pt-BR (`i18next`), nunca escrito no código; uma verificação automática falha se achar texto literal em JSX (D5).
- R6: os textos do projeto tratam por "você", em frases curtas e cordiais, sem gíria e sem "prezado"; as mensagens de erro dizem o que aconteceu, o que foi ou não alterado e o que fazer; texto de cobrança nunca é ameaça (RNF-24) (D6).
- R7: o ambiente de teste de componentes e telas (Vitest, Testing Library, MSW, axe) existe e traz um teste-modelo dos quatro estados de tela: carregando, vazio, erro e sem permissão por perfil (RNF-33) (D7).
- R8: a tela inicial mínima (casca) funciona a partir de 360 px e passa no axe nos dois temas.
- R9: `frontend/README.md`, `README.md` raiz e a seção "Comandos" do `AGENTS.md` dizem como subir, construir e testar o front-end.

## 3. Critérios de aceite

- CA1 (R1): dado `frontend/` com dependências instaladas, quando se roda `npm run build`, `npm run lint` e `npm run typecheck`, então os três terminam com código 0.
- CA2 (R2): dado o MSW iniciado pelo ambiente de teste, quando um teste registra um handler de teste (`server.use`) e chama essa rota, então recebe a resposta dele, e uma chamada a rota sem handler faz o teste falhar (`onUnhandledRequest: "error"`).
- CA3 (R2): dado o `docs/openapi.yaml`, quando um teste confere os caminhos dos handlers MSW, então todo handler da lista base (`src/mocks/handlers.ts`) corresponde a um caminho existente no OpenAPI (hoje `paths` está vazio, então a lista base é vazia; o teste passa a valer sozinho quando as rotas forem declaradas).
- CA4 (R3): dado o CSS global, quando se lê `:root` e `.dark`, então ambos definem os mesmos tokens de cor (fundo, texto, primária, borda, erro, foco e demais do shadcn/ui) e de tipografia (família e escala), verificado por teste.
- CA5 (R4): dado um aparelho com preferência escura e nenhuma escolha salva, quando a aplicação abre, então `<html>` tem a classe `dark`; com preferência clara, não tem.
- CA6 (R4): dada a aplicação aberta, quando a pessoa aciona o botão de tema, então o tema alterna entre claro e escuro, o botão tem nome acessível e a escolha fica salva; ao recarregar, o tema salvo prevalece sobre o do aparelho.
- CA7 (R4): dada uma leitura do armazenamento do navegador que falha ou está vazia, quando a aplicação abre, então segue o aparelho sem erro.
- CA8 (R5): dado o código de `frontend/src` (exceto testes e arquivos de tradução), quando roda a verificação de texto literal, então não há texto de tela em JSX nem em atributos de texto (`aria-label`, `title`, `placeholder`, `alt`) fora das traduções.
- CA9 (R5): dada uma chave de tradução inexistente, quando um componente a usa em teste, então o teste falha (configuração que trata chave ausente como erro).
- CA10 (R6): dado o arquivo de tradução pt-BR, quando um teste o varre, então nenhum texto contém "prezado", gírias da lista do teste, nem verbo de ameaça de cobrança da lista do teste, e os textos de erro trazem as três partes (o que aconteceu, o que foi ou não alterado, o que fazer), conferido pela estrutura das chaves de erro (D6).
- CA11 (R7): dado o teste-modelo com MSW, quando a rota responde lenta, vazia, com erro 500 ou 403, então a tela mostra, respectivamente, carregando, vazio, erro com ação de tentar de novo e "sem permissão", com texto de `pt-BR`.
- CA12 (R7): dado o mesmo teste-modelo, quando roda com perfis diferentes (permitido e negado), então o conteúdo restrito aparece só para o perfil permitido (o servidor continua sendo a barreira real; o teste só cobre a tela).
- CA13 (R8): dada a tela inicial renderizada em largura 360 px, quando o axe roda no tema claro e no escuro, então não há violação, e não existe rolagem horizontal (largura do conteúdo menor ou igual a 360 px) (D8).
- CA14 (R9): dados `frontend/README.md`, `README.md` e `AGENTS.md`, quando se leem, então trazem os comandos de instalar, subir, construir, lint, tipos e testar o front-end.

## 4. Plano técnico

- Dados: não muda.
- API: não muda `/api/v1` nem `docs/openapi.yaml`; o front só consome. `paths` do OpenAPI está vazio hoje (visto em `docs/openapi.yaml`), então a lista base de handlers nasce vazia e cada item que declarar rota acrescenta o handler dela.
- Interface: casca mínima (cabeçalho com título e botão de tema, área de conteúdo) apenas para provar tema, tradução, largura e axe; sem rotas de negócio. Os quatro estados ficam num exemplo em `src/` usado só pelo teste-modelo, para a #8 transformar em padrão (D7). Larguras: a partir de 360 px (`specs/projeto.md`). Acessibilidade: foco visível, nome acessível no botão de tema, contraste dos tokens conferido pelo axe nos dois temas.
- Segurança: sem dados pessoais nem credenciais; o front não é barreira de permissão, o teste de perfil só cobre a exibição. Nada de segredo no `VITE_*`. Armazenamento do navegador guarda só a escolha de tema.
- Arquivos: a alterar (existem): `frontend/README.md`, `README.md`, `AGENTS.md` (seção Comandos), `.gitignore` (já ignora `frontend/node_modules`, `dist`, `coverage`). A criar em `frontend/`: `package.json` (com lockfile), `tsconfig*.json`, `vite.config.ts` (proxy `/api` para o back-end local e configuração do Vitest), `index.html` (script curto no `<head>` que aplica o tema antes da pintura), `eslint.config.js`, `components.json` (shadcn/ui), `src/main.tsx`, `src/App.tsx`, `src/index.css` (Tailwind e tokens claro/escuro), `src/lib/utils.ts`, `src/theme/` (provedor e botão de tema), `src/i18n/` (`index.ts`, `pt-BR.json`), `src/mocks/` (`handlers.ts`, `browser.ts`, `server.ts`), `src/test/setup.ts` e os testes ao lado do código ou em `src/test/` (`theme.test.tsx`, `i18n.test.ts`, `tone.test.ts`, `msw.test.ts`, `screen-states.test.tsx`, `a11y.test.tsx`), mais o script de verificação de texto literal (regra de ESLint de texto literal em JSX, ver D5).
- Reuso: o OpenAPI de `docs/openapi.yaml` como fonte dos caminhos dos handlers; shadcn/ui só para o que a casca usa (`Button`) e `lucide-react` para o ícone do botão de tema. Nada do back-end é reutilizado.
- Riscos: (1) versões de Vite, React, Tailwind e shadcn/ui mudam entre major versions; fixar versões no lockfile e usar as estáveis na data da construção. (2) jsdom não calcula contraste de cor real, então o axe no jsdom não confirma contraste; o contraste dos tokens fica coberto por conferência na construção com tokens do shadcn/ui, que já têm contraste adequado, e por teste de razão de contraste dos pares fundo/texto escrito sobre os tokens (D8). (3) regra de texto literal pode dar falso positivo (nomes de classe, símbolos); lista de exceções curta. (4) MSW em jsdom exige as APIs `fetch` do Node 18+; Node 24 está instalado.

## 5. Tarefas

- [x] T1: criar o projeto Vite + React + TypeScript estrito com Tailwind, shadcn/ui (`components.json`, `Button`, `lib/utils.ts`), scripts `dev`, `build`, `lint`, `typecheck`, `test`, proxy `/api` e `.gitignore` conferido — cobre CA1 — prova: `npm ci && npm run build && npm run lint && npm run typecheck` em `frontend/`.
- [x] T2: definir os tokens de cor e tipografia em `src/index.css` para `:root` e `.dark` e ligá-los ao Tailwind — cobre CA4 — prova: teste de tokens (`theme.test.tsx`).
- [x] T3: implementar o tema (`src/theme/`): preferência do aparelho, botão com nome acessível, escolha salva com tolerância a falha do armazenamento, script no `index.html` contra flash — cobre CA5, CA6, CA7 — prova: `theme.test.tsx`.
- [x] T4: configurar `i18next`/`react-i18next` com `pt-BR.json` e chave ausente como erro; escrever os textos da casca e as mensagens-modelo (carregando, vazio, erro, sem permissão) no tom de RNF-24; adicionar a regra de ESLint de texto literal em JSX — cobre CA8, CA9, CA10 — prova: `i18n.test.ts`, `tone.test.ts` e `npm run lint`.
- [x] T5: configurar o MSW (`handlers`, `browser` para desenvolvimento, `server` para testes, `src/test/setup.ts` com `onUnhandledRequest: "error"`) com a lista base de handlers (vazia hoje, seguindo o OpenAPI), e o teste que confere os caminhos contra `docs/openapi.yaml` — cobre CA2, CA3 — prova: `msw.test.ts`.
- [x] T6: escrever o teste-modelo dos quatro estados e dos perfis (`screen-states.test.tsx`) com um componente de exemplo — cobre CA11, CA12 — prova: `npm test` em `frontend/`.
- [x] T7: montar a casca (`App.tsx`) e conferir 360 px e axe nos dois temas (`a11y.test.tsx`) — cobre CA13 — prova: `npm test` em `frontend/`.
- [x] T8: atualizar `frontend/README.md`, `README.md` e a seção "Comandos" do `AGENTS.md` e rodar tudo com cobertura — cobre CA14 — prova: leitura dos arquivos e `npm run build && npm run lint && npm run typecheck && npm test -- --coverage` verdes.

## 6. Decisões

- D1: gerenciador de pacotes `npm` (já instalado, Node 24), com `package-lock.json` versionado. Motivo: nada a instalar a mais e o CI da #10 usa `npm ci`. Alternativa: pnpm, descartada por exigir instalação extra.
- D2: Vite com plugin `@vitejs/plugin-react` e Tailwind pelo plugin oficial do Vite, nas versões estáveis na data da construção, fixadas no lockfile. Motivo: é o que a issue e `specs/projeto.md` pedem. Alternativa: Next.js, descartada por não estar na stack.
- D3: MSW em desenvolvimento liga apenas com `import.meta.env.DEV` e `VITE_USE_MSW` não desligado; a lista base de handlers hoje é vazia, pois o OpenAPI não tem rotas. Motivo: a issue manda ligar MSW em desenvolvimento e testes, mas as rotas de negócio ainda não têm contrato. Alternativa: inventar rotas de negócio nos handlers, descartada pelo AGENTS.md (OpenAPI é a fonte única).
- D4: tema guardado em `localStorage` com chave única; três estados lógicos (claro, escuro, seguir aparelho) com o botão alternando claro/escuro. Motivo: RNF-21 pede seguir o aparelho com botão para trocar, sem exigir menu de três opções. Alternativa: menu de três opções, descartada por ser mais interface do que o item pede. Suposição: a escolha é por navegador, não por conta (não há conta ainda); se a preferência tiver de acompanhar a pessoa entre aparelhos, será item da tela "Minha conta" (#36).
- D5: biblioteca de tradução `i18next` com `react-i18next`, único idioma `pt-BR` hoje; a "nenhum texto no código" é imposta por regra de ESLint (texto literal em JSX e em atributos de texto) que roda em `npm run lint`. Motivo: verificação automática no lugar de revisão manual. Alternativa: só convenção e revisão, descartada por não falhar sozinha.
- D6: RNF-24 é verificada por teste sobre `pt-BR.json`: lista curta de termos proibidos (prezado, gírias, ameaças de cobrança) e estrutura obrigatória das mensagens de erro em três chaves (`aconteceu`, `alterado`, `fazer`). O tom em si continua sendo revisão humana nos itens de tela; o teste só pega o mais objetivo. Alternativa: sem teste, descartada porque a issue pede um teste por código.
- D7: o teste-modelo dos quatro estados usa um componente de exemplo mínimo dentro do próprio teste ou de `src/test/`, não um padrão de tela publicado; o padrão compartilhado (estado vazio, erro, sem permissão como componentes) é da #8. Motivo: separar fundação de componentes base. Alternativa: já criar `StateView` reutilizável aqui, descartada por invadir a #8.
- D8: o axe roda em jsdom nas duas classes de tema; como jsdom não mede contraste, a regra `color-contrast` do axe fica desligada nesse ambiente e é coberta por um teste de razão de contraste (mínimo 4,5:1) dos pares texto/fundo dos tokens em `:root` e `.dark`. Alternativa: axe em navegador real via Playwright, descartada porque Playwright não entra neste item (largura de 360 px também é conferida em jsdom pela ausência de largura fixa maior que 360 px e pelo uso de classes responsivas). Suposição: se a conferência em navegador real for exigida já nesta sprint, entra na #10 (CI) com Playwright.
- Suposição: "esteira de CI verde" do "Pronto quando" só passa a valer quando a #10 existir; este item deixa os comandos prontos para ela.
- Suposição: como não há rota no OpenAPI, o teste-modelo (CA11, CA12) usa rotas de exemplo registradas só no teste; elas não entram em `handlers.ts` nem no contrato.

## Construção

- T1 — arquivos: `frontend/package.json`, `package-lock.json`, `tsconfig*.json`, `vite.config.ts`, `index.html`, `eslint.config.js`, `components.json`, `src/lib/utils.ts`, `src/components/ui/button.tsx`, `src/main.tsx` — prova: `npm ci && npm run build && npm run lint && npm run typecheck` em `frontend/` → os quatro com código 0
- T2 — arquivos: `src/index.css` — prova: `npm test` (`theme.test.tsx`: mesmos tokens em `:root` e `.dark`, contraste 4,5:1 dos pares) → passou
- T3 — arquivos: `src/theme/{theme.ts,ThemeProvider.tsx,ThemeToggle.tsx}`, script em `index.html`, `src/theme/theme.test.tsx` — prova: `npm test` → passou
- T4 — arquivos: `src/i18n/{index.ts,pt-BR.json,i18n.test.ts,tone.test.ts}`, regra `i18next/no-literal-string` em `eslint.config.js` — prova: `npm test` e `npm run lint` → passaram; conferido à mão que `<button title="..">texto</button>` num arquivo temporário (removido) falha no lint
- T5 — arquivos: `src/mocks/{handlers,browser,server}.ts`, `src/mocks/msw.test.ts`, `src/test/{setup,device}.ts`, `public/mockServiceWorker.js` (`npx msw init`) — prova: `npm test` → passou
- T6 — arquivos: `src/test/{ExampleScreen.tsx,screen-states.test.tsx}` — prova: `npm test` → passou
- T7 — arquivos: `src/App.tsx`, `src/test/{a11y.test.tsx,axe.ts}` — prova: `npm test` → passou
- T8 — arquivos: `frontend/README.md`, `README.md`, `AGENTS.md` — prova: `npm run build && npm run lint && npm run typecheck && npm test -- --coverage` → verdes; 6 arquivos, 23 testes passaram; cobertura 92,85% de instruções, 85,71% de ramos

Regressão: sem linha de base para o front-end (projeto novo); back-end não foi alterado e não foi rodado.
Desvios:
- MSW 3 renomeou `onUnhandledRequest` para `onUnhandledFrame`; usado `server.listen({ onUnhandledFrame: "error" })` (mesmo efeito, CA2).
- Regra de texto literal vem de `eslint-plugin-i18next` (modo `jsx-only`, atributos `aria-label`, `title`, `placeholder`, `alt`) em vez de regra própria. Ela ignora constantes nomeadas em MAIÚSCULAS; aceitável.
- Chave ausente lança erro só fora de produção (`import.meta.env.PROD`); em produção mostra a chave.
- Tokens em hexadecimal (não oklch) para o teste de contraste ler direto.
- Larguras de 360 px: conferidas no jsdom por ausência de `w-[Npx]` maior que 360 e viewport no `index.html` (D8).
- Atualizada também a linha "A definir nas fundações #7 e #10" do `AGENTS.md` (a seção Comandos já tinha outro formato do que a spec descrevia).
Não é do item: `specs/itens/7-...md` e `AGENTS.md` sem commit (fase 1/2).

## Verificação

### Rodada 1 — OK — 2026-10-15, estado 8e180a0e958c5385fe36d6860df8236432854daa

| Critério | Status | Evidência |
|---|---|---|
| CA1 | PASSOU | `npm ci`, `npm run build`, `npm run lint`, `npm run typecheck` com código 0 |
| CA2 | PASSOU | `msw.test.ts` (handler de teste responde; rota sem handler rejeita) |
| CA3 | PASSOU | `msw.test.ts` contra `docs/openapi.yaml`; lista base vazia, como previsto |
| CA4 | PASSOU | `theme.test.tsx` (mesmos tokens em `:root` e `.dark`, contraste 4,5:1) |
| CA5 | PASSOU | `theme.test.tsx` (aparelho escuro e claro sem escolha salva) |
| CA6 | PASSOU | `theme.test.tsx` (botão, nome acessível, salvo vence ao recarregar) |
| CA7 | PASSOU | `theme.test.tsx` (armazenamento com falha ou vazio) |
| CA8 | PASSOU | `npm run lint` com `i18next/no-literal-string` (jsx-only, atributos); sem violação |
| CA9 | PASSOU | `i18n.test.ts` (chave inexistente lança) |
| CA10 | PASSOU | `tone.test.ts` (termos proibidos e três partes do erro) |
| CA11 | PASSOU | `screen-states.test.tsx` (carregando, vazio, 500 com tentar de novo, 403) |
| CA12 | PASSOU | `screen-states.test.tsx` (perfis) |
| CA13 | PASSOU | `a11y.test.tsx` (axe nos dois temas; 360 px por proxy em jsdom, conforme D8) |
| CA14 | PASSOU | leitura de `AGENTS.md` (Comandos), `README.md` e `frontend/README.md` |

Comandos: `npm ci` ok; build 0; lint 0; typecheck 0; `npm test -- --coverage` 6 arquivos, 23 testes passaram, cobertura 92,85% instruções / 85,71% ramos. Back-end não tocado, não rodado. Sem linha de base do front-end (projeto novo).
Achados:
- sugestão, código, `frontend/src/i18n/index.ts` linha 16 e `src/mocks/handlers.ts` linha 17 sem cobertura — ramo de produção da chave ausente e resolver de rota; sem impacto nos critérios.
- sugestão, código, CA13 "sem rolagem horizontal" é conferido só por proxy (ausência de largura fixa > 360 px), pois jsdom não faz layout; aceito em D8, vale conferir em navegador real na #10.

## Entrega

Projeto front-end (Vite, React, TypeScript, Tailwind, shadcn/ui) com tema claro e escuro, textos em pt-BR por i18next e MSW ligado em desenvolvimento e testes.

- Tema segue o aparelho, com botão claro/escuro e escolha salva por navegador (D4).
- Texto literal em JSX falha no lint; chave de tradução ausente lança erro fora de produção.
- Verificação: build, lint, typecheck e `npm test -- --coverage` verdes (23 testes; 92,85% instruções, 85,71% ramos); axe nos dois temas.
- Limitações: largura de 360 px conferida por proxy em jsdom e contraste por teste de tokens (D8); lista de handlers MSW vazia, pois o OpenAPI não tem rotas de negócio ainda. Conferência em navegador real fica para a #10.
