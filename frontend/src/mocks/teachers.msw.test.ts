import { readFileSync } from "node:fs";
import { join } from "node:path";
import { parse } from "yaml";

const openapi = parse(readFileSync(join(process.cwd(), "../docs/openapi.yaml"), "utf8"));
const json = { "Content-Type": "application/json" };
const login = async (email: string, linkId?: string) => {
  await fetch("/api/v1/auth/login", { method: "POST", headers: json, body: JSON.stringify({ email, password: "segredo123" }) });
  if (linkId) await fetch("/api/v1/auth/session/link", { method: "PUT", headers: json, body: JSON.stringify({ linkId }) });
};
const list = (query = "") => fetch(`/api/v1/teachers${query}`);
const names = (data: { items: { name: string }[] }) => data.items.map((i) => i.name);

describe("professores x OpenAPI (RF-PRO-01, CA10)", () => {
  it("sem sessão responde 401, declarado no contrato", async () => {
    expect((await list()).status).toBe(401);
    expect(Object.keys(openapi.paths["/teachers"].get.responses)).toEqual(expect.arrayContaining(["200", "400", "401", "403"]));
  });

  it("Coordenador: envelope e linha no formato do contrato, sem contato, ordenados por nome", async () => {
    await login("ana@exemplo.com");
    const data = await (await list()).json();
    expect(Object.keys(data).sort()).toEqual(["items", "page", "size", "total"]);
    expect(data).toMatchObject({ page: 1, size: 25, total: 3 });
    expect(names(data)).toEqual(["Ana Souza", "João Pereira", "Marcos Lima"]);
    expect(Object.keys(data.items[0]).sort()).toEqual([...openapi.components.schemas.TeacherRow.required].sort());
    expect(JSON.stringify(data)).not.toMatch(/email|phone/);
    expect(names(await (await list("?sort=name,desc")).json())[0]).toBe("Marcos Lima");
  });

  it("filtros q (sem acento), modalidade, unidade e situação combinam; valor inválido dá 400", async () => {
    await login("ana@exemplo.com");
    expect(names(await (await list("?q=JOAO")).json())).toEqual(["João Pereira"]);
    expect(names(await (await list("?status=INACTIVE")).json())).toEqual(["Marcos Lima"]);
    expect(names(await (await list("?unitId=00000000-0000-0000-0000-000000000022")).json())).toEqual(["João Pereira", "Marcos Lima"]);
    expect(names(await (await list("?modalityId=00000000-0000-0000-0000-000000000103&unitId=00000000-0000-0000-0000-000000000022")).json())).toEqual(["João Pereira"]);
    expect((await list("?status=XYZ")).status).toBe(400);
    expect((await list("?size=10")).status).toBe(400);
    expect((await list("?page=0")).status).toBe(400);
    const second = await (await list("?page=2")).json();
    expect(second.items).toEqual([]);
  });

  it("Administrador só vê os da unidade dele; Professor e portal recebem 403", async () => {
    await login("varios@exemplo.com", "00000000-0000-0000-0000-000000000011");
    expect(names(await (await list()).json())).toEqual(["Ana Souza", "João Pereira"]);
    expect(names(await (await list("?unitId=00000000-0000-0000-0000-000000000022")).json())).toEqual([]);
    await login("varios@exemplo.com", "00000000-0000-0000-0000-000000000013");
    expect((await list()).status).toBe(403);
    await login("varios@exemplo.com", "00000000-0000-0000-0000-000000000012");
    expect((await list()).status).toBe(403);
  });
});
