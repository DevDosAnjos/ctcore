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
import type { PortalRequest } from "./types";

const session = (role: string): SessionView => ({
  account: { id: "a", email: "x@x.com", name: "X" },
  links: [{ id: "l1", organizationId: "o", organizationName: "Alfa", role, unitIds: [] }],
  activeLinkId: "l1",
  twoFactor: { enabled: false, required: false },
});
const request = (over: Partial<PortalRequest>): PortalRequest => ({
  id: "r1",
  kind: "CLASS_CHANGE",
  status: "OPEN",
  studentId: "s1",
  studentName: "Aluno Adulto Fictício",
  unitId: "u1",
  unit: "Centro",
  modalityRowId: "m1",
  modality: "Judô",
  targetClassId: "k2",
  targetClass: "Judô Noite",
  targetPlanId: null,
  targetPlan: null,
  targetModalityId: null,
  targetUnitId: null,
  targetUnit: null,
  reason: null,
  note: "Troco por causa do trabalho",
  response: null,
  createdAt: "2026-10-20T15:00:00Z",
  decidedAt: null,
  ...over,
});
const all = [
  request({}),
  request({ id: "r2", kind: "CLOSURE", targetClassId: null, targetClass: null, reason: "FINANCIAL", note: null, studentName: "Aluno Menor Fictício", studentId: "s2" }),
];

let calls: URLSearchParams[];
let decisions: { url: string; body: unknown }[];

function setup(role = "COORDINATOR", list?: () => Response | Promise<Response>) {
  calls = [];
  decisions = [];
  window.history.replaceState(null, "", "/pedidos");
  const decide =
    (status: string) =>
    async ({ request: req, params }: { request: Request; params: Record<string, unknown> }) => {
      const body = await req.json().catch(() => ({}));
      decisions.push({ url: req.url, body });
      return HttpResponse.json({ ...all.find((r) => r.id === params.id)!, status, response: (body as { message?: string }).message ?? null });
    };
  server.use(
    http.get("/api/v1/auth/session", () => HttpResponse.json(session(role))),
    http.get("/api/v1/permissions", () => HttpResponse.json({ permissions: mockPermissions(role) })),
    http.get("/api/v1/units/reachable", () => HttpResponse.json({ allUnits: true, units: [{ id: "u1", name: "Centro", shortName: "c" }] })),
    http.get("/api/v1/portal-requests", ({ request: req }) => {
      calls.push(new URL(req.url).searchParams);
      return list ? list() : HttpResponse.json({ items: all, page: 1, size: 25, total: all.length });
    }),
    http.post("/api/v1/portal-requests/:id/approve", decide("APPROVED")),
    http.post("/api/v1/portal-requests/:id/refuse", decide("REFUSED")),
  );
  return render(
    <ThemeProvider>
      <MemoryRouter initialEntries={["/pedidos"]}>
        <SessionProvider>
          <App />
        </SessionProvider>
      </MemoryRouter>
    </ThemeProvider>,
  );
}

const ready = () => screen.findByRole("heading", { name: "Pedidos do portal", level: 1 });

