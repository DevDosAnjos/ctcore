import type { Ref } from "react";
import { Field, Input } from "@/components/ui/field";

/** Campo de código das duas etapas: `totp` (6 dígitos) ou `any` (TOTP ou código de reserva). */
export function CodeField({
  label,
  value,
  onChange,
  kind = "totp",
  error,
  inputRef,
}: {
  id?: string;
  label: string;
  value: string;
  onChange: (v: string) => void;
  kind?: "totp" | "any";
  error?: string | null;
  inputRef?: Ref<HTMLInputElement>;
}) {
  const totp = kind === "totp";
  return (
    <Field label={label} error={error ?? undefined} alertError>
      {(c) => (
        <Input
          {...c}
          ref={inputRef}
          type="text"
          inputMode={totp ? "numeric" : "text"}
          autoComplete="one-time-code"
          autoCapitalize="characters"
          spellCheck={false}
          maxLength={totp ? 6 : 10}
          className="tracking-widest"
          value={value}
          onChange={(e) => onChange(totp ? e.target.value.replace(/\D/g, "") : e.target.value.replace(/\s/g, ""))}
        />
      )}
    </Field>
  );
}
