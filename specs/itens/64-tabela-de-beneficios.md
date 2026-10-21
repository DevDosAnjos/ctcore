# 64 — RF-BEN-01 · Tabela de benefícios

Origem: issue #64 (Sprint 07); `docs/especificacao/07-planos-e-beneficios.md` (RF-BEN-01, RN-BEN-02, 03, 06, 07; RN-BEN-01 e 04 como contexto do modelo), `02-contas-e-acesso.md` (permissão `BENEFITS`), `13-comunicacao-e-notificacoes.md` (RN-NOT-04). Branch: `item/64-tabela-de-beneficios`. Constrói sobre #55/#56 (`Plans`, `PlansPage`, `PlansTablePage`) e #63 (`enrollment_modality`). Canvas `BeneficiosLista.dc.html` inacessível ao agente.

## 1. Objetivo

A gestão (Coordenador; Administrador nas unidades dele) abre Financeiro › Planos e benefícios › Benefícios e vê a tabela de benefícios com aluno, modalidade, tipo, valor, motivo, decidido por, validade e situação, com filtros por tipo, modalidade, situação e "Benefícios altos". O cartão "Benefícios" de `/planos` passa a mostrar contagens reais.

Fora do escopo: conceder (#65) e encerrar (#66) benefício, aplicação do benefício na cobrança (Sprint 08), envio da notificação ao Coordenador (a caixa de notificações é do módulo 13; ver D5). Este item cria a tabela de dados que #65 e #66 gravam.

## 2. Requisitos

- R1 (RF-BEN-01, D1): `GET /api/v1/benefits` devolve, no envelope `{items, page, size, total}`, os benefícios da organização da sessão com `id`, `student` (id e nome), `unit` (id e nome), `modality` (id e nome), `type` (`PERCENT` ou `AMOUNT`), `value` (percentual inteiro de 1 a 100, ou centavos), `reason`, `decidedBy`, `grantedBy` (nome da conta), `validUntil` (data ou nulo = sem prazo), `status` (`ACTIVE`, `EXPIRED`, `ENDED`), `high` e `monthlyValueCents`, ordenados por nome do aluno e modalidade.
- R2 (RF-BEN-01, D2): filtros opcionais `type`, `modalityId`, `status`, `high=true` (Benefícios altos), `unitId` e `q` (nome do aluno, sem diferença de acento ou maiúsculas), combináveis; valor inválido de `type` ou `status` dá 400.
- R3 (RN-BEN-02, D3): o tipo é bolsa em percentual (inteiro de 1 a 100) ou desconto em reais (centavos, maior que zero); o banco recusa outros valores; não existe campo de faixa nem de pontualidade.
- R4 (RN-BEN-03): todo benefício guarda motivo, quem decidiu, quem lançou e validade (sem prazo ou data); a tabela os mostra.
- R5 (RN-BEN-06, D4): `high` é verdadeiro quando a bolsa passa de 50% ou o desconto em reais passa de metade do valor do plano da modalidade; o filtro "Benefícios altos" devolve só esses; não há aprovação.
- R6 (RN-BEN-07): a mensalidade com benefício (`monthlyValueCents`) nunca é negativa: valor do plano menos o desconto, no mínimo zero; bolsa de 100% dá zero. A conta fica em um método único reutilizável por #65 e pela cobrança.
- R7 (D6): a situação é calculada, nunca digitada: `ENDED` se encerrado, `EXPIRED` se ativo com validade anterior a hoje no fuso da unidade, senão `ACTIVE`. A validade vale até o fim do dia informado.
- R8 (permissão e alcance, D7): `BENEFITS` conferido no servidor: Coordenador e Administrador acessam; Professor, Aluno e Responsável 403; sem sessão 401. Administrador só vê benefícios de matrículas em unidades dele; `unitId` fora do alcance dá lista vazia. Organização sempre a da sessão.
- R9 (isolamento): filtro de organização no código e RLS no banco na tabela nova; teste de isolamento entre organizações e entre perfis.
- R10 (dados, D8): tabela `benefit` ligada a `enrollment_modality`, no máximo um benefício ativo por modalidade da matrícula (índice parcial), sem exclusão pelo papel de execução.
- R11 (resumo): `GET /plans/summary` passa a devolver `benefits.active` e `benefits.high` reais (ativos não vencidos e, entre eles, altos), com o mesmo alcance; `plans.studentsUsing` fica como está.
- R12 (tela, D9): `/beneficios` mostra a tabela com paginação e os filtros; o botão "Ver benefícios" de `/planos` passa a levar a ela; quatro estados, 360 px, axe nos dois temas, textos em `pt-BR.json`; vencido e encerrado têm selo próprio; sem botões de conceder e encerrar (são de #65 e #66).
- R13 (contrato): `docs/openapi.yaml` e handlers MSW refletem a rota; seed com benefícios sintéticos.
- R14 (auditoria): a rota só lê, não grava auditoria (D10).

## 3. Critérios de aceite

- CA1 (R1, R4): dado benefícios na organização, quando o Coordenador faz `GET /benefits`, então 200 com `items` ordenados, cada um com aluno, unidade, modalidade, tipo, valor, motivo, decidido por, lançado por, validade, situação, `high` e `monthlyValueCents`; `total`, `page` e `size` corretos e `size` fora de 25/50/100 dá 400.
- CA2 (R2): dado benefícios de tipos, modalidades, situações e níveis diferentes, quando filtra por `type`, `modalityId`, `status`, `high=true`, `unitId` e `q` ("joao" acha "João"), sozinhos e combinados, então só voltam os que atendem a todos; `type=X` ou `status=XYZ` dá 400.
- CA3 (R3): dado o banco, quando se tenta inserir bolsa de 0% ou 101%, desconto de 0 centavo ou tipo desconhecido, então o `check` recusa.
- CA4 (R5): dado bolsa de 50%, bolsa de 51%, desconto de R$ 75 e de R$ 76 em plano de R$ 150, então `high` é falso, verdadeiro, falso, verdadeiro; `high=true` devolve só os dois últimos mais a bolsa de 51%.
- CA5 (R6): dado plano de R$ 150, desconto de R$ 200 e bolsa de 100%, então `monthlyValueCents` é 0 nos dois; desconto de R$ 50 dá 10000; bolsa de 30% dá 10500. Teste unitário do método único cobre os limites.
- CA6 (R7): dado benefício ativo com validade ontem, de hoje e sem prazo, e outro encerrado, então as situações são `EXPIRED`, `ACTIVE`, `ACTIVE` e `ENDED`; o dia limite conta no fuso da unidade.
- CA7 (R8): dado Administrador com a unidade A e benefícios em A e B, quando lista, então só vê os de A; `unitId` de B dá `items` vazio; Professor, Aluno e Responsável recebem 403; sem sessão 401.
- CA8 (R9, R10): dado benefícios de duas organizações, quando o Coordenador de A lista, então não vê os de B; com `ctcore_app` e contexto de A, consulta direta a `benefit` não enxerga B; um segundo benefício ativo na mesma modalidade da matrícula é recusado pelo índice; `DELETE` pelo papel de execução é negado.
- CA9 (R11): dado benefícios ativos, vencido, encerrado, alto e comum, quando consulta `/plans/summary`, então `benefits.active` e `benefits.high` contam só os ativos não vencidos; Administrador conta só as unidades dele.
- CA10 (R12): dado a tela `/beneficios`, quando abre, então vê a tabela (aluno, modalidade, tipo, valor formatado em reais ou %, motivo, decidido por, validade ou "Sem prazo", situação) e, ao escolher um filtro, a consulta é refeita só com ele; em 360 px sem rolagem horizontal da página e axe sem violações nos dois temas.
- CA11 (R12): dado a tela, então carregando mostra o estado de carregamento; sem benefícios mostra vazio (e outro texto com "Limpar filtros" quando o filtro não acha nada); erro mostra "Tentar de novo"; Professor vê a tela de sem permissão; selos "Vencido" e "Encerrado" aparecem.
- CA12 (R12, R13): dado `/planos`, quando clica em "Ver benefícios", então vai a `/beneficios`; `OpenApiContractTest` e `ContractRoutesTest` passam; teste MSW cobre a rota no formato do servidor.

## 4. Plano técnico

- Dados: criar `V34__benefit.sql` (comentários em inglês, como V33): `benefit` com `id`, `organization_id`, `enrollment_modality_id` (FK composta com a organização), `student_id`, `unit_id` (cópia da unidade da modalidade, para alcance e índice, como `enrollment_modality`), `type` (`PERCENT`/`AMOUNT`), `value` bigint com checks (1 a 100 se `PERCENT`; maior que zero se `AMOUNT`), `reason` (2 a 200), `decided_by` (2 a 80), `granted_by_account_id` (FK `account`), `valid_until` date nulo, `status` (`ACTIVE`/`ENDED`), `ended_at`, `end_reason`, `created_at`; check `(status='ENDED') = (ended_at is not null)`; índice único parcial `(enrollment_modality_id) where status='ACTIVE'`; índices por organização e unidade; RLS forçada; `DELETE` revogado como em V33. Reversão manual: `drop table benefit`.
- API: `GET /api/v1/benefits?page&size&q&type&modalityId&unitId&status&high` (200 `PageResponse` de `BenefitRow`; 400, 401, 403) e `benefits` do `GET /plans/summary` com números reais. Entram em `docs/openapi.yaml`.
- Back-end: criar pacote `benefit` com `Benefits.java` (permissão `BENEFITS` pelo mesmo `requireAccess` de `Plans`, alcance reaproveitado de `Plans.reach`, `list`, contagem para o resumo e o método estático `monthlyValueCents`) e `BenefitController.java`; alterar `plan/Plans.java` (o ponto único `benefits(Scope)` chama a contagem real). `granted_by` vem do join com `account.name`.
- Interface: criar `frontend/src/features/benefits/BenefitsPage.tsx`, `BenefitFilters.tsx`, `useBenefits.ts` e testes; alterar `PlansPage.tsx` (botão vira link ativo), `App.tsx` (rota `/beneficios` em `RequireArea`, como `/planos/lista`), `mocks/handlers.ts` (+ teste MSW) e `i18n/pt-BR.json`. Usa `QueryPage`/`DataTable`/`Filter` como `PlansTablePage` e `formatMoney`; opções de modalidade de `GET /modalities` e de unidade de `/units/reachable`.
- Seed: `infra/seed/15-beneficios.sql` (`on conflict do nothing`) com benefícios sobre as matrículas de `14-matriculas.sql`: bolsa comum, bolsa alta, desconto em reais alto, um vencido e um encerrado.
- Segurança: parâmetros ligados; perfil e alcance do principal; a resposta traz só nome do aluno (sem CPF, contato ou dado de saúde); `reason` é texto livre da academia, exibido como texto (sem HTML).
- Arquivos: a alterar (vistos): `Plans.java`, `PlansPage.tsx`, `PlansPage.test.tsx`, `usePlans.ts` (se o tipo do resumo mudar), `App.tsx`, `mocks/handlers.ts`, `pt-BR.json`, `docs/openapi.yaml`, `PlanSummaryIT.java`; a criar: `V34__benefit.sql`, `Benefits.java`, `BenefitController.java`, `BenefitListIT.java`, `BenefitsTest.java` (cálculo), `15-beneficios.sql`, `BenefitsPage.tsx`, `BenefitFilters.tsx`, `useBenefits.ts`, `BenefitsPage.test.tsx`, `benefits.msw.test.ts`.
- Reuso: `PageRequest`/`PageResponse`, `QueryPage`, `DataTable`, `PlanFilters` como modelo, `Modalities.normalize`, `Plans.reach`, fuso de `unit.timezone` (como em `Agenda`).
- Riscos: (1) #65 e #66 gravam na tabela; o modelo cobre status, encerramento e motivo para não exigir nova migração. (2) `high` de desconto em reais usa o valor atual do plano; se o plano for reajustado (#57) a marca muda na leitura, sem gravar. (3) Canvas inacessível: o dono confere o layout no PR.

## 5. Tarefas

- [x] T1: migração `V34__benefit.sql` e seed `15-beneficios.sql` (preparação: sem a tabela nada lista) — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test=BenefitListIT` e `SeedDataIT`.
- [x] T2: `Benefits.list`, `monthlyValueCents`, situação calculada, `high`, `GET /benefits` com permissão, alcance, paginação e filtros, `BenefitsTest` e `BenefitListIT` (lista, ordem, filtros, 400, checks do banco, índice de um ativo, perfis, Administrador por unidade, isolamento com RLS, `DELETE` negado) — cobre CA1 a CA8 — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test=BenefitListIT` e `-Dtest=BenefitsTest`.
- [x] T3: resumo real em `Plans.benefits` e `PlanSummaryIT` — cobre CA9 — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test=PlanSummaryIT`.
- [x] T4: OpenAPI (`GET /benefits`, `BenefitRow`, `BenefitList`) e handler MSW com teste — cobre CA12 — prova: `./backend/mvnw -f backend/pom.xml test -Dtest='OpenApiContractTest,ContractRoutesTest'` e `npx vitest run src/mocks` em `frontend/`.
- [x] T5: `BenefitsPage`, `BenefitFilters`, `useBenefits`, rota `/beneficios`, link em "Ver benefícios", textos pt-BR, quatro estados, 360 px e axe — cobre CA10, CA11, CA12 (link) — prova: `npx vitest run src/features/benefits src/features/plans` em `frontend/`.
- [x] T6: regressão completa (não cobre critério: confere cobertura e pacotes exigidos) — prova: `./backend/mvnw -f backend/pom.xml verify` e, em `frontend/`, `npm run lint && npm run typecheck && npm run build && npm run test:cov && npm run e2e`.

## 6. Decisões

- D1: lista paginada (25/50/100), como as demais tabelas. Alternativa: devolver tudo; descartada pelo volume.
- D2: além dos filtros do card (tipo, modalidade, situação, Benefícios altos), aceita unidade e busca por nome do aluno, porque o Coordenador vê várias unidades e a lista cresce. Alternativa: só os quatro; descartada por deixar a lista difícil de usar.
- D3: valor em uma coluna só: percentual inteiro (1 a 100) para bolsa e centavos para desconto, conforme `type`. Alternativa: duas colunas; descartada por permitir estado inválido. Percentual fracionário não existe na regra ("percentual livre de 1 a 100%").
- D4: "desconto acima de metade do plano" compara com o valor atual do plano da modalidade; "acima" é estritamente maior que 50% (50% exato não é alto). Suposição: se a regra quiser "50% ou mais", muda só a comparação.
- D5 (muda o comportamento): a notificação ao Coordenador (RN-BEN-06, RN-NOT-04) não é enviada neste item: não existe caixa de notificações ainda (módulo 13) e quem cria o benefício é #65. Este item entrega a marca `high` e o filtro; a notificação é requisito de #65 sobre a infraestrutura do módulo 13 (sugestão ao maestro: registrar o encaixe em #65). Alternativa: criar a notificação aqui; descartada por não haver onde gravar nem quem dispare.
- D6: situação `EXPIRED` é calculada na leitura (validade anterior a hoje no fuso da unidade); não há tarefa agendada que grave "Vencido". Alternativa: job; descartada porque situações são calculadas (projeto.md §6) e a cobrança já ignora o vencido (RN-BEN-04).
- D7 (muda o comportamento): mesma permissão `BENEFITS` e alcance de `PLANS`; fora do alcance, lista vazia e não erro (RF-PLA-01). O alcance do Administrador é a unidade da matrícula, não a do cadastro do aluno. Alternativa: 404; descartada por não expor se a unidade existe.
- D8: "lançado por" guarda a conta (FK) e a tabela mostra o nome; "decidido por" é texto livre, porque quem decide pode não ter conta (RF-BEN-02 pede o campo). A tabela fica pronta para #65/#66, que só inserem e encerram linhas.
- D9: a tela fica em `/beneficios` (rota já listada em `menuCatalog.ts`); `/planos` continua sendo a página dos cartões. Alternativa: dentro de `/planos`; descartada porque o cartão "Ver benefícios" aponta para tela própria, como em #56.
- D10: sem auditoria nem notas de versão: só leitura; a gravação entra em #65 e #66.
- Suposição: o canvas `BeneficiosLista.dc.html` não está acessível; se a tabela do canvas tiver outras colunas além das do card, ajusta-se no PR.

## Construção

- T1 — arquivos: `backend/src/main/resources/db/migration/V34__benefit.sql`, `infra/seed/15-beneficios.sql`, `SeedDataIT.java` (limpeza inclui `benefit`; novo teste `benefitsSeed`) — prova: `verify -Dit.test=BenefitListIT,PlanSummaryIT,SeedDataIT` → SeedDataIT 16 testes verdes
- T2 — arquivos: `benefit/Benefits.java`, `benefit/BenefitController.java`, `plan/Plans.java` (`requireAccess` e `reach` viraram públicos), `BenefitsTest.java`, `BenefitListIT.java` — prova: `verify -Dit.test=BenefitListIT -Dtest=BenefitsTest` → BenefitsTest 2/2, BenefitListIT 6/6
- T3 — arquivos: `plan/Plans.java` (`benefits(Scope)` com contagem real), `PlanSummaryIT.java` (`benefitCounts`) — prova: `verify -Dit.test=PlanSummaryIT` → 5/5
- T4 — arquivos: `docs/openapi.yaml` (`/benefits`, `BenefitRow`, `BenefitList`, texto do resumo), `frontend/src/mocks/handlers.ts` (+ benefícios fictícios e resumo real), `mocks/benefits.msw.test.ts`, `mocks/plans.msw.test.ts` — prova: `test -Dtest=OpenApiContractTest,ContractRoutesTest` → 7/7; `vitest run src/mocks` verde
- T5 — arquivos: `features/benefits/{BenefitsPage,BenefitFilters}.tsx`, `useBenefits.ts`, `BenefitsPage.test.tsx`, `PlansPage.tsx` (+ teste), `App.tsx`, `menuCatalog.ts` (benefits disponível), `buildMenu.test.ts`, `i18n/pt-BR.json` — prova: `vitest run src/features/benefits src/features/plans src/mocks src/features/permissions` → 206/206
- T6 — regressão: `./backend/mvnw -f backend/pom.xml verify` → BUILD SUCCESS, 492 testes, JaCoCo ok; em `frontend/`: `npm run lint && typecheck && build && test:cov && e2e` → 876 testes, e2e 32 passaram

Regressão: sem falha nova; nada que já falhava foi encontrado.
Desvios: (1) `Plans.benefits` não chama `Benefits` (evita ciclo de beans); `Benefits` expõe constantes SQL (`STATUS_SQL`, `HIGH_SQL`, `FROM_SQL`) usadas pelos dois. (2) Item de menu "Benefícios" passou a disponível (necessário para a rota, D9); ajustados `buildMenu.test.ts` e o teste de menu de `PlansPage.test.tsx`. (3) A mensalidade com benefício arredonda a bolsa em meio centavo para cima (`(plano*pct+50)/100`).
Não rodou: axe real em 360 px/e2e do canvas fica coberto só pelos testes jsdom; layout do canvas inacessível (conferência do dono no PR).

## Verificação

### Rodada 1 — OK — 2026-10-21, estado 642aa519515d042c18bd0f8b8255378b9a34f25a

| Critério | Status | Evidência |
|---|---|---|
| CA1 | PASSOU | `BenefitListIT.listAndHigh` (lista, ordem, campos, paginação) no `verify` backend |
| CA2 | PASSOU | `BenefitListIT.filters` (type, modalityId, status, high, unitId, q; 400) |
| CA3 | PASSOU | `BenefitListIT.checks` (0%, 101%, 0 centavo, tipo desconhecido) |
| CA4 | PASSOU | `BenefitListIT.listAndHigh` (50/51/7500/7600 sobre plano de 15000) |
| CA5 | PASSOU | `BenefitsTest.monthlyValue` e `limits` |
| CA6 | PASSOU | `BenefitListIT.statuses` (ontem, hoje, sem prazo, encerrado) |
| CA7 | PASSOU | `BenefitListIT.permissions` (Administrador por unidade, 403, 401) |
| CA8 | PASSOU | `BenefitListIT.isolationAndSchema` (RLS com `ctcore_app`, índice de um ativo, DELETE negado) |
| CA9 | PASSOU | `PlanSummaryIT.benefitCounts` (5/5 no verify) |
| CA10 | PASSOU | `BenefitsPage.test.tsx` (colunas, filtros, axe a 360 px nos dois temas); a rolagem horizontal real não é medida em jsdom |
| CA11 | PASSOU | `BenefitsPage.test.tsx` (vazio, Limpar filtros, erro, sem permissão, selos) |
| CA12 | PASSOU | `PlansPage.test.tsx` (link), `OpenApiContractTest`/`ContractRoutesTest` dentro do verify, `benefits.msw.test.ts` |

Comandos: `./backend/mvnw -f backend/pom.xml verify` → BUILD SUCCESS, 492 testes, JaCoCo ok; em `frontend/`: `npm run lint && typecheck && build && test:cov && e2e` → exit 0, e2e 32 passaram.
Achados:
- sugestão, código, `Benefits.java` — a bolsa é arredondada em meio centavo para cima (desvio 3 já declarado); registrar na especificação da cobrança (Sprint 08) para o arredondamento ficar único.
- sugestão, código, `BenefitsPage` — layout do canvas inacessível e 360 px só em jsdom; o dono confere no PR.

### Rodada 2 — OK — 2026-10-21, estado 66a8a24baf8b07a248ef4dd2e9d0d3ab2d6ccbd3

Mudança desde a rodada 1: só `SeedDataIT.cleanTeachers` (limpeza de contas e vínculos) e a especificação; nenhum código de produto. CA1 a CA12 mantidos da rodada 1 (produto inalterado); CA1 a CA9 reexecutados pelos ITs abaixo.

Comandos:
- `verify -Dit.test=BenefitListIT,SeedDataIT,EnrollmentChangesIT,PlanSummaryIT -Dtest=BenefitsTest` → BUILD SUCCESS, 31 testes, 0 falhas.
- `./backend/mvnw -f backend/pom.xml verify` → BUILD SUCCESS, 492 testes, JaCoCo ok.
- Front-end não reexecutado (nenhum arquivo de front mudou).
Achados:
- A falha do CI (`EnrollmentChangesIT.clean` por FK `account_link`) está corrigida; a causa descrita é coerente com o diff.
- sugestão, código, `SeedDataIT.cleanTeachers` — a lista de tabelas é frágil; tabela nova com FK para `account` quebra de novo. Considerar um helper de limpeza comum aos ITs.

## Entrega

- Tabela de benefícios (RF-BEN-01): migração `V34__benefit`, rota de listagem com filtros (tipo, modalidade, status, alto, unidade, busca), validações de limites e valor da mensalidade com benefício, contagens no resumo de planos, semente `15-beneficios.sql`, contrato no OpenAPI e tela `BenefitsPage` com os quatro estados.
- Verificação: `verify` do back-end (492 testes, JaCoCo ok) e, no front-end, lint, typecheck, build, `test:cov` e `e2e` (32) verdes; rodada 1 da verificação OK.
- Decisões: a bolsa arredonda meio centavo para cima (registrar na cobrança da Sprint 08); `Plans.benefits` não chama `Benefits` para evitar ciclo de beans; item de menu "Benefícios" passou a disponível.
- Limitações: axe a 360 px só em jsdom; o layout do canvas fica para conferência do dono no PR.
- Verificações do PR (#305): `backend` falhou no CI (4 erros, 492 testes): `EnrollmentChangesIT.clean:38` não apaga `account where email like '%.example'` por violar `account_link_account_id_fkey` (linhas de `account_link` deixadas por outro IT, provavelmente `BenefitListIT` ou a semente; dependente da ordem de execução). Local passou. Demais verificações verdes. Corrigir a limpeza no teste que deixa `account_link`.

### Correção da entrega
- Falha do CI em `EnrollmentChangesIT.clean` (FK `account_link_account_id_fkey`): o `SeedDataIT` deixava contas e vínculos do seed (`*.example`) depois de cada teste, e o `EnrollmentChangesIT` não conseguia apagar `account` quando rodava depois dele — arquivos: `backend/src/test/java/br/com/ctcore/db/SeedDataIT.java` (`cleanTeachers` agora também apaga `email_message`, `account_device`, sessões, convites, `account_link_unit`, `account_link` e `account`) — prova: `verify -Dit.test=SeedDataIT,EnrollmentChangesIT,BenefitListIT` (ordem que falhou no CI: BenefitListIT, SeedDataIT, EnrollmentChangesIT) → 26 testes verdes; `./backend/mvnw -f backend/pom.xml verify` → BUILD SUCCESS, 492 testes, 0 falhas.

### Falha do CI após a correção (commit 7ab16b9)
- `backend` falhou de novo no PR #305: `EnrollmentChangesIT` (4 erros: `changesPlan`, `changesClass`, `transfers`, `reachAndProfiles`) no `clean`, `delete from account where email like '%.example'` viola `account_link_account_id_fkey`. A limpeza do `SeedDataIT` não bastou: outro IT deixa `account_link` de contas `*.example`. Correção sugerida: o próprio `EnrollmentChangesIT.clean` apagar `account_link_unit` e `account_link` antes de `account` (ou helper comum de limpeza). Demais verificações verdes.
