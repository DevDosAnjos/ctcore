import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { MemoryRouter } from "react-router";
import { App } from "@/App";
import {
  SessionProvider,
  type SessionView,
} from "@/features/auth/SessionProvider";
import { mockPermissions } from "@/mocks/permissions";
import { server } from "@/mocks/server";
import { axeViolations } from "@/test/axe";
import { setDevicePrefersDark } from "@/test/device";
import { ThemeProvider } from "@/theme/ThemeProvider";
import type { Modality, ModalityRequest } from "./useModalities";

const session = (role: string): SessionView => ({
  account: { id: "a", email: "x@x.com", name: "X" },
  links: [
    {
      id: "l1",
      organizationId: "o",
      organizationName: "Alfa",
      role,
      unitIds: [],
    },
  ],
  activeLinkId: "l1",
  twoFactor: { enabled: false, required: false },
});
const modality = (
  id: string,
  name: string,
  active: boolean,
  canManage = true,
): Modality => ({ id, name, active, canManage, graduationEnabled: false });

let items: Modality[];
let requests: ModalityRequest[];
let posts: unknown[];

function setup(
  role: string,
  over: { list?: () => Response | Promise<Response> } = {},
) {
  const manage = role === "COORDINATOR";
  items = [
    modality("m1", "Boxe", true, manage),
    modality("m2", "Judô", false, manage),
    modality("m3", "Jiu-Jitsu", false, manage),
  ];
  requests = [];
  posts = [];
  server.use(
    http.get("/api/v1/auth/session", () => HttpResponse.json(session(role))),
    http.get("/api/v1/permissions", () =>
      HttpResponse.json({ permissions: mockPermissions(role) }),
    ),
    http.get("/api/v1/units/reachable", () =>
      HttpResponse.json({ allUnits: false, units: [] }),
    ),
    http.get(
      "/api/v1/modalities",
      over.list ?? (() => HttpResponse.json(items)),
    ),
    http.get("/api/v1/modalities/requests", () => HttpResponse.json(requests)),
    http.put(
      "/api/v1/modalities/:id/activation",
      async ({ request, params }) => {
        const { active } = (await request.json()) as { active: boolean };
        if (params.id === "m1" && !active) {
          return HttpResponse.json(
            {
              code: "MODALITY_IN_USE",
              fields: [
                { field: "activeClasses", message: "2" },
                { field: "activeStudents", message: "3" },
              ],
            },
            { status: 409 },
          );
        }
        items = items.map((m) => (m.id === params.id ? { ...m, active } : m));
        return HttpResponse.json(items.find((m) => m.id === params.id));
      },
    ),
    http.post("/api/v1/modalities/requests", async ({ request }) => {
      const body = (await request.json()) as {
        name: string;
        note: string | null;
      };
      posts.push(body);
      if (body.name === "Judo")
        return HttpResponse.json(
          { code: "ALREADY_IN_CATALOG", fields: [] },
          { status: 409 },
        );
      const created: ModalityRequest = {
        id: "r1",
        name: body.name,
        note: body.note,
        status: "OPEN",
        createdAt: "2026-10-19T12:00:00Z",
      };
      requests = [created, ...requests];
      return HttpResponse.json(created, { status: 201 });
    }),
  );
  return render(
    <ThemeProvider>
      <MemoryRouter initialEntries={["/configuracoes/modalidades"]}>
        <SessionProvider>
          <App />
        </SessionProvider>
      </MemoryRouter>
    </ThemeProvider>,
  );
}

const ready = () =>
  screen.findByRole("heading", { name: "Modalidades", level: 1 });
const names = (title: string) => within(block(title)).queryAllByRole("listitem").map((li) => li.firstElementChild!.textContent);
const block = (name: string) =>
  screen.getByRole("heading", { name }).closest("section")!;

