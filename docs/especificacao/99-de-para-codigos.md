# 99b · De-para por código

Liga cada código dos documentos antigos aos códigos desta especificação, complementando o de-para por fase de `99-de-para.md`. Os códigos antigos são citados com o prefixo do documento de origem: `G:` Gestão, `P:` Portal, `S:` Site e acesso, `E:` Evolução para SaaS. A coluna "Situação" usa quatro valores: **Mantido** (mesmo significado), **Alterado** (coberto, mas a regra mudou), **Retirado** (deixou de valer) e **Absorvido** (coberto de forma genérica, por exemplo por um RNF ou por um padrão de tela). Quando não há código novo equivalente, a coluna aponta a seção do arquivo (ex.: `00` §9) ou fica com "—".

## Gestão (G:)

| Código antigo | Resumo | Código(s) novo(s) | Situação | Nota |
| --- | --- | --- | --- | --- |
| `G:RNF-01` | Gestão só no computador, a partir de 1280 px | `RNF-19` | Alterado | Gestão passa a ser responsiva, a partir de 360 px. |
| `G:RNF-02` | Textos em português simples | `RNF-24` | Mantido |  |
| `G:RNF-03` | Tarefas resolvidas na própria tela | `RNF-27` | Mantido |  |
| `G:RNF-04` | Mesmos padrões de filtro, tabela, painel e janela | `RNF-27` | Mantido |  |
| `G:RNF-05` | Acessibilidade nível AA | `RNF-20` | Mantido |  |
| `G:RNF-06` | Confirmação antes de ação irreversível | `RNF-26` | Mantido |  |
| `G:RNF-07` | Listas e painéis em até 2 segundos | `RNF-01` | Mantido |  |
| `G:RNF-08` | Busca responde enquanto digita | `RF-BUS-01` | Absorvido |  |
| `G:RNF-09` | Listas paginadas | `RNF-02` | Mantido |  |
| `G:RNF-10` | Login, senha com hash e HTTPS | `RNF-04`, `RN-ACE-09` | Mantido |  |
| `G:RNF-11` | Permissão conferida no servidor | `RNF-05` | Mantido |  |
| `G:RNF-12` | Sessão expira sem uso (prazo a definir) | `RN-ACE-19` | Alterado | Prazo definido: 12 horas na gestão. |
| `G:RNF-13` | Auditoria imutável, cobre alterações e exportações | `RN-AUD-01`, `RN-AUD-02` | Mantido |  |
| `G:RNF-14` | Dados pessoais só marcados; cópia completa pede senha | `RN-EXP-01`, `RN-EXP-03` | Alterado | A exportação avisa antes de levar dado pessoal; sem colunas desmarcadas. |
| `G:RNF-15` | Tratamento de dados segue a LGPD | `RN-LGP-03`, `RN-LGP-04` | Mantido |  |
| `G:RNF-16` | Cópia de segurança diária testada | `RNF-17` | Mantido |  |
| `G:RNF-17` | Operações financeiras sem repetição | `RNF-12` | Mantido |  |
| `G:RNF-18` | Operações em lote: tudo ou nada | `RNF-13`, `RN-IMP-03` | Alterado | A importação passou a aceitar as linhas válidas mesmo com erro em outras. |
| `G:RNF-19` | Falha de serviço externo não perde operação | `RNF-14` | Mantido |  |
| `G:RNF-20` | Mensagens de erro claras | `RNF-24` | Mantido |  |
| `G:RNF-21` | Teste automatizado por regra | `RNF-29` | Mantido |  |
| `G:RNF-22` | API não espelha as telas | `00` §9 | Absorvido |  |
| `G:RNF-23` | Código preparado para combinação, matrícula e provedor | `RN-PAG-03` | Alterado | Combinação de benefícios não existe; matrícula já definida em `09`. |
| `G:RNF-24` | Ambiente de teste separado, com dados fictícios | `00` §9 | Absorvido |  |
| `G:RNF-25` | Navegadores atuais | `RNF-19` | Mantido |  |
| `G:RNF-26` | Formatos brasileiros e fuso de Brasília | `RNF-23` | Alterado | Horário no fuso da unidade. |
| `G:RF-BAS-01` | Entrar e sair; sem sessão vai ao login | `RF-ACE-01`, `RN-ACE-16` | Mantido |  |
| `G:RF-BAS-02` | Dois perfis, permissão no servidor | `RNF-05`, `02` (Permissões) | Alterado | Três perfis: Coordenador, Administrador e Professor. |
| `G:RF-BAS-03` | Grupos do menu lateral | `RNF-27` | Absorvido |  |
| `G:RF-BAS-04` | Menu expandido ou recolhido | `RNF-27` | Absorvido |  |
| `G:RF-BAS-05` | Grupo do item em uso aberto e destacado | `RNF-27` | Absorvido |  |
| `G:RF-BAS-06` | Menu mostra só o permitido ao perfil | `RNF-05` | Absorvido |  |
| `G:RF-BAS-07` | Cabeçalho com trilha e usuário logado | `RNF-27` | Absorvido |  |
| `G:RF-BAS-08` | Toda operação gera registro de auditoria | `RN-AUD-02` | Mantido |  |
| `G:RF-BAS-09` | Estados carregando, vazio e erro nas listas | `RNF-25` | Mantido |  |
| `G:RF-BAS-10` | Tela de área sem permissão | `RF-ACE-08` | Mantido |  |
| `G:RF-BAS-11` | Paginação com contagem | `RNF-02` | Mantido |  |
| `G:RF-BAS-12` | Linha abre painel lateral | `RNF-27` | Mantido |  |
| `G:RN-BAS-01` | Servidor recusa ação sem permissão | `RNF-05` | Mantido |  |
| `G:RN-BAS-02` | Auditoria não editável nem apagável | `RN-AUD-01` | Mantido |  |
| `G:RN-BAS-03` | Datas, reais e fuso de Brasília | `RNF-23` | Alterado | Horário no fuso da unidade. |
| `G:RN-BAS-04` | Interface organizada pelo trabalho, não pelo banco | `00` §9 | Absorvido |  |
| `G:RN-BAS-05` | Ações do item ficam no painel ou janela | `RNF-27` | Absorvido |  |
| `G:RN-BAS-06` | Erro ao carregar informa que nada mudou | `RNF-24` | Mantido |  |
| `G:RF-ACA-01` | Editar nome, filiação, WhatsApp, e-mail e Instagram | `RF-ORG-01`, `RF-ORG-04` | Alterado | Dados divididos entre organização e unidade. |
| `G:RF-ACA-02` | Editar endereço completo | `RF-ORG-04`, `RN-ORG-14` | Alterado | Endereço passa a ser da unidade, com coordenadas. |
| `G:RF-ACA-03` | Logo e cor de destaque | `RF-ORG-01`, `RNF-22` | Alterado | Cor da organização no portal, recibos e e-mails; sem site. |
| `G:RF-ACA-04` | Publicar site, horários e equipe | `RN-SIT-01`, `RN-DIR-02` | Retirado | Não existe site da academia; há o diretório opcional. |
| `G:RF-ACA-05` | Mostrar última alteração e autor | `RF-ORG-08` | Mantido |  |
| `G:RF-ACA-06` | "Descartar" volta ao último valor salvo | `RNF-27` | Absorvido |  |
| `G:RN-ACA-01` | Uma única academia, sem unidades | `RN-ORG-04`, `RN-ORG-05` | Retirado | Organização pode ter várias unidades. |
| `G:RN-ACA-02` | Nome da academia obrigatório | `RF-ORG-01` | Mantido |  |
| `G:RN-ACA-03` | Horários e equipe do site vêm dos cadastros | `RN-DIR-04` | Alterado | Vale para o diretório, não para site próprio. |
| `G:RN-ACA-04` | Alteração fica no histórico | `RF-ORG-08` | Mantido |  |
| `G:RF-USU-01` | Lista da equipe com perfil e último acesso | `RF-ACE-06` | Mantido |  |
| `G:RF-USU-02` | Convidar usuário com e-mail e perfil | `RF-ACE-06`, `RN-ACE-05` | Alterado | Convites em cascata: Coordenador convida Administradores; Administrador, Professores. |
| `G:RF-USU-03` | Reenviar convite não aceito | `RF-ACE-06`, `RN-ACE-13` | Mantido |  |
| `G:RF-USU-04` | Quadro de perfis Administrador e Professor | `02` (Permissões) | Alterado | Três perfis e cinco permissões do Professor por unidade (a quinta, "ver ficha do aluno", vem desligada por padrão). |
| `G:RF-USU-05` | Aba com contas do portal | `RF-ALU-03` | Alterado | Acesso ao portal aparece na ficha do aluno, sem aba própria. |
| `G:RF-USU-06` | Contagem de acessos e convites pendentes do portal | `RF-INI-02` | Alterado | Convites não aceitos vão para o "Precisa de atenção". |
| `G:RF-USU-07` | Enviar acesso ao portal, um ou todos | `RN-MAT-08`, `RF-IMP-03` | Alterado | Convite sai sozinho na matrícula; envio em massa após importação. |
| `G:RN-USU-01` | Só dois perfis na gestão | `00` §4 | Alterado | Perfis passam a ser Coordenador, Administrador e Professor. |
| `G:RN-USU-02` | "Editar perfis" a definir | `00` §4 | Retirado | Perfis fixos; não existe edição de perfis. |
| `G:RN-USU-03` | Professor vê só suas turmas | `02` (Permissões) | Mantido |  |
| `G:RN-USU-04` | Sempre um administrador ativo | `RN-ORG-07` | Alterado | Pelo menos um Administrador por unidade ativa. |
| `G:RN-USU-05` | Convite com validade (a definir) | `RN-ACE-13` | Alterado | Prazo definido: 7 dias. |
| `G:RN-USU-06` | Responsável ligado a vários alunos | `RN-RSP-03` | Mantido |  |
| `G:RF-PRO-01` | Listar professores com faixa, turmas e status | `RF-PRO-01` | Mantido |  |
| `G:RF-PRO-02` | Filtrar por nome, status e tipo de turma | `RF-PRO-01` | Alterado | Filtros por modalidade e unidade no lugar do tipo de turma. |
| `G:RF-PRO-03` | Painel com contato, acesso e turmas | `RF-PRO-02` | Mantido |  |
| `G:RF-PRO-04` | Cadastrar e editar professor | `RF-PRO-03` | Alterado | Graduação por modalidade ensinada; unidades no cadastro. |
| `G:RF-PRO-05` | "Dar acesso ao sistema" com perfil | `RN-PRO-02` | Alterado | O acesso cria só o vínculo de Professor nas unidades marcadas. |
| `G:RF-PRO-06` | Ativar e inativar professor | `RF-PRO-02`, `RF-PRO-04` | Mantido |  |
| `G:RF-PRO-07` | Atalhos WhatsApp e agenda | `RF-PRO-02` | Mantido |  |
| `G:RN-PRO-01` | Cadastro separado do acesso | `RN-PRO-02` | Mantido |  |
| `G:RN-PRO-02` | E-mail obrigatório para dar acesso | `RN-PRO-02`, `RN-ACE-01` | Mantido |  |
| `G:RN-PRO-03` | Administrador também pode ser professor | `RN-ACE-02` | Mantido |  |
| `G:RN-PRO-04` | Inativo some das escolhas | `RN-PRO-05` | Mantido |  |
| `G:RN-PRO-05` | Faixa do professor informada no cadastro | `RN-PRO-03` | Mantido |  |
| `G:RF-PLA-01` | Três cartões: Planos, Descontos e Bolsas | `RF-PLA-01` | Alterado | Dois cartões: Planos e Benefícios. |
| `G:RF-PLA-02` | Lista de planos com valor e alunos | `RF-PLA-02` | Mantido |  |
| `G:RF-PLA-03` | Criar e editar planos | `RF-PLA-03` | Mantido |  |
| `G:RF-PLA-04` | Ativar e desativar plano | `RN-PLA-06` | Mantido |  |
| `G:RN-PLA-01` | Mensalidade é plano menos benefícios | `RN-COB-03` | Mantido |  |
| `G:RN-PLA-02` | Novo valor só nas cobranças futuras | `RN-PLA-04`, `RN-PLA-05` | Mantido |  |
| `G:RN-PLA-03` | Sem desconto por faixa | `RN-BEN-02`, `RN-GRA-08` | Mantido |  |
| `G:RN-PLA-04` | Só planos mensais Infantil, Juvenil e Adulto | `RN-PLA-01` | Alterado | Plano por modalidade e unidade, ainda só mensal. |
| `G:RN-PLA-05` | Plano com alunos só é desativado | `RN-PLA-06` | Mantido |  |
| `G:RF-TUR-01` | Lista de turmas com horário e grupo | `RF-TUR-01` | Mantido |  |
| `G:RF-TUR-02` | Filtrar por tipo, período e professor | `RF-TUR-01` | Alterado | Filtros por modalidade, professor, faixa etária e dia. |
| `G:RF-TUR-03` | Painel da turma | `RF-TUR-02` | Mantido |  |
| `G:RF-TUR-04` | Criar e editar turma tipo × período | `RF-TUR-03` | Alterado | Turma livre com horários por dia; sem plano padrão. |
| `G:RF-TUR-05` | Desativar turma | `RF-TUR-02`, `RN-TUR-03` | Mantido |  |
| `G:RF-TUR-06` | Atalhos: alunos, agenda e grupo | `RF-TUR-02` | Mantido |  |
| `G:RF-TUR-07` | Comunicado pronto ao mudar horário | `RF-TUR-03`, `RN-AGE-02` | Mantido |  |
| `G:RN-TUR-01` | Turma é tipo × período (nove turmas) | `RN-TUR-01` | Alterado | Turma livre, com nome único na unidade. |
| `G:RN-TUR-02` | Várias turmas no mesmo tipo e período (a definir) | `RN-TUR-01` | Alterado | Decidido: permitido, com nomes diferentes. |
| `G:RN-TUR-03` | Professor responsável e plano padrão | `RN-TUR-05` | Alterado | Titular obrigatório; plano padrão não existe mais. |
| `G:RN-TUR-04` | Mudança de horário vale para próximas aulas | `RN-AGE-02` | Mantido |  |
| `G:RN-TUR-05` | Turma com alunos só é desativada | `RN-TUR-03` | Mantido |  |
| `G:RN-TUR-06` | Turma não guarda presença | `RN-TUR-06` | Mantido |  |
| `G:RF-ALU-01` | Lista com turma, faixa e situação financeira | `RF-ALU-01` | Mantido |  |
| `G:RF-ALU-02` | Filtros rápidos Todos, Ativos, Inativos, Bolsistas | `RF-ALU-01`, `RF-ALU-02` | Alterado | Filtros Ativos, Em atraso, Bloqueados e Ex-alunos; Bolsista vira selo. |
| `G:RF-ALU-03` | Filtros por nome, turma, faixa e financeiro | `RF-ALU-01` | Mantido |  |
| `G:RF-ALU-04` | Painel lateral do aluno | `RF-ALU-03` | Mantido |  |
| `G:RF-ALU-05` | Ações do painel do aluno | `RF-ALU-03` | Mantido |  |
| `G:RF-ALU-06` | Ficha completa com graduações e financeiro | `RF-ALU-03` | Mantido |  |
| `G:RF-ALU-07` | Edição de dados, situação, turma, plano e vencimento | `RF-ALU-04`, `RF-ALU-03` | Alterado | Turma, plano e vencimento mudam pela aba da matrícula; situação é calculada. |
| `G:RF-ALU-08` | Benefícios e mensalidade só para leitura | `RF-ALU-03` | Mantido |  |
| `G:RF-ALU-09` | Situação do acesso ao portal e reenvio | `RF-ALU-03` | Mantido |  |
| `G:RF-ALU-10` | "Pausar ou encerrar" o aluno | `RF-MAT-03`, `RF-INA-02` | Alterado | Sem pausa; encerramento por modalidade. |
| `G:RF-ALU-11` | Janela explica efeitos da pausa ou encerramento | `RF-MAT-03`, `RNF-26` | Alterado | Sem pausa; resumo do efeito do encerramento. |
| `G:RF-ALU-12` | "Novo aluno" como cadastro simples | `RF-MAT-01` | Alterado | Vira a nova matrícula em etapas. |
| `G:RN-ALU-01` | Situações Ativo, Pausado, Inativo; nunca excluído | `RN-ALU-08`, `12` (Situações) | Alterado | Situações Ativo, Inativo e Ex-aluno, calculadas; sem Pausado. |
| `G:RN-ALU-02` | Menor precisa de responsável | `RN-RSP-01`, `RN-RSP-02` | Mantido |  |
| `G:RN-ALU-03` | Turma principal e outros horários | `RN-TUR-04`, `RN-MAT-02` | Mantido |  |
| `G:RN-ALU-04` | Vencimento nos dias 1, 5, 10 ou 15 | `RN-MAT-03` | Alterado | Dias permitidos configurados pela unidade (padrão 1, 5, 10 e 15). |
| `G:RN-ALU-05` | Mensalidade calculada, nunca digitada | `RN-COB-03` | Mantido |  |
| `G:RN-ALU-06` | Graduação só por "Registrar graduação" | `RN-GRA-03` | Mantido |  |
| `G:RN-ALU-07` | Situação financeira vem das cobranças | `RF-ALU-01` | Mantido |  |
| `G:RN-ALU-08` | Bolsista é quem tem bolsa ativa | `RF-ALU-02` | Mantido |  |
| `G:RN-ALU-09` | Efeitos da pausa do aluno | — | Retirado | A situação Pausado não existe. |
| `G:RN-ALU-10` | Fluxo de matrícula não definido | `RN-MAT-01`, `RF-MAT-01` | Alterado | Matrícula definida em `09`. |
| `G:RN-ALU-11` | Mesmo nome e nascimento é o mesmo aluno | `RN-ALU-04` | Alterado | CPF identifica; nome e nascimento só avisam. |
| `G:RN-ALU-12` | Sem presença nem frequência do aluno | `RN-TUR-06` | Mantido |  |
| `G:RF-GRA-01` | Lista de graduações registradas | `RF-GRA-01` | Mantido |  |
| `G:RF-GRA-02` | Resumo do período | `RF-GRA-01` | Mantido |  |
| `G:RF-GRA-03` | Filtros por aluno, faixa e datas | `RF-GRA-01` | Mantido |  |
| `G:RF-GRA-04` | "Registrar graduação" | `RF-GRA-02` | Mantido |  |
| `G:RF-GRA-05` | Faixa atual e nova lado a lado | `RF-GRA-02` | Mantido |  |
| `G:RF-GRA-06` | "Graduar vários alunos" | `RF-GRA-03` | Mantido |  |
| `G:RF-GRA-07` | Escolha de aviso no registro em lote | `RF-GRA-03`, `RN-GRA-10` | Mantido |  |
| `G:RF-GRA-08` | Histórico de graduações na ficha | `RF-GRA-04` | Mantido |  |
| `G:RN-GRA-01` | Sistema informa; mestre decide; sem "apto" | `RN-GRA-02` | Mantido |  |
| `G:RN-GRA-02` | Graduação não depende de frequência | `RN-GRA-02`, `RN-TUR-06` | Mantido |  |
| `G:RN-GRA-03` | Guarda quem decidiu e quem lançou | `RF-GRA-02` | Mantido |  |
| `G:RN-GRA-04` | Nova graduação vira atual; histórico fica | `RN-GRA-04`, `RN-GRA-12` | Mantido |  |
| `G:RN-GRA-05` | Data da graduação não pode ser futura | `RN-GRA-05` | Mantido |  |
| `G:RN-GRA-06` | Último grau só recebe a próxima faixa | `RN-GRA-06` | Alterado | Pular etapas é permitido, com aviso e motivo. |
| `G:RN-GRA-07` | Lista de faixas a confirmar | `RN-GRA-01` | Alterado | Sistema do catálogo (IBJJF), editável pelo Coordenador. |
| `G:RN-GRA-08` | Graduação não muda a mensalidade | `RN-GRA-08` | Mantido |  |
| `G:RN-GRA-09` | Professor registra graduação das suas turmas | `RN-GRA-03` | Alterado | Só a gestão registra; o Professor consulta. |
| `G:RF-GRU-01` | Lista de turmas com grupo | `RF-TUR-01` | Retirado | Sem tela própria de grupos. |
| `G:RF-GRU-02` | Destacar turmas sem grupo | — | Retirado | Sem tela própria de grupos. |
| `G:RF-GRU-03` | Painel do grupo | `RF-TUR-02` | Alterado | O grupo aparece no painel da turma. |
| `G:RF-GRU-04` | Vincular, editar e remover grupo | `RF-TUR-03` | Alterado | Link do grupo editado na janela da turma. |
| `G:RF-GRU-05` | Atalhos: abrir grupo e criar comunicado | `RF-TUR-02` | Alterado | Atalhos ficam no painel da turma. |
| `G:RN-GRU-01` | WhatsApp segue como canal da academia | `RN-COM-03` | Mantido |  |
| `G:RN-GRU-02` | No máximo um grupo por turma | `RF-TUR-03` | Mantido |  |
| `G:RN-GRU-03` | Turma sem grupo: só portal e e-mail | `RN-COM-03` | Alterado | Sem grupo, o WhatsApp abre a conversa com a mensagem pronta. |
| `G:RN-GRU-04` | Guarda nome e link; não lê mensagens | `RF-TUR-03` | Alterado | Guarda só o link do grupo. |
| `G:RF-COM-01` | Lista de comunicados enviados | `RF-COM-01` | Mantido |  |
| `G:RF-COM-02` | Filtros por período, turma e tipo | `RF-COM-01` | Mantido |  |
| `G:RF-COM-03` | Novo comunicado para uma turma ou todas | `RF-COM-02`, `RN-COM-01` | Alterado | Destinos: turma, modalidade, unidade ou todas as unidades. |
| `G:RF-COM-04` | Tipos com texto inicial pronto | `RN-COM-04` | Mantido |  |
| `G:RF-COM-05` | Canais Portal, WhatsApp e E-mail | `RN-COM-03` | Mantido |  |
| `G:RF-COM-06` | Prévia de destinatários e canais | `RN-COM-09` | Mantido |  |
| `G:RF-COM-07` | "Aula cancelada" cancela a aula junto | `RN-AGE-05` | Alterado | Fluxo inverso: cancelar a aula abre o comunicado. |
| `G:RF-COM-08` | Outras telas abrem comunicado preenchido | `RF-COM-02` | Mantido |  |
| `G:RN-COM-01` | Portal sempre; e-mail; WhatsApp do grupo | `RN-COM-03` | Mantido |  |
| `G:RN-COM-02` | WhatsApp enviado à mão | `RN-COM-03` | Mantido |  |
| `G:RN-COM-03` | Aviso do menor vai ao responsável | `RN-COM-02` | Mantido |  |
| `G:RN-COM-04` | Quem treina no horário também recebe | `RN-COM-02` | Mantido |  |
| `G:RN-COM-05` | Turma sem grupo: WhatsApp indisponível | `RN-COM-03` | Alterado | Sem grupo, abre a conversa com a mensagem pronta. |
| `G:RN-COM-06` | Comunicado enviado não muda | `RN-COM-06` | Mantido |  |
| `G:RN-COM-07` | Professor envia só às suas turmas | `RN-COM-01` | Mantido |  |
| `G:RF-AGE-01` | Lista de aulas do dia ou semana | `RF-AGE-01` | Alterado | Grade semanal no computador e lista do dia no celular. |
| `G:RF-AGE-02` | Atalhos de data e filtros | `RF-AGE-01` | Alterado | Atalhos Hoje e Esta semana; filtros por professor e modalidade. |
| `G:RF-AGE-03` | Painel da aula | `RF-AGE-02` | Mantido |  |
| `G:RF-AGE-04` | Ações do painel da aula | `RF-AGE-02` | Mantido |  |
| `G:RF-AGE-05` | Editar aula, só esta ou as seguintes | `RF-AGE-03`, `RN-AGE-04` | Mantido |  |
| `G:RF-AGE-06` | Troca de professor mostra substituição | `RF-AGE-02`, `RN-AGE-04` | Mantido |  |
| `G:RF-AGE-07` | Cancelar aula com motivo e aviso | `RF-AGE-03`, `RN-AGE-05`, `RN-AGE-06` | Mantido |  |
| `G:RF-AGE-08` | Cadastrar dias sem aula | `RF-AGE-05`, `RN-AGE-09` | Mantido |  |
| `G:RF-AGE-09` | Quando avisar o dia sem aula | `RN-AGE-10` | Mantido |  |
| `G:RF-AGE-10` | Editar ou desfazer dia sem aula | `RF-AGE-05`, `RN-AGE-10` | Mantido |  |
| `G:RF-AGE-11` | Criar aula avulsa | `RF-AGE-04` | Mantido |  |
| `G:RN-AGE-01` | Agenda sem presença nem chamada | `RN-TUR-06` | Mantido |  |
| `G:RN-AGE-02` | Aulas nascem das turmas | `RN-AGE-01` | Mantido |  |
| `G:RN-AGE-03` | Situações da aula pelo relógio | `RN-AGE-03` | Mantido |  |
| `G:RN-AGE-04` | Troca na aula não muda titular | `RN-AGE-04` | Mantido |  |
| `G:RN-AGE-05` | Cancelamento exige motivo | `RN-AGE-05` | Mantido |  |
| `G:RN-AGE-06` | Decidir a experimental antes de cancelar | `RN-AGE-06` | Mantido |  |
| `G:RN-AGE-07` | Dia sem aula avisado uma vez | `RN-AGE-10` | Mantido |  |
| `G:RN-AGE-08` | Aula concluída não é editada | `RN-AGE-08` | Alterado | Administrador corrige professor de aulas dos últimos 30 dias. |
| `G:RN-AGE-09` | Professor altera só suas aulas | `RN-AGE-07` | Alterado | Depende também da permissão da unidade. |
| `G:RF-EXP-01` | Lista de aulas experimentais | `RF-AEX-02` | Mantido |  |
| `G:RF-EXP-02` | Abas Hoje, Próximas, Realizadas, Canceladas | `RF-AEX-02` | Alterado | Novas abas Pedidos e Sem retorno. |
| `G:RF-EXP-03` | Filtros por nome, tipo e período | `RF-AEX-02` | Mantido |  |
| `G:RF-EXP-04` | Destacar pedidos novos do site | `RF-AEX-02` | Alterado | Pedidos chegam pelo link da unidade ou pelo diretório. |
| `G:RF-EXP-05` | Agendar experimental numa aula | `RF-AEX-03` | Mantido |  |
| `G:RF-EXP-06` | Confirmação pelo WhatsApp | `RF-AEX-03` | Mantido |  |
| `G:RF-EXP-07` | Painel com WhatsApp, Reagendar, Cancelar | `RF-AEX-04` | Mantido |  |
| `G:RF-EXP-08` | Estado de lista vazia | `RNF-25` | Absorvido |  |
| `G:RN-EXP-01` | Pedido vem do site ou da gestão | `RN-AEX-03`, `20` (Dados) | Alterado | Origens: link da unidade, diretório ou gestão. |
| `G:RN-EXP-02` | Experimental marcada em aula existente | `RF-AEX-03` | Mantido |  |
| `G:RN-EXP-03` | Situações Agendada, Confirmada, Realizada, Cancelada | `20` (Situações) | Alterado | Novas situações: Pedido recebido, Não compareceu, Matriculado e Sem retorno. |
| `G:RN-EXP-04` | Experimental não é aluno | `RN-AEX-05` | Mantido |  |
| `G:RN-EXP-05` | "Registrar como aluno" não desenvolver | `RN-AEX-06` | Alterado | Passa a existir e abre a matrícula preenchida. |
| `G:RN-EXP-06` | Resultado da experimental não registrado | `RN-AEX-06`, `RN-AEX-08` | Alterado | Registra "Não compareceu" e "Matriculado". |
| `G:RN-EXP-07` | Aula cancelada: reagendar ou cancelar | `RN-AEX-07` | Mantido |  |
| `G:RF-BEN-01` | Lista de descontos individuais | `RF-BEN-01` | Alterado | Lista única de benefícios (bolsa e desconto). |
| `G:RF-BEN-02` | Lista de bolsas | `RF-BEN-01` | Alterado | Lista única de benefícios (bolsa e desconto). |
| `G:RF-BEN-03` | Conceder benefício | `RF-BEN-02` | Mantido |  |
| `G:RF-BEN-04` | Prévia da nova mensalidade | `RF-BEN-02` | Mantido |  |
| `G:RF-BEN-05` | Encerrar benefício | `RF-BEN-03` | Mantido |  |
| `G:RF-BEN-06` | Aviso de combinação não definida | `RN-BEN-01` | Retirado | Não existe combinação: um benefício por modalidade. |
| `G:RN-BEN-01` | Desconto em reais; bolsa 30%, 50% ou integral | `RN-BEN-02` | Alterado | Bolsa com percentual livre de 1 a 100%. |
| `G:RN-BEN-02` | Sem desconto por faixa | `RN-BEN-02` | Mantido |  |
| `G:RN-BEN-03` | Benefício guarda motivo e quem decidiu | `RN-BEN-03` | Mantido |  |
| `G:RN-BEN-04` | Vale a partir da próxima cobrança | `RN-BEN-04` | Mantido |  |
| `G:RN-BEN-05` | Validade sem prazo ou com data | `RN-BEN-03`, `RN-BEN-04` | Mantido |  |
| `G:RN-BEN-06` | Bolsa integral não gera cobrança | `RN-BEN-05`, `RN-COB-04` | Mantido |  |
| `G:RN-BEN-07` | Combinação de benefícios a definir | `RN-BEN-01` | Retirado | No máximo um benefício por modalidade; sem regra de combinação. |
| `G:RN-BEN-08` | Benefícios guardados na pausa | — | Retirado | A situação Pausado não existe. |
| `G:RF-MEN-01` | Lista de cobranças do mês | `RF-COB-01` | Mantido |  |
| `G:RF-MEN-02` | Filtros por mês, status, aluno e turma | `RF-COB-01` | Mantido |  |
| `G:RF-MEN-03` | Destaque das mensalidades em atraso | `RF-COB-01`, `RF-COB-06` | Mantido |  |
| `G:RF-MEN-04` | Painel com composição do valor | `RF-COB-02` | Mantido |  |
| `G:RF-MEN-05` | Ações do painel da cobrança | `RF-COB-02` | Mantido |  |
| `G:RF-MEN-06` | Registrar pagamento manual | `RF-COB-04`, `RN-COB-12` | Mantido |  |
| `G:RF-MEN-07` | Gerar cobranças do mês com revisão | `RF-COB-03`, `RN-COB-02` | Alterado | Geração automática diária, com prévia. |
| `G:RF-MEN-08` | Decidir casos antes de gerar | — | Retirado | Sem combinação de benefícios; a geração é automática. |
| `G:RF-MEN-09` | Total previsto e distribuição por vencimento | `RF-COB-03` | Mantido |  |
| `G:RF-MEN-10` | Lista de mensalidades em atraso | `RF-COB-06` | Mantido |  |
| `G:RF-MEN-11` | Lembrete aos atrasados selecionados | `RF-COB-06` | Alterado | Por e-mail e WhatsApp com mensagem pronta. |
| `G:RF-MEN-12` | Configurar lembretes automáticos | `RN-COB-17`, `RF-COB-09` | Mantido |  |
| `G:RF-MEN-13` | Financeiro do aluno | `RF-ALU-03` | Mantido |  |
| `G:RF-MEN-14` | Alterar uma cobrança com motivo | `RN-COB-09` | Alterado | Vira renegociação, com parcelamento em até 6 vezes. |
| `G:RF-MEN-15` | Erro ao carregar sem alterar cobranças | `RNF-25`, `RNF-24` | Absorvido |  |
| `G:RN-MEN-01` | Uma cobrança mensal por aluno ativo | `RN-COB-01` | Alterado | Uma mensalidade por modalidade ativa da matrícula. |
| `G:RN-MEN-02` | Valor e composição guardados na geração | `RN-COB-03` | Mantido |  |
| `G:RN-MEN-03` | Sem cobrança: bolsa integral, pausado, inativo | `RN-COB-04` | Alterado | Sem Pausado; regra vale por modalidade. |
| `G:RN-MEN-04` | Geração manual mensal sem duplicar | `RN-COB-02`, `RN-COB-05` | Alterado | Geração automática diária e idempotente. |
| `G:RN-MEN-05` | Situações da cobrança | `10` (Situações) | Alterado | Novas situações: Em conferência, Cancelada, Renegociada e Estornada. |
| `G:RN-MEN-06` | Pagamento pelo portal e manual geram comprovante | `RN-PAG-10`, `RN-COB-12`, `RN-COB-16` | Mantido |  |
| `G:RN-MEN-07` | Sem pagamento parcial, juros ou multa | `RN-COB-07` | Mantido |  |
| `G:RN-MEN-08` | Lembretes do menor vão ao responsável | `RN-COB-18` | Mantido |  |
| `G:RN-MEN-09` | Lembretes param com o pagamento | `RN-COB-19` | Mantido |  |
| `G:RN-MEN-10` | Lembrete por WhatsApp depende de integração | `RN-COB-17`, `RF-COB-06` | Alterado | WhatsApp automático fora do escopo; SMS antes do bloqueio. |
| `G:RN-MEN-11` | Cobrança do primeiro mês a definir | `RN-MAT-05`, `RN-MAT-07` | Alterado | Primeiro mês sempre cheio, com vencimento na matrícula. |
| `G:RN-MEN-12` | Alterações e pagamentos no histórico | `RN-AUD-02` | Mantido |  |
| `G:RF-DES-01` | Lista de despesas | — | Retirado | Despesas fora da v1. |
| `G:RF-DES-02` | Previsto, pago e a pagar no mês | — | Retirado | Despesas fora da v1. |
| `G:RF-DES-03` | Filtros de despesas | — | Retirado | Despesas fora da v1. |
| `G:RF-DES-04` | Painel da despesa | — | Retirado | Despesas fora da v1. |
| `G:RF-DES-05` | Cadastrar e editar despesa | — | Retirado | Despesas fora da v1. |
| `G:RF-DES-06` | Marcar despesa como paga | — | Retirado | Despesas fora da v1. |
| `G:RF-DES-07` | Anexar boleto da despesa | — | Retirado | Despesas fora da v1. |
| `G:RN-DES-01` | Situações da despesa | — | Retirado | Despesas fora da v1. |
| `G:RN-DES-02` | Despesa mensal recorrente | — | Retirado | Despesas fora da v1. |
| `G:RN-DES-03` | Categorias de despesa | — | Retirado | Despesas fora da v1. |
| `G:RN-DES-04` | Despesa sem ligação com aluno | — | Retirado | Despesas fora da v1. |
| `G:RF-FIN-01` | Indicadores do mês, incluindo despesas | `RF-INI-01`, `RF-INI-02`, `RF-REL-01` | Alterado | Sem despesas; números no Início e em Relatórios. |
| `G:RF-FIN-02` | Próximas cobranças e movimentações | `RF-COB-01`, `RF-COB-08` | Alterado | Sem saídas; lista de cobranças e recebimentos do dia. |
| `G:RF-FIN-03` | Indicador leva à tela do assunto | `RN-INI-01` | Mantido |  |
| `G:RN-FIN-01` | Tela de consulta de Mensalidades e Despesas | `RN-INI-01`, `RN-REL-01` | Alterado | Sem Despesas. |
| `G:RN-FIN-02` | Poucos indicadores; gráficos em Relatórios | `RN-INI-02` | Mantido |  |
| `G:RF-INI-01` | Números do dia e recebido no mês | `RF-INI-02` | Alterado | Quatro números: ativos, recebido, em atraso e experimentais da semana. |
| `G:RF-INI-02` | Aulas de hoje | `RF-INI-02` | Mantido |  |
| `G:RF-INI-03` | Atalho "Avisar turma" em cada aula | `RF-INI-02` | Mantido |  |
| `G:RF-INI-04` | Lista "Precisa de atenção" | `RF-INI-02`, `15` (Precisa de atenção) | Mantido |  |
| `G:RN-INI-01` | Início só com o que vira ação | `RN-INI-02` | Mantido |  |
| `G:RN-INI-02` | Início lê os outros módulos | `RN-INI-01` | Mantido |  |
| `G:RN-INI-03` | Item resolvido sai da lista | `RN-INI-03` | Mantido |  |
| `G:RF-PRF-01` | Início do professor com aulas de hoje | `RF-INI-03` | Mantido |  |
| `G:RF-PRF-02` | Aviso de aula coberta de outro professor | `RF-INI-03` | Mantido |  |
| `G:RF-PRF-03` | Suas turmas e graduações registradas | `RF-INI-03` | Alterado | Professor não registra graduação. |
| `G:RF-PRF-04` | Atalhos "Registrar graduação" e "Avisar uma turma" | `RN-COM-01` | Alterado | Sem "Registrar graduação"; avisar depende da permissão. |
| `G:RF-PRF-05` | "Meus alunos" com filtros | `RF-ALU-01`, `02` (Permissões) | Mantido |  |
| `G:RF-PRF-06` | Painel do aluno para o professor | `RF-ALU-03` | Alterado | Sem "Registrar graduação"; contatos dependem da permissão. |
| `G:RF-PRF-07` | Bloco "Seu acesso" | `RF-AJU-01` | Absorvido |  |
| `G:RN-PRF-01` | Professor vê só suas turmas ou coberturas | `RN-PRO-06`, `02` (Permissões) | Mantido |  |
| `G:RN-PRF-02` | Professor sem dados financeiros nem edição | `02` (Permissões) | Mantido |  |
| `G:RN-PRF-03` | Professor sem Financeiro, Relatórios e Configurações | `02` (Permissões), `RN-REL-06` | Alterado | Vê o próprio resumo em Relatórios. |
| `G:RN-PRF-04` | Professor gradua, comunica e altera aulas | `RN-GRA-03`, `RN-COM-01`, `RN-AGE-07` | Alterado | Não registra graduação; demais ações dependem de permissão. |
| `G:RF-NOT-01` | Sino com contador de não lidas | `RN-NOT-01` | Mantido |  |
| `G:RF-NOT-02` | Painel de notificações ao lado do menu | `RF-NOT-01` | Mantido |  |
| `G:RF-NOT-03` | Grupos Hoje e Esta semana; filtros | `RF-NOT-01` | Mantido |  |
| `G:RF-NOT-04` | Notificação com ação que resolve | `RF-NOT-01` | Mantido |  |
| `G:RF-NOT-05` | Marcar todas como lidas | `RF-NOT-01` | Mantido |  |
| `G:RF-NOT-06` | Estado "Tudo lido" | `RF-NOT-01` | Mantido |  |
| `G:RN-NOT-01` | O que gera notificação | `RN-NOT-04` | Alterado | Lista ampliada; "turma sem grupo" saiu. |
| `G:RN-NOT-02` | Professor só recebe das suas turmas | `RN-NOT-03` | Mantido |  |
| `G:RN-NOT-03` | Notificação não substitui "Precisa de atenção" | `RN-NOT-01` | Mantido |  |
| `G:RF-BUS-01` | Abrir busca pelo campo ou Ctrl K | `RF-BUS-01` | Mantido |  |
| `G:RF-BUS-02` | Buscar telas, ações e pessoas | `RF-BUS-01` | Mantido |  |
| `G:RF-BUS-03` | Busca sem acento e por palavras relacionadas | `RF-BUS-01` | Mantido |  |
| `G:RF-BUS-04` | Campo vazio mostra telas e ações | `RF-BUS-02` | Mantido |  |
| `G:RF-BUS-05` | Escolher resultado por teclado ou mouse | `RF-BUS-01`, `RNF-20` | Mantido |  |
| `G:RF-BUS-06` | Nada encontrado sugere outra busca | `RNF-25` | Absorvido |  |
| `G:RN-BUS-01` | Busca respeita o perfil | `RF-BUS-01` | Mantido |  |
| `G:RN-BUS-02` | Pessoa abre a lista com ela selecionada | `RF-BUS-01` | Alterado | Escolher uma pessoa abre a ficha dela. |
| `G:RF-REL-01` | Três relatórios mensais | `RF-REL-01`, `RF-REL-02` | Alterado | Relatórios ampliados: financeiro, alunos, experimentais e aulas. |
| `G:RF-REL-02` | Escolha do período | `RN-REL-02` | Mantido |  |
| `G:RF-REL-03` | Gráfico ou tabela | `RN-REL-04` | Mantido |  |
| `G:RF-REL-04` | Números do mês ao apontar | `RN-REL-04` | Absorvido |  |
| `G:RF-REL-05` | Mês em andamento marcado | `RN-REL-03` | Mantido |  |
| `G:RF-REL-06` | Desde quando há dados; importar anteriores | `RF-IMP-01` | Absorvido |  |
| `G:RN-REL-01` | Relatórios mensais e só leitura | `RN-REL-01` | Mantido |  |
| `G:RN-REL-02` | "Em atraso" no fim de cada mês | `RF-REL-01` | Mantido |  |
| `G:RN-REL-03` | Ativos no último dia, entradas e saídas | `RF-REL-02` | Mantido |  |
| `G:RN-REL-04` | Só números somados | `RN-REL-01` | Mantido |  |
| `G:RN-REL-05` | Só o administrador acessa relatórios | `RN-REL-05`, `RN-REL-06` | Alterado | Coordenador vê consolidado; Professor vê o próprio resumo. |
| `G:RN-REL-06` | Gráficos sem depender de cor, com tabela | `RN-REL-04` | Mantido |  |
| `G:RF-HIS-01` | Lista de registros de auditoria | `RF-AUD-01` | Mantido |  |
| `G:RF-HIS-02` | Filtros por período, usuário e área | `RF-AUD-01` | Mantido |  |
| `G:RF-HIS-03` | Painel com antes e depois | `RF-AUD-02` | Mantido |  |
| `G:RF-HIS-04` | Faixa antes e depois nas graduações | `RF-AUD-02` | Absorvido |  |
| `G:RF-HIS-05` | Registrar ações do site e do portal | `RN-AUD-02` | Mantido |  |
| `G:RF-HIS-06` | Exportar o histórico | `RF-AUD-03` | Mantido |  |
| `G:RF-HIS-07` | Carregar registros antigos sob demanda | `RNF-02` | Absorvido |  |
| `G:RN-HIS-01` | Histórico não editável nem apagável | `RN-AUD-01` | Mantido |  |
| `G:RN-HIS-02` | Exportação gera registro | `RN-EXP-04` | Mantido |  |
| `G:RN-HIS-03` | Só o administrador consulta | `RN-AUD-05` | Alterado | Coordenador vê tudo; Administrador, só suas unidades. |
| `G:RN-HIS-04` | Origem: gestão, portal ou site | `RN-AUD-02` | Mantido |  |
| `G:RF-IMP-01` | Importação em quatro etapas | `RN-IMP-02`, `RF-IMP-02` | Mantido |  |
| `G:RF-IMP-02` | Modelo de planilha por tipo | `RF-IMP-01` | Mantido |  |
| `G:RF-IMP-03` | Ligar colunas aos campos | `RN-IMP-02` | Mantido |  |
| `G:RF-IMP-04` | Revisão com problemas por linha | `RN-IMP-02` | Mantido |  |
| `G:RF-IMP-05` | Guardar linhas com problema para depois | `RN-IMP-03` | Alterado | Linhas com erro saem num relatório para baixar. |
| `G:RF-IMP-06` | Importar alunos, pagamentos, despesas e graduações | `RF-IMP-01` | Alterado | Sem despesas e pagamentos; entram professores, turmas, planos e dívidas. |
| `G:RF-IMP-07` | Exportar de Alunos, Mensalidades, Despesas e outros | `RN-EXP-01` | Alterado | Toda lista exporta; despesas não existem. |
| `G:RF-IMP-08` | Escolher conteúdo, formato e colunas | `RN-EXP-01` | Alterado | Só CSV, com filtros e colunas visíveis. |
| `G:RF-IMP-09` | Aviso de colunas com dados pessoais | `RN-EXP-01` | Mantido |  |
| `G:RF-IMP-10` | Tela central de importar e exportar | `RF-IMP-01` | Mantido |  |
| `G:RF-IMP-11` | Últimas importações e exportações | `RF-IMP-01` | Mantido |  |
| `G:RF-IMP-12` | Cópia completa pedindo senha | `RN-EXP-03` | Mantido |  |
| `G:RF-IMP-13` | Baixar dados de um aluno | `RF-LGP-01` | Mantido |  |
| `G:RN-IMP-01` | Só o administrador importa e exporta | `RN-IMP-12`, `RN-EXP-02` | Alterado | Coordenador também; pacote completo só o Coordenador. |
| `G:RN-IMP-02` | Revisão antes de gravar | `RN-IMP-02` | Mantido |  |
| `G:RN-IMP-03` | Registros existentes ignorados (nome e nascimento) | `RN-IMP-04` | Alterado | CPF identifica; sem CPF, possível duplicado para decidir. |
| `G:RN-IMP-04` | Valor da mensalidade não é importado | `RN-COB-03` | Mantido |  |
| `G:RN-IMP-05` | Pagamentos importados como histórico | `RN-IMP-07` | Alterado | Importa dívidas em aberto, não pagamentos anteriores. |
| `G:RN-IMP-06` | Graduações importadas sem aviso | `RN-IMP-09` | Mantido |  |
| `G:RN-IMP-07` | Colunas pessoais desmarcadas na exportação | `RN-EXP-01` | Alterado | A tela avisa antes; sem colunas desmarcadas. |
| `G:RN-IMP-08` | Exportação de relatórios só com totais | `RN-REL-01`, `RN-REL-07` | Mantido |  |
| `G:RN-IMP-09` | Importação e exportação no histórico | `RN-IMP-12`, `RN-EXP-04` | Mantido |  |
| `G:RN-IMP-10` | Cópia completa exige senha | `RN-EXP-03`, `RN-ACE-21` | Mantido |  |
| `G:RF-INT-01` | Três integrações com situação | `RF-PAG-01` | Alterado | Só pagamentos têm tela própria, por unidade. |
| `G:RF-INT-02` | Remetente dos e-mails | `RN-COM-10` | Alterado | Remetente fixo: nome da unidade no domínio da plataforma. |
| `G:RF-INT-03` | Turmas com grupo de WhatsApp | — | Retirado | Sem tela de grupos; o link fica na turma. |
| `G:RF-INT-04` | Escolher e configurar provedor de pagamento | `RF-PAG-01`, `RN-PAG-01` | Alterado | Mercado Pago por unidade, conectado por OAuth. |
| `G:RF-INT-05` | "Pagar" no portal e baixa automática | `RN-PAG-10`, `RF-PAG-02` | Mantido |  |
| `G:RN-INT-01` | Provedor de pagamento a definir | `RN-PAG-01` | Alterado | Decidido: Mercado Pago. |
| `G:RN-INT-02` | Código de pagamento atrás de interface | `RN-PAG-03` | Mantido |  |
| `G:RN-INT-03` | WhatsApp manual com mensagem pronta | `RN-COM-03` | Mantido |  |
| `G:RN-INT-04` | Credenciais protegidas e ocultas | `RNF-08`, `RN-PAG-02` | Mantido |  |
| `G:RF-AJU-01` | Bloco de ajuda por módulo | `RF-AJU-01` | Alterado | Central com artigos curtos por tela. |
| `G:RF-AJU-02` | Explicação e link por submódulo | `RF-AJU-01`, `RF-AJU-02` | Alterado | Artigo por tela e ícone "?". |
| `G:RF-AJU-03` | Explicar menu, atalhos e áreas externas | `RF-AJU-01` | Absorvido |  |
| `G:RN-AJU-01` | Ajuda é conteúdo fixo | `RF-AJU-01` | Mantido |  |
| `G:RN-AJU-02` | Professor não vê links sem acesso | `RF-AJU-01` | Mantido |  |

