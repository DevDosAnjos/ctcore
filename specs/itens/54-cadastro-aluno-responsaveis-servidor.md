# 54 — Cadastro de aluno e responsáveis no servidor

Origem: issue #54 (Sprint 06); `docs/especificacao/08-alunos-e-responsaveis.md` (RN-ALU-01 a 11, RN-RSP-01 a 07, "Dados"), `03-auditoria-e-lgpd.md` (RN-LGP-02, RN-LGP-03), `02-contas-e-acesso.md` (RN-ACE-05 a 07, linha "Alunos: cadastrar, editar, matricular, encerrar" da tabela de permissões) e `13-comunicacao-e-notificacoes.md` (RN-COM-14). Branch: `item/54-cadastro-aluno-responsaveis-servidor`. Parte de `Permission.STUDENT_MANAGE`, do convite de `InviteService` (#18), do serviço de e-mail (#19), da foto no padrão de `TeacherWrite` (#44) e do agendador (#4).

## 1. Objetivo

O servidor passa a guardar o aluno (cadastro da organização) e seus responsáveis: cria o aluno com dados validados e regras de duplicidade, liga e desliga responsáveis (até dois por aluno, um financeiro, convite próprio), garante que o menor nunca fica sem responsável, avisa quando o aluno faz 18 anos e marca e-mail com problema. Entrega também a leitura mínima que as telas seguintes precisam (detalhe, busca por CPF, cartões do portal). Quem usa: gestão (Coordenador e Administrador) para cadastrar; responsável no portal para ver os alunos dele.

