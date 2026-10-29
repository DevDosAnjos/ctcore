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
import type { PortalScheduleData, ScheduleLesson } from "./usePortalSchedule";

const session: SessionView = {
  account: { id: "a", email: "x@x.com", name: "Ana Silva" },
  links: [{ id: "l1", organizationId: "o", organizationName: "Alfa", role: "GUARDIAN", unitIds: [] }],
  activeLinkId: "l1",
  twoFactor: { enabled: false, required: false },
};
const jiu = { id: "m1", name: "Jiu-Jitsu" };
const centro = { id: "u1", name: "Centro" };
const lesson = (over: Partial<ScheduleLesson>): ScheduleLesson => ({
  id: "l0",
  date: "2026-10-27",
  startsAt: "19:00",
  endsAt: "20:00",
  title: "Jiu-Jitsu Adulto",
  kind: "MAIN",
  status: "SCHEDULED",
  cancelReasonCode: null,
  cancelReason: null,
  changed: false,
  originalDate: null,
  originalStartsAt: null,
  substitute: false,
  past: false,
  noticeId: null,
  modality: jiu,
  unit: centro,
  teacherName: "Prof. Carlos Teste",
  ...over,
});
const schedule = (over: Partial<PortalScheduleData> = {}): PortalScheduleData => ({
  reduced: false,
  students: [{ id: "s1", name: "Ana Souza", self: true }],
  studentId: "s1",
  scope: "ALL",
  weekStart: "2026-10-26",
  weekEnd: "2026-11-01",
  today: "2026-10-27",
  timezone: "America/Sao_Paulo",
  lessons: [
    lesson({ id: "a", date: "2026-10-26", startsAt: "18:00", endsAt: "19:00", title: "Jiu-Jitsu Manhã", past: true, kind: "OTHER" }),
    lesson({ id: "b", changed: true, originalDate: "2026-10-26", originalStartsAt: "18:00", noticeId: "n1" }),
    lesson({ id: "c", date: "2026-10-29", status: "CANCELLED", cancelReasonCode: "HOLIDAY", title: "Judô Adulto" }),
    lesson({ id: "d", date: "2026-10-30", status: "CANCELLED", cancelReasonCode: "OTHER", cancelReason: "Reforma <b>do</b> tatame", title: "Judô Noite" }),
    lesson({ id: "e", date: "2026-10-31", kind: "ADHOC", title: "Seminário", substitute: true, changed: true, teacherName: "Prof. Marta Teste" }),
  ],
  noClassDays: [{ date: "2026-10-28", reason: "Feriado municipal", unit: centro }],
  ...over,
});
const serve = (data: PortalScheduleData | ((info: { request: Request }) => Response | Promise<Response>)) =>
  server.use(http.get("/api/v1/portal/schedule", typeof data === "function" ? data : () => HttpResponse.json(data)));

function viewport(wide: boolean) {
  window.matchMedia = ((query: string) => ({
    matches: wide && query.includes("min-width"),
    media: query,
    onchange: null,
    addEventListener: () => {},
    removeEventListener: () => {},
    addListener: () => {},
    removeListener: () => {},
    dispatchEvent: () => false,
  })) as typeof window.matchMedia;
}

