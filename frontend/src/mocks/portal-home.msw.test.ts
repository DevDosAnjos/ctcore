import { readFileSync } from "node:fs";
import { join } from "node:path";
import { parse } from "yaml";

const openapi = parse(readFileSync(join(process.cwd(), "../docs/openapi.yaml"), "utf8"));
const json = { "Content-Type": "application/json" };
const send = (method: string, path: string, body?: unknown) => fetch(`/api/v1${path}`, { method, headers: json, body: body === undefined ? undefined : JSON.stringify(body) });
const login = async (email: string, linkId?: string) => {
  await send("POST", "/auth/login", { email, password: "segredo123" });
  if (linkId) await send("PUT", "/auth/session/link", { linkId });
};
const ADULT = "00000000-0000-0000-0000-000000000701";
const MINOR = "00000000-0000-0000-0000-000000000702";
const CHILD_TWO = "00000000-0000-0000-0000-000000000703";
const CENTRO = "00000000-0000-0000-0000-000000000021";
const BJJ = { unitId: CENTRO, modalityId: "00000000-0000-0000-0000-000000000103", primaryClassId: "00000000-0000-0000-0000-000000000601", planId: "00000000-0000-0000-0000-000000000602", dueDay: 5 };
const STUDENT_LINK = "00000000-0000-0000-0000-000000000012";
const keys = (o: object) => Object.keys(o).sort();
const required = (name: string): string[] => [...openapi.components.schemas[name].required].sort();

describe("Início do portal x OpenAPI (RF-POR-02, #89)", () => {
  it("o contrato declara a rota, o parâmetro e os campos da resposta, sem presença nem progresso", () => {
    const op = openapi.paths["/portal/home"].get;
    expect(Object.keys(op.responses)).toEqual(expect.arrayContaining(["200", "401", "403", "404"]));
    expect(op.parameters.map((p: { name: string }) => p.name)).toEqual(["studentId"]);
    expect(required("PortalHome")).toEqual(["announcements", "belts", "charges", "familyOpenCents", "lessons", "reduced", "selectedStudentId", "students"]);
    expect(JSON.stringify(openapi.components.schemas.PortalHome)).not.toMatch(/attendance|frequency|progress/i);
  });

  it("sem sessão 401; gestão 403", async () => {
    expect((await send("GET", "/portal/home")).status).toBe(401);
    await login("ana@exemplo.com");
    expect((await send("GET", "/portal/home")).status).toBe(403);
  });

  it("termo pendente 403 TERM_PENDING", async () => {
    await login("termo@exemplo.com");
    const res = await send("GET", "/portal/home");
    expect(res.status).toBe(403);
    expect((await res.json()).code).toBe("TERM_PENDING");
  });

  it("aluno: a resposta bate com o contrato e traz a mensalidade da matrícula; aluno de fora da conta é 404", async () => {
    await login("ana@exemplo.com");
    await send("POST", `/students/${ADULT}/enrollment/conclude`, { modalities: [BJJ], units: [{ unitId: CENTRO }] });
    await login("varios@exemplo.com", STUDENT_LINK);
    const pending = await (await send("GET", "/portal/term")).json();
    await send("POST", `/portal/students/${ADULT}/term-acceptance`, { version: pending[0].term.version });

    const res = await send("GET", "/portal/home");
    expect(res.status).toBe(200);
    const home = await res.json();
    expect(keys(home)).toEqual(required("PortalHome"));
    expect(keys(home.students[0])).toEqual(required("PortalHomeStudent"));
    expect(keys(home.lessons[0])).toEqual(required("PortalHomeLesson"));
    expect(keys(home.announcements.items[0])).toEqual(required("PortalHomeNotice"));
    expect(keys(home.belts[0])).toEqual(required("PortalHomeBelt"));
    expect(home).toMatchObject({ reduced: false, selectedStudentId: ADULT, students: [{ id: ADULT, self: true }] });
    const monthly = [...home.charges.items.map((c: { dueOn: string }) => c.dueOn), home.charges.nextDueOn].filter(Boolean);
    expect(monthly.length).toBeGreaterThan(0);
    for (const c of home.charges.items) expect(keys(c)).toEqual(required("PortalHomeCharge"));
    expect(home.familyOpenCents).toBe(home.students[0].openCents);

    expect((await send("GET", `/portal/home?studentId=${MINOR}`)).status).toBe(404);
  });

  it("responsável com dois filhos: dois cartões, total da família e conteúdo do cartão escolhido", async () => {
    await login("familia@exemplo.com");
    const first = await (await send("GET", "/portal/home")).json();
    expect(first.students.map((s: { id: string }) => s.id)).toEqual([MINOR, CHILD_TWO]);
    expect(first.selectedStudentId).toBe(MINOR);
    expect(first.familyOpenCents).toBe(5000);
    expect(first.charges.items).toEqual([]);

    const second = await (await send("GET", `/portal/home?studentId=${CHILD_TWO}`)).json();
    expect(second.selectedStudentId).toBe(CHILD_TWO);
    expect(second.charges.items).toMatchObject([{ amountCents: 5000, situation: "DUE_SOON", canPay: true }]);
    expect((await send("GET", `/portal/home?studentId=${ADULT}`)).status).toBe(404);
  });

  it("conta reduzida: só a cobrança, com Pagar", async () => {
    await login("reduzido@exemplo.com");
    const home = await (await send("GET", "/portal/home")).json();
    expect(home).toMatchObject({ reduced: true, lessons: [], belts: [], announcements: { unreadCount: 0, items: [] } });
    expect(home.charges.items).toMatchObject([{ situation: "OVERDUE", canPay: true, amountCents: 12000 }]);
  });
});
