import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { Sidebar } from "./Sidebar";
import type { NavGroup } from "./AppLayout";
import { useSidebarCollapsed } from "./useSidebarCollapsed";

const groups: NavGroup[] = [
  { id: "home", label: "Início", items: [{ id: "home", label: "Início", href: "/", icon: "House" }] },
  {
    id: "people",
    label: "Pessoas",
    items: [
      { id: "students", label: "Alunos", href: "/alunos", icon: "Users" },
      { id: "requests", label: "Pedidos", href: "/pedidos", icon: "Inbox" },
    ],
  },
  { id: "fin", label: "Financeiro", items: [{ id: "plans", label: "Planos", href: "/planos" }] },
];

describe("Sidebar", () => {
  it("CA1: fundo do token, organização, área, grupos com aria-expanded e item ativo", async () => {
    const user = userEvent.setup();
    const { container } = render(<Sidebar groups={groups} activeId="students" org="Alfa" roleLabel="Coordenação" />);
    expect(container.firstChild).toHaveClass("bg-sidebar");
    expect(screen.getByText("Alfa")).toBeInTheDocument();
    expect(screen.getByText("Gestão · Coordenação")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /Alunos/ })).toHaveAttribute("aria-current", "page");
    const fin = screen.getByRole("button", { name: "Financeiro" });
    expect(fin).toHaveAttribute("aria-expanded", "false");
    await user.click(fin);
    expect(fin).toHaveAttribute("aria-expanded", "true");
    expect(screen.getByRole("link", { name: "Planos" })).toBeInTheDocument();
  });

  it.each([
    [3, "3"],
    [150, "99+"],
  ])("CA2: contador %i vira %s e zero some", (n, text) => {
    const { rerender } = render(<Sidebar groups={groups} activeId="students" badges={{ requests: n }} />);
    expect(screen.getByRole("link", { name: /Pedidos/ })).toHaveTextContent(text);
    rerender(<Sidebar groups={groups} activeId="students" badges={{ requests: 0 }} />);
    expect(screen.getByRole("link", { name: "Pedidos" })).toHaveTextContent(/^Pedidos$/);
  });

  it("CA3: Ajuda e Novidades desabilitadas com motivo e assinatura do produto", () => {
    render(<Sidebar groups={groups} activeId="home" />);
    expect(screen.getByRole("button", { name: /Ajuda/ })).toBeDisabled();
    expect(screen.getByRole("button", { name: /Novidades/ })).toBeDisabled();
    expect(screen.getAllByTitle("Em breve")).toHaveLength(2);
    expect(screen.getByText("feito com ctcore")).toBeInTheDocument();
  });

  it("CA4: recolhido tem 72 px, ícones com nome e title", () => {
    const { container } = render(<Sidebar groups={groups} activeId="home" collapsed onToggle={() => {}} />);
    expect(container.firstChild).toHaveClass("w-18");
    expect(screen.getByRole("link", { name: "Alunos" })).toHaveAttribute("title", "Alunos");
    expect(screen.queryByText("Alunos")).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Expandir menu" })).toBeInTheDocument();
  });
});

function Probe() {
  const [c, toggle] = useSidebarCollapsed();
  return <button onClick={toggle}>{c ? "fechado" : "aberto"}</button>;
}

describe("useSidebarCollapsed", () => {
  beforeEach(() => localStorage.clear());
  afterEach(() => vi.restoreAllMocks());

  it("CA4: guarda a escolha e volta recolhido ao recarregar", async () => {
    const user = userEvent.setup();
    const { unmount } = render(<Probe />);
    await user.click(screen.getByRole("button", { name: "aberto" }));
    unmount();
    render(<Probe />);
    expect(screen.getByRole("button", { name: "fechado" })).toBeInTheDocument();
  });

  it("CA4: sem localStorage funciona aberto", async () => {
    const user = userEvent.setup();
    vi.spyOn(Storage.prototype, "getItem").mockImplementation(() => {
      throw new Error("bloqueado");
    });
    vi.spyOn(Storage.prototype, "setItem").mockImplementation(() => {
      throw new Error("bloqueado");
    });
    render(<Probe />);
    expect(screen.getByRole("button", { name: "aberto" })).toBeInTheDocument();
    await user.click(screen.getByRole("button"));
    expect(screen.getByRole("button", { name: "fechado" })).toBeInTheDocument();
  });
});
