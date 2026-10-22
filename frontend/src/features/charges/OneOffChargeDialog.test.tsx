import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { delay, http, HttpResponse } from "msw";
import { enrollmentOf, modalityRow } from "@/features/students/enrollmentFixtures";
import { server } from "@/mocks/server";
import { axeViolations } from "@/test/axe";
import { setDevicePrefersDark } from "@/test/device";
import { ThemeProvider } from "@/theme/ThemeProvider";
import { OneOffChargeDialog } from "./OneOffChargeDialog";

const onDone = vi.fn();
const onClose = vi.fn();
const DUE = "2099-01-10";
const created = { id: "c9", student: { id: "s1", name: "Maria Lima", photoUrl: null }, unit: { id: "u1", name: "Centro" }, type: "ONE_OFF", modality: null, referenceMonth: null, description: "Kimono A2", amountCents: 25000, dueOn: DUE, status: "PENDING", payment: null };

let sent: { studentId: string; body: Record<string, unknown> }[];

function setup(opts: { post?: () => Response | Promise<Response>; modalities?: ReturnType<typeof modalityRow>[]; students?: () => Response | Promise<Response> } = {}) {
  sent = [];
  onDone.mockClear();
  onClose.mockClear();
  server.use(
    http.get("/api/v1/students", () => (opts.students ? opts.students() : HttpResponse.json({ items: [{ id: "s1", name: "Maria Lima" }, { id: "s2", name: "Mariana Costa" }], page: 1, size: 25, total: 2 }))),
    http.get("/api/v1/students/:id/enrollment", () => HttpResponse.json(enrollmentOf(opts.modalities ?? [modalityRow(), modalityRow({ id: "m2", modality: { id: "mod2", name: "Judô" } })]))),
    http.post("/api/v1/students/:id/charges", async ({ request, params }) => {
      sent.push({ studentId: params.id as string, body: (await request.json()) as Record<string, unknown> });
      return opts.post ? opts.post() : HttpResponse.json(created, { status: 201 });
    }),
  );
  return render(
    <ThemeProvider>
      <OneOffChargeDialog onClose={onClose} onDone={onDone} />
    </ThemeProvider>,
  );
}

const fill = async (user: ReturnType<typeof userEvent.setup>, description = "Kimono A2") => {
  await user.type(screen.getByLabelText("Buscar aluno pelo nome (mínimo 2 letras)"), "mar");
  await user.selectOptions(await screen.findByLabelText("Aluno"), "s1");
  await screen.findByText("Unidade: Centro");
  await user.type(screen.getByLabelText("Descrição"), description);
  await user.type(screen.getByLabelText("Valor"), "25000");
  fireEvent.change(screen.getByLabelText("Vencimento"), { target: { value: DUE } });
};

