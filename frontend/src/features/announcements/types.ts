// Espelha AnnouncementItem, AnnouncementList e AnnouncementType do OpenAPI (RF-COM-01).
export const ANNOUNCEMENT_TYPES = ["CANCELED_CLASS", "TEACHER_CHANGE", "SCHEDULE_CHANGE", "NO_CLASS_DAY", "GENERAL"] as const;
export type AnnouncementType = (typeof ANNOUNCEMENT_TYPES)[number];

export interface AnnouncementItem {
  id: string;
  date: string;
  target: { kind: "CLASS" | "MODALITY" | "UNIT" | "ORGANIZATION"; id: string | null; name: string | null };
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
