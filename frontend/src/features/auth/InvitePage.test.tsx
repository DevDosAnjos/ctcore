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

const URL_INFO = "/api/v1/auth/invitations/:code";
const URL_ACCEPT = "/api/v1/auth/invitations/:code/accept";
const err = (status: number, code: string, fields: { field: string; message: string }[] = []) =>
  HttpResponse.json({ code, message: "m", changed: false, fields, traceId: "t" }, { status });

function Where() {
  const l = useLocation();
  return <p data-testid="where">{l.pathname + l.search}</p>;
}

function open(path: string) {
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

type User = ReturnType<typeof userEvent.setup>;
const submitBtn = () => screen.getByRole("button", { name: "Criar senha e entrar" });
async function fillNew(user: User, password = "Senha123", confirm = password) {
  await user.type(await screen.findByLabelText("Senha"), password);
  await user.type(screen.getByLabelText("Confirmar senha"), confirm);
}

describe("InvitePage", () => {
  it("CA1: mostra nome e e-mail somente leitura e a validade", async () => {
    open("/convite/valido");
    const email = await screen.findByLabelText("E-mail");
    expect(email).toHaveValue("novo@exemplo.com");
    expect(email).toHaveAttribute("readonly");
    expect(email).toHaveAttribute("autocomplete", "username");
    expect(screen.getByLabelText("Nome")).toHaveValue("Nova Pessoa");
    expect(screen.getByText(/Vale até 31\/12\/2026 12h30/)).toBeInTheDocument();
  });

  it("CA1: convite sem nome não mostra o campo Nome", async () => {
    server.use(http.get(URL_INFO, () => HttpResponse.json({ email: "a@x.com", name: null, expiresAt: "2026-12-31T15:30:00Z", existingAccount: false, role: "TEACHER", organizationId: "o" })));
    open("/convite/qualquer");
    await screen.findByLabelText("E-mail");
    expect(screen.queryByLabelText("Nome")).toBeNull();
  });

  it("CA4: botão só habilita com senha válida e igual", async () => {
    const user = userEvent.setup();
    open("/convite/valido");
    await fillNew(user, "fraca", "fraca");
    expect(submitBtn()).toBeDisabled();
    await user.clear(screen.getByLabelText("Senha"));
    await user.clear(screen.getByLabelText("Confirmar senha"));
    await fillNew(user, "Senha123", "Senha124");
    expect(submitBtn()).toBeDisabled();
    await user.clear(screen.getByLabelText("Confirmar senha"));
    await user.type(screen.getByLabelText("Confirmar senha"), "Senha123");
    expect(submitBtn()).toBeEnabled();
  });

  it("CA8/CA9: aceita, guarda a sessão e vai a / sem o código na URL", async () => {
    const user = userEvent.setup();
    open("/convite/valido");
    await fillNew(user);
    await user.click(submitBtn());
    expect(await screen.findByRole("heading", { name: "Bem-vindo ao ctcore" })).toBeInTheDocument();
    expect(screen.getByTestId("where")).toHaveTextContent(/^\/$/);
  });

  it("CA5: senha vazada mostra o aviso junto ao campo, devolve o foco e mantém o preenchimento", async () => {
    const user = userEvent.setup();
    open("/convite/valido");
    await fillNew(user, "Password1");
    await user.click(submitBtn());
    expect(await screen.findByText("Essa senha é muito conhecida. Escolha outra.")).toBeInTheDocument();
    expect(screen.getByLabelText("Senha")).toHaveFocus();
    expect(screen.getByLabelText("Senha")).toHaveValue("Password1");
    expect(screen.getByLabelText("Confirmar senha")).toHaveValue("Password1");
  });

  it("CA5: senha fraca para o servidor mostra o erro com as exigências", async () => {
    const user = userEvent.setup();
    server.use(http.post(URL_ACCEPT, () => err(400, "PASSWORD_WEAK")));
    open("/convite/valido");
    await fillNew(user);
    await user.click(submitBtn());
    expect(await screen.findByText("A senha não atende às exigências abaixo.")).toBeInTheDocument();
    expect(screen.getByText("A senha precisa ter:")).toBeInTheDocument();
  });

  it.each([
    ["vencido", "Este convite venceu"],
    ["usado", "Este convite já foi usado"],
    ["cancelado", "Este convite foi cancelado"],
    ["desconhecido", "O link não leva a um convite"],
  ])("CA6: código %s explica e orienta, sem formulário", async (code, text) => {
    open(`/convite/${code}`);
    expect(await screen.findByRole("heading", { name: "Este convite não é válido" })).toBeInTheDocument();
    expect(screen.getByRole("alert")).toHaveTextContent(text);
    expect(screen.getByText("Peça um novo convite a quem convidou você.")).toBeInTheDocument();
    expect(screen.queryByLabelText("Senha")).toBeNull();
    expect(screen.queryByRole("button", { name: /senha e entrar/ })).toBeNull();
  });

  it("CA6: motivo DEACTIVATED cai na explicação genérica", async () => {
    server.use(http.get(URL_INFO, () => err(410, "INVITE_INVALID", [{ field: "reason", message: "DEACTIVATED" }])));
    open("/convite/x");
    expect(await screen.findByRole("alert")).toHaveTextContent("O link não leva a um convite");
  });

  it("CA7: 410 no aceite troca para a explicação do motivo", async () => {
    const user = userEvent.setup();
    server.use(http.post(URL_ACCEPT, () => err(410, "INVITE_INVALID", [{ field: "reason", message: "USED" }])));
    open("/convite/valido");
    await fillNew(user);
    await user.click(submitBtn());
    expect(await screen.findByRole("alert")).toHaveTextContent("Este convite já foi usado");
    expect(screen.queryByLabelText("Senha")).toBeNull();
  });

  it("CA8: conta existente pede só a senha atual; 401 limpa e devolve o foco; 429 avisa", async () => {
    const user = userEvent.setup();
    open("/convite/existente");
    const campo = await screen.findByLabelText("Sua senha atual");
    expect(campo).toHaveAttribute("autocomplete", "current-password");
    expect(screen.queryByLabelText("Confirmar senha")).toBeNull();
    expect(screen.queryByText("A senha precisa ter:")).toBeNull();
    expect(screen.getByText(/entra na conta que você já tem/)).toBeInTheDocument();
    const botao = screen.getByRole("button", { name: "Aceitar convite e entrar" });
    expect(botao).toBeDisabled();
    await user.type(campo, "errada");
    expect(botao).toBeEnabled();
    await user.click(botao);
    expect(await screen.findByRole("alert")).toHaveTextContent("E-mail ou senha incorretos.");
    expect(campo).toHaveValue("");
    expect(campo).toHaveFocus();
    await user.type(campo, "limite");
    await user.click(botao);
    await waitFor(() => expect(screen.getByRole("alert")).toHaveTextContent("Muitas tentativas"));
    await user.clear(campo);
    await user.type(campo, "certa");
    await user.click(botao);
    expect(await screen.findByRole("heading", { name: "Bem-vindo ao ctcore" })).toBeInTheDocument();
  });

  it("CA10: 500 na consulta mostra erro e Tentar de novo repete", async () => {
    const user = userEvent.setup();
    server.use(http.get(URL_INFO, () => err(500, "INTERNAL_ERROR")));
    open("/convite/valido");
    const retry = await screen.findByRole("button", { name: "Tentar de novo" });
    server.resetHandlers();
    await user.click(retry);
    expect(await screen.findByLabelText("E-mail")).toHaveValue("novo@exemplo.com");
  });

  it("CA10: durante o envio o botão fica desabilitado, sem envio duplo; rede fora mostra erro e repete", async () => {
    const user = userEvent.setup();
    const calls = vi.fn();
    let release = () => {};
    const gate = new Promise<void>((r) => (release = r));
    server.use(
      http.post(URL_ACCEPT, async () => {
        calls();
        await gate;
        return HttpResponse.error();
      }),
    );
    open("/convite/valido");
    await fillNew(user);
    await user.click(submitBtn());
    const botao = await screen.findByRole("button", { name: "Entrando..." });
    expect(botao).toBeDisabled();
    await user.click(botao);
    release();
    const retry = await screen.findByRole("button", { name: "Tentar de novo" });
    server.resetHandlers();
    await user.click(retry);
    expect(await screen.findByRole("heading", { name: "Bem-vindo ao ctcore" })).toBeInTheDocument();
    expect(calls).toHaveBeenCalledTimes(1);
  });

  it("CA14: nem armazenamento nem console recebem o código ou a senha", async () => {
    const user = userEvent.setup();
    const spies = [
      vi.spyOn(Storage.prototype, "setItem"),
      vi.spyOn(console, "log"),
      vi.spyOn(console, "error"),
      vi.spyOn(console, "warn"),
      vi.spyOn(console, "info"),
    ];
    open("/convite/valido");
    await fillNew(user);
    await user.click(submitBtn());
    await screen.findByRole("heading", { name: "Bem-vindo ao ctcore" });
    const seen = JSON.stringify(spies.flatMap((s) => s.mock.calls));
    expect(seen).not.toContain("valido");
    expect(seen).not.toContain("Senha123");
    spies.forEach((s) => s.mockRestore());
  });

  it.each([
    ["claro", false],
    ["escuro", true],
  ])("CA11: sem violações no axe a 360 px no tema %s, em cada estado", async (_n, dark) => {
    setDevicePrefersDark(dark);
    window.innerWidth = 360;
    for (const code of ["valido", "existente", "vencido"]) {
      const { container, unmount } = open(`/convite/${code}`);
      await waitFor(() => expect(screen.queryByRole("status")).toBeNull());
      expect(await axeViolations(container)).toEqual([]);
      unmount();
    }
    const { container } = open("/convite/valido");
    const user = userEvent.setup();
    await fillNew(user, "Password1");
    await user.click(submitBtn());
    await screen.findByText("Essa senha é muito conhecida. Escolha outra.");
    expect(await axeViolations(container)).toEqual([]);
  });
});
