# 42 — RF-PRO-01 · Pessoas › Professores (lista)

Origem: issue #42 (Sprint 04); `docs/especificacao/05-professores.md` (RF-PRO-01, RN-PRO-01, RN-PRO-03, RN-PRO-04, RN-PRO-07, "Dados"), `01-organizacao-e-isolamento.md` e a linha `TEACHERS` da tabela de permissões. Branch: `item/42-pessoas-professores-lista`. Segue o desenho de #41 (`AgeRanges`, `AgeRangesPage`, `V22`).

## 1. Objetivo

Em Pessoas › Professores, a gestão vê os professores da organização em lista paginada, com foto, nome, modalidades, unidades, turmas e situação, e filtra por nome, modalidade, unidade e situação. O Coordenador vê todos; o Administrador vê os que dão aula em ao menos uma unidade dele. Como o cadastro do professor ainda não existe, este item cria o modelo de dados do professor (todos os campos de "Dados"), que #43, #44 e #45 usam depois.

Fora do escopo: painel do professor (#43), janela de cadastro e edição, upload da foto e convite de acesso (#44), inativar (#45), turmas (Sprint 05; aqui a coluna mostra 0), diretório e horários públicos (Sprints 18 e 20; aqui só se guardam o consentimento e a declaração da gestão), notas de versão (Sprint 15, #142).

## 2. Requisitos

- R1 (RF-PRO-01, D3, D4): `GET /api/v1/teachers` devolve, no envelope `{items, page, size, total}`, os professores da organização da sessão com `id`, `name`, `photoUrl` (URL temporária assinada ou nulo), `modalities` (id, nome e graduação: nível e grau, ou nulos), `units` (id e nome), `classCount` (0 até a Sprint 05) e `status` (`ACTIVE` ou `INACTIVE`), ordenados por nome e depois id; aceita `page`, `size` e `sort=name`.
- R2 (RF-PRO-01, D2): filtros opcionais `q` (parte do nome, sem diferença de acento nem de maiúsculas), `modalityId`, `unitId` e `status`; combinam entre si (E); sem filtro devolve todos; valor inválido de `status` dá 400.
- R3 (RN-PRO-01): cada professor é um cadastro da organização ligado às unidades onde dá aula (tabela `teacher_unit`); um cadastro por conta por organização.
- R4 (RN-PRO-03): a graduação do professor é uma por modalidade que ele ensina (nível do sistema da organização e grau), guardada no cadastro do professor e sem relação com o registro de graduação de alunos.
- R5 (RN-PRO-04, D5): o cadastro guarda `directoryConsent` e a declaração da gestão "o professor autorizou"; a lista devolve `directoryConsent` e nunca devolve telefone nem e-mail.
- R6 (RN-PRO-07): o professor que também é aluno usa a mesma conta (`teacher.account_id`); a lista mostra um só cadastro dele, sem duplicar por vínculo, e `access` (`NONE`, `PENDING`, `ACTIVE`, `ENDED`) vem do vínculo `TEACHER` da conta, sem depender de ele ter vínculo de aluno.
- R7 (permissão): `TEACHERS` conferido no servidor: Coordenador (todas as unidades) e Administrador (unidades dele); Professor, Aluno e Responsável recebem 403; sem sessão 401. O Administrador não vê professor sem nenhuma unidade dele e `unitId` fora do alcance dele devolve lista vazia.
- R8 (isolamento): os dados são sempre da organização da sessão; o corpo e a URL nunca definem organização; as tabelas novas têm filtro no código e row-level security.
- R9 (tela): `/professores` mostra a lista (avatar com iniciais quando não há foto, nome, modalidades com graduação, unidades, turmas, situação) e os filtros nome, modalidade, unidade e situação, com paginação; tem os quatro estados, funciona a partir de 360 px, passa no axe nos dois temas, tem todos os textos em `pt-BR.json` e o item "Professores" do menu fica disponível com a permissão `TEACHERS`.
- R10 (contrato e dados fictícios): `docs/openapi.yaml` e os handlers MSW refletem a rota; o seed acrescenta professores fictícios às organizações de teste.

## 3. Critérios de aceite

- CA1 (R1, R3): dado organização com professores cadastrados, quando o Coordenador faz `GET /teachers`, então 200 com `items` ordenados por nome, cada um com unidades, modalidades com graduação, `classCount` 0 e `status`; `total`, `page` e `size` corretos e `size` fora de 25/50/100 dá 400.
- CA2 (R2): dado vários professores, quando filtra por `q` ("joao" acha "João"), `modalityId`, `unitId` e `status`, sozinhos e combinados, então só voltam os que atendem a todos os filtros; `status=XYZ` dá 400.
- CA3 (R4): dado professor que ensina duas modalidades, então cada modalidade traz seu nível e grau; a tabela recusa duas graduações para a mesma modalidade do mesmo professor e grau acima do máximo do nível.
- CA4 (R5): dado professor com telefone e e-mail gravados, quando lista, então a resposta (texto bruto) não contém nenhum dos dois nem as chaves `email` e `phone`; `directoryConsent` reflete o gravado.
- CA5 (R6): dado conta com vínculos `TEACHER` e `STUDENT` na organização, então o professor aparece uma única vez com `access` igual ao estado do vínculo `TEACHER`; sem vínculo `TEACHER`, `access = NONE`; duas contas iguais para dois cadastros da mesma organização são recusadas pela tabela.
- CA6 (R7): dado Administrador de A, então só vê professores com ao menos uma unidade de A (e vê todas as unidades deles), e `unitId` de unidade que não é dele devolve `items` vazio; Professor, Aluno e Responsável recebem 403; sem sessão, 401.
- CA7 (R8): dado duas organizações com professores de mesmo nome, então cada uma só vê os seus; consulta direta com o papel `ctcore_app`: sem contexto 0 linhas, com contexto só as da própria organização, insert com outra organização negado, nas três tabelas novas.
- CA8 (R9): dado a tela em 360 px, então sem rolagem horizontal e axe sem violações nos dois temas; estados carregando, vazio (sem professores, e outro texto quando o filtro não acha nenhum, com "Limpar filtros"), erro com "Tentar de novo" e sem permissão; trocar filtro ou página refaz a consulta; foto ausente mostra as iniciais.
- CA9 (R9): dado o menu, então "Professores" aparece e leva a `/professores` para Coordenador e Administrador e não aparece para Professor.
- CA10 (R10): `OpenApiContractTest` e `ContractRoutesTest` passam com a rota; testes MSW cobrem a rota no formato do servidor; `SeedDataIT` aplica o novo seed.
- CA11 (todos): ponta a ponta Playwright `@critical` "Coordenador filtra a lista de professores": abre Pessoas › Professores, vê a lista, filtra por nome e vê só o professor esperado.

## 4. Plano técnico

- **Dados.** Migração `V23__teacher.sql` (depois de `V22__age_range.sql`), com RLS `organization_scope` como em `V21`/`V22`: `teacher` (`id`, `organization_id`, `name` 2 a 80, `name_key` normalizado por `Modalities.normalize`, `photo` nulo com o nome do arquivo no armazenamento, `email`, `phone`, `bio` até 300, `directory_consent` boolean, `consent_declared_by_management` boolean, `status` com `check` em `ACTIVE`/`INACTIVE`, `account_id` nulo para `account`, `created_at`); índice único `(organization_id, account_id)` onde `account_id` não é nulo; `teacher_unit` (`organization_id`, `teacher_id`, `unit_id`, chave composta e chaves estrangeiras compostas como `account_link_unit`); `teacher_modality` (`organization_id`, `teacher_id`, `modality_id`, `level_id` nulo para `graduation_level`, `degree` 0 a 10, chave `(teacher_id, modality_id)`). O grau dentro do máximo do nível é conferido por gatilho ou consulta na escrita (#44); aqui, por `check`/gatilho simples na tabela (CA3). Seed `infra/seed/09-professores.sql` (`on conflict do nothing`) e ajuste de `SeedDataIT`.
- **API** (`/api/v1/teachers`, cookie como as demais): `GET` (200 `PageResponse` de `TeacherRow`; 400, 401, 403). Primeiro uso de `br.com.ctcore.api.page.PageRequest`/`PageResponse`. Nenhum campo de contato na resposta. Rota em `docs/openapi.yaml` no mesmo PR.
- **Back-end.** Pacote novo `br.com.ctcore.teacher`, a criar: `Teachers.java` (consulta com `JdbcClient` dentro de `OrganizationTransactions.execute`; `permissions.effective(who).get(Permission.TEACHERS)` como `StaffUsers.manager`; alcance do Administrador por `who.units()` em `teacher_unit`; URL da foto assinada pelo mesmo mecanismo do `logoUrl` em `OrganizationProfile` com `FileStorage`) e `TeacherController.java`. A alterar: `OpenApiContractTest`/`docs/openapi.yaml` (esquemas `TeacherRow`, `TeacherList`).
- **Interface.** A criar `frontend/src/features/teachers/TeachersPage.tsx`, `TeacherFilters.tsx`, `useTeachers.ts` (`useState/useEffect`, como `useModalities.ts`) e testes. A alterar `App.tsx` (rota `/professores`), `permissions/menuCatalog.ts` (`teachers` com `available = true`; ajustar `buildMenu.test.ts` se depender disso), `mocks/handlers.ts` (+ teste MSW), `i18n/pt-BR.json`. Opções dos filtros vêm das listas de modalidades e unidades que já existem (`GET /modalities`, `GET /units`).
- **Segurança.** Organização, perfil e alcance vêm do principal da sessão; filtros são parâmetros ligados (sem concatenar texto); a foto sai só como URL temporária; telefone e e-mail ficam fora do DTO da lista (RN-PRO-04); a tela é só leitura, sem ação que altere dado, então não há auditoria nova.
- **Riscos.** (1) #43 e #44 podem precisar de ajuste na tabela; a migração é aditiva, então ajustes entram em nova versão. (2) A coluna Turmas vale 0 até a Sprint 05: o valor fica em um só ponto de `Teachers` e a Sprint 05 o troca por contagem real. (3) `access` depende de `account_link`; conferir o isolamento dessa junção na consulta.

## 5. Tarefas

Uma parte só: servidor (T1 e T2), contrato (T3) e tela (T4 e T5).

- [x] T1: migração `V23__teacher.sql`, seed `infra/seed/09-professores.sql` e ajuste de `SeedDataIT` — cobre CA3 (restrições da tabela), CA5 (índice de conta), CA7 (RLS), CA10 (seed) — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test='SeedDataIT,TeacherIT'`.
- [x] T2: `Teachers` e `TeacherController` com permissão, alcance, paginação, filtros e testes de integração (lista, ordem, filtros, graduação por modalidade, sem contato, mesma conta com dois vínculos, perfis, Administrador por unidade, isolamento com RLS) — cobre CA1 a CA7 — prova: `verify -Dit.test=TeacherIT` (a criar).
- [x] T3: OpenAPI (operação e esquemas) e handler MSW com teste — cobre CA10 — prova: `./backend/mvnw -f backend/pom.xml test -Dtest='OpenApiContractTest,ContractRoutesTest'` e `npx vitest run src/mocks` em `frontend/`.
- [x] T4: `TeachersPage`, `TeacherFilters`, `useTeachers`, rota e menu disponível, quatro estados, paginação, textos pt-BR; 360 px e axe nos dois temas — cobre CA8, CA9 — prova: `npx vitest run src/features/teachers src/features/permissions`.
- [x] T5: e2e `@critical` "Coordenador filtra a lista de professores" (`frontend/e2e/professores.spec.ts`) e regressão completa (a regressão não cobre critério: confere cobertura e os 100% nos pacotes exigidos) — cobre CA11 — prova: `./backend/mvnw -f backend/pom.xml verify` e, em `frontend/`, `npm run lint && npm run typecheck && npm run build && npm run test:cov && npm run e2e`.

## 6. Decisões

- D1: o modelo de dados do professor nasce aqui, completo (campos de "Dados"), porque a lista não tem de onde ler sem ele e #43/#44/#45 já o esperam; o cadastro por tela é de #44. Alternativa: tabela mínima agora e ampliar depois; descartada por gerar migrações em cadeia dentro da mesma sprint.
- D2 (muda o comportamento): sem filtro de situação a lista traz Ativos e Inativos, e a tela abre em "Todas". Diferente de Configurações › Usuários, porque a especificação pede a situação como coluna e filtro, e o professor inativo continua no histórico (RN-PRO-05). Alternativa: ocultar inativos por padrão; descartada por esconder cadastros da gestão.
- D3: lista paginada pelo envelope `PageResponse` (25/50/100, padrão 25), ordenada por nome. Alternativa: lista sem paginação como as de poucos itens; descartada porque professores crescem com a organização.
- D4: `classCount` fixo em 0 até a Sprint 05, por não existir turma; a coluna aparece na tela com 0. Alternativa: ocultar a coluna; descartada porque o card a pede.
- D5 (muda o comportamento): este item guarda o consentimento (`directoryConsent`) e a declaração da gestão, mas nenhuma rota pública os usa; a regra de aparecer no diretório e nos horários públicos é aplicada nas Sprints 18 e 20, e a garantia deste item é que telefone e e-mail nunca saem na lista. Alternativa: criar já a projeção pública; descartada por não haver tela nem rota pública.
- D6: `access` vem do vínculo `TEACHER` da conta ligada ao cadastro; professor sem conta tem `NONE`. A lista não mostra o vínculo de aluno (RN-PRO-07 só exige conta única).
- D7 (muda o comportamento): o Administrador vê o professor com ao menos uma unidade dele e vê todas as unidades desse professor, como em Configurações › Usuários (#37). Alternativa: mostrar só as unidades dele; descartada por esconder onde o professor trabalha.
- D8: o canvas `Professores.dc.html` não está acessível; a tela segue os padrões do projeto (tabela em cartão, filtros no topo) e o dono confere o layout no PR.
- D9 (muda o comportamento, ajuste da revisão da Sprint 04, R04-1): com a graduação da modalidade desligada em `organization_modality` (ou sem linha), a lista (`GET /teachers`) e o painel (`GET /teachers/{id}`) devolvem `levelId`, `level` e `degree` nulos para ela, como o "Pronto quando" de `04` pede ("com graduação desligada, nenhuma tela mostra faixa"). O dado em `teacher_modality` não é apagado: volta a aparecer ao religar, e o PUT de #44 também o preserva. Sem migração nem mudança de contrato. Provado em `TeacherIT.graduationOff` e `TeacherWriteIT.updateKeepsHiddenLevel`.
- Suposição: o nome da modalidade e o nível vêm de `modality` e `graduation_level` (#38, #39); se um nível for apagado depois, `level_id` fica nulo e a lista mostra só a modalidade.

## Construção

- T1 — arquivos: `backend/src/main/resources/db/migration/V23__teacher.sql`, `infra/seed/09-professores.sql`, `backend/src/test/java/br/com/ctcore/db/SeedDataIT.java` (limpeza das 3 tabelas novas antes e depois, teste do seed) — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test='SeedDataIT,TeacherIT' -Dtest=NoSuchTest -Dsurefire.failIfNoSpecifiedTests=false -Djacoco.skip=true` → 18 testes, 0 falhas
- T2 — arquivos: `backend/src/main/java/br/com/ctcore/teacher/Teachers.java`, `TeacherController.java`, `backend/src/test/java/br/com/ctcore/teacher/TeacherIT.java` (7 testes: lista, filtros, graduação, sem contato, conta única, perfis e alcance, RLS nas 3 tabelas) — prova: mesmo comando de T1 → 0 falhas
- T3 — arquivos: `docs/openapi.yaml` (`/teachers`, `TeacherRow`, `TeacherList`), `frontend/src/mocks/handlers.ts`, `frontend/src/mocks/teachers.msw.test.ts` — prova: `./backend/mvnw -f backend/pom.xml test -Dtest='OpenApiContractTest,ContractRoutesTest'` → 7 testes, 0 falhas; `npx vitest run src/mocks/teachers.msw.test.ts` → 4 testes passam
- T4 — arquivos: `frontend/src/features/teachers/{TeachersPage.tsx,TeacherFilters.tsx,useTeachers.ts,TeachersPage.test.tsx}`, `frontend/src/App.tsx`, `features/permissions/menuCatalog.ts`, `buildMenu.test.ts`, `i18n/pt-BR.json` — prova: `npx vitest run src/features/teachers src/features/permissions` → 23 testes passam (inclui axe a 360 px nos dois temas)
- T5 — arquivos: `frontend/e2e/professores.spec.ts` — prova: `npm run e2e` → 20 passam, incluindo "Coordenador filtra a lista de professores"
Regressão: `./backend/mvnw -f backend/pom.xml verify` → 270 testes de integração e 126 rápidos, 0 falhas, BUILD SUCCESS (JaCoCo incluído); em `frontend/`: `npm run lint`, `npm run typecheck`, `npm run build` sem erro; `npm run test:cov` → 494 testes passam, `features/teachers` com 97,87% de linhas.
Desvios: (1) a lista usa `QueryPage`/`DataTable` já existentes (busca, ordem, página e tamanho no endereço) em vez de `useState/useEffect` próprio; `useTeachers.ts` guarda `fetchTeachers` e as opções dos filtros. (2) O vazio sem professores usa o texto padrão do `QueryPage` ("Nada por aqui ainda"); o do filtro sem resultado é o padrão com "Limpar filtros". (3) `sort=name` sem direção é aceito no controller e vira `name,asc`. (4) `SeedDataIT` ganhou limpeza das tabelas de professor depois de cada teste, porque o seed liga um professor a uma conta e travava o `delete from account` de outros `*IT`.
Não é do item: nenhum.

## Verificação

### Rodada 1 — OK — 2026-10-19, estado aa2e6688be77912dac7a0086c86ec43970ac578b

| Critério | Status | Evidência |
|---|---|---|
| CA1 | PASSOU | `TeacherIT.list` (ordem, unidades, modalidades, classCount 0, size 25 e 400 para size/page/sort inválidos) |
| CA2 | PASSOU | `TeacherIT.filters` |
| CA3 | PASSOU | `TeacherIT.graduationRules` + gatilho/check em V23 lido |
| CA4 | PASSOU | `TeacherIT.noContact`; DTO `Row` sem email/phone |
| CA5 | PASSOU | `TeacherIT.access`; índice único `teacher_org_account` |
| CA6 | PASSOU | `TeacherIT.permissions`; `Teachers.list` (alcance e unitId fora do alcance) |
| CA7 | PASSOU | `TeacherIT.isolation` (RLS nas 3 tabelas); policies em V23 |
| CA8 | PASSOU | `TeachersPage.test.tsx` (4 estados, filtros, iniciais, axe 360 px nos dois temas) |
| CA9 | PASSOU | `TeachersPage.test.tsx` CA9 + `buildMenu.test.ts` |
| CA10 | PASSOU | `verify` inclui OpenApiContractTest/ContractRoutesTest/SeedDataIT; `teachers.msw.test.ts` na suíte |
| CA11 | PASSOU | `npm run e2e` → 20 passam, inclui o `@critical` de professores |

Comandos:
- `./backend/mvnw -f backend/pom.xml verify` → 270 testes de integração, 0 falhas, BUILD SUCCESS (JaCoCo incluído)
- `npm run lint`, `npm run typecheck` → sem erro; `npm run test:cov` → 494 passam, `features/teachers` 97,87%
- `npm run e2e` → 20 passam

Achados:
- sugestão, código, `Teachers.java` — `sort=name` sem direção tratado no controller; aceitável (desvio 3 registrado).
- sugestão, especificação, `build` e `npm audit` não rodados nesta rodada; `npm run build` foi relatado pela construção, não reconferido.

## Entrega

- Muda: nova rota `GET /api/v1/teachers` (lista paginada, filtros, sem telefone nem e-mail), migração V23 com RLS nas 3 tabelas de professor, seed `09-professores.sql`, tela Pessoas › Professores com os quatro estados, item de menu e handlers MSW; contrato em `docs/openapi.yaml`.
- Verificação: `verify` do back-end (270 IT, 126 rápidos, 0 falhas), lint, typecheck, build, `test:cov` (494 testes; `features/teachers` 97,87%) e `npm run e2e` (20 passam).
- Decisões: lista traz Ativos e Inativos (D2); `classCount` fixo em 0 até a Sprint 05 (D4); consentimento do diretório só é guardado, a regra pública vem nas Sprints 18 e 20 (D5); o canvas `Professores.dc.html` não estava acessível, o dono confere o layout no PR (D8).
- Limitações: `npm audit` não rodado na verificação.

Refs #42
