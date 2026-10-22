import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { delay, http, HttpResponse } from "msw";
import { MemoryRouter } from "react-router";
import { App } from "@/App";
import { SessionProvider, type SessionView } from "@/features/auth/SessionProvider";
import { mockPermissions } from "@/mocks/permissions";
import { server } from "@/mocks/server";
import { axeViolations } from "@/test/axe";
import { setDevicePrefersDark } from "@/test/device";
import { ThemeProvider } from "@/theme/ThemeProvider";
import { RenegotiateDialog, previewInstallments } from "./RenegotiateDialog";
import type { ChargeDetail, ChargeRow } from "./types";

const session = (role: string): SessionView => ({
  account: { id: "a", email: "x@x.com", name: "X" },
  links: [{ id: "l1", organizationId: "o", organizationName: "Alfa", role, unitIds: [] }],
  activeLinkId: "l1",
  twoFactor: { enabled: false, required: false },
});
const row = (over: Partial<ChargeRow> = {}): ChargeRow => ({
  id: "c1", student: { id: "s1", name: "Ana Souza", photoUrl: null }, unit: { id: "u1", name: "Centro" }, type: "MONTHLY", modality: { id: "m1", name: "Jiu-Jitsu" },
  referenceMonth: "2026-03-01", description: null, amountCents: 12000, dueOn: "2026-03-05", status: "OVERDUE", payment: null, ...over,
});
const detail = (over: Partial<ChargeDetail> = {}): ChargeDetail => ({
  charge: row(), toleranceDays: 5,
  composition: { plan: { id: "p1", name: "Plano Mensal" }, planCents: 15000, benefitType: "PERCENT", benefitCents: 3000, amountCents: 12000 },
  payer: { id: "s1", name: "Ana Souza", kind: "STUDENT" }, origin: null, replacedBy: [], reason: null, canceledAt: null, canceledBy: null, payments: [], reminders: [],
  actions: { cancel: true, renegotiate: true, registerPayment: false, sendReminder: false, refund: false }, ...over,
});
const paidDetail = detail({
  charge: row({ status: "PAID", payment: { method: "CASH", paidAt: "2026-03-04T15:00:00Z" } }),
  payments: [{ id: "p1", amountCents: 12000, method: "CASH", origin: "MANAGEMENT", paidAt: "2026-03-04T15:00:00Z", registeredBy: { id: "a", name: "Bia Admin" }, observation: "No balcão", status: "CONFIRMED", receipt: { number: 7, code: "ABC123" } }],
  actions: { cancel: false, renegotiate: false, registerPayment: false, sendReminder: false, refund: false },
});

let posts: { url: string; body: unknown }[];
let listCalls: number;

function setup(opts: { role?: string; detail?: () => Response | Promise<Response>; post?: (url: string) => Response | Promise<Response> } = {}) {
  posts = [];
  listCalls = 0;
  const role = opts.role ?? "COORDINATOR";
  window.history.replaceState(null, "", "/cobrancas");
  const mutate = async ({ request }: { request: Request }) => {
    const url = new URL(request.url).pathname;
    posts.push({ url, body: await request.json() });
    return opts.post ? opts.post(url) : HttpResponse.json(url.endsWith("renegotiate") ? [row({ id: "n1", type: "INSTALLMENT", status: "PENDING" })] : detail());
  };
  server.use(
    http.get("/api/v1/auth/session", () => HttpResponse.json(session(role))),
    http.get("/api/v1/permissions", () => HttpResponse.json({ permissions: mockPermissions(role) })),
    http.get("/api/v1/units/reachable", () => HttpResponse.json({ allUnits: true, units: [] })),
    http.get("/api/v1/age-ranges", () => HttpResponse.json({ canManage: false, items: [] })),
    http.get("/api/v1/modalities", () => HttpResponse.json([])),
    http.get("/api/v1/classes", () => HttpResponse.json({ items: [], page: 1, size: 100, total: 0 })),
    http.get("/api/v1/charges", () => {
      listCalls++;
      return HttpResponse.json({ items: [row()], page: 1, size: 25, total: 1, counts: { overdue: 1, dueToday: 0, inReview: 0, paidMonth: 0 }, totals: { count: 1, amountCents: 12000 } });
    }),
    http.get("/api/v1/charges/c1", () => (opts.detail ? opts.detail() : HttpResponse.json(detail()))),
    http.post("/api/v1/charges/c1/cancel", mutate),
    http.post("/api/v1/charges/renegotiate", mutate),
  );
  return render(
    <ThemeProvider>
      <MemoryRouter initialEntries={["/cobrancas"]}>
        <SessionProvider>
          <App />
        </SessionProvider>
      </MemoryRouter>
    </ThemeProvider>,
  );
}

