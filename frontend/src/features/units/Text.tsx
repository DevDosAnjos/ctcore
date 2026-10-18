import { useId, type InputHTMLAttributes } from "react";

const inputClass = "min-h-11 w-full rounded-md border bg-background px-2 disabled:opacity-60";

type Props = { label: string; value: string; onChange: (v: string) => void; error?: string; help?: string } & Omit<
  InputHTMLAttributes<HTMLInputElement>,
  "value" | "onChange"
>;

// Campo de texto com rótulo, ajuda e erro ligados por aria-describedby; compartilhado pelas telas de unidade.
export function Text({ label, value, onChange, error, help, ...rest }: Props) {
  const id = useId();
  return (
    <div>
      <label htmlFor={id} className="block text-sm font-medium">
        {label}
      </label>
      <input
        {...rest}
        id={id}
        className={inputClass}
        value={value}
        aria-invalid={error ? true : undefined}
        aria-describedby={error ? `${id}-erro` : help ? `${id}-ajuda` : undefined}
        onChange={(e) => onChange(e.target.value)}
      />
      {help && !error && (
        <p id={`${id}-ajuda`} className="text-sm text-muted-foreground">
          {help}
        </p>
      )}
      {error && (
        <p id={`${id}-erro`} className="text-sm text-destructive">
          {error}
        </p>
      )}
    </div>
  );
}
