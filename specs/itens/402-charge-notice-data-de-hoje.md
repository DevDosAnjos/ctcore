# 402 — Teste ChargeNoticeIT e BillingFixtures dependem da data de hoje: remover o vencimento fixo de 2026-11-10

Origem: issue #402 (back-end, Sprint 10), achado R10-2 da revisão da Sprint 10 (`specs/revisoes/sprint-10.md`); mesma classe da #390. Branch: `item/402-charge-notice-data-de-hoje`.

## 1. Objetivo

Fazer `ChargeNoticeIT` e os ITs que usam `BillingFixtures.charge` passarem em qualquer data do relógio do sistema, sem depender da janela de 10 dias do `ChargeNoticeJob` agendado (relógio real). Fora do escopo: código de produto e outros testes que não criam cobrança vencendo perto de hoje.

## 2. Requisitos

- R1: `BillingFixtures.charge` não cria cobrança que entre na janela do agendador real (até 10 dias a partir de hoje, ou já vencida) em nenhuma data realista de execução (D1).
- R2: `ChargeNoticeIT` não depende da data do sistema: o relógio do job e os vencimentos do teste ficam juntos, num período que o agendador real nunca alcança (D2); sem retry nem espera.
- R3: nenhum teste alterado compara a data da JVM com a data da unidade.
- R4: `ChargeNoticeIT` verde com o relógio do contexto adiantado para 2026-11-01 e 2026-12-01, e 20 de 20 execuções; `verify` completo verde.

## 3. Critérios de aceite

- CA1 (R1): dado `BillingFixtures.charge` com qualquer parâmetro, então `due_on` é `2100-01-10`, e `select ... due_on <= current_date + 10` não a devolve.
- CA2 (R2): dado `ChargeNoticeIT` com o relógio do sistema em qualquer data, então as 7 asserções atuais passam com o job de relógio fixo em 2100-01-05 e vencimentos 2100-01-08 a 2100-01-15 (e 2100-02-04 para "30 dias").
- CA3 (R4): dado o `Clock` do contexto trocado (temporariamente) por 2026-11-01T12:00Z e depois 2026-12-01T12:00Z, então `ChargeNoticeIT` passa; prova registrada no PR.
- CA4 (R4): `ChargeNoticeIT` repetido 20 vezes, 0 falhas de asserção; `verify` completo verde.
- CA5 (R3): `grep` das datas `2026-` nos arquivos tocados não acha comparação de data da JVM com a da unidade.

## 4. Plano técnico

- Causa (vista no código): `BillingFixtures.charge` (linha 69) insere `due_on = date '2026-11-10'`; a partir de 2026-10-31 isso entra na janela do `ChargeNoticeJob` (`due_on <= hoje_da_unidade + 10`) e, depois de 10/11, fica vencida: o `@Scheduled` (relógio `Clock.systemUTC()`, `ClockConfig`) avisa e marca `notified_at` em cobranças de qualquer IT. `ChargeNoticeIT` usa `Clock.fixed(2026-10-05)` só no job do teste e vencimentos 2026-10-xx, que já são passado para o relógio real.
- `backend/src/test/java/br/com/ctcore/billing/BillingFixtures.java`: em `charge`, trocar `date '2026-11-10'` por uma constante `FAR_DUE` (`2100-01-10`) usada como parâmetro; sem outra mudança de assinatura (187 chamadas).
- `backend/src/test/java/br/com/ctcore/billing/BillingSchemaIT.java` linha 52: mesmo vencimento fixo em insert próprio; usar `BillingFixtures.FAR_DUE`.
- `backend/src/test/java/br/com/ctcore/billing/ChargeNoticeIT.java`: relógio do job `2100-01-05T12:00:00Z` (constante única, também no `errorIsolated`); vencimentos `2026-10-08/09/10/12/15` viram `2100-01-08/09/10/12/15` e `2026-11-04` vira `2100-02-04` (mesmos deslocamentos de 3, 4, 5, 7, 10 e 30 dias); `minor()` mantém a ordem criada por #390 com a data nova. Não desligar o agendador: com datas em 2100 ele nunca vê essas linhas.
- Prova da CA3: troca temporária (não commitada) do relógio do contexto por um `@TestConfiguration` com `@Primary Clock` fixo (padrão de `ChargeRenegotiationIT.NightClock`) nas duas datas.
- Se algum IT que usa o fixture depender de `2026-11-10` ou do vencimento próximo (por exemplo `OneOffChargeIT`, `Charges*IT`, `Portal*IT`), o `verify` mostra: ajustar o vencimento no próprio IT (`update charge set due_on = ...`), não o fixture.
- Dados, API, segurança, interface: não mudam. Risco: cobranças com vencimento em 2100 alterarem alguma ordenação ou totalização esperada; o `verify` completo cobre.

