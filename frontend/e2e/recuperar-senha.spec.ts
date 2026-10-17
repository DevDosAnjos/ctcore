import { expect, test } from "@playwright/test";

// RF-ACE-03: pede o link (mesma mensagem), cria a senha pelo link, entra; link vencido explica.
test("@critical Recuperação de senha", async ({ page }) => {
  await page.goto("/entrar");
  await page.getByRole("link", { name: "Esqueci minha senha" }).click();
  await page.getByLabel("E-mail").fill("ana@exemplo.com");
  await page.getByRole("button", { name: "Enviar link" }).click();
  await expect(page.getByText("Se esse e-mail tiver uma conta, você vai receber um link em instantes.")).toBeVisible();

  await page.goto("/redefinir-senha/valido");
  const botao = page.getByRole("button", { name: "Criar senha e entrar" });
  await page.getByLabel("Senha", { exact: true }).fill("fraca");
  await page.getByLabel("Confirmar senha").fill("fraca");
  await expect(botao).toBeDisabled();
  await page.getByLabel("Senha", { exact: true }).fill("Senha123");
  await page.getByLabel("Confirmar senha").fill("Senha123");
  await expect(botao).toBeEnabled();
  await botao.click();
  await expect(page.getByRole("heading", { name: "Bem-vindo ao ctcore" })).toBeVisible();
  await expect(page).toHaveURL(/\/$/);

  await page.goto("/redefinir-senha/vencido");
  await page.getByLabel("Senha", { exact: true }).fill("Senha123");
  await page.getByLabel("Confirmar senha").fill("Senha123");
  await page.getByRole("button", { name: "Criar senha e entrar" }).click();
  await expect(page.getByRole("heading", { name: "Este link não é válido ou já foi usado" })).toBeVisible();
});
