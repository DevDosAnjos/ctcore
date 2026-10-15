# 17 · Painel da plataforma e suporte

Entrega 1 · Fase 17 · Depende de: 16 · Código: PNL, SUP

## Objetivo

Área da equipe da plataforma para acompanhar e administrar as organizações, atender o suporte e operar o produto.

## Papéis

| Papel | Pode |
| --- | --- |
| Dono | Tudo: preços, catálogo, modelos, recursos por organização, cortesia, suspender, reativar, cancelar organização, reembolso, ocultar unidade do diretório, gerir a equipe |
| Suporte | Ver organizações, indicadores e chamados; consultar uma organização; alterar dados só com liberação do Coordenador; transferir coordenação mediante comprovação |

Duas etapas obrigatória para os dois.

## Regras

- **RN-PNL-01.** Listas e indicadores mostram só números e dados da organização. Dados pessoais de alunos aparecem apenas dentro do acesso de suporte.
- **RN-PNL-02.** Acesso de suporte: consultar uma organização não precisa de liberação; a tela mostra a faixa "Modo suporte" e tudo vai para a auditoria que o Coordenador vê.
- **RN-PNL-03.** Alterar algo dentro de uma organização exige que o Coordenador libere o acesso de suporte (vale 24 horas) e um motivo por ação. Sem liberação, o suporte só consulta.
- **RN-PNL-04.** Selo "Em risco", com o motivo, quando: a gestão está sem acesso há 14 dias, a fatura está em atraso, o Mercado Pago está desconectado há 7 dias, ou os alunos ativos caíram 20% em 2 ciclos.
- **RN-PNL-05.** Alertas: cadastro novo que repete CNPJ, CPF, telefone ou pagador de quem já teve reembolso; muitas inativações e reativações em sequência; divergências da conciliação; falhas de webhook.
- **RN-PNL-06.** Preços mudam com data de início e aviso de 30 dias às organizações.
- **RN-PNL-07.** Recursos novos nascem desligados e são ligados por organização. Um recurso com problema é desligado sem novo deploy.
- **RN-PNL-08.** O Dono pode ocultar uma unidade do diretório, com motivo e e-mail ao Coordenador. Cada resultado do diretório tem um link discreto "Denunciar", que chega aqui.
- **RN-PNL-09.** Avisos da plataforma: faixa no topo da gestão para os perfis escolhidos e, nos importantes, e-mail ao Coordenador. Alunos não recebem. Notas de versão vão para "Novidades".

### Suporte

- **RN-SUP-01.** Canais: chamado dentro do sistema (registra organização, pessoa e tela de onde foi aberto) e WhatsApp comercial para urgências.
- **RN-SUP-02.** Resposta em até 1 dia útil, de segunda a sexta, das 9h às 18h. Sistema fora do ar é atendido a qualquer hora.
- **RN-SUP-03.** Antes do login, as telas mostram o contato do suporte da plataforma; depois do login, o contato da unidade (para alunos) ou o chamado (para a equipe da organização).

## Requisitos

- **RF-PNL-01.** Início do painel com blocos escolhidos pelo Dono: receita e perda de clientes (receita mensal prevista, cadastros e cancelamentos), funil de vendas (aberturas da demonstração, cliques em "Criar minha academia", cadastros, pagos), uso do produto e saúde técnica (erros, fila de e-mails, webhooks).
- **RF-PNL-02.** Lista de organizações: nome, Coordenador, cidade, unidades, pico de alunos ativos, valor da fatura, situação da assinatura, Mercado Pago conectado, último acesso da gestão, data de entrada, selo "Em risco". Filtros: em atraso, cortesia, sem acesso há 14 dias, configuração incompleta, em risco.
- **RF-PNL-03.** Detalhe da organização: dados, unidades, Coordenador e Administradores, assinatura e faturas, primeiros passos concluídos, recursos ligados, chamados, auditoria de acessos de suporte.
- **RF-PNL-04.** Ações: conceder cortesia (dias), desconto fixo, suspender, reativar, cancelar, reenviar convite do Coordenador, transferir coordenação (com comprovação), registrar reembolso, ligar e desligar recursos, entrar em modo suporte.
- **RF-PNL-05.** Catálogo de modalidades e graduação padrão; pedidos de modalidade nova.
- **RF-PNL-06.** Preços da assinatura.
- **RF-PNL-07.** Modelos: Termos de uso, contrato de tratamento de dados, política de privacidade, termo de matrícula, termo de uso de imagem, textos padrão dos e-mails. Cada mudança gera nova versão.
- **RF-PNL-08.** Avisos da plataforma e notas de versão.
- **RF-PNL-09.** Diretório: denúncias e unidades ocultas.
- **RF-PNL-10.** Chamados: lista com organização, assunto, situação, prazo; responder (por e-mail e dentro do sistema).
- **RF-PNL-11.** Equipe da plataforma: convidar e desativar (Dono).

## Pronto quando

- O Suporte não altera nada numa organização sem liberação ativa, também pela API.
- Todo acesso de suporte aparece na auditoria da organização.
- Nenhuma lista do painel mostra nome de aluno.
