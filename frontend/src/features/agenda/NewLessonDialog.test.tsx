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
import { addDays, todayIso } from "./agendaDates";
import type { AgendaData, AgendaLesson } from "./useAgenda";
import type { LessonDetail } from "./useLesson";

const session = (role: string): SessionView => ({
  account: { id: "a", email: "x@x.com", name: "X" },
  links: [{ id: "l1", organizationId: "o", organizationName: "Alfa", role, unitIds: [] }],
  activeLinkId: "l1",
  twoFactor: { enabled: false, required: false },
});

const adhoc: AgendaLesson = {
  id: "av1",
  date: addDays(todayIso(), 1),
  startsAt: "10:00",
  endsAt: "11:30",
  status: "SCHEDULED",
  cancelReason: null,
  cancelReasonCode: null,
  class: null,
  title: "Seminário de quedas",
  unit: { id: "u1", name: "Centro" },
  modality: { id: "m1", name: "Judô" },
  teacher: { id: "t1", name: "Ana Souza" },
  substitute: false,
  canEdit: true,
  canCancel: true,
};
const agenda = (over: Partial<AgendaData> = {}): AgendaData => ({
  units: [{ id: "u1", name: "Centro" }],
  unit: { id: "u1", name: "Centro" },
  teachers: [{ id: "t1", name: "Ana Souza" }],
  modalities: [{ id: "m1", name: "Judô" }],
  lessons: [],
  noClassDays: [],
  ...over,
});
const panel = (over: Partial<LessonDetail> = {}): LessonDetail => ({
  ...adhoc,
  visibilityClass: null,
  head: null,
  activeStudents: 0,
  trialClasses: 0,
  groupLink: null,
  canViewClass: false,
  followingEdited: 0,
  ...over,
});

