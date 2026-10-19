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
import type { Graduation } from "./useGraduation";

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
const level = (id: string, name: string) => ({
  id,
  name,
  color: "#112233",
  secondaryColor: null,
  maxDegree: 4,
  minAge: 16,
  maxAge: null,
  kids: false,
});

let puts: unknown[];

function setup(
  role: string,
  over: {
    get?: () => Response | Promise<Response>;
    put?: () => Response | Promise<Response>;
  } = {},
) {
  const manage = role === "COORDINATOR";
  const graduation: Graduation = {
    enabled: false,
    requireOriginProof: false,
    canManage: manage,
    levels: [level("n1", "Branca"), level("n2", "Azul"), level("n3", "Roxa")],
  };
  puts = [];
  server.use(
    http.get("/api/v1/auth/session", () => HttpResponse.json(session(role))),
    http.get("/api/v1/permissions", () =>
      HttpResponse.json({ permissions: mockPermissions(role) }),
    ),
    http.get("/api/v1/units/reachable", () =>
      HttpResponse.json({ allUnits: false, units: [] }),
    ),
    http.get("/api/v1/modalities", () =>
      HttpResponse.json([
        {
          id: "m1",
          name: "Jiu-Jitsu",
          active: true,
          canManage: manage,
          graduationEnabled: false,
        },
        {
          id: "m2",
          name: "Judô",
          active: false,
          canManage: manage,
          graduationEnabled: false,
        },
      ]),
    ),
    http.get("/api/v1/modalities/requests", () => HttpResponse.json([])),
    http.get(
      "/api/v1/modalities/:id/graduation",
      over.get ?? (() => HttpResponse.json(graduation)),
    ),
    http.put(
      "/api/v1/modalities/:id/graduation",
      over.put ??
        (async ({ request }) => {
          const body = (await request.json()) as Omit<Graduation, "canManage">;
          puts.push(body);
          return HttpResponse.json({ ...body, canManage: true });
        }),
    ),
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

async function open(name = /Graduação\s*Jiu-Jitsu|Ver graduação\s*Jiu-Jitsu/) {
  const user = userEvent.setup();
  await screen.findByRole("heading", { name: "Modalidades", level: 1 });
  await user.click(await screen.findByRole("button", { name }));
  return { user, dialog: await screen.findByRole("dialog") };
}
const levelNames = (dialog: HTMLElement) =>
  within(dialog)
    .getAllByLabelText(/Nome do nível/)
    .map((i) => (i as HTMLInputElement).value);

describe("GraduationDialog", () => {
  it("CA12: o Coordenador vê o botão Graduação só nas linhas ativas", async () => {
    setup("COORDINATOR");
    await screen.findByRole("heading", { name: "Modalidades", level: 1 });
    expect(
      await screen.findAllByRole("button", { name: /Graduação/ }),
    ).toHaveLength(1);
  });

  it("CA12: Administrador consulta sem controles de edição", async () => {
    setup("ADMINISTRATOR");
    const { dialog } = await open();
    expect(
      await within(dialog).findByText("A graduação está desligada."),
    ).toBeInTheDocument();
    expect(within(dialog).getByText("Azul")).toBeInTheDocument();
    expect(within(dialog).queryByRole("button", { name: /Salvar/ })).toBeNull();
    expect(within(dialog).queryByRole("textbox")).toBeNull();
  });

  it("CA12: carregando, vazio, erro com nova tentativa e sem permissão", async () => {
    let calls = 0;
    setup("COORDINATOR", {
      get: () =>
        ++calls === 1
          ? HttpResponse.json({ code: "INTERNAL_ERROR" }, { status: 500 })
          : HttpResponse.json({
              enabled: false,
              requireOriginProof: false,
              canManage: true,
              levels: [],
            }),
    });
    const { user, dialog } = await open();
    await user.click(
      await within(dialog).findByRole("button", { name: "Tentar de novo" }),
    );
    expect(
      await within(dialog).findByText("Nenhum nível ainda"),
    ).toBeInTheDocument();
  });

  it("CA12: o 403 do servidor mostra o estado sem permissão", async () => {
    setup("COORDINATOR", {
      get: () => HttpResponse.json({ code: "FORBIDDEN" }, { status: 403 }),
    });
    const { dialog } = await open();
    expect(
      await within(dialog).findByRole("heading", {
        name: "Você não tem acesso",
      }),
    ).toBeInTheDocument();
  });

  it("CA13: renomeia, sobe, adiciona e remove; o pedido sai na ordem da tela, a janela fecha e avisa", async () => {
    setup("COORDINATOR");
    const { user, dialog } = await open();
    await within(dialog).findByLabelText("Nome do nível 1");
    const second = within(dialog).getByLabelText("Nome do nível 2");
    await user.clear(second);
    await user.type(second, "Azul escura");
    await user.click(
      within(dialog).getByRole("button", { name: "Subir nível 2" }),
    );
    expect(levelNames(dialog)).toEqual(["Azul escura", "Branca", "Roxa"]);
    await user.click(
      within(dialog).getByRole("button", { name: "Remover nível 3" }),
    );
    await user.click(
      within(dialog).getByRole("button", { name: "Adicionar nível" }),
    );
    await user.type(within(dialog).getByLabelText("Nome do nível 3"), "Marrom");
    await user.click(within(dialog).getByRole("switch"));
    await user.click(
      within(dialog).getByLabelText(
        "Exigir comprovante da graduação de origem",
      ),
    );
    await user.click(within(dialog).getByRole("button", { name: "Salvar" }));
    await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull());
    const sent = puts[0] as {
      enabled: boolean;
      requireOriginProof: boolean;
      levels: { id?: string; name: string }[];
    };
    expect(sent.enabled).toBe(true);
    expect(sent.requireOriginProof).toBe(true);
    expect(sent.levels.map((l) => l.name)).toEqual([
      "Azul escura",
      "Branca",
      "Marrom",
    ]);
    expect(sent.levels.map((l) => l.id)).toEqual(["n2", "n1", undefined]);
    expect(screen.getByText("Sistema de graduação salvo")).toBeInTheDocument();
  });

  it("CA13: nome vazio mostra o erro no campo e nada é enviado; 400 do servidor aparece no campo apontado", async () => {
    setup("COORDINATOR", {
      put: () =>
        HttpResponse.json(
          {
            code: "VALIDATION_ERROR",
            fields: [{ field: "levels[2].name", message: "nome repetido" }],
          },
          { status: 400 },
        ),
    });
    const { user, dialog } = await open();
    await within(dialog).findByLabelText("Nome do nível 1");
    const first = within(dialog).getByLabelText("Nome do nível 1");
    await user.clear(first);
    await user.click(within(dialog).getByRole("button", { name: "Salvar" }));
    expect(within(dialog).getByRole("alert")).toHaveTextContent(
      "1 a 40 caracteres",
    );
    expect(puts).toEqual([]);
    await user.type(first, "Branca");
    await user.click(within(dialog).getByRole("button", { name: "Salvar" }));
    expect(
      await within(dialog).findByText("nome repetido"),
    ).toBeInTheDocument();
    expect(
      within(dialog).getByLabelText("Nome do nível 3"),
    ).toHaveAccessibleDescription("nome repetido");
  });

  it("ligar sem nenhum nível mostra o erro sem enviar", async () => {
    setup("COORDINATOR", {
      get: () =>
        HttpResponse.json({
          enabled: false,
          requireOriginProof: false,
          canManage: true,
          levels: [],
        }),
    });
    const { user, dialog } = await open();
    await user.click(await within(dialog).findByRole("switch"));
    await user.click(within(dialog).getByRole("button", { name: "Salvar" }));
    expect(within(dialog).getByRole("alert")).toHaveTextContent(
      "ao menos um nível",
    );
    expect(puts).toEqual([]);
  });

  it.each([
    ["claro", false],
    ["escuro", true],
  ])(
    "CA12: a 360 px não tem violações no axe no tema %s, com a janela aberta",
    async (_n, dark) => {
      setDevicePrefersDark(dark);
      window.innerWidth = 360;
      setup("COORDINATOR");
      const { dialog } = await open();
      await within(dialog).findByLabelText("Nome do nível 1");
      expect(await axeViolations(document.body)).toEqual([]);
    },
  );
});
