import { useState } from "react";
import { useTranslation } from "react-i18next";
import { Link, Navigate } from "react-router";
import { Card } from "@/components/Card";
import { EmptyState, ErrorState, ForbiddenState, LoadingState } from "@/components/states";
import { Button } from "@/components/ui/button";
import { AccountFrame } from "@/features/account/AccountFrame";
import { ApiError } from "@/lib/api";
import { usePortalTerm, type PortalTermPending } from "./usePortalTerm";

function TermBlock({ item, onAccept }: { item: PortalTermPending; onAccept: (imageUse: boolean) => Promise<void> }) {
  const { t } = useTranslation();
  const [read, setRead] = useState(false);
  const [image, setImage] = useState(false);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<string>();
  const submit = async () => {
    setBusy(true);
    setMessage(undefined);
    try {
      await onAccept(image);
    } catch (e) {
      setMessage(t(e instanceof ApiError && e.status === 409 ? "portalTerm.errors.conflict" : "portalTerm.errors.generic"));
      setBusy(false);
    }
  };
  return (
    <Card heading="h2" title={t("portalTerm.studentHeading", { name: item.name })}>
      <p className="whitespace-pre-wrap break-words text-sm">{item.term.body}</p>
      <label className="mt-4 flex min-h-11 items-center gap-2">
        <input type="checkbox" checked={read} onChange={(e) => setRead(e.target.checked)} />
        <span>{t("portalTerm.accept")}</span>
      </label>
      <h3 className="mt-4 text-base font-semibold">{t("portalTerm.imageUseTitle")}</h3>
      <p className="mt-1 whitespace-pre-wrap break-words text-sm">{item.imageUse.body}</p>
      <label className="mt-2 flex min-h-11 items-center gap-2">
        <input type="checkbox" checked={image} onChange={(e) => setImage(e.target.checked)} />
        <span>{t("portalTerm.imageUse")}</span>
      </label>
      {message && (
        <p role="alert" className="mt-2 text-sm text-destructive">
          {message}
        </p>
      )}
      <Button className="mt-4 min-h-11" disabled={!read || busy} onClick={submit}>
        {busy ? t("portalTerm.saving") : t("portalTerm.submit")}
      </Button>
    </Card>
  );
}

// RN-MAT-11, RN-POR-03: aceite do termo de matrícula no portal, com o uso de imagem em caixa separada e opcional.
export function PortalTermPage() {
  const { t } = useTranslation();
  const term = usePortalTerm();
  const [accepted, setAccepted] = useState(false);
  let body;
  if (term.status === "loading") body = <LoadingState />;
  else if (term.status === "forbidden") body = <ForbiddenState />;
  else if (!term.data) body = <ErrorState onRetry={term.retry} />;
  else if (term.data.length === 0 && accepted) return <Navigate to="/" replace />;
  else if (term.data.length === 0) {
    body = (
      <EmptyState
        title={t("portalTerm.emptyTitle")}
        text={t("portalTerm.emptyText")}
        action={
          <Link to="/" className="inline-flex min-h-11 items-center underline">
            {t("portalTerm.emptyAction")}
          </Link>
        }
      />
    );
  } else {
    body = (
      <div className="flex flex-col gap-4">
        {term.data.map((item) => (
          <TermBlock key={item.id} item={item} onAccept={async (imageUse) => {
              await term.accept(item, imageUse);
              setAccepted(true);
            }} />
        ))}
      </div>
    );
  }
  return (
    <AccountFrame>
      <div className="mx-auto max-w-3xl">
        <h1 className="text-2xl font-semibold">{t("portalTerm.title")}</h1>
        <p className="mb-4 mt-2 text-muted-foreground">{t("portalTerm.intro")}</p>
        {body}
      </div>
    </AccountFrame>
  );
}
