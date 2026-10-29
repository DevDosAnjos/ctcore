import { render, screen, waitFor, within } from "@testing-library/react";
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
import type { StaffUser } from "./useUsers";

const CENTRO = { id: "u1", name: "Unidade Centro" };
const NORTE = { id: "u2", name: "Unidade Norte" };
const session = (role: string): SessionView => ({
  account: { id: "a", email: "x@x.com", name: "X" },
  links: [{ id: "l1", organizationId: "o", organizationName: "Alfa", role, unitIds: [] }],
  activeLinkId: "l1",
  twoFactor: { enabled: false, required: false },
});
const user = (over: Partial<StaffUser> = {}): StaffUser => ({
  linkId: "k1",
  name: "Bruno",
  email: "bruno@alfa.test",
  role: "ADMINISTRATOR",
  units: [CENTRO],
  lastAccessAt: null,
  status: "ACTIVE",
  canManage: true,
  ...over,
});

let rows: StaffUser[];
let calls: { method: string; path: string; body?: unknown }[];
let lastAdmin: boolean;

function setup(role = "COORDINATOR", read: () => Response | Promise<Response> = () => HttpResponse.json(rows)) {
  calls = [];
  const note = async (request: Request, path: string) => {
    calls.push({ method: request.method, path, body: request.method === "GET" || request.method === "DELETE" ? undefined : await request.json().catch(() => undefined) });
  };
  server.use(
    http.get("/api/v1/auth/session", () => HttpResponse.json(session(role))),
    http.get("/api/v1/permissions", () => HttpResponse.json({ permissions: mockPermissions(role) })),
    http.get("/api/v1/units/reachable", () => HttpResponse.json({ allUnits: role === "COORDINATOR", units: [CENTRO, NORTE].map((u) => ({ ...u, shortName: u.id })) })),
    http.get("/api/v1/users", read),
    http.post("/api/v1/users/coordinator-appointment", async ({ request }) => {
      await note(request, "appointment");
      lastAdmin = false;
      return HttpResponse.json(user());
    }),
    http.post("/api/v1/users", async ({ request }) => {
      const body0 = (await request.clone().json()) as { email: string; name: string };
      await note(request, "invite");
      const body = body0;
      if (body.email === "existente@x.com") return HttpResponse.json({ code: "ALREADY_LINKED", fields: [] }, { status: 409 });
      const created = user({ linkId: "novo", name: body.name, email: body.email, status: "PENDING" });
      rows = [...rows, created];
      return HttpResponse.json(created, { status: 201 });
    }),
    http.post("/api/v1/users/:id/resend", async ({ request }) => {
      await note(request, "resend");
      return new HttpResponse(null, { status: 204 });
    }),
    http.put("/api/v1/users/:id/units", async ({ request }) => {
      await note(request, "units");
      return HttpResponse.json(user());
    }),
    http.delete("/api/v1/users/:id", async ({ request, params }) => {
      await note(request, "deactivate");
      if (lastAdmin) return HttpResponse.json({ code: "LAST_UNIT_ADMINISTRATOR", fields: [{ field: "unitIds", message: CENTRO.id }] }, { status: 409 });
      rows = rows.filter((r) => r.linkId !== params.id);
      return new HttpResponse(null, { status: 204 });
    }),
  );
  return render(
    <ThemeProvider>
      <MemoryRouter initialEntries={["/configuracoes/usuarios"]}>
        <SessionProvider>
          <App />
        </SessionProvider>
      </MemoryRouter>
    </ThemeProvider>,
  );
}

const ready = () => screen.findByRole("heading", { name: "Usuários da gestão", level: 1 });
const card = (name: string) => screen.getAllByRole("row").find((r) => r.querySelector("td p")?.textContent === name)!;

beforeEach(() => {
  lastAdmin = false;
  rows = [user(), user({ linkId: "k2", name: "Carla", email: "carla@alfa.test", status: "PENDING" }), user({ linkId: "k3", name: "Diego", role: "TEACHER", canManage: false, lastAccessAt: "2026-10-16T08:00:00Z" })];
});

