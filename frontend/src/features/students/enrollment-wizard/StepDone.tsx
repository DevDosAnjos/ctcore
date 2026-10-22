import { useState } from "react";
import { useTranslation } from "react-i18next";
import { formatMoney } from "@/components/fields/masks";
import { ErrorState, LoadingState } from "@/components/states";
import { Button } from "@/components/ui/button";
import { usePermissions } from "@/features/permissions/usePermissions";
import { RegisterPaymentDialog } from "@/features/payments/RegisterPaymentDialog";
import { formatDate } from "@/lib/format";
import type { EnrollmentCharge, EnrollmentConcluded } from "./types";

// Etapa 5 (RF-MAT-01, RN-MAT-06, RN-MAT-08): aluno Ativo, estado do convite e as cobranças criadas, pagas no balcão uma a uma (D14).
export function StepDone({ name, result, onPortal }: { name: string; result: EnrollmentConcluded; onPortal: () => void }) {
  const { t } = useTranslation();
  const k = "enrollmentWizard.done.";
  const { status, permissions, retry } = usePermissions();
  const [paying, setPaying] = useState<EnrollmentCharge>();
  const [paid, setPaid] = useState<Record<string, number>>({});
  const canPay = permissions?.COLLECTIONS === "FULL";
  const label = (c: EnrollmentCharge) => (c.type === "ENROLLMENT_FEE" ? t(`${k}fee`) : t(`${k}monthly`));
  return (
    <section className="flex flex-col gap-3">
      <h2 className="text-lg font-semibold">{t(`${k}title`, { name })}</h2>
      <p role="status">{t(`${k}active`)}</p>
      <p>{t(`${k}invite.${result.inviteStatus}`)}</p>
      <h3 className="font-medium">{t(`${k}charges`)}</h3>
      {result.charges.length === 0 && <p>{t(`${k}noCharges`)}</p>}
      {status === "loading" && result.charges.length > 0 && <LoadingState />}
      {status === "error" && <ErrorState onRetry={retry} />}
      {status === "ready" && !canPay && result.charges.length > 0 && <p>{t(`${k}forbidden`)}</p>}
      <ul aria-label={t(`${k}charges`)} className="flex flex-col gap-2">
        {result.charges.map((c) => (
          <li key={c.id} className="flex flex-wrap items-center justify-between gap-2 rounded-md border p-3 text-sm">
            <span>{t(`${k}chargeLine`, { label: label(c), value: formatMoney(c.amountCents), date: formatDate(c.dueOn), days: c.toleranceDays })}</span>
            {paid[c.id] !== undefined ? (
              <span>{t(`${k}paid`, { number: paid[c.id] })}</span>
            ) : (
              canPay && (
                <Button type="button" variant="outline" className="min-h-11" onClick={() => setPaying(c)}>
                  {t(`${k}payNow`)}
                </Button>
              )
            )}
          </li>
        ))}
      </ul>
      <div className="flex flex-wrap items-center gap-2">
        <Button type="button" className="min-h-11" onClick={onPortal}>
          {t(`${k}portal`)}
        </Button>
      </div>
      {paying && (
        <RegisterPaymentDialog
          chargeId={paying.id}
          label={label(paying)}
          amountCents={paying.amountCents}
          onClose={() => setPaying(undefined)}
          onPaid={(p) => setPaid((old) => ({ ...old, [paying.id]: p.receipt.number }))}
        />
      )}
    </section>
  );
}
