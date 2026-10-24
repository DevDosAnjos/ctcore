import { readFileSync } from "node:fs";
import { join } from "node:path";
import { parse } from "yaml";

const openapi = parse(readFileSync(join(process.cwd(), "../docs/openapi.yaml"), "utf8"));
const json = { "Content-Type": "application/json" };
const login = async (email: string, linkId?: string) => {
  await fetch("/api/v1/auth/login", { method: "POST", headers: json, body: JSON.stringify({ email, password: "segredo123" }) });
  if (linkId) await fetch("/api/v1/auth/session/link", { method: "PUT", headers: json, body: JSON.stringify({ linkId }) });
};
const list = () => fetch("/api/v1/organization/texts");
const put = (key: string, body: unknown) => fetch(`/api/v1/organization/texts/${key}`, { method: "PUT", headers: json, body: JSON.stringify(body) });
const del = (key: string) => fetch(`/api/v1/organization/texts/${key}`, { method: "DELETE" });
const declared = (path: string, method: string) => Object.keys(openapi.paths[path][method].responses);
const required = (name: string) => [...openapi.components.schemas[name].required].sort();
const BODY = "Olá, {nome}: {descricao} de {valor} vence em {vencimento} ({unidade}). {link_pagamento}";

describe("textos x OpenAPI (RF-COM-03, CA19)", () => {
  it("sem sessão as rotas respondem 401, declarado no contrato", async () => {
    expect((await list()).status).toBe(401);
    expect((await put("WELCOME", { subject: "a", body: "b" })).status).toBe(401);
    expect((await fetch("/api/v1/announcements/templates")).status).toBe(401);
    expect(declared("/organization/texts", "get")).toContain("401");
    expect(declared("/organization/texts/{key}", "put")).toContain("401");
    expect(declared("/announcements/templates", "get")).toContain("401");
  });

  it("Coordenador: lê 9 modelos no formato do contrato, grava, substitui e volta ao padrão", async () => {
    await login("ana@exemplo.com");
    const first = await (await list()).json();
    expect(Object.keys(first).sort()).toEqual(required("TextModelList"));
    expect(first.canManage).toBe(true);
    expect(first.models).toHaveLength(9);
    expect(first.models.map((m: { key: string }) => m.key)).toEqual(openapi.components.schemas.TextModelKey.enum);
    expect(Object.keys(first.models[0]).sort()).toEqual(required("TextModel"));
    expect(first.models.every((m: { custom: unknown }) => m.custom === null)).toBe(true);

    const saved = await put("REMINDER_DUE", { subject: "Sua cobrança, {nome}", body: BODY });
    expect(saved.status).toBe(200);
    const model = await saved.json();
    expect(Object.keys(model).sort()).toEqual(required("TextModel"));
    expect(model.custom.subject).toBe("Sua cobrança, {nome}");
    expect(model.updatedBy).toBeTruthy();
    expect((await put("REMINDER_DUE", { subject: "Outro", body: "Outro texto {nome}" })).status).toBe(200);
    const again = await (await list()).json();
    expect(again.models.find((m: { key: string }) => m.key === "REMINDER_DUE").custom.subject).toBe("Outro");

    expect((await del("REMINDER_DUE")).status).toBe(204);
    expect((await del("REMINDER_DUE")).status).toBe(204);
    const reset = await (await list()).json();
    expect(reset.models.find((m: { key: string }) => m.key === "REMINDER_DUE").custom).toBeNull();
    for (const status of ["200", "400", "403", "404"]) expect(declared("/organization/texts/{key}", "put")).toContain(status);
    for (const status of ["204", "403", "404"]) expect(declared("/organization/texts/{key}", "delete")).toContain(status);
  });

  it("valida como o servidor: campo desconhecido, chave sem fecho, tamanhos, igual à plataforma, link e chave fora do catálogo", async () => {
    await login("ana@exemplo.com");
    const bad = async (key: string, body: unknown, field: string) => {
      const r = await put(key, body);
      expect(r.status).toBe(400);
      expect((await r.json()).fields[0].field).toBe(field);
    };
    await bad("REMINDER_DUE", { subject: "A", body: "Seu CPF {cpf}" }, "body");
    await bad("WELCOME", { subject: "A", body: "Valor {valor}" }, "body");
    await bad("WELCOME", { subject: "A", body: "Olá {nome" }, "body");
    await bad("WELCOME", { subject: "", body: "Olá" }, "subject");
    await bad("WELCOME", { subject: "x".repeat(151), body: "Olá" }, "subject");
    await bad("WELCOME", { subject: "A", body: "" }, "body");
    await bad("WELCOME", { subject: "A", body: "x".repeat(2001) }, "body");
    await bad("ANNOUNCEMENT_GENERAL", { subject: "A", body: "[aqui](javascript:alert(1))" }, "body");
    const platform = (await (await list()).json()).models.find((m: { key: string }) => m.key === "WELCOME").platform;
    await bad("WELCOME", platform, "body");
    expect((await put("PASSWORD_RESET", { subject: "A", body: "B" })).status).toBe(404);
    expect((await del("PASSWORD_RESET")).status).toBe(404);
  });

  it("Administrador lê com canManage falso e recebe 403 ao alterar; Professor recebe 403 em tudo", async () => {
    await login("varios@exemplo.com", "00000000-0000-0000-0000-000000000011");
    expect((await (await list()).json()).canManage).toBe(false);
    expect((await put("WELCOME", { subject: "A", body: "B" })).status).toBe(403);
    expect((await del("WELCOME")).status).toBe(403);
    await fetch("/api/v1/auth/session/link", { method: "PUT", headers: json, body: JSON.stringify({ linkId: "00000000-0000-0000-0000-000000000012" }) });
    expect((await list()).status).toBe(403);
  });

  it("GET /announcements/templates traz os cinco tipos e usa o texto próprio quando há", async () => {
    await login("ana@exemplo.com");
    const before = await (await fetch("/api/v1/announcements/templates")).json();
    expect(Object.keys(before).sort()).toEqual(required("AnnouncementTemplates"));
    expect(Object.keys(before.templates).sort()).toEqual(["CANCELED_CLASS", "GENERAL", "NO_CLASS_DAY", "SCHEDULE_CHANGE", "TEACHER_CHANGE"]);
    expect(before.templates.CANCELED_CLASS.body).toContain("{turma}");
    await put("ANNOUNCEMENT_GENERAL", { subject: "Meu aviso", body: "Texto de {unidade}" });
    const after = await (await fetch("/api/v1/announcements/templates")).json();
    expect(after.templates.GENERAL).toEqual({ title: "Meu aviso", body: "Texto de {unidade}" });
    expect(after.templates.CANCELED_CLASS.title).toBe("Aula cancelada");
  });
});
