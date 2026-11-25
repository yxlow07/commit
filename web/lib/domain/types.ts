/**
 * Row types mirroring the Supabase tables (supabase/migrations/0001_init.sql), which mirror the
 * phone's Room tables. Booleans are stored as 0/1 and times as epoch milliseconds on both sides,
 * so rows round-trip between phone and web unchanged.
 */

export type Flag = 0 | 1;

interface Synced {
  id: string;
  user_id: string;
  updated_at: number;
  deleted: Flag;
}

/** standard = todos, workout = routines, collection = a standalone collection tracker. */
export type CommitmentType = "standard" | "workout" | "collection";

export interface Commitment extends Synced {
  name: string;
  type: CommitmentType;
  /** ARGB colour as a signed 32-bit int (Kotlin Int). */
  color: number;
  sort_order: number;
  show_in_widget: Flag;
  archived: Flag;
}

export interface Todo extends Synced {
  commitment_id: string;
  title: string;
  /** Due instant; when has_time is 0 only the local date matters (stored as local midnight). */
  due_at: number | null;
  has_time: Flag;
  /** Completion time of a one-off todo. */
  done_at: number | null;
  /** Interval recurrence: due again N days after last_done_at. */
  recur_interval_days: number | null;
  /** Calendar recurrence: weekday bitmask (Mon = 1 ... Sun = 64). */
  recur_days: number | null;
  last_done_at: number | null;
  /** Reminder lead time in minutes before due_at (e.g. 0, 15, 30, 60). */
  reminder_lead_minutes: number | null;
}

export interface Completion extends Synced {
  todo_id: string;
  done_at: number;
}

export interface Routine extends Synced {
  commitment_id: string;
  name: string;
  rest_between_exercises_s: number;
  sort_order: number;
  /** Weekday bitmask, Monday = 1 … Sunday = 64. */
  planned_days: number;
}

export interface Exercise extends Synced {
  routine_id: string;
  name: string;
  sets: number;
  reps: number;
  weight: number;
  rest_seconds: number;
  /** Set for timed exercises (e.g. plank). */
  duration_seconds: number | null;
  sort_order: number;
}

export interface WorkoutSession extends Synced {
  routine_id: string;
  started_at: number;
  ended_at: number | null;
  notes: string | null;
}

export interface WorkoutSet extends Synced {
  session_id: string;
  exercise_id: string;
  set_no: number;
  reps: number;
  weight: number;
  done_at: number | null;
  skipped: Flag;
}

/** How a collection group orders its items. */
export type CollectionSort = "manual" | "name" | "missing_first" | "collected_first" | "newest";

/** A group inside a collection commitment (e.g. "1990s" in "Stamps"). */
export interface CollectionGroup extends Synced {
  commitment_id: string;
  name: string;
  sort_order: number;
  sort_mode: CollectionSort;
}

/** An item: a name, a photo (Storage key in the private `collection-photos` bucket), or both. */
export interface CollectionItem extends Synced {
  group_id: string;
  name: string | null;
  photo_path: string | null;
  collected: Flag;
  sort_order: number;
  created_at: number;
}

export interface Settings extends Synced {
  morning_recap_time: number;
  night_recap_time: number;
  morning_recap_enabled: Flag;
  night_recap_enabled: Flag;
  active_theme_id: string | null;
}

export interface Theme extends Synced {
  name: string;
  json: string;
}

export interface Tables {
  commitments: Commitment;
  todos: Todo;
  completions: Completion;
  routines: Routine;
  exercises: Exercise;
  workout_sessions: WorkoutSession;
  workout_sets: WorkoutSet;
  collection_groups: CollectionGroup;
  collection_items: CollectionItem;
  settings: Settings;
  themes: Theme;
}

export type TableName = keyof Tables;

export const TABLES: TableName[] = [
  "commitments", "todos", "completions", "routines", "exercises", "workout_sessions", "workout_sets",
  "collection_groups", "collection_items", "settings", "themes",
];