describe("UsersPage", () => {
  it("CA8: os filtros rápidos mostram a contagem e filtram a tabela por situação", async () => {
    const u = userEvent.setup();
    setup();
    await ready();
    await u.click(screen.getByRole("button", { name: "Convite enviado, 1" }));
    expect(screen.getAllByRole("row")).toHaveLength(2);
    expect(card("Carla")).toBeDefined();
    expect(screen.queryByText("Bruno")).toBeNull();
  });

  it("CA19: lista perfil, unidades, último acesso e situação; ações só com canManage; pendente tem Reenviar convite", async () => {
    setup();
    await ready();
    const bruno = card("Bruno");
    expect(within(bruno).getByText("bruno@alfa.test")).toBeInTheDocument();
    expect(within(bruno).getByText("Administrador")).toBeInTheDocument();
    expect(within(bruno).getByText("Unidade Centro")).toBeInTheDocument();
    expect(within(bruno).getByText("Nunca entrou")).toBeInTheDocument();
    expect(within(bruno).getByText("Ativo")).toBeInTheDocument();
    expect(within(bruno).queryByRole("button", { name: /Reenviar convite/ })).toBeNull();
    expect(within(bruno).getByRole("button", { name: /Alterar unidades/ })).toBeInTheDocument();
    const carla = card("Carla");
    expect(within(carla).getByText("Convite enviado")).toBeInTheDocument();
    expect(within(carla).getByRole("button", { name: /Reenviar convite/ })).toBeInTheDocument();
    expect(within(card("Diego")).queryByRole("button", { name: /Alterar unidades|Desativar/ })).toBeNull();
  });

  it("CA19: carregando, erro com Tentar de novo, vazio e sem permissão", async () => {
    const user = userEvent.setup();
    let n = 0;
    const { unmount } = setup("COORDINATOR", () => (++n === 1 ? HttpResponse.json({ code: "INTERNAL_ERROR" }, { status: 500 }) : HttpResponse.json([])));
    expect(screen.getByRole("status")).toHaveTextContent("Carregando");
    await user.click(await screen.findByRole("button", { name: "Tentar de novo" }));
    expect(await screen.findByRole("heading", { name: "Nenhum usuário" })).toBeInTheDocument();
    unmount();
    setup("COORDINATOR", () => HttpResponse.json({ code: "FORBIDDEN" }, { status: 403 }));
    expect(await screen.findByRole("heading", { name: "Você não tem acesso" })).toBeInTheDocument();
  });

  it("CA20: valida os campos sem enviar; com dados válidos fecha, avisa e a nova linha aparece como Convite enviado", async () => {
    const u = userEvent.setup();
    setup();
    await ready();
    await u.click(screen.getByRole("button", { name: "Convidar" }));
    const dialog = await screen.findByRole("dialog");
    await u.type(within(dialog).getByLabelText("E-mail"), "invalido");
    await u.click(within(dialog).getByRole("button", { name: "Enviar convite" }));
    expect(within(dialog).getByText("Informe o nome.")).toBeInTheDocument();
    expect(within(dialog).getByText("Informe um e-mail válido.")).toBeInTheDocument();
    expect(within(dialog).getByText("Escolha ao menos uma unidade.")).toBeInTheDocument();
    expect(calls).toEqual([]);
    await u.type(within(dialog).getByLabelText("Nome"), "Fabio");
    await u.clear(within(dialog).getByLabelText("E-mail"));
    await u.type(within(dialog).getByLabelText("E-mail"), "fabio@alfa.test");
    await u.click(within(dialog).getByRole("checkbox", { name: "Unidade Norte" }));
    await u.click(within(dialog).getByRole("button", { name: "Enviar convite" }));
    await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull());
    expect(calls).toEqual([{ method: "POST", path: "invite", body: { email: "fabio@alfa.test", name: "Fabio", unitIds: ["u2"] } }]);
    expect(screen.getByRole("status")).toHaveTextContent("Convite enviado.");
    await screen.findByText("fabio@alfa.test");
    expect(within(card("Fabio")).getByText("Convite enviado")).toBeInTheDocument();
  });

  it("CA20: e-mail já ligado (409) mostra a mensagem e mantém a janela", async () => {
    const u = userEvent.setup();
    setup();
    await ready();
    await u.click(screen.getByRole("button", { name: "Convidar" }));
    const dialog = await screen.findByRole("dialog");
    await u.type(within(dialog).getByLabelText("Nome"), "Ele");
    await u.type(within(dialog).getByLabelText("E-mail"), "existente@x.com");
    await u.click(within(dialog).getByRole("checkbox", { name: "Unidade Centro" }));
    await u.click(within(dialog).getByRole("button", { name: "Enviar convite" }));
    expect(await within(dialog).findByText(/já está ligada/)).toBeInTheDocument();
  });

  it("R6: reenviar convite chama a rota e avisa", async () => {
    const u = userEvent.setup();
    setup();
    await ready();
    await u.click(within(card("Carla")).getByRole("button", { name: /Reenviar convite/ }));
    expect(await screen.findByText("Convite reenviado.")).toBeInTheDocument();
    expect(calls).toEqual([{ method: "POST", path: "resend", body: undefined }]);
  });

  it("R7: alterar unidades envia a lista desejada", async () => {
    const u = userEvent.setup();
    setup();
    await ready();
    await u.click(within(card("Bruno")).getByRole("button", { name: /Alterar unidades/ }));
    const dialog = await screen.findByRole("dialog");
    expect(within(dialog).getByRole("checkbox", { name: "Unidade Centro" })).toBeChecked();
    await u.click(within(dialog).getByRole("checkbox", { name: "Unidade Norte" }));
    await u.click(within(dialog).getByRole("button", { name: "Salvar" }));
    await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull());
    expect(calls).toEqual([{ method: "PUT", path: "units", body: { unitIds: ["u1", "u2"] } }]);
    expect(screen.getByRole("status")).toHaveTextContent("Unidades atualizadas.");
  });

  it("R7: sem nenhuma unidade o erro aparece e nada é enviado", async () => {
    const u = userEvent.setup();
    setup();
    await ready();
    await u.click(within(card("Bruno")).getByRole("button", { name: /Alterar unidades/ }));
    const dialog = await screen.findByRole("dialog");
    await u.click(within(dialog).getByRole("checkbox", { name: "Unidade Centro" }));
    await u.click(within(dialog).getByRole("button", { name: "Salvar" }));
    expect(within(dialog).getByText("Escolha ao menos uma unidade.")).toBeInTheDocument();
    expect(calls).toEqual([]);
  });

  it("CA21: última administração lista as unidades; o Coordenador nomeia a si mesmo e a desativação é repetida", async () => {
    const u = userEvent.setup();
    lastAdmin = true;
    setup();
    await ready();
    await u.click(within(card("Bruno")).getByRole("button", { name: /Desativar/ }));
    const dialog = await screen.findByRole("dialog");
    await u.click(within(dialog).getByRole("button", { name: "Desativar" }));
    expect(await within(dialog).findByText("Estas unidades ficariam sem Administrador ativo:")).toBeInTheDocument();
    expect(within(dialog).getByText("Unidade Centro")).toBeInTheDocument();
    await u.click(within(dialog).getByRole("button", { name: "Nomear a mim mesmo" }));
    await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull());
    expect(calls.map((c) => c.path)).toEqual(["deactivate", "appointment", "deactivate"]);
    expect(calls[1].body).toEqual({ unitIds: ["u1"] });
    expect(screen.getByRole("status")).toHaveTextContent("Usuário desativado.");
    await waitFor(() => expect(screen.queryByText("Bruno")).toBeNull());
  });

  it("CA21: o Administrador vê só a explicação, sem Nomear a mim mesmo", async () => {
    const u = userEvent.setup();
    lastAdmin = true;
    rows = [user({ linkId: "k9", name: "Paulo", role: "TEACHER" })];
    setup("ADMINISTRATOR");
    await ready();
    await u.click(within(card("Paulo")).getByRole("button", { name: /Desativar/ }));
    const dialog = await screen.findByRole("dialog");
    await u.click(within(dialog).getByRole("button", { name: "Desativar" }));
    expect(await within(dialog).findByText(/Peça ao Coordenador/)).toBeInTheDocument();
    expect(within(dialog).queryByRole("button", { name: "Nomear a mim mesmo" })).toBeNull();
  });

  it.each([
    ["claro", false],
    ["escuro", true],
  ])("CA19: a 360 px não tem violações no axe no tema %s, com a lista e com a janela aberta", async (_n, dark) => {
    const u = userEvent.setup();
    setDevicePrefersDark(dark);
    window.innerWidth = 360;
    const { container } = setup();
    await ready();
    expect(await axeViolations(container)).toEqual([]);
    await u.click(screen.getByRole("button", { name: "Convidar" }));
    await screen.findByRole("dialog");
    expect(await axeViolations(document.body)).toEqual([]);
  });
});
