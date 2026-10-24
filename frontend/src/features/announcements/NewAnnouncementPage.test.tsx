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
import type { AnnouncementDraft, AnnouncementTargets } from "./types";

const session = (role: string): SessionView => ({
  account: { id: "a", email: "x@x.com", name: "X" },
  links: [{ id: "l1", organizationId: "o", organizationName: "Alfa", role, unitIds: [] }],
  activeLinkId: "l1",
  twoFactor: { enabled: false, required: false },
});
const full: AnnouncementTargets = {
  organization: true,
  units: [{ id: "u1", name: "Unidade Centro" }],
  modalities: [{ id: "m1", name: "Jiu-Jitsu", unitId: "u1" }],
  classes: [
    { id: "k1", name: "Jiu-Jitsu Adulto", unitId: "u1", modalityId: "m1", groupLink: "https://chat.example.com/jj" },
    { id: "k2", name: "Judô Infantil", unitId: "u1", modalityId: "m2", groupLink: null },
  ],
};
const teacher: AnnouncementTargets = { organization: false, units: [], modalities: [], classes: [full.classes[0]] };
const preview = (recipients: number, whatsapp: unknown = null) => HttpResponse.json({ recipients, portal: recipients, email: recipients - 1, whatsapp, channels: ["PORTAL"] });
const failure = (status: number, code: string, fields: { field: string; message: string }[] = []) => HttpResponse.json({ code, message: "m", fields, traceId: "t" }, { status });

let previews: AnnouncementDraft[];
let creates: AnnouncementDraft[];

