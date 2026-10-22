import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { MemoryRouter } from "react-router";
import { SessionProvider, type SessionView } from "@/features/auth/SessionProvider";
import type { ChargeRow } from "@/features/charges/types";
import { mockPermissions } from "@/mocks/permissions";
import { server } from "@/mocks/server";
import { axeViolations } from "@/test/axe";
import { setDevicePrefersDark } from "@/test/device";
import { ThemeProvider } from "@/theme/ThemeProvider";
import { StudentRecordPanel } from "./StudentRecordPanel";
import type { StudentRecord } from "./types";

const session = (role: string): SessionView => ({
  account: { id: "a", email: "x@x.com", name: "X" },
  links: [{ id: "l1", organizationId: "o", organizationName: "Alfa", role, unitIds: [] }],
  activeLinkId: "l1",
  twoFactor: { enabled: false, required: false },
});
const record: StudentRecord = {
  id: "s1", name: "Ana Souza", photoUrl: null, birthDate: "1990-05-02", age: 36, minor: false, ageRange: null, registeredUnit: { id: "u1", name: "Centro" }, seals: [],
  contact: { phone: "21912345678", email: "ana@alfa.test", emailProblem: false }, cpf: null,
  address: { postalCode: "01001000", street: "Rua Um", number: "1", complement: null, district: "Centro", city: "Rio", state: "RJ" }, guardians: [],
};
const charge = (over: Partial<ChargeRow> = {}): ChargeRow => ({
  id: "c1", student: { id: "s1", name: "Ana Souza", photoUrl: null }, unit: { id: "u1", name: "Centro" }, type: "MONTHLY", modality: { id: "m1", name: "Jiu-Jitsu" },
  referenceMonth: "2026-03-01", description: null, amountCents: 12000, dueOn: "2026-03-05", status: "OVERDUE", payment: null, ...over,
});
const paid = charge({ id: "c2", referenceMonth: "2026-02-01", dueOn: "2026-02-05", status: "PAID", payment: { method: "PIX_MP", paidAt: "2026-02-04T12:00:00Z" } });
const list = (items: ChargeRow[]) => ({ items, page: 1, size: 100, total: items.length, counts: { overdue: 0, dueToday: 0, inReview: 0, paidMonth: 0 }, totals: { count: items.length, amountCents: 0 } });

let queries: string[];

function setup(opts: { role?: string; charges?: () => Response | Promise<Response> } = {}) {
  queries = [];
  const role = opts.role ?? "COORDINATOR";
  server.use(
    http.get("/api/v1/auth/session", () => HttpResponse.json(session(role))),
    http.get("/api/v1/permissions", () => HttpResponse.json({ permissions: mockPermissions(role) })),
    http.get("/api/v1/students/s1/record", () => HttpResponse.json(record)),
    http.get("/api/v1/students/s1/enrollment", () => HttpResponse.json({ id: null, status: "NONE", enrolledAt: null, reopenedAt: null, modalities: [] })),
    http.get("/api/v1/charges", ({ request }) => {
      queries.push(new URL(request.url).search);
      return opts.charges ? opts.charges() : HttpResponse.json(list([charge(), paid]));
    }),
    http.get("/api/v1/charges/c1", () =>
      HttpResponse.json({
        charge: charge(), toleranceDays: 5, composition: { plan: null, planCents: 12000, benefitType: null, benefitCents: 0, amountCents: 12000 },
        payer: { id: "s1", name: "Ana Souza", kind: "STUDENT" }, origin: null, replacedBy: [], reason: null, canceledAt: null, canceledBy: null, payments: [], reminders: [],
        actions: { cancel: true, renegotiate: true, registerPayment: false, sendReminder: false, refund: false },
      }),
    ),
  );
  return render(
    <ThemeProvider>
      <MemoryRouter>
        <SessionProvider>
          <StudentRecordPanel id="s1" onClose={() => {}} />
        </SessionProvider>
      </MemoryRouter>
    </ThemeProvider>,
  );
}

