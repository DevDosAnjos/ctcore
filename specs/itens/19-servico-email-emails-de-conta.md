# 19 — Serviço de envio de e-mail e e-mails de conta

Origem: issue #19 (Sprint 02); `docs/especificacao/00-visao-geral.md` (RNF-14) e `docs/especificacao/02-contas-e-acesso.md` (RN-ACE-22). Branch: `item/19-servico-email-emails-de-conta`.

## 1. Objetivo

Dar ao back-end um serviço de e-mail que não perde a mensagem quando o envio falha: toda mensagem é gravada, enviada na hora, e, se falhar, registrada, avisada e tentada de novo. Em cima dele ficam os quatro e-mails de conta: convite, nova senha, senha alterada e "Novo acesso à sua conta". Serve a #20 a #25 e a todo e-mail das sprints seguintes (cobrança, lembretes).

Fora do item: telas e textos de tela (#20 a #25); e-mail real (Sprint 22: só um adaptador novo atrás de `EmailSender`); cidade por IP real (Sprint 22); modelos editáveis pela academia e comunicados (Sprint 09); tela ou rota para ver o registro de falhas (painel da plataforma).

## 2. Requisitos

- R1 (RNF-14): toda mensagem passa por `EmailService.send`, que a grava em `email_message` antes de enviar; falha do `EmailSender` (exceção ou `NOT_SENT` com a conexão ligada) não perde nem desfaz a operação de quem chamou.
- R2 (RNF-14): mensagem que falhou fica com o erro e o número de tentativas registrados e é reenviada sozinha, com intervalos crescentes, até o máximo de tentativas (D2); ao esgotar, vira falha definitiva.
- R3 (RNF-14): a falha avisa: log `WARN` a cada falha (id, modelo e tentativa, sem destinatário nem texto) e contador `ctcore.email.failures`; `ctcore.email.queue.pending` passa a refletir as mensagens aguardando envio.
- R4: existe reenvio manual (`EmailService.requeue`) que põe de volta na fila uma mensagem em falha definitiva, quando ela ainda guarda o texto (D3).
- R5: modelos `INVITE`, `PASSWORD_RESET`, `PASSWORD_CHANGED` e `NEW_ACCESS`, com assunto e texto em `messages.properties` (pt-BR); `AccountMailer` continua com as mesmas chamadas e passa a usar o serviço.
- R6 (RN-ACE-22): login em aparelho novo envia "Novo acesso à sua conta" com aparelho, cidade aproximada e hora; aparelho já conhecido da conta não envia.
- R7 (D4): o primeiro acesso da conta (nenhum aparelho conhecido) não envia o aviso, e registra o aparelho.
- R8 (segurança): código de convite e de nova senha só aparecem no texto da mensagem enquanto ela espera envio; depois de enviada ou em falha definitiva o texto é apagado (D3); log, métrica e auditoria nunca trazem destinatário, texto ou código (RNF-11).
- R9: com a conexão de e-mail desligada (`mode: off`), a mensagem fica `DISABLED`, sem tentativas e sem erro para quem chamou.

## 3. Critérios de aceite

- CA1 (R1): `send` com `EmailSender` que lança exceção devolve normalmente, deixa a linha `PENDING` com `attempts=1` e `last_error`, e a operação de quem chamou (ex.: criar convite) termina com sucesso.
- CA2 (R1): `send` com sucesso grava a linha `SENT`, com `sent_at`, e o texto apagado; a `Outbox` simulada recebe a mensagem.
- CA3 (R2): com relógio injetado, a tarefa de reenvio não toca a linha antes de `next_attempt_at`, reenvia depois, e a 5ª falha deixa `FAILED`; um sucesso no meio deixa `SENT`.
- CA4 (R2): duas execuções simultâneas da tarefa não enviam a mesma mensagem duas vezes (trava do agendador e `for update skip locked`).
- CA5 (R3): uma falha incrementa `ctcore.email.failures` e escreve um `WARN` sem endereço nem texto; o medidor de fila bate com as linhas `PENDING`.
- CA6 (R4): `requeue` de uma `FAILED` de modelo sem segredo volta a `PENDING` e é enviada; de modelo com segredo (texto apagado) é recusada com `IllegalStateException`.
- CA7 (R5): cada um dos quatro modelos gera assunto e texto em português; convite e nova senha trazem o link com o código; o convite criado por `InviteService` e a nova senha pedida por `AuthService` continuam chegando na `Outbox` (testes de #18 passam sem mudança).
- CA8 (R6, R7): primeiro login da conta: nenhuma mensagem `NEW_ACCESS`; segundo login com outro `User-Agent`: uma mensagem com o aparelho (ex.: "Chrome em Windows"), a cidade (ou "local não identificado" sem localizador) e a hora em horário de Brasília; novo login com o mesmo aparelho depois de encerrar a sessão: nada.
- CA9 (R8): depois do envio, `select body from email_message` não traz o código do convite; os logs do teste não contêm o e-mail nem o código; `NEW_ACCESS` e `PASSWORD_CHANGED` seguem a mesma regra de log.
- CA10 (R9): com `ctcore.integrations.email.mode=off`, `send` grava `DISABLED`, não chama o `EmailSender`, não conta falha e a tarefa não a reenvia.

## 4. Plano técnico

- **Dados.** Migração `V7__email.sql` (a criar): `email_message` (`id`, `to_email`, `template`, `subject`, `body` anulável, `status` `PENDING|SENT|FAILED|DISABLED`, `attempts`, `next_attempt_at`, `last_error` sem endereço, `created_at`, `sent_at`) com índice em `(status, next_attempt_at)`; `account_device` (`account_id`, `device_hash`, `first_seen_at`, `last_seen_at`, chave `(account_id, device_hash)`). Nenhuma das duas tem `organization_id`: não são dado de organização (como `account_session`), então sem RLS por organização e sem rota que as leia. `infra/seed/` não muda (nada a semear); `SeedDataIT` ganha as duas tabelas na limpeza.
- **Serviço (pacote `br.com.ctcore.mail`, a criar).** `EmailTemplate` (enum: chave de mensagem e se carrega segredo), `EmailService` (`send(to, template, args...)`, `requeue(id)`, `attempt(id)` que envia, atualiza a linha e conta falhas), `EmailRetryJob` (`@Scheduled(fixedDelay)` + `@SchedulerLock` do agendador de #4, lê `PENDING` vencidas com `for update skip locked`, remove linhas `SENT`/`DISABLED` com mais de 30 dias e atualiza o medidor de fila). Propriedades `ctcore.email.max-attempts` (5) e `ctcore.email.backoff` (`1m,5m,30m,2h`) em `application.yml`. `OperationalMetrics` (existe) ganha `emailFailure()`.
- **Modelos.** `messages.properties` (existe): chaves `mail.*` atuais passam a ser lidas pelo serviço; acrescentar `mail.new-access.*` com `{0}` aparelho, `{1}` cidade, `{2}` hora.
- **Conta.** `EmailAccountMailer` e `AccountMailer` (existem) trocam o envio direto por `EmailService.send` e ganham `newAccess(to, device, city, at)`. `SessionService.create` (existe) chama `NewAccessNotifier` (a criar, em `identity`) depois de gravar a sessão: confere/grava `account_device` pelo hash do `User-Agent`, e envia se o aparelho é novo e a conta já tinha outro. `DeviceLabel` (a criar) transforma o `User-Agent` em "Navegador em Sistema" por trechos conhecidos, ou "Aparelho desconhecido". `IpLocator` (interface, a criar) com `NoIpLocator` como único bean até a Sprint 22 (devolve vazio); não é uma das conexões de `IntegrationConfig` por não haver adaptador real nem simulado útil (D5).
- **API/OpenAPI, interface, MSW.** Não mudam: nenhuma rota nova (D6).
- **Auditoria.** Não muda: o envio é efeito de sistema, não ação de usuário sobre dado da organização; o registro da própria `email_message` é o histórico (D6).
- **Segurança.** Texto com código só enquanto aguarda (R8). Risco: o código fica em claro por minutos no banco enquanto a mensagem espera nova tentativa; mitigado por apagar ao enviar, ao falhar de vez e por a linha ser de uso interno sem leitura por rota. Risco: `NEW_ACCESS` dentro de `SessionService.create` não pode derrubar o login; falha ali é capturada e logada.
- **Reutiliza.** `EmailSender`, `Outbox`, `IntegrationConfig`, `OperationalMetrics`, `SchedulerConfig`, `MessageSource`, `Clock`, `AccountTransactions`.
- **Doc.** `backend/README.md`: seção curta do serviço de e-mail, propriedades e como criar um modelo. Notas de versão: não há página nem arquivo de notas ainda ("Novidades" é RF-AJU-04, outra sprint); nada a atualizar.

## 5. Tarefas

- [x] T1: migração `V7__email.sql` e limpeza das tabelas novas em `SeedDataIT` — preparação, dá o lugar de gravar R1 a R7 — prova: `MigrationIT` e `SeedDataIT` passam.
- [x] T2: `EmailTemplate`, `EmailService.send/attempt` (grava, envia, apaga texto, `DISABLED`), modelos em `messages.properties` — cobre CA1, CA2, CA7, CA9, CA10 — prova: `EmailServiceIT` (`./backend/mvnw -f backend/pom.xml verify -Dit.test=EmailServiceIT`).
- [x] T3: `EmailRetryJob`, backoff, falha definitiva, `requeue`, `emailFailure()` e medidor de fila — cobre CA3, CA4, CA5, CA6 — prova: `EmailServiceIT` com relógio injetado e duas execuções concorrentes.
- [x] T4: religar `EmailAccountMailer` ao serviço sem mudar quem chama — cobre CA7 — prova: `InviteIT`, `AccountIT` e os testes de login de #18 passam sem edição.
- [x] T5: `DeviceLabel`, `IpLocator`/`NoIpLocator`, `account_device`, `NewAccessNotifier` ligado em `SessionService.create`, texto `mail.new-access.*` — cobre CA8 — prova: `NewAccessIT` e `DeviceLabelTest`.
- [x] T6: `backend/README.md` (serviço, propriedades, novo modelo) e regressão — prova: `./backend/mvnw -f backend/pom.xml verify` com JaCoCo verde.

## 6. Decisões

- D1: o serviço envia na hora e guarda tudo em tabela, em vez de só fila assíncrona. Motivo: convite e nova senha precisam sair em segundos e o e-mail não pode depender da tarefa de reenvio; a tarefa só cobre falhas. Alternativa: só fila, com atraso de até 1 minuto.
- D2: no máximo 5 tentativas, com espera de 1 min, 5 min, 30 min e 2 h entre elas (cobre um problema de até cerca de 3 horas). O convite vale 7 dias e a nova senha 1 hora; passada a validade o link já não serve de qualquer modo. Alternativa: tentar por dias; descartada porque a pessoa pede outro link antes.
- D3: o texto é apagado quando a mensagem é enviada e, nos modelos com código (`INVITE`, `PASSWORD_RESET`), também na falha definitiva; reenvio manual só vale para os demais. Motivo: RN-ACE-13 diz que o código só existe em hash; guardar por minutos é o custo de poder tentar de novo. Para convite perdido, vale o reenvio de convite da #18, que gera código novo. Alternativa: sempre gerar código novo a cada tentativa, descartada por exigir o serviço de convite saber de e-mail.
- D4 (muda o produto): a primeira vez que uma conta entra não gera "Novo acesso", porque não há aparelho anterior para comparar e a pessoa acabou de criar a conta. Aparelho = hash do `User-Agent` (sem IP). Alternativa: avisar sempre no primeiro; gera e-mail inútil em todo convite aceito. Suposição: se o dono quiser o aviso também no primeiro acesso, muda só a condição em `NewAccessNotifier`.
- D5 (muda o produto): "cidade aproximada" sai como "local não identificado" até haver localizador por IP. Motivo: depende de fornecedor externo e as Sprints 22 e 23 é que ligam conexões reais; a porta `IpLocator` já está pronta. Sugestão de novo item na Sprint 22: adaptador de localização por IP.
- D6: sem rota, sem tela, sem OpenAPI e sem auditoria neste item; o isolamento entre organizações e perfis não se aplica porque nada é exposto por HTTP. A tabela `email_message` não leva `organization_id` porque os e-mails de conta valem para a pessoa, não para a organização; quando a Sprint 09 enviar e-mail por organização, acrescenta-se a coluna com RLS.

## 7. Construção

- T1: `V7__email.sql` (`email_message`, `account_device`); `SeedDataIT` limpa as duas tabelas. Prova: `./backend/mvnw -f backend/pom.xml verify`, `SeedDataIT` 8/8 e Flyway aplicou a V7 em todos os `*IT`.
- T2: `mail/EmailTemplate`, `mail/EmailService` (`send`, `attempt`), `mail.new-access.*` em `messages.properties`, `ctcore.email.*` em `application.yml`. Prova: `EmailServiceIT` 9/9 (CA1, CA2, CA7, CA9, CA10).
- T3: `mail/EmailRetryJob`, `requeue`, `OperationalMetrics.emailFailure()`, medidor de fila. Prova: `EmailServiceIT` com relógio injetado e duas execuções concorrentes (CA3 a CA6).
- T4: `EmailAccountMailer` usa `EmailService`; `AccountMailer` ganhou `newAccess` com implementação padrão vazia, para o `AccountMailer` anônimo de `InviteIT` seguir sem edição. Prova: `InviteIT`, `AccountIT`, `AuthRoutesIT`, `AuthIsolationIT` sem edição, todos verdes.
- T5: `DeviceLabel`, `IpLocator`/`NoIpLocator`, `NewAccessNotifier` (chamado ao fim de `SessionService.create`, fora da transação, com falha capturada). Prova: `NewAccessIT` 1/1 (CA8), `DeviceLabelTest` 1/1.
- T6: seção "E-mail" em `backend/README.md`. Prova: `./backend/mvnw -f backend/pom.xml verify`: 89 testes rápidos e 87 de integração, 0 falhas, "All coverage checks have been met", BUILD SUCCESS.

Desvios:
- `EmailService` e `EmailRetryJob` recebem `JdbcClient` e `PlatformTransactionManager` com `@Lazy`, como o resto do projeto, porque os testes sem banco carregam a aplicação inteira.
- Na concorrência (CA4) os `*IT` usam pool de 1 conexão, então as duas execuções se serializam no teste; a trava `for update skip locked` está no código, mas o teste não exercita o salto de linhas travadas.
- `AuthBase`/`AccountIT` não mudaram: `account_device` some por `on delete cascade` ao apagar a conta.
- Sem desvios de requisitos; sem pedido de ajuste. Front-end e `AGENTS.md` não mudaram (sem comando novo).

## 8. Verificação

### Rodada 1 — OK — 90db3e11b7fb0a20dbf66ab564a531f04a02199d

| Critério | Status | Evidência |
| --- | --- | --- |
| CA1 | PASSOU | `EmailServiceIT.failureKeepsPending`; `EmailService.send` captura `RuntimeException` do sender |
| CA2 | PASSOU | `EmailServiceIT.success` |
| CA3 | PASSOU | `EmailServiceIT.retries` (relógio injetado, 5ª falha em `FAILED`) |
| CA4 | PASSOU | `EmailServiceIT.concurrentRuns` + `for update skip locked` no código; o teste serializa por pool de 1 conexão (desvio declarado), então o salto de linhas travadas é provado só por leitura do código |
| CA5 | PASSOU | `EmailServiceIT.alertsAndGauge` (contador, WARN sem endereço/código, medidor) |
| CA6 | PASSOU | `EmailServiceIT.requeue` |
| CA7 | PASSOU | `EmailServiceIT.templates`; `InviteIT`, `AccountIT`, `AuthRoutesIT` sem edição verdes |
| CA8 | PASSOU | `NewAccessIT.newDevice`, `DeviceLabelTest` |
| CA9 | PASSOU | `EmailServiceIT.noSecretLeft` |
| CA10 | PASSOU | `EmailServiceIT.disabled` |

Comandos: `./backend/mvnw -f backend/pom.xml verify` com BUILD SUCCESS, 87 testes de integração, 0 falhas, "All coverage checks have been met" (JaCoCo 80%/100%).

Achados obrigatórios: nenhum. Comparação especificação x card: RNF-14 e RN-ACE-22 cobertos; sem achado de especificação.

Sugestões:
- `EmailService.send` insere a linha `PENDING` com `next_attempt_at = now` e só depois chama `attempt` sem travar a linha; se o `EmailRetryJob` rodar nesse intervalo, a mesma mensagem pode ser enviada duas vezes. Janela curta; considerar `next_attempt_at` no futuro até o primeiro envio ou `for update` em `attempt`.
- CA4: o teste de concorrência não exercita `skip locked` de fato (pool de 1 conexão).

RESULTADO: OK

## 9. Entrega

Closes #19

- O que muda: serviço de e-mail (`mail/EmailService`, `EmailRetryJob`, tabelas `email_message` e `account_device` na V7) com fila, até 5 tentativas (1 min, 5 min, 30 min, 2 h), alerta e métrica de falha; e-mails de conta (convite, nova senha) passam pelo serviço; aviso de "Novo acesso" por aparelho novo.
- Verificação: `./backend/mvnw -f backend/pom.xml verify`, 89 testes rápidos e 87 de integração, 0 falhas, cobertura JaCoCo atendida. Base `dev` não avançou; regressão da verificação vale.
- Decisões: D4 (primeiro acesso não gera aviso) e D5 ("local não identificado" até haver localizador por IP; sugestão de item na Sprint 22) mudam o produto; ver seção 6.
- Limitações: janela curta de envio duplo entre `send` e `EmailRetryJob`; teste de concorrência não exercita `skip locked` (pool de 1 conexão). Sem rota, tela ou OpenAPI neste item.
