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
const BJJ_CLASS = "00000000-0000-0000-0000-000000000601";
const BJJ_PLAN = "00000000-0000-0000-0000-000000000602";
const bjj = (extra: Record<string, unknown> = {}) => ({ unitId: CENTRO, modalityId: "00000000-0000-0000-0000-000000000103", primaryClassId: BJJ_CLASS, planId: BJJ_PLAN, dueDay: 5, ...extra });

// Conclui a matrícula do aluno menor e devolve a resposta.
async function conclude(modality = bjj()) {
  const done = await send("POST", `/students/${MINOR}/enrollment/conclude`, { modalities: [modality], units: [{ unitId: CENTRO }] });
  expect(done.status).toBe(201);
  return done.json();
}

describe("cobrança, pagamento e recibo x OpenAPI (RN-COB-12, RN-COB-16, #71)", () => {
  it("o contrato declara as quatro rotas", () => {
    expect(Object.keys(openapi.paths["/charges/{id}/payments"].post.responses)).toEqual(expect.arrayContaining(["201", "400", "401", "403", "404", "409", "422"]));
    expect(Object.keys(openapi.paths["/public/receipts/{code}"].get.responses)).toEqual(["200", "404"]);
    expect(Object.keys(openapi.paths["/billing/generation-preview"].get.responses)).toEqual(expect.arrayContaining(["200", "400", "401", "403"]));
    expect(Object.keys(openapi.paths["/students/{id}/modalities/{modalityRowId}/charges/advance"].post.responses)).toEqual(expect.arrayContaining(["201", "400", "403", "404", "422"]));
  });

  it("a conclusão devolve as cobranças criadas e a prévia da matrícula traz o débito em aberto", async () => {
    await login("ana@exemplo.com");
    const result = await conclude(bjj({ benefit: { type: "PERCENT", value: 25, reason: "Bolsa", decidedBy: "Mestre" } }));
    expect(result.charges).toHaveLength(1);
    expect(Object.keys(result.charges[0]).sort()).toEqual([...openapi.components.schemas.EnrollmentCharge.required, "referenceMonth"].sort());
    expect(result.charges[0]).toMatchObject({ type: "MONTHLY", unitId: CENTRO, toleranceDays: 5, amountCents: 15000 });
    expect(result.summary.openDebt).toEqual({ count: 0, totalCents: 0 });
    const again = await (await send("POST", `/students/${MINOR}/enrollment/preview`, { modalities: [bjj()], units: [{ unitId: CENTRO }] })).json();
    expect(again.openDebt).toEqual({ count: 1, totalCents: 15000 });
  });

  it("pagamento: 201 com recibo, segunda chamada 409, valor diferente 422, entrada inválida 400, desconhecida 404", async () => {
    await login("ana@exemplo.com");
    const [charge] = (await conclude()).charges;
    const path = `/charges/${charge.id}/payments`;
    expect((await send("POST", path, { method: "BOLETO" })).status).toBe(400);
    expect((await send("POST", path, { method: "CASH", paidAt: "2999-01-01T00:00:00Z" })).status).toBe(400);
    expect((await send("POST", path, { method: "CASH", observation: "x".repeat(201) })).status).toBe(400);
    expect((await send("POST", path, { method: "CASH", amountCents: 1 })).status).toBe(422);
    const paid = await send("POST", path, { method: "CASH", amountCents: 20000 });
    expect(paid.status).toBe(201);
    const payment = await paid.json();
    expect(Object.keys(payment).sort()).toEqual([...openapi.components.schemas.Payment.required].sort());
    expect(payment.receipt).toMatchObject({ number: 1 });
    const second = await send("POST", path, { method: "CASH" });
    expect(second.status).toBe(409);
    expect((await second.json()).code).toBe("CHARGE_NOT_PAYABLE");
    expect((await send("POST", "/charges/00000000-0000-0000-0000-00000000dead/payments", { method: "CASH" })).status).toBe(404);
  });

  it("pagamento: sem sessão 401 e Professor 403", async () => {
    expect((await send("POST", "/charges/x/payments", { method: "CASH" })).status).toBe(401);
    await login("varios@exemplo.com", "00000000-0000-0000-0000-000000000013");
    expect((await send("POST", "/charges/x/payments", { method: "CASH" })).status).toBe(403);
  });

  it("recibo público: 200 só com os campos do contrato; código desconhecido 404", async () => {
    await login("ana@exemplo.com");
    const [charge] = (await conclude()).charges;
    const { receipt } = await (await send("POST", `/charges/${charge.id}/payments`, { method: "PIX_KEY" })).json();
    const open = await send("GET", `/public/receipts/${receipt.code}`);
    expect(open.status).toBe(200);
    const body = await open.json();
    expect(Object.keys(body).sort()).toEqual([...openapi.components.schemas.PublicReceipt.required].sort());
    expect(body).toMatchObject({ number: 1, amountCents: 20000 });
    expect(body.reference).toMatch(/ · \d{2}\/\d{4}$/);
    expect((await send("GET", "/public/receipts/NAOEXISTE0000000")).status).toBe(404);
  });

  it("prévia da geração: formato do contrato, days fora de 1 a 60 é 400, sem sessão 401", async () => {
    expect((await send("GET", `/billing/generation-preview?unitId=${CENTRO}`)).status).toBe(401);
    await login("ana@exemplo.com");
    const ok = await send("GET", `/billing/generation-preview?unitId=${CENTRO}&days=10`);
    expect(ok.status).toBe(200);
    expect(Object.keys(await ok.json()).sort()).toEqual([...openapi.components.schemas.GenerationPreview.required].sort());
    expect((await send("GET", `/billing/generation-preview?unitId=${CENTRO}&days=0`)).status).toBe(400);
    expect((await send("GET", `/billing/generation-preview?unitId=${CENTRO}&days=61`)).status).toBe(400);
  });

  it("geração adiantada: 201 com as próximas mensalidades, count fora de 1 a 12 é 400, modalidade desconhecida 404", async () => {
    await login("ana@exemplo.com");
    await conclude();
    const rowId = (await (await send("GET", `/students/${MINOR}/enrollment`)).json()).modalities[0].id;
    const path = `/students/${MINOR}/modalities/${rowId}/charges/advance`;
    expect((await send("POST", path, { count: 0 })).status).toBe(400);
    expect((await send("POST", path, { count: 13 })).status).toBe(400);
    const made = await send("POST", path, { count: 3 });
    expect(made.status).toBe(201);
    const list = await made.json();
    expect(list).toHaveLength(3);
    expect(Object.keys(list[0]).sort()).toEqual([...openapi.components.schemas.ChargeCreated.required].sort());
    expect(new Set(list.map((c: { referenceMonth: string }) => c.referenceMonth)).size).toBe(3);
    expect((await send("POST", `/students/${MINOR}/modalities/00000000-0000-0000-0000-00000000dead/charges/advance`, { count: 1 })).status).toBe(404);
  });
});
