# 31 — RF-ORG-04 · Configurações › Unidade

Origem: issue #31 (Sprint 03); `docs/especificacao/01-organizacao-e-isolamento.md` (RF-ORG-04, RN-ORG-10, RN-ORG-14, RN-ORG-15 e tabela "Configurações da unidade"), `09-matricula.md` (RN-MAT-03, RN-MAT-05), `10-cobrancas.md` (RN-COB-17), `11-pagamentos-mercado-pago.md` (RN-PAG-01 a 05), `20-aula-experimental-e-autocadastro.md` (links). Branch: `item/31-configuracoes-unidade`.

## 1. Objetivo

Dar ao Coordenador e ao Administrador da unidade a tela e a API para ver e manter os dados e as configurações de uma unidade: dados de contato e endereço, dias de vencimento, taxa de matrícula, taxas por meio de pagamento, chave Pix, situação da conexão do Mercado Pago, dias de lembrete, permissões do Professor, aparecer no diretório e links públicos. Os valores ficam gravados em `unit` e `unit_settings` (V3), prontos para os itens que os usam.

Fora do escopo: conectar, reconectar e desconectar o Mercado Pago e prévia do total por meio (#97, #183); lembretes com canais, horário e liga/desliga (#80); QR code e cópia dos links públicos (#162); diretório propriamente dito (#169); modalidades oferecidas (#40); "Última alteração em [data] por [pessoa]" e consulta da auditoria (#35, aqui a alteração já grava a auditoria com antes e depois); seletor de unidade no topo (#32); ajuste do nome curto (já existe, #29); desativar unidade (#34).

## 2. Requisitos

- R1 (RF-ORG-04, leitura): `GET /api/v1/units/{id}` devolve os dados e as configurações da unidade, da organização do vínculo ativo; permitido ao Coordenador e ao Administrador da unidade (linha `UNIT_SETTINGS`); negado ao Professor, ao portal e a quem não tem a unidade.
- R2 (RF-ORG-04, escolha da unidade): `GET /api/v1/units/mine` devolve as unidades (id, nome, nome curto, cidade, situação) sobre as quais o vínculo tem `UNIT_SETTINGS`: todas para o Coordenador, só as suas para o Administrador (D1).
- R3 (RF-ORG-04, alteração): `PUT /api/v1/units/{id}` substitui os campos editáveis (dados e configurações) de uma só vez; Coordenador em qualquer unidade da organização, Administrador só nas suas; os demais recebem 403 sem efeito (D2).
- R4 (dados, RN-ORG-14): nome obrigatório (até 120); CEP, endereço, cidade, telefone, e-mail, WhatsApp e CNPJ opcionais e validados (mesmas regras de `NewUnit`, CNPJ com dígito verificador, guardado só com dígitos); latitude e longitude vêm juntas e dentro da faixa; com CEP novo e sem coordenadas enviadas, a porta `MapLocator` as obtém, e sem resultado ficam vazias (D3). O nome curto não muda aqui (RN-ORG-15).
- R5 (RN-MAT-03): dias de vencimento permitidos: de 1 a 28, sem repetição, ao menos um; guardados em ordem crescente (D4).
- R6 (RN-MAT-05): taxa de matrícula com liga/desliga e valor em centavos (não negativo); desligada, o valor guardado é 0.
- R7 (RN-PAG-05, RN-ORG-10): uma taxa fixa por meio de pagamento, em centavos, não negativa e com teto, para os nove meios da tabela `unit_settings` (dinheiro, Pix, boleto, crédito, débito, débito automático, Pix manual, maquininha, transferência) (D5).
- R8 (chave Pix): opcional; quando enviada precisa ser CPF, CNPJ, e-mail, telefone ou chave aleatória (UUID) válidos, guardada normalizada; em branco apaga (D6).
- R9 (RN-PAG-01, RN-PAG-02): a leitura mostra a situação da conexão do Mercado Pago em `settings.paymentAccount` (`CONNECTED` ou `NOT_CONNECTED`; o nome não cita o fornecedor, D13), sem token nem referência da conta; a alteração não mexe nela (D7).
- R10 (RN-COB-17): dias de lembrete como deslocamentos em relação ao vencimento (negativo antes, positivo depois), de -30 a 60, sem repetição, no máximo 8, podendo ficar vazio (D8).
- R11 (permissões do Professor): quatro chaves booleanas (avisos, editar agenda, ver contatos dos alunos, aulas experimentais) gravadas em `teacher_permissions` na ordem de `PermissionTable.teacherFlag`.
- R12 (diretório e links): liga/desliga "aparecer no diretório", "link de aula experimental" e "link de autocadastro".
- R13 (auditoria, RF-ORG-08 base): a alteração grava `unit.update` com os campos mudados, valor antes e depois, na mesma transação; chave Pix e CNPJ entram só como "alterado", sem o valor; chamada sem mudança não grava; recusada não grava (D9).
- R14 (isolamento, RNF-31): as três rotas só enxergam e alteram unidades da organização do vínculo ativo; `organizationId` enviado pelo cliente é ignorado; unidade de outra organização responde como inexistente.
- R15 (tela): `/configuracoes/unidade` mostra seções (Dados, Cobrança, Pagamentos, Lembretes, Professor, Divulgação) com um só "Salvar"; escolhe a unidade quando há mais de uma; quatro estados; 360 px; axe nos dois temas; textos em `pt-BR.json`; Administrador de outra unidade e Professor não acessam.
- R16 (menu): o item "Unidade" do catálogo passa a `available: true` para Coordenador e Administrador.
- R17 (contrato): as rotas entram em `docs/openapi.yaml` e nos handlers MSW, idênticas à implementação.

## 3. Critérios de aceite

- CA1 (R1): dado Coordenador, quando chama `GET /units/{id}` de unidade da sua organização, então 200 com `name`, `shortName`, `postalCode`, `address`, `city`, `latitude`, `longitude`, `phone`, `email`, `whatsapp`, `cnpj`, `active` e `settings` (`dueDays`, `enrollmentFeeEnabled`, `enrollmentFeeCents`, `feesCents` por meio, `pixKey`, `paymentAccount`, `reminderDays`, `teacherPermissions`, `directoryEnabled`, `trialLinkEnabled`, `selfRegistrationLinkEnabled`); a resposta não tem chaves além do contrato.
- CA2 (R1, R14): dado Administrador de outra unidade, Professor ou portal, quando chama `GET /units/{id}`, então 403; unidade de outra organização ou inexistente, 404; anônimo, 401.
- CA3 (R2): dado Coordenador de organização com 3 unidades, então `GET /units/mine` traz as 3; dado Administrador de 1 delas, traz só essa; Professor, 403.
- CA4 (R3, R14): dado Coordenador, quando envia `PUT` válido, então 200 com os novos valores, e nova leitura os devolve; um corpo com `organizationId` ou `shortName` é ignorado.
- CA5 (R3): dado Administrador da unidade, quando envia `PUT` na sua unidade, então 200; em unidade que não é dele, 403; Professor e portal, 403; nada muda nos casos de recusa.
- CA6 (R4): dado nome vazio ou com mais de 120 caracteres, e-mail, CEP, telefone ou CNPJ inválidos, latitude sem longitude ou fora da faixa, então 400 `VALIDATION_ERROR` com o campo e nada salvo; CNPJ válido com máscara salva só os dígitos.
- CA7 (R4): dado CEP novo sem coordenadas e a porta conhece o CEP, então a unidade guarda a latitude e a longitude da porta; CEP desconhecido, ficam vazias sem erro; coordenadas enviadas valem e a porta não é chamada; CEP inalterado e coordenadas ausentes mantêm as atuais.
- CA8 (R5): dado `dueDays` vazio, com 0, 29, repetido ou nulo, então 400 e nada salvo; `[15, 5, 1]` salva como `[1, 5, 15]`.
- CA9 (R6): dado taxa de matrícula desligada com valor 500, então salva valor 0; ligada com -1, então 400; ligada com 15000, então salva.
- CA10 (R7): dado taxa de meio negativa ou acima do teto, então 400 com o campo; valores válidos são lidos de volta idênticos.
- CA11 (R8): dado chave Pix de CPF, CNPJ, e-mail, telefone (+55 e dígitos) e UUID válidos, então salva normalizada; chave inválida, 400; em branco, apaga.
- CA12 (R9): dado unidade sem referência de conta, então `settings.paymentAccount = NOT_CONNECTED`; com referência, `CONNECTED`; nenhuma resposta contém a referência; o `PUT` não altera a referência.
- CA13 (R10): dado lembretes `[-3, 0, 3, 10]`, então salva; vazio, salva; repetido, fora de -30..60 ou mais de 8, 400.
- CA14 (R11, R12): dado `teacherPermissions` com 4 valores, então salva na ordem avisos, agenda, contatos, experimentais e `Permissions.grant` do Professor reflete a mudança; com 3 ou 5 valores ou nulo, 400.
- CA15 (R13): dado `PUT` que muda telefone e dias de vencimento, então há uma linha `unit.update` com antes e depois só desses campos, ator, perfil e organização; mudando a chave Pix ou o CNPJ, a linha traz o campo como alterado sem o valor; `PUT` idêntico ou recusado não grava; falha ao auditar desfaz a alteração (tudo ou nada).
- CA16 (R14): dado usuário da organização A que usa o id de unidade da B, no `GET` e no `PUT`, então 404 e a B permanece idêntica (molde `RouteIsolationModelIT`, perfis Coordenador, Administrador da unidade, Administrador de outra unidade, Professor, portal e anônimo).
- CA17 (R15): dado Coordenador na tela, quando altera dados, dias de vencimento, taxas e permissões e salva, então aparece confirmação e os valores novos; erro 400 aparece junto do campo; valores em reais com máscara de moeda, enviados em centavos.
- CA18 (R15): dado carregando, erro (com "Tentar de novo"), unidade sem alterações salvas ainda (padrões) e Professor, então `LoadingState`, `ErrorState`, a tela com os padrões e a área indisponível; 360 px sem rolagem horizontal; axe sem violações nos dois temas; botão "Salvar" desabilitado sem mudança.
- CA19 (R15): dado Administrador de duas unidades, então a tela oferece escolher entre elas (`?unidade=`); com uma, não mostra escolha.
- CA20 (R9, R15): dado conexão `NOT_CONNECTED`, a tela mostra "Mercado Pago não conectado" por texto e ícone e o aviso de que a conexão chega em item posterior, sem botão de conectar.
- CA21 (R16): dado o catálogo do menu, "Unidade" aparece para Coordenador e Administrador e não para o Professor.
- CA22 (R17): `OpenApiContractTest` e `ContractRoutesTest` passam com as três rotas; os handlers MSW devolvem o mesmo formato e os mesmos erros por perfil.
- CA23 (R4, R15, ponta a ponta `@critical`): dado o Coordenador do mock, quando abre Configurações › Unidade, altera o telefone e a taxa de matrícula e salva, então vê os valores após recarregar; o Professor vê a área indisponível.

## 4. Plano técnico

- Dados: sem tabela nova. `unit` (V3, V13, V14) e `unit_settings` (V3) já têm todas as colunas, com checks e RLS. Migração `V15__unit_cnpj_check.sql` (a criar): `check (cnpj ~ '^[0-9]{14}$')` em `unit.cnpj`, com reversão descrita no arquivo; conferir antes que a semente não grava CNPJ fora do padrão (`infra/seed/`). Teto das taxas e limite dos lembretes são regras do servidor, sem coluna. Semente: sem mudança.
- API (`docs/openapi.yaml`): `GET /api/v1/units/mine` (200 lista `UnitChoice`; 401; 403); `GET /api/v1/units/{id}` (200 `UnitDetail`; 401; 403; 404); `PUT /api/v1/units/{id}` (corpo `UnitUpdate`; 200 `UnitDetail`; 400 `VALIDATION_ERROR`; 401; 403; 404). Valores monetários em centavos inteiros, `feesCents` objeto com chaves `cash`, `pix`, `bankSlip`, `creditCard`, `debitCard`, `automaticDebit`, `manualPix`, `terminalCard`, `transfer`; `teacherPermissions` objeto `{ announcements, agendaEdit, studentContacts, trialClasses }`; `settings.paymentAccount` enum `CONNECTED | NOT_CONNECTED`. Cookie de sessão e CSRF como as demais rotas de escrita.
- Back-end (`br.com.ctcore.organization`): `UnitSettings` (`@Service`, a criar) com `mine`, `read`, `update`; `UnitUpdate` (`record` a criar) com `validated()` no estilo de `NewUnit`; `Cnpj` (validação de dígito verificador: reutilizar `HolderDocument` se expuser o método; senão extrair) e `PixKey` (a criar) para as validações novas. Permissão por `Permissions.grant(who, UNIT_SETTINGS, unitId)` (`FULL` para escrever, `!= NONE` para ler); unidade ausente na organização do principal responde 404 (`ApiStatusException`). `update` em `transactions.execute`: lê a linha anterior com `for update`, grava `unit` e `unit_settings`, calcula as diferenças e chama `AuditService.record` `unit.update`. Geocodificação com `MapLocator` fora da transação, como em `UnitCreation`. `UnitController` ganha os três métodos (`/mine` declarado antes de `/{id}`).
- Interface (`frontend/src/features/units/`): `UnitSettingsPage.tsx` (rota `/configuracoes/unidade` em `ManagementLayout` + `RequireArea` `UNIT_SETTINGS`, registrada em `App.tsx`), com seções em `Card`; `useUnitSettings.ts` (consulta `mine`, `read`, mutação `PUT`); campos de `components/fields` (moeda, telefone, CEP, e-mail; CNPJ e chave Pix com máscara simples a acrescentar em `fields` ou `format.ts` se não houver); `menuCatalog.ts` `unitSettings` com `available: true`; link "Configurar" na linha de `UnitsPage.tsx` para o Coordenador; `pt-BR.json` chaves `unitSettings.*` e erros; `mocks/handlers.ts` com as três rotas por perfil e estado em memória. Canvas `ConfigUnidade.dc.html` não acessível (ver Suposição).
- Segurança: permissão no servidor em toda rota; organização sempre a do principal e unidade conferida contra ela (RLS confirma); chave Pix e CNPJ fora da auditoria em claro; resposta sem token ou referência do Mercado Pago (RNF-08); validação de formato na borda; teste de isolamento entre organizações e entre perfis nas rotas novas.
- Arquivos a alterar (vistos): `backend/src/main/java/br/com/ctcore/organization/UnitController.java`, `.../organization/HolderDocument.java` (se o CNPJ for reaproveitado), `docs/openapi.yaml`, `frontend/src/App.tsx`, `frontend/src/features/permissions/menuCatalog.ts` e `buildMenu.test.ts`, `frontend/src/features/units/UnitsPage.tsx`, `frontend/src/mocks/handlers.ts`, `frontend/src/i18n/pt-BR.json` (conferir o caminho). A criar: `UnitSettings.java`, `UnitUpdate.java`, `PixKey.java`, `V15__unit_cnpj_check.sql`, `UnitUpdateValidationTest`, `PixKeyTest`, `UnitSettingsIT`, `UnitSettingsRouteIT`, `UnitSettingsPage.tsx`, `useUnitSettings.ts`, `UnitSettingsPage.test.tsx`, `e2e/configuracao-unidade.spec.ts`.
- Reuso: `NewUnit` (validações de CEP, e-mail, telefone), `MapLocator`, `Permissions`, `PermissionTable.teacherFlag`, `OrganizationTransactions`, `AuditService`, `ApiValidationException`/`ApiStatusException`, `UnitBase` e `RouteIsolationModelIT` nos testes, componentes `Card`, `states`, `fields`, `ManagementLayout`.
- Riscos: edição simultânea (a última gravação vence; o histórico fica na auditoria, #35 mostra a última alteração); `PUT` completo apaga um campo omitido (a tela sempre envia tudo; documentado no contrato); duas listas de permissões do Professor (colunas e `PermissionTable`) precisam manter a mesma ordem, o que o teste CA14 prende; cobertura de `isolation` continua 100%.

## 5. Tarefas

- [x] T1: migração `V15__unit_cnpj_check.sql` — não cobre critério: o banco passa a recusar CNPJ fora do padrão (apoia CA6), conferida em T3 — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test='UnitSettingsIT,SeedDataIT'`.
- [x] T2: `UnitUpdate.validated()`, `PixKey` e CNPJ (dados, dias de vencimento, taxas, lembretes, permissões do Professor) — cobre CA6, CA8, CA9, CA10, CA11, CA13, CA14 (400) — prova: `./backend/mvnw -f backend/pom.xml test -Dtest='UnitUpdateValidationTest,PixKeyTest'`.
- [x] T3: `UnitSettings.read`, `mine` e `update` (permissão, 404, coordenadas, gravação, auditoria tudo ou nada) — cobre CA1, CA3, CA4, CA7, CA12, CA14, CA15 — prova: `verify -Dit.test=UnitSettingsIT`.
- [x] T4: rotas no `UnitController`, erros 400/401/403/404 e isolamento entre organizações e entre perfis — cobre CA2, CA5, CA16 — prova: `verify -Dit.test=UnitSettingsRouteIT`.
- [x] T5: OpenAPI e teste de contrato — cobre CA22 — prova: `./backend/mvnw -f backend/pom.xml test -Dtest='OpenApiContractTest,ContractRoutesTest'`.
- [x] T6: front base: handlers MSW, `useUnitSettings`, textos `unitSettings.*`, menu `available` e link em `UnitsPage` — cobre CA21, CA22 (base) — prova: `npx vitest run src/mocks src/features/permissions src/features/units`.
- [x] T7: `UnitSettingsPage` (seções, escolha de unidade, moeda em centavos, quatro estados, 360 px, axe) e rota em `App.tsx` — cobre CA17, CA18, CA19, CA20 — prova: `npx vitest run src/features/units`.
- [x] T8: e2e `@critical` do Coordenador e do Professor — cobre CA23 — prova: `npm run e2e`.
- [x] T9: regressão do item — não cobre critério: confere que nada quebrou e a cobertura — prova: `./backend/mvnw -f backend/pom.xml verify` e, em `frontend/`, `npm run lint && npm run typecheck && npm run build && npm run test:cov && npm run e2e`.

## 6. Decisões

- D1 (muda o comportamento): a unidade editada vem de `GET /units/mine` e, sem o seletor da #32, a tela usa `?unidade=` e a primeira unidade da lista; o Administrador só vê as suas. Motivo: `GET /units` é só do Coordenador (#29) e não serve ao Administrador. Alternativa descartada: abrir `GET /units` ao Administrador, que mudaria a regra da #29.
- D2 (muda o comportamento): quem pode alterar segue a linha `UNIT_SETTINGS` da tabela de permissões: Coordenador em qualquer unidade e Administrador nas suas; o Professor não vê nem altera. Alternativa descartada: Administrador só leitura, que contraria a tabela e a especificação ("Administrador da unidade ou Coordenador").
- D3: coordenadas seguem a decisão D3 do item 30: porta `MapLocator` por CEP, campos de latitude e longitude para ajuste manual (mapa interativo fica para a Sprint 22). A geocodificação só roda quando o CEP muda e as coordenadas não foram enviadas.
- D4: dias de vencimento aceitam qualquer dia de 1 a 28 (a especificação diz "qualquer dia de 1 a 28"), guardados ordenados. Alternativa descartada: restringir a 1, 5, 10 e 15.
- D5 (muda o comportamento): as taxas por meio são valores fixos em centavos, como o banco (#13) já modela; a especificação 11 fala em "percentual e/ou valor fixo", mas a coluna só guarda centavos. O percentual e a prévia do total são da #97, que ajusta o modelo se precisar. Teto de R$ 1.000,00 por meio para barrar erro de digitação. Alternativa descartada: criar colunas de percentual agora, sem a regra de cálculo definida.
- D6: a chave Pix é validada por tipo (CPF, CNPJ, e-mail, telefone, UUID) e guardada normalizada; sem consulta ao banco central. Alternativa descartada: texto livre, que deixaria o aluno copiar uma chave inválida.
- D7 (muda o comportamento): a conexão do Mercado Pago aparece só como situação (conectada ou não), derivada de `payment_account_reference`; conectar e desconectar não entram, porque dependem do OAuth real (Sprint 23) e do simulador de pagamento (#97). Alternativa descartada: botão "Conectar" simulado, que seria jogado fora.
- D8: lembretes são só a lista de dias (deslocamento do vencimento), que é o que `reminder_days` guarda; canais, horário e liga/desliga ficam na #80. Limites -30..60 e 8 itens são do servidor (a especificação não define).
- D9: a auditoria já grava antes e depois (obrigatório pelo `AGENTS.md`); chave Pix e CNPJ entram como "alterado" sem valor por serem dados de cadastro de pessoa. A exibição de "Última alteração" e a consulta ficam na #35.
- D10: `PUT` completo em vez de vários `PATCH` por seção, para a tela ter um só "Salvar" e o servidor validar tudo junto (e distinguir "em branco" de "ausente"). Alternativa descartada: um endpoint por seção.
- D11: unidade desativada pode ser lida e alterada; a especificação não a trava (RN-ORG-11 trata só da desativação, #34). Se a #34 decidir bloquear a edição, ajusta lá.
- D12: o recurso não é liberado aos poucos por organização (não está na lista da Sprint 03), então não há flag; não há arquivo de notas de versão no repositório.
- D13 (ajuste pedido na Construção): o campo da situação da conexão se chama `settings.paymentAccount` (`CONNECTED | NOT_CONNECTED`) no contrato, no código e no MSW, porque `NoVendorNamesTest` (RNF-39) proíbe o nome do fornecedor em `backend/src`; o texto de tela segue "Mercado Pago" (`pt-BR.json`). Substitui `mercadoPago` em R9, CA1, CA12 e no plano de API; CA20 trata só do texto de tela e não muda. Alternativa descartada: exceção no `NoVendorNamesTest`, que mudaria uma regra do projeto.
- Suposição: o canvas `ConfigUnidade.dc.html` não está acessível; as seções seguem o texto da issue e os padrões de tela existentes. Se divergir, só a tela e os textos mudam.
- Suposição: `HolderDocument.validCnpj` é privado (visto no código) e `normalize` aceita CPF ou CNPJ; T2 expõe a validação de CNPJ sem mudar o comportamento atual. A semente não grava CNPJ (conferido), então a V15 é segura.

## Construção

- T1 — arquivos: `backend/src/main/resources/db/migration/V15__unit_cnpj_check.sql` — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test='UnitSettingsIT,SeedDataIT'` → passou (UnitSettingsIT.databaseCheck recusa CNPJ com máscara; SeedDataIT com a V15 aplicada).
- T2 — arquivos: `UnitUpdate.java`, `PixKey.java`, `HolderDocument.normalizeCnpj` (novo método público, comportamento atual intacto), testes `UnitUpdateValidationTest`, `PixKeyTest` — prova: `./backend/mvnw -f backend/pom.xml test -Dtest='UnitUpdateValidationTest,PixKeyTest,HolderDocumentTest'` → passou.
- T3 — arquivos: `UnitSettings.java`, `UnitSettingsIT.java`, `UnitBase.java` (helpers `mine/read/put/payload`; `addUnit` passa a criar também `unit_settings`) — prova: `verify -Dit.test=UnitSettingsIT` → 8 testes passaram (leitura, lista, PUT, coordenadas, auditoria, tudo ou nada, CNPJ no banco).
- T4 — arquivos: `UnitController.java` (`GET /mine` antes de `GET/PUT /{id}`), `UnitSettingsRouteIT.java` — prova: `verify -Dit.test=UnitSettingsRouteIT` → 3 testes passaram (401/403/404, Administrador de outra unidade, organização forjada, B intacta).
- T5 — arquivos: `docs/openapi.yaml` (3 rotas e schemas `UnitChoice`, `FeesCents`, `TeacherPermissions`, `UnitSettingsView`, `UnitDetail`, `UnitUpdateSettings`, `UnitUpdate`) — prova: `./backend/mvnw -f backend/pom.xml test -Dtest='OpenApiContractTest,ContractRoutesTest'` → passou.
- T6 — arquivos: `frontend/src/mocks/handlers.ts`, `useUnitSettings.ts`, `Text.tsx` (extraído de `NewUnitPage.tsx`), `pt-BR.json` (`unitSettings.*`, `units.configure`), `menuCatalog.ts` (`available: true`), `buildMenu.test.ts`, `UnitsPage.tsx` (link "Configurar"), `masks.ts`/`masks.test.ts` (`formatCep`), `mocks/unit-settings.msw.test.ts` — prova: `npx vitest run src/mocks src/features/permissions src/features/units src/components/fields` → passou.
- T7 — arquivos: `UnitSettingsPage.tsx`, `UnitSettingsPage.test.tsx`, rota em `App.tsx` — prova: `npx vitest run src/features/units` → 30 testes passaram (inclui axe a 360 px nos dois temas).
- T8 — arquivos: `frontend/e2e/configuracao-unidade.spec.ts` — prova: `npm run e2e` → 13 passaram (inclui o novo).
- T9 — ver "Regressão".

Regressão: `./backend/mvnw -f backend/pom.xml verify` → BUILD SUCCESS (122 testes rápidos e 175 no total, 0 falhas; "All coverage checks have been met", JaCoCo 80% e 100% nos pacotes exigidos). Em `frontend/`: `npm run lint && npm run typecheck && npm run build && npm run test:cov && npm run e2e` → tudo passou (335 testes; cobertura 94,91% instruções, 97,17% linhas, 90,91% ramos; e2e 13 de 13). Sem piora em relação à linha de base. Docker: nenhuma imagem nova (só as dos `*IT` já listadas no AGENTS.md).
Desvios:
- Nome do campo da situação da conexão: a especificação (R9, CA1, CA12, CA20) manda `mercadoPago`, mas `NoVendorNamesTest` (RNF-39) reprova qualquer "mercadopago" em `backend/src`. O campo ficou `settings.paymentAccount` (`CONNECTED | NOT_CONNECTED`), no código, no OpenAPI, no MSW e nos testes. O texto de tela segue "Mercado Pago ..." (chaves `unitSettings.mercadoPago*` em `pt-BR.json`, fora da regra do back-end).
- Corpo do PUT e da leitura aninham as configurações em `settings` (como a CA1 já descreve) e `teacherPermissions` é objeto com 4 chaves; "3 ou 5 valores" da CA14 não se aplica, vira "chave ausente ou nula → 400" (testado em `UnitUpdateValidationTest.flags`).
- Teto da taxa de matrícula: R$ 1.000.000,00 (barrar erro de digitação); a especificação só dizia não negativa.
- Auditoria de Pix/CNPJ: antes `[anterior]`, depois `[alterado]` (valores iguais nos dois lados seriam descartados pelo `AuditService` como "sem mudança").
- Erro de unidade de outra organização para Administrador: 404 (igual a inexistente); Administrador de outra unidade da mesma organização: 403.
Não é do item: nada.
Pedido de ajuste (atendido pela fase 2: D13 em "Decisões" troca o nome do campo para `paymentAccount`; o código já bate com ela; reconferido: `OpenApiContractTest,ContractRoutesTest,NoVendorNamesTest,UnitUpdateValidationTest,PixKeyTest` passam): R9 / CA1 / CA12 / CA20 e OpenAPI — o nome `mercadoPago` do campo de resposta conflita com RNF-39 (`NoVendorNamesTest` reprova o nome do fornecedor no back-end). O código usa `paymentAccount`; a especificação precisa trocar o nome do campo (ou o dono decidir outra forma de manter o contrato sem citar o fornecedor no código).

## Verificação

### Rodada 1 — OK — 2026-10-17, estado 8b6253d8b790f4358f1260f008c74d46db129c7d

| Critério | Status | Evidência |
|---|---|---|
| CA1, CA3, CA4, CA7, CA12, CA14 | PASSOU | `UnitSettingsIT` (reads, mineLists, updates, coordinates) 8 testes sem falha no `verify` |
| CA2, CA5, CA16 | PASSOU | `UnitSettingsRouteIT` 3 testes (401/403/404, Administrador de outra unidade, B intacta) |
| CA6, CA8, CA9, CA10, CA11, CA13 | PASSOU | `UnitUpdateValidationTest`, `PixKeyTest`, `UnitSettingsIT.invalid` |
| CA15 | PASSOU | `UnitSettingsIT.audited` e `allOrNothing` (trigger força falha de auditoria e a alteração é desfeita) |
| CA17, CA18, CA19, CA20 | PASSOU | `UnitSettingsPage.test.tsx` dentro de `npm run test:cov` (inclui axe a 360 px nos dois temas) |
| CA21 | PASSOU | `buildMenu.test.ts` |
| CA22 | PASSOU | `OpenApiContractTest` e `ContractRoutesTest` no `verify`; `unit-settings.msw.test.ts` |
| CA23 | PASSOU | `npm run e2e`: 13 de 13, inclui `configuracao-unidade.spec.ts` |

Comandos:
- `./backend/mvnw -f backend/pom.xml verify` → BUILD SUCCESS, 175 testes, 0 falhas, "All coverage checks have been met".
- Em `frontend/`: `npm run lint && npm run typecheck && npm run build && npm run test:cov` → passou; cobertura 94,91% instruções, 97,17% linhas, 90,91% ramos.
- `npm run e2e` → 13 passaram.
- Docker: nenhuma imagem ou container criado pela verificação.

Achados:
- sugestão, código, `UnitSettings.update` — `load(unitId)` (antes) roda antes do `select ... for update`; o plano manda ler a linha anterior já com o bloqueio. Numa edição simultânea o "antes" da auditoria pode estar velho. Inverter a ordem.
- sugestão, especificação, "Desvios" — a CA14 ("3 ou 5 valores") virou "chave ausente ou nula" e o teto da taxa de matrícula (R$ 1.000.000,00) não está em R6; ajustar o texto na próxima passada da especificação.

## Entrega

Configurações › Unidade (RF-ORG-04): `GET /units/mine`, `GET /units/{id}` e `PUT /units/{id}` com validação de CNPJ, chave Pix, taxas em centavos e permissões do professor; auditoria e gravação tudo ou nada; migração V15 (CHECK do CNPJ sem máscara); tela de configuração com os quatro estados, link "Configurar" na lista, menu liberado, MSW e OpenAPI atualizados.

Verificação: `verify` do back-end (175 testes, cobertura exigida cumprida) e lint, typecheck, build, `test:cov` e e2e (13 de 13) do front-end; rodada 1 da verificação OK.

Decisões e limitações: o campo da situação da conexão se chama `paymentAccount` (RNF-39 proíbe o nome do fornecedor no back-end); teto da taxa de matrícula de R$ 1.000.000,00; na auditoria de Pix/CNPJ o valor vira `[anterior]`/`[alterado]`. Sugestão aberta da verificação: ler a linha anterior depois do `select ... for update` em `UnitSettings.update`.
