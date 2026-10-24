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
import type { TextModel, TextModelList } from "./useTexts";

const session = (role: string): SessionView => ({
  account: { id: "a", email: "x@x.com", name: "X" },
  links: [{ id: "l1", organizationId: "o", organizationName: "Alfa", role, unitIds: [] }],
  activeLinkId: "l1",
  twoFactor: { enabled: false, required: false },
});
const REMINDER = ["nome", "valor", "vencimento", "unidade", "descricao", "link_pagamento"];
const model = (key: string, group: TextModel["group"], fields: string[], subject: string, body: string, custom: TextModel["custom"] = null): TextModel => ({
  key,
  group,
  fields,
  platform: { subject, body },
  custom,
  updatedAt: custom ? "2026-04-01T12:00:00Z" : null,
  updatedBy: custom ? "Ana" : null,
});
const initial = (): TextModel[] => [
  model("REMINDER_DUE", "REMINDERS", REMINDER, "Vence hoje", "Sua cobrança {descricao} de {valor} vence hoje."),
  model("WELCOME", "WELCOME", ["nome", "unidade"], "Bem-vindo(a)", "Olá, {nome}!"),
  model("ANNOUNCEMENT_GENERAL", "ANNOUNCEMENTS", ["unidade"], "Aviso geral", "Aviso de {unidade}.", { subject: "Meu aviso", body: "Texto próprio de {unidade}." }),
];

let models: TextModel[];
let puts: { key: string; body: { subject: string; body: string } }[];
let deletes: string[];

function setup(role: string, over: { get?: () => Response | Promise<Response>; put?: () => Response | undefined } = {}) {
  models = initial();
  puts = [];
  deletes = [];
  const list = (): TextModelList => ({ canManage: role === "COORDINATOR", models });
  server.use(
    http.get("/api/v1/auth/session", () => HttpResponse.json(session(role))),
    http.get("/api/v1/permissions", () => HttpResponse.json({ permissions: mockPermissions(role) })),
    http.get("/api/v1/units/reachable", () => HttpResponse.json({ allUnits: false, units: [] })),
    http.get("/api/v1/organization/texts", over.get ?? (() => HttpResponse.json(list()))),
    http.put("/api/v1/organization/texts/:key", async ({ request, params }) => {
      const body = (await request.json()) as { subject: string; body: string };
      puts.push({ key: params.key as string, body });
      const failure = over.put?.();
      if (failure) return failure;
      const i = models.findIndex((m) => m.key === params.key);
      models[i] = { ...models[i], custom: body, updatedAt: "2026-05-01T12:00:00Z", updatedBy: "X" };
      return HttpResponse.json(models[i]);
    }),
    http.delete("/api/v1/organization/texts/:key", ({ params }) => {
      deletes.push(params.key as string);
      const i = models.findIndex((m) => m.key === params.key);
      models[i] = { ...models[i], custom: null, updatedAt: null, updatedBy: null };
      return new HttpResponse(null, { status: 204 });
    }),
  );
  return render(
    <ThemeProvider>
      <MemoryRouter initialEntries={["/configuracoes/textos"]}>
        <SessionProvider>
          <App />
        </SessionProvider>
      </MemoryRouter>
    </ThemeProvider>,
  );
}

const ready = () => screen.findByRole("heading", { name: "Textos", level: 1 });
const open = async (user: ReturnType<typeof userEvent.setup>, name: string, action = "Editar") => {
  await user.click(await screen.findByRole("button", { name: `${action} ${name}` }));
};

