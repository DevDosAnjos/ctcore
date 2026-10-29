# 415 — Componentes base no padrão do canvas

Origem: issue #415 (Sprint 10.5 · Identidade visual), tabela "Componentes" de `docs/design/README.md`. Branch: `item/415-componentes-base-canvas`.

## 1. Objetivo

Ter em `frontend/src/components` as peças que o canvas usa em todas as telas (botão, selos, cartões, campos, tabela, abas, janelas, painel lateral, avisos, avatar, etapas, cabeçalho de página e os quatro estados), com o visual dos tokens da #414, para que as telas das próximas issues fiquem iguais só usando os componentes. Uma vitrine só de desenvolvimento mostra todas as peças nos dois temas, para o teste visual e a revisão do dono.

Fora do escopo: trocar as telas existentes para os componentes novos (issues de tela); regra, rota, texto de negócio. As telas atuais seguem funcionando com a API antiga de cada componente (D1).

Item dividido em três partes, construíveis em sessões separadas, com uma especificação, uma verificação e uma entrega:

- Parte A (T1 a T3): botão, selos e mapa de tons, cartões, aviso, resumo, avatar e cabeçalho da página.
- Parte B (T4 a T6): campos, abas, filtros rápidos, tabela, paginação, etapas e os quatro estados.
- Parte C (T7 a T8): janela, confirmação, painel lateral, vitrine, axe, documentação.

## 2. Requisitos

- R1: `Button` tem as variantes `primary`, `secondary`, `ghost`, `danger`, `dangerSolid`, `accent`, `whatsapp`; tamanhos `sm` (32 px), `md` (40 px), `lg` (48 px); aceita ícone, estado carregando (sem clique, anunciado) e desabilitado com dica (`title`/`aria-describedby`) (D1, D2).
- R2: `statusTone.ts` é o único mapa situação → tom e cobre todas as situações que o sistema já exibe; `StatusBadge` recebe a situação e o rótulo traduzido e escolhe o tom; `Badge` recebe o tom explícito (D3).
- R3: `Card`, `Section` (cartão com título e ação) e `Stat` (rótulo, valor em Barlow Condensed, nota com tom) existem com os cantos e a borda dos tokens.
- R4: `Field` (rótulo, obrigatório, dica, erro) liga rótulo, dica e erro ao controle por `htmlFor`/`aria-describedby`/`aria-invalid`; `Input`, `Select`, `Textarea` (contador), `Checkbox`, `RadioCard` e `Switch` existem; as máscaras de `components/fields` seguem funcionando com o visual novo (D4).
- R5: `DataTable` tem cabeçalho suave, linha de 52 px, coluna numérica à direita com números tabulares e seleção de linhas; `Pagination`, `QuickFilters` (filtro rápido com contador) e `Tabs` existem (D1, D4).
- R6: `Modal` (cabeçalho com título em Barlow, rodapé suave), `ConfirmDialog` (com `SummaryBox` e confirmação digitada) e `SidePanel` (680 px, abas) existem e mantêm foco preso, Esc e devolução do foco.
- R7: `Banner` (tons `info`, `warn`, `danger`, `ok`), `SummaryBox`, `Avatar` (iniciais e cor estável pelo nome), `Person`, `Stepper` e `PageHeader` (título em caixa alta, subtítulo, ações) existem.
- R8: os quatro estados de `components/states` têm o visual de `EstadoCarregando` (esqueleto), `EstadoSemResultados`, `EstadoErro` (com "Tentar de novo") e `EstadoSemAcesso`, sem mudar as props atuais.
- R9: nenhum componente novo ou alterado usa cor fora dos tokens (nada de hex nem classe de cor fixa como `bg-red-500`).
- R10: toda peça tem teste de cada variante e passa no axe nos dois temas; alvo de toque continua de 44 px (D2).
- R11: existe uma vitrine em `/dev/vitrine` com todas as peças nos dois temas, presente só em desenvolvimento e ausente do `dist`.
- R12: `docs/design/README.md` fica correto se algum nome mudou (D1).
- R13: textos fixos dos componentes (rótulos de leitor de tela, "Tentar de novo", contador) vêm de `pt-BR.json`.

## 3. Critérios de aceite

