import { readFileSync } from "node:fs";
import { join } from "node:path";
import { parse } from "yaml";
import { mockPermissions } from "@/mocks/permissions";
import { buildMenu } from "./buildMenu";
import { menuCatalog } from "./menuCatalog";

const t = (key: string) => key;
const allAvailable = menuCatalog.map((g) => ({ ...g, items: g.items.map((i) => ({ ...i, available: true })) }));
const groupIds = (role: string, catalog = allAvailable) => buildMenu(mockPermissions(role), t, catalog).map((g) => g.id);
const itemIds = (role: string) => buildMenu(mockPermissions(role), t, allAvailable).flatMap((g) => g.items.map((i) => i.id));

describe("buildMenu", () => {
  it("CA11: Professor não vê Financeiro nem Configurações; Administrador não vê Unidades; Coordenador vê tudo", () => {
    expect(groupIds("TEACHER")).toEqual(["home", "operation", "people", "reports"]);
    expect(groupIds("TEACHER")).not.toContain("finance");
    expect(itemIds("ADMINISTRATOR")).not.toContain("units");
    expect(itemIds("ADMINISTRATOR")).toContain("organizationSettings");
    expect(groupIds("COORDINATOR")).toEqual(menuCatalog.map((g) => g.id));
    expect(itemIds("COORDINATOR")).toContain("units");
  });

  it("CA11: nível NONE some, grupo vazio some e a ordem é a do catálogo; portal não tem menu", () => {
    const menu = buildMenu(mockPermissions("TEACHER"), t, allAvailable);
    expect(menu.find((g) => g.id === "operation")!.items.map((i) => i.id)).toEqual(["agenda", "graduation", "trialClasses", "announcements"]);
    expect(buildMenu(mockPermissions("STUDENT"), t, allAvailable)).toEqual([]);
    expect(buildMenu(mockPermissions(undefined), t, allAvailable)).toEqual([]);
  });

  it("CA12 / CA19 / CA16 (#29): com o catálogo real, o Coordenador vê Organização e Unidades, o Administrador só Organização e o Professor só o Início", () => {
    const home = { id: "home", label: "menu.groups.home", items: [{ id: "home", label: "menu.items.home", href: "/" }] };
    expect(buildMenu(mockPermissions("TEACHER"), t)).toEqual([home]);
    const organization = { id: "organizationSettings", label: "menu.items.organizationSettings", href: "/configuracoes/organizacao" };
    const units = { id: "units", label: "menu.items.units", href: "/configuracoes/unidades" };
    const settings = (items: object[]) => ({ id: "settings", label: "menu.groups.settings", items });
    expect(buildMenu(mockPermissions("COORDINATOR"), t)).toEqual([home, settings([organization, units])]);
    expect(buildMenu(mockPermissions("ADMINISTRATOR"), t)).toEqual([home, settings([organization])]);
  });
});

describe("mock de permissões x OpenAPI (CA14)", () => {
  const openapi = parse(readFileSync(join(process.cwd(), "../docs/openapi.yaml"), "utf8"));

  it("tem exatamente os códigos e níveis do contrato e o handler responde 401 sem sessão", async () => {
    expect(Object.keys(openapi.paths["/permissions"].get.responses)).toEqual(expect.arrayContaining(["200", "401"]));
    const codes: string[] = openapi.components.schemas.Permission.enum;
    const levels: string[] = openapi.components.schemas.Access.enum;
    for (const role of ["COORDINATOR", "ADMINISTRATOR", "TEACHER", "STUDENT", undefined]) {
      const map = mockPermissions(role);
      expect(Object.keys(map).sort()).toEqual([...codes].sort());
      for (const level of Object.values(map)) expect(levels).toContain(level);
    }
    expect((await fetch("/api/v1/permissions")).status).toBe(401);
  });
});
