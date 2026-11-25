"use client";

import { useEffect, useMemo, useState, useSyncExternalStore } from "react";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { useQueryClient } from "@tanstack/react-query";
import { useTable } from "@/lib/data/store";
import { SORTS, createGroup, deleteGroup, deleteItem, restoreItem, saveItem, sortItems, toggleItem, updateGroup, usePhotoUrls } from "@/lib/data/collections";
import { cssColor } from "@/lib/domain/format";
import type { CollectionGroup, CollectionItem, CollectionSort, Commitment } from "@/lib/domain/types";
import { usePageTitle, useUser } from "@/components/AppShell";
import { CommitmentDialog } from "@/components/CommitmentDialog";
import { useCommitments } from "@/components/Nav";
import { OFFLINE_REASON, useOnline } from "@/components/Online";
import { useToast } from "@/components/Toast";
import { BackLink, Button, Dialog, EmptyText, ErrorText, Field, FieldGroup, IconButton, IconTile, Icons, Loading, PageTitle, Segmented, Switch, TextField, useAction } from "@/components/ui";

/**
 * Folded groups: a per-browser view preference (like the phone's), kept in localStorage with an
 * in-memory fallback for when storage is blocked. useSyncExternalStore keeps every reader in step.
 */
const FOLD_KEY = "commit.collection.folded";
const foldListeners = new Set<() => void>();
let foldMemory = "[]";
function readFolded(): string {
  try {
    return localStorage.getItem(FOLD_KEY) ?? foldMemory;
  } catch {
    return foldMemory;
  }
}
function useFolded() {
  const raw = useSyncExternalStore(
    (cb) => { foldListeners.add(cb); return () => { foldListeners.delete(cb); }; },
    readFolded,
    () => "[]",
  );
  const folded = useMemo(() => {
    try {
      return new Set<string>(JSON.parse(raw));
    } catch {
      return new Set<string>();
    }
  }, [raw]);
  const setFolded = (ids: Set<string>) => {
    foldMemory = JSON.stringify([...ids]);
    try {
      localStorage.setItem(FOLD_KEY, foldMemory);
    } catch {
      // Storage blocked: the in-memory copy still works for this visit.
    }
    foldListeners.forEach((l) => l());
  };
  return [folded, setFolded] as const;
}

/**
 * A collection (commitment type "collection"), as on the phone: how much is collected, then each
 * group as a grid of cards. Click a card to mark it collected or missing; the pencil edits it.
 * Missing items are faded so the gaps stand out. Each group has its own sort.
 */
