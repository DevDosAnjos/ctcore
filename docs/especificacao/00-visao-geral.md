# 00 · Visão geral da plataforma

Especificação única da plataforma ctcore (nome provisório). Substitui o docx de arquitetura, o documento "Evolução para SaaS" e os três guias de desenvolvimento (Gestão, Portal do aluno, Site e acesso). As decisões que deram origem a este texto estão no Claude Doc "ctcore · Especificação única do SaaS", que fica como histórico.

Versão 1.2 · 09/10/2026 · Dono do produto: Nathan.

## 1. Como usar esta especificação

- Cada módulo tem um arquivo próprio nesta pasta. Este arquivo vale para todos.
- Toda mudança de regra começa por um PR nesta pasta. Só depois vira código.
- Um requisito é citado pelo código (ex.: `RN-COB-04`). Os códigos são únicos e nunca são reaproveitados. Requisito retirado fica marcado como **Retirado**, com a data.
- Os textos de tela entre aspas são o texto final, salvo quando marcados como exemplo.
- **[proposta]** marcaria um valor ainda não decidido pelo dono do produto. Na versão 1.1 não há nenhum: todos foram decididos em 09/10/2026.
- Prefixos: `RN` regra de negócio, `RF` requisito funcional, `RNF` requisito não funcional, `UC` caso de uso.

## 2. O produto

Plataforma de gestão para academias e equipes de artes marciais, vendida por assinatura. Começa pelo Jiu-Jitsu, mas o núcleo é genérico: modalidades vêm de um catálogo e as particularidades (graduação, por exemplo) são opcionais por modalidade.

A plataforma não toca no dinheiro dos alunos: cada unidade recebe 100% na própria conta do Mercado Pago. A única receita da plataforma é a assinatura mensal das organizações.

### Áreas

| Área | Endereço | Quem usa |
| --- | --- | --- |
| Site da plataforma | `ctcore.com.br/` | Qualquer pessoa |
| Diretório de academias | `ctcore.com.br/academias` | Qualquer pessoa |
| Pedido de aula experimental | `ctcore.com.br/aula/{unidade}` | Qualquer pessoa |
| Autocadastro de aluno | `ctcore.com.br/matricula/{unidade}` | Qualquer pessoa |
| Demonstração | `ctcore.com.br/demo` | Qualquer pessoa |
| Cadastro da organização | `ctcore.com.br/cadastro` | Qualquer pessoa |
| Conferir recibo | `ctcore.com.br/recibo/{codigo}` | Qualquer pessoa com o código |
| Login | `app.ctcore.com.br/entrar` | Todas as contas |
| Gestão | `app.ctcore.com.br/gestao` | Coordenador, Administrador, Professor |
| Portal | `app.ctcore.com.br/portal` | Aluno, Responsável |
| Painel da plataforma | `app.ctcore.com.br/plataforma` | Equipe da plataforma |

O domínio é exemplo até a escolha do nome definitivo. `{unidade}` é um nome curto gerado do nome da unidade (ex.: `academia-leao-tijuca`), que o Coordenador pode ajustar uma vez.

## 3. Glossário

| Termo | Significado |
| --- | --- |
| Plataforma | O produto e a empresa que o opera |
| Organização | O cliente que assina. Uma academia ou uma equipe com várias unidades. Na interface, com uma unidade só, aparece como "academia" |
| Unidade | Cada estabelecimento da organização |
| Vínculo | Ligação entre uma conta, uma organização, um papel e, quando se aplica, unidades |
| Coordenador | Dono da organização. Um por organização |
| Administrador | Gere as unidades a que está ligado |
| Professor | Dá aula. Vê só as próprias turmas |
| Aluno | Pessoa matriculada. Menor de idade não tem conta |
| Responsável | Adulto ligado a um aluno menor. Tem a conta do portal |
| Modalidade | Arte marcial do catálogo da plataforma (Jiu-Jitsu, Judô, Muay Thai…) |
| Matrícula | O vínculo do aluno com a organização. Uma por aluno |
| Modalidade da matrícula | Cada modalidade que o aluno faz, com unidade, turma, plano e cobrança próprios |
| Turma | Grupo com modalidade, professor e horários em uma unidade |
| Aula | Um encontro de uma turma em uma data. Nasce dos horários da turma |
| Plano | Valor mensal de uma modalidade em uma unidade |
| Benefício | Bolsa (percentual) ou desconto (reais) em uma modalidade da matrícula |
| Cobrança | Valor devido com vencimento: mensalidade, taxa de matrícula, avulsa ou parcela |
| Precisa de atenção | Lista de pendências que pedem ação, no Início da gestão |
| Equipe da plataforma | Dono e Suporte, que usam o painel da plataforma |

