import { expect, test } from "@playwright/test";

// A sessão simulada vive na memória da página: a navegação é feita pelo roteador, sem recarregar (ver unidades.spec.ts).
// RF-ACE-06 / RN-ORG-07: convidar, tentar desativar o último Administrador da unidade, nomear a si mesmo e desativar.
test("@critical Coordenador convida um Administrador e desativa outro", async ({ page }) => {
  await page.goto("/entrar");
  await page.getByLabel("E-mail").fill("ana@exemplo.com");
  await page.getByLabel("Senha").fill("segredo123");
  await page.getByRole("button", { name: "Entrar" }).click();
  await expect(page.getByRole("heading", { name: "Bem-vindo ao ctcore" })).toBeVisible();
  await page.evaluate(`history.pushState({}, "", "/configuracoes/usuarios"); dispatchEvent(new PopStateEvent("popstate"))`);
  await expect(page.getByRole("heading", { name: "Usuários da gestão", level: 1 })).toBeVisible();

  await page.getByRole("button", { name: "Convidar" }).click();
  const invite = page.getByRole("dialog");
  await invite.getByLabel("Nome").fill("Fabio");
  await invite.getByLabel("E-mail").fill("fabio@exemplo.com");
  await invite.getByRole("checkbox", { name: "Unidade Centro" }).check();
  await invite.getByRole("button", { name: "Enviar convite" }).click();
  const fabio = page.getByRole("row").filter({ hasText: "fabio@exemplo.com" });
  await expect(fabio.getByText("Convite enviado")).toBeVisible();

  await page.getByRole("row").filter({ hasText: "Bruno" }).getByRole("button", { name: /Desativar/ }).click();
  const dialog = page.getByRole("dialog");
  await dialog.getByRole("button", { name: "Desativar" }).click();
  await expect(dialog.getByText("Estas unidades ficariam sem Administrador ativo:")).toBeVisible();
  await dialog.getByRole("button", { name: "Nomear a mim mesmo" }).click();
  await expect(page.getByRole("status").filter({ hasText: "Usuário desativado." })).toBeVisible();
  await expect(page.getByRole("row").filter({ hasText: "Bruno" })).toHaveCount(0);
});
