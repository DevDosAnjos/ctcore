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
import type { UnitDetail } from "./useUnitSettings";

const session = (role: string): SessionView => ({
  account: { id: "a", email: "x@x.com", name: "X" },
  links: [{ id: "l1", organizationId: "o", organizationName: "Alfa", role, unitIds: [] }],
  activeLinkId: "l1",
  twoFactor: { enabled: false, required: false },
});
const A = "00000000-0000-0000-0000-00000000000a";
const B = "00000000-0000-0000-0000-00000000000b";
const detail = (id: string, name: string): UnitDetail => ({
  id,
  name,
  shortName: name.toLowerCase().replace(" ", "-"),
  postalCode: null,
  address: null,
  city: null,
  latitude: null,
  longitude: null,
  phone: null,
  email: null,
  whatsapp: null,
  cnpj: null,
  active: true,
  version: 0,
  lastChange: null,
  settings: {
    dueDays: [1, 5, 10, 15],
    enrollmentFeeEnabled: false,
    enrollmentFeeCents: 0,
    feesCents: { cash: 0, pix: 0, bankSlip: 0, creditCard: 0, debitCard: 0, automaticDebit: 0, manualPix: 0, terminalCard: 0, transfer: 0 },
    pixKey: null,
    paymentAccount: "NOT_CONNECTED",
    reminderDays: [-3, 0, 3, 10],
    remindersEnabled: true,
    remindersHour: 9,
    teacherPermissions: { announcements: true, agendaEdit: true, studentContacts: true, trialClasses: true, studentRecord: false },
    directoryEnabled: false,
    trialLinkEnabled: true,
    selfRegistrationLinkEnabled: true,
  },
});
const reach = (d: UnitDetail) => ({ id: d.id, name: d.name, shortName: d.shortName });

type Sent = { name: string; settings: Record<string, unknown> } & Record<string, unknown>;
let stored: Record<string, UnitDetail>;
let puts: { id: string; body: Sent }[];

function setup(
  role: string,
  opts: { units?: UnitDetail[]; path?: string; read?: () => Response | Promise<Response>; reachable?: () => Response } = {},
) {
  const units = opts.units ?? [detail(A, "Unidade A")];
  stored = Object.fromEntries(units.map((u) => [u.id, u]));
  puts = [];
  server.use(
    http.get("/api/v1/auth/session", () => HttpResponse.json(session(role))),
    http.get("/api/v1/permissions", () => HttpResponse.json({ permissions: mockPermissions(role) })),
    http.get("/api/v1/units/reachable", opts.reachable ?? (() => HttpResponse.json({ allUnits: role === "COORDINATOR", units: units.map(reach) }))),
    http.get("/api/v1/units/:id/modalities", () => HttpResponse.json({ canManage: true, items: [{ id: "m1", name: "Judô", offered: true }] })),
    http.get("/api/v1/units/:id", opts.read ?? (({ params }) => HttpResponse.json(stored[String(params.id)]))),
    http.put("/api/v1/units/:id", async ({ request, params }) => {
      const body = (await request.json()) as Sent;
      puts.push({ id: String(params.id), body });
      if (body.settings.pixKey === "recusada") {
        return HttpResponse.json({ code: "VALIDATION_ERROR", fields: [{ field: "settings.pixKey", message: "chave recusada pelo servidor" }] }, { status: 400 });
      }
      const current = stored[String(params.id)]!;
      stored[current.id] = { ...current, ...body, settings: { ...current.settings, ...body.settings } } as UnitDetail;
      return HttpResponse.json(stored[current.id]);
    }),
  );
  return render(
    <ThemeProvider>
      <MemoryRouter initialEntries={[opts.path ?? "/configuracoes/unidade"]}>
        <SessionProvider>
          <App />
        </SessionProvider>
      </MemoryRouter>
    </ThemeProvider>,
  );
}

const ready = async () => {
  await screen.findByRole("heading", { name: "Configurações da unidade", level: 1 });
  await screen.findByLabelText("Nome da unidade");
};
const save = () => screen.getByRole("button", { name: "Salvar" });

