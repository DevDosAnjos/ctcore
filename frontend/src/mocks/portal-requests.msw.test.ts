import { readFileSync } from "node:fs";
import { join } from "node:path";
import { parse } from "yaml";

const openapi = parse(readFileSync(join(process.cwd(), "../docs/openapi.yaml"), "utf8"));
const json = { "Content-Type": "application/json" };
const send = (method: string, path: string, body?: unknown) =>
  fetch(`/api/v1${path}`, { method, headers: json, body: body === undefined ? undefined : JSON.stringify(body) });
const login = async (email: string, linkId?: string) => {
  await send("POST", "/auth/login", { email, password: "segredo123" });
  if (linkId) await send("PUT", "/auth/session/link", { linkId });
};
const ADULT = "00000000-0000-0000-0000-000000000701";
const CENTRO = "00000000-0000-0000-0000-000000000021";
const JUDO = "00000000-0000-0000-0000-000000000104";
const BOXE = "00000000-0000-0000-0000-000000000100";
const JUDO_CLASS = "00000000-0000-0000-0000-000000000602";
const JUDO_PLAN = "00000000-0000-0000-0000-000000000601";
const ADMIN_LINK = "00000000-0000-0000-0000-000000000011";
const STUDENT_LINK = "00000000-0000-0000-0000-000000000012";
const TEACHER_LINK = "00000000-0000-0000-0000-000000000013";
const required = (name: string): string[] => [...openapi.components.schemas[name].required].sort();

// Gestão prepara o aluno: matrícula em Judô, uma segunda turma e um segundo plano de Judô, Boxe ofertado na unidade. Depois a conta do aluno aceita o termo.
async function setup() {
  await login("ana@exemplo.com");
  const klass = await (await send("POST", "/classes", { unitId: CENTRO, name: "Judô Noite", modalityId: JUDO, schedule: [{ weekday: 5, startsAt: "20:00", endsAt: "21:00" }], headTeacherId: "00000000-0000-0000-0000-000000000502" })).json();
  const plan = await (await send("POST", "/plans", { unitId: CENTRO, modalityId: JUDO, name: "Judô Livre", valueCents: 20000, weeklyFrequency: null, validOtherUnits: false })).json();
  await send("PUT", `/modalities/${BOXE}/activation`, { active: true });
  await send("PUT", `/units/${CENTRO}/modalities/${BOXE}`, { offered: true });
  const enrollment = await (await send("POST", `/students/${ADULT}/modalities`, { modalities: [{ unitId: CENTRO, modalityId: JUDO, primaryClassId: JUDO_CLASS, planId: JUDO_PLAN, dueDay: 5 }] })).json();
  const row = enrollment.modalities[0].id as string;
  await login("varios@exemplo.com", STUDENT_LINK);
  const pending = await (await send("GET", "/portal/term")).json();
  await send("POST", `/portal/students/${ADULT}/term-acceptance`, { version: pending[0].term.version });
  return { row, klass: klass.id as string, plan: plan.id as string };
}

