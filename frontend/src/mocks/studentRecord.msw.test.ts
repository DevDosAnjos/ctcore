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
const MINOR = "00000000-0000-0000-0000-000000000702";
const GUARDIAN = "00000000-0000-0000-0000-000000000801";

describe("ficha e histórico do aluno x OpenAPI (RF-ALU-03)", () => {
  it("o contrato declara as duas rotas, os esquemas e a chave do Professor", () => {
    expect(Object.keys(openapi.paths["/students/{id}/record"].get.responses)).toEqual(expect.arrayContaining(["200", "401", "403", "404"]));
    expect(Object.keys(openapi.paths["/students/{id}/history"].get.responses)).toEqual(expect.arrayContaining(["200", "400", "401", "403", "404"]));
    expect(openapi.components.schemas.TeacherPermissions.required).toContain("studentRecord");
    expect(openapi.components.schemas.Permission.enum).toContain("STUDENT_RECORD");
  });

  it("sem sessão 401; Professor 403; Administrador alcança só a unidade dele (404 fora)", async () => {
    expect((await send("GET", `/students/${MINOR}/record`)).status).toBe(401);
    expect((await send("GET", `/students/${MINOR}/history`)).status).toBe(401);
    await login("varios@exemplo.com", "00000000-0000-0000-0000-000000000013");
    expect((await send("GET", `/students/${MINOR}/record`)).status).toBe(403);
    expect((await send("GET", `/students/${MINOR}/history`)).status).toBe(403);
    await login("varios@exemplo.com", "00000000-0000-0000-0000-000000000011");
    expect((await send("GET", `/students/${MINOR}/record`)).status).toBe(200);
    expect((await send("GET", `/students/00000000-0000-0000-0000-0000000009ff/record`)).status).toBe(404);
  });

  it("a ficha tem os campos do esquema e os selos da lista", async () => {
    await login("ana@exemplo.com");
    const record = await (await send("GET", `/students/${MINOR}/record`)).json();
    const schema = openapi.components.schemas.StudentRecord;
    for (const key of schema.required) expect(record).toHaveProperty(key);
    expect(Object.keys(record).every((k) => k in schema.properties)).toBe(true);
    expect(record).toMatchObject({ id: MINOR, minor: true, guardians: [{ guardianId: GUARDIAN, financial: true, access: "PENDING" }] });
    const listed = (await (await send("GET", "/students?q=Menor")).json()).items.find((i: { id: string }) => i.id === MINOR);
    expect(record.seals).toEqual(listed.seals);
    expect(record.age).toBe(listed.age);
  });

  it("o histórico começa pelo cadastro, acrescenta as ações de responsável (mais recente primeiro) e valida a paginação", async () => {
    await login("ana@exemplo.com");
    expect((await send("POST", `/students/${MINOR}/guardians/${GUARDIAN}/invite`)).status).toBe(204);
    const history = await (await send("GET", `/students/${MINOR}/history`)).json();
    expect(history).toMatchObject({ page: 1, size: 25, total: 2 });
    expect(history.items.map((i: { action: string }) => i.action)).toEqual(["student.guardian_invite_resent", "student.created"]);
    const entry = openapi.components.schemas.StudentHistoryEntry;
    for (const key of entry.required) expect(history.items[0]).toHaveProperty(key);
    expect(history.items[0]).not.toHaveProperty("before");
    expect((await send("GET", `/students/${MINOR}/history?size=7`)).status).toBe(400);
    expect((await send("GET", `/students/${MINOR}/history?page=0`)).status).toBe(400);
  });

  it("a chave studentRecord vem desligada por padrão na configuração da unidade", async () => {
    await login("ana@exemplo.com");
    const unit = await (await send("GET", "/units/00000000-0000-0000-0000-000000000021")).json();
    expect(unit.settings.teacherPermissions).toMatchObject({ studentRecord: false });
  });
});
