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
export type StudentSeal = "INCOMPLETE_REGISTRATION" | "EMAIL_PROBLEM" | "BIRTHDAY_TODAY";
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
}
export interface StudentList {
  items: StudentRow[];
  page: number;
  size: number;
  total: number;
}
