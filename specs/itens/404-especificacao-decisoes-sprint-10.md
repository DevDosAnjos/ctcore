# 404 — Ajustar a especificação e o marco com as decisões da Sprint 10 (R10-3, D1, D2)

Origem: issue #404 (ajuste A3 da revisão da Sprint 10, achados R10-3 e R10-4, `specs/revisoes/sprint-10.md`) e respostas do dono de 28/10/2026 (D1, D2) no corpo da issue. Branch: `item/404-especificacao-decisoes-sprint-10`.

## 1. Objetivo

Levar para `docs/especificacao/` as 36 decisões "muda o comportamento" dos itens #85 a #94 e as respostas D1 e D2 do dono, para a especificação voltar a descrever o que o portal faz. Corrige também o texto do marco "Sprint 10 · Portal do aluno" no GitHub. Só texto: nenhum código, teste, OpenAPI ou migração. Mesma classe do #374 (Sprint 09).

Fora: mudar a Agenda para mostrar o nome do professor (é a #405, que depende deste); decisões "técnicas" dos itens (#95 D1 a D4, #363), que a revisão só manda registrar.

## 2. Requisitos

As 36 decisões: #85 D2 a D6; #86 D1 a D7; #87 D1 a D5; #88 D2, D3; #89 D2, D3, D5, D6; #90 D1, D2; #91 D2, D4; #92 D2, D3, D5; #93 D3, D4; #94 D2 a D5.

- R1 (matrícula, `09-matricula.md`): RN-MAT-12 e RN-MAT-14 — o uso de imagem nunca bloqueia o portal; nova versão dele com reaceite zera a concessão (consentimento é por texto) (#85 D3); o termo só está pendente para aluno com matrícula, e o reaceite vale quando a última versão aceita é menor que a maior publicada com reaceite (#85 D2). RN-MAT-13 — "modalidade nova" é a ativa criada depois do primeiro aceite do aluno e sem confirmação (cobre nova modalidade e reabertura; não cobre troca de unidade, turma ou plano); mostra o valor atual do plano, grava plano e valor mostrados, não bloqueia nem tem prazo, e quem confirma é quem aceita o termo (#85 D5). Boas-vindas saem na conclusão da matrícula: ao adulto com convite enviado e a cada responsável do menor com e-mail; endereço com problema é pulado (#85 D6).
- R2 (matrícula, pedidos): RN-MAT-16 — só pede quem aceita o termo (adulto com conta ou responsável financeiro do menor; o não financeiro só lê) (#86 D1); nova modalidade pede só unidade e modalidade (#86 D1); a gestão usa a permissão de gerir alunos no alcance de unidade, o Professor não vê (#86 D2); um pedido aberto por modalidade e tipo, e com encerramento aberto a modalidade não aceita troca de turma nem de plano (#86 D3); Aprovar marca o pedido na hora e abre o fluxo preenchido, sem alterar a matrícula; desistir do fluxo mantém o pedido Aprovado (#86 D4); prazos contam pela data local da unidade da modalidade (30 dias para expirar, 8 para encerrar) (#86 D7).
- R3 (matrícula, encerramento): RN-MAT-18 — encerramento não tem "Aprovar": a gestão encerra pelo fluxo normal (pedido vira Encerrado) ou recusa com mensagem (modalidade volta a Ativa) (#86 D5); ao encerrar, pela gestão ou pelo sistema, as cobranças em aberto com vencimento depois da data do pedido são canceladas e as anteriores seguem RN-MAT-17 (na via automática, continuam devidas); o sistema encerra quando a data local é pelo menos 8 dias depois do pedido, com a data do pedido como fim (#86 D6, D7). Resposta D1 do dono: o bullet "Pronto quando" de `09` passa a dizer "Um pedido de troca de turma, de plano ou de nova modalidade nunca altera a matrícula sem ação da gestão; o de encerramento segue a RN-MAT-18".
- R4 (cobranças, `10-cobrancas.md`): RN-COB-13 — envia o comprovante quem paga (adulto com conta ou responsável financeiro; o não financeiro não envia) (#87 D1); só cobrança aberta de unidade com chave Pix aceita comprovante (#87 D2); a gestão confirma/recusa com a permissão de cobranças completo (a de RN-COB-12) no alcance da unidade, o Professor não vê (#87 D3); confirmar grava meio Pix na chave, origem portal, data do envio e quem confirmou, com recibo opcional por e-mail (padrão marcado) (#87 D4); o prazo que volta de onde parou é um contador de dias pausados somado à data de bloqueio, contado pela data local da unidade do envio à recusa; vencimento e valor não mudam (#87 D5); cobrança em conferência continua visível ao aluno, sem "Pagar" (#89 D2). RN-COB-16 — o recibo baixado pelo portal sai com CPF mascarado; o completo só na sessão da gestão (#92 D5). RN-COB-14 — até a Sprint 11, "pagar junto" envia um comprovante para várias cobranças da mesma unidade, gravado um por cobrança e decidido um a um (#92 D2).
- R5 (portal, `14-portal-do-aluno.md`): RN-POR-03 — o bloqueio é do servidor (recusa em toda rota do portal fora o aceite e o uso de imagem; o front leva ao termo); responsável com vários filhos fica bloqueado até aceitar por todos os filhos pendentes (#85 D4). RN-POR-05/RF-POR-02 — o cartão escolhido filtra aulas, avisos e faixa; o total da família soma só o que a conta paga; o primeiro cartão é "Você", senão o primeiro filho por nome; a escolha não é gravada (#89 D6); o Início mostra todas as cobranças que pedem ação, não uma só (#89 D2); aula "alterada" é a com substituto ou editada à mão (#89 D3); selo "Nova" por 14 dias para graduação que não é de origem (#89 D5). RF-POR-01 — o sino conta avisos não lidos, não as notificações da gestão (#88 D2). RN-POR-08 — "reduzido" é calculado do que existe: aluno matriculado sem modalidade ativa; com vários alunos, vale se nenhum está ativo; bloqueio por atraso, reativação e fim do acesso do ex-aluno entram na Sprint 12 (#88 D3).
- R6 (portal, telas): RF-POR-03 — selo "Ativo" é ter modalidade ativa, "Inativo" cobre inativo e ex-aluno, data do dia no fuso da unidade de cadastro (#90 D1); o responsável vê a carteirinha de todo filho ligado, pagador ou não (#90 D2). RF-POR-04 — padrão "Todos os meus horários"; "Só minha turma" mostra só a turma principal de cada modalidade ativa (#91 D2); "Ver aviso" aponta para o comunicado enviado mais recente do tipo certo que alcança a turma, ou some se não houver (#91 D4). RF-POR-05 — o débito automático aparece como cartão informativo, sem ativar nada, até a Sprint 11 (#92 D3). RF-POR-06 — "Marcar todos como lidos" grava dispensa por conta e aviso (vale para todos os alunos da conta, tira destaque, sino e não lidos), não leitura; a gestão segue vendo zero; aviso novo volta a contar (#93 D3); o aviso só é lido quando a pessoa o abre (inclusive por link) (#93 D4). RF-POR-07 — qualquer conta ligada ao aluno edita contato, foto e preferências; ver não exige ser pagador (#94 D2); a troca de e-mail é a de Minha conta (RN-ACE-15) e o e-mail de contato do cadastro da conta acompanha o novo endereço, em todas as organizações da conta, com auditoria de origem portal; o de menor sem conta não muda (#94 D3); "Baixar meus dados" e "Excluir minha conta" ficam visíveis e desabilitados até a Sprint 13 (#94 D4); no portal reduzido a aba traz dados pessoais, contato da unidade, preferência, termos, Minha conta e ações de dados, sem treinos, plano, graduação nem pedidos; editar contato continua (#94 D5).
- R7 (comunicação e auditoria): `13` RN-COM-08 — "Marcar todos como lidos" registra dispensa, não leitura (#93 D3); `03` RN-AUD-02 — a dispensa, o aceite, o pedido e a troca de e-mail pelo portal entram na auditoria com origem portal (#93 D3, #94 D3, #86); ações de LGPD do portal (#94 D4) têm nota de que são ligadas na Sprint 13.
- R8 (D2 do dono, professor): `14` ganha a regra nova: o portal mostra o nome do professor (só o nome) ao aluno matriculado na turma ou na aula, em todas as telas (Agenda, aviso, treinos por modalidade); nunca foto, graduação, telefone nem e-mail. RN-POR-06 continua valendo para o contato. `05` RN-PRO-04 ganha a justificativa de que não contradiz: ela trata do diretório e dos horários públicos (anônimos); o portal é autenticado e restrito ao matriculado. RF-POR-04 passa a listar o professor na aula.
- R9: cada decisão D* marcada "muda o comportamento" de #85 a #94 aponta a regra ajustada, conferido por `grep`: cada arquivo `specs/itens/85` a `94` recebe, em "Decisões", ao final da linha da D*, a referência `→ RN-…` (só a referência; texto da decisão não muda). Exceção a verificar: o histórico de Construção e Verificação desses arquivos não se edita.
- R10: marco da Sprint 10 no GitHub: o bullet "Um pedido do portal nunca altera a matrícula sem ação da gestão" vira o texto de D1 (R3); o restante da descrição fica.
- R11: o 404 e o 405 constam no backlog (já constam); nenhuma linha muda além da situação.

## 3. Critérios de aceite

- CA1 (R1): `09` RN-MAT-12/13/14 trazem uso de imagem sem bloqueio, termo só com matrícula, definição de "modalidade nova" e boas-vindas.
- CA2 (R2): `09` RN-MAT-16 traz quem pede, a permissão da gestão, um pedido aberto por modalidade e tipo, Aprovar na hora e prazos pela data local.
- CA3 (R3, R10): `09` RN-MAT-18 traz Recusar, cancelamento das cobranças posteriores, 8º dia; o bullet "Pronto quando" de `09` e o marco do GitHub têm o texto da D1 e não o texto antigo.
- CA4 (R4): `10` RN-COB-13/14/16 e o `11` (se citar a conferência) dizem quem envia, chave Pix, permissão, dados do pagamento, dias pausados, recibo mascarado, "sem Pagar" em conferência, pagar junto.
- CA5 (R5, R6): `14` RN-POR-03/05/08, RF-POR-01 a 07 trazem cada ponto das decisões acima; `12` RN-INA-08 fica coerente com o "reduzido" calculado.
- CA6 (R7): `13` RN-COM-08 e `03` RN-AUD-02 trazem dispensa e origem portal.
- CA7 (R8): a regra do nome do professor existe em `14` e a justificativa em `05` RN-PRO-04; RN-POR-06 inalterada.
- CA8 (R9): para as 36 decisões, `grep -c "→ RN-\|→ RF-" ` nas linhas "muda o comportamento" de cada arquivo 85 a 94 dá a contagem esperada (5, 7, 5, 2, 4, 2, 2, 3, 2, 4).
- CA9 (todos): `git diff --stat origin/dev` lista só `docs/especificacao/*.md` e `specs/*`; nada em `backend/`, `frontend/`, `infra/` nem `docs/openapi.yaml`.

## 4. Plano técnico

- Dados, API, Interface, Segurança: não mudam (só texto).
- Arquivos (vistos): `docs/especificacao/09-matricula.md` (RN-MAT-12 a 18, linhas 35 a 44; "Pronto quando", linha 60); `10-cobrancas.md` (RN-COB-13, 14, 16); `14-portal-do-aluno.md` (inteiro, 4,7 KB); `13-comunicacao-e-notificacoes.md` (RN-COM-08, linha 29); `03-auditoria-e-lgpd.md` (RN-AUD-02, linha 20); `05-professores.md` (RN-PRO-04, linha 20); `12-inativacao.md` (RN-INA-08, linha 38; tabela de situações, linha 16 — só conferir); `08-alunos-e-responsaveis.md` (RN-ALU-11 e RN-RSP-02, só conferir contra #94 D2/D3); `specs/itens/85` a `94` (linhas "Decisões" da D* "muda o comportamento"; arquivos vistos).
- Marco: GitHub, milestone 11 (`gh api repos/DevDosAnjos/ctcore/milestones/11`); o `docs/plano-de-sprints.md` só tem a descrição curta e não muda.
- Reuso: o sentido de cada decisão está em `specs/itens/85` a `94`, "Decisões". Escrever o comportamento, sem número de decisão, nome de classe, tabela, coluna ou fornecedor (a especificação não cita código). Códigos de resposta de API que já são contrato (ex.: `TERM_PENDING`) não entram; basta "o servidor recusa". Notas de prazo no estilo do #374 ("volta a valer na Sprint 11/12/13").
- Riscos: (1) texto divergir do que o código faz: conferir cada trecho contra a decisão do item; (2) `10-cobrancas.md` e `11` descrevem "pagar" por Mercado Pago: a regra de "Pix na chave com comprovante até a Sprint 11" deve ficar como nota de prazo, não substituir a regra final; (3) mexer no marco é fora do repositório: registrar o texto antigo e o novo na Entrega; (4) a regra do professor mudar RN-PRO-04 só por nota, sem alterar o consentimento do diretório.

## 5. Tarefas

- [x] T1: `09-matricula.md` — reescrever RN-MAT-12, 13, 14, 16 e 18 e o bullet do "Pronto quando" (D1 do dono) — cobre CA1, CA2, CA3 — prova: `grep -nE "uso de imagem|modalidade nova|Aprovar|data local|Recusar|nunca altera a matrícula" docs/especificacao/09-matricula.md`
- [x] T2: `10-cobrancas.md` (RN-COB-13, 14, 16) e conferir `11-pagamentos-mercado-pago.md` RN-PAG-17 — cobre CA4 — prova: `grep -nE "responsável financeiro|dias pausados|mascarado|sem .Pagar." docs/especificacao/10-cobrancas.md`
- [x] T3: `14-portal-do-aluno.md` — RN-POR-03, 05, 06 (inalterada), 08 e RF-POR-01 a 07, mais a regra do nome do professor (D2 do dono) — cobre CA5, CA7 — prova: `grep -nE "nome do professor|dispensa|Todos os meus horários|desabilitad|informativo|reduzido" docs/especificacao/14-portal-do-aluno.md`
- [x] T4: `05-professores.md` RN-PRO-04 (justificativa), `13` RN-COM-08, `03` RN-AUD-02, e conferir `12` RN-INA-08 e `08` RN-ALU-11/RSP-02 (ajustar só se divergirem) — cobre CA5, CA6, CA7 — prova: `grep -nE "portal" docs/especificacao/05-professores.md docs/especificacao/03-auditoria-e-lgpd.md` e `git diff docs/especificacao/05-professores.md`
- [x] T5: em `specs/itens/85` a `94`, acrescentar `→ <regra>` ao fim de cada linha de decisão "muda o comportamento" (36 linhas), sem tocar em Construção e Verificação — cobre CA8 — prova: `for n in 85 86 87 88 89 90 91 92 93 94; do grep -E "^- D[0-9]+ \(muda" specs/itens/$n-*.md | grep -c "→ R"; done` dá 5 7 5 2 4 2 2 3 2 4
- [x] T6: corrigir a descrição do marco 11 no GitHub com `gh api -X PATCH repos/DevDosAnjos/ctcore/milestones/11 -f description=…` (só o bullet; o resto igual) — cobre CA3 — prova: `gh api repos/DevDosAnjos/ctcore/milestones/11 --jq .description | grep -n "encerramento segue a RN-MAT-18"`
- [x] T7: conferência final — cobre CA9 — prova: `git diff --stat origin/dev` só `docs/especificacao/*.md` e `specs/*`; `bash infra/scripts/changed-areas.test.sh` não é necessário

## 6. Decisões

- D1 (muda o comportamento): o portal não altera a matrícula sem ação da gestão, salvo o encerramento, que segue a RN-MAT-18 (o sistema encerra no 8º dia com a data do pedido). Decisão do dono de 28/10/2026 (issue #404, D1). Marco e `09` ganham o mesmo texto. Alternativa: tirar o encerramento automático; descartada pelo dono.
- D2 (muda o comportamento): o portal mostra o nome do professor, só o nome, ao aluno matriculado na turma ou na aula, em todas as telas; nunca foto, graduação, telefone nem e-mail. Não contradiz a RN-PRO-04, que trata do diretório e dos horários públicos; o portal é autenticado e restrito ao matriculado. RN-POR-06 vale para o contato. Decisão do dono de 28/10/2026 (issue #404, D2). A mudança na Agenda é a #405.
- D3: a especificação descreve o comportamento sem nome de classe, tabela, coluna nem código de erro interno (AGENTS.md); os nomes de permissão vão pela tabela de `02` ("gerir alunos", "cobranças completo").
- D4: a referência `→ regra` em `specs/itens/85` a `94` é só um sufixo na linha da D*; a Construção e a Verificação desses arquivos ficam como estão (histórico). Suposição: a conferência do `grep` do "Pronto quando" aceita esse sufixo.
- D5: a nota sobre "pagar" com comprovante vale até a Sprint 11 e fica como nota de prazo em `10`/`14`; a regra final do pagamento online (`11`) não muda.
- D6: a descrição do marco é editada com `gh api` na fase 3; o agente só altera o bullet de D1, mantendo o restante, e registra o texto antigo e o novo na Entrega.

## Construção

- T1 — arquivos: `docs/especificacao/09-matricula.md` (RN-MAT-11 a 14, 16, 18 e "Pronto quando") — prova: `grep -nE "uso de imagem|modalidade nova|Aprovar|data local|Recusar|nunca altera a matrícula"` → todos os termos presentes (a RN-MAT-18 diz "recusa com mensagem").
- T2 — arquivos: `docs/especificacao/10-cobrancas.md` (RN-COB-13, 14, 16); `11` RN-PAG-17 conferida, já descreve Pix na chave com comprovante, sem mudança — prova: `grep -nE "responsável financeiro|dias pausados|mascarado|sem .Pagar."` → presentes.
- T3 — arquivos: `docs/especificacao/14-portal-do-aluno.md` (RN-POR-03, 05, 08, RF-POR-01 a 07; nova RN-POR-06a para o nome do professor, RN-POR-06 inalterada) — prova: `grep -nE "nome do professor|dispensa|Todos os meus horários|desabilitad|informativo|reduzido"` → presentes.
- T4 — arquivos: `05-professores.md` (RN-PRO-04, nota), `13` RN-COM-08, `03` RN-AUD-02, `12` RN-INA-08 (nota do "reduzido" calculado); `08` RN-ALU-11/RSP-02 conferidas, coerentes, sem mudança — prova: `git diff` dos arquivos → só as linhas citadas.
- T5 — arquivos: `specs/itens/85` a `94` (sufixo `→ regra` nas 36 linhas D* "muda o comportamento") — prova: laço `grep -c "→ R"` → 5 7 5 2 4 2 2 3 2 4.
- T6 — marco 11 no GitHub, só o bullet. Texto antigo: "- Um pedido do portal nunca altera a matrícula sem ação da gestão." Novo: "- Um pedido de troca de turma, de plano ou de nova modalidade nunca altera a matrícula sem ação da gestão; o de encerramento segue a RN-MAT-18." — prova: `gh api repos/DevDosAnjos/ctcore/milestones/11 --jq .description | grep -n "encerramento segue a RN-MAT-18"` → linha 10.
- T7 — prova: `git diff --stat origin/dev` → 17 arquivos, só `docs/especificacao/*.md` e `specs/*`; nada em backend, frontend, infra nem openapi.
Regressão: não se aplica (só texto de especificação e marco); nenhum teste de código rodou.
Desvios: a regra do professor ficou como RN-POR-06a (nova numeração, para não alterar a RN-POR-06); o arquivo do item 404 é novo, ainda sem commit (fase 5).
Não é do item: nenhum.

## Verificação

### Rodada 1 — OK — 2026-10-28, estado 3841ddf0607e9dbbbaedc84123bcb37eb6d703fb

| Critério | Status | Evidência |
|---|---|---|
| CA1 | PASSOU | diff de `09`: RN-MAT-11/12/13/14 com termo só com matrícula, imagem sem bloqueio, definição de "modalidade nova", boas-vindas |
| CA2 | PASSOU | RN-MAT-16 traz quem pede, permissão de gerir alunos, um pedido por modalidade e tipo, Aprovar na hora, data local |
| CA3 | PASSOU | RN-MAT-18 com recusa, cancelamento posterior, 8º dia; "Pronto quando" de `09` e marco 11 (`gh api`, linha 10) com o texto da D1 |
| CA4 | PASSOU | `10` RN-COB-13/14/16 com todos os pontos; `11` não precisou mudar |
| CA5 | PASSOU | `14` RN-POR-03/05/08 e RF-POR-01 a 07 conferidos no diff; `12` RN-INA-08 coerente |
| CA6 | PASSOU | `13` RN-COM-08 e `03` RN-AUD-02 no diff |
| CA7 | PASSOU | RN-POR-06a nova em `14`, nota em `05` RN-PRO-04; RN-POR-06 sem diff |
| CA8 | PASSOU | laço de grep: 5 7 5 2 4 2 2 3 2 4; todas as linhas alteradas em `specs/itens/85-94` são linhas "D* (muda"; Construção/Verificação intactas |
| CA9 | PASSOU | `git diff --stat origin/dev`: 17 arquivos, só `docs/especificacao/*.md` e `specs/itens/*`; nada em backend, frontend, infra, openapi |

Comandos: `git diff --stat origin/dev`; laço grep de CA8; `gh api repos/DevDosAnjos/ctcore/milestones/11 --jq .description | grep -n "encerramento segue"` (linha 10); diff das especificações lido. Testes de código: não se aplicam (só texto).
Achados:
- sugestão, especificação, `14` RN-POR-06a — a regra do professor usa sufixo "a" em vez de número próprio; aceitável para preservar a RN-POR-06.

## Entrega

Só texto. Leva para `docs/especificacao/` as decisões "muda o comportamento" dos itens #85 a #94 (36 linhas) e as respostas D1 e D2 do dono (issue #404), para a especificação descrever o que o portal faz.

- Matrícula (`09`): RN-MAT-11 a 14, 16 e 18 reescritas; uso de imagem nunca bloqueia; "modalidade nova" definida; pedidos só de quem aceita o termo; um pedido aberto por modalidade e tipo; encerramento pelo fluxo normal ou recusa, com cancelamento das cobranças posteriores ao pedido e encerramento automático no 8º dia.
- Cobranças (`10`): RN-COB-13 (comprovante Pix na chave, confirmação pela gestão), RN-COB-14 (pagar junto, nota de prazo até a Sprint 11), RN-COB-16 (recibo com CPF mascarado no portal).
- Portal (`14`): RN-POR-03, 05, 08 e RF-POR-01 a 07; nova RN-POR-06a (nome do professor, só o nome; RN-POR-06 inalterada).
- Demais: `05` RN-PRO-04 (nota), `13` RN-COM-08, `03` RN-AUD-02, `12` RN-INA-08 (nota do "reduzido").
- Sufixo `→ regra` nas linhas "muda o comportamento" de `specs/itens/85` a `94`; Construção e Verificação desses arquivos ficaram como estão.
- Marco 11 no GitHub, só o bullet de D1. Texto antigo: "- Um pedido do portal nunca altera a matrícula sem ação da gestão." Texto novo: "- Um pedido de troca de turma, de plano ou de nova modalidade nunca altera a matrícula sem ação da gestão; o de encerramento segue a RN-MAT-18."

Verificação: rodada 1, OK (CA1 a CA9). Regressão: não se aplica (só texto); nenhum teste de código rodou.

Decisões que quem lê precisa saber: a regra do professor ficou como RN-POR-06a para não renumerar a RN-POR-06. A nota de "pagar" com comprovante vale até a Sprint 11 e não substitui a regra final de `11`.

Limitações: o texto da especificação não tem como provar que o código já faz o descrito; a conferência foi feita contra as decisões dos itens #85 a #94. A Agenda com o nome do professor é a #405, que depende deste item.
