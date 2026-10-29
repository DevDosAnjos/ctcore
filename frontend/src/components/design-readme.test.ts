import { readFileSync, readdirSync, statSync } from "node:fs";
import { join } from "node:path";

const root = join(__dirname, "..", "..", "..");
const src = join(__dirname, "..");
function files(dir: string): string[] {
  return readdirSync(dir).flatMap((f) => (statSync(join(dir, f)).isDirectory() ? files(join(dir, f)) : [join(dir, f)]));
}

describe("CA25: tabela Componentes de docs/design/README.md", () => {
  it("cada nome citado existe exportado em src (components e layouts do portal)", () => {
    const readme = readFileSync(join(root, "docs", "design", "README.md"), "utf8");
    const section = readme.slice(readme.indexOf("| No canvas |"));
    const table = section.split("\n").filter((l) => l.startsWith("|")).slice(2);
    const names = new Set(table.flatMap((l) => (l.split("|")[2].match(/`([A-Z]\w+)`/g) ?? []).map((n) => n.replaceAll("`", ""))));
    const code = files(src).filter((f) => /\.tsx?$/.test(f) && !f.includes(".test.")).map((f) => readFileSync(f, "utf8")).join("\n");
    const missing = [...names].filter((n) => !new RegExp(String.raw`export (function|const|class) ${n}\b|export \{[^}]*\b${n}\b`).test(code));
    expect(names.size).toBeGreaterThan(15);
    expect(missing).toEqual([]);
  });
});
