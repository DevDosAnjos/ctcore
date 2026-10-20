# 61 — RF-ALU-04 · Editar dados pessoais do aluno

Origem: issue #61 (Sprint 06); `docs/especificacao/08-alunos-e-responsaveis.md` (RF-ALU-04, RN-ALU-01 a 05, RN-ALU-07, RN-RSP-01), `03-auditoria-e-lgpd.md` (RN-LGP-02, RN-LGP-03). Branch: `item/61-editar-dados-pessoais-aluno`. Parte de `StudentRegistration` (#54: validações e duplicidade), `Students.detail`, `LogoImage` e `TeacherWrite.replacePhoto` (#44, foto), da ficha em painel (#60: `StudentRecordPanel`) e de `LogoCropDialog`/`TeacherFormDialog` (corte da foto no front).

## 1. Objetivo

A gestão (Coordenador e Administrador) edita os dados pessoais do aluno numa janela aberta pela ficha: nome, nascimento, endereço, CPF, e-mail, celular, sexo/gênero, como conheceu, contato de emergência e foto. O servidor aplica as mesmas validações do cadastro e a regra de duplicados (CPF repetido bloqueia; sem CPF, mesmo nome e nascimento avisam e deixam seguir se for outra pessoa). A ficha é uma só por organização, mesmo com treino em duas unidades (RN-ALU-05).

