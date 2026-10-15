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
