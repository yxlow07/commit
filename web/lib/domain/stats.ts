import { addDays, daysBetween, startOfDay, startOfWeek } from "./dates";
import { nextMatchingDay } from "./recurrence";

/** A completion with its todo and commitment (the phone's CompletionRow). */
export interface CompletionRow {
  completionId: string;
  todoId: string;
  title: string;
  commitmentId: string;
  commitmentName: string;
  commitmentColor: number;
  doneAt: number;
  dueAt: number | null;
  hasTime: boolean;
  recurIntervalDays: number | null;
  /** Weekday mask for calendar chores (M10); optional so older callers still type-check. */
  recurDays?: number | null;
}

export function isRecurringRow(r: CompletionRow): boolean {
  return (r.recurIntervalDays ?? 0) > 0 || (r.recurDays ?? 0) > 0;
}

/** A chore's due day after it was done on [doneDay]: + interval, or the next matching weekday (F4.2, F4.3). */
function nextDueAfter(r: CompletionRow, doneDay: number): number {
  const mask = r.recurDays ?? 0;
  return mask > 0 ? nextMatchingDay(mask, addDays(doneDay, 1)) : addDays(doneDay, r.recurIntervalDays ?? 0);
}

/** Due day of a chore's first completion: its due date (the first matching weekday on or after it). */
function firstDueDay(r: CompletionRow): number | null {
  if (r.dueAt == null) return null;
  const mask = r.recurDays ?? 0;
  return mask > 0 ? nextMatchingDay(mask, startOfDay(r.dueAt)) : startOfDay(r.dueAt);
}

export interface CommitmentStats {
  commitmentId: string;
  name: string;
  color: number;
  doneThisWeek: number;
  onTime: number;
  late: number;
  /** Current streak per recurring chore, longest first; only streaks > 0. */
  choreStreaks: [string, number][];
}

/** Best weight and volume of one exercise (by name) in one finished session. */
export interface ExercisePoint {
  exerciseName: string;
  sessionId: string;
  startedAt: number;
  bestWeight: number;
  bestReps: number;
  volume: number;
}

/**
 * Port of the phone's domain/Stats.kt (F8.1–F8.3, F8.6).
 * - One-off todo is late if done after its due time (date-only: after its due day).
 * - A chore's due day at each completion is the previous completion's day + interval (or its first
 *   due date); done on a later day is late.
 * - Chore streak: most recent completions in a row that were on time; 0 once the chore is overdue.
 */
export function lateness(rows: CompletionRow[]): Map<string, boolean> {
  const result = new Map<string, boolean>();
  for (const done of groupBy(rows, (r) => r.todoId).values()) {
    const sorted = [...done].sort((a, b) => a.doneAt - b.doneAt);
    const first = sorted[0];
    if (isRecurringRow(first)) {
      let previous: number | null = null;
      for (const c of sorted) {
        const day = startOfDay(c.doneAt);
        const dueDay = previous != null ? nextDueAfter(first, previous) : firstDueDay(first);
        if (dueDay != null) result.set(c.completionId, day > dueDay);
        previous = day;
      }
    } else {
      for (const c of sorted) {
        if (c.dueAt == null) continue;
        result.set(c.completionId, c.hasTime ? c.doneAt > c.dueAt : daysBetween(c.dueAt, c.doneAt) > 0);
      }
    }
  }
  return result;
}

/**
 * How late each completion was, for history notes: days after its due day (1 = "1 day late"),
 * 0 when late on the due day itself (after a due time), absent when on time or undated.
 */
