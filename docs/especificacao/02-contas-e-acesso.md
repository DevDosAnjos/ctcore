# 02 · Contas, acesso e permissões

Entrega 1 · Fase 2 · Depende de: 01 · Código: ACE

## Objetivo

Uma conta por pessoa, um login único para gestão e portal, convites, segurança da conta e a tabela de permissões de todos os perfis.

## Dados

- **Conta:** e-mail (único na plataforma, sem diferença entre maiúsculas e minúsculas), hash da senha (Argon2), nome, telefone, situação (Convite enviado, Ativa, Desativada), duas etapas (ligada ou não, segredo criptografado, códigos de reserva com hash), último acesso.
- **Vínculo:** conta, organização, papel (Coordenador, Administrador, Professor, Aluno, Responsável), unidades (para Administrador e Professor), situação, data de início e fim.
- **Sessão:** conta, aparelho (navegador e sistema), IP, início, último uso, validade.
- **Convite:** conta, vínculo, código (hash), validade, usado em, cancelado em.
- **Tentativa de acesso:** e-mail, IP, data, resultado.

## Regras

### Conta e vínculos

- **RN-ACE-01.** Uma conta por pessoa, identificada pelo e-mail, válida em toda a plataforma.
- **RN-ACE-02.** A mesma conta pode ter vários vínculos, na mesma organização ou em outras. Exemplo: professor em uma unidade e aluno em outra organização. Não existe segunda conta para segundo papel.
- **RN-ACE-03.** Quem tem um vínculo só vai direto para a sua área depois do login. Quem tem mais de um escolhe organização e área num modal logo após o login, e pode reabri-lo pelo menu sem novo login.
- **RN-ACE-04.** Não existe cadastro aberto para gestão nem portal. Toda conta nasce de um convite, do autocadastro aprovado (`20`) ou do cadastro da organização (`18`).
- **RN-ACE-05.** Convites em cascata: o Coordenador convida Administradores; o Administrador convida Professores da própria unidade; o convite do portal sai ao concluir a matrícula (ver `09`).
- **RN-ACE-06.** Aluno menor de idade não tem conta. O acesso é do responsável. Aos 18 anos, o sistema avisa o Administrador e o responsável, e a academia decide se convida o aluno.
- **RN-ACE-07.** Se o e-mail convidado já tem conta, o convite cria só o novo vínculo, e a pessoa aceita com a senha que já tem.

### Senha e login

- **RN-ACE-08.** Senha com 8 ou mais caracteres, pelo menos 1 número e 1 letra maiúscula, sem símbolo obrigatório. Senhas presentes em vazamentos conhecidos são recusadas ("Essa senha é muito conhecida. Escolha outra.").
- **RN-ACE-09.** A senha é guardada com hash Argon2. Ninguém, nem a plataforma, lê a senha. A academia não define nem redefine a senha de ninguém.
- **RN-ACE-10.** 5 senhas erradas seguidas travam aquele e-mail por 15 minutos. Há também limite por IP. O login e os formulários públicos usam desafio invisível (Turnstile), que só aparece se o acesso parecer de robô.
- **RN-ACE-11.** Login e recuperação de senha mostram sempre a mesma mensagem, sem revelar se o e-mail existe: "E-mail ou senha incorretos." e "Se esse e-mail tiver uma conta, você vai receber um link em instantes."
- **RN-ACE-12.** Conta com convite não aceito ou desativada não entra; a mensagem é a mesma do erro de senha.
- **RN-ACE-13.** Convite vale 7 dias e é de uso único. Reenviar cancela o anterior. Máximo de 3 reenvios por dia por pessoa.
- **RN-ACE-14.** Link de nova senha vale 1 hora, é de uso único, e um novo pedido cancela o anterior. Conta desativada não recebe link; conta com convite pendente recebe o convite de novo.
- **RN-ACE-15.** Troca de e-mail só vale depois de confirmada pelo link enviado ao endereço novo (válido 24 horas). O endereço antigo recebe um aviso.
- **RN-ACE-16.** Quem abre um link interno sem sessão faz login e cai na tela pedida. Quem já tem sessão e abre o login vai para a sua área.

### Duas etapas

- **RN-ACE-17.** Verificação em duas etapas por app autenticador (TOTP). Obrigatória para Coordenador e equipe da plataforma, que configuram no primeiro acesso. Opcional para Administrador, Professor, Aluno e Responsável.
- **RN-ACE-18.** Ao ativar, a pessoa recebe 10 códigos de reserva de uso único. Sem o celular e sem códigos, abre chamado; o suporte confirma a identidade (documento e dados da organização) antes de desativar. Não existe código por e-mail.

### Sessão

