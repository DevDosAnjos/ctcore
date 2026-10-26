import { expect, test } from "./support/msw";
import type { Page } from "@playwright/test";

const noOverflow = async (page: Page) => expect(await page.evaluate("document.documentElement.scrollWidth <= window.innerWidth")).toBe(true);
const unreadCount = async (page: Page) => {
  await page.waitForFunction("window.__mswOverride"); // MSW já de pé
  return page.evaluate(async () => ((await (await fetch("/api/v1/notifications/unread-count")).json()) as { count: number }).count);
};

async function login(page: Page) {
  await page.getByLabel("E-mail").fill("ana@exemplo.com");
  await page.getByLabel("Senha").fill("segredo123");
  await page.getByRole("button", { name: "Entrar" }).click();
  await expect(page.getByRole("heading", { name: "Bem-vindo ao ctcore" })).toBeVisible();
}

// #368 CA1: a sobrescrita vale só no teste que a pediu; o teste seguinte vê o padrão do handler.
test("isolamento: o teste sobrescreve o contador", async ({ page, msw }) => {
  await page.goto("/entrar");
  await msw.override({ method: "get", path: "/notifications/unread-count", status: 200, body: { count: 4321 } });
  await login(page);
  expect(await unreadCount(page)).toBe(4321);
});

test("isolamento: o teste seguinte vê o valor padrão", async ({ page }) => {
  await page.goto("/entrar");
  await login(page);
  expect(await unreadCount(page)).not.toBe(4321);
});

// #368 CA2: registrada antes do goto, a primeira resposta já é a sobrescrita.
test("antes da carga: a primeira leitura do contador já é a sobrescrita", async ({ page, msw }) => {
  await msw.override({ method: "get", path: "/notifications/unread-count", status: 200, body: { count: 777 } });
  await page.goto("/entrar");
  await expect.poll(() => unreadCount(page)).toBe(777);
});

// #362 CA2 a CA4, a 360 px.
test.describe("barra superior a 360 px", () => {
  test.use({ viewport: { width: 360, height: 740 } });

  test("sino com 100 ou mais mostra 99+", async ({ page, msw }) => {
    await msw.override({ method: "get", path: "/notifications/unread-count", status: 200, body: { count: 150 } });
    await page.goto("/entrar");
    await login(page);
    await expect(page.getByRole("button", { name: /99\+/ })).toBeVisible();
    await noOverflow(page);
  });

  test("nome de unidade longo", async ({ page, msw }) => {
    const nome = (c: string) => c.repeat(80);
    await msw.override({
      method: "get",
      path: "/units/reachable",
      status: 200,
      body: { allUnits: true, units: [{ id: "00000000-0000-0000-0000-000000000021", name: nome("A"), shortName: null }, { id: "00000000-0000-0000-0000-000000000022", name: nome("B"), shortName: null }] },
    });
    await page.goto("/entrar");
    await login(page);
    await expect(page.getByLabel("Unidade ativa")).toBeVisible();
    await noOverflow(page);
  });

  test("erro ao carregar as unidades", async ({ page, msw }) => {
    await msw.override({ method: "get", path: "/units/reachable", status: 500, body: { code: "INTERNAL", message: "Erro." } });
    await page.goto("/entrar");
    await login(page);
    await expect(page.getByRole("alert").filter({ hasText: "Não foi possível carregar as unidades." })).toBeVisible();
    await noOverflow(page);
  });
});
