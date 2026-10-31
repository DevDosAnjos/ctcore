// Comparação sistema x canvas (#435). Usado por scripts/visual-canvas.mjs e por comparar.test.ts.
import { readFile } from "node:fs/promises";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { PNG } from "pngjs";

const here = dirname(fileURLToPath(import.meta.url));

// Limite padrão de diferença (fração de blocos) e limiar de cor (0 a 255, maior canal). Calibrados na #435 (ver specs/itens/435).
export const LIMITE_PADRAO = 0.30;
export const LIMIAR_COR = 40;
export const BLOCO = 32;

// casos.ts só tem dados: tira os tipos e avalia como módulo (sem dependência nova).
export async function carregarCasos() {
  const src = (await readFile(join(here, "casos.ts"), "utf8"))
    .replace(/export type Passo = [^;]*;\r?\n/, "")
    .replace(/export type Caso = \{[\s\S]*?\};\r?\n/, "")
    .replace(/: Caso\[\]/, "")
    .replace(/: Record<string, string\[\]>/, "")
    .replace(/ as const/g, "");
  return (await import(`data:text/javascript;base64,${Buffer.from(src).toString("base64")}`)).casos;
}

// Fração (0 a 1) de blocos de cor média diferente, na altura comum. Texto e dados somem na média do bloco.
export function diferenca(bufA, bufB, { bloco = BLOCO, limiar = LIMIAR_COR, nomes = ["A", "B"] } = {}) {
  const a = PNG.sync.read(bufA);
  const b = PNG.sync.read(bufB);
  if (a.width !== b.width) throw new Error(`Larguras diferentes: ${nomes[0]} ${a.width}px, ${nomes[1]} ${b.width}px`);
  const h = Math.min(a.height, b.height);
  const media = (img, x0, y0) => {
    const m = [0, 0, 0];
    let n = 0;
    for (let y = y0; y < Math.min(y0 + bloco, h); y++)
      for (let x = x0; x < Math.min(x0 + bloco, a.width); x++) {
        const i = (y * img.width + x) * 4;
        for (let c = 0; c < 3; c++) m[c] += img.data[i + c];
        n++;
      }
    return m.map((v) => v / n);
  };
  let total = 0;
  let difere = 0;
  for (let y = 0; y < h; y += bloco)
    for (let x = 0; x < a.width; x += bloco) {
      const ma = media(a, x, y);
      const mb = media(b, x, y);
      total++;
      if (Math.max(...ma.map((v, c) => Math.abs(v - mb[c]))) > limiar) difere++;
    }
  return total ? difere / total : 0;
}

export const limiteDoCaso = (c) => c.limiteCanvas?.valor ?? LIMITE_PADRAO;
