import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { server } from "@/mocks/server";
import { axeViolations } from "@/test/axe";
import { setDevicePrefersDark } from "@/test/device";
import { ThemeProvider } from "@/theme/ThemeProvider";
import { choiceRoutes, enrollmentOf, modalityRow } from "./enrollmentFixtures";
import { TransferDialog } from "./TransferDialog";
import type { Enrollment } from "./types";

let bodies: unknown[];
let queries: string[];
const onDone = vi.fn();
const onClose = vi.fn();

const dialog = (enrollment: Enrollment) => (
  <ThemeProvider>
    <TransferDialog studentId="s1" enrollment={enrollment} row={enrollment.modalities[0]} onClose={onClose} onDone={onDone} />
  </ThemeProvider>
);

function setup(enrollment: Enrollment = enrollmentOf([modalityRow()]), post?: () => Response) {
  bodies = [];
  queries = [];
  onDone.mockClear();
  onClose.mockClear();
  server.use(
    ...choiceRoutes(queries),
    http.post("/api/v1/students/s1/modalities/m1/transfer", async ({ request }) => {
      bodies.push(await request.json());
      return post ? post() : HttpResponse.json(enrollment);
    }),
  );
  return render(dialog(enrollment));
}

async function fill(user: ReturnType<typeof userEvent.setup>) {
  await user.selectOptions(await screen.findByLabelText("Unidade de destino"), "u2");
  await user.selectOptions(await screen.findByLabelText("Turma principal"), "k3");
  await user.selectOptions(await screen.findByLabelText("Plano na unidade nova"), "p2");
}

describe("janela Transferir para outra unidade (R11, R25, CA34)", () => {
  it("oferece só outras unidades, pede turmas e planos da unidade nova e envia com o vencimento", async () => {
    const user = userEvent.setup();
    setup();
    await screen.findByLabelText("Unidade de destino");
    expect(screen.getByRole("option", { name: "Norte" })).toBeInTheDocument();
    expect(screen.queryByRole("option", { name: "Centro" })).toBeNull();
    await fill(user);
    expect(queries.every((q) => q.includes("unitId=u2") && q.includes("modalityId=mod1"))).toBe(true);
    const submit = screen.getByRole("button", { name: "Transferir" });
    expect(submit).toBeDisabled();
    await user.type(screen.getByLabelText("Dia de vencimento (1 a 28)"), "10");
    await user.click(submit);
    await waitFor(() => expect(onDone).toHaveBeenCalled());
    expect(bodies).toEqual([{ unitId: "u2", primaryClassId: "k3", otherClassIds: [], planId: "p2", dueDay: 10 }]);
    expect(onClose).toHaveBeenCalled();
  });

  it("com outra modalidade ativa na unidade nova, o vencimento é herdado e não é enviado", async () => {
    const user = userEvent.setup();
    setup(enrollmentOf([modalityRow(), modalityRow({ id: "m2", unit: { id: "u2", name: "Norte" }, modality: { id: "mod2", name: "Judô" }, dueDay: 15 })]));
    await fill(user);
    expect(screen.getByText("O vencimento será o dia 15, o mesmo das outras modalidades do aluno nesta unidade.")).toBeInTheDocument();
    expect(screen.queryByLabelText("Dia de vencimento (1 a 28)")).toBeNull();
    await user.click(screen.getByRole("button", { name: "Transferir" }));
    await waitFor(() => expect(bodies).toEqual([{ unitId: "u2", primaryClassId: "k3", otherClassIds: [], planId: "p2" }]));
  });

  it("avisa das cobranças e mostra o 422 do servidor no campo", async () => {
    const user = userEvent.setup();
    setup(enrollmentOf([modalityRow()]), () => HttpResponse.json({ code: "UNPROCESSABLE", fields: [{ field: "dueDay", message: "dia não permitido" }] }, { status: 422 }));
    expect(await screen.findByText(/cobranças já geradas ficam na unidade de origem/)).toBeInTheDocument();
    await fill(user);
    await user.type(screen.getByLabelText("Dia de vencimento (1 a 28)"), "29");
    await user.click(screen.getByRole("button", { name: "Transferir" }));
    expect(await screen.findByText("dia não permitido")).toBeInTheDocument();
    expect(onClose).not.toHaveBeenCalled();
  });

  it("falha ao carregar as unidades pede nova tentativa; 403 mostra o estado sem permissão", async () => {
    server.use(http.get("/api/v1/units/reachable", () => HttpResponse.json({ code: "INTERNAL_ERROR" }, { status: 500 })));
    const first = render(dialog(enrollmentOf([modalityRow()])));
    expect(await screen.findByRole("button", { name: "Tentar de novo" })).toBeInTheDocument();
    first.unmount();
    server.use(http.get("/api/v1/units/reachable", () => HttpResponse.json({ code: "FORBIDDEN" }, { status: 403 })));
    render(dialog(enrollmentOf([modalityRow()])));
    expect(await screen.findByText("Você não tem acesso")).toBeInTheDocument();
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
