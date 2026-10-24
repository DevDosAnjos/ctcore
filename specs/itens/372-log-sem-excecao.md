# 372 — Log sem a exceção inteira nos jobs da Sprint 09

Origem: issue #372 (achado R09-5 e ajuste A5 de `specs/revisoes/sprint-09.md`). Branch: `item/372-log-sem-excecao`.

## 1. Objetivo

Quatro caminhos da Sprint 09 registram a exceção inteira com `LOG.error(..., e)`. Uma violação de restrição do PostgreSQL traz `Key (...)=(valor)`, que pode ser um e-mail (RNF-11). Passam a registrar só a classe da exceção, como `Notifications.publish`.

Fora do escopo: outros logs do projeto; mudar o tratamento da falha (continua capturada e seguindo para o próximo).

## 2. Requisitos

- R1: `ChargeNoticeJob`, `PlanWrite.notifyRate`, `AnnouncementSendJob` e `InvitePendingJob` registram `cause={}` com `e.getClass().getSimpleName()`, sem passar a exceção ao logger.
- R2: um teste de log por caminho afirma que o endereço de e-mail não aparece no que foi registrado.

## 3. Critérios de aceite

- CA1 (R1): dada falha no aviso de cobrança, o log de erro do `ChargeNoticeJob` traz `cause=<Classe>` e não traz pilha nem mensagem da exceção.
- CA2 (R1): idem para `PlanWrite` (aviso de reajuste), `AnnouncementSendJob` e `InvitePendingJob`.
- CA3 (R2): em cada um dos quatro, a falha provocada carrega um e-mail fictício na mensagem e o texto do evento de log (mensagem formatada e pilha) não o contém.

## 4. Plano técnico

- Dados, API, interface: não mudam.
- Segurança: RNF-11, sem dado pessoal em log.
- Arquivos (vistos): `backend/src/main/java/br/com/ctcore/billing/ChargeNoticeJob.java:65`, `plan/PlanWrite.java:195` (`notifyRate`), `communication/AnnouncementSendJob.java:54`, `notification/InvitePendingJob.java:61`: trocar `LOG.error("… org={} …", …, e)` por `LOG.error("… org={} … cause={}", …, e.getClass().getSimpleName())`. Testes: `ChargeNoticeIT`, `PlanRateNoticeIT`, `AnnouncementSendJobIT`, `InvitePendingJobIT`, um caso novo em cada.
- Reuso: captura de log com `ListAppender<ILoggingEvent>` no logger raiz, como em `ChargeNoticeIT` (linha 62) e `EmailRegistryIT` (linha 36); formato `cause=` de `Notifications.java:97`.
- Riscos: provocar a falha em cada job (por exemplo, `RuntimeException` com e-mail na mensagem vinda de um colaborador substituído por `@MockitoBean`/`@SpyBean`, ou restrição real); a fase 3 escolhe o jeito que cada IT já permite. Se o caso novo passar antes da troca, o teste não prova nada: conferir que ele falha no código antigo.

## 5. Tarefas