## Portal (P:)

| Código antigo | Resumo | Código(s) novo(s) | Situação | Nota |
| --- | --- | --- | --- | --- |
| `P:RNF-P01` | Portal no computador e celular, sem app | `RNF-19`, `RN-POR-10` | Mantido |  |
| `P:RNF-P02` | Larguras de referência 1440 e 390 px | `RNF-19` | Alterado | Referência de 360 px a telas grandes. |
| `P:RNF-P03` | Português simples para alunos e pais | `RNF-24` | Mantido |  |
| `P:RNF-P04` | Área de toque de 44 px | `RNF-20` | Mantido |  |
| `P:RNF-P05` | Acessibilidade nível AA | `RNF-20` | Mantido |  |
| `P:RNF-P06` | Diferenças não dependem só de cor | `RNF-20` | Mantido |  |
| `P:RNF-P07` | Telas em até 2 segundos no celular | `RNF-01` | Mantido |  |
| `P:RNF-P08` | Confirmação do Pix sem recarregar | `RN-PAG-06` | Mantido |  |
| `P:RNF-P09` | Login, senha com hash e HTTPS | `RNF-04` | Mantido |  |
| `P:RNF-P10` | Conta vê só alunos vinculados | `RN-POR-01` | Mantido |  |
| `P:RNF-P11` | Dados de cartão não passam pelo servidor | `RNF-09`, `RN-PAG-08` | Mantido |  |
| `P:RNF-P12` | Sessão com "Manter conectado" | `RN-ACE-19` | Alterado | Sem "Manter conectado"; sessão de 30 dias. |
| `P:RNF-P13` | LGPD e dados de menores | `RN-LGP-03`, `RN-LGP-04` | Mantido |  |
| `P:RNF-P14` | Ações do portal na auditoria | `RN-AUD-02` | Mantido |  |
| `P:RNF-P15` | Pagamento não se repete | `RNF-12`, `RN-PAG-11` | Mantido |  |
| `P:RNF-P16` | Provedor fora do ar não derruba o portal | `RN-PAG-17` | Alterado | Portal passa a mostrar só a chave Pix da unidade. |
| `P:RNF-P17` | Erros dizem se algo foi cobrado | `RNF-24`, `RF-PAG-04` | Mantido |  |
| `P:RNF-P18` | Mesma API e regras da gestão | `RN-POR-02` | Mantido |  |
| `P:RNF-P19` | Teste automatizado por regra | `RNF-29` | Mantido |  |
| `P:RNF-P20` | Código de pagamento isolado | `RN-PAG-03` | Mantido |  |
| `P:RNF-P21` | Navegadores atuais | `RNF-19` | Mantido |  |
| `P:RNF-P22` | Formatos brasileiros e fuso de Brasília | `RNF-23` | Alterado | Horário no fuso da unidade. |
| `P:RNF-P23` | E-mails legíveis no celular | `RNF-28` | Mantido |  |
| `P:RF-POR-01` | Mesmo login leva cada conta à sua área | `RN-ACE-03` | Mantido |  |
| `P:RF-POR-02` | Barra com abas, sino e quem está logado | `RF-POR-01` | Alterado | Nova aba Avisos; logo da organização. |
| `P:RF-POR-03` | No celular, abas embaixo | `RF-POR-01` | Mantido |  |
| `P:RF-POR-04` | Sair do portal | `RF-ACE-04` | Absorvido |  |
| `P:RF-POR-05` | Atualizar último acesso | `RF-ACE-07` | Mantido |  |
| `P:RF-POR-06` | Estados carregando, vazio e erro | `RF-POR-08` | Mantido |  |
| `P:RF-POR-07` | Sem sessão, vai ao login | `RN-ACE-16` | Mantido |  |
| `P:RN-POR-01` | Conta vê só os alunos vinculados | `RN-POR-01` | Mantido |  |
| `P:RN-POR-02` | Acesso só por convite da gestão | `RN-ACE-04` | Alterado | Também pelo autocadastro aprovado. |
| `P:RN-POR-03` | Professor que também é aluno (a definir) | `RN-PRO-07`, `RN-ACE-02` | Alterado | Decidido: mesma conta, com dois vínculos. |
| `P:RN-POR-04` | Mesmas funções no computador e celular | `RNF-19` | Mantido |  |
| `P:RN-POR-05` | Nome, logo e cor da academia | `RNF-22` | Mantido |  |
| `P:RN-POR-06` | Aluno pausado continua entrando | — | Retirado | A situação Pausado não existe. |
| `P:RN-POR-07` | Acesso do aluno inativo (a definir) | `RN-INA-08`, `RN-INA-09` | Alterado | Decidido: portal reduzido; ex-aluno por 12 meses. |
| `P:RF-RSP-01` | Faixa "Você está vendo" com cada aluno | `RN-POR-05` | Mantido |  |
| `P:RF-RSP-02` | Escolher aluno muda todas as abas | `RN-POR-05` | Mantido |  |
| `P:RF-RSP-03` | Total em aberto da família | `RN-POR-05` | Mantido |  |
| `P:RF-RSP-04` | Títulos com o nome do aluno | `RN-POR-05` | Absorvido |  |
| `P:RF-RSP-05` | Barra identifica o Responsável | `RF-POR-01` | Mantido |  |
| `P:RF-RSP-06` | Responsável paga qualquer aluno vinculado | `RN-RSP-02`, `RN-POR-05` | Mantido |  |
| `P:RN-RSP-01` | Responsável faz tudo pelo menor | `RN-LGP-03`, `RN-ACE-06` | Mantido |  |
| `P:RN-RSP-02` | Avisos e cobranças do menor ao responsável | `RN-RSP-02`, `RN-COB-18` | Alterado | Cobranças só ao responsável financeiro; recibo a quem pagou. |
| `P:RN-RSP-03` | Responsável com vários alunos | `RN-RSP-03` | Mantido |  |
| `P:RN-RSP-04` | Mais de um responsável (a definir) | `RN-RSP-02` | Alterado | Decidido: até dois responsáveis com acesso. |
| `P:RN-RSP-05` | Conta própria do menor (a definir) | `RN-ACE-06` | Alterado | Decidido: menor não tem conta. |
| `P:RN-RSP-06` | Vínculo responsável–aluno só na gestão | `RN-RSP-05` | Mantido |  |
| `P:RN-RSP-07` | "Minhas informações" do aluno escolhido | `RN-POR-05` | Mantido |  |
| `P:RF-MIN-01` | Topo com nome, situação e turma | `RF-POR-07` | Mantido |  |
| `P:RF-MIN-02` | Dados pessoais | `RF-POR-07` | Mantido |  |
| `P:RF-MIN-03` | Treinos e outros horários | `RF-POR-07` | Mantido |  |
| `P:RF-MIN-04` | Plano, benefícios e mensalidade | `RF-POR-07` | Mantido |  |
| `P:RF-MIN-05` | Graduação atual e histórico | `RF-POR-07` | Mantido |  |
| `P:RF-MIN-06` | Ligar e desligar e-mails de avisos e lembretes | `RN-COM-13` | Alterado | Lembrete de cobrança não pode ser desligado. |
| `P:RF-MIN-07` | Editar dados de contato | `RF-POR-07`, `RN-ALU-11` | Mantido |  |
| `P:RN-MIN-01` | Aluno não altera turma, plano ou graduação | `RN-POR-07` | Mantido |  |
| `P:RN-MIN-02` | Dados editáveis pelo aluno (a definir) | `RN-ALU-11` | Alterado | Decidido: telefone, endereço, foto e emergência; e-mail com confirmação. |
| `P:RN-MIN-03` | Graduação só informada, sem progresso | `RN-POR-09` | Mantido |  |
| `P:RN-MIN-04` | Sem presença nem frequência | `RN-POR-09` | Mantido |  |
| `P:RN-MIN-05` | Desligar e-mail não desliga o portal | `RN-COM-03` | Mantido |  |
| `P:RN-MIN-06` | Graduações importadas aparecem se marcadas | `RN-IMP-09`, `RF-POR-07` | Alterado | Sem opção na importação; o histórico importado aparece sempre. |
| `P:RN-MIN-07` | Alteração do aluno na auditoria, origem portal | `RN-ALU-11` | Mantido |  |
| `P:RF-AGP-01` | Semana em grade | `RF-POR-04` | Alterado | No celular, lista do dia. |
| `P:RF-AGP-02` | Aula com horário, turma e professor | `RF-POR-04` | Mantido |  |
| `P:RF-AGP-03` | Legenda de tipos de aula | `RF-POR-04` | Mantido |  |
| `P:RF-AGP-04` | Marcar a aula de hoje | `RF-POR-04` | Absorvido |  |
| `P:RF-AGP-05` | "Todos os horários" ou "Só minha turma" | `RF-POR-04` | Mantido |  |
| `P:RF-AGP-06` | Trocar de semana | `RF-POR-04` | Mantido |  |
| `P:RF-AGP-07` | Aula alterada com "Ver aviso" | `RF-POR-04` | Mantido |  |
| `P:RF-AGP-08` | Aula cancelada e dia sem aula | `RF-POR-04` | Mantido |  |
| `P:RN-AGP-01` | Só turmas em que o aluno treina | `RF-POR-04`, `RN-AGE-12` | Mantido |  |
| `P:RN-AGP-02` | Agenda só para consulta | `RF-POR-04` | Mantido |  |
| `P:RN-AGP-03` | Sem presença nem frequência | `RN-POR-09` | Mantido |  |
| `P:RN-AGP-04` | Tipos de aula não só por cor | `RNF-20` | Mantido |  |
| `P:RN-AGP-05` | Alterações da gestão aparecem na hora | `RN-POR-02` | Absorvido |  |
| `P:RN-AGP-06` | Experimental de outra pessoa não aparece | `RN-POR-01` | Absorvido |  |
| `P:RF-AVI-01` | Lista de avisos com detalhes | `RF-POR-06` | Mantido |  |
| `P:RF-AVI-02` | Não lidos destacados e contagem no sino | `RF-POR-06`, `RF-POR-01` | Mantido |  |
| `P:RF-AVI-03` | Filtros por turma e tipo de aviso | `RF-POR-06` | Alterado | Filtros Todos, Não lidos e Por modalidade. |
| `P:RF-AVI-04` | Marcar todos como lidos | `RF-POR-06` | Mantido |  |
| `P:RF-AVI-05` | Como o aluno recebe avisos | `RF-POR-07` | Alterado | Preferências ficam em Minhas informações. |
| `P:RF-AVI-06` | Abrir aviso pelo atalho da agenda ou Início | `RF-POR-02`, `RF-POR-04` | Mantido |  |
| `P:RN-AVI-01` | Recebe avisos das suas turmas e gerais | `RN-COM-02` | Mantido |  |
| `P:RN-AVI-02` | Portal sempre; e-mail por preferência; WhatsApp externo | `RN-COM-03` | Mantido |  |
| `P:RN-AVI-03` | Sem resposta nem chat | `RN-COM-08` | Mantido |  |
| `P:RN-AVI-04` | Aluno não apaga avisos | `RN-COM-08` | Mantido |  |
| `P:RN-AVI-05` | Lido ao clicar ou "Marcar todos" | `RN-COM-08` | Alterado | "Marcar todos como lidos" não conta como leitura. |
| `P:RN-AVI-06` | No menor, o responsável lê | `RN-COM-08` | Mantido |  |
| `P:RF-FNP-01` | Mensalidade em aberto com "Pagar" | `RF-POR-05` | Mantido |  |
| `P:RF-FNP-02` | Composição do valor | `RF-POR-05` | Mantido |  |
| `P:RF-FNP-03` | Histórico por ano | `RF-POR-05` | Mantido |  |
| `P:RF-FNP-04` | Baixar comprovante | `RF-POR-05`, `RN-COB-16` | Mantido |  |
| `P:RF-FNP-05` | Atrasada antes da mensalidade do mês | `RF-POR-05` | Mantido |  |
| `P:RF-FNP-06` | "Em dia" com próximo vencimento | `RF-POR-02` | Mantido |  |
| `P:RN-FNP-01` | Cada conta vê só suas cobranças | `RN-POR-01` | Mantido |  |
| `P:RN-FNP-02` | Portal não recalcula valores | `RN-POR-02` | Mantido |  |
| `P:RN-FNP-03` | Mesmas situações da gestão | `RN-POR-02`, `10` (Situações) | Alterado | Novas situações: Em conferência, Cancelada, Renegociada e Estornada. |
| `P:RN-FNP-04` | Aluno não altera valor nem vencimento | `RN-POR-07` | Mantido |  |
| `P:RN-FNP-05` | Pagamento manual aparece com comprovante | `RN-COB-16` | Mantido |  |
| `P:RN-FNP-06` | Sem juros nem multa | `RN-COB-07` | Mantido |  |
| `P:RF-PGO-01` | Janela de pagamento | `RF-PAG-02` | Mantido |  |
| `P:RF-PGO-02` | Pix, cartão de crédito ou boleto | `RN-PAG-04` | Alterado | Inclui cartão de débito; boleto só quando o aluno pede. |
| `P:RF-PGO-03` | Pix com QR Code e validade | `RF-PAG-03`, `RN-PAG-06` | Mantido |  |
| `P:RF-PGO-04` | Pagamento com cartão, resposta na hora | `RF-PAG-04` | Mantido |  |
| `P:RF-PGO-05` | Boleto com vencimento no dia | `RF-PAG-05`, `RN-PAG-07` | Alterado | Vence com a cobrança, ou 3 dias úteis se já vencida. |
| `P:RF-PGO-06` | Pagamento confirmado com comprovante | `RF-PAG-06` | Mantido |  |
| `P:RF-PGO-07` | Pagamento não aprovado | `RF-PAG-04` | Mantido |  |
| `P:RF-PGO-08` | Informar quem processa o pagamento | `RF-PAG-02` | Mantido |  |
| `P:RF-PGO-09` | Baixa automática no portal e na gestão | `RN-PAG-10` | Mantido |  |
| `P:RN-PGO-01` | Provedor de pagamento a definir | `RN-PAG-01` | Alterado | Decidido: Mercado Pago, na conta de cada unidade. |
| `P:RN-PGO-02` | Não guarda número nem código do cartão | `RN-PAG-08` | Mantido |  |
| `P:RN-PGO-03` | Baixa só com confirmação do provedor | `RN-PAG-10` | Mantido |  |
| `P:RN-PGO-04` | Confirmação repetida não duplica | `RN-PAG-11` | Mantido |  |
| `P:RN-PGO-05` | Cobrança paga não é paga de novo | `RN-PAG-11` | Mantido |  |
| `P:RN-PGO-06` | Pagamento sempre do valor inteiro | `RN-COB-07` | Mantido |  |
| `P:RN-PGO-07` | Pix vale 30 minutos | `RN-PAG-06` | Mantido |  |
| `P:RN-PGO-08` | Boleto aguarda compensação | `RN-PAG-07`, `RN-PAG-10` | Mantido |  |
| `P:RN-PGO-09` | Pagamentos e recusas na auditoria e notificação | `RN-PAG-13` | Mantido |  |
| `P:RN-PGO-10` | Lembretes param com o pagamento | `RN-COB-19` | Mantido |  |
| `P:RN-PGO-11` | Aluno ou responsável paga; comprovante a quem pagou | `RN-RSP-02`, `RF-PAG-06` | Mantido |  |
| `P:RN-PGO-12` | Quem arca com a taxa (a definir) | `RN-PAG-05` | Alterado | Decidido: taxa repassada ao aluno, informada pela unidade. |
| `P:RF-INP-01` | Data e saudação | `RF-POR-02` | Absorvido |  |
| `P:RF-INP-02` | Próxima aula | `RF-POR-02` | Alterado | Mostra as próximas 3 aulas. |
| `P:RF-INP-03` | Aviso de aula alterada | `RF-POR-02` | Mantido |  |
| `P:RF-INP-04` | Mensalidade com "Pagar agora" | `RF-POR-02` | Mantido |  |
| `P:RF-INP-05` | "Tudo em dia" com próximo vencimento | `RF-POR-02` | Mantido |  |
| `P:RF-INP-06` | Avisos recentes | `RF-POR-02` | Alterado | Mostra avisos não lidos e fixados. |
| `P:RF-INP-07` | Graduação com selo "Nova" | `RF-POR-02`, `RN-GRA-10` | Mantido |  |
| `P:RF-INP-08` | Responsável vê o Início do aluno escolhido | `RN-POR-05` | Mantido |  |
| `P:RN-INP-01` | Início sem dados próprios | `RF-POR-02`, `RN-POR-02` | Mantido |  |
| `P:RN-INP-02` | Só o que é do aluno e vira ação | `RF-POR-02` | Mantido |  |
| `P:RN-INP-03` | Sem gráficos, frequência ou progresso | `RN-POR-09` | Mantido |  |
| `P:RN-INP-04` | Mensalidade atrasada aparece primeiro | `RF-POR-02` | Mantido |  |
| `P:RN-INP-05` | Cartão leva à aba do assunto | `RF-POR-02` | Mantido |  |

