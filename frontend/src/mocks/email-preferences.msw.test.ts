import { readFileSync } from "node:fs";
import { join } from "node:path";
import { parse } from "yaml";

const openapi = parse(readFileSync(join(process.cwd(), "../docs/openapi.yaml"), "utf8"));
const send = (method: string, path: string, body?: unknown) =>
  fetch(`/api/v1${path}`, { method, headers: { "Content-Type": "application/json" }, body: body ? JSON.stringify(body) : undefined });
const STUDENT_LINK = "00000000-0000-0000-0000-000000000012";

describe("preferência de e-mail x OpenAPI (CA17)", () => {
  it("o contrato declara GET e PUT com 200, 401, 403 e (no PUT) 422", () => {
    const path = openapi.paths["/auth/email-preferences"];
    expect(Object.keys(path.get.responses)).toEqual(expect.arrayContaining(["200", "401", "403"]));
    expect(Object.keys(path.put.responses)).toEqual(expect.arrayContaining(["200", "401", "403", "422"]));
  });

  it("sem sessão 401; gestão 403; portal lê ligada, desliga e liga", async () => {
    expect((await send("GET", "/auth/email-preferences")).status).toBe(401);
    expect((await send("PUT", "/auth/email-preferences", { announcements: false })).status).toBe(401);

    await send("POST", "/auth/login", { email: "varios@exemplo.com", password: "x" });
    await send("PUT", "/auth/session/link", { linkId: "00000000-0000-0000-0000-000000000011" });
    expect((await send("GET", "/auth/email-preferences")).status).toBe(403);
    expect((await send("PUT", "/auth/email-preferences", { announcements: false })).status).toBe(403);

    expect((await send("PUT", "/auth/session/link", { linkId: STUDENT_LINK })).status).toBe(200);
    expect(await (await send("GET", "/auth/email-preferences")).json()).toEqual({ announcements: true });
    expect(await (await send("PUT", "/auth/email-preferences", { announcements: false })).json()).toEqual({ announcements: false });
    expect(await (await send("GET", "/auth/email-preferences")).json()).toEqual({ announcements: false });
    expect((await send("PUT", "/auth/email-preferences", { announcements: "nao" })).status).toBe(422);
  });
});
