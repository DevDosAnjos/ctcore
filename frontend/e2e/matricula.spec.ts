import { expect, test, type Page } from "@playwright/test";

const CENTRO = "00000000-0000-0000-0000-000000000021";
const JIU_JITSU = "00000000-0000-0000-0000-000000000103";
const ANA = "00000000-0000-0000-0000-000000000501";
const ALUNO_MENOR = "00000000-0000-0000-0000-000000000702";
const TURMA_ADULTO = "00000000-0000-0000-0000-000000000601";
const PLANO_LIVRE = "00000000-0000-0000-0000-000000000602";

async function open(page: Page, path: string) {
  await page.evaluate(`history.pushState({}, "", ${JSON.stringify(path)}); dispatchEvent(new PopStateEvent("popstate"))`);
}

// API de teste: as mesmas rotas do contrato, no navegador (o simulado responde), para montar o cenário sem passar pelas telas que não são desta etapa.
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

const schedule = (weekday: number) => [{ weekday, startsAt: "19:00", endsAt: "20:00" }];
const classBody = (unitId: string, name: string, weekday: number) => ({ unitId, name, modalityId: JIU_JITSU, ageRangeId: null, schedule: schedule(weekday), capacity: null, headTeacherId: ANA, assistantIds: [], groupLink: null });

// RN-MAT-01, 15, 20 (#63): matrícula feita pela API, troca de turma e transferência pela ficha do aluno.
test("@critical Coordenador troca a turma e transfere a modalidade pela ficha", async ({ page }) => {
  await page.goto("/entrar");
  await page.getByLabel("E-mail").fill("ana@exemplo.com");
  await page.getByLabel("Senha").fill("segredo123");
  await page.getByRole("button", { name: "Entrar" }).click();
  await expect(page.getByRole("heading", { name: "Bem-vindo ao ctcore" })).toBeVisible();

  const { unit } = (await call(page, "POST", "/units", { name: "Unidade Leste" })) as { unit: { id: string } };
  await call(page, "PUT", `/teachers/${ANA}`, {
    name: "Ana Souza",
    email: null,
    phone: null,
    bio: null,
    directoryConsent: false,
    consentDeclaredByManagement: false,
    unitIds: [CENTRO, unit.id],
    modalities: [{ modalityId: JIU_JITSU, levelId: null, degree: 0 }],
  });
  await call(page, "POST", "/classes", classBody(CENTRO, "Jiu-Jitsu Noite", 2));
  await call(page, "POST", "/classes", classBody(unit.id, "Jiu-Jitsu Leste", 4));
  await call(page, "POST", "/plans", { unitId: unit.id, modalityId: JIU_JITSU, name: "Jiu-Jitsu Leste Mensal", valueCents: 10000, weeklyFrequency: null, validOtherUnits: false });
  await call(page, "POST", `/students/${ALUNO_MENOR}/modalities`, {
    modalities: [{ unitId: CENTRO, modalityId: JIU_JITSU, primaryClassId: TURMA_ADULTO, otherClassIds: [], planId: PLANO_LIVRE, dueDay: 5 }],
  });

  await open(page, `/alunos/${ALUNO_MENOR}`);
  const panel = page.getByRole("dialog", { name: "Ficha do aluno" });
  await expect(panel.getByText("Ativo", { exact: true })).toBeVisible();
  await expect(panel.getByText("Jiu-Jitsu · Unidade Centro · Jiu-Jitsu Adulto")).toBeVisible();

  await panel.getByRole("tab", { name: "Matrícula e modalidades" }).click();
  await panel.getByRole("button", { name: "Trocar turma" }).click();
  const classDialog = page.getByRole("dialog", { name: "Trocar turma de Jiu-Jitsu" });
  await expect(classDialog.getByText("A troca vale na hora.")).toBeVisible();
  await classDialog.getByLabel("Turma principal").selectOption({ label: "Jiu-Jitsu Noite" });
  await classDialog.getByRole("button", { name: "Trocar turma" }).click();
  await expect(classDialog).toHaveCount(0);
  await expect(panel.getByText("Jiu-Jitsu Noite")).toBeVisible();

  await panel.getByRole("button", { name: "Transferir" }).click();
  const transfer = page.getByRole("dialog", { name: "Transferir Jiu-Jitsu para outra unidade" });
  await transfer.getByLabel("Unidade de destino").selectOption({ label: "Unidade Leste" });
  await transfer.getByLabel("Turma principal").selectOption({ label: "Jiu-Jitsu Leste" });
  await transfer.getByLabel("Plano na unidade nova").selectOption({ label: "Jiu-Jitsu Leste Mensal" });
  await transfer.getByLabel("Dia de vencimento (1 a 28)").fill("10");
  await transfer.getByRole("button", { name: "Transferir" }).click();
  await expect(transfer).toHaveCount(0);
  await expect(panel.getByText("Unidade Leste")).toBeVisible();
  await expect(panel.getByText("Dia 10")).toBeVisible();
});

