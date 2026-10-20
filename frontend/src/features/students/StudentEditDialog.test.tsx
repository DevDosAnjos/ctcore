import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { vi } from "vitest";
import { MemoryRouter, useLocation } from "react-router";
import { App } from "@/App";
import { SessionProvider, type SessionView } from "@/features/auth/SessionProvider";
import { mockPermissions } from "@/mocks/permissions";
import { server } from "@/mocks/server";
import { axeViolations } from "@/test/axe";
import { setDevicePrefersDark } from "@/test/device";
import { ThemeProvider } from "@/theme/ThemeProvider";
import { StudentRecordPanel } from "./StudentRecordPanel";
import type { StudentDetail, StudentRecord, StudentRow } from "./types";

const session = (role: string): SessionView => ({
  account: { id: "a", email: "x@x.com", name: "X" },
  links: [{ id: "l1", organizationId: "o", organizationName: "Alfa", role, unitIds: [] }],
  activeLinkId: "l1",
  twoFactor: { enabled: false, required: false },
});
const row: StudentRow = {
  id: "s1", name: "Ana Souza", photoUrl: null, birthDate: "2014-05-02", age: 12, minor: true, ageRange: null,
  registeredUnit: { id: "u1", name: "Centro" }, createdAt: "2026-01-10T12:00:00Z", seals: [],
};
const detail = (over: Partial<StudentDetail> = {}): StudentDetail => ({
  id: "s1", registeredUnitId: "u1", name: "Ana Souza", birthDate: "2014-05-02", minor: true, postalCode: "01001000", street: "Rua Um", number: "1", complement: null, district: "Centro", city: "Rio", state: "RJ",
  cpf: null, email: null, phone: null, gender: null, referral: null, emergencyName: null, emergencyRelationship: null, emergencyPhone: null, photoUrl: null, emailProblem: false,
  guardians: [{ guardianId: "g1", name: "Marta Souza", email: "marta@alfa.test", phone: "21987654321", financial: true, access: "PENDING", emailProblem: false }], ...over,
});
const recordOf = (d: StudentDetail): StudentRecord => ({
  id: d.id, name: d.name, photoUrl: d.photoUrl, birthDate: d.birthDate, age: 12, minor: d.minor, ageRange: null, registeredUnit: { id: "u1", name: "Centro" }, seals: [],
  contact: { phone: d.phone, email: d.email, emailProblem: false }, cpf: d.cpf,
  address: { postalCode: d.postalCode, street: d.street, number: d.number, complement: d.complement, district: d.district, city: d.city, state: d.state },
  emergency: { name: d.emergencyName, relationship: d.emergencyRelationship, phone: d.emergencyPhone }, guardians: d.guardians,
});

// jsdom não decodifica imagem nem desenha em canvas: Image, URL e canvas são trocados por simulações (como no teste do corte).
class FakeImage {
  width = 800;
  height = 600;
  onload: (() => void) | null = null;
  onerror: (() => void) | null = null;
  set src(_: string) {
    queueMicrotask(() => this.onload?.());
  }
}
beforeEach(() => {
  localStorage.clear();
  vi.stubGlobal("Image", FakeImage);
  URL.createObjectURL = vi.fn(() => "blob:foto");
  URL.revokeObjectURL = vi.fn();
  HTMLCanvasElement.prototype.getContext = vi.fn(() => ({ drawImage: vi.fn() })) as never;
  HTMLCanvasElement.prototype.toBlob = vi.fn((cb: BlobCallback) => cb(new Blob(["png"], { type: "image/png" }))) as never;
});
afterEach(() => vi.unstubAllGlobals());

function Where() {
  return <p hidden data-testid="where">{useLocation().pathname}</p>;
}

let current: StudentDetail;
let puts: { name?: string; confirmDuplicate?: boolean; [key: string]: unknown }[];
let listCalls: number;

