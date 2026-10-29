import type { InputHTMLAttributes } from "react";
import { Field, Input } from "@/components/ui/field";

type Props = { label: string; value: string; onChange: (v: string) => void; error?: string; help?: string } & Omit<
  InputHTMLAttributes<HTMLInputElement>,
  "value" | "onChange"
>;

// Campo de texto com rótulo, ajuda e erro ligados por aria-describedby; compartilhado pelas telas de unidade.
export function Text({ label, value, onChange, error, help, ...rest }: Props) {
  return (
    <Field label={label} hint={error ? undefined : help} error={error}>
      {(c) => <Input {...rest} {...c} value={value} onChange={(e) => onChange(e.target.value)} />}
    </Field>
  );
}
