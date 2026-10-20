import { useState } from "react";
import { useTranslation } from "react-i18next";
import { Modal } from "@/components/Modal";
import { Button } from "@/components/ui/button";
import { Text } from "@/features/units/Text";
import { ApiError } from "@/lib/api";
import type { LessonDetail } from "./useLesson";
import { lessonName } from "./LessonCard";
import { cancelLesson, type CancelCode } from "./useLessonActions";

const CODES: CancelCode[] = ["TEACHER_UNAVAILABLE", "HOLIDAY", "ACADEMY_EVENT", "OTHER"];

// RF-AGE-03: cancelar com motivo e canais do comunicado (o envio é da Sprint 09; o texto pronto aparece no painel). O servidor reconfere.
export function CancelLessonDialog({ lesson, onClose, onSaved }: { lesson: LessonDetail | null; onClose: () => void; onSaved: () => void }) {
  if (!lesson) return null;
  return <Body key={lesson.id} lesson={lesson} onClose={onClose} onSaved={onSaved} />;
}

function Body({ lesson, onClose, onSaved }: { lesson: LessonDetail; onClose: () => void; onSaved: () => void }) {
  const { t } = useTranslation();
  const [reason, setReason] = useState<CancelCode | "">("");
  const [text, setText] = useState("");
  const [email, setEmail] = useState(false);
  const [whatsapp, setWhatsapp] = useState(false);
  const [trial, setTrial] = useState<"REBOOK" | "CANCEL_TRIALS" | "">("");
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [busy, setBusy] = useState(false);
  const hasGroup = !!lesson.groupLink;

  async function submit() {
    const found: Record<string, string> = {};
    if (!reason) found.reason = t("agenda.cancel.reasonRequired");
    if (reason === "OTHER" && !text.trim()) found.reasonText = t("agenda.cancel.textRequired");
    if (lesson.trialClasses > 0 && !trial) found.trialDecision = t("agenda.cancel.trialRequired");
    setErrors(found);
    if (Object.keys(found).length || !reason) return;
    setBusy(true);
    try {
      await cancelLesson(lesson.id, {
        reason,
        ...(reason === "OTHER" ? { reasonText: text.trim() } : {}),
        channels: [...(email ? (["EMAIL"] as const) : []), ...(whatsapp && hasGroup ? (["WHATSAPP"] as const) : [])],
        ...(trial ? { trialDecision: trial } : {}),
      });
      onClose();
      onSaved();
    } catch (e) {
      const next: Record<string, string> = {};
      if (e instanceof ApiError && e.fields.length) for (const f of e.fields) next[f.field] = f.message;
      else next.form = t(`agenda.cancel.errors.${e instanceof ApiError && [403, 404, 409, 422].includes(e.status) ? e.status : "generic"}`);
      setErrors(next);
    } finally {
      setBusy(false);
    }
  }


  return (
    <Modal open onOpenChange={(o) => !o && !busy && onClose()} title={t("agenda.cancel.title")}>
      <form
        noValidate
        className="flex flex-col gap-3"
        aria-busy={busy}
        onSubmit={(e) => {
          e.preventDefault();
          if (!busy) void submit();
        }}
      >
        <p className="text-sm">{t("agenda.cancel.intro", { name: lessonName(lesson) })}</p>
        <fieldset>
          <legend className="text-sm font-medium">{t("agenda.cancel.reason")}</legend>
          {CODES.map((c) => (
            <label key={c} className="flex min-h-11 items-center gap-2">
              <input type="radio" name="reason" checked={reason === c} onChange={() => setReason(c)} />
              {t(`agenda.cancelReasons.${c}`)}
            </label>
          ))}
          <Err text={errors.reason} />
        </fieldset>
        {reason === "OTHER" && <Text label={t("agenda.cancel.text")} value={text} onChange={setText} error={errors.reasonText} maxLength={200} autoComplete="off" />}
        <fieldset>
          <legend className="text-sm font-medium">{t("agenda.cancel.channels")}</legend>
          <label className="flex min-h-11 items-center gap-2">
            <input type="checkbox" checked disabled readOnly />
            {t("agenda.cancel.portal")}
          </label>
          <label className="flex min-h-11 items-center gap-2">
            <input type="checkbox" checked={email} onChange={(e) => setEmail(e.target.checked)} />
            {t("agenda.cancel.email")}
          </label>
          {hasGroup && (
            <label className="flex min-h-11 items-center gap-2">
              <input type="checkbox" checked={whatsapp} onChange={(e) => setWhatsapp(e.target.checked)} />
              {t("agenda.cancel.whatsapp")}
            </label>
          )}
          <p className="text-sm text-muted-foreground">{t("agenda.cancel.channelsHelp")}</p>
          <Err text={errors.channels} />
        </fieldset>
        {lesson.trialClasses > 0 && (
          <fieldset>
            <legend className="text-sm font-medium">{t("agenda.cancel.trials", { count: lesson.trialClasses })}</legend>
            {(["REBOOK", "CANCEL_TRIALS"] as const).map((d) => (
              <label key={d} className="flex min-h-11 items-center gap-2">
                <input type="radio" name="trial" checked={trial === d} onChange={() => setTrial(d)} />
                {t(`agenda.cancel.trialDecisions.${d}`)}
              </label>
            ))}
            <Err text={errors.trialDecision} />
          </fieldset>
        )}
        <Err text={errors.form} />
        <div className="flex flex-wrap justify-end gap-2">
          <Button type="button" variant="outline" className="min-h-11" onClick={onClose} disabled={busy}>
            {t("agenda.cancel.keep")}
          </Button>
          <Button type="submit" className="min-h-11" disabled={busy}>
            {busy ? t("agenda.cancel.sending") : t("agenda.cancel.confirm")}
          </Button>
        </div>
      </form>
    </Modal>
  );
}

function Err({ text }: { text?: string }) {
  return text ? (
    <p role="alert" className="text-sm text-destructive">
      {text}
    </p>
  ) : null;
}
