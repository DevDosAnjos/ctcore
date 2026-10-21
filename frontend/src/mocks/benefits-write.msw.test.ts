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
const ADULT = "00000000-0000-0000-0000-000000000701";
const CENTRO = "00000000-0000-0000-0000-000000000021";
const JUDO = "00000000-0000-0000-0000-000000000104";
const JUDO_CLASS = "00000000-0000-0000-0000-000000000602";
const JUDO_PLAN = "00000000-0000-0000-0000-000000000601"; // R$ 150,00

// Matricula o aluno adulto no Judô (plano de R$ 150, vencimento dia 5) e devolve o id da modalidade.
const enroll = async () => {
  const r = await (await send("POST", `/students/${ADULT}/modalities`, { modalities: [{ unitId: CENTRO, modalityId: JUDO, primaryClassId: JUDO_CLASS, planId: JUDO_PLAN, dueDay: 5 }] })).json();
  return r.modalities[0].id as string;
};
const body = (row: string, over: Record<string, unknown> = {}) => ({ studentId: ADULT, modalityRowId: row, type: "PERCENT", value: 30, reason: "Bolsa social", decidedBy: "Mestre", ...over });

describe("conceder benefício x OpenAPI (RF-BEN-02, CA15)", () => {
  it("o contrato declara as rotas, os esquemas e o selo Bolsista", () => {
    expect(Object.keys(openapi.paths["/benefits"].post.responses)).toEqual(expect.arrayContaining(["201", "400", "401", "403", "404", "409"]));
    expect(Object.keys(openapi.paths["/benefits/preview"].post.responses)).toEqual(expect.arrayContaining(["200", "400", "401", "403", "404"]));
    expect(openapi.components.schemas.BenefitPreview.required).toEqual(["plan", "current", "newMonthlyValueCents", "effectiveFrom", "replaces", "high"]);
    expect(openapi.components.schemas.StudentSeal.enum).toContain("SCHOLARSHIP");
  });

  it("sem sessão 401; Professor 403", async () => {
    expect((await send("POST", "/benefits/preview", body("x"))).status).toBe(401);
    await login("varios@exemplo.com", "00000000-0000-0000-0000-000000000013");
    expect((await send("POST", "/benefits", body("x"))).status).toBe(403);
    expect((await send("POST", "/benefits/preview", body("x"))).status).toBe(403);
  });

  it("preview: 30%, R$ 50, R$ 200 e 100% sobre R$ 150; não grava", async () => {
    await login("ana@exemplo.com");
    const row = await enroll();
    const cases: [string, number, number, boolean][] = [["PERCENT", 30, 10500, false], ["AMOUNT", 5000, 10000, false], ["AMOUNT", 20000, 0, true], ["PERCENT", 100, 0, true]];
    for (const [type, value, monthly, high] of cases) {
      const p = await (await send("POST", "/benefits/preview", body(row, { type, value }))).json();
      expect(Object.keys(p).sort()).toEqual([...openapi.components.schemas.BenefitPreview.required].sort());
      expect(p).toMatchObject({ newMonthlyValueCents: monthly, high, replaces: false, current: null, plan: { valueCents: 15000 } });
      expect(p.effectiveFrom).toMatch(/^\d{4}-\d{2}$/);
    }
    expect((await (await send("GET", "/benefits")).json()).total).toBe(5);
  });

  it("concede (201 no formato da lista), 409 sem confirmação, substitui com replaceExisting e dá o selo Bolsista", async () => {
    await login("ana@exemplo.com");
    const row = await enroll();
    const created = await send("POST", "/benefits", { ...body(row), grantedBy: "Invasor" });
    expect(created.status).toBe(201);
    const first = await created.json();
    expect(Object.keys(first).sort()).toEqual([...openapi.components.schemas.BenefitRow.required].sort());
    expect(first).toMatchObject({ status: "ACTIVE", type: "PERCENT", value: 30, monthlyValueCents: 10500, grantedBy: "Ana" });
    const list = await (await send("GET", "/benefits")).json();
    expect(list.total).toBe(6);
    expect((await (await send("GET", "/students?seal=SCHOLARSHIP")).json()).items.map((s: { name: string }) => s.name)).toEqual(["Aluno Adulto Fictício"]);

    const conflict = await send("POST", "/benefits", body(row, { type: "AMOUNT", value: 5000 }));
    expect(conflict.status).toBe(409);
    const preview = await (await send("POST", "/benefits/preview", body(row, { type: "AMOUNT", value: 5000 }))).json();
    expect(preview).toMatchObject({ replaces: true, current: { id: first.id, value: 30 } });
    const replaced = await send("POST", "/benefits", body(row, { type: "AMOUNT", value: 5000, replaceExisting: true }));
    expect(replaced.status).toBe(201);
    const after = await (await send("GET", "/benefits?status=ACTIVE")).json();
    expect(after.items.filter((b: { student: { id: string } }) => b.student.id === ADULT)).toHaveLength(1);
    expect((await send("GET", "/benefits?status=ENDED")).status).toBe(200);
    // só desconto em reais: o selo some
    expect((await (await send("GET", "/students?seal=SCHOLARSHIP")).json()).items).toEqual([]);
  });

  it("recusas: 400 com o campo, 404 de aluno e modalidade desconhecidos", async () => {
    await login("ana@exemplo.com");
    const row = await enroll();
    const field = async (over: Record<string, unknown>) => {
      const r = await send("POST", "/benefits", body(row, over));
      expect(r.status).toBe(400);
      return (await r.json()).fields[0].field as string;
    };
    expect(await field({ type: "X" })).toBe("type");
    expect(await field({ value: 0 })).toBe("value");
    expect(await field({ value: 101 })).toBe("value");
    expect(await field({ value: 10.5 })).toBe("value");
    expect(await field({ type: "AMOUNT", value: 100_000_000 })).toBe("value");
    expect(await field({ reason: " " })).toBe("reason");
    expect(await field({ decidedBy: "M" })).toBe("decidedBy");
    expect(await field({ validUntil: "2000-01-01" })).toBe("validUntil");
    expect((await send("POST", "/benefits", body(row, { studentId: "00000000-0000-0000-0000-00000000ffff" }))).status).toBe(404);
    expect((await send("POST", "/benefits", body("00000000-0000-0000-0000-00000000ffff"))).status).toBe(404);
    expect((await send("POST", "/benefits", body(row, { validUntil: "2999-12-31" }))).status).toBe(201);
  });
});
