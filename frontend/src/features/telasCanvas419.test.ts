import { readFileSync, readdirSync } from "node:fs";
import { join } from "node:path";

const FIXAS = /#[0-9a-fA-F]{3,8}\b|\b(bg|text|border|ring|fill|stroke)-(white|black)\b|\b(bg|text|border|ring)-(red|gray|grey|green|blue|yellow|orange|slate|zinc)-\d|text-destructive\b/;
const pastas = ["auth", "account", "organization", "units", "users"];
const arquivos = [
  ...pastas.flatMap((p) =>
    readdirSync(join(__dirname, p))
      .filter((f) => /\.tsx$/.test(f) && !f.includes(".test."))
      .map((f) => join(__dirname, p, f)),
  ),
  join(__dirname, "..", "components", "layout", "AuthShell.tsx"),
];
const nome = (f: string) => f.slice(__dirname.length + 1).replaceAll("\\", "/");

describe("telas de acesso, conta, organização, unidades e usuários (#419, CA10)", () => {
  it("há arquivos para varrer", () => expect(arquivos.length).toBeGreaterThan(40));

  it.each(arquivos.map((f) => [nome(f), f]))("%s não usa cor fixa nem campo solto", (_n, f) => {
    // Valor neutro que o seletor nativo de cor exige quando o campo está vazio; não é estilo.
    const src = readFileSync(f, "utf8").replaceAll("\"#000000\"", "\"\"");
    expect(src.match(FIXAS)).toBeNull();
    expect(src).not.toMatch(/<input[^>]*className="[^"]*min-h-11 w-full/);
  });

  it("o h1 das telas de acesso vem só do AuthShell", () => {
    const paginas = ["LoginPage", "ForgotPasswordPage", "ResetPasswordPage", "InvitePage", "AreaUnavailablePage", "TwoFactorRequiredPage"];
    for (const p of paginas) {
      const src = readFileSync(join(__dirname, "auth", `${p}.tsx`), "utf8");
      expect(src).not.toMatch(/<h1/);
      expect(src).toMatch(/<AuthShell/);
    }
  });
});
