import type { ReactNode } from "react";
import { AlertTriangle, CheckCircle2, Info, OctagonAlert } from "lucide-react";
import { cn } from "@/lib/utils";
import { TONE_CLASS } from "./status-tone";

const TONES = {
  info: { Icon: Info, role: "status" },
  ok: { Icon: CheckCircle2, role: "status" },
  warn: { Icon: AlertTriangle, role: "alert" },
  danger: { Icon: OctagonAlert, role: "alert" },
} as const;

export function Banner({ tone, children, className }: { tone: keyof typeof TONES; children: ReactNode; className?: string }) {
  const { Icon, role } = TONES[tone];
  return (
    <div role={role} data-tone={tone} className={cn("flex items-start gap-3 rounded-md px-4 py-3 text-sm", TONE_CLASS[tone], className)}>
      <Icon aria-hidden className="mt-0.5 size-4 shrink-0" />
      <div>{children}</div>
    </div>
  );
}