// RF-MAT-01 (#67): nova matrícula em cinco etapas, do aluno já cadastrado ao aluno ativo.
test("@critical Coordenador conclui a nova matrícula em cinco etapas", async ({ page }) => {
  await page.goto("/entrar");
  await page.getByLabel("E-mail").fill("ana@exemplo.com");
  await page.getByLabel("Senha").fill("segredo123");
  await page.getByRole("button", { name: "Entrar" }).click();
  await expect(page.getByRole("heading", { name: "Bem-vindo ao ctcore" })).toBeVisible();

  await call(page, "PUT", `/modalities/${JIU_JITSU}/activation`, { active: true });
  await call(page, "PUT", `/units/${CENTRO}/modalities/${JIU_JITSU}`, { offered: true });

  // CA24: a 360 px nenhuma etapa estoura na horizontal.
  await page.setViewportSize({ width: 360, height: 740 });
  const noOverflow = async () => expect(await page.evaluate("document.documentElement.scrollWidth <= window.innerWidth")).toBe(true);

  await open(page, "/alunos/nova-matricula");
  await expect(page.getByText("Etapa 1 de 5")).toBeVisible();
  await noOverflow();
  await page.getByLabel("CPF do aluno").fill("52998224725");
  await page.getByRole("button", { name: "Buscar" }).click();
  await expect(page.getByText("Matrícula nova")).toBeVisible();
  await page.getByRole("button", { name: "É este aluno, continuar" }).click();

  await expect(page.getByText("Etapa 2 de 5")).toBeVisible();
  await noOverflow();
  await page.getByLabel("Unidade", { exact: true }).selectOption({ label: "Unidade Centro" });
  await page.getByLabel("Modalidade", { exact: true }).selectOption({ label: "Jiu-Jitsu" });
  await page.getByLabel("Turma principal").selectOption({ label: "Jiu-Jitsu Adulto" });
  await page.getByLabel("Plano", { exact: true }).selectOption({ label: "Jiu-Jitsu Livre" });
  await page.getByRole("button", { name: "Avançar" }).click();

  await expect(page.getByText("Etapa 3 de 5")).toBeVisible();
  await noOverflow();
  await page.getByLabel("Dia de vencimento").selectOption({ label: "Dia 5" });
  await page.getByRole("button", { name: "Avançar" }).click();

  await expect(page.getByText("Etapa 4 de 5")).toBeVisible();
  await expect(page.getByText("Mensalidade: R$ 200,00")).toBeVisible();
  await noOverflow();
  await page.getByRole("button", { name: "Concluir matrícula" }).click();

  await expect(page.getByText("Aluno Ativo")).toBeVisible();
  await noOverflow();
  await expect(page.getByRole("button", { name: "Registrar pagamento agora" })).toBeDisabled();
  await page.getByRole("button", { name: "Aluno paga pelo portal" }).click();
  await expect(page.getByRole("dialog", { name: "Ficha do aluno" })).toBeVisible();
});
