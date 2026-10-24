# 06 · Turmas e agenda

Entrega 1 · Fase 6 · Depende de: 04, 05 · Código: TUR, AGE

## Objetivo

Organizar as turmas da unidade e as aulas que nascem delas. Sem presença nem frequência.

## Dados

- **Turma:** unidade, nome, modalidade, faixa etária (opcional), horários (dia da semana, início e término, um por dia, podendo variar por dia), capacidade (opcional), professor titular, auxiliares, link do grupo de WhatsApp (opcional), situação (Ativa, Desativada).
- **Aula:** turma (ou nenhuma, se avulsa), unidade, data, início, término, professor da aula, substituto (sim/não), situação (Agendada, Em andamento, Concluída, Cancelada), motivo do cancelamento, observação para a equipe, alterada manualmente (sim/não).
- **Aula avulsa:** além da aula, título, modalidade e visibilidade (uma turma específica ou todas as turmas da unidade).
- **Dia sem aula:** unidade, data ou período, motivo, turmas afetadas (todas por padrão), situação do aviso (Ainda não avisado, Agendado, Enviado), origem (feriado sugerido, unidade, Coordenador).

## Regras

### Turmas

- **RN-TUR-01.** O nome da turma é único na unidade enquanto ativa. Duas turmas podem ter a mesma modalidade e horário se o nome for diferente. A modalidade da turma tem de estar oferecida pela unidade (RF-MOD-03), e a unidade não muda depois que a turma é criada.
- **RN-TUR-02.** Capacidade é opcional e só avisa: acima dela a turma aparece "Acima da capacidade (31/30)", sem impedir matrícula. O aviso também aparece quando a matrícula em andamento faria a turma passar da capacidade. O teto técnico é 200 alunos por turma.
- **RN-TUR-03.** Turma com histórico (aulas passadas ou alunos) não é excluída, só desativada. Desativar exige que nenhum aluno a tenha como turma principal e é feito no painel da turma. A turma desativada continua listada e consultável, sai das escolhas de matrícula e de agenda e não é reativada nem editada (a edição devolve conflito).
- **RN-TUR-04.** O aluno tem uma turma principal por modalidade e pode ter outros horários (outras turmas da mesma modalidade na unidade) em que treina.
- **RN-TUR-05.** O professor titular é obrigatório. Auxiliares são opcionais e veem a turma como o titular. Titular e auxiliares têm de ser professores ativos da unidade da turma e ensinar a modalidade da turma, isto é, ter essa modalidade em "modalidades que ensina" (RN-PRO-09). Professor inativo ou que não ensina a modalidade não aparece nas escolhas, e o servidor o recusa (400 com o campo); a turma cujo titular ficou inativo ou deixou de ensinar a modalidade exige troca ao editar.
- **RN-TUR-06.** A turma não guarda presença. Não existe frequência no sistema.

### Agenda

- **RN-AGE-01.** Uma tarefa diária mantém sempre 8 semanas de aulas criadas a partir dos horários das turmas ativas. Dia sem aula já cadastrado gera a aula como Cancelada.
- **RN-AGE-02.** Mudar o horário da turma apaga e refaz só as aulas futuras Agendadas, não canceladas e não alteradas à mão; aula de hoje já iniciada não é tocada. Oferece o comunicado pronto.
- **RN-AGE-03.** Agendada, Em andamento e Concluída vêm do relógio, no fuso da unidade. Cancelada é ação de alguém.
- **RN-AGE-04.** Editar uma aula muda data, horário e professor, valendo "só esta aula" ou "esta e as seguintes". Em "esta e as seguintes", a data das seguintes se desloca pelo mesmo número de dias da mudança (mantendo o ritmo semanal) e o horário vale para todas, mesmo as já trocadas à mão; o professor também vale para todas, menos as que já têm substituto gravado, que mantêm o substituto (nas demais valem o horário e o professor desta edição); atinge só aulas da turma ainda não iniciadas. Trocar o professor de uma aula não muda o titular da turma e marca a aula como substituição. O substituto é professor ativo da organização que ensina a modalidade, de qualquer unidade. A janela de edição mostra, antes de confirmar "esta e as seguintes", quantas aulas já alteradas à mão serão atingidas, contando como alterada à mão a aula que já tem substituto gravado: nelas data e horário mudam, e o substituto fica. O professor da aula, na troca e na correção, e o professor da aula avulsa ensinam a modalidade da aula (RN-PRO-09).
- **RN-AGE-05.** Cancelar exige motivo: professor indisponível, feriado, evento na academia ou outro (com texto). Cancelar uma aula não envia comunicado: o aviso à turma sai por "Avisar turma", que abre o formulário de comunicado já preenchido (ver RN-AGE-10 e RN-COM-01).
- **RN-AGE-06.** Aula com aula experimental marcada só é cancelada depois de decidir: reagendar ou cancelar a experimental.
- **RN-AGE-07.** O Professor só edita e cancela aulas das suas turmas ou em que é substituto, e só com a permissão "editar e cancelar aulas". Os botões Editar aula e Cancelar aula só aparecem para quem pode. O Professor só cria aula avulsa em que ele mesmo é o professor.
- **RN-AGE-08.** Aula concluída não é editada pelo Professor nem cancelada. O Administrador e o Coordenador corrigem só o professor ou o substituto de aulas dos últimos 30 dias, com motivo e auditoria; data e horário não mudam. Aula cancelada não é editável. O professor ou substituto corrigido também ensina a modalidade da aula (RN-PRO-09).
- **RN-AGE-09.** Feriados nacionais aparecem como sugestão; a unidade confirma quais viram dia sem aula. Os feriados nacionais são calculados pelo sistema (1º/1, Sexta-feira Santa, 21/4, 1º/5, 7/9, 12/10, 2/11, 15/11, 20/11, 25/12; Carnaval e Corpus Christi ficam de fora); feriado estadual ou municipal a unidade lança à mão. Só o Coordenador lança um dia sem aula para várias unidades de uma vez; o Administrador lança uma unidade por vez. Cada unidade tem a sua linha e desfaz o seu sozinha.
- **RN-AGE-10.** Dia sem aula cancela as aulas afetadas e gera um comunicado por linha de dia (cada unidade tem a sua), enviado agora ou agendado (ex.: uma semana antes). Dia já avisado não é avisado de novo; ao editar o dia agendado o comunicado é refeito. Desfazer antes da data volta as aulas para Agendada e, se o dia já foi avisado, envia um aviso de aulas de volta. Cancelar uma aula não envia comunicado: o aviso sai por "Avisar turma", que abre o formulário já preenchido. Editar e desfazer só valem antes do dia; criar vale a partir de hoje e cancela só aula que ainda não terminou.
- **RN-AGE-11.** A mensalidade não muda por dia sem aula ou recesso.
- **RN-AGE-12.** Aula avulsa não pertence a turma; a visibilidade guarda a turma escolhida ou fica vazia para todas as turmas da unidade. Aparece na agenda de gestão da unidade e no portal só para a turma escolhida ou para todas as turmas da unidade.
- **RN-AGE-13.** Turma acima da capacidade não tem lista de espera.

