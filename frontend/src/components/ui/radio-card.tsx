import * as React from "react";
import { cn } from "@/lib/utils";

/** Opção em cartão; vários com o mesmo `name` formam o grupo (setas trocam a escolha). */
export function RadioCard({
  label,
  description,
  className,
  ...rest
}: Omit<React.ComponentProps<"input">, "type"> & { label: string; description?: string }) {
  return (
    <label
      className={cn(
        "flex min-h-11 cursor-pointer items-start gap-3 rounded-card border border-input bg-card p-3 text-sm has-[:checked]:border-brand has-[:checked]:bg-acc has-[:focus-visible]:ring-2 has-[:focus-visible]:ring-ring",
        className,
      )}
    >
      <input {...rest} type="radio" className="mt-0.5 size-4 accent-brand" />
      <span className="flex flex-col">
        <span className="font-medium">{label}</span>
        {description && <span className="text-xs text-muted-foreground">{description}</span>}
      </span>
    </label>
  );
}
