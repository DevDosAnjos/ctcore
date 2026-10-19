import { readFileSync } from "node:fs";
import { join } from "node:path";
import { parse } from "yaml";

const openapi = parse(readFileSync(join(process.cwd(), "../docs/openapi.yaml"), "utf8"));
const json = { "Content-Type": "application/json" };
const login = async (email: string, linkId?: string) => {
  await fetch("/api/v1/auth/login", { method: "POST", headers: json, body: JSON.stringify({ email, password: "segredo123" }) });
  if (linkId) await fetch("/api/v1/auth/session/link", { method: "PUT", headers: json, body: JSON.stringify({ linkId }) });
};
const list = (query = "") => fetch(`/api/v1/classes${query}`);
const names = (data: { items: { name: string }[] }) => data.items.map((i) => i.name);

describe("turmas x OpenAPI (RF-TUR-01, CA11)", () => {
  it("sem sessão responde 401, declarado no contrato", async () => {
    expect((await list()).status).toBe(401);
    expect(Object.keys(openapi.paths["/classes"].get.responses)).toEqual(expect.arrayContaining(["200", "400", "401", "403"]));
  });

  it("Coordenador: envelope e linha no formato do contrato, sem link do grupo, ordenadas por nome", async () => {
    await login("ana@exemplo.com");
    const data = await (await list()).json();
    expect(Object.keys(data).sort()).toEqual(["items", "page", "size", "total"]);
    expect(data).toMatchObject({ page: 1, size: 25, total: 3 });
    expect(names(data)).toEqual(["Boxe Noturno", "Jiu-Jitsu Adulto", "Judô Infantil"]);
    const schema = openapi.components.schemas.ClassRow;
    expect(Object.keys(data.items[0]).sort()).toEqual([...schema.required].sort());
    expect(Object.keys(data.items[1].schedule[0]).sort()).toEqual([...schema.properties.schedule.items.required].sort());
    expect(Object.keys(data.items[1].ageRange).sort()).toEqual([...schema.properties.ageRange.required].sort());
    expect(data.items[0].ageRange).toBeNull();
    expect(JSON.stringify(data)).not.toMatch(/groupLink|email|phone/);
    expect(names(await (await list("?sort=name,desc")).json())[0]).toBe("Judô Infantil");
  });

  it("filtros q (sem acento), professor (titular e auxiliar), dia e situação combinam; valor inválido dá 400", async () => {
    await login("ana@exemplo.com");
    expect(names(await (await list("?q=JUDO")).json())).toEqual(["Judô Infantil"]);
    expect(names(await (await list("?weekday=2")).json())).toEqual(["Judô Infantil"]);
    expect(names(await (await list("?status=INACTIVE")).json())).toEqual(["Boxe Noturno"]);
    expect(names(await (await list("?teacherId=00000000-0000-0000-0000-000000000502")).json())).toEqual(["Jiu-Jitsu Adulto", "Judô Infantil"]);
    expect(names(await (await list("?teacherId=00000000-0000-0000-0000-000000000502&weekday=3")).json())).toEqual(["Jiu-Jitsu Adulto"]);
    expect((await list("?weekday=8")).status).toBe(400);
    expect((await list("?status=XYZ")).status).toBe(400);
    expect((await list("?size=10")).status).toBe(400);
    expect((await (await list("?page=2")).json()).items).toEqual([]);
  });

  it("Administrador só vê as turmas da unidade dele; Professor e portal recebem 403", async () => {
    await login("varios@exemplo.com", "00000000-0000-0000-0000-000000000011");
    expect(names(await (await list()).json())).toEqual(["Jiu-Jitsu Adulto", "Judô Infantil"]);
    expect(names(await (await list("?unitId=00000000-0000-0000-0000-000000000022")).json())).toEqual([]);
    await login("varios@exemplo.com", "00000000-0000-0000-0000-000000000013");
    expect((await list()).status).toBe(403);
    await login("varios@exemplo.com", "00000000-0000-0000-0000-000000000012");
    expect((await list()).status).toBe(403);
  });
});

const JJ = "00000000-0000-0000-0000-000000000601";
const JUDO = "00000000-0000-0000-0000-000000000602";
const BOXE = "00000000-0000-0000-0000-000000000603";
const detail = (id: string) => fetch(`/api/v1/classes/${id}`);
const deactivate = (id: string) => fetch(`/api/v1/classes/${id}/deactivate`, { method: "POST" });

