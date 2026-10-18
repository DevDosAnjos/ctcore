import { expect, test } from "@playwright/test";

// RF-ACE-04 / RN-ACE-20: trocar a senha em Minha conta encerra as outras sessões e mantém a atual.
// O MSW vive em cada aba, então a "outra sessão" é a do aparelho listado pelo mock; o 401 dela é provado no back-end (SessionListIT).
test("@critical troca de senha encerrando as outras sessões", async ({ page }) => {
  await page.goto("/entrar");
  await page.getByLabel("E-mail").fill("ana@exemplo.com");
  await page.getByLabel("Senha").fill("segredo123");
  await page.getByRole("button", { name: "Entrar" }).click();
  await expect(page.getByRole("heading", { name: "Bem-vindo ao ctcore" })).toBeVisible();

  await page.getByRole("link", { name: "Minha conta" }).first().click();
  await expect(page.getByRole("heading", { name: "Minha conta", level: 1 })).toBeVisible();
  await expect(page.getByText("Safari em iOS")).toBeVisible();

  await page.getByLabel("Senha atual").fill("Atual12345");
  await page.locator("#acc-senha").fill("NovaSenha123");
  await page.getByLabel("Confirmar senha").fill("NovaSenha123");
  await page.getByRole("button", { name: "Trocar senha" }).click();

  await expect(page.getByText("Senha alterada. As outras sessões foram encerradas.")).toBeVisible();
  await expect(page.getByText("Safari em iOS")).toHaveCount(0);
  await expect(page.getByText("Chrome em Windows")).toBeVisible();
  await expect(page.getByText("Esta sessão")).toBeVisible();
});