## 5. Tarefas

- [x] T1: `BillingFixtures.FAR_DUE` e uso em `charge`; ajustar `BillingSchemaIT` linha 52 — cobre CA1 — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test='BillingSchemaIT,ChargesIT,OneOffChargeIT' -Dtest=NoSuchTest -Dsurefire.failIfNoSpecifiedTests=false -Dfailsafe.failIfNoSpecifiedTests=false`
- [x] T2: `ChargeNoticeIT` com relógio e vencimentos em 2100 — cobre CA2 e CA5 — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test=ChargeNoticeIT -Dtest=NoSuchTest -Dsurefire.failIfNoSpecifiedTests=false -Dfailsafe.failIfNoSpecifiedTests=false`
- [x] T3: provar com o `Clock` do contexto em 2026-11-01 e 2026-12-01 (troca temporária, desfeita) e repetir 20 vezes — cobre CA3 e CA4 — prova: mesma classe, três rodadas; laço de 20
- [x] T4: `verify` completo, corrigindo nos ITs (não no fixture) o que dependia de `2026-11-10` — cobre CA4 — prova: `./backend/mvnw -f backend/pom.xml clean verify`

## 6. Decisões

- D1: o vencimento padrão do fixture é a constante `2100-01-10`, não "hoje + N dias": fica fora da janela do agendador real em qualquer data e dispensa calcular o fuso da unidade no teste (o que reabriria a comparação JVM x unidade). Alternativa: calcular a partir de hoje no fuso da unidade, como a issue sugere; descartada por voltar a depender do relógio. A issue admite "ou cada IT passa o vencimento"; a constante cumpre o mesmo objetivo.
- D2: `ChargeNoticeIT` move relógio e vencimentos para 2100, em vez de desligar o agendador (alteraria o contexto compartilhado) ou de usar o relógio fixo também no agendador (outros contextos em cache seguem com relógio real e veriam as linhas). Alternativa descartada: vencimentos relativos a hoje com relógio de teste adiantado, que ainda cairia na janela de outros contextos.
- D3 (suposição): ninguém depende de `2026-11-10` nos ITs; a busca por `2026-11-10` achou só o fixture, `BillingSchemaIT` e usos sem relação (`OneOffChargeIT` relógio, `PortalScheduleIT` aula). Se estiver errada, o `verify` aponta e o ajuste é local ao IT (T4).
- Pedido de cobertura: `specs/backlog.md` não tem a linha do item #402; o `maestro` deve acrescentá-la (Sprint 10, a fazer).

## Construção

