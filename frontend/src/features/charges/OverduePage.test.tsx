import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { MemoryRouter } from "react-router";
import { App } from "@/App";
import { WhatsAppProvider } from "@/components/whatsapp/WhatsAppContext";
import { SessionProvider, type SessionView } from "@/features/auth/SessionProvider";
import { mockPermissions } from "@/mocks/permissions";
import { server } from "@/mocks/server";
import { axeViolations } from "@/test/axe";
import { setDevicePrefersDark } from "@/test/device";
import { ThemeProvider } from "@/theme/ThemeProvider";
import type { OverdueCharge } from "./types";

const session = (role: string): SessionView => ({
  account: { id: "a", email: "x@x.com", name: "X" },
  links: [{ id: "l1", organizationId: "o", organizationName: "Alfa", role, unitIds: [] }],
  activeLinkId: "l1",
  twoFactor: { enabled: false, required: false },
});
const line = (over: Partial<OverdueCharge> = {}): OverdueCharge => ({
  id: "c1",
  student: { id: "s1", name: "Ana Souza", photoUrl: null },
  unit: { id: "u1", name: "Centro" },
  type: "MONTHLY",
  referenceMonth: "2026-03-01",
  description: null,
  amountCents: 12000,
  dueOn: "2026-03-05",
  daysLate: 12,
  daysToBlock: 18,
  lastReminder: null,
  payer: { name: "Carla Souza", phone: "21900000011" },
  ...over,
});
const two = [
  line(),
  line({ id: "c2", student: { id: "s2", name: "João Pereira", photoUrl: null }, type: "ONE_OFF", referenceMonth: null, description: "Kimono", amountCents: 9000, daysLate: 5, daysToBlock: null, lastReminder: { at: "2026-03-10T15:00:00Z", channel: "EMAIL" }, payer: { name: "João Pereira", phone: null } }),
];

let calls: URLSearchParams[];
let posts: unknown[];

function setup(list?: (p: URLSearchParams) => Response | Promise<Response>, whatsapp = true, path = "/cobrancas/atrasados") {
  calls = [];
  posts = [];
  window.history.replaceState(null, "", path);
  server.use(
    http.get("/api/v1/auth/session", () => HttpResponse.json(session("COORDINATOR"))),
    http.get("/api/v1/permissions", () => HttpResponse.json({ permissions: mockPermissions("COORDINATOR") })),
    http.get("/api/v1/units/reachable", () => HttpResponse.json({ allUnits: true, units: [{ id: "u1", name: "Centro", shortName: "c" }, { id: "u2", name: "Norte", shortName: "n" }] })),
    http.get("/api/v1/age-ranges", () => HttpResponse.json({ canManage: false, items: [] })),
    http.get("/api/v1/modalities", () => HttpResponse.json([])),
    http.get("/api/v1/classes", () => HttpResponse.json({ items: [], page: 1, size: 100, total: 0 })),
    http.get("/api/v1/charges/overdue", ({ request }) => {
      const p = new URL(request.url).searchParams;
      calls.push(p);
      return list ? list(p) : HttpResponse.json({ items: two, page: 1, size: 25, total: 2 });
    }),
    http.post("/api/v1/charges/reminders", async ({ request }) => {
      const body = (await request.json()) as { chargeIds: string[] };
      posts.push(body);
      const results = ["SENT", "NO_ADDRESS", "EMAIL_PROBLEM", "RECENT", "NOT_OPEN"];
      return HttpResponse.json({ results: body.chargeIds.map((chargeId, i) => ({ chargeId, result: results[i] })) });
    }),
  );
  return render(
    <ThemeProvider>
      <MemoryRouter initialEntries={[path]}>
        <SessionProvider>
          <WhatsAppProvider enabled={whatsapp}>
            <App />
          </WhatsAppProvider>
        </SessionProvider>
      </MemoryRouter>
    </ThemeProvider>,
  );
}

const ready = () => screen.findByRole("heading", { name: "Cobranças em atraso", level: 1 });
const tableRow = (name: string) => screen.getAllByText(name).map((e) => e.closest("tr")).find(Boolean)!;

