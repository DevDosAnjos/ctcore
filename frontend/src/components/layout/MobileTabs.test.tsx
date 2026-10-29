import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MobileTabs } from "./MobileTabs";
import type { NavGroup } from "./AppLayout";

const groups: NavGroup[] = [{ id: "home", label: "Início", items: [{ id: "home", label: "Início", href: "/" }] }];
const tabs = [
  { id: "home", label: "Início", href: "/", icon: "House" },
  { id: "agenda", label: "Agenda", href: "/agenda", icon: "CalendarDays" },
  { id: "collections", label: "Cobranças", href: "/cobrancas", icon: "Wallet", disabled: true },
];

describe("MobileTabs e MobileMenu", () => {
  it("CA9: abas com a ativa em aria-current, indisponível desabilitada e Menu por último", () => {
    render(<MobileTabs tabs={tabs} groups={groups} activeId="agenda" />);
    const nav = screen.getByRole("navigation", { name: "Abas principais" });
    expect(nav).toHaveClass("md:hidden");
    expect(within(nav).getByRole("link", { name: "Agenda" })).toHaveAttribute("aria-current", "page");
    expect(within(nav).getByRole("link", { name: "Início" })).not.toHaveAttribute("aria-current");
    expect(within(nav).getByRole("button", { name: "Cobranças" })).toBeDisabled();
    expect(within(nav).getAllByRole("button").at(-1)).toHaveAccessibleName("Menu");
  });

  it("CA10: Menu abre em tela cheia com os itens, Esc fecha e o foco volta à aba", async () => {
    const user = userEvent.setup();
    render(<MobileTabs tabs={tabs} groups={groups} activeId="home" />);
    const aba = screen.getByRole("button", { name: "Menu" });
    await user.click(aba);
    const menu = screen.getByRole("dialog", { name: "Menu" });
    expect(menu).toHaveClass("inset-0");
    expect(within(menu).getByRole("link", { name: "Início" })).toBeInTheDocument();
    expect(menu).toContainElement(document.activeElement as HTMLElement);
    await user.keyboard("{Escape}");
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(aba).toHaveFocus();
  });
});
