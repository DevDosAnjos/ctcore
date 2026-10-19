// Datas da agenda como texto yyyy-MM-dd, sem fuso: a conta é feita ao meio-dia local para o horário de verão não mudar o dia.
const at = (iso: string) => {
  const [y, m, d] = iso.split("-").map(Number);
  return new Date(y, m - 1, d, 12);
};
export const toIso = (d: Date) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
export const todayIso = () => toIso(new Date());
export const addDays = (iso: string, n: number) => {
  const d = at(iso);
  d.setDate(d.getDate() + n);
  return toIso(d);
};
/** 1 (segunda) a 7 (domingo), como o servidor. */
export const weekday = (iso: string) => ((at(iso).getDay() + 6) % 7) + 1;
export const mondayOf = (iso: string) => addDays(iso, 1 - weekday(iso));
export const weekDays = (iso: string) => Array.from({ length: 7 }, (_, i) => addDays(mondayOf(iso), i));
export const br = (iso: string) => iso.split("-").reverse().join("/");
export const brShort = (iso: string) => br(iso).slice(0, 5);
