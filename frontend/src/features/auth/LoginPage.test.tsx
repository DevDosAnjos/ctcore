import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { MemoryRouter, useLocation } from "react-router";
import { vi } from "vitest";
import { App } from "@/App";
import { server } from "@/mocks/server";
import { axeViolations } from "@/test/axe";
import { portalShellOk } from "@/test/portalShell";
import { setDevicePrefersDark } from "@/test/device";
import { ThemeProvider } from "@/theme/ThemeProvider";
import { SessionProvider } from "./SessionProvider";

const URL_LOGIN = "/api/v1/auth/login";
const URL_SESSION = "/api/v1/auth/session";
const err = (status: number, code: string) =>
  HttpResponse.json({ code, message: "m", changed: false, fields: [], traceId: "t" }, { status });

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

async function fill(user: User) {
  await user.type(await screen.findByLabelText("E-mail"), "ana@x.com");
  await user.type(screen.getByLabelText("Senha"), "segredo123");
}

const submit = (user: User) => user.click(screen.getByRole("button", { name: "Entrar" }));

describe("LoginPage", () => {
  it("CA1: campos, link e Mostrar/Ocultar", async () => {
    const user = userEvent.setup();
    open("/entrar");
    const senha = await screen.findByLabelText("Senha");
    expect(senha).toHaveAttribute("type", "password");
    expect(senha).toHaveAttribute("autocomplete", "current-password");
    expect(screen.getByLabelText("E-mail")).toHaveAttribute("autocomplete", "username");
    expect(screen.getByRole("button", { name: "Entrar" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Esqueci minha senha" })).toHaveAttribute("href", "/esqueci-senha");
    await user.click(screen.getByRole("button", { name: "Mostrar" }));
    expect(senha).toHaveAttribute("type", "text");
    await user.click(screen.getByRole("button", { name: "Ocultar" }));
    expect(senha).toHaveAttribute("type", "password");
  });

  it("CA3: 401 mostra a mensagem única, limpa a senha e devolve o foco", async () => {
    const user = userEvent.setup();
    server.use(http.post(URL_LOGIN, () => err(401, "INVALID_CREDENTIALS")));
    open("/entrar");
    await fill(user);
    await submit(user);
    expect(await screen.findByRole("alert")).toHaveTextContent("E-mail ou senha incorretos.");
    expect(screen.getByLabelText("Senha")).toHaveValue("");
    expect(screen.getByLabelText("Senha")).toHaveFocus();
  });

  it("CA4: 429 e CHALLENGE_FAILED mostram avisos sem dizer se o e-mail existe", async () => {
    const user = userEvent.setup();
    server.use(http.post(URL_LOGIN, () => err(429, "TOO_MANY_REQUESTS")));
    open("/entrar");
    await fill(user);
    await submit(user);
    expect(await screen.findByRole("alert")).toHaveTextContent("Muitas tentativas");
    await user.type(screen.getByLabelText("Senha"), "outra");
    server.use(http.post(URL_LOGIN, () => err(400, "CHALLENGE_FAILED")));
    await submit(user);
    await waitFor(() => expect(screen.getByRole("alert")).toHaveTextContent("Tente de novo"));
    expect(screen.getByRole("alert")).not.toHaveTextContent(/existe|cadastrad/i);
  });

  it("CA5: campos vazios mostram erro por campo e não chamam a API; 500 mostra erro com Tentar de novo", async () => {
    const user = userEvent.setup();
    const calls = vi.fn();
    server.use(
      http.post(URL_LOGIN, () => {
        calls();
        return err(500, "INTERNAL_ERROR");
      }),
    );
    open("/entrar");
    await user.click(await screen.findByRole("button", { name: "Entrar" }));
    expect(screen.getByText("Informe o seu e-mail.")).toBeInTheDocument();
    expect(screen.getByText("Informe a sua senha.")).toBeInTheDocument();
    expect(calls).not.toHaveBeenCalled();
    await fill(user);
    await submit(user);
    await user.click(await screen.findByRole("button", { name: "Tentar de novo" }));
    await waitFor(() => expect(calls).toHaveBeenCalledTimes(2));
    expect(await screen.findByRole("button", { name: "Tentar de novo" })).toBeInTheDocument();
  });

  it("CA5: rede fora também mostra o erro", async () => {
    const user = userEvent.setup();
    server.use(http.post(URL_LOGIN, () => HttpResponse.error()));
    open("/entrar");
    await fill(user);
    await submit(user);
    expect(await screen.findByRole("button", { name: "Tentar de novo" })).toBeInTheDocument();
  });

  it("CA6: sem chave o corpo não traz challengeToken", async () => {
    const user = userEvent.setup();
    let body: Record<string, unknown> = {};
    server.use(
      http.post(URL_LOGIN, async ({ request }) => {
        body = (await request.json()) as typeof body;
        return err(401, "INVALID_CREDENTIALS");
      }),
    );
    open("/entrar");
    await fill(user);
    await submit(user);
    await screen.findByRole("alert");
    expect(body).toEqual({ email: "ana@x.com", password: "segredo123" });
  });

  it("CA6: com chave e script simulado o token vai no corpo", async () => {
    vi.stubEnv("VITE_TURNSTILE_SITE_KEY", "chave");
    window.turnstile = {
      render: (_el, o) => {
        (o.callback as (t: string) => void)("tok");
        return "w";
      },
      reset: vi.fn(),
    };
    const user = userEvent.setup();
    let body: Record<string, unknown> = {};
    server.use(
      http.post(URL_LOGIN, async ({ request }) => {
        body = (await request.json()) as typeof body;
        return err(401, "INVALID_CREDENTIALS");
      }),
    );
    open("/entrar");
    await fill(user);
    await submit(user);
    await screen.findByRole("alert");
    expect(body.challengeToken).toBe("tok");
    delete window.turnstile;
    vi.unstubAllEnvs();
  });

  it("CA7: login segue para next interno", async () => {
    const user = userEvent.setup();
    open("/entrar?next=%2Fesqueci-senha");
    await fill(user);
    await submit(user);
    await waitFor(() => expect(screen.getByTestId("where")).toHaveTextContent("/esqueci-senha"));
  });

  it.each(["//evil.com", "https://x"])("CA7: next=%s é ignorado", async (next) => {
    const user = userEvent.setup();
    open(`/entrar?next=${encodeURIComponent(next)}`);
    await fill(user);
    await submit(user);
    expect(await screen.findByRole("heading", { name: "Bem-vindo ao ctcore" })).toBeInTheDocument();
    expect(screen.getByTestId("where")).toHaveTextContent(/^\/$/);
  });

  it("CA7: com sessão, /entrar redireciona sem mostrar o formulário", async () => {
    server.use(portalShellOk, http.get(URL_SESSION, () => HttpResponse.json({ account: { id: "1", email: "a@x.com" }, links: [], twoFactor: { enabled: false, required: false } })));
    open("/entrar");
    expect(await screen.findByRole("heading", { name: "Bem-vindo ao ctcore" })).toBeInTheDocument();
    expect(screen.queryByLabelText("Senha")).toBeNull();
  });

  it("CA7: sem sessão, / vai a /entrar?next=%2F", async () => {
    open("/");
    await screen.findByLabelText("Senha");
    expect(screen.getByTestId("where")).toHaveTextContent("/entrar?next=%2F");
  });

  it("falha ao verificar a sessão mostra erro e Tentar de novo recarrega", async () => {
    const user = userEvent.setup();
    server.use(http.get(URL_SESSION, () => err(500, "INTERNAL_ERROR")));
    open("/");
    const retry = await screen.findByRole("button", { name: "Tentar de novo" });
    server.use(http.get(URL_SESSION, () => err(401, "UNAUTHENTICATED")));
    await user.click(retry);
    expect(await screen.findByLabelText("Senha")).toBeInTheDocument();
  });

  it("CA8: enquanto envia o botão fica desabilitado, diz Entrando... e não duplica o envio", async () => {
    const user = userEvent.setup();
    const calls = vi.fn();
    let release = () => {};
    const gate = new Promise<void>((r) => (release = r));
    server.use(
      http.post(URL_LOGIN, async () => {
        calls();
        await gate;
        return err(401, "INVALID_CREDENTIALS");
      }),
    );
    open("/entrar");
    await fill(user);
    await submit(user);
    const botao = await screen.findByRole("button", { name: "Entrando..." });
    expect(botao).toBeDisabled();
    await user.click(botao);
    release();
    await screen.findByRole("alert");
    expect(calls).toHaveBeenCalledTimes(1);
  });

  it.each([
    ["claro", false],
    ["escuro", true],
  ])("CA8: sem violações no axe a 360 px no tema %s, também com erro", async (_n, dark) => {
    setDevicePrefersDark(dark);
    window.innerWidth = 360;
    const user = userEvent.setup();
    const { container } = open("/entrar");
    await screen.findByLabelText("Senha");
    expect(await axeViolations(container)).toEqual([]);
    await user.click(screen.getByRole("button", { name: "Entrar" }));
    expect(await axeViolations(container)).toEqual([]);
  });

  describe("trava com horário (#445)", () => {
    const locked = (retryAt?: string) =>
      server.use(
        http.post(URL_LOGIN, () =>
          HttpResponse.json(
            { code: "TOO_MANY_REQUESTS", message: "m", changed: false, fields: retryAt ? [{ field: "retryAt", message: retryAt }] : [], traceId: "t" },
            { status: 429 },
          ),
        ),
      );

    it("CA4 e CA5: mostra o aviso com o horário e Criar nova senha; Entrar desabilitado até o horário, depois volta", async () => {
      const user = userEvent.setup();
      locked(new Date(Date.now() + 1500).toISOString());
      open("/entrar");
      await fill(user);
      await submit(user);
      const alert = await screen.findByRole("alert");
      expect(alert).toHaveTextContent(/Muitas tentativas\. Por segurança, este e-mail fica travado por 15 minutos\. Tente de novo às \d{2}h\d{2} ou crie uma nova senha\./);
      expect(screen.getByRole("link", { name: "Criar nova senha" })).toHaveAttribute("href", "/esqueci-senha");
      expect(screen.getByRole("button", { name: "Entrar" })).toBeDisabled();
      await waitFor(() => expect(screen.getByRole("button", { name: "Entrar" })).toBeEnabled(), { timeout: 3000 });
      expect(screen.queryByRole("link", { name: "Criar nova senha" })).not.toBeInTheDocument();
    });

    it("CA6: 429 sem retryAt (ou inválido) mostra o texto genérico, Entrar ativo e sem Criar nova senha", async () => {
      const user = userEvent.setup();
      locked();
      open("/entrar");
      await fill(user);
      await submit(user);
      expect(await screen.findByRole("alert")).toHaveTextContent("Muitas tentativas. Espere alguns minutos e tente de novo.");
      expect(screen.getByRole("button", { name: "Entrar" })).toBeEnabled();
      expect(screen.queryByRole("link", { name: "Criar nova senha" })).not.toBeInTheDocument();
      locked("lixo");
      await submit(user);
      await waitFor(() => expect(screen.getByRole("alert")).toHaveTextContent("Espere alguns minutos"));
    });

    it("CA8: sem violação de acessibilidade com o aviso, nos dois temas", async () => {
      const user = userEvent.setup();
      locked(new Date(Date.now() + 60_000).toISOString());
      const { container } = open("/entrar");
      await fill(user);
      await submit(user);
      await screen.findByRole("link", { name: "Criar nova senha" });
      expect(screen.getByRole("alert")).toHaveAttribute("data-tone", "warn");
      expect(await axeViolations(container)).toEqual([]);
      document.documentElement.classList.add("dark");
      expect(await axeViolations(container)).toEqual([]);
      document.documentElement.classList.remove("dark");
    });
  });
});
