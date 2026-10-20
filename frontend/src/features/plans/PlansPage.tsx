import { useTranslation } from "react-i18next";
import { Card, CardGrid } from "@/components/Card";
import { EmptyState, ErrorState, ForbiddenState, LoadingState } from "@/components/states";
import { Button } from "@/components/ui/button";
import { Link } from "react-router";
import { formatMoney } from "@/components/fields/masks";
import { ManagementLayout } from "@/features/permissions/ManagementLayout";
import { ALL, useUnitScope } from "@/features/units/UnitScopeProvider";
import { usePlanSummary } from "./usePlans";

function Stat({ label, value }: { label: string; value: string | number }) {
  return (
    <div className="flex items-baseline justify-between gap-2">
      <dt className="text-muted-foreground">{label}</dt>
      <dd className="font-medium">{value}</dd>
    </div>
  );
}

function Content() {
  const { t } = useTranslation();
  const scope = useUnitScope();
  const unitId = scope.selected && scope.selected !== ALL ? scope.selected : null;
  const { summary, status, retry } = usePlanSummary(unitId);
  let body;
  if (status === "loading") body = <LoadingState />;
  else if (status === "forbidden") body = <ForbiddenState />;
  else if (status === "error" || !summary) body = <ErrorState onRetry={retry} />;
  else {
    const { plans, benefits } = summary;
    if (plans.active + plans.inactive + benefits.active === 0) {
      body = <EmptyState title={t("plans.emptyTitle")} text={t("plans.emptyText")} />;
    } else {
      const range =
        plans.minValueCents === null || plans.maxValueCents === null
          ? t("plans.noRange")
          : plans.minValueCents === plans.maxValueCents
            ? formatMoney(plans.minValueCents)
            : t("plans.range", { min: formatMoney(plans.minValueCents), max: formatMoney(plans.maxValueCents) });
      body = (
        <CardGrid>
          <Card
            title={t("plans.plans.title")}
            heading="h2"
            actions={
              <Button asChild className="min-h-11">
                <Link to="/planos/lista">{t("plans.plans.view")}</Link>
              </Button>
            }
          >
            <dl className="flex flex-col gap-1">
              <Stat label={t("plans.plans.active")} value={plans.active} />
              <Stat label={t("plans.plans.inactive")} value={plans.inactive} />
              <Stat label={t("plans.plans.studentsUsing")} value={plans.studentsUsing} />
              <Stat label={t("plans.plans.range")} value={range} />
            </dl>
          </Card>
          <Card
            title={t("plans.benefits.title")}
            heading="h2"
            actions={
              <Button className="min-h-11" disabled>
                {t("plans.benefits.view")}
              </Button>
            }
          >
            <dl className="flex flex-col gap-1">
              <Stat label={t("plans.benefits.active")} value={benefits.active} />
              <Stat label={t("plans.benefits.high")} value={benefits.high} />
            </dl>
          </Card>
        </CardGrid>
      );
    }
  }
  return (
    <div className="flex flex-col gap-4">
      <h1 className="text-2xl font-semibold">{t("plans.title")}</h1>
      <p className="text-muted-foreground">{t("plans.intro")}</p>
      {body}
    </div>
  );
}

// RF-PLA-01: dois cartões (Planos e Benefícios) com o resumo da unidade escolhida; a barreira de dados é o servidor.
export function PlansPage() {
  return (
    <ManagementLayout activeId="plans">
      <Content />
    </ManagementLayout>
  );
}
