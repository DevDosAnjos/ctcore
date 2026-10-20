import { readFileSync } from "node:fs";
import { join } from "node:path";
import { parse } from "yaml";

const openapi = parse(readFileSync(join(process.cwd(), "../docs/openapi.yaml"), "utf8"));
const json = { "Content-Type": "application/json" };
const login = async (email: string, linkId?: string) => {
  await fetch("/api/v1/auth/login", { method: "POST", headers: json, body: JSON.stringify({ email, password: "segredo123" }) });
  if (linkId) await fetch("/api/v1/auth/session/link", { method: "PUT", headers: json, body: JSON.stringify({ linkId }) });
};
const send = (method: string, path: string, body?: unknown) =>
  fetch(`/api/v1${path}`, { method, headers: json, body: body === undefined ? undefined : JSON.stringify(body) });
const ADULT = "00000000-0000-0000-0000-000000000701";
const MINOR = "00000000-0000-0000-0000-000000000702";
const GUARDIAN = "00000000-0000-0000-0000-000000000801";
const CENTRO = "00000000-0000-0000-0000-000000000021";
const NORTE = "00000000-0000-0000-0000-000000000022";
const adultBody = (extra: Record<string, unknown> = {}) => ({
  registeredUnitId: CENTRO, name: "Novo Aluno", birthDate: "1991-02-03", postalCode: "01001-000", street: "Rua", number: "5", district: "Centro", city: "Rio", state: "rj",
  cpf: "111.444.777-35", email: "novo@alfa.test", phone: "(21) 91234-5678", ...extra,
});
const guardian = (extra: Record<string, unknown> = {}) => ({ name: "Resp Dois", cpf: "123.456.789-09", email: "resp2@alfa.test", phone: "21987654321", ...extra });
const minorBody = (extra: Record<string, unknown> = {}) => ({ ...adultBody({ name: "Menor Novo", birthDate: "2018-04-05", cpf: null, email: null, phone: null }), ...extra });

