/** Local-time date helpers. All day keys are "YYYY-MM-DD" in the device timezone. */

const pad = (n: number) => String(n).padStart(2, '0');

export function dateKey(d: Date): string {
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

export function todayKey(): string {
  return dateKey(new Date());
}

export function parseKey(key: string): Date {
  const [y, m, d] = key.split('-').map(Number);
  return new Date(y, m - 1, d);
}

export function addDays(key: string, n: number): string {
  const d = parseKey(key);
  d.setDate(d.getDate() + n);
  return dateKey(d);
}

/** Keys of the last `n` days ending with `end` (inclusive), oldest first. */
export function lastDays(n: number, end = todayKey()): string[] {
  return Array.from({ length: n }, (_, i) => addDays(end, i - n + 1));
}

/** Localised date labels; pass the current dictionary (`useT()`). */
interface DateDict {
  dates: { today: string; yesterday: string; weekdays: string[]; months: string[] };
}

export function shortLabel(key: string): string {
  const d = parseKey(key);
  return `${d.getDate()}.${pad(d.getMonth() + 1)}`;
}

export function weekdayLabel(key: string, L: DateDict): string {
  return L.dates.weekdays[parseKey(key).getDay()];
}

export function humanDate(key: string, L: DateDict): string {
  const t = todayKey();
  if (key === t) return L.dates.today;
  if (key === addDays(t, -1)) return L.dates.yesterday;
  const d = parseKey(key);
  return `${d.getDate()} ${L.dates.months[d.getMonth()]}, ${L.dates.weekdays[d.getDay()]}`;
}

export function timeLabel(ms: number): string {
  const d = new Date(ms);
  return `${pad(d.getHours())}:${pad(d.getMinutes())}`;
}
