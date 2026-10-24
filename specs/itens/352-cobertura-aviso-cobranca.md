# 352 — Cobertura de billing cai para 0,99 conforme o horário

Origem: issue #352 (back-end, Sprint 09), regressão vermelha do #80. Branch: `item/352-cobertura-aviso-cobranca`.

## 1. Objetivo

Cobrir com teste direto o ramo `"Cobrança"` de `ChargeNoticeJob` (cobrança sem descrição e sem mês de referência), que só era coberto se o `@Scheduled` disparasse durante outro teste. Fora do escopo: código de produto.

## 2. Requisitos

- R1: `ChargeNoticeIT` executa o ramo e afirma o aviso gravado.
- R2: `billing` fecha em 100% no JaCoCo sem depender do agendador.

## 3. Critérios de aceite

- CA1 (R1): `ChargeNoticeIT` passa com 7 testes e o caso novo.
- CA2 (R2): rodando só o `ChargeNoticeIT`, `ChargeNoticeJob` tem 0 linhas perdidas no relatório do JaCoCo.

## 4. Plano técnico

Um caso a mais no teste `text()` de `ChargeNoticeIT`: terceira cobrança com `description` e `reference_month` nulos. O assunto do e-mail não leva a descrição, então o teste afirma que o aviso foi gravado.

## 5. Tarefas

- [x] T1: caso novo em `ChargeNoticeIT` — cobre CA1, CA2 — prova: `verify -Dit.test=ChargeNoticeIT` → 7 testes, 0 falhas; `ChargeNoticeJob` com 0 linhas perdidas

## 6. Decisões

- D1: a causa é cobertura por acaso (o agendador de 15 minutos rodando dentro de outra classe); não houve mudança de produto. Medido às 23:37 de Brasília: `verify` do #80 com `billing` em 0,99.