describe("janela Nova cobrança avulsa (RN-COB-11, CA11, CA12, CA15)", () => {
  it("busca o aluno, usa a única unidade, envia uma vez com requestId, fecha e recarrega", async () => {
    const user = userEvent.setup();
    let release!: () => void;
    const gate = new Promise<void>((r) => (release = r));
    setup({ post: async () => (await gate, HttpResponse.json(created, { status: 201 })) });
    const submit = screen.getByRole("button", { name: "Criar cobrança" });
    expect(submit).toBeDisabled();
    await fill(user);
    expect(submit).toBeEnabled();
    await user.click(submit);
    expect(submit).toBeDisabled();
    await user.click(submit);
    release();
    await waitFor(() => expect(onDone).toHaveBeenCalledTimes(1));
    expect(onClose).toHaveBeenCalled();
    expect(sent).toHaveLength(1);
    expect(sent[0].studentId).toBe("s1");
    expect(sent[0].body).toMatchObject({ unitId: "u1", description: "Kimono A2", amountCents: 25000, dueOn: DUE });
    expect(sent[0].body.requestId).toMatch(/^[0-9a-f-]{36}$/);
  });

  it("com mais de uma unidade pede a escolha; sem escolher não envia", async () => {
    const user = userEvent.setup();
    setup({ modalities: [modalityRow(), modalityRow({ id: "m2", unit: { id: "u2", name: "Norte" } })] });
    await user.type(screen.getByLabelText("Buscar aluno pelo nome (mínimo 2 letras)"), "mar");
    await user.selectOptions(await screen.findByLabelText("Aluno"), "s1");
    await user.type(await screen.findByLabelText("Descrição"), "Exame");
    await user.type(screen.getByLabelText("Valor"), "5000");
    fireEvent.change(screen.getByLabelText("Vencimento"), { target: { value: DUE } });
    expect(screen.getByRole("button", { name: "Criar cobrança" })).toBeDisabled();
    await user.selectOptions(screen.getByLabelText("Unidade"), "u2");
    await user.click(screen.getByRole("button", { name: "Criar cobrança" }));
    await waitFor(() => expect(sent[0]?.body).toMatchObject({ unitId: "u2", amountCents: 5000 }));
  });

  it("a descrição para em 80 caracteres e vencimento no passado não envia", async () => {
    const user = userEvent.setup();
    setup();
    await fill(user, "x".repeat(85));
    expect(screen.getByLabelText("Descrição")).toHaveValue("x".repeat(80));
    expect(screen.getByText("80/80")).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText("Vencimento"), { target: { value: "2000-01-01" } });
    expect(screen.getByRole("button", { name: "Criar cobrança" })).toBeDisabled();
  });

  it.each([
    [422, "O aluno precisa ter matrícula na unidade"],
    [404, "O aluno ou a unidade não existe"],
    [409, "Este pedido já foi usado com outros dados"],
  ])("erro %s aparece em texto, a janela continua aberta e os dados ficam", async (status, text) => {
    const user = userEvent.setup();
    setup({ post: () => HttpResponse.json({ code: "X" }, { status }) });
    await fill(user);
    await user.click(screen.getByRole("button", { name: "Criar cobrança" }));
    expect(await screen.findByRole("alert")).toHaveTextContent(text);
    expect(onClose).not.toHaveBeenCalled();
    expect(screen.getByLabelText("Descrição")).toHaveValue("Kimono A2");
    expect(screen.getByLabelText("Valor")).toHaveValue("R$ 250,00");
  });

  it("erro 400 do servidor aparece no campo", async () => {
    const user = userEvent.setup();
    setup({ post: () => HttpResponse.json({ code: "VALIDATION_ERROR", fields: [{ field: "dueOn", message: "não pode ser anterior a hoje" }] }, { status: 400 }) });
    await fill(user);
    await user.click(screen.getByRole("button", { name: "Criar cobrança" }));
    expect(await screen.findByText("não pode ser anterior a hoje")).toBeInTheDocument();
  });

  it("estados da busca: carregando, vazio, erro com nova tentativa e sem permissão", async () => {
    const user = userEvent.setup();
    setup({ students: async () => (await delay(50), HttpResponse.json({ items: [], page: 1, size: 25, total: 0 })) });
    await user.type(screen.getByLabelText("Buscar aluno pelo nome (mínimo 2 letras)"), "zz");
    expect(await screen.findByText("Carregando...")).toBeInTheDocument();
    expect(await screen.findByText("Nenhum aluno encontrado.")).toBeInTheDocument();
    server.use(http.get("/api/v1/students", () => HttpResponse.json({ code: "INTERNAL_ERROR" }, { status: 500 })));
    await user.type(screen.getByLabelText("Buscar aluno pelo nome (mínimo 2 letras)"), "z");
    expect(await screen.findByRole("button", { name: "Tentar de novo" })).toBeInTheDocument();
    server.use(http.get("/api/v1/students", () => HttpResponse.json({ code: "FORBIDDEN" }, { status: 403 })));
    await user.type(screen.getByLabelText("Buscar aluno pelo nome (mínimo 2 letras)"), "z");
    expect(await screen.findByText("Você não tem acesso")).toBeInTheDocument();
  });

  it("aluno sem matrícula mostra o vazio e a falha na matrícula pede nova tentativa", async () => {
    const user = userEvent.setup();
    setup({ modalities: [] });
    await user.type(screen.getByLabelText("Buscar aluno pelo nome (mínimo 2 letras)"), "mar");
    await user.selectOptions(await screen.findByLabelText("Aluno"), "s1");
    expect(await screen.findByText("Este aluno não tem matrícula em nenhuma unidade.")).toBeInTheDocument();
    server.use(http.get("/api/v1/students/:id/enrollment", () => HttpResponse.json({ code: "INTERNAL_ERROR" }, { status: 500 })));
    await user.selectOptions(screen.getByLabelText("Aluno"), "s2");
    expect(await screen.findByRole("button", { name: "Tentar de novo" })).toBeInTheDocument();
  });

  it.each([
    ["claro", false],
    ["escuro", true],
  ])("a 360 px não tem violações no axe no tema %s", async (_n, dark) => {
    const user = userEvent.setup();
    setDevicePrefersDark(dark);
    window.innerWidth = 360;
    setup();
    await fill(user);
    expect(await axeViolations(document.body)).toEqual([]);
  });
});
