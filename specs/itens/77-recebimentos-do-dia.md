# 77 — RF-COB-08 · Recebimentos do dia

Origem: issue #77 (Sprint 08); `docs/especificacao/10-cobrancas.md` (RF-COB-08, RN-COB-12, RN-COB-07). Branch: `item/77-rf-cob-08-recebimentos-do-dia`. Parte do que a #71 e a #75 deixaram: tabela `payment` (V39: `method`, `origin`, `paid_at`, `registered_by_account_id`, `status`), `Payments.register`, a prévia da geração (`GET /billing/generation-preview`, `ChargePreviewPage`, `useChargePreview`, handler MSW) como molde de rota e de tela por unidade. A tela do canvas (`RecebimentosDia.dc.html`) é inacessível ao agente: o layout segue os padrões existentes e o dono confere no PR.

## 1. Objetivo

O Administrador (e o Coordenador, nas suas unidades) vê, em Financeiro › Cobranças › Recebimentos do dia, tudo o que a unidade recebeu em um dia, somado por meio de pagamento e por pessoa que lançou, com a lista dos pagamentos. Serve para conferir o que entrou no balcão sem abertura nem fechamento de caixa (RN-COB-12).

Fora do escopo: relatório financeiro por período (Sprint 15 / `21`); exportação CSV; "Próximas cobranças e movimentações" do painel (`G:RF-FIN-02`, outro item); estorno e pagamentos online (Sprint 11; o desenho já aceita `origin` e lançador nulo); conferência de caixa (não existe, RN-COB-12).

## 2. Requisitos

- R1 (RF-COB-08): `GET /api/v1/billing/daily-receipts?unitId=&date=` devolve, para a unidade e o dia (AAAA-MM-DD, padrão hoje, no fuso da unidade): a data, o total em centavos, a quantidade de pagamentos, o total por meio, o total por pessoa que lançou e a lista dos pagamentos do dia.
- R2 (RF-COB-08, D1, D2): entram só pagamentos `CONFIRMED` cujo `paid_at`, no fuso da unidade, cai no dia; estornados ficam fora. Cada meio presente traz quantidade e total; cada lançador traz id, nome, quantidade e total; pagamentos sem lançador (portal, débito automático) formam uma linha própria com `registeredBy` nulo.
- R3 (RF-COB-08): cada pagamento da lista traz hora, aluno, referência (modalidade e mês, ou a descrição da avulsa), meio, lançador, valor e número do recibo, da hora mais recente para a mais antiga (desempate por id) (D4).
- R4 (entrada): `unitId` ausente ou malformado, `date` malformada ou posterior a hoje (fuso da unidade): 400 com o campo (D5).
- R5 (permissão, D3): exige `COLLECTIONS` com algum acesso; Professor, Aluno e Responsável 403; sem sessão 401; unidade fora do alcance ou de outra organização devolve a resposta vazia (zeros e listas vazias), como a prévia.
- R6 (isolamento): a consulta filtra por `organization_id` e pela unidade, e o RLS impede ler pagamento de outra organização mesmo sem o filtro.
- R7 (contrato): `docs/openapi.yaml` ganha a rota e os schemas; `OpenApiContractTest` e `ContractRoutesTest` passam.
- R8 (tela): `/cobrancas/recebimentos` (link "Recebimentos do dia" em `/cobrancas`) mostra o total e a quantidade do dia, a tabela por meio, a tabela por pessoa que lançou e a lista de pagamentos, com seletor de unidade (quando "todas"), seletor de data (padrão hoje, máximo hoje) e botão de voltar às cobranças.
- R9 (estados e qualidade, RNF): a tela tem carregando, vazio ("nenhum recebimento neste dia"), erro (com tentar de novo) e sem permissão; funciona a partir de 360 px, passa no axe nos temas claro e escuro, usa textos só de `pt-BR.json` e valores, datas e horas pelos formatadores de `lib/format`.
- R10 (contrato do front): o handler MSW de `GET /billing/daily-receipts` reflete o OpenAPI (200, 400, 403) e calcula a resposta a partir dos pagamentos do simulado.

