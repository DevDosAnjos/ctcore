import { render, screen } from "@testing-library/react";
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
import { todayIso } from "./agendaDates";
import type { AgendaData } from "./useAgenda";
import type { LessonDetail } from "./useLesson";

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

const detail = (over: Partial<LessonDetail> = {}): LessonDetail => ({
  id: "les1",
  date: "2026-03-10",
  startsAt: "19:00",
  endsAt: "20:00",
  status: "SCHEDULED",
  cancelReason: null,
  class: { id: "c1", name: "Jiu-Jitsu Adulto" },
  unit: { id: "u1", name: "Centro" },
  modality: { id: "m1", name: "Jiu-Jitsu" },
  teacher: { id: "t1", name: "Ana Souza" },
  head: null,
  substitute: false,
  activeStudents: 0,
  trialClasses: 0,
  groupLink: "https://chat.whatsapp.com/abc",
  canEdit: true,
  canCancel: true,
  canViewClass: true,
  ...over,
});

type Opts = {
  role?: string;
  width?: number;
  lesson?: LessonDetail;
  status?: number;
  path?: string;
};
function setup(opts: Opts = {}) {
  window.innerWidth = opts.width ?? 1280;
  const path = opts.path ?? "/agenda/aulas/les1";
  window.history.replaceState(null, "", path);
  const role = opts.role ?? "COORDINATOR";
  server.use(
    http.get("/api/v1/auth/session", () => HttpResponse.json(session(role))),
    http.get("/api/v1/permissions", () =>
      HttpResponse.json({ permissions: mockPermissions(role) }),
    ),
    http.get("/api/v1/units/reachable", () =>
      HttpResponse.json({
        allUnits: true,
        units: [{ id: "u1", name: "Centro", shortName: "c" }],
      }),
    ),
    http.get("/api/v1/agenda/lessons/:id", () =>
      opts.status
        ? HttpResponse.json({ code: "X" }, { status: opts.status })
        : HttpResponse.json(opts.lesson ?? detail()),
    ),
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

const ready = () =>
  screen.findByRole("heading", { name: "Jiu-Jitsu Adulto", level: 1 });

describe("LessonPanelPage", () => {
  it("CA9, CA6: horário, turma, modalidade, professor, alunos, experimentais, grupo e ações do Coordenador", async () => {
    setup();
    await ready();
    expect(
      screen.getByText("10/03/2026, das 19:00 às 20:00"),
    ).toBeInTheDocument();
    expect(screen.getByText("Centro")).toBeInTheDocument();
    expect(screen.getByText("Jiu-Jitsu")).toBeInTheDocument();
    expect(screen.getByText("Ana Souza")).toBeInTheDocument();
    expect(
      screen.getByText("Alunos ativos da turma").nextElementSibling,
    ).toHaveTextContent("0");
    expect(
      screen.getByText("Aulas experimentais marcadas").nextElementSibling,
    ).toHaveTextContent("0");
    expect(screen.queryByText("Substituição")).toBeNull();
    const group = screen.getByRole("link", { name: "Abrir grupo" });
    expect(group).toHaveAttribute("href", "https://chat.whatsapp.com/abc");
    expect(group).toHaveAttribute("target", "_blank");
    expect(screen.getByRole("link", { name: "Ver turma" })).toHaveAttribute(
      "href",
      "/turmas/c1",
    );
    expect(screen.getByRole("button", { name: "Avisar turma" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "Editar aula" })).toBeDisabled();
    expect(
      screen.getByRole("button", { name: "Cancelar aula" }),
    ).toBeDisabled();
    expect(
      screen.getByRole("link", { name: "Voltar à agenda" }),
    ).toHaveAttribute("href", "/agenda");
  });

  it("CA9: substituição mostra o selo e o titular; cancelada mostra a situação e o motivo, sem Cancelar aula nem Editar aula", async () => {
    setup({
      lesson: detail({
        substitute: true,
        head: { id: "t2", name: "João Pereira" },
        status: "CANCELLED",
        cancelReason: "Chuva forte",
        canEdit: false,
        canCancel: false,
      }),
    });
    await ready();
    expect(screen.getByText("Substituição")).toBeInTheDocument();
    expect(screen.getByText("João Pereira")).toBeInTheDocument();
    expect(screen.getAllByText("Cancelada").length).toBeGreaterThan(0);
    expect(screen.getByText("Chuva forte")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Editar aula" })).toBeNull();
    expect(screen.queryByRole("button", { name: "Cancelar aula" })).toBeNull();
  });

  it("CA6: Professor não vê Ver turma; link de grupo inseguro ou ausente não vira link", async () => {
    setup({
      role: "TEACHER",
      lesson: detail({ canViewClass: false, groupLink: "javascript:alert(1)" }),
    });
    await ready();
    expect(screen.queryByRole("link", { name: "Ver turma" })).toBeNull();
    expect(screen.queryByRole("link", { name: "Abrir grupo" })).toBeNull();
    expect(
      screen.getByText("A turma não tem grupo cadastrado."),
    ).toBeInTheDocument();
  });

  it("CA10: carregando, erro com Tentar de novo, sem permissão e aula não encontrada", async () => {
    const user = userEvent.setup();
    let fail = true;
    setup();
    server.use(
      http.get("/api/v1/agenda/lessons/:id", () =>
        fail
          ? HttpResponse.json({ code: "INTERNAL_ERROR" }, { status: 500 })
          : HttpResponse.json(detail()),
      ),
    );
    expect(await screen.findByText("Carregando...")).toBeInTheDocument();
    const retry = await screen.findByRole("button", { name: "Tentar de novo" });
    fail = false;
    await user.click(retry);
    await ready();
  });

  it("CA10: 403 e 404", async () => {
    const { unmount } = setup({ status: 403 });
    expect(await screen.findByText("Você não tem acesso")).toBeInTheDocument();
    unmount();
    setup({ status: 404 });
    expect(await screen.findByText("Aula não encontrada")).toBeInTheDocument();
    expect(
      screen.getByRole("link", { name: "Voltar à agenda" }),
    ).toBeInTheDocument();
  });

  it.each([
    ["claro", false],
    ["escuro", true],
  ])("CA10: a 360 px não tem violações no axe no tema %s", async (_n, dark) => {
    setDevicePrefersDark(dark);
    const { container } = setup({
      width: 360,
      lesson: detail({
        substitute: true,
        head: { id: "t2", name: "João Pereira" },
      }),
    });
    await ready();
    expect(document.documentElement.scrollWidth).toBeLessThanOrEqual(
      document.documentElement.clientWidth || 360,
    );
    expect(await axeViolations(container)).toEqual([]);
  });

  it("CA9: no celular, o cartão da lista do dia leva ao painel", async () => {
    const day = todayIso();
    const view: AgendaData = {
      units: [{ id: "u1", name: "Centro" }],
      unit: { id: "u1", name: "Centro" },
      teachers: [],
      modalities: [],
      lessons: [
        {
          ...detail({ id: "x1", date: day }),
          teacher: { id: "t1", name: "Ana Souza" },
        },
      ],
      noClassDays: [],
    };
    server.use(http.get("/api/v1/agenda", () => HttpResponse.json(view)));
    setup({ width: 360, path: "/agenda" });
    const card = await screen.findByRole("link", { name: /Jiu-Jitsu Adulto/ });
    expect(card).toHaveAttribute("href", "/agenda/aulas/x1");
  });
});