describe("UnitSettingsPage (RF-ORG-04)", () => {
  it("CA17: o Coordenador altera dados, vencimentos, taxas e permissões e salva em centavos; vê a confirmação e os valores novos", async () => {
    const user = userEvent.setup();
    setup("COORDINATOR");
    await ready();
    await user.type(screen.getByLabelText("Telefone"), "21999998888");
    await user.click(screen.getByRole("checkbox", { name: "15" }));
    await user.click(screen.getByRole("checkbox", { name: "20" }));
    await user.click(screen.getByRole("checkbox", { name: "Cobrar taxa de matrícula" }));
    await user.clear(screen.getByLabelText("Valor da taxa de matrícula"));
    await user.type(screen.getByLabelText("Valor da taxa de matrícula"), "15000");
    expect(screen.getByLabelText("Valor da taxa de matrícula")).toHaveValue("R$ 150,00");
    await user.clear(screen.getByLabelText("Pix"));
    await user.type(screen.getByLabelText("Pix"), "199");
    expect(screen.getByRole("checkbox", { name: "Ver ficha do aluno" })).not.toBeChecked();
    await user.click(screen.getByRole("checkbox", { name: "Editar a agenda" }));
    await user.click(screen.getByRole("checkbox", { name: "Ver ficha do aluno" }));
    await user.click(save());
    expect(await screen.findByText("Dados salvos.")).toBeInTheDocument();
    expect(puts).toHaveLength(1);
    const { settings, ...data } = puts[0]!.body;
    expect(data).toMatchObject({ name: "Unidade A", phone: "21999998888", cnpj: null, latitude: null });
    expect(settings.dueDays).toEqual([1, 5, 10, 20]);
    expect(settings).toMatchObject({ enrollmentFeeEnabled: true, enrollmentFeeCents: 15000, reminderDays: [-3, 0, 3, 10] });
    expect((settings.feesCents as Record<string, number>).pix).toBe(199);
    expect(settings.teacherPermissions).toEqual({ announcements: true, agendaEdit: false, studentContacts: true, trialClasses: true, studentRecord: true });
    expect(screen.getByLabelText("Telefone")).toHaveValue("(21) 99999-8888");
    expect(save()).toBeDisabled();
  });

  it("CA17: erro 400 do servidor aparece junto do campo", async () => {
    const user = userEvent.setup();
    setup("COORDINATOR");
    await ready();
    await user.type(screen.getByLabelText("Chave Pix"), "recusada");
    await user.click(save());
    expect(await screen.findByText("chave recusada pelo servidor")).toBeInTheDocument();
    expect(screen.getByLabelText("Chave Pix")).toHaveAttribute("aria-invalid", "true");
    expect(screen.getByRole("alert", { name: "" })).toBeInTheDocument();
  });

  it("CA17: CEP novo vai sem coordenadas (o servidor busca)", async () => {
    const user = userEvent.setup();
    const withCoords = { ...detail(A, "Unidade A"), postalCode: "01001000", latitude: -1.5, longitude: 2.5 };
    setup("COORDINATOR", { units: [withCoords] });
    await ready();
    expect(screen.getByLabelText("CEP")).toHaveValue("01001-000");
    await user.clear(screen.getByLabelText("CEP"));
    await user.type(screen.getByLabelText("CEP"), "20040020");
    await user.click(save());
    await screen.findByText("Dados salvos.");
    expect(puts[0]!.body).toMatchObject({ postalCode: "20040020", latitude: null, longitude: null });
  });

  it("CA18: sem alterações salvas ainda mostra os padrões, com Salvar desabilitado; campo inválido bloqueia", async () => {
    const user = userEvent.setup();
    setup("COORDINATOR");
    await ready();
    expect(save()).toBeDisabled();
    expect(screen.getByLabelText("Dias de lembrete")).toHaveValue("-3, 0, 3, 10");
    expect(screen.getByRole("checkbox", { name: "Link público de aula experimental" })).toBeChecked();
    await user.type(screen.getByLabelText("E-mail"), "ruim");
    expect(screen.getByText("Confira o e-mail digitado.")).toBeInTheDocument();
    expect(save()).toBeDisabled();
    await user.clear(screen.getByLabelText("E-mail"));
    await user.clear(screen.getByLabelText("Dias de lembrete"));
    await user.type(screen.getByLabelText("Dias de lembrete"), "1, 1");
    expect(screen.getByText(/Use números inteiros de -30 a 60/)).toBeInTheDocument();
    expect(save()).toBeDisabled();
    await user.clear(screen.getByLabelText("Nome da unidade"));
    expect(screen.getByText("Informe o nome da unidade.")).toBeInTheDocument();
  });

  it("CA18: carregando, erro com Tentar de novo, sem permissão e Professor veem os estados certos", async () => {
    const user = userEvent.setup();
    const first = setup("COORDINATOR", { read: () => new Promise<Response>(() => {}) });
    expect(await screen.findByText("Carregando...")).toBeInTheDocument();
    first.unmount();

    let fail = true;
    const second = setup("COORDINATOR", {
      read: () => (fail ? HttpResponse.json({ code: "INTERNAL_ERROR" }, { status: 500 }) : HttpResponse.json(stored[A])),
    });
    const retry = await screen.findByRole("button", { name: "Tentar de novo" });
    fail = false;
    await user.click(retry);
    await ready();
    second.unmount();

    const third = setup("ADMINISTRATOR", { reachable: () => HttpResponse.json({ code: "FORBIDDEN" }, { status: 403 }) });
    expect(await screen.findByRole("heading", { name: "Você não tem acesso" })).toBeInTheDocument();
    third.unmount();

    setup("TEACHER");
    expect(await screen.findByRole("heading", { name: "Esta área não está disponível para o seu perfil" })).toBeInTheDocument();
  });

  it("CA18: sem nenhuma unidade para configurar mostra o estado vazio", async () => {
    setup("ADMINISTRATOR", { units: [] });
    expect(await screen.findByRole("heading", { name: "Nenhuma unidade para configurar" })).toBeInTheDocument();
  });

  it("CA10: Administrador com duas unidades troca a unidade no topo e o formulário recarrega com a outra", async () => {
    const user = userEvent.setup();
    setup("ADMINISTRATOR", { units: [detail(A, "Unidade A"), detail(B, "Unidade B")] });
    await ready();
    expect(screen.getByLabelText("Nome da unidade")).toHaveValue("Unidade A");
    expect(screen.queryByRole("option", { name: "Todas as unidades" })).not.toBeInTheDocument();
    await user.selectOptions(screen.getByLabelText("Unidade ativa"), B);
    await waitFor(() => expect(screen.getByLabelText("Nome da unidade")).toHaveValue("Unidade B"));
    expect(screen.queryByLabelText("Unidade")).not.toBeInTheDocument();
  });

  it("CA7: Coordenador em \"Todas as unidades\" vê o pedido de escolha e nenhum campo; ao escolher uma, o formulário aparece", async () => {
    const user = userEvent.setup();
    setup("COORDINATOR", { units: [detail(A, "Unidade A"), detail(B, "Unidade B")] });
    expect(await screen.findByText(/Escolha uma unidade no topo/)).toBeInTheDocument();
    expect(screen.queryByLabelText("Nome da unidade")).not.toBeInTheDocument();
    await user.selectOptions(screen.getByLabelText("Unidade ativa"), B);
    await waitFor(() => expect(screen.getByLabelText("Nome da unidade")).toHaveValue("Unidade B"));
  });

  it("CA20: Mercado Pago não conectado aparece por texto e ícone, com o aviso, sem botão de conectar", async () => {
    setup("COORDINATOR");
    await ready();
    const status = screen.getByText("Mercado Pago não conectado");
    expect(status.parentElement?.parentElement?.querySelector("svg")).not.toBeNull();
    expect(screen.getByText(/chega em uma próxima entrega/)).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /conectar/i })).not.toBeInTheDocument();
  });

  it("CA20: conectado mostra o texto de conectado", async () => {
    const connected = detail(A, "Unidade A");
    connected.settings.paymentAccount = "CONNECTED";
    setup("COORDINATOR", { units: [connected] });
    await ready();
    expect(screen.getByText("Mercado Pago conectado")).toBeInTheDocument();
  });

  it.each([
    ["claro", false],
    ["escuro", true],
  ])("CA18: a 360 px não tem violações no axe no tema %s, com erros visíveis", async (_n, dark) => {
    const user = userEvent.setup();
    setDevicePrefersDark(dark);
    window.innerWidth = 360;
    const { container } = setup("COORDINATOR");
    await ready();
    expect(await axeViolations(container)).toEqual([]);
    await user.clear(screen.getByLabelText("Nome da unidade"));
    await user.type(screen.getByLabelText("E-mail"), "ruim");
    expect(await axeViolations(container)).toEqual([]);
  });

  it("as permissões do Professor e a Divulgação estão na tela", async () => {
    setup("COORDINATOR");
    await ready();
    const teacher = screen.getByText("O que o Professor pode fazer nesta unidade.").closest("fieldset")!;
    expect(within(teacher).getAllByRole("checkbox")).toHaveLength(5);
    expect(screen.getByRole("heading", { name: "Divulgação", level: 2 })).toBeInTheDocument();
  });
});

