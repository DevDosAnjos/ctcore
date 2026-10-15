import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ThemeProvider } from "@/theme/ThemeProvider";
import { AppLayout, type NavGroup } from "./AppLayout";

const groups: NavGroup[] = [
  { id: "a", label: "Alunos", items: [{ id: "lista", label: "Lista", href: "/alunos" }] },
  { id: "f", label: "Financeiro", items: [{ id: "cob", label: "Cobranças", href: "/cobrancas" }] },
];

function setup() {
  return render(
    <ThemeProvider>
      <AppLayout groups={groups} activeId="cob">
        <h1>Conteúdo</h1>
      </AppLayout>
    </ThemeProvider>,
  );
}

describe("AppLayout", () => {
  it("CA1: item ativo com aria-current e grupo dele aberto", () => {
    setup();
    const nav = screen.getByRole("navigation", { name: "Menu principal", hidden: true });
    expect(within(nav).getByRole("link", { name: "Cobranças", hidden: true })).toHaveAttribute("aria-current", "page");
    expect(within(nav).getByRole("link", { name: "Lista", hidden: true })).not.toHaveAttribute("aria-current");
    const details = nav.querySelectorAll("details");
    expect(details[0]).not.toHaveAttribute("open");
    expect(details[1]).toHaveAttribute("open");
    expect(nav.closest("aside")).toHaveClass("hidden", "md:block");
  });

  it("CA2: gaveta abre com foco dentro, Esc fecha e devolve o foco ao botão", async () => {
    const user = userEvent.setup();
    setup();
    const botao = screen.getByRole("button", { name: "Abrir menu" });
    await user.click(botao);
    const gaveta = screen.getByRole("dialog", { name: "Menu" });
    expect(gaveta).toContainElement(document.activeElement as HTMLElement);
    await user.keyboard("{Escape}");
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(botao).toHaveFocus();
  });

  it("CA21: botões só de ícone têm nome e alvo de 44 px; sem largura fixa em px", () => {
    setup();
    const botao = screen.getByRole("button", { name: "Abrir menu" });
    expect(botao).toHaveClass("min-h-11", "min-w-11");
    expect(document.body.innerHTML).not.toMatch(/\bw-\[\d+px\]/);
  });
});
