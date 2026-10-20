import { useEffect, useState } from "react";
import { api, ApiError } from "@/lib/api";
import type { StudentDetail, StudentHistoryEntry, StudentRecord, StudentUpdate } from "./types";

type Failure = "forbidden" | "notFound" | "error";
type Done<T> = { path: string; data?: T; failure?: Failure };

// GET de um recurso da ficha: 403 vira "sem permissão" e 404 "não encontrado"; `reload` busca de novo (ex.: depois de vincular um responsável).
export function useResource<T>(path: string) {
  const [tick, setTick] = useState(0);
  const [done, setDone] = useState<Done<T>>();
  useEffect(() => {
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
  return { status: !current ? ("loading" as const) : (current.failure ?? ("ready" as const)), data: current?.data, reload: () => setTick((n) => n + 1) };
}

export function useStudentRecord(id: string) {
  return useResource<StudentRecord>(`/students/${id}/record`);
}

export function useStudentHistory(id: string, page: number) {
  return useResource<{ items: StudentHistoryEntry[]; page: number; size: number; total: number }>(`/students/${id}/history?page=${page}&size=25`);
}

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
