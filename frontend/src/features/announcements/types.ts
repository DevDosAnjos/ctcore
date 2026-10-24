// Espelha AnnouncementItem, AnnouncementList e AnnouncementType do OpenAPI (RF-COM-01).
export const ANNOUNCEMENT_TYPES = ["CANCELED_CLASS", "TEACHER_CHANGE", "SCHEDULE_CHANGE", "NO_CLASS_DAY", "GENERAL"] as const;
export type AnnouncementType = (typeof ANNOUNCEMENT_TYPES)[number];

export interface AnnouncementItem {
  id: string;
  date: string;
  target: { kind: "CLASS" | "CLASSES" | "MODALITY" | "UNIT" | "ORGANIZATION"; id: string | null; name: string | null };
  title: string;
  type: AnnouncementType;
  author: string;
  status: "SCHEDULED" | "SENT";
  recipients: number | null;
  reads: number;
}

export interface AnnouncementList {
  items: AnnouncementItem[];
  page: number;
  size: number;
  total: number;
}

// Espelham AnnouncementTargets, AnnouncementDraft, AnnouncementPreview, AnnouncementCreated e AnnouncementImage (RF-COM-02).
export type AnnouncementChannel = "PORTAL" | "EMAIL" | "WHATSAPP";

export interface AnnouncementTargets {
  units: { id: string; name: string }[];
  classes: { id: string; name: string; unitId: string; modalityId: string; groupLink: string | null }[];
  modalities: { id: string; name: string; unitId: string }[];
  organization: boolean;
}

export interface AnnouncementDraft {
  targetKind: "CLASS" | "MODALITY" | "UNIT" | "ORGANIZATION";
  classId?: string;
  modalityId?: string;
  unitId?: string;
  type?: AnnouncementType;
  title?: string;
  body?: string;
  channels: AnnouncementChannel[];
  imageName?: string;
  pinnedUntil?: string;
  sendAt?: string;
  noClassDayId?: string;
}

export interface AnnouncementPreview {
  recipients: number;
  portal: number;
  email: number;
  whatsapp: { mode: "GROUP" | "CONVERSATION"; groupLink: string | null } | null;
  channels: AnnouncementChannel[];
}

export interface AnnouncementCreated {
  id: string;
  status: "SCHEDULED" | "SENT";
  recipients: number | null;
}

export interface AnnouncementImage {
  name: string;
  url: string;
}

// Espelha AnnouncementTemplates do OpenAPI (RN-COM-12): o título e o texto em uso de cada tipo, com {campo} por preencher.
export type AnnouncementTemplates = Record<AnnouncementType, { title: string; body: string }>;
