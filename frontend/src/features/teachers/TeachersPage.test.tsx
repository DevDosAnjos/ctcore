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
import type { Teacher } from "./useTeachers";

const session = (role: string): SessionView => ({
  account: { id: "a", email: "x@x.com", name: "X" },
  links: [{ id: "l1", organizationId: "o", organizationName: "Alfa", role, unitIds: [] }],
  activeLinkId: "l1",
  twoFactor: { enabled: false, required: false },
});
const teacher = (over: Partial<Teacher>): Teacher => ({
  id: "t1",
  name: "Ana Souza",
  photoUrl: null,
  modalities: [{ id: "m1", name: "Jiu-Jitsu", level: "Roxa", degree: 2 }],
  units: [{ id: "u1", name: "Centro" }],
  classCount: 0,
  status: "ACTIVE",
  directoryConsent: false,
  access: "NONE",
  ...over,
});
const all = [
  teacher({}),
  teacher({ id: "t2", name: "João Pereira", modalities: [{ id: "m2", name: "Judô", level: null, degree: null }], units: [{ id: "u1", name: "Centro" }, { id: "u2", name: "Norte" }], status: "INACTIVE" }),
];

let calls: URLSearchParams[];

function setup(role = "COORDINATOR", list?: () => Response | Promise<Response>) {
  calls = [];
  window.history.replaceState(null, "", "/professores");
  server.use(
    http.get("/api/v1/auth/session", () => HttpResponse.json(session(role))),
    http.get("/api/v1/permissions", () => HttpResponse.json({ permissions: mockPermissions(role) })),
    http.get("/api/v1/units/reachable", () => HttpResponse.json({ allUnits: true, units: [{ id: "u1", name: "Centro", shortName: "c" }, { id: "u2", name: "Norte", shortName: "n" }] })),
    http.get("/api/v1/modalities", () =>
      HttpResponse.json([
        { id: "m1", name: "Jiu-Jitsu", active: true, canManage: false, graduationEnabled: true },
        { id: "m2", name: "Judô", active: true, canManage: false, graduationEnabled: false },
        { id: "m3", name: "Boxe", active: false, canManage: false, graduationEnabled: false },
      ]),
    ),
    http.get("/api/v1/teachers", ({ request }) => {
      const p = new URL(request.url).searchParams;
      calls.push(p);
      if (list) return list();
      const q = (p.get("q") ?? "").toLowerCase();
      const items = all.filter((t) => t.name.toLowerCase().includes(q) && (!p.get("status") || t.status === p.get("status")));
      return HttpResponse.json({ items, page: 1, size: 25, total: items.length });
    }),
  );
  return render(
    <ThemeProvider>
      <MemoryRouter initialEntries={["/professores"]}>
        <SessionProvider>
          <App />
        </SessionProvider>
      </MemoryRouter>
    </ThemeProvider>,
  );
}

const ready = () => screen.findByRole("heading", { name: "Professores", level: 1 });

