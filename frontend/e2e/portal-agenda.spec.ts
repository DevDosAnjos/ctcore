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

// RF-POR-04 (#91): agenda do portal em lista do dia a 360 px e em grade a 1024 px, nos dois temas.
test.describe("agenda do portal a 360 px", () => {
  test.use({ viewport: { width: 360, height: 740 } });

  for (const scheme of ["light", "dark"] as const) {
    test(`@critical responsável vê a aula alterada, a cancelada, o dia sem aula, troca de semana, de escopo e de aluno, e passa no axe (${scheme})`, async ({ page }) => {
      await page.emulateMedia({ colorScheme: scheme });
      await login(page, "familia@exemplo.com");
      await page.getByRole("link", { name: "Agenda", exact: true }).click();
      await expect(page.getByRole("heading", { name: "Agenda", level: 1 })).toBeVisible();

      await page.getByRole("tab", { name: /^Ter/ }).click();
      const panel = page.getByRole("tabpanel");
      await expect(panel.getByText("Alterada")).toBeVisible();
      await expect(panel.getByText(/^Era em /)).toBeVisible();
      await expect(panel.getByRole("link", { name: /Ver aviso/ })).toHaveAttribute("href", /\/portal\/avisos\?aviso=/);
      await page.getByRole("tab", { name: /^Qua/ }).click();
      await expect(panel.getByText("Sem aula")).toBeVisible();
      await expect(panel.getByText("Reforma do tatame")).toBeVisible();
      await page.getByRole("tab", { name: /^Qui/ }).click();
      await expect(panel.getByText("Cancelada")).toBeVisible();
      await expect(panel.getByText("Motivo: Feriado")).toBeVisible();
      expect(await noOverflow(page)).toBe(true);
      expect(await violations(page)).toEqual([]);

      await page.getByRole("button", { name: "Próxima semana" }).click();
      await expect(page).toHaveURL(/semana=\d{4}-\d{2}-\d{2}/);
      await page.getByRole("button", { name: "Só minha turma" }).click();
      await expect(page).toHaveURL(/escopo=MAIN/);
      await expect(page).toHaveURL(/semana=/);
      await page.getByRole("tab", { name: /^Qui/ }).click();
      await expect(page.getByRole("tabpanel").getByText("Cancelada")).toHaveCount(0);
      await page.getByRole("button", { name: "Hoje" }).click();
      await expect(page).not.toHaveURL(/semana=/);

      await page.getByRole("combobox", { name: "Aluno" }).selectOption(CHILD_TWO);
      await expect(page).toHaveURL(/aluno=/);
      await page.getByRole("tab", { name: /^Seg/ }).click();
      await expect(page.getByRole("tabpanel").getByText("Judô Infantil")).toBeVisible();
      expect(await noOverflow(page)).toBe(true);
      expect(await violations(page)).toEqual([]);
    });
  }

  test("@critical portal reduzido não tem a aba Agenda e /portal/agenda leva ao Início", async ({ page }) => {
    await login(page, "reduzido@exemplo.com");
    await expect(page.getByRole("link", { name: "Agenda", exact: true })).toHaveCount(0);
    await page.evaluate("window.history.pushState({}, '', '/portal/agenda'); window.dispatchEvent(new PopStateEvent('popstate'))");
    await expect(page).not.toHaveURL(/agenda/);
  });
});

test.describe("agenda do portal a 1024 px", () => {
  test.use({ viewport: { width: 1024, height: 768 } });

  for (const scheme of ["light", "dark"] as const) {
    test(`@critical grade da semana com 7 colunas e dia sem aula, sem rolagem horizontal, no axe (${scheme})`, async ({ page }) => {
      await page.emulateMedia({ colorScheme: scheme });
      await login(page, "familia@exemplo.com");
      await page.getByRole("link", { name: "Agenda", exact: true }).click();
      const grid = page.getByRole("table", { name: "Aulas da semana" });
      await expect(grid).toBeVisible();
      await expect(grid.getByRole("columnheader")).toHaveCount(8);
      await expect(grid.getByText("Sem aula")).toBeVisible();
      await expect(grid.getByText("Jiu-Jitsu Adulto")).toBeVisible();
      await expect(page.getByRole("tablist")).toHaveCount(0);
      expect(await noOverflow(page)).toBe(true);
      expect(await violations(page)).toEqual([]);
    });
  }
});