export default function CollectionPage() {
  const id = useSearchParams().get("id");
  const commitmentsQ = useTable("commitments");
  const groupsQ = useTable("collection_groups");
  const itemsQ = useTable("collection_items");
  const commitments = useCommitments();
  const commitment = commitmentsQ.data?.find((c) => c.id === id);
  usePageTitle(commitment?.name ?? "Collection");
  const [editingCommitment, setEditingCommitment] = useState(false);
  const [groupDialog, setGroupDialog] = useState<CollectionGroup | "new" | null>(null);
  const [itemDialog, setItemDialog] = useState<{ item: CollectionItem | null; groupId: string } | null>(null);
  const [folded, setFolded] = useFolded();

  const groups = useMemo(
    () => (groupsQ.data ?? []).filter((g) => g.commitment_id === id).sort((a, b) => a.sort_order - b.sort_order || a.name.localeCompare(b.name)),
    [groupsQ.data, id],
  );
  const groupIds = new Set(groups.map((g) => g.id));
  const items = (itemsQ.data ?? []).filter((i) => groupIds.has(i.group_id));
  const photos = usePhotoUrls(items.map((i) => i.photo_path).filter((p): p is string => !!p));

  if (commitmentsQ.isPending || groupsQ.isPending || itemsQ.isPending) return <Loading />;
  if (!commitment) return <EmptyText>This collection doesn&apos;t exist any more. <Link href="/" className="font-bold hover:underline">Back to Groups</Link></EmptyText>;

  const collected = items.filter((i) => i.collected).length;
  const allFolded = groups.length > 0 && groups.every((g) => folded.has(g.id));
  const toggleAll = () => {
    const next = new Set(folded);
    groups.forEach((g) => (allFolded ? next.delete(g.id) : next.add(g.id)));
    setFolded(next);
  };
  const toggleFold = (groupId: string, fold: boolean) => {
    const next = new Set(folded);
    if (fold) next.add(groupId); else next.delete(groupId);
    setFolded(next);
  };
  const accent = cssColor(commitment.color);

  return (
    <div className="flex flex-col gap-10">
      <div className="flex flex-col gap-4">
        <PageTitle title={commitment.name} before={<BackLink href="/" label="Groups" />}
          summary={items.length === 0 ? "Nothing in it yet." : `${collected} of ${items.length} collected.`}>
          <IconButton label={`Edit ${commitment.name}`} onClick={() => setEditingCommitment(true)}>{Icons.pencil}</IconButton>
        </PageTitle>
        {items.length > 0 && (
          <div className="h-1.5 w-full overflow-hidden rounded-full bg-track" role="progressbar" aria-valuemin={0} aria-valuemax={items.length} aria-valuenow={collected} aria-label="Collected">
            <div className="h-full rounded-full transition-[width] duration-300" style={{ width: `${(collected / items.length) * 100}%`, background: accent }} />
          </div>
        )}
      </div>

      {groups.length > 1 && (
        <button type="button" onClick={toggleAll}
          className="-mx-3 -mb-6 self-start cursor-pointer rounded-xl px-3 py-2 text-body text-muted hover:bg-hover hover:text-ink">
          {allFolded ? "Expand all" : "Collapse all"}
        </button>
      )}

      {groups.map((g, i) => (
        <GroupSection key={g.id} group={g} afterFolded={i > 0 && folded.has(groups[i - 1].id)} items={items.filter((i) => i.group_id === g.id)} photos={photos.data}
          folded={folded.has(g.id)} onFold={(fold) => toggleFold(g.id, fold)}
          onAdd={() => { toggleFold(g.id, false); setItemDialog({ item: null, groupId: g.id }); }}
          onEditGroup={() => setGroupDialog(g)}
          onEditItem={(item) => setItemDialog({ item, groupId: item.group_id })} />
      ))}

      <button type="button" onClick={() => setGroupDialog("new")}
        className="-mx-3 flex min-h-14 w-[calc(100%+1.5rem)] cursor-pointer items-center gap-3 rounded-xl px-3 text-left text-body-l text-muted hover:bg-hover hover:text-ink">
        {Icons.plus} New group
      </button>

      <CommitmentDialog open={editingCommitment} onClose={() => setEditingCommitment(false)} commitment={commitment}
        nextSortOrder={commitments.length ? Math.max(...commitments.map((c) => c.sort_order)) + 1 : 0} />
      <GroupDialog open={groupDialog != null} group={groupDialog === "new" ? null : groupDialog} commitment={commitment} groups={groups} items={items}
        onClose={() => setGroupDialog(null)} />
      <ItemDialog open={itemDialog != null} item={itemDialog?.item ?? null} groupId={itemDialog?.groupId ?? ""} groups={groups} items={items}
        photoUrl={itemDialog?.item?.photo_path ? photos.data?.get(itemDialog.item.photo_path) : undefined}
        onClose={() => setItemDialog(null)} />
    </div>
  );
}

