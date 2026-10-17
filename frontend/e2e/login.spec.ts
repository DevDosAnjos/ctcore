import { expect, test } from "@playwright/test";

// RF-ACE-01, RN-ACE-16: sem sessão a pessoa vai a /entrar; erro de senha; login volta ao caminho pedido.
test("@critical login: redireciona, recusa senha errada e entra", async ({ page }) => {
  await page.goto("/");
  await expect(page).toHaveURL(/\/entrar\?next=%2F$/);
  await expect(page.getByRole("link", { name: "Esqueci minha senha" })).toHaveAttribute("href", "/esqueci-senha");

  await page.getByLabel("E-mail").fill("ana@exemplo.com");
  await page.getByLabel("Senha").fill("errada");
  await page.getByRole("button", { name: "Entrar" }).click();
  await expect(page.getByRole("alert")).toHaveText("E-mail ou senha incorretos.");
  await expect(page.getByLabel("Senha")).toBeFocused();

  await page.getByLabel("Senha").fill("segredo123");
  await page.getByRole("button", { name: "Entrar" }).click();
  await expect(page.getByRole("heading", { name: "Bem-vindo ao ctcore" })).toBeVisible();
  await expect(page).toHaveURL(/\/$/);
});
