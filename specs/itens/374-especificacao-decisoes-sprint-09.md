# 374 — Atualizar a especificação com as decisões da Sprint 09

Origem: issue #374 (ajuste A3 da revisão da Sprint 09, achado R09-3, `specs/revisoes/sprint-09.md`) e decisões do dono de 24/10/2026 no corpo da issue. Branch: `item/374-especificacao-decisoes-sprint-09`.

## 1. Objetivo

Levar para `docs/especificacao` as 26 decisões "muda o comportamento" dos itens #78 a #84, para a especificação voltar a descrever o que o produto faz ("mudou uma regra, muda primeiro a especificação"). Só texto: nenhum código, teste, OpenAPI ou migração muda. Mesma classe do #337 da Sprint 08.

Fora do escopo: implementar a edição de comunicado agendado e a limpeza de imagens (é a #375, Sprint 13); mudar código; as decisões "técnicas" dos itens (D8 da #78, D9 da #81 etc.), que o A3 só manda registrar na revisão.

## 2. Requisitos

As 26 decisões, por item de origem: #78 D3, D4, D6, D7; #79 D1 a D5; #80 D1, D2, D6; #81 D1, D2; #82 D1, D2, D3, D5, D7, D8; #83 D1, D2, D4; #84 D1, D2, D3.

- R1: `13-comunicacao-e-notificacoes.md`, RN-COM-01 e RN-COM-02: o Administrador nunca envia para a organização inteira (só o Coordenador) e o servidor recusa (#82 D1); modalidade como destino exige a unidade (#82 D3); quem vê a lista e quem cancela agendado: o Coordenador vê tudo, o Administrador as unidades dele (sem "todas as unidades"), o Professor o que enviou e o destinado às suas turmas, com a permissão "enviar comunicado"; cancelam o Coordenador e o Administrador da unidade qualquer um, o Professor só o seu (#81 D2). Quem recebe: modalidade Ativa; "Encerramento solicitado" e as situações bloqueadas voltam a valer na Sprint 12, quando a situação existir (decisão do dono).
- R2: RN-COM-06: a regra de editar agendado fica como está, com a nota de que a edição vem no item #375 (Sprint 13); hoje só existe cancelar, que apaga o agendado (decisão do dono).
- R3: RN-COM-07: o limite de 20 por dia conta por unidade e por dia local, inclui os agendados do dia; comunicado da organização inteira tem limite próprio de 20 por dia na organização, sem consumir o das unidades; cancelar libera a vaga (#82 D5).
- R4: RN-COM-08 e RN-COM-09: a leitura é por aluno (no menor, a do responsável; vários responsáveis contam uma vez) (#81 D1); "pessoas" da prévia são destinatários distintos: o adulto e, no menor, cada responsável, contados uma vez; o e-mail é contado à parte (#82 D2).
- R5: RN-AGE-10 em `06-turmas-e-agenda.md` (com nota em `13`): o dia sem aula gera um comunicado por linha de dia (cada unidade tem a sua); ao editar o dia agendado o comunicado é refeito; ao desfazer um dia já avisado sai um aviso de aulas de volta; cancelar uma aula não envia comunicado, o aviso sai por "Avisar turma", que abre o formulário já preenchido (#82 D7, D8). O trecho "Até a Sprint 09 …" de RN-AGE-10 é revisto: o envio real já existe.
- R6: RN-COM-11 a RN-COM-14: "Cobrança gerada" sai ao pagador quando faltam até 10 dias para o vencimento, uma vez por cobrança, sem link de pagamento até a Sprint 11 (#78 D3, D4); catálogo fixo de nove modelos editáveis (lembretes antes, no dia e depois; boas-vindas; texto inicial dos cinco tipos de comunicado), texto próprio por organização, igual para todas as unidades; ficam fora os e-mails de conta e segurança, o recibo, cobrança gerada, reajuste, diferença e o aviso de bloqueio (#83 D1, D2); campos `{descricao}` e `{link_pagamento}`, este vazio até a Sprint 11 (#83 D4); e-mail que volta marca o endereço "Com problema" na organização, falha de envio não marca; recibo para endereço com problema não é enviado e o pagamento devolve `NO_ADDRESS`, e o recibo segue no link e no portal (#78 D6, D7).
- R7: RN-NOT-01 a RN-NOT-04: audiência por tipo (de unidade vai ao Administrador com acesso à unidade; da organização, ao Coordenador; "receber tudo" é preferência por vínculo, desligada por padrão, que só o Coordenador muda) (#84 D1); só os tipos cujo fato existe estão em vigor, os demais entram com o item que cria o fato (#84 D2); o resumo diário por e-mail ainda não existe e volta com o "Precisa de atenção", Sprint 13, #122 (#84 D3).
- R8: `10-cobrancas.md`, RN-COB-17 a RN-COB-20: lembrete no dia exato, na hora da unidade, uma só vez, sem recuperar dia perdido nem lembrar dia anterior à criação da cobrança (#79 D1, D2); liga/desliga é um interruptor geral da unidade, mais hora e dias; desligar um lembrete é tirar o dia da lista; os campos são obrigatórios no `PUT` (#80 D1, D6); canais: só e-mail até a Sprint 22, quando a regra de canais volta a valer (decisão do dono; #80 D2, #79 D1); aviso de bloqueio só para mensalidade, parcela e diferença de reajuste, em unidade com conta de pagamento conectada (#79 D3); SMS do aviso de bloqueio: vale "o aluno não abriu os e-mails anteriores" e, até o provedor registrar abertura, sai sempre que há telefone; limite de 3 por aluno e mês, todos os tipos juntos (#79 D4); envio manual: só e-mail, qualquer cobrança aberta, recusa a não aberta (`NOT_OPEN`) e a repetição em menos de 24 horas (`RECENT`), e não depende do interruptor da unidade (#79 D5).
- R9: `02-contas-e-acesso.md`: conferir se a permissão "enviar comunicado" e a tabela de permissões dizem o mesmo que R1; ajustar só se divergirem.
- R10: `specs/itens/81-comunicados-lista.md`: o texto do CA5 passa a dizer o que o produto faz (o Professor vê o comunicado da turma dele e o que ele mesmo enviou, não o da turma de outro professor).
- R11: toda regra adiada traz a sprint em que volta a valer: RN-COM-02 (Sprint 12), RN-COB-17 canais (Sprint 22), RN-COM-06 (#375, Sprint 13).
- R12: o item #375 (já criado) e o #374 constam no backlog, cada um na ordem do seu marco.

## 3. Critérios de aceite

- CA1 (R1): dado `13`, quando se lê RN-COM-01 e 02, então estão o limite do Administrador, a modalidade com unidade, a visão da lista, quem cancela e "Encerramento solicitado" com "Sprint 12".
- CA2 (R2, R11): dado RN-COM-06, quando se lê, então diz que a edição do agendado ainda não existe e cita a #375 e a Sprint 13.
- CA3 (R3): dado RN-COM-07, então traz o limite por unidade, o da organização, os agendados no dia e a vaga liberada ao cancelar.
- CA4 (R4): dado RN-COM-08 e 09, então a leitura é por aluno e "pessoas" são destinatários distintos, com o e-mail contado à parte.
- CA5 (R5): dado RN-AGE-10 em `06` e a nota em `13`, então há um comunicado por linha de dia, "Avisar turma" no lugar do envio ao cancelar e nenhuma frase dizendo que o envio real vem depois.
- CA6 (R6): dado RN-COM-11 a 14, então estão a janela de 10 dias, o pagador, o catálogo de nove modelos com o que fica fora, os dois campos novos, o bounce na organização e o `NO_ADDRESS`.
- CA7 (R7): dado RN-NOT-01 a 04, então estão a audiência por tipo, "receber tudo" por vínculo e o resumo diário marcado como adiado para a #122.
- CA8 (R8, R11): dado RN-COB-17 a 20, então estão o dia exato, o interruptor geral, "só e-mail até a Sprint 22", o aviso de bloqueio com conta conectada, o SMS por aluno e o envio manual com `RECENT`.
- CA9 (R9): dado `02`, então o texto da permissão e o do Professor batem com R1 (diff vazio se já batiam).
- CA10 (R10): dado o arquivo do #81, então o CA5 não contradiz o R3 e a D2 do mesmo arquivo.
- CA11 (todos): dado `git diff --stat`, então mudam só arquivos de `docs/especificacao/` e `specs/` (este item, o #81 e o backlog); nenhum de `backend/`, `frontend/`, `infra/` nem `docs/openapi.yaml`.
- CA12 (R12): dado `specs/backlog.md`, então há uma linha do 374 (Sprint 09, `a fazer`) depois da 372 e uma do 375 (Sprint 13, `a fazer`) depois da 335.

## 4. Plano técnico

- Dados, API, Interface, Segurança: não mudam (só texto).
- Arquivos (todos vistos): `docs/especificacao/13-comunicacao-e-notificacoes.md` (RN-COM-01, 02, 06 a 09, 11 a 14; RN-NOT-01 a 04); `docs/especificacao/10-cobrancas.md` (RN-COB-17 a 20, linhas 59 a 62); `docs/especificacao/06-turmas-e-agenda.md` (RN-AGE-10, linha 38); `docs/especificacao/02-contas-e-acesso.md` (tabela, linha 85, e parágrafo das permissões do Professor, linha 100; só conferência); `specs/itens/81-comunicados-lista.md` (CA5, linha 28; o desvio já registrado na Construção, linha 86, não se edita).
- Reuso: o sentido de cada decisão está em "Decisões" dos arquivos `specs/itens/78` a `84`; copiar o sentido, sem número de decisão nem nome de classe ou tabela (a especificação não cita código nem fornecedor). Usar o mesmo estilo de nota de prazo de RN-AGE-10 ("Até a Sprint 09 …").
- Riscos: (1) regra reescrita divergir do que o código faz: conferir cada trecho contra o arquivo do item de origem; (2) RN-COM-02: a decisão do dono diz Sprint 12, mas a situação "Encerramento solicitado" nasce da RN-MAT-18 (#86, Sprint 10); a especificação segue o dono (D2); (3) OpenAPI e telas não mudam, então nada além do texto pode ser dado como novo.

## 5. Tarefas

- [x] T1: reescrever em `13-comunicacao-e-notificacoes.md` RN-COM-01, 02, 06 a 09, 11 a 14 e RN-NOT-01 a 04, com as notas de prazo (Sprint 12, #375, #122, Sprint 11) — cobre CA1, CA2, CA3, CA4, CA6, CA7 — prova: `grep -nE "Sprint 12|#375|Avisar turma|NO_ADDRESS|receber tudo" docs/especificacao/13-comunicacao-e-notificacoes.md` e leitura contra os itens #78 a #84
- [x] T2: reescrever em `10-cobrancas.md` RN-COB-17 a 20 — cobre CA8 — prova: `grep -nE "dia exato|RECENT|Sprint 22|conta de pagamento" docs/especificacao/10-cobrancas.md`
- [x] T3: ajustar RN-AGE-10 em `06-turmas-e-agenda.md` (e a nota em `13`, se preciso) — cobre CA5 — prova: `grep -n "RN-AGE-10" docs/especificacao/06-turmas-e-agenda.md` sem a frase "vêm com o módulo de comunicados"
- [x] T4: conferir `02-contas-e-acesso.md` contra R1 e ajustar só se divergir — cobre CA9 — prova: `git diff docs/especificacao/02-contas-e-acesso.md` (vazio ou só o trecho divergente)
- [x] T5: corrigir o texto do CA5 em `specs/itens/81-comunicados-lista.md` — cobre CA10 — prova: `grep -n "^- CA5" specs/itens/81-comunicados-lista.md`
- [x] T6: conferência final — cobre CA11, CA12 — prova: `git diff --stat origin/dev` lista só `docs/especificacao/*.md` e `specs/*`; `grep -nE "^\| (374|375) " specs/backlog.md`

## 6. Decisões

- D1 (muda o comportamento): RN-COM-06 não muda de regra; ganha a nota de que a edição vem no #375 (Sprint 13). Decisão do dono de 24/10/2026 (issue #374). Alternativa: reescrever a regra para "só cancelar", descartada pelo dono.
- D2 (muda o comportamento): RN-COM-02 mantém "Encerramento solicitado" e registra que volta a valer na Sprint 12. Decisão do dono. Suposição: a situação nasce com a RN-MAT-18 (#86, Sprint 10) e as bloqueadas na Sprint 12; se o dono quiser a data da #86, só a nota muda.
- D3 (muda o comportamento): RN-COB-17 mantém "canais" e registra "só e-mail até a Sprint 22", quando a regra volta a valer; "desligar" é tirar o dia ou usar o interruptor geral. Decisão do dono.
- D4: a especificação descreve o comportamento, sem nome de classe, tabela nem fornecedor (AGENTS.md). Os códigos `RECENT`, `NO_ADDRESS` e `NOT_OPEN` podem aparecer porque são respostas da API, já contrato no OpenAPI.
- D5: `02` só muda se divergir, porque a tabela e o parágrafo do Professor já falam em "enviar comunicado" e "Comunicados".
- D6: no arquivo do #81 muda só o texto do CA5; a linha da Construção que registra o desvio fica (histórico).
- D7: as linhas do 374 e do 375 no backlog foram acrescentadas nesta fase, a pedido do `maestro`; nenhuma outra linha do backlog mudou.

## Construção

- T1 — arquivos: `docs/especificacao/13-comunicacao-e-notificacoes.md` (RN-COM-01, 02, 06 a 09, 11 a 14; RN-NOT-01, 03, 04) — prova: `grep -nE "Sprint 12|#375|NO_ADDRESS|receber tudo|#122"` → todos presentes
- T2 — arquivos: `docs/especificacao/10-cobrancas.md` (RN-COB-17, 20) — prova: grep `dia exato|RECENT|Sprint 22|conta de pagamento` → presentes
- T3 — arquivos: `docs/especificacao/06-turmas-e-agenda.md` (RN-AGE-10) — prova: grep `módulo de comunicados` e `Sprint 09` na linha → vazio; "Avisar turma" presente. Nota em `13` não foi preciso.
- T4 — `02-contas-e-acesso.md` conferido (tabela: Coordenador todas as unidades, Administrador U, Professor T*; parágrafo do Professor com "enviar comunicado"): bate com R1, sem alteração (`git diff` vazio)
- T5 — arquivos: `specs/itens/81-comunicados-lista.md` (CA5) — prova: grep `^- CA5` → texto novo
- T6 — prova: `git diff --stat` → só 3 arquivos de `docs/especificacao/`, `specs/backlog.md` (linhas 374 e 375 já existiam, Sprint 09 e 13, a fazer) e `specs/itens/81-...`; nada em backend, frontend, infra, openapi
Regressão: não se aplica (só texto de especificação; nenhum código tocado).
Desvios: em RN-NOT-01 o resumo diário foi descrito como "ainda não existe, volta na Sprint 13 (#122)". Edição feita com a ferramenta Edit (sem Python na máquina).

## Verificação

### Rodada 1 — CORRIGIR_ESPEC — 2026-10-24, estado b051913d0cf842e9ea10031906008e910718e6f2

| Critério | Status | Evidência |
|---|---|---|
| CA1 | PASSOU | diff de `13`: RN-COM-01/02 trazem limite do Administrador, modalidade com unidade, lista, cancelamento e "Sprint 12" |
| CA2 | PASSOU | RN-COM-06 cita #375 e Sprint 13 |
| CA3 | PASSOU | RN-COM-07 completa |
| CA4 | PASSOU | RN-COM-08/09 por aluno e "pessoas" distintas, e-mail à parte |
| CA5 | NÃO PASSOU | RN-AGE-10 foi reescrita, mas RN-AGE-11 foi colada na mesma linha (ver achado 1); além disso RN-AGE-05 (linha 33) ainda diz que nada é enviado até o módulo de comunicados e contradiz "cancelar uma aula não envia comunicado" |
| CA6 | PASSOU | RN-COM-11 a 14: 10 dias, catálogo de nove, campos, bounce, `NO_ADDRESS` |
| CA7 | PASSOU | RN-NOT-01/03/04, resumo diário adiado para #122 |
| CA8 | PASSOU | RN-COB-17/20 com dia exato, interruptor, Sprint 22, conta conectada, SMS, `RECENT` |
| CA9 | PASSOU | `02` sem diff |
| CA10 | PASSOU | CA5 do #81 reescrito |
| CA11 | PASSOU | `git diff --stat`: só `docs/especificacao/` e `specs/`; nada em backend, frontend, infra, openapi |
| CA12 | PASSOU | backlog tem 374 (Sprint 09) e 375 (Sprint 13), `a fazer` |

Comandos: `git diff --stat` e diff completo do estado; grep de "RN-AGE-1[01]", "módulo de comunicados", "Até a Sprint 09". Regressão: não se aplica (só texto).
Achados:
- obrigatório, especificação, `docs/especificacao/06-turmas-e-agenda.md` linha 38 — esperado: RN-AGE-10 e RN-AGE-11 em linhas separadas; observado: `...ainda não terminou.- **RN-AGE-11.** A mensalidade...` na mesma linha, RN-AGE-11 some da lista renderizada; evidência: diff do arquivo; corrigir: quebrar a linha antes de `- **RN-AGE-11.**`.
- obrigatório, especificação, `docs/especificacao/06-turmas-e-agenda.md` linha 33 (RN-AGE-05) — esperado: coerente com RN-AGE-10 (cancelar aula não envia comunicado; o aviso sai por "Avisar turma") e sem "até o módulo de comunicados (Sprint 09) ... nada é enviado"; observado: texto antigo mantido; evidência: grep "módulo de comunicados"; corrigir: reescrever RN-AGE-05 conforme o comportamento atual.
- sugestão, especificação — o item registra "Nota em `13` não foi preciso"; ok, sem ação.

### Correção da rodada 1
- achado 1 (RN-AGE-11 colada na RN-AGE-10) — arquivos: `docs/especificacao/06-turmas-e-agenda.md` linha 38 — prova: `sed -n 38,39p` → RN-AGE-10 e RN-AGE-11 em linhas separadas
- achado 2 (RN-AGE-05 contradizia RN-AGE-10) — arquivos: `docs/especificacao/06-turmas-e-agenda.md` linha 33 — prova: `grep -n "módulo de comunicados"` → vazio; RN-AGE-05 diz que cancelar não envia comunicado e o aviso sai por "Avisar turma"
Regressão: não se aplica (só texto de especificação).

### Rodada 2 — OK — 2026-10-24, estado 5070937afa054f56b73c48282f534fb987800fc2

| Critério | Status | Evidência |
|---|---|---|
| CA1 a CA4, CA6 a CA12 | PASSOU | mantido da rodada 1; o diff desde o estado da rodada 1 toca só `06` e este arquivo |
| CA5 | PASSOU | `06` linhas 33 e 38: RN-AGE-05 e RN-AGE-10 coerentes ("Cancelar uma aula não envia comunicado", "Avisar turma"); RN-AGE-11 em linha própria; grep "módulo de comunicados" e "Até a Sprint 09" em `06` e `13` vazio |

Comandos: `git diff --stat` do estado contra `dev` (só `docs/especificacao/` e `specs/`); `git diff` do estado da rodada 1; `sed -n 30,40p` de `06`; grep. Regressão: não se aplica (só texto).
Achados: os dois obrigatórios da rodada 1 estão corrigidos. Sem novos.

## Entrega

Atualiza a especificação com as decisões da Sprint 09 (ajuste A3 da revisão, achado R09-3): `docs/especificacao/06-turmas-e-agenda.md` (RN-AGE-05, RN-AGE-10, RN-AGE-11), `10-cobrancas.md` e `13-comunicacao-e-notificacoes.md` (RN-COM-06 ganha a nota de que a edição vem no #375, Sprint 13), mais o ajuste de `specs/itens/81-comunicados-lista.md` e as linhas do 374 e do 375 no backlog.

Verificação: rodada 2 `OK` (estado 5070937), todos os critérios passaram; regressão não se aplica (só texto de especificação).

Decisões: RN-COM-06 não muda de regra; a alternativa de reescrevê-la para "só cancelar" foi descartada pelo dono em 24/10/2026.

Limitações: nenhuma no código; a edição de comunicado agendado fica para o #375.