- CA1 (R1): dado cada variante e tamanho, quando renderizado, então a altura é 32/40/48 px (classes `h-8`/`h-10`/`h-12`) e a classe da variante usa só tokens; com ícone, o ícone fica antes do texto.
- CA2 (R1): dado `loading`, quando a pessoa clica, então `onClick` não é chamado, `aria-busy` é verdadeiro e um indicador com nome acessível aparece; dado `disabled` com `disabledReason`, então o botão fica `aria-disabled`, o motivo está disponível como dica e o clique não chama `onClick`.
- CA3 (R1, R10): dado qualquer tamanho do botão, então a área clicável tem pelo menos 44 px de altura (D2).
- CA4 (R2): dado cada chave de `statuses`/`status` de `pt-BR.json` e cada valor dos tipos `StoredStatus`, `ProofStatus`, `PortalRequestStatus`, `EnrollmentStatus`, `InviteStatus` e das cobranças em atraso/vencendo, quando consultado o mapa, então devolve um dos seis tons; chave desconhecida cai em `neutral`.
- CA5 (R2): dado `StatusBadge` com `OVERDUE`, então usa o tom `danger`, mostra o rótulo recebido e tem altura de 22 px; dado `Badge tone="info"`, usa o tom pedido.
- CA6 (R3): dado `Stat` com nota de tom `ok`, quando renderizado, então o valor usa a fonte de títulos e a nota tem o tom (e o texto, não só a cor, traz o significado).
- CA7 (R3): dado `Section` com título e ação, então o título é um cabeçalho que nomeia a região e a ação aparece à direita; `Card` sem título renderiza só o contêiner.
- CA8 (R4): dado `Field` com `required`, dica e erro, quando renderizado, então o controle tem o rótulo, `aria-required`, `aria-describedby` com a dica e o erro, e `aria-invalid`.
- CA9 (R4): dado `Textarea` com `maxLength`, quando a pessoa digita, então o contador mostra o uso; dado `Checkbox`, `Switch` e `RadioCard`, então alternam por clique e por teclado (Espaço/Enter, setas no grupo de rádios) com `role` e estado corretos.
- CA10 (R4): dado `CpfField`, `PhoneField`, `MoneyField` e `DateField`, quando digitados, então formatam e entregam o valor como hoje (testes de `fields.test.tsx` e `masks.test.ts` seguem verdes).
- CA11 (R5): dado `DataTable` com coluna `numeric`, então cabeçalho e células ficam à direita com `tabular-nums`; a linha tem `h-[52px]`; com `selectable`, marcar a linha e o "marcar todas" do cabeçalho chamam `onSelectionChange` com as chaves certas; sem as props novas, a tabela se comporta como hoje.
- CA12 (R5): dado `QuickFilters` com contadores, quando a pessoa aciona uma opção, então ela fica `aria-pressed` e `onChange` recebe a chave; o contador aparece no rótulo acessível.
- CA13 (R5): dado `Tabs` com três abas, quando a pessoa usa seta direita/esquerda, Home e End, então o foco e a aba ativa mudam e só o painel ativo é exibido.
- CA14 (R5): dado `Pagination`, então mantém as props atuais, o botão da página atual indica `aria-live` e os botões ficam desabilitados nos extremos.
- CA15 (R6): dado `Modal` aberto, então o título usa a fonte de títulos, o rodapé (`footer`) tem fundo suave, o foco fica preso, Esc fecha e o foco volta a quem abriu; `dismissible={false}` continua sem Esc e sem X.
- CA16 (R6): dado `ConfirmDialog` com `typedText` e `SummaryBox`, quando o texto digitado difere, então a confirmação fica desabilitada; ao digitar igual, habilita; todo fechamento limpa o texto (comportamento atual preservado, `ConfirmDialog.test.tsx` verde).
- CA17 (R6): dado `SidePanel` com abas, então tem largura de 680 px em tela larga, tela cheia a 360 px, abas navegáveis por teclado e o mesmo foco da janela.
- CA18 (R7): dado `Avatar` com o mesmo nome em duas renderizações, então as iniciais (até duas letras) e a cor são iguais; nomes diferentes podem ter cores diferentes dentre uma paleta de tokens; `Person` mostra avatar, nome e linha secundária.
- CA19 (R7): dado `Banner` de cada tom, então tem ícone, texto e `role` (`alert` para `danger`/`warn`, `status` para os demais); dado `SummaryBox` com itens, então os itens aparecem numa lista com seta.
- CA20 (R7): dado `Stepper`, então mostra as etapas numeradas com a atual marcada por `aria-current="step"` e mantém o fluxo e as props atuais (`Stepper.test.tsx` verde); dado `PageHeader`, então o título é um h1 em caixa alta (CSS `uppercase`, texto original no DOM), com subtítulo e ações.
- CA21 (R8): dado cada estado, então o carregando mostra esqueleto com `role="status"` e texto para leitor de tela, o vazio e o sem resultado mostram título, texto e ação, o erro mostra as três partes e "Tentar de novo" chama `onRetry`, o sem acesso não recebe conteúdo (`states.test.tsx` verde).
- CA22 (R9): dado o código-fonte dos componentes novos e alterados, quando um teste procura `#[0-9a-fA-F]{3,8}` e classes de paleta fixa (`bg-white`, `text-black`, `red-`, `gray-` etc.), então não encontra nenhuma.
- CA23 (R10, R11): dada a vitrine, quando renderizada nos temas claro e escuro, então o axe não aponta violação e todas as peças da tabela de `docs/design/README.md` aparecem.
- CA24 (R11): dado `npm run build`, quando se procura a marca da vitrine (`data-vitrine`) e o texto "Vitrine" em `dist`, então não há resultado; em `npm run dev`, `/dev/vitrine` abre.
- CA25 (R12): dada a tabela "Componentes" de `docs/design/README.md`, então cada nome citado existe exportado em `frontend/src/components` (teste lê a tabela e confere os arquivos).
- CA26 (R13): dado `npm run lint`, então não há texto literal novo nos componentes (regra de lint existente); a vitrine usa textos de exemplo só dentro de `src/dev`, isenta da regra como o `Showcase` (D5).
- CA27 (R10): dada a regressão completa do front (`lint`, `typecheck`, `build`, `test:cov`, `e2e`), então passa, inclusive o axe das telas existentes nos dois temas.

