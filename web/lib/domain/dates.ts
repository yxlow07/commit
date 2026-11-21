/**
 * Local-calendar helpers. A "day" is represented by the epoch ms of its local midnight, which
 * plays the role of Kotlin's LocalDate in the phone code. Day arithmetic goes through Date, so it
 * stays correct across daylight-saving changes.
 */

export const DAY_MS = 86_400_000;

/** Local midnight of the day containing [ms]. */
export function startOfDay(ms: number): number {
  const d = new Date(ms);
  d.setHours(0, 0, 0, 0);
  return d.getTime();
}

/** Local midnight [n] calendar days after [day]. */
export function addDays(day: number, n: number): number {
  const d = new Date(day);
  d.setDate(d.getDate() + n);
  d.setHours(0, 0, 0, 0);
  return d.getTime();
}

/** Whole calendar days from [a] to [b] (both any time; compared by local date). */
export function daysBetween(a: number, b: number): number {
  return Math.round((startOfDay(b) - startOfDay(a)) / DAY_MS);
}

/** Local midnight of [year, month (1-12), day], plus an optional time. */
export function localDate(year: number, month: number, day: number, hour = 0, minute = 0): number {
  return new Date(year, month - 1, day, hour, minute, 0, 0).getTime();
}

/** Monday of the week containing [ms] (local). */
export function startOfWeek(ms: number): number {
  const day = startOfDay(ms);
  const dow = (new Date(day).getDay() + 6) % 7; // Monday = 0
  return addDays(day, -dow);
}

/** "2026-11-21" for an input[type=date]. */
export function toDateInput(ms: number): string {
  const d = new Date(ms);
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

/** "14:05" for an input[type=time]. */
export function toTimeInput(ms: number): string {
  const d = new Date(ms);
  return `${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

/** Parses input[type=date] (+ optional time) into local epoch ms. */
export function fromInputs(date: string, time?: string): number {
  const [y, m, d] = date.split("-").map(Number);
  const [h, min] = time ? time.split(":").map(Number) : [0, 0];
  return localDate(y, m, d, h, min);
}

export function pad(n: number): string {
  return n.toString().padStart(2, "0");
}
