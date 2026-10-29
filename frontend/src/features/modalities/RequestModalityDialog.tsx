import { useState } from "react";
import { useTranslation } from "react-i18next";
import { Modal } from "@/components/Modal";
import { Button } from "@/components/ui/button";
import { ApiError } from "@/lib/api";
import { Input, Textarea } from "@/components/ui/field";

const MIN_NAME = 2;
const MAX_NAME = 60;
const MAX_NOTE = 300;

// RN-MOD-04: pedido de modalidade fora do catálogo. Valida o tamanho no campo; 409 mostra a mensagem do servidor no campo do nome.
export function RequestModalityDialog({
  open,
  onClose,
  onSend,
}: {
  open: boolean;
  onClose: () => void;
  onSend: (name: string, note: string) => Promise<unknown>;
}) {
  const { t } = useTranslation();
  const [name, setName] = useState("");
  const [note, setNote] = useState("");
  const [shown, setShown] = useState(false);
  const [busy, setBusy] = useState(false);
  const [serverError, setServerError] = useState<{
    field: "name" | "note";
    message: string;
  }>();

  const trimmed = name.trim();
  const nameBad = trimmed.length < MIN_NAME || trimmed.length > MAX_NAME;
  const noteBad = note.trim().length > MAX_NOTE;
  const nameError =
    serverError?.field === "name"
      ? serverError.message
      : shown && nameBad
        ? t("modalities.dialog.nameInvalid")
        : undefined;
  const noteError =
    shown && noteBad
      ? t("modalities.dialog.noteInvalid")
      : serverError?.field === "note"
        ? serverError.message
        : undefined;

  function close() {
    setName("");
    setNote("");
    setShown(false);
    setServerError(undefined);
    onClose();
  }

  async function submit() {
    if (busy) return;
    setShown(true);
    if (nameBad || noteBad) return;
    setBusy(true);
    setServerError(undefined);
    try {
      await onSend(trimmed, note.trim());
      close();
    } catch (e) {
      const status = e instanceof ApiError ? e.status : 0;
      const message =
        status === 409
          ? t(`modalities.dialog.conflict.${(e as ApiError).code}`, {
              defaultValue: t("modalities.dialog.error"),
            })
          : t("modalities.dialog.error");
      setServerError({ field: "name", message });
    } finally {
      setBusy(false);
    }
  }

  return (
    <Modal
      open={open}
      onOpenChange={(o) => !o && close()}
      title={t("modalities.dialog.title")}
    >
      <form
        className="flex flex-col gap-3"
        noValidate
        onSubmit={(e) => {
          e.preventDefault();
          void submit();
        }}
      >
        <p>{t("modalities.dialog.intro")}</p>
        <div>
          <label htmlFor="modality-name" className="block text-sm font-medium">
            {t("modalities.dialog.name")}
          </label>
          <Input
            id="modality-name"
            className="min-h-11"
            value={name}
            autoComplete="off"
            aria-invalid={nameError ? true : undefined}
            aria-describedby={nameError ? "modality-name-error" : undefined}
            onChange={(e) => {
              setName(e.target.value);
              setServerError(undefined);
            }}
          />
          {nameError && (
            <p
              id="modality-name-error"
              role="alert"
              className="mt-1 text-sm text-danger-foreground"
            >
              {nameError}
            </p>
          )}
        </div>
        <div>
          <label htmlFor="modality-note" className="block text-sm font-medium">
            {t("modalities.dialog.note")}
          </label>
          <Textarea
            id="modality-note"
            className="min-h-20"
            value={note}
            aria-invalid={noteError ? true : undefined}
            aria-describedby={noteError ? "modality-note-error" : undefined}
            onChange={(e) => setNote(e.target.value)}
          />
          {noteError && (
            <p
              id="modality-note-error"
              role="alert"
              className="mt-1 text-sm text-danger-foreground"
            >
              {noteError}
            </p>
          )}
        </div>
        <div className="flex flex-wrap justify-end gap-2">
          <Button type="button" variant="outline" onClick={close}>
            {t("common.cancel")}
          </Button>
          <Button type="submit" disabled={busy}>
            {busy
              ? t("modalities.dialog.sending")
              : t("modalities.dialog.send")}
          </Button>
        </div>
      </form>
    </Modal>
  );
}
