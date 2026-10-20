# 48 — RF-TUR-03 · Janela de turma

Origem: issue #48 (Sprint 05); `docs/especificacao/06-turmas-e-agenda.md` (RF-TUR-03, RN-TUR-01, RN-TUR-02, RN-TUR-05, RN-AGE-02, "Dados") e `05-professores.md` (RN-PRO-05). Branch: `item/48-janela-de-turma`. Parte do modelo `training_class` (`V24`, `TrainingClasses`, `ClassesPage`) de #46 e do painel de #47 (botão Editar desativado), e segue o desenho da janela de professor (#44).

## 1. Objetivo

Coordenador e Administrador criam e editam turma numa janela com todos os campos de "Dados": nome, modalidade, faixa etária, horários por dia, capacidade, titular, auxiliares e link do grupo. Ao mudar horários, a caixa "Avisar a turma" vem marcada. Hoje a lista e o painel leem turmas, mas só o seed as cria.

Fora do escopo: refazer aulas futuras e o comunicado em si (aulas em #49; comunicados na Sprint 09; aqui ficam o ponto de chamada e o pedido gravado, D5), matrícula e alunos (Sprints 06 e 07), desativar (#47), trocar a unidade de uma turma existente (D2), "Precisa de atenção" (painel de início; aqui só a regra de escolha, D4), notas de versão (Sprint 15).

## 2. Requisitos

- R1 (RF-TUR-03, RN-TUR-05, D1): `POST /api/v1/classes` cria turma `ACTIVE` na unidade informada com `unitId`, `name` (2 a 80, sem espaços nas pontas), `modalityId`, `ageRangeId` (opcional), `schedule` (ao menos um horário; um por dia 1 a 7, `startsAt` < `endsAt`, formato HH:mm), `capacity` (opcional, 1 a 200), `headTeacherId` (obrigatório), `assistantIds` (opcional, sem repetir o titular nem repetidos) e `groupLink` (opcional); responde 201 com `ClassDetail`.
- R2 (RF-TUR-03, D2, D3): `PUT /api/v1/classes/{id}` substitui os mesmos campos, menos `unitId`, que não muda, com horários e auxiliares como conjunto completo; responde 200 com `ClassDetail`; turma `INACTIVE` dá 409 (D3).
- R3 (RN-TUR-01): o nome é único na unidade entre turmas ativas, comparado por `name_key` (sem caixa nem acento); repetido dá 409 com o campo `name`; mesma modalidade e horário com nome diferente é permitido; ao editar, a própria turma não conflita consigo.
- R4 (RN-TUR-05, RN-PRO-05, D4): titular obrigatório; titular e auxiliares precisam ser professores ativos da organização e da unidade da turma (`teacher_unit`); inativo ou de fora da unidade dá 400 no campo; auxiliares são opcionais.
- R5 (D6): `modalityId` precisa ser modalidade ativa da organização e oferecida pela unidade (`unit_modality.offered`); `ageRangeId`, se vier, é da organização; senão 400.
- R6 (RN-TUR-02): capacidade opcional de 1 a 200 e nunca bloqueia: editar para valor abaixo dos alunos ativos salva e o painel mostra o aviso.
- R7 (D7): `groupLink`, se vier, é URL `http` ou `https` com até 300 caracteres e sem espaços nas pontas; vazio vira nulo; outro esquema dá 400 no campo `groupLink`.
- R8 (RN-AGE-02, D5): quando o conjunto de horários muda, o servidor chama o ponto único `ClassScheduleChange` (hoje vazio, porque aulas só existem a partir de #49), que refará só as aulas futuras não alteradas à mão; o pedido `notifyClass` (padrão verdadeiro quando os horários mudam, ignorado quando não mudam) vai para a auditoria e a resposta traz `scheduleChanged`.
- R9 (permissão): `POST`, `PUT` e `GET /classes/options` exigem `CLASSES_MANAGE` no servidor: Coordenador (todas as unidades) e Administrador (unidades dele); Professor, Aluno e Responsável 403; sem sessão 401. Administrador criando em unidade fora do alcance 403; `PUT` em turma fora do alcance ou de outra organização 404. Entrada inválida dá 400 com o campo.
- R10 (isolamento): organização só da sessão; unidade, modalidade, faixa e professor de outra organização dão 400; nada de outra organização é alterado (filtro no código e RLS das tabelas de #46).
- R11 (auditoria): criar e editar gravam `class.created` e `class.updated` com ator, turma, unidade e nomes dos campos alterados (e `notifyClass` quando horários mudam), sem o link do grupo com valor.
- R12 (RN-TUR-06): nenhum campo, coluna ou texto novo guarda ou mostra presença ou frequência.
- R13 (D8): `GET /api/v1/classes/options` devolve, para o formulário, as unidades ativas do alcance do usuário, cada uma com `modalities` (ativas e oferecidas) e `teachers` (ativos da unidade), e as `ageRanges` da organização.
- R14 (tela): botão "Nova turma" na lista e botão Editar ativo no painel (desativado em turma desativada) abrem a janela (Modal) com todos os campos: unidade (só na criação; na edição aparece fixa), modalidade, faixa etária, horários por dia (caixa por dia com início e término), capacidade, titular, auxiliares, link do grupo, e a caixa "Avisar a turma" visível e marcada quando os horários foram alterados. Salvar fecha a janela e abre o painel (criação) ou o atualiza (edição); erro de campo aparece no campo; 409 de nome aparece no campo nome. Estados carregando e erro das opções (com "Tentar de novo"), sem permissão, 360 px, axe nos dois temas, textos em `pt-BR.json`.
- R15 (contrato e dados): `docs/openapi.yaml` e os handlers MSW refletem as três rotas; o seed continua válido (`SeedDataIT`).

## 3. Critérios de aceite

- CA1 (R1): dado Coordenador, quando faz `POST /classes` com todos os campos válidos (dois horários, dois auxiliares, faixa, capacidade, link), então 201 com `ClassDetail` (`status ACTIVE`, horários ordenados por dia) e a turma aparece em `GET /classes`.
- CA2 (R1, R6, R7): dado nome com 1 ou 81 caracteres (ou só espaços), `schedule` vazio, dia repetido ou fora de 1 a 7, término não maior que início, capacidade 0 ou 201, `groupLink` com `javascript:` ou sem esquema, ou sem `headTeacherId`, então 400 com o campo e nada é gravado; capacidade 1 e 200 e `groupLink` vazio salvam.
- CA3 (R3): dado turma ativa "Judô Infantil" na unidade, quando cria outra com o mesmo nome (também "judo infantil") na mesma unidade, então 409 no campo `name`; em outra unidade salva; igual a uma turma desativada salva; mesma modalidade e mesmo horário com outro nome salva; editar sem mudar o nome salva.
- CA4 (R4): dado titular inativo, de outra unidade, de outra organização ou inexistente, ou auxiliar nessas condições, ou auxiliar igual ao titular ou repetido, então 400 no campo; sem auxiliares salva.
- CA5 (R5): dado modalidade inativa, não oferecida pela unidade ou de outra organização, ou faixa etária de outra organização, então 400; sem faixa salva.
- CA6 (R2, R6): dado turma ativa, quando faz `PUT` com horários, auxiliares, titular, capacidade e link diferentes, então 200, o conjunto novo substitui o antigo, `unit` e `status` continuam; capacidade abaixo dos alunos ativos (valor injetado) salva e `overCapacity` fica verdadeiro; `PUT` em turma `INACTIVE` dá 409 e nada muda.
- CA7 (R8): dado `PUT` que muda o conjunto de horários, então `scheduleChanged` é verdadeiro, o ponto `ClassScheduleChange` é chamado uma vez com os horários antigo e novo (verificado com `@MockitoSpyBean`) e a auditoria traz `notifyClass`; `PUT` com os mesmos horários não chama o ponto e `scheduleChanged` é falso.
- CA8 (R9): dado Professor, Aluno e Responsável, então 403 em `POST`, `PUT` e `options`; sem sessão 401; Administrador de A criando em unidade de B 403 e em unidade dele 201; `PUT` de Administrador em turma de unidade fora do alcance 404; Coordenador em qualquer unidade 201/200.
- CA9 (R10): dado duas organizações, então `unitId`, `modalityId`, `ageRangeId`, titular e auxiliares de outra organização dão 400, `PUT` em turma de outra organização dá 404 e, com `ctcore_app`, nenhuma linha de outra organização muda nem é vista.
- CA10 (R11): dado criar e editar, então cada ação grava um registro de auditoria com os campos alterados e sem o valor do link.
- CA11 (R12): dado o esquema do banco, o corpo bruto das respostas e `pt-BR.json`, então nenhuma tabela, coluna, campo ou texto novo menciona presença ou frequência.
- CA12 (R13): dado Administrador com duas unidades de quatro, quando chama `options`, então só as duas dele, com modalidades oferecidas e ativas e professores ativos da unidade (inativo e professor de outra unidade não aparecem); Coordenador vê todas as unidades ativas; unidade desativada não aparece.
- CA13 (R14): dado a janela em 360 px, então sem rolagem horizontal e axe sem violações nos dois temas; em criação, unidade, modalidade, titular e ao menos um horário são exigidos e erro 400 aparece no campo; 409 de nome aparece no campo nome; em edição a unidade é fixa e os dados atuais vêm preenchidos; mudar um horário faz aparecer "Avisar a turma" marcada, voltar ao original a esconde; titular inativo atual não vem como escolha e o campo pede novo titular; opções em carregamento e em erro com "Tentar de novo"; "Nova turma" e Editar só para quem tem `CLASSES_MANAGE`, Editar desativado em turma desativada; salvar na criação leva a `/turmas/{id}` e na edição o painel mostra os dados novos.
- CA14 (R15): `OpenApiContractTest` e `ContractRoutesTest` passam com as três rotas; testes MSW cobrem as rotas no formato do servidor; `SeedDataIT` passa.
- CA15 (R1, R14): ponta a ponta `@critical` "Coordenador cria uma turma": abre Operação › Turmas, clica em "Nova turma", preenche nome, unidade, modalidade, titular e um horário, salva e vê o painel da turma nova.

## 4. Plano técnico

- Dados: sem migração (`training_class`, `_schedule` e `_assistant` de `V24` já têm o necessário; `name_key` por `Modalities.normalize`; o índice único parcial de RN-TUR-01 é a rede contra corrida, o código responde 409 antes). Sem seed novo.
- API (cookie e CSRF como as demais; entram em `docs/openapi.yaml` no mesmo PR): `POST /classes` (201 `ClassDetail`; 400, 401, 403, 409), `PUT /classes/{id}` (200; 400, 401, 403, 404, 409), `GET /classes/options` (200 `ClassOptions`; 401, 403). Esquema `ClassInput` e `ClassDetail.scheduleChanged` (aditivo, só nas respostas de escrita). `/options` declarado antes de `/{id}` no controller.
- Back-end, em `br.com.ctcore.trainingclass` (vistos: `TrainingClasses.java`, `TrainingClassController.java`, `TrainingClassDeactivation.java`): a criar `TrainingClassWrite.java` (validação, criar, editar, `OrganizationTransactions.execute`, trava da linha com `for update`, 409 de nome, `AuditService` com `AuditEntry`; permissão e alcance por `TrainingClasses.get`/`requireAccess`, como `TrainingClassDeactivation`), `ClassScheduleChange.java` (ponto único, hoje sem efeito; #49 o implementa) e as opções (classe pequena ou método em `TrainingClasses`; decisão da construção). A alterar: `TrainingClassController.java` (três rotas) e `TrainingClasses.java` (visibilidade de `requireAccess`, se preciso). Reusa `teacher_unit`, `unit_modality`, `age_range`, `ApiValidationException`, `ErrorCode.CONFLICT`, `Modalities.normalize`.
- Interface: a criar `frontend/src/features/classes/ClassFormDialog.tsx` (e teste). A alterar `ClassPanelPage.tsx` (Editar ativo), `ClassesPage.tsx` (botão "Nova turma"), `useClasses.ts` (opções, criar, editar), `mocks/handlers.ts` (+ `classes.msw.test.ts`), `i18n/pt-BR.json`. Reusa `Modal`, campos de `components/fields` e o padrão de `features/teachers/TeacherFormDialog.tsx`; horários por dia com `input type="time"` se não houver seletor pronto.
- Segurança: organização, perfil e alcance vêm do principal; `CLASSES_MANAGE` em toda rota; parâmetros ligados; link só `http`/`https`; auditoria sem o link e sem dado pessoal; professores nas opções só com nome e id.
- Riscos: (1) `ClassScheduleChange` deve receber horário antigo e novo para #49 refazer só as aulas futuras sem alteração manual (RN-AGE-02); o contrato é fixado aqui. (2) Corrida de nome: a violação do índice único vira 409. (3) Titular que ficou inativo depois (RN-PRO-05): a edição só passa com ativo novo.

## 5. Tarefas

Uma parte só: servidor (T1, T2), contrato (T3), tela (T4, T5).

- [x] T1: `TrainingClassWrite` (criar e editar com R1 a R8, R10, R11), `ClassScheduleChange`, rotas `POST` e `PUT` e testes de integração (`TrainingClassWriteIT`: campos, nome único, titular e auxiliares, modalidade e faixa, capacidade, link, horário alterado, desativada 409, auditoria, perfis, alcance, isolamento e RLS, sem presença) — cobre CA1 a CA11 — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test=TrainingClassWriteIT`.
- [x] T2: rota `GET /classes/options` com testes (alcance do Administrador, só ativos, oferecidos, perfis) — cobre CA12 — prova: `verify -Dit.test=TrainingClassWriteIT`.
- [x] T3: OpenAPI (três operações, `ClassInput`, `ClassOptions`, `scheduleChanged`) e handlers MSW com teste — cobre CA14 — prova: `./backend/mvnw -f backend/pom.xml test -Dtest='OpenApiContractTest,ContractRoutesTest'`, `verify -Dit.test=SeedDataIT` e `npx vitest run src/mocks` em `frontend/`.
- [x] T4: `ClassFormDialog`, `useClasses`, botões "Nova turma" e Editar, textos pt-BR, quatro estados, 360 px e axe — cobre CA13 — prova: `npx vitest run src/features/classes`.
- [x] T5: e2e `@critical` "Coordenador cria uma turma" e regressão completa (a regressão não cobre critério: confere cobertura e os 100% nos pacotes exigidos) — cobre CA15 — prova: `./backend/mvnw -f backend/pom.xml verify` e, em `frontend/`, `npm run lint && npm run typecheck && npm run build && npm run test:cov && npm run e2e`.

## 6. Decisões

- D1: criar turma entra neste item porque a janela do card serve a criar e editar, como em #44, e hoje só o seed cria turmas. Alternativa: só editar; descartada porque a lista ficaria sem como cadastrar.
- D2: a unidade não muda depois de criada; trocar mexeria em nome único, professores e aulas futuras. Alternativa: permitir; descartada por risco e porque o card não pede.
- D3 (muda o comportamento): turma desativada não é editável (409) e o botão Editar fica desativado; como não há reativar (#47/D4) e o nome só é único entre ativas, editar desativada abriria conflito de nome sem saída. Alternativa: permitir; descartada.
- D4 (muda o comportamento): RN-PRO-05 neste item = professor inativo não aparece nas escolhas e o servidor o recusa (400); titular inativo precisa ser trocado ao editar. A lista "Precisa de atenção" ("turma sem professor ativo") é do painel de início e fica fora. Titular e auxiliar precisam ser da unidade da turma (`teacher_unit`); não se exige que ensinem a modalidade (a spec não pede). Suposição: se a gestão quiser exigir a modalidade, é regra nova.
- D5: RN-AGE-02 não tem aulas para refazer até #49 e o comunicado é da Sprint 09. Aqui fica o contrato: `ClassScheduleChange` recebe horários antigo e novo e é chamado só quando o conjunto muda; "Avisar a turma" vira o campo `notifyClass`, gravado na auditoria, e a resposta traz `scheduleChanged`, sem enviar nada. Alternativa: esconder a caixa; descartada porque o card a pede.
- D6 (muda o comportamento): a modalidade da turma precisa estar oferecida pela unidade (RF-MOD-03). Alternativa: qualquer modalidade ativa; descartada por contrariar a oferta da unidade.
- D7: validar o link do grupo na gravação (http/https, até 300) fecha o que #47/D7 deixou para #48.
- D8: opções do formulário numa rota própria `GET /classes/options`, para não depender de permissões de outras telas (modalidades, professores, faixas), que o Administrador pode não ter. Alternativa: reusar as listas existentes; descartada pelo risco de 403.
- D9: o canvas `TurmaEditar.dc.html` não está acessível; a tela segue os padrões do projeto e o dono confere o layout no PR (como #46/D8).

## Construção

- T1 — arquivos: `backend/.../trainingclass/TrainingClassWrite.java` (novo), `ClassScheduleChange.java` (novo), `TrainingClassController.java` (POST, PUT), `TrainingClasses.java` (`requireAccess` visível no pacote; `Detail.scheduleChanged` só nas respostas de escrita), `backend/src/test/.../TrainingClassWriteIT.java` (novo) — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test=TrainingClassWriteIT` → 10 testes, 0 falhas (CA1 a CA11).
- T2 — arquivos: `TrainingClassWrite.options` e rota `GET /classes/options` no controller; testes no mesmo IT (`optionsScope`, permissões em `permissions`) — prova: mesma execução → passou (CA12).
- T3 — arquivos: `docs/openapi.yaml` (POST /classes, GET /classes/options, PUT /classes/{id}, `ClassInput`, `ClassOptions`, `ClassDetail.scheduleChanged`), `frontend/src/mocks/handlers.ts`, `frontend/src/mocks/classes.msw.test.ts` — prova: `test -Dtest='OpenApiContractTest,ContractRoutesTest'` → 7 testes, 0 falhas; `npx vitest run src/mocks` → 82 passaram; `SeedDataIT` passou dentro do `verify` completo.
- T4 — arquivos: `frontend/src/features/classes/ClassFormDialog.tsx` e `.test.tsx` (novos), `useClasses.ts`, `ClassesPage.tsx` ("Nova turma"), `ClassPanelPage.tsx` (Editar ativo), `ClassPanelPage.test.tsx` (Editar saiu da lista de desativados), `i18n/pt-BR.json` (`classes.newButton`, `classes.form.*`) — prova: `npx vitest run src/features/classes` → 40 passaram (inclui axe claro e escuro a 360 px).
- T5 — arquivos: `frontend/e2e/turmas.spec.ts` (`@critical Coordenador cria uma turma`) — prova: `npm run e2e` → 26 passaram.

Regressão: `./backend/mvnw -f backend/pom.xml verify` → 324 testes, 0 falhas, BUILD SUCCESS (JaCoCo sem violação); em `frontend/`: `npm run lint`, `npm run typecheck`, `npm run build` sem erro; `npm run test:cov` → 70 arquivos, 592 testes passaram, limites de cobertura atendidos; `npm run e2e` → 26 passaram. Sem piora em relação à linha de base.
Desvios: opções do formulário ficaram em `TrainingClassWrite.options` (o plano deixava a escolha para a construção); `scheduleChanged` é componente opcional de `Detail` (omitido no JSON do GET por `NON_NULL`). Titular que continua ativo mas já não oferece a modalidade não é validado (D4). Sem docker novo: nenhuma imagem criada ou baixada.
Não é do item: nenhum.

## Verificação

### Rodada 1 — OK — 2026-10-19, estado 8a9f4a7ed7945baaf63e116c1a5ebb00b9311c95

| Critério | Status | Evidência |
|---|---|---|
| CA1 a CA11 | PASSOU | `TrainingClassWriteIT` 10 testes, 0 falhas no `verify`; leitura de `TrainingClassWrite.java` confere validação, nome único, titular/auxiliares, link, auditoria sem o link |
| CA12 | PASSOU | `optionsScope` no mesmo IT |
| CA13 | PASSOU | `ClassFormDialog.test.tsx` (9 casos: criação, edição, notifyClass, titular inativo, opções em erro/403, permissão, Editar desativado); axe 360 px dentro da suíte (592 passaram) |
| CA14 | PASSOU | `verify` completo inclui `OpenApiContractTest`, `ContractRoutesTest`, `SeedDataIT`; testes MSW passaram |
| CA15 | PASSOU | `npm run e2e`: 26 passaram, inclui "@critical Coordenador cria uma turma" |

Comandos: `./backend/mvnw -f backend/pom.xml verify` → 324 testes, 0 falhas, BUILD SUCCESS; em `frontend/`: `npm run lint`, `typecheck`, `build` sem erro; `npm run test:cov` → 70 arquivos, 592 testes, cobertura 96,87% de linhas; `npm run e2e` → 26 passaram.
Achados:
- sugestão, código, `TrainingClassWrite.update` — a validação de corpo (400) roda antes do 404/409; aceitável, sem impacto.

## Entrega

Janela de turma (RF-TUR-03): criar (`POST /classes`) e editar (`PUT /classes/{id}`) turma, com opções do formulário em `GET /classes/options`, validações RN-TUR-01/02/05 e RN-PRO-05, auditoria sem o link do grupo, e `ClassScheduleChange` como contrato para RN-AGE-02 (`notifyClass` gravado na auditoria, `scheduleChanged` na resposta; sem envio até a Sprint 09). Front: `ClassFormDialog`, botão "Nova turma" e Editar ativo, textos pt-BR, quatro estados, 360 px e axe.

Verificação: `./backend/mvnw -f backend/pom.xml verify` (324 testes, JaCoCo sem violação); em `frontend/`, lint, typecheck, build, `test:cov` (592 testes) e `e2e` (26) verdes.

Decisões: turma desativada não é editável (409); unidade não muda; modalidade precisa ser oferecida pela unidade; layout do canvas não estava acessível (D9), o dono confere no PR.

## Revisão pelo dono (20/10/2026, depois da rodada 2 da Sprint 05)

- D4 revista: titular e auxiliar **têm de ensinar a modalidade da turma** (RN-TUR-05, RN-PRO-09). A janela só oferece professores ativos da unidade que ensinam a modalidade escolhida e limpa a escolha ao trocar de modalidade; o servidor recusa com 400 em `headTeacherId` ou `assistantIds`; turma com titular que não ensina exige troca ao editar. A rota `GET /classes/options` passou a trazer `modalityIds` por professor. Implementado no PR `ajustes/decisoes-sprint-05`.
