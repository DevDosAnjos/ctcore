import { expect, test } from "@playwright/test";

// #448: reprova se o navegador voltar a mostrar "dd/mm/yyyy" e "Choose File" (o headless shell ignora o locale).
test("textos nativos em pt-BR", async ({ page }) => {
  await page.setContent(
    `<body style="margin:16px;font:16px sans-serif"><input type="date" style="display:block;margin-bottom:12px"><input type="file"></body>`,
  );
  await expect(page).toHaveScreenshot("idioma-pt-br.png", { clip: { x: 0, y: 0, width: 400, height: 100 } });
});
