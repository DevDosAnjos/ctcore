import { readFileSync } from "node:fs";
import { join } from "node:path";
import { parse } from "yaml";

const openapi = parse(readFileSync(join(process.cwd(), "../docs/openapi.yaml"), "utf8"));
const json = { "Content-Type": "application/json" };
const send = (method: string, path: string, body?: unknown) => fetch(`/api/v1${path}`, { method, headers: json, body: body === undefined ? undefined : JSON.stringify(body) });
const login = (email: string) => send("POST", "/auth/login", { email, password: "segredo123" });
const MINOR = "00000000-0000-0000-0000-000000000702";
const CHILD_TWO = "00000000-0000-0000-0000-000000000703";
const SECOND = "00000000-0000-0000-0000-0000000007c1";
const THIRD = "00000000-0000-0000-0000-0000000007c3";
const keys = (o: object) => Object.keys(o).sort();
const required = (name: string): string[] => [...openapi.components.schemas[name].required].sort();
const year = new Date().getFullYear();
// FormData e File do jsdom não passam pelo fetch do Node; o corpo multipart vai montado à mão.
const multipart = (ids: string[], type = "image/png", size = 100) =>
  [
    ...ids.map((id) => `--x\nContent-Disposition: form-data; name="chargeIds"\n\n${id}\n`),
    `--x\nContent-Disposition: form-data; name="file"; filename="comprovante"\nContent-Type: ${type}\n\n${"a".repeat(size)}\n--x--\n`,
  ].join("");
const upload = (ids: string[], body = multipart(ids)) => fetch("/api/v1/portal/charges/proof", { method: "POST", headers: { "Content-Type": "multipart/form-data; boundary=x" }, body });