- [x] T1: trocar o log nos quatro caminhos — cobre CA1, CA2 — prova: `./backend/mvnw -f backend/pom.xml compile`
- [x] T2: caso de log em `ChargeNoticeIT` e `PlanRateNoticeIT` — cobre CA1, CA3 — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test='ChargeNoticeIT,PlanRateNoticeIT' -Dtest=NoSuchTest -Dsurefire.failIfNoSpecifiedTests=false -Dfailsafe.failIfNoSpecifiedTests=false`
- [x] T3: caso de log em `AnnouncementSendJobIT` e `InvitePendingJobIT` — cobre CA2, CA3 — prova: mesmo comando com `-Dit.test='AnnouncementSendJobIT,InvitePendingJobIT'`
- [x] T4: regressão do back-end — não cobre critério: pedido do "Pronto quando" da issue (`verify` verde) — prova: `./backend/mvnw -f backend/pom.xml verify`

## 6. Decisões

- D1: o item fica no marco Sprint 09 (o da issue), embora a revisão diga "pode seguir na Sprint 10"; o ajuste é pequeno e não muda comportamento do produto.
- D2: o texto da mensagem de log fica em inglês, como os logs vizinhos.

## Construção

- T1 — arquivos: `ChargeNoticeJob`, `PlanWrite`, `AnnouncementSendJob`, `InvitePendingJob` (log com `cause={}` e `e.getClass().getSimpleName()`, sem passar a exceção) — prova: `./backend/mvnw -f backend/pom.xml verify` compilou → ok
- T2 — arquivos: `ChargeNoticeIT` (caso `errorIsolated` ampliado com captura de log e e-mail na mensagem), `PlanRateNoticeIT` (caso `failureLogsOnlyClass`, `@MockitoSpyBean EmailService`) — prova: comando da tarefa → ChargeNoticeIT 7/7 e PlanRateNoticeIT 2/2 verdes
- T3 — arquivos: `AnnouncementSendJobIT` (caso `oneFailureDoesNotStopTheOther` ampliado), `InvitePendingJobIT` (caso `failureLogsOnlyClass`, `Notifications` simulado) — prova: comando da tarefa → 2/2 e 2/2 verdes
- T4 — prova: `./backend/mvnw -f backend/pom.xml verify` → 752 testes, 0 falhas, BUILD SUCCESS
Regressão: apenas o back-end foi tocado; `verify` verde (acima).
Desvios: os testes novos têm 5 matchers no `PlanRateNoticeIT` (varargs de `EmailService.send`). Não conferi com execução que os casos falham no código antigo; no código antigo a exceção ia ao logger, então `getThrowableProxy() != null` reprovaria.

## Verificação

### Rodada 1 — OK — 2026-10-24, estado b68a5579e4519f43cb556eb7126614a893b14cce

| Critério | Status | Evidência |
|---|---|---|
| CA1 | PASSOU | diff: ChargeNoticeJob loga `cause={}` com classe, sem passar `e`; ChargeNoticeIT afirma `cause=IllegalStateException` e `getThrowableProxy()==null`; verify verde |
| CA2 | PASSOU | mesmo padrão em PlanWrite, AnnouncementSendJob, InvitePendingJob; testes PlanRateNoticeIT.failureLogsOnlyClass, AnnouncementSendJobIT, InvitePendingJobIT.failureLogsOnlyClass verdes |
| CA3 | PASSOU | cada teste lança exceção com e-mail fictício na mensagem e afirma ausência na mensagem formatada e sem pilha; os asserts `anySatisfy(cause=...)` provam que a falha foi de fato provocada |

Comandos: `./backend/mvnw -f backend/pom.xml verify` — 752 testes, 0 falhas, BUILD SUCCESS
Achados:
- sugestão, código, testes novos — não foi executado contra o código antigo (declarado na Construção); o `anySatisfy(cause=...)` já reprovaria o código antigo, então a prova é aceitável.

## Entrega

- Muda: `ChargeNoticeJob`, `PlanWrite.notifyRate`, `AnnouncementSendJob` e `InvitePendingJob` registram no log de erro só `cause=<classe da exceção>`, sem passar a exceção ao logger (sem pilha nem mensagem). Os quatro testes de log afirmam que o e-mail fictício da falha não aparece no evento formatado nem na pilha.
- Verificado: `./backend/mvnw -f backend/pom.xml verify`, 752 testes, 0 falhas, BUILD SUCCESS (rodada 1, estado b68a5579).
- Decisões: o item segue no marco Sprint 09 da issue; o texto do log fica em inglês, como os logs vizinhos.
- Limitações: os testes novos não foram rodados contra o código antigo; o `anySatisfy(cause=...)` é o que reprovaria o código antigo. O `PlanRateNoticeIT` usa 5 matchers para os varargs de `EmailService.send`.
