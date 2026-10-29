// Casos do teste visual (#416). Cada issue de tela da Sprint 10.5 acrescenta os seus.
// `perfil`: e-mail do login do MSW (senha segredo123); ausente = sem login.
// `canvas`: nome da tela em docs/design/canvas (sem .dc.html); `canvasEscuro`: versão escura, quando existe.
export type Caso = {
  nome: string;
  rota: string;
  perfil?: string;
  canvas?: string;
  canvasEscuro?: string;
  fullPage?: boolean;
};

const ana = "ana@exemplo.com"; // Coordenador, todas as áreas

export const casos: Caso[] = [
  { nome: "vitrine", rota: "/dev/vitrine", fullPage: true },
  { nome: "entrar", rota: "/entrar" },
  { nome: "inicio", rota: "/", perfil: ana, canvas: "Main", canvasEscuro: "MainEscuro" },
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
];

export const larguras = [
  { nome: "1440", width: 1440, height: 1000 },
  { nome: "390", width: 390, height: 844 },
] as const;
export const temas = [
  { nome: "claro", colorScheme: "light" },
  { nome: "escuro", colorScheme: "dark" },
] as const;
