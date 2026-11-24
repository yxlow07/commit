"use client";

import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { useQueryClient } from "@tanstack/react-query";
import { useTable } from "@/lib/data/store";
import { createTodo, deleteTodo, restoreRow, updateTodo, type TodoInput } from "@/lib/data/mutations";
import { startOfDay } from "@/lib/domain/dates";
import type { Commitment, Todo } from "@/lib/domain/types";
import { DateButton, RepeatField, TimeButton, repeatFrom, type RepeatValue } from "./Pickers";
import { OFFLINE_REASON, useOnline } from "./Online";
import { useToast } from "./Toast";
import { Button, Dialog, FieldGroup, IconButton, IconTile, Icons, Segmented, TextField, useNow } from "./ui";

/**
 * The edit panel (web spec, "Edit panel"): edits one todo with every field it can have. Its state
 * lives in the URL (?todo=<id> or ?todo=new), so reload and the back button work on every page.
 * Edits save with Save or Ctrl/Cmd+Enter, never as you type; leaving with unsaved changes asks once.
 */

interface PanelApi {
  /** The todo id in the URL, "new", or null. */
  current: string | null;
  open: (id: string) => void;
  openNew: (commitmentId?: string) => void;
  close: () => void;
}

const PanelContext = createContext<PanelApi>({ current: null, open: () => {}, openNew: () => {}, close: () => {} });
export const usePanel = () => useContext(PanelContext);

const LAST_COMMITMENT = "commit-last-commitment";
export function lastCommitment(): string | null {
  try { return localStorage.getItem(LAST_COMMITMENT); } catch { return null; }
}
export function rememberCommitment(id: string) {
  try { localStorage.setItem(LAST_COMMITMENT, id); } catch { /* private mode */ }
}

export function PanelProvider({ children }: { children: ReactNode }) {
  const router = useRouter();
  const pathname = usePathname();
  const params = useSearchParams();
  const current = params.get("todo");
  const dirty = useRef<() => boolean>(() => false);
  const pushed = useRef(false);
  const [pending, setPending] = useState<(() => void) | null>(null);

  const url = useCallback((todo: string | null, extra?: Record<string, string>) => {
    const next = new URLSearchParams(params.toString());
    next.delete("todo");
    next.delete("for");
    if (todo) next.set("todo", todo);
    for (const [k, v] of Object.entries(extra ?? {})) next.set(k, v);
    const q = next.toString();
    return q ? `${pathname}?${q}` : pathname;
  }, [params, pathname]);

  const guard = useCallback((go: () => void) => {
    if (dirty.current()) setPending(() => go);
    else go();
  }, []);

  const api = useMemo<PanelApi>(() => ({
    current,
    open: (id) => {
      if (id === current) return;
      guard(() => {
        if (current) router.replace(url(id), { scroll: false });
        else { pushed.current = true; router.push(url(id), { scroll: false }); }
      });
    },
    openNew: (commitmentId) => guard(() => {
      const target = url("new", commitmentId ? { for: commitmentId } : undefined);
      if (current) router.replace(target, { scroll: false });
      else { pushed.current = true; router.push(target, { scroll: false }); }
    }),
    close: () => guard(() => {
      const id = current;
      if (pushed.current) { pushed.current = false; router.back(); }
      else router.replace(url(null), { scroll: false });
      // Focus returns to the row the panel was opened from.
      if (id) setTimeout(() => document.querySelector<HTMLElement>(`[data-todo-row="${id}"]`)?.focus(), 60);
    }),
  }), [current, guard, router, url]);

  return (
    <PanelContext value={api}>
      <DirtyContext value={dirty}>{children}</DirtyContext>
      <Dialog open={pending != null} onClose={() => setPending(null)} title="Discard changes?">
        <p className="text-body text-text-strong">Your changes to this todo haven&apos;t been saved.</p>
        <div className="flex justify-end gap-2">
          <Button variant="text" onClick={() => setPending(null)}>Keep editing</Button>
          <Button variant="primary" onClick={() => { const go = pending; dirty.current = () => false; setPending(null); go?.(); }}>Discard</Button>
        </div>
      </Dialog>
    </PanelContext>
  );
}

const DirtyContext = createContext<React.RefObject<() => boolean>>({ current: () => false });

// ---------------- Draft ----------------

interface Draft {
  title: string;
  commitmentId: string;
  day: number | null;
  time: number | null;
  lead: number;
  repeat: RepeatValue;
}

function draftOf(todo: Todo): Draft {
  const due = todo.due_at;
  const d = due != null ? new Date(due) : null;
  return {
    title: todo.title,
    commitmentId: todo.commitment_id,
    day: due != null ? startOfDay(due) : null,
    time: d && todo.has_time ? d.getHours() * 60 + d.getMinutes() : null,
    lead: todo.reminder_lead_minutes ?? 0,
    repeat: repeatFrom(todo.recur_interval_days, todo.recur_days),
  };
}