## 3. Critérios de aceite

- CA1 (R1, R2): dados 3 pagamentos no dia (dinheiro 10000 e 5000 lançados pelo Administrador, Pix na chave 20000 pelo Coordenador), quando o Administrador consulta o dia, então total 35000, quantidade 3, por meio `CASH` 2/15000 e `PIX_KEY` 1/20000, por lançador Administrador 2/15000 e Coordenador 1/20000.
- CA2 (R2): dado um pagamento estornado, um de ontem e um de amanhã no fuso da unidade (inclusive às 23h30 locais, que em UTC cai no dia seguinte), quando consulta hoje, então só os confirmados do dia local entram.
- CA3 (R2): dado pagamento com `origin = PORTAL` e lançador nulo, quando consulta, então ele conta no total e no meio, e aparece como uma linha de lançador com `registeredBy` nulo, sem erro.
- CA4 (R3): dados vários pagamentos, quando consulta, então a lista traz hora, aluno, referência ("Jiu-Jitsu · nov/2026" ou a descrição da avulsa), meio, lançador, valor e número do recibo, do mais recente ao mais antigo.
- CA5 (R4): dado `unitId` ausente ou "x", `date` "31/12" ou de amanhã, quando consulta, então 400 com o campo; sem `date`, usa hoje.
- CA6 (R5): dado Professor, Aluno ou Responsável, então 403; sem sessão 401; Coordenador na sua unidade e Administrador, 200; unidade fora do alcance ou de outra organização, 200 vazio.
- CA7 (R6): dada a organização B, quando consulta a unidade da A, então vazio; e a consulta direta de `payment` pelo papel de execução da B, sem filtro, não devolve linha da A (RLS).
- CA8 (R7): `OpenApiContractTest` e `ContractRoutesTest` passam com a rota nova.
- CA9 (R8): dado Administrador em `/cobrancas`, quando clica "Recebimentos do dia", então abre `/cobrancas/recebimentos` com total, quantidade, tabela por meio, tabela por lançador e lista; dado "todas as unidades", então pede a unidade antes; ao trocar a data, a tela recarrega; "Voltar às cobranças" volta.
- CA10 (R9): dado resposta lenta, dia sem pagamento, erro e 403, quando a tela abre, então mostra, cada um, carregando, "nenhum recebimento neste dia", erro com "Tentar de novo" e sem permissão.
- CA11 (R9): dado 360 px e os temas claro e escuro, quando a tela está com dados, então o axe não aponta violação e a página não rola na horizontal (tabelas em contêiner com rolagem própria).
- CA12 (R10): o teste do MSW cobre 200 com totais corretos, 400 (sem `unitId`, data futura) e 403, e os campos batem com `docs/openapi.yaml`.

## 4. Plano técnico