const panel = () => screen.findByRole("dialog", { name: "Ficha do aluno" });
async function openTab(user = userEvent.setup()) {
  const dialog = await panel();
  await user.click(await within(dialog).findByRole("tab", { name: "Financeiro" }));
  return dialog;
}

describe("aba Financeiro da ficha (RF-COB-02, R14)", () => {
  it("CA18: lista as cobranças com situação em texto, vencimento, valor e meio da paga; o cabeçalho mostra Em atraso; o clique abre o painel", async () => {
    const user = userEvent.setup();
    setup();
    const dialog = await openTab(user);
    expect(await within(dialog).findByText("Vence em 05/03/2026 · R$ 120,00")).toBeInTheDocument();
    expect(within(dialog).getByText("Vence em 05/02/2026 · R$ 120,00 · Pix")).toBeInTheDocument();
    expect(within(dialog).getAllByText("Em atraso").length).toBeGreaterThanOrEqual(2);
    expect(within(dialog).getByText("Paga")).toBeInTheDocument();
    expect(queries).toEqual(["?studentId=s1&size=100"]);
    await user.click(within(dialog).getByRole("button", { name: /Abrir cobrança de Ana Souza: Jiu-Jitsu · 03\/2026/ }));
    const charge = await screen.findByRole("dialog", { name: "Cobrança" });
    expect(await within(charge).findByRole("button", { name: "Renegociar" })).toBeEnabled();
    expect(within(charge).queryByRole("link", { name: "Ver aluno" })).toBeNull();
  });

  it("CA18: sem cobrança em atraso o cabeçalho mostra Em dia", async () => {
    setup({ charges: () => HttpResponse.json(list([paid])) });
    const dialog = await panel();
    expect(await within(dialog).findByText("Em dia")).toBeInTheDocument();
  });

  it("CA18: Professor não vê a aba nem o selo, e a chamada de cobranças nem é feita", async () => {
    setup({ role: "TEACHER" });
    const dialog = await panel();
    await within(dialog).findByText("Ana Souza");
    expect(within(dialog).queryByRole("tab", { name: "Financeiro" })).toBeNull();
    expect(within(dialog).queryByText("Em dia")).toBeNull();
    expect(queries).toEqual([]);
  });

  it("CA20: sem cobranças mostra o estado vazio", async () => {
    setup({ charges: () => HttpResponse.json(list([])) });
    const dialog = await openTab();
    expect(await within(dialog).findByText("Este aluno ainda não tem cobranças.")).toBeInTheDocument();
  });

  it("CA20: 500 pede nova tentativa e a nova tentativa traz a lista", async () => {
    const user = userEvent.setup();
    let fail = true;
    let release!: () => void;
    const gate = new Promise<void>((r) => { release = r; });
    setup({ charges: async () => (await gate, fail ? HttpResponse.json({ code: "X" }, { status: 500 }) : HttpResponse.json(list([charge()]))) });
    const dialog = await openTab(user);
    expect(await within(dialog).findByText("Carregando...")).toBeInTheDocument();
    release();
    const retry = await within(dialog).findByRole("button", { name: "Tentar de novo" });
    fail = false;
    await user.click(retry);
    expect(await within(dialog).findByText("Vence em 05/03/2026 · R$ 120,00")).toBeInTheDocument();
  });

  it("CA20: 403 mostra sem permissão", async () => {
    setup({ charges: () => HttpResponse.json({ code: "FORBIDDEN" }, { status: 403 }) });
    expect(await within(await openTab()).findByText("Você não tem acesso")).toBeInTheDocument();
  });

  it("CA20: a 360 px a aba passa no axe nos dois temas", async () => {
    for (const dark of [false, true]) {
      setDevicePrefersDark(dark);
      window.innerWidth = 360;
      const { unmount } = setup();
      const dialog = await openTab();
      await within(dialog).findByText("Vence em 05/03/2026 · R$ 120,00");
      await waitFor(async () => expect(await axeViolations(dialog)).toEqual([]));
      unmount();
    }
  });
});
