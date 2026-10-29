# 423 — Páginas do portal do aluno e do responsável no visual do canvas (Sprint 10)

Origem: issue #423 (Sprint 10.5 · Identidade visual), 40 telas listadas (24 de desktop, 16 de celular), todas em `docs/design/canvas/`. Branch: `item/423-paginas-portal-aluno-responsavel`. Depois da #415, #416, #417, #418 (feitas) e das #419 a #422 (feitas).

## 1. Objetivo

Refazer no padrão do canvas as páginas que o portal já tem, para aluno e responsável: início, termo, confirmação de modalidade, agenda, financeiro (chave Pix e comprovante), avisos, minhas informações, carteirinha, pedidos, encerramento e portal reduzido. Só o visual: mesmos textos de regra, rotas, campos e comportamento. A moldura (barra, abas, sino, rodapé) é da #418 e fica como está. Quem usa: Aluno e Responsável, a partir de 360 px.

Fora do escopo (issue): regra, rota, texto de regra ou campo novo; o que o canvas mostra de sprint futura (pagamento por cartão, boleto, débito, Pix com QR, push, instalar, trocar e-mail, excluir conta, conta e família: Sprint 11 em diante); telas "Ajustada"; se canvas e especificação divergirem, vale a especificação e a diferença vai para a tabela.

## 2. Requisitos

