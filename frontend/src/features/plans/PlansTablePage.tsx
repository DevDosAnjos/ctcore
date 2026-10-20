import { useState } from "react";
import { useTranslation } from "react-i18next";
import type { TFunction } from "i18next";
import { QueryPage } from "@/components/query/QueryPage";
import type { Column } from "@/components/query/DataTable";
import { formatMoney } from "@/components/fields/masks";
import { Button } from "@/components/ui/button";
import { ManagementLayout } from "@/features/permissions/ManagementLayout";
import { usePermissions } from "@/features/permissions/usePermissions";
import { ApiError } from "@/lib/api";
import { DeactivatePlanDialog } from "./DeactivatePlanDialog";
import { PlanFilters } from "./PlanFilters";
import { PlanFormDialog } from "./PlanFormDialog";
import { activatePlan, fetchPlans, type PlanRow } from "./usePlans";

const A = { edit: "edit", deactivate: "deactivate", activate: "activate" } as const;
type Actions = { onEdit: (p: PlanRow) => void; onDeactivate: (p: PlanRow) => void; onActivate: (p: PlanRow) => void };

const columns = (t: TFunction, actions?: Actions): Column<PlanRow>[] => [
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
  ...(actions
    ? [
        {
          key: "actions",
          label: t("plans.actions.label"),
          render: (r: PlanRow) => {
            const button = (action: "edit" | "deactivate" | "activate", run: (p: PlanRow) => void) => (
              <Button type="button" variant="outline" className="min-h-11" aria-label={t("plans.actions.forRow", { action: t(`plans.actions.${action}`), name: r.name })} onClick={() => run(r)}>
                {t(`plans.actions.${action}`)}
              </Button>
            );
            return (
              <span className="flex flex-wrap gap-2">
                {r.status === "ACTIVE" && button(A.edit, actions.onEdit)}
                {r.status === "ACTIVE" ? button(A.deactivate, actions.onDeactivate) : button(A.activate, actions.onActivate)}
              </span>
            );
          },
        },
      ]
    : []),
];

// RF-PLA-02 e RF-PLA-03: tabela de planos com "Novo plano", editar, desativar e ativar. A barreira de dados é o servidor.
function Content() {
  const { t } = useTranslation();
  const { permissions } = usePermissions();
  const canManage = !!permissions && permissions.PLANS !== "NONE";
  const [editing, setEditing] = useState<PlanRow | "new" | null>(null);
  const [deactivating, setDeactivating] = useState<PlanRow | null>(null);
  const [error, setError] = useState<string>();
  const [version, setVersion] = useState(0);
  const reload = () => setVersion((n) => n + 1);

  async function activate(p: PlanRow) {
    setError(undefined);
    try {
      await activatePlan(p.id);
      reload();
    } catch (e) {
      setError(t(e instanceof ApiError && e.status === 409 ? "plans.actions.activateConflict" : "plans.actions.failed"));
    }
  }

  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h1 className="text-2xl font-semibold">{t("plans.table.title")}</h1>
        {canManage && (
          <Button className="min-h-11" onClick={() => setEditing("new")}>
            {t("plans.newButton")}
          </Button>
        )}
      </div>
      <p className="text-muted-foreground">{t("plans.table.intro")}</p>
      {error && (
        <p role="alert" className="text-sm text-destructive">
          {error}
        </p>
      )}
      <QueryPage
        key={version}
        columns={columns(t, canManage ? { onEdit: setEditing, onDeactivate: setDeactivating, onActivate: (p) => void activate(p) } : undefined)}
        fetchPage={fetchPlans}
        rowKey={(r) => r.id}
        filterKeys={["modalityId", "unitId", "status"]}
        filters={(p) => <PlanFilters {...p} />}
      />
      <PlanFormDialog open={editing !== null} plan={editing && editing !== "new" ? editing : undefined} onClose={() => setEditing(null)} onSaved={reload} />
      <DeactivatePlanDialog plan={deactivating} onClose={() => setDeactivating(null)} onDone={reload} />
    </div>
  );
}

export function PlansTablePage() {
  return (
    <ManagementLayout activeId="plans">
      <Content />
    </ManagementLayout>
  );
}