const BIA = { at: "2026-05-20T15:30:00Z", by: "Bia" };
const DIALOG = "Estes dados foram alterados por outra pessoa";

// A outra pessoa já salvou: o servidor está na versão 3 com outro nome; o primeiro PUT responde 409.
function conflicting(putStatuses: number[] = [409]) {
  const sent: { version: string | null; body: Sent }[] = [];
  const reads = { count: 0 };
  setup("COORDINATOR", {
    units: [{ ...detail(A, "Unidade A"), name: "Nome da outra pessoa", version: 3, lastChange: BIA }],
    read: () => {
      reads.count += 1;
      return HttpResponse.json(stored[A]);
    },
  });
  server.use(
    http.put("/api/v1/units/:id", async ({ request }) => {
      const body = (await request.json()) as Sent;
      sent.push({ version: request.headers.get("If-Match"), body });
      const status = putStatuses[Math.min(sent.length - 1, putStatuses.length - 1)];
      if (status === 409) return HttpResponse.json({ code: "EDIT_CONFLICT", message: "x", changed: false, fields: [], traceId: "t" }, { status });
      stored[A] = { ...stored[A]!, ...body, version: stored[A]!.version + 1 } as unknown as UnitDetail;
      return HttpResponse.json(stored[A]);
    }),
  );
  return { sent, reads };
}

