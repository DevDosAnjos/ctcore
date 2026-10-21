# 65 — RF-BEN-02 · Conceder benefício

Origem: issue #65 (Sprint 07), com o bloco "Encaixe herdado da Sprint 06" (selo Bolsista); `docs/especificacao/07-planos-e-beneficios.md` (RF-BEN-02, RN-BEN-01, 03, 04, 05, 06, 07, 08, 09), `10-cobrancas.md` (RN-COB-02, 03, 04 como contexto), `13` (RN-NOT-04), `08` (RF-ALU-02). Branch: `item/65-conceder-beneficio`. Constrói sobre #64 (tabela `benefit`, `Benefits`, `BenefitsPage`), #63 (`enrollment_modality`, ficha com modalidades) e #59 (selos). Canvas `BeneficioConceder.dc.html` inacessível ao agente.

## 1. Objetivo

Coordenador (e Administrador nas unidades dele) concede um benefício a uma modalidade da matrícula de um aluno: tipo (bolsa em % ou desconto em reais), valor, motivo, decidido por e validade. Antes de confirmar, a janela mostra plano, benefício, nova mensalidade e a partir de qual mês vale, e avisa quando um benefício ativo será encerrado. A ação fica na tela de benefícios e na ficha do aluno; a ficha e a lista ganham o selo Bolsista.

