import type { ReactNode } from "react";
import { cn } from "@/lib/utils";
import type { Tone } from "./status-tone";

const NOTE: Record<Tone, string> = {
  ok: "text-ok-foreground",
  warn: "text-warn-foreground",
  danger: "text-danger-foreground",
  info: "text-info-foreground",
  neutral: "text-neutral-foreground",
  acc: "text-acc-foreground",
};

/** Número de destaque: o tom da nota é reforço, o significado vem do texto. */
export function Stat({ label, value, note, tone = "neutral", className }: { label: string; value: ReactNode; note?: ReactNode; tone?: Tone; className?: string }) {
  return (
    <div className={cn("rounded-card border bg-card p-4", className)}>
      <p className="text-sm text-muted-foreground">{label}</p>
      <p data-stat-value className="font-heading text-[34px] font-semibold leading-tight tracking-[0.02em]">
        {value}
      </p>
      {note && (
        <p data-tone={tone} className={cn("text-xs font-medium", NOTE[tone])}>
          {note}
        </p>
      )}
    </div>
  );
}
