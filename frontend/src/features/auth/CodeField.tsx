import type { Ref } from "react";

const input = "min-h-11 w-full rounded-md border bg-background px-2";

/** Campo de código das duas etapas: `totp` (6 dígitos) ou `any` (TOTP ou código de reserva). */
export function CodeField({
  id,
  label,
  value,
  onChange,
  kind = "totp",
  error,
  inputRef,
}: {
  id: string;
  label: string;
  value: string;
  onChange: (v: string) => void;
  kind?: "totp" | "any";
  error?: string | null;
  inputRef?: Ref<HTMLInputElement>;
}) {
  const totp = kind === "totp";
  return (
    <div>
      <label htmlFor={id} className="block text-sm font-medium">
        {label}
      </label>
      <input
        id={id}
        ref={inputRef}
        type="text"
        inputMode={totp ? "numeric" : "text"}
        autoComplete="one-time-code"
        autoCapitalize="characters"
        spellCheck={false}
        maxLength={totp ? 6 : 10}
        className={`${input} tracking-widest`}
        value={value}
        onChange={(e) => onChange(totp ? e.target.value.replace(/\D/g, "") : e.target.value.replace(/\s/g, ""))}
        aria-invalid={!!error || undefined}
        aria-describedby={error ? `${id}-erro` : undefined}
      />
      {error && (
        <p id={`${id}-erro`} role="alert" className="text-sm text-destructive">
          {error}
        </p>
      )}
    </div>
  );
}
