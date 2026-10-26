import { useState } from "react";
import { useTranslation } from "react-i18next";
import { changeClass } from "./useStudentRecord";
import { combine, ModalityDialog, OtherClasses, PickSelect, useClassChoices } from "./ModalityPickers";
import type { EnrollmentModality } from "./types";

// R9 (RN-MAT-15): troca a turma principal e os outros horários; vale na hora. O servidor reconfere as regras e o erro aparece no campo.
// `initial` é a turma pedida pelo aluno (RN-MAT-16): a janela abre com ela escolhida, e a gestão confere e confirma.
export function ChangeClassDialog({ studentId, row, initial, onClose, onDone }: { studentId: string; row: EnrollmentModality; initial?: string; onClose: () => void; onDone: () => void }) {
  const { t } = useTranslation();
  const k = "students.record.enrollment.";
  const classes = useClassChoices(row.unit.id, row.modality.id);
  const [primary, setPrimary] = useState(initial ?? row.primaryClass.id);
  const [others, setOthers] = useState(row.otherClasses.map((c) => c.id).filter((id) => id !== initial));
  const { state, retry } = combine(classes);
  const options = classes.data?.items ?? [];
  return (
    <ModalityDialog
      title={t(`${k}class.title`, { modality: row.modality.name })}
      effect={t(`${k}class.effect`)}
      state={state}
      retry={retry}
      submitLabel={t(`${k}class.submit`)}
      canSubmit={!!primary}
      work={() => changeClass(studentId, row.id, { primaryClassId: primary, otherClassIds: others.filter((id) => id !== primary) })}
      onClose={onClose}
      onDone={onDone}
    >
      {(errors) => (
        <>
          <PickSelect label={t(`${k}class.primary`)} value={primary} onChange={setPrimary} options={options} error={errors.primaryClassId} />
          <OtherClasses options={options} primary={primary} value={others} onChange={setOthers} error={errors.otherClassIds} />
        </>
      )}
    </ModalityDialog>
  );
}
