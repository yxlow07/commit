"use client";

import type { QueryClient } from "@tanstack/react-query";
import { supabase } from "./supabase";
import { putRow, tableKey } from "./store";
import type { Commitment, CommitmentType, Exercise, Routine, Settings, TableName, Tables, Todo } from "@/lib/domain/types";
import { isDone } from "@/lib/domain/recurrence";

/**
 * Writes with the same rules as the phone: client-generated UUIDs, soft deletes, sort_order for
 * reordering. The server stamps user_id and updated_at (triggers), and its webhook pings the
 * phone to sync. Each write patches the local cache with the row the server returns.
 */

type Insert<T extends TableName> = Omit<Tables[T], "user_id" | "updated_at" | "deleted"> & { deleted?: 0 | 1 };

async function insertRow<T extends TableName>(qc: QueryClient, table: T, row: Insert<T>): Promise<Tables[T]> {
  const { data, error } = await supabase.from(table).insert({ deleted: 0, ...row }).select().single();
  if (error) throw error;
  putRow(qc, table, data as Tables[T]);
  return data as Tables[T];
}

async function updateRow<T extends TableName>(qc: QueryClient, table: T, id: string, changes: Partial<Tables[T]>): Promise<Tables[T]> {
  const { data, error } = await supabase.from(table).update(changes as never).eq("id", id).select().single();
  if (error) throw error;
  putRow(qc, table, data as Tables[T]);
  return data as Tables[T];
}

const softDelete = (qc: QueryClient, table: TableName, id: string) => updateRow(qc, table, id, { deleted: 1 });

/**
 * Saves [ids] as the new order of [items] (drag to reorder): each row's sort_order becomes its index,
 * writing only rows that changed. Rows of [items] missing from [ids] keep their place after them.
 */
async function reorderTo<T extends "commitments" | "routines" | "exercises">(
  qc: QueryClient, table: T, items: Tables[T][], ids: string[],
) {
  const byId = new Map(items.map((x) => [x.id, x]));
  const list = [...ids.map((id) => byId.get(id)).filter((x): x is Tables[T] => x != null), ...items.filter((x) => !ids.includes(x.id))];
  await Promise.all(
    list.map((x, i) => (x.sort_order === i ? null : updateRow(qc, table, x.id, { sort_order: i } as Partial<Tables[T]>))),
  );
}

// ---- Commitments (F1) ----

export const createCommitment = (qc: QueryClient, name: string, type: CommitmentType, color: number, sortOrder: number) =>
  insertRow(qc, "commitments", {
    id: crypto.randomUUID(), name: name.trim(), type, color, sort_order: sortOrder, show_in_widget: 1, archived: 0,
  });

export const updateCommitment = (qc: QueryClient, id: string, changes: Partial<Commitment>) =>
  updateRow(qc, "commitments", id, changes);

export const reorderCommitments = (qc: QueryClient, items: Commitment[], ids: string[]) =>
  reorderTo(qc, "commitments", items, ids);

/** Brings an archived commitment back, at the end of the list (as on the phone). */
export const restoreCommitment = (qc: QueryClient, id: string, sortOrder: number) =>
  updateRow(qc, "commitments", id, { archived: 0, sort_order: sortOrder });

// ---- Todos (F2, F4) ----

export interface TodoInput {
  title: string;
  commitmentId: string;
  dueAt: number | null;
  hasTime: boolean;
  repeatDays: number | null;
  recurDays?: number | null;
  reminderLeadMinutes?: number | null;
}

export const createTodo = (qc: QueryClient, input: TodoInput) =>
  insertRow(qc, "todos", {
    id: crypto.randomUUID(), commitment_id: input.commitmentId, title: input.title.trim(),
    due_at: input.dueAt, has_time: input.hasTime ? 1 : 0, done_at: null,
    recur_interval_days: input.repeatDays && input.repeatDays > 0 ? input.repeatDays : null,
    recur_days: input.recurDays && input.recurDays > 0 ? input.recurDays : null,
    last_done_at: null,
    reminder_lead_minutes: input.reminderLeadMinutes ?? null,
  });

export const updateTodo = (qc: QueryClient, id: string, input: TodoInput) =>
  updateRow(qc, "todos", id, {
    title: input.title.trim(), commitment_id: input.commitmentId, due_at: input.dueAt, has_time: input.hasTime ? 1 : 0,
    recur_interval_days: input.repeatDays && input.repeatDays > 0 ? input.repeatDays : null,
    recur_days: input.recurDays && input.recurDays > 0 ? input.recurDays : null,
    reminder_lead_minutes: input.reminderLeadMinutes ?? null,
  });

