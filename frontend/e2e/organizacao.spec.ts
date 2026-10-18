import { expect, test, type Page } from "@playwright/test";

async function loginAs(page: Page, email: string) {
  await page.goto("/entrar");
  await page.getByLabel("E-mail").fill(email);
  await page.getByLabel("Senha").fill("segredo123");
  await page.getByRole("button", { name: "Entrar" }).click();
}

// A sessão simulada vive na memória da página: recarregar de verdade a perderia. A navegação é feita pelo roteador
// (history + popstate), como um clique em link interno, e voltar à tela refaz a consulta ao "servidor".
async function open(page: Page, path: string) {
  await page.evaluate(`history.pushState({}, "", ${JSON.stringify(path)}); dispatchEvent(new PopStateEvent("popstate"))`);
}

// RF-ORG-01: o Coordenador altera nome e cor e vê o valor novo ao voltar à tela; o Administrador só vê.
test("@critical configurações da organização: Coordenador edita e Administrador só lê", async ({ page }) => {
  await loginAs(page, "ana@exemplo.com");
  await expect(page.getByRole("heading", { name: "Bem-vindo ao ctcore" })).toBeVisible();
  await open(page, "/configuracoes/organizacao");
  await expect(page.getByRole("heading", { name: "Organização", level: 1 })).toBeVisible();

  await page.getByLabel("Nome da organização").fill("Dojo Novo");
  await page.getByLabel("Cor principal (#RRGGBB)").fill("#ffeb3b");
  await expect(page.getByText(/Reprovado/)).toBeVisible();
  await expect(page.getByRole("button", { name: "Salvar" })).toBeDisabled();
  await page.getByLabel("Cor principal (#RRGGBB)").fill("#1a56db");
  await expect(page.getByText(/Aprovado/)).toBeVisible();
  await page.getByRole("button", { name: "Salvar" }).click();
  await expect(page.getByText("Dados salvos.")).toBeVisible();

  await open(page, "/");
  await expect(page.getByRole("heading", { name: "Bem-vindo ao ctcore" })).toBeVisible();
  await open(page, "/configuracoes/organizacao");
  await expect(page.getByLabel("Nome da organização")).toHaveValue("Dojo Novo");
  await expect(page.getByLabel("Cor principal (#RRGGBB)")).toHaveValue("#1a56db");

  await page.evaluate('fetch("/api/v1/auth/logout", { method: "POST" })');
  await page.goto("/entrar");
  await loginAs(page, "varios@exemplo.com");
  await page.getByRole("dialog").getByRole("button", { name: /Academia Alfa Gestão \(Administrador\)/ }).click();
  await expect(page.getByRole("heading", { name: "Bem-vindo ao ctcore" })).toBeVisible();
  await open(page, "/configuracoes/organizacao");
  await expect(page.getByRole("heading", { name: "Organização", level: 1 })).toBeVisible();
  await expect(page.getByLabel("Nome da organização")).toBeDisabled();
  await expect(page.getByRole("button", { name: "Salvar" })).toHaveCount(0);
  await expect(page.getByRole("button", { name: "Enviar logo" })).toHaveCount(0);
});
