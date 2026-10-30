import { expect, test, type Page } from "@playwright/test";

async function open(page: Page, path: string) {
  await page.evaluate(`history.pushState({}, "", ${JSON.stringify(path)}); dispatchEvent(new PopStateEvent("popstate"))`);
}

// RF-COM-02: o Coordenador cancela uma aula, abre "Avisar turma", envia agora e vê o comunicado Enviado na lista.
test("@critical Cancelamento de aula com aviso à turma", async ({ page }) => {
  await page.goto("/entrar");
  await page.getByLabel("E-mail").fill("ana@exemplo.com");
  await page.getByLabel("Senha").fill("segredo123");
  await page.getByRole("button", { name: "Entrar" }).click();
  await expect(page.getByRole("heading", { name: "Bem-vindo ao ctcore" })).toBeVisible();
  await open(page, "/agenda"); // o link do menu recarrega a página e a sessão simulada se perderia
  await expect(page.getByRole("heading", { name: "Agenda", level: 1 })).toBeVisible();
  await page.getByRole("button", { name: "Próxima semana" }).click();
  await page.getByRole("link", { name: /Jiu-Jitsu Adulto/ }).last().click();
  await page.getByRole("button", { name: "Cancelar aula" }).click();
  const cancel = page.getByRole("dialog", { name: "Cancelar aula" });
  await cancel.getByRole("radio", { name: "Feriado" }).check();
  await cancel.getByRole("button", { name: "Cancelar aula" }).click();
  await expect(page.getByText("Aviso pronto")).toBeVisible();

  await page.getByRole("link", { name: "Avisar turma" }).click();
  await expect(page.getByRole("heading", { name: "Novo comunicado", level: 1 })).toBeVisible();
  await expect(page.getByLabel("Destino")).toHaveValue(/^CLASS:/);
  await expect(page.getByLabel("Tipo")).toHaveValue("CANCELED_CLASS");
  await expect(page.getByLabel("Título")).toHaveValue("Aula cancelada");
  await expect(page.getByLabel("Texto")).toHaveValue(/Jiu-Jitsu Adulto do dia \d{2}\/\d{2}\/\d{4}, às \d{2}:\d{2}, foi cancelada/);
  await expect(page.getByText(/^Este aviso vai para \d+ pessoas$/)).toBeVisible();
  await page.getByRole("button", { name: "Enviar comunicado" }).click();

  await expect(page.getByRole("heading", { name: "Comunicados", level: 1 })).toBeVisible();
  await expect(page.getByText("Comunicado enviado.")).toBeVisible();
  const row = page.getByRole("row").filter({ hasText: "Aula cancelada" }).filter({ hasText: "Turma Jiu-Jitsu Adulto" }).first();
  await expect(row).toContainText("Enviado");
});
