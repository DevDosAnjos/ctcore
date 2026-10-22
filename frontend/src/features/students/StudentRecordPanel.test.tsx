import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { MemoryRouter, useLocation } from "react-router";
import { App } from "@/App";
import { StudentRecordPanel } from "./StudentRecordPanel";
import { SessionProvider, type SessionView } from "@/features/auth/SessionProvider";
import { mockPermissions } from "@/mocks/permissions";
import { server } from "@/mocks/server";
import { axeViolations } from "@/test/axe";
import { setDevicePrefersDark } from "@/test/device";
import { ThemeProvider } from "@/theme/ThemeProvider";
import { choiceRoutes, enrollmentOf, modalityRow } from "./enrollmentFixtures";
import type { Enrollment, StudentHistoryEntry, StudentRecord, StudentRow } from "./types";

const session = (role = "COORDINATOR"): SessionView => ({
  account: { id: "a", email: "x@x.com", name: "X" },
  links: [{ id: "l1", organizationId: "o", organizationName: "Alfa", role, unitIds: [] }],
  activeLinkId: "l1",
  twoFactor: { enabled: false, required: false },
});
const row: StudentRow = {
  id: "s1", name: "Ana Souza", photoUrl: null, birthDate: "2014-05-02", age: 12, minor: true, ageRange: { id: "r1", name: "Infantil" },
  registeredUnit: { id: "u1", name: "Centro" }, createdAt: "2026-01-10T12:00:00Z", seals: ["EMAIL_PROBLEM"], status: "NONE", financialStatus: "CURRENT", modalities: [],
};
const guardian = (over: Partial<NonNullable<StudentRecord["guardians"]>[number]> = {}) => ({
  guardianId: "g1", name: "Marta Souza", email: "marta@alfa.test", phone: "21987654321", financial: true, access: "PENDING" as const, emailProblem: false, ...over,
});
const baseRecord = (over: Partial<StudentRecord> = {}): StudentRecord => ({
  id: "s1", name: "Ana Souza", photoUrl: null, birthDate: "2014-05-02", age: 12, minor: true, ageRange: { id: "r1", name: "Infantil" },
  registeredUnit: { id: "u1", name: "Centro" }, seals: ["EMAIL_PROBLEM"], contact: { phone: "21912345678", email: "ana@alfa.test", emailProblem: false },
  cpf: null, address: { postalCode: "01001000", street: "Rua Um", number: "1", complement: null, district: "Centro", city: "Rio", state: "RJ" },
  emergency: { name: "Contato Um", relationship: "Mãe", phone: "21911112222" }, guardians: [guardian()], ...over,
});

function Where() {
  return <p hidden data-testid="where">{useLocation().pathname}</p>;
}

let record: StudentRecord;
let history: StudentHistoryEntry[];
let enrollment: Enrollment;
let enrollmentCalls: number;
let puts: { url: string; body: unknown }[];
let posts: { url: string; method: string; body: unknown }[];

function setup(path = "/alunos", opts: { record?: () => Response | Promise<Response>; history?: () => Response | Promise<Response>; role?: string; enrollment?: () => Response | Promise<Response>; direct?: boolean } = {}) {
  const role = opts.role ?? "COORDINATOR";
  enrollment = enrollmentOf([modalityRow()]);
  enrollmentCalls = 0;
  puts = [];
  record = baseRecord();
  history = [{ action: "student.created", at: "2026-01-10T15:00:00Z", actor: "Ana Coordenadora", actorProfile: "COORDINATOR", reason: null }];
  posts = [];
  const mutate = (kind: string) => async ({ request }: { request: Request }) => {
    const body = request.method === "POST" && request.headers.get("content-type") ? await request.json().catch(() => undefined) : request.method === "PUT" ? await request.json() : undefined;
    posts.push({ url: new URL(request.url).pathname, method: request.method, body });
    return kind === "204" ? new HttpResponse(null, { status: 204 }) : HttpResponse.json({});
  };
  server.use(
    http.get("/api/v1/auth/session", () => HttpResponse.json(session(role))),
    http.get("/api/v1/permissions", () => HttpResponse.json({ permissions: mockPermissions(role) })),
    http.get("/api/v1/units/reachable", () => HttpResponse.json({ allUnits: true, units: [] })),
    http.get("/api/v1/age-ranges", () => HttpResponse.json({ canManage: false, items: [] })),
    http.get("/api/v1/students", () => HttpResponse.json({ items: [row], page: 1, size: 25, total: 1 })),
    http.get("/api/v1/students/:id/record", ({ params }) => (opts.record ? opts.record() : params.id === "s1" ? HttpResponse.json(record) : HttpResponse.json({ code: "NOT_FOUND" }, { status: 404 }))),
    http.get("/api/v1/students/:id/history", () => (opts.history ? opts.history() : HttpResponse.json({ items: history, page: 1, size: 25, total: history.length }))),
    ...choiceRoutes(),
    http.get("/api/v1/students/:id/enrollment", () => {
      enrollmentCalls++;
      return opts.enrollment ? opts.enrollment() : HttpResponse.json(enrollment);
    }),
    http.put("/api/v1/students/:id/modalities/:row/class", async ({ request }) => {
      puts.push({ url: new URL(request.url).pathname, body: await request.json() });
      enrollment = enrollmentOf([modalityRow({ primaryClass: { id: "k3", name: "Turma Tarde" } })]);
      return HttpResponse.json(enrollment);
    }),
    http.post("/api/v1/students/:id/guardians/:gid/invite", mutate("204")),
    http.put("/api/v1/students/:id/guardians/:gid", mutate("200")),
  );
  return render(
    <ThemeProvider>
      <MemoryRouter initialEntries={[path]}>
        <Where />
        <SessionProvider>
          {opts.direct ? <StudentRecordPanel id="s1" onClose={() => {}} /> : <App />}
        </SessionProvider>
      </MemoryRouter>
    </ThemeProvider>,
  );
}

