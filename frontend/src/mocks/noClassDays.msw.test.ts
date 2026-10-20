import { readFileSync } from "node:fs";
import { join } from "node:path";
import { parse } from "yaml";
import { addDays, todayIso } from "@/features/agenda/agendaDates";

const openapi = parse(readFileSync(join(process.cwd(), "../docs/openapi.yaml"), "utf8"));
const schemas = openapi.components.schemas;
const json = { "Content-Type": "application/json" };
const UNIT = "00000000-0000-0000-0000-000000000021";
const login = async (email: string, linkId?: string) => {
  await fetch("/api/v1/auth/login", { method: "POST", headers: json, body: JSON.stringify({ email, password: "segredo123" }) });
  if (linkId) await fetch("/api/v1/auth/session/link", { method: "PUT", headers: json, body: JSON.stringify({ linkId }) });
};
const send = (method: string, path: string, body?: unknown) => fetch(`/api/v1/no-class-days${path}`, { method, headers: json, body: body === undefined ? undefined : JSON.stringify(body) });
const day = (over: Record<string, unknown> = {}) => ({ unitIds: [UNIT], startsOn: addDays(todayIso(), 3), endsOn: addDays(todayIso(), 3), reason: "Recesso", notice: { mode: "NONE" }, ...over });
const keys = (o: object) => Object.keys(o).sort();