describe("UnitSettingsPage: última alteração e edição simultânea (RF-ORG-08, RNF-15)", () => {
  it("CA9: mostra quem alterou por último e quando, ou 'Ainda sem alterações'", async () => {
    setup("ADMINISTRATOR", { units: [{ ...detail(A, "Unidade A"), lastChange: BIA }] });
    await ready();
    expect(screen.getByText("Última alteração em 20/05/2026 12h30 por Bia")).toBeInTheDocument();
  });

  it("CA9: sem registro mostra 'Ainda sem alterações'", async () => {
    setup("COORDINATOR");
    await ready();
    expect(screen.getByText("Ainda sem alterações")).toBeInTheDocument();
  });

  it("CA10: o 409 abre a janela com quem alterou, os campos diferentes com os dois valores e os dois botões; a gravação levou o If-Match lido", async () => {
    const user = userEvent.setup();
    const { sent } = conflicting();
    await ready();
    await user.clear(screen.getByLabelText("Nome da unidade"));
    await user.type(screen.getByLabelText("Nome da unidade"), "Meu nome");
    await user.click(await screen.findByRole("checkbox", { name: "Editar a agenda" }));
    await user.click(save());
    const dialog = await screen.findByRole("dialog", { name: DIALOG });
    expect(sent[0]?.version).toBe("3");
    expect(await within(dialog).findByText("Última alteração em 20/05/2026 12h30 por Bia")).toBeInTheDocument();
    const nome = within(dialog).getByText("Nome da unidade").closest("li")!;
    expect(nome).toHaveTextContent("Seu valor: Meu nome");
    expect(nome).toHaveTextContent("Valor atual: Nome da outra pessoa");
    const agenda = within(dialog).getByText("Editar a agenda").closest("li")!;
    expect(agenda).toHaveTextContent("Seu valor: Não");
    expect(agenda).toHaveTextContent("Valor atual: Sim");
    expect(within(dialog).getAllByRole("listitem")).toHaveLength(2);
    expect(within(dialog).getByRole("button", { name: "Recarregar" })).toBeInTheDocument();
    expect(within(dialog).getByRole("button", { name: "Sobrescrever" })).toBeInTheDocument();
  });

  it("CA10: Recarregar preenche o formulário com o atual, fecha e devolve o foco ao Salvar", async () => {
    const user = userEvent.setup();
    conflicting();
    await ready();
    await user.clear(screen.getByLabelText("Nome da unidade"));
    await user.type(screen.getByLabelText("Nome da unidade"), "Meu nome");
    await user.click(save());
    const dialog = await screen.findByRole("dialog", { name: DIALOG });
    await user.click(await within(dialog).findByRole("button", { name: "Recarregar" }));
    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
    expect(screen.getByLabelText("Nome da unidade")).toHaveValue("Nome da outra pessoa");
    expect(save()).toBeDisabled();
  });

  it("CA10: Sobrescrever regrava com a versão atual e mostra 'Dados salvos.'", async () => {
    const user = userEvent.setup();
    const { sent } = conflicting([409, 200]);
    await ready();
    await user.clear(screen.getByLabelText("Nome da unidade"));
    await user.type(screen.getByLabelText("Nome da unidade"), "Meu nome");
    await user.click(save());
    const dialog = await screen.findByRole("dialog", { name: DIALOG });
    await user.click(await within(dialog).findByRole("button", { name: "Sobrescrever" }));
    expect(await screen.findByText("Dados salvos.")).toBeInTheDocument();
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(sent.map((s) => s.version)).toEqual(["3", "3"]);
    expect(sent[1]?.body.name).toBe("Meu nome");
    expect(screen.getByLabelText("Nome da unidade")).toHaveValue("Meu nome");
  });

  it("CA10: novo 409 durante o Sobrescrever reabre o aviso", async () => {
    const user = userEvent.setup();
    const { sent, reads } = conflicting([409, 409]);
    await ready();
    await user.type(screen.getByLabelText("Nome da unidade"), "!");
    await user.click(save());
    const dialog = await screen.findByRole("dialog", { name: DIALOG });
    await user.click(await within(dialog).findByRole("button", { name: "Sobrescrever" }));
    await waitFor(() => expect(sent).toHaveLength(2));
    await waitFor(() => expect(reads.count).toBe(3));
    expect(await within(screen.getByRole("dialog")).findByRole("button", { name: "Sobrescrever" })).toBeEnabled();
    expect(screen.queryByText("Dados salvos.")).not.toBeInTheDocument();
  });

  it("CA10: erro de rede na consulta do estado atual mostra o erro com 'Tentar de novo'", async () => {
    const user = userEvent.setup();
    conflicting();
    await ready();
    await user.type(screen.getByLabelText("Nome da unidade"), "!");
    server.use(http.get("/api/v1/units/:id", () => HttpResponse.error(), { once: true }));
    await user.click(save());
    const dialog = await screen.findByRole("dialog", { name: DIALOG });
    expect(await within(dialog).findByText("Não foi possível consultar o estado atual. Nada foi alterado.")).toBeInTheDocument();
    await user.click(within(dialog).getByRole("button", { name: "Tentar de novo" }));
    expect(await within(dialog).findByRole("button", { name: "Sobrescrever" })).toBeInTheDocument();
  });

  it.each([
    ["claro", false],
    ["escuro", true],
  ])("CA11: a janela de conflito a 360 px não tem violações no axe no tema %s", async (_n, dark) => {
    const user = userEvent.setup();
    setDevicePrefersDark(dark);
    window.innerWidth = 360;
    conflicting();
    await ready();
    await user.type(screen.getByLabelText("Nome da unidade"), "!");
    await user.click(save());
    const dialog = await screen.findByRole("dialog", { name: DIALOG });
    await within(dialog).findByRole("button", { name: "Sobrescrever" });
    expect(await axeViolations(document.body)).toEqual([]);
  });
});