Fora do escopo: unidade de cadastro (não muda, D1); responsáveis (já na #60); edição pelo portal (RN-ALU-11, #89); matrícula, modalidades e a etapa 1 da matrícula `MatriculaAluno` (#67); anonimizar e baixar dados (RF-ALU-05); conta própria do aluno adulto e troca de e-mail da conta (#67, D4). Notas de versão: Sprint 15 (como a #54).

## 2. Requisitos

- R1: `PUT /api/v1/students/{id}` substitui os dados pessoais (mesmos campos de `StudentInput`, sem `registeredUnitId` nem `guardians`) e devolve `StudentDetail`; só Coordenador e Administrador (RF-ALU-04).
- R2: Obrigatórios e limites iguais ao cadastro: nome (2 a 80), nascimento no passado, endereço completo (CEP de 8 dígitos, logradouro, número, bairro, cidade, UF); adulto exige CPF, e-mail e celular; menor mantém CPF, e-mail e celular opcionais (RN-ALU-01).
- R3: CPF com dígitos verificadores e celular com DDD; formato inválido devolve 400 com o campo (RN-ALU-03).
- R4: CPF já usado por outro aluno da organização: 409 `STUDENT_CPF_EXISTS` com `id`, `name` e `photoUrl` do existente; o próprio aluno mantendo o CPF não conflita (RN-ALU-04).
- R5: Sem CPF, se nome ou nascimento mudaram e outro aluno tem o mesmo nome e nascimento: 409 `STUDENT_POSSIBLE_DUPLICATE`, repetível com `confirmDuplicate: true`; o aluno editado nunca conta como duplicado de si mesmo (RN-ALU-04, D2).
- R6: Menor de 18 nunca fica sem responsável: nascimento que torna o aluno menor exige que ele já tenha ao menos um responsável, senão 400 em `birthDate` (RN-RSP-01, D3).
- R7: Administrador só edita aluno cuja unidade de cadastro está no alcance dele; fora disso 404; Professor, Aluno e Responsável 403; aluno de outra organização 404 (RN-ALU-07, isolamento).
- R8: Contato de emergência é tudo ou nada (nome, parentesco, telefone), como no cadastro (RN-ALU-02).
- R9: Foto opcional: `PUT /students/{id}/photo` (multipart, JPG, PNG ou WebP, 512 x 512, até 5 MB) e `DELETE /students/{id}/photo`, com as mesmas permissões e alcance; o front corta em quadrado e reduz para 512 px antes de enviar (RN-ALU-02).
- R10: Toda alteração grava auditoria (`student.updated`, `student.photo_updated`, `student.photo_removed`) com antes e depois só dos campos não pessoais; CPF, e-mail, telefone, endereço e emergência vão como "alterado" (RN-LGP-02, RN-LGP-03); sem mudança, não grava.
- R11: Campo fora do contrato (como dado de saúde) é ignorado e não é gravado (RN-LGP-02).
- R12: Ficha: botão "Editar dados" no cabeçalho, visível só à gestão, abre a janela preenchida; ao salvar, a ficha e a lista mostram os dados novos. A janela tem os quatro estados (carregando os dados, erro, sem permissão e o formulário no lugar do vazio), funciona a partir de 360 px e passa no axe nos dois temas, com textos em pt-BR.
- R13: A janela mostra os erros por campo e, no 409, o aluno existente (nome e foto): "Abrir ficha" para CPF repetido e "É outra pessoa, salvar mesmo assim" para o aviso de nome e nascimento (RN-ALU-04).
- R14: OpenAPI e handlers MSW cobrem as rotas novas e batem com o servidor (RNF-32).

## 3. Critérios de aceite

- CA1 (R1, R2): dado aluno adulto, quando a gestão altera nome, endereço, e-mail e celular válidos, então 200 com os dados novos em `GET /students/{id}` e na ficha.
- CA2 (R2): dado adulto, quando CPF, e-mail ou celular vêm em branco, ou o nome tem 1 caractere, ou a data é futura, ou o CEP tem 7 dígitos, então 400 com o campo e nada é gravado.
- CA3 (R2): dado menor, quando salva sem CPF, e-mail e celular, então 200.
- CA4 (R3): quando o CPF tem dígito verificador errado ou o celular não tem DDD, então 400 em `cpf` ou `phone`.
- CA5 (R4): dado outro aluno com o CPF X, quando se edita este aluno para o CPF X, então 409 `STUDENT_CPF_EXISTS` com `id`, `name` e `photoUrl` do existente; salvar de novo o próprio CPF devolve 200.
- CA6 (R4): dois PUT simultâneos com o mesmo CPF novo em alunos diferentes: um 200 e o outro 409 (o índice único decide).
- CA7 (R5): dado aluno sem CPF, quando o nome muda para o de outro aluno de mesmo nascimento, então 409 `STUDENT_POSSIBLE_DUPLICATE`; com `confirmDuplicate: true`, 200. Mudar só o telefone de um aluno que já é "duplicado confirmado" devolve 200 sem aviso.
- CA8 (R6): dado menor com responsável, quando o nascimento passa a adulto, então 200 desde que CPF, e-mail e celular (R2) estejam preenchidos; dado adulto sem responsável, quando o nascimento passa a menor, então 400 em `birthDate`; com responsável, 200.
- CA9 (R7): Administrador de uma unidade que edita aluno cadastrado em outra: 404; Professor, Aluno e Responsável: 403; Coordenador de outra organização e id de outra organização: 404 (isolamento entre organizações e entre perfis nas três rotas).
- CA10 (R8): emergência só com nome: 400 apontando o campo que falta; os três em branco limpam o contato.
- CA11 (R9): envio de PNG 512 x 512 troca a foto, a URL aparece na ficha e o arquivo anterior sai do armazenamento; 300 x 300, ou arquivo que não é imagem, 400 em `file`; `DELETE` remove a foto; sem foto, a ficha mostra as iniciais.
- CA12 (R10): após cada operação existe o registro de auditoria com ator, aluno e unidade, sem CPF, e-mail, telefone nem endereço no antes e depois; PUT idêntico ao estado atual não cria registro; o histórico da ficha mostra a ação.
- CA13 (R11): corpo com `healthNotes` devolve 200 e o campo não é gravado nem aparece na resposta.
- CA14 (R12): dado gestão, quando clica "Editar dados", então a janela abre com os valores atuais; salvar fecha a janela e a ficha mostra os novos; Professor não vê o botão; falha de carga mostra erro com "Tentar de novo"; 403 mostra o estado sem permissão; axe sem violações nos temas claro e escuro, em 360 px.
- CA15 (R13): ao receber `STUDENT_CPF_EXISTS` a janela mostra o existente com "Abrir ficha" e mantém os dados digitados; ao receber `STUDENT_POSSIBLE_DUPLICATE` mostra "Parece ser [nome], já cadastrado" e o botão de confirmar reenvia com `confirmDuplicate`.
- CA16 (R14): `OpenApiContractTest` e `ContractRoutesTest` passam; testes MSW cobrem sucesso, 400 e 409 das três rotas.

## 4. Plano técnico

- Dados: não muda (colunas e índice único de CPF já existem em V29; `photo` já existe). Sem migração nova, sem seed novo.
- API: `PUT /students/{id}` (corpo `StudentUpdate`), `PUT /students/{id}/photo` (multipart `file`) e `DELETE /students/{id}/photo`; todos devolvem `StudentDetail`. Sessão e CSRF como as demais escritas. Erros: 400 (campo), 403, 404, 409 (`STUDENT_CPF_EXISTS`, `STUDENT_POSSIBLE_DUPLICATE`). As regras de `StudentRegistration.clean` são extraídas para um método comum, sem duplicar validação.
- Interface: `StudentEditDialog` aberto pelo botão no cabeçalho de `StudentRecordPanel`; carrega `GET /students/{id}`; campos como no cadastro; foto com `LogoCropDialog` e `checkLogoFile`; busca de CEP não entra. Textos em `frontend/src/i18n/pt-BR.json`.
- Segurança: `students.requireAccess` (STUDENT_MANAGE) e alcance por `registered_unit_id` do Administrador em `Students.detail` (404 fora); organização só da sessão, dentro de `OrganizationTransactions` (RLS); foto gravada antes da transação e descartada se ela falhar, como em `TeacherWrite`; só campos conhecidos são lidos (R11); auditoria sem dados pessoais.
- Arquivos a alterar (vistos): `backend/src/main/java/br/com/ctcore/student/StudentController.java`, `StudentRegistration.java`, `Students.java`, `docs/openapi.yaml`, `frontend/src/features/students/StudentRecordPanel.tsx`, `types.ts`, `frontend/src/mocks/handlers.ts`, `frontend/src/i18n/pt-BR.json`. A criar: `backend/src/main/java/br/com/ctcore/student/StudentEdit.java` (edição e foto), `backend/src/test/java/br/com/ctcore/student/StudentEditIT.java`, `frontend/src/features/students/StudentEditDialog.tsx` e `StudentEditDialog.test.tsx`, `frontend/src/mocks/studentEdit.msw.test.ts`.
- Reuso: `StudentRegistration.cpf`, `NewUnit.phone/email`, `Students.byCpf/byNameAndBirth`, `LogoImage`, `FileStorage`, `AuditService`, `LogoCropDialog`, `checkLogoFile`, componentes de `components/states` e `Modal`.
- Riscos: refatorar `clean` pode quebrar o cadastro (a `StudentRegistrationIT` cobre); cobertura mínima de 80% no pacote; corrida de CPF resolvida pelo índice único (`DuplicateKeyException`); o botão só aparece para a gestão, mas a barreira é o servidor.

## 5. Tarefas

- [x] T1: Extrair de `StudentRegistration` a validação dos dados pessoais para um método reutilizável (sem `registeredUnitId` e `guardians`) — não cobre critério: prepara R2 a R5 sem duplicar regra — prova: `./backend/mvnw -f backend/pom.xml test -Dtest='StudentRegistration*'` e `StudentRegistrationIT` continuam verdes.
- [x] T2: Criar `StudentEdit.update` e `PUT /students/{id}` com duplicados que ignoram o próprio aluno, regra do menor, alcance, auditoria e R11 — cobre CA1 a CA10, CA12, CA13 — prova: `StudentEditIT` e testes unitários das regras.
- [x] T3: Criar `PUT` e `DELETE /students/{id}/photo` com auditoria e descarte do arquivo anterior — cobre CA11, CA12 — prova: `StudentEditIT`.
- [x] T4: Publicar as três rotas e o schema `StudentUpdate` em `docs/openapi.yaml` — cobre CA16 — prova: `./backend/mvnw -f backend/pom.xml test -Dtest='OpenApiContractTest,ContractRoutesTest'`.
- [x] T5: Handlers MSW das três rotas, com 400 e 409 — cobre CA16 — prova: `studentEdit.msw.test.ts` via `npm test`.
- [x] T6: `StudentEditDialog` com validação, foto e os quatro estados, e botão "Editar dados" na ficha, com textos em pt-BR — cobre CA14, CA15 — prova: `StudentEditDialog.test.tsx`, `StudentRecordPanel.test.tsx`, axe nos dois temas a 360 px.
- [x] T7: Regressão completa — não cobre critério: confirma cobertura e o que o CI exige — prova: `./backend/mvnw -f backend/pom.xml verify` e, em `frontend/`, `npm run lint && npm run typecheck && npm run build && npm run test:cov && npm run e2e`.

## 6. Decisões

- D1: A unidade de cadastro (`registeredUnitId`) não é editável aqui. Motivo: ela define o alcance do Administrador (RN-ALU-07) e a transferência pertence à matrícula (#63); mudar por aqui permitiria tirar o aluno do alcance de outro Administrador. Alternativa: campo editável só para o Coordenador.
- D2: O aviso de nome e nascimento só roda quando o CPF está vazio e nome ou nascimento mudaram. Motivo: não reperguntar a quem já foi confirmado como outra pessoa ao corrigir um telefone. Alternativa: sempre checar.
- D3 (muda o comportamento): Nascimento que torna o aluno menor exige responsável já vinculado (400); virar adulto mantém os responsáveis, e a decisão sobre eles fica com a gestão (RN-RSP-07). Alternativa: apagar vínculos, descartada pela RN-RSP-05 (vínculo só se desfaz com motivo).
- D4: O PUT altera só a tabela `student`. Se o aluno tiver conta própria (`account_id`), a troca de e-mail da conta fica para o convite do aluno adulto (#67); hoje nenhum caminho preenche `account_id`. Suposição: se já houver aluno com conta, o e-mail da ficha e o da conta podem divergir até a #67.
- D5: Sem controle de edição simultânea (a última gravação vale), como em `PUT /teachers/{id}`; a auditoria registra quem alterou. Alternativa: versão otimista, que o projeto ainda não usa.
- D6: Foto em rotas próprias (como `/teachers/{id}/photo`); a janela salva o cadastro primeiro e envia a foto depois, e se a foto falhar os dados ficam salvos e o envio pode ser repetido, como na #44.
- Suposição: o botão "Editar dados" aparece conforme o perfil da sessão (gestão), já que a ficha do Professor não traz CPF e endereço; se o front não tiver o perfil à mão, usa-se a presença de `cpf` no registro.

## Construção

- T1 — arquivos: `StudentRegistration.java` (método `personal(Input)` comum, `Clean` e `exists` visíveis no pacote), `Students.java` (`byNameAndBirth` ganhou o id a ignorar) — prova: `StudentRegistrationIT` (9) e `StudentRecordsTest` → verdes
- T2 — arquivos: `StudentEdit.java` (novo), `StudentController.java` (PUT `/students/{id}`), `StudentEditIT.java` (novo) — prova: `mvnw verify -Dit.test=StudentEditIT` → 9 testes, 0 falhas (CA1 a CA10, CA12, CA13; corrida de CPF com duas threads)
- T3 — arquivos: `StudentEdit.java` (`replacePhoto`, `removePhoto`), `StudentController.java` (PUT/DELETE `/students/{id}/photo`) — prova: `StudentEditIT.photo` e `permissionsAndIsolation` → verdes (CA11, CA12)
- T4 — arquivos: `docs/openapi.yaml` (três rotas e `StudentUpdate`) — prova: `mvnw test -Dtest='OpenApiContractTest,ContractRoutesTest'` → 4 e 3 testes, 0 falhas
- T5 — arquivos: `frontend/src/mocks/handlers.ts`, `frontend/src/mocks/studentEdit.msw.test.ts` — prova: `npx vitest run src/mocks/studentEdit.msw.test.ts` → 6 testes verdes (sucesso, 400 e 409)
- T6 — arquivos: `StudentEditDialog.tsx` e `.test.tsx` (novos), `StudentRecordPanel.tsx` (botão e janela, `onChanged`), `StudentsPage.tsx` (lista recarrega ao salvar), `useStudentRecord.ts`, `types.ts`, `pt-BR.json` — prova: `npx vitest run src/features/students` → 52 testes verdes, incluindo axe a 360 px nos dois temas
- T7 — regressão completa (abaixo)

Regressão: `./backend/mvnw -f backend/pom.xml verify` → 444 testes, 0 falhas, BUILD SUCCESS (JaCoCo incluso); em `frontend/`: `npm run lint` e `npm run typecheck` sem erro, `npm run build` ok, `npm run test:cov` → 87 arquivos, 803 testes verdes (linhas 97,02%), `npm run e2e` → 30 de 31 na primeira rodada; o `minha-conta.spec.ts` falhou enquanto o `verify` do back-end rodava em paralelo e passou sozinho (instável por carga, sem relação com o item).
Desvios: o rótulo do contato de emergência na janela virou "Nome do contato" e "Telefone do contato" (o rótulo "Nome" duplicado quebrava o acesso por rótulo e a leitura por leitor de tela); na ficha ficou como estava. A lista recarrega remontando `QueryPage` (filtros e página vêm da URL e são mantidos).
Não é do item: nenhum.

## Verificação

### Rodada 1 — OK — 2026-10-20, estado beb3025bc24e48fc29a9a52cf0324eb8aea3773f

| Critério | Status | Evidência |
|---|---|---|
| CA1 a CA10, CA12, CA13 | PASSOU | `StudentEditIT` 9 testes verdes (inclui corrida de CPF, isolamento e perfis); leitura de `StudentEdit.java` confere com R1 a R8, R10, R11 |
| CA11 | PASSOU | `StudentEditIT.photo` verde; `replacePhoto`/`removePhoto` descartam o arquivo anterior |
| CA14, CA15 | PASSOU | `vitest src/features/students src/mocks` 190 testes verdes, incluindo axe nos dois temas |
| CA16 | PASSOU | `OpenApiContractTest` 4 e `ContractRoutesTest` 3 verdes; `studentEdit.msw.test.ts` verde |

Comandos: `mvnw verify -Dit.test='StudentEditIT,StudentRegistrationIT'` + contrato + `StudentRecordsTest` → BUILD SUCCESS; `npm run lint` e `typecheck` sem erro; `npm run test:cov` → 87 arquivos, 803 testes verdes. `verify` completo do back-end e `e2e` não reexecutados (registrados na Construção).
Achados:
- sugestão, código, `StudentEdit.update` — `students.requireAccess` roda antes da validação, bom; nada a corrigir além de nota: auditoria de `student.updated` não cobre troca só de foto (tem ação própria), coerente com a especificação.

## Entrega

Gestão edita os dados pessoais do aluno (PUT `/students/{id}`) e a foto (PUT/DELETE `/students/{id}/photo`), com as mesmas regras do cadastro (CPF único por organização, menor com responsável), auditoria sem dados pessoais e isolamento por organização e perfil. Na ficha, botão "Editar dados" abre a janela preenchida; ao salvar, ficha e lista recarregam.

Verificação: `verify` do back-end (444 testes), lint, typecheck, build, `test:cov` (803 testes) e e2e verdes; axe nos dois temas a 360 px; contrato OpenAPI e MSW conferidos. Rodada 1 da verificação: OK.

Decisões e limitações: rótulos do contato de emergência na janela são "Nome do contato" e "Telefone do contato"; troca só de foto tem ação de auditoria própria. O teste e2e `minha-conta.spec.ts` é instável sob carga paralela, sem relação com o item.
