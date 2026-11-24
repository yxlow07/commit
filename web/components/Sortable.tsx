"use client";

import { useId, useRef, useState, type ReactNode } from "react";

/**
 * Drag to reorder (web spec, "Drag to reorder"). A grip shows on hover and keyboard focus.
 * - Pointer: drag the grip; the row lifts with the hover background and the others slide out of
 *   the way (150 ms). The order saves on drop.
 * - Keyboard: focus the grip, Space lifts, arrow keys move, Space drops, Esc cancels.
 * Each move is announced ("Study, moved to position 2 of 4").
 */
export function SortableList<T>({
  items, getId, getLabel, onReorder, renderItem, className = "", itemClassName = "",
}: {
  items: T[];
  getId: (item: T) => string;
  getLabel: (item: T) => string;
  /** Saves the new order; the dropped order shows until the returned promise settles. */
  onReorder: (ids: string[]) => unknown;
  renderItem: (item: T, handle: ReactNode, dragging: boolean) => ReactNode;
  className?: string;
  itemClassName?: string;
}) {
  const helpId = useId();
  const serverIds = items.map(getId);
  // The order just dropped, shown until the saved order comes back from the server.
  const [optimistic, setOptimistic] = useState<string[] | null>(null);
  const ids = optimistic && sameSet(optimistic, serverIds) ? optimistic : serverIds;

  // Row positions are measured when a drag starts and kept with it, so render reads state only.
  const [drag, setDrag] = useState<{ id: string; from: number; to: number; dy: number; keyboard: boolean; m: Measure } | null>(null);
  const [announcement, setAnnouncement] = useState("");
  const rows = useRef(new Map<string, HTMLLIElement>());
  const byId = new Map(items.map((it) => [getId(it), it]));
  const label = (id: string) => { const it = byId.get(id); return it ? getLabel(it) : ""; };

  function snapshot(y: number): Measure {
    const tops: number[] = [];
    const heights: number[] = [];
    for (const id of ids) {
      const r = rows.current.get(id)?.getBoundingClientRect();
      tops.push(r?.top ?? 0);
      heights.push(r?.height ?? 0);
    }
    return { tops, heights, y };
  }

  /** Offset of row [i] while the dragged row moves from [from] to [to]. */
  function offset(i: number): number {
    if (!drag) return 0;
    const { from, to, m } = drag;
    if (i === from) {
      if (!drag.keyboard) return drag.dy;
      return m.tops[to] - m.tops[from] + (to > from ? m.heights[to] - m.heights[from] : 0);
    }
    const shift = m.heights[from] + gapAfter(m, from);
    if (from < to && i > from && i <= to) return -shift;
    if (to < from && i >= to && i < from) return shift;
    return 0;
  }

  function targetFor(m: Measure, dy: number, from: number): number {
    const centre = m.tops[from] + m.heights[from] / 2 + dy;
    let to = from;
    for (let i = 0; i < ids.length; i++) {
      if (i === from) continue;
      const mid = m.tops[i] + m.heights[i] / 2;
      if (i > from && centre > mid) to = Math.max(to, i);
      if (i < from && centre < mid) to = Math.min(to, i);
    }
    return to;
  }

  function commit(from: number, to: number) {
    if (from !== to) {
      const next = [...ids];
      next.splice(to, 0, next.splice(from, 1)[0]);
      setOptimistic(next);
      Promise.resolve(onReorder(next)).finally(() => setOptimistic(null));
    }
  }

  function announce(id: string, to: number) {
    setAnnouncement(`${label(id)}, moved to position ${to + 1} of ${ids.length}`);
  }

  return (
    <>
      <ul className={className}>
        {ids.map((id, i) => {
          const item = byId.get(id);
          if (!item) return null;
          const dragging = drag?.id === id;
          const dy = offset(i);
          const handle = (
            <button
              type="button"
              aria-label={`Reorder ${getLabel(item)}`}
              aria-describedby={helpId}
              aria-pressed={dragging || undefined}
              title="Drag to reorder"
              className={`flex h-11 w-6 shrink-0 cursor-grab touch-none items-center justify-center rounded-md text-faint hover:text-muted focus-visible:opacity-100 active:cursor-grabbing md:h-8 [@media(hover:none)]:opacity-100 ${dragging ? "opacity-100" : "opacity-0 group-hover/sort:opacity-100"}`}
              onPointerDown={(e) => {
                if (e.button !== 0) return;
                e.currentTarget.setPointerCapture(e.pointerId);
                setDrag({ id, from: i, to: i, dy: 0, keyboard: false, m: snapshot(e.clientY) });
              }}
              onPointerMove={(e) => {
                if (!drag || drag.keyboard) return;
                const moved = e.clientY - drag.m.y;
                setDrag({ ...drag, dy: moved, to: targetFor(drag.m, moved, drag.from) });
              }}
              onPointerUp={() => {
                if (!drag || drag.keyboard) return;
                commit(drag.from, drag.to);
                if (drag.from !== drag.to) announce(drag.id, drag.to);
                setDrag(null);
              }}
              onPointerCancel={() => setDrag(null)}
              onBlur={() => { if (drag?.keyboard) setDrag(null); }}
              onKeyDown={(e) => {
                if (e.key === " " || e.key === "Enter") {
                  e.preventDefault();
                  if (!drag) {
                    setDrag({ id, from: i, to: i, dy: 0, keyboard: true, m: snapshot(0) });
                    setAnnouncement(`${getLabel(item)} lifted. Use the arrow keys to move, Space to drop, Escape to cancel.`);
                  } else if (drag.keyboard) {
                    commit(drag.from, drag.to);
                    setAnnouncement(`${getLabel(item)} dropped at position ${drag.to + 1} of ${ids.length}`);
                    setDrag(null);
                  }
                } else if (drag?.keyboard && (e.key === "ArrowUp" || e.key === "ArrowDown")) {
                  e.preventDefault();
                  const to = Math.min(ids.length - 1, Math.max(0, drag.to + (e.key === "ArrowUp" ? -1 : 1)));
                  setDrag({ ...drag, to });
                  announce(drag.id, to);
                } else if (e.key === "Escape" && drag) {
                  e.preventDefault();
                  e.stopPropagation();
                  setDrag(null);
                  setAnnouncement(`Reorder cancelled. ${getLabel(item)} is back at position ${drag.from + 1}.`);
                }
              }}
            >
              {GRIP}
            </button>
          );
          return (
            <li
              key={id}
              ref={(el) => { if (el) rows.current.set(id, el); else rows.current.delete(id); }}
              style={drag ? { transform: dy ? `translateY(${dy}px)` : undefined, transition: dragging && !drag.keyboard ? "none" : "transform 150ms ease" } : undefined}
              className={`group/sort relative ${dragging ? "z-10 rounded-xl bg-background [&>*]:bg-hover" : ""} ${itemClassName}`}
            >
              {renderItem(item, handle, dragging)}
            </li>
          );
        })}
      </ul>
      <p id={helpId} hidden>Press Space to lift, the arrow keys to move, Space to drop.</p>
      <p className="sr-only" aria-live="assertive">{announcement}</p>
    </>
  );
}

interface Measure {
  tops: number[];
  heights: number[];
  /** Pointer y when the drag started. */
  y: number;
}

function gapAfter(m: Measure, i: number): number {
  const next = i + 1 < m.tops.length ? i + 1 : i - 1;
  if (next < 0) return 0;
  const [a, b] = next > i ? [i, next] : [next, i];
  return Math.max(0, m.tops[b] - m.tops[a] - m.heights[a]);
}

function sameSet(a: string[], b: string[]) {
  return a.length === b.length && a.every((x) => b.includes(x));
}

/** Two columns of three dots, as on the phone. */
const GRIP = (
  <svg viewBox="0 0 24 24" width="16" height="16" fill="currentColor" aria-hidden>
    {[9, 15].flatMap((x) => [6.5, 12, 17.5].map((y) => <circle key={`${x}-${y}`} cx={x} cy={y} r={1.7} />))}
  </svg>
);