- Dados: não muda. Só leitura de `payment`, `receipt`, `charge`, `student`, `account`, `unit` (V39, V6). O dia é `(p.paid_at at time zone u.timezone)::date`. Sem migração, sem seed (nenhuma tabela nova).
- API: `GET /billing/daily-receipts` (`unitId` obrigatório, `date` opcional). Resposta `DailyReceipts`: `date`, `totalCents`, `count`, `byMethod[{method,count,totalCents}]`, `byRegistrar[{registeredBy{id,name}|null,count,totalCents}]`, `payments[{id,paidAt,student{id,name},reference,method,registeredBy{id,name}|null,amountCents,receiptNumber}]`. Erros 400, 401, 403. Só lê: sem CSRF e sem auditoria (nada altera dado). Nome do lançador: `account.name`, com o e-mail se o nome for vazio (equipe da própria organização).
- Interface: página nova `DailyReceiptsPage` em `/cobrancas/recebimentos`, no molde da `ChargePreviewPage` (unidade, estados, `Button asChild` de voltar); data com `<input type="date">` (max hoje); resumo, tabela por meio, tabela por lançador e lista, em contêiner com rolagem própria a 360 px. Link em `ChargesPage` ao lado de "Prévia da geração".
- Segurança: permissão conferida no servidor (`Permissions.effective` e `grant` por unidade); parâmetros nomeados; sem dado de cartão, CPF nem e-mail de aluno na resposta; nomes exibidos só como texto; a organização vem da sessão.
- Arquivos: a alterar (vistos) `docs/openapi.yaml` (rota e schemas `DailyReceipts*`), `frontend/src/App.tsx` (rota, perto de `/cobrancas/previa`), `frontend/src/features/charges/ChargesPage.tsx` (link), `frontend/src/features/charges/types.ts` (tipos), `frontend/src/mocks/handlers.ts` (handler novo ao lado do da prévia, com `studentAccess` e as cobranças pagas do simulado), `frontend/src/i18n/pt-BR.json` (`charges.daily.*`). A criar: `backend/src/main/java/br/com/ctcore/payment/DailyReceipts.java` (consulta e regra), `payment/DailyReceiptsController.java` (rota; só delega), `backend/src/test/java/br/com/ctcore/payment/DailyReceiptsIT.java` e `DailyReceiptsIsolationIT.java` (ao lado de `PaymentIT` e `PaymentIsolationIT`), `frontend/src/features/charges/DailyReceiptsPage.tsx`, `useDailyReceipts.ts`, `DailyReceiptsPage.test.tsx`, `frontend/src/mocks/dailyReceipts.msw.test.ts`.
- Reuso: `OrganizationTransactions`, `Permissions.effective/grant`, `ApiValidationException`, a conversão de fuso de `Charges.today`, `BillingFixtures` e `OrganizationBase` nos testes; `useResource`, `useUnitScope`, `usePermissions`, `states/*`, `formatCurrency`/`formatDate`, `payments.method.*` de `pt-BR.json`, `ManagementLayout`.
- Riscos: (1) JaCoCo exige 100% no pacote `payment`: todo ramo de `DailyReceipts` (data padrão, data futura, unidade fora do alcance, lançador nulo) precisa de teste; (2) fuso: pagamento perto da meia-noite local, coberto no CA2; (3) lista sem paginação (D4); (4) `account` não tem RLS por organização: o join parte de `payment.registered_by_account_id` já filtrado pela organização, sem listar contas.

## 5. Tarefas

- [x] T1: `DailyReceipts` (consulta do dia no fuso da unidade, totais por meio e por lançador, lista, validações, permissão) e `DailyReceiptsController` — cobre CA1, CA2, CA3, CA4, CA5, CA6 — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test=DailyReceiptsIT -Dtest=NoTest -Dsurefire.failIfNoSpecifiedTests=false`
- [x] T2: OpenAPI da rota e dos schemas, teste de isolamento entre organizações e perfis com RLS — cobre CA7, CA8 — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test=DailyReceiptsIsolationIT -Dtest=NoTest -Dsurefire.failIfNoSpecifiedTests=false` e `./backend/mvnw -f backend/pom.xml test -Dtest='OpenApiContractTest,ContractRoutesTest'`
- [x] T3: handler MSW de `GET /billing/daily-receipts`, tipos e teste de contrato — cobre CA12 — prova: `cd frontend && npx vitest run src/mocks/dailyReceipts.msw.test.ts --maxWorkers=2`
- [x] T4: `DailyReceiptsPage` com `useDailyReceipts`, rota em `App.tsx`, textos `charges.daily.*` e os quatro estados — cobre CA9, CA10 — prova: `cd frontend && npx vitest run src/features/charges/DailyReceiptsPage.test.tsx --maxWorkers=2`
- [x] T5: link em `ChargesPage`, 360 px e axe nos dois temas, conferência completa — cobre CA11 — prova: `cd frontend && npm run lint && npm run typecheck && npx vitest run src/features/charges src/mocks --maxWorkers=2`

