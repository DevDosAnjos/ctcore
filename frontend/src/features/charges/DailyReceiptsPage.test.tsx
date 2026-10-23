import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
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
import type { DailyReceipts } from "./types";

const session: SessionView = {
  account: { id: "a", email: "x@x.com", name: "X" },
  links: [{ id: "l1", organizationId: "o", organizationName: "Alfa", role: "COORDINATOR", unitIds: [] }],
  activeLinkId: "l1",
  twoFactor: { enabled: false, required: false },
};
const ana = { id: "p1", name: "Ana Admin" };
const data = (over: Partial<DailyReceipts> = {}): DailyReceipts => ({
  date: "2026-10-20",
  totalCents: 35000,
  count: 3,
  byMethod: [
    { method: "CASH", count: 2, totalCents: 15000 },
    { method: "PIX_KEY", count: 1, totalCents: 20000 },
  ],
  byRegistrar: [
    { registeredBy: ana, count: 2, totalCents: 15000 },
    { registeredBy: null, count: 1, totalCents: 20000 },
  ],
  payments: [
    { id: "a", paidAt: "2026-10-20T17:30:00Z", student: { id: "s3", name: "Carla" }, reference: "Kimono", method: "PIX_KEY", registeredBy: null, amountCents: 20000, receiptNumber: 3 },
    { id: "b", paidAt: "2026-10-20T16:00:00Z", student: { id: "s2", name: "Bruno" }, reference: "Jiu-Jitsu · nov/2026", method: "CASH", registeredBy: ana, amountCents: 5000, receiptNumber: 2 },
    { id: "c", paidAt: "2026-10-20T15:00:00Z", student: { id: "s1", name: "Aline" }, reference: "Kimono", method: "CASH", registeredBy: ana, amountCents: 10000, receiptNumber: 1 },
  ],
  ...over,
});

let calls: URLSearchParams[];

function setup(opts: { receipts?: () => Response | Promise<Response>; allUnits?: boolean } = {}) {
  calls = [];
  window.history.replaceState(null, "", "/cobrancas/recebimentos");
  server.use(
    http.get("/api/v1/auth/session", () => HttpResponse.json(session)),
    http.get("/api/v1/permissions", () => HttpResponse.json({ permissions: { ...mockPermissions("COORDINATOR"), COLLECTIONS: "FULL" } })),
    http.get("/api/v1/units/reachable", () =>
      HttpResponse.json({ allUnits: opts.allUnits ?? false, units: [{ id: "u1", name: "Centro", shortName: "c" }, ...(opts.allUnits ? [{ id: "u2", name: "Norte", shortName: "n" }] : [])] }),
    ),
    http.get("/api/v1/billing/daily-receipts", ({ request }) => {
      calls.push(new URL(request.url).searchParams);
      return opts.receipts ? opts.receipts() : HttpResponse.json(data());
    }),
    http.get("/api/v1/charges", () => HttpResponse.json({ items: [], page: 1, size: 20, total: 0 })),
  );
  return render(
    <ThemeProvider>
      <MemoryRouter initialEntries={["/cobrancas/recebimentos"]}>
        <SessionProvider>
          <App />
        </SessionProvider>
      </MemoryRouter>
    </ThemeProvider>,
  );
}

const ready = () => screen.findByRole("heading", { name: "Recebimentos do dia", level: 1 });

describe("DailyReceiptsPage (RF-COB-08)", () => {
  it("CA9: total, quantidade, tabela por meio, por lançador (com Pagamento online) e a lista do mais recente ao mais antigo", async () => {
    setup();
    await ready();
    await screen.findByText("Total recebido");
    expect(screen.getByText("Total recebido").nextElementSibling).toHaveTextContent(/350,00/);
    expect(screen.getByText("Pagamentos").nextElementSibling).toHaveTextContent("3");
    const [byMethod, byRegistrar, list] = screen.getAllByRole("table");
    expect(within(byMethod).getByText("Dinheiro").closest("tr")).toHaveTextContent(/2.*150,00/);
    expect(within(byMethod).getByText("Pix pela chave").closest("tr")).toHaveTextContent(/1.*200,00/);
    expect(within(byRegistrar).getByText("Ana Admin").closest("tr")).toHaveTextContent(/2.*150,00/);
    expect(within(byRegistrar).getByText("Pagamento online").closest("tr")).toHaveTextContent(/1.*200,00/);
    const rows = within(list).getAllByRole("row").slice(1);
    expect(rows.map((r) => within(r).getAllByRole("cell")[1].textContent)).toEqual(["Carla", "Bruno", "Aline"]);
    expect(rows[0]).toHaveTextContent("14h30");
    expect(rows[1]).toHaveTextContent("Jiu-Jitsu · nov/2026");
    expect(rows[1]).toHaveTextContent("Ana Admin");
    expect(calls.at(-1)!.get("unitId")).toBe("u1");
    expect(calls.at(-1)!.get("date")).toMatch(/^\d{4}-\d{2}-\d{2}$/);
    expect(screen.getByRole("link", { name: "Voltar às cobranças" })).toHaveAttribute("href", "/cobrancas");
  });

  it("CA9: trocar a data recarrega; com todas as unidades pede a unidade antes", async () => {
    const user = userEvent.setup();
    setup({ allUnits: true });
    await ready();
    expect(await screen.findByText("Escolha uma unidade", { selector: "p, h2, h3" })).toBeInTheDocument();
    expect(calls).toHaveLength(0);
    await user.selectOptions(screen.getByRole("combobox", { name: "Unidade" }), "u2");
    await screen.findByText("Total recebido");
    expect(calls.at(-1)!.get("unitId")).toBe("u2");
    fireEvent.change(screen.getByLabelText("Data"), { target: { value: "2026-01-10" } });
    await waitFor(() => expect(calls.at(-1)!.get("date")).toBe("2026-01-10"));
  });

  it("CA10: carregando, erro com tentar de novo e dia sem pagamento", async () => {
    const user = userEvent.setup();
    let mode: "error" | "empty" = "error";
    setup({ receipts: () => (mode === "error" ? HttpResponse.json({}, { status: 500 }) : HttpResponse.json(data({ count: 0, totalCents: 0, byMethod: [], byRegistrar: [], payments: [] }))) });
    await ready();
    expect(screen.getByRole("status")).toBeInTheDocument();
    const retry = await screen.findByRole("button", { name: "Tentar de novo" });
    mode = "empty";
    await user.click(retry);
    expect(await screen.findByText("Nenhum recebimento neste dia")).toBeInTheDocument();
  });

  it("CA10: 403 da rota vira sem permissão", async () => {
    setup({ receipts: () => HttpResponse.json({}, { status: 403 }) });
    await ready();
    expect(await screen.findByText("Você não tem acesso")).toBeInTheDocument();
  });

  it("CA11: a 360 px com dados o axe não acusa violação nos dois temas", async () => {
    for (const dark of [false, true]) {
      setDevicePrefersDark(dark);
      window.innerWidth = 360;
      const { container, unmount } = setup();
      await ready();
      await screen.findByText("Total recebido");
      expect(await axeViolations(container)).toEqual([]);
      unmount();
    }
  });
});
