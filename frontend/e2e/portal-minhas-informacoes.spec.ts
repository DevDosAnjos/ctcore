import { createRequire } from "node:module";
import { expect, test } from "@playwright/test";
import type { Page } from "@playwright/test";

const axePath = createRequire(import.meta.url).resolve("axe-core/axe.min.js");

const violations = async (page: Page) => {
  await page.addScriptTag({ path: axePath });
  return page.evaluate(async () => (await (globalThis as unknown as { axe: { run: () => Promise<{ violations: unknown[] }> } }).axe.run()).violations);
};
const noOverflow = (page: Page) => page.evaluate("document.documentElement.scrollWidth <= window.innerWidth");
const CHILD_TWO = "00000000-0000-0000-0000-000000000703";

async function login(page: Page, email: string) {
  await page.goto("/entrar");
  await page.getByLabel("E-mail").fill(email);
  await page.getByLabel("Senha").fill("segredo123");
  await page.getByRole("button", { name: "Entrar" }).click();
}
const openTab = (page: Page) => page.getByRole("navigation", { name: "Portal" }).getByRole("link", { name: "Minhas informações" }).click();

// RF-POR-07 (#94): Minhas informações a 360 px e a 1024 px, nos dois temas.
for (const [width, height] of [
  [360, 740],
  [1024, 768],
] as const) {
  test.describe(`minhas informações do portal a ${width} px`, () => {
    test.use({ viewport: { width, height } });

    for (const scheme of ["light", "dark"] as const) {
      test(`@critical responsável edita o contato, troca de aluno, revoga o uso de imagem e vê pedidos, conta e ações de dados (${scheme})`, async ({ page }) => {
        await page.emulateMedia({ colorScheme: scheme });
        await login(page, "familia@exemplo.com");
        await openTab(page);
        await expect(page.getByRole("heading", { name: "Dados pessoais" })).toBeVisible();
        await expect(page.getByRole("heading", { name: "Treinos" })).toBeVisible();
        await expect(page.getByRole("heading", { name: "Plano e mensalidade" })).toBeVisible();
        await expect(page.getByRole("heading", { name: "Graduação" })).toBeVisible();
        await expect(page.getByText("Jiu-Jitsu Mensal · R$ 200,00")).toBeVisible();
        expect(await noOverflow(page)).toBe(true);
        expect(await violations(page)).toEqual([]);

        await page.getByRole("button", { name: "Editar contato" }).click();
        const dialog = page.getByRole("dialog", { name: "Editar contato" });
        await dialog.getByLabel("Logradouro").fill("Rua das Flores");
        await dialog.getByRole("button", { name: "Salvar" }).click();
        await expect(dialog).toBeHidden();
        await expect(page.getByText(/Rua das Flores, 2/)).toBeVisible();

        await page.getByRole("combobox", { name: "Aluno" }).selectOption(CHILD_TWO);
        await expect(page).toHaveURL(new RegExp(`aluno=${CHILD_TWO}`));
        await expect(page.locator("dd", { hasText: "Filho Fictício Dois" })).toBeVisible();

        await expect(page.getByText("Não concedido")).toBeVisible();
        await page.getByRole("button", { name: "Conceder uso de imagem" }).click();
        await expect(page.getByText(/^Concedido \(versão/)).toBeVisible();
        await page.getByRole("button", { name: "Revogar uso de imagem" }).click();
        await expect(page.getByText("Não concedido")).toBeVisible();

        await expect(page.getByRole("link", { name: "Fazer um pedido" })).toHaveAttribute("href", "/portal/pedidos");
        await expect(page.getByRole("heading", { name: "Minha conta", level: 2 })).toBeVisible();
        await expect(page.getByRole("button", { name: "Baixar meus dados" })).toBeDisabled();
        await expect(page.getByRole("button", { name: "Excluir minha conta" })).toBeDisabled();
        expect(await noOverflow(page)).toBe(true);
        expect(await violations(page)).toEqual([]);
      });
    }

    test("@critical portal reduzido abre a aba só com dados, contato da unidade, termos e conta", async ({ page }) => {
      await login(page, "reduzido@exemplo.com");
      await openTab(page);
      await expect(page.getByRole("heading", { name: "Dados pessoais" })).toBeVisible();
      await expect(page.getByRole("heading", { name: "Contato da academia" })).toBeVisible();
      await expect(page.getByRole("heading", { name: "Termos aceitos" })).toBeVisible();
      for (const name of ["Treinos", "Plano e mensalidade", "Graduação", "Pedidos à academia"]) await expect(page.getByRole("heading", { name })).toHaveCount(0);
      expect(await noOverflow(page)).toBe(true);
    });
  });
}
