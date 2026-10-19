import { useTranslation } from "react-i18next";
import { useState } from "react";
import { Link, useNavigate } from "react-router";
import type { TFunction } from "i18next";
import { QueryPage } from "@/components/query/QueryPage";
import type { Column } from "@/components/query/DataTable";
import { Button } from "@/components/ui/button";
import { ManagementLayout } from "@/features/permissions/ManagementLayout";
import { usePermissions } from "@/features/permissions/usePermissions";
import { TeacherFilters } from "./TeacherFilters";
import { TeacherFormDialog } from "./TeacherFormDialog";
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
        <Link to={`/professores/${r.id}`} className="inline-flex min-h-11 min-w-0 items-center break-words font-medium underline">
          {r.name}
        </Link>
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
  const navigate = useNavigate();
  const [creating, setCreating] = useState(false);
  const { permissions } = usePermissions();
  const canWrite = !!permissions && permissions.TEACHERS !== "NONE";
  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h1 className="text-2xl font-semibold">{t("teachers.title")}</h1>
        {canWrite && (
          <Button className="min-h-11" onClick={() => setCreating(true)}>
            {t("teachers.form.newButton")}
          </Button>
        )}
      </div>
      <p className="text-muted-foreground">{t("teachers.intro")}</p>
      <QueryPage
        columns={columns(t)}
        fetchPage={fetchTeachers}
        rowKey={(r) => r.id}
        filterKeys={["modalityId", "unitId", "status"]}
        filters={(p) => <TeacherFilters {...p} />}
      />
      <TeacherFormDialog open={creating} onClose={() => setCreating(false)} onSaved={(saved) => void navigate(`/professores/${saved.id}`)} />
    </div>
  );
}

// RF-PRO-01 e RF-PRO-03: lista com "Novo professor"; a barreira de dados é o servidor (403 vira "sem permissão" na própria lista).
export function TeachersPage() {
  return (
    <ManagementLayout activeId="teachers">
      <Content />
    </ManagementLayout>
  );
}
