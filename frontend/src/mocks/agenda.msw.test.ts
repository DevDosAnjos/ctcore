import { readFileSync } from "node:fs";
import { join } from "node:path";
import { parse } from "yaml";
import { addDays, mondayOf, todayIso } from "@/features/agenda/agendaDates";

const openapi = parse(readFileSync(join(process.cwd(), "../docs/openapi.yaml"), "utf8"));
const json = { "Content-Type": "application/json" };
const login = async (email: string, linkId?: string) => {
  await fetch("/api/v1/auth/login", { method: "POST", headers: json, body: JSON.stringify({ email, password: "segredo123" }) });
  if (linkId) await fetch("/api/v1/auth/session/link", { method: "PUT", headers: json, body: JSON.stringify({ linkId }) });
};
const week = () => `from=${mondayOf(todayIso())}&to=${addDays(mondayOf(todayIso()), 6)}`;
const get = (query: string) => fetch(`/api/v1/agenda?${query}`);

describe("agenda x OpenAPI (RF-AGE-01, CA17)", () => {
  it("sem sessão 401; contrato declara 200, 400, 401, 403 e 404", async () => {
    expect((await get(week())).status).toBe(401);
    expect(Object.keys(openapi.paths["/agenda"].get.responses)).toEqual(expect.arrayContaining(["200", "400", "401", "403", "404"]));
  });

  it("Coordenador: envelope e aula no formato do contrato, dia sem aula hoje e aulas de hoje canceladas", async () => {
    await login("ana@exemplo.com");
    const data = await (await get(week())).json();
    expect(Object.keys(data).sort()).toEqual([...openapi.components.schemas.Agenda.required].sort());
    expect(data.lessons.length).toBeGreaterThan(0);
    expect(Object.keys(data.lessons[0]).sort()).toEqual([...openapi.components.schemas.AgendaLesson.required].sort());
    expect(Object.keys(data.noClassDays[0]).sort()).toEqual([...openapi.components.schemas.NoClassDay.required].sort());
    expect(data.noClassDays[0].startsOn).toBe(todayIso());
    expect(data.lessons.filter((l: { date: string; status: string }) => l.date === todayIso()).every((l: { status: string }) => l.status === "CANCELLED")).toBe(true);
    const dates = data.lessons.map((l: { date: string; startsAt: string }) => l.date + l.startsAt);
    expect(dates).toEqual([...dates].sort());
    expect(JSON.stringify(data)).not.toMatch(/espera|presen|frequ/i);
  });

  it("filtros por professor e modalidade combinam; id desconhecido devolve lista vazia", async () => {
    await login("ana@exemplo.com");
    const all = await (await get(week())).json();
    const modality = all.lessons[0].modality.id;
    const only = await (await get(`${week()}&modalityId=${modality}`)).json();
    expect(only.lessons.length).toBeGreaterThan(0);
    expect(only.lessons.every((l: { modality: { id: string } }) => l.modality.id === modality)).toBe(true);
    expect((await (await get(`${week()}&teacherId=00000000-0000-0000-0000-0000000009ff`)).json()).lessons).toEqual([]);
  });

  it("parâmetros inválidos dão 400 com o campo; unidade fora do alcance 404; Administrador e Professor veem; portal 403", async () => {
    await login("ana@exemplo.com");
    for (const [query, field] of [["from=2026-13-01&to=2026-12-01", "from"], [`from=2026-03-10&to=2026-03-09`, "to"], [`from=2026-03-01&to=2026-04-01`, "to"], ["to=2026-03-01", "from"]]) {
      const res = await get(query);
      expect(res.status).toBe(400);
      expect((await res.json()).fields[0].field).toBe(field);
    }
    expect((await get(`${week()}&unitId=00000000-0000-0000-0000-0000000009ff`)).status).toBe(404);
    await login("varios@exemplo.com", "00000000-0000-0000-0000-000000000011");
    expect((await get(week())).status).toBe(200);
    expect((await get(`${week()}&unitId=00000000-0000-0000-0000-000000000022`)).status).toBe(404);
    await login("varios@exemplo.com", "00000000-0000-0000-0000-000000000013");
    expect((await get(week())).status).toBe(200);
    await login("varios@exemplo.com", "00000000-0000-0000-0000-000000000012");
    expect((await get(week())).status).toBe(403);
  });
});

