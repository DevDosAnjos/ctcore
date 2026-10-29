import { useState } from "react";
import { useTranslation } from "react-i18next";
import { Card } from "@/components/Card";
import { EmptyState, ErrorState, ForbiddenState, LoadingState } from "@/components/states";
import { Button } from "@/components/ui/button";
import { ApiError } from "@/lib/api";
import { ManagementLayout } from "@/features/permissions/ManagementLayout";
import { useTerms, type TermsKind, type TermsVersion } from "./useTerms";
import { PageHeader } from "@/components/ui/page-header";

const KINDS: TermsKind[] = ["ENROLLMENT", "IMAGE_USE"];
const when = (iso: string | null) => (iso ? new Intl.DateTimeFormat("pt-BR", { dateStyle: "short", timeStyle: "short" }).format(new Date(iso)) : "");

function History({ versions }: { versions: TermsVersion[] }) {
  const { t } = useTranslation();
  return (
    <Card heading="h2" title={t("terms.history.title")}>
      {versions.length === 0 ? (
        <EmptyState title={t("terms.history.empty")} text={t("terms.history.emptyText")} />
      ) : (
        <ul className="flex flex-col divide-y">
          {versions.map((v) => (
            <li key={v.version} className="py-2">
              <p className="break-words">
                <span className="font-medium">{t("terms.history.version", { version: v.version })}</span>
                <span className="text-muted-foreground">
                  {" · "}
                  {when(v.publishedAt)}
                  {v.publishedBy ? ` · ${v.publishedBy}` : ""}
                  {" · "}
                  {t(v.reaccept ? "terms.history.reacceptAll" : "terms.history.reacceptNew")}
                </span>
              </p>
              <details className="mt-1">
                <summary className="cursor-pointer text-sm underline">
                  {t("terms.history.read")}
                  <span className="sr-only"> {t("terms.history.version", { version: v.version })}</span>
                </summary>
                <p className="mt-1 whitespace-pre-wrap break-words text-sm">{v.body}</p>
              </details>
            </li>
          ))}
        </ul>
      )}
    </Card>
  );
}

function TermEditor({ kind }: { kind: TermsKind }) {
  const { t } = useTranslation();
  const api = useTerms(kind);
  const [text, setText] = useState<string>();
  const [reaccept, setReaccept] = useState<boolean>();
  const [message, setMessage] = useState<{ kind: "status" | "alert"; text: string }>();
  const [busy, setBusy] = useState(false);
  if (api.status === "loading") return <LoadingState />;
  if (api.status === "forbidden") return <ForbiddenState />;
  if (!api.data) return <ErrorState onRetry={api.retry} />;

  const { canManage, current, versions } = api.data;
  const body = text ?? current.body;
  const changed = body.trim() !== current.body.trim() && body.trim().length > 0;
  const publish = async () => {
    if (reaccept === undefined) return;
    setBusy(true);
    setMessage(undefined);
    try {
      await api.publish({ body, reaccept });
      setText(undefined);
      setReaccept(undefined);
      setMessage({ kind: "status", text: t("terms.published") });
    } catch (e) {
      const status = e instanceof ApiError ? e.status : 0;
      setMessage({ kind: "alert", text: t(status === 409 ? "terms.errors.conflict" : status === 400 ? "terms.errors.invalid" : "terms.errors.generic") });
      if (status === 409) api.retry();
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="flex flex-col gap-4">
      <p className="text-sm text-muted-foreground">
        {current.version === 0
          ? t("terms.current.model")
          : t("terms.current.published", { version: current.version, date: when(current.publishedAt), by: current.publishedBy ?? "" })}
      </p>
      {message && (
        <p role={message.kind === "alert" ? "alert" : "status"} className={message.kind === "alert" ? "text-sm text-destructive" : "text-sm"}>
          {message.text}
        </p>
      )}
      {canManage ? (
        <form
          className="flex flex-col gap-3"
          onSubmit={(e) => {
            e.preventDefault();
            void publish();
          }}
        >
          <label htmlFor={`terms-${kind}`} className="text-sm font-medium">
            {t("terms.editor.label")}
          </label>
          <textarea
            id={`terms-${kind}`}
            className="min-h-64 w-full rounded-md border bg-background px-2 py-1"
            value={body}
            onChange={(e) => setText(e.target.value)}
          />
          <fieldset className="flex flex-col gap-1">
            <legend className="text-sm font-medium">{t("terms.reaccept.legend")}</legend>
            <label className="flex items-center gap-2">
              <input type="radio" name={`reaccept-${kind}`} checked={reaccept === false} onChange={() => setReaccept(false)} />
              {t("terms.reaccept.newOnly")}
            </label>
            <label className="flex items-center gap-2">
              <input type="radio" name={`reaccept-${kind}`} checked={reaccept === true} onChange={() => setReaccept(true)} />
              {t("terms.reaccept.everyone")}
            </label>
          </fieldset>
          <div>
            <Button type="submit" disabled={busy || !changed || reaccept === undefined}>
              {t("terms.publish")}
            </Button>
          </div>
        </form>
      ) : (
        <Card heading="h2" title={t("terms.current.title")}>
          <p className="whitespace-pre-wrap break-words">{current.body}</p>
        </Card>
      )}
      <History versions={versions} />
    </div>
  );
}

function Content() {
  const { t } = useTranslation();
  const [kind, setKind] = useState<TermsKind>("ENROLLMENT");
  return (
    <div className="flex flex-col gap-4">
      <PageHeader title={t("terms.title")} />
      <p className="text-muted-foreground">{t("terms.intro")}</p>
      <div className="flex flex-wrap gap-2" role="group" aria-label={t("terms.tabs")}>
        {KINDS.map((k) => (
          <Button key={k} variant={k === kind ? "default" : "outline"} size="sm" aria-pressed={k === kind} onClick={() => setKind(k)}>
            {t(`terms.kinds.${k}`)}
          </Button>
        ))}
      </div>
      <TermEditor key={kind} kind={kind} />
    </div>
  );
}

// RF-MAT-05: Coordenador publica; Administrador só consulta (canManage). A barreira de dados é o servidor.
export function TermsPage() {
  return (
    <ManagementLayout activeId="terms">
      <Content />
    </ManagementLayout>
  );
}
