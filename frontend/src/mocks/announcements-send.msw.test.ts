import { readFileSync } from "node:fs";
import { join } from "node:path";
import { parse } from "yaml";

const openapi = parse(readFileSync(join(process.cwd(), "../docs/openapi.yaml"), "utf8"));
const json = { "Content-Type": "application/json" };
const login = async (email: string, linkId?: string) => {
  await fetch("/api/v1/auth/login", { method: "POST", headers: json, body: JSON.stringify({ email, password: "segredo123" }) });
  if (linkId) await fetch("/api/v1/auth/session/link", { method: "PUT", headers: json, body: JSON.stringify({ linkId }) });
};
const get = (path: string) => fetch(`/api/v1${path}`);
const post = (path: string, body: unknown) => fetch(`/api/v1${path}`, { method: "POST", headers: json, body: JSON.stringify(body) });
const ADMIN_LINK = "00000000-0000-0000-0000-000000000011";
const STUDENT_LINK = "00000000-0000-0000-0000-000000000012";
const TEACHER_LINK = "00000000-0000-0000-0000-000000000013";
const CLASS = "00000000-0000-0000-0000-000000000601";
const OTHER_CLASS = "00000000-0000-0000-0000-000000000602";
const INACTIVE_CLASS = "00000000-0000-0000-0000-000000000603";
const UNIT = "00000000-0000-0000-0000-000000000021";
const NORTE = "00000000-0000-0000-0000-000000000022";
const MISSING = "00000000-0000-0000-0000-0000000000de";
const valid = { targetKind: "CLASS", classId: CLASS, type: "GENERAL", title: "Aviso", body: "Texto **importante**", channels: ["EMAIL"] };
const fieldOf = async (res: Response) => ((await res.json()).fields as { field: string }[])[0]?.field;

