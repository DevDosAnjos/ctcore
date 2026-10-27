import { readFileSync } from "node:fs";
import { join } from "node:path";
import { parse } from "yaml";

const openapi = parse(readFileSync(join(process.cwd(), "../docs/openapi.yaml"), "utf8"));
const json = { "Content-Type": "application/json" };
const send = (method: string, path: string, body?: unknown) =>
  fetch(`/api/v1${path}`, { method, headers: json, body: body === undefined ? undefined : JSON.stringify(body) });
const login = (email: string) => send("POST", "/auth/login", { email, password: "segredo123" });
const MINOR = "00000000-0000-0000-0000-000000000702";
const CHILD_TWO = "00000000-0000-0000-0000-000000000703";
const keys = (o: object) => Object.keys(o).sort();
const required = (name: string): string[] => [...openapi.components.schemas[name].required].sort();
const lessonKeys = [...openapi.components.schemas.PortalSchedule.properties.lessons.items.required].sort();

describe("Agenda do portal x OpenAPI (RF-POR-04, #91)", () => {
  it("o contrato declara a rota, os parâmetros e os campos, sem professor, presença nem cobrança", () => {
    const op = openapi.paths["/portal/schedule"].get;
    expect(Object.keys(op.responses)).toEqual(expect.arrayContaining(["200", "400", "401", "403", "404"]));
    expect(op.parameters.map((p: { name: string }) => p.name)).toEqual(["studentId", "week", "scope"]);
    expect(JSON.stringify(openapi.components.schemas.PortalSchedule)).not.toMatch(/teacherId|teacherName|attendance|frequency|progress|waiting|amount/i);
  });

  it("sem sessão 401; gestão 403; termo pendente 403 TERM_PENDING", async () => {
    expect((await send("GET", "/portal/schedule")).status).toBe(401);
    await login("ana@exemplo.com");
    expect((await send("GET", "/portal/schedule")).status).toBe(403);
    await login("termo@exemplo.com");
    const res = await send("GET", "/portal/schedule");
    expect(res.status).toBe(403);
    expect((await res.json()).code).toBe("TERM_PENDING");
  });

  it("responsável com dois filhos: alterada, cancelada, avulsa e dia sem aula no formato do contrato; segundo filho por studentId; fora da conta 404", async () => {
    await login("familia@exemplo.com");
    const first = await (await send("GET", "/portal/schedule?week=2026-10-14")).json();
    expect(keys(first)).toEqual(required("PortalSchedule"));
    expect(first).toMatchObject({ studentId: MINOR, scope: "ALL", weekStart: "2026-10-12", weekEnd: "2026-10-18" });
    expect(first.students.map((s: { id: string }) => s.id)).toEqual([MINOR, CHILD_TWO]);
    expect(first.lessons.map((l: { kind: string }) => l.kind)).toEqual(["MAIN", "OTHER", "ADHOC"]);
    first.lessons.forEach((l: object) => expect(keys(l)).toEqual(lessonKeys));
    expect(first.lessons[0]).toMatchObject({ changed: true, originalStartsAt: "18:00", past: true });
    expect(first.lessons[1]).toMatchObject({ status: "CANCELLED", cancelReasonCode: "HOLIDAY" });
    expect(first.noClassDays).toEqual([expect.objectContaining({ date: "2026-10-14", reason: "Reforma do tatame" })]);

    const main = await (await send("GET", "/portal/schedule?week=2026-10-14&scope=MAIN")).json();
    expect(main.lessons.map((l: { kind: string }) => l.kind)).toEqual(["MAIN"]);
    const second = await (await send("GET", `/portal/schedule?studentId=${CHILD_TWO}`)).json();
    expect(second.studentId).toBe(CHILD_TWO);
    expect(second.lessons).toHaveLength(1);
    expect((await send("GET", "/portal/schedule?studentId=00000000-0000-0000-0000-000000000701")).status).toBe(404);
  });

  it("scope ou week inválidos 400; aluno inativo 200 com reduced e listas vazias", async () => {
    await login("familia@exemplo.com");
    expect((await send("GET", "/portal/schedule?scope=X")).status).toBe(400);
    expect((await send("GET", "/portal/schedule?week=ontem")).status).toBe(400);
    await login("reduzido@exemplo.com");
    const res = await send("GET", "/portal/schedule");
    expect(res.status).toBe(200);
    expect(await res.json()).toMatchObject({ reduced: true, lessons: [], noClassDays: [] });
  });
});
