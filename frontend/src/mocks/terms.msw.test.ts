import { readFileSync } from "node:fs";
import { join } from "node:path";
import { parse } from "yaml";

const openapi = parse(readFileSync(join(process.cwd(), "../docs/openapi.yaml"), "utf8"));
const json = { "Content-Type": "application/json" };
const login = async (email: string, linkId?: string) => {
  await fetch("/api/v1/auth/login", { method: "POST", headers: json, body: JSON.stringify({ email, password: "segredo123" }) });
  if (linkId) await fetch("/api/v1/auth/session/link", { method: "PUT", headers: json, body: JSON.stringify({ linkId }) });
};
const read = (kind: string) => fetch(`/api/v1/organization/terms/${kind}`);
const publish = (kind: string, body: unknown) =>
  fetch(`/api/v1/organization/terms/${kind}/versions`, { method: "POST", headers: json, body: JSON.stringify(body) });
const declared = (path: string, method: string) => Object.keys(openapi.paths[path][method].responses);
const TEXT = "Texto do termo com mais de vinte caracteres.";

describe("termos x OpenAPI (RF-MAT-05, CA11)", () => {
  it("sem sessão as duas rotas respondem 401, declarado no contrato", async () => {
    expect((await read("ENROLLMENT")).status).toBe(401);
    expect((await publish("ENROLLMENT", { body: TEXT, reaccept: false })).status).toBe(401);
    expect(declared("/organization/terms/{kind}", "get")).toContain("401");
    expect(declared("/organization/terms/{kind}/versions", "post")).toContain("401");
  });

  it("Coordenador: lê o modelo como versão 0, publica 1 e 2 no formato do contrato e recebe 400 e 404 declarados", async () => {
    await login("ana@exemplo.com");
    const first = await (await read("ENROLLMENT")).json();
    expect(Object.keys(first).sort()).toEqual([...openapi.components.schemas.TermsView.required].sort());
    expect(first.canManage).toBe(true);
    expect(first.current.version).toBe(0);
    expect(first.versions).toEqual([]);
    expect(Object.keys(first.current).sort()).toEqual([...openapi.components.schemas.TermsVersion.required].sort());

    const created = await publish("ENROLLMENT", { body: TEXT, reaccept: false });
    expect(created.status).toBe(201);
    const v1 = await created.json();
    expect(Object.keys(v1).sort()).toEqual([...openapi.components.schemas.TermsVersion.required].sort());
    expect(v1.version).toBe(1);
    expect((await publish("ENROLLMENT", { body: `${TEXT} Mais.`, reaccept: true })).status).toBe(201);
    const view = await (await read("ENROLLMENT")).json();
    expect(view.current.version).toBe(2);
    expect(view.versions.map((v: { version: number }) => v.version)).toEqual([2, 1]);
    expect((await (await read("IMAGE_USE")).json()).current.version).toBe(0);

    expect((await publish("ENROLLMENT", { body: "curto", reaccept: true })).status).toBe(400);
    expect((await publish("ENROLLMENT", { body: `${TEXT} Outro.` })).status).toBe(400);
    expect((await publish("ENROLLMENT", { body: `${TEXT} Mais.`, reaccept: true })).status).toBe(400);
    expect((await read("OUTRO")).status).toBe(404);
    for (const status of ["201", "400", "403", "404", "409"]) expect(declared("/organization/terms/{kind}/versions", "post")).toContain(status);
    for (const status of ["200", "403", "404"]) expect(declared("/organization/terms/{kind}", "get")).toContain(status);
  });

  it("Administrador lê com canManage falso e recebe 403 ao publicar; Professor recebe 403 em tudo", async () => {
    await login("varios@exemplo.com", "00000000-0000-0000-0000-000000000011");
    expect((await (await read("ENROLLMENT")).json()).canManage).toBe(false);
    expect((await publish("ENROLLMENT", { body: TEXT, reaccept: false })).status).toBe(403);
    await fetch("/api/v1/auth/session/link", { method: "PUT", headers: json, body: JSON.stringify({ linkId: "00000000-0000-0000-0000-000000000012" }) });
    expect((await read("ENROLLMENT")).status).toBe(403);
  });
});
