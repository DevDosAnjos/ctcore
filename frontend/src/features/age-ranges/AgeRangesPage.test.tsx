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
import type { AgeRange } from "./useAgeRanges";

const session = (role: string): SessionView => ({
  account: { id: "a", email: "x@x.com", name: "X" },
  links: [{ id: "l1", organizationId: "o", organizationName: "Alfa", role, unitIds: [] }],
  activeLinkId: "l1",
  twoFactor: { enabled: false, required: false },
});
const defaults = (): AgeRange[] => [
  { id: "r1", name: "Infantil", minAge: 4, maxAge: 12 },
  { id: "r2", name: "Juvenil", minAge: 13, maxAge: 17 },
  { id: "r3", name: "Adulto", minAge: 18, maxAge: null },
];

let items: AgeRange[];
let writes: { method: string; body: unknown }[];

function setup(role: string, over: { list?: () => Response | Promise<Response>; initial?: AgeRange[] } = {}) {
  items = over.initial ?? defaults();
  writes = [];
  server.use(
    http.get("/api/v1/auth/session", () => HttpResponse.json(session(role))),
    http.get("/api/v1/permissions", () => HttpResponse.json({ permissions: mockPermissions(role) })),
    http.get("/api/v1/units/reachable", () => HttpResponse.json({ allUnits: false, units: [] })),
    http.get("/api/v1/age-ranges", over.list ?? (() => HttpResponse.json({ canManage: role === "COORDINATOR", items }))),
    http.post("/api/v1/age-ranges", async ({ request }) => {
      const body = (await request.json()) as Omit<AgeRange, "id">;
      writes.push({ method: "POST", body });
      if (body.name.toLowerCase() === "juvenil") return HttpResponse.json({ code: "AGE_RANGE_NAME_TAKEN", fields: [] }, { status: 409 });
      const created = { id: "novo", ...body };
      items = [...items, created];
      return HttpResponse.json(created, { status: 201 });
    }),
    http.put("/api/v1/age-ranges/:id", async ({ request, params }) => {
      const body = (await request.json()) as Omit<AgeRange, "id">;
      writes.push({ method: "PUT", body });
      const updated = { id: params.id as string, ...body };
      items = items.map((r) => (r.id === params.id ? updated : r));
      return HttpResponse.json(updated);
    }),
    http.delete("/api/v1/age-ranges/:id", ({ params }) => {
      writes.push({ method: "DELETE", body: params.id });
      if (params.id === "r3") return HttpResponse.json({ code: "INTERNAL_ERROR" }, { status: 500 });
      items = items.filter((r) => r.id !== params.id);
      return new HttpResponse(null, { status: 204 });
    }),
  );
  return render(
    <ThemeProvider>
      <MemoryRouter initialEntries={["/configuracoes/faixas-etarias"]}>
        <SessionProvider>
          <App />
        </SessionProvider>
      </MemoryRouter>
    </ThemeProvider>,
  );
}

const ready = () => screen.findByRole("heading", { name: "Faixas etárias", level: 1 });
const rows = () => within(screen.getByRole("heading", { name: "Faixas da organização" }).closest("section")!).getAllByRole("listitem").map((li) => li.textContent);

