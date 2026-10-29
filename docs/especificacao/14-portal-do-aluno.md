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
- **RN-POR-03.** Antes do aceite do termo de matrícula (`09`), nenhuma outra tela abre. O bloqueio é do servidor, que recusa toda rota do portal fora o aceite e o uso de imagem; o front leva ao termo. O responsável com vários filhos fica bloqueado até aceitar por todos os filhos pendentes.
- **RN-POR-04.** Aluno com modalidades em várias unidades vê uma visão só, com etiqueta de modalidade e unidade em cada item e filtro. A graduação aparece por modalidade.
- **RN-POR-05.** Responsável: faixa no topo com um cartão por filho (e "Você", se também treina) e o total da família em aberto; abaixo, tudo do cartão escolhido. O Financeiro tem a visão "Toda a família" para pagar junto o que é da mesma unidade. O cartão escolhido filtra aulas, avisos e faixa; o total da família soma só o que a conta paga; o primeiro cartão é "Você" e, sem ele, o primeiro filho por nome; a escolha não é gravada.
- **RN-POR-06.** O aluno vê o contato da unidade (telefone, WhatsApp, e-mail), nunca o do professor.
- **RN-POR-06a.** O portal mostra o nome do professor, só o nome, ao aluno matriculado na turma ou na aula, em todas as telas (Agenda, aviso, treinos por modalidade); nunca foto, graduação, telefone nem e-mail. Não contradiz a RN-PRO-04, que trata do diretório e dos horários públicos; o portal é autenticado e restrito ao matriculado.
- **RN-POR-07.** O aluno não altera turma, plano, benefício, vencimento nem graduação; para isso faz um pedido (`09`).
- **RN-POR-08.** Inativo e ex-aluno veem o portal reduzido (RN-INA-08 e RN-INA-09). "Reduzido" é calculado do que existe: aluno matriculado sem modalidade ativa; com vários alunos na conta, vale se nenhum está ativo. O bloqueio por atraso, a reativação e o fim do acesso do ex-aluno entram na Sprint 12.
- **RN-POR-09.** Sem presença, sem frequência, sem progresso de graduação.

### Instalação e uso sem internet

- **RN-POR-10.** O portal é instalável como PWA ("Adicionar à tela inicial"), sem loja. Só o app (`app.`) é instalado.
- **RN-POR-11.** Sem internet, mostra a última versão do Início, da agenda e da carteirinha com o aviso "Sem conexão". Pagar e enviar pedidos exigem conexão.
- **RN-POR-12.** Push opcional, pedido ao instalar: cobrança vencendo, pagamento confirmado, aviso novo e aula cancelada. Quem não aceita segue com e-mail.

## Requisitos

- **RF-POR-01.** Barra com logo e nome da organização, abas Início, Agenda, Financeiro, Avisos e Minhas informações, sino com avisos não lidos (não as notificações da gestão), nome e papel de quem está logado. No celular, abas embaixo.
- **RF-POR-02.** **Início**, nesta ordem: 1) cartões de cobrança vencida ou vencendo com "Pagar" (todas as que pedem ação, a vencida antes), ou "Tudo em dia" com a data do próximo vencimento; 2) próximas 3 aulas, com alterações e cancelamentos destacados (aula "alterada" é a com substituto ou editada à mão); 3) avisos não lidos e fixados; 4) faixa atual por modalidade, com o selo "Nova" por 14 dias quando a graduação não é a de origem.
- **RF-POR-03.** **Carteirinha**, a partir do Início e de Minhas informações: foto, nome, organização, modalidades, faixa, selo "Ativo" (ter modalidade ativa) ou "Inativo" (cobre inativo e ex-aluno) com a data do dia no fuso da unidade de cadastro, logo e cor da organização. Sem QR code. Funciona sem internet mostrando "Atualizada em [data e hora]". O responsável vê a carteirinha de todo filho ligado, pagador ou não.
- **RF-POR-04.** **Agenda**: semana em grade no computador e lista do dia no celular; distingue turma principal, outros horários, aula avulsa, aula alterada (com resumo e "Ver aviso"), aula cancelada ("Cancelada"), dia sem aula ("Sem aula" e o motivo) e aula que já passou; lista o professor na aula (só o nome, RN-POR-06a); alternar "Só minha turma" e "Todos os meus horários", sendo o padrão "Todos os meus horários" e "Só minha turma" apenas a turma principal de cada modalidade ativa; trocar de semana. "Ver aviso" aponta para o comunicado enviado mais recente do tipo certo que alcança a turma, ou some se não houver. Só consulta.
- **RF-POR-05.** **Financeiro**: cobranças em aberto (vencidas primeiro) com composição e "Pagar"; seleção de várias da mesma unidade para pagar junto; histórico por ano com referência, vencimento, valor, data do pagamento, meio e situação; recibos para baixar; enviar comprovante de Pix na chave; débito automático. A cobrança em conferência continua visível, sem "Pagar". Até a Sprint 11, o débito automático aparece como cartão informativo, sem ativar nada; "pagar junto" segue a RN-COB-14. O recibo baixado sai com CPF mascarado (RN-COB-16).
- **RF-POR-06.** **Avisos**: lista com título, data, destino e autor; não lidos destacados; fixados no topo; filtros Todos, Não lidos, Por modalidade; "Marcar todos como lidos", que grava uma dispensa por conta e aviso (vale para todos os alunos da conta, tira destaque, sino e não lidos), não uma leitura; a gestão segue vendo zero leituras e aviso novo volta a contar. O aviso só é lido quando a pessoa o abre, inclusive por link.
- **RF-POR-07.** **Minhas informações**: qualquer conta ligada ao aluno edita contato, foto e preferências; ver não exige ser pagador. Dados pessoais (editar telefone, endereço, foto e emergência; trocar e-mail com confirmação, a mesma de Minha conta, RN-ACE-15, e o e-mail de contato do cadastro da conta acompanha o novo endereço em todas as organizações da conta, com auditoria de origem portal; o de menor sem conta não muda); treinos por modalidade (unidade, turma principal com dias, horário e professor, outros horários); plano, benefício, mensalidade e vencimento; graduação atual e histórico; preferências de e-mail; termos aceitos com data e o uso de imagem (pode revogar); pedidos (troca de turma, nova modalidade, troca de plano, solicitar encerramento) e a situação de cada um; Minha conta (senha, e-mail, duas etapas, sessões); "Baixar meus dados"; "Excluir minha conta" (as duas ficam visíveis e desabilitadas até a Sprint 13). No portal reduzido a aba traz dados pessoais, contato da unidade, preferência, termos, Minha conta e as ações de dados, sem treinos, plano, graduação nem pedidos; editar contato continua.
- **RF-POR-08.** Toda tela tem carregando, vazio, erro com "Tentar de novo".

## Pronto quando

- Um responsável com dois filhos em unidades diferentes vê os dois e paga junto só o que é da mesma unidade.
- Nenhuma resposta da API do portal traz dado de aluno não vinculado à conta.
- O aluno inativo só vê o que a regra permite, também pela API.
