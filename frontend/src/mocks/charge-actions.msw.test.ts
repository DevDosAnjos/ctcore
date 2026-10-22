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
const ADMIN_LINK = "00000000-0000-0000-0000-000000000011";
const TEACHER_LINK = "00000000-0000-0000-0000-000000000013";
const MISSING = "00000000-0000-0000-0000-0000000000de";
const bjj = { unitId: CENTRO, modalityId: "00000000-0000-0000-0000-000000000103", primaryClassId: "00000000-0000-0000-0000-000000000601", planId: "00000000-0000-0000-0000-000000000602", dueDay: 5 };

// Matricula o aluno menor (uma mensalidade de R$ 200,00) e gera mais duas pela geração adiantada; devolve o id da linha e as cobranças.
async function charges() {
  const done = await send("POST", `/students/${MINOR}/enrollment/conclude`, { modalities: [bjj], units: [{ unitId: CENTRO }] });
  expect(done.status).toBe(201);
  const body = await done.json();
  const rowId = body.enrollment.modalities[0].id as string;
  const more = await (await send("POST", `/students/${MINOR}/modalities/${rowId}/charges/advance`, { count: 2 })).json();
  return { rowId, ids: [...body.charges, ...more].map((c: { id: string }) => c.id) as string[] };
}
const detail = async (id: string) => (await send("GET", `/charges/${id}`)).json();
const reneg = (chargeIds: string[], over: Record<string, unknown> = {}) => send("POST", "/charges/renegotiate", { chargeIds, firstDueOn: "2099-01-31", installments: 3, reason: "Acordo", ...over });

