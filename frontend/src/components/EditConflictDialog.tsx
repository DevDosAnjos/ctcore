import type { RefObject } from "react";
import { useTranslation } from "react-i18next";
import { LastChange, type LastChangeValue } from "@/components/LastChange";
import { Modal } from "@/components/Modal";
import { LoadingState } from "@/components/states";
import { Button } from "@/components/ui/button";
import type { ConflictState } from "@/lib/useEditConflict";

export interface Difference {
  label: string;
  mine: string;
  current: string;
}

// RNF-15: quem salva depois de outra pessoa vê quem alterou, o que difere e escolhe Recarregar ou Sobrescrever.
export function EditConflictDialog({
  state,
  differences,
  busy,
  onRetry,
  onReload,
  onOverwrite,
  onClose,
  returnFocusRef,
}: {
  state: ConflictState<{ lastChange: LastChangeValue | null }> | null;
  differences: Difference[];
  busy: boolean;
  onRetry: () => void;
  onReload: () => void;
  onOverwrite: () => void;
  onClose: () => void;
  returnFocusRef?: RefObject<HTMLElement | null>;
}) {
  const { t } = useTranslation();
  return (
    <Modal open={state !== null} onOpenChange={(o) => !o && onClose()} title={t("editConflict.title")} returnFocusRef={returnFocusRef}>
      {state?.status === "loading" && <LoadingState text={t("editConflict.loading")} />}
      {state?.status === "error" && (
        <div role="alert" className="flex flex-col items-start gap-2">
          <p>{t("editConflict.error")}</p>
          <Button className="min-h-11" onClick={onRetry}>
            {t("states.retry")}
          </Button>
        </div>
      )}
      {state?.status === "ready" && (
        <div className="flex flex-col gap-3">
          <p>{t("editConflict.intro")}</p>
          <LastChange value={state.current.lastChange} />
          {differences.length === 0 ? (
            <p>{t("editConflict.noDifferences")}</p>
          ) : (
            <section aria-labelledby="edit-conflict-diff">
              <h3 id="edit-conflict-diff" className="mb-1 text-sm font-semibold">
                {t("editConflict.differences")}
              </h3>
              <ul className="flex flex-col gap-2">
                {differences.map((d) => (
                  <li key={d.label} className="rounded-md border p-2 text-sm">
                    <p className="font-medium">{d.label}</p>
                    <p className="break-words">
                      <span className="text-muted-foreground">{t("editConflict.mine")}: </span>
                      {d.mine}
                    </p>
                    <p className="break-words">
                      <span className="text-muted-foreground">{t("editConflict.currentValue")}: </span>
                      {d.current}
                    </p>
                  </li>
                ))}
              </ul>
            </section>
          )}
          <div className="flex flex-wrap gap-2">
            <Button variant="outline" className="min-h-11" disabled={busy} onClick={onReload}>
              {t("editConflict.reload")}
            </Button>
            <Button className="min-h-11" disabled={busy} onClick={onOverwrite}>
              {busy ? t("editConflict.overwriting") : t("editConflict.overwrite")}
            </Button>
          </div>
          <p className="text-sm text-muted-foreground">{t("editConflict.help")}</p>
        </div>
      )}
    </Modal>
  );
}
