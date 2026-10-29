import { readFileSync, readdirSync } from "node:fs";
import { join } from "node:path";

const FIXAS = /#[0-9a-fA-F]{3,8}\b|\b(bg|text|border|ring|fill|stroke)-(white|black)\b|\b(bg|text|border|ring)-(red|gray|grey|green|blue|yellow|orange|slate|zinc)-\d|(text|border)-destructive\b/;
const dir = join(__dirname, "portal");
const arquivos = readdirSync(dir).filter((n) => /\.tsx$/.test(n) && !n.includes(".test.")).map((n) => join(dir, n));
const nome = (f: string) => f.slice(__dirname.length + 1).replaceAll("\\", "/");

describe("telas do portal do aluno e do responsável (#423, CA8)", () => {
  it("há arquivos para varrer", () => expect(arquivos.length).toBeGreaterThan(30));

  it.each(arquivos.map((f) => [nome(f), f]))("%s não usa cor fixa nem campo solto", (_n, f) => {
    const src = readFileSync(f, "utf8");
    expect(src.match(FIXAS)).toBeNull();
    expect(src).not.toMatch(/border bg-background|min-h-11 w-full rounded/);
    expect(src).not.toMatch(/<select|<textarea/);
    expect(src).not.toMatch(/<input(?![^>]*type="(checkbox|radio|file)")/);
  });
});
