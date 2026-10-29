import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { MemoryRouter, useLocation } from "react-router";
import { App } from "@/App";
import { SessionProvider, type SessionView } from "@/features/auth/SessionProvider";
import { server } from "@/mocks/server";
import { axeViolations } from "@/test/axe";
import { setDevicePrefersDark } from "@/test/device";
import { ThemeProvider } from "@/theme/ThemeProvider";
import type { FinanceCharge, PortalFinanceData } from "./usePortalFinance";

// FormData e File do jsdom não passam pelo fetch do Node: o envio é trocado por um duplo simples e o resto usa o MSW.
const send = vi.hoisted(() => ({ calls: [] as unknown[][] }));
vi.mock("./usePixProof", async (original) => ({
  ...(await original<typeof import("./usePixProof")>()),
  sendProof: (...args: unknown[]) => {
    send.calls.push(args);
    return Promise.resolve([]);
  },
}));

const session: SessionView = {
  account: { id: "a", email: "x@x.com", name: "Ana Silva" },
  links: [{ id: "l1", organizationId: "o", organizationName: "Alfa", role: "GUARDIAN", unitIds: [] }],
  activeLinkId: "l1",
  twoFactor: { enabled: false, required: false },
};
const centro = { id: "u1", name: "Centro" };
const norte = { id: "u2", name: "Norte" };
const charge = (over: Partial<FinanceCharge>): FinanceCharge => ({
  id: "c1",
  studentId: "s1",
  studentName: "Ana Souza",
  type: "MONTHLY",
  description: null,
  referenceLabel: "Jiu-Jitsu · 10/2026",
  referenceMonth: "2026-10-01",
  dueOn: "2026-10-05",
  situation: "OVERDUE",
  amountCents: 9000,
  planCents: 10000,
  benefitCents: 1000,
  canPay: true,
  modality: { id: "m1", name: "Jiu-Jitsu" },
  unit: centro,
  ...over,
});
const finance = (over: Partial<PortalFinanceData> = {}): PortalFinanceData => ({
  reduced: false,
  students: [{ id: "s1", name: "Ana Souza", photoUrl: null, self: true, openCents: 21000 }],
  selectedStudentId: "s1",
  scope: "STUDENT",
  year: 2026,
  years: [2026, 2025],
  familyOpenCents: 21000,
  open: [
    charge({}),
    charge({ id: "c2", referenceLabel: "Kimono", type: "ONE_OFF", referenceMonth: null, dueOn: "2026-11-20", situation: "PENDING", amountCents: 3000, planCents: 3000, benefitCents: 0 }),
    charge({ id: "c3", referenceLabel: "Judô · 10/2026", situation: "IN_REVIEW", canPay: false, amountCents: 9000, unit: norte }),
    charge({ id: "c4", referenceLabel: "Seminário", situation: "DUE_SOON", amountCents: 4000, planCents: 4000, benefitCents: 0, unit: norte }),
  ],
  history: [
    { id: "h1", referenceLabel: "Jiu-Jitsu · 03/2026", dueOn: "2026-03-05", amountCents: 12000, status: "PAID", paidAt: "2026-03-04T15:00:00Z", method: "PIX_KEY", receipt: { number: 7, code: "RC-7" } },
    { id: "h2", referenceLabel: "Jiu-Jitsu · 02/2026", dueOn: "2026-02-05", amountCents: 12000, status: "CANCELED", paidAt: null, method: null, receipt: null },
  ],
  ...over,
});
const serve = (data: PortalFinanceData | ((info: { request: Request }) => Response | Promise<Response>)) =>
  server.use(http.get("/api/v1/portal/finance", typeof data === "function" ? data : () => HttpResponse.json(data)));
const batch = {
  pixKey: "pix@alfa.test",
  totalCents: 12000,
  unit: "Centro",
  charges: [
    { chargeId: "c1", studentName: "Ana Souza", amountCents: 9000, dueOn: "2026-10-05" },
    { chargeId: "c2", studentName: "Ana Souza", amountCents: 3000, dueOn: "2026-11-20" },
  ],
};

