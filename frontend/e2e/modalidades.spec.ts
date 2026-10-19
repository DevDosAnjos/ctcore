import { expect, test, type Page } from "@playwright/test";

async function open(page: Page, path: string) {
  await page.evaluate(`history.pushState({}, "", ${JSON.stringify(path)}); dispatchEvent(new PopStateEvent("popstate"))`);
}

// RF-MOD-01: o Coordenador ativa Jiu-Jitsu (vai para "Suas modalidades") e envia um pedido, que aparece em "Pedidos enviados".
test("@critical Coordenador ativa uma modalidade e pede uma nova", async ({ page }) => {
  await page.goto("/entrar");
  await page.getByLabel("E-mail").fill("ana@exemplo.com");
  await page.getByLabel("Senha").fill("segredo123");
  await page.getByRole("button", { name: "Entrar" }).click();
  await expect(page.getByRole("heading", { name: "Bem-vindo ao ctcore" })).toBeVisible();
  const nav = page.getByRole("navigation", { name: "Menu principal" });
  await nav.getByText("Configurações", { exact: true }).click();
  await expect(nav.getByRole("link", { name: "Modalidades" })).toBeVisible();
  await open(page, "/configuracoes/modalidades"); // o link recarrega a página e a sessão simulada se perderia
  await expect(page.getByRole("heading", { name: "Modalidades", level: 1 })).toBeVisible();

  const mine = page.locator("section", { has: page.getByRole("heading", { name: "Suas modalidades" }) });
  const available = page.locator("section", { has: page.getByRole("heading", { name: "Disponíveis para ativar" }) });
  await available.getByRole("button", { name: /Ativar\s*Jiu-Jitsu/ }).click();
  await expect(mine.getByRole("button", { name: /Desativar\s*Jiu-Jitsu/ })).toBeVisible();

  await page.getByRole("button", { name: "Não achei minha modalidade" }).click();
  await page.getByLabel("Nome da modalidade").fill("Sambo");
  await page.getByRole("dialog").getByRole("button", { name: "Enviar pedido" }).click();
  await expect(page.getByText("Pedido enviado")).toBeVisible();
  await expect(page.locator("section", { has: page.getByRole("heading", { name: "Pedidos enviados" }) }).getByText("Sambo")).toBeVisible();
});
