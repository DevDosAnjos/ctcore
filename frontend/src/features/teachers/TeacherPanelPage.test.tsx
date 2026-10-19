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
import type { TeacherDetail } from "./useTeachers";

const session = (role: string): SessionView => ({
  account: { id: "a", email: "x@x.com", name: "X" },
  links: [{ id: "l1", organizationId: "o", organizationName: "Alfa", role, unitIds: [] }],
  activeLinkId: "l1",
  twoFactor: { enabled: false, required: false },
});
const detail = (over: Partial<TeacherDetail> = {}): TeacherDetail => ({
  id: "t1",
  name: "Ana Souza",
  photoUrl: null,
  bio: "Faixa preta.",
  email: "ana@alfa.test",
  phone: "(11) 91234-5678",
  directoryConsent: true,
  consentDeclaredByManagement: true,
  status: "ACTIVE",
  modalities: [{ id: "m1", name: "Jiu-Jitsu", levelId: "l1", level: "Roxa", degree: 2 }],
  units: [{ id: "u1", name: "Centro" }, { id: "u2", name: "Norte" }],
  classes: [],
  upcomingClasses: [],
  access: "NONE",
  ...over,
});

let granted: unknown;
let resent: number;

function setup(get: () => Response | Promise<Response> = () => HttpResponse.json(detail()), role = "COORDINATOR") {
  granted = undefined;
  resent = 0;
  server.use(
    http.get("/api/v1/auth/session", () => HttpResponse.json(session(role))),
    http.get("/api/v1/permissions", () => HttpResponse.json({ permissions: mockPermissions(role) })),
    http.get("/api/v1/units/reachable", () => HttpResponse.json({ allUnits: true, units: [{ id: "u1", name: "Centro", shortName: "c" }, { id: "u2", name: "Norte", shortName: "n" }] })),
    http.get("/api/v1/teachers/t1", () => get()),
    http.post("/api/v1/teachers/t1/access", async ({ request }) => {
      granted = await request.json();
      return HttpResponse.json(detail({ access: "PENDING" }));
    }),
    http.post("/api/v1/teachers/t1/access/resend", () => {
      resent += 1;
      return new HttpResponse(null, { status: 204 });
    }),
  );
  return render(
    <ThemeProvider>
      <MemoryRouter initialEntries={["/professores/t1"]}>
        <SessionProvider>
          <App />
        </SessionProvider>
      </MemoryRouter>
    </ThemeProvider>,
  );
}

const ready = () => screen.findByRole("heading", { name: "Ana Souza", level: 1 });

