# 390 — ChargeNoticeIT.minor instável: o agendador envia o aviso ao aluno menor antes de o teste ligar o responsável

Origem: issue #390 (back-end, Sprint 10), mesma classe da #352 (agendador x `runOnce`). Branch: `item/390-charge-notice-minor-instavel`.

## 1. Objetivo

Fazer `ChargeNoticeIT.minor` passar sempre, eliminando a janela em que a cobrança do menor fica visível ao `@Scheduled` de `ChargeNoticeJob` (relógio real) sem `payer_guardian_id`. Fora do escopo: código de produto, outros testes da classe.

## 2. Requisitos

- R1: no teste `minor`, a cobrança só passa a existir (visível a outra conexão) já com `payer_guardian_id` preenchido e `due_on` dentro da janela, sem retry nem espera (D1).
- R2: `ChargeNoticeIT` passa 20 de 20 execuções e o `verify` completo passa.

## 3. Critérios de aceite

- CA1 (R1): dado o menor com responsável financeiro, quando o agendador roda em qualquer instante do teste, então o aviso vai só ao responsável e `sent("notice-menor%")` é vazio.
- CA2 (R2): dado `ChargeNoticeIT` repetido 20 vezes, então 0 falhas; `verify` completo verde.

## 4. Plano técnico

- Causa (vista em `ChargeNoticeIT.java`): `minor()` chama `charge(...)`, que faz `insert` (due 2026-11-10, fora da janela) e depois `update due_on = 2026-10-08` (vira devida para o relógio real); só então o teste faz `update payer_guardian_id`. Entre o segundo e o terceiro comando o agendador avisa o aluno.
- Mudança: só `backend/src/test/java/br/com/ctcore/billing/ChargeNoticeIT.java`, método `minor()`: montar a cobrança com `payer_guardian_id` e `due_on` num único `insert` (ou numa transação de teste que só confirma ao final), reutilizando `BillingFixtures.guardian`. Se um segundo teste precisar, um parâmetro `payerGuardian` em `BillingFixtures.charge`; senão, não mexer no fixture.
- Dados, API, segurança, interface: não mudam.
- Risco: `errorIsolated` (due 10-09/10-10, `notified_at is null` afirmado) tem a mesma janela entre `charge()` e o job; não aparece na issue (D2).

## 5. Tarefas

- [x] T1: reescrever a montagem da cobrança em `minor()` como um comando só, com responsável ligado — cobre CA1 — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test=ChargeNoticeIT -Dtest=NoSuchTest -Dsurefire.failIfNoSpecifiedTests=false -Dfailsafe.failIfNoSpecifiedTests=false`
- [x] T2: repetir a prova 20 vezes (laço de shell) e rodar o `verify` completo — cobre CA2 — prova: 20 de 20 verdes; `./backend/mvnw -f backend/pom.xml verify`

## 6. Decisões

- D1: remover a janela na montagem (inserção atômica), não esperar nem tentar de novo, como a issue pede; alternativa descartada: desligar o agendador no teste, que mexeria na configuração do contexto de todos os `*IT`.
- D2: `errorIsolated` e os demais testes da classe ficam como estão, pois a issue só aponta `minor`; se a repetição de 20 mostrar falha neles, entram na mesma correção como tarefa nova. Se a suposição estiver errada, a #390 volta a falhar por outro teste.

## Construção

- T1 — arquivos: `backend/src/test/java/br/com/ctcore/billing/ChargeNoticeIT.java` (`minor()`: cobrança criada fora da janela, e um único `update` liga `payer_guardian_id` e leva `due_on` para 2026-10-08; fixture intocado) — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test=ChargeNoticeIT ...` → 7 testes, 0 falhas
- T2 — prova: laço de 20 execuções da prova da T1 → 19 verdes; a 10ª falhou por infraestrutura (Testcontainers não subiu o `seaweedfs`, 7 erros de contexto, nenhum de asserção); repetida em seguida → verde (20 de 20 sem falha do teste). `./backend/mvnw -f backend/pom.xml clean verify` → 859 testes, 0 falhas, BUILD SUCCESS.
Regressão: back-end `verify` completo verde (só back-end; item só altera um teste de back-end, front-end não rodado: não afetado).
Desvios: o primeiro `verify` completo (sem `clean`) falhou na descoberta do JUnit (`NoClassDefFoundError: Tenant` em `ContactEmailSyncIT`, classe compilada obsoleta em `target/`); com `clean verify` passou. Não relacionado ao item.
Não é do item: `specs/backlog.md` já estava alterado.

## Verificação

### Rodada 1 — OK — 2026-10-28, estado 6e14336e4dee2ffe5e6b49cbd87ab7d5158d442f

| Critério | Status | Evidência |
|---|---|---|
| CA1 | PASSOU | `minor()` agora cria a cobrança fora da janela (due 2026-11-10) e um único `update` liga `payer_guardian_id` e leva `due_on` para a janela; `ChargeNoticeIT`: 7 testes, 0 falhas |
| CA2 | PASSOU | `ChargeNoticeIT` 4 execuções verdes nesta rodada (20 de 20 e `clean verify` 859 testes verdes registrados na Construção, não repetidos aqui) |

Comandos: `verify -Dit.test=ChargeNoticeIT ...` x4 → 7/7, BUILD SUCCESS (o ERROR no log é o caso esperado de `errorIsolated`). `verify` completo e front-end não reexecutados (diff só em um teste de back-end).
Achados:
- sugestão, código, `BillingFixtures.charge` (due_on fixo `date '2026-11-10'`) — o item depende do relógio real: a partir de 2026-10-31 o insert já cai na janela de 10 dias e a janela sem responsável volta (afeta `minor` e `errorIsolated`). Vale gerar a data fixa relativa a hoje (fuso da unidade) em item próprio.
- sugestão, especificação — a Construção diverge do plano (insert + um update, em vez de insert único); equivalente para o agendador, pois o update é atômico.

## Entrega

O que muda: `ChargeNoticeIT.minor()` cria a cobrança já fora da janela e um único `update` liga `payer_guardian_id` e leva `due_on` para 2026-10-08. Some a janela em que o `ChargeNoticeJob` via a cobrança devida sem responsável. Só um teste de back-end muda; sem código de produto.

Verificação: rodada 1 OK (`ChargeNoticeIT` 7 de 7; 20 de 20 execuções; `clean verify` com 859 testes verdes).

Limitações: `BillingFixtures.charge` mantém `due_on` fixo em 2026-11-10. A partir de 2026-10-31 a janela sem responsável volta a afetar `minor` e `errorIsolated`. A correção (data relativa a hoje, no fuso da unidade) fica em item próprio.
