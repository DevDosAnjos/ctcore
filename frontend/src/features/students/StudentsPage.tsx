import { useState } from "react";
import { useTranslation } from "react-i18next";
import { Link, useLocation, useNavigate, useParams } from "react-router";
import type { TFunction } from "i18next";
import { QueryPage } from "@/components/query/QueryPage";
import type { Column } from "@/components/query/DataTable";
import { useSession } from "@/features/auth/SessionProvider";
import { ManagementLayout } from "@/features/permissions/ManagementLayout";
import { formatDate } from "@/lib/format";
import type { StudentRow } from "./types";
import { NewEnrollmentLink } from "./NewEnrollmentLink";
import { StudentFilters } from "./StudentFilters";
import { StudentRecordPanel } from "./StudentRecordPanel";
import { OPTIONAL_COLUMNS, useStudentColumns, type OptionalColumn } from "./useStudentColumns";
import { fetchStudents } from "./useStudents";
import { PageHeader } from "@/components/ui/page-header";

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

// O nome abre a ficha em painel (RF-ALU-03), mantendo a busca e os filtros da lista na mesma página.
const allColumns = (t: TFunction, search: string): Record<"name" | OptionalColumn, Column<StudentRow>> => ({
  name: {
    key: "name",
    label: t("students.columns.name"),
    render: (r) => (
      <span className="flex flex-wrap items-center gap-2">
        <Avatar student={r} />
        <Link to={`/alunos/${r.id}${search}`} aria-label={t("students.record.open", { name: r.name })} className="inline-flex min-h-11 min-w-0 items-center break-words font-medium underline-offset-2 hover:underline">
          {r.name}
        </Link>
        {r.seals.map((seal) => (
          <span key={seal} className="rounded-md border px-2 py-0.5 text-sm">
            {t(`students.seals.${seal}`)}
          </span>
        ))}
      </span>
    ),
  },
  unit: { key: "unit", label: t("students.columns.unit"), render: (r) => r.registeredUnit.name },
  age: { key: "age", label: t("students.columns.age"), render: (r) => t("students.age", { count: r.age }) },
  ageRange: { key: "ageRange", label: t("students.columns.ageRange"), render: (r) => r.ageRange?.name ?? t("format.empty") },
  createdAt: { key: "createdAt", label: t("students.columns.createdAt"), render: (r) => formatDate(r.createdAt) },
  modalities: {
    key: "modalities",
    label: t("students.columns.modalities"),
    render: (r) =>
      r.modalities.length === 0 ? (
        t("format.empty")
      ) : (
        <ul className="flex flex-col gap-1">
          {r.modalities.map((m) => (
            <li key={m.id} className="break-words">
              {t("students.modalityLine", { modality: m.modality.name, unit: m.unit.name, class: m.primaryClass.name })}
              {m.level && <span className="ml-1 text-muted-foreground">{m.level.name}</span>}
            </li>
          ))}
        </ul>
      ),
  },
  status: { key: "status", label: t("students.columns.status"), render: (r) => t(`students.statuses.${r.status}`) },
  financial: { key: "financial", label: t("students.columns.financial"), render: (r) => t(`students.financial.${r.financialStatus}`) },
});

function Content({ accountId }: { accountId: string }) {
  const { t } = useTranslation();
  const { visible, toggle } = useStudentColumns(accountId);
  const { id } = useParams();
  const { search } = useLocation();
  const navigate = useNavigate();
  const [version, setVersion] = useState(0);
  const all = allColumns(t, search);
  const columns = [all.name, ...visible.map((c) => all[c])];
  return (
    <div className="flex flex-col gap-4">
      <PageHeader title={t("students.title")} />
      <p className="text-muted-foreground">{t("students.intro")}</p>
      <NewEnrollmentLink />
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
        key={version}
        columns={columns}
        fetchPage={fetchStudents}
        rowKey={(r) => r.id}
        filterKeys={["unitId", "ageRangeId", "seal", "modalityId", "classId", "levelId", "status", "financial"]}
        filters={(p) => <StudentFilters {...p} />}
      />
      {id && <StudentRecordPanel id={id} onClose={() => navigate(`/alunos${search}`)} onChanged={() => setVersion((v) => v + 1)} />}
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
