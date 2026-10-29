import { useEffect, useRef, useState, type FormEvent, type ReactNode } from "react";
import { useTranslation } from "react-i18next";
import { Link, useNavigate, useSearchParams } from "react-router";
import { CHAR_COUNTER_FIELD, CharCounter } from "@/components/CharCounter";
import { EmptyState, ErrorState, ForbiddenState, LoadingState } from "@/components/states";
import { Button } from "@/components/ui/button";
import { Input, Select } from "@/components/ui/field";
import { br, todayIso } from "@/features/agenda/agendaDates";
import { ManagementLayout } from "@/features/permissions/ManagementLayout";
import { ApiError } from "@/lib/api";
import { AnnouncementPreviewPanel, type PreviewState } from "./AnnouncementPreviewPanel";
import { AnnouncementTargetPicker, draftTarget, targetName } from "./AnnouncementTargetPicker";
import { templateFor } from "./announcementTemplates";
import { ANNOUNCEMENT_TYPES, type AnnouncementChannel, type AnnouncementDraft, type AnnouncementTargets, type AnnouncementTemplates, type AnnouncementType } from "./types";
import { createAnnouncement, fetchTargets, fetchTemplates, previewAnnouncement, uploadAnnouncementImage } from "./useAnnouncements";
import { PageHeader } from "@/components/ui/page-header";

const FIELD_ORDER = ["type", "title", "body", "file", "channels", "pinnedUntil", "sendAt"];

// Só http e https viram link (como o painel da turma).
function safeLink(link: string | null | undefined) {
  if (!link) return null;
  try {
    const u = new URL(link);
    return u.protocol === "http:" || u.protocol === "https:" ? u.href : null;
  } catch {
    return null;
  }
}

function Field({ id, label, error, help, children }: { id: string; label: string; error?: string; help?: string; children: ReactNode }) {
  return (
    <div>
      <label htmlFor={id} className="block text-sm font-medium">
        {label}
      </label>
      {children}
      {help && !error && (
        <p id={`${id}-ajuda`} className="text-sm text-muted-foreground">
          {help}
        </p>
      )}
      {error && (
        <p id={`${id}-erro`} className="text-sm text-danger-foreground">
          {error}
        </p>
      )}
    </div>
  );
}

function initialTarget(targets: AnnouncementTargets, p: URLSearchParams) {
  const classId = p.get("classId");
  if (classId && targets.classes.some((c) => c.id === classId)) return `CLASS:${classId}`;
  const unitId = p.get("unitId");
  return unitId && targets.units.some((u) => u.id === unitId) ? `UNIT:${unitId}` : "";
}

