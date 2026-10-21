import { useEffect, useState } from "react";
import { api, ApiError } from "@/lib/api";
import type { Enrollment, StudentDetail, StudentHistoryEntry, StudentRecord, StudentUpdate } from "./types";

type Failure = "forbidden" | "notFound" | "error";
type Done<T> = { path: string; data?: T; failure?: Failure };

// GET de um recurso da ficha: 403 vira "sem permissão" e 404 "não encontrado"; `reload` busca de novo (ex.: depois de vincular um responsável).
export function useResource<T>(path: string | null) {
  const [tick, setTick] = useState(0);
  const [done, setDone] = useState<Done<T>>();
  useEffect(() => {
    if (path === null) return;
    let alive = true;
    api<T>(path).then(
      (data) => alive && setDone({ path, data }),
      (e) =>
        alive &&
        setDone({ path, failure: e instanceof ApiError && e.status === 403 ? "forbidden" : e instanceof ApiError && e.status === 404 ? "notFound" : "error" }),
    );
    return () => {
      alive = false;
    };
  }, [path, tick]);
  // Ao recarregar, a ficha anterior segue na tela até a nova chegar (as abas e os avisos não se perdem).
  const current = done?.path === path ? done : undefined;
  return { status: path === null ? ("idle" as const) : !current ? ("loading" as const) : (current.failure ?? ("ready" as const)), data: current?.data, reload: () => setTick((n) => n + 1) };
}

export function useStudentRecord(id: string) {
  return useResource<StudentRecord>(`/students/${id}/record`);
}

export function useStudentHistory(id: string, page: number) {
  return useResource<{ items: StudentHistoryEntry[]; page: number; size: number; total: number }>(`/students/${id}/history?page=${page}&size=25`);
}

// Matrícula do aluno (RN-MAT-01); só a gestão a lê, então `enabled` falso não faz pedido.
export function useEnrollment(id: string, enabled: boolean) {
  return useResource<Enrollment>(enabled ? `/students/${id}/enrollment` : null);
}

// Trocas da matrícula (R9 a R11); cada uma devolve a matrícula inteira.
const row = (id: string, rowId: string) => `/students/${id}/modalities/${rowId}`;
export const changeClass = (id: string, rowId: string, body: { primaryClassId: string; otherClassIds: string[] }) => api<Enrollment>(`${row(id, rowId)}/class`, { method: "PUT", body });
export const changePlan = (id: string, rowId: string, planId: string) => api<Enrollment>(`${row(id, rowId)}/plan`, { method: "PUT", body: { planId } });
export const transferModality = (id: string, rowId: string, body: { unitId: string; primaryClassId: string; otherClassIds: string[]; planId: string; dueDay?: number }) =>
  api<Enrollment>(`${row(id, rowId)}/transfer`, { method: "POST", body });

// Dados pessoais completos para a janela de edição (RF-ALU-04).
export function useStudentDetail(id: string) {
  return useResource<StudentDetail>(`/students/${id}`);
}

export const updateStudent = (id: string, input: StudentUpdate) => api<StudentDetail>(`/students/${id}`, { method: "PUT", body: input });
export const uploadStudentPhoto = (id: string, photo: Blob) => {
  const form = new FormData();
  form.append("file", photo, "foto.png");
  return api<StudentDetail>(`/students/${id}/photo`, { method: "PUT", body: form });
};
export const removeStudentPhoto = (id: string) => api<StudentDetail>(`/students/${id}/photo`, { method: "DELETE" });
