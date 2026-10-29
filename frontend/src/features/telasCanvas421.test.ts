import { readFileSync, readdirSync } from "node:fs";
import { join } from "node:path";

const FIXAS = /#[0-9a-fA-F]{3,8}\b|\b(bg|text|border|ring|fill|stroke)-(white|black)\b|\b(bg|text|border|ring)-(red|gray|grey|green|blue|yellow|orange|slate|zinc)-\d|text-destructive\b/;
const pastas = ["plans", "students", "benefits", "terms"];
const varre = (dir: string): string[] =>
  readdirSync(dir, { withFileTypes: true }).flatMap((e) =>
    e.isDirectory() ? varre(join(dir, e.name)) : /\.tsx$/.test(e.name) && !e.name.includes(".test.") ? [join(dir, e.name)] : [],
  );
const arquivos = pastas.flatMap((p) => varre(join(__dirname, p)));
const nome = (f: string) => f.slice(__dirname.length + 1).replaceAll("\\", "/");

describe("telas de planos, alunos, matrícula, benefícios e termos (#421, CA10)", () => {
  it("há arquivos para varrer", () => expect(arquivos.length).toBeGreaterThan(30));

  it.each(arquivos.map((f) => [nome(f), f]))("%s não usa cor fixa nem campo solto", (_n, f) => {
    const src = readFileSync(f, "utf8");
    expect(src.match(FIXAS)).toBeNull();
    expect(src).not.toMatch(/bg-background|min-h-11 w-full rounded/);
    expect(src).not.toMatch(/<select|<textarea/);
    expect(src).not.toMatch(/<input(?![^>]*type="(checkbox|radio|file)")/);
  });
});
