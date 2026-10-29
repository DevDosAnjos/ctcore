import { createRequire } from "node:module";
import { expect, test } from "@playwright/test";
import type { Page } from "@playwright/test";

const axePath = createRequire(import.meta.url).resolve("axe-core/axe.min.js");

const violations = async (page: Page) => {
  await page.addScriptTag({ path: axePath });
  return page.evaluate(async () => (await (globalThis as unknown as { axe: { run: () => Promise<{ violations: unknown[] }> } }).axe.run()).violations);
};
const noOverflow = (page: Page) => page.evaluate("document.documentElement.scrollWidth <= window.innerWidth");

async function login(page: Page, email: string) {
  await page.goto("/entrar");
  await page.getByLabel("E-mail").fill(email);
  await page.getByLabel("Senha").fill("segredo123");
  await page.getByRole("button", { name: "Entrar" }).click();
}

// RF-POR-02 (#89): Início do portal a 360 px, nos dois temas.
test.describe("início do portal a 360 px", () => {
  test.use({ viewport: { width: 360, height: 740 } });

  for (const scheme of ["light", "dark"] as const) {
    test(`@critical aluno vê as quatro seções na ordem, filtra por modalidade e unidade e passa no axe (${scheme})`, async ({ page }) => {
      await page.emulateMedia({ colorScheme: scheme });
      await login(page, "termo@exemplo.com");
      await page.getByLabel("Li e aceito o termo de matrícula").check();
      await page.getByRole("button", { name: "Aceitar e continuar" }).click();

      await expect(page.getByText("Tudo em dia")).toBeVisible();
      await expect(page.getByRole("heading", { level: 2 })).toHaveText(["Cobrança", "Próximas aulas", "Avisos", "Faixa"]);
      await expect(page.getByText("Cancelada", { exact: true })).toBeVisible();
      await expect(page.getByText("Alterada", { exact: true })).toBeVisible();
      await expect(page.getByText("Nova", { exact: true })).toBeVisible();
      expect(await noOverflow(page)).toBe(true);
      expect(await violations(page)).toEqual([]);

      await page.getByLabel("Filtrar por modalidade e unidade").selectOption({ label: "Judô · Unidade Norte" });
      await expect(page.getByText("Judô Adulto")).toBeVisible();
      await expect(page.getByText("Jiu-Jitsu Adulto")).toHaveCount(0);
      await expect(page.getByText("Azul, grau 2")).toHaveCount(0);
      expect(await noOverflow(page)).toBe(true);
    });

    test(`@critical responsável troca de cartão e vê o total da família (${scheme})`, async ({ page }) => {
      await page.emulateMedia({ colorScheme: scheme });
      await login(page, "familia@exemplo.com");
      const strip = page.getByRole("region", { name: "Alunos da família" });
      await expect(strip.getByRole("button")).toHaveCount(2);
      await expect(strip.getByText(/Total da família em aberto: R\$\s50,00/)).toBeVisible();
      await expect(page.getByText("Tudo em dia")).toBeVisible();

      await strip.getByRole("button", { name: /^Filho Fictício Dois/ }).click();
      await expect(page.getByText("Mensalidade de Judô")).toBeVisible();
      await expect(page.getByText("Vencendo", { exact: true })).toBeVisible();
      await expect(strip.getByRole("button", { name: /^Filho Fictício Dois/ })).toHaveAttribute("aria-pressed", "true");
      expect(await noOverflow(page)).toBe(true);
      expect(await violations(page)).toEqual([]);
    });
  }

  test("@critical portal reduzido mostra só a cobrança vencida e o Pagar abre o Pix", async ({ page }) => {
    await login(page, "reduzido@exemplo.com");
    await expect(page.getByRole("heading", { level: 2 })).toHaveText(["Cobrança"]);
    await expect(page.getByText("Vencida", { exact: true })).toBeVisible();
    expect(await violations(page)).toEqual([]);
    await page.getByRole("button", { name: "Pagar Mensalidade em atraso" }).click();
    await expect(page.getByRole("dialog", { name: "Pix na chave com comprovante" })).toBeVisible();
    expect(await noOverflow(page)).toBe(true);
  });
});
