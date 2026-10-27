import { useTranslation } from "react-i18next";
import { Link } from "react-router";
import { useStudentFilterOptions } from "@/features/students/useStudents";
import { QUICK, SITUATIONS, TYPES, type ChargeQuick } from "./types";

type Props = { get: (k: string) => string; set: (k: string, v: string) => void; counts?: Record<string, number> };

const COUNT_KEY: Record<ChargeQuick, string> = { OVERDUE: "overdue", DUE_TODAY: "dueToday", IN_REVIEW: "inReview", PAID_MONTH: "paidMonth" };

function Select({ label, value, onChange, all, options }: { label: string; value: string; onChange: (v: string) => void; all: string; options: { id: string; name: string }[] }) {
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

function DateField({ label, value, onChange }: { label: string; value: string; onChange: (v: string) => void }) {
  return (
    <label className="flex min-w-0 flex-col gap-1 text-sm">
      {label}
      <input type="date" className="min-h-11 rounded-md border bg-background px-2" value={value} onChange={(e) => onChange(e.target.value)} />
    </label>
  );
}

// Filtros de Financeiro › Cobranças (RF-COB-01): atalhos com contagem (segundo clique desliga), período de vencimento, situação, tipo, unidade, modalidade e turma; o aluno é a busca da tela.
export function ChargeFilters({ get, set, counts }: Props) {
  const { t } = useTranslation();
  const { units, modalities, classes } = useStudentFilterOptions();
  const quick = get("quick");
  return (
    <>
      <div role="group" aria-label={t("charges.quick.label")} className="flex w-full flex-wrap gap-2">
        {QUICK.map((q) => {
          const label = t(`charges.quick.${q}`);
          const count = counts?.[COUNT_KEY[q]];
          return (
            <button
              key={q}
              type="button"
              aria-pressed={quick === q}
              className="inline-flex min-h-11 items-center rounded-md border px-3 text-sm aria-pressed:bg-primary aria-pressed:text-primary-foreground"
              onClick={() => set("quick", quick === q ? "" : q)}
            >
              {count === undefined ? label : t("charges.quick.withCount", { label, count })}
            </button>
          );
        })}
        {quick === "IN_REVIEW" && (
          <Link to="/cobrancas/conferencia" className="inline-flex min-h-11 items-center text-sm underline">
            {t("charges.quick.openReview")}
          </Link>
        )}
      </div>
      <DateField label={t("charges.filters.from")} value={get("from")} onChange={(v) => set("from", v)} />
      <DateField label={t("charges.filters.to")} value={get("to")} onChange={(v) => set("to", v)} />
      <Select label={t("charges.filters.status")} value={get("status")} onChange={(v) => set("status", v)} all={t("charges.filters.allStatuses")} options={SITUATIONS.map((id) => ({ id, name: t(`charges.status.${id}`) }))} />
      <Select label={t("charges.filters.type")} value={get("type")} onChange={(v) => set("type", v)} all={t("charges.filters.allTypes")} options={TYPES.map((id) => ({ id, name: t(`charges.types.${id}`) }))} />
      <Select label={t("charges.filters.unit")} value={get("unitId")} onChange={(v) => set("unitId", v)} all={t("charges.filters.allUnits")} options={units} />
      <Select label={t("charges.filters.modality")} value={get("modalityId")} onChange={(v) => set("modalityId", v)} all={t("charges.filters.allModalities")} options={modalities} />
      <Select label={t("charges.filters.class")} value={get("classId")} onChange={(v) => set("classId", v)} all={t("charges.filters.allClasses")} options={classes} />
    </>
  );
}
