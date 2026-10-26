import { test as base, expect, type Page } from "@playwright/test";

export type MswOverride = { method: "get" | "post" | "put" | "patch" | "delete"; path: string; status: number; body: unknown };

// Fixture `msw`: troca a resposta de um endpoint do MSW só durante o teste (worker.use no navegador) e desfaz ao fim (resetHandlers).
export const test = base.extend<{ msw: { override: (spec: MswOverride) => Promise<void> } }>({
  msw: async ({ page }, provide) => {
    await provide({
      override: async (spec) => {
        // Vale na carga seguinte (fila lida na partida do MSW) e, se a página já carregou, na hora.
        await page.addInitScript((s) => ((globalThis as unknown as { __mswQueue?: unknown[] }).__mswQueue ??= []).push(s), spec);
        await applyNow(page, spec);
      },
    });
    await page.evaluate("window.__mswReset?.()").catch(() => undefined);
  },
});

async function applyNow(page: Page, spec: MswOverride) {
  if (page.url() === "about:blank") return;
  await page.waitForFunction("window.__mswOverride");
  await page.evaluate((s) => (globalThis as unknown as { __mswOverride?: (x: unknown) => void }).__mswOverride?.(s), spec);
}

export { expect };
