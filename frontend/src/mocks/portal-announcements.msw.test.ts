import { readFileSync } from "node:fs";
import { join } from "node:path";
import { parse } from "yaml";

const openapi = parse(readFileSync(join(process.cwd(), "../docs/openapi.yaml"), "utf8"));
const json = { "Content-Type": "application/json" };
const send = (method: string, path: string, body?: unknown) =>
  fetch(`/api/v1${path}`, { method, headers: json, body: body === undefined ? undefined : JSON.stringify(body) });
const login = (email: string) => send("POST", "/auth/login", { email, password: "segredo123" });
const CHILD_TWO = "00000000-0000-0000-0000-000000000703";
const ADULT = "00000000-0000-0000-0000-000000000701";
const keys = (o: object) => Object.keys(o).sort();
const required = (name: string): string[] => [...openapi.components.schemas[name].required].sort();
const list = async (query = "") => (await send("GET", `/portal/announcements${query}`)).json();

describe("Avisos do portal x OpenAPI (RF-POR-06, #93)", () => {
  it("o contrato declara as três rotas, os parâmetros e os campos, sem professor, presença nem cobrança", () => {
    const get = openapi.paths["/portal/announcements"].get;
    expect(Object.keys(get.responses)).toEqual(expect.arrayContaining(["200", "400", "401", "403", "404"]));
    expect(get.parameters.map((p: { name: string }) => p.name)).toEqual(["studentId", "filter", "modalityId", "page", "size"]);
    expect(Object.keys(openapi.paths["/portal/announcements/{id}/open"].post.responses)).toEqual(expect.arrayContaining(["200", "401", "403", "404"]));
    expect(Object.keys(openapi.paths["/portal/announcements/read-all"].post.responses)).toEqual(expect.arrayContaining(["200", "401", "403"]));
    expect(JSON.stringify(openapi.components.schemas.PortalAnnouncements)).not.toMatch(/teacher|attendance|frequency|progress|amount/i);
  });

  it("sem sessão 401; gestão 403; termo pendente 403 TERM_PENDING, nas três rotas", async () => {
    expect((await send("GET", "/portal/announcements")).status).toBe(401);
    await login("ana@exemplo.com");
    expect((await send("GET", "/portal/announcements")).status).toBe(403);
    expect((await send("POST", "/portal/announcements/read-all")).status).toBe(403);
    await login("termo@exemplo.com");
    for (const [method, path] of [["GET", "/portal/announcements"], ["POST", "/portal/announcements/read-all"], ["POST", "/portal/announcements/x/open"]]) {
      const res = await send(method, path);
      expect(res.status).toBe(403);
      expect((await res.json()).code).toBe("TERM_PENDING");
    }
  });

  it("lista no formato do contrato: fixado no topo, não lido e destino; filtros UNREAD e MODALITY; 400 para filtro e modalidade inválidos", async () => {
    await login("familia@exemplo.com");
    const view = await list();
    expect(keys(view)).toEqual(required("PortalAnnouncements"));
    view.items.forEach((i: object) => expect(keys(i)).toEqual(required("PortalAnnouncementItem")));
    expect(view).toMatchObject({ reduced: false, filter: "ALL", unreadCount: 3, total: 3, page: 1, size: 20 });
    expect(view.items[0]).toMatchObject({ pinned: true, unread: true, destination: { kind: "UNIT", names: ["Unidade Centro"] } });
    expect((await list("?filter=UNREAD")).items).toHaveLength(3);

    const judo = view.modalities.find((m: { name: string }) => m.name === "Judô");
    const byModality = await list(`?filter=MODALITY&modalityId=${judo.id}`);
    expect(byModality.items.map((i: { title: string }) => i.title)).toEqual(["Mudança de horário no sábado", "Recesso de fim de ano"]);
    expect(byModality.unreadCount).toBe(3);
    expect((await send("GET", "/portal/announcements?filter=XYZ")).status).toBe(400);
    expect((await send("GET", "/portal/announcements?filter=MODALITY")).status).toBe(400);
    expect((await send("GET", `/portal/announcements?studentId=${ADULT}`)).status).toBe(404);
    expect((await list("?size=500")).size).toBe(50);
    expect((await list("?size=2&page=2")).items).toHaveLength(1);
  });

  it("abrir devolve o texto e marca como lido só do aluno escolhido; aviso que ele não alcança é 404; o sino acompanha", async () => {
    await login("familia@exemplo.com");
    const view = await list();
    const id = view.items[0].id;
    const opened = await (await send("POST", `/portal/announcements/${id}/open`)).json();
    expect(keys(opened)).toEqual(required("PortalAnnouncementItem").concat(["body", "imageUrl"]).sort());
    expect(opened).toMatchObject({ unread: false, body: expect.stringContaining("sábado") });
    expect((await list()).unreadCount).toBe(2);
    expect((await list(`?studentId=${CHILD_TWO}`)).unreadCount).toBe(3);
    expect((await (await send("GET", "/portal/shell")).json()).unreadAnnouncements).toBe(4);
    expect((await send("POST", `/portal/announcements/00000000-0000-0000-0000-0000000007e4/open`)).status).toBe(404);
    expect((await send("POST", `/portal/announcements/${id}/open?studentId=${CHILD_TWO}`)).status).toBe(200);
  });

  it("marcar todos limpa as duas crianças sem virar leitura, zera o sino e repetir responde 0", async () => {
    await login("familia@exemplo.com");
    expect(await (await send("POST", "/portal/announcements/read-all")).json()).toEqual({ dismissed: 4 });
    expect((await list()).unreadCount).toBe(0);
    expect((await list(`?studentId=${CHILD_TWO}`)).items.every((i: { unread: boolean }) => !i.unread)).toBe(true);
    expect(await (await send("GET", "/portal/shell")).json()).toMatchObject({ unreadAnnouncements: 0 });
    expect(await (await send("POST", "/portal/announcements/read-all")).json()).toEqual({ dismissed: 0 });
  });

  it("aluno inativo: 200 com reduced e lista vazia; abrir é 404 e marcar todos dá 0", async () => {
    await login("reduzido@exemplo.com");
    expect(await list()).toMatchObject({ reduced: true, items: [], total: 0, unreadCount: 0 });
    expect((await send("POST", "/portal/announcements/00000000-0000-0000-0000-0000000007e1/open")).status).toBe(404);
    expect(await (await send("POST", "/portal/announcements/read-all")).json()).toEqual({ dismissed: 0 });
  });
});
