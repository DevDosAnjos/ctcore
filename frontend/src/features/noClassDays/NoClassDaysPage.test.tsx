import { readFileSync } from "node:fs";
import { join } from "node:path";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { MemoryRouter } from "react-router";
import { App } from "@/App";
import { addDays, br, todayIso } from "@/features/agenda/agendaDates";
import { SessionProvider, type SessionView } from "@/features/auth/SessionProvider";
import { mockPermissions } from "@/mocks/permissions";
import { server } from "@/mocks/server";
import { axeViolations } from "@/test/axe";
import { setDevicePrefersDark } from "@/test/device";
import { ThemeProvider } from "@/theme/ThemeProvider";
import type { NoClassDayItem, NoClassDayList } from "./useNoClassDays";

const session = (role: string): SessionView => ({
  account: { id: "a", email: "x@x.com", name: "X" },
  links: [{ id: "l1", organizationId: "o", organizationName: "Alfa", role, unitIds: [] }],
  activeLinkId: "l1",
  twoFactor: { enabled: false, required: false },
});

const day = (over: Partial<NoClassDayItem> = {}): NoClassDayItem => ({
  id: "d1",
  unit: { id: "u1", name: "Centro" },
  startsOn: addDays(todayIso(), 5),
  endsOn: addDays(todayIso(), 6),
  reason: "Recesso de verão",
  origin: "COORDINATOR",
  allClasses: true,
  classes: [],
  notice: { mode: "NONE", sendAt: null, status: "NOT_NOTIFIED" },
  cancelledLessons: 2,
  canEdit: true,
  ...over,
});
const view = (over: Partial<NoClassDayList> = {}): NoClassDayList => ({
  units: [{ id: "u1", name: "Centro" }],
  unit: { id: "u1", name: "Centro" },
  days: [day()],
  suggestions: [
    { code: "TIRADENTES", date: addDays(todayIso(), 30) },
    { code: "CHRISTMAS", date: addDays(todayIso(), 60) },
  ],
  ...over,
});

type Call = { method: string; url: URL; body?: Record<string, unknown> };
let calls: Call[];

function setup(opts: { role?: string; width?: number; list?: () => Response | Promise<Response>; failWith?: number; classes?: boolean } = {}) {
  calls = [];
  window.innerWidth = opts.width ?? 1280;
  window.history.replaceState(null, "", "/dias-sem-aula");
  const role = opts.role ?? "COORDINATOR";
  const lessons = [
    { id: "l1", date: addDays(todayIso(), 5), startsAt: "19:00", endsAt: "20:00", class: { id: "c1", name: "Jiu-Jitsu Adulto" }, title: null },
    { id: "l2", date: addDays(todayIso(), 6), startsAt: "18:00", endsAt: "19:00", class: null, title: "Seminário" },
  ];
  const record = async (request: Request) => calls.push({ method: request.method, url: new URL(request.url), body: request.method === "GET" ? undefined : await request.clone().json().catch(() => undefined) });
  server.use(
    http.get("/api/v1/auth/session", () => HttpResponse.json(session(role))),
    http.get("/api/v1/permissions", () => HttpResponse.json({ permissions: mockPermissions(role) })),
    http.get("/api/v1/units/reachable", () => HttpResponse.json({ allUnits: true, units: [{ id: "u1", name: "Centro", shortName: "c" }] })),
    http.get("/api/v1/classes", () => (opts.classes === false ? HttpResponse.json({ items: [] }) : HttpResponse.json({ items: [{ id: "c1", name: "Jiu-Jitsu Adulto" }, { id: "c2", name: "Judô Infantil" }] }))),
    http.get("/api/v1/no-class-days/preview", async ({ request }) => {
      await record(request);
      return HttpResponse.json({ units: [{ unit: { id: "u1", name: "Centro" }, lessons }] });
    }),
    http.get("/api/v1/no-class-days", async ({ request }) => {
      await record(request);
      return opts.list ? opts.list() : HttpResponse.json(view());
    }),
    http.post("/api/v1/no-class-days", async ({ request }) => {
      await record(request);
      if (opts.failWith) return HttpResponse.json({ code: "X", fields: opts.failWith === 422 ? [{ field: "classIds", message: "escolha turmas ativas da unidade" }] : [] }, { status: opts.failWith });
      return HttpResponse.json([day({ id: "new" })], { status: 201 });
    }),
    http.put("/api/v1/no-class-days/:id", async ({ request }) => {
      await record(request);
      return HttpResponse.json(day());
    }),
    http.delete("/api/v1/no-class-days/:id", async ({ request }) => {
      await record(request);
      return opts.failWith ? HttpResponse.json({ code: "X" }, { status: opts.failWith }) : new HttpResponse(null, { status: 204 });
    }),
  );
  return render(
    <ThemeProvider>
      <MemoryRouter initialEntries={["/dias-sem-aula"]}>
        <SessionProvider>
          <App />
        </SessionProvider>
      </MemoryRouter>
    </ThemeProvider>,
  );
}

