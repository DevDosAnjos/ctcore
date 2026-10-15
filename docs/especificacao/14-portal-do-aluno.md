# 14 · Portal do aluno

Entrega 1 (push real na Entrega 2) · Fase 14 · Depende de: 02, 09 a 13 · Código: POR

## Objetivo

Dar ao aluno e ao responsável, no celular, o que eles precisam: pagar, ver as aulas, os avisos, a faixa e a carteirinha.

## Quem usa

Aluno adulto e Responsável. A área abre em `app.ctcore.com.br/portal`, com o logo e a cor da organização.

## Regras

### Base

- **RN-POR-01.** A conta só vê os alunos vinculados a ela. O servidor confere em toda consulta.
- **RN-POR-02.** O portal usa a mesma API e as mesmas regras da gestão. Nada é recalculado no navegador.
- **RN-POR-03.** Antes do aceite do termo de matrícula (`09`), nenhuma outra tela abre.
- **RN-POR-04.** Aluno com modalidades em várias unidades vê uma visão só, com etiqueta de modalidade e unidade em cada item e filtro. A graduação aparece por modalidade.
- **RN-POR-05.** Responsável: faixa no topo com um cartão por filho (e "Você", se também treina) e o total da família em aberto; abaixo, tudo do cartão escolhido. O Financeiro tem a visão "Toda a família" para pagar junto o que é da mesma unidade.
- **RN-POR-06.** O aluno vê o contato da unidade (telefone, WhatsApp, e-mail), nunca o do professor.
- **RN-POR-07.** O aluno não altera turma, plano, benefício, vencimento nem graduação; para isso faz um pedido (`09`).
- **RN-POR-08.** Inativo e ex-aluno veem o portal reduzido (RN-INA-08 e RN-INA-09).
- **RN-POR-09.** Sem presença, sem frequência, sem progresso de graduação.

### Instalação e uso sem internet

- **RN-POR-10.** O portal é instalável como PWA ("Adicionar à tela inicial"), sem loja. Só o app (`app.`) é instalado.
- **RN-POR-11.** Sem internet, mostra a última versão do Início, da agenda e da carteirinha com o aviso "Sem conexão". Pagar e enviar pedidos exigem conexão.
- **RN-POR-12.** Push opcional, pedido ao instalar: cobrança vencendo, pagamento confirmado, aviso novo e aula cancelada. Quem não aceita segue com e-mail.

## Requisitos

- **RF-POR-01.** Barra com logo e nome da organização, abas Início, Agenda, Financeiro, Avisos e Minhas informações, sino com não lidos, nome e papel de quem está logado. No celular, abas embaixo.
- **RF-POR-02.** **Início**, nesta ordem: 1) cartão de cobrança vencida ou vencendo com "Pagar" (a vencida aparece antes), ou "Tudo em dia" com a data do próximo vencimento; 2) próximas 3 aulas, com alterações e cancelamentos destacados; 3) avisos não lidos e fixados; 4) faixa atual por modalidade, com o selo "Nova" quando recente.
- **RF-POR-03.** **Carteirinha**, a partir do Início e de Minhas informações: foto, nome, organização, modalidades, faixa, selo "Ativo" ou "Inativo" com a data do dia, logo e cor da organização. Sem QR code. Funciona sem internet mostrando "Atualizada em [data e hora]".
- **RF-POR-04.** **Agenda**: semana em grade no computador e lista do dia no celular; distingue turma principal, outros horários, aula avulsa, aula alterada (com resumo e "Ver aviso"), aula cancelada ("Cancelada"), dia sem aula ("Sem aula" e o motivo) e aula que já passou; alternar "Só minha turma" e "Todos os meus horários"; trocar de semana. Só consulta.
- **RF-POR-05.** **Financeiro**: cobranças em aberto (vencidas primeiro) com composição e "Pagar"; seleção de várias da mesma unidade para pagar junto; histórico por ano com referência, vencimento, valor, data do pagamento, meio e situação; recibos para baixar; enviar comprovante de Pix na chave; débito automático.
- **RF-POR-06.** **Avisos**: lista com título, data, destino e autor; não lidos destacados; fixados no topo; filtros Todos, Não lidos, Por modalidade; "Marcar todos como lidos".
- **RF-POR-07.** **Minhas informações**: dados pessoais (editar telefone, endereço, foto e emergência; trocar e-mail com confirmação); treinos por modalidade (unidade, turma principal com dias, horário e professor, outros horários); plano, benefício, mensalidade e vencimento; graduação atual e histórico; preferências de e-mail; termos aceitos com data e o uso de imagem (pode revogar); pedidos (troca de turma, nova modalidade, troca de plano, solicitar encerramento) e a situação de cada um; Minha conta (senha, e-mail, duas etapas, sessões); "Baixar meus dados"; "Excluir minha conta".
- **RF-POR-08.** Toda tela tem carregando, vazio, erro com "Tentar de novo".

## Pronto quando

- Um responsável com dois filhos em unidades diferentes vê os dois e paga junto só o que é da mesma unidade.
- Nenhuma resposta da API do portal traz dado de aluno não vinculado à conta.
- O aluno inativo só vê o que a regra permite, também pela API.
