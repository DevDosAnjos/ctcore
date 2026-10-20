import { useTranslation } from "react-i18next";
import { useStudentFilterOptions } from "./useStudents";

const SEALS = ["INCOMPLETE_REGISTRATION", "EMAIL_PROBLEM", "BIRTHDAY_TODAY"];

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

// Filtros de Pessoas › Alunos (RF-ALU-01): unidade de cadastro, faixa etária e selo (RF-ALU-02); o nome é a busca da própria tela.
export function StudentFilters({ get, set }: Props) {
  const { t } = useTranslation();
  const { units, ageRanges } = useStudentFilterOptions();
  const seals = SEALS.map((id) => ({ id, name: t(`students.seals.${id}`) }));
  return (
    <>
      <Filter label={t("students.filters.unit")} value={get("unitId")} onChange={(v) => set("unitId", v)} all={t("students.filters.allUnits")} options={units} />
      <Filter label={t("students.filters.ageRange")} value={get("ageRangeId")} onChange={(v) => set("ageRangeId", v)} all={t("students.filters.allAgeRanges")} options={ageRanges} />
      <Filter label={t("students.filters.seal")} value={get("seal")} onChange={(v) => set("seal", v)} all={t("students.filters.allSeals")} options={seals} />
    </>
  );
}