describe("alunos e responsáveis x OpenAPI (RF-ALU-01, CA23)", () => {
  it("o contrato declara as rotas e os esquemas do servidor", () => {
    expect(Object.keys(openapi.paths["/students"].post.responses)).toEqual(expect.arrayContaining(["201", "400", "401", "403", "409"]));
    expect(Object.keys(openapi.paths["/students/lookup"].get.responses)).toEqual(expect.arrayContaining(["200", "400", "401", "403", "404"]));
    expect(Object.keys(openapi.paths["/students/{id}/guardians/{guardianId}/invite"].post.responses)).toEqual(expect.arrayContaining(["204", "404", "409", "429"]));
    expect(Object.keys(openapi.paths["/portal/students"].get.responses)).toEqual(expect.arrayContaining(["200", "401", "403"]));
    expect(openapi.components.schemas.Error.properties.code.enum).toEqual(
      expect.arrayContaining(["STUDENT_CPF_EXISTS", "STUDENT_POSSIBLE_DUPLICATE", "MINOR_NEEDS_GUARDIAN", "GUARDIAN_LIMIT", "GUARDIAN_CONFLICT"]),
    );
  });

  it("sem sessão 401; Professor e portal 403; Administrador só alcança a unidade dele (404 fora, 403 ao cadastrar fora)", async () => {
    expect((await send("GET", `/students/${ADULT}`)).status).toBe(401);
    await login("varios@exemplo.com", "00000000-0000-0000-0000-000000000013");
    expect((await send("GET", `/students/${ADULT}`)).status).toBe(403);
    expect((await send("POST", "/students", adultBody())).status).toBe(403);
    await login("varios@exemplo.com", "00000000-0000-0000-0000-000000000011");
    expect((await send("GET", `/students/${ADULT}`)).status).toBe(200);
    expect((await send("POST", "/students", adultBody({ registeredUnitId: NORTE }))).status).toBe(400);
    await login("ana@exemplo.com");
    const other = await (await send("POST", "/students", adultBody())).json();
    await login("varios@exemplo.com", "00000000-0000-0000-0000-000000000011");
    expect((await send("GET", `/students/${other.id}`)).status).toBe(200);
  });

  it("cria o adulto no formato de StudentDetail (CPF e telefone só com dígitos); ignora campo de saúde", async () => {
    await login("ana@exemplo.com");
    const res = await send("POST", "/students", adultBody({ bloodType: "O+" }));
    expect(res.status).toBe(201);
    const data = await res.json();
    expect(Object.keys(data).sort()).toEqual([...openapi.components.schemas.StudentDetail.required].sort());
    expect(data).toMatchObject({ cpf: "11144477735", phone: "21912345678", minor: false, state: "RJ", guardians: [] });
    expect(data).not.toHaveProperty("bloodType");
    expect(await (await send("GET", `/students/${data.id}`)).json()).toEqual(data);
  });

  it("valida o cadastro: nome, CPF, adulto sem CPF, menor sem responsável, financeiro e limite", async () => {
    await login("ana@exemplo.com");
    for (const body of [adultBody({ name: "A" }), adultBody({ cpf: "111.111.111-11" }), adultBody({ cpf: "" }), adultBody({ birthDate: "2999-01-01" }), minorBody()]) {
      expect((await send("POST", "/students", body)).status).toBe(400);
    }
    expect((await send("POST", "/students", minorBody({ guardians: [guardian(), guardian({ cpf: "98765432100", email: "outro@alfa.test" })] }))).status).toBe(400);
    expect((await send("POST", "/students", minorBody({ guardians: [guardian({ financial: true }), guardian({ financial: true, cpf: "98765432100", email: "outro@alfa.test" })] }))).status).toBe(400);
    const made = await send("POST", "/students", minorBody({ guardians: [guardian()] }));
    expect(made.status).toBe(201);
    const data = await made.json();
    expect(data).toMatchObject({ minor: true, guardians: [{ name: "Resp Dois", financial: true, access: "PENDING" }] });
    expect(Object.keys(data.guardians[0]).sort()).toEqual([...openapi.components.schemas.StudentGuardianRow.required].sort());
  });

  it("CPF repetido dá 409 com id, nome e foto; nome e nascimento iguais avisam e confirmDuplicate cria; busca por CPF devolve só três campos", async () => {
    await login("ana@exemplo.com");
    const same = await send("POST", "/students", adultBody({ cpf: "529.982.247-25" }));
    expect(same.status).toBe(409);
    const error = await same.json();
    expect(error.code).toBe("STUDENT_CPF_EXISTS");
    expect(error.fields.map((f: { field: string }) => f.field)).toEqual(["id", "name", "photoUrl"]);
    const warn = minorBody({ name: "ALUNO MENOR FICTICIO", birthDate: "2014-05-02", guardians: [guardian()] });
    expect((await (await send("POST", "/students", warn)).json()).code).toBe("STUDENT_POSSIBLE_DUPLICATE");
    expect((await send("POST", "/students", { ...warn, confirmDuplicate: true })).status).toBe(201);
    const found = await (await send("GET", "/students/lookup?cpf=52998224725")).json();
    expect(found).toEqual({ id: ADULT, name: "Aluno Adulto Fictício", photoUrl: null });
    expect((await send("GET", "/students/lookup?cpf=11144477735")).status).toBe(404);
    expect((await send("GET", "/students/lookup?cpf=123")).status).toBe(400);
  });

  it("ligar, trocar o financeiro e desligar seguem as regras (motivo, limite, menor nunca sem responsável)", async () => {
    await login("ana@exemplo.com");
    const base = `/students/${MINOR}/guardians`;
    expect((await send("POST", base, guardian())).status).toBe(400);
    expect((await send("POST", base, guardian({ reason: "ab" }))).status).toBe(400);
    expect((await send("POST", base, guardian({ cpf: "98765432100" , reason: "Pedido" }))).status).toBe(409);
    const linked = await send("POST", base, guardian({ reason: "Pedido da família" }));
    expect(linked.status).toBe(200);
    expect((await send("POST", base, guardian({ name: "Terceiro", cpf: "39053344705", email: "t@alfa.test", reason: "Outro" }))).status).toBe(409);
    const second = (await linked.json()).guardians.find((g: { name: string }) => g.name === "Resp Dois").guardianId;
    expect((await send("PUT", `${base}/${second}`, { financial: false })).status).toBe(400);
    const swapped = await (await send("PUT", `${base}/${second}`, { financial: true })).json();
    expect(swapped.guardians[0].guardianId).toBe(second);
    expect((await send("POST", `${base}/${second}/unlink`, { reason: "x" })).status).toBe(400);
    const left = await (await send("POST", `${base}/${second}/unlink`, { reason: "Pedido do responsável" })).json();
    expect(left.guardians).toMatchObject([{ guardianId: GUARDIAN, financial: true }]);
    const refused = await send("POST", `${base}/${GUARDIAN}/unlink`, { reason: "Tentativa" });
    expect(refused.status).toBe(409);
    expect((await refused.json()).code).toBe("MINOR_NEEDS_GUARDIAN");
  });

  it("reenvia o convite até 3 vezes por dia (429 no quarto)", async () => {
    await login("ana@exemplo.com");
    const path = `/students/${MINOR}/guardians/${GUARDIAN}/invite`;
    for (let i = 0; i < 3; i++) expect((await send("POST", path)).status).toBe(204);
    expect((await send("POST", path)).status).toBe(429);
    expect((await send("POST", `/students/${MINOR}/guardians/00000000-0000-0000-0000-0000000009ff/invite`)).status).toBe(404);
  });

  it("portal: cartões só com id, nome, foto e self; gestão recebe 403", async () => {
    expect((await send("GET", "/portal/students")).status).toBe(401);
    await login("ana@exemplo.com");
    expect((await send("GET", "/portal/students")).status).toBe(403);
    await login("varios@exemplo.com", "00000000-0000-0000-0000-000000000012");
    const cards = await (await send("GET", "/portal/students")).json();
    expect(cards).toHaveLength(2);
    expect(cards[0]).toMatchObject({ id: ADULT, self: true });
    for (const c of cards) expect(Object.keys(c).sort()).toEqual([...openapi.components.schemas.PortalStudentCard.required].sort());
  });
});
