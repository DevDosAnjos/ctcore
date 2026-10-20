import { expect, test, type Page } from "@playwright/test";

async function open(page: Page, path: string) {
  await page.evaluate(`history.pushState({}, "", ${JSON.stringify(path)}); dispatchEvent(new PopStateEvent("popstate"))`);
}

const parse = (br: string) => {
  const [d, m, y] = br.split("/").map(Number);
  return new Date(y, m - 1, d, 12);
};

// RF-AGE-05: o Coordenador confirma um feriado sugerido (de segunda a quinta, onde há aula no simulado) e a Agenda mostra "Sem aula" e as aulas canceladas.
test("@critical Coordenador confirma um feriado sugerido e a Agenda mostra Sem aula", async ({ page }) => {
  await page.goto("/entrar");
  await page.getByLabel("E-mail").fill("ana@exemplo.com");
  await page.getByLabel("Senha").fill("segredo123");
  await page.getByRole("button", { name: "Entrar" }).click();
  await expect(page.getByRole("heading", { name: "Bem-vindo ao ctcore" })).toBeVisible();
  const nav = page.getByRole("navigation", { name: "Menu principal" });
  await nav.getByText("Operação", { exact: true }).click();
  await expect(nav.getByRole("link", { name: "Dias sem aula" })).toBeVisible();
  await open(page, "/dias-sem-aula"); // o link recarrega a página e a sessão simulada se perderia
  await expect(page.getByRole("heading", { name: "Dias sem aula", level: 1 })).toBeVisible();
  await expect(page.getByText("Nenhum dia sem aula")).toBeVisible();

  const items = await page.getByRole("button", { name: /^Confirmar .+, \d{2}\/\d{2}\/\d{4}$/ }).evaluateAll((els) => els.map((e) => e.getAttribute("aria-label") ?? ""));
  const label = items.find((l) => {
    const [d, m, y] = l.slice(-10).split("/").map(Number);
    const weekday = new Date(y, m - 1, d, 12).getDay();
    return weekday >= 1 && weekday <= 4;
  })!;
  expect(label).toBeTruthy();
  await page.getByRole("button", { name: label, exact: true }).click();
  const dialog = page.getByRole("dialog", { name: "Novo dia sem aula" });
  await expect(dialog.getByText("A mensalidade não muda.")).toBeVisible();
  await expect(dialog.getByText(/aulas?$/).first()).toBeVisible(); // prévia das aulas que serão canceladas
  await dialog.getByRole("button", { name: "Salvar" }).click();
  await expect(dialog).toBeHidden();
  await expect(page.getByText("Origem: Feriado")).toBeVisible();
  await expect(page.getByText("Aviso: Ainda não avisado")).toBeVisible();

  // A Agenda: vai até a semana do feriado.
  const date = parse(label.slice(-10));
  const today = new Date();
  today.setHours(12, 0, 0, 0);
  const monday = new Date(today);
  monday.setDate(today.getDate() - ((today.getDay() + 6) % 7));
  const weeks = Math.floor((date.getTime() - monday.getTime()) / (7 * 86400000));
  await open(page, "/agenda");
  await expect(page.getByRole("heading", { name: "Agenda", level: 1 })).toBeVisible();
  for (let i = 0; i < weeks; i++) await page.getByRole("button", { name: "Próxima semana" }).click();
  await expect(page.getByText("Sem aula").first()).toBeVisible();
  await expect(page.getByText("Cancelada").first()).toBeVisible();
});
