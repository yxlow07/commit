"use client";

import { useEffect, useRef, useState, type CSSProperties, type KeyboardEvent, type ReactNode } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { createTodo, deleteTodo, restoreRow, setTodoDone } from "@/lib/data/mutations";
import { compareItems, type TodoItem } from "@/lib/domain/recurrence";
import { cssColor } from "@/lib/domain/format";
import type { Commitment } from "@/lib/domain/types";
import { lastCommitment, rememberCommitment, usePanel } from "./EditPanel";
import { OFFLINE_REASON, useOnline } from "./Online";
import { useToast } from "./Toast";
import { Icons, SectionHeading, TodoCheckbox, fieldClass } from "./ui";

/** `muted` tinted 35 % toward a commitment's colour (Today's second lines, as on the phone). */
export const tint = (color: number): CSSProperties => ({ color: `color-mix(in srgb, var(--muted) 65%, ${cssColor(color)})` });

// ---------------- Ticking ----------------

/**
 * Tick-then-fade (web spec, "Todo row on the web"): a ticked row stays in place, checked and struck
 * through, for 4 seconds or until the list is left, then fades out over 150 ms. Unticking before
 * then restores it. A tick that fails puts the checkbox back and shows a retry toast.
 */
export function useTicks() {
  const qc = useQueryClient();
  const toast = useToast();
  const [ticked, setTicked] = useState<Map<string, { item: TodoItem; fading: boolean }>>(new Map());
  const timers = useRef(new Map<string, ReturnType<typeof setTimeout>[]>());
  useEffect(() => {
    const all = timers.current;
    return () => all.forEach((ts) => ts.forEach(clearTimeout));
  }, []);

  const drop = (id: string) => {
    timers.current.get(id)?.forEach(clearTimeout);
    timers.current.delete(id);
    setTicked((m) => { const n = new Map(m); n.delete(id); return n; });
  };

  async function tick(item: TodoItem) {
    const id = item.todo.id;
    setTicked((m) => new Map(m).set(id, { item, fading: false }));
    timers.current.set(id, [
      setTimeout(() => setTicked((m) => (m.has(id) ? new Map(m).set(id, { item, fading: true }) : m)), 4000),
      setTimeout(() => drop(id), 4150),
    ]);
    try {
      await setTodoDone(qc, item.todo, true);
    } catch {
      drop(id);
      toast({ message: "Couldn't save. Check your connection.", action: { label: "Try again", run: () => tick(item) } });
    }
  }

  async function untick(item: TodoItem) {
    drop(item.todo.id);
    try {
      await setTodoDone(qc, item.todo, false);
    } catch {
      toast({ message: "Couldn't save. Check your connection." });
    }
  }

  /** Open items plus the ones ticked a moment ago, in widget order. */
  function merge(open: TodoItem[]): TodoItem[] {
    return [...open.filter((i) => !ticked.has(i.todo.id)), ...[...ticked.values()].map((t) => t.item)].sort(compareItems);
  }

  return { ticked, tick, untick, merge };
}

/** Untick from the Done tab: happens at once, with an undo toast. */
export function useUntickDone() {
  const qc = useQueryClient();
  const toast = useToast();
  return async (item: TodoItem) => {
    try {
      await setTodoDone(qc, item.todo, false);
      toast({ message: `${item.todo.title} marked not done`, action: { label: "Undo", run: () => setTodoDone(qc, { ...item.todo, done_at: null, last_done_at: null }, true) } });
    } catch {
      toast({ message: "Couldn't save. Check your connection." });
    }
  };
}

// ---------------- Row ----------------

export interface RowData {
  item: TodoItem;
  due: { text: string; late: boolean };
  second?: string;
  secondStyle?: CSSProperties;
  secondDanger?: boolean;
}

