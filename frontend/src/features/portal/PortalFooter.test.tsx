import { render, screen } from "@testing-library/react";
import { PortalFooter } from "./PortalFooter";

describe("PortalFooter (CA3)", () => {
  it("mostra organização, unidade, endereço, WhatsApp e feito com ctcore", () => {
    render(<PortalFooter organization="Academia Alfa" unit={{ name: "Centro", address: "Rua Fictícia, 10", whatsapp: "(21) 90000-0000" }} />);
    expect(screen.getByRole("contentinfo")).toHaveTextContent("Academia Alfa · Centro · Rua Fictícia, 10 · WhatsApp (21) 90000-0000feito com ctcore");
  });

  it("campo nulo some sem separador sobrando", () => {
    render(<PortalFooter organization="Academia Alfa" unit={{ name: "Centro", address: null, whatsapp: null }} />);
    expect(screen.getByRole("contentinfo")).toHaveTextContent("Academia Alfa · Centrofeito com ctcore");
  });

  it("unidade nula: só a organização", () => {
    render(<PortalFooter organization="Academia Alfa" unit={null} />);
    expect(screen.getByRole("contentinfo")).toHaveTextContent("Academia Alfafeito com ctcore");
  });
});