describe("OverduePage (RF-COB-06)", () => {
  it("CA25: mostra aluno, unidade, referência, vencimento, valor, dias de atraso, último lembrete e dias até o bloqueio; pede dias de atraso decrescente", async () => {
    setup();
    await ready();
    await screen.findAllByText("Ana Souza");
    const a = tableRow("Ana Souza");
    expect(within(a).getByText("Centro")).toBeInTheDocument();
    expect(within(a).getByText("Mensalidade · 03/2026")).toBeInTheDocument();
    expect(within(a).getByText("05/03/2026")).toBeInTheDocument();
    expect(within(a).getByText(/120,00/)).toBeInTheDocument();
    expect(within(a).getByText("12 dias")).toBeInTheDocument();
    expect(within(a).getByText("Nunca")).toBeInTheDocument();
    expect(within(a).getByText("18 dias")).toBeInTheDocument();
    const b = tableRow("João Pereira");
    expect(within(b).getByText("Kimono")).toBeInTheDocument();
    expect(within(b).getByText("10/03/2026 · E-mail")).toBeInTheDocument();
    expect(within(b).getByText("—")).toBeInTheDocument();
    expect(calls.at(-1)!.get("sort")).toBe("daysLate,desc");
  });

  it("CA26: sem seleção o botão fica desabilitado; seleciona 2, envia, mostra o resultado de cada uma e recarrega a lista", async () => {
    const user = userEvent.setup();
    setup();
    await ready();
    await screen.findAllByText("Ana Souza");
    const send = screen.getByRole("button", { name: "Enviar lembrete" });
    expect(send).toBeDisabled();
    const table = screen.getAllByRole("table")[0];
    await user.click(within(table).getByRole("checkbox", { name: "Selecionar Ana Souza" }));
    await user.click(within(table).getByRole("checkbox", { name: "Selecionar João Pereira" }));
    expect(screen.getByText("2 selecionadas")).toBeInTheDocument();
    const before = calls.length;
    await user.click(send);
    const dialog = await screen.findByRole("dialog", { name: "Enviar lembrete" });
    expect(within(dialog).getByText(/das 2 cobranças selecionadas/)).toBeInTheDocument();
    await user.click(within(dialog).getByRole("button", { name: "Enviar" }));
    expect(await within(dialog).findByText("Ana Souza · Mensalidade · 03/2026")).toBeInTheDocument();
    expect(within(dialog).getByText("Enviado")).toBeInTheDocument();
    expect(within(dialog).getByText("Sem e-mail")).toBeInTheDocument();
    expect(posts).toEqual([{ chargeIds: ["c1", "c2"] }]);
    await user.click(within(dialog).getAllByRole("button", { name: "Fechar" })[0]);
    await waitFor(() => expect(calls.length).toBeGreaterThan(before));
    await waitFor(() => expect(screen.getByText("0 selecionada")).toBeInTheDocument());
  });

  it("CA26: selecionar todas da página marca só as linhas da página e desmarca no segundo clique", async () => {
    const user = userEvent.setup();
    setup((p) => HttpResponse.json({ items: p.get("page") === "2" ? [line({ id: "c9", student: { id: "s9", name: "Zeca Lima", photoUrl: null } })] : two, page: Number(p.get("page")), size: 25, total: 30 }));
    await ready();
    await screen.findAllByText("Ana Souza");
    await user.click(screen.getByRole("checkbox", { name: "Selecionar todas da página" }));
    expect(screen.getByText("2 selecionadas")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Próxima página" }));
    await screen.findAllByText("Zeca Lima");
    expect(screen.getByRole("checkbox", { name: "Selecionar todas da página" })).not.toBeChecked();
    expect(screen.getByText("2 selecionadas")).toBeInTheDocument();
    await user.click(screen.getByRole("checkbox", { name: "Selecionar todas da página" }));
    expect(screen.getByText("3 selecionadas")).toBeInTheDocument();
    await user.click(screen.getByRole("checkbox", { name: "Selecionar todas da página" }));
    expect(screen.getByText("2 selecionadas")).toBeInTheDocument();
  });

  it("CA26: mais de 100 selecionadas desabilita o envio e avisa", async () => {
    const user = userEvent.setup();
    const many = Array.from({ length: 100 }, (_, i) => line({ id: `m${i}`, student: { id: `x${i}`, name: `Aluno ${i}`, photoUrl: null } }));
    setup((p) => HttpResponse.json({ items: p.get("page") === "2" ? [line({ id: "c9", student: { id: "s9", name: "Zeca Lima", photoUrl: null } })] : many, page: Number(p.get("page")), size: 100, total: 101 }), true, "/cobrancas/atrasados?tamanho=100");
    await ready();
    await screen.findAllByText("Aluno 0");
    await user.click(screen.getByRole("checkbox", { name: "Selecionar todas da página" }));
    expect(screen.getByRole("button", { name: "Enviar lembrete" })).toBeEnabled();
    await user.click(screen.getByRole("button", { name: "Próxima página" }));
    await screen.findAllByText("Zeca Lima");
    await user.click(screen.getAllByRole("checkbox", { name: "Selecionar Zeca Lima" })[0]);
    expect(screen.getByRole("alert")).toHaveTextContent("no máximo 100");
    expect(screen.getByRole("button", { name: "Enviar lembrete" })).toBeDisabled();
  });

  it("CA27: o WhatsApp abre wa.me com a mensagem pronta; sem telefone ou com o WhatsApp desligado o botão não aparece", async () => {
    const { unmount } = setup();
    await ready();
    await screen.findAllByText("Ana Souza");
    const links = within(tableRow("Ana Souza")).getAllByRole("link", { name: "Abrir conversa no WhatsApp" });
    const url = new URL(links[0].getAttribute("href")!);
    expect(url.origin + url.pathname).toBe("https://wa.me/5521900000011");
    const text = url.searchParams.get("text")!;
    for (const part of ["Carla Souza", "Centro", "Ana Souza", "120,00", "05/03/2026"]) expect(text).toContain(part);
    expect(within(tableRow("João Pereira")).queryByRole("link", { name: "Abrir conversa no WhatsApp" })).toBeNull();
    unmount();
    setup(undefined, false);
    await screen.findAllByText("Ana Souza");
    expect(screen.queryByRole("link", { name: "Abrir conversa no WhatsApp" })).toBeNull();
  });

  it("CA29: erro com tentar de novo e depois lista vazia", async () => {
    const user = userEvent.setup();
    let mode: "error" | "ok" = "error";
    setup(() => (mode === "error" ? HttpResponse.json({ code: "X", message: "m", fields: [], traceId: "t" }, { status: 500 }) : HttpResponse.json({ items: [], page: 1, size: 25, total: 0 })));
    expect(screen.getByText("Carregando...")).toBeInTheDocument();
    const retry = await screen.findByRole("button", { name: "Tentar de novo" });
    mode = "ok";
    await user.click(retry);
    expect(await screen.findByText("Nada por aqui ainda")).toBeInTheDocument();
  });

  it("CA29: 403 do servidor mostra sem permissão", async () => {
    setup(() => HttpResponse.json({ code: "FORBIDDEN", message: "m", fields: [], traceId: "t" }, { status: 403 }));
    expect(await screen.findByText("Você não tem acesso")).toBeInTheDocument();
  });

  it("CA29: o filtro de unidade vira unitId na consulta", async () => {
    const user = userEvent.setup();
    setup();
    await ready();
    await screen.findAllByText("Ana Souza");
    await user.selectOptions(screen.getByLabelText("Unidade"), "u2");
    await waitFor(() => expect(calls.at(-1)!.get("unitId")).toBe("u2"));
  });

  it("CA29: a 360 px as linhas viram cartões e o axe não acusa violação nos dois temas", async () => {
    for (const dark of [false, true]) {
      setDevicePrefersDark(dark);
      window.innerWidth = 360;
      const { container, unmount } = setup();
      await ready();
      await screen.findAllByText("Ana Souza");
      const cards = screen.getAllByRole("listitem").filter((li) => li.className.includes("rounded-md border p-3"));
      expect(cards).toHaveLength(2);
      expect(within(cards[0]).getByText("12 dias")).toBeInTheDocument();
      expect(await axeViolations(container)).toEqual([]);
      unmount();
    }
  });
});