describe("ModalitiesPage", () => {
  it("CA11: o Coordenador vê os dois blocos com Ativar/Desativar e o botão de pedido; ativar move a modalidade", async () => {
    const user = userEvent.setup();
    setup("COORDINATOR");
    await ready();
    expect(names("Suas modalidades")).toEqual(["Boxe"]);
    expect(names("Disponíveis para ativar")).toEqual(["Judô", "Jiu-Jitsu"]);
    expect(
      screen.getByRole("button", { name: "Não achei minha modalidade" }),
    ).toBeInTheDocument();
    await user.click(
      within(block("Disponíveis para ativar")).getByRole("button", {
        name: /Ativars*Judô/,
      }),
    );
    await waitFor(() => expect(names("Suas modalidades")).toEqual(["Boxe", "Judô"]));
    expect(names("Disponíveis para ativar")).toEqual(["Jiu-Jitsu"]);
  });

  it("CA11: o Administrador vê as listas sem os controles", async () => {
    setup("ADMINISTRATOR");
    await ready();
    expect(names("Suas modalidades")).toEqual(["Boxe"]);
    expect(
      screen.queryByRole("button", { name: /Ativar|Desativar|Não achei/ }),
    ).toBeNull();
  });

  it("CA11: 'Suas modalidades' vazio orienta a ativar; carregando, erro com nova tentativa e sem permissão", async () => {
    const user = userEvent.setup();
    let calls = 0;
    setup("COORDINATOR", {
      list: () =>
        ++calls === 1
          ? HttpResponse.json({ code: "INTERNAL_ERROR" }, { status: 500 })
          : HttpResponse.json([modality("m2", "Judô", false)]),
    });
    expect(screen.getByRole("status")).toHaveTextContent("Carregando");
    await user.click(
      await screen.findByRole("button", { name: "Tentar de novo" }),
    );
    await ready();
    expect(
      within(block("Suas modalidades")).getByText(/Ative uma da lista/),
    ).toBeInTheDocument();
  });

  it("CA11: catálogo sem itens mostra o estado vazio", async () => {
    setup("COORDINATOR", { list: () => HttpResponse.json([]) });
    expect(
      await screen.findByRole("heading", {
        name: "Nenhuma modalidade no catálogo",
      }),
    ).toBeInTheDocument();
  });

  it("CA11: o 403 do servidor mostra o estado sem permissão", async () => {
    setup("COORDINATOR", {
      list: () => HttpResponse.json({ code: "FORBIDDEN" }, { status: 403 }),
    });
    expect(
      await screen.findByRole("heading", { name: "Você não tem acesso" }),
    ).toBeInTheDocument();
  });

  it("CA13: MODALITY_IN_USE explica quantas turmas e alunos impedem e a modalidade permanece ativa", async () => {
    const user = userEvent.setup();
    setup("COORDINATOR");
    await ready();
    await user.click(
      within(block("Suas modalidades")).getByRole("button", {
        name: /Desativars*Boxe/,
      }),
    );
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "2 turma(s) ativa(s) e 3 aluno(s) ativo(s)",
    );
    expect(names("Suas modalidades")).toEqual(["Boxe"]);
  });

  it("CA12: nome curto mostra o erro no campo sem enviar; dados válidos fecham, avisam e listam o pedido; 409 aparece no campo", async () => {
    const user = userEvent.setup();
    setup("COORDINATOR");
    await ready();
    await user.click(
      screen.getByRole("button", { name: "Não achei minha modalidade" }),
    );
    const dialog = await screen.findByRole("dialog");
    await user.type(within(dialog).getByLabelText("Nome da modalidade"), "a");
    await user.click(
      within(dialog).getByRole("button", { name: "Enviar pedido" }),
    );
    expect(within(dialog).getByRole("alert")).toHaveTextContent(
      "2 a 60 caracteres",
    );
    expect(posts).toEqual([]);
    const name = within(dialog).getByLabelText("Nome da modalidade");
    await user.clear(name);
    await user.type(name, "Judo");
    await user.click(
      within(dialog).getByRole("button", { name: "Enviar pedido" }),
    );
    expect(
      await within(dialog).findByText(/já existe no catálogo/),
    ).toBeInTheDocument();
    await user.clear(name);
    await user.type(name, "Sambo");
    await user.type(
      within(dialog).getByLabelText("Observação (opcional)"),
      "Temos turma",
    );
    await user.click(
      within(dialog).getByRole("button", { name: "Enviar pedido" }),
    );
    await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull());
    expect(posts.at(-1)).toEqual({ name: "Sambo", note: "Temos turma" });
    expect(screen.getByText("Pedido enviado")).toBeInTheDocument();
    expect(
      within(block("Pedidos enviados")).getByText("Sambo"),
    ).toBeInTheDocument();
  });

  it.each([
    ["claro", false],
    ["escuro", true],
  ])(
    "CA11: a 360 px não tem violações no axe no tema %s, com a lista e com a janela aberta",
    async (_n, dark) => {
      const user = userEvent.setup();
      setDevicePrefersDark(dark);
      window.innerWidth = 360;
      const { container } = setup("COORDINATOR");
      await ready();
      expect(await axeViolations(container)).toEqual([]);
      await user.click(
        screen.getByRole("button", { name: "Não achei minha modalidade" }),
      );
      await screen.findByRole("dialog");
      expect(await axeViolations(document.body)).toEqual([]);
    },
  );
});