## 4. Plano técnico

- Dados e API: não mudam. Segurança: não se aplica (nenhum componente decide permissão; `ForbiddenState` só exibe).
- Interface: componentes compartilhados; todos os estados e os dois temas; a 360 px. Sem tela nova além da vitrine de desenvolvimento.
- Compatibilidade (D1): há 130 arquivos usando os componentes atuais. Cada componente existente ganha as variantes e o visual novos **no mesmo arquivo**, mantendo nome, props e comportamento; os valores antigos de `variant` (`default`, `outline`, `ghost`, `icon`) continuam aceitos e passam a ter o visual novo (`default` = `primary`, `outline` = `secondary`). Peça nova entra em arquivo novo.
- Arquivos (vistos no repositório):
  - `frontend/src/components/ui/button.tsx`: `buttonVariants` (cva) e `Button`: variantes, tamanhos, `loading`, `icon`, `disabledReason`; alvo de 44 px por `after:` invisível.
  - `frontend/src/components/Card.tsx`: `Card` (título opcional), novo `Section`; `CardGrid` fica.
  - `frontend/src/components/fields/fields.tsx`: `MaskedField` passa a usar `Field` e `Input`; `masks.ts` não muda.
  - `frontend/src/components/query/DataTable.tsx`: `Column.numeric`, props `selectable`/`selected`/`onSelectionChange`, visual; `Pagination.tsx`: visual e `Select`.
  - `frontend/src/components/Stepper.tsx`: indicador de etapas; `ConfirmDialog.tsx`: usa `SummaryBox`, `Field`, `Input`, `Textarea`; `Modal.tsx`: cabeçalho, rodapé (`footer` opcional), largura; `SidePanel.tsx`: 680 px e `tabs` opcional.
  - `frontend/src/components/states/states.tsx`: visual dos quatro estados (esqueleto, ícone), props iguais; `states.test.tsx` ajustado.
  - `frontend/src/App.tsx`: rota `/dev/vitrine` só sob `import.meta.env.DEV`, com `lazy`.
  - `frontend/src/i18n/pt-BR.json`: chaves de `ui.*` (carregando do botão, contador, selecionar linha/todas, etapas, fechar etc.).
  - `docs/design/README.md`: nomes e observações da tabela "Componentes", se algo mudar.
  - A criar em `frontend/src/components/ui/`: `status-tone.ts` (+ teste), `badge.tsx` (`Badge`, `StatusBadge`), `stat.tsx`, `banner.tsx`, `summary-box.tsx`, `avatar.tsx` (`Avatar`, `Person`), `page-header.tsx`, `field.tsx` (`Field`, `Input`, `Select`, `Textarea`), `checkbox.tsx`, `switch.tsx`, `radio-card.tsx`, `tabs.tsx`, `quick-filters.tsx`; um `.test.tsx` por arquivo.
  - A criar: `frontend/src/dev/Vitrine.tsx` (+ `Vitrine.test.tsx` com axe e verificação do que o build produz), `frontend/src/components/tokens.test.ts` (CA22) e `frontend/src/components/design-readme.test.ts` (CA25).
