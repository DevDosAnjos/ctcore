import { render, screen, waitFor } from "@testing-library/react";
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
import type { PlanSummary } from "./usePlans";

const session = (role: string): SessionView => ({
  account: { id: "a", email: "x@x.com", name: "X" },
  links: [{ id: "l1", organizationId: "o", organizationName: "Alfa", role, unitIds: [] }],
  activeLinkId: "l1",
  twoFactor: { enabled: false, required: false },
});
const full: PlanSummary = {
  plans: { active: 3, inactive: 1, studentsUsing: 0, minValueCents: 15000, maxValueCents: 30000 },
  benefits: { active: 0, high: 0 },
};
const noActive: PlanSummary = { plans: { active: 0, inactive: 2, studentsUsing: 0, minValueCents: null, maxValueCents: null }, benefits: { active: 0, high: 0 } };
const zero: PlanSummary = { plans: { active: 0, inactive: 0, studentsUsing: 0, minValueCents: null, maxValueCents: null }, benefits: { active: 0, high: 0 } };

let calls: URLSearchParams[];

function setup(opts: { role?: string; body?: () => Response | Promise<Response>; units?: { id: string; name: string; shortName: string }[] } = {}) {
  calls = [];
  const role = opts.role ?? "COORDINATOR";
  window.history.replaceState(null, "", "/planos");
  server.use(
    http.get("/api/v1/auth/session", () => HttpResponse.json(session(role))),
    http.get("/api/v1/permissions", () => HttpResponse.json({ permissions: mockPermissions(role) })),
    http.get("/api/v1/units/reachable", () =>
      HttpResponse.json({ allUnits: true, units: opts.units ?? [{ id: "u1", name: "Centro", shortName: "c" }] }),
    ),
    http.get("/api/v1/plans/summary", ({ request }) => {
      calls.push(new URL(request.url).searchParams);
      return opts.body ? opts.body() : HttpResponse.json(full);
    }),
  );
  return render(
    <ThemeProvider>
      <MemoryRouter initialEntries={["/planos"]}>
        <SessionProvider>
          <App />
        </SessionProvider>
      </MemoryRouter>
    </ThemeProvider>,
  );
}

describe("PlansPage (RF-PLA-01)", () => {
  it("CA7: cartões Planos e Benefícios com o resumo e a faixa em reais; botões desativados", async () => {
    setup();
    expect(await screen.findByRole("heading", { name: "Planos", level: 2 })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Benefícios", level: 2 })).toBeInTheDocument();
    expect(screen.getByText("R$ 150,00 a R$ 300,00")).toBeInTheDocument();
    expect(screen.getByText("Desativados").nextSibling).toHaveTextContent("1");
    expect(screen.getByText("Alunos usando")).toBeInTheDocument();
    expect(screen.getByText("Altos")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Ver planos" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "Ver benefícios" })).toBeDisabled();
  });

  it("CA7: sem plano ativo a faixa mostra o texto de vazio; faixa de um valor só mostra um valor", async () => {
    setup({ body: () => HttpResponse.json(noActive) });
    expect(await screen.findByText("Sem plano ativo")).toBeInTheDocument();
  });

  it("CA7: um valor só", async () => {
    setup({ body: () => HttpResponse.json({ ...full, plans: { ...full.plans, minValueCents: 15000, maxValueCents: 15000 } }) });
    expect(await screen.findByText("R$ 150,00")).toBeInTheDocument();
  });

  it("CA8: tudo zerado mostra o estado de vazio com a explicação", async () => {
    setup({ body: () => HttpResponse.json(zero) });
    expect(await screen.findByText("Nenhum plano ainda")).toBeInTheDocument();
    expect(screen.getByText(/Quando houver planos/)).toBeInTheDocument();
  });

  it("CA8: carregando, erro com Tentar de novo e recarga", async () => {
    const user = userEvent.setup();
    let fail = true;
    setup({ body: () => (fail ? HttpResponse.json({ code: "INTERNAL_ERROR" }, { status: 500 }) : HttpResponse.json(full)) });
    expect(await screen.findByText("Carregando...")).toBeInTheDocument();
    const retry = await screen.findByRole("button", { name: "Tentar de novo" });
    fail = false;
    await user.click(retry);
    expect(await screen.findByText("R$ 150,00 a R$ 300,00")).toBeInTheDocument();
  });

  it("CA8: sem permissão quando o servidor responde 403", async () => {
    setup({ body: () => HttpResponse.json({ code: "FORBIDDEN" }, { status: 403 }) });
    expect(await screen.findByText("Você não tem acesso")).toBeInTheDocument();
  });

  it("CA8: Professor vê a tela de sem permissão e não consulta o resumo", async () => {
    setup({ role: "TEACHER" });
    await screen.findByText("Esta área não está disponível para o seu perfil");
    expect(calls).toHaveLength(0);
  });

  it("CA9: trocar a unidade pede o resumo de novo com o unitId escolhido", async () => {
    const user = userEvent.setup();
    setup({ units: [{ id: "u1", name: "Centro", shortName: "c" }, { id: "u2", name: "Norte", shortName: "n" }] });
    await screen.findByText("R$ 150,00 a R$ 300,00");
    expect(calls.at(-1)!.get("unitId")).toBeNull();
    await user.selectOptions(await screen.findByLabelText("Unidade ativa"), "u2");
    await waitFor(() => expect(calls.at(-1)!.get("unitId")).toBe("u2"));
  });

  it("CA10: o menu leva Planos a /planos e Benefícios segue sem link", async () => {
    setup();
    await screen.findByText("R$ 150,00 a R$ 300,00");
    expect(screen.getAllByRole("link", { name: "Planos" })[0]).toHaveAttribute("href", "/planos");
    expect(screen.queryByRole("link", { name: "Benefícios" })).toBeNull();
  });

  it.each([
    ["claro", false],
    ["escuro", true],
  ])("CA8: a 360 px não tem violações no axe no tema %s", async (_n, dark) => {
    setDevicePrefersDark(dark);
    window.innerWidth = 360;
    const { container } = setup();
    await screen.findByText("R$ 150,00 a R$ 300,00");
    expect(await axeViolations(container)).toEqual([]);
  });
});