Fora do escopo: encerrar benefício sem substituição (#66), aplicar o benefício na cobrança (Sprint 08), notificação do benefício alto (D5), sem caixa de notificações ainda.

## 2. Requisitos

- R1 (RF-BEN-02, D1): `POST /api/v1/benefits` concede o benefício a uma modalidade ativa da matrícula (`studentId`, `modalityRowId`, `type`, `value`, `reason`, `decidedBy`, `validUntil` opcional, `replaceExisting`) e devolve 201 com a mesma `BenefitRow` da lista (#64).
- R2 (RF-BEN-02, D2): `POST /api/v1/benefits/preview`, sem gravar nem auditar, recebe `studentId`, `modalityRowId`, `type` e `value` e devolve plano (id, nome, valor em centavos), benefício ativo atual (ou nulo), `newMonthlyValueCents`, `effectiveFrom` (mês `AAAA-MM`), `replaces` e `high`.
- R3 (RN-BEN-01, D3): havendo benefício `ACTIVE` na modalidade, concede só com `replaceExisting=true`: o atual é encerrado (`ENDED`, `ended_at`, `end_reason` "Substituído por novo benefício") e o novo gravado na mesma transação; sem a confirmação, 409 e nada muda. Nunca ficam dois ativos (índice de #64 como rede).
- R4 (RN-BEN-08, RN-BEN-09, D4): professor, família e bolsa são o mesmo benefício comum, um por matrícula de modalidade; não há tipo, campo nem cobrança por família. Quando o atual é bolsa (`PERCENT`) e o novo é desconto em reais, ou o contrário, a escolha de qual fica é a confirmação de R3: confirmar mantém o novo, cancelar mantém o atual.
- R5 (RN-BEN-02/03, D5): validação no servidor: `type` `PERCENT` (inteiro de 1 a 100) ou `AMOUNT` (inteiro de centavos de 1 a 99999999); `reason` de 2 a 200 e `decidedBy` de 2 a 80 caracteres (sem espaços nas pontas); `validUntil` nulo ou data de hoje em diante no fuso da unidade da modalidade. O lançador (`grantedBy`) é a conta da sessão, nunca vem do corpo. Violação: 400 com o campo.
- R6 (RN-BEN-04, D6): `effectiveFrom` é o mês da primeira cobrança ainda não gerada da modalidade (a geração cria as que vencem em 10 dias, RN-COB-02): primeiro vencimento do `due_day` depois de hoje + 10 dias no fuso da unidade. Conceder não altera nenhuma cobrança existente (Sprint 08 aplica o benefício válido na geração, RN-COB-03).
- R7 (RN-BEN-05, RN-BEN-07): `newMonthlyValueCents` usa `Benefits.monthlyValueCents` (mínimo zero; bolsa de 100% dá zero); com zero a janela informa que a modalidade não gera mensalidade e que o aluno segue Ativo; conceder bolsa de 100% não muda a situação do aluno nem a matrícula.
- R8 (RN-BEN-06, D5): `high` (bolsa acima de 50% ou desconto acima de metade do plano) é calculado como em #64, devolvido no preview e na resposta; não há aprovação; a notificação ao Coordenador não é enviada (D5).
- R9 (permissão, D7): `BENEFITS` conferido no servidor nas duas rotas: Coordenador e Administrador; Professor, Aluno e Responsável 403; sem sessão 401. Administrador só concede para modalidade em unidade do alcance dele (403 fora). Aluno ou linha de modalidade que não existe, não está ativa ou é de outra organização: 404; linha que não pertence ao aluno informado: 404.
- R10 (auditoria): conceder grava `benefit.granted` (entidade `benefit`, unidade da modalidade, depois com aluno, modalidade, tipo, valor, validade, motivo e decidido por); substituir grava também `benefit.ended` do antigo com `end_reason` e o id do novo, na mesma transação. O preview não grava.
- R11 (concorrência e idempotência, D8): a modalidade é travada (`for update`) durante a concessão; duas concessões simultâneas resultam em um ativo só e a segunda recebe 409 ou, com `replaceExisting`, encerra a primeira; repetir a mesma chamada sem `replaceExisting` dá 409 e não duplica.
- R12 (RF-BEN-02, D9): a janela "Conceder benefício" (`GrantBenefitDialog`) tem aluno (busca por nome), modalidade, tipo, valor, motivo, decidido por e validade; mostra plano, benefício, nova mensalidade e "vale a partir de <mês>" (via preview) e, quando há ativo, o aviso de que será encerrado; confirmar chama R1 com `replaceExisting` igual a `replaces`; erros do servidor aparecem no campo; quatro estados, 360 px, axe nos dois temas, textos em `pt-BR.json`. Abre em `/beneficios` (botão "Conceder benefício") e na ficha do aluno (botão por modalidade ativa, com aluno e modalidade fixos); nunca na ficha do Professor nem para quem não pode.
- R13 (encaixe da Sprint 06, D10): `StudentSeal` ganha `SCHOLARSHIP` (Bolsista): aluno com benefício `PERCENT` ativo e não vencido em alguma modalidade ativa; aparece na lista, na ficha e no filtro `seal=SCHOLARSHIP`; benefício encerrado ou vencido não conta. Ficha reduzida do Professor segue sem selos.
- R14 (contrato): `docs/openapi.yaml` (duas rotas novas, `BenefitInput`, `BenefitPreview`, selo novo em `StudentSeal`), handlers MSW e seed refletem o servidor.
- R15 (notas de versão, D11): a mudança é visível para as academias; atualizar as notas de versão, se o arquivo existir.

## 3. Critérios de aceite

- CA1 (R1, R3): dado modalidade sem benefício ativo, quando o Coordenador faz `POST /benefits` válido, então 201 com a linha (`status` `ACTIVE`, `grantedBy` o nome da conta da sessão) e ela aparece em `GET /benefits`.
- CA2 (R3, R4): dado benefício ativo na modalidade, quando concede outro sem `replaceExisting`, então 409 e o atual segue ativo, sem novo registro; com `replaceExisting=true`, o atual vira `ENDED` com `ended_at` e motivo de substituição, o novo fica `ACTIVE` e a modalidade tem um ativo só; vale também de bolsa para desconto em reais e de desconto para bolsa.
- CA3 (R5): dado entradas inválidas (tipo desconhecido, `PERCENT` 0 ou 101 ou fracionário, `AMOUNT` 0 ou acima de 99999999, motivo vazio ou de 201 caracteres, decidido por de 1 caractere, validade de ontem), quando posta, então 400 com o campo e nada é gravado; validade de hoje é aceita.
- CA4 (R5): dado corpo com `grantedBy` ou `organizationId` estranhos, quando posta, então ignorados: o lançador é a conta da sessão e a organização a do vínculo.
- CA5 (R6): dado `due_day` 10, hoje dia 5 e dia 15 do mesmo mês e plano de R$ 150, quando pede preview, então `effectiveFrom` é o mês do próximo dia 10 que vence depois de hoje + 10 dias (testes com data fixa, virada de mês e de ano, e fuso da unidade); conceder não muda nenhuma linha fora de `benefit`.
- CA6 (R2, R7, R8): dado plano de R$ 150, quando preview de bolsa 30%, desconto R$ 50, desconto R$ 200 e bolsa 100%, então `newMonthlyValueCents` é 10500, 10000, 0 e 0; `high` é falso, falso, verdadeiro e verdadeiro; `replaces` e o benefício atual vêm preenchidos quando há ativo; o preview não grava benefício nem auditoria.
- CA7 (R7): dado bolsa de 100% concedida, quando lê a ficha do aluno, então ele segue `ACTIVE` e a matrícula e a modalidade não mudam.
- CA8 (R9): dado Professor, Aluno e Responsável, quando chama preview ou POST, então 403; sem sessão 401; Administrador da unidade A concede em A e recebe 403 em B; aluno inexistente, linha de outra organização, linha encerrada ou de outro aluno: 404.
- CA9 (R9, R10): dado duas organizações, quando o Coordenador de A usa ids de B, então 404 e nada muda em B; com `ctcore_app` e contexto de A, B continua invisível (RLS já testada em #64, repetida para a escrita).
- CA10 (R10): dado concessão simples e com substituição, então a auditoria tem `benefit.granted` (e `benefit.ended` do antigo) com unidade, antes e depois, sem dado de saúde, CPF ou contato; preview e chamadas recusadas não auditam.
- CA11 (R11): dado duas concessões simultâneas à mesma modalidade sem confirmação, então no fim há um benefício ativo e a perdedora recebe 409.
- CA12 (R13): dado alunos com bolsa ativa, bolsa vencida, bolsa encerrada, só desconto em reais ativo e sem benefício, quando lista, abre a ficha e filtra `seal=SCHOLARSHIP`, então só o primeiro tem o selo; Administrador só enxerga selos dos alunos do alcance dele; Professor continua sem selos.
- CA13 (R12): dado a janela aberta em `/beneficios`, quando escolhe aluno e modalidade, preenche e vê o resumo (plano, benefício, nova mensalidade, mês) e, com ativo, o aviso de encerramento, então Confirmar chama `POST /benefits` com `replaceExisting` correto, fecha, atualiza a tabela (e a ficha, quando aberta nela) e mostra erro do servidor no campo; com mensalidade zero mostra o aviso da RN-BEN-05.
- CA14 (R12): dado a janela, então carregando (preview ou modalidades), erro com "Tentar de novo", aluno sem modalidade ativa (vazio) e sem permissão (botão ausente na ficha do Professor e para perfil sem `BENEFITS`) têm estado próprio; 360 px sem rolagem horizontal da página e axe sem violações nos dois temas.
- CA15 (R14): `OpenApiContractTest` e `ContractRoutesTest` passam; teste MSW cobre preview, POST (201, 409, 400) e o selo no formato do servidor.

## 4. Plano técnico

- Dados: sem migração nova (`benefit` de #64 já tem status, encerramento, motivo, índice de um ativo e RLS). Seed `infra/seed/15-beneficios.sql` já cobre bolsa, vencida e encerrada; acrescentar uma bolsa ativa de aluno com selo se faltar.
- API: `POST /api/v1/benefits/preview` (200 `BenefitPreview`; 400, 401, 403, 404) e `POST /api/v1/benefits` (201 `BenefitRow`; 400, 401, 403, 404, 409), com CSRF como as demais escritas; filtro `seal` de `/students` aceita `SCHOLARSHIP`. Entram em `docs/openapi.yaml`.
- Back-end: criar `benefit/BenefitWrite.java` (preview, validação, `for update` na linha de `enrollment_modality`, encerramento do atual, insert, auditoria via `AuditService` como `PlanWrite`, permissão por `Plans.requireAccess` e alcance pela unidade da modalidade como `Enrollments.requireManage`/`Plans.reach`); reutilizar `Benefits.monthlyValueCents`, `HIGH_SQL` e `Benefits.get`/consulta de linha (extrair um `get(who, id)` de `Benefits.list` se não houver); adicionar as rotas a `BenefitController`; alterar `student/StudentSeal.java` (+ `SCHOLARSHIP`), `Students.sealPredicate` (`exists` em `benefit` `PERCENT` `ACTIVE`, `valid_until` nulo ou >= hoje, com modalidade `ACTIVE`) e a lista de colunas lida em `seals(rs)` (a ordem do enum é a do select, ver linha 228).
- Interface: criar `frontend/src/features/benefits/GrantBenefitDialog.tsx` (e `useGrantBenefit.ts` com preview/POST), reutilizando `ModalityDialog`/`PickSelect` de `students/ModalityPickers.tsx` se encaixar; alterar `BenefitsPage.tsx` (botão), `students/RecordEnrollment.tsx` (ação por modalidade com `canEdit` e permissão `BENEFITS`), `students/types.ts` (`StudentSeal`), `StudentFilters.tsx` (`SEALS`), `mocks/handlers.ts` (+ testes MSW), `i18n/pt-BR.json` (janela e `students.seals.SCHOLARSHIP`). Selo renderizado onde os demais selos já são (lista e ficha).
- Segurança: parâmetros ligados; perfil e alcance do principal; organização só da sessão; `reason` e `decidedBy` texto livre exibido como texto; auditoria sem dado pessoal além do necessário; sem expor CPF, contato ou saúde no preview.
- Arquivos: a alterar (vistos): `benefit/Benefits.java`, `benefit/BenefitController.java`, `student/StudentSeal.java`, `student/Students.java`, `docs/openapi.yaml`, `BenefitsPage.tsx`, `RecordEnrollment.tsx`, `students/types.ts`, `StudentFilters.tsx`, `mocks/handlers.ts`, `pt-BR.json`, `StudentSealsIT.java`, `infra/seed/15-beneficios.sql`; a criar: `BenefitWrite.java`, `BenefitGrantIT.java`, `BenefitWriteTest.java` (data efetiva e validação), `GrantBenefitDialog.tsx`, `useGrantBenefit.ts`, `GrantBenefitDialog.test.tsx`, `benefits-write.msw.test.ts`.
- Reuso: `AuditService`/`AuditEntry`, `OrganizationTransactions`, `Plans.requireAccess`/`reach`, `Benefits.monthlyValueCents`/`HIGH_SQL`, `ApiValidationException`, `ErrorCode.CONFLICT`, `ModalityDialog`, `useStudents` (busca por nome), `GET /students/{id}/enrollment` (modalidades do aluno), `formatMoney`.
- Riscos: (1) a regra "mês em que vale" depende de uma cobrança que só existe na Sprint 08; a data é calculada pela RN-COB-02 e a Sprint 08 deve respeitá-la (D6). (2) `Students.seals(rs)` lê colunas por ordem do enum: errar o índice troca selos (coberto por `StudentSealsIT`). (3) Encerrar e inserir na mesma transação: sem `for update` haveria corrida (R11). (4) Layout do canvas inacessível: o dono confere no PR.

## 5. Tarefas

- [x] T1: `BenefitWrite` com preview (plano, atual, nova mensalidade, `effectiveFrom`, `replaces`, `high`) e validação — cobre CA3, CA4, CA5, CA6 — prova: `./backend/mvnw -f backend/pom.xml test -Dtest=BenefitWriteTest` e `verify -Dit.test=BenefitGrantIT`.
- [x] T2: `POST /benefits` com travamento, substituição atômica, 409, auditoria (`benefit.granted`, `benefit.ended`), permissão, alcance e isolamento — cobre CA1, CA2, CA7, CA8, CA9, CA10, CA11 — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test=BenefitGrantIT`.
- [x] T3: selo `SCHOLARSHIP` em `StudentSeal`/`Students` (lista, ficha, filtro) — cobre CA12 — prova: `verify -Dit.test=StudentSealsIT`.
- [x] T4: OpenAPI, handlers MSW (preview, POST, selo) e seed — cobre CA15 — prova: `test -Dtest='OpenApiContractTest,ContractRoutesTest'` e `npx vitest run src/mocks` em `frontend/`.
- [x] T5: `GrantBenefitDialog` com resumo, aviso de substituição, mensalidade zero e quatro estados; botão em `/beneficios` — cobre CA13, CA14 — prova: `npx vitest run src/features/benefits` em `frontend/`.
- [x] T6: ação por modalidade na ficha, selo Bolsista na lista, na ficha e no filtro, textos pt-BR — cobre CA12 (tela), CA13, CA14 — prova: `npx vitest run src/features/students` em `frontend/`.
- [x] T7: notas de versão (se existirem) e regressão completa (não cobre critério: confere cobertura e pacotes exigidos) — prova: `./backend/mvnw -f backend/pom.xml verify` e, em `frontend/`, `npm run lint && npm run typecheck && npm run build && npm run test:cov && npm run e2e`.

## 6. Decisões

- D1: uma rota de concessão por modalidade da matrícula (`modalityRowId`), não por aluno e modalidade, porque a RN-BEN-01 fala em "modalidade da matrícula" e a linha já carrega unidade e plano. Alternativa: `studentId` + `modalityId`; descartada por ambiguidade na transferência.
- D2: o resumo antes de confirmar vem de uma rota de preview no servidor, para a conta (mensalidade, mês, `high`) ter um só lugar. Alternativa: calcular no front; descartada por duplicar a regra.
- D3 (muda o comportamento): substituir exige confirmação explícita (`replaceExisting`); sem ela, 409. Alternativa: encerrar o atual silenciosamente; descartada porque o card manda avisar.
- D4 (muda o comportamento): "Administrador escolhe qual fica" (RN-BEN-08, bolsa x desconto de professor) é a confirmação da substituição: confirmar fica com o novo, cancelar com o atual. Não há tela de escolha separada nem benefício combinado (RN-BEN-01). Alternativa: tela com dois benefícios lado a lado; descartada porque o resultado é o mesmo. Vale para Coordenador também.
- D5 (muda o comportamento): a notificação de benefício alto (RN-BEN-06, RN-NOT-04) continua sem ser enviada: não há infraestrutura de notificações (módulo 13) no código. Entrega a marca `high` e o filtro (#64); a notificação nasce com o módulo 13 (sugestão: registrar o encaixe no item de notificações). Alternativa: criar a tabela de notificações aqui; descartada por ser módulo independente.
- D6: "a próxima cobrança gerada" é calculada pelo `due_day` e pela antecedência de 10 dias da RN-COB-02: primeiro vencimento depois de hoje + 10 dias, no fuso da unidade (`due_day` vai de 1 a 28, sem ajuste de fim de mês). Suposição: se o item de cobrança escolher outra antecedência, só essa constante muda.
- D7: mesma permissão `BENEFITS` e alcance de #64; modalidade fora do alcance do Administrador dá 403 na escrita (como `Enrollments`) e lista vazia na leitura. Alternativa: 404; descartada por já haver convenção de 403 nas escritas.
- D8: concorrência resolvida com `for update` na linha da modalidade (padrão de `Enrollments.lockRow`), sem chave de idempotência: a operação de dois passos tem o 409 como proteção.
- D9: o aluno é escolhido na janela por busca de nome, pelas rotas existentes de lista e matrícula; sem rota nova de busca. Do contexto da ficha, aluno e modalidade vêm fixos. Suposição: o canvas pode ter só um dos dois pontos de entrada; ajusta-se no PR.
- D10: o selo Bolsista considera só `PERCENT` (bolsa) ativo e não vencido, não o desconto em reais, pela redação "bolsa ativa" do encaixe; o aluno com bolsa de 100% conta como bolsista e segue Ativo (RN-BEN-05). Alternativa: qualquer benefício; descartada porque "desconto" não é bolsa.
- D11: notas de versão só se o arquivo existir no repositório; senão a linha entra na descrição do PR. Item sem renumerar: se `specs/revisoes/sprint-06.md` pedir mais encaixes para #65, são tratados na construção.
- Suposição: não há encerramento sem substituição aqui (#66); a mudança de plano do aluno depois da concessão não reescreve o benefício (percentual ou valor ficam como estão).

## Construção

- T1 — arquivos: `benefit/BenefitWrite.java` (novo: preview, validação, `effectiveFrom`, `high`), `benefit/Benefits.java` (extraído `find`/`findActive`/`mapRow`, a lista usa o mesmo select), `BenefitWriteTest.java` — prova: `./backend/mvnw -f backend/pom.xml test -Dtest=BenefitWriteTest` → passou; `verify -Dit.test=BenefitGrantIT` → 7 testes, 0 falhas.
- T2 — arquivos: `BenefitController.java` (`POST /benefits`, `POST /benefits/preview`), `BenefitWrite.grant` (`for update` na modalidade, substituição atômica, 409, `benefit.granted`/`benefit.ended`), `BenefitGrantIT.java` — prova: `verify -Dit.test=BenefitGrantIT` → CA1, CA2, CA3, CA4, CA6, CA7, CA8, CA9, CA10, CA11 verdes (inclui concorrência com duas threads: 201 + 409).
- T3 — arquivos: `StudentSeal.java` (+`SCHOLARSHIP`), `Students.java` (`sealPredicate`, coluna extra no select e `timezone`/`status` movidos de 15/16 para 16/17), `StudentSealsIT.java` (+`scholarshipSeal`), `EnrollmentFixtures.clean` (+`benefit`) — prova: `verify -Dit.test=StudentSealsIT` → 8 testes, 0 falhas.
- T4 — arquivos: `docs/openapi.yaml` (duas rotas, `BenefitInput`, `BenefitPreviewInput`, `BenefitPreview`, `SCHOLARSHIP`), `mocks/handlers.ts` (benefícios em memória, preview, POST, selo), `mocks/benefits-write.msw.test.ts`, `students.msw.test.ts` — prova: `test -Dtest='OpenApiContractTest,ContractRoutesTest'` → verdes; `npx vitest run src/mocks` → verdes. Seed: `15-beneficios.sql` já tem bolsas ativas (k=1, 2); sem mudança.
- T5 — arquivos: `GrantBenefitDialog.tsx`, `useGrantBenefit.ts`, `BenefitsPage.tsx` (botão com `BENEFITS`), `ModalityPickers.tsx` (`work` passa a `Promise<unknown>`), `pt-BR.json`, `GrantBenefitDialog.test.tsx`, `BenefitsPage.test.tsx` (a asserção "sem botão conceder" da #64 virou "tem Conceder, não tem Encerrar") — prova: `npx vitest run src/features/benefits` → 15 testes verdes (carregando, erro, vazio, sem permissão, 360 px e axe nos dois temas).
- T6 — arquivos: `RecordEnrollment.tsx` (botão por modalidade ativa com `canEdit` e `canGrant`), `StudentRecordPanel.tsx` (passa `studentName` e `canGrant`, recarrega a ficha após conceder), `types.ts`, `StudentFilters.tsx`, `pt-BR.json` (`students.seals.SCHOLARSHIP` = Bolsista), `RecordEnrollment.test.tsx`, `StudentsPage.test.tsx` — prova: `npx vitest run src/features/students src/mocks` → 262 testes verdes.
- T7 — notas de versão: não existe arquivo no repositório; a linha vai na descrição do PR (D11): "Conceder benefício (bolsa em % ou desconto em reais) pela tela Benefícios e pela ficha do aluno; selo Bolsista". Regressão abaixo.

Regressão: `./backend/mvnw -f backend/pom.xml verify` → saída 0 (testes rápidos, ITs e JaCoCo); em `frontend/`: `npm run lint && npm run typecheck && npm run build && npm run test:cov` → verdes (linhas 97,26%); `npm run e2e` → 32 passaram. Sem piora em relação à linha de base.
Desvios: (1) o plano dizia reutilizar `ModalityDialog`; reutilizado, trocando só o tipo de `work` (`Promise<unknown>`). (2) `canGrant` chega por prop de `StudentRecordPanel` (que já lê as permissões), não por `usePermissions` dentro de `RecordEnrollment`. (3) `Benefits.find`/`findActive` em vez de um `get(who, id)`; a permissão e o alcance ficam em `BenefitWrite`. (4) Seed sem mudança.
Não é do item: nada.

## Verificação

### Rodada 1 — OK — 2026-10-21, estado fdb4ac5552a4ddf79f00f2e5989308a34caa146c

| Critério | Status | Evidência |
|---|---|---|
| CA1 | PASSOU | `BenefitGrantIT.grant` (7 testes, 0 falhas no `verify`) |
| CA2 | PASSOU | `BenefitGrantIT.replace`; leitura de `BenefitWrite.grant` (409 sem `replaceExisting`, encerra e insere na mesma transação) |
| CA3 | PASSOU | `BenefitGrantIT.validation`, `BenefitWriteTest` (3) |
| CA4 | PASSOU | `BenefitGrantIT.validation`/`grant`; `grantedBy` vem de `who.accountId()`, organização da sessão |
| CA5 | PASSOU | `BenefitWriteTest` (data efetiva) e `BenefitGrantIT.preview` |
| CA6 | PASSOU | `BenefitGrantIT.preview` |
| CA7 | PASSOU | `BenefitGrantIT.grant`; nenhum update fora de `benefit` no código |
| CA8 | PASSOU | `BenefitGrantIT.permissions` |
| CA9 | PASSOU | `BenefitGrantIT.isolation` |
| CA10 | PASSOU | `BenefitGrantIT.grant`/`replace` (auditoria); código audita só em sucesso |
| CA11 | PASSOU | `BenefitGrantIT.concurrent` (201 + 409) |
| CA12 | PASSOU | `StudentSealsIT` (8 testes, inclui `scholarshipSeal`) |
| CA13 | PASSOU | `GrantBenefitDialog.test.tsx`, `RecordEnrollment.test.tsx` (vitest, 891 testes verdes) |
| CA14 | PASSOU | `GrantBenefitDialog.test.tsx` (estados, 360 px, axe nos dois temas) |
| CA15 | PASSOU | `OpenApiContractTest` (4) e `ContractRoutesTest` (3); `benefits-write.msw.test.ts` |

Comandos:
- `./backend/mvnw -f backend/pom.xml verify` → BUILD SUCCESS, 500 testes, 0 falhas, JaCoCo "All coverage checks have been met".
- em `frontend/`: `npm run lint && npm run typecheck && npm run build && npm run test:cov` → verdes (99 arquivos, 891 testes; linhas 97,26%).
- `npm run e2e` → 32 passaram.

Achados:
- sugestão, código, `BenefitWrite.grant` — a validade (`validUntil`) é checada depois do 404/403 da modalidade; o resultado é correto, só a ordem dos erros difere de uma validação puramente de corpo.
- sugestão, especificação, notas de versão não existem no repositório; a linha segue para a descrição do PR (D11), como previsto.

## Entrega

- Concede benefício (bolsa em % ou desconto em reais) por `POST` de concessão no servidor, com pré-visualização de plano, nova mensalidade e data de vigência; substituição do benefício ativo só com `replaceExisting`, na mesma transação; auditoria, isolamento por organização e perfil, concorrência (201 + 409).
- Front: janela `GrantBenefitDialog` em `/beneficios` e na ficha do aluno (por modalidade ativa), com quatro estados, 360 px e axe nos dois temas; selo Bolsista na lista de alunos; OpenAPI e MSW atualizados.
- Verificação: `verify` do back-end (500 testes, JaCoCo ok); lint, typecheck, build e `test:cov` do front (891 testes); e2e 32 verdes.
- Notas de versão: não existe arquivo; linha para a descrição: "Conceder benefício (bolsa em % ou desconto em reais) pela tela Benefícios e pela ficha do aluno; selo Bolsista".
- Limitação: a validade é checada depois do 404/403 da modalidade (só ordem dos erros).