describe("painel da aula x OpenAPI (RF-AGE-02)", () => {
  const lessonOf = async (
    pick: (l: { substitute: boolean }) => boolean = () => true,
  ) => {
    const all = (
      await (
        await get(
          `from=${addDays(todayIso(), 1)}&to=${addDays(todayIso(), 14)}`,
        )
      ).json()
    ).lessons;
    return all.find(pick);
  };

  it("sem sessão 401; contrato declara 200, 400, 401, 403 e 404", async () => {
    expect((await fetch("/api/v1/agenda/lessons/x")).status).toBe(401);
    expect(
      Object.keys(openapi.paths["/agenda/lessons/{id}"].get.responses),
    ).toEqual(expect.arrayContaining(["200", "400", "401", "403", "404"]));
  });

  it("Coordenador: corpo no formato do contrato; titular só na substituição; Professor sem canViewClass", async () => {
    await login("ana@exemplo.com");
    const plain = await lessonOf((l) => !l.substitute);
    const res = await fetch(`/api/v1/agenda/lessons/${plain.id}`);
    expect(res.status).toBe(200);
    const detail = await res.json();
    expect(Object.keys(detail).sort()).toEqual(
      [...openapi.components.schemas.LessonDetail.required].sort(),
    );
    expect(detail).toMatchObject({
      head: null,
      activeStudents: 0,
      trialClasses: 0,
      canViewClass: true,
    });
    expect(JSON.stringify(detail)).not.toMatch(/espera|presen|frequ/i);
    const sub = await lessonOf((l) => l.substitute);
    const swapped = await (
      await fetch(`/api/v1/agenda/lessons/${sub.id}`)
    ).json();
    expect(swapped.head).not.toBeNull();
    expect(swapped.head.id).not.toBe(swapped.teacher.id);
    await login("varios@exemplo.com", "00000000-0000-0000-0000-000000000013");
    expect(
      (await (await fetch(`/api/v1/agenda/lessons/${plain.id}`)).json())
        .canViewClass,
    ).toBe(false);
  });

  it("id desconhecido 404; portal 403", async () => {
    await login("ana@exemplo.com");
    expect((await fetch("/api/v1/agenda/lessons/inexistente")).status).toBe(
      404,
    );
    const known = await lessonOf();
    await login("varios@exemplo.com", "00000000-0000-0000-0000-000000000012");
    expect((await fetch(`/api/v1/agenda/lessons/${known.id}`)).status).toBe(
      403,
    );
  });
});

