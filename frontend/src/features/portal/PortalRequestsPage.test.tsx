import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { MemoryRouter } from "react-router";
import { App } from "@/App";
import { SessionProvider, type SessionView } from "@/features/auth/SessionProvider";
import type { PortalRequest } from "@/features/portal-requests/types";
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
const done: PortalRequest = {
  id: "r1", kind: "PLAN_CHANGE", status: "REFUSED", studentId: "s1", studentName: "Aluno", unitId: "u1", unit: "Centro", modalityRowId: "m1", modality: "Judô", targetClassId: null, targetClass: null,
  targetPlanId: "p2", targetPlan: "Judô Anual", targetModalityId: null, targetUnitId: null, targetUnit: null, reason: null, note: null, response: "Fale com a recepção.", createdAt: "2026-10-20T15:00:00Z", decidedAt: "2026-10-21T15:00:00Z",
};
const options = (closureRequestedAt: string | null = null) => ({
  modalities: [
    { modalityRowId: "m1", modalityId: "d1", modality: "Judô", unitId: "u1", unit: "Centro", currentClass: { id: "k1", name: "Judô Manhã" }, currentPlan: { id: "p1", name: "Judô Mensal" }, closureRequestedAt, classes: [{ id: "k2", name: "Judô Noite" }], plans: [{ id: "p2", name: "Judô Anual" }] },
  ],
  offers: [{ unitId: "u1", unit: "Centro", modalityId: "d2", modality: "Boxe" }],
});

let posted: unknown[];
function setup(over: { students?: () => Response; requests?: PortalRequest[]; closure?: string | null; role?: string } = {}) {
  posted = [];
  window.history.replaceState(null, "", "/portal/pedidos");
  server.use(
    http.get("/api/v1/auth/session", () => HttpResponse.json(session(over.role ?? "STUDENT"))),
    http.get("/api/v1/portal/term", () => HttpResponse.json([])),
    http.get("/api/v1/portal/students", over.students ?? (() => HttpResponse.json([{ id: "s1", name: "Aluno Adulto Fictício", photoUrl: null, self: true }]))),
    http.get("/api/v1/portal/students/:id/request-options", () => HttpResponse.json(options(over.closure ?? null))),
    http.get("/api/v1/portal/students/:id/requests", () => HttpResponse.json(over.requests ?? [done])),
    http.post("/api/v1/portal/students/:id/requests", async ({ request }) => {
      posted.push(await request.json());
      return HttpResponse.json(done, { status: 201 });
    }),
  );
  return render(
    <ThemeProvider>
      <MemoryRouter initialEntries={["/portal/pedidos"]}>
        <SessionProvider>
          <App />
        </SessionProvider>
      </MemoryRouter>
    </ThemeProvider>,
  );
}

describe("PortalRequestsPage do portal (RF-MAT-04)", () => {
  it("CA20: mostra cada pedido com situação e resposta e passa no axe nos dois temas", async () => {
    for (const dark of [false, true]) {
      setDevicePrefersDark(dark);
      const { container, unmount } = setup();
      expect(await screen.findByRole("heading", { name: "Meus pedidos", level: 1 })).toBeInTheDocument();
      expect(await screen.findByText("Resposta da academia: Fale com a recepção.")).toBeInTheDocument();
      expect(screen.getByText(/^Recusado · /)).toBeInTheDocument();
      expect(await axeViolations(container)).toEqual([]);
      unmount();
    }
  });

  it("CA20: pedir troca de turma lista só as turmas válidas e envia o pedido", async () => {
    const user = userEvent.setup();
    setup();
    await user.click(await screen.findByRole("button", { name: "Pedir troca de turma" }));
    const dialog = await screen.findByRole("dialog", { name: "Pedir troca de turma" });
    expect(within(dialog).getAllByRole("option").map((o) => o.textContent).slice(1)).toEqual(["Judô Noite"]);
    await user.selectOptions(within(dialog).getByLabelText("Turma desejada"), "k2");
    await user.type(within(dialog).getByLabelText("Mensagem (opcional)"), "Troco por causa do trabalho");
    await user.click(within(dialog).getByRole("button", { name: "Enviar pedido" }));
    await waitFor(() => expect(posted).toEqual([{ kind: "CLASS_CHANGE", note: "Troco por causa do trabalho", modalityRowId: "m1", targetClassId: "k2" }]));
    await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull());
  });

  it("CA20: Solicitar encerramento exige o motivo da lista", async () => {
    const user = userEvent.setup();
    setup();
    await user.click(await screen.findByRole("button", { name: "Solicitar encerramento" }));
    const dialog = await screen.findByRole("dialog", { name: "Solicitar encerramento" });
    const submit = within(dialog).getByRole("button", { name: "Solicitar encerramento" });
    expect(submit).toBeDisabled();
    await user.selectOptions(within(dialog).getByLabelText("Motivo"), "FINANCIAL");
    await user.click(submit);
    await waitFor(() => expect(posted).toEqual([{ kind: "CLOSURE", modalityRowId: "m1", reason: "FINANCIAL" }]));
  });

  it("nova modalidade envia unidade e modalidade ofertadas", async () => {
    const user = userEvent.setup();
    setup();
    await user.click(await screen.findByRole("button", { name: "Pedir nova modalidade" }));
    const dialog = await screen.findByRole("dialog", { name: "Pedir nova modalidade" });
    await user.selectOptions(within(dialog).getByLabelText("Modalidade e unidade"), "u1|d2");
    await user.click(within(dialog).getByRole("button", { name: "Enviar pedido" }));
    await waitFor(() => expect(posted).toEqual([{ kind: "NEW_MODALITY", targetUnitId: "u1", targetModalityId: "d2" }]));
  });

  it("409 mostra o aviso na janela e não fecha", async () => {
    const user = userEvent.setup();
    setup();
    server.use(http.post("/api/v1/portal/students/:id/requests", () => HttpResponse.json({ code: "REQUEST_ALREADY_OPEN", message: "x" }, { status: 409 })));
    await user.click(await screen.findByRole("button", { name: "Pedir troca de plano" }));
    const dialog = await screen.findByRole("dialog");
    await user.selectOptions(within(dialog).getByLabelText("Plano desejado"), "p2");
    await user.click(within(dialog).getByRole("button", { name: "Enviar pedido" }));
    expect(await within(dialog).findByRole("alert")).toHaveTextContent("Já existe um pedido igual em aberto.");
  });

  it("encerramento aberto troca os botões pelo aviso", async () => {
    setup({ closure: "2026-10-20T15:00:00Z" });
    expect(await screen.findByText(/Encerramento solicitado em/)).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Pedir troca de turma" })).toBeNull();
  });

  it("sem pedidos mostra o vazio; falha mostra erro com nova tentativa; 403 mostra sem permissão", async () => {
    const { unmount } = setup({ requests: [] });
    expect(await screen.findByText("Nenhum pedido ainda")).toBeInTheDocument();
    unmount();
    const err = setup({ students: () => new HttpResponse(null, { status: 500 }) });
    expect(await screen.findByRole("button", { name: /tentar de novo/i })).toBeInTheDocument();
    err.unmount();
    setup({ students: () => HttpResponse.json({ code: "FORBIDDEN", message: "x" }, { status: 403 }) });
    expect(await screen.findByText("Você não tem acesso")).toBeInTheDocument();
  });
});
