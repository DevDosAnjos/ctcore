import { readFileSync } from "node:fs";
import { join } from "node:path";
import { parse } from "yaml";

const openapi = parse(readFileSync(join(process.cwd(), "../docs/openapi.yaml"), "utf8"));
const json = { "Content-Type": "application/json" };
const login = async (email: string, linkId?: string) => {
  await fetch("/api/v1/auth/login", { method: "POST", headers: json, body: JSON.stringify({ email, password: "segredo123" }) });
  if (linkId) await fetch("/api/v1/auth/session/link", { method: "PUT", headers: json, body: JSON.stringify({ linkId }) });
};
const send = (method: string, path: string, body?: unknown) => fetch(`/api/v1${path}`, { method, headers: json, body: body === undefined ? undefined : JSON.stringify(body) });
const MINOR = "00000000-0000-0000-0000-000000000702";
const CENTRO = "00000000-0000-0000-0000-000000000021";
const day = (path: string) => send("GET", `/billing/daily-receipts${path}`);
const bjj = { unitId: CENTRO, modalityId: "00000000-0000-0000-0000-000000000103", primaryClassId: "00000000-0000-0000-0000-000000000601", planId: "00000000-0000-0000-0000-000000000602", dueDay: 5 };

describe("recebimentos do dia x OpenAPI (RF-COB-08, #77)", () => {
  it("o contrato declara a rota e os campos", () => {
    const op = openapi.paths["/billing/daily-receipts"].get;
    expect(Object.keys(op.responses)).toEqual(expect.arrayContaining(["200", "400", "401", "403"]));
    expect(op.parameters.map((p: { name: string }) => p.name)).toEqual(["unitId", "date"]);
  });

  it("200: o pagamento do balcão entra no total, por meio e por lançador, com os campos do contrato", async () => {
    await login("ana@exemplo.com");
    const done = await send("POST", `/students/${MINOR}/enrollment/conclude`, { modalities: [bjj], units: [{ unitId: CENTRO }] });
    const [charge] = (await done.json()).charges;
    expect((await send("POST", `/charges/${charge.id}/payments`, { method: "PIX_KEY" })).status).toBe(201);

    const body = await (await day(`?unitId=${CENTRO}`)).json();
    expect(Object.keys(body).sort()).toEqual([...openapi.components.schemas.DailyReceipts.required].sort());
    expect(body).toMatchObject({ totalCents: 20000, count: 1, byMethod: [{ method: "PIX_KEY", count: 1, totalCents: 20000 }] });
    expect(body.byRegistrar).toHaveLength(1);
    expect(body.byRegistrar[0]).toMatchObject({ count: 1, totalCents: 20000 });
    expect(body.byRegistrar[0].registeredBy.id).toBeTruthy();
    const [p] = body.payments;
    expect(Object.keys(p).sort()).toEqual([...openapi.components.schemas.DailyReceipts.properties.payments.items.required].sort());
    expect(p).toMatchObject({ method: "PIX_KEY", amountCents: 20000, receiptNumber: 1 });
    expect(p.reference).toMatch(/ · \d{2}\/\d{4}$/);

    const past = await (await day(`?unitId=${CENTRO}&date=2000-01-01`)).json();
    expect(past).toMatchObject({ date: "2000-01-01", totalCents: 0, count: 0, byMethod: [], byRegistrar: [], payments: [] });
  });

  it("400: sem unitId, data malformada ou futura; 401 sem sessão; 403 para Professor", async () => {
    expect((await day(`?unitId=${CENTRO}`)).status).toBe(401);
    await login("ana@exemplo.com");
    expect((await day("")).status).toBe(400);
    expect((await day(`?unitId=${CENTRO}&date=31/12`)).status).toBe(400);
    const future = await day(`?unitId=${CENTRO}&date=2999-01-01`);
    expect(future.status).toBe(400);
    expect((await future.json()).fields[0].field).toBe("date");
    await login("varios@exemplo.com", "00000000-0000-0000-0000-000000000013");
    expect((await day(`?unitId=${CENTRO}`)).status).toBe(403);
  });
});
