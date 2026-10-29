import { useTranslation } from "react-i18next";
import { useClassFilterOptions } from "./useClasses";
import { Select } from "@/components/ui/field";

type Props = { get: (k: string) => string; set: (k: string, v: string) => void };

export function Filter({ label, value, onChange, all, options }: { label: string; value: string; onChange: (v: string) => void; all: string; options: { id: string; name: string }[] }) {
  return (
    <label className="flex min-w-0 flex-col gap-1 text-sm">
      {label}
      <Select className="min-h-11" value={value} onChange={(e) => onChange(e.target.value)}>
        <option value="">{all}</option>
        {options.map((o) => (
          <option key={o.id} value={o.id}>
            {o.name}
          </option>
        ))}
      </Select>
    </label>
  );
}

// Filtros de Operação › Turmas (RF-TUR-01): modalidade, professor, faixa etária, dia, unidade e situação; o nome é a busca da própria tela.
export function ClassFilters({ get, set }: Props) {
  const { t } = useTranslation();
  const { modalities, teachers, ageRanges, units } = useClassFilterOptions();
  const weekdays = [1, 2, 3, 4, 5, 6, 7].map((d) => ({ id: String(d), name: t(`classes.weekdaysLong.${d}`) }));
  const statuses = ["ACTIVE", "INACTIVE"].map((s) => ({ id: s, name: t(`classes.statuses.${s}`) }));
  return (
    <>
      <Filter label={t("classes.filters.modality")} value={get("modalityId")} onChange={(v) => set("modalityId", v)} all={t("classes.filters.allModalities")} options={modalities} />
      <Filter label={t("classes.filters.teacher")} value={get("teacherId")} onChange={(v) => set("teacherId", v)} all={t("classes.filters.allTeachers")} options={teachers} />
      <Filter label={t("classes.filters.ageRange")} value={get("ageRangeId")} onChange={(v) => set("ageRangeId", v)} all={t("classes.filters.allAgeRanges")} options={ageRanges} />
      <Filter label={t("classes.filters.weekday")} value={get("weekday")} onChange={(v) => set("weekday", v)} all={t("classes.filters.allWeekdays")} options={weekdays} />
      <Filter label={t("classes.filters.unit")} value={get("unitId")} onChange={(v) => set("unitId", v)} all={t("classes.filters.allUnits")} options={units} />
      <Filter label={t("classes.filters.status")} value={get("status")} onChange={(v) => set("status", v)} all={t("classes.filters.allStatuses")} options={statuses} />
    </>
  );
}
