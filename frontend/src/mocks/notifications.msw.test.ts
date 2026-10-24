import { readFileSync } from "node:fs";
import { join } from "node:path";
import { parse } from "yaml";

const openapi = parse(readFileSync(join(process.cwd(), "../docs/openapi.yaml"), "utf8"));
const json = { "Content-Type": "application/json" };
const login = async (email: string, linkId?: string) => {
  await fetch("/api/v1/auth/login", { method: "POST", headers: json, body: JSON.stringify({ email, password: "segredo123" }) });
  if (linkId) await fetch("/api/v1/auth/session/link", { method: "PUT", headers: json, body: JSON.stringify({ linkId }) });
};
const list = (query = "") => fetch(`/api/v1/notifications${query}`);
const put = (body: unknown) => fetch("/api/v1/notifications/preferences", { method: "PUT", headers: json, body: JSON.stringify(body) });
const declared = (path: string, method: string) => Object.keys(openapi.paths[path][method].responses);
const required = (name: string) => [...openapi.components.schemas[name].required].sort();
// "varios@exemplo.com": vínculos 11 (Administrador), 12 (Aluno), 13 (Professor de outra organização).
const ADMIN_LINK = "00000000-0000-0000-0000-000000000011";
const STUDENT_LINK = "00000000-0000-0000-0000-000000000012";
const TEACHER_LINK = "00000000-0000-0000-0000-000000000013";

describe("notificações x OpenAPI (RF-NOT-01, CA18)", () => {
  it("sem sessão as cinco rotas respondem 401, declarado no contrato", async () => {
    expect((await list()).status).toBe(401);
    expect((await fetch("/api/v1/notifications/unread-count")).status).toBe(401);
    expect((await fetch("/api/v1/notifications/read-all", { method: "POST" })).status).toBe(401);
    expect((await fetch("/api/v1/notifications/preferences")).status).toBe(401);
    expect((await put({ receiveAll: true })).status).toBe(401);
    for (const [path, method] of [["/notifications", "get"], ["/notifications/unread-count", "get"], ["/notifications/read-all", "post"], ["/notifications/preferences", "get"], ["/notifications/preferences", "put"]]) {
      expect(declared(path, method)).toContain("401");
    }
  });

  it("lista no formato do contrato: grupos, ordem do mais novo, janela de 7 dias e contador de qualquer data", async () => {
    await login("ana@exemplo.com");
    const body = await (await list()).json();
    expect(Object.keys(body).sort()).toEqual(required("NotificationList"));
    expect(body.items).toHaveLength(4);
    expect(Object.keys(body.items[0]).sort()).toEqual(required("Notification"));
    expect(body.items.map((n: { group: string }) => n.group)).toEqual(["TODAY", "TODAY", "WEEK", "WEEK"]);
    const dates = body.items.map((n: { createdAt: string }) => n.createdAt);
    expect([...dates].sort().reverse()).toEqual(dates);
    expect(body.items.every((n: { type: string }) => openapi.components.schemas.NotificationType.enum.includes(n.type))).toBe(true);
    expect(body.unreadCount).toBe(4); // a de 9 dias atrás conta, embora não apareça
    const unread = await (await list("?filter=unread")).json();
    expect(unread.items).toHaveLength(3);
    expect(unread.items.every((n: { readAt: unknown }) => n.readAt === null)).toBe(true);
    expect((await list("?filter=outro")).status).toBe(400);
    expect(declared("/notifications", "get")).toContain("400");
  });

  it("contador e read-all: marca, devolve quantas mudaram e repetir devolve 0", async () => {
    await login("ana@exemplo.com");
    expect(await (await fetch("/api/v1/notifications/unread-count")).json()).toEqual({ count: 4 });
    const first = await fetch("/api/v1/notifications/read-all", { method: "POST" });
    expect(first.status).toBe(200);
    expect(await first.json()).toEqual({ changed: 4 });
    expect(await (await fetch("/api/v1/notifications/unread-count")).json()).toEqual({ count: 0 });
    expect(await (await fetch("/api/v1/notifications/read-all", { method: "POST" })).json()).toEqual({ changed: 0 });
    const after = await (await list()).json();
    expect(after.items.every((n: { readAt: unknown }) => n.readAt !== null)).toBe(true);
  });

  it("preferência: o Coordenador liga e desliga; valor que não é booleano dá 422", async () => {
    await login("ana@exemplo.com");
    const initial = await (await fetch("/api/v1/notifications/preferences")).json();
    expect(Object.keys(initial).sort()).toEqual(required("NotificationPreferences"));
    expect(initial).toEqual({ receiveAll: false, canReceiveAll: true });
    expect(await (await put({ receiveAll: true })).json()).toEqual({ receiveAll: true, canReceiveAll: true });
    expect((await (await fetch("/api/v1/notifications/preferences")).json()).receiveAll).toBe(true);
    expect((await put({ receiveAll: "sim" })).status).toBe(422);
    expect((await put({})).status).toBe(422);
    expect(declared("/notifications/preferences", "put")).toEqual(expect.arrayContaining(["403", "422"]));
  });

  it("Administrador lê, vê canReceiveAll falso e não altera (403); Professor tem lista vazia; portal 403", async () => {
    await login("varios@exemplo.com", ADMIN_LINK);
    expect(await (await fetch("/api/v1/notifications/preferences")).json()).toEqual({ receiveAll: false, canReceiveAll: false });
    expect((await put({ receiveAll: true })).status).toBe(403);
    expect((await list()).status).toBe(200);
    await login("varios@exemplo.com", TEACHER_LINK);
    expect(await (await list()).json()).toEqual({ items: [], unreadCount: 0 });
    await login("varios@exemplo.com", STUDENT_LINK);
    for (const url of ["/api/v1/notifications", "/api/v1/notifications/unread-count", "/api/v1/notifications/preferences"]) {
      expect((await fetch(url)).status).toBe(403);
    }
    expect((await fetch("/api/v1/notifications/read-all", { method: "POST" })).status).toBe(403);
    expect(declared("/notifications", "get")).toContain("403");
  });
});