type Options = { role?: string; targets?: AnnouncementTargets; targetsResponse?: () => Response; create?: () => Response; path?: string };
function setup({ role = "COORDINATOR", targets = full, targetsResponse, create, path = "/comunicados/novo" }: Options = {}) {
  previews = [];
  creates = [];
  window.history.replaceState(null, "", path);
  server.use(
    http.get("/api/v1/auth/session", () => HttpResponse.json(session(role))),
    http.get("/api/v1/permissions", () => HttpResponse.json({ permissions: mockPermissions(role as never) })),
    http.get("/api/v1/units/reachable", () => HttpResponse.json({ allUnits: true, units: [] })),
    http.get("/api/v1/announcements/targets", () => targetsResponse?.() ?? HttpResponse.json(targets)),
    http.post("/api/v1/announcements/preview", async ({ request }) => {
      const draft = (await request.json()) as AnnouncementDraft;
      previews.push(draft);
      return preview(draft.targetKind === "CLASS" ? 8 : 25, draft.channels.includes("WHATSAPP") ? { mode: "GROUP", groupLink: "https://chat.example.com/jj" } : null);
    }),
    http.post("/api/v1/announcements", async ({ request }) => {
      creates.push((await request.json()) as AnnouncementDraft);
      return create?.() ?? HttpResponse.json({ id: "n1", status: "SENT", recipients: 8 }, { status: 201 });
    }),
    http.get("/api/v1/announcements", () => HttpResponse.json({ items: [], page: 1, size: 25, total: 0 })),
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

const ready = () => screen.findByRole("heading", { name: "Novo comunicado", level: 1 });
const optionNames = () => within(screen.getByLabelText("Destino")).getAllByRole("option").map((o) => o.textContent);

describe("NewAnnouncementPage (RF-COM-02)", () => {
  it("CA22: o Coordenador vê todas as unidades, unidade, modalidade e turma; o Professor, só as turmas dele", async () => {
    const { unmount } = setup();
    await ready();
    await screen.findByLabelText("Destino");
    expect(optionNames()).toEqual(["Escolha o destino", "Todas as unidades", "Unidade Centro", "Jiu-Jitsu · Unidade Centro", "Jiu-Jitsu Adulto", "Judô Infantil"]);
    unmount();
    setup({ role: "TEACHER", targets: teacher });
    await ready();
    await screen.findByLabelText("Destino");
    expect(optionNames()).toEqual(["Escolha o destino", "Jiu-Jitsu Adulto"]);
  });

  it("CA23: sem destino não há prévia e Enviar fica desabilitado; com destino a prévia conta pessoas e canais e se atualiza com os canais", async () => {
    const user = userEvent.setup();
    setup();
    await ready();
    await screen.findByLabelText("Destino");
    expect(screen.queryByRole("status", { name: "Prévia do envio" })).toBeNull();
    expect(screen.getByRole("button", { name: "Enviar comunicado" })).toBeDisabled();
    await user.selectOptions(screen.getByLabelText("Destino"), "Jiu-Jitsu Adulto");
    expect(await screen.findByText("Este aviso vai para 8 pessoas")).toBeInTheDocument();
    expect(screen.getByText("Por portal (8), e-mail (7)")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Enviar comunicado" })).toBeEnabled();
    await user.click(screen.getByRole("checkbox", { name: "WhatsApp" }));
    expect(await screen.findByText(/e WhatsApp \(grupo da turma\)/)).toBeInTheDocument();
    expect(previews.at(-1)).toMatchObject({ targetKind: "CLASS", classId: "k1", channels: ["PORTAL", "WHATSAPP"] });
    await user.selectOptions(screen.getByLabelText("Destino"), "Unidade Centro");
    expect(await screen.findByText("Este aviso vai para 25 pessoas")).toBeInTheDocument();
  });

  it("CA23: escolher o tipo preenche título e texto; mudar depois de editar pergunta antes de trocar", async () => {
    const user = userEvent.setup();
    setup({ path: "/comunicados/novo?classId=k1" });
    await ready();
    await screen.findByLabelText("Destino");
    await user.selectOptions(screen.getByLabelText("Tipo"), "Mudança de horário");
    expect(screen.getByLabelText("Título")).toHaveValue("Mudança de horário");
    expect(screen.getByLabelText("Texto")).toHaveValue("A aula de Jiu-Jitsu Adulto muda de horário a partir de [data]. O novo horário é [horário].");
    await user.selectOptions(screen.getByLabelText("Tipo"), "Troca de professor");
    expect(screen.getByLabelText("Título")).toHaveValue("Troca de professor");
    await user.type(screen.getByLabelText("Título"), " nova");
    await user.selectOptions(screen.getByLabelText("Tipo"), "Aviso geral");
    expect(screen.getByRole("alert")).toHaveTextContent("substitui o título e o texto");
    await user.click(screen.getByRole("button", { name: "Manter meu texto" }));
    expect(screen.getByLabelText("Título")).toHaveValue("Troca de professor nova");
    await user.selectOptions(screen.getByLabelText("Tipo"), "Aviso geral");
    await user.click(screen.getByRole("button", { name: "Trocar o texto" }));
    expect(screen.getByLabelText("Título")).toHaveValue("Aviso geral");
    expect(screen.getByLabelText("Texto")).toHaveValue("");
  });

  it("CA26: a URL preenche turma, tipo, data e horário no texto pronto", async () => {
    setup({ path: "/comunicados/novo?classId=k1&type=CANCELED_CLASS&date=2026-11-03&time=19:00" });
    await ready();
    await screen.findByLabelText("Destino");
    expect(screen.getByLabelText("Destino")).toHaveValue("CLASS:k1");
    expect(screen.getByLabelText("Tipo")).toHaveValue("CANCELED_CLASS");
    expect(screen.getByLabelText("Texto")).toHaveValue("A aula de Jiu-Jitsu Adulto do dia 03/11/2026, às 19:00, foi cancelada. Avisaremos quando houver reposição.");
    expect(await screen.findByText("Este aviso vai para 8 pessoas")).toBeInTheDocument();
  });

  it("CA26: o dia sem aula chega pela URL com a unidade, o período, o motivo e o vínculo ao dia", async () => {
    const user = userEvent.setup();
    setup({ path: "/comunicados/novo?type=NO_CLASS_DAY&unitId=u1&startsOn=2026-12-24&endsOn=2026-12-26&reason=Recesso&noClassDayId=d1" });
    await ready();
    await screen.findByLabelText("Destino");
    expect(screen.getByLabelText("Destino")).toHaveValue("UNIT:u1");
    expect(screen.getByLabelText("Texto")).toHaveValue("Não haverá aula em 24/12/2026 a 26/12/2026: Recesso.");
    await screen.findByText("Este aviso vai para 25 pessoas");
    await user.click(screen.getByRole("button", { name: "Enviar comunicado" }));
    await waitFor(() => expect(creates).toHaveLength(1));
    expect(creates[0]).toMatchObject({ targetKind: "UNIT", unitId: "u1", type: "NO_CLASS_DAY", noClassDayId: "d1" });
  });

  it("CA25: o campo de agendamento só aparece em Agendar; enviar leva a /comunicados com a confirmação", async () => {
    const user = userEvent.setup();
    setup({ path: "/comunicados/novo?classId=k1" });
    await ready();
    await screen.findByLabelText("Destino");
    expect(screen.queryByLabelText("Data e hora do envio")).toBeNull();
    await user.type(screen.getByLabelText("Título"), "Recesso");
    await user.type(screen.getByLabelText("Texto"), "Sem aula na sexta.");
    await user.click(screen.getByRole("checkbox", { name: "E-mail" }));
    await user.click(screen.getByRole("radio", { name: "Agendar" }));
    expect(screen.getByRole("button", { name: "Agendar comunicado" })).toBeDisabled();
    await user.type(screen.getByLabelText("Data e hora do envio"), "2099-01-01T10:00");
    await user.click(screen.getByRole("radio", { name: "Enviar agora" }));
    expect(screen.queryByLabelText("Data e hora do envio")).toBeNull();
    await user.click(screen.getByRole("button", { name: "Enviar comunicado" }));
    expect(await screen.findByRole("heading", { name: "Comunicados", level: 1 })).toBeInTheDocument();
    expect(screen.getByText("Comunicado enviado.")).toBeInTheDocument();
    expect(creates).toEqual([{ targetKind: "CLASS", classId: "k1", type: "GENERAL", title: "Recesso", body: "Sem aula na sexta.", channels: ["PORTAL", "EMAIL"] }]);
  });

  it("CA25: agendar envia o instante escolhido e a confirmação é de agendado", async () => {
    const user = userEvent.setup();
    setup({ path: "/comunicados/novo?classId=k1", create: () => HttpResponse.json({ id: "n1", status: "SCHEDULED", recipients: null }, { status: 201 }) });
    await ready();
    await screen.findByLabelText("Destino");
    await user.type(screen.getByLabelText("Título"), "Aviso");
    await user.type(screen.getByLabelText("Texto"), "Texto");
    await user.click(screen.getByRole("radio", { name: "Agendar" }));
    await user.type(screen.getByLabelText("Data e hora do envio"), "2099-01-01T10:00");
    await user.type(screen.getByLabelText("Fixar até"), "2099-01-05");
    await user.click(screen.getByRole("button", { name: "Agendar comunicado" }));
    expect(await screen.findByText("Comunicado agendado.")).toBeInTheDocument();
    expect(creates[0].sendAt).toBe(new Date("2099-01-01T10:00").toISOString());
    expect(creates[0].pinnedUntil).toBe("2099-01-05");
  });

  it("CA24: WhatsApp em grupo deixa o link do grupo na confirmação", async () => {
    const user = userEvent.setup();
    setup({ path: "/comunicados/novo?classId=k1" });
    await ready();
    await screen.findByLabelText("Destino");
    await user.type(screen.getByLabelText("Título"), "Aviso");
    await user.type(screen.getByLabelText("Texto"), "Texto");
    await user.click(screen.getByRole("checkbox", { name: "WhatsApp" }));
    await screen.findByText(/grupo da turma/);
    await user.click(screen.getByRole("button", { name: "Enviar comunicado" }));
    expect(await screen.findByRole("link", { name: "Abrir o grupo da turma" })).toHaveAttribute("href", "https://chat.example.com/jj");
  });

  it("CA24: 400 com campo mostra a mensagem junto do campo, mantém o formulário e leva o foco ao primeiro erro", async () => {
    const user = userEvent.setup();
    setup({ path: "/comunicados/novo?classId=k1", create: () => failure(400, "VALIDATION_ERROR", [{ field: "body", message: "x" }, { field: "title", message: "y" }]) });
    await ready();
    await screen.findByLabelText("Destino");
    await user.type(screen.getByLabelText("Texto"), "Meu texto");
    await user.click(screen.getByRole("button", { name: "Enviar comunicado" }));
    const title = screen.getByLabelText("Título");
    await waitFor(() => expect(title).toHaveFocus());
    expect(title).toHaveAccessibleDescription("Informe o título, de 1 a 120 caracteres.");
    expect(screen.getByLabelText("Texto")).toHaveValue("Meu texto");
    expect(screen.getByText(/Informe o texto, de 1 a 2.000/)).toBeInTheDocument();
  });

  it("CA24: 409 do limite e 403 aparecem no topo, sem perder o que foi escrito", async () => {
    const user = userEvent.setup();
    let status = 409;
    setup({ path: "/comunicados/novo?classId=k1", create: () => (status === 409 ? failure(409, "ANNOUNCEMENT_DAILY_LIMIT") : failure(403, "FORBIDDEN")) });
    await ready();
    await screen.findByLabelText("Destino");
    await user.type(screen.getByLabelText("Título"), "Aviso");
    await user.type(screen.getByLabelText("Texto"), "Texto");
    await user.click(screen.getByRole("button", { name: "Enviar comunicado" }));
    const alert = await screen.findByText(/limite de 20 comunicados por dia/);
    await waitFor(() => expect(alert).toHaveFocus());
    expect(screen.getByLabelText("Título")).toHaveValue("Aviso");
    status = 403;
    await user.click(screen.getByRole("button", { name: "Enviar comunicado" }));
    expect(await screen.findByText("Você não pode enviar comunicado para este destino.")).toBeInTheDocument();
    expect(screen.getByLabelText("Texto")).toHaveValue("Texto");
  });

  it("CA25: carregando, erro com Tentar de novo, sem permissão e vazio do Professor sem turma", async () => {
    const user = userEvent.setup();
    let mode: "error" | "ok" = "error";
    const first = setup({ targetsResponse: () => (mode === "error" ? failure(500, "X") : HttpResponse.json(full)) });
    expect(screen.getByText("Carregando...")).toBeInTheDocument();
    const retry = await screen.findByRole("button", { name: "Tentar de novo" });
    mode = "ok";
    await user.click(retry);
    expect(await screen.findByLabelText("Destino")).toBeInTheDocument();
    first.unmount();
    const second = setup({ targetsResponse: () => failure(403, "FORBIDDEN") });
    expect(await screen.findByText("Você não tem acesso")).toBeInTheDocument();
    second.unmount();
    setup({ role: "TEACHER", targets: { organization: false, units: [], modalities: [], classes: [] } });
    expect(await screen.findByText("Você não tem turmas liberadas para enviar comunicado.")).toBeInTheDocument();
  });

  it("CA25: a 360 px o axe não acusa violação nos dois temas", async () => {
    for (const dark of [false, true]) {
      setDevicePrefersDark(dark);
      window.innerWidth = 360;
      const { container, unmount } = setup({ path: "/comunicados/novo?classId=k1&type=CANCELED_CLASS&date=2026-11-03&time=19:00" });
      await ready();
      await screen.findByText("Este aviso vai para 8 pessoas");
      expect(await axeViolations(container)).toEqual([]);
      unmount();
    }
  });
});
