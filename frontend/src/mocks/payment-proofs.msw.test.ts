import { readFileSync } from "node:fs";
import { join } from "node:path";
import { parse } from "yaml";

const openapi = parse(readFileSync(join(process.cwd(), "../docs/openapi.yaml"), "utf8"));
const json = { "Content-Type": "application/json" };
const send = (method: string, path: string, body?: unknown) => fetch(`/api/v1${path}`, { method, headers: json, body: body === undefined ? undefined : JSON.stringify(body) });
const login = async (email: string, linkId?: string) => {
  await send("POST", "/auth/login", { email, password: "segredo123" });
  if (linkId) await send("PUT", "/auth/session/link", { linkId });
};
const ADULT = "00000000-0000-0000-0000-000000000701";
const CENTRO = "00000000-0000-0000-0000-000000000021";
const BJJ = { unitId: CENTRO, modalityId: "00000000-0000-0000-0000-000000000103", primaryClassId: "00000000-0000-0000-0000-000000000601", planId: "00000000-0000-0000-0000-000000000602", dueDay: 5 };
const ADMIN_LINK = "00000000-0000-0000-0000-000000000011";
const STUDENT_LINK = "00000000-0000-0000-0000-000000000012";
const TEACHER_LINK = "00000000-0000-0000-0000-000000000013";
const MISSING = "00000000-0000-0000-0000-0000000000de";
const required = (name: string): string[] => [...openapi.components.schemas[name].required].sort();
// FormData e File do jsdom não passam pelo fetch do Node; o corpo multipart vai montado à mão.
const file = (type = "image/png", size = 100) => `--x
Content-Disposition: form-data; name="file"; filename="comprovante"
Content-Type: ${type}

${"a".repeat(size)}
--x--
`;
const upload = (id: string, body: string) => fetch(`/api/v1/portal/charges/${id}/proof`, { method: "POST", headers: { "Content-Type": "multipart/form-data; boundary=x" }, body });
const setPixKey = async (pixKey: string) => {
  const unit = await (await send("GET", `/units/${CENTRO}`)).json();
  await fetch(`/api/v1/units/${CENTRO}`, { method: "PUT", headers: { ...json, "If-Match": String(unit.version) }, body: JSON.stringify({ ...unit, settings: { ...unit.settings, pixKey } }) });
};

// Gestão matricula o adulto (uma mensalidade) e põe a chave Pix na unidade; depois a conta do aluno aceita o termo.
async function setup(pixKey: string | null = "pix@alfa.test") {
  await login("ana@exemplo.com");
  if (pixKey) await setPixKey(pixKey);
  const done = await send("POST", `/students/${ADULT}/enrollment/conclude`, { modalities: [BJJ], units: [{ unitId: CENTRO }] });
  const chargeId = (await done.json()).charges[0].id as string;
  await login("varios@exemplo.com", STUDENT_LINK);
  const pending = await (await send("GET", "/portal/term")).json();
  await send("POST", `/portal/students/${ADULT}/term-acceptance`, { version: pending[0].term.version });
  return chargeId;
}

