import { expect, test, type Page } from "@playwright/test";

async function open(page: Page, path: string) {
  await page.evaluate(`history.pushState({}, "", ${JSON.stringify(path)}); dispatchEvent(new PopStateEvent("popstate"))`);
}

// RF-PRO-01: o Coordenador abre Pessoas › Professores, vê a lista e filtra pelo nome.
test("@critical Coordenador filtra a lista de professores", async ({ page }) => {
  await page.goto("/entrar");
  await page.getByLabel("E-mail").fill("ana@exemplo.com");
  await page.getByLabel("Senha").fill("segredo123");
  await page.getByRole("button", { name: "Entrar" }).click();
  await expect(page.getByRole("heading", { name: "Bem-vindo ao ctcore" })).toBeVisible();
  const nav = page.getByRole("navigation", { name: "Menu principal" });
  await nav.getByText("Pessoas", { exact: true }).click();
  await expect(nav.getByRole("link", { name: "Professores" })).toBeVisible();
  await open(page, "/professores"); // o link recarrega a página e a sessão simulada se perderia
  await expect(page.getByRole("heading", { name: "Professores", level: 1 })).toBeVisible();
  await expect(page.getByText("Ana Souza")).toBeVisible();
  await expect(page.getByText("João Pereira")).toBeVisible();

  await page.getByRole("searchbox", { name: "Buscar" }).fill("joao");
  await expect(page.getByText("João Pereira")).toBeVisible();
  await expect(page.getByText("Ana Souza")).toHaveCount(0);
});
