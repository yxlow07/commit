import { addDays, daysBetween, pad, startOfDay, startOfWeek } from "./dates";

/**
 * Port of the phone's ui/format/DueLabels.kt: the due label rules (App Design Spec), one rule set
 * everywhere a todo appears. Late / 18:00 / Today / Mon / 28 Nov / empty.
 */

const weekdayShort = new Intl.DateTimeFormat("en-GB", { weekday: "short" });
const dayMonth = new Intl.DateTimeFormat("en-GB", { day: "numeric", month: "short" });
const weekdayDayMonth = new Intl.DateTimeFormat("en-GB", { weekday: "short", day: "numeric", month: "short" });

export interface ShortDue {
  text: string;
  late: boolean;
}

export const hhmm = (ms: number) => {
  const d = new Date(ms);
  return `${pad(d.getHours())}:${pad(d.getMinutes())}`;
};

/** Open todo past due? Timed: after the due time; date-only: after the due day. */
export function isLate(dueAt: number | null, hasTime: boolean, now: number): boolean {
  if (dueAt == null) return false;
  return hasTime ? dueAt < now : startOfDay(dueAt) < startOfDay(now);
}

/** Left-column label for an open todo (chores: their next due date). */
export function openLabel(dueAt: number | null, hasTime: boolean, now: number): ShortDue {
  if (dueAt == null) return { text: "", late: false };
  if (isLate(dueAt, hasTime, now)) return { text: "Late", late: true };
  const days = daysBetween(now, dueAt);
  if (days === 0) return { text: hasTime ? hhmm(dueAt) : "Today", late: false };
  if (days <= 6) return { text: weekdayShort.format(dueAt), late: false };
  return { text: dayMonth.format(dueAt), late: false };
}

/** Left-column label for a finished todo: the weekday it was finished (older than a week: "28 Nov"). */
export function doneLabel(doneAt: number, now: number): string {
  const ago = daysBetween(doneAt, now);
  return ago >= 0 && ago <= 6 ? weekdayShort.format(doneAt) : dayMonth.format(doneAt);
}

/** "Fri 28 Nov", "Fri 28 Nov, 17:00", "Fri 28 Nov 2027". */
export function fullDate(dueAt: number, hasTime: boolean, now: number): string {
  const date = new Date(dueAt).getFullYear() === new Date(now).getFullYear()
    ? weekdayDayMonth.format(dueAt)
    : `${weekdayDayMonth.format(dueAt)} ${new Date(dueAt).getFullYear()}`;
  return hasTime ? `${date}, ${hhmm(dueAt)}` : date;
}

/** "Fri 28 Nov" for date buttons. */
export const shortDate = (ms: number) => weekdayDayMonth.format(ms);

/** "On time", "1 day late", "3 days late", "Late" (same day, after the time) or "No due date". */
export function timeliness(dueAt: number | null, hasTime: boolean, doneAt: number): string {
  if (dueAt == null) return "No due date";
  const days = daysBetween(dueAt, doneAt);
  if (days > 0) return lateDays(days);
  if (hasTime && doneAt > dueAt) return "Late";
  return "On time";
}

export const lateDays = (days: number) => (days === 1 ? "1 day late" : `${days} days late`);

/** "today", "tomorrow", "Fri", "28 Nov". */
export function relativeDay(day: number, now: number): string {
  const days = daysBetween(now, day);
  if (days === 0) return "today";
  if (days === 1) return "tomorrow";
  if (days >= 2 && days <= 6) return weekdayShort.format(day);
  return dayMonth.format(day);
}

/** Done tab week headings: "This week", "Last week", "3–9 Nov". */
export function weekTitle(at: number, now: number): string {
  const monday = startOfWeek(now);
  const week = startOfWeek(at);
  if (week === monday) return "This week";
  if (week === addDays(monday, -7)) return "Last week";
  const sunday = addDays(week, 6);
  const a = new Date(week);
  const b = new Date(sunday);
  return a.getMonth() === b.getMonth()
    ? `${a.getDate()}–${dayMonth.format(sunday)}`
    : `${dayMonth.format(week)}–${dayMonth.format(sunday)}`;
}

/**
 * Typed time (web spec, "Date and time"): "17", "1700", "17:00", "5pm", "5:30 pm", "9.15".
 * Returns minutes after midnight, or null when it isn't a time.
 */
export function parseTime(input: string): number | null {
  const s = input.trim().toLowerCase().replace(/\s+/g, "");
  if (!s) return null;
  const m = /^(\d{1,2})(?:[:.]?(\d{2}))?(am|pm|a|p)?$/.exec(s);
  if (!m) return null;
  let h = Number(m[1]);
  const min = m[2] ? Number(m[2]) : 0;
  const suffix = m[3];
  if (min > 59) return null;
  if (suffix) {
    if (h < 1 || h > 12) return null;
    if (suffix.startsWith("p") && h !== 12) h += 12;
    if (suffix.startsWith("a") && h === 12) h = 0;
  } else if (h > 23) {
    return null;
  }
  return h * 60 + min;
}

export const minutesLabel = (minutes: number) => `${pad(Math.floor(minutes / 60))}:${pad(minutes % 60)}`;

/** "45 s", "1 min", "1 min 30 s" (rest steppers). */
export function restLabel(seconds: number): string {
  const s = Math.max(0, Math.round(seconds));
  if (s < 60) return `${s} s`;
  const min = Math.floor(s / 60);
  const rest = s % 60;
  return rest ? `${min} min ${rest} s` : `${min} min`;
}

/** Repeat units, stored as days like the phone's wheel (week 7, month 30, year 365). */
export const REPEAT_UNITS = [
  { id: "days", days: 1, one: "day", many: "days" },
  { id: "weeks", days: 7, one: "week", many: "weeks" },
  { id: "months", days: 30, one: "month", many: "months" },
  { id: "years", days: 365, one: "year", many: "years" },
] as const;
export type RepeatUnit = (typeof REPEAT_UNITS)[number]["id"];

/** Splits stored days into the largest exact unit: 14 → 2 weeks, 30 → 1 month, 10 → 10 days. */
export function splitInterval(days: number): { count: number; unit: RepeatUnit } {
  const d = Math.max(1, days);
  for (const u of [...REPEAT_UNITS].reverse()) {
    if (d % u.days === 0 && d / u.days <= 99) return { count: d / u.days, unit: u.id };
  }
  return { count: Math.min(99, d), unit: "days" };
}
