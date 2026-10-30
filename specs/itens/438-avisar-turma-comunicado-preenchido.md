# 438 — "Avisar turma" abre o comunicado vazio em vez do aviso pronto

Origem: issue #438 (Sprint 10.5 · Identidade visual; auditoria A3 / F1-02). Branch: `item/438-avisar-turma-comunicado-preenchido`.

## 1. Objetivo

Garantir que "Avisar turma", no painel da aula, abra o Novo comunicado já preenchido: destino = turma da aula, tipo "Aula cancelada" se a aula está cancelada (senão "Aviso geral"), título e texto do modelo do tipo com turma, data e horário resolvidos. Para a gestão (RN-AGE-05, RF-COM-02, RN-COM-12).

Achado ao ler o código: o comportamento descrito na issue **já existe** em `dev` (entrou na #422). `LessonPanelPage.tsx:71` monta `classId`, `type`, `date` e `time`; `NewAnnouncementPage.tsx` (`initialTarget`, `vars`, `start`) lê todos e preenche tipo, título e texto por `templateFor`; tipo desconhecido cai em `GENERAL`. O relatório A3 provavelmente foi feito sobre build anterior. O item vira: provar o comportamento e fechar as lacunas de teste que a issue exige, sem mudar regra.

Fora: trocar a página por janela (#473).

## 2. Requisitos

- R1: com `classId`, `type`, `date` e `time` na URL, o formulário abre com destino = a turma, tipo e título/texto do modelo do tipo, com turma, data e horário resolvidos.
- R2: aula cancelada gera `type=CANCELED_CLASS`; aula não cancelada, `type=GENERAL`, com a turma já escolhida.
- R3: `type` desconhecido cai em "Aviso geral"; sem `type` na URL, o formulário abre como hoje (título e texto vazios).
- R4: o texto usado é o da organização ou, sem ele, o da plataforma (RN-COM-12).
- R5: o fluxo completo (aula cancelada, "Avisar turma", formulário preenchido) tem teste de ponta a ponta com o MSW.

## 3. Critérios de aceite

- CA1 (R1, R4): `/comunicados/novo?classId=k1&type=CANCELED_CLASS&date=...&time=19:00` abre com destino, tipo e texto com data e horário (teste CA26 existente) e com o texto da organização (CA18 existente).
- CA2 (R2): o link do painel de aula cancelada contém `type=CANCELED_CLASS` e o de aula normal `type=GENERAL` (testes existentes em `LessonPanelPage.test.tsx`); com `type=GENERAL` o formulário abre com tipo "Aviso geral" e a turma escolhida.
- CA3 (R3): `type=INVENTADO` abre com tipo "Aviso geral", título e texto vazios (igual a sem `type`, `NewAnnouncementPage.tsx:80`); sem `type`, igual a hoje (teste existente `classId=k1`).
- CA4 (R5): no e2e `comunicados.spec.ts`, depois de "Avisar turma", título e texto não estão vazios e o texto traz a data e o horário da aula.
- CA5: lint, tipos e testes do front passam; axe e quatro estados não mudam (nenhuma tela alterada).

## 4. Plano técnico

- Dados, API, segurança: não mudam.
- Interface: sem mudança de código esperada em `frontend/src/features/agenda/LessonPanelPage.tsx` nem em `frontend/src/features/announcements/NewAnnouncementPage.tsx`. Só se um teste novo revelar falha real, corrigir ali.
- Testes a alterar: `frontend/src/features/announcements/NewAnnouncementPage.test.tsx` (casos de `type=GENERAL` com turma e de `type` desconhecido, usando `setup`/`ready` já existentes); `frontend/e2e/comunicados.spec.ts` (após o clique em "Avisar turma", asserções de título e texto não vazios e de data/horário no texto).
- Riscos: o texto do modelo da plataforma para "Aula cancelada" pode não conter data e horário no MSW do e2e; nesse caso a asserção usa o que o modelo traz (ver D2).

## 5. Tarefas

- [x] T1: acrescentar em `NewAnnouncementPage.test.tsx` os casos `type=GENERAL&classId=k1` (tipo "Aviso geral", destino `CLASS:k1`) e `type=INVENTADO` (cai em "Aviso geral", título e texto vazios) — cobre CA2, CA3 — prova: `cd frontend && npx vitest run src/features/announcements/NewAnnouncementPage.test.tsx`.
- [x] T2: reforçar `e2e/comunicados.spec.ts` com título e texto não vazios e data/horário no texto após "Avisar turma" — cobre CA4 — prova: `cd frontend && npx playwright test e2e/comunicados.spec.ts`.
- [x] T3: rodar `npm run lint && npm run typecheck` e os testes de `LessonPanelPage` e `NewAnnouncementPage`; conferir à mão com o MSW (`ana@exemplo.com`, aula cancelada "Judô Infantil") — cobre CA1, CA2, CA5 — prova: `cd frontend && npm run lint && npm run typecheck && npx vitest run src/features/agenda/LessonPanelPage.test.tsx src/features/announcements`.

## 6. Decisões

- D1: não reescrever código que já cumpre a issue; o item entrega a prova que faltava. Motivo: evitar mexer em comportamento que funciona. Alternativa: refatorar `initialTarget`, descartada. Suposição: se a conferência manual em T3 ainda mostrar o formulário vazio, a causa é outra (por exemplo `GET /announcements/templates` na API real) e vira correção dentro deste item.
- D2: "texto não vazio" vale para "Aula cancelada"; o modelo de "Aviso geral" da plataforma tem texto vazio por desenho (teste CA23 existente) e assim continua. Alternativa: dar texto ao "Aviso geral", descartada por mudar RN-COM-12.
- D4 (ajuste, pedido da construção): CA3 corrigido para o que R3 e o código já dizem: `type` desconhecido abre tipo "Aviso geral" com título e texto vazios. Nenhuma tarefa refeita; o teste de T1 já assere isso. Alternativa (preencher o título), descartada por mudar comportamento sem pedido do dono.
- D3: o "falham sem a correção" da issue é atendido pelos testes novos de T1/T2 apenas na parte que não existia; como a correção já está em `dev`, não há estado vermelho a reproduzir. Registrar isso na descrição do PR.

## Construção

- T1 — arquivos: `frontend/src/features/announcements/NewAnnouncementPage.test.tsx` (2 casos novos: `type=GENERAL` com turma; `type=INVENTADO`) — prova: `npx vitest run src/features/announcements/NewAnnouncementPage.test.tsx` → passa (o caso INVENTADO assere título vazio, ver pedido de ajuste)
- T2 — arquivos: `frontend/e2e/comunicados.spec.ts` (título "Aula cancelada" e texto com turma, data e horário após "Avisar turma") — prova: `npx playwright test e2e/comunicados.spec.ts` → 1 passou
- T3 — prova: `npm run lint && npm run typecheck && npx vitest run src/features/agenda/LessonPanelPage.test.tsx src/features/announcements` → lint e tipos ok, 3 arquivos, 46 testes passaram. Conferência manual no navegador: não feita; o e2e com MSW cobre o mesmo fluxo (aula cancelada, "Avisar turma", formulário preenchido).
Regressão: só o front tocado por testes; `npm run test:cov` e `e2e` completos não rodados nesta sessão (mudança apenas em dois arquivos de teste).
Desvios: o e2e falhou no login na primeira rodada porque havia um Vite antigo sem MSW na 5173 (reaproveitado pelo Playwright); o processo foi encerrado e o teste passou com o servidor novo.
Pedido de ajuste (atendido pela fase 2, ver D4; CA3 já corrigido): CA3 — dizia que `type=INVENTADO` abre com título "Aviso geral", mas o código (`NewAnnouncementPage.tsx:80`) abre tipo "Aviso geral" com título e texto vazios, igual a "sem type" (R3). O teste novo assere o que o código faz; se o dono quiser título preenchido, é mudança de código/regra.

## Verificação

### Rodada 1 — OK — 2026-10-30, estado 5ba757300fbd253f0f6b5c72403eed70af294512

| Critério | Status | Evidência |
|---|---|---|
| CA1 | PASSOU | testes CA26/CA18 existentes em NewAnnouncementPage.test.tsx passam (vitest, 46 testes) |
| CA2 | PASSOU | LessonPanelPage.test.tsx linhas 139 e 421 (links GENERAL e CANCELED_CLASS); novo caso GENERAL com turma passa |
| CA3 | PASSOU | novo caso type=INVENTADO assere tipo GENERAL, título e texto vazios; passa |
| CA4 | PASSOU | npx playwright test e2e/comunicados.spec.ts: 1 passou (título "Aula cancelada", texto com data e horário) |
| CA5 | PASSOU | npm run lint e typecheck sem erro; nenhuma tela alterada |

Comandos: npm run lint, npm run typecheck (ok); vitest LessonPanelPage + announcements (3 arquivos, 46 passaram); playwright comunicados (1 passou). Regressão completa (test:cov, e2e:full) não rodada: só dois arquivos de teste mudaram.
Achados:
- sugestão, código, NewAnnouncementPage.test.tsx — os dois casos novos reutilizam o prefixo "CA26", que já existe; renomear para evitar ambiguidade.

## Entrega

- O que muda: só testes. `NewAnnouncementPage.test.tsx` ganha os casos `type=GENERAL` com turma (tipo "Aviso geral", destino `CLASS:k1`) e `type=INVENTADO` (cai em "Aviso geral", título e texto vazios); `e2e/comunicados.spec.ts` assere título e texto não vazios e data/horário após "Avisar turma". Nenhum código de produto mudou: o comportamento da issue já estava em `dev` (entrou na #422).
- Verificação: rodada 1 OK (estado `5ba757300fbd253f0f6b5c72403eed70af294512`). Lint, tipos, vitest (3 arquivos, 46 testes) e o e2e `comunicados` passaram. Regressão completa (`test:cov`, `e2e:full`) não rodou: só dois arquivos de teste mudaram.
- Decisões: D1 (não reescrever código que já cumpre a issue), D2 ("Aviso geral" segue sem texto, RN-COM-12), D4 (CA3 ajustado ao que o código faz: título vazio).
- Limitações: a conferência manual no navegador (T3) não foi feita; o e2e com MSW cobre o mesmo fluxo. Achado de baixa prioridade: os dois casos novos reutilizam o prefixo "CA26" de um teste existente.
- Descrição do PR: a issue #438 pede teste que falhe sem a correção; como a correção já está em `dev`, não há estado vermelho a reproduzir (D3).
