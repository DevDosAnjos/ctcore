import { useState } from "react";
import { useTranslation } from "react-i18next";
import { Link } from "react-router";
import { EmptyState, ErrorState, ForbiddenState, LoadingState } from "@/components/states";
import { Button } from "@/components/ui/button";
import { ManagementLayout } from "@/features/permissions/ManagementLayout";
import { usePermissions } from "@/features/permissions/usePermissions";
import { ALL, useUnitScope } from "@/features/units/UnitScopeProvider";
import { formatCurrency } from "@/lib/format";
import { ChargePreviewRows } from "./ChargePreviewRows";
import { PERIODS, useChargePreview } from "./useChargePreview";

const select = "min-h-11 rounded-md border bg-background px-2 font-normal";

function Content() {
  const { t } = useTranslation();
  const scope = useUnitScope();
  const { permissions } = usePermissions();
  const [days, setDays] = useState<number>(10);
  const [picked, setPicked] = useState("");
  // R10: com "todas as unidades" a rota não tem o que consultar; a pessoa escolhe uma da lista ao alcance.
  const all = scope.selected === ALL;
  const unitId = all ? picked || null : scope.selected;
  const { status, data, reload } = useChargePreview(unitId, days);
  const diff = data ? data.totalCents - data.previousMonthTotalCents : 0;

  let body;
  if (!unitId) body = <EmptyState title={t("charges.preview.pickUnit")} text={t("charges.preview.pickUnitText")} />;
  else if (status === "loading" || status === "idle") body = <LoadingState />;
  else if (status === "forbidden") body = <ForbiddenState />;
  else if (status === "error" || status === "notFound" || !data) body = <ErrorState onRetry={reload} />;
  else if (data.count === 0 && data.skipped.length === 0) body = <EmptyState title={t("charges.preview.empty", { days })} />;
  else
    body = (
      <>
        <dl className="grid gap-3 sm:grid-cols-3">
          <div className="rounded-md border p-3">
            <dt className="text-sm text-muted-foreground">{t("charges.preview.total")}</dt>
            <dd className="text-xl font-semibold">{formatCurrency(data.totalCents)}</dd>
          </div>
          <div className="rounded-md border p-3">
            <dt className="text-sm text-muted-foreground">{t("charges.preview.count")}</dt>
            <dd className="text-xl font-semibold">{data.count}</dd>
          </div>
          <div className="rounded-md border p-3">
            <dt className="text-sm text-muted-foreground">{t("charges.preview.diff")}</dt>
            <dd className={`text-xl font-semibold ${diff < 0 ? "text-destructive" : ""}`}>
              {diff === 0 ? formatCurrency(0) : `${diff > 0 ? "+" : "−"} ${formatCurrency(Math.abs(diff))}`}
            </dd>
          </div>
        </dl>
        <ChargePreviewRows data={data} canAdjust={permissions?.COLLECTIONS === "FULL"} onChanged={reload} />
      </>
    );

  return (
    <div className="flex flex-col gap-4">
      <h1 className="text-2xl font-semibold">{t("charges.preview.title")}</h1>
      <p className="text-muted-foreground">{t("charges.preview.intro")}</p>
      <div className="flex flex-wrap items-end gap-3">
        {all && (
          <label className="flex flex-col gap-1 text-sm font-medium">
            {t("charges.preview.unit")}
            <select className={select} value={picked} onChange={(e) => setPicked(e.target.value)}>
              <option value="">{t("charges.preview.chooseUnit")}</option>
              {scope.units.map((u) => (
                <option key={u.id} value={u.id}>
                  {u.name}
                </option>
              ))}
            </select>
          </label>
        )}
        <label className="flex flex-col gap-1 text-sm font-medium">
          {t("charges.preview.period")}
          <select className={select} value={days} onChange={(e) => setDays(Number(e.target.value))}>
            {PERIODS.map((d) => (
              <option key={d} value={d}>
                {t("charges.preview.periodOption", { count: d })}
              </option>
            ))}
          </select>
        </label>
        <Button asChild variant="outline" className="min-h-11">
          <Link to="/cobrancas">{t("charges.preview.back")}</Link>
        </Button>
      </div>
      {body}
    </div>
  );
}

// RF-COB-03: prévia da geração de mensalidades com ajustes antes de gerar (RN-COB-02); a barreira de dados e de ações é o servidor.
export function ChargePreviewPage() {
  return (
    <ManagementLayout activeId="collections">
      <Content />
    </ManagementLayout>
  );
}
