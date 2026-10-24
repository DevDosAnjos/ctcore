import { useTranslation } from "react-i18next";
import { WhatsAppButton } from "@/components/whatsapp/WhatsAppButton";
import { formatCurrency, formatDate } from "@/lib/format";
import { StudentCell } from "./ChargeCards";
import type { OverdueCharge } from "./types";

// Referência: mensalidade e parcela mostram o tipo e o mês (MM/AAAA); as demais, a descrição ou o tipo.
export function useOverdueReference() {
  const { t } = useTranslation();
  return (r: OverdueCharge) =>
    r.referenceMonth
      ? t("charges.overdue.reference", { type: t(`charges.types.${r.type}`), month: `${r.referenceMonth.slice(5, 7)}/${r.referenceMonth.slice(0, 4)}` })
      : (r.description ?? t(`charges.types.${r.type}`));
}

export function Student({ row }: { row: OverdueCharge }) {
  return <StudentCell row={row} />;
}

export function Select({ row, checked, onToggle }: { row: OverdueCharge; checked: boolean; onToggle: (row: OverdueCharge) => void }) {
  const { t } = useTranslation();
  return <input type="checkbox" className="size-6" aria-label={t("charges.overdue.selectOne", { student: row.student.name })} checked={checked} onChange={() => onToggle(row)} />;
}

export function LastReminder({ row }: { row: OverdueCharge }) {
  const { t } = useTranslation();
  const last = row.lastReminder;
  return <>{last ? t("charges.overdue.lastReminder", { date: formatDate(last.at), channel: t(`charges.reminder.channel.${last.channel}`) }) : t("charges.overdue.never")}</>;
}

// Até 5 dias para o bloqueio: cor e texto juntos (nunca só cor).
export function DaysToBlock({ row }: { row: OverdueCharge }) {
  const { t } = useTranslation();
  if (row.daysToBlock === null) return <>{t("format.empty")}</>;
  return <span className={row.daysToBlock <= 5 ? "font-medium text-destructive" : undefined}>{t("charges.overdue.daysLate", { count: row.daysToBlock })}</span>;
}

// RN-COM-03: a mensagem é montada aqui e a pessoa envia; o botão some sem telefone válido ou com o WhatsApp desligado.
export function Whatsapp({ row }: { row: OverdueCharge }) {
  const { t } = useTranslation();
  const message = t("charges.overdue.whatsappMessage", { payer: row.payer.name, student: row.student.name, amount: formatCurrency(row.amountCents), due: formatDate(row.dueOn), unit: row.unit.name });
  return <WhatsAppButton phone={row.payer.phone ?? undefined} message={message} />;
}

// Cartão do celular: mesma informação das colunas, em pilha.
export function OverdueCard({ row, checked, onToggle }: { row: OverdueCharge; checked: boolean; onToggle: (row: OverdueCharge) => void }) {
  const { t } = useTranslation();
  const reference = useOverdueReference();
  return (
    <div className="flex flex-col gap-2">
      <div className="flex items-center gap-3">
        <Select row={row} checked={checked} onToggle={onToggle} />
        <Student row={row} />
      </div>
      <p className="break-words">{reference(row)}</p>
      <dl className="grid grid-cols-2 gap-x-3 gap-y-1 text-sm">
        <dt className="text-muted-foreground">{t("charges.overdue.columns.unit")}</dt>
        <dd>{row.unit.name}</dd>
        <dt className="text-muted-foreground">{t("charges.overdue.columns.amount")}</dt>
        <dd>{formatCurrency(row.amountCents)}</dd>
        <dt className="text-muted-foreground">{t("charges.overdue.columns.dueOn")}</dt>
        <dd>{formatDate(row.dueOn)}</dd>
        <dt className="text-muted-foreground">{t("charges.overdue.columns.daysLate")}</dt>
        <dd className="font-medium text-destructive">{t("charges.overdue.daysLate", { count: row.daysLate })}</dd>
        <dt className="text-muted-foreground">{t("charges.overdue.columns.lastReminder")}</dt>
        <dd>
          <LastReminder row={row} />
        </dd>
        <dt className="text-muted-foreground">{t("charges.overdue.columns.daysToBlock")}</dt>
        <dd>
          <DaysToBlock row={row} />
        </dd>
      </dl>
      <Whatsapp row={row} />
    </div>
  );
}
