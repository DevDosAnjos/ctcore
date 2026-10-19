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

// RF-TUR-02: o Coordenador entra no painel de uma turma da lista, desativa e vê o selo "Desativada".
test("@critical Coordenador abre e desativa uma turma", async ({ page }) => {
  await page.goto("/entrar");
  await page.getByLabel("E-mail").fill("ana@exemplo.com");
  await page.getByLabel("Senha").fill("segredo123");
  await page.getByRole("button", { name: "Entrar" }).click();
  await expect(page.getByRole("heading", { name: "Bem-vindo ao ctcore" })).toBeVisible();
  await open(page, "/turmas");
  await page.getByRole("link", { name: "Jiu-Jitsu Adulto" }).click();
  await expect(page.getByRole("heading", { name: "Jiu-Jitsu Adulto", level: 1 })).toBeVisible();
  await expect(page.getByRole("link", { name: "Abrir grupo" })).toBeVisible();
  await expect(page.getByRole("button", { name: "Desativar" })).toBeEnabled();
  await page.getByRole("button", { name: "Desativar" }).click();
  await page.getByRole("dialog").getByRole("button", { name: "Desativar" }).click();
  await expect(page.getByText("Turma desativada.")).toBeVisible();
  await expect(page.getByText("Desativada", { exact: true })).toBeVisible();
  await expect(page.getByRole("button", { name: "Desativar" })).toBeDisabled();
});

// RF-TUR-03: o Coordenador abre Operação › Turmas, cria uma turma com nome, unidade, modalidade, titular e um horário e vê o painel dela.
test("@critical Coordenador cria uma turma", async ({ page }) => {
  await page.goto("/entrar");
  await page.getByLabel("E-mail").fill("ana@exemplo.com");
  await page.getByLabel("Senha").fill("segredo123");
  await page.getByRole("button", { name: "Entrar" }).click();
  await expect(page.getByRole("heading", { name: "Bem-vindo ao ctcore" })).toBeVisible();
  await open(page, "/turmas");
  await expect(page.getByRole("heading", { name: "Turmas", level: 1 })).toBeVisible();

  await page.getByRole("button", { name: "Nova turma" }).click();
  const dialog = page.getByRole("dialog", { name: "Nova turma" });
  await dialog.getByLabel("Nome da turma").fill("Muay Thai Noite");
  await dialog.getByLabel("Unidade").selectOption({ label: "Unidade Centro" });
  await dialog.getByLabel("Modalidade").selectOption({ label: "Judô" });
  await dialog.getByLabel("Professor titular").selectOption({ label: "Ana Souza" });
  await dialog.getByRole("checkbox", { name: "Sexta-feira" }).check();
  await dialog.getByRole("button", { name: "Salvar" }).click();

  await expect(page.getByRole("heading", { name: "Muay Thai Noite", level: 1 })).toBeVisible();
  await expect(page.getByText("Sexta-feira, 19:00–20:00")).toBeVisible();
});
