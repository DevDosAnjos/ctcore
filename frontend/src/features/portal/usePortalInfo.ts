import { api } from "@/lib/api";
import { useResource } from "@/features/students/useStudentRecord";
import type { PortalStudentCard } from "@/features/students/types";

// Espelha PortalInfo e PortalInfoPersonal do OpenAPI (docs/openapi.yaml). Sem presença, progresso, CPF completo nem contato do professor (RN-POR-09).
export interface InfoPersonal {
  name: string;
  birthDate: string;
  maskedCpf: string | null;
  photoUrl: string | null;
  phone: string | null;
  email: string | null;
  address: { postalCode: string; street: string; number: string; complement: string | null; district: string; city: string; state: string };
  emergency: { name: string | null; relationship: string | null; phone: string | null };
  canEdit: boolean;
  editableFields: string[];
}
export interface InfoUnit {
  id: string;
  name: string;
  phone: string | null;
  whatsapp: string | null;
  email: string | null;
}
export interface InfoClass {
  clazz: { id: string; name: string };
  teacherName: string;
  schedule: { weekday: number; startsAt: string; endsAt: string }[];
}
export interface InfoLevel {
  id: string;
  levelName: string;
  color: string;
  secondaryColor: string | null;
  degree: number;
  achievedOn: string | null;
  origin: boolean;
}
export interface InfoTraining {
  modality: { id: string; name: string };
  unit: InfoUnit;
  mainClass: InfoClass;
  otherClasses: InfoClass[];
}
export interface InfoPlan {
  modality: { id: string; name: string };
  plan: { id: string; name: string };
  planCents: number;
  benefit: { type: "PERCENT" | "AMOUNT"; value: number; validUntil: string | null } | null;
  benefitCents: number;
  monthlyCents: number;
  dueDay: number;
  nextPlan: { id: string; name: string; valueCents: number } | null;
}
export interface InfoGraduation {
  modality: { id: string; name: string };
  belt: InfoLevel | null;
  history: InfoLevel[];
}
export interface PortalInfoData {
  reduced: boolean;
  students: PortalStudentCard[];
  selectedStudentId: string;
  personal: InfoPersonal;
  unitContacts: InfoUnit[];
  trainings: InfoTraining[];
  plans: InfoPlan[];
  graduation: InfoGraduation[];
  emailAnnouncements: boolean;
  terms: { enrollment: { version: number; acceptedAt: string } | null; imageUse: { granted: boolean; version: number | null; at: string | null } };
}
export interface ContactInput {
  phone: string | null;
  postalCode: string;
  street: string;
  number: string;
  complement: string | null;
  district: string;
  city: string;
  state: string;
  emergencyName: string | null;
  emergencyRelationship: string | null;
  emergencyPhone: string | null;
}

export const usePortalInfo = (studentId: string | null) => useResource<PortalInfoData>(`/portal/info${studentId ? `?studentId=${studentId}` : ""}`);
export const savePortalContact = (studentId: string, body: ContactInput) => api<InfoPersonal>(`/portal/students/${studentId}/contact`, { method: "PUT", body });
export const uploadPortalPhoto = (studentId: string, photo: Blob) => {
  const form = new FormData();
  form.append("file", photo, "foto.png");
  return api<InfoPersonal>(`/portal/students/${studentId}/photo`, { method: "PUT", body: form });
};
export const removePortalPhoto = (studentId: string) => api<InfoPersonal>(`/portal/students/${studentId}/photo`, { method: "DELETE" });
// Conceder usa a versão vigente do termo de imagem (lida na hora); revogar não precisa de versão.
export async function setPortalImageUse(studentId: string, granted: boolean) {
  const path = `/portal/students/${studentId}/image-use`;
  const version = granted ? (await api<{ version: number }>(path)).version : undefined;
  return api<{ granted: boolean }>(path, { method: "PUT", body: { granted, ...(version !== undefined ? { version } : {}) } });
}
export const setEmailAnnouncements = (announcements: boolean) => api<{ announcements: boolean }>("/auth/email-preferences", { method: "PUT", body: { announcements } });
