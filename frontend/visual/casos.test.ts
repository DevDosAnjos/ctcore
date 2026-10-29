import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";
import { casos } from "./casos";

const indice: { arquivo: string }[] = JSON.parse(readFileSync("../docs/design/canvas/indice.json", "utf8"));
const telas = new Set(indice.map((t) => t.arquivo.replace(".dc.html", "")));
const app = readFileSync("src/App.tsx", "utf8");
const paths = [...app.matchAll(/<Route path=(?:"|\{)([^"}]+)/g)].map((m) => m[1]);
const casa = (rota: string) =>
  paths.some((p) => new RegExp(`^${p.replace(/\/:\w+\??/g, "(/[^/]+)?")}/?$`).test(rota));

describe("casos do teste visual", () => {
  it("nomes únicos", () => expect(new Set(casos.map((c) => c.nome)).size).toBe(casos.length));
  it.each(casos)("$nome: canvas existe e rota está em App.tsx", (c) => {
    for (const nome of [c.canvas, c.canvasEscuro]) if (nome) expect(telas.has(nome)).toBe(true);
    expect(casa(c.rota)).toBe(true);
  });
});
