import { expect, test, type Page } from "@playwright/test";

async function open(page: Page, path: string) {
  await page.evaluate(`history.pushState({}, "", ${JSON.stringify(path)}); dispatchEvent(new PopStateEvent("popstate"))`);
}

// RF-PRO-01: o Coordenador abre Pessoas › Professores, vê a lista e filtra pelo nome.
test("@critical Coordenador filtra a lista de professores", async ({ page }) => {
  await page.goto("/entrar");
  await page.getByLabel("E-mail").fill("ana@exemplo.com");
  await page.getByLabel("Senha").fill("segredo123");
  await page.getByRole("button", { name: "Entrar" }).click();
  await expect(page.getByRole("heading", { name: "Bem-vindo ao ctcore" })).toBeVisible();
  const nav = page.getByRole("navigation", { name: "Menu principal" });
  await nav.getByText("Pessoas", { exact: true }).click();
  await expect(nav.getByRole("link", { name: "Professores" })).toBeVisible();
  await open(page, "/professores"); // o link recarrega a página e a sessão simulada se perderia
  await expect(page.getByRole("heading", { name: "Professores", level: 1 })).toBeVisible();
  await expect(page.getByText("Ana Souza")).toBeVisible();
  await expect(page.getByText("João Pereira")).toBeVisible();

  await page.getByRole("searchbox", { name: "Buscar" }).fill("joao");
  await expect(page.getByText("João Pereira")).toBeVisible();
  await expect(page.getByText("Ana Souza")).toHaveCount(0);
});

// RF-PRO-02 / RN-PRO-02: o Coordenador abre o painel de um professor com e-mail e sem acesso, dá acesso e vê o convite enviado.
test("@critical Coordenador dá acesso a um professor", async ({ page }) => {
  await page.goto("/entrar");
  await page.getByLabel("E-mail").fill("ana@exemplo.com");
  await page.getByLabel("Senha").fill("segredo123");
  await page.getByRole("button", { name: "Entrar" }).click();
  await expect(page.getByRole("heading", { name: "Bem-vindo ao ctcore" })).toBeVisible();
  await open(page, "/professores");
  await page.getByRole("link", { name: "João Pereira" }).click(); // pelo roteador, sem recarregar
  await expect(page.getByRole("heading", { name: "João Pereira", level: 1 })).toBeVisible();
  await expect(page.getByText("Sem acesso", { exact: true })).toBeVisible();

  await page.getByRole("button", { name: "Dar acesso" }).click();
  const dialog = page.getByRole("dialog");
  await dialog.getByRole("button", { name: "Enviar convite" }).click();
  await expect(page.getByText("Convite enviado", { exact: true })).toBeVisible();
  await expect(page.getByRole("button", { name: "Reenviar convite" })).toBeVisible();
});

// RF-PRO-03: o Coordenador abre Pessoas › Professores, cadastra um professor com unidade e modalidade e vê o painel do professor novo.
test("@critical Coordenador cadastra um professor", async ({ page }) => {
  await page.goto("/entrar");
  await page.getByLabel("E-mail").fill("ana@exemplo.com");
  await page.getByLabel("Senha").fill("segredo123");
  await page.getByRole("button", { name: "Entrar" }).click();
  await expect(page.getByRole("heading", { name: "Bem-vindo ao ctcore" })).toBeVisible();
  const nav = page.getByRole("navigation", { name: "Menu principal" });
  await nav.getByText("Pessoas", { exact: true }).click();
  await expect(nav.getByRole("link", { name: "Professores" })).toBeVisible();
  await open(page, "/professores");
  await expect(page.getByRole("heading", { name: "Professores", level: 1 })).toBeVisible();

  await page.getByRole("button", { name: "Novo professor" }).click();
  const dialog = page.getByRole("dialog", { name: "Novo professor" });
  await dialog.getByLabel("Nome").fill("Paula Nogueira");
  await dialog.getByRole("checkbox", { name: "Unidade Centro" }).check();
  await dialog.getByRole("checkbox", { name: "Judô" }).check();
  await dialog.getByRole("button", { name: "Salvar" }).click();

  await expect(page.getByRole("heading", { name: "Paula Nogueira", level: 1 })).toBeVisible();
  await expect(page.getByText("Judô", { exact: true })).toBeVisible();
});

// RF-PRO-04 / RN-PRO-05: o Coordenador abre o painel de um professor ativo, inativa e vê a situação "Inativo".
test("@critical Coordenador inativa um professor", async ({ page }) => {
  await page.goto("/entrar");
  await page.getByLabel("E-mail").fill("ana@exemplo.com");
  await page.getByLabel("Senha").fill("segredo123");
  await page.getByRole("button", { name: "Entrar" }).click();
  await expect(page.getByRole("heading", { name: "Bem-vindo ao ctcore" })).toBeVisible();
  await open(page, "/professores");
  await page.getByRole("link", { name: "João Pereira" }).click();
  await expect(page.getByRole("heading", { name: "João Pereira", level: 1 })).toBeVisible();

  await page.getByRole("button", { name: "Inativar" }).click();
  const dialog = page.getByRole("dialog", { name: "Inativar João Pereira" });
  await expect(dialog.getByText("Nenhuma turma ficará sem titular.")).toBeVisible();
  await dialog.getByRole("button", { name: "Inativar" }).click();
  await expect(page.getByText("Inativo", { exact: true })).toBeVisible();
  await expect(page.getByRole("dialog")).toHaveCount(0);
});
