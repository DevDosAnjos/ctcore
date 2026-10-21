import { useState } from "react";
import { useTranslation } from "react-i18next";
import { Text } from "@/features/units/Text";
import { transferModality, useResource } from "./useStudentRecord";
import { combine, ModalityDialog, OtherClasses, PickSelect, useClassChoices, usePlanChoices, type Choice } from "./ModalityPickers";
import type { Enrollment, EnrollmentModality } from "./types";

// R11 (RN-MAT-20, RN-ORG-12): leva a modalidade para outra unidade, com turma, plano e vencimento da unidade nova; a mesma linha segue (histórico e graduação ficam).
export function TransferDialog({ studentId, enrollment, row, onClose, onDone }: { studentId: string; enrollment: Enrollment; row: EnrollmentModality; onClose: () => void; onDone: () => void }) {
  const { t } = useTranslation();
  const k = "students.record.enrollment.";
  const units = useResource<{ units: Choice[] }>("/units/reachable");
  const [unit, setUnit] = useState("");
  const [primary, setPrimary] = useState("");
  const [others, setOthers] = useState<string[]>([]);
  const [plan, setPlan] = useState("");
  const [day, setDay] = useState("");
  const classes = useClassChoices(unit || null, row.modality.id);
  const plans = usePlanChoices(unit || null, row.modality.id);
  const { state, retry } = combine(units, ...(unit ? [classes, plans] : []));
  // RN-MAT-03: com outra modalidade ativa na unidade nova, o dia é o dela.
  const inherited = enrollment.modalities.find((m) => m.id !== row.id && m.status === "ACTIVE" && m.unit.id === unit)?.dueDay;
  const pickUnit = (id: string) => {
    setUnit(id);
    setPrimary("");
    setOthers([]);
    setPlan("");
  };
  return (
    <ModalityDialog
      title={t(`${k}transfer.title`, { modality: row.modality.name })}
      effect={t(`${k}transfer.effect`, { unit: row.unit.name })}
      state={state}
      retry={retry}
      submitLabel={t(`${k}transfer.submit`)}
      canSubmit={!!unit && !!primary && !!plan && (inherited !== undefined || !!day)}
      work={() =>
        transferModality(studentId, row.id, {
          unitId: unit,
          primaryClassId: primary,
          otherClassIds: others.filter((id) => id !== primary),
          planId: plan,
          ...(inherited === undefined ? { dueDay: Number(day) } : {}),
        })
      }
      onClose={onClose}
      onDone={onDone}
    >
      {(errors) => (
        <>
          <PickSelect label={t(`${k}transfer.unit`)} value={unit} onChange={pickUnit} options={(units.data?.units ?? []).filter((u) => u.id !== row.unit.id)} error={errors.unitId} />
          {unit && (
            <>
              <PickSelect label={t(`${k}class.primary`)} value={primary} onChange={setPrimary} options={classes.data?.items ?? []} error={errors.primaryClassId} />
              <OtherClasses options={classes.data?.items ?? []} primary={primary} value={others} onChange={setOthers} error={errors.otherClassIds} />
              <PickSelect label={t(`${k}transfer.plan`)} value={plan} onChange={setPlan} options={plans.data?.items ?? []} error={errors.planId} />
              {inherited !== undefined ? (
                <p className="text-sm">{t(`${k}transfer.inheritedDay`, { day: inherited })}</p>
              ) : (
                <Text label={t(`${k}transfer.dueDay`)} type="number" min={1} max={28} inputMode="numeric" value={day} onChange={setDay} error={errors.dueDay} />
              )}
            </>
          )}
        </>
      )}
    </ModalityDialog>
  );
}
