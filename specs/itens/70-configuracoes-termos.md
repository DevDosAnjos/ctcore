# 70 — RF-MAT-05 · Configurações › Termos

Origem: issue #70 (Sprint 07); `docs/especificacao/09-matricula.md` (RF-MAT-05, RN-MAT-14; RN-MAT-11 e RN-MAT-12 como contexto), `01-organizacao-e-isolamento.md` (termos são da organização, padrão "modelo da plataforma"; só o Coordenador altera), `03-auditoria-e-lgpd.md` (aceite guarda versão do texto). Branch: `item/70-configuracoes-termos`. Segue o desenho de #41 (`AgeRanges`, `AgeRangesPage`, `V22`). Canvas `ConfigTermos.dc.html` inacessível ao agente.

## 1. Objetivo

O Coordenador edita, em Configurações › Termos, o texto do termo de matrícula e o do termo de uso de imagem, publica uma nova versão e escolhe, a cada publicação, se ela vale só para novos alunos ou se todos aceitam de novo no próximo acesso. O Administrador consulta o texto e o histórico. Cada versão publicada fica guardada e não muda mais, para que o aceite do portal (#85) aponte uma versão exata.

Fora do escopo: o aceite pelo portal, o selo "Termo pendente" e a exigência de reaceite no acesso (#85, Sprint 10); a revogação do uso de imagem no portal (RF-POR-07); o modelo da plataforma editável pelo painel (RF-PNL-07, Sprint posterior): aqui o modelo é um texto padrão fixo no servidor (D1).

## 2. Requisitos

- R1 (RF-MAT-05, D1, D2): `GET /api/v1/organization/terms/{kind}`, com `kind` `ENROLLMENT` (termo de matrícula) ou `IMAGE_USE` (uso de imagem), devolve `canManage`, `current` (`version`, `body`, `reaccept`, `publishedAt`, `publishedBy`; sem versão publicada: `version` 0, texto do modelo, `reaccept` falso, sem data nem autor) e `versions` (publicadas, da mais nova para a mais antiga, com os mesmos campos).
- R2 (RF-MAT-05, RN-MAT-14, D3): `POST /api/v1/organization/terms/{kind}/versions` com `body` e `reaccept` (booleano obrigatório) publica a versão seguinte (anterior + 1, a primeira é 1), vira a atual e responde 201 com a versão.
- R3 (RN-MAT-14): `reaccept` verdadeiro grava "todos aceitam de novo no próximo acesso"; falso, "vale só para novos alunos". O valor fica na versão para a #85 consultar.
- R4 (D4): `body` obrigatório, de 20 a 20000 caracteres depois de aparar as pontas; texto igual ao da versão atual (ou ao modelo, se não houve publicação) é recusado com 400 no campo `body`; violação não grava nada. `kind` fora da lista: 404.
- R5 (D5): versão publicada é imutável: não há rota de alterar nem apagar, e o papel de execução do banco não tem UPDATE nem DELETE na tabela.
- R6 (permissão, D6): `ORGANIZATION_SETTINGS` conferido no servidor: leitura para Coordenador e Administrador; publicar só com acesso `FULL` (Coordenador); Administrador 403 ao publicar; Professor, Aluno e Responsável 403 em tudo; sem sessão 401.
- R7 (isolamento): termos são sempre da organização da sessão; duas organizações não veem as versões uma da outra; filtro no código e row-level security na tabela nova.
- R8 (auditoria): publicar grava `terms.published` (ator, perfil, `kind`, `version`, `reaccept`), sem o texto; recusas não auditam.
- R9 (concorrência): duas publicações simultâneas do mesmo tipo resultam em uma 201 e uma 409 `TERMS_VERSION_CONFLICT`; nunca duas versões com o mesmo número.
- R10 (tela): `/configuracoes/termos` mostra os dois termos (um por aba), cada um com a versão atual, o editor do texto, a escolha de reaceite ("Só para novos alunos" ou "Todos aceitam de novo no próximo acesso", sem opção marcada de início) e "Publicar nova versão", mais o histórico (versão, data, quem publicou, reaceite) com leitura do texto de cada versão. Sem `canManage` mostra só leitura. Tem os quatro estados, funciona a partir de 360 px, passa no axe nos dois temas, textos em `pt-BR.json`, e entra no menu de Configurações com `ORGANIZATION_SETTINGS`.
- R11 (contrato): `docs/openapi.yaml` e os handlers MSW refletem as duas rotas; o seed de dados fictícios acrescenta uma versão publicada a uma organização de teste.
- R12 (encaixe da Sprint 06): o comentário de `StudentSeal.java` que aponta esta issue passa a apontar a #85.

## 3. Critérios de aceite

- CA1 (R1): dado organização sem publicação, quando o Coordenador lê `ENROLLMENT`, então 200, `current.version` 0 com o texto do modelo, `versions` vazia e `canManage` verdadeiro; `IMAGE_USE` devolve o seu modelo.
- CA2 (R2, R3): dado Coordenador, quando publica `body` válido com `reaccept` falso, então 201, versão 1, e a leitura devolve `current` igual a ela; publicando de novo com `reaccept` verdadeiro, versão 2, `reaccept` verdadeiro, e `versions` traz 2 e 1 nessa ordem; os dois tipos têm numeração independente.
- CA3 (R4): dado `body` ausente, com 19 caracteres, com 20001, só com espaços, ou `reaccept` ausente, quando publica, então 400 com o campo e nada é gravado; 20 e 20000 caracteres são aceitos; texto igual ao da versão atual dá 400 em `body`; `kind` desconhecido dá 404.
- CA4 (R5): dado a tabela de versões, quando `ctcore_app` tenta `update` ou `delete`, então o banco nega; não existe rota `PUT` nem `DELETE` para versões.
- CA5 (R6): dado Administrador, quando lê, então 200 com `canManage` falso; quando publica, 403 e nada muda; Professor, Aluno e Responsável 403 nas duas rotas; sem sessão 401.
- CA6 (R7): dado duas organizações com versões publicadas, quando o Coordenador de A lê e publica, então só vê e numera as da A, e a B segue idêntica; com `ctcore_app`: sem contexto 0 linhas, com o contexto de A só as da A, insert com a organização B negado.
- CA7 (R8): dado cada publicação, então a auditoria tem `terms.published` com ator, perfil, `kind`, `version` e `reaccept`, sem o texto; recusas (400, 403, 404, 409) não auditam.
- CA8 (R9): dado duas publicações simultâneas do mesmo `kind`, então uma 201, uma 409 `TERMS_VERSION_CONFLICT`, uma só versão nova e uma só linha de auditoria.
- CA9 (R10): dado a tela em 360 px, então sem rolagem horizontal e axe sem violações nos dois temas; estados carregando, vazio de histórico ("Nenhuma versão publicada ainda", com o texto do modelo no editor), erro com "Tentar de novo" e sem permissão (403 na leitura); o Coordenador edita, escolhe o reaceite e publica, e a atual e o histórico se atualizam sem recarregar; "Publicar" fica desabilitado sem alteração ou sem escolha de reaceite; erro 400 e 409 do servidor aparecem na tela; o Administrador vê o texto e o histórico sem editor nem botão.
- CA10 (R10): dado o menu de Configurações, então "Termos" aparece para Coordenador e Administrador e não para Professor.
- CA11 (R11): `OpenApiContractTest` e `ContractRoutesTest` passam com as duas rotas; teste MSW cobre leitura, publicação (201, 400) no formato do servidor; `SeedDataIT` aplica o novo seed.
- CA12 (R12): dado `StudentSeal.java`, então o comentário aponta a #85 e não a #70.

## 4. Plano técnico

- Dados: migração `V38__terms_version.sql` (depois de `V37`): tabela `terms_version` (`id uuid` pk, `organization_id` referência a `organization`, `kind text` com check em `ENROLLMENT`/`IMAGE_USE`, `version int` maior que 0, `body text` com check de tamanho 20 a 20000 depois de `btrim`, `reaccept boolean not null`, `published_by_account_id` referência a `account`, `published_at timestamptz default now()`), único `(organization_id, kind, version)` (trava de corrida, dá o 409), RLS `organization_scope` como em `V22`; só INSERT e SELECT para o papel de execução (como `benefit`, sem DELETE e sem UPDATE); reversão comentada no topo. Sem inserir nada para organizações existentes: o modelo é virtual (D1). Seed `infra/seed/17-termos.sql` (`on conflict do nothing`) com uma versão do termo de matrícula numa organização fictícia; `SeedDataIT` limpa a tabela nova antes de `organization` e testa o seed.
- API (cookie e CSRF como as demais): `GET /organization/terms/{kind}` (200; 401, 403, 404) e `POST /organization/terms/{kind}/versions` (201; 400, 401, 403, 404, 409). Código de erro novo `TERMS_VERSION_CONFLICT` (409) em `ErrorCode`, com mensagem pt-BR em `messages.properties`. Entram em `docs/openapi.yaml` no mesmo PR (`TermsView`, `TermsVersion`, `TermsPublishInput`).
- Back-end: pacote novo `br.com.ctcore.terms` (a criar): `Terms.java` (leitura e publicação; `permissions.grant(who, Permission.ORGANIZATION_SETTINGS, null)` com a dupla `access`/`requireFull` de `AgeRanges`; número = maior versão + 1 numa transação de `OrganizationTransactions`; violação do único vira 409; auditoria com `AuditService`/`AuditEntry`; `ApiValidationException` na validação; constantes dos dois textos do modelo) e `TermsController.java`. Para a #85, `Terms.current(organizationId, kind)` fica público, devolvendo versão e `reaccept`.
- Interface: criar `frontend/src/features/terms/TermsPage.tsx` (abas dos dois termos, `TermEditor`, histórico) e `useTerms.ts` (`useState/useEffect`, como `useAgeRanges.ts`), e `TermsPage.test.tsx`; alterar `App.tsx` (rota `/configuracoes/termos`), `permissions/menuCatalog.ts` (item `terms` com `ORGANIZATION_SETTINGS`, depois de `ageRanges`; ajustar `buildMenu.test.ts` se depender da contagem), `mocks/handlers.ts` (+ `terms.msw.test.ts`, a criar) e `i18n/pt-BR.json`. Reuso: `Card`, componentes de `states/*`, padrão de página de `AgeRangesPage`.
- Segurança: organização e perfil vêm do principal da sessão, nunca do corpo; `kind` validado contra a lista; texto livre mostrado como texto (sem HTML interpretado); parâmetros ligados; CSRF na escrita; o texto do termo não vai para a auditoria nem para os logs.
- Arquivos: a alterar (vistos): `backend/src/main/java/br/com/ctcore/student/StudentSeal.java`, `docs/openapi.yaml`, `frontend/src/App.tsx`, `frontend/src/features/permissions/menuCatalog.ts`, `frontend/src/mocks/handlers.ts`, `frontend/src/i18n/pt-BR.json`, `backend/src/test/java/br/com/ctcore/db/SeedDataIT.java`, `ErrorCode.java`, `messages.properties`. A criar: `V38__terms_version.sql`, `infra/seed/17-termos.sql`, `terms/Terms.java`, `terms/TermsController.java`, `backend/src/test/java/br/com/ctcore/terms/TermsIT.java`, os arquivos da interface acima.
- Riscos: (1) testes que apagam organizações precisam limpar `terms_version` antes (como `age_range`); aparece como falha de chave estrangeira. (2) O texto do modelo é provisório até o painel da plataforma (D1): é marcado como versão 0 e não conta como aceitável por versão. (3) Layout do canvas inacessível: o dono confere no PR.

## 5. Tarefas

- [x] T1: migração `V38__terms_version.sql`, seed `17-termos.sql` e ajuste de `SeedDataIT` — cobre CA4 (permissões da tabela), CA11 (seed) — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test='SeedDataIT,TermsIT'`.
- [x] T2: `Terms`, `TermsController` e `ErrorCode.TERMS_VERSION_CONFLICT` com permissão, validação, auditoria, concorrência e testes de integração (modelo, publicar, numeração por tipo, validação, perfis, isolamento com RLS, imutabilidade, auditoria, corrida) — cobre CA1 a CA8 — prova: `verify -Dit.test=TermsIT` (a criar).
- [x] T3: OpenAPI (duas operações e esquemas) e handlers MSW com teste — cobre CA11 — prova: `./backend/mvnw -f backend/pom.xml test -Dtest='OpenApiContractTest,ContractRoutesTest'` e `npx vitest run src/mocks` em `frontend/`.
- [x] T4: `TermsPage`, `useTerms`, rota e item de menu, quatro estados, textos pt-BR, 360 px e axe nos dois temas — cobre CA9, CA10 — prova: `npx vitest run src/features/terms src/features/permissions` em `frontend/`.
- [x] T5: comentário de `StudentSeal.java` apontando a #85 — cobre CA12 — prova: `grep -n "#85" backend/src/main/java/br/com/ctcore/student/StudentSeal.java`.
- [x] T6: regressão completa — não cobre critério: confere cobertura e pacotes exigidos — prova: `./backend/mvnw -f backend/pom.xml verify` e, em `frontend/`, `npm run lint && npm run typecheck && npm run build && npm run test:cov && npm run e2e`.

## 6. Decisões

- D1 (muda o comportamento): enquanto a organização não publica, vale um texto padrão ("modelo da plataforma", `01`) fixo no servidor e exibido como versão 0, sem linha no banco. A primeira publicação é a versão 1. Motivo: o modelo editável é do painel da plataforma (RF-PNL-07, depois), e não duplicar texto em migração e em código. Alternativa: gravar a versão 1 do modelo em cada organização na criação e na migração; descartada por duplicar o texto e por exigir mexer em `Organizations.initialize`.
- D2: um endpoint de leitura por tipo devolve a atual e o histórico completo (com o texto de cada versão), sem paginação: o volume é de poucas versões por organização. Se crescer, pagina-se o histórico.
- D3 (muda o comportamento): `reaccept` é obrigatório em toda publicação e sem valor padrão na tela, para o Coordenador escolher de propósito (RN-MAT-14). Vale por versão e fica gravado nela; a #85 usa o da versão atual para decidir quem precisa aceitar de novo. O uso de imagem tem a mesma escolha. Alternativa: padrão "só novos alunos"; descartada porque o aceite de novo é a escolha de maior efeito.
- D4: limites de 20 a 20000 caracteres e recusa de texto idêntico ao atual são escolha deste item, para evitar versão vazia ou publicação sem mudança que forçaria reaceite à toa. Se estiver errado, muda só a validação.
- D5: versões são imutáveis (sem editar nem apagar), porque o aceite do aluno cita a versão (`03`, RN-MAT-12). Correção é nova versão.
- D6 (muda o comportamento): o Administrador lê o termo e o histórico e não publica, seguindo a linha `ORGANIZATION_SETTINGS` ("só o Coordenador altera" as configurações da organização, `01`). Alternativa: Administrador também publica; descartada por contrariar a tabela de permissões.
- D7: o texto é puro, sem formatação rica nem HTML (editor de área de texto), exibido como texto com quebras de linha. Alternativa: editor rico; descartada, a especificação só pede "editor" e HTML traria risco de injeção. Upload de PDF do termo (`00`) fica fora.
- D8: notas de versão só se o arquivo existir (não existe hoje); a linha vai na descrição do PR: "Termos de matrícula e de uso de imagem editáveis, com versões e escolha de reaceite".
- Suposição: o canvas `ConfigTermos.dc.html` mostra editor, versões e a escolha de reaceite; se o layout divergir, ajusta-se no PR.
- Suposição: o último arquivo de migração é `V37`; se outro item entrar antes, o número sobe.

## Construção

- T1 — arquivos: `backend/src/main/resources/db/migration/V38__terms_version.sql`, `infra/seed/17-termos.sql`, `backend/src/test/java/br/com/ctcore/db/SeedDataIT.java` (limpeza de `terms_version` e teste `termsSeed`) — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test='SeedDataIT,TermsIT'` → SeedDataIT verde; o papel de execução sem UPDATE/DELETE provado em `TermsIT.isolationAndImmutability`.
- T2 — arquivos: `backend/src/main/java/br/com/ctcore/terms/Terms.java`, `TermsController.java`, `ErrorCode.java` (`TERMS_VERSION_CONFLICT`), `messages.properties`, `backend/src/test/java/br/com/ctcore/terms/TermsIT.java` — prova: `verify -Dit.test=TermsIT` → 7 testes, 0 falhas.
- T3 — arquivos: `docs/openapi.yaml`, `frontend/src/mocks/handlers.ts`, `frontend/src/mocks/terms.msw.test.ts` — prova: `test -Dtest='OpenApiContractTest,ContractRoutesTest'` → 7 testes verdes; `npx vitest run src/mocks/terms` → 3 verdes.
- T4 — arquivos: `frontend/src/features/terms/{TermsPage.tsx,useTerms.ts,TermsPage.test.tsx}`, `App.tsx`, `permissions/menuCatalog.ts`, `permissions/buildMenu.test.ts`, `i18n/pt-BR.json` — prova: `npx vitest run src/features/terms src/features/permissions` → verdes (10 de termos, incluindo axe nos dois temas a 360 px).
- T5 — arquivos: `backend/src/main/java/br/com/ctcore/student/StudentSeal.java` — prova: `grep -n "#85"` → comentário aponta a #85.
- T6 — regressão abaixo.

Regressão: `./backend/mvnw -f backend/pom.xml verify` → 524 testes, 0 falhas, "All coverage checks have been met"; em `frontend/`: `npm run lint`, `typecheck`, `build` sem erro, `npm run test:cov` → 108 arquivos / 961 testes verdes, limiares atendidos; `npm run e2e` → 33 passed (a primeira execução teve testes repetidos e terminou com "19 passed"; ao rodar de novo, 33 passed, sem mudança de código).
Desvios: CA8 — duas threads soltas ao mesmo tempo não garantem a corrida (o teste deu 201/201 porque rodaram em sequência); o teste `TermsIT.concurrent` foi trocado por uma corrida determinística: uma conexão rival grava a versão 1 sem confirmar, a publicação espera o índice único e, ao confirmar, responde 409 `TERMS_VERSION_CONFLICT`, sem segunda versão nem auditoria. Em `TermsPage` o histórico vazio usa o estado vazio dentro do cartão do histórico. As abas são dois botões `aria-pressed`, não `role=tab`.
Não é do item: nada.

## Verificação

### Rodada 1 — OK — 2026-10-21, estado 26e680dddfd34af7161249c43f02e1bb2c4b5d53

| Critério | Status | Evidência |
|---|---|---|
| CA1 | PASSOU | `TermsIT` 7/7 verdes (modelo versão 0, `versions` vazia) |
| CA2 | PASSOU | `TermsIT` (numeração, ordem, tipos independentes); `Terms.publish` lido |
| CA3 | PASSOU | `TermsIT`; validação em `Terms.publish` (20..20000 após `strip`, igual ao atual, `reaccept` nulo, kind 404) |
| CA4 | PASSOU | `TermsIT.isolationAndImmutability`; V38 sem UPDATE/DELETE; controller só tem GET e POST |
| CA5 | PASSOU | `TermsIT` (perfis); `requireFull` em `publish` |
| CA6 | PASSOU | `TermsIT.isolationAndImmutability` com RLS (V38 `organization_scope`) |
| CA7 | PASSOU | `TermsIT`; auditoria sem o texto, só depois do insert |
| CA8 | PASSOU | `TermsIT` com corrida determinística (409 `TERMS_VERSION_CONFLICT`); desvio registrado na Construção, aceitável |
| CA9 | PASSOU | `TermsPage.test.tsx` (quatro estados, axe nos dois temas a 360 px, publicar, 400/409, Administrador somente leitura) |
| CA10 | PASSOU | `TermsPage.test.tsx` CA10 + `buildMenu.test.ts` |
| CA11 | PASSOU | `OpenApiContractTest`/`ContractRoutesTest` dentro do verify sem falha; `terms.msw.test.ts` verde; `SeedDataIT` 17/17 |
| CA12 | PASSOU | diff de `StudentSeal.java` aponta a #85 |

Comandos:
- `./backend/mvnw -f backend/pom.xml verify` → 534 testes, 2 erros, ambos em `S3FileStorageIT` (`ConnectException`: armazenamento S3 do compose fora do ar nesta máquina; pacote `storage`, que o item não tocou). `TermsIT` 7/7 e `SeedDataIT` 17/17 verdes. Como o verify terminou em falha, a checagem de cobertura do JaCoCo não rodou; o item não toca `billing`, `payment`, `subscription`, `deactivation` nem `isolation`.
- `frontend`: `npm run lint` e `npm run typecheck` sem erro; `npx vitest run src/features/terms src/features/permissions src/mocks` → 32 arquivos, 199 testes verdes.
- Não rodados nesta rodada: `test:cov`, `build`, `e2e` do front (construtor registrou verdes).

Achados:
- sugestão, código, `Terms.view` — a leitura chama `access(who)` antes de validar `kind`, então perfil sem permissão com `kind` inválido recebe 403 em vez de 404; coerente com não vazar, sem impacto.

## Entrega

- Configurações › Termos: versões imutáveis (tabela `terms_version`, V38, RLS por organização, sem UPDATE/DELETE para o papel de execução), publicação só pelo perfil completo, auditoria sem o texto, conflito de numeração em 409 `TERMS_VERSION_CONFLICT`; tela com os quatro estados, axe nos dois temas a 360 px, menu e rota; contrato no OpenAPI, handlers MSW e seed `17-termos.sql`.
- Verificação: rodada 1 OK. `verify` do back-end só teve 2 erros em `S3FileStorageIT` (armazenamento S3 do compose fora do ar na máquina de verificação; pacote `storage` não tocado). Front: lint, typecheck, testes e, na construção, build, `test:cov` e `e2e` verdes.
- Desvio aceito: CA8 provado por corrida determinística (conexão rival), não por threads soltas; abas são botões `aria-pressed`.
- Selo "Termo pendente" fica para a #85 (comentário em `StudentSeal`).

Refs #70
