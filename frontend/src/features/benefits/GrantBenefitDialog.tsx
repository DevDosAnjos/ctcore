import { useId, useState } from "react";
import { useTranslation } from "react-i18next";
import { MoneyField } from "@/components/fields/fields";
import { formatMoney } from "@/components/fields/masks";
import {
  EmptyState,
  ErrorState,
  ForbiddenState,
  LoadingState,
} from "@/components/states";
import {
  ModalityDialog,
  PickSelect,
} from "@/features/students/ModalityPickers";
import { useResource } from "@/features/students/useStudentRecord";
import type { Enrollment, StudentList } from "@/features/students/types";
import {
  grantBenefit,
  useBenefitPreview,
  type BenefitType,
} from "./useGrantBenefit";

export interface GrantTarget {
  studentId: string;
  studentName: string;
  modalityRowId: string;
  modalityName: string;
}

function Field({
  label,
  error,
  children,
}: {
  label: string;
  error?: string;
  children: (id: string, describedBy: string | undefined) => React.ReactNode;
}) {
  const id = useId();
  return (
    <div>
      <label htmlFor={id} className="block text-sm font-medium">
        {label}
      </label>
      {children(id, error ? `${id}-erro` : undefined)}
      {error && (
        <p id={`${id}-erro`} className="text-sm text-destructive">
          {error}
        </p>
      )}
    </div>
  );
}

const input = "min-h-11 w-full rounded-md border bg-background px-2";

// Aluno por busca de nome (sem rota nova) e as modalidades ativas que a pessoa pode alterar, quando a janela não vem da ficha.
function useStudentModalities(query: string, studentId: string) {
  const students = useResource<StudentList>(
    query.trim().length >= 2
      ? `/students?q=${encodeURIComponent(query.trim())}&size=25&sort=name`
      : null,
  );
  const enrollment = useResource<Enrollment>(
    studentId ? `/students/${studentId}/enrollment` : null,
  );
  return { students, enrollment };
}