## 4. Perfis

| Perfil | Alcance |
| --- | --- |
| Equipe da plataforma · Dono | Todas as organizações. Preços, planos, catálogo, cancelar organização |
| Equipe da plataforma · Suporte | Consulta organizações e chamados. Altera só com liberação do Coordenador |
| Coordenador | Organização inteira: assinatura, unidades, configurações da organização, visão consolidada. Para operar uma unidade, entra nela |
| Administrador | Tudo dentro das unidades a que está ligado |
| Professor | Suas turmas: alunos, aulas, comunicados. Cinco permissões ligáveis por unidade, a ficha do aluno desligada por padrão |
| Aluno | Próprios dados no portal |
| Responsável | Dados dos alunos menores ligados a ele, no portal |

Perfis são fixos. Não existe edição de perfis. A tabela completa de permissões está em `02-contas-e-acesso.md`.

## 5. Princípios

1. **Configuração com padrão.** O que varia entre organizações vira configuração com valor padrão. Mudar um padrão depois não altera o que as organizações já salvaram.
2. **Regras fixas.** Segurança, prazos de acesso e boas práticas são iguais para todos. Mudança em regra fixa vale para todos, com aviso de 30 dias.
3. **Situação é calculada.** Nenhuma situação (de aluno, cobrança, assinatura) é digitada por alguém. Ela vem das regras.
4. **Nada se apaga do que tem história.** Aluno, cobrança, pagamento, graduação e auditoria nunca são excluídos; só anonimizados por pedido. Excluir só o que nunca foi usado.
5. **O sistema informa, a academia decide.** Sem sugestão de graduação, sem frequência, sem pontuação automática.
6. **Mínimo de dado pessoal.** Cada perfil vê só o que precisa. Nenhum dado de saúde é guardado.

## 6. Fora do escopo

Frequência e presença, avaliação física, competições, eventos, loja com estoque, chat, despesas da academia, remuneração e comissão de professor, site próprio por academia, API pública, split de pagamento, juros e multa, pagamento parcial, desconto por pontualidade, cupons, plano anual da assinatura, planos trimestrais, semestrais e anuais para alunos, envio automático pelo WhatsApp, nota fiscal, domínio próprio por academia.

## 7. Entregas

| Entrega | Para quê | Módulos |
| --- | --- | --- |
| **1 · Produto** | Todas as funcionalidades prontas no ambiente de teste, com as conexões externas na versão simulada | 01 a 22 |
| **2 · Conexões** | Trocar as versões simuladas pelas reais: e-mail, SMS, push, desafio invisível, mapa, métricas e Mercado Pago | §9 Conexões externas |
| **3 · Piloto e lançamento** | A academia piloto opera e cobra de verdade com tudo funcionando; depois, o cadastro abre ao público | — |
| **Depois** | Sem data | Programa de indicação, domínio próprio, plano anual, nota fiscal, WhatsApp automático |

A academia piloto usa de graça até o lançamento. Depois, assina com desconto fixo (exemplo: 50% por 12 meses), registrado no painel.

## 8. Ordem das fases

