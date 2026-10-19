# 40 — RF-MOD-03 · Modalidades oferecidas pela unidade

Origem: issue #40 (Sprint 04); `docs/especificacao/04-modalidades-e-graduacao.md` (RF-MOD-03, RN-MOD-02, RN-MOD-03, "Modalidade da unidade") e a linha `UNIT_SETTINGS` da tabela de permissões (`02-contas-e-acesso.md`). Branch: `item/40-modalidades-da-unidade`. Parte do que o #38 criou (`modality`, `organization_modality`, `Modalities`) e do #31 (`UnitSettings`, `UnitSettingsPage`).

## 1. Objetivo

Em Configurações › Unidade, uma seção "Modalidades oferecidas" em que o Coordenador (qualquer unidade) e o Administrador (as suas) marcam, entre as modalidades que a organização ativou, quais a unidade oferece. Quem usa a marcação são turmas, planos, aula experimental e diretório (Sprints 05, 06, 18 e 20).

Fora do escopo: ativar modalidades na organização (#38); modalidades no assistente "Nova unidade" (#30); usar a marcação em turmas e planos; recusar a retirada quando houver turma (Sprint 05); notas de versão (Sprint 15, #142).

## 2. Requisitos

- R1 (RF-MOD-03, RN-MOD-02, D1): `GET /api/v1/units/{unitId}/modalities` devolve as modalidades ativas da organização com `id`, `name` e `offered` (a unidade oferece), em ordem alfabética, mais `canManage`.
- R2 (RN-MOD-02, D2): `PUT /api/v1/units/{unitId}/modalities/{modalityId}` (corpo `{"offered": true|false}`) marca ou desmarca a modalidade na unidade; devolve o item atualizado. Repetir o valor é idempotente (200, sem nova auditoria).
- R3 (RN-MOD-03, D3): só modalidade ativa na organização pode ser marcada; modalidade inativa ou fora do catálogo responde 404, e a marcação feita antes de a organização desativá-la fica guardada, mas não aparece na lista.
- R4 (permissão): `UNIT_SETTINGS` conferido no servidor: leitura para Coordenador e para Administrador da unidade; escrita só com acesso `FULL` à unidade (Coordenador em qualquer unidade da organização, Administrador nas suas); Professor, portal e Administrador de outra unidade recebem 403; sem sessão, 401.
- R5 (isolamento): a unidade e a marcação são sempre da organização da sessão; unidade de outra organização ou inexistente responde 404; filtro no código e row-level security na tabela nova.
- R6 (auditoria): marcar e desmarcar gravam `unit.modality.offered` e `unit.modality.withdrawn` (ator, perfil, unidade, modalidade, antes e depois), sem dados pessoais.
- R7 (tela): a seção "Modalidades oferecidas" na tela `/configuracoes/unidade` lista as modalidades ativas com um interruptor por linha (efeito imediato, sem depender do "Salvar" da tela), segue a unidade escolhida na tela, tem os quatro estados, orienta a ativar modalidades quando a organização não tem nenhuma (com link para Configurações › Modalidades quando o perfil pode abrir essa tela), funciona a partir de 360 px, passa no axe nos dois temas, tem todos os textos em `pt-BR.json` e deixa o interruptor só leitura sem `canManage`.
- R8 (contrato): `docs/openapi.yaml` e os handlers MSW refletem as duas rotas; o seed de dados fictícios marca modalidades nas unidades de teste.

## 3. Critérios de aceite

- CA1 (R1): dado Coordenador de organização com Judô e Boxe ativas e Karatê inativa, quando `GET /units/{id}/modalities`, então 2 itens (Boxe, Judô) em ordem alfabética, `offered = false` e `canManage = true`; após marcar Judô, esse item vem `offered = true`.
- CA2 (R2, R6): dado Coordenador, quando marca Judô na unidade, então 200 com `offered = true` e auditoria `unit.modality.offered`; repetir devolve 200 sem nova auditoria; desmarcar devolve `offered = false` com `unit.modality.withdrawn`.
- CA3 (R3): dado modalidade que a organização não ativou, ou `modalityId` inexistente, quando marca, então 404 e nada muda; corpo sem `offered`, 400; dado Judô marcada e depois desativada na organização, então some da lista e, reativada, volta como `offered = true`.
- CA4 (R4): dado Administrador da unidade, quando lê e marca na sua unidade, então 200; em unidade que não é dele, 403 (leitura e escrita); Professor e portal, 403; sem sessão, 401; nada muda nas recusas.
- CA5 (R5): dado usuário da organização A com o id de unidade da B, quando lê ou marca, então 404 e a B segue idêntica; duas organizações marcando a mesma modalidade não veem a marcação uma da outra (API e consulta direta com o papel `ctcore_app` e o contexto de RLS: sem contexto 0 linhas, com contexto só as da própria, insert com outra organização negado).
- CA6 (R6): dado cada escrita, então existe auditoria com ator, ação e alvo, sem e-mail, senha ou token.
- CA7 (R7): dado a seção em 360 px, então sem rolagem horizontal e axe sem violações nos dois temas; estados carregando, vazio (organização sem modalidades ativas, com orientação), erro com "Tentar de novo" e sem permissão; Coordenador e Administrador da unidade marcam e a lista reflete o resultado sem recarregar; erro do servidor volta o interruptor ao valor anterior e mostra a mensagem.
- CA8 (R7): dado Administrador de duas unidades, quando troca a unidade na tela, então a seção mostra as marcações da unidade escolhida.
- CA9 (R8): `OpenApiContractTest` e `ContractRoutesTest` passam com as duas rotas; testes MSW cobrem as rotas no formato do servidor; `SeedDataIT` aplica o novo seed.
- CA10 (todos): ponta a ponta Playwright `@critical` "Coordenador marca uma modalidade na unidade": abre Configurações › Unidade, marca uma modalidade e a vê marcada após recarregar.

## 4. Plano técnico

- **Dados.** Migração `V21__unit_modality.sql` (depois de `V20__graduation.sql`): `unit_modality` (`organization_id` referência a `organization`, `unit_id` referência a `unit`, `modality_id` referência a `modality`, `offered boolean`, `updated_at`; chave primária `(unit_id, modality_id)`; RLS `organization_scope` como em `V19`; revert comentado no topo). Seed `infra/seed/07-modalidades-unidade.sql` (`on conflict do nothing`) marcando Jiu-Jitsu e Judô nas unidades fictícias; `SeedDataIT` limpa a tabela nova e testa o seed.
- **API** (`/api/v1/units/{unitId}/modalities`, cookie e CSRF como as demais): `GET` (200 `{canManage, items:[{id,name,offered}]}`; 401, 403, 404) e `PUT /{modalityId}` (corpo `{offered}`; 200 item; 400, 401, 403, 404). Sem `If-Match`: cada marcação é um fato independente e idempotente (D4). Rotas em `docs/openapi.yaml` no mesmo PR.
- **Back-end.** No pacote existente `br.com.ctcore.modality`, a criar `UnitModalities.java` (lista e marcação; localiza a unidade pela organização da sessão e confere `Permissions.grant(who, Permission.UNIT_SETTINGS, unitId)` como `organization/UnitSettings.read/update`, reaproveitando a localização da unidade que ele já faz em vez de repetir a consulta; escreve em `OrganizationTransactions`; audita com `AuditService`/`AuditEntry` como `Modalities.setActive`) e `UnitModalityController.java`. Consulta de apoio: modalidades com `organization_modality.active` para a organização, `left join unit_modality`.
- **Interface.** A criar `frontend/src/features/units/UnitModalitiesSection.tsx` e `useUnitModalities.ts` (TanStack Query, como `useUnitSettings.ts`), com teste; a alterar `UnitSettingsPage.tsx` (renderiza a seção com a unidade escolhida, fora do formulário e do "Salvar"), `mocks/handlers.ts` (+ teste MSW) e `i18n/pt-BR.json`. Reuso: `Card`, `states/*`, interruptor de `components/ui`, padrão de estados e de erro de `ModalitiesPage.tsx`. Canvas `ConfigUnidade.dc.html` não acessível ao agente (D5).
- **Segurança.** Organização e perfil vêm do principal da sessão; `unitId` e `modalityId` do caminho são validados contra a organização; nada do corpo define organização.
- **Riscos.** (1) A seção fica numa tela com formulário próprio; interruptores com efeito imediato não podem acionar o "Salvar" nem o aviso de alterações não salvas. (2) A marcação ainda não é usada por ninguém; os testes provam só gravar e ler.

## 5. Tarefas

Uma parte só: servidor (T1 a T3) e tela (T4 e T5).

- [x] T1: migração `V21__unit_modality.sql` e seed `infra/seed/07-modalidades-unidade.sql` (+ `SeedDataIT`) — cobre CA5 (RLS), CA9 (seed) — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test='SeedDataIT,UnitModalityIT'`.
- [x] T2: `UnitModalities` e `UnitModalityController` com permissão, auditoria e testes de integração (lista, marcar, idempotência, 404 de modalidade inativa, perfis, isolamento entre organizações com RLS, auditoria sem dado pessoal) — cobre CA1 a CA6 — prova: `verify -Dit.test=UnitModalityIT` (a criar).
- [x] T3: OpenAPI (duas operações e esquemas) e handlers MSW — cobre CA9 — prova: `./backend/mvnw -f backend/pom.xml test -Dtest='OpenApiContractTest,ContractRoutesTest'` e `npx vitest run src/mocks` em `frontend/`.
- [x] T4: `UnitModalitiesSection` na `UnitSettingsPage`, quatro estados, interruptor com reversão em erro, textos pt-BR; 360 px e axe nos dois temas — cobre CA7, CA8 — prova: `npx vitest run src/features/units`.
- [x] T5: e2e `@critical` "Coordenador marca uma modalidade na unidade" e regressão completa (a regressão não cobre critério: confere cobertura e 100% nos pacotes exigidos) — cobre CA10 — prova: `./backend/mvnw -f backend/pom.xml verify` e, em `frontend/`, `npm run lint && npm run typecheck && npm run build && npm run test:cov && npm run e2e`.

## 6. Decisões

- D1: a rota fica sob a unidade (`/units/{unitId}/modalities`) e lista só as modalidades ativas da organização, porque a unidade só pode oferecer o que a organização ativou (RN-MOD-02, RN-MOD-03). Alternativa: listar o catálogo todo; descartada por mostrar opções que não podem ser marcadas.
- D2 (muda o comportamento): Coordenador (qualquer unidade) e Administrador (as suas) marcam as modalidades da unidade, pela linha `UNIT_SETTINGS` da tabela de permissões ("cada unidade marca quais oferece"). Alternativa: só o Coordenador; descartada porque a tabela dá ao Administrador a configuração da sua unidade.
- D3: desativar uma modalidade na organização não apaga as marcações das unidades; elas ficam guardadas e voltam se a modalidade for reativada. Marcar modalidade inativa dá 404. Alternativa: apagar as marcações ao desativar; descartada por perder a escolha da unidade.
- D4: a marcação não usa `If-Match` (RF-ORG-08 o pede para a edição de organização e unidade): cada interruptor é um fato idempotente e independente. Alternativa: `If-Match` pela versão da unidade; descartada por forçar recarregar a tela a cada clique.
- D5: o canvas `ConfigUnidade.dc.html` não está acessível; a seção segue os padrões do projeto e o dono confere o layout no PR.
- D6 (muda o comportamento): desmarcar uma modalidade na unidade não é recusado quando há turmas ou alunos dela; a especificação só exige essa recusa na organização (RN-MOD-06). Se o dono quiser a recusa na unidade, entra com a chegada de turmas (Sprint 05).
- Suposição: o assistente "Nova unidade" (#30) continua sem modalidades; se estiver errado, o item vira ajuste daquele assistente.

## Construção

- T1 — arquivos: `backend/src/main/resources/db/migration/V21__unit_modality.sql`, `infra/seed/07-modalidades-unidade.sql`, `backend/src/test/java/br/com/ctcore/db/SeedDataIT.java` — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test='SeedDataIT,UnitModalityIT'` → 12 testes, 0 falhas
- T2 — arquivos: `modality/UnitModalities.java`, `modality/UnitModalityController.java`, `organization/UnitSettings.java` (novo `access(who, unitId)`: papel, 404 da unidade pela organização, nível UNIT_SETTINGS), `UnitModalityIT.java` (3 testes: CA1 a CA6, RLS com `ctcore_app`) — prova: mesmo comando → verde
- T3 — arquivos: `docs/openapi.yaml`, `frontend/src/mocks/handlers.ts`, `frontend/src/mocks/unit-modalities.msw.test.ts` — prova: `./backend/mvnw -f backend/pom.xml test -Dtest='OpenApiContractTest,ContractRoutesTest'` → 7 testes verdes; `npx vitest run src/mocks` → 12 arquivos, 51 testes verdes (antes do teste novo da tela)
- T4 — arquivos: `frontend/src/features/units/UnitModalitiesSection.tsx`, `useUnitModalities.ts`, `UnitModalitiesSection.test.tsx`, `UnitSettingsPage.tsx`, `UnitSettingsPage.test.tsx` (handler padrão da lista), `i18n/pt-BR.json` — prova: `npx vitest run src/features/units` → 62 testes verdes (inclui axe nos dois temas a 360 px)
- T5 — arquivos: `frontend/e2e/modalidades.spec.ts` — prova: `npx playwright test e2e/modalidades.spec.ts` → 3 verdes

Regressão: `./backend/mvnw -f backend/pom.xml verify` → 126 testes rápidos e 255 de integração, 0 falhas, JaCoCo "All coverage checks have been met"; em `frontend/`: `npm run lint && npm run typecheck && npm run build` verdes; `npm run test:cov` → 462 testes verdes; `npm run e2e` → 18 verdes.
Desvios: o plano cita TanStack Query, mas `useUnitSettings.ts` e os demais hooks usam `useState/useEffect`; `useUnitModalities.ts` segue esse padrão. O interruptor é `<input type="checkbox" role="switch">` (não existe componente de interruptor em `components/ui`; mesmo padrão do `GraduationDialog`). O "recarregar" do e2e é voltar à tela pelo roteador, porque o estado simulado vive na memória da página (mesmo método dos outros e2e).
Instável: uma primeira rodada de `npm run test:cov` falhou em `TwoFactorLogin.test.tsx` enquanto o `verify` do back-end rodava em paralelo; o arquivo passou sozinho e a rodada completa seguinte passou (462/462). Atribuído a carga da máquina.
Não é do item: nenhum.

## Verificação

### Rodada 1 — OK — 2026-10-19, estado 9ba24bdb6c90457ec35d160a828ffd23dbd1d825

| Critério | Status | Evidência |
|---|---|---|
| CA1 a CA6 | PASSOU | `UnitModalityIT` e `SeedDataIT` no `verify` (255 IT, 0 falhas); `UnitModalities.java` e `V21` lidos: filtro por organização, RLS forçada, auditoria só na mudança, 404 de modalidade inativa |
| CA7, CA8 | PASSOU | `npm run test:cov` 462 testes verdes (inclui `UnitModalitiesSection.test.tsx`, axe nos dois temas a 360 px) |
| CA9 | PASSOU | `OpenApiContractTest` e `ContractRoutesTest` dentro do `verify`; testes MSW no `test:cov` |
| CA10 | PASSOU | `npm run e2e` 18 verdes |

Comandos: `./backend/mvnw -f backend/pom.xml verify` → 255 IT, BUILD SUCCESS, "All coverage checks have been met"; em `frontend/`: `npm run lint && npm run typecheck && npm run build` verdes; `npm run test:cov` 462/462; `npm run e2e` 18/18.
Achados:
- sugestão, código, `UnitModalities.setOffered` — a validação de `offered` nulo vem depois da checagem de permissão (400 só para quem pode escrever); aceitável.

## Entrega

- Configurações da unidade ganha a seção Modalidades (RF-MOD-03): lista as modalidades ativas da organização com interruptor "oferecida nesta unidade". Back-end: migração V21 (`unit_modality` com RLS forçada), `UnitModalities` e `UnitModalityController`, auditoria só quando há mudança, 404 para modalidade inativa. Contrato em `docs/openapi.yaml`, handlers MSW e semente `07-modalidades-unidade.sql`.
- Verificação (rodada 1, OK): `verify` do back-end com 255 testes de integração e JaCoCo ok; no front lint, typecheck, build, `test:cov` (462) e `e2e` (18) verdes.
- Decisão D6: desmarcar na unidade não é recusado quando há turmas ou alunos; a recusa só existe na organização. Entra com as turmas (Sprint 05) se o dono quiser.
- Limitação: o canvas `ConfigUnidade` não estava acessível; o dono confere o layout no PR.

Closes #40
