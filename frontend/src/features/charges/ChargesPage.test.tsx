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
import { SITUATIONS, type ChargeRow } from "./types";

const session = (role: string): SessionView => ({
  account: { id: "a", email: "x@x.com", name: "X" },
  links: [{ id: "l1", organizationId: "o", organizationName: "Alfa", role, unitIds: [] }],
  activeLinkId: "l1",
  twoFactor: { enabled: false, required: false },
});
const charge = (over: Partial<ChargeRow> = {}): ChargeRow => ({
  id: "c1",
  student: { id: "s1", name: "Ana Souza", photoUrl: null },
  unit: { id: "u1", name: "Centro" },
  type: "MONTHLY",
  modality: { id: "m1", name: "Jiu-Jitsu" },
  referenceMonth: "2026-03-01",
  description: null,
  amountCents: 12000,
  dueOn: "2026-03-05",
  status: "OVERDUE",
  payment: null,
  ...over,
});
const paid = charge({ id: "c2", student: { id: "s2", name: "João Pereira", photoUrl: "https://files.example/foto.png" }, status: "PAID", payment: { method: "PIX_MP", paidAt: "2026-03-04T15:00:00Z" } });
const fee = charge({ id: "c3", student: { id: "s3", name: "Maria Lima", photoUrl: null }, type: "ENROLLMENT_FEE", modality: null, referenceMonth: null, description: "Taxa de matrícula", status: "PENDING" });
const counts = { overdue: 4, dueToday: 2, inReview: 1, paidMonth: 7 };

let calls: URLSearchParams[];

function setup(role = "COORDINATOR", list?: (p: URLSearchParams) => Response | Promise<Response>, path = "/cobrancas") {
  calls = [];
  window.history.replaceState(null, "", path);
  server.use(
    http.get("/api/v1/auth/session", () => HttpResponse.json(session(role))),
    http.get("/api/v1/permissions", () => HttpResponse.json({ permissions: mockPermissions(role) })),
    http.get("/api/v1/units/reachable", () => HttpResponse.json({ allUnits: true, units: [{ id: "u1", name: "Centro", shortName: "c" }, { id: "u2", name: "Norte", shortName: "n" }] })),
    http.get("/api/v1/age-ranges", () => HttpResponse.json({ canManage: false, items: [] })),
    http.get("/api/v1/modalities", () => HttpResponse.json([{ id: "m1", name: "Jiu-Jitsu", active: true }])),
    http.get("/api/v1/classes", () => HttpResponse.json({ items: [{ id: "k1", name: "Turma Noite" }], page: 1, size: 100, total: 1 })),
    http.get("/api/v1/charges", ({ request }) => {
      const p = new URL(request.url).searchParams;
      calls.push(p);
      if (list) return list(p);
      return HttpResponse.json({ items: [charge(), paid, fee], page: 1, size: 25, total: 3, counts, totals: { count: 3, amountCents: 36000 } });
    }),
  );
  return render(
    <ThemeProvider>
      <MemoryRouter initialEntries={[path]}>
        <SessionProvider>
          <App />
        </SessionProvider>
      </MemoryRouter>
    </ThemeProvider>,
  );
}

const ready = () => screen.findByRole("heading", { name: "Cobranças", level: 1 });
const tableRow = (name: string) => screen.getAllByText(name).map((e) => e.closest("tr")).find(Boolean)!;

