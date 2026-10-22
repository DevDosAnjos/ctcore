import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { formatMoney } from "@/components/fields/masks";
import { ErrorState, ForbiddenState, LoadingState } from "@/components/states";
import { ApiError } from "@/lib/api";
import { formatDate } from "@/lib/format";
import { previewEnrollment } from "./api";
import type { ConclusionRequest, EnrollmentPreview } from "./types";

type State = { kind: "loading" } | { kind: "forbidden" } | { kind: "error" } | { kind: "invalid"; error: ApiError } | { kind: "ready"; preview: EnrollmentPreview };

// Etapa 4 (RF-MAT-01, RN-MAT-03): o resumo vem da prévia do servidor, sem gravar nada. Recusa de regra (422) volta para a etapa do campo.
export function StepSummary({ studentId, request, onInvalid, onReady }: { studentId: string; request: ConclusionRequest; onInvalid: (e: ApiError) => void; onReady: (p: EnrollmentPreview) => void }) {
  const { t } = useTranslation();
  const k = "enrollmentWizard.summary.";
  const [state, setState] = useState<State>({ kind: "loading" });
  const [tick, setTick] = useState(0);
  useEffect(() => {
    let alive = true;
    previewEnrollment(studentId, request).then(
      (preview) => {
        if (!alive) return;
        setState({ kind: "ready", preview });
        onReady(preview);
      },
      (e) => {
        if (!alive) return;
        if (e instanceof ApiError && (e.status === 422 || e.status === 400 || e.status === 409)) onInvalid(e);
        else setState({ kind: e instanceof ApiError && e.status === 403 ? "forbidden" : "error" });
      },
    );
    return () => {
      alive = false;
    };
    // O pedido é refeito só quando a pessoa chega à etapa ou tenta de novo.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [tick]);
  if (state.kind === "loading") return <LoadingState />;
  if (state.kind === "forbidden") return <ForbiddenState />;
  if (state.kind === "error" || state.kind === "invalid") return <ErrorState
        onRetry={() => {
          setState({ kind: "loading" });
          setTick((n) => n + 1);
        }}
      />;
  const { preview } = state;
  return (
    <div className="flex flex-col gap-4">
      {preview.openDebt.count > 0 && (
        <p role="status" className="rounded-md border p-3 text-sm font-medium">
          {t(`${k}openDebt`, { count: preview.openDebt.count, value: formatMoney(preview.openDebt.totalCents) })}
        </p>
      )}
      <section aria-label={t(`${k}modalities`)}>
        <h2 className="font-medium">{t(`${k}modalities`)}</h2>
        <ul className="flex flex-col gap-2">
          {preview.modalities.map((m, i) => (
            <li key={i} className="rounded-md border p-3 text-sm">
              <p className="font-medium">{m.planName}</p>
              <p>{t(`${k}planValue`, { value: formatMoney(m.planCents) })}</p>
              {m.discountCents > 0 && <p>{t(`${k}discount`, { value: formatMoney(m.discountCents) })}</p>}
              <p>{t(`${k}monthly`, { value: formatMoney(m.monthlyCents) })}</p>
            </li>
          ))}
        </ul>
      </section>
      <section aria-label={t(`${k}units`)} className="flex flex-col gap-2">
        <h2 className="font-medium">{t(`${k}units`)}</h2>
        {preview.units.map((u) => (
          <div key={u.unitId} className="rounded-md border p-3 text-sm">
            <p className="font-medium">{u.unitName}</p>
            <p>{t(`${k}dueDay`, { day: u.dueDay })}</p>
            <p>{t(`${k}firstDue`, { date: formatDate(u.firstDueOn), days: u.toleranceDays })}</p>
            <p>{u.feeExempt ? t(`${k}feeExempt`, { reason: u.feeExemptReason }) : u.feeCents === 0 ? t(`${k}feeNone`) : t(`${k}fee`, { value: formatMoney(u.feeCents) })}</p>
            <p className="font-medium">{t(`${k}firstTotal`, { value: formatMoney(u.firstChargeCents) })}</p>
            <p>{t(`${k}secondDue`, { date: formatDate(u.secondDueOn) })}</p>
          </div>
        ))}
      </section>
      <p className="text-sm text-muted-foreground">{t(`${k}noCharge`)}</p>
    </div>
  );
}
