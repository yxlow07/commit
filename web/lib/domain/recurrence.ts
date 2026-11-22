import { addDays, startOfDay } from "./dates";
import type { Todo } from "./types";

/**
 * Port of the phone's domain/Recurrence.kt (F4). "Done" is calculated, never stored:
 * - Interval mode (F4.2): chore done on day D with interval N is due again at the start of day D + N.
 * - Calendar mode (F4.3): chore done on day D with weekday mask M is due again on the next matching day after D.
 * A chore is "done" while today is before that day.
 */
export function isRecurring(todo: Todo): boolean {
  return (todo.recur_interval_days ?? 0) > 0 || (todo.recur_days ?? 0) > 0;
}

const WEEKDAY_NAMES = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"];

export function weekdaysLabel(mask: number): string {
  const parts: string[] = [];
  for (let i = 0; i < 7; i++) {
    if ((mask & (1 << i)) !== 0) parts.push(WEEKDAY_NAMES[i]);
  }
  return parts.join(" · ");
}

/** "every day" / "every 4 days". */
/** Units the phone's wheel uses (stored as days: week 7, month 30, year 365); the largest exact one wins. */
const INTERVAL_UNITS: [number, string, string][] = [[365, "year", "years"], [30, "month", "months"], [7, "week", "weeks"], [1, "day", "days"]];

/** "every day", "every 4 days", "every 2 weeks", "every month". */
export function intervalLabel(days: number): string {
  const d = Math.max(1, days);
  const [size, one, many] = INTERVAL_UNITS.find(([u]) => d % u === 0)!;
  const n = d / size;
  return n === 1 ? `every ${one}` : `every ${n} ${many}`;
}

/** User-facing label for recurring chores. */
export function recurrenceLabel(todo: Todo): string {
  if ((todo.recur_days ?? 0) > 0) return weekdaysLabel(todo.recur_days!);
  if ((todo.recur_interval_days ?? 0) > 0) return intervalLabel(todo.recur_interval_days!);
  return "";
}

/** Finds the earliest day on or after [fromDay] that matches [mask]. */
export function nextMatchingDay(mask: number, fromDay: number): number {
  let cur = fromDay;
  for (let i = 0; i <= 7; i++) {
    const dow = (new Date(cur).getDay() + 6) % 7; // Monday = 0 ... Sunday = 6
    if ((mask & (1 << dow)) !== 0) return cur;
    cur = addDays(cur, 1);
  }
  return fromDay;
}

/** Next due day (local midnight) of a recurring todo; the due day of a one-off todo. */
export function nextDueDay(todo: Todo, now: number): number | null {
  if (!isRecurring(todo)) return todo.due_at == null ? null : startOfDay(todo.due_at);
  if ((todo.recur_days ?? 0) > 0) {
    // Never done: the first matching day on or after the due date (or today); a missed day stays
    // as the due date, so the chore shows as overdue until done (same as the phone).
    if (todo.last_done_at == null) {
      return nextMatchingDay(todo.recur_days!, todo.due_at == null ? startOfDay(now) : startOfDay(todo.due_at));
    }
    const strictlyAfter = addDays(startOfDay(todo.last_done_at), 1);
    return nextMatchingDay(todo.recur_days!, strictlyAfter);
  }
  if (todo.last_done_at == null) return todo.due_at == null ? startOfDay(now) : startOfDay(todo.due_at);
  return addDays(startOfDay(todo.last_done_at), todo.recur_interval_days!);
}

export function isDone(todo: Todo, now: number): boolean {
  if (!isRecurring(todo)) return todo.done_at != null;
  if (todo.last_done_at == null) return false;
  const next = nextDueDay(todo, now);
  return next != null && startOfDay(now) < next;
}

/** Due instant and whether it has a time, for sorting and labels (F3.5). */
export function effectiveDue(todo: Todo, now: number): { dueAt: number | null; hasTime: boolean } {
  if (!isRecurring(todo)) return { dueAt: todo.due_at, hasTime: todo.has_time === 1 };
  const next = nextDueDay(todo, now);
  // Never done and due on its own due date: keep the due time ("Today 18:00").
  if (todo.last_done_at == null && todo.due_at != null && next === startOfDay(todo.due_at)) {
    return { dueAt: todo.due_at, hasTime: todo.has_time === 1 };
  }
  return { dueAt: next, hasTime: false };
}

/** A todo with its calculated state, as shown in lists. */
export interface TodoItem {
  todo: Todo;
  dueAt: number | null;
  hasTime: boolean;
  isDone: boolean;
  isRecurring: boolean;
}

export function todoItem(todo: Todo, now: number): TodoItem {
  const { dueAt, hasTime } = effectiveDue(todo, now);
  return { todo, dueAt, hasTime, isDone: isDone(todo, now), isRecurring: isRecurring(todo) };
}

/** Widget order: by due date, undated last, then title (F3.2). */
export function compareItems(a: TodoItem, b: TodoItem): number {
  if ((a.dueAt == null) !== (b.dueAt == null)) return a.dueAt == null ? 1 : -1;
  if (a.dueAt != null && b.dueAt != null && a.dueAt !== b.dueAt) return a.dueAt - b.dueAt;
  return a.todo.title.toLowerCase().localeCompare(b.todo.title.toLowerCase());
}

/** Undone items in widget order. */
export function openItems(todos: Todo[], now: number): TodoItem[] {
  return todos.map((t) => todoItem(t, now)).filter((i) => !i.isDone).sort(compareItems);
}
