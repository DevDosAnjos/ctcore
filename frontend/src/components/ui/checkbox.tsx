import * as React from "react";
import { cn } from "@/lib/utils";

/** Caixa nativa com rótulo; sem rótulo visível, passe `aria-label`. */
export function Checkbox({ label, className, ...rest }: Omit<React.ComponentProps<"input">, "type"> & { label?: string }) {
  const box = <input {...rest} type="checkbox" className={cn("size-4 accent-brand", !label && className)} />;
  if (!label) return box;
  return (
    <label className={cn("inline-flex min-h-11 items-center gap-2 text-sm", className)}>
      {box}
      {label}
    </label>
  );
}
