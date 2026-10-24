import { expect, test, type Page } from "@playwright/test";

async function open(page: Page, path: string) {
  await page.evaluate(`history.pushState({}, "", ${JSON.stringify(path)}); dispatchEvent(new PopStateEvent("popstate"))`);
}

// RF-AGE-01: o Coordenador abre Operação › Agenda, vê a grade da semana com aula e "Sem aula", troca de semana e volta por "Hoje".
test("@critical Coordenador abre a Agenda", async ({ page }) => {
  await page.goto("/entrar");
  await page.getByLabel("E-mail").fill("ana@exemplo.com");
  await page.getByLabel("Senha").fill("segredo123");
  await page.getByRole("button", { name: "Entrar" }).click();
  await expect(page.getByRole("heading", { name: "Bem-vindo ao ctcore" })).toBeVisible();
  const nav = page.getByRole("navigation", { name: "Menu principal" });
  await nav.getByText("Operação", { exact: true }).click();
  await expect(nav.getByRole("link", { name: "Agenda" })).toBeVisible();
  await open(page, "/agenda"); // o link recarrega a página e a sessão simulada se perderia
  await expect(page.getByRole("heading", { name: "Agenda", level: 1 })).toBeVisible();
  await expect(page.getByText("Sem aula").first()).toBeVisible();
  await expect(page.getByText("Jiu-Jitsu Adulto").first()).toBeVisible();

  await page.getByRole("button", { name: "Próxima semana" }).click();
  await expect(page.getByText("Sem aula", { exact: true })).toHaveCount(0); // exato: o menu tem "Dias sem aula"
  await expect(page.getByText("Jiu-Jitsu Adulto").first()).toBeVisible();
  await page.getByRole("button", { name: "Hoje" }).click();
  await expect(page.getByText("Sem aula").first()).toBeVisible();

  // RF-AGE-02: clicar numa aula abre o painel dela.
  await page.getByRole("button", { name: "Próxima semana" }).click();
  await page.getByRole("link", { name: /Jiu-Jitsu Adulto/ }).first().click();
  await expect(page.getByRole("heading", { name: "Jiu-Jitsu Adulto", level: 1 })).toBeVisible();
  await expect(page.getByRole("link", { name: "Ver turma" })).toBeVisible();
  await expect(page.getByRole("link", { name: "Avisar turma" })).toBeVisible();

  // RF-AGE-03: edita o horário desta aula e, em seguida, cancela outra.
  await page.getByRole("button", { name: "Editar aula" }).click();
  const edit = page.getByRole("dialog", { name: "Editar aula" });
  await expect(edit.getByRole("option", { name: "João Pereira" })).toBeAttached();
  await edit.getByLabel("Início").fill("06:15");
  await edit.getByLabel("Término").fill("07:15");
  await edit.getByRole("button", { name: "Salvar" }).click();
  await expect(page.getByText(/das 06:15 às 07:15/)).toBeVisible();
  await page.getByRole("link", { name: "Voltar à agenda" }).click();
  await expect(page.getByRole("heading", { name: "Agenda", level: 1 })).toBeVisible();
  await page.getByRole("button", { name: "Próxima semana" }).click();
  await page.getByRole("link", { name: /Jiu-Jitsu Adulto/ }).last().click();
  await page.getByRole("button", { name: "Cancelar aula" }).click();
  const cancel = page.getByRole("dialog", { name: "Cancelar aula" });
  await cancel.getByRole("radio", { name: "Feriado" }).check();
  await cancel.getByRole("button", { name: "Cancelar aula" }).click();
  await expect(page.getByText("Aviso pronto")).toBeVisible();
  await expect(page.getByText("Cancelada").first()).toBeVisible();

  // RF-AGE-04: o Coordenador cria uma aula avulsa e a vê na agenda.
  await page.getByRole("link", { name: "Voltar à agenda" }).click();
  await page.getByRole("button", { name: "Nova aula avulsa" }).click();
  const adhoc = page.getByRole("dialog", { name: "Nova aula avulsa" });
  await adhoc.getByLabel("Título").fill("Seminário de quedas");
  await adhoc.getByLabel("Modalidade").selectOption({ label: "Jiu-Jitsu" });
  await adhoc.getByLabel("Início").fill("06:30");
  await adhoc.getByLabel("Término").fill("07:30");
  await adhoc.getByLabel("Professor").selectOption({ label: "Ana Souza" });
  await adhoc.getByRole("button", { name: "Criar aula" }).click();
  await expect(page.getByRole("dialog")).toHaveCount(0);
  await expect(page.getByText("Seminário de quedas").first()).toBeVisible();
  await expect(page.getByText("Avulsa").first()).toBeVisible();
});
