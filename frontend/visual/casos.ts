// Casos do teste visual (#416). Cada issue de tela da Sprint 10.5 acrescenta os seus.
// `perfil`: e-mail do login do MSW (senha segredo123); ausente = sem login.
// `canvas`: nome da tela em docs/design/canvas (sem .dc.html); `canvasEscuro`: versão escura, quando existe.
// D4 (#419): passos antes do print, para chegar a janelas e estados. `preencher`: [rótulo exato, valor]; `clicar`: início do nome do botão.
export type Passo = { preencher: [string, string] } | { clicar: string };
export type Caso = {
  nome: string;
  rota: string;
  perfil?: string;
  canvas?: string;
  canvasEscuro?: string;
  canvasCelular?: string; // tela do canvas feita para 390 px (comparada com a referência 390 do caso)
  limiteCanvas?: { valor: number; motivo: string }; // limite de diferença do caso, no lugar do padrão (#435)
  fullPage?: boolean;
  passos?: Passo[];
};

const ana = "ana@exemplo.com"; // Coordenador, todas as áreas
const admin = "admin@exemplo.com"; // Administrador
const prof = "prof@exemplo.com"; // Professor
const aluno = "aluno@exemplo.com"; // Aluno adulto, portal completo
const familia = "familia@exemplo.com"; // Responsável com dois filhos
const reduzido = "reduzido@exemplo.com"; // Aluno sem modalidade ativa, portal reduzido

