# 75 — RF-COB-04 · Registrar pagamento no balcão

Origem: issue #75 (Sprint 08); `docs/especificacao/10-cobrancas.md` (RF-COB-04, RN-COB-12, RN-COB-16), `07` (RN-PLA-04), `09` (RN-MAT-06). Branch: `item/75-rf-cob-04-registrar-pagamento`. Parte do que a #71 já deixou (D3, D10, D14 da #71): `Payments.register` e `POST /charges/{id}/payments` (meio, data, observação, valor conferido, recibo com número e código na mesma transação), `GET /public/receipts/{code}`, `RegisterPaymentDialog` mínimo (só o meio), `StepDone` da matrícula e os handlers MSW. Telas do canvas (`PagamentoRegistrar`, `GMPagamento`, `SiteRecibo`, `EmailPagamento`, `Recibo`) inacessíveis ao agente: o layout segue os padrões existentes e o dono confere no PR.

Dividido em **duas partes** (oito tarefas); a numeração é única. A Parte 2 só começa com a 1 concluída. Commit e PR são um só (`#75: RF-COB-04 · Registrar pagamento no balcão`, `Closes #75`), no fim da Parte 2.

| Parte | Entrega | Requisitos | Tarefas |
| --- | --- | --- | --- |
| 1 | Migração, PDF do recibo, rota do PDF, e-mail do recibo, `sendReceipt`, `actions.registerPayment`, contrato | R1 a R9, R13 (servidor) | T1 a T4 |
| 2 | Janela completa, painel, etapa 5, página pública `/recibo/:codigo`, MSW, acessibilidade, e2e | R10 a R15 | T5 a T8 |

## 1. Objetivo

O Administrador (e o Coordenador, nas suas unidades) registra o pagamento recebido no balcão pelo painel da cobrança e pela etapa final da matrícula, informando meio, data, valor (o da cobrança) e observação, e pode mandar o recibo ao pagador por e-mail. Todo pagamento tem recibo em PDF completo (RN-COB-16) com link que não expira, e o código confere na página pública. O registro e a numeração já existem desde a #71; este item entrega a janela completa, o PDF, o e-mail, a página pública e a habilitação do botão no painel.

