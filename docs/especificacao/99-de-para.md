# 99 · De-para dos documentos antigos

Liga cada fase e código dos documentos antigos ao arquivo novo. Os códigos foram renumerados: um código antigo não tem o mesmo significado que o novo de mesmo prefixo. Ao citar um código antigo (em issues, por exemplo), use o prefixo do documento: `G:` Gestão, `P:` Portal, `S:` Site e acesso, `E:` Evolução para SaaS. Exemplo: `G:RN-ALU-04` é a regra antiga dos dias de vencimento 1, 5, 10 e 15, hoje coberta por `RN-MAT-03` e pela configuração da unidade.

## Guia da Gestão (G)

| Fase antiga | Código antigo | Arquivo novo | Observação |
| --- | --- | --- | --- |
| 01 Base do sistema | BAS | 00, 02 | Gestão deixa de ser só para computador: é responsiva |
| 02 Academia | ACA | 01 | Vira Organização e Unidade |
| 03 Usuários e permissões | USU | 02 | Três perfis fixos; "Editar perfis" sai |
| 04 Professores | PRO | 05 | |
| 05 Planos | PLA | 07 | Plano por modalidade e unidade |
| 06 Turmas | TUR | 06 | Turma livre, sem a grade tipo × período |
| 07 Alunos | ALU | 08, 09, 12 | Sai "Pausado"; matrícula definida |
| 08 Graduações | GRA | 04 | Só a gestão registra; o Professor consulta |
| 09 Grupos | GRU | 06 (link do grupo na turma) | Sem tela própria de grupos |
| 10 Comunicados | COM | 13 | Destinos novos; agendamento |
| 11 Agenda | AGE | 06 | Grade semanal; 8 semanas geradas |
| 12 Aulas experimentais | EXP | 20 | Situações novas; "Registrar como aluno" existe |
| 13 Benefícios | BEN | 07 | Um por modalidade; percentual livre |
| 14 Mensalidades | MEN | 10, 11 | Geração automática com prévia |
| 15 Despesas | DES | — | **Retirado** da v1 |
| 16 Visão geral financeira | FIN | 15, 21 | |
| 17 Início | INI | 15 | |
| 18 Visão do professor | PRF | 02, 15 | |
| 19 Notificações | NOT | 13 | |
| 20 Busca | BUS | 15 | |
| 21 Relatórios | REL | 21 | |
| 22 Histórico de alterações | HIS | 03 | Chama-se Auditoria |
| 23 Importar e exportar | IMP | 19 | |
| 24 Integrações | INT | 11, 13 | Mercado Pago por unidade |
| 25 Ajuda | AJU | 15 | Central de ajuda e "?" |

## Guia do Portal (P)

| Fase antiga | Código antigo | Arquivo novo | Observação |
| --- | --- | --- | --- |
| 01 Base do portal | POR | 14 | |
| 02 Responsável | RSP | 08, 14 | Até dois responsáveis |
| 03 Minhas informações | MIN | 14 | Edição de contato, endereço, foto e emergência |
| 04 Agenda | AGP | 14 | |
| 05 Avisos | AVI | 13, 14 | Lido só ao abrir |
| 06 Financeiro | FNP | 10, 14 | |
| 07 Pagamento online | PGO | 11 | Mercado Pago; taxa repassada |
| 08 Início | INP | 14 | |

## Guia do Site e acesso (S)

| Fase antiga | Código antigo | Arquivo novo | Observação |
| --- | --- | --- | --- |
| 01 Login | LGN | 02 | Sem "Manter conectado" |
| 02 Primeiro acesso | PAC | 02 | |
| 03 Recuperar senha | SEN | 02 | |
| 04 Site da academia | SIT | 22 | **Retirado**: só existe o site da plataforma |
| 05 Horários e equipe | HEQ | 22 (diretório) | Só no diretório; consentimento do professor |
| 06 Pedido de aula | EXS | 20 | Link público por unidade |

## Evolução para SaaS (E)

| Código antigo | Arquivo novo | Observação |
| --- | --- | --- |
| MUL-01 a MUL-07 | 01, 03 | Organização › Unidades |
| CON-01 a CON-09 | 02 | |
| CON-10, CON-11 | — | **Retirados**: existe hierarquia organização › unidades e visão consolidada |
| API-01 a API-03 | 00 | |
| CAD-01 a CAD-09 | 18 | |
| ASS-01 a ASS-19 | 16 | |
| PNL-01 a PNL-10 | 17 | PNL-06 mudou: alterar exige liberação do Coordenador |
| IMP-01 a IMP-11 | 19 | |
| SIT-01 a SIT-07 | 22 | Site na raiz, app em subdomínio |
| LNK-01 a LNK-07 | 20 | |
| DEM-01 a DEM-08 | 22 | Quatro entradas, duas unidades |
| LGP-01 a LGP-09 | 03 | Dados após cancelamento: 30 dias (era 90) |

## Itens antigos que deixaram de valer

| Antes | Agora |
| --- | --- |
| Academias independentes, sem visão somada | Organização › Unidades, com visão consolidada |
| Perfis Administrador e Professor | Coordenador, Administrador e Professor |
| Modalidade "Com kimono" editável pela academia | Catálogo da plataforma, ativado pelo Coordenador |
| Turma = tipo × período | Turma livre, com horário por dia |
| Um plano por aluno | Uma matrícula por aluno, plano e cobrança por modalidade |
| Asaas | Mercado Pago |
| Multa e juros configuráveis | Não existem |
| Primeira mensalidade cheia ou proporcional | Sempre cheia |
| Regra de combinação de benefícios; bolsa de 30%, 50% ou integral | Um benefício por modalidade; percentual livre |
| Aluno Pausado | Não existe |
| Manter conectado | Não existe; sessão de 30 dias (aluno) e 12 horas (gestão) |
| Professor registrava graduação | Só a gestão registra |
| Gestão só no computador | Tudo responsivo |
| Despesas e categorias de despesa | Fora da v1 |
| Site da academia, equipe e grade públicas | Só site da plataforma e diretório |
| Endereço único com caminhos | Site na raiz, app em `app.` |
| Mesmo nome e nascimento = mesmo aluno | CPF identifica; nome e nascimento só avisam |