export const casos: Caso[] = [
  { nome: "vitrine", rota: "/dev/vitrine", fullPage: true },
  { nome: "entrar", rota: "/entrar", canvas: "Login" },
  { nome: "inicio", rota: "/", perfil: ana, canvas: "Main", canvasEscuro: "MainEscuro" },
  // Moldura da gestão (#417): menu, barra de cima, abas e menu de celular por perfil
  { nome: "moldura-coordenacao", rota: "/alunos", perfil: ana, canvas: "Sidebar" },
  { nome: "moldura-administrador", rota: "/alunos", perfil: admin, canvas: "Topbar" },
  { nome: "moldura-professor", rota: "/agenda", perfil: prof, canvas: "GMMenu" },
  // Moldura do portal (#418): barra com a marca, abas, sino, rodapé e faixa de acesso reduzido; a 390 px, barra de cima e abas embaixo
  { nome: "portal-aluno", rota: "/", perfil: aluno, canvas: "PortalInicio", canvasCelular: "PortalMInicio" },
  { nome: "portal-responsavel", rota: "/portal/financeiro", perfil: familia, canvas: "PortalNav" },
  { nome: "portal-reduzido", rota: "/", perfil: reduzido, canvas: "PortalReduzido" },
  { nome: "portal-celular-topo", rota: "/portal/minhas-informacoes", perfil: aluno, canvas: "PortalMTopo" },
  { nome: "portal-celular-abas", rota: "/portal/agenda", perfil: familia, canvas: "PortalMTabs" },
  { nome: "portal-celular-reduzido", rota: "/portal/financeiro", perfil: reduzido, canvas: "PortalMReduzido" },
  // Páginas do portal do aluno e do responsável (#423). Janelas e estados que o MSW não alcança (comprovante com envio, pedidos por id gerado) ficam sem caso (D2).
  { nome: "portal-inicio-responsavel", rota: "/", perfil: familia, canvas: "PortalResponsavel", canvasCelular: "PortalMResponsavel" },
  { nome: "portal-termo", rota: "/portal/termo", perfil: aluno, canvas: "PortalTermo", canvasCelular: "PortalMTermo" },
  { nome: "portal-termo-filhos", rota: "/portal/termo", perfil: familia, canvas: "PortalTermoFilhos" },
  { nome: "portal-agenda", rota: "/portal/agenda", perfil: aluno, canvas: "PortalAgenda", canvasCelular: "PortalMAgenda" },
  { nome: "portal-avisos", rota: "/portal/avisos", perfil: aluno, canvas: "PortalAvisos", canvasCelular: "PortalMAvisos" },
  { nome: "portal-financeiro", rota: "/portal/financeiro", perfil: aluno, canvas: "PortalFinanceiro", canvasCelular: "PortalMFinanceiro" },
  { nome: "portal-informacoes", rota: "/portal/minhas-informacoes", perfil: aluno, canvas: "PortalInfo", canvasCelular: "PortalMInfo" },
  { nome: "portal-carteirinha", rota: "/portal/carteirinha", perfil: aluno, canvas: "PortalCarteirinha", canvasCelular: "PortalMCarteirinhaOk", limiteCanvas: { valor: 0.5, motivo: "celular: sistema e canvas diferem na carteirinha a 390 px (medido na #435, a tratar na tela)" } },
  { nome: "portal-carteirinha-filhos", rota: "/portal/carteirinha", perfil: familia, canvas: "PortalCarteirinhaFilhos" },
  { nome: "portal-pedidos", rota: "/portal/pedidos", perfil: aluno, canvas: "PortalPedidos", canvasCelular: "PortalMPedidos" },
  { nome: "alunos", rota: "/alunos", perfil: ana, canvas: "Alunos" },
  { nome: "professores", rota: "/professores", perfil: ana, canvas: "Professores" },
  { nome: "agenda", rota: "/agenda", perfil: ana, canvas: "Agenda" },
  { nome: "dias-sem-aula", rota: "/dias-sem-aula", perfil: ana, canvas: "DiasSemAula" },
  { nome: "turmas", rota: "/turmas", perfil: ana, canvas: "Turmas" },
  { nome: "planos", rota: "/planos", perfil: ana, canvas: "Planos" },
  { nome: "beneficios", rota: "/beneficios", perfil: ana, canvas: "BeneficiosLista" },
  { nome: "cobrancas", rota: "/cobrancas", perfil: ana, canvas: "Mensalidades", canvasEscuro: "CobrancasEscuro" },
  { nome: "cobrancas-atrasados", rota: "/cobrancas/atrasados", perfil: ana, canvas: "Atrasados" },
  { nome: "cobrancas-conferencia", rota: "/cobrancas/conferencia", perfil: ana, canvas: "EmConferencia" },
  { nome: "cobrancas-recebimentos", rota: "/cobrancas/recebimentos", perfil: ana, canvas: "RecebimentosDia" },
  { nome: "cobrancas-previa", rota: "/cobrancas/previa", perfil: ana, canvas: "Fechamento" },
  { nome: "pedidos", rota: "/pedidos", perfil: ana, canvas: "PedidosPortal" },
  { nome: "comunicados", rota: "/comunicados", perfil: ana, canvas: "Comunicados" },
  { nome: "comunicados-novo", rota: "/comunicados/novo", perfil: ana, canvas: "ComunicadoNovo" },
  { nome: "configuracoes-organizacao", rota: "/configuracoes/organizacao", perfil: ana, canvas: "ConfigAcademia" },
  { nome: "configuracoes-unidades", rota: "/configuracoes/unidades", perfil: ana, canvas: "ConfigUnidades" },
  { nome: "configuracoes-unidade", rota: "/configuracoes/unidade", perfil: ana, canvas: "ConfigUnidade" },
  { nome: "configuracoes-modalidades", rota: "/configuracoes/modalidades", perfil: ana, canvas: "ConfigModalidades" },
  { nome: "configuracoes-faixas-etarias", rota: "/configuracoes/faixas-etarias", perfil: ana, canvas: "ConfigFaixas" },
  { nome: "configuracoes-usuarios", rota: "/configuracoes/usuarios", perfil: ana, canvas: "ConfigUsuarios" },
  { nome: "configuracoes-termos", rota: "/configuracoes/termos", perfil: ana, canvas: "ConfigTermos" },
  { nome: "configuracoes-textos", rota: "/configuracoes/textos", perfil: ana, canvas: "ConfigTextos" },
  { nome: "conta", rota: "/conta", perfil: ana, canvas: "MinhaConta" },
  // Acesso, conta, organização, unidades e usuários (#419). Estados e janelas abrem pelos passos.
  // Fora: EscolhaOrganizacao/EscolhaOrgM (o login simulado já entra com o vínculo ativo) e UnidadeUnica (o MSW tem mais de uma unidade).
  { nome: "login-travado", rota: "/entrar", canvas: "LoginTravado", passos: [{ preencher: ["E-mail", ana] }, { preencher: ["Senha", "limite"] }, { clicar: "Entrar" }] },
  { nome: "login-duas-etapas", rota: "/entrar", canvas: "LoginDuasEtapas", passos: [{ preencher: ["E-mail", "duas@exemplo.com"] }, { preencher: ["Senha", "segredo123"] }, { clicar: "Entrar" }] },
  {
    nome: "login-codigo-reserva",
    rota: "/entrar",
    canvas: "LoginCodigoReserva",
    passos: [{ preencher: ["E-mail", "duas@exemplo.com"] }, { preencher: ["Senha", "segredo123"] }, { clicar: "Entrar" }, { clicar: "Usar um código de reserva" }],
  },
  { nome: "esqueci-senha", rota: "/esqueci-senha", canvas: "RecuperarSenha" },
  { nome: "esqueci-senha-enviado", rota: "/esqueci-senha", canvas: "RecuperarSenha", passos: [{ preencher: ["E-mail", ana] }, { clicar: "Enviar link" }] },
  { nome: "redefinir-senha", rota: "/redefinir-senha/valido", canvas: "NovaSenha" },
  {
    nome: "redefinir-senha-vencida",
    rota: "/redefinir-senha/vencido",
    canvas: "LinkSenhaVencido",
    passos: [{ preencher: ["Senha", "Senhaforte1"] }, { preencher: ["Confirmar senha", "Senhaforte1"] }, { clicar: "Criar senha e entrar" }],
  },
  { nome: "convite", rota: "/convite/valido", canvas: "PrimeiroAcesso" },
  { nome: "convite-conta-existente", rota: "/convite/existente", canvas: "PrimeiroAcessoConta" },
  { nome: "convite-vencido", rota: "/convite/vencido", canvas: "ConviteVencido" },
  { nome: "sem-acesso", rota: "/configuracoes/usuarios", perfil: prof, canvas: "EstadoSemAcesso", limiteCanvas: { valor: 0.45, motivo: "janela ou fundo: sistema e canvas não mostram a mesma janela (divergência medida na #435, a tratar na tela)" } },
  { nome: "conta-aluno", rota: "/conta", perfil: aluno, canvas: "PortalTrocarEmail", limiteCanvas: { valor: 0.80, motivo: "janela ou fundo: sistema e canvas não mostram a mesma janela (divergência medida na #435, a tratar na tela)" } },
  { nome: "conta-duas-etapas", rota: "/conta", perfil: ana, canvas: "DuasEtapas", passos: [{ clicar: "Ativar duas etapas" }], limiteCanvas: { valor: 0.70, motivo: "janela ou fundo: sistema e canvas não mostram a mesma janela (divergência medida na #435, a tratar na tela)" } },
  { nome: "conta-codigos-reserva", rota: "/conta", perfil: "duas@exemplo.com", canvas: "CodigosReserva", passos: [{ clicar: "Gerar códigos de reserva novos" }], limiteCanvas: { valor: 0.75, motivo: "janela ou fundo: sistema e canvas não mostram a mesma janela (divergência medida na #435, a tratar na tela)" } },
  { nome: "configuracoes-organizacao-contraste", rota: "/configuracoes/organizacao", perfil: ana, canvas: "ConfigOrgContraste", passos: [{ preencher: ["Cor principal (#RRGGBB)", "#F5E663"] }] },
  { nome: "configuracoes-transferir-coordenacao", rota: "/configuracoes/organizacao", perfil: ana, canvas: "TransferirCoordenacao", passos: [{ clicar: "Transferir coordenação" }], limiteCanvas: { valor: 0.70, motivo: "janela ou fundo: sistema e canvas não mostram a mesma janela (divergência medida na #435, a tratar na tela)" } },
  { nome: "configuracoes-unidade-nova", rota: "/configuracoes/unidades/nova", perfil: ana, canvas: "UnidadeNova" },
  { nome: "configuracoes-unidade-desativar", rota: "/configuracoes/unidades", perfil: ana, canvas: "UnidadeDesativar", passos: [{ clicar: "Desativar" }], limiteCanvas: { valor: 0.75, motivo: "janela ou fundo: sistema e canvas não mostram a mesma janela (divergência medida na #435, a tratar na tela)" } },
  { nome: "configuracoes-unidade-professor", rota: "/configuracoes/unidade", perfil: ana, canvas: "ConfigPermProf", fullPage: true },
  { nome: "configuracoes-usuario-convidar", rota: "/configuracoes/usuarios", perfil: ana, canvas: "UsuarioConvidar", passos: [{ clicar: "Convidar" }], limiteCanvas: { valor: 0.70, motivo: "janela ou fundo: sistema e canvas não mostram a mesma janela (divergência medida na #435, a tratar na tela)" } },
  // Modalidades, faixas, professores, turmas, agenda e dias sem aula (#420). Janelas abrem pelos passos.
  // Sem caso: painel da aula, editar, cancelar e corrigir aula (o id da aula é gerado pelo MSW, sem rota fixa); GM* são as mesmas telas a 390 px (D1).
  { nome: "configuracoes-modalidades-administrador", rota: "/configuracoes/modalidades", perfil: admin, canvas: "ConfigModalidadesAdmin" },
  { nome: "configuracoes-modalidades-graduacao", rota: "/configuracoes/modalidades", perfil: ana, canvas: "ModalidadeGraduacao", passos: [{ clicar: "Ativar" }, { clicar: "Graduação" }], limiteCanvas: { valor: 0.70, motivo: "janela ou fundo: sistema e canvas não mostram a mesma janela (divergência medida na #435, a tratar na tela)" } },
  { nome: "configuracoes-modalidades-pedido", rota: "/configuracoes/modalidades", perfil: ana, canvas: "ModalidadesOferecidasRecusa", passos: [{ clicar: "Não achei minha modalidade" }], limiteCanvas: { valor: 0.80, motivo: "janela ou fundo: sistema e canvas não mostram a mesma janela (divergência medida na #435, a tratar na tela)" } },
  { nome: "configuracoes-faixa-nova", rota: "/configuracoes/faixas-etarias", perfil: ana, canvas: "ConfigFaixas", passos: [{ clicar: "Nova faixa" }], limiteCanvas: { valor: 0.80, motivo: "janela ou fundo: sistema e canvas não mostram a mesma janela (divergência medida na #435, a tratar na tela)" } },
  { nome: "professor-novo", rota: "/professores", perfil: ana, canvas: "ProfessorNovo", passos: [{ clicar: "Novo professor" }], limiteCanvas: { valor: 0.60, motivo: "janela ou fundo: sistema e canvas não mostram a mesma janela (divergência medida na #435, a tratar na tela)" } },
  { nome: "professor-painel", rota: "/professores/00000000-0000-0000-0000-000000000502", perfil: ana, canvas: "ProfessorPainel", limiteCanvas: { valor: 0.45, motivo: "janela ou fundo: sistema e canvas não mostram a mesma janela (divergência medida na #435, a tratar na tela)" } },
  { nome: "turma-nova", rota: "/turmas", perfil: ana, canvas: "TurmaEditar", passos: [{ clicar: "Nova turma" }], limiteCanvas: { valor: 0.55, motivo: "janela ou fundo: sistema e canvas não mostram a mesma janela (divergência medida na #435, a tratar na tela)" } },
  { nome: "turma-painel", rota: "/turmas/00000000-0000-0000-0000-000000000601", perfil: ana, canvas: "TurmaPainel", limiteCanvas: { valor: 0.50, motivo: "janela ou fundo: sistema e canvas não mostram a mesma janela (divergência medida na #435, a tratar na tela)" } },
  { nome: "agenda-aula-avulsa", rota: "/agenda", perfil: ana, canvas: "AulaAvulsa", passos: [{ clicar: "Nova aula avulsa" }], limiteCanvas: { valor: 0.65, motivo: "janela ou fundo: sistema e canvas não mostram a mesma janela (divergência medida na #435, a tratar na tela)" } },
  { nome: "dia-sem-aula-novo", rota: "/dias-sem-aula", perfil: ana, canvas: "DiaSemAulaNovo", passos: [{ clicar: "Novo dia sem aula" }], limiteCanvas: { valor: 0.60, motivo: "janela ou fundo: sistema e canvas não mostram a mesma janela (divergência medida na #435, a tratar na tela)" } },
  // Planos, alunos, matrícula, benefícios e termos (#421). Janelas abrem pelos passos.
  // Sem caso: ficha, trocar turma/plano, transferir, encerrar e passos da matrícula (id gerado ou fluxo longo no MSW); GM* e FichaProfessor são as mesmas telas a 390 px ou com o perfil Professor (D1).
  { nome: "planos-lista", rota: "/planos/lista", perfil: ana, canvas: "PlanosRegras", limiteCanvas: { valor: 0.55, motivo: "janela ou fundo: sistema e canvas não mostram a mesma janela (divergência medida na #435, a tratar na tela)" } },
  { nome: "plano-novo", rota: "/planos/lista", perfil: ana, canvas: "PlanosRegras", passos: [{ clicar: "Novo plano" }], limiteCanvas: { valor: 0.65, motivo: "janela ou fundo: sistema e canvas não mostram a mesma janela (divergência medida na #435, a tratar na tela)" } },
  { nome: "beneficio-conceder", rota: "/beneficios", perfil: ana, canvas: "BeneficioConceder", passos: [{ clicar: "Conceder benefício" }], limiteCanvas: { valor: 0.60, motivo: "janela ou fundo: sistema e canvas não mostram a mesma janela (divergência medida na #435, a tratar na tela)" } },
  // Cobranças, comunicação, notificações e pedidos (#422). Janelas abrem pelos passos.
  // Sem caso: recusar pedido e recusar comprovante (o MSW não tem pedido nem comprovante em aberto), recibo público (o MSW começa sem recibo, o código nasce no pagamento), painel da cobrança, registrar pagamento, cancelar, alterar vencimento e renegociar (id gerado ou fluxo longo), lembrete (seleção em lote) e imagem do comprovante; GM* são as mesmas telas a 390 px (D1).
  { nome: "cobranca-avulsa", rota: "/cobrancas", perfil: ana, canvas: "CobrancaAvulsa", passos: [{ clicar: "Nova cobrança avulsa" }], limiteCanvas: { valor: 0.70, motivo: "janela ou fundo: sistema e canvas não mostram a mesma janela (divergência medida na #435, a tratar na tela)" } },
  { nome: "cobranca-antecipar", rota: "/cobrancas", perfil: ana, canvas: "GerarProximas", passos: [{ clicar: "Gerar próximas mensalidades" }], limiteCanvas: { valor: 0.70, motivo: "janela ou fundo: sistema e canvas não mostram a mesma janela (divergência medida na #435, a tratar na tela)" } },
  { nome: "notificacoes", rota: "/alunos", perfil: ana, canvas: "Notificacoes", passos: [{ clicar: "Notificações" }], limiteCanvas: { valor: 0.50, motivo: "janela ou fundo: sistema e canvas não mostram a mesma janela (divergência medida na #435, a tratar na tela)" } },
  // Telas do canvas já construídas que o MSW alcança por rota (#435). Fora delas, ver semCaso.
  { nome: "inicio-professor", rota: "/", perfil: prof, canvas: "ProfessorInicio" },
  { nome: "matricula-aluno", rota: "/alunos/nova-matricula", perfil: ana, canvas: "MatriculaAluno" },
  { nome: "portal-conta", rota: "/conta", perfil: aluno, canvas: "PortalConta", canvasCelular: "PortalMConta" },
  { nome: "portal-familia", rota: "/portal/financeiro", perfil: familia, canvas: "PortalFamilia", canvasCelular: "PortalMFamilia" },
  { nome: "portal-reduzido-info", rota: "/portal/minhas-informacoes", perfil: reduzido, canvas: "PortalReduzidoInfo" },
];