describe("no-class-days x OpenAPI (RF-AGE-05, CA18)", () => {
  it("contrato declara as cinco rotas com os status do servidor", () => {
    const p = openapi.paths;
    expect(Object.keys(p["/no-class-days"].get.responses)).toEqual(expect.arrayContaining(["200", "400", "401", "403", "404"]));
    expect(Object.keys(p["/no-class-days"].post.responses)).toEqual(expect.arrayContaining(["201", "400", "401", "403", "404", "409", "422"]));
    expect(Object.keys(p["/no-class-days/preview"].get.responses)).toEqual(expect.arrayContaining(["200", "400", "403", "404"]));
    expect(Object.keys(p["/no-class-days/{id}"].put.responses)).toEqual(expect.arrayContaining(["200", "400", "404", "409"]));
    expect(Object.keys(p["/no-class-days/{id}"].delete.responses)).toEqual(expect.arrayContaining(["204", "404", "409"]));
  });

  it("sem sessão 401; Professor e portal 403; Administrador e Coordenador listam", async () => {
    expect((await send("GET", "")).status).toBe(401);
    await login("varios@exemplo.com", "00000000-0000-0000-0000-000000000012");
    expect((await send("GET", "")).status).toBe(403);
    await login("varios@exemplo.com", "00000000-0000-0000-0000-000000000013");
    expect((await send("GET", "")).status).toBe(403);
    await login("varios@exemplo.com", "00000000-0000-0000-0000-000000000011");
    expect((await send("GET", "")).status).toBe(200);
    await login("ana@exemplo.com");
    expect((await send("GET", "")).status).toBe(200);
    expect((await send("GET", "?unitId=00000000-0000-0000-0000-0000000009ff")).status).toBe(404);
  });

  it("lista no formato do contrato e sugere feriados que somem ao confirmar", async () => {
    await login("ana@exemplo.com");
    const view = await (await send("GET", "")).json();
    expect(keys(view)).toEqual([...schemas.NoClassDayList.required].sort());
    expect(view.days).toEqual([]);
    expect(view.suggestions.length).toBeGreaterThan(5);
    expect(keys(view.suggestions[0])).toEqual([...schemas.NoClassDaySuggestion.required].sort());
    const first = view.suggestions[0];
    const made = await send("POST", "", day({ startsOn: first.date, endsOn: first.date, holiday: true }));
    expect(made.status).toBe(201);
    const [created] = await made.json();
    expect(keys(created)).toEqual([...schemas.NoClassDayItem.required].sort());
    expect(keys(created.notice)).toEqual([...schemas.NoClassDayNotice.required].sort());
    expect(created.origin).toBe("HOLIDAY");
    expect(created.notice.status).toBe("NOT_NOTIFIED");
    const after = await (await send("GET", "")).json();
    expect(after.days).toHaveLength(1);
    expect(after.suggestions.map((s: { date: string }) => s.date)).not.toContain(first.date);
  });

  it("prévia traz as aulas por unidade e o POST seguinte cancela o mesmo número, sem gravar antes", async () => {
    await login("ana@exemplo.com");
    const start = addDays(todayIso(), 1);
    const end = addDays(todayIso(), 7);
    const q = `?unitIds=${UNIT}&startsOn=${start}&endsOn=${end}`;
    const preview = await (await send("GET", `/preview${q}`)).json();
    expect(keys(preview)).toEqual([...schemas.NoClassDayPreview.required].sort());
    const lessons = preview.units[0].lessons;
    expect(lessons.length).toBeGreaterThan(0);
    expect(keys(lessons[0])).toEqual([...schemas.NoClassDayPreviewLesson.required].sort());
    expect((await (await send("GET", "")).json()).days).toEqual([]);
    const [created] = await (await send("POST", "", day({ startsOn: start, endsOn: end }))).json();
    expect(created.cancelledLessons).toBe(lessons.length);
    expect(created.origin).toBe("COORDINATOR");
    const agenda = await (await fetch(`/api/v1/agenda?from=${start}&to=${end}&unitId=${UNIT}`)).json();
    expect(agenda.noClassDays.some((d: { reason: string }) => d.reason === "Recesso")).toBe(true);
    expect(agenda.lessons.filter((l: { status: string }) => l.status === "CANCELLED")).toHaveLength(lessons.length);
    expect(JSON.stringify(created)).not.toMatch(/espera|presen|frequ/i);
  });

  it("validação como no servidor: 400 com o campo, 404, 422 e 409", async () => {
    await login("ana@exemplo.com");
    const cases: [Record<string, unknown>, string][] = [
      [{ reason: " " }, "reason"],
      [{ reason: "x".repeat(121) }, "reason"],
      [{ startsOn: "2026-13-40" }, "startsOn"],
      [{ endsOn: addDays(todayIso(), 1) }, "endsOn"],
      [{ startsOn: addDays(todayIso(), -1) }, "startsOn"],
      [{ endsOn: addDays(todayIso(), 95) }, "endsOn"],
      [{ unitIds: [] }, "unitIds"],
      [{ notice: { mode: "SCHEDULED" } }, "notice"],
      [{ notice: { mode: "SCHEDULED", sendAt: new Date(Date.now() - 60000).toISOString() } }, "notice"],
    ];
    for (const [over, field] of cases) {
      const res = await send("POST", "", day(over));
      expect(res.status, field).toBe(400);
      expect((await res.json()).fields[0].field).toBe(field);
    }
    expect((await send("POST", "", day({ unitIds: ["00000000-0000-0000-0000-0000000009ff"] }))).status).toBe(404);
    expect((await send("POST", "", day({ classIds: ["00000000-0000-0000-0000-000000000603"] }))).status).toBe(422);
    expect((await send("POST", "", day({ classIds: ["00000000-0000-0000-0000-0000000009ff"] }))).status).toBe(404);
    expect((await send("POST", "", day())).status).toBe(201);
    expect((await send("POST", "", day())).status).toBe(409);
    const scheduled = day({ startsOn: addDays(todayIso(), 20), endsOn: addDays(todayIso(), 20), notice: { mode: "SCHEDULED", sendAt: new Date(Date.now() + 3600000).toISOString() } });
    const [made] = await (await send("POST", "", scheduled)).json();
    expect(made.notice.status).toBe("SCHEDULED");
  });

  it("Administrador lança só a unidade dele, com origem UNIT; várias unidades 400", async () => {
    await login("varios@exemplo.com", "00000000-0000-0000-0000-000000000011");
    expect((await send("POST", "", day({ unitIds: [UNIT, "00000000-0000-0000-0000-000000000022"] }))).status).toBe(400);
    expect((await send("POST", "", day({ unitIds: ["00000000-0000-0000-0000-000000000022"] }))).status).toBe(404);
    const [made] = await (await send("POST", "", day())).json();
    expect(made.origin).toBe("UNIT");
  });

  it("edita e desfaz dia futuro; dia que já começou 409; inexistente 404", async () => {
    await login("ana@exemplo.com");
    const [made] = await (await send("POST", "", day())).json();
    const classId = "00000000-0000-0000-0000-000000000601";
    const edited = await send("PUT", `/${made.id}`, day({ reason: "Outro", classIds: [classId], startsOn: addDays(todayIso(), 5), endsOn: addDays(todayIso(), 5) }));
    expect(edited.status).toBe(200);
    const item = await edited.json();
    expect(item.reason).toBe("Outro");
    expect(item.allClasses).toBe(false);
    expect(item.classes).toEqual([{ id: classId, name: "Jiu-Jitsu Adulto" }]);
    expect((await send("PUT", `/${made.id}`, day({ reason: "" }))).status).toBe(400);
    expect((await send("PUT", "/00000000-0000-0000-0000-0000000009ff", day())).status).toBe(404);
    expect((await send("DELETE", `/${made.id}?notify=true`)).status).toBe(204);
    expect((await send("DELETE", `/${made.id}`)).status).toBe(404);
    expect((await (await send("GET", "")).json()).days).toEqual([]);
  });
});
