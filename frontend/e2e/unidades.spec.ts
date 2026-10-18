import { expect, test, type Page } from "@playwright/test";

async function loginAs(page: Page, email: string) {
  await page.goto("/entrar");
  await page.getByLabel("E-mail").fill(email);
  await page.getByLabel("Senha").fill("segredo123");
  await page.getByRole("button", { name: "Entrar" }).click();
}

// A sessão simulada vive na memória da página: a navegação é feita pelo roteador, sem recarregar (ver organizacao.spec.ts).
async function open(page: Page, path: string) {
  await page.evaluate(`history.pushState({}, "", ${JSON.stringify(path)}); dispatchEvent(new PopStateEvent("popstate"))`);
}

// RF-ORG-02: o Coordenador vê a lista e ajusta o nome curto uma vez; o Administrador não tem o item nem a tela.
test("@critical configurações das unidades: Coordenador lista e ajusta, Administrador sem acesso", async ({ page }) => {
  await loginAs(page, "ana@exemplo.com");
  await expect(page.getByRole("heading", { name: "Bem-vindo ao ctcore" })).toBeVisible();
  const nav = page.getByRole("navigation", { name: "Menu principal" });
  await nav.getByText("Configurações", { exact: true }).click();
  await expect(nav.getByRole("link", { name: "Unidades" })).toBeVisible();
  await open(page, "/configuracoes/unidades"); // o link recarrega a página e a sessão simulada se perderia
  await expect(page.getByRole("heading", { name: "Unidades", level: 1 })).toBeVisible();
  await expect(page.getByRole("heading", { name: "Unidade Centro" })).toBeVisible();
  await expect(page.getByText("Desativada")).toBeVisible();

  await page.getByRole("button", { name: /Ajustar nome curto/ }).click();
  await page.getByLabel("Novo nome curto").fill("centro-novo");
  await page.getByRole("dialog").getByRole("button", { name: "Ajustar" }).click();
  await expect(page.getByText("centro-novo")).toBeVisible();
  await expect(page.getByRole("button", { name: /Ajustar nome curto/ })).toHaveCount(0);

  await page.evaluate('fetch("/api/v1/auth/logout", { method: "POST" })');
  await page.goto("/entrar");
  await loginAs(page, "varios@exemplo.com");
  await page.getByRole("dialog").getByRole("button", { name: /Academia Alfa Gestão \(Administrador\)/ }).click();
  await expect(page.getByRole("heading", { name: "Bem-vindo ao ctcore" })).toBeVisible();
  await expect(page.getByRole("navigation", { name: "Menu principal" }).getByRole("link", { name: "Unidades" })).toHaveCount(0);
  await open(page, "/configuracoes/unidades");
  await expect(page.getByRole("heading", { name: "Esta área não está disponível para o seu perfil" })).toBeVisible();
});
