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

## Construção

Registro depois do fato (ajuste A2 da revisão da Sprint 09, `specs/revisoes/sprint-09.md`): o item foi mesclado em `dev` (PR #353, commit `8cd3b29`) sem Construção, Verificação nem Entrega. Esta seção foi escrita a partir do que já está em `dev`; nenhum código foi alterado nesta sessão e nenhum teste foi rodado de novo.

- T1 — arquivos: `backend/src/test/java/br/com/ctcore/billing/ChargeNoticeIT.java` (+4 linhas no teste `text()`: terceira cobrança com `description` e `reference_month` nulos, e `assertThat(sent("notice-texto3%")).hasSize(1)`) — prova registrada na tarefa: `verify -Dit.test=ChargeNoticeIT` → 7 testes, 0 falhas; `ChargeNoticeJob` com 0 linhas perdidas. Origem desse resultado: execução da época, não repetida aqui; o commit confirma só o diff.
Regressão: não registrada na época; o PR #353 foi mesclado com o `ci-gate` exigido, mas o resultado não está neste arquivo.
Desvios: nenhum no código (o diff bate com o plano técnico). Desvio de processo: Construção, Verificação e Entrega não foram escritas antes do merge.
Não é do item: `specs/backlog.md` alterado no mesmo commit (espelho do item).

## Verificação

### Rodada 1 — OK — 2026-10-24, estado 7049aee9554d5afb4103b3ff01cbed23d95e87eb

Verificação depois do fato (ajuste A2), sobre o estado atual de `dev` mais este arquivo; o código do item é o commit `8cd3b29` (+4 linhas em `ChargeNoticeIT`), lido no diff e igual ao plano técnico.

| Critério | Status | Evidência |
|---|---|---|
| CA1 | PASSOU | `ChargeNoticeIT`: Tests run: 7, Failures: 0, Errors: 0; o caso novo afirma `sent("notice-texto3%")` com 1 registro |
| CA2 | PASSOU | `target/site/jacoco/jacoco.csv`, `billing.ChargeNoticeJob`: LINE_MISSED 0, LINE_COVERED 36; `jacoco:check`: "All coverage checks have been met" |

Comandos: `./backend/mvnw -f backend/pom.xml verify -Dit.test=ChargeNoticeIT -Dtest=NoSuchTest -Dsurefire.failIfNoSpecifiedTests=false -Dfailsafe.failIfNoSpecifiedTests=false` → BUILD SUCCESS (log em `%TEMP%/v352.log`). Regressão completa (`verify` inteiro, front-end) não rodada nesta verificação, por pedido: o escopo é o IT com cobertura; o item não toca código de produto.
Achados:
- sugestão, código, `ChargeNoticeIT` `text()` — o teste afirma só que o aviso foi gravado; o ramo "Cobrança" é provado pela cobertura, não por asserção do texto. Aceitável porque o assunto não leva a descrição.

## Entrega

- Mudança: só registro. O código e o teste do item (`ChargeNoticeIT`, +4 linhas) já estão em `dev` desde o PR #353 (commit `8cd3b29`); este PR acrescenta Construção, Verificação e Entrega neste arquivo, sem código de produto.
- Verificação: rodada 1 `OK` (CA1: 7 testes passam; CA2: `ChargeNoticeJob` com 0 linhas perdidas no JaCoCo), sobre o estado `7049aee9`.
- Decisão a conhecer: o item foi mesclado sem as seções de processo; este PR é o registro depois do fato (ajuste A2 da revisão da Sprint 09).
- Limitação: a regressão completa não foi rodada nesta entrega; o `ci-gate` do PR #353 é a única prova de regressão registrada.
