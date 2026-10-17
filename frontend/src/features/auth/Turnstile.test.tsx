import { render, waitFor } from "@testing-library/react";
import { vi } from "vitest";
import { Turnstile } from "./Turnstile";

afterEach(() => {
  vi.unstubAllEnvs();
  delete window.turnstile;
  document.head.querySelectorAll("script").forEach((s) => s.remove());
});

describe("Turnstile (CA6)", () => {
  it("sem chave não carrega script nem chama o token", () => {
    vi.stubEnv("VITE_TURNSTILE_SITE_KEY", "");
    const onToken = vi.fn();
    render(<Turnstile onToken={onToken} resetKey={0} />);
    expect(document.head.querySelector("script")).toBeNull();
    expect(onToken).not.toHaveBeenCalled();
  });

  it("com chave carrega o script da Cloudflare, renderiza e entrega o token; reiniciar limpa e reseta", async () => {
    vi.stubEnv("VITE_TURNSTILE_SITE_KEY", "chave");
    const onToken = vi.fn();
    const reset = vi.fn();
    const renderWidget = vi.fn((_el: HTMLElement, opts: Record<string, unknown>) => {
      (opts.callback as (t: string) => void)("tok");
      return "w1";
    });
    const { rerender } = render(<Turnstile onToken={onToken} resetKey={0} />);
    const script = document.head.querySelector("script")!;
    expect(script.src).toMatch(/^https:\/\/challenges\.cloudflare\.com\//);
    window.turnstile = { render: renderWidget, reset };
    script.dispatchEvent(new Event("load"));
    await waitFor(() => expect(onToken).toHaveBeenCalledWith("tok"));
    expect(renderWidget.mock.calls[0][1]).toMatchObject({ sitekey: "chave", appearance: "interaction-only" });
    rerender(<Turnstile onToken={onToken} resetKey={1} />);
    expect(onToken).toHaveBeenLastCalledWith(null);
    expect(reset).toHaveBeenCalledWith("w1");
  });

  it("com o script já carregado renderiza direto, sem novo script", () => {
    vi.stubEnv("VITE_TURNSTILE_SITE_KEY", "chave");
    window.turnstile = { render: vi.fn(() => "w"), reset: vi.fn() };
    render(<Turnstile onToken={vi.fn()} resetKey={0} />);
    expect(window.turnstile.render).toHaveBeenCalled();
    expect(document.head.querySelector("script")).toBeNull();
  });
});
