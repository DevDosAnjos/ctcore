import { expect, test, type Page } from "@playwright/test";

async function login(page: Page, email: string) {
  await page.goto("/entrar");
  await page.getByLabel("E-mail").fill(email);
  await page.getByLabel("Senha").fill("segredo123");
  await page.getByRole("button", { name: "Entrar" }).click();
}

// RF-ACE-05, RN-ACE-03: vários vínculos abrem o modal; escolher fecha e fica em "/"; o botão reabre; um vínculo só não abre.
test("@critical escolha de organização e área", async ({ page }) => {
  await login(page, "varios@exemplo.com");
  const modal = page.getByRole("dialog", { name: "Escolher organização e área" });
  await expect(modal).toBeVisible();
  await page.keyboard.press("Escape");
  await expect(modal).toBeVisible();

  await modal.getByRole("button", { name: /Academia Beta Gestão \(Professor\)/ }).click();
  await expect(modal).toBeHidden();
  await expect(page).toHaveURL(/\/$/);
  await expect(page.getByRole("heading", { name: "Bem-vindo ao ctcore" })).toBeVisible();

  const trigger = page.getByRole("button", { name: "Trocar organização ou área" });
  await trigger.click();
  await expect(modal).toBeVisible();
  await expect(modal.getByRole("button", { name: /Academia Beta Gestão/ })).toHaveAttribute("aria-current", "true");
  await page.keyboard.press("Escape");
  await expect(modal).toBeHidden();
});

test("@critical um vínculo só não abre o modal", async ({ page }) => {
  await login(page, "ana@exemplo.com");
  await expect(page.getByRole("heading", { name: "Bem-vindo ao ctcore" })).toBeVisible();
  await expect(page.getByRole("dialog")).toHaveCount(0);
  await expect(page.getByRole("button", { name: "Trocar organização ou área" })).toHaveCount(0);
});
