import { readFileSync } from "node:fs";
import { join } from "node:path";
import { parse } from "yaml";

const openapi = parse(readFileSync(join(process.cwd(), "../docs/openapi.yaml"), "utf8"));
const json = { "Content-Type": "application/json" };
const login = async (email: string, linkId?: string) => {
  await fetch("/api/v1/auth/login", { method: "POST", headers: json, body: JSON.stringify({ email, password: "segredo123" }) });
  if (linkId) await fetch("/api/v1/auth/session/link", { method: "PUT", headers: json, body: JSON.stringify({ linkId }) });
};
const send = (method: string, path: string) => fetch(`/api/v1${path}`, { method, headers: json });
const ADMIN_LINK = "00000000-0000-0000-0000-000000000011";
const STUDENT_LINK = "00000000-0000-0000-0000-000000000012";
const TEACHER_LINK = "00000000-0000-0000-0000-000000000013";
const MISSING = "00000000-0000-0000-0000-0000000000de";
const SENT_CLASS = "00000000-0000-0000-0000-000000000a01";
const SENT_ALL = "00000000-0000-0000-0000-000000000a02";
const SCHEDULED_UNIT = "00000000-0000-0000-0000-000000000a03";
const SCHEDULED_TEACHER = "00000000-0000-0000-0000-000000000a04";
const CLASS = "00000000-0000-0000-0000-000000000601";
const titles = async (query = "") => ((await (await send("GET", `/announcements${query}`)).json()).items as { title: string }[]).map((i) => i.title);

describe("comunicados x OpenAPI (RF-COM-01, #81, CA13)", () => {
  it("o contrato declara as duas rotas, os esquemas e o código de erro", () => {
    expect(Object.keys(openapi.paths["/announcements"].get.responses)).toEqual(expect.arrayContaining(["200", "400", "401", "403", "404"]));
    expect(Object.keys(openapi.paths["/announcements/{id}"].delete.responses)).toEqual(expect.arrayContaining(["204", "401", "403", "404", "409"]));
    expect(openapi.components.schemas.AnnouncementType.enum).toHaveLength(5);
    expect(openapi.components.schemas.AnnouncementItem.required).toEqual(expect.arrayContaining(["target", "status", "recipients", "reads"]));
    expect(openapi.components.schemas.Error.properties.code.enum).toContain("ANNOUNCEMENT_ALREADY_SENT");
  });

  it("sem sessão 401; Aluno 403 nas duas rotas; filtros inválidos 400; unidade desconhecida 404", async () => {
    expect((await send("GET", "/announcements")).status).toBe(401);
    expect((await send("DELETE", `/announcements/${SCHEDULED_UNIT}`)).status).toBe(401);
    await login("varios@exemplo.com", STUDENT_LINK);
    expect((await send("GET", "/announcements")).status).toBe(403);
    expect((await send("DELETE", `/announcements/${SCHEDULED_UNIT}`)).status).toBe(403);
    await login("ana@exemplo.com");
    for (const bad of ["?type=OTHER", "?from=2026-10-11&to=2026-10-10", "?from=ontem", "?sort=body,asc", "?size=10", "?classId=x"]) {
      expect((await send("GET", `/announcements${bad}`)).status, bad).toBe(400);
    }
    expect((await send("GET", `/announcements?unitId=${MISSING}`)).status).toBe(404);
  });

  it("o Coordenador vê tudo, do mais recente ao mais antigo, com recipients nulo e reads 0 no agendado", async () => {
    await login("ana@exemplo.com");
    const body = await (await send("GET", "/announcements")).json();
    expect(body.total).toBe(4);
    expect(body.items.map((i: { id: string }) => i.id)).toEqual([SCHEDULED_TEACHER, SCHEDULED_UNIT, SENT_ALL, SENT_CLASS]);
    expect(body.items[1]).toMatchObject({ status: "SCHEDULED", recipients: null, reads: 0, target: { kind: "UNIT", name: "Unidade Centro" } });
    expect(body.items[2]).toMatchObject({ status: "SENT", recipients: 40, reads: 18, target: { kind: "ORGANIZATION", id: null, name: null } });
    expect(body.items[3]).toMatchObject({ author: "Ana", type: "CANCELED_CLASS", target: { kind: "CLASS", name: "Jiu-Jitsu Adulto" } });
    expect(await titles("?sort=title,asc")).toEqual(["Aula de sexta cancelada", "Novo horário a partir de novembro", "Professor substituto", "Recesso de fim de ano"]);
  });

  it("filtra por tipo, turma e período (na data da unidade, inclusive nos extremos)", async () => {
    await login("ana@exemplo.com");
    expect(await titles("?type=GENERAL")).toEqual(["Recesso de fim de ano"]);
    expect(await titles(`?classId=${CLASS}`)).toEqual(["Professor substituto", "Aula de sexta cancelada"]);
    expect(await titles("?from=2026-10-20&to=2026-10-22")).toEqual(["Recesso de fim de ano", "Aula de sexta cancelada"]);
    expect(await titles("?from=2026-10-21&to=2026-10-21")).toEqual([]);
    expect(await titles("?from=2099-01-01")).toEqual(["Professor substituto", "Novo horário a partir de novembro"]);
  });

  it("o Administrador não vê o de todas as unidades; o Professor vê o que enviou e o da turma dele", async () => {
    await login("varios@exemplo.com", ADMIN_LINK);
    expect(await titles()).toEqual(["Professor substituto", "Novo horário a partir de novembro", "Aula de sexta cancelada"]);
    await login("varios@exemplo.com", TEACHER_LINK);
    expect(await titles()).toEqual(["Professor substituto", "Aula de sexta cancelada"]);
  });

  it("cancela o agendado (204), o enviado dá 409, repetir dá 404; o Professor só cancela o que ele agendou", async () => {
    await login("ana@exemplo.com");
    const sent = await send("DELETE", `/announcements/${SENT_CLASS}`);
    expect(sent.status).toBe(409);
    expect((await sent.json()).code).toBe("ANNOUNCEMENT_ALREADY_SENT");
    expect((await send("DELETE", `/announcements/${MISSING}`)).status).toBe(404);
    expect((await send("DELETE", `/announcements/${SCHEDULED_UNIT}`)).status).toBe(204);
    expect((await send("DELETE", `/announcements/${SCHEDULED_UNIT}`)).status).toBe(404);
    expect(await titles()).not.toContain("Novo horário a partir de novembro");
    await login("varios@exemplo.com", TEACHER_LINK);
    expect((await send("DELETE", `/announcements/${SENT_ALL}`)).status).toBe(403);
    expect((await send("DELETE", `/announcements/${SCHEDULED_TEACHER}`)).status).toBe(204);
  });
});
