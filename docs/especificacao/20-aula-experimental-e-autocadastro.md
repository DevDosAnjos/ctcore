# 20 · Aula experimental, autocadastro e links públicos

Entrega 1 · Fase 20 · Depende de: 06, 09, 13 · Código: AEX, AUT

## Objetivo

Trazer gente nova para a unidade: pedido de aula experimental e autocadastro por link (Instagram, QR na recepção, diretório), sempre com a unidade decidindo.

## Links públicos da unidade

| Link | Endereço | Liga/desliga |
| --- | --- | --- |
| Aula experimental | `ctcore.com.br/aula/{unidade}` | Configurações › Unidade |
| Autocadastro | `ctcore.com.br/matricula/{unidade}` | Configurações › Unidade |

São páginas simples, com logo, cor e nome da organização e da unidade, endereço e contato. Ao serem compartilhadas, mostram logo e nome (prévia de link). Desligado, o link mostra "Esta unidade não está recebendo pedidos por aqui" com o contato.

## Aula experimental

### Dados

Pessoa (nome, telefone/WhatsApp), para quem (para mim, para filho ou filha com nome e idade), modalidade (ou "Ainda não sei"), faixa etária, períodos preferidos (manhã, tarde, noite), mensagem, origem (link, diretório, gestão), aula agendada, situação, matrícula gerada, aceite da política de privacidade.

### Situações

| Situação | Regra |
| --- | --- |
| Pedido recebido | Chegou pelo link ou diretório. Expira em 15 dias sem agendamento e vira "Sem retorno" |
| Agendada | Marcada numa aula que existe na agenda |
| Confirmada | Opcional, quando a unidade confirmou com a pessoa |
| Realizada | Sozinha, quando a aula termina sem cancelamento |
| Não compareceu | Marcada pelo Administrador ou Professor |
| Cancelada | Pela gestão, com motivo |
| Matriculado | Virou matrícula |

### Regras

- **RN-AEX-01.** Sempre gratuita.
- **RN-AEX-02.** Limite por pessoa e por unidade: padrão 2, ajustável de 1 a 3, contado pelo telefone e somando as modalidades. O pedido acima do limite entra com o selo "Limite atingido" e só é agendado se a gestão usar "Liberar mais uma".
- **RN-AEX-03.** O pedido não marca a aula. A unidade confirma com a pessoa pelo WhatsApp (mensagem pronta) e agenda na gestão. O sistema não envia mensagem automática ao visitante.
- **RN-AEX-04.** Obrigatórios: nome e telefone; para criança, nome e idade dela. Não pede e-mail, documento nem endereço. Pedido de criança é feito por um adulto.
- **RN-AEX-05.** Quem faz aula experimental não é aluno: não tem cobrança, plano nem portal, e não conta na assinatura.
- **RN-AEX-06.** "Registrar como aluno" abre a nova matrícula preenchida e guarda a origem. Matrícula com o mesmo telefone até 60 dias depois é ligada automaticamente.
- **RN-AEX-07.** Aula cancelada com experimental marcada exige reagendar ou cancelar a experimental (RN-AGE-06).
- **RN-AEX-08.** O Professor vê as experimentais das suas aulas com a permissão "ver aulas experimentais" e pode marcar "Não compareceu".
- **RN-AEX-09.** Experimental não convertida é anonimizada no prazo da organização (padrão 6 meses).
- **RN-AEX-10.** Formulário com limite por IP, desafio invisível e aceite da política de privacidade.

### Requisitos

- **RF-AEX-01.** Página pública do pedido: três passos explicados (você envia, a unidade chama no WhatsApp, é só chegar), formulário, botão de WhatsApp para quem prefere falar direto, "Pedido enviado" com "Enviar outro pedido". Se falhar, mantém o preenchido e oferece o WhatsApp.
- **RF-AEX-02.** Operação › Aulas experimentais: abas Pedidos, Hoje, Próximas, Realizadas, Canceladas e Sem retorno, com contagem; filtros por nome, modalidade e período; pedidos novos destacados com a mensagem.
- **RF-AEX-03.** Agendar: turma e uma das próximas aulas dela, observação, mensagem de confirmação pronta no WhatsApp.
- **RF-AEX-04.** Painel: quando, turma, professor, telefone, histórico; ações WhatsApp, Reagendar, Confirmar, Não compareceu, Cancelar, Registrar como aluno, Liberar mais uma.
- **RF-AEX-05.** A experimental aparece na aula da agenda e no Início.

## Autocadastro

### Regras

- **RN-AUT-01.** Cada unidade tem link e QR code de autocadastro, e o diretório leva a ele em "Quero me matricular".
- **RN-AUT-02.** Adulto preenche os próprios dados obrigatórios (`08`), escolhe modalidade e, se quiser, a turma de interesse, e cria a senha. Para criança, o responsável cria a própria conta e adiciona um ou mais filhos na mesma ficha.
- **RN-AUT-03.** A ficha fica "Aguardando aprovação" e entra no "Precisa de atenção". Nada é cobrado antes da aprovação.
- **RN-AUT-04.** O Administrador escolhe turma, plano, benefício e vencimento e aprova; nascem a matrícula e a primeira cobrança, como numa matrícula feita pela gestão (`09`). O termo de matrícula é aceito no primeiro acesso ao portal, pelo adulto ou pelo responsável.
- **RN-AUT-05.** Recusar exige uma mensagem, enviada por e-mail. Ficha não aprovada em 30 dias expira e a pessoa é avisada.
- **RN-AUT-06.** CPF já cadastrado na organização não cria outra ficha: o pedido vira uma nova modalidade para a ficha existente, para aprovação.
- **RN-AUT-07.** A origem ("link da unidade" ou "diretório") fica na matrícula e aparece nos relatórios.

### Requisitos

- **RF-AUT-01.** Página pública de autocadastro em etapas: dados, modalidade e turma de interesse, senha, aceite da política de privacidade, "Pedido enviado".
- **RF-AUT-02.** Na gestão, lista "Aguardando aprovação" com Aprovar (abre a matrícula preenchida) e Recusar.
- **RF-AUT-03.** Configurações › Unidade › Links: copiar link, baixar QR code (PNG e PDF para imprimir), ligar e desligar.

## Pronto quando

- O pedido de uma pessoa que já fez 2 experimentais na unidade chega com "Limite atingido" e não pode ser agendado sem "Liberar mais uma".
- Uma experimental vira matrícula sem redigitar dados.
- Um autocadastro nunca gera cobrança sem aprovação.
