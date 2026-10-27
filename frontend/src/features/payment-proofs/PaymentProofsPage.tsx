import { useState } from "react";
import { useTranslation } from "react-i18next";
import { Link } from "react-router";
import type { TFunction } from "i18next";
import type { Column } from "@/components/query/DataTable";
import { QueryPage } from "@/components/query/QueryPage";
import { Button } from "@/components/ui/button";
import { StudentCell } from "@/features/charges/ChargeCards";
import { ManagementLayout } from "@/features/permissions/ManagementLayout";
import { formatCurrency, formatDate, formatDateTime } from "@/lib/format";
import { ConfirmProofDialog, RefuseProofDialog } from "./ProofDialogs";
import { ProofImage } from "./ProofImage";
import type { PaymentProof } from "./types";
import { fetchPaymentProofs } from "./usePaymentProofs";

type Decision = { proof: PaymentProof; mode: "confirm" | "refuse" };

const student = (p: PaymentProof) => ({ student: { id: p.studentId, name: p.studentName, photoUrl: p.studentPhotoUrl } });

function Actions({ p, onDecide }: { p: PaymentProof; onDecide: (d: Decision) => void }) {
  const { t } = useTranslation();
  return (
    <span className="flex flex-wrap gap-2">
      <Button className="min-h-11" aria-label={t("paymentProofs.actions.confirmFor", { student: p.studentName })} onClick={() => onDecide({ proof: p, mode: "confirm" })}>
        {t("paymentProofs.actions.confirm")}
      </Button>
      <Button variant="outline" className="min-h-11" aria-label={t("paymentProofs.actions.refuseFor", { student: p.studentName })} onClick={() => onDecide({ proof: p, mode: "refuse" })}>
        {t("paymentProofs.actions.refuse")}
      </Button>
    </span>
  );
}

const columns = (t: TFunction, onDecide: (d: Decision) => void): Column<PaymentProof>[] => [
  { key: "proof", label: t("paymentProofs.columns.proof"), render: (p) => <ProofImage proof={p} /> },
  { key: "student", label: t("paymentProofs.columns.student"), render: (p) => <StudentCell row={student(p)} /> },
  { key: "unit", label: t("paymentProofs.columns.unit"), render: (p) => p.unit },
  { key: "reference", label: t("paymentProofs.columns.reference"), render: (p) => <span className="break-words">{p.reference}</span> },
  { key: "amount", label: t("paymentProofs.columns.amount"), render: (p) => formatCurrency(p.amountCents) },
  { key: "dueOn", label: t("paymentProofs.columns.dueOn"), render: (p) => formatDate(p.dueOn) },
  { key: "sentAt", label: t("paymentProofs.columns.sentAt"), render: (p) => formatDateTime(p.sentAt) },
  { key: "actions", label: t("paymentProofs.columns.actions"), render: (p) => <Actions p={p} onDecide={onDecide} /> },
];

function Content() {
  const { t } = useTranslation();
  const [decision, setDecision] = useState<Decision>();
  const [version, setVersion] = useState(0);
  return (
    <div className="flex flex-col gap-4">
      <h1 className="text-2xl font-semibold">{t("paymentProofs.title")}</h1>
      <p className="text-muted-foreground">{t("paymentProofs.intro")}</p>
      <div>
        <Button asChild variant="outline" className="min-h-11">
          <Link to="/cobrancas">{t("paymentProofs.back")}</Link>
        </Button>
      </div>
      <QueryPage
        key={version}
        search={false}
        columns={columns(t, setDecision)}
        fetchPage={fetchPaymentProofs}
        rowKey={(p) => p.id}
        texts={{ emptyTitle: t("paymentProofs.emptyTitle"), emptyText: t("paymentProofs.emptyText") }}
        cards={(p) => (
          <div className="flex flex-col gap-2 text-sm">
            <StudentCell row={student(p)} />
            <ProofImage proof={p} />
            <p className="break-words">{t("paymentProofs.cardLine", { unit: p.unit, reference: p.reference })}</p>
            <p>{t("paymentProofs.cardDates", { amount: formatCurrency(p.amountCents), dueOn: formatDate(p.dueOn), sentAt: formatDateTime(p.sentAt) })}</p>
            <Actions p={p} onDecide={setDecision} />
          </div>
        )}
      />
      {decision?.mode === "confirm" && <ConfirmProofDialog proof={decision.proof} onClose={() => setDecision(undefined)} onDone={() => setVersion((n) => n + 1)} />}
      {decision?.mode === "refuse" && <RefuseProofDialog proof={decision.proof} onClose={() => setDecision(undefined)} onDone={() => setVersion((n) => n + 1)} />}
    </div>
  );
}

// RF-COB-05: comprovantes de Pix na chave em conferência; a barreira de dados é o servidor (403 vira "sem permissão" na própria lista).
export function PaymentProofsPage() {
  return (
    <ManagementLayout activeId="collections">
      <Content />
    </ManagementLayout>
  );
}
