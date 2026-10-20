import { useTranslation } from "react-i18next";
import type { TFunction } from "i18next";
import { QueryPage } from "@/components/query/QueryPage";
import type { Column } from "@/components/query/DataTable";
import { formatMoney } from "@/components/fields/masks";
import { ManagementLayout } from "@/features/permissions/ManagementLayout";
import { PlanFilters } from "./PlanFilters";
import { fetchPlans, type PlanRow } from "./usePlans";

const columns = (t: TFunction): Column<PlanRow>[] => [
  { key: "modality", label: t("plans.table.columns.modality"), render: (r) => r.modality.name },
  {
    key: "name",
    label: t("plans.table.columns.name"),
    render: (r) => (
      <span className="flex min-w-0 flex-col break-words">
        <span className="font-medium">{r.name}</span>
        <span className="text-sm text-muted-foreground">{t("plans.table.unit", { name: r.unit.name })}</span>
      </span>
    ),
  },
  { key: "value", label: t("plans.table.columns.value"), render: (r) => formatMoney(r.valueCents) },
  {
    key: "frequency",
    label: t("plans.table.columns.frequency"),
    render: (r) => (r.weeklyFrequency === null ? t("plans.table.free") : t("plans.table.perWeek", { count: r.weeklyFrequency })),
  },
  { key: "students", label: t("plans.table.columns.students"), render: (r) => r.studentsUsing },
  {
    key: "status",
    label: t("plans.table.columns.status"),
    render: (r) => (r.status === "INACTIVE" ? <span className="rounded-md border px-2 py-0.5 text-sm">{t("plans.table.inactiveBadge")}</span> : t("plans.table.statuses.ACTIVE")),
  },
];

// RF-PLA-02: tabela de planos com filtros; só leitura (edição entra na #57). A barreira de dados é o servidor.
export function PlansTablePage() {
  const { t } = useTranslation();
  return (
    <ManagementLayout activeId="plans">
      <div className="flex flex-col gap-4">
        <h1 className="text-2xl font-semibold">{t("plans.table.title")}</h1>
        <p className="text-muted-foreground">{t("plans.table.intro")}</p>
        <QueryPage columns={columns(t)} fetchPage={fetchPlans} rowKey={(r) => r.id} filterKeys={["modalityId", "unitId", "status"]} filters={(p) => <PlanFilters {...p} />} />
      </div>
    </ManagementLayout>
  );
}