function inputOf(d: Draft): TodoInput {
  const dueAt = d.day == null ? null : d.day + (d.time ?? 0) * 60_000;
  const hasTime = d.day != null && d.time != null;
  return {
    title: d.title,
    commitmentId: d.commitmentId,
    // Day + minutes through Date, so a DST change on that day still lands on the typed time.
    dueAt: dueAt == null ? null : (() => { const x = new Date(d.day!); x.setHours(0, d.time ?? 0, 0, 0); return x.getTime(); })(),
    hasTime,
    repeatDays: d.repeat.on && d.repeat.mode === "interval" ? d.repeat.intervalDays : null,
    recurDays: d.repeat.on && d.repeat.mode === "weekdays" ? d.repeat.weekdays : null,
    reminderLeadMinutes: hasTime ? d.lead : null,
  };
}

const same = (a: Draft, b: Draft) => JSON.stringify(a) === JSON.stringify(b);
const draftKey = (id: string) => `commit-draft:${id}`;

// ---------------- Panel ----------------

/** The panel itself; the app shell renders it once, beside whatever page is open. */
export function EditPanel() {
  const { current } = usePanel();
  const params = useSearchParams();
  if (!current) return null;
  return <PanelBody key={current} id={current} forCommitment={params.get("for")} />;
}

/** Unsaved edits and the version of the todo they started from. */
interface Edits {
  draft: Draft;
  base: Draft;
  baseUpdatedAt: number;
}

