import { readFileSync } from "node:fs";
import { join } from "node:path";
import { parse } from "yaml";

const openapi = parse(readFileSync(join(process.cwd(), "../docs/openapi.yaml"), "utf8"));
const json = { "Content-Type": "application/json" };
const login = async (email: string, linkId?: string) => {
  await fetch("/api/v1/auth/login", { method: "POST", headers: json, body: JSON.stringify({ email, password: "segredo123" }) });
  if (linkId) await fetch("/api/v1/auth/session/link", { method: "PUT", headers: json, body: JSON.stringify({ linkId }) });
};
const send = (method: string, path: string, body?: unknown) =>
  fetch(`/api/v1${path}`, { method, headers: json, body: body === undefined ? undefined : JSON.stringify(body) });
const ADULT = "00000000-0000-0000-0000-000000000701";
const CENTRO = "00000000-0000-0000-0000-000000000021";
const BJJ_CLASS = "00000000-0000-0000-0000-000000000601";
const JUDO_CLASS = "00000000-0000-0000-0000-000000000602";
const JUDO_PLAN = "00000000-0000-0000-0000-000000000601";
const BJJ_PLAN = "00000000-0000-0000-0000-000000000602";
const OLD_PLAN = "00000000-0000-0000-0000-000000000604";
const bjj = (extra: Record<string, unknown> = {}) => ({ unitId: CENTRO, modalityId: "00000000-0000-0000-0000-000000000103", primaryClassId: BJJ_CLASS, planId: BJJ_PLAN, dueDay: 5, ...extra });
const judo = (extra: Record<string, unknown> = {}) => ({ unitId: CENTRO, modalityId: "00000000-0000-0000-0000-000000000104", primaryClassId: JUDO_CLASS, planId: JUDO_PLAN, ...extra });

