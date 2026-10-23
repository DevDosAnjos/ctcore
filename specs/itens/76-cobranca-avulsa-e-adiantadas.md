# 76 — RF-COB-07 · Cobrança avulsa e gerar mensalidades adiantadas

Origem: issue #76 (Sprint 08); `docs/especificacao/10-cobrancas.md` (RF-COB-07, RN-COB-11, RN-COB-06, RN-COB-05, RN-COB-12). Branch: `item/76-rf-cob-07-avulsa-e-adiantadas`. Parte do que a #71 já deixou: `MonthlyGeneration.advance` e `POST /students/{id}/modalities/{modalityRowId}/charges/advance` (com `AdvanceChargesIT`, OpenAPI e handler MSW), o tipo `ONE_OFF` e a coluna `description` (até 80) na tabela `charge` (V39), `Charges`/`ChargeController`, `ModalityDialog`/`PickSelect` e a busca de aluno do `GrantBenefitDialog` (#65). Telas do canvas (`CobrancaAvulsa.dc.html`, `GerarProximas.dc.html`) inacessíveis ao agente: o layout segue os padrões existentes e o dono confere no PR.

Dividido em **duas partes** (seis tarefas); a numeração é única. A Parte 2 só começa com a 1 concluída. Commit e PR são um só (`#76: RF-COB-07 · Cobrança avulsa e gerar mensalidades adiantadas`, `Closes #76`), no fim da Parte 2.

| Parte | Entrega | Requisitos | Tarefas |
| --- | --- | --- | --- |
| 1 | Rota da cobrança avulsa, auditoria, idempotência, contrato, isolamento | R1 a R8 | T1, T2 |
| 2 | Janelas "Nova cobrança avulsa" e "Gerar próximas mensalidades", botões em `/cobrancas`, MSW, acessibilidade | R9 a R14 | T3 a T6 |

## 1. Objetivo

