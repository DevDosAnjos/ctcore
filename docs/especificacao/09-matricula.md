# 09 · Matrícula

Entrega 1 · Fase 9 · Depende de: 06, 07, 08 · Código: MAT

## Objetivo

Transformar uma pessoa em aluno: modalidades, turma, plano, vencimento, termo e primeira cobrança, num fluxo só, feito pela gestão.

## Dados

- **Matrícula:** organização, aluno, data de início, situação calculada do aluno (ver `12`), origem (gestão, importação, autocadastro, aula experimental), aula experimental de origem.
- **Modalidade da matrícula:** matrícula, modalidade, unidade, turma principal, outros horários, plano, benefício, situação (Ativa, Encerramento solicitado, Bloqueada por atraso, Bloqueada pela gestão, Encerrada), início, fim, motivo de saída.
- **Vencimento por unidade:** matrícula, unidade, dia de vencimento (vale para todas as modalidades do aluno naquela unidade).
- **Termo de matrícula:** versão, texto (da organização, a partir do modelo da plataforma), aceite por aluno.
- **Pedido do portal:** aluno, tipo (troca de turma, nova modalidade, troca de plano), detalhe, situação (Aberto, Aprovado, Recusado, Expirado), prazo.

## Regras

### Matrícula

- **RN-MAT-01.** Uma matrícula por aluno na organização, feita pela gestão. Ela pode cobrir várias modalidades e unidades, até 10 modalidades ativas. O aluno tem no máximo uma matrícula ativa por modalidade na organização, em uma unidade só; a transferência leva a modalidade para outra unidade. Treinar a mesma modalidade em mais de uma unidade é assunto do plano, não de matrícula duplicada.
- **RN-MAT-02.** Cada modalidade da matrícula tem unidade, turma principal, outros horários (opcional), plano e, se houver, um benefício.
- **RN-MAT-03.** O dia de vencimento é escolhido entre os dias permitidos da unidade e vale para todas as modalidades do aluno naquela unidade.
- **RN-MAT-04.** Ao concluir a matrícula o aluno já fica Ativo.
- **RN-MAT-05.** Primeira cobrança: o primeiro mês é sempre cheio e vence no dia da matrícula, com até 5 dias de tolerância antes de contar como atraso. A taxa de matrícula é opcional e nasce desligada. Se a unidade ligar, ela vai junto, cobrada uma vez por aluno naquela unidade; o Administrador pode isentar, com motivo. A taxa não bloqueia o aluno por atraso.
- **RN-MAT-06.** O Administrador registra o pagamento no balcão na própria tela de conclusão, ou deixa o aluno pagar pelo portal.
- **RN-MAT-07.** Segunda cobrança: o sistema sugere o primeiro vencimento a pelo menos 15 dias da matrícula, no dia escolhido; o Administrador pode ajustar a data.
- **RN-MAT-08.** O convite do portal sai sozinho ao concluir: para o aluno adulto ou para cada responsável. Na importação, o Administrador dispara os convites depois.
- **RN-MAT-09.** A graduação de origem é informada aqui, por modalidade (ver `04`).
- **RN-MAT-10.** Matrícula aberta por "Registrar como aluno" de uma aula experimental guarda de qual experimental veio. Matrícula com o mesmo telefone até 60 dias depois de uma experimental é ligada automaticamente.

### Termo de matrícula

- **RN-MAT-11.** O termo é aceito no portal, pelo aluno adulto ou pelo responsável financeiro do menor, uma vez por aluno. Até o aceite, a gestão vê o selo "Termo pendente" e o portal exige o aceite antes de qualquer outra tela.
- **RN-MAT-12.** O aceite guarda conta, versão, data, hora e IP. O uso de imagem é uma caixa separada e opcional, que pode ser revogada no portal.
- **RN-MAT-13.** Cada modalidade nova pede no portal uma confirmação curta com plano e valor.
- **RN-MAT-14.** Nova versão do termo: o Coordenador escolhe se vale só para novos alunos ou se todos aceitam de novo no próximo acesso.

### Mudanças e saída

- **RN-MAT-15.** Troca de turma vale na hora. Troca de plano vale a partir da próxima cobrança gerada.
- **RN-MAT-16.** Pedidos pelo portal (troca de turma, nova modalidade, troca de plano) nunca são aprovados sozinhos. Ficam no "Precisa de atenção" e expiram em 30 dias sem resposta, com aviso ao aluno. Aprovar abre o fluxo correspondente na gestão já preenchido.
- **RN-MAT-17.** Encerrar uma modalidade pela gestão exige motivo da lista (mudança de cidade, financeiro, horário, lesão, insatisfação, outro) com observação opcional. Para de gerar cobrança. As já geradas continuam devidas, e o Administrador decide manter ou cancelar cada uma, com motivo. Encerrar a modalidade encerra o benefício ativo dela. Enquanto não houver cobrança (Sprint 08), a janela mostra a lista de cobranças em aberto vazia; a decisão por cobrança entra com o painel da cobrança.
- **RN-MAT-18.** "Solicitar encerramento" pelo portal, com motivo: a modalidade fica "Encerramento solicitado" e a gestão tem 7 dias para conversar e encerrar. Sem ação, o sistema encerra com a data do pedido. Nenhuma cobrança vence depois do pedido; as anteriores continuam devidas.
- **RN-MAT-19.** Retorno de ex-aluno: reabre a mesma matrícula, achada por CPF ou e-mail, com histórico e graduação. Primeiro mês cheio; se a unidade cobra taxa de matrícula, ela é cobrada de novo, com isenção possível. Débito antigo aparece para o Administrador decidir. Se os dados foram anonimizados, é cadastro novo.
- **RN-MAT-20.** Transferência de modalidade entre unidades segue RN-ORG-12.

## Requisitos

- **RF-MAT-01.** "Nova matrícula" em etapas: 1) Aluno (busca por CPF; se existir, confirma nome e foto) e responsáveis; 2) Modalidades (unidade, turma principal, outros horários, plano, benefício, graduação de origem); 3) Vencimento e primeira cobrança (dia, taxa de matrícula com isenção quando a unidade cobra, data sugerida da segunda cobrança); 4) Resumo com valores e "Concluir matrícula"; 5) Conclusão com "Registrar pagamento agora" ou "Aluno paga pelo portal".
- **RF-MAT-02.** A etapa de modalidades avisa faixa etária fora da turma e turma acima da capacidade, sem impedir.
- **RF-MAT-03.** Encerrar modalidade (RN-MAT-17), mostrando as cobranças em aberto para decidir.
- **RF-MAT-04.** Pedidos do portal: lista e ações Aprovar e Recusar (com mensagem ao aluno).
- **RF-MAT-05.** Configurações › Termos: editor do termo de matrícula e do uso de imagem, versões e escolha de reaceite.

## Pronto quando

- Concluir uma matrícula cria a cobrança do primeiro mês cheio e a taxa, se houver, numa transação só.
- O portal não mostra nenhuma tela antes do aceite do termo.
- Um pedido do portal nunca altera a matrícula sem ação da gestão.
