-- Commit: Supabase schema (generated from app/schemas/.../5.json; mirrors Room v5 minus the phone-only `dirty` flag).
-- Apply in the Supabase SQL editor or with `supabase db push`. See app/SYNC_SETUP.md.

-- Every synced row belongs to one user and gets a server-clock updated_at (PRD sync rule 4).
-- The phone pulls rows with updated_at > its last sync, so updated_at must always come from this clock.
-- Owner: set from the signed-in user on insert and never changed by an update. With no signed-in user
-- (Table editor, SQL editor, service role) auth.uid() is null, so inserts keep the given user_id.
create or replace function public.commit_row_meta() returns trigger
language plpgsql security invoker set search_path = public as $$
begin
  if tg_op = 'UPDATE' then
    new.user_id := old.user_id;
  else
    new.user_id := coalesce(auth.uid(), new.user_id);
  end if;
  new.updated_at := (extract(epoch from clock_timestamp()) * 1000)::bigint;
  return new;
end $$;

create table if not exists public.commitments (
  id text primary key,
  user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  updated_at bigint not null,
  deleted bigint not null default 0,
  name text not null,
  type text not null,
  color bigint not null,
  sort_order bigint not null,
  show_in_widget bigint not null,
  archived bigint not null
);
alter table public.commitments enable row level security;
create policy "commitments: own rows" on public.commitments for all to authenticated
  using (user_id = auth.uid()) with check (user_id = auth.uid());
create trigger commitments_row_meta before insert or update on public.commitments
  for each row execute function public.commit_row_meta();
create index if not exists idx_commitments_user_id_updated_at on public.commitments (user_id, updated_at);

create table if not exists public.todos (
  id text primary key,
  user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  updated_at bigint not null,
  deleted bigint not null default 0,
  commitment_id text not null,
  title text not null,
  due_at bigint,
  has_time bigint not null,
  done_at bigint,
  recur_interval_days bigint,
  last_done_at bigint
);
alter table public.todos enable row level security;
create policy "todos: own rows" on public.todos for all to authenticated
  using (user_id = auth.uid()) with check (user_id = auth.uid());
create trigger todos_row_meta before insert or update on public.todos
  for each row execute function public.commit_row_meta();
create index if not exists idx_todos_user_id_updated_at on public.todos (user_id, updated_at);
create index if not exists idx_todos_user_id_done_at on public.todos (user_id, done_at);
create index if not exists idx_todos_commitment_id_due_at on public.todos (commitment_id, due_at);

create table if not exists public.completions (
  id text primary key,
  user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  updated_at bigint not null,
  deleted bigint not null default 0,
  todo_id text not null,
  done_at bigint not null
);
alter table public.completions enable row level security;
create policy "completions: own rows" on public.completions for all to authenticated
  using (user_id = auth.uid()) with check (user_id = auth.uid());
create trigger completions_row_meta before insert or update on public.completions
  for each row execute function public.commit_row_meta();
create index if not exists idx_completions_user_id_updated_at on public.completions (user_id, updated_at);
create index if not exists idx_completions_user_id_done_at on public.completions (user_id, done_at);
create index if not exists idx_completions_todo_id on public.completions (todo_id);

create table if not exists public.routines (
  id text primary key,
  user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  updated_at bigint not null,
  deleted bigint not null default 0,
  commitment_id text not null,
  name text not null,
  rest_between_exercises_s bigint not null,
  sort_order bigint not null,
  planned_days bigint not null default 0
);
alter table public.routines enable row level security;
create policy "routines: own rows" on public.routines for all to authenticated
  using (user_id = auth.uid()) with check (user_id = auth.uid());
create trigger routines_row_meta before insert or update on public.routines
  for each row execute function public.commit_row_meta();
create index if not exists idx_routines_user_id_updated_at on public.routines (user_id, updated_at);
create index if not exists idx_routines_commitment_id on public.routines (commitment_id);

