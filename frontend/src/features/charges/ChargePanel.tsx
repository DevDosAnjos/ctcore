import { useState, type ReactNode } from "react";
import { useTranslation } from "react-i18next";
import { Link } from "react-router";
import { SidePanel } from "@/components/SidePanel";
import { EmptyState, ErrorState, ForbiddenState, LoadingState } from "@/components/states";
import { Button } from "@/components/ui/button";
import { formatCurrency, formatDate, formatDateTime } from "@/lib/format";
import { StatusBadge, useReference } from "./ChargeCards";
import { CancelChargeDialog } from "./CancelChargeDialog";
import { RegisterPaymentDialog } from "@/features/payments/RegisterPaymentDialog";
import { receiptPdfUrl } from "@/features/payments/usePayment";
import { RenegotiateDialog } from "./RenegotiateDialog";
import type { ChargeDetail, ChargeRef } from "./types";
import { useChargeDetail } from "./useChargeActions";

const DISABLED = ["sendReminder", "refund"] as const;

function Block({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section className="flex flex-col gap-1">
      <h3 className="text-sm font-semibold">{title}</h3>
      {children}
    </section>
  );
}

function Refs({ refs }: { refs: ChargeRef[] }) {
  const { t } = useTranslation();
  return (
    <ul className="text-sm">
      {refs.map((r) => (
        <li key={r.id}>{t("charges.panel.ref", { type: t(`charges.types.${r.type}`), amount: formatCurrency(r.amountCents), due: formatDate(r.dueOn), status: t(`charges.stored.${r.status}`) })}</li>
      ))}
    </ul>
  );
}

