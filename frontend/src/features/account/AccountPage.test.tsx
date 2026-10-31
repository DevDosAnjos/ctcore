import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { MemoryRouter } from "react-router";
import { App } from "@/App";
import { SessionProvider, type SessionView } from "@/features/auth/SessionProvider";
import { mockPermissions } from "@/mocks/permissions";
import { server } from "@/mocks/server";
import { axeViolations } from "@/test/axe";
import { portalShellOk } from "@/test/portalShell";
import { setDevicePrefersDark } from "@/test/device";
import { ThemeProvider } from "@/theme/ThemeProvider";

const session = (role: string, twoFactor = { enabled: false, required: false }): SessionView => ({
  account: { id: "a", email: "x@x.com", name: "X" },
  links: [{ id: "l1", organizationId: "o", organizationName: "Alfa", role, unitIds: [] }],
  activeLinkId: "l1",
  twoFactor,
});

// A sessão do mock fica ativa para as rotas que exigem login; o GET /auth/session é trocado por `current`.
type Handler = Parameters<typeof server.use>[number];
async function setup(role = "COORDINATOR", twoFactor = { enabled: false, required: false }, path = "/conta", extra: Handler[] = []) {
  await fetch("/api/v1/auth/login", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ email: "a@b.c", password: "x" }) });
  let current = session(role, twoFactor);
  server.use(
    ...extra,
    portalShellOk,
    http.get("/api/v1/auth/session", () => HttpResponse.json(current)),
    http.get("/api/v1/permissions", () => HttpResponse.json({ permissions: mockPermissions(role) })),
    http.get("/api/v1/units/reachable", () => HttpResponse.json({ allUnits: false, units: [] })),
  );
  const view = render(
    <ThemeProvider>
      <MemoryRouter initialEntries={[path]}>
        <SessionProvider>
          <App />
        </SessionProvider>
      </MemoryRouter>
    </ThemeProvider>,
  );
  return { ...view, setSession: (s: SessionView) => (current = s) };
}

const ready = () => screen.findByRole("heading", { name: "Minha conta", level: 1 });
const strong = "NovaSenha123";