## Site e acesso (S:)

| Código antigo | Resumo | Código(s) novo(s) | Situação | Nota |
| --- | --- | --- | --- | --- |
| `S:RNF-S01` | Site e acesso no computador e celular | `RNF-19` | Mantido |  |
| `S:RNF-S02` | Larguras de referência 1440 e 390 px | `RNF-19` | Alterado | Referência de 360 px a telas grandes. |
| `S:RNF-S03` | Português simples para leigos e pais | `RNF-24` | Mantido |  |
| `S:RNF-S04` | Área de toque de 44 px | `RNF-20` | Mantido |  |
| `S:RNF-S05` | Acessibilidade nível AA | `RNF-20` | Mantido |  |
| `S:RNF-S06` | Preenchimento automático do navegador | `RF-ACE-01` | Absorvido |  |
| `S:RNF-S07` | Site em até 2,5 segundos | `RNF-03` | Mantido |  |
| `S:RNF-S08` | Login em até 2 segundos | `RNF-01` | Mantido |  |
| `S:RNF-S09` | Site não carrega gestão nem portal | `RNF-03`, `RN-SIT-05` | Mantido |  |
| `S:RNF-S10` | HTTPS e senha com hash | `RNF-04`, `RN-ACE-09` | Mantido |  |
| `S:RNF-S11` | Links de convite e senha seguros e únicos | `RN-ACE-13`, `RN-ACE-14` | Mantido |  |
| `S:RNF-S12` | Mensagens não revelam e-mail cadastrado | `RN-ACE-11` | Mantido |  |
| `S:RNF-S13` | Limite de tentativas | `RN-ACE-10`, `RNF-10` | Mantido |  |
| `S:RNF-S14` | Sessão em cookie protegido | `RNF-07` | Mantido |  |
| `S:RNF-S15` | Proteção contra envio forjado | `RNF-07` | Mantido |  |
| `S:RNF-S16` | Consulta pública só com campos exibidos | `RN-DIR-07` | Alterado | Vale para a consulta do diretório. |
| `S:RNF-S17` | LGPD no formulário de pedido | `RN-AEX-04`, `RN-AEX-10` | Mantido |  |
| `S:RNF-S18` | Clique duplo não repete ação | `RNF-12` | Absorvido |  |
| `S:RNF-S19` | Falha de e-mail é tentada de novo | `RNF-14` | Mantido |  |
| `S:RNF-S20` | Falha de horários ou equipe esconde seção | — | Retirado | O site não tem seções de horários e equipe. |
| `S:RNF-S21` | Mensagens de erro claras | `RNF-24` | Mantido |  |
| `S:RNF-S22` | Mesma autenticação de gestão e portal | `RN-ACE-01`, `RN-POR-02` | Mantido |  |
| `S:RNF-S23` | Conteúdo separado do layout | `RN-SIT-04` | Alterado | Textos mantidos pela equipe da plataforma, não pela academia. |
| `S:RNF-S24` | Teste automatizado por regra | `RNF-29` | Mantido |  |
| `S:RNF-S25` | Navegadores atuais | `RNF-19` | Mantido |  |
| `S:RNF-S26` | Formatos brasileiros e fuso de Brasília | `RNF-23` | Alterado | Horário no fuso da unidade. |
| `S:RNF-S27` | Site em buscadores; acesso não indexado | `RN-SIT-05`, `RN-SIT-06` | Mantido |  |
| `S:RNF-S28` | E-mails legíveis no celular | `RNF-28` | Mantido |  |
| `S:RF-LGN-01` | Login com "Manter conectado" | `RF-ACE-01`, `RN-ACE-19` | Alterado | Sem "Manter conectado". |
| `S:RF-LGN-02` | "Mostrar" e "Ocultar" senha | `RF-ACE-01` | Mantido |  |
| `S:RF-LGN-03` | Conta vai direto à sua área | `RN-ACE-03` | Alterado | Com mais de um vínculo, a pessoa escolhe no modal. |
| `S:RF-LGN-04` | Mesma mensagem para qualquer erro | `RN-ACE-11` | Mantido |  |
| `S:RF-LGN-05` | "Esqueci minha senha" | `RF-ACE-01`, `RF-ACE-03` | Mantido |  |
| `S:RF-LGN-06` | Orientação sobre convite no primeiro acesso | `RF-ACE-02` | Absorvido |  |
| `S:RF-LGN-07` | "Voltar ao site da academia" | — | Retirado | Não existe site da academia. |
| `S:RF-LGN-08` | Sem sessão, login e volta à tela pedida | `RN-ACE-16` | Mantido |  |
| `S:RF-LGN-09` | Com sessão, login leva à área | `RN-ACE-16` | Mantido |  |
| `S:RF-LGN-10` | Entrada atualiza último acesso | `RF-ACE-07` | Mantido |  |
| `S:RN-LGN-01` | Login único para gestão e portal | `RN-ACE-01` | Mantido |  |
| `S:RN-LGN-02` | Sem cadastro aberto; só convite | `RN-ACE-04` | Alterado | Também autocadastro aprovado e cadastro da organização. |
| `S:RN-LGN-03` | Área definida pelo tipo da conta | `RN-ACE-02`, `RN-ACE-03` | Alterado | A área vem do vínculo; uma conta pode ter gestão e portal. |
| `S:RN-LGN-04` | E-mail único, sem diferença de maiúsculas | `RN-ACE-01` | Mantido |  |
| `S:RN-LGN-05` | Um e-mail por papel (a definir) | `RN-ACE-02` | Alterado | Decidido: mesma conta com vários vínculos. |
| `S:RN-LGN-06` | Conta inativa ou sem aceite não entra | `RN-ACE-12` | Mantido |  |
| `S:RN-LGN-07` | Sessão com "Manter conectado" | `RN-ACE-19` | Alterado | Sem "Manter conectado"; 30 dias (aluno) e 12 horas (gestão). |
| `S:RN-LGN-08` | Bloqueio após tentativas com erro | `RN-ACE-10` | Mantido |  |
| `S:RN-LGN-09` | "Criar minha senha" só com convite | `RF-ACE-02` | Mantido |  |
| `S:RN-LGN-10` | Pausado entra; inativo conforme Portal | `RN-INA-08`, `RN-INA-09` | Alterado | Sem Pausado; inativo vê o portal reduzido. |
| `S:RF-PAC-01` | Tela do convite com nome, e-mail e validade | `RF-ACE-02` | Mantido |  |
| `S:RF-PAC-02` | Campos Nova senha e Confirmar senha | `RF-ACE-02` | Mantido |  |
| `S:RF-PAC-03` | Exigências marcadas ao digitar | `RF-ACE-02` | Mantido |  |
| `S:RF-PAC-04` | Aviso de senhas diferentes | `RF-ACE-02` | Mantido |  |
| `S:RF-PAC-05` | Botão só com senha válida e igual | `RF-ACE-02` | Mantido |  |
| `S:RF-PAC-06` | Opção de avisos por e-mail marcada | `RN-COM-13` | Alterado | Sai da tela; avisos começam ligados e mudam no portal. |
| `S:RF-PAC-07` | Concluir ativa conta e entra | `RF-ACE-02` | Mantido |  |
| `S:RF-PAC-08` | "Já tenho senha" leva ao login | `RF-ACE-02` | Absorvido |  |
| `S:RF-PAC-09` | Convite vencido, usado ou inválido | `RF-ACE-02` | Mantido |  |
| `S:RF-PAC-10` | Painel lateral com resumo do portal | — | Retirado | Painel não consta na tela de criar senha. |
| `S:RN-PAC-01` | Senha de 8 caracteres com letra e número | `RN-ACE-08` | Alterado | Exige letra maiúscula e recusa senhas vazadas. |
| `S:RN-PAC-02` | Convite de uso único com prazo | `RN-ACE-13` | Alterado | Prazo definido: 7 dias. |
| `S:RN-PAC-03` | Reenviar convite cancela o anterior | `RN-ACE-13` | Mantido |  |
| `S:RN-PAC-04` | E-mail não muda nesta tela | `RF-ACE-02` | Mantido |  |
| `S:RN-PAC-05` | Academia não define nem vê senha | `RN-ACE-09` | Mantido |  |
| `S:RN-PAC-06` | Texto próprio para contas da gestão | `RF-ACE-02` | Absorvido |  |
| `S:RN-PAC-07` | Avisos alteráveis; lembrete começa ligado | `RN-COM-13` | Alterado | Lembrete de cobrança não pode ser desligado. |
| `S:RN-PAC-08` | Aceite muda a conta para Ativa | `RF-ACE-06` | Mantido |  |
| `S:RN-PAC-09` | Responsável cria uma senha para todos | `RN-RSP-03` | Mantido |  |
| `S:RF-SEN-01` | Formulário de recuperação de senha | `RF-ACE-03` | Mantido |  |
| `S:RF-SEN-02` | "Voltar para o login" | `RF-ACE-03` | Absorvido |  |
| `S:RF-SEN-03` | "Confira seu e-mail" sem revelar conta | `RN-ACE-11` | Mantido |  |
| `S:RF-SEN-04` | Link abre tela de nova senha | `RF-ACE-03` | Mantido |  |
| `S:RF-SEN-05` | Nova senha entra direto | `RF-ACE-03` | Mantido |  |
| `S:RF-SEN-06` | E-mail avisa troca de senha | `RN-ACE-20` | Mantido |  |
| `S:RF-SEN-07` | Link vencido ou usado | `RF-ACE-03`, `RN-ACE-14` | Absorvido |  |
| `S:RN-SEN-01` | Mesma resposta, exista ou não a conta | `RN-ACE-11` | Mantido |  |
| `S:RN-SEN-02` | Link de uso único e curto (prazo a definir) | `RN-ACE-14` | Alterado | Prazo definido: 1 hora. |
| `S:RN-SEN-03` | Novo pedido cancela o link anterior | `RN-ACE-14` | Mantido |  |
| `S:RN-SEN-04` | Trocar senha encerra outras sessões | `RN-ACE-20` | Mantido |  |
| `S:RN-SEN-05` | Inativa não recebe; pendente recebe convite | `RN-ACE-14` | Mantido |  |
| `S:RN-SEN-06` | Limite de pedidos por conta e aparelho | `RN-ACE-10` | Mantido |  |
| `S:RN-SEN-07` | Academia não redefine senha | `RN-ACE-09` | Mantido |  |
| `S:RN-SEN-08` | Troca de senha na auditoria, sem a senha | `RN-AUD-02`, `RN-AUD-03` | Mantido |  |
| `S:RN-SEN-09` | Trocar senha logado não desenhado | `RF-ACE-04` | Alterado | "Minha conta" permite trocar a senha logado. |
| `S:RF-SIT-01` | Site da academia sem login | `RN-SIT-01` | Retirado | Não existe site da academia; só o da plataforma. |
| `S:RF-SIT-02` | Cabeçalho com links e botões | `RN-SIT-01` | Retirado | Não existe site da academia. |
| `S:RF-SIT-03` | Topo com frase e botões | `RN-SIT-01` | Retirado | Não existe site da academia. |
| `S:RF-SIT-04` | Faixa de números da academia | `RN-SIT-01` | Retirado | Não existe site da academia. |
| `S:RF-SIT-05` | Seção "Sobre nós" | `RN-SIT-01` | Retirado | Não existe site da academia. |
| `S:RF-SIT-06` | Seção "Modalidades" | `RN-SIT-01` | Retirado | Não existe site da academia. |
| `S:RF-SIT-07` | Seção "Dúvidas" | `RN-SIT-01` | Retirado | Não existe site da academia. |
| `S:RF-SIT-08` | Falar pelo WhatsApp após dúvidas | `RN-SIT-01` | Retirado | Não existe site da academia. |
| `S:RF-SIT-09` | Chamada final "Quero fazer uma aula" | `RN-SIT-01` | Retirado | Não existe site da academia. |
| `S:RF-SIT-10` | Rodapé com contatos e "Área do aluno" | `RN-SIT-01` | Retirado | Não existe site da academia. |
| `S:RF-SIT-11` | Botões de WhatsApp da academia | `RN-SIT-01` | Retirado | Não existe site da academia. |
| `S:RF-SIT-12` | "Site publicado" desligado | `RN-SIT-01` | Retirado | Não existe site da academia. |
| `S:RF-SIT-13` | Botão "Ver site" nas configurações | `RN-SIT-01` | Retirado | Não existe site da academia. |
| `S:RN-SIT-01` | Site público sem dados internos | `RN-SIT-01` | Retirado | Não existe site da academia. |
| `S:RN-SIT-02` | Dados do site vêm das configurações | `RN-SIT-01` | Retirado | Não existe site da academia. |
| `S:RN-SIT-03` | Origem dos números do topo (a definir) | `RN-SIT-01` | Retirado | Não existe site da academia. |
| `S:RN-SIT-04` | Só números e textos confirmados | `RN-SIT-01` | Retirado | Não existe site da academia. |
| `S:RN-SIT-05` | Cinco modalidades do desenho (a definir) | `RN-SIT-01`, `RN-MOD-05` | Retirado | Sem site da academia; Kids e No-Gi viram nome de turma. |
| `S:RN-SIT-06` | Textos a preencher pela academia | `RN-SIT-01` | Retirado | Não existe site da academia. |
| `S:RN-SIT-07` | "Entrar" e "Área do aluno" levam ao login | `RN-SIT-01` | Retirado | Não existe site da academia. |
| `S:RN-SIT-08` | Site desligado não afeta login | `RN-SIT-01` | Retirado | Não existe site da academia. |
| `S:RN-SIT-09` | Site sem fotos | `RN-SIT-01` | Retirado | Não existe site da academia. |
| `S:RF-HEQ-01` | Quadro de horários tipo × período | `RN-DIR-04` | Alterado | Só no diretório, com a grade normal da semana. |
| `S:RF-HEQ-02` | No celular, escolher período | — | Retirado | Sem quadro por período no site. |
| `S:RF-HEQ-03` | Grade atualizada pela academia | — | Retirado | Sem seção de horários no site. |
| `S:RF-HEQ-04` | Cartões da equipe | `RN-DIR-04`, `RN-PRO-04` | Alterado | Só no diretório, com consentimento do professor. |
| `S:RF-HEQ-05` | Mudanças da gestão aparecem sozinhas | `RN-DIR-04` | Absorvido |  |
| `S:RF-HEQ-06` | "Mostrar horários" e "Mostrar equipe" | `RN-DIR-02`, `RN-PRO-04` | Alterado | Substituídos por "Aparecer no diretório" e consentimento. |
| `S:RF-HEQ-07` | Célula vazia sem erro | — | Retirado | Sem quadro tipo × período. |
| `S:RN-HEQ-01` | Horários e equipe vêm dos cadastros | `RN-DIR-04` | Mantido |  |
| `S:RN-HEQ-02` | Mostra a grade normal da semana | `RN-DIR-04` | Mantido |  |
| `S:RN-HEQ-03` | Só turmas e professores ativos | `RN-DIR-04`, `RN-PRO-05` | Mantido |  |
| `S:RN-HEQ-04` | Várias turmas na mesma célula | — | Retirado | Sem quadro tipo × período. |
| `S:RN-HEQ-05` | Faixa atual do cadastro do professor | `RN-PRO-03` | Mantido |  |
| `S:RN-HEQ-06` | Funções do desenho inexistentes (a definir) | `RN-PRO-04` | Alterado | Professor tem apresentação de até 300 caracteres. |
| `S:RN-HEQ-07` | Ordem e destaque da equipe (a definir) | — | Retirado | Sem seção de equipe no site. |
| `S:RN-HEQ-08` | Sem telefone ou e-mail do professor | `RN-PRO-04`, `RN-DIR-04` | Mantido |  |
| `S:RN-HEQ-09` | Professor consente em aparecer | `RN-PRO-04` | Mantido |  |
| `S:RN-HEQ-10` | Sem nomes ou contagem de alunos | `RN-DIR-04` | Mantido |  |
| `S:RF-EXS-01` | Cabeçalho "Voltar ao site" e "Entrar" | — | Retirado | Não existe site da academia para voltar. |
| `S:RF-EXS-02` | Pedido explicado em três passos | `RF-AEX-01` | Mantido |  |
| `S:RF-EXS-03` | Campos nome e WhatsApp | `RN-AEX-04` | Mantido |  |
| `S:RF-EXS-04` | "Para mim" ou "Para meu filho ou filha" | `RN-AEX-04` | Mantido |  |
| `S:RF-EXS-05` | Modalidade com "Ainda não sei" | `RF-AEX-01` | Mantido |  |
| `S:RF-EXS-06` | Períodos preferidos | `RF-AEX-01` | Mantido |  |
| `S:RF-EXS-07` | Mensagem opcional | `RF-AEX-01` | Mantido |  |
| `S:RF-EXS-08` | "Enviar pedido" confere e grava | `RF-AEX-01` | Mantido |  |
| `S:RF-EXS-09` | "Pedido enviado" e "Enviar outro pedido" | `RF-AEX-01` | Mantido |  |
| `S:RF-EXS-10` | Dados usados só para combinar a aula | `RN-AEX-10` | Alterado | Exige aceite da política de privacidade. |
| `S:RF-EXS-11` | WhatsApp para falar direto | `RF-AEX-01` | Mantido |  |
| `S:RF-EXS-12` | Pedido aparece em Aulas experimentais | `RF-AEX-02` | Mantido |  |
| `S:RF-EXS-13` | Pedido gera notificação e auditoria | `RN-NOT-04`, `RN-AUD-02` | Mantido |  |
| `S:RF-EXS-14` | Falha mantém dados e oferece WhatsApp | `RF-AEX-01` | Mantido |  |
| `S:RN-EXS-01` | Pedido não marca a aula | `RN-AEX-03` | Mantido |  |
| `S:RN-EXS-02` | Sem vagas, datas ou confirmação automática | `RN-AEX-03` | Mantido |  |
| `S:RN-EXS-03` | Confirmação pela academia no WhatsApp | `RN-AEX-03` | Mantido |  |
| `S:RN-EXS-04` | Quem pede não vira aluno | `RN-AEX-05`, `RN-AEX-06` | Alterado | "Registrar como aluno" e autocadastro passam a existir. |
| `S:RN-EXS-05` | Nome e WhatsApp obrigatórios | `RN-AEX-04` | Mantido |  |
| `S:RN-EXS-06` | Modalidades dependem de Kids e No-Gi | `RN-MOD-03`, `RN-MOD-05` | Alterado | Modalidades ativas do catálogo; variações no nome da turma. |
| `S:RN-EXS-07` | Pedido de criança feito por adulto | `RN-AEX-04` | Mantido |  |
| `S:RN-EXS-08` | Formulário pede só o necessário | `RN-AEX-04` | Mantido |  |
| `S:RN-EXS-09` | Guarda de pedidos não convertidos (a definir) | `RN-AEX-09`, `RN-LGP-07` | Alterado | Decidido: anonimização em 6 meses (padrão). |
| `S:RN-EXS-10` | Contato por WhatsApp lançado na gestão | `RN-AEX-03` | Mantido |  |
| `S:RN-EXS-11` | Site desligado tira o formulário do ar | `RF-AUT-03` | Alterado | O link é ligado e desligado em Configurações › Unidade. |
| `S:RN-EXS-12` | Barreira contra envios automáticos | `RN-AEX-10`, `RNF-10` | Alterado | Desafio invisível no lugar do campo escondido. |

