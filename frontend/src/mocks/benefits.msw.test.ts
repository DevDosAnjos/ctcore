import { readFileSync } from "node:fs";
import { join } from "node:path";
import { parse } from "yaml";

const openapi = parse(readFileSync(join(process.cwd(), "../docs/openapi.yaml"), "utf8"));
const json = { "Content-Type": "application/json" };
const login = async (email: string, linkId?: string) => {
  await fetch("/api/v1/auth/login", { method: "POST", headers: json, body: JSON.stringify({ email, password: "segredo123" }) });
  if (linkId) await fetch("/api/v1/auth/session/link", { method: "PUT", headers: json, body: JSON.stringify({ linkId }) });
};
const benefits = (query = "") => fetch(`/api/v1/benefits${query}`);
const names = (data: { items: { student: { name: string } }[] }) => data.items.map((i) => i.student.name);

describe("tabela de benefícios x OpenAPI (RF-BEN-01, CA12)", () => {
  it("sem sessão 401; contrato declara a rota e os filtros", async () => {
    expect((await benefits()).status).toBe(401);
    const get = openapi.paths["/benefits"].get;
    expect(Object.keys(get.responses)).toEqual(expect.arrayContaining(["200", "400", "401", "403"]));
    expect(get.parameters.map((p: { name?: string }) => p.name).filter(Boolean)).toEqual(expect.arrayContaining(["q", "type", "modalityId", "unitId", "status", "high"]));
  });

  it("Coordenador: itens no formato do contrato, ordenados pelo aluno; filtros combinam; valores inválidos dão 400", async () => {
    await login("ana@exemplo.com");
    const data = await (await benefits()).json();
    const required = openapi.components.schemas.BenefitRow.required as string[];
    expect(data.total).toBe(5);
    for (const item of data.items) expect(Object.keys(item).sort()).toEqual([...required].sort());
    expect(names(data)).toEqual(["Ana Souza", "Bruno Lima", "Carla Dias", "Davi Rocha", "Eva Melo"]);
    expect(names(await (await benefits("?high=true")).json())).toEqual(["Bruno Lima", "Davi Rocha"]);
    expect((await (await benefits("?status=EXPIRED&type=PERCENT")).json()).total).toBe(1);
    expect((await (await benefits("?q=bruno")).json()).items[0].monthlyValueCents).toBe(8000);
    expect((await benefits("?type=X")).status).toBe(400);
    expect((await benefits("?status=XYZ")).status).toBe(400);
    expect((await benefits("?size=10")).status).toBe(400);
  });

  it("Administrador só vê a unidade dele; Professor e portal recebem 403", async () => {
    await login("varios@exemplo.com", "00000000-0000-0000-0000-000000000011");
    expect((await (await benefits()).json()).total).toBe(3);
    await login("varios@exemplo.com", "00000000-0000-0000-0000-000000000013");
    expect((await benefits()).status).toBe(403);
  });
});
