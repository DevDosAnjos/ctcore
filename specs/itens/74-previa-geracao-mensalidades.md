# 74 — RF-COB-03 · Prévia da geração de mensalidades

Origem: issue #74 (Sprint 08); `docs/especificacao/10-cobrancas.md` (RF-COB-03, RN-COB-02, RN-COB-04, RN-COB-05, RN-COB-10). Branch: `item/74-rf-cob-03-previa-geracao`. Parte do que a #71 já deixou: `MonthlyGeneration.preview` e `GET /billing/generation-preview` (só leitura, D7 da #71), o ponteiro `enrollment_modality.next_charge_ref/next_charge_due_on` (V39), `ChargeActions.cancelLocked` (#73), `GrantBenefitDialog` (#65) e o mock `/billing/generation-preview` que hoje devolve lista vazia. Telas do canvas (`Fechamento.dc.html`, `EmailCobranca.dc.html`) inacessíveis ao agente: o dono confere no PR.

Dividido em **duas partes** (sete tarefas); a numeração é única. A Parte 2 só começa com a 1 concluída. Commit e PR são um só (`#74: RF-COB-03 · Prévia da geração de mensalidades`, `Closes #74`), no fim da Parte 2.

| Parte | Entrega | Requisitos | Tarefas |
| --- | --- | --- | --- |
| 1 | Ajustar antes de gerar (trocar vencimento, cancelar), prévia marca o que dá para ajustar, contrato | R1 a R8 | T1 a T3 |
| 2 | Tela Financeiro › Cobranças › Prévia, janelas de ajuste, aplicar benefício, MSW, acessibilidade | R9 a R15 | T4 a T7 |

## 1. Objetivo

