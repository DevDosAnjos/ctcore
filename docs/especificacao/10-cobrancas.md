# 10 · Cobranças

Entrega 1 · Fase 10 · Depende de: 07, 09 · Código: COB

## Objetivo

Gerar, acompanhar e receber o que os alunos devem à unidade. Os meios online e o Mercado Pago estão em `11`.

## Dados

- **Cobrança:** unidade, aluno, quem paga (aluno adulto ou responsável financeiro), tipo (Mensalidade, Taxa de matrícula, Avulsa, Parcela, Diferença de reajuste), modalidade da matrícula (quando houver), mês de referência, descrição, vencimento, composição (plano, benefício aplicado, valor final), valor em centavos, situação, cobrança de origem (para parcelas), motivo (cancelamento, renegociação).
- **Pagamento:** cobrança(s), valor, taxa repassada, meio (Pix Mercado Pago, cartão de crédito, cartão de débito, boleto, Pix na chave da unidade, dinheiro, cartão na maquininha, transferência), origem (portal, gestão, débito automático), quem lançou, data e hora, identificador no provedor, comprovante anexado (Pix na chave).
- **Recibo:** pagamento, número sequencial por unidade, código de conferência, PDF.
- **Lembrete:** cobrança, regra (3 dias antes, no dia, 3 depois, 10 depois, 5 dias antes do bloqueio, manual), canal, data.

## Situações da cobrança

| Situação | Quando |
| --- | --- |
| Pendente | Gerada e antes do vencimento |
| Vence hoje | Calculada pela data |
| Em atraso | Calculada: passou do vencimento (mais a tolerância da primeira cobrança) sem pagamento |
| Em conferência | Comprovante de Pix na chave enviado, aguardando o Administrador |
| Paga | Paga até o vencimento |
| Paga com atraso | Paga depois do vencimento |
| Cancelada | Cancelada pela gestão, com motivo |
| Renegociada | Substituída por parcelas ou por nova cobrança |
| Estornada | Pagamento devolvido (ver `11`) |

## Regras

### Geração

- **RN-COB-01.** Uma mensalidade por modalidade ativa da matrícula, recebida pela unidade daquela modalidade, no dia de vencimento do aluno naquela unidade.
- **RN-COB-02.** Geração automática diária: cria as mensalidades que vencem dali a 10 dias. A tela de prévia mostra o que será gerado nos próximos dias para o Administrador conferir e ajustar antes (trocar vencimento, cancelar, aplicar benefício).
- **RN-COB-03.** Valor = plano menos o benefício válido na data da geração, nunca abaixo de zero. A composição fica guardada e não muda se o plano ou o benefício mudarem depois.
- **RN-COB-04.** Não geram mensalidade: modalidade com bolsa de 100%, modalidade bloqueada, encerrada ou com encerramento solicitado.
- **RN-COB-05.** A geração é idempotente: nunca existem duas mensalidades da mesma modalidade da matrícula para o mesmo mês de referência.
- **RN-COB-06.** Pagamento adiantado: o Administrador gera as próximas N mensalidades de uma modalidade. A geração automática pula meses já gerados.
- **RN-COB-07.** Não existem juros, multa nem pagamento parcial.

### Alterações

- **RN-COB-08.** Cobrança paga nunca muda.
- **RN-COB-09.** Renegociação: o Administrador altera valor e vencimento de uma cobrança em aberto, ou divide uma ou mais cobranças em até 6 parcelas mensais. As originais ficam "Renegociada". Motivo obrigatório e auditoria.
- **RN-COB-10.** Cancelar cobrança em aberto: qualquer valor e idade, motivo obrigatório. O relatório financeiro mostra "cancelado no período" por pessoa.
- **RN-COB-11.** Cobrança avulsa (kimono, exame, seminário): aluno, descrição (até 80 caracteres), valor, vencimento. Paga por Pix ou cartão. Não conta para a inativação por atraso.

### Recebimento

