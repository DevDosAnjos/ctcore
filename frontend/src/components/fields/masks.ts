// Funções puras de máscara e validação. Não formatam para exibição fora dos campos (isso é da #9).

export const onlyDigits = (s: string) => s.replace(/\D/g, "");

export function formatCpf(raw: string): string {
  const d = onlyDigits(raw).slice(0, 11);
  return d
    .replace(/^(\d{3})(\d)/, "$1.$2")
    .replace(/^(\d{3})\.(\d{3})(\d)/, "$1.$2.$3")
    .replace(/^(\d{3})\.(\d{3})\.(\d{3})(\d)/, "$1.$2.$3-$4");
}

export function isValidCpf(raw: string): boolean {
  const d = onlyDigits(raw);
  if (d.length !== 11 || /^(\d)\1{10}$/.test(d)) return false;
  const dv = (len: number) => {
    let sum = 0;
    for (let i = 0; i < len; i++) sum += Number(d[i]) * (len + 1 - i);
    const r = (sum * 10) % 11;
    return r === 10 ? 0 : r;
  };
  return dv(9) === Number(d[9]) && dv(10) === Number(d[10]);
}

export function formatPhone(raw: string): string {
  const d = onlyDigits(raw).slice(0, 11);
  if (d.length <= 2) return d.length ? `(${d}` : "";
  const split = d.length > 10 ? 7 : 6;
  const head = `(${d.slice(0, 2)}) ${d.slice(2, split)}`;
  return d.length > split ? `${head}-${d.slice(split)}` : head;
}

// DDD de 2 dígitos (11 a 99, sem zero no segundo); 10 dígitos (fixo) ou 11 (celular, começa com 9).
export function isValidPhone(raw: string): boolean {
  const d = onlyDigits(raw);
  if (!/^[1-9][1-9]/.test(d)) return false;
  return d.length === 10 || (d.length === 11 && d[2] === "9");
}

const MAX_MONEY_DIGITS = 12;

// Dígitos digitados viram centavos inteiros; vazio vira null.
export function parseMoney(raw: string): number | null {
  const d = onlyDigits(raw).slice(0, MAX_MONEY_DIGITS);
  return d ? Number(d) : null;
}

export function formatMoney(cents: number | null): string {
  if (cents === null) return "";
  const reais = Math.floor(cents / 100).toString().replace(/\B(?=(\d{3})+(?!\d))/g, ".");
  return `R$ ${reais},${String(cents % 100).padStart(2, "0")}`;
}

export function formatDate(raw: string): string {
  const d = onlyDigits(raw).slice(0, 8);
  return d.replace(/^(\d{2})(\d)/, "$1/$2").replace(/^(\d{2})\/(\d{2})(\d)/, "$1/$2/$3");
}

// dd/mm/aaaa -> aaaa-mm-dd; null se incompleta ou inexistente (ex.: 31/02).
export function parseDate(display: string): string | null {
  const m = /^(\d{2})\/(\d{2})\/(\d{4})$/.exec(display);
  if (!m) return null;
  const [d, mo, y] = [Number(m[1]), Number(m[2]), Number(m[3])];
  const dt = new Date(Date.UTC(y, mo - 1, d));
  if (y < 1 || dt.getUTCFullYear() !== y || dt.getUTCMonth() !== mo - 1 || dt.getUTCDate() !== d) return null;
  return `${m[3]}-${m[2]}-${m[1]}`;
}