describe("UnitSettingsPage: lembretes automáticos (RF-COB-09)", () => {
  it("CA7 / CA10: mostra interruptor, hora, dias, padrão e aviso fixo; salvar envia os três campos", async () => {
    const user = userEvent.setup();
    setup("COORDINATOR");
    await ready();
    expect(screen.getByRole("checkbox", { name: /Enviar lembretes automáticos/ })).toBeChecked();
    expect(screen.getByLabelText("Hora dos lembretes")).toHaveValue("9");
    expect(screen.getByLabelText("Dias de lembrete")).toHaveValue("-3, 0, 3, 10");
    expect(screen.getByText(/Padrão: 3 dias antes/)).toBeInTheDocument();
    expect(screen.getByText(/Aviso fixo: 5 dias antes do bloqueio/)).toBeInTheDocument();
    await user.clear(screen.getByLabelText("Hora dos lembretes"));
    await user.type(screen.getByLabelText("Hora dos lembretes"), "14");
    await user.click(save());
    expect(await screen.findByText("Dados salvos.")).toBeInTheDocument();
    expect(puts[0]!.body.settings).toMatchObject({ remindersEnabled: true, remindersHour: 14, reminderDays: [-3, 0, 3, 10] });
  });

  it("CA8: hora 25 ou vazia mostra o erro no campo, bloqueia o envio e o Enter leva o foco ao campo", async () => {
    const user = userEvent.setup();
    setup("COORDINATOR");
    await ready();
    const hour = screen.getByLabelText("Hora dos lembretes");
    await user.clear(hour);
    await user.type(hour, "25");
    expect(screen.getByText("Informe um número inteiro de 0 a 23.")).toBeInTheDocument();
    expect(hour).toHaveAttribute("aria-invalid", "true");
    expect(save()).toBeDisabled();
    await user.type(screen.getByLabelText("Nome da unidade"), "{Enter}");
    expect(hour).toHaveFocus();
    expect(puts).toHaveLength(0);
    await user.clear(hour);
    expect(screen.getByText("Informe um número inteiro de 0 a 23.")).toBeInTheDocument();
  });

  it("CA9: desligado, hora e dias ficam desabilitados com o aviso; ao ligar voltam editáveis com os valores", async () => {
    const user = userEvent.setup();
    setup("COORDINATOR");
    await ready();
    await user.click(screen.getByRole("checkbox", { name: /Enviar lembretes automáticos/ }));
    expect(screen.getByLabelText("Hora dos lembretes")).toBeDisabled();
    expect(screen.getByLabelText("Dias de lembrete")).toBeDisabled();
    expect(screen.getByText(/Nenhum lembrete automático será enviado/)).toBeInTheDocument();
    await user.click(screen.getByRole("checkbox", { name: /Enviar lembretes automáticos/ }));
    expect(screen.getByLabelText("Hora dos lembretes")).toBeEnabled();
    expect(screen.getByLabelText("Hora dos lembretes")).toHaveValue("9");
    expect(screen.getByLabelText("Dias de lembrete")).toHaveValue("-3, 0, 3, 10");
    expect(screen.queryByText(/Nenhum lembrete automático será enviado/)).not.toBeInTheDocument();
  });

  it("CA11: o conflito lista Lembretes automáticos e Hora dos lembretes", async () => {
    const user = userEvent.setup();
    conflicting();
    await ready();
    stored[A] = { ...stored[A]!, settings: { ...stored[A]!.settings, remindersEnabled: false, remindersHour: 14 } };
    await user.type(screen.getByLabelText("Nome da unidade"), "!");
    await user.click(save());
    const dialog = await screen.findByRole("dialog", { name: DIALOG });
    const on = (await within(dialog).findByText("Lembretes automáticos")).closest("li")!;
    expect(on).toHaveTextContent("Seu valor: Sim");
    expect(on).toHaveTextContent("Valor atual: Não");
    expect(within(dialog).getByText("Hora dos lembretes").closest("li")).toHaveTextContent("Valor atual: 14");
  });

  it.each([
    ["claro", false],
    ["escuro", true],
  ])("CA12: a 360 px, com lembretes desligados e com erro, não tem violações no axe no tema %s", async (_n, dark) => {
    const user = userEvent.setup();
    setDevicePrefersDark(dark);
    window.innerWidth = 360;
    const { container } = setup("COORDINATOR");
    await ready();
    await user.click(screen.getByRole("checkbox", { name: /Enviar lembretes automáticos/ }));
    expect(await axeViolations(container)).toEqual([]);
    await user.click(screen.getByRole("checkbox", { name: /Enviar lembretes automáticos/ }));
    await user.clear(screen.getByLabelText("Hora dos lembretes"));
    await user.type(screen.getByLabelText("Hora dos lembretes"), "25");
    expect(await axeViolations(container)).toEqual([]);
  });
});
