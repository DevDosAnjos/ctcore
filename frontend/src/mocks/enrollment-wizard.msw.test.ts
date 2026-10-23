import { readFileSync } from "node:fs";
import { join } from "node:path";
import { parse } from "yaml";

const openapi = parse(readFileSync(join(process.cwd(), "../docs/openapi.yaml"), "utf8"));
const json = { "Content-Type": "application/json" };
const login = async (email: string, linkId?: string) => {
  await fetch("/api/v1/auth/login", { method: "POST", headers: json, body: JSON.stringify({ email, password: "segredo123" }) });
  if (linkId) await fetch("/api/v1/auth/session/link", { method: "PUT", headers: json, body: JSON.stringify({ linkId }) });
};
const send = (method: string, path: string, body?: unknown) => fetch(`/api/v1${path}`, { method, headers: json, body: body === undefined ? undefined : JSON.stringify(body) });
const ADULT = "00000000-0000-0000-0000-000000000701";
const MINOR = "00000000-0000-0000-0000-000000000702";
const CENTRO = "00000000-0000-0000-0000-000000000021";
const BJJ_CLASS = "00000000-0000-0000-0000-000000000601";
const BJJ_PLAN = "00000000-0000-0000-0000-000000000602";
const bjj = (extra: Record<string, unknown> = {}) => ({ unitId: CENTRO, modalityId: "00000000-0000-0000-0000-000000000103", primaryClassId: BJJ_CLASS, planId: BJJ_PLAN, dueDay: 5, ...extra });
// FormData e File do jsdom não passam pelo fetch do Node; o corpo multipart vai montado à mão.
const file = (type: string, size = 10) =>
  fetch(`/api/v1/students/${ADULT}/enrollment/origin-proof`, {
    method: "POST",
    headers: { "Content-Type": "multipart/form-data; boundary=x" },
    body: `--x\r\nContent-Disposition: form-data; name="file"; filename="f.bin"\r\nContent-Type: ${type}\r\n\r\n${"a".repeat(size)}\r\n--x--\r\n`,
  });

