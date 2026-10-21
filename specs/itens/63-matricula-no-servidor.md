# 63 — Matrícula no servidor: modalidades, transferência e retorno

Origem: issue #63 (Sprint 07), com os blocos "Encaixe herdado das Sprints 04 e 05" e "Encaixe herdado da Sprint 06" no fim do corpo; `docs/especificacao/09-matricula.md` (RN-MAT-01, 02, 04, 15, 19, 20), `01` (RN-ORG-12), `08` (RN-ALU-07, RF-ALU-01 a 03), `02` (permissões do Professor), `04` (RN-MOD-06), `06` (RN-TUR-02 a 04); `specs/revisoes/sprint-06.md` ("Itens para as próximas sprints", S2 e S8). Branch: `item/63-matricula-no-servidor`. Parte de `Students`, `StudentRecords`, `StudentController`, `ModalityUsage`, `UnitDeactivationBlockers` e `TrainingClasses`, todos com um "ponto único a trocar" que espera a matrícula.

O item é grande demais para uma sessão (decisão do dono, revisão da Sprint 06). Está dividido em **seis partes**, cada uma construível, verificável e entregável em ordem; a numeração de R, CA, T e D é única no arquivo. A parte seguinte só começa com a anterior concluída. O PR e o commit continuam sendo um só por item (`#63: Matrícula no servidor: modalidades, transferência e retorno`, `Closes #63`), no fim da Parte 6.

| Parte | Entrega | Requisitos | Tarefas |
| --- | --- | --- | --- |
| 1 | Dados e matrícula: criar, adicionar modalidade, retorno | R1 a R8 | T1 a T5 |
| 2 | Troca de turma, troca de plano e transferência | R9 a R11 | T6 a T9 |
| 3 | Alcance por modalidade e contagens reais | R12 a R17 | T10 a T14 |
| 4 | Lista de alunos, lista simples e selo Novo | R18 a R22 | T15 a T19 |
| 5 | Ficha do aluno: abas, ações e janelas | R23 a R27 | T20 a T24 |
| 6 | Datas no fuso da unidade e fechamento | R28 a R29 | T25 a T26 |

## 1. Objetivo

Dar ao servidor a matrícula do aluno: uma matrícula por aluno na organização, com até 10 modalidades ativas, cada uma com unidade, turma principal, outros horários, plano e vencimento; troca de turma (na hora), troca de plano (a partir da próxima cobrança), transferência entre unidades e retorno de ex-aluno. Com a matrícula passam a existir o aluno Ativo, o selo Novo e o alcance por modalidade (RN-ALU-07), e os pontos que hoje devolvem 0 ou falso (`ModalityUsage.activeStudents`, `UnitDeactivationBlockers.activeStudents`, `TrainingClasses.activeStudents`, `StudentRecords.teacherReach`) passam a ler dado real. A lista e a ficha do aluno ganham o que dependia da matrícula, e as janelas Trocar turma, Trocar plano e Transferir entram na ficha.

