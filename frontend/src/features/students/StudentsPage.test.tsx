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
import type { StudentRow } from "./types";

const session = (role: string, accountId = "a"): SessionView => ({
  account: { id: accountId, email: "x@x.com", name: "X" },
  links: [{ id: "l1", organizationId: "o", organizationName: "Alfa", role, unitIds: [] }],
  activeLinkId: "l1",
  twoFactor: { enabled: false, required: false },
});
const student = (over: Partial<StudentRow>): StudentRow => ({
  id: "s1",
  name: "Ana Souza",
  photoUrl: null,
  birthDate: "2014-05-02",
  age: 12,
  minor: true,
  ageRange: { id: "r1", name: "Infantil" },
  registeredUnit: { id: "u1", name: "Centro" },
  createdAt: "2026-01-10T12:00:00Z",
  seals: [],
  status: "NONE",
  financialStatus: "CURRENT",
  modalities: [],
  ...over,
});
const bjj = { id: "mod1", unit: { id: "u1", name: "Centro" }, modality: { id: "m1", name: "Jiu-Jitsu" }, primaryClass: { id: "k1", name: "Turma Noite" }, level: null };
const judo = { id: "mod2", unit: { id: "u2", name: "Norte" }, modality: { id: "m3", name: "Judô" }, primaryClass: { id: "k2", name: "Turma Manhã" }, level: null };
const all = [student({ seals: ["INCOMPLETE_REGISTRATION", "BIRTHDAY_TODAY"], status: "ACTIVE", modalities: [bjj, judo] }), student({ id: "s2", name: "João Pereira", birthDate: "1990-03-10", age: 36, minor: false, ageRange: null, registeredUnit: { id: "u2", name: "Norte" } })];

let calls: URLSearchParams[];

function setup(role = "COORDINATOR", list?: () => Response | Promise<Response>, accountId = "a") {
  calls = [];
  window.history.replaceState(null, "", "/alunos");
  server.use(
    http.get("/api/v1/auth/session", () => HttpResponse.json(session(role, accountId))),
    http.get("/api/v1/permissions", () => HttpResponse.json({ permissions: mockPermissions(role) })),
    http.get("/api/v1/units/reachable", () => HttpResponse.json({ allUnits: true, units: [{ id: "u1", name: "Centro", shortName: "c" }, { id: "u2", name: "Norte", shortName: "n" }] })),
    http.get("/api/v1/age-ranges", () => HttpResponse.json({ canManage: false, items: [{ id: "r1", name: "Infantil", minAge: 4, maxAge: 12 }, { id: "r2", name: "Adulto", minAge: 18, maxAge: null }] })),
    http.get("/api/v1/modalities", () => HttpResponse.json([{ id: "m1", name: "Jiu-Jitsu", active: true }, { id: "m2", name: "Boxe", active: false }, { id: "m3", name: "Judô", active: true }])),
    http.get("/api/v1/classes", () => HttpResponse.json({ items: [{ id: "k1", name: "Turma Noite" }, { id: "k2", name: "Turma Manhã" }], page: 1, size: 100, total: 2 })),
    http.get("/api/v1/modalities/:id/graduation", () => HttpResponse.json({ enabled: true, requireOriginProof: false, canManage: false, levels: [{ id: "l1", name: "Azul", color: "#0000CD", secondaryColor: null, maxDegree: 4, minAge: 16, maxAge: null, kids: false }] })),
    http.get("/api/v1/students", ({ request }) => {
      const p = new URL(request.url).searchParams;
      calls.push(p);
      if (list) return list();
      const q = (p.get("q") ?? "").toLowerCase();
      const items = all.filter((s) => s.name.toLowerCase().includes(q));
      return HttpResponse.json({ items, page: 1, size: 25, total: items.length, counts: { active: 3, former: 1, overdue: 2 } });
    }),
  );
  return render(
    <ThemeProvider>
      <MemoryRouter initialEntries={["/alunos"]}>
        <SessionProvider>
          <App />
        </SessionProvider>
      </MemoryRouter>
    </ThemeProvider>,
  );
}

const ready = () => screen.findByRole("heading", { name: "Alunos", level: 1 });