function Form({ targets, templates }: { targets: AnnouncementTargets; templates: AnnouncementTemplates }) {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const [target, setTarget] = useState(() => initialTarget(targets, params));
  const vars = (value: string) => {
    const date = params.get("date");
    const from = params.get("startsOn");
    const to = params.get("endsOn");
    return {
      turma: targetName(targets, value),
      data: date ? br(date) : undefined,
      horario: params.get("time") ?? undefined,
      periodo: from ? (to && to !== from ? `${br(from)} a ${br(to)}` : br(from)) : undefined,
      motivo: params.get("reason") ?? undefined,
    };
  };
  // O preenchimento vem da URL só na abertura; depois a pessoa muda tudo.
  const [start] = useState(() => {
    const asked = params.get("type") as AnnouncementType | null;
    const known = !!asked && ANNOUNCEMENT_TYPES.includes(asked);
    return { type: known ? asked : ("GENERAL" as AnnouncementType), ...(known ? templateFor(t, templates, asked, vars(target)) : { title: "", body: "" }) };
  });
  const [type, setType] = useState<AnnouncementType>(start.type);
  const [title, setTitle] = useState(start.title);
  const [body, setBody] = useState(start.body);
  const applied = useRef({ title: start.title, body: start.body });
  const [pendingType, setPendingType] = useState<AnnouncementType>();
  const [email, setEmail] = useState(false);
  const [whatsapp, setWhatsapp] = useState(false);
  const [pinnedUntil, setPinnedUntil] = useState("");
  const [mode, setMode] = useState<"NOW" | "SCHEDULED">("NOW");
  const [sendAt, setSendAt] = useState("");
  const [image, setImage] = useState<{ name: string; url: string }>();
  const [uploading, setUploading] = useState(false);
  const [busy, setBusy] = useState(false);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [topError, setTopError] = useState("");
  const [focusTick, setFocusTick] = useState(0);
  const topRef = useRef<HTMLParagraphElement>(null);
  const [preview, setPreview] = useState<PreviewState | null>(null);

  const channels: AnnouncementChannel[] = ["PORTAL", ...(email ? (["EMAIL"] as const) : []), ...(whatsapp ? (["WHATSAPP"] as const) : [])];
  const targetPart = draftTarget(target);
  const showWhatsapp = targetPart?.targetKind === "CLASS" || body.trim() !== "";
  const channelsKey = channels.join(",");

  useEffect(() => {
    const part = draftTarget(target);
    if (!part) {
      setPreview(null);
      return;
    }
    let alive = true;
    setPreview({ status: "loading" });
    previewAnnouncement({ ...part, channels: channelsKey.split(",") as AnnouncementChannel[] }).then(
      (data) => alive && setPreview({ status: "ready", data }),
      () => alive && setPreview({ status: "error" }),
    );
    return () => {
      alive = false;
    };
  }, [target, channelsKey]);

  useEffect(() => {
    if (focusTick === 0) return;
    const first = FIELD_ORDER.find((f) => errors[f]);
    if (first) document.getElementById(`ann-${first}`)?.focus();
    else topRef.current?.focus();
  }, [focusTick]); // eslint-disable-line react-hooks/exhaustive-deps

  function applyType(next: AnnouncementType) {
    const text = templateFor(t, templates, next, vars(target));
    setType(next);
    setTitle(text.title);
    setBody(text.body);
    applied.current = text;
    setPendingType(undefined);
  }
  function chooseType(next: AnnouncementType) {
    if (next === type) return;
    if (title !== applied.current.title || body !== applied.current.body) setPendingType(next);
    else applyType(next);
  }

  async function chooseImage(file: File | undefined) {
    if (!file) return;
    setUploading(true);
    setErrors((e) => ({ ...e, file: "" }));
    try {
      setImage(await uploadAnnouncementImage(file));
    } catch (e) {
      setErrors((x) => ({ ...x, file: t(e instanceof ApiError && e.status === 400 ? "announcements.new.fields.file" : "announcements.new.errorGeneric") }));
    } finally {
      setUploading(false);
    }
  }

  async function submit(e: FormEvent) {
    e.preventDefault();
    if (busy || !targetPart) return;
    setBusy(true);
    setErrors({});
    setTopError("");
    const draft: AnnouncementDraft = {
      ...targetPart,
      type,
      title,
      body,
      channels,
      ...(image ? { imageName: image.name } : {}),
      ...(pinnedUntil ? { pinnedUntil } : {}),
      ...(mode === "SCHEDULED" && sendAt ? { sendAt: new Date(sendAt).toISOString() } : {}),
      ...(params.get("noClassDayId") ? { noClassDayId: params.get("noClassDayId")! } : {}),
    };
    try {
      const created = await createAnnouncement(draft);
      const group = preview?.status === "ready" && whatsapp && preview.data.whatsapp?.mode === "GROUP" ? safeLink(preview.data.whatsapp.groupLink) : null;
      navigate("/comunicados", { state: { sent: { status: created.status, groupLink: group } } });
    } catch (err) {
      const fields: Record<string, string> = {};
      let top = "";
      if (err instanceof ApiError && err.status === 400) {
        for (const f of err.fields) {
          if (FIELD_ORDER.includes(f.field)) fields[f.field] = t(`announcements.new.fields.${f.field}`);
          else top = t("announcements.new.fields.other");
        }
        if (!err.fields.length) top = t("announcements.new.fields.other");
      } else if (err instanceof ApiError && err.code === "ANNOUNCEMENT_DAILY_LIMIT") top = t("announcements.new.errorLimit");
      else if (err instanceof ApiError && err.status === 403) top = t("announcements.new.errorForbidden");
      else top = t("announcements.new.errorGeneric");
      setErrors(fields);
      setTopError(top);
      setFocusTick((n) => n + 1);
    } finally {
      setBusy(false);
    }
  }

  const describe = (id: string, field: string, help = false) => (errors[field] ? `${id}-erro` : help ? `${id}-ajuda` : undefined);
  return (
    <form className="flex flex-col gap-4" onSubmit={(e) => void submit(e)} noValidate>
      <AnnouncementTargetPicker targets={targets} value={target} onChange={setTarget} />

      <Field id="ann-type" label={t("announcements.new.type")} error={errors.type}>
        <Select id="ann-type" value={type} aria-invalid={errors.type ? true : undefined} aria-describedby={describe("ann-type", "type")} onChange={(e) => chooseType(e.target.value as AnnouncementType)}>
          {ANNOUNCEMENT_TYPES.map((v) => (
            <option key={v} value={v}>
              {t(`announcements.types.${v}`)}
            </option>
          ))}
        </Select>
      </Field>
      {pendingType && (
        <div role="alert" className="flex flex-col gap-2 rounded-card border bg-card p-4">
          <p>{t("announcements.new.typeChange", { type: t(`announcements.types.${pendingType}`) })}</p>
          <div className="flex flex-wrap gap-2">
            <Button type="button" className="min-h-11" onClick={() => applyType(pendingType)}>
              {t("announcements.new.typeChangeYes")}
            </Button>
            <Button type="button" variant="outline" className="min-h-11" onClick={() => setPendingType(undefined)}>
              {t("announcements.new.typeChangeNo")}
            </Button>
          </div>
        </div>
      )}

      <Field id="ann-title" label={t("announcements.new.titleField")} error={errors.title}>
        <Input id="ann-title" value={title} maxLength={120} aria-invalid={errors.title ? true : undefined} aria-describedby={describe("ann-title", "title")} onChange={(e) => setTitle(e.target.value)} />
      </Field>
      <Field id="ann-body" label={t("announcements.new.bodyField")} error={errors.body} help={t("announcements.new.bodyHelp")}>
        <CharCounter id="ann-body" rows={6} max={2000} className={CHAR_COUNTER_FIELD} value={body} onChange={setBody} aria-invalid={errors.body ? true : undefined} aria-describedby={describe("ann-body", "body", true)} />
      </Field>

      <Field id="ann-file" label={t("announcements.new.image")} error={errors.file}>
        <input id="ann-file" type="file" accept="image/*" className="block min-h-11 w-full" aria-describedby={describe("ann-file", "file")} onChange={(e) => void chooseImage(e.target.files?.[0])} />
        {uploading && <p role="status">{t("announcements.new.imageUploading")}</p>}
        {image && (
          <div className="mt-2 flex flex-wrap items-center gap-3">
            <img src={image.url} alt={t("announcements.new.imageAlt")} className="max-h-24 rounded border" />
            <Button type="button" variant="outline" className="min-h-11" onClick={() => setImage(undefined)}>
              {t("announcements.new.imageRemove")}
            </Button>
          </div>
        )}
      </Field>

      <fieldset id="ann-channels" tabIndex={-1} className="flex flex-col gap-1" aria-describedby={describe("ann-channels", "channels")}>
        <legend className="text-sm font-medium">{t("announcements.new.channels")}</legend>
        <label className="flex min-h-11 items-center gap-2">
          <input type="checkbox" checked disabled /> {t("announcements.new.channelPortal")}
        </label>
        <label className="flex min-h-11 items-center gap-2">
          <input type="checkbox" checked={email} onChange={(e) => setEmail(e.target.checked)} /> {t("announcements.new.channelEmail")}
        </label>
        {showWhatsapp && (
          <label className="flex min-h-11 items-center gap-2">
            <input type="checkbox" checked={whatsapp} onChange={(e) => setWhatsapp(e.target.checked)} /> {t("announcements.new.channelWhatsapp")}
          </label>
        )}
        {errors.channels && (
          <p id="ann-channels-erro" className="text-sm text-danger-foreground">
            {errors.channels}
          </p>
        )}
      </fieldset>

      <Field id="ann-pinnedUntil" label={t("announcements.new.pinnedUntil")} error={errors.pinnedUntil} help={t("announcements.new.pinnedHelp")}>
        <Input id="ann-pinnedUntil" type="date" min={todayIso()} value={pinnedUntil} aria-invalid={errors.pinnedUntil ? true : undefined} aria-describedby={describe("ann-pinnedUntil", "pinnedUntil", true)} onChange={(e) => setPinnedUntil(e.target.value)} />
      </Field>

      <fieldset className="flex flex-col gap-1">
        <legend className="text-sm font-medium">{t("announcements.new.when")}</legend>
        <label className="flex min-h-11 items-center gap-2">
          <input type="radio" name="ann-mode" checked={mode === "NOW"} onChange={() => setMode("NOW")} /> {t("announcements.new.now")}
        </label>
        <label className="flex min-h-11 items-center gap-2">
          <input type="radio" name="ann-mode" checked={mode === "SCHEDULED"} onChange={() => setMode("SCHEDULED")} /> {t("announcements.new.schedule")}
        </label>
        {mode === "SCHEDULED" && (
          <Field id="ann-sendAt" label={t("announcements.new.sendAt")} error={errors.sendAt}>
            <Input id="ann-sendAt" type="datetime-local" value={sendAt} aria-invalid={errors.sendAt ? true : undefined} aria-describedby={describe("ann-sendAt", "sendAt")} onChange={(e) => setSendAt(e.target.value)} />
          </Field>
        )}
      </fieldset>

      <AnnouncementPreviewPanel state={preview} />
      {topError && (
        <p ref={topRef} tabIndex={-1} role="alert" className="text-sm text-danger-foreground">
          {topError}
        </p>
      )}
      <div>
        <Button type="submit" className="min-h-11" disabled={!targetPart || busy || uploading || (mode === "SCHEDULED" && !sendAt)}>
          {busy ? t("announcements.new.sending") : t(mode === "NOW" ? "announcements.new.send" : "announcements.new.scheduleSend")}
        </Button>
      </div>
    </form>
  );
}

