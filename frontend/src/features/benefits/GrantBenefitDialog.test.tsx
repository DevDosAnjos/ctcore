import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { server } from "@/mocks/server";
import { enrollmentOf, modalityRow } from "@/features/students/enrollmentFixtures";
import { axeViolations } from "@/test/axe";
import { setDevicePrefersDark } from "@/test/device";
import { ThemeProvider } from "@/theme/ThemeProvider";
import { GrantBenefitDialog, type GrantTarget } from "./GrantBenefitDialog";
import type { BenefitRow } from "./useBenefits";

const fixed: GrantTarget = { studentId: "s1", studentName: "Ana Souza", modalityRowId: "m1", modalityName: "Jiu-Jitsu" };
const current: BenefitRow = {
  id: "b0",
  student: { id: "s1", name: "Ana Souza" },
  unit: { id: "u1", name: "Centro" },
  modality: { id: "mod1", name: "Jiu-Jitsu" },
  type: "PERCENT",
  value: 20,
  reason: "Antigo",
  decidedBy: "Mestre",
  grantedBy: "Gestora",
  validUntil: null,
  status: "ACTIVE",
  high: false,
  monthlyValueCents: 12000,
};
const previewOf = (over: Record<string, unknown> = {}) => ({
  plan: { id: "p1", name: "Plano Mensal", valueCents: 15000 },
  current: null,
  newMonthlyValueCents: 10500,
  effectiveFrom: "2026-12",
  replaces: false,
  high: false,
  ...over,
});

let granted: unknown[];
const onDone = vi.fn();
const onClose = vi.fn();

function setup(preview: Record<string, unknown> = previewOf(), post?: () => Response, props: { fixed?: GrantTarget } = { fixed }) {
  granted = [];
  onDone.mockClear();
  onClose.mockClear();
  server.use(
    http.post("/api/v1/benefits/preview", () => HttpResponse.json(preview)),
    http.post("/api/v1/benefits", async ({ request }) => {
      granted.push(await request.json());
      return post ? post() : HttpResponse.json(current, { status: 201 });
    }),
  );
  return render(
    <ThemeProvider>
      <GrantBenefitDialog {...props} onClose={onClose} onDone={onDone} />
    </ThemeProvider>,
  );
}

const fill = async (user: ReturnType<typeof userEvent.setup>, percent = "30") => {
  await user.type(screen.getByLabelText("Percentual (1 a 100)"), percent);
  await user.type(screen.getByLabelText("Motivo"), "Bolsa social");
  await user.type(screen.getByLabelText("Decidido por"), "Mestre Silva");
};