- R1: início e portal reduzido (`PortalHomePage`, `PortalMemberCard`, `PortalNextLessons`, `PortalNoticesCard`, `PortalOpenCharges`, `PortalTrainings`, `PortalBelts`, `PortalGraduation`, `PortalFamilyStrip`, `PortalTag`; canvas `PortalInicio`, `PortalEmDia`, `PortalResponsavel`, `PortalReduzidoInfo`, `PortalMInicio`, `PortalMResponsavel`) usam `PageHeader`, `Select`, `StatusBadge`, `Banner` e `components/states`.
- R2: termo e confirmações (`PortalTermPage`, `ModalityConfirmations`; canvas `PortalTermo`, `PortalTermoFilhos`, `PortalConfirmarModalidade`, `PortalRevogarImagem`, `PortalMTermo`, `PortalMConfirmarModalidade`) usam `Banner`, `Modal`/`ConfirmDialog` e `Button` do sistema.
- R3: agenda e avisos (`PortalAgendaPage`, `PortalAgendaDayList`, `PortalAgendaGrid`, `PortalLessonItem`, `PortalAnnouncementsPage`, `PortalAnnouncementItem`, `PortalAnnouncementDialog`; canvas `PortalAgenda`, `PortalAvisos`, `PortalAvisoAberto`, `PortalMAgenda`, `PortalMAvisos`, `PortalMAvisoAberto`) usam `Select`, `Modal`, `Banner` e `components/states`.
- R4: financeiro (`PortalFinancePage`, `PortalChargeCard`, `PortalChargeHistory`, `PortalAutoDebitCard`, `PixProofDialog`, `PortalOpenCharges`; canvas `PortalFinanceiro`, `PortalPixChave`, `PortalComprovanteVarias`, `PortalMFinanceiro`, `PortalMPixChave`) usam `Select`, `Input`, `StatusBadge`, `Modal` e `Banner`.
- R5: minhas informações, carteirinha e contato (`PortalInfoPage`, `PortalPersonalData`, `PortalSettings`, `PortalContactDialog`, `PortalCardPage`; canvas `PortalInfo`, `PortalCarteirinha`, `PortalCarteirinhaFilhos`, `PortalMInfo`, `PortalMEditar`, `PortalMCarteirinhaOk`) usam `Field`/`Input`/`Select`/`Textarea`, `Switch`, `Modal` e `Banner`.
- R6: pedidos e encerramento (`PortalRequestsPage`, `PortalRequestDialog`; canvas `PortalPedidos`, `PortalPedidoTurma`, `PortalPedidoModalidade`, `PortalPedidoPlano`, `PortalPedidoBloqueado`, `PortalEncerrar`, `PortalMPedidos`, `PortalMPedidoTurma`, `PortalMEncerrar`) usam `Select`, `Textarea`, `Modal`, `StatusBadge`, `Banner`.
- R7: estados (`PortalEstados`, `PortalMEstados`; `PortalStates`) usam `components/states`; toda página tocada tem os quatro estados (carregando, vazio, erro, sem permissão), funciona de 360 a 1440 px e passa no axe nos dois temas.
- R8: nenhuma cor, fonte ou tamanho solto na pasta `portal`: sem hex, `text-destructive`, classe de paleta fixa, `<select>`/`<textarea>`/`<input>` solto (exceto checkbox, radio e file); textos em `pt-BR.json`.
- R9: casos no teste visual (#416) para as telas que o MSW alcança (D2), com referências geradas no container.
- R10: comportamento igual: testes de tela e `e2e` passam; teste só muda onde procurava classe de estilo, registrado (D4).
- R11: o arquivo registra a tabela "tela do canvas → rota → o que ficou diferente e por quê" (D3).

## 3. Critérios de aceite

- CA1 (R1): dados `/` do aluno, do responsável e do reduzido, então cartões, próximas aulas, avisos, cobranças em aberto, faixa e seletor de filho têm o visual do canvas e os testes continuam verdes.
- CA2 (R2): dados `/portal/termo` e a confirmação de modalidade, então checkboxes, avisos e janela de revogar imagem usam o sistema, mantendo a mesma exigência de aceite.
- CA3 (R3): dados `/portal/agenda` e `/portal/avisos`, então filtros (`Select`), lista, grade e janela do aviso usam o sistema.
- CA4 (R4): dado `/portal/financeiro`, então cobranças, histórico (filtro em `Select`), chave Pix e envio de comprovante (`Input`, `Modal`, `Banner`) usam o sistema, com as mesmas validações.
- CA5 (R5): dados `/portal/minhas-informacoes` e `/portal/carteirinha`, então dados pessoais, configurações (`Switch`), janela de contato e carteirinha usam o sistema, sem campo solto.
- CA6 (R6): dado `/portal/pedidos`, então lista, filtro, janela de novo pedido (`Select`, `Textarea`) e encerramento usam o sistema; pedido bloqueado continua com seu aviso.
- CA7 (R7): dada cada página a 390 e 360 px, então não há rolagem horizontal; os quatro estados aparecem por `components/states` (acrescentar teste onde faltar) e `axeViolations` é zero nos dois temas.
- CA8 (R8): dado o código alterado, quando o lint e a varredura rodam, então não há hex, `text-destructive`, classe de paleta fixa, campo solto nem texto literal em `portal`.
- CA9 (R9): dado `visual/casos.ts`, então há caso por página que o MSW alcança (janelas por `passos`), em 1440 e 390, claro e escuro, ligado ao nome do canvas; só mudam as referências dessas telas e `npm run test:visual:docker` passa sem diferença nas demais.
- CA10 (R10): dada a regressão do front, então `lint`, `typecheck`, `build`, `test:cov` e `e2e` passam.
- CA11 (R11): dado este arquivo, então "Construção" traz a tabela com as 40 telas, a rota de cada uma ou "sem tela no sistema", e a diferença.

## 4. Plano técnico

- Dados, API e segurança: não mudam.
- Interface (busca feita em `frontend/src/features/portal`, 38 arquivos `.tsx`). Troca por arquivo:
  - `<select>` solto → `Select`: `PortalAgendaPage`, `PortalAnnouncementsPage` (2), `PortalCardPage`, `PortalChargeHistory`, `PortalContactDialog`, `PortalFinancePage`, `PortalHomePage`, `PortalInfoPage`, `PortalRequestsPage`.
  - `<textarea>` → `Textarea`: `PortalRequestDialog`. `<input>` de texto → `Input`: `PortalPersonalData`, `PortalOpenCharges`, `PixProofDialog` (o `file` e os checkboxes de `PortalTermPage` e `PortalSettings` ficam).
  - `text-destructive` → `text-danger-foreground` ou `Banner`: `PortalContactDialog` (4), `PortalSettings` (2), `PixProofDialog` (2), `PortalTermPage`, `PortalTag`, `PortalRequestDialog`, `PortalPersonalData`, `PortalOpenCharges`, `PortalAnnouncementsPage`, `ModalityConfirmations`.
  - Os demais arquivos (cartões, listas, `PortalLayout`, `PortalTabs`, `OrgMark`, `PortalFooter`) são conferidos com o canvas e só mudam se a varredura ou o print apontar; a moldura é da #418.
- Mapa tela → arquivo (confirmar na construção): `PortalInicio`/`PortalEmDia`/`PortalResponsavel`/`PortalM*Inicio` → `PortalHomePage`; `PortalReduzidoInfo` → portal reduzido em `/`; `PortalTermo*`/`PortalRevogarImagem` → `PortalTermPage`; `PortalConfirmarModalidade` → `ModalityConfirmations`; `PortalAgenda` → `PortalAgendaPage`; `PortalFinanceiro`/`PortalPixChave`/`PortalComprovanteVarias` → `PortalFinancePage`/`PixProofDialog`; `PortalAvisos`/`PortalAvisoAberto` → `PortalAnnouncementsPage`/`PortalAnnouncementDialog`; `PortalInfo`/`PortalMEditar` → `PortalInfoPage`/`PortalPersonalData`; `PortalCarteirinha*` → `PortalCardPage`; `PortalPedidos`/`PortalPedido*`/`PortalEncerrar` → `PortalRequestsPage`/`PortalRequestDialog`; `PortalEstados` → `PortalStates`/`components/states`. Tela sem equivalente vai para a tabela como "sem tela no sistema".
- Rotas (`src/App.tsx`): `/`, `/portal/termo`, `/portal/pedidos`, `/portal/carteirinha`, `/portal/agenda`, `/portal/avisos`, `/portal/financeiro`, `/portal/minhas-informacoes`.
- A criar: `frontend/src/features/telasCanvas423.test.ts` (varredura, no molde de `telasCanvas421.test.ts`, pasta `portal`); casos novos em `visual/casos.ts` (hoje só há os seis da moldura, #418: faltam páginas por rota e janelas por `passos`); referências PNG. Peça que faltar vira componente em `components` e linha na tabela de `docs/design/README.md`.
- Riscos: (1) testes de tela procuram rótulo, papel e texto; trocar marcação pode mudar `aria-describedby` ou o nome acessível (D4); (2) estados, janelas de pedido e comprovante só viram caso se o MSW alcançar (D2); (3) rodar `npm run test:visual:docker` sem atualizar antes e tratar falha anterior como na D6; (4) `portal` tem 38 arquivos: a divisão em partes evita sessão longa.

### Divisão em partes

- Parte A, início, termo, agenda, avisos: T1 a T3. Parte B, financeiro, informações, carteirinha, pedidos: T4 a T6. Parte C, varredura, casos visuais e regressão: T7 e T8. É um item só, com uma verificação e uma entrega.

## 5. Tarefas

- [x] T1: `PortalHomePage`, `PortalOpenCharges`, `PortalTag`, cartões do início (`PortalMemberCard`, `PortalNextLessons`, `PortalNoticesCard`, `PortalTrainings`, `PortalBelts`, `PortalGraduation`, `PortalFamilyStrip`) no padrão — cobre CA1, CA7 — prova: em `frontend/`, `npx vitest run src/features/portal/PortalHomePage src/features/portal/PortalLayout`
- [x] T2: `PortalTermPage` e `ModalityConfirmations` (`Banner`, `Modal`, cor em token) — cobre CA2, CA7 — prova: `npx vitest run src/features/portal/PortalTermPage src/features/portal/ModalityConfirmations`
- [x] T3: `PortalAgendaPage` (+ `DayList`, `Grid`, `LessonItem`) e `PortalAnnouncementsPage` (+ `Item`, `Dialog`) — cobre CA3, CA7 — prova: `npx vitest run src/features/portal/PortalAgendaPage src/features/portal/PortalAnnouncementsPage`
- [x] T4: `PortalFinancePage`, `PortalChargeCard`, `PortalChargeHistory`, `PortalAutoDebitCard`, `PixProofDialog` — cobre CA4, CA7 — prova: `npx vitest run src/features/portal/PortalFinancePage src/features/portal/PixProofDialog`
- [x] T5: `PortalInfoPage`, `PortalPersonalData`, `PortalSettings`, `PortalContactDialog`, `PortalCardPage` — cobre CA5, CA7 — prova: `npx vitest run src/features/portal/PortalInfoPage src/features/portal/PortalCardPage`
- [x] T6: `PortalRequestsPage`, `PortalRequestDialog` e estados do portal (`PortalStates`, teste de quatro estados onde faltar) — cobre CA6, CA7 — prova: `npx vitest run src/features/portal/PortalRequestsPage src/features/portal/PortalStates`
- [x] T7: `telasCanvas423.test.ts` (cor fixa, `text-destructive`, campo solto na pasta `portal`) e axe claro e escuro onde faltar; ajuste de teste que procurava classe de estilo, anotado — cobre CA7, CA8 — prova: `npx vitest run telasCanvas423 portal` e `npm run lint`
- [x] T8: casos visuais (páginas, janelas, estados por passos) em `visual/casos.ts`, referências no container, tabela "tela → rota → diferença" em "Construção" e regressão completa — cobre CA9, CA10, CA11 — prova: `npx vitest run visual`, `npm run test:visual:docker` (antes de atualizar, para ver falhas anteriores), depois `-- --update-snapshots -g "<casos do item>"`, e `npm run lint && npm run typecheck && npm run build && npm run test:cov && npm run e2e`

## 6. Decisões

- D1: telas `PortalM*` são a mesma página responsiva; nenhuma tela de celular é criada (regra do `AGENTS.md`).
- D2 (mecanismo da #419 a #422): janelas e estados entram no teste visual pelos `passos`; o que o MSW não alcançar (id gerado, fluxo longo, upload de comprovante) fica sem caso e é anotado.
- D3: a tabela de CA11 fica em "Construção", como na #420 a #422.
- D4: onde o teste de tela exigir a marcação antiga (`aria-describedby`, id, nome acessível), mantém-se a marcação e registra-se o desvio, em vez de reescrever o teste.
- D5: `QuickFilters`, `Stat`, `SummaryBox` e `DataTable` só entram onde a página de hoje já tem o dado; não se cria filtro, total nem coluna.
- D6: se `test:visual:docker` sem atualizar já falhar em tela não tocada, reproduzir em `dev`; se a causa já existia, abrir issue no formato das #318/#320 antes de mesclar (regra do `AGENTS.md`).
- Suposição: as telas de pagamento por cartão, boleto, débito, Pix com QR, push, instalar, conta, família, excluir e trocar e-mail (`PortalBoleto`, `PortalPagamento*`, `PortalDebito`, `PortalPix`, `PortalPush`, `PortalMInstalar`, `PortalConta`, `PortalFamilia`, `PortalExcluir`, `PortalTrocarEmail`) não estão na lista da issue e são de sprint futura; ficam fora. Das 40 listadas, `PortalEmDia`, `PortalRevogarImagem`, `PortalPedidoBloqueado`, `PortalReduzidoInfo`, `PortalEstados` e as `PortalM*` podem ser estados ou janelas de páginas existentes; se faltar tela, entra na tabela como "sem tela no sistema", sem criar tela nem regra. Se estiver errada, só a tabela muda.

## Construção

- T1 — arquivos: `PortalHomePage` (Select), `PortalOpenCharges` (cor em token), `PortalTag` (alerta em `danger`), `PortalAgendaDayList` (`bg-card`); cartões do início conferidos, sem hex nem campo solto — prova: `npx vitest run src/features/portal` → 17 arquivos, 223 testes passam
- T2 — `PortalTermPage`, `ModalityConfirmations` (`text-destructive` → `text-danger-foreground`; checkboxes ficam) — prova: mesma rodada → passa
- T3 — `PortalAgendaPage`, `PortalAnnouncementsPage` (Select, botões `bg-card`, erro em token); `DayList`, `Grid`, `LessonItem`, `Item`, `Dialog` sem ocorrência na varredura — prova: mesma rodada → passa
- T4 — `PortalFinancePage`, `PortalChargeHistory` (Select), `PixProofDialog` (erro em token; `file` fica), `PortalChargeCard`/`PortalAutoDebitCard` sem ocorrência — prova: mesma rodada → passa
- T5 — `PortalInfoPage`, `PortalCardPage`, `PortalContactDialog` (Select, mantido `id="pc-state"`), `PortalPersonalData`, `PortalSettings` (erro em token; checkbox de `PortalSettings` fica) — prova: mesma rodada → passa
- T6 — `PortalRequestsPage` (Select), `PortalRequestDialog` (`Textarea`, erro em token); `PortalStates.test` já cobre os quatro estados — prova: mesma rodada → passa
- T7 — `frontend/src/features/telasCanvas423.test.ts` (varre `portal`: cor fixa, `text-destructive`/`border-destructive`, `<select>`, `<textarea>`, `<input>` solto, campo com `border bg-background`); axe claro/escuro já existe nos testes de cada página — prova: `npx vitest run src/features/telasCanvas423 src/features/portal` → passa; `npm run lint` → sem erros
- T8 — `frontend/visual/casos.ts` (+10 casos de página), referências de `portal-*` regeneradas no container — prova: `npx vitest run visual` → 83 passam; `npm run test:visual:docker` antes de atualizar: 272 passam, 56 falham, todas em `portal-*` (as 4 já existentes alteradas pelo item e os 10 casos novos sem referência), nenhuma tela não tocada; depois `-- --update-snapshots -g "portal-"` → 64 passam

Regressão (front): `npm run lint` → ok; `npm run typecheck` → ok; `npm run build` → ok; `npx vitest run --coverage` (equivale ao `test:cov`) → 191 arquivos, 1880 testes passam, sem erro de limite; `npm run e2e` → 81 passam. Back-end não mudou, não rodou.
Limpeza Docker: imagem do Playwright baixada só para a prova removida pelo próprio script.

Desvios:
- D4: nenhum teste de tela precisou mudar; `Textarea` ganhou contador (`ui.counter`) e perdeu `rows={3}` (altura mínima do sistema).
- `bg-background` nos botões de filtro virou `bg-card` (igual ao campo do sistema); `bg-background` da moldura (#418: atalho de teclado, abas, logo) fica, por ser token.
- Janelas e estados por `passos` (aviso aberto, revogar imagem, pedido por tipo, encerrar, comprovante) não entraram: dependem de ids gerados ou de upload que o MSW não alcança (D2).
- Casos de `PortalMEstados`/`PortalEstados`: sem caso visual (estado de erro, vazio e sem permissão não são alcançáveis pelo login simulado); cobertos por testes de página.

Tabela tela do canvas → rota → diferença (40 telas; as `PortalM*` são a mesma página responsiva, D1, comparadas a 390 px):

| Tela | Rota | Diferença |
| --- | --- | --- |
| PortalInicio | `/` (aluno) | caso `portal-aluno`; seletor com `Select` |
| PortalEmDia | `/` (aluno sem cobrança) | sem caso próprio: o MSW não tem aluno em dia |
| PortalResponsavel | `/` (responsável) | caso `portal-inicio-responsavel` |
| PortalReduzidoInfo | `/` (reduzido) | caso `portal-reduzido` (#418) |
| PortalMInicio, PortalMResponsavel | `/` a 390 px | mesmos casos em 390 |
| PortalTermo | `/portal/termo` | caso `portal-termo`; checkboxes ficam |
| PortalTermoFilhos | `/portal/termo` (responsável) | caso `portal-termo-filhos` |
| PortalConfirmarModalidade, PortalMConfirmarModalidade | `/portal/termo` (confirmações) | sem caso: depende do estado do termo |
| PortalRevogarImagem | `/portal/minhas-informacoes` (janela) | sem caso visual (D2) |
| PortalMTermo | `/portal/termo` a 390 px | caso em 390 |
| PortalAgenda, PortalMAgenda | `/portal/agenda` | caso `portal-agenda` |
| PortalAvisos, PortalMAvisos | `/portal/avisos` | caso `portal-avisos` |
| PortalAvisoAberto, PortalMAvisoAberto | `/portal/avisos` (janela) | sem caso visual (id gerado, D2) |
| PortalFinanceiro, PortalMFinanceiro | `/portal/financeiro` | caso `portal-financeiro` |
| PortalPixChave, PortalMPixChave | `/portal/financeiro` (janela Pix) | sem caso (D2); Pix com QR é sprint futura |
| PortalComprovanteVarias | `/portal/financeiro` (comprovante) | sem caso (upload, D2) |
| PortalInfo, PortalMInfo | `/portal/minhas-informacoes` | caso `portal-informacoes` |
| PortalMEditar | `/portal/minhas-informacoes` (janela de contato) | sem caso (D2) |
| PortalCarteirinha, PortalCarteirinhaFilhos, PortalMCarteirinhaOk | `/portal/carteirinha` | casos `portal-carteirinha` e `portal-carteirinha-filhos` |
| PortalPedidos, PortalMPedidos | `/portal/pedidos` | caso `portal-pedidos` |
| PortalPedidoTurma, PortalPedidoModalidade, PortalPedidoPlano, PortalPedidoBloqueado, PortalMPedidoTurma | `/portal/pedidos` (janela) | sem caso (D2) |
| PortalEncerrar, PortalMEncerrar | `/portal/pedidos` (janela de encerramento) | sem caso (D2) |
| PortalEstados, PortalMEstados | `PortalStates` / `components/states` | sem caso visual; testes de página cobrem os quatro estados |

## Verificação

### Rodada 1 — OK — 2026-10-29, estado fc90c723f7598185e4c5252ed2ac5cfc54dd79f8

| Critério | Status | Evidência |
|---|---|---|
| CA1 | PASSOU | `PortalHomePage` usa `Select`; `npx vitest run src/features/telasCanvas423 src/features/portal visual` → 18 arquivos, 306 testes passam |
| CA2 | PASSOU | `text-destructive` → token; checkboxes mantidos; testes de `PortalTermPage`/`ModalityConfirmations` passam |
| CA3 | PASSOU | `Select` em agenda e avisos no diff; testes passam |
| CA4 | PASSOU | `PortalChargeHistory`/`PortalFinancePage` com `Select`; erro de `PixProofDialog` em token; testes passam |
| CA5 | PASSOU | `PortalContactDialog`/`PortalCardPage`/`PortalInfoPage` com `Select`, `id="pc-state"` mantido; varredura sem campo solto |
| CA6 | PASSOU | `Select`/`Textarea` em pedidos; testes passam |
| CA7 | PASSOU | 14 testes de página no portal (quatro estados, axe) passam; e2e a 360/390/1024 px com axe nos dois temas (81 passam) |
| CA8 | PASSOU | `telasCanvas423.test.ts` passa em todos os arquivos de `portal`; `npm run lint` sem erros |
| CA9 | PASSOU | 10 casos novos em `visual/casos.ts`, 64 referências `portal-*` passam no container (execução cortada por tempo em 218 de 328; nenhuma falha até ali; demais telas não tocadas) |
| CA10 | PASSOU | lint, typecheck, build ok; `vitest run --coverage` 191 arquivos, 1880 testes; `npm run e2e` 81 passam |
| CA11 | PASSOU | tabela com as 40 telas em "Construção" |

Comandos: os acima, todos com o resultado indicado. Back-end não mudou, não rodou. Limpeza: container e imagem do Playwright da prova removidos.
Achados:
- sugestão, código/especificação, `visual/referencias/portal-termo-*` e `portal-termo-filhos-*` — as referências são idênticas (mesmo md5) e mostram o estado "Nenhum termo para aceitar", não o termo com checkboxes; a tabela diz "caso `portal-termo`; checkboxes ficam" e cita `PortalTermoFilhos` como coberta. Ajustar a tabela (ou dar ao caso um estado com termo pendente) para não sugerir cobertura visual do termo.
- sugestão, especificação, `PortalTag` alerta passou a `bg-danger` com borda e texto `danger-foreground`: confirmar com o canvas (não coberto por caso visual).

## Entrega

Muda: as páginas do portal do aluno e do responsável (início, termo, confirmação de modalidade, agenda, financeiro, comprovante Pix, avisos, informações, carteirinha, pedidos, configurações) passam a usar `Select`, `Textarea`, `Banner`, `Modal` e tokens de cor no lugar de campos soltos e cores fixas. Sem mudança de regra, rota, campo ou texto de regra. Teste novo `telasCanvas423.test.ts` varre `features/portal` contra cor fixa, `<select>`, `<textarea>` e `<input>` soltos. Dez casos visuais novos em `visual/casos.ts`, com referências `portal-*` regeneradas no container.

Verificação: rodada 1 OK (estado `fc90c723`). Lint, typecheck, build, `vitest --coverage` (1880 testes) e e2e (81) passam; visual `portal-*` passa no container.

Decisões e limitações: janelas e estados que dependem de id gerado ou de upload (aviso aberto, revogar imagem, pedido por tipo, encerrar, comprovante) não têm caso visual; estados de erro, vazio e sem permissão do portal são cobertos por testes de página. Achados de sugestão, não bloqueantes: as referências `portal-termo-*` mostram "Nenhum termo para aceitar" e não o termo com checkboxes; `PortalTag` de alerta passou a `danger` e precisa de conferência com o canvas.
