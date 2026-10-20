# 47 — RF-TUR-02 · Painel da turma

Origem: issue #47 (Sprint 05); `docs/especificacao/06-turmas-e-agenda.md` (RF-TUR-02, RN-TUR-03, RN-TUR-04, RN-TUR-06, "Dados"). Branch: `item/47-painel-da-turma`. Parte do modelo `training_class` criado em #46 (`V24`, `TrainingClasses`, `ClassesPage`) e segue o desenho do painel do professor (#43) e da inativação (#45).

## 1. Objetivo

A gestão abre uma turma da lista e vê o painel dela: situação, modalidade, faixa etária, unidade, horários por dia, titular e auxiliares, alunos ativos e capacidade (com o aviso), próxima aula, alunos ativos (com a marca de quem a tem como outro horário) e o grupo de WhatsApp. Do painel, Coordenador e Administrador desativam a turma, respeitando RN-TUR-03, e abrem o grupo.

Fora do escopo: janela de criar e editar turma (#48; o botão Editar aparece desativado), aulas e agenda (#49 em diante; "próxima aula" vem vazia), matrícula e alunos (Sprints 06 e 07; a lista de alunos vem vazia), comunicados (Sprint 09; "Avisar turma" desativado), tela de alunos (Sprint 06; "Ver alunos" desativado), reativar turma (o card não pede).

## 2. Requisitos

- R1 (RF-TUR-02, D1, D2): `GET /api/v1/classes/{id}` devolve `id`, `name`, `status`, `unit`, `modality`, `ageRange` (ou nulo), `schedule` (dia 1 a 7, início, término, ordenados por dia), `head`, `assistants`, `capacity` (ou nulo), `activeStudents`, `overCapacity`, `groupLink` (ou nulo), `nextClass` (ou nulo), `students` (lista de `id`, `name`, `primary`) e `deactivation` (`allowed` e `blockingStudents`); `nextClass` e `students` vêm nulo e vazia até #49 e a Sprint 07 (D1).
- R2 (RN-TUR-04, D3): cada item de `students` traz `primary` (verdadeiro se a turma é a principal do aluno naquela modalidade, falso se é outro horário); o painel mostra o selo "Outro horário" nos falsos. Sem matrícula ainda, a regra é provada pelo cálculo isolado (D3).
- R3 (RN-TUR-03, D4): `POST /api/v1/classes/{id}/deactivate` passa a turma a `INACTIVE` quando nenhum aluno a tem como turma principal; com aluno principal, 409 `HAS_PRIMARY_STUDENTS` com a contagem; turma já desativada, 409; nada é apagado (turma com histórico só é desativada).
- R4 (auditoria): desativar grava auditoria (ator, turma, unidade), sem dado pessoal.
- R5 (permissão, D5): `CLASSES_MANAGE` conferido no servidor nas duas rotas: Coordenador (todas as unidades) e Administrador (unidades dele); Professor, Aluno e Responsável recebem 403; sem sessão 401. Turma de unidade fora do alcance do Administrador ou de outra organização, 404.
- R6 (isolamento): organização sempre da sessão; consulta com filtro no código e RLS (tabelas de #46).
- R7 (RN-TUR-06): nenhum campo da API, coluna nova ou texto de tela do item guarda ou mostra presença ou frequência.
- R8 (tela, D6, D7): `/turmas/:id` mostra o painel com as seções acima e as ações Editar (desativada até #48), Ver alunos (desativada até a Sprint 06), Avisar turma (desativada até a Sprint 09), Abrir grupo (só com `groupLink` http/https, abre em nova aba) e Desativar (com confirmação, desativada se a turma já está desativada); a linha da lista de turmas leva ao painel; mostra "Acima da capacidade (31/30)" quando couber; quatro estados mais "turma não encontrada"; 360 px; axe nos dois temas; textos em `pt-BR.json`.
- R9 (contrato e dados): `docs/openapi.yaml` e handlers MSW refletem as duas rotas; o seed de #46 cobre turma com e sem grupo e uma desativada.

## 3. Critérios de aceite

- CA1 (R1): dado turma com dois horários, dois auxiliares, faixa etária, capacidade e link de grupo, quando o Coordenador faz `GET /classes/{id}`, então 200 com todos os campos de R1, horários ordenados por dia, `activeStudents` 0, `nextClass` nulo, `students` vazia e `deactivation.allowed` verdadeiro.
- CA2 (R1, R5, R6): dado Administrador de A, quando abre turma de unidade que não é dele, então 404; de unidade dele, 200; Professor, Aluno e Responsável 403; sem sessão 401; id de outra organização 404 e, com `ctcore_app`, a consulta direta não enxerga a linha.
- CA3 (R2): dado o cálculo isolado de alunos da turma com um aluno de turma principal e outro de outro horário, então `primary` é verdadeiro e falso respectivamente e `activeStudents` conta os dois; a tela mostra o selo "Outro horário" só no segundo.
- CA4 (R3, R4): dado turma ativa sem aluno principal, quando o Coordenador desativa, então 200, `status` passa a `INACTIVE`, a turma continua listada em `GET /classes?status=INACTIVE`, e há registro de auditoria; desativar de novo, 409.
- CA5 (R3): dado turma com aluno principal (contagem injetada pelo ponto único de teste), quando desativa, então 409 `HAS_PRIMARY_STUDENTS`, nada muda e `deactivation.allowed` é falso no painel.
- CA6 (R5): dado Administrador sem alcance da unidade, quando desativa, então 404; Professor 403; sem sessão 401; nada muda.
- CA7 (R7): dado o esquema do banco, o corpo bruto da resposta e `pt-BR.json`, então nenhuma tabela, coluna, campo ou texto novo menciona presença ou frequência.
- CA8 (R8): dado o painel em 360 px, então sem rolagem horizontal e axe sem violações nos dois temas; estados carregando, erro com "Tentar de novo", sem permissão e turma não encontrada; sem grupo, "Abrir grupo" fica desativado; link com esquema diferente de http/https não vira botão; Editar, Ver alunos e Avisar turma desativados; alunos e próxima aula mostram o texto de vazio; turma desativada tem selo "Desativada" e Desativar desativado; capacidade excedida mostra o aviso; clicar no nome de uma turma da lista abre o painel.
- CA9 (R3, R8): dado o diálogo de desativar, quando a turma tem alunos principais, então a tela mostra a recusa com a contagem e não oferece confirmar; sem alunos, confirmar desativa e atualiza o painel.
- CA10 (R9): `OpenApiContractTest` e `ContractRoutesTest` passam com as duas rotas; testes MSW cobrem as rotas no formato do servidor; `SeedDataIT` passa.
- CA11 (R3, R8): ponta a ponta `@critical` "Coordenador abre e desativa uma turma": abre Operação › Turmas, entra no painel de uma turma, desativa e vê o selo "Desativada".

## 4. Plano técnico

- Dados: sem migração (`training_class`, `_schedule`, `_assistant` e `group_link` existem em `V24`; a desativação só muda `status`). Seed `infra/seed/10-turmas.sql` pode ganhar turma com link de grupo e uma desativada, se faltar (`on conflict do nothing`).
- API (cookie e CSRF como as demais): `GET /api/v1/classes/{id}` (200 `ClassDetail`; 401, 403, 404); `POST /api/v1/classes/{id}/deactivate` (200 `ClassDetail`; 401, 403, 404, 409). Entram em `docs/openapi.yaml` no mesmo PR.
- Back-end: em `br.com.ctcore.trainingclass`, acrescentar a `TrainingClasses.java` o método `get` (reusa o carregamento da lista, mesma regra de alcance, mais `group_link`) e criar `TrainingClassDeactivation.java` (transação com `OrganizationTransactions.execute`, trava da linha, 409, `AuditService` com `class.deactivated`) como `TeacherDeactivation`. Pontos únicos fixos, para #49 e a Sprint 07 trocarem: `nextClass(classId)` devolve nulo; `students(classId)` devolve lista vazia e `primaryStudents(classId)` devolve 0, ao lado de `activeStudents`. Alterar `TrainingClassController.java` (duas rotas) e `docs/openapi.yaml` (`ClassDetail`, `ClassStudent`, `ClassDeactivation`).
- Interface: criar `frontend/src/features/classes/ClassPanelPage.tsx`, `DeactivateClassDialog.tsx` e teste; alterar `useClasses.ts` (detalhe e ação), `ClassesPage.tsx` (nome vira link), `App.tsx` (`/turmas/:id`), `mocks/handlers.ts` (+ teste MSW) e `i18n/pt-BR.json`. O link do grupo é URL, então o `WhatsAppButton` (que é de telefone) não se aplica.
- Segurança: organização, perfil e alcance vêm do principal; `CLASSES_MANAGE` conferido no servidor nas duas rotas; `groupLink` só nesta rota autenticada e nunca na lista; no front, só `http:` e `https:` viram botão, com `rel="noopener noreferrer"`; parâmetros ligados; auditoria sem dado pessoal.
- Arquivos: a alterar (vistos): `trainingclass/TrainingClasses.java`, `TrainingClassController.java`, `frontend/src/features/classes/ClassesPage.tsx`, `useClasses.ts`; a criar: `TrainingClassDeactivation.java`, `TrainingClassDetailIT.java`, `ClassPanelPage.tsx`, `DeactivateClassDialog.tsx`, testes, caso novo em `frontend/e2e/turmas.spec.ts`.
- Reuso: `TrainingClasses` (carregamento, alcance), `TeacherDeactivation` e `DeactivateTeacherDialog` como modelo, `AuditService`, `Modal`, estados padrão.
- Riscos: (1) #48 pode pedir campo novo no detalhe; a resposta é aditiva. (2) Com `students` e `primaryStudents` fixos, a recusa por aluno principal só é provada com valor injetado; a Sprint 07 troca os pontos e acrescenta o teste real. (3) Desativar não encerra aulas futuras porque ainda não existem; #49 trata a agenda de turma desativada.

## 5. Tarefas

Uma parte só: servidor (T1, T2), contrato (T3), tela (T4, T5).

- [x] T1: `TrainingClasses.get`, pontos únicos (`nextClass`, `students`, `primaryStudents`) e rota `GET /classes/{id}` com alcance, 404 e testes de integração (campos, perfis, Administrador por unidade, RLS, sem presença) — cobre CA1, CA2, CA3, CA7 — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test=TrainingClassDetailIT`.
- [x] T2: `TrainingClassDeactivation`, rota `POST /classes/{id}/deactivate`, auditoria e testes (sucesso, repetida, aluno principal injetado, perfis, alcance, isolamento) — cobre CA4, CA5, CA6 — prova: `verify -Dit.test=TrainingClassDetailIT`.
- [x] T3: OpenAPI (duas operações, esquemas), handlers MSW com teste e ajuste do seed — cobre CA10 — prova: `./backend/mvnw -f backend/pom.xml test -Dtest='OpenApiContractTest,ContractRoutesTest'`, `verify -Dit.test=SeedDataIT` e `npx vitest run src/mocks` em `frontend/`.
- [x] T4: `ClassPanelPage`, `DeactivateClassDialog`, `useClasses`, rota, link na lista, textos pt-BR, quatro estados, 360 px e axe — cobre CA3 (selo), CA8, CA9 — prova: `npx vitest run src/features/classes`.
- [x] T5: e2e `@critical` "Coordenador abre e desativa uma turma" e regressão completa (a regressão não cobre critério: confere cobertura e os 100% nos pacotes exigidos) — cobre CA11 — prova: `./backend/mvnw -f backend/pom.xml verify` e, em `frontend/`, `npm run lint && npm run typecheck && npm run build && npm run test:cov && npm run e2e`.

## 6. Decisões

- D1: alunos e próxima aula não existem até a Sprint 07 e #49; o painel já tem as seções e a API já devolve `students` vazia e `nextClass` nulo, em pontos únicos para trocar. Alternativa: ocultar as seções; descartada porque o card as pede.
- D2: `groupLink` aparece só no detalhe (gestão autenticada), não na lista de #46. Alternativa: incluir na lista; descartada por não ser necessária e alargar a resposta.
- D3: RN-TUR-04 (turma principal por modalidade e outros horários) só tem dados na Sprint 07; aqui a API fixa o formato (`primary`) e o teste prova o cálculo isolado e o selo da tela. Alternativa: criar já a matrícula; descartada por fugir do escopo.
- D4 (muda o comportamento): Desativar vive neste painel (nenhum outro item da Sprint 05 o cobre) e exige nenhum aluno com a turma como principal; desativada, a turma continua listada e consultável, e sai das escolhas de matrícula e de agenda quando existirem. Não há reativar. Alternativa: deixar Desativar para #48; descartada porque o card de #47 o lista e RN-TUR-03 está nas regras dele.
- D5 (muda o comportamento): as duas rotas usam `CLASSES_MANAGE` (Coordenador e Administrador por unidade), como a lista (#46/D6); Professor vê as suas turmas pela agenda e pelo painel dele. Alternativa: leitura do painel ao Professor titular; descartada por contrariar a tabela de permissões.
- D6: Editar, Ver alunos e Avisar turma aparecem desativados até #48, Sprint 06 e Sprint 09, como Editar e Inativar em #43/D6. Alternativa: omitir; descartada para o painel já ter o desenho do card.
- D7: "Abrir grupo" abre o link em nova aba só para `http:`/`https:`; link inválido não vira botão (validar na gravação é de #48). Alternativa: aceitar qualquer esquema; descartada por risco de `javascript:`.
- D8: o canvas `TurmaPainel.dc.html` não está acessível; a tela segue os padrões do projeto e o dono confere o layout no PR (como D8 de #46).
- Suposição: o painel não precisa de ação de reativar; se a gestão precisar, é item novo. Se `activeStudents` precisar de outro formato na Sprint 07, ajusta-se no ponto único.

## Construção

- T1 — arquivos: `backend/.../trainingclass/TrainingClasses.java` (`get`, `children`, `requireAccess`, pontos únicos `nextClass`/`students`/`primaryStudents`, `isPrimary`, registros `Detail`, `Student`, `Deactivation`, `NextClass`), `TrainingClassController.java` (GET `/{id}`), `TrainingClassDetailIT.java` — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test=TrainingClassDetailIT` → 7 testes passam (CA1, CA2, CA3, CA7).
- T2 — arquivos: `TrainingClassDeactivation.java` (novo), `TrainingClassController.java` (POST `/{id}/deactivate`), `ErrorCode.HAS_PRIMARY_STUDENTS`, `messages.properties`, `TrainingClassDetailIT.java` — prova: mesmo comando → CA4, CA5, CA6 passam (409 `HAS_PRIMARY_STUDENTS` com contagem via `@MockitoSpyBean` em `primaryStudents`).
- T3 — arquivos: `docs/openapi.yaml` (`/classes/{id}`, `/classes/{id}/deactivate`, `ClassDetail`, `ClassStudent`, código `HAS_PRIMARY_STUDENTS`), `frontend/src/mocks/handlers.ts`, `frontend/src/mocks/classes.msw.test.ts`, `infra/seed/10-turmas.sql` (link de grupo na 1ª turma de cada organização; a 2ª já fica sem e a 3ª já é desativada) — prova: `test -Dtest='OpenApiContractTest,ContractRoutesTest'` → passam; `verify -Dit.test=SeedDataIT` → passa; `npx vitest run src/mocks` → passam.
- T4 — arquivos: `frontend/src/features/classes/ClassPanelPage.tsx`, `DeactivateClassDialog.tsx`, `ClassPanelPage.test.tsx`, `useClasses.ts`, `ClassesPage.tsx` (nome vira link) e `ClassesPage.test.tsx`, `App.tsx` (`/turmas/:id`), `i18n/pt-BR.json` — prova: `npx vitest run src/features/classes src/mocks` → 17 arquivos, 107 testes passam (inclui axe nos dois temas a 360 px).
- T5 — arquivos: `frontend/e2e/turmas.spec.ts` (caso `@critical` "Coordenador abre e desativa uma turma") — prova: `npm run e2e` → 25 passam.
Regressão: `./backend/mvnw -f backend/pom.xml verify` → exit 0 (JaCoCo sem violação); em `frontend/`: `npm run lint`, `npm run typecheck`, `npm run build` → ok; `npm run test:cov` → 69 arquivos, 577 testes passam, cobertura sem violação; `npm run e2e` → 25 passam. Linha de base não registrada no arquivo; nada falhou.
Desvios: (1) `Deactivation.allowed` também é falso quando a turma já está desativada (o painel usa para desativar o botão). (2) No simulado (MSW) a turma "Judô Infantil" tem 2 alunos principais só no campo `deactivation`, para exercitar a recusa; `students` segue vazia e o servidor real devolve 0 até a Sprint 07. (3) O teste CA3 troca `students` e `activeStudents` pelo `@MockitoSpyBean`, porque a contagem e a lista são pontos separados.
Não é do item: nenhum.

## Verificação

### Rodada 1 — OK — 2026-10-19, estado a2d848cc76312b71a6bb93dda7ce5ddd01859a40

| Critério | Status | Evidência |
|---|---|---|
| CA1 | PASSOU | `TrainingClassDetailIT` (7 testes) passa no `verify` do back-end |
| CA2 | PASSOU | mesmo IT: perfis 403/401, Administrador por unidade 404/200, outra organização 404 e RLS |
| CA3 | PASSOU | IT (cálculo isolado com `@MockitoSpyBean`) e `ClassPanelPage.test.tsx` (selo "Outro horário") |
| CA4 | PASSOU | IT: 200, `INACTIVE`, listada em `status=INACTIVE`, auditoria `class.deactivated`, repetida 409 |
| CA5 | PASSOU | IT: 409 `HAS_PRIMARY_STUDENTS` com valor injetado; leitura do código confirma que nada muda |
| CA6 | PASSOU | IT: 404, 403, 401 sem alteração |
| CA7 | PASSOU | sem migração; `grep` em `pt-BR.json` só acha "Apresentação" (bio); campos da API sem presença/frequência |
| CA8 | PASSOU | `ClassPanelPage.test.tsx` (axe nos dois temas a 360 px, estados, link inválido, desativados, capacidade) e `ClassesPage.test.tsx` (nome vira link) |
| CA9 | PASSOU | `ClassPanelPage.test.tsx` e leitura de `DeactivateClassDialog.tsx` |
| CA10 | PASSOU | `verify` completo (314 testes, inclui `OpenApiContractTest`, `ContractRoutesTest`, `SeedDataIT`); `classes.msw.test.ts` passa |
| CA11 | PASSOU | `npm run e2e`: 25 passam, inclui "Coordenador abre e desativa uma turma" |

Comandos:
- `./backend/mvnw -f backend/pom.xml verify` → BUILD SUCCESS, 314 testes, JaCoCo sem violação.
- `npm run lint && npm run typecheck && npm run build` → ok.
- `npm run test:cov` → 1ª execução: 1 falha em `TwoFactorLogin.test.tsx` (arquivo que o item não toca); isolado: 13 passam; 2ª execução completa: 69 arquivos, 577 testes passam, cobertura sem violação. Tratada como instabilidade sob carga.
- `npm run e2e` → 25 passam.

Achados:
- sugestão, código, `frontend/src/features/auth/TwoFactorLogin.test.tsx:155` — falha intermitente sob carga (fora do item); vale observar se reaparece.
- sugestão, código, `TrainingClasses.get` — o `group_link` sai em consulta separada, sendo que podia entrar na consulta principal; sem efeito funcional.

## Entrega

- Novo painel da turma (`/turmas/:id`): `GET /api/v1/classes/{id}` e `POST /api/v1/classes/{id}/deactivate` (RN-TUR-03), com `HAS_PRIMARY_STUDENTS`, auditoria `class.deactivated`, OpenAPI, MSW, seed e telas com os quatro estados.
- Verificação: rodada 1 OK (`verify` do back-end com 314 testes, lint, typecheck, build, `test:cov` e `e2e` com 25 passando).
- Decisões: alunos e próxima aula vazios até a Sprint 07 (D1); permissão `CLASSES_MANAGE` (D5); sem reativar (D4); layout do canvas a conferir no PR (D8).
- Limitação: falha intermitente observada em `TwoFactorLogin.test.tsx` (fora do item).
