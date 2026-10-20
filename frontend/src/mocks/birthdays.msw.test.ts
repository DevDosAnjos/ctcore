import { readFileSync } from "node:fs";
import { join } from "node:path";
import { parse } from "yaml";

const openapi = parse(readFileSync(join(process.cwd(), "../docs/openapi.yaml"), "utf8"));
const json = { "Content-Type": "application/json" };
const login = async (email: string, linkId?: string) => {
  await fetch("/api/v1/auth/login", { method: "POST", headers: json, body: JSON.stringify({ email, password: "segredo123" }) });
  if (linkId) await fetch("/api/v1/auth/session/link", { method: "PUT", headers: json, body: JSON.stringify({ linkId }) });
};
const send = (method: string, path: string, body?: unknown) =>
  fetch(`/api/v1${path}`, { method, headers: json, body: body === undefined ? undefined : JSON.stringify(body) });

describe("aniversariantes do dia x OpenAPI (RF-ALU-06)", () => {
  it("o contrato declara a rota e o esquema que o handler devolve", () => {
    expect(Object.keys(openapi.paths["/students/birthdays"].get.responses)).toEqual(expect.arrayContaining(["200", "401", "403"]));
    expect(openapi.components.schemas.StudentBirthday.required).toEqual(
      expect.arrayContaining(["id", "name", "age", "ageRange", "registeredUnit", "contactName", "contactPhone", "contactIsGuardian"]),
    );
  });

  it("sem sessão 401; Coordenador 403; Administrador 200 só com os de hoje, sem dado pessoal; Professor 200 vazio", async () => {
    expect((await send("GET", "/students/birthdays")).status).toBe(401);
    await login("ana@exemplo.com");
    expect((await send("GET", "/students/birthdays")).status).toBe(403);
    await login("varios@exemplo.com", "00000000-0000-0000-0000-000000000011");
    expect(await (await send("GET", "/students/birthdays")).json()).toEqual([]);
    const now = new Date();
    const birthDate = `1990-${String(now.getMonth() + 1).padStart(2, "0")}-${String(now.getDate()).padStart(2, "0")}`;
    const created = await send("POST", "/students", {
      registeredUnitId: "00000000-0000-0000-0000-000000000021", name: "Parabens Hoje", birthDate, postalCode: "01001-000", street: "Rua", number: "5",
      district: "Centro", city: "Rio", state: "rj", cpf: "111.444.777-35", email: "p@alfa.test", phone: "(21) 91234-5678",
    });
    expect(created.status).toBe(201);
    const list = await (await send("GET", "/students/birthdays")).json();
    expect(list).toHaveLength(1);
    const schema = openapi.components.schemas.StudentBirthday;
    for (const key of schema.required) expect(list[0]).toHaveProperty(key);
    expect(Object.keys(list[0]).every((k) => k in schema.properties)).toBe(true);
    expect(list[0]).toMatchObject({ name: "Parabens Hoje", contactPhone: "21912345678", contactIsGuardian: false });
    await login("varios@exemplo.com", "00000000-0000-0000-0000-000000000013");
    expect(await (await send("GET", "/students/birthdays")).json()).toEqual([]);
  });
});