describe("StudentsPage", () => {
  beforeEach(() => localStorage.clear());

  it("CA9: lista nome, unidade, idade, faixa e data; iniciais sem foto; traço sem faixa; pede a página 25 por nome", async () => {
    setup();
    await ready();
    const row = (await screen.findByText("Ana Souza")).closest("tr")!;
    expect(within(row).getByText("AS")).toBeInTheDocument();
    expect(within(row).getByText("Centro")).toBeInTheDocument();
    expect(within(row).getByText("12 anos")).toBeInTheDocument();
    expect(within(row).getByText("Infantil")).toBeInTheDocument();
    expect(within(row).getByText("10/01/2026")).toBeInTheDocument();
    const other = screen.getByText("João Pereira").closest("tr")!;
    // Faixa e modalidades vazias.
    expect(within(other).getAllByText("—")).toHaveLength(2);
    expect(calls.at(-1)!.get("page")).toBe("1");
    expect(calls.at(-1)!.get("size")).toBe("25");
    expect(calls.at(-1)!.get("sort")).toBe("name");
  });

  it("CA9: foto aparece quando há URL", async () => {
    setup("COORDINATOR", () => HttpResponse.json({ items: [student({ photoUrl: "https://files.example/foto.png" })], page: 1, size: 25, total: 1 }));
    await screen.findByText("Ana Souza");
    expect(document.querySelector("img[src='https://files.example/foto.png']")).not.toBeNull();
    expect(screen.queryByText("AS")).toBeNull();
  });

  it("CA9: filtros de unidade e faixa e a busca refazem a consulta e combinam", async () => {
    const user = userEvent.setup();
    setup();
    await screen.findByText("Ana Souza");
    const unit = screen.getByRole("combobox", { name: "Unidade" });
    await waitFor(() => expect(within(unit).getAllByRole("option").map((o) => o.textContent)).toEqual(["Todas as unidades", "Centro", "Norte"]));
    await user.selectOptions(unit, "u2");
    await waitFor(() => expect(calls.at(-1)!.get("unitId")).toBe("u2"));
    const range = screen.getByRole("combobox", { name: "Faixa etária" });
    await waitFor(() => expect(within(range).getAllByRole("option").map((o) => o.textContent)).toEqual(["Todas as faixas", "Infantil", "Adulto"]));
    await user.selectOptions(range, "r2");
    await waitFor(() => expect(calls.at(-1)!.get("ageRangeId")).toBe("r2"));
    await user.type(screen.getByRole("searchbox", { name: "Buscar" }), "joao");
    await waitFor(() => expect(calls.at(-1)!.get("q")).toBe("joao"));
    expect(calls.at(-1)!.get("unitId")).toBe("u2");
  });

  it("CA9: selos aparecem como texto junto ao nome e o filtro Selo refaz a consulta e some em Limpar filtros", async () => {
    const user = userEvent.setup();
    setup("COORDINATOR", () =>
      HttpResponse.json({ items: calls.at(-1)!.get("seal") ? [] : all, page: 1, size: 25, total: calls.at(-1)!.get("seal") ? 0 : all.length }),
    );
    await screen.findByText("Ana Souza");
    const badges = (text: string) => screen.queryAllByText(text).filter((e) => e.tagName !== "OPTION");
    expect(badges("Cadastro incompleto")).toHaveLength(1);
    expect(badges("Aniversário hoje")).toHaveLength(1);
    expect(badges("E-mail com problema")).toHaveLength(0);
    const seal = screen.getByRole("combobox", { name: "Selo" });
    expect(within(seal).getAllByRole("option").map((o) => o.textContent)).toEqual(["Todos os selos", "Cadastro incompleto", "E-mail com problema", "Aniversário hoje", "Novo", "Bolsista"]);
    await user.selectOptions(seal, "EMAIL_PROBLEM");
    await waitFor(() => expect(calls.at(-1)!.get("seal")).toBe("EMAIL_PROBLEM"));
    await user.click(await screen.findByRole("button", { name: "Limpar filtros" }));
    await waitFor(() => expect(calls.at(-1)!.get("seal")).toBeNull());
  });

  it("CA27: a linha mostra todas as modalidades ativas com unidade e turma principal e a situação; sem matrícula, traço e Sem matrícula", async () => {
    setup();
    const row = (await screen.findByText("Ana Souza")).closest("tr")!;
    expect(within(row).getByText("Jiu-Jitsu · Centro · Turma Noite")).toBeInTheDocument();
    expect(within(row).getByText("Judô · Norte · Turma Manhã")).toBeInTheDocument();
    expect(within(row).getByText("Ativo")).toBeInTheDocument();
    expect(within(screen.getByText("João Pereira").closest("tr")!).getByText("Sem matrícula")).toBeInTheDocument();
  });

  it("CA27: selo Novo aparece como texto junto ao nome e filtra por selo", async () => {
    const user = userEvent.setup();
    setup("COORDINATOR", () => HttpResponse.json({ items: [student({ seals: ["NEW"] })], page: 1, size: 25, total: 1, counts: { active: 1, former: 0 } }));
    await screen.findByText("Ana Souza");
    expect(screen.getAllByText("Novo").some((e) => e.tagName !== "OPTION")).toBe(true);
    await user.selectOptions(screen.getByRole("combobox", { name: "Selo" }), "NEW");
    await waitFor(() => expect(calls.at(-1)!.get("seal")).toBe("NEW"));
  });

  it("CA28: filtros de modalidade, turma e nível refazem a consulta e combinam; o nível só abre com a modalidade e vem da graduação dela", async () => {
    const user = userEvent.setup();
    setup();
    await screen.findByText("Ana Souza");
    const modality = screen.getByRole("combobox", { name: "Modalidade" });
    await waitFor(() => expect(within(modality).getAllByRole("option").map((o) => o.textContent)).toEqual(["Todas as modalidades", "Jiu-Jitsu", "Judô"]));
    const klass = screen.getByRole("combobox", { name: "Turma" });
    await waitFor(() => expect(within(klass).getAllByRole("option").map((o) => o.textContent)).toEqual(["Todas as turmas", "Turma Noite", "Turma Manhã"]));
    const level = screen.getByRole("combobox", { name: "Nível" });
    expect(level).toBeDisabled();
    await user.selectOptions(modality, "m1");
    await waitFor(() => expect(calls.at(-1)!.get("modalityId")).toBe("m1"));
    await waitFor(() => expect(level).toBeEnabled());
    await waitFor(() => expect(within(level).getAllByRole("option").map((o) => o.textContent)).toEqual(["Todos os níveis", "Azul"]));
    await user.selectOptions(level, "l1");
    await waitFor(() => expect(calls.at(-1)!.get("levelId")).toBe("l1"));
    await user.selectOptions(klass, "k1");
    await waitFor(() => expect(calls.at(-1)!.get("classId")).toBe("k1"));
    expect(calls.at(-1)!.get("modalityId")).toBe("m1");
    expect(calls.at(-1)!.get("levelId")).toBe("l1");
    // Trocar a modalidade limpa o nível.
    await user.selectOptions(modality, "m3");
    await waitFor(() => expect(calls.at(-1)!.get("modalityId")).toBe("m3"));
    expect(calls.at(-1)!.get("levelId")).toBeNull();
  });

  it("CA20: a coluna Situação financeira mostra Em atraso e Em dia e o atalho Em atraso (N) filtra e se desliga", async () => {
    const user = userEvent.setup();
    setup("COORDINATOR", () => HttpResponse.json({ items: [student({ financialStatus: "OVERDUE" }), student({ id: "s2", name: "João Pereira", financialStatus: "CURRENT" })], page: 1, size: 25, total: 2, counts: { active: 3, former: 1, overdue: 2 } }));
    const row = (await screen.findByText("Ana Souza")).closest("tr")!;
    expect(within(row).getByText("Em atraso")).toBeInTheDocument();
    expect(within(screen.getByText("João Pereira").closest("tr")!).getByText("Em dia")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Em atraso (2)" }));
    await waitFor(() => expect(calls.at(-1)!.get("financial")).toBe("OVERDUE"));
    expect(screen.getByRole("button", { name: "Em atraso (2)" })).toHaveAttribute("aria-pressed", "true");
    await user.click(screen.getByRole("button", { name: "Em atraso (2)" }));
    await waitFor(() => expect(calls.at(-1)!.get("financial")).toBeNull());
  });

  it("CA29: filtros rápidos Ativos e Ex-alunos mostram a contagem do servidor, filtram e se desligam com o segundo clique", async () => {
    const user = userEvent.setup();
    setup();
    const active = await screen.findByRole("button", { name: "Ativos (3)" });
    const former = screen.getByRole("button", { name: "Ex-alunos (1)" });
    expect(active).toHaveAttribute("aria-pressed", "false");
    await user.click(active);
    await waitFor(() => expect(calls.at(-1)!.get("status")).toBe("ACTIVE"));
    expect(screen.getByRole("button", { name: "Ativos (3)" })).toHaveAttribute("aria-pressed", "true");
    await user.click(former);
    await waitFor(() => expect(calls.at(-1)!.get("status")).toBe("FORMER"));
    await user.click(screen.getByRole("button", { name: "Ex-alunos (1)" }));
    await waitFor(() => expect(calls.at(-1)!.get("status")).toBeNull());
  });

  it("CA9: vazio sem alunos; outro texto com Limpar filtros quando o filtro não acha ninguém", async () => {
    const user = userEvent.setup();
    setup("COORDINATOR", () => HttpResponse.json({ items: [], page: 1, size: 25, total: 0 }));
    expect(await screen.findByText("Nada por aqui ainda")).toBeInTheDocument();
    await user.type(screen.getByRole("searchbox", { name: "Buscar" }), "zzz");
    expect(await screen.findByText("Nenhum resultado")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Limpar filtros" }));
    expect(await screen.findByText("Nada por aqui ainda")).toBeInTheDocument();
  });

  it("CA9: erro com Tentar de novo refaz a consulta", async () => {
    const user = userEvent.setup();
    let fail = true;
    setup("COORDINATOR", () => (fail ? HttpResponse.json({ code: "INTERNAL_ERROR" }, { status: 500 }) : HttpResponse.json({ items: all, page: 1, size: 25, total: 2 })));
    const retry = await screen.findByRole("button", { name: "Tentar de novo" });
    fail = false;
    await user.click(retry);
    expect(await screen.findByText("Ana Souza")).toBeInTheDocument();
  });

  it("CA9: sem permissão quando o servidor responde 403", async () => {
    setup("COORDINATOR", () => HttpResponse.json({ code: "FORBIDDEN" }, { status: 403 }));
    expect(await screen.findByText("Você não tem acesso")).toBeInTheDocument();
  });

  it("CA9: trocar de página refaz a consulta", async () => {
    const user = userEvent.setup();
    setup("COORDINATOR", () => HttpResponse.json({ items: all, page: 1, size: 25, total: 60 }));
    await screen.findByText("Ana Souza");
    await user.click(screen.getByRole("button", { name: "Próxima página" }));
    await waitFor(() => expect(calls.at(-1)!.get("page")).toBe("2"));
  });

  it("CA8: Nome é fixo; desligar Idade tira a coluna, a escolha volta ao reabrir com a mesma conta e não vale para outra", async () => {
    const user = userEvent.setup();
    const first = setup();
    await screen.findByText("Ana Souza");
    expect(screen.getByRole("columnheader", { name: "Idade" })).toBeInTheDocument();
    expect(screen.queryByRole("checkbox", { name: "Nome" })).toBeNull();
    await user.click(screen.getByRole("checkbox", { name: "Idade" }));
    expect(screen.queryByRole("columnheader", { name: "Idade" })).toBeNull();
    expect(screen.getByRole("columnheader", { name: "Nome" })).toBeInTheDocument();
    first.unmount();

    setup();
    await screen.findByText("Ana Souza");
    expect(screen.queryByRole("columnheader", { name: "Idade" })).toBeNull();
    expect(screen.getByRole("columnheader", { name: "Unidade" })).toBeInTheDocument();
  });


  it("CA8: outra conta abre com todas as colunas", async () => {
    localStorage.setItem("ctcore.studentColumns.a", JSON.stringify(["unit"]));
    setup("COORDINATOR", undefined, "b");
    await screen.findByText("Ana Souza");
    for (const name of ["Modalidades", "Situação", "Unidade", "Idade", "Faixa etária", "Cadastrado em"]) expect(screen.getByRole("columnheader", { name })).toBeInTheDocument();
  });

  it("CA8: sem armazenamento disponível a tela abre com todas as colunas e a escolha vale na página", async () => {
    const user = userEvent.setup();
    const get = vi.spyOn(Storage.prototype, "getItem").mockImplementation(() => {
      throw new Error("blocked");
    });
    const set = vi.spyOn(Storage.prototype, "setItem").mockImplementation(() => {
      throw new Error("blocked");
    });
    setup();
    await screen.findByText("Ana Souza");
    expect(screen.getByRole("columnheader", { name: "Idade" })).toBeInTheDocument();
    await user.click(screen.getByRole("checkbox", { name: "Idade" }));
    expect(screen.queryByRole("columnheader", { name: "Idade" })).toBeNull();
    get.mockRestore();
    set.mockRestore();
  });

  it.each(["COORDINATOR", "ADMINISTRATOR"])("CA10: %s vê Alunos no menu e chega à tela", async (role) => {
    setup(role);
    await ready();
    const links = screen.getAllByRole("link", { name: "Alunos" });
    expect(links.length).toBeGreaterThan(0);
    expect(links[0]).toHaveAttribute("href", "/alunos");
  });

  it("CA10: Professor não vê o item no menu", async () => {
    setup("TEACHER");
    await waitFor(() => expect(screen.queryByText("Carregando...")).toBeNull());
    expect(screen.queryByRole("link", { name: "Alunos" })).toBeNull();
  });

  it.each([
    ["claro", false],
    ["escuro", true],
  ])("CA9: a 360 px não tem violações no axe no tema %s", async (_n, dark) => {
    setDevicePrefersDark(dark);
    window.innerWidth = 360;
    const { container } = setup();
    await screen.findByText("Ana Souza");
    expect(await axeViolations(container)).toEqual([]);
  });
});
