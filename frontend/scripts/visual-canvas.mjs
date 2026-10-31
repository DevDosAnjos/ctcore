// Lado a lado + comparação: referência do sistema (visual/referencias) x print do canvas -> docs/design/prints/lado-a-lado/index.html e resultado.json
// Uso (de dentro de frontend/): npm run visual:canvas [-- --exige]. Sem --exige é relatório (sempre sai 0). Saída ignorada pelo Git. Não sobe o sistema (D5/#416, D1/#435).
import { copyFile, mkdir, readFile, writeFile } from "node:fs/promises";
import { existsSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { spawnSync } from "node:child_process";
import { carregarCasos, diferenca, limiteDoCaso } from "../visual/comparar.mjs";

const here = dirname(fileURLToPath(import.meta.url));
const refs = resolve(here, "../visual/referencias");
const prints = resolve(here, "../../docs/design/prints");
const out = join(prints, "lado-a-lado");
const exige = process.argv.includes("--exige");
const esc = (s) => String(s).replace(/[&<>"]/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" })[c]);

const casos = await carregarCasos();
const indice = JSON.parse(await readFile(resolve(here, "../../docs/design/canvas/indice.json"), "utf8"));
const titulo = (n) => indice.find((t) => t.arquivo === `${n}.dc.html`)?.titulo ?? n;

const ligados = casos.filter((c) => c.canvas);
const faltam = [...new Set(ligados.flatMap((c) => [c.canvas, c.canvasEscuro, c.canvasCelular]).filter(Boolean))].filter(
  (n) => !existsSync(join(prints, `${n}.png`)),
);
if (faltam.length) spawnSync(process.execPath, [join(here, "canvas-shot.mjs"), ...faltam], { stdio: "inherit" });

await mkdir(out, { recursive: true });
const img = async (from, to, alt) => {
  if (!existsSync(from)) return `<p class="sem">sem print</p>`;
  await copyFile(from, join(out, to));
  return `<img src="${esc(to)}" alt="${esc(alt)}" loading="lazy">`;
};

const resultado = [];
let rows = "";
for (const c of ligados) {
  const limite = limiteDoCaso(c);
  const variantes = [
    [1440, "claro", c.canvas],
    ...(c.canvasEscuro ? [[1440, "escuro", c.canvasEscuro]] : []),
    ...(c.canvasCelular ? [[390, "claro", c.canvasCelular]] : []),
  ];
  for (const [largura, tema, tela] of variantes) {
    const refSis = join(refs, `${c.nome}-${largura}-${tema}.png`);
    const refCan = join(prints, `${tela}.png`);
    let dif = null;
    let erro = "";
    if (existsSync(refSis) && existsSync(refCan)) {
      try {
        dif = diferenca(await readFile(refSis), await readFile(refCan), { nomes: [`${c.nome}-${largura}-${tema}`, tela] });
      } catch (e) {
        erro = e.message;
      }
    }
    const aprovada = dif !== null && dif <= limite;
    // sem imagem de um dos lados ou largura diferente: não dá para comparar, não reprova nem aprova
    if (dif !== null) resultado.push({ tela: c.nome, largura, tema, diferenca: +dif.toFixed(4), limite, aprovada });
    const sis = await img(refSis, `${c.nome}-${largura}-${tema}-sistema.png`, `Sistema: ${c.nome} (${largura}, ${tema})`);
    const can = await img(refCan, `${c.nome}-${largura}-${tema}-canvas.png`, `Canvas: ${titulo(tela)}`);
    const sel = dif === null ? `<b>${esc(erro || "sem comparação")}</b>` : `<b class="${aprovada ? "ok" : "ruim"}">${(dif * 100).toFixed(1)}% (limite ${(limite * 100).toFixed(1)}%) ${aprovada ? "aprovada" : "REPROVADA"}</b>`;
    rows += `<section><h2>${esc(titulo(tela))} <small>${esc(c.rota)} · ${largura} · ${tema}</small> ${sel}</h2><div class="par"><figure><figcaption>Sistema</figcaption>${sis}</figure><figure><figcaption>Canvas</figcaption>${can}</figure></div></section>\n`;
  }
}
const reprovadas = resultado.filter((r) => !r.aprovada);
await writeFile(join(out, "resultado.json"), JSON.stringify(resultado, null, 2));
await writeFile(
  join(out, "index.html"),
  `<!doctype html><html lang="pt-BR"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Sistema x canvas</title>
<style>body{font:14px system-ui;margin:16px;background:#f4f4f4;color:#111}h2{font-size:16px}small{font-weight:400;color:#666}.ok{color:#176b2c}.ruim{color:#b00020}.par{display:grid;grid-template-columns:1fr 1fr;gap:12px}figure{margin:0;min-width:0;overflow-x:auto}img{max-width:100%;height:auto;border:1px solid #ccc;background:#fff}.sem{padding:24px;border:1px dashed #999;color:#666}@media(max-width:800px){.par{grid-template-columns:1fr}}</style>
<h1>Sistema x canvas</h1><p>${resultado.length} comparadas, ${reprovadas.length} reprovadas.</p>${rows}</html>`,
);
console.log(join(out, "index.html"));
console.log(`${resultado.length} comparadas, ${reprovadas.length} reprovadas${reprovadas.length ? ": " + reprovadas.map((r) => `${r.tela} ${r.largura} ${r.tema}`).join(", ") : ""}`);
if (exige && reprovadas.length) process.exit(1);
