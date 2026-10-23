import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { enrollmentOf, modalityRow } from "@/features/students/enrollmentFixtures";
import { server } from "@/mocks/server";
import { axeViolations } from "@/test/axe";
import { gate } from "@/test/gate";
import { setDevicePrefersDark } from "@/test/device";
import { ThemeProvider } from "@/theme/ThemeProvider";
import { AdvanceChargesDialog } from "./AdvanceChargesDialog";

const onDone = vi.fn();
const onClose = vi.fn();
const made = (ref: string) => ({ id: ref, modalityRowId: "m1", type: "MONTHLY", unitId: "u1", referenceMonth: ref, dueOn: `${ref.slice(0, 8)}05`, toleranceDays: 0, amountCents: 20000 });

let sent: { path: string; body: unknown }[];

function setup(post?: () => Response) {
  sent = [];
  onDone.mockClear();
  onClose.mockClear();
  server.use(
    http.get("/api/v1/students", () => HttpResponse.json({ items: [{ id: "s1", name: "Maria Lima" }, { id: "s2", name: "Beto Lima" }], page: 1, size: 25, total: 2 })),
    http.get("/api/v1/students/s1/enrollment", () =>
      HttpResponse.json(enrollmentOf([modalityRow(), modalityRow({ id: "m2", modality: { id: "mod2", name: "Judô" } }), modalityRow({ id: "m3", status: "ENDED", modality: { id: "mod3", name: "Muay Thai" } })])),
    ),
    http.get("/api/v1/students/s2/enrollment", () => HttpResponse.json(enrollmentOf([modalityRow({ status: "ENDED" })]))),
    http.post("/api/v1/students/:id/modalities/:row/charges/advance", async ({ request, params }) => {
      sent.push({ path: `${params.id}/${params.row}`, body: await request.json() });
      return post ? post() : HttpResponse.json([made("2026-12-01"), made("2027-01-01"), made("2027-02-01")], { status: 201 });
    }),
  );
  return render(
    <ThemeProvider>
      <AdvanceChargesDialog onClose={onClose} onDone={onDone} />
    </ThemeProvider>,
  );
}

const search = async (user: ReturnType<typeof userEvent.setup>, student = "s1") => {
  await user.type(screen.getByLabelText("Buscar aluno pelo nome (mínimo 2 letras)"), "li");
  await user.selectOptions(await screen.findByLabelText("Aluno"), student);
};

describe("janela Gerar próximas mensalidades (RN-COB-06, CA13, CA14, CA15)", () => {
  it("lista só as modalidades ativas, envia N, mostra quantas e os meses, recarrega e fecha ao confirmar", async () => {
    const user = userEvent.setup();
    setup();
    await search(user);
    const select = await screen.findByLabelText("Modalidade");
    expect(screen.getAllByRole("option").map((o) => o.textContent)).toEqual(expect.arrayContaining(["Jiu-Jitsu · Centro", "Judô · Centro"]));
    expect(screen.queryByRole("option", { name: "Muay Thai · Centro" })).not.toBeInTheDocument();
    expect(screen.getByText(/pula os meses já gerados/)).toBeInTheDocument();
    await user.selectOptions(select, "m1");
    await user.type(screen.getByLabelText("Quantidade de mensalidades (1 a 12)"), "3");
    await user.click(screen.getByRole("button", { name: "Gerar mensalidades" }));
    expect(await screen.findByText("3 mensalidades criadas (de dez/2026 a fev/2027).")).toBeInTheDocument();
    expect(sent).toEqual([{ path: "s1/m1", body: { count: 3 } }]);
    expect(onDone).toHaveBeenCalledTimes(1);
    expect(onClose).not.toHaveBeenCalled();
    await user.click(screen.getByRole("button", { name: "Concluir" }));
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it("0 e 13 não enviam", async () => {
    const user = userEvent.setup();
    setup();
    await search(user);
    await user.selectOptions(await screen.findByLabelText("Modalidade"), "m1");
    const count = screen.getByLabelText("Quantidade de mensalidades (1 a 12)");
    const submit = screen.getByRole("button", { name: "Gerar mensalidades" });
    await user.type(count, "0");
    expect(submit).toBeDisabled();
    await user.clear(count);
    await user.type(count, "13");
    expect(submit).toBeDisabled();
    await user.clear(count);
    await user.type(count, "12");
    expect(submit).toBeEnabled();
  });

  it("422 (valor zero) mostra o motivo e a janela continua aberta", async () => {
    const user = userEvent.setup();
    setup(() => HttpResponse.json({ code: "UNPROCESSABLE" }, { status: 422 }));
    await search(user);
    await user.selectOptions(await screen.findByLabelText("Modalidade"), "m1");
    await user.type(screen.getByLabelText("Quantidade de mensalidades (1 a 12)"), "1");
    await user.click(screen.getByRole("button", { name: "Gerar mensalidades" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("bolsa de 100%");
    expect(onDone).not.toHaveBeenCalled();
    expect(screen.getByLabelText("Quantidade de mensalidades (1 a 12)")).toHaveValue("1");
  });

  it("estados: carregando, aluno sem modalidade ativa, erro com nova tentativa e sem permissão", async () => {
    const user = userEvent.setup();
    setup();
    const students = gate();
    server.use(http.get("/api/v1/students", async () => (await students.opened, HttpResponse.json({ items: [{ id: "s1", name: "Maria Lima" }, { id: "s2", name: "Beto Lima" }], page: 1, size: 25, total: 2 }))));
    await user.type(screen.getByLabelText("Buscar aluno pelo nome (mínimo 2 letras)"), "li");
    expect(await screen.findByText("Carregando...")).toBeInTheDocument();
    students.release();
    await user.selectOptions(await screen.findByLabelText("Aluno"), "s2");
    expect(await screen.findByText("Sem modalidade ativa")).toBeInTheDocument();
    server.use(http.get("/api/v1/students/s1/enrollment", () => HttpResponse.json({ code: "INTERNAL_ERROR" }, { status: 500 })));
    await user.selectOptions(screen.getByLabelText("Aluno"), "s1");
    expect(await screen.findByRole("button", { name: "Tentar de novo" })).toBeInTheDocument();
    server.use(http.get("/api/v1/students", () => HttpResponse.json({ code: "FORBIDDEN" }, { status: 403 })));
    await user.type(screen.getByLabelText("Buscar aluno pelo nome (mínimo 2 letras)"), "z");
    await waitFor(() => expect(screen.getByText("Você não tem acesso")).toBeInTheDocument());
  });

  it.each([
    ["claro", false],
    ["escuro", true],
  ])("a 360 px não tem violações no axe no tema %s", async (_n, dark) => {
    const user = userEvent.setup();
    setDevicePrefersDark(dark);
    window.innerWidth = 360;
    setup();
    await search(user);
    await user.selectOptions(await screen.findByLabelText("Modalidade"), "m1");
    expect(await axeViolations(document.body)).toEqual([]);
  });
});
