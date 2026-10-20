import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { MemoryRouter, useLocation } from "react-router";
import { App } from "@/App";
import { SessionProvider, type SessionView } from "@/features/auth/SessionProvider";
import { mockPermissions } from "@/mocks/permissions";
import { server } from "@/mocks/server";
import { ThemeProvider } from "@/theme/ThemeProvider";
import type { StudentRecord, StudentRecordGuardian } from "./types";

const session = (): SessionView => ({
  account: { id: "a", email: "x@x.com", name: "X" },
  links: [{ id: "l1", organizationId: "o", organizationName: "Alfa", role: "COORDINATOR", unitIds: [] }],
  activeLinkId: "l1",
  twoFactor: { enabled: false, required: false },
});
const guardian = (over: Partial<StudentRecordGuardian> = {}): StudentRecordGuardian => ({
  guardianId: "g1", name: "Marta Souza", email: "marta@alfa.test", phone: "21987654321", financial: true, access: "PENDING", emailProblem: false, ...over,
});
const baseRecord = (guardians: StudentRecordGuardian[] = [guardian()]): StudentRecord => ({
  id: "s1", name: "Ana Souza", photoUrl: null, birthDate: "2014-05-02", age: 12, minor: true, ageRange: null, registeredUnit: { id: "u1", name: "Centro" }, seals: [],
  contact: { phone: null, email: null, emailProblem: false }, cpf: null, address: undefined, emergency: undefined, guardians,
});

function Where() {
  return <p hidden data-testid="where">{useLocation().pathname}</p>;
}
let record: StudentRecord;
let calls: { url: string; method: string }[];

function setup() {
  record = baseRecord();
  calls = [];
  server.use(
    http.get("/api/v1/auth/session", () => HttpResponse.json(session())),
    http.get("/api/v1/permissions", () => HttpResponse.json({ permissions: mockPermissions("COORDINATOR") })),
    http.get("/api/v1/units/reachable", () => HttpResponse.json({ allUnits: true, units: [] })),
    http.get("/api/v1/age-ranges", () => HttpResponse.json({ canManage: false, items: [] })),
    http.get("/api/v1/students", () => HttpResponse.json({ items: [], page: 1, size: 25, total: 0 })),
    http.get("/api/v1/students/:id/record", () => HttpResponse.json(record)),
    http.post("/api/v1/students/:id/guardians/:gid/invite", ({ request }) => {
      calls.push({ url: new URL(request.url).pathname, method: request.method });
      return new HttpResponse(null, { status: 204 });
    }),
  );
  return render(
    <ThemeProvider>
      <MemoryRouter initialEntries={["/alunos/s1"]}>
        <Where />
        <SessionProvider>
          <App />
        </SessionProvider>
      </MemoryRouter>
    </ThemeProvider>,
  );
}

async function open() {
  const user = userEvent.setup();
  const dialog = await screen.findByRole("dialog", { name: "Ficha do aluno" });
  await within(dialog).findByText("Marta Souza");
  await user.click(within(dialog).getByRole("tab", { name: "Responsáveis" }));
  return { user, dialog };
}
const type = (user: ReturnType<typeof userEvent.setup>, label: string, value: string) => user.type(screen.getByLabelText(label), value);
async function fillLink(user: ReturnType<typeof userEvent.setup>, cpf: string) {
  await screen.findByRole("dialog", { name: "Vincular responsável" });
  await type(user, "Nome", "Paulo Souza");
  await type(user, "CPF", cpf);
  await type(user, "E-mail", "paulo@alfa.test");
  await type(user, "Celular", "21987654321");
  await type(user, "Motivo", "novo responsável");
}

