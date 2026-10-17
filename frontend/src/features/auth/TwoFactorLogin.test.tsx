import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { MemoryRouter, useLocation } from "react-router";
import { App } from "@/App";
import { server } from "@/mocks/server";
import { axeViolations } from "@/test/axe";
import { setDevicePrefersDark } from "@/test/device";
import { ThemeProvider } from "@/theme/ThemeProvider";
import { SessionProvider } from "./SessionProvider";

const err = (status: number, code: string) =>
  HttpResponse.json({ code, message: "m", changed: false, fields: [], traceId: "t" }, { status });

function Where() {
  const l = useLocation();
  return <p data-testid="where">{l.pathname}</p>;
}

function open(path = "/entrar") {
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

async function toSecondStep(user: User, email = "duas@exemplo.com") {
  await user.type(await screen.findByLabelText("E-mail"), email);
  await user.type(screen.getByLabelText("Senha"), "segredo123");
  await user.click(screen.getByRole("button", { name: "Entrar" }));
  return screen.findByLabelText("Código do app");
}

describe("login com duas etapas (CA21)", () => {
  it("mostra o segundo passo e conclui com o código certo", async () => {
    const user = userEvent.setup();
    open();
    const code = await toSecondStep(user);
    expect(screen.getByRole("heading", { name: "Verificação em duas etapas" })).toBeInTheDocument();
    expect(screen.getByText(/Abra um chamado com o suporte/)).toBeInTheDocument();
    await user.type(code, "123456");
    await user.click(screen.getByRole("button", { name: "Confirmar" }));
    expect(await screen.findByRole("heading", { name: "Bem-vindo ao ctcore" })).toBeInTheDocument();
  });

  it("código errado mostra o erro, mantém o campo e o foco", async () => {
    const user = userEvent.setup();
    open();
    const code = await toSecondStep(user);
    await user.type(code, "000000");
    await user.click(screen.getByRole("button", { name: "Confirmar" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Código incorreto, já usado ou vencido");
    expect(screen.getByLabelText("Código do app")).toHaveValue("000000");
    expect(screen.getByLabelText("Código do app")).toHaveFocus();
  });

  it("código vazio não chama a API", async () => {
    const user = userEvent.setup();
    open();
    await toSecondStep(user);
    await user.click(screen.getByRole("button", { name: "Confirmar" }));
    expect(await screen.findByText("Informe o código.")).toBeInTheDocument();
  });

  it("usa um código de reserva e volta ao e-mail e senha", async () => {
    const user = userEvent.setup();
    open();
    await toSecondStep(user);
    await user.click(screen.getByRole("button", { name: "Usar um código de reserva" }));
    const backup = screen.getByLabelText("Código de reserva");
    await user.type(backup, "RESERVA001");
    await user.click(screen.getByRole("button", { name: "Usar o código do app" }));
    expect(screen.getByLabelText("Código do app")).toHaveValue("");
    await user.click(screen.getByRole("button", { name: "Voltar" }));
    expect(await screen.findByLabelText("Senha")).toBeInTheDocument();
  });

  it("código de reserva válido entra", async () => {
    const user = userEvent.setup();
    open();
    await toSecondStep(user);
    await user.click(screen.getByRole("button", { name: "Usar um código de reserva" }));
    await user.type(screen.getByLabelText("Código de reserva"), "RESERVA001");
    await user.click(screen.getByRole("button", { name: "Confirmar" }));
    expect(await screen.findByRole("heading", { name: "Bem-vindo ao ctcore" })).toBeInTheDocument();
  });

  it("bilhete vencido volta ao primeiro passo com aviso", async () => {
    const user = userEvent.setup();
    open();
    const code = await toSecondStep(user, "vencida@exemplo.com");
    await user.type(code, "123456");
    await user.click(screen.getByRole("button", { name: "Confirmar" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("A verificação venceu");
    expect(screen.getByLabelText("Senha")).toBeInTheDocument();
  });

  it("429 e erro do servidor", async () => {
    const user = userEvent.setup();
    open();
    const code = await toSecondStep(user);
    await user.type(code, "limite");
    server.use(http.post("/api/v1/auth/login/two-factor", () => err(429, "TOO_MANY_REQUESTS")));
    await user.clear(code);
    await user.type(code, "123456");
    await user.click(screen.getByRole("button", { name: "Confirmar" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Muitas tentativas");
    server.use(http.post("/api/v1/auth/login/two-factor", () => err(500, "INTERNAL_ERROR")));
    await user.click(screen.getByRole("button", { name: "Confirmar" }));
    expect(await screen.findByRole("button", { name: "Tentar de novo" })).toBeInTheDocument();
  });

  it.each([
    ["claro", false],
    ["escuro", true],
  ])("axe a 360 px no tema %s, também com erro", async (_n, dark) => {
    setDevicePrefersDark(dark);
    window.innerWidth = 360;
    const user = userEvent.setup();
    const { container } = open();
    const code = await toSecondStep(user);
    expect(await axeViolations(container)).toEqual([]);
    await user.type(code, "000000");
    await user.click(screen.getByRole("button", { name: "Confirmar" }));
    await screen.findByRole("alert");
    expect(await axeViolations(container)).toEqual([]);
  });
});

describe("Coordenador sem duas etapas (CA24)", () => {
  it("entra, cai na configuração obrigatória, não sai por outro endereço e ativa", async () => {
    const user = userEvent.setup();
    open("/entrar?next=%2Fsem-acesso");
    await user.type(await screen.findByLabelText("E-mail"), "coord@exemplo.com");
    await user.type(screen.getByLabelText("Senha"), "segredo123");
    await user.click(screen.getByRole("button", { name: "Entrar" }));
    expect(await screen.findByRole("heading", { name: "Ative as duas etapas para continuar" })).toBeInTheDocument();
    expect(screen.getByTestId("where")).toHaveTextContent("/configurar-duas-etapas");
    expect(screen.getByRole("button", { name: "Sair da conta" })).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Ativar duas etapas" }));
    await user.type(await screen.findByLabelText("Código de 6 dígitos"), "123456");
    await user.click(screen.getByRole("button", { name: "Ativar" }));
    await user.click(await screen.findByLabelText("Já guardei os códigos"));
    await user.click(screen.getByRole("button", { name: "Concluir" }));
    await waitFor(() => expect(screen.getByTestId("where")).toHaveTextContent(/^\/$/));
    expect(screen.getByRole("heading", { name: "Bem-vindo ao ctcore" })).toBeInTheDocument();
  });

  it("Sair volta ao login", async () => {
    const user = userEvent.setup();
    open("/");
    await user.type(await screen.findByLabelText("E-mail"), "coord@exemplo.com");
    await user.type(screen.getByLabelText("Senha"), "segredo123");
    await user.click(screen.getByRole("button", { name: "Entrar" }));
    await user.click(await screen.findByRole("button", { name: "Sair da conta" }));
    expect(await screen.findByLabelText("Senha")).toBeInTheDocument();
  });

  it("quem não é obrigado não vê a página obrigatória", async () => {
    server.use(
      http.get("/api/v1/auth/session", () =>
        HttpResponse.json({ account: { id: "1", email: "a@x.com" }, links: [], twoFactor: { enabled: true, required: true } }),
      ),
    );
    open("/configurar-duas-etapas");
    expect(await screen.findByRole("heading", { name: "Bem-vindo ao ctcore" })).toBeInTheDocument();
  });

  it("axe a 360 px nos dois temas", async () => {
    for (const dark of [false, true]) {
      setDevicePrefersDark(dark);
      window.innerWidth = 360;
      server.use(
        http.get("/api/v1/auth/session", () =>
          HttpResponse.json({ account: { id: "1", email: "a@x.com" }, links: [], twoFactor: { enabled: false, required: true } }),
        ),
      );
      const { container, unmount } = open("/");
      await screen.findByRole("heading", { name: "Ative as duas etapas para continuar" });
      expect(await axeViolations(container)).toEqual([]);
      unmount();
    }
  });
});
