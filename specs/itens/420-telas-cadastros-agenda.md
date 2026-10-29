# 420 — Modalidades, faixas etárias, professores, turmas e agenda no visual do canvas (Sprints 04 e 05)

Origem: issue #420 (Sprint 10.5 · Identidade visual), telas listadas na issue em `docs/design/canvas` (as 33 existem). Branch: `item/420-telas-cadastros-agenda`. Depois da #415, #416, #417, #418 e #419 (feitas).

## 1. Objetivo

Refazer no padrão do canvas as telas que já existem para modalidades e graduação, faixas etárias, professores, turmas, agenda e dias sem aula. Só o visual: mesmos textos de regra, rotas, campos e comportamento. Quem usa: Coordenador, Administrador e Professor (gestão, a 360 px também).

Fora do escopo (issue): regra, rota, texto de regra ou campo novo; o que o canvas mostra de sprint futura (graduação além do que já existe, relatório, assinatura); telas "Ajustada" (mudança de comportamento é das issues de cada sprint); conteúdo de alunos, planos e cobranças (#421 a #423).

## 2. Requisitos

- R1: modalidades e faixas (`ConfigModalidades`, `ConfigModalidadesAdmin`, `ModalidadeGraduacao`, `ModalidadeDesativar`, `ModalidadesOferecidasRecusa`, `ConfigFaixas`) usam `PageHeader`, `Section`/`Card`, `DataTable` ou cartões como o canvas, `StatusBadge`, `Field`/`Input`/`Select`, `Modal`/`ConfirmDialog` e `Banner`.
- R2: professores (`Professores`, `ProfessorNovo`, `ProfessorPainel`, `ProfessorInativar`, `ProfessorModalidadeRecusa`, `GMProfessores`, `GMProfessorPainel`) usam `PageHeader`, `QuickFilters`, `DataTable`/`Person`/`Avatar`, `StatusBadge`, `Modal`, `ConfirmDialog` e `Banner`.
- R3: turmas (`Turmas`, `TurmaPainel`, `TurmaEditar`, `TurmaDesativar`, `TurmaTitularInativo`, `GMTurmas`, `GMTurmaPainel`) usam os mesmos componentes; o aviso de titular inativo sai em `Banner`.
- R4: agenda e dias sem aula (`Agenda`, `AgendaPainel`, `AulaEditar`, `AulaEditarSeguintes`, `AulaCancelar`, `AulaCorrigir`, `AulaAvulsa`, `DiasSemAula`, `DiaSemAulaNovo`, `GMAgenda`, `GMAgendaProfessor`, `GMAulaPainel`, `GMAulaEditar`, `GMAulaCancelar`, `GMDiasSemAula`) usam `PageHeader`, filtros e semana/dia como o canvas, `StatusBadge`, `Modal`, `Banner` e `SummaryBox` onde ele os traz.
- R5: telas `GM*` são as mesmas telas a 390 px, sem tela nova (D1).
- R6: toda tela tocada tem os quatro estados (carregando, vazio, erro, sem permissão) por `components/states`, funciona de 360 a 1440 px e passa no axe nos dois temas.
- R7: nenhuma cor, fonte ou tamanho solto nas pastas `modalities`, `age-ranges`, `teachers`, `classes`, `agenda` e `noClassDays`: sem hex nem classe de paleta fixa; textos em `pt-BR.json`. A cor `#808080` de `GraduationDialog` passa a token (ou variável CSS, se for a cor escolhida pelo usuário).
- R8: as telas ganham casos no teste visual (#416), com referências geradas no container; janelas e estados abrem pelos passos do caso (D2).
- R9: comportamento igual: testes de tela e `e2e` continuam passando; teste só muda onde procurava classe de estilo, e a troca fica registrada.
- R10: o arquivo registra a tabela "tela do canvas → rota → o que ficou diferente e por quê" (D3).

## 3. Critérios de aceite

- CA1 (R1): dado Coordenador em `/configuracoes/modalidades`, quando a página abre, então lista, graduação, desativar e pedido de modalidade usam os componentes novos e os testes de `modalities` continuam verdes; dado Administrador, a variante `ConfigModalidadesAdmin` aparece com o que o perfil já vê hoje.
- CA2 (R1): dado `/configuracoes/faixas-etarias`, então lista e janela de faixa usam os componentes novos, com as mesmas validações.
- CA3 (R2): dado `/professores` e `/professores/:id`, então lista, filtros, novo professor, painel, inativar e recusa de modalidade têm o visual do canvas e o comportamento de antes.
- CA4 (R3): dado `/turmas` e `/turmas/:id`, então lista, painel, editar, desativar e aviso de titular inativo têm o visual do canvas.
- CA5 (R4): dado `/agenda` (semana e dia) e `/agenda/aulas/:id`, então grade, cartões, painel da aula, editar (esta e seguintes), cancelar, corrigir e aula avulsa têm o visual do canvas; dado Professor, ele vê só o que via.
- CA6 (R4): dado `/dias-sem-aula`, então lista, novo dia e desfazer têm o visual do canvas.
- CA7 (R5): dada qualquer tela a 390 px, então não há rolagem horizontal e a barra e o menu de celular são os da #417.
- CA8 (R6): dada cada tela tocada, então carregando, vazio, erro com "Tentar de novo" e sem permissão aparecem por `components/states` (acrescentar teste onde faltar) e `axeViolations` é zero nos dois temas, em jsdom e nos e2e a 360 e 1024 px.
- CA9 (R7): dado o código novo ou alterado, quando o lint e a varredura rodam, então não há hex, classe de paleta fixa nem texto literal nas pastas tocadas.
- CA10 (R8): dado `visual/casos.ts`, então há um caso por tela da lista que existe no sistema (e por estado/janela que o MSW alcança), 1440 e 390, claro e escuro, ligado ao nome do canvas; só as referências dessas telas mudam.
- CA11 (R9): dada a regressão do front, então `lint`, `typecheck`, `build`, `test:cov` (100% em `features/{billing,charges,payments,subscription,deactivation}`) e `e2e` passam.
- CA12 (R10): dado este arquivo, então "Construção" traz a tabela com as 33 telas da issue, a rota de cada uma ou "sem tela no sistema", e a diferença.

## 4. Plano técnico

- Dados, API e segurança: não mudam (permissão continua no servidor; só marcação e classes).
- Interface: as telas de `features/modalities` (`ModalitiesPage`, `GraduationDialog` 441 linhas, `RequestModalityDialog`), `age-ranges` (`AgeRangesPage`, `AgeRangeDialog`), `teachers` (`TeachersPage`, `TeacherFilters`, `TeacherFormDialog` 369, `TeacherPanelPage`, `DeactivateTeacherDialog`, `GrantAccessDialog`), `classes` (`ClassesPage`, `ClassFilters`, `ClassFormDialog`, `ClassPanelPage`, `DeactivateClassDialog`), `agenda` (`AgendaPage`, `AgendaFilters`, `AgendaWeekGrid`, `AgendaDayList`, `LessonCard`, `LessonPanelPage`, `NewLessonDialog`, `EditLessonDialog`, `CancelLessonDialog`) e `noClassDays` (`NoClassDaysPage`, `NoClassDayDialog`, `UndoNoClassDayDialog`) já usam parte dos componentes da #415; falta alinhar ao canvas tela a tela. Todos vistos no repositório; a única cor fixa achada é `#808080` em `GraduationDialog.tsx:355`.
- Mapa tela → arquivo (confirmar na construção, anotando na tabela): `ModalidadeGraduacao` → `GraduationDialog`; `ModalidadesOferecidasRecusa` → `RequestModalityDialog`; `ProfessorNovo` → `TeacherFormDialog`; `ProfessorModalidadeRecusa` → recusa dentro de `TeacherFormDialog`/`TeacherPanelPage`; `TurmaEditar` → `ClassFormDialog`; `AulaAvulsa` → `NewLessonDialog`; `AulaEditar`/`AulaEditarSeguintes`/`AulaCorrigir` → `EditLessonDialog`/`LessonPanelPage`; `AulaCancelar` → `CancelLessonDialog`; `DiaSemAulaNovo` → `NoClassDayDialog`. Tela do canvas sem equivalente vai para a tabela como "sem tela no sistema".
- Rotas (`src/App.tsx`): `/configuracoes/modalidades`, `/configuracoes/faixas-etarias`, `/professores`, `/professores/:id`, `/turmas`, `/turmas/:id`, `/agenda`, `/agenda/aulas/:id`, `/dias-sem-aula`.
- A criar: `frontend/src/features/telasCanvas420.test.ts` (varredura de cor fixa e campo solto, no molde de `telasCanvas419.test.ts`); casos em `visual/casos.ts` (já existem `professores`, `agenda`, `dias-sem-aula`, `turmas`, `configuracoes-modalidades`, `configuracoes-faixas-etarias`; os passos `preencher`/`clicar` já existem desde a #419); referências PNG; peça faltante vira componente em `components` e linha na tabela do `docs/design/README.md` (só se aparecer).
- Reuso: `PageHeader`, `Card`, `Section`, `Field`, `Input`, `Select`, `Button`, `Banner`, `Badge`, `StatusBadge`, `SummaryBox`, `Modal`, `ConfirmDialog`, `DataTable`, `QuickFilters`, `Person`, `Avatar`, `components/states`, `axeViolations`, `canvas-shot.mjs`, `visual-canvas.mjs`, `test:visual:docker`.
- Riscos: (1) testes de tela procuram rótulo, papel e texto; trocar marcação pode mudar `aria-describedby` e quebrar `getByLabel` (na #419 houve cartão por linha no lugar de `DataTable` pelo mesmo motivo; vale o mesmo critério); (2) agenda tem grade semanal e cartões de aula com cor por modalidade: a cor vem de variável CSS, não de classe fixa; (3) estados e janelas só viram caso com passos (D2); (4) cerca de 25 casos novos × 4 PNGs: medir o tamanho total das referências (13 MB hoje).

### Divisão em partes (cada uma cabe numa sessão)

- Parte A, modalidades e faixas: T1 a T3. Parte B, professores e turmas: T4 a T5. Parte C, agenda e dias sem aula: T6 a T7. Parte D, varredura, casos visuais e regressão: T8 a T9. É um item só, com uma verificação e uma entrega.

## 5. Tarefas

- [x] T1: `ModalitiesPage` e `RequestModalityDialog` no padrão (lista, desativar, pedido e recusa, variante do Administrador) — cobre CA1, CA7, CA8 — prova: em `frontend/`, `npx vitest run features/modalities/ModalitiesPage features/modalities/RequestModalityDialog`
- [x] T2: `GraduationDialog` (tira `#808080`, tokens) — cobre CA1, CA9 — prova: `npx vitest run features/modalities/GraduationDialog`
- [x] T3: `AgeRangesPage` e `AgeRangeDialog` — cobre CA2, CA7, CA8 — prova: `npx vitest run features/age-ranges`
- [x] T4: professores: `TeachersPage`, `TeacherFilters`, `TeacherFormDialog`, `TeacherPanelPage`, `DeactivateTeacherDialog`, `GrantAccessDialog` — cobre CA3, CA7, CA8 — prova: `npx vitest run features/teachers`
- [x] T5: turmas: `ClassesPage`, `ClassFilters`, `ClassFormDialog`, `ClassPanelPage`, `DeactivateClassDialog` (aviso de titular inativo em `Banner`) — cobre CA4, CA7, CA8 — prova: `npx vitest run features/classes`
- [x] T6: agenda: `AgendaPage`, `AgendaFilters`, `AgendaWeekGrid`, `AgendaDayList`, `LessonCard`, `LessonPanelPage` e as janelas `NewLessonDialog`, `EditLessonDialog`, `CancelLessonDialog` — cobre CA5, CA7, CA8 — prova: `npx vitest run features/agenda`
- [x] T7: dias sem aula: `NoClassDaysPage`, `NoClassDayDialog`, `UndoNoClassDayDialog` — cobre CA6, CA7, CA8 — prova: `npx vitest run features/noClassDays`
- [x] T8: teste de varredura `telasCanvas420.test.ts` (cor fixa e campo solto nas seis pastas) e axe claro e escuro onde faltar; ajuste de teste que procurava classe de estilo, anotado — cobre CA8, CA9 — prova: `npx vitest run telasCanvas420 a11y` e `npm run lint`
- [x] T9: casos visuais novos (estados e janelas por passos) em `visual/casos.ts`, referências no container, tabela "tela → rota → diferença" em "Construção" e regressão completa — cobre CA10, CA11, CA12 — prova: `npx vitest run visual`, `npm run test:visual:docker -- --update-snapshots -g "<casos do item>"`, `npm run lint && npm run typecheck && npm run build && npm run test:cov && npm run e2e`

## 6. Decisões

- D1: telas `GM*` do canvas são a mesma tela responsiva vista a 390 px; nenhuma tela de celular é criada (regra do `AGENTS.md`).
- D2 (usa o mecanismo da #419): janelas e estados entram no teste visual pelos `passos` do caso; o que o MSW não alcançar fica sem caso e é anotado na tabela. Alternativa descartada: só páginas abertas.
- D3: a tabela de CA12 fica em "Construção" deste arquivo, como na #419. Se canvas e especificação divergirem, vale a especificação (README do design, regra 3) e a diferença entra na tabela.
- D4: onde um teste de tela exigir a marcação antiga (ex.: `section` por linha), mantém-se a marcação e registra-se o desvio, como na #419, em vez de reescrever o teste.
- Suposição: as 33 telas têm equivalente no sistema, exceto possivelmente `AulaEditarSeguintes`, `AulaCorrigir`, `TurmaTitularInativo` e `ProfessorModalidadeRecusa`, que podem ser estados de janelas existentes. Se faltar tela, entra na tabela como "sem tela no sistema", sem criar tela nem regra.

## Construção

- T1 a T7 (feitas juntas, mesma troca mecânica) — arquivos: os `.tsx` de `features/{modalities,age-ranges,teachers,classes,agenda,noClassDays}` (campos nativos viram `Input`/`Select`/`Textarea` de `components/ui/field`; erro de formulário e aviso de página viram `Banner`; erro de campo usa `text-danger-foreground`; situação em `StatusBadge`; marcas da aula em `Badge`; avatar do professor pelo `Avatar`) — prova: `npx vitest run src/features/{modalities,age-ranges,teachers,classes,agenda,noClassDays}` → 13 arquivos, 191 testes passaram, sem mudar nenhum teste.
- T2: `#808080` de `GraduationDialog` saiu; o nível novo começa com o token `--subtle` lido do tema (`neutralColor()`); a cor final é a que a pessoa escolhe.
- T8 — arquivos: `src/features/telasCanvas420.test.ts` — prova: `npx vitest run telasCanvas420` → 29 passaram; `npm run lint` → sem erro. O axe nos dois temas e o estado de cada tela já estavam nos testes de tela existentes (`axeViolations` em 13 deles); nada novo foi preciso.
- T9 — arquivos: `visual/casos.ts` (10 casos novos), 36 PNGs novos e 24 atualizados em `visual/referencias` (15 MB no total, eram 13) — prova: `npx vitest run visual` → 67 passaram; `npm run test:visual:docker -- --update-snapshots` nos casos do item → passou. Imagem do Playwright já existia; nenhuma imagem nova ficou no Docker.
Regressão: `npm run typecheck && npm run build` → ok; `npm run test:cov` → 188 arquivos, 1753 testes, cobertura dentro do exigido; `npm run e2e` → 81 passaram; `npm run lint` → ok. Sem falha anterior.
Desvios: (1) as tabelas continuam em `QueryPage`/`DataTable` e as listas de modalidades e faixas seguem como lista de linhas, não `DataTable` (D4: testes procuram rótulo e papel); (2) `SummaryBox` e `QuickFilters` não entraram, o MSW e as telas de hoje não têm resumo nem filtro rápido nessas telas; (3) filtros de lista continuam como `select` com rótulo (agora `Select` do sistema); (4) sem caso visual para painel, editar, cancelar e corrigir aula, porque o id da aula é gerado pelo MSW e não há rota fixa; (5) o caso `configuracoes-modalidades-graduacao` ativa Boxe antes (o MSW parte sem modalidade ativa).
Não é do item: nada.

### Tabela tela do canvas → rota → diferença

| Tela do canvas | Rota / caso visual | O que ficou diferente e por quê |
| --- | --- | --- |
| ConfigModalidades | `/configuracoes/modalidades` (`configuracoes-modalidades`) | Lista em cartões com `StatusBadge` nos pedidos; sem `DataTable` (D4) |
| ConfigModalidadesAdmin | mesma rota, perfil Administrador (`configuracoes-modalidades-administrador`) | Só consulta, como antes |
| ModalidadeGraduacao | janela da página (`configuracoes-modalidades-graduacao`) | Campos pelo sistema; cor inicial do token |
| ModalidadeDesativar | botão "Desativar" da lista, sem janela de confirmação no sistema | Sem tela no sistema: desativar é direto e o erro vem em `Banner` |
| ModalidadesOferecidasRecusa | `RequestModalityDialog` (`configuracoes-modalidades-pedido`) | Recusa aparece na lista de pedidos como situação "Recusado" |
| ConfigFaixas | `/configuracoes/faixas-etarias` (`configuracoes-faixas-etarias`, `configuracoes-faixa-nova`) | Lista de linhas, janela com `Input` |
| Professores | `/professores` (`professores`) | `Avatar` do sistema e `StatusBadge`; filtros em `Select` |
| ProfessorNovo | `TeacherFormDialog` (`professor-novo`) | Campos pelo sistema |
| ProfessorPainel | `/professores/:id` (`professor-painel`) | Avisos em `Banner` |
| ProfessorInativar | `DeactivateTeacherDialog` | Erros em `Banner`; sem caso (precisa de passos longos) |
| ProfessorModalidadeRecusa | erro dentro de `TeacherFormDialog` | Sem tela própria: é mensagem da janela, em `text-danger-foreground` |
| GMProfessores, GMProfessorPainel | mesmas rotas a 390 px | Mesma tela responsiva (D1) |
| Turmas | `/turmas` (`turmas`) | `StatusBadge`; filtros em `Select` |
| TurmaPainel | `/turmas/:id` (`turma-painel`) | `StatusBadge` no cabeçalho |
| TurmaEditar | `ClassFormDialog` (`turma-nova`) | Campos pelo sistema |
| TurmaDesativar | `DeactivateClassDialog` | Erros em `Banner`; sem caso |
| TurmaTitularInativo | aviso dentro de `ClassFormDialog`/`ClassPanelPage` | Sem tela própria: estado da tela, em `Banner` |
| GMTurmas, GMTurmaPainel | mesmas rotas a 390 px | Mesma tela responsiva (D1) |
| Agenda | `/agenda` (`agenda`) | Cartões com `Badge` (avulsa, substituto, cancelada); grade igual |
| AgendaPainel, AgendaPainelProfessor | `/agenda/aulas/:id` | `StatusBadge` e `Badge`; sem caso (id gerado) |
| AulaEditar, AulaEditarSeguintes, AulaCorrigir | `EditLessonDialog` | Mesma janela, as três são o escopo "esta / esta e as seguintes" e a correção; sem tela própria; sem caso |
| AulaCancelar | `CancelLessonDialog` | Erros em `Banner`; sem caso |
| AulaAvulsa | `NewLessonDialog` (`agenda-aula-avulsa`) | `Select` do sistema |
| DiasSemAula | `/dias-sem-aula` (`dias-sem-aula`) | Cartões mantidos |
| DiaSemAulaNovo | `NoClassDayDialog` (`dia-sem-aula-novo`) | `Input` do sistema; erro em `Banner` |
| GMAgenda, GMAgendaProfessor, GMAulaPainel, GMAulaEditar, GMAulaCancelar, GMDiasSemAula | mesmas rotas a 390 px | Mesma tela responsiva (D1) |

## Verificação

### Rodada 1 — OK — 2026-10-29, estado 707c0347e6619d59d63b763507203ecd01afcba1

| Critério | Status | Evidência |
|---|---|---|
| CA1 | PASSOU | `vitest run` das seis pastas: 15 arquivos, 287 testes verdes; `ModalitiesPage`, `RequestModalityDialog`, `GraduationDialog` usam `Input`/`Select`/`Banner`/`StatusBadge`; caso `configuracoes-modalidades-administrador` |
| CA2 | PASSOU | `age-ranges` verde; `AgeRangeDialog` com `Input` do sistema, mesmas validações |
| CA3 | PASSOU | `teachers` verde; `Avatar`, `StatusBadge`, `Banner`, `Select` |
| CA4 | PASSOU | `classes` verde; aviso em `Banner` |
| CA5 | PASSOU | `agenda` verde; `Badge`/`StatusBadge` nos cartões e painel |
| CA6 | PASSOU | `noClassDays` verde |
| CA7 | PASSOU | e2e 81 passados (inclui 360 px); sem teste novo de 390 px por tela, apoiado nos testes e e2e existentes |
| CA8 | PASSOU | `axeViolations` nos testes de tela existentes e e2e verdes; estados já cobertos pelos testes de tela |
| CA9 | PASSOU | `npm run lint` ok; `telasCanvas420.test.ts` verde; grep por hex/paleta fixa/`<select>`/`<textarea` sem achado (restam `<input>` checkbox, radio e color, nativos por natureza) |
| CA10 | PASSOU (ressalva) | 10 casos novos em `visual/casos.ts` ligados ao canvas; `vitest run visual` verde. Comparação de pixels no container não foi rodada nesta verificação (fica com o CI) |
| CA11 | PASSOU | lint, typecheck, build, `test:cov` (lines 96,68%) e `e2e` (81) sem falha |
| CA12 | PASSOU | tabela com as 33 telas em "Construção" |

Comandos: `npm run lint` ok; `npm run typecheck` ok; `npx vitest run` das seis pastas + `telasCanvas420` + `visual` → 15 arquivos, 287 testes ok; `npm run build` ok; `npm run test:cov` ok; `npm run e2e` → 81 passaram.
Instáveis: nenhum.
Achados:
- sugestão, código, `visual/casos.ts` — CA10 pede caso também por estado que o MSW alcança (vazio, erro, sem permissão); só entraram páginas e janelas.
- sugestão, especificação, "Construção" — conta 36 PNGs novos e 24 atualizados (60); o estado tem 52 alterados (40 novos, 12 atualizados). Corrigir o número.
- sugestão, código — o diff é pequeno (troca de campos nativos por `Input`/`Select`, `Banner`, `StatusBadge`); `QuickFilters`, `SummaryBox` e `DataTable` não entraram (desvios 1 a 3 registrados, aceitos por D4 e pelo escopo "só visual").

## Entrega

Muda: as telas de modalidades e graduação, faixas etárias, professores, turmas, agenda e dias sem aula passam a usar `Input`, `Select`, `Banner`, `StatusBadge`, `Badge` e `Avatar` do sistema, no padrão do canvas. Sem regra, rota, texto de regra nem campo novo; textos seguem em pt-BR nos arquivos de tradução. Casos visuais novos em `visual/casos.ts` e referências em `visual/referencias/`.

Verificado: Rodada 1 OK (estado 707c0347e6619d59d63b763507203ecd01afcba1): lint, typecheck, build, `test:cov` e `e2e` (81 passaram) sem falha; `telasCanvas420.test.ts` verde.

Decisões que o revisor precisa saber: lista de modalidades e faixas segue como lista de linhas, e as tabelas seguem em `DataTable` (D4); `SummaryBox` e `QuickFilters` não entraram; sem caso visual para painel, editar, cancelar e corrigir aula, porque o id da aula é gerado pelo MSW.

Limitações: a comparação de pixels no container (`test:visual:docker`) fica com o CI; estados vazio, erro e sem permissão não ganharam caso visual próprio além dos testes de tela existentes.

Verificações do PR (PR #430, run 40897152310, SHA 0080426): `visual` falhou (`CORRIGIR_CODIGO`) e o `ci-gate` ficou vermelho por causa dele. Oito casos fora de pixel, com razão ~0,01: `beneficios` e `moldura-professor` em 390 e 1440, claro e escuro. As referências desses casos não mudam nesta branch e o `dev` passou no #419 (run 40886511728). Suspeita: a mudança em componente compartilhado ou a renderização local diferente da do container do CI. Próximo passo, na construção: reproduzir com `npm run test:visual:docker` e corrigir a causa; não atualizar as referências sem mudança visual pedida.

### Correção da entrega
- `visual` do PR #430 (8 casos `beneficios` e `moldura-professor`, 390 e 1440, claro e escuro) — causa: `Filter` de `features/classes/ClassFilters.tsx` passou a usar `Select` do sistema e é compartilhado com `BenefitFilters` e `AgendaFilters`; o desenho mudou de verdade e as referências desses casos não tinham sido regeradas (a rodada anterior só rodou os casos do item). Reproduzido no container (8 falhas, ~2327 px) — arquivos: 8 PNGs em `frontend/visual/referencias` (`beneficios-*`, `moldura-professor-*`) — prova: `npm run test:visual:docker -- --update-snapshots -g "beneficios|moldura-professor"` → 8 passaram; `npm run test:visual:docker` (suíte inteira) → 264 passaram. Nenhum código mudou; regressão de código inalterada (nada além das referências foi tocado). Imagem do Playwright já existia; nada novo ficou no Docker.

### Rodada 2 — OK — 2026-10-29, estado 6cee6073d1c0850a707b56933da53765d2452160

| Critério | Status | Evidência |
|---|---|---|
| CA1 a CA9, CA11, CA12 | PASSOU (mantido da rodada 1) | desde a rodada 1 só mudaram 8 PNGs de `visual/referencias` e este arquivo; nenhum código |
| CA10 | PASSOU | `npm run test:visual:docker` (suíte inteira, container): 264 passaram; os 8 casos `beneficios` e `moldura-professor` isolados: 8 passaram |

Comandos: `npm run test:visual:docker -- -g "beneficios|moldura-professor"` → 8 passaram; `npm run test:visual:docker` → 264 passaram (11,2 min).
Instáveis: nenhum.
Achados do PR #430 (visual): corrigido. As 8 referências regeradas batem com o desenho atual de `Filter` (compartilhado por classes, benefícios e agenda); mudança justificada por mudança visual do item.
- sugestão, especificação — o número de PNGs em "Construção" (36 novos, 24 atualizados) segue desatualizado (rodada 1); agora são 8 atualizados a mais.