describe("AgeRangesPage", () => {
  it("CA9: lista nome e idades em texto, ordenadas, com Nova faixa para o Coordenador", async () => {
    setup("COORDINATOR");
    await ready();
    expect(rows()[0]).toContain("Infantil · 4 a 12 anos");
    expect(rows()[1]).toContain("Juvenil · 13 a 17 anos");
    expect(rows()[2]).toContain("Adulto · 18 anos ou mais");
    expect(screen.getByRole("button", { name: "Nova faixa" })).toBeInTheDocument();
  });

  it("CA9: o Administrador vê a lista sem botões de escrita", async () => {
    setup("ADMINISTRATOR");
    await ready();
    expect(rows()).toHaveLength(3);
    expect(screen.queryByRole("button", { name: /Nova faixa|Editar|Apagar/ })).toBeNull();
  });

  it("CA9: cria uma faixa sem máximo; a lista reflete sem recarregar; nome repetido mostra o erro no campo", async () => {
    const user = userEvent.setup();
    setup("COORDINATOR");
    await ready();
    await user.click(screen.getByRole("button", { name: "Nova faixa" }));
    const dialog = await screen.findByRole("dialog");
    await user.click(within(dialog).getByRole("button", { name: "Salvar" }));
    expect(within(dialog).getAllByRole("alert").length).toBeGreaterThanOrEqual(2);
    expect(writes).toEqual([]);
    await user.type(within(dialog).getByLabelText("Nome da faixa"), "Juvenil");
    await user.type(within(dialog).getByLabelText("Idade mínima"), "13");
    await user.click(within(dialog).getByRole("button", { name: "Salvar" }));
    expect(await within(dialog).findByText("Já existe uma faixa com este nome.")).toBeInTheDocument();
    const name = within(dialog).getByLabelText("Nome da faixa");
    await user.clear(name);
    await user.type(name, "Master");
    await user.clear(within(dialog).getByLabelText("Idade mínima"));
    await user.type(within(dialog).getByLabelText("Idade mínima"), "35");
    await user.click(within(dialog).getByRole("button", { name: "Salvar" }));
    await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull());
    expect(writes.at(-1)).toEqual({ method: "POST", body: { name: "Master", minAge: 35, maxAge: null } });
    expect(rows().at(-1)).toContain("Master · 35 anos ou mais");
    expect(screen.getByText("Faixa etária salva")).toBeInTheDocument();
  });

  it("CA9: máxima menor que a mínima mostra o erro no campo sem enviar", async () => {
    const user = userEvent.setup();
    setup("COORDINATOR");
    await ready();
    await user.click(screen.getByRole("button", { name: "Nova faixa" }));
    const dialog = await screen.findByRole("dialog");
    await user.type(within(dialog).getByLabelText("Nome da faixa"), "Teste");
    await user.type(within(dialog).getByLabelText("Idade mínima"), "20");
    await user.type(within(dialog).getByLabelText("Idade máxima (opcional)"), "10");
    await user.click(within(dialog).getByRole("button", { name: "Salvar" }));
    expect(within(dialog).getByRole("alert")).toHaveTextContent("da mínima até 120");
    expect(writes).toEqual([]);
  });

  it("CA9: edita nome e idades e a lista passa a mostrar os novos valores", async () => {
    const user = userEvent.setup();
    setup("COORDINATOR");
    await ready();
    await user.click(screen.getByRole("button", { name: /Editars*Juvenil/ }));
    const dialog = await screen.findByRole("dialog", { name: "Editar faixa etária" });
    expect(within(dialog).getByLabelText("Idade máxima (opcional)")).toHaveValue("17");
    const name = within(dialog).getByLabelText("Nome da faixa");
    await user.clear(name);
    await user.type(name, "Teen");
    await user.clear(within(dialog).getByLabelText("Idade máxima (opcional)"));
    await user.click(within(dialog).getByRole("button", { name: "Salvar" }));
    await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull());
    expect(writes.at(-1)).toEqual({ method: "PUT", body: { name: "Teen", minAge: 13, maxAge: null } });
    expect(rows()[1]).toContain("Teen · 13 anos ou mais");
  });

  it("CA9: apagar pede confirmação, remove da lista e cancelar não apaga; falha avisa e mantém a faixa", async () => {
    const user = userEvent.setup();
    setup("COORDINATOR");
    await ready();
    await user.click(screen.getByRole("button", { name: /Apagars*Juvenil/ }));
    await user.click(within(await screen.findByRole("dialog")).getByRole("button", { name: "Cancelar" }));
    expect(writes).toEqual([]);
    await user.click(screen.getByRole("button", { name: /Apagars*Juvenil/ }));
    await user.click(within(await screen.findByRole("dialog")).getByRole("button", { name: "Apagar" }));
    await waitFor(() => expect(rows()).toHaveLength(2));
    expect(screen.getByText("Faixa Juvenil apagada")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: /Apagars*Adulto/ }));
    await user.click(within(await screen.findByRole("dialog")).getByRole("button", { name: "Apagar" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Não foi possível apagar Adulto");
    expect(rows()).toHaveLength(2);
  });

  it("CA9: lista vazia mostra o estado vazio com botão de criar para quem pode, e sem ele para o Administrador", async () => {
    setup("COORDINATOR", { initial: [] });
    expect(await screen.findByRole("heading", { name: "Nenhuma faixa etária" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Nova faixa" })).toBeInTheDocument();
  });

  it("CA9: vazio para o Administrador não tem botão de criar", async () => {
    setup("ADMINISTRATOR", { initial: [] });
    expect(await screen.findByRole("heading", { name: "Nenhuma faixa etária" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Nova faixa" })).toBeNull();
  });

  it("CA9: carregando, erro com nova tentativa e sem permissão", async () => {
    const user = userEvent.setup();
    let calls = 0;
    setup("COORDINATOR", {
      list: () => (++calls === 1 ? HttpResponse.json({ code: "INTERNAL_ERROR" }, { status: 500 }) : HttpResponse.json({ canManage: true, items: defaults() })),
    });
    expect(screen.getByRole("status")).toHaveTextContent("Carregando");
    await user.click(await screen.findByRole("button", { name: "Tentar de novo" }));
    await ready();
  });

  it("CA9: o 403 do servidor mostra o estado sem permissão", async () => {
    setup("COORDINATOR", { list: () => HttpResponse.json({ code: "FORBIDDEN" }, { status: 403 }) });
    expect(await screen.findByRole("heading", { name: "Você não tem acesso" })).toBeInTheDocument();
  });

  it("CA10: o item aparece no menu de Configurações para Coordenador e Administrador e não para Professor", async () => {
    for (const [role, visible] of [["COORDINATOR", true], ["ADMINISTRATOR", true], ["TEACHER", false]] as const) {
      const { unmount } = setup(role);
      if (visible) {
        await ready();
        expect(screen.getAllByRole("link", { name: "Faixas etárias" })[0]).toHaveAttribute("href", "/configuracoes/faixas-etarias");
      } else {
        await screen.findByRole("heading", { level: 1 });
        expect(screen.queryByRole("link", { name: "Faixas etárias" })).toBeNull();
      }
      unmount();
    }
  });

  it.each([
    ["claro", false],
    ["escuro", true],
  ])("CA9: a 360 px não tem violações no axe no tema %s, com a lista e com a janela aberta", async (_n, dark) => {
    const user = userEvent.setup();
    setDevicePrefersDark(dark);
    window.innerWidth = 360;
    const { container } = setup("COORDINATOR");
    await ready();
    expect(await axeViolations(container)).toEqual([]);
    await user.click(screen.getByRole("button", { name: "Nova faixa" }));
    await screen.findByRole("dialog");
    expect(await axeViolations(document.body)).toEqual([]);
  });
});
