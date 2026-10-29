import type { ReactNode } from "react";

// Cor e texto juntos (nunca só cor), como o selo de situação da cobrança.
const TONE = {
  alert: "border border-danger-foreground bg-danger text-danger-foreground",
  strong: "border bg-primary text-primary-foreground",
  neutral: "border bg-secondary text-secondary-foreground",
} as const;

export function PortalTag({ tone = "neutral", children }: { tone?: keyof typeof TONE; children: ReactNode }) {
  return <span className={`inline-flex rounded-md px-2 py-0.5 text-sm ${TONE[tone]}`}>{children}</span>;
}
