import { readFileSync } from "node:fs";
import { join } from "node:path";
import { parse } from "yaml";

const openapi = parse(readFileSync(join(process.cwd(), "../docs/openapi.yaml"), "utf8"));
const json = { "Content-Type": "application/json" };
const login = async (email: string, linkId?: string) => {
  await fetch("/api/v1/auth/login", { method: "POST", headers: json, body: JSON.stringify({ email, password: "segredo123" }) });
  if (linkId) await fetch("/api/v1/auth/session/link", { method: "PUT", headers: json, body: JSON.stringify({ linkId }) });
};
const summary = (query = "") => fetch(`/api/v1/plans/summary${query}`);

describe("resumo de planos x OpenAPI (RF-PLA-01, CA10)", () => {
  it("sem sessão 401; contrato declara a rota", async () => {
    expect((await summary()).status).toBe(401);
    expect(Object.keys(openapi.paths["/plans/summary"].get.responses)).toEqual(expect.arrayContaining(["200", "401", "403"]));
  });

  it("Coordenador: formato do contrato, todas as unidades ou a filtrada", async () => {
    await login("ana@exemplo.com");
    const data = await (await summary()).json();
    const schema = openapi.components.schemas.PlanSummary;
    expect(Object.keys(data).sort()).toEqual([...schema.required].sort());
    expect(Object.keys(data.plans).sort()).toEqual([...schema.properties.plans.required].sort());
    expect(Object.keys(data.benefits).sort()).toEqual([...schema.properties.benefits.required].sort());
    expect(data.plans).toEqual({ active: 3, inactive: 1, studentsUsing: 0, minValueCents: 15000, maxValueCents: 30000 });
    expect(data.benefits).toEqual({ active: 0, high: 0 });
    const other = await (await summary("?unitId=00000000-0000-0000-0000-000000000022")).json();
    expect(other.plans).toMatchObject({ active: 1, inactive: 0, minValueCents: 30000, maxValueCents: 30000 });
    const none = await (await summary("?unitId=00000000-0000-0000-0000-0000000000ff")).json();
    expect(none.plans).toMatchObject({ active: 0, minValueCents: null, maxValueCents: null });
  });

  it("Administrador só vê a unidade dele (fora do alcance, zerado); Professor e portal recebem 403", async () => {
    await login("varios@exemplo.com", "00000000-0000-0000-0000-000000000011");
    expect((await (await summary()).json()).plans).toMatchObject({ active: 2, inactive: 1 });
    expect((await (await summary("?unitId=00000000-0000-0000-0000-000000000022")).json()).plans).toMatchObject({ active: 0, minValueCents: null });
    await login("varios@exemplo.com", "00000000-0000-0000-0000-000000000013");
    expect((await summary()).status).toBe(403);
    await login("varios@exemplo.com", "00000000-0000-0000-0000-000000000012");
    expect((await summary()).status).toBe(403);
  });
});

describe("tabela de planos x OpenAPI (RF-PLA-02, CA8)", () => {
  const list = (query = "") => fetch(`/api/v1/plans${query}`);

  it("sem sessão 401; contrato declara a rota", async () => {
    expect((await list()).status).toBe(401);
    expect(Object.keys(openapi.paths["/plans"].get.responses)).toEqual(expect.arrayContaining(["200", "400", "401", "403"]));
  });

  it("Coordenador: formato do contrato, ordem por modalidade e nome, filtros e 400", async () => {
    await login("ana@exemplo.com");
    const data = await (await list()).json();
    expect(data).toMatchObject({ page: 1, size: 25, total: 4 });
    for (const row of data.items) expect(Object.keys(row).sort()).toEqual([...openapi.components.schemas.PlanRow.required].sort());
    expect(data.items.map((r: { name: string }) => r.name)).toEqual(["Boxe 3x", "Jiu-Jitsu Livre", "Judô Antigo", "Judô Mensal"]);
    expect(data.items[1].weeklyFrequency).toBeNull();
    const judo = await (await list(`?q=JUDO&status=ACTIVE`)).json();
    expect(judo.items.map((r: { name: string }) => r.name)).toEqual(["Judô Mensal"]);
    expect((await list("?status=XYZ")).status).toBe(400);
    expect((await list("?size=10")).status).toBe(400);
  });

  it("Administrador só vê a unidade dele; Professor 403", async () => {
    await login("varios@exemplo.com", "00000000-0000-0000-0000-000000000011");
    expect((await (await list()).json()).total).toBe(3);
    expect((await (await list("?unitId=00000000-0000-0000-0000-000000000022")).json()).items).toEqual([]);
    await login("varios@exemplo.com", "00000000-0000-0000-0000-000000000013");
    expect((await list()).status).toBe(403);
  });
});