Fora do escopo: a tela "Nova matrícula em etapas" e os avisos de faixa etária e capacidade (#67, #68); encerrar modalidade (#69, RN-MAT-17); benefício (#64 a #66: a matrícula nasce sem benefício, D4); cobrança, taxa de matrícula, primeira e segunda cobrança, isenção e débito antigo (RN-MAT-05, 06, 07 e o que a RN-MAT-19 diz de cobrança; Sprint 08 em diante, D5); termo de matrícula e selo "Termo pendente" (RN-MAT-11 a 14, #85); convite do portal na conclusão (RN-MAT-08, #67); graduação de origem por modalidade (RN-MAT-09, #105 e #106; a transferência não a toca, então ela se mantém); aula experimental (RN-MAT-10); pedidos do portal (RN-MAT-16); anonimização (RF-ALU-05); selos "Bolsista", situação financeira e filtros Em atraso e Bloqueados (#65, #72, #109); próximo vencimento e pendências no Resumo da ficha (#73, S5); notas de versão (Sprint 15, como #54).

## 2. Requisitos

**Parte 1 — Dados e matrícula**

- R1 (RN-MAT-01): cada aluno tem no máximo uma matrícula na organização; só a gestão a cria ou altera; ela cobre várias modalidades e unidades, com no máximo 10 modalidades ativas; o aluno tem no máximo uma modalidade ativa de cada tipo (D3).
- R2 (RN-MAT-02): cada modalidade da matrícula guarda unidade, turma principal, outros horários (opcional, outras turmas da mesma modalidade e unidade), plano e dia de vencimento; o benefício fica para a #65 (D4).
- R3: `POST /api/v1/students/{id}/modalities` grava a matrícula e as modalidades pedidas, tudo ou nada: cria a matrícula se não existe, adiciona modalidades se existe, ou reabre se todas estão encerradas (R7). Cada modalidade só é aceita com: unidade ativa e no alcance de quem chama, modalidade ativa e oferecida pela unidade, turma principal ativa da unidade e da modalidade, outros horários ativos da mesma unidade e modalidade e diferentes da principal, plano ativo da unidade e da modalidade, dia de vencimento entre os dias permitidos da unidade (`due_days`) (D6).
- R4 (RN-MAT-03): o dia de vencimento é igual para todas as modalidades ativas do aluno na mesma unidade; uma modalidade nova numa unidade onde ele já tem outra ativa herda o dia dela e recusa dia diferente.
- R5 (RN-MAT-04): ao concluir a matrícula o aluno já fica Ativo: a situação do aluno é calculada (Ativo com ao menos uma modalidade ativa; Ex-aluno com matrícula e nenhuma ativa; sem matrícula, "Sem matrícula") e não gravada (D7). A data de matrícula (`enrolled_at`) nasce na conclusão e é a que o selo "Novo" lê.
- R6: `GET /api/v1/students/{id}/enrollment` devolve a matrícula: situação, data, e cada modalidade (unidade, modalidade, turma principal, outros horários, plano, plano agendado, vencimento, situação, `canEdit` conforme o alcance de quem chama). Aluno sem matrícula: 200 com situação "Sem matrícula" e lista vazia.
- R7 (RN-MAT-19): o retorno de ex-aluno reabre a mesma matrícula (mesmo `enrollment`), achada pela busca por CPF ou e-mail, mantendo o histórico (auditoria, modalidades encerradas) e a graduação; as modalidades novas entram como na R3 e a data `reopened_at` passa a valer para o selo Novo (D8). Se os dados foram anonimizados (CPF e e-mail apagados), a busca não acha e o cadastro é novo, sem código novo (D9).
- R8: `GET /students/lookup` aceita `cpf` ou `email` (um dos dois) e devolve também a situação (`enrollmentStatus`: `NONE`, `ACTIVE`, `FORMER`), para a tela decidir entre matrícula nova, nova modalidade e retorno; segue só id, nome e foto mais a situação (RN-ALU-06), de qualquer unidade da organização.

**Parte 2 — Troca de turma, troca de plano e transferência**

- R9 (RN-MAT-15): `PUT /students/{id}/modalities/{modalityRowId}/class` troca a turma principal e os outros horários, e vale na hora (a leitura seguinte já mostra a nova turma). As turmas seguem as regras da R3, na unidade da modalidade.
- R10 (RN-MAT-15): `PUT /students/{id}/modalities/{modalityRowId}/plan` guarda o plano novo como `next_plan_id`, que vale a partir da próxima cobrança gerada; o plano atual não muda até lá, e pedir o plano atual limpa o agendamento. O plano segue as regras da R3. A Sprint 08 aplica o plano agendado ao gerar a próxima cobrança (D10).
- R11 (RN-MAT-20, RN-ORG-12): `POST /students/{id}/modalities/{modalityRowId}/transfer` leva a modalidade para outra unidade da mesma organização, com turma principal, outros horários, plano e vencimento escolhidos para a unidade nova (R3 e R4 na unidade nova); mantém a mesma linha (histórico, graduação e data de início) e grava origem e destino na auditoria; cobranças já geradas ficam na unidade de origem (a cobrança guarda a própria unidade; nada a reescreve). Unidade igual à atual, ou outra organização, é recusada.

**Parte 3 — Alcance por modalidade e contagens**

- R12 (encaixe Sprint 04 e 05): `ModalityUsage.activeStudents` conta alunos distintos com a modalidade ativa na organização; desativar uma modalidade com aluno ativo dá 409 `MODALITY_IN_USE` com `activeStudents` maior que 0 nos campos, e a tela de modalidades mostra o que impede (RN-MOD-06).
- R13: `UnitDeactivationBlockers.activeStudents` conta alunos com modalidade ativa na unidade, e `TrainingClasses` passa a contar alunos ativos da turma (principal ou outro horário), listá-los com a marca de principal e contar os que a têm como principal (RN-TUR-02, 03, 04; desativar turma com aluno principal é recusado).
- R14 (RN-ALU-07 completa): o alcance do Administrador sobre um aluno é ter, em alguma unidade do alcance dele, ao menos uma modalidade (ativa ou encerrada); aluno sem nenhuma modalidade continua alcançado pela unidade de cadastro (D11). `Students.list`, `detail`, `reachPredicate`, `row` e `birthdays` usam esse ponto único. A lista filtrada por uma unidade mostra só quem tem modalidade nela. O Coordenador alcança todos.
- R15 (RN-ALU-07): o Administrador da unidade A vê, na ficha e na matrícula do aluno, as modalidades da unidade B, mas só altera (troca de turma, de plano, transferência) as da sua unidade; tentativa na B dá 403 (D12); dados pessoais continuam alteráveis por qualquer Administrador de unidade onde o aluno tem modalidade.
- R16 (encaixe Sprint 06): `StudentRecords.teacherReach` devolve verdadeiro para o aluno que está em turma ativa do Professor (titular ou auxiliar, como `TeacherAccess` e o painel da #43 definem), com a modalidade ativa do aluno ligada a essa turma (principal ou outro horário), e a chave "ver ficha do aluno" (`STUDENT_RECORD`) e a de "ver contatos" (`STUDENT_CONTACTS`) conferidas **na unidade da turma** (`Permissions.grant(who, permission, unitId)`), não em qualquer unidade: chave ligada em outra unidade não vale. Ficha de aluno de outra turma: 404; chave desligada na unidade da turma: 403.
- R17 (encaixe Sprint 06): a ficha do Professor só tem campos de uma lista permitida (nunca valores em reais, plano, benefício, mensalidade, CPF, endereço nem situação financeira), garantida por teste que reprova campo novo de `RecordView` fora da lista; `GET /students/birthdays` aplica matrícula ativa (aluno com ao menos uma modalidade ativa) e, para o Professor, o `teacherReach` real; o handler MSW do Professor deixa de devolver `[]`.

**Parte 4 — Lista e selo**

- R18 (RF-ALU-01): a linha da lista traz as modalidades ativas do aluno com unidade, turma principal e nível de graduação (nulo até a #105 e #106, D13) e a situação (Ativo, Ex-aluno, Sem matrícula); a lista mostra a coluna "Modalidades" e o aluno com várias modalidades mostra todas.
- R19 (RF-ALU-01): filtros por modalidade, por turma (principal ou outro horário) e por nível (aceito e vazio de resultado até a #105, D13), combináveis com os atuais; filtro de unidade e de turma inexistente ou de outra organização: 404 como o de faixa etária.
- R20 (RF-ALU-01): filtros rápidos "Ativos" e "Ex-alunos" com a contagem de cada um (`GET /students` devolve `counts` com `active` e `former`, calculados sobre o alcance de quem chama e os filtros de unidade, ignorando o próprio filtro rápido).
- R21 (RF-ALU-02): o selo `NEW` ("Novo") vale até 30 dias da matrícula, contando da data mais recente de `enrolled_at` ou `reopened_at` no fuso da unidade de cadastro, com o dia 30 incluso e o 31 fora; aparece na lista, na ficha e no filtro por selo.
- R22 (`02`): a lista simples do Professor (`GET /students/simple?classId=`) devolve nome, foto, faixa e `blocked` dos alunos ativos de uma turma dele ou de uma aula dele, sem depender das chaves do Professor, sem CPF, contato, valores nem selos; `blocked` é sempre falso até a #109 (D14). Turma que não é dele: 404. A gestão chama a mesma rota com o alcance de unidade. Quem tem a chave desligada continua vendo a lista simples e não abre a ficha.

**Parte 5 — Ficha**

- R23 (RF-ALU-03): a aba "Matrícula e modalidades" da ficha deixa o estado vazio explicativo e mostra a matrícula (R6): modalidades com unidade, turma, plano, vencimento, situação, plano agendado e as ações Trocar turma, Trocar plano e Transferir (por modalidade, só nas editáveis) e Encerrar (desligada com a dica "disponível em breve" até a #69, D15).
- R24 (RF-ALU-03): o Resumo da ficha mostra as modalidades com turma e a situação do aluno no cabeçalho (Ativo, Ex-aluno, Sem matrícula); sem valores, que chegam com a cobrança (S5).
- R25 (D16): as janelas Trocar turma (`TrocarTurma.dc.html`), Trocar plano (`TrocarPlano.dc.html`) e Transferir para outra unidade (`TransferirModalidade.dc.html`) chamam as rotas das R9 a R11 e mostram os erros do servidor; Trocar plano informa "vale a partir da próxima cobrança" e Trocar turma "vale na hora".
- R26 (encaixe Sprint 06, R06-9, S2): a ficha do Professor não mostra botões de escrita; ele continua vendo as abas Resumo e Responsáveis (os responsáveis, sem papel financeiro nem acesso, `08` RF-ALU-03) e não vê Histórico, que é só da gestão; os botões Tornar financeiro, Desvincular, Reenviar convite e Vincular responsável só aparecem a quem tem `STUDENT_MANAGE`.
- R27 (AGENTS): as telas e janelas novas têm os quatro estados (carregando, vazio, erro, sem permissão), funcionam a partir de 360 px, passam no axe nos dois temas e têm os textos em `pt-BR.json`.

**Parte 6 — Datas e fechamento**

- R28 (encaixe Sprint 06, S8): `StudentRegistration.personal`, `InviteService`, `MajorityNoticeJob` e `StudentGuardians` (linha 249) usam a data de hoje no fuso da unidade de cadastro em vez do UTC; a matrícula e o selo Novo já nascem assim; às 23h30 de Brasília o aluno de 18 anos no dia seguinte (UTC) ainda é menor.
- R29 (AGENTS, RNF-29, RNF-32): toda rota nova está no `docs/openapi.yaml` e nos handlers MSW; toda rota nova tem teste de isolamento entre organizações e entre perfis; toda ação que altera dado grava auditoria sem dado sensível; o código de cada regra citada (RN-MAT-01, 02, 04, 15, 19, 20, RN-ORG-12, RN-ALU-07) aparece por nome em ao menos um teste; seed `infra/seed/14-matriculas.sql` idempotente; cobertura mínima mantida.

## 3. Critérios de aceite

**Parte 1**

- CA1 (R1): dado aluno sem matrícula e Administrador da unidade A, quando chama `POST /students/{id}/modalities` com uma modalidade válida, então 201 e uma matrícula; chamar de novo com outra modalidade adiciona à mesma matrícula (um só `enrollment` para o aluno, confirmado por consulta ao banco); duas chamadas simultâneas não criam duas matrículas.
- CA2 (R1): dado aluno com 10 modalidades ativas, quando tenta a 11ª, então 422 com o campo `modalities` e nada é gravado; com 9, a 10ª entra (limite 10 inclusivo). A mesma modalidade ativa duas vezes para o aluno: 409.
- CA3 (R2, R3): dada a matrícula com turma principal e dois outros horários, quando lida, então traz unidade, turma, outros horários, plano e vencimento; turma inativa, de outra modalidade, de outra unidade ou de outra organização: 422 (ou 404 se de outra organização) e nada gravado; outro horário igual à principal: 422; plano inativo ou de outra unidade ou modalidade: 422; modalidade não oferecida pela unidade ou inativa: 422; unidade inativa: 422.
- CA4 (R3): dado um pedido com duas modalidades em que a segunda é inválida, quando enviado, então nenhuma é gravada (tudo ou nada) e a auditoria não ganha registro.
- CA5 (R3, R4): dia de vencimento fora de `due_days` da unidade: 422; aluno com modalidade ativa na unidade A (dia 5) pedindo nova modalidade na A com dia 10: 422; com dia 5: aceita; na unidade B, dia livre entre os dela.
- CA6 (R5): dado a matrícula concluída, quando se lê a ficha ou `/enrollment`, então a situação é Ativo e `enrolledAt` está preenchida; aluno só cadastrado: "Sem matrícula"; com todas as modalidades encerradas: Ex-aluno.
- CA7 (R6): `GET /students/{id}/enrollment` de aluno sem matrícula devolve 200 com situação "Sem matrícula"; de outra organização, 404; modalidade na unidade B lida pelo Administrador da A vem com `canEdit` falso.
- CA8 (R7): dado ex-aluno (todas as modalidades encerradas) achado por CPF ou por e-mail, quando a gestão matricula uma modalidade nova, então a mesma matrícula é reaberta (mesmo id, `reopened_at` preenchida, modalidades antigas mantidas como encerradas) e o aluno volta a Ativo; a auditoria registra a reabertura.
- CA9 (R7): dado aluno cujo CPF e e-mail foram apagados (simulado no teste), quando se busca pelos dados antigos, então não há resultado e o cadastro novo cria outro aluno.
- CA10 (R8): `GET /students/lookup?email=` e `?cpf=` acham o aluno; sem nenhum dos dois, 400; com os dois, 400; resposta traz `enrollmentStatus` correto nos três casos e só id, nome, foto e situação; aluno de outra organização: 404.
- CA11 (R3, R29): dado Professor, Aluno ou Responsável, quando chama qualquer rota nova de escrita ou leitura da matrícula, então 403; sem sessão, 401; Administrador sem a unidade no alcance: 403; Coordenador: permitido em qualquer unidade da organização; aluno ou turma de outra organização nunca aparece (duas organizações no teste).
- CA12 (R3, R29): toda matrícula grava `student.enrolled` (ou `student.enrollment_reopened`) e `student.modality_added` com a unidade, sem CPF, e-mail nem telefone no antes e depois.

**Parte 2**

- CA13 (R9): dado aluno com modalidade na turma X, quando a gestão troca a principal para a Y, então a leitura seguinte já mostra Y e a turma X deixa de contar o aluno; os outros horários são substituídos pelo que foi enviado; turma inválida: 422 e nada muda; auditoria `student.class_changed` com antes e depois.
- CA14 (R10): dado plano P1 atual, quando troca para P2, então `plan` continua P1, `nextPlan` é P2 e a auditoria `student.plan_changed` registra; pedir P1 de novo limpa o `nextPlan`; plano inválido: 422; trocar para o plano atual sem agendamento: 422 (D10).
- CA15 (R11): dado modalidade na unidade A, quando transferida para a B com turma, plano e vencimento da B, então a mesma linha (mesmo id, `started_at` e matrícula) passa a ter a unidade B, a turma e o plano novos; a graduação e o histórico não se alteram; a auditoria `student.modality_transferred` traz origem e destino.
- CA16 (R11): dado transferência para a mesma unidade, para unidade de outra organização, para unidade inativa, ou com turma, plano ou vencimento da unidade antiga, então 422 (404 para outra organização) e nada muda; modalidade de outra organização ou de outro aluno: 404.
- CA17 (R11): dado que ainda não há tabela de cobrança (Sprint 08), quando a modalidade é transferida, então o teste prova que a transferência não escreve em nenhuma outra tabela além de `enrollment_modality` e suas turmas (D17); a regra "cobrança fica na unidade de origem" é verificada na Sprint 08.
- CA18 (R9, R10, R11, R15): Administrador da unidade A tenta trocar turma, plano ou transferir modalidade da unidade B: 403; transferir da A para a B quando ele só alcança a A: 403; Coordenador: permitido; Professor: 403; isolamento entre organizações no teste das três rotas.

**Parte 3**

- CA19 (R12): modalidade com aluno de modalidade ativa: `PUT` de desativação dá 409 `MODALITY_IN_USE` com `activeStudents` maior que 0; a mesma modalidade só com aluno encerrado é desativada; teste de integração na rota existente; a tela de modalidades mostra a contagem que impede (teste de tela, ajuste só se a tela hoje ignora o campo).
- CA20 (R13): unidade com aluno de modalidade ativa: desativar dá 409 com `activeStudents` maior que 0; a lista de unidades traz a contagem real; turma com aluno principal não é desativada (RN-TUR-03) e o painel da turma mostra os alunos, com a marca de principal e de outro horário; `activeStudents` da turma conta os dois; capacidade excedida só avisa (RN-TUR-02).
- CA21 (R14): dado aluno cadastrado na unidade A com modalidade só na B, quando o Administrador da A lista, então não o vê; o da B vê; o Administrador da A abre a ficha dele e recebe 404, porque só alcança quem tem modalidade em unidade do alcance dele; aluno sem modalidade nenhuma: o Administrador da unidade de cadastro vê, e o de outra unidade não; Coordenador vê todos; filtro por unidade mostra só quem tem modalidade nela.
- CA22 (R14, R15): dado aluno com modalidade na A e na B, quando o Administrador da A abre a ficha, então vê as duas modalidades, com a da B marcada como não editável e a ação de troca na B recusada com 403; os dados pessoais podem ser editados por ele.
- CA23 (R16): dado Professor titular ou auxiliar de turma ativa com o aluno matriculado (principal ou outro horário), e `STUDENT_RECORD` ligada na unidade da turma, quando abre a ficha, então 200; aluno de turma de outro Professor: 404; chave desligada na unidade da turma mesmo ligada em outra: 403; turma inativa ou modalidade encerrada do aluno: 404.
- CA24 (R16): contatos e emergência da ficha do Professor só vêm com `STUDENT_CONTACTS` ligada na unidade da turma (não em outra).
- CA25 (R17): teste lista os campos de `RecordView` e reprova qualquer campo fora da lista permitida do Professor; a ficha do Professor serializada não traz valores, plano, benefício, mensalidade, CPF, endereço nem situação financeira.
- CA26 (R17): aniversariante sem matrícula ativa não entra; o Professor vê só os aniversariantes de turma dele com matrícula ativa; o handler MSW do Professor devolve o recorte (teste MSW).

**Parte 4**

- CA27 (R18): dado aluno com duas modalidades, quando lista, então a linha traz as duas com unidade e turma principal e a situação; sem matrícula, lista vazia de modalidades e "Sem matrícula"; teste de integração e de tela da coluna.
- CA28 (R19): filtro por modalidade, por turma (principal e outro horário) e por nível, sozinhos e combinados com unidade, faixa e selo; turma de outra organização: 404; nível retorna lista vazia sem erro (D13); teste de integração por filtro e de tela.
- CA29 (R20): `counts` traz `active` e `former` coerentes com a lista; filtro rápido Ativos e Ex-alunos filtram; Administrador conta só o seu alcance; teste de integração e de tela.
- CA30 (R21): matrícula há 30 dias: `NEW`; há 31: sem `NEW`; a contagem usa o fuso da unidade de cadastro (23h30 de Brasília); reabertura recente volta a mostrar `NEW`; aparece na lista, na ficha e no filtro `seal=NEW`.
- CA31 (R22): Professor chama `/students/simple?classId=` da sua turma e vê só nome, foto, faixa e `blocked`, sem CPF, contato, valores nem selos; de turma de outro: 404; com `STUDENT_RECORD` desligada continua vendo a lista e `GET /students/{id}/record` dá 403; sem sessão 401; Aluno e Responsável 403; teste de isolamento entre organizações.
- CA32 (R22): a gestão chama a mesma rota com o alcance de unidade e vê os alunos da turma; o painel da aula do Professor usa a mesma lista (teste de tela da lista simples).

**Parte 5**

- CA33 (R23, R24): dada a ficha da gestão, quando abre as abas Resumo e Matrícula e modalidades, então mostra modalidades com turma, plano, vencimento, situação e plano agendado e a situação no cabeçalho; a modalidade de outra unidade não tem ações; Encerrar aparece desligado com a dica; estados carregando, vazio, erro e sem permissão (teste de tela).
- CA34 (R25): dada cada janela, quando preenchida e confirmada, então chama a rota certa, mostra o erro do servidor (422) no campo e fecha com atualização da ficha no sucesso; o texto de efeito (na hora ou na próxima cobrança) aparece; teste de tela e MSW dos três handlers.
- CA35 (R26): dada a ficha do Professor, então não há botões de escrita nem a aba Histórico; a aba Responsáveis só mostra o que a API entrega; a ficha da gestão mantém os botões; teste de tela com os dois perfis.
- CA36 (R27): axe sem violações nos dois temas e a 360 px nas janelas, abas e na lista; nenhum texto literal (`npm run lint`).

**Parte 6**

- CA37 (R28): às 23h30 de Brasília, com o relógio simulado em UTC já no dia seguinte, o aluno que completa 18 anos no dia seguinte (fuso da unidade) ainda é menor no cadastro, na edição, no convite e no aviso de maioridade; as quatro leituras usam o fuso da unidade de cadastro (teste de unidade e de integração, com uma organização de duas unidades em fusos diferentes, para pegar troca de unidade, S3).
- CA38 (R29): `OpenApiContractTest` e `ContractRoutesTest` passam com todas as rotas novas; `SeedDataIT` aplica `14-matriculas.sql` duas vezes sem erro; cobertura de linhas 80% (100% em `isolation`); o código de cada regra citada aparece em ao menos um teste (conferido por `grep`).

## 4. Plano técnico

- Dados (V33, a criar `backend/src/main/resources/db/migration/V33__enrollment.sql`, com a linha "Revert" manual como as anteriores):
  - `enrollment(id, organization_id, student_id, enrolled_at timestamptz not null, reopened_at timestamptz, created_at)`; `unique (organization_id, student_id)`; FK composta para `student`.
  - `enrollment_modality(id, organization_id, enrollment_id, student_id, unit_id, modality_id, primary_class_id, plan_id, next_plan_id, due_day int, status text check in ('ACTIVE','ENDED'), started_at, ended_at, created_at)`; FKs compostas para `unit`, `training_class`, `plan`; `unique (organization_id, id)`; índice único parcial `(student_id, modality_id) where status = 'ACTIVE'`; índices por `(organization_id, unit_id, status)` e por `primary_class_id`; check `ended_at` coerente com status; `ENDED` entra já porque o retorno e o ex-aluno dependem dele, a #69 a usa e acrescenta `ENDING_REQUESTED` (D18).
  - `enrollment_modality_class(organization_id, modality_row_id, class_id)` para os outros horários, chave `(modality_row_id, class_id)`.
  - RLS `organization_scope` nas três tabelas (mesmo texto de V29), `force row level security`, e o `revoke delete` de V29 em `enrollment` e `enrollment_modality` (matrícula nunca é excluída, RN-ALU-08); `enrollment_modality_class` pode ter linha apagada (é configuração, não histórico).
  - Seed `infra/seed/14-matriculas.sql` (`on conflict do nothing`): matrículas para os alunos de `12-alunos.sql` nas turmas de `10-turmas.sql` e planos de `13-planos.sql`, com ativos, um ex-aluno e um sem matrícula; e as linhas de `plan_value_history` dos planos do seed (sugestão R06-11, só se couber na mesma sessão).
- API (todas sob `/api/v1/students`, em `StudentController`, a rota literal convive com `/{id}` como `/lookup` e `/birthdays`):
  - `POST /{id}/modalities` (201, body `{modalities:[{unitId, modalityId, primaryClassId, otherClassIds, planId, dueDay}]}`; devolve `Enrollment`); erros 400 (corpo), 401, 403, 404 (aluno, turma ou plano de outra organização), 409 (modalidade ativa repetida), 422 (regras da R3, limite).
  - `GET /{id}/enrollment` (`Enrollment`: `status`, `enrolledAt`, `reopenedAt`, `modalities[]` com ids e nomes, `canEdit`).
  - `PUT /{id}/modalities/{modalityRowId}/class`, `PUT .../plan`, `POST .../transfer`.
  - `GET /lookup` ganha `email` e `enrollmentStatus` (acréscimo compatível); `GET /` ganha `modalityId`, `classId`, `levelId`, `status` (`ACTIVE`, `FORMER`), `counts` e `modalities[]` e `status` em cada linha; `GET /simple?classId=` (ou `lessonId=`) para a lista simples.
  - O contrato: `docs/openapi.yaml` com os schemas `Enrollment`, `EnrollmentModality`, `ModalityInput`, `StudentSimple`; teste de contrato e `ContractRoutesTest`.
- Interface: em `frontend/src/features/students/`: aba `RecordEnrollment.tsx` (a criar), janelas `ChangeClassDialog.tsx`, `ChangePlanDialog.tsx`, `TransferDialog.tsx` (a criar), coluna e filtros em `StudentsPage.tsx`, `StudentFilters.tsx`, `useStudentColumns.ts`, `types.ts`, `RecordSummary.tsx`, `RecordGuardians.tsx` (botões por perfil), `StudentRecordPanel.tsx`; hook `useEnrollment.ts` (a criar); lista simples do Professor reaproveitando o painel da aula e o da turma (`frontend/src/features/agenda`, `classes`); textos em `frontend/src/i18n/pt-BR.json`; handlers em `frontend/src/mocks/handlers.ts` (e testes `enrollment.msw.test.ts`, a criar); estados de `components/states`. Canvas: `TrocarTurma.dc.html`, `TrocarPlano.dc.html`, `TransferirModalidade.dc.html` (as etapas 2 e 4 da nova matrícula são da #67).
- Segurança: `Permissions.grant(who, STUDENT_MANAGE, unitId)` por modalidade e unidade na matrícula, na troca e na transferência (Coordenador em qualquer unidade; Administrador só nas suas; origem e destino na transferência); `OrganizationTransactions` e RLS em todas as consultas, com `organization_id` também no código; `student` travado com `select ... for update` (limite de 10 e matrícula única sob concorrência, como `student_guardian_limit`); o Professor alcança aluno só por `teacherReach` com chaves na unidade da turma; a lista simples e a ficha do Professor seguem uma lista permitida de campos montada no servidor; auditoria sem CPF, e-mail, telefone nem endereço; nada de dado de saúde.
- Arquivos a alterar (vistos): `backend/src/main/java/br/com/ctcore/student/Students.java` (`reachPredicate`, `detail`, `list`, `rows`, `birthdays`, `sealPredicate`, `TODAY`), `StudentRecords.java` (`teacherReach`, `assemble`), `StudentController.java`, `StudentSeal.java` (`NEW`), `StudentRegistration.java`, `StudentGuardians.java`, `StudentEdit.java`, `MajorityNoticeJob.java`, `identity/InviteService.java`, `modality/ModalityUsage.java`, `organization/UnitDeactivationBlockers.java`, `trainingclass/TrainingClasses.java` (`activeStudents`, `students`, `primaryStudents`), `agenda/Agenda.java` (contagem usa o mesmo ponto), `docs/openapi.yaml`, `docs/especificacao/08` e `09` só se uma regra mudar (D11, D12: registrar antes).
- Arquivos a criar: `V33__enrollment.sql`; `student/Enrollments.java` (regras, uma transação por pedido), `student/EnrollmentController.java` ou rotas em `StudentController`; testes `EnrollmentIT`, `EnrollmentChangesIT`, `StudentReachIT`, `StudentListEnrollmentIT`, `StudentSimpleListIT`, `StudentDatesIT`; seed `14-matriculas.sql`; `docs/` das notas de versão não (Sprint 15).
- Reuso: `AuditService` e `AuditEntry` (padrão de `StudentEdit`), `OrganizationTransactions`, `Permissions.grant`, `ApiValidationException`, `ApiStatusException`, `TrainingClassWrite` (validação de modalidade oferecida pela unidade), `UnitSettings.due_days`, `Students.row` e `sealPredicate` (um predicado só), `PageRequest`, `WhatsAppButton` não muda, componentes de janela e estados do `frontend/src/components`.
- Riscos: (1) o alcance por modalidade mexe em `list`, `detail`, `rows`, `birthdays` e na ficha: regressão do isolamento entre unidades, mitigada por `StudentReachIT` com duas unidades e dois perfis antes de mexer nos demais; (2) as contagens trocadas (`activeStudents`) mudam respostas já publicadas (modalidade, unidade, turma, aula): os testes existentes mudam de 0 para o valor real e devem ser ajustados no mesmo PR; (3) dois pontos de verdade para "ativo" e "alcance": cada um vira SQL único reutilizado; (4) `Students.java` tem 300 linhas e vai crescer: o código novo da matrícula fica em `Enrollments` e o SQL de situação e alcance em constantes, sem inflar `Students`; (5) a Sprint 08 precisa ler `next_plan_id` e a unidade da cobrança: o contrato está nas decisões D10 e D17; (6) concorrência: limite de 10 e matrícula única dependem do `for update` no aluno.

## 5. Tarefas

<!-- Só as tarefas usam caixa de marcar neste arquivo. Uma parte só começa quando a anterior está construída e verificada. -->

**Parte 1 — Dados e matrícula**

- [x] T1: migração `V33__enrollment.sql` (três tabelas, RLS, sem DELETE, índices) e seed `14-matriculas.sql` — não cobre critério: base das demais tarefas e do `SeedDataIT` — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test='SeedDataIT,*Isolation*'` e `bash infra/scripts/seed.sh` duas vezes sem erro
- [x] T2: `Enrollments` e `POST /students/{id}/modalities` (criar, adicionar, limite 10, validações da R3 e R4, tudo ou nada, `student.enrolled`, `student.modality_added`, `for update`) — cobre CA1 a CA5, CA11, CA12 — prova: `EnrollmentIT` (`./backend/mvnw -f backend/pom.xml verify -Dit.test=EnrollmentIT`), incluindo isolamento entre organizações e perfis e a corrida de duas chamadas
- [x] T3: `GET /students/{id}/enrollment`, situação calculada e `enrolledAt` — cobre CA6, CA7 — prova: `EnrollmentIT`
- [x] T4: retorno de ex-aluno (reabrir a mesma matrícula, `reopened_at`, `student.enrollment_reopened`) e `GET /students/lookup` por CPF ou e-mail com `enrollmentStatus` — cobre CA8, CA9, CA10 — prova: `EnrollmentIT` e `StudentLookupIT` existente ajustado
- [x] T5: OpenAPI das rotas e schemas da Parte 1, handlers MSW e regressão — cobre CA38 (parcial) — prova: `./backend/mvnw -f backend/pom.xml test -Dtest='OpenApiContractTest,ContractRoutesTest'`, `npx vitest run src/mocks/enrollment.msw.test.ts` e `./backend/mvnw -f backend/pom.xml verify`

**Parte 2 — Troca e transferência**

- [x] T6: `PUT .../class` (troca de turma na hora, outros horários substituídos, `student.class_changed`) — cobre CA13, CA18 — prova: `EnrollmentChangesIT`
- [x] T7: `PUT .../plan` (plano agendado, `next_plan_id`, `student.plan_changed`, D10) — cobre CA14, CA18 — prova: `EnrollmentChangesIT`
- [x] T8: `POST .../transfer` (mesma linha, unidade nova, turma, plano e vencimento da nova, origem e destino na auditoria, alcance nas duas unidades) — cobre CA15, CA16, CA17, CA18 — prova: `EnrollmentChangesIT` com duas unidades e duas organizações
- [x] T9: OpenAPI, handlers MSW das três rotas e regressão — cobre CA38 (parcial) — prova: `OpenApiContractTest`, `ContractRoutesTest`, `enrollment.msw.test.ts` e `verify` completo

**Parte 3 — Alcance e contagens**

- [x] T10: `ModalityUsage.activeStudents` e `UnitDeactivationBlockers.activeStudents` com dado real; ajustar os testes que esperavam 0 — cobre CA19, CA20 — prova: `ModalityIT` e `UnitDeactivationIT` existentes (ajustados) e `./backend/mvnw -f backend/pom.xml verify -Dit.test='Modality*IT,Unit*IT'`
- [x] T11: `TrainingClasses.activeStudents`, `students` e `primaryStudents`, painel da turma e contagem da aula — cobre CA20 — prova: `TrainingClass*IT` e `Agenda*IT` ajustados e novo caso de turma com aluno principal
- [x] T12: alcance do Administrador por modalidade (`reachPredicate`, `detail`, `list`, `row`, `birthdays`; fallback para aluno sem modalidade; `canEdit` por unidade) — cobre CA21, CA22 — prova: `StudentReachIT`; antes de escrever, atualizar `08` RN-ALU-07 se o texto ficar diferente do decidido (D11)
- [x] T13: `teacherReach` real com chaves na unidade da turma, matrícula ativa nos aniversariantes, lista permitida de campos do Professor — cobre CA23, CA24, CA25, CA26 — prova: `StudentRecordTeacherIT` (novo) e `StudentBirthdaysIT` ajustado
- [x] T14: handler MSW do Professor em `birthdays` e ficha com o recorte real, e regressão — cobre CA26 (MSW) — prova: `npx vitest run src/mocks/birthdays.msw.test.ts src/mocks/studentRecord.msw.test.ts` e `verify` completo

**Parte 4 — Lista, lista simples e selo**

- [x] T15: linha da lista com `modalities[]` e `status`, filtros por modalidade, turma e nível, `counts`, filtros rápidos no servidor — cobre CA27, CA28, CA29 — prova: `StudentListEnrollmentIT`
- [x] T16: selo `NEW` (`StudentSeal`, `sealPredicate`, filtro, ficha) com 30 e 31 dias e fuso da unidade — cobre CA30 — prova: `StudentSealsIT` (caso de 30, 31 dias e 23h30)
- [x] T17: `GET /students/simple` e sua ligação com o painel da turma e o da aula — cobre CA31, CA32 (servidor) — prova: `StudentSimpleListIT`
- [x] T18: front da lista: coluna Modalidades, filtros por modalidade, turma e nível, filtros rápidos com contagem, selo Novo, lista simples do Professor, handlers MSW e textos — cobre CA27 a CA32 (tela), CA36 — prova: `StudentsPage.test.tsx`, teste do painel, `students.msw.test.ts`, axe a 360 px nos dois temas
- [x] T19: OpenAPI da Parte 4 e regressão — cobre CA38 (parcial) — prova: `OpenApiContractTest`, `ContractRoutesTest`, `npm run lint && npm run typecheck && npm run test:cov` e `verify`

**Parte 5 — Ficha**

- [x] T20: aba "Matrícula e modalidades" e Resumo com modalidades, situação no cabeçalho, `useEnrollment` — cobre CA33, CA36 — prova: `StudentRecordPanel.test.tsx` e `RecordEnrollment.test.tsx` (novo)
- [x] T21: janelas Trocar turma e Trocar plano — cobre CA34, CA36 — prova: `ChangeClassDialog.test.tsx`, `ChangePlanDialog.test.tsx` com axe
- [x] T22: janela Transferir — cobre CA34, CA36 — prova: `TransferDialog.test.tsx` com axe
- [x] T23: ficha do Professor sem botões de escrita nem Histórico e botões da gestão por `STUDENT_MANAGE` — cobre CA35 — prova: `StudentRecordPanel.test.tsx`, `StudentGuardiansTab.test.tsx`
- [x] T24: ponta a ponta do fluxo crítico (matricular pelo servidor via API de teste, trocar turma e transferir pela ficha) e regressão do front — cobre CA33, CA34 — prova: `npm run lint && npm run typecheck && npm run build && npm run test:cov && npm run e2e`

**Parte 6 — Datas e fechamento**

- [x] T25: datas no fuso da unidade em `StudentRegistration.personal`, `InviteService`, `MajorityNoticeJob`, `StudentGuardians:249` — cobre CA37 — prova: `StudentDatesIT` (novo, duas unidades em fusos diferentes, 23h30 de Brasília)
- [x] T26: regressão completa, código das regras por nome nos testes, OpenAPI e MSW batendo — cobre CA38 — prova: `grep -rl` de RN-MAT-01, 02, 04, 15, 19, 20, RN-ORG-12, RN-ALU-07 em `backend/src/test`, `./backend/mvnw -f backend/pom.xml verify` e, em `frontend/`, `npm run lint && npm run typecheck && npm run build && npm run test:cov && npm run e2e`

## 6. Decisões

- D1: Item dividido em seis partes no próprio arquivo, em ordem e em um só PR (decisão do dono na revisão da Sprint 06, `specs/revisoes/sprint-06.md`). Alternativa: seis PRs; contraria "um único PR por item" do `AGENTS.md`.
- D2 (suposição): Quem pode matricular é quem tem `STUDENT_MANAGE` (Coordenador e Administrador, `02` linha "matricular"), por unidade. Se o Coordenador não deve operar unidade sem entrar nela, só o teste de permissão da R3 muda.
- D3 (muda o comportamento): o aluno tem no máximo uma modalidade ativa de cada tipo (não duas vezes BJJ em unidades diferentes). Motivo: a transferência (RN-ORG-12) "leva a modalidade", e a graduação é por modalidade e aluno; duas linhas da mesma modalidade confundiriam a graduação. Alternativa: permitir uma por unidade.
- D4: o benefício não entra na linha da modalidade; a #65 acrescenta a coluna `benefit_id` com a tabela de benefícios (#64). Motivo: a tabela não existe. Alternativa: coluna sem chave, que seria dado sem dono.
- D5: sem cobrança nesta parte: o item grava `due_day` e o plano, e a Sprint 08 gera a primeira cobrança (mês cheio, taxa e isenção, RN-MAT-05 e 19) e mostra o débito antigo no retorno; nada aqui escreve em tabela de cobrança. Alternativa: simular cobrança agora, o que duplicaria a regra.
- D6: o plano deve ser da unidade e da modalidade da linha (`valid_other_units` não é consultado nesta parte). Suposição: a regra de plano válido em outras unidades (RN-PLA) vale na cobrança e não na matrícula; se valer, o teste de plano acrescenta o caso.
- D7: a situação do aluno é calculada, não gravada. Ativo: ao menos uma modalidade `ACTIVE`; Ex-aluno: matrícula sem modalidade ativa; Sem matrícula: nenhuma matrícula. As situações Bloqueado e Encerramento solicitado entram com a #69 e a Sprint 12 sobre a mesma conta. Alternativa: coluna `status`, que pode divergir das modalidades.
- D8: o selo Novo conta da data mais recente entre `enrolled_at` e `reopened_at`; no retorno ele volta (o aluno é "novo" de novo). Alternativa: só a primeira matrícula; a especificação diz "até 30 dias da matrícula" e o retorno reabre a matrícula.
- D9: anonimização: sem código novo, porque a busca é por CPF e e-mail e a anonimização (RF-ALU-05) os apaga; fica registrado como suposição até o RF-ALU-05 existir. Se a anonimização mantiver o e-mail, o teste da Sprint que a entrega pega.
- D10: troca de plano vale a partir da próxima cobrança gerada: o servidor guarda `next_plan_id` e a cobrança (Sprint 08) o aplica e limpa. Trocar de novo antes substitui o agendamento; pedir o plano atual limpa; trocar para o plano atual sem agendamento é recusado com 422. Alternativa: tabela de histórico de planos por vigência; mais estrutura sem cobrança para usá-la.
- D11 (muda o comportamento): alcance do Administrador por modalidade na unidade conta modalidade ativa ou encerrada (o ex-aluno continua visível à unidade onde treinou); aluno sem nenhuma modalidade continua pela unidade de cadastro, para não ficar fora do alcance de todos antes da primeira matrícula. Alternativa: só modalidade ativa, que esconderia o ex-aluno do próprio Administrador. Se o texto de `08` RN-ALU-07 precisar mudar, muda primeiro a especificação (AGENTS), na T12.
- D12 (muda o comportamento): o Administrador só altera modalidade da sua unidade, mesmo vendo as outras (RN-ALU-07); a transferência exige alcance na origem e no destino (nos dois lados ele é Administrador); Coordenador faz qualquer uma. Alternativa: transferir exigindo só a origem, que deixaria o Administrador da A mandar aluno para uma unidade que ele não administra.
- D13: nível e graduação vêm da #105 e #106: o campo de nível na linha fica nulo e o filtro por nível é aceito e vazio até lá, em vez de ausente, para a tela e o contrato não mudarem depois. Alternativa: tirar a coluna e o filtro agora; o RF-ALU-01 os pede nesta Sprint.
- D14: o `blocked` da lista simples é sempre falso até a inativação (#109 e seguintes); o campo existe para a tela não mudar. Alternativa: omitir; mudaria o contrato.
- D15: a ação Encerrar da ficha aparece desligada até a #69. Alternativa: escondê-la; o RF-ALU-03 a lista nesta etapa.
- D16: as janelas Trocar turma, Trocar plano e Transferir entram aqui porque o canvas da issue as lista e a ficha as exige; a Nova matrícula em etapas e os avisos de faixa e capacidade ficam com #67 e #68. Alternativa: janelas só na #67; ficaria a ficha sem as ações que o encaixe da Sprint 06 pede.
- D17 (suposição): a cobrança guardará a unidade de origem em linha própria (Sprint 08); a transferência não reescreve nada fora de `enrollment_modality` e das suas turmas. Se a Sprint 08 decidir ler a unidade da modalidade na hora, ela precisa guardá-la na cobrança.
- D18: a coluna `status` da modalidade aceita hoje `ACTIVE` e `ENDED`; a #69 acrescenta `ENDING_REQUESTED` (RN-MAT-18) alterando o check. Alternativa: criar já os três estados, sem rota que os use.
- D19: o limite de 10 conta modalidades ativas; modalidade encerrada não conta. A recusa é 422, com o campo `modalities`. Alternativa: 409; o resto da API usa 422 para regra de negócio de entrada (como `student_guardian_limit`).
- D20: a lista simples (R22) é uma rota nova em vez de abrir a lista completa ao Professor, porque `02` diz que ele não tem lista de alunos, só a lista simples dentro da turma e da aula. Alternativa: `GET /students` com perfil; misturaria dois cortes numa rota.
- D21: encaixes herdados cobrados em CA: S2 (ficha do Professor mantém Responsáveis, só o Histórico é da gestão, R26), S3 (CA37 com organização de duas unidades), S8 (`StudentGuardians:249` entra nas datas, R28), R06-9 (botões por perfil), `ModalityUsage` e `teacherReach` (R12, R16). S5 (próximo vencimento e pendências) fica com #73 e Início.
- Suposição: o seed de planos (R06-11) sem linha em `plan_value_history` entra aqui só se couber; se não, fica para a Sprint 08 como já está registrado.

## Construção

Partes 1 a 6 construídas (uma parte por sessão).

- T1 — arquivos: `backend/src/main/resources/db/migration/V33__enrollment.sql`, `infra/seed/14-matriculas.sql`, `backend/src/test/java/br/com/ctcore/db/SeedDataIT.java` (limpeza das três tabelas e caso `enrollmentsSeed`) — prova: `./backend/mvnw -f backend/pom.xml verify` (inclui `SeedDataIT` com o seed aplicado duas vezes, `MigrationIT`, `RowLevelSecurityIT`, `*Isolation*`) → verde. `bash infra/scripts/seed.sh` não rodou: não há ambiente docker-compose de pé nesta máquina; a idempotência foi provada pelo `SeedDataIT`.
- T2 — arquivos: `backend/src/main/java/br/com/ctcore/student/Enrollments.java` (novo), `StudentController.java` — prova: `EnrollmentIT` (`createsAndAdds`, `validatesRules`, `otherClasses`, `limitsAndAtomicity`, `concurrent`, `profiles`, `readsAndReach`) → 10 testes, 0 falhas.
- T3 — arquivos: `Enrollments.get`, rota `GET /students/{id}/enrollment` — prova: `EnrollmentIT` (`createsAndAdds`, `returns`, `readsAndReach`) → verde.
- T4 — arquivos: `Students.lookup` (CPF ou e-mail, `enrollmentStatus`), `StudentController`, `StudentRegistrationIT` (ajuste do teste existente de `lookup`: agora quatro campos) — prova: `EnrollmentIT` (`returns`, `anonymised`, `lookup`) e `StudentRegistrationIT` → verde.
- T5 — arquivos: `docs/openapi.yaml` (rotas `/students/{id}/modalities`, `/students/{id}/enrollment`, `lookup` com `email`; esquemas `StudentLookup`, `EnrollmentModalityInput`, `EnrollmentAdd`, `EnrollmentNamed`, `EnrollmentModality`, `Enrollment`), `frontend/src/mocks/handlers.ts`, `frontend/src/mocks/enrollment.msw.test.ts` (novo), `frontend/src/mocks/students.msw.test.ts` (ajuste do lookup) — prova: `OpenApiContractTest,ContractRoutesTest` → 7 testes verdes; `npx vitest run src/mocks` → 145 verdes.

Regressão: `./backend/mvnw -f backend/pom.xml verify` → 463 testes, 0 falhas, JaCoCo aprovado (BUILD SUCCESS). Em `frontend/`: `npm run lint` e `npm run typecheck` limpos; `npm test` → 90 arquivos, 816 testes verdes. `npm run test:cov`, `npm run build` e `npm run e2e` não rodaram nesta sessão (só `mocks/` mudou no front; a regressão completa do front fica para o fechamento da parte seguinte e da T26). Não havia linha de base registrada.

Desvios:
- Rotas de escrita da matrícula ficaram em `StudentController`, com a regra em `Enrollments` (sem `EnrollmentController`), como o plano permite.
- Recusas de regra usam `ApiStatusException(UNPROCESSABLE)` (422 com `fields`), como `LessonWrite`; `ApiValidationException` (400) só para campo ausente ou mal formado no corpo.
- Unidade que o Coordenador não alcança e que não existe na organização dá 403 (via `Permissions.grant`) e não 404; turma, plano e aluno de outra organização dão 404.
- `dueDay` é opcional quando o aluno já tem modalidade ativa na unidade (herda, R4); obrigatório na primeira.
- Alcance do Administrador sobre o aluno segue a unidade de cadastro nesta parte (a troca para modalidade é a T12): admin da unidade A só matricula aluno cadastrado na A e recebe 403 ao pedir modalidade em unidade fora do alcance dele.
- O `enrollmentStatus`/`status` usa `NONE`, `ACTIVE`, `FORMER` na API (a tela traduz para "Sem matrícula", "Ativo", "Ex-aluno").

Não é do item: nenhuma mudança fora do escopo; `specs/itens/63-matricula-no-servidor.md` é o arquivo do item (sem commit, fase 1/2).

### Parte 2 (T6 a T9), construída na sessão seguinte à verificação da Parte 1

- T6 — arquivos: `student/Enrollments.java` (`changeClass`, `validateClasses`, `replaceOthers`), `StudentController.java` (`PUT /students/{id}/modalities/{modalityRowId}/class`) — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test=EnrollmentChangesIT` (`changesClass`, `reachAndProfiles`) → 4 testes, 0 falhas.
- T7 — arquivos: `Enrollments.changePlan`, rota `PUT .../plan` (`next_plan_id`; atual sem agendamento 422; atual com agendamento limpa) — prova: `EnrollmentChangesIT` (`changesPlan`, `reachAndProfiles`) → verde.
- T8 — arquivos: `Enrollments.transfer`, rota `POST .../transfer` — prova: `EnrollmentChangesIT` (`transfers`: mesma linha, `started_at` e matrícula mantidos, origem e destino na auditoria, contagens de plano, turma, aluno e matrícula intactas; `reachAndProfiles`: 403 de Administrador na origem e no destino, Professor, Aluno e Responsável 403, 401, outra organização 404) → verde.
- T9 — arquivos: `docs/openapi.yaml` (três rotas; esquemas `EnrollmentClassChange`, `EnrollmentPlanChange`, `EnrollmentTransfer`), `frontend/src/mocks/handlers.ts` (três handlers, `modalityRowFor`, `checkClasses`), `frontend/src/mocks/enrollment.msw.test.ts` (cinco casos novos) — prova: `OpenApiContractTest,ContractRoutesTest` dentro do `verify`; `npx vitest run src/mocks/enrollment.msw.test.ts` → 11 verdes.

Regressão da Parte 2: `./backend/mvnw -f backend/pom.xml verify` → 467 testes, 0 falhas, BUILD SUCCESS (JaCoCo aprovado). Em `frontend/`: `npm run lint` e `npm run typecheck` limpos; `npm test` → 822 verdes. `npm run test:cov`, `build` e `e2e` não rodaram (só `mocks/` mudou no front; fica para a T18, T19 e T26).

Desvios da Parte 2:
- A variável de caminho é `{modalityRowId}` (o `ContractRoutesTest` exige o mesmo nome no controller e no contrato).
- Unidade de destino que não existe na organização (inclusive de outra organização) dá 404 antes da checagem de alcance, como pede o CA16; na matrícula (Parte 1) o 403 do desvio anterior continua.
- Modalidade encerrada dá 404 nas três rotas (só a ativa é alterável).
- A transferência descarta `next_plan_id` (o plano agendado era da unidade antiga).
- `EnrollmentChangesIT` repete os auxiliares de `EnrollmentIT` em vez de extraí-los para uma base comum.
- O CA17 foi provado pelas contagens de plano, turma, aluno e matrícula; as tabelas de cobrança ainda não existem (Sprint 08).

Não é do item: nenhuma mudança fora do escopo.

### Parte 3 (T10 a T14)

- T10 — arquivos: `modality/ModalityUsage.java`, `organization/UnitDeactivationBlockers.java` (contagem real de alunos distintos com modalidade ativa), `EnrollmentCountsIT` (novo) — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test='Modality*IT,Unit*IT,EnrollmentCountsIT'` → verde. A tela de modalidades já lê `activeStudents` (`ModalitiesPage.test.tsx`), sem ajuste.
- T11 — arquivos: `trainingclass/TrainingClasses.java` (`activeStudents`, `students`, `primaryStudents`; contagem lida dentro da transação), `EnrollmentCountsIT` — prova: `-Dit.test='TrainingClass*IT,Agenda*IT,EnrollmentCountsIT'` → verde.
- T12 — arquivos: `student/Students.java` (`inUnits` como ponto único de alcance em `list`, `detail`, `reachPredicate`, `row`, `birthdays`; `lookup`), `docs/especificacao/08-alunos-e-responsaveis.md` (RN-ALU-07 atualizada antes, D11), `StudentReachIT` (novo) — prova: `-Dit.test=StudentReachIT` → verde.
- T13 — arquivos: `student/StudentRecords.java` (`teacherReach`, `teacherUnits`, chaves `STUDENT_RECORD` e `STUDENT_CONTACTS` na unidade da turma), `Students.birthdays` (matrícula ativa), `StudentRecordTeacherIT` (novo, com o teste de lista de campos de `RecordView`), `StudentBirthdaysIT` — prova: `-Dit.test='StudentRecordTeacherIT,StudentBirthdaysIT'` → verde (a limpeza de `account_link` do `StudentRecordTeacherIT` foi corrigida nesta sessão).
- T14 — arquivos: `frontend/src/mocks/handlers.ts`, `birthdays.msw.test.ts`, `studentRecord.msw.test.ts` — prova: `npx vitest run src/mocks` → 152 verdes.

Regressão da Parte 3: `./backend/mvnw -f backend/pom.xml verify` → 474 testes, 5 falhas e 3 erros; os 3 erros eram a limpeza do `StudentRecordTeacherIT` (corrigida; o teste passa). As 4 falhas restantes (`PlanWriteIT.create`, `StudentListIT.lists` e `overlappingRanges`, `StudentRecordIT.record`, `StudentSealsIT.referenceDateIsTheUnitDate`) comparam `LocalDate.now()` ou idade com a data do fuso da unidade e rodaram às 21h45 de Brasília (já dia seguinte em UTC); arquivos não tocados nesta parte, falha anterior dependente do relógio (S8, R28), não corrigida. Depois da correção rodei só os ITs da Parte 3 (verdes); o `verify` completo não foi repetido. Frontend: `npm run lint` e `npm run typecheck` limpos; `src/mocks` verde. `test:cov`, `build` e `e2e` não rodaram (só `mocks/` mudou).

Desvios da Parte 3: nenhum.

Não é do item: nenhuma mudança fora do escopo.

### Parte 4 (T15 a T19)

- T15 — arquivos: `student/Students.java` (`Filter`, `StudentList`, `Counts`, `RowModality`; `list` com `modalityId`, `classId`, `levelId`, `status`; `counts`; `STATUS` como ponto único da situação, também usado em `lookup`; modalidades ativas da linha por uma consulta por página), `StudentController.java`, `StudentListEnrollmentIT` (novo), `StudentListIT` (unidade de outra organização passou de lista vazia para 404, R19) — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test=StudentListEnrollmentIT` → 3 testes, 0 falhas.
- T16 — arquivos: `StudentSeal.java` (`NEW`), `Students.sealPredicate` (maior de `enrolled_at` e `reopened_at`, no fuso da unidade de cadastro, dia 30 incluso), `StudentSealsIT.newSeal` (30 e 31 dias, reabertura, 23h30 de Brasília, Tóquio, lista, ficha e `seal=NEW`) — prova: `-Dit.test=StudentSealsIT` → `newSeal` verde.
- T17 — arquivos: `student/StudentSimple.java` (novo), rota `GET /students/simple` em `StudentController`, `StudentSimpleListIT` (novo: Professor titular e auxiliar fora das chaves, aula, outra turma 404, 400, 401, Aluno e Responsável 403, outra organização 404, gestão por alcance) — prova: `-Dit.test=StudentSimpleListIT` → 2 testes, 0 falhas.
- T18 — arquivos: `features/students/{types.ts,useStudents.ts,StudentFilters.tsx,StudentsPage.tsx,useStudentColumns.ts,StudentSimpleList.tsx (novo)}`, `components/query/QueryPage.tsx` (`counts` opcional entregue aos filtros), `features/agenda/LessonPanelPage.tsx` (cartão "Alunos da aula"), `i18n/pt-BR.json`, `mocks/handlers.ts` (linha com `status` e `modalities`, selo `NEW`, filtros, `counts`, `/students/simple`), testes `StudentsPage.test.tsx`, `StudentSimpleList.test.tsx` (novo), `LessonPanelPage.test.tsx`, `students.msw.test.ts`, e os três helpers de `StudentRow` nos testes da ficha — prova: `npx vitest run src/features/students src/features/agenda src/mocks` verde; axe a 360 px nos dois temas na lista e na lista simples.
- T19 — arquivos: `docs/openapi.yaml` (`/students/simple`; filtros e `counts` de `/students`; `StudentRow` com `status` e `modalities`; `StudentRowModality`, `StudentSimple`; selo `NEW`) — prova: `OpenApiContractTest,ContractRoutesTest` → 7 verdes, e regressão abaixo.

Regressão da Parte 4: `./backend/mvnw -f backend/pom.xml verify` → 480 testes, 5 falhas, JaCoCo aprovado ("All coverage checks have been met"). As 5 (`PlanWriteIT.create:122`, `StudentListIT.lists:108`, `StudentListIT.overlappingRanges:181`, `StudentRecordIT.record:85`, `StudentSealsIT.referenceDateIsTheUnitDate:114`) são as mesmas das rodadas 4 e 5: comparam `LocalDate.now()` ou a idade com o fuso da unidade e rodaram entre 21h e 23h de Brasília (já dia seguinte em UTC); falha anterior dependente do relógio, coberta pela R28 na Parte 6, não corrigida. O `StudentReachIT.reach` quebrou por causa do auxiliar `names` (lia todo campo `name`, agora há modalidade e turma na linha) e foi ajustado. Em `frontend/`: `npm run lint` e `typecheck` limpos; `npm run test:cov` → 91 arquivos, 836 testes verdes (cobertura de linhas 97,26%); `npm run build` verde; `npm run e2e` → 30 passaram e 1 (`configuracao-unidade`) deu tempo esgotado com o `verify` rodando ao mesmo tempo, e passou sozinho.

Desvios da Parte 4:
- A lista simples do painel da turma não mudou: o painel já lista os alunos pelo `GET /classes/{id}` (mesma consulta de alunos ativos); só o painel da aula ganhou o cartão com `GET /students/simple?lessonId=`.
- Modalidade e turma do filtro valem na mesma linha ativa da matrícula (aluno com Jiu-Jitsu numa turma e Judô em outra não aparece em modalidade Jiu-Jitsu com a turma do Judô).
- O filtro de nível na tela depende da modalidade escolhida e lê os níveis de `GET /modalities/{id}/graduation`; o servidor aceita `levelId` e devolve lista vazia (D13).
- `counts` usa só alcance e unidade (R20), não a busca por nome nem a faixa.
- `StudentList` (lista com `counts`) substitui `PageResponse` no controller; o JSON é o mesmo mais `counts`.
- `QueryPage` ganhou `counts` opcional em `Page` e no `filters`; as demais listas não mudam.
- Lista simples: aula sem turma devolve lista vazia, e o Professor também a vê quando é o professor da aula (substituto).

Não é do item: nenhuma mudança fora do escopo.

### Parte 5 (T20 a T24)

- T20 — arquivos: `features/students/{RecordEnrollment.tsx (novo), RecordSummary.tsx, StudentRecordPanel.tsx, useStudentRecord.ts (`useEnrollment`, `useResource` aceita caminho nulo), types.ts}`, `i18n/pt-BR.json` — prova: `npx vitest run src/features/students` → `StudentRecordPanel.test.tsx` (situação no cabeçalho, Resumo com modalidades, aba Matrícula com plano, vencimento, plano agendado, só leitura em unidade de outra gestão, Encerrar desligado com a dica, 403 e 500) e `RecordEnrollment.test.tsx` (novo: carregando, sem permissão, erro, vazio, encerrada, axe a 360 px nos dois temas) → verdes.
- T21 — arquivos: `ChangeClassDialog.tsx`, `ChangePlanDialog.tsx`, `ModalityPickers.tsx` (selects, outros horários, janela com os quatro estados e erro do servidor no campo), testes `ChangeClassDialog.test.tsx`, `ChangePlanDialog.test.tsx`, `enrollmentFixtures.ts` — prova: `npx vitest run src/features/students/Change` → verdes (corpo enviado, "vale na hora", "vale a partir da próxima cobrança", 422 no campo, 403, carregando, erro, sem permissão, axe nos dois temas a 360 px).
- T22 — arquivos: `TransferDialog.tsx`, `TransferDialog.test.tsx` — prova: `npx vitest run src/features/students/Transfer` → verde (só outras unidades, turmas e planos da unidade nova, vencimento herdado quando há outra modalidade ativa na unidade, 422 no campo aviso das cobranças, estados, axe).
- T23 — arquivos: `StudentRecordPanel.tsx` (Professor só com Resumo e Responsáveis, matrícula não pedida a ele), `RecordGuardians.tsx` (`canManage`: botões de escrita só com `STUDENT_MANAGE`) — prova: `StudentRecordPanel.test.tsx` (Professor sem abas Matrícula, Financeiro, Graduações e Histórico, sem botões, sem chamada de matrícula; gestão mantém os botões) e `StudentGuardiansTab.test.tsx` → verdes.
- T24 — arquivos: `frontend/e2e/matricula.spec.ts` (novo; `@critical`: matrícula pela API do simulado, troca de turma e transferência pela ficha) — prova: `npm run lint && npm run typecheck && npm run build && npm run test:cov && npm run e2e` → lint, typecheck e build limpos; `test:cov` 95 arquivos, 867 testes verdes (linhas 97,28%); `e2e` 32 passaram.

Regressão da Parte 5: só o front mudou. Back-end não foi tocado nesta parte (o `verify` não foi repetido; o contrato e as rotas não mudaram, `docs/openapi.yaml` e `mocks/handlers.ts` intactos). Frontend: comandos da T24 verdes.

Desvios da Parte 5:
- O Professor ainda não tem rota para abrir a ficha (`/alunos/:id` é só da gestão): o teste do Professor monta `StudentRecordPanel` direto; a tela do Professor chega com a ligação a turma e aula.
- Para o Professor as abas são só Resumo e Responsáveis (Matrícula, Financeiro, Graduações e Histórico saem): a matrícula é da gestão (403 do servidor) e as duas abas vazias não têm conteúdo para ele.
- A situação do cabeçalho e as modalidades do Resumo vêm de `GET /students/{id}/enrollment` (só pedido à gestão), não do `RecordView`, que não muda.
- A aba "Matrícula" mostra também modalidades encerradas (sem ações); Encerrar só aparece nas ativas editáveis, desligado (D15).
- Trocar plano deixa o botão desligado quando o plano escolhido é o atual sem agendamento (o servidor daria 422).
- Janela Transferir pede o dia de vencimento como número de 1 a 28; o servidor confere `due_days` da unidade (as opções de dia por unidade não são lidas no front).
- Os canvas `TrocarTurma.dc.html`, `TrocarPlano.dc.html` e `TransferirModalidade.dc.html` não estão no repositório; as janelas seguem a especificação.
- `useResource` passou a aceitar caminho nulo (estado "idle") para a matrícula não ser pedida a quem não é gestão.

Não é do item: nenhuma mudança fora do escopo.

### Parte 6 (T25 e T26)

- T25 — arquivos: `Students.unitZone` e `studentZone` (novos), `StudentRegistration.personal(Input, ZoneId)` e `clean`, `StudentEdit`, `identity/InviteService` (`unitZone`), `MajorityNoticeJob` (consulta no fuso da unidade de cadastro), `StudentGuardians.endAccess`, `StudentDatesIT` (novo: unidades de Brasília e Tóquio, relógio em 23h30 de Brasília) — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test=StudentDatesIT` → 4 testes, 0 falhas.
- T26 — prova: `./backend/mvnw -f backend/pom.xml verify` → 484 testes, 0 falhas, BUILD SUCCESS (JaCoCo aprovado; as 5 falhas de relógio da Parte 3 não ocorreram nesta hora e o `StudentDatesIT` cobre o fuso); `grep -rl` em `backend/src/test`: RN-MAT-01, 02, 04, 15, 19, 20, RN-ORG-12 e RN-ALU-07 aparecem em ao menos um teste cada; em `frontend/`, `npm run lint && npm run typecheck && npm run build && npm run test:cov && npm run e2e` → verdes (95 arquivos, 867 testes, linhas 97,28%; e2e 32 passaram). `ContractRoutesTest` e `OpenApiContractTest` dentro do `verify`.

Desvios da Parte 6: nenhum. O `verify` rodou de manhã (Brasília); a robustez à noite vem dos testes com relógio simulado.

Não é do item: nenhuma mudança fora do escopo.

## Verificação

Escopo desta rodada: Parte 1 (T1 a T5; CA1 a CA12 e a parte de CA38 que a T5 cobre). As Partes 2 a 6 não foram construídas e não são julgadas.

### Rodada 1 — CORRIGIR_CODIGO — 2026-10-20, estado 822faca009d70c3674afca13ccb4f55bc5ff6f16

| Critério | Status | Evidência |
|---|---|---|
| CA1 | PASSOU | `EnrollmentIT.createsAndAdds` e `concurrent` verdes |
| CA2 | PASSOU | `EnrollmentIT.limitsAndAtomicity` (10ª entra, 11ª 422, repetida 409) |
| CA3 | PASSOU | `EnrollmentIT.validatesRules`, `otherClasses` |
| CA4 | PASSOU | `EnrollmentIT.limitsAndAtomicity` (segunda inválida, nada gravado) |
| CA5 | PASSOU | `EnrollmentIT.validatesRules`, `createsAndAdds` (herança do dia, `due_days`) |
| CA6 | PASSOU | `EnrollmentIT.createsAndAdds`, `returns` |
| CA7 | NÃO CONFERIDO (parcial) | 200 "sem matrícula" e 404 de outra organização passam em `readsAndReach`; `canEdit` falso para modalidade da unidade B lida pelo Administrador da A não tem teste |
| CA8 | PASSOU | `EnrollmentIT.returns` |
| CA9 | PASSOU | `EnrollmentIT.anonymised` |
| CA10 | PASSOU | `EnrollmentIT.lookup` |
| CA11 | PASSOU | `EnrollmentIT.profiles`, `readsAndReach` (duas organizações) |
| CA12 | PASSOU | `EnrollmentIT.createsAndAdds` (auditoria) |
| CA38 (parcial, Parte 1) | PASSOU | `OpenApiContractTest`, `ContractRoutesTest` e `SeedDataIT` dentro do `verify`; `seed.sh` não rodou (sem docker-compose de pé; idempotência provada pelo `SeedDataIT`) |

Comandos:
- `./backend/mvnw -f backend/pom.xml verify` → 463 testes, 0 falhas, JaCoCo aprovado, BUILD SUCCESS.
- `npx vitest run src/mocks/enrollment.msw.test.ts src/mocks/students.msw.test.ts` → 18 verdes.
- `npm run lint` e `npm run typecheck` (frontend) → limpos.
- Não rodados: `npm run test:cov`, `build`, `e2e` (só `mocks/` mudou no front; sem linha de base registrada).

Achados:
- obrigatório, código, `backend/src/test/java/br/com/ctcore/student/EnrollmentIT.java` — esperado: CA7 exige `canEdit` falso para modalidade da unidade B lida pelo Administrador da A; observado: nenhum teste exercita `canEdit` (o código em `Enrollments.read` usa `permissions.grant` por unidade e parece correto); evidência: grep de `canEdit` no teste sem ocorrência; corrigir: caso em `readsAndReach` com aluno cadastrado na A, modalidade na B criada pelo Coordenador, leitura pelo Administrador da A com `canEdit` falso na B e verdadeiro numa modalidade da A.
- sugestão, código, `Enrollments.add` — o 403 de unidade fora do alcance sai depois de `students.detail` e da trava; ok, só registrar que o Coordenador com unidade inexistente recebe 403 (desvio já declarado).

### Correção da rodada 1
- CA7 `canEdit` por unidade sem teste — arquivos: `backend/src/test/java/br/com/ctcore/student/EnrollmentIT.java` (`readsAndReach`: Administrador da A lê matrícula com modalidade na A e na B; `canEdit` verdadeiro só na A) — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test=EnrollmentIT` → 10 testes, 0 falhas.
Regressão: só um teste mudou (sem código de produção); `verify` completo da rodada anterior (463 verdes) não foi repetido nesta correção.

### Rodada 2 — OK — 2026-10-20, estado 906239f1b6bf48a40c7c566e2969553d496e4dc8

Escopo: Parte 1 (T1 a T5). Diff desde a rodada 1: só `EnrollmentIT.readsAndReach` (4 linhas) e este arquivo.

| Critério | Status | Evidência |
|---|---|---|
| CA1 a CA6, CA8 a CA12 | PASSOU | mantido da rodada 1; `verify` completo repetido, verde |
| CA7 | PASSOU | `EnrollmentIT.readsAndReach` lê a matrícula como Administrador da A com modalidades na A e na B e afirma `canEdit` verdadeiro só na A; 200 sem matrícula e 404 de outra organização já passavam |
| CA38 (parcial, Parte 1) | PASSOU | `OpenApiContractTest`, `ContractRoutesTest`, `SeedDataIT` dentro do `verify`; `seed.sh` não rodou (sem docker-compose; idempotência provada pelo `SeedDataIT`) |

Comandos:
- `./backend/mvnw -f backend/pom.xml verify` → 463 testes, 0 falhas, BUILD SUCCESS.
- Front não reexecutado: nada mudou em `frontend/` desde a rodada 1.

Achados da rodada 1: o obrigatório (CA7 `canEdit` sem teste) foi corrigido.
Achados: nenhum obrigatório. Sugestão: manter o desvio do 403 para unidade inexistente declarado na Construção.

### Rodada 3 — OK — 2026-10-20, estado c30344c0acdaa7e10014ecf8de63724febcbd866

Escopo: Parte 2 (T6 a T9; CA13 a CA18 e a parte de CA38 que a T9 cobre). Parte 1 mantida da rodada 2 (diff desde ela: `Enrollments`, `StudentController`, `openapi.yaml`, `handlers.ts` e testes da Parte 2; `validate` só foi refatorado em `validateClasses` e `validatePlan`, sem mudar comportamento, e `EnrollmentIT` segue verde). Partes 3 a 6 não construídas e não julgadas.

| Critério | Status | Evidência |
|---|---|---|
| CA1 a CA12 | PASSOU | mantido da rodada 2; `verify` completo repetido, verde |
| CA13 | PASSOU | `EnrollmentChangesIT.changesClass`: leitura seguinte mostra Y, turma X sem alunos, outros horários substituídos, 422 sem mudança, auditoria `student.class_changed` com antes e depois sem CPF |
| CA14 | PASSOU | `changesPlan`: `plan` mantido, `nextPlan` P2, substituição, pedir P1 limpa, inválidos 422, atual sem agendamento 422, 3 auditorias |
| CA15 | PASSOU | `transfers`: mesmo id, `started_at` e `enrollment_id`, unidade, turma, plano e vencimento novos, auditoria com origem e destino |
| CA16 | PASSOU | `transfers`: mesma unidade, unidade inativa, turma e plano da antiga, vencimento fora: 422; outra organização, modalidade inexistente e de outro aluno: 404. Vencimento ausente numa unidade sem outra modalidade dá 400 (não 422), coerente com o desvio declarado |
| CA17 | PASSOU | `transfers`: contagens de plano, turma, aluno e matrícula intactas (sem tabela de cobrança ainda) |
| CA18 | PASSOU | `reachAndProfiles`: Administrador da A 403 na B e no destino fora do alcance; Coordenador pode; Professor, Aluno, Responsável 403; 401; outra organização 404 |
| CA38 (parcial, Parte 2) | PASSOU | `OpenApiContractTest`, `ContractRoutesTest` dentro do `verify`; `enrollment.msw.test.ts` verde |

Comandos:
- `./backend/mvnw -f backend/pom.xml verify` → 467 testes, 0 falhas, BUILD SUCCESS (JaCoCo aprovado).
- `npx vitest run src/mocks` (frontend) → 151 verdes.
- `npm run lint` e `npm run typecheck` (frontend) → limpos.
- Não rodados: `npm run test:cov`, `build`, `e2e` (só `mocks/` mudou no front; ficam para T18, T19, T26).

Achados:
- nenhum obrigatório.
- sugestão, código, `Enrollments.changePlan` — pedir o plano atual para limpar o agendamento chama `validatePlan`; se o plano atual foi inativado depois, a limpeza dá 422. Pular a validação quando `current` evita travar o aluno.
- sugestão, código, `EnrollmentChangesIT` — repete os auxiliares de `EnrollmentIT` (já declarado em "Desvios").

### Rodada 4 — CORRIGIR_CODIGO — 2026-10-20, estado 1ebdf6be20529d9453717c410c56659d48747b9b

Escopo: Parte 3 (T10 a T14; CA19 a CA26 e a parte de CA38 que a T14 cobre). Partes 1 e 2 mantidas da rodada 3 (`EnrollmentIT` e `EnrollmentChangesIT` verdes no `verify`). Partes 4 a 6 não construídas e não julgadas. Diff lido: `ModalityUsage`, `UnitDeactivationBlockers`, `TrainingClasses`, `Students` (`inUnits`, `lookup`, `birthdays`), `StudentRecords` (`teacherUnits`, `teacherReach`), spec `08` RN-ALU-07, testes e mocks da parte.

| Critério | Status | Evidência |
|---|---|---|
| CA1 a CA18 | PASSOU | mantido da rodada 3; `EnrollmentIT`, `EnrollmentChangesIT` verdes no `verify` |
| CA19 | PASSOU | `EnrollmentCountsIT.counts`: 409 `MODALITY_IN_USE` com `activeStudents` 2; com modalidade encerrada, 200. `ModalitiesPage.test.tsx` já lê o campo `activeStudents` |
| CA20 | NÃO CONFERIDO (parcial) | `counts`: `deactivation-check` da unidade, painel e lista de turmas (principal e outro horário, marca `primary`), turma com principal 409, `overCapacity` só avisa: passam. A lista de unidades (`GET /units`, `UnitList.summary`) usa o mesmo `blockers.activeStudents`, mas só há teste com valor 0 (`UnitListIT`); nenhum teste a vê com aluno real |
| CA21 | PASSOU | `StudentReachIT.reach`: lista, filtro `unitId`, ficha 404/200, sem modalidade pela unidade de cadastro, Coordenador, outra organização |
| CA22 | PASSOU | `StudentReachIT.viewsBothEditsOwn`: duas modalidades, `canEdit` só na A, 403 na B, edição de dados pessoais 200 |
| CA23 | PASSOU | `StudentRecordTeacherIT.reach` (titular, auxiliar, principal, outro horário; outra turma, inativa, encerrada, outra organização: 404) e `keysPerUnit` (403 com a chave desligada na unidade da turma e ligada em outra) |
| CA24 | PASSOU | `keysPerUnit`: contatos só com `STUDENT_CONTACTS` na unidade da turma |
| CA25 | PASSOU | `allowedFields`: reprova campo novo de `RecordView` fora das duas listas; corpo serializado sem CPF, endereço, plano, benefício, valores |
| CA26 | PASSOU | `StudentBirthdaysIT` (matrícula ativa, Professor com recorte e contato por unidade) e `birthdays.msw.test.ts` |
| CA38 (parcial, Parte 3) | PASSOU | `OpenApiContractTest`, `ContractRoutesTest`, `SeedDataIT` dentro do `verify`; nenhuma rota nova na parte |

Comandos:
- `./backend/mvnw -f backend/pom.xml verify` → 474 testes, 5 falhas (`PlanWriteIT.create:122`, `StudentListIT.lists:108`, `StudentListIT.overlappingRanges:181`, `StudentRecordIT.record:85`, `StudentSealsIT.referenceDateIsTheUnitDate:113`), BUILD FAILURE. As 5 comparam `LocalDate.now()` ou a idade com o fuso da unidade; rodaram às 21h50 de Brasília (00h50 UTC do dia seguinte); nenhum desses arquivos foi tocado (`git status` limpo para eles) e o item os cobre na Parte 6 (R28, S8). Falha anterior dependente do relógio, não é regressão da Parte 3; os testes da parte (`EnrollmentCountsIT`, `StudentReachIT`, `StudentRecordTeacherIT`, `StudentBirthdaysIT`, `EnrollmentIT`, `EnrollmentChangesIT`, `Modality*IT`, `Unit*IT`, `TrainingClass*IT`, `Agenda*IT`) passaram.
- `npx vitest run src/mocks` (frontend) → 152 verdes; `npm run lint` e `npm run typecheck` limpos.
- Não rodados: `npm run test:cov`, `build`, `e2e` (só `mocks/` mudou no front; ficam para T18, T19, T26).

Achados:
- obrigatório, código, `backend/src/test/java/br/com/ctcore/student/EnrollmentCountsIT.java` (`counts`) ou `organization/UnitListIT` — esperado: CA20 exige que a lista de unidades traga a contagem real; observado: `UnitListIT` só afirma `activeStudents` igual a 0 e `EnrollmentCountsIT` só lê `deactivation-check`; evidência: grep de `activeStudents` em `src/test` sem caso de `GET /api/v1/units` com aluno ativo (o código de `UnitList.summary` roda dentro da transação da organização e parece correto); corrigir: acrescentar em `counts` a leitura de `GET /api/v1/units` com os dois alunos ativos (valor 2 na unidade) e, depois de encerrar as modalidades, 0.
- sugestão, código, `StudentRecords.birthdays` — `teacherUnits` roda uma consulta por aniversariante do Professor (N+1); a lista do dia é curta, só registrar o teto.
- sugestão, código — rodar o `verify` completo fora da janela 21h a 24h de Brasília até a R28 existir, para a linha de base não oscilar.

### Correção da rodada 4
- CA20 lista de unidades sem teste com aluno real — arquivos: `backend/src/test/java/br/com/ctcore/student/EnrollmentCountsIT.java` (`counts`: `GET /api/v1/units` afirma `activeStudents` 2 com os dois alunos ativos e 0 depois de encerrar as modalidades) — prova: `./backend/mvnw -f backend/pom.xml verify -Dit.test=EnrollmentCountsIT,UnitListIT -Djacoco.skip=true` → 5 testes, 0 falhas.
Regressão: só um teste mudou (sem código de produção); `verify` completo não repetido (as 5 falhas dependentes do relógio, S8/R28, continuam como falha anterior; sugestões do N+1 e da janela de horário apenas registradas).

### Rodada 5 — OK — 2026-10-20, estado 01f694d90fc0af752d877d343587486316bc30a3

Escopo: Parte 3 (T10 a T14). Diff desde a rodada 4: só `EnrollmentCountsIT.counts` (duas linhas) e este arquivo; nenhum código de produção mudou.

| Critério | Status | Evidência |
|---|---|---|
| CA1 a CA19, CA21 a CA26 | PASSOU | mantido da rodada 4; `verify` repetido, sem falha nova |
| CA20 | PASSOU | `EnrollmentCountsIT.counts` agora lê `GET /api/v1/units` e afirma `activeStudents` 2 com os dois alunos ativos e 0 depois de encerrar as modalidades; demais partes já passavam |
| CA38 (parcial, Parte 3) | PASSOU | `OpenApiContractTest`, `ContractRoutesTest`, `SeedDataIT` dentro do `verify` |

Comandos:
- `./backend/mvnw -f backend/pom.xml verify` → 474 testes, 5 falhas, as mesmas da rodada 4 (`PlanWriteIT.create:122`, `StudentListIT.lists:108`, `StudentListIT.overlappingRanges:181`, `StudentRecordIT.record:85`, `StudentSealsIT.referenceDateIsTheUnitDate:113`), rodadas às 21h58 de Brasília (já dia seguinte em UTC); arquivos não tocados pela parte, dependentes do relógio, cobertos por R28 na Parte 6. Não é regressão. Todos os ITs da Parte 3 verdes.
- Front não reexecutado: nada mudou em `frontend/` desde a rodada 4.

Achados:
- nenhum obrigatório. O obrigatório da rodada 4 (CA20, lista de unidades) foi corrigido.
- sugestão, código — rodar o `verify` completo fora da janela 21h a 24h de Brasília para a linha de base ficar verde até a R28.

### Rodada 6 — OK — 2026-10-20, estado 5b67ddd5b6e127e27f2e6e30238b050d9d1411b1

Escopo: Parte 4 (T15 a T19; CA27 a CA32, CA36 nas telas da parte, e a parte de CA38 que a T19 cobre). Partes 1 a 3 mantidas das rodadas 3 e 5 (`EnrollmentIT`, `EnrollmentChangesIT`, `EnrollmentCountsIT`, `StudentReachIT`, `StudentRecordTeacherIT` verdes no `verify`). Partes 5 e 6 não construídas e não julgadas. Diff lido desde a rodada 5: `Students` (`Filter`, `StudentList`, `counts`, `STATUS`, `NEW`, modalidades da linha), `StudentSimple`, `StudentController`, `StudentSeal`, `openapi.yaml`, `QueryPage`, filtros, coluna, lista simples, painel da aula, mocks e testes.

| Critério | Status | Evidência |
|---|---|---|
| CA1 a CA26 | PASSOU | mantido das rodadas 3 e 5; `verify` repetido, sem falha nova |
| CA27 | PASSOU | `StudentListEnrollmentIT` (linha com modalidades, unidade, turma principal e `status`; sem matrícula: lista vazia) e `StudentsPage.test` CA27 (coluna, várias modalidades, "Sem matrícula") |
| CA28 | PASSOU | `StudentListEnrollmentIT`: modalidade, turma (principal e outro horário), nível (vazio sem erro), combinados com unidade e busca; turma de outra organização e inexistente 404; `status` inválido 400. Combinação com faixa e selo coberta nos testes existentes de cada filtro; tela em `StudentsPage.test` |
| CA29 | PASSOU | `StudentListEnrollmentIT.counts` (active 3, former 1, sem o filtro rápido, por unidade, Administrador só no alcance) e `StudentsPage.test` CA29 (botões com contagem, filtram, desligam) |
| CA30 | PASSOU | `StudentSealsIT.newSeal` (30 e 31 dias, reabertura, 23h30 de Brasília, Tóquio, lista, ficha e `seal=NEW`) |
| CA31 | PASSOU | `StudentSimpleListIT` (Professor titular e auxiliar, só nome, foto, faixa e `blocked`; outra turma 404; ficha 403 com a chave desligada e a lista continua; 401; Aluno e Responsável 403; outra organização 404) |
| CA32 | PASSOU | `StudentSimpleListIT` (gestão por alcance, `lessonId`), `StudentSimpleList.test` e `LessonPanelPage.test` (cartão da aula). Painel da turma não mudou (já listava os alunos), declarado nos desvios |
| CA36 (telas da parte) | PASSOU | axe a 360 px nos dois temas em `StudentsPage.test`, `StudentSimpleList.test` e `LessonPanelPage.test`; `npm run lint` limpo (sem texto literal). Janelas e abas da Parte 5 ficam para a rodada dela |
| CA38 (parcial, Parte 4) | PASSOU | `OpenApiContractTest` e `ContractRoutesTest` dentro do `verify` (`/students/simple`, filtros, `counts`) |

Comandos:
- `./backend/mvnw -f backend/pom.xml verify` → 480 testes, 5 falhas, BUILD FAILURE: `PlanWriteIT.create:122`, `StudentListIT.lists:108`, `StudentListIT.overlappingRanges:181`, `StudentRecordIT.record:85`, `StudentSealsIT.referenceDateIsTheUnitDate:114`. São as mesmas 5 da linha de base das rodadas 4 e 5 (data e idade no fuso da unidade contra UTC; rodou às 22h42 de Brasília, 01h42 UTC do dia seguinte), cobertas pela R28 na Parte 6; nenhuma é regressão da Parte 4 (a única mudança em `StudentListIT` foi a troca de lista vazia por 404 do R19, linha 234, que passa). Todos os ITs novos da parte verdes.
- Frontend: `npm run lint` e `npm run typecheck` limpos; `npm run test:cov` → 91 arquivos, 836 testes verdes, linhas 97,26%; `npm run build` verde; `npm run e2e` → 31 passaram.

Achados:
- nenhum obrigatório.
- sugestão, código — rodar o `verify` completo fora da janela 21h a 24h de Brasília até a R28 existir, para provar as 5 falhas de relógio verdes.
- sugestão, código, `Students.counts` — ignora busca e faixa (R20 só pede alcance e unidade; já declarado nos desvios). Sem ação.

### Rodada 7 — OK — 2026-10-20, estado 52fb7ea104d90b52aadf23062eb5d33b539cafab

Escopo: Parte 5 (T20 a T24; CA33 a CA36 e CA33/CA34 no ponta a ponta). Partes 1 a 4 mantidas da rodada 6. Parte 6 não construída e não julgada. Diff desde a rodada 6: só `frontend/` (aba, janelas, resumo, painel, guardiões, `useStudentRecord`, `pt-BR.json`, `e2e/matricula.spec.ts`) e este arquivo; nenhum arquivo de `backend/`, `docs/openapi.yaml` nem `mocks/handlers.ts` mudou.

| Critério | Status | Evidência |
|---|---|---|
| CA1 a CA32 | PASSOU | mantido das rodadas 3, 5 e 6 (back-end sem mudança desde então) |
| CA33 | PASSOU | `StudentRecordPanel.test` (Sem matrícula no cabeçalho e vazio; aba com plano, vencimento, plano agendado, Encerrar desligado com a dica; unidade de outra gestão sem ações; 403 e 500) e `RecordEnrollment.test` (carregando, sem permissão, erro, vazio); Resumo com modalidades via `RecordSummary` |
| CA34 | PASSOU | `ChangeClassDialog`, `ChangePlanDialog`, `TransferDialog` testes (corpo enviado, "vale na hora" e "próxima cobrança", 422 no campo sem fechar, 403, fecha e atualiza no sucesso); `StudentRecordPanel.test` (Trocar turma pela ficha); handlers MSW das três rotas da Parte 2; e2e `matricula.spec.ts` passou |
| CA35 | PASSOU | `StudentRecordPanel.test` (Professor só Resumo e Responsáveis, sem botões, sem Histórico, sem pedir a matrícula; gestão mantém botões) e `StudentGuardiansTab.test` |
| CA36 | PASSOU | axe a 360 px nos dois temas nos quatro testes novos (`RecordEnrollment`, três janelas); `npm run lint` limpo (sem texto literal) |
| CA37, CA38 final | NÃO CONFERIDO | Parte 6 (T25, T26), fora do escopo da rodada |

Comandos (em `frontend/`):
- `npm run lint` e `npm run typecheck` → limpos.
- `npm run test:cov` → 95 arquivos, 867 testes verdes; linhas 97,28%.
- `npm run build` → verde.
- `npm run e2e` → 32 passaram.
- Back-end: `verify` não repetido (nenhum arquivo de `backend/` mudou desde a rodada 6; as 5 falhas de relógio da linha de base continuam atribuídas à R28, Parte 6).

Achados:
- nenhum obrigatório.
- sugestão, código, `ModalityPickers.tsx` e `TransferDialog.tsx` — o dia de vencimento da transferência é número livre de 1 a 28; o servidor confere `due_days` da unidade e devolve 422 no campo. Ler os dias permitidos da unidade nova simplificaria o uso (já declarado nos desvios).
- sugestão, código, `RecordGuardians.tsx` — indentação do bloco `canManage` fora do padrão do Prettier (lint passou).

### Rodada 8 — OK — 2026-10-21, estado 8c53fba9a1077e3c0b26fa9985b2d8edac2f7abe

Escopo: Parte 6 (T25 e T26; CA37 e CA38 final). Partes 1 a 5 mantidas das rodadas 3, 5, 6 e 7. Diff desde a rodada 7: `InviteService`, `MajorityNoticeJob`, `StudentEdit`, `StudentGuardians`, `StudentRegistration`, `Students` (`unitZone`, `studentZone`), `StudentDatesIT` (novo) e ajuste de relógio em `PlanWriteIT`, `StudentListIT`, `StudentRecordIT`, `StudentSealsIT`; nada em `frontend/`, `openapi.yaml` nem `handlers.ts`.

| Critério | Status | Evidência |
|---|---|---|
| CA1 a CA36 | PASSOU | mantido; `verify` completo verde, sem falha nova |
| CA37 | PASSOU | `StudentDatesIT` (4 testes): cadastro, edição, convite e aviso de maioridade às 23h30 de Brasília com unidades de Brasília e Tóquio; `StudentGuardians.endAccess` usa o fuso do aluno (sem teste próprio de data, o critério lista só as quatro leituras) |
| CA38 | PASSOU | `OpenApiContractTest`, `ContractRoutesTest`, `SeedDataIT` e JaCoCo ("All coverage checks have been met") dentro do `verify`; `grep -rl` em `backend/src/test` acha RN-MAT-01, 02, 04, 15, 19, 20, RN-ORG-12 e RN-ALU-07 |

Comandos:
- `./backend/mvnw -f backend/pom.xml verify` (08h09 de Brasília) → 484 testes, 0 falhas, BUILD SUCCESS. As 5 falhas de relógio da linha de base foram resolvidas: o teste usa o fuso de Brasília e `StudentDatesIT` fixa o relógio; a robustez às 21h–24h vem do relógio simulado em `StudentDatesIT`, mas `PlanWriteIT`, `StudentListIT` e `StudentRecordIT` agora usam `America/Sao_Paulo` fixo, coerente com a unidade de teste.
- Front não reexecutado: nada mudou em `frontend/` desde a rodada 7 (lint, typecheck, build, `test:cov` 867 verdes, e2e 32 verdes).

Achados:
- nenhum obrigatório.
- sugestão, código, `StudentDatesIT` — acrescentar caso para `StudentGuardians.endAccess` (data de fim do vínculo no fuso do aluno), a quinta leitura que a R28 cita.
- sugestão, código, `Students.unitZone`/`InviteService.unitZone` — duas implementações da mesma consulta de fuso; unificar quando tocar de novo.

## Entrega

- Matrícula no servidor: modalidades, plano e turma por matrícula, transferência de unidade, retorno de aluno inativo e contagem `ModalityUsage.activeStudents` (migração V33, `Enrollments`, lista simples, seção de matrícula na ficha, diálogos de transferência, troca de plano e de turma, seed `14-matriculas.sql`).
- Datas de cadastro, edição, convite e aviso de maioridade passam a usar o fuso da unidade/aluno (CA37).
- Verificação: rodada 8 `OK`. `./backend/mvnw -f backend/pom.xml verify` com 484 testes verdes e JaCoCo atendido; front (rodada 7): lint, typecheck, build, `test:cov` 867 testes e e2e 32 verdes.
- Limitações: sem teste próprio de data para `StudentGuardians.endAccess`; `unitZone` duplicado em `Students` e `InviteService` (sugestões da rodada 8, sem impacto funcional).

Closes #63
