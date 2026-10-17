import { readFileSync } from "node:fs";
import { join } from "node:path";
import { http, HttpResponse } from "msw";
import { parse } from "yaml";
import { routes } from "./handlers";
import { server } from "./server";

describe("MSW (CA2)", () => {
  it("usa o handler registrado no teste", async () => {
    server.use(http.get("http://localhost/api/v1/ping", () => HttpResponse.json({ ok: true })));
    const res = await fetch("http://localhost/api/v1/ping");
    expect(await res.json()).toEqual({ ok: true });
  });

  it("rota sem handler falha", async () => {
    await expect(fetch("http://localhost/api/v1/sem-handler")).rejects.toThrow();
  });
});

describe("handlers x OpenAPI (CA3)", () => {
  const openapi = parse(readFileSync(join(process.cwd(), "../docs/openapi.yaml"), "utf8"));

  it("todo handler base corresponde a um caminho e método do OpenAPI", () => {
    for (const r of routes) {
      expect(openapi.paths?.[r.path]?.[r.method], `${r.method} ${r.path}`).toBeDefined();
    }
  });
});

describe("login e sessão x OpenAPI (CA9)", () => {
  const openapi = parse(readFileSync(join(process.cwd(), "../docs/openapi.yaml"), "utf8"));
  const call = (path: string, body?: unknown) =>
    fetch(`/api/v1${path}`, body ? { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body) } : undefined);

  it("sessão sem login responde o 401 do contrato; login 200 traz SessionView; depois a sessão é 200", async () => {
    expect(Object.keys(openapi.paths["/auth/session"].get.responses)).toEqual(expect.arrayContaining(["200", "401"]));
    const anon = await call("/auth/session");
    expect(anon.status).toBe(401);
    const err = await anon.json();
    for (const k of openapi.components.schemas.Error.required) expect(err).toHaveProperty(k);

    const ok = await call("/auth/login", { email: "a@b.c", password: "x" });
    expect(ok.status).toBe(200);
    const view = await ok.json();
    for (const k of openapi.components.schemas.SessionView.required) expect(view).toHaveProperty(k);
    expect((await call("/auth/session")).status).toBe(200);
  });

  it("login responde 401 e 429 declarados no contrato", async () => {
    const declared = Object.keys(openapi.paths["/auth/login"].post.responses);
    for (const [password, status, code] of [["errada", 401, "INVALID_CREDENTIALS"], ["limite", 429, "TOO_MANY_REQUESTS"]] as const) {
      const res = await call("/auth/login", { email: "a@b.c", password });
      expect(res.status).toBe(status);
      expect(declared).toContain(String(status));
      expect((await res.json()).code).toBe(code);
    }
  });
});