describe("Financeiro do portal x OpenAPI (RF-POR-05, #92)", () => {
  it("o contrato declara as quatro rotas e os campos, sem cartão nem chave de arquivo", () => {
    expect(Object.keys(openapi.paths["/portal/finance"].get.responses)).toEqual(expect.arrayContaining(["200", "400", "401", "403", "404"]));
    expect(openapi.paths["/portal/finance"].get.parameters.map((p: { name: string }) => p.name)).toEqual(["studentId", "scope", "year"]);
    expect(required("PortalFinance")).toEqual(["familyOpenCents", "history", "open", "reduced", "scope", "selectedStudentId", "students", "year", "years"]);
    expect(JSON.stringify(openapi.components.schemas.PortalFinance)).not.toMatch(/cardNumber|fileKey|storageKey|cpf/i);
    expect(Object.keys(openapi.paths["/portal/receipts/{code}/pdf"].get.responses)).toEqual(expect.arrayContaining(["200", "401", "403", "404"]));
    expect(openapi.paths["/portal/charges/pix-instructions"].get).toBeDefined();
    expect(openapi.paths["/portal/charges/proof"].post).toBeDefined();
  });

  it("sem sessão 401; gestão 403; termo pendente 403 TERM_PENDING", async () => {
    expect((await send("GET", "/portal/finance")).status).toBe(401);
    expect((await send("GET", "/portal/receipts/RC-MOCK-1/pdf")).status).toBe(401);
    await login("ana@exemplo.com");
    expect((await send("GET", "/portal/finance")).status).toBe(403);
    expect((await send("GET", "/portal/receipts/RC-MOCK-1/pdf")).status).toBe(403);
    await login("termo@exemplo.com");
    const res = await send("GET", "/portal/finance");
    expect(res.status).toBe(403);
    expect((await res.json()).code).toBe("TERM_PENDING");
  });

  it("responsável: primeiro cartão sem cobranças, filho dois com composição, família com o total e aluno de fora 404", async () => {
    await login("familia@exemplo.com");
    const first = await (await send("GET", "/portal/finance")).json();
    expect(keys(first)).toEqual(required("PortalFinance"));
    expect(first).toMatchObject({ reduced: false, selectedStudentId: MINOR, scope: "STUDENT", year, open: [], history: [], years: [] });

    const second = await (await send("GET", `/portal/finance?studentId=${CHILD_TWO}`)).json();
    expect(second.open.map((c: { id: string }) => c.id)).toEqual([SECOND, THIRD]);
    for (const c of second.open) expect(keys(c)).toEqual([...openapi.components.schemas.PortalFinance.properties.open.items.required].sort());
    expect(second.open[0]).toMatchObject({ situation: "DUE_SOON", amountCents: 5000, planCents: 6000, benefitCents: 1000, canPay: true });
    expect(second.open[1]).toMatchObject({ situation: "PENDING", amountCents: 3000 });
    expect(second.years).toEqual([year, year - 1]);

    const family = await (await send("GET", "/portal/finance?scope=FAMILY")).json();
    expect(family).toMatchObject({ scope: "FAMILY", familyOpenCents: 8000, history: [] });
    expect(family.open.map((c: { studentName: string }) => c.studentName)).toEqual(["Filho Fictício Dois", "Filho Fictício Dois"]);
    expect((await send("GET", "/portal/finance?studentId=00000000-0000-0000-0000-000000000701")).status).toBe(404);
  });

  it("histórico por ano com meio, data e recibo; ano inválido 400; recibo baixa e código desconhecido 404", async () => {
    await login("familia@exemplo.com");
    const now = await (await send("GET", `/portal/finance?studentId=${CHILD_TWO}`)).json();
    expect(now.history.map((h: { status: string }) => h.status)).toEqual(["PAID", "PAID"]);
    for (const h of now.history) expect(keys(h)).toEqual([...openapi.components.schemas.PortalFinance.properties.history.items.required].sort());
    expect(now.history[0]).toMatchObject({ method: "PIX_KEY" });
    const before = await (await send("GET", `/portal/finance?studentId=${CHILD_TWO}&year=${year - 1}`)).json();
    expect(before.year).toBe(year - 1);
    expect(before.history.map((h: { status: string }) => h.status)).toEqual(["PAID", "CANCELED"]);
    expect(before.history[1]).toMatchObject({ paidAt: null, method: null, receipt: null });
    expect((await send("GET", "/portal/finance?year=1999")).status).toBe(400);

    const pdf = await send("GET", `/portal/receipts/${now.history[0].receipt.code}/pdf`);
    expect(pdf.status).toBe(200);
    expect(pdf.headers.get("Content-Type")).toBe("application/pdf");
    expect((await send("GET", "/portal/receipts/inexistente/pdf")).status).toBe(404);
  });

  it("conta reduzida: abre com a cobrança em atraso", async () => {
    await login("reduzido@exemplo.com");
    const res = await (await send("GET", "/portal/finance")).json();
    expect(res.reduced).toBe(true);
    expect(res.open).toMatchObject([{ situation: "OVERDUE", canPay: true, amountCents: 12000 }]);
  });

  it("pagar junto: instruções somam o total, o envio põe todas em conferência e repetir dá 409; seleção inválida 422/404; arquivo ruim 422", async () => {
    await login("familia@exemplo.com");
    const ids = `${SECOND},${THIRD}`;
    const info = await (await send("GET", `/portal/charges/pix-instructions?ids=${ids}`)).json();
    expect(keys(info)).toEqual(required("PixBatchInstructions"));
    expect(info).toMatchObject({ totalCents: 8000, charges: [{ chargeId: SECOND }, { chargeId: THIRD }] });

    expect((await send("GET", `/portal/charges/pix-instructions?ids=${SECOND},${SECOND}`)).status).toBe(422);
    expect((await send("GET", "/portal/charges/pix-instructions?ids=")).status).toBe(422);
    expect((await send("GET", `/portal/charges/pix-instructions?ids=${SECOND},${MINOR}`)).status).toBe(404);
    for (const bad of [multipart([SECOND, THIRD], "text/plain"), multipart([SECOND, THIRD], "image/png", 0), multipart([SECOND, THIRD], "image/png", 5 * 1024 * 1024 + 1)]) {
      const res = await upload([], bad);
      expect(res.status).toBe(422);
    }
    expect((await upload([SECOND, THIRD], multipart([SECOND, THIRD], "text/plain"))).status).toBe(422);

    const sent = await upload([SECOND, THIRD]);
    expect(sent.status).toBe(201);
    expect((await sent.json()).map((p: { chargeId: string; status: string }) => [p.chargeId, p.status])).toEqual([[SECOND, "PENDING"], [THIRD, "PENDING"]]);
    const after = await (await send("GET", `/portal/finance?studentId=${CHILD_TWO}`)).json();
    expect(after.open.map((c: { situation: string; canPay: boolean }) => [c.situation, c.canPay])).toEqual([["IN_REVIEW", false], ["IN_REVIEW", false]]);
    expect((await send("GET", `/portal/charges/pix-instructions?ids=${SECOND}`)).status).toBe(409);
    expect((await upload([SECOND])).status).toBe(409);
  });
});
