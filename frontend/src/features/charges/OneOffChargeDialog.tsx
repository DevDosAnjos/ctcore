import { useId, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { CHAR_COUNTER_FIELD, CharCounter } from "@/components/CharCounter";
import { MoneyField } from "@/components/fields/fields";
import { Input } from "@/components/ui/field";
import { ErrorState, ForbiddenState, LoadingState } from "@/components/states";
import { useStudentModalities } from "@/features/benefits/GrantBenefitDialog";
import { ModalityDialog, PickSelect } from "@/features/students/ModalityPickers";
import { todayIso } from "./ChangeDueDialog";
import { StudentSearch } from "./StudentSearch";
import { createOneOff } from "./useChargeCreation";

const MAX_CENTS = 10_000_000;

// RN-COB-11: cobrança avulsa de uma unidade onde o aluno tem matrícula. O requestId nasce com a janela: duplo clique ou nova tentativa não duplicam (RNF-12).
export function OneOffChargeDialog({ onClose, onDone }: { onClose: () => void; onDone: () => void }) {
  const { t } = useTranslation();
  const k = "charges.creation.";
  const descriptionId = useId();
  const requestId = useRef(crypto.randomUUID());
  const [query, setQuery] = useState("");
  const [studentId, setStudentId] = useState("");
  const [chosenUnit, setChosenUnit] = useState("");
  const [description, setDescription] = useState("");
  const [amount, setAmount] = useState<number | null>(null);
  const [dueOn, setDueOn] = useState("");
  const { students, enrollment } = useStudentModalities(query, studentId);

  const units = [...new Map((enrollment.data?.modalities ?? []).map((m) => [m.unit.id, m.unit])).values()];
  const unitId = units.length === 1 ? units[0].id : chosenUnit;
  const canSubmit = !!studentId && !!unitId && description.trim().length >= 1 && amount !== null && amount >= 1 && amount <= MAX_CENTS && dueOn >= todayIso();

  return (
    <ModalityDialog
      title={t(`${k}title`)}
      effect={t(`${k}effect`)}
      state="ready"
      submitLabel={t(`${k}submit`)}
      canSubmit={canSubmit}
      errorPrefix={`${k}errors.`}
      work={() => createOneOff(studentId, { requestId: requestId.current, unitId, description: description.trim(), amountCents: amount!, dueOn })}
      onClose={onClose}
      onDone={onDone}
    >
      {(errors) => (
        <>
          <StudentSearch
            query={query}
            onQuery={(q) => {
              setQuery(q);
              setStudentId("");
              setChosenUnit("");
            }}
            students={students}
            studentId={studentId}
            onPick={(v) => {
              setStudentId(v);
              setChosenUnit("");
            }}
            error={errors.studentId}
          />
          {studentId && enrollment.status === "loading" && <LoadingState />}
          {studentId && enrollment.status === "forbidden" && <ForbiddenState />}
          {studentId && (enrollment.status === "error" || enrollment.status === "notFound") && <ErrorState onRetry={enrollment.reload} />}
          {studentId && enrollment.status === "ready" && units.length === 0 && <p className="text-sm text-muted-foreground">{t(`${k}noUnit`)}</p>}
          {studentId && enrollment.status === "ready" && units.length > 1 && (
            <PickSelect label={t(`${k}unit`)} value={chosenUnit} onChange={setChosenUnit} options={units} error={errors.unitId} />
          )}
          {studentId && enrollment.status === "ready" && units.length === 1 && <p className="text-sm">{t(`${k}unitFixed`, { unit: units[0].name })}</p>}
          <div>
            <label htmlFor={descriptionId} className="block text-sm font-medium">
              {t(`${k}description`)}
            </label>
            <CharCounter id={descriptionId} value={description} max={80} onChange={setDescription} rows={2} aria-invalid={errors.description ? true : undefined} className={CHAR_COUNTER_FIELD} />
          </div>
          {errors.description && <p className="text-sm text-danger-foreground">{errors.description}</p>}
          <MoneyField label={t(`${k}amount`)} onChange={setAmount} />
          {errors.amountCents && <p className="text-sm text-danger-foreground">{errors.amountCents}</p>}
          <label className="flex flex-col gap-1 text-sm font-medium">
            {t(`${k}dueOn`)}
            <Input type="date" min={todayIso()} value={dueOn} onChange={(e) => setDueOn(e.target.value)} aria-invalid={errors.dueOn ? true : undefined} />
          </label>
          {errors.dueOn && <p className="text-sm text-danger-foreground">{errors.dueOn}</p>}
        </>
      )}
    </ModalityDialog>
  );
}