function Details({ d, studentLink, onChanged }: { d: ChargeDetail; studentLink: boolean; onChanged: () => void }) {
  const { t } = useTranslation();
  const reference = useReference();
  const [dialog, setDialog] = useState<"cancel" | "renegotiate" | "pay">();
  const [paid, setPaid] = useState(false);
  const { charge, composition, actions } = d;
  const done = () => onChanged();
  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-col gap-1">
        <p className="break-words text-lg font-semibold">{charge.student.name}</p>
        <p className="break-words">{reference(charge)}</p>
        <div className="flex flex-wrap items-center gap-2">
          <StatusBadge status={charge.status} />
          <span className="font-medium">{formatCurrency(charge.amountCents)}</span>
        </div>
        <dl className="grid grid-cols-2 gap-x-3 gap-y-1 text-sm">
          <dt className="text-muted-foreground">{t("charges.columns.unit")}</dt>
          <dd>{charge.unit.name}</dd>
          <dt className="text-muted-foreground">{t("charges.columns.dueOn")}</dt>
          <dd>{formatDate(charge.dueOn)}</dd>
          <dt className="text-muted-foreground">{t("charges.panel.tolerance")}</dt>
          <dd>{t("charges.panel.days", { count: d.toleranceDays })}</dd>
          <dt className="text-muted-foreground">{t("charges.panel.payer")}</dt>
          <dd className="break-words">{d.payer.name}</dd>
        </dl>
      </div>

      <Block title={t("charges.panel.composition")}>
        <dl className="grid grid-cols-2 gap-x-3 gap-y-1 text-sm">
          <dt className="text-muted-foreground">{t("charges.panel.plan")}</dt>
          <dd className="break-words">{composition.plan ? `${composition.plan.name} · ${formatCurrency(composition.planCents)}` : t("format.empty")}</dd>
          <dt className="text-muted-foreground">{t("charges.panel.benefit")}</dt>
          <dd>{composition.benefitCents > 0 ? `− ${formatCurrency(composition.benefitCents)}` : t("format.empty")}</dd>
          <dt className="text-muted-foreground">{t("charges.panel.final")}</dt>
          <dd className="font-medium">{formatCurrency(composition.amountCents)}</dd>
        </dl>
      </Block>

      {(d.origin || d.replacedBy.length > 0 || d.reason) && (
        <Block title={t("charges.panel.history")}>
          {d.origin && (
            <>
              <p className="text-sm text-muted-foreground">{t("charges.panel.origin")}</p>
              <Refs refs={[d.origin]} />
            </>
          )}
          {d.replacedBy.length > 0 && (
            <>
              <p className="text-sm text-muted-foreground">{t("charges.panel.replacedBy")}</p>
              <Refs refs={d.replacedBy} />
            </>
          )}
          {d.reason && <p className="break-words text-sm">{t("charges.panel.reason", { reason: d.reason })}</p>}
          {d.canceledAt && <p className="text-sm">{t("charges.panel.canceled", { at: formatDateTime(d.canceledAt), by: d.canceledBy?.name ?? t("format.empty") })}</p>}
        </Block>
      )}

      <Block title={t("charges.panel.payments")}>
        {d.payments.length === 0 ? (
          <p className="text-sm text-muted-foreground">{t("charges.panel.noPayments")}</p>
        ) : (
          <ul className="flex flex-col gap-2 text-sm">
            {d.payments.map((p) => (
              <li key={p.id} className="rounded-md border p-2">
                <p>
                  {formatCurrency(p.amountCents)} · {t(`payments.method.${p.method}`)} · {formatDateTime(p.paidAt)}
                </p>
                <p className="text-muted-foreground">{t("charges.panel.registeredBy", { name: p.registeredBy?.name ?? t(`charges.panel.origins.${p.origin}`) })}</p>
                {p.observation && <p className="break-words">{p.observation}</p>}
                {p.status === "REFUNDED" && <p>{t("charges.status.REFUNDED")}</p>}
                {p.receipt && (
                  <p>
                    {t("charges.panel.receipt", { number: p.receipt.number, code: p.receipt.code })}{" "}
                    <a className="underline" href={receiptPdfUrl(p.receipt.code)} target="_blank" rel="noreferrer">
                      {t("charges.panel.downloadReceipt")}
                    </a>
                  </p>
                )}
              </li>
            ))}
          </ul>
        )}
      </Block>

      <Block title={t("charges.panel.reminders")}>
        {d.reminders.length === 0 && <p className="text-sm text-muted-foreground">{t("charges.panel.noReminders")}</p>}
      </Block>

      <div className="flex flex-col gap-2">
        <div className="flex flex-wrap gap-2">
          {actions.cancel && (
            <Button type="button" variant="outline" className="min-h-11" onClick={() => setDialog("cancel")}>
              {t("charges.panel.cancel")}
            </Button>
          )}
          {actions.renegotiate && (
            <Button type="button" variant="outline" className="min-h-11" onClick={() => setDialog("renegotiate")}>
              {t("charges.panel.renegotiate")}
            </Button>
          )}
          {studentLink && (
            <Button asChild variant="outline" className="min-h-11">
              <Link to={`/alunos/${charge.student.id}`}>{t("charges.panel.seeStudent")}</Link>
            </Button>
          )}
        </div>
        <div className="flex flex-col">
          <Button type="button" variant="outline" className="min-h-11 self-start" disabled={!actions.registerPayment} aria-describedby={actions.registerPayment ? undefined : "why-registerPayment"} onClick={() => setDialog("pay")}>
            {t("charges.panel.registerPayment")}
          </Button>
          {!actions.registerPayment && (
            <p id="why-registerPayment" className="text-sm text-muted-foreground">
              {t("charges.panel.disabled.registerPayment")}
            </p>
          )}
        </div>
        {DISABLED.map((k) => (
          <div key={k} className="flex flex-col">
            <Button type="button" variant="outline" className="min-h-11 self-start" disabled aria-describedby={`why-${k}`}>
              {t(`charges.panel.${k}`)}
            </Button>
            <p id={`why-${k}`} className="text-sm text-muted-foreground">
              {t(`charges.panel.disabled.${k}`)}
            </p>
          </div>
        ))}
      </div>

      {dialog === "cancel" && <CancelChargeDialog charge={charge} onClose={() => setDialog(undefined)} onDone={done} />}
      {dialog === "pay" && (
        <RegisterPaymentDialog
          chargeId={charge.id}
          label={reference(charge)}
          amountCents={charge.amountCents}
          onPaid={() => setPaid(true)}
          onClose={() => {
            setDialog(undefined);
            if (paid) done();
          }}
        />
      )}
      {dialog === "renegotiate" && <RenegotiateDialog charges={[charge]} onClose={() => setDialog(undefined)} onDone={done} />}
    </div>
  );
}

function Body({ id, studentLink, onChanged }: { id: string; studentLink: boolean; onChanged?: () => void }) {
  const { t } = useTranslation();
  const { status, data, reload } = useChargeDetail(id);
  if (status === "loading") return <LoadingState />;
  if (status === "forbidden") return <ForbiddenState />;
  if (status === "notFound") return <EmptyState title={t("charges.panel.notFound")} text={t("charges.panel.notFoundText")} />;
  if (status === "error" || !data) return <ErrorState onRetry={reload} />;
  return (
    <Details
      d={data}
      studentLink={studentLink}
      onChanged={() => {
        reload();
        onChanged?.();
      }}
    />
  );
}

// RF-COB-02: painel da cobrança em painel lateral (tela cheia no celular). O servidor decide o que cada perfil vê e quais ações valem (`actions`).
export function ChargePanel({ id, onClose, onChanged, studentLink = true }: { id: string; onClose: () => void; onChanged?: () => void; studentLink?: boolean }) {
  const { t } = useTranslation();
  return (
    <SidePanel open onOpenChange={(open) => !open && onClose()} title={t("charges.panel.title")}>
      <Body key={id} id={id} studentLink={studentLink} onChanged={onChanged} />
    </SidePanel>
  );
}
