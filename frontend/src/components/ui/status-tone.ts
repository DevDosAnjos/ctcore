export type Tone = "ok" | "warn" | "danger" | "info" | "neutral" | "acc";

// Único mapa situação -> tom (D3): a mesma palavra tem o mesmo tom em todo o sistema.
const GROUPS: Record<Tone, string[]> = {
  ok: ["ACTIVE", "PAID", "PAID_LATE", "CURRENT", "CONFIRMED", "APPROVED", "SENT", "COMPLETED", "ADDED"],
  warn: ["DUE_TODAY", "DUE_SOON", "CLOSURE_REQUEST", "IN_PROGRESS", "NO_ADDRESS", "NO_PHONE", "SMS_LIMIT", "NO_EMAIL", "MINOR", "EXISTING_ACCOUNT"],
  danger: ["OVERDUE", "BLOCKED", "FAILED", "EMAIL_PROBLEM", "REFUSED"],
  info: ["IN_REVIEW", "OPEN", "SCHEDULED"],
  neutral: [
    "PENDING", "INACTIVE", "ENDED", "CANCELED", "CANCELLED", "EXPIRED", "RENEGOTIATED", "REFUNDED",
    "FORMER", "NONE", "CLOSED", "SKIPPED", "DISMISSED", "NOT_OPEN", "RECENT",
  ],
  acc: ["BIRTHDAY_TODAY"],
};

const TONE_BY_STATUS = new Map<string, Tone>(
  (Object.entries(GROUPS) as [Tone, string[]][]).flatMap(([tone, keys]) => keys.map((k) => [k, tone] as const)),
);

export function hasTone(status: string): boolean {
  return TONE_BY_STATUS.has(status);
}

export function toneOf(status: string): Tone {
  return TONE_BY_STATUS.get(status) ?? "neutral";
}

// Classes completas (o Tailwind só enxerga texto literal).
export const TONE_CLASS: Record<Tone, string> = {
  ok: "bg-ok text-ok-foreground",
  warn: "bg-warn text-warn-foreground",
  danger: "bg-danger text-danger-foreground",
  info: "bg-info text-info-foreground",
  neutral: "bg-neutral text-neutral-foreground",
  acc: "bg-acc text-acc-foreground",
};
