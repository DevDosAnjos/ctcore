import { useState } from "react";
import { useTranslation } from "react-i18next";
import { Modal } from "@/components/Modal";
import { Button } from "@/components/ui/button";
import { Banner } from "@/components/ui/banner";
import { ApiError } from "@/lib/api";
import { deactivatePlan, type PlanRow } from "./usePlans";

// RN-PLA-06: desativar pede confirmação; nada é apagado e o plano pode ser ativado de novo. O servidor reconfere a permissão.
export function DeactivatePlanDialog({ plan, onClose, onDone }: { plan: PlanRow | null; onClose: () => void; onDone: () => void }) {
  if (!plan) return null;
  return <Body key={plan.id} plan={plan} onClose={onClose} onDone={onDone} />;
}

function Body({ plan, onClose, onDone }: { plan: PlanRow; onClose: () => void; onDone: () => void }) {
  const { t } = useTranslation();
  const [error, setError] = useState<string>();
  const [busy, setBusy] = useState(false);

  async function confirm() {
    setError(undefined);
    setBusy(true);
    try {
      await deactivatePlan(plan.id);
    } catch (e) {
      setBusy(false);
      setError(t(`plans.deactivateDialog.errors.${e instanceof ApiError && e.code === "FORBIDDEN" ? "FORBIDDEN" : "generic"}`));
      return;
    }
    setBusy(false);
    onClose();
    onDone();
  }

  return (
    <Modal open onOpenChange={(o) => !o && !busy && onClose()} title={t("plans.deactivateDialog.title", { name: plan.name })}>
      <div className="flex flex-col gap-3" aria-busy={busy}>
        <p className="text-sm">{t("plans.deactivateDialog.explain")}</p>
        {error && (
          <Banner tone="danger">
            {error}
          </Banner>
        )}
        <div className="flex flex-wrap justify-end gap-2">
          <Button type="button" variant="outline" className="min-h-11" onClick={onClose} disabled={busy}>
            {t("common.cancel")}
          </Button>
          <Button type="button" className="min-h-11" disabled={busy} onClick={() => void confirm()}>
            {t("plans.actions.deactivate")}
          </Button>
        </div>
      </div>
    </Modal>
  );
}
