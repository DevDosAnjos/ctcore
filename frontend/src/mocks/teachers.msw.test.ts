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
    expect(data).toMatchObject({ email: "joao.pereira@alfa.test", classes: [{ id: "00000000-0000-0000-0000-000000000601", name: "Jiu-Jitsu Adulto", role: "ASSISTANT" }, { id: "00000000-0000-0000-0000-000000000602", name: "Judô Infantil", role: "HEAD" }], upcomingClasses: [], access: "NONE" });
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

const send = (method: string, path: string, body?: unknown) => fetch(`/api/v1/teachers${path}`, { method, headers: json, body: body === undefined ? undefined : JSON.stringify(body) });
const CENTRO = "00000000-0000-0000-0000-000000000021";
const JIU = "00000000-0000-0000-0000-000000000103";
const JUDO = "00000000-0000-0000-0000-000000000104";
const valid = (extra: Record<string, unknown> = {}) => ({ name: "Nova Professora", unitIds: [CENTRO], modalities: [{ modalityId: JUDO, levelId: null, degree: null }], ...extra });

describe("cadastro e edição do professor x OpenAPI (RF-PRO-03, CA13)", () => {
  it("o contrato declara as rotas novas e o formato de TeacherInput e TeacherOptions", () => {
    expect(Object.keys(openapi.paths["/teachers"].post.responses)).toEqual(expect.arrayContaining(["201", "400", "401", "403"]));
    expect(Object.keys(openapi.paths["/teachers/{id}"].put.responses)).toEqual(expect.arrayContaining(["200", "400", "401", "403", "404"]));
    expect(Object.keys(openapi.paths["/teachers/{id}/photo"].put.responses)).toEqual(expect.arrayContaining(["200", "400", "401", "403", "404"]));
    expect(Object.keys(openapi.paths["/teachers/{id}/photo"].delete.responses)).toEqual(expect.arrayContaining(["200", "401", "403", "404"]));
    expect(Object.keys(openapi.paths["/teachers/options"].get.responses)).toEqual(expect.arrayContaining(["200", "401", "403"]));
    expect(openapi.components.schemas.TeacherInput.required).toEqual(["name", "unitIds"]);
  });

  it("sem sessão 401; Professor 403 em todas; opções só com modalidades ativas e no formato do contrato", async () => {
    expect((await send("POST", "", valid())).status).toBe(401);
    expect((await fetch("/api/v1/teachers/options")).status).toBe(401);
    await login("varios@exemplo.com", "00000000-0000-0000-0000-000000000013");
    expect((await send("POST", "", valid())).status).toBe(403);
    expect((await send("PUT", `/${JOAO}`, valid())).status).toBe(403);
    expect((await fetch("/api/v1/teachers/options")).status).toBe(403);
    await login("ana@exemplo.com");
    const data = await (await fetch("/api/v1/teachers/options")).json();
    expect(Object.keys(data)).toEqual(["modalities"]);
    expect(Object.keys(data.modalities[0]).sort()).toEqual(["graduationEnabled", "id", "levels", "name"]);
    expect(data.modalities.map((m: { name: string }) => m.name)).toEqual(["Boxe", "Jiu-Jitsu", "Judô"]);
  });

  it("POST: cria com 201 no formato de TeacherDetail, telefone só com dígitos, aparece na lista", async () => {
    await login("ana@exemplo.com");
    const res = await send("POST", "", valid({ phone: "(11) 98888-7777", email: "nova@exemplo.com", directoryConsent: true, consentDeclaredByManagement: true }));
    expect(res.status).toBe(201);
    const data = await res.json();
    expect(Object.keys(data).sort()).toEqual([...openapi.components.schemas.TeacherDetail.required].sort());
    expect(data).toMatchObject({ status: "ACTIVE", access: "NONE", phone: "11988887777", consentDeclaredByManagement: true, modalities: [{ name: "Judô", levelId: null, level: null, degree: null }] });
    expect(names(await (await list()).json())).toContain("Nova Professora");
  });

  it("POST: nome, e-mail, telefone, apresentação, unidades, consentimento e modalidades inválidos dão 400 com o campo", async () => {
    await login("ana@exemplo.com");
    const cases: [string, Record<string, unknown>][] = [
      ["name", { name: "A" }],
      ["email", { email: "sem-arroba" }],
      ["phone", { phone: "123" }],
      ["bio", { bio: "x".repeat(301) }],
      ["unitIds", { unitIds: [] }],
      ["unitIds", { unitIds: ["00000000-0000-0000-0000-0000000009aa"] }],
      ["consentDeclaredByManagement", { directoryConsent: true }],
      ["modalities", { modalities: [{ modalityId: JUDO }, { modalityId: JUDO }] }],
      ["modalities", { modalities: [{ modalityId: "00000000-0000-0000-0000-0000000009aa" }] }],
      ["modalities", { modalities: [{ modalityId: JUDO, levelId: "00000000-0000-0000-0000-0000000009aa", degree: 1 }] }],
    ];
    for (const [field, extra] of cases) {
      const res = await send("POST", "", valid(extra));
      expect(res.status, field).toBe(400);
      expect(JSON.stringify(await res.json())).toContain(field);
    }
  });

  it("Administrador só cria com a unidade dele (403 fora) e PUT mantém as unidades de fora do alcance", async () => {
    await login("varios@exemplo.com", "00000000-0000-0000-0000-000000000011");
    expect((await send("POST", "", valid({ unitIds: ["00000000-0000-0000-0000-000000000022"] }))).status).toBe(403);
    const res = await send("PUT", `/${JOAO}`, valid({ name: "João P." }));
    expect(res.status).toBe(200);
    expect((await res.json()).units.map((u: { id: string }) => u.id).sort()).toEqual([CENTRO, "00000000-0000-0000-0000-000000000022"]);
    expect((await send("PUT", `/${MARCOS}`, valid())).status).toBe(404);
  });

  it("PUT: substitui o cadastro, mantém situação e acesso, funciona em inativo; 404 inexistente", async () => {
    await login("ana@exemplo.com");
    const res = await send("PUT", `/${MARCOS}`, valid({ name: "Marcos L." }));
    expect(res.status).toBe(200);
    expect(await res.json()).toMatchObject({ name: "Marcos L.", status: "INACTIVE", access: "ENDED", units: [{ id: CENTRO }] });
    expect((await send("PUT", "/00000000-0000-0000-0000-0000000009ff", valid())).status).toBe(404);
    void JIU;
  });

  it("foto: PUT multipart troca, sem arquivo 400, DELETE remove", async () => {
    await login("ana@exemplo.com");
    // FormData e File do jsdom não passam pelo fetch do Node; o corpo multipart vai montado à mão.
    const multipart = (content: string) => ({ method: "PUT", headers: { "Content-Type": "multipart/form-data; boundary=x" }, body: `--x\r\nContent-Disposition: form-data; name="file"; filename="f.png"\r\nContent-Type: image/png\r\n\r\n${content}\r\n--x--\r\n` });
    const sent = await fetch(`/api/v1/teachers/${JOAO}/photo`, multipart("a".repeat(64)));
    expect(sent.status).toBe(200);
    expect((await sent.json()).photoUrl).not.toBeNull();
    expect((await fetch(`/api/v1/teachers/${JOAO}/photo`, { method: "PUT", headers: { "Content-Type": "multipart/form-data; boundary=x" }, body: "--x--\r\n" })).status).toBe(400);
    const gone = await fetch(`/api/v1/teachers/${JOAO}/photo`, { method: "DELETE" });
    expect(gone.status).toBe(200);
    expect((await gone.json()).photoUrl).toBeNull();
  });
});

