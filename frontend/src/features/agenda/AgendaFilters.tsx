import { useTranslation } from "react-i18next";
import type { AgendaData } from "./useAgenda";

export interface FilterValues {
  unitId: string;
  teacherId: string;
  modalityId: string;
}

function Select({ label, value, onChange, all, options }: { label: string; value: string; onChange: (v: string) => void; all?: string; options: { id: string; name: string }[] }) {
  return (
    <label className="flex min-w-0 flex-col gap-1 text-sm">
      {label}
      <select className="min-h-11 rounded-md border bg-background px-2" value={value} onChange={(e) => onChange(e.target.value)}>
        {all !== undefined && <option value="">{all}</option>}
        {options.map((o) => (
          <option key={o.id} value={o.id}>
            {o.name}
          </option>
        ))}
      </select>
    </label>
  );
}

// R13: professor e modalidade; a unidade só aparece quando o alcance tem mais de uma.
export function AgendaFilters({ options, values, onChange }: { options?: AgendaData; values: FilterValues; onChange: (v: Partial<FilterValues>) => void }) {
  const { t } = useTranslation();
  return (
    <div className="grid grid-cols-1 gap-2 sm:grid-cols-3">
      {options && options.units.length > 1 && (
        <Select
          label={t("agenda.filters.unit")}
          value={values.unitId || options.unit?.id || ""}
          onChange={(unitId) => onChange({ unitId, teacherId: "", modalityId: "" })}
          options={options.units}
        />
      )}
      <Select label={t("agenda.filters.teacher")} value={values.teacherId} onChange={(teacherId) => onChange({ teacherId })} all={t("agenda.filters.allTeachers")} options={options?.teachers ?? []} />
      <Select label={t("agenda.filters.modality")} value={values.modalityId} onChange={(modalityId) => onChange({ modalityId })} all={t("agenda.filters.allModalities")} options={options?.modalities ?? []} />
    </div>
  );
}
