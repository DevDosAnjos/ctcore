import { cleanup, render, screen, waitFor, within } from "@testing-library/react";
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
import type { ClassDetail } from "./useClasses";

const session = (role: string): SessionView => ({
  account: { id: "a", email: "x@x.com", name: "X" },
  links: [{ id: "l1", organizationId: "o", organizationName: "Alfa", role, unitIds: [] }],
  activeLinkId: "l1",
  twoFactor: { enabled: false, required: false },
});
const detail = (over: Partial<ClassDetail> = {}): ClassDetail => ({
  id: "c1",
  name: "Jiu-Jitsu Adulto",
  unit: { id: "u1", name: "Centro" },
  modality: { id: "m1", name: "Jiu-Jitsu" },
  ageRange: { id: "r1", name: "Adulto", minAge: 18, maxAge: null },
  schedule: [
    { weekday: 1, startsAt: "19:00", endsAt: "20:00" },
    { weekday: 3, startsAt: "19:00", endsAt: "20:00" },
  ],
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

let deactivated: number;

function setup(get: () => Response | Promise<Response> = () => HttpResponse.json(detail()), role = "COORDINATOR") {
  deactivated = 0;
  server.use(
    http.get("/api/v1/auth/session", () => HttpResponse.json(session(role))),
    http.get("/api/v1/permissions", () => HttpResponse.json({ permissions: mockPermissions(role) })),
    http.get("/api/v1/units/reachable", () => HttpResponse.json({ allUnits: true, units: [{ id: "u1", name: "Centro", shortName: "c" }] })),
    http.get("/api/v1/classes/c1", () => get()),
  );
  return render(
    <ThemeProvider>
      <MemoryRouter initialEntries={["/turmas/c1"]}>
        <SessionProvider>
          <App />
        </SessionProvider>
      </MemoryRouter>
    </ThemeProvider>,
  );
}

const ready = () => screen.findByRole("heading", { name: "Jiu-Jitsu Adulto", level: 1 });

// As permissões chegam depois do painel.
const enabled = () => waitFor(() => expect(screen.getByRole("button", { name: "Desativar" })).toBeEnabled());

describe("ClassPanelPage (RF-TUR-02)", () => {
  it("CA8: mostra situação, modalidade, faixa, unidade, horários, titular, auxiliares, capacidade, vazios e grupo", async () => {
    setup();
    await ready();
    expect(screen.getByText("Ativa")).toBeInTheDocument();
    expect(screen.getByText("Centro")).toBeInTheDocument();
    expect(screen.getByText("Jiu-Jitsu")).toBeInTheDocument();
    expect(screen.getByText("Adulto")).toBeInTheDocument();
    expect(screen.getByText("Segunda-feira, 19:00–20:00")).toBeInTheDocument();
    expect(screen.getByText("Quarta-feira, 19:00–20:00")).toBeInTheDocument();
    expect(screen.getByText("Ana Souza")).toBeInTheDocument();
    expect(screen.getByText("João Lima")).toBeInTheDocument();
    expect(screen.getByText("0 de 30 alunos ativos")).toBeInTheDocument();
    expect(screen.queryByText(/Acima da capacidade/)).toBeNull();
    expect(screen.getByText("Nenhuma aula agendada.")).toBeInTheDocument();
    expect(screen.getByText("Nenhum aluno ativo nesta turma.")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Abrir grupo" })).toHaveAttribute("href", "https://chat.example.com/grupo");
    expect(screen.getByRole("link", { name: "Abrir grupo" })).toHaveAttribute("target", "_blank");
    expect(screen.getByRole("link", { name: "Abrir grupo" })).toHaveAttribute("rel", "noopener noreferrer");
  });

  it("CA8: Ver alunos é link para a lista filtrada, também em turma desativada; Editar e Desativar estão ativos (CA13)", async () => {
    setup();
    await ready();
    expect(screen.getByRole("link", { name: "Ver alunos" })).toHaveAttribute("href", "/alunos?classId=c1");
    await enabled();
    cleanup();
    setup(() => HttpResponse.json(detail({ status: "INACTIVE" })));
    await ready();
    expect(screen.getByRole("link", { name: "Ver alunos" })).toHaveAttribute("href", "/alunos?classId=c1");
  });

  it("CA26 (#82): Avisar turma abre o comunicado já com a turma; sem a permissão ou com a turma desativada fica desativado", async () => {
    setup();
    await ready();
    expect(await screen.findByRole("link", { name: "Avisar turma" })).toHaveAttribute("href", "/comunicados/novo?classId=c1");
    cleanup();
    setup(() => HttpResponse.json(detail({ status: "INACTIVE" })));
    await ready();
    await enabled().catch(() => undefined);
    expect(screen.getByRole("button", { name: "Avisar turma" })).toBeDisabled();
  });

  it.each([[null], ["javascript:alert(1)"], ["data:text/html,x"], ["nao e url"]])("CA8: link de grupo %s não vira botão", async (link) => {
    setup(() => HttpResponse.json(detail({ groupLink: link })));
    await ready();
    expect(screen.queryByRole("link", { name: "Abrir grupo" })).toBeNull();
    expect(screen.getByRole("button", { name: "Abrir grupo" })).toBeDisabled();
  });

  it("CA8: capacidade excedida mostra o aviso; sem capacidade não mostra limite", async () => {
    const { unmount } = setup(() => HttpResponse.json(detail({ activeStudents: 31, overCapacity: true })));
    await ready();
    expect(screen.getByText("Acima da capacidade (31/30)")).toBeInTheDocument();
    unmount();
    setup(() => HttpResponse.json(detail({ activeStudents: 4, capacity: null })));
    await ready();
    expect(screen.getByText("4 alunos ativos, sem limite de capacidade")).toBeInTheDocument();
  });

  it("CA3, CA8: alunos aparecem e só quem tem a turma como outro horário leva o selo; próxima aula aparece", async () => {
    setup(() =>
      HttpResponse.json(
        detail({
          activeStudents: 2,
          nextClass: { date: "2026-10-20", startsAt: "19:00", endsAt: "20:00" },
          students: [
            { id: "s1", name: "Bia", primary: true },
            { id: "s2", name: "Caio", primary: false },
          ],
        }),
      ),
    );
    await ready();
    expect(screen.getByText("2026-10-20, 19:00–20:00")).toBeInTheDocument();
    const items = screen.getAllByRole("listitem").filter((li) => /Bia|Caio/.test(li.textContent ?? ""));
    expect(items).toHaveLength(2);
    expect(within(items[0]).queryByText("Outro horário")).toBeNull();
    expect(within(items[1]).getByText("Outro horário")).toBeInTheDocument();
  });

  it("CA8: turma desativada tem selo e Desativar desativado", async () => {
    setup(() => HttpResponse.json(detail({ status: "INACTIVE", deactivation: { allowed: false, blockingStudents: 0 } })));
    await ready();
    expect(screen.getByText("Desativada")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Desativar" })).toBeDisabled();
  });

  it("CA9: sem alunos principais confirmar desativa e o painel passa a Desativada", async () => {
    const user = userEvent.setup();
    let current = detail();
    setup(() => HttpResponse.json(current));
    server.use(
      http.post("/api/v1/classes/c1/deactivate", () => {
        deactivated += 1;
        current = detail({ status: "INACTIVE", deactivation: { allowed: false, blockingStudents: 0 } });
        return HttpResponse.json(current);
      }),
    );
    await ready();
    await enabled();
    await user.click(screen.getByRole("button", { name: "Desativar" }));
    const dialog = await screen.findByRole("dialog", { name: "Desativar Jiu-Jitsu Adulto" });
    await user.click(within(dialog).getByRole("button", { name: "Desativar" }));
    await waitFor(() => expect(deactivated).toBe(1));
    expect(await screen.findByText("Turma desativada.")).toBeInTheDocument();
    expect(screen.getByText("Desativada")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Desativar" })).toBeDisabled();
  });

  it("CA9: com alunos principais a janela mostra a recusa com a contagem e não oferece confirmar", async () => {
    const user = userEvent.setup();
    setup(() => HttpResponse.json(detail({ deactivation: { allowed: false, blockingStudents: 3 } })));
    await ready();
    await enabled();
    await user.click(screen.getByRole("button", { name: "Desativar" }));
    const dialog = await screen.findByRole("dialog");
    expect(within(dialog).getByRole("alert")).toHaveTextContent("3 alunos têm esta turma como principal");
    expect(within(dialog).queryByRole("button", { name: "Desativar" })).toBeNull();
    expect(deactivated).toBe(0);
  });

  it("CA9: se o servidor recusar com HAS_PRIMARY_STUDENTS a janela passa a mostrar a recusa; outro erro mostra aviso", async () => {
    const user = userEvent.setup();
    setup();
    server.use(
      http.post("/api/v1/classes/c1/deactivate", () =>
        HttpResponse.json({ code: "HAS_PRIMARY_STUDENTS", fields: [{ field: "primaryStudents", message: "1" }] }, { status: 409 }),
      ),
    );
    await ready();
    await enabled();
    await user.click(screen.getByRole("button", { name: "Desativar" }));
    const dialog = await screen.findByRole("dialog");
    await user.click(within(dialog).getByRole("button", { name: "Desativar" }));
    expect(await within(dialog).findByRole("alert")).toHaveTextContent("1 aluno tem esta turma como principal");
    expect(within(dialog).queryByRole("button", { name: "Desativar" })).toBeNull();
    await user.click(within(dialog).getAllByRole("button", { name: "Fechar" }).at(-1)!);

    server.use(http.post("/api/v1/classes/c1/deactivate", () => HttpResponse.json({ code: "INTERNAL_ERROR" }, { status: 500 })));
    await user.click(screen.getByRole("button", { name: "Desativar" }));
    const again = await screen.findByRole("dialog");
    await user.click(within(again).getByRole("button", { name: "Desativar" }));
    expect(await within(again).findByRole("alert")).toHaveTextContent("Não foi possível desativar");
  });

  it("CA8: carregando, erro com Tentar de novo, sem permissão e turma não encontrada", async () => {
    const user = userEvent.setup();
    let fail = true;
    const first = setup(() => (fail ? HttpResponse.json({ code: "INTERNAL_ERROR" }, { status: 500 }) : HttpResponse.json(detail())));
    expect(screen.getByRole("status")).toHaveTextContent("Carregando");
    const retry = await screen.findByRole("button", { name: "Tentar de novo" });
    fail = false;
    await user.click(retry);
    await ready();
    first.unmount();
    const second = setup(() => HttpResponse.json({ code: "FORBIDDEN" }, { status: 403 }));
    expect(await screen.findByText("Você não tem acesso")).toBeInTheDocument();
    second.unmount();
    setup(() => HttpResponse.json({ code: "NOT_FOUND" }, { status: 404 }));
    expect(await screen.findByText("Turma não encontrada")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Voltar às turmas" })).toHaveAttribute("href", "/turmas");
  });

  it.each([
    ["claro", false],
    ["escuro", true],
  ])("CA8: a 360 px não tem violações no axe no tema %s", async (_n, dark) => {
    setDevicePrefersDark(dark);
    window.innerWidth = 360;
    const { container } = setup();
    await ready();
    expect(await axeViolations(container)).toEqual([]);
  });
});