/** Moves keyboard focus between todo rows (↑ ↓ or J K). */
function focusRow(from: HTMLElement, delta: number) {
  const rows = [...document.querySelectorAll<HTMLElement>("[data-todo-row]")];
  const i = rows.indexOf(from);
  rows[Math.min(rows.length - 1, Math.max(0, i + delta))]?.focus();
}

/**
 * The todo row (App Design Spec "Todo row"): due label in a 56 px column, title, checkbox on the
 * right. The row is a button that opens the edit panel; the checkbox is a separate button.
 * Keys on a focused row: ↑ ↓ / J K move, Enter opens, X ticks, Delete deletes (with undo).
 */
export function TodoRow({ row, checked, fading, onToggle }: { row: RowData; checked: boolean; fading?: boolean; onToggle: (done: boolean) => void }) {
  const panel = usePanel();
  const online = useOnline();
  const qc = useQueryClient();
  const toast = useToast();
  const { item } = row;
  const selected = panel.current === item.todo.id;

  async function remove() {
    try {
      await deleteTodo(qc, item.todo.id);
      toast({ message: "Todo deleted", action: { label: "Undo", run: () => restoreRow(qc, "todos", item.todo.id) } });
    } catch {
      toast({ message: "Couldn't delete. Check your connection." });
    }
  }

  function onKey(e: KeyboardEvent<HTMLButtonElement>) {
    if (e.ctrlKey || e.metaKey || e.altKey) return;
    const k = e.key;
    if (k === "ArrowDown" || k === "j") { e.preventDefault(); focusRow(e.currentTarget, 1); }
    else if (k === "ArrowUp" || k === "k") { e.preventDefault(); focusRow(e.currentTarget, -1); }
    else if ((k === "x" || k === "X") && online) { e.preventDefault(); onToggle(!checked); }
    else if (k === "Delete" && online) {
      e.preventDefault();
      focusRow(e.currentTarget, 1);
      remove();
    }
  }

  return (
    <li className={`transition-opacity duration-150 ${fading ? "opacity-0" : ""}`}>
      <div className={`flex min-h-12 items-center rounded-xl pl-2 pr-3 ${selected ? "bg-hover" : "hover:bg-hover"}`}>
        <button
          type="button"
          data-todo-row={item.todo.id}
          aria-current={selected || undefined}
          onClick={() => panel.open(item.todo.id)}
          onKeyDown={onKey}
          className="flex min-w-0 flex-1 cursor-default items-baseline gap-0 py-2.5 text-left"
        >
          <span className={`w-14 shrink-0 text-meta tabular-nums ${row.due.late ? "text-danger" : "text-muted"}`}>{row.due.text}</span>
          <span className="min-w-0 flex-1">
            <span className={`block truncate text-body ${checked ? "text-muted line-through" : "text-ink"} ${selected ? "font-bold" : ""}`}>{item.todo.title}</span>
            {row.second && (
              <span className={`block truncate text-meta ${row.secondDanger ? "text-danger" : "text-muted"}`} style={row.secondDanger ? undefined : row.secondStyle}>
                {row.second}
              </span>
            )}
          </span>
        </button>
        <TodoCheckbox checked={checked} title={item.todo.title} disabled={!online} disabledReason={OFFLINE_REASON} onChange={onToggle} />
      </div>
    </li>
  );
}

/** A titled group of rows (Overdue, This week…); hidden by the caller when empty. */
export function TodoSection({ title, danger, children }: { title: string; danger?: boolean; children: ReactNode }) {
  return (
    <section className="flex flex-col gap-1">
      <SectionHeading danger={danger} className="px-2">{title}</SectionHeading>
      <ul className="flex flex-col">{children}</ul>
    </section>
  );
}

// ---------------- Inline add ----------------

/** Fired by the N shortcut; the inline add on the page opens. */
export const ADD_EVENT = "commit:add";

