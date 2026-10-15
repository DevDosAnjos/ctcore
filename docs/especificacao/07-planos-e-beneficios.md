# 07 · Planos e benefícios

Entrega 1 · Fase 7 · Depende de: 01, 04 · Código: PLA, BEN

## Objetivo

Definir quanto cada modalidade custa em cada unidade e os benefícios individuais que reduzem esse valor.

## Dados

- **Plano:** unidade, modalidade, nome, valor mensal (em centavos), frequência semanal informativa (2x, 3x, livre), vale em outras unidades (quando a organização usa "Depende do plano"), situação (Ativo, Desativado), histórico de valores com data de início.
- **Benefício:** modalidade da matrícula, tipo (Bolsa em percentual de 1 a 100, ou Desconto em reais), valor, motivo, decidido por, lançado por, início, validade (sem prazo ou data), situação (Ativo, Vencido, Encerrado).

## Regras

### Planos

- **RN-PLA-01.** Um plano por modalidade da matrícula. Só mensal. Não existe plano combo de várias modalidades.
- **RN-PLA-02.** A frequência semanal é só informativa. Nada controla quantas aulas o aluno faz.
- **RN-PLA-03.** Todo valor é guardado em centavos inteiros.
- **RN-PLA-04.** Troca de plano e reajuste valem a partir da próxima cobrança gerada. O reajuste avisa por e-mail os alunos afetados, com o valor novo e a partir de quando vale.
- **RN-PLA-05.** Cobrança paga nunca muda. Se houver cobranças já geradas e ainda não pagas (pagamento adiantado ou geração antecipada), o Administrador escolhe: mantê-las no valor antigo (padrão) ou ajustá-las. Para cobranças já pagas de meses futuros, ele escolhe se cobra a diferença numa cobrança separada "Diferença de reajuste, referência [mês]", com aviso ao aluno. O padrão é não cobrar, e a escolha vai para a auditoria.
- **RN-PLA-06.** Plano com alunos ou histórico não é excluído, só desativado. Plano desativado some das escolhas e continua nas matrículas que já o usam até troca.

### Benefícios

- **RN-BEN-01.** No máximo um benefício ativo por modalidade da matrícula. Para dar outro, o atual é encerrado. Não existe regra de combinação.
- **RN-BEN-02.** Bolsa em percentual livre de 1 a 100% ou desconto em reais. Não existe desconto por faixa nem por pontualidade.
- **RN-BEN-03.** Todo benefício guarda motivo, quem decidiu e quem lançou, e validade.
- **RN-BEN-04.** O benefício vale a partir da próxima cobrança gerada. Cobrança já gerada não muda. Benefício vencido deixa de ser aplicado.
- **RN-BEN-05.** Com bolsa de 100%, a modalidade não gera mensalidade, mas o aluno segue Ativo e conta na assinatura.
- **RN-BEN-06.** Benefício acima de 50% (ou desconto acima de metade do plano) notifica o Coordenador e aparece no filtro "Benefícios altos". Não precisa de aprovação.
- **RN-BEN-07.** Desconto em reais nunca deixa a mensalidade negativa: o valor mínimo é zero.
- **RN-BEN-08.** Desconto de professor é um benefício comum. Se já houver bolsa, o Administrador escolhe qual fica.
- **RN-BEN-09.** Desconto de família (irmãos) também é o benefício comum, dado em cada matrícula. Não existe cobrança por família.

## Requisitos

- **RF-PLA-01.** Financeiro › Planos e benefícios: cartões Planos e Benefícios, cada um com resumo.
- **RF-PLA-02.** Planos: tabela com modalidade, nome, valor, frequência, alunos usando, situação; filtro por modalidade.
- **RF-PLA-03.** Janela de plano: modalidade, nome, valor, frequência, vale em outras unidades. Mudar o valor mostra quantos alunos serão afetados, a partir de quando e as escolhas da RN-PLA-05.
- **RF-BEN-01.** Benefícios: tabela com aluno, modalidade, tipo, valor, motivo, decidido por, validade e situação; filtros por tipo, modalidade, situação e "Benefícios altos".
- **RF-BEN-02.** Conceder benefício: aluno, modalidade, tipo, valor, motivo, decidido por, validade. Antes de confirmar, mostra plano, benefício, nova mensalidade e a partir de qual mês vale. Se já houver um, avisa que ele será encerrado.
- **RF-BEN-03.** Encerrar benefício, com motivo.

## Pronto quando

- Mudar um plano não altera nenhuma cobrança já gerada sem a escolha explícita do Administrador.
- Não existe tela nem API que aceite dois benefícios ativos na mesma modalidade da matrícula.
- O Professor não vê planos nem benefícios.