export const deleteTodo = (qc: QueryClient, id: string) => softDelete(qc, "todos", id);

/**
 * Tick / untick (F2.2, F4.5) through the `toggle_todo` SQL function, which logs completions and
 * updates done_at / last_done_at in one transaction, exactly like the phone.
 */
export async function setTodoDone(qc: QueryClient, todo: Todo, done: boolean) {
  if (isDone(todo, Date.now()) === done) return;
  const { error } = await supabase.rpc("toggle_todo", { p_todo_id: todo.id, p_done: done });
  if (error) throw error;
  await Promise.all([
    qc.invalidateQueries({ queryKey: tableKey("todos") }),
    qc.invalidateQueries({ queryKey: tableKey("completions") }),
  ]);
}

// ---- Routines and exercises (F5.1) ----

export const createRoutine = (qc: QueryClient, commitmentId: string, name: string, sortOrder: number) =>
  insertRow(qc, "routines", {
    id: crypto.randomUUID(), commitment_id: commitmentId, name: name.trim(), rest_between_exercises_s: 120,
    sort_order: sortOrder, planned_days: 0,
  });

export const updateRoutine = (qc: QueryClient, id: string, changes: Partial<Routine>) => updateRow(qc, "routines", id, changes);
export const deleteRoutine = (qc: QueryClient, id: string) => softDelete(qc, "routines", id);
export const reorderRoutines = (qc: QueryClient, items: Routine[], ids: string[]) => reorderTo(qc, "routines", items, ids);

export interface ExerciseInput {
  name: string;
  sets: number;
  reps: number;
  weight: number;
  restSeconds: number;
  durationSeconds: number | null;
}

const exerciseFields = (e: ExerciseInput) => ({
  name: e.name.trim(), sets: Math.max(1, e.sets), reps: Math.max(0, e.reps), weight: Math.max(0, e.weight),
  rest_seconds: Math.max(0, e.restSeconds), duration_seconds: e.durationSeconds && e.durationSeconds > 0 ? e.durationSeconds : null,
});

export const createExercise = (qc: QueryClient, routineId: string, e: ExerciseInput, sortOrder: number) =>
  insertRow(qc, "exercises", { id: crypto.randomUUID(), routine_id: routineId, sort_order: sortOrder, ...exerciseFields(e) });

export const updateExercise = (qc: QueryClient, id: string, e: ExerciseInput) => updateRow(qc, "exercises", id, exerciseFields(e));
export const deleteExercise = (qc: QueryClient, id: string) => softDelete(qc, "exercises", id);
export const reorderExercises = (qc: QueryClient, items: Exercise[], ids: string[]) => reorderTo(qc, "exercises", items, ids);

// ---- History (F8.5) ----

export const editSet = (qc: QueryClient, id: string, reps: number, weight: number) =>
  updateRow(qc, "workout_sets", id, { reps: Math.max(0, reps), weight: Math.max(0, weight) });
export const deleteSet = (qc: QueryClient, id: string) => softDelete(qc, "workout_sets", id);

// ---- Settings (F6) ----

/** The one settings row per user, shared with the phone (`settings-<user id>`). */
export const settingsId = (userId: string) => `settings-${userId}`;

export const DEFAULT_SETTINGS = {
  morning_recap_time: 7 * 60 + 30, night_recap_time: 22 * 60, morning_recap_enabled: 1, night_recap_enabled: 1, active_theme_id: null,
} satisfies Partial<Settings>;

export async function saveSettings(qc: QueryClient, userId: string, current: Settings | undefined, changes: Partial<Settings>) {
  if (current) return updateRow(qc, "settings", current.id, changes);
  return insertRow(qc, "settings", { id: settingsId(userId), ...DEFAULT_SETTINGS, ...changes } as Insert<"settings">);
}

// ---- Themes (F9) ----

export const createTheme = (qc: QueryClient, name: string, json: string) =>
  insertRow(qc, "themes", {
    id: crypto.randomUUID(),
    name: name.trim(),
    json,
  });

export const deleteTheme = async (qc: QueryClient, id: string, activeId: string | null, userId: string) => {
  await softDelete(qc, "themes", id);
  if (activeId === id) {
    await updateRow(qc, "settings", settingsId(userId), { active_theme_id: null });
  }
};
