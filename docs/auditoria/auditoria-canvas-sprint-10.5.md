# Auditoria sistema x canvas · Sprint 10.5

Gerado em 30/10/2026 a partir de duas rodadas. Nenhum dado real: só os dados fictícios do MSW e do canvas. **As imagens citadas (`imagens/...`) ficam só na máquina do dono**: a pasta `docs/auditoria/imagens/` está no `.gitignore` e não vai para o repositório público.

Legenda de gravidade: **alta** = quebra fluxo; **média** = diverge do canvas; **baixa** = cosmético. Numeração: `A1…`, `M1…`, `B1…` vêm da rodada 1 e continuam; **[NOVO R2]** marca o que apareceu na rodada 2; "Rodada 2 · amplia Mx" é detalhe novo de um achado antigo.

## Resumo

| Gravidade | Rodada 1 (como entregue) | Novos na rodada 2 (como entregues) | Após reclassificação do dono (IDs finais) | Detalhes novos da rodada 2 em IDs antigos |
|---|---|---|---|---|
| alta | 2 | 1 | 7 | 8 |
| média | 36 | 117 | 150 | 83 |
| baixa | 5 | 21 | 25 | 6 |
| **total** | 43 | 139 | 182 | 97 |

A rodada 1 tinha **2 altas** (A1 e A2). O dono rebaixou A2 para **média** (decisão 15) e reclassificou como **alta** cinco achados: M7, B14, M123, M153 e M135 (IDs mantidos; as entradas estão na seção de gravidade alta). Alta final: A1, A3, M7, B14, M123, M153 e M135. A rodada 2 produziu 239 achados brutos nos 16 lotes: 139 viraram IDs novos, 97 foram anexados a IDs da rodada 1 e 2 foram movidos para "sem comparar" (mais a entrada B10, removida por ser a lista de telas não construídas; a numeração dos demais foi mantida). Na revisão do passo anterior, 13 achados que o agente classificou como alta desceram para média, com o motivo na própria entrada; o dono reclassificou depois os cinco acima.

## 1. Como comparei

- **Rodada 1 (29/10):** 83 pares do teste visual (sistema em `frontend/visual/referencias` x canvas), só 1440 px, claro; dois pares em escuro. Pares vistos com atenção: 51; os demais pela semelhança com os vistos (limitação reconhecida). Composições lado a lado em `imagens/rodada-1/` (sistema à esquerda, canvas à direita).
- **Rodada 2 (30/10):** 16 subagentes, um por lote de 13 a 22 telas do canvas, divididos por funcionalidade (Pessoas em 3 lotes, Operação 2, Financeiro 2, Comunicação e estrutura 2, Configurações 2, Portal 4, Acesso 1). Cada par foi aberto com leitura de imagem; os pares não vistos estão em "sem comparar".
- **Telas:** as 416 do `indice.json` foram distribuídas; 360 são de áreas de gestão, portal e acesso. Telas de funcionalidade ainda não construída ficam em "sem comparar" com a evidência (rota ausente no `App.tsx`, item `available=false` em `menuCatalog.ts`, issue aberta).
- **Larguras:** 1440 px (desktop) e 390 px (celular; o canvas de celular é de 360 ou 390). **Temas:** claro e escuro; o canvas só tem versão escura em poucas telas (Início e Cobranças), então só ali há par escuro. Onde o canvas só tem claro, o escuro do sistema foi capturado e examinado em busca de contraste ou cor fixa (nada grave foi apontado).
- **Dados:** dados do MSW que faltavam foram substituídos por fixtures temporárias (resposta de `fetch` trocada no navegador), sem mexer no repositório. Diferenças só de dado fictício foram descartadas (lista por lote no fim).
- **Pixels:** usados como apoio em parte dos pares (`...-diff.png` apenas onde havia achado); a análise foi visual e os pontos de texto/regra foram conferidos no código e em `docs/especificacao/`.
- **Ferramenta:** scripts fora do repositório (`cap.mjs`: captura do sistema com login pelo MSW, do canvas e diferença por faixas). Reproduzir exige o front em `localhost` com MSW.
- **Limitações:**
  - Pares lidos em parte: `PortalMTopo` (comparado só no topo; o print tinha 3973 px) e `PortalMReduzido` (comparado com captura de outra rota). `GMAulaCancelar` e `GMAulaEditar` a 390 px foram conferidos só no código; `GMAulaProfessor` só no canvas.
  - Estado de carregamento e vazio a 390 px do portal não foram capturados.
  - A rodada 1 não cobriu 390 px nem escuro; a rodada 2 cobriu, mas os 32 pares da rodada 1 não revistos 1 a 1 só foram revistos se a tela está também num lote da rodada 2.
  - Dados do MSW são poucos: janelas que dependem de dado inexistente (ex.: uso de imagem concedido, cobrança em aberto do aluno) ficaram sem comparar.

## 2. Divergências de componente (repetem em várias telas)

Agrupado pelo campo "Padrão" informado em cada achado; uma tela pode aparecer em mais de um grupo. Entre parênteses, o ID do achado. Corrigir o componente base resolve todas as telas do grupo.

### Busca vazia, sem ícone nem texto de ajuda

Telas afetadas (14): Comunicados (lista, cabeçalho e filtros) (M17); Comunicados no celular (M50); ConfigUsuarios (M24); Cobranças (lista) (M13); Cobranças (lista), celular (M13); Em atraso (M14); Benefícios (lista) (M12); Cobranças (M5); Tabela de planos (M5); Turmas (M9); Alunos (abas, filtros e busca) (M5); Alunos (barra de filtros) (M5); Etapa 1: Aluno (M115); Professores (lista) (M6)

### Filtros: seletor nativo com rótulo acima x pílula compacta do canvas

Telas afetadas (32): GMErro (M46); Comunicados (lista, cabeçalho e filtros) (M17); Barra superior (Topbar) (M1); Seletor de unidade no celular (M52); ConfigTermos (M25); ConfigUsuarios (M24); Cobranças (lista) (M13); Cobranças (lista), celular (M13); Benefícios (lista) (M12); Conceder benefício (M73); Cobranças (M5); Registrar pagamento (janela) (M77); Recebimentos do dia (M15); Tabela de planos (M5); Agenda (M131); GMAgenda (M8); Turmas (M9); Alunos (abas, filtros e busca) (M5); GMAlunosFiltros (folha de filtros) (M97); Ficha, aba Histórico (M104); Alunos (barra de filtros) (M5); Assistente de matrícula, estrutura (M114); Janela Trocar plano (M121); Professores (lista) (M6); PedidosPortal (M16); Avisos (lista) (B2); Início (B25); Início responsável (M142); Financeiro (M146); Financeiro (responsável) (M147); Carteirinha (responsável) (M149); Pedir troca de turma / plano / nova modalidade (janelas) (M151)

### Campos nativos do navegador (data em inglês, arquivo, caixa, rádio, interruptor)

Telas afetadas (41): Comunicados (lista, cabeçalho e filtros) (M17); Novo comunicado (formulário) (M18); Tema escuro (B21); Barra superior (Topbar) (M1); Novo comunicado no celular (M18); Seletor de unidade no celular (M52); ConfigUnidade (M20); ConfigPermProf (M54); ConfigLinks (Divulgação) (M55); Controles nativos no tema escuro (B9); ModalidadesOferecidasRecusa (M65); ModalidadeGraduacao (M23); Cobranças (lista) (M13); Cobranças (lista), celular (M13); Nova cobrança avulsa (B4); Benefícios (lista) (M12); Conceder benefício (M73); Cobranças (M5); Registrar pagamento (janela) (M77); Recebimentos do dia (M15); Tabela de planos (M5); Agenda (M8); GMAgenda (M8); AulaAvulsa (B3); Turmas (M9); Alunos (abas, filtros e busca) (M5); Alunos (cabeçalho da página) (M5); Editar dados pessoais (desktop) (M106); Encerrar modalidade (desktop) (M108); Alunos (barra de filtros) (M5); Etapa 3: Vencimento (M117); Professores (lista) (M6); ProfessorNovo (janela) (M124); PedidosPortal (M16); Professores / Pedidos (B22); Financeiro (M146); Início (B25); Financeiro (responsável) (M147); Carteirinha (responsável) (M149); Pedir troca de turma / plano / nova modalidade (janelas) (M151); Pix na chave com comprovante (desktop e celular) (M30)

### Tabela no celular (estoura a largura, sem lista de cartões)

Telas afetadas (39): Menu aberto no celular (M51); Seletor de unidade no celular (M52); Barra de cima no celular (B8); GMConfigUnidade (M56); GMConfig (M57); ConfigModalidades (M22); GMUsuarios (celular) (M60); Conceder benefício (M73); Registrar pagamento (janela) (M77); Planos no celular (M82); Cobranças no celular (M83); AulaAvulsa (B3); AulaEditar (M11); GMAulaCancelar / GMAulaEditar (M89); DiasSemAula (A2); GMDiasSemAula (M91); DiaSemAulaNovo (A2); GMTurmas (M93); GMAlunos (lista no celular) (M96); GMAlunosFiltros (folha de filtros) (M97); Ficha do aluno, aba Resumo (blocos) (M100); Ficha, aba Financeiro (M103); Ficha, aba Histórico (M104); Editar dados (celular) (M107); Encerrar modalidade (celular) (B18); Ficha do aluno no celular (M109); Etapa 2: Modalidades (M116); Etapa 4: Resumo (M118); Janela Trocar plano (M121); Professores (lista) (M6); GMPedidoRecusar (M128); Avisos (lista) (B2); Início (M137); Minhas informações (M31); Editar contato (M31); Pedidos (desktop e celular) (M33); Pedir troca de turma / plano / nova modalidade (janelas) (M151); Pix na chave com comprovante (desktop e celular) (M30); Portal reduzido › Minhas informações (M31)

### Gaveta lateral do canvas x página ou janela no sistema

Telas afetadas (27): EscolhaOrganizacao / EscolhaOrgM (M42); Novo comunicado para a organização inteira (janela) (M49); Painel da cobrança (M68); Painel da cobrança, celular (M69); Registrar pagamento (janela) (M77); Planos e benefícios (M10); Plano desativado (reativar) (M81); Cobranças no celular (M83); AgendaPainel (M7); GMAulaPainel (M88); GMAulaCancelar / GMAulaEditar (M89); TurmaPainel (M7); GMTurmaPainel (M7); Alunos (cabeçalho da página) (M5); Ficha do aluno (cabeçalho) (M98); Editar dados (celular) (M107); Encerrar modalidade (celular) (B18); Ficha do aluno, cabeçalho e Resumo (M5); ProfessorPainel (M7); Aviso aberto (M132); Início (M136); Termo (desktop e celular) (M144); Termo com 2 filhos (M145); Minhas informações (M31); Editar contato (M31); Pedir troca de turma / plano / nova modalidade (janelas) (M151); Pix na chave com comprovante (desktop e celular) (M30)

### Janelas: título em caixa baixa, sem rodapé fixo, resumo, motivo ou cartões de escolha

Telas afetadas (63): EscolhaOrganizacao / EscolhaOrgM (M42); DuasEtapas (janela) (M44); CodigosReserva (janela) (M45); Novo comunicado para a organização inteira (janela) (M49); Notificações (janela) (M28); ConfigAcademia (M19); UsuarioConvidar (M61); UnidadeDesativar (M63); UnidadeUnica (M64); ModalidadeDesativar (M66); ModalidadeGraduacao (M23); TransferirCoordenacao (M67); Painel da cobrança, celular (M69); Cancelar cobrança (M70); Nova cobrança avulsa (B4); Comprovante de várias cobranças (M72); Conceder benefício (M73); Encerrar benefício (B11); Substituir benefício (M74); Registrar pagamento (janela) (M77); Renegociar (janela) (M78); Gerar próximas mensalidades (janela) (M79); Planos e benefícios (M10); Plano (Editar/Novo) (M11); Desativar plano (M80); Plano desativado (reativar) (M81); Cobranças no celular (M83); AulaAvulsa (B3); AulaEditar (M11); AulaEditarSeguintes (M86); AulaCorrigir (M87); GMAulaCancelar / GMAulaEditar (M89); DiaSemAulaNovo (A2); TurmaEditar (M11); TurmaDesativar (M94); TurmaTitularInativo (M95); Ficha, aba Matrícula e modalidades (M101); Ficha, aba Financeiro (M103); Editar dados pessoais (desktop) (M106); Editar dados (celular) (M107); Encerrar modalidade (desktop) (M108); Encerrar modalidade (celular) (B18); Assistente de matrícula, estrutura (M114); Etapa 2: Modalidades (M116); Etapa 4: Resumo (M118); Janela Trocar turma (M120); Janela Trocar plano (M121); Janela Transferir (M122); ProfessorNovo (janela) (M124); ProfessorInativar (janela) (M125); PedidoRecusar / PedidoEncerramentoRecusar (janela) (M127); GMPedidoRecusar (M128); PedidoAprovadoFluxo (janela) (M129); ResponsavelDesligar (janela) (M130); Aviso aberto (M132); Confirmar nova modalidade (M133); Início (B26); Minha conta (portal) (M27); Minha conta (M27); Editar contato (M31); Pedir troca de turma / plano / nova modalidade (janelas) (M151); Pedido indisponível (encerramento aberto) (M152); Pix na chave com comprovante (desktop e celular) (M30)

### Botão destrutivo sem a cor de perigo

Telas afetadas (10): Cancelar comunicado agendado (janela) (M47); Cancelar cobrança (M70); Encerrar benefício (B11); Desativar plano (M80); AulaCancelar (B3); Ficha, aba Matrícula e modalidades (M101); Ficha, aba Matrícula (M110); ProfessorInativar (janela) (M125); PedidoRecusar / PedidoEncerramentoRecusar (janela) (M127); ResponsavelDesligar (janela) (M130)

### Moldura: barra superior, menu lateral, cabeçalho (gestão e portal)

Telas afetadas (37): LoginM (M2); todas as telas de acesso 390 (M2); MinhaConta (M27); GMConta (M27); Comunicados (menu) (A1); Estrutura da gestão (menu lateral) (M1); Estrutura da gestão (barra superior) (M1); Estrutura mobile da gestão (Início) (B6); Menu lateral e moldura (M1); Barra superior (Topbar) (M1); Notificações (janela) (M28); Menu aberto no celular (M51); Novo comunicado no celular (M18); Seletor de unidade no celular (M52); Barra de cima no celular (B8); Menu de Configurações (M58); ConfigTermos (M25); UnidadeNova (M62); Moldura (menu lateral e barra) em todas as telas do lote (M1); Menu lateral (todas as telas do lote) (A1); Cobranças (menu) (A1); Cobranças (M1); Cobranças (tabela) (M76); Agenda (M8); Agenda (menu) (B15); GMAulaPainel (M88); Turmas (estrutura) (M1); Alunos (cabeçalho da página) (M5); Ficha do aluno (ações Cobrar e menu ⋯) (M99); Ficha do aluno, cabeçalho e Resumo (M5); Ficha do aluno no celular (M109); Portal › estrutura (moldura) (M1); Portal › cabeçalho de perfil e responsável (B23); PortalNav / Início (M135); Início / Topo (celular) (M1); Financeiro e demais telas do portal (M1); Moldura do portal nas telas do lote (M1)

### Estados: vazio, erro, bloqueio e link vencido sem a ação do canvas

Telas afetadas (17): LoginTravadoM (M37); LinkSenhaVencidoM (M40); ConviteVencidoM (M3); SemAcessoM / GMSemAcesso (M4); GMErro (M46); Comunicados, limite diário (M48); UnidadeDesativar (M63); UnidadeUnica (M64); PagamentosDesconectado (M20); ModalidadeDesativar (M66); Estado de erro (M75); Cobranças (B13); EstadoSemResultados (M5); ProfessorModalidadeRecusa (janela) (M126); Estados do portal (M134); Carteirinha, estado sem conexão (M148); Pedido indisponível (encerramento aberto) (M152)

### Avatar, graduação, selo e colunas que faltam nas tabelas

Telas afetadas (26): EscolhaOrganizacao / EscolhaOrgM (M42); MinhaConta (M27); GMConta (M27); Comunicados (tabela) (M17); Novo comunicado (formulário) (M18); Barra superior (Topbar) (M1); ConfigAcademia (M19); ConfigFaixas (B1); Cobranças (lista) (M13); Benefícios (lista) (M12); Cobranças (tabela) (M76); Turmas (M9); TurmaEditar (M11); TurmaTitularInativo (M95); Alunos (lista, colunas) (M5); Alunos (cabeçalho da página) (M5); Ficha do aluno (cabeçalho) (M98); Ficha, aba Responsáveis (M112); Editar dados pessoais (desktop) (M106); Tema escuro (lista e ficha) (B20); Alunos (tabela) (M5); Professores (lista) (M6); ProfessorNovo (janela) (M124); PedidosPortal (M16); Minhas informações (M31); Portal reduzido › Minhas informações (M31)

### Contas e senhas: duas etapas, regras de senha, convite, aceite

Telas afetadas (15): todas as telas de acesso 390 (M2); DuasEtapasM (M2); NovaSenhaM (M41); PrimeiroAcessoM (M3); PrimeiroAcessoContaM (M3); MinhaConta (M27); MinhaContaNotificacoes (M43); GMConta (M27); Carregando (lista de cobranças) (B12); AulaEditarSeguintes (M86); Alunos (abas, filtros e busca) (M5); PedidosPortal (M16); Avisos (lista) (B2); Termo (desktop e celular) (M144); Termo com 2 filhos (M145)

## 3. Divergências por tela

### Gravidade alta

#### A1 · Menu da gestão

- Rodada 1: Menu da gestão: `/cobrancas` e `/comunicados` existem, mas `available=false` em `menuCatalog.ts` os esconde do menu.
- Print: sem imagem (achado de código/menu).
- **Rodada 2 · comunicacao-e-estrutura-da-gestao-1 · CE1-01** (Comunicados (menu)) — repetido de A1
  - Categoria: botões/ações · Largura/tema: 1440 claro e escuro, 390
  - Prints: sistema `imagens/comunicacao-e-estrutura-da-gestao-1/Comunicados-1440-claro-sistema.png` · canvas `imagens/comunicacao-e-estrutura-da-gestao-1/Comunicados-1440-claro-canvas.png`
  - o menu lateral do sistema não tem "Comunicados" (canvas: grupo "Comunicação" > "Comunicados" ativo). Como o item não está no menu, a trilha da barra superior também some (canvas: "Comunicação › Comunicados"; sistema: barra sem trilha). A tela só se alcança por URL.
  - Código: `frontend/src/features/permissions/menuCatalog.ts:31` (`item("announcements", ..., "/comunicados", "Megaphone")` sem `true`); `components/layout/Breadcrumb.tsx:9-10` retorna null quando o item não está nos grupos.
- **Rodada 2 · financeiro-1 · financeiro-1-01** (Menu lateral (todas as telas do lote)) — repetido de A1
  - Categoria: botões/ações · Largura/tema: 1440 claro
  - Prints: sistema `imagens/financeiro-1/Cobrancas-1440-claro-dados-sistema.png` · canvas `imagens/financeiro-1/CobrancaCancelar-1440-claro-canvas.png`
  - canvas mostra Financeiro aberto com "Visão geral", "Cobranças" e "Planos e benefícios", além de Comunicação e Relatórios. No sistema, em /cobrancas o grupo Financeiro fica fechado (sem item ativo); aberto em /beneficios traz só "Planos" e "Benefícios". Cobranças, Em atraso e Em conferência só chegam por botões da página.
  - Código: frontend/src/features/permissions/menuCatalog.ts:44 (`item("collections", "COLLECTIONS", "/cobrancas", "Wallet")` sem `available=true`; benefícios, linha 46, tem `true`)
- **Rodada 2 · financeiro-2 · F2-01** (Cobranças (menu)) — repetido de A1
  - Categoria: botões/ações · Largura/tema: 1440 claro e 390
  - Prints: sistema `imagens/financeiro-2/Mensalidades-1440-claro-fx-sistema.png` · canvas `imagens/financeiro-2/Mensalidades-canvas.png`
  - o grupo Financeiro do menu lista só Planos e Benefícios; "Cobranças" (e "Visão geral") não existem no menu. No celular a barra inferior mostra "Cobranças" esmaecida. A página só é alcançável por URL.
  - Código: frontend/src/features/permissions/menuCatalog.ts:44 (`item("collections", ...)` sem `available=true`)

#### M7 · Professor/Turma (painel) (reclassificada para alta pelo dono)

- Rodada 1: Professor/Turma (painel): canvas = gaveta lateral a partir da lista; sistema = página separada com "Voltar" e menos blocos.
- Issue própria na Fase 1 do plano (F1-10): o botão "Ver alunos" do painel da turma está sempre desabilitado (`ClassPanelPage.tsx:61-63`); a gaveta lateral entra pelo componente C7.
- Print (sistema à esquerda, canvas à direita): `imagens/rodada-1/75-turma-painel-claro.png`
- **Rodada 2 · operacao-1 · OP1-07** (AgendaPainel) — amplia M7 · gravidade revista de alta para média: O próprio achado diz que não bloqueia.
  - Categoria: modal/gaveta · Largura/tema: 1440 claro
  - Prints: sistema `imagens/operacao-1/AgendaPainel-1440-claro-sistema.png, AgendaPainelFuturo-1440-claro-sistema.png` · canvas `imagens/operacao-1/AgendaPainel-1440-claro-canvas.png`
  - canvas = gaveta lateral à direita sobre a agenda, com selos de modalidade e "Substituição", título e data/hora/unidade, "Professor da aula" com avatar e selo, "Titular da turma", "Alunos da turma", "Aulas experimentais" (nome do experimental), grupo de WhatsApp (link abreviado), "Observação para a equipe", "Histórico" (quem trocou o professor, quando) e rodapé fixo com "Avisar turma" (verde, ícone), "Editar aula", "Ver turma", "Cancelar aula" (vermelho). Sistema = página própria com "Voltar à agenda", selo de situação à direita, quatro botões em linha (Avisar turma, Editar aula, Cancelar aula, Ver turma, todos em contorno, sem ícone nem destaque), cartões "Sobre a aula", "Alunos e aulas experimentais", "Alunos da aula", "Grupo de WhatsApp". Faltam: titular da turma distinto do professor, nome do experimental, observação para a equipe, histórico de alterações, avatar. Não é alta por bloquear fluxo: todas as ações do canvas existem. Reclassificado como média. - Gravidade: média
- **Rodada 2 · operacao-1 · OP1-08** (AgendaPainelProfessor) — amplia M7
  - Categoria: botões/ações, colunas/campos · Largura/tema: 1440 claro
  - Prints: sistema `imagens/operacao-1/AgendaPainelProfessor-1440-claro-sistema.png` · canvas `imagens/operacao-1/AgendaPainelProfessor-1440-claro-canvas.png`
  - canvas do Professor lista os 5 primeiros alunos com avatar, faixa (desenho da faixa e graus) e situação (Ativo/Bloqueado), "Ver os 31" e aviso de que a ficha do aluno não abre sem a permissão; mostra "Editar aula" e "Cancelar aula" no rodapé. Sistema com prof@: só "Avisar turma" (sem editar/cancelar, por permissão do MSW, consistente) e lista de alunos vazia, "Nenhum aluno ativo" (sem dado). Divergência de estrutura: sem lista com faixa/graus e aviso de permissão da ficha. Sem dados na lista; ver "A conferir".
- **Rodada 2 · operacao-2 · operacao-2-05** (TurmaPainel) — amplia M7 · gravidade revista de alta para média: Botão desabilitado fixo em `ClassPanelPage.tsx:61-63` (a linha citada pelo agente era a do grupo de WhatsApp); a lista de alunos aparece na própria página.
  - Categoria: modal/gaveta · Largura/tema: 1440 claro
  - Prints: sistema `imagens/operacao-2/TurmaPainel-1440-claro-cheia-sistema.png` · canvas `imagens/operacao-2/TurmaPainel-canvas.png`
  - canvas = gaveta lateral sobre a lista, com selos (modalidade, "Acima da capacidade"), próxima aula "Hoje, 19h30 · Rafael Duarte", Professores, Capacidade "30 · só avisa", link do WhatsApp, lista de alunos com avatar, "Turma principal"/"Outro horário", faixa com cor e graus, "31 na turma principal · 4 como outro horário" e "Ver todos os alunos"; rodapé fixo com Editar, Avisar turma (verde), Abrir grupo, Desativar. Sistema = página com "Voltar às turmas" e 7 cartões empilhados; sem faixa/graus nem avatar; "Ver alunos" sempre desabilitado (caminho para a lista de alunos da turma inalcançável); próxima aula em ISO "2026-10-29, 19:30–21:00"; dias por extenso; "Ativa" solto à direita.
  - Código: frontend/src/features/classes/ClassPanelPage.tsx:76-80 (botão "Ver alunos" `disabled` fixo); lista de alunos sem faixa em :138
- **Rodada 2 · operacao-2 · operacao-2-06** (GMTurmaPainel) — amplia M7
  - Categoria: layout · Largura/tema: 390 claro
  - Prints: sistema `imagens/operacao-2/TurmaPainel-390-claro-sistema.png` · canvas `imagens/operacao-2/GMTurmaPainel-canvas.png`
  - canvas é tela cheia com cabeçalho "Turma" e seta, selos, resumo em um cartão, botões de largura total, "Desativar turma" em contorno vermelho e "Editar turma" fixo no rodapé. Sistema: sete cartões, cinco botões iguais em linha (sem destaque de perigo), "Voltar às turmas" sublinhado.
- **Rodada 2 · pessoas-2 · P2-04** (ProfessorPainel) — repetido de M7 (amplia)
  - Categoria: modal/gaveta, layout, colunas/campos · Largura/tema: 1440 claro e 390 claro
  - Prints: sistema `imagens/pessoas-2/ProfessorPainel-1440-claro-sistema.png, ProfessorPainel-390-claro-sistema.png` · canvas `imagens/pessoas-2/ProfessorPainel-1440-claro-canvas.png, GMProfessorPainel-360-claro-canvas.png`
  - além de página no lugar da gaveta (M7): sem selos "No diretório"; sem subtítulo "Professor · unidade · acesso ativo · último acesso"; Graduação sem faixa visual e sem bloco "Graduações" separado por modalidade; Apresentação sem a linha de consentimento ("Aparece no diretório (consentimento dado em …)"); Turmas é lista de texto, canvas é tabela Turma/Papel/Horários com selo "Substituição"; Próximas aulas sem dados (MSW); sobram blocos "Unidades" e "Acesso ao sistema"; ações no topo (Editar, Inativar, Dar acesso, WhatsApp) em vez de rodapé da gaveta (Editar, Reenviar convite, WhatsApp, Inativar em vermelho). A 390 o canvas é tela cheia com cabeçalho (avatar, nome, "Jiu-Jitsu · faixa marrom, 2 graus", selo), WhatsApp+Editar lado a lado, cartões Turmas e Acesso e "Inativar professor" em largura cheia; o sistema tem o link "Voltar aos professores" e botões quebrando em 2 linhas.
  - Código: `frontend/src/features/teachers/TeacherPanelPage.tsx`

#### A3 · AulaAvisarTurma **[NOVO R2]**

- Lote/ID de origem: operacao-1 · OP1-15
- Categoria: modal/gaveta, estados · Largura/tema: 1440 claro
- Prints: sistema `imagens/operacao-1/AulaAvisarTurma-1440-claro-sistema.png` · canvas `imagens/operacao-1/AulaAvisarTurma-1440-claro-canvas.png`
- O que difere: canvas = janela "AVISAR TURMA" por cima da agenda, aberta a partir da aula cancelada, com Tipo "Aula cancelada", Destino "Turma ...", Título "Aula de quinta cancelada" e Texto já preenchidos, canais Portal/E-mail/Abrir o grupo no WhatsApp, resumo de alcance, "Agendar" e "Enviar agora". No sistema, "Avisar turma" navega para a página cheia `/comunicados/novo?classId=...&type=...`: Destino "Judô Infantil", **Tipo "Aviso geral"** e Título "Aviso geral", **Texto vazio** (a aula estava cancelada, então o esperado seria tipo "Aula cancelada" e texto pronto), campo de imagem nativo em inglês ("Choose File No file chosen"), sem "Agendar" com horário. A tela perde o breadcrumb e mostra o menu com grupos fechados. O comunicado "já preenchido" do canvas não acontece, então o fluxo cancelar, avisar não entrega o aviso pronto. O painel mostra o bloco "Aviso pronto" com o texto, mas o formulário não o usa.
- Código: `frontend/src/features/agenda/LessonPanelPage.tsx:71` (monta `type`, `date`, `time`) e `frontend/src/features/announcements/NewAnnouncementPage.tsx:53-54` (só lê `classId`; não lê `type`, `date`, `time`)
- Padrão: fluxo de aviso sem pré-preenchimento (ver M18)

#### M123 · FichaProfessor **[NOVO R2]** (reclassificada para alta pelo dono)

- Lote/ID de origem: pessoas-2 · P2-01 · gravidade revista (era alta): RF-ALU-03: o Professor só abre a ficha com a permissão "ver ficha do aluno", desligada por padrão. Bloqueio por padrão é regra, não defeito.
- Categoria: botões/ações (caminho inalcançável) · Largura/tema: 1440 claro
- Prints: sistema `imagens/pessoas-2/FichaProfessor-1440-claro-sistema.png (e FichaProfessor-agenda-1440-claro-sistema.png)` · canvas `imagens/pessoas-2/FichaProfessor-1440-claro-canvas.png`
- O que difere: o canvas mostra o Professor abrindo a ficha do aluno em gaveta (Dados, Modalidades e aviso "Visão do Professor", sem valores/plano/CPF). No sistema, o Professor que abre `/alunos/<id>` cai em "Esta área não está disponível para o seu perfil"; a lista /alunos também. A agenda dele (`/agenda`) não tem nenhum caminho para a ficha. O MSW já tem `GET /students/{id}/record` para TEACHER (handlers.ts ~4423), o front não usa.
- Código: `frontend/src/App.tsx:138` e `:146` (`/alunos/:id?` dentro de `RequireArea roles={["COORDINATOR","ADMINISTRATOR"]}`); `frontend/src/features/agenda/LessonPanelPage.tsx` sem link para `/alunos`. Spec RF-ALU-03 (08-alunos-e-responsaveis.md:44) diz que o Professor abre a ficha dos alunos das suas turmas.
- Padrão: caminho do perfil Professor ausente

#### M135 · PortalNav / Início **[NOVO R2]** (reclassificada para alta pelo dono)

- Lote/ID de origem: portal-a-2 · portal-a-2-02
- Categoria: ícone/avatar/cor · Largura/tema: 1440 claro (e 390 claro)
- Prints: sistema `imagens/portal-a-2/PortalInicio-topo-1440-claro-sistema.png (compare com PortalInicio-1440-escuro-sistema.png, onde o ícone aparece)` · canvas `imagens/portal-a-2/PortalNav-canvas.png`
- O que difere: no tema claro o botão de tema (lua) da barra escura não aparece (ícone escuro sobre barra escura); no escuro aparece (sol). Canvas mostra a lua clara na barra em desktop.
- Código: `frontend/src/features/portal/PortalLayout.tsx:65-67` (cor do wrapper não alcança o ícone do botão)
- Padrão: contraste do ícone na barra escura

#### M153 · Revogar uso de imagem **[NOVO R2]** (reclassificada para alta pelo dono)

- Lote/ID de origem: portal-b-2 · portal-b-2-06
- Categoria: modal/gaveta · Largura/tema: 1440 claro
- Prints: sistema `sem print do estado (o MSW do aluno@ não tem uso de imagem concedido; só aparece "Conceder uso de imagem")` · canvas `imagens/portal-b-2/PortalRevogarImagem-1440-claro-canvas.png`
- O que difere: canvas abre janela de confirmação "REVOGAR USO DE IMAGEM" com texto de efeito, "RESUMO" de 3 itens e "Cancelar"/"Revogar" (vermelho). No código o botão "Revogar uso de imagem" chama `toggle()` e revoga direto, sem confirmação (conferido só no código).
- Código: frontend/src/features/portal/PortalSettings.tsx:55-80
- Padrão: ação sensível sem confirmação

#### B14 · Prévia da geração **[NOVO R2]** (reclassificada para alta pelo dono)

- Lote/ID de origem: financeiro-2 · F2-11
- Categoria: estados / código · Largura/tema: 1440 claro
- Prints: sistema `` · canvas ``
- O que difere: com um `reason` sem chave de tradução (usei um valor inventado na fixture), a tela inteira ficou em branco (erro "Chave de tradução ausente: charges.preview.reasons.X" lançado no render; o `defaultValue` não é usado). Hoje o contrato só emite FULL_BENEFIT, mas ao entrarem as outras razões do canvas a tela quebra se faltar a chave. Código: frontend/src/features/charges/ChargePreviewRows.tsx (t com defaultValue para reasons).
- Padrão: chave de tradução ausente derruba a tela

### Gravidade média

#### A2 · Dias sem aula (rebaixada para média pelo dono)

- Rodada 1: Dias sem aula: falta "Ignorar" nos feriados sugeridos e a tabela de lançados com situação do aviso e "Editar".
- Decisão 15 do dono (30/10): volta para média; fica só na issue O3 do plano.
- Print (sistema à esquerda, canvas à direita): `imagens/rodada-1/26-dias-sem-aula-claro.png`
- **Rodada 2 · operacao-1 · OP1-18** (DiasSemAula) — amplia A2
  - Categoria: colunas/campos, botões/ações · Largura/tema: 1440 claro
  - Prints: sistema `imagens/operacao-1/DiasSemAula-1440-claro-sistema.png, DiasSemAula-1440-claro-fx-sistema.png` · canvas `imagens/operacao-1/DiasSemAula-1440-claro-canvas.png`
  - canvas = "FERIADOS SUGERIDOS" em tabela (Data com dia da semana, Feriado, Aulas afetadas) com estado "Confirmado" + "Desfazer", e "Confirmar" + "Ignorar" por linha; "LANÇADOS" em tabela (Data ou período, Motivo, Turmas, Origem, Aviso com selo colorido Enviado/Agendado para 01/12/Ainda não avisado, "Editar"/"Avisar"). Sistema (com fixture) = cartão "Feriados nacionais sugeridos" em lista (data + nome + "Confirmar"), lista rolável com altura limitada (corta o último "Confirmar" no vazio) e "Próximos dias sem aula" em cartões com 4 linhas de texto cru ("Origem: Feriado / Todas as turmas / Aviso: Enviado / 5 aulas canceladas") e botões Editar / Desfazer / Avisar. Faltam: "Ignorar", coluna "Aulas afetadas" nas sugestões, dia da semana, selo colorido de aviso, o tom de tabela. "Editar", "Desfazer" e "Avisar" existem (A2 dizia que faltava "Editar" na tabela; existe nos cartões). Sem dados, o vazio mostra "Nenhum dia sem aula" abaixo (canvas não tem esse estado).
  - Código: `frontend/src/features/noClassDays/NoClassDaysPage.tsx:60-125`
- **Rodada 2 · operacao-1 · OP1-21** (DiaSemAulaNovo) — amplia A2
  - Categoria: colunas/campos · Largura/tema: 1440 claro
  - Prints: sistema `imagens/operacao-1/DiaSemAulaNovo-1440-claro-sistema.png` · canvas `imagens/operacao-1/DiaSemAulaNovo-1440-claro-canvas.png`
  - canvas = "De" / "Até (opcional)" com ícone de calendário, "Motivo *", "Turmas" (seletor), "Unidades" em chips (Coordenador, várias unidades), caixa "PRÉVIA" (38 aulas ficam canceladas, nenhuma experimental, mensalidade não muda), "Quando avisar" em cartões (Agora, Agendar com data e hora, Não avisar agora), botão "Lançar dia sem aula". Sistema = "Unidade: Unidade Centro" texto, Início/Fim nativos (obrigatórios, em vez de "Até opcional"), Motivo, Turmas em rádios ("Todas as turmas / Só algumas turmas"), caixa "Aulas que serão canceladas" (prévia mais pobre), "Avisar os alunos" em rádios (Não avisar / Avisar agora / Agendar o aviso, sem escolher data), frase "A mensalidade não muda." e botão "Salvar". Sem chips de unidades (por ser Administrador no sistema? ana@ é Coordenador, mas o MSW traz 1 unidade).

#### M1 · Estrutura do portal

- Rodada 1: Estrutura do portal: coluna ~410 px (canvas ~620), sem rótulo acima do título, barra com texto "Ana · Aluno / Minha conta" em vez de avatar+nome, tema e sino.
- Print (sistema à esquerda, canvas à direita): `imagens/rodada-1/07-portal-aluno-claro.png`
- **Rodada 2 · comunicacao-e-estrutura-da-gestao-1 · CE1-02** (Estrutura da gestão (menu lateral)) — amplia M1
  - Categoria: ícone/avatar/cor, layout · Largura/tema: 1440 claro
  - Prints: sistema `imagens/comunicacao-e-estrutura-da-gestao-1/Comunicados-1440-claro-sistema.png` · canvas `imagens/comunicacao-e-estrutura-da-gestao-1/Comunicados-1440-claro-canvas.png`
  - (a) grupos do menu (Pessoas, Operação, Financeiro, Configurações) sem ícone e com seta para baixo; canvas tem ícone à esquerda e seta para a direita (para baixo só no grupo aberto); (b) marca da organização: sistema mostra quadrado escuro com letra "C"; canvas mostra logo colorido "AL" e o nome em fonte de título maiúscula; (c) "Ajuda" e "Novidades" aparecem com "(Em breve)" e apagados; no canvas estão ativos e "Novidades" tem ponto vermelho; (d) largura 256 x 248 px.
  - Código: `components/layout/Sidebar.tsx:99` (só ChevronDown, sem ícone de grupo); `Sidebar.tsx:108` (rodapé).
- **Rodada 2 · comunicacao-e-estrutura-da-gestao-1 · CE1-03** (Estrutura da gestão (barra superior)) — amplia M1
  - Categoria: layout, botões/ações, ícone/avatar/cor · Largura/tema: 1440 claro e escuro
  - Prints: sistema `imagens/comunicacao-e-estrutura-da-gestao-1/Comunicados-1440-claro-sistema.png` · canvas `imagens/comunicacao-e-estrutura-da-gestao-1/Comunicados-1440-claro-canvas.png`
  - canvas tem trilha à esquerda, seletor de unidade (ícone de prédio + "Tijuca ⌄"), busca ativa com "Ctrl K", sino com contagem vermelha, tema, ajuda e conta (avatar com iniciais em círculo escuro/azul + nome completo + perfil + seta de menu), com divisória. Sistema: sem trilha (CE1-01); sem seletor de unidade (`UnitSwitcher` só aparece com 2 ou mais unidades; Ana no MSW mostra uma só; o "não confirmado" da rodada 1 fica explicado: oculto com 1 unidade); Buscar e Ajuda desativados e apagados; sino com contagem preta; avatar de uma letra rosada, só "Ana" e "Coordenação" (canvas "Coordenador"/"Administradora"), sem seta.
  - Código: `components/layout/Topbar.tsx:21-27` (busca e ajuda `disabled`); `features/units/UnitSwitcher.tsx:27`; `i18n/pt-BR.json` `layout.roles.COORDINATOR` = "Coordenação".
- **Rodada 2 · comunicacao-e-estrutura-da-gestao-2 · CE2-01** (Menu lateral e moldura) — amplia M1/A1
  - Categoria: layout / ícone/avatar/cor / botões/ações · Largura/tema: 1440 claro
  - Prints: sistema `imagens/comunicacao-e-estrutura-da-gestao-2/Sidebar-1440-claro-sistema.png` · canvas `imagens/comunicacao-e-estrutura-da-gestao-2/Sidebar-canvas.png`
  - canvas tem marca "AL" laranja arredondada e nome da academia em fonte condensada maiúscula, grupos Pessoas/Operação/Financeiro/Comunicação/Relatórios/Configurações com ícone à esquerda e seta ">" à direita (6 grupos), "Relatórios" como item direto. Sistema tem marca "C" cinza escura, nome em fonte normal, grupos só em texto com seta "v" e sem ícone (Operação, Pessoas, Financeiro, Configurações; ordem Operação antes de Pessoas), sem os grupos "Comunicação" e "Relatórios". Rodapé: canvas "Ajuda" e "Novidades" ativos (Novidades com ponto laranja); sistema mostra "Ajuda (Em breve)" e "Novidades (Em breve)" apagados. "Recolher menu": canvas seta simples "<", sistema "«".
  - Código: `frontend/src/features/permissions/menuCatalog.ts` (grupo `reports` e `announcements`/`collections` com `available=false`; não há grupo `communication`); `frontend/src/components/layout/Topbar.tsx` (busca e ajuda `disabled`).
- **Rodada 2 · comunicacao-e-estrutura-da-gestao-2 · CE2-02** (Barra superior (Topbar)) — amplia M1
  - Categoria: layout / botões/ações · Largura/tema: 1440 claro
  - Prints: sistema `imagens/comunicacao-e-estrutura-da-gestao-2/Topbar-1440-claro-unidades-sistema.png e Main-1440-claro-sistema.png` · canvas `imagens/comunicacao-e-estrutura-da-gestao-2/Topbar-canvas.png`
  - ordem no canvas: seletor de unidade (botão com ícone de prédio, nome e seta) · busca larga ativa "Buscar  Ctrl K" · sino com selo vermelho · tema · ajuda · divisor · avatar (iniciais, azul) + nome + perfil + seta. Sistema: sino · avatar rosa com inicial · nome/perfil (sem seta) · busca (apagada, desabilitada, menor) · tema · ajuda. Seletor de unidade é `<select>` nativo (visível só com 2+ unidades, e no MSW padrão não aparece nem para o Coordenador). Selo do sino é preto (canvas vermelho). Na largura 1440 com o seletor, a barra quebra em duas linhas (ajuda cai para baixo; trilha desce).
  - Código: `frontend/src/features/permissions/ManagementLayout.tsx:45-48`, `frontend/src/features/units/UnitSwitcher.tsx` (`Select` nativo), `frontend/src/components/layout/Topbar.tsx`.
- **Rodada 2 · configuracoes-2 · configuracoes-2-14** (Moldura (menu lateral e barra) em todas as telas do lote) — amplia M1/M24
  - Categoria: layout · Largura/tema: 1440 claro
  - Prints: sistema `imagens/configuracoes-2/ConfigUsuarios-1440-claro-sistema.png` · canvas `imagens/configuracoes-2/ConfigUsuarios-1440-claro-canvas.png`
  - o submenu de Configurações do sistema lista "Configurações da unidade", "Configurações da organizaç..." (truncado), "Faixas etárias", "Usuários da gestão"; o canvas usa "Organização, Unidades, Unidade, Modalidades, Faixas etárias, Usuários, Termos, Textos, Assinatura, Auditoria, Importar e exportar". Faltam no menu Comunicação, Relatórios, Assinatura, Auditoria e Importar e exportar (telas de sprints futuras, `menuCatalog.ts:64-66`). Barra de cima: canvas tem seletor "Todas as unidades"; sistema não mostra (não confirmado se oculto por haver uma unidade); marca "Academia Alfa" sem logo AL em cor. "Ajuda/Novidades (Em breve)" no sistema.
- **Rodada 2 · financeiro-2 · F2-05** (Cobranças) — repetido de M1
  - Categoria: layout · Largura/tema: 1440 claro/escuro
  - Prints: sistema `` · canvas ``
  - barra superior sem trilha "Financeiro › Cobranças", sem seletor de unidade nem avatar+nome; menu com grupos recolhidos e sem destaque do item ativo. Escuro: coerente com o canvas (sem problema de contraste).
- **Rodada 2 · operacao-2 · operacao-2-10** (Turmas (estrutura)) — amplia M1
  - Categoria: ícone/avatar/cor · Largura/tema: 1440 claro/escuro
  - Prints: sistema `imagens/operacao-2/Turmas-1440-escuro-cheia-sistema.png` · canvas `Turmas-1440-claro-canvas.png`
  - barra sem seletor de unidade; menu sem "Aulas experimentais", "Graduações", "Comunicação", "Relatórios" (não construídas ou ocultas); "Ajuda/Novidades (Em breve)". Escuro 1440 e 390: legível, sem problema de contraste.
- **Rodada 2 · portal-a-1 · PA-01** (Portal › estrutura (moldura)) — repetido de M1
  - Categoria: layout · Largura/tema: 1440 claro e 390 claro (todas as telas do lote)
  - Prints: sistema `imagens/portal-a-1/PortalAgenda-1440-claro-sistema.png` · canvas `imagens/portal-a-1/PortalAgenda-1440-claro-canvas.png`
  - sistema usa coluna estreita (~735 px) centralizada, título sem rótulo acima ("SÓ CONSULTA", "DA ACADEMIA", "FINANCEIRO", data no Início), barra com "Ana · Aluno / Minha conta" em texto sublinhado (canvas: avatar com iniciais + nome + perfil), sem botão de tema na barra a 1440 claro (aparece no escuro, à direita), sem sublinhado vermelho na aba ativa (aba ativa vira botão laranja cheio), rodapé em duas linhas à esquerda (canvas: endereço à esquerda, "feito com ctcore" à direita). A 390 a barra superior fica alta (2 linhas) com sino e "Minha conta" soltos; canvas tem marca, sino e avatar em uma linha e sem endereço no rodapé.
- **Rodada 2 · portal-a-2 · portal-a-2-01** (PortalNav / Início) — amplia M1
  - Categoria: layout, ícone/avatar/cor · Largura/tema: 1440 claro
  - Prints: sistema `imagens/portal-a-2/PortalInicio-topo-1440-claro-sistema.png` · canvas `imagens/portal-a-2/PortalNav-canvas.png`
  - canvas: marca com fonte condensada em caixa alta, abas com sublinhado laranja na ativa, conteúdo da barra alinhado à coluna de 1120 px, sino, avatar com iniciais + nome + perfil. Sistema: marca em fonte comum, aba ativa em pílula laranja cheia, barra de borda a borda, texto "Ana · Aluno" e link sublinhado "Minha conta" no lugar do avatar.
  - Código: `frontend/src/features/portal/PortalLayout.tsx:62-63`
- **Rodada 2 · portal-a-2 · portal-a-2-11** (Início / Topo (celular)) — amplia M1
  - Categoria: layout · Largura/tema: 390 claro
  - Prints: sistema `imagens/portal-a-2/PortalInicio-fx-390-claro-sistema.png, imagens/portal-a-2/PortalMTopo-390-claro-sistema.png` · canvas `imagens/portal-a-2/PortalMInicio-canvas.png, imagens/portal-a-2/PortalMTopo-canvas.png`
  - canvas: barra de 56 px numa linha (marca, sino com contador, avatar). Sistema: barra de ~108 px em duas linhas (marca; depois sino e link "Minha conta" deslocados à direita); a rota de conta é um link sublinhado, não um avatar.
  - Código: `PortalLayout.tsx:41-69` (`flex-wrap`)
- **Rodada 2 · portal-b-1 · PB1-03** (Financeiro e demais telas do portal) — repetido de M1
  - Categoria: layout · Largura/tema: 1440 claro, 390 claro
  - Prints: sistema `` · canvas ``
  - falta o rótulo acima do título ("UNIDADE TIJUCA", "MINHAS INFORMAÇÕES"); coluna estreita (~735 px; canvas ~1120 no desktop); barra com "Ana · Aluno / Minha conta" sublinhado em vez de avatar+nome+perfil; aba ativa em pílula laranja (canvas: sublinhado); a 390 px a barra de cima ocupa duas linhas (canvas: uma linha com sino e avatar).
- **Rodada 2 · portal-b-2 · portal-b-2-07** (Moldura do portal nas telas do lote) — repetido de M1
  - Categoria: layout · Largura/tema: 1440 e 390 claro
  - Prints: sistema `` · canvas ``
  - sistema mostra "Ana · Aluno / Minha conta" em vez de avatar+nome+perfil e botão de tema; sem aba ativa sublinhada na página de pedidos; coluna central estreita; no celular o sino fica numa segunda linha (canvas: logo, sino e avatar numa linha, com "Voltar"). Tema escuro do sistema (PortalPedidos-1440-escuro-sistema.png) sem problema visível de contraste.

#### M2 · Login e acesso

- Rodada 1: Login e acesso: marca só texto sem ícone, slogan diferente, falta subtítulo do "Entrar", 2FA com campo único (canvas: 6 caixas).
- Print (sistema à esquerda, canvas à direita): `imagens/rodada-1/01-entrar-claro.png`
- **Rodada 2 · acesso · acesso-01** (LoginM) — amplia M2
  - Categoria: layout / texto / ícone · Largura/tema: 390 claro (e escuro)
  - Prints: sistema `imagens/acesso/LoginM-390-claro-sistema.png` · canvas `imagens/acesso/LoginM-390-claro-canvas.png`
  - canvas mostra o ícone "ct" laranja + "ctcore" no topo, bloco alinhado ao topo (título logo abaixo da marca), subtítulo "Gestão e portal do aluno, com a mesma conta.", "Esqueci minha senha" alinhado à direita entre a senha e o botão, "Mostrar" dentro do campo de senha, ajuda em uma linha cinza ("Precisa de ajuda? suporte@ctcore.com.br · WhatsApp (21) 4000-1234"). Sistema: marca só texto "CTCORE" com botão de tema, bloco centralizado verticalmente (grande vazio no topo), sem subtítulo, "Mostrar" é botão separado ao lado do campo, "Esqueci minha senha" abaixo do botão, ajuda em bloco "Precisa de ajuda? / E-mail: ..." sem WhatsApp, rótulos maiores/escuros.
  - Código: `frontend/src/components/layout/AuthShell.tsx:40` (main com `justify-center`)
- **Rodada 2 · acesso · acesso-02** (todas as telas de acesso 390) — amplia M2
  - Categoria: layout / ícone · Largura/tema: 390 claro e escuro
  - Prints: sistema `` · canvas ``
  - em todas as telas do grupo (login, duas etapas, recuperar, nova senha, convite) o sistema repete: marca em texto sem ícone, botão lua/sol de tema no topo (canvas não tem), conteúdo centralizado na vertical, campo de senha com botão "Mostrar" fora do campo, texto de ajuda em bloco com título. Canvas: tudo no topo, "Mostrar" embutido, ajuda em uma linha com WhatsApp. Tema escuro do sistema ok (contraste e botões legíveis; vistos Login, Escolha, Convite, Minha conta, Minha conta 360).
- **Rodada 2 · acesso · acesso-04** (DuasEtapasM) — amplia M2
  - Categoria: texto / colunas-campos / botões · Largura/tema: 390 claro
  - Prints: sistema `imagens/acesso/DuasEtapasM-390-claro-sistema.png` · canvas `imagens/acesso/DuasEtapasM-390-claro-canvas.png`
  - título "Duas etapas" (canvas) x "Verificação em duas etapas" (sistema, quebra em duas linhas); canvas tem 6 caixas de dígito (cada uma com a mesma largura, a atual em destaque), sistema um campo único "Código do app"; canvas tem "Usar um código de reserva" como link e nota "O código vale 5 minutos depois da senha. Errando 5 vezes, você volta para o login."; sistema tem botões "Usar um código de reserva" (secundário) e "Voltar" e a frase "Perdeu o celular e os códigos de reserva? Abra um chamado com o suporte." (o canvas não tem "Voltar" nem essa frase).
  - Código: `frontend/src/features/auth/CodeField.tsx:29-36` (input único, `maxLength` 6)

#### M3 · Convite

- Rodada 1: Convite: canvas dá boas-vindas, mostra quem convidou/perfil/validade e regras de senha com ✓; sistema "CRIAR SUA SENHA" com nome/e-mail editáveis.
- Print (sistema à esquerda, canvas à direita): `imagens/rodada-1/55-convite-claro.png`
- **Rodada 2 · acesso · acesso-09** (PrimeiroAcessoM) — amplia M3
  - Categoria: texto / campos · Largura/tema: 390 claro
  - Prints: sistema `imagens/acesso/PrimeiroAcessoM-390-claro-sistema.png` · canvas `imagens/acesso/PrimeiroAcessoM-390-claro-canvas.png`
  - canvas subtítulo "Convite da Academia Leão · vale até 15/10/2026" (organização + validade no subtítulo), Nome e E-mail como campos somente leitura (fundo acinzentado), regras com ✓/✗, "Repita a senha". Sistema: Nome e E-mail editáveis (campos brancos), "Vale até 31/12/2026 12h30" solto, sem nome da organização, regras "pendente", "Confirmar senha".
  - Código: `frontend/src/features/auth/InvitePage.tsx`
- **Rodada 2 · acesso · acesso-10** (PrimeiroAcessoContaM) — amplia M3
  - Categoria: texto / campos / botões · Largura/tema: 390 claro
  - Prints: sistema `imagens/acesso/PrimeiroAcessoContaM-390-claro-sistema.png` · canvas `imagens/acesso/PrimeiroAcessoContaM-390-claro-canvas.png`
  - canvas "Aceitar convite", subtítulo "Você já tem conta no ctcore com este e-mail.", cartão do convite com ícone de pessoas ("Professor na Academia Leão", "Unidades Tijuca e Barra · convite de Carla Mendes"), E-mail somente leitura, "Sua senha atual" com "Mostrar", botão "Aceitar e entrar" e link "Esqueci minha senha". Sistema: sem cartão do vínculo (perfil, organização, quem convidou), E-mail em campo editável, "Sua senha atual" sem "Mostrar", botão "Aceitar convite e entrar", sem "Esqueci minha senha".
- **Rodada 2 · acesso · acesso-11** (ConviteVencidoM) — amplia M3
  - Categoria: texto / botões · Largura/tema: 390 claro
  - Prints: sistema `imagens/acesso/ConviteVencidoM-390-claro-sistema.png` · canvas `imagens/acesso/ConviteVencidoM-390-claro-canvas.png`
  - canvas "Este convite venceu", "Convites valem 7 dias e só podem ser usados uma vez.", orientação com o nome da academia e botão "Ir para o login" ("Se você já criou a sua senha, é só entrar"). Sistema: "Este convite não é válido", faixa âmbar "Este convite venceu. Os convites valem por 7 dias.", "Peça um novo convite a quem convidou você." e nenhum botão.

#### M4 · Sem acesso

- Rodada 1: Sem acesso: canvas mantém menu e barra e tem "Ver minhas turmas"; sistema usa layout de login.
- Print (sistema à esquerda, canvas à direita): `imagens/rodada-1/58-sem-acesso-claro.png`
- **Rodada 2 · acesso · acesso-13** (SemAcessoM / GMSemAcesso) — amplia M4
  - Categoria: layout / texto / ícone · Largura/tema: 390 e 360 claro (e escuro)
  - Prints: sistema `imagens/acesso/SemAcessoM-390-claro-sistema.png, imagens/acesso/GMSemAcesso-360-claro-sistema.png` · canvas `imagens/acesso/SemAcessoM-390-claro-canvas.png, imagens/acesso/GMSemAcesso-360-claro-canvas.png`
  - canvas tem ícone de cadeado em quadro arredondado acima do título, texto "Se você acha que deveria ter acesso, fale com o Administrador da sua unidade." e botão de largura total "Ir para o Início"; no canvas de gestão (GMSemAcesso) a tela fica dentro do app (cabeçalho "Financeiro" com unidade, sino, busca e barra inferior). Sistema: sem ícone, texto "O seu perfil não permite abrir este endereço. Volte ao Início ou, se precisar, troque de organização ou área.", botão pequeno (não ocupa a largura), na estrutura de login sem menu nem barra do app, texto colado ao título.
  - Código: `frontend/src/features/auth/AreaUnavailablePage.tsx:5-16`
- **Rodada 2 · financeiro-1 · financeiro-1-17** (Sem permissão) — repetido de M4
  - Categoria: estados, layout · Largura/tema: 1440 claro
  - Prints: sistema `imagens/financeiro-1/EstadoSemAcesso-1440-claro-sistema.png` · canvas `imagens/financeiro-1/EstadoSemAcesso-1440-claro-canvas.png`
  - canvas (Professor em /cobrancas) mantém menu do Professor e barra, cadeado, "ESTA ÁREA NÃO ESTÁ DISPONÍVEL PARA O SEU PERFIL", texto personalizado ("Valores e cobranças ficam com a gestão da unidade... fale com a Carla Mendes (Administradora)") e botões "Voltar ao Início" e "Ver minhas turmas". Sistema troca todo o layout pelo de login (painel preto "SUA ACADEMIA, EM ORDEM." e mensagem curta), sem menu nem barra, texto genérico, só "Ir para o Início", mais e-mail de suporte.

#### M5 · Alunos

- Rodada 1: Alunos: faltam coluna Graduação, WhatsApp, Exportar CSV; busca é caixa vazia; filtros nativos (canvas: pílulas); abas "Ativos (0)" com 2 linhas.
- Print (sistema à esquerda, canvas à direita): `imagens/rodada-1/23-alunos-claro.png`
- **Rodada 2 · financeiro-2 · F2-03** (Cobranças) — amplia M5 e M13
  - Categoria: filtros/abas/busca · Largura/tema: 1440 claro e escuro
  - Prints: sistema `imagens/financeiro-2/Mensalidades-1440-claro-fx-sistema.png` · canvas `imagens/financeiro-2/Mensalidades-canvas.png`
  - canvas = pílulas (Todas 128, Em atraso 11, Vence hoje 2, Em conferência 3, Pagas no mês 61) com contagem em chip; busca "Aluno, CPF ou referência" com ícone; filtros-pílula "Período Outubro/2026", Situação, Tipo, Modalidade, Turma. Sistema: caixa de busca vazia sem placeholder nem ícone; pílulas "Em atraso (11)" sem "Todas" e sem estado ativo; dois campos de data nativos "dd/mm/yyyy" (Vencimento de/até) em vez de Período; seletores nativos com rótulo acima; seletor "Unidade" extra.
- **Rodada 2 · financeiro-2 · F2-14** (Tabela de planos) — amplia M5 (busca)
  - Categoria: filtros/abas/busca · Largura/tema: 1440 claro
  - Prints: sistema `imagens/financeiro-2/PlanosRegras-1440-claro-sistema.png` · canvas `imagens/financeiro-2/Planos-canvas.png`
  - caixa de busca grande vazia (sem placeholder), seletores nativos Modalidade/Unidade/Situação; canvas só tem pílula "Modalidade Todas".
- **Rodada 2 · pessoas-1-1 · pessoas-1-1-01** (Alunos (lista, colunas)) — amplia M5
  - Categoria: colunas/campos · Largura/tema: 1440 claro
  - Prints: sistema `imagens/pessoas-1-1/Alunos-1440-claro-sistema.png` · canvas `imagens/pessoas-1-1/Alunos-1440-claro-canvas.png · Diferença: imagens/pessoas-1-1/Alunos-1440-claro-diff.png`
  - canvas tem colunas Aluno (avatar colorido, nome, "27 anos · Tijuca", selo colorido ao lado), Modalidades e turmas (bolinha da cor da modalidade, nome em negrito, turma em cinza), Graduação (faixa desenhada + "Faixa azul · 2 graus"), Situação (selo verde "Ativo"; "Inativo" + "Bloqueada por atraso"), Financeiro (selo "Em dia" verde / "Em atraso" vermelho / "Vence hoje" / "Em conferência") e botão WhatsApp por linha. Sistema: tabela de texto puro com Nome, Modalidades, Situação, Situação financeira, Unidade, Idade, Faixa etária e Cadastrado em; avatar cinza com iniciais; situação e financeiro sem selo nem cor; selos do aluno (ex.: "Cadastro incompleto") em contorno neutro; sem coluna Graduação e sem WhatsApp na lista. Idade e unidade viraram colunas em vez de linha secundária sob o nome.
  - Código: `frontend/src/features/students/StudentsPage.tsx:37-80` (`allColumns`) e `useStudentColumns.ts` (opcionais).
- **Rodada 2 · pessoas-1-1 · pessoas-1-1-02** (Alunos (abas, filtros e busca)) — amplia M5
  - Categoria: filtros/abas/busca · Largura/tema: 1440 claro (e 390)
  - Prints: sistema `imagens/pessoas-1-1/Alunos-1440-claro-sistema.png` · canvas `imagens/pessoas-1-1/Alunos-1440-claro-canvas.png`
  - canvas tem abas em pílula "Ativos 131 / Em atraso 9 / Bloqueados 3 / Ex-alunos 46" (contagem em chip), busca com lupa e placeholder "Nome, CPF, e-mail ou telefone", e filtros em pílula com o rótulo dentro ("Modalidade Todas", "Turma", "Nível", "Faixa etária", "Financeiro", "Selos"). Sistema: 3 botões retangulares com contagem entre parênteses (Ativos, Ex-alunos, Em atraso; falta Bloqueados, que depende da Sprint 12), uma caixa de busca vazia (sem lupa, sem placeholder, sem rótulo), filtros como seletores nativos com rótulo acima e textos "Todas as unidades/faixas/selos/modalidades/turmas/níveis"; sobra o filtro "Unidade" (no canvas a unidade fica no seletor da barra superior) e falta o filtro "Financeiro" (só existe o botão rápido "Em atraso").
  - Código: `frontend/src/features/students/StudentFilters.tsx:7,40-60`.
- **Rodada 2 · pessoas-1-1 · pessoas-1-1-03** (Alunos (cabeçalho da página)) — amplia M5
  - Categoria: botões/ações · Largura/tema: 1440 claro
  - Prints: sistema `imagens/pessoas-1-1/Alunos-1440-claro-sistema.png` · canvas `imagens/pessoas-1-1/Alunos-1440-claro-canvas.png`
  - canvas: título, subtítulo "Unidade Tijuca · 131 ativos · 1 ficha por pessoa na organização" e, à direita, "Exportar CSV" (contorno) e "Nova matrícula" (primário com +); a escolha de colunas é o botão "Colunas" com ícone, à direita dos filtros. Sistema: título, texto de introdução longo, "Nova matrícula" abaixo do texto, sem Exportar CSV, e "Escolher colunas" como um `<details>` nativo em negrito que ocupa uma linha inteira. Paginação: canvas "1–25 de 131 · Por página [25] ‹ ›"; sistema "3 resultados · Itens por página · Página anterior / Página 1 de 1 / Próxima página" com botões de texto.
  - Código: `frontend/src/features/students/StudentsPage.tsx:90-103`.
- **Rodada 2 · pessoas-1-2 · L1-02** (EstadoSemResultados) — amplia M5
  - Categoria: estados · Largura/tema: 1440 claro
  - Prints: sistema `imagens/pessoas-1-2/EstadoSemResultados-1440-claro-sistema.png` · canvas `imagens/pessoas-1-2/EstadoSemResultados-1440-claro-canvas.png`
  - canvas mantém o cabeçalho da tabela (Aluno, Modalidades e turmas, Graduação, Situação, Financeiro) dentro de um cartão e mostra "NENHUM ALUNO ENCONTRADO", texto com o termo e os filtros ativos ("A busca ignora acentos.") e dois botões, "Limpar filtros" (secundário) e "Nova matrícula". Sistema: sem cabeçalho de tabela nem cartão, título genérico "Nenhum resultado", "Nenhum item bate com a busca ou com os filtros." e só "Limpar filtros" (primário).
- **Rodada 2 · pessoas-1-2 · L1-03** (Alunos (barra de filtros)) — amplia M5
  - Categoria: filtros/abas/busca · Largura/tema: 1440 claro
  - Prints: sistema `imagens/pessoas-1-2/EstadoSemResultados-1440-claro-sistema.png` · canvas `...-canvas.png`
  - canvas tem abas em pílula com contagem ("Ativos 131", "Em atraso 9", "Bloqueados 3", "Ex-alunos 46"), busca com ícone e placeholder "Nome, CPF, e-mail ou telefone", filtros em pílula (Modalidade, Turma, Nível, Faixa etária, Financeiro, Selos) e botão "Colunas" à direita; subtítulo "Unidade Tijuca · 131 ativos · 1 ficha por pessoa na organização". Sistema: não há aba "Bloqueados"; contagem "(0)" entre parênteses; busca sem ícone nem placeholder (caixa vazia); seis seletores nativos com rótulo acima (inclui "Unidade", falta "Financeiro"; "Nível" desabilitado); "Escolher colunas" como texto solto acima da busca; subtítulo genérico; botão "Buscar" da barra desabilitado ("Em breve").
- **Rodada 2 · pessoas-1-2 · L1-04** (Alunos (tabela)) — amplia M5
  - Categoria: colunas/campos · Largura/tema: 1440 claro
  - Prints: sistema `imagens/pessoas-1-2/Ficha-1440-claro-sistema.png (lista ao fundo)` · canvas `imagens/pessoas-1-2/ExportarAlunos-1440-claro-canvas.png`
  - canvas: avatar colorido com iniciais, nome + "idade · unidade", modalidades com ponto de cor, turma e nível, Graduação (faixa visual), Situação e Financeiro em chips, botão WhatsApp por linha, paginação "1–25 de 131". Sistema: colunas Nome, Modalidades, Situação, Situação financeira, Unidade, Idade, Faixa etária, Cadastrado em (texto simples; sem Graduação nem WhatsApp; avatar cinza; selo "Cadastro incompleto" em contorno).
- **Rodada 2 · pessoas-1-2 · L1-05** (Ficha do aluno, cabeçalho e Resumo) — amplia M5
  - Categoria: layout/botões · Largura/tema: 390 claro (1440 de apoio)
  - Prints: sistema `imagens/pessoas-1-2/GMFicha-390-claro-sistema.png` · canvas `imagens/pessoas-1-2/GMFicha-360-claro-canvas.png`
  - canvas: tela cheia com seta de voltar, "Ficha do aluno" e unidade; avatar grande colorido, nome em fonte de título, "idade · desde mm/aaaa", chips Ativo/Em atraso, botões "WhatsApp" (verde) e "Cobrar", abas rolando na horizontal, aviso de bloqueio ("Muay Thai bloqueia em 10/10..."), cartões Modalidades (ponto de cor, turma, chip Ativa) e Contato (celular, e-mail, emergência, próximo vencimento). Sistema: painel com "X" (sem voltar nem unidade), botões "WhatsApp" e "Editar dados" (sem "Cobrar"), sem aviso de bloqueio nem próximo vencimento, avatar cinza.

#### M6 · Professores

- Rodada 1: Professores: faltam avatar, coluna Graduação, WhatsApp; busca vazia; subtítulo sem contagem.
- Print (sistema à esquerda, canvas à direita): `imagens/rodada-1/24-professores-claro.png`
- **Rodada 2 · pessoas-2 · P2-02** (Professores (lista)) — amplia M6
  - Categoria: colunas/campos, botões/ações, filtros/abas/busca, layout · Largura/tema: 1440 claro e 390 claro
  - Prints: sistema `imagens/pessoas-2/Professores-1440-claro-sistema.png, Professores-390-claro-sistema.png` · canvas `imagens/pessoas-2/Professores-1440-claro-canvas.png, GMProfessores-360-claro-canvas.png`
  - (a) avatar já existe (M6 desatualizado quanto a isso), mas nome é link sublinhado; canvas é texto em negrito sem sublinhado; (b) falta coluna Graduação (faixa visual + "Faixa preta · 3 graus") e botão WhatsApp por linha; (c) busca é caixa vazia sem ícone nem placeholder "Nome"; (d) filtros são rótulo acima + select nativo em coluna ("Todas as modalidades"); canvas usa seletores compactos "Modalidade Todas", com ponto de cor na modalidade; (e) situação padrão "Todas" (canvas "Ativos"); (f) subtítulo genérico, canvas "6 professores · 5 com acesso"; (g) coluna Modalidades sem bolinha de cor e com a graduação embutida; (h) rodapé do canvas "O Professor nunca vê valores…" ausente; Situação "Convite enviado" ausente (sistema só Ativo/Inativo no mock); (i) paginação "Itens por página" com select nativo; (j) a 390 a tabela estoura a largura e corta a coluna Turmas, sem cartões como no GMProfessores (cartão com avatar, modalidade · unidade, selo, busca "Nome do professor" e botão "Novo professor" cheio no rodapé); (k) barra do celular com migalha "Pe… > Prof…" truncada, canvas tem voltar + título + seletor de unidade.
  - Código: `frontend/src/features/teachers/TeachersPage.tsx` (sem `cards`; nenhuma coluna de graduação)

#### M8 · Agenda

- Rodada 1: Agenda: canvas grade semanal por horas, alternador de semana, botão "Dias sem aula", legenda; sistema lista por faixa de horário.
- Print (sistema à esquerda, canvas à direita): `imagens/rodada-1/25-agenda-claro.png`
- **Rodada 2 · operacao-1 · OP1-01** (Agenda) — amplia M8 · gravidade revista de alta para média: "Dias sem aula" tem item próprio no menu (`menuCatalog.ts:27`); só falta o atalho na agenda.
  - Categoria: botões/ações · Largura/tema: 1440 claro
  - Prints: sistema `imagens/operacao-1/Agenda-1440-claro-sistema.png` · canvas `imagens/operacao-1/Agenda-1440-claro-canvas.png`
  - o canvas tem o botão "Dias sem aula" ao lado de "Nova aula avulsa" no cabeçalho da Agenda. No sistema não há: a página só tem "Nova aula avulsa". O único caminho para `/dias-sem-aula` é o item do menu lateral (no celular, o menu "Menu"). Detalhe novo sobre M8 (que citava o botão só como visual).
  - Código: `frontend/src/features/agenda/AgendaPage.tsx:51-58` (PageHeader só com `Nova aula avulsa`)
- **Rodada 2 · operacao-1 · OP1-02** (Agenda) — amplia M8
  - Categoria: layout · Largura/tema: 1440 claro, escuro
  - Prints: sistema `imagens/operacao-1/Agenda-1440-claro-sistema.png` · canvas `imagens/operacao-1/Agenda-1440-claro-canvas.png`
  - canvas = grade com escala de horas na vertical (7h a 21h), cartão posicionado e dimensionado pela duração, coluna do dia sem aula hachurada com cartão "Sem aula + motivo", cabeçalho de dia com número grande, hoje destacado, cor por modalidade (fundo azul/vermelho suave), selo "N experimental(is)" e "Substituição", cartão com faixa lateral. Sistema = linhas por faixa de início (18:00, 19:00), cartões brancos com borda colorida e horário "18:00–19:00" no topo, sem escala de horas, dia sem aula só como texto no cabeçalho da coluna, sem selo de experimental, aulas canceladas com selo "Cancelada" mais motivo no cartão.
  - Código: `frontend/src/features/agenda/AgendaWeekGrid.tsx`, `LessonCard.tsx`
- **Rodada 2 · operacao-1 · OP1-03** (Agenda) — amplia M8
  - Categoria: filtros/abas/busca · Largura/tema: 1440 claro - Sistema/Canvas: mesmos prints do OP1-02
  - Prints: sistema `` · canvas ``
  - canvas = alternador segmentado "Esta semana | Próxima semana", "Hoje", setas em ícone, filtros em pílula "Professor Todos", "Modalidade Todas" na mesma linha, e legenda de cores de modalidade (Jiu-Jitsu, Muay Thai). Sistema = botões separados "‹ › Hoje Esta semana" (setas em caractere de texto), faixa de datas em texto, dois seletores nativos com rótulo acima ("Professor", "Modalidade") em linha própria, sem legenda de cores.
- **Rodada 2 · operacao-1 · OP1-05** (GMAgenda) — amplia M8
  - Categoria: layout · Largura/tema: 390 claro
  - Prints: sistema `imagens/operacao-1/GMAgenda-390-claro-sistema.png` · canvas `imagens/operacao-1/GMAgenda-390-claro-canvas.png`
  - canvas = cabeçalho "Agenda · Tijuca", título do dia "TERÇA, 13/10" centralizado com contagem "5 aulas · Tijuca", setas em botões, pílulas (Hoje, Esta semana, Professor: todos, Modalidade...), cartões com hora grande à esquerda ("7h / até 8h30"), nome com período, professor e nº de alunos, selo "1 experimental", cartão cinza "Segunda, 12/10: sem aula · feriado" e botão "Nova aula avulsa" ao pé. Sistema = botão "Nova aula avulsa" no topo, parágrafo introdutório, botões ‹ › Hoje Esta semana, rótulo "Quinta-feira, 29/10/2026", dois seletores nativos empilhados, faixa "Sem aula · Feriado", cartão simples sem hora em destaque nem nº de alunos; as setas movem 1 dia.

#### M9 · Turmas

- Rodada 1: Turmas: falta alerta de capacidade excedida, coluna de professor com auxiliares; busca vazia.
- Print (sistema à esquerda, canvas à direita): `imagens/rodada-1/27-turmas-claro.png`
- **Rodada 2 · operacao-2 · operacao-2-01** (Turmas) — amplia M9
  - Categoria: filtros/abas/busca · Largura/tema: 1440 claro
  - Prints: sistema `imagens/operacao-2/Turmas-1440-claro-sistema.png` · canvas `imagens/operacao-2/Turmas-1440-claro-canvas.png · Diferença: imagens/operacao-2/Turmas-1440-claro-diff.png`
  - busca é caixa vazia sem ícone nem placeholder "Nome da turma"; 5 filtros em seletor nativo com rótulo acima ("Todas as modalidades"), sem o padrão pílula "Modalidade Todas"; sistema tem filtro extra "Unidade" (canvas não tem; unidade vem do seletor da barra); canvas "Situação: Ativas e desativadas", sistema "Todas as situações".
- **Rodada 2 · operacao-2 · operacao-2-03** (Turmas) — repetido de M9
  - Categoria: estados · Largura/tema: 1440 claro (com fixture)
  - Prints: sistema `imagens/operacao-2/Turmas-1440-claro-cheia-sistema.png` · canvas `Turmas-1440-claro-canvas.png`
  - o aviso de capacidade existe (ClassesPage.tsx:61) mas é texto vermelho em segunda linha; canvas é selo âmbar "Acima da capacidade (31/30)" na mesma linha. Auxiliares existem ("Auxiliares: …"; canvas "aux. Thiago Moreira" menor). Parte do M9 já foi resolvida; resta o formato.

#### M10 · Planos

- Rodada 1: Planos: canvas junta cartões-resumo e tabela; sistema só resumo (tabela em /planos/lista).
- Print (sistema à esquerda, canvas à direita): `imagens/rodada-1/28-planos-claro.png`
- **Rodada 2 · financeiro-2 · F2-13** (Planos e benefícios) — amplia M10
  - Categoria: colunas/campos, layout · Largura/tema: 1440 claro
  - Prints: sistema `imagens/financeiro-2/Planos-1440-claro-sistema.png, PlanosRegras-1440-claro-sistema.png` · canvas `imagens/financeiro-2/Planos-canvas.png`
  - canvas tem dois cartões-resumo com ícone e número grande, com "Novo plano"/"Ver benefícios", e abaixo a tabela de planos com filtro "Modalidade Todas", ponto colorido da modalidade, Frequência, Alunos, "Outras unidades" (chip "Vale nas outras unidades"), Situação e "Editar" em texto. Sistema: cartões com listas (Ativos, Desativados, Alunos usando, Faixa de valores; Ativos, Altos) e botões escuros "Ver planos"/"Ver benefícios"; tabela em /planos/lista ("Tabela de planos"): Modalidade, Plano (com subtítulo "Unidade Unidade Norte", palavra repetida), Valor, Frequência semanal, Alunos usando, Situação, Ações (Editar/Desativar em botões), sem ponto de cor e sem "Outras unidades". Falta o rodapé "A frequência é só informativa...".

#### M11 · Plano novo/editar

- Rodada 1: Plano novo/editar: canvas "Editar plano" com aviso de alunos afetados e escolhas de reajuste; sistema formulário simples.
- Print (sistema à esquerda, canvas à direita): `imagens/rodada-1/79-plano-novo-claro.png`
- **Rodada 2 · financeiro-2 · F2-15** (Plano (Editar/Novo)) — repetido de M11
  - Categoria: modal/gaveta, campos · Largura/tema: 1440 claro
  - Prints: sistema `imagens/financeiro-2/PlanosRegras-1440-claro-editar-sistema.png` · canvas `imagens/financeiro-2/PlanosRegras-canvas.png`
  - canvas "Editar plano" com Modalidade/Nome/Frequência em linha, "Valor atual" desabilitado + "Novo valor", interruptor "Vale também nas outras unidades", aviso "58 alunos serão afetados...", escolhas para cobranças já geradas e já pagas (cartões), "Última alteração em ... por ..." e "Salvar plano". Sistema: coluna única, Unidade e Modalidade em texto, valor "300,00" sem prefixo R$, checkbox "Vale em outras unidades", botões "Cancelar"/"Salvar"; sem aviso de alunos afetados, sem escolhas de reajuste, sem última alteração.
- **Rodada 2 · operacao-1 · OP1-11** (AulaEditar) — amplia M11 (parte do "reajuste") / novo
  - Categoria: colunas/campos, modal/gaveta · Largura/tema: 1440 claro
  - Prints: sistema `imagens/operacao-1/AulaEditarFuturo-1440-claro-sistema.png` · canvas `imagens/operacao-1/AulaEditar-1440-claro-canvas.png`
  - canvas = cartão "AULA" com resumo (modalidade, data, horário, unidade); Data, Início e Término em uma linha; "Professor da aula" com ajuda "Pode ser de outra unidade..."; "Vale para" em cartões com descrição ("Só esta aula: fica marcada como alterada à mão e vira substituição"); "Observação para a equipe"; caixa "Avisar a turma". Sistema = título da aula em texto, data/hora nativas, seletor de professor, rádios simples "Só esta aula / Esta e as seguintes (as datas das seguintes mudam pelo mesmo número de dias)"; sem observação, sem "Avisar a turma", sem resumo.
- **Rodada 2 · operacao-2 · operacao-2-07** (TurmaEditar) — amplia M11 (padrão de janela)
  - Categoria: modal/gaveta · Largura/tema: 1440 claro
  - Prints: sistema `imagens/operacao-2/TurmaEditar-edicao-1440-claro-sistema.png e TurmaEditar-1440-claro-sistema.png` · canvas `imagens/operacao-2/TurmaEditar-canvas.png`
  - canvas "EDITAR TURMA" em 3 colunas (Nome, Modalidade, Faixa), horários em tabela Dia/Início/Término com lixeira e "Adicionar horário", capacidade/titular/auxiliares lado a lado, link do WhatsApp com ícone, aviso azul "Você mudou os horários. As aulas futuras serão refeitas…", caixa "Avisar a turma", rodapé "Última alteração em … por …" e "Cancelar/Salvar turma". Sistema: coluna única estreita; título em caixa mista; 7 caixas de dias com horário só nos marcados (rótulo "Início de Segunda-feira" quebra em 3 linhas); sem aviso azul; sem "Avisar a turma" na tela; sem "Última alteração"; auxiliares em caixas de marcação (canvas seletor); "Link do grupo" sem ícone; em "Nova turma" a janela passa da altura da tela e Salvar fica abaixo da dobra.
  - Código: frontend/src/features/classes/ClassFormDialog.tsx:235 (campos), :53 (`notify` existe no estado)

#### M12 · Benefícios

- Rodada 1: Benefícios: faltam abas por situação com contagem, avatar, filtro de tipo em pílula.
- Print (sistema à esquerda, canvas à direita): `imagens/rodada-1/29-beneficios-claro.png`
- **Rodada 2 · financeiro-1 · financeiro-1-12** (Benefícios (lista)) — amplia M12
  - Categoria: filtros/abas/busca, colunas/campos, botões/ações · Largura/tema: 1440 claro e escuro
  - Prints: sistema `imagens/financeiro-1/BeneficiosLista-1440-claro-sistema.png, imagens/financeiro-1/BeneficiosLista-1440-escuro-sistema.png` · canvas `imagens/financeiro-1/BeneficiosLista-1440-claro-canvas.png`
  - repete M12 (abas com contagem "Ativos 14 / Benefícios altos 2 / Encerrados 9", avatar, pílulas). Novos: subtítulo do canvas "Unidade Tijuca · no máximo um benefício ativo por modalidade · vale a partir da próxima cobrança gerada" no lugar do texto genérico; "Conceder benefício" sem ícone "+"; busca vazia sem ícone e sem placeholder "Aluno"; filtros com rótulo acima e caixa "Benefícios altos" no lugar da aba; sem selo "Alto" ao lado do valor (texto "Benefício alto" sob o valor); sem ponto colorido da modalidade; situação como texto com borda em vez de selo colorido; coluna "Ações" com botão "Encerrar" grande por linha (canvas não mostra botão por linha); sem nota de rodapé "Benefício acima de 50% ... Não precisa de aprovação."; paginação com "Itens por página".
  - Código: frontend/src/features/benefits/BenefitsPage.tsx, BenefitFilters.tsx

#### M13 · Cobranças

- Rodada 1: Cobranças: faltam Exportar CSV e abas com contagem no formato; filtros e datas nativos.
- Print (sistema à esquerda, canvas à direita): `imagens/rodada-1/30-cobrancas-claro.png`
- **Rodada 2 · financeiro-1 · financeiro-1-02** (Cobranças (lista)) — amplia M13
  - Categoria: botões/ações, filtros/abas/busca, colunas/campos · Largura/tema: 1440 claro e escuro
  - Prints: sistema `imagens/financeiro-1/Cobrancas-1440-claro-dados-sistema.png, imagens/financeiro-1/CobrancasEscuro-1440-escuro-dados-sistema.png` · canvas `imagens/financeiro-1/CobrancasEscuro-1440-escuro-canvas.png`
  - (a) sem breadcrumb "Financeiro › Cobranças" e sem seletor de unidade na barra; (b) subtítulo sem unidade nem a regra "sem juros, multa ou pagamento parcial..."; (c) cinco botões em fila, todos secundários e sem ícone, sem "Exportar CSV" (#151, Sprint 17) e sem botão primário "Nova cobrança" (vira "Nova cobrança avulsa"; "Gerar próximas mensalidades" não está nessa barra no canvas); (d) abas "Todas 128 / Em atraso 11 ..." viram botões "Em atraso (11)" sem "Todas"; (e) busca é caixa vazia sem ícone nem placeholder "Aluno, CPF ou referência"; (f) filtros: dois campos de data nativos (dd/mm/yyyy) no lugar do seletor "Período Outubro/2026", demais em seletores nativos com rótulo acima (canvas: pílulas "Situação Todas"); (g) colunas: sistema tem "Unidade" e não mostra tipo ("Mensalidade/Avulsa") sob a referência, dias de atraso na situação nem valor pago em "Meio" (só "Pix pela chave"); sem coluna de ações "…" por linha; (h) rodapé: sem totais "12 de 128 · Pago R$ … · Em aberto R$ …", só "128 cobranças somando R$ …"; paginação "Itens por página/Página anterior" no lugar de "1–12 de 128" com setas; (i) avatar neutro (círculo bege com iniciais), sem cor por aluno.
  - Código: frontend/src/features/charges/ChargesPage.tsx:33-40 (colunas), :58-61 (botões)
- **Rodada 2 · financeiro-1 · financeiro-1-03** (Cobranças (lista), celular) — amplia M13
  - Categoria: layout · Largura/tema: 390 claro
  - Prints: sistema `imagens/financeiro-1/GMCobrancas-390-claro-dados-sistema.png` · canvas `imagens/financeiro-1/GMCobrancas-360-claro-canvas.png`
  - canvas tem barra enxuta (marca/unidade, lupa, sino), título com "Outubro/2026 · 128 cobranças", uma linha horizontal de pílulas e cartões compactos (avatar, nome, referência, valor à direita, selo e "venceu 10/09 · 25 dias") e totais "11 cobranças em atraso · R$ 1.910,00". Sistema empilha botões, busca vazia, 4 botões de aba e 7 filtros nativos (a primeira tela é quase toda filtro) e cartões com lista Valor/Vencimento/Unidade; a lista só aparece depois de rolar.
- **Rodada 2 · financeiro-2 · F2-02** (Cobranças) — amplia M13 / M14
  - Categoria: botões/ações · Largura/tema: 1440 claro
  - Prints: sistema `imagens/financeiro-2/Mensalidades-1440-claro-fx-sistema.png` · canvas `imagens/financeiro-2/Mensalidades-canvas.png`
  - canvas tem 4 botões: Recebimentos do dia, Prévia da geração, Exportar CSV, Nova cobrança (primário escuro, com "+"), todos com ícone. Sistema tem 5 botões em contorno e sem ícone: Prévia, Recebimentos, "Em atraso", "Nova cobrança avulsa", "Gerar próximas mensalidades" (no canvas, "Gerar próximas" fica na Prévia). Falta Exportar CSV (não construído, #151). Subtítulo do canvas "Unidade Tijuca · sem juros, multa ou pagamento parcial · taxas ... repassadas ao aluno" x sistema "Cobranças das unidades ao seu alcance...". Ação primária não destacada.

#### M14 · Em atraso

- Rodada 1: Em atraso: canvas tem aviso dos lembretes automáticos, "Lembrete" por WhatsApp, Registrar/Renegociar por linha, "Mensagem pronta"; sistema só seleção em lote e e-mail.
- Print (sistema à esquerda, canvas à direita): `imagens/rodada-1/32-cobrancas-atrasados-claro.png`
- **Rodada 2 · financeiro-1 · financeiro-1-08** (Em atraso) — amplia M14
  - Categoria: botões/ações, colunas/campos, layout · Largura/tema: 1440 claro
  - Prints: sistema `imagens/financeiro-1/Atrasados-1440-claro-dados-sistema.png` · canvas `imagens/financeiro-1/Atrasados-1440-claro-canvas.png`
  - repete M14 (aviso dos lembretes automáticos com "Configurar", "Lembrete" por WhatsApp, Registrar/Renegociar por linha, "Mensagem pronta do lembrete" ausentes; seleção em lote). Novos: sem breadcrumb e título "Cobranças em atraso" (canvas "EM ATRASO" com subtítulo "N cobranças · R$ total"); canvas tem Aluno (avatar + "Modalidade · Unidade"), Atraso, Valor, Último lembrete, Bloqueio (selos "Bloqueada" / "Bloqueia em N dias"); sistema tem Seleção, Unidade, Referente a, Vencimento, Dias de atraso, Dias até o bloqueio; células quebram em duas linhas a 1440 ("R$ / 160,00", "E- / mail"); "0 dia" onde o canvas mostra "Bloqueada"; busca vazia sem placeholder; botão "WhatsApp" outline sem o verde do canvas e sem botão quando falta telefone (regra RNF-42, ok).
  - Código: frontend/src/features/charges/OverduePage.tsx:55-66; OverdueRows.tsx:29-36

#### M15 · Recebimentos do dia e Prévia

- Rodada 1: Recebimentos do dia e Prévia: faltam "Dia anterior", "Exportar CSV", 3 cartões de totais (sem dados para comparar).
- Print (sistema à esquerda, canvas à direita): `imagens/rodada-1/35-cobrancas-previa-claro.png`
- **Rodada 2 · financeiro-2 · F2-10** (Prévia da geração) — amplia M15
  - Categoria: colunas/campos, botões/ações, layout · Largura/tema: 1440 claro
  - Prints: sistema `imagens/financeiro-2/Fechamento-1440-claro-fx-sistema.png` · canvas `imagens/financeiro-2/Fechamento-canvas.png`
  - título do sistema "Prévia da geração de mensalidades" (canvas "Prévia da geração", com subtítulo "Novembro/2026 · Unidade Tijuca · a geração é automática..."). Canvas tem botão "Gerar próximas mensalidades" no topo (ausente), cartões "Total previsto (+ R$ ... em relação a outubro)", "Mensalidades (4 alunos não serão cobrados)" e "Já gerado" (sistema: 3º cartão "Diferença para o mês anterior", sem "Já gerado"), tabela "Por dia de vencimento" (dia, gera em, mensalidades, total, diferença, "Ver alunos"), tabela "Quem não será cobrado e por quê" (aluno, modalidade, motivo) e nota de rodapé. Sistema: lista por aluno com 3 botões em cada linha (Trocar vencimento, Cancelar mensalidade, Aplicar benefício), seletor "Período" (10/30/60 dias) que o canvas não mostra, e "Não serão cobrados" em linhas tracejadas. Motivos: canvas mostra 4 (bolsa 100%, bloqueio por atraso, encerramento solicitado, modalidade encerrada); o contrato só tem FULL_BENEFIT (docs/openapi.yaml:4339).
- **Rodada 2 · financeiro-2 · F2-12** (Recebimentos do dia) — amplia M15
  - Categoria: colunas/campos, botões/ações · Largura/tema: 1440 claro
  - Prints: sistema `imagens/financeiro-2/RecebimentosDia-1440-claro-fx-sistema.png` · canvas `imagens/financeiro-2/RecebimentosDia-canvas.png`
  - canvas = duas tabelas lado a lado "Por meio" (Meio, Pagamentos, Valor, Taxa repassada, linha Total) e "Por quem lançou" (inclui "Portal (aluno pagou)" e quem não lança, com 0), botões "Dia anterior" e "Exportar CSV", data por extenso. Sistema: campo de data nativo + "Voltar às cobranças", 2 cartões (Total recebido, Pagamentos), tabelas empilhadas sem "Taxa repassada" nem Total, "Pagamento online" no lugar de "Portal (aluno pagou)", e uma tabela "Pagamentos do dia" (hora, aluno, referente, meio, lançado por, valor, recibo) que o canvas não tem. Nomes de meio: sistema "Pix", "Maquininha de cartão"; canvas "Pix pelo portal", "Cartão pelo portal", "Cartão na maquininha".

#### M16 · Pedidos (gestão)

- Rodada 1: Pedidos (gestão): canvas abas + chips por tipo com contagem; sistema 3 seletores.
- Print (sistema à esquerda, canvas à direita): `imagens/rodada-1/36-pedidos-claro.png`
- **Rodada 2 · pessoas-2 · P2-07** (PedidosPortal) — amplia M16
  - Categoria: filtros/abas/busca, colunas/campos, botões/ações, texto · Largura/tema: 1440 claro, 1440 escuro, 390 claro
  - Prints: sistema `imagens/pessoas-2/PedidosPortal-1440-claro-sistema.png, PedidosPortal-390-claro-sistema.png` · canvas `imagens/pessoas-2/PedidosPortal-1440-claro-canvas.png, GMPedidos-360-claro-canvas.png`
  - (a) título "Pedidos do portal" e parágrafo longo; canvas "PEDIDOS" + "Tudo que chegou pelo portal e pelo link de matrícula, para você decidir"; (b) falta barra de abas "Pedidos do portal 4 | Aguardando aprovação 2" (a segunda é tela futura, ver Sem comparar); (c) filtros: 3 selects com rótulo acima (Situação/Tipo/Unidade); canvas tem pílulas por tipo com contagem (Todos 4, Troca de turma 1…) e só o seletor de unidade; (d) colunas: sistema Tipo, Aluno, Unidade, Pedido, Mensagem, Data (com hora), Situação, Ações; canvas Aluno (avatar com iniciais + unidade abaixo), Pedido, Detalhe ("Muay Thai: Iniciante → Avançado (Sex 19h)"), Recebido em, Prazo ("expira em 29 dias"/"faltam 6 dias"); falta avatar e falta Prazo; (e) botões: Aprovar preto e Recusar contorno de 44 px; canvas Aprovar contorno pequeno e Recusar texto; encerramento "Abrir ficha" (canvas "Ver ficha" no desktop e "Encerrar" no celular) e o "Recusar" desce para uma 2ª linha; (f) falta a nota de rodapé "Pedidos sem resposta expiram em 30 dias…"; (g) nome do aluno em link sublinhado; (h) a 390 o canvas tem cartão com avatar, nome, "Tipo · expira em N dias", resumo e botões Aprovar/Recusar de largura igual; sistema lista de linhas sem avatar, com Situação "Aberto" em cada cartão e barra com migalha truncada.
  - Código: `frontend/src/features/portal-requests/PortalRequestsPage.tsx:82-99` (colunas), `:40-55` (filtros)

#### M17 · Comunicados (lista)

- Rodada 1: Comunicados (lista): filtros de data nativos (canvas: pílulas), sem percentual de lidos.
- Print (sistema à esquerda, canvas à direita): `imagens/rodada-1/37-comunicados-claro.png`
- **Rodada 2 · comunicacao-e-estrutura-da-gestao-1 · CE1-04** (Comunicados (lista, cabeçalho e filtros)) — amplia M17
  - Categoria: filtros/abas/busca, texto, layout · Largura/tema: 1440 claro
  - Prints: sistema `imagens/comunicacao-e-estrutura-da-gestao-1/Comunicados-1440-claro-sistema.png` · canvas `imagens/comunicacao-e-estrutura-da-gestao-1/Comunicados-1440-claro-canvas.png`
  - subtítulo do canvas "Unidade Tijuca · o aluno lê no portal e por e-mail; não responde · limite de 20 por dia"; sistema "Avisos enviados aos alunos e responsáveis, os agendados e quantos já os abriram." Filtros: canvas três pílulas "Período Últimos 30 dias", "Turma Todas", "Tipo Todos"; sistema campo de busca vazio sem placeholder (largo, sem ícone), "De"/"Até" com data nativa em inglês (dd/mm/yyyy), dois selects nativos com rótulo acima. Botão "Novo comunicado" sem ícone "+" (canvas com +).
- **Rodada 2 · comunicacao-e-estrutura-da-gestao-1 · CE1-05** (Comunicados (tabela)) — amplia M17
  - Categoria: colunas/campos, botões/ações · Largura/tema: 1440 claro e escuro
  - Prints: sistema `imagens/comunicacao-e-estrutura-da-gestao-1/Comunicados-1440-claro-sistema.png` · canvas `imagens/comunicacao-e-estrutura-da-gestao-1/Comunicados-1440-claro-canvas.png`
  - canvas: Data como "08/10 · 10h00" (agendado: "08/10 · agendado 01/12"); Tipo e Situação como selos coloridos (Enviado verde, Agendado azul); título em negrito; "Fixado até 17/10" ao lado do título; Lidos com percentual ("19 (73%)"); sem coluna de ações (cancelar fica na janela). Sistema: texto puro em Tipo e Situação (sem selos); título normal; sem indicação de fixado; Lidos só o número, sem percentual; coluna "Ações" com botão largo "Cancelar agendamento" em cada agendado; rodapé de paginação "4 resultados / Itens por página / Página anterior / Próxima página" (canvas: só a nota "Enviado não é editado nem apagado. Agendado pode ser editado ou cancelado até sair. A gestão vê só quantos leram." e sem paginação). Cabeçalho da tabela em caixa alta no sistema, normal no canvas. Canvas permite editar agendado (nota); sistema só cancela.

#### M18 · Novo comunicado

- Rodada 1: Novo comunicado: canvas 2 colunas (destino em cartões, chips de tipo, editor com barra, prévia "Para quem vai", Salvar rascunho); sistema 1 coluna; campo de imagem nativo em inglês.
- Print (sistema à esquerda, canvas à direita): `imagens/rodada-1/38-comunicados-novo-claro.png`
- **Rodada 2 · comunicacao-e-estrutura-da-gestao-1 · CE1-06** (Novo comunicado (formulário)) — amplia M18
  - Categoria: layout, colunas/campos, botões/ações · Largura/tema: 1440 claro e escuro, 390
  - Prints: sistema `imagens/comunicacao-e-estrutura-da-gestao-1/ComunicadoNovo-1440-claro-sistema.png e ...-destino-sistema.png` · canvas `imagens/comunicacao-e-estrutura-da-gestao-1/ComunicadoNovo-1440-claro-canvas.png`
  - canvas em duas colunas (formulário em cartão + coluna "Prévia"); sistema em uma coluna, sem cartão, largura ~670 px. Destino: canvas 4 cartões de rádio (Turma, Modalidade, Unidade, Todas as unidades "Só o Coordenador") + select da turma; sistema um select único "Escolha o destino" misturando unidades, modalidades e turmas. Tipo: canvas chips ("Aula cancelada", "Troca de professor", ...) com "Cada tipo começa com um texto pronto"; sistema select nativo, e título/texto começam vazios (canvas já traz o texto pronto do tipo). Texto: canvas editor com barra B, lista, lista numerada, link e "212 de 2.000 caracteres"; sistema caixa simples com dica "Use **negrito**..." e contador "0/2000". Imagem: canvas área tracejada "Enviar imagem · até 5 MB"; sistema campo nativo "Choose File No file chosen" em inglês. Fixar: canvas "Fixar no topo do portal até (opcional)" com ícone de calendário e "dd/mm/aaaa"; sistema "Fixar até" com data nativa em inglês. Canais: canvas 4 caixas em linha (Portal "sempre", E-mail, Push "quando existir", WhatsApp "abre o grupo da turma para você enviar"); sistema Portal/E-mail empilhados, WhatsApp só depois de escolher turma, sem Push. Quando: canvas dois cartões de rádio; sistema rádios nativos azuis (cor do navegador, fora dos tokens). Faltam "Salvar rascunho", trilha "Comunicação › Comunicados › Novo comunicado" (sistema tem link sublinhado "Voltar aos comunicados") e o contador "3 de 20 comunicados hoje" no subtítulo. No celular (390) o formulário segue a mesma coluna única, ok; a barra de abas inferior cobre o conteúdo no print de página inteira (efeito da captura).
- **Rodada 2 · comunicacao-e-estrutura-da-gestao-1 · CE1-07** (Novo comunicado (prévia de destinatários)) — amplia M18
  - Categoria: layout, texto · Largura/tema: 1440 claro
  - Prints: sistema `imagens/comunicacao-e-estrutura-da-gestao-1/ComunicadoNovo-1440-claro-destino-sistema.png` · canvas `imagens/comunicacao-e-estrutura-da-gestao-1/ComunicadoNovo-1440-claro-canvas.png`
  - canvas mostra "PARA QUEM VAI" (alunos e responsáveis, portal x e-mail, quem não recebe por modalidade bloqueada) e um cartão "Como aparece no portal" com título e trecho; sistema mostra no pé do formulário só "Este aviso vai para 25 pessoas / Por portal (25), e-mail (23)", sem detalhe de adultos/responsáveis/bloqueados e sem prévia do texto. Botão "Enviar comunicado" sem ícone de envio e fora de um cartão de prévia.
  - Código: `i18n/pt-BR.json` `announcements.new.preview*`; `features/announcements/AnnouncementPreviewPanel.tsx`.
- **Rodada 2 · comunicacao-e-estrutura-da-gestao-2 · CE2-05** (Novo comunicado no celular) — amplia M18
  - Categoria: layout / colunas/campos / botões/ações / texto · Largura/tema: 390 claro
  - Prints: sistema `imagens/comunicacao-e-estrutura-da-gestao-2/GMNovoComunicado-390-claro-sistema.png` · canvas `imagens/comunicacao-e-estrutura-da-gestao-2/GMNovoComunicado-canvas.png`
  - canvas é tela cheia com seta de voltar e título "Novo comunicado" (+ unidade) na barra, campos Destino/Tipo/Título/Texto (contador "106 / 2.000" com espaço), Canais (Portal, E-mail, "Abrir o grupo no WhatsApp"), cartão "RESUMO" ("Vai para 33 pessoas, 31 por e-mail") e barra fixa embaixo com "Agendar" (ícone relógio) e "Enviar" (ícone avião), ambos ativos. Sistema: página comum com link sublinhado "Voltar aos comunicados" e título grande "NOVO COMUNICADO" + subtítulo, destino "Escolha o destino" vazio, contador "0/2000", dica de Markdown, campo de imagem nativo em inglês ("Choose File No file chosen"), "Fixar até" com `dd/mm/yyyy` nativo, "Quando enviar" com botões de opção (Enviar agora/Agendar) e botão único "Enviar comunicado" cinza desabilitado, sem cartão de resumo visível e sem a barra fixa. Sobra no sistema (não está no canvas): Imagem e Fixar até; falta o canal WhatsApp no print (cortado pela barra de abas fixa na captura inteira, a conferir).
  - Código: `frontend/src/features/announcements/NewAnnouncementPage.tsx`.

#### M19 · Organização (config)

- Rodada 1: Organização (config): faltam "Regras da organização", swatches de cor, prévia do botão; sobra Titular/Logo separados.
- Print (sistema à esquerda, canvas à direita): `imagens/rodada-1/39-configuracoes-organizacao-claro.png`
- **Rodada 2 · configuracoes-1 · CF1-01** (ConfigAcademia) — amplia M19
  - Categoria: colunas/campos, layout, botões/ações · Largura/tema: 1440 claro
  - Prints: sistema `imagens/configuracoes-1/ConfigAcademia-1440-claro-sistema.png` · canvas `imagens/configuracoes-1/ConfigAcademia-1440-claro-canvas.png`
  - sistema tem uma coluna com cartões "Dados da organização" (nome, cor em campo vazio com quadrado preto, titular), "Logo" e "Transferir coordenação". Canvas tem 2 colunas: "Marca" (nome, titular, logo com miniatura e "Trocar logo", 5 amostras de cor, prévia "Pagar R$ 180,90" com selo de contraste), "Regras da organização" (prazo para bloquear, acesso de ex-aluno, guardar experimentais, experimentais por telefone, "Treinar em outra unidade" em 3 cartões de opção, interruptor de comprovante) e cartão "Coordenação" (avatar, nome, e-mail, "duas etapas ativa", "Transferir coordenação" em vermelho). Rodapé: canvas "Descartar" + "Salvar alterações" à direita; sistema "Salvar" dentro do cartão. "Última alteração em ... por ..." fica à direita do título no canvas; sistema mostra "Ainda sem alterações" abaixo. Subtítulo "Marca e regras que valem para todas as unidades · só o Coordenador altera" ausente. Logo sem miniatura, "Enviar logo" em vez de "Trocar logo" com ícone.

#### M20 · Unidade (config)

- Rodada 1: Unidade (config): faltam abas (Dados, Pagamentos, Lembretes, Links, Permissões), fuso, dias de vencimento, taxa de matrícula, modalidades oferecidas, diretório.
- Print (sistema à esquerda, canvas à direita): `imagens/rodada-1/41-configuracoes-unidade-claro.png`
- **Rodada 2 · configuracoes-1 · CF1-03** (ConfigUnidade) — amplia M20
  - Categoria: layout, filtros/abas/busca, colunas/campos · Largura/tema: 1440 claro e escuro
  - Prints: sistema `imagens/configuracoes-1/ConfigUnidade-1440-claro-sistema.png (escuro: ...-escuro-sistema.png)` · canvas `imagens/configuracoes-1/ConfigUnidade-1440-claro-canvas.png`
  - sistema é uma página única de ~3400 px com 7 cartões empilhados em coluna estreita (Dados, Cobrança, Pagamentos, Lembretes, Permissões do Professor, Divulgação, Modalidades oferecidas), título "Configurações da unidade". Canvas é "Unidade Tijuca" com subtítulo, "Última alteração", abas Dados/Pagamentos/Lembretes/Links/Permissões do Professor, dados em 2 colunas, dias de vencimento como pílulas (sistema: 28 caixas de marcação), taxa de matrícula com interruptor e valor, fuso horário, "Modalidades oferecidas" com interruptores e "5 turmas · 4 planos", cartão "Diretório de academias" com interruptor, rodapé Descartar/Salvar alterações. Sistema tem campos que o canvas não mostra (Nome curto, CEP, Cidade, latitude/longitude, Telefone) e não tem fuso. O estado vazio de "Modalidades oferecidas" é dado do MSW.
- **Rodada 2 · configuracoes-2 · configuracoes-2-08** (LembretesAuto (Unidade › Lembretes)) — amplia M20
  - Categoria: colunas/campos / layout · Largura/tema: 1440 claro
  - Prints: sistema `imagens/configuracoes-2/LembretesAuto-1440-claro-sistema.png` · canvas `imagens/configuracoes-2/LembretesAuto-1440-claro-canvas.png`
  - canvas = aba "Lembretes" com 5 interruptores (3 dias antes, no dia, 3 e 10 dias depois, 5 dias antes do bloqueio "Sempre ligado"), canais E-mail/Push por linha, seletor de horário, campo "Menores de idade: Vão para o responsável financeiro", aviso azul com "Editar textos", Descartar/Salvar alterações. Sistema: um único marcador "Enviar lembretes automáticos", "Hora dos lembretes" numérica, "Dias de lembrete" em texto separado por vírgula (-3, 0, 3, 10), texto "Canal: e-mail", sem Push, sem menores, sem atalho para textos. Cabeçalho "Unidade Tijuca" com última alteração/autor ausentes; a página é um só rolo (Dados, Cobrança, Pagamentos, Lembretes, Permissões, Divulgação) sem abas.
- **Rodada 2 · configuracoes-2 · configuracoes-2-09** (PagamentosDesconectado) — amplia M20
  - Categoria: estados · Largura/tema: 1440 claro
  - Prints: sistema `imagens/configuracoes-2/LembretesAuto-1440-claro-sistema.png (seção Pagamentos)` · canvas `imagens/configuracoes-2/PagamentosDesconectado-1440-claro-canvas.png`
  - canvas tem faixa vermelha global "O Mercado Pago da unidade ... foi desconectado" com "Reconectar", cartão "Mercado Pago · Desconectado" com data de expiração e botão, e cartão "Enquanto estiver desconectado" com 4 consequências. Sistema só tem o aviso estático "Mercado Pago não conectado / A conexão ... chega em uma próxima entrega" (`pt-BR.json:1731-1733`), sem Reconectar nem faixa global; as taxas por meio de pagamento aparecem na mesma página (canvas: aba Pagamentos limpa).

#### M21 · Unidades (config)

- Rodada 1: Unidades (config): canvas "Abrir"/"Ver histórico"; sistema "Configurar"/"Desativar".
- Print (sistema à esquerda, canvas à direita): `imagens/rodada-1/40-configuracoes-unidades-claro.png`
- **Rodada 2 · configuracoes-1 · CF1-10** (ConfigUnidades) — amplia M21
  - Categoria: colunas/campos, botões/ações, layout · Largura/tema: 1440 claro
  - Prints: sistema `imagens/configuracoes-1/ConfigUnidades-1440-claro-sistema.png` · canvas `imagens/configuracoes-1/ConfigUnidades-1440-claro-canvas.png`
  - "Nova unidade" fica abaixo do título (canvas: canto superior direito, com ícone +). Falta o subtítulo "2 ativas · até 10 unidades e 200 usuários por organização · unidades não mudam o preço". Cabeçalho da tabela em caixa alta e sem cartão (canvas: texto normal dentro de cartão). Ações: "Configurar"/"Desativar" em botões; canvas "Abrir" (e "Ver histórico" na desativada, com "Desativada em 30/06/2026" no selo). Nome curto: "Ajustar nome curto" é botão na linha; canvas tem nota explicativa abaixo da tabela e botão vermelho "Desativar a Barra...". "Cidade" sem UF.

#### M22 · Modalidades (config)

- Rodada 1: Modalidades (config): canvas cartões em grade e link "Não achei minha modalidade"; sistema lista.
- Print (sistema à esquerda, canvas à direita): `imagens/rodada-1/42-configuracoes-modalidades-claro.png`
- **Rodada 2 · configuracoes-1 · CF1-11** (ConfigModalidades) — amplia M22
  - Categoria: layout, botões/ações · Largura/tema: 1440 claro e escuro
  - Prints: sistema `imagens/configuracoes-1/ConfigModalidades-1440-claro-sistema.png` · canvas `imagens/configuracoes-1/ConfigModalidades-1440-claro-canvas.png`
  - sistema lista as 13 modalidades em linhas com "Ativar" à direita, "Não achei minha modalidade" como botão no fim e bloco "Pedidos enviados" (o canvas não tem). Canvas: grade de 4 colunas com "Catálogo ctcore" e "+ Ativar", títulos de seção em caixa alta, cartões "Suas modalidades" com "Graduação ligada · 13 níveis · Tijuca e Barra" e botão "Graduação", link "Não achei minha modalidade" com ícone no canto superior, nota "Variações como No-Gi, Kids ou Competição...". Não comparei o estado com modalidades ativas (MSW traz nenhuma, sem fixture).

#### M23 · Modalidade, graduação (modal)

- Rodada 1: Modalidade, graduação (modal): canvas tabela de níveis com cor, grau máximo, idades e arrastar; sistema só caixas e "Adicionar nível".
- Print (sistema à esquerda, canvas à direita): `imagens/rodada-1/69-configuracoes-modalidades-graduacao-claro.png`
- **Rodada 2 · configuracoes-2 · configuracoes-2-12** (ModalidadeGraduacao) — repetido de M23
  - Categoria: modal/gaveta · Largura/tema: 1440 claro
  - Prints: sistema `imagens/configuracoes-2/ModalidadeGraduacao-1440-claro-sistema.png` · canvas `imagens/configuracoes-2/ModalidadeGraduacao-1440-claro-canvas.png`
  - sem tabela de níveis (cor/faixa visual, grau máximo, idades, arrastar, lixeira), sem interruptor "Graduação ligada" com descrição (é caixa simples), sem "Voltar ao padrão do catálogo", sem texto do catálogo IBJJF; "Exigir comprovante" é caixa em vez de interruptor com descrição. O título da janela é "Sistema de graduação" em fonte normal (canvas: "GRADUAÇÃO · JIU-JITSU" em caixa alta).

#### M24 · Usuários

- Rodada 1: Usuários: faltam 3 cartões de perfil, "Tabela de permissões", filtros de perfil/unidade/situação.
- Print (sistema à esquerda, canvas à direita): `imagens/rodada-1/44-configuracoes-usuarios-claro.png`
- **Rodada 2 · configuracoes-2 · configuracoes-2-01** (PermissoesTabela (Usuários › Tabela de permissões)) — amplia M24 · gravidade revista de alta para média: Tela informativa; a permissão já é aplicada no servidor (#27). Sem issue aberta para a tela.
  - Categoria: botões/ações · Largura/tema: 1440 claro
  - Prints: sistema `(não existe)` · canvas `imagens/configuracoes-2/PermissoesTabela-1440-claro-canvas.png`
  - o canvas tem o botão "Tabela de permissões" em Usuários e a página com legenda (pode, só consulta, U, T, *) e a matriz Coordenador x Administrador x Professor com 27 linhas. O sistema não tem botão nem rota; a tela fica inalcançável.
  - Código: `frontend/src/App.tsx:161` (só `/configuracoes/usuarios`; nenhuma rota de permissões); `UsersPage.tsx` sem link. A permissão no servidor existe (#27, fechada); falta só a tela.
- **Rodada 2 · configuracoes-2 · configuracoes-2-02** (ConfigUsuarios) — amplia M24
  - Categoria: layout / colunas / filtros · Largura/tema: 1440 claro
  - Prints: sistema `imagens/configuracoes-2/ConfigUsuarios-1440-claro-sistema.png` · canvas `imagens/configuracoes-2/ConfigUsuarios-1440-claro-canvas.png`
  - sistema sem subtítulo ("Equipe da gestão · 3 perfis fixos..."), sem os 3 cartões de perfil, sem busca "Nome ou e-mail", sem filtros Perfil/Unidade/Situação (só pílulas Todos/Ativos/Convite enviado, que o canvas não tem). Tabela do sistema: coluna "Ações" com 2 ou 3 botões com borda em cada linha (canvas: ações em texto simples, só as cabíveis), avatar com inicial em cor única (canvas: iniciais coloridas por pessoa) e perfil em texto puro (canvas: pílula colorida por perfil), cabeçalho em CAIXA ALTA e tabela sem cartão com borda arredondada. Título "USUÁRIOS DA GESTÃO" (canvas: "Usuários"); botão sem ícone "+" e sem "Tabela de permissões". Sistema mostra Convidar acima das abas, à esquerda; canvas, à direita no cabeçalho.

#### M25 · Termos

- Rodada 1: Termos: canvas 2 colunas (editor com barra, "Ao publicar a versão N", tabela de versões com aceites); sistema formulário empilhado.
- Print (sistema à esquerda, canvas à direita): `imagens/rodada-1/45-configuracoes-termos-claro.png`
- **Rodada 2 · configuracoes-1 · CF1-14** (ConfigTermos) — amplia M25
  - Categoria: colunas/campos, botões/ações, filtros/abas/busca · Largura/tema: 1440 claro e escuro
  - Prints: sistema `imagens/configuracoes-1/ConfigTermos-1440-claro-sistema.png` · canvas `imagens/configuracoes-1/ConfigTermos-1440-claro-canvas.png`
  - abas do sistema são botões em pílula ("Termo de matrícula" / "Termo de uso de imagem"); canvas usa abas com sublinhado ("Termo de matrícula" / "Uso de imagem"). Editor é caixa de texto simples (canvas: barra B · I · Lista · Link). Falta "Salvar rascunho"; "Publicar nova versão" desabilitado abaixo do editor (canvas: "Publicar versão 4" ao lado). Quem aceita: botões de opção sem descrição abaixo do editor (canvas: cartão "Ao publicar a versão 4" com título e descrição). Histórico: sistema mostra estado vazio (dado do MSW); canvas tabela Versão / Publicada / Aceites. Falta a nota sobre o uso de imagem.

#### M26 · Textos

- Rodada 1: Textos: canvas lista + editor lado a lado com chips de campos e prévia; sistema lista com "Editar".
- Print (sistema à esquerda, canvas à direita): `imagens/rodada-1/46-configuracoes-textos-claro.png`
- **Rodada 2 · configuracoes-1 · CF1-15** (ConfigTextos) — amplia M26
  - Categoria: layout, colunas/campos · Largura/tema: 1440 claro
  - Prints: sistema `imagens/configuracoes-1/ConfigTextos-1440-claro-sistema.png` · canvas `imagens/configuracoes-1/ConfigTextos-1440-claro-canvas.png`
  - sistema lista 9 modelos agrupados em 3 cartões (Lembretes de cobrança, Boas-vindas, Comunicados) com "Editar"; canvas tem lista única à esquerda com selos "Editado"/"Texto da plataforma" e editor ao lado (Assunto, Texto, chips de campos {nome} {valor}..., "Voltar ao texto da plataforma", Prévia preenchida, "Última alteração", "Salvar texto"). Rótulos: "Lembrete antes do vencimento" contra "Lembrete: antes do vencimento"; "Aula cancelada" contra "Comunicado: Aula cancelada". Subtítulo "Nove modelos que a organização edita ... ficam fora: ..." ausente.

#### M27 · Minha conta

- Rodada 1: Minha conta: canvas 2 colunas com sessões ativas e preferências de e-mail; sistema 3 formulários empilhados.
- Print (sistema à esquerda, canvas à direita): `imagens/rodada-1/47-conta-claro.png`
- **Rodada 2 · acesso · acesso-14** (MinhaConta) — amplia M27
  - Categoria: layout / colunas-campos / botões · Largura/tema: 1440 claro e escuro
  - Prints: sistema `imagens/acesso/MinhaConta-1440-claro-sistema.png` · canvas `imagens/acesso/MinhaConta-1440-claro-canvas.png`
  - canvas: subtítulo "Carla Mendes · vale para a gestão e para o portal"; cartão "Acesso" com e-mail e "senha alterada em…" mais botões "Trocar e-mail" e "Trocar senha" que abrem a troca; cartão "Verificação em duas etapas" com selo "Ativa", "ativada em…", "Ver códigos de reserva novos" e "Desligar", nota "Opcional para Administradores. Obrigatória para o Coordenador."; à direita "Sessões ativas" em tabela (Aparelho com ícone, Local, Último uso, Encerrar), selo "Este aparelho", "Encerrar todas as outras" e nota de duração das sessões; cartão "E-mails" com chaves. Sistema: coluna única com os formulários de senha e e-mail sempre abertos (campos vazios, lista de regras "pendente"), "Duas etapas" como faixa azul "desligadas", "Sessões ativas" em lista de linhas (sem tabela/ícone), sem subtítulo, sem cartão de preferências de e-mail, sem nota de duração das sessões. Cabeçalho do sistema: sino, avatar "Ana · Coordenação", busca, tema, ajuda; canvas tem ainda seletor de unidade, nome da página à esquerda e menu do avatar com seta. Menu lateral: canvas tem nome da organização em caixa alta com logo, Pessoas/Operação/Financeiro/Comunicação/Relatórios/Configurações; sistema "Academia Alfa" sem logo e sem Comunicação/Relatórios (itens `available=false`).
- **Rodada 2 · acesso · acesso-18** (GMConta) — amplia M27
  - Categoria: layout / colunas-campos · Largura/tema: 360 claro e escuro
  - Prints: sistema `imagens/acesso/GMConta-360-claro-sistema.png` · canvas `imagens/acesso/GMConta-360-claro-canvas.png`
  - canvas é tela de lista: cabeçalho "Minha conta" com seta de voltar e seletor de unidade, cartão com avatar "CM", nome, e-mail e perfil, lista de linhas com ícone, valor e seta (Senha "alterada há 4 meses", E-mail, Duas etapas "Ligada", Sessões ativas "2", Notificações) e botão "Sair". Sistema: título "MINHA CONTA" abaixo de barra com voltar, sino, avatar, busca e tema; formulários completos empilhados (senha, e-mail, duas etapas, sessões); sem cartão do perfil, sem lista de atalhos, sem "Sair" na tela, sem Notificações. Barra inferior do sistema: Cobranças em cinza (item desativado), canvas com 5 itens ativos.
- **Rodada 2 · portal-a-2 · portal-a-2-17** (Minha conta (portal)) — amplia M27
  - Categoria: modal/gaveta, layout · Largura/tema: 1440 claro
  - Prints: sistema `imagens/portal-a-2/PortalTrocarEmail-1440-claro-sistema.png` · canvas `imagens/portal-a-2/PortalTrocarEmail-canvas.png`
  - canvas: página "MINHA CONTA" (rótulo "MINHAS INFORMAÇÕES", aba ativa) com cartão "Acesso" (e-mail + "Trocar e-mail"/"Trocar senha") e a janela "TROCAR E-MAIL" (novo e-mail, senha atual com dica da trava de 5 tentativas, código do app quando há duas etapas, caixa "RESUMO" com 4 avisos, "Cancelar"/"Enviar link de confirmação"). Sistema: `/conta` mostra formulários empilhados (Senha, E-mail, Duas etapas, Sessões) sem janela, sem resumo, sem dica de tentativas, e nenhuma aba fica ativa na barra. Não há botão "Trocar e-mail" para abrir janela.
  - Código: `frontend/src/features/account/AccountFrame.tsx`; rota em `frontend/src/App.tsx`
- **Rodada 2 · portal-b-1 · PB1-10** (Minha conta) — amplia M27
  - Categoria: layout, colunas/campos · Largura/tema: 1440 claro, 390 claro
  - Prints: sistema `PortalConta-1440-claro-sistema.png, PortalMConta-390-claro-sistema.png` · canvas `PortalConta-1440-claro-canvas.png, PortalMConta-390-claro-canvas.png`
  - canvas = "Acesso" (e-mail, "Senha alterada em ...", botões "Trocar e-mail"/"Trocar senha"), "Verificação em duas etapas" (tag Desligada, "Opcional para alunos", "Ativar"), "Sessões ativas" em tabela com ícone de aparelho, "Este aparelho", "Último uso" (agora/ontem), "Encerrar" e "Encerrar todas as outras" em vermelho, nota "No portal, a sessão dura 30 dias.", cartões "Baixar meus dados" e "Excluir minha conta"; no celular "‹ Voltar" e rótulo "MINHAS INFORMAÇÕES". Sistema = formulários empilhados já abertos (Senha com requisitos, E-mail, Duas etapas com faixa azul, Sessões em lista), sem "Seus dados", sem rótulo, sem "Voltar", sem ícone de aparelho, sem nota dos 30 dias; "Último uso: há 11 dias".
  - Código: frontend/src/features/account/AccountPage.tsx

#### M28 · Notificações

- Rodada 1: Notificações: canvas menu flutuante na barra; sistema janela central escurecendo a tela.
- Print (sistema à esquerda, canvas à direita): `imagens/rodada-1/83-notificacoes-claro.png`
- **Rodada 2 · comunicacao-e-estrutura-da-gestao-2 · CE2-03** (Notificações (janela)) — amplia M28
  - Categoria: modal/gaveta / layout / ícone/avatar/cor / texto · Largura/tema: 1440 claro e escuro; 390 claro
  - Prints: sistema `imagens/comunicacao-e-estrutura-da-gestao-2/Notificacoes-1440-claro-sistema.png, Notificacoes-1440-escuro-sistema.png, GMNotificacoes-390-claro-sistema.png` · canvas `imagens/comunicacao-e-estrutura-da-gestao-2/Notificacoes-canvas.png, GMNotificacoes-canvas.png`
  - (1) Desktop: canvas é um menu flutuante de ~440 px ancorado ao sino, sem escurecer a página; sistema é gaveta lateral de altura total (~680 px) que escurece o fundo, com "X". (2) Canvas: título "NOTIFICAÇÕES" + "Marcar todas como lidas" no topo; abas "Todas" / "Não lidas 2"; seção "HOJE"/"ESTA SEMANA" em caixa-alta pequena; cada item com ícone colorido por tipo (pagamento verde, comprovante azul, pedido amarelo, recusado vermelho...), título em negrito, linha de detalhe, tempo e ponto vermelho de não lida; item inteiro clicável; rodapé "As notificações somem depois de 30 dias". Sistema: cartões com borda, sem ícone de tipo, prefixo textual "Não lida:", uma frase só (sem título), botão "Ver cobranças/Ver benefícios/Ver aluno/Ver usuários" em cada cartão, contadores "Todas (4)" / "Não lidas (3)", sem rodapé de 30 dias. (3) Sistema tem a chave "Receber tudo" (avisos de todas as unidades) que o canvas não mostra. (4) Celular: canvas é tela cheia com seta de voltar, título + unidade, abas e "Marcar lidas"; sistema é janela ocupando quase toda a altura com "X" e a página aparecendo embaixo. Tema escuro do sistema legível, sem problema de contraste.
  - Código: `frontend/src/features/notifications/NotificationPanel.tsx`, `frontend/src/features/notifications/BellButton.tsx`.

#### M29 · Portal Agenda

- Rodada 1: Portal Agenda: canvas 7 colunas legíveis; sistema colunas estreitas com texto quebrando.
- Print (sistema à esquerda, canvas à direita): `imagens/rodada-1/16-portal-agenda-claro.png`
- **Rodada 2 · portal-a-1 · PA-02** (Agenda (1440)) — amplia M29
  - Categoria: layout / colunas · Largura/tema: 1440 claro
  - Prints: sistema `imagens/portal-a-1/PortalAgenda-1440-claro-sistema.png` · canvas `imagens/portal-a-1/PortalAgenda-1440-claro-canvas.png`
  - sistema = grade semanal por horário (linhas 10:00, 19:00) com colunas de ~90 px; os cartões quebram texto em palavras soltas ("Era em 26/10/202 6 às 18:00" parte a data), tag "Já aconteceu" estoura o cartão, a coluna do dia "Sem aula" é uma faixa vazia alta. Canvas = 7 colunas iguais ocupando a largura, cartão por aula com cor lateral por tipo (principal azul, outro horário/avulsa neutro, cancelada riscada), "Sem aula" como faixa listrada com motivo, "—" nos dias vazios, aviso em pílula amarela "Alterada" + link "Ver aviso".
- **Rodada 2 · portal-a-1 · PA-04** (Agenda celular) — amplia M29
  - Categoria: layout, botões/ações · Largura/tema: 390 claro
  - Prints: sistema `imagens/portal-a-1/PortalMAgenda-390-claro-sistema.png` · canvas `imagens/portal-a-1/PortalMAgenda-390-claro-canvas.png`
  - sistema empilha 4 blocos (alternador, 3 botões de semana, legenda de 6 chips, 7 pílulas de dia) antes de mostrar a aula do dia; cartão do dia sem horário em destaque nem barra lateral colorida. Canvas é lista do dia: navegação "<  SEXTA, 16/10  >", alternador segmentado, e cartões com horário em negrito ("7h–8h30"), pílula de situação (Alterada, Turma principal, Cancelada) e título em negrito. Sistema mostra só os dias com pílulas "Seg 26/10 ..." e o primeiro cartão aparece abaixo da dobra.

#### M30 · Portal Financeiro

- Rodada 1: Portal Financeiro: canvas tabela de cobranças com seleção, "Pagar R$", "Enviar comprovante", cartões de débito automático e recibos; sistema sem dados para comparar (pagamento online é Sprint 11).
- Print (sistema à esquerda, canvas à direita): `imagens/rodada-1/18-portal-financeiro-claro.png`
- **Rodada 2 · portal-a-1 · PA-10** (Financeiro › Toda a família) — repetido de M30
  - Categoria: colunas/campos, botões/ações · Largura/tema: 1440 claro e 390 claro
  - Prints: sistema `imagens/portal-a-1/PortalFamilia-1440-claro-sistema.png` · canvas `imagens/portal-a-1/PortalFamilia-1440-claro-canvas.png (celular: PortalMFamilia-390-claro-sistema.png / -canvas.png)`
  - canvas = 3 cartões de filho (avatar, idade, modalidade, situação) e "Toda a família" selecionado, cartão "Em aberto na família", uma tabela por unidade ("TIJUCA / pagamento próprio desta unidade", "BARRA / pagamento separado: outra unidade") com colunas Aluno/Cobrança/Vencimento/Valor/Situação, caixa de seleção, total "Pagar R$ 75,00" com prévia Pix/Cartão/Boleto e nota sobre unidades diferentes. Sistema: select "Aluno: Toda a família" + "Total da família em aberto", lista vertical com cada cobrança, caixa de seleção sem rótulo, um botão "Pagar" por cobrança, "Composição", e cartão "Débito automático ... Inativo" (que o canvas desta tela não mostra). Sem agrupamento por unidade, sem avatar, sem "Responsável" (rótulo "Só o que você paga como responsável financeira") e sem o botão de pagamento somado. Pagamento online é da Sprint 11 (ver M30), mas a tabela e o agrupamento por unidade já poderiam existir.
- **Rodada 2 · portal-b-1 · PB1-01** (Financeiro) — amplia M30 · gravidade revista de alta para média: Seleção, total por meio e "Pagar" são do pagamento online (Sprint 11).
  - Categoria: botões/ações · Largura/tema: 1440 claro, 390 claro
  - Prints: sistema `imagens/portal-b-1/PortalFinanceiro-1440-claro-sistema.png, PortalMFinanceiro-390-claro-sistema.png` · canvas `imagens/portal-b-1/PortalFinanceiro-1440-claro-canvas.png, PortalMFinanceiro-390-claro-canvas.png`
  - canvas tem "Em aberto" como tabela com seleção, resumo "N selecionadas · R$", total por meio (Pix/Cartão/Boleto), "Enviar comprovante de Pix" e "Pagar R$". Sistema não tem seleção nem "Pagar" por meio (parte é Sprint 11: #98, #100, #101). "Enviar comprovante" existe no código (PixProofDialog), mas não foi comparado porque o MSW não traz cobrança em aberto para aluno@/familia@.
  - Código: frontend/src/features/portal/PortalFinancePage.tsx:55; frontend/src/features/portal/PortalOpenCharges.tsx:7
- **Rodada 2 · portal-b-2 · portal-b-2-04** (Pix na chave com comprovante (desktop e celular)) — amplia M30
  - Categoria: modal/gaveta / layout / texto · Largura/tema: 1440 claro, 390 claro
  - Prints: sistema `imagens/portal-b-2/PortalPixChave-1440-claro-sistema.png, PortalMPixChave-390-claro-sistema.png` · canvas `imagens/portal-b-2/PortalPixChave-1440-claro-canvas.png, PortalMPixChave-390-claro-canvas.png`
  - canvas "PAGAR NA CHAVE DA UNIDADE" com aviso âmbar (pagamento online fora do ar), chave com tipo (CNPJ) e titular, "Copiar chave" com ícone, valor "(sem taxa)", zona tracejada de envio ("Envie a foto ou o PDF do comprovante / JPG, PNG ou PDF · até 5 MB") e nota sobre "Em conferência". No celular é página cheia com "Voltar", valor grande, descrição das cobranças, cartão da chave com copiar em ícone, "RESUMO" e botão largo. Sistema: janela central "Pix na chave com comprovante" com lista Unidade/Valor/Chave Pix, "Copiar" sem ícone, lista de cobranças só quando várias, campo de arquivo nativo em inglês ("Choose File No file chosen"), sem aviso, zona de envio nem resumo; no celular a mesma janela central.
  - Código: frontend/src/features/portal/PixProofDialog.tsx

#### M31 · Portal Minhas informações

- Rodada 1: Portal Minhas informações: faltam blocos foto+carteirinha, termos, preferências de e-mail, "Conta e privacidade".
- Print (sistema à esquerda, canvas à direita): `imagens/rodada-1/19-portal-informacoes-claro.png`
- **Rodada 2 · portal-b-1 · PB1-08** (Minhas informações) — amplia M31
  - Categoria: colunas/campos, layout · Largura/tema: 1440 claro, 390 claro
  - Prints: sistema `PortalInfo-1440-claro-sistema.png, PortalMInfo-390-claro-sistema.png` · canvas `PortalInfo-1440-claro-canvas.png, PortalMInfo-390-claro-canvas.png`
  - canvas = título com o nome do aluno, 2 colunas (Dados pessoais em grade; à direita "Foto/Trocar foto/Carteirinha" e "Termos" com Ver/Revogar), "Treinos" em tabela (Modalidade, Turma principal, Outros horários, Plano e benefício, Mensalidade, Situação) com "Fazer um pedido", e dois cartões "Preferências de e-mail" (interruptor) e "Conta e privacidade" (Minha conta, Baixar meus dados, Excluir minha conta). Sistema = coluna única com 9 cartões: Dados pessoais em lista rótulo/valor com "Escolher foto" e "Editar contato", Treinos como texto, "Plano e mensalidade", "Graduação" com histórico, "Preferências" com caixa de seleção, "Termos aceitos" (com "Conceder uso de imagem"), "Pedidos à academia", e a "Minha conta" inteira embutida (Senha, E-mail, Duas etapas, Sessões, Seus dados); "Ver carteirinha" é link sublinhado solto. No celular, canvas = título "EU", cartão de perfil com botão Carteirinha e lista de atalhos com seta (Termos e uso de imagem, Preferências, Minha conta, Baixar, Excluir, Instalar o app); sistema = a mesma página de ~4000 px.
  - Código: PortalInfoPage.tsx:17-27
- **Rodada 2 · portal-b-1 · PB1-11** (Editar contato) — amplia M31
  - Categoria: modal/gaveta, colunas/campos · Largura/tema: 390 claro
  - Prints: sistema `PortalMEditar-390-claro-sistema.png` · canvas `PortalMEditar-390-claro-canvas.png`
  - canvas = tela cheia ("‹ Cancelar", rótulo + título "EDITAR CONTATO") com Celular*, CEP, Endereço, Complemento, Contato de emergência, Telefone de emergência, nota "Nome, CPF e nascimento só a academia altera..." e botões Cancelar/Salvar lado a lado; sistema = modal com rolagem e campos mais fragmentados (Logradouro, Número, Complemento, Bairro, Cidade, UF, Nome, Parentesco, Telefone do contato); rótulos em negrito escuro (canvas: cinza pequeno).
  - Código: PortalContactDialog.tsx:76
- **Rodada 2 · portal-b-2 · portal-b-2-05** (Portal reduzido › Minhas informações) — amplia M31
  - Categoria: layout / colunas/campos · Largura/tema: 1440 claro
  - Prints: sistema `imagens/portal-b-2/PortalReduzidoInfo-1440-claro-sistema.png` · canvas `imagens/portal-b-2/PortalReduzidoInfo-1440-claro-canvas.png`
  - canvas: rótulo + nome em título, cartões compactos em grade (Dados pessoais com "Editar contato" e ícone; "Contato da unidade" com botão verde "WhatsApp da unidade"; "Preferências de e-mail" com chave liga/desliga; "Termos" em uma linha; "Conta e dados" com "Minha conta", "Baixar meus dados", "Excluir minha conta"), faixa "Seu acesso está reduzido: nenhuma modalidade está ativa no momento." e frase final sobre o que volta a aparecer. Sistema: página de 2600 px em coluna única com "Ver carteirinha", Dados pessoais em tabela com foto e "Escolher foto", "Contato da unidade" sem botão de WhatsApp, "Preferências" com caixa de marcar, "Termos aceitos" com "Conceder uso de imagem", "Minha conta" inteira embutida (senha, e-mail, duas etapas, sessões) e "Seus dados" com botões desabilitados ("ficam disponíveis em breve"). Faixa com texto diferente ("você vê Início, Financeiro e Minhas informações..."); a barra do sistema mostra também "Início", o canvas só "Financeiro" e "Minhas informações".
  - Código: frontend/src/features/portal/PortalInfoPage.tsx (Sections), PortalSettings.tsx

#### M32 · Portal Carteirinha

- Rodada 1: Portal Carteirinha: canvas cartão visual escuro com marca e faixas + "Baixar imagem"; sistema cartão simples.
- Print (sistema à esquerda, canvas à direita): `imagens/rodada-1/20-portal-carteirinha-claro.png`
- **Rodada 2 · portal-b-1 · PB1-05** (Carteirinha) — amplia M32
  - Categoria: ícone/avatar/cor, layout, botões/ações · Largura/tema: 1440 claro, 390 claro
  - Prints: sistema `imagens/portal-b-1/PortalCarteirinha-1440-claro-sistema.png, PortalMCarteirinhaOk-390-claro-sistema.png` · canvas `PortalCarteirinha-1440-claro-canvas.png, PortalMCarteirinhaOk-390-claro-canvas.png`
  - canvas = cartão escuro com faixa de cor da organização (marca + tag "Ativo"), avatar roxo, nome em caixa alta, "Aluno desde 03/2022 · Tijuca", modalidades com bolinha e faixa desenhada com grau, "Válida em...", "feito com ctcore"; ao lado, texto "Mostre na recepção quando pedirem. Não tem QR code..." e botões "Baixar imagem" e "Voltar" (no celular "‹ Voltar" no topo). Sistema = cartão claro com borda, etiqueta "Academia Alfa", avatar claro, tag "Ativo" + "em 29/10/2026", faixa como retângulo sem grau, "Atualizada em ...", sem texto de recepção, sem "Baixar imagem", sem "Voltar".
  - Código: frontend/src/features/portal/PortalMemberCard.tsx:16

#### M33 · Portal Pedidos

- Rodada 1: Portal Pedidos: canvas 4 cartões de ação + tabela "Meus pedidos"; sistema seletor de aluno + "Fazer um pedido" recolhido.
- Print (sistema à esquerda, canvas à direita): `imagens/rodada-1/22-portal-pedidos-claro.png`
- **Rodada 2 · portal-b-2 · portal-b-2-01** (Pedidos (desktop e celular)) — amplia M33
  - Categoria: layout / botões/ações / colunas · Largura/tema: 1440 claro, 390 claro
  - Prints: sistema `imagens/portal-b-2/PortalPedidos-1440-claro-fx-sistema.png, PortalMPedidos-390-claro-sistema.png` · canvas `imagens/portal-b-2/PortalPedidos-1440-claro-canvas.png, PortalMPedidos-390-claro-canvas.png`
  - canvas tem rótulo "MINHAS INFORMAÇÕES" acima do título "PEDIDOS", subtítulo "A academia sempre confirma. Pedidos sem resposta expiram em 30 dias...", 4 cartões lado a lado (Trocar de turma, Nova modalidade, Trocar de plano, Solicitar encerramento, cada um com ícone, texto de apoio e "Pedir") e "Meus pedidos" em tabela (Pedido, Detalhe, Enviado, Situação com selo colorido, nota "expira em N dias"/motivo). No celular: "Voltar", cartões 2x2 e lista de cartões com selo. Sistema: título "MEUS PEDIDOS" sem rótulo, subtítulo diferente, seletor "Aluno" sempre visível, cartão "Fazer um pedido" com uma linha por modalidade e botões em texto longo ("Pedir troca de turma"...), "Pedidos feitos" como lista de texto "Aberto · data hora" (sem selo, sem "expira em"). Aba "Minhas informações" não fica marcada como ativa na barra. Sem modalidade ativa o cartão "Fazer um pedido" fica vazio (canvas sempre mostra os 4 cartões).
  - Código: frontend/src/features/portal/PortalRequestsPage.tsx (Student); frontend/src/i18n/pt-BR.json "portalOwn"

#### M34 · Portal Início

- Rodada 1: Portal Início: canvas painel (cobrança em destaque, próximas aulas, avisos, graduação); sistema lista simples.
- Print (sistema à esquerda, canvas à direita): `imagens/rodada-1/07-portal-aluno-claro.png`
- **Rodada 2 · portal-a-1 · PA-08** (Início do portal (tudo em dia)) — repetido de M34
  - Categoria: layout, texto · Largura/tema: 1440 claro
  - Prints: sistema `imagens/portal-a-1/PortalEmDia-1440-claro-sistema.png` · canvas `imagens/portal-a-1/PortalEmDia-1440-claro-canvas.png`
  - sistema "BEM-VINDO AO CTCORE" (canvas: data + "OLÁ, MARINA"), lista vertical de 5 blocos. Canvas = painel em grade: cartão "FINANCEIRO" com selo verde "Tudo em dia", "Próximo vencimento", botão "Ver financeiro"; "PRÓXIMAS AULAS" com horário grande à esquerda e link "Ver agenda ›"; "AVISOS" com "Ver todos ›"; "GRADUAÇÃO" com faixa desenhada e "Ver carteirinha" com ícone. Sistema usa um filtro "Filtrar por modalidade e unidade" (select nativo) no topo, "Cobrança / Tudo em dia" sem próximo vencimento nem botão, links soltos "Ver carteirinha" (abaixo do título) e "Meus pedidos" (fim), "Ver todos os avisos" sublinhado, e não tem "Ver agenda". Canvas não mostra o filtro (só há uma combinação).

#### M35 · Portal Termo

- Rodada 1: Portal Termo: caso do teste visual usa aluno sem termo pendente, nunca mostra o termo com os dois aceites.
- Print (sistema à esquerda, canvas à direita): `imagens/rodada-1/14-portal-termo-claro.png`

#### M36 · Início da gestão

- Rodada 1: Início da gestão: sistema só "BEM-VINDO AO CTCORE" (previsto nas #122 a #124, Sprint 13).
- Fora da 10.5: o Início de cada perfil é entrega das issues #122, #123 e #124 (Sprint 13); a tela atual é provisória.
- Print (sistema à esquerda, canvas à direita): `imagens/rodada-1/02-inicio-claro.png`
- **Rodada 2 · comunicacao-e-estrutura-da-gestao-2 · CE2-08** (Início do Administrador, Professor e tema escuro) — repetido de M36 · gravidade revista de alta para média: Início previsto nas #122 a #124 (Sprint 13); fora da 10.5.
  - Categoria: botões/ações / estados / texto · Largura/tema: 1440 claro e escuro
  - Prints: sistema `imagens/comunicacao-e-estrutura-da-gestao-2/Main-1440-claro-sistema.png, Main-1440-escuro-sistema.png, ProfessorInicio-1440-claro-sistema.png` · canvas `imagens/comunicacao-e-estrutura-da-gestao-2/Main-canvas.png, MainEscuro-canvas.png, ProfessorInicio-canvas.png`
  - canvas é um painel ("BOM DIA, CARLA", "Nova matrícula", "PRECISA DE ATENÇÃO" com 7 itens e botões de ação, 4 cartões de número, "AULAS DE HOJE", Aniversariantes, "Primeiros passos"; Professor: aulas de hoje/amanhã com alunos e faixas, Experimentais de hoje, Aniversariantes, "Para você"). Sistema só tem "BEM-VINDO AO CTCORE", linha da unidade e o cartão "Aniversariantes de hoje" (vazio). É a entrega das issues #122 (Coordenador), #123 (Administrador), #124 (Professor), Sprint 13; portanto previsto, mas a tela construída até agora não corresponde. Tema escuro do sistema legível.
  - Código: `frontend/src/App.tsx:55-72` (`Welcome`, `ScopeLine`, `BirthdaysCard`).

#### M37 · LoginTravadoM **[NOVO R2]**

- Lote/ID de origem: acesso · acesso-03
- Categoria: estados · Largura/tema: 390 claro
- Prints: sistema `imagens/acesso/LoginTravadoM-390-claro-sistema.png` · canvas `imagens/acesso/LoginTravadoM-390-claro-canvas.png`
- O que difere: canvas mostra aviso âmbar com cadeado, "Muitas tentativas. Por segurança, este e-mail fica travado por 15 minutos. Tente de novo às 21h47 ou crie uma nova senha.", botão Entrar desabilitado e botão secundário "Criar nova senha". Sistema: faixa vermelha "Muitas tentativas. Espere alguns minutos e tente de novo.", sem horário de liberação, Entrar continua ativo, sem "Criar nova senha" (só o link "Esqueci minha senha"). A resposta 429 do MSW não traz horário; confirmar se o contrato traz `retryAt`.
- Código: `frontend/src/features/auth/LoginPage.tsx:59`
- Padrão: estado de bloqueio sem horário/ação

#### M38 · CodigoReservaM **[NOVO R2]**

- Lote/ID de origem: acesso · acesso-05
- Categoria: texto / estados · Largura/tema: 390 claro
- Prints: sistema `imagens/acesso/CodigoReservaM-390-claro-sistema.png` · canvas `imagens/acesso/CodigoReservaM-390-claro-canvas.png`
- O que difere: canvas tem tela própria: título "Código de reserva", subtítulo "Use um dos 10 códigos que você guardou ao ativar as duas etapas.", dica "Cada código vale uma vez.", link "Voltar para o código do app" e caixa informativa "Depois de entrar, gere códigos novos em Minha conta: restam 9." Sistema reaproveita o título "Verificação em duas etapas", subtítulo "Digite um dos seus códigos de reserva.", botões "Usar o código do app" e "Voltar", sem dica e sem a caixa de códigos restantes.
- Padrão: texto de tela de verificação

#### M39 · RecuperarSenhaM **[NOVO R2]**

- Lote/ID de origem: acesso · acesso-06
- Categoria: texto / estados · Largura/tema: 390 claro
- Prints: sistema `imagens/acesso/RecuperarSenhaM-390-claro-sistema.png e ...-enviado-sistema.png` · canvas `imagens/acesso/RecuperarSenhaM-390-claro-canvas.png`
- O que difere: título "Esqueci minha senha" e subtítulo "Enviamos um link para criar uma nova senha." (canvas) x "Recuperar senha" com subtítulo mais longo (sistema). Canvas depois do envio mantém o formulário e acrescenta caixa azul com envelope "Se este e-mail tiver conta, você vai receber o link em alguns minutos. Confira também a caixa de spam." e "Voltar para o login"; sistema troca o formulário por faixa verde "Se esse e-mail tiver uma conta, você vai receber um link em instantes." e link "Entrar" (sem aviso sobre spam).
- Padrão: texto de tela de acesso; caixa informativa (canvas) x faixa de sucesso (sistema)

#### M40 · LinkSenhaVencidoM **[NOVO R2]**

- Lote/ID de origem: acesso · acesso-07
- Categoria: estados / botões · Largura/tema: 390 claro
- Prints: sistema `imagens/acesso/LinkSenhaVencidoM-390-claro-sistema.png` · canvas `imagens/acesso/LinkSenhaVencidoM-390-claro-canvas.png`
- O que difere: canvas "Este link venceu", explica "vale 1 hora e só funciona uma vez", já traz o campo de e-mail preenchido, botão "Enviar um link novo" e "Voltar para o login". Sistema "Este link não é válido ou já foi usado" com faixa âmbar, sem campo de e-mail nem botão de reenvio, só o link "Pedir novo link". Sistema chega a esse estado só depois de enviar a nova senha (a página abre como formulário e o erro vem na resposta).
- Código: `frontend/src/features/auth/ResetPasswordPage.tsx`
- Padrão: estado de link vencido sem ação direta

#### M41 · NovaSenhaM **[NOVO R2]**

- Lote/ID de origem: acesso · acesso-08
- Categoria: texto / campos · Largura/tema: 390 claro
- Prints: sistema `imagens/acesso/NovaSenhaM-390-claro-sistema.png` · canvas `imagens/acesso/NovaSenhaM-390-claro-canvas.png`
- O que difere: canvas tem subtítulo "Depois de criar, você entra direto.", rótulos "Nova senha" e "Repita a nova senha", lista de 4 regras com ✓ verde (inclui "Não aparece em vazamentos conhecidos") e nota "As sessões abertas em outros aparelhos serão encerradas." Sistema: rótulos "Senha" e "Confirmar senha", lista "A senha precisa ter:" com 3 regras em texto "pendente" (sem ícone, sem a regra de vazamento), sem subtítulo e sem a nota das sessões; botão desabilitado em cinza chapado (canvas: bege suave).
- Código: `frontend/src/features/auth/PasswordRules.tsx`
- Padrão: regras de senha em texto x lista com ✓

#### M42 · EscolhaOrganizacao / EscolhaOrgM **[NOVO R2]**

- Lote/ID de origem: acesso · acesso-12
- Categoria: modal/gaveta / layout / ícone-avatar · Largura/tema: 1440 claro e escuro, 390 claro e escuro
- Prints: sistema `imagens/acesso/EscolhaOrganizacao-1440-claro-sistema.png` · canvas `imagens/acesso/EscolhaOrganizacao-1440-claro-canvas.png (e EscolhaOrgM-390-claro-sistema/canvas)`
- O que difere: canvas é uma página só com o cartão "Onde você quer entrar?" (saudação "Olá, Carla…"), cada vínculo com avatar de iniciais colorido, nome da organização, "Gestão · Administradora · Tijuca e Barra", "Último acesso…", seta, caixa "Lembrar esta escolha neste aparelho" e botão "Sair". Sistema: janela "Escolher organização e área" sobre a tela do portal (que mostra por trás "Você não tem acesso" e o menu do portal), itens só com nome da organização em caixa alta e "Gestão (Administrador)", sem avatar, sem unidades, sem último acesso, sem "Lembrar" e sem "Sair". No celular o canvas é cartão em página cheia, o sistema segue em janela.
- Código: `frontend/src/features/auth/ChooseLinkModal.tsx`, `ChooseLinkGate.tsx:5-11`
- Padrão: modal x página; lista sem avatar

#### M43 · MinhaContaNotificacoes **[NOVO R2]**

- Lote/ID de origem: acesso · acesso-15 · gravidade revista (era alta): "Receber tudo" existe no painel do sino (`NotificationPanel.tsx:111-114`); falta só a aba de preferências em Minha conta.
- Categoria: botões/ações / filtros-abas · Largura/tema: 1440 claro
- Prints: sistema `imagens/acesso/MinhaConta-1440-claro-sistema.png` · canvas `imagens/acesso/MinhaContaNotificacoes-1440-claro-canvas.png`
- O que difere: o canvas tem abas "Acesso | Duas etapas | Sessões | Notificações" e na aba Notificações os cartões "Sino da gestão" (Notificações da minha unidade, Receber tudo), "Resumo diário por e-mail" e "E-mails de boas-vindas", com "Descartar" e "Salvar alterações". O sistema não tem abas nem nenhuma preferência de notificação em Minha conta: `AccountPage` só monta Senha, E-mail, Duas etapas e Sessões. O usuário não consegue ligar/desligar "Receber tudo" (RN-NOT-03), o resumo diário nem os e-mails de aviso pela tela. (Dado que esse trecho do canvas não foi entregue na #419, confirmar se tem issue própria; a conta de aluno também precisa das preferências.)
- Código: `frontend/src/features/account/AccountPage.tsx:12-23`; texto "Receber tudo" existe só em `frontend/src/i18n/pt-BR.json:2688` (outra tela)
- Padrão: abas de Minha conta ausentes

#### M44 · DuasEtapas (janela) **[NOVO R2]**

- Lote/ID de origem: acesso · acesso-16
- Categoria: modal/gaveta / texto / botões · Largura/tema: 1440 claro
- Prints: sistema `imagens/acesso/DuasEtapas-1440-claro-vp-sistema.png e ...-codigos-sistema.png` · canvas `imagens/acesso/DuasEtapas-1440-claro-canvas.png`
- O que difere: canvas "ATIVAR DUAS ETAPAS" (largura ~620 px) com subtítulo "Obrigatória para o Coordenador · configurada no primeiro acesso", passos numerados (1 Ler o QR code, 2 Confirmar código, 3 Guardar códigos), QR à esquerda e instruções numeradas à direita, chave em campo com espaços ("JBSW Y3DP EHPK 3PXP"), campo com placeholder "000 000", aviso "Não existe código por e-mail ou SMS…", botões "Agora não" e "Confirmar". Sistema: janela estreita (~510 px) de uma coluna, sem indicador de passos, QR centralizado, "Chave para digitar" em fonte mono sem separação, campo "Código de 6 dígitos" sem placeholder, botões "Ativar" e "Cancelar"; o passo dos códigos usa "Copiar códigos"/"Baixar códigos" e caixa "Já guardei os códigos" com "Concluir" em largura total (a versão do canvas fica na janela "Códigos de reserva" abaixo).
- Código: `frontend/src/features/auth/TwoFactorSetupDialog.tsx:63-64`
- Padrão: modal em passos x modal simples

#### M45 · CodigosReserva (janela) **[NOVO R2]**

- Lote/ID de origem: acesso · acesso-17
- Categoria: modal/gaveta / botões · Largura/tema: 1440 claro
- Prints: sistema `imagens/acesso/CodigosReserva-1440-claro-vp-sistema.png e imagens/acesso/DuasEtapas-1440-claro-codigos-sistema.png` · canvas `imagens/acesso/CodigosReserva-1440-claro-canvas.png`
- O que difere: canvas mostra direto os 10 códigos em grade 2x5 em caixas largas (formato 4821-9037), botões "Copiar" e "Baixar .txt" com ícone, caixa âmbar "Sem o celular e sem códigos, só o suporte desliga as duas etapas, depois de confirmar sua identidade.", caixa "Guardei os códigos em lugar seguro" e "Concluir" lado a lado no rodapé. Sistema: ao abrir pede antes senha e código ("Gerar códigos novos"/"Cancelar") e, nos passos seguintes, mostra códigos em formato RESERVA001, "Copiar códigos"/"Baixar códigos" sem ícone, sem a caixa âmbar, "Concluir" em largura total e desabilitado até marcar a caixa. (O passo de senha+código não existe no canvas; ver "A conferir na especificação".)
- Código: `frontend/src/features/auth/BackupCodesDialog.tsx`, `frontend/src/features/auth/ReauthDialog.tsx`
- Padrão: modal em passos x modal simples

#### M46 · GMErro **[NOVO R2]**

- Lote/ID de origem: acesso · acesso-19
- Categoria: estados · Largura/tema: 360 claro
- Prints: sistema `imagens/acesso/GMErro-360-claro-sistema.png` · canvas `imagens/acesso/GMErro-360-claro-canvas.png`
- O que difere: canvas mostra o erro como a única coisa da tela (título "COBRANÇAS", ícone vermelho sem círculo, "Não conseguimos carregar as cobranças", "Nada foi alterado. Confira a sua conexão e tente de novo.", botão "Tentar de novo" com ícone de atualizar), com cabeçalho do app (logo da organização + título + unidade, busca, sino). Sistema: página renderiza ações e filtros (campos de data nativos, seletores nativos) acima e o erro só aparece abaixo, em ícone com círculo vermelho claro, título genérico "Não foi possível carregar", três frases ("Não conseguimos carregar as informações agora." / "Nada foi alterado nos seus dados." / "Tente de novo em instantes. Se continuar, fale com o suporte."), botão sem ícone; cabeçalho móvel diferente (voltar, sino, avatar, busca, tema). O erro não cita o que falhou.
- Código: `frontend/src/components/states` (ErrorState)
- Padrão: estado de erro genérico, filtros visíveis com erro

#### M47 · Cancelar comunicado agendado (janela) **[NOVO R2]**

- Lote/ID de origem: comunicacao-e-estrutura-da-gestao-1 · CE1-08
- Categoria: modal/gaveta, texto, botões/ações · Largura/tema: 1440 claro
- Prints: sistema `imagens/comunicacao-e-estrutura-da-gestao-1/ComunicadoCancelar-1440-claro-sistema.png` · canvas `imagens/comunicacao-e-estrutura-da-gestao-1/ComunicadoCancelar-1440-claro-canvas.png`
- O que difere: título "CANCELAR COMUNICADO AGENDADO" em fonte de título maiúscula (sistema: "Cancelar agendamento" menor, sem caixa alta); canvas explica "A vaga do limite diário volta." e tem caixa "RESUMO" (título + data do agendamento, turma + número de alunos); sistema só a frase "O comunicado "X" ainda não saiu e será apagado. Ninguém receberá esse aviso."; botões: canvas "Voltar" (texto) e "Cancelar comunicado" em contorno vermelho; sistema "Manter" (contorno) e "Cancelar agendamento" preenchido escuro (não sinaliza destruição); sem rodapé separado.
- Código: `features/announcements/CancelAnnouncementDialog.tsx`; `pt-BR.json` `announcements.cancel.*`.
- Padrão: confirmação destrutiva sem cor de perigo

#### M48 · Comunicados, limite diário **[NOVO R2]**

- Lote/ID de origem: comunicacao-e-estrutura-da-gestao-1 · CE1-09
- Categoria: estados, botões/ações · Largura/tema: 1440 claro
- Prints: sistema `imagens/comunicacao-e-estrutura-da-gestao-1/ComunicadoLimite-1440-claro-sistema.png` · canvas `imagens/comunicacao-e-estrutura-da-gestao-1/ComunicadoLimite-1440-claro-canvas.png`
- O que difere: canvas mostra na lista uma faixa amarela "A Tijuca chegou a 20 comunicados hoje, contando os agendados para hoje. Amanhã o limite volta. Cancelar um agendado de hoje libera a vaga." e o botão "Novo comunicado" desativado; subtítulo "20 de 20 enviados hoje". Sistema: nada na lista, botão sempre ativo; o limite só aparece depois de preencher e enviar, como texto vermelho solto no pé do formulário ("Esta unidade já atingiu o limite de 20 comunicados por dia. Escolha outro dia."), sem ícone nem faixa.
- Código: `features/announcements/NewAnnouncementPage.tsx:187`; a listagem não devolve a contagem do dia (ver A conferir).
- Padrão: estado de limite só no envio

#### M49 · Novo comunicado para a organização inteira (janela) **[NOVO R2]**

- Lote/ID de origem: comunicacao-e-estrutura-da-gestao-1 · CE1-10
- Categoria: modal/gaveta · Largura/tema: 1440 claro
- Prints: sistema `imagens/comunicacao-e-estrutura-da-gestao-1/ComunicadoNovo-1440-claro-destino-sistema.png` · canvas `imagens/comunicacao-e-estrutura-da-gestao-1/ComunicadoOrganizacao-1440-claro-canvas.png`
- O que difere: canvas = janela sobre a lista com rádios "Organização inteira / Uma unidade / Uma modalidade de uma unidade / Uma turma", resumo "Vai para 286 pessoas nas duas unidades, 271 por e-mail" e "Limite próprio da organização: 1 de 20 hoje. Não consome o limite das unidades.", botões Cancelar / Agendar / Enviar. Sistema: "Todas as unidades" é só mais um item do select de destino, numa página inteira; sem resumo do limite próprio; "Agendar" é rádio em "Quando enviar", não botão.
- Código: `features/announcements/AnnouncementTargetPicker.tsx:33`.
- Padrão: janela x página

#### M50 · Comunicados no celular **[NOVO R2]**

- Lote/ID de origem: comunicacao-e-estrutura-da-gestao-1 · CE1-11
- Categoria: layout, filtros/abas/busca · Largura/tema: 390 claro (canvas 360)
- Prints: sistema `imagens/comunicacao-e-estrutura-da-gestao-1/Comunicados-390-claro-sistema.png` · canvas `imagens/comunicacao-e-estrutura-da-gestao-1/GMComunicados-360-claro-canvas.png`
- O que difere: canvas = cartões compactos com selos (tipo e situação) no topo, título em negrito e uma linha "Turma · data · autor" / "18 de 26 leram", botão "Novo comunicado" largo com ícone "+" abaixo do título, sem filtros. Sistema: botão pequeno ao lado do título, parágrafo de subtítulo, caixa de busca vazia (quadrado sem placeholder) + De/Até nativos + 2 selects ocupando ~170 px, cartões com 7 linhas rótulo/valor (Data, Destino, Tipo, Autor, Situação, Destinatários, Lidos) e botão "Cancelar agendamento" largo. Barra: canvas tem voltar + título "Comunicados" + unidade "Tijuca ⌄", lupa e sino; sistema só voltar, sino, avatar, lupa apagada e tema, sem título. Aba inferior "Cobranças" apagada (`/cobrancas` com `available=false`).
- Padrão: cartão em lista de pares rótulo/valor; busca vazia sem placeholder

#### M51 · Menu aberto no celular **[NOVO R2]**

- Lote/ID de origem: comunicacao-e-estrutura-da-gestao-2 · CE2-04
- Categoria: modal/gaveta / layout / botões/ações · Largura/tema: 390 claro (perfil Professor no sistema; canvas é Administrador)
- Prints: sistema `imagens/comunicacao-e-estrutura-da-gestao-2/GMMenu-390-claro-sistema.png` · canvas `imagens/comunicacao-e-estrutura-da-gestao-2/GMMenu-canvas.png`
- O que difere: canvas é tela cheia escura com marca + "X", seletor "Unidade Tijuca" dentro do menu, grupos como rótulos pequenos em caixa-alta (PESSOAS, OPERAÇÃO, FINANCEIRO, COMUNICAÇÃO, CONFIGURAÇÕES) com todos os itens já listados em fonte grande e separadores, e no fim Ajuda, Novidades, "Tema escuro", "Minha conta", "Sair". Sistema: painel escuro de largura fixa (~256 px) dentro de 390 px (sobra faixa escura vazia à direita), título "Menu", grupos recolhidos em sanfona (só Operação aberta), sem seletor de unidade, sem "Tema escuro", "Minha conta" nem "Sair" (ficam na barra de cima), Ajuda e Novidades apagadas "(Em breve)". Sem botão de fechar visível no print.
- Código: `frontend/src/components/layout/MobileMenu.tsx`, `frontend/src/components/layout/MobileTabs.tsx`.
- Padrão: menu celular em sanfona x lista completa

#### M52 · Seletor de unidade no celular **[NOVO R2]**

- Lote/ID de origem: comunicacao-e-estrutura-da-gestao-2 · CE2-06
- Categoria: modal/gaveta / layout · Largura/tema: 390 claro, com fixture de 2 unidades
- Prints: sistema `imagens/comunicacao-e-estrutura-da-gestao-2/GMUnidade-390-claro-unidades-sistema.png` · canvas `imagens/comunicacao-e-estrutura-da-gestao-2/GMUnidade-canvas.png`
- O que difere: canvas abre uma folha deslizante de baixo para cima, título "UNIDADE", uma opção por cartão com botão de opção, nome e "131 alunos ativos", "Todas as unidades" com "Só consulta · Coordenador" e "Fechar"; o seletor fica sob o título da tela na barra do topo (ícone prédio, "Tijuca"). Sistema: `<select>` nativo "Todas as unidades" no topo, que força a barra a quebrar em duas linhas (seletor + sino + avatar em cima; busca e tema embaixo) e deixa a trilha de navegação cortada ("Pes... > Alun..."). No celular o canvas também troca a marca e o nome do início por logo + título da página + unidade; o sistema mantém seta de voltar + trilha.
- Código: `frontend/src/features/units/UnitSwitcher.tsx` (`Select`), `frontend/src/components/layout/AppLayout.tsx`.
- Padrão: seletor nativo no lugar de folha / barra do celular quebrando em duas linhas

#### M53 · ConfigPagamentos **[NOVO R2]**

- Lote/ID de origem: configuracoes-1 · CF1-04
- Categoria: colunas/campos, estados · Largura/tema: 1440 claro
- Prints: sistema `imagens/configuracoes-1/ConfigUnidade-1440-claro-sistema.png (cartão "Pagamentos")` · canvas `imagens/configuracoes-1/ConfigPagamentos-1440-claro-canvas.png`
- O que difere: canvas é a aba Pagamentos com cartão "Mercado Pago · Conectado" (Reconectar/Desconectar), tabela "Taxas por meio de pagamento" (Pix, Cartão de crédito, Cartão de débito, Boleto, Débito automático) com % + R$ e coluna "Exemplo: mensalidade de R$ 180,00", faixa "No portal, o aluno vê: ..." e "Chave Pix da unidade" com seletor de tipo + chave. Sistema: 9 campos de taxa fixa em R$ (Dinheiro, Pix, Boleto, Cartão de crédito, Cartão de débito, Débito automático, Pix manual, Maquininha, Transferência), sem percentual, sem exemplo, "Chave Pix" em campo único e aviso "Mercado Pago não conectado ... chega em uma próxima entrega". Conexão com o fornecedor é da #97 (Sprint 11, aberta).
- Padrão: taxa só em reais · sem exemplo calculado

#### M54 · ConfigPermProf **[NOVO R2]**

- Lote/ID de origem: configuracoes-1 · CF1-05
- Categoria: botões/ações, texto · Largura/tema: 1440 claro
- Prints: sistema `imagens/configuracoes-1/ConfigUnidade-1440-claro-sistema.png (cartão "Permissões do Professor"); par visto com ConfigPermProf-1440-claro-sistema.png` · canvas `imagens/configuracoes-1/ConfigPermProf-1440-claro-canvas.png`
- O que difere: sistema usa caixas de marcação sem descrição: "Enviar comunicados", "Editar a agenda", "Ver os contatos dos alunos", "Ver aulas experimentais", "Ver ficha do aluno". Canvas usa interruptores com descrição (ex.: "Só para as turmas dele", "Das turmas dele ou em que é substituto"), rótulos "Enviar comunicado", "Editar e cancelar aulas", "Ver contatos", título "O que os Professores da Tijuca podem fazer" e faixa com cadeado "Em qualquer caso, o Professor nunca vê valores, plano, ...". Ausentes no sistema: descrições e faixa.
- Padrão: caixa de marcação x interruptor · descrições ausentes

#### M55 · ConfigLinks (Divulgação) **[NOVO R2]**

- Lote/ID de origem: configuracoes-1 · CF1-06
- Categoria: botões/ações · Largura/tema: 1440 claro
- Prints: sistema `imagens/configuracoes-1/ConfigUnidade-1440-claro-sistema.png (cartão "Divulgação")` · canvas `imagens/configuracoes-1/ConfigLinks-1440-claro-canvas.png`
- O que difere: o sistema só tem as caixas "Aparecer no diretório", "Link público de aula experimental", "Link público de autocadastro". Canvas mostra os links com URL, "Copiar link", "QR code PNG", "PDF para imprimir", selo "Ligado" e interruptor. A tela de links é da #162 (Sprint 18, aberta); só a configuração existe.
- Padrão: caixa de marcação x interruptor

#### M56 · GMConfigUnidade **[NOVO R2]**

- Lote/ID de origem: configuracoes-1 · CF1-07
- Categoria: layout · Largura/tema: 390 claro (canvas 360)
- Prints: sistema `imagens/configuracoes-1/GMConfigUnidade-390-claro-sistema.png` · canvas `imagens/configuracoes-1/GMConfigUnidade-360-claro-canvas.png`
- O que difere: canvas tem cabeçalho com seta de voltar, título "Unidade" e seletor da unidade, abas em pílula, formulário curto (Nome, Endereço, WhatsApp, Dias de vencimento, Modalidades oferecidas), "Última alteração" e barra fixa Descartar/Salvar. Sistema mostra a página longa do desktop (~4000 px), "Salvar" solto no fim e, na barra de cima, o caminho truncado "C... > Confi..." (baixa).
- Padrão: celular = desktop empilhado

#### M57 · GMConfig **[NOVO R2]**

- Lote/ID de origem: configuracoes-1 · CF1-08
- Categoria: layout, botões/ações · Largura/tema: 390 claro
- Prints: sistema `imagens/configuracoes-1/GMConfig-390-claro-sistema.png (página de Organização; não há tela equivalente)` · canvas `imagens/configuracoes-1/GMConfig-360-claro-canvas.png`
- O que difere: canvas tem uma tela "Configurações" com lista de links com seta (Organização, Unidade, Modalidades, Faixas etárias, Usuários, Termos, Textos, Auditoria, Importar e exportar) e nota "Assinatura e Unidades aparecem só para o Coordenador". `frontend/src/App.tsx:155-165` não tem rota `/configuracoes`; no celular o acesso seria pelo "Menu" da barra inferior (não abri a gaveta). A barra de cima do celular no sistema: seta, caminho truncado, sino, avatar, busca (canvas: marca, título e unidade).
- Padrão: celular sem tela-índice

#### M58 · Menu de Configurações **[NOVO R2]**

- Lote/ID de origem: configuracoes-1 · CF1-09
- Categoria: texto, botões/ações · Largura/tema: 1440 claro
- Prints: sistema `imagens/configuracoes-1/ConfigAcademia-1440-claro-sistema.png` · canvas `imagens/configuracoes-1/ConfigAcademia-1440-claro-canvas.png`
- O que difere: rótulos do sistema "Configurações da unidade" e "Configurações da organização" (cortado com reticências) contra "Unidade" e "Organização"; "Usuários da gestão" contra "Usuários"; ordem do canvas Organização, Unidades, Unidade (sistema: Unidade, Organização, ..., Unidades por último). Faltam Assinatura, Auditoria e Importar e exportar (`menuCatalog.ts:64-66`, `available=false`; sprints 14, 13 e 17, esperado). Grupos Comunicação e Relatórios ausentes; "Ajuda (Em breve)" e "Novidades (Em breve)" com texto de estado, sem ponto vermelho. Seletor de unidade ("Todas as unidades") ausente na barra com `ana@` (`ManagementLayout.tsx:45` tem `UnitSwitcher`; provavelmente oculto com uma só unidade ativa, não confirmado).
- Padrão: rótulos de menu longos

#### M59 · ConfigModalidadesAdmin **[NOVO R2]**

- Lote/ID de origem: configuracoes-1 · CF1-12
- Categoria: colunas/campos, estados · Largura/tema: 1440 claro
- Prints: sistema `imagens/configuracoes-1/ConfigModalidadesAdmin-1440-claro-sistema.png` · canvas `imagens/configuracoes-1/ConfigModalidadesAdmin-1440-claro-canvas.png`
- O que difere: canvas mostra faixa azul "Quem ativa e desativa modalidades ... é o Coordenador" e tabela Modalidade (ponto colorido) / Graduação / Unidades que oferecem / Situação. Sistema mostra os cartões do Coordenador (Suas modalidades vazio + Disponíveis para ativar sem botões + Pedidos enviados), sem faixa e sem tabela. Subtítulo "Catálogo da organização · só consulta para o Administrador" ausente.
- Padrão: visão por perfil sem tela própria

#### M60 · GMUsuarios (celular) **[NOVO R2]**

- Lote/ID de origem: configuracoes-2 · configuracoes-2-03
- Categoria: layout · Largura/tema: 360 e 390 claro
- Prints: sistema `imagens/configuracoes-2/GMUsuarios-360-claro-sistema.png` · canvas `imagens/configuracoes-2/GMUsuarios-360-claro-canvas.png`
- O que difere: sistema repete a tabela do desktop, que estoura a largura (colunas Situação e Ações só com rolagem lateral; botões de ação inalcançáveis à vista). Canvas: lista de cartões (avatar, nome, "Perfil · último acesso", pílula de situação), botão Convidar em largura total, cabeçalho "Usuários" com seletor de unidade e busca na barra.
- Padrão: tabela-no-celular

#### M61 · UsuarioConvidar **[NOVO R2]**

- Lote/ID de origem: configuracoes-2 · configuracoes-2-04
- Categoria: modal/gaveta · Largura/tema: 1440 claro
- Prints: sistema `imagens/configuracoes-2/UsuarioConvidar-1440-claro-sistema.png` · canvas `imagens/configuracoes-2/UsuarioConvidar-1440-claro-canvas.png`
- O que difere: título "Convidar usuário" (canvas: "Convidar para a equipe"); campos empilhados sem asterisco de obrigatório (canvas: Nome e E-mail lado a lado, com *); falta o bloco "Perfil" com cartões Administrador/Professor; falta o texto "O convite vale 7 dias..."; botões sem rodapé separado e "Enviar convite" sem ícone de envio; unidades em caixa simples (canvas: lista com caixas marcadas, ver tela).
- Padrão: modal-sem-rodape; campo-sem-asterisco

#### M62 · UnidadeNova **[NOVO R2]**

- Lote/ID de origem: configuracoes-2 · configuracoes-2-05
- Categoria: layout / texto · Largura/tema: 1440 claro
- Prints: sistema `imagens/configuracoes-2/UnidadeNova-1440-claro-sistema.png (+ `-passo2`, `-passo3`)` · canvas `imagens/configuracoes-2/UnidadeNova-1440-claro-canvas.png`
- O que difere: canvas = cartão com assistente de 4 etapas em linha (Nome e endereço, Contato, Modalidades, Administrador), "Nome curto" gerado já na etapa 1 ("Gerado do nome; ajustável uma vez"), CEP + endereço lado a lado e mapa com pino arrastável, "Cancelar"/"Continuar" com seta; subtítulo "Assistente curto...". Sistema: 3 etapas (Unidade, Endereço, Contato e convite), sem cartão, campos de 576 px empilhados, indicador "Etapa 1 de 3" com bolinhas numeradas, link "Voltar para as unidades" no lugar de "Cancelar", sem mapa (só "Ajustar coordenadas"), sem etapa de Modalidades (a oferta de modalidades só existe depois, em Unidade), e o convite do Administrador é opcional dentro da etapa 3 (canvas: etapa própria).
- Padrão: assistente-estrutura; cartao-ausente

#### M63 · UnidadeDesativar **[NOVO R2]**

- Lote/ID de origem: configuracoes-2 · configuracoes-2-06
- Categoria: modal/gaveta · Largura/tema: 1440 claro (e escuro)
- Prints: sistema `imagens/configuracoes-2/UnidadeDesativar-1440-claro-bloqueios-sistema.png` · canvas `imagens/configuracoes-2/UnidadeDesativar-1440-claro-canvas.png`
- O que difere: canvas "Desativar a Barra" em vermelho, com checklist "O que impede agora" (x vermelho/✓ verde por item: alunos, turmas, cobrança em conferência), links "Ver alunos"/"Ver turmas", texto de orientação e confirmação por "DESATIVAR e sua senha"; rodapé com "Fechar" e "Desativar unidade" desabilitado. Sistema: aviso rosa com lista de pontos, sem links, sem item de cobranças em conferência, campo de nome da unidade, botões Cancelar/Continuar; título "Desativar Unidade Centro" em fonte normal (canvas em caixa alta, vermelho).
- Padrão: modal-bloqueio-checklist

#### M64 · UnidadeUnica **[NOVO R2]**

- Lote/ID de origem: configuracoes-2 · configuracoes-2-07
- Categoria: modal/gaveta / botões · Largura/tema: 1440 claro
- Prints: sistema `imagens/configuracoes-2/UnidadeDesativar-1440-claro-sistema.png` · canvas `imagens/configuracoes-2/UnidadeUnica-1440-claro-canvas.png`
- O que difere: canvas: janela só informativa ("A única unidade ativa não pode ser desativada", "Toda organização tem ao menos uma unidade ativa", texto "cancele a assinatura em Configurações › Assinatura") com "Fechar" e "Ir para a assinatura". Sistema: mesmo aviso dentro do formulário com campo de confirmação e "Continuar" desabilitado, sem "Fechar" nem caminho para assinatura (a tela de assinatura ainda não existe, `menuCatalog.ts:64`, `available=false`).
- Padrão: modal-bloqueio-checklist

#### M65 · ModalidadesOferecidasRecusa **[NOVO R2]**

- Lote/ID de origem: configuracoes-2 · configuracoes-2-10
- Categoria: layout / texto · Largura/tema: 1440 claro e escuro
- Prints: sistema `imagens/configuracoes-2/ModalidadesOferecidasRecusa-1440-claro-sistema.png` · canvas `imagens/configuracoes-2/ModalidadesOferecidasRecusa-1440-claro-canvas.png`
- O que difere: canvas: cartão "Modalidades oferecidas na Tijuca" é o primeiro bloco da aba Dados, caixas de marcar com "Graduação ligada" como subtítulo e aviso com ícone proibido "O Muay Thai não pode sair da Tijuca", turma ativa com nº de alunos e orientação "Desative a turma em Operação › Turmas e tente de novo". Sistema: cartão no fim da página (após Salvar), mais largo que os demais, interruptores (caixas azuis do navegador, cor fora da paleta) à direita, sem subtítulo; aviso com texto "A unidade não pode deixar de oferecer Muay Thai enquanto houver turmas ativas dela..." e lista de turmas (com contagem de alunos só se o servidor mandar). Sem MSW de unidade com modalidades ativas, a seção aparece vazia por padrão (mock).
- Padrão: caixa-nativa-azul; secao-fora-de-ordem

#### M66 · ModalidadeDesativar **[NOVO R2]**

- Lote/ID de origem: configuracoes-2 · configuracoes-2-11
- Categoria: modal/gaveta · Largura/tema: 1440 claro
- Prints: sistema `imagens/configuracoes-2/ModalidadeDesativar-1440-claro-sistema.png` · canvas `imagens/configuracoes-2/ModalidadeDesativar-1440-claro-canvas.png`
- O que difere: canvas = janela "Desativar modalidade" com aviso proibido "Não dá para desativar o Muay Thai agora", turmas ativas nomeadas por unidade, alunos ativos, orientação e botão "Entendi". Sistema: nenhuma janela; faixa rosa no topo da página com uma frase ("há 3 turma(s) ativa(s) e 63 aluno(s) ativo(s) nela"), sem nomes de turma, com "(s)" no plural (`pt-BR.json:1526`).
- Padrão: modal-bloqueio-checklist; plural-com-parenteses

#### M67 · TransferirCoordenacao **[NOVO R2]**

- Lote/ID de origem: configuracoes-2 · configuracoes-2-13
- Categoria: modal/gaveta · Largura/tema: 1440 claro
- Prints: sistema `imagens/configuracoes-2/TransferirCoordenacao-1440-claro-sistema.png` · canvas `imagens/configuracoes-2/TransferirCoordenacao-1440-claro-canvas.png`
- O que difere: canvas = título vermelho, seletor "Novo Coordenador" com administradores já listados, bloco "Resumo" (3 consequências com setas), campo "Digite TRANSFERIR", "Sua senha" e "Código de duas etapas", botão vermelho "Transferir coordenação". Sistema: aviso âmbar com o resumo em um parágrafo, campo "E-mail do novo Coordenador" digitado, campo "Digite TRANSFERIR", sem senha nem código na janela (a reautenticação é outra janela depois, `ReauthDialog`), botão "Continuar" cinza.
- Padrão: modal-sem-rodape

#### M68 · Painel da cobrança **[NOVO R2]**

- Lote/ID de origem: financeiro-1 · financeiro-1-04
- Categoria: modal/gaveta, layout, botões/ações · Largura/tema: 1440 claro
- Prints: sistema `imagens/financeiro-1/CobrancaPainel-1440-claro-sistema.png` · canvas `imagens/financeiro-1/CobrancaPainel-1440-claro-canvas.png`
- O que difere: a gaveta existe e abre pela referência (ok). Canvas tem selos no topo (tipo, situação, alerta "Pagamento em duplicidade"), título em caixa alta, aluno com avatar e "responsável financeiro", aviso vermelho com "Estornar um", Composição em tabela (Item/Valor), nota "a composição não muda depois de gerada", pagamentos em tabela (Quando/Meio/Valor/Recibo com link nº), lembretes com tipo ("3 dias antes do vencimento") e rodapé fixo de botões. Sistema é lista de pares rótulo/valor, pagamentos em caixa de texto corrido ("Lançado por portal do aluno · código abc"), sem aviso de duplicidade (RN-COB-15, Sprint 11), botões soltos abaixo do conteúdo com texto de apoio ("Só cobranças em aberto recebem pagamento."), sem rodapé fixo, sem "bloqueia em" (mostra "Tolerância 5 dias").
- Código: frontend/src/features/charges/ChargePanel.tsx:42-220
- Padrão: gaveta sem hierarquia visual; botão desabilitado com texto explicativo

#### M69 · Painel da cobrança, celular **[NOVO R2]**

- Lote/ID de origem: financeiro-1 · financeiro-1-05
- Categoria: layout · Largura/tema: 390 claro
- Prints: sistema `imagens/financeiro-1/GMCobrancaPainel-390-claro-sistema.png` · canvas `imagens/financeiro-1/GMCobrancaPainel-360-claro-canvas.png`
- O que difere: canvas é tela cheia com seta de voltar, cartões Composição e Lembretes, botões em largura total ("Cancelar cobrança" em vermelho) e "Registrar pagamento" fixo no rodapé. Sistema abre folha inferior sobre a lista, com botões pequenos em linhas quebradas e "Cancelar cobrança" sem destaque de perigo.
- Padrão: modal x gaveta

#### M70 · Cancelar cobrança **[NOVO R2]**

- Lote/ID de origem: financeiro-1 · financeiro-1-06
- Categoria: modal/gaveta, texto · Largura/tema: 1440 claro
- Prints: sistema `imagens/financeiro-1/CobrancaCancelar-1440-claro-sistema.png` · canvas `imagens/financeiro-1/CobrancaCancelar-1440-claro-canvas.png`
- O que difere: canvas: título em caixa alta, cartão "COBRANÇA" com resumo, "Motivo *", nota "A cobrança sai do portal do aluno. O relatório financeiro mostra o valor em cancelado no período...", botões "Voltar" e "Cancelar cobrança" vermelho. Sistema: linha de texto simples, aviso "não pode ser reaberta", contador 0/200, "Cancelar" e "Cancelar cobrança" cinza (desabilitado até haver motivo), sem vermelho de perigo, sem a nota do relatório.
- Padrão: modal sem cartão de resumo; botão destrutivo sem cor de perigo

#### M71 · Em conferência **[NOVO R2]**

- Lote/ID de origem: financeiro-1 · financeiro-1-09
- Categoria: layout, colunas/campos, botões/ações · Largura/tema: 1440 claro e 390 claro
- Prints: sistema `imagens/financeiro-1/EmConferencia-1440-claro-dados-sistema.png, imagens/financeiro-1/GMConferencia-390-claro-sistema.png` · canvas `imagens/financeiro-1/EmConferencia-1440-claro-canvas.png, imagens/financeiro-1/GMConferencia-360-claro-canvas.png`
- O que difere: canvas mostra o primeiro comprovante em destaque (cartão do comprovante Pix, dados do aluno, valor, enviado em, chave da unidade, prazo de bloqueio pausado, aviso "Confira no extrato do banco...", "Confirmar pagamento" e "Recusar") e abaixo "TAMBÉM AGUARDANDO" com "Conferir". Sistema é tabela única (Comprovante "Imagem indisponível" nos dados fictícios, Aluno, Unidade, Referência, Valor, Vencimento, Enviado em, Ações) com "Confirmar" e "Recusar" na linha, sem ver a imagem em tamanho de conferência antes de decidir. Texto explicativo do canvas ("Quando o Mercado Pago está desconectado... o prazo volta de onde parou") e subtítulo "N aguardando" ausentes. Celular: canvas tem cartões com pré-visualização grande e botões lado a lado; sistema mostra a imagem como texto e botão extra "Voltar às cobranças".
- Código: frontend/src/features/payment-proofs/PaymentProofsPage.tsx
- Padrão: tabela x cartão de destaque

#### M72 · Comprovante de várias cobranças **[NOVO R2]**

- Lote/ID de origem: financeiro-1 · financeiro-1-10
- Categoria: modal/gaveta · Largura/tema: 1440 claro
- Prints: sistema `imagens/financeiro-1/EmConferencia-1440-claro-dados-sistema.png` · canvas `imagens/financeiro-1/ComprovanteVarias-1440-claro-canvas.png`
- O que difere: canvas abre "Conferir comprovante" com um comprovante e duas cobranças (selo "1 comprovante · 2 cobranças"), cada uma com Confirmar/Recusar. Sistema não agrupa: uma linha por cobrança, sem janela de conferência. RN-COB-14 diz "gravado um por cobrança e decidido um a um".
- Padrão: modal x lista direta

#### M73 · Conceder benefício **[NOVO R2]**

- Lote/ID de origem: financeiro-1 · financeiro-1-13
- Categoria: modal/gaveta, campos · Largura/tema: 1440 claro
- Prints: sistema `imagens/financeiro-1/BeneficioConceder-1440-claro-sistema.png` · canvas `imagens/financeiro-1/BeneficioConceder-1440-claro-canvas.png`
- O que difere: canvas: Aluno (busca com ícone) e Modalidade (seletor) lado a lado, aviso amarelo de substituição, Tipo em dois cartões com botão de opção e descrição, Percentual com "%", Validade com ícone de calendário, Decidido por (seletor), Motivo obrigatório, caixa "RESUMO" (plano, benefício, nova mensalidade, "vale a partir de nov/2026", aviso acima de 50%) e rodapé "Lançado por Carla Mendes". Sistema: uma coluna (busca de aluno, Tipo em seletor, Percentual sem "%", "Decidido por" em texto livre, "Válido até" em data nativa dd/mm/yyyy, "Desconto de professor (opcional)" em seletor), sem Modalidade visível antes de escolher aluno, resumo só após selecionar (por `preview`), sem "Lançado por".
- Código: frontend/src/features/benefits/GrantBenefitDialog.tsx:70-363
- Padrão: campo de data nativo em inglês; seletor x cartões de opção; modal sem resumo/rodapé

#### M74 · Substituir benefício **[NOVO R2]**

- Lote/ID de origem: financeiro-1 · financeiro-1-15
- Categoria: modal/gaveta · Largura/tema: 1440 claro
- Prints: sistema `sem print (janela não existe)` · canvas `imagens/financeiro-1/BeneficioSubstituir-1440-claro-canvas.png`
- O que difere: canvas tem a janela "Substituir benefício?" com cartões "Atual" x "Novo", resumo e botões "Manter o atual" / "Substituir" (RN-BEN-08). No sistema a substituição é aviso dentro de "Conceder benefício" (`replaceExisting`, texto "será encerrado e substituído") e erro de campo se não confirmar; não há janela de confirmação nem comparação lado a lado.
- Código: frontend/src/features/benefits/GrantBenefitDialog.tsx:136, 318-320, 363
- Padrão: confirmação embutida x janela própria

#### M75 · Estado de erro **[NOVO R2]**

- Lote/ID de origem: financeiro-1 · financeiro-1-16
- Categoria: estados · Largura/tema: 1440 claro
- Prints: sistema `imagens/financeiro-1/EstadoErro-1440-claro-sistema.png` · canvas `imagens/financeiro-1/EstadoErro-1440-claro-canvas.png`
- O que difere: canvas mantém cabeçalho, abas e filtros e mostra cartão com ícone vermelho, "NÃO FOI POSSÍVEL CARREGAR AS COBRANÇAS", "A conexão caiu no meio do caminho. Nada foi alterado. Se continuar, fale com o suporte." e dois botões ("Tentar de novo" e "Falar com o suporte"). Sistema usa o estado genérico "Não foi possível carregar", três linhas de texto, só "Tentar de novo", sem cartão e sem "Falar com o suporte"; abas perdem a contagem.
- Padrão: estado genérico x específico da tela

#### M76 · Cobranças (tabela) **[NOVO R2]**

- Lote/ID de origem: financeiro-2 · F2-04
- Categoria: colunas/campos, botões/ações · Largura/tema: 1440 claro
- Prints: sistema `imagens/financeiro-2/Mensalidades-1440-claro-fx-sistema.png` · canvas `imagens/financeiro-2/Mensalidades-canvas.png`
- O que difere: canvas tem avatar colorido por aluno, referência em duas linhas ("Muay Thai · set/2026" + tipo "Mensalidade"/"Avulsa"/"Parcela"), vencimento antes da situação, situação com "25 dias" de atraso, meio com valor pago ("Pix · R$ 180,90"), menu "..." por linha, rodapé de totais ("12 de 128 cobranças", soma, "Pago R$ ... · Em aberto R$ ...") e paginação "1–12 de 128" com setas. Sistema: avatar só com iniciais sem cor, coluna "Unidade" extra, tipo não aparece, sem dias de atraso, meio só o nome, sem menu por linha (a ação abre pela referência), rodapé "128 cobranças somando R$ ..." e paginação "Página 1 de 6 / Próxima página" em texto; cabeçalho em caixa alta.
- Padrão: tabela sem avatar colorido / sem menu de linha

#### M77 · Registrar pagamento (janela) **[NOVO R2]**

- Lote/ID de origem: financeiro-2 · F2-07
- Categoria: modal/gaveta, campos · Largura/tema: 1440 claro e 390
- Prints: sistema `imagens/financeiro-2/PagamentoRegistrar-1440-claro-sistema.png, GMPagamento-390-claro-sistema.png` · canvas `imagens/financeiro-2/PagamentoRegistrar-canvas.png, GMPagamento-canvas.png`
- O que difere: canvas tem cartão "Cobrança" (aluno · modalidade · vence), "Meio" em 4 cartões de opção (Dinheiro, Pix na chave da unidade, Cartão na maquininha, Transferência), data com ícone, valor desabilitado com "Igual ao da cobrança: não existe pagamento parcial", checkbox "Enviar recibo por e-mail" com destinatário e nº do recibo, rodapé "Lançado por ..." e botão "Registrar pagamento". Sistema: abre empilhada sobre a gaveta "Cobrança"; "Valor" readonly sem explicação; "Meio de pagamento" em seletor nativo (lista inclui meios online); data nativa; contador "0 de 200 caracteres"; checkbox sem e-mail/nº do recibo; sem "Lançado por"; botão "Confirmar pagamento". No celular o canvas é tela cheia com rodapé fixo Cancelar/Registrar e aviso sobre bloqueio/lembretes; o sistema segue modal pequeno sobre a gaveta.
- Padrão: modal x tela cheia no celular; opções em cartões x seletor nativo; data nativa

#### M78 · Renegociar (janela) **[NOVO R2]**

- Lote/ID de origem: financeiro-2 · F2-08
- Categoria: modal/gaveta, campos · Largura/tema: 1440 claro e 390
- Prints: sistema `imagens/financeiro-2/Renegociar-1440-claro-sistema.png, GMRenegociar-390-claro-sistema.png` · canvas `imagens/financeiro-2/Renegociar-canvas.png, GMRenegociar-canvas.png`
- O que difere: canvas lista "Cobranças escolhidas" em tabela com total (várias), "Parcelas" + "Primeiro vencimento" com calendário, tabela de prévia das parcelas, "Motivo *" obrigatório e cartão RESUMO (originais ficam "Renegociada", parcelas contam para o bloqueio, aluno é avisado). No celular, escolha "Como" (Alterar valor e vencimento / Dividir em parcelas). Sistema: uma cobrança só, "Total a pagar" editável, vencimento nativo vazio, "Parcelas (1 a 6)" em seletor, motivo sem asterisco com contador 0/200, sem prévia das parcelas e sem resumo; botão desabilitado sem indicar o que falta.
- Padrão: modal sem prévia/resumo

#### M79 · Gerar próximas mensalidades (janela) **[NOVO R2]**

- Lote/ID de origem: financeiro-2 · F2-09
- Categoria: modal/gaveta, campos · Largura/tema: 1440 claro
- Prints: sistema `imagens/financeiro-2/GerarProximas-1440-claro-sistema.png` · canvas `imagens/financeiro-2/GerarProximas-canvas.png`
- O que difere: canvas: busca de aluno com ícone, seletor de modalidade com plano e valor, "Quantos meses" segmentado 1/2/3/6, tabela de prévia com total, nota sobre reajuste, botão "Gerar 3 mensalidades". Sistema: dois campos de texto vazios ("Buscar aluno pelo nome (mínimo 2 letras)", "Quantidade de mensalidades (1 a 12)"), sem modalidade, sem prévia, botão "Gerar mensalidades" desabilitado.
- Padrão: modal sem prévia

#### M80 · Desativar plano **[NOVO R2]**

- Lote/ID de origem: financeiro-2 · F2-16
- Categoria: modal/gaveta, texto · Largura/tema: 1440 claro
- Prints: sistema `imagens/financeiro-2/PlanoDesativar-1440-claro-sistema.png` · canvas `imagens/financeiro-2/PlanoDesativar-canvas.png`
- O que difere: canvas "Desativar plano" com subtítulo (modalidade · plano · valor), "Os 21 alunos que já usam continuam com ele", cartão RESUMO com 3 itens e botão de perigo "Desativar plano" (contorno vermelho). Sistema: "Desativar o plano Boxe 3x?" com 1 parágrafo, botão "Desativar" escuro (sem cor de perigo), sem contagem de alunos nem regras de reativação.
- Padrão: confirmação sem resumo de efeitos; ação destrutiva sem cor de perigo

#### M81 · Plano desativado (reativar) **[NOVO R2]**

- Lote/ID de origem: financeiro-2 · F2-17
- Categoria: modal/gaveta, botões/ações · Largura/tema: 1440 claro
- Prints: sistema `imagens/financeiro-2/PlanosRegras-1440-claro-sistema.png (linha "Judô Antigo")` · canvas `imagens/financeiro-2/PlanoDesativado-canvas.png`
- O que difere: canvas abre gaveta de consulta (chip "Desativado", aviso "só consulta", alunos que ainda usam, "Desativado em ... por ...", "Vale em outras unidades", tabela "Histórico de valor", botão "Reativar plano"). Sistema: sem gaveta; a linha só tem o botão "Ativar" (texto diferente de "Reativar plano") e o histórico de valor não aparece em lugar nenhum.
- Padrão: modal x gaveta

#### M82 · Planos no celular **[NOVO R2]**

- Lote/ID de origem: financeiro-2 · F2-18
- Categoria: layout · Largura/tema: 390 claro
- Prints: sistema `imagens/financeiro-2/GMPlanos-390-claro-sistema.png, GMPlanos-390-claro-resumo-sistema.png` · canvas `imagens/financeiro-2/GMPlanos-canvas.png`
- O que difere: canvas = cartões por plano (ponto + modalidade, nome, "3x por semana · 58 alunos", valor), abas "Planos 4 / Benefícios 14" e botão largo "Novo plano". Sistema: a tabela é mantida e transborda (colunas Alunos/Situação cortadas, linhas altas), "Novo plano" ao lado do título, filtros em duas linhas, barra inferior com "Cobranças" esmaecida. /planos mostra só os dois cartões-resumo.
- Padrão: tabela no celular sem versão em cartões

#### M83 · Cobranças no celular **[NOVO R2]**

- Lote/ID de origem: financeiro-2 · F2-19
- Categoria: layout, filtros · Largura/tema: 390 claro
- Prints: sistema `imagens/financeiro-2/Mensalidades-390-claro-fx-sistema.png` · canvas `sem tela de lista no lote (só GMPagamento/GMRenegociar)`
- O que difere: a lista em cartões funciona, mas o cabeçalho ocupa mais de uma tela (5 botões, busca, pílulas, 7 filtros expostos) antes da primeira cobrança; Registrar/Renegociar aparecem como janela pequena sobre a gaveta e não como tela cheia com rodapé fixo (ver F2-07, F2-08).
- Padrão: modal x tela cheia no celular

#### M84 · Agenda **[NOVO R2]**

- Lote/ID de origem: operacao-1 · OP1-04
- Categoria: texto · Largura/tema: 1440 claro
- Prints: sistema `` · canvas ``
- O que difere: subtítulo do canvas "Unidade Tijuca · 8 semanas sempre criadas a partir dos horários das turmas"; sistema "As aulas da semana da sua unidade, com o professor, a modalidade e o que foi cancelado." (sem nome da unidade nem a regra das 8 semanas). Também a barra do topo não tem o seletor de unidade ("Tijuca") nem o avatar+nome em menu (ver M1, repetido).
- Código: `frontend/src/i18n/pt-BR.json:770` (`agenda.intro`)
- Padrão: subtítulo sem contexto da unidade

#### M85 · GMAgendaProfessor **[NOVO R2]**

- Lote/ID de origem: operacao-1 · OP1-09
- Conferido (passo A): é só o mock. O back-end filtra a agenda do Professor (`backend/.../agenda/Agenda.java`, `teacher ? " and " + mine`), RN-AGE-07 e a tabela de permissões da especificação (`Agenda: ver` · Professor `T`) mandam mostrar só as turmas dele; o handler da agenda no MSW (`frontend/src/mocks/handlers.ts`) não filtra por papel.
- Categoria: botões/ações, estados · Largura/tema: 390 claro
- Prints: sistema `imagens/operacao-1/GMAgendaProfessor-390-claro-sistema.png` · canvas `imagens/operacao-1/GMAgendaProfessor-390-claro-canvas.png`
- O que difere: para prof@ o sistema mostra a mesma tela do coordenador, inclusive o botão "Nova aula avulsa" (o MSW dá `AGENDA_EDIT` a Professor? ver `mocks/permissions.ts:11`, a coluna do Professor aparece como `F` no primeiro perfil; conferir) e todas as aulas da unidade (Judô Infantil de João Pereira, que não é do professor). Canvas = só as aulas do professor, com setas do dia, título "QUINTA, 08/10", cartões com chevron e selo "Substituição", sem botão de criar, sem seletores. A barra inferior do sistema mostra "Início/Agenda/Avisos(desativado)/Menu"; canvas tem "Avisos" ativo.
- Código: `frontend/src/features/agenda/AgendaPage.tsx:41` (`canCreate` depende de permissão); conferir mock.
- Padrão: visão do Professor igual à da gestão

#### M86 · AulaEditarSeguintes **[NOVO R2]**

- Lote/ID de origem: operacao-1 · OP1-12
- Categoria: modal/gaveta, estados · Largura/tema: 1440 claro - Canvas: imagens/operacao-1/AulaEditarSeguintes-1440-claro-canvas.png · Sistema: imagens/operacao-1/AulaEditarFuturo-1440-claro-sistema.png
- Prints: sistema `` · canvas ``
- O que difere: canvas mostra, ao escolher "Esta e as seguintes", alerta amarelo "3 aulas já alteradas à mão serão atingidas", e caixa "RESUMO" com deslocamento de dias e "Salvar 22 aulas" (contagem no botão). O sistema só mostra o aviso de editadas à mão depois de escolher (`followingEdited`) e o botão continua "Salvar", sem contagem nem resumo. Texto do aviso existe em `pt-BR.json:831`. O estado "seguintes" não foi aberto no sistema com dados (MSW devolve `followingEdited` 0 nessa aula); registrado como parcial.
- Código: `frontend/src/features/agenda/EditLessonDialog.tsx` (`followingEdited` banner)
- Padrão: botão sem contagem; resumo ausente

#### M87 · AulaCorrigir **[NOVO R2]**

- Lote/ID de origem: operacao-1 · OP1-14
- Categoria: texto, modal/gaveta · Largura/tema: 1440 claro
- Prints: sistema `imagens/operacao-1/AulaCorrigir-1440-claro-sistema.png` · canvas `imagens/operacao-1/AulaCorrigir-1440-claro-canvas.png`
- O que difere: canvas = modal próprio "CORRIGIR PROFESSOR" com subtítulo, "Professor que deu a aula", "Motivo *", caixa "RESUMO" (data e horário não mudam; vira Substituição; vai para a auditoria) e nota sobre "só aulas concluídas nos últimos 30 dias, pelo Administrador ou Coordenador", botão "Salvar correção". Sistema reusa "Editar aula": nota "Aula concluída: só o professor pode ser corrigido, com motivo", campos de data/hora desabilitados (visíveis mas cinza), seletor e "Motivo da correção" (sem asterisco), botão "Salvar". Sem resumo nem regra dos 30 dias na tela.
- Código: `frontend/src/features/agenda/EditLessonDialog.tsx` (`completed`)
- Padrão: reuso de modal sem resumo

#### M88 · GMAulaPainel **[NOVO R2]**

- Lote/ID de origem: operacao-1 · OP1-16
- Categoria: layout, botões/ações · Largura/tema: 390 claro e escuro
- Prints: sistema `imagens/operacao-1/GMAulaPainel-390-claro-sistema.png, AgendaPainelFuturo-390-escuro-sistema.png` · canvas `imagens/operacao-1/GMAulaPainel-390-claro-canvas.png`
- O que difere: canvas = tela cheia com cabeçalho "Aula" + unidade, selos, título, bloco de dados em duas colunas (Professor, Titular, Alunos, Experimentais), "Histórico", botões "Avisar turma"/"Ver turma" e barra fixa "Editar" / "Cancelar aula". Sistema = mesma página do desktop empilhada, cartões em coluna única, ações em fila de botões de contorno no topo (sem barra fixa de ação); a barra de navegação inferior fica sobreposta ao conteúdo no print de página inteira. No dia 29/10 a aula está cancelada, então Editar/Cancelar não aparecem (esperado).
- Padrão: gaveta x página; barra de ação fixa

#### M89 · GMAulaCancelar / GMAulaEditar **[NOVO R2]**

- Lote/ID de origem: operacao-1 · OP1-17
- Categoria: modal/gaveta · Largura/tema: 390 claro - Canvas: imagens/operacao-1/GMAulaCancelar-390-claro-canvas.png, GMAulaEditar-390-claro-canvas.png
- Prints: sistema `` · canvas ``
- O que difere: canvas = telas cheias (cabeçalho com seta de voltar, resumo, motivos em cartões, aviso "Cancelar não avisa ninguém sozinho", rodapé fixo "Voltar / Cancelar aula"). O sistema usa a mesma janela modal centralizada do desktop (não capturada a 390; o código usa `Modal` sem variante de tela cheia). Confirmado só no código.
- Código: `frontend/src/features/agenda/CancelLessonDialog.tsx` e `EditLessonDialog.tsx` (`<Modal ...>` único)
- Padrão: modal x tela cheia no celular

#### M90 · DiasSemAula **[NOVO R2]**

- Lote/ID de origem: operacao-1 · OP1-19
- Categoria: layout, texto · Largura/tema: 1440 claro
- Prints: sistema `` · canvas ``
- O que difere: canvas subtítulo "Unidade Tijuca · a mensalidade não muda por feriado ou recesso" e nota "Feriados nacionais aparecem aqui; a unidade confirma quais viram dia sem aula"; sistema "Os dias em que a unidade não tem aula. Ao lançar um dia, as aulas dele são canceladas; ao desfazer, voltam para Agendada." Título da seção em caixa-alta condensada (canvas: "FERIADOS SUGERIDOS", "LANÇADOS") x títulos em minúsculas com fonte comum ("Feriados nacionais sugeridos", "Próximos dias sem aula"). Também a seleção de unidade existe no código (`NoClassDaysPage.tsx:51`) mas só com várias unidades.
- Padrão: subtítulo sem contexto da unidade

#### M91 · GMDiasSemAula **[NOVO R2]**

- Lote/ID de origem: operacao-1 · OP1-20
- Categoria: layout · Largura/tema: 390 claro
- Prints: sistema `imagens/operacao-1/GMDiasSemAula-390-claro-sistema.png` · canvas `imagens/operacao-1/GMDiasSemAula-390-claro-canvas.png`
- O que difere: canvas = cartão "Feriados para confirmar" com "sugestão do sistema", cartão "Lançados" com "5 aulas canceladas · avisado em 05/10" e botão de desfazer (ícone), "Novo dia sem aula" em largura total ao pé. Sistema = botão no topo ao lado do título, parágrafo longo, lista de sugestões numa caixa com altura fixa e rolagem interna (botão "Confirmar" cortado e alinhado diferente por linha: em linhas de nome longo o botão cai para baixo), cartão vazio "Nenhum dia sem aula".
- Código: `frontend/src/features/noClassDays/NoClassDaysPage.tsx:72` (`max-h-64 overflow-y-auto`)
- Padrão: rolagem interna em cartão

#### M92 · Turmas **[NOVO R2]**

- Lote/ID de origem: operacao-2 · operacao-2-02
- Categoria: layout · Largura/tema: 1440 claro - Sistema/Canvas: idem 01
- Prints: sistema `` · canvas ``
- O que difere: subtítulo genérico (canvas: "Unidade Tijuca · 5 ativas · a turma não guarda presença"). Botão "Nova turma" sem ícone "+". Nome da turma é link sublinhado com "Unidade: X" em segunda linha (canvas: negrito sem sublinhado, unidade só na barra). Modalidade sem ponto de cor. Tabela sem cartão com borda arredondada; cabeçalho em caixa-alta (canvas capitalização normal). Cabeçalho "Alunos" (canvas "Alunos ativos"); "0/30" (canvas "31 / 30"); turma sem capacidade mostra só o número (canvas "14 · sem limite"). Dias em "Seg 19:00–20:00" (canvas "Seg, Qua e Sex · 19h30–21h"). Linha desativada sem texto esmaecido. Paginação "Itens por página / Página anterior" visível (canvas não mostra).
- Padrão: tabela sem cartão / formato de horário / link sublinhado

#### M93 · GMTurmas **[NOVO R2]**

- Lote/ID de origem: operacao-2 · operacao-2-04 · gravidade revista (era alta): Dá para usar com rolagem lateral; é problema de layout.
- Categoria: layout · Largura/tema: 390 claro e escuro
- Prints: sistema `imagens/operacao-2/Turmas-390-claro-sistema.png` · canvas `imagens/operacao-2/GMTurmas-360-claro-canvas.png`
- O que difere: no celular o canvas é lista de cartões (nome, ponto de modalidade, dias, professor, ocupação "31/30") com abas em pílula ("Ativas 5", "Desativadas 1", modalidades) e "Nova turma" largo no fim; o sistema mantém a tabela de 7 colunas com rolagem horizontal (Professor, Alunos e Situação cortados, texto quebrado em 4 linhas) e 6 seletores empilhados. Ver professor e situação no celular exige rolar de lado. Barra do topo também difere (canvas: seta, "Turmas" + unidade, busca, sino).
- Código: frontend/src/features/classes/ClassesPage.tsx (tabela única, sem variante móvel)
- Padrão: tabela x lista de cartões no celular

#### M94 · TurmaDesativar **[NOVO R2]**

- Lote/ID de origem: operacao-2 · operacao-2-08
- Categoria: modal/gaveta · Largura/tema: 1440 claro
- Prints: sistema `imagens/operacao-2/TurmaDesativar-1440-claro-bloqueada-sistema.png e TurmaDesativar-1440-claro-sistema.png` · canvas `imagens/operacao-2/TurmaDesativar-canvas.png`
- O que difere: canvas "DESATIVAR TURMA" com subtítulo "Jiu-Jitsu Kids · Tijuca", texto "não pode ser reativada nem editada" e, se bloqueada, caixa rosa com LISTA dos alunos que impedem ("e mais 20 alunos") e "Troque a turma principal desses alunos e tente de novo", "Fechar" preto em rodapé cinza. Sistema: título "Desativar <nome>" em caixa mista; bloqueio só diz "22 alunos têm esta turma como principal" (sem nomes); "Fechar" em contorno; no caso livre não avisa que não se reativa nem edita.
- Código: DeactivateClassDialog.tsx:40-47 (só contagem); `ClassDetail.deactivation` só tem `blockingStudents` (useClasses.ts:75)
- Padrão: modal sem lista de impedimento

#### M95 · TurmaTitularInativo **[NOVO R2]**

- Lote/ID de origem: operacao-2 · operacao-2-09
- Categoria: modal/gaveta · Largura/tema: 1440 claro
- Prints: sistema `imagens/operacao-2/TurmaTitularInativo-1440-claro-sistema.png` · canvas `imagens/operacao-2/TurmaTitularInativo-canvas.png`
- O que difere: o comportamento existe (campo volta vazio com erro, ClassFormDialog.tsx:50-60), mas o formato difere: canvas é janela curta com subtítulo "Para salvar, troque o titular", Modalidade somente leitura com ajuda, erro "Sérgio Lopes está inativo e não pode ser titular. Escolha outro professor que ensine Jiu-Jitsu na Tijuca" e auxiliares em campo de busca. Sistema: janela longa com todos os campos, sem subtítulo, texto "não é mais um professor ativo desta unidade", modalidade editável.
- Padrão: janela de formulário em coluna única

#### M96 · GMAlunos (lista no celular) **[NOVO R2]**

- Lote/ID de origem: pessoas-1-1 · pessoas-1-1-04
- Categoria: layout · Largura/tema: 390 claro (canvas 360)
- Prints: sistema `imagens/pessoas-1-1/GMAlunos-390-claro-sistema.png` · canvas `imagens/pessoas-1-1/GMAlunos-360-claro-canvas.png`
- O que difere: canvas mostra lista em cartões (avatar, nome, "34 anos · JJ e Muay Thai", selos de situação, seta ›), cabeçalho "Alunos / Tijuca ⌄" com logo da academia, busca em destaque, abas em pílula roláveis e botão "Nova matrícula" ao lado de "Filtros". Sistema: a mesma tabela do desktop com 4 colunas cortadas na borda ("SITUAÇÃO FINANCEIRA" truncada), avatar e nome empilhados em coluna estreita (o nome quebra em duas linhas), texto introdutório e "Escolher colunas" ocupando a metade superior da tela, e a busca vazia. A barra superior tem seta de voltar + breadcrumb "Pes… > Alunos" + sino + avatar + busca + tema, sem título "Alunos / Tijuca". A barra inferior mostra "Cobranças" apagado (item indisponível, ver A1).
- Código: `frontend/src/features/students/StudentsPage.tsx:37-103` (sem variante de cartões).
- Padrão: tabela no celular em vez de cartões

#### M97 · GMAlunosFiltros (folha de filtros) **[NOVO R2]**

- Lote/ID de origem: pessoas-1-1 · pessoas-1-1-05
- Categoria: modal/gaveta · Largura/tema: 390 claro (canvas 360)
- Prints: sistema `imagens/pessoas-1-1/GMAlunos-390-claro-sistema.png (não há folha)` · canvas `imagens/pessoas-1-1/GMAlunosFiltros-360-claro-canvas.png`
- O que difere: canvas abre folha inferior "FILTROS" com Modalidade, Turma, Faixa etária, Situação financeira e Selo, botão "Ver 9 alunos" e link "Limpar filtros". Sistema mostra os 6 seletores soltos acima da lista, sem botão de filtros, sem contagem de resultado e sem "Limpar filtros".
- Código: `frontend/src/features/students/StudentFilters.tsx:40-90`.
- Padrão: filtros inline x folha de filtros no celular

#### M98 · Ficha do aluno (cabeçalho) **[NOVO R2]**

- Lote/ID de origem: pessoas-1-1 · pessoas-1-1-06
- Categoria: layout / ícone-avatar-cor · Largura/tema: 1440 claro
- Prints: sistema `imagens/pessoas-1-1/AlunoDetalhes-1440-claro-sistema.png` · canvas `imagens/pessoas-1-1/AlunoDetalhes-1440-claro-canvas.png · Diferença: imagens/pessoas-1-1/AlunoDetalhes-1440-claro-diff.png`
- O que difere: canvas: sem faixa de título; avatar grande colorido, nome em fonte de título em caixa alta, linha "34 anos · aluno desde 03/2022 · Tijuca", selos coloridos (Ativo verde, Em atraso vermelho, Bolsista azul), botão "WhatsApp" verde com ícone, "Cobrar" com ícone, botão "⋯" e fechar. Sistema: faixa "Ficha do aluno" com fechar, avatar cinza, nome em corpo comum, "36 anos" (sem "aluno desde" nem unidade), selos em contorno neutro (Ativo, Em atraso, Sem matrícula, Em dia), botão "WhatsApp" neutro com ícone de balão, "Editar dados" (no canvas "Editar dados pessoais" fica no rodapé do Resumo, ver 09); a faixa de abas tem rótulos diferentes (ver 10).
- Código: `frontend/src/features/students/StudentRecordPanel.tsx:33-66,150`.
- Padrão: selo neutro em contorno · avatar sem cor · gaveta com faixa de título

#### M99 · Ficha do aluno (ações Cobrar e menu ⋯) **[NOVO R2]**

- Lote/ID de origem: pessoas-1-1 · pessoas-1-1-07
- Categoria: botões/ações · Largura/tema: 1440 claro
- Prints: sistema `imagens/pessoas-1-1/AlunoDetalhes-1440-claro-sistema.png` · canvas `imagens/pessoas-1-1/AlunoDetalhes-1440-claro-canvas.png`
- O que difere: canvas tem "Cobrar" e o menu "⋯" no cabeçalho; sistema não tem nenhum dos dois (só WhatsApp e Editar dados). "Nova cobrança avulsa" do aluno não é alcançável da ficha; só em /cobrancas.
- Código: `frontend/src/features/students/StudentRecordPanel.tsx:33-66` (sem esses botões); i18n sem "Cobrar" em `students`.
- Padrão: ação rápida do cabeçalho ausente

#### M100 · Ficha do aluno, aba Resumo (blocos) **[NOVO R2]**

- Lote/ID de origem: pessoas-1-1 · pessoas-1-1-08
- Categoria: colunas/campos / botões/ações · Largura/tema: 1440 claro
- Prints: sistema `imagens/pessoas-1-1/AlunoDetalhes-1440-claro-sistema.png` · canvas `imagens/pessoas-1-1/AlunoDetalhes-1440-claro-canvas.png · Diferença: imagens/pessoas-1-1/AlunoDetalhes-1440-claro-diff.png`
- O que difere: canvas: seção "Pendências" (alerta vermelho "Muay Thai bloqueia em 10/10/2026 se a mensalidade de set/2026 ... não for paga" + "Ver cobranças"; depende da Sprint 12, #109), tabela "Modalidades" (Modalidade com bolinha, Turma, Faixa desenhada, Situação "Ativa"; a faixa depende da Sprint 12), seção "Contato" em grade de 2 colunas com rótulo pequeno e valor (Celular, E-mail, Endereço, Contato de emergência "Larissa Carvalho (esposa) · (21) ...", Próximo vencimento "10/10/2026 · R$ 340,00 (2 modalidades)", Termo de matrícula "Aceito em 03/03/2022 · versão 2") e linha final de ações "Editar dados pessoais", "Baixar dados do aluno" e "Anonimizar aluno" (#117, #120, #121, Sprint 13). Sistema: 3 linhas soltas (Unidade de cadastro, Faixa etária, Nascimento), cartões "Modalidades" (lista de texto), "Contato" (Celular, E-mail), "Responsáveis" ("Este aluno não tem responsável vinculado."), "Emergência" (Nome, Parentesco, Telefone); sem Endereço mesmo com o dado existindo em `record.address`, sem Próximo vencimento, sem Termo de matrícula e sem a linha de ações.
- Código: `frontend/src/features/students/RecordSummary.tsx:25-72` (nenhuma referência a `address`; o tipo `StudentRecord.address` existe em `types.ts`).
- Padrão: ficha em cartões empilhados x grade rótulo/valor

#### M101 · Ficha, aba Matrícula e modalidades **[NOVO R2]**

- Lote/ID de origem: pessoas-1-1 · pessoas-1-1-10
- Categoria: colunas/campos / botões/ações / layout · Largura/tema: 1440 claro
- Prints: sistema `imagens/pessoas-1-1/AlunoMatricula-1440-claro-sistema.png` · canvas `imagens/pessoas-1-1/AlunoMatricula-1440-claro-canvas.png · Diferença: imagens/pessoas-1-1/AlunoMatricula-1440-claro-diff.png`
- O que difere: canvas: linha "Uma matrícula na organização · até 10 modalidades ativas" com botão "Adicionar modalidade" (+) à direita; cada modalidade em cartão com selo "Ativa" verde, grade 3 colunas (Unidade, Turma principal "Adulto · Noite (Seg, Qua e Sex 19h30)", Outros horários; Plano com valor "Adulto 3x · R$ 180,00", Benefício, Mensalidade; Vencimento), alerta vermelho de bloqueio por modalidade (Sprint 12) e ordem dos botões "Trocar turma, Trocar plano, Conceder benefício, Transferir, Encerrar" com Encerrar em contorno vermelho. Sistema: sem a linha de limite nem "Adicionar modalidade" (para aluno Ativo o link "Nova matrícula" só aparece na lista; na ficha só com matrícula vazia ou Ex-aluno; alcançável pelo assistente de Nova matrícula), cartão com 4 linhas rótulo/valor empilhadas (Unidade, Turma, Plano sem valor, Vencimento), sem Benefício nem Mensalidade, turma sem os dias e horários, "Outros horários: ..." em linha solta, selo "Ativa" em contorno, botões grandes de contorno neutro na ordem Trocar turma, Trocar plano, Transferir, Encerrar, Conceder benefício (Encerrar sem destaque de perigo).
- Código: `frontend/src/features/students/RecordEnrollment.tsx:28-62,87,102`.
- Padrão: cartão de modalidade sem grade · botão destrutivo sem cor de perigo

#### M102 · Ficha, aba Matrícula (bloco "Situação do aluno") **[NOVO R2]**

- Lote/ID de origem: pessoas-1-1 · pessoas-1-1-11
- Categoria: botões/ações · Largura/tema: 1440 claro
- Prints: sistema `imagens/pessoas-1-1/AlunoMatricula-1440-claro-sistema.png` · canvas `imagens/pessoas-1-1/AlunoMatricula-1440-claro-canvas.png`
- O que difere: canvas fecha a aba com o cartão "Situação do aluno" (texto "Ativo enquanto houver ao menos uma modalidade Ativa...", botões "Inativar", "Reativar" e link "Cancelar cobranças acumuladas"). Sistema não tem o bloco. É da Sprint 12 (#110 RF-INA-02, aberta); anoto aqui porque a ficha é construída, mas as janelas ficam em "Sem comparar".
- Código: sem `students.record.enrollment` para inativar/reativar em `pt-BR.json`; `gh issue view 110`.
- Padrão: ação de sprint futura ausente

#### M103 · Ficha, aba Financeiro **[NOVO R2]**

- Lote/ID de origem: pessoas-1-1 · pessoas-1-1-12
- Categoria: colunas/campos / botões/ações · Largura/tema: 1440 claro
- Prints: sistema `imagens/pessoas-1-1/AlunoFinanceiro-1440-claro-sistema.png` · canvas `imagens/pessoas-1-1/AlunoFinanceiro-1440-claro-canvas.png · Diferença: imagens/pessoas-1-1/AlunoFinanceiro-1440-claro-diff.png`
- O que difere: canvas: 3 cartões de resumo (Em aberto R$ 500,00 · "3 cobranças"; Em atraso R$ 160,00 · "1 cobrança · 25 dias"; Pago em 2026 R$ 2.940,00 · "17 pagamentos"), nota "As taxas do meio de pagamento são somadas quando o aluno paga online.", botão "Nova cobrança avulsa" e tabela Referência / Vencimento / Valor / Situação (selos coloridos, inclusive "Paga com atraso") com ação por linha (Registrar pagamento, Abrir, Recibo). Sistema: lista de cartões clicáveis "Muay Thai · 09/2026 — Vence em 10/09/2026 · R$ 160,00" com selo à direita, sem cartões de resumo, sem nota, sem "Nova cobrança avulsa" e sem ações por linha (o cartão inteiro abre o painel da cobrança, onde ficam as ações).
- Código: `frontend/src/features/students/StudentFinancialTab.tsx:13-60`.
- Padrão: lista de cartões x tabela com ações · faltam cartões-resumo

#### M104 · Ficha, aba Histórico **[NOVO R2]**

- Lote/ID de origem: pessoas-1-1 · pessoas-1-1-13
- Categoria: colunas/campos / filtros/abas/busca · Largura/tema: 1440 claro
- Prints: sistema `imagens/pessoas-1-1/AlunoHistorico-1440-claro-sistema.png` · canvas `imagens/pessoas-1-1/AlunoHistorico-1440-claro-canvas.png · Diferença: imagens/pessoas-1-1/AlunoHistorico-1440-claro-diff.png`
- O que difere: canvas: filtros "Área Todas" e "Período Últimos 12 meses", link "Ver na auditoria" e tabela Quando (data e hora) / Quem ("Carla Mendes · Administradora") / O que aconteceu / Área (Financeiro, Alunos). Sistema: lista de cartões com a ação, "data · por Fulano" (sem o perfil de quem agiu), sem Área, sem filtros, sem link para a auditoria; paginação por botões Anterior/Próxima.
- Código: `frontend/src/features/students/RecordHistory.tsx:11-50`.
- Padrão: lista de cartões x tabela · sem filtros na ficha

#### M105 · Ficha, aba Responsáveis **[NOVO R2]**

- Lote/ID de origem: pessoas-1-1 · pessoas-1-1-14
- Categoria: colunas/campos / botões/ações · Largura/tema: 1440 claro
- Prints: sistema `imagens/pessoas-1-1/AlunoResponsaveis-1440-claro-sistema.png (aluno menor do MSW)` · canvas `imagens/pessoas-1-1/AlunoResponsaveis-1440-claro-canvas.png · Diferença: imagens/pessoas-1-1/AlunoResponsaveis-1440-claro-diff.png`
- O que difere: canvas: aviso azul "Davi tem 9 anos. Só os responsáveis entram no portal dele. Até 2 responsáveis, um deles o financeiro."; um cartão por responsável com avatar colorido, nome, "Mãe · (21) ...", selo "Responsável financeiro", CPF mascarado, situação do portal ("Acessou em 02/10/2026", "Convite enviado em 01/10 · vale até 08/10") e ações "Reenviar convite", "Tornar financeiro", "Desvincular" em estilos diferentes; "Vincular responsável" desabilitado com "Limite de 2 responsáveis atingido" ao lado. Sistema: sem aviso; cartão simples com nome, "telefone · e-mail" (sem parentesco nem CPF), selos "Financeiro" e "Acesso pendente" em contorno, botões "Reenviar convite" e "Desvincular" de contorno; "Vincular responsável" é botão preto primário sozinho, sem informar o limite. Observação: o MSW tem um só responsável, que já é financeiro, então "Tornar financeiro" não foi exibido (existe em `pt-BR.json:572`).
- Código: `frontend/src/features/students/RecordGuardians.tsx:36-109`.
- Padrão: cartão de pessoa sem avatar · selos neutros · aviso informativo ausente

#### M106 · Editar dados pessoais (desktop) **[NOVO R2]**

- Lote/ID de origem: pessoas-1-1 · pessoas-1-1-15
- Categoria: modal/gaveta / layout / colunas/campos · Largura/tema: 1440 claro
- Prints: sistema `imagens/pessoas-1-1/AlunoEditar-1440-claro-sistema.png` · canvas `imagens/pessoas-1-1/AlunoEditar-1440-claro-canvas.png · Diferença: imagens/pessoas-1-1/AlunoEditar-1440-claro-diff.png`
- O que difere: canvas: janela central larga ("EDITAR DADOS PESSOAIS"), campos em 2 a 4 colunas (Nome completo | Data de nascimento; CPF | Celular | E-mail; CEP | Endereço | Complemento; Bairro | Cidade | UF), asteriscos nos obrigatórios, subtítulo "Opcionais" (Sexo ou gênero, Como conheceu, Contato de emergência | Parentesco | Telefone), nota "Não guardamos dados de saúde. Atestados ficam fora do sistema.", rodapé "Última alteração em 12/09/2026 às 18h40 por Bruno Carvalho (portal)" e botões Cancelar / Salvar. Sistema: janela estreita (~510 px) com uma só coluna, mais de 1000 px de altura, rola; traz bloco "Foto" ("Escolher foto", que o canvas não mostra), "Nascimento" com campo de data nativo (calendário à direita, formato do navegador), CEP, Logradouro, Número, Complemento, Bairro, Cidade, UF em linhas separadas, sem asterisco, sem nota de saúde e sem "Última alteração" (o componente `LastChange` não é usado aqui); rótulos "Nome" e "Nascimento" (canvas: "Nome completo" e "Data de nascimento"); botão Salvar do rodapé fora da vista sem rolar.
- Código: `frontend/src/features/students/StudentEditDialog.tsx:47-380`.
- Padrão: modal em coluna única · campo de data nativo · rodapé "Última alteração" ausente

#### M107 · Editar dados (celular) **[NOVO R2]**

- Lote/ID de origem: pessoas-1-1 · pessoas-1-1-16
- Categoria: modal/gaveta · Largura/tema: 390 claro (canvas 360)
- Prints: sistema `imagens/pessoas-1-1/GMAlunoEditar-390-claro-sistema.png` · canvas `imagens/pessoas-1-1/GMAlunoEditar-360-claro-canvas.png`
- O que difere: canvas é tela cheia, com seta de voltar, título "Editar dados" e unidade na barra, 7 campos (Nome completo, CPF, Nascimento, Celular, E-mail, Endereço em um campo, Contato de emergência em um campo), "Última alteração..." e barra fixa Cancelar | Salvar. Sistema: modal em cartão arredondado sobre a lista (a margem mostra a lista atrás), título "Editar dados do aluno" com ✕, foto + 12 campos em uma coluna longa, rodapé que só se alcança rolando, sem barra fixa.
- Código: `frontend/src/features/students/StudentEditDialog.tsx:47`; `frontend/src/components/Modal.tsx`.
- Padrão: modal x tela cheia no celular

#### M108 · Encerrar modalidade (desktop) **[NOVO R2]**

- Lote/ID de origem: pessoas-1-1 · pessoas-1-1-17
- Categoria: modal/gaveta / texto · Largura/tema: 1440 claro
- Prints: sistema `imagens/pessoas-1-1/EncerrarModalidade-1440-claro-sistema.png` · canvas `imagens/pessoas-1-1/EncerrarModalidade-1440-claro-canvas.png · Diferença: imagens/pessoas-1-1/EncerrarModalidade-1440-claro-diff.png`
- O que difere: canvas: título com o nome ("ENCERRAR MUAY THAI"), Motivo já escolhido, tabela de cobranças em aberto (Cobrança "Muay Thai · set/2026 · vencida há 25 dias" | Valor | alternador segmentado Manter/Cancelar), campo "Motivo do cancelamento *", bloco "RESUMO" com 4 linhas de efeito ("Muay Thai fica Encerrada hoje...", "Mantida: ... Cancelada: ...", "Jiu-Jitsu continua Ativa...", "Bruno recebe um e-mail...") e botão vermelho "Encerrar modalidade" / link "Voltar". Sistema: título genérico "Encerrar modalidade", parágrafo de efeito, linha "Bruno Carvalho · Jiu-Jitsu (Tijuca)", cobranças em `fieldset` com legenda e botões de opção nativos Manter/Cancelar, Motivo "Escolha" (select nativo), Observação com contador 0/500, sem bloco de resumo, botão "Encerrar modalidade" cinza (desabilitado) e "Cancelar" em vez de "Voltar". A caixa é mais estreita (~510 px, canvas ~640 px).
- Código: `frontend/src/features/students/EndModalityDialog.tsx:30-120`.
- Padrão: botão de opção nativo x alternador segmentado · resumo de efeitos ausente

#### M109 · Ficha do aluno no celular **[NOVO R2]**

- Lote/ID de origem: pessoas-1-2 · L1-06 · gravidade revista (era alta): Ficha abre e funciona a 390 px; problema de layout.
- Categoria: layout · Largura/tema: 390 claro
- Prints: sistema `imagens/pessoas-1-2/GMFicha-390-claro-sistema.png (também FichaFinanceiro-390 e FichaResponsaveis-390)` · canvas `imagens/pessoas-1-2/GMFicha-360-claro-canvas.png`
- O que difere: o nome quebra uma sílaba por linha ("Alu/no/Adu/lto/Fict/ício") porque os botões ficam na mesma linha do nome; os chips quebram ("Em dia", "Sem matrícula"); as abas quebram em duas linhas em vez de rolar; a lista de alunos aparece abaixo do painel. Canvas empilha os botões e rola as abas. Tela quase ilegível a 390 px.
- Código: `frontend/src/features/students/StudentRecordPanel.tsx:80` (abas com `flex-wrap`) e `Header` (linha ~123).
- Padrão: cabeçalho que não empilha no celular

#### M110 · Ficha, aba Matrícula **[NOVO R2]**

- Lote/ID de origem: pessoas-1-2 · L1-07
- Categoria: colunas/campos/botões · Largura/tema: 390 e 1440 claro
- Prints: sistema `imagens/pessoas-1-2/FichaMatricula-390-claro-sistema.png` · canvas `imagens/pessoas-1-2/GMFichaMatricula-360-claro-canvas.png`
- O que difere: canvas: por modalidade, grade Turma/Plano (com valor)/Vencimento/Graduação, chip "Ativa" ou "Bloqueia 10/10", ações "Trocar turma", "Trocar plano", "Encerrar" (vermelho) e "Adicionar modalidade" no fim; no desktop também "Outros horários", "Benefício", "Mensalidade" e "Adicionar modalidade" no topo. Sistema: lista rótulo-valor (Unidade, Turma, Plano sem valor, Vencimento), sem Graduação, "Transferir" e "Conceder benefício" ao lado das demais, "Encerrar" sem destaque, sem "Adicionar modalidade".
- Padrão: ação destrutiva sem destaque

#### M111 · Ficha, aba Financeiro **[NOVO R2]**

- Lote/ID de origem: pessoas-1-2 · L1-08
- Categoria: colunas/campos/botões · Largura/tema: 390 claro
- Prints: sistema `imagens/pessoas-1-2/FichaFinanceiro-390-claro-sistema.png` · canvas `imagens/pessoas-1-2/GMFichaFinanceiro-360-claro-canvas.png`
- O que difere: canvas separa "Em aberto" (valor em destaque, chip Em atraso/Pendente, "venceu 10/09 · 28 dias", botão "Registrar pagamento") e "Pagos" (com botão de baixar recibo, "Pix · 09/09"). Sistema: lista única de cartões "Vence em dd/mm/aaaa · R$ ...", sem agrupamento, sem "Registrar pagamento" na aba nem baixar recibo.
- Padrão: lista plana x agrupada

#### M112 · Ficha, aba Responsáveis **[NOVO R2]**

- Lote/ID de origem: pessoas-1-2 · L1-09
- Categoria: colunas/campos/botões · Largura/tema: 390 claro
- Prints: sistema `imagens/pessoas-1-2/FichaResponsaveis-390-claro-sistema.png (adulto sem responsável)` · canvas `imagens/pessoas-1-2/GMFichaResponsaveis-360-claro-canvas.png`
- O que difere: canvas: cartão por responsável (avatar, parentesco · telefone, chip Financeiro/Com acesso, "WhatsApp", "Desligar"), "Vincular responsável" em largura total e a regra "Até dois responsáveis com acesso...". Sistema (só vazio): "Nenhum responsável vinculado." e botão preto. Cartão cheio não verificado (ver Sem comparar); `RecordGuardians.tsx` não foi lido linha a linha.
- Padrão: cartão de pessoa sem avatar/chips

#### M113 · Ficha, aba Graduações **[NOVO R2]**

- Lote/ID de origem: pessoas-1-2 · L1-10
- Categoria: estados · Largura/tema: 1440 claro
- Prints: sistema `` · canvas ``
- O que difere: a aba existe mas só exibe estado vazio (`LATER = ["graduations"]` em `frontend/src/features/students/StudentRecordPanel.tsx:21`); o canvas já mostra graduação no cartão ("Roxa · 1 grau"). Provável dependência de sprint futura (graduações).
- Padrão: aba adiada

#### M114 · Assistente de matrícula, estrutura **[NOVO R2]**

- Lote/ID de origem: pessoas-1-2 · L1-11 · gravidade revista (era alta): Fluxo funciona; diferença de estrutura.
- Categoria: layout/texto · Largura/tema: 1440 claro e 390
- Prints: sistema `imagens/pessoas-1-2/MatriculaAluno-1440-claro-sistema.png, Wiz1440-e1..e5-sistema.png` · canvas `MatriculaAluno, MatriculaModalidades, MatriculaVencimento, MatriculaResumo, MatriculaConcluida (-1440-claro-canvas.png)`
- O que difere: canvas: subtítulo "Unidade Tijuca · o aluno fica Ativo ao concluir", migalha "Pessoas > Alunos > Nova matrícula", cartão único com passos em círculo numerado (check nos concluídos), coluna lateral "Até aqui" (aluno, responsável financeira, unidade) nas etapas 2 e 3, Voltar/Continuar com seta. Sistema: sem cartão, passos em pílulas de texto com "Etapa N de 5", linha "Aluno: ..." em texto, sem coluna "Até aqui", botões "Voltar"/"Avançar" sem ícone, migalha só "Pessoas > Alunos".
- Padrão: passos em pílula x círculos; falta resumo lateral

#### M115 · Etapa 1: Aluno **[NOVO R2]**

- Lote/ID de origem: pessoas-1-2 · L1-12 · gravidade revista (era alta): Fluxo funciona; busca por CPF primeiro pode ser intencional; faltam campos (foto, "Como conheceu", emergência). Conferir a especificação.
- Categoria: colunas/campos · Largura/tema: 1440 e 390 claro
- Prints: sistema `imagens/pessoas-1-2/MatriculaAluno-1440-claro-sistema.png, Wiz390-e1-sistema.png` · canvas `imagens/pessoas-1-2/MatriculaAluno-1440-claro-canvas.png, GMMatricula1-360-claro-canvas.png`
- O que difere: canvas já mostra o cadastro (CPF opcional para menor, nome, nascimento com "10 anos · faixa etária Infantil", aviso de duplicidade, CEP e endereço, "Responsáveis" com cartão e "Adicionar segundo responsável", foto, "Como conheceu", contato de emergência, Cancelar/Continuar). Sistema só mostra "CPF do aluno" + "Buscar"; o formulário só abre após buscar um CPF novo e não tem foto, "Como conheceu" nem emergência; aluno achado vira cartão com inicial, "Matrícula nova" e "É este aluno, continuar". No celular o canvas tem barra fixa com "Continuar" e aviso "Menor de idade: vincule ao menos um responsável".
- Código: `frontend/src/features/students/enrollment-wizard/StepStudent.tsx` (`StepStudent`: busca por CPF primeiro; `NewStudentForm`).
- Padrão: formulário completo x busca por CPF primeiro

#### M116 · Etapa 2: Modalidades **[NOVO R2]**

- Lote/ID de origem: pessoas-1-2 · L1-13
- Categoria: colunas/campos/estados · Largura/tema: 1440 e 390 claro
- Prints: sistema `imagens/pessoas-1-2/Wiz1440-e2-sistema.png, Wiz390-e2-sistema.png` · canvas `imagens/pessoas-1-2/MatriculaModalidades-1440-claro-canvas.png, GMMatricula2-360-claro-canvas.png`
- O que difere: canvas: cartão com ponto de cor e nome da modalidade, lixeira, Unidade/Turma/Outros horários em linha, banner âmbar "Turma acima da capacidade... 26 de 25. Você pode seguir", Plano/Benefício/Graduação de origem em selects, "Mensalidade: R$ 150,00 − 10% = R$ 135,00", "Adicionar outra modalidade" com dica. Sistema: cartão "Modalidade 1" com "Remover" em texto, campos empilhados com "Outros horários" em caixas de marcar, aviso de capacidade como texto em negrito sem banner, benefício em bloco com caixa, sem cálculo de mensalidade. No celular a barra inferior do app cobre o fim do formulário e não há barra fixa Voltar/Continuar.
- Padrão: aviso em texto x banner; rodapé fixo ausente no celular

#### M117 · Etapa 3: Vencimento **[NOVO R2]**

- Lote/ID de origem: pessoas-1-2 · L1-14
- Categoria: colunas/campos · Largura/tema: 1440 claro
- Prints: sistema `imagens/pessoas-1-2/Wiz1440-e3-sistema.png` · canvas `imagens/pessoas-1-2/MatriculaVencimento-1440-claro-canvas.png`
- O que difere: canvas: seletor segmentado "Dia 1 / 5 / 10 / 15", cartão "Primeira cobrança" com tabela (Cobrança, Vencimento, Valor, taxa com selo "Isenta" e valor riscado, Total), interruptor "Isentar a taxa de matrícula" com "Motivo da isenção", cartão "Segunda cobrança" com data sugerida. Sistema: select "Dia de vencimento", texto "Taxa de matrícula: R$ 80,00" com caixa "Isentar taxa", campo de data nativo "dd/mm/yyyy" (inglês), sem tabela da primeira cobrança nem total.
- Padrão: campo de data nativo em inglês

#### M118 · Etapa 4: Resumo **[NOVO R2]**

- Lote/ID de origem: pessoas-1-2 · L1-15
- Categoria: layout/colunas · Largura/tema: 1440 e 390 claro
- Prints: sistema `imagens/pessoas-1-2/Wiz1440-e4-sistema.png, Wiz390-e4-sistema.png` · canvas `imagens/pessoas-1-2/MatriculaResumo-1440-claro-canvas.png, GMMatricula4-360-claro-canvas.png`
- O que difere: canvas: cartões "Aluno" (nome, nascimento, endereço, responsável) e "Modalidade" (turma, plano, benefício, graduação), tabela de cobranças com coluna "Por meio, com a taxa", cartão "Termo de matrícula", botão terracota "Concluir matrícula" com check; no celular, "Pagamento" com "Registrar pagamento agora"/"Responsável paga pelo portal". Sistema: blocos "Modalidades" e "Por unidade" em texto corrido, sem dados do aluno/responsável, sem meio de pagamento, sem aviso do termo, botão preto.
- Padrão: resumo em texto x cartões/tabela

#### M119 · Etapa 5: Conclusão **[NOVO R2]**

- Lote/ID de origem: pessoas-1-2 · L1-16
- Categoria: layout/texto · Largura/tema: 1440 claro
- Prints: sistema `imagens/pessoas-1-2/Wiz1440-e5-sistema.png` · canvas `imagens/pessoas-1-2/MatriculaConcluida-1440-claro-canvas.png`
- O que difere: canvas: ícone de sucesso, "THEO ESTÁ MATRICULADO", frase com turma e convite enviado (vale 7 dias), dois cartões "Registrar pagamento agora" e "Aluno paga pelo portal" (com valor), link "Abrir ficha de Theo". Sistema: título "Matrícula de ... concluída" sem ícone, "Aluno Ativo", "Sem convite: o aluno não tem e-mail cadastrado.", lista "Cobranças criadas" com "Registrar pagamento agora" e botão preto "Aluno paga pelo portal"; sem link para a ficha.
- Padrão: conclusão sem destaque

#### M120 · Janela Trocar turma **[NOVO R2]**

- Lote/ID de origem: pessoas-1-2 · L1-17
- Categoria: modal/gaveta · Largura/tema: 1440 claro
- Prints: sistema `imagens/pessoas-1-2/TrocarTurma-1440-claro-sistema.png` · canvas `imagens/pessoas-1-2/TrocarTurma-1440-claro-canvas.png`
- O que difere: canvas: título "TROCAR TURMA" em caixa alta, "Modalidade" e "Turma principal atual" somente leitura, "Nova turma principal" com ocupação ("18 de 25"), "Outros horários" com legenda, bloco "RESUMO", "Motivo (opcional)". Sistema: título "Trocar turma de Jiu-Jitsu", "A troca vale na hora.", só Turma principal e Outros horários, sem resumo nem motivo.
- Padrão: janela sem resumo/motivo

#### M121 · Janela Trocar plano **[NOVO R2]**

- Lote/ID de origem: pessoas-1-2 · L1-18
- Categoria: modal/gaveta · Largura/tema: 1440 claro
- Prints: sistema `imagens/pessoas-1-2/TrocarPlano-1440-claro-sistema.png` · canvas `imagens/pessoas-1-2/TrocarPlano-1440-claro-canvas.png`
- O que difere: canvas: planos em cartões com botão de opção (nome, valor, descrição), resumo (plano atual, novo, vigência "a partir da próxima cobrança: nov/2026", valores por meio) e "Motivo *". Sistema: "Plano atual: Adulto 3x" + select "Plano novo", botão desabilitado, sem resumo nem motivo.
- Padrão: janela sem resumo/motivo; select x cartões

#### M122 · Janela Transferir **[NOVO R2]**

- Lote/ID de origem: pessoas-1-2 · L1-19
- Categoria: modal/gaveta · Largura/tema: 1440 claro
- Prints: sistema `imagens/pessoas-1-2/TransferirModalidade-1440-claro-sistema.png` · canvas `imagens/pessoas-1-2/TransferirModalidade-1440-claro-canvas.png`
- O que difere: canvas "TRANSFERIR MODALIDADE": Modalidade/De/Para, Turma e Plano na unidade nova, Dia de vencimento + "A partir de", resumo (mensalidade, cobrança que fica na origem, administrador avisado), "Motivo *". Sistema: "Transferir Jiu-Jitsu para outra unidade" com só "Unidade de destino" e botão desabilitado; campos seguintes não verificados.
- Padrão: janela sem resumo/motivo

#### M124 · ProfessorNovo (janela) **[NOVO R2]**

- Lote/ID de origem: pessoas-2 · P2-03
- Categoria: colunas/campos, modal/gaveta · Largura/tema: 1440 claro
- Prints: sistema `imagens/pessoas-2/ProfessorNovo-1440-claro-sistema.png` · canvas `imagens/pessoas-2/ProfessorNovo-1440-claro-canvas.png`
- O que difere: canvas é janela larga em 3 colunas, título em caixa alta, nome/e-mail/celular obrigatórios com asterisco e ajuda "Recebe o convite de acesso (vale 7 dias)", modalidades e unidades como chips, "Graduação · <modalidade>" (select), "Foto" com "Enviar foto", apresentação com contador "86 de 300", dois interruptores (Aceita aparecer no diretório; Dar acesso à gestão como Professor) e botão "Salvar e enviar convite". Sistema: janela estreita de 1 coluna longa (cabe só rolando), título em caixa baixa, foto com círculo vazio e "Escolher foto", campos sem asterisco, unidades e modalidades como caixas de marcação nativas (uma lista de 3 modalidades), sem Graduação (só aparece com `graduationEnabled`, ausente no MSW), sem interruptor de acesso (acesso é outra ação, "Dar acesso", no painel), "Aparecer no diretório" e "O professor autorizou" como caixas, botão "Salvar". Sem o texto "Não pedimos CPF nem endereço de professores".
- Código: `frontend/src/features/teachers/TeacherFormDialog.tsx:107` (graduação condicional), `:244` (fieldset de modalidades)
- Padrão: caixa de marcação nativa x chip; janela de formulário em 1 coluna; título de janela em caixa baixa

#### M125 · ProfessorInativar (janela) **[NOVO R2]**

- Lote/ID de origem: pessoas-2 · P2-05
- Categoria: modal/gaveta, campos · Largura/tema: 1440 claro
- Prints: sistema `imagens/pessoas-2/ProfessorInativar-1440-claro-fx-sistema.png (e -sistema.png sem fixture)` · canvas `imagens/pessoas-2/ProfessorInativar-1440-claro-canvas.png`
- O que difere: canvas "INATIVAR THIAGO MOREIRA": aviso âmbar "2 turmas ficam sem titular…", tabela Turma/Papel/Novo titular com seletor por turma ("Escolher depois"), linha de Auxiliar "sai da turma", campo "Motivo *" e botão vermelho "Inativar professor". Sistema: apenas texto "Estas turmas ficarão sem titular:" com lista simples (inclusive as de auxiliar, misturadas), sem escolha de novo titular, sem Motivo, sem aviso colorido, botão "Inativar" preto (não vermelho/perigo). Sem a frase "O acesso dele é desligado agora. O histórico…".
- Código: `frontend/src/features/teachers/DeactivateTeacherDialog.tsx:50-62`
- Padrão: confirmação destrutiva sem botão vermelho nem resumo

#### M126 · ProfessorModalidadeRecusa (janela) **[NOVO R2]**

- Lote/ID de origem: pessoas-2 · P2-06
- Categoria: modal/gaveta, estados · Largura/tema: 1440 claro
- Prints: sistema `imagens/pessoas-2/ProfessorModalidadeRecusa-1440-claro-fx-sistema.png` · canvas `imagens/pessoas-2/ProfessorModalidadeRecusa-1440-claro-canvas.png`
- O que difere: canvas bloqueia na hora (aviso rosa "O Jiu-Jitsu não pode sair do Thiago" com itens Titular/Auxiliar expansíveis, botão Salvar desabilitado) e mostra a graduação sob cada modalidade marcada. Sistema só reage depois de clicar em Salvar (409): texto vermelho solto abaixo da lista, sem caixa, o formulário completo continua (não é janela "Editar professor" enxuta) e não indica papel (Titular/Auxiliar) das turmas.
- Código: `frontend/src/features/teachers/TeacherFormDialog.tsx:157-159`, `:293-305`
- Padrão: erro de regra em texto solto x aviso em caixa

#### M127 · PedidoRecusar / PedidoEncerramentoRecusar (janela) **[NOVO R2]**

- Lote/ID de origem: pessoas-2 · P2-08
- Categoria: modal/gaveta, texto, botões · Largura/tema: 1440 claro
- Prints: sistema `imagens/pessoas-2/PedidoRecusar-1440-claro-sistema.png, PedidoEncerramentoRecusar-1440-claro-sistema.png` · canvas `imagens/pessoas-2/PedidoRecusar-1440-claro-canvas.png, PedidoEncerramentoRecusar-1440-claro-canvas.png`
- O que difere: canvas tem título em caixa alta e distinto por tipo ("RECUSAR PEDIDO" / "RECUSAR ENCERRAMENTO" com subtítulo "Olívia Ramos · Muay Thai · pedido de 07/10 · encerra sozinho em 15/10"), caixa "RESUMO" com marcadores (o que acontece: modalidade volta a "Ativa", aluno recebe a mensagem, pedido fica "Recusado"), campo "Mensagem para a Olívia *" com texto sugerido e contador "164 de 500", botão vermelho "Recusar pedido" / "Recusar e enviar mensagem" e "Voltar". Sistema: mesmo título "Recusar pedido" também para encerramento, texto corrido de efeito, linha "Aluno · resumo", "Mensagem do aluno: …", rótulo "Mensagem ao aluno" sem asterisco, contador "0/500", botão "Recusar pedido" cinza (desabilitado) sem tom de perigo; texto genérico "No pedido de encerramento, a modalidade volta a ficar ativa" mesmo em pedido de troca de turma.
- Código: `frontend/src/features/portal-requests/RefuseRequestDialog.tsx:21-60`
- Padrão: título de janela em caixa baixa; caixa de resumo ausente; botão de perigo sem vermelho

#### M128 · GMPedidoRecusar **[NOVO R2]**

- Lote/ID de origem: pessoas-2 · P2-09
- Categoria: modal/gaveta, layout · Largura/tema: 390 claro
- Prints: sistema `imagens/pessoas-2/GMPedidoRecusar-390-claro-sistema.png` · canvas `imagens/pessoas-2/GMPedidoRecusar-360-claro-canvas.png`
- O que difere: canvas é folha que sobe da base (alça, "RECUSAR ENCERRAMENTO", campo, nota "O Muay Thai volta a "Ativa"…", botão cheio "Recusar e enviar" e "Voltar" em texto); sistema é janela central arredondada com Cancelar/Recusar lado a lado e botão cinza.
- Código: `frontend/src/components/Modal.tsx` / `ModalityDialog` (sem variante de folha no celular)
- Padrão: janela x folha inferior no celular

#### M129 · PedidoAprovadoFluxo (janela) **[NOVO R2]**

- Lote/ID de origem: pessoas-2 · P2-10
- Categoria: modal/gaveta, texto, campos · Largura/tema: 1440 claro
- Prints: sistema `imagens/pessoas-2/PedidoAprovadoFluxo-1440-claro-sistema.png` · canvas `imagens/pessoas-2/PedidoAprovadoFluxo-1440-claro-canvas.png`
- O que difere: canvas "TROCAR TURMA" com subtítulo "Camila Ferreira · pedido do portal de 07/10", aviso verde "Pedido aprovado. Termine a troca abaixo; se desistir, o pedido continua Aprovado e a matrícula não muda.", campo "Modalidade" somente leitura, "Nova turma principal" já preenchida com a turma pedida, "Outros horários (opcional)" e nota "A troca de turma vale na hora. O plano e a mensalidade não mudam."; botões "Desistir" / "Confirmar troca". Sistema: "Trocar turma de Jiu-Jitsu" com "A troca vale na hora.", só "Turma principal", sem aviso de pedido aprovado, sem subtítulo do aluno/pedido, sem "Outros horários" e botões "Cancelar" / "Trocar turma". O pedido (fixture) abriu a janela sobre a ficha em gaveta (e não sobre a lista), o que é aceitável; a ficha traz título "Ficha do aluno" e aba "Matrícula e modalidades".
- Código: `frontend/src/features/students/ChangeClassDialog.tsx` (corpo), `RecordEnrollment.tsx:73-100` (abertura por `?pedido=`)
- Padrão: janela sem aviso de contexto; título de janela em caixa baixa

#### M130 · ResponsavelDesligar (janela) **[NOVO R2]**

- Lote/ID de origem: pessoas-2 · P2-11
- Categoria: texto, botões/ações, modal/gaveta · Largura/tema: 1440 claro
- Prints: sistema `imagens/pessoas-2/ResponsavelDesligar-1440-claro-sistema.png` · canvas `imagens/pessoas-2/ResponsavelDesligar-1440-claro-canvas.png`
- O que difere: canvas "DESLIGAR RESPONSÁVEL" (subtítulo "Ana Paula Nogueira · responsável financeira"), explica que ela perde o portal e recebe e-mail neutro, caixa RESUMO (o outro responsável passa a financeiro, o aluno não fica sem responsável, o motivo vai à auditoria), "Motivo *" e botão vermelho de contorno "Desligar". Sistema usa a palavra "Desvincular" (botão do cartão e da janela), título "Desvincular Responsável Um Fictício", texto curto, "Motivo" sem asterisco e "Informe o motivo para continuar." e botão "Desvincular" cinza. Cartão do responsável (sistema): selos "Financeiro/Acesso pendente", "Reenviar convite" e "Desvincular" em botões; fora do escopo do canvas, mas a aba inteira difere (ver lote pessoas-1).
- Código: `frontend/src/features/students/` (diálogo de desvincular responsável; i18n `students.record.guardians…`)
- Padrão: botão de perigo sem vermelho; resumo ausente

#### M131 · Agenda **[NOVO R2]**

- Lote/ID de origem: portal-a-1 · PA-03
- Categoria: botões/ações, filtros · Largura/tema: 1440 claro
- Prints: sistema `imagens/portal-a-1/PortalAgenda-1440-claro-sistema.png` · canvas `imagens/portal-a-1/PortalAgenda-1440-claro-canvas.png`
- O que difere: canvas tem navegação de semana com setas "<" ">" e legenda do período "12 a 18 de outubro", seletor "Modalidade: Todas" à direita, alternador "Só minha turma / Todos os meus horários" em controle segmentado alinhado ao título e nota "Sem presença nem frequência. Para mudar de turma, faça um pedido em Minhas informações." no fim. Sistema: dois botões grandes separados (Só minha turma / Todos os meus horários), outra linha com "Semana anterior", "Hoje", "Próxima semana" em botões de texto e "Semana de 26/10/2026 a 01/11/2026", linha de legenda em chips clicáveis (Principal, Outro horário, Avulsa, Alterada, Cancelada, Sem aula) que o canvas não tem na semana; sem seletor de modalidade (sistema só filtra pela legenda) e sem a nota de rodapé.
- Padrão: filtro em botões x segmentado

#### M132 · Aviso aberto **[NOVO R2]**

- Lote/ID de origem: portal-a-1 · PA-06 · gravidade revista (era alta): O aviso abre e mostra o conteúdo; faltam só os atalhos "Ver na agenda" e "Abrir o grupo".
- Categoria: modal/gaveta, botões/ações · Largura/tema: 1440 claro e 390 claro
- Prints: sistema `imagens/portal-a-1/PortalAvisoAberto-1440-claro-sistema.png` · canvas `imagens/portal-a-1/PortalAvisoAberto-1440-claro-canvas.png (celular: PortalMAvisoAberto-390-claro-sistema.png / -canvas.png)`
- O que difere: canvas = página própria ("< Voltar aos avisos" / "< Avisos") com tipo do aviso em pílula ("Aula cancelada"), modalidade · turma · unidade, data e hora ("08/10/2026 às 10h02"), título grande em caixa alta, autor com avatar e perfil ("Bianca Torres, Professora"), texto com negrito, e dois botões de ação: "Ver na agenda" e "Abrir o grupo da turma" (verde, WhatsApp), mais nota "Ao abrir, o aviso conta como lido para a academia". Sistema = janela modal sobre a lista com título, "Enviado em", "Para:", "Por" e o texto puro; sem tipo, sem hora, sem avatar, sem "Ver na agenda" e sem "Abrir o grupo da turma". O caminho "do aviso para a agenda/grupo" não existe.
- Código: `frontend/src/features/portal/PortalAnnouncementDialog.tsx:44-60` (corpo da janela só com data, destino, autor, imagem e texto; nenhuma ação); abertura por modal em `frontend/src/features/portal/PortalAnnouncementsPage.tsx:150`.
- Padrão: modal x página

#### M133 · Confirmar nova modalidade **[NOVO R2]**

- Lote/ID de origem: portal-a-1 · PA-07
- Categoria: modal/gaveta, estados · Largura/tema: 1440 claro e 390 claro
- Prints: sistema `imagens/portal-a-1/PortalConfirmarModalidade-1440-claro-sistema.png` · canvas `imagens/portal-a-1/PortalConfirmarModalidade-1440-claro-canvas.png (celular: PortalMConfirmarModalidade-390-claro-sistema.png / -canvas.png)`
- O que difere: canvas = janela central (celular: gaveta de baixo com alça) "NOVA MODALIDADE", subtítulo "Não bloqueia o portal e não tem prazo.", tabela Modalidade/Turma principal/Plano/Mensalidade, nota da primeira mensalidade, caixa "Confirmo o plano e o valor" e botões "Depois" e "Confirmar". Sistema = cartão fixo "Confirme a modalidade nova" no fim da página do Início (a 390 px depois de Faixa), com texto corrido e um botão "Confirmar Muay Thai"; sem tabela, sem caixa de confirmação, sem "Depois" e fora da vista (não chama a atenção). Também só aparece com pendência (nenhum aluno do MSW tem; fixture criou).
- Código: `frontend/src/features/portal/ModalityConfirmations.tsx:51-69` (cartão); montado em `frontend/src/App.tsx:91`.
- Padrão: modal x cartão

#### M134 · Estados do portal **[NOVO R2]**

- Lote/ID de origem: portal-a-1 · PA-09
- Categoria: estados · Largura/tema: 1440 claro e 390 claro (erro)
- Prints: sistema `imagens/portal-a-1/PortalEstados-1440-claro-erro-sistema.png, -carregando-sistema.png, -semacesso-sistema.png, -vazio-sistema.png` · canvas `imagens/portal-a-1/PortalEstados-1440-claro-canvas.png (celular: PortalMEstados-390-claro-erro-sistema.png / PortalMEstados-390-claro-canvas.png)`
- O que difere: canvas mostra cada estado dentro de um cartão com o nome do módulo: carregando com barras e texto "Carregando suas cobranças...", vazio com check verde e "Tudo em dia ... A próxima vence em 10/11." + "Ver histórico", erro com ícone vermelho "Não conseguimos carregar o Financeiro. Nada foi alterado. Confira a sua conexão e tente de novo." + "Tentar de novo" (com ícone), sem acesso com cadeado e botão "Voltar ao Início". Sistema: o estado ocupa a página inteira com ícone em círculo, texto genérico "Não foi possível carregar / Não conseguimos carregar as informações agora. Nada foi alterado nos seus dados. Tente de novo em instantes. Se continuar, fale com o suporte." (3 parágrafos soltos), carregando só 3 barras sem texto, sem acesso "Você não tem acesso. Seu perfil não permite ver esta tela. Fale com quem administra a sua academia." sem botão "Voltar ao Início"; vazio aparece como dois cartões ("Nenhuma cobrança em aberto", "Nenhum histórico neste ano") sem "Tudo em dia" nem a próxima data nem "Ver histórico". Os quatro estados existem, mas texto, ícone e ação diferem.
- Padrão: estados genéricos

#### M136 · Início **[NOVO R2]**

- Lote/ID de origem: portal-a-2 · portal-a-2-03
- Categoria: texto · Largura/tema: 1440 e 390 claro
- Prints: sistema `imagens/portal-a-2/PortalInicio-fx-1440-claro-sistema.png` · canvas `imagens/portal-a-2/PortalInicio-canvas.png`
- O que difere: canvas: rótulo "QUINTA-FEIRA, 8 DE OUTUBRO" e título "OLÁ, BRUNO". Sistema: título fixo "BEM-VINDO AO CTCORE", sem data nem nome; "Ver carteirinha" solto sob o título (canvas: link dentro do cartão Graduação) e "Meus pedidos" solto no fim da página (canvas: sem esse link).
- Código: `frontend/src/features/portal/PortalHomePage.tsx:35-41`; `frontend/src/i18n/pt-BR.json` (`app.welcomeTitle`)
- Padrão: título da página sem contexto

#### M137 · Início **[NOVO R2]**

- Lote/ID de origem: portal-a-2 · portal-a-2-04
- Categoria: layout · Largura/tema: 1440 claro
- Prints: sistema `imagens/portal-a-2/PortalInicio-fx-1440-claro-sistema.png` · canvas `imagens/portal-a-2/PortalInicio-canvas.png`
- O que difere: canvas: 2 colunas (cobrança + próximas aulas na linha de cima; avisos + graduação embaixo), coluna total ~1120 px. Sistema: pilha única de ~736 px (`max-w-3xl`) com Cobrança, Próximas aulas, Avisos e Faixa, todas em largura total.
- Código: `frontend/src/features/portal/PortalLayout.tsx:76`; `PortalHomePage.tsx:72`
- Padrão: grade de cartões do portal

#### M138 · Início **[NOVO R2]**

- Lote/ID de origem: portal-a-2 · portal-a-2-05
- Categoria: colunas/campos, botões/ações · Largura/tema: 1440 e 390 claro (fixture)
- Prints: sistema `imagens/portal-a-2/PortalInicio-fx-1440-claro-sistema.png, imagens/portal-a-2/PortalInicio-fx-390-claro-sistema.png` · canvas `imagens/portal-a-2/PortalInicio-canvas.png, imagens/portal-a-2/PortalMInicio-canvas.png`
- O que difere: cartão de cobrança. Canvas: rótulo "MUAY THAI · SET/2026", pílula "Vencida" à direita, valor grande "R$ 160,00", "Venceu em… bloqueia em…", linha "Pix/Cartão/Boleto" com valores, "Também em aberto…" e "Pagar" largura total. Sistema: pílula + pílula de modalidade, título genérico "Cobrança", "Vence em … · R$ …" em linha comum e "Pagar" pequeno; sem valor em destaque, sem referência do mês, sem valores por meio, sem "Também em aberto".
- Código: `frontend/src/features/portal/PortalChargeCard.tsx`
- Padrão: cartão de cobrança do portal

#### M139 · Início **[NOVO R2]**

- Lote/ID de origem: portal-a-2 · portal-a-2-06
- Categoria: colunas/campos, botões/ações · Largura/tema: 1440 e 390 claro
- Prints: sistema `imagens/portal-a-2/PortalInicio-fx-1440-claro-sistema.png` · canvas `imagens/portal-a-2/PortalInicio-canvas.png, imagens/portal-a-2/PortalMInicio-canvas.png`
- O que difere: Próximas aulas. Canvas: dia/hora curto em destaque à esquerda ("Hoje 19h30", "Sex 7h"), título da aula, linha "unidade · professor", pílula "Alterada" à direita, link "Ver agenda ›" no cabeçalho. Sistema: pílulas (status, modalidade · unidade) acima, título, data completa "30/10/2026 · 19:00 às 20:00"; sem professor, sem "Ver agenda". Responsável (canvas): pílulas "1 experimental na turma" e "Sem aula · Feriado"; sistema usa "Cancelada" com título riscado e "Motivo:" (sem experimental).
- Código: `frontend/src/features/portal/PortalNextLessons.tsx`, `PortalLessonItem.tsx`
- Padrão: item de aula do portal

#### M140 · Início **[NOVO R2]**

- Lote/ID de origem: portal-a-2 · portal-a-2-07
- Categoria: colunas/campos · Largura/tema: 1440 e 390 claro
- Prints: sistema `imagens/portal-a-2/PortalInicio-fx-1440-claro-sistema.png` · canvas `imagens/portal-a-2/PortalInicio-canvas.png`
- O que difere: Avisos. Canvas: ponto vermelho nos não lidos, título + pílula "Fixado" com ícone de alfinete, linha "modalidade · unidade · professor", data curta à direita (06/10), "Ver todos ›" no cabeçalho. Sistema: pílulas "Fixado"/"Não lido" acima do título, "Enviado em dd/mm/aaaa", contador "3 avisos não lidos" no cabeçalho, link "Ver todos os avisos" no rodapé do cartão; sem modalidade/unidade.
- Código: `frontend/src/features/portal/PortalNoticesCard.tsx`
- Padrão: item de aviso (liga com B2)

#### M141 · Início **[NOVO R2]**

- Lote/ID de origem: portal-a-2 · portal-a-2-08
- Categoria: colunas/campos, ícone/avatar/cor · Largura/tema: 1440 e 390 claro
- Prints: sistema `imagens/portal-a-2/PortalInicio-fx-1440-claro-sistema.png` · canvas `imagens/portal-a-2/PortalInicio-canvas.png, imagens/portal-a-2/PortalMInicio-canvas.png`
- O que difere: canvas: cartão "GRADUAÇÃO" com ponto colorido da modalidade, faixa desenhada (cor, tarja preta e grau), "Faixa roxa · 1 grau", pílula "Nova" e link "Ver carteirinha". Sistema: cartão "Faixa" com retângulo liso de uma cor, pílula da modalidade, "Azul, grau 2", "desde dd/mm/aaaa", sem tarja nem link.
- Código: `frontend/src/features/portal/PortalBelts.tsx`
- Padrão: faixa sem desenho (M32 mostra o mesmo na carteirinha)

#### M142 · Início responsável **[NOVO R2]**

- Lote/ID de origem: portal-a-2 · portal-a-2-13
- Categoria: filtros/abas/busca, colunas/campos · Largura/tema: 1440 e 390 claro
- Prints: sistema `imagens/portal-a-2/PortalResponsavel-1440-claro-sistema.png, imagens/portal-a-2/PortalResponsavel-390-claro-sistema.png` · canvas `imagens/portal-a-2/PortalResponsavel-canvas.png, imagens/portal-a-2/PortalMResponsavel-canvas.png`
- O que difere: canvas: uma faixa de cartões por filho com avatar, idade · modalidade · unidade e situação ("Em dia", "R$ 150,00 vencendo"), cartão "Toda a família" (Financeiro de todos) e cartão "Em aberto na família R$ 225,00"; no celular, pílulas Davi / Lia / Família. Sistema: dois botões de texto "Em aberto: R$ x" e a linha "Total da família em aberto"; sem avatar, sem situação, sem opção "Toda a família". Título do cartão de cobrança também diferente ("DAVI · JIU-JITSU · OUT/2026" no canvas). Barra: canvas mostra "Responsável" no perfil; sistema "Ana · Responsável" em texto.
- Código: `frontend/src/features/portal/PortalFamilyStrip.tsx`
- Padrão: seletor de filho

#### M143 · Portal reduzido **[NOVO R2]**

- Lote/ID de origem: portal-a-2 · portal-a-2-14
- Categoria: colunas/campos, botões/ações · Largura/tema: 1440 e 390 claro
- Prints: sistema `imagens/portal-a-2/PortalReduzido-1440-claro-sistema.png, imagens/portal-a-2/PortalReduzido-fin-1440-claro-sistema.png, imagens/portal-a-2/PortalReduzido-390-claro-sistema.png` · canvas `imagens/portal-a-2/PortalReduzido-canvas.png, imagens/portal-a-2/PortalMReduzido-canvas.png`
- O que difere: canvas: cartão "Fale com a academia" com botão verde "WhatsApp da unidade", telefone e e-mail; "Histórico e recibos" em tabela (Referência, Pago em, Valor, Recibo); aviso informando o motivo ("Muay Thai bloqueado por atraso"); abas Financeiro e Minhas informações. Sistema: Início reduzido só com o cartão de cobrança; Financeiro com lista própria; faixa de aviso genérica; nenhum contato da unidade (o `/portal/shell` devolve `unit: null` e o rodapé fica só com o nome). RN-INA-08 manda mostrar o contato da unidade. Na sistema o reduzido também não tem sino (canvas mantém sino e avatar).
- Código: `frontend/src/mocks/handlers.ts:4563`; `PortalLayout.tsx:48,70-75`
- Padrão: portal reduzido

#### M144 · Termo (desktop e celular) **[NOVO R2]**

- Lote/ID de origem: portal-a-2 · portal-a-2-15
- Categoria: layout, texto, botões/ações · Largura/tema: 1440 claro, 390 claro (fixture)
- Prints: sistema `imagens/portal-a-2/PortalTermo-fx-1440-claro-sistema.png, imagens/portal-a-2/PortalTermo-fx-390-claro-sistema.png` · canvas `imagens/portal-a-2/PortalTermo-canvas.png, imagens/portal-a-2/PortalMTermo-canvas.png`
- O que difere: canvas: marca da academia no topo (desktop) ou barra do portal (celular), rótulo "ANTES DE COMEÇAR", texto de abertura personalizado ("Olá, Juliana! Para liberar o portal do Theo…"), termo numa caixa rolável com "Termo de matrícula · versão 3", "Baixar PDF" e "Versão 3 · publicada em…", dicas sob os dois aceites ("Fica registrado com data, hora e versão"; revogação em Minhas informações), botão "Sair" ao lado de "Aceitar e continuar". Sistema: barra "ctcore" com "Minha conta" e tema, título, texto genérico, cartão "Termo de Theo" com texto corrido, caixa de uso de imagem em seção separada com texto próprio, sem versão/PDF, sem dicas, sem "Sair", botão cinza desabilitado até marcar.
- Código: `frontend/src/features/portal/PortalTermPage.tsx:19-96`
- Padrão: página de aceite

#### M145 · Termo com 2 filhos **[NOVO R2]**

- Lote/ID de origem: portal-a-2 · portal-a-2-16
- Categoria: colunas/campos, estados · Largura/tema: 1440 claro (fixture)
- Prints: sistema `imagens/portal-a-2/PortalTermoFilhos-fx-1440-claro-sistema.png` · canvas `imagens/portal-a-2/PortalTermoFilhos-canvas.png`
- O que difere: canvas: cartão "Alunos · 1 de 2 aceitos" com avatar, turma e selos "Aceito agora"/"Falta aceitar", depois um único termo do aluno atual com o botão. Sistema: dois cartões completos empilhados, cada um com seu termo e botão, sem progresso, sem avatar, sem selo, sem texto "Você é responsável financeira de 2 alunos…".
- Código: `PortalTermPage.tsx:66-73`
- Padrão: página de aceite

#### M146 · Financeiro **[NOVO R2]**

- Lote/ID de origem: portal-b-1 · PB1-02
- Categoria: layout, colunas · Largura/tema: 1440 claro - Sistema/Canvas: PortalFinanceiro-1440-claro-sistema.png / -canvas.png
- Prints: sistema `` · canvas ``
- O que difere: canvas tem "Débito automático" (com texto da taxa e botão "Ativar") e "Recibos por e-mail" em dois cartões lado a lado com ícone; sistema tem um cartão "Débito automático" com tag "Inativo" e sem botão, e nenhum cartão de recibos por e-mail. Histórico: canvas em tabela (Referência, Vencimento, Valor pago, Pago em, Meio, Situação, "Recibo" com ícone) com anos em abas 2026/2025/2024; sistema em lista de blocos com tag "Paga", "Baixar recibo" sublinhado e seletor nativo "Ano". A 390 px: canvas tem linhas compactas com botão de download em ícone e "Desligado" + "Ativar".
- Código: PortalAutoDebitCard.tsx:7; PortalChargeHistory.tsx
- Padrão: seletor nativo x abas em pílula; lista x tabela

#### M147 · Financeiro (responsável) **[NOVO R2]**

- Lote/ID de origem: portal-b-1 · PB1-04
- Categoria: filtros/abas · Largura/tema: 1440 claro
- Prints: sistema `imagens/portal-b-1/PortalFinanceiro-1440-claro-familia-sistema.png` · canvas ``
- O que difere: seletor nativo "Aluno" (inclui "Toda a família"); o canvas do responsável (Carteirinhas) usa chips por filho. Não há canvas de Financeiro do responsável neste lote; comparação só por padrão.
- Código: PortalFinancePage.tsx:41
- Padrão: seletor nativo x pílulas

#### M148 · Carteirinha, estado sem conexão **[NOVO R2]**

- Lote/ID de origem: portal-b-1 · PB1-06
- Categoria: estados · Largura/tema: 390 claro - Canvas: PortalMCarteirinha-390-claro-canvas.png
- Prints: sistema `` · canvas ``
- O que difere: canvas mostra faixa amarela no topo da página "Sem conexão · Atualizada em ..." e texto "Sem internet, o app mostra a última versão do Início, da agenda e da carteirinha..."; no código a indicação é só uma tag dentro do cartão (PortalMemberCard.tsx:86). Estado não alcançado no navegador (MSW).
- Padrão: estado offline

#### M149 · Carteirinha (responsável) **[NOVO R2]**

- Lote/ID de origem: portal-b-1 · PB1-07
- Categoria: layout, filtros/abas · Largura/tema: 1440 claro
- Prints: sistema `PortalCarteirinhaFilhos-1440-claro-sistema.png` · canvas `PortalCarteirinhaFilhos-1440-claro-canvas.png`
- O que difere: canvas tem rótulo "RESPONSÁVEL", título "CARTEIRINHAS", texto "Você vê a carteirinha de cada filho...", chips por filho e um cartão por filho lado a lado; sistema tem título "CARTEIRINHA", seletor nativo "Aluno" e um cartão só.
- Código: PortalCardPage.tsx:30-41
- Padrão: seletor nativo x pílulas; um cartão x vários

#### M150 · Minhas informações, ações de dados **[NOVO R2]**

- Lote/ID de origem: portal-b-1 · PB1-09
- Categoria: botões/ações · Largura/tema: 1440 claro, 390 claro
- Prints: sistema `` · canvas ``
- O que difere: "Baixar meus dados" e "Excluir minha conta" aparecem desabilitados com "Estas ações ficam disponíveis em breve." (canvas: ativos; Excluir abre a janela PortalExcluir). Previsto na Sprint 13 (#118). Falta também "Instalar o app" no celular.
- Código: i18n/pt-BR.json:3231
- Padrão: ação pendente de sprint

#### M151 · Pedir troca de turma / plano / nova modalidade (janelas) **[NOVO R2]**

- Lote/ID de origem: portal-b-2 · portal-b-2-02
- Categoria: modal/gaveta / colunas/campos · Largura/tema: 1440 claro, 390 claro
- Prints: sistema `imagens/portal-b-2/PortalPedidoTurma-1440-claro-sistema.png, PortalPedidoPlano-1440-claro-sistema.png, PortalPedidoModalidade-1440-claro-sistema.png, PortalMPedidoTurma-390-claro-sistema.png` · canvas `mesmos nomes com -canvas.png`
- O que difere: canvas: título em caixa alta condensada, campo "Modalidade" somente leitura, "Nova turma principal" (com dias e professor), mensagem com ajuda ("Plano e mensalidade não mudam..."); plano: "Plano atual" e "Novo plano" em cartões de escolha com valor e motivo de indisponível ("Não disponível para a sua idade"); nova modalidade: "Unidade" e "Modalidade" separados e bloco "RESUMO" com 3 itens; rodapé separado, "Cancelar" em texto, "Enviar pedido" escuro. No celular o canvas é gaveta inferior (alça, botão largo). Sistema: janela central, frase de efeito no topo, linha "Jiu-Jitsu (Unidade Centro)" no lugar do campo, um único seletor nativo ("Turma desejada", "Plano desejado", "Modalidade e unidade" juntos, opções só com nome), contador "0/500", sem "Plano atual", sem valores, sem resumo; no celular a mesma janela central. "Enviar pedido" cinza até escolher.
- Código: frontend/src/features/portal/PortalRequestDialog.tsx; ModalityDialog em frontend/src/features/students/ModalityPickers.tsx:78
- Padrão: modal x gaveta no celular; seletor nativo x cartões de escolha; falta resumo

#### M152 · Pedido indisponível (encerramento aberto) **[NOVO R2]**

- Lote/ID de origem: portal-b-2 · portal-b-2-03
- Categoria: estados / modal/gaveta · Largura/tema: 1440 claro
- Prints: sistema `imagens/portal-b-2/PortalPedidoBloqueado-1440-claro-sistema.png` · canvas `imagens/portal-b-2/PortalPedidoBloqueado-1440-claro-canvas.png`
- O que difere: canvas abre "Pedir troca de turma" com aviso âmbar "Há um pedido de encerramento aberto para o Muay Thai. Enquanto ele estiver aberto, não dá para pedir troca de turma nem de plano nessa modalidade." e botão "Entendi". Sistema esconde os botões de troca e mostra só texto cinza "Encerramento solicitado em ... A academia vai entrar em contato." (não explica que turma/plano ficam travados).
- Código: frontend/src/features/portal/PortalRequestsPage.tsx (ramo `m.closureRequestedAt`)
- Padrão: estado bloqueado como texto solto x aviso em janela

### Gravidade baixa

#### B1 · Faixas etárias

- Rodada 1: Faixas etárias: canvas coluna "Usada em", ícone de editar, aviso de categorias; sistema Editar/Apagar em texto.
- Print (sistema à esquerda, canvas à direita): `imagens/rodada-1/43-configuracoes-faixas-etarias-claro.png`
- **Rodada 2 · configuracoes-1 · CF1-13** (ConfigFaixas) — amplia B1
  - Categoria: colunas/campos, botões/ações · Largura/tema: 1440 claro
  - Prints: sistema `imagens/configuracoes-1/ConfigFaixas-1440-claro-sistema.png` · canvas `imagens/configuracoes-1/ConfigFaixas-1440-claro-canvas.png`
  - sistema mostra linhas "Infantil · 4 a 12 anos" com "Editar"/"Apagar" em texto; canvas tem tabela Nome / Idade mínima / Idade máxima / Usada em, botão de lápis, faixa azul sobre categorias de competição e "Última alteração". "Nova faixa" fica dentro do cartão (canvas: "+ Nova faixa etária" no topo). Subtítulo do sistema "As faixas organizam turmas, aulas experimentais e o diretório..." contra "Administrativas: ...".

#### B2 · Portal Avisos

- Rodada 1: Portal Avisos: canvas filtros por modalidade com contagem e ponto vermelho; sistema cartões com borda.
- Print (sistema à esquerda, canvas à direita): `imagens/rodada-1/17-portal-avisos-claro.png`
- **Rodada 2 · portal-a-1 · PA-05** (Avisos (lista)) — amplia B2
  - Categoria: filtros/abas/busca, layout, botões/ações · Largura/tema: 1440 claro e 390 claro
  - Prints: sistema `imagens/portal-a-1/PortalAvisos-1440-claro-sistema.png` · canvas `imagens/portal-a-1/PortalAvisos-1440-claro-canvas.png (celular: PortalMAvisos-390-claro-sistema.png / -canvas.png)`
  - canvas = lista única em cartão com linhas separadas, ponto vermelho nos não lidos e fundo rosado, título + pílula "Fixado até 17/10", linha de apoio (modalidade, turma, autor, resumo) e data curta à direita; filtros em pílulas com contagem (Todos 6, Não lidos 2, Jiu-Jitsu 4, Muay Thai 2); "Marcar todos como lidos" com ícone de check no canto direito; dica "Abrir um aviso marca como lido...". Sistema = cartões individuais com borda lateral grossa, 4 linhas por aviso (tag "Não lido", título, "Enviado em", "Para:", "Por"), três filtros sem contagem (Todos, Não lidos, "Por modalidade", que depois exige escolher a modalidade) e botão "Marcar todos como lidos" em linha separada abaixo. Sem ponto vermelho, sem data curta, sem resumo do texto. No celular o canvas mostra "Marcar lidos" ao lado do título e as pílulas na mesma linha; sistema quebra em 2 linhas de botões grandes.

#### B3 · Nova aula avulsa

- Rodada 1: Nova aula avulsa: faltam "Observação para a equipe" e "Avisar os alunos"; turma em botão de opção (canvas cartões).
- Print (sistema à esquerda, canvas à direita): `imagens/rodada-1/76-agenda-aula-avulsa-claro.png`
- **Rodada 2 · operacao-1 · OP1-10** (AulaAvulsa) — amplia B3
  - Categoria: colunas/campos, layout · Largura/tema: 1440 claro
  - Prints: sistema `imagens/operacao-1/AulaAvulsa-1440-claro-sistema.png` · canvas `imagens/operacao-1/AulaAvulsa-1440-claro-canvas.png`
  - canvas = título com asterisco, Modalidade e Professor lado a lado, Data/Início/Término em três campos, "Quem vê" em cartões (Uma turma / Todas as turmas da unidade com descrição), "Observação para a equipe", caixa "Avisar os alunos", rodapé fixo. Sistema = "Unidade: Unidade Centro" em texto, uma coluna, Modalidade e Professor com "Escolha", data nativa (calendário do navegador) e hora nativa "--:--", rádios simples e sem observação nem "Avisar". Rótulo "Quem vê esta aula" x "Quem vê na agenda e no portal".
- **Rodada 2 · operacao-1 · OP1-13** (AulaCancelar) — amplia B3 (padrão modal)
  - Categoria: modal/gaveta, estados · Largura/tema: 1440 claro
  - Prints: sistema `imagens/operacao-1/AulaCancelar-1440-claro-sistema.png` · canvas `imagens/operacao-1/AulaCancelar-1440-claro-canvas.png`
  - canvas = cartão "AULA" com resumo, motivos em cartões (Outro com campo "Escreva o motivo"), aviso amarelo "Esta aula tem 1 aula experimental marcada. Decida antes de cancelar" com linha por experimental (Reagendar / Cancelar experimental, seletor de nova data), "Avisar por Portal / E-mail / Push", botão vermelho "Cancelar aula", "Voltar". Sistema = frase "A aula de ... será cancelada. Esta ação não pode ser desfeita aqui", rádios simples (sem campo para "Outro"), "Canais do comunicado: Portal (sempre), E-mail" (sem Push), texto "O envio do comunicado chega em breve", botões "Manter aula" e "Cancelar aula" em preto (canvas = vermelho). Sem tratamento de aulas experimentais (tela de experimentais não existe).

#### B4 · Nova cobrança avulsa

- Rodada 1: Nova cobrança avulsa: falta prévia das taxas; busca de aluno sem ícone nem aluno confirmado.
- Print (sistema à esquerda, canvas à direita): `imagens/rodada-1/81-cobranca-avulsa-claro.png`
- **Rodada 2 · financeiro-1 · financeiro-1-07** (Nova cobrança avulsa) — amplia B4
  - Categoria: modal/gaveta, campos · Largura/tema: 1440 claro
  - Prints: sistema `imagens/financeiro-1/CobrancaAvulsa-1440-claro-sistema.png` · canvas `imagens/financeiro-1/CobrancaAvulsa-1440-claro-canvas.png`
  - busca de aluno sem ícone nem aluno confirmado (B4); parágrafo de introdução no sistema, ausente no canvas; "Descrição" é textarea de 3 linhas e "0/80" (canvas: campo de uma linha com "18 de 80 caracteres · aparece no portal e no recibo"); valor sem "R$" formatado; vencimento em data nativa em inglês (canvas: ícone de calendário e dd/mm/aaaa); sem "Pix: R$ ... · Cartão: R$ ... Avulsa não conta para o bloqueio por atraso." (taxas dependem da Sprint 11); sem "*" de obrigatório; título e rodapé fora do padrão do canvas (caixa alta, rodapé em faixa).

#### B5 · Organização, contraste da cor

- Rodada 1: Organização, contraste da cor: falta cor sugerida com contraste calculado.
- Print (sistema à esquerda, canvas à direita): `imagens/rodada-1/62-configuracoes-organizacao-contraste-claro.png`
- **Rodada 2 · configuracoes-1 · CF1-02** (ConfigOrgContraste) — amplia B5
  - Categoria: estados, texto, ícone/avatar/cor · Largura/tema: 1440 claro
  - Prints: sistema `imagens/configuracoes-1/ConfigOrgContraste-1440-claro-sistema.png` · canvas `imagens/configuracoes-1/ConfigOrgContraste-1440-claro-canvas.png`
  - o estado existe e bloqueia "Salvar" (ok). Texto do sistema "Contraste com texto branco: 1,28 para 1" + "Reprovado: o mínimo AA é 4,5 para 1. Escolha uma cor mais escura." com X vermelho; canvas "Esta cor tem contraste 1,8:1 com o texto branco. Escolha uma com pelo menos 4,5:1 (AA)." com ícone de alerta. Falta a "Sugestão: #8A5A00 · 5,9:1" com amostra. Prévia: sistema "Botão de exemplo" ao lado de "Prévia do botão"; canvas botão "Pagar" + "Prévia: o texto fica ilegível". Sistema separa campo de texto e quadrado nativo de cor (canvas: um campo com ícone).

#### B6 · Estrutura mobile da gestão (Início) **[NOVO R2]**

- Lote/ID de origem: comunicacao-e-estrutura-da-gestao-1 · CE1-12
- Categoria: layout, ícone/avatar/cor · Largura/tema: 390 claro
- Prints: sistema `imagens/comunicacao-e-estrutura-da-gestao-1/Inicio-390-claro-sistema.png` · canvas `imagens/comunicacao-e-estrutura-da-gestao-1/GMInicioCoord-360-claro-canvas.png`
- O que difere: barra do canvas = logo colorido "AL" + título + seletor de unidade + lupa + sino; sistema = logo "C" escuro + título, sino, avatar, lupa apagada, tema. Aba "Cobranças" desativada no sistema, ativa no canvas. Abas iguais em ícone, rótulo e ordem.
- Padrão: barra superior simplificada

#### B7 · Tema escuro **[NOVO R2]**

- Lote/ID de origem: comunicacao-e-estrutura-da-gestao-1 · CE1-13
- Categoria: ícone/avatar/cor · Largura/tema: 1440 escuro
- Prints: sistema `imagens/comunicacao-e-estrutura-da-gestao-1/Comunicados-1440-escuro-sistema.png, ComunicadoNovo-1440-escuro-sistema.png` · canvas ``
- O que difere: o canvas dessas telas não tem versão escura. O escuro do sistema está legível (texto, tabela, botões, bordas). Pontos fracos: ícone de calendário dos campos de data quase invisível e campo nativo de arquivo "Choose File No file chosen" sem estilo.
- Padrão: campo de arquivo/data nativo

#### B8 · Barra de cima no celular **[NOVO R2]**

- Lote/ID de origem: comunicacao-e-estrutura-da-gestao-2 · CE2-07
- Categoria: layout / ícone/avatar/cor · Largura/tema: 390 claro
- Prints: sistema `imagens/comunicacao-e-estrutura-da-gestao-2/GMNotificacoes-390-claro-sistema.png e GMNovoComunicado-390-claro-sistema.png` · canvas `GMUnidade-canvas.png`
- O que difere: canvas mostra marca "AL" + "Início" + unidade à esquerda e lupa + sino (selo vermelho) à direita; sistema mostra seta de voltar (ou inicial "C"), sino com selo preto, avatar rosa, busca desabilitada e tema. Sem lupa ativa.
- Padrão: barra do celular com ícones de menos

#### B9 · Controles nativos no tema escuro **[NOVO R2]**

- Lote/ID de origem: configuracoes-1 · CF1-16
- Categoria: ícone/avatar/cor · Largura/tema: 1440 escuro
- Prints: sistema `imagens/configuracoes-1/ConfigUnidade-1440-escuro-sistema.png, imagens/configuracoes-1/ConfigTermos-1440-escuro-sistema.png` · canvas ``
- O que difere: caixas de marcação e botões de opção são os nativos, brancos sobre fundo escuro (28 dias de vencimento, "Quem precisa aceitar esta versão"); "Salvar"/"Publicar" desabilitados ficam cinza opaco. O resto do tema escuro está legível nas telas abertas (Organização, Unidade, Termos, Modalidades).
- Padrão: controle nativo

#### B10 · (removida)

Era a lista "telas não construídas" do lote configuracoes-2, lida como achado. Numeração dos demais mantida; as telas estão em "sem comparar".

#### B11 · Encerrar benefício **[NOVO R2]**

- Lote/ID de origem: financeiro-1 · financeiro-1-14
- Categoria: modal/gaveta, texto · Largura/tema: 1440 claro
- Prints: sistema `imagens/financeiro-1/BeneficioEncerrar-1440-claro-sistema.png` · canvas `imagens/financeiro-1/BeneficioEncerrar-1440-claro-canvas.png`
- O que difere: canvas: título em caixa alta, cartão "RESUMO" ("Marina Teixeira · Jiu-Jitsu · desconto de R$ 30,00 · até 11/2026"), "Motivo *", nota com o valor que a próxima cobrança volta a ter, "Voltar" e "Encerrar benefício" em vermelho. Sistema: "Ana Souza · Judô" em texto, nota genérica sem valor, "Cancelar" e "Encerrar benefício" cinza (desabilitado até o motivo), sem vermelho, sem "*".
- Padrão: botão destrutivo sem cor de perigo; modal sem cartão de resumo

#### B12 · Carregando (lista de cobranças) **[NOVO R2]**

- Lote/ID de origem: financeiro-1 · financeiro-1-18
- Categoria: estados · Largura/tema: 1440 claro
- Prints: sistema `imagens/financeiro-1/Cobrancas-1440-claro-carregando-sistema.png` · canvas `sem tela de carregando neste lote`
- O que difere: sem canvas para comparar. Observação: as abas mostram "(0)" até chegar o total (ver `Cobrancas-1440-claro-sistema.png`), o que passa a impressão de "nenhuma cobrança" por um instante.
- Padrão: contagem 0 durante o carregamento Tema escuro: Cobranças e Benefícios no escuro (imagens/financeiro-1/CobrancasEscuro-1440-escuro-dados-sistema.png, BeneficiosLista-1440-escuro-sistema.png) sem problema de contraste, cor fixa ou texto ilegível; as diferenças com o canvas escuro são as do achado 02.

#### B13 · Cobranças **[NOVO R2]**

- Lote/ID de origem: financeiro-2 · F2-06
- Categoria: estados · Largura/tema: 1440 claro
- Prints: sistema `imagens/financeiro-2/Mensalidades-1440-claro-erro-sistema.png, Mensalidades-1440-claro-vazio-sistema.png` · canvas ``
- O que difere: erro e vazio funcionam ("Não foi possível carregar" + "Tentar de novo"; "Nada por aqui ainda"). Vazio genérico ("Quando houver itens...") sem ação sugerida. Canvas não traz estado vazio/erro para comparar.
- Padrão: estado vazio genérico

#### B15 · Agenda (menu) **[NOVO R2]**

- Lote/ID de origem: operacao-1 · OP1-06
- Categoria: ícone/avatar/cor · Largura/tema: 1440
- Prints: sistema `` · canvas ``
- O que difere: no menu lateral do sistema "Operação" mostra Agenda, Turmas, Dias sem aula; o canvas tem Aulas experimentais e Graduações (ver "Sem comparar"). O sistema tem ainda "Ajuda (Em breve)" e "Novidades (Em breve)" no rodapé; canvas só Ajuda e Novidades, com ponto vermelho.
- Padrão: item de menu futuro

#### B16 · Agenda, Dias sem aula **[NOVO R2]**

- Lote/ID de origem: operacao-1 · OP1-22
- Categoria: ícone/avatar/cor · Largura/tema: 1440 escuro, 390 escuro
- Prints: sistema `imagens/operacao-1/Agenda-1440-escuro-sistema.png, DiasSemAula-1440-escuro-sistema.png, AgendaPainelFuturo-390-escuro-sistema.png` · canvas ``
- O que difere: sem problema de contraste visível; legível nos três prints. O canvas só tem tema claro dessas telas. Cartões da grade escuros mantêm a borda colorida; selos "Cancelada"/"Substituição" legíveis. Nenhuma cor fixa clara vazando. Registro: sem achado de contraste, só nota. - Gravidade: baixa (nota)

#### B17 · Ficha do aluno (abas) **[NOVO R2]**

- Lote/ID de origem: pessoas-1-1 · pessoas-1-1-09
- Categoria: texto · Largura/tema: 1440 claro
- Prints: sistema `imagens/pessoas-1-1/AlunoMatricula-1440-claro-sistema.png` · canvas `imagens/pessoas-1-1/AlunoMatricula-1440-claro-canvas.png`
- O que difere: aba "Matrícula e modalidades" no sistema, "Matrícula" no canvas (as demais iguais: Resumo, Financeiro, Graduações, Responsáveis, Histórico). Sublinhado da aba ativa preto no sistema, vermelho no canvas.
- Código: `frontend/src/i18n/pt-BR.json:452-460` (`students.record.tabs`).
- Padrão: rótulo de aba

#### B18 · Encerrar modalidade (celular) **[NOVO R2]**

- Lote/ID de origem: pessoas-1-1 · pessoas-1-1-18
- Categoria: modal/gaveta · Largura/tema: 390 claro (canvas 360)
- Prints: sistema `imagens/pessoas-1-1/GMEncerrar-390-claro-sistema.png` · canvas `imagens/pessoas-1-1/GMEncerrar-360-claro-canvas.png`
- O que difere: canvas é tela cheia com seta de voltar, cartão "RESUMO", select de motivo, bloco "Cobranças em aberto" com um select Manter/Cancelar por linha e barra fixa Cancelar | Encerrar (vermelho). Sistema: modal em cartão sobre a ficha (ainda visível atrás), mesmos botões de opção do desktop, botões no fim do conteúdo. Mesma causa de 15/16 e 17; fica separado por ser a janela do celular.
- Código: `frontend/src/features/students/EndModalityDialog.tsx`, `ModalityDialog` (ver `ModalityPickers.tsx`).
- Padrão: modal x tela cheia no celular

#### B19 · Estado carregando da lista **[NOVO R2]**

- Lote/ID de origem: pessoas-1-1 · pessoas-1-1-19
- Categoria: estados · Largura/tema: 1440 claro
- Prints: sistema `imagens/pessoas-1-1/EstadoCarregando-1440-claro-sistema.png` · canvas `imagens/pessoas-1-1/EstadoCarregando-1440-claro-canvas.png · Diferença: imagens/pessoas-1-1/EstadoCarregando-1440-claro-diff.png`
- O que difere: canvas mantém o cabeçalho, abas com contagem e a tabela com cabeçalho e 8 linhas esqueleto (círculo de avatar, duas barras no nome, barras nas colunas), subtítulo "Unidade Tijuca · carregando..." e rodapé "1–25 de 131". Sistema mantém abas (sem contagem) e filtros, mas troca a tabela por 3 barras horizontais genéricas, sem cabeçalho de colunas.
- Código: `frontend/src/components/states/LoadingState` (usado por `QueryPage`).
- Padrão: esqueleto genérico x esqueleto da tabela

#### B20 · Tema escuro (lista e ficha) **[NOVO R2]**

- Lote/ID de origem: pessoas-1-1 · pessoas-1-1-20
- Categoria: ícone/avatar/cor · Largura/tema: 1440 escuro (só sistema)
- Prints: sistema `imagens/pessoas-1-1/Alunos-1440-escuro-sistema.png, imagens/pessoas-1-1/AlunoDetalhes-1440-escuro-sistema.png` · canvas `não existe versão escura destas telas.`
- O que difere: sem problema de legibilidade; contraste das linhas, selos e do botão "Nova matrícula" (claro sobre escuro) está correto. Único detalhe: o fundo do avatar com iniciais (cinza escuro) quase some contra o cartão e o selo "Em atraso" fica igual ao "Ativo" (sem cor), o que no escuro dificulta distinguir situação. Sem ação além das já registradas em 01 e 06.
- Padrão: selo neutro em contorno

#### B21 · Tema escuro **[NOVO R2]**

- Lote/ID de origem: pessoas-1-2 · L1-20
- Categoria: ícone/avatar/cor · Largura/tema: 1440 escuro
- Prints: sistema `imagens/pessoas-1-2/Wiz1440-escuro-e3-sistema.png, FichaMatricula-1440-escuro-sistema.png` · canvas ``
- O que difere: "Isentar taxa" aparece como caixa branca nativa e o ícone do calendário do campo de data some no fundo escuro; o resto legível.
- Padrão: controle nativo no escuro

#### B22 · Professores / Pedidos **[NOVO R2]**

- Lote/ID de origem: pessoas-2 · P2-12
- Categoria: ícone/avatar/cor, layout · Largura/tema: 1440 claro e escuro
- Prints: sistema `imagens/pessoas-2/Professores-1440-escuro-sistema.png, PedidosPortal-1440-escuro-sistema.png` · canvas ``
- O que difere: escuro sem problema de contraste visível. Barra superior do sistema: Sino, avatar+"Ana Coordenação", busca "Buscar Ctrl K" desabilitada, tema e ajuda; o canvas traz seletor de unidade ("Tijuca"), busca ativa e menu da conta com nome completo (já coberto por M1/M-estrutura); menu lateral com "Ajuda (Em breve)"/"Novidades (Em breve)" e sem contador de pedidos fixo (sistema mostra contador "4" de Pedidos só quando há dados; canvas "6"). Checkbox nativo azul (Jiu-Jitsu) ao lado de checkbox com cor da marca (Unidade) na mesma janela de edição de professor.
- Padrão: caixa de marcação nativa x marca

#### B23 · Portal › cabeçalho de perfil e responsável **[NOVO R2]**

- Lote/ID de origem: portal-a-1 · PA-11
- Categoria: ícone/avatar/cor · Largura/tema: 1440 claro
- Prints: sistema `imagens/portal-a-1/PortalFamilia-1440-claro-sistema.png` · canvas `imagens/portal-a-1/PortalFamilia-1440-claro-canvas.png`
- O que difere: sistema mostra "Ana · Responsável" em texto; canvas mostra avatar com iniciais e perfil abaixo do nome. Já registrado como parte de M1; aqui só como evidência do perfil responsável.
- Padrão: moldura do portal

#### B24 · Financeiro **[NOVO R2]**

- Lote/ID de origem: portal-a-1 · PA-12
- Categoria: ícone/avatar/cor · Largura/tema: 1440 escuro (e claro)
- Prints: sistema `imagens/portal-a-1/PortalFamilia-1440-escuro-sistema.png` · canvas `imagens/portal-a-1/PortalFamilia-1440-claro-canvas.png`
- O que difere: a caixa de seleção das cobranças é um quadrado cru, grande e branco no tema escuro (canvas: caixa pequena de 18 px, preta com check quando marcada).
- Padrão: caixa de seleção nativa

#### B25 · Início **[NOVO R2]**

- Lote/ID de origem: portal-a-2 · portal-a-2-09
- Categoria: filtros/abas/busca · Largura/tema: 1440 claro
- Prints: sistema `imagens/portal-a-2/PortalInicio-1440-claro-sistema.png` · canvas `imagens/portal-a-2/PortalInicio-canvas.png`
- O que difere: com mais de uma modalidade/unidade o sistema põe um seletor nativo "Filtrar por modalidade e unidade" antes dos cartões; o canvas não tem filtro na tela (aluno com 2 modalidades).
- Código: `PortalHomePage.tsx:73-85` (regra RN-POR-04)
- Padrão: seletor nativo no lugar de pílulas

#### B26 · Início **[NOVO R2]**

- Lote/ID de origem: portal-a-2 · portal-a-2-10
- Categoria: layout · Largura/tema: 1440 claro
- Prints: sistema `imagens/portal-a-2/PortalInicio-fx-1440-claro-sistema.png` · canvas `imagens/portal-a-2/PortalInicio-canvas.png`
- O que difere: rodapé. Canvas: linha única "Academia · Unidade · endereço · WhatsApp" na coluna e "feito com ctcore" à direita, com filete acima. Sistema: rodapé de borda a borda, "feito com ctcore" embaixo em segunda linha à esquerda.
- Código: `frontend/src/features/portal/PortalFooter.tsx`
- Padrão: rodapé do portal

#### B27 · Abas inferiores **[NOVO R2]**

- Lote/ID de origem: portal-a-2 · portal-a-2-12
- Categoria: ícone/avatar/cor · Largura/tema: 390 claro
- Prints: sistema `imagens/portal-a-2/PortalMTabs-390-claro-sistema.png (e barra visível em PortalInicio-erro-390-claro-sistema.png)` · canvas `imagens/portal-a-2/PortalMTabs-canvas.png`
- O que difere: "Avisos" usa ícone de megafone com contador; canvas usa sino. Aba ativa no sistema ganha traço vermelho no topo da barra; canvas marca só com ícone/rótulo mais escuros. Rótulos e ordem batem (Início, Agenda, Financeiro, Avisos, Eu).
- Código: `frontend/src/features/portal/PortalTabs.tsx:7-12`
- Padrão: ícone/estilo de aba

## 4. Regras que dependem de conferência na especificação (com as respostas)

Respostas às dúvidas que os 16 lotes deixaram, aplicando as regras R1 a R4 e as decisões de `decisoes-canvas.md`. "R2" = a API (e o contrato e o MSW) muda; "R3" = visível e desabilitado com "Em breve". Onde a especificação precisa mudar, a edição dela vem primeiro (regra do `AGENTS.md`).

### Acesso e Minha conta
- **Códigos de reserva (RN-ACE-18, RF-ACE-04):** decisão 3. A especificação não exige confirmar com senha e código ao gerar; segue o canvas (mostra os códigos novos direto). R2: o endpoint deixa de pedir a confirmação.
- **Login travado (RN-ACE-10):** o canvas mostra o horário de liberação e "Criar nova senha". R1 + R2: a resposta 429 passa a trazer o horário de liberação; o atalho de nova senha entra na tela.
- **Preferências de notificação em Minha conta (RN-NOT-03):** decisão 9. "Receber tudo" vai para Minha conta › Notificações e só aparece para o Coordenador; resumo diário e e-mails de boas-vindas ficam "Em breve".
- **Duas etapas e o Coordenador (RN-ACE-17):** decisão 4. Sem "Agora não" para o Coordenador; a janela de Minha conta mostra o botão só para os perfis opcionais.
- **Nome no convite (RF-ACE-02):** "nome e e-mail já preenchidos" e o canvas os mostra somente leitura (R1): o convidado não edita o nome ao criar a senha.

### Comunicação
- **"N de 20 enviados hoje" (RN-COM-07):** R2. A listagem e o formulário passam a trazer a contagem do dia da unidade (e a da organização para o comunicado da organização inteira); o botão desabilita no limite. O 409 do servidor continua sendo a barreira.
- **Editar agendado (RN-COM-06):** a edição vem no item #375 (Sprint 13). R3: "Editar" visível e desabilitado, "Em breve".
- **Canais Push e "Salvar rascunho":** são de sprint futura (Push real, Sprint 22). R3: o canal Push e o botão de rascunho aparecem desabilitados, "Em breve".
- **Tipo e texto iniciais do comunicado aberto pela aula (RN-COM-12, RN-AGE-05):** a especificação diz que "Avisar turma" abre o formulário já preenchido. É defeito, não dúvida: tipo "Aula cancelada" e texto do modelo da organização, com turma, data e horário. Vai para a Fase 1 do plano.
- **"Fixado até" na lista:** R2. O item de comunicado passa a trazer a data de fixação.
- **Notificações (RF-NOT):** "Receber tudo" sai do sino (decisão 9). O rodapé "As notificações somem depois de 30 dias" vira regra: **aprovada pelo dono em 30/10** e registrada como **RN-NOT-05** (30 dias, lida ou não, limpeza diária); o plano tem uma issue para a limpeza e o rodapé.
- **Seletor de unidade (RF-ORG-05):** o rótulo "Só consulta · Coordenador" para "Todas as unidades" entra como no canvas (R1); a regra de só consulta já existe.

### Configurações
- **Regras da organização na tela Organização (RF-ORG-01):** R1: os campos entram na tela. Os que já têm backend (prazo de bloqueio, acesso de ex-aluno) ficam ativos; os de funcionalidade futura (experimentais, treinar em outra unidade) ficam "Em breve" (R3).
- **Taxas por meio (RF-PAG-01):** decisão 5: modelo do sistema, mais a prévia do total para um valor de exemplo.
- **Unidade (RF-ORG-04):** fuso horário e taxa de matrícula (RN-MAT-05, nasce desligada) entram ativos; "Diretório de academias" fica "Em breve" (Sprint 20). As abas Pagamentos, Lembretes, Links e Permissões do Professor seguem o canvas.
- **Permissões do Professor (tabela de permissões):** os rótulos do canvas valem. "Editar e cancelar aulas" é o nome da linha na tabela da especificação (`02`), então substitui "Editar a agenda".
- **Unidades (RF-ORG-02, RN-ORG-05):** o subtítulo "até 10 unidades e 200 usuários" é o limite da RN-ORG-05. "Ver histórico" da unidade desativada: R3, "Em breve".
- **Convidar usuário:** o cartão "Professor" aparece como no canvas e orienta a cadastrar o Professor em Pessoas › Professores; o acesso do Professor continua sendo dado ali ("Dar acesso").
- **Lembretes automáticos (RN-COB-17):** a regra é 3 dias antes, no dia, 3 e 10 dias depois, com interruptor geral, hora e dias; "desligar um lembrete é tirar o dia da lista". O visual do canvas (um interruptor por lembrete) é só a apresentação dessa lista de dias (R4). Push: "Em breve".
- **Nova unidade:** assistente de 4 etapas do canvas (R1), com o Administrador como etapa própria.
- **Desativar unidade (RN-ACE-21):** a regra pede a senha de novo (e o código, se as duas etapas estiverem ligadas). Valem a regra e o canvas juntos: digitar "DESATIVAR" e a senha (e o código quando houver).
- **Desativar modalidade:** R2. O 409 devolve as turmas (por nome) e os alunos que impedem, não só as contagens.

### Financeiro
- **Cancelar cobrança (RN-COB-10):** a regra exige motivo; "não pode ser reaberta" não está na especificação. Vale o texto do canvas (R1).
- **Comprovante de várias cobranças (RN-COB-14):** a regra é um comprovante gravado por cobrança e decidido um a um (R4). Visual do canvas: as cobranças enviadas juntas aparecem agrupadas ("1 comprovante · 2 cobranças"), cada uma com a sua confirmação ou recusa.
- **Substituir benefício (RN-BEN-08):** a especificação já manda a janela de confirmar substituição (confirmar deixa o novo, cancelar mantém o atual). Janela própria como no canvas.
- **"Não precisa de aprovação" (benefício acima de 50%):** está na RN-BEN-06. Entra como no canvas.
- **Registrar pagamento (RN-COB-07):** o texto "não existe pagamento parcial" entra (é a regra). R2: o registro manual lista só os meios manuais (dinheiro, Pix na chave, cartão na maquininha), não os online.
- **Renegociar várias cobranças de uma vez:** **não aprovado agora** (decisão 13). Vale a especificação: uma cobrança por vez, em até 6 parcelas (RN-COB-09), com o visual do canvas adaptado a essa regra. Fica registrado como melhoria futura, que precisa entrar antes na especificação. As parcelas de renegociação contam para o bloqueio por atraso (RN-INA-01), como o canvas diz.
- **Prévia da geração (RF-COB-03):** R2. O contrato aceita todos os motivos de "não será cobrado" do canvas; a tela mostra os que o servidor emitir hoje e não quebra com chave de tradução ausente (defeito da Fase 1).
- **Plano, reajuste (RN-PLA-05):** a regra já descreve o canvas: manter ou ajustar as cobranças não pagas; cobrar ou não a diferença de reajuste nas já pagas de meses futuros; cobrança paga nunca muda. Janela "Editar plano" do canvas com o aviso de alunos afetados.
- **Plano desativado (RN-PLA-06):** a regra "reativar só se não houver plano ativo com o mesmo nome" já é imposta pelo servidor (`PlanWrite.setActive`); a tela só precisa explicá-la como no canvas. "Plano desativado não é editável": a tela bloqueia a edição.

### Operação
- **Correção de professor da aula (RN-AGE-08):** a regra dos 30 dias, só Administrador e Coordenador, com motivo, está na especificação; o texto do canvas entra.
- **Agenda do Professor (RN-AGE-07, tabela `Agenda: ver · T`):** o Professor vê só as aulas das suas turmas ou em que é substituto. O back-end já filtra; o defeito é do MSW (M85).
- **Cancelar aula com experimental marcada:** depende das experimentais (Sprint 18, #155 a #159). R3: as opções "Reagendar" e "Cancelar experimental" aparecem desabilitadas, "Em breve".
- **Dias sem aula, "Ignorar":** R1 + R2. Ignorar apenas esconde a sugestão (a especificação já diz que nada é lançado até confirmar); o contrato ganha a ação e o estado "ignorado". Precisa do texto na especificação (RF-DSA).
- **Desativar turma (RN-TUR-03):** R2. O 409 devolve a lista dos alunos que a têm como turma principal.
- **Editar turma, aviso "aulas futuras serão refeitas":** vale o aviso do canvas (R1), mostrado na edição de horário.
- **"sem limite":** texto do canvas para turma sem capacidade.

### Pessoas
- **Coluna Graduação (RF-ALU-01):** a coluna entra visível (R1); até a Sprint 12 (graduação) o conteúdo é "—". Aba/filtro "Bloqueados": R3, "Em breve" (o bloqueio por atraso é da Sprint 12). Filtro "Financeiro": ativo com a situação que já existe.
- **Ficha, Resumo:** Endereço já existe. Próximo vencimento e Termo de matrícula entram com os dados reais se a API já os tiver (cobranças e termos existem); senão, "Em breve".
- **Menu "⋯" e "Cobrar":** decisão 8.
- **Adicionar modalidade a partir da ficha (RN-MAT-01, RN-MAT-05):** o botão do canvas abre a nova matrícula do aluno ativo; o limite de 10 modalidades ativas é da RN-MAT-01.
- **Encerrar modalidade (RN-MAT-17, RN-MAT-18):** janela do canvas, com o resumo e um só motivo.
- **Exportar CSV:** Sprint 17 (#151). R3: botão visível e desabilitado, "Em breve".
- **Etapa 1:** decisão 7. **Etapa 3:** decisão 6. **Etapa 4/5:** RF-MAT-01 põe a escolha entre "Registrar pagamento agora" e "Aluno paga pelo portal" na conclusão (etapa 5); vale a regra (R4) e o aviso do termo do canvas aparece no resumo.
- **Trocar plano e transferir, motivo:** a especificação não trata o motivo dessas ações; vale o canvas (R1).
- **Professores:** situação padrão "Ativos" (R1); coluna Graduação e WhatsApp por linha entram. **Inativar (RF-PRO-04):** a especificação pede só mostrar as turmas que ficam sem titular; o canvas (motivo obrigatório e novo titular por turma na própria janela) vence no visual e, como não contraria a regra, no fluxo (R1 + R2). **Modalidade recusada (RN-PRO-09):** bloqueio preventivo na tela (Salvar desabilitado ao desmarcar) como no canvas; o 409 do servidor continua sendo a barreira.
- **Pedidos (RN-MAT-16):** decisão 1. A aba "Aguardando aprovação" (autocadastro, Sprint 18) entra como "Em breve" (R3).
- **Responsável, "Desligar":** o rótulo é o do canvas (R1). As regras seguem a especificação: o e-mail neutro ao desligado e a promoção do outro responsável a financeiro.
- **Professor e a ficha:** decisão 11.

### Portal
- **Aviso aberto (RN-COM-08):** o canvas mostra tipo, hora, autor e os atalhos "Ver na agenda" e "Abrir o grupo da turma" (R1). O grupo só aparece quando a turma tem link.
- **Confirmar nova modalidade (RN-MAT-13):** a regra é uma confirmação curta que não bloqueia: janela com "Depois", como no canvas, aberta ao entrar no Início.
- **Financeiro da família (RN-COB-14):** a tabela por unidade e a seleção múltipla entram agora (até a Sprint 11 o pagar junto envia um comprovante de Pix na chave para várias cobranças da mesma unidade); "Pagar R$ …" por Pix, cartão e boleto fica "Em breve".
- **Início, "Toda a família":** visão agregada, como no canvas.
- **Termo:** versão publicada e "Sair" entram; "Baixar PDF": R3, "Em breve".
- **Portal reduzido:** decisão 2.
- **Carteirinha:** "Baixar imagem": R3, "Em breve". O texto de recepção entra.
- **Minhas informações:** Termos com "Ver" e "Revogar" entram (revogar com confirmação); "Baixar meus dados" e "Excluir minha conta" ficam "Em breve" (LGPD, Sprint 13, #118).
- **Recibos por e-mail:** o cartão entra; se não houver preferência no servidor, fica "Em breve".
- **Pedidos (RN-MAT-16, RN-MAT-18):** decisão 1. **Pedido indisponível (RN-MAT-18):** a modalidade com encerramento aberto não aceita troca; o canvas mostra a janela explicando e a especificação diz que o servidor recusa. Vale a regra no servidor e o visual do canvas (botão visível, abre a janela de aviso) (R4).

### O que ainda depende do dono
Nada. As duas pendências foram decididas em 30/10/2026: retenção das notificações aprovada (RN-NOT-05) e renegociação de várias cobranças não aprovada agora (melhoria futura). Ver `decisoes-canvas.md`, itens 12 e 13.



### Dúvidas originais, como cada agente registrou

#### acesso

- CodigosReserva | RF-ACE-04 / RN-ACE-18 | o canvas mostra os códigos novos direto; o sistema pede senha e código antes de gerar. Confirmar se confirmar com senha e código é exigido ao gerar códigos novos (o RF-ACE-04 só cita "ver códigos de reserva novos").
- LoginTravadoM | RN-ACE-10 | o canvas mostra o horário de liberação e "Criar nova senha"; confirmar se a resposta 429 deve trazer o horário e se a trava oferece o atalho de nova senha.
- MinhaContaNotificacoes | `docs/especificacao/13-comunicacao-e-notificacoes.md` (linha 16, preferências) e RN-NOT-03 | confirmar em qual sprint a tela de preferências de Minha conta entra (RF-ACE-04 não a lista) e qual issue a cobre.
- DuasEtapas | RN-ACE-17 | canvas mostra "Agora não" para o Coordenador (obrigatória) na janela de Minha conta; confirmar se o Coordenador pode adiar nesta janela ou só na janela de primeiro acesso (`/dois-fatores`).
- PrimeiroAcessoM | RN-ACE (convite) | canvas mostra Nome e E-mail somente leitura; confirmar se o convidado pode editar o próprio nome ao criar a senha.

#### comunicacao-e-estrutura-da-gestao-1

- Comunicados (lista e Novo) | RN-COM-07 (`docs/especificacao/13-comunicacao-e-notificacoes.md:28`) | o canvas mostra "N de 20 enviados hoje" e desativa o botão no limite; `GET /announcements` (docs/openapi.yaml) não devolve a contagem do dia. Criar campo/rota ou o sistema deve só reagir ao 409?
- Comunicados (lista) | RN-COM-06 | o canvas diz que agendado "pode ser editado ou cancelado até sair"; o sistema só cancela. Edição de agendado está no escopo?
- Novo comunicado (canais) | RF-COM-02 | canvas lista Push e "Salvar rascunho"; o contrato tem só PORTAL/EMAIL/WHATSAPP e não tem rascunho. São de sprint futura?
- Novo comunicado (tipo) | RN-COM-12 | canvas já inicia com o texto pronto do tipo "Aula cancelada"; o sistema inicia em "Aviso geral" com título e texto vazios. Qual o tipo e o texto iniciais?
- Lista (Fixado até) | RF-COM-01 | `AnnouncementItem` não traz a data de fixação; a marca "Fixado até 17/10" exigiria campo novo no contrato.

#### comunicacao-e-estrutura-da-gestao-2

- Notificações | RF-NOT (em `docs/especificacao/`) | A chave "Receber tudo" na gaveta existe no sistema e não no canvas; confirmar se faz parte do requisito ou só do canvas ficar de fora. O rodapé "As notificações somem depois de 30 dias" do canvas existe como regra de retenção no sistema?
- Seletor de unidade | RF-ORG-05 / RN-ORG-04 | "Todas as unidades — só consulta" aparece no canvas como opção; confirmar se o rótulo "Só consulta · Coordenador" deve ser mostrado.

#### configuracoes-1

- ConfigAcademia | RF-ORG-01 | os campos de "Regras da organização" (prazo de bloqueio, ex-aluno, experimentais, treinar em outra unidade, comprovante de origem) pertencem a esta tela agora? O sistema não os tem.
- ConfigPagamentos | RF-PAG-01 | taxa por meio é percentual + valor fixo (canvas) ou só valor fixo em R$ como hoje? Quais meios existem (canvas 5; sistema 9)?
- ConfigUnidade | RF-ORG-04 | fuso horário, diretório e taxa de matrícula com interruptor entram agora?
- ConfigPermProf | RF-ORG-04 / tabela de permissões | rótulos oficiais das 5 permissões; "Editar e cancelar aulas" substitui "Editar a agenda"?
- ConfigUnidades | RF-ORG-02 | "Ver histórico" da unidade desativada e limite de 10 unidades/200 usuários no subtítulo.

#### configuracoes-2

- UsuarioConvidar | RN-ACE (convite) | o convite à gestão permite perfil Professor por este caminho (canvas) ou só Administrador? Canvas manda cadastrar Professor em Pessoas › Professores, mas mostra o cartão como opção.
- LembretesAuto | RF de lembretes (docs/especificacao/ cobrança) | o canal Push e a configuração por lembrete (liga/desliga individual) existem na regra? Sistema só tem liga geral e dias livres em CSV.
- UnidadeNova | RF-ORG-* | assistente tem 3 (sistema) ou 4 etapas (canvas)? Administrador é etapa própria?
- UnidadeDesativar | RN-ACE-21 | confirmação digitando "DESATIVAR" e senha (canvas) x nome da unidade + reautenticação (sistema).
- ModalidadeDesativar | RF-MOD | bloqueio mostra turmas por nome e alunos (canvas) ou só contagens (sistema)?

#### financeiro-1

- Cancelar cobrança | RN-COB-10 | "não pode ser reaberta" (sistema) é regra ou texto? O canvas diz só que sai do portal e vai para "cancelado no período".
- Comprovante de várias cobranças | RN-COB-14 | a gestão vê um comprovante agrupado ("1 comprovante · 2 cobranças") ou uma linha por cobrança?
- Em conferência | RN-COB-13 | onde fica a caixa "enviar recibo por e-mail" (padrão marcado) na tela de conferência? O sistema não mostra na linha.
- Benefícios | RN-BEN-08 | substituir exige janela própria com "Manter o atual" ou o aviso dentro da concessão basta?
- Benefícios | nota "Não precisa de aprovação" (benefício acima de 50%) | confirmar na especificação 07.

#### financeiro-2

- Registrar pagamento | RN-COB-07 | Canvas explica "não existe pagamento parcial" e que o pagamento manual não tem taxa; sistema só trava o campo. Conferir se o texto deve aparecer na janela e quais meios o registro manual pode usar (o seletor do sistema lista meios online).
- Renegociar | RN-COB (renegociação, bloqueio por atraso) | Canvas renegocia várias cobranças de uma vez e diz que as parcelas contam para o bloqueio; o sistema renegocia uma por vez. Conferir.
- Prévia da geração | RF-COB-03 | Motivos de "não será cobrado" do canvas (bloqueio por atraso, encerramento, modalidade encerrada) x contrato só com FULL_BENEFIT; decidir se entram no contrato.
- Plano (editar) | regras de reajuste de plano | Escolhas para cobranças já geradas e já pagas ("Diferença de reajuste") e aviso de alunos afetados: conferir em docs/especificacao/ (tipo READJUSTMENT_DIFF já existe no front).
- Plano desativado | regras de planos | "Reativar só se não houver outro plano ativo com o mesmo nome" e "plano desativado não é editável": conferir se o sistema impõe; sistema oferece "Ativar" direto.

#### operacao-1

- AgendaPainel / AulaCorrigir | RF-AGE-02 / correção de professor | a regra "só aulas concluídas nos últimos 30 dias, pelo Administrador ou Coordenador" aparece só no canvas; o sistema não a mostra. Está na especificação?
- GMAgendaProfessor | RF-AGE-01 | o Professor vê só as próprias aulas ou todas da unidade? (sistema mostra todas com prof@ e "Nova aula avulsa").
- AulaCancelar | RF-AEX / RF-AGE-03 | cancelar aula com experimental exige decidir (Reagendar/Cancelar experimental) antes? Depende da tela de experimentais (#156).
- AulaAvisarTurma | RF-COM-02 | o comunicado aberto a partir da aula deve vir com tipo e texto prontos (canvas) ou só destino (sistema)?
- DiasSemAula | RF-DSA | "Ignorar" feriado sugerido e visibilidade para o Coordenador com várias unidades.

#### operacao-2

- TurmaDesativar | RN-TUR-03 | canvas lista os alunos que impedem; confirmar se o OpenAPI deve devolver a lista (hoje só `blockingStudents`).
- TurmaEditar | RN-TUR (alteração de horário) | aviso "aulas futuras serão refeitas" e "Avisar a turma": valem na edição ou só na criação?
- Turmas | RN-TUR (capacidade) | rótulo "sem limite" para turma sem capacidade.

#### pessoas-1-1

- Alunos (lista) | RF-ALU-01 (`08-alunos-e-responsaveis.md:42`) | A especificação lista a coluna de graduação e diz que ela "nasce" com a matrícula (Sprint 07). A lista atual não tem a coluna Graduação; o campo `level` existe e vem nulo (D13). Confirmar se a coluna entra já ou só com as graduações (Sprint 12).
- Alunos (lista) | RF-ALU-01 | Filtro rápido "Bloqueados" e filtro "Financeiro": a especificação os põe na Sprint 08 em diante com inativação. Confirmar se faltam agora ou só na Sprint 12.
- Ficha, Resumo | RF-ALU-03 / RF-ALU-05 | O canvas mostra Endereço, Próximo vencimento e Termo de matrícula no Resumo. O Endereço já existe em `record.address`; os outros dois dependem de cobrança e termos. Confirmar quais entram agora.
- Ficha, cabeçalho | RF-ALU-03 | "Cobrar" e "⋯" no cabeçalho: confirmar o que o menu "⋯" contém (não aparece no canvas) e se "Cobrar" abre a cobrança avulsa do aluno.
- Matrícula | RN-MAT / RF-MAT (`09-matricula.md`) | Limite de "até 10 modalidades ativas" e botão "Adicionar modalidade" na ficha: confirmar se o acesso à Nova matrícula a partir da ficha de aluno Ativo faz parte do escopo (hoje só pelo assistente).
- Encerrar modalidade | RF-MAT-03, RN-MAT-17/18 | O canvas escolhe o motivo antes e mostra "Resumo"; confirmar se o motivo do cancelamento por cobrança é obrigatório ao marcar Cancelar (canvas mostra um campo único "Motivo do cancelamento *").

#### pessoas-1-2

- Alunos lista | RF-ALU-01 | "Bloqueados" é aba de situação (canvas) ou só selo? Sistema tem "Em atraso" como aba.
- Exportar CSV | RF de alunos / auditoria | Em que sprint entra a exportação? Não achei RF em docs/especificacao; confirmar.
- Assistente, etapa 1 | RF-MAT-01, RN-ALU-06 | Busca por CPF obrigatória antes do cadastro (sistema) ou cadastro direto com checagem de duplicidade (canvas)?
- Etapa 3 | RN-MAT-03, 05, 07 | Vencimento da primeira cobrança (canvas 08/10 com 5 dias de tolerância; sistema na data da matrícula): conferir.
- Etapa 4/5 | RN-MAT-19 / termo | Canvas escolhe o pagamento (portal x balcão) no resumo e mostra o aviso do termo; sistema só na conclusão. Confirmar o momento.
- Trocar plano / Transferir | RN-MAT-10, 11 | "Motivo" obrigatório no canvas (plano, transferência) e opcional na turma: confirmar.

#### pessoas-2

- Professores (lista) | RF-PRO-01/02 (`docs/especificacao/`) | Situação padrão deve ser "Ativos" (canvas) ou "Todas" (sistema)? E a coluna Graduação e o WhatsApp por linha são do escopo da lista?
- ProfessorInativar | RF-PRO-04 | A inativação do canvas pede "Motivo" obrigatório e escolha de novo titular por turma na própria janela; a especificação exige isso ou só avisa das turmas (como o sistema)? O MSW também sempre devolve `headClasses: []`.
- ProfessorModalidadeRecusa | RN-PRO-09 | O bloqueio deve ser preventivo (já ao desmarcar, Salvar desabilitado) como no canvas, ou só pelo 409 no Salvar?
- PedidosPortal | RF-MAT-04 | "Prazo"/expira em 30 dias e a aba "Aguardando aprovação" (RF-AUT-02) fazem parte da lista de Pedidos agora ou só na Sprint 18?
- ResponsavelDesligar | RF-RES-* / `08-alunos-e-responsaveis.md` | Nome da ação: "Desligar" (canvas) x "Desvincular" (sistema); e o e-mail neutro ao responsável desligado e a promoção do outro responsável a financeiro.
- FichaProfessor | RF-ALU-03 / `02-contas-e-acesso.md` | Como o Professor chega à ficha (pela turma ou pela aula)? A permissão "ver ficha do aluno" vem desligada por padrão: o canvas mostra a ficha com ela ligada?

#### portal-a-1

- Aviso aberto | RF-POR-06, RN-COM-08 | O canvas traz "Ver na agenda" e "Abrir o grupo da turma" (WhatsApp) no aviso. Checar em `docs/especificacao/` se o aviso tem tipo, link para a agenda e grupo; se não, o canvas está à frente da regra.
- Confirmar nova modalidade | RN-MAT-13 | A regra fala de "confirmação curta" que não bloqueia; o canvas a mostra como janela com "Depois". Decidir se é janela automática ao abrir o Início (canvas) ou cartão fixo (sistema).
- Financeiro família | RF-POR-05 | Pagamento somado por unidade e prévia Pix/Cartão/Boleto dependem do pagamento online (Sprint 11); confirmar se a tabela por unidade deve vir antes.

#### portal-a-2

- Início | RN-POR-04 | etiqueta e filtro por modalidade/unidade ficam no topo como seletor; o canvas esconde o filtro. Manter o seletor ou usar pílulas?
- Portal reduzido | RN-INA-08 / RN-POR-08 | o canvas do celular (PortalMReduzido) mostra as 5 abas, o do desktop só 2; a regra diz que Agenda e avisos ficam ocultos. Qual canvas prevalece?
- Início responsável | RN-POR-0x (cartão por filho) | confirmar se "Toda a família" é visão agregada do Início ou só atalho para o Financeiro.
- Termo | RN-MAT-11 / RN-POR-03 | "Baixar PDF", versão publicada e botão "Sair" do canvas fazem parte do escopo atual (issue do termo)?
- Início, aulas | experimental na turma | o canvas mostra "1 experimental na turma"; a spec de experimentais é de sprint futura?

#### portal-b-1

- Carteirinha | RF-POR-03 / RN-INA-08 | "Baixar imagem" e o texto de recepção fazem parte do RF-POR-03?
- Minhas informações | RF-POR-07 | "Minha conta" embutida e Termos com "Revogar" estão no escopo atual?
- Financeiro | RF-POR-05 | "Recibos por e-mail" como cartão e seleção múltipla com total por meio são Sprint 10 ou 11?

#### portal-b-2

- Pedidos | RF-MAT-04 / RN-MAT-16 | canvas diz "Pedidos sem resposta expiram em 30 dias"; o sistema diz que encerramento sem resposta em 7 dias é concluído na data do pedido. Confirmar prazos.
- Pedido indisponível | RN-MAT-18 | a troca deve poder ser aberta e só então avisar (canvas) ou ter os botões ocultos (sistema)?
- Portal reduzido | RN-POR-08 / RN-INA-08 | "Ver carteirinha" e a seção "Minha conta" completa pertencem a Minhas informações do reduzido?

## 5. Telas que ficaram sem comparar e por quê

### Movidas na revisão

- Estornar (financeiro-1): Estornar: botão `DISABLED = ["refund"]` em `ChargePanel.tsx:17`; é o RF-PAG-08, issue #104 (Sprint 11).
- ExportarAlunos (pessoas-1-2): Exportar CSV de alunos: funcionalidade da Sprint 17 (#151, #152). Nada em `frontend/src/features/students`.
- Telas não construídas (lote configuracoes-2) (configuracoes-2): ver lista do lote em "por lote"; entrada B10 removida do relatório.

### Por lote, como cada agente registrou

#### acesso

- GMSomenteLeitura | não construída: faixa "Somente consulta. A fatura do ctcore está em atraso…" vem da Sprint 14 (Assinatura da plataforma; `menuCatalog.ts:64` item `subscription` com `available=false`; marco "Sprint 14 · Assinatura da plataforma" tem "somente leitura" no "Pronto quando"; nenhum texto "Somente consulta" em `frontend/src`).
- SuporteAcesso | não construída: rota `/configuracoes/suporte` ausente em `App.tsx`; item `support` com `available=false` em `menuCatalog.ts:68`; issues #133 (Modo suporte: consulta, liberação pelo Coordenador e auditoria) e #134 abertas na Sprint 15.
- Telas escuras: capturei o sistema em escuro em todos os pares, mas o canvas deste lote não tem versão escura (só MainEscuro e CobrancasEscuro no índice); abri o escuro de Login, Escolha (390 e 1440), Convite, Minha conta (1440 e 360) e não vi problema de contraste; os demais escuros ficaram capturados e não abertos.
- Passo "senha + código" da janela de códigos de reserva com sucesso: a captura falhou (rótulo "Senha" ambíguo na página); o passo dos códigos foi visto pelo fluxo de ativação (`DuasEtapas-1440-claro-codigos-sistema.png`).

#### comunicacao-e-estrutura-da-gestao-1

- Ajuda (Central de ajuda) e Ajuda › Tour no primeiro acesso | não construídas: botão "Ajuda" desativado com "(Em breve)" (`Sidebar.tsx:108`, `Topbar.tsx:27`); issues #172, #173, #174 (Sprint 21).
- Busca global, Busca vazia e GMBusca | não construídas: botão "Buscar" `disabled` (`Topbar.tsx:21`); issues #125 e #126 (Sprint 13).
- Relatórios › Exportar CSV | rota `/relatorios` ausente em `App.tsx`; item "reports" sem `available`; issues #163 a #167 (Sprint 19).
- Ajuda › Falar com o suporte | não construída; issue #134 (Sprint 15).
- Barra superior › Faixas de aviso (assinatura em atraso, somente leitura, suspensa, Mercado Pago desconectado) | nenhum texto nem componente em `pt-BR.json`/`src`; dependem de assinatura e Mercado Pago (sprints posteriores). Existe só o componente genérico `components/ui/banner.tsx`.
- Início (Coordenador) e Celular › Início (Administrador, Coordenador, Professor) | o sistema mostra só "BEM-VINDO AO CTCORE" (repetido de M36); issues #122, #123, #124 (Sprint 13). Print do sistema guardado só como evidência.
- Formulário Novo comunicado no celular | o canvas não tem tela de celular dele; só print do sistema.

#### comunicacao-e-estrutura-da-gestao-2

- RelatorioConsolidado, RelatorioProfessor, GMRelatorios, Relatorios, RelatoriosAlunos, RelatoriosAulas, RelatoriosExperimentais | não construídas: sem rota `/relatorios` no `frontend/src/App.tsx`, itens `reports` e `allUnits` com `available=false` em `menuCatalog.ts`; issues #163 a #167 na Sprint 19 · Relatórios (abertas).
- Novidades | não construída: sem rota no `App.tsx`; botão "Novidades (Em breve)" desabilitado; issue #175 (RF-AJU-04), Sprint 21.
- Conteúdo do Início de Coordenador/Administrador/Professor | ver CE2-08 (issues #122 a #124, Sprint 13).

#### configuracoes-1

- AlunosImportar | não construída: sem rota em `App.tsx`; `menuCatalog.ts:66` com `available=false`; #152 e #153 abertas (Sprint 17).
- ConfigAssinatura, AssinaturaCancelar, GMAssinatura | não construídas: sem rota; `menuCatalog.ts:64`; #128 e #130 abertas (Sprint 14).
- AuditoriaPainel, GMAuditoria | não construídas: sem rota; `menuCatalog.ts:65`; #114 e #115 abertas (Sprint 13).
- ConfigLinks (URL, QR, PDF) | tela da #162, Sprint 18, aberta; comparei só a seção "Divulgação" (CF1-06).
- ConfigPagamentos (conexão do fornecedor) | #97, Sprint 11, aberta; comparei só a seção existente (CF1-04).
- Estados de erro, carregando e sem permissão destas telas: não gerados neste lote.
- Nota: na captura de página inteira do celular a barra inferior aparece no meio da imagem; é artefato da captura, não divergência.

#### configuracoes-2

- ConvitesPortal | não construída: sem rota em `App.tsx`; item da #154 (Sprint 17, aberta).
- Historico (Configurações › Auditoria) | não construída: `menuCatalog.ts:65` `available=false`, sem rota; #114/#115 (Sprint 13).
- ImportarDados, ImportarGraduacoes, ImportarPagamentos, PacoteCompleto | não construídas: `menuCatalog.ts:66` `available=false`, sem rota; #151/#152/#153 (Sprint 17).
- PermissoesTabela | tela inexistente no sistema (M24, gravidade média).
- Estados vazio/erro/carregando/sem permissão das telas do lote | só vi os do canvas que existem (bloqueios, recusa); sem permissão de Usuários já coberto pelo caso `sem-acesso` do teste visual.
- Variante 390 de UnidadeNova | capturei (`UnidadeNova-390-claro-sistema.png`), mas o canvas não tem celular desta tela; não analisei.

#### financeiro-1

- Estornar (janela) | não construído: `ChargePanel.tsx:17` desabilita "refund"; #104, Sprint 11. Só o botão desabilitado foi visto.
- Aviso de duplicidade do painel | depende do pagamento online (Sprint 11).
- Exportar CSV | #151, Sprint 17.
- Escuro de Atrasados, Conferência, painéis e modais | o canvas só tem escuro em CobrancasEscuro.

#### financeiro-2

- ExportarMensalidades (janela Exportar CSV) | não construída: nenhum "Exportar"/CSV em `frontend/src`; issue #151 "Exportação CSV e pacote completo" aberta.
- Financeiro (Visão geral) e GMFinanceiro | não construídas: sem rota `/financeiro` em App.tsx nem item no menuCatalog.ts; ligadas a RF-REL-01 (#164 aberta) e ao início da gestão (M36).
- SomenteLeitura (faixa vermelha de assinatura em atraso, "Nova cobrança" desabilitado) | não construída: somente leitura é da Sprint 14 (docs/plano-de-sprints.md:46); nenhuma ocorrência no front.
- Temas escuros de Planos, Recebimentos, Prévia, janelas e celular | canvas só tem escuro de Cobranças (CobrancasEscuro, comparado).
- Estados carregando e sem permissão (403) de Cobranças | não capturados.

#### operacao-1

- Aulas experimentais (EstadoVazio) | não construída: `/aulas-experimentais` não tem rota em `frontend/src/App.tsx` (grep sem resultado); o item `trialClasses` em `menuCatalog.ts:29` não tem `available=true`; issues abertas #156 (RF-AEX-02) e #157 (RF-AEX-03).
- Graduações (menu do canvas) | não construída (`/graduacao` sem rota; item sem `available`).
- GMAulaEditar e GMAulaCancelar a 390 | janela modal não aberta a 390 no sistema (só o painel foi capturado); comparação feita só pelo código (OP1-17).
- GMAulaProfessor | painel do Professor a 390 não capturado (prof@ só viu o desktop); só canvas lido.
- Estados carregando/erro/sem permissão da Agenda e de Dias sem aula | não capturados por fixtures (canvas não os mostra neste lote; o código tem `LoadingState`, `ErrorState` e `ForbiddenState` em `AgendaPage.tsx`).

#### operacao-2

- Aulas experimentais (Experimentais, GMExperimentais, GMExperimental, ExperimentalAgendar, ExperimentalPainel) | não construída: sem rota `/aulas-experimentais` em App.tsx; `trialClasses` sem `available` em menuCatalog.ts:29; issues #155 a #159 na Sprint 18.
- Graduações (Graduacoes, GMGraduacoes, GMGraduar, GraduacaoRegistrar, GraduacaoLote, ExportarGraduacoes) | não construída: sem rota `/graduacao` em App.tsx; `graduation` sem `available` em menuCatalog.ts:28; issues #106 (registrar), #107 (lote) na Sprint 12 e #152 (importar/exportar).

#### pessoas-1-1

- AlunoGraduacoes | aba construída só como aviso "Ainda não há o que mostrar" (`LATER = ["graduations"]` em `StudentRecordPanel.tsx:21`; `pt-BR.json:470`); a linha do tempo, "Registrar graduação" e os cartões por modalidade são das issues #106 e #108 (Sprint 12, abertas). Print do sistema em `AlunoGraduacoes-1440-claro-sistema.png` para referência.
- AlunoInativar | não construída: sem rota/botão "Inativar" na ficha; `gh issue list`: #110 (RF-INA-02) aberta, Sprint 12.
- AlunoReativar | idem, #110, Sprint 12 (e a reativação automática em #111).
- AlunoAnonimizar | não construída: sem botão nem janela; #120 (RF-LGP-04) e #121 (ações de LGPD na ficha) abertas, Sprint 13. "Baixar dados do aluno" (#117) idem.
- Aba Responsáveis com "Tornar financeiro" e limite de 2 | o MSW só tem um responsável já financeiro, então o botão e o estado de limite não foram alcançados (existe no código).

#### pessoas-1-2

- ExportarAlunos (sistema) | janela não existe (L1-01); só canvas visto.
- GMFichaResponsaveis (cartão cheio) | MSW sem menor com responsáveis; só o vazio de adulto.
- TransferirModalidade (campos após escolher destino) | sem unidade de destino com acesso no MSW.
- Estados erro/carregando/sem permissão | sem tela `Estado*` no lote; carregando visto de passagem (esqueleto da lista).

#### pessoas-2

- AguardandoAprovacao | tela de sprint futura: RF-AUT-02 é a issue #161, "Sprint 18 · Aula experimental e autocadastro"; não há rota nem componente de autocadastro/"Aguardando aprovação" em `frontend/src` (`App.tsx` só tem `/pedidos`). A aba "Aguardando aprovação 2" do canvas depende dela (ver P2-07 b).
- Tema escuro dos canvas deste lote: o `indice.json` não tem versão escura para nenhum deles; só foi tirado o print escuro do sistema (Professores e Pedidos), sem problema visível.
- Estado de erro de Pedidos: capturado (PedidosPortal-1440-claro-erro-sistema.png), não comparado por não haver canvas `Estado*` do lote.

#### portal-a-1

- PortalMEstados 390: só o estado de erro foi aberto no sistema; carregando e vazio a 390 não capturados (mesmos componentes do 1440, sem divergência de estrutura além do já relatado em PA-09).
- Tema escuro do canvas: não existe nas telas do lote; só o sistema foi capturado no escuro.

#### portal-a-2

- Janela "Trocar e-mail" | o sistema não tem a janela (formulário inline em `/conta`); registrado em portal-a-2-17.
- Estados carregando/vazio/sem permissão do Início e do Termo | o canvas do lote não traz tela para eles; erro e carregando foram capturados do sistema só como evidência (PortalInicio-erro-390, PortalInicio-carregando-390, PortalTermo-erro-1440), sem par.
- Canvas escuro | nenhuma tela do lote tem versão escura; escuro do sistema visto em Início e Termo, sem problema visível além da portal-a-2-02 (ícone de tema só some no claro) e do botão desabilitado cinza do termo, de baixo contraste mas desabilitado.
- Pagar (Pix/Cartão/Boleto, "bloqueia em", bloqueio por atraso no aviso do reduzido) | pagamento online é Sprint 11 e bloqueio por atraso Sprint 12 (RN-POR-08); diferença fica em 05 e 14 como estrutura, não como falta de regra.

#### portal-b-1

- PortalBoleto / PortalMBoleto | boleto é Sprint 11 (#101, janela #98); sem rota nem componente.
- PortalMCartaoRecusado | cartão é #100 (Sprint 11).
- PortalDebito / PortalMDebito | débito automático é #103 (Sprint 11); no sistema só o cartão informativo (PortalAutoDebitCard.tsx:7).
- PortalComprovanteVarias | o PixProofDialog existe, mas o MSW não traz cobrança em aberto para aluno@/familia@ e não montei fixture.
- PortalExcluir / PortalMExcluir | Excluir minha conta é Sprint 13 (#118); botão desabilitado.
- PortalEncerrar / PortalMEncerrar | encerramento é pedido do portal (tipo CLOSURE, PortalRequestDialog); janela não aberta no sistema.
- PortalMCarteirinha (sem conexão) | sem como simular offline no MSW (ver PB1-06).

#### portal-b-2

- PortalMInstalar | não construída: app instalável (PWA) é a #171 (aberta); sem código de instalação em `frontend/src`
- PortalMPush | não construída: Web Push real é a #178 (aberta, "depende de você")
- PortalMPagamento, PortalPagamento | não construídas: janela de pagamento no portal é a #98 (aberta); hoje "Pagar" abre só o Pix com comprovante
- PortalPagamentoErro (cartão recusado) | não construída: cartão é a #100 (aberta)
- PortalMPagamentoOk, PortalPagamentoOk | não construídas: pagamento confirmado e recibo, #102 (aberta)
- PortalMPix, PortalPix (Pix com QR code e contador) | não construídas: Pix online é a #99 (aberta)
- PortalRevogarImagem (lado do sistema) | estado não alcançável no MSW; ver portal-b-2-06

### Fora do escopo da auditoria

Telas do canvas de áreas ainda não construídas ou de documentos: Site da plataforma (desktop e celular), Painel da plataforma, E-mails e Recibo, Índices do canvas (9) e Relatórios. Não há tela no sistema para comparar; entram nas sprints de cada uma.

## Apêndice A · Pares vistos por lote

#### acesso

- LoginM | /entrar | sem login | 390 claro, 390 escuro | fixture: não
- LoginTravadoM | /entrar (e-mail ana@, senha "limite") | sem login | 390 claro, 390 escuro (capturado, não aberto) | não
- DuasEtapasM | /entrar (duas@) | sem login | 390 claro, 390 escuro (capturado, não aberto) | não
- CodigoReservaM | /entrar (duas@ > "Usar um código de reserva") | sem login | 390 claro, 390 escuro (capturado, não aberto) | não
- RecuperarSenhaM | /esqueci-senha (vazio e depois de enviar) | sem login | 390 claro, 390 escuro (capturado, não aberto) | não
- LinkSenhaVencidoM | /redefinir-senha/vencido (envia senha) | sem login | 390 claro, 390 escuro (capturado, não aberto) | não
- NovaSenhaM | /redefinir-senha/valido | sem login | 390 claro, 390 escuro (capturado, não aberto) | não
- PrimeiroAcessoM | /convite/valido | sem login | 390 claro, 390 escuro | não
- PrimeiroAcessoContaM | /convite/existente | sem login | 390 claro, 390 escuro (capturado, não aberto) | não
- ConviteVencidoM | /convite/vencido | sem login | 390 claro, 390 escuro (capturado, não aberto) | não
- EscolhaOrgM | /entrar com varios@ (vários vínculos) | varios@ | 390 claro, 390 escuro | não
- EscolhaOrganizacao | idem | varios@ | 1440 claro, 1440 escuro | não
- SemAcessoM | /configuracoes/usuarios | prof@ | 390 claro, 390 escuro (capturado, não aberto) | não
- GMSemAcesso | /configuracoes/usuarios | prof@ | 360 claro, 360 escuro (capturado, não aberto) | não
- MinhaConta | /conta | ana@ | 1440 claro, 1440 escuro | não
- MinhaContaNotificacoes | /conta (não há aba/seção de notificações) | ana@ | 1440 claro | não
- GMConta | /conta | ana@ | 360 claro, 360 escuro | não
- DuasEtapas (janela) | /conta > "Ativar duas etapas" (e passo dos códigos) | ana@ | 1440 claro (`-vp-` e `-codigos-`) | não
- CodigosReserva (janela) | /conta > "Gerar códigos de reserva novos" (duas@) | duas@ | 1440 claro (`-vp-`) | não
- GMErro | /cobrancas com `GET /api/v1/charges` em 500 | ana@ | 360 claro | sim (`fx/acesso/erro.json`)

Fixtures e passos usados: `AUD/fx/acesso/` (erro.json, duas.json, reserva.json, travado.json, vencida.json, enviado.json, ativar.json, ativar2.json, codigos.json, varios.json).

#### comunicacao-e-estrutura-da-gestao-1

- Comunicados | /comunicados | ana@ | 1440 claro (sistema x canvas), 1440 escuro (só sistema; canvas só tem claro), 390 claro (sistema x GMComunicados 360) | fixture: não
- ComunicadoNovo | /comunicados/novo | ana@ | 1440 claro (vazio e com destino escolhido), 1440 escuro (só sistema), 390 claro (sem canvas de celular do formulário) | fixture: não
- ComunicadoCancelar | /comunicados + botão "Cancelar agendamento" | ana@ | 1440 claro | fixture: não
- ComunicadoLimite | /comunicados/novo, envio com 409 `ANNOUNCEMENT_DAILY_LIMIT` | ana@ | 1440 claro | fixture: sim (`AUD/fx/comunicacao-e-estrutura-da-gestao-1/fx-limite.json`, `p-limite.json`)
- ComunicadoOrganizacao | /comunicados/novo (destino "Todas as unidades" é item do mesmo select) | ana@ | canvas visto; sistema é página, não janela (ver CE1-10)
- GMComunicados | /comunicados | ana@ | 390 claro x canvas 360
- InicioCoordenador / GMInicioCoord | / | ana@ | 1440 e 390 do sistema x canvas, só para confirmar que o Início é provisório (ver Sem comparar)
- Estrutura (menu, barra, cabeçalho) vista em todos os prints acima.

Prints: `Comunicados-1440-claro-{sistema,canvas}.png`, `Comunicados-1440-escuro-sistema.png`, `Comunicados-390-claro-sistema.png`, `GMComunicados-360-claro-canvas.png`, `ComunicadoNovo-1440-claro-{sistema,canvas}.png`, `ComunicadoNovo-1440-claro-destino-sistema.png`, `ComunicadoNovo-1440-escuro-sistema.png`, `ComunicadoNovo-390-claro-sistema.png`, `ComunicadoCancelar-1440-claro-{sistema,canvas}.png`, `ComunicadoLimite-1440-claro-{sistema,canvas}.png`, `ComunicadoOrganizacao-1440-claro-canvas.png`, `FaixasTopo-1440-claro-canvas.png`, `InicioCoordenador-1440-claro-{sistema,canvas}.png`, `GMInicioCoord-360-claro-canvas.png`, `Inicio-390-claro-sistema.png`.

#### comunicacao-e-estrutura-da-gestao-2

- Main (Início › Administrador) | `/` | admin@ | 1440 claro | fixture: não (Início ainda é a tela de boas-vindas)
- MainEscuro | `/` | admin@ | 1440 escuro | não
- ProfessorInicio | `/` | prof@ | 1440 claro | não
- Notificacoes (Sino) | `/alunos` + clique no sino | ana@ | 1440 claro e 1440 escuro (o canvas só tem a versão com o fundo claro; o escuro do sistema foi visto para contraste) | não
- Sidebar (componente) | `/alunos` | ana@ | 1440 claro (só a janela) | não
- Topbar (componente) | `/alunos` | ana@ | 1440 claro, normal e com fixture de 2 unidades (`AUD/fx/comunicacao-e-estrutura-da-gestao-2/unidades.json`) | sim (só o seletor)
- GMMenu | `/agenda` + "Menu" | prof@ (o canvas é do Administrador; Professor vê menos itens) | 390 claro | não
- GMNotificacoes | `/alunos` + sino | ana@ | 390 claro | não
- GMNovoComunicado | `/comunicados/novo` | ana@ | 390 claro | não
- GMUnidade | `/alunos` | ana@ | 390 claro, normal e com fixture de 2 unidades | sim

#### configuracoes-1

- ConfigAcademia | /configuracoes/organizacao | ana@ | 1440 claro (par), 1440 escuro (só sistema) | fixture: não
- ConfigOrgContraste | /configuracoes/organizacao + passo (cor #F5E663) | ana@ | 1440 claro | não
- ConfigUnidade | /configuracoes/unidade | ana@ | 1440 claro (par), 1440 escuro (só sistema) | não
- ConfigPagamentos | /configuracoes/unidade (seção "Pagamentos", não há aba) | ana@ | 1440 claro | não
- ConfigPermProf | /configuracoes/unidade (seção "Permissões do Professor") | ana@ | 1440 claro | não
- ConfigLinks | /configuracoes/unidade (só a seção "Divulgação" existe) | ana@ | 1440 claro | não
- GMConfigUnidade | /configuracoes/unidade | ana@ | 390 claro (canvas 360) | não
- GMConfig | sem rota própria; sistema visto em /configuracoes/organizacao a 390 só para comparar barra e menu | ana@ | 390 claro | não
- ConfigUnidades | /configuracoes/unidades | ana@ | 1440 claro (par), escuro (só sistema) | não
- ConfigModalidades | /configuracoes/modalidades | ana@ | 1440 claro (par), escuro (só sistema) | não
- ConfigModalidadesAdmin | /configuracoes/modalidades | admin@ | 1440 claro (par), escuro (só sistema, capturado) | não
- ConfigFaixas | /configuracoes/faixas-etarias | ana@ | 1440 claro (par); escuro capturado, não aberto | não
- ConfigTermos | /configuracoes/termos | ana@ | 1440 claro (par), escuro (só sistema) | não
- ConfigTextos | /configuracoes/textos | ana@ | 1440 claro (par); escuro capturado, não aberto | não
Total: 14 pares. O canvas desse lote só tem versão clara; os prints escuros são só do sistema.

#### configuracoes-2

- ConfigUsuarios | /configuracoes/usuarios | ana@ | 1440 claro, 1440 escuro (só sistema), 390 claro | fixture: não
- GMUsuarios | /configuracoes/usuarios | ana@ | 360 claro | não
- UsuarioConvidar | /configuracoes/usuarios (botão Convidar) | ana@ | 1440 claro | não
- UnidadeNova | /configuracoes/unidades/nova (passos 1, 2 e 3 do sistema) | ana@ | 1440 claro | não
- UnidadeDesativar | /configuracoes/unidades (Desativar) | ana@ | 1440 claro e escuro | sim (`fx/configuracoes-2/bloqueio.json`: 83 alunos, 5 turmas)
- UnidadeUnica | mesma janela, caso do MSW (única unidade ativa) | ana@ | 1440 claro | não
- LembretesAuto | /configuracoes/unidade (seção Lembretes, página inteira) | ana@ | 1440 claro | não
- PagamentosDesconectado | /configuracoes/unidade (seção Pagamentos) | ana@ | 1440 claro | não (o estado "desconectado" não existe no sistema)
- ModalidadesOferecidasRecusa | /configuracoes/unidade (Modalidades oferecidas) | ana@ | 1440 claro e escuro | sim (`ofer.json`: 409 UNIT_MODALITY_IN_USE)
- ModalidadeDesativar | /configuracoes/modalidades (Desativar) | ana@ | 1440 claro | sim (`mod.json`: 409 MODALITY_IN_USE 3 turmas / 63 alunos)
- ModalidadeGraduacao | /configuracoes/modalidades (Ativar, Graduação) | ana@ | 1440 claro | não
- TransferirCoordenacao | /configuracoes/organizacao (Transferir coordenação) | ana@ | 1440 claro | não

Prints em `imagens/configuracoes-2/` (`<Tela>-<largura>-<tema>-sistema.png` e `-canvas.png`; variantes com sufixo `-bloqueios`, `-passo2`, `-passo3`).

#### financeiro-1

- BeneficiosLista | /beneficios | ana@ | 1440 claro (sistema com dados do MSW), 1440 escuro só sistema | fixture: não
- BeneficioConceder | /beneficios + "Conceder benefício" | ana@ | 1440 claro | não
- BeneficioEncerrar | /beneficios + "Encerrar" na linha | ana@ | 1440 claro | não
- Atrasados | /cobrancas/atrasados | ana@ | 1440 claro (vazio do MSW e preenchido com fixture) | sim
- CobrancaAvulsa | /cobrancas + "Nova cobrança avulsa" | ana@ | 1440 claro | não (modal vazio, sem aluno)
- CobrancasEscuro (e lista clara) | /cobrancas | ana@ | 1440 escuro com fixture; claro com fixture | sim
- GMCobrancas | /cobrancas | ana@ | 390 claro (canvas 360), vazio e com fixture | sim
- CobrancaPainel | /cobrancas?cobranca=cN | ana@ | 1440 claro, cobrança paga e aberta | sim
- GMCobrancaPainel | idem | ana@ | 390 claro (aberta) | sim
- CobrancaCancelar | painel, "Cancelar cobrança" | ana@ | 1440 claro | sim
- EmConferencia | /cobrancas/conferencia | ana@ | 1440 claro (vazio e com fixture) | sim
- GMConferencia | idem | ana@ | 390 claro | sim
- EstadoErro | /cobrancas com GET /charges 500 | ana@ | 1440 claro | sim
- EstadoSemAcesso | /cobrancas | prof@ | 1440 claro | não
- Carregando (sem canvas próprio) | /cobrancas, `never:true` | ana@ | 1440 claro | sim (só sistema)

#### financeiro-2

- Mensalidades (Cobranças) | /cobrancas | ana@ | 1440 claro (vazio MSW + fixture), 1440 escuro (fixture, contra CobrancasEscuro), 390 claro (fixture), estados erro e vazio 1440 | fixture: sim
- PagamentoRegistrar | /cobrancas?cobranca=c5 > "Registrar pagamento" | ana@ | 1440 claro | fixture: sim
- GMPagamento | idem | 390 claro | fixture: sim
- Renegociar | /cobrancas?cobranca=c1 > "Renegociar" | ana@ | 1440 claro | fixture: sim
- GMRenegociar | idem | 390 claro | fixture: sim
- GerarProximas | /cobrancas > "Gerar próximas mensalidades" | ana@ | 1440 claro | fixture: sim (lista)
- Fechamento (Prévia da geração) | /cobrancas/previa | ana@ | 1440 claro (vazio MSW + fixture), erro 1440 | fixture: sim
- RecebimentosDia | /cobrancas/recebimentos | ana@ | 1440 claro (vazio MSW + fixture) | fixture: sim
- Planos | /planos | ana@ | 1440 claro | não
- PlanosRegras (Editar plano) | /planos/lista > "Editar" | ana@ | 1440 claro | não
- PlanoDesativar | /planos/lista > "Desativar" | ana@ | 1440 claro | não
- PlanoDesativado | /planos/lista (linha "Ativar", sem gaveta) | ana@ | 1440 claro | não
- GMPlanos | /planos/lista e /planos | ana@ | 390 claro | não

#### operacao-1

- Agenda | /agenda | ana@ | 1440 claro (sistema+canvas), 1440 escuro (só sistema) | fixture: não
- GMAgenda | /agenda | ana@ | 390 claro | não
- AgendaPainel | /agenda/aulas/:id | ana@ | 1440 claro (aula concluída `AgendaPainel-...-sistema`; aula futura `AgendaPainelFuturo-...-sistema`) | não
- AgendaPainelProfessor | /agenda/aulas/:id | prof@ | 1440 claro | não
- GMAgendaProfessor | /agenda | prof@ | 390 claro | não
- AulaAvisarTurma | /comunicados/novo?classId=... (aberto pelo "Avisar turma" do painel) | ana@ | 1440 claro | não
- AulaAvulsa | /agenda, botão "Nova aula avulsa" | ana@ | 1440 claro | não
- AulaCancelar | painel da aula futura, "Cancelar aula" | ana@ | 1440 claro | não
- GMAulaCancelar | painel da aula (29/10, já cancelada, por isso sem botão Cancelar) | ana@ | 390 claro: só dá para ver o painel; modal de cancelar a 390 não foi aberto (sem comparar a janela em tela cheia)
- AulaCorrigir | painel de aula concluída, "Editar aula" | ana@ | 1440 claro
- AulaEditar | painel de aula futura, "Editar aula" (`AulaEditarFuturo-...`) | ana@ | 1440 claro
- GMAulaEditar | só canvas visto; a 390 o sistema abre a mesma janela modal (não capturada a 390) | sem comparar a 390
- AulaEditarSeguintes | "Esta e as seguintes" (opção existe no `AulaEditarFuturo`) | ana@ | 1440 claro; confirmação de seguintes não aberta no sistema
- GMAulaPainel | painel a 390 (`GMAulaPainel-390-claro-sistema`) | ana@ | 390 claro (e escuro em `AgendaPainelFuturo-390-escuro-sistema`)
- GMAulaProfessor | só canvas; prof@ a 390 abre o mesmo painel desktop (não capturado) | sem comparar o painel do professor a 390
- DiaSemAulaNovo | /dias-sem-aula, "Novo dia sem aula" | ana@ | 1440 claro
- DiasSemAula | /dias-sem-aula | ana@ | 1440 claro vazio e 1440 claro com fixture `fx/operacao-1/dias.json` (`-fx-sistema`), 1440 escuro | fixture: sim
- GMDiasSemAula | /dias-sem-aula | ana@ | 390 claro (vazio e `-fx-`)
- EstadoVazio (Aulas experimentais) | sem comparar (ver abaixo)

#### operacao-2

(Todos os pares abaixo tiveram sistema e canvas abertos com Read.)
- Turmas | /turmas | ana@ | 1440 claro (+ sistema escuro 1440 e 390) | fixture: sim (lista com turma acima da capacidade; fx/operacao-2/lista.json)
- GMTurmas | /turmas | ana@ | 390 claro (canvas 360), 390 escuro só sistema | fixture: não
- TurmaPainel | /turmas/…601 | ana@ | 1440 claro | fixture: sim (painel.json: alunos, acima da capacidade, grupo)
- GMTurmaPainel | /turmas/…601 | ana@ | 390 claro | fixture: não (dados MSW)
- TurmaEditar | /turmas (Nova turma) e painel (Editar) | ana@ | 1440 claro | fixture: não
- TurmaDesativar | painel > Desativar | ana@ | 1440 claro (livre e bloqueada) | fixture: sim (bloqueada, 22 alunos principais)
- TurmaTitularInativo | painel > Editar | ana@ | 1440 claro | fixture: sim (titular.json: titular fora das opções)

#### pessoas-1-1

- Alunos | /alunos | ana@ | 1440 claro (sistema x canvas), 1440 escuro (só sistema) | fixture: sim (e sem dados, vazio, erro 500 e 403 só do sistema)
- AlunoDetalhes | /alunos/:id (aba Resumo) | ana@ | 1440 claro, 1440 escuro (só sistema) | fixture: sim (e sem dados)
- AlunoMatricula | /alunos/:id, aba "Matrícula e modalidades" | ana@ | 1440 claro | fixture: sim
- AlunoFinanceiro | aba Financeiro | ana@ | 1440 claro | fixture: sim
- AlunoGraduacoes | aba Graduações | ana@ | 1440 claro | fixture: sim (aba é só um aviso de "ainda não")
- AlunoHistorico | aba Histórico | ana@ | 1440 claro | fixture: sim
- AlunoResponsaveis | aba Responsáveis, aluno menor do MSW (702) | ana@ | 1440 claro | fixture: não (dado do MSW)
- AlunoEditar | botão "Editar dados" | ana@ | 1440 claro | fixture: sim
- EncerrarModalidade | aba Matrícula > "Encerrar" | ana@ | 1440 claro | fixture: sim (inclui `end-preview`)
- EstadoCarregando | /alunos com a lista pendente | ana@ | 1440 claro | fixture: sim (`never`)
- GMAlunos | /alunos | ana@ | 390 claro (canvas 360) | fixture: sim
- GMAlunosFiltros | /alunos (filtros) | ana@ | 390 claro (canvas 360), comparado com a lista do sistema, que não tem folha | fixture: sim
- GMAlunoEditar | "Editar dados" | ana@ | 390 claro (canvas 360) | fixture: sim
- GMEncerrar | "Encerrar" | ana@ | 390 claro (canvas 360) | fixture: sim
- AlunoInativar, AlunoReativar, AlunoAnonimizar: só o canvas foi visto (ver "Sem comparar").

Prints em `imagens/pessoas-1-1/` (`<Tela>-<largura>-<tema>-sistema|canvas|diff.png`; variações: `-semdados-`, `-erro-`, `-403-`, `-vazio-`).

#### pessoas-1-2

- EstadoSemResultados | /alunos (busca "joao pedro") | ana@ | 1440 claro | fixture: não | `EstadoSemResultados-1440-claro-{sistema,canvas}.png`
- ExportarAlunos | /alunos | ana@ | 1440 claro | sistema sem janela (não existe, ver L1-01) | só canvas + lista do sistema
- GMFicha | /alunos/:id aba Resumo | ana@ | 390 claro (canvas 360) | não | `GMFicha-390-claro-sistema.png` x `GMFicha-360-claro-canvas.png`
- GMFichaFinanceiro | aba Financeiro | ana@ | 390 | fixture: sim | `FichaFinanceiro-390-claro-sistema.png` x `GMFichaFinanceiro-360-claro-canvas.png`
- GMFichaMatricula | aba Matrícula e modalidades | ana@ | 390 | fixture: sim | `FichaMatricula-390-claro-sistema.png` x `GMFichaMatricula-360-claro-canvas.png`
- GMFichaResponsaveis | aba Responsáveis | ana@ | 390 | não (aluno sem responsável) | `FichaResponsaveis-390-claro-sistema.png` x `GMFichaResponsaveis-360-claro-canvas.png`
- GMMatricula1 | /alunos/nova-matricula etapa 1 | ana@ | 390 | não | `Wiz390-e1-sistema.png` x `GMMatricula1-360-claro-canvas.png`
- GMMatricula2 | etapa 2 | ana@ | 390 | fixture: sim | `Wiz390-e2-sistema.png` x `GMMatricula2-360-claro-canvas.png`
- GMMatricula4 | etapa 4 | ana@ | 390 | fixture: sim | `Wiz390-e4-sistema.png` x `GMMatricula4-360-claro-canvas.png`
- MatriculaAluno | etapa 1 | ana@ | 1440 | não | `MatriculaAluno-1440-claro-sistema.png`, `Wiz1440-e1-sistema.png` x canvas
- MatriculaModalidades | etapa 2 | ana@ | 1440 | sim | `Wiz1440-e2-sistema.png` x canvas
- MatriculaVencimento | etapa 3 | ana@ | 1440 | sim (taxa R$ 80 ligada) | `Wiz1440-e3-sistema.png` x canvas
- MatriculaResumo | etapa 4 | ana@ | 1440 | sim | `Wiz1440-e4-sistema.png` x canvas
- MatriculaConcluida | etapa 5 | ana@ | 1440 | sim | `Wiz1440-e5-sistema.png` x canvas
- TrocarTurma | ficha > Matrícula > Trocar turma | ana@ | 1440 | sim | `TrocarTurma-1440-claro-{sistema,canvas}.png`
- TrocarPlano | idem Trocar plano | ana@ | 1440 | sim | `TrocarPlano-1440-claro-{sistema,canvas}.png`
- TransferirModalidade | idem Transferir | ana@ | 1440 | sim | `TransferirModalidade-1440-claro-{sistema,canvas}.png`
- Apoio (ficha desktop): `Ficha-1440-claro-sistema.png`, `FichaMatricula-1440-claro-sistema.png`, `FichaFinanceiro-1440-claro-sistema.png`, `FichaResponsaveis-1440-claro-sistema.png`; escuro: `FichaMatricula-1440-escuro-sistema.png`, `Wiz1440-escuro-e3-sistema.png`.

#### pessoas-2

Todos abertos com Read (sistema e canvas). Fixtures em `AUD/fx/pessoas-2/`. Diferença de pixels não foi gerada (análise visual direta).
- Professores | /professores | ana@ | 1440 claro, 1440 escuro (só sistema), 390 claro (x GMProfessores 360) | fixture: não
- ProfessorNovo | /professores + "Novo professor" | ana@ | 1440 claro | não
- ProfessorPainel | /professores/…0502 | ana@ | 1440 claro, 390 claro (x GMProfessorPainel 360) | não
- ProfessorInativar | painel + "Inativar" | ana@ | 1440 claro | sim (`inativar-fx.json`: o MSW sempre devolve `headClasses: []`)
- ProfessorModalidadeRecusa | painel + Editar, desmarcar Judô, Salvar | ana@ | 1440 claro | sim (`mod-fx.json`: o MSW não devolve o 409 TEACHER_MODALITY_IN_USE)
- PedidosPortal | /pedidos | ana@ | 1440 claro, 1440 escuro (só sistema), 390 claro (x GMPedidos 360); estados vazio e erro (só sistema) | sim (`pedidos-fx.json`: o MSW não tem pedido em aberto)
- PedidoRecusar | /pedidos + Recusar (troca de turma) | ana@ | 1440 claro | sim
- PedidoEncerramentoRecusar | /pedidos + Recusar (encerramento) | ana@ | 1440 claro | sim
- GMPedidoRecusar | idem a 390 | ana@ | 390 claro (x 360) | sim
- PedidoAprovadoFluxo | /alunos/…0701?pedido=r1 | ana@ | 1440 claro | sim (`fluxo-fx.json`: pedido aprovado e matrícula)
- ResponsavelDesligar | ficha do aluno menor › Responsáveis › Desvincular | ana@ | 1440 claro | não
- FichaProfessor | /alunos/…0701 e /agenda | prof@ | 1440 claro | não (inalcançável, ver P2-01)

#### portal-a-1

- PortalAgenda | /portal/agenda | aluno@ | 1440 claro | fixture: não
- PortalMAgenda | /portal/agenda | aluno@ | 390 claro | fixture: não
- PortalAvisos | /portal/avisos | aluno@ | 1440 claro, 390 claro (PortalMAvisos) | fixture: não
- PortalAvisoAberto | /portal/avisos?aviso=<id> (clique no cartão) | aluno@ | 1440 claro, 390 claro (PortalMAvisoAberto) | fixture: não (passos.json `aberto.json`)
- PortalConfirmarModalidade | / | aluno@ | 1440 claro (só a janela visível, topo da página), 390 claro (página inteira) | fixture: sim (`confirm.json`, o MSW do aluno não traz pendência)
- PortalEmDia | / | aluno@ | 1440 claro | fixture: não (o aluno do MSW tem 3 avisos, 2 modalidades, cobrança em dia)
- PortalEstados | /portal/financeiro | aluno@ | 1440 claro: erro, carregando, sem acesso, vazio (4 estados, cada um aberto com fixture); 390 claro: só erro (PortalMEstados) | fixture: sim (`erro.json`, `carregando.json`, `semacesso.json`, `vazio.json`)
- PortalFamilia | /portal/financeiro?visao=familia | familia@ | 1440 claro, 390 claro (PortalMFamilia) | fixture: não
- Escuro (só sistema, canvas só tem claro): Agenda, Avisos, Família a 1440; sem problema de contraste visível. Única observação: caixas de seleção (checkbox) das cobranças ficam brancas e nativas no escuro (baixa, ver PA-12).
- Sem diferença de pixels: as telas diferem por estrutura inteira, a análise foi visual.

#### portal-a-2

- PortalInicio | / | aluno@ | 1440 claro (MSW "Tudo em dia" + fixture com cobrança vencida), 1440 escuro (sistema), 390 claro (par PortalMInicio, MSW + fixture) | fixture: sim
- PortalMInicio | / | aluno@ | 390 claro | fixture: sim
- PortalNav | /portal/financeiro | familia@ (e topo de / com aluno@) | 1440 claro | não
- PortalMTopo | /portal/minhas-informações | aluno@ | 390 claro (só topo comparável) | não
- PortalMTabs | /portal/agenda | familia@ | 390 claro | não
- PortalReduzido | / e /portal/financeiro | reduzido@ | 1440 claro | não
- PortalMReduzido | / e /portal/financeiro | reduzido@ | 390 claro | não
- PortalResponsavel | / | familia@ | 1440 claro | não
- PortalMResponsavel | / | familia@ | 390 claro | não
- PortalTermo | /portal/termo | aluno@ | 1440 claro e escuro, 390 claro (MSW "Nenhum termo" + fixture com termo pendente); erro (viewport 1440) | fixture: sim
- PortalMTermo | /portal/termo | aluno@ | 390 claro | fixture: sim
- PortalTermoFilhos | /portal/termo | familia@ | 1440 claro (fixture com 2 termos) | fixture: sim
- PortalTrocarEmail | /conta | aluno@ | 1440 claro (página; a janela não existe no sistema) | não
- Estados do Início (sem canvas): erro 390 claro visto; carregando 390 gerado.

#### portal-b-1

- PortalFinanceiro | /portal/financeiro | aluno@ | 1440 claro | fixture: não (MSW sem cobrança em aberto)
- PortalFinanceiro (variação responsável, só sistema) | /portal/financeiro | familia@ | 1440 claro | não
- PortalMFinanceiro | /portal/financeiro | aluno@ | 390 claro | não
- PortalInfo | /portal/minhas-informacoes | aluno@ | 1440 claro (+ escuro só sistema) | não
- PortalMInfo | /portal/minhas-informacoes | aluno@ | 390 claro | não
- PortalCarteirinha | /portal/carteirinha | aluno@ | 1440 claro (+ escuro só sistema) | não
- PortalMCarteirinhaOk | /portal/carteirinha | aluno@ | 390 claro | não
- PortalCarteirinhaFilhos | /portal/carteirinha | familia@ | 1440 claro | não
- PortalConta | /conta | aluno@ | 1440 claro | não
- PortalMConta | /conta | aluno@ | 390 claro | não
- PortalMEditar | /portal/minhas-informacoes, botão "Editar contato" | aluno@ | 390 claro | não
- Só canvas, abertos e lidos (sem par no sistema): PortalBoleto, PortalMBoleto, PortalMCartaoRecusado, PortalComprovanteVarias, PortalMCarteirinha. Só capturados (não lidos em detalhe): PortalDebito, PortalMDebito, PortalEncerrar, PortalMEncerrar, PortalExcluir, PortalMExcluir.

#### portal-b-2

- PortalPedidos | /portal/pedidos | aluno@ | 1440 claro (MSW vazio e com fixture `fx/portal-b-2/pedidos.json`), 1440 escuro (só sistema) | fixture: sim
- PortalMPedidos | /portal/pedidos | aluno@ | 390 claro | fixture: não (vazio no MSW; estrutura comparada também com o 1440 com fixture)
- PortalPedidoTurma | /portal/pedidos + "Pedir troca de turma" | aluno@ | 1440 claro | fixture: sim
- PortalPedidoPlano | idem + "Pedir troca de plano" | aluno@ | 1440 claro | fixture: sim
- PortalPedidoModalidade | idem + "Pedir nova modalidade" | aluno@ | 1440 claro | fixture: sim
- PortalMPedidoTurma | idem | aluno@ | 390 claro | fixture: sim
- PortalPedidoBloqueado | /portal/pedidos com encerramento aberto | aluno@ | 1440 claro | fixture: sim (`pedidos-encerramento.json`)
- PortalPixChave | /portal/financeiro + "Pagar" | aluno@ | 1440 claro | fixture: sim (`financeiro.json`)
- PortalMPixChave | idem | aluno@ | 390 claro | fixture: sim
- PortalReduzidoInfo | /portal/minhas-informacoes | reduzido@ | 1440 claro | fixture: não
Canvas só claro em todas as telas do lote.

## Apêndice B · Diferenças descartadas por virem só dos dados fictícios

#### acesso

- Nomes (Carla Mendes, Bruno Carvalho, Marina Teixeira, Juliana Andrade, Academia Leão/Team Rocha/Dojô Kaizen x Academia Alfa/Beta, Ana, Nova Pessoa), e-mails, datas (convite "vale até"), horário "21h47", "restam 9", unidades (Tijuca/Barra), contagens de sessões e "alterada há 4 meses".
- Barra inferior e escurecimento da janela deslocados na página inteira e a lista em branco do portal por trás da escolha de organização no celular, quando só aparecem por causa do tipo de captura.
- Menu "Comunicação" e "Relatórios" ausentes no sistema: já coberto por A1 (itens `available=false`).

#### comunicacao-e-estrutura-da-gestao-1

- Nome da academia ("Academia Alfa" x "Academia Leão"), usuário ("Ana"/"Coordenação" x "Carla Mendes"/"Administradora"), títulos, turmas, autores, datas (2099), contagens, destinatários/lidos e contagem do sino (4 x 3).
- Quantidade de linhas (4 x 5).
- Menu lateral que parece cortado em 1000 px nos prints de página inteira: efeito da captura.

#### comunicacao-e-estrutura-da-gestao-2

- Nome da academia (Academia Alfa x Leão), nome do usuário (Ana x Carla/Rafael), unidade (Centro x Tijuca), contagens do selo do sino (4 x 3), textos e valores das notificações (Maria Souza x Ana Beatriz), prefixo "Não lida:" não, esse é de estrutura (em CE2-03).
- Texto "Unidade: Unidade Centro" x "Unidade Tijuca" na linha de data.

#### configuracoes-1

- Nome da academia (Alfa x Leão), nomes de unidades (Centro/Norte x Tijuca/Barra/Méier), contagens de alunos, administradores, versões e aceites, datas de última alteração, usuário logado.
- Estados vazios de "Modalidades oferecidas", "Histórico de versões" e "Suas modalidades": o MSW não traz dado.

#### configuracoes-2

- Nomes de academia/pessoas (Academia Leão/Alfa, Marcos, Carla, Bruno), e-mails, contagens (131 alunos, 83 alunos, 5 turmas), datas, "Tijuca/Barra/Méier" x "Unidade Centro/Norte".
- Sidebar aparecendo no meio da imagem em prints de página inteira: artefato da captura (menu fixo).

#### financeiro-1

- Nomes de academia (Alfa x Leão), de pessoas, valores, datas, contagens (128, 11, 61) e "Unidade Unidade Centro".
- Listas vazias em /cobrancas, /cobrancas/atrasados e /cobrancas/conferencia com o MSW puro: preenchidas por fixture.
- Barra do canvas com "Carla Mendes · Administradora" x "Ana · Coordenação" (perfil do MSW); coberto em M1.

#### financeiro-2

- Academia Alfa x Leão, Ana/Coordenação x Carla Mendes/Administradora, nomes de alunos, valores, contagens (128, 11, 2, 3, 61) e datas dos canvases.
- No MSW sem fixture, lista de cobranças, recebimentos e prévia vêm vazios; resolvido com fixtures.

#### operacao-1

- Nomes da academia (Alfa x Leão), unidade (Centro x Tijuca), pessoas (Ana Souza, João Pereira x Thiago, Rafael), datas (outubro 2026 diferentes), contagens (5 aulas, 38 aulas, 22 alunos), modalidades (Judô x Jiu-Jitsu/Muay Thai), motivo "Feriado sintético".
- Lista "Alunos da aula" vazia ("Nenhum aluno ativo") e "Alunos ativos da turma 0": dado do MSW; só a estrutura foi comparada (ver OP1-08).
- Cobertura "Sem aula" na quinta 29/10 e aula cancelada: vêm do MSW (o canvas mostra outro caso).

#### operacao-2

- Nomes (Academia Alfa x Leão, Ana x Carla Mendes), turmas, professores, contagens, datas, unidades, total de turmas.

#### pessoas-1-1

- Nomes (Bruno Carvalho x Aluno Adulto Fictício), academia (Alfa x Leão), contagens (131 ativos x 2), datas, valores de cobrança e "36 anos" x "34 anos".
- Ações do histórico aparecem como código bruto (`charge.reminder`, `student.update`) só porque os códigos usados na fixture não existem no i18n; o código cai para o texto cru quando a ação é desconhecida (`RecordHistory.tsx:19-23`), comportamento previsto.
- CPF "381.204" no formulário de edição: é o valor mascarado que pus na fixture, não do MSW.
- Print de página inteira do celular mostra a barra inferior sobre as linhas da lista: efeito do print com elemento fixo.
- Tema escuro: canvas só tem claro nestas telas.
- Estados erro 500, sem permissão e vazio da lista (`Alunos-1440-claro-erro/403/vazio-sistema.png`): o canvas deste lote só tem "carregando"; os três do sistema seguem o padrão de `states` (ícone, título, texto e "Tentar de novo") e não geram divergência contra este lote.

#### pessoas-1-2

- Nome da academia (Alfa x Leão), usuária (Ana x Carla Mendes), unidade (Centro x Tijuca), nomes (Bruno, Theo), valores, datas, contagens (4 x 3 notificações, 131 ativos).

#### pessoas-2

- Nomes (Rafael Duarte x Ana Souza, Camila Ferreira etc.), contagens ("6 professores" x "3 resultados"), turmas "0" por professor, academia "Leão" x "Alfa", unidades Tijuca/Barra x Centro/Norte, telefones e datas.
- Painel do professor do MSW sem "Próximas aulas" e sem faixa/graus por falta de dados.
- "na unidade Barra" (sem nome da modalidade) em Nova modalidade: artefato da fixture sem `modality`; o MSW preenche.
- Pedido de encerramento aparece com "Abrir ficha" e não "Encerrar": é rótulo, tratado em P2-07.

#### portal-a-1

- Nome da academia (Alfa x Leão), do aluno (Ana x Bruno/Marina) e do responsável, datas (outubro x semana atual), valores (R$ 180,00 x R$ 80,00), contagem de avisos (3 x 2/6), modalidades e unidades (Centro/Norte x Tijuca/Barra), lista de aulas e quantidade de cartões nas telas; texto do aviso de exemplo.

#### portal-a-2

- Nomes (Academia Alfa/Leão, Bruno, Gabriel, Juliana, Theo, Davi, Lia), valores (R$ 160,00 x R$ 120,00), datas, contagem de avisos (2 x 3), faixas/graus exibidos, endereço e WhatsApp do rodapé.
- Início do aluno no MSW sem cobrança ("Tudo em dia"): comparado com fixture.
- Estado "Nenhum termo para aceitar" do sistema (sem tela no canvas).

#### portal-b-1

- Nome da academia/aluno, valores, datas, faixas, unidades, contagens de avisos.
- Tema escuro do sistema (PortalInfo e PortalCarteirinha, 1440, imagens/portal-b-1/*-escuro-sistema.png): legível, sem cor fixa ou contraste ruim visível; canvas só tem claro.

#### portal-b-2

- Nomes, academia, contagens do sino, valores, datas e pedidos de exemplo.
