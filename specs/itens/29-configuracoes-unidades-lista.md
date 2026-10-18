# 29 — RF-ORG-02 · Configurações › Unidades (lista)

Origem: issue #29 (Sprint 03); `docs/especificacao/01-organizacao-e-isolamento.md` (RF-ORG-02, RN-ORG-15). Branch: `item/29-configuracoes-unidades-lista`.

## 1. Objetivo

Dar ao Coordenador a tela e a API da lista de unidades da organização (nome, cidade, alunos ativos, Administradores e situação) e fechar a regra do nome curto: único na plataforma, gerado do nome, ajustável uma única vez pelo Coordenador, com o nome antigo redirecionando para o novo.

Fora do escopo: criar unidade (#30), editar dados da unidade (#31), seletor de unidade (#32), desativar (#34), rota pública que consome o redirecionamento (diretório e páginas públicas, sprints futuras).

## 2. Requisitos

- R1: `GET /api/v1/units` lista as unidades da organização do vínculo ativo, cada uma com nome, cidade, alunos ativos, Administradores (nome) e situação (Ativa ou Desativada) (RF-ORG-02).
- R2: só o Coordenador acessa a lista (tabela de permissões, linha `UNITS`); Administrador, Professor e demais recebem 403 e não veem o item de menu.
- R3: a lista nunca traz unidades de outra organização (RN-ORG-01, RN-ORG-02).
- R4: o nome curto é gerado do nome (minúsculas, sem acento, `a-z0-9` e hífen) e é único na plataforma; em colisão recebe sufixo numérico (RN-ORG-15, D3).
- R5: o Coordenador ajusta o nome curto uma única vez por unidade; a segunda tentativa é recusada (RN-ORG-15).
- R6: nome curto já usado por outra unidade, hoje ou como nome antigo, é recusado (RN-ORG-15, D4).
- R7: ao ajustar, o nome antigo passa a resolver para o novo (`UnitShortNames.resolve`) (RN-ORG-15).
- R8: o ajuste grava na auditoria `unit.short_name.update`, com antes e depois, sem dado sensível.
- R9: a tela tem os quatro estados (carregando, vazio, erro, sem permissão), funciona a partir de 360 px, passa no axe nos dois temas, e os textos ficam em pt-BR.
- R10: `docs/openapi.yaml` e os handlers MSW trazem as duas rotas novas.

## 3. Critérios de aceite

- CA1 (R1): dado Coordenador com 2 unidades, quando chama `GET /api/v1/units`, então recebe as 2 com `name`, `city`, `activeStudents`, `administrators` e `active`.
- CA2 (R1): dado unidade com dois Administradores ativos e um vínculo encerrado, então só os dois ativos da unidade aparecem; dado unidade sem Administrador, então a lista vem vazia.
- CA3 (R1, D2): dado que ainda não existe cadastro de alunos, então `activeStudents` é 0 em toda unidade.
- CA4 (R2): dado Administrador, Professor ou Aluno, quando chama `GET` ou `PATCH`, então 403.
- CA5 (R2): dado sessão sem login, então 401.
- CA6 (R3): dado Coordenador da organização A, quando lista ou ajusta nome curto de unidade da B por identificador adivinhado, então a lista não traz a B e o ajuste dá 404.
- CA7 (R4): dado nome "Academia Leão — Tijuca", então gera `academia-leao-tijuca`; dado que já existe, então gera `academia-leao-tijuca-2`.
- CA8 (R5): dado unidade nunca ajustada, quando o Coordenador envia `PATCH /api/v1/units/{id}/short-name` válido, então 200 e a unidade passa a `shortNameAdjusted = true`.
- CA9 (R5): dado unidade já ajustada, quando tenta de novo, então 409 e nada muda.
- CA10 (R6): dado nome curto de outra unidade ou nome antigo de qualquer unidade, então 409; dado formato inválido (vazio, maiúscula, espaço, mais de 60 caracteres), então 400 `VALIDATION_ERROR`.
- CA11 (R7): dado unidade ajustada de `a` para `b`, quando se resolve `a`, então devolve `b`; resolve `b` devolve `b`; nome desconhecido devolve vazio.
- CA12 (R8): dado ajuste feito, então há uma linha de auditoria `unit.short_name.update` com antes e depois e sem senha, token ou dado de cartão.
- CA13 (R9): dado a tela, então mostra carregando, vazio ("nenhuma unidade"), erro com nova tentativa e sem permissão; em 360 px não há rolagem horizontal da página; axe sem violações nos dois temas.
- CA14 (R9): dado Coordenador na tela, quando ajusta o nome curto numa linha, então o botão some depois do ajuste e a lista mostra o novo valor.
- CA15 (R10): `OpenApiContractTest` e `ContractRoutesTest` passam com as duas rotas.
- CA16 (R2): dado Administrador no menu, então "Unidades" não aparece e a rota `/configuracoes/unidades` mostra "sem permissão".

## 4. Plano técnico

- Dados: migração `V13__unit_city_short_name_history.sql`: `unit.city text`, `unit.short_name_adjusted boolean not null default false` e tabela `unit_short_name_history (short_name text primary key, organization_id uuid not null, unit_id uuid not null, foreign key (organization_id, unit_id) references unit(organization_id, id), replaced_at timestamptz not null)` com RLS por `organization_id` no mesmo molde das tabelas de V3/V4. Unicidade entre nome atual e antigo conferida no serviço dentro da transação e protegida por `unique (short_name)` de `unit` e pela chave da história. Reversão descrita no próprio arquivo. Seed: `infra/seed/04-unidades.sql` (cidades fictícias, `on conflict do nothing`).
- API (`docs/openapi.yaml`): `GET /api/v1/units` (200 lista de `UnitSummary { id, name, shortName, shortNameAdjusted, city, activeStudents, administrators[{name}], active }`; 401; 403); `PATCH /api/v1/units/{id}/short-name` (corpo `{ shortName }`; 200 `UnitSummary`; 400 `VALIDATION_ERROR`; 401; 403; 404; 409). Sem paginação (máximo de 10 unidades, RN-ORG-05).
- Back-end (pacote `br.com.ctcore.organization`): `UnitList` (`@Service`; `list`, `adjustShortName`; leitura e escrita por `OrganizationTransactions`, organização do principal; permissão `Permissions.grant(who, Permission.UNITS, null) == FULL`; auditoria na mesma transação via `AuditService`); `UnitController` (`/api/v1/units`); `ShortName` (a criar; `generate(name)`, `validate(value)`, normalização com `java.text.Normalizer`); `UnitShortNames` (a criar; `resolve(String)` consulta atual e história). `Organizations.initialize` passa a usar `ShortName` apenas se recebido nulo; hoje recebe o nome pronto e não muda.
- Alunos ativos: sem tabela de alunos até a Sprint de cadastro; a consulta fica num único ponto (`activeStudents` constante 0 com comentário `ponytail:` apontando a troca por `count` quando a tabela existir). Administradores: `account_link` (role `ADMINISTRATOR`, status `ACTIVE`) via `account_link_unit` e `account.name`.
- Segurança: permissão no servidor; organização sempre do principal, nunca do corpo ou da URL além do id da unidade, que é conferido contra a organização (404 se não for dela); RLS ativa; validação de formato na borda; auditoria sem dado sensível.
- Interface (`frontend/src/features/units/`, a criar): `UnitsPage.tsx` (rota `/configuracoes/unidades` em `RequireArea` com `UNITS`, dentro de `ManagementLayout`; registrar em `App.tsx`), `useUnits.ts` (consulta e mutação), `ShortNameDialog.tsx` (aviso de que só se ajusta uma vez e que o endereço antigo continua valendo). Tabela em telas largas e cartões em 360 px. Reuso: `QueryPage`/`states`, `Modal`, `ConfirmDialog`, `fields.tsx`, `Card`, `button.tsx`. `menuCatalog.ts`: `units` com `available: true`. `pt-BR.json`: chaves `units.*`. `mocks/handlers.ts`: as duas rotas por papel. Canvas `ConfigUnidades.dc.html` não acessível ao agente (Suposição).
- Arquivos a alterar (vistos): `docs/openapi.yaml`, `frontend/src/App.tsx`, `frontend/src/features/permissions/menuCatalog.ts`, `frontend/src/features/permissions/buildMenu.test.ts`, `frontend/src/mocks/handlers.ts`, arquivo de tradução pt-BR. A criar: migração V13, seed 04, `UnitList`, `UnitController`, `ShortName`, `UnitShortNames`, testes `ShortNameTest`, `UnitListIT`, `UnitRouteIT` (estende `RouteIsolationModelIT`, molde do `OrganizationRouteIT`), `UnitsPage.test.tsx`, `ShortNameDialog.test.tsx`, `e2e/unidades.spec.ts` `@critical`.
- Riscos: corrida entre dois ajustes iguais (cobrir com a chave primária da história e `409`); RLS esquecida na tabela nova (coberta pelo teste de isolamento); cobertura de `isolation` continua 100%.

## 5. Tarefas

- [x] T1: migração `V13` (`city`, `short_name_adjusted`, `unit_short_name_history` com RLS, reversão no arquivo) e seed `04-unidades.sql` — não cobre critério: base das regras, conferida em T3 e no `SeedDataIT` — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test='UnitListIT,SeedDataIT'`.
- [x] T2: `ShortName` (gerar e validar) e `UnitShortNames.resolve` — cobre CA7, CA10 (formato), CA11 — prova: `./backend/mvnw -f backend/pom.xml test -Dtest=ShortNameTest` e `UnitListIT`.
- [x] T3: `UnitList` com `list` (Administradores, alunos ativos 0) e `adjustShortName` (uma vez, unicidade, história, auditoria) — cobre CA1, CA2, CA3, CA8, CA9, CA10, CA12 — prova: `verify -Dit.test=UnitListIT`.
- [x] T4: `UnitController` com as duas rotas e erros 400/401/403/404/409 — cobre CA4, CA5, CA6 — prova: `verify -Dit.test=UnitRouteIT`.
- [x] T5: teste de isolamento entre organizações e perfis nas duas rotas — cobre CA4, CA6 — prova: `verify -Dit.test=UnitRouteIT`.
- [x] T6: OpenAPI e teste de contrato — cobre CA15 — prova: `./backend/mvnw -f backend/pom.xml test -Dtest='OpenApiContractTest,ContractRoutesTest'`.
- [x] T7: front base: handlers MSW, `useUnits`, textos `units.*`, menu `available: true` — cobre CA16 — prova: `npx vitest run src/features/permissions src/mocks`.
- [x] T8: `UnitsPage` e `ShortNameDialog` com os quatro estados, 360 px e axe — cobre CA13, CA14, CA16 — prova: `npx vitest run src/features/units`.
- [x] T9: e2e `@critical` do Coordenador (lista e ajuste) e do Administrador (sem acesso) — cobre CA14, CA16 — prova: `npm run e2e`.
- [x] T10: regressão do item — não cobre critério: confere que nada quebrou e a cobertura — prova: `./backend/mvnw -f backend/pom.xml verify` e, em `frontend/`, `npm run lint && npm run typecheck && npm run build && npm run test:cov && npm run e2e`.

## 6. Decisões

- D1 (muda o comportamento): rotas em `/api/v1/units`, organização sempre a do vínculo ativo (mesmo princípio do item 28). Só o Coordenador acessa, conforme a linha `UNITS` da tabela de permissões (Administrador sem acesso), embora o card original diga "Administradores" apenas como coluna.
- D2: "alunos ativos" retorna 0 até existir cadastro de alunos; o contrato e a tela já ficam prontos e só a consulta muda. Alternativa descartada: ocultar a coluna, porque o card a exige.
- D3: nome curto no formato `a-z0-9` e hífen, até 60 caracteres; geração com sufixo `-2`, `-3` em colisão. A especificação não fixa o formato; mudar só altera `ShortName` e o teste.
- D4 (muda o comportamento): nome antigo fica reservado para sempre à unidade que o usou (não pode ser reaproveitado por outra), para o redirecionamento nunca apontar para a unidade errada.
- D5: o redirecionamento fica como serviço (`UnitShortNames.resolve`); a rota pública que o usa entra com o diretório e as páginas públicas. Alternativa descartada: criar rota pública agora, sem consumidor e sem regra de exposição definida.
- D6: `city` é coluna nova e opcional em `unit`, preenchida pelo assistente (#30) e pela edição (#31); o endereço continua texto livre. Alternativa descartada: extrair a cidade de `address`, que é frágil.
- D7: a edição do nome curto fica na própria lista, com diálogo de aviso, pois não há outra tela de unidade antes do #31.
- D8 (ajuste, verificação rodada 1): formato inválido do nome curto responde 400 `VALIDATION_ERROR`, convenção da API (`ApiValidationException`; não há 422 no back-end), em vez do 422 escrito antes em CA10, no plano e em T4. Código e OpenAPI já estavam assim.
- Suposição: o canvas `ConfigUnidades.dc.html` não está acessível; a tela segue o texto da issue e os padrões existentes. Se divergir, só tela e textos mudam.

## Construção

- T1 — arquivos: `backend/src/main/resources/db/migration/V13__unit_city_short_name_history.sql`, `infra/seed/04-unidades.sql` — prova: `./backend/mvnw -f backend/pom.xml verify` (inclui `UnitListIT` e `SeedDataIT`) → verde
- T2 — arquivos: `organization/ShortName.java`, `organization/UnitShortNames.java`, `ShortNameTest` — prova: `verify -Dit.test='UnitListIT,UnitRouteIT' -Dtest=ShortNameTest` → ShortNameTest 2/2, UnitListIT 4/4
- T3 — arquivos: `organization/UnitList.java`, `api/error/ApiStatusException.java` (+ handler em `ApiExceptionHandler`), `UnitListIT`, `UnitBase` — prova: mesmo comando → 4/4
- T4 — arquivos: `organization/UnitController.java` — prova: `UnitRouteIT` → 2/2
- T5 — arquivos: `UnitRouteIT` — prova: mesmo comando → 2/2
- T6 — arquivos: `docs/openapi.yaml` — prova: `test -Dtest='OpenApiContractTest,ContractRoutesTest'` → 7/7
- T7 — arquivos: `mocks/handlers.ts`, `features/units/useUnits.ts`, `i18n/pt-BR.json`, `menuCatalog.ts`, `buildMenu.test.ts`, `mocks/units.msw.test.ts`, `App.tsx` — prova: `npx vitest run src/features/permissions src/mocks src/features/units` → 38/38
- T8 — arquivos: `features/units/UnitsPage.tsx`, `ShortNameDialog.tsx`, `UnitsPage.test.tsx` — prova: mesmo comando → verde (axe nos dois temas a 360 px, lista e janela)
- T9 — arquivos: `frontend/e2e/unidades.spec.ts` — prova: `npm run e2e` → 11 passaram
- T10 — ver regressão.

Regressão: `./backend/mvnw -f backend/pom.xml verify` → BUILD SUCCESS (109 testes rápidos, 151 no total, JaCoCo ok); em `frontend/`: `npm run lint`, `typecheck`, `build`, `test:cov` (linhas 97,93%), `e2e` (11) → verdes.
Desvios:
- Formato inválido responde 400 `VALIDATION_ERROR` (convenção da API e do OpenAPI, `ApiValidationException`), não 422 como diz CA10; os testes e o contrato usam 400.
- `unit_short_name_history.replaced_at` é anulável (nulo = nome atual) e a história também reserva o nome novo no ajuste: a chave primária recusa nome antigo de outra organização mesmo com a RLS escondendo a linha.
- `UnitShortNames.resolve` roda no escopo da organização do chamador (RLS); a rota pública futura (D5) terá de definir esse escopo.
- Unidade criada pelo #30 deve registrar o nome curto inicial na história (hoje o nome original entra na história ao ser ajustado).
- Lista em cartões (`dl`) em todas as larguras, sem tabela separada, para não duplicar conteúdo; `ShortNameDialog` testado dentro de `UnitsPage.test.tsx` (sem arquivo próprio); `UnitRouteIT` estende `OrganizationBase` (como o #28), não `RouteIsolationModelIT`.
Não é do item: nada.

## Verificação

### Rodada 1 — CORRIGIR_ESPEC — 2026-10-17, estado 04ba919c3087f3305fc6fdb89c0b5d3248bfa2d5

| Critério | Status | Evidência |
|---|---|---|
| CA1, CA2, CA3 | PASSOU | `UnitListIT.listUnits` (campos, 2 administradores ativos, lista vazia, `activeStudents` 0) |
| CA4, CA5 | PASSOU | `UnitRouteIT.deniedProfiles` (401 sem login; 403 nos demais perfis em GET e PATCH) |
| CA6 | PASSOU | `UnitRouteIT.crossOrganization` (lista sem a B; PATCH 404) |
| CA7 | PASSOU | `ShortNameTest` |
| CA8, CA9, CA12 | PASSOU | `UnitListIT.adjustOnce` (200, flag, 409 na segunda, auditoria antes/depois sem dado sensível) |
| CA10 | NÃO PASSOU (parte do formato) | conflitos dão 409 (`conflictsAndFormat`), mas formato inválido responde 400 `VALIDATION_ERROR`, não 422 como o critério diz |
| CA11 | PASSOU | `UnitListIT.resolve` |
| CA13, CA14, CA16 | PASSOU | `UnitsPage.test.tsx`, `e2e/unidades.spec.ts` (`npm run e2e`: 11 passaram) |
| CA15 | PASSOU | `verify` inclui `OpenApiContractTest` e `ContractRoutesTest` |

Comandos:
- `./backend/mvnw -f backend/pom.xml verify` → BUILD SUCCESS, 151 testes, JaCoCo ok.
- `frontend`: `npm run lint && npm run typecheck && npm run test:cov && npm run e2e` → verdes; linhas 97,93%; e2e 11/11.

Achados:
- obrigatório, especificação, seções 2/3/4 (R6/CA10 e API `422`) — esperado: texto coerente com o código; observado: a spec manda 422 para formato inválido, mas a API usa 400 `VALIDATION_ERROR` (`ApiValidationException`; não há 422 em todo o back-end) e o OpenAPI do item declara 400; o desvio está só em "Construção" e a spec segue contradizendo o código; evidência: `UnitListIT.conflictsAndFormat` espera 400, CA10 diz 422; corrigir: ajustar CA10 e o plano técnico para 400 (`--ajustar`), registrando em Decisões.
- sugestão, código, `UnitList.adjustShortName` — a corrida entre dois ajustes iguais depende da violação de PK virar 409; não há teste concorrente, só o caso sequencial.

### Correção da rodada 1
- Achado obrigatório (CA10: 422 x 400) é de especificação e já foi atendido pela fase 2 (D8); o código, os testes e o OpenAPI já usavam 400 `VALIDATION_ERROR`, então não houve mudança de código — prova: `verify -Dit.test='UnitListIT,UnitRouteIT' -Dtest=ShortNameTest` → UnitListIT 4/4, UnitRouteIT 2/2, ShortNameTest 2/2.
- Sugestão (teste concorrente do ajuste do nome curto): opcional, não feita.
Regressão: sem mudança de código nesta rodada; a regressão completa da rodada anterior (151 testes back-end, front verde, e2e 11) segue válida.

### Rodada 2 — OK — 2026-10-17, estado 07310f7c3e5c2ec17f67227b9ce420c635ff16ee

Desde a rodada 1 (`git diff 04ba919… 07310f7…`) mudou só este arquivo (spec); nenhum código.

| Critério | Status | Evidência |
|---|---|---|
| CA10 | PASSOU | spec agora diz 400 `VALIDATION_ERROR` (linha do CA10 e D8); `UnitListIT.conflictsAndFormat` espera 400 e 409 |
| CA1–CA9, CA11–CA16 | PASSOU | mantido da rodada 1; `verify` e front reexecutados sem falha |

Comandos:
- `./backend/mvnw -f backend/pom.xml verify` → exit 0 (JaCoCo ok).
- `frontend`: `npm run lint && npm run typecheck && npm run test:cov && npm run e2e` → exit 0; linhas 97,93%; e2e 11/11.

Achados:
- Achado obrigatório da rodada 1 (422 x 400): resolvido na especificação (CA10, D8).
- sugestão, código, `UnitList.adjustShortName` — teste concorrente do ajuste continua ausente (mantido da rodada 1).

## Entrega

- Lista de unidades em Configurações › Unidades (RF-ORG-02): rota `/api/v1` nova no back-end (`UnitController`, `UnitList`, `ShortName`), migração V13 (cidade e histórico do nome curto), contrato em `docs/openapi.yaml`, tela com os quatro estados, handlers MSW, textos pt-BR e seed `04-unidades.sql`.
- Verificação (rodada 2, OK): `verify` do back-end verde (JaCoCo ok); front com lint, tipos, cobertura 97,93% e e2e 11/11.
- Decisão: formato inválido responde 400 `VALIDATION_ERROR` (D8), não 422.
- Limitação: falta teste concorrente do ajuste do nome curto (sugestão, não obrigatória).
