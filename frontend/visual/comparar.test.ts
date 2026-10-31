import { describe, expect, it } from "vitest";
// @ts-expect-error pngjs sem tipos (só no teste; sem dependência nova de tipos)
import { PNG } from "pngjs";
// @ts-expect-error módulo .mjs sem tipos, compartilhado com scripts/visual-canvas.mjs
import { carregarCasos, diferenca, LIMITE_PADRAO, limiteDoCaso } from "./comparar.mjs";

type Rect = [number, number, number, number, [number, number, number]];
const png = (w: number, h: number, fundo: [number, number, number], rects: Rect[] = []) => {
  const p = new PNG({ width: w, height: h });
  for (let y = 0; y < h; y++)
    for (let x = 0; x < w; x++) {
      const r = rects.find(([x0, y0, w0, h0]) => x >= x0 && x < x0 + w0 && y >= y0 && y < y0 + h0);
      const [R, G, B] = r ? r[4] : fundo;
      p.data.set([R, G, B, 255], (y * w + x) * 4);
    }
  return PNG.sync.write(p);
};
const branco: [number, number, number] = [255, 255, 255];

describe("comparação com o canvas", () => {
  it("carrega os casos pelo mesmo caminho do script", async () => {
    const casos = await carregarCasos();
    expect(casos.length).toBeGreaterThan(50);
  });
  it("ignora texto e pontos pequenos", () => {
    const a = png(320, 320, branco);
    const b = png(320, 320, branco, [[10, 10, 6, 6, [0, 0, 0]], [100, 50, 40, 4, [0, 0, 0]]]);
    expect(diferenca(a, b)).toBeLessThan(LIMITE_PADRAO);
  });
  it("pega fundo e blocos diferentes", () => {
    const a = png(320, 320, branco);
    const b = png(320, 320, [20, 20, 20]);
    expect(diferenca(a, b)).toBeGreaterThan(LIMITE_PADRAO);
    const c = png(320, 320, branco, [[0, 0, 320, 160, [30, 60, 200]]]);
    expect(diferenca(a, c)).toBeGreaterThan(LIMITE_PADRAO);
  });
  it("compara só a altura comum e erra em larguras diferentes", () => {
    expect(diferenca(png(320, 1000, branco), png(320, 1500, branco))).toBe(0);
    expect(() => diferenca(png(320, 100, branco), png(390, 100, branco), { nomes: ["sistema", "canvas"] })).toThrow(/sistema.*320.*canvas.*390/);
  });
  it("limiteCanvas do caso vale no lugar do padrão", () => {
    expect(limiteDoCaso({})).toBe(LIMITE_PADRAO);
    expect(limiteDoCaso({ limiteCanvas: { valor: 0.6, motivo: "x" } })).toBe(0.6);
  });
});
