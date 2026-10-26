import type { ChargeSituation } from "@/features/charges/types";
// Formato do servidor (docs/openapi.yaml: StudentDetail, StudentGuardianRow, StudentRef, PortalStudentCard). As telas vêm na ficha (#60), na matrícula (#67) e no portal (#89).
export type StudentGuardianAccess = "PENDING" | "ACTIVE";

export interface StudentGuardianRow {
  guardianId: string;
  name: string;
  email: string;
  phone: string;
  financial: boolean;
  access: StudentGuardianAccess;
  emailProblem: boolean;
}

export interface StudentDetail {
  id: string;
  registeredUnitId: string;
  name: string;
  birthDate: string;
  minor: boolean;
  postalCode: string;
  street: string;
  number: string;
  complement: string | null;
  district: string;
  city: string;
  state: string;
  cpf: string | null;
  email: string | null;
  phone: string | null;
  gender: string | null;
  referral: string | null;
  emergencyName: string | null;
  emergencyRelationship: string | null;
  emergencyPhone: string | null;
  photoUrl: string | null;
  emailProblem: boolean;
  guardians: StudentGuardianRow[];
}

export interface StudentRef {
  id: string;
  name: string;
  photoUrl: string | null;
}

export interface PortalStudentCard extends StudentRef {
  self: boolean;
}

export interface StudentGuardianInput {
  name: string;
  cpf: string;
  email: string;
  phone: string;
  financial?: boolean;
  reason?: string;
}

export interface StudentInput {
  registeredUnitId: string;
  name: string;
  birthDate: string;
  postalCode: string;
  street: string;
  number: string;
  complement?: string | null;
  district: string;
  city: string;
  state: string;
  cpf?: string | null;
  email?: string | null;
  phone?: string | null;
  gender?: string | null;
  referral?: string | null;
  emergencyName?: string | null;
  emergencyRelationship?: string | null;
  emergencyPhone?: string | null;
  confirmDuplicate?: boolean;
  guardians?: StudentGuardianInput[];
}

// Espelha StudentRow e StudentList do OpenAPI (RF-ALU-01). CPF, e-mail, telefone, endereço e emergência nunca vêm na lista.
export type StudentSeal = "INCOMPLETE_REGISTRATION" | "EMAIL_PROBLEM" | "BIRTHDAY_TODAY" | "NEW" | "SCHOLARSHIP" | "TERM_PENDING";
export type EnrollmentStatus = "NONE" | "ACTIVE" | "FORMER";
// Modalidade ativa na linha da lista (StudentRowModality); o nível é nulo até a graduação do aluno (D13).
export interface StudentRowModality {
  id: string;
  unit: { id: string; name: string };
  modality: { id: string; name: string };
  primaryClass: { id: string; name: string };
  level: { id: string; name: string } | null;
}
// Lista simples de turma ou aula (StudentSimple): sem CPF, contato, valores nem selos.
export interface StudentSimple {
  id: string;
  name: string;
  photoUrl: string | null;
  belt: string | null;
  blocked: boolean;
}
export interface StudentRow {
  id: string;
  name: string;
  photoUrl: string | null;
  birthDate: string;
  age: number;
  minor: boolean;
  ageRange: { id: string; name: string } | null;
  registeredUnit: { id: string; name: string };
  createdAt: string;
  seals: StudentSeal[];
  status: EnrollmentStatus;
  modalities: StudentRowModality[];
  financialStatus: "OVERDUE" | "CURRENT";
}
export interface StudentList {
  items: StudentRow[];
  page: number;
  size: number;
  total: number;
  counts: { active: number; former: number; overdue: number };
}

// Espelha StudentRecord e StudentHistoryEntry do OpenAPI (RF-ALU-03). Na ficha reduzida do Professor faltam birthDate, seals, cpf, address e, nos responsáveis, financial e access.
export interface StudentRecordGuardian {
  guardianId: string;
  name: string;
  email?: string;
  phone?: string;
  financial?: boolean;
  access?: StudentGuardianAccess;
  emailProblem?: boolean;
}
export interface StudentRecord {
  id: string;
  name: string;
  photoUrl: string | null;
  birthDate?: string;
  age: number;
  minor: boolean;
  ageRange: { id: string; name: string } | null;
  registeredUnit: { id: string; name: string };
  seals?: StudentSeal[];
  contact?: { phone: string | null; email: string | null; emailProblem: boolean };
  cpf?: string | null;
  address?: { postalCode: string; street: string; number: string; complement: string | null; district: string; city: string; state: string };
  emergency?: { name: string | null; relationship: string | null; phone: string | null };
  guardians: StudentRecordGuardian[];
}
export interface StudentHistoryEntry {
  action: string;
  at: string;
  actor: string | null;
  actorProfile: string | null;
  reason: string | null;
}

// Corpo de PUT /students/{id} (RF-ALU-04): os dados pessoais de StudentInput, sem unidade de cadastro nem responsáveis.
export type StudentUpdate = Omit<StudentInput, "registeredUnitId" | "guardians">;

// RF-ALU-06 (docs/openapi.yaml: StudentBirthday): aniversariante de hoje; o contato é o do WhatsApp (nulo sem telefone ou sem permissão).
export interface StudentBirthday {
  id: string;
  name: string;
  photoUrl: string | null;
  age: number;
  ageRange: { id: string; name: string } | null;
  registeredUnit: { id: string; name: string };
  contactName: string | null;
  contactPhone: string | null;
  contactIsGuardian: boolean;
}

// Espelha Enrollment e EnrollmentModality do OpenAPI (RN-MAT-01, 02): a matrícula do aluno; `canEdit` vale por unidade (RN-ALU-07).
export interface EnrollmentNamed {
  id: string;
  name: string;
}
export interface EnrollmentModality {
  id: string;
  unit: EnrollmentNamed;
  modality: EnrollmentNamed;
  primaryClass: EnrollmentNamed;
  otherClasses: EnrollmentNamed[];
  plan: EnrollmentNamed;
  nextPlan: EnrollmentNamed | null;
  dueDay: number;
  status: "ACTIVE" | "ENDED";
  startedAt: string;
  endedAt: string | null;
  canEdit: boolean;
}
export interface Enrollment {
  id: string | null;
  status: EnrollmentStatus;
  enrolledAt: string | null;
  reopenedAt: string | null;
  modalities: EnrollmentModality[];
}

// Espelha ModalityEndInput e EndPreview do OpenAPI (RF-MAT-03, RN-MAT-17).
export type EndReason = "CITY_CHANGE" | "FINANCIAL" | "SCHEDULE" | "INJURY" | "DISSATISFACTION" | "OTHER";
export interface OpenCharge {
  id: string;
  referenceMonth: string | null;
  dueOn: string;
  amountCents: number;
  status: ChargeSituation;
}
export interface ChargeDecision {
  chargeId: string;
  action: "KEEP" | "CANCEL";
  reason?: string;
}
export interface EndPreview {
  openCharges: OpenCharge[];
}
