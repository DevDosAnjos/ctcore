# 8 — Componentes base e padrões de tela

Origem: issue #8 (Sprint 00 · Fundação), RNF-19, RNF-20, RNF-25, RNF-26, RNF-27 e RNF-34 em `docs/especificacao/00-visao-geral.md`. Branch: `item/8-estados-de-tela`.

## 1. Objetivo

Entregar a biblioteca de componentes e padrões de tela que todas as telas das sprints seguintes vão reutilizar: layout com menu lateral e topo, consulta (filtros e tabela com paginação no endereço), painel lateral, janela, etapas, cartões, os quatro estados de tela, janelas de confirmação (com resumo, motivo e confirmação digitada), contador de caracteres e campos de data, moeda, telefone e CPF no formato brasileiro. Cada componente nasce responsivo a partir de 360 px, acessível e conferido no axe nos dois temas.

Fora do escopo: datas relativas e botão de WhatsApp (#9); telas e rotas de negócio, login e roteador de páginas; formatação de exibição de data, moeda e telefone fora dos campos (#9 e telas); Playwright e conferência em navegador real (#10); handlers MSW de negócio (o OpenAPI ainda não tem rotas).

O item é construído em três partes, cada uma numa sessão: **A** (base: estados, layout, cartões, contador), **B** (padrões: consulta, painel, janela, confirmações, etapas), **C** (campos brasileiros).

## 2. Requisitos

- R1: o layout tem menu lateral (fixo em tela larga, recolhível em gaveta no celular, com o grupo do item em uso aberto e destacado) e topo com o botão de tema, funcionando de 360 px a telas grandes (RNF-19, D1).
- R2: os quatro estados de tela existem como componentes únicos reutilizáveis: carregando, vazio (uma frase e a ação principal), erro (com "Tentar de novo", no tom do RNF-24) e sem permissão (RNF-25, D2).
- R3: o padrão de consulta combina filtros e tabela, com busca, filtros, ordenação e página guardados no endereço (query string), de modo que recarregar ou compartilhar o link reproduz a mesma consulta; a tabela tem paginação e nunca carrega todos os registros (RNF-27, D3).
- R4: a consulta mostra também o estado "busca sem resultado", distinto do vazio, com ação de limpar filtros (RNF-25, D2).
- R5: o painel lateral abre ao lado em tela larga e em tela cheia no celular, com foco preso, fecha com Esc e devolve o foco a quem o abriu (RNF-27, RNF-20).
- R6: a janela (modal) tem título, foco preso, fecha com Esc e devolve o foco (RNF-27, RNF-20).
- R7: a janela de confirmação mostra o resumo do que vai acontecer, pede motivo quando configurada para isso e só habilita o botão de confirmar quando o motivo é válido; durante o envio impede duplo clique (RNF-26, D4).
- R8: a confirmação digitada exige digitar o texto pedido exatamente antes de habilitar o botão; é opção do mesmo componente, que as telas de cancelar assinatura, transferir coordenação, anonimizar aluno e desativar unidade usarão (RNF-26, D4).
- R9: o componente de etapas mostra a etapa atual e o total, permite voltar e avançar, e só avança quando a etapa é válida (RNF-27).
- R10: o cartão agrupa informação com título e ações, e a grade de cartões se reorganiza de uma coluna (360 px) a várias (RNF-27).
- R11: o contador de caracteres mostra usados/máximo, avisa por leitor de tela ao se aproximar do limite e impede passar dele.
- R12: os campos de data (dd/mm/aaaa), moeda (R$ 1.250,00, valor em centavos), telefone (com DDD) e CPF (000.000.000-00, com dígitos verificadores) formatam enquanto se digita, entregam ao formulário o valor limpo e mostram erro de validação com texto acessível (D5).
- R13: todos os componentes têm teclado completo, foco visível, nome acessível, situação nunca só por cor, alvos de toque de 44 px e não quebram com zoom de 200% (RNF-20).
- R14: todos os componentes passam no axe nos dois temas e funcionam a partir de 360 px (RNF-19, RNF-34, D6).
- R15: todo texto dos componentes vem de `pt-BR.json`; textos padrão podem ser sobrescritos por quem usa (D7).
- R16: `frontend/README.md` documenta onde ficam os componentes e como usá-los.

## 3. Critérios de aceite

- CA1 (R1): dado o layout em 1280 px, quando renderizado, então o menu lateral está visível, o item ativo tem `aria-current="page"` e o grupo dele está aberto.
- CA2 (R1): dado o layout em 360 px, quando a pessoa aciona o botão de menu, então a gaveta abre com foco dentro, Esc a fecha e o foco volta ao botão; não há largura fixa que cause rolagem horizontal (D6).
- CA3 (R2): dados os componentes de estado, quando cada um é renderizado, então carregando tem `role="status"`, vazio mostra uma frase e a ação principal, erro mostra as três partes do texto e o botão "Tentar de novo" que chama a função recebida, e sem permissão não mostra o conteúdo protegido.
- CA4 (R2): dada uma tela de exemplo com MSW, quando a rota responde lenta, vazia, 500 ou 403, então aparecem respectivamente carregando, vazio, erro e sem permissão, já com os componentes novos (o `ExampleScreen` da #7 passa a usá-los).
- CA5 (R3): dada a consulta aberta em `?q=ana&pagina=2&ordem=nome`, quando renderizada, então busca, página e ordenação refletem o endereço; ao filtrar, o endereço é atualizado sem recarregar e a página volta para 1.
- CA6 (R3): dada uma página fora do intervalo ou parâmetro inválido no endereço, quando a consulta abre, então usa a primeira página e os padrões, sem erro.
- CA7 (R3): dada a tabela, quando há mais registros que o tamanho da página, então só a página atual é pedida e renderizada, e a paginação mostra "página X de Y" com botões desabilitados nos extremos.
- CA8 (R4): dada uma consulta com filtro ativo e zero resultados, quando renderizada, então mostra "busca sem resultado" com o botão "Limpar filtros", que zera o endereço; sem filtro e com zero registros mostra o vazio de R2.
- CA9 (R5): dado o painel lateral aberto, quando a pessoa pressiona Tab repetidamente, então o foco não sai do painel; Esc o fecha e o foco volta ao botão de origem; em 360 px o painel ocupa a largura toda (classes conferidas).
- CA10 (R6): dada a janela aberta, quando a pessoa pressiona Esc, então fecha e o foco volta; a janela tem o título como nome acessível.
- CA11 (R7): dada a confirmação com motivo obrigatório, quando o motivo está vazio ou só com espaços, então o botão de confirmar fica desabilitado e a mensagem diz o que falta; com motivo válido, confirma uma única vez mesmo com cliques repetidos.
- CA12 (R7): dada a confirmação, quando renderizada, então mostra o resumo recebido, e o botão de cancelar fecha sem chamar a ação.
- CA13 (R8): dada a confirmação digitada com texto "DESATIVAR", quando se digita "desativar" ou texto parcial, então o botão segue desabilitado; digitando exatamente o texto, habilita (D4).
- CA14 (R9): dado o componente de etapas com 3 etapas, quando a primeira é inválida, então "Avançar" fica desabilitado e a etapa é anunciada ("Etapa 1 de 3"); válida, avança; "Voltar" retorna sem perder o que foi preenchido.
- CA15 (R10): dada a grade de cartões em 360 px, quando renderizada, então os cartões ficam em uma coluna (classes responsivas conferidas), cada um com título como cabeçalho e ações alcançáveis por teclado.
- CA16 (R11): dado o contador com máximo 100, quando se digitam 101 caracteres, então o campo mantém 100, o contador mostra 100/100 e o leitor de tela recebe aviso ao passar de 90%.
- CA17 (R12): dado o campo de CPF, quando se digita `52998224725`, então exibe `529.982.247-25` e entrega `52998224725`; CPF com dígito verificador errado ou com todos os dígitos iguais mostra erro ligado ao campo por `aria-describedby`.
- CA18 (R12): dado o campo de moeda, quando se digitam `125000`, então exibe `R$ 1.250,00` e entrega `125000` (centavos, inteiro); letras são ignoradas.
- CA19 (R12): dado o campo de telefone, quando se digitam 11 dígitos (celular) ou 10 (fixo), então exibe `(11) 91234-5678` ou `(11) 1234-5678`; DDD inválido ou número curto mostra erro.
- CA20 (R12): dado o campo de data, quando se digita `08102026`, então exibe `08/10/2026` e entrega `2026-10-08`; `31/02/2026` mostra erro de data inexistente.
- CA21 (R13): dado cada componente interativo, quando percorrido só por teclado, então toda ação é alcançável, o foco é visível e botões só de ícone têm nome acessível; alvos de toque têm ao menos 44 px (classes `min-h-11 min-w-11` conferidas).
- CA22 (R14): dada cada demonstração de componente nos temas claro e escuro, quando o axe roda, então não há violação (D6).
- CA23 (R15): dado o código dos componentes, quando roda `npm run lint`, então não há texto literal em JSX; os textos padrão são chaves de `pt-BR.json` e o `tone.test.ts` segue passando com as chaves novas.
- CA24 (R16): dado `frontend/README.md`, quando lido, então lista os componentes e o uso de cada um.

## 4. Plano técnico

- Dados: não muda.
- API: não muda `/api/v1` nem `docs/openapi.yaml`. A consulta recebe uma função de busca; os parâmetros de endereço (`q`, `pagina`, `ordem`) são convenção do front que cada tela mapeará para o contrato quando houver rota. Handlers MSW só de teste.
- Interface: componentes novos em `frontend/src/components/` (todos a criar): `layout/AppLayout.tsx`, `states/` (`LoadingState`, `EmptyState`, `ErrorState`, `ForbiddenState`), `query/` (`QueryPage`, `DataTable`, `Pagination`, `useQueryParams`), `SidePanel.tsx`, `Modal.tsx`, `ConfirmDialog.tsx`, `Stepper.tsx`, `Card.tsx`, `CharCounter.tsx`, `fields/` (`DateField`, `MoneyField`, `PhoneField`, `CpfField`, `masks.ts` com funções puras). Janela e painel sobre `@radix-ui/react-dialog` (foco preso, Esc, retorno de foco), no padrão shadcn/ui já usado em `components/ui/button.tsx`. Toda lista é paginada e tem os estados carregando, vazio, busca sem resultado e erro; erro traz as três partes do RNF-24. Página de demonstração `src/test/Showcase.tsx`, só para teste (axe e 360 px), não publicada.
- Segurança: componentes não são barreira de permissão; `ForbiddenState` só exibe, o servidor recusa. Motivo e texto digitado não são guardados no front; a auditoria do motivo é do servidor, nas rotas dos itens que usarem. CPF e telefone não vão para o endereço nem para log (D3).
- Arquivos: a alterar (existem): `frontend/src/i18n/pt-BR.json`, `frontend/src/test/ExampleScreen.tsx`, `frontend/src/test/screen-states.test.tsx`, `frontend/src/App.tsx` (só se D1 permitir), `frontend/package.json` (+ `@radix-ui/react-dialog`) e lockfile, `frontend/README.md`. A criar: os componentes acima, seus testes ao lado e `src/test/Showcase.tsx`.
- Reuso: `Button` e `cn` (`lib/utils.ts`), `ThemeToggle`, `axeViolations` (`src/test/axe.ts`), `setDevicePrefersDark` (`src/test/device.ts`), servidor MSW de testes, regra `i18next/no-literal-string`, chaves `states.*` e `errors.generic.*` de `pt-BR.json`.
- Riscos: (1) jsdom não faz layout nem mede contraste: 360 px e zoom ficam por proxy, como na #7 (D6). (2) foco preso e retorno de foco dependem do Radix e do jsdom; cobertos com `userEvent`. (3) escopo grande: por isso as três partes. (4) sobreposição com a #9: `masks.ts` não pode virar formatador de exibição (D5).

## 5. Tarefas

Parte A — base

- [x] T1: adicionar `@radix-ui/react-dialog`, criar as chaves de tradução e os componentes `LoadingState`, `EmptyState`, `ErrorState`, `ForbiddenState`; migrar `ExampleScreen` para eles — cobre CA3, CA4, CA23 — prova: `states.test.tsx`, `screen-states.test.tsx`, `tone.test.ts`, `npm run lint`.
- [x] T2: criar `AppLayout` (menu lateral fixo ou em gaveta, grupos, item ativo, topo com tema) — cobre CA1, CA2, CA21 — prova: `AppLayout.test.tsx`.
- [x] T3: criar `Card` com grade de cartões e `CharCounter` — cobre CA15, CA16 — prova: `Card.test.tsx`, `CharCounter.test.tsx`.

Parte B — padrões

- [x] T4: criar `useQueryParams`, `DataTable`, `Pagination` e `QueryPage` (busca, filtros, ordenação, paginação no endereço, busca sem resultado) — cobre CA5, CA6, CA7, CA8 — prova: `QueryPage.test.tsx`.
- [x] T5: criar `Modal` e `SidePanel` sobre Radix Dialog — cobre CA9, CA10 — prova: `Modal.test.tsx`, `SidePanel.test.tsx`.
- [x] T6: criar `ConfirmDialog` (resumo, motivo opcional ou obrigatório, texto digitado, proteção contra duplo clique) — cobre CA11, CA12, CA13 — prova: `ConfirmDialog.test.tsx`.
- [x] T7: criar `Stepper` — cobre CA14 — prova: `Stepper.test.tsx`.

Parte C — campos

- [x] T8: criar `masks.ts` (máscara e validação de CPF, telefone, moeda em centavos e data) e os campos `CpfField`, `MoneyField`, `PhoneField`, `DateField` — cobre CA17, CA18, CA19, CA20 — prova: `masks.test.ts`, `fields.test.tsx`.

Fechamento

- [x] T9: montar `Showcase` de teste com todos os componentes, conferir axe nos dois temas e alvos de 44 px, e atualizar `frontend/README.md` — cobre CA21, CA22, CA24 — prova: `a11y.test.tsx` e `npm run build && npm run lint && npm run typecheck && npm test -- --coverage` verdes, cobertura mínima de 80%.

## 6. Decisões

- D1: o layout recebe a lista de itens e o `id` ativo por propriedades e usa links comuns (`<a href>`); não se adiciona roteador neste item. Motivo: não há rotas de negócio e a escolha do roteador cabe às primeiras telas reais. Alternativa: instalar `react-router` já, descartada por ser dependência sem uso agora.
- D2: "busca sem resultado" é estado separado do "vazio"; o vazio mostra frase e ação principal, a busca sem resultado mostra "Limpar filtros". Motivo: RNF-25 pede ação principal no vazio e os dois casos pedem ações diferentes.
- D3: estado da consulta no endereço via `URLSearchParams` e `history`, num hook próprio pequeno; parâmetros `q`, `pagina`, `ordem` e filtros nomeados pela tela. Motivo: a issue pede filtros e paginação no endereço, sem roteador (D1). Dado pessoal (CPF, telefone) não vai para o endereço.
- D4: a confirmação é um componente único; a confirmação digitada é opção dele, comparando o texto exatamente (diferencia maiúsculas). As quatro ações do RNF-26 que exigem digitação são ligadas nas telas de cada item; aqui só se entrega e prova o componente. Alternativa: criar já as quatro janelas, descartada por serem ações de itens futuros.
- D5: os campos nascem aqui (a issue #8 os lista) e a #9 não os recria. O valor entregue ao formulário é o limpo: CPF e telefone só dígitos, moeda em centavos inteiros, data em ISO `aaaa-mm-dd`. Data é campo de texto com máscara, não `<input type="date">`, para mostrar sempre dd/mm/aaaa. Telefone só do Brasil (DDD de 2 dígitos, 8 ou 9 dígitos). Moeda só em reais e sem valor negativo (suposição: nenhum campo digitado aceita negativo; se aparecer, estende-se o campo).
- D6: como na #7 (D8), axe e 360 px são conferidos em jsdom, com `color-contrast` desligado e contraste coberto pelo teste de tokens existente; rolagem horizontal e zoom de 200% por proxy (classes responsivas, ausência de largura fixa). Conferência em navegador real fica para a #10; este item deixa a `Showcase` pronta para ela.
- D7: textos padrão dos componentes ("Tentar de novo", "Limpar filtros", "Avançar", "Voltar", "Cancelar", "Confirmar") ficam em `pt-BR.json`; cada tela pode passar textos próprios, já traduzidos por ela. Motivo: manter a regra de nenhum texto no código e permitir reuso.
- Suposição: a cobertura de 100% das regras de cobrança, pagamentos, assinatura, inativação e isolamento não se aplica (interface sem regra de negócio); vale o mínimo de 80%.
- Suposição: "Esteira de CI verde" do "Pronto quando" vale quando a #10 existir, como na #7.
- Suposição: mantém-se o branch já criado `item/8-estados-de-tela`, embora o slug não siga o título da issue; se o dono preferir `item/8-componentes-base`, só o nome do branch muda.

## Construção

Parte A (T1 a T3) feita na sessão anterior; parte B (T4 a T7) feita na segunda sessão. Parte C (T8) e o fechamento (T9) ficam para as próximas sessões.

- T1 — arquivos: `frontend/package.json`, `package-lock.json` (+ `@radix-ui/react-dialog`), `src/i18n/pt-BR.json` (chaves `layout.*`, `counter.*`), `src/components/states/{states.tsx,index.ts,states.test.tsx}`, `src/test/ExampleScreen.tsx` (migrado) — prova: `npx vitest run` (states, screen-states, tone) e `npx eslint .` → 32 testes passam, lint sem erro
- T2 — arquivos: `src/components/layout/{AppLayout.tsx,AppLayout.test.tsx}`, `src/components/ui/button.tsx` (tamanho `icon` passa a `h-11 w-11 min-h-11 min-w-11`, alvo de 44 px) — prova: `npx vitest run` → AppLayout 3 testes passam (CA1, CA2, CA21)
- T3 — arquivos: `src/components/{Card.tsx,Card.test.tsx,CharCounter.tsx,CharCounter.test.tsx}` — prova: `npx vitest run` → passam (CA15, CA16)
Regressão: `npx vitest run` → 10 arquivos, 32 testes passam; `npx eslint .` e `npx tsc -b --noEmit` sem erros. Build e cobertura ficam para o T9.
Desvios: `CharCounter` é o campo (textarea) com contador embutido, e não só o contador, porque o CA16 exige que o campo mantenha 100 caracteres. O texto `counter.nearLimit` foi escrito como "Faltam poucos caracteres para o limite." porque a lista de `tone.test.ts` barra "tá " dentro de "está"; o teste não foi alterado. O CA2 do `AppLayout` cobre foco, Esc e retorno; não há rolagem horizontal por proxy (sem `w-[Npx]`).
Não é do item: nenhum.

### Parte B (segunda sessão)

- T4 — arquivos: `src/components/query/{useQueryParams.ts,DataTable.tsx,Pagination.tsx,QueryPage.tsx,QueryPage.test.tsx}` — prova: `npx vitest run` → QueryPage 6 testes passam (CA5 a CA8)
- T5 — arquivos: `src/components/{Modal.tsx,SidePanel.tsx,Modal.test.tsx}` — prova: `npx vitest run` → passam (CA9, CA10)
- T6 — arquivos: `src/components/{ConfirmDialog.tsx,ConfirmDialog.test.tsx}` — prova: `npx vitest run` → 5 testes passam (CA11 a CA13)
- T7 — arquivos: `src/components/{Stepper.tsx,Stepper.test.tsx}`, chaves `common.*`, `query.*`, `confirm.*`, `stepper.*` em `pt-BR.json` — prova: `npx vitest run` → passa (CA14)
Regressão (parte B): `npx vitest run` → 14 arquivos, 47 testes passam (duas execuções seguidas); `npx eslint .` e `npx tsc -b --noEmit` sem erros. Build e cobertura ficam para o T9.
Desvios: os testes de `Modal` e `SidePanel` ficam num arquivo só (`Modal.test.tsx`), pois `SidePanel` é o `Modal` com outras classes. Sem `Trigger`, o Radix não devolve o foco; o `Modal` guarda o elemento que tinha foco ao abrir e o devolve ao fechar. `vite.config.ts` ganhou `testTimeout: 20000`: com a máquina carregada, testes com `userEvent` passavam de 5 s (inclusive o `CharCounter` da parte A) e falhavam por tempo, sem falha de código. A `QueryPage` recebe `filterKeys` (nomes dos filtros no endereço) e `filters` (render prop) para os filtros da tela.
Não é do item: nenhum.

### Parte C (terceira sessão)

- T8 — arquivos: `src/components/fields/{masks.ts,masks.test.ts,fields.tsx,fields.test.tsx}`, chaves `fields.*` em `pt-BR.json` — prova: `npx vitest run` → masks (4) e fields (4) passam (CA17 a CA20)
Regressão (parte C): `npx vitest run` → 16 arquivos, 55 testes passam; `npx eslint .` e `npx tsc -b --noEmit` sem erros. Build e cobertura ficam para o T9.
Desvios: os quatro campos ficam num arquivo só (`fields.tsx`), sobre um `MaskedField` interno, em vez de um arquivo por campo. O valor inicial vai em `initial` (o campo guarda o texto exibido e entrega só o valor limpo por `onChange`); o erro aparece ao sair do campo. O texto "dd/mm/aaaa" e "(00) 00000-0000" dos placeholders vão para `pt-BR.json` por causa do lint.
Não é do item: nenhum.

### Fechamento (quarta sessão)

- T9 — arquivos: `src/test/Showcase.tsx` (novo), `src/test/a11y.test.tsx` (+ axe da Showcase nos dois temas, das janelas abertas e proxy de 44 px), `frontend/README.md` (seção Componentes) — prova: `npm run build && npm run lint && npm run typecheck && npm test -- --coverage` → build ok, lint e tipos sem erro, 16 arquivos e 61 testes passam, cobertura 93,89% (instruções), 93,04% (ramos), 96,03% (linhas)
Regressão: mesma execução acima; nada que passava passou a falhar.
Desvios: a conferência de 44 px é um teste de proxy que procura `min-h-11`/`min-w-11` em `AppLayout`, `button` e `Pagination` (jsdom não mede). `ExampleScreen` e `screen-states.test.tsx` já tinham sido migrados na parte A. Conferência de navegador real fica para a #10 (D6).
Não é do item: nenhum.

## Verificação

### Rodada 1 — OK — 2026-10-15, estado 05f4857382c68c6c511f604565ce26b037fd8272

| Critério | Status | Evidência |
|---|---|---|
| CA1 | PASSOU | `AppLayout.test.tsx` (aria-current, grupo aberto, aside `hidden md:block`) |
| CA2 | PASSOU | `AppLayout.test.tsx`: gaveta, foco dentro, Esc, foco volta; sem `w-[Npx]` (proxy, D6) |
| CA3 | PASSOU | `states.test.tsx` |
| CA4 | PASSOU | `screen-states.test.tsx` com `ExampleScreen` migrado |
| CA5 a CA8 | PASSOU | `QueryPage.test.tsx` (6 testes); leitura de `QueryPage.tsx` e `useQueryParams.ts` confere |
| CA9, CA10 | PASSOU | `Modal.test.tsx` (Tab preso, Esc, foco volta, título como nome, `w-full md:w-96`) |
| CA11 a CA13 | PASSOU | `ConfirmDialog.test.tsx` |
| CA14 | PASSOU | `Stepper.test.tsx` |
| CA15, CA16 | PASSOU | `Card.test.tsx`, `CharCounter.test.tsx` |
| CA17 a CA20 | PASSOU | `masks.test.ts`, `fields.test.tsx` |
| CA21 | PASSOU (proxy) | `a11y.test.tsx` procura `min-h-11`/`min-w-11`; navegador real na #10 (D6) |
| CA22 | PASSOU | `a11y.test.tsx`: Showcase nos dois temas e janelas abertas sem violações |
| CA23 | PASSOU | `npm run lint` sem erro; `tone.test.ts` passa |
| CA24 | PASSOU | seção "Componentes" em `frontend/README.md` |

Comandos (em `frontend/`): `npm run build` ok; `npm run lint` sem erro; `npm run typecheck` sem erro; `npm test -- --coverage` 16 arquivos, 61 testes passam, cobertura 93,89% instruções, 93,04% ramos, 96,03% linhas (mínimo 80%).
Achados:
- sugestão, especificação, seção 1 e "Suposição" do branch — a spec cita `item/8-estados-de-tela`, mas o branch real é `item/8-componentes-base` (o slug correto); atualizar o texto.
- sugestão, especificação, tarefas T5 e T1 — citam `SidePanel.test.tsx` e `screen-states.test.tsx` como arquivos a criar/alterar; o primeiro não existe (coberto em `Modal.test.tsx`) e o segundo não mudou.
- sugestão, código, `QueryPage.tsx` — cada tecla na busca faz uma requisição e um `pushState`; considerar atraso (debounce) quando houver rotas reais.

## Entrega

- Componentes base do front-end: estados de tela, `AppLayout` (menu e gaveta no celular), `QueryPage` com tabela, paginação e filtros no endereço, `Modal`, `SidePanel`, `ConfirmDialog`, `Stepper`, `Card`, `CharCounter` e campos com máscara (CPF, telefone, data, valor), com textos em `pt-BR.json`.
- Verificação (rodada 1, OK): build, lint e tipos sem erro; 16 arquivos e 61 testes passam; cobertura 93,89% instruções, 93,04% ramos, 96,03% linhas; axe da `Showcase` nos dois temas sem violações.
- Decisões: textos padrão dos componentes em `pt-BR.json`, com textos próprios por tela; `testTimeout: 20000` no Vitest por causa dos testes com `userEvent`.
- Limitações: alvo de 44 px conferido por proxy de classes (jsdom não mede); navegador real fica para a #10. Cada tecla na busca faz uma requisição (considerar debounce com rotas reais).

Refs #8
