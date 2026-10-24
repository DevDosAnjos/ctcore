# 81 — RF-COM-01 · Comunicação › Comunicados (lista)

Origem: issue #81 (Sprint 09); `docs/especificacao/13-comunicacao-e-notificacoes.md` (RF-COM-01, RN-COM-06, RN-COM-08, dados do Comunicado e da Leitura), `02-contas-e-acesso.md` (permissão `ANNOUNCEMENTS`). Branch: `item/81-comunicados-lista`. Hoje não existe nada de comunicado no código: só o item de menu `/comunicados` (`frontend/src/features/permissions/menuCatalog.ts`), a permissão `ANNOUNCEMENTS` (`PermissionTable`) e o e-mail `ANNOUNCEMENT` (#78). A tela `Comunicados.dc.html` do canvas está inacessível: o dono confere o layout (D8).

## 1. Objetivo

A gestão (Coordenador, Administrador nas suas unidades, Professor nas suas turmas) vê em Comunicação › Comunicados a lista dos comunicados com data, destino, título, tipo, autor, situação (Agendado, Enviado), destinatários e lidos, filtra por período, turma e tipo, e cancela um comunicado agendado. Este item cria a tabela de comunicados e de leituras e a leitura deles; criar, editar e enviar é a #82, e a leitura pelo aluno (que grava a leitura) é do portal (Sprint 10).

