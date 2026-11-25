/**
 * Demo mode (`NEXT_PUBLIC_DEMO=1`, local preview only): an in-memory stand-in for the parts of the
 * Supabase client the site uses, seeded with sample data, so every page can be viewed and designed
 * without signing in. Nothing leaves the browser and nothing persists across reloads.
 */
import type { Tables, TableName } from "@/lib/domain/types";

type Row = Record<string, unknown> & { id: string };
type Result = { data: unknown; error: null };

const USER = "demo-user";
const DAY = 86_400_000;

function midnight(offsetDays: number): number {
  const d = new Date();
  d.setHours(0, 0, 0, 0);
  return d.getTime() + offsetDays * DAY;
}
function at(offsetDays: number, hour: number, minute = 0): number {
  return midnight(offsetDays) + (hour * 60 + minute) * 60_000;
}
/** ARGB as a signed 32-bit int, as the phone stores it. */
const argb = (hex: number) => (0xff000000 | hex) | 0;

function seed(): { [T in TableName]: Row[] } {
  const base = { user_id: USER, updated_at: Date.now(), deleted: 0 };
  const commitments = [
    { id: "c-study", name: "Study", type: "standard", color: argb(0x3d6fd8), sort_order: 0, show_in_widget: 1, archived: 0 },
    { id: "c-research", name: "Research", type: "standard", color: argb(0x8a5cc7), sort_order: 1, show_in_widget: 1, archived: 0 },
    { id: "c-home", name: "Home", type: "standard", color: argb(0x2f9e72), sort_order: 2, show_in_widget: 1, archived: 0 },
    { id: "c-gym", name: "Gym", type: "workout", color: argb(0xd2622f), sort_order: 3, show_in_widget: 1, archived: 0 },
    { id: "c-monster", name: "Monster cans", type: "collection", color: argb(0x3fa34d), sort_order: 4, show_in_widget: 0, archived: 0 },
  ];
  const lines: [string, string[]][] = [
    ["Original", ["Original", "Zero Sugar", "Khaos", "Assault", "M-80"]],
    ["Ultra", ["Zero Ultra", "Ultra Paradise", "Ultra Violet", "Ultra Red", "Ultra Watermelon", "Ultra Fiesta Mango", "Ultra Strawberry Dreams"]],
    ["Juice", ["Mango Loco", "Pipeline Punch", "Pacific Punch", "Khaotic", "Monarch"]],
    ["Rehab", ["Rehab Orangeade"]],
  ];
  const groups = lines.map(([name], i) => ({ id: `g-${i}`, commitment_id: "c-monster", name, sort_order: i, sort_mode: i === 1 ? "missing_first" : "manual" }));
  const items = lines.flatMap(([, names], gi) => names.map((name, i) => ({
    id: `ci-${gi}-${i}`, group_id: `g-${gi}`, name, photo_path: null, collected: (gi + i) % 3 === 0 ? 1 : 0, sort_order: i, created_at: Date.now() - (gi * 10 + i) * DAY,
  })));
  const todo = (id: string, commitment_id: string, title: string, extra: Partial<Tables["todos"]> = {}) => ({
    id, commitment_id, title, due_at: null, has_time: 0, done_at: null, recur_interval_days: null, recur_days: null,
    last_done_at: null, reminder_lead_minutes: null, ...extra,
  });
  const todos = [
    todo("t1", "c-study", "Problem set 4", { due_at: midnight(-1) }),
    todo("t2", "c-study", "Read chapter 7", { due_at: at(0, 18, 0), has_time: 1, reminder_lead_minutes: 30 }),
    todo("t3", "c-study", "Essay draft", { due_at: midnight(2) }),
    todo("t4", "c-study", "Revise lecture notes", { recur_days: 1 | 4 | 16, last_done_at: at(-2, 20) }),
    todo("t5", "c-research", "Email supervisor", { due_at: midnight(1) }),
    todo("t6", "c-research", "Lit review: 5 papers", { due_at: midnight(5) }),
    todo("t7", "c-research", "Clean dataset"),
    todo("t8", "c-home", "Water plants", { recur_interval_days: 3, last_done_at: at(-3, 9) }),
    todo("t9", "c-home", "Laundry", { recur_interval_days: 7, last_done_at: at(-5, 11) }),
    todo("t10", "c-home", "Pay rent", { due_at: midnight(6) }),
    todo("t11", "c-study", "Submit lab report", { due_at: midnight(-3), done_at: at(-3, 16) }),
  ];
  const completions: Row[] = [];
  let n = 0;
  for (let d = 0; d < 80; d++) {
    const count = (d * 7919) % 5 === 0 ? 0 : ((d * 31) % 4) + (d < 14 ? 1 : 0);
    for (let i = 0; i < count; i++) {
      completions.push({ id: `cp${n++}`, todo_id: ["t4", "t8", "t9", "t11"][i % 4], done_at: at(-d, 9 + i * 3) });
    }
  }
  const routines = [
    { id: "r-push", commitment_id: "c-gym", name: "Push", rest_between_exercises_s: 120, sort_order: 0, planned_days: 1 | 8 },
    { id: "r-pull", commitment_id: "c-gym", name: "Pull", rest_between_exercises_s: 120, sort_order: 1, planned_days: 2 | 16 },
  ];
  const exercises = [
    { id: "e-bench", routine_id: "r-push", name: "Bench press", sets: 3, reps: 8, weight: 60, rest_seconds: 90, duration_seconds: null, sort_order: 0 },
    { id: "e-ohp", routine_id: "r-push", name: "Overhead press", sets: 3, reps: 8, weight: 40, rest_seconds: 90, duration_seconds: null, sort_order: 1 },
    { id: "e-dips", routine_id: "r-push", name: "Dips", sets: 3, reps: 10, weight: 0, rest_seconds: 60, duration_seconds: null, sort_order: 2 },
    { id: "e-row", routine_id: "r-pull", name: "Barbell row", sets: 3, reps: 8, weight: 55, rest_seconds: 90, duration_seconds: null, sort_order: 0 },
    { id: "e-plank", routine_id: "r-pull", name: "Plank", sets: 2, reps: 0, weight: 0, rest_seconds: 30, duration_seconds: 45, sort_order: 1 },
  ];
  const sessions: Row[] = [];
  const sets: Row[] = [];
  for (let w = 0; w < 8; w++) {
    for (const [routine, dayOffset, list] of [["r-push", 1, ["e-bench", "e-ohp", "e-dips"]], ["r-pull", 4, ["e-row"]]] as const) {
      const start = at(-(w * 7 + dayOffset), 18, 10);
      const id = `s-${routine}-${w}`;
      sessions.push({ id, routine_id: routine, started_at: start, ended_at: start + 52 * 60_000, notes: null });
      list.forEach((ex, i) => {
        const e = exercises.find((x) => x.id === ex)!;
        for (let k = 1; k <= e.sets; k++) {
          sets.push({
            id: `${id}-${ex}-${k}`, session_id: id, exercise_id: ex, set_no: k, reps: e.reps,
            weight: Math.max(0, e.weight - w * 2.5), done_at: start + (i * 15 + k * 4) * 60_000, skipped: 0,
          });
        }
      });
    }
  }
  const settings = [{ id: "settings", morning_recap_time: 7 * 60, night_recap_time: 21 * 60, morning_recap_enabled: 1, night_recap_enabled: 1, active_theme_id: null }];
  const add = (rows: object[]) => rows.map((r) => ({ ...base, ...r }) as unknown as Row);
  return {
    commitments: add(commitments), todos: add(todos), completions: add(completions), routines: add(routines),
    exercises: add(exercises), workout_sessions: add(sessions), workout_sets: add(sets),
    collection_groups: add(groups), collection_items: add(items), settings: add(settings), themes: [],
  };
}

