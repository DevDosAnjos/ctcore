import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { Modal } from "@/components/Modal";
import { ErrorState, LoadingState } from "@/components/states";
import { Button } from "@/components/ui/button";
import { ApiError } from "@/lib/api";
import { deactivateTeacher, fetchDeactivationPreview, type DeactivationPreview, type TeacherDetail } from "./useTeachers";

const KNOWN = ["FORBIDDEN", "CONFLICT"];

// RF-PRO-04: mostra as turmas que ficarão sem titular e confirma; o servidor reconfere alcance e situação.
export function DeactivateTeacherDialog({ teacher, onClose, onDone }: { teacher: TeacherDetail | null; onClose: () => void; onDone: () => void }) {
  if (!teacher) return null;
  return <Body key={teacher.id} teacher={teacher} onClose={onClose} onDone={onDone} />;
}

function Body({ teacher, onClose, onDone }: { teacher: TeacherDetail; onClose: () => void; onDone: () => void }) {
  const { t } = useTranslation();
  const [tick, setTick] = useState(0);
  const [loaded, setLoaded] = useState<{ tick: number; preview?: DeactivationPreview }>();
  const [form, setForm] = useState<string>();
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    let alive = true;
    fetchDeactivationPreview(teacher.id).then(
      (preview) => alive && setLoaded({ tick, preview }),
      () => alive && setLoaded({ tick }),
    );
    return () => {
      alive = false;
    };
  }, [teacher.id, tick]);
  const current = loaded?.tick === tick ? loaded : undefined;
  const preview = current?.preview;

  async function confirm() {
    setForm(undefined);
    setBusy(true);
    try {
      await deactivateTeacher(teacher.id);
    } catch (e) {
      setForm(t(`teachers.panel.deactivateDialog.errors.${e instanceof ApiError && KNOWN.includes(e.code) ? e.code : "generic"}`));
      setBusy(false);
      return;
    }
    setBusy(false);
    onClose();
    onDone();
  }

  return (
    <Modal open onOpenChange={(o) => !o && !busy && onClose()} title={t("teachers.panel.deactivateDialog.title", { name: teacher.name })}>
      <div className="flex flex-col gap-3" aria-busy={busy || !current}>
        {!current && <LoadingState />}
        {current && !preview && <ErrorState onRetry={() => setTick((n) => n + 1)} />}
        {preview && (
          <>
            {preview.headClasses.length > 0 ? (
              <>
                <p className="text-sm">{t("teachers.panel.deactivateDialog.withoutHead")}</p>
                <ul className="list-disc pl-5 text-sm">
                  {preview.headClasses.map((c) => (
                    <li key={c.id} className="break-words">
                      {c.name}
                    </li>
                  ))}
                </ul>
              </>
            ) : (
              <p className="text-sm">{t("teachers.panel.deactivateDialog.noClasses")}</p>
            )}
            {teacher.access === "PENDING" || teacher.access === "ACTIVE" ? <p className="text-sm">{t("teachers.panel.deactivateDialog.accessEnds")}</p> : null}
            {!preview.canDeactivate && (
              <p role="alert" className="text-sm text-destructive">
                {t("teachers.panel.deactivateDialog.outOfReach")}
              </p>
            )}
          </>
        )}
        {form && (
          <p role="alert" className="text-sm text-destructive">
            {form}
          </p>
        )}
        <div className="flex flex-wrap justify-end gap-2">
          <Button type="button" variant="outline" className="min-h-11" onClick={onClose} disabled={busy}>
            {t("common.cancel")}
          </Button>
          <Button type="button" className="min-h-11" disabled={busy || !preview?.canDeactivate} onClick={() => void confirm()}>
            {t("teachers.panel.deactivate")}
          </Button>
        </div>
      </div>
    </Modal>
  );
}
