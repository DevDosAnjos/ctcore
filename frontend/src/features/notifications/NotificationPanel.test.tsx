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
import type { AppNotification, NotificationList } from "./useNotifications";
import { notificationTarget, relativeTime } from "./notificationActions";

const session = (role: string): SessionView => ({
  account: { id: "a", email: "x@x.com", name: "X" },
  links: [{ id: "l1", organizationId: "o", organizationName: "Alfa", role, unitIds: [] }],
  activeLinkId: "l1",
  twoFactor: { enabled: false, required: false },
});
const note = (id: string, type: AppNotification["type"], group: AppNotification["group"], read: boolean, params: AppNotification["params"] = {}): AppNotification => ({
  id,
  type,
  group,
  createdAt: new Date(Date.now() - (group === "TODAY" ? 5 * 60_000 : 3 * 86_400_000)).toISOString(),
  readAt: read ? "2026-10-24T10:00:00Z" : null,
  refType: type === "EMAIL_PROBLEM" ? "student" : "charge",
  refId: "00000000-0000-0000-0000-0000000000b1",
  params,
});
const initial = (): NotificationList => ({
  items: [
    note("1", "PAYMENT_RECEIVED", "TODAY", false, { studentName: "Maria Souza", amountCents: 15000 }),
    note("2", "HIGH_BENEFIT", "TODAY", true, { studentName: "João Lima" }),
    note("3", "EMAIL_PROBLEM", "WEEK", false),
    note("4", "INVITE_PENDING", "WEEK", true, { personName: "Paula Reis" }),
  ],
  unreadCount: 2,
});

let data: NotificationList;
let readAlls: number;
let puts: unknown[];

function setup(role: string, over: { list?: () => Response | Promise<Response>; count?: () => Response | Promise<Response>; prefs?: boolean } = {}) {
  data = initial();
  readAlls = 0;
  puts = [];
  let receiveAll = false;
  server.use(
    http.get("/api/v1/auth/session", () => HttpResponse.json(session(role))),
    http.get("/api/v1/permissions", () => HttpResponse.json({ permissions: mockPermissions(role) })),
    http.get("/api/v1/units/reachable", () => HttpResponse.json({ allUnits: false, units: [] })),
    http.get("/api/v1/notifications/unread-count", over.count ?? (() => HttpResponse.json({ count: data.unreadCount }))),
    http.get("/api/v1/notifications", over.list ?? (() => HttpResponse.json(data))),
    http.post("/api/v1/notifications/read-all", () => {
      readAlls += 1;
      const changed = data.unreadCount;
      data = { items: data.items.map((n) => ({ ...n, readAt: n.readAt ?? "2026-10-24T11:00:00Z" })), unreadCount: 0 };
      return HttpResponse.json({ changed });
    }),
    http.get("/api/v1/notifications/preferences", () => HttpResponse.json({ receiveAll, canReceiveAll: role === "COORDINATOR" })),
    http.put("/api/v1/notifications/preferences", async ({ request }) => {
      const body = (await request.json()) as { receiveAll: boolean };
      puts.push(body);
      receiveAll = body.receiveAll;
      return HttpResponse.json({ receiveAll, canReceiveAll: true });
    }),
  );
  return render(
    <ThemeProvider>
      <MemoryRouter>
        <SessionProvider>
          <App />
        </SessionProvider>
      </MemoryRouter>
    </ThemeProvider>,
  );
}

const bell = () => screen.findByRole("button", { name: /^Notificações/ });
async function openPanel(user: ReturnType<typeof userEvent.setup>) {
  await user.click(await bell());
  return screen.findByRole("dialog", { name: "Notificações" });
}

describe("notificationActions", () => {
  it("leva cada tipo à tela do objeto e mede o tempo em minutos, horas e dias", () => {
    expect(notificationTarget({ type: "EMAIL_PROBLEM", refType: "student", refId: "x" })).toBe("/alunos/x");
    expect(notificationTarget({ type: "EMAIL_PROBLEM", refType: "guardian", refId: "x" })).toBe("/alunos");
    expect(notificationTarget({ type: "PAYMENT_RECEIVED", refType: "charge", refId: "x" })).toBe("/cobrancas");
    expect(notificationTarget({ type: "INVITE_PENDING", refType: "invite", refId: "x" })).toBe("/configuracoes/usuarios");
    expect(notificationTarget({ type: "PORTAL_REQUEST", refType: "portal_request", refId: "x" })).toBe("/pedidos");
    expect(notificationTarget({ type: "PAYMENT_PROOF", refType: "payment_proof", refId: "x" })).toBe("/cobrancas/conferencia");
    expect(notificationTarget({ type: "CLOSURE_REQUEST", refType: "portal_request", refId: "x" })).toBe("/pedidos");
    const now = new Date("2026-10-24T12:00:00Z");
    expect(relativeTime("2026-10-24T11:59:40Z", now)).toEqual({ key: "now", count: 0 });
    expect(relativeTime("2026-10-24T11:15:00Z", now)).toEqual({ key: "minutes", count: 45 });
    expect(relativeTime("2026-10-24T07:00:00Z", now)).toEqual({ key: "hours", count: 5 });
    expect(relativeTime("2026-10-21T12:00:00Z", now)).toEqual({ key: "days", count: 3 });
  });
});