describe("editar e cancelar aula x OpenAPI (RF-AGE-03)", () => {
  const send = (method: string, path: string, body: unknown) =>
    fetch(`/api/v1/agenda/lessons/${path}`, { method, headers: json, body: JSON.stringify(body) });
  const future = async () => {
    const all = (await (await get(`from=${addDays(todayIso(), 1)}&to=${addDays(todayIso(), 14)}`)).json()).lessons;
    return all.find((l: { substitute: boolean }) => !l.substitute);
  };
  const edit = (l: { date: string; teacher: { id: string } }, over: object = {}) => ({
    scope: "THIS",
    date: l.date,
    startsAt: "06:00",
    endsAt: "07:00",
    teacherId: l.teacher.id,
    ...over,
  });

  it("contrato declara PUT e POST com 200, 400, 401, 403, 404, 409 e 422; sem sessão 401", async () => {
    for (const op of [openapi.paths["/agenda/lessons/{id}"].put, openapi.paths["/agenda/lessons/{id}/cancel"].post]) {
      expect(Object.keys(op.responses)).toEqual(expect.arrayContaining(["200", "400", "401", "403", "404", "409", "422"]));
    }
    expect((await send("PUT", "x", {})).status).toBe(401);
    expect((await send("POST", "x/cancel", {})).status).toBe(401);
  });

  it("editar: formato do contrato, horário novo, professor da substituição e validações", async () => {
    await login("ana@exemplo.com");
    const l = await future();
    const res = await send("PUT", l.id, edit(l));
    expect(res.status).toBe(200);
    const d = await res.json();
    expect(Object.keys(d).sort()).toEqual([...openapi.components.schemas.LessonDetail.required].sort());
    expect(d.startsAt).toBe("06:00");
    const other = (await (await get(week())).json()).teachers.find((t: { id: string }) => t.id !== l.teacher.id);
    const swapped = await (await send("PUT", l.id, edit(l, { teacherId: other.id }))).json();
    expect(swapped).toMatchObject({ substitute: true, teacher: { id: other.id } });
    expect(swapped.head).not.toBeNull();
    expect((await send("PUT", l.id, edit(l, { endsAt: "05:00" }))).status).toBe(400);
    expect((await send("PUT", l.id, edit(l, { scope: "ALL" }))).status).toBe(400);
    expect((await send("PUT", l.id, edit(l, { date: addDays(todayIso(), -3) }))).status).toBe(400);
    const bad = await send("PUT", l.id, edit(l, { teacherId: "00000000-0000-0000-0000-0000000009ff" }));
    expect(bad.status).toBe(422);
    expect((await bad.json()).fields[0].field).toBe("teacherId");
    expect((await send("PUT", "inexistente", edit(l))).status).toBe(404);
  });

  it("cancelar: motivo, texto só em Outro, 409 na repetição e o motivo aparece no painel e na lista", async () => {
    await login("ana@exemplo.com");
    const l = await future();
    expect((await send("POST", `${l.id}/cancel`, { reason: "OTHER", channels: [] })).status).toBe(400);
    expect((await send("POST", `${l.id}/cancel`, { reason: "HOLIDAY", channels: ["SMS"] })).status).toBe(400);
    const res = await send("POST", `${l.id}/cancel`, { reason: "HOLIDAY", reasonText: "ignorado", channels: ["EMAIL"] });
    expect(res.status).toBe(200);
    expect(await res.json()).toMatchObject({ status: "CANCELLED", cancelReasonCode: "HOLIDAY", cancelReason: null, canEdit: false, canCancel: false });
    expect((await send("POST", `${l.id}/cancel`, { reason: "HOLIDAY", channels: [] })).status).toBe(409);
    expect((await send("PUT", l.id, edit(l))).status).toBe(409);
    const listed = (await (await get(`from=${l.date}&to=${l.date}`)).json()).lessons.find((x: { id: string }) => x.id === l.id);
    expect(listed.cancelReasonCode).toBe("HOLIDAY");
    const another = (await (await get(`from=${addDays(todayIso(), 1)}&to=${addDays(todayIso(), 14)}`)).json()).lessons.find((x: { status: string }) => x.status === "SCHEDULED");
    const other = await (await send("POST", `${another.id}/cancel`, { reason: "OTHER", reasonText: "Chuva", channels: [] })).json();
    expect(other).toMatchObject({ cancelReasonCode: "OTHER", cancelReason: "Chuva" });
    expect(JSON.stringify(other)).not.toMatch(/espera|presen|frequ/i);
  });

  it("portal 403", async () => {
    await login("ana@exemplo.com");
    const l = await future();
    await login("varios@exemplo.com", "00000000-0000-0000-0000-000000000012");
    expect((await send("PUT", l.id, edit(l))).status).toBe(403);
    expect((await send("POST", `${l.id}/cancel`, { reason: "HOLIDAY", channels: [] })).status).toBe(403);
  });
});
