import { useTranslation } from "react-i18next";
import { Filter } from "@/features/classes/ClassFilters";
import { usePlanFilterOptions } from "./usePlans";

// Filtros de Financeiro › Planos (RF-PLA-02): modalidade, unidade e situação; o nome é a busca da própria tela.
export function PlanFilters({ get, set }: { get: (k: string) => string; set: (k: string, v: string) => void }) {
  const { t } = useTranslation();
  const { modalities, units } = usePlanFilterOptions();
  const statuses = ["ACTIVE", "INACTIVE"].map((s) => ({ id: s, name: t(`plans.table.statuses.${s}`) }));
  return (
    <>
      <Filter label={t("plans.table.filters.modality")} value={get("modalityId")} onChange={(v) => set("modalityId", v)} all={t("plans.table.filters.allModalities")} options={modalities} />
      <Filter label={t("plans.table.filters.unit")} value={get("unitId")} onChange={(v) => set("unitId", v)} all={t("plans.table.filters.allUnits")} options={units} />
      <Filter label={t("plans.table.filters.status")} value={get("status")} onChange={(v) => set("status", v)} all={t("plans.table.filters.allStatuses")} options={statuses} />
    </>
  );
}
