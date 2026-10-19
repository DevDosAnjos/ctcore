# 04 · Modalidades, faixas etárias e graduação

Entrega 1 · Fase 4 · Depende de: 01, 02 · Código: MOD, GRA

## Objetivo

Modalidades vêm de um catálogo da plataforma. A organização ativa as que oferece. A graduação é um módulo opcional por modalidade, registrado pela gestão.

## Dados

- **Modalidade (catálogo da plataforma):** nome, tem graduação (sim/não), sistema de graduação padrão.
- **Sistema de graduação padrão:** lista ordenada de níveis (faixas), cada um com cor, grau máximo, idade mínima e máxima, e se é infantil ou adulto.
- **Modalidade da organização:** organização, modalidade, ativa, graduação ligada, sistema de graduação da organização (cópia editável do padrão).
- **Modalidade da unidade:** unidade, modalidade da organização, oferecida.
- **Faixa etária:** organização, nome, idade mínima, idade máxima.
- **Graduação:** aluno, modalidade, nível e grau anteriores, nível e grau novos, data, decidido por (texto livre ou professor), registrado por, observação, origem (registro, origem, importação), comprovante (PDF ou imagem, opcional), avisar o aluno (sim/não), motivo (quando pula etapa ou corrige).

## Regras

### Modalidades

- **RN-MOD-01.** Catálogo inicial: Jiu-Jitsu, Judô, Muay Thai, Boxe, Karatê, Wrestling, MMA, Taekwondo, Capoeira, Kickboxing, Luta Livre, Kung Fu, Krav Magá e Defesa pessoal.
- **RN-MOD-02.** Só o Coordenador ativa e desativa modalidades para a organização. Cada unidade marca quais oferece: o Coordenador em qualquer unidade e o Administrador nas suas. Desativar na organização guarda as marcações das unidades, que voltam se a modalidade for reativada. O Administrador só consulta o catálogo da organização.
- **RN-MOD-03.** Modalidade não ativa não aparece em turmas, planos, aula experimental, diretório nem portal. O aluno vê só as modalidades em que está matriculado.
- **RN-MOD-04.** "Não achei minha modalidade" envia um pedido ao painel da plataforma. A equipe inclui a modalidade no catálogo para todos.
- **RN-MOD-05.** Variações (No-Gi, Kids, Competição) ficam no nome da turma e na faixa etária. Não existe cadastro extra.
- **RN-MOD-06.** Desativar uma modalidade exige que não haja turma ativa nem aluno com ela ativa na organização; havendo, a desativação é recusada e a tela mostra o que impede, sem "desativar assim mesmo". Desmarcar a modalidade numa unidade não passa por essa recusa.

### Faixas etárias

- **RN-MOD-07.** Lista da organização com idade mínima e máxima (a máxima é opcional; idades de 0 a 120, nome de 2 a 40 caracteres, único sem acento e sem diferença de maiúsculas; faixas podem se sobrepor). Vêm três faixas padrão: Infantil (4 a 12), Juvenil (13 a 17) e Adulto (18 ou mais). Só o Coordenador cria, edita e apaga; o Administrador consulta. Apagar é livre até que turmas, aula experimental e diretório passem a usar a faixa. Usada em turmas, aula experimental e diretório. Fora da faixa, o sistema avisa e não impede.

### Graduação

