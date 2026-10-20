# 05 · Professores

Entrega 1 · Fase 5 · Depende de: 01, 02, 04 · Código: PRO

## Objetivo

Cadastro de quem dá aula, separado da conta de acesso, ligado às unidades e modalidades em que ensina.

## Dados

- **Professor:** organização, nome, foto (JPG, PNG ou WebP, cortada em quadrado, 512 px), e-mail, telefone, unidades, modalidades que ensina, graduação por modalidade (nível e grau, informados no cadastro), apresentação (até 300 caracteres), aparecer no diretório (consentimento), situação (Ativo, Inativo), conta (opcional).

Sem CPF e sem endereço.

## Regras

- **RN-PRO-01.** Um cadastro por organização, ligado às unidades onde dá aula.
- **RN-PRO-02.** O cadastro de professor é separado da conta. "Dar acesso ao sistema" envia o convite e cria o vínculo de Professor nas unidades marcadas na janela (por padrão, todas as unidades do professor que quem convida alcança). O Coordenador convida e reenvia em qualquer caso; o Administrador, só para unidades dele.
- **RN-PRO-03.** A graduação do professor é uma por modalidade que ele ensina, informada no cadastro, e não passa pelo registro de graduação de alunos. Nível e grau só são pedidos quando a modalidade tem graduação ligada; com ela desligada ficam nulos e o dado já gravado é mantido, sem aparecer. Modalidades são opcionais e ao menos uma unidade é obrigatória.
- **RN-PRO-04.** A apresentação, o nome, a foto e a graduação aparecem no diretório e nos horários públicos das turmas só se o próprio professor consentir (marcado na conta dele, ou pela gestão com a declaração "o professor autorizou"). Nunca aparecem telefone nem e-mail. Marcar o consentimento pela gestão exige a declaração "o professor autorizou". O contato (e-mail e telefone) aparece só no painel, para a gestão autenticada.
- **RN-PRO-05.** Professor inativado some das escolhas de turma e de substituição. As turmas em que era titular entram no "Precisa de atenção" como "turma sem professor ativo" até alguém escolher outro. O histórico de aulas continua com o nome dele. O vínculo de acesso é desativado e as sessões encerradas. O Coordenador inativa qualquer professor; o Administrador só inativa se todas as unidades do professor forem dele (senão, usa Editar para tirar só as unidades dele). Inativar não pede motivo. Professor inativo também não é aceito como titular, auxiliar nem substituto de turma ou aula; a turma com titular inativo exige troca ao editar.
- **RN-PRO-06.** Professor substituto pode ser de outra unidade da organização, desde que ensine a modalidade. Ele vê a turma e os alunos dela só durante a substituição (do dia da aula até o fim desse dia). O servidor confere ao gravar a substituição que o substituto ensina a modalidade e, no painel da aula, só o deixa abrir a aula no dia dela (fora disso, 404).
- **RN-PRO-07.** O professor que também é aluno usa a mesma conta, com os dois vínculos.
- **RN-PRO-08.** Desconto de professor na mensalidade é um benefício comum (ver `07`). Encerrar o vínculo de professor encerra esse desconto.

## Requisitos

- **RF-PRO-01.** Pessoas › Professores: lista com foto, nome, modalidades, unidades, turmas e situação; filtros por nome, modalidade, unidade e situação. Sem filtro de situação, a lista traz Ativos e Inativos. O Administrador vê o professor que tem ao menos uma unidade dele e vê todas as unidades desse professor; gerencia só as suas unidades e não perde as de outros ao salvar.
- **RF-PRO-02.** Painel do professor: graduações, contato, turmas (titular e auxiliar), próximas aulas, situação do acesso; ações Editar, Dar acesso ou Reenviar convite, Inativar, WhatsApp.
- **RF-PRO-03.** Janela de cadastro e edição com os campos de Dados, contador na apresentação.
- **RF-PRO-04.** Inativar mostra quais turmas ficarão sem titular.

## Pronto quando

- Um professor inativo não aparece em nenhuma escolha e as turmas dele aparecem no "Precisa de atenção".
- O substituto de outra unidade perde acesso aos alunos da turma no dia seguinte à aula.
- Telefone e e-mail do professor nunca saem em resposta pública da API.
