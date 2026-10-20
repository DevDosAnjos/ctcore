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
import type { TrainingClass } from "./useClasses";

const session = (role: string): SessionView => ({
  account: { id: "a", email: "x@x.com", name: "X" },
  links: [{ id: "l1", organizationId: "o", organizationName: "Alfa", role, unitIds: [] }],
  activeLinkId: "l1",
  twoFactor: { enabled: false, required: false },
});
const klass = (over: Partial<TrainingClass>): TrainingClass => ({
  id: "c1",
  name: "Jiu-Jitsu Adulto",
  unit: { id: "u1", name: "Centro" },
  modality: { id: "m1", name: "Jiu-Jitsu" },
  ageRange: { id: "a1", name: "Adulto", minAge: 18, maxAge: null },
  schedule: [
    { weekday: 1, startsAt: "19:00", endsAt: "20:00" },
    { weekday: 3, startsAt: "19:00", endsAt: "20:00" },
  ],
  head: { id: "t1", name: "Ana Souza" },
  assistants: [{ id: "t2", name: "João Pereira" }],
  activeStudents: 12,
  capacity: 30,
  overCapacity: false,
  status: "ACTIVE",
  ...over,
});
const all = [
  klass({}),
  klass({ id: "c2", name: "Boxe Noturno", modality: { id: "m2", name: "Boxe" }, ageRange: null, assistants: [], capacity: null, activeStudents: 0, status: "INACTIVE", schedule: [{ weekday: 5, startsAt: "20:00", endsAt: "21:30" }] }),
  klass({ id: "c3", name: "Judô Lotada", activeStudents: 31, capacity: 30, overCapacity: true }),
];

let calls: URLSearchParams[];

function setup(role = "COORDINATOR", list?: () => Response | Promise<Response>) {
  calls = [];
  window.history.replaceState(null, "", "/turmas");
  server.use(
    http.get("/api/v1/auth/session", () => HttpResponse.json(session(role))),
    http.get("/api/v1/permissions", () => HttpResponse.json({ permissions: mockPermissions(role) })),
    http.get("/api/v1/units/reachable", () => HttpResponse.json({ allUnits: true, units: [{ id: "u1", name: "Centro", shortName: "c" }] })),
    http.get("/api/v1/modalities", () =>
      HttpResponse.json([
        { id: "m1", name: "Jiu-Jitsu", active: true, canManage: false, graduationEnabled: true },
        { id: "m3", name: "Karatê", active: false, canManage: false, graduationEnabled: false },
      ]),
    ),
    http.get("/api/v1/teachers", () => HttpResponse.json({ items: [{ id: "t1", name: "Ana Souza" }], page: 1, size: 100, total: 1 })),
    http.get("/api/v1/age-ranges", () => HttpResponse.json({ canManage: false, items: [{ id: "a1", name: "Adulto", minAge: 18, maxAge: null }] })),
    http.get("/api/v1/classes", ({ request }) => {
      const p = new URL(request.url).searchParams;
      calls.push(p);
      if (list) return list();
      const q = (p.get("q") ?? "").toLowerCase();
      const items = all.filter((c) => c.name.toLowerCase().includes(q) && (!p.get("status") || c.status === p.get("status")));
      return HttpResponse.json({ items, page: 1, size: 25, total: items.length });
    }),
  );
  return render(
    <ThemeProvider>
      <MemoryRouter initialEntries={["/turmas"]}>
        <SessionProvider>
          <App />
        </SessionProvider>
      </MemoryRouter>
    </ThemeProvider>,
  );
}

const ready = () => screen.findByRole("heading", { name: "Turmas", level: 1 });