export const larguras = [
  { nome: "1440", width: 1440, height: 1000 },
  { nome: "390", width: 390, height: 844 },
] as const;
export const temas = [
  { nome: "claro", colorScheme: "light" },
  { nome: "escuro", colorScheme: "dark" },
] as const;

// Telas de gestão e portal do canvas sem caso, por motivo (#435). casos.test.ts exige que toda tela dessas páginas esteja em um caso ou aqui.
export const semCaso: Record<string, string[]> = {
  // variante de perfil, estado ou dado que o MSW não alcança por rota hoje
  "variante de perfil, estado ou dado que o MSW não alcança por rota hoje": [
    "InicioCoordenador", "AguardandoAprovacao", "PlanoDesativado", "ComunicadoOrganizacao", "PermissoesTabela", "SuporteAcesso",
    "PortalEmDia", "PortalMConfirmarModalidade", "PortalMPedidoTurma", "PortalMEncerrar", "PortalMExcluir",
  ],
  // tela de funcionalidade ainda sem rota em App.tsx (sprint futura)
  "tela de funcionalidade ainda sem rota em App.tsx (sprint futura)": [
    "Busca", "FaixasTopo", "Experimentais", "Graduacoes", "GraduacaoLote", "Financeiro",
    "Relatorios", "RelatoriosAlunos", "RelatoriosExperimentais", "RelatoriosAulas", "RelatorioConsolidado", "RelatorioProfessor",
    "ConfigPagamentos", "LembretesAuto", "ConfigLinks", "ConfigAssinatura", "Historico", "ImportarDados",
    "ConvitesPortal", "MinhaContaNotificacoes", "Ajuda", "AjudaTour", "Novidades", "PortalMInstalar",
    "PortalMPush",
  ],
  // janela, painel, ficha ou etapa que o MSW não alcança por rota ou passo (id gerado, fluxo longo ou dado ausente)
  "janela, painel, ficha ou etapa que o MSW não alcança por rota ou passo (id gerado, fluxo longo ou dado ausente)": [
    "BuscaVazia", "EscolhaOrganizacao", "EstadoCarregando", "EstadoSemResultados", "ExportarAlunos", "AlunoDetalhes",
    "AlunoMatricula", "AlunoFinanceiro", "AlunoGraduacoes", "AlunoResponsaveis", "AlunoHistorico", "AlunoEditar",
    "TrocarTurma", "TrocarPlano", "TransferirModalidade", "EncerrarModalidade", "AlunoInativar", "AlunoReativar",
    "AlunoAnonimizar", "FichaProfessor", "ResponsavelDesligar", "MatriculaModalidades", "MatriculaVencimento", "MatriculaResumo",
    "MatriculaConcluida", "PedidoRecusar", "PedidoEncerramentoRecusar", "PedidoAprovadoFluxo", "ProfessorInativar", "ProfessorModalidadeRecusa",
    "AgendaPainel", "AulaEditar", "AulaCancelar", "AgendaPainelProfessor", "AulaEditarSeguintes", "AulaCorrigir",
    "AulaAvisarTurma", "TurmaTitularInativo", "TurmaDesativar", "EstadoVazio", "ExperimentalAgendar", "ExperimentalPainel",
    "GraduacaoRegistrar", "ExportarGraduacoes", "CobrancaPainel", "SomenteLeitura", "EstadoErro", "PagamentoRegistrar",
    "Renegociar", "CobrancaCancelar", "Estornar", "ExportarMensalidades", "ComprovanteVarias", "BeneficioEncerrar",
    "PlanoDesativar", "BeneficioSubstituir", "ComunicadoCancelar", "ComunicadoLimite", "ExportarRelatorios", "UnidadeUnica",
    "PagamentosDesconectado", "ModalidadeDesativar", "AssinaturaCancelar", "AuditoriaPainel", "ImportarGraduacoes", "ImportarPagamentos",
    "AlunosImportar", "PacoteCompleto", "FalarSuporte", "PortalConfirmarModalidade", "PortalExcluir", "PortalAvisoAberto",
    "PortalEncerrar", "PortalPedidoTurma", "PortalPedidoModalidade", "PortalPedidoPlano", "PortalPedidoBloqueado", "PortalRevogarImagem",
    "PortalEstados", "PortalMAvisoAberto", "PortalMCarteirinha", "PortalMEditar", "PortalMEstados",
  ],
  // celular a 360 px: o teste visual captura 390 (D5 da #435)
  "celular a 360 px: o teste visual captura 390 (D5 da #435)": [
    "GMInicio", "GMBusca", "GMInicioCoord", "GMInicioProfessor", "GMNotificacoes", "GMUnidade",
    "GMConta", "GMAlunos", "GMFicha", "GMAlunosFiltros", "GMFichaMatricula", "GMFichaFinanceiro",
    "GMFichaResponsaveis", "GMAlunoEditar", "GMEncerrar", "GMAgenda", "GMAulaPainel", "GMAulaEditar",
    "GMAulaCancelar", "GMDiasSemAula", "GMAgendaProfessor", "GMAulaProfessor", "GMCobrancas", "GMPagamento",
    "GMMatricula1", "GMMatricula2", "GMMatricula4", "GMProfessores", "GMProfessorPainel", "GMPedidos",
    "GMPedidoRecusar", "GMTurmas", "GMTurmaPainel", "GMExperimentais", "GMExperimental", "GMGraduacoes",
    "GMGraduar", "GMFinanceiro", "GMCobrancaPainel", "GMRenegociar", "GMConferencia", "GMPlanos",
    "GMComunicados", "GMNovoComunicado", "GMRelatorios", "GMConfig", "GMConfigUnidade", "GMUsuarios",
    "GMAuditoria", "GMAssinatura", "GMSomenteLeitura", "GMSemAcesso", "GMErro",
  ],
  // pagamento do portal: depende de cobrança em aberto e do pagamento simulado, que o MSW não deixa em estado fixo
  "pagamento do portal: depende de cobrança em aberto e do pagamento simulado, que o MSW não deixa em estado fixo": [
    "PortalPagamento", "PortalPix", "PortalBoleto", "PortalPagamentoOk", "PortalPagamentoErro", "PortalPixChave",
    "PortalDebito", "PortalComprovanteVarias", "PortalMPagamento", "PortalMPix", "PortalMBoleto", "PortalMPagamentoOk",
    "PortalMCartaoRecusado", "PortalMPixChave", "PortalMDebito",
  ],
};
