import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { MemoryRouter, useLocation } from "react-router";
import { vi } from "vitest";
import { App } from "@/App";
import { server } from "@/mocks/server";
import { axeViolations } from "@/test/axe";
import { setDevicePrefersDark } from "@/test/device";
import { ThemeProvider } from "@/theme/ThemeProvider";
import { SessionProvider } from "./SessionProvider";

const URL_RESET = "/api/v1/auth/password-reset";
const MSG = "Se esse e-mail tiver uma conta, você vai receber um link em instantes.";

function Where() {
  const l = useLocation();
  return <p data-testid="where">{l.pathname}</p>;
}

function open(path = "/esqueci-senha") {
  return render(
    <ThemeProvider>
      <MemoryRouter initialEntries={[path]}>
        <SessionProvider>
          <App />
          <Where />
        </SessionProvider>
      </MemoryRouter>
    </ThemeProvider>,
  );
}

const send = () => screen.getByRole("button", { name: "Enviar link" });

describe("ForgotPasswordPage", () => {
  it("CA1: mostra E-mail, Enviar link e o link Entrar; o login leva até aqui", async () => {
    const user = userEvent.setup();
    open("/entrar");
    await user.click(await screen.findByRole("link", { name: "Esqueci minha senha" }));
    expect(await screen.findByRole("heading", { name: "Recuperar senha" })).toBeInTheDocument();
    expect(screen.getByLabelText("E-mail")).toHaveAttribute("autocomplete", "username");
    expect(send()).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Entrar" })).toHaveAttribute("href", "/entrar");
  });

  it("CA2: e-mail vazio avisa junto ao campo, devolve o foco e não chama a API", async () => {
    const user = userEvent.setup();
    const calls = vi.fn();
    server.use(http.post(URL_RESET, () => (calls(), HttpResponse.json({ message: MSG }))));
    open();
    await user.click(await screen.findByRole("button", { name: "Enviar link" }));
    expect(screen.getByText("Informe o seu e-mail.")).toBeInTheDocument();
    expect(screen.getByLabelText("E-mail")).toHaveFocus();
    expect(calls).not.toHaveBeenCalled();
  });

  it.each(["com@exemplo.com", "sem@exemplo.com", "desativada@exemplo.com"])("CA3: %s recebe a mesma mensagem, sem formulário", async (email) => {
    const user = userEvent.setup();
    open();
    await user.type(await screen.findByLabelText("E-mail"), email);
    await user.click(send());
    expect(await screen.findByRole("status")).toHaveTextContent(MSG);
    expect(screen.queryByLabelText("E-mail")).toBeNull();
    expect(screen.getByRole("link", { name: "Entrar" })).toBeInTheDocument();
  });

  it("CA16: 429 avisa a espera e mantém o formulário", async () => {
    const user = userEvent.setup();
    open();
    await user.type(await screen.findByLabelText("E-mail"), "limite@exemplo.com");
    await user.click(send());
    expect(await screen.findByRole("alert")).toHaveTextContent("Muitas tentativas. Espere alguns minutos e tente de novo.");
    expect(screen.getByLabelText("E-mail")).toHaveValue("limite@exemplo.com");
  });

  it("CA10: durante o envio o botão fica desabilitado, sem envio duplo; 500 mostra erro e Tentar de novo repete", async () => {
    const user = userEvent.setup();
    const calls = vi.fn();
    let release = () => {};
    const gate = new Promise<void>((r) => (release = r));
    server.use(
      http.post(URL_RESET, async () => {
        calls();
        await gate;
        return HttpResponse.json({ code: "INTERNAL_ERROR", message: "m", changed: false, fields: [], traceId: "t" }, { status: 500 });
      }),
    );
    open();
    await user.type(await screen.findByLabelText("E-mail"), "a@exemplo.com");
    await user.click(send());
    const botao = await screen.findByRole("button", { name: "Enviando..." });
    expect(botao).toBeDisabled();
    await user.click(botao);
    release();
    const retry = await screen.findByRole("button", { name: "Tentar de novo" });
    server.resetHandlers();
    await user.click(retry);
    expect(await screen.findByRole("status")).toHaveTextContent(MSG);
    expect(calls).toHaveBeenCalledTimes(1);
  });

  it("CA15: nem armazenamento nem console recebem o e-mail digitado", async () => {
    const user = userEvent.setup();
    const spies = [
      vi.spyOn(Storage.prototype, "setItem"),
      vi.spyOn(console, "log"),
      vi.spyOn(console, "error"),
      vi.spyOn(console, "warn"),
      vi.spyOn(console, "info"),
    ];
    open();
    await user.type(await screen.findByLabelText("E-mail"), "segredo@exemplo.com");
    await user.click(send());
    await screen.findByRole("status");
    expect(JSON.stringify(spies.flatMap((s) => s.mock.calls))).not.toContain("segredo");
    spies.forEach((s) => s.mockRestore());
  });

  it.each([
    ["claro", false],
    ["escuro", true],
  ])("CA11: sem violações no axe a 360 px no tema %s, em cada estado", async (_n, dark) => {
    setDevicePrefersDark(dark);
    window.innerWidth = 360;
    const user = userEvent.setup();
    const { container } = open();
    await screen.findByLabelText("E-mail");
    expect(await axeViolations(container)).toEqual([]);
    await user.click(send());
    await screen.findByText("Informe o seu e-mail.");
    expect(await axeViolations(container)).toEqual([]);
    await user.type(screen.getByLabelText("E-mail"), "limite@exemplo.com");
    await user.click(send());
    await screen.findByRole("alert");
    expect(await axeViolations(container)).toEqual([]);
    await user.clear(screen.getByLabelText("E-mail"));
    await user.type(screen.getByLabelText("E-mail"), "a@exemplo.com");
    await user.click(send());
    await waitFor(() => expect(screen.getByRole("status")).toHaveTextContent(MSG));
    expect(await axeViolations(container)).toEqual([]);
  });
});
