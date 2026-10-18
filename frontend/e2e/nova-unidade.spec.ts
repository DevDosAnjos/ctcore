import { expect, test, type Page } from "@playwright/test";

async function loginAs(page: Page, email: string) {
  await page.goto("/entrar");
  await page.getByLabel("E-mail").fill(email);
  await page.getByLabel("Senha").fill("segredo123");
  await page.getByRole("button", { name: "Entrar" }).click();
}

// A sessão simulada vive na memória da página: a navegação é feita pelo roteador, sem recarregar (ver unidades.spec.ts).
async function open(page: Page, path: string) {
  await page.evaluate(`history.pushState({}, "", ${JSON.stringify(path)}); dispatchEvent(new PopStateEvent("popstate"))`);
}

// RF-ORG-03: o Coordenador cria a unidade com convite; o Administrador não tem acesso ao assistente.
test("@critical nova unidade: Coordenador cria com convite, Administrador sem acesso", async ({ page }) => {
  await loginAs(page, "ana@exemplo.com");
  await expect(page.getByRole("heading", { name: "Bem-vindo ao ctcore" })).toBeVisible();
  await open(page, "/configuracoes/unidades");
  await page.getByRole("link", { name: "Nova unidade" }).click();
  await page.getByLabel("Nome da unidade").fill("Academia Tijuca");
  await page.getByRole("button", { name: "Avançar" }).click();
  await page.getByLabel("CEP").fill("01001000");
  await page.getByRole("button", { name: "Avançar" }).click();
  await page.getByLabel("Nome do Administrador").fill("Ana Nova");
  await page.getByLabel("E-mail do Administrador").fill("nova@exemplo.com");
  await page.getByRole("button", { name: "Concluir" }).click();
  await expect(page.getByRole("heading", { name: "Unidades", level: 1 })).toBeVisible();
  await expect(page.getByRole("heading", { name: "Academia Tijuca" })).toBeVisible();

  await page.evaluate('fetch("/api/v1/auth/logout", { method: "POST" })');
  await page.goto("/entrar");
  await loginAs(page, "varios@exemplo.com");
  await page.getByRole("dialog").getByRole("button", { name: /Academia Alfa Gestão \(Administrador\)/ }).click();
  await expect(page.getByRole("heading", { name: "Bem-vindo ao ctcore" })).toBeVisible();
  await open(page, "/configuracoes/unidades/nova");
  await expect(page.getByRole("heading", { name: "Esta área não está disponível para o seu perfil" })).toBeVisible();
});
