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
import type { ClassDetail, ClassOptions } from "./useClasses";

const session = (role: string): SessionView => ({
  account: { id: "a", email: "x@x.com", name: "X" },
  links: [{ id: "l1", organizationId: "o", organizationName: "Alfa", role, unitIds: [] }],
  activeLinkId: "l1",
  twoFactor: { enabled: false, required: false },
});
const options: ClassOptions = {
  units: [
    { id: "u1", name: "Centro", modalities: [{ id: "m1", name: "Jiu-Jitsu" }, { id: "m2", name: "Judô" }], teachers: [{ id: "t1", name: "Ana Souza" }, { id: "t2", name: "João Lima" }] },
    { id: "u2", name: "Norte", modalities: [{ id: "m2", name: "Judô" }], teachers: [{ id: "t3", name: "Marcos Dias" }] },
  ],
  ageRanges: [{ id: "r1", name: "Adulto", minAge: 18, maxAge: null }],
};
const detail = (over: Partial<ClassDetail> = {}): ClassDetail => ({
  id: "c1",
  name: "Jiu-Jitsu Adulto",
  unit: { id: "u1", name: "Centro" },
  modality: { id: "m1", name: "Jiu-Jitsu" },
  ageRange: { id: "r1", name: "Adulto", minAge: 18, maxAge: null },
  schedule: [{ weekday: 1, startsAt: "19:00", endsAt: "20:00" }],
  head: { id: "t1", name: "Ana Souza" },
  assistants: [{ id: "t2", name: "João Lima" }],
  activeStudents: 0,
  capacity: 30,
  overCapacity: false,
  status: "ACTIVE",
  groupLink: "https://chat.example.com/grupo",
  nextClass: null,
  students: [],
  deactivation: { allowed: true, blockingStudents: 0 },
  ...over,
});

let sent: { method: string; url: string; body: Record<string, unknown> }[];
let current: ClassDetail;