- **RN-ACE-19.** Sessão sem uso expira em 30 dias para contas que só têm vínculos de Aluno ou Responsável e em 12 horas quando a área aberta é a gestão ou o painel da plataforma. Não existe "Manter conectado".
- **RN-ACE-20.** Trocar a senha, desativar a conta ou remover o último vínculo de gestão encerra na hora todas as sessões em outros aparelhos. A pessoa recebe e-mail avisando da troca de senha.
- **RN-ACE-21.** Ações críticas pedem a senha de novo (e o código de duas etapas, se ligado): transferir coordenação, cancelar assinatura, exportação completa, desconectar o Mercado Pago, anonimizar aluno, desativar unidade.
- **RN-ACE-22.** Login em aparelho novo gera e-mail "Novo acesso à sua conta", com aparelho, cidade aproximada e hora.

## Requisitos

- **RF-ACE-01.** Login: e-mail, senha com "Mostrar"/"Ocultar", "Entrar", "Esqueci minha senha". Antes do login aparece o suporte da plataforma; depois, o contato da unidade.
- **RF-ACE-02.** Criar senha pelo convite: nome e e-mail já preenchidos, validade do convite, exigências da senha marcadas enquanto digita, "Criar senha e entrar" só habilitado com a senha válida e igual nos dois campos. Convite vencido, usado ou cancelado explica e orienta pedir outro.
- **RF-ACE-03.** Recuperação de senha (RN-ACE-11, RN-ACE-14) e tela de nova senha com as mesmas exigências. Depois de criar, a pessoa entra direto.
- **RF-ACE-04.** Minha conta (gestão e portal): trocar senha estando logado, trocar e-mail, ligar e desligar duas etapas, ver códigos de reserva novos, lista de sessões ativas com "Encerrar" e "Encerrar todas as outras".
- **RF-ACE-05.** Modal de escolha de organização e área (RN-ACE-03).
- **RF-ACE-06.** Configurações › Usuários: lista da equipe com perfil, unidades, último acesso e situação; convidar, reenviar convite, alterar unidades, desativar. O Coordenador gere Administradores; o Administrador gere Professores das suas unidades.
- **RF-ACE-07.** Atualizar o último acesso a cada login.
- **RF-ACE-08.** Tela "Esta área não está disponível para o seu perfil", com atalhos para o Início, quando alguém abre um endereço fora do seu alcance.

## Permissões

`●` pode · `○` só consulta · `—` não acessa · `U` só nas suas unidades · `T` só nas suas turmas · `*` depende da permissão da unidade

| Área / ação | Coordenador | Administrador | Professor |
| --- | --- | --- | --- |
| Início da unidade | ● | U | T |
| "Todas as unidades" (consolidado) | ○ | — | — |
| Alunos: ver lista e ficha | ● | U | T (sem dados financeiros, CPF ou endereço) |
| Alunos: contatos e emergência | ● | U | T* (ver contatos) |
| Alunos: cadastrar, editar, matricular, encerrar | ● | U | — |
| Benefícios | ● | U | — |
| Planos | ● | U | — |
| Cobranças e pagamentos | ● | U | — |
| Estorno, renegociação, cancelar cobrança | ● | U | — |
| Turmas: criar e editar | ● | U | — |
| Agenda: ver | ● | U | T |
| Agenda: editar e cancelar aula | ● | U | T* |
| Dias sem aula | ● (várias unidades de uma vez) | U | — |
| Graduação: registrar | ● | U | — (consulta T) |
| Comunicados | ● (todas as unidades) | U | T* |
| Aulas experimentais | ● | U | T* (ver) |
| Professores | ● | U | — |
| Usuários da gestão | ● (Administradores) | U (Professores) | — |
| Configurações da unidade | ● | U | — |
| Configurações da organização | ● | ○ | — |
| Unidades | ● | — | — |
| Assinatura e faturas | ● | faixa de aviso | — |
| Auditoria | ● | U | — |
| Relatórios | ● (consolidado) | U | o próprio resumo |
| Exportar listas | ● | U | — |
| Exportação completa | ● | — | — |
| Importar | ● | U | — |
| Liberar acesso de suporte | ● | — | — |

Permissões do Professor por unidade (todas ligadas por padrão): **enviar comunicado**, **editar e cancelar aulas**, **ver contatos**, **ver aulas experimentais**.

O Professor nunca vê valores em reais, plano, benefício, CPF, endereço nem situação financeira; vê só se o aluno está ativo ou bloqueado na modalidade.

## Pronto quando

- Um usuário de Professor não encontra nenhum valor em reais em nenhuma tela, busca ou resposta da API.
- Tentar abrir uma rota fora do perfil pelo endereço ou pela API é recusado no servidor.
- Coordenador sem duas etapas não passa do primeiro acesso sem configurar.
- Trocar a senha derruba as outras sessões no mesmo minuto (teste ponta a ponta).
- A mensagem de erro do login é idêntica para e-mail inexistente e senha errada.
