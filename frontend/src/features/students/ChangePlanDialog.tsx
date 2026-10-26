import { useState } from "react";
import { useTranslation } from "react-i18next";
import { changePlan } from "./useStudentRecord";
import { combine, ModalityDialog, PickSelect, usePlanChoices } from "./ModalityPickers";
import type { EnrollmentModality } from "./types";

// R10 (RN-MAT-15, D10): o plano novo vale a partir da próxima cobrança; pedir o plano atual limpa o agendamento.
// `initial` é o plano pedido pelo aluno (RN-MAT-16): a janela abre com ele escolhido, e a gestão confere e confirma.
export function ChangePlanDialog({ studentId, row, initial, onClose, onDone }: { studentId: string; row: EnrollmentModality; initial?: string; onClose: () => void; onDone: () => void }) {
  const { t } = useTranslation();
  const k = "students.record.enrollment.";
  const plans = usePlanChoices(row.unit.id, row.modality.id);
  const [plan, setPlan] = useState(initial ?? row.nextPlan?.id ?? row.plan.id);
  const { state, retry } = combine(plans);
  // O plano atual sem agendamento não muda nada (o servidor recusa com 422).
  const changes = !!plan && (plan !== row.plan.id || !!row.nextPlan);
  return (
    <ModalityDialog
      title={t(`${k}plan.title`, { modality: row.modality.name })}
      effect={t(`${k}plan.effect`)}
      state={state}
      retry={retry}
      submitLabel={t(`${k}plan.submit`)}
      canSubmit={changes}
      work={() => changePlan(studentId, row.id, plan)}
      onClose={onClose}
      onDone={onDone}
    >
      {(errors) => (
        <>
          <p className="text-sm">{t(`${k}plan.current`, { plan: row.plan.name })}</p>
          <PickSelect label={t(`${k}plan.next`)} value={plan} onChange={setPlan} options={plans.data?.items ?? []} error={errors.planId} />
        </>
      )}
    </ModalityDialog>
  );
}
