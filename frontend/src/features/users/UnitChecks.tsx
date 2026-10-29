import { useTranslation } from "react-i18next";
import { Checkbox } from "@/components/ui/checkbox";
import type { ReachableUnit } from "@/features/units/UnitScopeProvider";

// Escolha das unidades por caixas de marcação; o erro fica ligado ao grupo.
export function UnitChecks({ units, value, onChange, error }: { units: ReachableUnit[]; value: string[]; onChange: (v: string[]) => void; error?: string }) {
  const { t } = useTranslation();
  return (
    <fieldset aria-describedby={error ? "unit-checks-erro" : undefined}>
      <legend className="text-sm font-medium">{t("users.units")}</legend>
      {units.map((u) => (
        <label key={u.id} className="flex min-h-11 items-center gap-2">
          <Checkbox
            checked={value.includes(u.id)}
            onChange={(e) => onChange(e.target.checked ? [...value, u.id] : value.filter((id) => id !== u.id))}
          />
          {u.name}
        </label>
      ))}
      {error && (
        <p id="unit-checks-erro" className="text-xs text-danger-foreground">
          {error}
        </p>
      )}
    </fieldset>
  );
}
