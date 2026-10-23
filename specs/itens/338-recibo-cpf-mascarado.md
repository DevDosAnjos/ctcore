# 338 — Recibo: CPF mascarado no PDF do link público e completo só com sessão

Origem: issue #338 (back-end e front-end, Sprint 08), decisão D1 do dono na revisão da Sprint 08 (`specs/revisoes/sprint-08.md`); RN-COB-16 já ajustada (PR #337). Branch: `item/338-recibo-cpf-mascarado`.

## 1. Objetivo

O PDF do recibo aberto pelo link público (e-mail e página de conferência) imprime o CPF mascarado (`***.456.789-**`); o CPF completo só para quem tem sessão e acesso a cobranças da unidade do recibo. Fora do escopo: página pública de conferência (já mostra só iniciais), e-mail.

## 2. Requisitos

- R1: `GET /api/v1/public/receipts/{code}/pdf` imprime o CPF mascarado (CPF fora do padrão vira `***`).
- R2: `GET /api/v1/receipts/{code}/pdf` (sessão) imprime o CPF completo; sem sessão 401; sem acesso a Cobranças e pagamentos 403; outra organização, unidade fora do alcance ou código desconhecido: o mesmo 404.
- R3: o painel da cobrança e a janela de pagamento baixam o recibo pela rota com sessão; a página pública segue na pública.
- R4: OpenAPI, MSW e testes batem.

## 3. Critérios de aceite

- CA1 (R1): `ReceiptPdfTest.maskedCpf` e `PaymentReceiptIT` (adulto e menor) leem o texto do PDF público e não acham os 11 dígitos nem o CPF formatado.
- CA2 (R2): `PaymentReceiptIT.staffGetsFullCpf` (Coordenador e Administrador veem o completo; o público segue mascarado) e `staffRouteAccess` (401, 403 do Professor, 404 de outra organização, de outra unidade e de código desconhecido, com o mesmo corpo).
- CA3 (R3): `ChargePanel.test.tsx` confere o `href` da rota com sessão; `PublicReceiptPage.test.tsx` segue com a pública.
- CA4 (R4): `OpenApiContractTest`, `ContractRoutesTest` e `payment.msw.test.ts` passam.

## 4. Plano técnico

`ReceiptFiles` (novo) concentra a consulta `receipt_pdf_data`, a logo e a montagem do PDF; `PublicReceiptController` delega (CPF mascarado) e `ReceiptStaffController` (novo) entrega com sessão. Sem migração: a unidade do recibo vem de `receipt.unit_id` sob a RLS. Front: `receiptStaffPdfUrl` em `usePayment.ts`.

## 5. Tarefas

- [x] T1: `ReceiptPdf.maskedCpf` e o parâmetro `fullCpf` — cobre CA1
- [x] T2: `ReceiptFiles`, `ReceiptStaffController`, OpenAPI — cobre CA2, CA4
- [x] T3: front, handler MSW e testes — cobre CA3, CA4

## 6. Decisões

- D1: a rota com sessão fica em `/receipts/{code}/pdf`, fora de `/public`, em vez de um parâmetro no PDF público (um parâmetro com sessão opcional misturaria os dois). Alternativa: `/charges/{id}/payments/{pid}/receipt`; exigiria o id do pagamento no painel, que hoje só tem o código.
- D2: a leitura do PDF completo não grava auditoria (só leitura, sem mudança de dado).
