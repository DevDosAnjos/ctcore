import { useState } from "react";
import { useTranslation } from "react-i18next";
import { Modal } from "@/components/Modal";
import { Button } from "@/components/ui/button";
import { Field, Input } from "@/components/ui/field";
import { ApiError } from "@/lib/api";
import type { UnitSummary } from "./useUnits";

const FORMAT = /^[a-z0-9]+(-[a-z0-9]+)*$/;

// RN-ORG-15: o ajuste vale uma única vez e o endereço antigo continua valendo; o servidor confere o formato e a unicidade.
export function ShortNameDialog({
  unit,
  onClose,
  onSave,
}: {
  unit: UnitSummary | null;
  onClose: () => void;
  onSave: (id: string, shortName: string) => Promise<unknown>;
}) {
  const { t } = useTranslation();
  const [value, setValue] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string>();

  const formatBad = value !== "" && (value.length > 60 || !FORMAT.test(value));
  const shown = error ?? (formatBad ? t("units.dialog.formatInvalid") : undefined);

  function close() {
    setValue("");
    setError(undefined);
    onClose();
  }

  async function submit() {
    if (!unit || busy || value === "" || formatBad) return;
    setBusy(true);
    setError(undefined);
    try {
      await onSave(unit.id, value);
      close();
    } catch (e) {
      const status = e instanceof ApiError ? e.status : 0;
      setError(status === 409 ? t("units.dialog.conflict") : status === 400 ? t("units.dialog.formatInvalid") : t("units.dialog.error"));
    } finally {
      setBusy(false);
    }
  }

  return (
    <Modal open={unit !== null} onOpenChange={(open) => !open && close()} title={t("units.dialog.title", { name: unit?.name ?? "" })}>
      <form
        className="flex flex-col gap-3"
        noValidate
        onSubmit={(e) => {
          e.preventDefault();
          void submit();
        }}
      >
        <p>{t("units.dialog.warning")}</p>
        <p className="text-sm text-muted-foreground">{t("units.dialog.current", { value: unit?.shortName ?? "" })}</p>
        <Field label={t("units.dialog.label")} error={shown} alertError>
          {(c) => (
            <Input
              {...c}
              value={value}
              maxLength={80}
              autoComplete="off"
              onChange={(e) => {
                setValue(e.target.value);
                setError(undefined);
              }}
            />
          )}
        </Field>
        <div className="flex flex-wrap justify-end gap-2">
          <Button type="button" variant="secondary" onClick={close}>
            {t("common.cancel")}
          </Button>
          <Button type="submit" disabled={busy || value === "" || formatBad}>
            {busy ? t("units.dialog.saving") : t("units.dialog.confirm")}
          </Button>
        </div>
      </form>
    </Modal>
  );
}
