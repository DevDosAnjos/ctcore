# 46 — RF-TUR-01 · Operação › Turmas (lista)

Origem: issue #46 (Sprint 05); `docs/especificacao/06-turmas-e-agenda.md` (RF-TUR-01, RN-TUR-01, RN-TUR-02, RN-TUR-06, "Dados"), `02-contas-e-acesso.md` (linha "Turmas: criar e editar") e `PermissionTable` (`CLASSES_MANAGE`). Branch: `item/46-operacao-turmas-lista`. Segue o desenho de #42 (`Teachers`, `TeachersPage`, `V23`).

## 1. Objetivo

Em Operação › Turmas, a gestão vê as turmas das suas unidades em lista paginada, com nome, modalidade, faixa etária, dias e horários, professor, alunos ativos/capacidade e situação, e filtra por modalidade, professor, faixa etária, dia e situação. O Coordenador vê todas as unidades; o Administrador, as dele. Como a turma ainda não existe no banco, este item cria o modelo de dados dela (todos os campos de "Dados"), que #47 (painel), #48 (janela) e a agenda usam depois, e troca por valores reais os pontos que #42 a #45 deixaram fixos em zero.

Fora do escopo: painel da turma (#47), criar, editar e desativar turma (#48), aulas e agenda (#49 em diante), matrícula e alunos (Sprints 06 e 07; aqui "alunos ativos" vale 0), tela Relatórios › Aulas (Sprint 19), notas de versão (Sprint 15).

## 2. Requisitos

- R1 (RF-TUR-01, D3, D4): `GET /api/v1/classes` devolve, no envelope `{items, page, size, total}`, as turmas da organização da sessão com `id`, `name`, `unit` (id e nome), `modality` (id e nome), `ageRange` (id, nome, idade mínima e máxima, ou nulo), `schedule` (dia da semana de 1 a 7, segunda a domingo, início e término, ordenados por dia), `head` (id e nome do titular), `assistants` (id e nome), `activeStudents`, `capacity` (ou nulo), `overCapacity` e `status` (`ACTIVE` ou `INACTIVE`), ordenadas por nome e depois id; aceita `page`, `size` e `sort=name`.
- R2 (RF-TUR-01, D2): filtros opcionais `q` (parte do nome, sem diferença de acento nem de maiúsculas), `modalityId`, `teacherId` (titular ou auxiliar), `ageRangeId`, `weekday` (1 a 7), `unitId` e `status`; combinam entre si (E); sem filtro devolve todas; valor inválido de `weekday` ou `status` dá 400.
- R3 (RN-TUR-02, D5): a capacidade é opcional (1 a 200, o teto técnico) e só avisa: `overCapacity` é verdadeiro quando `activeStudents` passa de `capacity`, e a tela mostra "Acima da capacidade (31/30)"; sem capacidade a tela mostra só o número de alunos.
- R4 (RN-TUR-06): não existe presença nem frequência: nenhuma tabela, coluna, campo da API nem texto de tela do item guarda ou mostra presença.
- R5 (modelo, RN-TUR-01, RN-TUR-05, D1): a turma é da unidade, com modalidade, faixa etária opcional, um horário por dia da semana (término depois do início), titular obrigatório, auxiliares, link do grupo opcional e situação; o nome é único na unidade enquanto ativa.
- R6 (permissão, D6): `CLASSES_MANAGE` conferido no servidor: Coordenador (todas as unidades) e Administrador (unidades dele); Professor, Aluno e Responsável recebem 403; sem sessão 401. Para o Administrador, `unitId` fora do alcance devolve lista vazia.
- R7 (isolamento): os dados são sempre da organização da sessão; corpo e URL nunca definem organização; as tabelas novas têm filtro no código e row-level security.
- R8 (reuso, D7): com a tabela de turmas, `classCount` da lista de professores (#42), `headClasses` do painel e da inativação do professor (#43, #45) e as contagens de turmas ativas que bloqueiam desativar modalidade e unidade passam a vir do banco, sem mudar contrato.
- R9 (tela): `/turmas` mostra a lista (nome, modalidade, faixa etária, dias e horários, professor, alunos/capacidade com o aviso, situação) e os filtros, com paginação; tem os quatro estados, funciona a partir de 360 px, passa no axe nos dois temas, tem todos os textos em `pt-BR.json`, e o item "Turmas" do menu fica disponível com a permissão `CLASSES_MANAGE`.
- R10 (contrato e dados fictícios): `docs/openapi.yaml` e os handlers MSW refletem a rota; o seed acrescenta turmas fictícias às organizações de teste.

## 3. Critérios de aceite

- CA1 (R1, R5): dado organização com turmas, quando o Coordenador faz `GET /classes`, então 200 com `items` ordenados por nome, cada um com unidade, modalidade, faixa etária, horários por dia ordenados, titular, auxiliares, `activeStudents` 0 e `status`; `total`, `page` e `size` corretos e `size` fora de 25/50/100 dá 400.
- CA2 (R2): dado várias turmas, quando filtra por `q` ("avancado" acha "Avançado"), `modalityId`, `teacherId` (titular e auxiliar), `ageRangeId`, `weekday`, `unitId` e `status`, sozinhos e combinados, então só voltam as que atendem a todos; `weekday=8` e `status=XYZ` dão 400.
- CA3 (R3): dado turma com capacidade 30 e 31 alunos ativos, então `overCapacity` é verdadeiro e a tela mostra "Acima da capacidade (31/30)"; com 30 ou sem capacidade é falso; a tabela recusa capacidade 0 e 201.
- CA4 (R4): dado o esquema do banco, a resposta da lista e os textos da tela, então nenhuma tabela, coluna, campo ou texto menciona presença ou frequência (teste sobre `information_schema`, o corpo bruto da resposta e `pt-BR.json`).
- CA5 (R5): dado a tabela, então ela recusa duas turmas ativas com o mesmo nome (sem diferença de maiúsculas e acento) na mesma unidade, aceita o mesmo nome em outra unidade ou com a outra desativada, aceita mesma modalidade e horário com nomes diferentes, recusa término igual ou antes do início, dois horários no mesmo dia e turma sem titular.
- CA6 (R6): dado Administrador de A, então só vê turmas de unidades dele e `unitId` de unidade que não é dele devolve `items` vazio; Professor, Aluno e Responsável recebem 403; sem sessão, 401.
- CA7 (R7): dado duas organizações com turmas de mesmo nome, então cada uma só vê as suas; consulta direta com o papel `ctcore_app`: sem contexto 0 linhas, com contexto só as da própria organização, insert com outra organização negado, nas três tabelas novas.
- CA8 (R8): dado turma ativa com o professor como titular, então `GET /teachers` mostra `classCount` 1 para ele, o painel e a prévia de inativação listam a turma, e tentar desativar a modalidade ou a unidade da turma devolve a contagem de turmas ativas; turma desativada não conta.
- CA9 (R9): dado a tela em 360 px, então sem rolagem horizontal e axe sem violações nos dois temas; estados carregando, vazio (sem turmas, e outro texto quando o filtro não acha nenhuma, com "Limpar filtros"), erro com "Tentar de novo" e sem permissão; trocar filtro ou página refaz a consulta; turma desativada tem selo "Desativada"; faixa etária ausente mostra traço.
- CA10 (R9): dado o menu, então "Turmas" aparece em Operação e leva a `/turmas` para Coordenador e Administrador e não aparece para Professor.
- CA11 (R10): `OpenApiContractTest` e `ContractRoutesTest` passam com a rota; testes MSW cobrem a rota no formato do servidor; `SeedDataIT` aplica o novo seed.
- CA12 (todos): ponta a ponta Playwright `@critical` "Coordenador filtra a lista de turmas": abre Operação › Turmas, vê a lista, filtra por dia e por modalidade e vê só a turma esperada.

## 4. Plano técnico

- **Dados.** Migração `V24__training_class.sql` (depois de `V23__teacher.sql`), com RLS `organization_scope` como em `V22`/`V23`: `training_class` (`id`, `organization_id`, `unit_id`, `name` 2 a 80, `name_key` por `Modalities.normalize`, `modality_id` para `modality`, `age_range_id` nulo para `age_range`, `capacity` nulo com `check` de 1 a 200, `head_teacher_id` não nulo, `group_link` nulo, `status` com `check` em `ACTIVE`/`INACTIVE`, `created_at`, `unique (organization_id, id)`, chaves estrangeiras compostas para `unit` e `teacher` como em `teacher_unit`; índice único parcial `(unit_id, name_key)` onde `status = 'ACTIVE'`); `training_class_schedule` (`organization_id`, `class_id`, `weekday` 1 a 7, `starts_at time`, `ends_at time`, `check (ends_at > starts_at)`, chave `(class_id, weekday)`); `training_class_assistant` (`organization_id`, `class_id`, `teacher_id`, chave `(class_id, teacher_id)`). Sem nenhuma coluna de presença (R4). Seed `infra/seed/10-turmas.sql` (`on conflict do nothing`) e ajuste de `SeedDataIT` (limpeza das tabelas novas, como foi feito para professor, antes de apagar unidade, professor e modalidade).
- **API** (`/api/v1/classes`, cookie como as demais): `GET` (200 `PageResponse` de `ClassRow`; 400, 401, 403). Usa `PageRequest`/`PageResponse` já existentes. Rota, `ClassRow` e `ClassList` em `docs/openapi.yaml` no mesmo PR. Nenhuma rota de escrita aqui.
- **Back-end.** Pacote novo `br.com.ctcore.trainingclass` (a criar): `TrainingClasses.java` (consulta com `JdbcClient` dentro de `OrganizationTransactions.execute`; `permissions.effective(who).get(Permission.CLASSES_MANAGE)` como `Teachers.requireAccess`; alcance do Administrador por `who.units()` em `unit_id`; filtros por parâmetros ligados; `activeStudents` por um único método privado que devolve 0 até existir matrícula, para a Sprint 07 trocar) e `TrainingClassController.java`. A alterar: `Teachers.java` (`CLASS_COUNT` vira contagem de turmas ativas em que é titular ou auxiliar; `headClasses` lista as turmas ativas em que é titular, `ClassRef` com `role`), `ModalityUsage.java` (`activeClasses`) e `UnitDeactivationBlockers.java` (`activeClasses`), cada um já com um ponto único de contagem; `OpenApiContractTest` se depender dos esquemas.
- **Interface.** A criar `frontend/src/features/classes/ClassesPage.tsx`, `ClassFilters.tsx`, `useClasses.ts` (`fetchClasses` e opções dos filtros, como `useTeachers.ts`) e testes. A alterar `App.tsx` (rota `/turmas`), `permissions/menuCatalog.ts` (`classes` com `available = true`; ajustar `buildMenu.test.ts`), `mocks/handlers.ts` (+ teste MSW), `i18n/pt-BR.json`. A lista usa `QueryPage`/`DataTable` como `TeachersPage`; opções dos filtros vêm de `GET /modalities`, `GET /units/reachable`, `GET /age-ranges` e `GET /teachers` (rotas que já existem). Dias e horários saem como "Seg 19:00–20:00, Qua 19:00–20:00"; nomes dos dias em `pt-BR.json`.
- **Segurança.** Organização, perfil e alcance vêm do principal da sessão; filtros ligados como parâmetros; a lista não devolve contato de professor nem link de grupo (fica para o painel, #47); tela só de leitura, sem ação que altere dado, então não há auditoria nova.
- **Riscos.** (1) #47 e #48 podem pedir ajuste na tabela; a migração é aditiva e ajustes entram em nova versão. (2) A troca dos pontos de contagem (R8) mexe em código de #38, #34, #43 e #45: os testes existentes de cada um precisam continuar passando, e a regressão completa confere. (3) `activeStudents` fixo em 0 esconde o aviso de capacidade até a Sprint 07; o teste do aviso insere os alunos ativos por um ponto de teste ou prova só `overCapacity` pelo método de cálculo, e o seed não cria alunos.

## 5. Tarefas

Uma parte só: servidor (T1 a T3), contrato (T4) e tela (T5 e T6).

- [x] T1: migração `V24__training_class.sql`, seed `infra/seed/10-turmas.sql` e ajuste de `SeedDataIT` — cobre CA3 (limite de capacidade), CA5 (restrições), CA7 (RLS), CA11 (seed) — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test='SeedDataIT,TrainingClassIT'`.
- [x] T2: `TrainingClasses` e `TrainingClassController` com permissão, alcance, paginação, filtros, `overCapacity` e testes de integração (lista, ordem, filtros um a um e combinados, aviso de capacidade, sem presença no esquema nem na resposta, perfis, Administrador por unidade, isolamento com RLS) — cobre CA1 a CA4, CA6, CA7 — prova: `verify -Dit.test=TrainingClassIT` (a criar).
- [x] T3: trocar os pontos fixos em zero por contagem real em `Teachers`, `ModalityUsage` e `UnitDeactivationBlockers`, com testes no `TrainingClassIT` e os `*IT` existentes de professor, modalidade e unidade passando — cobre CA8 — prova: `verify -Dit.test='TrainingClassIT,TeacherIT,TeacherDeactivationIT,ModalityIT,UnitDeactivationIT'`.
- [x] T4: OpenAPI (operação e esquemas) e handler MSW com teste — cobre CA11 — prova: `./backend/mvnw -f backend/pom.xml test -Dtest='OpenApiContractTest,ContractRoutesTest'` e `npx vitest run src/mocks` em `frontend/`.
- [x] T5: `ClassesPage`, `ClassFilters`, `useClasses`, rota e menu disponível, quatro estados, paginação, aviso de capacidade, textos pt-BR; 360 px e axe nos dois temas — cobre CA3 (texto do aviso), CA4 (textos), CA9, CA10 — prova: `npx vitest run src/features/classes src/features/permissions`.
- [x] T6: e2e `@critical` "Coordenador filtra a lista de turmas" (`frontend/e2e/turmas.spec.ts`) e regressão completa (a regressão não cobre critério: confere cobertura e os 100% nos pacotes exigidos) — cobre CA12 — prova: `./backend/mvnw -f backend/pom.xml verify` e, em `frontend/`, `npm run lint && npm run typecheck && npm run build && npm run test:cov && npm run e2e`.

## 6. Decisões

- D1: o modelo de dados da turma nasce aqui, completo (campos de "Dados" da turma), porque a lista não tem de onde ler sem ele e #47/#48 o esperam; criar, editar e desativar são de #48. Alternativa: tabela mínima agora e ampliar depois; descartada por gerar migrações em cadeia na mesma sprint (mesmo caminho de #42).
- D2 (muda o comportamento): sem filtro de situação a lista traz Ativas e Desativadas, com selo na desativada, e o filtro `weekday` casa com qualquer dia do horário da turma. Igual a Professores (#42/D2): a situação é coluna e filtro, e a turma desativada continua no histórico (RN-TUR-03). Alternativa: ocultar desativadas por padrão; descartada por esconder cadastros da gestão.
- D3: lista paginada pelo envelope `PageResponse` (25/50/100, padrão 25), ordenada por nome. Alternativa: sem paginação; descartada porque turmas crescem com a unidade.
- D4: além das colunas do card, a linha traz a unidade (nome e id) e os auxiliares junto do titular, porque a turma é da unidade, o Coordenador vê várias, e o filtro de professor casa titular e auxiliar (RN-TUR-05). Alternativa: só titular e sem unidade; descartada porque duas turmas de mesmo nome em unidades diferentes ficariam indistinguíveis.
- D5: `activeStudents` vale 0 até a matrícula existir (Sprint 07); fica em um só método de `TrainingClasses` para trocar por contagem real. O cálculo de `overCapacity` e o texto do aviso já valem e são provados com valores injetados. Alternativa: ocultar a coluna; descartada porque o card a pede.
- D6 (muda o comportamento): a lista usa a permissão `CLASSES_MANAGE`, que a tabela de permissões dá a Coordenador e Administrador (por unidade) e nega ao Professor; o Professor vê as suas turmas pela Agenda (`AGENDA_VIEW`, #49) e pelo painel do professor, não por esta lista. O menu "Turmas" já está ligado a essa permissão. Alternativa: abrir a lista de leitura ao Professor com "só as suas turmas" (`T` da legenda); descartada por contrariar a linha "Turmas: criar e editar — " e não estar no card.
- D7: trocar os contadores fixos de #38, #34, #42, #43 e #45 entra aqui, porque os pontos únicos foram deixados para a tabela de turmas e a lista de turmas passa a existir só com eles consistentes; sem mudança de contrato. Alternativa: deixá-los para #48; descartada porque turmas do seed já existiriam e os números ficariam errados.
- D8: o canvas `Turmas.dc.html` não está acessível; a tela segue os padrões do projeto (tabela em cartão, filtros no topo, como Professores) e o dono confere o layout no PR. `RelatoriosAulas.dc.html` é da Sprint 19 e fica fora.
- D9: os horários são horas de parede da unidade (`time` sem fuso); a conversão para o fuso da unidade é da agenda (#49). Suposição: se a agenda precisar de outro formato, ajusta-se por nova migração.
- Suposição: o nome da modalidade, da faixa etária, da unidade e do professor vêm de `modality`, `age_range`, `unit` e `teacher` (#38, #41, #13, #42); desativar um deles não apaga a turma, e a lista continua mostrando o nome.

## Construção

- T1 — arquivos: `backend/src/main/resources/db/migration/V24__training_class.sql`, `infra/seed/10-turmas.sql`, `backend/src/test/java/br/com/ctcore/db/SeedDataIT.java` — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test='SeedDataIT,TrainingClassIT,TeacherIT,TeacherDeactivationIT,ModalityIT,UnitDeactivationIT'` → SeedDataIT 12 testes verdes (inclui `classesSeed`); restrições da tabela e RLS provadas no `TrainingClassIT`.
- T2 — arquivos: `backend/src/main/java/br/com/ctcore/trainingclass/TrainingClasses.java`, `TrainingClassController.java`, `backend/src/test/java/br/com/ctcore/trainingclass/TrainingClassIT.java` — prova: `verify -Dit.test=TrainingClassIT` → 8 testes verdes (CA1 a CA7).
- T3 — arquivos: `teacher/Teachers.java`, `modality/ModalityUsage.java`, `organization/UnitDeactivationBlockers.java` (a classe `None` virou `Jdbc`, com contagem real de turmas ativas; alunos seguem 0) — prova: mesmo comando de T1 → TeacherIT 11, TeacherDeactivationIT 6, ModalityIT 5, UnitDeactivationIT 7 verdes; CA8 no `TrainingClassIT.realCounts`.
- T4 — arquivos: `docs/openapi.yaml` (rota `/classes`, `ClassRow`, `ClassRef`, `ClassList`; textos de `classCount` e `headClasses` atualizados), `frontend/src/mocks/handlers.ts`, `frontend/src/mocks/classes.msw.test.ts` — prova: `verify -Dtest='OpenApiContractTest,ContractRoutesTest'` → 7 verdes; `npx vitest run src/mocks/classes.msw.test.ts` → verde.
- T5 — arquivos: `frontend/src/features/classes/{ClassesPage,ClassFilters}.tsx`, `useClasses.ts`, `ClassesPage.test.tsx`, `App.tsx`, `permissions/menuCatalog.ts`, `permissions/buildMenu.test.ts`, `i18n/pt-BR.json` — prova: `npx vitest run src/features/classes src/features/permissions` → verde (inclui axe a 360 px nos dois temas).
- T6 — arquivos: `frontend/e2e/turmas.spec.ts` — prova: `npm run e2e` → 24 passaram (inclui "Coordenador filtra a lista de turmas").

Regressão: `./backend/mvnw -f backend/pom.xml verify` → BUILD SUCCESS (JaCoCo incluso); em `frontend/`: `npm run lint && npm run typecheck && npm run build` → verdes; `npm run test:cov` → verde (linhas 96,75%; `features/classes` 98%); `npm run e2e` → 24 passaram. Sem linha de base registrada para comparar; nada que passava passou a falhar.
Desvios: (1) os contadores `None` de `ModalityUsage` e `UnitDeactivationBlockers` foram renomeados para `Jdbc` (deixam de ser "nenhum"); (2) o filtro de unidade entrou também na tela (a issue pede a API); (3) o e2e ativa Judô em Modalidades antes, porque o filtro só lista modalidades ativas e o mock começa sem nenhuma ativa; (4) `activeStudents` e o aviso de capacidade na tela são provados com valor injetado (`overCapacity` estático e teste de página), como o plano prevê.
Não é do item: nenhum.

## Verificação

### Rodada 1 — OK — 2026-10-19, estado 89cc494ff7fff3d1f8817ab99e15402b698a2211

| Critério | Status | Evidência |
|---|---|---|
| CA1 | PASSOU | `TrainingClassIT.list` (verify: 307 testes, 0 falhas); leitura de `TrainingClasses`: ordem por nome/id, `size` inválido dado 400 no teste |
| CA2 | PASSOU | `TrainingClassIT.filters`; filtros por parâmetros ligados, `weekday`/`status` inválidos dão `ApiValidationException` |
| CA3 | PASSOU | `TrainingClassIT.capacity` (check 1 a 200) e `ClassesPage.test` (aviso com números); `overCapacity` provado por cálculo estático |
| CA4 | PASSOU | `TrainingClassIT.noAttendance` (information_schema, corpo bruto) e teste de `pt-BR.json` |
| CA5 | PASSOU | `TrainingClassIT.tableRules`; índice único parcial e checks em `V24` |
| CA6 | PASSOU | `TrainingClassIT.permissions` |
| CA7 | PASSOU | `TrainingClassIT.isolation`; RLS forçada nas três tabelas em `V24` |
| CA8 | PASSOU | `TrainingClassIT.realCounts`; TeacherIT, ModalityIT, UnitDeactivationIT verdes no verify |
| CA9 | PASSOU | `ClassesPage.test.tsx` (estados, filtros, selo, traço, axe); 360 px conferido só por teste, sem navegador real |
| CA10 | PASSOU | `buildMenu.test.ts` e teste "CA10" |
| CA11 | PASSOU | `OpenApiContractTest`/`ContractRoutesTest` dentro do verify; `classes.msw.test.ts`; `SeedDataIT` verde |
| CA12 | PASSOU | `npm run e2e`: 24 passaram, inclui "Coordenador filtra a lista de turmas" |

Comandos: `./backend/mvnw -f backend/pom.xml verify` → BUILD SUCCESS, 307 testes, JaCoCo ok; em `frontend/`: `npm run lint && typecheck && build && test:cov` → verdes (linhas 96,75%); `npm run e2e` → 24 passaram. Sem linha de base registrada; nenhuma falha.
Achados:
- sugestão, código, `TrainingClasses.activeStudents` — método sem uso de `classId` (ponto de troca da Sprint 07, previsto em D5); sem ação agora.

## Entrega

- Lista de turmas em Operação › Turmas: migração V24 (turma, horários e vínculo de professores, com RLS), `GET /api/v1/classes` com filtros e paginação, contagens reais de turmas ativas em professor, modalidade e inativação de unidade, tela com quatro estados, menu, OpenAPI, MSW e seed `10-turmas.sql`.
- Verificação: `verify` do back-end (307 testes, JaCoCo ok); lint, typecheck, build, `test:cov` (96,75%) e `e2e` (24) no front-end. Rodada 1 OK.
- Decisões: contadores `None` viraram `Jdbc`; `activeStudents` segue 0 até a Sprint 07 (provado com valor injetado); filtro de unidade também na tela.
- Limitação: 360 px conferido só por teste, sem navegador real.
