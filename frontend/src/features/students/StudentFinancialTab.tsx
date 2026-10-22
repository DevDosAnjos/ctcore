import { useState } from "react";
import { useTranslation } from "react-i18next";
import { EmptyState, ErrorState, ForbiddenState, LoadingState } from "@/components/states";
import { StatusBadge, useReference } from "@/features/charges/ChargeCards";
import { ChargePanel } from "@/features/charges/ChargePanel";
import type { ChargeList } from "@/features/charges/types";
import { formatCurrency, formatDate } from "@/lib/format";

type Charges = { status: string; data?: ChargeList; reload: () => void };

// Situação financeira do cabeçalho (R14): Em atraso se alguma cobrança está em atraso, senão Em dia.
export const isOverdue = (data?: ChargeList) => !!data?.items.some((c) => c.status === "OVERDUE");

// RF-COB-02, R14: cobranças do aluno (situação com cor e texto, vencimento, valor, meio quando paga); cada uma abre o painel.
export function StudentFinancialTab({ charges, onChanged }: { charges: Charges; onChanged: () => void }) {
  const { t } = useTranslation();
  const reference = useReference();
  const [open, setOpen] = useState<string>();
  if (charges.status === "loading" || charges.status === "idle") return <LoadingState />;
  if (charges.status === "forbidden") return <ForbiddenState />;
  if (charges.status !== "ready" || !charges.data) return <ErrorState onRetry={charges.reload} />;
  if (charges.data.items.length === 0) return <EmptyState title={t("students.record.financial.emptyTitle")} text={t("students.record.financial.emptyText")} />;
  return (
    <>
      <ul className="flex flex-col gap-2">
        {charges.data.items.map((c) => (
          <li key={c.id} className="rounded-md border p-2">
            <button
              type="button"
              className="flex min-h-11 w-full flex-col gap-1 text-left"
              aria-label={t("charges.panel.open", { student: c.student.name, reference: reference(c) })}
              onClick={() => setOpen(c.id)}
            >
              <span className="flex flex-wrap items-center justify-between gap-2">
                <span className="break-words font-medium">{reference(c)}</span>
                <StatusBadge status={c.status} />
              </span>
              <span className="text-sm">
                {t("students.record.financial.line", { due: formatDate(c.dueOn), amount: formatCurrency(c.amountCents) })}
                {c.payment && ` · ${t(`payments.method.${c.payment.method}`)}`}
              </span>
            </button>
          </li>
        ))}
      </ul>
      {open && (
        <ChargePanel
          id={open}
          studentLink={false}
          onClose={() => setOpen(undefined)}
          onChanged={() => {
            charges.reload();
            onChanged();
          }}
        />
      )}
    </>
  );
}
