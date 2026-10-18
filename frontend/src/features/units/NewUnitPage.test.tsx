import { render, screen } from "@testing-library/react";
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
import type { NewUnit, UnitSummary } from "./useUnits";

const session = (role: string): SessionView => ({
  account: { id: "a", email: "x@x.com", name: "X" },
  links: [{ id: "l1", organizationId: "o", organizationName: "Alfa", role, unitIds: [] }],
  activeLinkId: "l1",
  twoFactor: { enabled: false, required: false },
});
const unit = (n: number): UnitSummary => ({
  id: `u${n}`,
  name: `Unidade ${n}`,
  shortName: `alfa-${n}`,
  shortNameAdjusted: false,
  city: null,
  activeStudents: 0,
  administrators: [],
  active: true,
});
const created = unit(99);

let posts: NewUnit[];

function setup(role: string, path: string, opts: { list?: UnitSummary[]; post?: () => Response } = {}) {
  posts = [];
  server.use(
    http.get("/api/v1/auth/session", () => HttpResponse.json(session(role))),
    http.get("/api/v1/permissions", () => HttpResponse.json({ permissions: mockPermissions(role) })),
    http.get("/api/v1/units", () => HttpResponse.json(opts.list ?? [unit(1)])),
    http.post("/api/v1/units", async ({ request }) => {
      posts.push((await request.json()) as NewUnit);
      return opts.post?.() ?? HttpResponse.json({ unit: created, invite: "SENT" }, { status: 201 });
    }),
  );
  return render(
    <ThemeProvider>
      <MemoryRouter initialEntries={[path]}>
        <SessionProvider>
          <App />
        </SessionProvider>
      </MemoryRouter>
    </ThemeProvider>,
  );
}

const NEW = "/configuracoes/unidades/nova";
const next = (user: ReturnType<typeof userEvent.setup>) => user.click(screen.getByRole("button", { name: "Avançar" }));
const wizard = () => screen.findByRole("heading", { name: "Nova unidade", level: 1 });