- Reuso: `cn` (`lib/utils`), `cva`, `Slot`, `@radix-ui/react-dialog` (já na `Modal`), `lucide-react`, `CharCounter`, `useId`, as máscaras de `masks.ts`, `axeViolations` e `setDevicePrefersDark` de `src/test`, os tokens da #414 (`bg-ok`, `text-ok-foreground`, `--font-heading`, `--radius-card`, `--shadow-*`).
- Sem dependência nova (D4). Mapa de tons: ler as chaves de situação de `pt-BR.json` e os tipos de status do código para o CA4.
- Riscos: restyle muda aparência de telas existentes (testes que olham classe ou altura, e os e2e/axe; percebido na regressão); `h-11` fixo de hoje vira 40 px visuais, o alvo de 44 px depende do `after:` (percebido no CA3); `ConfirmDialog`, `Modal` e `DataTable` são usados em dezenas de telas, então qualquer mudança de props quebraria o `typecheck`; o build precisa eliminar a vitrine (CA24).

## 5. Tarefas

- [x] T1: (Parte A) `Button` com variantes, tamanhos, ícone, carregando e desabilitado com dica; `status-tone.ts`, `Badge`, `StatusBadge` — cobre CA1, CA2, CA3, CA4, CA5 — prova: `npx vitest run src/components/ui/button src/components/ui/badge src/components/ui/status-tone`
- [x] T2: (Parte A) `Card` (título opcional), `Section`, `Stat`, `Banner`, `SummaryBox`, `Avatar`, `Person`, `PageHeader` — cobre CA6, CA7, CA18, CA19, CA20 (cabeçalho) — prova: `npx vitest run src/components/Card src/components/ui/stat src/components/ui/banner src/components/ui/summary-box src/components/ui/avatar src/components/ui/page-header`
- [x] T3: (Parte A) teste de cor fora dos tokens (`tokens.test.ts`) e chaves `ui.*` do `pt-BR.json` usadas até aqui — cobre CA22, CA26 — prova: `npx vitest run src/components/tokens.test.ts src/i18n && npm run lint`
- [x] T4: (Parte B) `Field`, `Input`, `Select`, `Textarea`, `Checkbox`, `RadioCard`, `Switch`; `fields.tsx` passa a usá-los — cobre CA8, CA9, CA10 — prova: `npx vitest run src/components/ui/field src/components/ui/checkbox src/components/ui/switch src/components/ui/radio-card src/components/fields`
- [x] T5: (Parte B) `Tabs`, `QuickFilters`, `DataTable` (numérica, 52 px, seleção), `Pagination`, `Stepper` — cobre CA11, CA12, CA13, CA14, CA20 (etapas) — prova: `npx vitest run src/components/ui/tabs src/components/ui/quick-filters src/components/query src/components/Stepper.test.tsx`
- [x] T6: (Parte B) quatro estados no visual do canvas — cobre CA21 — prova: `npx vitest run src/components/states src/test/screen-states.test.tsx`
- [x] T7: (Parte C) `Modal` (cabeçalho, rodapé), `ConfirmDialog` (com `SummaryBox`), `SidePanel` (680 px, abas) — cobre CA15, CA16, CA17 — prova: `npx vitest run src/components/Modal.test.tsx src/components/ConfirmDialog.test.tsx src/components/SidePanel`
- [x] T8: (Parte C) vitrine `/dev/vitrine`, teste axe nos dois temas, conferência do `dist`, `design-readme.test.ts`, `docs/design/README.md` e regressão — cobre CA23, CA24, CA25, CA27 — prova: `npx vitest run src/dev src/components/design-readme.test.ts && npm run build && grep -r "data-vitrine" dist; npm run lint && npm run typecheck && npm run test:cov && npm run e2e`

