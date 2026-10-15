# 15 · Inícios, Precisa de atenção, busca e ajuda

Entrega 1 · Fase 15 · Depende de: 01 a 14 · Código: INI, BUS, AJU

## Objetivo

Cada perfil começa o dia vendo o que pede ação, e chega a qualquer coisa digitando poucas letras.

## Regras

- **RN-INI-01.** O Início não tem dados próprios. Ele lê os outros módulos, e cada item leva à tela que resolve.
- **RN-INI-02.** Só entra no Início o que pode virar ação. Gráficos ficam em Relatórios.
- **RN-INI-03.** Item resolvido sai da lista na hora.

### Precisa de atenção (Administrador, por unidade; Coordenador, por unidade e somado)

| Grupo | Itens |
| --- | --- |
| Dinheiro | Cobranças em atraso; vencimentos de hoje; comprovantes em conferência; bloqueios nos próximos 5 dias; cartões recusados no débito automático; pagamentos em duplicidade |
| Pedidos | Pedidos de encerramento (com prazo de 7 dias); pedidos do portal; pedidos de exclusão de dados (prazo de 15 dias) |
| Cadastro | Termos pendentes; cadastros incompletos; convites não aceitos; alunos que fizeram 18 anos; e-mails com problema; autocadastros aguardando aprovação |
| Operação | Aulas experimentais de hoje; pedidos novos pelo link; turmas acima da capacidade; turmas sem professor ativo; Mercado Pago desconectado |

## Requisitos

- **RF-INI-01.** **Início do Coordenador**: resumo curto, sem poluição visual: alunos ativos e pico do ciclo, dinheiro do mês (recebido, a receber, em atraso), pendências por unidade (número com atalho), situação da assinatura. Checklist "Primeiros passos" até tudo feito.
- **RF-INI-02.** **Início do Administrador**: "Precisa de atenção" no topo, cada item com ação direta; 4 números (alunos ativos, recebido no mês, em atraso, experimentais da semana); aulas de hoje com "Avisar turma"; aniversariantes com mensagem de WhatsApp pronta. Checklist "Primeiros passos" da unidade, se houver.
- **RF-INI-03.** **Início do Professor**: aulas de hoje e amanhã com a lista de alunos de cada turma (nome, foto, faixa, aviso se bloqueado), selo quando ele é substituto, experimentais do dia (com a permissão), aniversariantes e os itens do "Precisa de atenção" que são dele.
- **RF-BUS-01.** Busca global no topo da gestão (Ctrl+K ou toque no ícone): acha aluno, responsável, professor, turma e cobrança por nome, CPF, e-mail ou telefone, ignorando acento e maiúscula; e também telas e ações por nome e palavras relacionadas ("faixa" acha Graduações). Respeita unidade e perfil. Escolher uma pessoa abre a ficha dela.
- **RF-BUS-02.** Com o campo vazio, mostra as telas mais usadas e as ações rápidas.
- **RF-AJU-01.** Central de ajuda: artigos curtos com prints, escritos pela equipe, por tela. Cada perfil vê só os artigos das áreas que acessa.
- **RF-AJU-02.** Ícone "?" nas telas mais complexas, abrindo o artigo certo.
- **RF-AJU-03.** Tour guiado no primeiro acesso de cada perfil, que pode ser pulado e revisto pela Ajuda.
- **RF-AJU-04.** "Novidades": página com as notas de versão e um ponto no menu quando há algo novo.
- **RF-AJU-05.** Botão "Ajuda" abre também "Falar com o suporte" (chamado, ver `17`).

## Pronto quando

- Cada item do "Precisa de atenção" leva à tela certa, e os números batem com os módulos.
- O Professor não encontra telas financeiras na busca.
- A busca acha "João" digitando "joao".
