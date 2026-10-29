import { readFileSync, readdirSync } from "node:fs";
import { join } from "node:path";

const FIXAS = /#[0-9a-fA-F]{3,8}\b|\b(bg|text|border|ring|fill|stroke)-(white|black)\b|\b(bg|text|border|ring)-(red|gray|grey|green|blue|yellow|orange|slate|zinc)-\d|text-destructive\b/;
const pastas = ["modalities", "age-ranges", "teachers", "classes", "agenda", "noClassDays"];
const arquivos = pastas.flatMap((p) =>
  readdirSync(join(__dirname, p))
    .filter((f) => /\.tsx$/.test(f) && !f.includes(".test."))
    .map((f) => join(__dirname, p, f)),
);
const nome = (f: string) => f.slice(__dirname.length + 1).replaceAll("\\", "/");

describe("telas de cadastros e agenda (#420, CA9)", () => {
  it("há arquivos para varrer", () => expect(arquivos.length).toBeGreaterThan(25));

  it.each(arquivos.map((f) => [nome(f), f]))("%s não usa cor fixa nem campo solto", (_n, f) => {
    const src = readFileSync(f, "utf8");
    expect(src.match(FIXAS)).toBeNull();
    expect(src).not.toMatch(/bg-background|min-h-11 w-full/);
    expect(src).not.toMatch(/<select|<textarea/);
  });
});
