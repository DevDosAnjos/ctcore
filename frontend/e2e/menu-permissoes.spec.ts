import { expect, test, type Page } from "@playwright/test";

async function loginAs(page: Page, email: string) {
  await page.goto("/entrar");
  await page.getByLabel("E-mail").fill(email);
  await page.getByLabel("Senha").fill("segredo123");
  await page.getByRole("button", { name: "Entrar" }).click();
}

// R7: o menu da gestão vem das permissões do servidor (mock). Hoje só o Início tem tela (D7), então
// Configurações e Financeiro ainda não aparecem para ninguém; ver pedido de ajuste do CA16 no arquivo do item.
test("@critical menu da gestão por perfil", async ({ page }) => {
  await loginAs(page, "ana@exemplo.com");
  await expect(page.getByRole("heading", { name: "Bem-vindo ao ctcore" })).toBeVisible();
  const nav = page.getByRole("navigation", { name: "Menu principal" });
  await expect(nav.getByRole("link", { name: "Início" })).toHaveAttribute("aria-current", "page");

  await page.evaluate('fetch("/api/v1/auth/logout", { method: "POST" })');
  await page.goto("/entrar");
  await loginAs(page, "varios@exemplo.com");
  await page.getByRole("dialog").getByRole("button", { name: /Academia Beta Gestão \(Professor\)/ }).click();
  await expect(page.getByRole("heading", { name: "Bem-vindo ao ctcore" })).toBeVisible();
  await expect(nav.getByRole("link", { name: "Início" })).toBeVisible();
  await expect(nav.getByText("Financeiro")).toHaveCount(0);
  await expect(nav.getByText("Configurações")).toHaveCount(0);
});
