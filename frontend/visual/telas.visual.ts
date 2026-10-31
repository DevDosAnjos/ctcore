import { expect, test, type Page } from "@playwright/test";
import { casos, larguras, temas, type Passo } from "./casos";

// R3: relógio fixo em 29/10/2026 12:00 (America/Sao_Paulo, -03:00).
const AGORA = new Date("2026-10-29T12:00:00-03:00");

async function abrir(page: Page, rota: string, perfil?: string, passos: Passo[] = [], matricula = false) {
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
    if (matricula) {
      // o MSW vive na página: a Coordenação matricula o aluno fictício na turma 601, liga a ficha do Professor e o login volta ao perfil do caso
      await page.evaluate(async (email) => {
        const post = (url: string, body: unknown) => fetch(`/api/v1${url}`, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body) });
        await post("/auth/login", { email: "ana@exemplo.com", password: "segredo123" });
        const r = await post("/students/00000000-0000-0000-0000-000000000701/enrollment/conclude", {
          modalities: [{ unitId: "00000000-0000-0000-0000-000000000021", modalityId: "00000000-0000-0000-0000-000000000103", primaryClassId: "00000000-0000-0000-0000-000000000601", planId: "00000000-0000-0000-0000-000000000602", dueDay: 5 }],
          units: [],
        });
        if (!r.ok) throw new Error(`matrícula do caso: ${r.status} ${await r.text()}`);
        // "ver ficha do aluno" ligada na unidade da turma (no padrão ela vem desligada)
        const unit = (await (await fetch("/api/v1/units/00000000-0000-0000-0000-000000000021")).json()) as { version: number; settings: { teacherPermissions: object } };
        const u = await fetch("/api/v1/units/00000000-0000-0000-0000-000000000021", {
          method: "PUT", headers: { "Content-Type": "application/json", "If-Match": String(unit.version) },
          body: JSON.stringify({ ...unit, settings: { ...unit.settings, teacherPermissions: { ...unit.settings.teacherPermissions, studentRecord: true } } }),
        });
        if (!u.ok) throw new Error(`permissão do caso: ${u.status} ${await u.text()}`);
        await post("/auth/login", { email, password: "segredo123" });
      }, perfil);
    }
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
        await abrir(page, caso.rota, caso.perfil, caso.passos, caso.matricula);
        await expect(page).toHaveScreenshot(`${caso.nome}-${l.nome}-${t.nome}.png`, { fullPage: caso.fullPage });
      });
    }
  }
}
