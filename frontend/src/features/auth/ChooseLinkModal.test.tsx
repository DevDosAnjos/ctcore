import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { MemoryRouter } from "react-router";
import { App } from "@/App";
import { mockPermissions } from "@/mocks/permissions";
import { server } from "@/mocks/server";
import { axeViolations } from "@/test/axe";
import { setDevicePrefersDark } from "@/test/device";
import { ThemeProvider } from "@/theme/ThemeProvider";
import { SessionProvider, type SessionView } from "./SessionProvider";

const L = (id: string, org: string, role: string) => ({
  id,
  organizationId: `o-${org}`,
  organizationName: org,
  role,
  unitIds: [],
});
const links = [
  L("l1", "Academia Alfa", "ADMINISTRATOR"),
  L("l2", "Academia Alfa", "STUDENT"),
  L("l3", "Academia Beta", "GUARDIAN"),
  L("l4", "Academia Beta", "TEACHER"),
  L("l5", "Academia Beta", "COORDINATOR"),
];
const account = { id: "a", email: "x@x.com", name: "X" };
const off = { enabled: false, required: false };
let view: SessionView;
let puts: unknown[];

function setup(
  initial: Partial<SessionView> = {},
  put?: () => Response | Promise<Response>,
) {
  view = { account, links, activeLinkId: null, twoFactor: off, ...initial };
  puts = [];
  server.use(
    http.get("/api/v1/auth/session", () => HttpResponse.json(view)),
    http.get("/api/v1/permissions", () =>
      HttpResponse.json({
        permissions: mockPermissions(
          view.links.find((l) => l.id === view.activeLinkId)?.role,
        ),
      }),
    ),
    http.put("/api/v1/auth/session/link", async ({ request }) => {
      const body = (await request.json()) as { linkId: string };
      puts.push(body);
      if (put) return put();
      view = { ...view, activeLinkId: body.linkId };
      return HttpResponse.json(view);
    }),
  );
  return render(
    <ThemeProvider>
      <MemoryRouter initialEntries={["/"]}>
        <SessionProvider>
          <App />
        </SessionProvider>
      </MemoryRouter>
    </ThemeProvider>,
  );
}

const err = (status: number) =>
  HttpResponse.json(
    { code: "X", message: "m", changed: false, fields: [], traceId: "t" },
    { status },
  );
const TITLE = "Escolher organização e área";

