import { readFileSync, readdirSync } from "node:fs";
import { join } from "node:path";

// CA22: nenhum componente usa cor fora dos tokens. Partes B e C acrescentam os seus arquivos
// à varredura de `ui/` e aos arquivos listados aqui.
const ALTERADOS = ["Card.tsx", "Modal.tsx", "ConfirmDialog.tsx", "SidePanel.tsx", "Stepper.tsx", "fields/fields.tsx", "query/DataTable.tsx", "query/Pagination.tsx", "states/states.tsx"];
const FIXAS = /#[0-9a-fA-F]{3,8}\b|\b(bg|text|border|ring|fill|stroke)-(white|black)\b|\b(bg|text|border|ring)-(red|gray|grey|green|blue|yellow|orange|slate|zinc)-\d/;

function fontes(dir: string): string[] {
  return readdirSync(dir, { withFileTypes: true }).flatMap((e) =>
    e.isDirectory() ? fontes(join(dir, e.name)) : /\.tsx?$/.test(e.name) && !/\.test\./.test(e.name) ? [join(dir, e.name)] : [],
  );
}

describe("tokens (CA22)", () => {
  const files = [...fontes(join(__dirname, "ui")), ...ALTERADOS.map((f) => join(__dirname, f))];
  it("há arquivos para varrer", () => expect(files.length).toBeGreaterThan(8));
  it.each(files)("%s não usa cor fixa", (f) => {
    expect(readFileSync(f, "utf8").match(FIXAS)).toBeNull();
  });
});
