import { createRequire } from "node:module";
import { expect, test } from "@playwright/test";
import type { Page } from "@playwright/test";

const axePath = createRequire(import.meta.url).resolve("axe-core/axe.min.js");

const violations = async (page: Page) => {
  await page.addScriptTag({ path: axePath });
  return page.evaluate(async () => ((await (globalThis as unknown as { axe: { run: () => Promise<{ violations: unknown[] }> } }).axe.run()).violations));
};

// RN-MAT-11, RN-POR-03 (#85): aluno com matrícula sem aceite é levado ao termo antes de qualquer tela; aceita e segue.
test.describe("aceite do termo no portal a 360 px", () => {
  test.use({ viewport: { width: 360, height: 740 } });

  for (const scheme of ["light", "dark"] as const) {
    test(`@critical aceite do termo e acesso ao início (${scheme})`, async ({ page }) => {
      await page.emulateMedia({ colorScheme: scheme });
      await page.goto("/entrar");
      await page.getByLabel("E-mail").fill("termo@exemplo.com");
      await page.getByLabel("Senha").fill("segredo123");
      await page.getByRole("button", { name: "Entrar" }).click();

      await expect(page).toHaveURL(/\/portal\/termo$/);
      await expect(page.getByRole("heading", { name: "Termo de matrícula", level: 1 })).toBeVisible();
      const image = page.getByLabel(/Autorizo o uso de imagem/);
      await expect(image).not.toBeChecked();
      expect(await page.evaluate("document.documentElement.scrollWidth <= window.innerWidth")).toBe(true);
      expect(await violations(page)).toEqual([]);

      const submit = page.getByRole("button", { name: "Aceitar e continuar" });
      await expect(submit).toBeDisabled();
      await page.getByLabel("Li e aceito o termo de matrícula").check();
      await submit.click();

      await expect(page.getByRole("heading", { name: "Bem-vindo ao ctcore" })).toBeVisible();
      await expect(page).toHaveURL(/\/$/);
    });
  }
});
