// Lado a lado: referência do sistema (visual/referencias, 1440) x print do canvas -> docs/design/prints/lado-a-lado/index.html
// Uso (de dentro de frontend/): npm run visual:canvas. Saída ignorada pelo Git. Não sobe o sistema (D5).
import { copyFile, mkdir, readFile, writeFile } from "node:fs/promises";
import { existsSync, readFileSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { spawnSync } from "node:child_process";

const here = dirname(fileURLToPath(import.meta.url));
const refs = resolve(here, "../visual/referencias");
const prints = resolve(here, "../../docs/design/prints");
const out = join(prints, "lado-a-lado");
const esc = (s) => String(s).replace(/[&<>"]/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" })[c]);

// casos.ts só tem dados: tira os tipos e avalia como módulo (sem dependência nova).
const src = (await readFile(join(here, "../visual/casos.ts"), "utf8"))
  .replace(/export type Caso = \{[\s\S]*?\};\n/, "")
  .replace(/: Caso\[\]/, "")
  .replace(/ as const/g, "");
const { casos } = await import(`data:text/javascript;base64,${Buffer.from(src).toString("base64")}`);
const indice = JSON.parse(await readFile(resolve(here, "../../docs/design/canvas/indice.json"), "utf8"));
const titulo = (n) => indice.find((t) => t.arquivo === `${n}.dc.html`)?.titulo ?? n;

const ligados = casos.filter((c) => c.canvas);
const faltam = [...new Set(ligados.flatMap((c) => [c.canvas, c.canvasEscuro]).filter(Boolean))].filter(
  (n) => !existsSync(join(prints, `${n}.png`)),
);
if (faltam.length) spawnSync(process.execPath, [join(here, "canvas-shot.mjs"), ...faltam], { stdio: "inherit" });

await mkdir(out, { recursive: true });
const img = async (from, to, alt) => {
  if (!existsSync(from)) return `<p class="sem">sem print</p>`;
  await copyFile(from, join(out, to));
  return `<img src="${esc(to)}" alt="${esc(alt)}" loading="lazy">`;
};

let rows = "";
for (const c of ligados) {
  const temas = [["claro", c.canvas], ...(c.canvasEscuro ? [["escuro", c.canvasEscuro]] : [])];
  for (const [tema, tela] of temas) {
    const sis = await img(join(refs, `${c.nome}-1440-${tema}.png`), `${c.nome}-${tema}-sistema.png`, `Sistema: ${c.nome} (${tema})`);
    const can = await img(join(prints, `${tela}.png`), `${c.nome}-${tema}-canvas.png`, `Canvas: ${titulo(tela)}`);
    rows += `<section><h2>${esc(titulo(tela))} <small>${esc(c.rota)} · ${tema}</small></h2><div class="par"><figure><figcaption>Sistema</figcaption>${sis}</figure><figure><figcaption>Canvas</figcaption>${can}</figure></div></section>\n`;
  }
}
await writeFile(
  join(out, "index.html"),
  `<!doctype html><html lang="pt-BR"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Sistema x canvas</title>
<style>body{font:14px system-ui;margin:16px;background:#f4f4f4;color:#111}h2{font-size:16px}small{font-weight:400;color:#666}.par{display:grid;grid-template-columns:1fr 1fr;gap:12px}figure{margin:0;min-width:0;overflow-x:auto}img{max-width:100%;height:auto;border:1px solid #ccc;background:#fff}.sem{padding:24px;border:1px dashed #999;color:#666}@media(max-width:800px){.par{grid-template-columns:1fr}}</style>
<h1>Sistema x canvas</h1>${rows}</html>`,
);
console.log(join(out, "index.html"));
