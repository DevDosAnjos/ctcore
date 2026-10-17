import { readFileSync } from "node:fs";
import { join } from "node:path";
import { parse } from "yaml";

describe("convite x OpenAPI (CA12)", () => {
  const openapi = parse(readFileSync(join(process.cwd(), "../docs/openapi.yaml"), "utf8"));
  const accept = (code: string, password: string) =>
    fetch(`/api/v1/auth/invitations/${code}/accept`, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ password }) });

  it("consulta 200 traz os campos obrigatórios do InviteInfo, com name, email e expiresAt", async () => {
    const res = await fetch("/api/v1/auth/invitations/valido");
    expect(res.status).toBe(200);
    const body = await res.json();
    for (const k of openapi.components.schemas.InviteInfo.required) expect(body).toHaveProperty(k);
    expect(body).toHaveProperty("name");
  });

  it("consulta e aceite respondem os status declarados no contrato", async () => {
    const get = Object.keys(openapi.paths["/auth/invitations/{code}"].get.responses);
    const post = Object.keys(openapi.paths["/auth/invitations/{code}/accept"].post.responses);
    const gone = await fetch("/api/v1/auth/invitations/vencido");
    expect(get).toContain(String(gone.status));
    expect((await gone.json()).fields).toEqual([{ field: "reason", message: "EXPIRED" }]);
    for (const [code, password, status] of [
      ["valido", "Senha123", 200],
      ["valido", "Password1", 400],
      ["valido", "fraca", 400],
      ["existente", "errada", 401],
      ["existente", "limite", 429],
      ["usado", "Senha123", 410],
    ] as const) {
      const res = await accept(code, password);
      expect(res.status).toBe(status);
      expect(post).toContain(String(status));
    }
  });
});
