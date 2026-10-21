import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router";
import { axeViolations } from "@/test/axe";
import { setDevicePrefersDark } from "@/test/device";
import { ThemeProvider } from "@/theme/ThemeProvider";
import { enrollmentOf, modalityRow } from "./enrollmentFixtures";
import { RecordEnrollment } from "./RecordEnrollment";
import type { Enrollment } from "./types";

type Result = Parameters<typeof RecordEnrollment>[0]["enrollment"];
const reload = vi.fn();
const result = (status: Result["status"], data?: Enrollment): Result => ({ status, data, reload });
const show = (enrollment: Result, canGrant = false) =>
  render(
    <ThemeProvider>
      <MemoryRouter>
        <RecordEnrollment studentId="s1" studentName="Aluno Teste" canGrant={canGrant} enrollment={enrollment} onChanged={() => {}} />
      </MemoryRouter>
    </ThemeProvider>,
  );

describe("aba Matrícula e modalidades (R23, R27, CA33)", () => {
  beforeEach(() => reload.mockClear());

  it("carregando, sem permissão e erro com nova tentativa", async () => {
    const user = userEvent.setup();
    const first = show(result("loading"));
    expect(screen.getByRole("status")).toHaveTextContent("Carregando");
    first.unmount();
    const second = show(result("forbidden"));
    expect(screen.getByText("Você não tem acesso")).toBeInTheDocument();
    second.unmount();
    show(result("error"));
    await user.click(screen.getByRole("button", { name: "Tentar de novo" }));
    expect(reload).toHaveBeenCalled();
  });

  it("RF-BEN-02: Conceder benefício só aparece com permissão de benefícios e abre a janela da modalidade", async () => {
    const user = userEvent.setup();
    const data = enrollmentOf([modalityRow(), modalityRow({ id: "m2", canEdit: false })]);
    const without = show(result("ready", data));
    expect(screen.queryByRole("button", { name: "Conceder benefício" })).toBeNull();
    without.unmount();
    show(result("ready", data), true);
    expect(screen.getAllByRole("button", { name: "Conceder benefício" })).toHaveLength(1);
    await user.click(screen.getByRole("button", { name: "Conceder benefício" }));
    expect(await screen.findByRole("dialog", { name: "Conceder benefício em Jiu-Jitsu" })).toBeInTheDocument();
  });

  it("sem matrícula mostra o estado vazio explicativo", () => {
    show(result("ready", { id: null, status: "NONE", enrolledAt: null, reopenedAt: null, modalities: [] }));
    expect(screen.getByText("Sem matrícula")).toBeInTheDocument();
    expect(screen.getByText(/nova matrícula/)).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Nova matrícula" })).toHaveAttribute("href", "/alunos/nova-matricula?aluno=s1");
  });

  it("modalidade encerrada aparece sem ações", () => {
    show(result("ready", enrollmentOf([modalityRow({ status: "ENDED", endedAt: "2026-03-01T12:00:00Z" })])));
    expect(screen.getByText("Encerrada")).toBeInTheDocument();
    expect(screen.queryByRole("button")).toBeNull();
    // R11: ex-aluno recomeça pelo assistente, já no aluno
    expect(screen.getByRole("link", { name: "Nova matrícula" })).toHaveAttribute("href", "/alunos/nova-matricula?aluno=s1");
  });

  it.each([
    ["claro", false],
    ["escuro", true],
  ])("a 360 px não tem violações no axe no tema %s", async (_n, dark) => {
    setDevicePrefersDark(dark);
    window.innerWidth = 360;
    const { container } = show(result("ready", enrollmentOf([modalityRow({ nextPlan: { id: "p2", name: "Plano Trimestral" } }), modalityRow({ id: "m2", canEdit: false })])));
    expect(await axeViolations(container)).toEqual([]);
  });
});