describe("ClassesPage", () => {
  it("CA9: lista nome, unidade, modalidade, faixa (traço quando ausente), dias e horários, professor, alunos/capacidade e situação; selo Desativada", async () => {
    setup();
    await ready();
    const row = (await screen.findByText("Jiu-Jitsu Adulto")).closest("tr")!;
    expect(within(row).getByText("Unidade: Centro")).toBeInTheDocument();
    expect(within(row).getByText("Jiu-Jitsu")).toBeInTheDocument();
    expect(within(row).getByText("Adulto")).toBeInTheDocument();
    expect(within(row).getByText("Seg 19:00–20:00, Qua 19:00–20:00")).toBeInTheDocument();
    expect(within(row).getByText("Ana Souza")).toBeInTheDocument();
    expect(within(row).getByText("Auxiliares: João Pereira")).toBeInTheDocument();
    expect(within(row).getByText("12/30")).toBeInTheDocument();
    expect(within(row).getByText("Ativa")).toBeInTheDocument();
    expect(within(row).queryByText(/Acima da capacidade/)).toBeNull();
    const other = screen.getByText("Boxe Noturno").closest("tr")!;
    expect(within(other).getByText("—")).toBeInTheDocument();
    expect(within(other).getByText("Desativada")).toBeInTheDocument();
    expect(within(other).getByText("Sex 20:00–21:30")).toBeInTheDocument();
    expect(calls.at(-1)!.get("page")).toBe("1");
    expect(calls.at(-1)!.get("size")).toBe("25");
  });

  it("CA8: o nome da turma leva ao painel", async () => {
    setup();
    const link = await screen.findByRole("link", { name: "Jiu-Jitsu Adulto" });
    expect(link.getAttribute("href")).toContain("/turmas/");
  });

  it("CA3: acima da capacidade mostra o aviso com os números; sem capacidade mostra só os alunos", async () => {
    setup();
    const full = (await screen.findByText("Judô Lotada")).closest("tr")!;
    expect(within(full).getByText("Acima da capacidade (31/30)")).toBeInTheDocument();
    expect(within(full).getByText("31/30")).toBeInTheDocument();
    const free = screen.getByText("Boxe Noturno").closest("tr")!;
    expect(within(free).getByText("0")).toBeInTheDocument();
    expect(within(free).queryByText(/\//)).toBeNull();
  });

  it("CA9: filtros só com opções ativas; trocar filtro, dia e busca refaz a consulta", async () => {
    const user = userEvent.setup();
    setup();
    await screen.findByText("Jiu-Jitsu Adulto");
    const modality = screen.getByLabelText("Modalidade");
    await waitFor(() => expect(within(modality).getAllByRole("option").map((o) => o.textContent)).toEqual(["Todas as modalidades", "Jiu-Jitsu"]));
    await user.selectOptions(modality, "m1");
    await waitFor(() => expect(calls.at(-1)!.get("modalityId")).toBe("m1"));
    await user.selectOptions(screen.getByLabelText("Professor"), "t1");
    await waitFor(() => expect(calls.at(-1)!.get("teacherId")).toBe("t1"));
    await user.selectOptions(screen.getByLabelText("Faixa etária"), "a1");
    await waitFor(() => expect(calls.at(-1)!.get("ageRangeId")).toBe("a1"));
    await user.selectOptions(screen.getByLabelText("Dia"), "3");
    await waitFor(() => expect(calls.at(-1)!.get("weekday")).toBe("3"));
    await user.selectOptions(screen.getByLabelText("Unidade"), "u1");
    await waitFor(() => expect(calls.at(-1)!.get("unitId")).toBe("u1"));
    await user.selectOptions(screen.getByLabelText("Situação"), "INACTIVE");
    await waitFor(() => expect(calls.at(-1)!.get("status")).toBe("INACTIVE"));
    await user.type(screen.getByRole("searchbox", { name: "Buscar" }), "box");
    await waitFor(() => expect(calls.at(-1)!.get("q")).toBe("box"));
    expect(calls.at(-1)!.get("modalityId")).toBe("m1");
  });

  it("CA9: ordenar pelo nome pede sort=name; filtro sem resultado mostra Limpar filtros", async () => {
    const user = userEvent.setup();
    setup();
    await screen.findByText("Jiu-Jitsu Adulto");
    await user.click(screen.getByRole("button", { name: "Ordenar por Turma" }));
    await waitFor(() => expect(calls.at(-1)!.get("sort")).toBe("name,asc"));
    await user.type(screen.getByRole("searchbox", { name: "Buscar" }), "zzz");
    expect(await screen.findByText("Nenhum resultado")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Limpar filtros" }));
    expect(await screen.findByText("Jiu-Jitsu Adulto")).toBeInTheDocument();
  });

  it("CA9: vazio sem turmas", async () => {
    setup("COORDINATOR", () => HttpResponse.json({ items: [], page: 1, size: 25, total: 0 }));
    expect(await screen.findByText("Nada por aqui ainda")).toBeInTheDocument();
  });

  it("CA9: erro com Tentar de novo refaz a consulta", async () => {
    const user = userEvent.setup();
    let fail = true;
    setup("COORDINATOR", () => (fail ? HttpResponse.json({ code: "INTERNAL_ERROR" }, { status: 500 }) : HttpResponse.json({ items: all, page: 1, size: 25, total: 3 })));
    const retry = await screen.findByRole("button", { name: "Tentar de novo" });
    fail = false;
    await user.click(retry);
    expect(await screen.findByText("Jiu-Jitsu Adulto")).toBeInTheDocument();
  });

  it("CA9: sem permissão quando o servidor responde 403", async () => {
    setup("COORDINATOR", () => HttpResponse.json({ code: "FORBIDDEN" }, { status: 403 }));
    expect(await screen.findByText("Você não tem acesso")).toBeInTheDocument();
  });

  it.each(["COORDINATOR", "ADMINISTRATOR"])("CA10: %s vê Turmas em Operação e chega à tela", async (role) => {
    setup(role);
    await ready();
    const links = screen.getAllByRole("link", { name: "Turmas" });
    expect(links[0]).toHaveAttribute("href", "/turmas");
  });

  it("CA10: Professor não vê o item no menu", async () => {
    setup("TEACHER");
    await waitFor(() => expect(screen.queryByText("Carregando...")).toBeNull());
    expect(screen.queryByRole("link", { name: "Turmas" })).toBeNull();
  });

  it("CA4: o arquivo de tradução da tela não menciona presença nem frequência", () => {
    const text = JSON.stringify(JSON.parse(readFileSync(join(process.cwd(), "src/i18n/pt-BR.json"), "utf8")).classes);
    expect(text).not.toMatch(/presen|frequên|frequen/i);
  });

  it.each([
    ["claro", false],
    ["escuro", true],
  ])("CA9: a 360 px não tem violações no axe no tema %s", async (_n, dark) => {
    setDevicePrefersDark(dark);
    window.innerWidth = 360;
    const { container } = setup();
    await screen.findByText("Jiu-Jitsu Adulto");
    expect(await axeViolations(container)).toEqual([]);
  });
});
