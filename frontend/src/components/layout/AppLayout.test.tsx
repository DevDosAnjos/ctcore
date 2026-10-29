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
      <AppLayout groups={groups} activeId="cob" org="Alfa" roleLabel="Administrador">
        <h1>Conteúdo</h1>
      </AppLayout>
    </ThemeProvider>,
  );
}

describe("AppLayout", () => {
  it("CA1: item ativo com aria-current e grupo dele aberto; menu só no desktop", () => {
    setup();
    const nav = screen.getByRole("navigation", { name: "Menu principal", hidden: true });
    expect(within(nav).getByRole("link", { name: "Cobranças", hidden: true })).toHaveAttribute("aria-current", "page");
    expect(within(nav).queryByRole("link", { name: "Lista", hidden: true })).not.toBeInTheDocument();
    expect(within(nav).getByRole("button", { name: "Financeiro", hidden: true })).toHaveAttribute("aria-expanded", "true");
    expect(nav.closest("aside")).toHaveClass("hidden", "md:block");
  });

  it("CA2: gaveta abre com foco dentro, Esc fecha e devolve o foco ao botão", async () => {
    const user = userEvent.setup();
    setup();
    const botao = screen.getByRole("button", { name: "Menu" });
    await user.click(botao);
    const gaveta = screen.getByRole("dialog", { name: "Menu" });
    expect(gaveta).toContainElement(document.activeElement as HTMLElement);
    await user.keyboard("{Escape}");
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(botao).toHaveFocus();
  });

  it("CA21: sem largura fixa em px; abas com alvo de 56 px", () => {
    setup();
    expect(screen.getByRole("button", { name: "Menu" })).toHaveClass("min-h-14");
    expect(document.body.innerHTML).not.toMatch(/\bw-\[\d+px\]/);
  });

  it("CA14: o link de pular leva o foco ao main", async () => {
    const user = userEvent.setup();
    setup();
    await user.tab();
    expect(screen.getByRole("link", { name: "Ir para o conteúdo" })).toHaveFocus();
    await user.keyboard("{Enter}");
    expect(screen.getByRole("main")).toHaveFocus();
  });

  it("CA6: topbar com trilha, busca e ajuda desabilitadas com motivo, tema", () => {
    setup();
    const trilha = screen.getByRole("navigation", { name: "Você está em" });
    expect(within(trilha).getByText("Financeiro")).toBeInTheDocument();
    expect(within(trilha).getByText("Cobranças")).toHaveAttribute("aria-current", "page");
    expect(screen.getByRole("button", { name: "Buscar, Em breve" })).toBeDisabled();
    expect(screen.getAllByRole("button", { name: /^Ajuda, Em breve/ })[0]).toBeDisabled();
    expect(screen.getByRole("button", { name: /tema/i })).toBeInTheDocument();
  });
});