- T1 — arquivos: `backend/src/test/java/br/com/ctcore/billing/BillingFixtures.java` (`FAR_DUE = "2100-01-10"` usado em `charge`), `BillingSchemaIT.java` (insert usa `BillingFixtures.FAR_DUE`) — prova: `verify -Dit.test='BillingSchemaIT,ChargesIT,OneOffChargeIT,ChargeNoticeIT'` → 25 testes, 0 falhas
- T2 — arquivos: `ChargeNoticeIT.java` (relógio do job 2100-01-05T12:00Z nas duas ocorrências; vencimentos 2100-01-08/09/10/12/15 e 2100-02-04; `reference_month` 2100-01-01) — prova: mesma rodada → ChargeNoticeIT 7 de 7; `grep 2026` nos três arquivos → nada (CA5)
- T3 — prova: `Clock` primário temporário (`@Import` de `@TestConfiguration`, desfeito; arquivo restaurado) em 2026-11-01T12:00Z e 2026-12-01T12:00Z → ChargeNoticeIT 7 de 7 nas duas; laço de 20 execuções `failsafe:integration-test -Dit.test=ChargeNoticeIT` → 0 falhas
- T4 — prova: `./backend/mvnw -f backend/pom.xml clean verify` → BUILD SUCCESS (11 min 40 s), nenhum IT precisou de ajuste (D3 confirmada)
Regressão: só back-end alterado (testes); front-end não rodado, sem mudança nele.
Desvios: `ChargeNoticeIT` ganhou 2100-01-01 em `reference_month` (consistência, não pedido); o laço de 20 usou `failsafe:integration-test` direto, sem recompilar.
Não é do item: `specs/backlog.md`, `specs/revisoes/sprint-10.md` já alterados antes da sessão.

## Verificação

### Rodada 1 — OK — 2026-10-28, estado df2a9f7fec015ec35839c0a27049a859bd0edd9e

| Critério | Status | Evidência |
|---|---|---|
| CA1 | PASSOU | diff: `FAR_DUE = "2100-01-10"` em `charge` e em `BillingSchemaIT`; `verify` completo verde (BillingSchemaIT 2 de 2); 2100 está fora de `current_date + 10` |
| CA2 | PASSOU | `ChargeNoticeIT` 7 de 7 no `verify`; relógio do job 2100-01-05 nas duas ocorrências, vencimentos 2100-01-08..15 e 2100-02-04 (lidos no diff) |
| CA3 | PASSOU | conferido por leitura: relógio do job e todos os vencimentos são literais de 2100, sem relação com o `Clock` do contexto nem com o dia real; troca temporária do relógio não refeita nesta rodada (não altero código) |
| CA4 | PASSOU | `verify` completo: 859 testes, 0 falhas, BUILD SUCCESS (11 min 51 s); 3 repetições extras de `failsafe:integration-test -Dit.test=ChargeNoticeIT` sem erro (saída em modo quieto); o laço de 20 é do relato da construção |
| CA5 | PASSOU | `grep 2026` nos três arquivos tocados: nenhuma ocorrência; nenhum teste compara data da JVM com a da unidade |

Comandos: `./backend/mvnw -f backend/pom.xml verify` → 859 testes, 0 falhas, BUILD SUCCESS; `failsafe:integration-test -Dit.test=ChargeNoticeIT` x3 → sem falha. Front-end não rodado (sem mudança nele).
Achados:
- sugestão, código, `ChargeNoticeIT` `reference_month = date '2100-01-01'` — desvio já registrado, coerente; sem ação.

## Entrega

- O que muda: `BillingFixtures.charge` usa vencimento fixo `2100-01-10` (`FAR_DUE`) em vez de `2026-11-10`; `BillingSchemaIT` usa a mesma constante; `ChargeNoticeIT` roda com relógio do job em `2100-01-05` e vencimentos de 2100 (mesmos deslocamentos). Nenhuma mudança de produto.
- Verificação: rodada 1 OK (estado df2a9f7): `verify` completo com 859 testes verdes; `ChargeNoticeIT` 7 de 7; 3 repetições extras do `ChargeNoticeIT` sem falha; `grep 2026` nos arquivos tocados vazio.
- Limitação: a prova de relógio adiantado (2026-11-01 e 2026-12-01) e o laço de 20 execuções foram feitos na construção, não repetidos na verificação.
- Arquivo `specs/revisoes/sprint-10.md` e as linhas 403 a 405 do backlog já estavam alterados antes do item; entram no mesmo commit por estarem na pasta de trabalho.
