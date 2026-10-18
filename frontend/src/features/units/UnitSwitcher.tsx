import { useTranslation } from "react-i18next";
import { Button } from "@/components/ui/button";
import { ALL, useUnitScope } from "./UnitScopeProvider";

// RF-ORG-05: seletor da barra superior, só com duas ou mais unidades ao alcance (RN-ORG-04). O servidor segue decidindo cada acesso.
export function UnitSwitcher() {
  const { t } = useTranslation();
  const scope = useUnitScope();
  if (scope.status === "loading") return <span role="status" className="text-sm text-muted-foreground">{t("unitScope.loading")}</span>;
  if (scope.status === "error") {
    return (
      <span className="flex items-center gap-2">
        <span role="alert" className="text-sm">{t("unitScope.error")}</span>
        <Button variant="outline" size="sm" onClick={scope.retry}>{t("states.retry")}</Button>
      </span>
    );
  }
  if (scope.status === "forbidden" || scope.units.length < 2) return null;
  return (
    <select
      aria-label={t("unitScope.label")}
      className="min-h-11 max-w-[11rem] rounded-md border bg-background px-2 text-sm"
      value={scope.selected ?? ""}
      onChange={(e) => scope.select(e.target.value)}
    >
      {scope.allUnits && <option value={ALL}>{t("unitScope.all")}</option>}
      {scope.units.map((u) => (
        <option key={u.id} value={u.id}>
          {u.name}
        </option>
      ))}
    </select>
  );
}

// Abrangência escolhida, em uma linha, para o Início (sem dados somados: esses vêm nas próximas sprints).
export function ScopeLine() {
  const { t } = useTranslation();
  const scope = useUnitScope();
  if (scope.status !== "ready" || scope.selected === null) return null;
  const unit = scope.units.find((u) => u.id === scope.selected);
  return <p className="mt-2 text-sm text-muted-foreground">{unit ? t("unitScope.scopeOne", { name: unit.name }) : t("unitScope.scopeAll")}</p>;
}
