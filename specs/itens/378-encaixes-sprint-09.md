# 378 — Encaixes da Sprint 09 no backlog

Origem: issue #378 (ajuste A4 da revisão da Sprint 09, `specs/revisoes/sprint-09.md`, achado R09-4). Branch: `item/378-encaixes-sprint-09`.

## 1. Objetivo

Acrescentar a `specs/backlog.md`, na seção "Encaixes para as próximas sprints", o bloco "Sprint 09, comunicação (#78 a #84)", que aponta a issue que fará cada encaixe que a sprint deixou. Quem especificar essas issues sabe que herda o gancho. Só texto, sem código.

Fora do escopo: editar o corpo das issues (os blocos "Encaixe herdado da Sprint 09" já estão nelas e na #375), código, OpenAPI, criar issue nova.

## 2. Requisitos

- R1: `specs/backlog.md`, seção "Encaixes para as próximas sprints", ganha o bloco "Sprint 09, comunicação (#78 a #84)", no mesmo formato dos blocos vizinhos (uma linha de lista em negrito, apontando as issues).
- R2: o bloco lista cada encaixe com a issue que o faz: #176 (retorno e abertura de e-mail), #177 (SMS e e-mail não aberto), #122 (resumo diário, RN-NOT-01), #93 (leitura do comunicado), #86, #87, #100, #157, #160, #118, #131, #183 (notificações de cada fato), #99 (`{link_pagamento}`), #85 (boas-vindas) e #375 (editar comunicado agendado e limpar imagens).
- R3: o bloco diz que o aviso "turma sem professor" fica sem produtor aberto, porque o item que inativa o professor (#45) foi entregue sem publicá-lo (RN-PRO-05, RN-NOT-04).
- R4: o bloco não copia o conteúdo das issues; só aponta (AGENTS.md, "Backlog").

## 3. Critérios de aceite

- CA1 (R1): dado o `specs/backlog.md` alterado, quando se lê a seção de encaixes, então existe um bloco "Sprint 09, comunicação (#78 a #84)" depois dos já existentes.
- CA2 (R2): dado o bloco, quando se confere cada número da lista de R2, então todos aparecem e cada um corresponde a uma issue aberta cujo corpo tem "Encaixe herdado da Sprint 09" (a #375 traz a edição e a limpeza no próprio texto).
- CA3 (R3): dado o bloco, quando se procura "turma sem professor", então aparece dito que não há issue aberta que o produza e que a #45 foi entregue sem publicá-lo.
- CA4 (R4): dado o bloco, quando se compara com as issues, então não repete os "Pronto quando" delas.

## 4. Plano técnico

- Dados, API, Interface, Segurança: não mudam / não se aplica.
- Arquivos: `specs/backlog.md` (visto), um item de lista novo ao fim da seção "Encaixes para as próximas sprints" (hoje termina no bloco "Sprint 06, encaixes da lista, dos selos e da ficha do aluno").
- Reuso: formato dos blocos da Sprint 08 e da Sprint 07 da mesma seção; texto das propostas do A4 em `specs/revisoes/sprint-09.md`.
- Riscos: número de issue errado; conferir com `gh issue view`. Não repetir texto que as issues já têm, para não divergir deles.

## 5. Tarefas

- [x] T1: escrever o bloco em `specs/backlog.md` com os encaixes de R2 agrupados (e-mail e SMS da Sprint 22; leitura do portal; notificações por fato; link de pagamento e boas-vindas; resumo diário; #375) e a frase de R3 — cobre CA1, CA2, CA3, CA4 — prova: `grep -n "Sprint 09, comunicação" specs/backlog.md` e `for n in 176 177 122 93 86 87 100 157 160 118 131 183 99 85 375; do gh issue view $n --json state --jq .state; done` (todas `OPEN`)

## 6. Decisões

- D1: "turma sem professor" fica registrado no bloco como encaixe sem produtor, e não vira issue aqui: o card pede só o bloco e "sem código". Alternativa descartada: criar a issue neste item (trabalho independente, a critério do dono). Sugestão de novo item no resumo.
- D2: a #96 e os pedidos de encerramento/exclusão do A4 original não entram como linhas próprias; vale a lista da issue #378, onde pedido de encerramento está na #86 e exclusão de dados na #118.
- Suposição: a linha da #378 não está na tabela de `specs/backlog.md` (conferido: não há "378" lá). O arquivo foi feito mesmo assim; se o `maestro` exigir a linha, ela precisa ser acrescentada.

## Construção

- T1 — arquivos: `specs/backlog.md` (bloco "Sprint 09, comunicação (#78 a #84)" ao fim dos encaixes) — prova: `grep -n "Sprint 09, comunicação" specs/backlog.md` → 1 linha; `gh issue view <n> --json state` para 176 177 122 93 86 87 100 157 160 118 131 183 99 85 375 → todas `OPEN`
Regressão: não se aplica (só texto de especificação, sem código).
Desvios: a #45 está `CLOSED`, como o bloco afirma (entregue sem publicar o aviso). Não conferi o texto "Encaixe herdado da Sprint 09" no corpo de cada issue (CA2), só o estado `OPEN`.
Não é do item: a linha da #378 na tabela de `specs/backlog.md` já estava na pasta de trabalho (alteração anterior à sessão).

## Entrega

O que muda: `specs/backlog.md` ganha o bloco "Sprint 09, comunicação (#78 a #84)" ao fim da seção "Encaixes para as próximas sprints", apontando a issue que fará cada encaixe de comunicação deixado pela Sprint 09 (#176, #177, #122, #93, #86, #87, #100, #157, #160, #118, #131, #183, #99, #85, #375). A linha da #378 vai para "feito". Só texto, sem código.

Verificação: rodada 1 OK (CA1 a CA4). Conferido de novo contra `1b25fc1`: só `specs/backlog.md` e este arquivo mudaram.

Decisões: "turma sem professor" fica registrado como encaixe sem produtor aberto, sem issue nova (D1). A #45 foi entregue sem publicar o aviso (RN-PRO-05, RN-NOT-04).

Limitações: nenhuma no código. A issue #378 é fechada pelo `Closes #378` no merge.

## Verificação

### Rodada 1 — OK — 2026-10-24, estado 1b25fc1+pasta de trabalho (diff: specs/backlog.md, 2 linhas)

| Critério | Status | Evidência |
|---|---|---|
| CA1 | PASSOU | bloco "Sprint 09, comunicação (#78 a #84)" é o último item da seção de encaixes (git diff) |
| CA2 | PASSOU | `gh issue view` nas 14 issues: todas OPEN, autor DevDosAnjos, corpo com "Encaixe herdado da Sprint 09"; #375 OPEN, título e corpo trazem edição de agendado e limpeza de imagens |
| CA3 | PASSOU | texto diz "sem produtor aberto" e que a #45 foi entregue sem publicá-lo; #45 CLOSED |
| CA4 | PASSOU | bloco só aponta, sem copiar "Pronto quando" |

Comandos: git diff (só texto); gh issue view das 15 issues + #45. Regressão: não se aplica (só especificação).
Achados:
- sugestão, especificação, seção Construção — o desvio "não conferi o texto" fica resolvido por esta rodada.
