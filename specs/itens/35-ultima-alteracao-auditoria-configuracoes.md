# 35 — RF-ORG-08 · "Última alteração" e auditoria das configurações

Origem: issue #35 (Sprint 03); `docs/especificacao/01-organizacao-e-isolamento.md` (RF-ORG-08), `00-visao-geral.md` (RNF-15). Branch: `item/35-ultima-alteracao-auditoria-configuracoes`.

## 1. Objetivo

Duas coisas nas telas de configuração que já existem (Organização, #28; Unidade, #31): (a) mostrar "Última alteração em [data] por [pessoa]"; (b) proteger contra edição simultânea: quem salva por último é avisado, vê o que mudou e escolhe recarregar ou sobrescrever. A auditoria com antes e depois já é gravada por essas rotas (`organization.update`, `organization.logo.*`, `unit.update`, `unit.short_name.update`, `unit.deactivated`); este item a confirma por teste e a usa como fonte da "última alteração".

Fora do escopo: tela de consulta da auditoria; configurações que ainda não têm rota de escrita (faixas etárias, tabela de permissões, limites): quando ganharem, usam o mesmo mecanismo; controle de versão na logo, no nome curto e na desativação (ações pontuais, valem as últimas, D3).

## 2. Requisitos

- R1 (RF-ORG-08): `GET /api/v1/organization` e `GET /api/v1/units/{id}` devolvem `lastChange { at, by }` (data e nome da pessoa, ou e-mail se não houver nome) tirado do último registro de auditoria da configuração (D1); sem registro, `lastChange = null`.
- R2 (RF-ORG-08): `lastChange` considera as ações de configuração da própria entidade: `organization.update`, `organization.logo.update`, `organization.logo.remove`; `unit.update`, `unit.short_name.update`, `unit.deactivated`. Registros de outras ações (ex.: transferência de coordenação) não entram.
- R3 (RF-ORG-08): as telas Configurações › Organização e Configurações › Unidade mostram "Última alteração em [data] por [pessoa]" (ou "Ainda sem alterações") para qualquer perfil que as abra.
- R4 (RF-ORG-08, auditoria): toda gravação das duas telas continua registrando antes e depois só dos campos mudados, sem segredo (já existe); afirmado por teste para os dois fluxos (D4).
- R5 (RNF-15): organização e unidade passam a ter `version` (inteiro) devolvido nas leituras e acrescido a cada gravação que muda valor em `PATCH /organization` e `PUT /units/{id}`.
- R6 (RNF-15): essas duas rotas exigem o cabeçalho `If-Match` com a versão lida; versão diferente da atual responde 409 `EDIT_CONFLICT` e nada é gravado; cabeçalho ausente ou inválido responde 428 `PRECONDITION_REQUIRED` (D2).
- R7 (RNF-15): a checagem de versão e a gravação são atômicas: duas gravações simultâneas com a mesma versão resultam em uma gravada e uma 409.
- R8 (RNF-15, tela): ao receber 409 a tela consulta o estado atual, mostra quem alterou e quando (`lastChange`), lista os campos em que o valor atual difere do que a pessoa estava editando (seu valor e valor atual) e oferece "Recarregar" (descarta a edição e carrega o atual) e "Sobrescrever" (regrava com a versão atual, mantendo a edição).
- R9: gravação sem mudança de valor não aumenta a versão nem grava auditoria (comportamento atual mantido).
- R10: `docs/openapi.yaml` e os handlers MSW refletem `lastChange`, `version`, `If-Match`, 409 e 428.
- R11: telas com os quatro estados, a partir de 360 px, axe nos dois temas, textos em `pt-BR.json`.

## 3. Critérios de aceite

- CA1 (R1, R2): dado organização com alteração feita pelo Coordenador X, quando qualquer perfil com acesso lê `GET /organization`, então `lastChange.by` é o nome de X e `lastChange.at` é o instante da auditoria; um envio de logo posterior passa a ser a última alteração; uma transferência de coordenação posterior não muda `lastChange`.
- CA2 (R1): dado unidade sem nenhuma alteração, então `lastChange = null`; após `PUT /units/{id}`, `lastChange` traz o ator e o instante; idem após ajuste do nome curto e desativação.
- CA3 (R1, isolamento): dado alteração na organização B, quando a organização A lê a sua configuração e a de unidade, então `lastChange` nunca mostra registro, pessoa ou data da B; unidade de B pelo id continua 404.
- CA4 (R4): dado `PATCH /organization` mudando nome e `PUT /units/{id}` mudando um campo, então há um registro de auditoria em cada com antes e depois só do campo mudado, ator e organização, e sem senha, token ou URL assinada.
- CA5 (R5, R9): dado leitura com `version = n`, quando a gravação muda valor, então a resposta traz `version = n + 1`; gravação com os mesmos valores mantém `n` e não grava auditoria.
- CA6 (R6): dado `If-Match` com versão antiga, quando chama `PATCH /organization` ou `PUT /units/{id}`, então 409 `EDIT_CONFLICT`, dados e auditoria inalterados; sem `If-Match` ou com valor não numérico, 428 `PRECONDITION_REQUIRED` e nada muda; com a versão atual, grava.
- CA7 (R7): dado duas chamadas simultâneas com a mesma versão, então exatamente uma grava (200) e a outra responde 409; a versão sobe uma só vez.
- CA8 (R6): dado Administrador sem escrita, `PATCH`/`PUT` seguem 403 antes de qualquer checagem de versão; Professor e portal seguem 403 na leitura.
- CA9 (R3): dado a tela de Organização e a de Unidade abertas, então aparece "Última alteração em [data] por [pessoa]" com a data no formato do projeto, ou "Ainda sem alterações" quando `lastChange` é nulo; o Administrador (somente leitura) também vê.
- CA10 (R8): dado conflito (409) ao salvar, então abre a janela com quem alterou e quando, os campos diferentes com seu valor e o valor atual, e dois botões; "Recarregar" preenche o formulário com o atual e fecha; "Sobrescrever" regrava com a versão atual e mostra a confirmação de salvo; novo 409 durante o sobrescrever reabre o aviso; erro de rede na consulta do estado atual mostra o estado de erro com "Tentar de novo".
- CA11 (R11): dado a janela de conflito e a linha de última alteração, em 360 px sem rolagem horizontal, axe sem violações nos dois temas, foco entra na janela e volta ao botão de salvar ao fechar.
- CA12 (R10): `OpenApiContractTest` e `ContractRoutesTest` passam; testes MSW cobrem `lastChange`, 409 e 428 no mesmo formato do servidor.

## 4. Plano técnico

- **Dados.** `V17__config_version.sql` (a criar): `alter table organization add column version integer not null default 0; alter table unit add column version integer not null default 0;` (reversão: `drop column`). Sem tabela nova: a "última alteração" vem de `audit_log` (índice `audit_log_org_entity` já cobre organização + entidade). RLS das tabelas segue a existente. Seed não muda. Conferir na construção se `account` é legível pela consulta sob RLS da organização (é lida por outros serviços; usar o mesmo padrão).
- **API.** Sem rota nova. `OrganizationView` e `UnitDetail` ganham `version` e `lastChange { at, by }`. `PATCH /organization` e `PUT /units/{id}` passam a exigir `If-Match`. Erros: 409 `EDIT_CONFLICT` e 428 `PRECONDITION_REQUIRED` (códigos novos em `api/error/ErrorCode.java`, com `messages.properties`). Como o contrato muda, `docs/openapi.yaml` é atualizado no mesmo PR.
- **Back-end** (`br.com.ctcore.organization`): 
  - `LastChange.java` (a criar): consulta o último `audit_log` da entidade nas ações do R2, junto do nome em `account`; devolve `record LastChange(OffsetDateTime at, String by)` ou nulo. Chamado dentro da transação de leitura de `OrganizationProfile` e `UnitSettings`.
  - `OrganizationProfile.update` (existente): lê a linha com `for update`, compara `version` com o esperado (409 se diferente), grava e faz `version = version + 1` só quando há mudança. `UnitSettings.update` (existente): a trava `for update` da unidade já existe; comparar `version` ali e incrementar junto do `update unit`.
  - `OrganizationController` e `UnitController` (existentes): ler `If-Match` (`@RequestHeader`, 428 se ausente ou não numérico) e repassar.
  - Reuso: `OrganizationTransactions`, `AuditService`, `ApiStatusException`.
  - Testes: `LastChangeIT` (a criar: CA1 a CA3), ajustes em `OrganizationProfileIT`, `OrganizationRouteIT`, `UnitSettingsIT`/`UnitUpdateIT` (conferir nomes reais; enviam `If-Match`; CA4 a CA8), teste de concorrência com duas threads (CA7).
- **Segurança.** O servidor decide por permissão antes de checar a versão (CA8); 409 e 428 não expõem dado de outra organização; `lastChange` é lido sob a organização do vínculo (RLS); nome da pessoa vem de conta da própria organização, nunca devolve e-mail de terceiros fora do vínculo (e-mail só como substituto do nome de quem alterou na mesma organização).
- **Front-end:** 
  - a criar: `frontend/src/components/LastChange.tsx` (linha de texto, usa o formatador de datas existente de #9), `frontend/src/components/EditConflictDialog.tsx` (janela com diferenças e os dois botões, sobre `Modal`) + testes.
  - a alterar: `features/organization/useOrganization.ts` e `OrganizationSettingsPage.tsx`, `features/units/useUnitSettings.ts` e `UnitSettingsPage.tsx` (guardam `version`, enviam `If-Match`, tratam 409 com o diálogo), `lib/api.ts` (aceitar cabeçalhos extras, se ainda não aceita), `mocks/handlers.ts` (+ teste MSW), `i18n/pt-BR.json`.
  - Estados: carregando, erro, sem permissão e vazio já existem nas telas; o diálogo tem carregando (consulta do atual) e erro. 
- **Riscos.** Diferença campo a campo da unidade (objeto aninhado com taxas e listas) precisa de rótulos pt-BR por campo: usar os mesmos rótulos do formulário; testes existentes das duas rotas precisam enviar `If-Match`; o mock e o e2e `@critical` de organização (#28) precisam enviar a versão.

## 5. Tarefas

- [x] T1: migração `V17__config_version.sql`, códigos `EDIT_CONFLICT` e `PRECONDITION_REQUIRED` com mensagens — não cobre critério: base de CA5 a CA8 — prova: `./backend/mvnw -f backend/pom.xml compile`.
- [x] T2: `LastChange` e inclusão de `lastChange` e `version` nas leituras de organização e unidade — cobre CA1, CA2, CA3 — prova: `verify -Dit.test=LastChangeIT`.
- [x] T3: `If-Match` e checagem atômica de versão em `PATCH /organization` e `PUT /units/{id}`; ajuste dos testes existentes dessas rotas — cobre CA4, CA5, CA6, CA7, CA8 — prova: `verify -Dit.test='OrganizationProfileIT,OrganizationRouteIT,LastChangeIT'` mais o IT de unidade.
- [x] T4: OpenAPI e handlers MSW (`lastChange`, `version`, `If-Match`, 409, 428) — cobre CA12 — prova: `./backend/mvnw -f backend/pom.xml test -Dtest='OpenApiContractTest,ContractRoutesTest'` e `npx vitest run src/mocks`.
- [x] T5: `LastChange.tsx` nas duas telas e textos pt-BR — cobre CA9 — prova: `npx vitest run src/features/organization src/features/units`.
- [x] T6: `EditConflictDialog` e tratamento do 409 nas duas telas (Recarregar e Sobrescrever), axe e 360 px — cobre CA10, CA11 — prova: `npx vitest run src/components src/features/organization src/features/units`.
- [x] T7: ajustar o e2e `@critical` de organização para a versão e regressão — não cobre critério: confere o "Pronto quando" — prova: `./backend/mvnw -f backend/pom.xml verify` e, em `frontend/`, `npm run lint && npm run typecheck && npm run build && npm run test:cov && npm run e2e`.

## 6. Decisões

- D1: "Última alteração" vem da auditoria (último registro das ações do R2), não de colunas `updated_at/updated_by`. Motivo: a auditoria já tem data, ator e é a fonte única; evita dado duplicado que pode divergir. Alternativa descartada: colunas nas tabelas (duas fontes de verdade).
- D2 (muda o comportamento): controle de edição simultânea por versão inteira com `If-Match` obrigatório (428 sem ele). Motivo: se fosse opcional, qualquer cliente que o esquecesse desligaria a proteção em silêncio. Alternativa: `If-Match` opcional (menos ajuste nos testes, proteção frágil).
- D3 (muda o comportamento): só `PATCH /organization` e `PUT /units/{id}` têm versão; logo, nome curto e desativação são ações pontuais, não editam um formulário, e valem as últimas (todas auditadas e contam na "última alteração"). Alternativa: versão em todas (conflito falso quando alguém troca a logo enquanto outro edita o nome).
- D4: o card pede "valor antes e depois" na auditoria; já é feito em #28 e #31 (com titular mascarado e campos sensíveis ocultos). Aqui só se confirma por teste, sem reabrir o formato.
- D5: "Sobrescrever" é o cliente reenviar a mesma edição com a versão atual; o servidor não tem modo de forçar. "Ver o que mudou" é feito na tela comparando a edição com o estado atual (nova leitura), sem enviar o diff no corpo do 409.
- D6: configurações sem rota de escrita hoje (faixas etárias, permissões, limites) não entram; o mecanismo (`version`, `LastChange`) é reutilizável por elas. O canvas `ConfigAcademia.dc.html` não está acessível ao agente.
- Suposição: o nome da pessoa vem de `account.name`, com o e-mail como substituto; se o dono preferir só o nome, muda a consulta e o texto.
- Suposição: `lastChange` de uma entidade sem nenhum registro (criada antes da auditoria ou sem alteração) é nulo e a tela diz "Ainda sem alterações".

## Construção

- T1 — arquivos: `backend/src/main/resources/db/migration/V17__config_version.sql`, `api/error/ErrorCode.java` (EDIT_CONFLICT 409, PRECONDITION_REQUIRED 428), `messages.properties` — prova: `./backend/mvnw -f backend/pom.xml compile` → ok; a migração roda em todos os `*IT`.
- T2 — arquivos: `organization/LastChange.java` (consulta o último `audit_log` das ações do R2 com nome em `account`, e-mail como substituto), `OrganizationProfile.java`, `UnitSettings.java` (`version` e `lastChange` nas leituras), `LastChangeIT` — prova: `verify -Dit.test=LastChangeIT` → 7 testes, 0 falhas (CA1 a CA3).
- T3 — arquivos: `organization/IfMatch.java`, `OrganizationController.java`, `UnitController.java`, `OrganizationProfile.update` (linha `for update`, confere versão, `version = version + 1` só se mudou), `UnitSettings.update` (trava antes da leitura, confere versão, incrementa junto da auditoria); testes ajustados: `OrganizationBase.call` envia `If-Match` com a versão atual do banco quando o teste não envia (`raw` não envia), `CoordinationTransferIT`, `OrganizationProfileIT`, `UnitSettingsIT` (chaves do contrato); `LastChangeIT` cobre CA4 a CA8 (concorrência com duas threads) — prova: `verify -Dit.test='LastChangeIT,OrganizationProfileIT,OrganizationRouteIT,UnitSettings*IT,UnitSettingsRouteIT,CoordinationTransferIT,UnitDeactivationIT,UnitListIT,UnitReachableIT,UnitCreate*IT'` → sem falhas.
- T4 — arquivos: `docs/openapi.yaml` (`LastChange`, `version`, `If-Match`, respostas 409/428, códigos no enum), `frontend/src/mocks/handlers.ts`, `organization.msw.test.ts`, `unit-settings.msw.test.ts`, `OpenApiContractTest` (contagem de respostas reutilizáveis 8 para 10) — prova: `./backend/mvnw -f backend/pom.xml test -Dtest='OpenApiContractTest,ContractRoutesTest'` → ok; `npx vitest run src/mocks` → 31 testes ok.
- T5 — arquivos: `components/LastChange.tsx`, `useOrganization.ts`, `useUnitSettings.ts`, as duas páginas, `i18n/pt-BR.json`, testes das páginas — prova: `npx vitest run src/features/organization src/features/units` → ok.
- T6 — arquivos: `components/EditConflictDialog.tsx`, `lib/useEditConflict.ts`, `components/Modal.tsx` (`returnFocusRef`: o foco volta ao Salvar mesmo quando o botão ficou desabilitado durante a gravação), `components/ui/button.tsx` (aceita `ref` como propriedade), páginas e testes (Recarregar, Sobrescrever, novo 409, erro de rede, axe claro e escuro a 360 px) — prova: `npx vitest run src/components src/features/organization src/features/units` → ok.
- T7 — o e2e `@critical` de organização não precisou de ajuste (usa o mock, que já trata a versão) — prova: regressão abaixo.

Regressão:
- `./backend/mvnw -f backend/pom.xml verify` → BUILD SUCCESS, 199 testes de integração e os rápidos, 0 falhas, JaCoCo dentro das metas.
- `npm run lint && npm run typecheck && npm run build && npm run test:cov && npm run e2e` (em `frontend/`) → lint e tipos sem erro; build ok; 51 arquivos e 384 testes passando, cobertura sem reprovar; e2e `@critical` 13 de 13.
Desvios:
- Teste de CA8 para o Administrador: só cobre `PATCH /organization` (403 antes do 428/409). Na unidade o Administrador da própria unidade escreve (RF-ORG-04), então o 403 vale só para quem não tem a linha UNIT_SETTINGS da unidade.
- `OpenApiContractTest.conventions` tinha a contagem fixa de 8 respostas reutilizáveis; passou a 10 (EditConflict e PreconditionRequired).
- O branch local chama-se `item/35-ultima-alteracao-auditoria` (o arquivo cita `item/35-ultima-alteracao-auditoria-configuracoes`); não foi trocado.
- Linha "Última alteração" do `lastChange` com autor que não é pessoa (ex.: sistema) mostra só a data (`lastChange.lineSystem`).
Não é do item: nenhum.

## Verificação

### Rodada 1 — OK — 2026-10-18, estado ccce964db1d6521982c0ae0a6afbc01b6e06bf66

| Critério | Status | Evidência |
|---|---|---|
| CA1 | PASSOU | `LastChangeIT.organizationLastChange` (verify, 199 IT, 0 falhas) |
| CA2 | PASSOU | `LastChangeIT.unitLastChange` |
| CA3 | PASSOU | `LastChangeIT.isolation` |
| CA4 | PASSOU | `LastChangeIT.audit` |
| CA5 | PASSOU | `LastChangeIT.versions` |
| CA6 | PASSOU | `LastChangeIT.versions`; `IfMatch.version` devolve 428 para ausente/não numérico |
| CA7 | PASSOU | `LastChangeIT.concurrent`; `for update` antes da checagem nas duas rotas |
| CA8 | PASSOU | `LastChangeIT.permissionsFirst` (PATCH da organização; PUT da unidade checa permissão antes de `IfMatch` no código) |
| CA9 | PASSOU | testes "CA9" das duas páginas |
| CA10 | PASSOU | testes "CA10" das duas páginas (409, Recarregar, Sobrescrever, novo 409, erro de rede) |
| CA11 | PASSOU | testes "CA11" a 360 px, axe claro e escuro; foco volta ao Salvar (testes de Recarregar) |
| CA12 | PASSOU | `verify` inclui `OpenApiContractTest` e `ContractRoutesTest`; testes MSW em `src/mocks` dentro de `test:cov` |

Comandos:
- `./backend/mvnw -f backend/pom.xml verify` -> BUILD SUCCESS, 199 IT, 0 falhas, JaCoCo ok
- `npm run lint && typecheck && build && test:cov && e2e` (frontend) -> exit 0; 51 arquivos, 384 testes; e2e 13 de 13
Achados:
- sugestão, código, `OrganizationController.update` — falta espaço em `ifMatch,body.name()`.
- sugestão, especificação — o branch local é `item/35-ultima-alteracao-auditoria`, e o arquivo cita `...-configuracoes`; alinhar o nome antes do PR.
- sugestão, código, `LastChange.find` — `order by occurred_at desc` sem desempate; dois registros no mesmo instante dão resultado indefinido.

## Entrega

- Configurações da organização e da unidade passam a devolver `lastChange` (quem alterou e quando, lido da auditoria) e uma `version` por configuração (migração V17); as gravações exigem `If-Match` (428 se ausente, 409 se desatualizada) com travamento `for update` contra gravações concorrentes.
- Telas mostram "Última alteração" e, no conflito (409), a janela com os campos diferentes, "Recarregar" e "Sobrescrever".
- Verificação: `verify` do back-end (199 IT, 0 falhas, JaCoCo ok); front-end lint, typecheck, build, `test:cov` e e2e (13 de 13) verdes.
- Sugestões da verificação sem bloqueio: desempate na ordenação de `LastChange.find`; nome do arquivo do item difere do branch.
- Sobrescrever é o cliente reenviar a edição com a versão atual; o servidor não tem modo de forçar.
