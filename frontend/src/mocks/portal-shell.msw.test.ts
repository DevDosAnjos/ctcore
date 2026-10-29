import { readFileSync } from "node:fs";
import { join } from "node:path";
import { parse } from "yaml";

const openapi = parse(readFileSync(join(process.cwd(), "../docs/openapi.yaml"), "utf8"));
const json = { "Content-Type": "application/json" };
const send = (method: string, path: string, body?: unknown) =>
  fetch(`/api/v1${path}`, { method, headers: json, body: body === undefined ? undefined : JSON.stringify(body) });
const login = (email: string) => send("POST", "/auth/login", { email, password: "segredo123" });

describe("moldura do portal x OpenAPI (#88)", () => {
  it("o contrato declara a rota e o formato da resposta", () => {
    expect(Object.keys(openapi.paths["/portal/shell"].get.responses)).toEqual(expect.arrayContaining(["200", "401", "403"]));
    const schema = openapi.components.schemas.PortalShell;
    expect(schema.required).toEqual(["organization", "reduced", "unreadAnnouncements"]);
    expect(Object.keys(schema.properties.organization.properties)).toEqual(["name", "color", "logoUrl"]);
  });

  it("sem sessão 401; gestão 403", async () => {
    expect((await send("GET", "/portal/shell")).status).toBe(401);
    await login("ana@exemplo.com");
    expect((await send("GET", "/portal/shell")).status).toBe(403);
  });

  it("termo pendente 403 TERM_PENDING", async () => {
    await login("termo@exemplo.com");
    const res = await send("GET", "/portal/shell");
    expect(res.status).toBe(403);
    expect((await res.json()).code).toBe("TERM_PENDING");
  });

  it("conta reduzida: reduced verdadeiro e contador 0", async () => {
    await login("reduzido@exemplo.com");
    expect(await (await send("GET", "/portal/shell")).json()).toMatchObject({ reduced: true, unreadAnnouncements: 0, organization: { name: "Academia Alfa", logoUrl: null } });
    expect((await send("GET", "/portal/term")).status).toBe(200);
    expect(await (await send("GET", "/portal/term")).json()).toEqual([]);
  });

  it("depois do aceite do termo a conta ativa recebe reduced falso e o contador", async () => {
    await login("termo@exemplo.com");
    const pending = await (await send("GET", "/portal/term")).json();
    await send("POST", `/portal/students/${pending[0].id}/term-acceptance`, { version: pending[0].term.version });
    expect(await (await send("GET", "/portal/shell")).json()).toMatchObject({ reduced: false, unreadAnnouncements: 3 });
  });
});
