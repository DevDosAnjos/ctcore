import { useId, useState } from "react";
import { useTranslation } from "react-i18next";
import { formatCurrency, formatDate } from "@/lib/format";
import { combine, ModalityDialog, PickSelect } from "./ModalityPickers";
import type { EndReason, EnrollmentModality } from "./types";
import { endModality, useEndPreview } from "./useStudentRecord";

const REASONS: EndReason[] = ["CITY_CHANGE", "FINANCIAL", "SCHEDULE", "INJURY", "DISSATISFACTION", "OTHER"];

// RF-MAT-03 (RN-MAT-17): encerra a modalidade com motivo e observação opcional; a lista de cobranças em aberto vem do servidor (vazia até a Sprint 08).
export function EndModalityDialog({ studentId, studentName, row, onClose, onDone }: { studentId: string; studentName: string; row: EnrollmentModality; onClose: () => void; onDone: () => void }) {
  const { t } = useTranslation();
  const k = "students.record.enrollment.end.";
  const noteId = useId();
  const preview = useEndPreview(studentId, row.id);
  const [reason, setReason] = useState("");
  const [note, setNote] = useState("");
  const { state, retry } = combine(preview);
  const charges = preview.data?.openCharges ?? [];
  return (
    <ModalityDialog
      title={t(`${k}title`)}
      effect={t(`${k}effect`)}
      state={state}
      retry={retry}
      submitLabel={t(`${k}submit`)}
      canSubmit={!!reason}
      work={() => endModality(studentId, row.id, { reason, note: note.trim() || undefined })}
      onClose={onClose}
      onDone={onDone}
    >
      {(errors) => (
        <>
          <p className="text-sm">{t(`${k}who`, { student: studentName, modality: row.modality.name, unit: row.unit.name })}</p>
          <div role="group" aria-labelledby={`${noteId}-charges`} className="flex flex-col gap-1">
            <p id={`${noteId}-charges`} className="text-sm font-medium">
              {t(`${k}charges`)}
            </p>
            {charges.length === 0 ? (
              <p className="text-sm text-muted-foreground">{t(`${k}noCharges`)}</p>
            ) : (
              <ul className="flex flex-col gap-1 text-sm">
                {charges.map((c) => (
                  <li key={c.id}>{t(`${k}chargeLine`, { due: formatDate(c.dueOn), amount: formatCurrency(c.amountCents), status: c.status })}</li>
                ))}
              </ul>
            )}
          </div>
          <PickSelect label={t(`${k}reason`)} value={reason} onChange={setReason} options={REASONS.map((r) => ({ id: r, name: t(`${k}reasons.${r}`) }))} error={errors.reason} />
          <div>
            <label htmlFor={noteId} className="block text-sm font-medium">
              {t(`${k}note`)}
            </label>
            <textarea
              id={noteId}
              className="w-full rounded-md border bg-background p-2"
              rows={3}
              maxLength={500}
              value={note}
              onChange={(e) => setNote(e.target.value)}
              aria-invalid={errors.note ? true : undefined}
              aria-describedby={errors.note ? `${noteId}-erro` : undefined}
            />
            {errors.note && (
              <p id={`${noteId}-erro`} className="text-sm text-destructive">
                {errors.note}
              </p>
            )}
          </div>
        </>
      )}
    </ModalityDialog>
  );
}