const ready = () => screen.findByRole("heading", { name: "Dias sem aula", level: 1 });
const writes = () => calls.filter((c) => c.method !== "GET");

describe("NoClassDaysPage", () => {
  it("CA17: lista com período, motivo, origem, turmas, aviso e aulas canceladas em texto; sugestões de feriado", async () => {
    setup();
    await ready();
    const item = (await screen.findByText(/Recesso de verão/)).closest("li")!;
    expect(within(item).getByText(`${br(addDays(todayIso(), 5))} a ${br(addDays(todayIso(), 6))} · Recesso de verão`)).toBeInTheDocument();
    expect(within(item).getByText("Origem: Coordenador")).toBeInTheDocument();
    expect(within(item).getByText("Todas as turmas")).toBeInTheDocument();
    expect(within(item).getByText("Aviso: Ainda não avisado")).toBeInTheDocument();
    expect(within(item).getByText("2 aulas canceladas")).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Feriados nacionais sugeridos" })).toBeInTheDocument();
    expect(screen.getByText(new RegExp(`${br(addDays(todayIso(), 30))} · Tiradentes`))).toBeInTheDocument();
  });

  it("CA17: confirmar uma sugestão abre a janela com a data e o motivo, o aviso é escolhido e a lista é refeita ao salvar", async () => {
    const user = userEvent.setup();
    setup();
    await ready();
    await user.click(await screen.findByRole("button", { name: `Confirmar Tiradentes, ${br(addDays(todayIso(), 30))}` }));
    const dialog = await screen.findByRole("dialog", { name: "Novo dia sem aula" });
    expect(within(dialog).getByLabelText("Início")).toHaveValue(addDays(todayIso(), 30));
    expect(within(dialog).getByLabelText("Motivo")).toHaveValue("Tiradentes");
    expect(within(dialog).getByText("A mensalidade não muda.")).toBeInTheDocument();
    await user.click(within(dialog).getByRole("radio", { name: "Agendar o aviso" }));
    await user.click(within(dialog).getByRole("button", { name: "Salvar" }));
    expect(await within(dialog).findByText("Informe quando enviar, antes do início do dia.")).toBeInTheDocument();
    expect(writes()).toHaveLength(0);
    await user.click(within(dialog).getByRole("radio", { name: "Avisar agora" }));
    const before = calls.filter((c) => c.method === "GET" && c.url.pathname.endsWith("/no-class-days")).length;
    await user.click(within(dialog).getByRole("button", { name: "Salvar" }));
    await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull());
    expect(writes()[0].body).toMatchObject({ unitIds: ["u1"], startsOn: addDays(todayIso(), 30), endsOn: addDays(todayIso(), 30), reason: "Tiradentes", holiday: true, classIds: [], notice: { mode: "NOW" } });
    await waitFor(() => expect(calls.filter((c) => c.method === "GET" && c.url.pathname.endsWith("/no-class-days")).length).toBeGreaterThan(before));
  });

  it("CA17: novo dia com prévia das aulas, aviso agendado e turmas escolhidas", async () => {
    const user = userEvent.setup();
    setup();
    await ready();
    await user.click(await screen.findByRole("button", { name: "Novo dia sem aula" }));
    const dialog = await screen.findByRole("dialog", { name: "Novo dia sem aula" });
    await user.type(within(dialog).getByLabelText("Motivo"), "Evento da academia");
    expect(await within(dialog).findByText("Centro: 2 aulas")).toBeInTheDocument();
    expect(within(dialog).getByText(new RegExp(`${br(addDays(todayIso(), 5))}, 19:00 às 20:00, Jiu-Jitsu Adulto`))).toBeInTheDocument();
    expect(within(dialog).getByText(new RegExp("18:00 às 19:00, Seminário"))).toBeInTheDocument();
    await user.click(within(dialog).getByRole("radio", { name: "Só algumas turmas" }));
    await user.click(within(dialog).getByRole("button", { name: "Salvar" }));
    expect(await within(dialog).findByText("Escolha ao menos uma turma.")).toBeInTheDocument();
    await user.click(within(dialog).getByRole("checkbox", { name: "Judô Infantil" }));
    await waitFor(() => expect(calls.at(-1)!.url.searchParams.getAll("classIds")).toEqual(["c2"]));
    await user.click(within(dialog).getByRole("radio", { name: "Agendar o aviso" }));
    await user.type(within(dialog).getByLabelText("Enviar em"), `${addDays(todayIso(), 1)}T09:00`);
    await user.click(within(dialog).getByRole("button", { name: "Salvar" }));
    await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull());
    const body = writes()[0].body as { classIds: string[]; notice: { mode: string; sendAt: string }; holiday?: boolean };
    expect(body.classIds).toEqual(["c2"]);
    expect(body.notice.mode).toBe("SCHEDULED");
    expect(new Date(body.notice.sendAt).getTime()).toBe(new Date(`${addDays(todayIso(), 1)}T09:00`).getTime());
    expect(body.holiday).toBeUndefined();
  });

  it("CA17: só o Coordenador escolhe várias unidades; com mais de uma, turmas ficam bloqueadas", async () => {
    const user = userEvent.setup();
    const two = view({ units: [{ id: "u1", name: "Centro" }, { id: "u2", name: "Norte" }] });
    setup({ list: () => HttpResponse.json(two) });
    await ready();
    await user.click(await screen.findByRole("button", { name: "Novo dia sem aula" }));
    const dialog = await screen.findByRole("dialog", { name: "Novo dia sem aula" });
    await user.click(within(dialog).getByRole("checkbox", { name: "Norte" }));
    expect(within(dialog).getByText("Para limitar a turmas, escolha uma só unidade.")).toBeInTheDocument();
    await user.type(within(dialog).getByLabelText("Motivo"), "Recesso");
    await user.click(within(dialog).getByRole("button", { name: "Salvar" }));
    await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull());
    expect((writes()[0].body as { unitIds: string[] }).unitIds).toEqual(["u1", "u2"]);
  });

  it("CA17: o Administrador não vê a escolha de várias unidades", async () => {
    const user = userEvent.setup();
    setup({ role: "ADMINISTRATOR", list: () => HttpResponse.json(view({ units: [{ id: "u1", name: "Centro" }, { id: "u2", name: "Norte" }] })) });
    await ready();
    await user.click(await screen.findByRole("button", { name: "Novo dia sem aula" }));
    const dialog = await screen.findByRole("dialog", { name: "Novo dia sem aula" });
    expect(within(dialog).queryByRole("checkbox", { name: "Norte" })).toBeNull();
    expect(within(dialog).getByText("Unidade: Centro")).toBeInTheDocument();
  });

  it("CA17: editar troca os campos pelo PUT; desfazer pede confirmação e envia o aviso das turmas", async () => {
    const user = userEvent.setup();
    setup();
    await ready();
    await user.click(await screen.findByRole("button", { name: "Editar" }));
    const edit = await screen.findByRole("dialog", { name: "Editar dia sem aula" });
    const reason = within(edit).getByLabelText("Motivo");
    expect(reason).toHaveValue("Recesso de verão");
    await user.clear(reason);
    await user.type(reason, "Reforma");
    await user.click(within(edit).getByRole("button", { name: "Salvar" }));
    await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull());
    expect(writes()[0]).toMatchObject({ method: "PUT", body: { reason: "Reforma", unitIds: ["u1"] } });
    expect(writes()[0].url.pathname).toBe("/api/v1/no-class-days/d1");

    await user.click(await screen.findByRole("button", { name: "Desfazer" }));
    const undo = await screen.findByRole("dialog", { name: "Desfazer dia sem aula" });
    expect(within(undo).getByText(/A mensalidade não muda/)).toBeInTheDocument();
    await user.click(within(undo).getByRole("checkbox", { name: "Avisar as turmas" }));
    await user.click(within(undo).getByRole("button", { name: "Desfazer dia" }));
    await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull());
    expect(writes()[1].method).toBe("DELETE");
    expect(writes()[1].url.search).toBe("?notify=true");
  });

  it("CA17: dia que já começou não oferece editar nem desfazer", async () => {
    setup({ list: () => HttpResponse.json(view({ days: [day({ canEdit: false })] })) });
    await ready();
    await screen.findByText(/Recesso de verão/);
    expect(screen.queryByRole("button", { name: "Editar" })).toBeNull();
    expect(screen.queryByRole("button", { name: "Desfazer" })).toBeNull();
  });

  it.each([
    [409, "Já existe um dia sem aula neste período, ou o dia já começou. Nada foi gravado."],
    [403, "Você não tem permissão para isto. Nada foi gravado."],
    [404, "Unidade ou turma não encontrada. Nada foi gravado."],
    [400, "Algum dado está errado. Confira os campos. Nada foi gravado."],
    [500, "Não foi possível salvar. Tente de novo. Nada foi gravado."],
  ])("CA17: erro %s do servidor aparece na janela, que segue aberta", async (status, text) => {
    const user = userEvent.setup();
    setup({ failWith: status });
    await ready();
    await user.click(await screen.findByRole("button", { name: "Novo dia sem aula" }));
    const dialog = await screen.findByRole("dialog", { name: "Novo dia sem aula" });
    await user.type(within(dialog).getByLabelText("Motivo"), "Recesso");
    await user.click(within(dialog).getByRole("button", { name: "Salvar" }));
    expect(await within(dialog).findByText(text)).toBeInTheDocument();
    expect(screen.getByRole("dialog")).toBeInTheDocument();
  });

  it("CA17: erro 422 do campo de turmas aparece junto do campo", async () => {
    const user = userEvent.setup();
    setup({ failWith: 422 });
    await ready();
    await user.click(await screen.findByRole("button", { name: "Novo dia sem aula" }));
    const dialog = await screen.findByRole("dialog", { name: "Novo dia sem aula" });
    await user.type(within(dialog).getByLabelText("Motivo"), "Recesso");
    await user.click(within(dialog).getByRole("button", { name: "Salvar" }));
    expect(await within(dialog).findByText("escolha turmas ativas da unidade")).toBeInTheDocument();
  });

  it("CA17: erro ao desfazer aparece na janela", async () => {
    const user = userEvent.setup();
    setup({ failWith: 409 });
    await ready();
    await user.click(await screen.findByRole("button", { name: "Desfazer" }));
    const undo = await screen.findByRole("dialog", { name: "Desfazer dia sem aula" });
    await user.click(within(undo).getByRole("button", { name: "Desfazer dia" }));
    expect(await within(undo).findByText("O dia já começou e não pode ser desfeito. Nada foi alterado.")).toBeInTheDocument();
  });

  it("CA17: carregando, vazio, erro com Tentar de novo e sem permissão", async () => {
    const user = userEvent.setup();
    let fail = true;
    setup({ list: () => (fail ? HttpResponse.json({ code: "INTERNAL_ERROR" }, { status: 500 }) : HttpResponse.json(view({ days: [], suggestions: [] }))) });
    expect(await screen.findByText("Carregando...")).toBeInTheDocument();
    const retry = await screen.findByRole("button", { name: "Tentar de novo" });
    fail = false;
    await user.click(retry);
    expect(await screen.findByText("Nenhum dia sem aula")).toBeInTheDocument();
  });

  it("CA17: sem permissão quando o servidor responde 403", async () => {
    setup({ list: () => HttpResponse.json({ code: "FORBIDDEN" }, { status: 403 }) });
    expect(await screen.findByText("Você não tem acesso")).toBeInTheDocument();
  });

  it("CA17: com mais de uma unidade, escolher refaz a consulta da unidade", async () => {
    const user = userEvent.setup();
    setup({ list: () => HttpResponse.json(view({ units: [{ id: "u1", name: "Centro" }, { id: "u2", name: "Norte" }] })) });
    await user.selectOptions(await screen.findByLabelText("Unidade"), "u2");
    await waitFor(() => expect(calls.at(-1)!.url.searchParams.get("unitId")).toBe("u2"));
  });

  it("CA19: o arquivo de tradução da tela não menciona lista de espera, presença nem frequência", () => {
    const text = JSON.stringify(JSON.parse(readFileSync(join(process.cwd(), "src/i18n/pt-BR.json"), "utf8")).noClassDays);
    expect(text).not.toMatch(/presen|frequ|espera/i);
  });

  it.each([
    ["claro", false],
    ["escuro", true],
  ])("CA17: a 360 px, página e janela não têm violações no axe no tema %s", async (_n, dark) => {
    const user = userEvent.setup();
    setDevicePrefersDark(dark);
    const { container } = setup({ width: 360 });
    await ready();
    await screen.findByText(/Recesso de verão/);
    expect(await axeViolations(container)).toEqual([]);
    await user.click(screen.getByRole("button", { name: "Novo dia sem aula" }));
    const dialog = await screen.findByRole("dialog", { name: "Novo dia sem aula" });
    await within(dialog).findByText("Centro: 2 aulas");
    expect(await axeViolations(dialog)).toEqual([]);
    expect(document.documentElement.scrollWidth).toBeLessThanOrEqual(window.innerWidth);
  });
});