describe("NewUnitPage", () => {
  it("CA13: três etapas, só avança com a etapa válida, mostra o resumo do convite e volta à lista ao concluir", async () => {
    const user = userEvent.setup();
    setup("COORDINATOR", NEW);
    await wizard();
    expect(screen.getByRole("button", { name: "Avançar" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "Voltar" })).toBeDisabled();
    await user.type(screen.getByLabelText("Nome da unidade"), "Academia Tijuca");
    await next(user);
    await user.type(screen.getByLabelText("CEP"), "01001000");
    await user.type(screen.getByLabelText("Cidade"), "Rio de Janeiro");
    await next(user);
    await user.type(screen.getByLabelText("Nome do Administrador"), "Ana");
    expect(screen.getByRole("button", { name: "Concluir" })).toBeDisabled();
    expect(screen.getByText("Informe o nome e o e-mail juntos, ou deixe os dois vazios.")).toBeInTheDocument();
    await user.type(screen.getByLabelText("E-mail do Administrador"), "ana@exemplo.com");
    expect(screen.getByText(/Vamos enviar um convite de Administrador desta unidade para Ana \(ana@exemplo.com\)/)).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Concluir" }));
    expect(await screen.findByRole("heading", { name: "Unidades", level: 1 })).toBeInTheDocument();
    expect(posts).toEqual([{ name: "Academia Tijuca", postalCode: "01001000", city: "Rio de Janeiro", administrator: { name: "Ana", email: "ana@exemplo.com" } }]);
  });

  it("CA13: voltar mantém o que foi preenchido e CEP ou coordenadas inválidos bloqueiam o avanço", async () => {
    const user = userEvent.setup();
    setup("COORDINATOR", NEW);
    await wizard();
    await user.type(screen.getByLabelText("Nome da unidade"), "Nova");
    await next(user);
    await user.type(screen.getByLabelText("CEP"), "123");
    expect(screen.getByText("Confira o CEP: são 8 números.")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Avançar" })).toBeDisabled();
    await user.clear(screen.getByLabelText("CEP"));
    await user.type(screen.getByLabelText("Latitude"), "95");
    expect(screen.getByRole("button", { name: "Avançar" })).toBeDisabled();
    await user.clear(screen.getByLabelText("Latitude"));
    expect(screen.getByRole("button", { name: "Avançar" })).toBeEnabled();
    await user.click(screen.getByRole("button", { name: "Voltar" }));
    expect(screen.getByLabelText("Nome da unidade")).toHaveValue("Nova");
  });

  it("CA16: 409 UNIT_LIMIT_REACHED mostra a mensagem e permite tentar de novo", async () => {
    const user = userEvent.setup();
    setup("COORDINATOR", NEW, { post: () => HttpResponse.json({ code: "UNIT_LIMIT_REACHED", fields: [] }, { status: 409 }) });
    await wizard();
    await user.type(screen.getByLabelText("Nome da unidade"), "Nova");
    await next(user);
    await next(user);
    await user.click(screen.getByRole("button", { name: "Concluir" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("A organização já tem 10 unidades");
    expect(screen.getByRole("button", { name: "Concluir" })).toBeEnabled();
  });

  it("CA13: falha do servidor mostra o erro", async () => {
    const user = userEvent.setup();
    setup("COORDINATOR", NEW, { post: () => HttpResponse.json({ code: "INTERNAL_ERROR", fields: [] }, { status: 500 }) });
    await wizard();
    await user.type(screen.getByLabelText("Nome da unidade"), "Nova");
    await next(user);
    await next(user);
    await user.click(screen.getByRole("button", { name: "Concluir" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Não foi possível criar a unidade");
  });

  it("CA13: 403 do servidor mostra o estado sem permissão", async () => {
    const user = userEvent.setup();
    setup("COORDINATOR", NEW, { post: () => HttpResponse.json({ code: "FORBIDDEN", fields: [] }, { status: 403 }) });
    await wizard();
    await user.type(screen.getByLabelText("Nome da unidade"), "Nova");
    await next(user);
    await next(user);
    await user.click(screen.getByRole("button", { name: "Concluir" }));
    expect(await screen.findByRole("heading", { name: "Você não tem acesso" })).toBeInTheDocument();
  });

  it("CA16: convite FAILED mantém a tela com a unidade criada e o aviso", async () => {
    const user = userEvent.setup();
    setup("COORDINATOR", NEW, { post: () => HttpResponse.json({ unit: created, invite: "FAILED" }, { status: 201 }) });
    await wizard();
    await user.type(screen.getByLabelText("Nome da unidade"), "Nova");
    await next(user);
    await next(user);
    await user.click(screen.getByRole("button", { name: "Concluir" }));
    expect(await screen.findByText("Unidade Unidade 99 criada.")).toBeInTheDocument();
    expect(screen.getByText(/o convite não foi enviado/)).toBeInTheDocument();
  });

  it("CA15: com menos de 10 unidades o botão abre o assistente; com 10 fica desativado com o motivo", async () => {
    const user = userEvent.setup();
    const view = setup("COORDINATOR", "/configuracoes/unidades");
    await user.click(await screen.findByRole("link", { name: "Nova unidade" }));
    await wizard();
    view.unmount();
    setup("COORDINATOR", "/configuracoes/unidades", { list: Array.from({ length: 10 }, (_, i) => unit(i + 1)) });
    const button = await screen.findByRole("button", { name: "Nova unidade" });
    expect(button).toBeDisabled();
    expect(button).toHaveAccessibleDescription(/10 unidades/);
  });

  it("CA18: o Administrador não vê o assistente e a rota mostra que a área não está disponível", async () => {
    setup("ADMINISTRATOR", NEW);
    expect(await screen.findByRole("heading", { name: "Esta área não está disponível para o seu perfil" })).toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "Nova unidade" })).toBeNull();
  });

  it.each([
    ["claro", false],
    ["escuro", true],
  ])("CA14: a 360 px não tem violações no axe no tema %s, em cada etapa", async (_n, dark) => {
    const user = userEvent.setup();
    setDevicePrefersDark(dark);
    window.innerWidth = 360;
    const { container } = setup("COORDINATOR", NEW);
    await wizard();
    expect(await axeViolations(container)).toEqual([]);
    await user.type(screen.getByLabelText("Nome da unidade"), "Nova");
    await next(user);
    expect(await axeViolations(container)).toEqual([]);
    await next(user);
    expect(await axeViolations(container)).toEqual([]);
  });
});
