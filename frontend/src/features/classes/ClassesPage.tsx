import { useState } from "react";
import { useTranslation } from "react-i18next";
import { Link, useNavigate } from "react-router";
import type { TFunction } from "i18next";
import { QueryPage } from "@/components/query/QueryPage";
import type { Column } from "@/components/query/DataTable";
import { Button } from "@/components/ui/button";
import { ManagementLayout } from "@/features/permissions/ManagementLayout";
import { usePermissions } from "@/features/permissions/usePermissions";
import { ClassFilters } from "./ClassFilters";
import { ClassFormDialog } from "./ClassFormDialog";
import { fetchClasses, type TrainingClass } from "./useClasses";

const columns = (t: TFunction): Column<TrainingClass>[] => [
  {
    key: "name",
    label: t("classes.columns.name"),
    sortable: true,
    render: (r) => (
      <span className="flex min-w-0 flex-col break-words">
        <Link to={`/turmas/${r.id}`} className="inline-flex min-h-11 items-center font-medium underline">
          {r.name}
        </Link>
        <span className="text-sm text-muted-foreground">{t("classes.unit", { name: r.unit.name })}</span>
      </span>
    ),
  },
  { key: "modality", label: t("classes.columns.modality"), render: (r) => r.modality.name },
  { key: "ageRange", label: t("classes.columns.ageRange"), render: (r) => r.ageRange?.name ?? t("classes.none") },
  {
    key: "schedule",
    label: t("classes.columns.schedule"),
    render: (r) =>
      r.schedule.length === 0
        ? t("classes.none")
        : r.schedule.map((s) => t("classes.slot", { day: t(`classes.weekdays.${s.weekday}`), from: s.startsAt, to: s.endsAt })).join(", "),
  },
  {
    key: "head",
    label: t("classes.columns.head"),
    render: (r) => (
      <span className="flex min-w-0 flex-col break-words">
        {r.head.name}
        {r.assistants.length > 0 && (
          <span className="text-sm text-muted-foreground">{t("classes.assistants", { names: r.assistants.map((a) => a.name).join(", ") })}</span>
        )}
      </span>
    ),
  },
  {
    key: "students",
    label: t("classes.columns.students"),
    render: (r) =>
      r.capacity === null ? (
        r.activeStudents
      ) : (
        <span className="flex flex-col">
          {t("classes.students", { count: r.activeStudents, capacity: r.capacity })}
          {r.overCapacity && <span className="font-medium text-destructive">{t("classes.over", { count: r.activeStudents, capacity: r.capacity })}</span>}
        </span>
      ),
  },
  { key: "status", label: t("classes.columns.status"), render: (r) => t(`classes.statuses.${r.status}`) },
];

function Content() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [creating, setCreating] = useState(false);
  const { permissions } = usePermissions();
  const canManage = !!permissions && permissions.CLASSES_MANAGE !== "NONE";
  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h1 className="text-2xl font-semibold">{t("classes.title")}</h1>
        {canManage && (
          <Button className="min-h-11" onClick={() => setCreating(true)}>
            {t("classes.newButton")}
          </Button>
        )}
      </div>
      <p className="text-muted-foreground">{t("classes.intro")}</p>
      <QueryPage
        columns={columns(t)}
        fetchPage={fetchClasses}
        rowKey={(r) => r.id}
        filterKeys={["modalityId", "teacherId", "ageRangeId", "weekday", "unitId", "status"]}
        filters={(p) => <ClassFilters {...p} />}
      />
      <ClassFormDialog open={creating} onClose={() => setCreating(false)} onSaved={(saved) => void navigate(`/turmas/${saved.id}`)} />
    </div>
  );
}

// RF-TUR-01 e RF-TUR-03: lista de turmas com "Nova turma"; a barreira de dados é o servidor (403 vira "sem permissão" na própria lista).
export function ClassesPage() {
  return (
    <ManagementLayout activeId="classes">
      <Content />
    </ManagementLayout>
  );
}
