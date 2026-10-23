# Plano de sprints

Parte geral do documento "ctcore · Documento da aplicação e plano de sprints · v1.2" (o PDF completo está em `docs/ctcore-especificacao-1.2.pdf`). A regra de cada código está em `docs/especificacao/`; o que fazer em cada item está na issue dele no GitHub.

## Como o plano está no GitHub

- Cada sprint é um marco (milestone) `Sprint NN · Nome`. A ordem de trabalho é: marco, depois número da issue.
- Cada RF da especificação é uma issue (`RF-XXX-NN · título`). As partes de uma sprint que não são RF (fundação, serviços do servidor, conexões reais, piloto) são issues de base técnica.
- Os 520 códigos (RN, RF, RNF) estão todos nas issues, cada um em uma só, com o texto completo.
- Etiquetas: área (`back-end`, `front-end`, `infra`, `operação`), entrega e `depende de você` (precisa de conta, contratação ou ação do dono; fica nas Sprints 22 a 25).

## Ordem: oito travas

| Antes | Depois | Por quê |
| --- | --- | --- |
| Isolamento e auditoria (01) | Tudo | Mudar o isolamento depois exige mexer em todas as tabelas e rotas; toda escrita grava auditoria desde o primeiro dia |
| Contas e permissões (02, 03) | Toda tela | Cada rota nasce com verificação de perfil e teste de isolamento |
| Modalidades, turmas, planos e alunos (04 a 06) | Matrícula (07) | A matrícula liga aluno, modalidade, turma e plano |
| Matrícula (07) | Cobranças (08) | A mensalidade nasce da modalidade da matrícula; a primeira cobrança fecha o fluxo de matrícula |
| Comunicação e portal (09, 10) | Pagamentos online (11) | O pagamento online acontece no portal, e os recibos e avisos saem pelo serviço de e-mail |
| Pagamentos (11) | Inativação (12) | Bloquear só é justo quando o aluno já consegue pagar sozinho e a baixa é automática |
| Produto completo (00 a 21) | Conexões reais (22, 23) | Cada conexão real só troca o simulado por trás de uma porta que já existe e já tem teste |
| Conexões reais (22, 23) | Piloto (24) | A piloto entra com todas as funcionalidades e conexões funcionando de verdade |

Nenhuma sprint usa algo que uma sprint posterior constrói. Quando uma tela precisa de um módulo que vem depois, a sprint deixa o ponto de encaixe (descrito no marco) e a sprint seguinte o liga.

## Sprints

