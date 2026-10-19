import { readFileSync } from "node:fs";
import { join } from "node:path";
import { parse } from "yaml";

const openapi = parse(readFileSync(join(process.cwd(), "../docs/openapi.yaml"), "utf8"));
const json = { "Content-Type": "application/json" };
const login = async (email: string, linkId?: string) => {
  await fetch("/api/v1/auth/login", { method: "POST", headers: json, body: JSON.stringify({ email, password: "segredo123" }) });
  if (linkId) await fetch("/api/v1/auth/session/link", { method: "PUT", headers: json, body: JSON.stringify({ linkId }) });
};
const list = (query = "") => fetch(`/api/v1/teachers${query}`);
const names = (data: { items: { name: string }[] }) => data.items.map((i) => i.name);

describe("professores x OpenAPI (RF-PRO-01, CA10)", () => {
  it("sem sessão responde 401, declarado no contrato", async () => {
    expect((await list()).status).toBe(401);
    expect(Object.keys(openapi.paths["/teachers"].get.responses)).toEqual(expect.arrayContaining(["200", "400", "401", "403"]));
  });

  it("Coordenador: envelope e linha no formato do contrato, sem contato, ordenados por nome", async () => {
    await login("ana@exemplo.com");
    const data = await (await list()).json();
    expect(Object.keys(data).sort()).toEqual(["items", "page", "size", "total"]);
    expect(data).toMatchObject({ page: 1, size: 25, total: 3 });
    expect(names(data)).toEqual(["Ana Souza", "João Pereira", "Marcos Lima"]);
    expect(Object.keys(data.items[0]).sort()).toEqual([...openapi.components.schemas.TeacherRow.required].sort());
    expect(JSON.stringify(data)).not.toMatch(/email|phone/);
    expect(names(await (await list("?sort=name,desc")).json())[0]).toBe("Marcos Lima");
  });

  it("filtros q (sem acento), modalidade, unidade e situação combinam; valor inválido dá 400", async () => {
    await login("ana@exemplo.com");
    expect(names(await (await list("?q=JOAO")).json())).toEqual(["João Pereira"]);
    expect(names(await (await list("?status=INACTIVE")).json())).toEqual(["Marcos Lima"]);
    expect(names(await (await list("?unitId=00000000-0000-0000-0000-000000000022")).json())).toEqual(["João Pereira", "Marcos Lima"]);
    expect(names(await (await list("?modalityId=00000000-0000-0000-0000-000000000103&unitId=00000000-0000-0000-0000-000000000022")).json())).toEqual(["João Pereira"]);
    expect((await list("?status=XYZ")).status).toBe(400);
    expect((await list("?size=10")).status).toBe(400);
    expect((await list("?page=0")).status).toBe(400);
    const second = await (await list("?page=2")).json();
    expect(second.items).toEqual([]);
  });

  it("Administrador só vê os da unidade dele; Professor e portal recebem 403", async () => {
    await login("varios@exemplo.com", "00000000-0000-0000-0000-000000000011");
    expect(names(await (await list()).json())).toEqual(["Ana Souza", "João Pereira"]);
    expect(names(await (await list("?unitId=00000000-0000-0000-0000-000000000022")).json())).toEqual([]);
    await login("varios@exemplo.com", "00000000-0000-0000-0000-000000000013");
    expect((await list()).status).toBe(403);
    await login("varios@exemplo.com", "00000000-0000-0000-0000-000000000012");
    expect((await list()).status).toBe(403);
  });
});

const JOAO = "00000000-0000-0000-0000-000000000502";
const ANA = "00000000-0000-0000-0000-000000000501";
const MARCOS = "00000000-0000-0000-0000-000000000503";
const post = (path: string, body?: unknown) => fetch(`/api/v1/teachers/${path}`, { method: "POST", headers: json, body: body === undefined ? undefined : JSON.stringify(body) });

describe("painel do professor x OpenAPI (RF-PRO-02, CA10)", () => {
  it("o contrato declara as três rotas e o painel traz os campos de TeacherDetail com contato", async () => {
    expect(Object.keys(openapi.paths["/teachers/{id}"].get.responses)).toEqual(expect.arrayContaining(["200", "401", "403", "404"]));
    expect(Object.keys(openapi.paths["/teachers/{id}/access"].post.responses)).toEqual(expect.arrayContaining(["200", "400", "401", "403", "404", "409"]));
    expect(Object.keys(openapi.paths["/teachers/{id}/access/resend"].post.responses)).toEqual(expect.arrayContaining(["204", "401", "403", "404", "409"]));
    expect((await fetch(`/api/v1/teachers/${JOAO}`)).status).toBe(401);
    await login("ana@exemplo.com");
    const data = await (await fetch(`/api/v1/teachers/${JOAO}`)).json();
    expect(Object.keys(data).sort()).toEqual([...openapi.components.schemas.TeacherDetail.required].sort());
    expect(data).toMatchObject({ email: "joao.pereira@alfa.test", classes: [], upcomingClasses: [], access: "NONE" });
    expect((await fetch("/api/v1/teachers/00000000-0000-0000-0000-0000000009ff")).status).toBe(404);
  });

  it("Administrador só abre professor com a unidade dele; Professor recebe 403", async () => {
    await login("varios@exemplo.com", "00000000-0000-0000-0000-000000000011");
    expect((await fetch(`/api/v1/teachers/${JOAO}`)).status).toBe(200);
    expect((await fetch(`/api/v1/teachers/${MARCOS}`)).status).toBe(404);
    await login("varios@exemplo.com", "00000000-0000-0000-0000-000000000013");
    expect((await fetch(`/api/v1/teachers/${JOAO}`)).status).toBe(403);
  });

  it("dar acesso: recusas (sem e-mail 400, inativo 409, unidade alheia 400/403) e sucesso deixa access PENDING; reenviar só com PENDING", async () => {
    await login("ana@exemplo.com");
    expect((await post(`${MARCOS}/access`)).status).toBe(400);
    expect((await post(`${JOAO}/access`, { unitIds: ["00000000-0000-0000-0000-0000000009aa"] })).status).toBe(400);
    expect((await post(`${JOAO}/access/resend`)).status).toBe(409);
    const granted = await post(`${JOAO}/access`, { unitIds: ["00000000-0000-0000-0000-000000000021"] });
    expect(granted.status).toBe(200);
    expect((await granted.json()).access).toBe("PENDING");
    expect((await post(`${JOAO}/access`)).status).toBe(409);
    expect((await post(`${ANA}/access`)).status).toBe(409);
    for (let i = 0; i < 3; i++) expect((await post(`${JOAO}/access/resend`)).status).toBe(204);
    expect((await post(`${JOAO}/access/resend`)).status).toBe(429);
    await login("varios@exemplo.com", "00000000-0000-0000-0000-000000000011");
    expect((await post(`${ANA}/access`, { unitIds: ["00000000-0000-0000-0000-000000000022"] })).status).toBe(403);
  });
});
