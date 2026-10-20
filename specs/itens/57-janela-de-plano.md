# 57 — RF-PLA-03 · Janela de plano

Origem: issue #57 (Sprint 06); `docs/especificacao/07-planos-e-beneficios.md` (RF-PLA-03, RN-PLA-01 a RN-PLA-06, "Dados"), `01-organizacao-e-isolamento.md` (RN-ORG-13, configuração "Treinar em outra unidade"), `02-contas-e-acesso.md` (permissão `PLANS`). Branch: `item/57-janela-de-plano`. Constrói sobre #55 (tabela `plan`, `Plans`) e #56 (`PlansTablePage`) e segue o padrão de #48 (`TrainingClassWrite`, `ClassFormDialog`).

## 1. Objetivo

Coordenador e Administrador criam e editam planos numa janela aberta da tabela de planos (modalidade, nome, valor, frequência semanal, vale em outras unidades) e desativam ou reativam um plano (nunca excluem). Ao mudar o valor, a janela mostra quantos alunos serão afetados, a partir de quando vale e as escolhas da RN-PLA-05; a escolha vai para a auditoria. O valor passa a ter histórico com data de início.

Fora do escopo: contagem real de alunos, cobranças, e-mail de reajuste (RN-PLA-04) e aplicação das escolhas da RN-PLA-05 (dependem de matrícula e cobrança, Sprint 07 em diante; ficam em pontos únicos que hoje devolvem zero e não alteram nada), plano como escolha de matrícula (Sprint 07), benefícios, tela da configuração "Treinar em outra unidade" (item de Configurações › Organização; aqui só se lê).

## 2. Requisitos

