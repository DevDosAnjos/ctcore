import { useTranslation } from "react-i18next";
import { StatusBadge as Badge } from "@/components/ui/badge";
import { formatCurrency, formatDate } from "@/lib/format";
import type { ChargeRow, ChargeSituation } from "./types";

const initials = (name: string) =>
  name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((w) => w[0].toUpperCase())
    .join("");

export function StatusBadge({ status }: { status: ChargeSituation }) {
  const { t } = useTranslation();
  return <Badge status={status} label={t(`charges.status.${status}`)} />;
}

export function StudentCell({ row }: { row: Pick<ChargeRow, "student"> }) {
  const { name, photoUrl } = row.student;
  return (
    <span className="flex items-center gap-2">
      {photoUrl ? (
        <img src={photoUrl} alt="" className="size-10 shrink-0 rounded-full object-cover" />
      ) : (
        <span aria-hidden="true" className="flex size-10 shrink-0 items-center justify-center rounded-full bg-muted text-sm font-semibold">
          {initials(name)}
        </span>
      )}
      <span className="min-w-0 break-words font-medium">{name}</span>
    </span>
  );
}

// Referência: mensalidade mostra modalidade e mês (MM/AAAA); as demais, a descrição ou o tipo.
export function useReference() {
  const { t } = useTranslation();
  return (r: ChargeRow) =>
    r.modality && r.referenceMonth
      ? t("charges.reference", { modality: r.modality.name, month: `${r.referenceMonth.slice(5, 7)}/${r.referenceMonth.slice(0, 4)}` })
      : (r.description ?? t(`charges.types.${r.type}`));
}

// A referência abre o painel da cobrança (RF-COB-02).
export function OpenButton({ row, onOpen }: { row: ChargeRow; onOpen: (id: string) => void }) {
  const { t } = useTranslation();
  const reference = useReference();
  return (
    <button type="button" aria-label={t("charges.panel.open", { student: row.student.name, reference: reference(row) })} className="inline-flex min-h-11 items-center break-words text-left underline-offset-2 hover:underline" onClick={() => onOpen(row.id)}>
      {reference(row)}
    </button>
  );
}

// Cartão do celular: mesma informação das colunas, em pilha.
export function ChargeCard({ row, onOpen }: { row: ChargeRow; onOpen?: (id: string) => void }) {
  const { t } = useTranslation();
  const reference = useReference();
  return (
    <div className="flex flex-col gap-2">
      <StudentCell row={row} />
      <div className="flex flex-wrap items-center justify-between gap-2">
        {onOpen ? <OpenButton row={row} onOpen={onOpen} /> : <span className="break-words">{reference(row)}</span>}
        <StatusBadge status={row.status} />
      </div>
      <dl className="grid grid-cols-2 gap-x-3 gap-y-1 text-sm">
        <dt className="text-muted-foreground">{t("charges.columns.amount")}</dt>
        <dd>{formatCurrency(row.amountCents)}</dd>
        <dt className="text-muted-foreground">{t("charges.columns.dueOn")}</dt>
        <dd>{formatDate(row.dueOn)}</dd>
        <dt className="text-muted-foreground">{t("charges.columns.unit")}</dt>
        <dd>{row.unit.name}</dd>
        {row.payment && (
          <>
            <dt className="text-muted-foreground">{t("charges.columns.method")}</dt>
            <dd>{t(`payments.method.${row.payment.method}`)}</dd>
          </>
        )}
      </dl>
    </div>
  );
}
