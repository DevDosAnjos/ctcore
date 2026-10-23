# 331 — Teste ChargeRenegotiationIT.invalid falha entre 21h e 24h de Brasília

Origem: issue #331 (back-end, Sprint 08), achado R08-1 e R08-2 de `specs/revisoes/sprint-08.md`. Branch: `item/331-renegotiation-it-fuso`.

## 1. Objetivo

Fazer `ChargeRenegotiationIT` usar o mesmo "hoje" do servidor (o da unidade, `America/Sao_Paulo`) e provar a janela da noite com relógio fixo. Fora do escopo: código de produto; outros testes.

## 2. Requisitos

- R1: o teste não usa `LocalDate.now()` da JVM, que roda em UTC (bloco `static` de `CtcoreApplication`).
- R2: a janela em que a data UTC já é a de amanhã (22:30 de Brasília) é exercitada sempre, com `Clock` fixo.
- R3: a regra do R08-2 fica no `AGENTS.md`.

## 3. Critérios de aceite

- CA1 (R1, R2): com o relógio em 2026-10-23T01:30:00Z, `ChargeRenegotiationIT` passa; antes da correção, o mesmo relógio reprovava `invalid` na data de ontem (400 esperado, 201 recebido).
- CA2 (R1): `ChargeActionsBase.today()` é o "hoje" da unidade e `open()` o usa.
- CA3 (R3): o `AGENTS.md` diz que falha na regressão completa vira issue antes da rodada `OK` e que teste usa o fuso da unidade.

## 4. Plano técnico

Só testes e `AGENTS.md`. `ChargeActionsBase.today()` (fuso da unidade); `ChargeRenegotiationIT` importa uma `@TestConfiguration` com `Clock` fixo `@Primary` e sobrescreve `today()` com o mesmo relógio.

## 5. Tarefas

- [x] T1: reproduzir com o relógio fixo, sem mudar as datas — cobre CA1 — prova: `verify -Dit.test=ChargeRenegotiationIT` → 1 falha em `invalid` (firstDueOn 2026-10-22 aceito)
- [x] T2: `today()` na base e no IT, trocando os `LocalDate.now()` — cobre CA1, CA2 — prova: `verify -Dit.test='ChargeRenegotiationIT,ChargeCancelIT,ChargePanelIT,ChargeActionsIsolationIT'` → 14 testes, 0 falhas
- [x] T3: regra no `AGENTS.md` — cobre CA3

## 6. Decisões

- D1: relógio fixo só neste IT (contexto de teste próprio), em vez de mexer em todos os ITs; os demais usam folga de dias e não comparam com o "hoje" da unidade. Alternativa: `today()` pelo fuso sem relógio fixo; passaria, mas só exercitaria a janela da noite quando rodasse à noite.
