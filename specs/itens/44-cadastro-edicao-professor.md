# 44 — RF-PRO-03 · Cadastro e edição de professor

Origem: issue #44 (Sprint 04); `docs/especificacao/05-professores.md` (RF-PRO-03, RN-PRO-01, RN-PRO-03, RN-PRO-04, "Dados"), `01-organizacao-e-isolamento.md` e a linha `TEACHERS` da tabela de permissões. Branch: `item/44-cadastro-edicao-professor`. Parte do modelo `teacher` de #42 (`V23`, `Teachers`, `TeachersPage`) e do painel de #43 (`TeacherPanelPage`, botão Editar desativado).

## 1. Objetivo

A gestão (Coordenador e Administrador) cadastra um professor novo e edita um existente numa janela com todos os campos de "Dados": foto, nome, e-mail, telefone, unidades, modalidades que ensina com nível e grau de cada uma, apresentação com contador (até 300 caracteres) e aparecer no diretório (consentimento). Hoje a lista e o painel leem professores, mas só o seed os cria.

Fora do escopo: inativar e reativar (#45; a situação não se altera aqui), dar acesso e convite (#43), turmas (Sprint 05), uso público do consentimento (Sprints 18 e 20), ligar o cadastro a conta existente, notas de versão (Sprint 15, #142).

## 2. Requisitos

- R1 (RF-PRO-03, RN-PRO-01): `POST /api/v1/teachers` cria o professor da organização da sessão com `name` (2 a 80, sem espaços nas pontas), `email` (opcional, formato válido, até 254), `phone` (opcional, telefone brasileiro como em `NewUnit`, guardado só com dígitos), `bio` (opcional, até 300), `directoryConsent`, `consentDeclaredByManagement`, `unitIds` (ao menos uma) e `modalities`; nasce `ACTIVE`, sem conta; responde 201 com `TeacherDetail`.
- R2 (RF-PRO-03): `PUT /api/v1/teachers/{id}` substitui os mesmos campos (unidades e modalidades como conjunto completo) e responde 200 com `TeacherDetail`; não altera situação nem conta; permitido também em professor inativo.
- R3 (RN-PRO-03, D3): `modalities` é uma lista de `{modalityId, levelId, degree}`, uma entrada por modalidade; a modalidade precisa estar ativa na organização; se a modalidade tem graduação ligada, `levelId` é obrigatório, do nível dela, e `degree` de 0 ao máximo do nível; se não tem graduação, `levelId` e `degree` ficam nulos. Repetir modalidade dá 400.
- R4 (RN-PRO-01, D4): as unidades precisam ser da organização e ativas. O Coordenador escolhe qualquer uma. O Administrador só adiciona ou remove unidades dele (unidade fora do alcance dele no pedido dá 403); as que o professor já tem fora do alcance dele ficam como estão, e ele não deixa o professor sem ao menos uma unidade dele.
- R5 (RN-PRO-04, D5): `directoryConsent = true` exige `consentDeclaredByManagement = true` (a declaração "o professor autorizou"); os dois valores são gravados e voltam no `TeacherDetail`; nada muda na lista nem em rota pública.
- R6 (RF-PRO-03, D1, D6): `PUT /api/v1/teachers/{id}/photo` (multipart, campo `file`) troca a foto: JPG, PNG ou WebP, exatamente 512 x 512, até 5 MB, conferido pelos bytes como `LogoImage`; `DELETE` a remove; o arquivo antigo é apagado do armazenamento; ambas respondem 200 com `TeacherDetail`.
- R7 (D7): `GET /api/v1/teachers/options` devolve as modalidades ativas da organização com `graduationEnabled` e, se ligada, os níveis (`id`, `name`, `maxDegree`), para o formulário; exige `TEACHERS`.
- R8 (permissão): as rotas novas exigem `TEACHERS` no servidor: Coordenador e Administrador; Professor, Aluno e Responsável 403; sem sessão 401. Administrador sem alcance sobre o professor recebe 404 em `PUT` e foto, como em #43. Entrada inválida dá 400 com o campo.
- R9 (isolamento): organização só da sessão; corpo e URL nunca a definem; modalidade, nível e unidade de outra organização dão 400; id de professor de outra organização dá 404.
- R10 (auditoria): criar, editar e trocar ou remover foto gravam auditoria (`teacher.created`, `teacher.updated`, `teacher.photo_updated`, `teacher.photo_removed`) com ator, professor e nomes dos campos alterados; e-mail e telefone não vão com valor.
- R11 (tela): botão "Novo professor" na lista e botão Editar ativo no painel abrem a janela (Modal) com todos os campos, máscara de telefone, contador `n/300` na apresentação, caixas de unidades (`UnitChecks`), linhas de modalidade com nível e grau, caixas "Aparecer no diretório" e "O professor autorizou", escolha de foto com corte quadrado 512 px (`LogoCropDialog`) e "Remover foto". Salvar fecha a janela e abre o painel (criação) ou o atualiza (edição); erro de campo aparece no campo. Estados carregando e erro das opções, 360 px, axe nos dois temas, textos em `pt-BR.json`.
- R12 (contrato e dados): `docs/openapi.yaml` e os handlers MSW refletem as rotas e os campos novos; o seed continua válido (`SeedDataIT`).

## 3. Critérios de aceite

- CA1 (R1): dado Coordenador, quando faz `POST /teachers` com todos os campos válidos, então 201 com `TeacherDetail` (`status ACTIVE`, `access NONE`, unidades, modalidades com nível e grau, telefone só com dígitos), e o professor aparece em `GET /teachers`.
- CA2 (R1): dado nome com 1 ou 81 caracteres (ou só espaços), e-mail ou telefone inválidos, apresentação com 301 caracteres ou `unitIds` vazio, quando cria, então 400 com o campo e nada é gravado.
- CA3 (R2): dado professor existente, quando faz `PUT` com unidades e modalidades diferentes, então 200, o conjunto novo substitui o antigo, situação e conta continuam iguais; em professor `INACTIVE` também funciona.
- CA4 (R3): dado modalidade com graduação ligada, quando falta `levelId`, o nível é de outra modalidade ou `degree` passa do máximo, então 400; modalidade sem graduação com `levelId` 400; modalidade repetida 400; modalidade inativa na organização 400; grau 0 até o máximo e modalidade sem graduação com nível nulo salvam.
- CA5 (R4): dado Administrador de A, quando cria com unidade de B (mesma organização), então 403; com unidade dele, salva; ao editar professor com unidades de A e B, as de B permanecem após o `PUT` e as de A seguem o pedido; um `PUT` que tire a última unidade dele dá 400; Coordenador escolhe qualquer unidade ativa; unidade inativa ou inexistente dá 400.
- CA6 (R5): dado `directoryConsent = true` com `consentDeclaredByManagement = false`, então 400; os dois true salvam e voltam no detalhe; `directoryConsent = false` salva com qualquer declaração.
- CA7 (R6): dado arquivo PNG, JPEG ou WebP 512 x 512, quando envia a foto, então 200 com `photoUrl` assinada e o arquivo anterior some do armazenamento; arquivo de outro tamanho, outro tipo ou acima de 5 MB dá 400 e a foto antiga fica; `DELETE` zera `photoUrl` e apaga o arquivo.
- CA8 (R7): dado Administrador e Coordenador, quando chamam `GET /teachers/options`, então só modalidades ativas, com níveis quando a graduação está ligada; modalidade inativa não aparece.
- CA9 (R8): dado Professor, Aluno e Responsável, então 403 em todas as rotas novas; sem sessão 401; Administrador sem alcance 404 em `PUT` e foto; Administrador com alcance 200.
- CA10 (R9): dado duas organizações, então `unitId`, `modalityId` e `levelId` de outra organização dão 400, `PUT` e foto de professor de outra organização dão 404 e nenhuma linha de outra organização é alterada (RLS conferido com `ctcore_app`).
- CA11 (R10): dado criar, editar, trocar e remover foto, então cada ação grava um registro de auditoria com os campos alterados e sem e-mail nem telefone.
- CA12 (R11): dado a janela em 360 px, então sem rolagem horizontal e axe sem violações nos dois temas; o contador mostra `n/300`; telefone com máscara; erro 400 aparece no campo correspondente; salvar na criação leva a `/professores/{id}`; na edição o painel mostra os dados novos; opções em carregamento e em erro (com "Tentar de novo"); sem foto mostra iniciais; Editar ativo no painel e "Novo professor" na lista só para quem tem `TEACHERS`.
- CA13 (R12): `OpenApiContractTest` e `ContractRoutesTest` passam com as rotas; testes MSW cobrem as rotas no formato do servidor; `SeedDataIT` passa.
- CA14 (R1, R11): ponta a ponta `@critical` "Coordenador cadastra um professor": abre Pessoas › Professores, clica em "Novo professor", preenche nome, unidade e uma modalidade, salva e vê o painel do professor novo.

## 4. Plano técnico

- **Dados.** Não muda a estrutura: `teacher`, `teacher_unit` e `teacher_modality` (com gatilho de nível e grau) existem em `V23__teacher.sql`. Nome e `name_key` (por `Modalities.normalize`) gravados na escrita. Sem seed novo.
- **API** (cookie e CSRF como as demais; rotas em `docs/openapi.yaml` no mesmo PR): `POST /teachers` (201 `TeacherDetail`; 400, 401, 403), `PUT /teachers/{id}` (200; 400, 401, 403, 404), `PUT` e `DELETE /teachers/{id}/photo` (200; 400, 401, 403, 404), `GET /teachers/options` (200 `TeacherOptions`; 401, 403). Esquema `TeacherInput` com `modalities[{modalityId, levelId, degree}]`. Aditivos em respostas existentes: `ModalityRef.levelId` (lista e detalhe) e `TeacherDetail.consentDeclaredByManagement`, para o formulário editar sem adivinhar.
- **Back-end.** Em `br.com.ctcore.teacher`: a criar `TeacherWrite.java` (validação, criar, editar, foto, auditoria; `OrganizationTransactions.execute`; `AuditService` com `AuditEntry`; `FileStorage` e `LogoImage.parse` para a foto, no padrão de `OrganizationProfile.replaceLogo`/`discard`; permissão e alcance por `Teachers.requireAccess` e `Teachers.find`) e o método de opções (em `Teachers` ou classe pequena que lê `modality`, `organization_modality` e `graduation_level`). A alterar: `Teachers.java` (`ModalityRef.levelId`, `Detail.consentDeclaredByManagement`, em `refs`/`detail`), `TeacherController.java` (rotas; `@RequestPart` como `OrganizationController.replaceLogo`). Validação de campo com `ApiValidationException`; formato de e-mail e telefone no padrão de `NewUnit` (extrair ou duplicar o mínimo, decisão da construção).
- **Interface.** A criar `frontend/src/features/teachers/TeacherFormDialog.tsx` (e teste). A alterar `TeacherPanelPage.tsx` (Editar ativo), `TeachersPage.tsx` (botão "Novo professor"), `useTeachers.ts` (opções, criar, editar, foto), `mocks/handlers.ts` (+ teste MSW), `i18n/pt-BR.json`. Reusa `Modal`, `CharCounter`, `PhoneField` e máscaras de `components/fields`, `features/users/UnitChecks.tsx`, `features/organization/LogoCropDialog.tsx` e `logoFile` (512 px; parametrizar os textos, hoje de logo).
- **Segurança.** Organização, perfil e alcance vêm do principal; `TEACHERS` conferido em toda rota; parâmetros ligados; foto validada pelos bytes e nome gerado no servidor (`teacher-<uuid>.<ext>`), nunca o enviado; telefone e e-mail fora do log e da auditoria; contato só em respostas autenticadas (já é assim em `Detail`).
- **Riscos.** (1) Arquivo novo gravado e falha na transação: descartar o novo, como em `replaceLogo`. (2) Administrador editando professor com unidades de outros: a preservação de R4 é o ponto frágil; teste dedicado. (3) `LogoCropDialog` tem texto de logo: usar chaves de tradução próprias ao reaproveitar.

## 5. Tarefas

Duas partes: servidor e contrato (T1 a T4), tela e fechamento (T5 a T7).

- [x] T1: `Teachers` com `levelId` e `consentDeclaredByManagement` nas respostas, e `TeacherWrite` com `POST /teachers` e `PUT /teachers/{id}` (validações R1 a R5, alcance do Administrador, auditoria) e testes de integração em `TeacherWriteIT` (a criar) — cobre CA1 a CA6, CA9 a CA11 — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test='TeacherWriteIT,TeacherIT' -Dtest=NoSuchTest -Dsurefire.failIfNoSpecifiedTests=false -Djacoco.skip=true`.
- [x] T2: foto (`PUT` e `DELETE /teachers/{id}/photo`) com limpeza do arquivo antigo, auditoria e testes — cobre CA7, CA9 a CA11 — prova: `verify -Dit.test=TeacherWriteIT` (mesmos flags).
- [x] T3: `GET /teachers/options` e testes (perfis, só ativas, níveis) — cobre CA8, CA9 — prova: `verify -Dit.test=TeacherWriteIT` (mesmos flags).
- [x] T4: OpenAPI (rotas, esquemas novos e campos aditivos) e handlers MSW com teste — cobre CA13 — prova: `./backend/mvnw -f backend/pom.xml test -Dtest='OpenApiContractTest,ContractRoutesTest'`, `verify -Dit.test=SeedDataIT` e `npx vitest run src/mocks` em `frontend/`.
- [x] T5: `TeacherFormDialog`, `useTeachers` (opções, criar, editar, foto), botão "Novo professor", Editar ativo, textos pt-BR, contador, erros por campo, 360 px e axe nos dois temas — cobre CA12 — prova: `npx vitest run src/features/teachers` em `frontend/`.
- [x] T6: e2e `@critical` "Coordenador cadastra um professor" em `frontend/e2e/professores.spec.ts` — cobre CA14 — prova: `npm run e2e` em `frontend/`.
- [x] T7: regressão completa (confere cobertura e os 100% dos pacotes exigidos) — não cobre critério: confirma que nada quebrou — prova: `./backend/mvnw -f backend/pom.xml verify` e, em `frontend/`, `npm run lint && npm run typecheck && npm run build && npm run test:cov && npm run e2e`.

## 6. Decisões

- D1: a foto tem rota própria (multipart, depois do cadastro), no padrão da logo de #28; o formulário guarda o corte e o envia após salvar. Falha no envio da foto mantém o professor salvo e mostra o erro com nova tentativa. Alternativa: multipart único com os campos; descartada por misturar JSON e arquivo e fugir do padrão existente.
- D2: criar e editar usam a mesma permissão `TEACHERS` (Coordenador e Administrador, ambos com acesso pleno na tabela). Não há ação de situação aqui (#45).
- D3 (muda o comportamento): graduação obrigatória só quando a modalidade tem graduação ligada na organização; senão nível e grau ficam nulos. Alternativa: exigir nível sempre; descartada por não haver nível em modalidade sem graduação.
- D4 (muda o comportamento): o Administrador gerencia só as unidades dele num professor compartilhado e não perde as de outros ao salvar (mesma lógica de #37 e da P1: o alcance de cada um vale). Alternativa: bloquear a edição de professor com unidades fora do alcance; descartada por impedir o Administrador de corrigir nome e foto.
- D5 (muda o comportamento): consentimento exige a declaração "o professor autorizou" marcada pela gestão, já que o consentimento do próprio professor na conta dele ainda não existe; a regra pública vem nas Sprints 18 e 20 (como D5 de #42). Alternativa: gravar consentimento sem declaração; descartada porque RN-PRO-04 pede uma das duas formas.
- D6: a foto segue o padrão da logo: 512 x 512 e até 5 MB conferidos pelos bytes; o corte quadrado é feito no navegador (`LogoCropDialog`), porque "Dados" pede quadrado de 512 px.
- D7: `GET /teachers/options` existe porque o Administrador não lê `GET /modalities/{id}/graduation` (exige Configurações da organização). Alternativa: liberar aquela rota; descartada por ampliar permissão de outro módulo.
- D8: nome e e-mail repetidos entre professores da organização são permitidos; nenhuma regra os proíbe (RN-PRO-01 fala de conta). Alternativa: unicidade por e-mail; descartada por não estar na especificação.
- D9: modalidades são opcionais no cadastro (o card só lista o campo); unidades são obrigatórias (RN-PRO-01). Se o dono quiser modalidade obrigatória, é ajuste pequeno.
- D10: o canvas `ProfessorNovo.dc.html` não está acessível; a tela segue os padrões do projeto e o dono confere o layout no PR (como D8 de #42).
- Suposição: telefone segue o formato de `NewUnit` (DDD mais 8 ou 9 dígitos); se o dono aceitar telefone estrangeiro, muda a validação. Editar o e-mail do cadastro não altera a conta já ligada.

## Construção

- T1 — arquivos: `backend/.../teacher/Teachers.java` (`ModalityRef.levelId`, `Detail.consentDeclaredByManagement`, `photoUrl` visível ao pacote), `teacher/TeacherWrite.java` (novo: criar, editar, validações R1 a R5, alcance do Administrador, auditoria), `TeacherController.java` (POST e PUT), `organization/NewUnit.java` (`email` e `phone` públicos, reaproveitados), `TeacherWriteIT.java` (novo) — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test='TeacherWriteIT,TeacherIT,TeacherAccessIT' -Dtest=NoSuchTest -Dsurefire.failIfNoSpecifiedTests=false -Djacoco.skip=true` → 27 testes, 0 falhas
- T2 — arquivos: `TeacherWrite.java` (`replacePhoto`, `removePhoto`, reaproveita `LogoImage` e `FileStorage`), `TeacherController.java` (`PUT` e `DELETE /{id}/photo`), `TeacherWriteIT` (`photo`, `permissions`, `isolation`) — prova: `verify -Dit.test=TeacherWriteIT` (mesmos flags) → 10 testes, 0 falhas
- T3 — arquivos: `TeacherWrite.options`, `TeacherController` (`GET /options`), `TeacherWriteIT.options` — prova: mesma execução da T2 → passou
- T4 — arquivos: `docs/openapi.yaml` (rotas, `TeacherInput`, `TeacherOptions`, `levelId`, `consentDeclaredByManagement`), `frontend/src/mocks/handlers.ts` (5 handlers e validação como no servidor), `frontend/src/mocks/teachers.msw.test.ts` — prova: `./backend/mvnw -f backend/pom.xml test -Dtest='OpenApiContractTest,ContractRoutesTest' -Djacoco.skip=true` → 7 testes, 0 falhas; `verify -Dit.test=SeedDataIT` → 11 testes, 0 falhas; `npx vitest run src/mocks` → 68 de 68
- T5 — arquivos: `features/teachers/TeacherFormDialog.tsx` (novo) e `.test.tsx` (novo), `useTeachers.ts`, `TeachersPage.tsx` ("Novo professor"), `TeacherPanelPage.tsx` (Editar ativo), `features/organization/LogoCropDialog.tsx` (prop `labels`), `i18n/pt-BR.json` (`teachers.form`), testes de página com `levelId` nos dados — prova: `npx vitest run src/features/teachers` → 39 de 39 (axe a 360 px nos dois temas incluído)
- T6 — arquivos: `frontend/e2e/professores.spec.ts` — prova: `npm run e2e` → 22 de 22
- T7 — prova abaixo.

Regressão: `./backend/mvnw -f backend/pom.xml verify` → 290 testes, 0 falhas, JaCoCo sem violação (BUILD SUCCESS); em `frontend/`: `npm run lint` e `npm run typecheck` limpos, `npm run build` ok, `npm run test:cov` → 66 arquivos, 531 testes passam, limites de cobertura atendidos, `npm run e2e` → 22 de 22. Sem linha de base registrada no arquivo; nada que passava passou a falhar.
Desvios: (1) Editar e "Novo professor" só aparecem com `TEACHERS` diferente de `NONE` (via `usePermissions`), por CA12; o teste do painel que esperava Editar desativado passou a esperá-lo ativo. (2) Em `PUT`, o 403 de unidade fora do alcance do Administrador vem depois do 404 de professor sem alcance. (3) Telefone e e-mail reaproveitam `NewUnit.phone/email` (tornados públicos) em vez de duplicar. (4) Grau obrigatório (não nulo) quando a graduação está ligada. (5) Auditoria: e-mail e telefone entram só como "informado" ou "alterado". (6) Ao salvar com foto pendente, a foto é enviada depois (D1); se falhar, o cadastro fica salvo e há "Tentar enviar a foto de novo".
Não é do item: nenhum.

## Verificação

### Rodada 1 — OK — 2026-10-19, estado 8828f96978e60cdc7f0fa497019b4d82325f141a

| Critério | Status | Evidência |
|---|---|---|
| CA1 a CA6 | PASSOU | `TeacherWriteIT` (create, update, modalities, units, consent) dentro do `verify`: 290 testes, 0 falhas; leitura de `TeacherWrite.clean/checkUnits/checkModalities/replaceUnits` confere a lógica |
| CA7 | PASSOU | `TeacherWriteIT.photo`; `replacePhoto` valida por `LogoImage.parse`, descarta o arquivo novo em falha e apaga o antigo |
| CA8 | PASSOU | `TeacherWriteIT.options`; consulta filtra `o.active` |
| CA9, CA10 | PASSOU | `TeacherWriteIT.permissions` e `isolation` (verify verde) |
| CA11 | PASSOU | auditoria `teacher.created/updated/photo_*`; e-mail e telefone só como "informado" ou "alterado" |
| CA12 | PASSOU | `TeacherFormDialog.test.tsx` e testes de página (`test:cov` verde; axe a 360 px nos dois temas); formulário filtra unidades pelo alcance |
| CA13 | PASSOU | `OpenApiContractTest`, `ContractRoutesTest` e `SeedDataIT` dentro do `verify`; testes MSW no `test:cov` |
| CA14 | PASSOU | `npm run e2e`: 22 de 22, incluindo "Coordenador cadastra um professor" |

Comandos:
- `./backend/mvnw -f backend/pom.xml verify` → 290 testes, 0 falhas, JaCoCo atendido, BUILD SUCCESS
- em `frontend/`: `npm run lint && npm run typecheck && npm run build && npm run test:cov` → exit 0 (linhas 96,67%)
- `npm run e2e` → 22 passed

Achados:
- sugestão, código, `TeacherWrite.update` — o 403 de unidade fora do alcance vem depois do 404 de professor sem alcance (já declarado como desvio 2); aceitável.

## Entrega

- Cadastro e edição de professor: `POST /teachers` e `PUT /teachers/{id}` (dados, modalidades com graduação, unidades, consentimento, foto), opções de formulário, auditoria sem dado sensível; formulário `TeacherFormDialog` ligado à lista e ao painel; contrato, MSW e e2e atualizados.
- Verificação: `verify` do back-end (290 testes, JaCoCo ok), lint, typecheck, build, `test:cov` e `e2e` (22 de 22) verdes.
- Decisões e desvios: ver "Desvios" acima (403 de unidade depois do 404; grau obrigatório com graduação ligada; foto enviada após o salvar, com nova tentativa).
- Limitações: nenhuma conhecida.

Refs #44