describe("PortalRequestsPage (RF-MAT-04)", () => {
  it("CA19: lista tipo, aluno, unidade, pedido, mensagem e data; pede os abertos por padrão", async () => {
    setup();
    await ready();
    const row = (await screen.findAllByText("Aluno Adulto Fictício"))[0].closest("tr")!;
    expect(within(row).getByText("Troca de turma")).toBeInTheDocument();
    expect(within(row).getByText("Centro")).toBeInTheDocument();
    expect(within(row).getByText("Judô: para a turma Judô Noite")).toBeInTheDocument();
    expect(within(row).getByText("Troco por causa do trabalho")).toBeInTheDocument();
    const closure = screen.getAllByText("Aluno Menor Fictício")[0].closest("tr")!;
    expect(within(closure).getByText("Judô: encerrar (Motivo financeiro)")).toBeInTheDocument();
    expect(within(closure).queryByRole("button", { name: /^Aprovar/ })).toBeNull();
    expect(within(closure).getByRole("link", { name: "Abrir ficha de Aluno Menor Fictício para encerrar" })).toHaveAttribute("href", "/alunos/s2");
    expect(calls.at(-1)!.get("status")).toBeNull();
  });

  it("filtros de situação, tipo e unidade refazem a consulta", async () => {
    const user = userEvent.setup();
    setup();
    await screen.findAllByText("Aluno Adulto Fictício");
    await user.selectOptions(screen.getByLabelText("Situação"), "ALL");
    await waitFor(() => expect(calls.at(-1)!.get("status")).toBe("ALL"));
    await user.selectOptions(screen.getByLabelText("Tipo"), "CLOSURE");
    await waitFor(() => expect(calls.at(-1)!.get("kind")).toBe("CLOSURE"));
    await waitFor(() => expect(within(screen.getByLabelText("Unidade")).getAllByRole("option")).toHaveLength(2));
    await user.selectOptions(screen.getByLabelText("Unidade"), "u1");
    await waitFor(() => expect(calls.at(-1)!.get("unitId")).toBe("u1"));
  });

  it("CA19: Recusar abre a janela, exige mensagem de 3 caracteres e some o pedido da lista aberta", async () => {
    const user = userEvent.setup();
    setup();
    await user.click((await screen.findAllByRole("button", { name: "Recusar pedido de Aluno Adulto Fictício" }))[0]);
    const dialog = await screen.findByRole("dialog", { name: "Recusar pedido" });
    const submit = within(dialog).getByRole("button", { name: "Recusar pedido" });
    expect(submit).toBeDisabled();
    await user.type(within(dialog).getByLabelText("Mensagem ao aluno"), "ab");
    expect(submit).toBeDisabled();
    await user.type(within(dialog).getByLabelText("Mensagem ao aluno"), "c");
    server.use(http.get("/api/v1/portal-requests", () => HttpResponse.json({ items: [all[1]], page: 1, size: 25, total: 1 })));
    await user.click(submit);
    await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull());
    expect(decisions).toEqual([{ url: expect.stringContaining("/portal-requests/r1/refuse"), body: { message: "abc" } }]);
    await waitFor(() => expect(screen.queryByText("Troco por causa do trabalho")).toBeNull());
  });

  it("CA19: Aprovar marca o pedido e leva à ficha do aluno", async () => {
    const user = userEvent.setup();
    setup();
    await user.click((await screen.findAllByRole("button", { name: "Aprovar pedido de Aluno Adulto Fictício" }))[0]);
    const dialog = await screen.findByRole("dialog", { name: "Aprovar pedido" });
    await user.type(within(dialog).getByLabelText("Mensagem ao aluno (opcional)"), "Pode vir");
    await user.click(within(dialog).getByRole("button", { name: "Aprovar e continuar" }));
    await waitFor(() => expect(decisions).toEqual([{ url: expect.stringContaining("/portal-requests/r1/approve"), body: { message: "Pode vir" } }]));
    await waitFor(() => expect(screen.queryByRole("heading", { name: "Pedidos do portal" })).toBeNull());
  });

  it("CA19: vazio, erro com nova tentativa e sem permissão", async () => {
    const user = userEvent.setup();
    let fail = true;
    const view = setup("COORDINATOR", () => (fail ? HttpResponse.json({ code: "INTERNAL_ERROR" }, { status: 500 }) : HttpResponse.json({ items: [], page: 1, size: 25, total: 0 })));
    const retry = await screen.findByRole("button", { name: "Tentar de novo" });
    fail = false;
    await user.click(retry);
    expect(await screen.findByText("Nenhum pedido")).toBeInTheDocument();
    view.unmount();
    setup("COORDINATOR", () => HttpResponse.json({ code: "FORBIDDEN" }, { status: 403 }));
    expect(await screen.findByText("Você não tem acesso")).toBeInTheDocument();
  });

  it("CA19: o item Pedidos aparece no menu Pessoas", async () => {
    setup("ADMINISTRATOR");
    await ready();
    expect(screen.getAllByRole("link", { name: "Pedidos" })[0]).toHaveAttribute("href", "/pedidos");
  });

  it.each([
    ["claro", false],
    ["escuro", true],
  ])("CA19: a 360 px não tem violações no axe no tema %s", async (_n, dark) => {
    setDevicePrefersDark(dark);
    window.innerWidth = 360;
    const { container } = setup();
    await screen.findAllByText("Aluno Adulto Fictício");
    expect(await axeViolations(container)).toEqual([]);
  });
});
