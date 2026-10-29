import { cn } from "@/lib/utils";

export type QuickFilter = { key: string; label: string; count?: number };

/** Filtros rápidos com contador; a opção ativa fica `aria-pressed`. */
export function QuickFilters({ options, value, onChange, className }: { options: QuickFilter[]; value: string; onChange: (key: string) => void; className?: string }) {
  return (
    <div className={cn("flex flex-wrap gap-2", className)}>
      {options.map((o) => (
        <button
          key={o.key}
          type="button"
          aria-pressed={o.key === value}
          aria-label={o.count === undefined ? undefined : `${o.label}, ${o.count}`}
          onClick={() => onChange(o.key)}
          className={cn(
            "relative inline-flex h-8 items-center gap-1.5 rounded-full border px-3 text-sm font-medium after:absolute after:left-0 after:top-1/2 after:h-11 after:w-full after:-translate-y-1/2 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring",
            o.key === value ? "border-primary bg-primary text-primary-foreground" : "border-input bg-card text-foreground hover:bg-surface-soft",
          )}
        >
          {o.label}
          {o.count !== undefined && <span className="tabular-nums opacity-80">{o.count}</span>}
        </button>
      ))}
    </div>
  );
}
