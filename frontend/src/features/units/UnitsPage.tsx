import { useState } from "react";
import { useTranslation } from "react-i18next";
import { Link } from "react-router";
import { DataTable, type Column } from "@/components/query/DataTable";
import { EmptyState, ErrorState, ForbiddenState, LoadingState } from "@/components/states";
import { Banner } from "@/components/ui/banner";
import { Button } from "@/components/ui/button";
import { StatusBadge } from "@/components/ui/badge";
import { ManagementLayout } from "@/features/permissions/ManagementLayout";
import { DeactivateUnitDialog } from "./DeactivateUnitDialog";
import { ShortNameDialog } from "./ShortNameDialog";
import { MAX_UNITS, useUnits, type UnitSummary } from "./useUnits";
import { PageHeader } from "@/components/ui/page-header";

function Content() {
  const { t } = useTranslation();
  const api = useUnits();
  const [adjusting, setAdjusting] = useState<UnitSummary | null>(null);
  const [deactivating, setDeactivating] = useState<UnitSummary | null>(null);
  const [notice, setNotice] = useState(false);
  if (api.status === "loading") return <LoadingState />;
  if (api.status === "forbidden") return <ForbiddenState />;
  if (!api.units) return <ErrorState onRetry={api.retry} />;

  const sr = (u: UnitSummary) => <span className="sr-only"> {u.name}</span>;
  const columns: Column<UnitSummary>[] = [
    { key: "name", label: t("units.title"), render: (u) => <span className="font-medium">{u.name}</span> },
    { key: "city", label: t("units.city"), render: (u) => u.city ?? t("units.noCity") },
    { key: "students", label: t("units.activeStudents"), numeric: true, render: (u) => u.activeStudents },
    {
      key: "admins",
      label: t("units.administrators"),
      render: (u) => (u.administrators.length ? u.administrators.map((a) => a.name).join(", ") : t("units.noAdministrators")),
    },
    { key: "status", label: t("units.status"), render: (u) => <StatusBadge status={u.active ? "ACTIVE" : "INACTIVE"} label={u.active ? t("units.active") : t("units.inactive")} /> },
    {
      key: "short",
      label: t("units.shortName"),
      render: (u) => (
        <div>
          <span>{u.shortName}</span>
          {!u.shortNameAdjusted && (
            <Button variant="secondary" size="sm" className="mt-1 block whitespace-normal" onClick={() => setAdjusting(u)}>
              {t("units.adjust")}
              {sr(u)}
            </Button>
          )}
        </div>
      ),
    },
    {
      key: "actions",
      label: t("units.actions"),
      render: (u) => (
        <div className="flex flex-wrap gap-2">
          <Button asChild variant="secondary" size="sm">
            <Link to={`/configuracoes/unidade?unidade=${u.id}`}>
              {t("units.configure")}
              {sr(u)}
            </Link>
          </Button>
          {u.active && (
            <Button variant="secondary" size="sm" onClick={() => setDeactivating(u)}>
              {t("units.deactivate.button")}
              {sr(u)}
            </Button>
          )}
        </div>
      ),
    },
  ];

  return (
    <div className="flex flex-col gap-4">
      <PageHeader title={t("units.title")} />
      {notice && <Banner tone="ok">{t("units.deactivate.done")}</Banner>}
      {api.units.length >= MAX_UNITS ? (
        <div>
          <Button disabled aria-describedby="units-limit">
            {t("units.newButton")}
          </Button>
          <p id="units-limit" className="text-sm text-muted-foreground">
            {t("units.limitReached")}
          </p>
        </div>
      ) : (
        <Button asChild className="self-start">
          <Link to="/configuracoes/unidades/nova">{t("units.newButton")}</Link>
        </Button>
      )}
      {api.units.length === 0 ? (
        <EmptyState title={t("units.emptyTitle")} text={t("units.emptyText")} />
      ) : (
        <DataTable columns={columns} rows={api.units} rowKey={(u) => u.id} order="" onOrder={() => undefined} />
      )}
      <DeactivateUnitDialog
        unit={deactivating}
        onClose={() => setDeactivating(null)}
        onDone={() => {
          setNotice(true);
          void api.refresh();
        }}
      />
      <ShortNameDialog unit={adjusting} onClose={() => setAdjusting(null)} onSave={api.adjustShortName} />
    </div>
  );
}

// RF-ACE-06: só o Coordenador (a rota já nega os demais; a barreira de dados é o servidor).
export function UnitsPage() {
  return (
    <ManagementLayout activeId="units">
      <Content />
    </ManagementLayout>
  );
}