## Evolução para SaaS (E:)

| Código antigo | Resumo | Código(s) novo(s) | Situação | Nota |
| --- | --- | --- | --- | --- |
| `E:MUL-01` | Identificador da academia em toda tabela | `RN-ORG-01` | Alterado | Organização em toda tabela e unidade nas operacionais. |
| `E:MUL-02` | Filtro na camada de dados | `RN-ORG-02` | Mantido |  |
| `E:MUL-03` | Arquivos separados por academia | `RN-ORG-03` | Mantido |  |
| `E:MUL-04` | Testes provam isolamento entre academias | `RNF-31` | Mantido |  |
| `E:MUL-05` | Ações sensíveis na auditoria | `RN-AUD-02` | Mantido |  |
| `E:MUL-06` | Exportar todos os dados, incluindo despesas | `RN-EXP-03` | Alterado | Pacote completo sem despesas. |
| `E:MUL-07` | Restaurar uma academia sem afetar outras | `RNF-17` | Absorvido |  |
| `E:CON-01` | Uma conta por pessoa, pelo e-mail | `RN-ACE-01` | Mantido |  |
| `E:CON-02` | Vínculo de conta, academia e papel | `RN-ACE-02` | Alterado | Novo papel Coordenador; vínculo inclui unidades. |
| `E:CON-03` | Vários vínculos na mesma conta | `RN-ACE-02` | Mantido |  |
| `E:CON-04` | Modal para escolher academia e área | `RN-ACE-03`, `RF-ACE-05` | Mantido |  |
| `E:CON-05` | Cada responsável com o próprio convite | `RN-RSP-06` | Mantido |  |
| `E:CON-06` | Aluno menor sem conta | `RN-ACE-06` | Mantido |  |
| `E:CON-07` | Inativo consulta pelo prazo configurado | `RN-INA-08`, `RN-INA-09` | Alterado | Inativo vê portal reduzido; ex-aluno por 12 meses. |
| `E:CON-08` | Pelo menos um Administrador ativo | `RN-ORG-07` | Alterado | Pelo menos um por unidade ativa. |
| `E:CON-09` | Administrador da plataforma separado | `17` (Papéis) | Alterado | Vira Equipe da plataforma: Dono e Suporte. |
| `E:CON-10` | Academias da mesma conta independentes | `RN-ORG-08` | Retirado | Existe hierarquia organização › unidades. |
| `E:CON-11` | Sem hierarquia nem visão somada | `RN-ORG-08` | Retirado | Existe visão consolidada para o Coordenador. |
| `E:API-01` | Contrato OpenAPI como referência única | `RNF-32` | Mantido |  |
| `E:API-02` | Rotas por sprint; mock enquanto faltar | `00` §9 | Absorvido |  |
| `E:API-03` | Pagamento online depende de webhooks | `RN-PAG-10` | Mantido |  |
| `E:CAD-01` | Proteção contra cadastros automáticos | `RN-CAD-01` | Mantido |  |
| `E:CAD-02` | CNPJ ou CPF repetido gera aviso | `RN-CAD-02` | Mantido |  |
| `E:CAD-03` | E-mail existente cria só o vínculo | `RN-CAD-03` | Alterado | O vínculo criado é de Coordenador. |
| `E:CAD-04` | Ativa só após o primeiro pagamento | `RN-CAD-04` | Mantido |  |
| `E:CAD-05` | Respostas do assistente editáveis depois | `RN-CAD-06` | Mantido |  |
| `E:CAD-06` | Aceite dos termos registrado | `RN-LGP-01` | Mantido |  |
| `E:CAD-07` | Assistente retoma de onde parou | `RN-CAD-05` | Mantido |  |
| `E:CAD-08` | Telefone confirmado por código | `RF-CAD-02` | Mantido |  |
| `E:CAD-09` | Sem segundo reembolso por CNPJ ou CPF | `RN-CAD-02` | Mantido |  |
| `E:ASS-01` | Definição de aluno ativo | `RN-ASS-01` | Mantido |  |
| `E:ASS-02` | Vale o maior número do ciclo | `RN-ASS-02` | Mantido |  |
| `E:ASS-03` | Pacotes de 50, 100 e 150 alunos | `RN-ASS-03`, `16` (Preço) | Alterado | Acima de 150, mais R$ 40 a cada 50 alunos. |
| `E:ASS-04` | Sem teste gratuito | `RN-ASS-11` | Mantido |  |
| `E:ASS-05` | Cobrança pela conta da plataforma | `RN-ASS-06` | Mantido |  |
| `E:ASS-06` | Só o Administrador acessa a assinatura | `RN-ASS-09` | Alterado | Só o Coordenador vê e paga. |
| `E:ASS-07` | Tela com ativos, valor e faturas | `RF-ASS-01` | Mantido |  |
| `E:ASS-08` | Pagar fatura e cancelar assinatura | `RF-ASS-02`, `RF-ASS-03` | Mantido |  |
| `E:ASS-17` | Avisos 7 e 1 dia antes do vencimento | `RN-ASS-07` | Mantido |  |
| `E:ASS-18` | Aviso na gestão e por e-mail | `RN-ASS-07` | Mantido |  |
| `E:ASS-19` | Sem aviso se a fatura está paga | `RN-ASS-07` | Mantido |  |
| `E:ASS-09` | Pagamento devolve acesso na hora | `RN-ASS-08` | Mantido |  |
| `E:ASS-10` | Nenhuma situação apaga dados antes do prazo | `RN-ASS-16` | Mantido |  |
| `E:ASS-11` | Mudanças de situação na auditoria e painel | `RN-ASS-17` | Mantido |  |
| `E:ASS-12` | Todas as funções desde o primeiro dia | `16` (Situações) | Absorvido |  |
| `E:ASS-13` | Reembolso em até 7 dias | `RN-ASS-11` | Mantido |  |
| `E:ASS-14` | Reembolso uma vez por CNPJ ou CPF | `RN-ASS-11` | Mantido |  |
| `E:ASS-15` | Conta nova nasce vazia | `RN-ASS-12` | Mantido |  |
| `E:ASS-16` | Reembolso só nos Termos de uso | `RN-ASS-11`, `RN-SIT-03` | Mantido |  |
| `E:PNL-01` | Acesso restrito com duas etapas | `RN-ACE-17`, `17` (Papéis) | Alterado | Papéis Dono e Suporte, ambos com duas etapas. |
| `E:PNL-02` | Lista de academias e situações | `RF-PNL-02` | Alterado | Novas colunas e situações (ex.: Cortesia, "Em risco"). |
| `E:PNL-03` | Detalhe da academia | `RF-PNL-03` | Mantido |  |
| `E:PNL-04` | Ações do painel | `RF-PNL-04` | Mantido |  |
| `E:PNL-05` | Indicadores do painel | `RF-PNL-01` | Mantido |  |
| `E:PNL-06` | Acesso de suporte sem autorização | `RN-PNL-02`, `RN-PNL-03` | Alterado | Alterar exige liberação do Coordenador. |
| `E:PNL-07` | Modelos, faixas e categorias de despesa | `RF-PNL-07`, `RF-PNL-05` | Alterado | Sem categorias de despesa; faixas no catálogo. |
| `E:PNL-08` | Avisos da plataforma às academias | `RN-PNL-09`, `RF-PNL-08` | Mantido |  |
| `E:PNL-09` | Listas só com números | `RN-PNL-01` | Mantido |  |
| `E:PNL-10` | Alerta de cadastro de quem teve reembolso | `RN-PNL-05` | Mantido |  |
| `E:IMP-01` | Modelos em XLSX e CSV | `RN-IMP-01` | Mantido |  |
| `E:IMP-02` | Tipos de dado da primeira versão | `RF-IMP-01` | Mantido |  |
| `E:IMP-03` | Validação linha a linha e relatório | `RN-IMP-03` | Mantido |  |
| `E:IMP-04` | Duplicados por CPF ou e-mail | `RN-IMP-04` | Alterado | Mesmo CPF ganha só a nova modalidade; sem CPF, decidir. |
| `E:IMP-05` | Importação em lote com autor | `RN-IMP-10` | Mantido |  |
| `E:IMP-06` | Desfazer lote sem movimentação | `RN-IMP-10` | Mantido |  |
| `E:IMP-07` | Importados sem convite automático | `RN-IMP-08` | Mantido |  |
| `E:IMP-08` | Faixas confirmadas antes de importar | `RN-IMP-09` | Mantido |  |
| `E:IMP-09` | Só o Administrador importa | `RN-IMP-12` | Alterado | O Coordenador também importa. |
| `E:IMP-10` | Arquivo apagado em até 30 dias | `RN-IMP-11` | Mantido |  |
| `E:IMP-11` | Sem integração direta com outros sistemas | `RN-IMP-01` | Absorvido |  |
| `E:SIT-01` | Páginas do site da plataforma | `RN-SIT-02` | Alterado | Inclui diretório, demonstração e conferir recibo. |
| `E:SIT-02` | Conteúdo fixo no repositório | `RN-SIT-04` | Mantido |  |
| `E:SIT-03` | Páginas pré-renderizadas com prévia | `RN-SIT-05` | Mantido |  |
| `E:SIT-04` | Primeira parte em até 2,5 segundos | `RN-SIT-05`, `RNF-03` | Mantido |  |
| `E:SIT-05` | Código de cada área separado | `RN-SIT-05` | Mantido |  |
| `E:SIT-06` | Nome e logo da academia no app | `RNF-22`, `RF-ORG-01` | Mantido |  |
| `E:SIT-07` | Domínio em nome da empresa | `RN-SIT-08` | Mantido |  |
| `E:LNK-01` | Link público de aula experimental | `RF-AEX-01`, `RN-AUT-01` | Alterado | Por unidade, com link de autocadastro ao lado. |
| `E:LNK-02` | Nome e WhatsApp obrigatórios | `RN-AEX-04` | Mantido |  |
| `E:LNK-03` | Faixas etárias e modalidades da academia | `RN-MOD-03`, `RN-MOD-07` | Mantido |  |
| `E:LNK-04` | Pedido entra em Aulas experimentais | `RN-AEX-05`, `RF-AEX-02` | Mantido |  |
| `E:LNK-05` | Proteção e aceite da política | `RN-AEX-10` | Mantido |  |
| `E:LNK-06` | Copiar e desligar o link | `RF-AUT-03` | Mantido |  |
| `E:LNK-07` | Prazo de guarda do pedido | `RN-AEX-09` | Mantido |  |
| `E:DEM-01` | Demonstração pública em /demo | `RN-DEM-01` | Mantido |  |
| `E:DEM-02` | Roda no navegador com academia fictícia | `RN-DEM-02` | Alterado | Organização fictícia com duas unidades. |
| `E:DEM-03` | Duas entradas: administrador e aluno | `RN-DEM-03` | Alterado | Quatro entradas: Coordenador, Administrador, Professor e Aluno. |
| `E:DEM-04` | Alterações somem ao recarregar | `RN-DEM-04` | Mantido |  |
| `E:DEM-05` | Faixa de demonstração e "Criar minha academia" | `RN-DEM-05` | Mantido |  |
| `E:DEM-06` | Pagamento, e-mail e WhatsApp simulados | `RN-DEM-06` | Mantido |  |
| `E:DEM-07` | Mocks carregados só na rota | `RN-DEM-07` | Mantido |  |
| `E:DEM-08` | Contagem de aberturas e cliques | `RN-DEM-08` | Mantido |  |
| `E:LGP-01` | Aceite com data, versão e conta | `RN-LGP-01` | Mantido |  |
| `E:LGP-02` | Dados de menores e consentimento | `RN-LGP-03` | Mantido |  |
| `E:LGP-03` | Exportar dados do aluno a pedido | `RN-LGP-04` | Alterado | O próprio aluno também baixa pelo portal. |
| `E:LGP-04` | Excluir ou anonimizar a pedido | `RN-LGP-05`, `RN-LGP-06` | Mantido |  |
| `E:LGP-05` | Experimentais anonimizadas após o prazo | `RN-LGP-07` | Mantido |  |
| `E:LGP-06` | Exportação após saída por 90 dias | `RN-LGP-09` | Alterado | Prazo passou para 30 dias. |
| `E:LGP-07` | Canal de contato de privacidade | `RN-SIT-02` | Mantido |  |
| `E:LGP-08` | Procedimento para incidente | `RN-LGP-12` | Mantido |  |
| `E:LGP-09` | Suporte segue a regra PNL-06 | `RN-LGP-10`, `RN-PNL-03` | Alterado | Suporte só altera com liberação do Coordenador. |
