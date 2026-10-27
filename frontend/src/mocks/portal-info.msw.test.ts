import { readFileSync } from "node:fs";
import { join } from "node:path";
import { parse } from "yaml";

const openapi = parse(readFileSync(join(process.cwd(), "../docs/openapi.yaml"), "utf8"));
const json = { "Content-Type": "application/json" };
const send = (method: string, path: string, body?: unknown) =>
  fetch(`/api/v1${path}`, {
    method,
    headers: json,
    body: body === undefined ? undefined : JSON.stringify(body),
  });
const login = (email: string) => send("POST", "/auth/login", { email, password: "segredo123" });
const MINOR = "00000000-0000-0000-0000-000000000702";
const CHILD_TWO = "00000000-0000-0000-0000-000000000703";
const keys = (o: object) => Object.keys(o).sort();
const required = (name: string): string[] => [...openapi.components.schemas[name].required].sort();
const contact = {
  phone: "21911112222",
  postalCode: "20000000",
  street: "Rua Nova",
  number: "10",
  complement: null,
  district: "Centro",
  city: "Rio de Janeiro",
  state: "RJ",
  emergencyName: "Contato Fictício",
  emergencyRelationship: "Mãe",
  emergencyPhone: "21933334444",
};

describe("Minhas informações do portal x OpenAPI (RF-POR-07, #94)", () => {
  it("o contrato declara as rotas e os campos, sem CPF completo, presença nem progresso", () => {
    expect(Object.keys(openapi.paths["/portal/info"].get.responses)).toEqual(expect.arrayContaining(["200", "401", "403", "404"]));
    expect(Object.keys(openapi.paths["/portal/students/{id}/contact"].put.responses)).toEqual(expect.arrayContaining(["200", "422"]));
    expect(Object.keys(openapi.paths["/portal/students/{id}/photo"])).toEqual(expect.arrayContaining(["put", "delete"]));
    expect(required("PortalInfo")).toEqual(["emailAnnouncements", "graduation", "personal", "plans", "reduced", "selectedStudentId", "students", "terms", "trainings", "unitContacts"]);
    expect(JSON.stringify(openapi.components.schemas.PortalInfo)).not.toMatch(/attendance|frequency|progress|"cpf"/i);
  });

  it("sem sessão 401; gestão 403; termo pendente 403 TERM_PENDING", async () => {
    expect((await send("GET", "/portal/info")).status).toBe(401);
    await login("ana@exemplo.com");
    expect((await send("GET", "/portal/info")).status).toBe(403);
    await login("termo@exemplo.com");
    const res = await send("GET", "/portal/info");
    expect(res.status).toBe(403);
    expect((await res.json()).code).toBe("TERM_PENDING");
  });

  it("responsável com dois filhos: formato do contrato, CPF mascarado, segundo filho por studentId, fora da conta 404", async () => {
    await login("familia@exemplo.com");
    const first = await (await send("GET", "/portal/info")).json();
    expect(keys(first)).toEqual(required("PortalInfo"));
    expect(keys(first.personal)).toEqual(required("PortalInfoPersonal"));
    expect(first.selectedStudentId).toBe(MINOR);
    expect(first.students).toHaveLength(2);
    expect(first.reduced).toBe(false);
    expect(first.personal.editableFields).not.toEqual(expect.arrayContaining(["name", "cpf", "birthDate", "email"]));
    expect(first.trainings).toHaveLength(2);
    expect(keys(first.trainings[0].mainClass)).toEqual(required("PortalInfoClass"));
    expect(first.plans[0].monthlyCents).toBe(18000);
    expect(first.graduation[0].history[0].id).toBe(first.graduation[0].belt.id);
    expect(JSON.stringify(first)).not.toMatch(/"cpf"|\d{3}\.\d{3}\.\d{3}-\d{2}/);
    const second = await (await send("GET", `/portal/info?studentId=${CHILD_TWO}`)).json();
    expect(second.selectedStudentId).toBe(CHILD_TWO);
    expect((await send("GET", "/portal/info?studentId=00000000-0000-0000-0000-000000000701")).status).toBe(404);
  });

  it("aluno inativo: reduced, sem treinos, planos e graduação, com o contato da unidade", async () => {
    await login("reduzido@exemplo.com");
    const info = await (await send("GET", "/portal/info")).json();
    expect(info).toMatchObject({ reduced: true, trainings: [], plans: [], graduation: [] });
    expect(info.unitContacts).toHaveLength(1);
  });

  it("contato: grava e a leitura mostra; campo fora da lista, celular sem DDD e emergência incompleta dão 422 sem mudar nada", async () => {
    await login("familia@exemplo.com");
    const ok = await send("PUT", `/portal/students/${MINOR}/contact`, contact);
    expect(ok.status).toBe(200);
    expect(keys(await ok.json())).toEqual(required("PortalInfoPersonal"));
    expect((await (await send("GET", "/portal/info")).json()).personal.address.street).toBe("Rua Nova");

    for (const [bad, field] of [
      [{ ...contact, name: "Outro Nome" }, "name"],
      [{ ...contact, cpf: "11111111111" }, "cpf"],
      [{ ...contact, phone: "999990000" }, "phone"],
      [{ ...contact, emergencyRelationship: null, emergencyPhone: null }, "emergencyName"],
    ] as const) {
      const res = await send("PUT", `/portal/students/${MINOR}/contact`, bad);
      expect(res.status).toBe(422);
      expect((await res.json()).fields[0].field).toBe(field);
    }
    expect((await (await send("GET", "/portal/info")).json()).personal.address.street).toBe("Rua Nova");
    expect((await send("PUT", "/portal/students/00000000-0000-0000-0000-000000000701/contact", contact)).status).toBe(404);
  });

  it("foto: PNG troca, outro tipo 422, DELETE remove", async () => {
    await login("familia@exemplo.com");
    // FormData e File do jsdom não passam pelo fetch do Node; o corpo multipart vai montado à mão.
    const upload = (type: string) =>
      fetch(`/api/v1/portal/students/${MINOR}/photo`, {
        method: "PUT",
        headers: { "Content-Type": "multipart/form-data; boundary=x" },
        body: `--x
Content-Disposition: form-data; name="file"; filename="f"
Content-Type: ${type}

xx
--x--
`,
      });
    expect((await upload("text/plain")).status).toBe(422);
    const ok = await upload("image/png");
    expect(ok.status).toBe(200);
    expect((await ok.json()).photoUrl).not.toBeNull();
    const removed = await send("DELETE", `/portal/students/${MINOR}/photo`);
    expect((await removed.json()).photoUrl).toBeNull();
  });
});
