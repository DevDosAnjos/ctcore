import { expect, test } from "@playwright/test";

// RF-ACE-02: convite válido mostra nome e e-mail, exige senha forte e igual, entra; convite vencido explica.
test("@critical Convite e criação de senha", async ({ page }) => {
  await page.goto("/convite/valido");
  await expect(page.getByLabel("Nome")).toHaveValue("Nova Pessoa");
  await expect(page.getByLabel("E-mail")).toHaveValue("novo@exemplo.com");

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

  await page.goto("/convite/vencido");
  await expect(page.getByRole("heading", { name: "Este convite não é válido" })).toBeVisible();
  await expect(page.getByText("Este convite venceu")).toBeVisible();
});
