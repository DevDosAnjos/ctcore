import { useEffect, useState } from "react";
import { api, ApiError } from "@/lib/api";

// Espelham UnitDetail e UnitUpdate do OpenAPI (RF-ORG-04, docs/openapi.yaml). Valores em centavos.
export interface UnitChoice {
  id: string;
  name: string;
  shortName: string;
  city: string | null;
  active: boolean;
}
export const FEE_KEYS = ["cash", "pix", "bankSlip", "creditCard", "debitCard", "automaticDebit", "manualPix", "terminalCard", "transfer"] as const;
export type FeeKey = (typeof FEE_KEYS)[number];
export const TEACHER_KEYS = ["announcements", "agendaEdit", "studentContacts", "trialClasses"] as const;
export type TeacherKey = (typeof TEACHER_KEYS)[number];

export interface UnitSettingsData {
  dueDays: number[];
  enrollmentFeeEnabled: boolean;
  enrollmentFeeCents: number;
  feesCents: Record<FeeKey, number>;
  pixKey: string | null;
  paymentAccount: "CONNECTED" | "NOT_CONNECTED";
  reminderDays: number[];
  teacherPermissions: Record<TeacherKey, boolean>;
  directoryEnabled: boolean;
  trialLinkEnabled: boolean;
  selfRegistrationLinkEnabled: boolean;
}
export interface UnitDetail {
  id: string;
  name: string;
  shortName: string;
  postalCode: string | null;
  address: string | null;
  city: string | null;
  latitude: number | null;
  longitude: number | null;
  phone: string | null;
  email: string | null;
  whatsapp: string | null;
  cnpj: string | null;
  active: boolean;
  settings: UnitSettingsData;
}
export type UnitUpdate = Omit<UnitDetail, "id" | "shortName" | "active" | "settings"> & {
  settings: Omit<UnitSettingsData, "paymentAccount" | "enrollmentFeeCents"> & { enrollmentFeeCents: number | null };
};

type Status = "loading" | "ready" | "forbidden" | "error";
const statusOf = (e: unknown): Status => (e instanceof ApiError && (e.status === 403 || e.status === 404) ? "forbidden" : "error");

// Leitura e gravação de uma unidade. Erros da gravação sobem (ApiError) para a tela apontar o campo.
export function useUnitDetail(id: string) {
  const [tick, setTick] = useState(0);
  const [done, setDone] = useState<{ tick: number; id: string; detail?: UnitDetail; status?: Status }>();
  useEffect(() => {
    let alive = true;
    api<UnitDetail>(`/units/${id}`).then(
      (detail) => alive && setDone({ tick, id, detail }),
      (e) => alive && setDone({ tick, id, status: statusOf(e) }),
    );
    return () => {
      alive = false;
    };
  }, [id, tick]);
  const current = done?.tick === tick && done.id === id ? done : undefined;
  const status: Status = !current ? "loading" : current.detail ? "ready" : (current.status ?? "error");
  return {
    status,
    detail: current?.detail,
    retry: () => setTick((n) => n + 1),
    save: async (body: UnitUpdate) => {
      const detail = await api<UnitDetail>(`/units/${id}`, { method: "PUT", body });
      setDone({ tick, id, detail });
      return detail;
    },
  } as const;
}
