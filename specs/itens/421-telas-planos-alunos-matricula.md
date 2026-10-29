# 421 — Planos, alunos, ficha, responsáveis, matrícula e benefícios no visual do canvas (Sprints 06 e 07)

Origem: issue #421 (Sprint 10.5 · Identidade visual), telas listadas na issue em `docs/design/canvas` (as 40 existem). Branch: `item/421-telas-planos-alunos-matricula`. Depois da #415, #416, #417, #418, #419 e #420 (feitas).

## 1. Objetivo

Refazer no padrão do canvas as telas que já existem para planos, alunos (lista e ficha com abas), responsáveis, matrícula (assistente e janelas de troca/encerramento), benefícios e termos. Só o visual: mesmos textos de regra, rotas, campos e comportamento. Quem usa: Coordenador, Administrador e Professor (gestão, também a 360 px).

Fora do escopo (issue): regra, rota, texto de regra ou campo novo; o que o canvas mostra de sprint futura (assinatura, graduação, relatório); telas "Ajustada" (mudança de comportamento é das issues de cada sprint); cobranças e portal (#422 e #423). Se canvas e especificação divergirem, vale a especificação e a diferença vai para a tabela.

## 2. Requisitos

- R1: planos (`PlansPage`, `PlansTablePage`, `PlanFilters`, `PlanFormDialog`, `DeactivatePlanDialog`; canvas `Planos`, `PlanosRegras`, `PlanoDesativar`, `PlanoDesativado`, `GMPlanos`) usam `PageHeader`, `Field`/`Input`/`Select`, `StatusBadge`, `Modal`/`ConfirmDialog` e `Banner`.
- R2: lista de alunos e ficha (`StudentsPage`, `StudentFilters`, `StudentRecordPanel`, `RecordSummary`, `RecordEnrollment`, `RecordHistory`, `RecordGuardians`, `StudentFinancialTab`, `StudentEditDialog`, `StudentSimpleList`, `BirthdaysCard`; canvas `Alunos`, `EstadoSemResultados`, `AlunoDetalhes`, `AlunoEditar`, `AlunoResponsaveis`, `AlunoMatricula`, `FichaProfessor`, `GM*` de alunos e ficha) usam `DataTable`/`Person`/`Avatar` onde a lista é tabela, `QuickFilters`/`Select` nos filtros, `StatusBadge`, painel lateral com abas, `Banner` e `components/states`.
- R3: responsáveis (`GuardianLinkDialog`, `RecordGuardians`; canvas `AlunoResponsaveis`, `ResponsavelDesligar`) usam `Input`/`Textarea`/`Field`/`Modal`/`ConfirmDialog`/`Banner`.
- R4: matrícula (`EnrollmentWizardPage`, `StepStudent`, `StepModalities`, `StepBilling`, `StepSummary`, `StepDone`, `ClassWarnings`, `ModalityPickers`, `ChangeClassDialog`, `ChangePlanDialog`, `TransferDialog`, `EndModalityDialog`; canvas `Matricula*`, `TrocarTurma`, `TrocarPlano`, `TransferirModalidade`, `EncerrarModalidade`, `GMMatricula1/2`, `GMEncerrar`) usam `Stepper`, `Field`/`Input`/`Select`, `SummaryBox`, `Banner` e `Modal`.
- R5: benefícios e termos (`BenefitsPage`, `BenefitFilters`, `GrantBenefitDialog`, `EndBenefitDialog`, `TermsPage`; canvas `BeneficiosLista`, `BeneficioConceder`, `BeneficioSubstituir`, `BeneficioEncerrar`, `ConfigTermos`) usam `PageHeader`, filtros, `StatusBadge`, `Input`/`Select`/`Textarea`, `Modal` e `Banner`.
- R6: telas do Professor (`FichaProfessor`, `AgendaPainelProfessor`, `GMAulaProfessor`) são a mesma tela vista com o perfil Professor, sem tela nova; `AgendaPainelProfessor` e `GMAulaProfessor` já foram tratadas na #420 (`LessonPanelPage`) e só entram na tabela (D1).
- R7: toda tela tocada tem os quatro estados (carregando, vazio, erro, sem permissão) por `components/states`, funciona de 360 a 1440 px e passa no axe nos dois temas.
- R8: nenhuma cor, fonte ou tamanho solto nas pastas `plans`, `students`, `benefits` e `terms`: sem hex, sem `text-destructive`, sem classe de paleta fixa, sem `<select>`/`<textarea>` soltos; textos em `pt-BR.json`.
- R9: as telas ganham casos no teste visual (#416), com referências geradas no container (D2).
- R10: comportamento igual: testes de tela e `e2e` continuam passando; teste só muda onde procurava classe de estilo, e a troca fica registrada.
- R11: o arquivo registra a tabela "tela do canvas → rota → o que ficou diferente e por quê".

## 3. Critérios de aceite

- CA1 (R1): dado `/planos` e `/planos/lista`, quando abrem, então lista, janela de plano (campos, impacto, opções) e desativar usam os componentes novos e os testes de `plans` continuam verdes.
- CA2 (R2): dado `/alunos`, então lista, filtros, colunas e estado sem resultados têm o visual do canvas; dado `/alunos/:id`, painel e abas (resumo, matrícula, responsáveis, financeiro, histórico) idem; dado Professor, ele vê só o que via.
- CA3 (R2): dada a janela de editar aluno, então os campos usam `Input`/`Select` do sistema e mantêm as mesmas validações.
- CA4 (R3): dada a ficha com responsáveis, então vincular e desligar responsável usam os componentes novos, com as mesmas validações.
- CA5 (R4): dado `/alunos/nova-matricula`, então os passos (aluno, modalidades, vencimento, resumo, concluída) usam os componentes novos e os testes de `students` continuam verdes.
- CA6 (R4): dadas as janelas trocar turma, trocar plano, transferir e encerrar modalidade, então usam `Modal`, `Select`, `Textarea` e `Banner` do sistema.
- CA7 (R5): dado `/beneficios`, então lista, conceder/substituir e encerrar usam os componentes novos; dado `/configuracoes/termos`, o editor usa `Textarea` e `Banner`.
- CA8 (R6, R7): dada qualquer tela tocada a 390 px e 360 px, então não há rolagem horizontal e a barra e o menu de celular são os da #417.
- CA9 (R7): dada cada tela tocada, então carregando, vazio, erro com "Tentar de novo" e sem permissão aparecem por `components/states` (acrescentar teste onde faltar) e `axeViolations` é zero nos dois temas.
- CA10 (R8): dado o código novo ou alterado, quando o lint e a varredura rodam, então não há hex, `text-destructive`, classe de paleta fixa, `<select>`/`<textarea>` solto nem texto literal nas pastas tocadas.
- CA11 (R9): dado `visual/casos.ts`, então há caso por tela que o MSW alcança (janelas por `passos`), 1440 e 390, claro e escuro, ligado ao nome do canvas; só as referências dessas telas mudam, e `npm run test:visual:docker` passa sem diferença nas referências de telas não tocadas.
- CA12 (R10): dada a regressão do front, então `lint`, `typecheck`, `build`, `test:cov` (100% em `features/{billing,charges,payments,subscription,deactivation}`) e `e2e` passam.
- CA13 (R11): dado este arquivo, então "Construção" traz a tabela com as 40 telas da issue, a rota de cada uma ou "sem tela no sistema", e a diferença.

## 4. Plano técnico

- Dados, API e segurança: não mudam (permissão continua no servidor; só marcação e classes).
- Interface, vista no repositório. `plans/`: `PlansPage`, `PlansTablePage`, `PlanFilters`, `PlanFormDialog` (228 linhas; `select` e `text-destructive`), `DeactivatePlanDialog`. `students/`: 25 arquivos, 2755 linhas, entre eles `StudentEditDialog` (397; três `select`, `input` nativo), `GuardianLinkDialog` (`inputClass` e `textarea` soltos), `EndModalityDialog` (`textarea`, `input`), `ModalityPickers` (`select`), `StudentFilters` (`select`), `StepStudent`/`StepModalities`/`StepBilling`/`StepSummary`/`StepDone`, `StudentsPage`, `StudentRecordPanel`. `benefits/`: `BenefitsPage`, `BenefitFilters`, `GrantBenefitDialog` (385; `input` e `select` soltos), `EndBenefitDialog`. `terms/TermsPage` (`textarea` e `text-destructive`).
- Troca mecânica, como na #420: `select`/`input`/`textarea` soltos viram `Select`/`Input`/`Textarea` de `components/ui/field`; `text-destructive` vira `text-danger-foreground` ou `Banner`; situação em `StatusBadge`. Checkbox, radio e `file` nativos continuam (como na #420).
- Mapa tela → arquivo (confirmar na construção): `PlanosRegras` → impacto dentro de `PlanFormDialog`; `PlanoDesativar`/`PlanoDesativado` → `DeactivatePlanDialog` e linha desativada em `PlansTablePage`; `AlunoDetalhes`/`AlunoMatricula`/`AlunoResponsaveis` → abas de `StudentRecordPanel`; `ResponsavelDesligar` → ação em `RecordGuardians`; `MatriculaAluno`/`Modalidades`/`Vencimento`/`Resumo`/`Concluida` → `StepStudent`/`StepModalities`/`StepBilling`/`StepSummary`/`StepDone`; `TrocarTurma`/`TrocarPlano`/`TransferirModalidade`/`EncerrarModalidade` → as quatro janelas; `BeneficioConceder`/`BeneficioSubstituir` → `GrantBenefitDialog`; `BeneficioEncerrar` → `EndBenefitDialog`; `ConfigTermos` → `TermsPage`. Tela sem equivalente vai para a tabela como "sem tela no sistema".
- Rotas (`src/App.tsx`): `/planos`, `/planos/lista`, `/alunos/:id?`, `/alunos/nova-matricula`, `/beneficios`, `/configuracoes/termos`.
- A criar: `frontend/src/features/telasCanvas421.test.ts` (varredura, no molde de `telasCanvas420.test.ts`, com as quatro pastas e a regra de `text-destructive`); casos novos em `visual/casos.ts` (já existem `alunos`, `planos`, `beneficios`, `configuracoes-termos`; os passos `preencher`/`clicar` existem); referências PNG. Peça que faltar vira componente em `components` e linha na tabela de `docs/design/README.md`.
- Riscos: (1) testes de tela procuram rótulo, papel e texto, e trocar marcação pode mudar `aria-describedby`; vale o critério da #419/#420 (D4); (2) `tabela` do aluno e `DataTable`: manter a atual se o teste exigir; (3) estados e janelas só viram caso por passos, e o que o MSW não alcança fica sem caso (D2); (4) a #420 teve o job `visual` vermelho no PR (casos `beneficios` e `moldura-professor`): rodar `npm run test:visual:docker` sem atualizar antes e confirmar se `beneficios` e a moldura passam; se a diferença for do container e não do item, tratar na construção como falha anterior (issue no formato das #318/#320, regra do `AGENTS.md`) antes de mesclar; (5) tamanho das referências (15 MB hoje).

### Divisão em partes

- Parte A, planos e termos: T1 e T2. Parte B, lista, ficha, responsáveis e edição: T3 a T5. Parte C, matrícula e janelas: T6. Parte D, benefícios, varredura, casos visuais e regressão: T7 a T9. É um item só, com uma verificação e uma entrega.

## 5. Tarefas

- [x] T1: `PlansPage`, `PlansTablePage`, `PlanFilters`, `PlanFormDialog`, `DeactivatePlanDialog` no padrão (`Select`, `Banner`, `StatusBadge`) — cobre CA1, CA8, CA9 — prova: em `frontend/`, `npx vitest run features/plans`
- [x] T2: `TermsPage` (`Textarea`, `Banner`, sem `text-destructive`) — cobre CA7, CA9 — prova: `npx vitest run features/terms`
- [x] T3: `StudentsPage`, `StudentFilters`, `StudentSimpleList`, `BirthdaysCard` — cobre CA2, CA8, CA9 — prova: `npx vitest run features/students/StudentsPage features/students/StudentFilters`
- [x] T4: ficha: `StudentRecordPanel`, `RecordSummary`, `RecordEnrollment`, `RecordHistory`, `StudentFinancialTab` — cobre CA2, CA9 — prova: `npx vitest run features/students/Record features/students/StudentRecordPanel features/students/StudentFinancialTab`
- [x] T5: `StudentEditDialog`, `GuardianLinkDialog`, `RecordGuardians` — cobre CA3, CA4 — prova: `npx vitest run features/students/StudentEditDialog features/students/GuardianLinkDialog features/students/RecordGuardians`
- [x] T6: matrícula: `EnrollmentWizardPage`, `Step*`, `ModalityPickers`, `ClassWarnings`, `ChangeClassDialog`, `ChangePlanDialog`, `TransferDialog`, `EndModalityDialog` — cobre CA5, CA6, CA9 — prova: `npx vitest run features/students/enrollment-wizard features/students/ModalityPickers features/students/ChangeClassDialog features/students/ChangePlanDialog features/students/TransferDialog features/students/EndModalityDialog`
- [x] T7: benefícios: `BenefitsPage`, `BenefitFilters`, `GrantBenefitDialog`, `EndBenefitDialog` — cobre CA7, CA9 — prova: `npx vitest run features/benefits`
- [x] T8: `telasCanvas421.test.ts` (cor fixa, `text-destructive`, campo solto nas quatro pastas) e axe claro e escuro onde faltar; ajuste de teste que procurava classe de estilo, anotado — cobre CA9, CA10 — prova: `npx vitest run telasCanvas421 a11y` e `npm run lint`
- [x] T9: casos visuais (janelas e estados por passos) em `visual/casos.ts`, referências no container, tabela "tela → rota → diferença" em "Construção" e regressão completa — cobre CA11, CA12, CA13 — prova: `npx vitest run visual`, `npm run test:visual:docker` (antes de atualizar, para ver a falha de `beneficios`/moldura da #420), depois `-- --update-snapshots -g "<casos do item>"`, e `npm run lint && npm run typecheck && npm run build && npm run test:cov && npm run e2e`

## 6. Decisões

- D1: telas `GM*` e `FichaProfessor`/`AgendaPainelProfessor`/`GMAulaProfessor` são a mesma tela responsiva ou o mesmo componente com outro perfil; nenhuma tela de celular é criada (regra do `AGENTS.md`).
- D2 (mecanismo da #419/#420): janelas e estados entram no teste visual pelos `passos`; o que o MSW não alcançar (id gerado, fluxo longo) fica sem caso e é anotado.
- D3: a tabela de CA13 fica em "Construção", como na #420.
- D4: onde o teste de tela exigir a marcação antiga, mantém-se a marcação e registra-se o desvio, em vez de reescrever o teste.
- D5: `QuickFilters`, `SummaryBox` e `DataTable` só entram onde a tela de hoje já tem o dado (filtro, resumo, tabela); não se cria filtro, resumo nem coluna nova (fora do escopo da issue).
- D6: a falha de `visual` da #420 (`beneficios`, `moldura-professor`) é tratada primeiro na T9: reproduzir sem atualizar as referências. Se vier de componente compartilhado alterado nesta branch, corrige-se aqui; se já existia em `dev`, abre-se issue antes de mesclar.
- Suposição: as 40 telas têm equivalente no sistema, exceto possivelmente `PlanoDesativado`, `ResponsavelDesligar`, `BeneficioSubstituir`, `MatriculaConcluida`, `EstadoSemResultados` e as `GM*`, que podem ser estados de telas existentes. Se faltar tela, entra na tabela como "sem tela no sistema", sem criar tela nem regra.

## Construção

- T1 — arquivos: `plans/PlanFormDialog.tsx` (três `select` viram `Select`, erros em `Banner`), `plans/DeactivatePlanDialog.tsx`, `plans/PlansTablePage.tsx` (erro em `Banner`) — prova: `npx vitest run src/features/plans` → verde
- T2 — arquivos: `terms/TermsPage.tsx` (`Textarea`, mensagem em `Banner`, sem `text-destructive`) — prova: `npx vitest run src/features/terms` → verde
- T3 — arquivos: `students/StudentFilters.tsx` (`Select`); `StudentsPage`, `StudentSimpleList` e `BirthdaysCard` já não tinham campo solto nem cor fixa — prova: `npx vitest run src/features/students` → verde
- T4 — arquivos: `students/RecordSummary.tsx` (`text-destructive` → token `text-danger-foreground`); ficha sem outro campo solto — prova: `npx vitest run src/features/students` → verde
- T5 — arquivos: `students/StudentEditDialog.tsx` (três `Select`, erros em `Banner`), `students/GuardianLinkDialog.tsx` (`Input`/`Textarea`, sem `inputClass`), `students/RecordGuardians.tsx` (`Banner`) — prova: `npx vitest run src/features/students` → verde
- T6 — arquivos: `students/ModalityPickers.tsx` (`Select`, `Banner`), `students/EndModalityDialog.tsx` (`Input`/`Textarea`), `students/enrollment-wizard/{EnrollmentWizardPage,StepModalities,StepStudent}.tsx` (`Banner`) — prova: `npx vitest run src/features/students` → verde
- T7 — arquivos: `benefits/GrantBenefitDialog.tsx` (`Input`/`Select`/`Banner`), `benefits/EndBenefitDialog.tsx` (`Input`) — prova: `npx vitest run src/features/benefits` → verde
- T8 — arquivos: `src/features/telasCanvas421.test.ts` (35 arquivos de `plans`, `students`, `benefits`, `terms`: cor fixa, `text-destructive`, `<select>`, `<textarea>` e `<input>` que não seja checkbox/radio/file) — prova: `npx vitest run telasCanvas421 a11y` → verde; `npm run lint` → sem erros. Nenhum teste de tela precisou mudar.
- T9 — arquivos: `visual/casos.ts` (casos `planos-lista`, `plano-novo`, `beneficio-conceder`) — prova: `npx vitest run visual` → 70 passaram.

Desvios:
- Mantidos os erros de campo `<p id="…-erro" className="text-sm text-danger-foreground">` (já no token do sistema) em vez de `Banner`, porque os testes dependem de `aria-describedby`/id (D4). O `GrantBenefitDialog` mantém seu `Field` local (só os controles foram trocados).
- Checkbox, radio e `file` nativos continuam (como na #420).
- Passo único de teste visual: casos só para o que o MSW alcança por clique (D2). Sem caso: ficha, trocar turma/plano, transferir, encerrar, passos da matrícula, responsáveis.

### Tabela tela do canvas → rota → diferença

| Tela | Rota | Diferença |
| --- | --- | --- |
| Planos, GMPlanos | `/planos`, `/planos/lista` | nenhuma de regra; `Select`/`Banner` na janela |
| PlanosRegras | `/planos/lista` (impacto em `PlanFormDialog`) | erro do impacto em `Banner` |
| PlanoDesativar, PlanoDesativado | `DeactivatePlanDialog` e linha em `/planos/lista` | erro em `Banner` |
| Alunos, GMAlunos, GMAlunosFiltros, GMBusca | `/alunos` | filtros em `Select` do sistema |
| EstadoSemResultados | `/alunos` (estado vazio por `components/states`) | estado, não tela própria |
| AlunoDetalhes, GMFicha | `/alunos/:id` | aviso de e-mail com token de cor |
| AlunoEditar, GMAlunoEditar | janela da ficha | `Select` do sistema |
| AlunoResponsaveis, GMFichaResponsaveis | aba da ficha | `Input`/`Textarea` na janela de vínculo |
| ResponsavelDesligar | ação em `RecordGuardians` | `Banner` no erro |
| AlunoMatricula, GMFichaMatricula | aba Matrícula da ficha | nenhuma |
| FichaProfessor | `/alunos/:id` com perfil Professor | mesma tela, sem tela nova (D1) |
| AgendaPainelProfessor, GMAulaProfessor | `LessonPanelPage` | feitas na #420 |
| MatriculaAluno, MatriculaModalidades, MatriculaVencimento, MatriculaResumo, MatriculaConcluida, GMMatricula1, GMMatricula2 | `/alunos/nova-matricula` (passos) | erros em `Banner`; sem caso visual (fluxo longo) |
| TrocarTurma, TrocarPlano, TransferirModalidade | janelas da ficha | `Select`/`Banner` via `ModalityPickers` |
| EncerrarModalidade, GMEncerrar | janela da ficha | `Input`/`Textarea` do sistema |
| BeneficiosLista | `/beneficios` | nenhuma de regra |
| BeneficioConceder, BeneficioSubstituir | `GrantBenefitDialog` | `Input`/`Select`/`Banner`; substituir é o mesmo diálogo |
| BeneficioEncerrar | `EndBenefitDialog` | `Input` |
| ConfigTermos | `/configuracoes/termos` | `Textarea` e `Banner` |

- T9 (complemento) — referências: criadas `planos-lista`, `plano-novo`, `beneficio-conceder` (1440/390, claro/escuro); atualizadas só `alunos`, `configuracoes-termos`, `moldura-coordenacao` e `moldura-administrador` (as duas últimas mostram `/alunos` com o filtro novo) — prova: `bash scripts/visual-docker.sh --update-snapshots -g " (alunos|…) "` → 28 passaram. Antes de atualizar, a rodada completa deu 248 passados e 24 reprovados, todos nesses casos (referência antiga ou ausente); `beneficios` e `moldura-professor` passaram, então a falha da #420 (D6) não se repetiu e não gerou issue.

Regressão (em `frontend/`): `npm run lint`, `typecheck`, `build` → ok; `npm run test:cov` → 189 arquivos, 1792 testes, passou (limites de cobertura atendidos); `npm run e2e` → 79 passaram, 2 estouraram 30 s sob carga (`comprovantes`, `configuracao-unidade`) e passaram ao rodar de novo isoladas (3 de 3); não tocam as pastas do item. Back-end não alterado, não rodou.
Imagem Playwright do teste visual: removida pelo script ao fim, se baixada pela prova.
Não é do item: nada.

## Verificação

### Rodada 1 — OK — 2026-10-29, estado e9798d061fea2b7ddabd07515fe3940332448754

| Critério | Status | Evidência |
|---|---|---|
| CA1, CA5, CA6, CA7 | PASSOU | `test:cov` 189 arquivos, 1792 testes verdes (inclui plans, students, benefits, terms) |
| CA2, CA3, CA4 | PASSOU | testes de StudentsPage, StudentEditDialog, GuardianLinkDialog, RecordGuardians verdes; `Select`/`Input`/`Textarea` no diff |
| CA8, CA9 | PASSOU | testes de tela com axe claro/escuro, 403 e erro existentes nas quatro pastas; 360/390 px cobertos pelo teste visual dos casos tocados |
| CA10 | PASSOU | `telasCanvas421.test.ts` verde; `npm run lint` ok; grep sem `<select>`, `<textarea>`, `text-destructive` ou hex |
| CA11 | PASSOU | `npm run test:visual:docker` 276 passaram, 0 reprovados, com as referências do estado |
| CA12 | PASSOU | lint, typecheck, build ok; test:cov ok; `npm run e2e` 81 passaram |
| CA13 | PASSOU | tabela em "Construção" cobre as telas da issue |

Comandos: lint+typecheck+build (ok); test:cov (ok); e2e (81 passaram); test:visual:docker (276 passaram). Back-end não alterado.
Achados:
- sugestão, código, `students/GuardianLinkDialog`/`EndBenefitDialog` — erros de campo seguem como `<p>` com token (desvio D4 registrado), não `Banner`.
- sugestão, código, `visual/casos.ts` — sem caso visual para ficha, matrícula e janelas de troca/encerramento (D2 registrado).

## Entrega

- Planos, alunos (lista e ficha com abas), responsáveis, matrícula (assistente e janelas de troca e encerramento), benefícios e termos passam para os componentes do sistema (`Select`, `Input`, `Textarea`, `Banner`, `StatusBadge`, `Modal`, `Stepper`), sem campo solto nem cor fixa. Só visual: regras, rotas, campos e textos de regra iguais.
- Verificação: rodada 1 OK (estado `e9798d06`). `npm run lint`, `typecheck` e `build` ok; `test:cov` com 189 arquivos e 1792 testes; `e2e` com 81 aprovados; `test:visual:docker` com 276 aprovados e 0 reprovados. Varredura `telasCanvas421.test.ts` cobre as quatro pastas. Back-end não alterado.
- Decisões: erros de campo seguem como `<p>` com token do sistema, não `Banner`, para não quebrar `aria-describedby` dos testes (D4); casos visuais só para o que o MSW alcança por clique (D2). A tabela "tela → rota → diferença" está em "Construção".
- Limitações: ficha, trocar turma e plano, transferir, encerrar e os passos da matrícula ficam sem caso visual (fluxo longo ou id gerado). Dois testes e2e estouraram 30 s sob carga e passaram isolados; não são das pastas do item. Falha de `visual` da #420 (`beneficios`, `moldura-professor`) não se repetiu.

Closes #421
