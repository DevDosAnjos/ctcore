import { readFileSync } from "node:fs";
import { join } from "node:path";
import { parse } from "yaml";

const openapi = parse(readFileSync(join(process.cwd(), "../docs/openapi.yaml"), "utf8"));
const json = { "Content-Type": "application/json" };
const login = async (email: string, linkId?: string) => {
  await fetch("/api/v1/auth/login", { method: "POST", headers: json, body: JSON.stringify({ email, password: "segredo123" }) });
  if (linkId) await fetch("/api/v1/auth/session/link", { method: "PUT", headers: json, body: JSON.stringify({ linkId }) });
};
const get = () => fetch("/api/v1/organization");
const patch = (body: unknown) => fetch("/api/v1/organization", { method: "PATCH", headers: json, body: JSON.stringify(body) });
// FormData e File do jsdom não passam pelo fetch do Node; o corpo multipart vai montado à mão.
const putLogo = (size = 10) =>
  fetch("/api/v1/organization/logo", {
    method: "PUT",
    headers: { "Content-Type": "multipart/form-data; boundary=x" },
    body: `--x\r\nContent-Disposition: form-data; name="file"; filename="logo.png"\r\nContent-Type: image/png\r\n\r\n${"a".repeat(size)}\r\n--x--\r\n`,
  });
const deleteLogo = () => fetch("/api/v1/organization/logo", { method: "DELETE" });

describe("organização x OpenAPI (CA21)", () => {
  const declared = (path: string, method: string) => Object.keys(openapi.paths[path][method].responses);

  it("sem sessão as quatro rotas respondem o 401 do contrato", async () => {
    for (const [res, path, method] of [
      [await get(), "/organization", "get"],
      [await patch({ name: "x" }), "/organization", "patch"],
      [await putLogo(), "/organization/logo", "put"],
      [await deleteLogo(), "/organization/logo", "delete"],
    ] as const) {
      expect(res.status).toBe(401);
      expect(declared(path, method)).toContain("401");
    }
  });

  it("Coordenador: leitura traz exatamente as chaves do contrato; escrita 200 e erros 400 com o campo", async () => {
    await login("ana@exemplo.com");
    const required: string[] = openapi.components.schemas.OrganizationView.required;
    const read = await (await get()).json();
    expect(Object.keys(read).sort()).toEqual([...required].sort());
    expect(Object.keys(openapi.components.schemas.OrganizationView.properties).sort()).toEqual([...required].sort());

    const saved = await patch({ name: " Dojo ", color: "#1A56DB", holderDocument: "11.222.333/0001-81" });
    expect(saved.status).toBe(200);
    expect(await saved.json()).toEqual({ name: "Dojo", color: "#1a56db", holderDocument: "11222333000181", logoUrl: null });
    expect(await (await get()).json()).toMatchObject({ name: "Dojo" });

    const invalid = [
      [{ name: "" }, "name"],
      [{ name: "ok", color: "#ffeb3b" }, "color"],
      [{ name: "ok", color: "azul" }, "color"],
      [{ name: "ok", holderDocument: "123" }, "holderDocument"],
    ] as const;
    for (const [body, field] of invalid) {
      const res = await patch(body);
      expect(res.status).toBe(400);
      expect(declared("/organization", "patch")).toContain("400");
      const err = await res.json();
      expect(err.code).toBe("VALIDATION_ERROR");
      expect(err.fields.map((f: { field: string }) => f.field)).toContain(field);
    }

    const withLogo = await putLogo();
    expect(withLogo.status).toBe(200);
    expect((await withLogo.json()).logoUrl).toMatch(/^data:image\/png/);
    expect((await putLogo(6 * 1024 * 1024)).status).toBe(400);
    const without = await deleteLogo();
    expect(without.status).toBe(200);
    expect((await without.json()).logoUrl).toBeNull();
    expect((await deleteLogo()).status).toBe(200);
  });

  it("Administrador lê, mas PATCH, PUT e DELETE respondem 403 e nada muda; Professor e portal recebem 403 na leitura", async () => {
    await login("varios@exemplo.com", "00000000-0000-0000-0000-000000000011");
    expect((await get()).status).toBe(200);
    for (const [res, path, method] of [
      [await patch({ name: "Hack" }), "/organization", "patch"],
      [await putLogo(), "/organization/logo", "put"],
      [await deleteLogo(), "/organization/logo", "delete"],
    ] as const) {
      expect(res.status).toBe(403);
      expect((await res.json()).code).toBe("FORBIDDEN");
      expect(declared(path, method)).toContain("403");
    }
    expect((await (await get()).json()).name).toBe("Academia Alfa");
    for (const link of ["00000000-0000-0000-0000-000000000012", "00000000-0000-0000-0000-000000000013"]) {
      await fetch("/api/v1/auth/session/link", { method: "PUT", headers: json, body: JSON.stringify({ linkId: link }) });
      const res = await get();
      expect(res.status).toBe(403);
      expect(declared("/organization", "get")).toContain("403");
    }
  });
});

describe("transferência da coordenação x OpenAPI (RF-ORG-06, CA16)", () => {
  const transfer = (body: unknown, token?: string) =>
    fetch("/api/v1/organization/coordination-transfer", {
      method: "POST",
      headers: token ? { ...json, "X-Reauth-Token": token } : json,
      body: JSON.stringify(body),
    });
  const declared = Object.keys(openapi.paths["/organization/coordination-transfer"].post.responses);
  const ok = { email: " Admin@Alfa.test ", confirmation: " transferir " };

  it("sem sessão 401; Administrador 403; Coordenador sem token 403 REAUTH_REQUIRED; campos errados 400; sucesso 204 e vira Administrador", async () => {
    expect((await transfer(ok, "t")).status).toBe(401);
    await login("varios@exemplo.com", "00000000-0000-0000-0000-000000000011");
    expect((await transfer(ok, "t")).status).toBe(403);

    await login("ana@exemplo.com");
    const noToken = await transfer(ok);
    expect(noToken.status).toBe(403);
    expect((await noToken.json()).code).toBe("REAUTH_REQUIRED");
    for (const [body, field] of [
      [{ ...ok, confirmation: "" }, "confirmation"],
      [{ ...ok, email: "outro@x.com" }, "email"],
    ] as const) {
      const res = await transfer(body, "t");
      expect(res.status).toBe(400);
      expect((await res.json()).fields.map((f: { field: string }) => f.field)).toContain(field);
    }
    const done = await transfer(ok, "t");
    expect(done.status).toBe(204);
    for (const status of [204, 400, 401, 403]) expect(declared).toContain(String(status));
    expect((await transfer(ok, "t")).status).toBe(403);
    expect((await (await fetch("/api/v1/auth/session")).json()).links[0].role).toBe("ADMINISTRATOR");
  });
});