function GroupSection({ group, afterFolded, items, photos, folded, onFold, onAdd, onEditGroup, onEditItem }: {
  group: CollectionGroup; afterFolded: boolean; items: CollectionItem[]; photos?: Map<string, string>; folded: boolean; onFold: (fold: boolean) => void;
  onAdd: () => void; onEditGroup: () => void; onEditItem: (item: CollectionItem) => void;
}) {
  const qc = useQueryClient();
  const toast = useToast();
  const sorted = sortItems(items, group.sort_mode);
  const collected = items.filter((i) => i.collected).length;
  const fail = () => toast({ message: "Couldn't save. Check your connection." });
  return (
    <section className={`flex flex-col gap-3 ${afterFolded ? "-mt-8" : ""}`} aria-label={group.name}>
      <div className="flex min-h-11 items-center gap-2">
        <h2 className="min-w-0 flex-1">
          <button type="button" onClick={() => onFold(!folded)} aria-expanded={!folded} aria-controls={`items-${group.id}`}
            className="-ml-3 flex min-h-11 max-w-full cursor-pointer items-center gap-2 rounded-xl px-3 text-left hover:bg-hover">
            <span className={`shrink-0 text-muted transition-transform duration-200 motion-reduce:transition-none ${folded ? "" : "rotate-90"}`} aria-hidden>{Icons.chevron}</span>
            <span className="min-w-0 truncate text-title-section">{group.name}</span>
            <span className="shrink-0 text-meta font-normal tabular-nums text-muted">{collected}/{items.length}</span>
          </button>
        </h2>
        <label className="sr-only" htmlFor={`sort-${group.id}`}>Sort {group.name}</label>
        <select id={`sort-${group.id}`} value={group.sort_mode}
          onChange={(e) => updateGroup(qc, group.id, { sort_mode: e.target.value as CollectionSort }).catch(fail)}
          className="h-10 cursor-pointer rounded-xl bg-transparent px-2 text-body text-muted hover:bg-hover hover:text-ink">
          {SORTS.map((s) => <option key={s.id} value={s.id}>{s.label}</option>)}
        </select>
        <IconButton label={`Edit group ${group.name}`} onClick={onEditGroup}>{Icons.pencil}</IconButton>
        <IconButton label={`Add item to ${group.name}`} className="-mr-3" onClick={onAdd}>{Icons.plus}</IconButton>
      </div>
      {folded ? null : sorted.length === 0 ? <p id={`items-${group.id}`} className="text-body text-muted">Nothing here yet. Add an item with +.</p> : (
        <ul id={`items-${group.id}`} className="grid grid-cols-2 gap-3 sm:grid-cols-3 md:grid-cols-4">
          {sorted.map((item) => (
            <ItemCard key={item.id} item={item} url={item.photo_path ? photos?.get(item.photo_path) : undefined}
              onToggle={() => toggleItem(qc, item).catch(fail)} onEdit={() => onEditItem(item)} />
          ))}
        </ul>
      )}
    </section>
  );
}

