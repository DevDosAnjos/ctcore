import { expect, test } from "@playwright/test";

// RNF-36: fluxo crítico de hoje (abrir o app, textos em pt-BR, trocar de tema).
test("@critical abre o app em pt-BR e alterna o tema", async ({ page }) => {
  await page.goto("/entrar");
  await expect(page.getByRole("heading", { name: "Entrar" })).toBeVisible();

  const html = page.locator("html");
  const isDark = async () => (await html.getAttribute("class"))?.includes("dark") ?? false;
  const antes = await isDark();
  await page.getByRole("button", { name: /Usar tema (escuro|claro)/ }).click();
  expect(await isDark()).toBe(!antes);
  await page.getByRole("button", { name: /Usar tema (escuro|claro)/ }).click();
  expect(await isDark()).toBe(antes);
});