let posted: unknown;
function setup(opts: { role?: string; width?: number; view?: AgendaData; classes?: () => Response; create?: () => Response; path?: string; lesson?: LessonDetail } = {}) {
  posted = undefined;
  window.innerWidth = opts.width ?? 1280;
  const path = opts.path ?? "/agenda";
  window.history.replaceState(null, "", path);
  const role = opts.role ?? "COORDINATOR";
  server.use(
    http.get("/api/v1/auth/session", () => HttpResponse.json(session(role))),
    http.get("/api/v1/permissions", () => HttpResponse.json({ permissions: mockPermissions(role) })),
    http.get("/api/v1/units/reachable", () => HttpResponse.json({ allUnits: true, units: [{ id: "u1", name: "Centro", shortName: "c" }] })),
    http.get("/api/v1/agenda", () => HttpResponse.json(opts.view ?? agenda())),
    http.get("/api/v1/agenda/lessons/:id", () => HttpResponse.json(opts.lesson ?? panel())),
    http.get("/api/v1/classes", () => (opts.classes ? opts.classes() : HttpResponse.json({ items: [{ id: "c1", name: "Judô Infantil" }], page: 1, size: 100, total: 1 }))),
    http.post("/api/v1/agenda/lessons", async ({ request }) => {
      posted = await request.json();
      return opts.create ? opts.create() : HttpResponse.json(panel(), { status: 201 });
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

type User = ReturnType<typeof userEvent.setup>;
const open = async (user: User) => {
  await user.click(await screen.findByRole("button", { name: "Nova aula avulsa" }));
  return screen.findByRole("dialog", { name: "Nova aula avulsa" });
};
const fill = async (user: User, dialog: HTMLElement) => {
  await within(dialog).findByLabelText("Título");
  await user.type(within(dialog).getByLabelText("Título"), "Seminário de quedas");
  await user.selectOptions(within(dialog).getByLabelText("Modalidade"), "m1");
  await user.type(within(dialog).getByLabelText("Início"), "10:00");
  await user.type(within(dialog).getByLabelText("Término"), "11:30");
  await user.selectOptions(within(dialog).getByLabelText("Professor"), "t1");
};

describe("RF-AGE-04: Nova aula avulsa", () => {
  it("CA10: o botão abre a janela, salva no formato do contrato e fecha", async () => {
    const user = userEvent.setup();
    setup();
    const dialog = await open(user);
    await fill(user, dialog);
    await user.click(within(dialog).getByRole("button", { name: "Criar aula" }));
    await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull());
    expect(posted).toEqual({ unitId: "u1", title: "Seminário de quedas", modalityId: "m1", date: todayIso(), startsAt: "10:00", endsAt: "11:30", teacherId: "t1" });
  });

  it("CA10: escolher uma turma envia visibilityClassId", async () => {
    const user = userEvent.setup();
    setup();
    const dialog = await open(user);
    await fill(user, dialog);
    await user.click(await within(dialog).findByLabelText("Uma turma"));
    await user.selectOptions(within(dialog).getByLabelText("Turma"), "c1");
    await user.click(within(dialog).getByRole("button", { name: "Criar aula" }));
    await waitFor(() => expect(posted).toMatchObject({ visibilityClassId: "c1" }));
  });

  it("CA10: campos vazios e término antes do início são recusados sem chamar o servidor; o 400 com campo aparece na janela", async () => {
    const user = userEvent.setup();
    setup({ create: () => HttpResponse.json({ code: "VALIDATION_ERROR", fields: [{ field: "title", message: "de 1 a 80 caracteres" }] }, { status: 400 }) });
    const dialog = await open(user);
    await within(dialog).findByLabelText("Título");
    await user.click(within(dialog).getByRole("button", { name: "Criar aula" }));
    expect(await within(dialog).findByText("Informe o título, em até 80 caracteres.")).toBeInTheDocument();
    expect(within(dialog).getByText("Escolha a modalidade.")).toBeInTheDocument();
    expect(within(dialog).getByText("Escolha o professor.")).toBeInTheDocument();
    expect(posted).toBeUndefined();
    await fill(user, dialog);
    await user.clear(within(dialog).getByLabelText("Término"));
    await user.type(within(dialog).getByLabelText("Término"), "09:00");
    await user.click(within(dialog).getByRole("button", { name: "Criar aula" }));
    expect(await within(dialog).findByText("Informe início e término, com o término depois do início.")).toBeInTheDocument();
    expect(posted).toBeUndefined();
    await user.clear(within(dialog).getByLabelText("Término"));
    await user.type(within(dialog).getByLabelText("Término"), "11:30");
    await user.click(within(dialog).getByRole("button", { name: "Criar aula" }));
    expect(await within(dialog).findByText("de 1 a 80 caracteres")).toBeInTheDocument();
    expect(screen.getByRole("dialog")).toBeInTheDocument();
  });

  it.each([
    [403, "Você não tem permissão para criar esta aula. Nada foi gravado."],
    [422, "Algum dado não pode ser usado. Nada foi gravado."],
    [500, "Não foi possível criar a aula. Tente de novo. Nada foi gravado."],
  ])("CA10: erro %s sem campo aparece na janela, que fica aberta", async (status, text) => {
    const user = userEvent.setup();
    setup({ create: () => HttpResponse.json({ code: "X" }, { status }) });
    const dialog = await open(user);
    await fill(user, dialog);
    await user.click(within(dialog).getByRole("button", { name: "Criar aula" }));
    expect(await within(dialog).findByText(text)).toBeInTheDocument();
  });

  it("CA11: turmas com erro 500 têm Tentar de novo", async () => {
    const user = userEvent.setup();
    let fail = true;
    setup({ classes: () => (fail ? HttpResponse.json({ code: "X" }, { status: 500 }) : HttpResponse.json({ items: [], page: 1, size: 100, total: 0 })) });
    const dialog = await open(user);
    const retry = await within(dialog).findByRole("button", { name: "Tentar de novo" });
    fail = false;
    await user.click(retry);
    expect(await within(dialog).findByLabelText("Todas as turmas da unidade")).toBeChecked();
    expect(within(dialog).queryByLabelText("Uma turma")).toBeNull();
  });

  it("CA11: turmas 403 (Professor) mantém o formulário só com todas as turmas", async () => {
    const user = userEvent.setup();
    setup({ role: "TEACHER", classes: () => HttpResponse.json({ code: "FORBIDDEN" }, { status: 403 }) });
    const dialog = await open(user);
    expect(await within(dialog).findByLabelText("Todas as turmas da unidade")).toBeChecked();
    expect(within(dialog).queryByLabelText("Uma turma")).toBeNull();
  });

  it("CA11: unidade sem professor mostra o vazio; sem unidade não há botão", async () => {
    const user = userEvent.setup();
    const { unmount } = setup({ view: agenda({ teachers: [] }) });
    const dialog = await open(user);
    expect(await within(dialog).findByText("Ainda não dá para criar uma aula avulsa")).toBeInTheDocument();
    unmount();
    setup({ view: agenda({ unit: null, units: [] }) });
    await screen.findByRole("heading", { name: "Agenda", level: 1 });
    expect(screen.queryByRole("button", { name: "Nova aula avulsa" })).toBeNull();
  });

  it("CA11: sem AGENDA_EDIT não há botão", async () => {
    setup({ role: "TEACHER" });
    server.use(http.get("/api/v1/permissions", () => HttpResponse.json({ permissions: { ...mockPermissions("TEACHER"), AGENDA_EDIT: "NONE" } })));
    await screen.findByRole("heading", { name: "Agenda", level: 1 });
    expect(screen.queryByRole("button", { name: "Nova aula avulsa" })).toBeNull();
  });

  it.each([
    ["claro", false],
    ["escuro", true],
  ])("CA11: a 360 px a janela não rola na horizontal e não tem violações no axe no tema %s", async (_n, dark) => {
    const user = userEvent.setup();
    setDevicePrefersDark(dark);
    setup({ width: 360 });
    const dialog = await open(user);
    await within(dialog).findByLabelText("Título");
    expect(document.documentElement.scrollWidth).toBeLessThanOrEqual(document.documentElement.clientWidth || 360);
    expect(await axeViolations(document.body)).toEqual([]);
  });

  it("CA10: a lista mostra o título e a marca Avulsa", async () => {
    setup({ view: agenda({ lessons: [{ ...adhoc, date: todayIso() }] }), width: 360 });
    expect(await screen.findByText("Seminário de quedas")).toBeInTheDocument();
    expect(screen.getByText("Avulsa")).toBeInTheDocument();
  });

  it("CA10: o painel abre sem Ver turma, sem grupo e com a turma de visibilidade", async () => {
    setup({ path: "/agenda/aulas/av1", lesson: panel({ visibilityClass: { id: "c1", name: "Judô Infantil" } }) });
    expect(await screen.findByRole("heading", { name: "Seminário de quedas", level: 1 })).toBeInTheDocument();
    expect(screen.getByText("Avulsa")).toBeInTheDocument();
    expect(screen.getByText("Judô Infantil")).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Ver turma" })).toBeNull();
    expect(screen.queryByText("Grupo de WhatsApp")).toBeNull();
    expect(screen.queryByText("Alunos ativos da turma")).toBeNull();
    expect(screen.getByRole("button", { name: "Editar aula" })).toBeInTheDocument();
  });

  it("CA10: sem turma de visibilidade o painel mostra todas as turmas; editar usa o título e só oferece esta aula", async () => {
    const user = userEvent.setup();
    setup({ path: "/agenda/aulas/av1" });
    await screen.findByRole("heading", { name: "Seminário de quedas", level: 1 });
    expect(screen.getByText("Todas as turmas da unidade")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Editar aula" }));
    const dialog = await screen.findByRole("dialog", { name: "Editar aula" });
    await within(dialog).findByLabelText("Professor da aula");
    expect(within(dialog).getByText("Seminário de quedas")).toBeInTheDocument();
    expect(within(dialog).getByLabelText("Só esta aula")).toBeInTheDocument();
    expect(within(dialog).queryByLabelText(/Esta e as seguintes/)).toBeNull();
  });
});
