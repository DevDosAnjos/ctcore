import { useState } from "react";
import { useTranslation } from "react-i18next";
import { useSearchParams } from "react-router";
import { ErrorState, ForbiddenState, LoadingState } from "@/components/states";
import { formatCurrency } from "@/lib/format";
import { PortalAutoDebitCard } from "./PortalAutoDebitCard";
import { PortalChargeHistory } from "./PortalChargeHistory";
import { PortalLayout } from "./PortalLayout";
import { PortalOpenCharges } from "./PortalOpenCharges";
import { usePortalFinance, type PortalFinanceData } from "./usePortalFinance";

const FAMILY = "FAMILY";

// RF-POR-05: Financeiro do aluno em `?aluno=`, `?visao=familia` e `?ano=` (padrão: primeiro cartão, só ele, ano de hoje). Abre também no portal reduzido (RN-POR-08).
export function PortalFinancePage() {
  const { t } = useTranslation();
  const [params, setParams] = useSearchParams();
  const student = params.get("aluno");
  const family = params.get("visao") === "familia";
  const year = params.get("ano");
  const { status, data, reload } = usePortalFinance(student, family ? FAMILY : "STUDENT", year);
  // Os controles ficam na tela enquanto a leitura nova carrega.
  const [last, setLast] = useState<PortalFinanceData>();
  if (data && data !== last) setLast(data);
  const shown = data ?? last;

  const change = (patch: Record<string, string | null>) => {
    const next = new URLSearchParams(params);
    for (const [k, v] of Object.entries(patch)) {
      if (v === null) next.delete(k);
      else next.set(k, v);
    }
    setParams(next, { replace: true });
  };
  const choose = (value: string) => (value === FAMILY ? change({ visao: "familia", ano: null }) : change({ aluno: value, visao: null, ano: null }));

  return (
    <PortalLayout>
      <h1 className="mb-4 text-2xl font-semibold">{t("portalShell.tabs.finance")}</h1>
      {shown && shown.students.length > 1 && (
        <div className="mb-4 flex flex-col gap-2">
          <label className="flex flex-col gap-1">
            <span className="text-sm font-medium">{t("portalFinance.student")}</span>
            <select className="min-h-11 rounded-md border bg-background px-2" value={family ? FAMILY : (student ?? shown.selectedStudentId ?? "")} onChange={(e) => choose(e.target.value)}>
              {shown.students.map((s) => (
                <option key={s.id} value={s.id}>
                  {s.self ? t("portalFinance.you") : s.name}
                </option>
              ))}
              <option value={FAMILY}>{t("portalFinance.family")}</option>
            </select>
          </label>
          {family && <p className="font-medium">{t("portalFinance.familyTotal", { value: formatCurrency(shown.familyOpenCents) })}</p>}
        </div>
      )}
      {status === "loading" || status === "idle" ? (
        <LoadingState />
      ) : status === "forbidden" ? (
        <ForbiddenState />
      ) : status === "ready" && data ? (
        <div className="flex flex-col gap-4">
          <PortalOpenCharges items={data.open} showStudent={family} onChanged={reload} />
          {!family && <PortalChargeHistory items={data.history} year={data.year} years={data.years} onYear={(y) => change({ ano: String(y) })} />}
          <PortalAutoDebitCard />
        </div>
      ) : (
        <ErrorState onRetry={reload} />
      )}
    </PortalLayout>
  );
}
