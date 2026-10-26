import { api } from "@/lib/api";
import type { Choice } from "@/features/students/ModalityPickers";
import { useResource } from "@/features/students/useStudentRecord";
import type { PortalRequest, PortalRequestKind } from "@/features/portal-requests/types";

// Espelham PortalStudentCard e PortalRequestOptions do OpenAPI.
export interface PortalStudentCard {
  id: string;
  name: string;
  self: boolean;
}
export interface ModalityOption {
  modalityRowId: string;
  modality: string;
  unit: string;
  currentClass: Choice;
  currentPlan: Choice;
  closureRequestedAt: string | null;
  classes: Choice[];
  plans: Choice[];
}
export interface OfferOption {
  unitId: string;
  unit: string;
  modalityId: string;
  modality: string;
}
export interface RequestOptions {
  modalities: ModalityOption[];
  offers: OfferOption[];
}
export interface NewRequest {
  kind: PortalRequestKind;
  modalityRowId?: string;
  targetClassId?: string;
  targetPlanId?: string;
  targetUnitId?: string;
  targetModalityId?: string;
  reason?: string;
  note?: string;
}

export const usePortalStudents = () => useResource<PortalStudentCard[]>("/portal/students");
export const useRequestOptions = (studentId: string | null) => useResource<RequestOptions>(studentId ? `/portal/students/${studentId}/request-options` : null);
export const useMyRequests = (studentId: string | null) => useResource<PortalRequest[]>(studentId ? `/portal/students/${studentId}/requests` : null);
export const createRequest = (studentId: string, body: NewRequest) => api<PortalRequest>(`/portal/students/${studentId}/requests`, { method: "POST", body });