export function lateDaysByCompletion(rows: CompletionRow[]): Map<string, number> {
  const result = new Map<string, number>();
  for (const done of groupBy(rows, (r) => r.todoId).values()) {
    const sorted = [...done].sort((a, b) => a.doneAt - b.doneAt);
    const first = sorted[0];
    if (isRecurringRow(first)) {
      let previous: number | null = null;
      for (const c of sorted) {
        const day = startOfDay(c.doneAt);
        const dueDay = previous != null ? nextDueAfter(first, previous) : firstDueDay(first);
        if (dueDay != null && day > dueDay) result.set(c.completionId, daysBetween(dueDay, day));
        previous = day;
      }
    } else {
      for (const c of sorted) {
        if (c.dueAt == null) continue;
        const days = daysBetween(c.dueAt, c.doneAt);
        if (days > 0) result.set(c.completionId, days);
        else if (c.hasTime && c.doneAt > c.dueAt) result.set(c.completionId, 0);
      }
    }
  }
  return result;
}

/** Current streak per chore (todo id), only for chores still on time; 0 streaks left out. */
export function choreStreaks(rows: CompletionRow[], now: number): Map<string, number> {
  const late = lateness(rows);
  const today = startOfDay(now);
  const result = new Map<string, number>();
  for (const [todoId, done] of groupBy(rows.filter(isRecurringRow), (r) => r.todoId)) {
    const n = choreStreak(done, late, today);
    if (n > 0) result.set(todoId, n);
  }
  return result;
}

export function commitmentStats(rows: CompletionRow[], now: number): CommitmentStats[] {
  const today = startOfDay(now);
  const weekStart = startOfWeek(now);
  const late = lateness(rows);
  const stats: CommitmentStats[] = [];
  for (const [id, list] of groupBy(rows, (r) => r.commitmentId)) {
    const streaks: [string, number][] = [];
    for (const done of groupBy(list.filter(isRecurringRow), (r) => r.todoId).values()) {
      const s = choreStreak(done, late, today);
      if (s > 0) streaks.push([done[0].title, s]);
    }
    streaks.sort((a, b) => b[1] - a[1]);
    stats.push({
      commitmentId: id,
      name: list[0].commitmentName,
      color: list[0].commitmentColor,
      doneThisWeek: list.filter((r) => startOfDay(r.doneAt) >= weekStart).length,
      onTime: list.filter((r) => late.get(r.completionId) === false).length,
      late: list.filter((r) => late.get(r.completionId) === true).length,
      choreStreaks: streaks,
    });
  }
  return stats.sort((a, b) => b.doneThisWeek - a.doneThisWeek);
}

function choreStreak(done: CompletionRow[], late: Map<string, boolean>, today: number): number {
  const sorted = [...done].sort((a, b) => b.doneAt - a.doneAt);
  if (!isRecurringRow(sorted[0])) return 0;
  const nextDue = nextDueAfter(sorted[0], startOfDay(sorted[0].doneAt));
  if (today > nextDue) return 0;
  let n = 0;
  for (const c of sorted) {
    if (late.get(c.completionId) === true) break;
    n++;
  }
  return n;
}

/** Completions per local day (key: local midnight ms), for the heatmap (F8.2). */
export function perDay(rows: CompletionRow[]): Map<number, number> {
  const map = new Map<number, number>();
  for (const r of rows) {
    const d = startOfDay(r.doneAt);
    map.set(d, (map.get(d) ?? 0) + 1);
  }
  return map;
}

/** Flags sessions whose best weight beat every earlier one (the first is a baseline). */
export function progress(points: ExercisePoint[]): { point: ExercisePoint; isPersonalBest: boolean }[] {
  let best = Number.NEGATIVE_INFINITY;
  return [...points].sort((a, b) => a.startedAt - b.startedAt).map((p, i) => {
    const pb = i > 0 && p.bestWeight > best;
    if (p.bestWeight > best) best = p.bestWeight;
    return { point: p, isPersonalBest: pb };
  });
}

function groupBy<T>(items: T[], key: (t: T) => string): Map<string, T[]> {
  const map = new Map<string, T[]>();
  for (const item of items) {
    const k = key(item);
    const list = map.get(k);
    if (list) list.push(item);
    else map.set(k, [item]);
  }
  return map;
}
