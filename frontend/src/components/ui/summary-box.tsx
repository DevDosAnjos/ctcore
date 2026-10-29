import { ArrowRight } from "lucide-react";
import { cn } from "@/lib/utils";

/** Resumo do que vai acontecer: lista com setas. */
export function SummaryBox({ items, className }: { items: string[]; className?: string }) {
  return (
    <ul className={cn("flex flex-col gap-2 rounded-md bg-surface-soft p-3 text-sm", className)}>
      {items.map((item) => (
        <li key={item} className="flex items-start gap-2">
          <ArrowRight aria-hidden className="mt-0.5 size-4 shrink-0 text-subtle" />
          <span>{item}</span>
        </li>
      ))}
    </ul>
  );
}