const db = seed();
const demoPhotos = new Map<string, string>();

/** Chainable query in the shape of PostgREST's builder; awaiting it runs it. */
class Query implements PromiseLike<Result> {
  private filters: [string, unknown][] = [];
  private op: "select" | "insert" | "update" = "select";
  private payload: Row | Partial<Row> | null = null;
  private one = false;
  private window: [number, number] | null = null;

  constructor(private table: TableName) {}

  select() { return this; }
  order() { return this; }
  eq(col: string, value: unknown) { this.filters.push([col, value]); return this; }
  range(from: number, to: number) { this.window = [from, to]; return this; }
  single() { this.one = true; return this; }
  insert(row: Row) { this.op = "insert"; this.payload = row; return this; }
  update(changes: Partial<Row>) { this.op = "update"; this.payload = changes; return this; }

  private run(): Result {
    const rows = db[this.table];
    const stamp = { user_id: USER, updated_at: Date.now() };
    if (this.op === "insert") {
      const row = { ...stamp, deleted: 0, ...this.payload } as Row;
      rows.push(row);
      return { data: this.one ? row : [row], error: null };
    }
    const hit = rows.filter((r) => this.filters.every(([c, v]) => r[c] === v));
    if (this.op === "update") {
      hit.forEach((r) => Object.assign(r, this.payload, stamp));
      return { data: this.one ? hit[0] ?? null : hit, error: null };
    }
    const page = this.window ? hit.slice(this.window[0], this.window[1] + 1) : hit;
    return { data: structuredClone(this.one ? page[0] ?? null : page), error: null };
  }