| Fase | Entrega | Módulo | Arquivo |
| --- | --- | --- | --- |
| 0 | 1 | Fundação: repositórios, ambientes, CI, design system, OpenAPI, dados fictícios | este arquivo |
| 1 | 1 | Organização, unidades e isolamento | 01 |
| 2 | 1 | Contas, acesso e permissões | 02 |
| 3 | 1 | Auditoria e LGPD (serviço de auditoria nasce na fase 1) | 03 |
| 4 | 1 | Modalidades, faixas etárias e graduação | 04 |
| 5 | 1 | Professores | 05 |
| 6 | 1 | Turmas e agenda | 06 |
| 7 | 1 | Planos e benefícios | 07 |
| 8 | 1 | Alunos e responsáveis | 08 |
| 9 | 1 | Matrícula | 09 |
| 10 | 1 | Cobranças | 10 |
| 11 | 1 | Pagamentos e Mercado Pago | 11 |
| 12 | 1 | Inativação e reativação | 12 |
| 13 | 1 | Comunicação e notificações | 13 |
| 14 | 1 | Portal do aluno | 14 |
| 15 | 1 | Inícios, Precisa de atenção e busca | 15 |
| 16 | 1 | Assinatura da plataforma | 16 |
| 17 | 1 | Painel da plataforma | 17 |
| 18 | 1 | Cadastro da organização e boas-vindas | 18 |
| 19 | 1 | Importação e exportação | 19 |
| 20 | 1 | Aula experimental, autocadastro e links públicos | 20 |
| 21 | 1 | Relatórios | 21 |
| 22 | 1 | Site, diretório e demonstração | 22 |
| 23 | 2 | Conexões externas reais | 00 §9 |
| 24 | 3 | Entrada da piloto e lançamento | 00 §7 |

Roteiro de toda fase: reler o módulo; modelar dados e migração; regras no servidor com testes; API no OpenAPI com verificação de perfil; handlers MSW; telas; testes de tela, acessibilidade e ponta a ponta; conferir "Pronto quando".

## 9. Arquitetura

- **Monólito modular.** Um back-end com módulos de fronteira clara: Identity & Access, Organizations, Units, People, Students, Teachers, Modalities, Graduation, Classes, Enrollments, Billing, Payments, Communication, Reports, Audit, Platform Subscription, Platform Admin, Public.
- **Banco.** PostgreSQL compartilhado. Toda tabela de negócio tem `organization_id`; as operacionais também `unit_id`.
- **Isolamento em duas camadas.** O código filtra por organização e unidade na camada de dados, e o próprio PostgreSQL recusa linhas de outra organização (row-level security, com a organização da requisição na sessão do banco).
- **Back-end.** Java 21, Spring Boot 3, Maven, Flyway, identificadores UUID, API REST em `/api/v1`, datas em UTC no banco e exibidas no fuso da unidade, tarefas agendadas com trava (uma execução por vez em vários servidores).
- **Front-end.** TypeScript, React, Vite, shadcn/ui, Tailwind CSS, Radix. Textos em arquivos de tradução (só pt-BR por enquanto). MSW simula a API; os handlers são o mapa do que o front espera.
- **Contrato.** OpenAPI no repositório compose é a fonte única para servidor e mocks.
- **Pagamento.** Mercado Pago atrás de uma interface de provedor, para outro poder entrar sem mexer nas regras de cobrança (ver Conexões externas).
- **Arquivos.** Serviço compatível com S3, pastas por organização, 5 MB por arquivo.
- **E-mail e SMS.** Fornecedores a escolher, com domínio autenticado, registro de falhas e reenvio (ver Conexões externas).
- **Hospedagem.** Plataforma gerenciada com Docker e PostgreSQL gerenciado. Fornecedor a escolher.

### Conexões externas

Conexão externa é todo serviço de terceiro que uma funcionalidade usa para funcionar. Elas são construídas por último, na Entrega 2, e podem trocar de fornecedor ou sair do projeto sem reescrever regras: cada uma fica atrás de uma porta (RNF-39), tem versão simulada (RNF-40) e pode ser desligada (RNF-41). Até a Entrega 2, todas as funcionalidades são construídas e testadas com a versão simulada.

