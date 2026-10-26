import { createRequire } from "node:module";
import { expect, test } from "@playwright/test";
import type { Page } from "@playwright/test";

const axePath = createRequire(import.meta.url).resolve("axe-core/axe.min.js");
const CENTRO = "00000000-0000-0000-0000-000000000021";
const JIU_JITSU = "00000000-0000-0000-0000-000000000103";
const ANA = "00000000-0000-0000-0000-000000000501";
const ALUNO_ADULTO = "00000000-0000-0000-0000-000000000701";
const TURMA_ADULTO = "00000000-0000-0000-0000-000000000601";
const PLANO_LIVRE = "00000000-0000-0000-0000-000000000602";

const violations = async (page: Page) => {
  await page.addScriptTag({ path: axePath });
  return page.evaluate(async () => (await (globalThis as unknown as { axe: { run: () => Promise<{ violations: unknown[] }> } }).axe.run()).violations);
};
const noOverflow = (page: Page) => page.evaluate("document.documentElement.scrollWidth <= window.innerWidth");

async function open(page: Page, path: string) {
  await page.evaluate(`history.pushState({}, "", ${JSON.stringify(path)}); dispatchEvent(new PopStateEvent("popstate"))`);
}

// API de teste: as mesmas rotas do contrato, no navegador (o simulado responde), para montar o cenário.
async function call(page: Page, method: string, path: string, body?: unknown) {
  const result = await page.evaluate(
    async ([m, p, b]) => {
      const response = await fetch(`/api/v1${p}`, { method: m as string, headers: { "Content-Type": "application/json" }, body: b ? JSON.stringify(b) : undefined });
      return { status: response.status, body: await response.json().catch(() => null) };
    },
    [method, path, body] as const,
  );
  expect(result.status, `${method} ${path}`).toBeLessThan(300);
  return result.body;
}

// O simulado guarda o estado na página: a mesma conta tem vínculo de gestão e de portal e troca de um para o outro sem recarregar.
async function chooseLink(page: Page, name: RegExp) {
  const modal = page.getByRole("dialog", { name: "Escolher organização e área" });
  if (!(await modal.waitFor({ timeout: 3000 }).then(() => true, () => false))) await page.getByRole("button", { name: "Trocar organização ou área" }).click();
  await modal.getByRole("button", { name }).click();
  await expect(modal).toBeHidden();
}

// RF-MAT-04 (#86): o aluno pede pelo portal; a gestão recusa um pedido e aprova outro, que abre a troca já preenchida.
test.describe("pedidos do portal a 360 px", () => {
  test.use({ viewport: { width: 360, height: 740 } });

  for (const scheme of ["light", "dark"] as const) {
    test(`@critical aluno pede, gestão recusa e aprova (${scheme})`, async ({ page }) => {
      await page.emulateMedia({ colorScheme: scheme });
      await page.goto("/entrar");
      await page.getByLabel("E-mail").fill("varios@exemplo.com");
      await page.getByLabel("Senha").fill("segredo123");
      await page.getByRole("button", { name: "Entrar" }).click();
      await chooseLink(page, /Academia Alfa Gestão \(Administrador\)/);
      await expect(page.getByRole("heading", { name: "Bem-vindo ao ctcore" })).toBeVisible();
      await call(page, "POST", "/classes", { unitId: CENTRO, name: "Jiu-Jitsu Noite", modalityId: JIU_JITSU, ageRangeId: null, schedule: [{ weekday: 2, startsAt: "19:00", endsAt: "20:00" }], capacity: null, headTeacherId: ANA, assistantIds: [], groupLink: null });
      await call(page, "POST", `/students/${ALUNO_ADULTO}/modalities`, { modalities: [{ unitId: CENTRO, modalityId: JIU_JITSU, primaryClassId: TURMA_ADULTO, otherClassIds: [], planId: PLANO_LIVRE, dueDay: 5 }] });

      // Aluno: aceita o termo, abre os pedidos e faz dois.
      await chooseLink(page, /Academia Alfa Portal \(Aluno\)/);
      await expect(page).toHaveURL(/\/portal\/termo$/);
      await page.getByLabel("Li e aceito o termo de matrícula").check();
      await page.getByRole("button", { name: "Aceitar e continuar" }).click();
      await page.getByRole("link", { name: "Meus pedidos" }).click();
      await expect(page.getByRole("heading", { name: "Meus pedidos", level: 1 })).toBeVisible();
      await expect(page.getByText("Nenhum pedido ainda")).toBeVisible();
      expect(await noOverflow(page)).toBe(true);
      expect(await violations(page)).toEqual([]);

      await page.getByRole("button", { name: "Pedir troca de turma" }).click();
      const dialog = page.getByRole("dialog", { name: "Pedir troca de turma" });
      await dialog.getByLabel("Turma desejada").selectOption({ label: "Jiu-Jitsu Noite" });
      await dialog.getByLabel("Mensagem (opcional)").fill("Troco por causa do trabalho");
      expect(await noOverflow(page)).toBe(true);
      expect(await violations(page)).toEqual([]);
      await dialog.getByRole("button", { name: "Enviar pedido" }).click();
      await expect(dialog).toHaveCount(0);

      await page.getByRole("button", { name: "Solicitar encerramento" }).click();
      const closure = page.getByRole("dialog", { name: "Solicitar encerramento" });
      await closure.getByLabel("Motivo").selectOption("FINANCIAL");
      await closure.getByRole("button", { name: "Solicitar encerramento" }).click();
      await expect(page.getByText(/Encerramento solicitado em/)).toBeVisible();
      await expect(page.getByText("Judô: para a turma Jiu-Jitsu Noite").or(page.getByText(/para a turma Jiu-Jitsu Noite/))).toBeVisible();

      // Gestão: recusa o encerramento com mensagem e aprova a troca de turma.
      await chooseLink(page, /Academia Alfa Gestão \(Administrador\)/);
      await open(page, "/pedidos");
      await expect(page.getByRole("heading", { name: "Pedidos do portal", level: 1 })).toBeVisible();
      await expect(page.getByText("Troca de turma").first()).toBeVisible();
      expect(await noOverflow(page)).toBe(true);
      expect(await violations(page)).toEqual([]);

      await page.getByRole("button", { name: /^Recusar pedido de/ }).last().click();
      const refuse = page.getByRole("dialog", { name: "Recusar pedido" });
      await refuse.getByLabel("Mensagem ao aluno").fill("Vamos conversar antes de encerrar.");
      expect(await violations(page)).toEqual([]);
      await refuse.getByRole("button", { name: "Recusar pedido" }).click();
      await expect(refuse).toHaveCount(0);

      await page.getByRole("button", { name: /^Aprovar pedido de/ }).click();
      await page.getByRole("dialog", { name: "Aprovar pedido" }).getByRole("button", { name: "Aprovar e continuar" }).click();
      await expect(page).toHaveURL(new RegExp(`/alunos/${ALUNO_ADULTO}\\?pedido=`));
      const change = page.getByRole("dialog", { name: /Trocar turma/ });
      await expect(change.getByLabel("Turma principal")).toHaveValue(/.+/);
      await expect(change.getByLabel("Turma principal").locator("option:checked")).toHaveText("Jiu-Jitsu Noite");
    });
  }
});