function setup(opts: { role?: string; path?: string; klass?: ClassDetail; optionsResponse?: () => Response | Promise<Response> } = {}) {
  const role = opts.role ?? "COORDINATOR";
  const path = opts.path ?? "/turmas";
  sent = [];
  current = opts.klass ?? detail();
  server.use(
    http.get("/api/v1/auth/session", () => HttpResponse.json(session(role))),
    http.get("/api/v1/permissions", () => HttpResponse.json({ permissions: mockPermissions(role) })),
    http.get("/api/v1/units/reachable", () => HttpResponse.json({ allUnits: true, units: [{ id: "u1", name: "Centro", shortName: "c" }] })),
    http.get("/api/v1/modalities", () => HttpResponse.json([])),
    http.get("/api/v1/teachers", () => HttpResponse.json({ items: [], page: 1, size: 100, total: 0 })),
    http.get("/api/v1/age-ranges", () => HttpResponse.json({ items: [] })),
    http.get("/api/v1/classes", () => HttpResponse.json({ items: [], page: 1, size: 25, total: 0 })),
    http.get("/api/v1/classes/options", () => (opts.optionsResponse ? opts.optionsResponse() : HttpResponse.json(options))),
    http.get("/api/v1/classes/c1", () => HttpResponse.json(current)),
    http.get("/api/v1/classes/n1", () => HttpResponse.json(detail({ id: "n1", name: "Muay Thai Noite" }))),
    http.post("/api/v1/classes", async ({ request }) => {
      sent.push({ method: "POST", url: "/classes", body: (await request.json()) as Record<string, unknown> });
      return HttpResponse.json(detail({ id: "n1", name: "Muay Thai Noite" }), { status: 201 });
    }),
    http.put("/api/v1/classes/c1", async ({ request }) => {
      const body = (await request.json()) as Record<string, unknown>;
      sent.push({ method: "PUT", url: "/classes/c1", body });
      current = detail({ name: body.name as string });
      return HttpResponse.json({ ...current, scheduleChanged: true });
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

const openNew = async (user: ReturnType<typeof userEvent.setup>) => {
  await user.click(await screen.findByRole("button", { name: "Nova turma" }));
  return screen.findByRole("dialog", { name: "Nova turma" });
};
const openEdit = async (user: ReturnType<typeof userEvent.setup>) => {
  await screen.findByRole("heading", { name: current.name, level: 1 });
  await waitFor(() => expect(screen.getByRole("button", { name: "Editar" })).toBeEnabled());
  await user.click(screen.getByRole("button", { name: "Editar" }));
  return screen.findByRole("dialog", { name: "Editar turma" });
};

describe("ClassFormDialog (RF-TUR-03, CA13)", () => {
  it("cria: exige unidade, modalidade, titular e um horário; envia o corpo do contrato e leva ao painel", async () => {
    const user = userEvent.setup();
    setup();
    const dialog = await openNew(user);
    await within(dialog).findByLabelText("Nome da turma");
    await user.click(within(dialog).getByRole("button", { name: "Salvar" }));
    for (const text of ["Informe o nome, de 2 a 80 caracteres.", "Escolha a unidade.", "Escolha a modalidade.", "Escolha o professor titular.", "Marque ao menos um dia com horário."]) {
      expect(within(dialog).getByText(text)).toBeInTheDocument();
    }
    expect(sent).toEqual([]);

    await user.type(within(dialog).getByLabelText("Nome da turma"), "  Muay Thai Noite ");
    await user.selectOptions(within(dialog).getByLabelText("Unidade"), "Centro");
    await user.selectOptions(within(dialog).getByLabelText("Modalidade"), "Judô");
    await user.selectOptions(within(dialog).getByLabelText("Professor titular"), "Ana Souza");
    await user.click(within(dialog).getByRole("checkbox", { name: "João Lima" }));
    await user.click(within(dialog).getByRole("checkbox", { name: "Sexta-feira" }));
    await user.type(within(dialog).getByLabelText("Capacidade"), "25");
    await user.click(within(dialog).getByRole("button", { name: "Salvar" }));

    expect(await screen.findByRole("heading", { name: "Muay Thai Noite", level: 1 })).toBeInTheDocument();
    expect(sent).toEqual([
      {
        method: "POST",
        url: "/classes",
        body: {
          name: "Muay Thai Noite",
          modalityId: "m2",
          ageRangeId: null,
          schedule: [{ weekday: 5, startsAt: "19:00", endsAt: "20:00" }],
          capacity: 25,
          headTeacherId: "t1",
          assistantIds: ["t2"],
          groupLink: null,
          unitId: "u1",
        },
      },
    ]);
  });

  it("trocar a unidade limpa modalidade e titular que ela não tem; validações de horário, capacidade e link", async () => {
    const user = userEvent.setup();
    setup();
    const dialog = await openNew(user);
    await user.selectOptions(await within(dialog).findByLabelText("Unidade"), "Centro");
    await user.selectOptions(within(dialog).getByLabelText("Modalidade"), "Jiu-Jitsu");
    await user.selectOptions(within(dialog).getByLabelText("Professor titular"), "Ana Souza");
    await user.selectOptions(within(dialog).getByLabelText("Unidade"), "Norte");
    expect(within(dialog).getByLabelText("Modalidade")).toHaveValue("");
    expect(within(dialog).getByLabelText("Professor titular")).toHaveValue("");
    await user.type(within(dialog).getByLabelText("Nome da turma"), "Judô Norte");
    await user.selectOptions(within(dialog).getByLabelText("Modalidade"), "Judô");
    await user.selectOptions(within(dialog).getByLabelText("Professor titular"), "Marcos Dias");
    await user.click(within(dialog).getByRole("checkbox", { name: "Segunda-feira" }));
    await user.clear(within(dialog).getByLabelText("Término de Segunda-feira"));
    await user.type(within(dialog).getByLabelText("Término de Segunda-feira"), "18:00");
    await user.type(within(dialog).getByLabelText("Capacidade"), "0");
    await user.type(within(dialog).getByLabelText("Link do grupo"), "javascript:alert(1)");
    await user.click(within(dialog).getByRole("button", { name: "Salvar" }));
    expect(within(dialog).getByText("O término deve ser depois do início.")).toBeInTheDocument();
    expect(within(dialog).getByText("Informe um número de 1 a 200.")).toBeInTheDocument();
    expect(within(dialog).getByText("Informe um link começando com http:// ou https://.")).toBeInTheDocument();
    expect(sent).toEqual([]);
  });

  it("erro 400 aparece no campo e 409 de nome aparece no campo nome", async () => {
    const user = userEvent.setup();
    setup();
    const fill = async (dialog: HTMLElement) => {
      await user.type(await within(dialog).findByLabelText("Nome da turma"), "Muay Thai Noite");
      await user.selectOptions(within(dialog).getByLabelText("Unidade"), "Centro");
      await user.selectOptions(within(dialog).getByLabelText("Modalidade"), "Judô");
      await user.selectOptions(within(dialog).getByLabelText("Professor titular"), "Ana Souza");
      await user.click(within(dialog).getByRole("checkbox", { name: "Sexta-feira" }));
    };
    server.use(http.post("/api/v1/classes", () => HttpResponse.json({ code: "VALIDATION_ERROR", fields: [{ field: "headTeacherId", message: "professor inativo" }] }, { status: 400 })));
    const dialog = await openNew(user);
    await fill(dialog);
    await user.click(within(dialog).getByRole("button", { name: "Salvar" }));
    expect(await within(dialog).findByText("professor inativo")).toBeInTheDocument();
    expect(within(dialog).getByLabelText("Professor titular")).toHaveAttribute("aria-invalid", "true");

    server.use(http.post("/api/v1/classes", () => HttpResponse.json({ code: "CONFLICT", fields: [{ field: "name", message: "já existe uma turma ativa com este nome na unidade" }] }, { status: 409 })));
    await user.click(within(dialog).getByRole("button", { name: "Salvar" }));
    expect(await within(dialog).findByText("já existe uma turma ativa com este nome na unidade")).toBeInTheDocument();
    expect(within(dialog).getByLabelText("Nome da turma")).toHaveAttribute("aria-invalid", "true");

    server.use(http.post("/api/v1/classes", () => HttpResponse.json({ code: "INTERNAL_ERROR" }, { status: 500 })));
    await user.click(within(dialog).getByRole("button", { name: "Salvar" }));
    expect(await within(dialog).findByText("Não foi possível salvar. Nada foi alterado. Tente de novo.")).toBeInTheDocument();
  });

  it("edita: abre preenchido com a unidade fixa; mudar horário mostra Avisar a turma marcada e voltar esconde; salva e o painel atualiza", async () => {
    const user = userEvent.setup();
    setup({ path: "/turmas/c1" });
    const dialog = await openEdit(user);
    expect(await within(dialog).findByLabelText("Nome da turma")).toHaveValue("Jiu-Jitsu Adulto");
    expect(within(dialog).queryByRole("combobox", { name: "Unidade" })).toBeNull();
    expect(within(dialog).getByText("Centro")).toBeInTheDocument();
    expect(within(dialog).getByLabelText("Modalidade")).toHaveValue("m1");
    expect(within(dialog).getByLabelText("Faixa etária")).toHaveValue("r1");
    expect(within(dialog).getByLabelText("Capacidade")).toHaveValue(30);
    expect(within(dialog).getByLabelText("Professor titular")).toHaveValue("t1");
    expect(within(dialog).getByRole("checkbox", { name: "João Lima" })).toBeChecked();
    expect(within(dialog).getByLabelText("Link do grupo")).toHaveValue("https://chat.example.com/grupo");
    expect(within(dialog).getByLabelText("Início de Segunda-feira")).toHaveValue("19:00");
    expect(within(dialog).queryByRole("checkbox", { name: "Avisar a turma" })).toBeNull();

    const end = within(dialog).getByLabelText("Término de Segunda-feira");
    await user.clear(end);
    await user.type(end, "21:00");
    expect(within(dialog).getByRole("checkbox", { name: "Avisar a turma" })).toBeChecked();
    await user.clear(end);
    await user.type(end, "20:00");
    expect(within(dialog).queryByRole("checkbox", { name: "Avisar a turma" })).toBeNull();
    await user.clear(end);
    await user.type(end, "21:00");
    await user.click(within(dialog).getByRole("checkbox", { name: "Avisar a turma" }));
    await user.clear(within(dialog).getByLabelText("Nome da turma"));
    await user.type(within(dialog).getByLabelText("Nome da turma"), "Jiu-Jitsu Noite");
    await user.click(within(dialog).getByRole("button", { name: "Salvar" }));
    expect(await screen.findByRole("heading", { name: "Jiu-Jitsu Noite", level: 1 })).toBeInTheDocument();
    expect(sent[0].body).toMatchObject({ name: "Jiu-Jitsu Noite", notifyClass: false, schedule: [{ weekday: 1, startsAt: "19:00", endsAt: "21:00" }], assistantIds: ["t2"] });
    expect(sent[0].body).not.toHaveProperty("unitId");
  });

  it("edição sem mudar horários não envia notifyClass", async () => {
    const user = userEvent.setup();
    setup({ path: "/turmas/c1" });
    const dialog = await openEdit(user);
    await within(dialog).findByLabelText("Nome da turma");
    await user.click(within(dialog).getByRole("button", { name: "Salvar" }));
    await waitFor(() => expect(sent).toHaveLength(1));
    expect(sent[0].body).not.toHaveProperty("notifyClass");
  });

  it("titular inativo atual não vem como escolha e o campo pede novo titular", async () => {
    const user = userEvent.setup();
    setup({ path: "/turmas/c1", klass: detail({ head: { id: "t9", name: "Inativo Silva" } }) });
    const dialog = await openEdit(user);
    expect(await within(dialog).findByLabelText("Professor titular")).toHaveValue("");
    expect(within(dialog).queryByRole("option", { name: "Inativo Silva" })).toBeNull();
    expect(within(dialog).getByText("Inativo Silva não é mais um professor ativo desta unidade. Escolha um novo titular.")).toBeInTheDocument();
    await user.click(within(dialog).getByRole("button", { name: "Salvar" }));
    expect(sent).toEqual([]);
  });

  it("opções: com erro oferece Tentar de novo; carregando mostra Carregando; 403 mostra sem permissão", async () => {
    const user = userEvent.setup();
    let fail = true;
    const { unmount } = setup({ optionsResponse: () => (fail ? HttpResponse.json({ code: "INTERNAL_ERROR" }, { status: 500 }) : HttpResponse.json(options)) });
    const dialog = await openNew(user);
    const retry = await within(dialog).findByRole("button", { name: "Tentar de novo" });
    fail = false;
    await user.click(retry);
    expect(await within(dialog).findByLabelText("Nome da turma")).toBeInTheDocument();
    unmount();

    const second = setup({ optionsResponse: () => new Promise(() => undefined) });
    const loading = await openNew(user);
    expect(within(loading).getByText("Carregando...")).toBeInTheDocument();
    second.unmount();

    setup({ optionsResponse: () => HttpResponse.json({ code: "FORBIDDEN" }, { status: 403 }) });
    const denied = await openNew(user);
    expect(await within(denied).findByText("Você não tem acesso")).toBeInTheDocument();
  });

  it("quem não tem CLASSES_MANAGE não vê Nova turma", async () => {
    setup({ role: "TEACHER" });
    await waitFor(() => expect(screen.queryByText("Carregando...")).toBeNull());
    expect(screen.queryByRole("button", { name: "Nova turma" })).toBeNull();
  });

  it("Editar fica desativado em turma desativada", async () => {
    setup({ path: "/turmas/c1", klass: detail({ status: "INACTIVE" }) });
    await screen.findByRole("heading", { name: "Jiu-Jitsu Adulto", level: 1 });
    expect(screen.getByRole("button", { name: "Editar" })).toBeDisabled();
  });

  it.each([
    ["claro", false],
    ["escuro", true],
  ])("a 360 px a janela não tem violações no axe no tema %s", async (_n, dark) => {
    setDevicePrefersDark(dark);
    window.innerWidth = 360;
    const user = userEvent.setup();
    setup({ path: "/turmas/c1" });
    const dialog = await openEdit(user);
    await within(dialog).findByLabelText("Nome da turma");
    expect(await axeViolations(dialog)).toEqual([]);
  });
});