  then<A = Result, B = never>(ok?: ((v: Result) => A | PromiseLike<A>) | null, fail?: ((e: unknown) => B | PromiseLike<B>) | null) {
    return new Promise<Result>((resolve) => setTimeout(() => resolve(this.run()), 60)).then(ok, fail);
  }
}

function toggleTodo(id: string, done: boolean) {
  const t = db.todos.find((r) => r.id === id && !r.deleted);
  if (!t) return;
  const recurring = ((t.recur_interval_days as number) ?? 0) > 0 || ((t.recur_days as number) ?? 0) > 0;
  const now = Date.now();
  if (done) {
    db.completions.push({ id: crypto.randomUUID(), todo_id: id, done_at: now, user_id: USER, updated_at: now, deleted: 0 });
    Object.assign(t, recurring ? { last_done_at: now } : { done_at: now }, { updated_at: now });
  } else {
    const latest = db.completions.filter((c) => c.todo_id === id && !c.deleted).sort((a, b) => (b.done_at as number) - (a.done_at as number))[0];
    if (latest) latest.deleted = 1;
    Object.assign(t, recurring ? { last_done_at: null } : { done_at: null }, { updated_at: now });
  }
}

const session = {
  user: { id: USER, email: "demo@example.com", user_metadata: { full_name: "Demo" } },
  access_token: "demo", refresh_token: "demo", expires_in: 3600, token_type: "bearer",
};

export function demoClient() {
  const channel = { on: () => channel, subscribe: () => channel };
  return {
    from: (table: TableName) => new Query(table),
    rpc: async (fn: string, args: { p_todo_id: string; p_done: boolean }) => {
      if (fn === "toggle_todo") toggleTodo(args.p_todo_id, args.p_done);
      return { data: null, error: null };
    },
    channel: () => channel,
    // Photos stay in memory as object URLs; nothing is uploaded.
    storage: {
      from: () => ({
        upload: async (key: string, blob: Blob) => { demoPhotos.set(key, URL.createObjectURL(blob)); return { data: { path: key }, error: null }; },
        createSignedUrls: async (keys: string[]) => ({ data: keys.map((path) => ({ path, signedUrl: demoPhotos.get(path) ?? "" })), error: null }),
      }),
    },
    removeChannel: async () => "ok",
    auth: {
      getSession: async () => ({ data: { session }, error: null }),
      onAuthStateChange: () => ({ data: { subscription: { unsubscribe() {} } } }),
      signInWithOAuth: async () => ({ data: null, error: null }),
      signOut: async () => ({ error: null }),
    },
  };
}