function setup(opts: { role?: string; detail?: () => Response | Promise<Response>; put?: (body: Record<string, unknown>) => Response | undefined } = {}) {
  const role = opts.role ?? "COORDINATOR";
  current = detail();
  puts = [];
  listCalls = 0;
  server.use(
    http.get("/api/v1/auth/session", () => HttpResponse.json(session(role))),
    http.get("/api/v1/permissions", () => HttpResponse.json({ permissions: mockPermissions(role) })),
    http.get("/api/v1/units/reachable", () => HttpResponse.json({ allUnits: true, units: [] })),
    http.get("/api/v1/age-ranges", () => HttpResponse.json({ canManage: false, items: [] })),
    http.get("/api/v1/students", () => {
      listCalls += 1;
      return HttpResponse.json({ items: [{ ...row, name: current.name }], page: 1, size: 25, total: 1 });
    }),
    http.get("/api/v1/students/s1/record", () => HttpResponse.json(recordOf(current))),
    http.get("/api/v1/students/s1/history", () => HttpResponse.json({ items: [], page: 1, size: 25, total: 0 })),
    http.get("/api/v1/students/other/record", () => HttpResponse.json({ ...recordOf(current), id: "other", name: "Bia Lima" })),
    http.get("/api/v1/students/s1", () => (opts.detail ? opts.detail() : HttpResponse.json(current))),
    http.put("/api/v1/students/s1", async ({ request }) => {
      const body = (await request.json()) as Record<string, unknown>;
      puts.push(body);
      const refused = opts.put?.(body);
      if (refused) return refused;
      current = { ...current, name: body.name as string, street: body.street as string };
      return HttpResponse.json(current);
    }),
  );
  return render(
    <ThemeProvider>
      <MemoryRouter initialEntries={["/alunos/s1"]}>
        <Where />
        <SessionProvider>
          <App />
        </SessionProvider>
      </MemoryRouter>
    </ThemeProvider>,
  );
}

const openEdit = async (user: ReturnType<typeof userEvent.setup>) => {
  const panel = await screen.findByRole("dialog", { name: "Ficha do aluno" });
  await user.click(await within(panel).findByRole("button", { name: "Editar dados" }));
  const dialog = await screen.findByRole("dialog", { name: "Editar dados do aluno" });
  await within(dialog).findByLabelText("Nome");
  return dialog;
};
const problem = (status: number, code: string, fields: { field: string; message: string }[] = []) =>
  HttpResponse.json({ code, message: "x", changed: false, fields }, { status });