describe("NotificationPanel (RF-NOT-01)", () => {
  it("CA15: mostra os grupos, filtra as não lidas, leva ao objeto e marca todas como lidas", async () => {
    const user = userEvent.setup();
    setup("ADMINISTRATOR");
    expect(await screen.findByRole("button", { name: "Notificações, 2 não lidas" })).toBeInTheDocument();
    const panel = await openPanel(user);
    expect(await within(panel).findByRole("heading", { name: "Hoje" })).toBeInTheDocument();
    expect(within(panel).getByRole("heading", { name: "Esta semana" })).toBeInTheDocument();
    expect(within(panel).getByText(/Maria Souza pagou R\$.*150,00/)).toBeInTheDocument();
    expect(within(panel).getByRole("link", { name: "Ver cobranças" })).toHaveAttribute("href", "/cobrancas");
    expect(within(panel).getByRole("link", { name: "Ver usuários" })).toHaveAttribute("href", "/configuracoes/usuarios");
    expect(within(panel).queryByRole("switch")).not.toBeInTheDocument();

    await user.click(within(panel).getByRole("button", { name: "Não lidas (2)" }));
    expect(within(panel).getAllByRole("listitem")).toHaveLength(2);
    expect(within(panel).queryByText(/João Lima/)).not.toBeInTheDocument();

    await user.click(within(panel).getByRole("button", { name: "Marcar todas como lidas" }));
    expect(await within(panel).findByText("Tudo lido")).toBeInTheDocument();
    expect(readAlls).toBe(1);
    await waitFor(() => expect(screen.getByRole("button", { name: "Notificações", hidden: true })).toBeInTheDocument());
  });

  it("CA15: contador acima de 99 mostra 99+ e em zero o sino fica sem número", async () => {
    const first = setup("ADMINISTRATOR", { count: () => HttpResponse.json({ count: 150 }) });
    expect(await screen.findByText("99+")).toBeInTheDocument();
    first.unmount();
    setup("ADMINISTRATOR", { count: () => HttpResponse.json({ count: 0 }) });
    const button = await bell();
    expect(button).toHaveAccessibleName("Notificações");
    expect(within(button).queryByText(/\d/)).not.toBeInTheDocument();
  });

  it("CA16: o Coordenador vê Receber tudo e a mudança envia o PUT; o Administrador não vê a chave", async () => {
    const user = userEvent.setup();
    setup("COORDINATOR");
    const panel = await openPanel(user);
    const toggle = await within(panel).findByRole("switch", { name: /Receber tudo/ });
    expect(toggle).not.toBeChecked();
    await user.click(toggle);
    await waitFor(() => expect(puts).toEqual([{ receiveAll: true }]));
    await waitFor(() => expect(toggle).toBeChecked());
  });

  it("CA17: carregando, vazio, erro com tentar de novo e sem permissão", async () => {
    const user = userEvent.setup();
    const loading = setup("ADMINISTRATOR", { list: () => new Promise<Response>(() => {}) });
    expect(await within(await openPanel(user)).findByRole("status")).toHaveTextContent(/carregando/i);
    loading.unmount();

    const empty = setup("ADMINISTRATOR", { list: () => HttpResponse.json({ items: [], unreadCount: 0 }) });
    expect(await within(await openPanel(user)).findByRole("heading", { name: "Nenhuma notificação" })).toBeInTheDocument();
    empty.unmount();

    let fail = true;
    const broken = setup("ADMINISTRATOR", { list: () => (fail ? HttpResponse.json({ code: "INTERNAL_ERROR" }, { status: 500 }) : HttpResponse.json(initial())) });
    const panel = await openPanel(user);
    const retry = await within(panel).findByRole("button", { name: "Tentar de novo" });
    fail = false;
    await user.click(retry);
    expect(await within(panel).findByRole("heading", { name: "Hoje" })).toBeInTheDocument();
    broken.unmount();

    setup("ADMINISTRATOR", { list: () => HttpResponse.json({ code: "FORBIDDEN" }, { status: 403 }) });
    expect(await within(await openPanel(user)).findByRole("heading", { name: "Você não tem acesso" })).toBeInTheDocument();
  });

  it("CA17: perfil sem permissão não mostra o sino", async () => {
    setup("ADMINISTRATOR", { count: () => HttpResponse.json({ code: "FORBIDDEN" }, { status: 403 }) });
    await screen.findByRole("link", { name: "Início", hidden: true });
    await waitFor(() => expect(screen.queryByRole("button", { name: /^Notificações/ })).not.toBeInTheDocument());
  });

  it("CA17: Esc fecha o painel e o foco volta ao sino", async () => {
    const user = userEvent.setup();
    setup("ADMINISTRATOR");
    const button = await bell();
    await user.click(button);
    await screen.findByRole("dialog", { name: "Notificações" });
    await user.keyboard("{Escape}");
    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
    expect(button).toHaveFocus();
  });

  it.each([
    ["claro", false],
    ["escuro", true],
  ])("CA17: a 360 px não tem violações no axe no tema %s", async (_n, dark) => {
    const user = userEvent.setup();
    setDevicePrefersDark(dark);
    window.innerWidth = 360;
    setup("COORDINATOR");
    const panel = await openPanel(user);
    await within(panel).findByRole("heading", { name: "Hoje" });
    await within(panel).findByRole("switch");
    expect(await axeViolations(document.body)).toEqual([]);
  });
});