## Requisitos

- **RF-TUR-01.** Operação › Turmas: lista com nome, modalidade, faixa etária, dias e horários, professor, alunos ativos/capacidade e situação; filtros por modalidade, professor, faixa etária, dia e situação. Sem filtro de situação a lista traz Ativas e Desativadas, com selo na desativada, e o filtro de dia casa com qualquer dia do horário. Só Coordenador e Administrador (nas suas unidades) acessam; o Professor vê as suas turmas pela Agenda e pelo painel do professor.
- **RF-TUR-02.** Painel da turma (Coordenador e Administrador): alunos ativos (com quem a tem como outro horário), próxima aula, horários, professores, grupo de WhatsApp; ações Editar, Ver alunos, Avisar turma, Abrir grupo (só link http ou https), Desativar (RN-TUR-03).
- **RF-TUR-03.** Janela de turma: nome, modalidade, faixa etária, horários por dia, capacidade, titular, auxiliares, link do grupo. Ao mudar horários, opção "Avisar a turma" marcada.
- **RF-AGE-01.** Operação › Agenda: no computador, grade da semana (dias por horários), cada aula com turma, professor e cor da modalidade; aula cancelada riscada com o rótulo "Cancelada"; dia sem aula sombreado com "Sem aula" e o motivo. No celular, lista do dia com setas para trocar de dia. Atalhos Hoje e Esta semana; filtros por professor e modalidade. Dia sem aula vale para as turmas afetadas (todas por padrão).
- **RF-AGE-02.** Painel da aula: horário, turma, professor (com selo "Substituição"), número de alunos, aulas experimentais marcadas, grupo; ações Avisar turma, Editar aula, Cancelar aula, Ver turma. O professor substituto só abre a aula do início do dia dela até o fim desse dia, no fuso da unidade (RN-PRO-06); fora disso recebe 404, e a lista da agenda continua mostrando a aula dele. O Professor da turma (titular, auxiliar ou substituto no dia da aula) vê os alunos da aula como lista simples (nome e ativo ou bloqueado na modalidade), sem abrir a ficha; isso não depende de nenhuma permissão por unidade (RN-PRO-06).
- **RF-AGE-03.** Janelas Editar aula e Cancelar aula (RN-AGE-04 a RN-AGE-06).
- **RF-AGE-04.** Nova aula avulsa, com o mesmo formulário de edição mais título, modalidade e visibilidade.
- **RF-AGE-05.** Dias sem aula: lista com sugestões de feriados a confirmar, novo dia sem aula com prévia das aulas canceladas, escolha de quando avisar, editar e desfazer.

## Pronto quando

- Não existe registro de presença em nenhuma tela nem tabela.
- Um feriado confirmado cancela todas as aulas do dia e gera um comunicado só.
- Mudar o horário da turma não desfaz uma aula alterada à mão.
- Sempre existem 8 semanas de aulas à frente.