describe("StudentEditDialog (RF-ALU-04, CA14, CA15)", () => {
  it("abre preenchida pela ficha, salva, fecha e a ficha e a lista mostram os dados novos", async () => {
    const user = userEvent.setup();
    setup();
    const dialog = await openEdit(user);
    expect(within(dialog).getByLabelText("Nome")).toHaveValue("Ana Souza");
    expect(within(dialog).getByLabelText("CEP")).toHaveValue("01001-000");
    expect(within(dialog).getByLabelText("UF")).toHaveValue("RJ");
    const before = listCalls;
    await user.clear(within(dialog).getByLabelText("Nome"));
    await user.type(within(dialog).getByLabelText("Nome"), "  Ana Souza Lima ");
    await user.clear(within(dialog).getByLabelText("Logradouro"));
    await user.type(within(dialog).getByLabelText("Logradouro"), "Rua Nova");
    await user.click(within(dialog).getByRole("button", { name: "Salvar" }));
    await waitFor(() => expect(screen.queryByRole("dialog", { name: "Editar dados do aluno" })).toBeNull());
    expect(puts).toHaveLength(1);
    expect(puts[0]).toMatchObject({ name: "Ana Souza Lima", street: "Rua Nova", postalCode: "01001000", state: "RJ", cpf: null, email: null, phone: null, complement: null });
    expect(puts[0]).not.toHaveProperty("registeredUnitId");
    expect(puts[0]).not.toHaveProperty("guardians");
    expect(puts[0]).not.toHaveProperty("confirmDuplicate");
    const panel = screen.getByRole("dialog", { name: "Ficha do aluno" });
    expect(await within(panel).findByText("Ana Souza Lima")).toBeInTheDocument();
    await waitFor(() => expect(listCalls).toBeGreaterThan(before));
    expect(await screen.findByRole("link", { name: "Abrir a ficha de Ana Souza Lima", hidden: true })).toBeInTheDocument();
  });

  it("valida no cliente: adulto exige CPF, e-mail e celular; o menor pode salvar sem eles; emergência é tudo ou nada", async () => {
    const user = userEvent.setup();
    setup();
    const dialog = await openEdit(user);
    fireEvent.change(within(dialog).getByLabelText("Nascimento"), { target: { value: "1990-01-01" } });
    await user.click(within(dialog).getByRole("button", { name: "Salvar" }));
    expect(within(dialog).getAllByText("Preencha este campo.")).toHaveLength(3);
    expect(puts).toEqual([]);
    fireEvent.change(within(dialog).getByLabelText("Nascimento"), { target: { value: "2014-05-02" } });
    const names = [within(dialog).getByLabelText("Nome"), within(dialog).getByLabelText("Nome do contato")];
    await user.type(names[1], "Contato");
    await user.click(within(dialog).getByRole("button", { name: "Salvar" }));
    expect(within(dialog).getByText("Informe nome, parentesco e telefone do contato, ou deixe os três em branco.")).toBeInTheDocument();
    expect(puts).toEqual([]);
    await user.clear(names[1]);
    await user.click(within(dialog).getByRole("button", { name: "Salvar" }));
    await waitFor(() => expect(puts).toHaveLength(1));
  });

  it("erro do servidor por campo aparece no campo e a janela continua aberta com os dados", async () => {
    const user = userEvent.setup();
    setup({ put: () => problem(400, "VALIDATION_ERROR", [{ field: "street", message: "logradouro recusado" }]) });
    const dialog = await openEdit(user);
    await user.click(within(dialog).getByRole("button", { name: "Salvar" }));
    expect(await within(dialog).findByText("logradouro recusado")).toBeInTheDocument();
    expect(within(dialog).getByLabelText("Nome")).toHaveValue("Ana Souza");
  });

  it("CA15: CPF repetido mostra o existente, mantém os dados e Abrir ficha leva à ficha dele", async () => {
    const user = userEvent.setup();
    setup({ put: () => problem(409, "STUDENT_CPF_EXISTS", [{ field: "id", message: "other" }, { field: "name", message: "Bia Lima" }, { field: "photoUrl", message: "" }]) });
    const dialog = await openEdit(user);
    await user.clear(within(dialog).getByLabelText("Nome"));
    await user.type(within(dialog).getByLabelText("Nome"), "Nome Digitado");
    await user.click(within(dialog).getByRole("button", { name: "Salvar" }));
    expect(await within(dialog).findByText("Este CPF já está cadastrado para Bia Lima.")).toBeInTheDocument();
    expect(within(dialog).queryByRole("button", { name: "É outra pessoa, salvar mesmo assim" })).toBeNull();
    expect(within(dialog).getByLabelText("Nome")).toHaveValue("Nome Digitado");
    await user.click(within(dialog).getByRole("button", { name: "Abrir ficha" }));
    expect(await screen.findByText("Bia Lima", { selector: "p" })).toBeInTheDocument();
    expect(screen.getByTestId("where").textContent).toBe("/alunos/other");
    expect(screen.queryByRole("dialog", { name: "Editar dados do aluno" })).toBeNull();
  });

  it("CA15: aviso de nome e nascimento mostra o parecido e o botão reenvia com confirmDuplicate", async () => {
    const user = userEvent.setup();
    setup({ put: (body) => (body.confirmDuplicate ? undefined : problem(409, "STUDENT_POSSIBLE_DUPLICATE", [{ field: "id", message: "other" }, { field: "name", message: "Bia Lima" }, { field: "photoUrl", message: "" }])) });
    const dialog = await openEdit(user);
    await user.click(within(dialog).getByRole("button", { name: "Salvar" }));
    expect(await within(dialog).findByText("Parece ser Bia Lima, já cadastrado.")).toBeInTheDocument();
    await user.click(within(dialog).getByRole("button", { name: "É outra pessoa, salvar mesmo assim" }));
    await waitFor(() => expect(screen.queryByRole("dialog", { name: "Editar dados do aluno" })).toBeNull());
    expect(puts).toHaveLength(2);
    expect(puts[0]).not.toHaveProperty("confirmDuplicate");
    expect(puts[1].confirmDuplicate).toBe(true);
  });

  it("falha de carga mostra o erro com Tentar de novo; 403 mostra o estado sem permissão", async () => {
    const user = userEvent.setup();
    let calls = 0;
    setup({ detail: () => (++calls === 1 ? problem(500, "INTERNAL_ERROR") : HttpResponse.json(current)) });
    const panel = await screen.findByRole("dialog", { name: "Ficha do aluno" });
    await user.click(await within(panel).findByRole("button", { name: "Editar dados" }));
    const dialog = await screen.findByRole("dialog", { name: "Editar dados do aluno" });
    await user.click(await within(dialog).findByRole("button", { name: "Tentar de novo" }));
    expect(await within(dialog).findByLabelText("Nome")).toHaveValue("Ana Souza");
  });

  it("403 ao carregar mostra o estado sem permissão", async () => {
    const user = userEvent.setup();
    setup({ detail: () => problem(403, "FORBIDDEN") });
    const panel = await screen.findByRole("dialog", { name: "Ficha do aluno" });
    await user.click(await within(panel).findByRole("button", { name: "Editar dados" }));
    const dialog = await screen.findByRole("dialog", { name: "Editar dados do aluno" });
    expect(await within(dialog).findByText("Você não tem acesso")).toBeInTheDocument();
  });

  it("quem não tem STUDENT_MANAGE não vê o botão", async () => {
    server.use(
      http.get("/api/v1/auth/session", () => HttpResponse.json(session("TEACHER"))),
      http.get("/api/v1/permissions", () => HttpResponse.json({ permissions: mockPermissions("TEACHER") })),
      http.get("/api/v1/students/s1/record", () => HttpResponse.json(recordOf(detail()))),
    );
    render(
      <ThemeProvider>
        <MemoryRouter>
          <SessionProvider>
            <StudentRecordPanel id="s1" onClose={() => undefined} />
          </SessionProvider>
        </MemoryRouter>
      </ThemeProvider>,
    );
    const panel = await screen.findByRole("dialog", { name: "Ficha do aluno" });
    await within(panel).findByText("Marta Souza");
    expect(screen.queryByRole("button", { name: "Editar dados" })).toBeNull();
  });

  it("foto: o corte é enviado depois de salvar; se falhar, os dados ficam salvos e dá para tentar de novo", async () => {
    const user = userEvent.setup();
    setup();
    let photoCalls = 0;
    server.use(
      http.put("/api/v1/students/s1/photo", () => {
        photoCalls += 1;
        return photoCalls === 1 ? problem(500, "INTERNAL_ERROR") : HttpResponse.json(current);
      }),
    );
    const dialog = await openEdit(user);
    fireEvent.change(within(dialog).getByLabelText("Arquivo da foto", { selector: "input" }), { target: { files: [new File(["x"], "f.png", { type: "image/png" })] } });
    const crop = await screen.findByRole("dialog", { name: "Cortar a foto" });
    const confirm = await within(crop).findByRole("button", { name: "Usar este corte" });
    await waitFor(() => expect(confirm).toBeEnabled());
    await user.click(confirm);
    expect(await within(dialog).findByText("Foto escolhida. Ela é enviada ao salvar.")).toBeInTheDocument();
    await user.click(within(dialog).getByRole("button", { name: "Salvar" }));
    expect(await within(dialog).findByText("Os dados foram salvos, mas a foto não foi enviada.")).toBeInTheDocument();
    expect(puts).toHaveLength(1);
    await user.click(within(dialog).getByRole("button", { name: "Tentar enviar a foto de novo" }));
    await waitFor(() => expect(screen.queryByRole("dialog", { name: "Editar dados do aluno" })).toBeNull());
    expect(puts).toHaveLength(1);
    expect(photoCalls).toBe(2);
  });

  it("foto: tipo inválido é recusado antes do corte; remover envia DELETE", async () => {
    const user = userEvent.setup();
    setup();
    let removed = 0;
    current = detail({ photoUrl: "https://files.test/a.png" });
    server.use(
      http.delete("/api/v1/students/s1/photo", () => {
        removed += 1;
        current = detail();
        return HttpResponse.json(current);
      }),
    );
    const dialog = await openEdit(user);
    fireEvent.change(within(dialog).getByLabelText("Arquivo da foto", { selector: "input" }), { target: { files: [new File(["x"], "f.gif", { type: "image/gif" })] } });
    expect(await within(dialog).findByText("Use uma imagem PNG, JPG ou WebP.")).toBeInTheDocument();
    expect(screen.queryByRole("dialog", { name: "Cortar a foto" })).toBeNull();
    await user.click(within(dialog).getByRole("button", { name: "Remover foto" }));
    await user.click(within(dialog).getByRole("button", { name: "Salvar" }));
    await waitFor(() => expect(removed).toBe(1));
  });

  it.each([
    ["claro", false],
    ["escuro", true],
  ])("a 360 px não tem violações no axe no tema %s", async (_n, dark) => {
    const user = userEvent.setup();
    setDevicePrefersDark(dark);
    window.innerWidth = 360;
    setup();
    await openEdit(user);
    expect(await axeViolations(document.body)).toEqual([]);
  });
});
