import { render, screen } from "@testing-library/react";
import { vi } from "vitest";
import { SupportContact, platformContact } from "./SupportContact";

afterEach(() => vi.unstubAllEnvs());

describe("SupportContact (CA2)", () => {
  it("plataforma: e-mail configurado e sem WhatsApp quando a variável falta", () => {
    vi.stubEnv("VITE_SUPPORT_EMAIL", "ajuda@ctcore.test");
    vi.stubEnv("VITE_SUPPORT_WHATSAPP", "");
    render(<SupportContact contact={platformContact()} />);
    expect(screen.getByRole("link", { name: "ajuda@ctcore.test" })).toHaveAttribute("href", "mailto:ajuda@ctcore.test");
    expect(screen.queryByRole("link", { name: /WhatsApp/ })).toBeNull();
  });

  it("padrão de e-mail quando a variável falta; WhatsApp aparece quando configurado", () => {
    vi.stubEnv("VITE_SUPPORT_EMAIL", "");
    vi.stubEnv("VITE_SUPPORT_WHATSAPP", "92991234567");
    render(<SupportContact contact={platformContact()} />);
    expect(screen.getByText("suporte@ctcore.com.br")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /WhatsApp/ })).toBeInTheDocument();
  });

  it("unidade: telefone, WhatsApp e e-mail", () => {
    render(<SupportContact title="Sua unidade" contact={{ phone: "(92) 3333-4444", whatsapp: "92991234567", email: "u@x.com" }} />);
    expect(screen.getByRole("heading", { name: "Sua unidade" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "(92) 3333-4444" })).toHaveAttribute("href", "tel:9233334444");
    expect(screen.getByRole("link", { name: "u@x.com" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /WhatsApp/ })).toBeInTheDocument();
  });
});