O Administrador (e o Coordenador, nas suas unidades) abre a prévia da geração, vê as próximas mensalidades por dia de vencimento, o total previsto, a diferença para o mês anterior e quem não será cobrado e por quê, e ajusta o que for preciso antes de a tarefa diária gerar: trocar o vencimento, cancelar a mensalidade do mês ou aplicar um benefício ao aluno. O cálculo da prévia já existe no servidor (#71); este item entrega a tela e as ações de ajuste (RN-COB-02), que a #71 deixou para cá.

Fora do escopo: e-mail "Cobrança gerada" (`EmailCobranca.dc.html`; é a RN-COM-11 e entra com os e-mails automáticos da #78, D1); geração adiantada e cobrança avulsa (#76); registrar pagamento (#75); motivos de "não cobrado" por modalidade bloqueada ou com encerramento solicitado (esses estados ainda não existem, chegam na Sprint 10 e na Sprint 12, D2); mudar a regra de valor (RN-COB-03).

## 2. Requisitos

### Parte 1 — Servidor

- R1 (RN-COB-02, D3): `PATCH /api/v1/students/{id}/modalities/{modalityRowId}/next-charge` com `dueOn` (AAAA-MM-DD) troca o vencimento da próxima mensalidade ainda não gerada daquela modalidade. A data não pode ser anterior a hoje (fuso da unidade) nem passar do último dia do mês de referência; fora disso, 400. Vale só para aquela mensalidade: a seguinte volta ao dia de vencimento do aluno. Responde 200 com a linha ajustada (`dueOn`, `referenceMonth`).
- R2 (RN-COB-02, RN-COB-10, D4): `POST /api/v1/students/{id}/modalities/{modalityRowId}/next-charge/cancel` com `reason` (obrigatório, sem espaços nas pontas, até 200 caracteres, a mesma regra de `ChargeActions.reason`) cancela a próxima mensalidade: grava a cobrança do mês como `CANCELED` com o motivo e avança o ponteiro. Assim a geração não a recria (RN-COB-05, índice único vale para cancelada) e o relatório de "cancelado no período" a enxerga. Responde 200 com o id da cobrança cancelada.
- R3 (RN-COB-04): modalidade que não gera mensalidade (valor final zero por bolsa de 100%, ou não `ACTIVE`) não aceita R1 nem R2: 422. Modalidade inexistente, de outro aluno, de outra organização ou de unidade fora do alcance: 404.
- R4 (permissão): R1 e R2 exigem `COLLECTIONS` com acesso `FULL` na unidade da modalidade (Coordenador e Administrador nas suas unidades); Professor, Aluno e Responsável 403; sem sessão 401. A prévia continua exigindo `COLLECTIONS` com leitura.
- R5 (RNF-12, RNF-13): R1 e R2 travam a linha da modalidade (`for update`), são tudo ou nada e não duplicam: duas chamadas simultâneas de cancelar resultam em uma cobrança cancelada e uma resposta 422/409 sem erro 500; a mesma data enviada duas vezes não muda nada na segunda.
- R6 (auditoria): trocar o vencimento grava `charge.next_due_changed` (ator, perfil, modalidade, de, para); cancelar grava `charge.canceled` (via `cancelLocked`, com motivo) e `charge.generation_adjusted` com a modalidade e o mês de referência. Recusas não auditam.
- R7 (RF-COB-03): cada linha da prévia ganha `adjustable` (verdadeiro só para a primeira mensalidade ainda não gerada de cada modalidade, a que o ponteiro aponta; as demais linhas do mesmo aluno, quando `days` alcança mais de um mês, vêm falsas). `skipped` continua com `FULL_BENEFIT` e a lista de motivos fica aberta a novos (D2).
- R8 (contrato, isolamento): `docs/openapi.yaml` ganha as duas rotas e `adjustable`; `OpenApiContractTest` e `ContractRoutesTest` passam; as duas rotas têm teste de isolamento entre organizações (404 e RLS) e entre perfis.

### Parte 2 — Tela

- R9 (RF-COB-03): rota `/cobrancas/previa` (Financeiro › Cobranças › Prévia da geração), aberta por um botão em `/cobrancas`, mostra para a unidade escolhida e o período de 10, 30 ou 60 dias (padrão 10): o total previsto, a quantidade, a diferença para o mês anterior (`totalCents - previousMonthTotalCents`, com sinal e cor além do texto), as mensalidades agrupadas por dia de vencimento (aluno, modalidade, plano, desconto, valor) e a seção "Não serão cobrados" com aluno, modalidade e o motivo em texto.
- R10 (unidade): a prévia usa a unidade do seletor; com "todas as unidades" a tela pede para escolher uma (lista das unidades ao alcance), porque a rota exige `unitId`.
- R11 (RN-COB-02): em cada linha `adjustable`, três ações: "Trocar vencimento" (janela com data, mínimo hoje, máximo o fim do mês de referência), "Cancelar mensalidade" (janela com motivo obrigatório e contador de 200) e "Aplicar benefício" (abre o `GrantBenefitDialog` da #65 com aluno e modalidade da linha). Depois de cada ação a prévia é recarregada e os totais mudam.
- R12 (permissão na tela): as ações só aparecem para quem tem `COLLECTIONS` completo (como o `StepDone`); quem só lê vê a prévia sem botões. O servidor segue sendo a barreira.
- R13 (estados): a tela e a lista têm carregando, vazio ("Nada a gerar nos próximos N dias"), erro (com tentar de novo) e sem permissão (403 da rota).
- R14 (qualidade de tela): funciona a partir de 360 px (linhas viram cartões), passa no axe nos temas claro e escuro, tem textos só em `pt-BR.json` e datas/valores pelos formatadores de `lib/format`.
- R15 (contrato do front): os handlers MSW refletem as rotas novas e a prévia deixa de ser vazia: calcula as mensalidades das modalidades ativas do simulado, aplica trocar vencimento e cancelar em memória e devolve `adjustable`.

## 3. Critérios de aceite

- CA1 (R1): dado modalidade ativa com próxima mensalidade em 25/11 (dia 25), quando o Administrador troca para 28/11, então a resposta traz `dueOn` 28/11, a prévia mostra a linha no dia 28, a geração diária cria a cobrança com vencimento 28/11 e a mensalidade de dezembro vence dia 25.
- CA2 (R1): dado a mesma modalidade, quando a data é anterior a hoje, ou passa do último dia do mês de referência, ou não é uma data, então 400 e nada muda.
- CA3 (R2): dado modalidade com próxima mensalidade a gerar, quando o Administrador cancela com motivo "Aluno viajando", então existe uma cobrança `CANCELED` daquele mês com o motivo, o ponteiro avançou para o mês seguinte, a prévia não traz mais a linha e rodar a geração duas vezes não cria cobrança para aquele mês.
- CA4 (R2): dado motivo vazio, só espaços ou com mais de 200 caracteres, quando cancela, então 400 e nada é gravado.
- CA5 (R3): dado aluno com bolsa de 100% (valor zero) ou modalidade encerrada, quando troca vencimento ou cancela, então 422; dado modalidade de outro aluno, de outra organização ou de unidade fora do alcance, então 404.
- CA6 (R4): dado Professor, Aluno e Responsável, quando chamam as duas rotas, então 403; sem sessão, 401; Coordenador de outra unidade, 404; leitura da prévia continua 200 para quem tem `COLLECTIONS` de leitura.
- CA7 (R5): dado duas chamadas simultâneas de cancelar a mesma modalidade, quando terminam, então há exatamente uma cobrança cancelada, nenhuma resposta 500 e a segunda não duplica auditoria; a mesma data enviada duas vezes devolve 200 e grava uma auditoria só.
- CA8 (R6): dado troca de vencimento e cancelamento concluídos, quando se consulta a auditoria, então há `charge.next_due_changed` com de/para e `charge.canceled` com o motivo e `charge.generation_adjusted`, todos sem dado de cartão ou senha; uma recusa não gera linha.
- CA9 (R7): dado aluno com duas mensalidades dentro de 45 dias, quando a prévia roda com `days` 60, então só a primeira linha do aluno tem `adjustable` verdadeiro; trocar o vencimento da primeira não torna a segunda ajustável.
- CA10 (R8): `OpenApiContractTest` e `ContractRoutesTest` passam; organização B nunca vê nem ajusta modalidade da organização A (404, e consulta direta pelo papel de execução não devolve linhas).
- CA11 (RN-COB-04, R7): dado aluno com bolsa de 100% e modalidade encerrada no mesmo mês, quando abre a prévia, então a bolsa aparece em "Não serão cobrados" com o motivo "Bolsa de 100%" e a modalidade encerrada não aparece nem em cobradas nem em não cobradas.
- CA12 (R9): dado cinco mensalidades em três dias e uma bolsa, quando abre `/cobrancas/previa`, então vê três grupos por dia com a quantidade e o subtotal, o total previsto, a diferença para o mês anterior com sinal (ex.: "+ R$ 150,00" ou "− R$ 80,00") e a bolsa em "Não serão cobrados".
- CA13 (R9, R10): dado seletor em "todas as unidades", quando abre a prévia, então a tela pede uma unidade e só chama a rota depois da escolha; trocar de 10 para 30 dias chama a rota com `days=30`.
- CA14 (R11): dado uma linha ajustável, quando o Administrador troca o vencimento pela janela, então a linha muda de dia e os totais são recarregados; ao cancelar sem motivo o botão não envia e mostra o erro; ao cancelar com motivo a linha some; "Aplicar benefício" abre a janela da #65 com o aluno e a modalidade preenchidos e, ao concluir, o valor da linha cai.
- CA15 (R12): dado quem tem `COLLECTIONS` só de leitura, quando abre a prévia, então vê os dados sem os três botões; dado 403 da rota, vê o estado "sem permissão".
- CA16 (R13): dado resposta vazia, erro 500 e lentidão, quando a tela carrega, então mostra, nesta ordem, o vazio com a frase do período, o erro com "Tentar de novo" e o indicador de carregamento.
- CA17 (R14): dado largura de 360 px e os temas claro e escuro, quando a prévia está aberta com dados, então não há rolagem horizontal da página e o axe não aponta violação.
- CA18 (R15): o teste de contrato do MSW cobre prévia (com dados e vazia), troca de vencimento (200, 400, 404, 422) e cancelar (200, 400, 404, 422), e os dados batem com `docs/openapi.yaml`.

## 4. Plano técnico

- Dados: não muda. O ponteiro (`next_charge_ref`, `next_charge_due_on`, V39) e a cobrança `CANCELED` com `canceled_at` (V40) já existem; `charge_monthly_once` cobre a cancelada e impede a geração do mesmo mês. Seed: nada a acrescentar (a prévia lê as matrículas semeadas pela #71).
- API: `PATCH /students/{id}/modalities/{modalityRowId}/next-charge` e `POST .../next-charge/cancel` (erros 400, 401, 403, 404, 422), cookie e CSRF como as demais; `GET /billing/generation-preview` ganha `adjustable` na linha. A organização vem da sessão; a unidade é conferida com `permissions.grant(who, COLLECTIONS, unit) == FULL`.
- Interface: nova página `/cobrancas/previa`; resumo (total, quantidade, diferença), lista por dia (tabela em tela larga, cartões em 360 px), seção "Não serão cobrados", seletor de período e de unidade; janelas Trocar vencimento e Cancelar mensalidade; "Aplicar benefício" reutiliza `GrantBenefitDialog`.
- Segurança: ações só com `FULL`; 403 antes de 404 como em `advance`; modalidade e aluno conferidos juntos (`student_id` e `organization_id` na consulta); motivo é texto livre exibido só como texto; parâmetros nomeados; nada de dado pessoal novo na auditoria.
- Arquivos (vistos): `backend/src/main/java/br/com/ctcore/billing/MonthlyGeneration.java` (métodos novos `changeNextDue` e `cancelNext` reaproveitando `plan(..., onlyRow, lock=true)`, `apply` e `today`; `Line` ganha `adjustable`; `preview` marca a primeira candidata de cada modalidade), `billing/ChargeController.java` (as duas rotas e seus registros de entrada), `billing/ChargeActions.java` (`cancelLocked` e `reason` reaproveitados; só chamados), `docs/openapi.yaml`, `frontend/src/App.tsx` (rota `/cobrancas/previa`), `frontend/src/features/charges/ChargesPage.tsx` (botão para a prévia), `frontend/src/features/charges/types.ts`, `frontend/src/features/benefits/GrantBenefitDialog.tsx` (só usado), `frontend/src/mocks/handlers.ts` (preview e as duas rotas), `frontend/src/i18n/pt-BR.json`. A criar: `backend/src/test/java/br/com/ctcore/billing/NextChargeAdjustIT.java`, `NextChargeIsolationIT.java`; `frontend/src/features/charges/ChargePreviewPage.tsx`, `ChargePreviewRows.tsx`, `ChangeDueDialog.tsx`, `CancelNextDialog.tsx`, `useChargePreview.ts` e os testes `ChargePreviewPage.test.tsx`; o teste MSW `frontend/src/mocks/chargePreview.msw.test.ts`; um fluxo curto em `frontend/e2e/` só se couber como `@critical` (opcional, D5).
- Reuso: `MonthlyGeneration.plan/apply/today`, `ChargeActions.cancelLocked/reason`, `AuditService`, `OrganizationTransactions`, `Permissions.grant`, `GrantBenefitDialog`, `ManagementLayout`, `states/*`, `Modal`, `CharCounter`, `useUnitScope`, `lib/format`.
- Riscos: (1) cobertura de 100% no pacote `billing` (JaCoCo): todo ramo dos dois métodos novos precisa de teste, inclusive 422 e 404; (2) `plan(...)` com `onlyRow` precisa trazer a data certa quando o ponteiro está nulo (linhas antigas): o teste usa os dois casos; (3) trocar o vencimento para depois dos 10 dias tira a linha da janela da geração de hoje, o que é esperado e deve aparecer na prévia com `days` maior; (4) a "diferença para o mês anterior" compara o total previsto de um período curto com um mês inteiro; segue a D7 da #71 e a tela rotula "mês anterior" para não enganar.

## 5. Tarefas

Parte 1 — back-end

- [x] T1: `MonthlyGeneration.changeNextDue` e a rota `PATCH .../next-charge`, com validação de data, travas, 422/404, permissão `FULL` e auditoria — cobre CA1, CA2, CA5, CA6, CA7 (data repetida), CA8 (troca) — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test=NextChargeAdjustIT -Dtest=NoTest -Dsurefire.failIfNoSpecifiedTests=false`
- [x] T2: `MonthlyGeneration.cancelNext` e a rota `POST .../next-charge/cancel` (cria a cobrança, cancela com `cancelLocked`, avança o ponteiro) e `adjustable` na prévia — cobre CA3, CA4, CA5, CA7 (cancelar), CA8 (cancelar), CA9, CA11 — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test=NextChargeAdjustIT,GenerationPreviewIT -Dtest=NoTest -Dsurefire.failIfNoSpecifiedTests=false`
- [x] T3: OpenAPI das rotas e de `adjustable`, teste de isolamento entre organizações e perfis com RLS — cobre CA6, CA10 — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test=NextChargeIsolationIT -Dtest=NoTest -Dsurefire.failIfNoSpecifiedTests=false` e `./backend/mvnw -f backend/pom.xml test -Dtest='OpenApiContractTest,ContractRoutesTest'`

Parte 2 — front-end

- [x] T4: handlers MSW (prévia calculada, trocar vencimento, cancelar) e o teste de contrato — cobre CA18 — prova: `cd frontend && npx vitest run src/mocks/chargePreview.msw.test.ts`
- [x] T5: página `ChargePreviewPage` com resumo, grupos por dia, "Não serão cobrados", seletor de período e de unidade, quatro estados, botão em `/cobrancas` e rota — cobre CA12, CA13, CA15 (403 e leitura), CA16 — prova: `cd frontend && npx vitest run src/features/charges/ChargePreviewPage.test.tsx`
- [x] T6: janelas Trocar vencimento e Cancelar mensalidade, "Aplicar benefício" com `GrantBenefitDialog` e recarga da prévia, ações só com `COLLECTIONS` completo — cobre CA14, CA15 (sem botões) — prova: `cd frontend && npx vitest run src/features/charges/ChargePreviewPage.test.tsx`
- [x] T7: textos em `pt-BR.json`, 360 px, axe nos dois temas, cobertura e conferência completa — cobre CA17 — prova: `cd frontend && npm run lint && npm run typecheck && npx vitest run src/features/charges`

## 6. Decisões

- D1: o e-mail "Cobrança gerada" listado entre as telas do canvas não entra aqui. É a RN-COM-11, depende do serviço de e-mails automáticos e das preferências, e é da #78 (Sprint 09); a geração (#71) não envia e-mail hoje. Alternativa: criar o modelo de e-mail agora, sem envio nem teste de uso.
- D2: "quem não será cobrado e por quê" lista só `FULL_BENEFIT` (bolsa de 100% ou desconto que zera o valor). Modalidade encerrada simplesmente não gera e não aparece. "Bloqueada" e "encerramento solicitado" (RN-COB-04) ainda não existem no sistema; entram com seus motivos nas sprints que criam esses estados. A tela mapeia o motivo para texto em `pt-BR.json` e mostra o código cru como "Outro motivo" se vier um desconhecido. Alternativa: listar as encerradas do mês, descartada por não ser pedida e crescer sem limite.
- D3 (muda o comportamento): "trocar vencimento" é por mensalidade, não muda o dia de vencimento do aluno (esse é o da matrícula). Fica dentro do mês de referência para a regra de uma mensalidade por mês (RN-COB-05) valer sem exceção. Alternativa: aceitar qualquer data futura, descartada porque levaria a mensalidade para outro mês e confundiria o relatório.
- D4 (muda o comportamento): "cancelar" na prévia grava a cobrança do mês como cancelada com motivo (em vez de só pular o mês), para o motivo e o autor ficarem no painel, na auditoria e no relatório "cancelado no período" (RN-COB-10), e para a geração não recriar a cobrança. Alternativa: apenas avançar o ponteiro, sem registro.
- D5: "aplicar benefício" é feito pela janela existente da #65, sem rota nova; depois da concessão a prévia é recarregada e o valor da linha já sai com o desconto, porque a prévia usa a mesma conta da geração. O fluxo ponta a ponta `@critical` é opcional: só entra se a esteira atual aguentar sem ficar instável (histórico das #318 e #320); os testes de tela e de contrato cobrem o requisito.
- Suposição: a primeira linha por modalidade na prévia é sempre a que o ponteiro aponta. Se um dia a prévia passar a mostrar meses já gerados, `adjustable` deve passar a vir do servidor por outra regra; o campo foi criado por isso.
- Suposição: não há arquivo de notas de versão no repositório (só citado no plano). Se existir fora do repositório, a linha "Prévia da geração de mensalidades" deve entrar lá na entrega.

## Construção

Parte 1 (T1 a T3) concluída na sessão anterior; Parte 2 (T4 a T7) concluída na segunda sessão.

- T1 — arquivos: `billing/MonthlyGeneration.java` (`changeNextDue`, `lockNext`), `billing/ChargeController.java` (PATCH), `NextChargeAdjustIT.java` — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test=NextChargeAdjustIT ...` → 6 testes, 0 falhas (troca, data repetida, ponteiro nulo, 400, 422, 404, 403/401, auditoria)
- T2 — arquivos: `MonthlyGeneration.java` (`cancelNext`, `adjustable` em `Line`), `ChargeController.java` (POST cancel), `NextChargeAdjustIT.java`, `GenerationPreviewIT.java` (`marksAdjustable`) — prova: `verify -Dit.test=NextChargeAdjustIT,GenerationPreviewIT` → 0 falhas (cancelar, geração duas vezes, concorrência 200+409, CA9, CA11)
- T3 — arquivos: `docs/openapi.yaml` (duas rotas, quatro schemas, `adjustable`), `NextChargeIsolationIT.java` — prova: `verify -Dit.test=NextChargeIsolationIT,GenerationPreviewIT` → 4 testes, 0 falhas; `test -Dtest='OpenApiContractTest,ContractRoutesTest'` → 7 testes, 0 falhas
Regressão: `./backend/mvnw -f backend/pom.xml verify` → 616 testes, 0 falhas, "All coverage checks have been met" (JaCoCo, inclusive 100% em `billing`). Front-end não rodado nesta parte (nada alterado nele).
Desvios:
- `CancelNext` aceita `referenceMonth` opcional (além de `reason`): se o ponteiro já andou, 409. Sem isso, duas chamadas simultâneas cancelariam dois meses seguidos e o CA7 não seria atendido. A tela da Parte 2 deve enviar o mês da linha. Entrou no OpenAPI.
- `cancelNext` recebe `ChargeActions` por parâmetro (o controller o injeta) para não mudar o construtor de `MonthlyGeneration`, usado em cinco testes.
- Cobrança já existente do mês na hora de cancelar: 409 (não estava no texto; evita cancelar duas vezes).
- Auditoria nos testes é contada por organização, porque `audit_log` não é limpo entre testes.
Não é do item: nenhum.

### Parte 2
- T4 — arquivos: `frontend/src/mocks/handlers.ts` (prévia calculada das modalidades ativas, PATCH next-charge, POST next-charge/cancel, reinício do estado), `frontend/src/mocks/chargePreview.msw.test.ts` — prova: `npx vitest run src/mocks/chargePreview.msw.test.ts --maxWorkers=2` → 7 testes, 0 falhas
- T5 — arquivos: `features/charges/ChargePreviewPage.tsx`, `useChargePreview.ts`, `types.ts`, `ChargesPage.tsx` (botão), `App.tsx` (rota), `ChargePreviewPage.test.tsx` — prova: `npx vitest run src/features/charges/ChargePreviewPage.test.tsx --maxWorkers=2` → 10 testes, 0 falhas
- T6 — arquivos: `ChargePreviewRows.tsx`, `ChangeDueDialog.tsx`, `CancelNextDialog.tsx` (reaproveitam `failureOf`/`FieldError` de `CancelChargeDialog`; `GrantBenefitDialog` só usado), mesmo teste — prova: mesma → 0 falhas
- T7 — arquivos: `i18n/pt-BR.json` (`charges.preview.*`) — prova: `npm run lint && npm run typecheck && npx vitest run src/mocks/chargePreview.msw.test.ts src/features/charges --maxWorkers=2` → 41 testes, 0 falhas; axe nos dois temas a 360 px dentro do teste
Regressão front-end: `npx vitest run --coverage --maxWorkers=2` → 116 arquivos, 1033 testes, 0 falhas; cobertura 96,73% de linhas. Back-end não alterado nesta parte. `npm run build` e `npm run e2e` não rodados nesta sessão (typecheck e lint passam); fluxo `@critical` opcional (D5) não criado.
Desvios da Parte 2:
- A linha da prévia é uma lista de cartões responsiva (`li`), não tabela em tela larga; atende "linhas viram cartões" com um só markup.
- `CancelNextDialog` envia `referenceMonth` da linha (desvio da Parte 1) e os erros 404/409/422 usam as mensagens genéricas de `charges.actions.*`.
- Motivos desconhecidos de "não cobrado" caem em "Outro motivo" (D2).

## Verificação

### Rodada 1 — OK — 2026-10-22, estado 5414b101f11a992d1ed71af4db73d3956b287264

| Critério | Status | Evidência |
|---|---|---|
| CA1, CA2 | PASSOU | `NextChargeAdjustIT` dentro do `verify` completo (616 testes, 0 falhas); diff de `changeNextDue` confere data mín./máx., ponteiro e auditoria |
| CA3, CA4 | PASSOU | `NextChargeAdjustIT` (cancelar, geração duas vezes, motivo inválido) |
| CA5, CA6 | PASSOU | `NextChargeAdjustIT` e `NextChargeIsolationIT` (422/404/403/401) |
| CA7 | PASSOU | teste de concorrência 200+409 no `NextChargeAdjustIT`; data repetida não audita (código: só grava se `!to.equals(from)`) |
| CA8 | PASSOU | auditoria `charge.next_due_changed`, `charge.canceled`, `charge.generation_adjusted` no código e nos ITs |
| CA9, CA11 | PASSOU | `GenerationPreviewIT.marksAdjustable` |
| CA10 | PASSOU | `NextChargeIsolationIT`; `OpenApiContractTest`/`ContractRoutesTest` rodaram no `verify` |
| CA12 a CA17 | PASSOU | `ChargePreviewPage.test.tsx` (10 testes, inclui axe a 360 px nos dois temas); suíte completa verde |
| CA18 | PASSOU | `chargePreview.msw.test.ts` (7 testes) |

Comandos: `./backend/mvnw -f backend/pom.xml verify` → 616 testes, 0 falhas, "All coverage checks have been met", BUILD SUCCESS; em `frontend/`: `npm run lint` e `npm run typecheck` OK; `npx vitest run --coverage --maxWorkers=2` → 116 arquivos, 1033 testes, 0 falhas; `npm run build` OK. `npm run e2e` não rodado (fluxo @critical do item é opcional, D5; CI roda).
Instáveis: nenhum.
Achados:
- sugestão, código, `MonthlyGeneration.cancelNext` — `in.referenceMonth()` não protege `in == null` (a linha de `reason` protege); corpo ausente já é 400 pelo Spring, então sem efeito prático.
- sugestão, especificação — CA17/CA12 pedem valores/mensagens no canvas inacessível ao agente; o dono confere no PR (já registrado no item).

## Entrega

- Prévia da geração de mensalidades em `/cobrancas/previa`: total previsto, quantidade, diferença para o mês anterior com sinal, grupos por dia de vencimento, seção "Não serão cobrados" (bolsa de 100%) e seletores de período (10, 30 ou 60 dias) e de unidade.
- Ações por mensalidade ainda não gerada (só a que o ponteiro aponta): trocar vencimento (dentro do mês de referência), cancelar com motivo (grava a cobrança como cancelada e avança o ponteiro) e aplicar benefício pela janela da #65.
- Back-end: `PATCH /students/{id}/modalities/{modalityRowId}/next-charge` e `POST .../next-charge/cancel`; `adjustable` na prévia; permissão `COLLECTIONS` com `FULL`; auditoria; trava `for update`; isolamento entre organizações e perfis testado.
- Verificação (rodada 1, OK): `verify` do back-end com 616 testes e 0 falhas, cobertura 100% em `billing`; front-end com lint, typecheck, build, 1033 testes e 96,73% de linhas.
- Decisões: D1 a D5 do item. O e-mail "Cobrança gerada" fica para a #78; o fluxo `@critical` não foi criado (opcional, D5).
- Limitações: `npm run e2e` não rodou localmente (o CI roda); CA12 e CA17 (layout em 360 px e axe) pedem conferência visual do dono no PR.
- Desvios: `CancelNext` aceita `referenceMonth` (409 se o ponteiro já andou); a linha da prévia é lista de cartões, não tabela.
