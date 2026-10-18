import { readFileSync } from "node:fs";
import { join } from "node:path";
import { parse } from "yaml";

const openapi = parse(readFileSync(join(process.cwd(), "../docs/openapi.yaml"), "utf8"));
const json = { "Content-Type": "application/json" };
const FIRST = "00000000-0000-0000-0000-000000000021";
const SECOND = "00000000-0000-0000-0000-000000000022";
const login = async (email: string, linkId?: string) => {
  await fetch("/api/v1/auth/login", { method: "POST", headers: json, body: JSON.stringify({ email, password: "segredo123" }) });
  if (linkId) await fetch("/api/v1/auth/session/link", { method: "PUT", headers: json, body: JSON.stringify({ linkId }) });
};
const mine = () => fetch("/api/v1/units/mine");
const read = (id: string) => fetch(`/api/v1/units/${id}`);
// Sem versão explícita lê a atual (RNF-15); null omite o If-Match.
const put = async (id: string, body: unknown, ifMatch?: string | null) => {
  const version = ifMatch === undefined ? String((await (await read(id)).json()).version) : ifMatch;
  return fetch(`/api/v1/units/${id}`, { method: "PUT", headers: version === null ? json : { ...json, "If-Match": version }, body: JSON.stringify(body) });
};
const declared = (path: string, method: string) => Object.keys(openapi.paths[path][method].responses);
const keys = (name: string) => Object.keys(openapi.components.schemas[name].properties).sort();

type Body = { name: string; cnpj?: string; settings: { dueDays: number[]; enrollmentFeeEnabled: boolean; enrollmentFeeCents: number; feesCents: Record<string, number>; pixKey: string | null; reminderDays: number[] } };
const body = (change: (b: Body & Record<string, unknown>) => void = () => {}) => {
  const b = {
    name: "Unidade Centro",
    settings: {
      dueDays: [15, 5],
      enrollmentFeeEnabled: true,
      enrollmentFeeCents: 15000,
      feesCents: { cash: 0, pix: 199, bankSlip: 0, creditCard: 0, debitCard: 0, automaticDebit: 0, manualPix: 0, terminalCard: 0, transfer: 0 },
      pixKey: null,
      reminderDays: [3, -3],
      teacherPermissions: { announcements: true, agendaEdit: false, studentContacts: true, trialClasses: true },
      directoryEnabled: true,
      trialLinkEnabled: true,
      selfRegistrationLinkEnabled: true,
    },
  } as Body & Record<string, unknown>;
  change(b);
  return b;
};

