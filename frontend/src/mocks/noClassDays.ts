// RF-AGE-05: as regras puras do servidor que o simulado repete (feriados nacionais e validação do corpo), sem estado.
export interface MockHoliday {
  code: string;
  date: string;
}

const pad = (n: number) => String(n).padStart(2, "0");
const iso = (d: Date) => `${d.getUTCFullYear()}-${pad(d.getUTCMonth() + 1)}-${pad(d.getUTCDate())}`;

// Domingo de Páscoa (Meeus/Jones/Butcher), o mesmo cálculo do servidor.
function easter(year: number): Date {
  const a = year % 19;
  const b = Math.floor(year / 100);
  const c = year % 100;
  const d = Math.floor(b / 4);
  const e = b % 4;
  const f = Math.floor((b + 8) / 25);
  const g = Math.floor((b - f + 1) / 3);
  const h = (19 * a + b - d - g + 15) % 30;
  const i = Math.floor(c / 4);
  const k = c % 4;
  const l = (32 + 2 * e + 2 * i - h - k) % 7;
  const m = Math.floor((a + 11 * h + 22 * l) / 451);
  const month = Math.floor((h + l - 7 * m + 114) / 31);
  const day = ((h + l - 7 * m + 114) % 31) + 1;
  return new Date(Date.UTC(year, month - 1, day));
}

const FIXED: [string, number, number][] = [
  ["NEW_YEAR", 1, 1],
  ["TIRADENTES", 4, 21],
  ["LABOUR_DAY", 5, 1],
  ["INDEPENDENCE", 9, 7],
  ["APARECIDA", 10, 12],
  ["ALL_SOULS", 11, 2],
  ["REPUBLIC", 11, 15],
  ["BLACK_AWARENESS", 11, 20],
  ["CHRISTMAS", 12, 25],
];

// Feriados nacionais de hoje até 12 meses depois (exclusive), por data.
export function nextHolidays(today: string): MockHoliday[] {
  const year = Number(today.slice(0, 4));
  const end = `${year + 1}${today.slice(4)}`;
  const all: MockHoliday[] = [];
  for (const y of [year, year + 1]) {
    for (const [code, m, d] of FIXED) all.push({ code, date: `${y}-${pad(m)}-${pad(d)}` });
    const good = easter(y);
    good.setUTCDate(good.getUTCDate() - 2);
    all.push({ code: "GOOD_FRIDAY", date: iso(good) });
  }
  return all.filter((h) => h.date >= today && h.date < end).sort((a, b) => a.date.localeCompare(b.date));
}

export interface DayBody {
  unitIds?: string[];
  startsOn?: string;
  endsOn?: string;
  reason?: string;
  holiday?: boolean;
  classIds?: string[];
  notice?: { mode?: string; sendAt?: string };
}

const isDate = (v: unknown): v is string => typeof v === "string" && /^\d{4}-\d{2}-\d{2}$/.test(v) && !Number.isNaN(Date.parse(`${v}T12:00:00Z`));
const days = (a: string, b: string) => (Date.parse(`${b}T12:00:00Z`) - Date.parse(`${a}T12:00:00Z`)) / 86400000;

// Mesmas validações de formato do servidor (400 com o campo); `full` liga motivo e aviso (a prévia não os exige).
export function validateDay(b: DayBody, today: string, full: boolean): { field: string; message: string } | null {
  if (!isDate(b.startsOn)) return { field: "startsOn", message: "data inválida, use yyyy-MM-dd" };
  if (!isDate(b.endsOn)) return { field: "endsOn", message: "data inválida, use yyyy-MM-dd" };
  if (b.endsOn < b.startsOn) return { field: "endsOn", message: "não pode ser anterior a startsOn" };
  if (days(b.startsOn, b.endsOn) + 1 > 90) return { field: "endsOn", message: "período de no máximo 90 dias" };
  const reason = (b.reason ?? "").trim();
  if (full && (reason.length < 1 || reason.length > 120)) return { field: "reason", message: "de 1 a 120 caracteres" };
  if (b.startsOn < today) return { field: "startsOn", message: "o início não pode ficar no passado" };
  if (full && b.notice) {
    if (!["NONE", "NOW", "SCHEDULED"].includes(b.notice.mode ?? "")) return { field: "notice", message: "modo inválido" };
    if (b.notice.mode === "SCHEDULED") {
      const at = Date.parse(b.notice.sendAt ?? "");
      if (Number.isNaN(at)) return { field: "notice", message: "sendAt obrigatório para aviso agendado" };
      if (at <= Date.now() || at >= new Date(`${b.startsOn}T00:00:00`).getTime()) return { field: "notice", message: "sendAt precisa ser futuro e antes do início do dia" };
    }
  }
  return null;
}
