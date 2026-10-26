import { useId, useState } from "react";
import { useTranslation } from "react-i18next";
import { formatCurrency, formatDate } from "@/lib/format";
import { combine, ModalityDialog, PickSelect } from "./ModalityPickers";
import type { ChargeDecision, EndReason, EnrollmentModality } from "./types";
import { endModality, useEndPreview } from "./useStudentRecord";

const REASONS: EndReason[] = ["CITY_CHANGE", "FINANCIAL", "SCHEDULE", "INJURY", "DISSATISFACTION", "OTHER"];

type Choice = { cancel: boolean; reason: string };

// RF-MAT-03 (RN-MAT-17): encerra a modalidade com motivo e observação opcional; as cobranças em aberto vêm do servidor e cada uma é mantida ou cancelada (com motivo).
export function EndModalityDialog({ studentId, studentName, row, onClose, onDone }: { studentId: string; studentName: string; row: EnrollmentModality; onClose: () => void; onDone: () => void }) {
  const { t } = useTranslation();
  const k = "students.record.enrollment.end.";
  const noteId = useId();
  const preview = useEndPreview(studentId, row.id);
  const [reason, setReason] = useState("");
  const [note, setNote] = useState("");
  const [choices, setChoices] = useState<Record<string, Choice>>({});
  const { state, retry } = combine(preview);
  const all = preview.data?.openCharges ?? [];
  // RN-MAT-18 (D6): com pedido de encerramento aberto, o servidor cancela as cobranças com vencimento depois dele; a janela só as mostra.
  const automatic = all.filter((c) => c.afterRequest);
  const charges = all.filter((c) => !c.afterRequest);
  const choose = (id: string, change: Partial<Choice>) => setChoices((c) => ({ ...c, [id]: { ...(c[id] ?? { cancel: false, reason: "" }), ...change } }));
  const decisions: ChargeDecision[] = charges.map((c) => (choices[c.id]?.cancel ? { chargeId: c.id, action: "CANCEL", reason: choices[c.id].reason.trim() } : { chargeId: c.id, action: "KEEP" }));
  return (
    <ModalityDialog
      title={t(`${k}title`)}
      effect={t(`${k}effect`)}
      state={state}
      retry={retry}
      submitLabel={t(`${k}submit`)}
      canSubmit={!!reason && decisions.every((d) => d.action === "KEEP" || !!d.reason)}
      work={() => endModality(studentId, row.id, { reason, note: note.trim() || undefined, ...(decisions.length ? { charges: decisions } : {}) })}
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
            {all.length === 0 && <p className="text-sm text-muted-foreground">{t(`${k}noCharges`)}</p>}
            {automatic.length > 0 && (
              <>
                <p className="text-sm text-muted-foreground">{t(`${k}afterRequest`)}</p>
                <ul className="flex flex-col gap-1 text-sm">
                  {automatic.map((c) => (
                    <li key={c.id} className="rounded-md border p-2">
                      {t(`${k}chargeLine`, { due: formatDate(c.dueOn), amount: formatCurrency(c.amountCents), status: t(`${k}autoCanceled`) })}
                    </li>
                  ))}
                </ul>
              </>
            )}
            {charges.length > 0 && (
              <ul className="flex flex-col gap-2 text-sm">
                {charges.map((c) => {
                  const choice = choices[c.id];
                  const line = t(`${k}chargeLine`, { due: formatDate(c.dueOn), amount: formatCurrency(c.amountCents), status: t(`charges.status.${c.status}`) });
                  return (
                    <li key={c.id}>
                      <fieldset className="flex flex-col gap-1 rounded-md border p-2">
                        <legend className="px-1">{line}</legend>
                        <div className="flex flex-wrap gap-4">
                          <label className="flex min-h-11 items-center gap-2">
                            <input type="radio" name={`${noteId}-${c.id}`} checked={!choice?.cancel} onChange={() => choose(c.id, { cancel: false })} />
                            {t(`${k}keep`)}
                          </label>
                          <label className="flex min-h-11 items-center gap-2">
                            <input type="radio" name={`${noteId}-${c.id}`} checked={!!choice?.cancel} onChange={() => choose(c.id, { cancel: true })} />
                            {t(`${k}cancel`)}
                          </label>
                        </div>
                        {choice?.cancel && (
                          <input
                            className="min-h-11 rounded-md border bg-background px-3"
                            aria-label={t(`${k}cancelReason`, { line })}
                            maxLength={200}
                            value={choice.reason}
                            onChange={(e) => choose(c.id, { reason: e.target.value })}
                          />
                        )}
                      </fieldset>
                    </li>
                  );
                })}
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
