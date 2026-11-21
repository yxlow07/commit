import { daysBetween, pad } from "./dates";

export type DueStatus = "none" | "overdue" | "today" | "upcoming";

export interface DueLabel {
  text: string;
  status: DueStatus;
}

const weekday = new Intl.DateTimeFormat("en-GB", { weekday: "short" });
const dayMonth = new Intl.DateTimeFormat("en-GB", { day: "numeric", month: "short" });
const dayMonthYear = new Intl.DateTimeFormat("en-GB", { day: "numeric", month: "short", year: "numeric" });

/**
 * Port of the phone's ui/format/DueFormatter.kt (F3.2, F2.4). The text always says "Overdue" /
 * "Today", so colour is never the only signal.
 */
export function formatDue(dueAt: number | null, hasTime: boolean, now: number): DueLabel {
  if (dueAt == null) return { text: "", status: "none" };
  const due = new Date(dueAt);
  const time = hasTime ? `${pad(due.getHours())}:${pad(due.getMinutes())}` : null;

  const overdue = hasTime ? dueAt < now : daysBetween(dueAt, now) > 0;
  if (overdue) {
    const daysLate = daysBetween(dueAt, now);
    return { text: `Overdue · ${daysLate === 0 ? time : `${daysLate}d`}`, status: "overdue" };
  }

  const days = daysBetween(now, dueAt);
  let day: string;
  if (days === 0) day = "Today";
  else if (days === 1) day = "Tomorrow";
  else if (days < 7) day = weekday.format(due);
  else if (due.getFullYear() === new Date(now).getFullYear()) day = dayMonth.format(due);
  else day = dayMonthYear.format(due);
  return { text: time ? `${day} ${time}` : day, status: days === 0 ? "today" : "upcoming" };
}