describe("TeacherPanelPage (RF-PRO-02)", () => {
  it("CA9: mostra cabeçalho, apresentação, contato, graduação, unidades, vazios de turmas e aulas e a situação do acesso", async () => {
    setup();
    await ready();
    expect(screen.getByText("AS")).toBeInTheDocument();
    expect(screen.getByText("Ativo")).toBeInTheDocument();
    expect(screen.getByText("Faixa preta.")).toBeInTheDocument();
    expect(screen.getByText("ana@alfa.test")).toBeInTheDocument();
    expect(screen.getByText("(11) 91234-5678")).toBeInTheDocument();
    expect(screen.getByText("Jiu-Jitsu · Roxa, grau 2")).toBeInTheDocument();
    expect(screen.getByText("Centro, Norte")).toBeInTheDocument();
    expect(screen.getByText("Nenhuma turma ainda.")).toBeInTheDocument();
    expect(screen.getByText("Nenhuma aula agendada.")).toBeInTheDocument();
    expect(screen.getByText("Sem acesso")).toBeInTheDocument();
  });

  it("CA9: Editar ativo e Inativar desativado; foto aparece quando há URL; WhatsApp só com telefone", async () => {
    setup(() => HttpResponse.json(detail({ photoUrl: "https://files.example/f.png" })));
    await ready();
    expect(await screen.findByRole("button", { name: "Editar" })).toBeEnabled();
    expect(screen.getByRole("button", { name: "Inativar" })).toBeDisabled();
    expect(document.querySelector("img[src='https://files.example/f.png']")).not.toBeNull();
    expect(screen.queryByText("AS")).toBeNull();
    expect(screen.getByRole("link", { name: "Abrir conversa no WhatsApp" })).toHaveAttribute("href", expect.stringContaining("https://wa.me/55"));
  });

  it("CA9: sem telefone não há WhatsApp; turmas e próximas aulas listam o que vier", async () => {
    setup(() => HttpResponse.json(detail({ phone: null, classes: [{ id: "c1", name: "Jiu-Jitsu Noite", role: "HEAD" }], upcomingClasses: [{ classId: "c1", name: "Jiu-Jitsu Noite", startsAt: "2026-10-20T22:00:00Z" }] })));
    await ready();
    expect(screen.queryByRole("link", { name: "Abrir conversa no WhatsApp" })).toBeNull();
    expect(screen.getByText("Jiu-Jitsu Noite · Titular")).toBeInTheDocument();
    expect(screen.getAllByText(/Jiu-Jitsu Noite/).length).toBeGreaterThanOrEqual(2);
  });

  it.each([
    ["NONE", "Dar acesso", true],
    ["ENDED", "Dar acesso", true],
    ["PENDING", "Reenviar convite", true],
    ["ACTIVE", "", false],
  ] as const)("CA9: com acesso %s mostra a ação certa", async (access, label, shows) => {
    setup(() => HttpResponse.json(detail({ access })));
    await ready();
    if (shows) expect(screen.getByRole("button", { name: label })).toBeInTheDocument();
    else {
      expect(screen.queryByRole("button", { name: "Dar acesso" })).toBeNull();
      expect(screen.queryByRole("button", { name: "Reenviar convite" })).toBeNull();
    }
    if (access === "PENDING") expect(screen.queryByRole("button", { name: "Dar acesso" })).toBeNull();
    if (access === "NONE") expect(screen.queryByRole("button", { name: "Reenviar convite" })).toBeNull();
  });

  it("CA11: Dar acesso abre a janela com as unidades marcadas, envia e passa a mostrar Convite enviado", async () => {
    const user = userEvent.setup();
    let current = detail();
    setup(() => HttpResponse.json(current));
    server.use(
      http.post("/api/v1/teachers/t1/access", async ({ request }) => {
        granted = await request.json();
        current = detail({ access: "PENDING" });
        return HttpResponse.json(current);
      }),
    );
    await ready();
    await user.click(screen.getByRole("button", { name: "Dar acesso" }));
    const dialog = await screen.findByRole("dialog", { name: "Dar acesso a Ana Souza" });
    expect(within(dialog).getByRole("checkbox", { name: "Centro" })).toBeChecked();
    await user.click(within(dialog).getByRole("checkbox", { name: "Norte" }));
    await user.click(within(dialog).getByRole("button", { name: "Enviar convite" }));
    await waitFor(() => expect(granted).toEqual({ unitIds: ["u1"] }));
    expect(await screen.findByText("Convite enviado")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Reenviar convite" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Dar acesso" })).toBeNull();
  });

  it("CA11: sem unidade marcada a janela avisa e não envia; erro 409 do servidor aparece na janela", async () => {
    const user = userEvent.setup();
    setup();
    server.use(http.post("/api/v1/teachers/t1/access", () => HttpResponse.json({ code: "ALREADY_LINKED" }, { status: 409 })));
    await ready();
    await user.click(screen.getByRole("button", { name: "Dar acesso" }));
    const dialog = await screen.findByRole("dialog");
    await user.click(within(dialog).getByRole("checkbox", { name: "Centro" }));
    await user.click(within(dialog).getByRole("checkbox", { name: "Norte" }));
    await user.click(within(dialog).getByRole("button", { name: "Enviar convite" }));
    expect(await within(dialog).findByText("Escolha ao menos uma unidade.")).toBeInTheDocument();
    expect(granted).toBeUndefined();
    await user.click(within(dialog).getByRole("checkbox", { name: "Centro" }));
    await user.click(within(dialog).getByRole("button", { name: "Enviar convite" }));
    expect(await within(dialog).findByRole("alert")).toHaveTextContent("já está ligado a outro cadastro");
  });

  it("CA9: Reenviar convite chama a rota e avisa; falha também avisa", async () => {
    const user = userEvent.setup();
    setup(() => HttpResponse.json(detail({ access: "PENDING" })));
    await ready();
    await user.click(screen.getByRole("button", { name: "Reenviar convite" }));
    expect(await screen.findByText("Convite reenviado.")).toBeInTheDocument();
    expect(resent).toBe(1);
    server.use(http.post("/api/v1/teachers/t1/access/resend", () => HttpResponse.json({ code: "TOO_MANY_REQUESTS" }, { status: 429 })));
    await user.click(screen.getByRole("button", { name: "Reenviar convite" }));
    expect(await screen.findByText(/Não foi possível reenviar/)).toBeInTheDocument();
  });

  it("CA9: carregando, erro com Tentar de novo, sem permissão e professor não encontrado", async () => {
    const user = userEvent.setup();
    let fail = true;
    setup(() => (fail ? HttpResponse.json({ code: "INTERNAL_ERROR" }, { status: 500 }) : HttpResponse.json(detail())));
    expect(screen.getByRole("status")).toHaveTextContent("Carregando");
    const retry = await screen.findByRole("button", { name: "Tentar de novo" });
    fail = false;
    await user.click(retry);
    await ready();
  });

  it("CA9: 403 vira sem permissão e 404 vira não encontrado", async () => {
    const { unmount } = setup(() => HttpResponse.json({ code: "FORBIDDEN" }, { status: 403 }));
    expect(await screen.findByText("Você não tem acesso")).toBeInTheDocument();
    unmount();
    setup(() => HttpResponse.json({ code: "NOT_FOUND" }, { status: 404 }));
    expect(await screen.findByText("Professor não encontrado")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Voltar aos professores" })).toHaveAttribute("href", "/professores");
  });

  it.each([
    ["claro", false],
    ["escuro", true],
  ])("CA9: a 360 px não tem violações no axe no tema %s", async (_n, dark) => {
    setDevicePrefersDark(dark);
    window.innerWidth = 360;
    const { container } = setup();
    await ready();
    expect(await axeViolations(container)).toEqual([]);
  });
});
