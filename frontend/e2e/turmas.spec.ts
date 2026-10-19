import { expect, test, type Page } from "@playwright/test";

async function open(page: Page, path: string) {
  await page.evaluate(`history.pushState({}, "", ${JSON.stringify(path)}); dispatchEvent(new PopStateEvent("popstate"))`);
}

// RF-TUR-01: o Coordenador abre Operação › Turmas, vê a lista e filtra pelo dia e pela modalidade.
test("@critical Coordenador filtra a lista de turmas", async ({ page }) => {
  await page.goto("/entrar");
  await page.getByLabel("E-mail").fill("ana@exemplo.com");
  await page.getByLabel("Senha").fill("segredo123");
  await page.getByRole("button", { name: "Entrar" }).click();
  await expect(page.getByRole("heading", { name: "Bem-vindo ao ctcore" })).toBeVisible();
  // A modalidade só aparece no filtro depois de ativa na organização.
  await open(page, "/configuracoes/modalidades");
  await page.locator("section", { has: page.getByRole("heading", { name: "Disponíveis para ativar" }) }).getByRole("button", { name: /Ativar\s*Judô/ }).click();
  await expect(page.getByRole("button", { name: /Desativar\s*Judô/ })).toBeVisible();
  const nav = page.getByRole("navigation", { name: "Menu principal" });
  await nav.getByText("Operação", { exact: true }).click();
  await expect(nav.getByRole("link", { name: "Turmas" })).toBeVisible();
  await open(page, "/turmas"); // o link recarrega a página e a sessão simulada se perderia
  await expect(page.getByRole("heading", { name: "Turmas", level: 1 })).toBeVisible();
  await expect(page.getByText("Jiu-Jitsu Adulto")).toBeVisible();
  await expect(page.getByText("Judô Infantil")).toBeVisible();
  await expect(page.getByText("Boxe Noturno")).toBeVisible();

  await page.getByLabel("Dia").selectOption("2");
  await expect(page.getByText("Judô Infantil")).toBeVisible();
  await expect(page.getByText("Jiu-Jitsu Adulto")).toHaveCount(0);

  await page.getByLabel("Modalidade").selectOption({ label: "Judô" });
  await expect(page.getByText("Judô Infantil")).toBeVisible();
  await expect(page.getByText("Boxe Noturno")).toHaveCount(0);
});
