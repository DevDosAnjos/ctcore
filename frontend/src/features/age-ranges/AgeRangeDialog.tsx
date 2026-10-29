import { useState } from "react";
import { useTranslation } from "react-i18next";
import { Modal } from "@/components/Modal";
import { Button } from "@/components/ui/button";
import { ApiError } from "@/lib/api";
import type { AgeRange, AgeRangeInput } from "./useAgeRanges";
import { Input } from "@/components/ui/field";

const MIN_NAME = 2;
const MAX_NAME = 40;
const MAX_AGE = 120;

type Field = "name" | "minAge" | "maxAge";

const inputClass = "min-h-11";

function FieldInput({ id, label, value, error, numeric, onChange }: { id: Field; label: string; value: string; error?: string; numeric?: boolean; onChange: (v: string) => void }) {
  return (
    <div>
      <label htmlFor={`age-range-${id}`} className="block text-sm font-medium">
        {label}
      </label>
      <Input
        id={`age-range-${id}`}
        className={inputClass}
        value={value}
        autoComplete="off"
        inputMode={numeric ? "numeric" : undefined}
        aria-invalid={error ? true : undefined}
        aria-describedby={error ? `age-range-${id}-error` : undefined}
        onChange={(e) => onChange(e.target.value)}
      />
      {error && (
        <p id={`age-range-${id}-error`} role="alert" className="mt-1 text-sm text-danger-foreground">
          {error}
        </p>
      )}
    </div>
  );
}

// RN-MOD-07: cria ou edita uma faixa. Valida no campo antes de enviar; 409 (nome repetido) e 400 do servidor aparecem no campo certo.
export function AgeRangeDialog({
  range,
  open,
  onClose,
  onSave,
}: {
  range: AgeRange | null;
  open: boolean;
  onClose: () => void;
  onSave: (input: AgeRangeInput) => Promise<unknown>;
}) {
  const { t } = useTranslation();
  // A janela é montada de novo a cada abertura (key no chamador): o estado inicial vem da faixa.
  const [name, setName] = useState(range?.name ?? "");
  const [min, setMin] = useState(range ? String(range.minAge) : "");
  const [max, setMax] = useState(range?.maxAge != null ? String(range.maxAge) : "");
  const [shown, setShown] = useState(false);
  const [busy, setBusy] = useState(false);
  const [server, setServer] = useState<{ field: Field; message: string }>();

  const trimmed = name.trim();
  const whole = (s: string) => /^\d+$/.test(s.trim());
  const minNum = Number(min);
  const errors: Partial<Record<Field, string>> = {};
  if (trimmed.length < MIN_NAME || trimmed.length > MAX_NAME) errors.name = t("ageRanges.dialog.nameInvalid");
  if (!whole(min) || minNum > MAX_AGE) errors.minAge = t("ageRanges.dialog.minInvalid");
  if (max.trim() !== "" && (!whole(max) || Number(max) > MAX_AGE || (!errors.minAge && Number(max) < minNum))) {
    errors.maxAge = t("ageRanges.dialog.maxInvalid");
  }
  const error = (f: Field) => (server?.field === f ? server.message : shown ? errors[f] : undefined);

  const change = (set: (v: string) => void, v: string) => {
    set(v);
    setServer(undefined);
  };

  async function submit() {
    if (busy) return;
    setShown(true);
    if (Object.keys(errors).length > 0) return;
    setBusy(true);
    setServer(undefined);
    try {
      await onSave({ name: trimmed, minAge: minNum, maxAge: max.trim() === "" ? null : Number(max) });
      onClose();
    } catch (e) {
      if (e instanceof ApiError && e.status === 409) {
        setServer({ field: "name", message: t("ageRanges.dialog.taken") });
      } else {
        const bad = e instanceof ApiError && e.status === 400 ? e.fields.find((f) => ["name", "minAge", "maxAge"].includes(f.field)) : undefined;
        setServer({ field: (bad?.field as Field | undefined) ?? "name", message: t("ageRanges.dialog.error") });
      }
    } finally {
      setBusy(false);
    }
  }

  return (
    <Modal open={open} onOpenChange={(o) => !o && onClose()} title={range ? t("ageRanges.dialog.editTitle") : t("ageRanges.dialog.createTitle")}>
      <form
        className="flex flex-col gap-3"
        noValidate
        onSubmit={(e) => {
          e.preventDefault();
          void submit();
        }}
      >
        <FieldInput id="name" label={t("ageRanges.dialog.name")} value={name} error={error("name")} onChange={(v) => change(setName, v)} />
        <FieldInput id="minAge" label={t("ageRanges.dialog.minAge")} value={min} error={error("minAge")} numeric onChange={(v) => change(setMin, v)} />
        <FieldInput id="maxAge" label={t("ageRanges.dialog.maxAge")} value={max} error={error("maxAge")} numeric onChange={(v) => change(setMax, v)} />
        <p className="text-sm text-muted-foreground">{t("ageRanges.dialog.hint")}</p>
        <div className="flex flex-wrap justify-end gap-2">
          <Button type="button" variant="outline" onClick={onClose}>
            {t("common.cancel")}
          </Button>
          <Button type="submit" disabled={busy}>
            {busy ? t("ageRanges.dialog.saving") : t("ageRanges.dialog.save")}
          </Button>
        </div>
      </form>
    </Modal>
  );
}
