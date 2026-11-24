/** Ports of Weekdays.kt and WorkoutFormat.kt, plus colour helpers. */

export const WEEKDAYS = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"] as const;

/** Bit for weekday index 0 (Monday) … 6 (Sunday). */
export const weekdayBit = (index: number) => 1 << index;

export function hasWeekday(mask: number, index: number): boolean {
  return (mask & weekdayBit(index)) !== 0;
}

export function toggleWeekday(mask: number, index: number): number {
  return mask ^ weekdayBit(index);
}

/** Weekday index (Monday = 0) of a local date. */
export function weekdayIndex(ms: number): number {
  return (new Date(ms).getDay() + 6) % 7;
}

/** "Mon, Thu", or "" when no day is set. */
export function weekdaysLabel(mask: number): string {
  return WEEKDAYS.filter((_, i) => hasWeekday(mask, i)).join(", ");
}

/** "60 kg", "62.5 kg". */
export function formatWeight(kg: number): string {
  return `${Number(kg.toFixed(2))} kg`;
}

/** "3,450 kg". */
export function formatVolume(kg: number): string {
  return `${Math.round(kg).toLocaleString("en-GB")} kg`;
}

/** Whole minutes, at least 1. */
export function durationMinutes(start: number, end: number | null): number {
  return Math.max(1, Math.floor(((end ?? start) - start) / 60_000));
}

/** Commitment colours, same palette as the phone (ARGB, as signed 32-bit ints like Kotlin Int). */
export const PALETTE: number[] = [
  0xff4f6bed, 0xff00897b, 0xff43a047, 0xffc0ca33, 0xffffb300,
  0xfff4511e, 0xffe53935, 0xffd81b60, 0xff8e24aa, 0xff6d4c41,
].map((c) => c | 0);

/** CSS colour from an ARGB int (the phone stores Kotlin Ints, which can be negative). */
export function cssColor(argb: number): string {
  return `#${((argb >>> 0) & 0xffffff).toString(16).padStart(6, "0")}`;
}