| Marco | Objetivo | Issues |
| --- | --- | --- |
| Sprint 00 · Fundação | Deixar o projeto pronto para receber funcionalidades: repositórios, esteira de testes, ambientes, contrato da API, base visual e dados fictícios. | 12 |
| Sprint 01 · Organização, unidades, isolamento e auditoria | Criar a base multi-cliente: organizações isoladas entre si no código e no banco, unidades, onde ficam as configurações, e o serviço de auditoria que todo módulo vai usar. | 5 |
| Sprint 02 · Contas, login, convites e sessões | Uma conta por pessoa, um login único para gestão e portal, convites, recuperação de senha e sessões seguras. | 8 |
| Sprint 03 · Duas etapas, usuários, permissões e configurações | Fechar a segurança da conta, montar a tabela de permissões dos três perfis da gestão e as telas de configuração da organização e das unidades. | 12 |
| Sprint 04 · Modalidades, faixas etárias, graduação e professores | Ter o catálogo de modalidades ativado pela organização, o sistema de graduação configurável, as faixas etárias e o cadastro de professores. | 8 |
| Sprint 05 · Turmas e agenda | Organizar as turmas da unidade e as aulas que nascem delas, com edição, cancelamento, aula avulsa, substituição e dias sem aula. | 8 |
| Sprint 06 · Planos, alunos e responsáveis | Cadastrar quanto custa cada modalidade em cada unidade e as pessoas: alunos e responsáveis, com a ficha completa. | 9 |
| Sprint 07 · Matrícula e benefícios | Transformar uma pessoa em aluno: modalidades, turma, plano, benefício, vencimento, graduação de origem e termo, mais as mudanças e a saída. | 8 |
| Sprint 08 · Cobranças | Gerar, acompanhar e receber no balcão tudo o que os alunos devem, com recibo, e fechar o fluxo de matrícula com a primeira cobrança. | 7 |
| Sprint 09 · Comunicação, lembretes e notificações | Avisar alunos e equipe do que importa: comunicados, e-mails automáticos, lembretes de cobrança e notificações da gestão. | 7 |
| Sprint 10 · Portal do aluno | Dar ao aluno e ao responsável, no celular, o termo, as cobranças, as aulas, os avisos, a faixa e a carteirinha, e os pedidos à academia. | 11 |
| Sprint 11 · Pagamentos online | Receber online pelo portal, com baixa automática, conciliação, estorno e o comportamento certo quando o provedor cai, tudo contra o simulador de pagamento. O Mercado Pago real entra na Sprint 23. | 9 |
| Sprint 12 · Inativação, reativação e graduações | Tirar da operação quem não paga e devolver na hora quando paga, e registrar as graduações com aviso ao aluno. | 7 |
| Sprint 13 · Inícios, busca, auditoria e LGPD | Fazer cada perfil começar o dia pelo que pede ação, com busca global e as telas de auditoria e LGPD. | 15 |
| Sprint 14 · Assinatura da plataforma | Cobrar cada organização todo mês pelo pico de alunos ativos do ciclo, com as situações de atraso e o somente leitura. | 6 |
| Sprint 15 · Painel da plataforma e suporte | Dar à equipe da plataforma a área para acompanhar e administrar as organizações e atender o suporte. | 13 |
| Sprint 16 · Cadastro da organização e boas-vindas | Permitir que uma organização se cadastre, pague, configure e comece a usar sem ajuda da equipe. | 5 |
| Sprint 17 · Importação e exportação | Trazer uma academia que já existe em planilha em uma tarde e deixar a organização levar os próprios dados quando quiser. | 4 |
| Sprint 18 · Aula experimental e autocadastro | Trazer gente nova para a unidade por links públicos e QR code, sempre com a unidade decidindo. | 8 |
| Sprint 19 · Relatórios | Mostrar o andamento da unidade e da organização ao longo do tempo, com números que batem com a operação. | 5 |
| Sprint 20 · Site, diretório e demonstração | Vender a plataforma, deixar o dono de academia experimentar sem cadastro e ajudar alunos a encontrar academias parceiras. | 3 |
| Sprint 21 · App instalável e central de ajuda | Fechar o produto: portal como app no celular, uso sem internet, central de ajuda, tour e Novidades. | 5 |
| Sprint 22 · Conexões reais: e-mail, SMS, push, desafio invisível, mapa e métricas | Trocar as versões simuladas pelas reais em tudo, menos o pagamento, sem mudar nenhuma regra: só adaptadores novos atrás das portas da Sprint 00. | 9 |
| Sprint 23 · Conexão real com o Mercado Pago | Ligar o pagamento online de verdade: cada unidade conecta a própria conta, e a plataforma recebe as faturas da assinatura pela conta dela. | 4 |
| Sprint 24 · Entrada da academia piloto | Colocar a academia piloto para operar e cobrar de verdade, com todas as funcionalidades e conexões ligadas, e acompanhar o primeiro ciclo de cobrança. | 3 |
| Sprint 25 · Lançamento | Abrir o cadastro ao público depois de a piloto rodar um ciclo completo, com tudo o que fica fora do código resolvido. | 2 |

Entregas: 1 · Produto (Sprints 00 a 21, conexões simuladas) · 2 · Conexões (22 e 23) · 3 · Piloto e lançamento (24 e 25).

## Qualidade e definição de pronto

Teste é parte do item, não uma etapa depois dele: um item só entra em `dev` com os testes que a sua sprint pede, e só vai para `main` com a suíte ponta a ponta inteira verde.

### Testes obrigatórios