describe("configurações da unidade x OpenAPI (CA22)", () => {
  it("sem sessão as três rotas respondem o 401 do contrato", async () => {
    expect((await mine()).status).toBe(401);
    expect((await read(FIRST)).status).toBe(401);
    expect((await put(FIRST, body())).status).toBe(401);
    for (const [path, method] of [["/units/mine", "get"], ["/units/{id}", "get"], ["/units/{id}", "put"]] as const) expect(declared(path, method)).toContain("401");
  });

  it("Coordenador: lista e leitura trazem as chaves do contrato e o PUT guarda, ordena e não toca no que é só leitura", async () => {
    await login("ana@exemplo.com");
    const choices = await (await mine()).json();
    expect(choices).toHaveLength(2);
    expect(Object.keys(choices[0]).sort()).toEqual(keys("UnitChoice"));
    const detail = await (await read(FIRST)).json();
    expect(Object.keys(detail).sort()).toEqual(keys("UnitDetail"));
    expect(Object.keys(detail.settings).sort()).toEqual(keys("UnitSettingsView"));
    expect(Object.keys(detail.settings.feesCents).sort()).toEqual(keys("FeesCents"));

    const saved = await put(FIRST, body((b) => Object.assign(b, { shortName: "invadido", cnpj: "11.222.333/0001-81", postalCode: "01001-000" })));
    expect(saved.status).toBe(200);
    const next = await saved.json();
    expect(next.shortName).toBe("alfa-centro");
    expect(next.cnpj).toBe("11222333000181");
    expect(next.latitude).toBe(-23.5505);
    expect(next.settings.dueDays).toEqual([5, 15]);
    expect(next.settings.reminderDays).toEqual([-3, 3]);
    expect(next.settings.paymentAccount).toBe("NOT_CONNECTED");
    expect(await (await read(FIRST)).json()).toEqual(next);
    expect((await (await put(FIRST, body((b) => (b.settings.enrollmentFeeEnabled = false)))).json()).settings.enrollmentFeeCents).toBe(0);
  });

  it("400 com o campo, 404 para unidade que não existe; ambos estão no contrato", async () => {
    await login("ana@exemplo.com");
    const cases: [(b: Body) => void, string][] = [
      [(b) => (b.name = " "), "name"],
      [(b) => (b.cnpj = "11.222.333/0001-82"), "cnpj"],
      [(b) => (b.settings.dueDays = []), "settings.dueDays"],
      [(b) => (b.settings.feesCents.pix = -1), "settings.feesCents.pix"],
      [(b) => (b.settings.pixKey = "abc"), "settings.pixKey"],
      [(b) => (b.settings.reminderDays = [1, 1]), "settings.reminderDays"],
    ];
    for (const [change, field] of cases) {
      const res = await put(FIRST, body(change));
      expect(res.status).toBe(400);
      const err = await res.json();
      expect(err.code).toBe("VALIDATION_ERROR");
      expect(err.fields.map((f: { field: string }) => f.field)).toContain(field);
    }
    expect(declared("/units/{id}", "put")).toEqual(expect.arrayContaining(["400", "404"]));
    expect((await read("00000000-0000-0000-0000-0000000000ff")).status).toBe(404);
    expect((await put("00000000-0000-0000-0000-0000000000ff", body())).status).toBe(404);
  });

  it("Administrador vê só a sua unidade; na dos outros e Professor e portal, 403", async () => {
    await login("varios@exemplo.com", "00000000-0000-0000-0000-000000000011");
    const own = await (await mine()).json();
    expect(own.map((u: { id: string }) => u.id)).toEqual([FIRST]);
    expect((await read(FIRST)).status).toBe(200);
    expect((await put(FIRST, body())).status).toBe(200);
    for (const res of [await read(SECOND), await put(SECOND, body())]) {
      expect(res.status).toBe(403);
      expect((await res.json()).code).toBe("FORBIDDEN");
    }
    expect(declared("/units/{id}", "get")).toContain("403");
    for (const link of ["00000000-0000-0000-0000-000000000012", "00000000-0000-0000-0000-000000000013"]) {
      await fetch("/api/v1/auth/session/link", { method: "PUT", headers: json, body: JSON.stringify({ linkId: link }) });
      for (const res of [await mine(), await read(FIRST), await put(FIRST, body())]) expect(res.status).toBe(403);
    }
    expect(declared("/units/mine", "get")).toContain("403");
  });
});

describe("última alteração e versão da unidade x OpenAPI (RF-ORG-08, RNF-15)", () => {
  it("lastChange nulo no início; PUT que muda valor sobe a versão e preenche o ator; PUT idêntico não muda; 409 e 428 do contrato", async () => {
    await login("ana@exemplo.com");
    expect(await (await read(FIRST)).json()).toMatchObject({ version: 0, lastChange: null });
    const saved = await (await put(FIRST, body())).json();
    expect(saved.version).toBe(1);
    expect(saved.lastChange).toEqual({ at: expect.stringMatching(/^\d{4}-\d{2}-\d{2}T/), by: "Ana" });
    expect(await (await put(FIRST, body())).json()).toEqual(saved);

    const stale = await put(FIRST, body((b) => (b.name = "Outro")), "0");
    expect(stale.status).toBe(409);
    expect((await stale.json()).code).toBe("EDIT_CONFLICT");
    for (const header of [null, "x"]) {
      const res = await put(FIRST, body((b) => (b.name = "Outro")), header);
      expect(res.status).toBe(428);
      expect((await res.json()).code).toBe("PRECONDITION_REQUIRED");
    }
    expect((await (await read(FIRST)).json()).name).toBe("Unidade Centro");
    expect(declared("/units/{id}", "put")).toEqual(expect.arrayContaining(["409", "428"]));
  });
});
