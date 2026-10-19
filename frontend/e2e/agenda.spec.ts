import { expect, test, type Page } from "@playwright/test";

async function open(page: Page, path: string) {
  await page.evaluate(`history.pushState({}, "", ${JSON.stringify(path)}); dispatchEvent(new PopStateEvent("popstate"))`);
}

// RF-AGE-01: o Coordenador abre Operação › Agenda, vê a grade da semana com aula e "Sem aula", troca de semana e volta por "Hoje".
test("@critical Coordenador abre a Agenda", async ({ page }) => {
  await page.goto("/entrar");
  await page.getByLabel("E-mail").fill("ana@exemplo.com");
  await page.getByLabel("Senha").fill("segredo123");
  await page.getByRole("button", { name: "Entrar" }).click();
  await expect(page.getByRole("heading", { name: "Bem-vindo ao ctcore" })).toBeVisible();
  const nav = page.getByRole("navigation", { name: "Menu principal" });
  await nav.getByText("Operação", { exact: true }).click();
  await expect(nav.getByRole("link", { name: "Agenda" })).toBeVisible();
  await open(page, "/agenda"); // o link recarrega a página e a sessão simulada se perderia
  await expect(page.getByRole("heading", { name: "Agenda", level: 1 })).toBeVisible();
  await expect(page.getByText("Sem aula").first()).toBeVisible();
  await expect(page.getByText("Jiu-Jitsu Adulto").first()).toBeVisible();

  await page.getByRole("button", { name: "Próxima semana" }).click();
  await expect(page.getByText("Sem aula")).toHaveCount(0);
  await expect(page.getByText("Jiu-Jitsu Adulto").first()).toBeVisible();
  await page.getByRole("button", { name: "Hoje" }).click();
  await expect(page.getByText("Sem aula").first()).toBeVisible();

  // RF-AGE-02: clicar numa aula abre o painel dela.
  await page.getByRole("button", { name: "Próxima semana" }).click();
  await page.getByRole("link", { name: /Jiu-Jitsu Adulto/ }).first().click();
  await expect(page.getByRole("heading", { name: "Jiu-Jitsu Adulto", level: 1 })).toBeVisible();
  await expect(page.getByRole("link", { name: "Ver turma" })).toBeVisible();
  await expect(page.getByRole("button", { name: "Avisar turma" })).toBeDisabled();
  await page.getByRole("link", { name: "Voltar à agenda" }).click();
  await expect(page.getByRole("heading", { name: "Agenda", level: 1 })).toBeVisible();
});