async function openPanel(user = userEvent.setup()) {
  await user.click((await screen.findAllByRole("button", { name: /Abrir cobrança de Ana Souza/ }))[0]);
  return screen.findByRole("dialog", { name: "Cobrança" });
}

describe("ChargePanel (RF-COB-02)", () => {
  it("CA15: abre pela lista, vai para o endereço e mostra os blocos, Cancelar e Renegociar e as três ações desabilitadas com o motivo", async () => {
    setup();
    const panel = await openPanel();
    expect(window.location.search).toBe("?cobranca=c1");
    expect(await within(panel).findByText("Plano Mensal · R$ 150,00")).toBeInTheDocument();
    expect(within(panel).getByText("− R$ 30,00")).toBeInTheDocument();
    expect(within(panel).getByText("5 dias")).toBeInTheDocument();
    expect(within(panel).getByText("Nenhum pagamento registrado")).toBeInTheDocument();
    expect(within(panel).getByText("Nenhum lembrete enviado")).toBeInTheDocument();
    expect(within(panel).getByRole("button", { name: "Cancelar cobrança" })).toBeEnabled();
    expect(within(panel).getByRole("button", { name: "Renegociar" })).toBeEnabled();
    for (const [name, why] of [["Registrar pagamento", /cobranças em aberto/], ["Enviar lembrete", /envio de lembretes/], ["Estornar", /estorno/]] as const) {
      const button = within(panel).getByRole("button", { name });
      expect(button).toBeDisabled();
      expect(button).toHaveAccessibleDescription(why);
    }
    expect(within(panel).getByRole("link", { name: "Ver aluno" })).toHaveAttribute("href", "/alunos/s1");
  });

  it("CA1/CA15: cobrança paga mostra pagamento com recibo e não oferece Cancelar nem Renegociar", async () => {
    setup({ detail: () => HttpResponse.json(paidDetail) });
    const panel = await openPanel();
    expect(await within(panel).findByText(/R\$ 120,00 · Dinheiro/)).toBeInTheDocument();
    expect(within(panel).getByText("Lançado por Bia Admin")).toBeInTheDocument();
    expect(within(panel).getByText("No balcão")).toBeInTheDocument();
    expect(within(panel).getByText("Recibo número 7 · código ABC123")).toBeInTheDocument();
    expect(within(panel).queryByRole("button", { name: "Cancelar cobrança" })).toBeNull();
    expect(within(panel).queryByRole("button", { name: "Renegociar" })).toBeNull();
  });

  it("CA2: mostra a origem da parcela e as cobranças que substituíram a original, com o motivo e quem cancelou", async () => {
    const ref = { id: "n1", type: "INSTALLMENT" as const, referenceMonth: "2026-03-01", dueOn: "2026-04-05", amountCents: 6000, status: "OPEN" as const };
    setup({
      detail: () =>
        HttpResponse.json(detail({ charge: row({ status: "RENEGOTIATED" }), replacedBy: [ref], origin: { ...ref, id: "o0", type: "MONTHLY" }, reason: "Acordo", canceledAt: "2026-03-10T15:00:00Z", canceledBy: { id: "a", name: "Bia" }, actions: { cancel: false, renegotiate: false, registerPayment: false, sendReminder: false, refund: false } })),
    });
    const panel = await openPanel();
    expect(await within(panel).findByText("Substituída por:")).toBeInTheDocument();
    expect(within(panel).getByText("Substitui a cobrança:")).toBeInTheDocument();
    expect(within(panel).getByText("Parcela · R$ 60,00 · vence em 05/04/2026 · Em aberto")).toBeInTheDocument();
    expect(within(panel).getByText("Motivo: Acordo")).toBeInTheDocument();
    expect(within(panel).getByText(/Cancelada em 10\/03\/2026 12h00 por Bia/)).toBeInTheDocument();
  });

  it("CA20: erro 500 pede nova tentativa, 403 mostra sem permissão e 404 mostra não encontrada", async () => {
    const user = userEvent.setup();
    let fail = true;
    setup({ detail: async () => (await delay(100), fail ? HttpResponse.json({ code: "X" }, { status: 500 }) : HttpResponse.json(detail())) });
    const panel = await openPanel(user);
    expect(within(panel).getByText("Carregando...")).toBeInTheDocument();
    const retry = await within(panel).findByRole("button", { name: "Tentar de novo" });
    fail = false;
    await user.click(retry);
    expect(await within(panel).findByText("Plano Mensal · R$ 150,00")).toBeInTheDocument();
  });

  it("CA20: 403 e 404 do servidor", async () => {
    setup({ detail: () => HttpResponse.json({ code: "FORBIDDEN" }, { status: 403 }) });
    expect(await within(await openPanel()).findByText("Você não tem acesso")).toBeInTheDocument();
  });

  it("CA20: 404 mostra cobrança não encontrada", async () => {
    setup({ detail: () => HttpResponse.json({ code: "NOT_FOUND" }, { status: 404 }) });
    expect(await within(await openPanel()).findByText("Cobrança não encontrada.")).toBeInTheDocument();
  });

  it("fechar o painel limpa o endereço", async () => {
    const user = userEvent.setup();
    setup();
    const panel = await openPanel(user);
    await user.click(within(panel).getByRole("button", { name: "Fechar" }));
    await waitFor(() => expect(window.location.search).toBe(""));
  });

  it("CA16: Cancelar só confirma com motivo, envia o motivo sem espaços, fecha e recarrega a lista e o painel", async () => {
    const user = userEvent.setup();
    setup();
    const panel = await openPanel(user);
    await user.click(await within(panel).findByRole("button", { name: "Cancelar cobrança" }));
    const dialog = await screen.findByRole("dialog", { name: "Cancelar cobrança" });
    const confirm = within(dialog).getByRole("button", { name: "Cancelar cobrança" });
    expect(confirm).toBeDisabled();
    await user.type(within(dialog).getByRole("textbox", { name: /Motivo/ }), "  Mudou de cidade ");
    expect(within(dialog).getByText("18/200")).toBeInTheDocument();
    const before = listCalls;
    await user.click(confirm);
    await waitFor(() => expect(screen.queryByRole("dialog", { name: "Cancelar cobrança" })).toBeNull());
    expect(posts).toEqual([{ url: "/api/v1/charges/c1/cancel", body: { reason: "Mudou de cidade" } }]);
    await waitFor(() => expect(listCalls).toBeGreaterThan(before));
  });

  it("CA16: o 400 aparece junto do campo e o 409 mostra a mensagem de cobrança já alterada; a janela segue aberta", async () => {
    const user = userEvent.setup();
    let status = 400;
    setup({ post: () => HttpResponse.json(status === 400 ? { code: "VALIDATION_ERROR", fields: [{ field: "reason", message: "até 200 caracteres" }] } : { code: "CONFLICT", fields: [] }, { status }) });
    const panel = await openPanel(user);
    await user.click(await within(panel).findByRole("button", { name: "Cancelar cobrança" }));
    const dialog = await screen.findByRole("dialog", { name: "Cancelar cobrança" });
    await user.type(within(dialog).getByRole("textbox", { name: /Motivo/ }), "x");
    await user.click(within(dialog).getByRole("button", { name: "Cancelar cobrança" }));
    expect(await within(dialog).findByText("até 200 caracteres")).toBeInTheDocument();
    status = 409;
    await user.click(within(dialog).getByRole("button", { name: "Cancelar cobrança" }));
    expect(await within(dialog).findByRole("alert")).toHaveTextContent("já foi alterada por outra pessoa");
    status = 500;
    await user.click(within(dialog).getByRole("button", { name: "Cancelar cobrança" }));
    await waitFor(() => expect(within(dialog).getByRole("alert")).toHaveTextContent("Nada foi alterado"));
    status = 404;
  });

  it("CA17: Renegociar mostra a prévia das parcelas e da soma e envia o pedido; só o total mudado vai como amountCents", async () => {
    const user = userEvent.setup();
    setup();
    const panel = await openPanel(user);
    await user.click(await within(panel).findByRole("button", { name: "Renegociar" }));
    const dialog = await screen.findByRole("dialog", { name: "Renegociar" });
    const submit = within(dialog).getByRole("button", { name: "Renegociar" });
    expect(submit).toBeDisabled();
    expect(within(dialog).getByLabelText("Total a pagar")).toHaveValue("R$ 120,00");
    await user.type(within(dialog).getByLabelText("Primeiro vencimento"), "2099-01-31");
    await user.selectOptions(within(dialog).getByLabelText("Parcelas (1 a 6)"), "3");
    await user.type(within(dialog).getByRole("textbox", { name: /Motivo/ }), "Acordo");
    expect(within(dialog).getByText("Parcela 1: R$ 40,00 em 31/01/2099")).toBeInTheDocument();
    expect(within(dialog).getByText("Parcela 2: R$ 40,00 em 28/02/2099")).toBeInTheDocument();
    expect(within(dialog).getByText("Parcela 3: R$ 40,00 em 31/03/2099")).toBeInTheDocument();
    expect(within(dialog).getByText("Total: R$ 120,00")).toBeInTheDocument();
    await user.click(submit);
    await waitFor(() => expect(posts).toHaveLength(1));
    expect(posts[0]).toEqual({ url: "/api/v1/charges/renegotiate", body: { chargeIds: ["c1"], firstDueOn: "2099-01-31", installments: 3, reason: "Acordo" } });
  });

  it("CA17: total editado vai como amountCents; o 400 de campo aparece no campo", async () => {
    const user = userEvent.setup();
    setup({ post: () => HttpResponse.json({ code: "VALIDATION_ERROR", fields: [{ field: "firstDueOn", message: "não pode ser anterior a hoje" }] }, { status: 400 }) });
    const panel = await openPanel(user);
    await user.click(await within(panel).findByRole("button", { name: "Renegociar" }));
    const dialog = await screen.findByRole("dialog", { name: "Renegociar" });
    const total = within(dialog).getByLabelText("Total a pagar");
    await user.clear(total);
    await user.type(total, "8000");
    await user.type(within(dialog).getByLabelText("Primeiro vencimento"), "2099-01-10");
    await user.type(within(dialog).getByRole("textbox", { name: /Motivo/ }), "Acordo");
    await user.click(within(dialog).getByRole("button", { name: "Renegociar" }));
    expect(await within(dialog).findByText("não pode ser anterior a hoje")).toBeInTheDocument();
    expect(posts[0].body).toMatchObject({ amountCents: 8000, installments: 1 });
  });

  it("CA17: com várias cobranças o total fica bloqueado com a soma e não vai como amountCents", async () => {
    const user = userEvent.setup();
    const done = vi.fn();
    const sent: unknown[] = [];
    server.use(http.post("/api/v1/charges/renegotiate", async ({ request }) => (sent.push(await request.json()), HttpResponse.json([], { status: 201 }))));
    render(
      <ThemeProvider>
        <RenegotiateDialog charges={[row(), row({ id: "c2", amountCents: 5000 })]} onClose={vi.fn()} onDone={done} />
      </ThemeProvider>,
    );
    const total = screen.getByLabelText("Total a pagar");
    expect(total).toBeDisabled();
    expect(total).toHaveValue("R$ 170,00");
    expect(screen.getByText(/mais de uma cobrança/)).toBeInTheDocument();
    await user.type(screen.getByLabelText("Primeiro vencimento"), "2099-05-31");
    await user.type(screen.getByRole("textbox", { name: /Motivo/ }), "Acordo");
    await user.click(screen.getByRole("button", { name: "Renegociar" }));
    await waitFor(() => expect(done).toHaveBeenCalled());
    expect(sent).toEqual([{ chargeIds: ["c1", "c2"], firstDueOn: "2099-05-31", installments: 1, reason: "Acordo" }]);
  });

  it("a prévia leva o resto dos centavos para a primeira parcela e limita o dia ao fim do mês", () => {
    expect(previewInstallments(10000, 3, "2099-01-31")).toEqual([
      { amountCents: 3334, dueOn: "2099-01-31" },
      { amountCents: 3333, dueOn: "2099-02-28" },
      { amountCents: 3333, dueOn: "2099-03-31" },
    ]);
  });

  it("CA20: a 360 px o painel e as janelas passam no axe nos dois temas", async () => {
    const user = userEvent.setup();
    for (const dark of [false, true]) {
      setDevicePrefersDark(dark);
      window.innerWidth = 360;
      const { unmount } = setup();
      const panel = await openPanel(user);
      await within(panel).findByText("Plano Mensal · R$ 150,00");
      expect(await axeViolations(panel)).toEqual([]);
      await user.click(within(panel).getByRole("button", { name: "Renegociar" }));
      expect(await axeViolations(await screen.findByRole("dialog", { name: "Renegociar" }))).toEqual([]);
      await user.click(screen.getByRole("button", { name: "Cancelar" }));
      await user.click(within(panel).getByRole("button", { name: "Cancelar cobrança" }));
      expect(await axeViolations(await screen.findByRole("dialog", { name: "Cancelar cobrança" }))).toEqual([]);
      unmount();
    }
  });

  it("CA12/CA13: cobrança aberta habilita Registrar pagamento; confirma com meio, observação e e-mail; mostra o recibo e recarrega ao fechar", async () => {
    let paid = false;
    setup({ detail: () => HttpResponse.json(paid ? paidDetail : detail({ actions: { ...detail().actions, registerPayment: true } })) });
    server.use(
      http.post("/api/v1/charges/c1/payments", async ({ request }) => {
        posts.push({ url: "payments", body: await request.json() });
        paid = true;
        return HttpResponse.json({ id: "p1", chargeId: "c1", receipt: { number: 7, code: "ABCDEF0123456789" }, receiptEmail: "NO_ADDRESS" }, { status: 201 });
      }),
    );
    const user = userEvent.setup();
    const panel = await openPanel(user);
    const button = await within(panel).findByRole("button", { name: "Registrar pagamento" });
    expect(button).toBeEnabled();
    await user.click(button);
    const dialog = within(await screen.findByRole("dialog", { name: "Registrar pagamento" }));
    expect(dialog.getByLabelText("Valor")).toHaveValue("R$ 120,00");
    expect(dialog.getByLabelText("Enviar recibo por e-mail")).toBeChecked();
    await user.selectOptions(dialog.getByLabelText("Meio de pagamento"), "Pix pela chave");
    await user.type(dialog.getByLabelText("Observação (opcional)"), "No balcão");
    expect(dialog.getByText("9 de 200 caracteres")).toBeInTheDocument();
    await user.click(dialog.getByRole("button", { name: "Confirmar pagamento" }));
    expect(await dialog.findByText("Recibo número 7")).toBeInTheDocument();
    expect(dialog.getByText(/não tem e-mail cadastrado/)).toBeInTheDocument();
    expect(dialog.getByRole("link", { name: "Baixar recibo" })).toHaveAttribute("href", "/api/v1/public/receipts/ABCDEF0123456789/pdf");
    expect(posts[0].body).toEqual({ method: "PIX_KEY", amountCents: 12000, sendReceipt: true, observation: "No balcão" });
    await user.click(dialog.getAllByRole("button", { name: "Fechar" })[0]);
    expect(await within(panel).findByRole("link", { name: "Baixar recibo" })).toHaveAttribute("href", "/api/v1/public/receipts/ABC123/pdf");
    expect(within(panel).getByRole("button", { name: "Registrar pagamento" })).toBeDisabled();
  });

  it("R1/CA2: data futura não é enviada; 400, 409 e 422 mostram a mensagem e a janela segue aberta", async () => {
    setup({ detail: () => HttpResponse.json(detail({ actions: { ...detail().actions, registerPayment: true } })) });
    const user = userEvent.setup();
    const panel = await openPanel(user);
    await user.click(await within(panel).findByRole("button", { name: "Registrar pagamento" }));
    const dialog = within(await screen.findByRole("dialog", { name: "Registrar pagamento" }));
    fireEvent.change(dialog.getByLabelText("Data do pagamento"), { target: { value: "2999-01-01" } });
    await user.click(dialog.getByRole("button", { name: "Confirmar pagamento" }));
    expect(await dialog.findByRole("alert")).toHaveTextContent(/não pode ser futura/);
    fireEvent.change(dialog.getByLabelText("Data do pagamento"), { target: { value: "2026-01-02" } });
    for (const [status, text] of [[400, /Confira a data/], [409, /não pode receber pagamento/], [422, /valor da cobrança mudou/]] as const) {
      server.use(http.post("/api/v1/charges/c1/payments", () => HttpResponse.json({ code: "X", message: "x", changed: false, fields: [], traceId: "t" }, { status })));
      await user.click(dialog.getByRole("button", { name: "Confirmar pagamento" }));
      expect(await dialog.findByText(text)).toBeInTheDocument();
    }
  });

  it("CA16: a janela de pagamento passa no axe nos dois temas", async () => {
    for (const dark of [false, true]) {
      setDevicePrefersDark(dark);
      const { unmount } = setup({ detail: () => HttpResponse.json(detail({ actions: { ...detail().actions, registerPayment: true } })) });
      const user = userEvent.setup();
      const panel = await openPanel(user);
      await user.click(await within(panel).findByRole("button", { name: "Registrar pagamento" }));
      expect(await axeViolations(await screen.findByRole("dialog", { name: "Registrar pagamento" }))).toEqual([]);
      unmount();
    }
  });
});
