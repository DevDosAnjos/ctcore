import { useCallback, useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { useParams } from "react-router";
import { EmptyState, ErrorState, LoadingState } from "@/components/states";
import { Button } from "@/components/ui/button";
import { ApiError, api } from "@/lib/api";
import { formatCurrency, formatDateTime } from "@/lib/format";
import { ThemeToggle } from "@/theme/ThemeToggle";
import { receiptPdfUrl } from "./usePayment";

interface PublicReceipt {
  unit: string;
  number: number;
  reference: string;
  amountCents: number;
  paidAt: string;
  payerInitials: string;
}
type Load = { status: "loading" } | { status: "error" } | { status: "notFound" } | { status: "ready"; data: PublicReceipt };

// RN-COB-16: conferência pública do recibo, sem sessão. Só unidade, número, referência, valor, data e iniciais; o PDF completo vem pelo mesmo código.
export function PublicReceiptPage() {
  const { t } = useTranslation();
  const { codigo: code = "" } = useParams();
  const [load, setLoad] = useState<Load>({ status: "loading" });
  const [attempt, setAttempt] = useState(0);
  const retry = useCallback(() => {
    setLoad({ status: "loading" });
    setAttempt((n) => n + 1);
  }, []);

  useEffect(() => {
    let alive = true;
    api<PublicReceipt>(`/public/receipts/${encodeURIComponent(code)}`)
      .then((data) => alive && setLoad({ status: "ready", data }))
      .catch((e) => alive && setLoad({ status: e instanceof ApiError && e.status === 404 ? "notFound" : "error" }));
    return () => {
      alive = false;
    };
  }, [code, attempt]);

  function body() {
    if (load.status === "loading") return <LoadingState />;
    if (load.status === "error") return <ErrorState onRetry={retry} />;
    if (load.status === "notFound") return <EmptyState title={t("publicReceipt.notFoundTitle")} text={t("publicReceipt.notFoundText")} />;
    const r = load.data;
    return (
      <div className="flex flex-col gap-4">
        <h1 className="text-xl font-semibold">{t("publicReceipt.title", { number: r.number })}</h1>
        <dl className="grid grid-cols-2 gap-x-3 gap-y-1 text-sm">
          <dt className="text-muted-foreground">{t("publicReceipt.unit")}</dt>
          <dd className="break-words">{r.unit}</dd>
          <dt className="text-muted-foreground">{t("publicReceipt.reference")}</dt>
          <dd className="break-words">{r.reference}</dd>
          <dt className="text-muted-foreground">{t("publicReceipt.amount")}</dt>
          <dd>{formatCurrency(r.amountCents)}</dd>
          <dt className="text-muted-foreground">{t("publicReceipt.paidAt")}</dt>
          <dd>{formatDateTime(r.paidAt)}</dd>
          <dt className="text-muted-foreground">{t("publicReceipt.payer")}</dt>
          <dd>{r.payerInitials}</dd>
        </dl>
        <Button asChild className="min-h-11 self-start">
          <a href={receiptPdfUrl(code)}>{t("publicReceipt.download")}</a>
        </Button>
      </div>
    );
  }

  return (
    <div className="min-h-screen">
      <header className="flex items-center justify-between gap-2 border-b px-4 py-2">
        <span className="text-lg font-semibold">{t("app.name")}</span>
        <ThemeToggle />
      </header>
      <main className="mx-auto w-full max-w-md px-4 py-6">
        <div className="rounded-lg border bg-card p-4 text-card-foreground">{body()}</div>
      </main>
    </div>
  );
}
