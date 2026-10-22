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
import { centsToReais, reaisToCents, type PlanOptions, type PlanRow } from "./usePlans";

const session = (role: string): SessionView => ({
  account: { id: "a", email: "x@x.com", name: "X" },
  links: [{ id: "l1", organizationId: "o", organizationName: "Alfa", role, unitIds: [] }],
  activeLinkId: "l1",
  twoFactor: { enabled: false, required: false },
});
const options = (cross: PlanOptions["crossUnitTraining"] = "plan"): PlanOptions => ({
  crossUnitTraining: cross,
  units: [
    { id: "u1", name: "Centro", modalities: [{ id: "m1", name: "Jiu-Jitsu" }, { id: "m2", name: "Judô" }] },
    { id: "u2", name: "Norte", modalities: [{ id: "m2", name: "Judô" }] },
  ],
});
const plan = (over: Partial<PlanRow> = {}): PlanRow => ({
  id: "p1",
  unit: { id: "u1", name: "Centro" },
  modality: { id: "m1", name: "Jiu-Jitsu" },
  name: "Mensal 2x",
  valueCents: 15000,
  weeklyFrequency: 2,
  validOtherUnits: false,
  studentsUsing: 0,
  status: "ACTIVE",
  ...over,
});

let sent: { method: string; url: string; body?: Record<string, unknown> }[];
let rows: PlanRow[];
let listCalls: number;
let impactCalls: string[];
let impactCounts = { unpaidCharges: 2, paidFutureCharges: 1 };

function setup(opts: { cross?: PlanOptions["crossUnitTraining"]; optionsResponse?: () => Response | Promise<Response>; post?: () => Response; activate?: () => Response } = {}) {
  sent = [];
  impactCalls = [];
  listCalls = 0;
  rows = [plan(), plan({ id: "p2", name: "Livre", status: "INACTIVE", weeklyFrequency: null })];
  window.history.replaceState(null, "", "/planos/lista");
  server.use(
    http.get("/api/v1/auth/session", () => HttpResponse.json(session("COORDINATOR"))),
    http.get("/api/v1/permissions", () => HttpResponse.json({ permissions: mockPermissions("COORDINATOR") })),
    http.get("/api/v1/units/reachable", () => HttpResponse.json({ allUnits: true, units: [{ id: "u1", name: "Centro", shortName: "c" }] })),
    http.get("/api/v1/modalities", () => HttpResponse.json([])),
    http.get("/api/v1/plans", () => {
      listCalls += 1;
      return HttpResponse.json({ items: rows, page: 1, size: 25, total: rows.length });
    }),
    http.get("/api/v1/plans/options", () => (opts.optionsResponse ? opts.optionsResponse() : HttpResponse.json(options(opts.cross)))),
    http.get("/api/v1/plans/p1/value-impact", ({ request }) => {
      impactCalls.push(new URL(request.url).searchParams.get("valueCents")!);
      return HttpResponse.json({ studentsAffected: 0, effectiveFrom: "NEXT_CHARGE", ...impactCounts });
    }),
    http.post("/api/v1/plans", async ({ request }) => {
      sent.push({ method: "POST", url: "/plans", body: (await request.json()) as Record<string, unknown> });
      if (opts.post) return opts.post();
      return HttpResponse.json(plan({ id: "n1", name: "Novo" }), { status: 201 });
    }),
    http.put("/api/v1/plans/p1", async ({ request }) => {
      sent.push({ method: "PUT", url: "/plans/p1", body: (await request.json()) as Record<string, unknown> });
      return HttpResponse.json(plan());
    }),
    http.post("/api/v1/plans/p1/deactivate", () => {
      sent.push({ method: "POST", url: "/plans/p1/deactivate" });
      rows = [plan({ status: "INACTIVE" }), rows[1]];
      return HttpResponse.json(rows[0]);
    }),
    http.post("/api/v1/plans/p2/activate", () => {
      sent.push({ method: "POST", url: "/plans/p2/activate" });
      return opts.activate ? opts.activate() : HttpResponse.json(plan({ id: "p2" }));
    }),
  );
  return render(
    <ThemeProvider>
      <MemoryRouter initialEntries={["/planos/lista"]}>
        <SessionProvider>
          <App />
        </SessionProvider>
      </MemoryRouter>
    </ThemeProvider>,
  );
}

const dialog = () => screen.findByRole("dialog");