describe("comprovantes em conferência x OpenAPI (RF-COB-05, #87)", () => {
  it("o contrato declara as seis rotas e o selo novo", () => {
    for (const [path, method, codes] of [
      ["/portal/charges/{id}/pix-instructions", "get", ["200", "401", "403", "404", "409", "422"]],
      ["/portal/charges/{id}/proof", "post", ["201", "401", "403", "404", "409", "422"]],
      ["/payment-proofs", "get", ["200", "401", "403"]],
      ["/payment-proofs/{id}", "get", ["200", "401", "403", "404"]],
      ["/payment-proofs/{id}/confirm", "post", ["200", "401", "403", "404", "409"]],
      ["/payment-proofs/{id}/refuse", "post", ["200", "400", "401", "403", "404", "409"]],
    ] as const) {
      expect(Object.keys(openapi.paths[path][method].responses)).toEqual(expect.arrayContaining([...codes]));
    }
    expect(openapi.components.schemas.NotificationType.enum).toContain("PAYMENT_PROOF");
  });

  it("sem sessão 401; gestão 403 no portal; portal e Professor 403 na gestão", async () => {
    expect((await send("GET", "/payment-proofs")).status).toBe(401);
    expect((await send("GET", `/portal/charges/${MISSING}/pix-instructions`)).status).toBe(401);
    await login("ana@exemplo.com");
    expect((await send("GET", `/portal/charges/${MISSING}/pix-instructions`)).status).toBe(403);
    await login("varios@exemplo.com", TEACHER_LINK);
    expect((await send("GET", "/payment-proofs")).status).toBe(403);
    await login("varios@exemplo.com", STUDENT_LINK);
    expect((await send("GET", "/payment-proofs")).status).toBe(403);
  });

  it("envia o comprovante, a cobrança fica em conferência e o sino da gestão avisa", async () => {
    const chargeId = await setup();
    const instructions = await (await send("GET", `/portal/charges/${chargeId}/pix-instructions`)).json();
    expect(Object.keys(instructions).sort()).toEqual(required("PixInstructions"));
    expect(instructions.pixKey).toBe("pix@alfa.test");

    const sent = await upload(chargeId, file());
    expect(sent.status).toBe(201);
    expect(Object.keys(await sent.json()).sort()).toEqual(required("PaymentProofSent"));
    expect((await upload(chargeId, file())).status).toBe(409);
    expect((await send("GET", `/portal/charges/${chargeId}/pix-instructions`)).status).toBe(409);

    await login("ana@exemplo.com");
    expect((await (await send("GET", `/charges/${chargeId}`)).json()).charge.status).toBe("IN_REVIEW");
    const notes = await (await send("GET", "/notifications")).json();
    expect(notes.items[0]).toMatchObject({ type: "PAYMENT_PROOF", params: { studentName: expect.any(String) } });
    const list = await (await send("GET", "/payment-proofs")).json();
    expect(list.total).toBe(1);
    expect(Object.keys(list.items[0]).sort()).toEqual(required("PaymentProof"));
  });

  it("recusa o arquivo inválido no campo `file`, a unidade sem chave e a cobrança de fora", async () => {
    const chargeId = await setup(null);
    expect((await send("GET", `/portal/charges/${chargeId}/pix-instructions`)).status).toBe(422);
    expect((await upload(chargeId, file())).status).toBe(422);
    expect((await upload(MISSING, file())).status).toBe(404);
    await login("ana@exemplo.com");
    await setPixKey("pix@alfa.test");
    await login("varios@exemplo.com", STUDENT_LINK);
    for (const bad of [file("text/plain"), file("image/png", 0), file("image/png", 5 * 1024 * 1024 + 1)]) {
      const res = await upload(chargeId, bad);
      expect(res.status).toBe(422);
      expect((await res.json()).fields[0].field).toBe("file");
    }
  });

  it("recusa pede motivo e reabre a cobrança; confirma uma vez só, com recibo", async () => {
    const chargeId = await setup();
    const proof = await (await upload(chargeId, file("application/pdf"))).json();
    await login("varios@exemplo.com", ADMIN_LINK);
    expect((await send("POST", `/payment-proofs/${MISSING}/confirm`, {})).status).toBe(404);
    expect((await send("POST", `/payment-proofs/${proof.id}/refuse`, { reason: "ab" })).status).toBe(400);
    const refused = await send("POST", `/payment-proofs/${proof.id}/refuse`, { reason: "Valor diferente" });
    expect(refused.status).toBe(200);
    expect(await refused.json()).toMatchObject({ status: "REFUSED", refusalReason: "Valor diferente" });
    expect((await send("POST", `/payment-proofs/${proof.id}/confirm`, {})).status).toBe(409);
    expect((await (await send("GET", `/charges/${chargeId}`)).json()).charge.status).not.toBe("IN_REVIEW");

    await login("varios@exemplo.com", STUDENT_LINK);
    const again = await (await upload(chargeId, file())).json();
    await login("varios@exemplo.com", ADMIN_LINK);
    const ok = await send("POST", `/payment-proofs/${again.id}/confirm`, { sendReceipt: true });
    expect(ok.status).toBe(200);
    const body = await ok.json();
    expect(Object.keys(body).sort()).toEqual(required("PaymentProofConfirmed"));
    expect(body.payment).toMatchObject({ method: "PIX_KEY", receiptEmail: "SENT" });
    expect((await send("POST", `/payment-proofs/${again.id}/confirm`, {})).status).toBe(409);
    expect((await (await send("GET", `/charges/${chargeId}`)).json()).charge.status).toMatch(/^PAID/);
    expect((await (await send("GET", "/payment-proofs")).json()).total).toBe(0);
  });
});
