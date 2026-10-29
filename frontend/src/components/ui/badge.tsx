import type { ReactNode } from "react";
import { cn } from "@/lib/utils";
import { TONE_CLASS, toneOf, type Tone } from "./status-tone";

export function Badge({ tone, children, className }: { tone: Tone; children: ReactNode; className?: string }) {
  return (
    <span
      data-tone={tone}
      className={cn("inline-flex h-[22px] items-center gap-1 whitespace-nowrap rounded-full px-2.5 text-xs font-semibold", TONE_CLASS[tone], className)}
    >
      {children}
    </span>
  );
}

/** Recebe a situação e o rótulo já traduzido; o tom sai do mapa. */
export function StatusBadge({ status, label, className }: { status: string; label: string; className?: string }) {
  return (
    <Badge tone={toneOf(status)} className={className}>
      {label}
    </Badge>
  );
}
