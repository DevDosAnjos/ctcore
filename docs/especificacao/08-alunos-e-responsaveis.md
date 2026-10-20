# 08 · Alunos e responsáveis

Entrega 1 · Fase 8 · Depende de: 01, 02, 06, 07 · Código: ALU, RSP

## Objetivo

Cadastro de alunos e responsáveis, com a ficha que reúne tudo o que a gestão precisa saber de cada aluno.

## Dados

- **Pessoa (aluno):** organização, nome completo, nascimento, CPF, e-mail, telefone celular, endereço completo (CEP, logradouro, número, complemento, bairro, cidade, UF), foto, sexo/gênero (masculino, feminino, outro, prefiro não dizer), como conheceu (Instagram, indicação, diretório, passou na frente, outro), contato de emergência (nome, parentesco, telefone), conta (adulto), selos calculados.
- **Responsável:** conta (nome, CPF, e-mail, telefone celular), alunos vinculados, financeiro (sim/não por aluno).

## Regras

### Cadastro

- **RN-ALU-01.** Obrigatórios: nome, nascimento, endereço completo, e CPF, e-mail e telefone celular de quem paga. Aluno adulto informa os seus; para aluno menor, os do responsável são obrigatórios e o CPF, e-mail e telefone da criança são opcionais.
- **RN-ALU-02.** Opcionais: foto (JPG, PNG ou WebP, cortada em quadrado e reduzida para 512 px no envio), sexo/gênero, como conheceu, contato de emergência.
- **RN-ALU-03.** CPF e telefone são validados no formato brasileiro (dígitos verificadores do CPF; celular com DDD).
- **RN-ALU-04.** CPF repetido na organização impede o cadastro e mostra o existente. Sem CPF, mesmo nome e nascimento mostram "Parece ser [nome], já cadastrado" e deixam seguir se for outra pessoa. Nos dois casos o cadastrante vê só o nome e a foto do aluno existente, mesmo que ele seja de outra unidade. O aviso de nome e nascimento só vale quando o pedido não tem CPF.
- **RN-ALU-05.** O cadastro do aluno é da organização. Uma pessoa que treina em duas unidades tem uma ficha só.
- **RN-ALU-06.** Na matrícula numa unidade, o CPF encontra o cadastro existente na organização e mostra só nome e foto para confirmar; a nova modalidade entra na mesma matrícula.
- **RN-ALU-07.** A lista de alunos de uma unidade mostra só quem tem modalidade nela. A ficha mostra as outras modalidades e unidades, mas o Administrador só altera o que é da sua unidade. Dados pessoais podem ser alterados por qualquer Administrador de uma unidade onde o aluno tem modalidade. Até a matrícula existir (Sprint 07), o aluno tem uma unidade de cadastro e é ela que dá alcance ao Administrador; o Coordenador alcança todos. Depois da matrícula vale a modalidade na unidade.
- **RN-ALU-08.** Aluno, matrícula, cobranças e graduações nunca são excluídos; só anonimizados (`03`).
- **RN-ALU-09.** Aluno sem CPF ou e-mail de quem paga (vindo da importação) recebe o selo "Cadastro incompleto" e entra no "Precisa de atenção". As mensalidades continuam sendo geradas, mas sem Pix, cartão ou boleto do Mercado Pago (que exigem CPF) e sem e-mail: só pagamento manual no balcão até o cadastro ser completado. Ao completar, tudo volta ao normal. Quem paga é o aluno adulto ou, no menor, o responsável financeiro; o menor sem responsável financeiro também fica "Cadastro incompleto".
- **RN-ALU-10.** E-mail que volta (bounce) é marcado "Com problema", os envios param e a gestão recebe um item para corrigir.
- **RN-ALU-11.** No portal, o aluno ou responsável altera sozinho telefone, endereço, foto e contato de emergência, com auditoria de origem "portal". E-mail muda só após confirmação no endereço novo. Nome, CPF e nascimento só a gestão altera.

### Responsáveis

- **RN-RSP-01.** Aluno menor de 18 anos precisa de pelo menos um responsável. O menor nunca fica sem responsável.
- **RN-RSP-02.** Um aluno pode ter até dois responsáveis com acesso. Um é o financeiro: só ele recebe cobranças e lembretes. Os dois veem tudo, recebem avisos e podem pagar; o recibo vai para quem pagou.
- **RN-RSP-03.** Uma conta de responsável pode ter vários alunos vinculados, inclusive em unidades diferentes.
- **RN-RSP-04.** Responsável que também treina aparece como "Você" entre os cartões dos filhos, na mesma conta.
- **RN-RSP-05.** O vínculo entre responsável e aluno é criado e desfeito só pela gestão, com motivo na auditoria. Quem perdeu o acesso recebe um e-mail neutro. Ao desligar o responsável financeiro havendo outro, o que fica passa a ser o financeiro; trocar o financeiro é ligar outro como financeiro. Aluno menor não fica sem responsável: nascimento que torne o aluno menor exige responsável já vinculado, e virar adulto mantém os responsáveis até a gestão decidir (RN-RSP-07).
- **RN-RSP-06.** Cada responsável recebe o próprio convite e aceita com a própria conta. O convite sai na hora em que o responsável é vinculado, sem esperar o fim da matrícula.
- **RN-RSP-07.** Aos 18 anos, o sistema avisa o Administrador e o responsável. A academia decide se convida o aluno para ter conta própria e se o responsável continua como financeiro.

