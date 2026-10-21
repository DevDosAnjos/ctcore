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
import type { BenefitRow } from "./useBenefits";

const session = (role: string): SessionView => ({
  account: { id: "a", email: "x@x.com", name: "X" },
  links: [{ id: "l1", organizationId: "o", organizationName: "Alfa", role, unitIds: [] }],
  activeLinkId: "l1",
  twoFactor: { enabled: false, required: false },
});
const benefit = (over: Partial<BenefitRow>): BenefitRow => ({
  id: "b1",
  student: { id: "s1", name: "Ana Souza" },
  unit: { id: "u1", name: "Centro" },
  modality: { id: "m1", name: "Judô" },
  type: "PERCENT",
  value: 30,
  reason: "Aluno de projeto social",
  decidedBy: "Mestre Fictício",
  grantedBy: "Gestora",
  validUntil: null,
  status: "ACTIVE",
  high: false,
  monthlyValueCents: 10500,
  ...over,
});
const all = [
  benefit({}),
  benefit({ id: "b2", student: { id: "s2", name: "Bruno Lima" }, type: "AMOUNT", value: 12050, validUntil: "2026-01-31", status: "EXPIRED", high: true }),
  benefit({ id: "b3", student: { id: "s3", name: "Carla Dias" }, status: "ENDED" }),
];

let calls: URLSearchParams[];

function setup(role = "COORDINATOR", list?: () => Response | Promise<Response>) {
  calls = [];
  window.history.replaceState(null, "", "/beneficios");
  server.use(
    http.get("/api/v1/auth/session", () => HttpResponse.json(session(role))),
    http.get("/api/v1/permissions", () => HttpResponse.json({ permissions: mockPermissions(role) })),
    http.get("/api/v1/units/reachable", () => HttpResponse.json({ allUnits: true, units: [{ id: "u1", name: "Centro", shortName: "c" }] })),
    http.get("/api/v1/modalities", () => HttpResponse.json([{ id: "m1", name: "Judô", active: true, canManage: false, graduationEnabled: true }])),
    http.get("/api/v1/benefits", ({ request }) => {
      calls.push(new URL(request.url).searchParams);
      if (list) return list();
      return HttpResponse.json({ items: all, page: 1, size: 25, total: all.length });
    }),
  );
  return render(
    <ThemeProvider>
      <MemoryRouter initialEntries={["/beneficios"]}>
        <SessionProvider>
          <App />
        </SessionProvider>
      </MemoryRouter>
    </ThemeProvider>,
  );
}

describe("BenefitsPage (RF-BEN-01)", () => {
  it("CA10: colunas com % ou reais, motivo, decidido por, validade ou Sem prazo e situação; selos Vencido e Encerrado", async () => {
    setup();
    const row = (await screen.findByText("Ana Souza")).closest("tr")!;
    expect(within(row).getByText("Unidade Centro")).toBeInTheDocument();
    expect(within(row).getByText("Judô")).toBeInTheDocument();
    expect(within(row).getByText("30%")).toBeInTheDocument();
    expect(within(row).getByText("Aluno de projeto social")).toBeInTheDocument();
    expect(within(row).getByText("Mestre Fictício")).toBeInTheDocument();
    expect(within(row).getByText("Sem prazo")).toBeInTheDocument();
    expect(within(row).getByText("Ativo")).toBeInTheDocument();
    const expired = screen.getByText("Bruno Lima").closest("tr")!;
    expect(within(expired).getByText("R$ 120,50")).toBeInTheDocument();
    expect(within(expired).getByText("31/01/2026")).toBeInTheDocument();
    expect(within(expired).getByText("Vencido")).toBeInTheDocument();
    expect(within(expired).getByText("Benefício alto")).toBeInTheDocument();
    expect(within(screen.getByText("Carla Dias").closest("tr")!).getByText("Encerrado")).toBeInTheDocument();
    expect(calls.at(-1)!.get("size")).toBe("25");
    expect(screen.queryByRole("button", { name: /conceder|encerrar/i })).toBeNull();
  });

  it("CA10: escolher um filtro refaz a consulta só com ele; Benefícios altos envia high; sem resultado mostra Limpar filtros", async () => {
    const user = userEvent.setup();
    setup("COORDINATOR", () => {
      const filtered = ["type", "high"].some((k) => calls.at(-1)!.get(k));
      return HttpResponse.json({ items: filtered ? [] : all, page: 1, size: 25, total: filtered ? 0 : 3 });
    });
    await screen.findByText("Ana Souza");
    await user.selectOptions(screen.getByLabelText("Tipo"), "AMOUNT");
    await waitFor(() => expect(calls.at(-1)!.get("type")).toBe("AMOUNT"));
    expect(calls.at(-1)!.get("status")).toBeNull();
    expect(await screen.findByText("Nenhum resultado")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Limpar filtros" }));
    expect(await screen.findByText("Ana Souza")).toBeInTheDocument();
    await user.click(screen.getByLabelText("Benefícios altos"));
    await waitFor(() => expect(calls.at(-1)!.get("high")).toBe("true"));
  });

  it("CA11: vazio, erro com Tentar de novo e sem permissão", async () => {
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

  it("CA11: Professor não chega à tela", async () => {
    setup("TEACHER");
    await waitFor(() => expect(screen.queryByText("Carregando...")).toBeNull());
    expect(screen.queryByRole("heading", { name: "Benefícios" })).toBeNull();
  });

  it.each([
    ["claro", false],
    ["escuro", true],
  ])("CA10: a 360 px não tem violações no axe no tema %s", async (_n, dark) => {
    setDevicePrefersDark(dark);
    window.innerWidth = 360;
    const { container } = setup();
    await screen.findByText("Ana Souza");
    expect(await axeViolations(container)).toEqual([]);
  });
});
