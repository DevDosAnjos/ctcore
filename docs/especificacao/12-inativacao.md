# 12 · Inativação e reativação

Entrega 1 · Fase 12 · Depende de: 09, 10, 11 · Código: INA

## Objetivo

Tirar da operação quem não paga, sem trabalho manual, e devolver na hora quando paga. Não existe "Pausado".

## Situações

### Modalidade da matrícula

| Situação | Como entra | Como sai |
| --- | --- | --- |
| Ativa | Matrícula, reativação | — |
| Encerramento solicitado | Pedido no portal | Gestão encerra em até 7 dias, ou o sistema encerra no 8º dia com a data do pedido |
| Bloqueada por atraso | Mensalidade vencida além do prazo | Volta a Ativa ao quitar |
| Bloqueada pela gestão | Administrador inativa à mão, com motivo | Reativação manual (após carência) ou pagamento |
| Encerrada | Encerramento pela gestão ou automático | Retorno (reabre a matrícula) |

### Aluno (calculada, nunca digitada)

| Situação | Regra | Conta na assinatura |
| --- | --- | --- |
| Ativo | Ao menos uma modalidade Ativa ou com Encerramento solicitado | Sim |
| Inativo | Nenhuma ativa e ao menos uma bloqueada | Não |
| Ex-aluno | Todas encerradas | Não |

## Regras

- **RN-INA-01.** Uma modalidade com cobrança vencida há mais que o prazo da organização (15 a 90 dias, padrão 30) é bloqueada automaticamente. Só a modalidade em atraso é bloqueada. Contam mensalidades, parcelas de renegociação, dívidas importadas e diferenças de reajuste daquela modalidade. 
- **RN-INA-02.** Não contam para o prazo: cobranças avulsas, taxa de matrícula, cobranças em conferência (o prazo pausa e volta de onde parou se o comprovante for recusado) e unidades com o Mercado Pago desconectado (ninguém é bloqueado nelas enquanto durar).
- **RN-INA-03.** Aviso fixo 5 dias antes do bloqueio, por e-mail e, se os e-mails anteriores não foram abertos, por SMS. O "Precisa de atenção" lista os bloqueios dos próximos 5 dias.
- **RN-INA-04.** Modalidade bloqueada sai das turmas daquela modalidade, não recebe graduação, comunicado nem cobrança nova.
- **RN-INA-05.** Reativação automática: quando o aluno quita tudo, isto é, com o pagamento confirmado e nenhuma outra cobrança vencida (avulsas não contam), as modalidades bloqueadas por atraso voltam a Ativa sozinhas e o Administrador é avisado. A próxima cobrança volta ao ciclo normal de vencimento.
- **RN-INA-06.** O Administrador pode: inativar à mão, com motivo; reativar mesmo com débito, caso em que o prazo recomeça do zero a partir da reativação; cancelar cobranças acumuladas, com motivo.
- **RN-INA-07.** Carência: modalidade bloqueada pela gestão só pode ser reativada à mão 2 dias depois. Pagando as cobranças vencidas, reativa na hora.
- **RN-INA-08.** Portal do aluno Inativo: cobranças em aberto com "Pagar", histórico financeiro, recibos, dados da conta e contato da unidade. A carteirinha continua visível, com o selo "Inativo". Agenda, avisos e graduação ficam ocultos. "Inativo" no portal é calculado do que existe: aluno matriculado sem modalidade ativa (RN-POR-08).
- **RN-INA-09.** Ex-aluno sem débito vê o histórico financeiro e os recibos pelo prazo da organização (padrão 12 meses). Depois o acesso do vínculo é desligado, sem apagar dados. Ex-aluno com débito vê como o Inativo.
- **RN-INA-10.** Todas as mudanças de situação vão para a auditoria e geram e-mail ao aluno (bloqueio, reativação, encerramento).
- **RN-INA-11.** A contagem de alunos ativos para a assinatura usa a situação do aluno (ver `16`).

## Requisitos

- **RF-INA-01.** Na ficha, cada modalidade mostra a situação e, se em atraso, "Bloqueia em [data]".
- **RF-INA-02.** Ações Inativar, Reativar e Cancelar cobranças acumuladas, com resumo do efeito e motivo.
- **RF-INA-03.** Tarefa diária de bloqueio, com trava, no fuso de cada unidade, às 6h.

## Pronto quando

- Teste de ponta a ponta: cobrança atrasa, recebe os avisos, bloqueia no dia certo, o aluno paga pelo portal e volta a Ativo sozinho.
- Unidade desconectada não bloqueia ninguém.
- Aluno com a modalidade bloqueada não aparece entre os alunos da turma no Início do Professor nem recebe comunicado.