describe("reais e centavos (R3)", () => {
  it("converte sem ponto flutuante e recusa o que está fora da faixa", () => {
    expect(reaisToCents("150")).toBe(15000);
    expect(reaisToCents("150,5")).toBe(15050);
    expect(reaisToCents("1.234,56")).toBe(123456);
    expect(reaisToCents("0,01")).toBe(1);
    expect(reaisToCents("999999,99")).toBe(99999999);
    for (const bad of ["", "0", "0,00", "abc", "1,234", "1000000", "-5", "10,5,1", "1.23"]) expect(reaisToCents(bad), bad).toBeNull();
    expect(centsToReais(15000)).toBe("150,00");
    expect(centsToReais(5)).toBe("0,05");
  });
});

describe("PlanFormDialog (RF-PLA-03)", () => {
  it("CA13: Novo plano abre com as modalidades da unidade; salvar envia centavos, recarrega a lista e fecha", async () => {
    const user = userEvent.setup();
    setup();
    await screen.findByText("Mensal 2x");
    await user.click(screen.getByRole("button", { name: "Novo plano" }));
    const d = within(await dialog());
    const modality = await d.findByLabelText("Modalidade");
    await user.selectOptions(d.getByLabelText("Unidade"), "u2");
    expect(within(modality).getAllByRole("option").map((o) => o.textContent)).toEqual(["Escolha", "Judô"]);
    await user.selectOptions(d.getByLabelText("Unidade"), "u1");
    expect(within(modality).getAllByRole("option").map((o) => o.textContent)).toEqual(["Escolha", "Jiu-Jitsu", "Judô"]);
    await user.click(d.getByRole("button", { name: "Salvar" }));
    expect(await d.findByText("Escolha a modalidade.")).toBeInTheDocument();
    expect(d.getByText("Informe um nome de 2 a 60 caracteres.")).toBeInTheDocument();
    expect(d.getByText("Informe um valor de R$ 0,01 a R$ 999.999,99.")).toBeInTheDocument();
    expect(sent).toEqual([]);

    await user.selectOptions(modality, "m2");
    await user.type(d.getByLabelText("Nome do plano"), "Trimestral");
    await user.type(d.getByLabelText("Valor mensal (R$)"), "1.234,5");
    await user.selectOptions(d.getByLabelText("Frequência semanal"), "3");
    await user.click(d.getByLabelText("Vale em outras unidades"));
    const before = listCalls;
    await user.click(d.getByRole("button", { name: "Salvar" }));
    await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull());
    expect(sent).toEqual([{ method: "POST", url: "/plans", body: { name: "Trimestral", valueCents: 123450, weeklyFrequency: 3, validOtherUnits: true, unitId: "u1", modalityId: "m2" } }]);
    await waitFor(() => expect(listCalls).toBeGreaterThan(before));
  });

  it("CA13: erro de campo do servidor aparece no campo e a janela continua aberta; 409 mostra o conflito", async () => {
    const user = userEvent.setup();
    setup({ post: () => HttpResponse.json({ code: "VALIDATION_ERROR", fields: [{ field: "name", message: "de 2 a 60 caracteres" }] }, { status: 400 }) });
    await screen.findByText("Mensal 2x");
    await user.click(screen.getByRole("button", { name: "Novo plano" }));
    const d = within(await dialog());
    await user.selectOptions(await d.findByLabelText("Unidade"), "u1");
    await user.selectOptions(d.getByLabelText("Modalidade"), "m1");
    await user.type(d.getByLabelText("Nome do plano"), "Plano");
    await user.type(d.getByLabelText("Valor mensal (R$)"), "100");
    await user.click(d.getByRole("button", { name: "Salvar" }));
    const field = await d.findByText("de 2 a 60 caracteres");
    expect(d.getByLabelText("Nome do plano")).toHaveAccessibleDescription(field.textContent!);
    expect(screen.getByRole("dialog")).toBeInTheDocument();
  });

  it("CA13: 409 de nome repetido mostra a mensagem de conflito", async () => {
    const user = userEvent.setup();
    setup({ post: () => HttpResponse.json({ code: "CONFLICT" }, { status: 409 }) });
    await screen.findByText("Mensal 2x");
    await user.click(screen.getByRole("button", { name: "Novo plano" }));
    const d = within(await dialog());
    await user.selectOptions(await d.findByLabelText("Unidade"), "u1");
    await user.selectOptions(d.getByLabelText("Modalidade"), "m1");
    await user.type(d.getByLabelText("Nome do plano"), "Plano");
    await user.type(d.getByLabelText("Valor mensal (R$)"), "100");
    await user.click(d.getByRole("button", { name: "Salvar" }));
    expect(await d.findByRole("alert")).toHaveTextContent("Já existe um plano ativo com este nome");
  });

  it("CA13, CA15: Editar abre preenchida, com unidade e modalidade fixas; sem mudança de valor não há bloco de impacto", async () => {
    const user = userEvent.setup();
    setup();
    await user.click(await screen.findByRole("button", { name: "Editar o plano Mensal 2x" }));
    const d = within(await dialog());
    expect(await d.findByLabelText("Nome do plano")).toHaveValue("Mensal 2x");
    expect(d.getByLabelText("Valor mensal (R$)")).toHaveValue("150,00");
    expect(d.getByLabelText("Frequência semanal")).toHaveValue("2");
    expect(d.queryByLabelText("Unidade")).toBeNull();
    expect(d.queryByLabelText("Modalidade")).toBeNull();
    expect(d.getByText("Centro")).toBeInTheDocument();
    expect(d.getByText("Jiu-Jitsu")).toBeInTheDocument();
    expect(d.queryByText("Efeito da mudança de valor")).toBeNull();
    await user.clear(d.getByLabelText("Nome do plano"));
    await user.type(d.getByLabelText("Nome do plano"), "Mensal 3x");
    await user.click(d.getByRole("button", { name: "Salvar" }));
    await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull());
    expect(sent).toEqual([{ method: "PUT", url: "/plans/p1", body: { name: "Mensal 3x", valueCents: 15000, weeklyFrequency: 2, validOtherUnits: false } }]);
    expect(impactCalls).toEqual([]);
  });

  it("CA15: ao mudar o valor aparecem alunos afetados, a partir de quando vale e as escolhas com os padrões; a consulta é refeita e as escolhas vão no PUT", async () => {
    const user = userEvent.setup();
    setup();
    await user.click(await screen.findByRole("button", { name: "Editar o plano Mensal 2x" }));
    const d = within(await dialog());
    const value = await d.findByLabelText("Valor mensal (R$)");
    await user.clear(value);
    await user.type(value, "180");
    const block = within(await d.findByRole("region", { name: "Efeito da mudança de valor" }));
    expect(await block.findByText("Alunos afetados: 0")).toBeInTheDocument();
    expect(block.getByText("O novo valor vale a partir da próxima cobrança gerada. Cobrança já paga não muda.")).toBeInTheDocument();
    expect(block.getByLabelText("Manter no valor antigo")).toBeChecked();
    expect(block.getByLabelText("Não cobrar a diferença")).toBeChecked();
    expect(impactCalls.at(-1)).toBe("18000");
    await user.type(value, ",5");
    await waitFor(() => expect(impactCalls.at(-1)).toBe("18050"));
    const again = within(await d.findByRole("region", { name: "Efeito da mudança de valor" })); // o bloco some enquanto o valor é ilegível
    await user.click(again.getByLabelText("Ajustar para o novo valor"));
    await user.click(again.getByLabelText("Cobrar a diferença em cobrança separada"));
    await user.click(d.getByRole("button", { name: "Salvar" }));
    await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull());
    expect(sent[0].body).toMatchObject({ valueCents: 18050, unpaidCharges: "ADJUST", paidFutureDifference: true });
  });

  it("CA29: sem cobranças afetadas a janela do plano não mostra as escolhas", async () => {
    const user = userEvent.setup();
    setup();
    impactCounts = { unpaidCharges: 0, paidFutureCharges: 0 };
    await user.click(await screen.findByRole("button", { name: "Editar o plano Mensal 2x" }));
    const d = within(await dialog());
    const value = await d.findByLabelText("Valor mensal (R$)");
    await user.clear(value);
    await user.type(value, "180");
    const block = within(await d.findByRole("region", { name: "Efeito da mudança de valor" }));
    expect(await block.findByText("Alunos afetados: 0")).toBeInTheDocument();
    expect(block.queryByLabelText("Ajustar para o novo valor")).toBeNull();
    expect(block.queryByLabelText("Cobrar a diferença em cobrança separada")).toBeNull();
  });

  it("CA14: com allowed ou denied a janela mostra a regra em texto e nenhum controle; com plan mostra o controle", async () => {
    const user = userEvent.setup();
    const view = setup({ cross: "allowed" });
    await user.click(await screen.findByRole("button", { name: "Editar o plano Mensal 2x" }));
    const d = within(await dialog());
    expect(await d.findByText(/permite treinar em outra unidade em todos os planos/)).toBeInTheDocument();
    expect(d.queryByLabelText("Vale em outras unidades")).toBeNull();
    view.unmount();
    setup({ cross: "denied" });
    await user.click(await screen.findByRole("button", { name: "Editar o plano Mensal 2x" }));
    expect(await within(await dialog()).findByText(/não permite treinar em outra unidade em nenhum plano/)).toBeInTheDocument();
    expect(within(screen.getByRole("dialog")).queryByRole("checkbox")).toBeNull();
  });

  it("CA16: carregar as opções mostra carregamento; falha mostra Tentar de novo e recarrega", async () => {
    const user = userEvent.setup();
    let fail = true;
    let release: () => void = () => undefined;
    const gate = new Promise<void>((r) => (release = r));
    setup({ optionsResponse: async () => (fail ? (await gate, HttpResponse.json({ code: "INTERNAL_ERROR" }, { status: 500 })) : HttpResponse.json(options())) });
    await screen.findByText("Mensal 2x");
    await user.click(screen.getByRole("button", { name: "Novo plano" }));
    expect(within(await dialog()).getByText("Carregando...")).toBeInTheDocument();
    release();
    const retry = await within(screen.getByRole("dialog")).findByRole("button", { name: "Tentar de novo" });
    fail = false;
    await user.click(retry);
    expect(await within(screen.getByRole("dialog")).findByLabelText("Nome do plano")).toBeInTheDocument();
  });

  it("CA16: sem permissão nas opções mostra o estado de sem permissão", async () => {
    const user = userEvent.setup();
    setup({ optionsResponse: () => HttpResponse.json({ code: "FORBIDDEN" }, { status: 403 }) });
    await screen.findByText("Mensal 2x");
    await user.click(screen.getByRole("button", { name: "Novo plano" }));
    expect(await within(await dialog()).findByText("Você não tem acesso")).toBeInTheDocument();
  });

  it("CA13: Desativar pede confirmação e só então chama o servidor; Ativar volta o plano à lista", async () => {
    const user = userEvent.setup();
    setup();
    await user.click(await screen.findByRole("button", { name: "Desativar o plano Mensal 2x" }));
    const d = within(await dialog());
    expect(d.getByText("Desativar o plano Mensal 2x?")).toBeInTheDocument();
    await user.click(d.getByRole("button", { name: "Cancelar" }));
    expect(sent).toEqual([]);
    await user.click(screen.getByRole("button", { name: "Desativar o plano Mensal 2x" }));
    await user.click(within(await dialog()).getByRole("button", { name: "Desativar" }));
    await waitFor(() => expect(sent).toEqual([{ method: "POST", url: "/plans/p1/deactivate" }]));
    await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull());
    expect(screen.queryByRole("button", { name: "Editar o plano Mensal 2x" })).toBeNull();

    sent.length = 0;
    await user.click(screen.getByRole("button", { name: "Ativar o plano Livre" }));
    await waitFor(() => expect(sent).toEqual([{ method: "POST", url: "/plans/p2/activate" }]));
  });

  it("CA13: ativar com homônimo ativo mostra o conflito", async () => {
    const user = userEvent.setup();
    setup({ activate: () => HttpResponse.json({ code: "CONFLICT" }, { status: 409 }) });
    await user.click(await screen.findByRole("button", { name: "Ativar o plano Livre" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Já existe um plano ativo com este nome");
  });

  it.each([
    ["claro", false],
    ["escuro", true],
  ])("CA16: a 360 px, com a janela de edição e o bloco de impacto abertos, não há violações no axe no tema %s", async (_n, dark) => {
    const user = userEvent.setup();
    setDevicePrefersDark(dark);
    window.innerWidth = 360;
    const { baseElement } = setup();
    await user.click(await screen.findByRole("button", { name: "Editar o plano Mensal 2x" }));
    const d = within(await dialog());
    const value = await d.findByLabelText("Valor mensal (R$)");
    await user.clear(value);
    await user.type(value, "200");
    await d.findByText("Alunos afetados: 0");
    expect(await axeViolations(baseElement)).toEqual([]);
  });
});