describe("painel da turma x OpenAPI (RF-TUR-02, CA10)", () => {
  it("sem sessão 401; contrato declara as duas rotas e o código novo", async () => {
    expect((await detail(JJ)).status).toBe(401);
    expect(Object.keys(openapi.paths["/classes/{id}"].get.responses)).toEqual(expect.arrayContaining(["200", "401", "403", "404"]));
    expect(Object.keys(openapi.paths["/classes/{id}/deactivate"].post.responses)).toEqual(expect.arrayContaining(["200", "401", "403", "404", "409"]));
    expect(openapi.components.schemas.Error.properties.code.enum).toContain("HAS_PRIMARY_STUDENTS");
  });

  it("Coordenador: detalhe no formato do contrato, com link só no detalhe e alunos vazios", async () => {
    await login("ana@exemplo.com");
    const data = await (await detail(JJ)).json();
    const schema = openapi.components.schemas.ClassDetail;
    const required = [...openapi.components.schemas.ClassRow.required, ...schema.allOf[1].required].sort();
    expect(Object.keys(data).sort()).toEqual(required);
    expect(data).toMatchObject({ name: "Jiu-Jitsu Adulto", groupLink: "https://chat.example.com/jiu-jitsu-adulto", nextClass: null, students: [] });
    expect(data.deactivation).toEqual({ allowed: true, blockingStudents: 0 });
    expect((await (await detail(JUDO)).json()).groupLink).toBeNull();
    expect((await detail("00000000-0000-0000-0000-0000000009ff")).status).toBe(404);
  });

  it("desativar: ok, repetido 409, com aluno principal 409 HAS_PRIMARY_STUDENTS com a contagem", async () => {
    await login("ana@exemplo.com");
    const blocked = await deactivate(JUDO);
    expect(blocked.status).toBe(409);
    expect(await blocked.json()).toMatchObject({ code: "HAS_PRIMARY_STUDENTS", fields: [{ field: "primaryStudents", message: "2" }] });
    expect((await (await detail(JUDO)).json()).deactivation).toEqual({ allowed: false, blockingStudents: 2 });
    const ok = await deactivate(JJ);
    expect(ok.status).toBe(200);
    expect(await ok.json()).toMatchObject({ status: "INACTIVE", deactivation: { allowed: false } });
    expect((await deactivate(JJ)).status).toBe(409);
    expect((await deactivate(BOXE)).status).toBe(409);
  });

  it("Administrador só abre turma da unidade dele (404 fora); Professor e portal 403", async () => {
    await login("varios@exemplo.com", "00000000-0000-0000-0000-000000000011");
    expect((await detail(JJ)).status).toBe(200);
    expect((await detail(BOXE)).status).toBe(404);
    expect((await deactivate(BOXE)).status).toBe(404);
    await login("varios@exemplo.com", "00000000-0000-0000-0000-000000000013");
    expect((await detail(JJ)).status).toBe(403);
    expect((await deactivate(JJ)).status).toBe(403);
  });
});

const UNIT_CENTRO = "00000000-0000-0000-0000-000000000021";
const JOAO = "00000000-0000-0000-0000-000000000502";
const send = (method: string, url: string, body: unknown) => fetch(`/api/v1${url}`, { method, headers: json, body: JSON.stringify(body) });
const ok = (over: Record<string, unknown> = {}) => ({
  unitId: UNIT_CENTRO,
  name: "Muay Thai Noite",
  modalityId: "",
  schedule: [{ weekday: 5, startsAt: "20:00", endsAt: "21:00" }],
  headTeacherId: JOAO,
  ...over,
});