function setup(path = "/portal/agenda") {
  server.use(
    http.get("/api/v1/auth/session", () => HttpResponse.json(session)),
    http.get("/api/v1/portal/term", () => HttpResponse.json([])),
    http.get("/api/v1/portal/students", () => HttpResponse.json([])),
    http.get("/api/v1/portal/shell", () => HttpResponse.json({ organization: { name: "Academia Alfa", color: null, logoUrl: null }, reduced: false, unreadAnnouncements: 0 })),
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

describe("PortalAgendaPage (RF-POR-04)", () => {
  beforeEach(() => viewport(false));

  it("CA3 a CA7, CA13: no celular, lista do dia começando em hoje, com Alterada, resumo, Ver aviso, Já aconteceu, Sem aula e Cancelada com o motivo", async () => {
    serve(schedule());
    const user = userEvent.setup();
    setup();
    const panel = await screen.findByRole("tabpanel");
    expect(screen.getByRole("tab", { name: /Ter/, selected: true })).toBeInTheDocument();
    expect(within(panel).getByText("Alterada")).toBeInTheDocument();
    expect(within(panel).getByText("Professor: Prof. Carlos Teste")).toBeInTheDocument();
    expect(within(panel).getByText("Era em 26/10/2026 às 18:00")).toBeInTheDocument();
    expect(within(panel).getByRole("link", { name: "Ver aviso sobre Jiu-Jitsu Adulto" })).toHaveAttribute("href", "/portal/avisos?aviso=n1");
    expect(within(panel).queryByText("Já aconteceu")).not.toBeInTheDocument();

    await user.click(screen.getByRole("tab", { name: /Seg/ }));
    expect(within(screen.getByRole("tabpanel")).getByText("Já aconteceu")).toBeInTheDocument();
    expect(within(screen.getByRole("tabpanel")).getByText("Outro horário")).toBeInTheDocument();

    await user.click(screen.getByRole("tab", { name: /Qua/ }));
    expect(within(screen.getByRole("tabpanel")).getByText("Sem aula")).toBeInTheDocument();
    expect(within(screen.getByRole("tabpanel")).getByText("Feriado municipal")).toBeInTheDocument();

    await user.click(screen.getByRole("tab", { name: /Qui/ }));
    expect(within(screen.getByRole("tabpanel")).getByText("Cancelada")).toBeInTheDocument();
    expect(within(screen.getByRole("tabpanel")).getByText("Motivo: Feriado")).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: /Ver aviso/ })).not.toBeInTheDocument();

    await user.click(screen.getByRole("tab", { name: /Sex/ }));
    expect(within(screen.getByRole("tabpanel")).getByText("Motivo: Reforma <b>do</b> tatame")).toBeInTheDocument();

    await user.click(screen.getByRole("tab", { name: /Sáb/ }));
    expect(within(screen.getByRole("tabpanel")).getByText("Avulsa")).toBeInTheDocument();
    expect(within(screen.getByRole("tabpanel")).getByText("Professor substituto: Prof. Marta Teste")).toBeInTheDocument();
    expect(document.body.textContent).not.toMatch(/presen|frequ|progress|espera|R\$/i);
  });

  it("CA13: a partir de 768 px, grade de 7 colunas com as aulas por horário e o dia sem aula sombreado com texto; sem a lista do dia", async () => {
    viewport(true);
    serve(schedule());
    setup();
    const grid = await screen.findByRole("table", { name: "Aulas da semana" });
    expect(within(grid).getAllByRole("columnheader")).toHaveLength(8);
    expect(within(grid).getByRole("columnheader", { name: /Qua 28\/10/ })).toHaveTextContent("Sem aula");
    expect(within(grid).getByRole("columnheader", { name: /Qua 28\/10/ })).toHaveTextContent("Feriado municipal");
    expect(within(grid).getAllByRole("rowheader").map((r) => r.textContent)).toEqual(["18:00", "19:00"]);
    expect(screen.queryByRole("tablist")).not.toBeInTheDocument();
  });

  it("CA14: semana anterior, próxima e Hoje refazem a leitura; o escopo e o aluno mantêm a semana", async () => {
    const asked: string[] = [];
    serve(({ request }) => {
      asked.push(new URL(request.url).search);
      return HttpResponse.json(
        schedule({
          students: [
            { id: "s1", name: "Ana Souza", self: false },
            { id: "s2", name: "Beto Souza", self: false },
          ],
        }),
      );
    });
    const user = userEvent.setup();
    setup();
    await screen.findByRole("tabpanel");
    expect(screen.getByText("Semana de 26/10/2026 a 01/11/2026")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Próxima semana" }));
    await waitFor(() => expect(asked.at(-1)).toBe("?week=2026-11-02"));
    await user.click(screen.getByRole("button", { name: "Só minha turma" }));
    await waitFor(() => expect(asked.at(-1)).toBe("?week=2026-11-02&scope=MAIN"));
    expect(screen.getByRole("button", { name: "Só minha turma" })).toHaveAttribute("aria-pressed", "true");
    await user.selectOptions(screen.getByLabelText("Aluno"), "s2");
    await waitFor(() => expect(asked.at(-1)).toBe("?studentId=s2&week=2026-11-02&scope=MAIN"));
    await user.click(screen.getByRole("button", { name: "Semana anterior" }));
    await waitFor(() => expect(asked.at(-1)).toBe("?studentId=s2&week=2026-10-19&scope=MAIN"));
    await user.click(screen.getByRole("button", { name: "Hoje" }));
    await waitFor(() => expect(asked.at(-1)).toBe("?studentId=s2&scope=MAIN"));
  });

  it("CA14: ?aluno=, ?semana= e ?escopo= na URL chegam à leitura", async () => {
    const asked: string[] = [];
    serve(({ request }) => {
      asked.push(new URL(request.url).search);
      return HttpResponse.json(schedule());
    });
    setup("/portal/agenda?aluno=s2&semana=2026-11-02&escopo=MAIN");
    await screen.findByRole("tabpanel");
    expect(asked).toEqual(["?studentId=s2&week=2026-11-02&scope=MAIN"]);
  });

  it("CA15: semana sem aulas mostra o estado vazio; a semana sem hoje começa na segunda", async () => {
    serve(schedule({ lessons: [], noClassDays: [] }));
    setup();
    expect(await screen.findByText("Nenhuma aula nesta semana")).toBeInTheDocument();
  });

  it("CA13: semana que não tem hoje abre na segunda-feira", async () => {
    serve(schedule({ today: "2026-10-20" }));
    setup();
    await screen.findByRole("tabpanel");
    expect(screen.getByRole("tab", { name: /Seg/, selected: true })).toBeInTheDocument();
  });

  it("CA15: carregando, erro com Tentar de novo e sem permissão", async () => {
    serve(() => new Promise<Response>(() => {}));
    const loading = setup();
    expect((await screen.findAllByRole("status")).length).toBeGreaterThan(0);
    loading.unmount();

    let calls = 0;
    serve(() => (++calls === 1 ? new HttpResponse(null, { status: 500 }) : HttpResponse.json(schedule())));
    const user = userEvent.setup();
    const failed = setup();
    await user.click(await screen.findByRole("button", { name: "Tentar de novo" }));
    expect(await screen.findByRole("tabpanel")).toBeInTheDocument();
    expect(calls).toBe(2);
    failed.unmount();

    serve(() => new HttpResponse(null, { status: 403 }));
    setup();
    expect(await screen.findByText("Você não tem acesso")).toBeInTheDocument();
  });

  for (const dark of [false, true]) {
    for (const wide of [false, true]) {
      it(`CA15: sem violações de acessibilidade no tema ${dark ? "escuro" : "claro"}, ${wide ? "grade" : "lista"}`, async () => {
        setDevicePrefersDark(dark);
        viewport(wide);
        serve(schedule());
        const { container } = setup();
        await screen.findByText("Semana de 26/10/2026 a 01/11/2026");
        await waitFor(async () => expect(await axeViolations(container)).toEqual([]));
      });
    }
  }
});