describe("ChooseLink", () => {
  it("CA1, CA4: abre sozinho, uma opção por vínculo com a área do papel, e não fecha por Esc nem clique fora", async () => {
    const user = userEvent.setup();
    setup();
    const dlg = await screen.findByRole("dialog", { name: TITLE });
    expect(screen.getAllByRole("listitem")).toHaveLength(5);
    for (const name of [
      /Alfa Gestão \(Administrador\)/,
      /Alfa Portal \(Aluno\)/,
      /Beta Portal \(Responsável\)/,
      /Beta Gestão \(Professor\)/,
      /Beta Gestão \(Coordenador\)/,
    ]) {
      expect(screen.getByRole("button", { name })).toBeInTheDocument();
    }
    expect(
      screen.queryByRole("button", { name: "Fechar" }),
    ).not.toBeInTheDocument();
    await waitFor(() =>
      expect(dlg).toContainElement(document.activeElement as HTMLElement),
    );
    await user.keyboard("{Escape}");
    fireEvent.pointerDown(document.body);
    fireEvent.click(document.body);
    expect(screen.getByRole("dialog", { name: TITLE })).toBeInTheDocument();
    expect(puts).toEqual([]);
  });

  it("CA2: com um vínculo o modal não abre e não há gatilho", async () => {
    setup({ links: [links[0]], activeLinkId: "l1" });
    await screen.findByRole("heading", { name: "Bem-vindo ao ctcore" });
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "Trocar organização ou área" }),
    ).not.toBeInTheDocument();
  });

  it("CA3: escolher chama o PUT, atualiza a sessão, fecha e fica na página", async () => {
    const user = userEvent.setup();
    setup();
    await user.click(
      await screen.findByRole("button", {
        name: /Alfa Gestão \(Administrador\)/,
      }),
    );
    await waitFor(() =>
      expect(screen.queryByRole("dialog")).not.toBeInTheDocument(),
    );
    expect(puts).toEqual([{ linkId: "l1" }]);
    expect(
      screen.getByRole("heading", { name: "Bem-vindo ao ctcore" }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "Trocar organização ou área" }),
    ).toBeInTheDocument();
  });

  it("CA5: o gatilho reabre com o vínculo atual marcado; Esc fecha sem API e devolve o foco; escolher outro troca", async () => {
    const user = userEvent.setup();
    setup({ activeLinkId: "l1" });
    const trigger = await screen.findByRole("button", {
      name: "Trocar organização ou área",
    });
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    await user.click(trigger);
    expect(
      screen.getByRole("button", { name: /Alfa Gestão \(Administrador\)/ }),
    ).toHaveAttribute("aria-current", "true");
    expect(
      screen.getByRole("button", { name: /Alfa Portal/ }),
    ).not.toHaveAttribute("aria-current");
    await user.keyboard("{Escape}");
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    await waitFor(() => expect(trigger).toHaveFocus());
    expect(puts).toEqual([]);
    await user.click(trigger);
    await user.click(
      screen.getByRole("button", { name: /Beta Gestão \(Professor\)/ }),
    );
    await waitFor(() => expect(puts).toEqual([{ linkId: "l4" }]));
    await waitFor(() =>
      expect(screen.queryByRole("dialog")).not.toBeInTheDocument(),
    );
  });

  it("CA10: enquanto envia as opções ficam desabilitadas; erro 500 mantém a lista e Tentar de novo repete a escolha", async () => {
    const user = userEvent.setup();
    let fail = true;
    let release = () => {};
    const gate = new Promise<void>((r) => (release = r));
    setup({}, async () => {
      await gate;
      if (fail) return err(500);
      view = { ...view, activeLinkId: "l2" };
      return HttpResponse.json(view);
    });
    const opt = await screen.findByRole("button", { name: /Alfa Portal/ });
    await user.click(opt);
    expect(screen.getByRole("status")).toHaveTextContent(
      "Enviando a escolha...",
    );
    for (const b of screen.getAllByRole("button")) expect(b).toBeDisabled();
    await user.click(opt);
    release();
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Não conseguimos salvar a escolha",
    );
    expect(screen.getAllByRole("listitem")).toHaveLength(5);
    expect(puts).toHaveLength(1);
    fail = false;
    await user.click(screen.getByRole("button", { name: "Tentar de novo" }));
    await waitFor(() =>
      expect(screen.queryByRole("dialog")).not.toBeInTheDocument(),
    );
    expect(puts).toEqual([{ linkId: "l2" }, { linkId: "l2" }]);
  });

  it("CA10: rede fora também mostra o erro com Tentar de novo", async () => {
    const user = userEvent.setup();
    setup({}, () => HttpResponse.error());
    await user.click(
      await screen.findByRole("button", { name: /Alfa Portal/ }),
    );
    expect(
      await screen.findByRole("button", { name: "Tentar de novo" }),
    ).toBeInTheDocument();
  });

  it("CA10: 404 orienta e atualiza a lista a partir de GET /auth/session", async () => {
    const user = userEvent.setup();
    setup({}, () => {
      view = { ...view, links: links.slice(0, 2) };
      return err(404);
    });
    await user.click(
      await screen.findByRole("button", { name: /Beta Gestão \(Professor\)/ }),
    );
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Essa opção não existe mais",
    );
    await waitFor(() =>
      expect(screen.getAllByRole("listitem")).toHaveLength(2),
    );
    expect(
      screen.queryByRole("button", { name: "Tentar de novo" }),
    ).not.toBeInTheDocument();
  });

  it.each([
    ["claro", false],
    ["escuro", true],
  ])(
    "CA11: sem violações no axe a 360 px no tema %s (inicial, erro)",
    async (_n, dark) => {
      setDevicePrefersDark(dark);
      window.innerWidth = 360;
      const user = userEvent.setup();
      setup({}, () => err(500));
      await screen.findByRole("dialog", { name: TITLE });
      expect(await axeViolations(document.body)).toEqual([]);
      await user.click(screen.getByRole("button", { name: /Alfa Portal/ }));
      await screen.findByRole("alert");
      expect(await axeViolations(document.body)).toEqual([]);
    },
  );

  it.each([
    ["claro", false],
    ["escuro", true],
  ])("CA11: reaberto e enviando passam no axe no tema %s", async (_n, dark) => {
    setDevicePrefersDark(dark);
    const user = userEvent.setup();
    window.innerWidth = 360;
    setup({ activeLinkId: "l1" }, () => new Promise<Response>(() => {}));
    await user.click(
      await screen.findByRole("button", { name: "Trocar organização ou área" }),
    );
    expect(await axeViolations(document.body)).toEqual([]);
    await user.click(screen.getByRole("button", { name: /Alfa Portal/ }));
    await screen.findByRole("status");
    expect(await axeViolations(document.body)).toEqual([]);
  });

  it("RN-ORG-07 / D7 do #37: Coordenador que também é Administrador da mesma organização vê duas opções distintas e cada uma escolhe o vínculo certo", async () => {
    const user = userEvent.setup();
    setup({
      links: [
        L("c1", "Academia Alfa", "COORDINATOR"),
        L("a1", "Academia Alfa", "ADMINISTRATOR"),
      ],
      activeLinkId: "c1",
    });
    await user.click(
      await screen.findByRole("button", { name: "Trocar organização ou área" }),
    );
    const coordinator = screen.getByRole("button", {
      name: /Alfa Gestão \(Coordenador\)/,
    });
    const administrator = screen.getByRole("button", {
      name: /Alfa Gestão \(Administrador\)/,
    });
    expect(coordinator).toHaveAttribute("aria-current", "true");
    expect(administrator).not.toHaveAttribute("aria-current");
    await user.click(administrator);
    await waitFor(() => expect(puts).toEqual([{ linkId: "a1" }]));
    await user.click(
      await screen.findByRole("button", { name: "Trocar organização ou área" }),
    );
    await user.click(
      screen.getByRole("button", { name: /Alfa Gestão \(Coordenador\)/ }),
    );
    await waitFor(() =>
      expect(puts).toEqual([{ linkId: "a1" }, { linkId: "c1" }]),
    );
  });
});