| Conexão | Usada em | Versão simulada | Sem esta conexão |
| --- | --- | --- | --- |
| **Pagamento online** (Mercado Pago, `11`) | Pagar pelo portal (Pix, cartão, boleto), débito automático, estorno pelo sistema, conciliação, fatura da assinatura | Simulador de pagamento: aprovar, recusar, expirar, estornar, contestar e repetir o aviso | O aluno paga no balcão ou por Pix na chave da unidade com comprovante (RN-COB-12, RN-COB-13); estornos são marcados à mão (RN-PAG-15); a fatura da assinatura é paga por Pix na chave da plataforma e baixada pelo Dono no painel |
| **E-mail** | Convites, nova senha, cobranças, lembretes, recibos, comunicados, resumo da equipe | Caixa de saída | A gestão copia o link do convite ou da nova senha e envia por conta própria; os avisos ficam só no portal e no sino. Na prática, o caso real é trocar de fornecedor |
| **SMS** | Código de confirmação de telefone (cadastro da organização, autocadastro, troca de telefone) e aviso 5 dias antes do bloqueio para quem não abriu os e-mails | Caixa de saída, com o código visível | O telefone deixa de ser confirmado por código (vale o e-mail confirmado); o aviso antes do bloqueio vai só por e-mail e portal |
| **Push** | Cobrança vencendo, pagamento confirmado, aviso novo e aula cancelada no app instalado | Caixa de saída | Os avisos seguem por e-mail e portal |
| **WhatsApp** | Botões que abrem a conversa ou o grupo com a mensagem pronta (cobrar, lembrete, aniversariante, aula experimental, avisar turma) e o contato da unidade no portal | Não precisa: é só um link e funciona em qualquer ambiente, sem API | Os botões somem (RNF-42); a pessoa usa telefone ou e-mail |
| **Desafio invisível** (Cloudflare Turnstile) | Login, recuperação de senha, cadastro e formulários públicos | Sempre aprova | Ficam o limite por IP e o bloqueio por tentativas |
| **Mapa e localização** | Coordenadas do endereço da unidade, busca por CEP e mapa do diretório | Coordenadas fixas para os CEPs do script de dados | O diretório busca por cidade e bairro, em lista, sem mapa nem distância |
| **Métricas de uso** (Plausible ou Umami) | Uso do site e da gestão | Nada é enviado | O painel usa só os próprios contadores da demonstração e do cadastro (RN-DEM-08) |

Para retirar uma conexão de vez: desligá-la (RNF-41), marcar como **Retirado** os códigos que só existem por ela, tirar dos outros códigos o trecho que a cita e apagar o adaptador real. Nenhuma outra regra muda. Códigos afetados por conexão:

| Conexão | Códigos que só existem por ela | Códigos que perdem um trecho |
| --- | --- | --- |
| Pagamento online | Todo o `11`, RNF-09, RN-COB-14, RN-COB-15, RN-COB-21, RN-ASS-06, RF-ASS-02 | RNF-08, RNF-12, RNF-14, RF-POR-05 (débito automático), RN-INA-02, RN-PNL-04, RN-PNL-05, RF-PNL-02 |
| E-mail | RN-COM-10, RN-COM-14, RN-ALU-10, RNF-28 | Toda regra que diz "por e-mail" (convites, lembretes, recibos, comunicados, notificações) |
| SMS | RN-COB-20, RF-CAD-02 | RN-COB-17, RN-INA-03, `00` §11 (SMS) |
| Push | RN-POR-12 | RN-COB-17, RN-COM-03 |
| WhatsApp | RNF-42 | RF-PRO-02, RF-TUR-02, RF-ALU-03, RF-ALU-06, RF-COB-06, RN-COM-03, RN-POR-06, RF-INI-02, RN-SUP-01, RN-AEX-03, RF-AEX-01, RF-AEX-03, RF-AEX-04, RN-DEM-06; campos "WhatsApp" da unidade e "link do grupo" da turma |
| Desafio invisível | nenhum | RNF-10, RN-ACE-10, RN-CAD-01, RN-AEX-10 |
| Mapa e localização | nenhum | RN-ORG-14, RN-DIR-03, RN-DIR-04 |
| Métricas de uso | RN-SIT-07 | RF-PNL-01 |

