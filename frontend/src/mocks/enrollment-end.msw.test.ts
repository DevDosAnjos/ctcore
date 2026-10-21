import { readFileSync } from "node:fs";
import { join } from "node:path";
import { parse } from "yaml";

const openapi = parse(readFileSync(join(process.cwd(), "../docs/openapi.yaml"), "utf8"));
const json = { "Content-Type": "application/json" };
const send = (method: string, path: string, body?: unknown) => fetch(`/api/v1${path}`, { method, headers: json, body: body === undefined ? undefined : JSON.stringify(body) });
const ADULT = "00000000-0000-0000-0000-000000000701";

describe("encerrar modalidade x OpenAPI (RF-MAT-03, RN-MAT-17)", () => {
  const base = "/students/{id}/modalities/{modalityRowId}";

  it("o contrato declara as duas rotas e os esquemas", () => {
    expect(Object.keys(openapi.paths[`${base}/end`].post.responses)).toEqual(expect.arrayContaining(["200", "400", "401", "403", "404"]));
    expect(Object.keys(openapi.paths[`${base}/end-preview`].get.responses)).toEqual(expect.arrayContaining(["200", "401", "403", "404"]));
    expect(openapi.components.schemas.ModalityEndInput.required).toEqual(["reason"]);
    expect(openapi.components.schemas.EndPreview.required).toEqual(["openCharges"]);
  });

  it("encerra a linha com motivo, devolve a matrícula, prévia vazia e 404 depois de encerrada", async () => {
    await fetch("/api/v1/auth/login", { method: "POST", headers: json, body: JSON.stringify({ email: "ana@exemplo.com", password: "segredo123" }) });
    const modality = { unitId: "00000000-0000-0000-0000-000000000021", modalityId: "00000000-0000-0000-0000-000000000103", primaryClassId: "00000000-0000-0000-0000-000000000601", planId: "00000000-0000-0000-0000-000000000602", dueDay: 5 };
    const created = await (await send("POST", `/students/${ADULT}/modalities`, { modalities: [modality] })).json();
    const id = created.modalities[0].id as string;
    const url = `/students/${ADULT}/modalities/${id}`;

    expect(await (await send("GET", `${url}/end-preview`)).json()).toEqual({ openCharges: [] });
    for (const bad of [{}, { reason: "NOPE" }, { reason: "OTHER", note: "x".repeat(501) }]) expect((await send("POST", `${url}/end`, bad)).status).toBe(400);

    const ended = await send("POST", `${url}/end`, { reason: "FINANCIAL", note: "Sem tempo" });
    expect(ended.status).toBe(200);
    const body = await ended.json();
    expect(Object.keys(body).sort()).toEqual([...openapi.components.schemas.Enrollment.required].sort());
    expect(body).toMatchObject({ status: "FORMER", modalities: [{ id, status: "ENDED" }] });
    expect(body.modalities[0].endedAt).not.toBeNull();
    expect((await send("POST", `${url}/end`, { reason: "OTHER" })).status).toBe(404);
    expect((await send("GET", `${url}/end-preview`)).status).toBe(404);
  });
});
