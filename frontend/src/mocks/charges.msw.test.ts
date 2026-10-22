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
const NORTE = "00000000-0000-0000-0000-000000000022";
const bjj = { unitId: CENTRO, modalityId: "00000000-0000-0000-0000-000000000103", primaryClassId: "00000000-0000-0000-0000-000000000601", planId: "00000000-0000-0000-0000-000000000602", dueDay: 5 };
const conclude = async () => {
  const done = await send("POST", `/students/${MINOR}/enrollment/conclude`, { modalities: [bjj], units: [{ unitId: CENTRO }] });
  expect(done.status).toBe(201);
  return (await done.json()).charges[0] as { id: string };
};

describe("lista de cobranças x OpenAPI (RF-COB-01, #72, CA21)", () => {
  it("o contrato declara a rota, os parâmetros novos de /students e a resposta sem juros nem multa", () => {
    const get = openapi.paths["/charges"].get;
    expect(Object.keys(get.responses)).toEqual(expect.arrayContaining(["200", "400", "401", "403", "404"]));
    const names = get.parameters.map((p: { $ref?: string; name?: string }) => p.name ?? p.$ref);
    expect(names).toEqual(expect.arrayContaining(["quick", "from", "to", "status", "type", "modalityId", "classId", "unitId", "q"]));
    expect(openapi.paths["/students"].get.parameters.map((p: { name?: string }) => p.name)).toContain("financial");
    expect(Object.keys(openapi.components.schemas.ChargeRow.properties).join()).not.toMatch(/interest|fine|penalty|juros|multa/i);
  });

  it("sem sessão 401; Professor 403; filtros inválidos 400; ids desconhecidos 404; Administrador fora da unidade recebe lista vazia", async () => {
    expect((await send("GET", "/charges")).status).toBe(401);
    await login("varios@exemplo.com", "00000000-0000-0000-0000-000000000013");
    expect((await send("GET", "/charges")).status).toBe(403);
    await login("ana@exemplo.com");
    for (const bad of ["size=30", "sort=x,asc", "quick=X", "status=X", "type=X", "from=2026-13-01", "from=2026-05-02&to=2026-05-01"]) expect((await send("GET", `/charges?${bad}`)).status).toBe(400);
    for (const missing of ["modalityId", "classId", "unitId"]) expect((await send("GET", `/charges?${missing}=00000000-0000-0000-0000-0000000000de`)).status).toBe(404);
    await login("varios@exemplo.com", "00000000-0000-0000-0000-000000000011");
    const other = await (await send("GET", `/charges?unitId=${NORTE}`)).json();
    expect(other).toMatchObject({ items: [], total: 0, totals: { count: 0, amountCents: 0 } });
  });

  it("devolve o formato do contrato, a situação calculada, os totais do filtrado e passa a Paga com o pagamento", async () => {
    await login("ana@exemplo.com");
    const { id } = await conclude();
    const list = await (await send("GET", "/charges")).json();
    expect(Object.keys(list).sort()).toEqual(["counts", "items", "page", "size", "total", "totals"]);
    expect(Object.keys(list.counts).sort()).toEqual([...openapi.components.schemas.ChargeList.allOf[1].properties.counts.required].sort());
    const row = list.items.find((r: { id: string }) => r.id === id);
    expect(Object.keys(row).sort()).toEqual([...openapi.components.schemas.ChargeRow.required].sort());
    expect(openapi.components.schemas.ChargeSituation.enum).toContain(row.status);
    expect(row).toMatchObject({ type: "MONTHLY", unit: { id: CENTRO }, amountCents: 20000, payment: null });
    expect(list.totals.amountCents).toBe(list.items.reduce((sum: number, r: { amountCents: number }) => sum + r.amountCents, 0));
    expect((await send("POST", `/charges/${id}/payments`, { method: "CASH" })).status).toBe(201);
    const after = await (await send("GET", `/charges?quick=PAID_MONTH`)).json();
    expect(after.items[0]).toMatchObject({ id, status: "PAID", payment: { method: "CASH" } });
    expect(after.counts.paidMonth).toBe(1);
    const byAmount = await (await send("GET", "/charges?sort=amount,asc&q=nao-existe")).json();
    expect(byAmount.total).toBe(0);
  });

  it("a lista de alunos traz financialStatus, counts.overdue e o filtro financial=OVERDUE", async () => {
    await login("ana@exemplo.com");
    const all = await (await send("GET", "/students")).json();
    expect(all.items.every((s: { financialStatus: string }) => ["OVERDUE", "CURRENT"].includes(s.financialStatus))).toBe(true);
    const late = await (await send("GET", "/students?financial=OVERDUE")).json();
    expect(late.total).toBe(all.counts.overdue);
    expect(late.items.every((s: { financialStatus: string }) => s.financialStatus === "OVERDUE")).toBe(true);
    expect((await send("GET", "/students?financial=X")).status).toBe(400);
  });
});
