import { createRequire } from "node:module";
import { expect, test } from "@playwright/test";
import type { Page } from "@playwright/test";

const axePath = createRequire(import.meta.url).resolve("axe-core/axe.min.js");
const CENTRO = "00000000-0000-0000-0000-000000000021";
const ALUNO_ADULTO = "00000000-0000-0000-0000-000000000701";
const BJJ = {
  unitId: CENTRO,
  modalityId: "00000000-0000-0000-0000-000000000103",
  primaryClassId: "00000000-0000-0000-0000-000000000601",
  planId: "00000000-0000-0000-0000-000000000602",
  dueDay: 5,
};

const violations = async (page: Page) => {
  await page.addScriptTag({ path: axePath });
  return page.evaluate(async () => (await (globalThis as unknown as { axe: { run: () => Promise<{ violations: unknown[] }> } }).axe.run()).violations);
};
const noOverflow = (page: Page) => page.evaluate("document.documentElement.scrollWidth <= window.innerWidth");

async function open(page: Page, path: string) {
  await page.evaluate(`history.pushState({}, "", ${JSON.stringify(path)}); dispatchEvent(new PopStateEvent("popstate"))`);
}

// API de teste: as mesmas rotas do contrato, no navegador (o simulado responde), para montar o cenário. `form` envia um arquivo multipart.
async function call(page: Page, method: string, path: string, body?: unknown, extra: Record<string, string> = {}, form = false) {
  const result = await page.evaluate(
    async ([m, p, b, h, f]) => {
      const data = f ? new FormData() : undefined;
      data?.append("file", new File(["comprovante"], "comprovante.png", { type: "image/png" }));
      const response = await fetch(`/api/v1${p}`, {
        method: m as string,
        headers: f ? (h as Record<string, string>) : { "Content-Type": "application/json", ...(h as Record<string, string>) },
        body: f ? data : b ? JSON.stringify(b) : undefined,
      });
      return { status: response.status, body: await response.json().catch(() => null) };
    },
    [method, path, body, extra, form] as const,
  );
  expect(result.status, `${method} ${path}`).toBeLessThan(300);
  return result.body;
}

async function chooseLink(page: Page, name: RegExp) {
  const modal = page.getByRole("dialog", { name: "Escolher organização e área" });
  const swap = page.getByRole("button", { name: "Trocar organização ou área" });
  await modal.or(swap).first().waitFor(); // o estado que vier primeiro, sem esperar 3 s pelo diálogo que não vem
  if (!(await modal.isVisible())) await swap.click();
  await modal.getByRole("button", { name }).click();
  await expect(modal).toBeHidden();
}

// RF-COB-05 (#87): o aluno envia o comprovante (feito pela API, a janela do portal entra com a #92); a gestão recusa um e confirma outro.
test.describe("comprovantes em conferência a 360 px", () => {
  test.use({ viewport: { width: 360, height: 740 } });

  for (const scheme of ["light", "dark"] as const) {
    test(`@critical gestão recusa e confirma comprovantes (${scheme})`, async ({ page }) => {
      await page.emulateMedia({ colorScheme: scheme });
      await page.goto("/entrar");
      await page.getByLabel("E-mail").fill("varios@exemplo.com");
      await page.getByLabel("Senha").fill("segredo123");
      await page.getByRole("button", { name: "Entrar" }).click();
      await chooseLink(page, /Academia Alfa Gestão \(Administrador\)/);
      await expect(page.getByRole("heading", { name: "Bem-vindo ao ctcore" })).toBeVisible();

      // Cenário: chave Pix na unidade e a mensalidade do aluno adulto.
      const unit = (await call(page, "GET", `/units/${CENTRO}`)) as { settings: object; version: number };
      await call(page, "PUT", `/units/${CENTRO}`, { ...unit, settings: { ...unit.settings, pixKey: "pix@alfa.test" } }, { "If-Match": String(unit.version) });
      const done = (await call(page, "POST", `/students/${ALUNO_ADULTO}/enrollment/conclude`, { modalities: [BJJ], units: [{ unitId: CENTRO }] })) as { charges: { id: string }[] };
      const chargeId = done.charges[0].id;

      // Aluno: aceita o termo e envia o comprovante.
      await chooseLink(page, /Academia Alfa Portal \(Aluno\)/);
      await expect(page).toHaveURL(/\/portal\/termo$/);
      await page.getByLabel("Li e aceito o termo de matrícula").check();
      await page.getByRole("button", { name: "Aceitar e continuar" }).click();
      await expect(page).not.toHaveURL(/\/portal\/termo$/);
      await call(page, "POST", `/portal/charges/${chargeId}/proof`, undefined, {}, true);

      // Gestão: a cobrança está em conferência e o sino avisa.
      await chooseLink(page, /Academia Alfa Gestão \(Administrador\)/);
      await open(page, "/cobrancas/conferencia");
      await expect(page.getByRole("heading", { name: "Comprovantes em conferência", level: 1 })).toBeVisible();
      await expect(page.getByRole("button", { name: /^Confirmar comprovante de/ })).toBeVisible();
      expect(await noOverflow(page)).toBe(true);
      expect(await violations(page)).toEqual([]);

      await page.getByRole("button", { name: /^Recusar comprovante de/ }).click();
      const refuse = page.getByRole("dialog", { name: "Recusar comprovante" });
      await refuse.getByLabel(/Motivo da recusa/).fill("O valor do Pix não confere.");
      expect(await violations(page)).toEqual([]);
      await refuse.getByRole("button", { name: "Recusar comprovante" }).click();
      await expect(refuse).toHaveCount(0);
      await expect(page.getByText("Nenhum comprovante em conferência")).toBeVisible();

      // O aluno envia outro; a gestão confirma com o recibo.
      await chooseLink(page, /Academia Alfa Portal \(Aluno\)/);
      await call(page, "POST", `/portal/charges/${chargeId}/proof`, undefined, {}, true);
      await chooseLink(page, /Academia Alfa Gestão \(Administrador\)/);
      await open(page, "/cobrancas/conferencia");
      await page.getByRole("button", { name: /^Confirmar comprovante de/ }).click();
      const confirm = page.getByRole("dialog", { name: "Confirmar comprovante" });
      await expect(confirm.getByLabel("Enviar o recibo por e-mail")).toBeChecked();
      expect(await violations(page)).toEqual([]);
      await confirm.getByRole("button", { name: "Confirmar pagamento" }).click();
      await expect(confirm).toHaveCount(0);
      await expect(page.getByText("Nenhum comprovante em conferência")).toBeVisible();
      expect(await noOverflow(page)).toBe(true);
    });
  }
});
