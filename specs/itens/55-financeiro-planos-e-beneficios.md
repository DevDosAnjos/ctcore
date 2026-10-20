# 55 — RF-PLA-01 · Financeiro › Planos e benefícios

Origem: issue #55 (Sprint 06); `docs/especificacao/07-planos-e-beneficios.md` (RF-PLA-01, "Dados", RN-PLA-01 a RN-PLA-03, RN-PLA-06), `docs/especificacao/02-contas-e-acesso.md` (linhas Planos e Benefícios da tabela de permissões). Branch: `item/55-planos-e-beneficios`. Segue o padrão de #46 e #47 (turmas) para rota, alcance por unidade e tela; #56 (tabela) e #57 (janela) constroem sobre o modelo criado aqui.

## 1. Objetivo

A gestão abre Financeiro › Planos e benefícios e vê dois cartões, Planos e Benefícios, cada um com o seu resumo da unidade escolhida (ou de todas as unidades do alcance). Este item cria o modelo de dados do plano (tabela `plan`) e a rota de resumo; é a base das tabelas e janelas dos itens seguintes.

Fora do escopo: tabela de planos e filtro (#56), janela de criar e editar plano e histórico de valores (#57), qualquer dado de benefício e matrícula (Sprint 07; o resumo de Benefícios vem zerado até lá), tela de benefícios (RF-BEN-01 a 03, Sprint 07), cobrança.

## 2. Requisitos

- R1 (RF-PLA-01, D1): a tela `/planos` mostra dois cartões, Planos e Benefícios, cada um com título e resumo.
- R2 (RF-PLA-01, D2): `GET /api/v1/plans/summary` devolve `plans` (`active`, `inactive`, `studentsUsing`, `minValueCents` e `maxValueCents` dos planos ativos, nulos sem plano ativo) e `benefits` (`active`, `high`); `studentsUsing`, `benefits.active` e `benefits.high` são 0 até a Sprint 07 (D3).
- R3 (RN-PLA-01, RN-PLA-02, RN-PLA-03, RN-PLA-06, D4): o banco ganha a tabela `plan` (unidade, modalidade, nome, valor em centavos, frequência semanal informativa, vale em outras unidades, situação `ACTIVE` ou `INACTIVE`), com RLS por organização; um plano por modalidade e nome na unidade entre os ativos; valor inteiro em centavos e maior que zero; nenhuma coluna de controle de aulas feitas.
- R4 (escopo, D5): o resumo aceita `unitId` opcional; Coordenador vê todas as unidades ou a filtrada; Administrador vê só as unidades dele (`unitId` fora do alcance dá resumo zerado, não erro); organização sempre a da sessão.
- R5 (permissão, D6): `PLANS` conferido no servidor: Coordenador e Administrador acessam; Professor, Aluno e Responsável recebem 403; sem sessão 401. O front mostra "sem permissão" ao Professor, e o menu já oculta Financeiro para ele.
- R6 (isolamento): consulta com filtro de organização no código e RLS no banco; teste de isolamento entre organizações e entre perfis.
- R7 (tela): quatro estados (carregando, vazio, erro com "Tentar de novo", sem permissão), 360 px, axe nos dois temas, textos em `pt-BR.json`; valores em reais formatados a partir dos centavos.
- R8 (contrato e dados): `docs/openapi.yaml` e handlers MSW refletem a rota; o seed ganha planos fictícios.
- R9 (menu, D1): o item "Planos" do menu (`/planos`) passa a disponível; "Benefícios" continua indisponível.

## 3. Critérios de aceite

- CA1 (R2, R3): dado organização com 3 planos ativos (R$ 150,00, R$ 200,00, R$ 300,00) e 1 desativado, quando o Coordenador faz `GET /plans/summary`, então 200 com `plans.active` 3, `inactive` 1, `minValueCents` 15000, `maxValueCents` 30000, `studentsUsing` 0 e `benefits` com 0 e 0.
- CA2 (R2): dado organização sem plano, então `active` 0, `inactive` 0 e `minValueCents`/`maxValueCents` nulos.
- CA3 (R4): dado Administrador com a unidade A e planos em A e B, quando consulta sem filtro, então conta só os de A; com `unitId` de B, resumo zerado; Coordenador com `unitId=B` vê só os de B.
- CA4 (R5): dado Professor, Aluno ou Responsável, quando chama a rota, então 403; sem sessão, 401.
- CA5 (R6): dado plano da organização B, quando o Coordenador de A consulta, então não entra na conta; com `ctcore_app` e contexto de A, a consulta direta a `plan` não enxerga a linha de B.
- CA6 (R3): dado o esquema, então `value_cents <= 0` é recusado pelo banco, dois planos ativos com a mesma unidade, modalidade e nome são recusados, um desativado com o mesmo nome é aceito, e nenhuma coluna menciona presença ou frequência de aula feita.
- CA7 (R1, R7): dado resumo com dados, quando a gestão abre `/planos`, então vê os cartões "Planos" (ativos, desativados, alunos usando, faixa de valores em reais) e "Benefícios" (ativos, altos); sem plano ativo a faixa mostra o texto de vazio.
- CA8 (R7): dado a tela em 360 px, então sem rolagem horizontal e axe sem violações nos dois temas; carregando mostra o estado de carregamento; erro mostra "Tentar de novo" e recarrega; Professor vê a tela de sem permissão; resumo todo zerado mostra o estado de vazio com a explicação.
- CA9 (R4, R7): dado Coordenador com várias unidades, quando troca o seletor de unidade, então o resumo é pedido de novo com o `unitId` escolhido.
- CA10 (R8, R9): `OpenApiContractTest` e `ContractRoutesTest` passam com a rota; teste MSW cobre a rota no formato do servidor; `SeedDataIT` passa; o menu leva "Planos" a `/planos` e "Benefícios" segue sem link.

## 4. Plano técnico

- Dados: migração `V30__plan.sql` (depois de `V29`), com RLS `organization_scope` como em `V24`: `plan` (`id`, `organization_id`, `unit_id`, `modality_id`, `name` 2 a 60 (`00-visao-geral`: nome de plano 60), `name_key` por `Modalities.normalize`, `value_cents bigint check (> 0)`, `weekly_frequency` nulo (2, 3 ou nulo = livre; `check` em 2 e 3), `valid_other_units boolean default false`, `status` com `check` em `ACTIVE`/`INACTIVE`, `created_at`, `unique (organization_id, id)`, chaves estrangeiras compostas para `unit` e `modality` como em `training_class`; índice único parcial `(unit_id, modality_id, name_key)` onde `status = 'ACTIVE'`). Histórico de valores fica em #57. Seed `infra/seed/13-planos.sql` (`on conflict do nothing`), com ajuste de `SeedDataIT` (limpar `plan` antes de apagar unidade e modalidade).
- API (cookie como as demais): `GET /api/v1/plans/summary?unitId=` (200 `PlanSummary`; 401, 403). Entra em `docs/openapi.yaml` no mesmo PR. Pontos únicos fixos, para a Sprint 07 trocar: `studentsUsing(scope)` devolve 0 e `BenefitSummary benefits(scope)` devolve zeros.
- Back-end: criar pacote `br.com.ctcore.plan` com `Plans.java` (consulta, `requireAccess` por `Permission.PLANS`, alcance do Administrador como em `TrainingClasses`, `OrganizationTransactions.execute`) e `PlanController.java`. Rota somente leitura: não grava auditoria.
- Interface: criar `frontend/src/features/plans/PlansPage.tsx`, `usePlans.ts` e teste; alterar `App.tsx` (`/planos` dentro de `RequireArea` Coordenador e Administrador), `menuCatalog.ts` (`plans` com `available = true`), `mocks/handlers.ts` (+ teste MSW) e `i18n/pt-BR.json`. Os botões "Ver planos" (#56) e "Ver benefícios" (Sprint 07) aparecem desativados nos cartões. Seguir o seletor de unidade e o padrão de `ClassesPage`.
- Segurança: organização, perfil e alcance vêm do principal; `PLANS` conferido no servidor; parâmetros ligados; nada de dado pessoal na resposta; Professor nunca vê plano (tabela de permissões).
- Arquivos: a alterar (vistos): `frontend/src/features/permissions/menuCatalog.ts`, `frontend/src/App.tsx`, `frontend/src/mocks/handlers.ts`, `frontend/src/i18n/pt-BR.json`, `docs/openapi.yaml`, `SeedDataIT`; a criar: `V30__plan.sql`, `plan/Plans.java`, `plan/PlanController.java`, `PlanSummaryIT.java`, `infra/seed/13-planos.sql`, `PlansPage.tsx`, `usePlans.ts`, `PlansPage.test.tsx`, `plans.msw.test.ts`.
- Reuso: `TrainingClasses` como modelo de alcance e `requireAccess`, `Permissions`, `OrganizationTransactions`, `Modalities.normalize`, `Card`, estados padrão, formato de moeda de #9, `useClasses.ts` como modelo de consulta com unidade.
- Riscos: (1) #57 pode pedir coluna a mais em `plan`; a mudança é aditiva em migração nova. (2) Com `studentsUsing` e `benefits` fixos, os números só ficam reais na Sprint 07; o teste prova o formato e o ponto único. (3) O canvas `Planos.dc.html` não está acessível: o dono confere o layout no PR.

## 5. Tarefas

Uma parte só: dados (T1), servidor (T2), contrato (T3), tela (T4), regressão (T5).

- [x] T1: migração `V30__plan.sql`, seed `13-planos.sql` e ajuste de `SeedDataIT` — cobre CA6 — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test='PlanSummaryIT,SeedDataIT'`.
- [x] T2: `Plans` e `PlanController` com `GET /plans/summary`, alcance por unidade, pontos únicos e testes de integração (resumo, vazio, Administrador por unidade, perfis, isolamento e RLS) — cobre CA1, CA2, CA3, CA4, CA5 — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test=PlanSummaryIT`.
- [x] T3: OpenAPI (`/plans/summary`, `PlanSummary`) e handlers MSW com teste — cobre CA10 — prova: `./backend/mvnw -f backend/pom.xml test -Dtest='OpenApiContractTest,ContractRoutesTest'` e `npx vitest run src/mocks` em `frontend/`.
- [x] T4: `PlansPage`, `usePlans`, rota, menu, textos pt-BR, quatro estados, 360 px e axe — cobre CA7, CA8, CA9, CA10 (menu) — prova: `npx vitest run src/features/plans src/features/permissions` em `frontend/`.
- [x] T5: regressão completa (não cobre critério: confere cobertura e os 100% dos pacotes exigidos) — prova: `./backend/mvnw -f backend/pom.xml verify` e, em `frontend/`, `npm run lint && npm run typecheck && npm run build && npm run test:cov && npm run e2e`.

## 6. Decisões

- D1: a tela é uma só em `/planos` (a página "Planos e benefícios" do canvas, com os dois cartões); o item "Planos" do menu a abre. "Benefícios" segue indisponível no menu até a Sprint 07. Alternativa: duas rotas; descartada porque o card pede uma tela com dois cartões.
- D2: o resumo vem de uma rota própria `GET /plans/summary`, e não de contar a lista, porque #56 pagina e filtra a tabela. Alternativa: derivar do front; descartada por contar errado com paginação.
- D3: benefícios e alunos usando não existem até a Sprint 07 (matrícula); a API devolve zero em pontos únicos e a tela mostra os cartões completos. Alternativa: ocultar os números; descartada porque o card pede resumo em cada cartão.
- D4: este item, o primeiro da sequência, cria a tabela `plan` completa (sem histórico de valores, que é de #57), para #56 e #57 não repetirem migração. Alternativa: criar a tabela só em #56; descartada porque o resumo já precisa dela.
- D5 (muda o comportamento): o resumo respeita o seletor de unidade e o alcance do Administrador, como as demais telas de gestão; `unitId` fora do alcance dá resumo zerado. Alternativa: 404; descartada por não expor se a unidade existe.
- D6 (muda o comportamento): a permissão `PLANS` (Coordenador e Administrador por unidade; Professor sem acesso) vale para planos e para o resumo de benefícios, pois a tabela de permissões dá o mesmo alcance a "Benefícios". Alternativa: exigir `BENEFITS` para a parte de benefícios; descartada porque os perfis são iguais e o resumo é uma resposta só.
- D7: o item não grava auditoria porque só lê; a gravação entra em #57. Notas de versão: não se aplica, porque ainda não existe tela de Novidades (Sprint 21) e o item não entrega ação visível ao aluno.
- D8: o canvas `Planos.dc.html` não está acessível; a tela segue os padrões do projeto e o dono confere o layout no PR (como D8 de #46 e #47).
- Suposição: "resumo" dos cartões é contagem e faixa de valores; se o canvas mostrar outro número (por exemplo receita mensal estimada), ajusta-se no ponto único da rota.

## Construção

- T1 — arquivos: `backend/src/main/resources/db/migration/V30__plan.sql`, `infra/seed/13-planos.sql`, `SeedDataIT` (limpa `plan` e testa o seed) — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test='PlanSummaryIT,SeedDataIT'` → 18 testes, 0 falhas.
- T2 — arquivos: `backend/src/main/java/br/com/ctcore/plan/Plans.java`, `PlanController.java`, `backend/src/test/java/br/com/ctcore/plan/PlanSummaryIT.java` (4 testes: resumo e vazio, alcance e perfis, isolamento e RLS, regras do esquema) — prova: mesmo comando de T1 → verde.
- T3 — arquivos: `docs/openapi.yaml` (`/plans/summary`, `PlanSummary`), `frontend/src/mocks/handlers.ts` (`mockPlans` e handler), `frontend/src/mocks/plans.msw.test.ts` — prova: `./backend/mvnw -f backend/pom.xml test -Dtest='OpenApiContractTest,ContractRoutesTest'` → 7 testes verdes; `npx vitest run src/mocks` → 19 arquivos, 116 testes verdes (antes do teste novo; o teste novo passa na rodada completa).
- T4 — arquivos: `frontend/src/features/plans/{PlansPage.tsx,usePlans.ts,PlansPage.test.tsx}`, `App.tsx`, `menuCatalog.ts` (`plans` disponível), `i18n/pt-BR.json` (`plans`), `buildMenu.test.ts` (grupo Financeiro com Planos para Coordenador e Administrador) — prova: `npx vitest run src/features/plans src/features/permissions` → 22 testes verdes.
- T5 — regressão abaixo.

Regressão: `./backend/mvnw -f backend/pom.xml verify` → 134 testes rápidos e 404 de integração, 0 falhas, JaCoCo ok, BUILD SUCCESS. Em `frontend/`: `npm run lint` e `npm run typecheck` sem erro; `npm run build` ok; `npm run test:cov` → 79 arquivos, 709 testes verdes, linhas 97,01%; `npm run e2e` → 28 passaram. Nada piorou.
Desvios: a `plan` não tem chave estrangeira composta para `modality` (como em `training_class`, `modality` é global: chave simples por `id`). O botão "Ver planos"/"Ver benefícios" é desativado, como o plano técnico pede. Professor vê a tela padrão de área indisponível (`RequireArea`), não o `ForbiddenState`; o `ForbiddenState` cobre o 403 vindo do servidor.
Não é do item: nenhuma mudança fora do escopo.

## Verificação

### Rodada 1 — OK — 2026-10-20, estado c6923d41fe3321a1a80507bad19aaadb12b4e765

| Critério | Status | Evidência |
|---|---|---|
| CA1, CA2 | PASSOU | `PlanSummaryIT.summary` (verify: 404 ITs, 0 falhas) |
| CA3, CA4 | PASSOU | `PlanSummaryIT.permissions` (Administrador por unidade, 403 para Professor/Aluno/Responsável, 401 anônimo) |
| CA5 | PASSOU | `PlanSummaryIT.isolation` (API e `ctcore_app` com RLS, inclusive insert cruzado) |
| CA6 | PASSOU | `PlanSummaryIT.schema` + `V30__plan.sql` lida |
| CA7, CA8, CA9 | PASSOU | `PlansPage.test.tsx` (estados, 360 px, axe nos dois temas, troca de unidade) |
| CA10 | PASSOU | `OpenApiContractTest`/`ContractRoutesTest` dentro do `verify`; `plans.msw.test.ts`; `SeedDataIT`; menu em `buildMenu.test.ts` e `PlansPage.test.tsx` |

Comandos: `./backend/mvnw -f backend/pom.xml verify` → 404 ITs, 0 falhas, BUILD SUCCESS; em `frontend/`: `npm run lint`, `typecheck`, `build` ok; `test:cov` → 79 arquivos, 709 testes, linhas 97,01%; `npm run e2e` → 28 passaram.
Achados:
- sugestão, código, `Plans.java` — o desvio (sem FK composta para `modality`) está registrado e coerente com `training_class`.

## Entrega

- Tela Financeiro › Planos e benefícios para Coordenador e Administrador, com os quatro estados, 360 px e axe nos dois temas; rota `GET /plans/summary` (OpenAPI e MSW alinhados) e tabela `plan` com RLS (V30) e seed `13-planos.sql`.
- Verificação: `verify` do back-end (404 ITs, 0 falhas); front com lint, typecheck, build, `test:cov` (97,01%) e `e2e` (28) verdes.
- Decisões: resumo por rota própria, respeitando unidade e alcance; permissão `PLANS` vale também para benefícios; sem auditoria (só leitura).
- Limitações: canvas `Planos.dc.html` inacessível, o dono confere o layout no PR; benefícios e alunos usando valem zero até a Sprint 07; `plan` sem FK composta para `modality` (global).

Closes #55