## 6. Decisões

- D1: pagamento estornado (`REFUNDED`) não entra nos recebimentos do dia, porque o dinheiro foi devolvido. O estorno só existe na Sprint 11; o filtro já fica pronto. Alternativa: linha negativa, descartada por não haver regra que peça.
- D2: pagamento sem lançador (portal, débito automático) entra no total, no meio e numa linha de lançador com `registeredBy` nulo, que a tela mostra como "Pagamento online". Sem essa linha a soma por lançador não bateria com o total. Alternativa: só pagamentos de balcão, descartada porque o total do dia ficaria menor que o recebido.
- D3 (muda o comportamento): quem tem `COLLECTIONS` com qualquer acesso (leitura inclusive) vê os recebimentos do dia das suas unidades, como a prévia; a especificação não restringe esta tela ao acesso completo. Alternativa: exigir `FULL`, descartada por ser tela de consulta; trocar é uma linha se o dono preferir.
- D4: a lista do dia vem inteira, sem paginação, por hora. Alternativa: paginar, descartada por custo sem ganho para um dia de uma unidade; se um dia passar de alguns milhares de linhas, vira item de paginação.
- D5: "hoje" e a hora dos pagamentos são os do fuso da unidade (como em `Charges`); data futura é 400.
- Suposição: o canvas `RecebimentosDia.dc.html` não foi lido; se tiver exportação, gráfico ou filtro que a issue não cita, entra como item novo.
- Suposição: não há notas de versão no repositório; se existirem fora dele, a linha "Recebimentos do dia" entra lá na entrega.

## Construção

