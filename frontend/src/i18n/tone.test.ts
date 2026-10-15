import ptBR from "./pt-BR.json";

type Tree = { [k: string]: string | Tree };

function leaves(node: Tree, path = ""): [string, string][] {
  return Object.entries(node).flatMap(([k, v]) =>
    typeof v === "string" ? [[`${path}${k}`, v] as [string, string]] : leaves(v, `${path}${k}.`),
  );
}

// RNF-24. Listas curtas: pegam só o mais objetivo; o tom em si é revisão humana.
const proibidos = ["prezado", "prezada", "vc ", "tá ", "blz", "mano", "galera", "bora"];
const ameacas = ["bloquearemos", "negativar", "negativado", "protestar", "protesto", "cobrança judicial", "processaremos", "spc", "serasa", "sob pena"];

describe("tom dos textos (CA10)", () => {
  const todos = leaves(ptBR as Tree);

  it("nenhum texto tem forma de tratamento formal, gíria ou ameaça", () => {
    for (const [chave, texto] of todos) {
      const t = `${texto.toLowerCase()} `;
      for (const p of [...proibidos, ...ameacas]) expect(t, `${chave} contém "${p}"`).not.toContain(p);
    }
  });

  it("toda mensagem de erro traz o que aconteceu, o que foi alterado e o que fazer", () => {
    const erros = (ptBR as Tree).errors as Tree;
    expect(Object.keys(erros).length).toBeGreaterThan(0);
    for (const [nome, msg] of Object.entries(erros)) {
      const m = msg as Tree;
      for (const parte of ["aconteceu", "alterado", "fazer"]) {
        expect(typeof m[parte], `errors.${nome}.${parte}`).toBe("string");
        expect((m[parte] as string).length).toBeGreaterThan(0);
      }
    }
  });
});
