import { formatMoney, formatPhone, isValidPhone } from "@/components/fields/masks";

// Formatadores de exibição (RNF-23). Datas no fuso da unidade, nunca no do navegador.
export const DEFAULT_TZ = "America/Sao_Paulo";

type Input = Date | string | number;

const CALENDAR_DATE = /^(\d{4})-(\d{2})-(\d{2})$/;

// Data de calendário do contrato (`date`, AAAA-MM-DD): é o mesmo dia em qualquer fuso, sem hora.
export function isCalendarDate(value: unknown): value is string {
  return typeof value === "string" && CALENDAR_DATE.test(value);
}

function parts(value: Input, timeZone: string) {
  if (isCalendarDate(value)) {
    // new Date("AAAA-MM-DD") é meia-noite UTC e cairia no dia anterior no Brasil; aqui não há conversão de fuso.
    const [, y, mo, d] = CALENDAR_DATE.exec(value)!;
    const dt = new Date(Date.UTC(Number(y), Number(mo) - 1, Number(d)));
    if (dt.getUTCFullYear() !== Number(y) || dt.getUTCMonth() !== Number(mo) - 1 || dt.getUTCDate() !== Number(d)) return null;
    return { y, mo, d, h: "00", mi: "00" };
  }
  const d = value instanceof Date ? value : new Date(value);
  if (Number.isNaN(d.getTime())) return null;
  const p = Object.fromEntries(
    new Intl.DateTimeFormat("pt-BR", { timeZone, hourCycle: "h23", year: "numeric", month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit" })
      .formatToParts(d)
      .map((x) => [x.type, x.value]),
  );
  return { y: p.year, mo: p.month, d: p.day, h: p.hour, mi: p.minute };
}

export function formatDate(value: Input, timeZone = DEFAULT_TZ): string {
  const p = parts(value, timeZone);
  return p ? `${p.d}/${p.mo}/${p.y}` : "";
}

export function formatTime(value: Input, timeZone = DEFAULT_TZ): string {
  const p = parts(value, timeZone);
  return p ? `${p.h}h${p.mi}` : "";
}

// Data de calendário não tem hora: sai só a data.
export function formatDateTime(value: Input, timeZone = DEFAULT_TZ): string {
  const p = parts(value, timeZone);
  if (!p) return "";
  return isCalendarDate(value) ? `${p.d}/${p.mo}/${p.y}` : `${p.d}/${p.mo}/${p.y} ${p.h}h${p.mi}`;
}

export const formatCurrency = (cents: number): string => formatMoney(cents);

// Número inválido volta o texto original.
export function formatPhoneDisplay(raw: string): string {
  return isValidPhone(raw) ? formatPhone(raw) : raw;
}

// Dias de calendário entre `value` e `now` no fuso (0 = hoje, 1 = ontem, negativo = futuro); null se inválida.
export function relativeDay(value: Input, now: Input = new Date(), timeZone = DEFAULT_TZ): number | null {
  const a = parts(value, timeZone);
  const b = parts(now, timeZone);
  if (!a || !b) return null;
  const day = (p: NonNullable<typeof a>) => Date.UTC(Number(p.y), Number(p.mo) - 1, Number(p.d)) / 86_400_000;
  return day(b) - day(a);
}
