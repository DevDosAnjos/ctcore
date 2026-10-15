# 16 · Assinatura da plataforma

Entrega 1 · Fase 16 · Depende de: 01, 12 · Código: ASS

## Objetivo

Cobrar a organização todo mês pelo maior número de alunos ativos do ciclo, somando todas as unidades. É a única receita da plataforma.

## Preço

| Maior número de alunos ativos no ciclo | Assinatura mensal |
| --- | --- |
| Até 50 | R$ 120 |
| De 51 a 100 | R$ 160 |
| De 101 a 150 | R$ 200 |
| Acima de 150 | Mais R$ 40 a cada 50 alunos (151–200: R$ 240; 201–250: R$ 280…) |

Unidades e usuários não mudam o preço. Não existem plano escolhido, upgrade, downgrade, cupons nem plano anual.

## Situações

| Situação | Quando | O que acontece |
| --- | --- | --- |
| Aguardando pagamento | Cadastro feito, primeiro pagamento pendente | Lembretes no 1º e no 5º dia; apagada no 7º dia sem pagamento |
| Ativa | Pagamento em dia | Tudo funciona |
| Cortesia | Concedida pelo painel | Tudo funciona; adia a próxima fatura |
| Em atraso | Do vencimento até 7 dias | Aviso na gestão e por e-mail; tudo funciona |
| Somente leitura | De 8 a 30 dias | A gestão consulta e exporta, mas não cadastra nem altera; cobranças dos alunos e o portal seguem funcionando |
| Suspensa | Após 30 dias, por até 90 | Só a tela Assinatura e a exportação; portal indisponível |
| Cancelamento agendado | Coordenador cancelou | Tudo funciona até o fim do ciclo pago |
| Cancelada | Fim do ciclo após cancelar, ou 90 dias suspensa | Exportação por 30 dias |
| Encerrada | 30 dias após Cancelada | Dados apagados |

O Coordenador é avisado antes de cada mudança de situação.

## Regras

- **RN-ASS-01.** Aluno ativo é o aluno com situação Ativo (`12`). Cada aluno conta uma vez na organização, mesmo com várias modalidades ou unidades. Bolsista conta; inativo, ex-aluno e aula experimental não.
- **RN-ASS-02.** Vale o pico do ciclo: o maior número de alunos ativos registrado em qualquer dia do ciclo mensal. O número é gravado todo dia.
- **RN-ASS-03.** Nada trava por passar de faixa. Se o pico do ciclo passou da faixa paga, a fatura seguinte vem pela nova faixa mais a diferença do ciclo que passou. O Coordenador vê o aviso na tela Assinatura assim que o pico passa da faixa.
- **RN-ASS-04.** Se o pico do ciclo coube numa faixa menor, a fatura seguinte baixa sozinha.
- **RN-ASS-05.** Primeira fatura: R$ 120 por padrão; se no cadastro a organização informar mais alunos, o sistema sugere a faixa que os cobre.
- **RN-ASS-06.** Pagamento pela conta da plataforma no Mercado Pago: cartão com renovação automática, ou Pix e boleto avulsos a cada ciclo.
- **RN-ASS-07.** Avisos 7 dias e 1 dia antes do vencimento, na gestão e por e-mail, com valor, data e caminho para pagar. Não são enviados se a fatura já está paga.
- **RN-ASS-08.** Pagar a fatura devolve o acesso completo na hora, sem ação da equipe.
- **RN-ASS-09.** Só o Coordenador vê e paga faturas. Os Administradores veem uma faixa de aviso quando há atraso, somente leitura ou suspensão.
- **RN-ASS-10.** Cancelamento vale até o fim do ciclo pago, sem devolução proporcional. Confirmação digitada e senha.
- **RN-ASS-11.** Sem teste gratuito. Reembolso integral se pedido em até 7 dias do primeiro pagamento, uma vez por CNPJ ou CPF, informado só nos Termos de uso (não é argumento de venda). O estorno é feito pela equipe no Mercado Pago e registrado no painel.
- **RN-ASS-12.** Conta nova nasce vazia. Nenhum dado é transferido de uma organização anterior.
- **RN-ASS-13.** Cortesia adia a próxima fatura pelo número de dias concedido, com aviso 7 e 1 dia antes do fim.
- **RN-ASS-14.** Reajuste de preço com aviso de 30 dias. Mudança dos Termos pede novo aceite do Coordenador no próximo login.
- **RN-ASS-15.** Proteção contra burla: vale o pico do ciclo; aluno inativo fica fora da operação; carência de 2 dias na reativação manual; o painel alerta padrões estranhos (muitas inativações e reativações em sequência).
- **RN-ASS-16.** Nenhuma situação apaga dados antes do prazo de saída.
- **RN-ASS-17.** Mudanças de situação vão para a auditoria e aparecem no painel da plataforma.
- **RN-ASS-18.** A academia piloto recebe cortesia até o lançamento e, depois, desconto fixo registrado no painel (exemplo: 50% por 12 meses).
- **RN-ASS-19.** A primeira cobrança real só acontece depois de a empresa ter CNPJ.

## Requisitos

- **RF-ASS-01.** Configurações › Assinatura (Coordenador): situação, alunos ativos hoje, pico do ciclo, faixa e valor previsto, data da próxima fatura, forma de pagamento (trocar cartão), histórico de faturas com recibo.
- **RF-ASS-02.** Pagar fatura em aberto (cartão, Pix, boleto).
- **RF-ASS-03.** Cancelar assinatura, mostrando até quando funciona e o prazo de exportação.
- **RF-ASS-04.** Faixa no topo da gestão para Coordenador e Administradores conforme a situação.
- **RF-ASS-05.** Em somente leitura, todos os botões de criar e alterar ficam desabilitados com a explicação, e a API recusa escrita.

## Pronto quando

- O pico do ciclo bate com a contagem diária gravada.
- Em somente leitura, nenhuma escrita passa pela API da gestão, mas o portal e os pagamentos dos alunos funcionam.
- O pagamento de uma fatura em atraso devolve o acesso sem intervenção.