Hospedagem, banco, arquivos e monitoramento são infraestrutura, não conexões de funcionalidade: entram na Fundação.

### Repositórios

Monorepo `DevDosAnjos/ctcore`, com as pastas `backend/`, `frontend/`, `infra/` (docker-compose, scripts, publicação) e `docs/` (esta especificação e o OpenAPI), o `AGENTS.md` geral na raiz e as issues. Decisão do dono em 13/10/2026, no lugar do repositório compose com submódulos da versão 1.2.

### Ambientes e publicação

- **Teste** (branch `dev`) e **produção** (branch `main`), com publicação automática.
- Testes rodam em todo PR e bloqueiam o merge.
- O agente faz merge em `dev`. Levar `dev` para `main` exige aprovação humana e a suíte ponta a ponta completa verde.
- O ambiente de teste só tem dados fictícios, criados por script (o mesmo que monta a organização de referência), com as conexões externas na versão simulada (depois da Entrega 2, o Mercado Pago em sandbox). Dado de produção nunca é copiado.
- Versões pela data (ex.: `2026.11.03`), com notas de versão na página "Novidades" da gestão.
- Recursos novos nascem desligados e são ligados por organização no painel da plataforma.

## 10. Requisitos não funcionais

### Desempenho

- **RNF-01.** Telas e listas em até 2 segundos, medidas com a organização de referência: 10 unidades, 2.000 alunos, 5 anos de cobranças, num banco com 200 organizações.
- **RNF-02.** Listas paginadas em 25, com opção de 50 ou 100, mostrando o total. Filtros e página ficam no endereço da tela, para voltar à mesma posição e compartilhar o link. Nenhuma tela carrega todos os registros.
- **RNF-03.** O site público carrega a primeira parte em até 2,5 segundos no celular e não carrega código da gestão nem do portal.

### Segurança

- **RNF-04.** Sempre HTTPS. Senha com hash Argon2.
- **RNF-05.** Permissão conferida no servidor em toda rota e toda ação. O front nunca é a única barreira.
- **RNF-06.** Isolamento entre organizações nas duas camadas (código e banco).
- **RNF-07.** Sessão em cookie protegido (HttpOnly, Secure, SameSite), com proteção contra envio forjado.
- **RNF-08.** Credenciais de integração criptografadas no banco e nunca devolvidas à tela.
- **RNF-09.** Dados de cartão vão direto do navegador ao Mercado Pago. O sistema guarda só o código do cartão e os 4 últimos dígitos.
- **RNF-10.** Formulários públicos com limite por IP e desafio invisível (Cloudflare Turnstile).
- **RNF-11.** Logs sem dado sensível (senha, token, CPF completo, dados de cartão), guardados por 30 dias.

### Confiabilidade

- **RNF-12.** Operações financeiras são idempotentes: gerar duas vezes não duplica; o mesmo aviso de pagamento recebido duas vezes não gera dois pagamentos.
- **RNF-13.** Operações em lote gravam tudo ou nada e informam o resultado.
- **RNF-14.** Falha de serviço externo (e-mail, SMS, Mercado Pago) não perde a operação: registra, avisa e tenta de novo.
- **RNF-15.** Edição simultânea: quem salva por último é avisado, vê o que mudou e escolhe recarregar ou sobrescrever.
- **RNF-16.** Meta interna de disponibilidade de 99,5%, fora do contrato. Manutenção entre 1h e 5h, avisada com 48 horas.
- **RNF-17.** Backup diário guardado por 30 dias, recuperação até um ponto no tempo nos últimos 7 dias quando a hospedagem oferecer, restauração testada todo mês.
- **RNF-18.** Monitoramento com alertas de erro, lentidão, fila de e-mail e falhas de webhook.

### Interface