describe("nova matrícula x OpenAPI (RF-MAT-01, #67)", () => {
  it("o contrato declara as três rotas e os esquemas", () => {
    for (const [path, codes] of [
      ["/students/{id}/enrollment/preview", ["200", "400", "401", "403", "404", "422"]],
      ["/students/{id}/enrollment/conclude", ["201", "400", "401", "403", "404", "409", "422"]],
      ["/students/{id}/enrollment/origin-proof", ["201", "400", "401", "403", "404", "422"]],
    ] as const) {
      expect(Object.keys(openapi.paths[path].post.responses)).toEqual(expect.arrayContaining([...codes]));
    }
    expect([...openapi.components.schemas.EnrollmentConcluded.required].sort()).toEqual(["charges", "enrollment", "inviteStatus", "summary", "termPending"]);
    expect(openapi.components.schemas.OriginProofStored.required).toEqual(["proofKey"]);
  });

  it("sem sessão 401 e Professor 403 nas três rotas", async () => {
    expect((await send("POST", `/students/${ADULT}/enrollment/preview`, { modalities: [bjj()] })).status).toBe(401);
    await login("varios@exemplo.com", "00000000-0000-0000-0000-000000000013");
    expect((await send("POST", `/students/${ADULT}/enrollment/preview`, { modalities: [bjj()] })).status).toBe(403);
    expect((await send("POST", `/students/${ADULT}/enrollment/conclude`, { modalities: [bjj()] })).status).toBe(403);
    expect((await file("application/pdf")).status).toBe(403);
  });

  it("a prévia devolve o formato do contrato, a segunda cobrança sugerida e não grava nada", async () => {
    await login("ana@exemplo.com");
    const res = await send("POST", `/students/${ADULT}/enrollment/preview`, { modalities: [bjj({ benefit: { type: "PERCENT", value: 10, reason: "Bolsa", decidedBy: "Mestre" } })], units: [{ unitId: CENTRO }] });
    expect(res.status).toBe(200);
    const preview = await res.json();
    expect(Object.keys(preview).sort()).toEqual([...openapi.components.schemas.EnrollmentPreview.required].sort());
    expect(Object.keys(preview.modalities[0]).sort()).toEqual([...openapi.components.schemas.EnrollmentPreviewModality.required].sort());
    expect(Object.keys(preview.units[0]).sort()).toEqual([...openapi.components.schemas.EnrollmentPreviewUnit.required].sort());
    expect(preview.modalities[0]).toMatchObject({ planCents: 20000, discountCents: 2000, monthlyCents: 18000 });
    expect(new Date(preview.units[0].suggestedSecondDueOn).getUTCDate()).toBe(5);
    expect((await (await send("GET", `/students/${ADULT}/enrollment`)).json()).status).toBe("NONE");
  });

  it("recusas da prévia: segunda cobrança fora de 15 a 60 dias e isenção sem motivo", async () => {
    await login("ana@exemplo.com");
    const day = (n: number) => new Date(Date.now() + n * 86_400_000).toISOString().slice(0, 10);
    expect((await send("POST", `/students/${ADULT}/enrollment/preview`, { modalities: [bjj()], units: [{ unitId: CENTRO, secondDueOn: day(14) }] })).status).toBe(422);
    expect((await send("POST", `/students/${ADULT}/enrollment/preview`, { modalities: [bjj()], units: [{ unitId: CENTRO, secondDueOn: day(61) }] })).status).toBe(422);
    expect((await send("POST", `/students/${ADULT}/enrollment/preview`, { modalities: [bjj()], units: [{ unitId: CENTRO, feeExempt: true }] })).status).toBe(422);
    expect((await send("POST", `/students/${ADULT}/enrollment/preview`, { modalities: [] })).status).toBe(400);
  });

  it("conclui no formato do contrato, deixa o aluno ativo, guarda o benefício e a segunda chamada dá 409", async () => {
    await login("ana@exemplo.com");
    const body = { modalities: [bjj({ benefit: { type: "PERCENT", value: 10, reason: "Bolsa", decidedBy: "Mestre" } })], units: [{ unitId: CENTRO }] };
    const done = await send("POST", `/students/${MINOR}/enrollment/conclude`, body);
    expect(done.status).toBe(201);
    const result = await done.json();
    expect(Object.keys(result).sort()).toEqual([...openapi.components.schemas.EnrollmentConcluded.required].sort());
    expect(result).toMatchObject({ termPending: true, inviteStatus: "MINOR", enrollment: { status: "ACTIVE" } });
    expect((await send("POST", `/students/${MINOR}/enrollment/conclude`, body)).status).toBe(409);
    expect((await (await send("GET", `/students/${MINOR}/enrollment`)).json()).modalities).toHaveLength(1);
  });

  it("tudo ou nada: dia de vencimento inválido não grava nada", async () => {
    await login("ana@exemplo.com");
    expect((await send("POST", `/students/${ADULT}/enrollment/conclude`, { modalities: [bjj({ dueDay: 9 })] })).status).toBe(422);
    expect((await (await send("GET", `/students/${ADULT}/enrollment`)).json()).status).toBe("NONE");
  });

  it("comprovante: aceita PDF, recusa outro tipo e acima de 5 MB; a chave vale só para o mesmo aluno", async () => {
    await login("ana@exemplo.com");
    expect((await file("image/gif")).status).toBe(422);
    expect((await file("application/pdf", 5 * 1024 * 1024 + 1)).status).toBe(422);
    const ok = await file("application/pdf");
    expect(ok.status).toBe(201);
    const { proofKey } = await ok.json();
    expect(proofKey).toContain(ADULT);
    const origin = { levelId: "x", degree: 1, proofKey };
    // modalidade sem graduação ligada: a origem é recusada
    expect((await send("POST", `/students/${MINOR}/enrollment/conclude`, { modalities: [bjj({ origin })] })).status).toBe(422);
  });
});
