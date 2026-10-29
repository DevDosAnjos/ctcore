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
const EXAM = "00000000-0000-0000-0000-0000000007e2";

async function login(page: Page, email: string) {
  await page.goto("/entrar");
  await page.getByLabel("E-mail").fill(email);
  await page.getByLabel("Senha").fill("segredo123");
  await page.getByRole("button", { name: "Entrar" }).click();
}
async function acceptTerm(page: Page) {
  await login(page, "termo@exemplo.com");
  await page.getByLabel("Li e aceito o termo de matrícula").check();
  await page.getByRole("button", { name: "Aceitar e continuar" }).click();
  await expect(page.getByText("Tudo em dia")).toBeVisible();
}
const goTo = (page: Page, path: string) => page.evaluate(`window.history.pushState({}, '', '${path}'); window.dispatchEvent(new PopStateEvent('popstate'))`);

// RF-POR-06 (#93): aba Avisos do portal a 360 px e a 1024 px, nos dois temas.
for (const viewport of [{ width: 360, height: 740 }, { width: 1024, height: 768 }]) {
  test.describe(`avisos do portal a ${viewport.width} px`, () => {
    test.use({ viewport });

    for (const scheme of ["light", "dark"] as const) {
      test(`@critical aluno vê fixado e não lido, abre pela lista e por ?aviso=, o sino cai e passa no axe (${scheme})`, async ({ page }) => {
        await page.emulateMedia({ colorScheme: scheme });
        await acceptTerm(page);
        await expect(page.getByRole("link", { name: "Avisos, 3 não lidos" })).toBeVisible();
        await page.getByRole("link", { name: "Ver todos os avisos" }).click();
        await expect(page.getByRole("heading", { name: "Avisos", level: 1 })).toBeVisible();

        const list = page.getByRole("list", { name: "Lista de avisos" });
        await expect(list.getByRole("listitem")).toHaveCount(3);
        await expect(list.getByRole("listitem").first().getByText("Fixado")).toBeVisible();
        await expect(list.getByRole("listitem").first().getByText("Não lido")).toBeVisible();
        await expect(list.getByRole("listitem").first()).toContainText("Para: Unidade Centro");
        await expect(list.getByRole("listitem").first()).toContainText("Por Coordenação Fictícia");
        expect(await noOverflow(page)).toBe(true);
        expect(await violations(page)).toEqual([]);

        await list.getByRole("button", { name: /Mudança de horário no sábado/ }).click();
        const dialog = page.getByRole("dialog", { name: "Mudança de horário no sábado" });
        await expect(dialog.getByText(/A aula de sábado será às 10h/)).toBeVisible();
        await expect(dialog.getByText("sem HTML", { exact: false })).toBeVisible();
        await expect(page).toHaveURL(/aviso=/);
        expect(await noOverflow(page)).toBe(true);
        expect(await violations(page)).toEqual([]);
        await page.keyboard.press("Escape");
        await expect(dialog).toHaveCount(0);
        await expect(page).not.toHaveURL(/aviso=/);
        await expect(page.getByRole("link", { name: "Avisos, 2 não lidos" })).toBeVisible();

        await goTo(page, `/portal/avisos?aviso=${EXAM}`);
        await expect(page.getByRole("dialog", { name: "Exame de faixa em dezembro" })).toBeVisible();
        await page.keyboard.press("Escape");
        await expect(page.getByRole("link", { name: "Avisos, 1 não lido" })).toBeVisible();
      });

      test(`@critical responsável filtra, troca de aluno, marca todos como lidos e passa no axe (${scheme})`, async ({ page }) => {
        await page.emulateMedia({ colorScheme: scheme });
        await login(page, "familia@exemplo.com");
        await page.getByRole("link", { name: "Avisos, 4 não lidos" }).click();
        const list = page.getByRole("list", { name: "Lista de avisos" });
        await expect(list.getByRole("listitem")).toHaveCount(3);

        await page.getByRole("button", { name: "Não lidos" }).click();
        await expect(page).toHaveURL(/filtro=nao-lidos/);
        await expect(page.getByRole("button", { name: "Não lidos" })).toHaveAttribute("aria-pressed", "true");
        await page.getByRole("button", { name: "Por modalidade" }).click();
        await expect(page).toHaveURL(/filtro=modalidade/);
        await page.getByLabel("Modalidade").selectOption({ label: "Judô" });
        await expect(list.getByRole("listitem")).toHaveCount(2);
        await page.getByRole("button", { name: "Todos", exact: true }).click();
        await expect(page).not.toHaveURL(/filtro=/);

        await page.getByRole("combobox", { name: "Aluno" }).selectOption(CHILD_TWO);
        await expect(page).toHaveURL(/aluno=/);
        await expect(list.getByRole("button", { name: /Judô Infantil sem aula na quarta/ })).toBeVisible();
        expect(await noOverflow(page)).toBe(true);
        expect(await violations(page)).toEqual([]);

        await page.getByRole("button", { name: "Marcar todos como lidos" }).click();
        await expect(page.getByText("4 avisos marcados como lidos.")).toBeVisible();
        await expect(page.getByRole("button", { name: "Marcar todos como lidos" })).toBeDisabled();
        await expect(list.getByText("Não lido")).toHaveCount(0);
        await expect(page.getByRole("link", { name: /não lidos?$/ })).toHaveCount(0);
        expect(await violations(page)).toEqual([]);
      });
    }

    test("@critical portal reduzido não tem a aba Avisos e /portal/avisos leva ao Início", async ({ page }) => {
      await login(page, "reduzido@exemplo.com");
      await expect(page.getByRole("link", { name: /^Avisos/ })).toHaveCount(0);
      await goTo(page, "/portal/avisos");
      await expect(page).not.toHaveURL(/avisos/);
    });
  });
}