// RF-BEN-02: conceder benefício a uma modalidade da matrícula. O resumo (mensalidade, mês, aviso de substituição) vem do servidor; a barreira é dele.
export function GrantBenefitDialog({
  fixed,
  onClose,
  onDone,
}: {
  fixed?: GrantTarget;
  onClose: () => void;
  onDone: () => void;
}) {
  const { t } = useTranslation();
  const k = "benefits.grant.";
  const [query, setQuery] = useState("");
  const [studentId, setStudentId] = useState(fixed?.studentId ?? "");
  const [rowId, setRowId] = useState(fixed?.modalityRowId ?? "");
  const [type, setType] = useState<BenefitType>("PERCENT");
  const [percent, setPercent] = useState("");
  const [amount, setAmount] = useState<number | null>(null);
  const [reason, setReason] = useState("");
  const [decidedBy, setDecidedBy] = useState("");
  const [validUntil, setValidUntil] = useState("");
  const [teacherId, setTeacherId] = useState("");
  const teachers = useResource<{ items: { id: string; name: string }[] }>("/teachers?status=ACTIVE&size=100&sort=name,asc");
  const { students, enrollment } = useStudentModalities(
    query,
    fixed ? "" : studentId,
  );

  const value =
    type === "PERCENT"
      ? /^\d{1,3}$/.test(percent) &&
        Number(percent) >= 1 &&
        Number(percent) <= 100
        ? Number(percent)
        : null
      : amount;
  const preview = useBenefitPreview(studentId, rowId, type, value);
  const modalities = (enrollment.data?.modalities ?? []).filter(
    (m) => m.status === "ACTIVE" && m.canEdit,
  );
  const canSubmit =
    preview.status === "ready" &&
    reason.trim().length >= 2 &&
    decidedBy.trim().length >= 2;

  return (
    <ModalityDialog
      title={
        fixed
          ? t(`${k}titleFor`, { modality: fixed.modalityName })
          : t(`${k}title`)
      }
      effect={t(`${k}effect`)}
      state="ready"
      retry={() => {}}
      submitLabel={t(`${k}submit`)}
      canSubmit={canSubmit}
      work={() =>
        grantBenefit({
          studentId,
          modalityRowId: rowId,
          type,
          value: value!,
          reason: reason.trim(),
          decidedBy: decidedBy.trim(),
          validUntil: validUntil || null,
          replaceExisting: preview.data?.replaces ?? false,
          ...(teacherId ? { teacherId } : {}),
        })
      }
      onClose={onClose}
      onDone={onDone}
    >
      {(errors) => (
        <>
          {fixed ? (
            <p className="text-sm">
              {t(`${k}fixed`, {
                student: fixed.studentName,
                modality: fixed.modalityName,
              })}
            </p>
          ) : (
            <>
              <Field label={t(`${k}search`)}>
                {(id) => (
                  <input
                    id={id}
                    className={input}
                    value={query}
                    onChange={(e) => {
                      setQuery(e.target.value);
                      setStudentId("");
                      setRowId("");
                    }}
                  />
                )}
              </Field>
              {students.status === "loading" && <LoadingState />}
              {students.status === "forbidden" && <ForbiddenState />}
              {(students.status === "error" ||
                students.status === "notFound") && (
                <ErrorState onRetry={students.reload} />
              )}
              {students.status === "ready" &&
                students.data?.items.length === 0 && (
                  <p className="text-sm text-muted-foreground">
                    {t(`${k}noStudent`)}
                  </p>
                )}
              {students.status === "ready" &&
                (students.data?.items.length ?? 0) > 0 && (
                  <PickSelect
                    label={t(`${k}student`)}
                    value={studentId}
                    onChange={(v) => {
                      setStudentId(v);
                      setRowId("");
                    }}
                    options={students.data!.items.map((s) => ({
                      id: s.id,
                      name: s.name,
                    }))}
                    error={errors.studentId}
                  />
                )}
              {studentId && enrollment.status === "loading" && <LoadingState />}
              {studentId && enrollment.status === "forbidden" && (
                <ForbiddenState />
              )}
              {studentId &&
                (enrollment.status === "error" ||
                  enrollment.status === "notFound") && (
                  <ErrorState onRetry={enrollment.reload} />
                )}
              {studentId &&
                enrollment.status === "ready" &&
                modalities.length === 0 && (
                  <EmptyState
                    title={t(`${k}noModalityTitle`)}
                    text={t(`${k}noModalityText`)}
                  />
                )}
              {studentId &&
                enrollment.status === "ready" &&
                modalities.length > 0 && (
                  <PickSelect
                    label={t(`${k}modality`)}
                    value={rowId}
                    onChange={setRowId}
                    options={modalities.map((m) => ({
                      id: m.id,
                      name: `${m.modality.name} · ${m.unit.name}`,
                    }))}
                    error={errors.modalityRowId}
                  />
                )}
            </>
          )}
          <Field label={t(`${k}type`)} error={errors.type}>
            {(id) => (
              <select
                id={id}
                className={input}
                value={type}
                onChange={(e) => setType(e.target.value as BenefitType)}
              >
                <option value="PERCENT">{t("benefits.types.PERCENT")}</option>
                <option value="AMOUNT">{t("benefits.types.AMOUNT")}</option>
              </select>
            )}
          </Field>
          {type === "PERCENT" ? (
            <Field label={t(`${k}percent`)} error={errors.value}>
              {(id, d) => (
                <input
                  id={id}
                  className={input}
                  inputMode="numeric"
                  value={percent}
                  aria-invalid={errors.value ? true : undefined}
                  aria-describedby={d}
                  onChange={(e) =>
                    setPercent(e.target.value.replace(/\D/g, "").slice(0, 3))
                  }
                />
              )}
            </Field>
          ) : (
            <>
              <MoneyField
                key="amount"
                label={t(`${k}amount`)}
                initial={amount ?? undefined}
                onChange={setAmount}
              />
              {errors.value && (
                <p className="text-sm text-destructive">{errors.value}</p>
              )}
            </>
          )}
          <Field label={t(`${k}reason`)} error={errors.reason}>
            {(id, d) => (
              <input
                id={id}
                className={input}
                maxLength={200}
                value={reason}
                aria-invalid={errors.reason ? true : undefined}
                aria-describedby={d}
                onChange={(e) => setReason(e.target.value)}
              />
            )}
          </Field>
          <Field label={t(`${k}decidedBy`)} error={errors.decidedBy}>
            {(id, d) => (
              <input
                id={id}
                className={input}
                maxLength={80}
                value={decidedBy}
                aria-invalid={errors.decidedBy ? true : undefined}
                aria-describedby={d}
                onChange={(e) => setDecidedBy(e.target.value)}
              />
            )}
          </Field>
          <Field label={t(`${k}validUntil`)} error={errors.validUntil}>
            {(id, d) => (
              <input
                id={id}
                type="date"
                className={input}
                value={validUntil}
                aria-invalid={errors.validUntil ? true : undefined}
                aria-describedby={d}
                onChange={(e) => setValidUntil(e.target.value)}
              />
            )}
          </Field>
          {teachers.status === "ready" && (teachers.data?.items.length ?? 0) > 0 && (
            <Field label={t(`${k}teacher`)} error={errors.teacherId}>
              {(id) => (
                <select id={id} className={input} value={teacherId} onChange={(e) => setTeacherId(e.target.value)}>
                  <option value="">{t(`${k}noTeacher`)}</option>
                  {teachers.data!.items.map((x) => (
                    <option key={x.id} value={x.id}>
                      {x.name}
                    </option>
                  ))}
                </select>
              )}
            </Field>
          )}
          {errors.replaceExisting && (
            <p role="alert" className="text-sm text-destructive">
              {errors.replaceExisting}
            </p>
          )}
          {preview.status === "loading" && <LoadingState />}
          {preview.status === "forbidden" && <ForbiddenState />}
          {preview.status === "error" && (
            <ErrorState onRetry={preview.reload} />
          )}
          {preview.status === "ready" && (
            <section
              aria-label={t(`${k}summary.title`)}
              className="flex flex-col gap-1 rounded-md border p-3 text-sm"
            >
              <p>
                {t(`${k}summary.plan`, {
                  plan: preview.data.plan.name,
                  value: formatMoney(preview.data.plan.valueCents),
                })}
              </p>
              <p>
                {type === "PERCENT"
                  ? t(`${k}summary.percent`, { value })
                  : t(`${k}summary.amount`, { value: formatMoney(value) })}
              </p>
              <p className="font-medium">
                {t(`${k}summary.monthly`, {
                  value: formatMoney(preview.data.newMonthlyValueCents),
                })}
              </p>
              <p>
                {t(`${k}summary.from`, {
                  month: preview.data.effectiveFrom
                    .split("-")
                    .reverse()
                    .join("/"),
                })}
              </p>
              {preview.data.newMonthlyValueCents === 0 && (
                <p>{t(`${k}summary.zero`)}</p>
              )}
              {preview.data.high && <p>{t("benefits.highBadge")}</p>}
              {preview.data.current && (
                <p role="status" className="font-medium">
                  {t(`${k}summary.replaces`, {
                    current:
                      preview.data.current.type === "PERCENT"
                        ? t("benefits.percent", {
                            value: preview.data.current.value,
                          })
                        : formatMoney(preview.data.current.value),
                  })}
                </p>
              )}
            </section>
          )}
        </>
      )}
    </ModalityDialog>
  );
}
