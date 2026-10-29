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

// RF-POR-01 (#88): barra e abas do portal a 360 px; Agenda e Avisos somem no portal reduzido.
test.describe("barra do portal a 360 px", () => {
  test.use({ viewport: { width: 360, height: 740 } });

  for (const scheme of ["light", "dark"] as const) {
    test(`@critical abas fixas embaixo, sino com contador e axe (${scheme})`, async ({ page }) => {
      await page.emulateMedia({ colorScheme: scheme });
      await login(page, "termo@exemplo.com");
      await expect(page.getByRole("heading", { name: "Termo de matrícula", level: 1 })).toBeVisible();
      await page.getByLabel("Li e aceito o termo de matrícula").check();
      await page.getByRole("button", { name: "Aceitar e continuar" }).click();
      await expect(page.getByRole("link", { name: "Academia Alfa" })).toBeVisible();

      const nav = page.getByRole("navigation", { name: "Portal" });
      await expect(nav.getByRole("link")).toHaveText(["Início", "Agenda", "Financeiro", /^Avisos/, "Eu"]); // a 360 px valem as abas de baixo, com nome curto (#418)
      const box = (await nav.boundingBox())!;
      expect(Math.round(box.y + box.height)).toBe(740);
      expect(await noOverflow(page)).toBe(true);
      await expect(page.getByRole("link", { name: "Avisos, 3 não lidos" })).toHaveAttribute("href", "/portal/avisos");

      await nav.getByRole("link", { name: "Financeiro" }).click();
      await expect(page.getByRole("heading", { name: "Financeiro", level: 1 })).toBeVisible();
      await expect(nav.getByRole("link", { name: "Financeiro" })).toHaveAttribute("aria-current", "page");
      expect(await noOverflow(page)).toBe(true);
      expect(await violations(page)).toEqual([]);
    });
  }

  test("@critical portal reduzido: sem Agenda, Avisos e sino, e o endereço de Avisos leva ao Início", async ({ page }) => {
    await login(page, "reduzido@exemplo.com");
    await expect(page.getByRole("heading", { name: "Bem-vindo ao ctcore" })).toBeVisible();
    const nav = page.getByRole("navigation", { name: "Portal" });
    await expect(nav.getByRole("link")).toHaveText(["Início", "Financeiro", "Eu"]);
    await expect(page.getByText(/Seu acesso está reduzido/)).toBeVisible();
    await expect(page.getByRole("link", { name: /^Avisos/ })).toHaveCount(0);

    await page.evaluate(`history.pushState({}, "", "/portal/avisos"); dispatchEvent(new PopStateEvent("popstate"))`);
    await expect(page.getByRole("heading", { name: "Bem-vindo ao ctcore" })).toBeVisible();
    expect(new URL(page.url()).pathname).toBe("/");
    expect(await violations(page)).toEqual([]);
  });
});
