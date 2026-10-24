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
const MINOR = "00000000-0000-0000-0000-000000000702";
const CENTRO = "00000000-0000-0000-0000-000000000021";
const ADMIN_LINK = "00000000-0000-0000-0000-000000000011";
const TEACHER_LINK = "00000000-0000-0000-0000-000000000013";
const MISSING = "00000000-0000-0000-0000-0000000000de";
const bjj = { unitId: CENTRO, modalityId: "00000000-0000-0000-0000-000000000103", primaryClassId: "00000000-0000-0000-0000-000000000601", planId: "00000000-0000-0000-0000-000000000602", dueDay: 5 };

// Matricula o aluno menor com a data de hoje no dia 1 de janeiro e depois avança o relógio 30 dias: a mensalidade (tolerância de 5 dias) fica em atraso.
async function overdue() {
  vi.useFakeTimers({ toFake: ["Date"] });
  vi.setSystemTime(new Date("2099-01-01T12:00:00Z"));
  const done = await send("POST", `/students/${MINOR}/enrollment/conclude`, { modalities: [bjj], units: [{ unitId: CENTRO }] });
  expect(done.status).toBe(201);
  const id = (await done.json()).charges[0].id as string;
  vi.setSystemTime(new Date("2099-01-31T12:00:00Z"));
  return id;
}
const remind = (chargeIds: unknown) => send("POST", "/charges/reminders", { chargeIds });

afterEach(() => vi.useRealTimers());

describe("em atraso e lembretes x OpenAPI (RF-COB-06, #79, CA30)", () => {
  it("o contrato declara as duas rotas, o histórico tipado e os esquemas", () => {
    expect(Object.keys(openapi.paths["/charges/overdue"].get.responses)).toEqual(expect.arrayContaining(["200", "400", "401", "403", "404"]));
    expect(Object.keys(openapi.paths["/charges/reminders"].post.responses)).toEqual(expect.arrayContaining(["200", "400", "401", "403", "404"]));
    expect(openapi.components.schemas.ChargeDetail.properties.reminders.items.$ref).toBe("#/components/schemas/ChargeReminder");
  });

  it("sem sessão 401; Professor 403 nas duas rotas; sort inválido 400; chargeIds inválidos 400; ids ou unidade desconhecidos 404", async () => {
    expect((await send("GET", "/charges/overdue")).status).toBe(401);
    expect((await remind([MISSING])).status).toBe(401);
    await login("varios@exemplo.com", TEACHER_LINK);
    expect((await send("GET", "/charges/overdue")).status).toBe(403);
    expect((await remind([MISSING])).status).toBe(403);
    await login("ana@exemplo.com");
    expect((await send("GET", "/charges/overdue?sort=x,asc")).status).toBe(400);
    expect((await send("GET", `/charges/overdue?unitId=${MISSING}`)).status).toBe(404);
    for (const bad of [[], "x", ["nao-uuid"], [MISSING, MISSING], Array.from({ length: 101 }, (_, i) => `00000000-0000-0000-0000-${String(i).padStart(12, "0")}`)]) {
      const res = await remind(bad);
      expect(res.status).toBe(400);
      expect((await res.json()).fields[0].field).toBe("chargeIds");
    }
    expect((await remind([MISSING])).status).toBe(404);
  });

  it("lista a cobrança em atraso no formato do contrato, com o responsável do menor como pagador, e a tira da lista ao pagar", async () => {
    await login("ana@exemplo.com");
    const id = await overdue();
    const list = await (await send("GET", "/charges/overdue")).json();
    expect(Object.keys(list).sort()).toEqual(["items", "page", "size", "total"]);
    expect(list.total).toBe(1);
    expect(Object.keys(list.items[0]).sort()).toEqual([...openapi.components.schemas.OverdueCharge.required].sort());
    expect(list.items[0]).toMatchObject({ id, daysLate: 30, daysToBlock: null, lastReminder: null, payer: { name: "Responsável Um Fictício", phone: "21900000011" } });
    expect((await (await send("GET", "/charges/overdue?q=nao-existe")).json()).total).toBe(0);
    await send("POST", `/charges/${id}/payments`, { method: "CASH" });
    expect((await (await send("GET", "/charges/overdue")).json()).total).toBe(0);
  });

  it("envia o lembrete, grava o histórico no painel, repetir é RECENT e a paga é NOT_OPEN", async () => {
    await login("ana@exemplo.com");
    const id = await overdue();
    expect(await (await remind([id])).json()).toEqual({ results: [{ chargeId: id, result: "SENT" }] });
    expect((await (await remind([id])).json()).results[0].result).toBe("RECENT");
    const panel = await (await send("GET", `/charges/${id}`)).json();
    expect(panel.actions.sendReminder).toBe(true);
    expect(panel.reminders).toHaveLength(1);
    expect(Object.keys(panel.reminders[0]).sort()).toEqual([...openapi.components.schemas.ChargeReminder.required].sort());
    expect(panel.reminders[0]).toMatchObject({ kind: "MANUAL", channel: "EMAIL", status: "SENT", reason: null });
    expect((await (await send("GET", "/charges/overdue")).json()).items[0].lastReminder).toMatchObject({ channel: "EMAIL" });
    await send("POST", `/charges/${id}/payments`, { method: "CASH" });
    expect((await (await remind([id])).json()).results[0].result).toBe("NOT_OPEN");
    expect((await (await send("GET", `/charges/${id}`)).json()).actions.sendReminder).toBe(false);
  });

  it("o Administrador da unidade vê a cobrança e envia o lembrete; filtrar por outra unidade dá lista vazia", async () => {
    await login("ana@exemplo.com");
    const id = await overdue();
    await login("varios@exemplo.com", ADMIN_LINK);
    expect((await (await send("GET", "/charges/overdue")).json()).total).toBe(1);
    expect((await (await send("GET", "/charges/overdue?unitId=00000000-0000-0000-0000-000000000022")).json()).total).toBe(0);
    expect((await (await remind([id])).json()).results[0].result).toBe("SENT");
  });
});
