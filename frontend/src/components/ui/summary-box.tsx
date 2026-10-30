import { ArrowRight } from "lucide-react";
import { cn } from "@/lib/utils";

/** Resumo do que vai acontecer: lista com setas, com rótulo opcional (ex.: "Resumo"). */
export function SummaryBox({ items, label, className }: { items: string[]; label?: string; className?: string }) {
  return (
    <div className={cn("flex flex-col gap-2 rounded-md bg-surface-soft p-3 text-sm", className)}>
      {label && <span className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">{label}</span>}
      <ul className="flex flex-col gap-2">
        {items.map((item) => (
          <li key={item} className="flex items-start gap-2">
            <ArrowRight aria-hidden className="mt-0.5 size-4 shrink-0 text-subtle" />
            <span>{item}</span>
          </li>
        ))}
      </ul>
    </div>
  );
}