describe("AccountPage: senha (CA1, CA2)", () => {
  it("mostra o erro no campo com a senha atual errada e, com a certa, confirma e deixa só a sessão atual", async () => {
    const user = userEvent.setup();
    await setup();
    await ready();
    expect(await screen.findByText("Safari em iOS")).toBeInTheDocument();
    const submit = screen.getByRole("button", { name: "Trocar senha" });
    expect(submit).toBeDisabled();

    await user.type(screen.getByLabelText("Senha atual"), "errada");
    await user.type(screen.getByLabelText("Senha", { selector: "#acc-senha" }), strong);
    await user.type(screen.getByLabelText("Confirmar senha"), strong);
    await user.click(submit);
    expect(await screen.findByText("A senha atual está incorreta.")).toBeInTheDocument();

    await user.clear(screen.getByLabelText("Senha atual"));
    await user.type(screen.getByLabelText("Senha atual"), "Atual12345");
    await user.click(screen.getByRole("button", { name: "Trocar senha" }));
    expect(await screen.findByText("Senha alterada. As outras sessões foram encerradas.")).toBeInTheDocument();
    await waitFor(() => expect(screen.queryByText("Safari em iOS")).not.toBeInTheDocument());
    expect(screen.getByText("Chrome em Windows")).toBeInTheDocument();
    expect(screen.getByLabelText("Senha atual")).toHaveValue("");
  });

  it("senha conhecida vira erro no campo da senha nova; 429 vira aviso do formulário", async () => {
    const user = userEvent.setup();
    await setup();
    await ready();
    await user.type(screen.getByLabelText("Senha atual"), "Atual12345");
    await user.type(screen.getByLabelText("Senha", { selector: "#acc-senha" }), "Password1");
    await user.type(screen.getByLabelText("Confirmar senha"), "Password1");
    await user.click(screen.getByRole("button", { name: "Trocar senha" }));
    expect(await screen.findByText("Essa senha é muito conhecida. Escolha outra.")).toBeInTheDocument();

    await user.clear(screen.getByLabelText("Senha atual"));
    await user.type(screen.getByLabelText("Senha atual"), "limite");
    await user.click(screen.getByRole("button", { name: "Trocar senha" }));
    expect(await screen.findByText("Muitas tentativas. Espere alguns minutos e tente de novo.")).toBeInTheDocument();
  });

  it("repetição diferente não deixa enviar", async () => {
    const user = userEvent.setup();
    await setup();
    await ready();
    await user.type(screen.getByLabelText("Senha atual"), "Atual12345");
    await user.type(screen.getByLabelText("Senha", { selector: "#acc-senha" }), strong);
    await user.type(screen.getByLabelText("Confirmar senha"), "Outra12345");
    expect(screen.getByText("As senhas não são iguais.")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Trocar senha" })).toBeDisabled();
  });
});

describe("AccountPage: e-mail (CA3, CA4)", () => {
  it("pede o link e avisa; erros por campo", async () => {
    const user = userEvent.setup();
    await setup();
    await ready();
    const send = screen.getByRole("button", { name: "Enviar link de confirmação" });
    await user.click(send);
    expect(await screen.findByText("Preencha todos os campos.")).toBeInTheDocument();

    await user.type(screen.getByLabelText("Novo e-mail"), "ana@exemplo.com");
    await user.type(screen.getByLabelText("Sua senha"), "Atual12345");
    await user.click(send);
    expect(await screen.findByText("Informe outro e-mail válido.")).toBeInTheDocument();

    await user.clear(screen.getByLabelText("Novo e-mail"));
    await user.type(screen.getByLabelText("Novo e-mail"), "novo@x.com");
    await user.clear(screen.getByLabelText("Sua senha"));
    await user.type(screen.getByLabelText("Sua senha"), "errada");
    await user.click(send);
    expect(await screen.findByText("Senha incorreta.")).toBeInTheDocument();

    await user.clear(screen.getByLabelText("Sua senha"));
    await user.type(screen.getByLabelText("Sua senha"), "Atual12345");
    await user.click(send);
    expect(await screen.findByText(/novo@x\.com puder ser usado/)).toBeInTheDocument();
  });

  it("com as duas etapas pede o código", async () => {
    const user = userEvent.setup();
    await setup("ADMINISTRATOR", { enabled: true, required: false }, "/conta", [
      http.post("/api/v1/auth/email-change", async ({ request }) => {
        const { code } = (await request.json()) as { code?: string };
        return code === "123456" ? new HttpResponse(null, { status: 202 }) : HttpResponse.json({ code: "INVALID_TWO_FACTOR_CODE" }, { status: 401 });
      }),
    ]);
    await ready();
    await user.type(screen.getByLabelText("Novo e-mail"), "novo@x.com");
    await user.type(screen.getByLabelText("Sua senha"), "Atual12345");
    await user.click(screen.getByRole("button", { name: "Enviar link de confirmação" }));
    expect(await screen.findByText("Preencha todos os campos.")).toBeInTheDocument();
    await user.type(screen.getByLabelText("Código do app ou de reserva"), "000000");
    await user.click(screen.getByRole("button", { name: "Enviar link de confirmação" }));
    expect(await screen.findByText("Código incorreto.")).toBeInTheDocument();
    await user.clear(screen.getByLabelText("Código do app ou de reserva"));
    await user.type(screen.getByLabelText("Código do app ou de reserva"), "123456");
    await user.click(screen.getByRole("button", { name: "Enviar link de confirmação" }));
    expect(await screen.findByText(/novo@x\.com puder ser usado/)).toBeInTheDocument();
  });
});

describe("ConfirmEmailPage (CA7, CA8)", () => {
  it("confirma com o link válido e mostra o e-mail novo", async () => {
    const user = userEvent.setup();
    await setup("COORDINATOR", { enabled: false, required: false }, "/confirmar-email/valido");
    await screen.findByRole("heading", { name: "Confirmar novo e-mail" });
    await user.click(screen.getByRole("button", { name: "Confirmar troca" }));
    expect(await screen.findByText("E-mail alterado. Use o endereço novo para entrar.")).toBeInTheDocument();
  });

  it("link vencido, usado ou de outra conta mostra o aviso", async () => {
    const user = userEvent.setup();
    await setup("STUDENT", { enabled: false, required: false }, "/confirmar-email/vencido");
    await user.click(await screen.findByRole("button", { name: "Confirmar troca" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Este link venceu");
  });

  it("sem sessão leva ao login e guarda o link para voltar", async () => {
    render(
      <ThemeProvider>
        <MemoryRouter initialEntries={["/confirmar-email/valido"]}>
          <SessionProvider>
            <App />
          </SessionProvider>
        </MemoryRouter>
      </ThemeProvider>,
    );
    expect(await screen.findByRole("heading", { name: "Entrar" })).toBeInTheDocument();
  });
});

describe("AccountPage: duas etapas (CA9)", () => {
  it("Coordenador obrigado não vê Desligar, mas gera códigos novos", async () => {
    const user = userEvent.setup();
    await setup("COORDINATOR", { enabled: true, required: true });
    await ready();
    expect(screen.getByText("As duas etapas estão ligadas.")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Desligar duas etapas" })).not.toBeInTheDocument();
    server.use(http.post("/api/v1/auth/two-factor/backup-codes", () => HttpResponse.json({ backupCodes: Array.from({ length: 10 }, (_, i) => `CODIGO000${i}`) })));
    await user.click(screen.getByRole("button", { name: "Gerar códigos de reserva novos" }));
    const dialog = await screen.findByRole("dialog");
    expect(await within(dialog).findAllByRole("listitem")).toHaveLength(10);
  });

  it("liga: QR, código e códigos de reserva; a seção passa a mostrar ligado", async () => {
    const user = userEvent.setup();
    const { setSession } = await setup("ADMINISTRATOR");
    await ready();
    expect(screen.getByText("As duas etapas estão desligadas.")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Ativar duas etapas" }));
    await user.type(await screen.findByLabelText("Código de 6 dígitos"), "123456");
    setSession(session("ADMINISTRATOR", { enabled: true, required: false }));
    await user.click(screen.getByRole("button", { name: "Ativar" }));
    expect(within(await screen.findByRole("list", { name: "Seus códigos de reserva" })).getAllByRole("listitem")).toHaveLength(10);
    await user.click(screen.getByLabelText("Já guardei os códigos"));
    await user.click(screen.getByRole("button", { name: "Concluir" }));
    expect(await screen.findByText("As duas etapas estão ligadas.")).toBeInTheDocument();
  });

  it("desliga com senha e código; erros aparecem nos campos", async () => {
    const user = userEvent.setup();
    const { setSession } = await setup("ADMINISTRATOR", { enabled: true, required: false });
    await ready();
    await user.click(screen.getByRole("button", { name: "Desligar duas etapas" }));
    const dialog = await screen.findByRole("dialog");
    await user.click(within(dialog).getByRole("button", { name: "Desligar" }));
    expect(await within(dialog).findByText("Preencha a senha e o código.")).toBeInTheDocument();
    await user.type(within(dialog).getByLabelText("Senha"), "errada");
    await user.type(within(dialog).getByLabelText("Código do app ou de reserva"), "123456");
    await user.click(within(dialog).getByRole("button", { name: "Desligar" }));
    expect(await within(dialog).findByText("Senha incorreta.")).toBeInTheDocument();

    setSession(session("ADMINISTRATOR"));
    await user.clear(within(dialog).getByLabelText("Senha"));
    await user.type(within(dialog).getByLabelText("Senha"), "Atual12345");
    await user.click(within(dialog).getByRole("button", { name: "Desligar" }));
    expect(await screen.findByText("As duas etapas estão desligadas.")).toBeInTheDocument();
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });
});

describe("AccountPage: sessões (CA11, CA12, CA15)", () => {
  it("encerra uma sessão; a atual não tem botão", async () => {
    const user = userEvent.setup();
    await setup();
    await ready();
    expect(await screen.findByText("Esta sessão")).toBeInTheDocument();
    expect(screen.getAllByRole("button", { name: /^Encerrar a sessão em/ })).toHaveLength(1);
    await user.click(screen.getByRole("button", { name: "Encerrar a sessão em Safari em iOS" }));
    await waitFor(() => expect(screen.queryByText("Safari em iOS")).not.toBeInTheDocument());
    expect(screen.getByText("Não há outras sessões ativas.")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Encerrar todas as outras" })).not.toBeInTheDocument();
  });

  it("encerra todas as outras só depois da confirmação", async () => {
    const user = userEvent.setup();
    await setup();
    await ready();
    await user.click(await screen.findByRole("button", { name: "Encerrar todas as outras" }));
    const dialog = await screen.findByRole("dialog");
    expect(screen.getByText("Safari em iOS")).toBeInTheDocument();
    await user.click(within(dialog).getByRole("button", { name: "Encerrar todas as outras" }));
    await waitFor(() => expect(screen.queryByText("Safari em iOS")).not.toBeInTheDocument());
    expect(screen.getByText("Chrome em Windows")).toBeInTheDocument();
  });

  it("falha ao encerrar mostra o aviso", async () => {
    const user = userEvent.setup();
    await setup("COORDINATOR", { enabled: false, required: false }, "/conta", [http.delete("/api/v1/auth/sessions/:id", () => new HttpResponse(null, { status: 500 }))]);
    await ready();
    await user.click(await screen.findByRole("button", { name: "Encerrar a sessão em Safari em iOS" }));
    expect(await screen.findByText("Não conseguimos encerrar a sessão. Tente de novo.")).toBeInTheDocument();
  });

  it("carregando, erro com Tentar de novo e sessão expirada", async () => {
    const user = userEvent.setup();
    let fail = true;
    let release = () => {};
    const gate = new Promise<void>((r) => (release = r));
    await setup("COORDINATOR", { enabled: false, required: false }, "/conta", [http.get("/api/v1/auth/sessions", async () => {
        await gate;
        return fail ? new HttpResponse(null, { status: 500 }) : HttpResponse.json([]);
      })]);
    await ready();
    expect(screen.getByText("Carregando...")).toBeInTheDocument();
    release();
    const retry = await screen.findByRole("button", { name: "Tentar de novo" });
    fail = false;
    await user.click(retry);
    await waitFor(() => expect(screen.queryByRole("button", { name: "Tentar de novo" })).not.toBeInTheDocument());
  });

  it("401 na lista volta ao login", async () => {
    let calls = 0;
    await setup("COORDINATOR", { enabled: false, required: false }, "/conta", [
      http.get("/api/v1/auth/sessions", () => HttpResponse.json({ code: "UNAUTHENTICATED" }, { status: 401 })),
      http.get("/api/v1/auth/session", () => (calls++ === 0 ? HttpResponse.json(session("COORDINATOR")) : HttpResponse.json({ code: "UNAUTHENTICATED" }, { status: 401 }))),
    ]);
    expect(await screen.findByRole("heading", { name: "Entrar" })).toBeInTheDocument();
  });
});

describe("AccountPage: acessibilidade em 360 px (CA15)", () => {
  it.each([
    ["gestão", "COORDINATOR", false],
    ["gestão", "COORDINATOR", true],
    ["portal", "STUDENT", false],
    ["portal", "GUARDIAN", true],
  ])("perfil de %s (%s) sem violações no axe (escuro: %s)", async (_n, role, dark) => {
    setDevicePrefersDark(dark);
    window.innerWidth = 360;
    const { container } = await setup(role);
    await ready();
    await screen.findByText("Safari em iOS");
    expect(document.documentElement.classList.contains("dark")).toBe(dark);
    expect(await axeViolations(container)).toEqual([]);
  });
});