/**
 * "Add a todo" at the end of a list (web spec, "Inline add"). Click or N: a field in place. Enter
 * saves with no date and keeps the field open; Shift+Enter (or More) saves and opens the panel.
 * With [commitments] (Today), a small picker chooses the commitment, defaulting to the last used.
 */
export function InlineAdd({ commitmentId, commitments }: { commitmentId?: string; commitments?: Commitment[] }) {
  const qc = useQueryClient();
  const panel = usePanel();
  const online = useOnline();
  const toast = useToast();
  const [open, setOpen] = useState(false);
  const [text, setText] = useState("");
  const [busy, setBusy] = useState(false);
  const [picked, setPicked] = useState<string>(() => {
    const last = lastCommitment();
    return commitments?.find((c) => c.id === last)?.id ?? commitments?.[0]?.id ?? "";
  });
  const input = useRef<HTMLInputElement>(null);
  const target = commitmentId ?? (commitments?.some((c) => c.id === picked) ? picked : commitments?.[0]?.id) ?? "";

  useEffect(() => {
    const onAdd = () => { setOpen(true); requestAnimationFrame(() => input.current?.focus()); };
    window.addEventListener(ADD_EVENT, onAdd);
    return () => window.removeEventListener(ADD_EVENT, onAdd);
  }, []);

  async function add(more: boolean) {
    const title = text.trim();
    if (!title || !target || busy) return;
    setBusy(true);
    try {
      const created = await createTodo(qc, { title, commitmentId: target, dueAt: null, hasTime: false, repeatDays: null });
      rememberCommitment(target);
      setText("");
      if (more) { setOpen(false); panel.open(created.id); }
    } catch {
      toast({ message: "Couldn't save. Check your connection.", action: { label: "Try again", run: () => add(more) } });
    } finally {
      setBusy(false);
    }
  }

  if (!target) return null;
  if (!open) {
    return (
      <button
        type="button"
        data-inline-add
        disabled={!online}
        title={online ? "Add a todo (N)" : OFFLINE_REASON}
        onClick={() => { setOpen(true); requestAnimationFrame(() => input.current?.focus()); }}
        className="flex min-h-12 w-full cursor-pointer items-center rounded-xl pl-2 pr-3 text-left text-body text-muted hover:bg-hover disabled:cursor-not-allowed"
      >
        <span className="flex w-14 shrink-0 items-center">{Icons.plus}</span>
        Add a todo
      </button>
    );
  }
  return (
    <div data-inline-add className="flex min-h-12 items-center gap-2 rounded-xl pl-2 pr-1">
      <span aria-hidden className="flex w-12 shrink-0 items-center text-muted">{Icons.plus}</span>
      <input
        ref={input}
        aria-label="New todo title"
        placeholder="Add a todo"
        value={text}
        disabled={!online}
        onChange={(e) => setText(e.target.value)}
        onBlur={() => { if (!text.trim()) setOpen(false); }}
        onKeyDown={(e) => {
          if (e.key === "Enter") { e.preventDefault(); add(e.shiftKey); }
          if (e.key === "Escape") { e.preventDefault(); e.stopPropagation(); setText(""); setOpen(false); }
        }}
        className={`${fieldClass} h-11 min-w-0 flex-1`}
      />
      {commitments && commitments.length > 1 && (
        <select aria-label="Commitment" value={target} onChange={(e) => setPicked(e.target.value)}
          onMouseDown={(e) => e.stopPropagation()}
          className="h-11 max-w-36 shrink-0 cursor-pointer truncate rounded-xl bg-transparent px-2 text-meta text-muted hover:bg-hover">
          {commitments.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
        </select>
      )}
      {text.trim() && (
        <button type="button" onMouseDown={(e) => e.preventDefault()} onClick={() => add(true)} title="Save and add details (Shift+Enter)"
          className="h-9 shrink-0 cursor-pointer rounded-lg px-2.5 text-label font-bold text-ink hover:bg-hover">More</button>
      )}
    </div>
  );
}
