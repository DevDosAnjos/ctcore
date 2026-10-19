import { readFileSync } from "node:fs";
import { join } from "node:path";
import { parse } from "yaml";

const openapi = parse(readFileSync(join(process.cwd(), "../docs/openapi.yaml"), "utf8"));
const json = { "Content-Type": "application/json" };
const FIRST = "00000000-0000-0000-0000-000000000021";
const SECOND = "00000000-0000-0000-0000-000000000022";
const login = async (email: string, linkId?: string) => {
  await fetch("/api/v1/auth/login", { method: "POST", headers: json, body: JSON.stringify({ email, password: "segredo123" }) });
  if (linkId) await fetch("/api/v1/auth/session/link", { method: "PUT", headers: json, body: JSON.stringify({ linkId }) });
};
const list = (unit: string) => fetch(`/api/v1/units/${unit}/modalities`);
const offer = (unit: string, modality: string, body: unknown) =>
  fetch(`/api/v1/units/${unit}/modalities/${modality}`, { method: "PUT", headers: json, body: JSON.stringify(body) });
const declared = (path: string, method: string) => Object.keys(openapi.paths[path][method].responses);
const required = (name: string): string[] => [...openapi.components.schemas[name].required].sort();

describe("modalidades da unidade x OpenAPI (RF-MOD-03, CA9)", () => {
  it("sem sessão as duas rotas respondem o 401 do contrato", async () => {
    expect((await list(FIRST)).status).toBe(401);
    expect((await offer(FIRST, "x", { offered: true })).status).toBe(401);
    expect(declared("/units/{unitId}/modalities", "get")).toContain("401");
    expect(declared("/units/{unitId}/modalities/{modalityId}", "put")).toContain("401");
  });

  it("Coordenador: lista só as ativas no formato do contrato; marca, repete, desmarca; 404 e 400 do contrato; marcação por unidade", async () => {
    await login("ana@exemplo.com");
    const catalog = await (await fetch("/api/v1/modalities")).json();
    const judo = catalog.find((m: { name: string }) => m.name === "Judô").id;
    const boxe = catalog.find((m: { name: string }) => m.name === "Boxe").id;
    expect((await (await list(FIRST)).json()).items).toEqual([]);
    expect((await offer(FIRST, judo, { offered: true })).status).toBe(404);
    for (const id of [judo, boxe]) await fetch(`/api/v1/modalities/${id}/activation`, { method: "PUT", headers: json, body: JSON.stringify({ active: true }) });

    const view = await (await list(FIRST)).json();
    expect(Object.keys(view).sort()).toEqual(required("UnitModalities"));
    expect(view.canManage).toBe(true);
    expect(view.items.map((m: { name: string }) => m.name)).toEqual(["Boxe", "Judô"]);
    for (const m of view.items) expect(Object.keys(m).sort()).toEqual(required("UnitModality"));

    const marked = await offer(FIRST, judo, { offered: true });
    expect(marked.status).toBe(200);
    expect(Object.keys(await marked.json()).sort()).toEqual(required("UnitModality"));
    expect((await offer(FIRST, judo, { offered: true })).status).toBe(200);
    expect((await (await list(FIRST)).json()).items[1].offered).toBe(true);
    expect((await (await list(SECOND)).json()).items[1].offered).toBe(false);
    expect((await (await offer(FIRST, judo, { offered: false })).json()).offered).toBe(false);

    expect((await offer(FIRST, judo, {})).status).toBe(400);
    expect((await offer(FIRST, "00000000-0000-0000-0000-0000000009ff", { offered: true })).status).toBe(404);
    expect((await list("00000000-0000-0000-0000-0000000009ff")).status).toBe(404);
    for (const status of ["200", "400", "403", "404"]) expect(declared("/units/{unitId}/modalities/{modalityId}", "put")).toContain(status);
    for (const status of ["200", "403", "404"]) expect(declared("/units/{unitId}/modalities", "get")).toContain(status);
  });

  it("Professor recebe 403 nas duas rotas", async () => {
    await login("varios@exemplo.com", "00000000-0000-0000-0000-000000000012");
    expect((await list(FIRST)).status).toBe(403);
    expect((await offer(FIRST, "x", { offered: true })).status).toBe(403);
  });
});
