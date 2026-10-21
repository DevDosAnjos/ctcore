import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { axeViolations } from "@/test/axe";
import { setDevicePrefersDark } from "@/test/device";
import { ThemeProvider } from "@/theme/ThemeProvider";
import { enrollmentOf, modalityRow } from "./enrollmentFixtures";
import { RecordEnrollment } from "./RecordEnrollment";
import type { Enrollment } from "./types";

type Result = Parameters<typeof RecordEnrollment>[0]["enrollment"];
const reload = vi.fn();
const result = (status: Result["status"], data?: Enrollment): Result => ({ status, data, reload });
const show = (enrollment: Result) =>
  render(
    <ThemeProvider>
      <RecordEnrollment studentId="s1" enrollment={enrollment} onChanged={() => {}} />
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

  it("sem matrícula mostra o estado vazio explicativo", () => {
    show(result("ready", { id: null, status: "NONE", enrolledAt: null, reopenedAt: null, modalities: [] }));
    expect(screen.getByText("Sem matrícula")).toBeInTheDocument();
    expect(screen.getByText(/nova matrícula/)).toBeInTheDocument();
  });

  it("modalidade encerrada aparece sem ações", () => {
    show(result("ready", enrollmentOf([modalityRow({ status: "ENDED", endedAt: "2026-03-01T12:00:00Z" })])));
    expect(screen.getByText("Encerrada")).toBeInTheDocument();
    expect(screen.queryByRole("button")).toBeNull();
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
