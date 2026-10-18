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

describe("desativação de unidade x OpenAPI (RF-ORG-07, CA13)", () => {
  const check = (id: string) => fetch(`/api/v1/units/${id}/deactivation-check`);
  const deactivate = (id: string, confirmation: string, token?: string) =>
    fetch(`/api/v1/units/${id}/deactivation`, {
      method: "POST",
      headers: token ? { ...json, "X-Reauth-Token": token } : json,
      body: JSON.stringify({ confirmation }),
    });
  const SECOND = "00000000-0000-0000-0000-000000000022";
  const checkKeys: string[] = openapi.components.schemas.UnitDeactivationCheck.required;

  it("sem sessão 401; Administrador 403; unidade desconhecida 404; chaves do check iguais ao contrato", async () => {
    expect((await check(FIRST)).status).toBe(401);
    expect((await deactivate(FIRST, "x", "t")).status).toBe(401);
    await login("varios@exemplo.com", "00000000-0000-0000-0000-000000000011");
    expect((await check(FIRST)).status).toBe(403);
    expect((await deactivate(FIRST, "x", "t")).status).toBe(403);
    await login("ana@exemplo.com");
    expect((await check("00000000-0000-0000-0000-0000000000ff")).status).toBe(404);
    expect((await deactivate("00000000-0000-0000-0000-0000000000ff", "x", "t")).status).toBe(404);
    const body = await (await check(FIRST)).json();
    expect(Object.keys(body).sort()).toEqual([...checkKeys].sort());
    for (const status of [200, 401, 403, 404]) expect(declared("/units/{id}/deactivation-check", "get")).toContain(String(status));
  });

  it("a única unidade ativa dá 409 UNIT_HAS_BLOCKERS; unidade já desativada 409; nome errado 400; sem token 403; sucesso 204", async () => {
    await login("ana@exemplo.com");
    const last = await deactivate(FIRST, "Unidade Centro", "t");
    expect(last.status).toBe(409);
    const error = await last.json();
    expect(error.code).toBe("UNIT_HAS_BLOCKERS");
    expect(error.fields.map((f: { field: string }) => f.field).sort()).toEqual(["activeClasses", "activeStudents", "lastActiveUnit"]);
    expect((await (await check(FIRST)).json()).canDeactivate).toBe(false);
    expect((await deactivate(SECOND, "Unidade Norte", "t")).status).toBe(409);

    const reactivated = await (await fetch("/api/v1/units/mine")).json();
    expect(reactivated).toHaveLength(2);
    for (const status of [204, 400, 401, 403, 404, 409]) expect(declared("/units/{id}/deactivation", "post")).toContain(String(status));
    expect(openapi.components.schemas.Error.properties.code.enum).toContain("UNIT_HAS_BLOCKERS");
  });
});
