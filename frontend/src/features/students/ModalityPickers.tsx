import { useId, useState, type FormEvent, type ReactNode } from "react";
import { useTranslation } from "react-i18next";
import { Modal } from "@/components/Modal";
import { ErrorState, ForbiddenState, LoadingState } from "@/components/states";
import { Button } from "@/components/ui/button";
import { ApiError } from "@/lib/api";
import { useResource } from "./useStudentRecord";

export interface Choice {
  id: string;
  name: string;
}

// Turmas e planos ativos da unidade e da modalidade; unidade nula (ainda não escolhida) não pede nada.
export const useClassChoices = (unitId: string | null, modalityId: string) => useResource<{ items: Choice[] }>(unitId ? `/classes?unitId=${unitId}&modalityId=${modalityId}&status=ACTIVE&size=100` : null);
export const usePlanChoices = (unitId: string | null, modalityId: string) => useResource<{ items: Choice[] }>(unitId ? `/plans?unitId=${unitId}&modalityId=${modalityId}&status=ACTIVE&size=100` : null);

export function PickSelect({ label, value, onChange, options, error }: { label: string; value: string; onChange: (v: string) => void; options: Choice[]; error?: string }) {
  const { t } = useTranslation();
  const id = useId();
  return (
    <div>
      <label htmlFor={id} className="block text-sm font-medium">
        {label}
      </label>
      <select
        id={id}
        className="min-h-11 w-full rounded-md border bg-background px-2"
        value={value}
        onChange={(e) => onChange(e.target.value)}
        aria-invalid={error ? true : undefined}
        aria-describedby={error ? `${id}-erro` : undefined}
      >
        <option value="">{t("students.record.enrollment.choose")}</option>
        {options.map((o) => (
          <option key={o.id} value={o.id}>
            {o.name}
          </option>
        ))}
      </select>
      {error && (
        <p id={`${id}-erro`} className="text-sm text-destructive">
          {error}
        </p>
      )}
    </div>
  );
}

// Outros horários: as demais turmas da modalidade, marcadas uma a uma.
export function OtherClasses({ options, primary, value, onChange, error }: { options: Choice[]; primary: string; value: string[]; onChange: (v: string[]) => void; error?: string }) {
  const { t } = useTranslation();
  const rest = options.filter((o) => o.id !== primary);
  if (rest.length === 0) return null;
  return (
    <fieldset className="flex flex-col gap-1">
      <legend className="text-sm font-medium">{t("students.record.enrollment.otherClasses")}</legend>
      {rest.map((o) => (
        <label key={o.id} className="flex min-h-11 items-center gap-2">
          <input type="checkbox" checked={value.includes(o.id)} onChange={(e) => onChange(e.target.checked ? [...value, o.id] : value.filter((x) => x !== o.id))} />
          {o.name}
        </label>
      ))}
      {error && <p className="text-sm text-destructive">{error}</p>}
    </fieldset>
  );
}

// Estado combinado das leituras da janela: a pior vence.
export function combine(...r: { status: string; reload: () => void }[]): { state: "loading" | "forbidden" | "error" | "ready"; retry: () => void } {
  const s = r.map((x) => x.status);
  const state = s.includes("forbidden") ? "forbidden" : s.some((x) => x === "error" || x === "notFound") ? "error" : s.includes("loading") ? "loading" : "ready";
  return { state, retry: () => r.forEach((x) => x.reload()) };
}

// Janela das três trocas: título, aviso de quando vale, opções carregadas (quatro estados) e o erro do servidor no campo ou no alto, sem fechar.
export function ModalityDialog({
  title,
  effect,
  state,
  retry,
  submitLabel,
  canSubmit,
  work,
  onClose,
  onDone,
  errorPrefix = "students.record.enrollment.errors.",
  children,
}: {
  title: string;
  effect: string;
  state: "loading" | "forbidden" | "error" | "ready";
  retry?: () => void;
  submitLabel: string;
  canSubmit: boolean;
  work: () => Promise<unknown>;
  onClose: () => void;
  onDone: () => void;
  /** Chaves pt-BR dos erros sem campo (por status e `generic`); o padrão é o das trocas de matrícula. */
  errorPrefix?: string;
  children: (errors: Record<string, string>) => ReactNode;
}) {
  const { t } = useTranslation();
  const [busy, setBusy] = useState(false);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [form, setForm] = useState<string>();

  async function submit(e: FormEvent) {
    e.preventDefault();
    if (busy || !canSubmit) return;
    setBusy(true);
    setErrors({});
    setForm(undefined);
    try {
      await work();
      onDone();
      onClose();
    } catch (err) {
      const failure = err instanceof ApiError ? err : null;
      if (failure?.fields.length) setErrors(Object.fromEntries(failure.fields.map((f) => [f.field.split(".").pop()!, f.message])));
      else setForm(t(`${errorPrefix}${failure?.status ?? "generic"}`, { defaultValue: t(`${errorPrefix}generic`) }));
    } finally {
      setBusy(false);
    }
  }

  return (
    <Modal open onOpenChange={(o) => !o && onClose()} title={title}>
      {state === "loading" && <LoadingState />}
      {state === "forbidden" && <ForbiddenState />}
      {state === "error" && retry && <ErrorState onRetry={retry} />}
      {state === "ready" && (
        <form onSubmit={submit} noValidate className="flex flex-col gap-3">
          <p className="text-sm text-muted-foreground">{effect}</p>
          {form && (
            <p role="alert" className="text-sm text-destructive">
              {form}
            </p>
          )}
          {children(errors)}
          <div className="flex flex-wrap justify-end gap-2">
            <Button type="button" variant="outline" className="min-h-11" onClick={onClose}>
              {t("common.cancel")}
            </Button>
            <Button type="submit" className="min-h-11" disabled={busy || !canSubmit}>
              {submitLabel}
            </Button>
          </div>
        </form>
      )}
    </Modal>
  );
}
