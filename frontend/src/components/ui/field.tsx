import * as React from "react";
import { useTranslation } from "react-i18next";
import { cn } from "@/lib/utils";

export type ControlProps = {
  id: string;
  "aria-describedby"?: string;
  "aria-invalid"?: true;
  "aria-required"?: true;
};

/** Liga rótulo, dica e erro ao controle; o controle recebe as props prontas. */
export function Field({
  id: fixedId,
  label,
  required,
  hint,
  error,
  alertError,
  className,
  children,
}: {
  /** Id fixo do controle, quando um teste ou link depende dele. */
  id?: string;
  label: string;
  required?: boolean;
  hint?: string;
  error?: string;
  /** Anuncia o erro na hora (role alert). */
  alertError?: boolean;
  className?: string;
  children: (control: ControlProps) => React.ReactNode;
}) {
  const generated = React.useId();
  const id = fixedId ?? generated;
  const describedBy = [hint && `${id}-dica`, error && `${id}-erro`].filter(Boolean).join(" ");
  return (
    <div className={cn("flex flex-col gap-1", className)}>
      <label htmlFor={id} className="text-sm font-medium">
        {label}
        {required && <span aria-hidden> *</span>}
      </label>
      {children({
        id,
        "aria-describedby": describedBy || undefined,
        "aria-invalid": error ? true : undefined,
        "aria-required": required ? true : undefined,
      })}
      {hint && (
        <p id={`${id}-dica`} className="text-xs text-muted-foreground">
          {hint}
        </p>
      )}
      {error && (
        <p id={`${id}-erro`} role={alertError ? "alert" : undefined} className="text-xs text-danger-foreground">
          {error}
        </p>
      )}
    </div>
  );
}

const CONTROL =
  "w-full rounded-md border border-input bg-card px-3 text-sm text-foreground placeholder:text-subtle focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring aria-[invalid=true]:border-danger-foreground disabled:opacity-50";

export function Input({ className, ...rest }: React.ComponentProps<"input">) {
  return <input {...rest} className={cn(CONTROL, "h-10", className)} />;
}

export function Select({ className, ...rest }: React.ComponentProps<"select">) {
  return <select {...rest} className={cn(CONTROL, "h-10", className)} />;
}

/** Com `maxLength`, mostra o contador usados/máximo. */
export function Textarea({ className, maxLength, onChange, ...rest }: React.ComponentProps<"textarea">) {
  const { t } = useTranslation();
  const [typed, setTyped] = React.useState(String(rest.value ?? rest.defaultValue ?? "").length);
  const used = rest.value === undefined ? typed : String(rest.value).length;
  return (
    <div className="flex flex-col gap-1">
      <textarea
        {...rest}
        maxLength={maxLength}
        className={cn(CONTROL, "min-h-24 py-2", className)}
        onChange={(e) => {
          setTyped(e.target.value.length);
          onChange?.(e);
        }}
      />
      {maxLength !== undefined && (
        <p className="text-right text-xs text-muted-foreground">{t("ui.counter", { used, max: maxLength })}</p>
      )}
    </div>
  );
}
