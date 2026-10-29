import { useId, useState } from "react";
import { useTranslation } from "react-i18next";
import { Textarea } from "@/components/ui/field";
import { ModalityDialog, PickSelect } from "@/features/students/ModalityPickers";
import type { PortalRequestKind } from "@/features/portal-requests/types";
import { createRequest, type ModalityOption, type OfferOption } from "./usePortalRequests";

const REASONS = ["CITY_CHANGE", "FINANCIAL", "SCHEDULE", "INJURY", "DISSATISFACTION", "OTHER"];
const FIELD = { CLASS_CHANGE: "targetClassId", PLAN_CHANGE: "targetPlanId", NEW_MODALITY: "targetModalityId", CLOSURE: "reason" } as const;

type Props = { studentId: string; kind: PortalRequestKind; modality?: ModalityOption; offers: OfferOption[]; onClose: () => void; onDone: () => void };

// Uma janela para os quatro tipos de pedido (RF-MAT-04); o pedido nunca altera a matrícula (RN-MAT-16).
export function PortalRequestDialog({ studentId, kind, modality, offers, onClose, onDone }: Props) {
  const { t } = useTranslation();
  const noteId = useId();
  const [pick, setPick] = useState("");
  const [note, setNote] = useState("");
  const k = `portalOwn.dialog.${kind}.`;
  const choices =
    kind === "CLASS_CHANGE"
      ? (modality?.classes ?? [])
      : kind === "PLAN_CHANGE"
        ? (modality?.plans ?? [])
        : kind === "NEW_MODALITY"
          ? offers.map((o) => ({ id: `${o.unitId}|${o.modalityId}`, name: `${o.modality} · ${o.unit}` }))
          : REASONS.map((r) => ({ id: r, name: t(`students.record.enrollment.end.reasons.${r}`) }));

  const work = () => {
    const [targetUnitId, targetModalityId] = pick.split("|");
    const target = kind === "NEW_MODALITY" ? { targetUnitId, targetModalityId } : { modalityRowId: modality!.modalityRowId, [FIELD[kind]]: pick };
    return createRequest(studentId, { kind, note: note.trim() || undefined, ...target });
  };

  return (
    <ModalityDialog
      title={t(`${k}title`)}
      effect={t(`${k}effect`)}
      state="ready"
      submitLabel={t(`${k}submit`)}
      canSubmit={pick !== ""}
      work={work}
      onClose={onClose}
      onDone={onDone}
      errorPrefix="portalOwn.errors."
    >
      {(errors) => (
        <>
          {modality && <p className="text-sm">{t("portalOwn.dialog.of", { modality: modality.modality, unit: modality.unit })}</p>}
          <PickSelect label={t(`${k}field`)} value={pick} onChange={setPick} options={choices} error={errors[FIELD[kind]]} />
          <div>
            <label htmlFor={noteId} className="block text-sm font-medium">
              {t("portalOwn.dialog.note")}
            </label>
            <Textarea id={noteId} maxLength={500} value={note} onChange={(e) => setNote(e.target.value)} />
            {errors.note && <p className="text-sm text-danger-foreground">{errors.note}</p>}
          </div>
        </>
      )}
    </ModalityDialog>
  );
}