describe("comunicados: novo comunicado x OpenAPI (RF-COM-02, #82, CA27)", () => {
  it("o contrato declara as quatro rotas novas e o código do limite", () => {
    expect(Object.keys(openapi.paths["/announcements/targets"].get.responses)).toEqual(expect.arrayContaining(["200", "401", "403"]));
    expect(Object.keys(openapi.paths["/announcements/preview"].post.responses)).toEqual(expect.arrayContaining(["200", "400", "403", "404"]));
    expect(Object.keys(openapi.paths["/announcements"].post.responses)).toEqual(expect.arrayContaining(["201", "400", "403", "404", "409"]));
    expect(Object.keys(openapi.paths["/announcements/image"].post.responses)).toEqual(expect.arrayContaining(["200", "400", "403"]));
    expect(openapi.components.schemas.Error.properties.code.enum).toContain("ANNOUNCEMENT_DAILY_LIMIT");
  });

  it("sem sessão 401; Aluno 403 nas quatro rotas", async () => {
    for (const [method, path] of [["GET", "/announcements/targets"], ["POST", "/announcements/preview"], ["POST", "/announcements"], ["POST", "/announcements/image"]]) {
      expect((await fetch(`/api/v1${path}`, { method, headers: json })).status, path).toBe(401);
    }
    await login("varios@exemplo.com", STUDENT_LINK);
    expect((await get("/announcements/targets")).status).toBe(403);
    expect((await post("/announcements/preview", valid)).status).toBe(403);
    expect((await post("/announcements", valid)).status).toBe(403);
  });

  it("destinos: o Coordenador vê tudo; o Administrador, a unidade dele; o Professor, só as turmas dele", async () => {
    await login("ana@exemplo.com");
    const all = await (await get("/announcements/targets")).json();
    expect(all.organization).toBe(true);
    expect(all.units.map((u: { id: string }) => u.id)).toEqual([UNIT]);
    expect(all.classes.map((c: { id: string }) => c.id)).toEqual([CLASS, OTHER_CLASS]);
    expect(all.classes[0]).toMatchObject({ unitId: UNIT, groupLink: "https://chat.example.com/jiu-jitsu-adulto" });
    expect(all.modalities.length).toBe(2);
    await login("varios@exemplo.com", ADMIN_LINK);
    const admin = await (await get("/announcements/targets")).json();
    expect(admin.organization).toBe(false);
    expect(admin.units).toHaveLength(1);
    await login("varios@exemplo.com", TEACHER_LINK);
    const teacher = await (await get("/announcements/targets")).json();
    expect(teacher).toMatchObject({ units: [], modalities: [], organization: false });
    expect(teacher.classes.map((c: { id: string }) => c.id)).toEqual([CLASS]);
  });

  it("prévia: conta pessoas e canais, WhatsApp em grupo ou conversa, PUSH 400, outro destino 404 e turma desativada 400", async () => {
    await login("ana@exemplo.com");
    expect(await (await post("/announcements/preview", { targetKind: "CLASS", classId: CLASS, channels: ["EMAIL", "WHATSAPP"] })).json()).toEqual({
      recipients: 8, portal: 8, email: 6, whatsapp: { mode: "GROUP", groupLink: "https://chat.example.com/jiu-jitsu-adulto" }, channels: ["PORTAL", "EMAIL", "WHATSAPP"],
    });
    const noLink = await (await post("/announcements/preview", { targetKind: "CLASS", classId: OTHER_CLASS, channels: ["WHATSAPP"] })).json();
    expect(noLink.whatsapp).toEqual({ mode: "CONVERSATION", groupLink: null });
    expect((await (await post("/announcements/preview", { targetKind: "UNIT", unitId: UNIT, channels: [] })).json()).whatsapp).toBeNull();
    expect((await (await post("/announcements/preview", { targetKind: "ORGANIZATION" })).json()).recipients).toBe(40);
    const push = await post("/announcements/preview", { targetKind: "UNIT", unitId: UNIT, channels: ["PUSH"] });
    expect([push.status, await fieldOf(push)]).toEqual([400, "channels"]);
    expect((await post("/announcements/preview", { targetKind: "CLASS", classId: MISSING, channels: [] })).status).toBe(404);
    const off = await post("/announcements/preview", { targetKind: "CLASS", classId: INACTIVE_CLASS, channels: [] });
    expect([off.status, await fieldOf(off)]).toEqual([400, "classId"]);
  });

  it("cria enviado e agendado, e o comunicado aparece na lista", async () => {
    await login("ana@exemplo.com");
    const sent = await post("/announcements", valid);
    expect(sent.status).toBe(201);
    expect(await sent.json()).toMatchObject({ status: "SENT", recipients: 8 });
    const later = new Date(Date.now() + 86_400_000).toISOString();
    const scheduled = await post("/announcements", { ...valid, title: "Depois", sendAt: later });
    expect(await scheduled.json()).toMatchObject({ status: "SCHEDULED", recipients: null });
    const items = (await (await get("/announcements?size=100")).json()).items as { title: string; status: string; type: string }[];
    expect(items.find((i) => i.title === "Aviso")).toMatchObject({ status: "SENT", type: "GENERAL" });
    expect(items.find((i) => i.title === "Depois")?.status).toBe("SCHEDULED");
  });

  it("valida os campos: título, texto, tipo, canal, link, data fixa, envio no passado e imagem", async () => {
    await login("ana@exemplo.com");
    const cases: [object, string][] = [
      [{ title: " " }, "title"],
      [{ title: "x".repeat(121) }, "title"],
      [{ body: "" }, "body"],
      [{ body: "x".repeat(2001) }, "body"],
      [{ body: "[clique](javascript:alert(1))" }, "body"],
      [{ type: "OTHER" }, "type"],
      [{ channels: ["PUSH"] }, "channels"],
      [{ pinnedUntil: "2020-01-01" }, "pinnedUntil"],
      [{ sendAt: "2020-01-01T10:00:00Z" }, "sendAt"],
      [{ imageName: "../segredo.png" }, "imageName"],
    ];
    for (const [over, field] of cases) {
      const res = await post("/announcements", { ...valid, ...over });
      expect([res.status, await fieldOf(res)], JSON.stringify(over)).toEqual([400, field]);
    }
    expect((await post("/announcements", { ...valid, body: "[ok](https://exemplo.test)", imageName: "announcement-ab12.png" })).status).toBe(201);
  });

  it("alcance: o Professor só envia à turma dele; o Administrador não envia a todas as unidades nem a outra unidade", async () => {
    await login("varios@exemplo.com", TEACHER_LINK);
    expect((await post("/announcements", valid)).status).toBe(201);
    expect((await post("/announcements", { ...valid, classId: OTHER_CLASS })).status).toBe(403);
    expect((await post("/announcements", { ...valid, targetKind: "UNIT", unitId: UNIT })).status).toBe(403);
    expect((await post("/announcements", { ...valid, targetKind: "ORGANIZATION" })).status).toBe(403);
    await login("varios@exemplo.com", ADMIN_LINK);
    expect((await post("/announcements", { ...valid, targetKind: "ORGANIZATION" })).status).toBe(403);
    expect((await post("/announcements", { ...valid, targetKind: "UNIT", unitId: NORTE })).status).toBe(403);
    expect((await post("/announcements", { ...valid, targetKind: "UNIT", unitId: UNIT })).status).toBe(201);
    await login("ana@exemplo.com");
    expect((await post("/announcements", { ...valid, targetKind: "ORGANIZATION" })).status).toBe(201);
  });

  it("o 21º comunicado do dia na unidade volta 409 ANNOUNCEMENT_DAILY_LIMIT; outra unidade passa", async () => {
    await login("ana@exemplo.com");
    for (let i = 0; i < 19; i++) expect((await post("/announcements", { ...valid, targetKind: "UNIT", unitId: UNIT, classId: undefined, title: `n${i}` })).status).toBe(201);
    // 4 do simulado inicial caem em outros dias; os 19 + 1 chegam a 20
    expect((await post("/announcements", { ...valid, targetKind: "UNIT", unitId: UNIT, classId: undefined })).status).toBe(201);
    const over = await post("/announcements", { ...valid, targetKind: "UNIT", unitId: UNIT, classId: undefined });
    expect(over.status).toBe(409);
    expect((await over.json()).code).toBe("ANNOUNCEMENT_DAILY_LIMIT");
    expect((await post("/announcements", { ...valid, targetKind: "ORGANIZATION" })).status).toBe(201);
  });

  it("imagem: aceita uma imagem e recusa vazio, grande ou outro tipo", async () => {
    await login("ana@exemplo.com");
    // FormData e File do jsdom não passam pelo fetch do Node; o corpo multipart vai montado à mão (como no teste da logo).
    const send = (type: string, size: number) =>
      fetch("/api/v1/announcements/image", {
        method: "POST",
        headers: { "Content-Type": "multipart/form-data; boundary=x" },
        body: `--x
Content-Disposition: form-data; name="file"; filename="a.bin"
Content-Type: ${type}

${"a".repeat(size)}
--x--
`,
      });
    const ok = await send("image/png", 10);
    expect(ok.status).toBe(200);
    expect(await ok.json()).toMatchObject({ name: expect.stringMatching(/^announcement-.+.png$/), url: expect.any(String) });
    for (const res of [await send("image/png", 0), await send("text/plain", 10), await send("image/png", 5 * 1024 * 1024 + 1)]) {
      expect([res.status, await fieldOf(res)]).toEqual([400, "file"]);
    }
  });
});
