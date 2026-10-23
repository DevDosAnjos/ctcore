import { readFileSync } from "node:fs";
import { join } from "node:path";
import { parse } from "yaml";

const openapi = parse(readFileSync(join(process.cwd(), "../docs/openapi.yaml"), "utf8"));
const json = { "Content-Type": "application/json" };
const login = (email: string) => fetch("/api/v1/auth/login", { method: "POST", headers: json, body: JSON.stringify({ email, password: "segredo123" }) });
const send = (method: string, path: string, body?: unknown) => fetch(`/api/v1${path}`, { method, headers: json, body: body === undefined ? undefined : JSON.stringify(body) });
const MINOR = "00000000-0000-0000-0000-000000000702";
const CENTRO = "00000000-0000-0000-0000-000000000021";
const bjj = (extra: Record<string, unknown> = {}) => ({
  unitId: CENTRO, modalityId: "00000000-0000-0000-0000-000000000103", primaryClassId: "00000000-0000-0000-0000-000000000601", planId: "00000000-0000-0000-0000-000000000602", dueDay: 5, ...extra,
});
const preview = async (days = 60) => (await send("GET", `/billing/generation-preview?unitId=${CENTRO}&days=${days}`)).json();
const enroll = async (modality = bjj()) => {
  await login("ana@exemplo.com");
  const done = await send("POST", `/students/${MINOR}/enrollment/conclude`, { modalities: [modality], units: [{ unitId: CENTRO }] });
  expect(done.status).toBe(201);
  return (await done.json()).enrollment.modalities[0].id as string;
};
const next = `/students/${MINOR}/modalities`;
const today = () => new Date().toISOString().slice(0, 10);
const endOfMonth = (ref: string) => new Date(Date.UTC(Number(ref.slice(0, 4)), Number(ref.slice(5, 7)), 0)).toISOString().slice(0, 10);

describe("prévia da geração e ajustes x OpenAPI (RF-COB-03, RN-COB-02, #74)", () => {
  it("o contrato declara as rotas e os códigos", () => {
    expect(Object.keys(openapi.paths["/students/{id}/modalities/{modalityRowId}/next-charge"].patch.responses)).toEqual(expect.arrayContaining(["200", "400", "401", "403", "404", "422"]));
    expect(Object.keys(openapi.paths["/students/{id}/modalities/{modalityRowId}/next-charge/cancel"].post.responses)).toEqual(expect.arrayContaining(["200", "400", "401", "403", "404", "409", "422"]));
  });

  it("prévia vazia sem matrículas; unitId obrigatório; days fora de 1 a 60 é 400; sem sessão 401", async () => {
    expect((await send("GET", `/billing/generation-preview?unitId=${CENTRO}`)).status).toBe(401);
    await login("ana@exemplo.com");
    const empty = await preview(10);
    expect(Object.keys(empty).sort()).toEqual([...openapi.components.schemas.GenerationPreview.required].sort());
    expect(empty).toMatchObject({ days: [], count: 0, totalCents: 0, skipped: [] });
    expect((await send("GET", "/billing/generation-preview")).status).toBe(400);
    expect((await send("GET", `/billing/generation-preview?unitId=${CENTRO}&days=61`)).status).toBe(400);
  });

  it("com dados: a linha vem com os campos do contrato, ajustável, e o total bate", async () => {
    const rowId = await enroll();
    const p = await preview();
    expect(p.count).toBeGreaterThanOrEqual(1);
    const [day] = p.days;
    expect(Object.keys(day.lines[0]).sort()).toEqual([...openapi.components.schemas.GenerationPreviewLine.required].sort());
    expect(day.lines[0]).toMatchObject({ modalityRowId: rowId, adjustable: true, amountCents: 20000 });
    expect(p.totalCents).toBe(p.days.reduce((t: number, d: { totalCents: number }) => t + d.totalCents, 0));
    expect(p.days.flatMap((d: { lines: { adjustable: boolean }[] }) => d.lines).filter((l: { adjustable: boolean }) => l.adjustable)).toHaveLength(1);
    expect(p.previousMonthTotalCents).toBe(20000);
  });

  it("trocar vencimento: 200 e a linha muda de dia; 400 para data passada, inválida e depois do mês; 404; 401", async () => {
    const rowId = await enroll();
    const ref = (await preview()).days[0].lines[0].referenceMonth as string;
    const path = `${next}/${rowId}/next-charge`;
    for (const dueOn of ["2000-01-01", "2999-01-01", "abc", "2026-02-30", undefined]) expect((await send("PATCH", path, { dueOn })).status).toBe(400);
    expect((await send("PATCH", `${next}/00000000-0000-0000-0000-00000000dead/next-charge`, { dueOn: endOfMonth(ref) })).status).toBe(404);
    const ok = await send("PATCH", path, { dueOn: endOfMonth(ref) });
    expect(ok.status).toBe(200);
    expect(await ok.json()).toEqual({ dueOn: endOfMonth(ref), referenceMonth: ref });
    expect((await preview()).days[0].dueOn).toBe(endOfMonth(ref));
    expect(endOfMonth(ref) >= today()).toBe(true);
  });

  it("cancelar: 400 sem motivo, 409 com mês diferente, 200 grava e a prévia perde a linha; a seguinte vira a próxima", async () => {
    const rowId = await enroll();
    const ref = (await preview()).days[0].lines[0].referenceMonth as string;
    const path = `${next}/${rowId}/next-charge/cancel`;
    expect((await send("POST", path, { reason: "   " })).status).toBe(400);
    expect((await send("POST", path, { reason: "x".repeat(201) })).status).toBe(400);
    expect((await send("POST", path, { reason: "Viagem", referenceMonth: "1999-01-01" })).status).toBe(409);
    expect((await send("POST", `${next}/00000000-0000-0000-0000-00000000dead/next-charge/cancel`, { reason: "Viagem" })).status).toBe(404);
    const ok = await send("POST", path, { reason: " Aluno viajando ", referenceMonth: ref });
    expect(ok.status).toBe(200);
    expect(Object.keys(await ok.json()).sort()).toEqual([...openapi.components.schemas.NextChargeCanceled.required].sort());
    expect((await preview()).days.flatMap((d: { lines: { referenceMonth: string }[] }) => d.lines).every((l: { referenceMonth: string }) => l.referenceMonth !== ref)).toBe(true);
  });

  it("bolsa de 100%: aparece em não cobrados e trocar vencimento ou cancelar dão 422", async () => {
    const rowId = await enroll(bjj({ benefit: { type: "PERCENT", value: 100, reason: "Bolsa", decidedBy: "Mestre" } }));
    const p = await preview();
    expect(p.days).toEqual([]);
    expect(p.skipped).toHaveLength(1);
    expect(p.skipped[0]).toMatchObject({ reason: "FULL_BENEFIT", studentId: MINOR });
    expect((await send("PATCH", `${next}/${rowId}/next-charge`, { dueOn: endOfMonth(p.skipped[0].referenceMonth) })).status).toBe(422);
    expect((await send("POST", `${next}/${rowId}/next-charge/cancel`, { reason: "Viagem" })).status).toBe(422);
  });

  it("Administrador vê a própria unidade e recebe prévia vazia de outra", async () => {
    await enroll();
    await login("varios@exemplo.com");
    await send("PUT", "/auth/session/link", { linkId: "00000000-0000-0000-0000-000000000011" });
    expect((await preview()).count).toBeGreaterThanOrEqual(1);
    const other = await (await send("GET", "/billing/generation-preview?unitId=00000000-0000-0000-0000-000000000022")).json();
    expect(other.count).toBe(0);
  });
});
