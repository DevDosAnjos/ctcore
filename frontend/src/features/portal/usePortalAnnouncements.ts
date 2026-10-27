import { api } from "@/lib/api";
import { useResource } from "@/features/students/useStudentRecord";
import { refreshPortalShell } from "./usePortalShell";

// Espelham PortalAnnouncements e PortalAnnouncementDetail do OpenAPI (docs/openapi.yaml). Sem contato do professor, presença nem dado financeiro (RN-POR-06).
export type AnnouncementFilter = "ALL" | "UNREAD" | "MODALITY";
export interface AnnouncementItem {
  id: string;
  title: string;
  type: string;
  sentAt: string;
  pinned: boolean;
  unread: boolean;
  destination: { kind: "ORGANIZATION" | "UNIT" | "MODALITY" | "CLASS" | "CLASSES"; names: string[] };
  author: string;
}
export interface AnnouncementDetail extends AnnouncementItem {
  body: string;
  imageUrl: string | null;
}
export interface PortalAnnouncementsData {
  reduced: boolean;
  students: { id: string; name: string; self: boolean }[];
  studentId: string;
  filter: AnnouncementFilter;
  modalityId: string | null;
  modalities: { id: string; name: string }[];
  unreadCount: number;
  items: AnnouncementItem[];
  page: number;
  size: number;
  total: number;
}

/** Lista de avisos do aluno escolhido (nulo: o primeiro cartão). Sem cópia local: o estado fica na URL e no servidor (D8). */
export function usePortalAnnouncements(studentId: string | null, filter: AnnouncementFilter, modalityId: string | null, page: number) {
  const q = new URLSearchParams();
  if (studentId) q.set("studentId", studentId);
  if (filter === "UNREAD") q.set("filter", filter);
  if (filter === "MODALITY" && modalityId) {
    q.set("filter", filter);
    q.set("modalityId", modalityId);
  }
  if (page > 1) q.set("page", String(page));
  const s = q.toString();
  return useResource<PortalAnnouncementsData>(`/portal/announcements${s ? `?${s}` : ""}`);
}

/** Abrir grava a leitura do aluno; o sino é relido. */
export async function openPortalAnnouncement(id: string, studentId: string | null) {
  const detail = await api<AnnouncementDetail>(`/portal/announcements/${id}/open${studentId ? `?studentId=${studentId}` : ""}`, { method: "POST" });
  refreshPortalShell();
  return detail;
}

/** Marca de "limpo" da conta (não é leitura); o sino é relido. */
export async function markAllPortalAnnouncementsRead() {
  const done = await api<{ dismissed: number }>("/portal/announcements/read-all", { method: "POST" });
  refreshPortalShell();
  return done.dismissed;
}
