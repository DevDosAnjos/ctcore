import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { Tabs } from "./tabs";

describe("Tabs (CA13)", () => {
  it("setas, Home e End movem foco e aba ativa; só o painel ativo aparece", async () => {
    const u = userEvent.setup();
    render(
      <Tabs
        label="Seções"
        tabs={[
          { id: "a", label: "A", content: <p>painel a</p> },
          { id: "b", label: "B", content: <p>painel b</p> },
          { id: "c", label: "C", content: <p>painel c</p> },
        ]}
      />,
    );
    const [a, b, c] = screen.getAllByRole("tab");
    expect(a).toHaveAttribute("aria-selected", "true");
    expect(screen.getByRole("tabpanel")).toHaveTextContent("painel a");
    a.focus();
    await u.keyboard("{ArrowRight}");
    expect(b).toHaveFocus();
    expect(b).toHaveAttribute("aria-selected", "true");
    expect(screen.queryByText("painel a")).not.toBeInTheDocument();
    await u.keyboard("{End}");
    expect(c).toHaveFocus();
    await u.keyboard("{ArrowRight}");
    expect(a).toHaveFocus();
    await u.keyboard("{ArrowLeft}");
    expect(c).toHaveFocus();
    await u.keyboard("{Home}");
    expect(a).toHaveFocus();
    expect(a).toHaveAttribute("tabindex", "0");
    expect(b).toHaveAttribute("tabindex", "-1");
  });
});