- **RNF-19.** Gestão, portal e site responsivos, de 360 px a telas grandes. Chrome, Edge, Firefox e Safari nas duas últimas versões; Android 10+ e iOS 15+.
- **RNF-20.** Acessibilidade AA: uso só com teclado, foco visível, rótulos para leitor de tela, situação nunca só por cor, zoom de 200% sem quebrar, alvos de toque de 44 px.
- **RNF-21.** Tema claro e escuro, seguindo o aparelho, com botão para trocar. Cada tela é conferida nos dois.
- **RNF-22.** A marca da organização (logo e cor principal com contraste AA garantido) aparece no portal, na carteirinha, nos recibos e nos e-mails ao aluno, com "feito com ctcore" no rodapé.
- **RNF-23.** Formatos brasileiros: 08/10/2026, 19h30, R$ 1.250,00, telefone com DDD, no fuso da unidade. Notificações e histórico usam "hoje", "ontem", "há 3 dias", com a data exata ao passar o mouse ou tocar.
- **RNF-24.** Textos tratam por "você", com frases curtas e cordiais, sem gíria e sem "prezado". Erros dizem o que aconteceu, o que foi ou não alterado e o que fazer. Cobrança nunca em tom de ameaça. Erro de campo na API é 400 com o código `VALIDATION_ERROR` e o campo.
- **RNF-25.** Toda tela tem os estados carregando, vazio (com uma frase e a ação principal), erro (com "Tentar de novo") e sem permissão. Nenhuma conta nova vem com dados de exemplo.
- **RNF-26.** Ações com efeito abrem uma janela com o resumo do que vai acontecer e pedem motivo quando a regra exige. Confirmação digitada para cancelar assinatura, transferir coordenação, anonimizar aluno e desativar unidade.
- **RNF-27.** Padrões de tela: consulta (filtros e tabela), painel lateral (tela cheia no celular), janela, etapas (processos longos) e cartões.
- **RNF-28.** E-mails em uma coluna, fonte grande e botão largo, conferidos em Gmail e Outlook no celular.

### Testes e qualidade

Testes são indispensáveis em todo o sistema. Toda mudança vem com teste.

- **RNF-29.** Unitários para toda regra de negócio.
- **RNF-30.** Integração com PostgreSQL real (Testcontainers), incluindo migrações e row-level security.
- **RNF-31.** Isolamento: para cada rota, um teste prova que um usuário da organização A não lê nem altera dados da organização B, e que um perfil não alcança o que não deve.
- **RNF-32.** Contrato da API contra o OpenAPI.
- **RNF-33.** Componentes e telas (Vitest e Testing Library com MSW): carregando, vazio, erro e permissões por perfil.
- **RNF-34.** Acessibilidade automática (axe) em todas as telas, nos dois temas.
- **RNF-35.** Segurança: varredura de dependências com falha conhecida e de segredos no código a cada PR.
- **RNF-36.** Ponta a ponta (Playwright) nos fluxos críticos a cada PR, e a suíte completa antes de levar `dev` para `main`.
- **RNF-37.** Cobertura mínima de 80%; o PR falha abaixo disso. Cobrança, pagamentos, assinatura, inativação e isolamento exigem 100% das regras com teste.
- **RNF-38.** Desempenho medido contra RNF-01 antes de cada ida para `main` que mexa em consultas.

### Conexões externas

- **RNF-39.** Toda conexão externa (pagamento, e-mail, SMS, push, desafio invisível, mapa e localização, métricas) fica atrás de uma porta: uma interface no back-end, ou um componente único no front-end, com uma versão simulada e uma real. Regras de negócio, telas e testes falam com a porta e nunca citam o fornecedor.
- **RNF-40.** Até a conexão real existir, e sempre no ambiente de teste, vale a versão simulada: e-mails, SMS e push vão para a "Caixa de saída", uma tela da equipe da plataforma que só existe no ambiente de teste; o simulador de pagamento permite aprovar, recusar, expirar, estornar, contestar e repetir o aviso. Os testes ponta a ponta rodam contra a versão simulada; a real tem os próprios testes contra o sandbox do fornecedor.
- **RNF-41.** Cada conexão pode ser desligada por configuração da plataforma. Desligada, o sistema segue o comportamento "Sem esta conexão" de `00` §9 (Conexões externas), sem erro para quem usa.
- **RNF-42.** Os botões de WhatsApp são um único componente que abre a conversa ou o grupo com a mensagem pronta (link `wa.me`), sem API. Com o WhatsApp desligado pela plataforma, os botões somem e o resto da tela não muda.

