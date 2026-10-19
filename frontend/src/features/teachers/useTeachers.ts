import { useEffect, useState } from "react";
import { api, ApiError } from "@/lib/api";
import type { Page, Query } from "@/components/query/QueryPage";

// Espelham TeacherRow do OpenAPI (docs/openapi.yaml). Telefone e e-mail nunca vêm na lista (RN-PRO-04).
export interface Teacher {
  id: string;
  name: string;
  photoUrl: string | null;
  modalities: { id: string; name: string; levelId: string | null; level: string | null; degree: number | null }[];
  units: { id: string; name: string }[];
  classCount: number;
  status: "ACTIVE" | "INACTIVE";
  directoryConsent: boolean;
  access: "NONE" | "PENDING" | "ACTIVE" | "ENDED";
}
interface Choice {
  id: string;
  name: string;
}

// Query da tela (RNF-27) -> GET /teachers. A ordem "name" ou "-name" vira sort=name,asc|desc; erro sobe com o status (403 vira "sem permissão").
export async function fetchTeachers(q: Query): Promise<Page<Teacher>> {
  const p = new URLSearchParams({ page: String(q.pagina), size: String(q.tamanho) });
  if (q.q) p.set("q", q.q);
  if (q.ordem) p.set("sort", q.ordem.startsWith("-") ? "name,desc" : "name,asc");
  for (const [k, v] of Object.entries(q.filtros)) p.set(k, v);
  const data = await api<{ items: Teacher[]; total: number }>(`/teachers?${p}`);
  return { rows: data.items, total: data.total };
}

// Opções dos filtros: modalidades ativas da organização e unidades ao alcance. Falha deixa a lista vazia; a lista de professores segue.
export function useTeacherFilterOptions() {
  const [options, setOptions] = useState<{ modalities: Choice[]; units: Choice[] }>({ modalities: [], units: [] });
  useEffect(() => {
    let alive = true;
    Promise.all([
      api<(Choice & { active: boolean })[]>("/modalities"),
      api<{ units: Choice[] }>("/units/reachable"),
    ]).then(
      ([m, u]) => alive && setOptions({ modalities: m.filter((x) => x.active), units: u.units }),
      () => undefined,
    );
    return () => {
      alive = false;
    };
  }, []);
  return options;
}

// Espelha TeacherDetail do OpenAPI (RF-PRO-02). Contato só vem aqui; classes e upcomingClasses vêm vazias até a Sprint 05.
export interface TeacherDetail {
  id: string;
  name: string;
  photoUrl: string | null;
  bio: string | null;
  email: string | null;
  phone: string | null;
  directoryConsent: boolean;
  consentDeclaredByManagement: boolean;
  status: "ACTIVE" | "INACTIVE";
  modalities: Teacher["modalities"];
  units: Teacher["units"];
  classes: { id: string; name: string; role: "HEAD" | "ASSISTANT" }[];
  upcomingClasses: { classId: string; name: string; startsAt: string }[];
  access: Teacher["access"];
}

export const grantAccess = (id: string, unitIds: string[]) => api<TeacherDetail>(`/teachers/${id}/access`, { method: "POST", body: { unitIds } });
export const resendTeacherInvite = (id: string) => api<void>(`/teachers/${id}/access/resend`, { method: "POST" });

type Loaded = { tick: number; id: string; teacher?: TeacherDetail; failure?: "forbidden" | "notFound" | "error" };

// Painel do professor. O servidor decide o alcance: 403 vira "sem permissão" e 404 "não encontrado".
export function useTeacher(id: string) {
  const [tick, setTick] = useState(0);
  const [done, setDone] = useState<Loaded>();
  useEffect(() => {
    let alive = true;
    api<TeacherDetail>(`/teachers/${id}`).then(
      (teacher) => alive && setDone({ tick, id, teacher }),
      (e) => alive && setDone({ tick, id, failure: e instanceof ApiError && e.status === 403 ? "forbidden" : e instanceof ApiError && e.status === 404 ? "notFound" : "error" }),
    );
    return () => {
      alive = false;
    };
  }, [id, tick]);
  const current = done?.tick === tick && done.id === id ? done : undefined;
  return {
    status: !current ? "loading" : (current.failure ?? "ready"),
    teacher: current?.teacher,
    retry: () => setTick((n) => n + 1),
    // Recarrega sem passar por "carregando" (depois de dar acesso ou reenviar).
    refresh: async () => setDone({ tick, id, teacher: await api<TeacherDetail>(`/teachers/${id}`) }),
  } as const;
}

// Espelham TeacherInput e TeacherOptions do OpenAPI (RF-PRO-03).
export interface TeacherInput {
  name: string;
  email: string | null;
  phone: string | null;
  bio: string | null;
  directoryConsent: boolean;
  consentDeclaredByManagement: boolean;
  unitIds: string[];
  modalities: { modalityId: string; levelId: string | null; degree: number | null }[];
}
export interface TeacherOptions {
  modalities: { id: string; name: string; graduationEnabled: boolean; levels: { id: string; name: string; maxDegree: number }[] }[];
}

export const createTeacher = (input: TeacherInput) => api<TeacherDetail>("/teachers", { method: "POST", body: input });
export const updateTeacher = (id: string, input: TeacherInput) => api<TeacherDetail>(`/teachers/${id}`, { method: "PUT", body: input });
export const uploadTeacherPhoto = (id: string, photo: Blob) => {
  const form = new FormData();
  form.append("file", photo, "foto.png");
  return api<TeacherDetail>(`/teachers/${id}/photo`, { method: "PUT", body: form });
};
export const removeTeacherPhoto = (id: string) => api<TeacherDetail>(`/teachers/${id}/photo`, { method: "DELETE" });

// Opções do formulário (modalidades ativas e níveis). Só carrega com o formulário aberto.
export function useTeacherOptions(enabled: boolean) {
  const [tick, setTick] = useState(0);
  const [done, setDone] = useState<{ tick: number; options?: TeacherOptions }>();
  useEffect(() => {
    if (!enabled) return;
    let alive = true;
    api<TeacherOptions>("/teachers/options").then(
      (options) => alive && setDone({ tick, options }),
      () => alive && setDone({ tick }),
    );
    return () => {
      alive = false;
    };
  }, [enabled, tick]);
  const current = done?.tick === tick ? done : undefined;
  return { status: !current ? "loading" : current.options ? "ready" : "error", options: current?.options, retry: () => setTick((n) => n + 1) } as const;
}

// Espelha TeacherDeactivationPreview do OpenAPI (RF-PRO-04): turmas que ficarão sem titular e se quem chama pode inativar.
export interface DeactivationPreview {
  headClasses: { id: string; name: string; role: "HEAD" | "ASSISTANT" }[];
  canDeactivate: boolean;
}
export const fetchDeactivationPreview = (id: string) => api<DeactivationPreview>(`/teachers/${id}/deactivation`);
export const deactivateTeacher = (id: string) => api<TeacherDetail>(`/teachers/${id}/deactivate`, { method: "POST" });
