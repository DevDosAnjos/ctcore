import { useState, type ReactNode } from "react";
import { useTranslation } from "react-i18next";
import { Link } from "react-router";
import { Card } from "@/components/Card";
import { EmptyState, ErrorState, ForbiddenState, LoadingState } from "@/components/states";
import { Button } from "@/components/ui/button";
import { ManagementLayout } from "@/features/permissions/ManagementLayout";
import { ShortNameDialog } from "./ShortNameDialog";
import { MAX_UNITS, useUnits, type UnitSummary } from "./useUnits";

function Field({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="min-w-0">
      <dt className="text-xs text-muted-foreground">{label}</dt>
      <dd className="break-words">{children}</dd>
    </div>
  );
}

// Cartão em 360 px; em telas largas os campos viram colunas lado a lado.
function UnitRow({ unit, onAdjust }: { unit: UnitSummary; onAdjust: (u: UnitSummary) => void }) {
  const { t } = useTranslation();
  return (
    <li>
      <Card heading="h2" title={unit.name}>
        <dl className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-5">
          <Field label={t("units.city")}>{unit.city ?? t("units.noCity")}</Field>
          <Field label={t("units.activeStudents")}>{unit.activeStudents}</Field>
          <Field label={t("units.administrators")}>
            {unit.administrators.length ? unit.administrators.map((a) => a.name).join(", ") : t("units.noAdministrators")}
          </Field>
          <Field label={t("units.status")}>{unit.active ? t("units.active") : t("units.inactive")}</Field>
          <Field label={t("units.shortName")}>
            <span>{unit.shortName}</span>
            {!unit.shortNameAdjusted && (
              <Button variant="outline" size="sm" className="mt-1 whitespace-normal" onClick={() => onAdjust(unit)}>
                {t("units.adjust")}
                <span className="sr-only"> {unit.name}</span>
              </Button>
            )}
          </Field>
        </dl>
      </Card>
    </li>
  );
}

function Content() {
  const { t } = useTranslation();
  const api = useUnits();
  const [adjusting, setAdjusting] = useState<UnitSummary | null>(null);
  if (api.status === "loading") return <LoadingState />;
  if (api.status === "forbidden") return <ForbiddenState />;
  if (!api.units) return <ErrorState onRetry={api.retry} />;
  return (
    <div className="flex flex-col gap-4">
      <h1 className="text-2xl font-semibold">{t("units.title")}</h1>
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
        <ul className="flex flex-col gap-4">
          {api.units.map((u) => (
            <UnitRow key={u.id} unit={u} onAdjust={setAdjusting} />
          ))}
        </ul>
      )}
      <ShortNameDialog unit={adjusting} onClose={() => setAdjusting(null)} onSave={api.adjustShortName} />
    </div>
  );
}

// RF-ORG-02: só o Coordenador (a rota já nega os demais; a barreira de dados é o servidor).
export function UnitsPage() {
  return (
    <ManagementLayout activeId="units">
      <Content />
    </ManagementLayout>
  );
}
