import { readFileSync } from "node:fs";
import { join } from "node:path";
import { parse } from "yaml";

const openapi = parse(readFileSync(join(process.cwd(), "../docs/openapi.yaml"), "utf8"));
const json = { "Content-Type": "application/json" };
const login = async (email: string, linkId?: string) => {
  await fetch("/api/v1/auth/login", { method: "POST", headers: json, body: JSON.stringify({ email, password: "segredo123" }) });
  if (linkId) await fetch("/api/v1/auth/session/link", { method: "PUT", headers: json, body: JSON.stringify({ linkId }) });
};
const send = (method: string, path: string, body?: unknown) => fetch(`/api/v1${path}`, { method, headers: json, body: body === undefined ? undefined : JSON.stringify(body) });
const ANA = "00000000-0000-0000-0000-000000000701"; // benefício ativo semeado
const ENDED = "00000000-0000-0000-0000-000000000705";

describe("encerrar benefício x OpenAPI (RF-BEN-03, CA12)", () => {
  it("o contrato declara a rota e o esquema", () => {
    expect(Object.keys(openapi.paths["/benefits/{id}/end"].post.responses)).toEqual(expect.arrayContaining(["200", "400", "401", "403", "404", "409"]));
    expect(openapi.components.schemas.BenefitEndInput.required).toEqual(["reason"]);
    expect(openapi.components.schemas.BenefitInput.allOf[1].properties.teacherId).toBeDefined();
  });

  it("sem sessão 401; Professor 403", async () => {
    expect((await send("POST", `/benefits/${ANA}/end`, { reason: "Motivo" })).status).toBe(401);
    await login("varios@exemplo.com", "00000000-0000-0000-0000-000000000013");
    expect((await send("POST", `/benefits/${ANA}/end`, { reason: "Motivo" })).status).toBe(403);
  });

  it("400 no motivo, 404, 200 e depois 409", async () => {
    await login("ana@exemplo.com");
    for (const reason of ["", "x", "x".repeat(201)]) expect((await send("POST", `/benefits/${ANA}/end`, { reason })).status).toBe(400);
    expect((await send("POST", "/benefits/00000000-0000-0000-0000-00000000ffff/end", { reason: "Motivo" })).status).toBe(404);
    const ok = await send("POST", `/benefits/${ANA}/end`, { reason: "Mudou de situação" });
    expect(ok.status).toBe(200);
    expect((await ok.json()).status).toBe("ENDED");
    expect((await send("POST", `/benefits/${ANA}/end`, { reason: "Outra vez" })).status).toBe(409);
    expect((await send("POST", `/benefits/${ENDED}/end`, { reason: "Motivo" })).status).toBe(409);
  });
});
