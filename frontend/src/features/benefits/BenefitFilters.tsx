import { useTranslation } from "react-i18next";
import { Filter } from "@/features/classes/ClassFilters";
import { useBenefitFilterOptions } from "./useBenefits";

// Filtros de Financeiro › Benefícios (RF-BEN-01): tipo, modalidade, unidade, situação e Benefícios altos; o nome do aluno é a busca da tela.
export function BenefitFilters({ get, set }: { get: (k: string) => string; set: (k: string, v: string) => void }) {
  const { t } = useTranslation();
  const { modalities, units } = useBenefitFilterOptions();
  const types = ["PERCENT", "AMOUNT"].map((s) => ({ id: s, name: t(`benefits.types.${s}`) }));
  const statuses = ["ACTIVE", "EXPIRED", "ENDED"].map((s) => ({ id: s, name: t(`benefits.statuses.${s}`) }));
  return (
    <>
      <Filter label={t("benefits.filters.type")} value={get("type")} onChange={(v) => set("type", v)} all={t("benefits.filters.allTypes")} options={types} />
      <Filter label={t("benefits.filters.modality")} value={get("modalityId")} onChange={(v) => set("modalityId", v)} all={t("benefits.filters.allModalities")} options={modalities} />
      <Filter label={t("benefits.filters.unit")} value={get("unitId")} onChange={(v) => set("unitId", v)} all={t("benefits.filters.allUnits")} options={units} />
      <Filter label={t("benefits.filters.status")} value={get("status")} onChange={(v) => set("status", v)} all={t("benefits.filters.allStatuses")} options={statuses} />
      <label className="flex min-h-11 items-center gap-2 self-end text-sm">
        <input type="checkbox" className="size-5" checked={get("high") === "true"} onChange={(e) => set("high", e.target.checked ? "true" : "")} />
        {t("benefits.filters.high")}
      </label>
    </>
  );
}
