# 358 — AuditServiceIT.writesDiffWithoutSecrets instável: '4111' coincide com carimbo de hora do log

Origem: issue #358 (back-end, Sprint 09). Branch: `item/358-audit-it-instavel`. Mesmo formato da #318 e da #320.

## 1. Objetivo

Estabilizar `AuditServiceIT.writesDiffWithoutSecrets` sem mascarar: a asserção que proíbe o cartão de teste no log não pode reprovar por coincidência com um carimbo de hora (`...4254111Z`). Fora do escopo: outros testes, mudança no `AuditService` ou no formato do log.

## 2. Requisitos

- R1: a asserção do log procura o valor sensível inteiro (`4111111111111111`), não o prefixo `4111`; carimbo de hora não a reprova.
- R2: continua provado que o valor do cartão não vai ao log nem ao `after_data` (a asserção não enfraquece).
- R3: sem retry, sem timeout, sem `@RepeatedTest` permanente.
- R4: o teste passa em execuções repetidas, em que o log tem carimbos variados.

## 3. Critérios de aceite

- CA1 (R1, R2): dado o teste corrigido, quando o log contém um carimbo com `4111`, então o teste não falha; e quando o log contém `4111111111111111`, falha.
- CA2 (R3): o diff do teste não traz retry, timeout nem repetição.
- CA3 (R4): `AuditServiceIT` passa 20 de 20 nas execuções medidas na construção.

## 4. Plano técnico

- Causa confirmada pela leitura do teste (`backend/src/test/java/br/com/ctcore/audit/AuditServiceIT.java`, método `writesDiffWithoutSecrets`): `doesNotContain("4111")` é aplicado ao `getOut()+getErr()` inteiro (que traz carimbos de hora do log) e também a `after_data`. O número de cartão do teste é `4111111111111111`, 16 dígitos; uma sequência de 16 dígitos iguais àquela não ocorre por acaso em carimbo. O `after_data` não tem carimbo, mas recebe a mesma troca por consistência.
- Mudança: nas duas asserções, trocar `"4111"` pela constante local `CARD = "4111111111111111"` (extraída no método, usada na entrada `Map.of("cardNumber", CARD)` e nas asserções). Nenhum arquivo a criar; backend de produção, dados, API, segurança e interface não mudam.
- Risco: prefixo do cartão vazando sem ser o número inteiro não seria pego; aceito, pois a regra proíbe o valor da chave sensível, que é gravado inteiro ou não.

## 5. Tarefas

- [x] T1: em `AuditServiceIT.writesDiffWithoutSecrets`, usar o cartão inteiro nas duas asserções (constante local) — cobre CA1, CA2 — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test=AuditServiceIT#writesDiffWithoutSecrets` (ou a forma equivalente do `backend/README.md`)
- [x] T2: rodar `AuditServiceIT` 20 vezes seguidas e registrar falhas/execuções — cobre CA3 — prova: laço de 20 chamadas do comando de T1, 20 de 20

## 6. Decisões

- D1: trocar `"4111"` pelo número inteiro, em vez de limitar a asserção a campos da auditoria: o log é justamente o que precisa ser provado sem o valor. Alternativa descartada: retirar a asserção do log (enfraquece CA9) ou retry (mascara).
- D2: a taxa geral da falha não foi medida (diz a issue) e não será; a causa é determinística pela leitura do código, e a prova é 20/20 após a correção.

## Construção

- T1 — arquivos: `backend/src/test/java/br/com/ctcore/audit/AuditServiceIT.java` (constante local `card` usada na entrada e nas duas asserções no lugar de `"4111"`) — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test=AuditServiceIT -Dtest=NoSuch -Dsurefire.failIfNoSpecifiedTests=false -Djacoco.skip=true` → 7 testes, 0 falhas
- T2 — mesmo comando em laço de 20 execuções → 20 de 20 passaram (0 falhas)
Regressão: `./backend/mvnw -f backend/pom.xml verify` → BUILD SUCCESS, 750 testes de integração, 0 falhas (JaCoCo passou). Front-end não alterado, não rodou.
Desvios: nenhum.

## Verificação

### Rodada 1 — OK — 2026-10-24, estado a7c5c65d81a2f09060ec794caecc7b017c07bbc5

| Critério | Status | Evidência |
|---|---|---|
| CA1 | PASSOU | diff: as duas asserções (after_data e log) usam o cartão inteiro de 16 dígitos; carimbo com `4111` não casa; `AuditServiceIT` 7 testes, 0 falhas |
| CA2 | PASSOU | diff do teste sem retry, timeout nem repetição |
| CA3 | PASSOU | 20 de 20 registrado na construção; nesta verificação, 1 execução do IT e a regressão passaram (não repeti 20 vezes) |

Comandos: `mvnw verify -Dit.test=AuditServiceIT ...` → 7 testes, 0 falhas; `mvnw verify` completo → 750 testes, 0 falhas, BUILD SUCCESS. Front-end não alterado.
Achados:
- sugestão, código, AuditServiceIT.writesDiffWithoutSecrets — prefixo do cartão vazado sem o número inteiro não seria pego (risco já aceito na spec).

## Entrega

- O que muda: `AuditServiceIT.writesDiffWithoutSecrets` passa a procurar o cartão de teste inteiro (`4111111111111111`, constante local) no `after_data` e no log, no lugar do prefixo `4111`. Só o teste muda; produção, dados, API e interface não.
- Verificação: rodada 1 OK (CA1, CA2, CA3). `AuditServiceIT` 7 testes sem falha; 20 de 20 execuções na construção; regressão `mvnw verify` com 750 testes de integração e BUILD SUCCESS.
- Decisão: o número inteiro em vez de limitar a asserção ao log (D1 do plano).
- Limitação aceita: um prefixo do cartão vazado sem o número inteiro não seria pego (risco já registrado no plano).
