import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { server } from "@/mocks/server";
import { axeViolations } from "@/test/axe";
import { setDevicePrefersDark } from "@/test/device";
import { ThemeProvider } from "@/theme/ThemeProvider";
import { EndModalityDialog } from "./EndModalityDialog";
import { enrollmentOf, modalityRow } from "./enrollmentFixtures";

let bodies: unknown[];
const onDone = vi.fn();
const onClose = vi.fn();
const preview = (openCharges: unknown[] = []) => http.get("/api/v1/students/s1/modalities/m1/end-preview", () => HttpResponse.json({ openCharges }));
const open = (id: string, due: string) => ({ id, referenceMonth: "2026-03-01", dueOn: due, amountCents: 15000, status: "OVERDUE" });

function setup(post?: () => Response, ...extra: Parameters<typeof server.use>) {
  bodies = [];
  onDone.mockClear();
  onClose.mockClear();
  server.use(
    ...extra,
    preview(),
    http.post("/api/v1/students/s1/modalities/m1/end", async ({ request }) => {
      bodies.push(await request.json());
      return post ? post() : HttpResponse.json(enrollmentOf([modalityRow({ status: "ENDED", endedAt: "2026-02-01T12:00:00Z" })]));
    }),
  );
  return render(
    <ThemeProvider>
      <EndModalityDialog studentId="s1" studentName="Ana Souza" row={modalityRow()} onClose={onClose} onDone={onDone} />
    </ThemeProvider>,
  );
}

describe("janela Encerrar modalidade (RF-MAT-03, CA9, CA10)", () => {
  it("mostra o efeito e o estado vazio das cobranças; confirma só com motivo e envia motivo e observação", async () => {
    const user = userEvent.setup();
    setup();
    expect(await screen.findByText(/para de gerar cobrança/)).toBeInTheDocument();
    expect(screen.getByText("Ana Souza · Jiu-Jitsu (Centro)")).toBeInTheDocument();
    expect(screen.getByText("Nenhuma cobrança em aberto")).toBeInTheDocument();
    const submit = screen.getByRole("button", { name: "Encerrar modalidade" });
    expect(submit).toBeDisabled();
    await user.selectOptions(screen.getByLabelText("Motivo"), "FINANCIAL");
    await user.type(screen.getByLabelText("Observação (opcional)"), "  Sem tempo  ");
    await user.click(submit);
    await waitFor(() => expect(onDone).toHaveBeenCalled());
    expect(bodies).toEqual([{ reason: "FINANCIAL", note: "Sem tempo" }]);
    expect(onClose).toHaveBeenCalled();
  });

  it("lista as cobranças em aberto quando existirem", async () => {
    setup(undefined, preview([open("c1", "2026-03-05")]));
    expect(await screen.findByText(/05\/03\/2026 · R\$ 150,00 · Em atraso/)).toBeInTheDocument();
  });

  it("CA19: escolhe Cancelar (com motivo) numa cobrança e Manter na outra; o envio leva charges", async () => {
    const user = userEvent.setup();
    setup(undefined, preview([open("c1", "2026-03-05"), open("c2", "2026-04-05")]));
    const groups = await screen.findAllByRole("group", { name: /R\$ 150,00/ });
    expect(groups).toHaveLength(2);
    expect(screen.getAllByRole("radio", { name: "Manter", checked: true })).toHaveLength(2);
    await user.selectOptions(screen.getByLabelText("Motivo"), "FINANCIAL");
    await user.click(within(groups[0]).getByRole("radio", { name: "Cancelar" }));
    const submit = screen.getByRole("button", { name: "Encerrar modalidade" });
    expect(submit).toBeDisabled();
    await user.type(within(groups[0]).getByRole("textbox"), " Mudou de cidade ");
    expect(submit).toBeEnabled();
    await user.click(submit);
    await waitFor(() => expect(onDone).toHaveBeenCalled());
    expect(bodies).toEqual([
      { reason: "FINANCIAL", charges: [{ chargeId: "c1", action: "CANCEL", reason: "Mudou de cidade" }, { chargeId: "c2", action: "KEEP" }] },
    ]);
  });

  it("mostra o 400 do servidor no campo e não fecha", async () => {
    const user = userEvent.setup();
    setup(() => HttpResponse.json({ code: "VALIDATION_ERROR", fields: [{ field: "note", message: "até 500 caracteres" }] }, { status: 400 }));
    await screen.findByLabelText("Motivo");
    await user.selectOptions(screen.getByLabelText("Motivo"), "OTHER");
    await user.click(screen.getByRole("button", { name: "Encerrar modalidade" }));
    expect(await screen.findByText("até 500 caracteres")).toBeInTheDocument();
    expect(onClose).not.toHaveBeenCalled();
  });

  it("404 na confirmação avisa que a modalidade não existe mais", async () => {
    const user = userEvent.setup();
    setup(() => HttpResponse.json({ code: "NOT_FOUND" }, { status: 404 }));
    await screen.findByLabelText("Motivo");
    await user.selectOptions(screen.getByLabelText("Motivo"), "OTHER");
    await user.click(screen.getByRole("button", { name: "Encerrar modalidade" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("já foi encerrada");
  });

  it("RN-MAT-18: a cobrança com vencimento depois do pedido aparece já cancelada, sem escolha, e fica fora das decisões", async () => {
    const user = userEvent.setup();
    setup(undefined, preview([{ ...open("c1", "2026-02-05"), afterRequest: false }, { ...open("c2", "2026-03-05"), afterRequest: true }]));
    expect(await screen.findByText(/cobranças com vencimento depois do pedido são canceladas/)).toBeInTheDocument();
    expect(screen.getByText(/será cancelada/)).toBeInTheDocument();
    expect(screen.getAllByRole("radio", { name: "Manter" })).toHaveLength(1);
    await user.selectOptions(screen.getByLabelText("Motivo"), "OTHER");
    await user.click(screen.getByRole("button", { name: "Encerrar modalidade" }));
    await waitFor(() => expect(onDone).toHaveBeenCalled());
    expect(bodies).toEqual([{ reason: "OTHER", charges: [{ chargeId: "c1", action: "KEEP" }] }]);
  });

  it("falha na consulta pede nova tentativa e 403 mostra sem permissão", async () => {
    const user = userEvent.setup();
    setup(undefined, http.get("/api/v1/students/s1/modalities/m1/end-preview", () => HttpResponse.json({ code: "INTERNAL_ERROR" }, { status: 500 }), { once: true }));
    await user.click(await screen.findByRole("button", { name: "Tentar de novo" }));
    expect(await screen.findByLabelText("Motivo")).toBeInTheDocument();
  });

  it("sem permissão na consulta mostra o estado próprio", async () => {
    setup(undefined, http.get("/api/v1/students/s1/modalities/m1/end-preview", () => HttpResponse.json({ code: "FORBIDDEN" }, { status: 403 })));
    expect(await screen.findByText("Você não tem acesso")).toBeInTheDocument();
  });

  it.each([
    ["claro", false],
    ["escuro", true],
  ])("a 360 px não tem violações no axe no tema %s", async (_n, dark) => {
    setDevicePrefersDark(dark);
    window.innerWidth = 360;
    setup();
    await screen.findByLabelText("Motivo");
    expect(await axeViolations(document.body)).toEqual([]);
  });
});
