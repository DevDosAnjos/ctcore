import { useId } from "react";
import { useTranslation } from "react-i18next";
import { ErrorState, ForbiddenState, LoadingState } from "@/components/states";
import { PickSelect } from "@/features/students/ModalityPickers";
import type { StudentList } from "@/features/students/types";
import type { useResource } from "@/features/students/useStudentRecord";

// Busca de aluno por nome (mínimo 2 letras) das janelas de cobrança, com carregando, vazio, erro e sem permissão.
export function StudentSearch({
  query,
  onQuery,
  students,
  studentId,
  onPick,
  error,
}: {
  query: string;
  onQuery: (q: string) => void;
  students: ReturnType<typeof useResource<StudentList>>;
  studentId: string;
  onPick: (id: string) => void;
  error?: string;
}) {
  const { t } = useTranslation();
  const id = useId();
  return (
    <>
      <div>
        <label htmlFor={id} className="block text-sm font-medium">
          {t("charges.creation.search")}
        </label>
        <input id={id} className="min-h-11 w-full rounded-md border bg-background px-2" value={query} onChange={(e) => onQuery(e.target.value)} />
      </div>
      {students.status === "loading" && <LoadingState />}
      {students.status === "forbidden" && <ForbiddenState />}
      {(students.status === "error" || students.status === "notFound") && <ErrorState onRetry={students.reload} />}
      {students.status === "ready" && students.data?.items.length === 0 && <p className="text-sm text-muted-foreground">{t("charges.creation.noStudent")}</p>}
      {students.status === "ready" && (students.data?.items.length ?? 0) > 0 && (
        <PickSelect label={t("charges.creation.student")} value={studentId} onChange={onPick} options={students.data!.items.map((s) => ({ id: s.id, name: s.name }))} error={error} />
      )}
    </>
  );
}