Fluxos críticos de ponta a ponta: login com duas etapas; convite e criação de senha; matrícula com primeira cobrança; pagamento por Pix com baixa automática (simulador na Entrega 1, sandbox do Mercado Pago na Entrega 2); atraso até a inativação e reativação pelo pagamento; responsável pagando dois filhos; cancelamento de aula com aviso; graduação; troca de senha encerrando sessões.

## 11. Limites e prazos fixos

| Item | Limite |
| --- | --- |
| Unidades por organização | 10 (acima, só pelo suporte) |
| Usuários da gestão por organização | 200 (acima, só pelo suporte) |
| Alunos por turma | 200 (a capacidade da unidade só avisa, abaixo desse teto) |
| Modalidades ativas por aluno | 10 |
| Nome de pessoa | 120 caracteres |
| Nome de turma, plano e unidade | 60 caracteres |
| Motivo | 500 caracteres |
| Apresentação do professor | 300 caracteres |
| Descrição de cobrança avulsa | 80 caracteres |
| Comunicado | 2.000 caracteres; 20 por dia por unidade |
| Termo da organização | 20.000 caracteres |
| Arquivo enviado | 5 MB. Fotos (aluno, professor, logo) em JPG, PNG ou WebP, cortadas em quadrado e reduzidas para 512 px no envio. PDF só para termos e documentos não médicos |
| Importação | 5.000 linhas e 5 MB por arquivo |
| Reenvio de convite | 3 por dia por pessoa |
| Pacote completo de exportação | 1 por hora |
| SMS por aluno | 3 por mês, contando todos os tipos |

Campos de texto mostram um contador perto do limite.

| Link enviado | Validade |
| --- | --- |
| Convite | 7 dias, uso único |
| Nova senha | 1 hora, uso único |
| Confirmar troca de e-mail | 24 horas |
| Pacote completo de exportação | 7 dias |
| Acesso de suporte liberado | 24 horas |
| Link de pagamento nos e-mails de cobrança | Sem prazo: abre a cobrança atual e pede login |
| Recibo | Permanente, com código de conferência |
| Pix do Mercado Pago | 30 minutos |

**SMS.** Incluído na assinatura, só para o essencial: código de confirmação de telefone (cadastro da organização, autocadastro e troca de telefone pelo portal) e o aviso "faltam 5 dias para o bloqueio" quando o aluno não abriu os e-mails anteriores. O resto vai por e-mail e push.

**Limites do plano no banco.** O banco guarda, por organização, limites de alunos, usuários, unidades e módulos, para planos futuros. Na v1, só o número de alunos ativos define o preço e nada é bloqueado por limite de plano.

## 12. Pendências fora do código

1. CNPJ da empresa titular, antes da primeira cobrança real e da conta da plataforma no Mercado Pago.
2. Revisão de advogado: Termos de uso, contrato de tratamento de dados, política de privacidade, reembolso de 7 dias (Decreto 7.962/2013), repasse da taxa ao aluno, prazos de 30 dias após cancelamento e 60 dias de encerramento da plataforma.
3. Confirmar na documentação do Mercado Pago, antes da conexão real: conexão por autorização (OAuth) em nome da unidade, cartão de débito, débito automático no cartão, webhooks, estorno parcial pela API. Se algum item não existir, a regra correspondente muda ou a conexão é retirada (§9 Conexões externas).
4. Nome definitivo e domínio (o domínio é preciso para autenticar o remetente dos e-mails).
5. Fornecedores de infraestrutura: hospedagem, arquivos, monitoramento. Decisão do dono em 13/10/2026: tudo o que depende dele fica no fim do plano; a contratação e a publicação automática foram para a Sprint 22 (issues #192 e #193). Até lá, o projeto roda com o docker-compose e o CI do GitHub.
6. Fornecedores das conexões externas, antes da Entrega 2: e-mail, SMS, mapa e localização, métricas (Plausible ou Umami).
