import { http, HttpResponse } from "msw";
import type { Enrollment, EnrollmentModality } from "./types";

// Dados e rotas de apoio dos testes da matrícula na ficha (R23 a R26).
export const modalityRow = (over: Partial<EnrollmentModality> = {}): EnrollmentModality => ({
  id: "m1",
  unit: { id: "u1", name: "Centro" },
  modality: { id: "mod1", name: "Jiu-Jitsu" },
  primaryClass: { id: "k1", name: "Turma Manhã" },
  otherClasses: [{ id: "k2", name: "Turma Noite" }],
  plan: { id: "p1", name: "Plano Mensal" },
  nextPlan: null,
  dueDay: 5,
  status: "ACTIVE",
  startedAt: "2026-01-10T12:00:00Z",
  endedAt: null,
  canEdit: true,
  closureRequestedAt: null,
  ...over,
});

export const enrollmentOf = (modalities: EnrollmentModality[], over: Partial<Enrollment> = {}): Enrollment => ({
  id: "e1",
  status: modalities.some((m) => m.status === "ACTIVE") ? "ACTIVE" : "FORMER",
  enrolledAt: "2026-01-10T12:00:00Z",
  reopenedAt: null,
  modalities,
  ...over,
});

// Turmas, planos e unidades que as janelas pedem; devolve as consultas feitas, para conferir o filtro.
export function choiceRoutes(calls: string[] = []) {
  return [
    http.get("/api/v1/classes", ({ request }) => {
      calls.push(request.url);
      return HttpResponse.json({ items: [{ id: "k1", name: "Turma Manhã" }, { id: "k2", name: "Turma Noite" }, { id: "k3", name: "Turma Tarde" }], page: 1, size: 100, total: 3 });
    }),
    http.get("/api/v1/plans", ({ request }) => {
      calls.push(request.url);
      return HttpResponse.json({ items: [{ id: "p1", name: "Plano Mensal" }, { id: "p2", name: "Plano Trimestral" }], page: 1, size: 100, total: 2 });
    }),
    http.get("/api/v1/units/reachable", () => HttpResponse.json({ allUnits: true, units: [{ id: "u1", name: "Centro" }, { id: "u2", name: "Norte" }] })),
  ];
}
