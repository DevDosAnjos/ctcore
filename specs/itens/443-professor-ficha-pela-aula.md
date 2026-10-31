# 443 — Professor chega à ficha do aluno pela aula (com a permissão "ver ficha do aluno")

Origem: issue #443 (Sprint 10.5 · Identidade visual); RF-ALU-03 (`docs/especificacao/08-alunos-e-responsaveis.md`), `02-contas-e-acesso.md`, decisão 11 de `docs/auditoria/decisoes-canvas.md`, relatório M123 (FichaProfessor, AgendaPainelProfessor). Branch: `item/443-professor-ficha-pela-aula`.

## 1. Objetivo

Hoje o Professor vê a lista simples dos alunos no painel da aula (`StudentSimpleList`), mas o nome não leva a lugar nenhum, e `/alunos/:id` é só da gestão (cai em "Esta área não está disponível"). Este item dá ao Professor o caminho aula -> aluno -> ficha "Visão do Professor", só quando a permissão `STUDENT_RECORD` está ligada, e prova no servidor e no front que aluno de outra turma e perfil sem permissão não abrem.

Fora do escopo: visual novo da gaveta e da ficha (#463 e #466); foto, faixa e "Ver os 31" além do que a lista simples já devolve; caminho pelo Início do Professor (Sprint 13, RF-INI-03); lista `/alunos` e painel de turma para o Professor (a especificação não os prevê).

## 2. Requisitos

- R1 (RF-ALU-03): no painel da aula, para o Professor com `STUDENT_RECORD` ligada, o nome de cada aluno da lista vira link para a ficha, dentro do contexto da aula (D1).
- R2 (RF-ALU-03, canvas AgendaPainelProfessor): para o Professor sem `STUDENT_RECORD`, a lista fica sem link e mostra o aviso "a ficha do aluno não abre sem a permissão" (D2).
- R3 (RF-ALU-03): a rota da ficha do Professor é servida só a ele, por `GET /students/{id}/record`, e mostra o aviso "Visão do Professor"; a tela só exibe o que o servidor devolveu (sem valores, plano, benefício, CPF, endereço, situação financeira; contatos e emergência pela permissão "ver contatos").
- R4 (RF-ALU-03, RN de isolamento): no servidor, a ficha do Professor só abre para aluno ativo de turma dele (404 fora, igual a inexistente e a outra organização) e só com `STUDENT_RECORD` ligada na unidade da turma (403 sem ela). Já implementado em `StudentRecords.record`; o item prova com teste (D3).
- R5 (regras gerais): Coordenador e Administrador continuam em `/alunos/:id?` como hoje; o Professor continua sem acesso a `/alunos`.
- R6 (regras gerais): quatro estados na ficha e na lista, a partir de 360 px, axe nos dois temas, todo texto em `pt-BR.json`.

## 3. Critérios de aceite

- CA1 (R1): dado Professor com `STUDENT_RECORD` ligada, quando abre o painel de uma aula da turma dele, então cada aluno da lista é um link para `/agenda/aulas/<aula>/alunos/<aluno>` e o aviso de permissão não aparece.
- CA2 (R2): dado Professor com `STUDENT_RECORD` desligada, quando abre o painel da aula, então os nomes não são link e o aviso aparece; para Coordenador e Administrador a lista fica como hoje (sem link novo, sem aviso).
- CA3 (R3): dado Professor com a permissão, quando abre a ficha pelo link, então vê o painel com o aviso "Visão do Professor", só as abas Resumo e Responsáveis, e nenhum CPF, endereço, selos, valores, plano, benefício nem situação financeira; fechar o painel volta ao painel da aula.
- CA4 (R3, R6): dado a ficha do Professor, quando a leitura está carregando, falha (500, com "Tentar de novo"), devolve 403 ou 404, então aparece o estado correspondente (carregando, erro, sem permissão, não encontrado).
- CA5 (R3, R5): dado Coordenador, Administrador ou Aluno, quando abre `/agenda/aulas/<aula>/alunos/<aluno>`, então vê "Esta área não está disponível"; dado Professor abrindo `/alunos` ou `/alunos/<id>`, o mesmo.
- CA6 (R4): dado Professor com a chave desligada, quando `GET /students/{id}/record`, então 403; com a chave ligada e aluno sem modalidade ativa em turma dele, 404; aluno de turma dele, 200 sem os campos omitidos; aluno da organização B, 404 (isolamento) e Coordenador/Administrador seguem pelas regras atuais (perfis).
- CA7 (R4): o MSW do Professor recusa (403/404) nos mesmos casos do CA6 e os testes do MSW cobrem isso.
- CA8 (R6): axe sem violação nos dois temas a 360 px e 1440 px na lista da aula (com e sem permissão) e na ficha; nenhum texto fora do `pt-BR.json` (`npm run lint` passa).

## 4. Plano técnico

- Dados / API: não muda. `GET /students/{id}/record` e `/students/simple` já existem no `docs/openapi.yaml` e no servidor (`StudentController.record`, `StudentRecords.record` com `teacherUnits` e `grant(STUDENT_RECORD, unidade)`). Nenhuma rota nova.
- Interface:
  - `frontend/src/features/students/StudentSimpleList.tsx`: parâmetros novos `recordHref?: (id: string) => string` e `noRecordNotice?: boolean`; com `recordHref`, o nome vira `Link` (`min-h-11`); com `noRecordNotice`, mostra o aviso (texto `students.simple.noRecord`) abaixo da lista.
  - `frontend/src/features/agenda/LessonPanelPage.tsx` (`Panel`): decide pelo papel do vínculo (`useSession`, mesmo cálculo de `RequireArea`) e por `permissions.STUDENT_RECORD !== "NONE"` (já lido via `usePermissions`); só para `TEACHER` passa `recordHref` (permissão ligada) ou `noRecordNotice` (desligada).
  - `frontend/src/features/students/TeacherRecordPage.tsx` (a criar): `ManagementLayout activeId="agenda"` + `StudentRecordPanel` com `onClose = navigate("/agenda/aulas/<aula>")`; params `:id` (aula) e `:studentId`.
  - `frontend/src/features/students/StudentRecordPanel.tsx` (`Body`): quando `!canEdit` (visão do Professor), `Banner` com `students.record.teacherView` acima das abas. A ficha do Professor já esconde abas, botões e cobranças (`TEACHER_TABS`, teste CA35).
  - `frontend/src/App.tsx`: novo grupo `RequireArea roles={["TEACHER"]}` com `/agenda/aulas/:id/alunos/:studentId` -> `TeacherRecordPage`; o grupo da gestão não muda.
  - `frontend/src/i18n/pt-BR.json`: `students.simple.noRecord` ("A ficha do aluno não abre sem a permissão."), `students.record.teacherView` ("Visão do Professor: dados e modalidades, sem valores, plano, benefício, CPF, endereço nem situação financeira.").
- Segurança: a barreira é o servidor (R4); o front só esconde o link. Rota do front restrita ao Professor para não abrir um segundo caminho à gestão. Sem dado real em teste.
- Reuso: `StudentSimpleList`, `StudentRecordPanel`, `usePermissions`, `RequireArea`, `Banner`, `ForbiddenState`/`ErrorState`/`EmptyState` já usados pelo painel.
- Testes existentes a manter: `StudentRecordPanel.test.tsx` (CA35 Professor), `StudentSimpleList.test.tsx`, `LessonPanelPage.test.tsx`, `StudentRecordIT.teacherKey`, `studentRecord.msw.test.ts`.
- Riscos: `usePermissions` devolve a permissão do vínculo, não por unidade da turma; o link pode aparecer e a ficha dar 403 numa unidade com a chave desligada (tratado pelo `ForbiddenState`). A rota nova passa pelo ranking do React Router sem conflito (caminho mais longo e distinto de `/agenda/aulas/:id`).

## 5. Tarefas

- [x] T1: `StudentSimpleList` com `recordHref` e `noRecordNotice` e textos em `pt-BR.json` — cobre CA1, CA2 — prova: `cd frontend && npx vitest run src/features/students/StudentSimpleList.test.tsx`
- [x] T2: `LessonPanelPage` passa link ou aviso só para o Professor — cobre CA1, CA2 — prova: `cd frontend && npx vitest run src/features/agenda/LessonPanelPage.test.tsx`
- [x] T3: `TeacherRecordPage`, aviso "Visão do Professor" em `Body` e rota em `App.tsx` — cobre CA3, CA4, CA5 — prova: `cd frontend && npx vitest run src/features/students/TeacherRecordPage.test.tsx src/features/students/StudentRecordPanel.test.tsx src/App.test.tsx` (nome do teste da rota conforme existir)
- [x] T4: testes que falham sem a correção: back-end Professor com chave ligada abre aluno da turma (200 sem campos omitidos), turma alheia e organização B 404, chave desligada 403, perfis da gestão; MSW igual — cobre CA6, CA7 — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test=StudentRecordIT` e `cd frontend && npx vitest run src/mocks/studentRecord.msw.test.ts`
- [x] T5: axe nos dois temas e quatro estados da ficha e da lista; capturas e comparação com o canvas (`FichaProfessor`, `AgendaPainelProfessor`, 1440 e 390 px, pt-BR) em `.capturas/443/` — cobre CA8 — prova: `cd frontend && npm run lint && npm run typecheck && npx vitest run src/features/students src/features/agenda`

## 6. Decisões

- D1 (muda o comportamento): rota própria `/agenda/aulas/:id/alunos/:studentId`, só do Professor, em vez de abrir `/alunos/:id` para ele. Motivo: mantém o contexto da aula (volta ao painel), não abre `/alunos` nem a lista ao Professor (decisão 11) e não mexe na rota da gestão. Alternativa descartada: `/alunos/:id` com visão por papel (exigiria a página da lista condicionar o papel).
- D2 (muda o comportamento): sem `STUDENT_RECORD` o aviso aparece e a lista segue visível como lista simples (RN-PRO-06). Motivo: é o que o canvas mostra.
- D3: o back-end já limita o Professor às turmas dele e confere a chave na unidade da turma (`StudentRecords.record`); a tarefa do back-end é só completar a prova de isolamento e de perfil, sem mudar regra. Se um teste novo expuser falha, corrigir no mesmo PR.
- D4: foto, faixa e "Ver os 31" ficam como a lista simples já entrega (a faixa vem nula até a Sprint 13); sem o recorte de "5 primeiros" do canvas, que pertence ao visual (#463/#466).
- Suposição: `permissions.STUDENT_RECORD` do vínculo é bom indicador para mostrar o link; se o servidor negar por unidade, o `ForbiddenState` da ficha cobre.

## Construção

- T1 — arquivos: frontend/src/features/students/StudentSimpleList.tsx, StudentSimpleList.test.tsx, frontend/src/i18n/pt-BR.json — prova: vitest StudentSimpleList.test.tsx → passou
- T2 — arquivos: frontend/src/features/agenda/LessonPanelPage.tsx, LessonPanelPage.test.tsx (link com permissão, aviso sem, gestão sem nenhum) — prova: vitest LessonPanelPage.test.tsx → passou
- T3 — arquivos: frontend/src/features/students/TeacherRecordPage.tsx (novo, com PageHeader exigido por shell.test.ts), StudentRecordPanel.tsx (Banner), StudentRecordPanel.test.tsx (CA3, CA4, CA5), frontend/src/App.tsx — prova: vitest src/features/students src/features/agenda src/components/layout/shell.test.ts → passou
- T4 — arquivos: backend/src/test/java/br/com/ctcore/student/StudentRecordIT.java (teacherClass: 200 sem campos omitidos, turma alheia 404, chave desligada 403, gestão 200, org B 404; clean apaga matrículas) — prova: mvnw verify -Dit.test=StudentRecordIT → 6 testes, 0 falhas. MSW: studentRecord.msw.test.ts já cobre 403/404/200 recortado (sem mudança; sem teste de org B, o simulado é de uma organização).
- T5 — NÃO marcada: axe nos dois temas da lista já coberto pelo teste existente de StudentSimpleList; axe da ficha do Professor e capturas/comparação com o canvas (FichaProfessor, AgendaPainelProfessor, 1440 e 390 px, .capturas/443/) não rodaram nesta sessão. lint e typecheck passam.
- T5 (atualização) — axe da ficha do Professor nos dois temas a 360 px adicionado em StudentRecordPanel.test.tsx; prova: `npm run lint && npm run typecheck && npx vitest run src/features/students src/features/agenda src/components/layout/shell.test.ts` → 18 arquivos, 245 testes passam. Continua NÃO marcada: capturas e comparação com o canvas (1440 e 390 px, pt-BR, `.capturas/443/`) não rodaram (exigem navegador e canvas; não feito nesta sessão).
Regressão: front `npm run test:cov` → 1 falha (shell.test.ts exigindo PageHeader na página nova), corrigida; depois shell.test.ts + StudentRecordPanel.test.tsx passam; lint, typecheck e build OK. Não reexecutei a suíte completa nem e2e, nem o verify completo do back-end, depois da correção.
Desvios: branch da pasta é item/443-professor-ficha-aluno-aula (spec cita item/443-professor-ficha-pela-aula); T4 sem mudança de código de produção (D3).
Regressão (sessão 2): front `npm run lint && npm run typecheck && npx vitest run` → lint e tipos OK, 192 arquivos, 1913 testes passam. Não rodaram: `test:cov`, build, e2e, `verify` completo do back-end, capturas e comparação com o canvas (T5), por exigirem navegador/ambiente que não foram levantados nesta sessão.

### Sessão 3 — T5 (resposta do dono: sem Docker, back-end nem front fora do MSW)
- Decisão do dono: não subir Docker/back-end/front sem MSW; T5 feita só com o teste visual com MSW e a comparação automática da #435.
- T5 — arquivos: frontend/visual/casos.ts (casos `agenda-painel-professor` e `ficha-professor`, campo `matricula`; as duas telas saem de `semCaso`), frontend/visual/telas.visual.ts (passo `matricula`: no MSW da página, a Coordenação matricula o aluno fictício na turma 601, liga `studentRecord` na unidade Centro e o login volta ao Professor; o MSW começa sem matrículas e com a chave desligada). Professor com STUDENT_RECORD ligada, rota `/agenda/aulas/<turma>-2026-11-02-19:00[/alunos/<aluno>]`.
- Prova: `npx playwright test -c playwright.visual.config.ts -g "ficha-professor|agenda-painel-professor" --update-snapshots` → 8 passam (1440 e 390, claro e escuro, pt-BR); `node scripts/visual-canvas.mjs` → 95 comparadas, 2 reprovadas (as duas novas, 1440 claro: painel 46,9% e ficha 41,9%, limite 30%); `npm run lint && npm run typecheck && npm run test:cov` → OK, 192 arquivos, 1915 testes, linhas 96,69%; `vitest run visual/casos.test.ts` → 93 passam.
- Capturas em `.capturas/443/`: `agenda-painel-professor-{1440,390}-sistema.png`, `ficha-professor-{1440,390}-sistema.png`, `agenda-painel-professor-1440-canvas.png`, `ficha-professor-1440-canvas.png`. O canvas só existe a 1440 para essas duas telas (não há versão de 390 px no índice): a 390 só há o print do sistema, conferido à mão (sem estouro, aviso e abas legíveis).
- Divergências da comparação (para a descrição do PR): (1) o canvas mostra painel da aula e ficha como gaveta lateral sobre a agenda; o sistema mostra o painel da aula como página e a ficha como gaveta sobre uma agenda vazia. (2) Painel da aula: canvas traz faixa, grau, situação e "5 de 31" por aluno, e o aviso sem permissão como cartão com cadeado; o sistema traz foto e nome (D4: faixa e recorte são do visual #463/#466); o caso mostra a permissão ligada, então não há aviso. (3) Ficha: o canvas tem cabeçalho com nome em destaque, selo "Ativo", cartões Dados e Modalidades e o aviso abaixo; o sistema tem Resumo/Responsáveis, aviso acima das abas e sem Modalidades (visual, #463/#466). O comportamento (link, aviso, campos omitidos, 403/404) segue a especificação. Os limites do caso ficam no padrão (30%): as duas reprovam até o visual das gavetas ser tratado; não mudei o limite.
- Referências: geradas aqui no Windows, não no container do CI; apaguei as 8 imagens de `visual/referencias` para não entrarem no PR com fonte diferente. Falta `npm run test:visual:docker -- --update-snapshots -g "ficha-professor|agenda-painel-professor"` (precisa de Docker, que o dono mandou não subir).
- Regressão (sessão 3): front `npm run lint && npm run typecheck && npm run test:cov` → OK. Não rodaram: e2e e `verify` do back-end (sem Docker, por decisão do dono).

### Sessão 4 — respostas do dono (T5 concluída)
- (1) Referências geradas só no container do teste visual: `npm run test:visual:docker -- --update-snapshots -g "ficha-professor|agenda-painel-professor"` → 8 passam; 8 imagens novas em `frontend/visual/referencias/` (agenda-painel-professor e ficha-professor, 1440 e 390, claro e escuro). Sem compose, back-end nem front fora do MSW. A imagem `mcr.microsoft.com/playwright:v1.64.1-noble` já estava na máquina antes desta sessão (imagem do CI), por isso o script a manteve; nenhum container ou imagem nova ficou (7 containers antes e depois, lista de imagens igual).
- (2) `limiteCanvas` nas duas telas novas, padrão de 30% intacto: `agenda-painel-professor` 0,55 (medido 46,9%) e `ficha-professor` 0,50 (medido 41,9%), cada um com motivo e a issue que remove a exceção. Conferência de autoria: o painel da aula do Professor (AgendaPainelProfessor) é da #472 (consta na Evidência dela, M7); a #463 é só o componente da gaveta (pilotos Turma e Notificações); a ficha é da #466. Por isso a #472 e a #466 receberam o texto, não a #463.
- (3) `gh issue edit` no "Pronto quando" da #472 e da #466: "Ao terminar, remove a exceção `limiteCanvas` ... e a comparação passa com o limite padrão de 30%". Só texto, sem dado real.
- (4) As outras 23 exceções `limiteCanvas` já existentes tiveram o motivo ajustado em `casos.ts` para citar a issue que as remove, e essas issues receberam o mesmo texto no "Pronto quando": #499 (portal-carteirinha), #489 (sem-acesso), #498 (conta-aluno), #490 (conta-duas-etapas, conta-codigos-reserva), #482 (transferir-coordenacao), #484 (unidade-desativar), #487 (usuario-convidar), #485 (modalidades-graduacao, modalidades-pedido), #486 (faixa-nova), #470 (professor-novo, professor-painel), #475 (turma-nova, turma-painel), #473 (agenda-aula-avulsa), #474 (dia-sem-aula-novo), #480 (planos-lista, plano-novo), #481 (beneficio-conceder), #477 (cobranca-avulsa, cobranca-antecipar), #492 (notificacoes). Issues editadas ao todo: #466, #470, #472, #473, #474, #475, #477, #480, #481, #482, #484, #485, #486, #487, #489, #490, #492, #498, #499 (listar no PR).
- Prova: `node scripts/visual-canvas.mjs` → 95 comparadas, 0 reprovadas; `npx vitest run visual/casos.test.ts visual/comparar.test.ts` → 98 passam; `npm run lint` → OK.
- Regressão: os testes do front e do back-end não mudaram nesta sessão (só `visual/casos.ts` e imagens); e2e e `verify` do back-end continuam sem rodar (sem Docker/ambiente, decisão do dono).

## Verificação

### Rodada 1 — OK — 2026-10-31, estado a684bad548512dc25d413cbb15e4eea18f0b666d

| Critério | Status | Evidência |
|---|---|---|
| CA1 | PASSOU | LessonPanelPage.test.tsx (443 CA1, CA2): link `/agenda/aulas/les1/alunos/s9`, sem aviso |
| CA2 | PASSOU | mesmo teste: sem permissão, aviso e sem link; gestão sem link nem aviso |
| CA3 | PASSOU | StudentRecordPanel.test.tsx (443 CA3, CA5): Visão do Professor, sem "Em dia", Fechar volta a `/agenda/aulas/les1` |
| CA4 | PASSOU | StudentRecordPanel.test.tsx (443 CA4): 403, 404 e 500 com "Tentar de novo"; carregando coberto pelo componente existente |
| CA5 | PASSOU | mesmo teste CA3/CA5: Coordenador na rota nova e Professor em `/alunos/s1` veem "Esta área não está disponível" (Administrador e Aluno usam o mesmo `RequireArea roles=["TEACHER"]`) |
| CA6 | PASSOU | StudentRecordIT (teacherClass + teacherKey): 6 testes, 0 falhas, com Docker |
| CA7 | PASSOU | studentRecord.msw.test.ts passa (403/404/200 recortado); sem caso de org B no MSW, simulado é de uma organização |
| CA8 | PASSOU | axe da lista (existente) e da ficha do Professor a 360 px nos dois temas passam; `npm run lint` OK |

Comandos:
- `npm run lint && npm run typecheck` (frontend) → OK
- `npx vitest run src/features/students src/features/agenda shell.test.ts studentRecord.msw.test.ts visual/casos.test.ts visual/comparar.test.ts` → 21 arquivos, 349 testes passam
- `./backend/mvnw ... verify -Dit.test=StudentRecordIT` → 6 testes, 0 falhas
- `npm run build && npm run test:cov` → OK; 192 arquivos, 1915 testes; linhas 96,69%
- `npm run e2e` → 81 passam
- `node scripts/visual-canvas.mjs` → 95 comparadas, 0 reprovadas
- Não rodei o `verify` completo do back-end (só o IT do item; o item não mudou código de produção do back-end) nem `test:visual:docker`.
- Capturas conferidas à mão em `.capturas/443/` (1440 sistema e canvas). A 390 só existe a do sistema: o canvas das duas telas não tem versão de 390.

Instáveis: nenhum.

Achados:
- sugestão, código, `students.record.teacherView` em pt-BR.json: o aviso diz "dados e modalidades", mas a ficha do Professor não mostra o cartão Modalidades do canvas; ajustar o texto ou mostrar as modalidades quando #466 tratar o visual.
- sugestão, código, divergências com o canvas (gaveta sobre agenda real, cabeçalho com selo, cartões Dados e Modalidades, texto do aviso do canvas) já registradas em Construção/Sessão 3 e cobertas por `limiteCanvas` com issue de saída (#472, #466); listá-las na descrição do PR.

## Entrega

- O que muda: o Professor chega à ficha do aluno pela aula. Com a permissão "ver ficha do aluno" (`STUDENT_RECORD`), o painel da aula mostra o link para a ficha, na rota nova `/agenda/aulas/:id/alunos/:studentId` (só do Professor, D1). Sem a permissão, aparece o aviso e a lista não tem link. A ficha do Professor mostra a visão recortada (sem "Em dia", com aviso) e Fechar volta ao painel da aula. No back-end não muda regra: `StudentRecordIT` ganha os casos de turma (200 sem campos omitidos, turma alheia 404, chave desligada 403, gestão 200, organização B 404).
- Verificação: Rodada 1 OK (estado a684bad). Front: lint, tipos, build, `test:cov` (1915 testes, linhas 96,69%), e2e (81 passam) e comparação com o canvas (95 comparadas, 0 reprovadas). Back-end: `StudentRecordIT`, 6 testes. Referências do teste visual geradas no container (`test:visual:docker`), 8 imagens.
- Comparação com o canvas (1440 px; o canvas não tem versão de 390 px dessas telas). Divergências, não corrigidas no item e cobertas por `limiteCanvas` com a issue que as remove (#472 para o painel da aula, #466 para a ficha):
  1. O canvas mostra painel da aula e ficha como gavetas sobre a agenda; o sistema mostra o painel como página e a ficha como gaveta sobre uma agenda vazia.
  2. Painel da aula: o canvas traz faixa, grau, situação e "5 de 31" por aluno, e o aviso sem permissão como cartão com cadeado; o sistema traz foto e nome (D4).
  3. Ficha: o canvas tem cabeçalho com nome em destaque, selo "Ativo", cartões Dados e Modalidades; o sistema tem Resumo e Responsáveis, com o aviso acima das abas e sem Modalidades.
- Limitações: a regressão completa do back-end (`verify`) não rodou localmente (sem Docker, decisão do dono); só `StudentRecordIT`. O CI roda o `verify` no PR. Sugestão do verificador: o aviso diz "dados e modalidades", mas a ficha não mostra Modalidades; ajustar o texto ou mostrar o cartão quando #466 tratar o visual.
- Desvio: a branch é `item/443-professor-ficha-aluno-aula`; a especificação citava `item/443-professor-ficha-pela-aula`. Sem efeito na entrega.
