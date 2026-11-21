-- Fix: editing rows in the Table editor failed with "null value in column user_id".
-- The trigger always set user_id := auth.uid(), which is null outside the app (Table editor,
-- SQL editor, service role). Now the owner is set on insert and never changed by an update.
-- Safe to run more than once. (0001_init.sql already contains this version for new projects.)
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
