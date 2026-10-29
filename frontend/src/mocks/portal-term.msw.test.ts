import { readFileSync } from "node:fs";
import { join } from "node:path";
import { parse } from "yaml";

const openapi = parse(readFileSync(join(process.cwd(), "../docs/openapi.yaml"), "utf8"));
const json = { "Content-Type": "application/json" };
const send = (method: string, path: string, body?: unknown) =>
  fetch(`/api/v1${path}`, { method, headers: json, body: body === undefined ? undefined : JSON.stringify(body) });
const login = (email: string) => send("POST", "/auth/login", { email, password: "segredo123" });
const ADULT = "00000000-0000-0000-0000-000000000701";
const MINOR = "00000000-0000-0000-0000-000000000702";

describe("aceite do termo no portal x OpenAPI (#85)", () => {
  it("o contrato declara as rotas e o selo novo", () => {
    expect(Object.keys(openapi.paths["/portal/term"].get.responses)).toEqual(expect.arrayContaining(["200", "401", "403"]));
    expect(Object.keys(openapi.paths["/portal/students/{id}/term-acceptance"].post.responses)).toEqual(expect.arrayContaining(["200", "201", "400", "404", "409"]));
    expect(Object.keys(openapi.paths["/portal/students/{id}/image-use"].put.responses)).toEqual(expect.arrayContaining(["200", "400", "404", "409"]));
    expect(Object.keys(openapi.paths["/portal/modality-confirmations/{modalityRowId}"].post.responses)).toEqual(expect.arrayContaining(["200", "201", "403", "404"]));
    expect(openapi.components.schemas.StudentSeal.enum).toContain("TERM_PENDING");
    expect(openapi.components.schemas.Error.properties.code.enum).toContain("TERM_PENDING");
  });

  it("sem sessão 401; gestão 403 em todas as rotas", async () => {
    expect((await send("GET", "/portal/term")).status).toBe(401);
    await login("ana@exemplo.com");
    for (const [m, p] of [["GET", "/portal/term"], ["POST", `/portal/students/${ADULT}/term-acceptance`], ["GET", `/portal/students/${ADULT}/image-use`], ["PUT", `/portal/students/${ADULT}/image-use`], ["GET", "/portal/modality-confirmations"]]) {
      expect((await send(m, p, m === "GET" ? undefined : {})).status).toBe(403);
    }
  });

  it("termo pendente trava os cartões até o aceite; versão errada 409, sem versão 400, aluno de outra conta 404; repetir 200", async () => {
    await login("termo@exemplo.com");
    const students = await send("GET", "/portal/students");
    expect(students.status).toBe(403);
    expect((await students.json()).code).toBe("TERM_PENDING");
    const pending = await (await send("GET", "/portal/term")).json();
    expect(pending).toHaveLength(1);
    expect(pending[0]).toMatchObject({ id: ADULT, imageUse: { granted: false } });
    expect(pending[0].term.body).toBeTruthy();

    expect((await send("POST", `/portal/students/${ADULT}/term-acceptance`, {})).status).toBe(400);
    expect((await send("POST", `/portal/students/${ADULT}/term-acceptance`, { version: 9 })).status).toBe(409);
    expect((await send("POST", `/portal/students/${MINOR}/term-acceptance`, { version: 0 })).status).toBe(404);
    expect((await send("GET", "/portal/modality-confirmations")).status).toBe(403);

    const accepted = await send("POST", `/portal/students/${ADULT}/term-acceptance`, { version: pending[0].term.version, imageUse: true });
    expect(accepted.status).toBe(201);
    expect(await accepted.json()).toMatchObject({ studentId: ADULT, imageUse: true });
    expect((await send("POST", `/portal/students/${ADULT}/term-acceptance`, { version: pending[0].term.version })).status).toBe(200);
    expect(await (await send("GET", "/portal/term")).json()).toEqual([]);
    expect((await send("GET", "/portal/students")).status).toBe(200);
    expect(await (await send("GET", "/portal/modality-confirmations")).json()).toEqual([]);
  });

  it("uso de imagem: revoga e concede de novo", async () => {
    await login("termo@exemplo.com");
    expect((await (await send("GET", `/portal/students/${ADULT}/image-use`)).json()).granted).toBe(false);
    expect((await send("PUT", `/portal/students/${ADULT}/image-use`, {})).status).toBe(400);
    expect((await send("PUT", `/portal/students/${ADULT}/image-use`, { granted: true, version: 9 })).status).toBe(409);
    expect((await (await send("PUT", `/portal/students/${ADULT}/image-use`, { granted: true })).json()).granted).toBe(true);
    expect((await (await send("PUT", `/portal/students/${ADULT}/image-use`, { granted: false })).json()).granted).toBe(false);
  });

  it("selo Termo pendente na lista da gestão para aluno com matrícula, e o filtro", async () => {
    await login("ana@exemplo.com");
    const res = await send("GET", "/students?seal=TERM_PENDING");
    expect(res.status).toBe(200);
    expect((await res.json()).items).toEqual([]);
  });
});
