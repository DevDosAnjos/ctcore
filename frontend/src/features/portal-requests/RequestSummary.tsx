import { useTranslation } from "react-i18next";
import type { PortalRequest } from "./types";

// Uma linha com o que foi pedido, igual na lista da gestão e no portal.
export function requestSummary(t: (key: string, o?: Record<string, unknown>) => string, r: PortalRequest): string {
  return t(`portalRequests.summary.${r.kind}`, {
    modality: r.modality ?? "",
    targetClass: r.targetClass ?? "",
    targetPlan: r.targetPlan ?? "",
    targetUnit: r.targetUnit ?? "",
    reason: r.reason ? t(`students.record.enrollment.end.reasons.${r.reason}`) : "",
  });
}

export function RequestSummary({ request }: { request: PortalRequest }) {
  const { t } = useTranslation();
  return <>{requestSummary(t, request)}</>;
}
