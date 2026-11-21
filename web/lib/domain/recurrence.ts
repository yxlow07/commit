import { addDays, startOfDay } from "./dates";
import type { Todo } from "./types";

/**
 * Port of the phone's domain/Recurrence.kt (F4). "Done" is calculated, never stored: a chore done
 * on day D with interval N is due again at the start of day D + N (local time), so it is "done"
 * while today is before that day.
 */
export function isRecurring(todo: Todo): boolean {
  return (todo.recur_interval_days ?? 0) > 0;
}

/** Next due day (local midnight) of a recurring todo; the due day of a one-off todo. */
export function nextDueDay(todo: Todo, now: number): number | null {
  if (!isRecurring(todo)) return todo.due_at == null ? null : startOfDay(todo.due_at);
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
  if (!isRecurring(todo) || (todo.last_done_at == null && todo.due_at != null)) {
    return { dueAt: todo.due_at, hasTime: todo.has_time === 1 };
  }
  return { dueAt: nextDueDay(todo, now), hasTime: false };
}

/** "every day" / "every 4 days". */
export function intervalLabel(days: number): string {
  return days === 1 ? "every day" : `every ${days} days`;
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
