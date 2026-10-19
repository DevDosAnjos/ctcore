import { useTranslation } from "react-i18next";
import type { TFunction } from "i18next";
import { QueryPage } from "@/components/query/QueryPage";
import type { Column } from "@/components/query/DataTable";
import { ManagementLayout } from "@/features/permissions/ManagementLayout";
import { TeacherFilters } from "./TeacherFilters";
import { fetchTeachers, type Teacher } from "./useTeachers";

const initials = (name: string) =>
  name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((w) => w[0].toUpperCase())
    .join("");

function Avatar({ teacher }: { teacher: Teacher }) {
  return teacher.photoUrl ? (
    <img src={teacher.photoUrl} alt="" className="size-10 shrink-0 rounded-full object-cover" />
  ) : (
    <span aria-hidden="true" className="flex size-10 shrink-0 items-center justify-center rounded-full bg-muted text-sm font-semibold">
      {initials(teacher.name)}
    </span>
  );
}

const columns = (t: TFunction): Column<Teacher>[] => [
  {
    key: "name",
    label: t("teachers.columns.name"),
    sortable: true,
    render: (r) => (
      <span className="flex items-center gap-2">
        <Avatar teacher={r} />
        <span className="min-w-0 break-words font-medium">{r.name}</span>
      </span>
    ),
  },
  {
    key: "modalities",
    label: t("teachers.columns.modalities"),
    render: (r) => (
      <ul>
        {r.modalities.map((m) => (
          <li key={m.id}>{m.level === null ? m.name : t("teachers.modality", { name: m.name, level: m.level, degree: m.degree })}</li>
        ))}
      </ul>
    ),
  },
  { key: "units", label: t("teachers.columns.units"), render: (r) => r.units.map((u) => u.name).join(", ") },
  { key: "classes", label: t("teachers.columns.classes"), render: (r) => r.classCount },
  { key: "status", label: t("teachers.columns.status"), render: (r) => t(`teachers.statuses.${r.status}`) },
];

function Content() {
  const { t } = useTranslation();
  return (
    <div className="flex flex-col gap-4">
      <h1 className="text-2xl font-semibold">{t("teachers.title")}</h1>
      <p className="text-muted-foreground">{t("teachers.intro")}</p>
      <QueryPage
        columns={columns(t)}
        fetchPage={fetchTeachers}
        rowKey={(r) => r.id}
        filterKeys={["modalityId", "unitId", "status"]}
        filters={(p) => <TeacherFilters {...p} />}
      />
    </div>
  );
}

// RF-PRO-01: lista só de leitura; a barreira de dados é o servidor (403 vira "sem permissão" na própria lista).
export function TeachersPage() {
  return (
    <ManagementLayout activeId="teachers">
      <Content />
    </ManagementLayout>
  );
}