describe("pedidos do portal x OpenAPI (RF-MAT-04, #86)", () => {
  it("o contrato declara as rotas e o selo novo", () => {
    for (const [path, method, codes] of [
      ["/portal/students/{id}/request-options", "get", ["200", "401", "403", "404"]],
      ["/portal/students/{id}/requests", "get", ["200", "401", "403", "404"]],
      ["/portal/students/{id}/requests", "post", ["201", "400", "403", "404", "409"]],
      ["/portal-requests", "get", ["200", "400", "401", "403"]],
      ["/portal-requests/{id}", "get", ["200", "401", "403", "404"]],
      ["/portal-requests/{id}/approve", "post", ["200", "400", "404", "409"]],
      ["/portal-requests/{id}/refuse", "post", ["200", "400", "404", "409"]],
    ] as const) {
      expect(Object.keys(openapi.paths[path][method].responses)).toEqual(expect.arrayContaining([...codes]));
    }
    expect(openapi.components.schemas.NotificationType.enum).toContain("PORTAL_REQUEST");
    expect(openapi.components.schemas.EnrollmentModality.required).toContain("closureRequestedAt");
  });

  it("sem sessão 401; gestão e Professor 403 nas rotas do portal; portal 403 nas da gestão", async () => {
    expect((await send("GET", `/portal/students/${ADULT}/requests`)).status).toBe(401);
    expect((await send("GET", "/portal-requests")).status).toBe(401);
    await login("ana@exemplo.com");
    expect((await send("GET", `/portal/students/${ADULT}/request-options`)).status).toBe(403);
    expect((await send("POST", `/portal/students/${ADULT}/requests`, { kind: "CLOSURE" })).status).toBe(403);
    await login("varios@exemplo.com", TEACHER_LINK);
    expect((await send("GET", "/portal-requests")).status).toBe(403);
    await login("varios@exemplo.com", STUDENT_LINK);
    expect((await send("GET", "/portal-requests")).status).toBe(403);
  });

  it("as opções trazem turma, plano e oferta; os quatro pedidos nascem abertos sem mexer na matrícula", async () => {
    const { row, klass, plan } = await setup();
    const options = await (await send("GET", `/portal/students/${ADULT}/request-options`)).json();
    expect(Object.keys(options).sort()).toEqual(required("PortalRequestOptions"));
    expect(options.modalities[0]).toMatchObject({ modalityRowId: row, classes: [{ id: klass }], plans: [{ id: plan }], closureRequestedAt: null });
    expect(options.offers).toEqual([expect.objectContaining({ unitId: CENTRO, modalityId: BOXE })]);

    for (const body of [
      { kind: "CLASS_CHANGE", modalityRowId: row, targetClassId: klass },
      { kind: "PLAN_CHANGE", modalityRowId: row, targetPlanId: plan, note: "Mais aulas" },
      { kind: "NEW_MODALITY", targetUnitId: CENTRO, targetModalityId: BOXE },
    ]) {
      const created = await send("POST", `/portal/students/${ADULT}/requests`, body);
      expect(created.status).toBe(201);
      const view = await created.json();
      expect(Object.keys(view).sort()).toEqual(expect.arrayContaining(required("PortalRequest")));
      expect(view.status).toBe("OPEN");
    }
    const enrollment = await (await send("GET", `/portal/students/${ADULT}/request-options`)).json();
    expect(enrollment.modalities[0].currentClass.id).toBe(JUDO_CLASS);
    expect(enrollment.modalities[0].currentPlan.id).toBe(JUDO_PLAN);
    expect(await (await send("GET", `/portal/students/${ADULT}/requests`)).json()).toHaveLength(3);
  });

  it("dados inválidos dão 400; repetir dá 409 REQUEST_ALREADY_OPEN; aluno de outra conta 404", async () => {
    const { row, klass } = await setup();
    const bad = (body: unknown) => send("POST", `/portal/students/${ADULT}/requests`, body);
    expect((await bad({ kind: "NOPE" })).status).toBe(400);
    expect((await bad({ kind: "CLOSURE", modalityRowId: row, reason: "NOPE" })).status).toBe(400);
    expect((await bad({ kind: "CLASS_CHANGE", modalityRowId: row, targetClassId: JUDO_CLASS })).status).toBe(400);
    expect((await bad({ kind: "PLAN_CHANGE", modalityRowId: row, targetPlanId: JUDO_PLAN })).status).toBe(400);
    expect((await bad({ kind: "NEW_MODALITY", targetUnitId: CENTRO, targetModalityId: JUDO })).status).toBe(400);
    expect((await bad({ kind: "CLASS_CHANGE", modalityRowId: row, targetClassId: klass, note: "x".repeat(501) })).status).toBe(400);
    expect(await (await send("GET", `/portal/students/${ADULT}/requests`)).json()).toEqual([]);

    expect((await bad({ kind: "CLASS_CHANGE", modalityRowId: row, targetClassId: klass })).status).toBe(201);
    const again = await bad({ kind: "CLASS_CHANGE", modalityRowId: row, targetClassId: klass });
    expect(again.status).toBe(409);
    expect((await again.json()).code).toBe("REQUEST_ALREADY_OPEN");
    expect((await send("GET", `/portal/students/00000000-0000-0000-0000-000000000702/requests`)).status).toBe(404);
  });

  it("encerramento aberto marca a modalidade, trava troca de turma e de plano, e a gestão encerra", async () => {
    const { row, klass, plan } = await setup();
    expect((await send("POST", `/portal/students/${ADULT}/requests`, { kind: "CLOSURE", modalityRowId: row, reason: "FINANCIAL" })).status).toBe(201);
    expect((await send("POST", `/portal/students/${ADULT}/requests`, { kind: "CLASS_CHANGE", modalityRowId: row, targetClassId: klass })).status).toBe(409);
    expect((await send("POST", `/portal/students/${ADULT}/requests`, { kind: "PLAN_CHANGE", modalityRowId: row, targetPlanId: plan })).status).toBe(409);

    await login("ana@exemplo.com");
    const enrollment = await (await send("GET", `/students/${ADULT}/enrollment`)).json();
    expect(enrollment.status).toBe("ACTIVE");
    expect(enrollment.modalities[0].closureRequestedAt).not.toBeNull();
    const [closure] = (await (await send("GET", "/portal-requests")).json()).items;
    expect((await send("POST", `/portal-requests/${closure.id}/approve`, {})).status).toBe(400);

    expect((await send("POST", `/students/${ADULT}/modalities/${row}/end`, { reason: "FINANCIAL" })).status).toBe(200);
    expect((await (await send("GET", `/portal-requests/${closure.id}`)).json()).status).toBe("CLOSED");
  });

  it("a gestão lista os abertos com filtros, aprova, recusa e responde 409 ao decidir de novo", async () => {
    const { row, klass, plan } = await setup();
    await send("POST", `/portal/students/${ADULT}/requests`, { kind: "CLASS_CHANGE", modalityRowId: row, targetClassId: klass });
    await send("POST", `/portal/students/${ADULT}/requests`, { kind: "PLAN_CHANGE", modalityRowId: row, targetPlanId: plan });

    await login("varios@exemplo.com", ADMIN_LINK);
    const open = await (await send("GET", "/portal-requests")).json();
    expect(Object.keys(open).sort()).toEqual(["items", "page", "size", "total"]);
    expect(open.items.map((r: { kind: string }) => r.kind)).toEqual(["CLASS_CHANGE", "PLAN_CHANGE"]);
    expect((await (await send("GET", "/portal-requests?kind=PLAN_CHANGE")).json()).items).toHaveLength(1);
    expect((await (await send("GET", `/portal-requests?unitId=00000000-0000-0000-0000-000000000022`)).json()).items).toEqual([]);
    expect((await send("GET", "/portal-requests?status=NOPE")).status).toBe(400);

    const [first, second] = open.items;
    const approved = await send("POST", `/portal-requests/${first.id}/approve`, { message: "Pode vir amanhã" });
    expect(approved.status).toBe(200);
    expect(await approved.json()).toMatchObject({ status: "APPROVED", response: "Pode vir amanhã" });
    const repeated = await send("POST", `/portal-requests/${first.id}/approve`, {});
    expect(repeated.status).toBe(409);
    expect((await repeated.json()).code).toBe("REQUEST_NOT_OPEN");

    expect((await send("POST", `/portal-requests/${second.id}/refuse`, {})).status).toBe(400);
    expect((await send("POST", `/portal-requests/${second.id}/refuse`, { message: "ok" })).status).toBe(400);
    expect((await (await send("GET", `/portal-requests/${second.id}`)).json()).status).toBe("OPEN");
    expect(await (await send("POST", `/portal-requests/${second.id}/refuse`, { message: "Sem vaga no plano" })).json()).toMatchObject({ status: "REFUSED", response: "Sem vaga no plano" });
    expect((await (await send("GET", "/portal-requests")).json()).items).toEqual([]);
    expect((await (await send("GET", "/portal-requests?status=ALL")).json()).items).toHaveLength(2);
    expect((await send("GET", "/portal-requests/00000000-0000-0000-0000-0000000009ff")).status).toBe(404);
  });
});
