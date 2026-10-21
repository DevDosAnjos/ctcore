# 67 — RF-MAT-01 · Nova matrícula em etapas

Origem: issue #67 (Sprint 07), com o bloco "Encaixe herdado da Sprint 06" no fim do corpo; `docs/especificacao/09-matricula.md` (RF-MAT-01, RN-MAT-03, 04, 05, 07, 08, 09, 12), `03` (RN-LGP-01), `04` (RN-GRA-09), `08` (RN-ALU-06, RN-RSP-06), `docs/plano-de-sprints.md`. Branch: `item/67-nova-matricula-em-etapas`. Parte do que a #63 (matrícula no servidor), a #54 (cadastro e responsáveis) e a #65 (conceder benefício) já entregaram: `Enrollments.add`, `POST /students`, `GET /students/lookup`, `BenefitWrite.grant`.

O item tem duas partes, construíveis em ordem; a numeração de R, CA, T e D é única no arquivo. O PR e o commit são um só (`#67: RF-MAT-01 · Nova matrícula em etapas`, `Closes #67`), no fim da Parte 2.

| Parte | Entrega | Requisitos | Tarefas |
| --- | --- | --- | --- |
| 1 | Servidor: prévia, conclusão atômica, graduação de origem, convite | R1 a R10 | T1 a T6 |
| 2 | Tela: assistente de cinco etapas | R11 a R15 | T7 a T10 |

## 1. Objetivo

Dar à gestão (Coordenador e Administrador) o fluxo "Nova matrícula" em cinco etapas: aluno e responsáveis, modalidades (com benefício e graduação de origem), vencimento e primeira cobrança, resumo com valores e conclusão. Concluir grava a matrícula, o benefício e a graduação de origem numa transação só, deixa o aluno Ativo e dispara o convite do portal ao aluno adulto.

