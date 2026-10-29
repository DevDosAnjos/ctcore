import { useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { Card } from "@/components/Card";
import { ConfirmDialog } from "@/components/ConfirmDialog";
import { EmptyState, ErrorState, ForbiddenState, LoadingState } from "@/components/states";
import { Banner } from "@/components/ui/banner";
import { Button } from "@/components/ui/button";
import { Input, Textarea } from "@/components/ui/field";
import { ApiError } from "@/lib/api";
import { ManagementLayout } from "@/features/permissions/ManagementLayout";
import { useTexts, type TextGroup, type TextModel } from "./useTexts";
import { PageHeader } from "@/components/ui/page-header";

const GROUPS: TextGroup[] = ["REMINDERS", "WELCOME", "ANNOUNCEMENTS"];
const when = (iso: string | null) => (iso ? new Intl.DateTimeFormat("pt-BR", { dateStyle: "short", timeStyle: "short" }).format(new Date(iso)) : "");

type Field = "subject" | "body";
type Api = ReturnType<typeof useTexts>;

function Editor({ model, canManage, api }: { model: TextModel; canManage: boolean; api: Api }) {
  const { t } = useTranslation();
  const inUse = model.custom ?? model.platform;
  const [subject, setSubject] = useState(inUse.subject);
  const [body, setBody] = useState(inUse.body);
  const [errors, setErrors] = useState<Partial<Record<Field | "top", string>>>({});
  const [message, setMessage] = useState("");
  const [busy, setBusy] = useState(false);
  const [confirming, setConfirming] = useState(false);
  const last = useRef<Field>("body");
  const subjectRef = useRef<HTMLInputElement>(null);
  const bodyRef = useRef<HTMLTextAreaElement>(null);
  const fieldEl = (f: Field) => (f === "subject" ? subjectRef : bodyRef).current;
  const name = t(`texts.models.${model.key}`);

  const changed = (subject.trim() !== inUse.subject.trim() || body.trim() !== inUse.body.trim()) && subject.trim() !== "" && body.trim() !== "";
  const sample = (f: string) => t(`texts.sample.${f}`);
  const fill = (text: string) => text.replace(/\{([a-z_]+)\}/g, (all, f: string) => (model.fields.includes(f) ? sample(f) : all));

  function insert(field: string) {
    const target = last.current;
    const el = fieldEl(target);
    const text = `{${field}}`;
    const set = target === "subject" ? setSubject : setBody;
    const value = target === "subject" ? subject : body;
    const start = el?.selectionStart ?? value.length;
    const end = el?.selectionEnd ?? value.length;
    set(value.slice(0, start) + text + value.slice(end));
    requestAnimationFrame(() => {
      el?.focus();
      el?.setSelectionRange(start + text.length, start + text.length);
    });
  }

  async function save() {
    setBusy(true);
    setErrors({});
    setMessage("");
    try {
      await api.save(model.key, { subject: subject.trim(), body: body.trim() });
      setMessage(t("texts.saved"));
    } catch (e) {
      const next: Partial<Record<Field | "top", string>> = {};
      if (e instanceof ApiError && e.status === 400) {
        for (const f of e.fields) {
          if (f.field === "subject" || f.field === "body") next[f.field] = t(f.message.startsWith("igual") ? "texts.errors.same" : `texts.errors.${f.field}`);
        }
      }
      if (!next.subject && !next.body) next.top = t("texts.errors.generic");
      setErrors(next);
      const first: Field | undefined = next.subject ? "subject" : next.body ? "body" : undefined;
      if (first) fieldEl(first)?.focus();
    } finally {
      setBusy(false);
    }
  }

  async function reset() {
    try {
      await api.reset(model.key);
    } catch {
      setErrors({ top: t("texts.errors.resetGeneric") });
      throw new Error("reset");
    }
    setSubject(model.platform.subject);
    setBody(model.platform.body);
    setErrors({});
    setMessage(t("texts.resetDone"));
  }

  return (
    <Card heading="h2" title={t("texts.editor.title", { name })}>
      <div className="flex flex-col gap-3">
        <p className="text-sm text-muted-foreground">
          {model.custom ? t("texts.editor.updated", { date: when(model.updatedAt), by: model.updatedBy ?? "" }) : t("texts.origin.platform")}
        </p>
        {model.key === "WELCOME" && <p className="text-sm text-muted-foreground">{t("texts.welcomeNote")}</p>}
        {!canManage && <p className="text-sm text-muted-foreground">{t("texts.editor.readOnly")}</p>}
        {message && <p role="status" className="text-sm">{message}</p>}
        {errors.top && <Banner tone="danger">{errors.top}</Banner>}
        <form
          className="flex flex-col gap-3"
          onSubmit={(e) => {
            e.preventDefault();
            if (canManage && changed && !busy) void save();
          }}
        >
          <div>
            <label htmlFor="texts-subject" className="text-sm font-medium">
              {t("texts.editor.subject")}
            </label>
            <Input
              id="texts-subject"
              ref={subjectRef}
              value={subject}
              disabled={!canManage}
              aria-invalid={!!errors.subject}
              aria-describedby={errors.subject ? "texts-subject-erro" : undefined}
              onFocus={() => (last.current = "subject")}
              onChange={(e) => setSubject(e.target.value)}
            />
            {errors.subject && (
              <p id="texts-subject-erro" className="text-sm text-danger-foreground">
                {errors.subject}
              </p>
            )}
          </div>
          <div>
            <label htmlFor="texts-body" className="text-sm font-medium">
              {t("texts.editor.body")}
            </label>
            <Textarea
              id="texts-body"
              ref={bodyRef}
              className="min-h-40"
              value={body}
              disabled={!canManage}
              aria-invalid={!!errors.body}
              aria-describedby={errors.body ? "texts-body-erro" : undefined}
              onFocus={() => (last.current = "body")}
              onChange={(e) => setBody(e.target.value)}
            />
            {errors.body && (
              <p id="texts-body-erro" className="text-sm text-danger-foreground">
                {errors.body}
              </p>
            )}
          </div>
          <fieldset className="flex flex-col gap-2" disabled={!canManage}>
            <legend className="text-sm font-medium">{t("texts.editor.fields")}</legend>
            <p className="text-sm text-muted-foreground">{t("texts.editor.fieldsHelp")}</p>
            <div className="flex flex-wrap gap-2">
              {model.fields.map((f) => (
                <Button key={f} type="button" variant="outline" size="sm" className="min-h-11" aria-label={t("texts.editor.insert", { field: t(`texts.fieldLabels.${f}`) })} onClick={() => insert(f)}>
                  {`{${f}}`}
                </Button>
              ))}
            </div>
            {model.fields.includes("link_pagamento") && <p className="text-sm text-muted-foreground">{t("texts.editor.linkNote")}</p>}
          </fieldset>
          {canManage && (
            <div className="flex flex-wrap gap-2">
              <Button type="submit" className="min-h-11" disabled={busy || !changed}>
                {t("texts.save")}
              </Button>
              {model.custom && (
                <Button type="button" variant="outline" className="min-h-11" disabled={busy} onClick={() => setConfirming(true)}>
                  {t("texts.reset")}
                </Button>
              )}
            </div>
          )}
        </form>
        <section aria-label={t("texts.preview.title")} className="flex flex-col gap-2">
          <h3 className="text-sm font-medium">{t("texts.preview.title")}</h3>
          <p className="break-words text-sm text-muted-foreground">{t("texts.preview.subject", { subject: fill(subject) })}</p>
          <div className="mx-auto w-full max-w-xl rounded-card border bg-card p-4">
            <p className="whitespace-pre-wrap break-words text-lg">{fill(body)}</p>
            <p className="mt-4 text-sm text-muted-foreground">{t("texts.preview.footer")}</p>
          </div>
        </section>
      </div>
      <ConfirmDialog
        open={confirming}
        onOpenChange={setConfirming}
        title={t("texts.resetConfirm.title")}
        summary={t("texts.resetConfirm.summary", { name })}
        confirmLabel={t("texts.resetConfirm.confirm")}
        onConfirm={reset}
      />
    </Card>
  );
}

function Content() {
  const { t } = useTranslation();
  const api = useTexts();
  const [key, setKey] = useState<string>();
  if (api.status === "loading") return <LoadingState />;
  if (api.status === "forbidden") return <ForbiddenState />;
  if (!api.data) return <ErrorState onRetry={api.retry} />;

  const { canManage, models } = api.data;
  const selected = models.find((m) => m.key === key);
  return (
    <div className="flex flex-col gap-4">
      <PageHeader title={t("texts.title")} />
      <p className="text-muted-foreground">{t("texts.intro")}</p>
      {models.length === 0 && <EmptyState title={t("texts.emptyTitle")} text={t("texts.emptyText")} />}
      {GROUPS.map((g) => {
        const items = models.filter((m) => m.group === g);
        if (items.length === 0) return null;
        return (
          <Card key={g} heading="h2" title={t(`texts.groups.${g}`)}>
            <ul className="flex flex-col divide-y">
              {items.map((m) => (
                <li key={m.key} className="flex flex-wrap items-center justify-between gap-2 py-2">
                  <span className="break-words">
                    <span className="font-medium">{t(`texts.models.${m.key}`)}</span>
                    <span className="text-muted-foreground">{` · ${t(m.custom ? "texts.origin.custom" : "texts.origin.platform")}`}</span>
                  </span>
                  <Button variant={m.key === key ? "default" : "outline"} size="sm" className="min-h-11" aria-pressed={m.key === key} aria-label={`${t(canManage ? "texts.edit" : "texts.view")} ${t(`texts.models.${m.key}`)}`} onClick={() => setKey(m.key)}>
                    {t(canManage ? "texts.edit" : "texts.view")}
                  </Button>
                </li>
              ))}
            </ul>
          </Card>
        );
      })}
      {selected && <Editor key={selected.key} model={selected} canManage={canManage} api={api} />}
    </div>
  );
}

// RF-COM-03: Coordenador edita; Administrador só consulta (canManage). A barreira de dados é o servidor.
export function TextsPage() {
  return (
    <ManagementLayout activeId="texts">
      <Content />
    </ManagementLayout>
  );
}