const panel = () => screen.findByRole("dialog", { name: "Ficha do aluno" });

describe("ficha do aluno (RF-ALU-03)", () => {
  beforeEach(() => localStorage.clear());

  it("CA9: clicar no nome abre o painel em /alunos/:id e Fechar volta a /alunos", async () => {
    const user = userEvent.setup();
    setup();
    await user.click(await screen.findByRole("link", { name: "Abrir a ficha de Ana Souza" }));
    const dialog = await panel();
    expect(screen.getByTestId("where").textContent).toBe("/alunos/s1");
    expect(await within(dialog).findByText("Contato Um")).toBeInTheDocument();
    await user.click(within(dialog).getByRole("button", { name: "Fechar" }));
    await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull());
    expect(screen.getByTestId("where").textContent).toBe("/alunos");
  });

  it("CA9: abrir /alunos/:id direto mostra o painel; id inexistente mostra o estado de não encontrado", async () => {
    setup("/alunos/s1");
    expect(await within(await panel()).findByText("Ana Souza")).toBeInTheDocument();
  });

  it("CA9: aluno inexistente mostra Aluno não encontrado", async () => {
    setup("/alunos/zzz");
    expect(await within(await panel()).findByText("Aluno não encontrado.")).toBeInTheDocument();
  });

  it("CA10: cabeçalho com iniciais, nome, idade, selos em texto e WhatsApp; Resumo com contato, responsáveis e emergência", async () => {
    setup("/alunos/s1");
    const dialog = await panel();
    expect(await within(dialog).findByText("AS")).toBeInTheDocument();
    expect(within(dialog).getByText("12 anos")).toBeInTheDocument();
    expect(within(dialog).getByText("E-mail com problema")).toBeInTheDocument();
    expect(within(dialog).getByText("ana@alfa.test")).toBeInTheDocument();
    expect(within(dialog).getByText("Marta Souza")).toBeInTheDocument();
    expect(within(dialog).getByText("Mãe")).toBeInTheDocument();
    expect(within(dialog).getAllByRole("tab")).toHaveLength(6);
    const link = within(dialog).queryByRole("link", { name: /WhatsApp/ });
    if (link) expect(link).toHaveAttribute("href", expect.stringContaining("wa.me/5521912345678"));
  });

  it("CA10: sem telefone o botão WhatsApp some e o contato não aparece sem a seção", async () => {
    setup("/alunos/s1");
    record = baseRecord({ contact: undefined, emergency: undefined, seals: undefined, birthDate: undefined });
    const dialog = await panel();
    await within(dialog).findByText("Marta Souza");
    expect(within(dialog).queryByRole("link", { name: /WhatsApp/ })).toBeNull();
    expect(within(dialog).queryByText("Emergência")).toBeNull();
  });

  it("CA10: as abas se alternam por teclado; Financeiro e Graduações mostram o estado vazio explicativo", async () => {
    const user = userEvent.setup();
    setup("/alunos/s1");
    const dialog = await panel();
    await within(dialog).findByText("Marta Souza");
    const first = within(dialog).getByRole("tab", { name: "Resumo" });
    first.focus();
    await user.keyboard("{ArrowRight}");
    expect(within(dialog).getByRole("tab", { name: "Matrícula e modalidades", selected: true })).toHaveFocus();
    expect(await within(dialog).findByText("Jiu-Jitsu")).toBeInTheDocument();
    await user.keyboard("{ArrowRight}");
    expect(within(dialog).getByText(/quando as cobranças estiverem disponíveis/)).toBeInTheDocument();
    await user.keyboard("{ArrowRight}");
    expect(within(dialog).getByText(/quando a graduação do aluno estiver disponível/)).toBeInTheDocument();
    await user.keyboard("{End}");
    expect(within(dialog).getByRole("tab", { name: "Histórico", selected: true })).toHaveFocus();
    await user.keyboard("{ArrowRight}");
    expect(within(dialog).getByRole("tab", { name: "Resumo", selected: true })).toHaveFocus();
    await user.keyboard("{ArrowLeft}");
    expect(within(dialog).getByRole("tab", { name: "Histórico", selected: true })).toBeInTheDocument();
    await user.keyboard("{Home}");
    expect(within(dialog).getByRole("tab", { name: "Resumo", selected: true })).toBeInTheDocument();
  });

  it("CA33: o cabeçalho traz a situação e o Resumo as modalidades ativas com unidade e turma", async () => {
    setup("/alunos/s1");
    const dialog = await panel();
    await within(dialog).findByText("Marta Souza");
    expect(await within(dialog).findByText("Jiu-Jitsu · Centro · Turma Manhã")).toBeInTheDocument();
    expect(within(dialog).getByText("Ativo")).toBeInTheDocument();
  });

  it("CA33: aluno sem matrícula mostra Sem matrícula no cabeçalho e o estado vazio na aba", async () => {
    const user = userEvent.setup();
    setup("/alunos/s1", { enrollment: () => HttpResponse.json({ id: null, status: "NONE", enrolledAt: null, reopenedAt: null, modalities: [] }) });
    const dialog = await panel();
    await within(dialog).findByText("Marta Souza");
    expect(await within(dialog).findByText("Nenhuma modalidade ativa.")).toBeInTheDocument();
    await user.click(within(dialog).getByRole("tab", { name: "Matrícula e modalidades" }));
    expect(within(dialog).getAllByText("Sem matrícula")).toHaveLength(2);
  });

  it("CA33: a aba Matrícula mostra a modalidade com plano, vencimento e ações; Encerrar habilitado", async () => {
    const user = userEvent.setup();
    setup("/alunos/s1");
    const dialog = await panel();
    await within(dialog).findByText("Marta Souza");
    await user.click(within(dialog).getByRole("tab", { name: "Matrícula e modalidades" }));
    expect(await within(dialog).findByText("Plano Mensal")).toBeInTheDocument();
    expect(within(dialog).getByText("Dia 5")).toBeInTheDocument();
    expect(within(dialog).getByText("Outros horários: Turma Noite")).toBeInTheDocument();
    for (const name of ["Trocar turma", "Trocar plano", "Transferir"]) expect(within(dialog).getByRole("button", { name })).toBeEnabled();
    expect(within(dialog).getByRole("button", { name: "Encerrar" })).toBeEnabled();
    expect(within(dialog).queryByText("Disponível em breve")).toBeNull();
  });

  it("CA33: modalidade de outra unidade e plano agendado: sem ações, só leitura", async () => {
    const user = userEvent.setup();
    setup("/alunos/s1", { enrollment: () => HttpResponse.json(enrollmentOf([modalityRow({ canEdit: false, nextPlan: { id: "p2", name: "Plano Trimestral" } })])) });
    const dialog = await panel();
    await within(dialog).findByText("Marta Souza");
    await user.click(within(dialog).getByRole("tab", { name: "Matrícula e modalidades" }));
    expect(await within(dialog).findByText("A partir da próxima cobrança: Plano Trimestral")).toBeInTheDocument();
    expect(within(dialog).getByText("Modalidade de outra unidade: só leitura.")).toBeInTheDocument();
    expect(within(dialog).queryByRole("button", { name: "Trocar turma" })).toBeNull();
    expect(within(dialog).queryByRole("button", { name: "Encerrar" })).toBeNull();
  });

  it("CA34: Trocar turma pela ficha chama a rota e atualiza a matrícula", async () => {
    const user = userEvent.setup();
    setup("/alunos/s1");
    const dialog = await panel();
    await within(dialog).findByText("Marta Souza");
    await user.click(within(dialog).getByRole("tab", { name: "Matrícula e modalidades" }));
    await user.click(await within(dialog).findByRole("button", { name: "Trocar turma" }));
    const janela = await screen.findByRole("dialog", { name: "Trocar turma de Jiu-Jitsu" });
    await user.selectOptions(await within(janela).findByLabelText("Turma principal"), "k3");
    await user.click(within(janela).getByRole("button", { name: "Trocar turma" }));
    await waitFor(() => expect(puts).toHaveLength(1));
    expect(puts[0].url).toBe("/api/v1/students/s1/modalities/m1/class");
    expect(await within(dialog).findByText("Turma Tarde")).toBeInTheDocument();
    expect(enrollmentCalls).toBeGreaterThan(1);
  });

  it("CA33: matrícula com 403 mostra sem permissão; com 500 pede nova tentativa", async () => {
    const user = userEvent.setup();
    const first = setup("/alunos/s1", { enrollment: () => HttpResponse.json({ code: "FORBIDDEN" }, { status: 403 }) });
    let dialog = await panel();
    await within(dialog).findByText("Marta Souza");
    await user.click(within(dialog).getByRole("tab", { name: "Matrícula e modalidades" }));
    expect(await within(dialog).findByText("Você não tem acesso")).toBeInTheDocument();
    first.unmount();
    setup("/alunos/s1", { enrollment: () => HttpResponse.json({ code: "INTERNAL_ERROR" }, { status: 500 }) });
    dialog = await panel();
    await within(dialog).findByText("Marta Souza");
    await user.click(within(dialog).getByRole("tab", { name: "Matrícula e modalidades" }));
    expect(await within(dialog).findByRole("button", { name: "Tentar de novo" })).toBeInTheDocument();
  });

  it("CA35: a ficha do Professor só tem Resumo e Responsáveis, sem botões de escrita, sem Histórico e sem pedir a matrícula", async () => {
    const user = userEvent.setup();
    setup("/alunos/s1", { role: "TEACHER", direct: true });
    const dialog = await panel();
    await within(dialog).findByText("Marta Souza");
    expect(within(dialog).getAllByRole("tab").map((tab) => tab.textContent)).toEqual(["Resumo", "Responsáveis"]);
    expect(within(dialog).queryByRole("button", { name: "Editar dados" })).toBeNull();
    await user.click(within(dialog).getByRole("tab", { name: "Responsáveis" }));
    expect(await within(dialog).findByText("Marta Souza")).toBeInTheDocument();
    for (const name of ["Tornar financeiro", "Desvincular", "Reenviar convite", "Vincular responsável"]) expect(within(dialog).queryByRole("button", { name })).toBeNull();
    expect(enrollmentCalls).toBe(0);
  });

  it("CA35: a ficha da gestão mantém os botões de escrita", async () => {
    const user = userEvent.setup();
    setup("/alunos/s1");
    const dialog = await panel();
    await within(dialog).findByText("Marta Souza");
    await user.click(within(dialog).getByRole("tab", { name: "Responsáveis" }));
    for (const name of ["Desvincular", "Reenviar convite", "Vincular responsável"]) expect(await within(dialog).findByRole("button", { name })).toBeInTheDocument();
  });

  it("CA12: carregando enquanto a ficha não chega", async () => {
    let release: () => void = () => {};
    const gate = new Promise<void>((r) => (release = r));
    setup("/alunos/s1", { record: async () => (await gate, HttpResponse.json(baseRecord())) });
    const dialog = await panel();
    expect(within(dialog).getByRole("status")).toHaveTextContent("Carregando...");
    release();
    expect(await within(dialog).findByText("Marta Souza")).toBeInTheDocument();
  });

  it("CA12: Histórico vazio", async () => {
    const user = userEvent.setup();
    setup("/alunos/s1");
    const dialog = await panel();
    await within(dialog).findByText("Marta Souza");
    history = [];
    await user.click(within(dialog).getByRole("tab", { name: "Histórico" }));
    expect(await within(dialog).findByText("Ainda não há ações registradas para este aluno.")).toBeInTheDocument();
  });

  it("CA12: erro 500 na ficha mostra Tentar de novo e refaz a consulta", async () => {
    const user = userEvent.setup();
    let fail = true;
    setup("/alunos/s1", { record: () => (fail ? HttpResponse.json({ code: "INTERNAL_ERROR" }, { status: 500 }) : HttpResponse.json(baseRecord())) });
    const dialog = await panel();
    const retry = await within(dialog).findByRole("button", { name: "Tentar de novo" });
    fail = false;
    await user.click(retry);
    expect(await within(dialog).findByText("Marta Souza")).toBeInTheDocument();
  });

  it("CA12: 403 mostra sem permissão; o Histórico com 403 também", async () => {
    setup("/alunos/s1", { record: () => HttpResponse.json({ code: "FORBIDDEN" }, { status: 403 }) });
    expect(await within(await panel()).findByText("Você não tem acesso")).toBeInTheDocument();
  });

  it("CA12: Histórico lista a ação em texto com ator e data no fuso da unidade; ação desconhecida mostra o código; erro e 403 do Histórico", async () => {
    const user = userEvent.setup();
    history = [];
    setup("/alunos/s1");
    history = [
      { action: "student.guardian_unlinked", at: "2026-01-11T02:30:00Z", actor: "Ana Coordenadora", actorProfile: "COORDINATOR", reason: "saiu da família" },
      { action: "student.nova_acao", at: "2026-01-10T15:00:00Z", actor: null, actorProfile: null, reason: null },
    ];
    const dialog = await panel();
    await within(dialog).findByText("Marta Souza");
    await user.click(within(dialog).getByRole("tab", { name: "Histórico" }));
    expect(await within(dialog).findByText("Responsável desvinculado")).toBeInTheDocument();
    expect(within(dialog).getByText(/10\/01\/2026 23:30|10\/01\/2026 23h30/)).toBeInTheDocument();
    expect(within(dialog).getByText(/por Ana Coordenadora/)).toBeInTheDocument();
    expect(within(dialog).getByText("Motivo: saiu da família")).toBeInTheDocument();
    expect(within(dialog).getByText("student.nova_acao")).toBeInTheDocument();
  });

  it("CA12: Histórico com erro mostra Tentar de novo e com 403 mostra sem permissão", async () => {
    const user = userEvent.setup();
    setup("/alunos/s1", { history: () => HttpResponse.json({ code: "FORBIDDEN" }, { status: 403 }) });
    const dialog = await panel();
    await within(dialog).findByText("Marta Souza");
    await user.click(within(dialog).getByRole("tab", { name: "Histórico" }));
    expect(await within(dialog).findByText("Você não tem acesso")).toBeInTheDocument();
  });

  it("CA12: Histórico com erro 500 mostra Tentar de novo", async () => {
    const user = userEvent.setup();
    setup("/alunos/s1", { history: () => HttpResponse.json({ code: "INTERNAL_ERROR" }, { status: 500 }) });
    const dialog = await panel();
    await within(dialog).findByText("Marta Souza");
    await user.click(within(dialog).getByRole("tab", { name: "Histórico" }));
    expect(await within(dialog).findByRole("button", { name: "Tentar de novo" })).toBeInTheDocument();
  });

  it("CA12: Histórico com mais de uma página pagina", async () => {
    const user = userEvent.setup();
    const pages: string[] = [];
    setup("/alunos/s1", {
      history: () => HttpResponse.json({ items: [{ action: "student.created", at: "2026-01-10T15:00:00Z", actor: null, actorProfile: null, reason: null }], page: 1, size: 25, total: 30 }),
    });
    server.use(
      http.get("/api/v1/students/:id/history", ({ request }) => {
        pages.push(new URL(request.url).searchParams.get("page")!);
        return HttpResponse.json({ items: [{ action: "student.created", at: "2026-01-10T15:00:00Z", actor: null, actorProfile: null, reason: null }], page: 1, size: 25, total: 30 });
      }),
    );
    const dialog = await panel();
    await within(dialog).findByText("Marta Souza");
    await user.click(within(dialog).getByRole("tab", { name: "Histórico" }));
    await user.click(await within(dialog).findByRole("button", { name: "Próxima" }));
    await waitFor(() => expect(pages).toContain("2"));
    await user.click(await within(dialog).findByRole("button", { name: "Anterior" }));
    await waitFor(() => expect(pages.at(-1)).toBe("1"));
  });

  it.each([
    ["claro", false],
    ["escuro", true],
  ])("CA12: a 360 px não tem violações no axe no tema %s", async (_n, dark) => {
    setDevicePrefersDark(dark);
    window.innerWidth = 360;
    setup("/alunos/s1");
    await within(await panel()).findByText("Marta Souza");
    expect(await axeViolations(document.body)).toEqual([]);
  });
});