## 6. Decisões

- D1: compatibilidade em vez de renomear: cada componente existente é evoluído no próprio arquivo, com as mesmas props e os nomes antigos de variante (`default`, `outline`, `ghost`, `icon`) aceitos; as telas atuais mudam de aparência sem mudar de código, e as issues de tela trocam para as variantes novas. Motivo: 130 arquivos usam esses componentes e a issue manda manter compatibilidade. Alternativa descartada: renomear e atualizar todas as telas neste PR.
- D2 (muda o comportamento): altura visual do botão segue o canvas (32/40/48 px), mas o alvo de toque de 44 px do RNF-20 é mantido com uma área clicável invisível (`after:`). Motivo: o canvas e a RNF-20 divergem; a regra vale, o visual fica fiel. Alternativa descartada: botão de 44 px visuais, que destoa do canvas.
- D3: um só mapa por chave de situação (a mesma palavra tem o mesmo tom em todo o sistema): `ACTIVE`, `PAID`, `CURRENT`, `CONFIRMED`, `APPROVED`, `SENT` → `ok`; `DUE_TODAY`, `DUE_SOON`, `CLOSURE_REQUEST` → `warn`; `OVERDUE`, `BLOCKED`, `FAILED`, `EMAIL_PROBLEM`, `REFUSED` → `danger`; `IN_REVIEW`, `OPEN`, `SCHEDULED` → `info`; `PENDING`, `INACTIVE`, `ENDED`, `CANCELED`, `EXPIRED`, `RENEGOTIATED`, `REFUNDED`, `FORMER`, `NONE`, `CLOSED` → `neutral`; `BIRTHDAY_TODAY` → `acc`. A lista final sai do levantamento do CA4; chave sem tom cai em `neutral` e o teste lista as faltantes. Alternativa descartada: tom passado por tela.
- D4: sem dependência nova: `Tabs`, `Switch`, `Checkbox` e `RadioCard` são elementos nativos com ARIA (`role="tablist"`, `role="switch"`, `input type="checkbox"/"radio"`), como `Modal` já usa só o Radix Dialog. Motivo: menos pacote e lockfile; o teste cobre o teclado. Alternativa descartada: `@radix-ui/react-tabs`, `-switch`, `-checkbox`, `-radio-group`.
- D5: a vitrine mora em `frontend/src/dev/`, com textos de exemplo fixos (como o `Showcase` de teste) e sem entrar no `dist`: a rota só existe sob `import.meta.env.DEV` e o módulo é carregado por `lazy`, então o Vite elimina o código. Se o lint de texto literal reclamar de `src/dev`, a pasta entra na exceção dele, como `src/test`.
- D6: `PageHeader` põe a caixa alta por CSS (`uppercase`), com o texto original no DOM, para leitor de tela e busca não receberem texto gritado.
- Suposição: o canvas (`Alunos`, `AlunoDetalhes`, `TrocarTurma`, `UnidadeDesativar`, `PagamentoRegistrar`, `RecebimentosDia`, `MatriculaAluno`, `Estado*`) é lido na construção por `node scripts/canvas-shot.mjs` ou pelo código dos `.dc.html`; medidas exatas não citadas aqui (espaçamentos internos, tamanho do ícone dos estados) seguem o canvas, e as não citadas no README ficam com os tokens. Se o dono quiser outro valor, muda só o componente.

## Construção

Partes A (T1 a T3) e B (T4 a T6) construídas; Parte C (T7 a T8) por fazer.

