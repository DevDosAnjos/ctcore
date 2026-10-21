import type { Enrollment, EnrollmentStatus } from "../types";

// Espelha EnrollmentConclusionRequest, EnrollmentPreview, EnrollmentConcluded e OriginProofStored do OpenAPI (RF-MAT-01, #67). Valores em centavos.
export interface BenefitInput {
  type: "PERCENT" | "AMOUNT";
  value: number;
  reason: string;
  decidedBy: string;
  validUntil?: string | null;
}
export interface OriginInput {
  levelId: string;
  degree: number;
  achievedOn?: string | null;
  proofKey?: string | null;
}
export interface ConclusionModality {
  unitId: string;
  modalityId: string;
  primaryClassId: string;
  otherClassIds: string[];
  planId: string;
  dueDay?: number | null;
  benefit?: BenefitInput | null;
  origin?: OriginInput | null;
}
export interface ConclusionUnit {
  unitId: string;
  feeExempt?: boolean;
  feeExemptReason?: string | null;
  secondDueOn?: string | null;
}
export interface ConclusionRequest {
  modalities: ConclusionModality[];
  units: ConclusionUnit[];
}
export interface PreviewModality {
  unitId: string;
  modalityId: string;
  planId: string;
  planName: string;
  planCents: number;
  discountCents: number;
  monthlyCents: number;
}
export interface PreviewUnit {
  unitId: string;
  unitName: string;
  dueDay: number;
  firstDueOn: string;
  toleranceDays: number;
  feeCents: number;
  feeExempt: boolean;
  feeExemptReason: string | null;
  firstChargeCents: number;
  secondDueOn: string;
  suggestedSecondDueOn: string;
}
export interface EnrollmentPreview {
  modalities: PreviewModality[];
  units: PreviewUnit[];
}
export type InviteStatus = "SENT" | "FAILED" | "MINOR" | "EXISTING_ACCOUNT" | "NO_EMAIL";
export interface EnrollmentConcluded {
  enrollment: Enrollment;
  summary: EnrollmentPreview;
  termPending: boolean;
  inviteStatus: InviteStatus;
}

// Rascunho do assistente: fica no componente (nada em armazenamento do navegador).
export interface BenefitDraft {
  on: boolean;
  type: "PERCENT" | "AMOUNT";
  percent: string;
  amount: number | null;
  reason: string;
  decidedBy: string;
  validUntil: string;
}
export interface OriginDraft {
  on: boolean;
  levelId: string;
  degree: string;
  achievedOn: string;
  proofKey: string;
  proofName: string;
  proofRequired: boolean;
}
export interface ModalityDraft {
  key: string;
  unitId: string;
  modalityId: string;
  primaryClassId: string;
  otherClassIds: string[];
  planId: string;
  benefit: BenefitDraft;
  origin: OriginDraft;
}
export interface UnitDraft {
  dueDay: string;
  feeExempt: boolean;
  feeExemptReason: string;
  secondDueOn: string;
}
export interface WizardStudent {
  id: string;
  name: string;
  photoUrl: string | null;
  enrollmentStatus: EnrollmentStatus;
}
export type Drafts = Record<string, UnitDraft>;

// Espelha ClassWarnings do OpenAPI (RF-MAT-02, #68).
export interface ClassWarnings {
  studentAge: number;
  ageRange: { id: string; name: string; minAge: number; maxAge: number | null } | null;
  ageOutOfRange: boolean;
  capacity: number | null;
  activeStudents: number;
  overCapacity: boolean;
}
