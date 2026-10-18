import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { MemoryRouter } from "react-router";
import { App } from "@/App";
import { SessionProvider, type SessionView } from "@/features/auth/SessionProvider";
import { mockPermissions } from "@/mocks/permissions";
import { server } from "@/mocks/server";
import { axeViolations } from "@/test/axe";
import { setDevicePrefersDark } from "@/test/device";
import { ThemeProvider } from "@/theme/ThemeProvider";

const session = (role: string): SessionView => ({
  account: { id: "a", email: "x@x.com", name: "X" },
  links: [{ id: "l1", organizationId: "o", organizationName: "Alfa", role, unitIds: [] }],
  activeLinkId: "l1",
  twoFactor: { enabled: false, required: false },
});

let role: string;
let calls: { token: string | null; body: unknown }[];

function setup(transfer?: () => Response | Promise<Response>) {
  role = "COORDINATOR";
  calls = [];
  server.use(
    http.get("/api/v1/auth/session", () => HttpResponse.json(session(role))),
    http.get("/api/v1/permissions", () => HttpResponse.json({ permissions: mockPermissions(role) })),
    http.get("/api/v1/units/reachable", () => HttpResponse.json({ allUnits: false, units: [] })),
    http.get("/api/v1/organization", () => HttpResponse.json({ name: "Academia Alfa", color: null, holderDocument: null, logoUrl: null })),
    http.post("/api/v1/auth/reauth", () => HttpResponse.json({ reauthToken: "tok", expiresAt: "2030-01-01T00:00:00Z" })),
    http.post("/api/v1/organization/coordination-transfer", async ({ request }) => {
      calls.push({ token: request.headers.get("X-Reauth-Token"), body: await request.json() });
      if (transfer) return transfer();
      role = "ADMINISTRATOR";
      return new HttpResponse(null, { status: 204 });
    }),
  );
  return render(
    <ThemeProvider>
      <MemoryRouter initialEntries={["/configuracoes/organizacao"]}>
        <SessionProvider>
          <App />
        </SessionProvider>
      </MemoryRouter>
    </ThemeProvider>,
  );
}

const open = async (user: ReturnType<typeof userEvent.setup>) => {
  await user.click(await screen.findByRole("button", { name: "Transferir coordenação" }));
  return screen.findByRole("dialog", { name: "Transferir coordenação" });
};
const fill = async (user: ReturnType<typeof userEvent.setup>, word = "transferir") => {
  await user.type(screen.getByLabelText("E-mail do novo Coordenador"), "admin@alfa.test");
  await user.type(screen.getByLabelText("Digite TRANSFERIR para confirmar"), word);
};
const reauth = async (user: ReturnType<typeof userEvent.setup>) => {
  await user.click(screen.getByRole("button", { name: "Continuar" }));
  await user.type(await screen.findByLabelText("Senha"), "segredo123");
  await user.click(screen.getByRole("button", { name: "Confirmar" }));
};

describe("TransferCoordinationDialog (RF-ORG-06)", () => {
  it("CA13 / CA14: só continua com e-mail e palavra; pede a senha e só então chama a rota com o token; no sucesso vira Administrador", async () => {
    const user = userEvent.setup();
    setup();
    await open(user);
    const next = screen.getByRole("button", { name: "Continuar" });
    expect(next).toBeDisabled();
    await fill(user, "errado");
    expect(next).toBeDisabled();
    await user.clear(screen.getByLabelText("Digite TRANSFERIR para confirmar"));
    await user.type(screen.getByLabelText("Digite TRANSFERIR para confirmar"), " transferir ");
    expect(next).toBeEnabled();
    await user.click(next);
    await screen.findByRole("dialog", { name: "Confirme que é você" });
    expect(calls).toEqual([]);
    await user.type(screen.getByLabelText("Senha"), "segredo123");
    await user.click(screen.getByRole("button", { name: "Confirmar" }));
    expect(await screen.findByText("Coordenação transferida. Agora você é Administrador.")).toBeInTheDocument();
    expect(calls).toEqual([{ token: "tok", body: { email: "admin@alfa.test", confirmation: "transferir" } }]);
    expect(screen.queryByRole("button", { name: "Transferir coordenação" })).not.toBeInTheDocument();
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("CA14: erro 400 no e-mail aparece no campo e a janela volta ao formulário", async () => {
    const user = userEvent.setup();
    setup(() => HttpResponse.json({ code: "VALIDATION_ERROR", fields: [{ field: "email", message: "x" }] }, { status: 400 }));
    await open(user);
    await fill(user);
    await reauth(user);
    expect(await screen.findByText("Informe o e-mail de um Administrador ativo da organização.")).toBeInTheDocument();
    expect(screen.getByLabelText("E-mail do novo Coordenador")).toHaveAttribute("aria-invalid", "true");
  });

  it("CA14: 403 mostra o estado sem permissão e erro de servidor mostra o erro com nova tentativa", async () => {
    const user = userEvent.setup();
    let status = 500;
    setup(() => HttpResponse.json({ code: "X" }, { status }));
    await open(user);
    await fill(user);
    await reauth(user);
    await user.click(await screen.findByRole("button", { name: "Tentar de novo" }));
    status = 403;
    await user.type(await screen.findByLabelText("Senha"), "segredo123");
    await user.click(screen.getByRole("button", { name: "Confirmar" }));
    expect(await screen.findByRole("heading", { name: "Você não tem acesso" })).toBeInTheDocument();
  });

  it("CA15: Administrador não vê o botão", async () => {
    setup();
    role = "ADMINISTRATOR";
    expect(await screen.findByRole("heading", { name: "Organização", level: 1 })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Transferir coordenação" })).not.toBeInTheDocument();
  });

  it.each([
    ["claro", false],
    ["escuro", true],
  ])("CA15: a 360 px, sem violações no axe no tema %s, na tela e na janela", async (_n, dark) => {
    const user = userEvent.setup();
    setDevicePrefersDark(dark);
    window.innerWidth = 360;
    const { container } = setup();
    await screen.findByRole("button", { name: "Transferir coordenação" });
    expect(await axeViolations(container)).toEqual([]);
    const dialog = await open(user);
    expect(await axeViolations(dialog)).toEqual([]);
  });
});