- T1 — arquivos: `frontend/src/components/ui/{button.tsx,status-tone.ts,badge.tsx}` + testes `button.test.tsx`, `status-tone.test.ts`, `badge.test.tsx`; `src/i18n/pt-BR.json` (`ui.loading`) — prova: `npx vitest run src/components/ui` → passou
- T2 — arquivos: `frontend/src/components/Card.tsx` (`Card` com título opcional, `Section`), `ui/{stat,banner,summary-box,avatar,page-header}.tsx` + testes, `Card.test.tsx` ampliado — prova: `npx vitest run src/components/Card src/components/ui` → passou
- T3 — arquivos: `frontend/src/components/tokens.test.ts` (varre `ui/` e `Card.tsx`; B e C acrescentam seus arquivos) — prova: `npx vitest run src/components/tokens.test.ts src/i18n && npm run lint` → passou (46 testes nos 12 arquivos da parte A)
Regressão (Parte A): `npm run lint`, `npm run typecheck`, `npm run build` → ok; `npm run test:cov` → 169 arquivos, 1481 testes, passou (limiares atendidos); `npm run e2e` (@critical) → 81 passaram.
Desvios: (1) `status-tone.ts` cobre também chaves que o `pt-BR.json` já exibe e a D3 não listava (`PAID_LATE`, `IN_PROGRESS`, `COMPLETED`, `CANCELLED`, `ADDED`, `DISMISSED`, `NO_ADDRESS`, `NO_PHONE`, `SMS_LIMIT`, `SKIPPED`, `NOT_OPEN`, `RECENT`, `NO_EMAIL`, `MINOR`, `EXISTING_ACCOUNT`), conforme a D3 manda ("lista final sai do levantamento"). (2) Por causa da D2, três testes antigos que exigiam `h-11`/`min-h-11` no botão passaram a exigir `after:min-h-11`/`after:min-w-11`: `src/test/a11y.test.tsx` (linhas 82 e 86) e `src/components/layout/AppLayout.test.tsx` (linha 48). (3) Tamanhos antigos `default`/`icon` do botão agora têm 40 px visuais (`sm` 32 px); as telas atuais mudam de aparência sem mudar de código (D1). (4) `whatsapp` usa os tokens `ok`, pois o canvas/tokens não têm verde próprio. (5) Medidas finas (espaçamentos internos) seguiram os tokens do README; o canvas não foi aberto nesta parte.
Não é do item: nenhum.

### Parte B (sessão seguinte)

- T4 — arquivos: `ui/{field,checkbox,switch,radio-card}.tsx` + `field.test.tsx`; `fields/fields.tsx` (`MaskedField` usa `Field` e `Input`); `pt-BR.json` (`ui.counter`, `ui.selectRow`, `ui.selectAll`, `ui.stepsLabel`) — prova: `npx vitest run src/components/ui/field src/components/fields` → passou (32 testes)
- T5 — arquivos: `ui/{tabs,quick-filters}.tsx` + testes; `query/DataTable.tsx` (numérica, 52 px, seleção), `query/Pagination.tsx`, `Stepper.tsx` (indicador numerado) + `query/DataTable.test.tsx` (novo, inclui Pagination), `Stepper.test.tsx` — prova: `npx vitest run src/components/ui/tabs src/components/ui/quick-filters src/components/query src/components/Stepper.test.tsx` → passou
- T6 — arquivos: `states/states.tsx` (esqueleto, ícones, título em fonte de títulos), `states.test.tsx` — prova: `npx vitest run src/components/states src/test/screen-states.test.tsx` → passou
Regressão (Parte B): `npm run lint`, `typecheck`, `build` → ok; `npm run test:cov` → 173 arquivos, 1506 testes, passou (limiares atendidos); `npm run e2e` (@critical) → 81 passaram.
Desvios: (1) `Field` usa render-prop (`children(control)`) para entregar `id`/`aria-*` ao controle. (2) `Tabs` recebe `tabs[]` com `content` e `label`, não filhos. (3) `QuickFilters` usa `aria-label` "rótulo, contador" quando há contador. (4) `tokens.test.ts` passou a varrer também `Stepper.tsx`, `fields/fields.tsx`, `query/*` e `states/states.tsx`. (5) `Textarea` novo usa o contador `ui.counter`; `CharCounter` antigo segue como está. (6) Arquivos reescritos por inteiro (`DataTable.tsx`) ficaram com LF; o Git normaliza.
Não é do item: nenhum.