Fora do escopo: "Recebimentos do dia" (RF-COB-08, é a #77); pagar várias cobranças de uma vez, estorno e crédito (Sprint 11); pagamento parcial (RN-COB-07); Pix com comprovante (RN-COB-13, #80); aviso por e-mail do reajuste de plano ao aluno (RN-PLA-04, fica na #78, D5); portal do aluno baixar recibo (Sprint 10); o PDF da fatura da assinatura.

## 2. Requisitos

- R1 (RF-COB-04): a janela "Registrar pagamento" pede meio (dinheiro, Pix na chave, cartão na maquininha, transferência), data (padrão hoje, nunca futura), observação (opcional, até 200 caracteres) e mostra o valor da cobrança só para leitura; a caixa "Enviar recibo por e-mail" vem marcada quando o pagador tem e-mail.
- R2 (RN-COB-12): o pagamento guarda meio e quem lançou, não há abertura nem fechamento de caixa, e o painel mostra os dois.
- R3 (RN-COB-07, RNF-12): valor diferente do da cobrança é recusado (422) e nada é gravado; cobrança que não está aberta, ou já paga, recusa com 409; duas chamadas ao mesmo tempo geram um só pagamento.
- R4 (RN-COB-16): o recibo em PDF traz logo (quando a organização tem), nome da unidade, CNPJ quando informado, endereço, número sequencial da unidade, nome e CPF de quem pagou (CPF só quando existe), aluno, referência ("Modalidade · mês/ano" ou a descrição da avulsa), valor, taxa repassada, meio, data e hora (fuso da unidade), a frase "Este recibo não é nota fiscal." e o código com o endereço `ctcore.com.br/recibo/{codigo}`.
- R5 (RN-COB-16): o PDF se baixa por `GET /api/v1/public/receipts/{code}/pdf`, sem sessão; o link não expira; código desconhecido ou malformado é 404.
- R6 (RN-COB-16): o número do recibo é sequencial por unidade, sem repetir e sem lacuna (já existe; ganha teste de concorrência e de duas unidades).
- R7 (RF-COB-04, D2): com `sendReceipt` verdadeiro e e-mail do pagador (aluno adulto ou responsável financeiro) válido, sai o e-mail "Pagamento confirmado" com o link do PDF; sem e-mail, o pagamento é registrado e a resposta diz `receiptEmail: "NO_ADDRESS"`; falha do envio nunca desfaz o pagamento.
- R8 (RN-COB-16): a página pública de conferência (`/recibo/:codigo`) mostra só unidade, número, referência, valor, data e as iniciais de quem pagou, e o botão "Baixar PDF"; código inválido mostra a mensagem de recibo não encontrado.
- R9 (RF-COB-02, D3): o painel da cobrança habilita "Registrar pagamento" quando a cobrança está aberta (`actions.registerPayment`), e mostra o link "Baixar recibo" nos pagamentos que têm recibo.
- R10 (RN-MAT-06): na etapa de conclusão da matrícula, "Registrar pagamento" abre a mesma janela completa; o aluno pode deixar para pagar depois pelo portal (sem pagamento, nada muda).
- R11 (RN-PLA-04): troca de plano e reajuste valem só nas cobranças geradas depois (já existe e tem teste); o e-mail ao aluno afetado não sai neste item (D5).
- R12: só COLLECTIONS `FULL` na unidade da cobrança registra (Coordenador e Administrador); Professor, Aluno e Responsável recebem 403; cobrança de outra organização ou de unidade fora do alcance é 404 (isolamento nas duas camadas).
- R13: o registro grava `payment.registered` na auditoria (com `receiptNumber` e se houve envio, sem a observação, sem CPF e sem e-mail).
- R14: telas com os quatro estados, a partir de 360 px, axe nos dois temas, textos em pt-BR, janela e página pública utilizáveis por teclado.
- R15: OpenAPI e handlers MSW batem com o servidor (rota nova do PDF, campos `sendReceipt`, `receiptEmail`, `actions.registerPayment`).

## 3. Critérios de aceite

- CA1 (R1, R2): dado o Administrador da unidade e uma cobrança aberta, quando ele registra com meio PIX_KEY, data de ontem e observação, então responde 201, a cobrança fica Paga, o pagamento guarda meio, data e quem lançou, e o painel mostra os três.
- CA2 (R1): dado `paidAt` futuro, observação com 201 caracteres ou meio fora dos quatro, quando registra, então 400 com o campo e nada é gravado.
- CA3 (R3): dado `amountCents` diferente do da cobrança, quando registra, então 422 e a cobrança segue aberta; dado cobrança paga, cancelada, renegociada ou em conferência, então 409 `CHARGE_NOT_PAYABLE`.
- CA4 (R3): dadas duas chamadas simultâneas na mesma cobrança, então uma responde 201 e a outra 409, há um só pagamento e um só recibo.
- CA5 (R4): dado um pagamento de mensalidade de Jiu-Jitsu referente a nov/2026 de um aluno adulto com CPF, quando o PDF é baixado, então é um PDF válido cujo texto traz unidade, CNPJ, endereço, número, nome e CPF do pagador, aluno, "Jiu-Jitsu · nov/2026", valor, meio, data e hora, a frase de não ser nota fiscal e o endereço do código.
- CA6 (R4): dado pagador menor com responsável financeiro, então o PDF traz nome e CPF do responsável e o aluno nominado à parte; dado cobrança avulsa, então a referência é a descrição; dado unidade sem CNPJ ou organização sem logo, então o PDF sai sem essas linhas, sem erro.
- CA7 (R5): dado o código de um recibo, quando `GET /public/receipts/{code}/pdf` sem sessão, então 200 `application/pdf`; dado código inexistente ou fora do formato, então 404 igual nos dois casos; dado o mesmo link depois de dias, então continua valendo.
- CA8 (R6): dados 20 pagamentos em paralelo na unidade A e 3 na B, então os números da A vão de 1 a 20 sem repetir nem pular e os da B de 1 a 3.
- CA9 (R7): dado `sendReceipt: true` e e-mail do aluno adulto, quando registra, então uma mensagem "Pagamento confirmado" é guardada para esse endereço com o link do PDF e a resposta traz `receiptEmail: "SENT"`; dado menor, então vai ao responsável financeiro.
- CA10 (R7): dado pagador sem e-mail, então 201 com `receiptEmail: "NO_ADDRESS"` e nenhuma mensagem; dado `sendReceipt: false`, então `receiptEmail: "NOT_REQUESTED"`; dado envio que falha, então o pagamento continua registrado.
- CA11 (R8): dado um código válido, quando abre `/recibo/{codigo}`, então vê unidade, número, referência, valor, data e iniciais, e nenhum nome completo, CPF ou e-mail; dado código inválido, então a mensagem de não encontrado; dado carregando e erro de rede, então os estados correspondentes.
- CA12 (R9): dada cobrança aberta, quando o painel abre, então "Registrar pagamento" está habilitado e abre a janela; dada cobrança paga, então o botão está desabilitado com o motivo e o pagamento mostra "Baixar recibo".
- CA13 (R10): dada a etapa de conclusão com cobranças abertas, quando clica em "Registrar pagamento", então abre a janela completa; ao confirmar, a cobrança aparece como paga com número do recibo; fechar sem pagar mantém a matrícula concluída.
- CA14 (R12): dado Professor, Aluno ou Responsável, quando registra, então 403; dada cobrança de outra organização ou de unidade fora do alcance do Administrador, então 404 e nada é gravado; com a linha de outra organização, o RLS não a devolve mesmo sem o filtro do código.
- CA15 (R13): dado um pagamento registrado, então existe `payment.registered` com ator, unidade, número do recibo e sem observação, CPF nem e-mail.
- CA16 (R14): dada a janela e a página pública, nos temas claro e escuro e a 360 px, então axe sem violações e a janela se opera só por teclado, com foco devolvido ao botão ao fechar.
- CA17 (R15): então o teste de contrato (`OpenApiContractTest`, `ContractRoutesTest`) passa e os testes MSW reproduzem 201, 400, 409, 422 e o PDF.
- CA18 (R11): dado um reajuste de plano, então as cobranças já geradas não mudam e as seguintes saem com o valor novo (teste existente `RepricingIT`, citado para a RN-PLA-04).

## 4. Plano técnico

- Dados: migração `V41__payment_receipt_email_pdf.sql` (próxima depois da V40): (a) amplia o `check` de `email_message.template` com `PAYMENT_CONFIRMED` (mesma técnica da V29); (b) função `receipt_pdf_data(p_code text)` `security definer`, no molde de `public_receipt` (V39), que devolve só o necessário ao PDF: organização, nome da unidade, CNPJ, endereço, fuso, nome do arquivo da logo, número, código, valor, taxa repassada, meio, data e hora, aluno, pagador (nome e CPF), tipo, referência e descrição; sem permitir listar. Reverter à mão: `drop function receipt_pdf_data(text)` e voltar o `check` ao texto da V29 mais os modelos posteriores. Sem coluna nova em `payment` ou `receipt`.
- API: `POST /charges/{id}/payments` ganha `sendReceipt` (boolean, padrão falso) na entrada e `receiptEmail` (`SENT`, `NO_ADDRESS`, `NOT_REQUESTED`, `FAILED`) na saída; `GET /public/receipts/{code}/pdf` novo, público, `application/pdf`, 404 uniforme; `GET /charges/{id}` devolve `actions.registerPayment` verdadeiro quando a cobrança está aberta. Erros e permissões como hoje (R12).
- Interface: `RegisterPaymentDialog` completo (meio, data, valor só leitura, observação com contador, caixa de e-mail, estados de envio e erro 400/409/422, tela de sucesso com número, código, "Baixar recibo" e aviso do e-mail); `ChargePanel` habilita o botão e mostra "Baixar recibo"; `StepDone` usa a janela; página pública nova `/recibo/:codigo` fora de `RequireSession`, com os quatro estados (sem permissão não se aplica: é pública); textos em `pt-BR.json`.
- Segurança: o PDF é capacidade por código de 80 bits (não enumerável, sem expiração, como pede a RN-COB-16); como traz o CPF do pagador, só o link enviado ao próprio pagador e o painel o expõem (D1); resposta 404 igual para inexistente e malformado; cabeçalhos `Cache-Control: private, no-store` e `X-Content-Type-Options: nosniff` na rota do PDF; nenhum CPF, e-mail ou observação em log ou auditoria; `sendReceipt` não confia no cliente para o destinatário (o endereço vem do cadastro).
- Arquivos (vistos no repositório): `backend/src/main/java/br/com/ctcore/payment/Payments.java` (`Input` + `sendReceipt`, `Registered` + `receiptEmail`, envio após o commit), `PaymentController.java`, `PublicReceiptController.java` (rota `/{code}/pdf`), `billing/ChargeActions.java` (`read`: `Actions.registerPayment = open`), `mail/EmailTemplate.java` (`PAYMENT_CONFIRMED`), `resources/messages.properties` (assunto e texto), `storage/FileStorage.java` e `S3FileStorage.java` (método `get(name)` para a logo), `config/SecurityConfig.java` (a linha de `/public/receipts/*` passa a cobrir `/*/pdf`), `docs/openapi.yaml` (rota, `PaymentInput`, `Payment`, `ChargeDetail.actions`). Frontend: `features/payments/RegisterPaymentDialog.tsx`, `usePayment.ts`, `features/charges/ChargePanel.tsx` (remover `registerPayment` de `DISABLED`), `types.ts`, `features/students/enrollment-wizard/StepDone.tsx`, `App.tsx` (rota pública), `i18n/pt-BR.json`, `mocks/handlers.ts`. A criar: `payment/ReceiptPdf.java` (monta o PDF), `payment/ReceiptMailer.java` (escolhe o destinatário e envia), `db/migration/V41__payment_receipt_email_pdf.sql`, `features/payments/PublicReceiptPage.tsx` + `usePublicReceipt.ts`, testes (`ReceiptPdfTest`, `PaymentReceiptIT`, `ReceiptEmailIT`, `PaymentIsolationIT`, `RegisterPaymentDialog.test.tsx`, `PublicReceiptPage.test.tsx`, e2e `pagamento.spec.ts`), seed `infra/seed/NN-recibos.sql` só se o `SeedDataIT` pedir pagamentos.
- Reuso: `Payments.register` e `ReceiptCodes`; `public_receipt` como molde da função; `EmailService.send` + `EmailLayout` + `ctcore.app.base-url` (como `EmailAccountMailer`); `OrganizationTransactions` e o contexto de organização para ler a logo; `formatMoney`, `Modal`, máscaras e `useChargeActions` no front; o mock `/charges/{id}/payments` e `/public/receipts/{code}`.
- Riscos: (1) PDF exige biblioteca nova: usar Apache PDFBox 3.x (Apache 2.0), sem outro componente; checar OSV/`npm`-equivalente no CI e `mvn verify` (nota 7 ou mais reprova); texto em acentos usa fonte embutida (Liberation/DejaVu do próprio jar ou fonte Standard 14 com WinAnsi cobre pt-BR; conferir "ç", "ã" no teste); (2) pacote `payment` exige 100% de cobertura (JaCoCo): `ReceiptPdf`, `ReceiptMailer` e todos os ramos (sem logo, sem CNPJ, menor, avulsa, sem e-mail, envio falho) precisam de teste; (3) a rota pública do PDF roda sem sessão, então a logo é lida dentro de um contexto de organização aberto com a organização que a função devolveu: conferir que não vaza para a próxima requisição (fechar no `finally`); (4) enviar e-mail dentro da transação do pagamento seguraria a trava da cobrança: o envio sai depois do commit; (5) canvas inacessível, o dono confere no PR; (6) a imagem `ctcore-backend` cresce com a biblioteca: reconstruir e remover a versão anterior (`<none>` do build), conforme o AGENTS.md.

## 5. Tarefas

Parte 1 — back-end

- [x] T1: migração V41 (`PAYMENT_CONFIRMED`, `receipt_pdf_data`), `EmailTemplate`, textos do e-mail, `FileStorage.get` e ajuste da `SecurityConfig` — cobre CA9 (base), CA7 (base) — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test=MigrationIT,EmailServiceIT -Dtest=NoTest -Dsurefire.failIfNoSpecifiedTests=false`
- [x] T2: `ReceiptPdf` (PDFBox) e rota `GET /public/receipts/{code}/pdf` com logo, CNPJ, CPF, menor, avulsa e cabeçalhos — cobre CA5, CA6, CA7 — prova: `./backend/mvnw -f backend/pom.xml verify -Dtest=ReceiptPdfTest -Dit.test=PaymentReceiptIT -Dsurefire.failIfNoSpecifiedTests=false`
- [x] T3: `Payments.register` com `sendReceipt`, `ReceiptMailer` pós-commit, `receiptEmail`, auditoria sem dado pessoal, `actions.registerPayment`; testes de validação, 422/409, concorrência e numeração por unidade — cobre CA1, CA2, CA3, CA4, CA8, CA9, CA10, CA15, CA18 — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test=PaymentIT,ReceiptEmailIT,RepricingIT -Dtest=NoTest -Dsurefire.failIfNoSpecifiedTests=false`
- [x] T4: isolamento entre organizações e entre perfis (com RLS) na rota de pagamento e na do PDF, OpenAPI e contrato — cobre CA14, CA17 (servidor) — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test=PaymentIsolationIT -Dtest=NoTest -Dsurefire.failIfNoSpecifiedTests=false` e `./backend/mvnw -f backend/pom.xml test -Dtest='OpenApiContractTest,ContractRoutesTest'`

Parte 2 — front-end

- [x] T5: `RegisterPaymentDialog` completo, `usePayment` (data, observação, `sendReceipt`), tipos e textos pt-BR — cobre CA1, CA2, CA3, CA16 (janela) — prova: `npx vitest run src/features/payments/RegisterPaymentDialog.test.tsx` em `frontend/`
- [x] T6: `ChargePanel` com botão habilitado e "Baixar recibo", `StepDone` com a janela — cobre CA12, CA13 — prova: `npx vitest run src/features/charges/ChargePanel.test.tsx src/features/students/enrollment-wizard` em `frontend/`
- [x] T7: página pública `/recibo/:codigo` com os quatro estados, e handlers MSW (`sendReceipt`, `receiptEmail`, PDF, 400/409/422) — cobre CA11, CA16 (página), CA17 — prova: `npx vitest run src/features/payments src/mocks` em `frontend/`
- [x] T8: e2e `@critical` "Registrar pagamento no balcão" (painel, janela, recibo, página pública) e regressão completa — cobre CA1, CA7, CA11, CA13 (fim a fim) — prova: `npm run e2e -- pagamento` em `frontend/`; depois `./backend/mvnw -f backend/pom.xml verify` e, em `frontend/`, `npm run lint && npm run typecheck && npm run build && npm run test:cov && npm run e2e`

## 6. Decisões

- D1 (muda o comportamento): o PDF do recibo é aberto por quem tem o link com o código (80 bits), sem sessão, porque a RN-COB-16 diz que o link não expira e o e-mail precisa levá-lo; a página de conferência mostra só as iniciais e o PDF traz o nome e o CPF completos, como a regra manda. Alternativa: PDF só com sessão (o pagador sem conta não abriria o link do e-mail) ou CPF mascarado no PDF (contraria o texto da regra).
- D2 (muda o comportamento): "enviar recibo por e-mail" manda a mensagem com o link do PDF, não o PDF anexado: o `EmailSender` não tem anexos e o e-mail guardado (RNF-14) é texto. Alternativa: estender `EmailSender` e as versões simulada e real com anexo, o que mexe numa porta fora do item.
- D3: `actions.registerPayment` passa a valer `true` só para cobrança aberta (era `false` pela D2 da #73); o botão sai da lista `DISABLED` do painel. Estornar e Enviar lembrete seguem desabilitados.
- D4: a data informada vira o instante do pagamento no fuso da unidade (data escolhida, hora atual do dia; hoje usa o instante atual). Pagamento em data passada é aceito sem limite inferior; só o futuro é recusado. Alternativa: limitar a 30 dias; a especificação não pede.
- D5 (muda o comportamento): o e-mail de reajuste ao aluno (RN-PLA-04) continua na #78, como a D9 da #71 decidiu; esta issue cita a RN-PLA-04 porque o plano do canvas mostra o aviso, e o que existe (contagem, vigência na próxima cobrança, `RepricingIT`) é o que o teste cobre aqui. Alternativa: enviar o e-mail agora, duplicando a base de e-mails da #78.
- D6: o e-mail "Pagamento confirmado" é o primeiro modelo de cobrança; a #78 pode movê-lo para o registro de e-mails automáticos sem mudar o contrato. O destinatário é o pagador da cobrança (aluno adulto ou responsável financeiro); endereço com `email_problem_at` preenchido ainda recebe (RN-COM-13: recibo sempre chega), a #78 trata o retorno.
- D7: o envio sai depois do commit da transação do pagamento e nunca a desfaz; `receiptEmail: "FAILED"` aparece na janela, com a nova tentativa pelo mecanismo de reenvio existente (`EmailRetryJob`). Alternativa: enviar dentro da transação, que seguraria a trava da cobrança.
- D8: biblioteca de PDF: Apache PDFBox 3.x (licença Apache 2.0, sem dependência nativa). Alternativa: OpenPDF (LGPL/MPL), descartada por licença; gerar HTML e converter no navegador, descartada porque o recibo é gerado no servidor para o link público.
- D9: sem recurso liberado por organização (a sprint não o pede); notas de versão só se o arquivo existir, senão a linha vai na descrição do PR.
- D10: a página pública `/recibo/:codigo` entra neste item (a #71 a atribuiu à #75, na suposição final) como página do próprio aplicativo; o domínio `ctcore.com.br` e o site institucional são das Sprints 22/23. O endereço impresso no PDF usa `ctcore.com.br/recibo/{codigo}` fixo, e o link do e-mail usa `ctcore.app.base-url`.
- Suposição: o pagador sem CPF (aluno adulto que não informou) sai no PDF só com o nome; se o dono quiser CPF obrigatório para emitir recibo, a regra muda no cadastro, não aqui.
- Suposição: "taxa repassada" é `payment.fee_passed_cents` (0 no balcão), mostrada como R$ 0,00.
- Suposição: as telas e o PDF do canvas não foram vistas; o dono confere no PR e pede ajuste de layout.
- Fora do item, sugestões: (a) na #78, mover o e-mail "Pagamento confirmado" e incluir os e-mails de reajuste (D5); (b) na Sprint 10, o portal baixar o recibo pelo mesmo endereço do PDF (RF-POR-05).

## Construção

Parte 1 (T1 a T4) e Parte 2 (T5 a T8) concluídas.

- T1 — arquivos: `backend/pom.xml` (PDFBox 3.0.5), `db/migration/V41__payment_receipt_email_pdf.sql`, `EmailTemplate.java`, `messages.properties`, `FileStorage.java` e `S3FileStorage.java` (`get`), `SecurityConfig.java` (`/public/receipts/*/pdf`) — prova: coberta pelo `verify` completo (a migração sobe em todos os `*IT`; `PAYMENT_CONFIRMED` gravado em `ReceiptEmailIT`) → 625 testes, 0 falhas
- T2 — arquivos: `payment/ReceiptPdf.java`, `PublicReceiptController.java` (rota `/{code}/pdf`, logo lida em contexto de organização fechado no fim), `ReceiptPdfTest`, `PaymentReceiptIT` — prova: `verify` completo → passou (texto do PDF, imagem da logo, menor, avulsa, sem CNPJ, logo ausente, 404 uniforme, cabeçalhos, contexto fechado)
- T3 — arquivos: `Payments.java` (`sendReceipt`, `receiptEmail`, envio após o commit, auditoria com `receiptRequested`), `ReceiptMailer.java`, `ChargeActions.java` (`registerPayment = open`), `ReceiptEmailIT`, `PaymentIsolationIT` (20 + 3 em paralelo) — prova: `verify` completo → passou; `PaymentIT` já cobria validação, 422/409, corrida e auditoria
- T4 — arquivos: `docs/openapi.yaml` (rota do PDF, `sendReceipt`, `receiptEmail`, `actions.registerPayment`), `PaymentIsolationIT` (PDF por organização e RLS) — prova: `OpenApiContractTest,ContractRoutesTest` → 7 testes, 0 falhas; isolamento dentro do `verify`
Regressão: `./backend/mvnw -f backend/pom.xml verify` → BUILD SUCCESS, 625 testes, 0 falhas, JaCoCo (100% em `payment`) ok. Front-end não tocado nesta parte (não rodou).
Desvios: (1) o CNPJ vem do banco só com dígitos e o PDF o formata; (2) `ReceiptEmailIT` e outros nomes de prova (`MigrationIT`, `EmailServiceIT`, `RepricingIT`) foram cobertos pelo `verify` completo, não por seleção; (3) `receiptEmail: "SENT"` também vale para mensagem guardada com envio desligado (`DISABLED`); `FAILED` quando a mensagem fica `PENDING`/`FAILED`; (4) `ReceiptPdf.render` declara `IOException` em vez de embrulhá-la (ramo inalcançável sem cobertura); (5) imagem `ctcore-backend` não reconstruída nesta parte (risco 6 do plano): fazer na Parte 2.
Não é do item: nenhum.

Parte 2
- T5 — arquivos: `payments/RegisterPaymentDialog.tsx` (valor só leitura, data até hoje, observação com contador, caixa de e-mail, erros 400/409/422, sucesso com aviso do e-mail e "Baixar recibo"), `payments/usePayment.ts`, `i18n/pt-BR.json`, `RegisterPaymentDialog.test.tsx`, testes da janela em `ChargePanel.test.tsx` — prova: `npx vitest run src/features/payments src/features/charges/ChargePanel.test.tsx src/mocks/payment.msw.test.ts --maxWorkers=2` → passou (axe claro e escuro dentro do teste)
- T6 — arquivos: `charges/ChargePanel.tsx` (botão habilitado por `actions.registerPayment`, link "Baixar recibo", recarrega ao fechar a janela após pagar), `ChargePanel.test.tsx`; `StepDone.tsx` já usava a janela (sem mudança) — prova: `npx vitest run src/features/payments src/features/students/enrollment-wizard src/features/charges --maxWorkers=2` → 7 arquivos, 72 testes, 0 falhas
- T7 — arquivos: `payments/PublicReceiptPage.tsx` + `PublicReceiptPage.test.tsx` (rota pública `/recibo/:codigo` em `App.tsx`; carregando, não encontrado, erro, pronto; axe nos dois temas), `mocks/handlers.ts` (`sendReceipt`/`receiptEmail`, meio e observação guardados, `actions.registerPayment`, rota do PDF), `mocks/payment.msw.test.ts` (201, 400, 422, 409, PDF, 404), `charge-actions.msw.test.ts` (linha 58: `registerPayment` agora `true`) — prova: mesma execução acima → passou
- T8 — arquivos: `e2e/pagamento.spec.ts` — prova: `npm run e2e -- pagamento` → 1 passou
Regressão (Parte 2): em `frontend/` `npm run lint && npm run typecheck && npm run build` → ok; `npx vitest run --coverage --maxWorkers=2` (suíte completa, uma vez) → 119 arquivos, 1041 testes, 0 falhas, linhas de `features/payments` 100%, limiares ok; `npm run e2e` → 35 passaram. Back-end não alterado nesta parte: o `verify` da Parte 1 (625 testes) vale; não foi repetido.
Desvios (Parte 2): (6) a caixa "Enviar recibo por e-mail" vem sempre marcada, porque o painel não recebe o e-mail do pagador (`payer` não o traz); sem e-mail o servidor responde `NO_ADDRESS` e a janela avisa. (7) data de dias anteriores vai como `paidAt` ao meio-dia local; a de hoje vai sem `paidAt` (o servidor usa o instante atual). (8) imagem `ctcore-backend` construída (não existia antes; nenhum `<none>` deixado); `ctcore-frontend` não reconstruída. (9) a janela da etapa 5 já existia e foi ampliada no próprio componente; os testes antigos do assistente seguem passando.

## Verificação

### Rodada 1 — OK — 2026-10-22, estado 86181d97bb5a3a89a0eb80a3293d0351251c6f1c

| Critério | Status | Evidência |
|---|---|---|
| CA1, CA2, CA3, CA4 | PASSOU | `PaymentIT` (registers, rejects, notPayable, raceAndExternalId) dentro do `verify` completo |
| CA5, CA6, CA7 | PASSOU | `ReceiptPdfTest`, `PaymentReceiptIT` (adulto com logo, menor/avulsa/sem CNPJ, logo ausente, 404 uniforme, cabeçalhos) |
| CA8 | PASSOU | `PaymentIsolationIT.numbersPerUnit` (20 + 3 em paralelo); `PaymentIT.concurrentNumbers` |
| CA9, CA10 | PASSOU | `ReceiptEmailIT` (sent, noAddress, failure) |
| CA11 | PASSOU | `PublicReceiptPage.test.tsx` (pronto, não encontrado, carregando, erro) |
| CA12, CA13 | PASSOU | `ChargePanel.test.tsx`, testes do assistente, e2e `pagamento.spec.ts` |
| CA14 | PASSOU | `PaymentIT.permissions/isolation`, `PaymentIsolationIT.pdfAndRls` |
| CA15 | PASSOU | `PaymentIT.audit` (receiptNumber, sem observação/CPF/e-mail) |
| CA16 | PASSOU | axe claro e escuro em `RegisterPaymentDialog.test.tsx` e `PublicReceiptPage.test.tsx`; 360 px e foco não exercitados por teste dedicado além do e2e |
| CA17 | PASSOU | `OpenApiContractTest`/`ContractRoutesTest` no `verify`; `payment.msw.test.ts` |
| CA18 | PASSOU | `RepricingIT` no `verify` |

Comandos:
- `./backend/mvnw -f backend/pom.xml verify` → BUILD SUCCESS, 625 testes (149 unitários), 0 falhas, JaCoCo ok.
- `npm run lint && npm run typecheck && npm run build` → ok.
- `npx vitest run --coverage --maxWorkers=2` (uma vez) → 119 arquivos, 1041 testes, 0 falhas, linhas de `features/payments` 100%.
- `npm run e2e` → 35 passaram.
Instáveis: nenhum.
Achados:
- sugestão, código, `RegisterPaymentDialog.tsx` (`useState(true)` de `sendReceipt`) — R1 pede a caixa marcada só quando o pagador tem e-mail; o desvio 6 está registrado e o servidor responde `NO_ADDRESS` com aviso na janela.
- sugestão, código, `ReceiptPdf.safe` — caractere fora do WinAnsi vira "?" (nome com caracteres não latinos); aceitável hoje, anotar para quando houver fonte embutida.

## Entrega

- O que muda: janela "Registrar pagamento" completa (meio, data, observação, valor só leitura, caixa de e-mail), registro com recibo numerado na mesma transação, PDF do recibo com logo, CNPJ e CPF quando existem, rota pública do PDF, e-mail "Pagamento confirmado" (falha de envio não desfaz o pagamento; sem e-mail responde `NO_ADDRESS`), página pública `/recibo/:codigo` com os quatro estados, botão "Registrar pagamento" no painel da cobrança e na etapa 5 da matrícula, "Baixar recibo" nos pagamentos, migração V41, contrato OpenAPI e handlers MSW.
- Verificação: rodada 1 OK (estado `86181d97bb5a3a89a0eb80a3293d0351251c6f1c`). `./backend/mvnw -f backend/pom.xml verify` com BUILD SUCCESS (625 testes, 0 falhas, JaCoCo ok); `npm run lint && npm run typecheck && npm run build` ok; `npx vitest run --coverage` com 1041 testes e 0 falhas, `features/payments` 100%; `npm run e2e` com 35 passando.
- Decisões: a caixa de e-mail vem marcada mesmo sem e-mail do pagador, porque o painel não recebe o e-mail (desvio 6 da especificação); sem e-mail o servidor responde `NO_ADDRESS` e a janela avisa. Data de dias anteriores vai como `paidAt` ao meio-dia local; a de hoje vai sem `paidAt`.
- Limitações: a tela do canvas não foi acessível ao agente, então o layout segue os padrões existentes e o dono confere no PR. Caractere fora do WinAnsi vira "?" no PDF (anotado para quando houver fonte embutida). Imagem `ctcore-backend` construída; `ctcore-frontend` não reconstruída.
