import { fireEvent, render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router";
import { contrastRatio } from "@/features/organization/contrastRatio";
import { OrgMark, orgColor, orgStyle } from "./OrgMark";
import { PortalTabs } from "./PortalTabs";

describe("OrgMark e cor da organização (CA5, CA6)", () => {
  it.each([
    ["#F5E663", undefined], // claro, reprovado no contraste
    ["#1E3A8A", "#1E3A8A"], // escuro, aprovado
    [null, undefined],
    ["azul", undefined],
    ["#12345", undefined],
  ])("CA5: %s -> %s", (color, expected) => {
    expect(orgColor(color)).toBe(expected);
    if (expected) {
      expect(contrastRatio(expected)).toBeGreaterThanOrEqual(4.5);
      expect(orgStyle(color)).toEqual({ "--org": expected });
    } else expect(orgStyle(color)).toBeUndefined();
  });

  it("CA6: sem logo ou com imagem quebrada, o quadrado com as iniciais", () => {
    const { container, rerender } = render(<OrgMark name="Academia Alfa" logoUrl={null} />);
    expect(container.querySelector("[data-initials]")).toHaveAttribute("data-initials", "AA");
    expect(container.querySelector("img")).toBeNull();
    rerender(<OrgMark name="Academia Alfa" logoUrl="data:image/png;base64,AAAA" />);
    const img = container.querySelector("img")!;
    expect(img).toHaveAttribute("src", "data:image/png;base64,AAAA");
    fireEvent.error(img);
    expect(container.querySelector("img")).toBeNull();
    expect(container.querySelector("[data-initials]")).toHaveAttribute("data-initials", "AA");
  });
});

describe("PortalTabs (CA2)", () => {
  const tabs = (count: number, reduced = false) => (
    <MemoryRouter initialEntries={["/portal/financeiro"]}>
      <PortalTabs reduced={reduced} count={count} variant="bar" />
    </MemoryRouter>
  );

  it("contador 99+ acima de 99, some em zero e o nome acessível diz a contagem", () => {
    const { rerender } = render(tabs(120));
    expect(screen.getByRole("link", { name: "Avisos 99+ não lidos" })).toHaveTextContent("99+");
    rerender(tabs(2));
    expect(screen.getByRole("link", { name: "Avisos 2 não lidos" })).toHaveTextContent("2");
    rerender(tabs(0));
    expect(screen.getByRole("link", { name: "Avisos" })).toHaveTextContent(/^Avisos$/);
  });

  it("só a aba atual tem aria-current e o reduzido esconde Agenda e Avisos", () => {
    const { rerender } = render(tabs(0));
    expect(screen.getAllByRole("link").filter((a) => a.getAttribute("aria-current") === "page").map((a) => a.textContent)).toEqual(["Financeiro"]);
    rerender(tabs(0, true));
    expect(screen.getAllByRole("link").map((a) => a.textContent)).toEqual(["Início", "Financeiro", "Minhas informações"]);
  });
});
