import { render, screen } from "@testing-library/react";
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

const URL_RESET = "/api/v1/auth/password-reset/:code";

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
async function fill(user: User, password = "Senha123", confirm = password) {
  await user.type(await screen.findByLabelText("Senha"), password);
  await user.type(screen.getByLabelText("Confirmar senha"), confirm);
}

describe("ResetPasswordPage", () => {
  it("CA6: exigências trocam ao digitar, confirmação divergente avisa e o botão só habilita com senha válida e igual", async () => {
    const user = userEvent.setup();
    open("/redefinir-senha/valido");
    expect(await screen.findByLabelText("Senha")).toHaveAttribute("autocomplete", "new-password");
    await fill(user, "Senha123", "Senha124");
    expect(screen.getByText("As senhas não são iguais.")).toBeInTheDocument();
    expect(submitBtn()).toBeDisabled();
    await user.clear(screen.getByLabelText("Confirmar senha"));
    await user.type(screen.getByLabelText("Confirmar senha"), "Senha123");
    expect(submitBtn()).toBeEnabled();
    await user.clear(screen.getByLabelText("Senha"));
    await user.type(screen.getByLabelText("Senha"), "fraca");
    expect(submitBtn()).toBeDisabled();
  });

  it("CA9: aceita, guarda a sessão e vai a / sem o código na URL", async () => {
    const user = userEvent.setup();
    open("/redefinir-senha/valido");
    await fill(user);
    await user.click(submitBtn());
    expect(await screen.findByRole("heading", { name: "Bem-vindo ao ctcore" })).toBeInTheDocument();
    expect(screen.getByTestId("where")).toHaveTextContent(/^\/$/);
  });

  it("CA7: senha vazada mostra o aviso, devolve o foco e mantém o preenchimento", async () => {
    const user = userEvent.setup();
    open("/redefinir-senha/valido");
    await fill(user, "Password1");
    await user.click(submitBtn());
    expect(await screen.findByText("Essa senha é muito conhecida. Escolha outra.")).toBeInTheDocument();
    expect(screen.getByLabelText("Senha")).toHaveFocus();
    expect(screen.getByLabelText("Senha")).toHaveValue("Password1");
    expect(screen.getByLabelText("Confirmar senha")).toHaveValue("Password1");
  });

  it("CA7: senha fraca para o servidor mostra o erro com as exigências e o mesmo link serve de novo", async () => {
    const user = userEvent.setup();
    open("/redefinir-senha/fraca");
    await fill(user);
    await user.click(submitBtn());
    expect(await screen.findByText("A senha não atende às exigências abaixo.")).toBeInTheDocument();
    expect(screen.getByText("A senha precisa ter:")).toBeInTheDocument();
    expect(submitBtn()).toBeEnabled();
  });

  it.each(["vencido", "desconhecido"])("CA8: link %s (410) explica, oferece Pedir novo link e esconde o formulário", async (code) => {
    const user = userEvent.setup();
    open(`/redefinir-senha/${code}`);
    await fill(user);
    await user.click(submitBtn());
    expect(await screen.findByRole("heading", { name: "Este link não é válido ou já foi usado" })).toBeInTheDocument();
    expect(screen.getByRole("alert")).toHaveTextContent("Peça outro link");
    expect(screen.queryByLabelText("Senha")).toBeNull();
    await user.click(screen.getByRole("link", { name: "Pedir novo link" }));
    expect(await screen.findByRole("heading", { name: "Recuperar senha" })).toBeInTheDocument();
  });

  it("CA10: durante o envio o botão fica desabilitado, sem envio duplo; rede fora mostra erro e repete com os dados", async () => {
    const user = userEvent.setup();
    const calls = vi.fn();
    let release = () => {};
    const gate = new Promise<void>((r) => (release = r));
    server.use(
      http.post(URL_RESET, async () => {
        calls();
        await gate;
        return HttpResponse.error();
      }),
    );
    open("/redefinir-senha/valido");
    await fill(user);
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

  it("CA15: nem armazenamento nem console recebem o código ou a senha", async () => {
    const user = userEvent.setup();
    const spies = [
      vi.spyOn(Storage.prototype, "setItem"),
      vi.spyOn(console, "log"),
      vi.spyOn(console, "error"),
      vi.spyOn(console, "warn"),
      vi.spyOn(console, "info"),
    ];
    open("/redefinir-senha/valido");
    await fill(user);
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
    const user = userEvent.setup();
    const { container, unmount } = open("/redefinir-senha/valido");
    await screen.findByLabelText("Senha");
    expect(await axeViolations(container)).toEqual([]);
    unmount();
    const second = open("/redefinir-senha/vazada");
    await fill(user, "Senha123");
    await user.click(submitBtn());
    await screen.findByText("Essa senha é muito conhecida. Escolha outra.");
    expect(await axeViolations(second.container)).toEqual([]);
    second.unmount();
    const third = open("/redefinir-senha/vencido");
    await fill(user, "Senha123");
    await user.click(submitBtn());
    await screen.findByRole("heading", { name: "Este link não é válido ou já foi usado" });
    expect(await axeViolations(third.container)).toEqual([]);
  });
});
