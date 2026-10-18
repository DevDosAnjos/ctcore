import { readFileSync } from "node:fs";
import { join } from "node:path";
import { parse } from "yaml";

const openapi = parse(readFileSync(join(process.cwd(), "../docs/openapi.yaml"), "utf8"));
const json = { "Content-Type": "application/json" };
const login = async (email: string, linkId?: string) => {
  await fetch("/api/v1/auth/login", { method: "POST", headers: json, body: JSON.stringify({ email, password: "segredo123" }) });
  if (linkId) await fetch("/api/v1/auth/session/link", { method: "PUT", headers: json, body: JSON.stringify({ linkId }) });
};
const list = () => fetch("/api/v1/units");
const adjust = (id: string, shortName: unknown) =>
  fetch(`/api/v1/units/${id}/short-name`, { method: "PATCH", headers: json, body: JSON.stringify({ shortName }) });
const FIRST = "00000000-0000-0000-0000-000000000021";
const declared = (path: string, method: string) => Object.keys(openapi.paths[path][method].responses);

describe("unidades x OpenAPI (CA15)", () => {
  it("sem sessão as duas rotas respondem o 401 do contrato", async () => {
    expect((await list()).status).toBe(401);
    expect((await adjust(FIRST, "x")).status).toBe(401);
    expect(declared("/units", "get")).toContain("401");
    expect(declared("/units/{id}/short-name", "patch")).toContain("401");
  });

  it("Coordenador: a lista traz exatamente as chaves do contrato; o ajuste devolve 200, e os erros 400, 404 e 409 estão no contrato", async () => {
    await login("ana@exemplo.com");
    const required: string[] = openapi.components.schemas.UnitSummary.required;
    const units = await (await list()).json();
    expect(units).toHaveLength(2);
    for (const unit of units) expect(Object.keys(unit).sort()).toEqual([...required].sort());
    expect(Object.keys(openapi.components.schemas.UnitSummary.properties).sort()).toEqual([...required].sort());

    for (const [body, status] of [
      ["Maiuscula", 400],
      ["", 400],
      ["em-uso", 409],
      ["alfa-norte", 409],
    ] as const) {
      expect((await adjust(FIRST, body)).status).toBe(status);
      expect(declared("/units/{id}/short-name", "patch")).toContain(String(status));
    }
    expect((await adjust("00000000-0000-0000-0000-0000000000ff", "novo")).status).toBe(404);
    expect(declared("/units/{id}/short-name", "patch")).toContain("404");

    const ok = await adjust(FIRST, "centro-novo");
    expect(ok.status).toBe(200);
    expect(await ok.json()).toMatchObject({ id: FIRST, shortName: "centro-novo", shortNameAdjusted: true });
    expect((await adjust(FIRST, "outro-nome")).status).toBe(409);
  });

  it("Administrador, Professor e portal recebem 403 na lista e no ajuste, e nada muda", async () => {
    await login("varios@exemplo.com", "00000000-0000-0000-0000-000000000011");
    for (const link of ["00000000-0000-0000-0000-000000000011", "00000000-0000-0000-0000-000000000012", "00000000-0000-0000-0000-000000000013"]) {
      await fetch("/api/v1/auth/session/link", { method: "PUT", headers: json, body: JSON.stringify({ linkId: link }) });
      for (const res of [await list(), await adjust(FIRST, "invasor")]) {
        expect(res.status).toBe(403);
        expect((await res.json()).code).toBe("FORBIDDEN");
      }
    }
    expect(declared("/units", "get")).toContain("403");
    expect(declared("/units/{id}/short-name", "patch")).toContain("403");
    await login("ana@exemplo.com");
    expect((await (await list()).json())[0].shortName).toBe("alfa-centro");
  });
});
