// Espelham PaymentProof, PaymentProofConfirmed e PixInstructions do OpenAPI (RF-COB-05).
export type ProofStatus = "PENDING" | "CONFIRMED" | "REFUSED";

export interface PaymentProof {
  id: string;
  chargeId: string;
  studentId: string;
  studentName: string;
  studentPhotoUrl: string | null;
  unitId: string;
  unit: string;
  reference: string;
  amountCents: number;
  dueOn: string;
  sentAt: string;
  status: ProofStatus;
  contentType: string;
  imageUrl: string | null;
  refusalReason: string | null;
  decidedAt: string | null;
}

export interface PaymentProofConfirmed {
  proof: PaymentProof;
  payment: { id: string; receipt: { number: number; code: string }; receiptEmail: string };
}