describe("matrícula x OpenAPI (RN-MAT-01, 02, 03, 04, 19)", () => {
  it("o contrato declara as rotas, os esquemas e a busca por CPF ou e-mail", () => {
    expect(Object.keys(openapi.paths["/students/{id}/modalities"].post.responses)).toEqual(expect.arrayContaining(["201", "400", "401", "403", "404", "409", "422"]));
    expect(Object.keys(openapi.paths["/students/{id}/enrollment"].get.responses)).toEqual(expect.arrayContaining(["200", "401", "403", "404"]));
    expect(openapi.paths["/students/lookup"].get.parameters.map((p: { name: string }) => p.name)).toEqual(["cpf", "email"]);
    expect(openapi.components.schemas.StudentLookup.required).toContain("enrollmentStatus");
  });

  it("sem sessão 401; Professor 403; aluno de outra unidade do Administrador, 404", async () => {
    expect((await send("GET", `/students/${ADULT}/enrollment`)).status).toBe(401);
    await login("varios@exemplo.com", "00000000-0000-0000-0000-000000000013");
    expect((await send("GET", `/students/${ADULT}/enrollment`)).status).toBe(403);
    expect((await send("POST", `/students/${ADULT}/modalities`, { modalities: [bjj()] })).status).toBe(403);
    await login("varios@exemplo.com", "00000000-0000-0000-0000-000000000011");
    expect((await send("GET", `/students/${ADULT}/enrollment`)).status).toBe(200);
    expect((await send("GET", "/students/00000000-0000-0000-0000-00000000ffff/enrollment")).status).toBe(404);
  });

  it("sem matrícula, 200 com NONE; matricula, acrescenta herdando o vencimento e devolve o formato do contrato", async () => {
    await login("ana@exemplo.com");
    expect(await (await send("GET", `/students/${ADULT}/enrollment`)).json()).toEqual({ id: null, status: "NONE", enrolledAt: null, reopenedAt: null, modalities: [] });
    const created = await send("POST", `/students/${ADULT}/modalities`, { modalities: [bjj({ otherClassIds: [] })] });
    expect(created.status).toBe(201);
    const first = await created.json();
    expect(Object.keys(first).sort()).toEqual([...openapi.components.schemas.Enrollment.required].sort());
    expect(Object.keys(first.modalities[0]).sort()).toEqual([...openapi.components.schemas.EnrollmentModality.required].sort());
    expect(first).toMatchObject({ status: "ACTIVE", modalities: [{ dueDay: 5, canEdit: true, nextPlan: null }] });
    expect((await send("POST", `/students/${ADULT}/modalities`, { modalities: [judo({ dueDay: 10 })] })).status).toBe(422);
    const added = await (await send("POST", `/students/${ADULT}/modalities`, { modalities: [judo()] })).json();
    expect(added.id).toBe(first.id);
    expect(added.modalities.map((m: { dueDay: number }) => m.dueDay)).toEqual([5, 5]);
    expect(await (await send("GET", `/students/${ADULT}/enrollment`)).json()).toEqual(added);
  });

  it("recusas: 400 sem campo, 404 de turma inexistente, 409 de modalidade repetida, 422 de plano inativo, turma errada e dia fora da unidade", async () => {
    await login("ana@exemplo.com");
    const post = (modalities: unknown) => send("POST", `/students/${ADULT}/modalities`, { modalities });
    expect((await post([])).status).toBe(400);
    expect((await post([bjj({ dueDay: null })])).status).toBe(400);
    expect((await post([bjj({ primaryClassId: "00000000-0000-0000-0000-00000000ffff" })])).status).toBe(404);
    expect((await post([bjj({ planId: OLD_PLAN })])).status).toBe(422);
    expect((await post([bjj({ primaryClassId: JUDO_CLASS })])).status).toBe(422);
    expect((await post([bjj({ dueDay: 9 })])).status).toBe(422);
    expect((await post([bjj({ otherClassIds: [BJJ_CLASS] })])).status).toBe(422);
    expect((await post([bjj(), bjj()])).status).toBe(409);
    expect((await send("GET", `/students/${ADULT}/enrollment`).then((r) => r.json())).status).toBe("NONE");
    expect((await post([bjj()])).status).toBe(201);
    expect((await post([bjj()])).status).toBe(409);
  });

  it("a busca por e-mail devolve a situação e exige um só dos dois critérios", async () => {
    await login("ana@exemplo.com");
    expect((await send("GET", "/students/lookup")).status).toBe(400);
    expect((await send("GET", "/students/lookup?cpf=11144477735&email=a@b.test")).status).toBe(400);
    expect((await send("GET", "/students/lookup?email=nao-existe@alfa.test")).status).toBe(404);
  });

  describe("troca de turma, de plano e transferência (RN-MAT-15, RN-MAT-20, RN-ORG-12)", () => {
    const NORTE = "00000000-0000-0000-0000-000000000022";
    const row = async (student = ADULT) => (await (await send("GET", `/students/${student}/enrollment`)).json()).modalities[0].id as string;
    const matricular = async () => {
      await login("ana@exemplo.com");
      await send("POST", `/students/${ADULT}/modalities`, { modalities: [bjj()] });
      return row();
    };

    it("o contrato declara as três rotas e os esquemas", () => {
      const base = "/students/{id}/modalities/{modalityRowId}";
      expect(Object.keys(openapi.paths[`${base}/class`].put.responses)).toEqual(expect.arrayContaining(["200", "400", "401", "403", "404", "422"]));
      expect(Object.keys(openapi.paths[`${base}/plan`].put.responses)).toEqual(expect.arrayContaining(["200", "422"]));
      expect(Object.keys(openapi.paths[`${base}/transfer`].post.responses)).toEqual(expect.arrayContaining(["200", "403", "404", "422"]));
      expect(openapi.components.schemas.EnrollmentTransfer.required).toEqual(["unitId", "primaryClassId", "planId"]);
    });

    it("sem sessão 401, Professor 403, modalidade inexistente 404", async () => {
      expect((await send("PUT", `/students/${ADULT}/modalities/x/class`, { primaryClassId: BJJ_CLASS })).status).toBe(401);
      await login("varios@exemplo.com", "00000000-0000-0000-0000-000000000013");
      expect((await send("PUT", `/students/${ADULT}/modalities/x/plan`, { planId: BJJ_PLAN })).status).toBe(403);
      await login("ana@exemplo.com");
      expect((await send("PUT", `/students/${ADULT}/modalities/00000000-0000-0000-0000-00000000ffff/class`, { primaryClassId: BJJ_CLASS })).status).toBe(404);
    });

    it("troca a turma na hora, substitui os outros horários e recusa turma errada", async () => {
      const id = await matricular();
      const url = `/students/${ADULT}/modalities/${id}/class`;
      expect((await send("PUT", url, { primaryClassId: JUDO_CLASS })).status).toBe(422);
      expect((await send("PUT", url, { primaryClassId: BJJ_CLASS, otherClassIds: [BJJ_CLASS] })).status).toBe(422);
      expect((await send("PUT", url, { primaryClassId: "00000000-0000-0000-0000-00000000ffff" })).status).toBe(404);
      expect((await send("PUT", url, {})).status).toBe(400);
      const done = await send("PUT", url, { primaryClassId: BJJ_CLASS, otherClassIds: [] });
      expect(done.status).toBe(200);
      expect((await done.json()).modalities[0].primaryClass.id).toBe(BJJ_CLASS);
    });

    it("agenda o plano novo, mantém o atual, limpa ao pedir o atual e recusa o atual sem agendamento", async () => {
      const id = await matricular();
      const url = `/students/${ADULT}/modalities/${id}/plan`;
      expect((await send("PUT", url, { planId: BJJ_PLAN })).status).toBe(422);
      expect((await send("PUT", url, { planId: OLD_PLAN })).status).toBe(422);
      expect((await send("PUT", url, { planId: JUDO_PLAN })).status).toBe(422);
      const created = await (await send("POST", "/plans", { unitId: CENTRO, modalityId: "00000000-0000-0000-0000-000000000103", name: "BJJ Novo", valueCents: 25000 })).json();
      const scheduled = (await (await send("PUT", url, { planId: created.id })).json()).modalities[0];
      expect(scheduled.plan.id).toBe(BJJ_PLAN);
      expect(scheduled.nextPlan.id).toBe(created.id);
      const cleared = (await (await send("PUT", url, { planId: BJJ_PLAN })).json()).modalities[0];
      expect(cleared.nextPlan).toBeNull();
    });

    it("transferência: mesma unidade e unidade desativada dão 422, unidade inexistente 404, Administrador fora do alcance 403", async () => {
      const id = await matricular();
      const url = `/students/${ADULT}/modalities/${id}/transfer`;
      const to = { primaryClassId: BJJ_CLASS, planId: BJJ_PLAN, dueDay: 5 };
      expect((await send("POST", url, { ...to, unitId: CENTRO })).status).toBe(422);
      expect((await send("POST", url, { ...to, unitId: NORTE })).status).toBe(422);
      expect((await send("POST", url, { ...to, unitId: "00000000-0000-0000-0000-00000000ffff" })).status).toBe(404);
      expect((await send("POST", url, { unitId: NORTE })).status).toBe(400);
      await login("varios@exemplo.com", "00000000-0000-0000-0000-000000000011");
      expect((await send("POST", url, { ...to, unitId: NORTE })).status).toBe(403);
    });

    it("transfere a mesma linha para a unidade nova, com turma, plano e vencimento dela", async () => {
      const id = await matricular();
      const unit = (await (await send("POST", "/units", { name: "Unidade Sul" })).json()).unit.id as string;
      const teacher = await (await send("POST", "/teachers", { name: "Professora Sul", unitIds: [unit], modalities: [{ modalityId: "00000000-0000-0000-0000-000000000103" }] })).json();
      const klass = await (await send("POST", "/classes", { unitId: unit, name: "Jiu-Jitsu Sul", modalityId: "00000000-0000-0000-0000-000000000103", headTeacherId: teacher.id, schedule: [{ weekday: 1, startsAt: "19:00", endsAt: "20:00" }] })).json();
      const plan = await (await send("POST", "/plans", { unitId: unit, modalityId: "00000000-0000-0000-0000-000000000103", name: "BJJ Sul", valueCents: 20000 })).json();
      const url = `/students/${ADULT}/modalities/${id}/transfer`;
      expect((await send("POST", url, { unitId: unit, primaryClassId: klass.id, planId: BJJ_PLAN, dueDay: 5 })).status).toBe(422);
      expect((await send("POST", url, { unitId: unit, primaryClassId: klass.id, planId: plan.id, dueDay: 9 })).status).toBe(422);
      const moved = await send("POST", url, { unitId: unit, primaryClassId: klass.id, planId: plan.id, dueDay: 10 });
      expect(moved.status).toBe(200);
      const m = (await moved.json()).modalities;
      expect(m).toHaveLength(1);
      expect(m[0]).toMatchObject({ id, unit: { id: unit }, primaryClass: { id: klass.id }, plan: { id: plan.id }, nextPlan: null, dueDay: 10 });
    });
  });
});
