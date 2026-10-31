// Gera o print de telas do canvas de referência (docs/design/canvas) em docs/design/prints/.
// Uso (de dentro de frontend/): node scripts/canvas-shot.mjs Alunos AlunoDetalhes
//   sem nomes: gera todas as telas do indice.json. Saída ignorada pelo Git.
import { createServer } from "node:http";
import { readFile, mkdir } from "node:fs/promises";
import { join, extname, resolve, dirname } from "node:path";
import { fileURLToPath } from "node:url";
import { chromium } from "@playwright/test";

const here = dirname(fileURLToPath(import.meta.url));
const root = resolve(here, "../../docs/design/canvas");
const out = resolve(here, "../../docs/design/prints");
const types = { ".html": "text/html; charset=utf-8", ".json": "application/json", ".js": "text/javascript" };

const server = createServer(async (req, res) => {
  const path = decodeURIComponent(new URL(req.url, "http://x").pathname);
  const file = join(root, path === "/" ? "ver.html" : path);
  if (!file.startsWith(root)) return res.writeHead(403).end();
  try {
    const body = await readFile(file);
    res.writeHead(200, { "content-type": types[extname(file)] ?? "application/octet-stream" }).end(body);
  } catch {
    res.writeHead(404).end();
  }
});
await new Promise((ok) => server.listen(0, "127.0.0.1", ok));
const port = server.address().port;

const index = JSON.parse(await readFile(join(root, "indice.json"), "utf8"));
const wanted = process.argv.slice(2).map((n) => (n.endsWith(".dc.html") ? n : `${n}.dc.html`));
const list = wanted.length ? index.filter((t) => wanted.includes(t.arquivo)) : index;
await mkdir(out, { recursive: true });

const browser = await chromium.launch({ channel: "chromium", args: ["--lang=pt-BR"] });
for (const t of list) {
  const page = await browser.newPage({ locale: "pt-BR", viewport: { width: t.largura, height: t.altura } });
  await page.goto(`http://127.0.0.1:${port}/ver.html?f=${encodeURIComponent(t.arquivo)}`);
  await page.waitForSelector("body[data-done]", { timeout: 20000 });
  await page.waitForTimeout(200);
  const png = join(out, t.arquivo.replace(".dc.html", ".png"));
  await page.screenshot({ path: png });
  console.log(png);
  await page.close();
}
await browser.close();
server.close();
