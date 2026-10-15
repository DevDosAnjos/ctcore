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

- **RN-TUR-01.** O nome da turma é único na unidade enquanto ativa. Duas turmas podem ter a mesma modalidade e horário se o nome for diferente.
- **RN-TUR-02.** Capacidade é opcional e só avisa: acima dela a turma aparece "Acima da capacidade (31/30)", sem impedir matrícula. O teto técnico é 200 alunos por turma.
- **RN-TUR-03.** Turma com histórico (aulas passadas ou alunos) não é excluída, só desativada. Desativar exige que nenhum aluno a tenha como turma principal.
- **RN-TUR-04.** O aluno tem uma turma principal por modalidade e pode ter outros horários (outras turmas da mesma modalidade na unidade) em que treina.
- **RN-TUR-05.** O professor titular é obrigatório. Auxiliares são opcionais e veem a turma como o titular.
- **RN-TUR-06.** A turma não guarda presença. Não existe frequência no sistema.

### Agenda

- **RN-AGE-01.** Uma tarefa diária mantém sempre 8 semanas de aulas criadas a partir dos horários das turmas ativas. Dia sem aula já cadastrado gera a aula como Cancelada.
- **RN-AGE-02.** Mudar o horário da turma refaz só as aulas futuras que não foram alteradas à mão, e oferece o comunicado pronto.
- **RN-AGE-03.** Agendada, Em andamento e Concluída vêm do relógio, no fuso da unidade. Cancelada é ação de alguém.
- **RN-AGE-04.** Editar uma aula muda data, horário e professor, valendo "só esta aula" ou "esta e as seguintes". Trocar o professor de uma aula não muda o titular da turma e marca a aula como substituição.
- **RN-AGE-05.** Cancelar exige motivo: professor indisponível, feriado, evento na academia ou outro (com texto). O cancelamento abre o comunicado pronto, com os canais escolhidos.
- **RN-AGE-06.** Aula com aula experimental marcada só é cancelada depois de decidir: reagendar ou cancelar a experimental.
- **RN-AGE-07.** O Professor só edita e cancela aulas das suas turmas ou em que é substituto, e só com a permissão "editar e cancelar aulas".
- **RN-AGE-08.** Aula concluída não é editada pelo Professor. O Administrador corrige professor ou substituto de aulas dos últimos 30 dias, com motivo e auditoria. Aula concluída não é cancelada.
- **RN-AGE-09.** Feriados nacionais aparecem como sugestão; a unidade confirma quais viram dia sem aula. O Coordenador lança um dia sem aula para várias unidades de uma vez; cada unidade pode desfazer o seu.
- **RN-AGE-10.** Dia sem aula cancela as aulas afetadas e gera um único comunicado para as turmas, enviado agora ou agendado (ex.: uma semana antes). Dia já avisado não é avisado de novo. Desfazer antes da data volta as aulas para Agendada e avisa.
- **RN-AGE-11.** A mensalidade não muda por dia sem aula ou recesso.
- **RN-AGE-12.** Aula avulsa aparece na agenda e no portal só para a turma escolhida ou para todas as turmas da unidade.
- **RN-AGE-13.** Turma acima da capacidade não tem lista de espera.

## Requisitos

- **RF-TUR-01.** Operação › Turmas: lista com nome, modalidade, faixa etária, dias e horários, professor, alunos ativos/capacidade e situação; filtros por modalidade, professor, faixa etária, dia e situação.
- **RF-TUR-02.** Painel da turma: alunos ativos (com quem a tem como outro horário), próxima aula, horários, professores, grupo de WhatsApp; ações Editar, Ver alunos, Avisar turma, Abrir grupo, Desativar.
- **RF-TUR-03.** Janela de turma: nome, modalidade, faixa etária, horários por dia, capacidade, titular, auxiliares, link do grupo. Ao mudar horários, opção "Avisar a turma" marcada.
- **RF-AGE-01.** Operação › Agenda: no computador, grade da semana (dias por horários), cada aula com turma, professor e cor da modalidade; aula cancelada riscada com o rótulo "Cancelada"; dia sem aula sombreado com "Sem aula" e o motivo. No celular, lista do dia com setas para trocar de dia. Atalhos Hoje e Esta semana; filtros por professor e modalidade.
- **RF-AGE-02.** Painel da aula: horário, turma, professor (com selo "Substituição"), número de alunos, aulas experimentais marcadas, grupo; ações Avisar turma, Editar aula, Cancelar aula, Ver turma.
- **RF-AGE-03.** Janelas Editar aula e Cancelar aula (RN-AGE-04 a RN-AGE-06).
- **RF-AGE-04.** Nova aula avulsa, com o mesmo formulário de edição mais título, modalidade e visibilidade.
- **RF-AGE-05.** Dias sem aula: lista com sugestões de feriados a confirmar, novo dia sem aula com prévia das aulas canceladas, escolha de quando avisar, editar e desfazer.

## Pronto quando

- Não existe registro de presença em nenhuma tela nem tabela.
- Um feriado confirmado cancela todas as aulas do dia e gera um comunicado só.
- Mudar o horário da turma não desfaz uma aula alterada à mão.
- Sempre existem 8 semanas de aulas à frente.