Fora do escopo: criar, editar e enviar comunicado, prévia de destinatários, imagem e "fixar até" (#82, que acrescenta as colunas); marcar como lido no portal e a tela Portal › Avisos (Sprint 10); dados de destinatários por pessoa; ponta a ponta (D7).

## 2. Requisitos

- R1: `GET /announcements` devolve página de comunicados com `id`, `date` (instante de envio ou de agendamento), `target` (tipo e nome do destino: turma, modalidade, unidade ou todas as unidades), `title`, `type`, `author` (nome), `status` (`SCHEDULED` ou `SENT`), `recipients` e `reads`; ordem padrão: data mais recente primeiro.
- R2: filtros `from` e `to` (período, na data da unidade, inclusivos), `classId` (comunicados cujo destino é a turma) e `type` (um dos cinco tipos da RN-COM-04); mais `unitId` (D3), todos opcionais e combináveis.
- R3: o Coordenador vê os comunicados da organização; o Administrador, os das unidades a que está ligado; o Professor, os que enviou e os destinados às suas turmas (D2). Comunicado "todas as unidades" só o Coordenador vê (D2). Sem permissão `ANNOUNCEMENTS` na unidade, 403; recurso de outra organização nunca aparece.
- R4: `reads` conta alunos distintos que abriram o comunicado; a leitura do responsável conta como a do aluno menor e, com vários responsáveis, conta uma vez (RN-COM-08). Marcar todos como lidos não grava leitura (a regra é do portal; a tabela só aceita a leitura de abrir, D5). Comunicado agendado tem `reads = 0` e `recipients = null` (D4).
- R5: `DELETE /announcements/{id}` cancela comunicado agendado (some da lista, sem ter saído); comunicado enviado não é apagado nem editado (RN-COM-06): 409 `ANNOUNCEMENT_ALREADY_SENT`. Quem cancela: Coordenador e Administrador da unidade; Professor só o que ele mesmo agendou (D2). Grava `announcement.cancel` na auditoria.
- R6: a tela Comunicação › Comunicados em `/comunicados` mostra a tabela (cartões abaixo de 640 px) com as colunas de R1, os filtros de R2 na URL, paginação, a ação "Cancelar agendamento" só nas linhas agendadas (com confirmação) e o botão "Novo comunicado", que leva a `/comunicados/novo` (rota da #82; sem a #82 ela cai na página "não encontrada", D6).
- R7: a tela mantém os quatro estados (carregando, vazio — com a diferença entre "nenhum comunicado" e "nenhum com estes filtros" —, erro, sem permissão), funciona a partir de 360 px, passa no axe nos dois temas e todos os textos vêm de `pt-BR.json`.
- R8: toda rota nova tem teste de isolamento entre organizações e entre perfis; `docs/openapi.yaml` e os handlers MSW batem com o servidor.

## 3. Critérios de aceite

- CA1 (R1): dado três comunicados (um agendado, dois enviados), quando `GET /announcements`, então vêm os três ordenados da data mais recente para a mais antiga, com autor, destino nomeado, tipo, situação, `recipients` e `reads`; o agendado traz `recipients = null` e `reads = 0`.
- CA2 (R2): dado comunicados de tipos, turmas e datas variados, quando filtro por `type`, por `classId`, por `from`/`to` e por combinações, então só voltam os que casam; `from`/`to` contam a data no fuso da unidade (inclusive nos dois extremos) e `to` antes de `from` dá 400 `VALIDATION_ERROR`; `type` fora dos cinco dá 400.
- CA3 (R2): dado `unitId` de outra organização, quando listar, então 404; Administrador pedindo unidade fora do alcance recebe lista vazia (como `OverdueCharges`).
- CA4 (R3): dado comunicados em duas unidades e um "todas as unidades", quando listar como Coordenador, então vê todos; como Administrador de uma unidade, só os dela; o "todas as unidades" não aparece.
- CA5 (R3): dado Professor com duas turmas, um comunicado para a turma dele, outro para turma de outro professor e um dele mesmo para a unidade, quando listar, então vê o da turma dele e o que ele mesmo enviou, e não o da turma de outro professor; sem a permissão "enviar comunicado" ligada na unidade, 403.
- CA6 (R3, R8): dado Aluno/portal, usuário sem sessão e organização B, quando `GET` e `DELETE`, então 403, 401 e (organização B, id da A) 404; uma consulta direta ao banco com o papel operacional da organização B não enxerga as linhas da A (RLS, teste de isolamento da tabela).
- CA7 (R4): dado comunicado com leituras de dois responsáveis do mesmo aluno menor e de um aluno adulto, quando listar, então `reads = 2`; a restrição de unicidade impede gravar duas leituras do mesmo aluno no mesmo comunicado.
- CA8 (R5): dado comunicado agendado do Administrador, quando `DELETE`, então 204, a linha sai da lista, existe `announcement.cancel` na auditoria com o título e o destino, e nada mais é afetado; repetir o `DELETE` dá 404.
- CA9 (R5): dado comunicado enviado, quando `DELETE`, então 409 `ANNOUNCEMENT_ALREADY_SENT` e a linha continua igual.
- CA10 (R5): dado Professor, quando cancela comunicado agendado por outro autor (mesmo para a turma dele), então 403; o próprio, 204. Administrador de outra unidade e Professor sem permissão: 403 (404 se fora da organização).
- CA11 (R6): dado a página aberta com comunicados, então a tabela mostra data, destino, título, tipo, autor, situação, destinatários e lidos ("—" no agendado), os filtros (período, turma, tipo) alteram a URL e a lista, e a ação "Cancelar agendamento" só existe nas linhas agendadas; confirmar chama o `DELETE` e recarrega a lista, e o 409 mostra o erro do servidor sem remover a linha.
- CA12 (R7): a tela mostra carregando, vazio sem filtro ("Nenhum comunicado ainda" com o botão "Novo comunicado"), vazio com filtro ("Nenhum comunicado com estes filtros" e "Limpar filtros"), erro com "Tentar de novo" e sem permissão; passa no axe nos dois temas e cabe em 360 px.
- CA13 (R8): o MSW responde `GET` e `DELETE` como o servidor (filtros, 400, 409) e `OpenApiContractTest` e `ContractRoutesTest` passam.

## 4. Plano técnico

- Dados: migração `backend/src/main/resources/db/migration/V44__announcement.sql` (a criar). Tabela `announcement`: `id`, `organization_id`, `unit_id` (nulo = todas as unidades), `target_kind` (`CLASS`, `MODALITY`, `UNIT`, `ORGANIZATION`), `class_id`, `modality_id` (checks coerentes com o `target_kind`), `type` (`CANCELED_CLASS`, `TEACHER_CHANGE`, `SCHEDULE_CHANGE`, `NO_CLASS_DAY`, `GENERAL`), `title` (1 a 120), `body` (até 2.000), `channels text[]`, `scheduled_for`, `sent_at`, `recipient_count` (preenchido no envio), `author_account_id`, `created_at`, check de que está ou agendado (`scheduled_for` sem `sent_at`) ou enviado (`sent_at` e `recipient_count` preenchidos). Chaves estrangeiras compostas por `(organization_id, id)` para turma, modalidade e unidade como nas migrações vizinhas; índice `(organization_id, coalesce(sent_at, scheduled_for) desc)` e `(organization_id, class_id)`. Tabela `announcement_read`: `organization_id`, `announcement_id`, `student_id`, `account_id` (quem abriu: aluno ou responsável), `opened_at`, `primary key (announcement_id, student_id)`. RLS ligada e forçada nas duas (`organization_scope`, como `V43`); `announcement_read` sem UPDATE nem DELETE para o papel operacional; `announcement` sem UPDATE (a #82 acrescenta o que precisar) e com DELETE permitido (só o cancelamento do agendado, que a regra do servidor guarda e um `delete ... where sent_at is null` reforça). Cabeçalho com a reversão manual (`drop table announcement_read; drop table announcement;`). Seed `infra/seed/19-comunicados.sql` (a criar; idempotente, `on conflict do nothing`, dados fictícios) com enviados e agendados em duas unidades.
- API (`docs/openapi.yaml`): `GET /announcements` (query `from`, `to`, `classId`, `type`, `unitId`, `page`, `size`, `sort`; ordenação por `date`, `title`, `type`; erros 400, 401, 403, 404) e `DELETE /announcements/{id}` (204, 401, 403, 404, 409). Esquemas `AnnouncementItem`, `AnnouncementList`, `AnnouncementType`, `AnnouncementStatus`. Paginação como `OverdueCharges` (`PageRequest`, tamanho máximo do projeto). Código de erro novo `ANNOUNCEMENT_ALREADY_SENT` em `ErrorCode` (409) e no OpenAPI.
- Back-end (pacote novo `br.com.ctcore.communication`, a criar): `Announcements.java` (serviço: `list(who, Filter, PageRequest)` e `cancel(who, id, ip)`; modelo em `OverdueCharges`: `OrganizationTransactions`, `Permissions.grant(who, Permission.ANNOUNCEMENTS, unit)`, `AuditService.record`) e `AnnouncementController.java` (rotas, `@AuthenticationPrincipal AccountPrincipal`). Reuso: `PageRequest`, `Charges.today`-estilo para a data no fuso (`coalesce(u.timezone, 'America/Sao_Paulo')`), e da `Agenda` o recorte do Professor pelas suas turmas (`teacher.account_id`, `training_class.head_teacher_id`, `training_class_assistant`). Ver abaixo permissão e segurança.
- Segurança: permissão no servidor em toda rota (`ANNOUNCEMENTS`, nível completo para cancelar); a unidade vem do registro, nunca do corpo; organização do vínculo ativo; resposta fora do alcance é 404 sem revelar existência; consultas com parâmetros ligados; nenhum dado pessoal de destinatário na resposta (só contagens); auditoria `announcement.cancel` sem texto do corpo.
- Interface (`frontend/src/features/announcements/`, a criar): `AnnouncementsPage.tsx` (usa `QueryPage`, `DataTable`, `ManagementLayout activeId="announcements"` como `OverduePage.tsx`), `AnnouncementFilters.tsx` (período com `input type="date"`, turma e tipo; reusa `useStudentFilterOptions` para turmas, como `ChargeFilters.tsx`), `CancelAnnouncementDialog.tsx`, `useAnnouncements.ts` (`fetchAnnouncements`, `cancelAnnouncement`), `types.ts`. Rota `/comunicados` em `frontend/src/App.tsx` (junto das de cobranças); textos no grupo `announcements` de `frontend/src/i18n/pt-BR.json`; `frontend/src/mocks/handlers.ts` ganha os dois handlers e `frontend/src/mocks/announcements.msw.test.ts` os casos (modelo: `charge-reminders.msw.test.ts`). O botão "Novo comunicado" leva a `/comunicados/novo` (D6).
- Testes a criar: `backend/src/test/java/br/com/ctcore/communication/AnnouncementsIT.java` (lista, filtros, escopo por perfil, leituras, cancelamento), `AnnouncementsRouteIT.java` (401/403/404, isolamento entre organizações e entre perfis), `AnnouncementSchemaIT.java` (checks, unicidade da leitura, RLS, privilégios) e `frontend/src/features/announcements/AnnouncementsPage.test.tsx`. `OpenApiContractTest` e `ContractRoutesTest` já existem.
- Riscos: (1) filtro por data no fuso da unidade, com o "todas as unidades" sem unidade (usa `America/Sao_Paulo`, suposição S1); (2) o recorte do Professor mistura turma, autor e permissão por unidade: errar vaza comunicado de outra turma, coberto por CA5; (3) a #82 constrói sobre esta tabela: colunas que ela precisar (imagem, fixar até) vêm por migração dela; (4) `reads` agregado na consulta: usar `count` em subconsulta, não `join`, para não duplicar linhas na paginação.

## 5. Tarefas

- [x] T1: migração `V44__announcement.sql`, `ErrorCode.ANNOUNCEMENT_ALREADY_SENT`, seed `19-comunicados.sql` e `AnnouncementSchemaIT` (checks, unicidade da leitura por aluno, RLS, privilégios) — cobre CA6 (RLS), CA7 (unicidade) — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test='AnnouncementSchemaIT,SeedDataIT' -Dtest=NoTest -Dsurefire.failIfNoSpecifiedTests=false`
- [x] T2: servidor — `Announcements.list` (filtros, escopo por perfil, leituras) e `AnnouncementController` com `GET` — cobre CA1, CA2, CA3, CA4, CA5, CA7 — prova: mesmo comando de T1 com `-Dit.test='AnnouncementsIT'`
- [x] T3: servidor — `Announcements.cancel`, `DELETE`, auditoria; `AnnouncementsRouteIT` (acesso negado por perfil, isolamento) e `docs/openapi.yaml` — cobre CA6, CA8, CA9, CA10, CA13 (servidor) — prova: `-Dit.test='AnnouncementsIT,AnnouncementsRouteIT'` e `./backend/mvnw -f backend/pom.xml test -Dtest='OpenApiContractTest,ContractRoutesTest'`
- [x] T4: front — tipos, `useAnnouncements.ts`, textos em `pt-BR.json` e handlers MSW com `announcements.msw.test.ts` — cobre CA13 (MSW) — prova: em `frontend/`, `npx vitest run src/mocks/announcements.msw.test.ts` e `npm run typecheck`
- [x] T5: front — `AnnouncementsPage`, filtros, diálogo de cancelamento, rota em `App.tsx` e `AnnouncementsPage.test.tsx` (quatro estados, axe nos dois temas a 360 px) — cobre CA11, CA12 — prova: em `frontend/`, `npx vitest run src/features/announcements/AnnouncementsPage.test.tsx`; ao fim `./backend/mvnw -f backend/pom.xml verify` e, em `frontend/`, `npm run lint && npm run typecheck && npm run build && npm run test:cov && npm run e2e`

## 6. Decisões

- D1 (muda o comportamento): a leitura do aluno é por aluno, não por conta: `announcement_read` tem chave `(comunicado, aluno)`; o responsável que abre o aviso de um menor grava a leitura do menor, e vários responsáveis contam uma vez (RN-COM-08). Alternativa: uma linha por conta, contando alunos distintos na consulta; descartada porque a chave no banco já impede contar duas vezes. A coluna `account_id` guarda quem abriu.
- D2 (muda o comportamento): quem vê e quem cancela. Coordenador vê tudo; Administrador vê as unidades dele e não vê "todas as unidades" (RN-COM-01 reserva o envio ao Coordenador); Professor vê o que enviou e o destinado às suas turmas, com a permissão "enviar comunicado" ligada na unidade. Cancelar agendado: Coordenador e Administrador da unidade qualquer um; Professor só o seu. A especificação não diz quem vê a lista; segui a tabela de permissões (`ANNOUNCEMENTS`: Coordenador todas, Administrador unidade, Professor turmas). Alternativa: Administrador também vê os "todas as unidades" por serem parte da audiência dele; descartada para não mostrar o que ele não pode enviar nem cancelar.
- D3: `unitId` entra como filtro opcional da lista (a gestão troca de unidade no seletor do cabeçalho, como nas cobranças); sem ele vale o alcance do perfil (D2). A RF-COM-01 cita só período, turma e tipo; `unitId` não muda a regra.
- D4: `recipients` só existe depois do envio (`recipient_count`, gravado pela #82 quando o aviso sai); o agendado mostra "—" porque o conjunto de destinatários ainda pode mudar até a saída (RN-COM-06). Alternativa: calcular a prévia na lista; descartada por custo e por ser a prévia da #82 (RN-COM-09).
- D5: este item cria a tabela de leituras e a conta; quem grava a leitura é o portal (Sprint 10), junto com "Marcar todos como lidos", que não grava leitura. Nesta sprint a regra "marcar todos não conta" é garantida pelo desenho (só abrir grava) e pelo teste de contagem; não há rota para gravar leitura aqui.
- D6: "Novo comunicado" aponta para `/comunicados/novo`, rota da #82, que vem logo depois na mesma sprint; até lá cai na página de rota não encontrada. Alternativa: esconder o botão até a #82; descartada porque os dois itens saem na mesma sprint e o botão faz parte da tela do canvas.
- D7: sem ponta a ponta e sem recurso desligado por organização: é leitura e cancelamento de gestão, sem integração externa (como as D5 da #80 e D10 da #79); a prova é integração e tela. Notas de versão: RF-AJU-04 é da Sprint 21; nada a atualizar.
- D8: o canvas `Comunicados.dc.html` não pôde ser aberto; a tela segue os componentes de `OverduePage.tsx` e a especificação. O dono confere o layout na revisão da sprint.
- D9: cancelar apaga a linha (`delete`), porque o comunicado agendado nunca saiu e a RN-COM-06 só protege o enviado; a auditoria `announcement.cancel` guarda o que existia. Alternativa: situação `CANCELED`, que mostraria "Cancelado" na lista; descartada porque a RF-COM-01 só define Agendado e Enviado.
- Suposição S1: o período é comparado na data do fuso da unidade do comunicado; no "todas as unidades", que não tem unidade, usa `America/Sao_Paulo` (padrão de `V3`). Se o dono quiser outra regra, só a consulta muda.
- Suposição S2: o autor aparece pelo nome da conta (`account.name`), com o e-mail como reserva se o nome estiver vazio.
- Fora do item (sugestão): nenhum trabalho independente encontrado.

## Construção

- T1 — arquivos: `backend/src/main/resources/db/migration/V44__announcement.sql`, `ErrorCode.java` (+ `messages.properties`), `infra/seed/19-comunicados.sql`, `AnnouncementSchemaIT.java`, `SeedDataIT.java` (limpeza das duas tabelas e caso do seed) — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test='AnnouncementSchemaIT,SeedDataIT' -Dtest=NoTest -Dsurefire.failIfNoSpecifiedTests=false` → AnnouncementSchemaIT 3 de 3 e SeedDataIT 19 de 19 passam (a segunda rodada de AnnouncementSchemaIT sozinha também: 3 de 3)
- T2 — arquivos: `backend/src/main/java/br/com/ctcore/communication/Announcements.java`, `AnnouncementController.java`, `AnnouncementBase.java`, `AnnouncementsIT.java` — prova: `-Dit.test='AnnouncementsIT'` (mesmo comando de T1) → passa, junto com T3
- T3 — arquivos: `Announcements.cancel`, `DELETE`, `AnnouncementsRouteIT.java`, `docs/openapi.yaml` (rotas, esquemas, `ANNOUNCEMENT_ALREADY_SENT`) — prova: `-Dit.test='AnnouncementsIT,AnnouncementsRouteIT'` → 9 + 3 passam; `./backend/mvnw -f backend/pom.xml test -Dtest='OpenApiContractTest,ContractRoutesTest'` → 7 de 7
- T4 — arquivos: `frontend/src/features/announcements/types.ts`, `useAnnouncements.ts`, grupo `announcements` de `pt-BR.json`, dois handlers em `frontend/src/mocks/handlers.ts`, `frontend/src/mocks/announcements.msw.test.ts` — prova: `npx vitest run src/mocks/announcements.msw.test.ts` → 6 de 6; `npm run typecheck` → sem erros
- T5 — arquivos: `AnnouncementsPage.tsx`, `AnnouncementFilters.tsx`, `CancelAnnouncementDialog.tsx`, `AnnouncementsPage.test.tsx`, rota em `App.tsx`, `QueryPage.tsx` (prop opcional `texts`) — prova: `npx vitest run src/features/announcements` → 10 de 10 (inclui axe nos dois temas a 360 px)

Regressão: `./backend/mvnw -f backend/pom.xml verify` → 705 testes, 0 falhas, "All coverage checks have been met"; em `frontend/`: `npm run lint && npm run typecheck && npm run build` → ok; `npm run test:cov` → 130 arquivos, 1123 testes, cobertura atendida; `npm run e2e` → 35 de 35. Sem falhas anteriores.

Desvios:
- `FOR UPDATE` no cancelamento não funciona: o plano pedia travar a linha, mas o papel operacional não tem UPDATE em `announcement` (privilégio exigido pelo `for update`). A corrida com o envio é decidida pelo próprio `delete ... where sent_at is null`; se ele não apagar nada, devolve 409 `ANNOUNCEMENT_ALREADY_SENT`.
- O seed tem uma só unidade por organização, então o plano "em duas unidades" virou: um enviado para a turma da unidade, um enviado "todas as unidades" e um agendado para a unidade.
- `QueryPage` ganhou a prop opcional `texts` (títulos próprios dos dois vazios), porque CA12 exige "Nenhum comunicado ainda" e "Nenhum comunicado com estes filtros".
- A rota `/comunicados` ficou no grupo de rotas que já aceita Professor (junto de `/agenda`), pois o Professor com a permissão também lista comunicados (R3); o servidor continua sendo a barreira.
- CA5 do texto da especificação diz "os dois primeiros" e "não o terceiro", o que contradiz R3/D2 (o Professor vê o que ele mesmo enviou). Segui R3/D2: vê o da turma dele (titular ou auxiliar) e o que enviou, não o da turma de outro professor.
- A mensagem do 409 na tela vem do `pt-BR.json` por código/status (o cliente `api()` só devolve o código), não do texto do servidor.
- O `check` de situação aceita `scheduled_for` preenchido também no enviado (histórico do agendamento): é ou agendado, ou enviado com `sent_at` e `recipient_count`.

Não é do item: nada.

## Verificação

### Rodada 1 — OK — 2026-10-24, estado 661634ce79c8523cdc1f5b8bed2807d2b6954ebe

| Critério | Status | Evidência |
|---|---|---|
| CA1 | PASSOU | `AnnouncementsIT.list` (verify: 705 testes, 0 falhas) |
| CA2 | PASSOU | `AnnouncementsIT.filters` (fuso, extremos, 400) |
| CA3 | PASSOU | `AnnouncementsIT.unitFilter` |
| CA4 | PASSOU | `AnnouncementsIT.administratorReach` |
| CA5 | PASSOU | `AnnouncementsIT.teacherScope`; segue R3/D2, desvio do texto de CA5 registrado em Construção |
| CA6 | PASSOU | `AnnouncementsRouteIT` (403/401/404) e `AnnouncementSchemaIT` (RLS) |
| CA7 | PASSOU | `AnnouncementsIT.reads`, `AnnouncementSchemaIT` (unicidade) |
| CA8 | PASSOU | `AnnouncementsIT.cancel` |
| CA9 | PASSOU | `AnnouncementsIT.alreadySent` |
| CA10 | PASSOU | `AnnouncementsIT.cancelScope`, `AnnouncementsRouteIT.profilesOnCancel` |
| CA11 | PASSOU | `AnnouncementsPage.test.tsx` (16 testes com o MSW, todos passam); leitura de `AnnouncementsPage.tsx` e `CancelAnnouncementDialog.tsx` |
| CA12 | PASSOU | `AnnouncementsPage.test.tsx` (quatro estados, axe nos dois temas a 360 px) |
| CA13 | PASSOU | `announcements.msw.test.ts`; `OpenApiContractTest` e `ContractRoutesTest` dentro do verify |

Comandos:
- `./backend/mvnw -f backend/pom.xml verify` → 705 testes, 0 falhas, "All coverage checks have been met", BUILD SUCCESS
- `npm run lint && npm run typecheck && npm run build` (frontend) → ok
- `npx vitest run src/features/announcements src/mocks/announcements.msw.test.ts` → 2 arquivos, 16 testes passam
- `npm run test:cov` → 130 arquivos, 1123 testes, cobertura atendida
- `npm run e2e` → 35 de 35

Achados:
- sugestão, código, `CancelAnnouncementDialog.tsx` — CA11 diz que o 409 "mostra o erro do servidor"; a tela mostra texto do `pt-BR.json` por status (desvio já registrado, coerente com a regra de textos em tradução).
- sugestão, código, `Announcements.requireAnnouncements` — a checagem de `ANNOUNCEMENTS` na lista é feita pelo acesso efetivo geral, não por unidade do registro; o recorte por perfil cobre o caso, sem vazamento observado.

## Entrega

Lista de comunicados em Comunicação › Comunicados, com filtros (situação, período com fuso da unidade, unidade), leitura por perfil, cancelamento de comunicado agendado com confirmação e os quatro estados de tela. Inclui a migração V44 (`announcement`, com unicidade), o seed `infra/seed/19-comunicados.sql` e a rota no OpenAPI.

Verificação: rodada 1 OK (estado 661634ce). `verify` com 705 testes e 0 falhas; `lint`, `typecheck`, `build`, `test:cov` (1123 testes) e `e2e` (35 de 35) verdes; perfis conferidos nas rotas (`AnnouncementsRouteIT.profilesOnCancel`).

Decisões: o 409 do cancelamento mostra o texto do `pt-BR.json` por status, e não o erro cru do servidor, coerente com a regra de textos em tradução. A checagem de `ANNOUNCEMENTS` na lista usa o acesso efetivo geral; o recorte por perfil cobre o caso.

Limitações: sugestões de código registradas na verificação, sem bloqueio: (1) `CancelAnnouncementDialog.tsx`, texto do 409 por status; (2) `Announcements.requireAnnouncements`, checagem de acesso geral e não por unidade do registro.