### Parte C (sessão seguinte)

- T7 — arquivos: `Modal.tsx` (cabeçalho com título em `font-heading`, `footer` opcional com fundo suave, corpo rolável, cantos `rounded-window`, fundo escuro do `bg-sidebar`), `ConfirmDialog.tsx` (usa `Field`, `Input`, `Textarea`, `SummaryBox` por `summaryItems?`, botões no rodapé), `SidePanel.tsx` (680 px, `tabs?` com `Tabs`, `children` opcional) + `Modal.test.tsx`, `ConfirmDialog.test.tsx` ampliados — prova: `npx vitest run src/components/Modal.test.tsx src/components/ConfirmDialog.test.tsx` → 14 passaram
- T8 — arquivos: `src/dev/Vitrine.tsx` + `Vitrine.test.tsx` (axe nos dois aparelhos, dois temas na página), `src/components/design-readme.test.ts`, `App.tsx` (rota `/dev/vitrine` sob `import.meta.env.DEV` com `lazy`), `eslint.config.js` (`src/dev/**` isenta do texto literal), `docs/design/README.md` (linha do layout: `Sidebar`/`Topbar` não existem como componentes; ficam dentro do `AppLayout`), `tokens.test.ts` (varre também `Modal`, `ConfirmDialog`, `SidePanel`) — prova: `npx vitest run src/dev src/components` → 155 passaram; `npm run build` e `grep -r "data-vitrine\|Vitrine" dist` → sem resultado
Desvios: (1) o `design-readme.test.ts` procura os nomes em todo `src` (não só `components`), porque `PortalLayout` mora em `features/portal`. (2) `ConfirmDialog` não passa `required` ao `Field` (o asterisco mudaria o rótulo "Motivo" e quebraria `ConfirmDialog.test.tsx`); põe `aria-required` direto no `Textarea`. (3) Fundo da janela: `bg-black` é proibido pelo CA22; usei `bg-sidebar/70`, escuro nos dois temas.
Não é do item: nenhum.
Desvio (4): a largura do painel ficou `md:w-[min(680px,100%)]`, porque `a11y.test.tsx` reprova `w-[Npx]` acima de 360; o efeito é 680 px em tela larga.
Regressão (Parte C): `npm run lint`, `typecheck`, `build` → ok (sem `Vitrine`/`data-vitrine` em `dist`); `npm run test:cov` → 175 arquivos, 1516 testes, passou (limiares atendidos); `npm run e2e` (@critical) → 81 passaram. Falha anterior: nenhuma.

## Verificação

### Rodada 1 — OK — 2026-10-29, estado 0cf88b140821d6e9f52be525e917d4615f067340

| Critério | Status | Evidência |
|---|---|---|
| CA1, CA2, CA3 | PASSOU | `ui/button.test.tsx` (tamanhos 32/40/48 com `after:min-h-11`, variantes só com tokens, ícone antes, loading e disabledReason sem clique); leitura do diff de `button.tsx` |
| CA4, CA5 | PASSOU | `status-tone.test.ts` (chaves do pt-BR.json e dos tipos têm tom, desconhecida = neutral), `badge.test.tsx` (h-[22px]) |
| CA6, CA7 | PASSOU | `stat.test.tsx`, `Card.test.tsx` |
| CA8, CA9 | PASSOU | `field.test.tsx` (aria, contador, Checkbox/Switch/RadioCard por clique e teclado) |
| CA10 | PASSOU | `fields.test.tsx` e `masks.test.ts` verdes na suíte completa |
| CA11, CA14 | PASSOU | `query/DataTable.test.tsx` (numérica, h-[52px], seleção, Pagination); diff de `DataTable.tsx` e `Pagination.tsx` |
| CA12, CA13 | PASSOU | `quick-filters.test.tsx`, `tabs.test.tsx` |
| CA15, CA16, CA17 | PASSOU | `Modal.test.tsx`, `ConfirmDialog.test.tsx` (inclui SidePanel com abas e classes `w-full`/`md:w-[min(680px,100%)]`) |
| CA18, CA19, CA20 | PASSOU | `avatar`, `banner`, `summary-box`, `page-header`, `Stepper.test.tsx` |
| CA21 | PASSOU | `states.test.tsx` e `screen-states.test.tsx` |
| CA22, CA26 | PASSOU | `tokens.test.ts`; `npm run lint` ok (`src/dev/**` isenta) |
| CA23 | PASSOU | `Vitrine.test.tsx`: axe sem violações nos dois temas; a vitrine renderiza todos os componentes da tabela, exceto `AppLayout`/`PortalLayout` (layouts reais) |
| CA24 | PASSOU | `npm run build` e `grep -r "data-vitrine\|Vitrine" dist` sem resultado; rota só sob `import.meta.env.DEV` com `lazy` (leitura de `App.tsx`); `/dev/vitrine` aberta no navegador não foi exercitada |
| CA25 | PASSOU | `design-readme.test.ts` |
| CA27 | PASSOU | lint, typecheck, build ok; `test:cov` 175 arquivos, 1516 testes passaram; `e2e` 81 passaram |

