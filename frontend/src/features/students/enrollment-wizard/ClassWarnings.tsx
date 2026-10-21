import { useTranslation } from "react-i18next";
import { ErrorState, ForbiddenState } from "@/components/states";
import { useResource } from "../useStudentRecord";
import type { ClassWarnings as Warnings } from "./types";

// RF-MAT-02: avisos de faixa etária e capacidade da turma escolhida (RN-MOD-07, RN-TUR-02). Só informam: nunca desabilitam avançar nem concluir.
export function ClassWarnings({ studentId, classId, className }: { studentId: string; classId: string; className: string }) {
  const { t } = useTranslation();
  const k = "enrollmentWizard.modalities.";
  const warnings = useResource<Warnings>(classId ? `/students/${studentId}/enrollment/class-warnings?classId=${classId}` : null);
  if (warnings.status === "idle") return null;
  if (warnings.status === "loading") return <p className="text-sm text-muted-foreground">{t(`${k}warnLoading`)}</p>;
  if (warnings.status === "forbidden") return <ForbiddenState />;
  if (warnings.status !== "ready" || !warnings.data) return <ErrorState onRetry={warnings.reload} />;
  const w = warnings.data;
  const range = w.ageRange;
  return (
    <div role="status" className="flex flex-col gap-1">
      {w.ageOutOfRange && range && (
        <p className="text-sm font-medium">
          {t(range.maxAge === null ? `${k}warnAgeOpen` : `${k}warnAge`, { age: w.studentAge, klass: className, range: range.name, min: range.minAge, max: range.maxAge })}
        </p>
      )}
      {w.overCapacity && (
        <p className="text-sm font-medium">{t(`${k}warnCapacity`, { klass: className, count: w.activeStudents + 1, capacity: w.capacity })}</p>
      )}
    </div>
  );
}
