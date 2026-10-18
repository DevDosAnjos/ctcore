import { render, screen, waitFor } from "@testing-library/react";
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
import type { UnitSummary } from "./useUnits";

const session = (role: string): SessionView => ({
  account: { id: "a", email: "x@x.com", name: "X" },
  links: [{ id: "l1", organizationId: "o", organizationName: "Alfa", role, unitIds: [] }],
  activeLinkId: "l1",
  twoFactor: { enabled: false, required: false },
});
const unit = (over: Partial<UnitSummary> = {}): UnitSummary => ({
  id: "u1",
  name: "Unidade Centro",
  shortName: "alfa-centro",
  shortNameAdjusted: false,
  city: "Rio de Janeiro",
  activeStudents: 0,
  administrators: [],
  active: true,
  ...over,
});

type Check = { activeStudents: number; activeClasses: number; lastActiveUnit: boolean; canDeactivate: boolean };
const free: Check = { activeStudents: 0, activeClasses: 0, lastActiveUnit: false, canDeactivate: true };
let units: UnitSummary[];
let calls: { token: string | null; body: unknown }[];

function setup(opts: { role?: string; check?: () => Response | Promise<Response>; deactivate?: () => Response | Promise<Response> } = {}) {
  const role = opts.role ?? "COORDINATOR";
  units = [unit(), unit({ id: "u2", name: "Unidade Norte", shortName: "alfa-norte" })];
  calls = [];
  server.use(
    http.get("/api/v1/auth/session", () => HttpResponse.json(session(role))),
    http.get("/api/v1/permissions", () => HttpResponse.json({ permissions: mockPermissions(role) })),
    http.get("/api/v1/units/reachable", () => HttpResponse.json({ allUnits: false, units: [] })),
    http.get("/api/v1/units", () => HttpResponse.json(units)),
    http.get("/api/v1/units/:id/deactivation-check", () => (opts.check ? opts.check() : HttpResponse.json(free))),
    http.post("/api/v1/auth/reauth", () => HttpResponse.json({ reauthToken: "tok", expiresAt: "2030-01-01T00:00:00Z" })),
    http.post("/api/v1/units/:id/deactivation", async ({ request, params }) => {
      calls.push({ token: request.headers.get("X-Reauth-Token"), body: await request.json() });
      if (opts.deactivate) return opts.deactivate();
      units = units.map((u) => (u.id === params.id ? { ...u, active: false } : u));
      return new HttpResponse(null, { status: 204 });
    }),
  );
  return render(
    <ThemeProvider>
      <MemoryRouter initialEntries={["/configuracoes/unidades"]}>
        <SessionProvider>
          <App />
        </SessionProvider>
      </MemoryRouter>
    </ThemeProvider>,
  );
}

type User = ReturnType<typeof userEvent.setup>;
const open = async (user: User) => {
  await user.click((await screen.findAllByRole("button", { name: /Desativar\s*Unidade Centro/ }))[0]);
  return screen.findByRole("dialog", { name: "Desativar Unidade Centro" });
};
const NAME = "Digite o nome da unidade (Unidade Centro) para confirmar";
const reauth = async (user: User) => {
  await user.click(screen.getByRole("button", { name: "Continuar" }));
  await user.type(await screen.findByLabelText("Senha"), "segredo123");
  await user.click(screen.getByRole("button", { name: "Confirmar" }));
};

