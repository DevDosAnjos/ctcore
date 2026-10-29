import { readFileSync, readdirSync } from "node:fs";
import { join } from "node:path";

const FIXAS = /#[0-9a-fA-F]{3,8}\b|\b(bg|text|border|ring|fill|stroke)-(white|black)\b|\b(bg|text|border|ring)-(red|gray|grey|green|blue|yellow|orange|slate|zinc)-\d/;
const pages = readdirSync(__dirname).filter((f) => /Page\.tsx$/.test(f));

describe("páginas do portal (CA7, CA10)", () => {
  it("há páginas para varrer", () => expect(pages.length).toBeGreaterThan(5));

  it.each(pages)("%s não tem <h1 próprio (o título vem do PageHeader)", (f) => {
    expect(readFileSync(join(__dirname, f), "utf8")).not.toMatch(/<h1/);
  });

  it.each(["OrgMark.tsx", "PortalTabs.tsx", "PortalFooter.tsx", "PortalLayout.tsx"])("%s não usa cor fixa", (f) => {
    expect(readFileSync(join(__dirname, f), "utf8").match(FIXAS)).toBeNull();
  });
});
