import { fireEvent, render, screen, within } from "@testing-library/react";
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
  cancelReasonCode: null,
  class: { id: "c1", name: "Jiu-Jitsu Adulto" },
  title: null,
  unit: { id: "u1", name: "Centro" },
  modality: { id: "m1", name: "Jiu-Jitsu" },
  teacher: { id: "t1", name: "Ana Souza" },
  visibilityClass: null,
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
    expect(screen.getByRole("button", { name: "Editar aula" })).toBeEnabled();
    expect(screen.getByRole("button", { name: "Cancelar aula" })).toBeEnabled();
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

  describe("RF-AGE-03: janelas Editar aula e Cancelar aula", () => {
    const teachers = [
      { id: "t1", name: "Ana Souza" },
      { id: "t2", name: "João Pereira" },
    ];
    const stubTeachers = (status = 200) =>
      server.use(
        http.get("/api/v1/agenda", () =>
          status === 200
            ? HttpResponse.json({
                units: [],
                unit: { id: "u1", name: "Centro" },
                teachers,
                modalities: [],
                lessons: [],
                noClassDays: [],
              })
            : HttpResponse.json({ code: "X" }, { status }),
        ),
      );
    // O painel lê a aula de novo depois de gravar: `current` troca quando a gravação responde.
    const stateful = (changed: LessonDetail, initial: LessonDetail = detail()) => {
      let current = initial;
      const sent: unknown[] = [];
      server.use(
        http.get("/api/v1/agenda/lessons/:id", () => HttpResponse.json(current)),
        http.put("/api/v1/agenda/lessons/:id", async ({ request }) => {
          sent.push(await request.json());
          current = changed;
          return HttpResponse.json(changed);
        }),
        http.post("/api/v1/agenda/lessons/:id/cancel", async ({ request }) => {
          sent.push(await request.json());
          current = changed;
          return HttpResponse.json(changed);
        }),
      );
      return sent;
    };

    it("CA11: editar com esta e as seguintes e trocar o professor atualiza o painel", async () => {
      const user = userEvent.setup();
      setup();
      stubTeachers();
      const sent = stateful(
        detail({ startsAt: "18:30", substitute: true, head: teachers[0], teacher: teachers[1] }),
      );
      await ready();
      await user.click(screen.getByRole("button", { name: "Editar aula" }));
      const dialog = await screen.findByRole("dialog", { name: "Editar aula" });
      await screen.findByRole("option", { name: "João Pereira" });
      fireEvent.change(within(dialog).getByLabelText("Início"), { target: { value: "18:30" } });
      await user.selectOptions(within(dialog).getByLabelText("Professor da aula"), "t2");
      await user.click(within(dialog).getByRole("radio", { name: /Esta e as seguintes/ }));
      await user.click(within(dialog).getByRole("button", { name: "Salvar" }));
      expect(await screen.findByText("Substituição")).toBeInTheDocument();
      expect(screen.queryByRole("dialog")).toBeNull();
      expect(sent).toEqual([
        { scope: "FOLLOWING", date: "2026-03-10", startsAt: "18:30", endsAt: "20:00", teacherId: "t2" },
      ]);
      expect(screen.getByText("10/03/2026, das 18:30 às 20:00")).toBeInTheDocument();
    });

    it("CA11: 409 e 422 aparecem na janela; fica aberta", async () => {
      const user = userEvent.setup();
      setup();
      stubTeachers();
      await ready();
      server.use(
        http.put("/api/v1/agenda/lessons/:id", () =>
          HttpResponse.json({ code: "CONFLICT", fields: [] }, { status: 409 }),
        ),
      );
      await user.click(screen.getByRole("button", { name: "Editar aula" }));
      await screen.findByRole("option", { name: "João Pereira" });
      await user.click(screen.getByRole("button", { name: "Salvar" }));
      expect(await screen.findByText(/já existe aula da turma neste dia e horário/)).toBeInTheDocument();
      server.use(
        http.put("/api/v1/agenda/lessons/:id", () =>
          HttpResponse.json(
            { code: "UNPROCESSABLE", fields: [{ field: "teacherId", message: "Professor não ensina a modalidade." }] },
            { status: 422 },
          ),
        ),
      );
      await user.click(screen.getByRole("button", { name: "Salvar" }));
      expect(await screen.findByText("Professor não ensina a modalidade.")).toBeInTheDocument();
      expect(screen.getByRole("dialog", { name: "Editar aula" })).toBeInTheDocument();
    });

    it("CA11: término antes do início é recusado na janela sem chamar o servidor", async () => {
      const user = userEvent.setup();
      setup();
      stubTeachers();
      await ready();
      await user.click(screen.getByRole("button", { name: "Editar aula" }));
      await screen.findByRole("option", { name: "João Pereira" });
      fireEvent.change(screen.getByLabelText("Término"), { target: { value: "18:00" } });
      await user.click(screen.getByRole("button", { name: "Salvar" }));
      expect(await screen.findByText(/o término depois do início/)).toBeInTheDocument();
    });

    it("CA12: opções com erro 500 têm Tentar de novo; 403 mostra sem permissão", async () => {
      const user = userEvent.setup();
      setup();
      stubTeachers(500);
      await ready();
      await user.click(screen.getByRole("button", { name: "Editar aula" }));
      const retry = await screen.findByRole("button", { name: "Tentar de novo" });
      stubTeachers();
      await user.click(retry);
      expect(await screen.findByRole("option", { name: "João Pereira" })).toBeInTheDocument();
      await user.click(screen.getByRole("button", { name: "Cancelar" }));
      stubTeachers(403);
      await user.click(screen.getByRole("button", { name: "Editar aula" }));
      expect(await screen.findByText("Você não tem acesso")).toBeInTheDocument();
    });

    it("CA11: aula concluída só corrige o professor, com motivo", async () => {
      const user = userEvent.setup();
      setup({ lesson: detail({ status: "COMPLETED", canCancel: false }) });
      stubTeachers();
      const sent = stateful(detail({ status: "COMPLETED", canCancel: false, teacher: teachers[1], substitute: true, head: teachers[0] }), detail({ status: "COMPLETED", canCancel: false }));
      await ready();
      await user.click(screen.getByRole("button", { name: "Editar aula" }));
      await screen.findByRole("option", { name: "João Pereira" });
      expect(screen.getByLabelText("Data")).toBeDisabled();
      await user.selectOptions(screen.getByLabelText("Professor da aula"), "t2");
      await user.click(screen.getByRole("button", { name: "Salvar" }));
      expect(await screen.findByText("Informe o motivo da correção.")).toBeInTheDocument();
      await user.type(screen.getByLabelText("Motivo da correção"), "Troca registrada");
      await user.click(screen.getByRole("button", { name: "Salvar" }));
      expect(await screen.findByText("Substituição")).toBeInTheDocument();
      expect(sent).toEqual([
        { scope: "THIS", date: "2026-03-10", startsAt: "19:00", endsAt: "20:00", teacherId: "t2", reason: "Troca registrada" },
      ]);
    });

    it("CA11, D4: cancelar pede o texto em Outro, envia os canais e o painel mostra Cancelada, o motivo e o aviso pronto", async () => {
      const user = userEvent.setup();
      setup();
      const sent = stateful(
        detail({ status: "CANCELLED", cancelReason: "Chuva forte", cancelReasonCode: "OTHER", canEdit: false, canCancel: false }),
      );
      await ready();
      await user.click(screen.getByRole("button", { name: "Cancelar aula" }));
      const dialog = await screen.findByRole("dialog", { name: "Cancelar aula" });
      expect(within(dialog).getByRole("checkbox", { name: "Portal (sempre)" })).toBeDisabled();
      await user.click(within(dialog).getByRole("button", { name: "Cancelar aula" }));
      expect(await within(dialog).findByText("Escolha o motivo.")).toBeInTheDocument();
      await user.click(within(dialog).getByRole("radio", { name: "Outro" }));
      await user.click(within(dialog).getByRole("button", { name: "Cancelar aula" }));
      expect(await within(dialog).findByText(/Informe o motivo, em até 200/)).toBeInTheDocument();
      await user.type(within(dialog).getByLabelText("Qual o motivo?"), "Chuva forte");
      await user.click(within(dialog).getByRole("checkbox", { name: "E-mail" }));
      await user.click(within(dialog).getByRole("checkbox", { name: /WhatsApp/ }));
      await user.click(within(dialog).getByRole("button", { name: "Cancelar aula" }));
      expect(await screen.findByText("Aviso pronto")).toBeInTheDocument();
      expect(sent).toEqual([{ reason: "OTHER", reasonText: "Chuva forte", channels: ["EMAIL", "WHATSAPP"] }]);
      expect(screen.getAllByText("Cancelada").length).toBeGreaterThan(0);
      expect(
        screen.getByText("A aula de Jiu-Jitsu Adulto de 10/03/2026, das 19:00 às 20:00, foi cancelada. Motivo: Chuva forte."),
      ).toBeInTheDocument();
      expect(screen.queryByRole("button", { name: "Cancelar aula" })).toBeNull();
    });

    it("CA11: motivo por código é traduzido; sem grupo não há WhatsApp; 409 aparece na janela", async () => {
      const user = userEvent.setup();
      setup({ lesson: detail({ groupLink: null }) });
      await ready();
      server.use(
        http.post("/api/v1/agenda/lessons/:id/cancel", () =>
          HttpResponse.json({ code: "CONFLICT", fields: [] }, { status: 409 }),
        ),
      );
      await user.click(screen.getByRole("button", { name: "Cancelar aula" }));
      const dialog = await screen.findByRole("dialog", { name: "Cancelar aula" });
      expect(within(dialog).queryByRole("checkbox", { name: /WhatsApp/ })).toBeNull();
      await user.click(within(dialog).getByRole("radio", { name: "Feriado" }));
      await user.click(within(dialog).getByRole("button", { name: "Cancelar aula" }));
      expect(await within(dialog).findByText(/já concluída ou cancelada/)).toBeInTheDocument();
      await user.click(within(dialog).getByRole("button", { name: "Manter aula" }));
      expect(screen.queryByRole("dialog")).toBeNull();
    });

    it("CA12: cancelada por código mostra o texto traduzido no painel", async () => {
      setup({
        lesson: detail({ status: "CANCELLED", cancelReasonCode: "HOLIDAY", canEdit: false, canCancel: false }),
      });
      await ready();
      expect(screen.getAllByText("Feriado").length).toBeGreaterThan(0);
    });

    it("CA12: botões somem quando canEdit e canCancel são falsos", async () => {
      setup({ role: "TEACHER", lesson: detail({ canEdit: false, canCancel: false }) });
      await ready();
      expect(screen.queryByRole("button", { name: "Editar aula" })).toBeNull();
      expect(screen.queryByRole("button", { name: "Cancelar aula" })).toBeNull();
    });

    it.each([
      ["claro", false, "Editar aula"],
      ["escuro", true, "Cancelar aula"],
    ])("CA12: a 360 px, a janela aberta não tem violações no axe no tema %s", async (_n, dark, button) => {
      const user = userEvent.setup();
      setDevicePrefersDark(dark);
      setup({ width: 360 });
      stubTeachers();
      await ready();
      await user.click(screen.getByRole("button", { name: button }));
      await screen.findByRole("dialog", { name: button });
      if (button === "Editar aula") await screen.findByRole("option", { name: "João Pereira" });
      expect(document.documentElement.scrollWidth).toBeLessThanOrEqual(
        document.documentElement.clientWidth || 360,
      );
      expect(await axeViolations(document.body)).toEqual([]);
    });
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