function PanelBody({ id, forCommitment }: { id: string; forCommitment: string | null }) {
  const isNew = id === "new";
  const qc = useQueryClient();
  const toast = useToast();
  const online = useOnline();
  const now = useNow();
  const panel = usePanel();
  const dirtyRef = useContext(DirtyContext);
  const todosQ = useTable("todos");
  const commitmentsQ = useTable("commitments");
  const commitments = useMemo(
    () => (commitmentsQ.data ?? []).filter((c) => !c.archived && c.type !== "workout").sort((a, b) => a.sort_order - b.sort_order),
    [commitmentsQ.data],
  );
  const todo = isNew ? undefined : todosQ.data?.find((t) => t.id === id);
  const loaded = !todosQ.isPending && !commitmentsQ.isPending;

  // A new todo starts from the commitment it was opened for, else the last one used.
  const [fresh] = useState<Draft>(() => ({
    title: "", commitmentId: "", day: null, time: null, lead: 0, repeat: repeatFrom(null, null),
  }));
  const freshCommitment = [forCommitment, lastCommitment()].find((c) => c && commitments.some((x) => x.id === c)) ?? commitments[0]?.id ?? "";

  // Untouched, the panel shows the live todo, so changes from another device arrive quietly.
  // Once edited, it keeps the edits and remembers which version they started from.
  const [edits, setEdits] = useState<Edits | null>(() => {
    try {
      const saved = JSON.parse(sessionStorage.getItem(draftKey(id)) ?? "null") as Edits | null;
      return saved && saved.draft && saved.base ? saved : null;
    } catch { return null; }
  });
  const live: Draft | null = todo ? draftOf(todo) : isNew ? { ...fresh, commitmentId: freshCommitment } : null;
  const draft = edits?.draft ?? live;
  const dirty = edits != null && !same(edits.draft, edits.base);
  const [saving, setSaving] = useState(false);
  const changedElsewhere = !!todo && !!edits && dirty && todo.updated_at !== edits.baseUpdatedAt && !saving;
  const deletedElsewhere = !isNew && loaded && !todo && !saving;

  useEffect(() => {
    dirtyRef.current = () => dirty && !saving;
    try {
      if (dirty) sessionStorage.setItem(draftKey(id), JSON.stringify(edits));
      else sessionStorage.removeItem(draftKey(id));
    } catch { /* storage unavailable */ }
    return () => { dirtyRef.current = () => false; };
  }, [dirty, edits, saving, id, dirtyRef]);

  // Focus the title when the panel opens; Esc closes it (unless a dialog or popover is on top).
  const titleRef = useRef<HTMLInputElement>(null);
  const hasDraft = draft != null;
  useEffect(() => { if (hasDraft) titleRef.current?.focus(); }, [hasDraft]);
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key !== "Escape" || e.defaultPrevented || document.querySelector("dialog[open]")) return;
      panel.close();
    };
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
  }, [panel]);

  const set = (changes: Partial<Draft>) => {
    if (!draft) return;
    setEdits((e) => e
      ? { ...e, draft: { ...e.draft, ...changes } }
      : { draft: { ...draft, ...changes }, base: draft, baseUpdatedAt: todo?.updated_at ?? 0 });
  };

  async function save() {
    if (!draft || !draft.title.trim() || !draft.commitmentId) return;
    setSaving(true);
    dirtyRef.current = () => false;
    try {
      if (isNew) await createTodo(qc, inputOf(draft));
      else await updateTodo(qc, id, inputOf(draft));
      rememberCommitment(draft.commitmentId);
      try { sessionStorage.removeItem(draftKey(id)); } catch { /* storage unavailable */ }
      panel.close();
    } catch {
      setSaving(false);
      toast({ message: "Couldn't save. Check your connection.", action: { label: "Try again", run: save } });
    }
  }

  async function remove() {
    if (!todo) return;
    setSaving(true);
    dirtyRef.current = () => false;
    panel.close();
    try {
      await deleteTodo(qc, todo.id);
      toast({ message: "Todo deleted", action: { label: "Undo", run: () => restoreRow(qc, "todos", todo.id) } });
    } catch {
      toast({ message: "Couldn't delete. Check your connection." });
    }
  }

  const title = isNew ? "New todo" : "Edit todo";

  return (
    <aside
      aria-label={title}
      className="fade-in fixed inset-0 z-40 flex flex-col bg-background sm:inset-x-0 sm:top-14 sm:z-30 sm:bg-surface md:left-auto md:top-0 md:w-[380px] md:border-l md:border-track"
      onKeyDown={(e) => {
        if (e.key === "Enter" && (e.ctrlKey || e.metaKey)) { e.preventDefault(); save(); }
      }}
    >
      <div className="flex h-16 shrink-0 items-center gap-2 px-4 sm:px-6">
        <IconButton label="Back" className="-ml-2 sm:hidden" onClick={panel.close}>{Icons.back}</IconButton>
        <h2 className="flex-1 text-title-section">{title}</h2>
        <IconButton label="Close" shortcut="Esc" className="max-sm:hidden" onClick={panel.close}>{Icons.close}</IconButton>
      </div>

      {!draft ? (
        <p className="px-6 text-meta text-muted">{deletedElsewhere ? "This todo was deleted on another device." : "Loading…"}</p>
      ) : (
        <div className="flex min-h-0 flex-1 flex-col gap-5 overflow-y-auto px-4 pb-6 pt-1 sm:px-6">
          <div className="flex flex-col gap-1.5">
            <label htmlFor="todo-title" className="text-caption text-muted">Title</label>
            <TextField id="todo-title" ref={titleRef} value={draft.title} placeholder="What needs doing?" onChange={(e) => set({ title: e.target.value })}
              onKeyDown={(e) => { if (e.key === "Enter" && !e.ctrlKey && !e.metaKey) { e.preventDefault(); save(); } }} />
          </div>

          {commitments.length > 1 && (
            <FieldGroup label="Commitment">
              <Segmented scroll label="Commitment" value={draft.commitmentId}
                items={commitments.map((c: Commitment) => ({ id: c.id, label: c.name }))}
                onChange={(commitmentId) => set({ commitmentId })} />
            </FieldGroup>
          )}

          <FieldGroup label="Due">
            <div className="flex flex-wrap items-center gap-2">
              <DateButton value={draft.day} now={now} onChange={(day) => set({ day })} />
              <TimeButton value={draft.time} now={now} onChange={(time) => set({ time, day: draft.day ?? startOfDay(now) })} />
              {draft.day != null && <Button variant="quiet" onClick={() => set({ day: null, time: null })}>No date</Button>}
            </div>
          </FieldGroup>

          {draft.day != null && draft.time != null && (
            <FieldGroup label="Reminder">
              <Segmented scroll label="Reminder" value={String(draft.lead)}
                items={[{ id: "0", label: "At due time" }, { id: "15", label: "15 min" }, { id: "30", label: "30 min" }, { id: "60", label: "1 hour" }]}
                onChange={(v) => set({ lead: Number(v) })} />
            </FieldGroup>
          )}

          <RepeatField value={draft.repeat} onChange={(repeat) => set({ repeat })} />

          {changedElsewhere && (
            <p role="status" aria-live="polite" className="rounded-xl bg-fill p-3 text-meta">
              Changed on another device.{" "}
              <button type="button" className="cursor-pointer font-bold underline-offset-2 hover:underline" onClick={() => setEdits(null)}>
                Load their version
              </button>
            </p>
          )}
          {deletedElsewhere && <p role="status" aria-live="polite" className="rounded-xl bg-fill p-3 text-meta">This todo was deleted on another device.</p>}
        </div>
      )}

      <div className="flex shrink-0 items-center gap-2 px-4 py-4 sm:px-6">
        {!isNew && todo && <IconTile label="Delete todo" disabled={!online} onClick={remove}>{Icons.trash}</IconTile>}
        <span className="flex-1" />
        <Button variant="quiet" onClick={panel.close}>{deletedElsewhere ? "Close" : "Cancel"}</Button>
        {!deletedElsewhere && (
          <Button variant="primary" title={!online ? OFFLINE_REASON : "Save todo (Ctrl+Enter)"}
            disabled={!draft || !draft.title.trim() || !draft.commitmentId || !online || saving} onClick={save}>
            Save todo
          </Button>
        )}
      </div>
    </aside>
  );
}
