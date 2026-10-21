import { useId } from "react";
import { useTranslation } from "react-i18next";
import { Button } from "@/components/ui/button";
import type { EnrollmentConcluded } from "./types";

// Etapa 5 (RF-MAT-01, RN-MAT-08): aluno Ativo e estado do convite. "Registrar pagamento agora" fica desabilitada até a #75 (D3).
export function StepDone({ name, result, onPortal }: { name: string; result: EnrollmentConcluded; onPortal: () => void }) {
  const { t } = useTranslation();
  const k = "enrollmentWizard.done.";
  const hint = useId();
  return (
    <section className="flex flex-col gap-3">
      <h2 className="text-lg font-semibold">{t(`${k}title`, { name })}</h2>
      <p role="status">{t(`${k}active`)}</p>
      <p>{t(`${k}invite.${result.inviteStatus}`)}</p>
      <div className="flex flex-wrap items-center gap-2">
        <Button type="button" variant="outline" className="min-h-11" disabled aria-describedby={hint}>
          {t(`${k}payNow`)}
        </Button>
        <span id={hint} className="text-sm text-muted-foreground">
          {t(`${k}payNowHint`)}
        </span>
        <Button type="button" className="min-h-11" onClick={onPortal}>
          {t(`${k}portal`)}
        </Button>
      </div>
    </section>
  );
}
