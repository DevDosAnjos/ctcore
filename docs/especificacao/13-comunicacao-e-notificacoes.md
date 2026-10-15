# 13 · Comunicação e notificações

Entrega 1 · Fase 13 · Depende de: 06, 08, 10 · Código: COM, NOT

## Objetivo

Avisar alunos e equipe do que importa, pelos canais certos, sem o aluno responder e sem envio automático pelo WhatsApp.

## Dados

- **Comunicado:** organização, unidade (ou todas), destino (turma, modalidade, unidade, organização), tipo, título, texto, imagem (opcional), canais, fixado até (opcional), agendado para (opcional), autor, enviado em, destinatários, lidos.
- **Leitura:** comunicado, conta, aluno, aberto em.
- **E-mail enviado:** destinatário, modelo, assunto, situação (Enviado, Entregue, Aberto, Voltou, Falhou), tentativas.
- **Modelo de texto:** chave, texto da plataforma, texto da organização (quando editável), campos automáticos.
- **Notificação da equipe:** conta, tipo, título, detalhe, link, lida, data.
- **Preferências:** conta, e-mail de avisos da academia (ligado), resumo diário da equipe (ligado), "receber tudo" (Coordenador, desligado), push (quando existir).

## Regras

### Comunicados

- **RN-COM-01.** Destinos: o Professor envia para as suas turmas (com a permissão "enviar comunicado"); o Administrador para turma, modalidade ou a unidade; o Coordenador também para todas as unidades de uma vez.
- **RN-COM-02.** Recebem os alunos com a modalidade Ativa ou com Encerramento solicitado que têm a turma como principal ou como outro horário. Para menores, os responsáveis.
- **RN-COM-03.** Canais: portal sempre; e-mail para quem não desligou os avisos da academia; push quando existir; WhatsApp abre o grupo da turma (se houver link) ou a conversa com a mensagem pronta, e a pessoa envia.
- **RN-COM-04.** Tipos com texto inicial pronto e editável: Aula cancelada, Troca de professor, Alteração de horário, Dia sem aula, Aviso geral.
- **RN-COM-05.** Título, texto com negrito, listas e links (até 2.000 caracteres) e uma imagem opcional. Pode ficar fixado no topo do portal até uma data.
- **RN-COM-06.** Comunicado pode ser agendado. Agendado pode ser editado ou cancelado até sair. Enviado não é editado nem apagado.
- **RN-COM-07.** Limite de 20 comunicados por dia por unidade.
- **RN-COM-08.** O aluno não responde. A gestão vê só quantos leram. Abrir o aviso marca como lido; "Marcar todos como lidos" limpa a lista, mas não conta como leitura. No menor, conta a leitura do responsável.
- **RN-COM-09.** Antes de enviar, a tela mostra para quantas pessoas e por quais canais o aviso vai.

### E-mails ao aluno e responsável

- **RN-COM-10.** Remetente: nome da unidade, no domínio da plataforma ("Academia Leão · Tijuca <avisos@ctcore.com.br>"). Com uma unidade só, é o nome da academia. Responder vai para o e-mail de contato da unidade.
- **RN-COM-11.** E-mails automáticos:
  - Pagamento: cobrança gerada, lembretes, pagamento confirmado com recibo, pagamento recusado, débito automático falhou, comprovante recusado.
  - Matrícula e situação: boas-vindas e convite, termo pendente, bloqueio, reativação, encerramento, resposta a pedidos.
  - Aulas: cancelamento, troca de professor, mudança de horário, dia sem aula.
  - Graduação: nova graduação (quando o aviso não foi desmarcado).
  - Conta: senha alterada, e-mail alterado, novo acesso, reajuste de plano.
- **RN-COM-12.** A organização edita lembretes, comunicados e boas-vindas, com campos automáticos (`{nome}`, `{valor}`, `{vencimento}`, `{unidade}`, `{link_pagamento}`). E-mails de segurança e de conta não são editáveis.
- **RN-COM-13.** O aluno só pode desligar o e-mail dos comunicados da academia, que continuam no portal. Cobrança, lembretes, recibo, aviso de bloqueio, aulas canceladas e e-mails de conta e segurança sempre chegam.
- **RN-COM-14.** E-mail com falha é tentado de novo; e-mail que volta marca o endereço "Com problema" (RN-ALU-10).

### Notificações da equipe

- **RN-NOT-01.** Sino na gestão com contador de não lidas, mais um resumo diário por e-mail com o "Precisa de atenção", que cada pessoa pode desligar.
- **RN-NOT-02.** E-mail na hora só para o urgente: pedido de encerramento, Mercado Pago desconectado e assinatura em atraso.
- **RN-NOT-03.** Quem recebe: os Administradores da unidade. O Coordenador recebe o que é da organização e pode ligar "receber tudo". O Professor recebe só o que é das suas turmas, conforme as suas permissões, e nada financeiro.
- **RN-NOT-04.** Geram notificação: pagamento recebido, pagamento recusado, comprovante para conferir, pedido do portal, pedido de aula experimental, autocadastro, benefício alto (Coordenador), aluno fez 18 anos, convite não aceito em 7 dias, turma sem professor ativo, e-mail com problema, pedido de exclusão de dados.

## Requisitos

- **RF-COM-01.** Comunicação › Comunicados: lista com data, destino, título, tipo, autor, situação (Agendado, Enviado), destinatários e lidos; filtros por período, turma e tipo.
- **RF-COM-02.** Novo comunicado: destino, tipo, título, texto, imagem, canais, fixar até, enviar agora ou agendar, prévia de destinatários. Abre já preenchido a partir de Turmas, Agenda e Dias sem aula.
- **RF-COM-03.** Configurações › Textos: modelos editáveis com prévia e "Voltar ao texto da plataforma".
- **RF-NOT-01.** Painel do sino: grupos Hoje e Esta semana, filtros Todas e Não lidas, cada notificação com a ação que resolve, "Marcar todas como lidas", estado "Tudo lido".

## Pronto quando

- Um comunicado de teste chega ao portal e ao e-mail; o responsável recebe pelo filho menor.
- O Professor não consegue escolher turma de outro professor nem a unidade inteira.
- Aluno com modalidade bloqueada não recebe o comunicado da turma.
