import { useResource } from "@/features/students/useStudentRecord";

// Espelham PortalSchedule do OpenAPI (docs/openapi.yaml). Do professor, só o nome; sem presença, frequência, progresso, lista de espera nem cobrança (RN-POR-06, RN-POR-09).
export type ScheduleScope = "ALL" | "MAIN";
export type CancelCode = "TEACHER_UNAVAILABLE" | "HOLIDAY" | "ACADEMY_EVENT" | "OTHER";
export interface ScheduleLesson {
  id: string;
  date: string;
  startsAt: string;
  endsAt: string;
  title: string;
  kind: "MAIN" | "OTHER" | "ADHOC";
  status: "SCHEDULED" | "CANCELLED";
  cancelReasonCode: CancelCode | null;
  cancelReason: string | null;
  changed: boolean;
  originalDate: string | null;
  originalStartsAt: string | null;
  substitute: boolean;
  past: boolean;
  noticeId: string | null;
  modality: { id: string; name: string };
  unit: { id: string; name: string };
  teacherName: string;
}
export interface ScheduleNoClassDay {
  date: string;
  reason: string;
  unit: { id: string; name: string };
}
export interface PortalScheduleData {
  reduced: boolean;
  students: { id: string; name: string; self: boolean }[];
  studentId: string;
  scope: ScheduleScope;
  weekStart: string;
  weekEnd: string;
  today: string;
  timezone: string;
  lessons: ScheduleLesson[];
  noClassDays: ScheduleNoClassDay[];
}

/** Agenda da semana do aluno escolhido (nulo: o primeiro cartão; semana nula: a de hoje). Sem cópia local (D8). */
export function usePortalSchedule(studentId: string | null, week: string | null, scope: ScheduleScope) {
  const q = new URLSearchParams();
  if (studentId) q.set("studentId", studentId);
  if (week) q.set("week", week);
  if (scope === "MAIN") q.set("scope", scope);
  const s = q.toString();
  return useResource<PortalScheduleData>(`/portal/schedule${s ? `?${s}` : ""}`);
}