describe("janela de turma x OpenAPI (RF-TUR-03, CA14)", () => {
  it("contrato declara as três rotas e o esquema de entrada", () => {
    expect(Object.keys(openapi.paths["/classes"].post.responses)).toEqual(expect.arrayContaining(["201", "400", "401", "403", "409"]));
    expect(Object.keys(openapi.paths["/classes/{id}"].put.responses)).toEqual(expect.arrayContaining(["200", "400", "401", "403", "404", "409"]));
    expect(Object.keys(openapi.paths["/classes/options"].get.responses)).toEqual(expect.arrayContaining(["200", "401", "403"]));
    expect(openapi.components.schemas.ClassInput.required).toEqual(["name", "modalityId", "schedule", "headTeacherId"]);
  });

  it("opções: formato do contrato; Administrador só vê a unidade dele; Professor 403", async () => {
    await login("ana@exemplo.com");
    const all = await (await fetch("/api/v1/classes/options")).json();
    expect(Object.keys(all).sort()).toEqual([...openapi.components.schemas.ClassOptions.required].sort());
    expect(Object.keys(all.units[0]).sort()).toEqual([...openapi.components.schemas.ClassOptions.properties.units.items.required].sort());
    expect(all.units.map((u: { id: string }) => u.id)).toEqual([UNIT_CENTRO]);
    expect(all.units[0].teachers.map((t: { id: string }) => t.id)).not.toContain("00000000-0000-0000-0000-000000000503");
    await login("varios@exemplo.com", "00000000-0000-0000-0000-000000000011");
    expect((await (await fetch("/api/v1/classes/options")).json()).units).toHaveLength(1);
    await login("varios@exemplo.com", "00000000-0000-0000-0000-000000000013");
    expect((await fetch("/api/v1/classes/options")).status).toBe(403);
  });

  it("cria: 201 no formato do detalhe e aparece na lista; erro de campo 400; nome repetido 409 no campo name", async () => {
    await login("ana@exemplo.com");
    const options = await (await fetch("/api/v1/classes/options")).json();
    const modalityId = options.units[0].modalities[0].id;
    const created = await send("POST", "/classes", ok({ modalityId, capacity: 10, groupLink: "https://x.example.com/g" }));
    expect(created.status).toBe(201);
    expect(await created.json()).toMatchObject({ name: "Muay Thai Noite", status: "ACTIVE", capacity: 10, groupLink: "https://x.example.com/g" });
    expect(names(await (await list()).json())).toContain("Muay Thai Noite");
    const dup = await send("POST", "/classes", ok({ modalityId, name: "muay thai NOITE" }));
    expect(dup.status).toBe(409);
    expect((await dup.json()).fields[0].field).toBe("name");
    for (const [over, field] of [[{ name: "A" }, "name"], [{ schedule: [] }, "schedule"], [{ capacity: 0 }, "capacity"], [{ groupLink: "javascript:x" }, "groupLink"], [{ headTeacherId: undefined }, "headTeacherId"], [{ headTeacherId: "00000000-0000-0000-0000-000000000503" }, "headTeacherId"]] as const) {
      const bad = await send("POST", "/classes", ok({ modalityId, name: "Outra", ...over }));
      expect(bad.status).toBe(400);
      expect((await bad.json()).fields[0].field).toBe(field);
    }
  });

  it("edita: troca campos, scheduleChanged só quando os horários mudam, desativada 409, Administrador fora do alcance 404/403", async () => {
    await login("ana@exemplo.com");
    const modalityId = (await (await fetch("/api/v1/classes/options")).json()).units[0].modalities[0].id;
    const same = await send("PUT", `/classes/${JJ}`, ok({ modalityId, name: "Jiu-Jitsu Adulto", schedule: [{ weekday: 1, startsAt: "19:00", endsAt: "20:00" }, { weekday: 3, startsAt: "19:00", endsAt: "20:00" }] }));
    expect(same.status).toBe(200);
    expect(await same.json()).toMatchObject({ scheduleChanged: false, head: { id: JOAO }, unit: { id: UNIT_CENTRO } });
    const moved = await send("PUT", `/classes/${JJ}`, ok({ modalityId, name: "Jiu-Jitsu Adulto" }));
    expect(await moved.json()).toMatchObject({ scheduleChanged: true, schedule: [{ weekday: 5 }] });
    expect((await send("PUT", `/classes/${BOXE}`, ok({ modalityId }))).status).toBe(409);
    expect((await send("PUT", "/classes/00000000-0000-0000-0000-0000000009ff", ok({ modalityId }))).status).toBe(404);
    await login("varios@exemplo.com", "00000000-0000-0000-0000-000000000011");
    expect((await send("PUT", `/classes/${BOXE}`, ok({ modalityId }))).status).toBe(404);
    expect((await send("POST", "/classes", ok({ modalityId, unitId: "00000000-0000-0000-0000-000000000022" }))).status).toBe(403);
    await login("varios@exemplo.com", "00000000-0000-0000-0000-000000000013");
    expect((await send("POST", "/classes", ok({ modalityId }))).status).toBe(403);
  });
});