describe("janela Conceder benefício (RF-BEN-02, CA13, CA14)", () => {
  it("da ficha: mostra plano, benefício, nova mensalidade e o mês; confirma sem substituição", async () => {
    const user = userEvent.setup();
    setup();
    expect(screen.getByText("Ana Souza · Jiu-Jitsu")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Conceder" })).toBeDisabled();
    await fill(user);
    expect(await screen.findByText("Nova mensalidade: R$ 105,00")).toBeInTheDocument();
    expect(screen.getByText("Plano: Plano Mensal (R$ 150,00)")).toBeInTheDocument();
    expect(screen.getByText("Benefício: bolsa de 30%")).toBeInTheDocument();
    expect(screen.getByText("Vale a partir de 12/2026")).toBeInTheDocument();
    expect(screen.queryByText(/será encerrado/)).not.toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Conceder" }));
    await waitFor(() => expect(onDone).toHaveBeenCalled());
    expect(granted).toEqual([{ studentId: "s1", modalityRowId: "m1", type: "PERCENT", value: 30, reason: "Bolsa social", decidedBy: "Mestre Silva", validUntil: null, replaceExisting: false }]);
    expect(onClose).toHaveBeenCalled();
  });

  it("com benefício ativo avisa que será encerrado e confirma com replaceExisting; desconto em reais vai em centavos", async () => {
    const user = userEvent.setup();
    setup(previewOf({ current, replaces: true, newMonthlyValueCents: 10000 }));
    await user.selectOptions(screen.getByLabelText("Tipo"), "AMOUNT");
    await user.type(screen.getByLabelText("Valor do desconto"), "5000");
    await user.type(screen.getByLabelText("Motivo"), "Troca");
    await user.type(screen.getByLabelText("Decidido por"), "Direção");
    expect(await screen.findByText(/será encerrado e substituído/)).toBeInTheDocument();
    expect(screen.getByText(/\(20%\)/)).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Conceder" }));
    await waitFor(() => expect(granted).toHaveLength(1));
    expect(granted[0]).toMatchObject({ type: "AMOUNT", value: 5000, replaceExisting: true });
  });

  it("mensalidade zero informa que não gera mensalidade e que o aluno segue Ativo", async () => {
    const user = userEvent.setup();
    setup(previewOf({ newMonthlyValueCents: 0, high: true }));
    await fill(user, "100");
    expect(await screen.findByText("Esta modalidade não vai gerar mensalidade. O aluno segue Ativo.")).toBeInTheDocument();
    expect(screen.getByText("Benefício alto")).toBeInTheDocument();
  });

  it("mostra o erro do servidor no campo e não fecha", async () => {
    const user = userEvent.setup();
    setup(previewOf(), () => HttpResponse.json({ code: "VALIDATION_ERROR", fields: [{ field: "reason", message: "de 2 a 200 caracteres" }] }, { status: 400 }));
    await fill(user);
    await screen.findByText("Nova mensalidade: R$ 105,00");
    await user.click(screen.getByRole("button", { name: "Conceder" }));
    expect(await screen.findByText("de 2 a 200 caracteres")).toBeInTheDocument();
    expect(onClose).not.toHaveBeenCalled();
  });

  it("falha no resumo pede nova tentativa", async () => {
    const user = userEvent.setup();
    setup();
    server.use(http.post("/api/v1/benefits/preview", () => HttpResponse.json({ code: "INTERNAL_ERROR" }, { status: 500 })));
    await fill(user);
    expect(await screen.findByRole("button", { name: "Tentar de novo" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Conceder" })).toBeDisabled();
  });

  it("de /beneficios: busca o aluno, escolhe a modalidade e mostra o resumo; aluno sem modalidade ativa é vazio", async () => {
    const user = userEvent.setup();
    server.use(
      http.get("/api/v1/students", () => HttpResponse.json({ items: [{ id: "s1", name: "Ana Souza" }, { id: "s2", name: "Beto Lima" }], page: 1, size: 25, total: 2 })),
      http.get("/api/v1/students/s1/enrollment", () => HttpResponse.json(enrollmentOf([modalityRow(), modalityRow({ id: "m2", canEdit: false })]))),
      http.get("/api/v1/students/s2/enrollment", () => HttpResponse.json(enrollmentOf([modalityRow({ status: "ENDED" })]))),
    );
    setup(previewOf(), undefined, {});
    await user.type(screen.getByLabelText("Buscar aluno por nome"), "an");
    await user.selectOptions(await screen.findByLabelText("Aluno"), "s2");
    expect(await screen.findByText("Sem modalidade ativa")).toBeInTheDocument();
    await user.selectOptions(screen.getByLabelText("Aluno"), "s1");
    const select = await screen.findByLabelText("Modalidade");
    expect(screen.getAllByRole("option", { name: "Jiu-Jitsu · Centro" })).toHaveLength(1);
    await user.selectOptions(select, "m1");
    await fill(user);
    expect(await screen.findByText("Nova mensalidade: R$ 105,00")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Conceder" }));
    await waitFor(() => expect(granted[0]).toMatchObject({ studentId: "s1", modalityRowId: "m1" }));
  });

  it("sem permissão na busca de alunos mostra o estado de sem permissão", async () => {
    const user = userEvent.setup();
    server.use(http.get("/api/v1/students", () => HttpResponse.json({ code: "FORBIDDEN" }, { status: 403 })));
    setup(previewOf(), undefined, {});
    await user.type(screen.getByLabelText("Buscar aluno por nome"), "an");
    expect(await screen.findByText("Você não tem acesso")).toBeInTheDocument();
  });

  it.each([
    ["claro", false],
    ["escuro", true],
  ])("a 360 px não tem violações no axe no tema %s", async (_n, dark) => {
    const user = userEvent.setup();
    setDevicePrefersDark(dark);
    window.innerWidth = 360;
    setup(previewOf({ current, replaces: true }));
    await fill(user);
    await screen.findByText(/será encerrado e substituído/);
    expect(await axeViolations(document.body)).toEqual([]);
  });
});
