import { readFileSync } from "node:fs";
import { join } from "node:path";
import { parse } from "yaml";

const openapi = parse(readFileSync(join(process.cwd(), "../docs/openapi.yaml"), "utf8"));
const json = { "Content-Type": "application/json" };
const login = (email: string) => fetch("/api/v1/auth/login", { method: "POST", headers: json, body: JSON.stringify({ email, password: "segredo123" }) });
const send = (method: string, path: string, body?: unknown) => fetch(`/api/v1${path}`, { method, headers: json, body: body === undefined ? undefined : JSON.stringify(body) });
const ADULT = "00000000-0000-0000-0000-000000000701";
const MINOR = "00000000-0000-0000-0000-000000000702";
const CENTRO = "00000000-0000-0000-0000-000000000021";
const day = (plus: number) => new Date(Date.now() + plus * 86_400_000).toISOString().slice(0, 10);
const enroll = async (student: string) => {
  const done = await send("POST", `/students/${student}/enrollment/conclude`, {
    modalities: [{ unitId: CENTRO, modalityId: "00000000-0000-0000-0000-000000000103", primaryClassId: "00000000-0000-0000-0000-000000000601", planId: "00000000-0000-0000-0000-000000000602", dueDay: 5 }],
    units: [{ unitId: CENTRO }],
  });
  expect(done.status).toBe(201);
};
const body = (over: Record<string, unknown> = {}) => ({ requestId: crypto.randomUUID(), unitId: CENTRO, description: "Kimono A2", amountCents: 25000, dueOn: day(7), ...over });
const post = (student: string, b: unknown) => send("POST", `/students/${student}/charges`, b);

describe("cobrança avulsa x OpenAPI (RN-COB-11, #76)", () => {
  it("o contrato declara a rota e os códigos", () => {
    const op = openapi.paths["/students/{id}/charges"].post;
    expect(Object.keys(op.responses)).toEqual(expect.arrayContaining(["200", "201", "400", "401", "403", "404", "409", "422"]));
    expect(openapi.components.schemas.OneOffInput.required).toEqual(["unitId", "description", "amountCents", "dueOn"]);
  });

  it("sem sessão é 401", async () => {
    expect((await post(ADULT, body())).status).toBe(401);
  });

  it("201 com a linha do contrato e a avulsa entra na lista com tipo ONE_OFF", async () => {
    await login("ana@exemplo.com");
    await enroll(ADULT);
    const created = await post(ADULT, body({ description: "  Kimono A2  " }));
    expect(created.status).toBe(201);
    const row = await created.json();
    expect(Object.keys(row).sort()).toEqual([...openapi.components.schemas.ChargeRow.required].sort());
    expect(row).toMatchObject({ type: "ONE_OFF", status: "PENDING", amountCents: 25000, description: "Kimono A2", referenceMonth: null, modality: null });
    const list = await (await send("GET", "/charges?type=ONE_OFF")).json();
    expect(list.items.map((c: { id: string }) => c.id)).toEqual([row.id]);
  });

  it("repetir o mesmo requestId devolve 200 sem duplicar; dados diferentes são 409", async () => {
    await login("ana@exemplo.com");
    await enroll(ADULT);
    const b = body();
    const first = await (await post(ADULT, b)).json();
    const again = await post(ADULT, b);
    expect(again.status).toBe(200);
    expect((await again.json()).id).toBe(first.id);
    expect((await post(ADULT, { ...b, amountCents: 25001 })).status).toBe(409);
    expect((await (await send("GET", "/charges?type=ONE_OFF")).json()).items).toHaveLength(1);
  });

  it("400 com o campo para unitId, descrição, valor e vencimento inválidos", async () => {
    await login("ana@exemplo.com");
    await enroll(ADULT);
    const bad: [string, Record<string, unknown>][] = [
      ["unitId", { unitId: undefined }],
      ["description", { description: "   " }],
      ["description", { description: "x".repeat(81) }],
      ["amountCents", { amountCents: 0 }],
      ["amountCents", { amountCents: 10_000_001 }],
      ["dueOn", { dueOn: day(-2) }],
      ["dueOn", { dueOn: "31/12" }],
      ["dueOn", { dueOn: undefined }],
    ];
    for (const [field, over] of bad) {
      const res = await post(ADULT, body(over));
      expect(res.status).toBe(400);
      expect((await res.json()).fields[0].field).toBe(field);
    }
    expect((await (await send("GET", "/charges?type=ONE_OFF")).json()).items).toHaveLength(0);
  });

  it("404 para aluno ou unidade inexistente; 422 sem matrícula na unidade; menor com responsável financeiro é aceito", async () => {
    await login("ana@exemplo.com");
    expect((await post("00000000-0000-0000-0000-00000000dead", body())).status).toBe(404);
    expect((await post(ADULT, body({ unitId: "00000000-0000-0000-0000-00000000dead" }))).status).toBe(404);
    expect((await post(ADULT, body())).status).toBe(422);
    await enroll(MINOR);
    expect((await post(MINOR, body())).status).toBe(201);
  });
});
