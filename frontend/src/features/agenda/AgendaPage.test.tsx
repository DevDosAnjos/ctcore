import { readFileSync } from "node:fs";
import { join } from "node:path";
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
import { addDays, br, mondayOf, todayIso } from "./agendaDates";
import type { AgendaData, AgendaLesson } from "./useAgenda";

const session = (role: string): SessionView => ({
  account: { id: "a", email: "x@x.com", name: "X" },
  links: [{ id: "l1", organizationId: "o", organizationName: "Alfa", role, unitIds: [] }],
  activeLinkId: "l1",
  twoFactor: { enabled: false, required: false },
});

const monday = () => mondayOf(todayIso());
const lesson = (over: Partial<AgendaLesson>): AgendaLesson => ({
  id: "l1",
  date: monday(),
  startsAt: "19:00",
  endsAt: "20:00",
  status: "SCHEDULED",
  cancelReason: null,
  class: { id: "c1", name: "Jiu-Jitsu Adulto" },
  unit: { id: "u1", name: "Centro" },
  modality: { id: "m1", name: "Jiu-Jitsu" },
  teacher: { id: "t1", name: "Ana Souza" },
  substitute: false,
  canEdit: true,
  canCancel: true,
  ...over,
});
const data = (over: Partial<AgendaData> = {}): AgendaData => ({
  units: [{ id: "u1", name: "Centro" }],
  unit: { id: "u1", name: "Centro" },
  teachers: [{ id: "t1", name: "Ana Souza" }],
  modalities: [
    { id: "m1", name: "Jiu-Jitsu" },
    { id: "m2", name: "Judô" },
  ],
  lessons: [
    lesson({}),
    lesson({ id: "l2", date: addDays(monday(), 1), class: { id: "c2", name: "Judô Infantil" }, modality: { id: "m2", name: "Judô" }, startsAt: "18:00", endsAt: "19:00", status: "CANCELLED", cancelReason: "Chuva forte" }),
    lesson({ id: "l3", date: addDays(monday(), 2), substitute: true, teacher: { id: "t2", name: "João Pereira" } }),
  ],
  noClassDays: [{ startsOn: addDays(monday(), 3), endsOn: addDays(monday(), 3), reason: "Feriado local" }],
  ...over,
});

let calls: URLSearchParams[];

function setup(opts: { role?: string; width?: number; body?: () => Response | Promise<Response>; view?: AgendaData } = {}) {
  calls = [];
  window.innerWidth = opts.width ?? 1280;
  window.history.replaceState(null, "", "/agenda");
  const role = opts.role ?? "COORDINATOR";
  server.use(
    http.get("/api/v1/auth/session", () => HttpResponse.json(session(role))),
    http.get("/api/v1/permissions", () => HttpResponse.json({ permissions: mockPermissions(role) })),
    http.get("/api/v1/units/reachable", () => HttpResponse.json({ allUnits: true, units: [{ id: "u1", name: "Centro", shortName: "c" }] })),
    http.get("/api/v1/agenda", ({ request }) => {
      calls.push(new URL(request.url).searchParams);
      return opts.body ? opts.body() : HttpResponse.json(opts.view ?? data());
    }),
  );
  return render(
    <ThemeProvider>
      <MemoryRouter initialEntries={["/agenda"]}>
        <SessionProvider>
          <App />
        </SessionProvider>
      </MemoryRouter>
    </ThemeProvider>,
  );
}

const ready = () => screen.findByRole("heading", { name: "Agenda", level: 1 });