describe("DeactivateUnitDialog (RF-ORG-07)", () => {
  it("CA10 / CA11: sem impedimento habilita ao digitar o nome, pede a senha, só então chama a rota com o token; a lista mostra a unidade desativada e o aviso", async () => {
    const user = userEvent.setup();
    setup();
    await open(user);
    const next = screen.getByRole("button", { name: "Continuar" });
    expect(next).toBeDisabled();
    await user.type(screen.getByLabelText(NAME), "unidade");
    expect(next).toBeDisabled();
    await user.clear(screen.getByLabelText(NAME));
    await user.type(screen.getByLabelText(NAME), " unidade CENTRO ");
    expect(next).toBeEnabled();
    await user.click(next);
    await screen.findByRole("dialog", { name: "Confirme que é você" });
    expect(calls).toEqual([]);
    await user.type(screen.getByLabelText("Senha"), "segredo123");
    await user.click(screen.getByRole("button", { name: "Confirmar" }));
    expect(await screen.findByText(/Unidade desativada\./)).toBeInTheDocument();
    expect(calls).toEqual([{ token: "tok", body: { confirmation: "unidade CENTRO" } }]);
    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
    expect(screen.queryByRole("button", { name: /Desativar\s*Unidade Centro/ })).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Desativar\s*Unidade Norte/ })).toBeInTheDocument();
  });

  it("CA10: com alunos e turmas ativos mostra as contagens e não deixa continuar", async () => {
    const user = userEvent.setup();
    setup({ check: () => HttpResponse.json({ activeStudents: 2, activeClasses: 1, lastActiveUnit: false, canDeactivate: false }) });
    const dialog = await open(user);
    expect(await screen.findByText(/2 alunos com modalidade ativa/)).toBeInTheDocument();
    expect(dialog).toHaveTextContent("1 turma ativa");
    expect(screen.getByRole("button", { name: "Continuar" })).toBeDisabled();
    expect(screen.getByLabelText(NAME)).toBeDisabled();
  });

  it("CA10: a única unidade ativa mostra o aviso", async () => {
    const user = userEvent.setup();
    setup({ check: () => HttpResponse.json({ activeStudents: 0, activeClasses: 0, lastActiveUnit: true, canDeactivate: false }) });
    await open(user);
    expect(await screen.findByText("É a única unidade ativa da organização.")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Continuar" })).toBeDisabled();
  });

  it("CA11: 400 mostra a mensagem no campo; 409 volta a consultar e mostra o que impede", async () => {
    const user = userEvent.setup();
    let blocked = false;
    setup({
      check: () => HttpResponse.json(blocked ? { ...free, activeStudents: 3, canDeactivate: false } : free),
      deactivate: () => {
        if (blocked) return HttpResponse.json({ code: "UNIT_HAS_BLOCKERS", fields: [] }, { status: 409 });
        return HttpResponse.json({ code: "VALIDATION_ERROR", fields: [{ field: "confirmation", message: "x" }] }, { status: 400 });
      },
    });
    await open(user);
    await user.type(screen.getByLabelText(NAME), "Unidade Centro");
    await reauth(user);
    expect(await screen.findByText("O nome digitado não confere com o da unidade.")).toBeInTheDocument();
    expect(screen.getByLabelText(NAME)).toHaveAttribute("aria-invalid", "true");

    blocked = true;
    await user.click(screen.getByRole("button", { name: "Continuar" }));
    await user.type(await screen.findByLabelText("Senha"), "segredo123");
    await user.click(screen.getByRole("button", { name: "Confirmar" }));
    expect(await screen.findByText(/3 alunos com modalidade ativa/)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Continuar" })).toBeDisabled();
  });

  it("CA11: erro da consulta e do envio mostram o erro com nova tentativa; 403 mostra o estado sem permissão", async () => {
    const user = userEvent.setup();
    let checks = 0;
    let status = 500;
    setup({
      check: () => (++checks === 1 ? HttpResponse.json({ code: "X" }, { status: 500 }) : HttpResponse.json(free)),
      deactivate: () => HttpResponse.json({ code: "X" }, { status }),
    });
    await open(user);
    await user.click(await screen.findByRole("button", { name: "Tentar de novo" }));
    await user.type(await screen.findByLabelText(NAME), "Unidade Centro");
    await reauth(user);
    await user.click(await screen.findByRole("button", { name: "Tentar de novo" }));
    status = 403;
    await user.type(await screen.findByLabelText("Senha"), "segredo123");
    await user.click(screen.getByRole("button", { name: "Confirmar" }));
    expect(await screen.findByRole("heading", { name: "Você não tem acesso" })).toBeInTheDocument();
  });

  it("CA11: 403 na consulta mostra o estado sem permissão", async () => {
    const user = userEvent.setup();
    setup({ check: () => HttpResponse.json({ code: "FORBIDDEN" }, { status: 403 }) });
    await open(user);
    expect(await screen.findByRole("heading", { name: "Você não tem acesso" })).toBeInTheDocument();
  });

  it("CA12: o Administrador não vê o botão", async () => {
    setup({ role: "ADMINISTRATOR" });
    expect(await screen.findByRole("heading", { name: "Esta área não está disponível para o seu perfil" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Desativar/ })).not.toBeInTheDocument();
  });

  it.each([
    ["claro", false],
    ["escuro", true],
  ])("CA12: a 360 px, sem violações no axe no tema %s, na lista e na janela", async (_n, dark) => {
    const user = userEvent.setup();
    setDevicePrefersDark(dark);
    window.innerWidth = 360;
    const { container } = setup();
    await screen.findAllByRole("button", { name: /Desativar\s*Unidade Centro/ });
    expect(await axeViolations(container)).toEqual([]);
    const dialog = await open(user);
    await screen.findByLabelText(NAME);
    expect(await axeViolations(dialog)).toEqual([]);
  });
});
