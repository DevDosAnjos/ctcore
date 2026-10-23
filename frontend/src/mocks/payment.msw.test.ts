import { readFileSync } from "node:fs";
import { join } from "node:path";
import { parse } from "yaml";

const openapi = parse(readFileSync(join(process.cwd(), "../docs/openapi.yaml"), "utf8"));
const json = { "Content-Type": "application/json" };
const send = (method: string, path: string, body?: unknown) => fetch(`/api/v1${path}`, { method, headers: json, body: body === undefined ? undefined : JSON.stringify(body) });
const MINOR = "00000000-0000-0000-0000-000000000702";
const CENTRO = "00000000-0000-0000-0000-000000000021";
const bjj = { unitId: CENTRO, modalityId: "00000000-0000-0000-0000-000000000103", primaryClassId: "00000000-0000-0000-0000-000000000601", planId: "00000000-0000-0000-0000-000000000602", dueDay: 5 };

describe("pagamento no balcão x OpenAPI (RF-COB-04, #75, CA17)", () => {
  it("o contrato declara a rota do PDF e os campos novos", () => {
    expect(openapi.paths["/public/receipts/{code}/pdf"].get.responses["200"].content["application/pdf"]).toBeDefined();
    expect(Object.keys(openapi.paths["/receipts/{code}/pdf"].get.responses)).toEqual(["200", "401", "403", "404"]);
    expect(Object.keys(openapi.components.schemas.PaymentInput.properties)).toEqual(expect.arrayContaining(["paidAt", "observation", "amountCents", "sendReceipt"]));
    expect(openapi.components.schemas.Payment.properties.receiptEmail.enum).toEqual(["SENT", "NO_ADDRESS", "NOT_REQUESTED", "FAILED"]);
  });

  it("201 com receiptEmail, 400, 422, 409 e o PDF pelo código", async () => {
    await send("POST", "/auth/login", { email: "ana@exemplo.com", password: "segredo123" });
    const done = await send("POST", `/students/${MINOR}/enrollment/conclude`, { modalities: [bjj], units: [{ unitId: CENTRO }] });
    const [charge] = (await done.json()).charges as { id: string; amountCents: number }[];
    const pay = (body: object) => send("POST", `/charges/${charge.id}/payments`, body);
    expect((await send("GET", `/charges/${charge.id}`).then((r) => r.json())).actions.registerPayment).toBe(true);
    expect((await pay({ method: "BITCOIN" })).status).toBe(400);
    expect((await pay({ method: "CASH", paidAt: "2999-01-01T00:00:00Z" })).status).toBe(400);
    expect((await pay({ method: "CASH", observation: "x".repeat(201) })).status).toBe(400);
    expect((await pay({ method: "CASH", amountCents: charge.amountCents + 1 })).status).toBe(422);
    const ok = await pay({ method: "PIX_KEY", amountCents: charge.amountCents, sendReceipt: true });
    expect(ok.status).toBe(201);
    const body = await ok.json();
    expect(body.receiptEmail).toBe("SENT");
    expect((await pay({ method: "CASH" })).status).toBe(409);
    const pdf = await send("GET", `/public/receipts/${body.receipt.code}/pdf`);
    expect(pdf.status).toBe(200);
    expect(pdf.headers.get("Content-Type")).toBe("application/pdf");
    expect((await send("GET", "/public/receipts/NAOEXISTE/pdf")).status).toBe(404);
    const staff = await send("GET", `/receipts/${body.receipt.code}/pdf`);
    expect(staff.status).toBe(200);
    expect(staff.headers.get("Content-Type")).toBe("application/pdf");
    expect((await send("GET", "/receipts/NAOEXISTE/pdf")).status).toBe(404);
    const detail = await send("GET", `/charges/${charge.id}`).then((r) => r.json());
    expect(detail.actions.registerPayment).toBe(false);
    expect(detail.payments[0].method).toBe("PIX_KEY");
  });
});
