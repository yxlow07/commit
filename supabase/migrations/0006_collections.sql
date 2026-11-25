-- Migration 0006: collections, a third commitment type (commitments.type = 'collection').
-- A collection holds groups; groups hold items (a name, a photo, or both) with a collected flag.
-- Mirrors Room v7 (collection_groups, collection_items) minus the phone-only `dirty` and `photo_dirty`.
-- Photos live in the private Storage bucket `collection-photos` under `{user_id}/`.
-- Safe to run more than once. After running it, add the two tables to the notify-sync webhooks
-- (Database -> Webhooks, see app/SYNC_SETUP.md) so the phone hears about edits made on the web.

create table if not exists public.collection_groups (
  id text primary key,
  user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  updated_at bigint not null,
  deleted bigint not null default 0,
  commitment_id text not null,
  name text not null,
  sort_order bigint not null,
  sort_mode text not null default 'manual'
);
alter table public.collection_groups enable row level security;
drop policy if exists "collection_groups: own rows" on public.collection_groups;
create policy "collection_groups: own rows" on public.collection_groups for all to authenticated
  using (user_id = auth.uid()) with check (user_id = auth.uid());
drop trigger if exists collection_groups_row_meta on public.collection_groups;
create trigger collection_groups_row_meta before insert or update on public.collection_groups
  for each row execute function public.commit_row_meta();
create index if not exists idx_collection_groups_user_id_updated_at on public.collection_groups (user_id, updated_at);

create table if not exists public.collection_items (
  id text primary key,
  user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  updated_at bigint not null,
  deleted bigint not null default 0,
  group_id text not null,
  name text,
  photo_path text,
  collected bigint not null default 0,
  sort_order bigint not null,
  created_at bigint not null
);
alter table public.collection_items enable row level security;
drop policy if exists "collection_items: own rows" on public.collection_items;
create policy "collection_items: own rows" on public.collection_items for all to authenticated
  using (user_id = auth.uid()) with check (user_id = auth.uid());
drop trigger if exists collection_items_row_meta on public.collection_items;
create trigger collection_items_row_meta before insert or update on public.collection_items
  for each row execute function public.commit_row_meta();
create index if not exists idx_collection_items_user_id_updated_at on public.collection_items (user_id, updated_at);

-- Realtime, so the web app updates live (RLS still applies to the events).
do $$
declare t text;
begin
  foreach t in array array['collection_groups', 'collection_items']
  loop
    if not exists (
      select 1 from pg_publication_tables
      where pubname = 'supabase_realtime' and schemaname = 'public' and tablename = t
    ) then
      execute format('alter publication supabase_realtime add table public.%I', t);
    end if;
  end loop;
end $$;

-- Photos: a private bucket; each user can only touch objects under their own `{user_id}/` folder.
insert into storage.buckets (id, name, public)
values ('collection-photos', 'collection-photos', false)
on conflict (id) do nothing;

drop policy if exists "collection-photos: own read" on storage.objects;
create policy "collection-photos: own read" on storage.objects for select to authenticated
  using (bucket_id = 'collection-photos' and (storage.foldername(name))[1] = auth.uid()::text);
drop policy if exists "collection-photos: own insert" on storage.objects;
create policy "collection-photos: own insert" on storage.objects for insert to authenticated
  with check (bucket_id = 'collection-photos' and (storage.foldername(name))[1] = auth.uid()::text);
drop policy if exists "collection-photos: own update" on storage.objects;
create policy "collection-photos: own update" on storage.objects for update to authenticated
  using (bucket_id = 'collection-photos' and (storage.foldername(name))[1] = auth.uid()::text);
drop policy if exists "collection-photos: own delete" on storage.objects;
create policy "collection-photos: own delete" on storage.objects for delete to authenticated
  using (bucket_id = 'collection-photos' and (storage.foldername(name))[1] = auth.uid()::text);
