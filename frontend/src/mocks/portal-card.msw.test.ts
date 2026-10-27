import { readFileSync } from "node:fs";
import { join } from "node:path";
import { parse } from "yaml";

const openapi = parse(readFileSync(join(process.cwd(), "../docs/openapi.yaml"), "utf8"));
const json = { "Content-Type": "application/json" };
const send = (method: string, path: string, body?: unknown) =>
  fetch(`/api/v1${path}`, {
    method,
    headers: json,
    body: body === undefined ? undefined : JSON.stringify(body),
  });
const login = (email: string) => send("POST", "/auth/login", { email, password: "segredo123" });
const MINOR = "00000000-0000-0000-0000-000000000702";
const CHILD_TWO = "00000000-0000-0000-0000-000000000703";
const keys = (o: object) => Object.keys(o).sort();
const required = (name: string): string[] => [...openapi.components.schemas[name].required].sort();

describe("Carteirinha do portal x OpenAPI (RF-POR-03, #90)", () => {
  it("o contrato declara a rota, o parâmetro e os campos, sem presença, progresso nem dados pessoais", () => {
    const op = openapi.paths["/portal/card"].get;
    expect(Object.keys(op.responses)).toEqual(expect.arrayContaining(["200", "401", "403", "404"]));
    expect(op.parameters.map((p: { name: string }) => p.name)).toEqual(["studentId"]);
    expect(required("PortalCard")).toEqual(["generatedAt", "modalities", "organization", "status", "student", "students", "timezone", "today"]);
    expect(JSON.stringify(openapi.components.schemas.PortalCard)).not.toMatch(/attendance|frequency|progress|cpf|phone|email/i);
  });

  it("sem sessão 401; gestão 403", async () => {
    expect((await send("GET", "/portal/card")).status).toBe(401);
    await login("ana@exemplo.com");
    expect((await send("GET", "/portal/card")).status).toBe(403);
  });

  it("termo pendente 403 TERM_PENDING", async () => {
    await login("termo@exemplo.com");
    const res = await send("GET", "/portal/card");
    expect(res.status).toBe(403);
    expect((await res.json()).code).toBe("TERM_PENDING");
  });

  it("responsável com dois filhos: padrão o primeiro, o segundo por studentId, formato do contrato; fora da conta 404", async () => {
    await login("familia@exemplo.com");
    const first = await (await send("GET", "/portal/card")).json();
    expect(keys(first)).toEqual(required("PortalCard"));
    expect(first).toMatchObject({ status: "ACTIVE", student: { id: MINOR } });
    expect(first.students.map((s: { id: string }) => s.id)).toEqual([MINOR, CHILD_TWO]);
    expect(keys(first.students[0])).toEqual(["id", "name", "self"]);
    expect(first.modalities).toHaveLength(2);
    expect(keys(first.modalities[0])).toEqual(["belt", "modality", "unit"]);
    expect(first.modalities[0].belt).toMatchObject({
      levelName: "Azul",
      degree: 2,
    });
    expect(first.modalities[1].belt).toBeNull();

    const second = await (await send("GET", `/portal/card?studentId=${CHILD_TWO}`)).json();
    expect(second.student.id).toBe(CHILD_TWO);
    expect((await send("GET", "/portal/card?studentId=00000000-0000-0000-0000-000000000701")).status).toBe(404);
  });

  it("aluno inativo: 200, INACTIVE e sem modalidades", async () => {
    await login("reduzido@exemplo.com");
    const res = await send("GET", "/portal/card");
    expect(res.status).toBe(200);
    expect(await res.json()).toMatchObject({
      status: "INACTIVE",
      modalities: [],
    });
  });
});