| Código | Teste | Ferramenta |
| --- | --- | --- |
| RNF-29 | Unitário para toda regra de negócio | JUnit |
| RNF-30 | Integração com PostgreSQL real, incluindo migrações e row-level security | Testcontainers |
| RNF-31 | Isolamento: para cada rota, a organização A não lê nem altera a B, e cada perfil só alcança o que deve | JUnit + Testcontainers |
| RNF-32 | Contrato da API contra o OpenAPI | Validador de contrato |
| RNF-33 | Componentes e telas: carregando, vazio, erro e permissões por perfil | Vitest, Testing Library, MSW |
| RNF-34 | Acessibilidade automática em todas as telas, nos dois temas | axe |
| RNF-35 | Dependências com falha conhecida e segredos no código, a cada PR | Varredura no CI |
| RNF-36 | Fluxos críticos a cada PR; suíte completa antes de `dev` → `main` | Playwright |
| RNF-37 | Cobertura mínima de 80%; 100% das regras de cobrança, pagamentos, assinatura, inativação e isolamento | Relatório de cobertura no CI |
| RNF-38 | Desempenho contra RNF-01 antes de cada ida para `main` que mexa em consultas | Script da organização de referência |

### Fluxo de cada item

1. Reler o trecho da sprint e as regras citadas.
2. Modelar os dados e escrever a migração Flyway.
3. Escrever as regras no servidor, com os testes unitários e de integração.
4. Publicar a rota no OpenAPI, com verificação de perfil e teste de isolamento.
5. Atualizar os handlers MSW.
6. Construir as telas com os quatro estados.
7. Testes de tela, acessibilidade e, se for fluxo crítico, ponta a ponta.
8. Conferir o "Pronto quando" da sprint e abrir o PR.

### Pronto quando (vale para todo item)

- Todas as regras citadas têm teste, e a cobertura do PR não caiu abaixo do mínimo.
- Toda rota nova tem teste de isolamento entre organizações e entre perfis.
- O OpenAPI e os handlers MSW batem com a implementação.
- Toda ação que altera dado grava na auditoria.
- As telas têm os quatro estados, funcionam a partir de 360 px e passam no axe nos dois temas.
- Textos nos arquivos de tradução, nunca escritos no código.
- Recurso novo nasce desligado por organização, quando a sprint diz que é um recurso liberado aos poucos.
- Notas de versão atualizadas quando a mudança é visível para as academias.

### Fluxos críticos de ponta a ponta

Cada fluxo entra na suíte na sprint que o completa e roda em todo PR dali em diante.

| Fluxo | Sprint |
| --- | --- |
| Convite e criação de senha | 02 |
| Troca de senha encerrando as outras sessões | 03 |
| Login com duas etapas | 03 |
| Matrícula com primeira cobrança | 08 |
| Cancelamento de aula com aviso à turma | 09 |
| Pagamento por Pix com baixa automática (simulador; sandbox do Mercado Pago na Sprint 23) | 11 |
| Responsável pagando dois filhos | 11 |
| Atraso até o bloqueio e reativação pelo pagamento | 12 |
| Graduação com aviso ao aluno | 12 |
| Organização nova do cadastro até cobrar o primeiro aluno | 16 |

## Decisões do dono depois da versão 1.2

- 13/10/2026 · Monorepo (`backend/`, `frontend/`, `infra/`, `docs/`) no lugar do compose com submódulos.
- 13/10/2026 · Tudo o que depende do dono fica no fim do plano: hospedagem, publicação automática, backup e alertas foram da Sprint 00 para a Sprint 22 (#192, #193). As issues #5 e #11 ficaram só com a parte que o agente faz sozinho.
- 13/10/2026 · `dev` é o branch de trabalho e padrão; `main` só recebe `dev` por PR com o método "Merge", no fim de cada sprint, depois da revisão do dono.
- 23/10/2026 · O repositório fica público durante o desenvolvimento, para o CI rodar sem limite de minutos, e volta a ser privado antes da Sprint 22, antes de qualquer chave real ou dado de cliente. Regras e trava no `AGENTS.md`, seção "Repositório público: segredos e dados reais".
