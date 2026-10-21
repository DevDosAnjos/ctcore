import { useEffect, useState } from "react";
import { api, ApiError } from "@/lib/api";
import type { BenefitRow } from "./useBenefits";

export type BenefitType = "PERCENT" | "AMOUNT";

// Espelha BenefitPreview do OpenAPI (docs/openapi.yaml).
export interface BenefitPreview {
  plan: { id: string; name: string; valueCents: number };
  current: BenefitRow | null;
  newMonthlyValueCents: number;
  effectiveFrom: string;
  replaces: boolean;
  high: boolean;
}

export interface GrantBody {
  studentId: string;
  modalityRowId: string;
  type: BenefitType;
  value: number;
  reason: string;
  decidedBy: string;
  validUntil: string | null;
  replaceExisting: boolean;
}

export const grantBenefit = (body: GrantBody) =>
  api<BenefitRow>("/benefits", { method: "POST", body });

type PreviewState =
  | { status: "idle" | "loading" | "error" | "forbidden"; data?: undefined }
  | { status: "ready"; data: BenefitPreview };

// RF-BEN-02: resumo do servidor para a combinação escolhida; sem aluno, modalidade ou valor, não pede nada.
export function useBenefitPreview(
  studentId: string,
  modalityRowId: string,
  type: BenefitType,
  value: number | null,
) {
  const [tick, setTick] = useState(0);
  const key =
    studentId && modalityRowId && value !== null
      ? `${studentId}|${modalityRowId}|${type}|${value}|${tick}`
      : null;
  const [done, setDone] = useState<{ key: string; state: PreviewState }>();
  useEffect(() => {
    if (key === null || value === null) return;
    let alive = true;
    api<BenefitPreview>("/benefits/preview", {
      method: "POST",
      body: { studentId, modalityRowId, type, value },
    }).then(
      (data) => alive && setDone({ key, state: { status: "ready", data } }),
      (e) =>
        alive &&
        setDone({
          key,
          state: {
            status:
              e instanceof ApiError && e.status === 403 ? "forbidden" : "error",
          },
        }),
    );
    return () => {
      alive = false;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [key]);
  const state: PreviewState =
    key === null
      ? { status: "idle" }
      : done?.key === key
        ? done.state
        : { status: "loading" };
  return { ...state, reload: () => setTick((n) => n + 1) };
}
