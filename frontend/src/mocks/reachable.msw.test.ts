import { readFileSync } from "node:fs";
import { join } from "node:path";
import { parse } from "yaml";

const openapi = parse(readFileSync(join(process.cwd(), "../docs/openapi.yaml"), "utf8"));
const json = { "Content-Type": "application/json" };
const FIRST = "00000000-0000-0000-0000-000000000021";
const login = (email: string) => fetch("/api/v1/auth/login", { method: "POST", headers: json, body: JSON.stringify({ email, password: "segredo123" }) });
const link = (linkId: string) => fetch("/api/v1/auth/session/link", { method: "PUT", headers: json, body: JSON.stringify({ linkId }) });
const reachable = () => fetch("/api/v1/units/reachable");
const declared = Object.keys(openapi.paths["/units/reachable"].get.responses);
const schema = openapi.components.schemas.ReachableUnits;

describe("unidades ao alcance x OpenAPI (RF-ORG-05)", () => {
  it("sem sessão responde o 401 do contrato", async () => {
    expect((await reachable()).status).toBe(401);
    expect(declared).toEqual(expect.arrayContaining(["200", "401", "403"]));
  });

  it("Coordenador recebe as ativas com allUnits; Administrador só a sua; portal 403", async () => {
    await login("ana@exemplo.com");
    const all = await (await reachable()).json();
    expect(Object.keys(all).sort()).toEqual(Object.keys(schema.properties).sort());
    expect(all.allUnits).toBe(true);
    expect(Object.keys(all.units[0]).sort()).toEqual(Object.keys(schema.properties.units.items.properties).sort());
    expect(all.units.map((u: { id: string }) => u.id)).toEqual([FIRST]);

    await login("varios@exemplo.com");
    await link("00000000-0000-0000-0000-000000000011");
    const own = await (await reachable()).json();
    expect(own.allUnits).toBe(false);
    expect(own.units).toHaveLength(1);

    await link("00000000-0000-0000-0000-000000000012");
    const res = await reachable();
    expect(res.status).toBe(403);
    expect((await res.json()).code).toBe("FORBIDDEN");
  });
});
