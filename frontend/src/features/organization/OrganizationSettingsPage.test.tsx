import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { MemoryRouter } from "react-router";
import { vi } from "vitest";
import { App } from "@/App";
import { SessionProvider, type SessionView } from "@/features/auth/SessionProvider";
import { mockPermissions } from "@/mocks/permissions";
import { server } from "@/mocks/server";
import { axeViolations } from "@/test/axe";
import { setDevicePrefersDark } from "@/test/device";
import { ThemeProvider } from "@/theme/ThemeProvider";
import type { OrganizationView } from "./useOrganization";

const session = (role: string): SessionView => ({
  account: { id: "a", email: "x@x.com", name: "X" },
  links: [{ id: "l1", organizationId: "o", organizationName: "Alfa", role, unitIds: [] }],
  activeLinkId: "l1",
  twoFactor: { enabled: false, required: false },
});
const empty: OrganizationView = { name: "Academia Alfa", color: null, holderDocument: null, logoUrl: null, version: 0, lastChange: null };
const full: OrganizationView = { name: "Academia Alfa", color: "#1a56db", holderDocument: "11222333000181", logoUrl: "data:image/png;base64,AAAA", version: 0, lastChange: null };

let writes: { method: string; body?: unknown }[];
let stored: OrganizationView;

function setup(role: string, initial: OrganizationView = empty, read?: () => Response | Promise<Response>) {
  stored = initial;
  writes = [];
  server.use(
    http.get("/api/v1/auth/session", () => HttpResponse.json(session(role))),
    http.get("/api/v1/permissions", () => HttpResponse.json({ permissions: mockPermissions(role) })),
    http.get("/api/v1/units/reachable", () => HttpResponse.json({ allUnits: false, units: [] })),
    http.get("/api/v1/organization", read ?? (() => HttpResponse.json(stored))),
    http.patch("/api/v1/organization", async ({ request }) => {
      const body = (await request.json()) as Partial<OrganizationView>;
      writes.push({ method: "PATCH", body });
      if (body.name === "Recusado") {
        return HttpResponse.json({ code: "VALIDATION_ERROR", fields: [{ field: "name", message: "nome recusado pelo servidor" }] }, { status: 400 });
      }
      stored = { ...stored, ...body } as OrganizationView;
      return HttpResponse.json(stored);
    }),
    http.delete("/api/v1/organization/logo", () => {
      writes.push({ method: "DELETE" });
      stored = { ...stored, logoUrl: null, version: 0, lastChange: null };
      return HttpResponse.json(stored);
    }),
    http.put("/api/v1/organization/logo", () => {
      writes.push({ method: "PUT" });
      stored = { ...stored, logoUrl: "data:image/png;base64,BBBB" };
      return HttpResponse.json(stored);
    }),
  );
  return render(
    <ThemeProvider>
      <MemoryRouter initialEntries={["/configuracoes/organizacao"]}>
        <SessionProvider>
          <App />
        </SessionProvider>
      </MemoryRouter>
    </ThemeProvider>,
  );
}

const ready = () => screen.findByRole("heading", { name: "Organização", level: 1 });

