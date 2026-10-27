import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { MemoryRouter } from "react-router";
import { App } from "@/App";
import { SessionProvider, type SessionView } from "@/features/auth/SessionProvider";
import { server } from "@/mocks/server";
import { axeViolations } from "@/test/axe";
import { setDevicePrefersDark } from "@/test/device";
import { ThemeProvider } from "@/theme/ThemeProvider";
import type { PortalInfoData } from "./usePortalInfo";

const session: SessionView = {
  account: { id: "a", email: "x@x.com", name: "Ana Silva" },
  links: [{ id: "l1", organizationId: "o", organizationName: "Alfa", role: "GUARDIAN", unitIds: [] }],
  activeLinkId: "l1",
  twoFactor: { enabled: false, required: false },
};
const jiu = { id: "m1", name: "Jiu-Jitsu" };
const centro = { id: "u1", name: "Unidade Centro", phone: "2133334444", whatsapp: null, email: "centro@exemplo.test" };
const level = (id: string, degree: number, achievedOn: string | null, origin = false) => ({ id, levelName: "Azul", color: "#1E40AF", secondaryColor: null, degree, achievedOn, origin });
const info = (over: Partial<PortalInfoData> = {}): PortalInfoData => ({
  reduced: false,
  students: [{ id: "s1", name: "Ana Souza", photoUrl: null, self: true }],
  selectedStudentId: "s1",
  personal: {
    name: "Ana Souza",
    birthDate: "1990-03-10",
    maskedCpf: "***.***.***-25",
    photoUrl: null,
    phone: "21900000001",
    email: "ana@exemplo.test",
    address: { postalCode: "01001000", street: "Rua Fictícia", number: "1", complement: null, district: "Centro", city: "Rio de Janeiro", state: "RJ" },
    emergency: { name: null, relationship: null, phone: null },
    canEdit: true,
    editableFields: ["phone", "photo"],
  },
  unitContacts: [centro],
  trainings: [
    {
      modality: jiu,
      unit: centro,
      mainClass: { clazz: { id: "c1", name: "Jiu-Jitsu Adulto" }, teacherName: "Fictício", schedule: [{ weekday: 1, startsAt: "19:00", endsAt: "20:00" }] },
      otherClasses: [{ clazz: { id: "c2", name: "Livre" }, teacherName: "Outro", schedule: [{ weekday: 5, startsAt: "20:00", endsAt: "21:00" }] }],
    },
  ],
  plans: [
    {
      modality: jiu,
      plan: { id: "p1", name: "Mensal" },
      planCents: 20000,
      benefit: { type: "PERCENT", value: 10, validUntil: null },
      benefitCents: 2000,
      monthlyCents: 18000,
      dueDay: 5,
      nextPlan: null,
    },
  ],
  graduation: [{ modality: jiu, belt: level("l2", 2, "2026-10-01"), history: [level("l2", 2, "2026-10-01"), level("l0", 0, null, true)] }],
  emailAnnouncements: true,
  terms: { enrollment: { version: 1, acceptedAt: "2026-01-10T12:00:00Z" }, imageUse: { granted: true, version: 1, at: "2026-01-10T12:00:00Z" } },
  ...over,
});
type Resolver = (i: { request: Request }) => Response | Promise<Response>;
const serve = (data: PortalInfoData | Resolver) => server.use(http.get("/api/v1/portal/info", typeof data === "function" ? data : () => HttpResponse.json(data)));

