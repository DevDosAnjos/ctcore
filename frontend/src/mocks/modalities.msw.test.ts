import { readFileSync } from "node:fs";
import { join } from "node:path";
import { parse } from "yaml";

const openapi = parse(
  readFileSync(join(process.cwd(), "../docs/openapi.yaml"), "utf8"),
);
const json = { "Content-Type": "application/json" };
const login = async (email: string, linkId?: string) => {
  await fetch("/api/v1/auth/login", {
    method: "POST",
    headers: json,
    body: JSON.stringify({ email, password: "segredo123" }),
  });
  if (linkId)
    await fetch("/api/v1/auth/session/link", {
      method: "PUT",
      headers: json,
      body: JSON.stringify({ linkId }),
    });
};
const list = () => fetch("/api/v1/modalities");
const activate = (id: string, body: unknown) =>
  fetch(`/api/v1/modalities/${id}/activation`, {
    method: "PUT",
    headers: json,
    body: JSON.stringify(body),
  });
const ask = (body: unknown) =>
  fetch("/api/v1/modalities/requests", {
    method: "POST",
    headers: json,
    body: JSON.stringify(body),
  });
const declared = (path: string, method: string) =>
  Object.keys(openapi.paths[path][method].responses);

describe("modalidades x OpenAPI (RF-MOD-01, CA14)", () => {
  it("sem sessão as quatro rotas respondem 401, declarado no contrato", async () => {
    expect((await list()).status).toBe(401);
    expect((await activate("x", { active: true })).status).toBe(401);
    expect((await ask({ name: "Sambo" })).status).toBe(401);
    expect((await fetch("/api/v1/modalities/requests")).status).toBe(401);
    for (const [path, method] of [
      ["/modalities", "get"],
      ["/modalities/{id}/activation", "put"],
      ["/modalities/requests", "get"],
      ["/modalities/requests", "post"],
    ]) {
      expect(declared(path, method)).toContain("401");
    }
  });

  it("Coordenador: lista com as chaves do contrato; ativa, desativa, 404, 400 e 409 do contrato", async () => {
    await login("ana@exemplo.com");
    const required: string[] = openapi.components.schemas.Modality.required;
    const items = await (await list()).json();
    expect(items).toHaveLength(14);
    for (const m of items)
      expect(Object.keys(m).sort()).toEqual([...required].sort());
    expect(
      items.every(
        (m: { active: boolean; canManage: boolean }) =>
          !m.active && m.canManage,
      ),
    ).toBe(true);

    const boxe = items.find((m: { name: string }) => m.name === "Boxe").id;
    expect((await activate(boxe, { active: true })).status).toBe(200);
    expect((await activate(boxe, {})).status).toBe(400);
    expect(
      (await activate("00000000-0000-0000-0000-0000000009ff", { active: true }))
        .status,
    ).toBe(404);
    const inUse = await activate(boxe, { active: false });
    expect(inUse.status).toBe(409);
    expect((await inUse.json()).code).toBe("MODALITY_IN_USE");
    for (const status of ["200", "400", "404", "409"])
      expect(declared("/modalities/{id}/activation", "put")).toContain(status);

    const created = await ask({ name: "Sambo", note: "Temos turma" });
    expect(created.status).toBe(201);
    const requestKeys: string[] =
      openapi.components.schemas.ModalityRequest.required;
    expect(Object.keys(await created.json()).sort()).toEqual(
      [...requestKeys].sort(),
    );
    expect((await ask({ name: "sambo" })).status).toBe(409);
    expect((await ask({ name: "Judô" })).status).toBe(409);
    expect((await ask({ name: "a" })).status).toBe(400);
    expect(
      await (await fetch("/api/v1/modalities/requests")).json(),
    ).toHaveLength(1);
    for (const status of ["201", "400", "409"])
      expect(declared("/modalities/requests", "post")).toContain(status);
  });

  it("Administrador lê com canManage falso e recebe 403 ao alterar; Professor recebe 403 em tudo", async () => {
    await login("varios@exemplo.com", "00000000-0000-0000-0000-000000000011");
    const admin = await (await list()).json();
    expect(admin[0].canManage).toBe(false);
    expect((await activate(admin[0].id, { active: true })).status).toBe(403);
    expect((await ask({ name: "Sambo" })).status).toBe(403);
    await fetch("/api/v1/auth/session/link", {
      method: "PUT",
      headers: json,
      body: JSON.stringify({ linkId: "00000000-0000-0000-0000-000000000012" }),
    });
    expect((await list()).status).toBe(403);
    expect((await fetch("/api/v1/modalities/requests")).status).toBe(403);
    expect(declared("/modalities", "get")).toContain("403");
  });
});
