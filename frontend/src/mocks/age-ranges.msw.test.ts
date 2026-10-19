import { readFileSync } from "node:fs";
import { join } from "node:path";
import { parse } from "yaml";

const openapi = parse(readFileSync(join(process.cwd(), "../docs/openapi.yaml"), "utf8"));
const json = { "Content-Type": "application/json" };
const login = async (email: string, linkId?: string) => {
  await fetch("/api/v1/auth/login", { method: "POST", headers: json, body: JSON.stringify({ email, password: "segredo123" }) });
  if (linkId) await fetch("/api/v1/auth/session/link", { method: "PUT", headers: json, body: JSON.stringify({ linkId }) });
};
const list = () => fetch("/api/v1/age-ranges");
const create = (body: unknown) => fetch("/api/v1/age-ranges", { method: "POST", headers: json, body: JSON.stringify(body) });
const change = (id: string, body: unknown) => fetch(`/api/v1/age-ranges/${id}`, { method: "PUT", headers: json, body: JSON.stringify(body) });
const remove = (id: string) => fetch(`/api/v1/age-ranges/${id}`, { method: "DELETE" });
const declared = (path: string, method: string) => Object.keys(openapi.paths[path][method].responses);

describe("faixas etárias x OpenAPI (RF-MOD-04, CA11)", () => {
  it("sem sessão as quatro rotas respondem 401, declarado no contrato", async () => {
    expect((await list()).status).toBe(401);
    expect((await create({ name: "Master", minAge: 35 })).status).toBe(401);
    expect((await change("x", { name: "Master", minAge: 35 })).status).toBe(401);
    expect((await remove("x")).status).toBe(401);
    for (const [path, method] of [["/age-ranges", "get"], ["/age-ranges", "post"], ["/age-ranges/{id}", "put"], ["/age-ranges/{id}", "delete"]]) {
      expect(declared(path, method)).toContain("401");
    }
  });

  it("Coordenador: lista as três padrão no formato do contrato; cria, altera e apaga com os códigos declarados", async () => {
    await login("ana@exemplo.com");
    const data = await (await list()).json();
    expect(Object.keys(data).sort()).toEqual([...openapi.components.schemas.AgeRangeList.required].sort());
    expect(data.canManage).toBe(true);
    expect(data.items.map((i: { name: string }) => i.name)).toEqual(["Infantil", "Juvenil", "Adulto"]);
    expect(Object.keys(data.items[0]).sort()).toEqual([...openapi.components.schemas.AgeRange.required].sort());
    expect(data.items[2].maxAge).toBeNull();

    const created = await create({ name: "Master", minAge: 35 });
    expect(created.status).toBe(201);
    const master = await created.json();
    expect((await create({ name: "INFANTIL", minAge: 4 })).status).toBe(409);
    expect((await create({ name: "Kids", minAge: 4, maxAge: 12 })).status).toBe(201);
    expect((await create({ name: "a", minAge: 4 })).status).toBe(400);
    expect((await create({ name: "Teste" })).status).toBe(400);
    expect((await create({ name: "Teste", minAge: 10, maxAge: 9 })).status).toBe(400);
    const changed = await change(master.id, { name: "Veterano", minAge: 40, maxAge: 60 });
    expect(changed.status).toBe(200);
    expect(await changed.json()).toEqual({ id: master.id, name: "Veterano", minAge: 40, maxAge: 60 });
    expect((await change(master.id, { name: "adulto", minAge: 40 })).status).toBe(409);
    expect((await change("00000000-0000-0000-0000-0000000009ff", { name: "Z1", minAge: 1 })).status).toBe(404);
    expect((await remove(master.id)).status).toBe(204);
    expect((await remove(master.id)).status).toBe(404);
    for (const [path, method, statuses] of [
      ["/age-ranges", "post", ["201", "400", "409"]],
      ["/age-ranges/{id}", "put", ["200", "400", "404", "409"]],
      ["/age-ranges/{id}", "delete", ["204", "404"]],
    ] as const) {
      for (const status of statuses) expect(declared(path, method)).toContain(status);
    }
  });

  it("Administrador lê com canManage falso e recebe 403 ao escrever; Professor recebe 403 em tudo", async () => {
    await login("varios@exemplo.com", "00000000-0000-0000-0000-000000000011");
    const admin = await (await list()).json();
    expect(admin.canManage).toBe(false);
    expect((await create({ name: "Master", minAge: 35 })).status).toBe(403);
    expect((await change(admin.items[0].id, { name: "Novo", minAge: 1 })).status).toBe(403);
    expect((await remove(admin.items[0].id)).status).toBe(403);
    await fetch("/api/v1/auth/session/link", { method: "PUT", headers: json, body: JSON.stringify({ linkId: "00000000-0000-0000-0000-000000000012" }) });
    expect((await list()).status).toBe(403);
    expect(declared("/age-ranges", "get")).toContain("403");
  });
});
