# 28 — RF-ORG-01 · Configurações › Organização

Origem: issue #28 (Sprint 03); `docs/especificacao/01-organizacao-e-isolamento.md` (RF-ORG-01, RN-ORG-01 a 03), `00-visao-geral.md` (RNF-05, RNF-08, RNF-20, RNF-22, RNF-31). Branch: `item/28-configuracoes-organizacao`.

## 1. Objetivo

Dar ao Coordenador a tela e a API para manter os dados da organização: nome, logo (quadrada, 512 px), cor principal com contraste AA garantido e titular (CPF ou CNPJ). O Administrador vê, não altera. Os dados da marca ficam prontos para os lugares que a mostram (RNF-22); aqui a marca passa a valer no modelo base de e-mail.

Fora do escopo: "Última alteração em [data] por [pessoa]" na tela e consulta da auditoria (#35; aqui a alteração já grava a auditoria com antes e depois); marca no portal, na carteirinha e nos recibos (telas que ainda não existem; cada item as monta com o dado e a regra de contraste daqui); logo dentro do e-mail (D5); credenciais de integração (RNF-08, ver R9); dados da unidade (#31); transferir coordenação (#33).

## 2. Requisitos

- R1 (RF-ORG-01, leitura): `GET /api/v1/organization` devolve nome, cor, titular e a URL temporária assinada da logo da organização do vínculo ativo; permitido a Coordenador e Administrador, negado ao Professor e ao portal.
- R2 (RF-ORG-01, alteração): `PATCH /api/v1/organization` altera nome, cor principal e titular; só o Coordenador (nível `FULL` em `ORGANIZATION_SETTINGS`); Administrador recebe 403 sem efeito.
- R3 (nome, RN-ORG): nome obrigatório, sem ser só espaços, até 120 caracteres (D2).
- R4 (titular): o titular é CPF ou CNPJ com dígitos verificadores válidos, guardado só com dígitos; pode ficar em branco (D3).
- R5 (cor, RNF-22): a cor principal é `#RRGGBB` e precisa dar contraste mínimo de 4,5:1 com texto branco (AA); abaixo disso o servidor recusa com 422 e nada é salvo; pode ficar em branco (cor padrão da plataforma) (D4).
- R6 (logo, RF-ORG-01): `PUT /api/v1/organization/logo` recebe JPG, PNG ou WebP (tipo conferido pelo conteúdo, não pelo nome), exatamente 512 x 512 px e até 5 MB; só o Coordenador. Outro tipo, outro tamanho em pixels ou arquivo corrompido: 422 sem efeito. `DELETE /api/v1/organization/logo` remove a logo (D6).
- R7 (arquivos, RN-ORG-03): a logo fica na pasta da organização via `FileStorage`; a tela e a API só recebem URL temporária assinada, nunca o caminho do armazenamento. Trocar a logo apaga o arquivo anterior.
- R8 (auditoria): nome, cor, titular e logo alterados gravam na auditoria o valor antes e depois (logo: só o nome do arquivo; titular: sem expor além do que a especificação já audita); nada de URL assinada. Chamada sem mudança de valor não grava.
- R9 (RNF-08): a resposta de `GET /organization` não traz nenhum segredo nem credencial de integração; a regra é afirmada em teste sobre o formato da resposta (esta tela não guarda credenciais) (D7).
- R10 (isolamento, RNF-31): as quatro rotas só enxergam e alteram a organização do vínculo ativo; identificador de organização enviado pelo cliente é ignorado; teste de isolamento entre organizações e entre perfis em cada rota.
- R11 (tela): `Configurações › Organização` (`/configuracoes/organizacao`) mostra e edita os campos; logo com envio, corte em quadrado e saída 512 px feitos no navegador; cor com seletor, campo hexadecimal e prévia de contraste (razão e aprovado ou reprovado, com texto e ícone, nunca só cor); para o Administrador a tela é só leitura (campos desabilitados, sem botões de salvar).
- R12 (estados e acessibilidade): carregando, erro com "Tentar de novo", sem permissão (Professor e portal veem a tela de área indisponível) e vazio (organização sem logo, cor e titular mostra o estado padrão de cada campo); funciona a partir de 360 px, axe sem violações nos dois temas, rótulo em todo campo, foco visível, alvos de 44 px; todo texto em `pt-BR.json`.
- R13 (menu): o item "Organização" do catálogo passa a `available: true`, aparecendo para Coordenador e Administrador (D8).
- R14 (RNF-22, e-mail): o modelo base de e-mail (`EmailLayout`) aceita a cor principal da organização no botão e traz "feito com ctcore" no rodapé; sem cor, mantém a cor atual (D5).
- R15 (contrato): as quatro rotas entram em `docs/openapi.yaml` e nos handlers MSW, idênticos à implementação.

## 3. Critérios de aceite

- CA1 (R1): dado Coordenador da org A, quando chama `GET /organization`, então 200 com nome, cor, titular e `logoUrl` assinada (ou nula sem logo) da A; Administrador também recebe 200.
- CA2 (R1, R10): dado Professor, usuário de portal ou sem vínculo ativo, quando chama `GET /organization`, então 403 (anônimo, 401).
- CA3 (R2): dado Coordenador, quando envia `PATCH` com nome, cor e titular válidos, então 200 com os novos valores e uma nova leitura os devolve.
- CA4 (R2): dado Administrador, quando envia `PATCH`, `PUT` ou `DELETE` de logo, então 403 e os dados não mudam.
- CA5 (R3): dado `PATCH` com nome em branco, só espaços ou com 121 caracteres, então 422 com o campo apontado e nada salvo.
- CA6 (R4): dado titular com CPF e outro com CNPJ válidos (com ou sem máscara), então salva só os dígitos; dígito verificador errado, tamanho errado ou letras: 422; vazio: salva nulo.
- CA7 (R5): dado cor `#1a56db`, então salva; cor com contraste menor que 4,5:1 com branco (ex.: `#ffeb3b`), formato inválido (`azul`, `#fff`) ou sem `#`: 422 e nada salvo; vazio: salva nulo. Teste da função de contraste com valores de referência conhecidos (preto 21:1, branco 1:1, limite 4,5).
- CA8 (R6): dado PNG, JPEG e WebP de 512 x 512, quando enviados por `PUT /organization/logo`, então 204/200 e a leitura devolve a URL assinada que baixa a mesma imagem.
- CA9 (R6): dado arquivo `.png` que na verdade é texto ou executável, imagem de 511 x 512 ou 1024 x 1024, GIF, SVG, arquivo vazio ou acima de 5 MB, então 422 e a logo anterior permanece.
- CA10 (R6): dado Coordenador com logo, quando chama `DELETE /organization/logo`, então a leitura volta com `logoUrl` nula e o arquivo some do armazenamento; sem logo, a chamada repete sem erro (idempotente).
- CA11 (R7): dado troca de logo, então o arquivo antigo não existe mais no armazenamento, o novo está na pasta da organização, e a coluna guarda só o nome do arquivo; a URL devolvida expira (assinada).
- CA12 (R8): dado `PATCH` que muda nome e cor, então há registros de auditoria `organization.update` com antes e depois só dos campos alterados, ator, perfil e organização; `PATCH` com os mesmos valores, ou recusado, não cria registro; troca e remoção de logo geram `organization.logo.update` e `organization.logo.remove`; nenhum registro contém URL assinada.
- CA13 (R9): dado `GET /organization`, então as chaves da resposta são exatamente as do contrato (sem campos extras de integração ou segredo).
- CA14 (R10): dado usuário da org A que forja `organizationId` no corpo, no caminho ou no cabeçalho, ou usa o identificador da B, então lê e altera só a A; a org B permanece idêntica (modelo `RouteIsolationModelIT` nas quatro rotas, com perfis Coordenador, Administrador, Professor, portal e anônimo).
- CA15 (R11, R12): dado a tela aberta pelo Coordenador, quando altera nome, cor e titular e salva, então aparece confirmação e os valores novos; erro 422 do servidor aparece junto do campo; cor reprovada mostra a razão e "reprovado" por texto e ícone e bloqueia o salvar.
- CA16 (R11): dado envio de imagem, quando o Coordenador escolhe o arquivo, então abre o corte quadrado, ao confirmar o navegador gera 512 x 512 e envia; arquivo de tipo não aceito ou maior que 5 MB é recusado no navegador com mensagem; "Remover logo" pede confirmação.
- CA17 (R11): dado o Administrador, então a tela mostra os dados sem botão de salvar, envio ou remoção, com os campos somente leitura.
- CA18 (R12): dado resposta carregando, erro e Professor, então `LoadingState`, `ErrorState` com "Tentar de novo" e a tela de área indisponível; organização sem logo, cor e titular mostra os campos vazios com o texto de padrão; 360 px sem rolagem horizontal; axe sem violações nos dois temas.
- CA19 (R13): dado o catálogo do menu, então "Organização" aparece para Coordenador e Administrador e não para o Professor.
- CA20 (R14): dado `EmailLayout` com cor `#1a56db`, então o botão usa essa cor e o rodapé traz "feito com ctcore"; sem cor, o botão mantém o `#1a56db` de hoje; o texto do rodapé continua escapado.
- CA21 (R15): `OpenApiContractTest` e `ContractRoutesTest` passam com as quatro rotas; os handlers MSW devolvem o mesmo formato e os mesmos erros por perfil.
- CA22 (R3 a R7, ponta a ponta `@critical`): dado o Coordenador do mock, quando abre Configurações › Organização, altera o nome e a cor e salva, então vê o novo nome após recarregar; o Administrador do mock vê a tela sem botão de salvar.

## 4. Plano técnico

- **Dados.** Sem migração de tabela: `organization` (V3) já tem `name`, `logo_url`, `color`, `holder_document`, com RLS. `logo_url` passa a guardar o nome do arquivo no `FileStorage` (nunca URL), sem renomear a coluna (migração destrutiva sem ganho). Avaliar um `check` em `color` (`~ '^#[0-9a-f]{6}$'`) e em `holder_document` (11 ou 14 dígitos) em `V12__organization_brand_checks.sql` (a criar; reversão: `drop constraint`); como as colunas hoje estão vazias na seed, a migração é segura; conferir `infra/seed/01-organizations.sql` antes. Seed: sem mudança; opcionalmente acrescentar cor e titular fictícios em `infra/seed/04-organization-brand.sql` (a criar, `on conflict do nothing` não se aplica a update: usar `update ... where color is null`).
- **API.** `GET /api/v1/organization` (200 `OrganizationView { name, color, holderDocument, logoUrl }`; 401; 403), `PATCH /api/v1/organization` (corpo `{ name, color, holderDocument }`; 200 com a visão; 403; 422), `PUT /api/v1/organization/logo` (`multipart/form-data`, campo `file`; 200 com a visão; 403; 422), `DELETE /api/v1/organization/logo` (200 com a visão; 403). Cookie de sessão e cabeçalho CSRF como as demais rotas de escrita. Quem chama: leitura `grant(ORGANIZATION_SETTINGS, null) != NONE` (Coordenador `FULL`, Administrador `READ`); escrita `== FULL`. Resposta de acesso negado não nomeia recurso (`FORBIDDEN` do `ApiExceptionHandler`).
- **Back-end** (pacote `br.com.ctcore.organization`, existente; `Organizations.java` hoje é só provisionamento):
  - `OrganizationProfile` (`@Service`, a criar): `read`, `update`, `replaceLogo`, `removeLogo`. Leitura e escrita via `OrganizationTransactions` (RLS ativa), organização sempre do `OrganizationContext`/principal. Permissão por `Permissions.grant(principal, ORGANIZATION_SETTINGS, null)`. Atualização e auditoria na mesma transação (`AuditService.record`, `MANDATORY`). Troca de logo: grava o novo arquivo, atualiza a coluna e a auditoria na transação e, só depois do commit, apaga o antigo (falha ao apagar vai para o log e não desfaz).
  - `OrganizationController` (a criar): as quatro rotas, DTOs `record`, validação na borda (tamanho do corpo, tipo declarado).
  - `BrandColor` (a criar): valida `#RRGGBB` e calcula razão de contraste WCAG com branco (luminância relativa); limite 4,5.
  - `HolderDocument` (a criar): valida e normaliza CPF e CNPJ (não existe validador no back-end; fonte: regra do `isValidCpf` do front para manter igual).
  - `LogoImage` (a criar): detecta o tipo pelos bytes (PNG, JPEG, WebP), lê largura e altura sem decodificar a imagem inteira (PNG: IHDR; JPEG: marcador SOF; WebP: `VP8`, `VP8L`, `VP8X`) e exige 512 x 512; ignora o `Content-Type` e o nome enviados. Nome gravado: `logo-<uuid>.<ext>`.
  - `mail/EmailLayout` (existente): parâmetro opcional de cor do botão e rodapé "feito com ctcore" (texto fixo em constante; o rodapé do e-mail segue o padrão de mensagens `messages.properties`). `EmailService` (existente) chama sem cor até um e-mail precisar da marca.
  - Reuso: `Permissions`, `OrganizationTransactions`, `OrganizationContext`, `AuditService`/`AuditEntry`/`AuditActor`, `FileStorage`/`S3FileStorage`, `ApiExceptionHandler`, `RouteIsolationModelIT` (molde do #15, usado no `PermissionRouteIT`).
  - Testes: `BrandColorTest`, `HolderDocumentTest`, `LogoImageTest` (imagens geradas no teste), `OrganizationProfileIT` (banco e armazenamento reais: CA3, CA5 a CA13), `OrganizationRouteIT` (estende `RouteIsolationModelIT`: CA2, CA4, CA14), `EmailLayoutTest` (CA20).
- **Segurança.** RNF-05: o servidor decide só pelo vínculo ativo; `PUT` valida o conteúdo, não o nome nem o tipo declarado; limite de 5 MB (também no `spring.servlet.multipart` / tamanho máximo da requisição); URL assinada e temporária (RN-ORG-03); a coluna nunca guarda URL; saída escapada no e-mail; auditoria sem dado sensível. Titular (CPF/CNPJ) é dado pessoal quando CPF: finalidade é identificar o titular da assinatura, visível só a Coordenador e Administrador; guardado só com dígitos; base legal e retenção ficam como estão para dados da organização (D3, suposição).
- **Front-end** (`frontend/src/features/organization/`, a criar):
  - `OrganizationSettingsPage.tsx` (rota `/configuracoes/organizacao` dentro de `RequireArea` com `ORGANIZATION_SETTINGS` e do `ManagementLayout`, existentes; registrar em `App.tsx`), `useOrganization.ts` (consulta e mutações), `LogoCropDialog.tsx` (corte quadrado em `canvas` nativo, saída 512 x 512, sem biblioteca nova; recorte por arraste e zoom simples), `ColorField.tsx` com `contrastRatio.ts` (mesma fórmula do servidor, testada com os mesmos valores de referência).
  - Reuso: `fields.tsx` e `masks.ts` (`formatCpf`, `isValidCpf`; conferir se há CNPJ, senão acrescentar lá), `Card`, `Modal`, `ConfirmDialog`, `QueryPage`/`states`, `button.tsx`.
  - `menuCatalog.ts`: `organizationSettings` com `available: true` (hoje false). `pt-BR.json`: chaves `organization.*`. `mocks/handlers.ts`: as quatro rotas por papel do vínculo ativo (Coordenador escreve, Administrador lê, demais 403) e estado em memória.
  - Testes: `OrganizationSettingsPage.test.tsx` (CA15 a CA18, axe claro e escuro, 360 px), `LogoCropDialog.test.tsx` (CA16), `contrastRatio.test.ts` (CA7), `buildMenu.test.ts` ajustado (CA19), `e2e/organizacao.spec.ts` `@critical` (CA22). Cobertura do front >= 80%.
  - Canvas: `ConfigAcademia.dc.html` e `EmailBase.dc.html` não estão acessíveis ao agente; a tela segue o texto da issue e os padrões de tela existentes (Suposição).
- **Arquivos a alterar (vistos no repositório):** `backend/src/main/java/br/com/ctcore/mail/EmailLayout.java`, `mail/EmailService.java` (se a assinatura mudar), `docs/openapi.yaml`, `frontend/src/App.tsx`, `frontend/src/features/permissions/menuCatalog.ts`, `frontend/src/features/permissions/buildMenu.test.ts`, `frontend/src/mocks/handlers.ts`, `frontend/src/locales` (arquivo `pt-BR.json`; conferir o caminho antes), `backend/src/main/resources/application.yml` (limite de multipart).
- **Riscos.** Leitura de dimensões de WebP e JPEG feita à mão (cobrir com imagens reais dos três formatos nos testes); gravar o arquivo novo e a coluna de forma não atômica (por isso o apagamento do arquivo antigo só depois do commit e arquivo órfão aceito se o commit falhar, registrado no log); `Permissions.grant` com unidade nula só vale para linhas de escopo `ALL` (é o caso desta linha); corte de imagem em canvas depende do navegador (testar com `jsdom` simulado e cobrir no e2e só o fluxo de texto).

## 5. Tarefas

- [x] T1: `BrandColor` (formato e razão de contraste), `HolderDocument` (CPF/CNPJ) e `LogoImage` (tipo e 512 x 512 de PNG, JPEG e WebP) — cobre CA6, CA7, CA9 — prova: `./backend/mvnw -f backend/pom.xml test -Dtest='BrandColorTest,HolderDocumentTest,LogoImageTest'`.
- [x] T2: migração `V12__organization_brand_checks.sql` (checks de cor e titular, com reversão descrita no próprio arquivo) — não cobre critério: a regra de cor e titular fica também no banco (RNF de integridade), conferida em T3 — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test=OrganizationProfileIT`.
- [x] T3: `OrganizationProfile` com leitura, atualização, troca e remoção de logo, permissão `FULL`/`READ`, auditoria e armazenamento — cobre CA1, CA3, CA5, CA8, CA10, CA11, CA12, CA13 — prova: `verify -Dit.test=OrganizationProfileIT`.
- [x] T4: `OrganizationController` (quatro rotas), limite de multipart e erros 403/422 — cobre CA2, CA4, CA9 — prova: `verify -Dit.test=OrganizationRouteIT`.
- [x] T5: teste de isolamento entre organizações e perfis nas quatro rotas, pelo molde `RouteIsolationModelIT` — cobre CA14 — prova: `verify -Dit.test=OrganizationRouteIT`.
- [x] T6: `EmailLayout` com cor opcional e rodapé "feito com ctcore" — cobre CA20 — prova: `./backend/mvnw -f backend/pom.xml test -Dtest=EmailLayoutTest`.
- [x] T7: OpenAPI e teste de contrato das quatro rotas — cobre CA21 — prova: `./backend/mvnw -f backend/pom.xml test -Dtest='OpenApiContractTest,ContractRoutesTest'`.
- [x] T8: front, base: `contrastRatio.ts`, handlers MSW das quatro rotas, `useOrganization`, textos `organization.*`, item do menu `available: true` — cobre CA7, CA19, CA21 — prova: `npx vitest run src/features/organization src/features/permissions src/mocks`.
- [x] T9: `LogoCropDialog` (tipo, tamanho e corte 512 x 512) — cobre CA16 — prova: `npx vitest run src/features/organization/LogoCropDialog`.
- [x] T10: `OrganizationSettingsPage` com os quatro estados, modo leitura do Administrador, rota em `App.tsx`, axe e 360 px — cobre CA15, CA17, CA18 — prova: `npx vitest run src/features/organization/OrganizationSettingsPage`.
- [x] T11: e2e `@critical` do Coordenador e do Administrador — cobre CA22 — prova: `npm run e2e`.
- [x] T12: regressão do item (fecha a definição de pronto; inclui a cobertura mínima e a seed) — não cobre critério: confere que nada quebrou — prova: `./backend/mvnw -f backend/pom.xml verify` e, em `frontend/`, `npm run lint && npm run typecheck && npm run build && npm run test:cov && npm run e2e`.

## 6. Decisões

- D1 (muda o comportamento): rotas no recurso `organization` (`/api/v1/organization`), sem o identificador na URL: a organização é a do vínculo ativo, nunca enviada pelo cliente (RNF-05, RN-ORG-01). Alternativa descartada: `/organizations/{id}`, que abriria espaço para forjar identificador.
- D2: nome de até 120 caracteres; o card não fixa o limite. Se a especificação definir outro, muda só a constante e o teste.
- D3 (muda o comportamento): "titular" é o CPF ou CNPJ do titular (coluna `holder_document`, `01-organizacao-e-isolamento.md` §Organização), opcional e validado pelos dígitos. Suposição: o card não pede nome do titular, só o documento; se pedir, entra coluna nova. LGPD: base legal e retenção do CPF seguem as dos dados da organização; se o dono entender que CPF de titular exige tratamento próprio, vira pendência.
- D4 (muda o comportamento): contraste AA = razão mínima 4,5:1 da cor principal com texto branco; abaixo disso o servidor recusa e a tela bloqueia, em vez de escurecer a cor sozinho. Motivo: "contraste AA garantido" (RNF-22) e previsível para quem escolhe. Alternativa descartada: ajuste automático da cor, que altera o que o dono escolheu sem avisar.
- D5: no e-mail só a cor e o rodapé entram agora. A logo não vai no e-mail porque a URL assinada expira e e-mails ficam guardados; precisa de endereço público estável da marca, que é decisão do primeiro item que enviar e-mail ao aluno ou do portal. O `EmailLayout` ganha o parâmetro, e a ligação às mensagens ao aluno vem com elas. RNF-22 no portal, na carteirinha e nos recibos fica para os itens dessas telas, que usam `color` e a logo daqui.
- D6: logo trocada por substituição; sem logo nova, `DELETE` remove (a tela volta ao padrão da plataforma). O corte quadrado de 512 px é feito no navegador; o servidor só confere o resultado (exatamente 512 x 512), sem redimensionar. Alternativa descartada: o servidor redimensionar, que exigiria biblioteca de imagem para um ganho pequeno.
- D7: o RNF-08 (credenciais de integração criptografadas e nunca devolvidas) não tem credencial nesta tela. Fica coberto aqui por afirmar que a resposta traz só os campos do contrato; as credenciais reais chegam com as conexões (Sprints 22 e 23), onde a cifra já existe (`identity/SecretCipher`).
- D8 (muda o comportamento): o Administrador vê o item de menu e a tela em modo leitura (nível `READ` da tabela de permissões); o Professor não vê.
- D9: logo e dados voltam sem paginação, uma só organização por vínculo; sem controle de edição simultânea (último a salvar vale). A auditoria guarda o histórico. Alternativa descartada: versão otimista, sem requisito no card.
- Suposição: os canvas `ConfigAcademia.dc.html` e `EmailBase.dc.html` não estão acessíveis ao agente; a tela e o rodapé seguem o texto da issue e a base visual do projeto. Se divergirem, só a tela, os textos e o `EmailLayout` mudam.
- Suposição: a lista de erros de imagem (tipo, dimensão, tamanho) usa 422 como as demais validações do projeto; confirmar com `ApiExceptionHandler` na construção.

## Construção

- T1 — arquivos: `backend/.../organization/{BrandColor,HolderDocument,LogoImage}.java`, testes `BrandColorTest`, `HolderDocumentTest`, `LogoImageTest` — prova: `./backend/mvnw -f backend/pom.xml test -Dtest='BrandColorTest,HolderDocumentTest,LogoImageTest'` → passou. WebP (VP8, VP8L, VP8X) coberto com cabeçalhos montados no teste (não há codificador WebP no projeto); PNG e JPEG com imagens reais geradas por ImageIO.
- T2 — arquivos: `backend/src/main/resources/db/migration/V12__organization_brand_checks.sql` — prova: `verify -Dit.test=OrganizationProfileIT` → `databaseChecks` passou (banco recusa cor e titular fora do formato).
- T3 — arquivos: `organization/OrganizationProfile.java`, `OrganizationProfileIT.java`, `OrganizationBase.java` (SeaweedFS e PostgreSQL reais) — prova: `verify -Dit.test='OrganizationProfileIT,OrganizationRouteIT'` → 12 testes, 0 falhas.
- T4 — arquivos: `organization/OrganizationController.java`, `api/error/ApiExceptionHandler.java` (arquivo acima do limite), `application.yml` (multipart 5 MB / 6 MB) — prova: mesma execução acima → passou.
- T5 — arquivos: `OrganizationRouteIT.java` — prova: mesma execução → passou (ver Desvios sobre o molde).
- T6 — arquivos: `mail/EmailLayout.java`, `EmailLayoutTest.java` — prova: `test -Dtest=EmailLayoutTest` → passou.
- T7 — arquivos: `docs/openapi.yaml` (quatro rotas, `OrganizationView`, `OrganizationUpdateRequest`) — prova: `test -Dtest='OpenApiContractTest,ContractRoutesTest'` → 7 testes passaram.
- T8 — arquivos: `frontend/src/features/organization/{contrastRatio.ts,contrastRatio.test.ts,logoFile.ts,useOrganization.ts}`, `mocks/handlers.ts`, `mocks/organization.msw.test.ts`, `i18n/pt-BR.json`, `features/permissions/{menuCatalog.ts,buildMenu.test.ts}`, `lib/api.ts` (aceita FormData), `components/fields/masks.ts` (+CNPJ e titular, com teste) — prova: `npx vitest run src/features/organization src/features/permissions src/mocks` → passou.
- T9 — arquivos: `LogoCropDialog.tsx`, `LogoCropDialog.test.tsx` — prova: `npx vitest run src/features/organization/LogoCropDialog` → 6 passaram.
- T10 — arquivos: `OrganizationSettingsPage.tsx`, `ColorField.tsx`, `OrganizationSettingsPage.test.tsx`, `App.tsx` (rota dentro de `RequireArea`), `components/Card.tsx` (prop `heading`, para o título da tela ser h1 e os cartões h2; padrão segue h3) — prova: `npx vitest run src/features/organization/OrganizationSettingsPage` → 14 passaram, axe sem violações nos dois temas a 360 px.
- T11 — arquivos: `frontend/e2e/organizacao.spec.ts` — prova: `npm run e2e` → 10 passaram (incluindo o novo `@critical`).
- T12 — regressão abaixo.

Regressão: `./backend/mvnw -f backend/pom.xml verify` → BUILD SUCCESS (107 testes rápidos e 145 de integração, JaCoCo aprovado). Em `frontend/`: `npm run lint` e `npm run typecheck` sem erros, `npm run build` ok, `npm run test:cov` → 42 arquivos / 297 testes, cobertura 97,89% de linhas (mínimo 80%), `npm run e2e` → 10 passaram. Sem piora em relação à linha de base. Não rodou: `e2e:full` (o AGENTS.md exige só `@critical` fora do PR para `main`) e a seed opcional `infra/seed/04-organization-brand.sql` (não criada: sem requisito).

Desvios:
- Erros de validação respondem 400 `VALIDATION_ERROR` (padrão do `ApiExceptionHandler` e do OpenAPI do projeto), não 422: a suposição do arquivo ("422 como as demais validações") não bate com o código. Os CA que dizem 422 valem como 400 com o campo apontado.
- CA14 / T5: o molde `RouteIsolationModelIT` pressupõe identificador na URL (leitura de B por A igual a "não existe"); as rotas não têm identificador (D1). `OrganizationRouteIT` afirma as mesmas garantias (anônimo 401, sem vínculo/Professor/portal 403, Administrador sem escrita, `organizationId` forjado em cabeçalho, parâmetro e corpo ignorado, B intacta, caminho com id é 404) sem estender o molde.
- Logo: PNG e JPEG são decodificados de verdade (arquivo cortado é recusado); WebP só pelo cabeçalho e pelo tamanho do RIFF (marcado com `ponytail:` em `LogoImage`).
- Titular na auditoria: gravado mascarado (só os dois últimos dígitos), por "sem expor além do que a especificação já audita".
- Mock e e2e (CA22): a sessão simulada vive na memória da página e recarregar perde a sessão; o e2e navega pelo roteador e volta à tela (nova consulta) em vez de `page.reload()`. O mock de `PUT /organization/logo` lê o corpo como texto, porque o parser multipart do Node recusa o `File` do jsdom nos testes.
- `Card` ganhou a prop opcional `heading` (axe: sem salto de nível de título); `masks.ts` ganhou `isValidCnpj`, `formatCpfCnpj`, `isValidCpfOrCnpj`; `api()` aceita `FormData`.
- Seed opcional e `AGENTS.md`/`projeto.md`: sem comando nem estrutura nova, nada a atualizar.

Não é do item: nenhuma mudança alheia na pasta de trabalho além dos arquivos da fase 1 já sem commit.

## Verificação

### Rodada 1 — OK — 2026-10-17, estado faea9f285a072ec66395e9ea611970c15f90dd06

| Critério | Status | Evidência |
|---|---|---|
| CA1, CA13 | PASSOU | `OrganizationProfileIT.read` (verify, 145 IT sem falha) |
| CA2, CA4 | PASSOU | `OrganizationRouteIT.anonymousAndDeniedProfiles`, `administratorReadsOnly` |
| CA3, CA5, CA6, CA7 | PASSOU | `OrganizationProfileIT.update`, `validation`, `databaseChecks`; `BrandColorTest`, `HolderDocumentTest`, `contrastRatio.test.ts` (resposta é 400 `VALIDATION_ERROR`, não 422; ver sugestão 1) |
| CA8, CA9, CA10, CA11 | PASSOU | `OrganizationProfileIT.logoReplace/logoInvalid/logoRemove`, `LogoImageTest` (PNG/JPEG reais; WebP por cabeçalho) |
| CA12 | PASSOU | `OrganizationProfileIT.auditTrail` |
| CA14 | PASSOU | `OrganizationRouteIT.forgedOrganizationIsIgnored`, `OrganizationProfileIT.logoIsolated` (molde sem id na URL, desvio justificado por D1) |
| CA15 a CA18 | PASSOU | `OrganizationSettingsPage.test.tsx` (14), `LogoCropDialog.test.tsx` (6), axe nos dois temas a 360 px dentro dos testes; `test:cov` 297 testes |
| CA19 | PASSOU | `buildMenu.test.ts` |
| CA20 | PASSOU | `EmailLayoutTest` (leitura do diff: só `#rrggbb` chega ao atributo `style`) |
| CA21 | PASSOU | `verify` completo inclui `OpenApiContractTest` e `ContractRoutesTest`; `organization.msw.test.ts` |
| CA22 | PASSOU | `npm run e2e`: 10 passaram; confere a nova consulta pela navegação, não por `page.reload()` (ver sugestão 2) |

Comandos:
- `./backend/mvnw -f backend/pom.xml verify` -> BUILD SUCCESS, 145 IT sem falha, JaCoCo "All coverage checks have been met".
- `npm run lint && npm run typecheck && npm run build && npm run test:cov && npm run e2e` (frontend) -> exit 0; 42 arquivos / 297 testes; 97,89% de linhas; e2e 10 passaram.

Achados:
- sugestão, especificação, CA3 a CA9 e R5/R6 dizem 422; a implementação e o OpenAPI usam 400 `VALIDATION_ERROR` (padrão do projeto, já registrado em Desvios) — ajustar o texto dos CA para 400 na próxima passada de especificação.
- sugestão, especificação/código, CA22 pede "após recarregar"; o mock perde a sessão no reload, então o e2e volta à tela. O servidor real persiste (coberto por IT); sem ação obrigatória.
- sugestão, código, `LogoImage.webp` valida só cabeçalho e tamanho RIFF (já marcado `ponytail:`); aceita WebP truncado no corpo. Aceitável para a logo.

## Entrega

- Configurações › Organização: leitura e edição do perfil da organização (nome, cor da marca, titular CPF/CNPJ) e logo (envio com recorte, troca e remoção), com isolamento por organização no código e no PostgreSQL (V12 com checks de cor e titular). Coordenador escreve, Administrador só lê, demais perfis recebem 403.
- A cor da marca e a logo entram no rodapé dos e-mails (`EmailLayout`, só `#rrggbb` chega ao `style`). Auditoria de toda alteração; titular gravado mascarado.
- Verificação: `verify` do back-end (145 IT), lint, typecheck, build, `test:cov` (97,89%) e `e2e` (10) verdes; contrato OpenAPI e MSW conferidos.
- Decisões e limitações: erros de validação respondem 400 `VALIDATION_ERROR` (não 422); WebP validado só por cabeçalho e tamanho RIFF; o mock perde a sessão ao recarregar, então o e2e volta à tela pela navegação.

Refs #28
