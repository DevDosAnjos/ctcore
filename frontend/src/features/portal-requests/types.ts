// Espelham PortalRequest e os esquemas de pedido do OpenAPI (RF-MAT-04).
export type PortalRequestKind = "CLASS_CHANGE" | "NEW_MODALITY" | "PLAN_CHANGE" | "CLOSURE";
export type PortalRequestStatus = "OPEN" | "APPROVED" | "REFUSED" | "EXPIRED" | "CLOSED";
export const REQUEST_KINDS: PortalRequestKind[] = ["CLASS_CHANGE", "NEW_MODALITY", "PLAN_CHANGE", "CLOSURE"];
export const REQUEST_STATUSES: PortalRequestStatus[] = ["OPEN", "APPROVED", "REFUSED", "EXPIRED", "CLOSED"];

export interface PortalRequest {
  id: string;
  kind: PortalRequestKind;
  status: PortalRequestStatus;
  studentId: string;
  studentName: string;
  unitId: string;
  unit: string;
  modalityRowId: string | null;
  modality: string | null;
  targetClassId: string | null;
  targetClass: string | null;
  targetPlanId: string | null;
  targetPlan: string | null;
  targetModalityId: string | null;
  targetUnitId: string | null;
  targetUnit: string | null;
  reason: string | null;
  note: string | null;
  response: string | null;
  createdAt: string;
  decidedAt: string | null;
}