- **RN-GRA-01.** A graduação é opcional por modalidade. O catálogo traz o padrão (Jiu-Jitsu: IBJJF completo, com faixas infantis intermediárias e grau máximo por faixa); só o Jiu-Jitsu tem padrão, e as demais modalidades começam sem níveis. O padrão é copiado para a organização ao ativar a modalidade. Só o Coordenador liga, desliga e edita níveis, cores, grau máximo e idades; o Administrador consulta. Desligar mantém os níveis e os dados já gravados, que só deixam de aparecer nas telas; ligar exige ao menos um nível. Limites: nome do nível de 2 a 40 caracteres, até 30 níveis, grau máximo de 0 a 10. O conteúdo exato do padrão (19 níveis: 12 faixas infantis de duas cores, Branca, Azul, Roxa, Marrom, Preta com 6 graus, Coral e Vermelha) e esses limites foram aceitos pelo dono em 19/10/2026. A edição grava o sistema inteiro de uma vez, e em gravações simultâneas vale a última.
- **RN-GRA-02.** O sistema informa e registra; o mestre decide. Não existe sugestão de quem está apto, barra de progresso, tempo mínimo, contagem de aulas nem sugestão de grau por tempo.
- **RN-GRA-03.** Só a gestão registra: Coordenador em qualquer unidade e Administrador nas modalidades do aluno que são das suas unidades. O Professor consulta.
- **RN-GRA-04.** A faixa é única por modalidade, mesmo com o aluno em várias unidades. Vale o registro mais recente pela data da graduação.
- **RN-GRA-05.** A data da graduação não pode ser futura.
- **RN-GRA-06.** Pular nível ou grau (ex.: azul 2º grau direto para roxa) mostra "Isso pula etapas. Confirmar?" e exige motivo. Não bloqueia.
- **RN-GRA-07.** Idade fora da faixa do nível mostra aviso e não bloqueia.
- **RN-GRA-08.** A graduação não muda o valor da mensalidade.
- **RN-GRA-09.** Graduação de origem: informada na matrícula, por modalidade, com data quando houver, marcada "de origem", sem aviso ao aluno. Com "Exigir comprovante da graduação de origem" ligado na modalidade, o comprovante é obrigatório. O histórico mostra "com comprovante" ou "sem comprovante".
- **RN-GRA-10.** Registro avisa o aluno pelo portal (selo "Nova") e por e-mail. A gestão pode desmarcar o aviso ao registrar.
- **RN-GRA-11.** Modalidade bloqueada ou aluno inativo não recebe graduação.
- **RN-GRA-12.** Graduação nunca é excluída. Um registro errado é corrigido por um novo registro marcado "Correção", com motivo; o errado fica no histórico como "Corrigido" (riscado) e sai do portal do aluno.
- **RN-GRA-13.** Registro em lote grava tudo ou nada.

## Requisitos

- **RF-MOD-01.** Configurações › Modalidades: blocos "Suas modalidades" e "Disponíveis para ativar", botão "Não achei minha modalidade".
- **RF-MOD-02.** Por modalidade ativa: graduação ligada ou desligada, editor do sistema de graduação (níveis em ordem, cor, grau máximo, idades), exigir comprovante de origem.
- **RF-MOD-03.** Configurações › Unidade › Modalidades oferecidas.
- **RF-MOD-04.** Configurações › Faixas etárias (ver RN-MOD-07).
- **RF-GRA-01.** Operação › Graduações: lista com aluno, modalidade, turma, graduação anterior, nova, data e quem registrou; resumo do período (graduações, novos graus, trocas de faixa); filtros por modalidade, turma, nível e período.
- **RF-GRA-02.** "Registrar graduação": aluno (ou já preenchido a partir da ficha), modalidade, atual e nova lado a lado, data, decidido por, observação, avisar o aluno.
- **RF-GRA-03.** "Graduar vários alunos": filtrar por turma, marcar alunos e escolher para cada um "mais um grau" ou "próximo nível", com a mesma data, quem decidiu e escolha de aviso.
- **RF-GRA-04.** Na ficha do aluno, aba Graduações: linha do tempo por modalidade, da mais recente para a mais antiga, com selos "de origem", "com comprovante", "Correção".

## Pronto quando

- Nenhuma tela sugere quem graduar.
- Com graduação desligada numa modalidade, nenhuma tela mostra faixa para ela.
- O Professor não encontra o botão de registrar e a API recusa.
- Aluno em duas unidades com a mesma modalidade tem uma faixa só.
