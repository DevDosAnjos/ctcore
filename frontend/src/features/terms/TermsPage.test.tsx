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
import type { TermsVersion, TermsView } from "./useTerms";

const session = (role: string): SessionView => ({
  account: { id: "a", email: "x@x.com", name: "X" },
  links: [{ id: "l1", organizationId: "o", organizationName: "Alfa", role, unitIds: [] }],
  activeLinkId: "l1",
  twoFactor: { enabled: false, required: false },
});
const MODEL: TermsVersion = { version: 0, body: "Texto padrão da plataforma para o termo.", reaccept: false, publishedAt: null, publishedBy: null };
const v1: TermsVersion = { version: 1, body: "Primeira versão publicada do termo.", reaccept: false, publishedAt: "2026-03-01T12:00:00Z", publishedBy: "Ana" };

let versions: TermsVersion[];
let posts: { kind: string; body: { body: string; reaccept: boolean } }[];

const view = (role: string): TermsView => ({ canManage: role === "COORDINATOR", current: versions[0] ?? MODEL, versions });

function setup(role: string, over: { get?: () => Response | Promise<Response>; post?: (n: number) => Response | undefined; initial?: TermsVersion[] } = {}) {
  versions = over.initial ?? [];
  posts = [];
  server.use(
    http.get("/api/v1/auth/session", () => HttpResponse.json(session(role))),
    http.get("/api/v1/permissions", () => HttpResponse.json({ permissions: mockPermissions(role) })),
    http.get("/api/v1/units/reachable", () => HttpResponse.json({ allUnits: false, units: [] })),
    http.get("/api/v1/organization/terms/:kind", over.get ?? (() => HttpResponse.json(view(role)))),
    http.post("/api/v1/organization/terms/:kind/versions", async ({ request, params }) => {
      const body = (await request.json()) as { body: string; reaccept: boolean };
      posts.push({ kind: params.kind as string, body });
      const failure = over.post?.(posts.length);
      if (failure) return failure;
      const created = { version: (versions[0]?.version ?? 0) + 1, body: body.body, reaccept: body.reaccept, publishedAt: "2026-04-01T12:00:00Z", publishedBy: "X" };
      versions = [created, ...versions];
      return HttpResponse.json(created, { status: 201 });
    }),
  );
  return render(
    <ThemeProvider>
      <MemoryRouter initialEntries={["/configuracoes/termos"]}>
        <SessionProvider>
          <App />
        </SessionProvider>
      </MemoryRouter>
    </ThemeProvider>,
  );
}

const ready = () => screen.findByRole("heading", { name: "Termos", level: 1 });
const editor = () => screen.findByLabelText("Texto do termo");
const publishButton = () => screen.getByRole("button", { name: "Publicar nova versão" });