Fora do escopo: criar a cobrança do primeiro mês, a taxa e a segunda cobrança, e "Registrar pagamento agora" funcionando (Sprint 08, #71 e #75; aqui só se calcula, se mostra e se guarda a escolha, D3); avisos de faixa etária e capacidade (#68); aceite do termo, registro de conta/versão/data/hora/IP, uso de imagem e selo "Termo pendente" (#70, #85, D4); editor de termos (#70); aula experimental de origem (RN-MAT-10); débito antigo do retorno (RN-MAT-19, Sprint 08); importação e envio de convites em massa; tela de graduações e aviso ao aluno (#105, #106); encerrar modalidade (#69); notas de versão (Sprint 15).

## 2. Requisitos

**Parte 1 — Servidor**

- R1 (RF-MAT-01, RN-MAT-03): `POST /api/v1/students/{id}/enrollment/preview` devolve, sem gravar nada, o que o Resumo mostra: por modalidade, valor do plano, desconto do benefício e mensalidade resultante; por unidade, dia de vencimento, data da primeira cobrança (data da matrícula no fuso da unidade, com 5 dias de tolerância informados), taxa de matrícula (0 se a unidade não cobra ou se isentada), total da primeira cobrança e data sugerida da segunda cobrança. Quem chama precisa de `STUDENT_MANAGE` em cada unidade pedida.
- R2 (RN-MAT-05): a taxa de matrícula só entra se a unidade a ligou (`enrollment_fee_enabled`), vale o valor da unidade e é cobrada uma vez por aluno na unidade: não entra se o aluno já teve modalidade nessa unidade, salvo retorno de ex-aluno (RN-MAT-19), em que entra de novo. O Administrador pode isentar, com motivo de 3 a 200 caracteres; isenção sem motivo, ou em unidade que não cobra taxa, é recusada.
- R3 (RN-MAT-07): a data sugerida da segunda cobrança é a primeira data a pelo menos 15 dias da matrícula que cai no dia de vencimento da unidade; o Administrador pode ajustar para outra data entre 15 e 60 dias da matrícula (D5); fora disso, 422.
- R4 (RF-MAT-01, RN-MAT-04): `POST /api/v1/students/{id}/enrollment/conclude` valida e grava tudo ou nada: as modalidades (mesmas regras de `Enrollments.add`: unidade, turma, plano, dia de vencimento, limite de 10, retorno de ex-aluno), o benefício de cada modalidade que o pedido trouxer (mesmas regras de `BenefitWrite.grant`), a graduação de origem (R6) e a escolha da primeira cobrança por unidade (R5). Ao concluir, o aluno já está Ativo (situação calculada) e a resposta traz a matrícula e o resumo (R1).
- R5 (RN-MAT-05, RN-MAT-07; D3): a conclusão guarda, por unidade, a taxa a cobrar (ou a isenção com motivo) e a data da segunda cobrança escolhida, em `enrollment_start`, para a Sprint 08 gerar a cobrança; nenhuma cobrança nasce neste item.
- R6 (RN-GRA-09, RN-MAT-09): cada modalidade do pedido pode trazer a graduação de origem (nível da própria modalidade, grau de 0 ao máximo do nível, data não futura no fuso da unidade, opcional), gravada em `student_graduation` marcada como de origem, sem aviso ao aluno. Só vale em modalidade com graduação ligada. Com "Exigir comprovante da graduação de origem" ligado na modalidade, informar a graduação sem comprovante é recusado (422) e o comprovante se anexa antes (R7); sem a exigência, o comprovante é opcional, e o registro guarda se tem comprovante (a leitura mostra "com comprovante" ou "sem comprovante").
- R7: `POST /api/v1/students/{id}/enrollment/origin-proof` (multipart) guarda o comprovante (JPG, PNG, WebP ou PDF, até 5 MB) no armazenamento da organização e devolve `proofKey`; a conclusão só aceita `proofKey` que exista na pasta da organização e do aluno.
- R8 (RN-MAT-08, RN-RSP-06): ao concluir, o aluno adulto com e-mail e sem conta ativa na organização recebe o convite do portal, enviado depois do commit; a falha do e-mail não desfaz a matrícula e fica registrada. Aluno menor não recebe convite próprio e os responsáveis não recebem outro e-mail na conclusão, porque já foram convidados ao serem ligados (RN-RSP-06, D6). Retorno de ex-aluno com conta ativa não recebe convite novo.
- R9 (RN-MAT-12, RN-LGP-01; D4): a conclusão não registra aceite de termo; a resposta traz `termPending: true` (não há aceite). O registro do aceite fica na #85.
- R10 (regras transversais): `STUDENT_MANAGE` conferido por unidade, aluno de outra organização é 404, Professor 403; toda conclusão grava na auditoria (matrícula, cada modalidade, benefício, graduação de origem, taxa isentada com motivo), sem CPF nem comprovante no conteúdo; repetir a conclusão não duplica nada (a segunda chamada recebe 409 `CONFLICT` pela modalidade já ativa e nenhum dado muda).

**Parte 2 — Tela**

- R11 (RF-MAT-01): a rota `/alunos/nova-matricula` (Coordenador e Administrador) abre o assistente com cinco etapas e indicador de progresso; "Nova matrícula" aparece na lista de alunos e, na ficha de aluno sem matrícula ou ex-aluno, como ação da aba "Matrícula e modalidades" (abre o assistente já no aluno, a partir da etapa 2).
- R12 (RF-MAT-01, RN-ALU-06, RN-MAT-19): etapa 1: busca por CPF; se o aluno existe, mostra só nome e foto para confirmar e a situação (matrícula nova, nova modalidade ou retorno); se não existe, mostra o formulário de dados do aluno e responsáveis (reaproveita `POST /students`, a validação e os duplicados da #54). Aluno com CPF existente de outra unidade aparece só com nome e foto.
- R13 (RF-MAT-01, RN-MAT-02, RN-MAT-09, RN-MAT-10 fora): etapa 2: uma ou mais modalidades (até 10 ativas no total), cada uma com unidade, turma principal, outros horários, plano, benefício opcional (tipo, valor, motivo, decidido por, validade) e, quando a modalidade tem graduação ligada, graduação de origem (nível, grau, data, comprovante).
- R14 (RF-MAT-01, RN-MAT-03, 05, 07): etapa 3: um dia de vencimento por unidade, entre os dias permitidos dela; taxa de matrícula com valor e caixa de isenção com motivo quando a unidade cobra; data sugerida da segunda cobrança, editável. Etapa 4: resumo da prévia (R1) com "Concluir matrícula" (desabilitado durante o envio). Etapa 5: conclusão com o aluno Ativo, o estado do convite (enviado, ou "sem convite" para menor) e as ações "Registrar pagamento agora" (desabilitada com a dica "disponível com as cobranças" até a #75, D3) e "Aluno paga pelo portal" (volta para a ficha).
- R15 (regras de tela do AGENTS): cada etapa e a tela têm os quatro estados (carregando, vazio, erro, sem permissão), funcionam a partir de 360 px, passam no axe nos dois temas, usam textos de `pt-BR.json`, e os handlers MSW batem com o `docs/openapi.yaml`.

## 3. Critérios de aceite

- CA1 (R1): dado aluno sem matrícula, plano de R$ 200,00 e benefício percentual de 10%, quando pede a prévia, então a mensalidade é R$ 180,00 em centavos, a primeira cobrança vence na data de hoje no fuso da unidade e nada foi gravado (nenhuma linha em `enrollment`, `benefit`, `enrollment_start`, nem auditoria).
- CA2 (R2): dado unidade com taxa de R$ 50,00, quando o aluno nunca teve modalidade ali, então a taxa é 5000; quando já teve (matrícula encerrada) e a conclusão é um retorno, então a taxa entra de novo; quando a unidade não cobra, a taxa é 0.
- CA3 (R2): dado Administrador que isenta a taxa, quando manda o motivo "bolsa da turma", então a taxa vira 0 e o motivo vai para `enrollment_start` e para a auditoria; sem motivo, ou em unidade sem taxa, a resposta é 422 com o campo `feeExemptReason`.
- CA4 (R3): dado dia de vencimento 10, quando a matrícula é em 20/10 ou 25/10, então a segunda cobrança sugerida é 10/11 (a primeira data com dia 10 a pelo menos 15 dias); matrícula em 28/10 sugere 10/12 (10/11 fica a menos de 15 dias). Data escolhida a menos de 15 dias ou a mais de 60 dias da matrícula: 422 `secondDueOn`.
- CA5 (R4, R10): dado pedido com duas modalidades em unidades diferentes, uma delas com plano de outra unidade, quando conclui, então a resposta é 422 e não existe matrícula, modalidade, benefício, graduação nem `enrollment_start` (tudo ou nada).
- CA6 (R4): dado pedido válido com benefício em uma das modalidades, quando conclui, então existem a matrícula, as modalidades, o benefício ativo e o `enrollment_start` de cada unidade, `GET /students/{id}/enrollment` mostra situação Ativo e o selo Novo vale.
- CA7 (R4): dado ex-aluno (todas as modalidades encerradas), quando conclui com uma modalidade nova, então a mesma matrícula é reaberta (`reopened_at`) com o histórico e a graduação anteriores, e a taxa segue R2.
- CA8 (R4): dado aluno com 9 modalidades ativas, quando conclui com 2 novas, então 422 `modalities` e nada é gravado; modalidade já ativa dá 409; dia de vencimento fora dos permitidos ou diferente do que o aluno já tem na unidade dá 422 (RN-MAT-03).
- CA9 (R6): dado modalidade com graduação ligada e a exigência de comprovante, quando conclui com graduação de origem sem `proofKey`, então 422 `origin.proofKey` e nada é gravado; com `proofKey` válido, grava com `has_proof` verdadeiro e a leitura diz "com comprovante".
- CA10 (R6): dado modalidade sem a exigência, quando conclui com graduação de origem sem comprovante, então grava marcada "de origem" com `has_proof` falso; nível de outra modalidade, grau acima do máximo, data futura, ou graduação em modalidade com graduação desligada: 422 e nada é gravado; nenhum aviso ao aluno nem e-mail de graduação é enviado.
- CA11 (R7): dado envio de comprovante, quando o tipo não é JPG, PNG, WebP ou PDF, ou passa de 5 MB, então 422; `proofKey` de outra organização ou de outro aluno na conclusão: 422 e nada é gravado.
- CA12 (R8): dado aluno adulto com e-mail, quando conclui, então um convite do portal é emitido e enviado depois do commit; dado e-mail que falha no envio, então a matrícula continua gravada, a falha é registrada e a resposta informa convite não enviado; dado aluno menor com dois responsáveis, então nenhum e-mail de convite sai na conclusão e a resposta diz "sem convite próprio"; dado ex-aluno com conta ativa, então nenhum convite novo.
- CA13 (R9): dado qualquer conclusão, então a resposta traz `termPending: true` e não existe registro de aceite (RN-MAT-12 fica com a #85).
- CA14 (R10): dado Professor, Aluno ou Responsável, quando chama preview, conclude ou origin-proof, então 403; Administrador com unidade fora do alcance em qualquer modalidade: 403 e nada gravado; aluno de outra organização: 404; sem sessão: 401.
- CA15 (R10): dado conclusão bem-sucedida, então a auditoria tem `student.enrolled` (ou `student.enrollment_reopened`), `student.modality_added` por modalidade, `benefit.granted`, `student.origin_graduation_registered` e, havendo isenção, `enrollment.fee_exempted` com o motivo, e nenhuma entrada contém CPF, e-mail ou chave do comprovante.
- CA16 (R10): dado que a conclusão é enviada duas vezes seguidas, então a segunda recebe 409 e a contagem de linhas em `enrollment_modality`, `benefit` e `enrollment_start` não muda.
- CA17 (R10): dado Administrador da organização A, quando usa o id de um aluno, turma, plano, unidade ou nível da organização B no pedido, então 404 (RLS e filtro no código) e nada é gravado.
- CA18 (R11): dado Coordenador ou Administrador, quando abre `/alunos/nova-matricula`, então vê o assistente; Professor vê o estado "sem permissão"; na ficha de aluno sem matrícula a ação "Nova matrícula" abre o assistente com o aluno já escolhido na etapa 2.
- CA19 (R12): dado CPF que existe, quando busca, então a etapa mostra só nome, foto e a situação (nova, nova modalidade ou retorno) e pede confirmação; dado CPF novo, então mostra o formulário e o cadastro cria o aluno; CPF duplicado mostra o existente (RN-ALU-04); menor sem responsável não avança.
- CA20 (R13): dado modalidade com graduação ligada e exigência de comprovante, quando o usuário informa a graduação de origem, então o envio do arquivo é obrigatório na tela; dado modalidade sem graduação, então o bloco de origem não aparece; a 11ª modalidade não pode ser adicionada.
- CA21 (R14): dado unidade com taxa ligada, quando o usuário vê a etapa 3, então aparecem o valor da taxa e a caixa "Isentar taxa" que exige o motivo; dado unidade sem taxa, então nada de taxa; o dia de vencimento só oferece os dias da unidade e a segunda cobrança vem sugerida e editável.
- CA22 (R14): dado resumo, quando clica "Concluir matrícula", então o botão fica desabilitado até a resposta; sucesso leva à etapa 5 com "Aluno Ativo" e o estado do convite; erro 422 volta à etapa do campo com a mensagem e preserva o que foi digitado; erro de rede mostra o estado de erro com "Tentar de novo".
- CA23 (R14): dado a etapa 5, então "Registrar pagamento agora" está desabilitada com a dica e "Aluno paga pelo portal" leva à ficha do aluno.
- CA24 (R15): dado cada etapa, então vale carregando, vazio (sem modalidade oferecida, sem plano ou sem turma ativa com mensagem que diz o que fazer), erro e sem permissão; sem estouro horizontal em 360 px; sem violações do axe nos temas claro e escuro; nenhum texto literal fora de `pt-BR.json` (`npm run lint`).
- CA25 (R15): dado o contrato, então `OpenApiContractTest` e `ContractRoutesTest` passam e os handlers MSW devolvem os mesmos formatos de preview, conclusão e comprovante.

## 4. Plano técnico

- Dados (V36, a criar): `student_graduation` (id, organization_id, student_id, modality_id, level_id, degree, achieved_on nullable, origin boolean, proof_file nullable, has_proof, registered_by_account_id, created_at), com FK composta por organização para `student` e `graduation_level`, RLS e FORCE como as demais, sem DELETE para o papel de execução (RN-ALU-08: graduações nunca são excluídas); a #105 e a #106 reaproveitam a tabela. `enrollment_start` (id, organization_id, enrollment_id, unit_id, concluded_at, fee_cents, fee_exempt_reason nullable, second_due_on, created_at), com RLS e sem DELETE; é o que a #71 lê para gerar a primeira cobrança. Recuperação documentada no cabeçalho da migração (drop das duas tabelas, nenhum dado fora delas). Seed `infra/seed/NN-matricula-inicio.sql` com `on conflict do nothing`, aplicado pelo `SeedDataIT`.
- API: três rotas novas em `StudentController`, todas Coordenador e Administrador: `POST /students/{id}/enrollment/preview`, `POST /students/{id}/enrollment/conclude` (corpo: `modalities[]` com os campos de `Enrollments.ModalityInput` mais `benefit` e `origin`, e `units[]` com `unitId`, `feeExempt`, `feeExemptReason`, `secondDueOn`), `POST /students/{id}/enrollment/origin-proof` (multipart, devolve `proofKey`). Erros no padrão atual: 400 formato, 401, 403, 404, 409 (modalidade já ativa), 422 regra. Contrato novo em `docs/openapi.yaml`. A lista `GET /students/lookup` e `GET /students/{id}/enrollment` não mudam.
- Interface: `features/students/enrollment-wizard/` (a criar) com o assistente de cinco etapas, e a rota `/alunos/nova-matricula` em `App.tsx` dentro do grupo de `COORDINATOR` e `ADMINISTRATOR`. Entradas: botão na `StudentsPage` e ação em `RecordEnrollment`. Estado do assistente no componente (não em armazenamento do navegador). Textos em `i18n/pt-BR.json`.
- Segurança: perfil por rota e `Permissions.grant(who, STUDENT_MANAGE, unitId)` por unidade; ids do pedido sempre filtrados pela organização, além da RLS; comprovante guardado na pasta da organização com nome gerado no servidor, tipo e tamanho conferidos, URL de leitura só assinada e temporária; auditoria sem CPF, e-mail nem chave de arquivo; falha de e-mail registrada sem perder a operação.
- Arquivos a alterar (vistos): `backend/src/main/java/br/com/ctcore/student/Enrollments.java` (extrair `doAdd` para uso da conclusão e acrescentar a conclusão e a prévia, ou classe nova ao lado se passar de ~450 linhas), `student/StudentController.java`, `identity/InviteService.java` (método `inviteStudent` no padrão de `inviteGuardian`: cria dentro da transação e envia depois do commit), `docs/openapi.yaml`, `frontend/src/App.tsx`, `frontend/src/features/students/StudentsPage.tsx`, `RecordEnrollment.tsx`, `ModalityPickers.tsx` (reuso de `PickSelect` e dos hooks de turma e plano), `frontend/src/mocks` (handlers de matrícula), `frontend/src/i18n/pt-BR.json`. A criar: `V36__student_graduation_enrollment_start.sql`, `student/EnrollmentConclusion.java` (conclusão e prévia), `student/OriginProof.java`, testes `EnrollmentConclusionIT`, `EnrollmentPreviewTest`, `OriginProofIT`, os arquivos do assistente e seus testes, `infra/seed/NN-matricula-inicio.sql`.
- Reuso: `Enrollments` (validação de modalidade, turma, plano e vencimento, limite de 10, retorno), `BenefitWrite.grant` e `Benefits.monthlyValueCents` (valor do benefício), `StudentRegistration`/`POST /students` e `GET /students/lookup` na etapa 1, `FileStorage` (como a foto do aluno em `StudentEdit`), `AuditService`, `Clock`, `unit_settings.due_days` e `enrollment_fee_*`, `Graduation.get` (nível e exigência de comprovante), `PickSelect`, `useClassChoices`, `usePlanChoices`, `GrantBenefitDialog` (campos do benefício), componentes de estado (`LoadingState`, `ErrorState`, `ForbiddenState`).
- Riscos: (1) a conclusão chama `Enrollments.add`/`BenefitWrite.grant` dentro de uma transação só; se alguém mudar essas classes para abrir transação própria isolada, o "tudo ou nada" quebra (CA5 e CA16 pegam); (2) `Enrollments` já tem 408 linhas, por isso a conclusão nasce em classe própria; (3) cobrança ausente até a #71: a matrícula concluída não gera cobrança nenhuma até lá, o que é aceito dentro da Sprint 07 e fica visível na tela (D3); (4) abandonar o assistente depois da etapa 1 deixa um aluno "Sem matrícula" (D1); (5) o convite do aluno adulto não liga `student.account_id` porque isso ocorre no aceite (Sprint 10).

## 5. Tarefas

**Parte 1 — Servidor**

- [x] T1: migração V36 com `student_graduation` e `enrollment_start` (RLS, sem DELETE, recuperação no cabeçalho) e seed — não cobre critério: base de dados de R5 e R6; prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test=MigrationIT,SeedDataIT` e teste de isolamento das duas tabelas entre organizações.
- [x] T2: prévia (`EnrollmentConclusion.preview`) com valores, taxa (retorno, isenção), primeira cobrança e segunda sugerida, e a rota — cobre CA1, CA2, CA3, CA4 — prova: `EnrollmentPreviewTest` (regras de data e taxa, unitário) e `EnrollmentConclusionIT` (nada gravado).
- [x] T3: comprovante da origem (`OriginProof`, rota multipart) e validação da graduação de origem (nível, grau, data, exigência, `has_proof`) — cobre CA9, CA10, CA11 — prova: `OriginProofIT`.
- [x] T4: conclusão atômica (`EnrollmentConclusion.conclude`): modalidades via `Enrollments`, benefício via `BenefitWrite.grant`, graduação de origem, `enrollment_start`, auditoria, `termPending`, e a rota; 409 na repetição — cobre CA5, CA6, CA7, CA8, CA13, CA15, CA16 — prova: `EnrollmentConclusionIT`.
- [x] T5: convite do portal ao aluno adulto na conclusão (`InviteService.inviteStudent`, envio depois do commit, falha registrada sem desfazer) — cobre CA12 — prova: `EnrollmentConclusionIT` com o e-mail simulado falhando e `InviteServiceTest`.
- [x] T6: segurança e contrato: testes de perfil e de isolamento entre organizações para as três rotas, rota nova no `docs/openapi.yaml` — cobre CA14, CA17, CA25 (servidor) — prova: `EnrollmentConclusionIT`, `OpenApiContractTest,ContractRoutesTest`, `./backend/mvnw -f backend/pom.xml verify` com a cobertura mínima.

**Parte 2 — Tela**

- [x] T7: tipos, hooks e handlers MSW de preview, conclusão e comprovante, batendo com o OpenAPI — cobre CA25 — prova: `enrollment-wizard.msw.test.ts` e `npm run typecheck`.
- [x] T8: assistente: casca, rota `/alunos/nova-matricula`, indicador, etapas 1 e 2 (busca por CPF, confirmação, cadastro e responsáveis, modalidades, benefício, graduação de origem e comprovante) — cobre CA18, CA19, CA20 — prova: testes de tela do assistente.
- [x] T9: etapas 3, 4 e 5 (vencimento por unidade, taxa e isenção, segunda cobrança, resumo, conclusão, ações finais) — cobre CA21, CA22, CA23 — prova: testes de tela do assistente.
- [x] T10: entradas na lista e na ficha, quatro estados, 360 px, axe nos dois temas, textos no `pt-BR.json`, ponta a ponta `@critical` da matrícula — cobre CA18, CA24 — prova: `npm run lint && npm run typecheck && npm run build && npm run test:cov && npm run e2e`.

## 6. Decisões

- D1: a etapa 1 cria o aluno na hora (reaproveitando `POST /students`) e a conclusão grava o resto de uma vez. Motivo: o cadastro tem validação, duplicados e responsáveis próprios (#54) que não devem ser reescritos; aluno "Sem matrícula" já é um estado previsto. Alternativa descartada: uma rota única que cria aluno e matrícula juntos, que duplicaria a validação do cadastro.
- D2: o benefício e a graduação de origem entram no pedido de conclusão, não em chamadas separadas. Motivo: sem isso a matrícula pode ficar gravada com o benefício faltando; o benefício reusa a regra de `BenefitWrite.grant` (a #65 não muda). Alternativa descartada: o assistente chamar `POST /benefits` depois, sem transação única.
- D3 (muda o comportamento): enquanto a #71 não existe, concluir a matrícula não cria cobrança: guarda a taxa, a isenção e a data da segunda cobrança em `enrollment_start`, e "Registrar pagamento agora" fica desabilitada com a dica. Motivo: a cobrança, o recibo e o pagamento no balcão são da Sprint 08 (a #71 lista "primeira cobrança na matrícula"); a Sprint 07 só vai para `main` com a 08 revisada junto ou depois, conforme o dono decidir na revisão. Alternativa descartada: criar uma cobrança provisória aqui, que seria jogada fora.
- D4 (muda o comportamento): RN-MAT-12 e RN-LGP-01 (registro do aceite com conta, versão, data, hora e IP, e o uso de imagem opcional) e o selo "Termo pendente" ficam na #85, que tem o portal e as versões do termo da #70; esta entrega só garante que o aluno nasce sem aceite (`termPending: true`). É a decisão pedida no encaixe da issue: o selo não é entregue aqui. Alternativa descartada: criar a tabela de aceite sem versão de termo nem tela que a use.
- D5: a segunda cobrança pode ser ajustada entre 15 e 60 dias da matrícula. O limite de 60 dias é suposição (a especificação só fixa os 15 dias mínimos); se o dono quiser outro teto ou nenhum, muda só a validação de `secondDueOn`.
- D6: na conclusão, só o aluno adulto recebe convite novo; os responsáveis já o receberam ao serem ligados (RN-RSP-06: "sem esperar o fim da matrícula"), então a conclusão não reenvia. Isso cumpre "para cada responsável" da RN-MAT-08 sem e-mail duplicado. Alternativa descartada: reenviar a todos na conclusão.
- D7: a taxa de matrícula conta como "já cobrada" quando o aluno teve alguma modalidade na unidade (qualquer situação), até a Sprint 08 ter a cobrança real para consultar; o retorno de ex-aluno cobra de novo (RN-MAT-19). Se a #71 preferir ler as cobranças, troca só esse ponto.
- D8: o comprovante da graduação de origem é enviado antes (`origin-proof`) e referenciado por `proofKey` na conclusão, em vez de um corpo multipart único. Motivo: o pedido de conclusão tem muitas modalidades e campos; um envio por arquivo mantém o JSON simples. Alternativa descartada: multipart com o JSON dentro.
- Suposição: "Administrador pode isentar" vale também para o Coordenador (que tem tudo do Administrador). Se só o Administrador puder, muda a checagem de perfil na isenção.
- Suposição: o fuso da "data da matrícula" é o da unidade de cada modalidade, como a #63 e a #65 fazem; se a matrícula em duas unidades de fusos diferentes exigir uma data só, o resumo passa a mostrar a data por unidade (já é por unidade).

## Construção

- T1 — arquivos: `backend/src/main/resources/db/migration/V36__student_graduation_enrollment_start.sql`, `infra/seed/16-matricula-inicio.sql`, `backend/src/test/java/br/com/ctcore/db/SeedDataIT.java` (limpeza e teste do seed) — prova: `./backend/mvnw -f backend/pom.xml verify` (MigrationIT, SeedDataIT e `EnrollmentConclusionIT.isolation`: RLS forçada e sem DELETE nas duas tabelas) → 519 testes, 0 falhas
- T2 — arquivos: `student/EnrollmentConclusion.java` (prévia, `suggestSecond`, `fee`), `StudentController.java`, `EnrollmentPreviewTest.java`, `EnrollmentConclusionIT.java` (`preview`, `fee`, `secondDue`) — prova: `verify` → CA1 a CA4 passam; a prévia não grava nada (`nothingWritten` zero)
- T3 — arquivos: `student/OriginProof.java`, `ApiExceptionHandler.java` (arquivo acima de 5 MB nessa rota é 422), `EnrollmentConclusionIT.java` (`origin`, `originRules`) — prova: `verify` → CA9, CA10, CA11 passam
- T4 — arquivos: `student/EnrollmentConclusion.java` (conclusão), `Enrollments.java` (`checkInputs`, `addLocked`, `Added`), `benefit/BenefitWrite.java` (`type` e `value` públicos), `EnrollmentConclusionIT.java` (`atomic`, `conclude`, `reopen`, `limits`) — prova: `verify` → CA5 a CA8, CA13, CA15, CA16 passam
- T5 — arquivos: `identity/InviteService.java` (`inviteStudent`, `inviteInside`), `EnrollmentConclusionIT.java` (`invite`, com `AccountMailer` falhando por `@MockitoSpyBean`) — prova: `verify` → CA12 passa
- T6 — arquivos: `docs/openapi.yaml` (3 rotas e 10 esquemas), `EnrollmentConclusionIT.java` (`profiles`, `atomic`) — prova: `OpenApiContractTest,ContractRoutesTest` passam; `verify` com JaCoCo → BUILD SUCCESS (519 testes, cobertura mínima respeitada)
- T7 — arquivos: `frontend/src/features/students/enrollment-wizard/{types,api,request}.ts`, `frontend/src/mocks/handlers.ts` (prévia, conclusão, comprovante; `addMockModalities` extraída da rota de modalidades), `frontend/src/mocks/enrollment-wizard.msw.test.ts` — prova: `npx vitest run src/mocks/enrollment-wizard.msw.test.ts` → 7 testes; `npm run typecheck` → sem erros
- T8 — arquivos: `enrollment-wizard/{EnrollmentWizardPage,StepStudent,StepModalities}.tsx`, `App.tsx` (rota `/alunos/nova-matricula`) — prova: `EnrollmentWizardPage.test.tsx` (CA18 a CA20) → passa
- T9 — arquivos: `enrollment-wizard/{StepBilling,StepSummary,StepDone}.tsx` — prova: `EnrollmentWizardPage.test.tsx` (CA21 a CA23) → passa
- T10 — arquivos: `students/NewEnrollmentLink.tsx`, `StudentsPage.tsx`, `RecordEnrollment.tsx` (+ teste ajustado com `MemoryRouter`), `i18n/pt-BR.json` (bloco `enrollmentWizard`), `e2e/matricula.spec.ts` (`@critical` da nova matrícula) — prova: `npm run lint && npm run typecheck && npm run build && npm run test:cov && npm run e2e` → lint, tipos e build limpos; 103 arquivos e 928 testes passam (cobertura 92,8% das linhas); e2e `@critical` 33 passam

Regressão: `./backend/mvnw -f backend/pom.xml verify` → 519 testes, 0 falhas, JaCoCo OK; em `frontend/`: `npm run lint`, `typecheck`, `build`, `test:cov`, `e2e` → todos verdes (antes da mudança todos os testes anteriores já passavam; nenhuma falha anterior).
Não rodou: `npm run e2e:full`, gitleaks e OSV-Scanner (só no CI); imagens Docker do projeto não foram reconstruídas (nenhum Dockerfile mudou).
Desvios:
- A prévia valida unidade, alcance, plano, benefício, dia de vencimento, taxa e segunda cobrança; turma, modalidade oferecida e limite de 10 só na conclusão (que refaz tudo antes de gravar).
- `student_graduation.level_id` usa chave estrangeira simples para `graduation_level(id)` (a tabela não tem `unique (organization_id, id)`); a organização é filtrada no código e pela RLS. Efeito colateral registrado como P2 no backlog.
- Arquivo acima de 5 MB no comprovante: o limite do multipart recusava antes do controlador com 400; o tratador passou a dar 422 só nessa rota, como pede o CA11.
- Auditoria extra `student.origin_proof_uploaded` (envio do comprovante altera dado), sem a chave do arquivo.
- Resposta da conclusão ganhou `inviteStatus` (`SENT`, `FAILED`, `MINOR`, `EXISTING_ACCOUNT`, `NO_EMAIL`); a falha do envio fica no log do servidor (sem e-mail nem código) e no registro do próprio serviço de e-mail.
- Etapa 1 da tela cadastra um responsável (o financeiro) para o aluno menor; o segundo responsável se liga pela ficha.
- A etapa 3 lê os dias e a taxa da unidade em `GET /units/{id}`; sem esse acesso, oferece os dias 1 a 28 e o servidor decide (o resumo mostra o resultado).
- Comprovante enviado e depois abandonado fica no armazenamento sem referência (sem limpeza automática neste item).
Não é do item: nenhum.

## Verificação

### Rodada 1 — NAO_CONFERIDO — 2026-10-21, estado 4e281e5d8f3338f665da9f1e0bfad6d992e60671

| Critério | Status | Evidência |
|---|---|---|
| CA1 a CA4 | PASSOU | `EnrollmentConclusionIT` (`preview`, `fee`, `secondDue`) e `EnrollmentPreviewTest` dentro do `verify` |
| CA5 a CA8 | PASSOU | `EnrollmentConclusionIT` (`atomic`, `conclude`, `reopen`, `limits`) |
| CA9 a CA11 | PASSOU | `EnrollmentConclusionIT` (`origin`, `originRules`) |
| CA12 | PASSOU | `EnrollmentConclusionIT.invite` (envio, falha com `AccountMailer` espionado, menor, conta existente) |
| CA13 a CA17 | PASSOU | `EnrollmentConclusionIT` (`conclude`, `profiles`, `atomic`, `isolation`); leitura de `EnrollmentConclusion`: `termPending` sempre true, auditoria sem CPF/e-mail/chave |
| CA18 a CA23 | PASSOU | `EnrollmentWizardPage.test.tsx` (testes nomeados por CA); e2e `@critical` 33 passam |
| CA24 | NÃO CONFERIDO | o axe nos dois temas só roda nas etapas 1, 2 e 3 (`EnrollmentWizardPage.test.tsx`, teste "nas etapas 1, 2 e 3"); etapas 4 e 5 sem axe; estouro horizontal a 360 px não é medido em lugar nenhum (jsdom e e2e sem `scrollWidth`/viewport); lint e quatro estados da etapa 2 conferidos |
| CA25 | PASSOU | `OpenApiContractTest` e `ContractRoutesTest` dentro do `verify`; `enrollment-wizard.msw.test.ts` (7 testes) |

Comandos:
- `./backend/mvnw -f backend/pom.xml verify` → 519 testes, 0 falhas, BUILD SUCCESS (JaCoCo respeitado)
- `npm run lint && npm run typecheck && npm run build && npm run test:cov` (frontend) → limpos; 103 arquivos, 928 testes; linhas 96,49%
- `npm run e2e` → 33 passam
- Não rodado: `e2e:full`, gitleaks, OSV-Scanner (só CI)

Achados:
- obrigatório (conferência), código, `frontend/src/features/students/enrollment-wizard/EnrollmentWizardPage.test.tsx` (teste "CA24 ... etapas 1, 2 e 3") — esperado: axe nos dois temas e sem estouro a 360 px em cada etapa (CA24); observado: etapas 4 (resumo) e 5 (conclusão) sem axe e nenhuma checagem de estouro horizontal; corrigir: estender o teste de axe às etapas 4 e 5 e acrescentar uma checagem de 360 px (e2e com `setViewportSize` e `scrollWidth <= innerWidth`, ou equivalente) para o assistente.
- sugestão, código — tarefas T3/T5 citam `OriginProofIT` e `InviteServiceTest`, que não existem; a cobertura está em `EnrollmentConclusionIT` (cobre os CAs, só o nome diverge).
- sugestão, código, `EnrollmentConclusion.compute` — pedido de isenção em unidade que cobra mas em que a taxa padrão já é 0 (aluno já teve modalidade ali) é aceito em silêncio sem gravar motivo; considerar 422 ou aviso.

### Correção da rodada 1
- CA24 (obrigatório): axe nos dois temas estendido às etapas 4 e 5 e checagem de estouro horizontal a 360 px em cada etapa — arquivos: `frontend/src/features/students/enrollment-wizard/EnrollmentWizardPage.test.tsx` (teste "CA24 ... nas cinco etapas"), `frontend/e2e/matricula.spec.ts` (`setViewportSize` 360 e `scrollWidth <= innerWidth` nas etapas 1 a 5) — prova: `npm run lint && npm run typecheck && npm run build && npm run test:cov && npm run e2e` → limpos; 103 arquivos, 928 testes passam; e2e `@critical` 33 passam
- Sugestões (nomes `OriginProofIT`/`InviteServiceTest` e isenção silenciosa com taxa 0): opcionais, não atendidas.
Regressão: back-end não alterado nesta correção (`verify` da construção: 519 testes, 0 falhas).

### Rodada 2 — OK — 2026-10-21, estado 969bc4a05161a0859b32555b3ec87ae5101fb82b

| Critério | Status | Evidência |
|---|---|---|
| CA1 a CA17 | PASSOU | mantido da rodada 1 (back-end sem alteração desde o estado 4e281e5d) |
| CA18 a CA23 | PASSOU | mantido da rodada 1; `npm run e2e` 33 passam |
| CA24 | PASSOU | teste "CA24 ... nas cinco etapas" (axe nos dois temas, etapas 1 a 5) e e2e `matricula.spec.ts` com viewport 360 e `scrollWidth <= innerWidth` nas etapas 1 a 5; `npm run lint` limpo |
| CA25 | PASSOU | mantido da rodada 1 |

Comandos:
- `npm run lint && npm run typecheck && npm run build && npm run test:cov` (frontend) → limpos; linhas 96,49%
- `npm run e2e` → 33 passam
- `vitest run src/features/students/enrollment-wizard` → passa
- Back-end não rodado de novo: `git diff` entre os estados mostra só 3 arquivos (e2e, teste da tela, este arquivo)
- Não rodado: `e2e:full`, gitleaks, OSV-Scanner (só CI)

Achados:
- Achado obrigatório da rodada 1 (CA24): resolvido.
- sugestão, código — mantidas as 2 sugestões da rodada 1 (nomes de testes citados nas tarefas; isenção silenciosa com taxa 0).

## Entrega

Assistente de nova matrícula em 5 etapas (`/alunos/nova-matricula`): aluno, modalidades (turma, plano, benefício, graduação de origem com comprovante), cobrança (vencimento, taxa e isenção, segunda cobrança), resumo e conclusão. No servidor: prévia sem gravação, envio do comprovante, conclusão atômica (matrícula, benefício, graduação e convite) e a migração V36 (`enrollment_start`, `student_graduation`).

Verificação: rodada 2 OK. Back-end `verify` com 519 testes e JaCoCo; front-end lint, typecheck, build, `test:cov` e e2e `@critical` verdes (axe nos dois temas e 360 px nas cinco etapas).

Decisões que importam: a conclusão ainda não cria cobrança (D3, depende da #71); termo e aceite ficam na #85 (D4); teto de 60 dias da segunda cobrança é suposição (D5). Limitações: comprovante abandonado fica no armazenamento sem limpeza; P2 no backlog (nível de graduação em uso, tratar na #105). Não rodados localmente: `e2e:full`, gitleaks, OSV-Scanner (CI).

Closes #67
