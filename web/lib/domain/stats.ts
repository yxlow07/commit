import { addDays, daysBetween, startOfDay, startOfWeek } from "./dates";

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
    const interval = sorted[0].recurIntervalDays ?? 0;
    if (interval > 0) {
      let previous: number | null = null;
      for (const c of sorted) {
        const day = startOfDay(c.doneAt);
        const dueDay = previous != null ? addDays(previous, interval) : c.dueAt != null ? startOfDay(c.dueAt) : null;
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

export function commitmentStats(rows: CompletionRow[], now: number): CommitmentStats[] {
  const today = startOfDay(now);
  const weekStart = startOfWeek(now);
  const late = lateness(rows);
  const stats: CommitmentStats[] = [];
  for (const [id, list] of groupBy(rows, (r) => r.commitmentId)) {
    const streaks: [string, number][] = [];
    for (const done of groupBy(list.filter((r) => (r.recurIntervalDays ?? 0) > 0), (r) => r.todoId).values()) {
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
  const interval = sorted[0].recurIntervalDays;
  if (!interval) return 0;
  const nextDue = addDays(startOfDay(sorted[0].doneAt), interval);
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
