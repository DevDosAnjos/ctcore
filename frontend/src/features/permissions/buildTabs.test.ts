import { mockPermissions } from "@/mocks/permissions";
import { buildTabs } from "./buildTabs";

const t = (k: string) => k.split(".").pop()!;
const ids = (role: string, p = mockPermissions(role)) => buildTabs(p, role, t).map((x) => x.id);

describe("buildTabs", () => {
  it("CA9: gestão tem 4 abas, professor 3 (a aba Menu é da moldura)", () => {
    expect(ids("COORDINATOR")).toEqual(["home", "students", "agenda", "collections"]);
    expect(ids("TEACHER")).toEqual(["home", "agenda", "announcements"]);
  });

  it("CA11: sem permissão a aba não aparece; tela ainda inexistente fica desabilitada", () => {
    const p = { ...mockPermissions("COORDINATOR"), STUDENT_MANAGE: "NONE" as const };
    expect(ids("COORDINATOR", p)).not.toContain("students");
    expect(buildTabs(mockPermissions("COORDINATOR"), "COORDINATOR", t).find((x) => x.id === "collections")?.disabled).toBe(true);
  });
});