describe("painel, cancelar e renegociar x OpenAPI (RF-COB-02, #73, CA21)", () => {
  it("o contrato declara as três rotas com 400/401/403/404/409, studentId e a decisão do encerramento", () => {
    expect(Object.keys(openapi.paths["/charges/{id}"].get.responses)).toEqual(expect.arrayContaining(["200", "401", "403", "404"]));
    expect(Object.keys(openapi.paths["/charges/{id}/cancel"].post.responses)).toEqual(expect.arrayContaining(["200", "400", "401", "403", "404", "409"]));
    expect(Object.keys(openapi.paths["/charges/renegotiate"].post.responses)).toEqual(expect.arrayContaining(["201", "400", "401", "403", "404", "409"]));
    expect(openapi.paths["/charges"].get.parameters.map((p: { name?: string }) => p.name)).toContain("studentId");
    expect(openapi.components.schemas.ModalityEndInput.properties.charges.items.required).toEqual(["chargeId", "action"]);
  });

  it("sem sessão 401; Professor 403; cobrança que não existe 404; os corpos inválidos dão 400", async () => {
    expect((await send("GET", `/charges/${MISSING}`)).status).toBe(401);
    await login("varios@exemplo.com", TEACHER_LINK);
    for (const [m, p] of [["GET", `/charges/${MISSING}`], ["POST", `/charges/${MISSING}/cancel`], ["POST", "/charges/renegotiate"]]) expect((await send(m, p, m === "GET" ? undefined : { reason: "x" })).status).toBe(403);
    await login("ana@exemplo.com");
    expect((await send("GET", `/charges/${MISSING}`)).status).toBe(404);
    expect((await send("POST", `/charges/${MISSING}/cancel`, { reason: "x" })).status).toBe(404);
    expect((await reneg([MISSING])).status).toBe(404);
    await login("varios@exemplo.com", ADMIN_LINK);
    expect((await send("GET", `/charges/${MISSING}`)).status).toBe(404);
  });

  it("devolve o painel no formato do contrato e cancela com motivo; repetir é 200 e paga/renegociada é 409", async () => {
    await login("ana@exemplo.com");
    const { ids } = await charges();
    const panel = await detail(ids[0]);
    expect(Object.keys(panel).sort()).toEqual([...openapi.components.schemas.ChargeDetail.required].sort());
    expect(Object.keys(panel.charge).sort()).toEqual([...openapi.components.schemas.ChargeRow.required].sort());
    expect(panel).toMatchObject({ payments: [], reminders: [], origin: null, replacedBy: [], actions: { cancel: true, renegotiate: true, registerPayment: false, sendReminder: false, refund: false } });
    for (const bad of [undefined, "   ", "x".repeat(201)]) {
      const res = await send("POST", `/charges/${ids[0]}/cancel`, { reason: bad });
      expect(res.status).toBe(400);
      expect((await res.json()).fields[0].field).toBe("reason");
    }
    const ok = await send("POST", `/charges/${ids[0]}/cancel`, { reason: "x".repeat(200) });
    expect(ok.status).toBe(200);
    expect(await ok.json()).toMatchObject({ charge: { status: "CANCELED" }, reason: "x".repeat(200), actions: { cancel: false, renegotiate: false } });
    expect((await send("POST", `/charges/${ids[0]}/cancel`, { reason: "de novo" })).status).toBe(200);
    expect((await detail(ids[0])).reason).toBe("x".repeat(200));
    expect((await send("POST", `/charges/${ids[1]}/payments`, { method: "CASH" })).status).toBe(201);
    expect((await send("POST", `/charges/${ids[1]}/cancel`, { reason: "x" })).status).toBe(409);
    expect((await reneg([ids[1]])).status).toBe(409);
    const paid = await detail(ids[1]);
    expect(paid.payments[0]).toMatchObject({ method: "CASH", origin: "MANAGEMENT", status: "CONFIRMED", receipt: { number: 1 } });
    const list = await (await send("GET", "/charges?status=CANCELED")).json();
    expect(list.items.map((r: { id: string }) => r.id)).toEqual([ids[0]]);
  });

  it("renegocia em parcelas mensais, com o resto dos centavos na primeira, e mostra origem e substituídas", async () => {
    await login("ana@exemplo.com");
    const { ids } = await charges();
    const res = await reneg([ids[0]], { amountCents: 10000 });
    expect(res.status).toBe(201);
    const made = await res.json();
    expect(made.map((c: { amountCents: number }) => c.amountCents)).toEqual([3334, 3333, 3333]);
    expect(made.map((c: { dueOn: string }) => c.dueOn)).toEqual(["2099-01-31", "2099-02-28", "2099-03-31"]);
    expect(made.every((c: { type: string; status: string }) => c.type === "INSTALLMENT" && c.status === "PENDING")).toBe(true);
    expect(Object.keys(made[0]).sort()).toEqual([...openapi.components.schemas.ChargeRow.required].sort());
    const original = await detail(ids[0]);
    expect(original).toMatchObject({ charge: { status: "RENEGOTIATED" }, reason: "Acordo", actions: { cancel: false } });
    expect(original.replacedBy.map((r: { id: string }) => r.id)).toEqual(made.map((c: { id: string }) => c.id));
    expect((await detail(made[1].id)).origin).toMatchObject({ id: ids[0], status: "RENEGOTIATED" });
    expect((await send("POST", `/charges/${ids[0]}/cancel`, { reason: "x" })).status).toBe(409);
    expect((await reneg([ids[0]])).status).toBe(409);
  });

  it("várias cobranças somam; o total só muda com uma; as validações dão 400 com o campo e nada muda", async () => {
    await login("ana@exemplo.com");
    const { ids } = await charges();
    const field = async (res: Response) => {
      expect(res.status).toBe(400);
      return (await res.json()).fields[0].field;
    };
    expect(await field(await reneg([ids[0], ids[1]], { amountCents: 14000 }))).toBe("amountCents");
    expect(await field(await reneg([ids[0]], { installments: 0 }))).toBe("installments");
    expect(await field(await reneg([ids[0]], { installments: 7 }))).toBe("installments");
    expect(await field(await reneg([ids[0]], { firstDueOn: "2020-01-01" }))).toBe("firstDueOn");
    expect(await field(await reneg([ids[0]], { firstDueOn: "2099-02-30" }))).toBe("firstDueOn");
    expect(await field(await reneg([ids[0]], { amountCents: 0 }))).toBe("amountCents");
    expect(await field(await reneg([ids[0]], { amountCents: 2 }))).toBe("amountCents");
    expect(await field(await reneg([]))).toBe("chargeIds");
    expect(await field(await reneg([ids[0], ids[0]]))).toBe("chargeIds");
    expect(await field(await reneg([ids[0]], { reason: " " }))).toBe("reason");
    expect((await detail(ids[0])).charge.status).not.toBe("RENEGOTIATED");
    const sum = await reneg([ids[0], ids[1]], { installments: 2 });
    expect(sum.status).toBe(201);
    expect((await sum.json()).reduce((t: number, c: { amountCents: number }) => t + c.amountCents, 0)).toBe(40000);
    const single = await (await reneg([ids[2]], { amountCents: 8000, installments: 1 })).json();
    expect(single).toHaveLength(1);
    expect(single[0]).toMatchObject({ amountCents: 8000, dueOn: "2099-01-31" });
  });

  it("GET /charges aceita studentId (404 para aluno desconhecido) e o encerramento lista e decide as cobranças em aberto", async () => {
    await login("ana@exemplo.com");
    const { rowId, ids } = await charges();
    expect((await send("GET", `/charges?studentId=${MISSING}`)).status).toBe(404);
    expect((await send("GET", "/charges?studentId=x")).status).toBe(400);
    const byStudent = await (await send("GET", `/charges?studentId=${MINOR}`)).json();
    expect(byStudent.total).toBe(ids.length);
    const preview = await (await send("GET", `/students/${MINOR}/modalities/${rowId}/end-preview`)).json();
    expect(preview.openCharges.map((c: { id: string }) => c.id).sort()).toEqual([...ids].sort());
    expect(Object.keys(preview.openCharges[0]).sort()).toEqual([...openapi.components.schemas.EndPreview.properties.openCharges.items.required].sort());
    const end = (charges: unknown[]) => send("POST", `/students/${MINOR}/modalities/${rowId}/end`, { reason: "FINANCIAL", charges });
    expect((await end([{ chargeId: ids[0], action: "CANCEL" }])).status).toBe(400);
    expect((await end([{ chargeId: MISSING, action: "KEEP" }])).status).toBe(400);
    expect((await end([{ chargeId: ids[0], action: "X" }])).status).toBe(400);
    expect((await end([{ chargeId: ids[0], action: "KEEP" }, { chargeId: ids[0], action: "KEEP" }])).status).toBe(400);
    expect((await detail(ids[0])).charge.status).not.toBe("CANCELED");
    expect((await end([{ chargeId: ids[0], action: "CANCEL", reason: "Mudou de cidade" }, { chargeId: ids[1], action: "KEEP" }])).status).toBe(200);
    expect((await detail(ids[0])).charge.status).toBe("CANCELED");
    expect((await detail(ids[1])).charge.status).not.toBe("CANCELED");
    expect((await detail(ids[2])).charge.status).not.toBe("CANCELED");
  });
});
