import { expect, test, type Page } from "@playwright/test";

async function open(page: Page, path: string) {
  await page.evaluate(`history.pushState({}, "", ${JSON.stringify(path)}); dispatchEvent(new PopStateEvent("popstate"))`);
}

// RF-ALU-01: o Coordenador abre Pessoas › Alunos, vê a lista e filtra pelo nome.
test("@critical Coordenador filtra a lista de alunos", async ({ page }) => {
  await page.goto("/entrar");
  await page.getByLabel("E-mail").fill("ana@exemplo.com");
  await page.getByLabel("Senha").fill("segredo123");
  await page.getByRole("button", { name: "Entrar" }).click();
  await expect(page.getByRole("heading", { name: "Bem-vindo ao ctcore" })).toBeVisible();
  const nav = page.getByRole("navigation", { name: "Menu principal" });
  await nav.getByText("Pessoas", { exact: true }).click();
  await expect(nav.getByRole("link", { name: "Alunos" })).toBeVisible();
  await open(page, "/alunos"); // o link recarrega a página e a sessão simulada se perderia
  await expect(page.getByRole("heading", { name: "Alunos", level: 1 })).toBeVisible();
  await expect(page.getByText("Aluno Adulto Fictício")).toBeVisible();
  await expect(page.getByText("Aluno Menor Fictício")).toBeVisible();

  await page.getByRole("searchbox", { name: "Buscar" }).fill("menor");
  await expect(page.getByText("Aluno Menor Fictício")).toBeVisible();
  await expect(page.getByText("Aluno Adulto Fictício")).toHaveCount(0);
});

// RF-ALU-02: o Coordenador filtra a lista pelo selo "Cadastro incompleto" e vê só o aluno esperado, com a etiqueta.
test("@critical Coordenador filtra alunos por selo", async ({ page }) => {
  await page.goto("/entrar");
  await page.getByLabel("E-mail").fill("ana@exemplo.com");
  await page.getByLabel("Senha").fill("segredo123");
  await page.getByRole("button", { name: "Entrar" }).click();
  await expect(page.getByRole("heading", { name: "Bem-vindo ao ctcore" })).toBeVisible();
  await open(page, "/alunos");
  await expect(page.getByText("Aluno Menor Fictício")).toBeVisible();

  await page.getByRole("combobox", { name: "Selo" }).selectOption({ label: "Cadastro incompleto" });
  await expect(page.getByText("Aluno Menor Fictício")).toHaveCount(0);
  await expect(page.getByText("Aluno Adulto Fictício")).toBeVisible();
  await expect(page.getByText("Cadastro incompleto", { exact: true }).and(page.locator("span"))).toBeVisible();
});