/** A square photo (or the name), a round check in the corner, the name below a photo; missing items faded. */
function ItemCard({ item, url, onToggle, onEdit }: { item: CollectionItem; url?: string; onToggle: () => void; onEdit: () => void }) {
  const label = item.name ?? "Item";
  const on = item.collected === 1;
  return (
    <li className="group relative flex flex-col gap-1.5">
      <button type="button" role="checkbox" aria-checked={on} aria-label={label} onClick={onToggle}
        className="relative aspect-square w-full cursor-pointer overflow-hidden rounded-2xl bg-fill text-left">
        <span className={`absolute inset-0 flex items-center justify-center transition-opacity ${on ? "opacity-100" : "opacity-45"}`}>
          {url ? (
            // eslint-disable-next-line @next/next/no-img-element -- signed Storage URLs, no image optimiser in a static export
            <img src={url} alt="" className="size-full object-contain" loading="lazy" />
          ) : (
            <span className="line-clamp-3 px-3 text-center text-body text-text-strong">{item.photo_path ? "…" : label}</span>
          )}
        </span>
        <span aria-hidden className={`absolute right-2 top-2 flex size-7 items-center justify-center rounded-full ${on ? "bg-ink text-on-ink" : "border-[1.5px] border-ink bg-surface/85"}`}>
          {on && <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round"><path d="m5 12.5 4.5 4.5L19 7.5" /></svg>}
        </span>
      </button>
      <button type="button" onClick={onEdit} aria-label={`Edit ${label}`} title={`Edit ${label}`}
        className="absolute left-2 top-2 flex size-8 cursor-pointer items-center justify-center rounded-full bg-surface/85 text-ink opacity-0 focus-visible:opacity-100 group-hover:opacity-100 [@media(hover:none)]:opacity-100">
        {Icons.pencil}
      </button>
      {item.name && item.photo_path && <span className={`line-clamp-2 px-0.5 text-meta ${on ? "text-ink" : "text-muted"}`}>{item.name}</span>}
    </li>
  );
}

/** New group, or edit one: rename, move up or down, delete. */
function GroupDialog(props: {
  open: boolean; group: CollectionGroup | null; commitment: Commitment; groups: CollectionGroup[]; items: CollectionItem[]; onClose: () => void;
}) {
  // The form mounts only while the dialog is open, so its state starts fresh each time.
  return <Dialog open={props.open} onClose={props.onClose} title={props.group ? "Edit group" : "New group"}><GroupForm {...props} /></Dialog>;
}

function GroupForm({ group, commitment, groups, items, onClose }: {
  group: CollectionGroup | null; commitment: Commitment; groups: CollectionGroup[]; items: CollectionItem[]; onClose: () => void;
}) {
  const qc = useQueryClient();
  const online = useOnline();
  const action = useAction();
  // The dialog only renders its content while open, so this state starts fresh on every open.
  const [name, setName] = useState(group?.name ?? "");
  const index = group ? groups.findIndex((g) => g.id === group.id) : -1;

  async function move(delta: number) {
    const ids = groups.map((g) => g.id);
    const j = index + delta;
    if (index < 0 || j < 0 || j >= ids.length) return;
    ids.splice(j, 0, ids.splice(index, 1)[0]);
    await action.run(() => Promise.all(ids.map((gid, i) => (groups.find((g) => g.id === gid)!.sort_order === i ? null : updateGroup(qc, gid, { sort_order: i })))));
    onClose();
  }

  return (
      <form className="flex flex-col gap-5" onSubmit={async (e) => {
        e.preventDefault();
        const ok = await action.run(() => group
          ? updateGroup(qc, group.id, { name: name.trim() })
          : createGroup(qc, commitment.id, name, groups.length ? Math.max(...groups.map((g) => g.sort_order)) + 1 : 0));
        if (ok) onClose();
      }}>
        <Field label="Name" htmlFor="group-name">
          <TextField id="group-name" autoFocus value={name} onChange={(e) => setName(e.target.value)} placeholder="Group name" />
        </Field>
        {group && groups.length > 1 && (
          <div className="flex gap-2">
            <Button variant="secondary" disabled={index <= 0} onClick={() => move(-1)}>Move up</Button>
            <Button variant="secondary" disabled={index >= groups.length - 1} onClick={() => move(1)}>Move down</Button>
          </div>
        )}
        <ErrorText error={action.error} />
        <div className="flex items-center gap-2">
          {group && <IconTile label={`Delete ${group.name}`} disabled={!online || action.busy} onClick={async () => { if (await action.run(() => deleteGroup(qc, group, items))) onClose(); }}>{Icons.trash}</IconTile>}
          <span className="flex-1" />
          <Button variant="quiet" onClick={onClose}>Cancel</Button>
          <Button variant="primary" type="submit" disabled={!name.trim() || action.busy || !online} title={online ? undefined : OFFLINE_REASON}>{group ? "Save" : "Add group"}</Button>
        </div>
      </form>
  );
}

/** Add or edit an item: name, photo, group, collected; delete (with undo) when editing. */
function ItemDialog(props: {
  open: boolean; item: CollectionItem | null; groupId: string; groups: CollectionGroup[]; items: CollectionItem[]; photoUrl?: string; onClose: () => void;
}) {
  return <Dialog open={props.open} onClose={props.onClose} title={props.item ? "Edit item" : "New item"}><ItemForm {...props} /></Dialog>;
}

function ItemForm({ item, groupId, groups, items, photoUrl, onClose }: {
  item: CollectionItem | null; groupId: string; groups: CollectionGroup[]; items: CollectionItem[]; photoUrl?: string; onClose: () => void;
}) {
  const qc = useQueryClient();
  const user = useUser();
  const online = useOnline();
  const toast = useToast();
  const action = useAction();
  const [name, setName] = useState(item?.name ?? "");
  const [group, setGroup] = useState(groupId);
  const [collected, setCollected] = useState(item?.collected === 1);
  const [photo, setPhoto] = useState<File | null>(null);
  const [removed, setRemoved] = useState(false);
  const [preview, setPreview] = useState<string | null>(null);
  useEffect(() => () => { if (preview) URL.revokeObjectURL(preview); }, [preview]);

  const shown = preview ?? (removed ? null : photoUrl ?? null);
  const hasPhoto = !!photo || (!!item?.photo_path && !removed);
  const nextOrder = (gid: string) => Math.max(-1, ...items.filter((i) => i.group_id === gid).map((i) => i.sort_order)) + 1;

  return (
      <form className="flex flex-col gap-5" onSubmit={async (e) => {
        e.preventDefault();
        const ok = await action.run(() => saveItem(qc, user.id, item, { name, groupId: group, collected, photo, removePhoto: removed }, nextOrder(group)));
        if (ok) onClose();
      }}>
        <Field label="Name" htmlFor="item-name">
          <TextField id="item-name" autoFocus value={name} onChange={(e) => setName(e.target.value)} placeholder="Optional with a photo" />
        </Field>
        <FieldGroup label="Photo">
          {shown && (
            // eslint-disable-next-line @next/next/no-img-element -- object URL or signed Storage URL
            <img src={shown} alt="" className="size-32 rounded-2xl bg-fill object-cover" />
          )}
          <div className="flex flex-wrap items-center gap-2">
            <label className="inline-flex h-12 cursor-pointer items-center rounded-xl bg-fill px-6 text-body font-medium text-ink hover:bg-hover">
              {hasPhoto ? "Change photo" : "Add photo"}
              <input type="file" accept="image/*" className="sr-only" onChange={(e) => {
                const f = e.target.files?.[0];
                if (f) { setPhoto(f); setRemoved(false); setPreview(URL.createObjectURL(f)); }
                e.target.value = "";
              }} />
            </label>
            {hasPhoto && <Button variant="quiet" onClick={() => { setPhoto(null); setPreview(null); setRemoved(true); }}>Remove</Button>}
          </div>
        </FieldGroup>
        {groups.length > 1 && (
          <FieldGroup label="Group">
            <Segmented scroll label="Group" value={group} onChange={setGroup} items={groups.map((g) => ({ id: g.id, label: g.name }))} />
          </FieldGroup>
        )}
        <div className="flex items-center justify-between gap-3">
          <span className="text-body-l">Collected</span>
          <Switch label="Collected" checked={collected} onChange={setCollected} />
        </div>
        <ErrorText error={action.error} />
        <div className="flex items-center gap-2">
          {item && (
            <IconTile label={`Delete ${item.name ?? "item"}`} disabled={!online || action.busy} onClick={async () => {
              if (await action.run(() => deleteItem(qc, item.id))) {
                onClose();
                toast({ message: `${item.name ?? "Item"} deleted`, action: { label: "Undo", run: () => restoreItem(qc, item.id) } });
              }
            }}>{Icons.trash}</IconTile>
          )}
          <span className="flex-1" />
          <Button variant="quiet" onClick={onClose}>Cancel</Button>
          <Button variant="primary" type="submit" title={online ? undefined : OFFLINE_REASON}
            disabled={(!name.trim() && !hasPhoto) || action.busy || !online}>
            {action.busy ? "Saving…" : item ? "Save" : "Add item"}
          </Button>
        </div>
      </form>
  );
}
