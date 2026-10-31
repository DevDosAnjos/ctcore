# 447 — Retenção de 30 dias das notificações da equipe (RN-NOT-05)

Origem: issue #447 (Sprint 10.5, decisão 12 do dono em 30/10/2026); `docs/especificacao/13-comunicacao-e-notificacoes.md` (RN-NOT-05, RF-NOT-01); relatório M28 e plano F1-11 em `docs/auditoria/`. Branch: `item/447-retencao-notificacoes-30-dias`. Parte do módulo `notification` (#84) e do padrão de job diário de `LessonGenerationJob` / `InvitePendingJob`.

## 1. Objetivo

A notificação do sino passa a ser apagada 30 dias depois de criada (lida ou não), por uma limpeza diária, e o painel do sino mostra no rodapé "As notificações somem depois de 30 dias". Para a equipe, o sino deixa de acumular aviso velho; o contador de não lidas nunca conta o que já expirou, mesmo antes da próxima execução da limpeza.

Fora do escopo: retenção de comunicados, e-mails e auditoria (têm regra própria); o visual do popover (issue #492); mudança de tipos, destinatários ou janela de 7 dias da lista.

## 2. Requisitos

- R1 (RN-NOT-05): um job diário apaga as notificações com 30 dias ou mais de criadas (`created_at <= agora - 30 dias`, comparando instantes com o `Clock` injetado), lidas ou não, de todas as organizações ativas.
- R2 (isolamento): cada organização é limpa no seu próprio escopo (RLS); a limpeza de uma nunca apaga nem lê dado de outra; falha em uma organização não impede as demais.
- R3 (RN-NOT-05): `GET /notifications` (lista e `unreadCount`) e `GET /notifications/unread-count` só consideram notificações com menos de 30 dias, para não contar item expirado entre duas execuções.
- R4: cada execução registra no log a quantidade apagada, sem dado pessoal.
- R5 (RF-NOT-01): o painel do sino mostra o rodapé "As notificações somem depois de 30 dias", com o texto no `pt-BR.json`.
- R6 (D1): a limpeza não cria DELETE para o papel operacional na tabela; usa uma função de banco restrita à organização.

## 3. Critérios de aceite

- CA1 (R1): dado notificações de 29, 30 e 31 dias (relógio fixo), quando o job roda, então as de 30 e 31 dias somem e a de 29 fica; lida ou não lida não muda o resultado.
- CA2 (R2): dado notificações vencidas em duas organizações, quando o job roda, então as duas são limpas, cada uma só com o próprio escopo; chamar a função de limpeza com a organização A nunca apaga linha da B.
- CA3 (R2): dado falha na limpeza de uma organização, quando o job roda, então as demais são limpas e o erro vai ao log só com a classe da exceção.
- CA4 (R3): dado uma não lida de 31 dias ainda gravada (job não rodou), quando `GET /notifications/unread-count` e `GET /notifications?filter=unread`, então ela não entra no contador nem no `unreadCount`; uma de 29 dias entra; depois do job o contador continua igual.
- CA5 (R4): dado a execução do job, então o log traz a quantidade apagada e nenhum e-mail, nome ou texto de notificação.
- CA6 (R5): dado o painel aberto em qualquer estado com dados (lista, "Tudo lido", vazio), então o rodapé com o prazo aparece; teste do front confere o texto.
- CA7 (R6): dado o papel operacional, quando tenta `delete from notification` direto, então é negado (continua sem DELETE), e a função apaga só as vencidas da organização informada.

## 4. Plano técnico

- Dados: migração `V52__notification_retention.sql` (a criar) com `purge_expired_notifications(p_org uuid, p_cutoff timestamptz) returns integer`, `language sql security definer set search_path = public, pg_temp`, `delete from notification where organization_id = p_org and created_at <= p_cutoff`, retornando a contagem; `revoke all ... from public` e `grant execute` ao papel operacional (seguir como as demais migrações dão grant, ver V25 `active_organization_ids`). A V47 revoga DELETE de propósito; a função mantém isso. Linha de "Revert" no cabeçalho. Índice `notification_link_created` basta para as consultas; a limpeza por `created_at` varre a tabela diariamente (volume baixo; ponytail: índice em `created_at` se crescer).
- API: nenhuma rota nova; `docs/openapi.yaml` e MSW não mudam (comportamento de leitura apenas). Mencionar a retenção na descrição de `GET /notifications` se já houver texto de janela.
- Interface: `frontend/src/features/notifications/NotificationPanel.tsx` ganha o rodapé no fim do conteúdo do `SidePanel`; chave nova `notifications.retention` em `frontend/src/i18n/pt-BR.json`. Os quatro estados continuam; o rodapé aparece nos estados com conteúdo, sem quebrar 360 px nem axe. Regerar só a referência visual do caso `notificacoes` (`npm run test:visual:docker -- --update-snapshots -g notificacoes`) e as capturas em `.capturas/447/`; a divergência com o canvas segue no `limiteCanvas` já existente da #492.
- Segurança: função com `search_path` fixo, recebe a organização e filtra por ela; chamada dentro de `transactions.provision(org, ...)`; log sem dado pessoal; nada de auditoria (limpeza do sistema, não ação de pessoa).
- Arquivos: a criar `backend/src/main/java/br/com/ctcore/notification/NotificationRetentionJob.java` (cron `0 15 3 * * *` UTC, `@SchedulerLock(name = "notification-retention")`, `runOnce()` devolve o total, laço por `active_organization_ids()` como `InvitePendingJob`; `RETENTION_DAYS = 30` constante pública do pacote); a criar `V52__notification_retention.sql`; a alterar `Notifications.java` (`unread(who)` e a consulta de `list`: adicionar `created_at > :expiry` com `clock.instant() - 30 dias`, reutilizando a constante); a alterar `NotificationPanel.tsx` e `pt-BR.json`; a criar `NotificationRetentionJobIT.java` e acrescentar casos em `NotificationsIT.java`; a alterar `NotificationPanel.test.tsx`.
- Reuso: padrão de `InvitePendingJob` (provision, `active_organization_ids`, log só com classe), `OrganizationBase` dos IT (`tenant()`, `adminJdbc()`), `Clock` injetável.
- Riscos: perfil de banco do CI/IT precisa conseguir executar a função (grant); conferir com o IT que roda com o papel operacional. O limite `<= cutoff` na limpeza e `> cutoff` nas consultas precisa ser o mesmo instante (30 dias exatos já expirados nos dois lados).

## 5. Tarefas

- [x] T1: migração V52 com a função de limpeza por organização e o grant ao papel operacional — cobre CA7 — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test=NotificationRetentionJobIT`
- [x] T2: `NotificationRetentionJob` (cron, lock, laço por organização, log da quantidade) e `NotificationRetentionJobIT` com 29/30/31 dias, duas organizações, falha isolada, log sem dado pessoal e tentativa de DELETE direto negada — cobre CA1, CA2, CA3, CA5, CA7 — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test=NotificationRetentionJobIT`
- [x] T3: filtrar por 30 dias o contador e a lista em `Notifications`, com casos em `NotificationsIT` (contador e `filter=unread` com item de 31 dias ainda gravado; isolamento entre organizações e perfis na consulta) — cobre CA4 — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test=NotificationsIT`
- [x] T4: rodapé no painel, chave `notifications.retention`, teste do front nos estados com conteúdo — cobre CA6 — prova: `npm test -- NotificationPanel` (em `frontend/`)
- [x] T5: regerar a referência visual do caso `notificacoes` e salvar as capturas de `.capturas/447/` (1440 e 390, pt-BR) — não cobre critério: exigência do AGENTS.md para tela alterada — prova: `npm run test:visual:docker -- -g notificacoes` e `npm run visual:canvas` (em `frontend/`)

## 6. Decisões

- D1: a limpeza usa função `security definer` por organização, em vez de devolver DELETE ao papel operacional. Motivo: a V47 tirou DELETE de propósito (a notificação não é apagada por ninguém da aplicação); a função limita o poder a "vencidas da organização informada" e segue o padrão de `active_organization_ids`. Alternativa descartada: grant de DELETE ao papel, que abriria a tabela inteira dentro do escopo RLS.
- D2: o prazo é "30 dias ou mais" (`created_at <= agora - 30 dias`), como pede a issue (só as de 30 dias ou mais somem); as consultas usam o mesmo corte, então não há janela em que o contador conte item que a limpeza apagaria. A comparação é por instante, sem fuso (o prazo é de 30 x 24 h, não de dia civil).
- D3: horário do job 03:15 UTC, entre os jobs existentes (03:00 agenda, 03:45 convites), com lock próprio. Escolha técnica sem efeito no produto.
- D4: o rodapé fica dentro do painel (`SidePanel`), no fim do conteúdo, em todos os estados com conteúdo; o ajuste fino do visual do popover é da #492.
- Suposição: não existe seed de notificação em `infra/seed/` que dependa de datas antigas; se existir, o `SeedDataIT` pode precisar de data recente.

## Construção

- T1 — arquivos: `backend/src/main/resources/db/migration/V52__notification_retention.sql` — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test=NotificationRetentionJobIT` → 3 testes, 0 falhas (grant ao papel que tem SELECT em `notification`, mesma técnica da V47)
- T2 — arquivos: `NotificationRetentionJob.java`, `NotificationRetentionJobIT.java` — prova: mesmo comando → CA1, CA2, CA3, CA5, CA7 passam
- T3 — arquivos: `Notifications.java` (`unread` com `created_at > agora - 30 dias`), `NotificationsIT.java` (caso `expiredNotCounted`) — prova: `verify -Dit.test='NotificationsIT,NotificationRetentionJobIT,InvitePendingJobIT'` → 11 testes, 0 falhas. A consulta de `list` já limita a 8 dias (`since`), então só o contador precisou do corte.
- T4 — arquivos: `NotificationPanel.tsx`, `NotificationPanel.test.tsx`, `pt-BR.json` (`notifications.retention`; rodapé em todo estado `ready`: lista, "Tudo lido" e vazio) — prova: `npm test -- NotificationPanel` → 9 passam; `npm run lint` e `npm run typecheck` limpos
- T5 — `npm run test:visual:docker -- --update-snapshots -g notificacoes` → 4 passam, referências sem diferença acima da tolerância (git sem mudança nos PNGs); `npm run visual:canvas` → 96 comparadas, 0 reprovadas. Capturas em `.capturas/447/` (sistema 1440 e 390; canvas só 1440, que é o que o canvas-shot e o lado a lado geram para `Notificacoes`).

Regressão: `npm run test:cov` (frontend) → 193 arquivos, 1921 testes passam. Backend `verify` completo → 867 testes, 0 falhas, BUILD SUCCESS. `npm run e2e` não rodado nesta sessão (mudança só de rodapé; o CI cobre).
Desvios: nenhum além do grant (feito pelo papel com SELECT na tabela, em vez de copiar V25, que não tem grant explícito).
Não é do item: nada.

## Verificação

### Rodada 1 — CORRIGIR_CODIGO — 2026-10-31, estado c42a63042299727e015d0b3f964e5deefbf1b172

| Critério | Status | Evidência |
|---|---|---|
| CA1 | PASSOU | `NotificationRetentionJobIT.purgesExpired` (29/30/31 dias, lida e não lida), verify verde |
| CA2 | PASSOU | `purgesExpired` (duas organizações) e `functionOnly` (função da org A não toca a B) |
| CA3 | PASSOU | `failureIsIsolated` (org A falha, B limpa, log só com a classe) |
| CA4 | PASSOU | `NotificationsIT.expiredNotCounted` (contador e `filter=unread`; contador igual depois do job) |
| CA5 | PASSOU | assert do log em `purgesExpired` (só `deleted=N`, sem e-mail/nome) |
| CA6 | PASSOU | `NotificationPanel.test.tsx`, 9 testes passam (lista, "Tudo lido", vazio); ver achado sobre a referência visual |
| CA7 | PASSOU | `functionOnly`: `delete from notification` com `ctcore_app` dá "permission denied"; a função apaga 3 |

Comandos:
- `./backend/mvnw -f backend/pom.xml verify -Dit.test='NotificationRetentionJobIT,NotificationsIT,InvitePendingJobIT'` → 11 testes, 0 falhas, BUILD SUCCESS
- `npx vitest run src/features/notifications --maxWorkers=2` → 9 passam; `npm run lint` e `npm run typecheck` limpos
- `npm run test:visual:docker -- -g notificacoes` → 4 passam (mas ver achado)
- Regressão completa (verify total, test:cov, e2e) não reexecutada; vale o registro da construção (867 testes backend, 1921 front).

Achados:
- obrigatório, código, `frontend/visual/referencias/notificacoes-*.png` e `.capturas/447/notificacoes-{1440,390}-sistema.png` — esperado: referência e capturas do painel já com o rodapé "As notificações somem depois de 30 dias" (T5; AGENTS: o PR regera os prints da própria tela e o verificador compara as imagens); observado: as imagens abertas (1440 e 390) não mostram o rodapé, embora `NotificationPanel.tsx` o renderize; o teste visual passa porque `maxDiffPixelRatio: 0.001` absorve a linha de texto e o Playwright não sobrescreve a referência dentro da tolerância; as capturas são cópia da referência antiga; evidência: imagens lidas, `git status frontend/visual` sem mudança; corrigir: `npm run test:visual:docker -- --update-snapshots all -g notificacoes`, conferir que o rodapé aparece nas quatro referências e refazer `.capturas/447/` e `npm run visual:canvas`.
- sugestão, código, `.capturas/447/` — falta `notificacoes-390-canvas.png` (AGENTS pede 1440 e 390); a construção justifica que o gerador só produz 1440, o que pode ser aceito se registrado no PR.

### Correção da rodada 1
- referência visual sem o rodapé — arquivos: `frontend/visual/referencias/notificacoes-{1440,390}-{claro,escuro}.png` (regeradas), `.capturas/447/notificacoes-{1440,390}-sistema.png` (copiadas da referência nova; rodapé visível ao abrir as imagens) — prova: `npm run test:visual:docker -- --update-snapshots all -g notificacoes` → 4 passam, 4 PNGs modificados no git; `npm run visual:canvas -- --exige` → 96 comparadas, 0 reprovadas
- sugestão (390 do canvas): `notificacoes-390-canvas.png` não gerado, o gerador só produz 1440 para `Notificacoes`; registrar no PR.
Regressão: não reexecutada; a correção só mexe em PNGs de referência (código inalterado), vale o registro da construção.

### Rodada 2 — OK — 2026-10-31, estado 08057f5e4a5bae38c3668e2ed852986d6e54cd1a

| Critério | Status | Evidência |
|---|---|---|
| CA1 | PASSOU | `NotificationRetentionJobIT` 3 testes verdes (reexecutado) |
| CA2 | PASSOU | idem (`purgesExpired`, `functionOnly`) |
| CA3 | PASSOU | idem (`failureIsIsolated`; log só com a classe `IllegalStateException`) |
| CA4 | PASSOU | `NotificationsIT` 6 testes verdes (reexecutado), inclui `expiredNotCounted` |
| CA5 | PASSOU | mantido da rodada 1 |
| CA6 | PASSOU | `npx vitest run src/features/notifications --maxWorkers=2` → 9 passam; imagens abertas (referência 390 escuro e captura 1440 do sistema) mostram o rodapé "As notificações somem depois de 30 dias" |
| CA7 | PASSOU | `functionOnly`, verde |

Achado da rodada 1 (referência visual sem rodapé): corrigido. PNGs de referência regerados (4 modificados no git) e `.capturas/447/notificacoes-{1440,390}-sistema.png` idênticas às referências claras novas (`cmp`).
Mudança desde a rodada 1: só PNGs e este arquivo; código inalterado.

Comandos: `verify -Dit.test='NotificationRetentionJobIT,NotificationsIT'` → 9 testes, 0 falhas, BUILD SUCCESS; vitest de notificações 9 passam. Regressão completa não reexecutada (código inalterado; vale a construção: 867 backend, 1921 front); e2e não rodado, o CI cobre (limitação aceita, AGENTS Autonomia 2).
Achados:
- sugestão, `.capturas/447/` — falta `notificacoes-390-canvas.png` (o gerador só produz 1440 para `Notificacoes`); registrar no PR.

## Entrega

- O que muda: a limpeza diária (`NotificationRetentionJob`, função `V52__notification_retention.sql` com grant só para a função) apaga notificações da equipe com mais de 30 dias, lidas ou não; o contador de não lidas deixa de contar o que já expirou, mesmo antes da próxima execução; o painel do sino mostra o rodapé "As notificações somem depois de 30 dias" em todo estado `ready`.
- Verificação: rodada 2 `OK` (estado `08057f5e`). Backend: `NotificationRetentionJobIT` e `NotificationsIT` (9 testes, 0 falhas). Front: `NotificationPanel` (9 passam), lint e typecheck limpos. Visual: `test:visual:docker -- -g notificacoes` (4 passam), referências regeradas com o rodapé; `visual:canvas -- --exige` com 96 comparadas e 0 reprovadas. Regressão completa da construção: backend `verify` 867 testes sem falha, front `test:cov` 1921 testes; `e2e` não rodado nesta fase (o CI cobre).
- Decisão: o painel mostra o rodapé em todo estado `ready` (lista, "Tudo lido" e vazio), porque o canvas não traz o rodapé e o texto é regra de retenção, não visual.
- Divergência de comparação: o gerador do canvas produz só 1440 para `Notificacoes`; não há `notificacoes-390-canvas.png`. Registrado aqui e no PR.
- Limitações: retenção de comunicados, e-mails e auditoria ficam fora (têm regra própria). O nome da branch (`item/447-retencao-notificacoes-equipe`) difere do cabeçalho deste arquivo; a branch é a que está em uso.
