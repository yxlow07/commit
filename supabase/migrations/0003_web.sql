-- M8 web app: live updates and ticking todos with the same rules as the phone.
-- Safe to run more than once.

-- 1. Realtime: the web app listens for changes on these tables (RLS still applies to the events).
do $$
declare t text;
begin
  foreach t in array array['commitments', 'todos', 'completions', 'routines', 'exercises',
                           'workout_sessions', 'workout_sets', 'themes', 'settings']
  loop
    if not exists (
      select 1 from pg_publication_tables
      where pubname = 'supabase_realtime' and schemaname = 'public' and tablename = t
    ) then
      execute format('alter publication supabase_realtime add table public.%I', t);
    end if;
  end loop;
end $$;

-- 2. Tick / untick a todo in one transaction, mirroring TodoRepository.setDone on the phone:
--    every completion is logged in `completions`; one-off todos set done_at; recurring todos
--    (recur_interval_days > 0) set last_done_at, and unticking removes the latest completion and
--    recomputes last_done_at from the ones left. Whether a recurring todo currently counts as done
--    depends on the user's local date, so the caller decides `done` (the web app checks first).
--    security invoker: row-level security applies, so users can only touch their own rows.
create or replace function public.toggle_todo(p_todo_id text, p_done boolean)
returns void
language plpgsql security invoker set search_path = public as $$
declare
  v_recurring boolean;
  v_now bigint := (extract(epoch from clock_timestamp()) * 1000)::bigint;
  v_latest text;
begin
  select coalesce(recur_interval_days, 0) > 0 into v_recurring
  from todos where id = p_todo_id and deleted = 0;
  if not found then
    return;
  end if;

  if p_done then
    insert into completions (id, todo_id, done_at, deleted, updated_at)
    values (gen_random_uuid()::text, p_todo_id, v_now, 0, v_now);
    if v_recurring then
      update todos set last_done_at = v_now where id = p_todo_id;
    else
      update todos set done_at = v_now where id = p_todo_id;
    end if;
  else
    select id into v_latest from completions
    where todo_id = p_todo_id and deleted = 0
    order by done_at desc limit 1;
    if v_latest is not null then
      update completions set deleted = 1 where id = v_latest;
    end if;
    if v_recurring then
      update todos set last_done_at = (
        select max(done_at) from completions where todo_id = p_todo_id and deleted = 0
      ) where id = p_todo_id;
    else
      update todos set done_at = null where id = p_todo_id;
    end if;
  end if;
end $$;

grant execute on function public.toggle_todo(text, boolean) to authenticated;