create table if not exists public.exercises (
  id text primary key,
  user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  updated_at bigint not null,
  deleted bigint not null default 0,
  routine_id text not null,
  name text not null,
  sets bigint not null,
  reps bigint not null,
  weight double precision not null,
  rest_seconds bigint not null,
  duration_seconds bigint,
  sort_order bigint not null
);
alter table public.exercises enable row level security;
create policy "exercises: own rows" on public.exercises for all to authenticated
  using (user_id = auth.uid()) with check (user_id = auth.uid());
create trigger exercises_row_meta before insert or update on public.exercises
  for each row execute function public.commit_row_meta();
create index if not exists idx_exercises_user_id_updated_at on public.exercises (user_id, updated_at);
create index if not exists idx_exercises_routine_id on public.exercises (routine_id);

create table if not exists public.workout_sessions (
  id text primary key,
  user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  updated_at bigint not null,
  deleted bigint not null default 0,
  routine_id text not null,
  started_at bigint not null,
  ended_at bigint,
  notes text
);
alter table public.workout_sessions enable row level security;
create policy "workout_sessions: own rows" on public.workout_sessions for all to authenticated
  using (user_id = auth.uid()) with check (user_id = auth.uid());
create trigger workout_sessions_row_meta before insert or update on public.workout_sessions
  for each row execute function public.commit_row_meta();
create index if not exists idx_workout_sessions_user_id_updated_at on public.workout_sessions (user_id, updated_at);
create index if not exists idx_workout_sessions_routine_id on public.workout_sessions (routine_id);

create table if not exists public.workout_sets (
  id text primary key,
  user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  updated_at bigint not null,
  deleted bigint not null default 0,
  session_id text not null,
  exercise_id text not null,
  set_no bigint not null,
  reps bigint not null,
  weight double precision not null,
  done_at bigint,
  skipped bigint not null
);
alter table public.workout_sets enable row level security;
create policy "workout_sets: own rows" on public.workout_sets for all to authenticated
  using (user_id = auth.uid()) with check (user_id = auth.uid());
create trigger workout_sets_row_meta before insert or update on public.workout_sets
  for each row execute function public.commit_row_meta();
create index if not exists idx_workout_sets_user_id_updated_at on public.workout_sets (user_id, updated_at);
create index if not exists idx_workout_sets_user_id_done_at on public.workout_sets (user_id, done_at);
create index if not exists idx_workout_sets_session_id on public.workout_sets (session_id);
create index if not exists idx_workout_sets_exercise_id on public.workout_sets (exercise_id);

create table if not exists public.themes (
  id text primary key,
  user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  updated_at bigint not null,
  deleted bigint not null default 0,
  name text not null,
  json text not null
);
alter table public.themes enable row level security;
create policy "themes: own rows" on public.themes for all to authenticated
  using (user_id = auth.uid()) with check (user_id = auth.uid());
create trigger themes_row_meta before insert or update on public.themes
  for each row execute function public.commit_row_meta();
create index if not exists idx_themes_user_id_updated_at on public.themes (user_id, updated_at);

create table if not exists public.settings (
  id text primary key,
  user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  updated_at bigint not null,
  deleted bigint not null default 0,
  morning_recap_time bigint not null,
  night_recap_time bigint not null,
  morning_recap_enabled bigint not null,
  night_recap_enabled bigint not null,
  active_theme_id text
);
alter table public.settings enable row level security;
create policy "settings: own rows" on public.settings for all to authenticated
  using (user_id = auth.uid()) with check (user_id = auth.uid());
create trigger settings_row_meta before insert or update on public.settings
  for each row execute function public.commit_row_meta();
create index if not exists idx_settings_user_id_updated_at on public.settings (user_id, updated_at);

-- FCM tokens of the user's devices, used by the notify-sync Edge Function (PRD sync rule 7).
create table if not exists public.device_tokens (
  token text primary key,
  user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  platform text not null default 'android',
  updated_at bigint not null default 0
);
alter table public.device_tokens enable row level security;
create policy "device_tokens: own rows" on public.device_tokens for all to authenticated
  using (user_id = auth.uid()) with check (user_id = auth.uid());
create trigger device_tokens_row_meta before insert or update on public.device_tokens
  for each row execute function public.commit_row_meta();
create index if not exists idx_device_tokens_user on public.device_tokens (user_id);
