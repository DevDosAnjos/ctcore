import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { server } from "@/mocks/server";
import { axeViolations } from "@/test/axe";
import { setDevicePrefersDark } from "@/test/device";
import { ThemeProvider } from "@/theme/ThemeProvider";
import { ClassWarnings } from "./ClassWarnings";

const URL = "/api/v1/students/:id/enrollment/class-warnings";
const range = { id: "r1", name: "Juvenil", minAge: 13, maxAge: 17 };
const view = () =>
  render(
    <ThemeProvider>
      <ClassWarnings studentId="s1" classId="c1" className="Judô Juvenil" />
    </ThemeProvider>,
  );

describe("avisos da turma (RF-MAT-02, #68)", () => {
  it("CA5: mostra o aviso de faixa etária e o de capacidade em texto visível", async () => {
    server.use(http.get(URL, () => HttpResponse.json({ studentAge: 10, ageRange: range, ageOutOfRange: true, capacity: 2, activeStudents: 2, overCapacity: true })));
    view();
    expect(await screen.findByText("Aluno com 10 anos, fora da faixa etária da turma Judô Juvenil (Juvenil, 13–17 anos).")).toBeInTheDocument();
    expect(screen.getByText("Turma Judô Juvenil acima da capacidade com esta matrícula (3/2).")).toBeInTheDocument();
    expect(screen.getByRole("status")).toBeInTheDocument();
  });

  it("CA8: o simulado devolve o formato do contrato", async () => {
    await fetch("/api/v1/auth/login", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ email: "ana@exemplo.com", password: "segredo123" }) });
    const ok = await fetch("/api/v1/students/00000000-0000-0000-0000-000000000701/enrollment/class-warnings?classId=00000000-0000-0000-0000-000000000601");
    expect(Object.keys(await ok.json()).sort()).toEqual(["activeStudents", "ageOutOfRange", "ageRange", "capacity", "overCapacity", "studentAge"]);
    expect((await fetch("/api/v1/students/00000000-0000-0000-0000-000000000701/enrollment/class-warnings")).status).toBe(400);
  });

  it("faixa sem máximo usa o texto 'a partir de'", async () => {
    server.use(http.get(URL, () => HttpResponse.json({ studentAge: 10, ageRange: { ...range, minAge: 18, maxAge: null }, ageOutOfRange: true, capacity: null, activeStudents: 0, overCapacity: false })));
    view();
    expect(await screen.findByText(/a partir de 18 anos/)).toBeInTheDocument();
  });

  it("CA5: sem o que avisar nada aparece", async () => {
    server.use(http.get(URL, () => HttpResponse.json({ studentAge: 15, ageRange: range, ageOutOfRange: false, capacity: 5, activeStudents: 1, overCapacity: false })));
    view();
    expect(await screen.findByRole("status")).toBeEmptyDOMElement();
  });

  it("CA9: erro mostra Tentar de novo e a nova tentativa funciona", async () => {
    const user = userEvent.setup();
    server.use(http.get(URL, () => HttpResponse.json({ studentAge: 10, ageRange: range, ageOutOfRange: true, capacity: null, activeStudents: 0, overCapacity: false })));
    server.use(http.get(URL, () => HttpResponse.json({}, { status: 500 }), { once: true }));
    view();
    await user.click(await screen.findByRole("button", { name: "Tentar de novo" }));
    expect(await screen.findByText(/fora da faixa etária/)).toBeInTheDocument();
  });

  it("CA9: sem permissão mostra o estado padrão", async () => {
    server.use(http.get(URL, () => HttpResponse.json({}, { status: 403 })));
    view();
    expect(await screen.findByText("Você não tem acesso")).toBeInTheDocument();
  });

  it.each([
    ["claro", false],
    ["escuro", true],
  ])("CA9: sem violações no axe no tema %s", async (_n, dark) => {
    setDevicePrefersDark(dark);
    server.use(http.get(URL, () => HttpResponse.json({ studentAge: 10, ageRange: range, ageOutOfRange: true, capacity: 2, activeStudents: 2, overCapacity: true })));
    const { container } = view();
    await screen.findByText(/fora da faixa etária/);
    expect(await axeViolations(container)).toEqual([]);
  });
});