- R1 (RF-PLA-03, RN-PLA-01, D1): `POST /api/v1/plans` cria plano com `unitId`, `modalityId`, `name` (2 a 60 caracteres), `valueCents`, `weeklyFrequency` (2, 3 ou nulo = livre) e `validOtherUnits`; responde 201 com a forma de `PlanRow`. A unidade precisa estar ativa e a modalidade ativa na unidade; só mensal; um plano por unidade, modalidade e nome entre os ativos (repetido dá 409); não existe campo de várias modalidades.
- R2 (RF-PLA-03, D2): `PUT /api/v1/plans/{id}` altera `name`, `valueCents`, `weeklyFrequency` e `validOtherUnits`; `unitId` e `modalityId`, se enviados, têm de ser os atuais (senão 400); plano desativado não é editável (409); 404 para id de outra organização ou fora do alcance.
- R3 (RN-PLA-03, D3): `valueCents` é inteiro de 1 a 99.999.999; a tela digita em reais e envia centavos; valor fora disso dá 400.
- R4 (RN-PLA-02): a frequência continua só informativa: nada na API, no banco ou na tela controla ou mostra aulas feitas, presença ou comparecimento.
- R5 (RN-ORG-13, D4): `GET /api/v1/plans/options` devolve as unidades do alcance com as modalidades ativas de cada uma e `crossUnitTraining` (`plan`, `allowed` ou `denied`) da organização. O campo "vale em outras unidades" só é editável com `plan`; com `allowed` ou `denied` a janela mostra o texto da regra, sem controle, e o servidor ignora o valor enviado e grava `false`.
- R6 (RF-PLA-03, RN-PLA-04, RN-PLA-05, D5): `GET /api/v1/plans/{id}/value-impact?valueCents=` devolve `studentsAffected` (ponto único, 0 até a Sprint 07) e `effectiveFrom` fixo `NEXT_CHARGE` (próxima cobrança gerada). Na janela de edição, ao mudar o valor, a tela mostra quantos alunos serão afetados, a partir de quando vale (próxima cobrança gerada; cobrança paga não muda) e as escolhas da RN-PLA-05: cobranças geradas e não pagas, "manter no valor antigo" (padrão) ou "ajustar"; cobranças pagas de meses futuros, "não cobrar a diferença" (padrão) ou "cobrar a diferença em cobrança separada".
- R7 (RN-PLA-05, D5): o `PUT` com valor diferente aceita `unpaidCharges` (`KEEP` padrão ou `ADJUST`) e `paidFutureDifference` (`false` padrão ou `true`); outro valor nesses campos dá 400; as escolhas vão para a auditoria junto com o valor antigo e o novo; hoje nenhuma cobrança é tocada (ponto único). Sem mudança de valor, as escolhas são ignoradas.
- R8 (RN-PLA-06, D6): `POST /api/v1/plans/{id}/deactivate` e `POST /api/v1/plans/{id}/activate` mudam a situação sem apagar nada; não existe rota de exclusão. Reativar com outro plano ativo de mesmo nome na unidade e modalidade dá 409. Repetir a mesma chamada responde 200 sem mudar nada (repetição segura). O plano desativado continua na lista (filtro de situação de #56) e nas matrículas que já o usam; sumir das escolhas de matrícula fica com a Sprint 07.
- R9 (histórico, D7): o banco ganha `plan_value_history` (plano, valor, início, quem registrou); criar e mudar o valor registram uma linha; a linha nunca é alterada nem apagada.
- R10 (permissão e alcance): `PLANS` conferido no servidor em toda rota nova: Coordenador e Administrador acessam; Professor, Aluno e Responsável 403; sem sessão 401. Administrador só age em unidades dele (fora do alcance: 403 ao criar, 404 nas demais rotas); organização sempre a da sessão.
- R11 (isolamento): filtro de organização no código e RLS no banco; teste de isolamento entre organizações e entre perfis em toda rota nova.
- R12 (auditoria, D10): criar, editar, desativar e ativar gravam auditoria com antes e depois, sem dado sensível; em mudança de valor entram as escolhas da RN-PLA-05.
- R13 (tela, D8): na tabela de #56, botão "Novo plano" e, por linha, "Editar", "Desativar" e "Ativar" (confirmação ao desativar); a janela traz modalidade e unidade (fixas ao editar), nome, valor em reais, frequência (2x, 3x, livre), "vale em outras unidades" conforme R5 e o bloco de impacto de R6; quatro estados (carregando e erro com "Tentar de novo" ao carregar opções; sem permissão pela rota), erros de campo ao lado do campo, 360 px, axe nos dois temas, textos em `pt-BR.json`.
- R14 (contrato): `docs/openapi.yaml` e handlers MSW refletem as rotas; o seed existente basta.

## 3. Critérios de aceite

- CA1 (R1, R3, R9): dado Coordenador e unidade e modalidade válidas, quando faz `POST /plans` com nome "Mensal 2x", `valueCents` 15000, frequência 2, então 201 com o plano ativo, `studentsUsing` 0, e uma linha de histórico com o valor e a data de início de hoje.
- CA2 (R1, R3): dado entrada inválida (nome de 1 ou 61 caracteres, valor 0, negativo ou 100.000.000, frequência 4, sem modalidade, modalidade inativa na unidade, unidade inativa), quando cria, então 400 com o campo e nada gravado.
- CA3 (R1): dado plano ativo "Mensal 2x" no Jiu-Jitsu da unidade A, quando cria outro com o mesmo nome (sem diferença de acento ou maiúsculas) na mesma unidade e modalidade, então 409; com o mesmo nome em outra modalidade ou unidade, ou com o primeiro desativado, 201.
- CA4 (R2, R3, R9): dado plano ativo, quando `PUT` muda nome, frequência e valor, então 200 com os dados novos e o histórico ganha uma linha só quando o valor mudou; `PUT` com `unitId` ou `modalityId` diferente dá 400; plano desativado dá 409; id de outra organização dá 404.
- CA5 (R5): dado a organização com `crossUnitTraining` `plan`, quando cria com `validOtherUnits` verdadeiro, então grava verdadeiro; com `allowed` ou `denied`, grava falso mesmo com verdadeiro enviado; `GET /plans/options` traz a configuração, só unidades do alcance e só modalidades ativas de cada uma.
- CA6 (R6): dado plano com valor 15000, quando `GET /value-impact?valueCents=18000`, então 200 com `studentsAffected` 0 e `effectiveFrom` `NEXT_CHARGE`; `valueCents` inválido ou ausente dá 400.
- CA7 (R7, R12): dado `PUT` com valor novo, `unpaidCharges` `ADJUST` e `paidFutureDifference` verdadeiro, quando grava, então a auditoria `plan.updated` traz valor antigo, novo e as duas escolhas; sem escolhas, valem `KEEP` e `false`; escolha inválida dá 400; sem mudança de valor, as escolhas não aparecem na auditoria.
- CA8 (R8, R12): dado plano ativo, quando `deactivate`, então 200 e `INACTIVE`, continua em `GET /plans?status=INACTIVE`, auditoria `plan.deactivated`; repetir dá 200 sem nova auditoria; `activate` volta a `ACTIVE` com `plan.activated`; `activate` com homônimo ativo dá 409; `DELETE /plans/{id}` não existe (404 ou 405) e o plano segue no banco.
- CA9 (R4): dado o esquema, o corpo das respostas e `pt-BR.json` (chaves de `plans`), então nada menciona presença ou aulas feitas.
- CA10 (R10): dado Administrador da unidade A, quando cria em B, então 403; edita, desativa ou ativa plano de B, 404; em A, funciona; Professor, Aluno e Responsável recebem 403 em todas as rotas novas; sem sessão, 401.
- CA11 (R11): dado planos de duas organizações, quando o Coordenador de A usa as rotas com id de plano de B, então 404 e nada muda; com `ctcore_app` e contexto de A, consulta e escrita direta em `plan` e em `plan_value_history` não alcançam B.
- CA12 (R12): dado qualquer escrita bem-sucedida, então existe auditoria com ator, ação, unidade e antes e depois, sem campos de senha, token ou cartão.
- CA13 (R13): dado a tabela de planos, quando o Coordenador clica em "Novo plano", então abre a janela com as modalidades da unidade escolhida; ao salvar, a lista é recarregada e a janela fecha; erro de campo do servidor aparece no campo; "Editar" abre a janela preenchida com modalidade e unidade bloqueadas; "Desativar" pede confirmação.
- CA14 (R5, R13): dado a organização com `allowed` ou `denied`, então a janela mostra a regra em texto e nenhum controle de "vale em outras unidades"; com `plan`, mostra o controle.
- CA15 (R6, R13): dado a janela de edição, quando o valor muda, então aparece o bloco com alunos afetados, "a partir da próxima cobrança gerada" e as duas escolhas com os padrões marcados; sem mudança de valor o bloco não aparece; a consulta de impacto é refeita ao mudar o valor.
- CA16 (R13): dado a janela, então carregar as opções mostra o estado de carregamento, falha mostra "Tentar de novo"; em 360 px sem rolagem horizontal da página e axe sem violações nos dois temas, com a janela aberta.
- CA17 (R14): `OpenApiContractTest` e `ContractRoutesTest` passam com as rotas; teste MSW cobre as rotas no formato do servidor; `SeedDataIT` passa.

## 4. Plano técnico

- Dados: migração `V31__plan_value_history.sql` (depois de `V30`): `plan_value_history` (`id`, `organization_id`, `plan_id`, `value_cents bigint check (> 0)`, `starts_on date`, `created_by uuid`, `created_at`), chave estrangeira composta `(organization_id, plan_id)` para `plan(organization_id, id)`, RLS `organization_scope` como em `V30`, índice por `(plan_id, starts_on)`. Preenche uma linha por plano já existente (valor atual, `starts_on` = data de `created_at`). Acrescenta teto `value_cents <= 99999999` em `plan`. Reversão manual comentada no topo. Seed não muda. Tabela só de acréscimo: conferir ao construir como as tabelas históricas existentes limitam `update` e `delete`.
- API (cookie como as demais; `ApiValidationException` para 400, `ApiStatusException` para 409): `POST /plans` (201), `PUT /plans/{id}`, `GET /plans/options`, `GET /plans/{id}/value-impact`, `POST /plans/{id}/deactivate`, `POST /plans/{id}/activate`; respostas de plano usam `PlanRow`. Entram em `docs/openapi.yaml` no mesmo PR (`PlanInput`, `PlanOptions`, `PlanValueImpact`). Unicidade entre ativos pelo índice parcial de `V30` (409 em `DuplicateKeyException`); desativar e ativar idempotentes por estado.
- Back-end: criar `plan/PlanWrite.java` (validação, alcance, `OrganizationTransactions.execute`, `AuditService.record`, como `TrainingClassWrite`); alterar `plan/Plans.java` (abrir `requireAccess` e `reach` ao pacote, método `get` por id com alcance, pontos únicos `studentsAffected` e `applyRepricing` que hoje nada fazem) e `plan/PlanController.java` (rotas novas). Modalidades da unidade de `unit_modality` (V21), como em `TrainingClassWrite.options`; `crossUnitTraining` de `organization_settings.cross_unit_training` (V3). Nome normalizado por `Modalities.normalize`.
- Interface: criar `frontend/src/features/plans/PlanFormDialog.tsx`, `DeactivatePlanDialog.tsx` e `PlanFormDialog.test.tsx`; alterar `PlansTablePage.tsx` (botão e coluna de ações, recarregar ao salvar), `usePlans.ts` (opções, salvar, impacto, ativar e desativar), `mocks/handlers.ts` (+ `plans.msw.test.ts`) e `i18n/pt-BR.json`. Modelos: `ClassFormDialog.tsx` e `DeactivateClassDialog.tsx`; moeda: `formatMoney`; reais para centavos sem ponto flutuante.
- Segurança: perfil, alcance e organização vêm do principal; `PLANS` conferido em cada rota; parâmetros ligados; entrada validada no servidor (tamanho, faixa, enumerações); auditoria sem dado sensível; plano nunca excluído; id de outra organização responde 404.
- Arquivos: a alterar (vistos): `backend/src/main/java/br/com/ctcore/plan/Plans.java`, `PlanController.java`, `frontend/src/features/plans/PlansTablePage.tsx`, `usePlans.ts`, `frontend/src/mocks/handlers.ts`, `frontend/src/i18n/pt-BR.json`, `docs/openapi.yaml`; a criar: `V31__plan_value_history.sql`, `PlanWrite.java`, `PlanWriteIT.java`, `PlanFormDialog.tsx`, `PlanFormDialog.test.tsx`, `DeactivatePlanDialog.tsx`.
- Reuso: `TrainingClassWrite` (validação, alcance, auditoria), `ClassFormDialog` e `DeactivateClassDialog`, `Plans.reach`, `Modalities.normalize`, `formatMoney`, componentes de formulário e estados padrão de #8.
- Riscos: (1) o impacto mostra sempre zero aluno até a Sprint 07; o teste prova o formato e o ponto único. (2) O teto novo em `plan` precisa caber nos dados de seed de #55; o `SeedDataIT` confere. (3) Canvas `PlanosRegras.dc.html` inacessível: o dono confere o layout no PR. (4) Sem versão otimista no `PUT` (D9).

## 5. Tarefas

Duas partes: A, servidor e contrato (T1 a T4); B, interface (T5 a T7); T8 fecha.

- [x] T1: migração `V31__plan_value_history.sql` (histórico, preenchimento dos planos existentes, teto do valor) — cobre CA1 (histórico) e CA11 (RLS) — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test='PlanWriteIT,SeedDataIT'`.
- [x] T2: `PlanWrite` com `POST /plans`, `PUT /plans/{id}`, `GET /plans/options` e `GET /plans/{id}/value-impact`, validação, 409 de nome, `crossUnitTraining`, histórico e pontos únicos — cobre CA1 a CA7 — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test=PlanWriteIT`.
- [x] T3: `deactivate` e `activate` idempotentes, sem rota de exclusão — cobre CA8 — prova: `PlanWriteIT`.
- [x] T4: perfis, alcance do Administrador, isolamento com RLS, auditoria, ausência de presença no esquema e nas respostas, e OpenAPI (`PlanInput`, `PlanOptions`, `PlanValueImpact`, rotas) — cobre CA9 a CA12 e CA17 (contrato) — prova: `PlanWriteIT` e `./backend/mvnw -f backend/pom.xml test -Dtest='OpenApiContractTest,ContractRoutesTest'`.
- [x] T5: handlers MSW das rotas novas com teste — cobre CA17 (MSW) — prova: `npx vitest run src/mocks` em `frontend/`.
- [x] T6: `PlanFormDialog` (campos, reais para centavos, regra de "vale em outras unidades", bloco de impacto com padrões, erros de campo), hooks e textos pt-BR — cobre CA13 a CA15 — prova: `npx vitest run src/features/plans` em `frontend/`.
- [x] T7: botão "Novo plano" e ações por linha em `PlansTablePage` (editar, desativar com confirmação, ativar), estados, 360 px e axe com a janela aberta — cobre CA13 e CA16 — prova: `npx vitest run src/features/plans`.
- [x] T8: regressão completa (não cobre critério: confere cobertura e os 100% dos pacotes exigidos) — prova: `./backend/mvnw -f backend/pom.xml verify` e, em `frontend/`, `npm run lint && npm run typecheck && npm run build && npm run test:cov && npm run e2e`.

## 6. Decisões

- D1 (muda o comportamento): só Coordenador e Administrador criam e editam plano (`PLANS`, a mesma de #55 e #56), no alcance das unidades; o Administrador não cria plano em unidade alheia. Alternativa: só Coordenador; descartada porque a tabela de permissões dá `PLANS` completo ao Administrador na unidade.
- D2 (muda o comportamento): unidade e modalidade não mudam depois de criado o plano; para outra, cria-se outro e desativa-se o antigo. Motivo: matrículas e cobranças apontam para o plano (RN-PLA-01, RN-PLA-06). Alternativa: permitir trocar; descartada por quebrar o histórico.
- D3: valor de 1 a 99.999.999 centavos, por sanidade; o card só exige centavos inteiros. Suposição: nenhuma academia tem plano acima disso; se houver, a mudança é aditiva.
- D4 (muda o comportamento): com "Treinar em outra unidade" em `allowed` ou `denied`, o plano guarda `false` e a janela não oferece o controle (RN-ORG-13: vale para todos os planos). Alternativa: guardar o valor efetivo; descartada porque mudar a configuração depois alteraria o que a academia escolheu. Quem consome o plano (Sprint 07) combina configuração e campo.
- D5: sem aluno nem cobrança ainda, o impacto e a aplicação das escolhas da RN-PLA-05 ficam em pontos únicos que hoje devolvem zero e não alteram cobrança; a janela e a API já mostram, validam e auditam as escolhas, e a Sprint 07 e a cobrança trocam os pontos (o e-mail da RN-PLA-04 vai junto). Padrões da RN-PLA-05: manter não pagas no valor antigo, não cobrar diferença. Alternativa: esconder o bloco até haver alunos; descartada porque o card pede mostrá-lo.
- D6 (muda o comportamento): desativar e ativar são ações próprias, não campo do `PUT`; reativar só sem homônimo ativo (409); plano desativado não é editável (reativa-se antes). Alternativa: desativado editável; descartada por tornar o histórico de valor ambíguo.
- D7: histórico guarda valor e data de início (hoje); só acrescenta linhas; planos de #55 ganham a linha inicial na migração. "Vale a partir da próxima cobrança" é efeito da cobrança, não do histórico. Alternativa: criar o histórico só na primeira mudança de valor; descartada porque o histórico faz parte dos dados do plano (`07`, "Dados").
- D8: a janela abre da tabela de #56 (modal como `ClassFormDialog`), sem rota própria; a coluna de ações é a prevista no risco 1 de #56.
- D9: sem versão otimista no `PUT` (padrão de #48): em edição simultânea a última vence, ambas auditadas. Suposição; se o dono quiser `version`, vira item à parte.
- D10: auditoria com ações `plan.created`, `plan.updated`, `plan.deactivated`, `plan.activated`, entidade `plan`. Notas de versão: não há arquivo de notas no repositório (conferido em `docs/` e na raiz); não se cria um aqui. Suposição: a entrega registra a mudança na descrição do PR.
- Suposição: o canvas `PlanosRegras.dc.html` não está acessível; se a janela do canvas tiver campos além dos do card, ajusta-se no PR.

## Construção

- T1 — arquivos: `backend/src/main/resources/db/migration/V31__plan_value_history.sql` (histórico, preenchimento dos planos existentes, teto `plan_value_cents_max`, RLS, sem UPDATE/DELETE para o papel operacional como em V19) — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test='PlanWriteIT,SeedDataIT'` → 24 testes, 0 falhas
- T2 — arquivos: `plan/PlanWrite.java` (novo), `plan/Plans.java` (`get`, `reach` aberto, pontos únicos `studentsAffected` e `applyRepricing`), `plan/PlanController.java`, `PlanWriteIT.java` (novo) — prova: `verify -Dit.test=PlanWriteIT` → 10 testes, 0 falhas
- T3 — `PlanWrite.setActive` (idempotente, 409 com homônimo ativo, sem rota de exclusão) — prova: `PlanWriteIT.activation` → passa
- T4 — arquivos: `docs/openapi.yaml` (rotas e `PlanInput`, `PlanOptions`, `PlanValueImpact`), `PlanWriteIT` (perfis, alcance, isolamento com `ctcore_app`, auditoria, sem presença) — prova: `./backend/mvnw -f backend/pom.xml test -Dtest='OpenApiContractTest,ContractRoutesTest'` → 7 testes, 0 falhas
- T5 — arquivos: `frontend/src/mocks/handlers.ts` (planos mutáveis, 6 rotas novas), `frontend/src/mocks/plans.msw.test.ts` — prova: `npx vitest run src/mocks` → 122 testes, 0 falhas
- T6 — arquivos: `frontend/src/features/plans/PlanFormDialog.tsx`, `PlanFormDialog.test.tsx` (novos), `usePlans.ts` (opções, impacto, salvar, reais para centavos), `i18n/pt-BR.json` — prova: `npx vitest run src/features/plans` → 31 testes, 0 falhas
- T7 — arquivos: `PlansTablePage.tsx` (botão "Novo plano", coluna de ações, recarregar ao salvar), `DeactivatePlanDialog.tsx` (novo); axe nos dois temas a 360 px com a janela e o bloco de impacto abertos em `PlanFormDialog.test.tsx` — prova: `npx vitest run src/features/plans` → passa
- T8 — ver regressão.

Regressão: `./backend/mvnw -f backend/pom.xml verify` → 418 testes rápidos e de integração, 0 falhas, "All coverage checks have been met" (JaCoCo); em `frontend/`: `npm run lint && npm run typecheck && npm run build && npm run test:cov && npm run e2e` → lint e tipos limpos, build ok, 81 arquivos e 735 testes passam, cobertura 96,99% de linhas, e2e `@critical` 28 passaram.
Desvios: (1) a ação de ativar/desativar na tabela só aparece com `PLANS` diferente de `NONE` (padrão de `ClassesPage`); (2) o corpo do `POST/PUT` recebe `valueCents` como número decimal no Java (`BigDecimal`) para recusar fração (150,5) em vez de truncar; (3) a data de início do histórico usa o fuso da unidade; (4) um teste de esquema que contava colunas passou a filtrar `table_schema = 'public'` (a primeira rodada completa falhou por esquemas de outros ITs no mesmo banco; corrigido e a regressão rodou de novo e passou); (5) o seed não ganhou linha de histórico (o plano semeado não tem, e o item não mexe no seed), só os planos existentes na migração.
Não é do item: nada.

## Verificação

### Rodada 1 — OK — 2026-10-20, estado 379ccc230ee6698f6d7f6ca3341a133953c4c096

| Critério | Status | Evidência |
|---|---|---|
| CA1 | PASSOU | `PlanWriteIT.create` (verify completo, 418 testes, 0 falhas) |
| CA2 | PASSOU | `PlanWriteIT.invalid` |
| CA3 | PASSOU | `PlanWriteIT.uniqueName` |
| CA4 | PASSOU | `PlanWriteIT.update` |
| CA5 | PASSOU | `PlanWriteIT.crossUnit` |
| CA6 | PASSOU | `PlanWriteIT.impact` |
| CA7 | PASSOU | `PlanWriteIT.update` e leitura de `PlanWrite.update` (auditoria com escolhas só se o valor muda) |
| CA8 | PASSOU | `PlanWriteIT.activation` |
| CA9 | PASSOU | `PlanWriteIT.schema` |
| CA10 | PASSOU | `PlanWriteIT.permissions` |
| CA11 | PASSOU | `PlanWriteIT.isolation` (com `ctcore_app`; V31 com RLS e sem UPDATE/DELETE) |
| CA12 | PASSOU | auditoria nos testes de create, update e activation |
| CA13 | PASSOU | `PlanFormDialog.test.tsx` (CA13) |
| CA14 | PASSOU | `PlanFormDialog.test.tsx` (CA14) |
| CA15 | PASSOU | `PlanFormDialog.test.tsx` (CA15) |
| CA16 | PASSOU | `PlanFormDialog.test.tsx` (CA16; 360 px e axe nos dois temas) |
| CA17 | PASSOU | `OpenApiContractTest`, `ContractRoutesTest`, `SeedDataIT` e `plans.msw.test.ts` dentro do verify e do test:cov |

Comandos: `./backend/mvnw -f backend/pom.xml verify` → 418 testes, 0 falhas, JaCoCo ok; em `frontend/`: `npm run lint && npm run typecheck && npm run build && npm run test:cov && npm run e2e` → tudo passou, cobertura de linhas 96,99%, e2e 28 passaram.
Achados:
- sugestão, código, `PlanWrite.clean` — `unpaidCharges` inválido dá 400 mesmo sem mudança de valor, e R7 diz que sem mudança as escolhas são ignoradas; efeito pequeno.

## Entrega

Janela de plano (RF-PLA-03): criar, editar, ativar e desativar plano (sem exclusão), com nome único por unidade, valor em centavos com teto, histórico de valor (V31, RLS, sem UPDATE/DELETE para o papel operacional), aviso de impacto nos alunos e escolha sobre cobranças em aberto, auditoria e isolamento entre unidades, organizações e perfis. Front com `PlanFormDialog`, `DeactivatePlanDialog`, botão "Novo plano" e ações na tabela; OpenAPI e MSW atualizados.

Verificação: back-end `verify` com 418 testes e JaCoCo ok; front-end lint, tipos, build, `test:cov` (96,99%) e e2e `@critical` (28) verdes. A base `dev` não avançou; regressão da verificação vale.
Limitações: `unpaidCharges` inválido dá 400 mesmo sem mudança de valor (efeito pequeno); desvios registrados em "Construção".

Refs #57
