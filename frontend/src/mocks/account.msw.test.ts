import { readFileSync } from "node:fs";
import { join } from "node:path";
import { parse } from "yaml";

const openapi = parse(readFileSync(join(process.cwd(), "../docs/openapi.yaml"), "utf8"));
const send = (method: string, path: string, body?: unknown) =>
  fetch(`/api/v1${path}`, { method, headers: { "Content-Type": "application/json" }, body: body ? JSON.stringify(body) : undefined });
const declared = (path: string, method: string) => Object.keys(openapi.paths[path][method].responses);
const CURRENT = "00000000-0000-0000-0000-000000000031";
const OTHER = "00000000-0000-0000-0000-000000000032";

describe("minha conta x OpenAPI (CA17)", () => {
  it("sem sessão as rotas novas respondem 401 declarado", async () => {
    for (const [method, path] of [["GET", "/auth/sessions"], ["DELETE", "/auth/sessions"], ["DELETE", `/auth/sessions/${OTHER}`], ["POST", "/auth/password"]] as const) {
      expect((await send(method, path, method === "POST" ? {} : undefined)).status).toBe(401);
    }
    expect(declared("/auth/sessions", "get")).toContain("401");
  });

  it("lista de sessões tem o formato do contrato; encerrar a atual é 409, outra conta é 404, outra sessão é 204", async () => {
    await send("POST", "/auth/login", { email: "a@b.c", password: "x" });
    const list = await (await send("GET", "/auth/sessions")).json();
    for (const item of list) for (const k of openapi.components.schemas.AccountSession.required) expect(item).toHaveProperty(k);
    expect(list.filter((s: { current: boolean }) => s.current)).toHaveLength(1);
    expect(JSON.stringify(list)).not.toMatch(/token|hash|"ip"/);

    const current = await send("DELETE", `/auth/sessions/${CURRENT}`);
    expect(current.status).toBe(409);
    expect((await current.json()).code).toBe("SESSION_CURRENT");
    expect((await send("DELETE", "/auth/sessions/00000000-0000-0000-0000-0000000000ff")).status).toBe(404);
    expect(declared("/auth/sessions/{id}", "delete")).toEqual(expect.arrayContaining(["204", "401", "404", "409"]));
    expect((await send("DELETE", `/auth/sessions/${OTHER}`)).status).toBe(204);
    expect(await (await send("GET", "/auth/sessions")).json()).toHaveLength(1);
  });

  it("encerrar as outras mantém só a atual", async () => {
    await send("POST", "/auth/login", { email: "a@b.c", password: "x" });
    expect((await send("DELETE", "/auth/sessions")).status).toBe(204);
    const list = await (await send("GET", "/auth/sessions")).json();
    expect(list).toHaveLength(1);
    expect(list[0].current).toBe(true);
  });

  it("troca de senha: erros no campo, fraca, conhecida, limite e sucesso encerram as outras sessões", async () => {
    await send("POST", "/auth/login", { email: "a@b.c", password: "x" });
    const wrong = await send("POST", "/auth/password", { currentPassword: "errada", newPassword: "Novasenha1" });
    expect(wrong.status).toBe(400);
    expect((await wrong.json()).fields[0].field).toBe("currentPassword");
    expect((await send("POST", "/auth/password", { currentPassword: "limite", newPassword: "Novasenha1" })).status).toBe(429);
    expect((await (await send("POST", "/auth/password", { currentPassword: "x", newPassword: "fraca" })).json()).code).toBe("PASSWORD_WEAK");
    expect((await (await send("POST", "/auth/password", { currentPassword: "x", newPassword: "Password1" })).json()).code).toBe("PASSWORD_LEAKED");
    expect((await send("POST", "/auth/password", { currentPassword: "x", newPassword: "Novasenha1" })).status).toBe(204);
    expect(await (await send("GET", "/auth/sessions")).json()).toHaveLength(1);
    expect(declared("/auth/password", "post")).toEqual(expect.arrayContaining(["204", "400", "401"]));
  });

  it("troca de e-mail: 202, validações, senha, código das duas etapas e confirmação pelo link", async () => {
    await send("POST", "/auth/login", { email: "a@b.c", password: "x" });
    expect((await send("POST", "/auth/email-change", { newEmail: "outro@exemplo.com", password: "x" })).status).toBe(202);
    expect((await send("POST", "/auth/email-change", { newEmail: "sem-arroba", password: "x" })).status).toBe(400);
    expect((await send("POST", "/auth/email-change", { newEmail: "ANA@exemplo.com", password: "x" })).status).toBe(400);
    expect((await send("POST", "/auth/email-change", { newEmail: "outro@exemplo.com", password: "errada" })).status).toBe(401);
    expect((await send("POST", "/auth/email-change", { newEmail: "outro@exemplo.com", password: "limite" })).status).toBe(429);
    expect(declared("/auth/email-change", "post")).toEqual(expect.arrayContaining(["202", "400", "401", "429"]));

    const gone = await send("POST", "/auth/email-change/vencido/confirm");
    expect(gone.status).toBe(410);
    expect((await gone.json()).code).toBe("EMAIL_CHANGE_INVALID");
    expect((await send("POST", "/auth/email-change/valido/confirm")).status).toBe(204);
    expect((await (await send("GET", "/auth/session")).json()).account.email).toBe("novo@exemplo.com");
    expect(declared("/auth/email-change/{code}/confirm", "post")).toEqual(expect.arrayContaining(["204", "401", "410"]));
  });

  it("com as duas etapas ligadas o pedido de e-mail exige o código", async () => {
    const first = await (await send("POST", "/auth/login", { email: "duas@exemplo.com", password: "x" })).json();
    await send("POST", "/auth/login/two-factor", { ticket: first.ticket, code: "123456" });
    const body = { newEmail: "outro@exemplo.com", password: "x" };
    expect((await (await send("POST", "/auth/email-change", body)).json()).code).toBe("INVALID_TWO_FACTOR_CODE");
    expect((await send("POST", "/auth/email-change", { ...body, code: "123456" })).status).toBe(202);
  });
});
