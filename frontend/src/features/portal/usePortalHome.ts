import { useResource } from "@/features/students/useStudentRecord";

// Espelham PortalHome do OpenAPI (docs/openapi.yaml). Sem presença, frequência nem progresso de graduação (RN-POR-09).
export interface HomeRef {
  id: string;
  name: string;
}
export interface HomeStudent {
  id: string;
  name: string;
  photoUrl: string | null;
  self: boolean;
  openCents: number;
}
export type HomeSituation = "OVERDUE" | "DUE_TODAY" | "DUE_SOON" | "IN_REVIEW";
export interface HomeCharge {
  id: string;
  studentId: string;
  description: string | null;
  referenceMonth: string | null;
  dueOn: string;
  amountCents: number;
  situation: HomeSituation;
  canPay: boolean;
  modality: HomeRef | null;
  unit: HomeRef;
}
export interface HomeLesson {
  id: string;
  date: string;
  startsAt: string;
  endsAt: string;
  title: string;
  status: "SCHEDULED" | "CANCELLED";
  cancelReason: string | null;
  changed: boolean;
  adhoc: boolean;
  modality: HomeRef;
  unit: HomeRef;
}
export interface HomeNotice {
  id: string;
  title: string;
  sentAt: string;
  pinned: boolean;
  unread: boolean;
  authorName: string | null;
}
export interface HomeBelt {
  modality: HomeRef;
  levelName: string;
  color: string;
  secondaryColor: string | null;
  degree: number;
  achievedOn: string | null;
  isNew: boolean;
}
export interface PortalHomeData {
  reduced: boolean;
  students: HomeStudent[];
  familyOpenCents: number;
  selectedStudentId: string | null;
  charges: { items: HomeCharge[]; nextDueOn: string | null };
  lessons: HomeLesson[];
  announcements: { unreadCount: number; items: HomeNotice[] };
  belts: HomeBelt[];
}

/** Início do portal do aluno escolhido (nulo: o primeiro cartão). Trocar de cartão refaz a leitura (D8). */
export function usePortalHome(studentId: string | null) {
  return useResource<PortalHomeData>(`/portal/home${studentId ? `?studentId=${studentId}` : ""}`);
}
