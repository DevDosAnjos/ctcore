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

// RF-MOD-02: o Coordenador ativa o Jiu-Jitsu, liga a graduação, renomeia um nível, salva e reabre vendo a mudança.
test("@critical Coordenador liga a graduação do Jiu-Jitsu e edita um nível", async ({ page }) => {
  await page.goto("/entrar");
  await page.getByLabel("E-mail").fill("ana@exemplo.com");
  await page.getByLabel("Senha").fill("segredo123");
  await page.getByRole("button", { name: "Entrar" }).click();
  await expect(page.getByRole("heading", { name: "Bem-vindo ao ctcore" })).toBeVisible();
  await open(page, "/configuracoes/modalidades");
  await expect(page.getByRole("heading", { name: "Modalidades", level: 1 })).toBeVisible();

  const available = page.locator("section", { has: page.getByRole("heading", { name: "Disponíveis para ativar" }) });
  await available.getByRole("button", { name: /Ativar\s*Jiu-Jitsu/ }).click();
  await page.getByRole("button", { name: /Graduação\s*Jiu-Jitsu/ }).click();
  const dialog = page.getByRole("dialog", { name: "Sistema de graduação" });
  await dialog.getByRole("switch", { name: "Graduação ligada" }).check();
  await dialog.getByLabel("Nome do nível 2").fill("Azul royal");
  await dialog.getByRole("button", { name: "Salvar" }).click();
  await expect(page.getByText("Sistema de graduação salvo")).toBeVisible();

  await page.getByRole("button", { name: /Graduação\s*Jiu-Jitsu/ }).click();
  const reopened = page.getByRole("dialog", { name: "Sistema de graduação" });
  await expect(reopened.getByLabel("Nome do nível 2")).toHaveValue("Azul royal");
  await expect(reopened.getByRole("switch", { name: "Graduação ligada" })).toBeChecked();
});