describe("OrganizationSettingsPage", () => {
  it("CA15: o Coordenador altera nome, cor e titular, salva e vê a confirmação com os valores novos", async () => {
    const user = userEvent.setup();
    setup("COORDINATOR", full);
    await ready();
    expect(screen.getByLabelText("Nome da organização")).toHaveValue("Academia Alfa");
    expect(screen.getByLabelText("Cor principal (#RRGGBB)")).toHaveValue("#1a56db");
    expect(screen.getByLabelText("Titular (CPF ou CNPJ)")).toHaveValue("11.222.333/0001-81");
    await user.clear(screen.getByLabelText("Nome da organização"));
    await user.type(screen.getByLabelText("Nome da organização"), "Dojo Novo");
    await user.clear(screen.getByLabelText("Cor principal (#RRGGBB)"));
    await user.type(screen.getByLabelText("Cor principal (#RRGGBB)"), "#0B6E4F");
    await user.clear(screen.getByLabelText("Titular (CPF ou CNPJ)"));
    await user.type(screen.getByLabelText("Titular (CPF ou CNPJ)"), "52998224725");
    expect(screen.getByLabelText("Titular (CPF ou CNPJ)")).toHaveValue("529.982.247-25");
    await user.click(screen.getByRole("button", { name: "Salvar" }));
    expect(await screen.findByText("Dados salvos.")).toBeInTheDocument();
    expect(writes).toEqual([{ method: "PATCH", body: { name: "Dojo Novo", color: "#0b6e4f", holderDocument: "52998224725" } }]);
    expect(screen.getByLabelText("Nome da organização")).toHaveValue("Dojo Novo");
  });

  it("CA15: erro do servidor aparece junto do campo", async () => {
    const user = userEvent.setup();
    setup("COORDINATOR");
    await ready();
    await user.clear(screen.getByLabelText("Nome da organização"));
    await user.type(screen.getByLabelText("Nome da organização"), "Recusado");
    await user.click(screen.getByRole("button", { name: "Salvar" }));
    expect(await screen.findByText("nome recusado pelo servidor")).toBeInTheDocument();
    expect(screen.getByLabelText("Nome da organização")).toHaveAttribute("aria-invalid", "true");
  });

  it("CA15: cor reprovada mostra a razão e Reprovado com texto e ícone e bloqueia o salvar; aprovada mostra Aprovado", async () => {
    const user = userEvent.setup();
    setup("COORDINATOR");
    await ready();
    await user.type(screen.getByLabelText("Cor principal (#RRGGBB)"), "#ffeb3b");
    expect(screen.getByText(/Contraste com texto branco: 1,2\d para 1/)).toBeInTheDocument();
    expect(screen.getByText(/Reprovado/)).toBeInTheDocument();
    expect(screen.getByText(/Reprovado/).querySelector("svg")).not.toBeNull();
    expect(screen.getByRole("button", { name: "Salvar" })).toBeDisabled();
    await user.clear(screen.getByLabelText("Cor principal (#RRGGBB)"));
    await user.type(screen.getByLabelText("Cor principal (#RRGGBB)"), "#1a56db");
    expect(screen.getByText(/Aprovado/).querySelector("svg")).not.toBeNull();
    expect(screen.getByRole("button", { name: "Salvar" })).toBeEnabled();
    await user.clear(screen.getByLabelText("Cor principal (#RRGGBB)"));
    await user.type(screen.getByLabelText("Cor principal (#RRGGBB)"), "azul");
    expect(screen.getByRole("alert")).toHaveTextContent("Use o formato #RRGGBB");
    expect(screen.getByRole("button", { name: "Salvar" })).toBeDisabled();
  });

  it("nome em branco e titular inválido bloqueiam o salvar", async () => {
    const user = userEvent.setup();
    setup("COORDINATOR");
    await ready();
    await user.clear(screen.getByLabelText("Nome da organização"));
    expect(screen.getByText("Informe o nome da organização.")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Salvar" })).toBeDisabled();
    await user.type(screen.getByLabelText("Nome da organização"), "Dojo");
    await user.type(screen.getByLabelText("Titular (CPF ou CNPJ)"), "52998224726");
    await user.tab();
    expect(screen.getByText(/o CPF ou CNPJ digitado não é válido/)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Salvar" })).toBeDisabled();
  });

  it("CA16: tipo não aceito e arquivo acima de 5 MB são recusados no navegador, sem abrir o corte", async () => {
    const user = userEvent.setup({ applyAccept: false });
    setup("COORDINATOR");
    await ready();
    await user.upload(screen.getByLabelText("Arquivo da logo"), new File(["x"], "logo.gif", { type: "image/gif" }));
    expect(await screen.findByText("Use uma imagem PNG, JPG ou WebP.")).toBeInTheDocument();
    const big = new File(["x"], "grande.png", { type: "image/png" });
    Object.defineProperty(big, "size", { value: 5 * 1024 * 1024 + 1 });
    await user.upload(screen.getByLabelText("Arquivo da logo"), big);
    expect(await screen.findByText("A imagem tem mais de 5 MB. Escolha uma menor.")).toBeInTheDocument();
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(writes).toEqual([]);
  });

  it("CA16: arquivo aceito abre o corte; ao confirmar o navegador gera a imagem e envia", async () => {
    const user = userEvent.setup();
    class FakeImage {
      width = 512;
      height = 512;
      onload: (() => void) | null = null;
      set src(_: string) {
        queueMicrotask(() => this.onload?.());
      }
    }
    vi.stubGlobal("Image", FakeImage);
    URL.createObjectURL = vi.fn(() => "blob:logo");
    URL.revokeObjectURL = vi.fn();
    HTMLCanvasElement.prototype.getContext = vi.fn(() => ({ drawImage: vi.fn() })) as never;
    HTMLCanvasElement.prototype.toBlob = ((cb: BlobCallback) => cb(new Blob(["png"], { type: "image/png" }))) as never;
    try {
      setup("COORDINATOR");
      await ready();
      expect(screen.getByText("Sem logo: vale a marca padrão da plataforma.")).toBeInTheDocument();
      await user.upload(screen.getByLabelText("Arquivo da logo"), new File(["x"], "logo.png", { type: "image/png" }));
      const confirm = await screen.findByRole("button", { name: "Usar este corte" });
      await waitFor(() => expect(confirm).toBeEnabled());
      await user.click(confirm);
      expect(await screen.findByText("Logo atualizada.")).toBeInTheDocument();
      expect(writes).toEqual([{ method: "PUT" }]);
      expect(screen.getByRole("img", { name: "Logo da organização" })).toBeInTheDocument();
      expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    } finally {
      vi.unstubAllGlobals();
    }
  });

  it("CA16: Remover logo pede confirmação antes de apagar", async () => {
    const user = userEvent.setup();
    setup("COORDINATOR", full);
    await ready();
    await user.click(screen.getByRole("button", { name: "Remover logo" }));
    await screen.findByRole("dialog", { name: "Remover logo" });
    expect(writes).toEqual([]);
    await user.click(screen.getByRole("button", { name: "Cancelar" }));
    expect(writes).toEqual([]);
    await user.click(screen.getByRole("button", { name: "Remover logo" }));
    const again = await screen.findByRole("dialog", { name: "Remover logo" });
    await user.click(within(again).getByRole("button", { name: "Remover logo" }));
    expect(await screen.findByText("Logo removida.")).toBeInTheDocument();
    expect(writes).toEqual([{ method: "DELETE" }]);
    expect(screen.getByText("Sem logo: vale a marca padrão da plataforma.")).toBeInTheDocument();
  });

  it("CA17: o Administrador vê os dados com campos desabilitados e sem salvar, enviar nem remover", async () => {
    setup("ADMINISTRATOR", full);
    await ready();
    expect(screen.getByText("Você pode ver estes dados, mas só o Coordenador altera.")).toBeInTheDocument();
    for (const label of ["Nome da organização", "Cor principal (#RRGGBB)", "Titular (CPF ou CNPJ)", "Escolher cor"]) {
      expect(screen.getByLabelText(label)).toBeDisabled();
    }
    for (const name of ["Salvar", "Enviar logo", "Trocar logo", "Remover logo"]) {
      expect(screen.queryByRole("button", { name })).not.toBeInTheDocument();
    }
    expect(screen.queryByLabelText("Arquivo da logo")).not.toBeInTheDocument();
  });

  it("CA18: carregando, erro com Tentar de novo e Professor veem os estados certos", async () => {
    const user = userEvent.setup();
    const first = setup("COORDINATOR", empty, () => new Promise<Response>(() => {}));
    expect(await screen.findByText("Carregando...")).toBeInTheDocument();
    first.unmount();

    let fail = true;
    setup("COORDINATOR", empty, () => (fail ? HttpResponse.json({ code: "INTERNAL_ERROR" }, { status: 500 }) : HttpResponse.json(empty)));
    const retry = await screen.findByRole("button", { name: "Tentar de novo" });
    fail = false;
    await user.click(retry);
    await ready();
  });

  it("CA18: Professor vê a tela de área indisponível e o servidor negando (403) mostra o estado sem permissão", async () => {
    const first = setup("TEACHER");
    expect(await screen.findByRole("heading", { name: "Esta área não está disponível para o seu perfil" })).toBeInTheDocument();
    first.unmount();
    setup("ADMINISTRATOR", empty, () => HttpResponse.json({ code: "FORBIDDEN" }, { status: 403 }));
    expect(await screen.findByRole("heading", { name: "Você não tem acesso" })).toBeInTheDocument();
  });

  it("CA18: organização sem logo, cor e titular mostra os campos vazios com o texto de padrão", async () => {
    setup("COORDINATOR");
    await ready();
    expect(screen.getByLabelText("Cor principal (#RRGGBB)")).toHaveValue("");
    expect(screen.getByText("Sem cor definida: vale a cor padrão da plataforma.")).toBeInTheDocument();
    expect(screen.getByLabelText("Titular (CPF ou CNPJ)")).toHaveValue("");
    expect(screen.getByText(/Não informado/)).toBeInTheDocument();
    expect(screen.getByText("Sem logo: vale a marca padrão da plataforma.")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Enviar logo" })).toBeInTheDocument();
  });

  it.each([
    ["claro", false],
    ["escuro", true],
  ])("CA18: a 360 px não tem violações no axe no tema %s, com a cor reprovada e o aviso visíveis", async (_n, dark) => {
    const user = userEvent.setup();
    setDevicePrefersDark(dark);
    window.innerWidth = 360;
    const { container } = setup("COORDINATOR", full);
    await ready();
    expect(await axeViolations(container)).toEqual([]);
    await user.clear(screen.getByLabelText("Cor principal (#RRGGBB)"));
    await user.type(screen.getByLabelText("Cor principal (#RRGGBB)"), "#ffeb3b");
    await user.clear(screen.getByLabelText("Nome da organização"));
    expect(await axeViolations(container)).toEqual([]);
  });

  it("CA18: o Administrador também passa no axe", async () => {
    const { container } = setup("ADMINISTRATOR", full);
    await ready();
    expect(await axeViolations(container)).toEqual([]);
  });
});

const BIA = { at: "2026-05-20T15:30:00Z", by: "Bia" };
const DIALOG = "Estes dados foram alterados por outra pessoa";
const saveButton = () => screen.getByRole("button", { name: "Salvar" });

// A outra pessoa já salvou: o servidor está na versão 3 com outro nome; o primeiro PATCH responde 409.
function conflicting(patchStatuses: number[] = [409]) {
  const sent: { version: string | null; body: Partial<OrganizationView> }[] = [];
  const reads = { count: 0 };
  setup("COORDINATOR", { ...full, name: "Nome da outra pessoa", version: 3, lastChange: BIA }, () => {
    reads.count += 1;
    return HttpResponse.json(stored);
  });
  server.use(
    http.patch("/api/v1/organization", async ({ request }) => {
      const body = (await request.json()) as Partial<OrganizationView>;
      sent.push({ version: request.headers.get("If-Match"), body });
      const status = patchStatuses[Math.min(sent.length - 1, patchStatuses.length - 1)];
      if (status === 409) return HttpResponse.json({ code: "EDIT_CONFLICT", message: "x", changed: false, fields: [], traceId: "t" }, { status });
      stored = { ...stored, ...body, version: stored.version + 1 } as OrganizationView;
      return HttpResponse.json(stored);
    }),
  );
  return { sent, reads };
}

describe("OrganizationSettingsPage: última alteração e edição simultânea (RF-ORG-08, RNF-15)", () => {
  it("CA9: mostra quem alterou por último e quando, ou 'Ainda sem alterações'; o Administrador também vê", async () => {
    setup("ADMINISTRATOR", { ...full, lastChange: BIA });
    await ready();
    expect(screen.getByText("Última alteração em 20/05/2026 12h30 por Bia")).toBeInTheDocument();
  });

  it("CA9: sem registro mostra 'Ainda sem alterações'", async () => {
    setup("COORDINATOR", empty);
    await ready();
    expect(screen.getByText("Ainda sem alterações")).toBeInTheDocument();
  });

  it("RNF-15: a gravação envia a versão lida no If-Match", async () => {
    const user = userEvent.setup();
    const { sent } = conflicting([200]);
    await ready();
    await user.type(screen.getByLabelText("Nome da organização"), "!");
    await user.click(saveButton());
    expect(await screen.findByText("Dados salvos.")).toBeInTheDocument();
    expect(sent[0]?.version).toBe("3");
  });

  it("CA10: o 409 abre a janela com quem alterou, o campo diferente com os dois valores e os dois botões", async () => {
    const user = userEvent.setup();
    conflicting();
    await ready();
    await user.clear(screen.getByLabelText("Nome da organização"));
    await user.type(screen.getByLabelText("Nome da organização"), "Meu nome");
    await user.click(saveButton());
    const dialog = await screen.findByRole("dialog", { name: DIALOG });
    expect(await within(dialog).findByText("Última alteração em 20/05/2026 12h30 por Bia")).toBeInTheDocument();
    const item = within(dialog).getByText("Nome da organização").closest("li")!;
    expect(item).toHaveTextContent("Seu valor: Meu nome");
    expect(item).toHaveTextContent("Valor atual: Nome da outra pessoa");
    expect(within(dialog).queryByText("Cor principal")).not.toBeInTheDocument();
    expect(within(dialog).getByRole("button", { name: "Recarregar" })).toBeInTheDocument();
    expect(within(dialog).getByRole("button", { name: "Sobrescrever" })).toBeInTheDocument();
  });

  it("CA10: Recarregar descarta a edição, mostra o atual e devolve o foco ao Salvar", async () => {
    const user = userEvent.setup();
    conflicting();
    await ready();
    await user.clear(screen.getByLabelText("Nome da organização"));
    await user.type(screen.getByLabelText("Nome da organização"), "Meu nome");
    await user.click(saveButton());
    const dialog = await screen.findByRole("dialog", { name: DIALOG });
    await user.click(await within(dialog).findByRole("button", { name: "Recarregar" }));
    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
    expect(screen.getByLabelText("Nome da organização")).toHaveValue("Nome da outra pessoa");
    expect(screen.getByText("Última alteração em 20/05/2026 12h30 por Bia")).toBeInTheDocument();
    await waitFor(() => expect(saveButton()).toHaveFocus());
  });

  it("CA10: Sobrescrever regrava com a versão atual, mantém a edição e mostra 'Dados salvos.'", async () => {
    const user = userEvent.setup();
    const { sent } = conflicting([409, 200]);
    await ready();
    await user.clear(screen.getByLabelText("Nome da organização"));
    await user.type(screen.getByLabelText("Nome da organização"), "Meu nome");
    await user.click(saveButton());
    const dialog = await screen.findByRole("dialog", { name: DIALOG });
    await user.click(await within(dialog).findByRole("button", { name: "Sobrescrever" }));
    expect(await screen.findByText("Dados salvos.")).toBeInTheDocument();
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(sent.map((s) => s.version)).toEqual(["3", "3"]);
    expect(sent[1]?.body.name).toBe("Meu nome");
    expect(screen.getByLabelText("Nome da organização")).toHaveValue("Meu nome");
    await waitFor(() => expect(saveButton()).toHaveFocus());
  });

  it("CA10: novo 409 durante o Sobrescrever consulta de novo e mantém o aviso aberto", async () => {
    const user = userEvent.setup();
    const { sent, reads } = conflicting([409, 409]);
    await ready();
    await user.type(screen.getByLabelText("Nome da organização"), "!");
    await user.click(saveButton());
    const dialog = await screen.findByRole("dialog", { name: DIALOG });
    await user.click(await within(dialog).findByRole("button", { name: "Sobrescrever" }));
    await waitFor(() => expect(sent).toHaveLength(2));
    expect(await screen.findByRole("dialog", { name: DIALOG })).toBeInTheDocument();
    await waitFor(() => expect(reads.count).toBe(3));
    expect(await within(screen.getByRole("dialog")).findByRole("button", { name: "Sobrescrever" })).toBeEnabled();
    expect(screen.queryByText("Dados salvos.")).not.toBeInTheDocument();
  });

  it("CA10: erro de rede na consulta do estado atual mostra o erro com 'Tentar de novo'", async () => {
    const user = userEvent.setup();
    conflicting();
    await ready();
    await user.type(screen.getByLabelText("Nome da organização"), "!");
    server.use(http.get("/api/v1/organization", () => HttpResponse.error(), { once: true }));
    await user.click(saveButton());
    const dialog = await screen.findByRole("dialog", { name: DIALOG });
    expect(await within(dialog).findByText("Não foi possível consultar o estado atual. Nada foi alterado.")).toBeInTheDocument();
    await user.click(within(dialog).getByRole("button", { name: "Tentar de novo" }));
    expect(await within(dialog).findByRole("button", { name: "Sobrescrever" })).toBeInTheDocument();
  });

  it.each([
    ["claro", false],
    ["escuro", true],
  ])("CA11: a janela de conflito a 360 px não tem violações no axe no tema %s", async (_n, dark) => {
    const user = userEvent.setup();
    setDevicePrefersDark(dark);
    window.innerWidth = 360;
    conflicting();
    await ready();
    await user.type(screen.getByLabelText("Nome da organização"), "!");
    await user.click(saveButton());
    const dialog = await screen.findByRole("dialog", { name: DIALOG });
    await within(dialog).findByRole("button", { name: "Sobrescrever" });
    expect(await axeViolations(document.body)).toEqual([]);
  });
});