Fora do escopo: lista e filtros de alunos (#58), selos (#59), ficha em painel (#60), editar dados pessoais e foto (#61), aniversariantes (#62), matrícula, modalidades e convite do aluno adulto (#63, #67), pedidos do portal e cadastro feito pelo responsável (#86), anonimizar e baixar dados (RF-ALU-05, sprint própria), notificação do sino e "Precisa de atenção" (#84 e Início). As telas (`AlunoResponsaveis`, `MatriculaAluno`, `PortalResponsavel`) vivem na ficha, na matrícula e no portal (D1). Notas de versão: Sprint 15.

## 2. Requisitos

- R1 (RN-ALU-01, RN-ALU-02, RN-ALU-03): `POST /api/v1/students` cria o aluno na organização da sessão com `name` (2 a 80), `birthDate` (passado), endereço completo (`postalCode`, `street`, `number`, `complement` opcional, `district`, `city`, `state`), `cpf`, `email` e `phone` (celular com DDD) e, opcionais, `gender` (`MALE`, `FEMALE`, `OTHER`, `PREFER_NOT_TO_SAY`), `referral` (`INSTAGRAM`, `REFERRAL`, `DIRECTORY`, `WALK_BY`, `OTHER`) e contato de emergência (`emergencyName`, `emergencyRelationship`, `emergencyPhone`, os três juntos ou nenhum). CPF com dígitos verificadores, guardado só com dígitos; responde 201 com `StudentDetail`.
- R2 (RN-ALU-01): aluno com 18 anos ou mais informa CPF, e-mail e telefone próprios (obrigatórios). Aluno menor: CPF, e-mail e telefone dele são opcionais e os do responsável financeiro são obrigatórios (R6).
- R3 (RN-ALU-04, RN-ALU-06): CPF já cadastrado na organização recusa com 409 `STUDENT_CPF_EXISTS` trazendo só `id`, `name` e `photoUrl` do existente. Sem CPF, mesmo nome (comparação sem acento e caixa) e mesmo nascimento dão 409 `STUDENT_POSSIBLE_DUPLICATE` com os mesmos três campos, e o pedido repetido com `confirmDuplicate = true` cria. `GET /api/v1/students/lookup?cpf=` devolve `{id, name, photoUrl}` do aluno da organização com esse CPF (de qualquer unidade) ou 404; nenhum outro dado.
- R4 (RN-RSP-01, RN-RSP-02): aluno menor de 18 anos só é gravado com ao menos um responsável (`guardians` no `POST`); nunca fica com mais de dois; exatamente um é financeiro enquanto houver responsável; com um só, ele é o financeiro.
- R5 (RN-RSP-03, RN-RSP-06): responsável é uma pessoa da organização com conta (`name`, `cpf`, `email`, `phone`). O mesmo responsável (mesmo e-mail) liga-se a vários alunos, inclusive de unidades diferentes, sem duplicar cadastro nem conta; CPF diferente para o mesmo e-mail, ou CPF de outro e-mail, dá 409 `GUARDIAN_CONFLICT`. Cada responsável novo recebe o próprio convite (conta `INVITED` e vínculo `GUARDIAN` pendente, convite de 7 dias do #18); o responsável cuja conta já existe só ganha o vínculo e aceita com a senha que tem (RN-ACE-07).
- R6 (RN-RSP-02, RN-RSP-05): `GET /students/{id}` traz os responsáveis (`guardianId`, nome, e-mail, telefone, `financial`, situação do acesso `PENDING`/`ACTIVE`). `POST /students/{id}/guardians` liga responsável (corpo como em R5 mais `financial` e `reason`); `PUT /students/{id}/guardians/{guardianId}` troca quem é o financeiro; `POST /students/{id}/guardians/{guardianId}/unlink` desliga com `reason` obrigatório (3 a 200 caracteres). Ligar e desligar só pela gestão; o motivo vai para a auditoria.
- R7 (RN-RSP-01, D4): desligar o último responsável de aluno menor dá 409 `MINOR_NEEDS_GUARDIAN`; desligar o financeiro com outro presente passa o financeiro ao que fica; ligar o terceiro dá 409 `GUARDIAN_LIMIT`. Aluno adulto pode ter 0, 1 ou 2.
- R8 (RN-RSP-05): quem foi desligado recebe e-mail neutro (sem nome do aluno nem motivo); sem aluno nenhum ligado na organização, o vínculo `GUARDIAN` termina e o convite pendente é cancelado. Reenvio do convite: `POST /students/{id}/guardians/{guardianId}/invite` (limite de 3 por dia da RN-ACE-13; 409 se o acesso já está ativo).
- R9 (RN-RSP-07, RN-ACE-06): tarefa diária avisa quando o aluno, cadastrado menor, completa 18 anos: marca `majorityNoticeAt` (uma só vez), manda e-mail aos responsáveis e grava auditoria do sistema; a marca é o que o sino (#84) e o "Precisa de atenção" lerão. Aluno cadastrado já adulto nasce sem aviso. O aviso não muda vínculo nem financeiro: a decisão é da academia.
- R10 (RN-RSP-03, RN-RSP-04, RN-LGP-03): `GET /api/v1/portal/students` devolve os cartões de quem tem sessão do portal: um por aluno ligado ao responsável (em qualquer unidade da organização) e, se a mesma conta é a conta do aluno, o cartão dele com `self = true` ("Você"). Cartão traz só `id`, `name`, `photoUrl`, `self`. Menor nunca aparece para outra conta; sessão de gestão recebe 403.
- R11 (RN-LGP-02): o cadastro não tem campo de saúde nem de documento médico; o contato de emergência é só nome, parentesco e telefone, e campo extra no corpo é ignorado e não gravado.
- R12 (RN-ALU-08): aluno e seus vínculos nunca são excluídos: não há rota de exclusão e o papel operacional do banco não tem `DELETE` em `student` nem em `student_guardian` (desligar guarda o histórico na auditoria; o vínculo sai de `student_guardian`, o registro permanece na auditoria).
- R13 (RN-ALU-10): `EmailProblems.mark(organizationId, email)` marca o e-mail de aluno e de responsável da organização como "com problema" (`emailProblem` em `StudentDetail` e no responsável), grava auditoria e faz os envios do aluno e do responsável (aviso de R9 e de R8) pararem para esse endereço; `clear` desmarca (usado por #61 ao corrigir). A entrada real de devolução (bounce) vem com o e-mail real (Sprint 22), pela porta; aqui só o estado e o bloqueio.
- R14 (permissão e escopo): toda rota de gestão exige `STUDENT_MANAGE` no servidor: Coordenador e Administrador; Professor, Aluno e Responsável 403; sem sessão 401. Administrador alcança o aluno cuja unidade de cadastro (`registeredUnitId`, R15) está no alcance dele; fora disso 404, exceto R3 (só nome e foto). Organização sempre a da sessão.
- R15 (D2, muda o comportamento): `registeredUnitId` obrigatório no `POST`, unidade ativa da organização: Coordenador escolhe qualquer uma, Administrador só as dele (senão 403). É o que dá escopo ao aluno até existir matrícula (#63, que acrescenta o escopo por modalidade).
- R16 (auditoria): criar aluno, ligar, trocar financeiro, desligar, reenviar convite, aviso dos 18 anos e marcar ou limpar e-mail com problema gravam auditoria (`student.created`, `student.guardian_linked`, `student.guardian_financial_changed`, `student.guardian_unlinked`, `student.guardian_invite_resent`, `student.majority_notice`, `student.email_problem_marked`, `student.email_problem_cleared`) com ator e ids; CPF, e-mail, telefone e endereço não vão com valor (só "informado").
- R17 (isolamento): toda tabela nova com `organization_id` e RLS; corpo e URL nunca definem a organização; aluno, responsável ou unidade de outra organização dá 404 (`registeredUnitId` de outra organização, 400); CPF igual em outra organização é permitido.
- R18 (contrato e dados): `docs/openapi.yaml` e os handlers MSW refletem as rotas e os campos; seed com alunos fictícios `infra/seed/12-alunos.sql`, idempotente, validado pelo `SeedDataIT`.

## 3. Critérios de aceite

- CA1 (R1): dado Coordenador, quando faz `POST /students` com todos os campos válidos de um adulto, então 201 com `StudentDetail` (CPF e telefone só com dígitos, `registeredUnitId`, sem responsáveis) e o `GET /students/{id}` devolve o mesmo.
- CA2 (R1, R2): dado nome com 1 ou 81 caracteres, nascimento no futuro, CPF com dígito errado ou repetido (`111.111.111-11`), celular sem DDD, UF inválida, ou adulto sem CPF, e-mail ou telefone, quando cria, então 400 com o campo e nada é gravado; emergência só com o nome também dá 400.
- CA3 (R1, R11): dado corpo com `bloodType` ou `medicalCertificate`, quando cria, então 201 e nenhuma coluna ou resposta guarda o campo; a tabela `student` não tem coluna de saúde (teste de esquema).
- CA4 (R3): dado aluno com CPF X, quando outro cadastro usa X (também por Administrador de outra unidade), então 409 `STUDENT_CPF_EXISTS` com só `id`, `name`, `photoUrl`; o mesmo CPF em outra organização cria.
- CA5 (R3): dado menor sem CPF com nome e nascimento iguais a um existente (nome com outra caixa ou sem acento), quando cria, então 409 `STUDENT_POSSIBLE_DUPLICATE`; com `confirmDuplicate = true` cria; nascimento diferente cria sem aviso.
- CA6 (R3): dado `GET /students/lookup?cpf=` com CPF existente (de outra unidade), então 200 com só `id`, `name`, `photoUrl`; CPF inexistente, de outra organização ou inválido, 404 ou 400.
- CA7 (R4, R6): dado menor, quando cria sem `guardians`, então 400 `VALIDATION_ERROR` com o campo `guardians` (D13) e nada é gravado (nem conta nem vínculo); com um responsável, ele é financeiro; com dois, só um com `financial = true` (zero ou dois financeiros dá 400); três dá 400.
- CA8 (R2, R4): dado menor, quando os dados do responsável financeiro (CPF, e-mail, celular) faltam ou são inválidos, então 400 com o campo; CPF, e-mail e telefone do menor ausentes salvam.
- CA9 (R5): dado responsável novo, quando o aluno é criado, então nascem a conta `INVITED`, o vínculo `GUARDIAN` `PENDING` e um convite, o e-mail de convite sai para o endereço dele, e dois responsáveis recebem dois convites distintos.
- CA10 (R5): dado responsável já cadastrado na organização com um filho na unidade A, quando é ligado a um aluno da unidade B pelo mesmo e-mail, então a mesma conta e o mesmo cadastro de responsável servem aos dois e nenhum convite novo sai se o acesso já está ativo; dado e-mail igual com CPF diferente, ou CPF igual com e-mail diferente, então 409 `GUARDIAN_CONFLICT`.
- CA11 (R5): dado e-mail de responsável que já tem conta ativa (por exemplo, de Aluno em outra organização), quando é ligado, então só o vínculo novo nasce e a conta não muda.
- CA12 (R6, R16): dado Administrador, quando liga um responsável sem `reason` ou com `reason` de 2 ou 201 caracteres, então 400; com motivo válido, 200 e a auditoria traz o motivo; `PUT` com `financial = true` passa o financeiro e `financial = false` sozinho dá 400.
- CA13 (R7): dado menor com um responsável, quando desliga esse, então 409 `MINOR_NEEDS_GUARDIAN` e o vínculo continua; com dois, desligar o financeiro deixa o outro financeiro; ligar um terceiro dá 409 `GUARDIAN_LIMIT`; adulto desliga o último sem erro.
- CA14 (R8): dado desligamento, então o e-mail neutro sai para o desligado sem o nome do aluno nem o motivo; se ele não tem mais nenhum aluno na organização o vínculo `GUARDIAN` fica `ENDED` e o convite pendente cancelado; se tem outro aluno, o vínculo continua `ACTIVE`/`PENDING`.
- CA15 (R8): dado responsável com acesso pendente, quando a gestão reenvia, então novo convite e o anterior cancelado; o quarto reenvio no dia dá 429; com acesso ativo dá 409.
- CA16 (R9): dado aluno cadastrado com 17 anos e responsáveis, quando a tarefa roda no dia do 18º aniversário, então `majorityNoticeAt` é gravado, cada responsável recebe um e-mail e há auditoria do sistema; uma segunda execução não repete; aluno cadastrado já adulto não é avisado; o vínculo e o financeiro continuam como estavam; organização com falha não impede as demais.
- CA17 (R10): dado responsável com dois filhos (unidades diferentes) e conta que também é aluna da organização, quando chama `GET /portal/students`, então 3 cartões, o próprio com `self = true`, só com `id`, `name`, `photoUrl`, `self`; outro responsável não vê esses alunos; sessão de gestão recebe 403; sem sessão 401.
- CA18 (R12): dado o papel operacional do banco (`ctcore_app`), quando tenta `delete from student` ou `student_guardian`, então erro de permissão (teste com banco real); nenhuma rota `DELETE /students/{id}` existe.
- CA19 (R13): dado e-mail marcado com `EmailProblems.mark`, então `StudentDetail.emailProblem` e o do responsável ficam `true`, a auditoria registra, e o aviso de 18 anos e o e-mail neutro para esse endereço não são enviados; `clear` volta ao normal.
- CA20 (R14): dado Professor, Aluno e Responsável em todas as rotas de gestão, então 403; sem sessão 401; Administrador sem alcance sobre a unidade de cadastro 404 em `GET`, ligar, trocar e desligar; `POST` com `registeredUnitId` fora do alcance dele 403; Coordenador com qualquer unidade ativa 201; unidade inativa ou inexistente 400.
- CA21 (R15, R17): dado duas organizações, então aluno, responsável e unidade de outra organização dão 404 (ou 400 em `registeredUnitId`), nada de outra organização é alterado e a RLS é conferida lendo com `ctcore_app` sem contexto (zero linhas) e com a outra organização (zero linhas).
- CA22 (R16): dado cada ação de R16, então um registro de auditoria com ator, ids e motivo e sem CPF, e-mail, telefone ou endereço com valor.
- CA23 (R18): `OpenApiContractTest` e `ContractRoutesTest` passam com as rotas; testes MSW cobrem as rotas no formato do servidor; `SeedDataIT` passa com `12-alunos.sql`, e rodar o seed duas vezes não duplica.

## 4. Plano técnico

- **Dados.** Migração a criar `V29__student_guardian.sql` (reversão manual no cabeçalho: drop das tabelas e funções novas; sem dado existente a perder):
  - `student`: `id`, `organization_id` (FK), `registered_unit_id` (FK composta `(organization_id, unit_id)`), `name`, `name_key` (`Modalities.normalize`), `birth_date`, `cpf` (11 dígitos, nulo permitido), `email`, `phone`, `postal_code`, `street`, `number`, `complement`, `district`, `city`, `state`, `photo` (nulo aqui; gravado por #61), `gender` e `referral` (checks de lista), `emergency_name`, `emergency_relationship`, `emergency_phone` (check: três ou nenhum), `account_id` (nulo; ligado por #63), `email_problem_at`, `majority_notice_at`, `created_at`, `unique (organization_id, id)`. Índice único parcial `(organization_id, cpf) where cpf is not null`; índices `(organization_id, name_key, id)` e `(organization_id, name_key, birth_date)`.
  - `guardian`: `id`, `organization_id`, `account_id` (FK `account`), `name`, `cpf` (obrigatório), `phone`, `email_problem_at`, `created_at`; únicos `(organization_id, account_id)` e `(organization_id, cpf)`; `unique (organization_id, id)`. O e-mail fica em `account.email`.
  - `student_guardian`: `organization_id`, `student_id`, `guardian_id` (FKs compostas), `financial` boolean, `created_at`; PK `(student_id, guardian_id)`; índice único parcial `(student_id) where financial`; gatilho que recusa o terceiro vínculo do aluno (`23514`). Escrita serializada com `select ... for update` na linha do aluno.
  - RLS `organization_scope` forçada nas três (modelo de `teacher`); `revoke delete` em `student` e `student_guardian` dos papéis de execução (modelo do bloco de `V19__modality.sql`; atenção ao papel `ctcore_app` de `prepare-runtime-db.sh`). Sem dado de saúde por construção (RN-LGP-02). Seed `infra/seed/12-alunos.sql` (alunos adultos e menores, responsáveis, um com dois filhos), `on conflict do nothing`.
- **API** (cookie e CSRF como as demais; rotas no `docs/openapi.yaml` no mesmo PR): `POST /students` (201 `StudentDetail`; 400, 401, 403, 409), `GET /students/{id}` (200; 401, 403, 404), `GET /students/lookup` (200 `StudentRef`; 400, 401, 403, 404), `POST /students/{id}/guardians` (200 `StudentDetail`; 400, 401, 403, 404, 409), `PUT /students/{id}/guardians/{guardianId}`, `POST .../unlink`, `POST .../invite` (429), `GET /portal/students` (200 lista de `PortalStudentCard`; 401, 403). Códigos novos de erro em `ErrorCode`: `STUDENT_CPF_EXISTS`, `STUDENT_POSSIBLE_DUPLICATE`, `MINOR_NEEDS_GUARDIAN`, `GUARDIAN_LIMIT`, `GUARDIAN_CONFLICT` (409). Resposta de CPF repetido traz o existente (RN-ALU-04), só nome e foto.
- **Back-end.** Pacote novo `br.com.ctcore.student` (a criar): `StudentController.java`, `PortalStudentController.java`, `Students.java` (leitura, escopo por `registered_unit_id`, `StudentDetail`), `StudentRegistration.java` (R1 a R4, R11, R15; validação com `ApiValidationException`; `HolderDocument.normalize` para CPF e `NewUnit.phone`/`NewUnit.email` para telefone e e-mail, como em #44), `StudentGuardians.java` (R5 a R8; ligar, financeiro, desligar, reenvio; `OrganizationTransactions.execute`, `AuditService`), `MajorityNoticeJob.java` (modelo de `LessonGenerationJob`: `@Scheduled` diário, `@SchedulerLock`, `active_organization_ids()`, `provision` por organização, e-mail depois do commit), `EmailProblems.java` (R13), `PortalStudents.java` (R10). A alterar: `InviteService` (métodos `inviteGuardian` e `resendGuardian` para vínculo `GUARDIAN`, com a autorização feita por `STUDENT_MANAGE` no chamador, e `InviteRules.allowed` aceitando o convite de responsável pela gestão), `EmailTemplate` e `messages.properties` (`GUARDIAN_UNLINKED` neutro, `STUDENT_MAJORITY`), `ErrorCode`. Permissão por `Permissions.require(who, STUDENT_MANAGE, unitId)`; sessão de portal reconhecida por `Role.STUDENT`/`GUARDIAN` na rota `/portal` (como `OrganizationScopeFilter` já distingue portal).
- **Interface.** Não muda (D1): as telas nascem na ficha (#60), na matrícula (#67) e no portal (#89). Entra só `frontend/src/mocks/handlers.ts` (+ `students.msw.test.ts`, a criar) com as rotas e os erros 409 no formato do servidor, e tipos em `frontend/src/features/students/types.ts` (a criar) usados pelos MSW. Textos de tela: não se aplica.
- **Segurança.** Organização, perfil e alcance vêm do principal; `STUDENT_MANAGE` em toda rota de gestão; consulta por CPF e duplicidade expõem só nome e foto (RN-ALU-06); CPF, e-mail e telefone fora de log e de auditoria (como `TeacherWrite`); parâmetros ligados; foto fora do item; e-mail neutro não revela aluno nem motivo; resposta de recusa por alcance é 404 e não revela existência; LGPD: dado mínimo, menor só acessível pelo responsável no portal (R10), nenhum dado de saúde, nada excluído (anonimização é de item próprio, RF-ALU-05).
- **Riscos.** (1) Corrida: dois pedidos criando o mesmo CPF ou o terceiro responsável; o índice único parcial e o gatilho vencem no banco, e o serviço trata a violação como 409. (2) Conta nova criada e a transação falhar: convite e e-mail só depois do commit. (3) `revoke delete` pode não pegar o papel certo; o CA18 prova com `ctcore_app`. (4) A tarefa diária roda sem sessão: fica em `provision` por organização, como a de aulas. (5) O escopo por `registered_unit_id` é provisório até #63.

## 5. Tarefas

Três partes, cada uma construível numa sessão: dados e cadastro (T1 a T3), responsáveis e convites (T4 a T6), avisos, portal e contrato (T7 a T10).

- [x] T1: migração `V29__student_guardian.sql` (tabelas, índices, gatilho, RLS, `revoke delete`) e seed `12-alunos.sql` — cobre CA3, CA18, CA21 (parte do esquema e da RLS) — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test='StudentSchemaIT,SeedDataIT' -Dtest=NoSuchTest -Dsurefire.failIfNoSpecifiedTests=false -Djacoco.skip=true`.
- [x] T2: `StudentRegistration` e `Students` (criar adulto; o menor só é aceito a partir de T4, que acrescenta os responsáveis, validações, duplicidade, lookup, detalhe, escopo por unidade, auditoria) e `StudentController` — cobre CA1 a CA6, CA8, CA20, CA21, CA22 — prova: `verify -Dit.test=StudentRegistrationIT` (mesmos flags).
- [x] T3: `InviteService.inviteGuardian`/`resendGuardian`, `InviteRules`, e-mails e `messages.properties` — não cobre critério por si: prepara o convite de responsável de T4 — prova: `./backend/mvnw -f backend/pom.xml test -Dtest='InviteRulesTest'` e `verify -Dit.test=InviteServiceIT` (mesmos flags).
- [x] T4: `StudentGuardians` dentro do `POST /students` (menor exige responsável, financeiro, limite, convites, conta existente, conflito) — cobre CA7 a CA11 — prova: `verify -Dit.test=StudentGuardiansIT`.
- [x] T5: ligar, trocar financeiro, desligar e reenviar convite (rotas, motivo, e-mail neutro, vínculo que termina, auditoria) — cobre CA12 a CA15, CA20, CA22 — prova: `verify -Dit.test=StudentGuardiansIT`.
- [x] T6: `EmailProblems` (marcar, limpar, bloquear envios) — cobre CA19 — prova: `verify -Dit.test=EmailProblemsIT`.
- [x] T7: `MajorityNoticeJob` — cobre CA16 — prova: `verify -Dit.test=MajorityNoticeJobIT` (relógio fixo).
- [x] T8: `PortalStudents` e `PortalStudentController` — cobre CA17 — prova: `verify -Dit.test=PortalStudentsIT`.
- [x] T9: OpenAPI (rotas, esquemas, erros), handlers MSW e teste — cobre CA23 — prova: `./backend/mvnw -f backend/pom.xml test -Dtest='OpenApiContractTest,ContractRoutesTest'`, `verify -Dit.test=SeedDataIT` e `npx vitest run src/mocks` em `frontend/`.
- [x] T10: regressão completa (cobertura e 100% dos pacotes exigidos) — não cobre critério: confirma que nada quebrou — prova: `./backend/mvnw -f backend/pom.xml verify` e, em `frontend/`, `npm run lint && npm run typecheck && npm run build && npm run test:cov && npm run e2e`.

## 6. Decisões

- D1: a issue é "no servidor": o item entrega rotas, regras, OpenAPI e MSW; as telas listadas no canvas pertencem a itens que já as hospedam (ficha #60, matrícula #67, portal #89). Alternativa: construir as três telas aqui; descartada por repetir #60, #67 e #89 e fugir do título da issue.
- D2 (muda o comportamento): o aluno guarda a unidade de cadastro (`registered_unit_id`) e é ela que dá alcance ao Administrador até a matrícula existir; depois de #63 vale também a modalidade na unidade (RN-ALU-07). Coordenador alcança todos. Alternativa: aluno sem unidade visível a todo Administrador; descartada por violar "Administrador só altera o que é da sua unidade".
- D3 (muda o comportamento): CPF repetido e "mesmo nome e nascimento" mostram ao cadastrante só nome e foto do existente, mesmo que seja de outra unidade, como a RN-ALU-06 pede para o CPF. Aviso de nome e nascimento só vale quando o pedido não tem CPF (texto da RN-ALU-04).
- D4 (muda o comportamento): ao desligar o responsável financeiro havendo outro, o que fica vira financeiro; sem isso o aluno ficaria sem cobrança. Trocar o financeiro é `PUT` com `financial = true`; `false` sozinho não existe. Alternativa: exigir escolha explícita antes de desligar; descartada por travar o fluxo.
- D5 (muda o comportamento): o convite do responsável sai na hora em que ele é ligado (RN-RSP-06), não só ao concluir a matrícula (RN-ACE-05 fala do convite do portal do aluno). Alternativa: convite só na matrícula (#63); descartada porque o responsável cadastrado na etapa 1 ficaria sem acesso até lá e o vínculo exige conta. O convite do aluno adulto continua em #63.
- D6: responsável tem tabela própria `guardian` por organização (CPF e telefone, que `account` não guarda) e o e-mail fica na conta; o vínculo de acesso continua em `account_link` com papel `GUARDIAN`. Alternativa: guardar CPF em `account`; descartada porque `account` é global e o CPF é dado da relação com a organização.
- D7: RN-RSP-07 entrega a marca `majority_notice_at`, o e-mail aos responsáveis e a auditoria; o aviso ao Administrador aparece no sino e no "Precisa de atenção" quando #84 e o Início existirem, lendo essa marca. Idem RN-ALU-10: o item de correção para a gestão virá desses mesmos itens, lendo `email_problem_at`.
- D8: RN-ALU-10 sem entrada real de bounce: a porta de e-mail só devolve `SENT` ou `NOT_SENT` hoje; marcar e limpar são serviço e auditoria, e a chamada vem com o e-mail real (Sprint 22).
- D9: cadastro feito pelo próprio responsável (RN-LGP-03, "ou pelo responsável") fica para os pedidos do portal (#86); aqui o menor é cadastrado pela gestão e só o responsável o vê no portal.
- D10: aluno não se exclui nem aqui nem depois (RN-ALU-08); desligar responsável remove a linha de `student_guardian` e o histórico fica na auditoria. Alternativa: marcar `ended_at` e manter a linha; descartada por complicar a unicidade do financeiro sem ganho, já que a auditoria guarda quem, quando e por quê.
- D11: telefone no formato de `NewUnit.phone` (DDD mais 8 ou 9 dígitos) e e-mail como `NewUnit.email`; o card pede só "celular com DDD". Se o dono aceitar telefone estrangeiro, muda a validação.
- D12: item grande, dividido nas três partes de "Tarefas"; "Recurso novo nasce desligado" e notas de versão não se aplicam (cadastro base, não é recurso liberado aos poucos).
- D13 (ajuste, Verificação rodada 1): o código `MINOR_NEEDS_GUARDIAN` (409) vale só ao desligar o último responsável (R7). No `POST /students`, menor sem responsável é erro de validação do corpo: 400 `VALIDATION_ERROR` no campo `guardians` (R4, CA7 reescrito). Motivo: é entrada inválida, não conflito de estado, e o código já faz assim com teste. Alternativa: 400 com o código `MINOR_NEEDS_GUARDIAN`; descartada por misturar dois status para o mesmo código.
- D14 (ajuste, Verificação rodada 1): "cadastrado menor" (R9) é medido pela data de `created_at` em UTC; cadastro feito no próprio dia do 18º aniversário conta como adulto e não gera aviso. A sugestão de gravar a auditoria `student.guardian_invite_resent` na mesma transação do reenvio não é requisito: fica como melhoria para a construção, sem tarefa nova.
- Suposição: o menor sem e-mail próprio é aceito; se o e-mail do menor coincidir com o do responsável, nada impede (não há regra). Aluno de 18 anos exatos hoje conta como adulto (mesma regra de `InviteRules.adult`).

## Construção

Partes 1 a 3 (T1 a T10) concluídas.

- T1 — arquivos: `backend/src/main/resources/db/migration/V29__student_guardian.sql`, `infra/seed/12-alunos.sql`, `infra/scripts/prepare-runtime-db.sh` (revoke de DELETE em `student` e `student_guardian`, pois o script reconcede CRUD em todas as tabelas), `backend/src/test/java/br/com/ctcore/student/StudentSchemaIT.java` (novo), `backend/src/test/java/br/com/ctcore/db/SeedDataIT.java` (limpeza das tabelas novas, contagens de conta e vínculo, teste `studentsSeed`) — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test='StudentSchemaIT,SeedDataIT' -Dtest=NoSuchTest -Dsurefire.failIfNoSpecifiedTests=false -Djacoco.skip=true` → 18 testes, 0 falhas (StudentSchemaIT 5, SeedDataIT 13).
- T2 — arquivos: `backend/src/main/java/br/com/ctcore/student/{Students,StudentRegistration,StudentController}.java` (novos), `ErrorCode.java` e `messages.properties` (cinco códigos novos), `backend/src/test/java/br/com/ctcore/student/StudentRegistrationIT.java` (novo) — prova: `verify -Dit.test=StudentRegistrationIT` (mesmos flags) → 9 testes, 0 falhas. CA5 e CA8 ficam provados até a recusa por falta de responsável; o cadastro do menor com responsável é de T4.
- T3 — arquivos: `InviteService.java` (`inviteGuardian`, `sendInvite`, `resendGuardian`), `InviteRules.java` (`guardianAccess`), `EmailTemplate.java` (`GUARDIAN_UNLINKED`, `STUDENT_MAJORITY`), `messages.properties`, `InviteRulesTest.java` e `InviteIT.java` (dois testes novos) — prova: `test -Dtest=InviteRulesTest` → 2 testes ok; `verify -Dit.test=InviteIT` (mesmos flags) → 8 testes, 0 falhas.
- T4 a T8 — arquivos: `backend/src/main/java/br/com/ctcore/student/{StudentGuardians,EmailProblems,MajorityNoticeJob,PortalStudents,PortalStudentController}.java` (novos), `StudentRegistration.java` e `StudentController.java` (guardians no POST e rotas de responsáveis), testes `StudentGuardiansIT` (7), `EmailProblemsIT` (1), `MajorityNoticeJobIT` (1), `PortalStudentsIT` (1) — prova: `verify` completo (T10) → 0 falhas. As caixas já estavam marcadas sem linha aqui; o código e os testes existem e passam.
- T9 — arquivos: `docs/openapi.yaml`, `frontend/src/mocks/handlers.ts`, `frontend/src/mocks/students.msw.test.ts`, `frontend/src/features/students/types.ts`, `infra/seed/12-alunos.sql` — prova: `verify` (inclui `OpenApiContractTest`, `ContractRoutesTest`, `SeedDataIT`) e `npm run test:cov` (inclui `src/mocks`) → ok.
- T10 — prova: `./backend/mvnw -f backend/pom.xml verify` → 399 testes, 0 falhas, "All coverage checks have been met", BUILD SUCCESS; em `frontend/`: `npm run lint && npm run typecheck && npm run build && npm run test:cov` → ok (linhas 96,99%); `npm run e2e` → 28 passed.

Regressão completa (parte 3): ver T10; sem falha anterior a citar.
Desvios da parte 3:
- Primeiro `verify` completo: 19 falhas nos `*IT` de `student`, porque a limpeza (`@AfterEach`) apagava `account_link` sem apagar antes `account_session` de contas `%.example` deixadas por outros testes (FK `account_session_active_link_id_fkey`); `unlinkNotifies` falhou em cascata. Correção: `delete from account_session where account_id in (...)` na limpeza de `StudentRegistrationIT`, `StudentGuardiansIT` e `EmailProblemsIT`. Segundo `verify` completo: verde.
- `npm run e2e` falhou uma vez (`menu-permissoes.spec.ts`) com o `verify` do back-end rodando em paralelo; repetido duas vezes sem carga → 28 passed. Instável por carga, não reproduziu.

Regressão da parte 1 (histórico): parcial, só do que aquela parte alterou. `./backend/mvnw -f backend/pom.xml test` → só `ContractRoutesTest` e `OpenApiContractTest` falham, porque as rotas novas ainda não estão no `docs/openapi.yaml` (T9, parte 3); o restante dos testes rápidos passa. A regressão completa (T10) e o front-end não rodaram nesta parte.

Desvios:
- Os testes de T3 entraram em `InviteIT` (já tem a fixture de convite) e a prova roda `InviteIT`; não foi criado `InviteServiceIT`.
- `inviteGuardian` não envia o e-mail nem abre transação própria: roda na transação de quem chama (cadastro do aluno, T4) e `sendInvite` manda depois do commit (risco 2 do plano). Vínculo `GUARDIAN` já ativo devolve `Issued` com `code` nulo (sem convite novo).
- `MINOR_NEEDS_GUARDIAN` é 409 no `ErrorCode` (R7, desligar o último). No `POST /students` (CA7 pede 400) o menor sem responsável dá 400 `VALIDATION_ERROR` no campo `guardians`, não o código `MINOR_NEEDS_GUARDIAN`; a diferença de status entre R7 e CA7 fica a cargo da fase 2/4 se o dono quiser o código também no 400.
- 409 de CPF repetido e de possível duplicidade leva `id`, `name` e `photoUrl` em `fields` (campo e valor) do corpo de erro padrão.
- Gatilho `student_guardian_limit` não conta o par que já existe (senão `on conflict do nothing` do seed falha com dois responsáveis).
- Mensagem do e-mail `STUDENT_MAJORITY` leva o nome do aluno como argumento `{0}` (T7 passa o argumento).
Não é do item: nenhuma mudança fora do escopo; `specs/itens/54-...md` era o único arquivo sem commit no início.

### Correção da rodada 1
- Achado obrigatório (R4/CA7 x R7, `MINOR_NEEDS_GUARDIAN`): já atendido pela fase 2 (D13, CA7 reescrito); o código já dá 400 `VALIDATION_ERROR` no campo `guardians` no `POST` e 409 `MINOR_NEEDS_GUARDIAN` ao desligar, com teste. Sem mudança de código.
- Sugestões (auditoria do reenvio na mesma transação; `created_at` em UTC, D14): opcionais, não feitas.
- Prova: `verify -Dit.test='StudentRegistrationIT,StudentGuardiansIT,MajorityNoticeJobIT' -Dtest=NoSuchTest -Dsurefire.failIfNoSpecifiedTests=false -Djacoco.skip=true` → 17 testes, 0 falhas, BUILD SUCCESS. Regressão completa não repetida: o código não mudou desde o `verify` de T10 (399 testes, 0 falhas).

## Verificação

### Rodada 1 — CORRIGIR_ESPEC — 2026-10-20, estado 8d68728633f5b485f2fe7b5f4513f5ce477207dc

| Critério | Status | Evidência |
|---|---|---|
| CA1 | PASSOU | `StudentRegistrationIT.createsAdult` (verify completo verde) |
| CA2 | PASSOU | `StudentRegistrationIT.validates` (nome, data, CPF, telefone, UF, adulto sem dados, emergência parcial) |
| CA3 | PASSOU | `StudentRegistrationIT` (bloodType/medicalCertificate ignorados) e `StudentSchemaIT.noHealthColumns` |
| CA4 | PASSOU | `StudentRegistrationIT.cpfExists` |
| CA5 | PASSOU | `StudentRegistrationIT.possibleDuplicate` |
| CA6 | PASSOU | `StudentRegistrationIT.lookup` (200 com 3 campos, 404, 404 de outra org, 400) |
| CA7 | NÃO PASSOU (parcial) | 400 sem `guardians`, financeiro, dois/zero/três: `StudentGuardiansIT.createsMinor`. O critério pede o código `MINOR_NEEDS_GUARDIAN` no 400; o servidor devolve `VALIDATION_ERROR` no campo `guardians` (desvio registrado em Construção; `StudentRegistrationIT.minorNeedsGuardian` confere só o campo) |
| CA8 | PASSOU | `createsMinor` (telefone do responsável inválido, campo `guardians.phone`) |
| CA9 | PASSOU | `createsMinor` (conta INVITED, vínculo PENDING, 3 convites distintos, e-mails) |
| CA10 | PASSOU | `StudentGuardiansIT.reusesGuardian` (GUARDIAN_CONFLICT) |
| CA11 | PASSOU | `reusesGuardian` |
| CA12 | PASSOU | `linkAndUnlink` (motivo 2/201 dão 400, PUT) e `audit` |
| CA13 | PASSOU | `linkAndUnlink` (GUARDIAN_LIMIT, MINOR_NEEDS_GUARDIAN 409, financeiro passa) |
| CA14 | PASSOU | `unlinkNotifies` (vínculo ENDED, e-mail neutro) |
| CA15 | PASSOU | `resends` (429 no quarto, 409 ativo) |
| CA16 | PASSOU | `MajorityNoticeJobIT.notifiesOnce` |
| CA17 | PASSOU | `PortalStudentsIT.cards` |
| CA18 | PASSOU | `StudentSchemaIT.runtimeRoleCannotDelete`; sem rota DELETE (ContractRoutesTest) |
| CA19 | PASSOU | `EmailProblemsIT.marksAndClears` |
| CA20 | PASSOU | `permissionsAndScope` e `permissionsAndIsolation` |
| CA21 | PASSOU | `isolation`, `StudentSchemaIT.rowLevelSecurity` |
| CA22 | PASSOU | `audit` nos dois ITs |
| CA23 | PASSOU | `verify` (OpenApiContractTest, ContractRoutesTest, SeedDataIT); `students.msw.test.ts` |

Comandos:
- `./backend/mvnw -f backend/pom.xml verify` -> 399 testes, 0 falhas, "All coverage checks have been met", BUILD SUCCESS.
- em `frontend/`: `npm run lint && npm run typecheck && npx vitest run src/mocks` -> ok, 113 testes. `build`, `test:cov` e `e2e` não rodados nesta rodada (o item só toca `src/mocks` e tipos novos; registrados como ok na Construção, não reconferidos).
Achados:
- obrigatório, especificação, R4/CA7 x R7/`ErrorCode.MINOR_NEEDS_GUARDIAN` — esperado: CA7 diz "400 `MINOR_NEEDS_GUARDIAN`", R7 diz 409 com o mesmo código ao desligar; observado: o código é 409 em `ErrorCode` e o `POST /students` de menor sem responsável dá 400 `VALIDATION_ERROR` (`StudentGuardians.prepare`); evidência: leitura do código e `StudentRegistrationIT.minorNeedsGuardian`; corrigir: a especificação deve decidir um comportamento só (por exemplo, no `POST` o código `MINOR_NEEDS_GUARDIAN` com status 400, ou aceitar `VALIDATION_ERROR` no campo `guardians` e reescrever o CA7), e o código/teste acompanham.
- sugestão, código, `StudentGuardians.resendInvite` — a auditoria `student.guardian_invite_resent` roda em transação separada, depois do reenvio; se falhar, o convite sai sem auditoria. Gravar na mesma transação do reenvio.
- sugestão, código, `MajorityNoticeJob.notify` — o critério "cadastrado menor" usa a data de `created_at` em UTC; cadastro no próprio dia do 18º aniversário é tratado como adulto. Aceitável, vale registrar na especificação.

### Rodada 2 — OK — 2026-10-20, estado 248e89e1698414e24db90d8dc06d7933982f15b9

Desde a rodada 1 (estado 8d68728) mudou só este arquivo (D13, D14, CA7 reescrito, "Correção da rodada 1"); nenhum código. CA1 a CA6 e CA8 a CA23: mantido da rodada 1 (código inalterado).

| Critério | Status | Evidência |
|---|---|---|
| CA7 | PASSOU | CA7 reescrito (D13): 400 `VALIDATION_ERROR` no campo `guardians`; `StudentRegistrationIT.minorNeedsGuardian` confere o campo; `StudentGuardiansIT.createsMinor` cobre um, dois, zero e três responsáveis |
| Demais | mantido da rodada 1 | `verify` dos ITs de student verde de novo |

Comandos:
- `./backend/mvnw -f backend/pom.xml verify -Dit.test='StudentRegistrationIT,StudentGuardiansIT,MajorityNoticeJobIT,StudentSchemaIT,PortalStudentsIT,EmailProblemsIT' -Dtest=NoSuchTest -Dsurefire.failIfNoSpecifiedTests=false -Djacoco.skip=true` -> 24 testes, 0 falhas, BUILD SUCCESS.
- Regressão completa não repetida: o código não mudou desde o `verify` da rodada 1 (399 testes, 0 falhas).
Achados:
- Achado obrigatório da rodada 1 (R4/CA7 x R7): resolvido pela especificação (D13).
- sugestão, código, `StudentGuardians.resendInvite` — auditoria do reenvio em transação separada (mantida, opcional).

## Entrega

- Cadastro de aluno e responsáveis no servidor (migração V29, `student/`, rotas em `/api/v1` e no OpenAPI, handlers MSW, seed `12-alunos.sql`), com convite de responsável, aviso de maioridade (job) e marca de e-mail com problema.
- Verificação: rodada 2 `OK`; `verify` completo da rodada 1 com 399 testes e 0 falhas; ITs de student verdes de novo.
- Decisões: D13 (menor exige 1 ou 2 responsáveis, erro `VALIDATION_ERROR` em `guardians`) e D14, no arquivo do item.
- Limitação: a devolução real de e-mail (bounce) vem na Sprint 22; aqui só o estado e o bloqueio. Sugestão opcional: auditoria do reenvio em transação separada.
- A base não avançou; regressão não repetida.
