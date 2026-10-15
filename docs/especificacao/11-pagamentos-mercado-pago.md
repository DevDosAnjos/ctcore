# 11 · Pagamentos e Mercado Pago

Entrega 1 com o simulador de pagamento; Mercado Pago real na Entrega 2 · Fase 11 · Depende de: 10 · Código: PAG

## Objetivo

Receber online, direto na conta do Mercado Pago de cada unidade, com baixa automática e conferência contra o provedor.

## Dados

- **Conexão de pagamento:** unidade, provedor (Mercado Pago), identificador da conta, tokens (criptografados), conectado em, conectado por, situação (Conectada, Desconectada, Token expirado).
- **Taxas da unidade:** percentual e/ou valor fixo por meio (Pix, crédito, débito, boleto, débito automático).
- **Tentativa de pagamento:** cobrança(s), meio, valor com taxa, identificador no provedor, situação (Criada, Aprovada, Recusada, Expirada, Estornada, Contestada), resposta do provedor.
- **Débito automático:** aluno ou responsável, unidade, cartão (código do provedor e 4 últimos dígitos), ativo, falhas seguidas.
- **Aviso do provedor (webhook):** identificador, conteúdo, recebido em, processado em, resultado.

## Regras

### Conexão

- **RN-PAG-01.** Cada unidade conecta a própria conta pelo botão "Conectar", por autorização no Mercado Pago (OAuth). Ninguém copia chave. Não existe split: a unidade recebe 100%.
- **RN-PAG-02.** O token fica criptografado e nunca volta à tela. A tela mostra "Conectado em [data] por [pessoa]" e os botões "Reconectar" e "Desconectar" (que pede senha).
- **RN-PAG-03.** O código de pagamento fica atrás de uma interface de provedor. Trocar ou somar provedor não muda as regras de `10`.

### Meios e taxas

- **RN-PAG-04.** Meios online: Pix, cartão de crédito, cartão de débito e boleto. O boleto só é gerado quando o aluno pede.
- **RN-PAG-05.** A unidade informa a taxa de cada meio. O portal mostra o total de cada meio antes de o aluno escolher ("Pix: R$ 180,90 · Cartão: R$ 186,30"). A taxa é arredondada para cima no centavo e aparece no recibo.
- **RN-PAG-06.** Pix do Mercado Pago vale 30 minutos; um clique gera outro. A confirmação aparece na tela sem recarregar.
- **RN-PAG-07.** Boleto vence junto com a cobrança; se ela já venceu, 3 dias úteis à frente. O prazo de inativação não muda por causa do boleto. O portal avisa que a confirmação leva até 3 dias úteis.
- **RN-PAG-08.** Dados de cartão vão direto do navegador ao Mercado Pago, pelo formulário seguro dele. O sistema guarda só o código do cartão e os 4 últimos dígitos.
- **RN-PAG-09.** Débito automático no cartão é opcional para o aluno ou responsável, com a taxa repassada mostrada antes de ativar. Cobra no vencimento. Se falhar, avisa, tenta de novo em 3 e em 6 dias e, persistindo, desliga o débito automático e avisa.

### Confirmação e conferência

- **RN-PAG-10.** A baixa só acontece com a confirmação do provedor (webhook ou consulta), nunca pelo retorno do navegador.
- **RN-PAG-11.** O mesmo aviso ou a mesma confirmação recebidos duas vezes não geram dois pagamentos. Cobrança paga não pode ser paga de novo; se o provedor confirmar um segundo pagamento, vira "Pagamento em duplicidade" (RN-COB-15).
- **RN-PAG-12.** Conciliação: a cada hora o sistema consulta no Mercado Pago as cobranças pendentes dos últimos 7 dias; de madrugada confere todos os pagamentos do dia anterior. Divergências viram alerta no painel da plataforma.
- **RN-PAG-13.** Cada pagamento e cada tentativa recusada vão para a auditoria e geram notificação na gestão.

### Estorno e contestação

- **RN-PAG-14.** Estorno pelo sistema, total ou parcial, no Mercado Pago. Motivo obrigatório e auditoria. A cobrança fica "Estornada" (no total) e o recibo é cancelado. No parcial, a cobrança continua "Paga", o recibo original é cancelado e sai um novo com o valor líquido, o mesmo número com sufixo (ex.: 0153-A) e a observação do estorno.
- **RN-PAG-15.** Pagamento em Pix na chave, dinheiro, maquininha ou transferência é devolvido por fora; o Administrador marca "Estornado" com motivo.
- **RN-PAG-16.** Contestação no cartão chega pelo webhook e vira alerta. Se o banco der razão ao aluno, a cobrança volta a "Em aberto" (Pendente ou Em atraso conforme a data).

### Mercado Pago indisponível ou desconectado

- **RN-PAG-17.** Com a unidade desconectada, token expirado ou o provedor fora do ar: as cobranças continuam sendo geradas; o portal esconde cartão e boleto e mostra só a chave Pix da unidade, se houver, com "Envie o comprovante"; débitos automáticos são adiados; Coordenador e Administrador recebem alerta urgente por e-mail e uma faixa vermelha com "Reconectar" até resolver.
- **RN-PAG-18.** Enquanto a unidade estiver desconectada, ninguém é inativado por atraso nela.

## Requisitos

- **RF-PAG-01.** Configurações › Unidade › Pagamentos: conexão, taxas por meio com prévia do total para um valor de exemplo, chave Pix.
- **RF-PAG-02.** Janela de pagamento no portal: cobrança(s) escolhida(s), total por meio, escolha do meio, quem processa ("Pagamento processado pelo Mercado Pago").
- **RF-PAG-03.** Pix: QR Code, copia e cola, contador de validade, "Gerar novo".
- **RF-PAG-04.** Cartão: formulário seguro do Mercado Pago, resposta na hora. Recusado: "Nada foi cobrado. A mensalidade continua em aberto." com "Tentar outro cartão", "Pagar com Pix" e "Pagar depois".
- **RF-PAG-05.** Boleto: gerar, copiar linha digitável, baixar PDF, enviar por e-mail.
- **RF-PAG-06.** Confirmado: valor, meio, data e hora, referência, recibo para baixar; recibo por e-mail para quem pagou.
- **RF-PAG-07.** Débito automático no portal: ativar com cartão, ver cartão (4 últimos dígitos), trocar, desativar.
- **RF-PAG-08.** Estornar (total ou parcial) no painel da cobrança.

## Pronto quando

- Em sandbox, um Pix pago dá baixa sem ninguém tocar na gestão, e um webhook repetido não cria segundo pagamento.
- Um webhook perdido é recuperado pela conciliação da hora seguinte.
- Com a unidade desconectada, o portal mostra só a chave Pix e ninguém é inativado nela.
- Nenhum número de cartão passa pelo servidor (conferido nos logs e nas requisições).
