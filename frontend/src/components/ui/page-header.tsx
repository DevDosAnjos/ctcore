import type { ReactNode } from "react";
import { cn } from "@/lib/utils";

/** Título em caixa alta só por CSS (D6): o DOM guarda o texto original. */
export function PageHeader({ title, subtitle, actions, className }: { title: string; subtitle?: string; actions?: ReactNode; className?: string }) {
  return (
    <header className={cn("flex flex-wrap items-end justify-between gap-3", className)}>
      <div className="min-w-0">
        <h1 className="text-[28px] font-semibold uppercase leading-tight sm:text-[38px]">{title}</h1>
        {subtitle && <p className="text-sm text-muted-foreground">{subtitle}</p>}
      </div>
      {actions && <div className="flex flex-wrap gap-2">{actions}</div>}
    </header>
  );
}
