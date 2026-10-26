# 85 — Aceite do termo e confirmações no portal

Origem: issue #85 (Sprint 10 · Portal do aluno), com os encaixes herdados das Sprints 06, 07 e 09; `docs/especificacao/09-matricula.md` (RN-MAT-11 a 14), `14-portal-do-aluno.md` (RN-POR-03), `03-auditoria-e-lgpd.md` (RN-LGP-01). Branch: `item/85-aceite-termo-confirmacoes-portal`.

<!-- Fase 2: especificação. Escrita por `especificar`, que grava só as seções 1 a 6. -->

## 1. Objetivo

Dar ao aluno adulto e ao responsável financeiro do menor o aceite do termo de matrícula no portal, com o uso de imagem opcional e revogável, e a confirmação curta (plano e valor) de cada modalidade nova. O servidor guarda cada aceite (conta, versão, data, hora, IP), bloqueia as demais rotas do portal enquanto o termo está pendente e alimenta o selo "Termo pendente" da gestão. A única tela nova é a do aceite (canvas `PortalTermo` e `PortalMTermo`). As boas-vindas (modelo `WELCOME` da #83) passam a sair com o convite do portal.

Fora do escopo: barra e navegação do portal (#88), Início (#89), Minhas informações (#94, onde o aluno revoga o uso de imagem pela tela; aqui só a rota), pedidos do portal (#86), "Baixar meus dados" e "Excluir minha conta" (Sprint 13), portal reduzido de Inativo (Sprint 12), notas de versão (o arquivo não existe no repositório).

## 2. Requisitos

- R1 (RN-MAT-11): o termo de matrícula é aceito uma vez por aluno, só pelo próprio aluno adulto (conta do aluno) ou pelo responsável financeiro do aluno menor de 18 anos. Responsável não financeiro e qualquer outra conta não aceitam. Só vale para aluno que tem matrícula.
- R2 (RN-MAT-12, RN-LGP-01): cada aceite grava aluno, tipo, versão do texto, conta, data, hora e IP, e nunca é alterado nem apagado. O servidor aceita só a versão vigente (`Terms.current`); outra versão responde 409 `TERMS_VERSION_CONFLICT`.
- R3 (RN-MAT-12): o uso de imagem é uma caixa separada e opcional no mesmo aceite; o aluno ou o responsável financeiro pode conceder depois e revogar a qualquer momento por rota do portal. Cada concessão e revogação é um registro novo (D3).
- R4 (RN-MAT-14, D2): o termo está pendente para o aluno com matrícula que nunca aceitou, ou cuja última versão aceita é menor que a maior versão publicada com `reaccept` verdadeiro. Versão publicada com `reaccept` falso não gera pendência para quem já aceitou.
- R5 (RN-POR-03, RN-MAT-11, D4): enquanto a conta tem aluno com termo pendente que ela mesma deve aceitar, toda rota do portal, exceto as do próprio aceite e do uso de imagem, responde 403 `TERM_PENDING`. A conta sem pendência não é afetada; responsável não financeiro nunca é bloqueado.
- R6 (RN-MAT-11, encaixes das Sprints 06 e 07): `StudentSeal` ganha `TERM_PENDING` ("Termo pendente"), na lista, na ficha e no filtro `seal`, com a mesma regra do R4; sai ao aceitar. A ficha do Professor continua sem selos. O comentário de `StudentSeal.java` aponta esta issue.
- R7 (RN-MAT-13, D5): a modalidade ativa criada depois do primeiro aceite do aluno pede uma confirmação com plano e valor, feita pela mesma pessoa do R1; o portal lista as pendentes e grava cada confirmação (conta, data, hora, IP, plano e valor mostrados). Confirmar nunca altera matrícula, plano nem cobrança, e a pendência não bloqueia o portal.
- R8 (RN-POR-01, isolamento): toda rota nova é só de Aluno e Responsável (gestão e Professor recebem 403). Aluno que não é ligado à conta responde 404; aluno ligado mas que a conta não pode aceitar responde 403. Nenhuma resposta traz dado de aluno de outra organização ou de outra conta.
- R9: aceite, concessão e revogação de uso de imagem e confirmação de modalidade gravam na auditoria (ação, aluno, versão, IP; sem texto do termo).
- R10: as rotas entram em `docs/openapi.yaml` e nos handlers MSW; o enum `StudentSeal` ganha `TERM_PENDING`.
- R11 (RF do canvas): a tela de aceite (`/portal/termo`) mostra o texto do termo, a caixa de uso de imagem separada e desmarcada, e "Aceitar e continuar"; tem carregando, vazio, erro e sem permissão, funciona a partir de 360 px, passa no axe nos dois temas e usa só textos do arquivo pt-BR. Portal com termo pendente leva a ela antes de qualquer outra tela (RN-POR-03); as confirmações do R7 aparecem em cartão na tela inicial provisória do portal.
- R12 (encaixe da Sprint 09): ao concluir a matrícula, o modelo `WELCOME` (texto próprio da organização ou o da plataforma) é enviado a quem recebe o convite do portal: o aluno adulto com convite enviado e cada responsável do menor com e-mail (D6).
- R13 (AGENTS.md): um seed fictício de aceite acompanha as tabelas novas.

## 3. Critérios de aceite

- CA1 (R1, R2): dado aluno adulto com matrícula e termo vigente v1 sem aceite, quando a conta dele faz `POST /portal/students/{id}/term-acceptance` com `version` 1, então 201 e fica gravado aceite com conta, versão 1, data, hora e IP, e o termo deixa de estar pendente.
- CA2 (R1): dado aluno menor com dois responsáveis, quando o responsável financeiro aceita, então 201; quando o não financeiro tenta, então 403 e nada é gravado.
- CA3 (R1, R8): dado aluno ligado a outra conta, ou de outra organização, quando uma conta de portal tenta aceitar, então 404 sem revelar se existe.
- CA4 (R2): dado termo vigente v2, quando o aceite chega com `version` 1 ou sem `version`, então 409 `TERMS_VERSION_CONFLICT` e 400 `VALIDATION_ERROR` respectivamente, e nada é gravado.
- CA5 (R1, R2): dado aceite já feito da versão vigente, quando o mesmo aceite chega de novo (inclusive duas chamadas simultâneas), então 200 sem segunda linha.
- CA6 (R1): dado aluno sem matrícula, quando a conta tenta aceitar, então 200 sem gravar nada (nada pendente).
- CA7 (R3): dado o aceite com `imageUse` verdadeiro, então grava também o aceite de uso de imagem na versão vigente dele; com `imageUse` falso ou ausente, não grava. O aceite do termo não depende da caixa.
- CA8 (R3): dado uso de imagem concedido, quando `PUT /portal/students/{id}/image-use` com `granted` falso, então 200, `GET` mostra `granted` falso, a revogação fica registrada e o histórico mantém a concessão; conceder de novo com a versão vigente cria novo registro; versão antiga responde 409.
- CA9 (R3, R4): dado uso de imagem concedido na v1 e v2 de uso de imagem publicada com `reaccept` verdadeiro, quando `GET .../image-use`, então `granted` falso com o texto da v2.
- CA10 (R4): dado aceite da v1 e v2 publicada com `reaccept` falso, então o termo não está pendente; com v3 `reaccept` verdadeiro, volta a pendente; depois de aceitar a v3, sai.
- CA11 (R4): dado aluno matriculado depois da publicação da v3 com `reaccept` falso e sem aceite, então está pendente e o aceite grava a v3.
- CA12 (R5): dado aluno adulto com termo pendente, quando a conta chama `GET /portal/students`, então 403 `TERM_PENDING`; depois do aceite, 200. `GET /portal/term` e as rotas de aceite e de uso de imagem funcionam durante a pendência.
- CA13 (R5): dado responsável com dois filhos, um pendente e outro não, quando chama `GET /portal/students`, então 403 até aceitar o do segundo; dado responsável não financeiro do único filho pendente, então 200.
- CA14 (R5, R8): dado usuário da gestão (Coordenador, Administrador) ou Professor, quando chama qualquer rota nova do portal, então 403 `FORBIDDEN`; sem sessão, 401.
- CA15 (R4, R5): `GET /portal/term` lista só os alunos pendentes que a conta aceita, cada um com id, nome, versão e texto do termo e do uso de imagem; sem pendência, lista vazia.
- CA16 (R6): dado aluno com matrícula e sem aceite, quando a gestão lista alunos ou abre a ficha, então `seals` traz `TERM_PENDING`; `seal=TERM_PENDING` filtra; após o aceite, o selo some; a ficha do Professor segue sem selos; aluno sem matrícula não tem o selo.
- CA17 (R7): dado aluno com aceite e modalidade adicionada depois dele, quando `GET /portal/modality-confirmations`, então a lista traz aluno, modalidade, unidade, plano e valor em centavos; modalidade criada antes do aceite, já confirmada ou encerrada não aparece.
- CA18 (R7): quando a conta confirma por `POST /portal/modality-confirmations/{modalityRowId}`, então 201 com plano e valor gravados, a pendência some e matrícula, plano e cobranças ficam iguais; repetir devolve 200 sem nova linha.
- CA19 (R7, R8): dado modalidade de aluno de outra conta ou organização, então 404; de aluno ligado que a conta não pode aceitar, 403; com termo pendente, 403 `TERM_PENDING`.
- CA20 (R9): cada aceite, concessão, revogação e confirmação gera uma entrada de auditoria com ação, aluno, versão e IP, sem o texto do termo; repetição sem gravação não gera entrada.
- CA21 (R10): `OpenApiContractTest` e `ContractRoutesTest` passam com as rotas novas; os handlers MSW respondem com os formatos do OpenAPI.
- CA22 (R11): dado portal com termo pendente, quando a pessoa abre qualquer rota do portal, então é levada a `/portal/termo`; aceita e segue; a tela tem os quatro estados, não estoura a 360 px e passa no axe.
- CA23 (R12): dado conclusão de matrícula de aluno adulto com e-mail e sem conta ativa, então, além do convite, sai o e-mail `WELCOME` com `{nome}` e `{unidade}` preenchidos (texto próprio ou o da plataforma); com menor, sai a cada responsável com e-mail; falha no envio não desfaz a matrícula.
- CA24 (R13): `SeedDataIT` aplica o seed novo sem erro, mais de uma vez.

## 4. Plano técnico

- Dados: migração `V48__term_acceptance.sql`. `term_acceptance` (id, `organization_id`, `student_id`, `kind` ENROLLMENT ou IMAGE_USE, `event` ACCEPTED ou REVOKED, `version` ≥ 0, `account_id`, `accepted_at` default now, `ip`; REVOKED só para IMAGE_USE; índice único parcial por (aluno, versão) onde `kind` = ENROLLMENT, que segura a corrida do CA5). `modality_confirmation` (id, `organization_id`, `enrollment_modality_id` único, `student_id`, `account_id`, `plan_id`, `plan_value_cents`, `confirmed_at`, `ip`). As duas com RLS forçada e a política `organization_scope` como em `V38__terms_version.sql`, e sem UPDATE nem DELETE para o papel operacional (mesma técnica do V38). A migração também amplia `email_message_template_check` (texto do `V47__notification.sql`) com `WELCOME`. Reversão manual no cabeçalho: `drop table modality_confirmation; drop table term_acceptance;` e restaurar o check do V47.
- API (todas em `/api/v1/portal`, só Aluno e Responsável): `GET /term`; `POST /students/{id}/term-acceptance` (`{version, imageUse?}`); `GET` e `PUT /students/{id}/image-use` (`{granted, version?}`); `GET /modality-confirmations`; `POST /modality-confirmations/{modalityRowId}`. Códigos: 400, 401, 403 (`FORBIDDEN`, `TERM_PENDING`), 404, 409 (`TERMS_VERSION_CONFLICT`). `GET /portal/students` passa a responder 403 `TERM_PENDING`. Contrato de `StudentSeal` e dos esquemas novos no OpenAPI.
- Interface: página `/portal/termo` (carregando, vazio que volta ao início, erro com "Tentar de novo", sem permissão); um bloco por aluno pendente, com nome, texto do termo (texto puro com quebras de linha, D7), caixa "Li e aceito", caixa separada e desmarcada do uso de imagem com o texto dele, e "Aceitar e continuar". Guarda `RequirePortalTerm` leva ao `/portal/termo`. Cartão de confirmação de modalidade na tela inicial provisória do portal (`Home` em `App.tsx`). Selo "Termo pendente" na lista, na ficha e no filtro de alunos.
- Segurança: perfil e vínculo conferidos no servidor em toda rota (`PortalTerms.requirePortal`, no molde de `PortalStudents.cards`); regra "quem aceita" num só ponto, reusada pelo aceite, pelo uso de imagem, pela confirmação e pelo portão. SQL sempre com parâmetros. IP de `request.getRemoteAddr()`. A auditoria não leva o texto do termo. Aceite é dado pessoal com base legal de obrigação contratual e consentimento (uso de imagem); revogação sem prejuízo da matrícula, como diz o texto-modelo.
- Arquivos a alterar (vistos no repositório): `backend/src/main/java/br/com/ctcore/student/StudentSeal.java` (novo valor, comentário); `.../student/Students.java` (`sealPredicate` com `TERM_PENDING`, select de `rows` com mais uma coluna, o que desloca `u.timezone`, `STATUS` e `FINANCIAL` em `rows` e o loop de `seals`; `AGE` e `TODAY` deixam de ser privados para o aceite reaproveitar); `.../student/PortalStudentController.java` (portão antes de `portal.cards`); `.../student/EnrollmentConclusion.java` (`termPending` passa a vir do mesmo cálculo; boas-vindas depois do convite); `.../api/error/ErrorCode.java` e `backend/src/main/resources/messages.properties` (`TERM_PENDING`, 403); `.../mail/EmailTemplate.java` (`WELCOME`, `Kind.ALWAYS`); `docs/openapi.yaml`; `frontend/src/mocks/handlers.ts`; `frontend/src/features/students/types.ts`, `StudentFilters.tsx`; `frontend/src/i18n/pt-BR.json`; `frontend/src/App.tsx` (rota e guarda, cartão na `Home` do portal).
- Arquivos a criar: `backend/src/main/resources/db/migration/V48__term_acceptance.sql`; `.../student/PortalTerms.java` (pendências, aceite, uso de imagem, portão e regra de quem aceita); `.../student/PortalTermController.java`; `.../student/ModalityConfirmations.java`; `.../student/WelcomeEmail.java`; `infra/seed/21-aceites-termo.sql`; testes `PortalTermsIT`, `PortalTermControllerIT` (ou na mesma classe), `ModalityConfirmationsIT`, `WelcomeEmailIT`; `StudentSealsIT` ganha os casos do selo (arquivo existente); no front `frontend/src/features/portal/` (`PortalTermPage.tsx`, `usePortalTerm.ts`, `RequirePortalTerm.tsx`, `ModalityConfirmations.tsx` e testes), `frontend/src/mocks/portal-term.msw.test.ts`, `frontend/e2e/portal-termo.spec.ts` (`@critical`, com `worker.use` da #368).
- Reuso: `Terms.current` (versão vigente do termo e do uso de imagem), `OrganizationTransactions.execute`, `AuditService.record` e `AuditEntry` como em `Terms.publish`, `PortalStudents.cards` (molde de perfil e vínculo), `TextModels.custom`, `platform` e `fill`, `EmailService.sendText`, `ApiStatusException`, `ErrorCode.TERMS_VERSION_CONFLICT`, `Students.row(...).minor()`, `student_guardian.financial`, `plan.value_cents`.
- Riscos: o selo novo entra em testes que listam `seals` de aluno matriculado (`StudentSealsIT`, `StudentListIT`, `StudentRecordIT`, `students.msw.test.ts`, `benefits-write.msw.test.ts`) e a coluna a mais em `rows` desloca índices (quebra visível na lista e na ficha); `GET /portal/students` passa a exigir o aceite, e `PortalStudentsIT` precisa de aluno sem matrícula ou já aceito; o e-mail de boas-vindas depende da constraint nova (sem a migração o insert falha, e a falha só vai para o log); o valor mostrado na confirmação é `plan.value_cents` atual, sem benefício (D5).

## 5. Tarefas

Construção em quatro partes, cada uma em uma sessão: A (T1 a T3, servidor do aceite), B (T4 e T5, selo e confirmações), C (T6, boas-vindas) e D (T7 a T10, front, seed e ponta a ponta). O comando de um teste de integração é `./backend/mvnw -f backend/pom.xml verify -Dit.test=<Classe> -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Djacoco.skip=true` (Docker ativo).

- [x] T1: migração `V48`, `ErrorCode.TERM_PENDING`, mensagem pt-BR, `EmailTemplate.WELCOME` — cobre CA24 (em parte, base das tabelas) — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test=SeedDataIT -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Djacoco.skip=true` (migra o banco com as tabelas novas)
- [x] T2: `PortalTerms`: regra de quem aceita, pendência (R4), aceite com corrida, uso de imagem (concessão, revogação, estado), portão e auditoria — cobre CA1, CA2, CA4 a CA11, CA20 — prova: `-Dit.test=PortalTermsIT`
- [x] T3: `PortalTermController` (`GET /term`, aceite, `GET` e `PUT` de uso de imagem), portão em `PortalStudentController`, OpenAPI das rotas — cobre CA3, CA12 a CA15, CA21 — prova: `-Dit.test=PortalTermControllerIT` e `./backend/mvnw -f backend/pom.xml test -Dtest='OpenApiContractTest,ContractRoutesTest'`
- [x] T4: `StudentSeal.TERM_PENDING` em `Students` (lista, ficha, filtro), `EnrollmentConclusion.termPending` calculado, OpenAPI do enum, ajuste dos testes que listam selos — cobre CA16 — prova: `-Dit.test=StudentSealsIT` (casos novos do selo no arquivo existente)
- [x] T5: `ModalityConfirmations` e rotas `GET /modality-confirmations` e `POST /modality-confirmations/{modalityRowId}`, com OpenAPI — cobre CA17 a CA19 e CA20 (confirmação) — prova: `-Dit.test=ModalityConfirmationsIT` e o teste de contrato do T3
- [x] T6: `WelcomeEmail` e chamada em `EnrollmentConclusion` depois do convite, sem derrubar a matrícula — cobre CA23 — prova: `-Dit.test=WelcomeEmailIT`
- [x] T7: tipos, MSW e textos: handlers de `/portal/term`, aceite, uso de imagem e confirmações no estado do mock, `TERM_PENDING` em `GET /portal/students`, selo no mock e em `types.ts`, `StudentFilters.tsx` e `pt-BR.json` — cobre CA21 (lado MSW) e CA16 (tela de alunos) — prova: `npm test -- portal-term.msw.test.ts students.msw.test.ts` (em `frontend/`)
- [x] T8: `PortalTermPage`, `usePortalTerm`, `RequirePortalTerm` e a rota `/portal/termo`, com os quatro estados — cobre CA22 — prova: `npm test -- PortalTermPage` (em `frontend/`)
- [x] T9: cartão de confirmação de modalidade na `Home` do portal (`ModalityConfirmations.tsx`) — cobre CA17 e CA18 na tela — prova: `npm test -- ModalityConfirmations` (em `frontend/`)
- [x] T10: seed `21-aceites-termo.sql` e teste ponta a ponta do aceite (`portal-termo.spec.ts`, a 360 px e com axe nos dois temas) — cobre CA22 e CA24 — prova: `SeedDataIT` como no T1 e `npm run e2e -- portal-termo` (em `frontend/`)

## 6. Decisões

- D1: aceite e uso de imagem ficam em uma tabela só, `term_acceptance`, só de inserção, com `kind` e `event`. O estado atual é o último registro; nada é editado, porque o aceite cita a versão (RN-LGP-01) e a revogação precisa preservar a concessão. Alternativa descartada: uma linha com `revoked_at` atualizável, que apagaria o histórico.
- D2 (muda o comportamento): o termo só está pendente para aluno que tem matrícula, e a regra de reaceite é "última versão aceita menor que a maior versão publicada com `reaccept` verdadeiro", como o #70 deixou gravado. Motivo: o termo é "de matrícula" e a conclusão da matrícula já nasce sem aceite; aluno só cadastrado não vê o selo nem trava o portal. Alternativa: contar todo aluno cadastrado; descartada por exigir aceite de quem ainda não é aluno.
- D3 (muda o comportamento): o uso de imagem não bloqueia nada. Concedido por caixa no aceite ou depois por `PUT`; uma nova versão dele com `reaccept` verdadeiro faz `granted` voltar a falso (consentimento é por texto) sem travar o portal. Alternativa: tratá-lo como o termo e bloquear; descartada por contrariar "opcional" (RN-MAT-12).
- D4 (muda o comportamento): o bloqueio do RN-POR-03 é do servidor: 403 com código `TERM_PENDING` em toda rota do portal fora o aceite e o uso de imagem, e o front leva ao `/portal/termo`. O portão cobre só rotas do portal (hoje só `GET /portal/students`; as próximas usam `PortalTerms.requireAccepted`). Conta de responsável com vários filhos fica bloqueada até todos os filhos pendentes que ela aceita estarem aceitos; aceite é por aluno, em chamadas separadas. Alternativa: bloquear só no front; descartada pelo AGENTS.md (o front nunca é a única barreira).
- D5 (muda o comportamento): "modalidade nova" é a modalidade ativa com `created_at` posterior ao primeiro aceite do aluno e sem confirmação, o que cobre adicionar modalidade e reabertura de matrícula, e não cobre troca de unidade, de turma ou de plano (alteram a linha existente). A confirmação mostra o valor atual do plano (`plan.value_cents`), sem benefício, e grava plano e valor mostrados. Não bloqueia o portal nem tem prazo; quem confirma é quem aceita o termo. Alternativa: bloquear até confirmar; descartada porque a RN-MAT-13 pede "confirmação curta" e a RN-POR-03 só cita o termo.
- D6 (muda o comportamento): as boas-vindas saem na conclusão da matrícula, para o aluno adulto quando o convite foi enviado e para cada responsável do menor com e-mail (o convite deles saiu no cadastro). `{unidade}` é a primeira unidade da matrícula. E-mail com problema é pulado pelo `EmailService`. Alternativa: mandar só ao aluno adulto; descartada por deixar menor sem boas-vindas.
- D7: texto do termo exibido como texto puro com quebras de linha (mesmo padrão do #70), sem HTML.
- D8: aceite repetido da versão vigente, ou de aluno sem pendência, é idempotente: 200 sem gravar (operações repetíveis, AGENTS.md); só o primeiro grava e audita (201).
- D9: sem recurso desligado por organização (a sprint não pede liberação gradual) e sem nota de versão (o arquivo não existe); a descrição do PR leva: "Aceite do termo e confirmação de modalidade no portal, selo Termo pendente e boas-vindas no convite".
- Suposição: `plan.value_cents` é o valor vigente do plano e `Students.AGE` pode ser reaproveitado sem mudar comportamento; se o valor vigente vier do histórico de valores (`plan_value_history`), só a consulta da confirmação muda.
- Suposição: todo aluno menor tem exatamente um responsável financeiro (índice único em `V29`, validado no cadastro); se não tiver, ninguém aceita e o selo permanece até a gestão corrigir o vínculo.

## Construção

Parte A (T1 a T3) feita nesta sessão; faltam B (T4, T5), C (T6) e D (T7 a T10). O Docker Desktop estava parado e foi iniciado para as provas.

- T1 — arquivos: `backend/src/main/resources/db/migration/V48__term_acceptance.sql` (novo), `ErrorCode.java` (`TERM_PENDING`), `messages.properties` (`error.TERM_PENDING`, `mail.welcome.*`), `EmailTemplate.java` (`WELCOME`), `SeedDataIT.java` (limpeza das duas tabelas novas) — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test='SeedDataIT,EmailRegistryIT' ...` → 25 testes, 0 falhas
- T2 — arquivos: `student/PortalTerms.java` (novo), `PortalTermBase.java` e `PortalTermsIT.java` (novos, testes) — prova: `-Dit.test=PortalTermsIT` → 6 testes, 0 falhas (CA1, CA2, CA4 a CA11, CA20 no aceite e no uso de imagem; CA5 inclui duas chamadas simultâneas)
- T3 — arquivos: `student/PortalTermController.java` (novo), `PortalStudentController.java` (portão `requireAccepted`), `docs/openapi.yaml` (4 rotas, 6 esquemas, `TERM_PENDING` no enum de erros), `PortalTermControllerIT.java` (novo) — prova: `-Dit.test='PortalTermControllerIT,PortalStudentsIT' -Dtest='OpenApiContractTest,ContractRoutesTest'` → 7 testes de contrato e 6 de integração, 0 falhas
Regressão (parte A): não rodada nesta parte (só ao fim da última parte); rodados também `PortalStudentsIT` (portão não afeta aluno sem matrícula).

Parte B (T4, T5) feita em outra sessão; faltam C (T6) e D (T7 a T10).

- T4 — arquivos: `StudentSeal.java` (`TERM_PENDING`, comentário), `Students.java` (predicado do selo, coluna a mais em `rows` e índices 17 a 19), `EnrollmentConclusion.java` (`termPending` vem do selo via `students.row`), `docs/openapi.yaml` (enum), `StudentSealsIT.java` (helper `seals` sem o selo novo, `allSeals`, limpeza das tabelas novas, caso `termPendingSeal`) — prova: `-Dit.test='StudentSealsIT,EnrollmentConclusionIT,StudentListIT,StudentRecordIT,StudentRecordTeacherIT'` → 41 testes, 0 falhas (CA16: lista, ficha, filtro, reaccept, sem matrícula sem selo; a ficha do Professor segue sem `seals` em `StudentRecordTeacherIT`)
- T5 — arquivos: `ModalityConfirmations.java` (novo), `PortalTerms.java` (`candidates` deixa de ser privado), `PortalTermController.java` (duas rotas), `docs/openapi.yaml` (2 rotas, `ModalityConfirmationItem`, `ModalityConfirmed`), `ModalityConfirmationsIT.java` (novo) — prova: `-Dit.test='ModalityConfirmationsIT,PortalTermControllerIT,PortalTermsIT,PortalStudentsIT' -Dtest='OpenApiContractTest,ContractRoutesTest'` → 7 de contrato e 15 de integração, 0 falhas (CA17 a CA20, CA14 nas rotas novas)
Desvios (parte B): as rotas de confirmação ficaram em `PortalTermController` (sem classe nova); a confirmação passa pelo portão `requireAccepted` (403 `TERM_PENDING` também na lista); modalidade que não está a confirmar (anterior ao aceite, encerrada) responde 404 no POST. A auditoria da confirmação é `modality.confirmed` com aluno, modalidade, plano e valor (o plano não pedia versão do termo ali).
Regressão da parte B: só os testes acima; a regressão completa fica para a última parte.
Desvios: o enum `StudentSeal` do OpenAPI recebe `TERM_PENDING` no T4, como no plano. A resposta do aceite é `{studentId, version, imageUse}` (201 gravou, 200 repetiu), e `GET /term` devolve `{id, name, term{version, body}, imageUse{version, body, granted}}`; o plano não fixava o formato.
Não é do item: nenhum.

Parte C (T6) feita em outra sessão; falta D (T7 a T10).

- T6 — arquivos: `student/WelcomeEmail.java` (novo; texto próprio ou da plataforma, `{nome}` e `{unidade}`, falha só no log), `EnrollmentConclusion.java` (chama depois do convite; menor: a cada responsável), `EnrollmentConclusionIT.java` (caso `welcome` e limpeza de `text_model` e dos e-mails `gen-%`) — prova: `-Dit.test=EnrollmentConclusionIT` → 18 testes, 0 falhas (CA23: adulto convidado, dois responsáveis, texto próprio, convite que falha não envia boas-vindas nem desfaz a matrícula)
Desvios (parte C): o caso de teste ficou em `EnrollmentConclusionIT` (reaproveita os apoios) e não em `WelcomeEmailIT`; a unidade do `{unidade}` vem do resumo da conclusão (primeira unidade). Boas-vindas do adulto só saem com convite enviado (`SENT`).
Regressão da parte C: só o teste acima; a completa fica para a última parte.

Parte D (T7 a T10) feita em outra sessão.

- T7 — arquivos: `frontend/src/mocks/handlers.ts` (estado de aceite em memória, 6 rotas do portal, `TERM_PENDING` em `GET /portal/students`, selo no mock, conta `termo@exemplo.com`), `features/students/types.ts`, `StudentFilters.tsx`, `i18n/pt-BR.json` (selo, `portalTerm`, `portalConfirm`), `mocks/portal-term.msw.test.ts` (novo), `students.msw.test.ts` (enum com o selo novo) — prova: `npm test -- portal-term.msw.test.ts students.msw.test.ts benefits-write.msw.test.ts` → 26 testes, 0 falhas
- T8 — arquivos: `features/portal/usePortalTerm.ts`, `PortalTermPage.tsx`, `RequirePortalTerm.tsx`, `PortalTermPage.test.tsx` (novos), `App.tsx` (rota `/portal/termo` e guarda sobre `/` e `/conta`) — prova: `npm test -- PortalTermPage` → 8 testes, 0 falhas (aceite com e sem imagem, redirecionamento, vazio, erro com nova tentativa, sem permissão, 409, axe claro e escuro)
- T9 — arquivos: `features/portal/ModalityConfirmations.tsx`, `ModalityConfirmations.test.tsx` (novos), `App.tsx` (cartão na `Home` do portal) — prova: `npm test -- ModalityConfirmations` → 3 testes, 0 falhas
- T10 — arquivos: `infra/seed/21-aceites-termo.sql` (novo), `SeedDataIT.java` (caso `termAcceptanceSeed`), `frontend/e2e/portal-termo.spec.ts` (novo) — prova: `SeedDataIT` → 21 testes, 0 falhas; `npm run e2e -- portal-termo` → 2 passam (360 px, axe claro e escuro, `@critical`)
Regressão final: `./backend/mvnw -f backend/pom.xml verify` → BUILD SUCCESS (JaCoCo incluso); em `frontend/`: `npm run lint && npm run typecheck && npm run build` ok, `npm run test:cov` → 139 arquivos, 1192 testes, 0 falhas, cobertura acima do mínimo; `npm run e2e` → 38 passam. `e2e:full`, gitleaks e OSV não rodados (ficam para o CI).
Desvios (parte D): o e2e usa a conta `termo@exemplo.com` do mock (adulto com matrícula pendente) em vez de `worker.use`, porque o aceite precisa mudar o estado entre chamadas; o cartão de confirmação some em silêncio no carregando, vazio e erro de leitura (é complemento da tela inicial, não tela); `StudentsPage.test.tsx` ganhou "Termo pendente" na lista de selos do filtro; a seed marca o aluno 1 como aceito (versão 1, termo e uso de imagem). O e2e não usou `AGENTS.md`/`specs/projeto.md` (sem comando novo).
Não é do item: nenhum.

## Verificação

### Rodada 1 — OK — 2026-10-26, estado daac224b29498fcdb9beab500c781f5619e437df

| Critério | Status | Evidência |
|---|---|---|
| CA1, CA2, CA4 a CA11, CA20 | PASSOU | `PortalTermsIT` no `verify` completo (768 testes, 0 falhas); leitura de `PortalTerms.java` confere regra de quem aceita, versão, idempotência e corrida (`for update of s` + índice único) |
| CA3, CA12 a CA15 | PASSOU | `PortalTermControllerIT`, `PortalStudentsIT` no `verify`; `requirePortal` dá 403 para gestão/Professor |
| CA16 | PASSOU | `StudentSealsIT`, `StudentListIT`, `StudentRecordIT`, `StudentRecordTeacherIT` no `verify` |
| CA17 a CA19 | PASSOU | `ModalityConfirmationsIT` no `verify` |
| CA21 | PASSOU | `OpenApiContractTest`, `ContractRoutesTest` no `verify`; `portal-term.msw.test.ts` no `test:cov` |
| CA22 | PASSOU | `PortalTermPage.test.tsx` (axe claro e escuro) e `portal-termo.spec.ts` (360 px) no `test:cov` e `e2e` |
| CA23 | PASSOU | `EnrollmentConclusionIT` (caso `welcome`) no `verify` |
| CA24 | PASSOU | `SeedDataIT` no `verify` |

Comandos: `./backend/mvnw -f backend/pom.xml verify` → 768 testes, 0 falhas, BUILD SUCCESS (JaCoCo incluso); em `frontend/`: `npm run lint && npm run typecheck && npm run build` ok; `npm run test:cov` → 139 arquivos, 1192 testes, 0 falhas; `npm run e2e` → 38 passam. `e2e:full`, gitleaks e OSV não rodados (ficam para o CI).
Achados:
- sugestão, código, `ModalityConfirmations.tsx` — falha de leitura do cartão some em silêncio (já registrado como desvio da construção).
- sugestão, especificação — a branch atual é `item/85-aceite-termo-portal`, e o plano cita `item/85-aceite-termo-confirmacoes-portal`; ajustar o nome na entrega.

## Entrega

- O que muda: aceite do termo pelo aluno adulto e pelo responsável financeiro do menor, com versão, data, hora e IP gravados em `term_acceptance` (só de inserção); uso de imagem opcional e revogável por caixa; bloqueio do portal com `TERM_PENDING` no servidor (403) e redirecionamento para `/portal/termo`; confirmação curta de plano e valor para modalidade nova; selo "Termo pendente" na gestão; boas-vindas no convite e na conclusão da matrícula; seed de aceites (`infra/seed/21-aceites-termo.sql`).
- Verificação: rodada 1 OK (estado `daac224b`). `./backend/mvnw -f backend/pom.xml verify` com 768 testes e 0 falhas; `npm run lint`, `typecheck`, `build`, `test:cov` (1192 testes) e `e2e` (38 passam) ok no front.
- Decisões que o revisor precisa conhecer: D2 (termo só para quem tem matrícula), D3 (uso de imagem não bloqueia), D4 (bloqueio no servidor; responsável com vários filhos fica bloqueado até os filhos pendentes que ele aceita estarem aceitos), D5 (confirmação não bloqueia nem tem prazo), D6 (boas-vindas também ao responsável do menor). Detalhes na seção 6.
- Limitações: `e2e:full`, gitleaks e OSV não rodados localmente (ficam para o CI). Falha de leitura do cartão de confirmação some em silêncio, como desvio registrado na construção. O nome da branch (`item/85-aceite-termo-portal`) difere do citado na linha "Branch" acima; mantido o nome atual.
