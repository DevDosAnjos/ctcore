import { readFileSync } from "node:fs";
import { join } from "node:path";
import { parse } from "yaml";

describe("recuperação de senha x OpenAPI (CA12)", () => {
  const openapi = parse(readFileSync(join(process.cwd(), "../docs/openapi.yaml"), "utf8"));
  const post = (url: string, body: unknown) =>
    fetch(`/api/v1/auth/password-reset${url}`, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body) });

  it("o pedido responde 200 com message", async () => {
    const res = await post("", { email: "a@exemplo.com" });
    expect(res.status).toBe(200);
    expect(Object.keys(openapi.paths["/auth/password-reset"].post.responses)).toContain("200");
    expect(await res.json()).toHaveProperty("message");
  });

  it("a nova senha responde os status do contrato, com SessionView no 200 e Error nos demais", async () => {
    const declared = Object.keys(openapi.paths["/auth/password-reset/{code}"].post.responses);
    for (const [code, password, status, errorCode] of [
      ["valido", "Senha123", 200, undefined],
      ["valido", "Password1", 400, "PASSWORD_LEAKED"],
      ["fraca", "Senha123", 400, "PASSWORD_WEAK"],
      ["vazada", "Senha123", 400, "PASSWORD_LEAKED"],
      ["vencido", "Senha123", 410, "RESET_LINK_INVALID"],
    ] as const) {
      const res = await post(`/${code}`, { password });
      expect(res.status).toBe(status);
      expect(declared).toContain(String(status));
      const body = await res.json();
      if (status === 200) for (const k of openapi.components.schemas.SessionView.required) expect(body).toHaveProperty(k);
      else expect(body.code).toBe(errorCode);
    }
  });
});
