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
const base = {
  name: "Aluno Adulto Editado", birthDate: "1990-03-10", postalCode: "01001-000", street: "Rua Nova", number: "9", district: "Centro", city: "Rio de Janeiro", state: "RJ",
  cpf: "52998224725", email: "novo@alfa.test", phone: "21900000009",
};
// FormData e File do jsdom não passam pelo fetch do Node; o corpo multipart vai montado à mão.
const multipart = (content: string) => ({
  method: "PUT",
  headers: { "Content-Type": "multipart/form-data; boundary=x" },
  body: `--x\r\nContent-Disposition: form-data; name="file"; filename="f.png"\r\nContent-Type: image/png\r\n\r\n${content}\r\n--x--\r\n`,
});
const photo = () => fetch(`/api/v1/students/${ADULT}/photo`, multipart("a".repeat(64)));

describe("edição do aluno x OpenAPI (RF-ALU-04)", () => {
  it("o contrato declara as três rotas e o esquema StudentUpdate sem unidade nem responsáveis", () => {
    expect(Object.keys(openapi.paths["/students/{id}"].put.responses)).toEqual(expect.arrayContaining(["200", "400", "401", "403", "404", "409"]));
    expect(Object.keys(openapi.paths["/students/{id}/photo"].put.responses)).toEqual(expect.arrayContaining(["200", "400", "403", "404"]));
    expect(Object.keys(openapi.paths["/students/{id}/photo"].delete.responses)).toEqual(expect.arrayContaining(["200", "403", "404"]));
    const props = openapi.components.schemas.StudentUpdate.properties;
    expect(props).not.toHaveProperty("registeredUnitId");
    expect(props).not.toHaveProperty("guardians");
    expect(props).toHaveProperty("confirmDuplicate");
  });

  it("sem sessão 401; Professor 403; Administrador fora do alcance 404", async () => {
    expect((await send("PUT", `/students/${ADULT}`, base)).status).toBe(401);
    await login("varios@exemplo.com", "00000000-0000-0000-0000-000000000013");
    expect((await send("PUT", `/students/${ADULT}`, base)).status).toBe(403);
    expect((await photo()).status).toBe(403);
    expect((await send("DELETE", `/students/${ADULT}/photo`)).status).toBe(403);
    await login("ana@exemplo.com");
    expect((await send("PUT", "/students/00000000-0000-0000-0000-0000000009ff", base)).status).toBe(404);
  });

  it("sucesso: devolve o detalhe, o GET mostra os dados novos e o histórico ganha a ação", async () => {
    await login("ana@exemplo.com");
    const res = await send("PUT", `/students/${ADULT}`, { ...base, healthNotes: "x" });
    expect(res.status).toBe(200);
    const saved = await res.json();
    expect(saved).toMatchObject({ id: ADULT, name: "Aluno Adulto Editado", street: "Rua Nova", postalCode: "01001000", phone: "21900000009" });
    expect(saved).not.toHaveProperty("healthNotes");
    expect(await (await send("GET", `/students/${ADULT}`)).json()).toEqual(saved);
    const history = await (await send("GET", `/students/${ADULT}/history`)).json();
    expect(history.items[0].action).toBe("student.updated");
  });

  it("400 com o campo: nome curto, CPF inválido, adulto sem e-mail, menor sem responsável e emergência incompleta", async () => {
    await login("ana@exemplo.com");
    const field = async (id: string, body: unknown) => (await (await send("PUT", `/students/${id}`, body)).json()).fields[0].field;
    expect(await field(ADULT, { ...base, name: "A" })).toBe("name");
    expect(await field(ADULT, { ...base, cpf: "52998224724" })).toBe("cpf");
    expect(await field(ADULT, { ...base, email: "" })).toBe("email");
    expect(await field(ADULT, { ...base, birthDate: "2020-01-01" })).toBe("birthDate");
    expect(await field(ADULT, { ...base, emergencyName: "Mãe" })).toBe("emergencyRelationship");
    expect((await send("PUT", `/students/${MINOR}`, { ...base, birthDate: "2014-05-02", cpf: null, email: null, phone: null })).status).toBe(200);
  });

  it("409: CPF de outro aluno traz id, nome e foto; o próprio CPF passa; nome e nascimento repetidos avisam e confirmDuplicate libera", async () => {
    await login("ana@exemplo.com");
    const other = { ...base, name: "Aluno Menor Fictício", birthDate: "2014-05-02", cpf: null, email: null, phone: null };
    const taken = await send("PUT", `/students/${MINOR}`, { ...other, name: "Outro Nome", cpf: "52998224725", birthDate: "1990-03-10", email: "a@alfa.test", phone: "21900000001" });
    expect(taken.status).toBe(409);
    const error = await taken.json();
    expect(error.code).toBe("STUDENT_CPF_EXISTS");
    expect(error.fields.map((f: { field: string }) => f.field)).toEqual(["id", "name", "photoUrl"]);
    expect((await send("PUT", `/students/${ADULT}`, base)).status).toBe(200);
    const guardian = { name: "Responsável Dois", cpf: "11144477735", email: "dois@alfa.test", phone: "21900000022" };
    const created = await send("POST", "/students", { ...other, name: "Ana Lima", registeredUnitId: "00000000-0000-0000-0000-000000000021", guardians: [guardian] });
    expect(created.status).toBe(201);
    const warn = await send("PUT", `/students/${MINOR}`, { ...other, name: "ANA LIMA" });
    expect(warn.status).toBe(409);
    expect((await warn.json()).code).toBe("STUDENT_POSSIBLE_DUPLICATE");
    expect((await send("PUT", `/students/${MINOR}`, { ...other, name: "ANA LIMA", confirmDuplicate: true })).status).toBe(200);
    // Já confirmado: mudar só o telefone não avisa de novo.
    expect((await send("PUT", `/students/${MINOR}`, { ...other, name: "ANA LIMA", phone: "21900000033" })).status).toBe(200);
  });

  it("foto: troca, remove e recusa arquivo inválido; o histórico registra", async () => {
    await login("ana@exemplo.com");
    expect((await (await photo()).json()).photoUrl).toBeTruthy();
    expect((await (await send("DELETE", `/students/${ADULT}/photo`)).json()).photoUrl).toBeNull();
    const bad = await fetch(`/api/v1/students/${ADULT}/photo`, { method: "PUT", headers: { "Content-Type": "multipart/form-data; boundary=x" }, body: "--x--\r\n" });
    expect(bad.status).toBe(400);
    expect((await bad.json()).fields[0].field).toBe("file");
    const actions = (await (await send("GET", `/students/${ADULT}/history`)).json()).items.map((i: { action: string }) => i.action);
    expect(actions).toEqual(expect.arrayContaining(["student.photo_updated", "student.photo_removed"]));
  });
});