function Where() {
  const { pathname, search } = useLocation();
  return <span data-testid="where">{pathname + search}</span>;
}
function setup(path = "/portal/financeiro") {
  server.use(
    http.get("/api/v1/auth/session", () => HttpResponse.json(session)),
    http.get("/api/v1/portal/term", () => HttpResponse.json([])),
    http.get("/api/v1/portal/students", () => HttpResponse.json([])),
    http.get("/api/v1/portal/shell", () => HttpResponse.json({ organization: { name: "Academia Alfa", color: null, logoUrl: null }, reduced: false, unreadAnnouncements: 0 })),
  );
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

describe("PortalFinancePage (RF-POR-05)", () => {
  beforeEach(() => {
    send.calls.length = 0;
  });

  it("CA9: abertas na ordem do servidor, composição aberta por botão, Em conferência sem Pagar, histórico com recibo e débito automático inativo (CA8)", async () => {
    serve(finance());
    const user = userEvent.setup();
    setup();
    const open = within(await screen.findByRole("region", { name: "Em aberto" }));
    expect(open.getAllByRole("listitem").map((li) => li.querySelector("p")?.textContent)).toEqual(["Jiu-Jitsu · 10/2026", "Kimono", "Judô · 10/2026", "Seminário"]);
    expect(open.getByText("Em conferência")).toBeInTheDocument();
    expect(open.getAllByRole("button", { name: /^Pagar / })).toHaveLength(3);
    expect(open.queryByRole("button", { name: "Pagar Judô · 10/2026" })).not.toBeInTheDocument();
    expect(open.queryByRole("checkbox", { name: "Selecionar Judô · 10/2026" })).not.toBeInTheDocument();

    const first = open.getAllByRole("listitem")[0];
    await user.click(within(first).getByText("Composição"));
    expect(within(first).getByText("Plano").nextElementSibling).toHaveTextContent(/100,00/);
    expect(within(first).getByText("Benefício").nextElementSibling).toHaveTextContent(/10,00/);
    expect(within(first).getByText("Valor final").nextElementSibling).toHaveTextContent(/90,00/);

    const history = within(screen.getByRole("region", { name: "Histórico" }));
    expect(history.getByText("Pago em 04/03/2026 · Meio: Pix pela chave")).toBeInTheDocument();
    expect(history.getByRole("link", { name: "Baixar recibo de Jiu-Jitsu · 03/2026" })).toHaveAttribute("href", "/api/v1/portal/receipts/RC-7/pdf");
    expect(history.getAllByRole("link")).toHaveLength(1);
    expect(history.getByText("Cancelada")).toBeInTheDocument();

    const debit = within(screen.getByRole("region", { name: "Débito automático" }));
    expect(debit.getByText("Inativo")).toBeInTheDocument();
    expect(debit.queryByRole("button")).not.toBeInTheDocument();
  });

  it("CA9, CA10: marcar duas da mesma unidade soma o total e envia o arquivo único; a lista é relida", async () => {
    let reads = 0;
    serve(() => HttpResponse.json((reads++, finance())));
    server.use(http.get("/api/v1/portal/charges/pix-instructions", ({ request }) => (new URL(request.url).searchParams.get("ids") === "c1,c2" ? HttpResponse.json(batch) : HttpResponse.json({}, { status: 404 }))));
    const user = userEvent.setup();
    setup();
    const open = within(await screen.findByRole("region", { name: "Em aberto" }));
    await user.click(open.getByRole("checkbox", { name: "Selecionar Jiu-Jitsu · 10/2026" }));
    await user.click(open.getByRole("checkbox", { name: "Selecionar Kimono" }));
    expect(open.getByRole("status")).toHaveTextContent("2 selecionadas · Total R$ 120,00");
    const paySelected = open.getByRole("button", { name: "Pagar selecionadas" });
    expect(paySelected).toBeEnabled();
    await user.click(paySelected);

    const dialog = within(await screen.findByRole("dialog", { name: "Pix na chave com comprovante" }));
    expect(await dialog.findByText("pix@alfa.test")).toBeInTheDocument();
    expect(dialog.getByText(/120,00/)).toBeInTheDocument();
    expect(dialog.getAllByRole("listitem")).toHaveLength(2);
    await user.upload(dialog.getByLabelText(/Comprovante \(JPG/), new File(["x"], "comprovante.png", { type: "image/png" }));
    await user.click(dialog.getByRole("button", { name: "Enviar comprovante" }));
    expect(await dialog.findByText("Comprovante enviado. A cobrança ficou em conferência.")).toBeInTheDocument();
    expect(send.calls).toEqual([[["c1", "c2"], expect.any(File)]]);
    await waitFor(() => expect(reads).toBe(2));
  });

  it("CA9: unidades diferentes mostram o aviso e desabilitam Pagar selecionadas; desmarcar libera", async () => {
    serve(finance());
    const user = userEvent.setup();
    setup();
    const open = within(await screen.findByRole("region", { name: "Em aberto" }));
    await user.click(open.getByRole("checkbox", { name: "Selecionar Jiu-Jitsu · 10/2026" }));
    await user.click(open.getByRole("checkbox", { name: "Selecionar Seminário" }));
    expect(open.getByRole("alert")).toHaveTextContent("Escolha cobranças da mesma unidade para pagar juntas.");
    expect(open.getByRole("button", { name: "Pagar selecionadas" })).toBeDisabled();
    await user.click(open.getByRole("checkbox", { name: "Selecionar Seminário" }));
    expect(open.queryByRole("alert")).not.toBeInTheDocument();
    expect(open.getByRole("button", { name: "Pagar selecionadas" })).toBeEnabled();
  });

  it("CA9: Pagar numa cobrança abre o diálogo com uma só", async () => {
    serve(finance());
    server.use(http.get("/api/v1/portal/charges/c2/pix-instructions", () => HttpResponse.json({ chargeId: "c2", pixKey: "pix@alfa.test", amountCents: 3000, dueOn: "2026-11-20", unit: "Centro" })));
    const user = userEvent.setup();
    setup();
    await user.click(await screen.findByRole("button", { name: "Pagar Kimono" }));
    const dialog = within(await screen.findByRole("dialog", { name: "Pix na chave com comprovante" }));
    expect(await dialog.findByText(/30,00/)).toBeInTheDocument();
    expect(dialog.queryByRole("list")).not.toBeInTheDocument();
  });

  it("CA9: seletor de aluno com Toda a família; a visão lista as cobranças com o aluno, sem histórico, e muda ?visao=", async () => {
    const two = [
      { id: "s1", name: "Ana Souza", photoUrl: null, self: true, openCents: 12000 },
      { id: "s2", name: "Davi Souza", photoUrl: null, self: false, openCents: 5000 },
    ];
    const seen: string[] = [];
    serve(({ request }) => {
      const q = new URL(request.url).searchParams;
      seen.push(q.toString());
      if (q.get("scope") === "FAMILY") return HttpResponse.json(finance({ students: two, scope: "FAMILY", familyOpenCents: 17000, history: [], open: [charge({}), charge({ id: "c9", studentId: "s2", studentName: "Davi Souza", referenceLabel: "Judô · 10/2026" })] }));
      return HttpResponse.json(finance({ students: two, selectedStudentId: q.get("studentId") ?? "s1", familyOpenCents: 17000 }));
    });
    const user = userEvent.setup();
    setup();
    const select = await screen.findByRole("combobox", { name: "Aluno" });
    expect(within(select).getAllByRole("option").map((o) => o.textContent)).toEqual(["Você", "Davi Souza", "Toda a família"]);
    await user.selectOptions(select, "FAMILY");
    expect(await screen.findByText("Total da família em aberto: R$ 170,00")).toBeInTheDocument();
    expect(screen.getByTestId("where")).toHaveTextContent("/portal/financeiro?visao=familia");
    expect(await screen.findByText("Davi Souza", { selector: "p" })).toBeInTheDocument();
    expect(screen.queryByRole("region", { name: "Histórico" })).not.toBeInTheDocument();
    expect(seen).toContain("scope=FAMILY");

    await user.selectOptions(screen.getByRole("combobox", { name: "Aluno" }), "s2");
    await waitFor(() => expect(screen.getByTestId("where")).toHaveTextContent("/portal/financeiro?aluno=s2"));
    expect(await screen.findByRole("region", { name: "Histórico" })).toBeInTheDocument();
    expect(seen.at(-1)).toBe("studentId=s2");
  });

  it("CA9: trocar o ano refaz o histórico e muda ?ano=", async () => {
    serve(({ request }) => {
      const year = Number(new URL(request.url).searchParams.get("year") ?? 2026);
      return HttpResponse.json(finance({ year, history: year === 2025 ? [] : finance().history }));
    });
    const user = userEvent.setup();
    setup();
    await screen.findByText("Pago em 04/03/2026 · Meio: Pix pela chave");
    await user.selectOptions(screen.getByRole("combobox", { name: "Ano" }), "2025");
    expect(screen.getByTestId("where")).toHaveTextContent("/portal/financeiro?ano=2025");
    expect(await screen.findByText("Nenhum histórico neste ano")).toBeInTheDocument();
  });

  it("CA11: sem cobranças e sem histórico mostra os dois vazios", async () => {
    serve(finance({ open: [], history: [], years: [] }));
    setup();
    expect(await screen.findByText("Nenhuma cobrança em aberto")).toBeInTheDocument();
    expect(screen.getByText("Nenhum histórico neste ano")).toBeInTheDocument();
  });

  it("CA11: carregando, erro com Tentar de novo e sem permissão", async () => {
    let calls = 0;
    serve(() => (calls++ === 0 ? HttpResponse.json({ code: "X", message: "m", fields: [], traceId: "t" }, { status: 500 }) : HttpResponse.json(finance())));
    const user = userEvent.setup();
    const first = setup();
    expect(screen.getByRole("status")).toHaveTextContent("Carregando");
    await user.click(await screen.findByRole("button", { name: "Tentar de novo" }));
    expect(await screen.findByRole("region", { name: "Em aberto" })).toBeInTheDocument();
    first.unmount();

    serve(() => HttpResponse.json({ code: "FORBIDDEN", message: "m", fields: [], traceId: "t" }, { status: 403 }));
    setup();
    expect(await screen.findByText("Você não tem acesso")).toBeInTheDocument();
  });

  it("CA7: portal reduzido mostra o Financeiro", async () => {
    serve(finance({ reduced: true }));
    setup();
    expect(await screen.findByRole("heading", { name: "Financeiro", level: 1 })).toBeInTheDocument();
    expect(await screen.findByRole("region", { name: "Em aberto" })).toBeInTheDocument();
  });

  it("CA11: o axe não acusa violação nos dois temas", async () => {
    for (const dark of [false, true]) {
      setDevicePrefersDark(dark);
      serve(finance());
      const { container, unmount } = setup();
      await screen.findByRole("region", { name: "Em aberto" });
      await waitFor(async () => expect(await axeViolations(container)).toEqual([]));
      unmount();
    }
  });
});
