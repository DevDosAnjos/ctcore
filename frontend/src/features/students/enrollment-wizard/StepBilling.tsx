import { useTranslation } from "react-i18next";
import { formatMoney } from "@/components/fields/masks";
import { ErrorState, LoadingState } from "@/components/states";
import { Text } from "@/features/units/Text";
import { PickSelect } from "../ModalityPickers";
import { useResource } from "../useStudentRecord";
import type { UnitDraft } from "./types";

interface UnitInfo {
  name: string;
  settings: { dueDays: number[]; enrollmentFeeEnabled: boolean; enrollmentFeeCents: number };
}

const ALL_DAYS = Array.from({ length: 28 }, (_, i) => i + 1);

function UnitBlock({ unitId, name, draft, inherited, errors, onChange }: { unitId: string; name: string; draft: UnitDraft; inherited?: number; errors: Record<string, string>; onChange: (d: UnitDraft) => void }) {
  const { t } = useTranslation();
  const k = "enrollmentWizard.billing.";
  const info = useResource<UnitInfo>(`/units/${unitId}`);
  // Sem a configuração da unidade (alcance de leitura menor), o servidor decide os dias e a taxa; o resumo mostra o resultado.
  const settings = info.status === "ready" ? info.data?.settings : undefined;
  const days = inherited ? [inherited] : (settings?.dueDays ?? ALL_DAYS);
  const set = (patch: Partial<UnitDraft>) => onChange({ ...draft, ...patch });
  if (info.status === "loading") return <LoadingState />;
  return (
    <section aria-label={name} className="flex flex-col gap-3 rounded-md border p-3">
      <h2 className="font-medium">{name}</h2>
      {info.status === "error" && <ErrorState onRetry={info.reload} />}
      <PickSelect
        label={t(`${k}dueDay`)}
        value={inherited ? String(inherited) : draft.dueDay}
        onChange={(v) => set({ dueDay: v })}
        options={days.map((d) => ({ id: String(d), name: t(`${k}dayOption`, { day: d }) }))}
        error={errors.dueDay}
      />
      {inherited && <p className="text-sm text-muted-foreground">{t(`${k}dueInherited`, { day: inherited })}</p>}
      {settings?.enrollmentFeeEnabled && (
        <fieldset className="flex flex-col gap-2">
          <legend className="text-sm font-medium">{t(`${k}fee`, { value: formatMoney(settings.enrollmentFeeCents) })}</legend>
          <p className="text-sm text-muted-foreground">{t(`${k}feeHelp`)}</p>
          <label className="flex min-h-11 items-center gap-2">
            <input type="checkbox" className="size-5" checked={draft.feeExempt} onChange={(e) => set({ feeExempt: e.target.checked })} />
            {t(`${k}exempt`)}
          </label>
          {draft.feeExempt && <Text label={t(`${k}exemptReason`)} value={draft.feeExemptReason} onChange={(v) => set({ feeExemptReason: v })} maxLength={200} error={errors.feeExemptReason} />}
        </fieldset>
      )}
      <Text label={t(`${k}secondDue`)} type="date" value={draft.secondDueOn} onChange={(v) => set({ secondDueOn: v })} help={t(`${k}secondDueHelp`)} error={errors.secondDueOn} />
    </section>
  );
}

// Etapa 3 (RF-MAT-01, RN-MAT-03, 05, 07): um dia de vencimento por unidade, a taxa com isenção e a segunda cobrança, que o servidor sugere e a pessoa pode editar.
export function StepBilling({
  units,
  drafts,
  inherited,
  errors,
  onChange,
}: {
  units: { id: string; name: string }[];
  drafts: Record<string, UnitDraft>;
  inherited: Record<string, number>;
  errors: Record<string, string>;
  onChange: (unitId: string, d: UnitDraft) => void;
}) {
  const { t } = useTranslation();
  return (
    <div className="flex flex-col gap-3">
      <p className="text-sm text-muted-foreground">{t("enrollmentWizard.billing.intro")}</p>
      {units.map((u) => (
        <UnitBlock key={u.id} unitId={u.id} name={u.name} draft={drafts[u.id]} inherited={inherited[u.id]} errors={errors} onChange={(d) => onChange(u.id, d)} />
      ))}
    </div>
  );
}
