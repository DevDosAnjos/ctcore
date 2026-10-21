import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { MemoryRouter } from "react-router";
import { App } from "@/App";
import { SessionProvider } from "@/features/auth/SessionProvider";
import { server } from "@/mocks/server";
import { axeViolations } from "@/test/axe";
import { setDevicePrefersDark } from "@/test/device";
import { ThemeProvider } from "@/theme/ThemeProvider";

// FormData do jsdom não passa pelo fetch do Node (D8): o envio do comprovante é trocado por um duplo.
const upload = vi.hoisted(() => vi.fn());
vi.mock("./api", async (original) => ({ ...(await original<typeof import("./api")>()), uploadOriginProof: upload }));

const ADULT_CPF = "52998224725";
const NEW_CPF = "11144477735";
const ADULT_ID = "00000000-0000-0000-0000-000000000701";

// O simulado guarda a sessão do último login (os pedidos da tela passam pelos mesmos handlers do navegador).
const login = (email: string, linkId?: string) =>
  fetch("/api/v1/auth/login", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ email, password: "segredo123" }) }).then(() =>
    linkId ? fetch("/api/v1/auth/session/link", { method: "PUT", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ linkId }) }) : undefined,
  );

async function setup(role = "COORDINATOR", path = "/alunos/nova-matricula") {
  window.history.replaceState(null, "", path);
  if (role === "TEACHER") await login("varios@exemplo.com", "00000000-0000-0000-0000-000000000013");
  else await login("ana@exemplo.com");
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

const choose = async (user: ReturnType<typeof userEvent.setup>, label: RegExp | string, option: RegExp | string) =>
  user.selectOptions(await screen.findByLabelText(label), await screen.findByRole("option", { name: option }));

// Etapa 2 do aluno adulto do simulado: Jiu-Jitsu no Centro.
async function fillModality(user: ReturnType<typeof userEvent.setup>) {
  // O simulado começa sem modalidade ligada nas unidades: aqui o Centro oferece Jiu-Jitsu.
  server.use(http.get("/api/v1/units/:id/modalities", () => HttpResponse.json({ canManage: true, items: [{ id: "00000000-0000-0000-0000-000000000103", name: "Jiu-Jitsu", offered: true }, { id: "00000000-0000-0000-0000-000000000104", name: "Judô", offered: false }] })));
  await choose(user, "Unidade", /Centro/);
  await choose(user, "Modalidade", "Jiu-Jitsu");
  await choose(user, "Turma principal", "Jiu-Jitsu Adulto");
  await choose(user, "Plano", /Jiu-Jitsu Livre/);
}

async function toBilling(user: ReturnType<typeof userEvent.setup>) {
  await setup("COORDINATOR", `/alunos/nova-matricula?aluno=${ADULT_ID}`);
  await fillModality(user);
  await user.click(screen.getByRole("button", { name: "Avançar" }));
  await choose(user, "Dia de vencimento", "Dia 5");
}

describe("assistente Nova matrícula (RF-MAT-01, #67)", () => {
  beforeEach(() => upload.mockReset());

  it("CA18: a gestão vê o assistente na etapa 1; o Professor não", async () => {
    const first = await setup();
    expect(await screen.findByRole("heading", { name: "Nova matrícula", level: 1 })).toBeInTheDocument();
    expect(screen.getByText("Etapa 1 de 5")).toBeInTheDocument();
    first.unmount();
    await setup("TEACHER");
    await waitFor(() => expect(screen.queryByRole("heading", { name: "Nova matrícula", level: 1 })).toBeNull());
  });

  it("CA18: aberto pela ficha, o assistente já está no aluno, a partir da etapa 2", async () => {
    await setup("COORDINATOR", `/alunos/nova-matricula?aluno=${ADULT_ID}`);
    expect(await screen.findByText("Etapa 2 de 5")).toBeInTheDocument();
    expect(screen.getByText("Aluno: Aluno Adulto Fictício")).toBeInTheDocument();
  });

  it("CA19: CPF existente mostra só nome e situação e pede confirmação; CPF novo mostra o cadastro", async () => {
    const user = userEvent.setup();
    await setup();
    await user.type(await screen.findByLabelText("CPF do aluno"), ADULT_CPF);
    await user.click(screen.getByRole("button", { name: "Buscar" }));
    expect(await screen.findByText("Aluno Adulto Fictício")).toBeInTheDocument();
    expect(screen.getByText("Matrícula nova")).toBeInTheDocument();
    expect(screen.queryByLabelText("Nome completo")).toBeNull();
    await user.click(screen.getByRole("button", { name: "É este aluno, continuar" }));
    expect(await screen.findByText("Etapa 2 de 5")).toBeInTheDocument();
  });

  it("CA19: CPF novo cadastra o aluno; menor sem responsável não avança", async () => {
    const user = userEvent.setup();
    await setup();
    await user.type(await screen.findByLabelText("CPF do aluno"), NEW_CPF);
    await user.click(screen.getByRole("button", { name: "Buscar" }));
    await choose(user, "Unidade de cadastro", /Centro/);
    await user.type(await screen.findByLabelText("Nome completo"), "Criança Nova");
    await user.type(screen.getByLabelText("Data de nascimento"), "2018-04-05");
    await user.type(screen.getByLabelText("CEP"), "01001000");
    await user.type(screen.getByLabelText("Rua"), "Rua A");
    await user.type(screen.getByLabelText("Número"), "10");
    await user.type(screen.getByLabelText("Bairro"), "Centro");
    await user.type(screen.getByLabelText("Cidade"), "Rio");
    await choose(user, "Estado", "RJ");
    expect(await screen.findByText("Responsável")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Cadastrar e continuar" }));
    expect(await screen.findAllByText("Informe os dados do responsável.")).not.toHaveLength(0);
    expect(screen.queryByText("Etapa 2 de 5")).toBeNull();
  });

  it("RF-MAT-02 CA5: turma fora da faixa e cheia mostra os dois avisos e não bloqueia avançar; trocar a turma remove os avisos", async () => {
    const user = userEvent.setup();
    await setup("COORDINATOR", `/alunos/nova-matricula?aluno=${ADULT_ID}`);
    server.use(
      http.get("/api/v1/students/:id/enrollment/class-warnings", ({ request }) =>
        HttpResponse.json(
          new URL(request.url).searchParams.get("classId") === "00000000-0000-0000-0000-000000000601"
            ? { studentAge: 10, ageRange: { id: "r", name: "Adulto", minAge: 18, maxAge: null }, ageOutOfRange: true, capacity: 2, activeStudents: 2, overCapacity: true }
            : { studentAge: 10, ageRange: null, ageOutOfRange: false, capacity: null, activeStudents: 0, overCapacity: false },
        ),
      ),
    );
    await fillModality(user);
    expect(await screen.findByText(/fora da faixa etária da turma Jiu-Jitsu Adulto/)).toBeInTheDocument();
    expect(screen.getByText(/acima da capacidade/)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Avançar" })).toBeEnabled();
  });

  it("CA20: a 11ª modalidade não pode ser adicionada", async () => {
    const user = userEvent.setup();
    await setup("COORDINATOR", `/alunos/nova-matricula?aluno=${ADULT_ID}`);
    const add = await screen.findByRole("button", { name: "Adicionar modalidade" });
    for (let i = 0; i < 9; i++) await user.click(add);
    expect(add).toBeDisabled();
    expect(screen.getByText("O aluno pode ter no máximo 10 modalidades ativas.")).toBeInTheDocument();
  });

  it("CA20: sem unidade e modalidade escolhidas não avança; sem modalidade oferecida diz o que fazer", async () => {
    const user = userEvent.setup();
    await setup("COORDINATOR", `/alunos/nova-matricula?aluno=${ADULT_ID}`);
    expect(await screen.findByRole("button", { name: "Avançar" })).toBeDisabled();
    await choose(user, "Unidade", /Centro/);
    expect(await screen.findByText(/não oferece modalidade disponível/)).toBeInTheDocument();
  });

  it("CA20: com exigência de comprovante, a graduação de origem só avança com o arquivo enviado", async () => {
    const user = userEvent.setup();
    upload.mockResolvedValue({ proofKey: "origin-proof-x.pdf" });
    server.use(
      http.get("/api/v1/modalities/:id/graduation", () =>
        HttpResponse.json({ enabled: true, requireOriginProof: true, canManage: false, levels: [{ id: "lv1", name: "Azul", maxDegree: 4 }] }),
      ),
    );
    await setup("COORDINATOR", `/alunos/nova-matricula?aluno=${ADULT_ID}`);
    await fillModality(user);
    const next = screen.getByRole("button", { name: "Avançar" });
    expect(next).toBeEnabled();
    await user.click(await screen.findByLabelText("O aluno já tem graduação nesta modalidade"));
    await choose(user, "Nível", "Azul");
    expect(next).toBeDisabled();
    expect(screen.getByText("Envie o comprovante para continuar.")).toBeInTheDocument();
    await user.upload(screen.getByLabelText("Comprovante da graduação (obrigatório)"), new File(["x"], "faixa.pdf", { type: "application/pdf" }));
    expect(await screen.findByText("Comprovante enviado: faixa.pdf")).toBeInTheDocument();
    expect(next).toBeEnabled();
  });

  it("CA20: modalidade sem graduação não mostra o bloco de origem", async () => {
    const user = userEvent.setup();
    server.use(http.get("/api/v1/modalities/:id/graduation", () => HttpResponse.json({ enabled: false, requireOriginProof: false, canManage: false, levels: [] })));
    await setup("COORDINATOR", `/alunos/nova-matricula?aluno=${ADULT_ID}`);
    await fillModality(user);
    expect(screen.queryByText("Graduação de origem")).toBeNull();
  });

  it("CA21: o dia de vencimento só oferece os dias da unidade e a segunda cobrança é editável", async () => {
    const user = userEvent.setup();
    await toBilling(user);
    const days = within(screen.getByLabelText("Dia de vencimento")).getAllByRole("option").map((o) => o.textContent);
    expect(days).toEqual(["Escolha", "Dia 1", "Dia 5", "Dia 10", "Dia 15"]);
    const second = screen.getByLabelText("Segunda cobrança (opcional)");
    await user.type(second, "2030-01-10");
    expect(second).toHaveValue("2030-01-10");
  });

  it("CA21: unidade com taxa mostra o valor e a isenção exige o motivo; sem taxa não mostra nada", async () => {
    const user = userEvent.setup();
    server.use(
      http.get("/api/v1/units/:id", ({ params }) =>
        params.id === "reachable" ? undefined : HttpResponse.json({ id: params.id, name: "Centro", settings: { dueDays: [5, 10], enrollmentFeeEnabled: true, enrollmentFeeCents: 5000 } }),
      ),
    );
    await toBilling(user);
    expect(await screen.findByText("Taxa de matrícula: R$ 50,00")).toBeInTheDocument();
    await user.click(screen.getByLabelText("Isentar taxa"));
    expect(screen.getByRole("button", { name: "Avançar" })).toBeDisabled();
    await user.type(screen.getByLabelText(/Motivo da isenção/), "bolsa da turma");
    expect(screen.getByRole("button", { name: "Avançar" })).toBeEnabled();
  });

  it("CA21: sem taxa na unidade, nada de taxa", async () => {
    const user = userEvent.setup();
    await toBilling(user);
    expect(screen.queryByText(/Taxa de matrícula:/)).toBeNull();
    expect(screen.queryByLabelText("Isentar taxa")).toBeNull();
  });

  it("CA22 e CA23: resumo, conclusão com botão desabilitado durante o envio, etapa 5 e volta para a ficha", async () => {
    const user = userEvent.setup();
    let release: () => void = () => {};
    const gate = new Promise<void>((resolve) => (release = resolve));
    const real = await fetch("/api/v1/auth/session").catch(() => null);
    void real;
    await toBilling(user);
    await user.click(screen.getByRole("button", { name: "Avançar" }));
    expect(await screen.findByText("Mensalidade: R$ 200,00")).toBeInTheDocument();
    expect(screen.getByText(/Nenhuma cobrança é criada agora/)).toBeInTheDocument();
    server.use(
      http.post("/api/v1/students/:id/enrollment/conclude", async () => {
        await gate;
        return HttpResponse.json(
          {
            enrollment: { id: "e1", status: "ACTIVE", enrolledAt: "2026-10-21T12:00:00Z", reopenedAt: null, modalities: [] },
            summary: { modalities: [], units: [] },
            termPending: true,
            inviteStatus: "MINOR",
          },
          { status: 201 },
        );
      }),
    );
    const conclude = screen.getByRole("button", { name: "Concluir matrícula" });
    await user.click(conclude);
    expect(conclude).toBeDisabled();
    release();
    expect(await screen.findByText("Aluno Ativo")).toBeInTheDocument();
    expect(screen.getByText(/Sem convite: o aluno é menor/)).toBeInTheDocument();
    const pay = screen.getByRole("button", { name: "Registrar pagamento agora" });
    expect(pay).toBeDisabled();
    expect(pay).toHaveAccessibleDescription("Disponível com as cobranças");
    await user.click(screen.getByRole("button", { name: "Aluno paga pelo portal" }));
    await waitFor(() => expect(screen.queryByText("Aluno Ativo")).toBeNull());
  });

  it("CA22: erro 422 volta à etapa do campo com a mensagem e preserva o que foi digitado", async () => {
    const user = userEvent.setup();
    await toBilling(user);
    await user.click(screen.getByRole("button", { name: "Avançar" }));
    await screen.findByText("Mensalidade: R$ 200,00");
    server.use(
      http.post("/api/v1/students/:id/enrollment/conclude", () =>
        HttpResponse.json({ code: "UNPROCESSABLE", message: "x", changed: false, fields: [{ field: "dueDay", message: "dia não permitido pela unidade" }], traceId: "t" }, { status: 422 }),
      ),
    );
    await user.click(screen.getByRole("button", { name: "Concluir matrícula" }));
    expect(await screen.findByText("dia não permitido pela unidade")).toBeInTheDocument();
    expect(screen.getByText("Etapa 3 de 5")).toBeInTheDocument();
    expect(screen.getByLabelText("Dia de vencimento")).toHaveValue("5");
  });

  it("CA22: erro de rede mostra o estado de erro com Tentar de novo", async () => {
    const user = userEvent.setup();
    await toBilling(user);
    await user.click(screen.getByRole("button", { name: "Avançar" }));
    await screen.findByText("Mensalidade: R$ 200,00");
    server.use(http.post("/api/v1/students/:id/enrollment/conclude", () => HttpResponse.error()));
    await user.click(screen.getByRole("button", { name: "Concluir matrícula" }));
    expect(await screen.findByRole("button", { name: "Tentar de novo" })).toBeInTheDocument();
  });

  it("CA24: carregando e erro da etapa 2", async () => {
    server.use(http.get("/api/v1/students/:id/enrollment", () => HttpResponse.json({}, { status: 500 })));
    await setup("COORDINATOR", `/alunos/nova-matricula?aluno=${ADULT_ID}`);
    expect(await screen.findByRole("button", { name: "Tentar de novo" })).toBeInTheDocument();
  });

  it("CA24: sem permissão na etapa 2", async () => {
    server.use(http.get("/api/v1/students/:id/enrollment", () => HttpResponse.json({}, { status: 403 })));
    await setup("COORDINATOR", `/alunos/nova-matricula?aluno=${ADULT_ID}`);
    expect(await screen.findByText("Você não tem acesso")).toBeInTheDocument();
  });

  it("CA18: a lista de alunos oferece Nova matrícula", async () => {
    await setup("COORDINATOR", "/alunos");
    expect(await screen.findByRole("link", { name: "Nova matrícula" })).toHaveAttribute("href", "/alunos/nova-matricula");
  });

  it.each([
    ["claro", false],
    ["escuro", true],
  ])("CA24: a 360 px não tem violações no axe nas cinco etapas no tema %s", async (_n, dark) => {
    const user = userEvent.setup();
    setDevicePrefersDark(dark);
    window.innerWidth = 360;
    const first = await setup();
    await screen.findByLabelText("CPF do aluno");
    expect(await axeViolations(first.container)).toEqual([]);
    first.unmount();
    const second = await setup("COORDINATOR", `/alunos/nova-matricula?aluno=${ADULT_ID}`);
    await fillModality(user);
    expect(await axeViolations(second.container)).toEqual([]);
    await user.click(screen.getByRole("button", { name: "Avançar" }));
    await choose(user, "Dia de vencimento", "Dia 5");
    expect(await axeViolations(second.container)).toEqual([]);
    await user.click(screen.getByRole("button", { name: "Avançar" }));
    await screen.findByText("Mensalidade: R$ 200,00");
    expect(await axeViolations(second.container)).toEqual([]);
    await user.click(screen.getByRole("button", { name: "Concluir matrícula" }));
    await screen.findByText("Aluno Ativo");
    expect(await axeViolations(second.container)).toEqual([]);
  });
});
