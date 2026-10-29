import { render, screen } from "@testing-library/react";
import { axeViolations } from "@/test/axe";
import { setDevicePrefersDark } from "@/test/device";
import Vitrine from "./Vitrine";

describe("Vitrine (CA23)", () => {
  it.each([
    ["claro", false],
    ["escuro", true],
  ])("sem violações no axe com o aparelho no tema %s, e mostra os dois temas", async (_n, dark) => {
    setDevicePrefersDark(dark);
    const { container } = render(<Vitrine />);
    expect(container.querySelector("[data-vitrine]")).not.toBeNull();
    expect(container.querySelector(".dark")).not.toBeNull();
    expect(screen.getAllByRole("button", { name: "primary" })).toHaveLength(2);
    expect((await axeViolations(container)).map((v) => v.id + v.nodes.map((n) => n.html.slice(0, 90)).join("|"))).toEqual([]);
  });
});
