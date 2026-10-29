# 422 — Cobranças, comunicação, notificações, pedidos e conferência no visual do canvas (Sprints 08 a 10, gestão)

Origem: issue #422 (Sprint 10.5 · Identidade visual), 41 telas listadas na issue, todas existentes em `docs/design/canvas/`. Branch: `item/422-telas-cobrancas-comunicacao-pedidos`. Depois da #415, #416, #417, #418, #419, #420 e #421 (feitas).

## 1. Objetivo

Refazer no padrão do canvas as telas que já existem de cobranças (mensalidades, prévia, avulsa, cancelar, renegociar, registrar pagamento, recebimentos do dia, atrasados, conferência), do recibo público, de comunicados, textos e lembretes, das notificações e dos pedidos do portal na gestão. Só o visual: mesmos textos de regra, rotas, campos e comportamento. Quem usa: Coordenador, Administrador e Professor (gestão, também a 360 px) e quem abre o recibo público.

Fora do escopo (issue): regra, rota, texto de regra ou campo novo; o que o canvas mostra de sprint futura (assinatura, graduação, relatório); telas "Ajustada" (comportamento é das issues de cada sprint); portal do aluno e do responsável (#423). Se canvas e especificação divergirem, vale a especificação e a diferença vai para a tabela.

## 2. Requisitos

- R1: cobranças (`ChargesPage`, `ChargeFilters`, `ChargeCards`, `ChargePanel`, `ChargePreviewPage`/`ChargePreviewRows`, `OneOffChargeDialog`, `StudentSearch`, `CancelChargeDialog`, `CancelNextDialog`, `ChangeDueDialog`, `AdvanceChargesDialog`, `RenegotiateDialog`, `SendReminderDialog`, `DailyReceiptsPage`, `OverduePage`/`OverdueRows`, `payments/RegisterPaymentDialog`; canvas `Mensalidades`, `GerarProximas`, `CobrancaPainel`, `CobrancaAvulsa`, `CobrancaCancelar`, `PagamentoRegistrar`, `Renegociar`, `RecebimentosDia`, `Fechamento`, `Atrasados`, `AlunoFinanceiro`, `EstadoErro`, `GMCobrancas`, `GMCobrancaPainel`, `GMPagamento`, `GMRenegociar`, `GMFinanceiro`, `GMFichaFinanceiro`, `GMMatricula4`) usam `PageHeader`, `QuickFilters`/`Select`/`Input`/`Field`, `DataTable`/`Person`, `Stat`/`SummaryBox`, `StatusBadge`, `Modal`/`ConfirmDialog`, `Banner` e `components/states`.
- R2: recibo público (`payments/PublicReceiptPage`; canvas `SiteMRecibo`) no padrão, sem cor fixa; mesmo conteúdo e CPF mascarado.
- R3: comunicados e textos (`AnnouncementsPage`, `AnnouncementFilters`, `NewAnnouncementPage`, `AnnouncementTargetPicker`, `AnnouncementPreviewPanel`, `CancelAnnouncementDialog`, `texts/TextsPage`; canvas `Comunicados`, `ComunicadoNovo`, `ComunicadoCancelar`, `ComunicadoLimite`, `ComunicadoOrganizacao`, `AulaAvisarTurma`, `ConfigTextos`, `LembretesAuto`, `GMComunicados`, `GMNovoComunicado`) usam `Field`/`Input`/`Select`/`Textarea`, `RadioCard`, `Modal`, `Banner`, `StatusBadge` e `components/states`.
- R4: notificações (`BellButton`, `NotificationPanel`; canvas `Notificacoes`, `GMNotificacoes`) usam os componentes novos, sem campo solto nem cor fixa.
- R5: pedidos e conferência (`PortalRequestsPage`, `RequestSummary`, `RefuseRequestDialog`, `PaymentProofsPage`, `ProofDialogs`, `ProofImage`; canvas `PedidosPortal`, `PedidoRecusar`, `PedidoEncerramentoRecusar`, `PedidoAprovadoFluxo`, `EmConferencia`, `ComprovanteVarias`, `GMPedidos`, `GMPedidoRecusar`, `GMConferencia`) usam `Modal`, `Textarea`, `Banner`, `StatusBadge`, `DataTable` e `components/states`.
- R6: toda tela tocada tem os quatro estados (carregando, vazio, erro, sem permissão) por `components/states`, funciona de 360 a 1440 px e passa no axe nos dois temas.
- R7: nenhuma cor, fonte ou tamanho solto nas pastas `charges`, `payments`, `announcements`, `notifications`, `payment-proofs`, `portal-requests` e `texts`: sem hex, sem `text-destructive`, sem classe de paleta fixa, sem `<select>`/`<textarea>`/`<input>` soltos (exceto checkbox, radio e file); textos em `pt-BR.json`.
- R8: as telas ganham casos no teste visual (#416), com referências geradas no container (D2).
- R9: comportamento igual: testes de tela e `e2e` continuam passando; teste só muda onde procurava classe de estilo, e a troca fica registrada (D4).
- R10: o arquivo registra a tabela "tela do canvas → rota → o que ficou diferente e por quê" (D3).

## 3. Critérios de aceite

- CA1 (R1): dado `/cobrancas`, quando abre, então lista, filtros, cartões de totais e painel da cobrança têm o visual do canvas e os testes de `charges` continuam verdes.
- CA2 (R1): dadas as janelas cobrança avulsa, cancelar, alterar vencimento, antecipar, renegociar, enviar lembrete e registrar pagamento, então usam `Modal`, `Field`/`Input`/`Select`/`Textarea` e `Banner` do sistema, com as mesmas validações e resultados.
- CA3 (R1): dados `/cobrancas/previa`, `/cobrancas/recebimentos` e `/cobrancas/atrasados`, então usam `PageHeader`, tabela e totais do sistema, sem campo solto.
- CA4 (R2): dado `/recibo/:codigo`, então o recibo usa tokens do sistema nos dois temas e mantém o CPF mascarado.
- CA5 (R3): dados `/comunicados` e `/comunicados/novo`, então lista, filtros, seletor de destino, prévia, limite e cancelamento usam os componentes novos.
- CA6 (R3): dado `/configuracoes/textos`, então textos e lembretes automáticos usam `Textarea`/`Input`/`Switch` do sistema e mantêm as mesmas validações.
- CA7 (R4): dado o sino da barra, quando abre o painel, então lista, vazio, erro e marcar como lida usam os componentes novos.
- CA8 (R5): dados `/pedidos` e `/cobrancas/conferencia`, então lista, janelas de recusar/aprovar e imagem do comprovante usam os componentes novos; recusa continua exigindo motivo.
- CA9 (R6): dada cada tela tocada a 390 e 360 px, então não há rolagem horizontal; carregando, vazio, erro com "Tentar de novo" e sem permissão aparecem por `components/states` (acrescentar teste onde faltar) e `axeViolations` é zero nos dois temas.
- CA10 (R7): dado o código novo ou alterado, quando o lint e a varredura rodam, então não há hex, `text-destructive`, classe de paleta fixa, campo solto nem texto literal nas pastas tocadas.
- CA11 (R8): dado `visual/casos.ts`, então há caso por tela que o MSW alcança (janelas por `passos`), em 1440 e 390, claro e escuro, ligado ao nome do canvas; só as referências dessas telas mudam, e `npm run test:visual:docker` passa sem diferença nas telas não tocadas.
- CA12 (R9): dada a regressão do front, então `lint`, `typecheck`, `build`, `test:cov` (100% em `features/{billing,charges,payments,subscription,deactivation}`) e `e2e` passam.
- CA13 (R10): dado este arquivo, então "Construção" traz a tabela com as 41 telas da issue, a rota de cada uma ou "sem tela no sistema", e a diferença.

## 4. Plano técnico

- Dados, API e segurança: não mudam (permissão continua no servidor; só marcação e classes).
- Interface, vista no repositório. Arquivos com `<select>`, `<textarea>`, `<input>` solto, `text-destructive` ou hex (busca feita): `charges/` (AdvanceChargesDialog, CancelChargeDialog, CancelNextDialog, ChangeDueDialog, ChargeCards, ChargeFilters, ChargePreviewPage, DailyReceiptsPage, OneOffChargeDialog, OverduePage, OverdueRows, RenegotiateDialog, SendReminderDialog, StudentSearch), `payments/RegisterPaymentDialog`, `announcements/` (AnnouncementFilters, AnnouncementPreviewPanel, AnnouncementTargetPicker, CancelAnnouncementDialog, NewAnnouncementPage), `notifications/NotificationPanel`, `payment-proofs/ProofDialogs`, `portal-requests/` (PortalRequestsPage, RefuseRequestDialog), `texts/TextsPage`. Os demais arquivos das pastas (ChargesPage, ChargePanel, PublicReceiptPage, AnnouncementsPage, BellButton, PaymentProofsPage, RequestSummary) são conferidos e só mudam se a varredura ou o canvas apontar.
- Troca mecânica, como na #420/#421: `select`/`input`/`textarea` soltos viram `Select`/`Input`/`Textarea` de `components/ui`; `text-destructive` vira `text-danger-foreground` ou `Banner`; situação em `StatusBadge`; totais em `Stat`/`SummaryBox`; filtros por situação em `QuickFilters` só onde já há o filtro (D5). Checkbox, radio e file nativos continuam.
- Mapa tela → arquivo (confirmar na construção): `Mensalidades`/`GMCobrancas` → `ChargesPage`; `GerarProximas`/`Fechamento` → `ChargePreviewPage`; `CobrancaPainel`/`GMCobrancaPainel` → `ChargePanel`; `CobrancaAvulsa` → `OneOffChargeDialog`; `CobrancaCancelar` → `CancelChargeDialog`/`CancelNextDialog`; `PagamentoRegistrar`/`GMPagamento` → `RegisterPaymentDialog`; `Renegociar`/`GMRenegociar` → `RenegotiateDialog`; `RecebimentosDia` → `DailyReceiptsPage`; `Atrasados` → `OverduePage`; `AlunoFinanceiro`/`GMFichaFinanceiro`/`GMMatricula4`/`GMFinanceiro` → aba financeira da ficha (`students/StudentFinancialTab`, já tocada na #421) e passo de vencimento, só conferidas; `EstadoErro` → estado de `components/states`; `SiteMRecibo` → `PublicReceiptPage`; comunicados → `announcements/`; `ConfigTextos`/`LembretesAuto` → `TextsPage`; `AulaAvisarTurma` → ação de aviso na agenda (conferir); `Notificacoes`/`GMNotificacoes` → `NotificationPanel`; `PedidosPortal`/`PedidoRecusar`/`PedidoEncerramentoRecusar`/`PedidoAprovadoFluxo`/`GMPedidos`/`GMPedidoRecusar` → `PortalRequestsPage`/`RefuseRequestDialog`; `EmConferencia`/`ComprovanteVarias`/`GMConferencia` → `PaymentProofsPage`/`ProofDialogs`. Tela sem equivalente vai para a tabela como "sem tela no sistema".
- Rotas (`src/App.tsx`): `/cobrancas`, `/cobrancas/previa`, `/cobrancas/recebimentos`, `/cobrancas/atrasados`, `/cobrancas/conferencia`, `/comunicados`, `/comunicados/novo`, `/pedidos`, `/configuracoes/textos`, `/recibo/:codigo`.
- A criar: `frontend/src/features/telasCanvas422.test.ts` (varredura, no molde de `telasCanvas421.test.ts`, com as sete pastas); casos novos em `visual/casos.ts` (já existem `cobrancas*`, `pedidos`, `comunicados*`, `configuracoes-textos`; faltam janelas por `passos`, recibo, sino); referências PNG. Peça que faltar vira componente em `components` e linha na tabela de `docs/design/README.md`.
- Riscos: (1) testes de tela procuram rótulo, papel e texto, e trocar marcação pode mudar `aria-describedby` (D4); (2) `charges` e `payments` exigem 100% de cobertura: trocar marcação sem ramo novo, sem código morto; (3) estados, recibo e janelas só viram caso por passos/rota que o MSW alcança (D2); (4) a #420 teve `visual` vermelho no PR: rodar `npm run test:visual:docker` sem atualizar antes e tratar a falha anterior como na D6; (5) tamanho das referências; (6) `charges` tem 14 arquivos a trocar: a divisão em partes evita sessão longa.

### Divisão em partes

- Parte A, cobranças (lista, painel, prévia, recebimentos, atrasados): T1 e T2. Parte B, janelas de cobrança e pagamento, recibo: T3 e T4. Parte C, comunicados, textos e notificações: T5 e T6. Parte D, pedidos e conferência, varredura, casos visuais e regressão: T7 a T9. É um item só, com uma verificação e uma entrega.

## 5. Tarefas

- [x] T1: `ChargesPage`, `ChargeFilters`, `ChargeCards`, `ChargePanel`, `StudentSearch` no padrão (`Select`, `Input`, `Stat`, `StatusBadge`, `Banner`) — cobre CA1, CA9 — prova: em `frontend/`, `npx vitest run src/features/charges/ChargesPage src/features/charges/ChargePanel src/features/charges/ChargeFilters`
- [x] T2: `ChargePreviewPage`/`ChargePreviewRows`, `DailyReceiptsPage`, `OverduePage`/`OverdueRows` — cobre CA3, CA9 — prova: `npx vitest run src/features/charges/ChargePreview src/features/charges/DailyReceipts src/features/charges/Overdue`
- [x] T3: janelas `OneOffChargeDialog`, `CancelChargeDialog`, `CancelNextDialog`, `ChangeDueDialog`, `AdvanceChargesDialog`, `RenegotiateDialog`, `SendReminderDialog` (`Modal`, `Field`, `Input`, `Select`, `Textarea`, `Banner`) — cobre CA2 — prova: `npx vitest run src/features/charges`
- [x] T4: `payments/RegisterPaymentDialog` e `PublicReceiptPage` — cobre CA2, CA4, CA9 — prova: `npx vitest run src/features/payments`
- [x] T5: `AnnouncementsPage`, `AnnouncementFilters`, `NewAnnouncementPage`, `AnnouncementTargetPicker`, `AnnouncementPreviewPanel`, `CancelAnnouncementDialog` — cobre CA5, CA9 — prova: `npx vitest run src/features/announcements`
- [x] T6: `texts/TextsPage` e `notifications/NotificationPanel`/`BellButton` — cobre CA6, CA7, CA9 — prova: `npx vitest run src/features/texts src/features/notifications`
- [x] T7: `PortalRequestsPage`, `RefuseRequestDialog`, `RequestSummary`, `PaymentProofsPage`, `ProofDialogs`, `ProofImage` — cobre CA8, CA9 — prova: `npx vitest run src/features/portal-requests src/features/payment-proofs`
- [x] T8: `telasCanvas422.test.ts` (cor fixa, `text-destructive`, campo solto nas sete pastas) e axe claro e escuro onde faltar; ajuste de teste que procurava classe de estilo, anotado — cobre CA9, CA10 — prova: `npx vitest run telasCanvas422 a11y` e `npm run lint`
- [x] T9: casos visuais (janelas, recibo, sino e estados por passos) em `visual/casos.ts`, referências no container, tabela "tela → rota → diferença" em "Construção" e regressão completa — cobre CA11, CA12, CA13 — prova: `npx vitest run visual`, `npm run test:visual:docker` (antes de atualizar, para ver falhas anteriores), depois `-- --update-snapshots -g "<casos do item>"`, e `npm run lint && npm run typecheck && npm run build && npm run test:cov && npm run e2e`

## 6. Decisões

- D1: telas `GM*` são a mesma tela responsiva; nenhuma tela de celular é criada (regra do `AGENTS.md`). `AlunoFinanceiro`, `GMFichaFinanceiro`, `GMMatricula4` e `GMFinanceiro` são a aba da ficha e o passo de vencimento já tratados na #421; entram só na tabela, com conferência.
- D2 (mecanismo da #419/#420/#421): janelas e estados entram no teste visual pelos `passos`; o que o MSW não alcançar (id gerado, fluxo longo, imagem de comprovante) fica sem caso e é anotado.
- D3: a tabela de CA13 fica em "Construção", como na #420 e #421.
- D4: onde o teste de tela exigir a marcação antiga (`aria-describedby`, id), mantém-se a marcação e registra-se o desvio, em vez de reescrever o teste.
- D5: `QuickFilters`, `SummaryBox`, `Stat` e `DataTable` só entram onde a tela de hoje já tem o dado (filtro, total, tabela); não se cria filtro, total nem coluna nova.
- D6: se `test:visual:docker` sem atualizar já falhar em tela não tocada, reproduzir em `dev`; se a causa já existia, abrir issue no formato das #318/#320 antes de mesclar (regra do `AGENTS.md`).
- Suposição: as 41 telas têm equivalente no sistema, exceto possivelmente `ComunicadoLimite`, `ComunicadoOrganizacao`, `AulaAvisarTurma`, `PedidoAprovadoFluxo`, `EstadoErro` e as `GM*`, que podem ser estados ou janelas de telas existentes. Se faltar tela, entra na tabela como "sem tela no sistema", sem criar tela nem regra. Se estiver errada, só a tabela muda.

## Construção

- T1 — arquivos: `charges/{ChargeCards,ChargeFilters,StudentSearch}.tsx` (`ChargesPage` e `ChargePanel` só conferidos, sem cor fixa nem campo solto) — `StatusBadge` da cobrança passa a usar o `StatusBadge` do sistema (mapa de tons próprio removido), `Select`/`Input` do sistema nos filtros e na busca de aluno — prova: `npx vitest run src/features/charges` → 8 arquivos, 83 testes passam
- T2 — arquivos: `charges/{ChargePreviewPage,DailyReceiptsPage,OverduePage,OverdueRows}.tsx` (`ChargePreviewRows` conferido) — `Select`/`Input`, cartões de total em `rounded-card bg-card`, `text-destructive` → `text-danger-foreground`, aviso de limite em `Banner` — prova: mesma rodada de `charges` → passa
- T3 — arquivos: `charges/{OneOffChargeDialog,CancelChargeDialog,CancelNextDialog,ChangeDueDialog,AdvanceChargesDialog,RenegotiateDialog,SendReminderDialog}.tsx`, `components/CharCounter.tsx` (nova constante `CHAR_COUNTER_FIELD`, o padrão do `CharCounter` ficou como estava) — erro do alto em `Banner`, campos em `Input`/`Select` — prova: `npx vitest run src/features/charges` → passa
- T4 — arquivos: `payments/RegisterPaymentDialog.tsx` (+ caso axe nos dois temas em `RegisterPaymentDialog.test.tsx`); `PublicReceiptPage` conferido (já em tokens, CPF mascarado intacto) — prova: `npx vitest run src/features/payments` → 3 testes passam
- T5 — arquivos: `announcements/{AnnouncementFilters,AnnouncementTargetPicker,AnnouncementPreviewPanel,CancelAnnouncementDialog,NewAnnouncementPage}.tsx` (`AnnouncementsPage` conferido) — prova: `npx vitest run src/features/announcements` → 23 testes passam
- T6 — arquivos: `texts/TextsPage.tsx` (`Input`, `Textarea`, `Banner`, prévia em `bg-card`), `notifications/NotificationPanel.tsx` (`Switch` do sistema; `BellButton` conferido) — prova: `npx vitest run src/features/texts src/features/notifications` → 19 testes passam
- T7 — arquivos: `portal-requests/{PortalRequestsPage,RefuseRequestDialog}.tsx`, `payment-proofs/ProofDialogs.tsx` (`Select`/`Textarea`); `RequestSummary`, `PaymentProofsPage` e `ProofImage` conferidos — prova: `npx vitest run src/features/portal-requests src/features/payment-proofs` → 16 testes passam
- T8 — arquivos: `src/features/telasCanvas422.test.ts` (35 casos, sete pastas) — prova: `npx vitest run src/features/telasCanvas422` → 35 passam; `npm run lint` → sem erro. Axe nos dois temas já existia nos testes de página de todas as pastas; só faltava `RegisterPaymentDialog`, acrescentado (T4)
- T9 — arquivos: `visual/casos.ts` (+5 casos: `cobranca-avulsa`, `cobranca-antecipar`, `notificacoes`), referências de `cobrancas`, `cobrancas-atrasados`, `cobrancas-previa`, `cobrancas-recebimentos`, `comunicados`, `comunicados-novo`, `pedidos` e dos três casos novos (4 PNG cada) — prova: `npm run test:visual:docker` antes de atualizar (os 40 testes das telas tocadas mudaram como esperado; `professor-novo` mudou por causa de uma troca no `CharCounter`, desfeita e trocada pela constante `CHAR_COUNTER_FIELD`); depois `-- --update-snapshots -g "(…) (1440|390) "` → 40 passam; rodada final sem atualizar → 288 passam

Regressão (front): `npm run lint && npm run typecheck && npm run build` → ok; `npm run test:cov` → 190 arquivos, 1831 testes passam, cobertura mínima atendida (100% em `charges`/`payments`); `npm run e2e` → 81 passam; `npm run test:visual:docker` → 288 passam. Back-end não alterado, não rodou.
Desvios:
- D4: filtros rápidos de `ChargeFilters` e de `StudentFilters` seguem como botões próprios, não `QuickFilters`: o teste exige o nome "Em atraso (4)" e o componente do sistema nomeia "Em atraso, 4". Dados em `dl`, não em `Stat`, pelo mesmo motivo (testes por `dt`/`dd`); só os tokens do cartão mudaram. Rótulo envolvente de `label` mantido em vez de `Field` nas janelas de cobrança.
- Sem caso visual: `pedido-recusar` e `conferencia-recusar` (o MSW não tem pedido nem comprovante em aberto, D2), recibo público (o MSW começa sem recibo), painel da cobrança, registrar pagamento, cancelar, alterar vencimento, renegociar e lembrete (id gerado, fluxo longo ou seleção em lote).
- `Textarea` de recusa de pedido e de comprovante agora mostra o contador do sistema; o de `RegisterPaymentDialog` limita por código (`slice`) para manter o contador próprio do texto.
Não é do item: `specs/itens/422-…md` (arquivo da fase 2) ainda sem commit.

### Tabela tela do canvas → rota → diferença (CA13, 41 telas)

| Tela | Rota / arquivo | O que ficou diferente e por quê |
| --- | --- | --- |
| Mensalidades | `/cobrancas` | Filtros em `Select`/`Input`, selo em `StatusBadge`; atalhos de situação mantêm o botão próprio (D4) |
| GMCobrancas | `/cobrancas` a 390 px | Mesma tela responsiva (D1) |
| GerarProximas | janela "Gerar próximas mensalidades" em `/cobrancas` | `Input` e erro em `Banner`; caso visual `cobranca-antecipar` |
| Fechamento | `/cobrancas/previa` | `Select`, totais em cartão do sistema; sem `Stat` (D4) |
| CobrancaPainel / GMCobrancaPainel | painel em `/cobrancas?cobranca=` | Conferido, sem cor fixa; sem caso visual (id gerado) |
| CobrancaAvulsa | janela em `/cobrancas` | `Input`, erros em token; caso `cobranca-avulsa` |
| CobrancaCancelar | `CancelChargeDialog`/`CancelNextDialog` | `Banner` e campo de motivo em tokens; sem caso visual |
| PagamentoRegistrar / GMPagamento | `RegisterPaymentDialog` | `Input`/`Select`/`Textarea`/`Banner`; sem caso visual |
| Renegociar / GMRenegociar | `RenegotiateDialog` | `Input`/`Select`/`Banner`; sem caso visual |
| RecebimentosDia | `/cobrancas/recebimentos` | `Select`/`Input`, cartões de total |
| Atrasados | `/cobrancas/atrasados` | `Select`, atraso em `text-danger-foreground`, limite em `Banner` |
| AlunoFinanceiro / GMFichaFinanceiro / GMMatricula4 / GMFinanceiro | aba financeira da ficha e passo de vencimento | Já tratadas na #421 (D1); só conferidas (usam o `StatusBadge` de cobrança, agora do sistema) |
| EstadoErro | `components/states` | Sem tela própria: é o `ErrorState` usado em todas as telas |
| SiteMRecibo | `/recibo/:codigo` | Já em tokens, CPF mascarado; sem caso visual (o MSW não tem recibo) |
| Comunicados / GMComunicados | `/comunicados` | `Input`/`Select` nos filtros |
| ComunicadoNovo / GMNovoComunicado | `/comunicados/novo` | Campos e erros em tokens, prévia em cartão do sistema |
| ComunicadoCancelar | `CancelAnnouncementDialog` | Erro em `Banner`; sem caso visual |
| ComunicadoLimite | `NewAnnouncementPage` (erro de limite) | Sem tela própria: é o erro do formulário |
| ComunicadoOrganizacao | opção "Toda a organização" do seletor de destino | Sem tela própria |
| AulaAvisarTurma | `/comunicados/novo?classId=` (atalho do painel da aula) | Sem tela própria; usa o mesmo formulário |
| ConfigTextos / LembretesAuto | `/configuracoes/textos` | `Input`/`Textarea`/`Banner`; lembretes automáticos são os modelos da mesma página |
| Notificacoes / GMNotificacoes | sino da barra | `Switch` do sistema; caso `notificacoes` |
| PedidosPortal / GMPedidos | `/pedidos` | `Select` nos filtros |
| PedidoRecusar / GMPedidoRecusar / PedidoEncerramentoRecusar | `RefuseRequestDialog` | `Textarea` com contador; sem caso visual (sem pedido no MSW) |
| PedidoAprovadoFluxo | abre `flowOf` da ficha ou da nova matrícula | Sem tela própria; é o fluxo já existente |
| EmConferencia / GMConferencia | `/cobrancas/conferencia` | Conferido; lista igual |
| ComprovanteVarias | `ProofDialogs` | `Textarea`; sem caso visual (sem comprovante no MSW) |

## Verificação

### Rodada 1 — OK — 2026-10-29, estado b597018ca98231513ca39323bc649afa623f3693

| Critério | Status | Evidência |
|---|---|---|
| CA1 | PASSOU | `test:cov` 190 arquivos / 1831 testes verdes (inclui `charges`); referências `cobrancas*` atualizadas e `test:visual:docker` verde |
| CA2 | PASSOU | diff das janelas usa `Input`/`Select`/`Banner`; testes de `charges` e `payments` verdes. `Modal`/`Field` já em uso ou D4 registrado (rótulo envolvente mantido) |
| CA3 | PASSOU | diff de `ChargePreviewPage`, `DailyReceiptsPage`, `OverduePage`: `Select`/`Input`, `Banner`; referências verdes |
| CA4 | PASSOU | `PublicReceiptPage` sem alteração; busca sem hex/classe fixa na pasta `payments` |
| CA5 | PASSOU | diff de `announcements/` com componentes do sistema; testes verdes |
| CA6 | PASSOU | diff de `TextsPage` (`Input`/`Textarea`/`Banner`); testes verdes |
| CA7 | PASSOU | `NotificationPanel` com `Switch` do sistema; caso visual `notificacoes` verde |
| CA8 | PASSOU | `Select`/`Textarea` em pedidos e comprovantes; testes de recusa com motivo verdes |
| CA9 | PASSOU | axe nos dois temas nos testes de página (+ `RegisterPaymentDialog`); e2e 81 verdes. Estados e 360 px mantidos dos testes existentes (não alterados) |
| CA10 | PASSOU | `telasCanvas422.test.ts` 35 verdes; `npm run lint` sem erro; grep próprio por `text-destructive`, `<select`, `<textarea`, hex nas sete pastas: vazio |
| CA11 | PASSOU | `test:visual:docker` sem atualizar: 288 passam; só referências das telas tocadas mudaram no diff |
| CA12 | PASSOU | lint, typecheck, build ok; `test:cov` 1831 ok com cobertura mínima; `e2e` 81 ok |
| CA13 | PASSOU | tabela com as 41 telas em "Construção" |

Comandos: `npm run lint && typecheck && build && test:cov` ok; `npm run e2e` 81 passam; `npx vitest run src/features/telasCanvas422` 35 passam; `npm run test:visual:docker` 288 passam.
Achados:
- sugestão, código, `ChargeFilters`/`StudentFilters` seguem com botões próprios e dados em `dl` (D4 registrado); revisar em item futuro se o canvas exigir `QuickFilters`/`Stat`.

## Entrega

- **O que muda:** cobranças (lista, janelas, prévia, recebimentos, atrasados, conferência), comunicados, notificações, pedidos do portal, comprovantes e configuração de textos passam a usar os componentes e tokens do sistema do canvas. Cada uma das 41 telas está mapeada na tabela da Construção, com a rota e o que ficou diferente.
- **Verificação:** rodada 1 `OK` (estado `b597018`). Lint, typecheck, build, `test:cov` com 1831 testes, `e2e` com 81 e `test:visual:docker` com 288 passando sem atualizar referências; `telasCanvas422.test.ts` com 35 testes.
- **Decisões:** filtros rápidos de cobrança e de alunos seguem como botões próprios, não `QuickFilters`, e os dados ficam em `dl` (D4), porque os testes e o nome dos atalhos dependem disso.
- **Limitações:** telas sem caso visual (painel da cobrança, registrar pagamento, renegociar, recusa de pedido, comprovante, recibo público e lembrete), por falta de dado no MSW ou por fluxo longo. Estão cobertas por testes de página com axe nos dois temas.
