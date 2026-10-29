import { useState } from "react";
import { useTranslation } from "react-i18next";
import { Modal } from "@/components/Modal";
import { Banner } from "@/components/ui/banner";
import { Button } from "@/components/ui/button";
import { failureOf, NO_FAILURE, type Failure } from "./CancelChargeDialog";
import type { ReminderResult } from "./types";
import { sendReminders } from "./useChargeActions";

export type ReminderTarget = { id: string; label: string };

// RF-COB-06: confirma o envio do lembrete por e-mail e mostra o resultado de cada cobrança (texto, nunca só cor). Fechar depois do envio avisa a tela (`onDone`) para recarregar.
export function SendReminderDialog({ targets, onClose, onDone }: { targets: ReminderTarget[]; onClose: () => void; onDone: () => void }) {
  const { t } = useTranslation();
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState<Failure>(NO_FAILURE);
  const [results, setResults] = useState<ReminderResult[]>();

  async function submit() {
    if (busy) return;
    setBusy(true);
    setErr(NO_FAILURE);
    try {
      setResults(await sendReminders(targets.map((x) => x.id)));
    } catch (x) {
      setErr(failureOf(x, t));
    } finally {
      setBusy(false);
    }
  }
  const close = () => {
    if (busy) return;
    if (results) onDone();
    onClose();
  };
  const label = (id: string) => targets.find((x) => x.id === id)?.label ?? id;

  return (
    <Modal open onOpenChange={(o) => !o && close()} title={t("charges.reminder.title")}>
      <div className="flex flex-col gap-3">
        {results ? (
          <ul className="flex flex-col gap-2 text-sm">
            {results.map((r) => (
              <li key={r.chargeId} className="flex flex-wrap justify-between gap-2 rounded-md border p-2">
                <span className="min-w-0 break-words">{label(r.chargeId)}</span>
                <span className="font-medium">{t(`charges.reminder.result.${r.result}`)}</span>
              </li>
            ))}
          </ul>
        ) : (
          <>
            <p>{t("charges.reminder.summary", { count: targets.length })}</p>
            <p className="text-sm text-muted-foreground">{t("charges.reminder.effect")}</p>
          </>
        )}
        {err.form && (
          <Banner tone="danger">{err.form}</Banner>
        )}
        <div className="flex flex-wrap justify-end gap-2">
          <Button type="button" variant="outline" className="min-h-11" onClick={close} disabled={busy}>
            {results ? t("common.close") : t("common.cancel")}
          </Button>
          {!results && (
            <Button type="button" className="min-h-11" onClick={submit} disabled={busy}>
              {t("charges.reminder.submit")}
            </Button>
          )}
        </div>
      </div>
    </Modal>
  );
}
