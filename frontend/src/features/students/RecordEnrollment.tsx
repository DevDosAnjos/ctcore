import { useId, useState } from "react";
import { useTranslation } from "react-i18next";
import { EmptyState, ErrorState, ForbiddenState, LoadingState } from "@/components/states";
import { Button } from "@/components/ui/button";
import { GrantBenefitDialog } from "@/features/benefits/GrantBenefitDialog";
import { ChangeClassDialog } from "./ChangeClassDialog";
import { ChangePlanDialog } from "./ChangePlanDialog";
import { NewEnrollmentLink } from "./NewEnrollmentLink";
import { TransferDialog } from "./TransferDialog";
import type { useEnrollment } from "./useStudentRecord";
import type { Enrollment, EnrollmentModality } from "./types";

const ACTIONS = ["class", "plan", "transfer"] as const;
type Action = { kind: "class" | "plan" | "transfer" | "benefit"; row: EnrollmentModality };

function Line({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex flex-wrap gap-x-2">
      <dt className="text-muted-foreground">{label}</dt>
      <dd className="min-w-0 break-words">{value}</dd>
    </div>
  );
}

function ModalityCard({ row, onAction, canGrant }: { row: EnrollmentModality; onAction: (a: Action) => void; canGrant: boolean }) {
  const { t } = useTranslation();
  const k = "students.record.enrollment.";
  const hint = useId();
  const active = row.status === "ACTIVE";
  return (
    <li className="flex flex-col gap-2 rounded-md border p-3">
      <div className="flex flex-wrap items-center gap-2">
        <p className="font-medium">{row.modality.name}</p>
        <span className="rounded-md border px-2 py-0.5 text-sm">{t(active ? `${k}active` : `${k}ended`)}</span>
      </div>
      <dl className="flex flex-col gap-1 text-sm">
        <Line label={t(`${k}unit`)} value={row.unit.name} />
        <Line label={t(`${k}classLabel`)} value={row.primaryClass.name} />
        <Line label={t(`${k}planLabel`)} value={row.plan.name} />
        <Line label={t(`${k}dueDay`)} value={t(`${k}dueDayValue`, { day: row.dueDay })} />
      </dl>
      {row.otherClasses.length > 0 && <p className="break-words">{t(`${k}otherClassesLine`, { names: row.otherClasses.map((c) => c.name).join(", ") })}</p>}
      {row.nextPlan && <p className="text-sm">{t(`${k}nextPlan`, { plan: row.nextPlan.name })}</p>}
      {active && !row.canEdit && <p className="text-sm text-muted-foreground">{t(`${k}readOnly`)}</p>}
      {active && (
        <div className="flex flex-wrap gap-2">
          {row.canEdit &&
            ACTIONS.map((kind) => (
              <Button key={kind} type="button" variant="outline" className="min-h-11" onClick={() => onAction({ kind, row })}>
                {t(`${k}actions.${kind}`)}
              </Button>
            ))}
          {row.canEdit && canGrant && (
            <Button type="button" variant="outline" className="min-h-11" onClick={() => onAction({ kind: "benefit", row })}>
              {t(`${k}actions.benefit`)}
            </Button>
          )}
          {row.canEdit && (
            <>
              <Button type="button" variant="outline" className="min-h-11" disabled aria-describedby={hint}>
                {t(`${k}actions.end`)}
              </Button>
              <span id={hint} className="self-center text-sm text-muted-foreground">
                {t(`${k}actions.endSoon`)}
              </span>
            </>
          )}
        </div>
      )}
    </li>
  );
}

// RF-ALU-03 (R23): matrícula e modalidades do aluno, com as trocas por modalidade (só nas que a pessoa pode alterar). O servidor decide `canEdit`; a barreira é dele.
export function RecordEnrollment({ studentId, studentName, enrollment, canGrant = false, onChanged }: { studentId: string; studentName: string; enrollment: ReturnType<typeof useEnrollment>; canGrant?: boolean; onChanged: () => void }) {
  const { t } = useTranslation();
  const [action, setAction] = useState<Action>();
  const { status, data, reload } = enrollment;
  if (status === "loading" || status === "idle") return <LoadingState />;
  if (status === "forbidden") return <ForbiddenState />;
  if (status === "error" || status === "notFound" || !data) return <ErrorState onRetry={reload} />;
  if (data.modalities.length === 0) return <EmptyState title={t("students.record.enrollment.emptyTitle")} text={t("students.record.enrollment.emptyText")} action={<NewEnrollmentLink studentId={studentId} />} />;
  const done = () => {
    reload();
    onChanged();
  };
  const props = { studentId, onClose: () => setAction(undefined), onDone: done };
  return (
    <>
      {data.status === "FORMER" && <NewEnrollmentLink studentId={studentId} />}
      <ul className="flex flex-col gap-3">
        {data.modalities.map((row) => (
          <ModalityCard key={row.id} row={row} onAction={setAction} canGrant={canGrant} />
        ))}
      </ul>
      {action?.kind === "class" && <ChangeClassDialog {...props} row={action.row} />}
      {action?.kind === "plan" && <ChangePlanDialog {...props} row={action.row} />}
      {action?.kind === "benefit" && (
        <GrantBenefitDialog
          fixed={{ studentId, studentName, modalityRowId: action.row.id, modalityName: action.row.modality.name }}
          onClose={props.onClose}
          onDone={done}
        />
      )}
      {action?.kind === "transfer" && <TransferDialog {...props} enrollment={data as Enrollment} row={action.row} />}
    </>
  );
}