Comandos: `npm run lint && npm run typecheck && npm run build` → ok; `npm run test:cov` → 1516/1516, limiares atendidos; `npm run e2e` → 81 passaram. Logs em /tmp/v415.
Instáveis: nenhum.
Achados:
- sugestão, código, `ConfirmDialog.tsx` final do JSX — linha em branco sobrando antes de `);`.
- sugestão, código, `Vitrine.test.tsx` — a tarefa T8 previa conferir também o `dist` e a presença de cada peça da tabela; o teste só roda o axe e checa dois botões. Conferi o `dist` à mão; vale automatizar.
- sugestão, código, `Vitrine.tsx` — `Modal`, `ConfirmDialog` e `SidePanel` ficam fechados; o axe não os cobre abertos (foco e contraste da janela só são testados nos testes do próprio componente).

## Entrega

Componentes base do canvas (Partes A, B e C): `Button` com variantes e tamanhos, `Badge`/`StatusBadge` com tons por situação, `Card`/`Section`, `Stat`, `Banner`, `SummaryBox`, `Avatar`/`Person`, `PageHeader`, `Field`/`Input`/`Select`/`Textarea`, `Checkbox`, `Switch`, `RadioCard`, `Tabs`, `QuickFilters`, `DataTable` (coluna numérica, linha de 52 px, seleção), `Pagination`, `Stepper`, os quatro estados no visual novo, `Modal` (cabeçalho e rodapé), `ConfirmDialog` (com `SummaryBox`), `SidePanel` (680 px e abas) e a vitrine em `/dev/vitrine` (só em desenvolvimento). Os componentes existentes mantêm nome e props; os valores antigos de variante (`default`, `outline`, `ghost`, `icon`) continuam aceitos.

Verificação: rodada 1 OK (estado `0cf88b140821d6e9f52be525e917d4615f067340`); `npm run lint`, `typecheck`, `build` e `test:cov` (1516 testes) passaram e o `e2e` (@critical) teve 81 testes passando. O `dist` não contém a vitrine.

Decisões: D1 (compatibilidade, sem renomear), D2 (botão visual de 32/40/48 px com área de toque de 44 px por `after:`), D3 (um só mapa de tons por chave de situação), D4 (sem dependência nova), D5 (vitrine em `src/dev`, fora do build), D6 (caixa alta do `PageHeader` por CSS). Desvios de construção: `status-tone.ts` cobre chaves extras que já aparecem no `pt-BR.json`; três testes antigos passaram a exigir `after:min-h-11`/`after:min-w-11`; `ConfirmDialog` põe `aria-required` direto no `Textarea` (sem `required` no `Field`); a largura de 680 px é `md:w-[min(680px,100%)]`.

Limitações: a vitrine não foi aberta no navegador (CA24 cobre só o build); o canvas não foi comparado lado a lado (issue #416); o axe não cobre `Modal`, `ConfirmDialog` e `SidePanel` abertos. Sugestões da verificação não aplicadas: linha em branco sobrando no fim de `ConfirmDialog.tsx`, automatizar a conferência do `dist` e as peças da vitrine no teste.
