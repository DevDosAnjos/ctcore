import { readFileSync } from "node:fs";
import { join } from "node:path";
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
import type { PlanRow } from "./usePlans";

const session = (role: string): SessionView => ({
  account: { id: "a", email: "x@x.com", name: "X" },
  links: [{ id: "l1", organizationId: "o", organizationName: "Alfa", role, unitIds: [] }],
  activeLinkId: "l1",
  twoFactor: { enabled: false, required: false },
});
const plan = (over: Partial<PlanRow>): PlanRow => ({
  id: "p1",
  unit: { id: "u1", name: "Centro" },
  modality: { id: "m1", name: "Judô" },
  name: "Judô Mensal",
  valueCents: 15000,
  weeklyFrequency: 2,
  validOtherUnits: false,
  studentsUsing: 0,
  status: "ACTIVE",
  ...over,
});
const all = [plan({}), plan({ id: "p2", name: "Boxe Livre", modality: { id: "m2", name: "Boxe" }, weeklyFrequency: null, valueCents: 20050, status: "INACTIVE" })];

let calls: URLSearchParams[];

function setup(role = "COORDINATOR", list?: () => Response | Promise<Response>) {
  calls = [];
  window.history.replaceState(null, "", "/planos/lista");
  server.use(
    http.get("/api/v1/auth/session", () => HttpResponse.json(session(role))),
    http.get("/api/v1/permissions", () => HttpResponse.json({ permissions: mockPermissions(role) })),
    http.get("/api/v1/units/reachable", () => HttpResponse.json({ allUnits: true, units: [{ id: "u1", name: "Centro", shortName: "c" }] })),
    http.get("/api/v1/modalities", () =>
      HttpResponse.json([
        { id: "m1", name: "Judô", active: true, canManage: false, graduationEnabled: true },
        { id: "m3", name: "Karatê", active: false, canManage: false, graduationEnabled: false },
      ]),
    ),
    http.get("/api/v1/plans", ({ request }) => {
      const p = new URL(request.url).searchParams;
      calls.push(p);
      if (list) return list();
      return HttpResponse.json({ items: all, page: 1, size: 25, total: all.length });
    }),
  );
  return render(
    <ThemeProvider>
      <MemoryRouter initialEntries={["/planos/lista"]}>
        <SessionProvider>
          <App />
        </SessionProvider>
      </MemoryRouter>
    </ThemeProvider>,
  );
}

describe("PlansTablePage (RF-PLA-02)", () => {
  it("CA6: colunas com valor em reais, frequência (Livre quando nula), alunos usando e situação; selo Desativado", async () => {
    setup();
    const row = (await screen.findByText("Judô Mensal")).closest("tr")!;
    expect(within(row).getByText("Unidade Centro")).toBeInTheDocument();
    expect(within(row).getByText("Judô")).toBeInTheDocument();
    expect(within(row).getByText("R$ 150,00")).toBeInTheDocument();
    expect(within(row).getByText("2x por semana")).toBeInTheDocument();
    expect(within(row).getByText("0")).toBeInTheDocument();
    expect(within(row).getByText("Ativo")).toBeInTheDocument();
    const other = screen.getByText("Boxe Livre").closest("tr")!;
    expect(within(other).getByText("R$ 200,50")).toBeInTheDocument();
    expect(within(other).getByText("Livre")).toBeInTheDocument();
    expect(within(other).getByText("Desativado")).toBeInTheDocument();
    expect(calls.at(-1)!.get("size")).toBe("25");
  });

  it("CA6: escolher modalidade refaz a consulta só com ela; filtro sem resultado mostra Limpar filtros", async () => {
    const user = userEvent.setup();
    setup("COORDINATOR", () => HttpResponse.json({ items: calls.at(-1)!.get("modalityId") ? [] : all, page: 1, size: 25, total: calls.at(-1)!.get("modalityId") ? 0 : 2 }));
    await screen.findByText("Judô Mensal");
    const modality = screen.getByLabelText("Modalidade");
    await waitFor(() => expect(within(modality).getAllByRole("option").map((o) => o.textContent)).toEqual(["Todas as modalidades", "Judô"]));
    await user.selectOptions(modality, "m1");
    await waitFor(() => expect(calls.at(-1)!.get("modalityId")).toBe("m1"));
    expect(await screen.findByText("Nenhum resultado")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Limpar filtros" }));
    expect(await screen.findByText("Judô Mensal")).toBeInTheDocument();
  });

  it("CA7: vazio, erro com Tentar de novo e sem permissão", async () => {
    const user = userEvent.setup();
    let fail = true;
    const view = setup("COORDINATOR", () => (fail ? HttpResponse.json({ code: "INTERNAL_ERROR" }, { status: 500 }) : HttpResponse.json({ items: [], page: 1, size: 25, total: 0 })));
    const retry = await screen.findByRole("button", { name: "Tentar de novo" });
    fail = false;
    await user.click(retry);
    expect(await screen.findByText("Nada por aqui ainda")).toBeInTheDocument();
    view.unmount();
    setup("COORDINATOR", () => HttpResponse.json({ code: "FORBIDDEN" }, { status: 403 }));
    expect(await screen.findByText("Você não tem acesso")).toBeInTheDocument();
  });

  it("CA7: Professor não chega à tela", async () => {
    setup("TEACHER");
    await waitFor(() => expect(screen.queryByText("Carregando...")).toBeNull());
    expect(screen.queryByText("Tabela de planos")).toBeNull();
  });

  it("CA3: o arquivo de tradução de planos não menciona presença nem aulas feitas", () => {
    const text = JSON.stringify(JSON.parse(readFileSync(join(process.cwd(), "src/i18n/pt-BR.json"), "utf8")).plans);
    expect(text).not.toMatch(/presen|aulas feitas|comparec/i);
  });

  it.each([
    ["claro", false],
    ["escuro", true],
  ])("CA6: a 360 px não tem violações no axe no tema %s", async (_n, dark) => {
    setDevicePrefersDark(dark);
    window.innerWidth = 360;
    const { container } = setup();
    await screen.findByText("Judô Mensal");
    expect(await axeViolations(container)).toEqual([]);
  });
});