describe("TeachersPage", () => {
  it("CA8: lista nome, modalidades com graduação, unidades, turmas e situação; iniciais sem foto", async () => {
    setup();
    await ready();
    const row = (await screen.findByText("Ana Souza")).closest("tr")!;
    expect(within(row).getByText("AS")).toBeInTheDocument();
    expect(within(row).getByText("Jiu-Jitsu · Roxa, grau 2")).toBeInTheDocument();
    expect(within(row).getByText("Centro")).toBeInTheDocument();
    expect(within(row).getByText("0")).toBeInTheDocument();
    expect(within(row).getByText("Ativo")).toBeInTheDocument();
    const other = screen.getByText("João Pereira").closest("tr")!;
    expect(within(other).getByText("Judô")).toBeInTheDocument();
    expect(within(other).getByText("Centro, Norte")).toBeInTheDocument();
    expect(within(other).getByText("Inativo")).toBeInTheDocument();
    expect(calls.at(-1)!.get("page")).toBe("1");
    expect(calls.at(-1)!.get("size")).toBe("25");
  });

  it("CA8: foto aparece quando há URL", async () => {
    setup("COORDINATOR", () => HttpResponse.json({ items: [teacher({ photoUrl: "https://files.example/foto.png" })], page: 1, size: 25, total: 1 }));
    await screen.findByText("Ana Souza");
    expect(document.querySelector("img[src='https://files.example/foto.png']")).not.toBeNull();
    expect(screen.queryByText("AS")).toBeNull();
  });

  it("CA8: filtros de modalidade e unidade só com opções ativas; trocar filtro e buscar refaz a consulta", async () => {
    const user = userEvent.setup();
    setup();
    await screen.findByText("Ana Souza");
    const modality = screen.getByLabelText("Modalidade");
    await waitFor(() => expect(within(modality).getAllByRole("option").map((o) => o.textContent)).toEqual(["Todas as modalidades", "Jiu-Jitsu", "Judô"]));
    await user.selectOptions(modality, "m1");
    await waitFor(() => expect(calls.at(-1)!.get("modalityId")).toBe("m1"));
    await user.selectOptions(screen.getByLabelText("Unidade"), "u2");
    await waitFor(() => expect(calls.at(-1)!.get("unitId")).toBe("u2"));
    await user.selectOptions(screen.getByLabelText("Situação"), "INACTIVE");
    await waitFor(() => expect(calls.at(-1)!.get("status")).toBe("INACTIVE"));
    await user.type(screen.getByRole("searchbox", { name: "Buscar" }), "joao");
    await waitFor(() => expect(calls.at(-1)!.get("q")).toBe("joao"));
    expect(calls.at(-1)!.get("modalityId")).toBe("m1");
  });

  it("CA8: ordenar pelo nome pede sort=name; filtro sem resultado mostra Limpar filtros", async () => {
    const user = userEvent.setup();
    setup();
    await screen.findByText("Ana Souza");
    await user.click(screen.getByRole("button", { name: "Ordenar por Nome" }));
    await waitFor(() => expect(calls.at(-1)!.get("sort")).toBe("name,asc"));
    await user.click(screen.getByRole("button", { name: "Ordenar por Nome" }));
    await waitFor(() => expect(calls.at(-1)!.get("sort")).toBe("name,desc"));
    await user.type(screen.getByRole("searchbox", { name: "Buscar" }), "zzz");
    expect(await screen.findByText("Nenhum resultado")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Limpar filtros" }));
    expect(await screen.findByText("Ana Souza")).toBeInTheDocument();
  });

  it("CA8: vazio sem professores", async () => {
    setup("COORDINATOR", () => HttpResponse.json({ items: [], page: 1, size: 25, total: 0 }));
    expect(await screen.findByText("Nada por aqui ainda")).toBeInTheDocument();
  });

  it("CA8: erro com Tentar de novo refaz a consulta", async () => {
    const user = userEvent.setup();
    let fail = true;
    setup("COORDINATOR", () => (fail ? HttpResponse.json({ code: "INTERNAL_ERROR" }, { status: 500 }) : HttpResponse.json({ items: all, page: 1, size: 25, total: 2 })));
    const retry = await screen.findByRole("button", { name: "Tentar de novo" });
    fail = false;
    await user.click(retry);
    expect(await screen.findByText("Ana Souza")).toBeInTheDocument();
  });

  it("CA8: sem permissão quando o servidor responde 403", async () => {
    setup("COORDINATOR", () => HttpResponse.json({ code: "FORBIDDEN" }, { status: 403 }));
    expect(await screen.findByText("Você não tem acesso")).toBeInTheDocument();
  });

  it.each(["COORDINATOR", "ADMINISTRATOR"])("CA9: %s vê Professores no menu e chega à tela", async (role) => {
    setup(role);
    await ready();
    const links = screen.getAllByRole("link", { name: "Professores" });
    expect(links.length).toBeGreaterThan(0);
    expect(links[0]).toHaveAttribute("href", "/professores");
  });

  it("CA9: Professor não vê o item no menu", async () => {
    setup("TEACHER");
    await waitFor(() => expect(screen.queryByText("Carregando...")).toBeNull());
    expect(screen.queryByRole("link", { name: "Professores" })).toBeNull();
  });

  it.each([
    ["claro", false],
    ["escuro", true],
  ])("CA8: a 360 px não tem violações no axe no tema %s", async (_n, dark) => {
    setDevicePrefersDark(dark);
    window.innerWidth = 360;
    const { container } = setup();
    await screen.findByText("Ana Souza");
    expect(await axeViolations(container)).toEqual([]);
  });
});
