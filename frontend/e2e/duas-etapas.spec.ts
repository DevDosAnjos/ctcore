import { expect, test } from "@playwright/test";

// RN-ACE-17 / RF-ACE-01 (LoginDuasEtapas): senha certa leva ao segundo passo; o código conclui o login.
test("@critical login com duas etapas: senha, segundo passo, código e área", async ({ page }) => {
  await page.goto("/entrar");
  await page.getByLabel("E-mail").fill("duas@exemplo.com");
  await page.getByLabel("Senha").fill("segredo123");
  await page.getByRole("button", { name: "Entrar" }).click();

  await expect(page.getByRole("heading", { name: "Verificação em duas etapas" })).toBeVisible();
  await page.getByLabel("Código do app").fill("000000");
  await page.getByRole("button", { name: "Confirmar" }).click();
  await expect(page.getByRole("alert")).toContainText("Código incorreto");

  await page.getByLabel("Código do app").fill("123456");
  await page.getByRole("button", { name: "Confirmar" }).click();
  await expect(page.getByRole("heading", { name: "Bem-vindo ao ctcore" })).toBeVisible();
});
