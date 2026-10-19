import { useTranslation } from "react-i18next";
import { useTeacherFilterOptions } from "./useTeachers";

type Props = { get: (k: string) => string; set: (k: string, v: string) => void };

function Filter({ label, value, onChange, all, options }: { label: string; value: string; onChange: (v: string) => void; all: string; options: { id: string; name: string }[] }) {
  return (
    <label className="flex min-w-0 flex-col gap-1 text-sm">
      {label}
      <select className="min-h-11 rounded-md border bg-background px-2" value={value} onChange={(e) => onChange(e.target.value)}>
        <option value="">{all}</option>
        {options.map((o) => (
          <option key={o.id} value={o.id}>
            {o.name}
          </option>
        ))}
      </select>
    </label>
  );
}

// Filtros de Pessoas › Professores (RF-PRO-01): modalidade, unidade e situação; o nome é a busca da própria tela.
export function TeacherFilters({ get, set }: Props) {
  const { t } = useTranslation();
  const { modalities, units } = useTeacherFilterOptions();
  const statuses = [
    { id: "ACTIVE", name: t("teachers.statuses.ACTIVE") },
    { id: "INACTIVE", name: t("teachers.statuses.INACTIVE") },
  ];
  return (
    <>
      <Filter label={t("teachers.filters.modality")} value={get("modalityId")} onChange={(v) => set("modalityId", v)} all={t("teachers.filters.allModalities")} options={modalities} />
      <Filter label={t("teachers.filters.unit")} value={get("unitId")} onChange={(v) => set("unitId", v)} all={t("teachers.filters.allUnits")} options={units} />
      <Filter label={t("teachers.filters.status")} value={get("status")} onChange={(v) => set("status", v)} all={t("teachers.filters.allStatuses")} options={statuses} />
    </>
  );
}
