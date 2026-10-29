import { defineConfig, devices } from "@playwright/test";

// D1: config própria, fora do `playwright test` do e2e. Referências só do container do CI (D2).
export default defineConfig({
  testDir: "./visual",
  testMatch: "**/*.visual.ts",
  snapshotDir: "./visual/referencias",
  snapshotPathTemplate: "{snapshotDir}/{arg}{ext}",
  forbidOnly: !!process.env.CI,
  retries: 0,
  reporter: process.env.CI ? [["list"], ["html", { open: "never" }]] : "list",
  expect: { toHaveScreenshot: { animations: "disabled", caret: "hide", maxDiffPixelRatio: 0.001 } },
  use: { baseURL: "http://localhost:5174", locale: "pt-BR", timezoneId: "America/Sao_Paulo", ...devices["Desktop Chrome"] },
  projects: [{ name: "visual" }],
  webServer: {
    command: "npm run dev -- --port 5174 --strictPort",
    url: "http://localhost:5174",
    reuseExistingServer: !process.env.CI,
    timeout: 120000,
  },
});