describe("ficha do aluno: aba Responsáveis (RN-RSP-02, RN-RSP-05)", () => {
  beforeEach(() => localStorage.clear());

  it("CA11: mostra financeiro e acesso; Reenviar convite só para pendente e confirma", async () => {
    setup();
    record = baseRecord([guardian(), guardian({ guardianId: "g2", name: "Paulo Souza", financial: false, access: "ACTIVE" })]);
    const { user, dialog } = await open();
    expect(within(screen.getByRole("tabpanel")).getByText("Financeiro")).toBeInTheDocument();
    expect(within(dialog).getByText("Acesso pendente")).toBeInTheDocument();
    expect(within(dialog).getByText("Acesso ativo")).toBeInTheDocument();
    expect(within(dialog).getAllByRole("button", { name: "Reenviar convite" })).toHaveLength(1);
    await user.click(within(dialog).getByRole("button", { name: "Reenviar convite" }));
    expect(await within(dialog).findByText("Convite reenviado para Marta Souza.")).toBeInTheDocument();
    expect(calls.at(-1)).toEqual({ url: "/api/v1/students/s1/guardians/g1/invite", method: "POST" });
  });

  it("CA11: Tornar financeiro troca o papel e recarrega a ficha", async () => {
    setup();
    record = baseRecord([guardian(), guardian({ guardianId: "g2", name: "Paulo Souza", financial: false, access: "ACTIVE" })]);
    let sent: unknown;
    server.use(
      http.put("/api/v1/students/:id/guardians/:gid", async ({ request }) => {
        sent = await request.json();
        record = baseRecord([guardian({ financial: false }), guardian({ guardianId: "g2", name: "Paulo Souza", financial: true, access: "ACTIVE" })]);
        return HttpResponse.json({});
      }),
    );
    const { user, dialog } = await open();
    await user.click(within(dialog).getByRole("button", { name: "Tornar financeiro" }));
    expect(await within(dialog).findByText("Paulo Souza agora é o responsável financeiro.")).toBeInTheDocument();
    expect(sent).toEqual({ financial: true });
    await waitFor(() => expect(within(screen.getByRole("tabpanel")).getAllByText("Financeiro")).toHaveLength(1));
  });

  it("CA11: Desvincular pede o motivo e a recusa do servidor aparece no painel sem fechar", async () => {
    setup();
    server.use(http.post("/api/v1/students/:id/guardians/:gid/unlink", () => HttpResponse.json({ code: "MINOR_NEEDS_GUARDIAN" }, { status: 409 })));
    const { user, dialog } = await open();
    await user.click(within(dialog).getByRole("button", { name: "Desvincular" }));
    const confirm = await screen.findByRole("dialog", { name: "Desvincular Marta Souza" });
    await user.type(within(confirm).getByLabelText(/Motivo/), "saiu da família");
    await user.click(within(confirm).getByRole("button", { name: "Desvincular" }));
    expect(await screen.findByText(/precisa de ao menos um responsável/)).toBeInTheDocument();
    expect(screen.getByRole("dialog", { name: "Ficha do aluno" })).toBeInTheDocument();
    expect(screen.getByTestId("where").textContent).toBe("/alunos/s1");
  });

  it("CA11: Desvincular com sucesso envia o motivo e recarrega a lista", async () => {
    setup();
    record = baseRecord([guardian(), guardian({ guardianId: "g2", name: "Paulo Souza", financial: false })]);
    let sent: unknown;
    server.use(
      http.post("/api/v1/students/:id/guardians/:gid/unlink", async ({ request }) => {
        sent = await request.json();
        record = baseRecord([guardian({ guardianId: "g2", name: "Paulo Souza", financial: true })]);
        return HttpResponse.json({});
      }),
    );
    const { user, dialog } = await open();
    await user.click(within(dialog).getAllByRole("button", { name: "Desvincular" })[0]);
    const confirm = await screen.findByRole("dialog", { name: "Desvincular Marta Souza" });
    await user.type(within(confirm).getByLabelText(/Motivo/), "mudou de cidade");
    await user.click(within(confirm).getByRole("button", { name: "Desvincular" }));
    expect(await screen.findByText("Marta Souza foi desvinculado.")).toBeInTheDocument();
    expect(sent).toEqual({ reason: "mudou de cidade" });
    await waitFor(() => expect(screen.queryByText("Marta Souza", { selector: "p" })).toBeNull());
  });

  it("CA11: reenvio recusado (429) mostra o erro no painel", async () => {
    setup();
    server.use(http.post("/api/v1/students/:id/guardians/:gid/invite", () => HttpResponse.json({ code: "TOO_MANY_REQUESTS" }, { status: 429 })));
    const { user, dialog } = await open();
    await user.click(within(dialog).getByRole("button", { name: "Reenviar convite" }));
    expect(await within(dialog).findByRole("alert")).toHaveTextContent("Muitos reenvios hoje");
  });

  it("CA11: Vincular responsável com CPF inválido mostra o erro do campo e não envia", async () => {
    setup();
    let posted = 0;
    server.use(http.post("/api/v1/students/:id/guardians", () => (posted++, HttpResponse.json({}))));
    const { user, dialog } = await open();
    await user.click(within(dialog).getByRole("button", { name: "Vincular responsável" }));
    await fillLink(user, "11111111111");
    await user.click(screen.getByRole("button", { name: "Vincular" }));
    expect(screen.getAllByText("Preencha este campo.").length).toBeGreaterThan(0);
    expect(posted).toBe(0);
  });

  it("CA11: Vincular responsável com dados válidos envia, fecha e recarrega a lista com o novo", async () => {
    setup();
    let sent: Record<string, unknown> = {};
    server.use(
      http.post("/api/v1/students/:id/guardians", async ({ request }) => {
        sent = (await request.json()) as Record<string, unknown>;
        record = baseRecord([guardian(), guardian({ guardianId: "g2", name: "Paulo Souza", financial: false })]);
        return HttpResponse.json({});
      }),
    );
    const { user, dialog } = await open();
    await user.click(within(dialog).getByRole("button", { name: "Vincular responsável" }));
    await fillLink(user, "52998224725");
    await user.click(screen.getByRole("button", { name: "Vincular" }));
    expect(await screen.findByText("Paulo Souza foi vinculado ao aluno.")).toBeInTheDocument();
    expect(sent).toMatchObject({ name: "Paulo Souza", cpf: "52998224725", email: "paulo@alfa.test", phone: "21987654321", financial: false, reason: "novo responsável" });
    expect(await screen.findByText("Paulo Souza", { selector: "p" })).toBeInTheDocument();
  });

  it("CA11: erro do servidor no vínculo (limite e campo) aparece na janela sem fechar", async () => {
    setup();
    let limit = true;
    server.use(
      http.post("/api/v1/students/:id/guardians", () =>
        limit ? HttpResponse.json({ code: "GUARDIAN_LIMIT" }, { status: 409 }) : HttpResponse.json({ code: "VALIDATION_ERROR", fields: [{ field: "guardians.cpf", message: "CPF inválido" }] }, { status: 400 }),
      ),
    );
    const { user, dialog } = await open();
    await user.click(within(dialog).getByRole("button", { name: "Vincular responsável" }));
    await fillLink(user, "52998224725");
    const link = screen.getByRole("dialog", { name: "Vincular responsável" });
    await user.click(within(link).getByRole("button", { name: "Vincular" }));
    expect(await within(link).findByRole("alert")).toHaveTextContent("no máximo dois responsáveis");
    limit = false;
    await user.click(within(link).getByRole("button", { name: "Vincular" }));
    await waitFor(() => expect(within(link).getAllByText("Confira os dados informados.").length).toBeGreaterThan(0));
    await user.click(within(link).getByRole("button", { name: "Cancelar" }));
    await waitFor(() => expect(screen.queryByRole("dialog", { name: "Vincular responsável" })).toBeNull());
  });
});