describe("inativar professor x OpenAPI (RF-PRO-04, CA10)", () => {
  it("o contrato declara as duas rotas; pré-visualização não altera e inativar dá 200 INACTIVE, 409 ao repetir", async () => {
    expect(Object.keys(openapi.paths["/teachers/{id}/deactivation"].get.responses)).toEqual(expect.arrayContaining(["200", "401", "403", "404"]));
    expect(Object.keys(openapi.paths["/teachers/{id}/deactivate"].post.responses)).toEqual(expect.arrayContaining(["200", "401", "403", "404", "409"]));
    expect((await fetch(`/api/v1/teachers/${JOAO}/deactivation`)).status).toBe(401);
    await login("ana@exemplo.com");
    const preview = await (await fetch(`/api/v1/teachers/${JOAO}/deactivation`)).json();
    expect(Object.keys(preview).sort()).toEqual([...openapi.components.schemas.TeacherDeactivationPreview.required].sort());
    expect(preview).toEqual({ headClasses: [], canDeactivate: true });
    expect((await (await fetch(`/api/v1/teachers/${JOAO}`)).json()).status).toBe("ACTIVE");
    await post(`${JOAO}/access`);
    const done = await post(`${JOAO}/deactivate`);
    expect(done.status).toBe(200);
    expect(await done.json()).toMatchObject({ status: "INACTIVE", access: "ENDED" });
    expect((await post(`${JOAO}/deactivate`)).status).toBe(409);
    expect((await (await fetch(`/api/v1/teachers/${JOAO}/deactivation`)).json()).canDeactivate).toBe(false);
    expect((await post("00000000-0000-0000-0000-0000000009ff/deactivate")).status).toBe(404);
  });

  it("Administrador só inativa quem tem só a unidade dele (403 e canDeactivate falso senão); Professor 403", async () => {
    await login("varios@exemplo.com", "00000000-0000-0000-0000-000000000011");
    expect((await (await fetch(`/api/v1/teachers/${JOAO}/deactivation`)).json()).canDeactivate).toBe(false);
    expect((await post(`${JOAO}/deactivate`)).status).toBe(403);
    expect((await post(`${ANA}/deactivate`)).status).toBe(200);
    await login("varios@exemplo.com", "00000000-0000-0000-0000-000000000013");
    expect((await post(`${JOAO}/deactivate`)).status).toBe(403);
    expect((await fetch(`/api/v1/teachers/${JOAO}/deactivation`)).status).toBe(403);
  });
});