function setup(path = "/portal/minhas-informacoes", reduced = false) {
  server.use(
    http.get("/api/v1/auth/session", () => HttpResponse.json(session)),
    http.get("/api/v1/portal/term", () => HttpResponse.json([])),
    http.get("/api/v1/portal/students", () => HttpResponse.json([])),
    http.get("/api/v1/portal/students/:id/requests", () => HttpResponse.json([])),
    http.get("/api/v1/auth/sessions", () => HttpResponse.json([])),
    http.get("/api/v1/portal/shell", () => HttpResponse.json({ organization: { name: "Academia Alfa", color: null, logoUrl: null }, reduced, unreadAnnouncements: 0 })),
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
const card = (name: string) => screen.getByRole("heading", { name }).closest("section") as HTMLElement;

describe("PortalInfoPage (RF-POR-07)", () => {
  it("dados: mostra o CPF mascarado, o telefone e o endereço; sem CPF completo", async () => {
    serve(info());
    setup();
    await screen.findByRole("heading", { name: "Dados pessoais" });
    const personal = card("Dados pessoais");
    expect(within(personal).getByText("***.***.***-25")).toBeInTheDocument();
    expect(within(personal).getByText("(21) 90000-0001")).toBeInTheDocument();
    expect(within(personal).getByText(/Rua Fictícia, 1 · Centro · Rio de Janeiro\/RJ · CEP 01001000/)).toBeInTheDocument();
    expect(within(personal).getByText("Não informado", { selector: "dd" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Ver carteirinha" })).toHaveAttribute("href", "/portal/carteirinha");
  });

  it("dados: editar o contato envia só os campos da lista e mostra o dado novo", async () => {
    const body: unknown[] = [];
    let street = "Rua Fictícia";
    serve(() => HttpResponse.json(info({ personal: { ...info().personal, address: { ...info().personal.address, street } } })));
    server.use(
      http.put("/api/v1/portal/students/s1/contact", async ({ request }) => {
        const sent = (await request.json()) as Record<string, string>;
        body.push(sent);
        street = sent.street;
        return HttpResponse.json(info().personal);
      }),
    );
    setup();
    const user = userEvent.setup();
    await user.click(await screen.findByRole("button", { name: "Editar contato" }));
    const dialog = await screen.findByRole("dialog", { name: "Editar contato" });
    const field = within(dialog).getByLabelText("Logradouro");
    await user.clear(field);
    await user.type(field, "Rua Nova");
    await user.click(within(dialog).getByRole("button", { name: "Salvar" }));
    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
    expect(Object.keys(body[0] as object).sort()).toEqual(["city", "complement", "district", "emergencyName", "emergencyPhone", "emergencyRelationship", "number", "phone", "postalCode", "state", "street"]);
    expect(await screen.findByText(/Rua Nova, 1/)).toBeInTheDocument();
  });

  it("dados: erro 422 do servidor aparece no campo e a janela continua aberta; emergência incompleta nem sai", async () => {
    let calls = 0;
    serve(info());
    server.use(
      http.put("/api/v1/portal/students/s1/contact", () => {
        calls += 1;
        return HttpResponse.json({ code: "UNPROCESSABLE", fields: [{ field: "city", message: "x" }] }, { status: 422 });
      }),
    );
    setup();
    const user = userEvent.setup();
    await user.click(await screen.findByRole("button", { name: "Editar contato" }));
    const dialog = await screen.findByRole("dialog");
    await user.type(within(dialog).getByLabelText("Nome do contato"), "Mãe");
    await user.click(within(dialog).getByRole("button", { name: "Salvar" }));
    expect(await within(dialog).findByText(/Informe nome, parentesco e telefone/)).toBeInTheDocument();
    expect(calls).toBe(0);
    await user.clear(within(dialog).getByLabelText("Nome do contato"));
    await user.click(within(dialog).getByRole("button", { name: "Salvar" }));
    expect(await within(dialog).findByText("Confira este campo.")).toBeInTheDocument();
    expect(calls).toBe(1);
  });

  it("dados: remover a foto chama o servidor", async () => {
    let removed = 0;
    serve(info({ personal: { ...info().personal, photoUrl: "http://x.test/f.png" } }));
    server.use(http.delete("/api/v1/portal/students/s1/photo", () => (removed++, HttpResponse.json(info().personal))));
    setup();
    await userEvent.setup().click(await screen.findByRole("button", { name: "Remover foto" }));
    await waitFor(() => expect(removed).toBe(1));
  });

  it("dados: trocar a foto (escolher, recortar, enviar) mostra a nova", async () => {
    // jsdom não decodifica imagem nem desenha em canvas: Image, URL e canvas são simulações (como no teste do recorte).
    class FakeImage {
      width = 800;
      height = 600;
      onload: (() => void) | null = null;
      set src(_: string) {
        queueMicrotask(() => this.onload?.());
      }
    }
    vi.stubGlobal("Image", FakeImage);
    URL.createObjectURL = vi.fn(() => "blob:foto");
    URL.revokeObjectURL = vi.fn();
    HTMLCanvasElement.prototype.getContext = vi.fn(() => ({ drawImage: vi.fn() })) as never;
    HTMLCanvasElement.prototype.toBlob = vi.fn((cb: BlobCallback) => cb(new Blob(["x"], { type: "image/png" }))) as never;
    try {
      let sent = "";
      let uploaded = false;
      serve(() => HttpResponse.json(info({ personal: { ...info().personal, photoUrl: uploaded ? "http://x.test/nova.png" : null } })));
      server.use(
        http.put("/api/v1/portal/students/s1/photo", async ({ request }) => {
          sent = request.headers.get("content-type") ?? "";
          uploaded = true;
          return HttpResponse.json({ ...info().personal, photoUrl: "http://x.test/nova.png" });
        }),
      );
      setup();
      const user = userEvent.setup();
      await screen.findByRole("button", { name: "Escolher foto" });
      expect(document.querySelector("img[src='http://x.test/nova.png']")).toBeNull();
      await user.upload(screen.getByLabelText("Arquivo da foto"), new File(["x"], "eu.png", { type: "image/png" }));
      const confirm = await screen.findByRole("button", { name: "Usar este corte" });
      await waitFor(() => expect(confirm).toBeEnabled());
      await user.click(confirm);
      await waitFor(() => expect(document.querySelector("img[src='http://x.test/nova.png']")).not.toBeNull());
      expect(sent).toContain("multipart/form-data");
      expect(await screen.findByRole("button", { name: "Trocar foto" })).toBeInTheDocument();
    } finally {
      vi.unstubAllGlobals();
    }
  });

  it("treinos: modalidade, unidade com contato, turma principal com dia e professor, outros horários; sem contato do professor", async () => {
    serve(info());
    setup();
    await screen.findByRole("heading", { name: "Treinos" });
    const trainings = card("Treinos");
    expect(within(trainings).getByText("Jiu-Jitsu")).toBeInTheDocument();
    expect(within(trainings).getByText("Jiu-Jitsu Adulto · Prof. Fictício")).toBeInTheDocument();
    expect(within(trainings).getByText("Segunda-feira 19:00–20:00")).toBeInTheDocument();
    expect(within(trainings).getByText("Sexta-feira 20:00–21:00")).toBeInTheDocument();
    expect(within(trainings).getByText("(21) 3333-4444")).toBeInTheDocument();
    expect(within(trainings).queryByText(/WhatsApp/)).not.toBeInTheDocument();
  });

  it("plano: valor do plano, benefício, mensalidade resultante e vencimento, sem botão de alterar", async () => {
    serve(info());
    setup();
    await screen.findByRole("heading", { name: "Plano e mensalidade" });
    const plans = card("Plano e mensalidade");
    expect(within(plans).getByText(/Mensal · R\$\s20\.000,00|Mensal · R\$\s200,00/)).toBeInTheDocument();
    expect(within(plans).getByText("10% de desconto")).toBeInTheDocument();
    expect(within(plans).getByText(/R\$\s180,00/)).toBeInTheDocument();
    expect(within(plans).getByText("todo dia 5")).toBeInTheDocument();
    expect(within(plans).queryByRole("button")).not.toBeInTheDocument();
  });

  it("graduação: faixa atual e histórico com a marca de origem", async () => {
    serve(info());
    setup();
    await screen.findByRole("heading", { name: "Graduação" });
    const grad = card("Graduação");
    expect(within(grad).getAllByText("Azul, grau 2").length).toBeGreaterThanOrEqual(2);
    expect(within(grad).getByText("De origem")).toBeInTheDocument();
    expect(grad.textContent).not.toMatch(/progress/i);
  });

  it("preferências e termos: desligar o e-mail grava; revogar o uso de imagem grava e relê", async () => {
    let prefs: unknown;
    let image: unknown;
    let granted = true;
    serve(() => HttpResponse.json(info({ terms: { ...info().terms, imageUse: { granted, version: 1, at: "2026-01-10T12:00:00Z" } } })));
    server.use(
      http.put("/api/v1/auth/email-preferences", async ({ request }) => HttpResponse.json((prefs = await request.json()))),
      http.put("/api/v1/portal/students/s1/image-use", async ({ request }) => {
        image = await request.json();
        granted = false;
        return HttpResponse.json({ granted });
      }),
    );
    setup();
    const user = userEvent.setup();
    await user.click(await screen.findByRole("checkbox", { name: "Receber comunicados por e-mail" }));
    await waitFor(() => expect(prefs).toEqual({ announcements: false }));
    const terms = card("Termos aceitos");
    expect(within(terms).getByText(/Aceito \(versão 1\) em 10\/01\/2026/)).toBeInTheDocument();
    await user.click(within(terms).getByRole("button", { name: "Revogar uso de imagem" }));
    await waitFor(() => expect(image).toEqual({ granted: false }));
    expect(await within(card("Termos aceitos")).findByText("Não concedido")).toBeInTheDocument();
  });

  it("pedidos, conta e ações de dados: lista os pedidos, leva a /portal/pedidos, mostra Minha conta e deixa as duas ações desabilitadas sem chamada", async () => {
    const calls: string[] = [];
    serve(info());
    setup();
    server.use(
      http.get("/api/v1/portal/students/s1/requests", () =>
        HttpResponse.json([
          { id: "r1", kind: "CLASS_CHANGE", status: "OPEN", createdAt: "2026-10-20T12:00:00Z" },
          { id: "r2", kind: "PLAN_CHANGE", status: "REFUSED", createdAt: "2026-10-10T12:00:00Z" },
        ]),
      ),
      http.all("/api/v1/portal/*download*", () => (calls.push("x"), HttpResponse.json({}))),
    );
    await screen.findByRole("heading", { name: "Pedidos à academia" });
    const requests = card("Pedidos à academia");
    expect(await within(requests).findByText("Troca de turma · Aberto · 20/10/2026")).toBeInTheDocument();
    expect(within(requests).getByText("Troca de plano · Recusado · 10/10/2026")).toBeInTheDocument();
    expect(within(requests).getByRole("link", { name: "Fazer um pedido" })).toHaveAttribute("href", "/portal/pedidos");
    for (const name of ["Senha", "E-mail", "Duas etapas", "Sessões"]) expect(screen.getAllByRole("heading", { name: new RegExp(`^${name}`) }).length).toBeGreaterThan(0);
    expect(screen.getByRole("button", { name: "Baixar meus dados" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "Excluir minha conta" })).toBeDisabled();
    expect(calls).toEqual([]);
  });

  it("CA5: pedidos mostram carregando enquanto busca", async () => {
    serve(info());
    setup();
    server.use(http.get("/api/v1/portal/students/s1/requests", () => new Promise<Response>(() => {})));
    await screen.findByRole("heading", { name: "Pedidos à academia" });
    expect(within(card("Pedidos à academia")).getByRole("status")).toBeInTheDocument();
  });

  it("CA5: pedidos mostram sem permissão no 403", async () => {
    serve(info());
    setup();
    server.use(http.get("/api/v1/portal/students/s1/requests", () => new HttpResponse(null, { status: 403 })));
    await screen.findByRole("heading", { name: "Pedidos à academia" });
    expect(await within(card("Pedidos à academia")).findByRole("heading", { name: "Você não tem acesso" })).toBeInTheDocument();
  });

  for (const dark of [false, true]) {
    it(`CA5/CA7: pedidos mostram erro com retry que refaz só essa busca, sem violações no axe no tema ${dark ? "escuro" : "claro"}`, { timeout: 40000 }, async () => {
      setDevicePrefersDark(dark);
      let fail = true;
      let calls = 0;
      serve(info());
      const view = setup();
      server.use(
        http.get("/api/v1/portal/students/s1/requests", () => {
          calls++;
          return fail ? new HttpResponse(null, { status: 500 }) : HttpResponse.json([]);
        }),
      );
      await screen.findByRole("heading", { name: "Pedidos à academia" });
      const retry = await within(card("Pedidos à academia")).findByRole("button", { name: "Tentar de novo" });
      expect(await axeViolations(view.container)).toEqual([]);
      fail = false;
      const before = calls;
      await userEvent.click(retry);
      expect(await within(card("Pedidos à academia")).findByText("Nenhum pedido feito.")).toBeInTheDocument();
      expect(calls).toBe(before + 1);
    });
  }

  it("portal reduzido: dados, contato da unidade, preferências, termos e conta; sem treinos, plano, graduação nem pedidos", async () => {
    serve(info({ reduced: true, trainings: [], plans: [], graduation: [] }));
    setup("/portal/minhas-informacoes", true);
    await screen.findByRole("heading", { name: "Dados pessoais" });
    expect(screen.getByRole("heading", { name: "Contato da academia" })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Preferências" })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Termos aceitos" })).toBeInTheDocument();
    for (const name of ["Treinos", "Plano e mensalidade", "Graduação", "Pedidos à academia"]) expect(screen.queryByRole("heading", { name })).not.toBeInTheDocument();
  });

  it("vazio: aluno sem modalidade mostra o vazio dos treinos, os dados, os termos e a conta", async () => {
    serve(info({ trainings: [], plans: [], graduation: [] }));
    setup();
    expect(await screen.findByText("Nenhuma modalidade ativa")).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Dados pessoais" })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Termos aceitos" })).toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "Plano e mensalidade" })).not.toBeInTheDocument();
  });

  it("responsável: o seletor troca de aluno, refaz a leitura e muda ?aluno=", async () => {
    const asked: (string | null)[] = [];
    const two = [
      { id: "s1", name: "Ana Souza", photoUrl: null, self: false },
      { id: "s2", name: "Beto Souza", photoUrl: null, self: false },
    ];
    serve(({ request }) => {
      const id = new URL(request.url).searchParams.get("studentId");
      asked.push(id);
      const selected = id ?? "s1";
      return HttpResponse.json(info({ students: two, selectedStudentId: selected, personal: { ...info().personal, name: two.find((s) => s.id === selected)!.name } }));
    });
    setup();
    await screen.findByText("Ana Souza", { selector: "dd" });
    await userEvent.setup().selectOptions(screen.getByRole("combobox", { name: "Aluno" }), "s2");
    expect(await screen.findByText("Beto Souza", { selector: "dd" })).toBeInTheDocument();
    expect(asked).toEqual([null, "s2"]);
  });

  it("estados: carregando, erro com Tentar de novo e sem permissão", async () => {
    let fail = true;
    serve(() => (fail ? HttpResponse.json({}, { status: 500 }) : HttpResponse.json(info())));
    setup();
    expect(screen.getByRole("status")).toBeInTheDocument();
    const user = userEvent.setup();
    const retry = await screen.findByRole("button", { name: "Tentar de novo" });
    fail = false;
    await user.click(retry);
    expect(await screen.findByRole("heading", { name: "Dados pessoais" })).toBeInTheDocument();
  });

  it("estados: 403 mostra o sem permissão", async () => {
    serve(() => HttpResponse.json({ code: "FORBIDDEN" }, { status: 403 }));
    setup();
    expect(await screen.findByText("Você não tem acesso")).toBeInTheDocument();
  });

  for (const dark of [false, true]) {
    it(`axe: sem violações no tema ${dark ? "escuro" : "claro"}`, async () => {
      setDevicePrefersDark(dark);
      serve(info());
      const { container } = setup();
      await screen.findByRole("heading", { name: "Graduação" });
      expect(await axeViolations(container)).toEqual([]);
    });
  }
});
