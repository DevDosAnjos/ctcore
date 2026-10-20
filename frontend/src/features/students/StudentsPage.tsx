import { useTranslation } from "react-i18next";
import type { TFunction } from "i18next";
import { QueryPage } from "@/components/query/QueryPage";
import type { Column } from "@/components/query/DataTable";
import { useSession } from "@/features/auth/SessionProvider";
import { ManagementLayout } from "@/features/permissions/ManagementLayout";
import { formatDate } from "@/lib/format";
import type { StudentRow } from "./types";
import { StudentFilters } from "./StudentFilters";
import { OPTIONAL_COLUMNS, useStudentColumns, type OptionalColumn } from "./useStudentColumns";
import { fetchStudents } from "./useStudents";

const initials = (name: string) =>
  name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((w) => w[0].toUpperCase())
    .join("");

function Avatar({ student }: { student: StudentRow }) {
  return student.photoUrl ? (
    <img src={student.photoUrl} alt="" className="size-10 shrink-0 rounded-full object-cover" />
  ) : (
    <span aria-hidden="true" className="flex size-10 shrink-0 items-center justify-center rounded-full bg-muted text-sm font-semibold">
      {initials(student.name)}
    </span>
  );
}

// O nome ainda não leva a lugar nenhum: a ficha em painel vem na #60.
const allColumns = (t: TFunction): Record<"name" | OptionalColumn, Column<StudentRow>> => ({
  name: {
    key: "name",
    label: t("students.columns.name"),
    render: (r) => (
      <span className="flex items-center gap-2">
        <Avatar student={r} />
        <span className="min-w-0 break-words font-medium">{r.name}</span>
      </span>
    ),
  },
  unit: { key: "unit", label: t("students.columns.unit"), render: (r) => r.registeredUnit.name },
  age: { key: "age", label: t("students.columns.age"), render: (r) => t("students.age", { count: r.age }) },
  ageRange: { key: "ageRange", label: t("students.columns.ageRange"), render: (r) => r.ageRange?.name ?? t("format.empty") },
  createdAt: { key: "createdAt", label: t("students.columns.createdAt"), render: (r) => formatDate(r.createdAt) },
});

function Content({ accountId }: { accountId: string }) {
  const { t } = useTranslation();
  const { visible, toggle } = useStudentColumns(accountId);
  const all = allColumns(t);
  const columns = [all.name, ...visible.map((c) => all[c])];
  return (
    <div className="flex flex-col gap-4">
      <h1 className="text-2xl font-semibold">{t("students.title")}</h1>
      <p className="text-muted-foreground">{t("students.intro")}</p>
      <details>
        <summary className="inline-flex min-h-11 cursor-pointer items-center font-medium">{t("students.columnsChoose")}</summary>
        <div className="flex flex-wrap gap-x-4">
          {OPTIONAL_COLUMNS.map((c) => (
            <label key={c} className="flex min-h-11 items-center gap-2 text-sm">
              <input type="checkbox" className="size-5" checked={visible.includes(c)} onChange={() => toggle(c)} />
              {t(`students.columns.${c}`)}
            </label>
          ))}
        </div>
      </details>
      <QueryPage
        columns={columns}
        fetchPage={fetchStudents}
        rowKey={(r) => r.id}
        filterKeys={["unitId", "ageRangeId"]}
        filters={(p) => <StudentFilters {...p} />}
      />
    </div>
  );
}

// RF-ALU-01: lista de alunos para a gestão; a barreira de dados é o servidor (403 vira "sem permissão" na própria lista).
export function StudentsPage() {
  const { state } = useSession();
  const accountId = state.status === "ready" ? state.session.account.id : "";
  return (
    <ManagementLayout activeId="students">
      <Content key={accountId} accountId={accountId} />
    </ManagementLayout>
  );
}