describe("TextsPage", () => {
  it("CA14: lista os modelos em três grupos com a marca de origem", async () => {
    setup("COORDINATOR");
    await ready();
    for (const g of ["Lembretes de cobrança", "Boas-vindas", "Comunicados"]) expect(screen.getByRole("heading", { name: g, level: 2 })).toBeInTheDocument();
    expect(screen.getByText("Lembrete no dia do vencimento").parentElement).toHaveTextContent("Texto da plataforma");
    expect(screen.getByText("Aviso geral").parentElement).toHaveTextContent("Texto próprio");
  });

  it("CA14: abre o editor com a prévia, que muda ao digitar; clicar num campo o insere; Salvar envia o PUT e confirma", async () => {
    const user = userEvent.setup();
    setup("COORDINATOR");
    await ready();
    await open(user, "Lembrete no dia do vencimento");
    const body = await screen.findByLabelText("Texto do e-mail");
    expect(body).toHaveValue("Sua cobrança {descricao} de {valor} vence hoje.");
    const preview = screen.getByRole("region", { name: "Prévia com dados de exemplo" });
    expect(preview).toHaveTextContent("Sua cobrança Mensalidade 11/2026 de R$ 150,00 vence hoje.");
    expect(screen.getByRole("button", { name: "Salvar" })).toBeDisabled();

    await user.clear(body);
    await user.type(body, "Oi ");
    await user.click(screen.getByRole("button", { name: "Inserir o campo nome" }));
    expect(body).toHaveValue("Oi {nome}");
    expect(preview).toHaveTextContent("Oi Maria Souza");
    expect(within(preview).getByText("feito com ctcore")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Salvar" }));
    expect(await screen.findByText("Texto salvo.")).toBeInTheDocument();
    expect(puts).toEqual([{ key: "REMINDER_DUE", body: { subject: "Vence hoje", body: "Oi {nome}" } }]);
    expect(screen.getByText("Lembrete no dia do vencimento").parentElement).toHaveTextContent("Texto próprio");
    expect(screen.getByRole("button", { name: "Salvar" })).toBeDisabled();
  });

  it("CA15: voltar ao texto da plataforma só envia o DELETE depois de confirmar; cancelar não envia nada", async () => {
    const user = userEvent.setup();
    setup("COORDINATOR");
    await ready();
    await open(user, "Aviso geral");
    const body = await screen.findByLabelText("Texto do e-mail");
    expect(body).toHaveValue("Texto próprio de {unidade}.");
    await user.click(screen.getByRole("button", { name: "Voltar ao texto da plataforma" }));
    await user.click(await screen.findByRole("button", { name: "Cancelar" }));
    expect(deletes).toEqual([]);
    expect(body).toHaveValue("Texto próprio de {unidade}.");

    await user.click(screen.getByRole("button", { name: "Voltar ao texto da plataforma" }));
    await user.click(await screen.findByRole("button", { name: "Voltar ao padrão" }));
    await waitFor(() => expect(deletes).toEqual(["ANNOUNCEMENT_GENERAL"]));
    expect(await screen.findByText("O texto da plataforma voltou a valer.")).toBeInTheDocument();
    expect(screen.getByLabelText("Texto do e-mail")).toHaveValue("Aviso de {unidade}.");
    expect(screen.queryByRole("button", { name: "Voltar ao texto da plataforma" })).toBeNull();
  });

  it("CA16: o 400 do servidor aparece junto do campo e o foco vai a ele", async () => {
    const user = userEvent.setup();
    setup("COORDINATOR", { put: () => HttpResponse.json({ code: "VALIDATION_ERROR", fields: [{ field: "body", message: "campo automático desconhecido: {cpf}" }] }, { status: 400 }) });
    await ready();
    await open(user, "Boas-vindas ao aluno");
    const body = await screen.findByLabelText("Texto do e-mail");
    await user.type(body, " {{cpf}");
    await user.click(screen.getByRole("button", { name: "Salvar" }));
    expect(await screen.findByText(/Confira o texto/)).toBeInTheDocument();
    expect(body).toHaveFocus();
    expect(body).toHaveAccessibleDescription(/Confira o texto/);
  });

  it("CA16: texto igual ao da plataforma recusado mostra a explicação própria", async () => {
    const user = userEvent.setup();
    setup("COORDINATOR", { put: () => HttpResponse.json({ code: "VALIDATION_ERROR", fields: [{ field: "body", message: "igual ao texto da plataforma" }] }, { status: 400 }) });
    await ready();
    await open(user, "Boas-vindas ao aluno");
    await user.type(await screen.findByLabelText("Texto do e-mail"), "x");
    await user.click(screen.getByRole("button", { name: "Salvar" }));
    expect(await screen.findByText(/igual ao da plataforma/)).toBeInTheDocument();
  });

  it("CA17: o Administrador vê o editor desabilitado e sem Salvar", async () => {
    const user = userEvent.setup();
    setup("ADMINISTRATOR");
    await ready();
    await open(user, "Boas-vindas ao aluno", "Ver");
    expect(await screen.findByLabelText("Texto do e-mail")).toBeDisabled();
    expect(screen.getByLabelText("Assunto")).toBeDisabled();
    expect(screen.queryByRole("button", { name: "Salvar" })).toBeNull();
    expect(screen.queryByRole("button", { name: "Voltar ao texto da plataforma" })).toBeNull();
    expect(screen.getByRole("button", { name: "Inserir o campo nome" })).toBeDisabled();
  });

  it("CA17: carregando, erro com nova tentativa, vazio e sem permissão", async () => {
    const user = userEvent.setup();
    let calls = 0;
    const first = setup("COORDINATOR", { get: () => (++calls === 1 ? HttpResponse.json({ code: "INTERNAL_ERROR" }, { status: 500 }) : HttpResponse.json({ canManage: true, models: [] })) });
    expect(screen.getByRole("status")).toHaveTextContent("Carregando");
    await user.click(await screen.findByRole("button", { name: "Tentar de novo" }));
    expect(await screen.findByRole("heading", { name: "Nenhum modelo de texto" })).toBeInTheDocument();
    first.unmount();
    setup("COORDINATOR", { get: () => HttpResponse.json({ code: "FORBIDDEN" }, { status: 403 }) });
    expect(await screen.findByRole("heading", { name: "Você não tem acesso" })).toBeInTheDocument();
  });

  it("R12: o item aparece no menu de Configurações para Coordenador e Administrador e não para Professor", async () => {
    for (const [role, visible] of [["COORDINATOR", true], ["ADMINISTRATOR", true], ["TEACHER", false]] as const) {
      const { unmount } = setup(role);
      if (visible) {
        await ready();
        expect(screen.getAllByRole("link", { name: "Textos" })[0]).toHaveAttribute("href", "/configuracoes/textos");
      } else {
        await screen.findByRole("heading", { level: 1 });
        expect(screen.queryByRole("link", { name: "Textos" })).toBeNull();
      }
      unmount();
    }
  });

  it.each([
    ["claro", false],
    ["escuro", true],
  ])("CA17: a 360 px não tem violações no axe no tema %s, para quem edita e para quem consulta", async (_n, dark) => {
    const user = userEvent.setup();
    setDevicePrefersDark(dark);
    window.innerWidth = 360;
    const first = setup("COORDINATOR");
    await ready();
    await open(user, "Lembrete no dia do vencimento");
    await screen.findByLabelText("Texto do e-mail");
    expect(await axeViolations(first.container)).toEqual([]);
    first.unmount();
    const second = setup("ADMINISTRATOR");
    await ready();
    await open(user, "Aviso geral", "Ver");
    await screen.findByLabelText("Texto do e-mail");
    expect(await axeViolations(second.container)).toEqual([]);
  });
});