O Administrador (e o Coordenador, nas suas unidades) cria uma cobrança avulsa para um aluno (kimono, exame, seminário: descrição, valor e vencimento) e gera adiantado as próximas N mensalidades de uma modalidade, ambas pela tela Financeiro › Cobranças. A geração adiantada já existe no servidor (#71, RN-COB-06); este item entrega a rota da cobrança avulsa (RN-COB-11) e as duas janelas.

Fora do escopo: pagamento online da avulsa por Pix ou cartão (Sprint 11; o pagamento no balcão já serve a qualquer cobrança, #75); a regra de inativação por atraso (Sprint 12) — este item só garante que a avulsa é identificável (`type = ONE_OFF`) para ela ficar de fora; cobrança avulsa em lote ou para várias pessoas; editar a avulsa (renegociar e cancelar já existem, #73); e-mail de aviso da nova cobrança (#78); mudar a regra de geração (RN-COB-01 a RN-COB-05).

## 2. Requisitos

### Parte 1 — Servidor

- R1 (RN-COB-11): `POST /api/v1/students/{id}/charges` cria uma cobrança do tipo `ONE_OFF` para o aluno com `unitId`, `description` (sem espaços nas pontas, de 1 a 80 caracteres), `amountCents` (inteiro de 1 a 10.000.000) e `dueOn` (AAAA-MM-DD, hoje ou depois, no fuso da unidade). Responde 201 com a linha da cobrança (a mesma forma de `Charges.Row`). Sem mês de referência, sem modalidade, sem plano nem benefício; tolerância 0; origem `MANUAL`; situação `OPEN`.
- R2 (RN-COB-11, D1): o aluno precisa ter matrícula na unidade informada (ao menos uma linha de `enrollment_modality`, ativa ou encerrada); sem isso, 422. A cobrança é da unidade informada; o pagador é o aluno adulto ou, para menor de 18 anos, o responsável financeiro (422 se o menor não tiver responsável financeiro).
- R3 (RN-COB-11): valor fora do intervalo, descrição vazia ou longa demais, vencimento ausente, malformado ou anterior a hoje, e `unitId` ausente: 400 com o campo; nada é gravado.
- R4 (permissão, RN-COB-11): exige `COLLECTIONS` com acesso `FULL` na unidade (Coordenador e Administrador nas suas unidades); Professor, Aluno e Responsável 403; sem sessão 401; aluno de outra organização, inexistente ou unidade fora do alcance 404 (403 antes de 404, como em `advance`).
- R5 (RNF-12, D2): a rota é idempotente: o corpo aceita `requestId` (uuid, opcional) que vira o id da cobrança; repetir o mesmo `requestId` com o mesmo aluno, unidade, descrição, valor e vencimento devolve 200 com a cobrança já criada, sem duplicar nem auditar de novo; com dados diferentes, 409.
- R6 (auditoria): criar grava `charge.created` (ator, perfil, unidade, tipo `ONE_OFF`, origem `MANUAL`, valor, vencimento, descrição, aluno) na mesma transação; recusas e repetições não auditam. Sem dado de cartão ou senha.
- R7 (RN-COB-11, D3): a avulsa não conta para a inativação por atraso: é identificável por `type = ONE_OFF` e nenhuma regra existente (ponteiro de geração, `charge_monthly_once`, geração diária, prévia, adiantada) a toca; a avulsa também não cria nem avança mensalidade.
- R8 (contrato, isolamento): `docs/openapi.yaml` ganha a rota e seus schemas; `OpenApiContractTest` e `ContractRoutesTest` passam; a rota tem teste de isolamento entre organizações (404 e RLS) e entre perfis. A rota de geração adiantada (RN-COB-06) segue como está, com os testes da #71.

### Parte 2 — Tela

- R9 (RF-COB-07): em Financeiro › Cobranças, dois botões, "Nova cobrança avulsa" e "Gerar próximas mensalidades", só para quem tem `COLLECTIONS` completo (como a prévia e o `StepDone`); quem só lê não os vê. O servidor segue sendo a barreira.
- R10 (RN-COB-11): a janela "Nova cobrança avulsa" pede aluno (busca por nome, mínimo 2 letras, como no `GrantBenefitDialog`), unidade (escolhida entre as unidades das matrículas do aluno; automática quando há uma só), descrição (contador de 80), valor (campo de dinheiro em centavos) e vencimento (data, mínimo hoje). Ao concluir, fecha, a lista é recarregada e a nova cobrança aparece com tipo "Avulsa". O botão só envia com todos os campos válidos; erros do servidor (400, 404, 422, 409) aparecem em texto no campo ou na janela.
- R11 (RN-COB-06): a janela "Gerar próximas mensalidades" pede aluno (mesma busca), a modalidade ativa (entre as que a pessoa pode alterar) e a quantidade N de 1 a 12; mostra o aviso de que a geração automática pula os meses já gerados. Ao concluir, mostra quantas mensalidades foram criadas e o intervalo de meses, a lista é recarregada e a janela fecha ao confirmar. Modalidade encerrada ou de valor zero: o servidor responde 422 e a janela mostra o motivo.
- R12 (envio): ambos os envios desabilitam o botão enquanto enviam; a avulsa envia um `requestId` novo por abertura da janela, para um duplo clique ou nova tentativa não duplicar (R5).
- R13 (estados e qualidade, RNF): as janelas têm carregando (busca de aluno e de matrícula), vazio ("nenhum aluno encontrado", "sem modalidade ativa"), erro (com tentar de novo) e sem permissão; funcionam a partir de 360 px, passam no axe nos temas claro e escuro, têm textos só em `pt-BR.json` e datas/valores pelos formatadores de `lib/format`.
- R14 (contrato do front): os handlers MSW refletem `POST /students/{id}/charges` (201, 200 na repetição, 400, 404, 409, 422) e a avulsa criada entra na lista de `/charges` com tipo `ONE_OFF`; o handler da geração adiantada já existe e continua batendo com o OpenAPI.

## 3. Critérios de aceite

- CA1 (R1): dado aluno adulto matriculado na unidade, quando o Administrador cria "Kimono A2", R$ 250,00, vencimento daqui a 7 dias, então a resposta é 201 com tipo `ONE_OFF`, situação pendente, valor 25000, sem mês de referência nem modalidade, e a cobrança aparece em `GET /charges?type=ONE_OFF`.
- CA2 (R2): dado aluno menor de 18 anos com responsável financeiro, quando cria a avulsa, então o pagador do painel é o responsável; dado menor sem responsável financeiro, ou aluno sem matrícula na unidade informada, então 422 e nada é gravado.
- CA3 (R3): dado descrição vazia, só espaços ou com 81 caracteres, valor 0, negativo ou acima de 10.000.000, vencimento ontem, "31/12" ou ausente, `unitId` ausente, quando cria, então 400 indicando o campo e nenhuma cobrança nova.
- CA4 (R4): dado Professor, Aluno e Responsável, quando chamam a rota, então 403; sem sessão 401; Coordenador de outra unidade, aluno inexistente ou de outra organização, 404; Coordenador na sua unidade, 201.
- CA5 (R5): dado a mesma chamada enviada duas vezes (mesmo `requestId`), inclusive em paralelo, quando terminam, então existe uma cobrança só, a segunda resposta é 200 com o mesmo id, uma auditoria só e nenhum 500; mesmo `requestId` com valor diferente devolve 409.
- CA6 (R6): dado criação concluída, quando se consulta a auditoria, então há `charge.created` com unidade, valor, vencimento e aluno; uma recusa (400, 403, 404, 422) não gera linha.
- CA7 (R7): dado aluno com mensalidade e uma avulsa, quando a geração diária roda duas vezes, então a avulsa não muda nada na geração (ponteiro, mensalidades criadas e prévia iguais) e a avulsa continua com `type = ONE_OFF`; criar a avulsa não altera `next_charge_ref` nem `next_charge_due_on`.
- CA8 (R8): `OpenApiContractTest` e `ContractRoutesTest` passam; organização B nunca cria avulsa para aluno da organização A (404) nem enxerga a cobrança dela (RLS: consulta direta pelo papel de execução não devolve linhas).
- CA9 (R6, RN-COB-06 já entregue, D4): dado aluno com modalidade ativa, quando o Administrador gera as próximas 3 mensalidades, então 201 com 3 cobranças; chamar de novo gera as 3 seguintes e a geração diária não repete nenhum mês (conferido pelos testes da #71, sem novo código de servidor).
- CA10 (R9): dado Administrador, quando abre `/cobrancas`, então vê os dois botões; dado quem tem `COLLECTIONS` só de leitura, então não vê nenhum.
- CA11 (R10, R12): dado a janela da avulsa, quando digita "mar", escolhe o aluno, a unidade (já escolhida se for uma só), preenche descrição, valor e vencimento e confirma, então envia uma vez (botão desabilitado durante o envio), a janela fecha e a lista mostra a cobrança como "Avulsa"; com descrição de 81 caracteres o contador impede passar de 80; sem campo obrigatório o botão não envia.
- CA12 (R10): dado erro 422 (menor sem responsável financeiro) ou 404 do servidor, quando confirma, então a janela continua aberta e mostra a mensagem em texto, sem perder o que foi digitado.
- CA13 (R11): dado aluno com duas modalidades ativas e uma encerrada, quando abre "Gerar próximas mensalidades", escolhe o aluno, então só as ativas aparecem; escolhe uma, N = 3 e confirma, então a janela informa "3 mensalidades criadas (de dez/2026 a dez/2026)" com os meses corretos e a lista é recarregada; N = 0 ou 13 não envia.
- CA14 (R11): dado modalidade de valor zero (bolsa de 100%), quando confirma, então a janela mostra o motivo do 422 e nada foi criado.
- CA15 (R13): dado resposta lenta, vazia e com erro na busca de aluno ou de matrícula, quando as janelas abrem, então mostram, nesta ordem, carregando, "nenhum aluno encontrado" e o erro com "Tentar de novo"; dado 403, o estado "sem permissão".
- CA16 (R13): dado largura de 360 px e os temas claro e escuro, quando as duas janelas estão abertas com dados, então não há rolagem horizontal da página e o axe não aponta violação.
- CA17 (R14): o teste de contrato do MSW cobre a avulsa (201, 200 na repetição, 400, 404, 409, 422) e a avulsa criada na lista; os dados batem com `docs/openapi.yaml`.

## 4. Plano técnico

- Dados: não muda. `charge.type = 'ONE_OFF'`, `description` (check 1 a 80), `reference_month` nulo, `origin` texto livre e `created_by_account_id` já existem (V39). A idempotência usa o próprio id da cobrança (`requestId`), sem coluna nova. Seed: nada a acrescentar (nenhuma tabela nova).
- API: `POST /students/{id}/charges` (corpo `OneOffInput`: `requestId?`, `unitId`, `description`, `amountCents`, `dueOn`; 201 `ChargeRow`, 200 na repetição; erros 400, 401, 403, 404, 409, 422), cookie e CSRF como as demais. A organização vem da sessão; a unidade é conferida com `permissions.grant(who, COLLECTIONS, unit) == FULL` (403 se `effective == NONE`, 404 se sem alcance, como `advance`). Insert com `on conflict (id) do nothing`; se 0 linhas, lê a existente da organização e compara (200 ou 409). Valida o pagador pela data de nascimento (menor de 18 anos em relação a hoje da unidade) com o responsável financeiro de `student_guardian.financial`, como em `MonthlyGeneration`. A rota de adiantada não muda.
- Interface: em `ChargesPage`, dois botões ao lado de "Prévia da geração"; `OneOffChargeDialog` e `AdvanceChargesDialog` sobre `ModalityDialog`; busca de aluno e matrícula reaproveitadas do `GrantBenefitDialog` (a função `useStudentModalities` é exportada para os dois, sem copiar); `PickSelect` para unidade e modalidade; `MoneyField`, contador de 200/80 (`CharCounter`). Tela de lista recarrega com o `version` que já existe.
- Segurança: ações só com `FULL`; aluno e unidade conferidos juntos com `organization_id`; texto livre (descrição) exibido só como texto; parâmetros nomeados; `requestId` não vaza dado entre organizações (RLS); nada de dado pessoal novo na auditoria além do id do aluno.
- Arquivos (vistos): `backend/src/main/java/br/com/ctcore/billing/ChargeController.java` (nova rota), `billing/Charges.java` (reaproveita `Row` e a consulta da linha; ganha, se preciso, um método `row(org, id)` para montar a resposta), `billing/MonthlyGeneration.java` (só leitura da regra de menor/responsável financeiro, sem mudar), `docs/openapi.yaml` (rota, `OneOffInput`, exemplo), `frontend/src/features/charges/ChargesPage.tsx` (botões e janelas), `features/charges/types.ts` (tipos), `features/benefits/GrantBenefitDialog.tsx` (exporta `useStudentModalities`), `frontend/src/mocks/handlers.ts` (nova rota; o handler `advance` já existe), `frontend/src/i18n/pt-BR.json` (`charges.oneOff.*`, `charges.advance.*`). A criar: `billing/OneOffCharges.java` (regra e auditoria; o controller só delega), `backend/src/test/java/br/com/ctcore/billing/OneOffChargeIT.java`, `OneOffChargeIsolationIT.java`; `frontend/src/features/charges/OneOffChargeDialog.tsx`, `AdvanceChargesDialog.tsx`, `useChargeCreation.ts` (chamadas `createOneOff` e `advanceCharges`) e os testes `OneOffChargeDialog.test.tsx`, `AdvanceChargesDialog.test.tsx`; o teste MSW `frontend/src/mocks/oneOffCharge.msw.test.ts`; fluxo `@critical` em `frontend/e2e/` só se couber (opcional, D5).
- Reuso: `ChargeController` (padrão das rotas), `Permissions.grant/effective`, `AuditService`/`AuditEntry`, `OrganizationTransactions`, `Charges.today`, `ApiValidationException`/`ApiStatusException`, `ModalityDialog`, `PickSelect`, `MoneyField`, `CharCounter`, `useResource`, `lib/format`, `states/*`, `BillingFixtures` nos testes.
- Riscos: (1) cobertura de 100% no pacote `billing` (JaCoCo): todo ramo de `OneOffCharges` precisa de teste, inclusive 409, 422 por menor sem responsável e a repetição concorrente; (2) o `on conflict (id)` com RLS: o teste de repetição usa a mesma organização e outra organização com o mesmo `requestId` (o INSERT não pode vazar nem derrubar com 500); (3) a avulsa aparece na contagem "Em atraso" da lista quando vence (esperado: só a inativação a ignora, Sprint 12; conferido na lista); (4) a janela de adiantada depende da matrícula carregar; sem matrícula ativa a janela mostra o vazio.

## 5. Tarefas

Parte 1 — back-end

- [x] T1: `OneOffCharges` (validação, pagador, matrícula na unidade, insert idempotente por `requestId`, auditoria `charge.created`) e a rota `POST /students/{id}/charges` — cobre CA1, CA2, CA3, CA4, CA5, CA6, CA7 — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test=OneOffChargeIT -Dtest=NoTest -Dsurefire.failIfNoSpecifiedTests=false`
- [x] T2: OpenAPI da rota e dos schemas, teste de isolamento entre organizações e perfis com RLS; confirma que a adiantada segue verde — cobre CA8, CA9 — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test=OneOffChargeIsolationIT,AdvanceChargesIT -Dtest=NoTest -Dsurefire.failIfNoSpecifiedTests=false` e `./backend/mvnw -f backend/pom.xml test -Dtest='OpenApiContractTest,ContractRoutesTest'`

Parte 2 — front-end

- [x] T3: handler MSW de `POST /students/{id}/charges` (a avulsa entra na lista) e o teste de contrato — cobre CA17 — prova: `cd frontend && npx vitest run src/mocks/oneOffCharge.msw.test.ts --maxWorkers=2`
- [x] T4: `OneOffChargeDialog` (busca de aluno, unidade, descrição, valor, vencimento, `requestId`, erros do servidor) com os quatro estados — cobre CA11, CA12, CA15 — prova: `cd frontend && npx vitest run src/features/charges/OneOffChargeDialog.test.tsx --maxWorkers=2`
- [x] T5: `AdvanceChargesDialog` (aluno, modalidade ativa, N de 1 a 12, resultado com os meses, 422) e exportação de `useStudentModalities` — cobre CA13, CA14, CA15 — prova: `cd frontend && npx vitest run src/features/charges/AdvanceChargesDialog.test.tsx --maxWorkers=2`
- [x] T6: botões em `ChargesPage` só com `COLLECTIONS` completo, recarga da lista, textos em `pt-BR.json`, 360 px, axe nos dois temas e conferência completa — cobre CA10, CA16 — prova: `cd frontend && npm run lint && npm run typecheck && npx vitest run src/features/charges src/mocks --maxWorkers=2`

## 6. Decisões

- D1 (muda o comportamento): a avulsa leva uma unidade (`unitId`), escolhida entre as unidades onde o aluno tem matrícula; a cobrança é daquela unidade, a quem pertence o recebimento e o recibo. Alternativa: usar a unidade do seletor da gestão, descartada porque "todas as unidades" não define uma e o aluno pode estar em duas.
- D2: idempotência da avulsa (RNF-12) por `requestId` enviado pela tela, que vira o id da cobrança, sem migração. Alternativa: coluna ou chave de idempotência em tabela própria, descartada por custo sem ganho; sem `requestId` a rota cria uma cobrança por chamada.
- D3: "não conta para a inativação por atraso" (RN-COB-11) é cumprido por tipo: a avulsa é `ONE_OFF` e não entra em nenhuma regra de mensalidade. A regra de inativação só existe na Sprint 12; lá o filtro deve considerar só mensalidades (e demais tipos que a especificação listar). Este item não cria inativação nem teste dela; a conferência da issue é o CA7. Sugestão de acompanhar na #12x que criar a inativação.
- D4: a geração adiantada (RN-COB-06) não tem código de servidor novo: já entregue e testada na #71 (`AdvanceChargesIT`); esta issue entrega a janela. Se a revisão achar lacuna no servidor, volta como ajuste.
- D5: valor da avulsa de R$ 0,01 a R$ 100.000,00 e vencimento de hoje em diante; a especificação não define limites, são os mais simples que evitam valor zero e data no passado. O fluxo ponta a ponta `@critical` é opcional: só entra se a esteira aguentar sem ficar instável (histórico #318 e #320); testes de tela e de contrato cobrem o requisito.
- D6: sem e-mail de aviso da nova cobrança; entra com os e-mails automáticos da #78.
- Suposição: não há notas de versão no repositório; se existirem fora dele, a linha "Cobrança avulsa e geração de mensalidades adiantadas" entra lá na entrega.
- Suposição: o pagamento da avulsa "por Pix ou cartão" (RN-COB-11) é online e fica para a Sprint 11; até lá ela é paga no balcão (#75). Se o dono quiser restringir os meios da avulsa no balcão, é regra nova.

## Construção

- T1 — arquivos: `backend/.../billing/OneOffCharges.java` (novo), `ChargeController.java` (rota `POST /students/{id}/charges`, 201/200), `backend/src/test/.../billing/OneOffChargeIT.java` (novo) — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test=OneOffChargeIT` → 6 testes passam (CA1 a CA7: criação, pagador menor/adulto, 400, permissões, idempotência sequencial e em paralelo, ponteiro e geração diária intactos)
- T2 — arquivos: `docs/openapi.yaml` (rota e `OneOffInput`), `backend/src/test/.../billing/OneOffChargeIsolationIT.java` (novo) — prova: `verify -Dit.test=OneOffChargeIT,OneOffChargeIsolationIT,AdvanceChargesIT` → verde; `test -Dtest='OpenApiContractTest,ContractRoutesTest'` → verde
- T3 — arquivos: `frontend/src/mocks/handlers.ts` (handler `POST /students/{id}/charges`, `ONE_OFF` no `MockCharge`), `frontend/src/mocks/oneOffCharge.msw.test.ts` (novo) — prova: `npx vitest run src/mocks/oneOffCharge.msw.test.ts --maxWorkers=2` → 6 testes passam
- T4 — arquivos: `features/charges/OneOffChargeDialog.tsx`, `StudentSearch.tsx`, `useChargeCreation.ts`, `types.ts`, `OneOffChargeDialog.test.tsx`; `ModalityPickers.tsx` ganha `errorPrefix` (mensagens de erro próprias) — prova: `npx vitest run src/features/charges/OneOffChargeDialog.test.tsx --maxWorkers=2` → 11 testes passam (inclui axe claro/escuro a 360 px)
- T5 — arquivos: `features/charges/AdvanceChargesDialog.tsx`, `AdvanceChargesDialog.test.tsx`; `GrantBenefitDialog.tsx` exporta `useStudentModalities` — prova: `npx vitest run src/features/charges/AdvanceChargesDialog.test.tsx --maxWorkers=2` → 6 testes passam
- T6 — arquivos: `features/charges/ChargesPage.tsx` (botões só com `COLLECTIONS` FULL, recarga), `ChargesPage.test.tsx`, `i18n/pt-BR.json` (`charges.creation.*`, `charges.advance.*`) — prova: `npm run lint && npm run typecheck && npx vitest run src/features/charges src/mocks --maxWorkers=2` → 40 arquivos, 266 testes passam

Regressão: `./backend/mvnw -f backend/pom.xml verify` → BUILD SUCCESS (testes rápidos e `*IT` verdes, JaCoCo 80% e 100% nos pacotes exigidos, inclusive `billing`); em `frontend/`: `npm run lint`, `npm run typecheck`, `npm run build` e `npm run test:cov -- --maxWorkers=2` (uma vez) → 122 arquivos, 1066 testes passam, cobertura acima do mínimo. Não rodou: `npm run e2e` (D5: fluxo `@critical` opcional, não criado).
Desvios: (1) `ModalityDialog` ganhou a prop opcional `errorPrefix` para mensagens de erro próprias das janelas de cobrança (o padrão segue o das trocas de matrícula). (2) O Coordenador alcança todas as unidades da organização (`Permissions.grant`), então "Coordenador de outra unidade 404" (CA4) é testado com Administrador sem a unidade; o Coordenador na sua unidade dá 201. (3) Botão final do resultado da geração adiantada chama-se "Concluir" (o "Fechar" já é o X da janela). (4) `StudentSearch.tsx` criado para a busca de aluno comum às duas janelas, em vez de copiá-la.

## Verificação

### Rodada 1 — OK — 2026-10-22, estado ad99ec17856903511f3c5b07b0608ec1bd7229ad

| Critério | Status | Evidência |
|---|---|---|
| CA1 | PASSOU | `OneOffChargeIT.createsForAdult` (201, ONE_OFF, PENDING, 25000, sem mês/modalidade, listada por `type=ONE_OFF`) |
| CA2 | PASSOU | `OneOffChargeIT.payer` (pagador = responsável; menor sem responsável e sem matrícula = 422, nada gravado) |
| CA3 | PASSOU | `OneOffChargeIT.validation` (12 corpos inválidos = 400; limites 80 e 10.000.000 aceitos) |
| CA4 | PASSOU | `OneOffChargeIT.permissions`, `OneOffChargeIsolationIT` (401/403/404/201). Desvio 2 da construção (Coordenador alcança todas as unidades) é coerente com `Permissions.grant` |
| CA5 | PASSOU | `OneOffChargeIT.idempotent` (200 na repetição, 409 com dado diferente ou outra organização, paralelo = 200+201, 1 auditoria) |
| CA6 | PASSOU | `OneOffChargeIT.createsForAdult` e `payer` (audit `charge.created` com unidade/valor/vencimento/aluno; recusas não auditam) |
| CA7 | PASSOU | `OneOffChargeIT.leavesMonthlyAlone` |
| CA8 | PASSOU | `OneOffChargeIsolationIT` (404 e RLS); `OpenApiContractTest`/`ContractRoutesTest` dentro do `verify` |
| CA9 | PASSOU | `AdvanceChargesIT` no `verify` (código da #71, sem mudança) |
| CA10 | PASSOU | `ChargesPage.test.tsx` (FULL vê os dois, READ não vê) |
| CA11 | PASSOU | `OneOffChargeDialog.test.tsx` (1 envio, botão desabilitado, contador 80, requestId) e `ChargesPage.test.tsx` (fecha e recarrega); rótulo "Avulsa" já existe em `pt-BR.json` |
| CA12 | PASSOU | `OneOffChargeDialog.test.tsx` (422/404/409 em texto, dados mantidos; 400 no campo) |
| CA13 | PASSOU | `AdvanceChargesDialog.test.tsx` (só ativas, resumo com meses, 0 e 13 não enviam) |
| CA14 | PASSOU | `AdvanceChargesDialog.test.tsx` (422 mostra "bolsa de 100%") |
| CA15 | PASSOU | testes de estados nos dois diálogos (carregando, vazio, erro com "Tentar de novo", sem permissão) |
| CA16 | PASSOU | axe claro/escuro com `innerWidth = 360` nos dois diálogos; rolagem horizontal não é medível em jsdom, layout lido no diff (flex-wrap, campos `w-full`) |
| CA17 | PASSOU | `oneOffCharge.msw.test.ts` (201, 200, 400, 404, 409, 422, lista, contrato) |

Comandos:
- `./backend/mvnw -f backend/pom.xml verify` → BUILD SUCCESS, 632 testes, 0 falhas (inclui OneOffChargeIT 6, OneOffChargeIsolationIT 1), JaCoCo "All coverage checks have been met".
- `npm run lint && npm run typecheck && npm run build` (frontend) → verdes.
- `npm run test:cov -- --maxWorkers=2` (uma vez) → 122 arquivos, 1066 testes passam, sem falha de cobertura.
- Não rodado: `npm run e2e` (D5: fluxo `@critical` opcional, não criado).

Instáveis: nenhum.

Achados:
- sugestão, código, `AdvanceChargesDialog.tsx` `month()` — formata mês com `toLocaleDateString` direto, e o R13 pede `lib/format`; `lib/format` não tem formatador de mês, então vale acrescentar um lá em item futuro.
- sugestão, código, `OneOffCharges.java` — com `COLLECTIONS` abaixo de FULL e acima de NONE o retorno é 404, não 403; hoje nenhum perfil tem esse acesso e é igual ao `advance`, mas vale alinhar se surgir.

## Entrega

- Cobrança avulsa (`POST /students/{id}/charges`, 201 ou 200 na repetição) e geração adiantada de mensalidades no painel de cobranças, com os quatro estados e axe claro/escuro a 360 px.
- Pagador: responsável; menor sem responsável e sem matrícula recebe 422 e nada é gravado. Idempotência por `requestId` (repetição 200, dado diferente 409, paralelo 200+201). Toda criação grava `charge.created`.
- Verificação: rodada 1 OK (17 critérios passaram). Backend `verify` verde (632 testes, JaCoCo ok); frontend lint, typecheck, build e `test:cov` verdes (1066 testes).
- Limitações: `npm run e2e` não rodado (fluxo `@critical` opcional, não criado). Desvios de construção 1 a 4 em "Construção". Sugestões de código em "Verificação" (formatador de mês e status 404 vs 403 para perfis intermediários) ficam para item futuro.