- **RN-COB-12.** Pagamento manual guarda meio (dinheiro, Pix na chave, cartão na maquininha, transferência) e quem lançou. Não existe abertura e fechamento de caixa.
- **RN-COB-13.** Pix na chave da unidade: o aluno paga, anexa o comprovante no portal e a cobrança fica "Em conferência". Lembretes e prazo de inativação param. O Administrador confirma (vira pagamento) ou recusa com motivo; recusado, o prazo volta a contar de onde parou.
- **RN-COB-14.** Várias cobranças podem ser pagas de uma vez, num só Pix, boleto ou cartão, desde que sejam da mesma unidade.
- **RN-COB-15.** Pagamento em duplicidade é registrado como tal e vai para o "Precisa de atenção". O Administrador estorna ou usa como crédito na próxima cobrança da mesma modalidade.
- **RN-COB-16.** Todo pagamento gera recibo em PDF: logo, nome da unidade, CNPJ quando informado e endereço; número sequencial da unidade; nome e CPF de quem pagou; aluno e referência ("Jiu-Jitsu · nov/2026" ou a descrição da avulsa); valor, taxa repassada, meio, data e hora; "Este recibo não é nota fiscal."; código de conferência válido em `ctcore.com.br/recibo/{codigo}`. O link do recibo não expira.

### Lembretes

- **RN-COB-17.** Lembretes por e-mail (e push, quando existir): 3 dias antes, no dia, 3 e 10 dias depois do vencimento, configuráveis por unidade (ligar, desligar, canais, horário). Mais um aviso fixo 5 dias antes do bloqueio; se o aluno não abriu os e-mails anteriores, esse aviso também vai por SMS.
- **RN-COB-18.** Lembretes de menor vão para o responsável financeiro.
- **RN-COB-19.** Lembretes param quando a cobrança é paga, cancelada, renegociada ou fica em conferência.
- **RN-COB-20.** SMS: no máximo 3 por aluno por mês, contando todos os tipos (ver `00` §11).
- **RN-COB-21.** O link de pagamento nos e-mails de cobrança não expira: abre a cobrança atual da pessoa e pede login se não houver sessão. Se a cobrança já foi paga, mostra o recibo.

## Requisitos

- **RF-COB-01.** Financeiro › Cobranças: colunas aluno com foto, referência, valor final, vencimento, situação (cor e texto), meio quando paga; filtros rápidos Em atraso, Vence hoje, Em conferência, Pagas no mês; filtros por período, situação, tipo, modalidade e turma; totais do que está filtrado no rodapé.
- **RF-COB-02.** Painel da cobrança: composição, vencimento, histórico de lembretes, pagamentos e recibos; ações Registrar pagamento, Enviar lembrete, Renegociar, Cancelar, Estornar (quando paga), Ver aluno.
- **RF-COB-03.** Prévia da geração: próximas mensalidades por dia de vencimento, total previsto, diferença para o mês anterior, quem não será cobrado e por quê.
- **RF-COB-04.** Registrar pagamento: meio, data, valor (igual ao da cobrança), observação, enviar recibo por e-mail.
- **RF-COB-05.** Em conferência: lista de comprovantes com imagem, aluno e valor; Confirmar ou Recusar.
- **RF-COB-06.** Em atraso: dias de atraso, valor, último lembrete, dias até o bloqueio; seleção múltipla e "Enviar lembrete" (e-mail, e WhatsApp com mensagem pronta por aluno).
- **RF-COB-07.** Nova cobrança avulsa e "Gerar próximas mensalidades" (adiantado).
- **RF-COB-08.** Recebimentos do dia: por meio e por pessoa que lançou.
- **RF-COB-09.** Configurações › Unidade › Lembretes.

## Pronto quando

- Rodar a geração duas vezes no mesmo dia não cria nada repetido.
- Aluno com bolsa de 100% não recebe mensalidade e continua ativo.
- Lembretes param no minuto do pagamento.
- O recibo confere pelo código na página pública, que mostra só unidade, número, referência, valor, data e as iniciais de quem pagou.
