-- Migration 0005: toggle_todo treats weekday-recurring todos (recur_days, M10) as recurring.
-- Before this, ticking a weekday chore on the web set done_at as if it were a one-off todo, so
-- the chore disappeared from the phone's lists for good. Run after 0004.

create or replace function public.toggle_todo(p_todo_id text, p_done boolean)
returns void
language plpgsql security invoker set search_path = public as $$
declare
  v_recurring boolean;
  v_now bigint := (extract(epoch from clock_timestamp()) * 1000)::bigint;
  v_latest text;
begin
  select coalesce(recur_interval_days, 0) > 0 or coalesce(recur_days, 0) > 0 into v_recurring
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

-- Repair weekday chores already ticked on the web before this fix: they got done_at instead of
-- last_done_at. Move it across (keeping the latest completion as last_done_at).
update todos t set
  last_done_at = coalesce(t.last_done_at, t.done_at),
  done_at = null
where coalesce(t.recur_days, 0) > 0 and t.done_at is not null and t.deleted = 0;