describe("TermsPage", () => {
  it("CA9: sem versão publicada mostra o histórico vazio e o texto padrão no editor; publicar começa desabilitado", async () => {
    setup("COORDINATOR");
    await ready();
    expect(await editor()).toHaveValue(MODEL.body);
    expect(screen.getByRole("heading", { name: "Nenhuma versão publicada ainda" })).toBeInTheDocument();
    expect(screen.getByLabelText("Só para novos alunos")).not.toBeChecked();
    expect(screen.getByLabelText("Todos aceitam de novo no próximo acesso")).not.toBeChecked();
    expect(publishButton()).toBeDisabled();
  });

  it("CA9: publica com a escolha de reaceite; a atual e o histórico se atualizam sem recarregar; o botão volta a desabilitar", async () => {
    const user = userEvent.setup();
    setup("COORDINATOR", { initial: [v1] });
    const box = await editor();
    expect(box).toHaveValue(v1.body);
    expect(publishButton()).toBeDisabled();
    await user.clear(box);
    await user.type(box, "Segunda versão do termo, com mais texto.");
    expect(publishButton()).toBeDisabled();
    await user.click(screen.getByLabelText("Todos aceitam de novo no próximo acesso"));
    expect(publishButton()).toBeEnabled();
    await user.click(publishButton());
    expect(await screen.findByText("Nova versão publicada.")).toBeInTheDocument();
    expect(posts).toEqual([{ kind: "ENROLLMENT", body: { body: "Segunda versão do termo, com mais texto.", reaccept: true } }]);
    expect(screen.getByText(/Versão atual: 2/)).toBeInTheDocument();
    expect(screen.getAllByText("Versão 2").length).toBeGreaterThan(0);
    expect(screen.getAllByText("Versão 1").length).toBeGreaterThan(0);
    expect(publishButton()).toBeDisabled();
  });

  it("CA9: a aba do termo de uso de imagem lê e publica o outro tipo", async () => {
    const user = userEvent.setup();
    setup("COORDINATOR");
    await ready();
    await user.click(screen.getByRole("button", { name: "Termo de uso de imagem" }));
    const box = await editor();
    await user.clear(box);
    await user.type(box, "Texto novo do termo de imagem.");
    await user.click(screen.getByLabelText("Só para novos alunos"));
    await user.click(publishButton());
    await screen.findByText("Nova versão publicada.");
    expect(posts[0].kind).toBe("IMAGE_USE");
  });

  it("CA9: erro 400 e 409 do servidor aparecem na tela", async () => {
    const user = userEvent.setup();
    setup("COORDINATOR", {
      post: (n) => HttpResponse.json({ code: n === 1 ? "VALIDATION_ERROR" : "TERMS_VERSION_CONFLICT", fields: [] }, { status: n === 1 ? 400 : 409 }),
    });
    const box = await editor();
    await user.clear(box);
    await user.type(box, "Texto novo com mais de vinte letras.");
    await user.click(screen.getByLabelText("Só para novos alunos"));
    await user.click(publishButton());
    expect(await screen.findByRole("alert")).toHaveTextContent("de 20 a 20000 caracteres");
    await user.click(publishButton());
    await waitFor(() => expect(screen.getByRole("alert")).toHaveTextContent("Outra pessoa publicou"));
    expect(versions).toEqual([]);
  });

  it("CA9: o Administrador vê o texto e o histórico, com a leitura de cada versão, sem editor nem botão", async () => {
    const user = userEvent.setup();
    setup("ADMINISTRATOR", { initial: [v1] });
    await ready();
    expect((await screen.findAllByText("Versão 1")).length).toBeGreaterThan(0);
    expect(screen.queryByLabelText("Texto do termo")).toBeNull();
    expect(screen.queryByRole("button", { name: "Publicar nova versão" })).toBeNull();
    await user.click(screen.getByText("Ler o texto"));
    expect(screen.getAllByText(v1.body).length).toBeGreaterThanOrEqual(1);
  });

  it("CA9: carregando, erro com nova tentativa e sem permissão", async () => {
    const user = userEvent.setup();
    let calls = 0;
    setup("COORDINATOR", { get: () => (++calls === 1 ? HttpResponse.json({ code: "INTERNAL_ERROR" }, { status: 500 }) : HttpResponse.json(view("COORDINATOR"))) });
    expect(screen.getByRole("status")).toHaveTextContent("Carregando");
    await user.click(await screen.findByRole("button", { name: "Tentar de novo" }));
    expect(await editor()).toBeInTheDocument();
  });

  it("CA9: o 403 do servidor mostra o estado sem permissão", async () => {
    setup("COORDINATOR", { get: () => HttpResponse.json({ code: "FORBIDDEN" }, { status: 403 }) });
    expect(await screen.findByRole("heading", { name: "Você não tem acesso" })).toBeInTheDocument();
  });

  it("CA10: o item aparece no menu de Configurações para Coordenador e Administrador e não para Professor", async () => {
    for (const [role, visible] of [["COORDINATOR", true], ["ADMINISTRATOR", true], ["TEACHER", false]] as const) {
      const { unmount } = setup(role);
      if (visible) {
        await ready();
        expect(screen.getAllByRole("link", { name: "Termos" })[0]).toHaveAttribute("href", "/configuracoes/termos");
      } else {
        await screen.findByRole("heading", { level: 1 });
        expect(screen.queryByRole("link", { name: "Termos" })).toBeNull();
      }
      unmount();
    }
  });

  it.each([
    ["claro", false],
    ["escuro", true],
  ])("CA9: a 360 px não tem violações no axe no tema %s, para quem edita e para quem consulta", async (_n, dark) => {
    setDevicePrefersDark(dark);
    window.innerWidth = 360;
    const first = setup("COORDINATOR", { initial: [v1] });
    await editor();
    expect(await axeViolations(first.container)).toEqual([]);
    first.unmount();
    const second = setup("ADMINISTRATOR", { initial: [v1] });
    await screen.findAllByText("Versão 1");
    expect(await axeViolations(second.container)).toEqual([]);
  });
});