function Content() {
  const { t } = useTranslation();
  const [tick, setTick] = useState(0);
  const [state, setState] = useState<{ status: "loading" | "error" | "forbidden" } | { status: "ready"; data: AnnouncementTargets; templates: AnnouncementTemplates }>({ status: "loading" });

  useEffect(() => {
    let alive = true;
    Promise.all([fetchTargets(), fetchTemplates()]).then(
      ([data, templates]) => alive && setState({ status: "ready", data, templates }),
      (e) => alive && setState({ status: e instanceof ApiError && e.status === 403 ? "forbidden" : "error" }),
    );
    return () => {
      alive = false;
    };
  }, [tick]);

  const data = state.status === "ready" ? state.data : null;
  const templates = state.status === "ready" ? state.templates : null;
  const none = !!data && !data.organization && data.units.length === 0 && data.classes.length === 0 && data.modalities.length === 0;
  return (
    <div className="flex max-w-2xl flex-col gap-4">
      <Link to="/comunicados" className="inline-flex min-h-11 items-center self-start text-sm underline">
        {t("announcements.new.back")}
      </Link>
      <PageHeader title={t("announcements.new.title")} />
      {state.status === "loading" && <LoadingState />}
      {state.status === "error" && <ErrorState onRetry={() => {
            setState({ status: "loading" });
            setTick((n) => n + 1);
          }} />}
      {state.status === "forbidden" && <ForbiddenState />}
      {none && <EmptyState title={t("announcements.new.emptyTitle")} text={t("announcements.new.emptyText")} />}
      {data && templates && !none && (
        <>
          <p className="text-muted-foreground">{t("announcements.new.intro")}</p>
          <Form targets={data} templates={templates} />
        </>
      )}
    </div>
  );
}

// RF-COM-02: a barreira de dados é o servidor (403 vira "sem permissão" na própria tela).
export function NewAnnouncementPage() {
  return (
    <ManagementLayout activeId="announcements">
      <Content />
    </ManagementLayout>
  );
}
