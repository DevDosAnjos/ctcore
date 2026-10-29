import { expect, test, type Page } from "@playwright/test";
import { casos, larguras, temas, type Passo } from "./casos";

// R3: relógio fixo em 29/10/2026 12:00 (America/Sao_Paulo, -03:00).
const AGORA = new Date("2026-10-29T12:00:00-03:00");

async function abrir(page: Page, rota: string, perfil?: string, passos: Passo[] = []) {
  if (perfil) {
    await page.goto("/entrar");
    await page.getByLabel("E-mail").fill(perfil);
    await page.getByLabel("Senha").fill("segredo123");
    await page.getByRole("button", { name: "Entrar" }).click();
    if (perfil.startsWith("duas@")) {
      await page.getByLabel("Código do app").fill("123456"); // conta com as duas etapas ligadas
      await page.getByRole("button", { name: "Confirmar" }).click();
    }
    await expect(page.getByRole("heading", { name: "Bem-vindo ao ctcore" })).toBeVisible();
    // recarregar perderia a sessão simulada: navega pelo histórico
    await page.evaluate(`history.pushState({}, "", ${JSON.stringify(rota)}); dispatchEvent(new PopStateEvent("popstate"))`);
    if (rota !== "/") await expect(page.getByRole("heading", { name: "Bem-vindo ao ctcore" })).toBeHidden();
  } else {
    await page.goto(rota);
  }
  await expect(page.locator("[data-vitrine], h1").first()).toBeVisible();
  for (const p of passos) {
    if ("preencher" in p) await page.getByLabel(p.preencher[0], { exact: true }).fill(p.preencher[1]);
    else await page.getByRole("button", { name: new RegExp(`^${p.clicar}`) }).first().click();
  }
  await page.waitForLoadState("networkidle");
  if (perfil) await expect(page.getByRole("status").filter({ hasText: /Carregando/ })).toHaveCount(0); // esqueletos de carregamento
  await page.waitForTimeout(300); // ponytail: espera fixa p/ o último render; trocar por sinal da tela se oscilar
  await page.evaluate("document.fonts.ready");
}

for (const caso of casos) {
  for (const l of larguras) {
    for (const t of temas) {
      test(`${caso.nome} ${l.nome} ${t.nome}`, async ({ page }) => {
        await page.setViewportSize({ width: l.width, height: l.height });
        await page.emulateMedia({ colorScheme: t.colorScheme, reducedMotion: "reduce" });
        await page.clock.setFixedTime(AGORA);
        await abrir(page, caso.rota, caso.perfil, caso.passos);
        await expect(page).toHaveScreenshot(`${caso.nome}-${l.nome}-${t.nome}.png`, { fullPage: caso.fullPage });
      });
    }
  }
}