describe("AgendaPage", () => {
  it("CA13: grade da semana com turma, professor e modalidade; cancelada riscada com o rótulo; Sem aula com motivo; selo Substituição", async () => {
    setup();
    await ready();
    const cell = (await screen.findAllByText("Jiu-Jitsu Adulto"))[0].closest("li")!;
    expect(within(cell).getByText("Ana Souza")).toBeInTheDocument();
    expect(within(cell).getByText("Jiu-Jitsu")).toBeInTheDocument();
    const cancelled = screen.getByText("Judô Infantil").closest("li")!;
    expect(screen.getByText("Judô Infantil")).toHaveClass("line-through");
    expect(within(cancelled).getByText("Cancelada")).toBeInTheDocument();
    expect(within(cancelled).getByText("Chuva forte")).toBeInTheDocument();
    expect(screen.getByText("Substituição")).toBeInTheDocument();
    const head = screen.getByText("Sem aula").closest("th")!;
    expect(head).toHaveClass("bg-muted");
    expect(within(head).getByText("Feriado local")).toBeInTheDocument();
    expect(screen.getByRole("columnheader", { name: /Seg/ })).toBeInTheDocument();
    expect(screen.getByRole("rowheader", { name: "18:00" })).toBeInTheDocument();
    expect(calls[0].get("from")).toBe(monday());
    expect(calls[0].get("to")).toBe(addDays(monday(), 6));
  });

  it("CA13: setas trocam a semana, Hoje e Esta semana voltam; filtros refazem a consulta; unidade só com mais de uma", async () => {
    const user = userEvent.setup();
    setup();
    await screen.findByText("Substituição");
    expect(screen.queryByLabelText("Unidade")).toBeNull();
    await user.click(screen.getByRole("button", { name: "Próxima semana" }));
    await waitFor(() => expect(calls.at(-1)!.get("from")).toBe(addDays(monday(), 7)));
    expect(screen.getByText(`${br(addDays(monday(), 7))} a ${br(addDays(monday(), 13))}`)).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Hoje" }));
    await waitFor(() => expect(calls.at(-1)!.get("from")).toBe(monday()));
    await user.click(screen.getByRole("button", { name: "Semana anterior" }));
    await waitFor(() => expect(calls.at(-1)!.get("from")).toBe(addDays(monday(), -7)));
    await user.click(screen.getByRole("button", { name: "Esta semana" }));
    await waitFor(() => expect(calls.at(-1)!.get("from")).toBe(monday()));
    await user.selectOptions(await screen.findByLabelText("Professor"), "t1");
    await waitFor(() => expect(calls.at(-1)!.get("teacherId")).toBe("t1"));
    await user.selectOptions(screen.getByLabelText("Modalidade"), "m2");
    await waitFor(() => expect(calls.at(-1)!.get("modalityId")).toBe("m2"));
  });

  it("CA13: com mais de uma unidade aparece o seletor e escolher refaz a consulta sem os filtros antigos", async () => {
    const user = userEvent.setup();
    setup({ view: data({ units: [{ id: "u1", name: "Centro" }, { id: "u2", name: "Norte" }] }) });
    await user.selectOptions(await screen.findByLabelText("Unidade"), "u2");
    await waitFor(() => expect(calls.at(-1)!.get("unitId")).toBe("u2"));
    expect(calls.at(-1)!.get("teacherId")).toBeNull();
  });

  it("CA14: a 360 px mostra só o dia, setas cruzam a semana, Hoje volta ao dia atual e dia sem aulas mostra o vazio", async () => {
    const user = userEvent.setup();
    setup({ width: 360 });
    await ready();
    const today = todayIso();
    expect(await screen.findByText(br(today), { exact: false })).toBeInTheDocument();
    expect(screen.queryByRole("table")).toBeNull();
    await user.click(screen.getByRole("button", { name: "Dia anterior" }));
    await user.click(screen.getByRole("button", { name: "Dia anterior" }));
    await user.click(screen.getByRole("button", { name: "Hoje" }));
    expect(screen.getByRole("button", { name: "Próximo dia" })).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Próximo dia" }));
    expect(await screen.findByText(br(addDays(today, 1)), { exact: false })).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Esta semana" }));
    expect(await screen.findByText(br(addDays(today, 1)), { exact: false })).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Hoje" }));
    expect(await screen.findByText(br(today), { exact: false })).toBeInTheDocument();
  });

  it("CA14: a lista do dia mostra cancelada riscada, selo e Sem aula com o motivo", async () => {
    const day = todayIso();
    setup({
      width: 360,
      view: data({
        lessons: [lesson({ id: "x1", date: day, status: "CANCELLED", cancelReason: "Chuva", substitute: true }), lesson({ id: "x2", date: day, startsAt: "20:00", endsAt: "21:00", class: { id: "c3", name: "Boxe Noite" } })],
        noClassDays: [{ startsOn: day, endsOn: day, reason: "Feriado local" }],
      }),
    });
    await ready();
    expect(await screen.findByText("Cancelada")).toBeInTheDocument();
    expect(screen.getByText("Jiu-Jitsu Adulto")).toHaveClass("line-through");
    expect(screen.getByText("Substituição")).toBeInTheDocument();
    expect(screen.getByText("Sem aula")).toBeInTheDocument();
    expect(screen.getByText(/Feriado local/)).toBeInTheDocument();
    expect(screen.getByText("Boxe Noite")).toBeInTheDocument();
  });

  it("CA14: dia sem aulas na lista mostra o estado vazio", async () => {
    setup({ width: 360, view: data({ lessons: [], noClassDays: [] }) });
    expect(await screen.findByText("Nenhuma aula neste período")).toBeInTheDocument();
  });

  it("CA15: carregando, vazio, erro com Tentar de novo e sem permissão", async () => {
    const user = userEvent.setup();
    let fail = true;
    const view = () => (fail ? HttpResponse.json({ code: "INTERNAL_ERROR" }, { status: 500 }) : HttpResponse.json(data({ lessons: [], noClassDays: [] })));
    setup({ body: view });
    expect(await screen.findByText("Carregando...")).toBeInTheDocument();
    const retry = await screen.findByRole("button", { name: "Tentar de novo" });
    fail = false;
    await user.click(retry);
    expect(await screen.findByText("Nenhuma aula neste período")).toBeInTheDocument();
  });

  it("CA15: sem permissão quando o servidor responde 403", async () => {
    setup({ body: () => HttpResponse.json({ code: "FORBIDDEN" }, { status: 403 }) });
    expect(await screen.findByText("Você não tem acesso")).toBeInTheDocument();
  });

  it.each(["COORDINATOR", "ADMINISTRATOR", "TEACHER"])("%s vê Agenda em Operação e chega à tela", async (role) => {
    setup({ role });
    await ready();
    expect(screen.getAllByRole("link", { name: "Agenda" })[0]).toHaveAttribute("href", "/agenda");
  });

  it("CA12: o arquivo de tradução da tela não menciona lista de espera, presença nem frequência", () => {
    const text = JSON.stringify(JSON.parse(readFileSync(join(process.cwd(), "src/i18n/pt-BR.json"), "utf8")).agenda);
    expect(text).not.toMatch(/presen|frequ|espera/i);
  });

  it.each([
    ["claro", false],
    ["escuro", true],
  ])("CA15: a 360 px não tem violações no axe no tema %s", async (_n, dark) => {
    setDevicePrefersDark(dark);
    const { container } = setup({ width: 360 });
    await ready();
    await waitFor(() => expect(screen.queryByText("Carregando...")).toBeNull());
    expect(await axeViolations(container)).toEqual([]);
  });

  it("CA15: a grade a 1280 px não tem violações no axe", async () => {
    const { container } = setup();
    await screen.findByText("Substituição");
    expect(await axeViolations(container)).toEqual([]);
  });
});
