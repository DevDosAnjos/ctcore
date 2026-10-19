import { expect, test, type Page } from "@playwright/test";

async function open(page: Page, path: string) {
  await page.evaluate(`history.pushState({}, "", ${JSON.stringify(path)}); dispatchEvent(new PopStateEvent("popstate"))`);
}

// RF-MOD-04: o Coordenador cria uma faixa etária e a vê na lista ao voltar à tela.
test("@critical Coordenador cria uma faixa etária", async ({ page }) => {
  await page.goto("/entrar");
  await page.getByLabel("E-mail").fill("ana@exemplo.com");
  await page.getByLabel("Senha").fill("segredo123");
  await page.getByRole("button", { name: "Entrar" }).click();
  await expect(page.getByRole("heading", { name: "Bem-vindo ao ctcore" })).toBeVisible();
  const nav = page.getByRole("navigation", { name: "Menu principal" });
  await nav.getByText("Configurações", { exact: true }).click();
  await expect(nav.getByRole("link", { name: "Faixas etárias" })).toBeVisible();
  await open(page, "/configuracoes/faixas-etarias"); // o link recarrega a página e a sessão simulada se perderia
  await expect(page.getByRole("heading", { name: "Faixas etárias", level: 1 })).toBeVisible();
  await expect(page.getByText("Adulto · 18 anos ou mais")).toBeVisible();

  await page.getByRole("button", { name: "Nova faixa" }).click();
  const dialog = page.getByRole("dialog", { name: "Nova faixa etária" });
  await dialog.getByLabel("Nome da faixa").fill("Master");
  await dialog.getByLabel("Idade mínima").fill("35");
  await dialog.getByRole("button", { name: "Salvar" }).click();
  await expect(page.getByText("Master · 35 anos ou mais")).toBeVisible();

  await open(page, "/");
  await expect(page.getByRole("heading", { name: "Bem-vindo ao ctcore" })).toBeVisible();
  await open(page, "/configuracoes/faixas-etarias");
  await expect(page.getByText("Master · 35 anos ou mais")).toBeVisible();
});