- T1 — arquivos: `backend/src/main/java/br/com/ctcore/payment/DailyReceipts.java`, `DailyReceiptsController.java`, `backend/src/test/java/br/com/ctcore/payment/DailyReceiptsIT.java` — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test=DailyReceiptsIT -Dtest=NoTest -Dsurefire.failIfNoSpecifiedTests=false` → 5 testes, 0 falhas, BUILD SUCCESS
- T2 — arquivos: `docs/openapi.yaml` (rota `/billing/daily-receipts`, schemas `DailyReceipts` e `DailyReceiptsPerson`), `backend/src/test/java/br/com/ctcore/payment/DailyReceiptsIsolationIT.java` — prova: `verify -Dit.test=DailyReceiptsIsolationIT ...` → 1 teste, 0 falhas; `test -Dtest='OpenApiContractTest,ContractRoutesTest'` → 7 testes, 0 falhas
- T3 — arquivos: `frontend/src/mocks/handlers.ts` (handler novo; recibos do simulado guardam o lançador em `by`), `frontend/src/features/charges/types.ts`, `frontend/src/mocks/dailyReceipts.msw.test.ts` — prova: `npx vitest run src/mocks/dailyReceipts.msw.test.ts --maxWorkers=2` → 3 testes passam
- T4 — arquivos: `frontend/src/features/charges/DailyReceiptsPage.tsx`, `useDailyReceipts.ts`, `DailyReceiptsPage.test.tsx`, `frontend/src/App.tsx`, `frontend/src/i18n/pt-BR.json` (`charges.daily.*`) — prova: `npx vitest run src/features/charges/DailyReceiptsPage.test.tsx --maxWorkers=2` → 5 testes passam
- T5 — arquivos: `frontend/src/features/charges/ChargesPage.tsx`, `ChargesPage.test.tsx` (teste do link) — prova: `npm run lint && npm run typecheck && npx vitest run src/features/charges src/mocks --maxWorkers=2` → lint e tipos limpos, 42 arquivos e 275 testes passam (axe nos dois temas a 360 px no `DailyReceiptsPage.test.tsx`)

Regressão:
- `npm run build && npm run test:cov` → build ok; 124 arquivos, 1075 testes passam; cobertura acima do mínimo.
- `npm run e2e` → 35 passam.
- `./backend/mvnw -f backend/pom.xml verify` → 638 testes, 1 falha: `ChargeRenegotiationIT.invalid` (ver "Não é do item"); o resto passa. Como a falha interrompeu o `verify` antes do `jacoco:check`, rodei `jacoco:report@report jacoco:check@check` sobre o `jacoco.exec` dessa execução → BUILD SUCCESS (80% e 100% nos pacotes protegidos, `payment` incluído).

Desvios: nenhum no plano. A data da tela usa `todayIso` de `ChangeDueDialog.tsx` (fuso de São Paulo, como o resto das cobranças) em vez de um helper novo.

Não é do item: `ChargeRenegotiationIT.invalid` falhou na execução noturna (relógio em 2026-10-23 UTC); causa provável, não confirmada: o teste monta `LocalDate.now()` no fuso da JVM e o servidor valida o "hoje" no fuso da unidade/UTC (o relógio já estava em 2026-10-23 UTC). O código dele não foi tocado por este item (falha anterior, não corrigida).

## Verificação

### Rodada 1 — OK — 2026-10-22, estado c89ba8f1c7fbe6ba07336dab35b66ee3c15b1214

| Critério | Status | Evidência |
|---|---|---|
| CA1-CA6 | PASSOU | `DailyReceiptsIT` 5 testes, 0 falhas |
| CA7 | PASSOU | `DailyReceiptsIsolationIT` 1 teste, 0 falhas |
| CA8 | PASSOU | `OpenApiContractTest` 4 e `ContractRoutesTest` 3, 0 falhas |
| CA9, CA10, CA11 | PASSOU | `DailyReceiptsPage.test.tsx` (inclui axe nos dois temas a 360 px) e `ChargesPage.test.tsx` passam |
| CA12 | PASSOU | `dailyReceipts.msw.test.ts` passa |

Comandos: `mvnw verify -Dit.test='DailyReceipts*IT' -Dtest='OpenApiContractTest,ContractRoutesTest'` → BUILD SUCCESS (jacoco check incluso); `npm run lint && npm run typecheck && npx vitest run src/features/charges src/mocks --maxWorkers=2` → 42 arquivos, 275 testes passam. Regressão completa (verify, test:cov, e2e) não repetida; vale o registrado na Construção.
Achados:
- sugestão, código, `DailyReceipts.java` — a consulta de fuso lê `unit` sem filtro explícito de organização (o RLS cobre); aceitável.

## Entrega

- O que muda: relatório de recebimentos do dia (RF-COB-08). Rota `GET /billing/daily-receipts` no back-end (com teste de isolamento entre organizações), schemas no OpenAPI, handler MSW, tela de recebimentos com link a partir de Cobranças e textos em `charges.daily.*`.
- Verificação: rodada 1 OK. `DailyReceiptsIT` (5), `DailyReceiptsIsolationIT` (1), contratos OpenAPI e de rotas (7); front com 275 testes de cobranças e mocks passando, com axe a 360 px nos dois temas.
- Regressão completa: `npm run build`, `test:cov` e `e2e` passam. `mvnw verify` reprovou só `ChargeRenegotiationIT.invalid`, falha anterior ao item (execução noturna, relógio em 2026-10-23 UTC), não corrigida aqui; o `jacoco:check` rodado à mão sobre o `jacoco.exec` passa, com 100% nos pacotes protegidos.
- Decisão: a data da tela usa `todayIso` de `ChangeDueDialog.tsx` (fuso de São Paulo), como o resto das cobranças, em vez de helper novo.
- Limitação: `ChargeRenegotiationIT.invalid` continua vermelho na base até ser tratado em item próprio.
