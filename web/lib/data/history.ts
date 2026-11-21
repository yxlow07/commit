import type { Commitment, Completion, Exercise, Routine, Todo, WorkoutSession, WorkoutSet } from "@/lib/domain/types";
import type { CompletionRow, ExercisePoint } from "@/lib/domain/stats";

/** Client-side joins equivalent to the phone's history queries (WorkoutDaos.kt, LaterDaos.kt). */

/** Completions with their todo and commitment, newest first (skips deleted todos/commitments). */
export function completionRows(completions: Completion[], todos: Todo[], commitments: Commitment[]): CompletionRow[] {
  const todoById = new Map(todos.map((t) => [t.id, t]));
  const commitmentById = new Map(commitments.map((c) => [c.id, c]));
  const rows: CompletionRow[] = [];
  for (const c of completions) {
    const t = todoById.get(c.todo_id);
    const m = t && commitmentById.get(t.commitment_id);
    if (!t || !m) continue;
    rows.push({
      completionId: c.id, todoId: t.id, title: t.title, commitmentId: m.id, commitmentName: m.name, commitmentColor: m.color,
      doneAt: c.done_at, dueAt: t.due_at, hasTime: t.has_time === 1, recurIntervalDays: t.recur_interval_days, recurDays: t.recur_days,
    });
  }
  return rows.sort((a, b) => b.doneAt - a.doneAt);
}

export interface SessionSummary {
  session: WorkoutSession;
  routineName: string;
  volume: number;
  setCount: number;
}

/** Finished sessions with routine name and set totals, newest first (F8.4). */
export function sessionSummaries(sessions: WorkoutSession[], sets: WorkoutSet[], routines: Routine[]): SessionSummary[] {
  const routineName = new Map(routines.map((r) => [r.id, r.name]));
  const bySession = new Map<string, WorkoutSet[]>();
  for (const s of sets) bySession.set(s.session_id, [...(bySession.get(s.session_id) ?? []), s]);
  return sessions
    .filter((s) => s.ended_at != null)
    .map((s) => {
      const list = bySession.get(s.id) ?? [];
      return {
        session: s,
        routineName: routineName.get(s.routine_id) ?? "Workout",
        volume: list.reduce((sum, x) => sum + x.reps * x.weight, 0),
        setCount: list.length,
      };
    })
    .sort((a, b) => b.session.started_at - a.session.started_at);
}

/** One point per (exercise name, finished session), oldest first; timed sets (0 reps) ignored (F8.6). */
export function exercisePoints(sets: WorkoutSet[], exercises: Exercise[], sessions: WorkoutSession[]): ExercisePoint[] {
  const exerciseById = new Map(exercises.map((e) => [e.id, e]));
  const sessionById = new Map(sessions.map((s) => [s.id, s]));
  const points = new Map<string, ExercisePoint>();
  for (const set of sets) {
    const e = exerciseById.get(set.exercise_id);
    const s = sessionById.get(set.session_id);
    if (!e || !s || s.ended_at == null || set.skipped || set.reps <= 0) continue;
    const key = `${e.name.toLowerCase()}|${s.id}`;
    const p = points.get(key) ?? { exerciseName: e.name, sessionId: s.id, startedAt: s.started_at, bestWeight: 0, bestReps: 0, volume: 0 };
    p.bestWeight = Math.max(p.bestWeight, set.weight);
    p.bestReps = Math.max(p.bestReps, set.reps);
    p.volume += set.reps * set.weight;
    points.set(key, p);
  }
  return [...points.values()].sort((a, b) => a.startedAt - b.startedAt);
}
