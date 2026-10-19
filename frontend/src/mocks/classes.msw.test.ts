import { readFileSync } from "node:fs";
import { join } from "node:path";
import { parse } from "yaml";

const openapi = parse(readFileSync(join(process.cwd(), "../docs/openapi.yaml"), "utf8"));
const json = { "Content-Type": "application/json" };
const login = async (email: string, linkId?: string) => {
  await fetch("/api/v1/auth/login", { method: "POST", headers: json, body: JSON.stringify({ email, password: "segredo123" }) });
  if (linkId) await fetch("/api/v1/auth/session/link", { method: "PUT", headers: json, body: JSON.stringify({ linkId }) });
};
const list = (query = "") => fetch(`/api/v1/classes${query}`);
const names = (data: { items: { name: string }[] }) => data.items.map((i) => i.name);

describe("turmas x OpenAPI (RF-TUR-01, CA11)", () => {
  it("sem sessão responde 401, declarado no contrato", async () => {
    expect((await list()).status).toBe(401);
    expect(Object.keys(openapi.paths["/classes"].get.responses)).toEqual(expect.arrayContaining(["200", "400", "401", "403"]));
  });

  it("Coordenador: envelope e linha no formato do contrato, sem link do grupo, ordenadas por nome", async () => {
    await login("ana@exemplo.com");
    const data = await (await list()).json();
    expect(Object.keys(data).sort()).toEqual(["items", "page", "size", "total"]);
    expect(data).toMatchObject({ page: 1, size: 25, total: 3 });
    expect(names(data)).toEqual(["Boxe Noturno", "Jiu-Jitsu Adulto", "Judô Infantil"]);
    const schema = openapi.components.schemas.ClassRow;
    expect(Object.keys(data.items[0]).sort()).toEqual([...schema.required].sort());
    expect(Object.keys(data.items[1].schedule[0]).sort()).toEqual([...schema.properties.schedule.items.required].sort());
    expect(Object.keys(data.items[1].ageRange).sort()).toEqual([...schema.properties.ageRange.required].sort());
    expect(data.items[0].ageRange).toBeNull();
    expect(JSON.stringify(data)).not.toMatch(/groupLink|email|phone/);
    expect(names(await (await list("?sort=name,desc")).json())[0]).toBe("Judô Infantil");
  });

  it("filtros q (sem acento), professor (titular e auxiliar), dia e situação combinam; valor inválido dá 400", async () => {
    await login("ana@exemplo.com");
    expect(names(await (await list("?q=JUDO")).json())).toEqual(["Judô Infantil"]);
    expect(names(await (await list("?weekday=2")).json())).toEqual(["Judô Infantil"]);
    expect(names(await (await list("?status=INACTIVE")).json())).toEqual(["Boxe Noturno"]);
    expect(names(await (await list("?teacherId=00000000-0000-0000-0000-000000000502")).json())).toEqual(["Jiu-Jitsu Adulto", "Judô Infantil"]);
    expect(names(await (await list("?teacherId=00000000-0000-0000-0000-000000000502&weekday=3")).json())).toEqual(["Jiu-Jitsu Adulto"]);
    expect((await list("?weekday=8")).status).toBe(400);
    expect((await list("?status=XYZ")).status).toBe(400);
    expect((await list("?size=10")).status).toBe(400);
    expect((await (await list("?page=2")).json()).items).toEqual([]);
  });

  it("Administrador só vê as turmas da unidade dele; Professor e portal recebem 403", async () => {
    await login("varios@exemplo.com", "00000000-0000-0000-0000-000000000011");
    expect(names(await (await list()).json())).toEqual(["Jiu-Jitsu Adulto", "Judô Infantil"]);
    expect(names(await (await list("?unitId=00000000-0000-0000-0000-000000000022")).json())).toEqual([]);
    await login("varios@exemplo.com", "00000000-0000-0000-0000-000000000013");
    expect((await list()).status).toBe(403);
    await login("varios@exemplo.com", "00000000-0000-0000-0000-000000000012");
    expect((await list()).status).toBe(403);
  });
});
