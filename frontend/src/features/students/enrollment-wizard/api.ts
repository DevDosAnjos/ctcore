import { api } from "@/lib/api";
import type { StudentDetail, StudentInput } from "../types";
import type { ConclusionRequest, EnrollmentConcluded, EnrollmentPreview } from "./types";

export const previewEnrollment = (studentId: string, body: ConclusionRequest) => api<EnrollmentPreview>(`/students/${studentId}/enrollment/preview`, { method: "POST", body });
export const concludeEnrollment = (studentId: string, body: ConclusionRequest) => api<EnrollmentConcluded>(`/students/${studentId}/enrollment/conclude`, { method: "POST", body });
export const uploadOriginProof = (studentId: string, file: File) => {
  const form = new FormData();
  form.append("file", file, file.name);
  return api<{ proofKey: string }>(`/students/${studentId}/enrollment/origin-proof`, { method: "POST", body: form });
};
export const lookupByCpf = (cpf: string) => api<{ id: string; name: string; photoUrl: string | null; enrollmentStatus: "NONE" | "ACTIVE" | "FORMER" }>(`/students/lookup?cpf=${cpf}`);
export const createStudent = (body: StudentInput) => api<StudentDetail>("/students", { method: "POST", body });