## Requisitos

- **RF-ALU-01.** Pessoas › Alunos: colunas padrão nome com foto e selos, modalidades com turma, graduação; filtros rápidos Ativos, Em atraso, Bloqueados, Ex-alunos (com contagem); filtros por modalidade, turma, nível, faixa etária, situação financeira e selos; colunas configuráveis por usuário; paginação. Entregue por etapas: a lista nasce com nome, idade, unidade, faixa etária, responsável e selos, e ganha modalidades com turma, graduação, filtros por modalidade, turma e nível e os filtros rápidos Ativos e Ex-alunos com a matrícula (Sprint 07), e situação financeira e os filtros rápidos Em atraso e Bloqueados com cobrança e inativação (Sprint 08 em diante). As colunas configuráveis ficam guardadas por usuário no navegador, sem seguir o usuário entre aparelhos. O Professor não tem esta lista: a "lista simples" dele (nome e ativo ou bloqueado, `02`) existe só com a matrícula e a turma, na turma ou na aula.
- **RF-ALU-02.** Selos: "Termo pendente", "Cadastro incompleto", "Bolsista", "E-mail com problema", "Novo" (até 30 dias da matrícula), "Aniversário hoje". Os selos chegam conforme existe o dado: Cadastro incompleto, E-mail com problema e Aniversário hoje primeiro; Novo (matrícula), Bolsista (benefício) e Termo pendente (aceite do termo) com as suas sprints. O aniversário de 29/02 é comemorado em 28/02 nos anos não bissextos, e "hoje" é a data no fuso da unidade. O filtro aceita um selo por vez.
- **RF-ALU-03.** Ficha do aluno em painel lateral (tela cheia no celular). O Professor só abre a ficha dos alunos das suas turmas e só com a permissão "ver ficha do aluno" da unidade (desligada por padrão, `02`); para ele a ficha omite valores em reais, plano, benefício, CPF, endereço, situação financeira e o botão Cobrar, e contatos e emergência seguem a permissão "ver contatos". Cabeçalho: foto, nome, idade, selos, situação, botões WhatsApp e Cobrar. Abas: Para o Professor a ficha omite também a data de nascimento (mostra só a idade), os selos e o papel financeiro e o acesso dos responsáveis, e o Histórico, que mostra ação, ator, perfil, data e motivo sem os valores antes e depois, é só da gestão. A ficha é entregue por etapas: Resumo, Responsáveis e Histórico primeiro; Matrícula e modalidades, Financeiro e Graduações, com a situação, o botão Cobrar, o próximo vencimento e as pendências, chegam com a matrícula, a cobrança e a graduação do aluno.
  - **Resumo:** contato, responsáveis, emergência, modalidades com turma, faixa e situação, próximo vencimento, pendências.
  - **Matrícula e modalidades:** cada modalidade com unidade, turma principal, outros horários, plano, benefício, mensalidade resultante, vencimento, situação; ações Trocar turma, Trocar plano, Conceder benefício, Transferir, Encerrar.
  - **Financeiro:** cobranças com situação, recibos, ações de cobrança (ver `10`).
  - **Graduações:** linha do tempo (ver `04`).
  - **Responsáveis:** lista, quem é o financeiro, convites, vincular e desvincular.
  - **Histórico:** auditoria do aluno.
- **RF-ALU-04.** Editar dados pessoais em janela, com a regra de duplicados.
- **RF-ALU-05.** "Baixar dados do aluno" e "Anonimizar aluno" (ver `03`).
- **RF-ALU-06.** Aniversariantes do dia no Início do Administrador e do Professor, com mensagem de WhatsApp pronta. Não há e-mail de aniversário. O Coordenador não tem este cartão. "Hoje" é a data no fuso da unidade. O Professor só vê aniversariantes dos alunos das suas turmas, o que só existe com a matrícula.

## Pronto quando

- Um menor não é gravado sem responsável.
- Dois cadastros com o mesmo CPF não existem na mesma organização.
- O Administrador da unidade A vê, mas não altera, a modalidade do aluno na unidade B.