describe("ChargesPage (RF-COB-01)", () => {
  it("CA16/R12: colunas com aluno, foto ou iniciais, referência, valor, vencimento, situação em texto e meio só na paga; pede vencimento decrescente", async () => {
    setup();
    await ready();
    await screen.findAllByText("Ana Souza");
    const row = tableRow("Ana Souza");
    expect(within(row).getByText("AS")).toBeInTheDocument();
    expect(within(row).getByText("Centro")).toBeInTheDocument();
    expect(within(row).getByText("Jiu-Jitsu · 03/2026")).toBeInTheDocument();
    expect(within(row).getByText(/120,00/)).toBeInTheDocument();
    expect(within(row).getByText("05/03/2026")).toBeInTheDocument();
    expect(within(row).getByText("Em atraso")).toBeInTheDocument();
    expect(within(row).getByText("—")).toBeInTheDocument();
    const other = tableRow("João Pereira");
    expect(within(other).getByText("Paga")).toBeInTheDocument();
    expect(within(other).getByText("Pix")).toBeInTheDocument();
    expect(document.querySelector("img[src='https://files.example/foto.png']")).not.toBeNull();
    expect(within(tableRow("Maria Lima")).getByText("Taxa de matrícula")).toBeInTheDocument();
    expect(calls.at(-1)!.get("sort")).toBe("dueOn,desc");
    expect(calls.at(-1)!.get("size")).toBe("25");
  });

  it("CA16: cada situação aparece com texto traduzido", async () => {
    const rows = SITUATIONS.map((status, i) => charge({ id: `x${i}`, student: { id: `s${i}`, name: `Aluno ${i}`, photoUrl: null }, status }));
    setup("COORDINATOR", () => HttpResponse.json({ items: rows, page: 1, size: 25, total: 9, counts, totals: { count: 9, amountCents: 108000 } }));
    await screen.findAllByText("Aluno 0");
    const names = ["Pendente", "Vence hoje", "Em atraso", "Em conferência", "Paga", "Paga com atraso", "Cancelada", "Renegociada", "Estornada"];
    const table = screen.getAllByRole("table")[0];
    for (const n of names) expect(within(table).getByText(n)).toBeInTheDocument();
  });

  it("CA15: o endereço vira a consulta, o atalho fica ativo com a contagem, o rodapé mostra os totais e trocar filtro volta à página 1", async () => {
    const user = userEvent.setup();
    setup("COORDINATOR", () => HttpResponse.json({ items: [charge()], page: 2, size: 25, total: 30, counts, totals: { count: 30, amountCents: 360000 } }), "/cobrancas?quick=OVERDUE&pagina=2");
    await screen.findAllByText("Ana Souza");
    expect(calls.at(-1)!.get("quick")).toBe("OVERDUE");
    expect(calls.at(-1)!.get("page")).toBe("2");
    expect(screen.getByRole("button", { name: "Em atraso (4)" })).toHaveAttribute("aria-pressed", "true");
    expect(screen.getByRole("button", { name: "Vence hoje (2)" })).toHaveAttribute("aria-pressed", "false");
    expect(screen.getByRole("button", { name: "Em conferência (1)" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Pagas no mês (7)" })).toBeInTheDocument();
    expect(screen.getByText(/30 cobranças somando/)).toHaveTextContent(/3\.600,00/);
    await user.click(screen.getByRole("button", { name: "Vence hoje (2)" }));
    await waitFor(() => expect(calls.at(-1)!.get("quick")).toBe("DUE_TODAY"));
    expect(calls.at(-1)!.get("page")).toBe("1");
    await user.click(screen.getByRole("button", { name: "Vence hoje (2)" }));
    await waitFor(() => expect(calls.at(-1)!.get("quick")).toBeNull());
  });

  it("R5: período, situação, tipo, unidade, modalidade, turma e busca combinam na consulta", async () => {
    const user = userEvent.setup();
    setup();
    await screen.findAllByText("Ana Souza");
    await waitFor(() => expect(within(screen.getByRole("combobox", { name: "Unidade" })).getAllByRole("option")).toHaveLength(3));
    await waitFor(() => expect(within(screen.getByRole("combobox", { name: "Turma" })).getAllByRole("option")).toHaveLength(2));
    await user.type(screen.getByLabelText("Vencimento de"), "2026-03-01");
    await user.type(screen.getByLabelText("Vencimento até"), "2026-03-31");
    await user.selectOptions(screen.getByRole("combobox", { name: "Situação" }), "PAID");
    await user.selectOptions(screen.getByRole("combobox", { name: "Tipo" }), "MONTHLY");
    await user.selectOptions(screen.getByRole("combobox", { name: "Unidade" }), "u2");
    await user.selectOptions(screen.getByRole("combobox", { name: "Modalidade" }), "m1");
    await user.selectOptions(screen.getByRole("combobox", { name: "Turma" }), "k1");
    await user.type(screen.getByRole("searchbox", { name: "Buscar" }), "ana");
    await waitFor(() => expect(calls.at(-1)!.get("q")).toBe("ana"));
    const p = calls.at(-1)!;
    expect(Object.fromEntries(["from", "to", "status", "type", "unitId", "modalityId", "classId"].map((k) => [k, p.get(k)]))).toEqual({
      from: "2026-03-01", to: "2026-03-31", status: "PAID", type: "MONTHLY", unitId: "u2", modalityId: "m1", classId: "k1",
    });
  });

  it("ordenar por valor pede amount,asc e depois amount,desc", async () => {
    const user = userEvent.setup();
    setup();
    await screen.findAllByText("Ana Souza");
    await user.click(screen.getByRole("button", { name: "Ordenar por Valor" }));
    await waitFor(() => expect(calls.at(-1)!.get("sort")).toBe("amount,asc"));
    await user.click(screen.getByRole("button", { name: "Ordenar por Valor" }));
    await waitFor(() => expect(calls.at(-1)!.get("sort")).toBe("amount,desc"));
  });

  it("CA17: vazio (com limpar filtros quando há filtro), erro com tentar de novo, sem permissão e carregando", async () => {
    const user = userEvent.setup();
    const empty = () => HttpResponse.json({ items: [], page: 1, size: 25, total: 0, counts, totals: { count: 0, amountCents: 0 } });
    setup("COORDINATOR", empty);
    expect(await screen.findByText("Nada por aqui ainda")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Em atraso (4)" }));
    expect(await screen.findByRole("button", { name: "Limpar filtros" })).toBeInTheDocument();
  });

  it("CA17: erro 500 oferece tentar de novo; 403 mostra sem permissão", async () => {
    const user = userEvent.setup();
    let fail = true;
    setup("COORDINATOR", () => (fail ? HttpResponse.json({ code: "X", message: "m", fields: [], traceId: "t" }, { status: 500 }) : HttpResponse.json({ items: [charge()], page: 1, size: 25, total: 1, counts, totals: { count: 1, amountCents: 12000 } })));
    expect(screen.getByText("Carregando...")).toBeInTheDocument();
    const retry = await screen.findByRole("button", { name: "Tentar de novo" });
    fail = false;
    await user.click(retry);
    expect((await screen.findAllByText("Ana Souza")).length).toBeGreaterThan(0);
  });

  it("CA17: 403 do servidor mostra sem permissão", async () => {
    setup("COORDINATOR", () => HttpResponse.json({ code: "FORBIDDEN", message: "m", fields: [], traceId: "t" }, { status: 403 }));
    expect(await screen.findByText("Você não tem acesso")).toBeInTheDocument();
  });

  it("CA17: Professor não vê Cobranças no menu e a rota o leva a Sem acesso", async () => {
    setup("TEACHER");
    expect(await screen.findByText("Esta área não está disponível para o seu perfil")).toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "Cobranças" })).toBeNull();
    expect(screen.queryByRole("link", { name: "Cobranças e pagamentos" })).toBeNull();
  });

  it("CA18: a 360 px a lista também sai em cartões e o axe não acusa violação nos dois temas", async () => {
    for (const dark of [false, true]) {
      setDevicePrefersDark(dark);
      window.innerWidth = 360;
      const { container, unmount } = setup();
      await ready();
      await screen.findAllByText("Ana Souza");
      const cards = screen.getAllByRole("listitem").filter((li) => li.className.includes("rounded-md border p-3"));
      expect(cards).toHaveLength(3);
      expect(within(cards[0]).getByText("Em atraso")).toBeInTheDocument();
      expect(within(cards[1]).getByText("Pix")).toBeInTheDocument();
      expect(await axeViolations(container)).toEqual([]);
      unmount();
    }
  });
});
