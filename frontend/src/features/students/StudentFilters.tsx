import { useTranslation } from "react-i18next";
import { useGraduation } from "@/features/modalities/useGraduation";
import { useStudentFilterOptions } from "./useStudents";

const SEALS = ["INCOMPLETE_REGISTRATION", "EMAIL_PROBLEM", "BIRTHDAY_TODAY", "NEW"];
const QUICK = ["ACTIVE", "FORMER"] as const;

type Props = { get: (k: string) => string; set: (k: string, v: string) => void; counts?: Record<string, number> };

function Filter({ label, value, onChange, all, options, disabled }: { label: string; value: string; onChange: (v: string) => void; all: string; options: { id: string; name: string }[]; disabled?: boolean }) {
  return (
    <label className="flex min-w-0 flex-col gap-1 text-sm">
      {label}
      <select className="min-h-11 rounded-md border bg-background px-2" value={value} disabled={disabled} onChange={(e) => onChange(e.target.value)}>
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

// Filtros de Pessoas › Alunos (RF-ALU-01): unidade de cadastro, faixa etária, selo (RF-ALU-02), modalidade, turma e nível; o nome é a busca da própria tela.
// Os filtros rápidos Ativos e Ex-alunos mostram a contagem do servidor e se desligam com o segundo clique.
export function StudentFilters({ get, set, counts }: Props) {
  const { t } = useTranslation();
  const { units, ageRanges, modalities, classes } = useStudentFilterOptions();
  const modalityId = get("modalityId");
  const graduation = useGraduation(modalityId || null);
  const seals = SEALS.map((id) => ({ id, name: t(`students.seals.${id}`) }));
  const levels = graduation.data?.levels ?? [];
  const status = get("status");
  const countOf = (s: (typeof QUICK)[number]) => (counts ? (s === "ACTIVE" ? counts.active : counts.former) : undefined);
  return (
    <>
      <div role="group" aria-label={t("students.quick.label")} className="flex w-full flex-wrap gap-2">
        {QUICK.map((s) => (
          <button
            key={s}
            type="button"
            aria-pressed={status === s}
            className="inline-flex min-h-11 items-center rounded-md border px-3 text-sm aria-pressed:bg-primary aria-pressed:text-primary-foreground"
            onClick={() => set("status", status === s ? "" : s)}
          >
            {countOf(s) === undefined ? t(`students.quick.${s}`) : t("students.quick.withCount", { label: t(`students.quick.${s}`), count: countOf(s) })}
          </button>
        ))}
      </div>
      <Filter label={t("students.filters.unit")} value={get("unitId")} onChange={(v) => set("unitId", v)} all={t("students.filters.allUnits")} options={units} />
      <Filter label={t("students.filters.ageRange")} value={get("ageRangeId")} onChange={(v) => set("ageRangeId", v)} all={t("students.filters.allAgeRanges")} options={ageRanges} />
      <Filter label={t("students.filters.seal")} value={get("seal")} onChange={(v) => set("seal", v)} all={t("students.filters.allSeals")} options={seals} />
      <Filter
        label={t("students.filters.modality")}
        value={modalityId}
        onChange={(v) => {
          set("levelId", "");
          set("modalityId", v);
        }}
        all={t("students.filters.allModalities")}
        options={modalities}
      />
      <Filter label={t("students.filters.class")} value={get("classId")} onChange={(v) => set("classId", v)} all={t("students.filters.allClasses")} options={classes} />
      <Filter label={t("students.filters.level")} value={get("levelId")} onChange={(v) => set("levelId", v)} all={t("students.filters.allLevels")} options={levels} disabled={!modalityId} />
    </>
  );
}
