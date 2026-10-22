import { expect, test, type Page } from "@playwright/test";

const CENTRO = "00000000-0000-0000-0000-000000000021";
const JIU_JITSU = "00000000-0000-0000-0000-000000000103";
const ALUNO_MENOR = "00000000-0000-0000-0000-000000000702";
const TURMA_ADULTO = "00000000-0000-0000-0000-000000000601";
const PLANO_LIVRE = "00000000-0000-0000-0000-000000000602";

async function open(page: Page, path: string) {
  await page.evaluate(`history.pushState({}, "", ${JSON.stringify(path)}); dispatchEvent(new PopStateEvent("popstate"))`);
}

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

// RF-COB-04, RN-COB-12, RN-COB-16 (#75): Administrador registra o pagamento pelo painel da cobrança; o recibo confere na página pública.
test("@critical Registrar pagamento no balcão", async ({ page }) => {
  await page.goto("/entrar");
  await page.getByLabel("E-mail").fill("ana@exemplo.com");
  await page.getByLabel("Senha").fill("segredo123");
  await page.getByRole("button", { name: "Entrar" }).click();
  await expect(page.getByRole("heading", { name: "Bem-vindo ao ctcore" })).toBeVisible();

  await call(page, "PUT", `/modalities/${JIU_JITSU}/activation`, { active: true });
  await call(page, "PUT", `/units/${CENTRO}/modalities/${JIU_JITSU}`, { offered: true });
  const done = (await call(page, "POST", `/students/${ALUNO_MENOR}/enrollment/conclude`, {
    modalities: [{ unitId: CENTRO, modalityId: JIU_JITSU, primaryClassId: TURMA_ADULTO, planId: PLANO_LIVRE, dueDay: 5 }],
    units: [{ unitId: CENTRO }],
  })) as { charges: { id: string }[] };

  await page.setViewportSize({ width: 360, height: 740 });
  await open(page, `/cobrancas?cobranca=${done.charges[0].id}`);
  const panel = page.getByRole("dialog", { name: "Cobrança" });
  await panel.getByRole("button", { name: "Registrar pagamento" }).click();
  const dialog = page.getByRole("dialog", { name: "Registrar pagamento" });
  await expect(dialog.getByLabel("Valor")).toHaveValue("R$ 200,00");
  await dialog.getByLabel("Meio de pagamento").selectOption({ label: "Pix pela chave" });
  await dialog.getByLabel("Observação (opcional)").fill("Pago no balcão");
  await dialog.getByRole("button", { name: "Confirmar pagamento" }).click();
  await expect(dialog.getByText("Recibo número 1")).toBeVisible();
  const code = (await dialog.getByText(/Código de conferência: [A-Z0-9]{16}/).innerText()).split(": ")[1];
  await expect(dialog.getByRole("link", { name: "Baixar recibo" })).toHaveAttribute("href", `/api/v1/public/receipts/${code}/pdf`);
  await dialog.getByRole("button", { name: "Fechar" }).first().click();

  await expect(panel.getByRole("link", { name: "Baixar recibo" })).toBeVisible();
  await expect(panel.getByRole("button", { name: "Registrar pagamento" })).toBeDisabled();
  await expect(panel.getByText("Pago no balcão")).toBeVisible();

  await open(page, `/recibo/${code}`);
  await expect(page.getByRole("heading", { name: "Recibo número 1" })).toBeVisible();
  await expect(page.getByRole("link", { name: "Baixar PDF" })).toBeVisible();
  await expect(await page.evaluate("document.documentElement.scrollWidth <= window.innerWidth")).toBe(true);
  await open(page, "/recibo/INEXISTENTE000000");
  await expect(page.getByText("Recibo não encontrado")).toBeVisible();
});
