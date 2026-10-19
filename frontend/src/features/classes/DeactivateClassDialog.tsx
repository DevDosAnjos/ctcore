import { useState } from "react";
import { useTranslation } from "react-i18next";
import { Modal } from "@/components/Modal";
import { Button } from "@/components/ui/button";
import { ApiError } from "@/lib/api";
import { deactivateClass, type ClassDetail } from "./useClasses";

// RN-TUR-03: com aluno que tem a turma como principal, mostra a recusa com a contagem e não oferece confirmar; o servidor reconfere.
export function DeactivateClassDialog({ klass, onClose, onDone }: { klass: ClassDetail | null; onClose: () => void; onDone: () => void }) {
  if (!klass) return null;
  return <Body key={klass.id} klass={klass} onClose={onClose} onDone={onDone} />;
}

function Body({ klass, onClose, onDone }: { klass: ClassDetail; onClose: () => void; onDone: () => void }) {
  const { t } = useTranslation();
  const [blocked, setBlocked] = useState(klass.deactivation.blockingStudents);
  const [error, setError] = useState<string>();
  const [busy, setBusy] = useState(false);

  async function confirm() {
    setError(undefined);
    setBusy(true);
    try {
      await deactivateClass(klass.id);
    } catch (e) {
      setBusy(false);
      if (e instanceof ApiError && e.code === "HAS_PRIMARY_STUDENTS") setBlocked(Number(e.fields[0]?.message) || 1);
      else setError(t(`classes.panel.deactivateDialog.errors.${e instanceof ApiError && ["FORBIDDEN", "CONFLICT"].includes(e.code) ? e.code : "generic"}`));
      return;
    }
    setBusy(false);
    onClose();
    onDone();
  }

  return (
    <Modal open onOpenChange={(o) => !o && !busy && onClose()} title={t("classes.panel.deactivateDialog.title", { name: klass.name })}>
      <div className="flex flex-col gap-3" aria-busy={busy}>
        {blocked > 0 ? (
          <p role="alert" className="text-sm text-destructive">
            {t("classes.panel.deactivateDialog.blocked", { count: blocked })}
          </p>
        ) : (
          <p className="text-sm">{t("classes.panel.deactivateDialog.explain")}</p>
        )}
        {error && (
          <p role="alert" className="text-sm text-destructive">
            {error}
          </p>
        )}
        <div className="flex flex-wrap justify-end gap-2">
          <Button type="button" variant="outline" className="min-h-11" onClick={onClose} disabled={busy}>
            {blocked > 0 ? t("common.close") : t("common.cancel")}
          </Button>
          {blocked === 0 && (
            <Button type="button" className="min-h-11" disabled={busy} onClick={() => void confirm()}>
              {t("classes.panel.deactivate")}
            </Button>
          )}
        </div>
      </div>
    </Modal>
  );
}
