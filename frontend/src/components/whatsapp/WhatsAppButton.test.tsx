import { readFileSync } from "node:fs";
import { render, screen } from "@testing-library/react";
import { WhatsAppButton, WhatsAppProvider } from ".";

describe("WhatsAppButton (RNF-42)", () => {
  it("CA11 RNF-42: link wa.me com mensagem codificada", () => {
    render(<WhatsAppButton phone="(11) 91234-5678" message="Olá, tudo bem?" />);
    const a = screen.getByRole("link", { name: "Abrir conversa no WhatsApp" });
    expect(a).toHaveAttribute("href", "https://wa.me/5511912345678?text=Ol%C3%A1%2C%20tudo%20bem%3F");
    expect(a).toHaveAttribute("target", "_blank");
    expect(a).toHaveAttribute("rel", "noopener noreferrer");
  });
  it("CA12 RNF-42: grupo vale e ignora a mensagem; outro domínio não", () => {
    const { rerender } = render(<WhatsAppButton groupUrl="https://chat.whatsapp.com/abc" message="oi" phone="11912345678" />);
    expect(screen.getByRole("link")).toHaveAttribute("href", "https://chat.whatsapp.com/abc");
    rerender(<WhatsAppButton groupUrl="https://evil.com/abc" />);
    expect(screen.queryByRole("link")).toBeNull();
  });
  it("CA13 RNF-42: desligado pela plataforma não renderiza e o resto fica", () => {
    render(
      <WhatsAppProvider enabled={false}>
        <p>outro</p>
        <WhatsAppButton phone="11912345678" />
      </WhatsAppProvider>,
    );
    expect(screen.queryByRole("link")).toBeNull();
    expect(screen.getByText("outro")).toBeInTheDocument();
  });
  it.each([[undefined], [""], ["123"]])("CA14 RNF-42: destino %s não aparece", (phone) => {
    render(<WhatsAppButton phone={phone} />);
    expect(screen.queryByRole("link")).toBeNull();
  });
  it("CA15 RNF-42: alvo de toque e sem texto literal", () => {
    render(<WhatsAppButton phone="5511912345678" />);
    expect(screen.getByRole("link")).toHaveClass("min-h-11", "min-w-11");
    expect(screen.getByRole("link")).toHaveAttribute("href", "https://wa.me/5511912345678");
    expect(readFileSync("src/components/whatsapp/WhatsAppButton.tsx", "utf8")).not.toMatch(/>\s*[A-Za-zÀ-ú]+\s*</);
  });
});
