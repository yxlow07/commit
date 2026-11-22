"use client";

import { useRef, useState, type ReactNode } from "react";

/**
 * Drag to reorder (same model as the phone): drag a row by its grip handle, or focus the handle and
 * use the Up / Down arrow keys. The list shows the new order while dragging and hands it to
 * [onReorder] when the pointer is released (or straight away for a key press).
 */
export function SortableList<T>({
  items, getId, getLabel, onReorder, renderItem, className = "",
}: {
  items: T[];
  getId: (item: T) => string;
  getLabel: (item: T) => string;
  onReorder: (ids: string[]) => void;
  renderItem: (item: T, handle: ReactNode, dragging: boolean) => ReactNode;
  className?: string;
}) {
  const ids = items.map(getId);
  const [drag, setDrag] = useState<{ id: string; order: string[]; dy: number } | null>(null);
  const rows = useRef(new Map<string, HTMLLIElement>());
  // Captured when a drag starts: each row's top and height (in the original order), and where the pointer began.
  const start = useRef<{ y: number; tops: Map<string, number>; heights: Map<string, number>; gap: number; listTop: number } | null>(null);

  const order = drag?.order ?? ids;
  const byId = new Map(items.map((it) => [getId(it), it]));

  /** Where row [id] sits in [list], from the measured heights. */
  function topOf(id: string, list: string[]) {
    const s = start.current!;
    let top = s.listTop;
    for (const other of list) {
      if (other === id) return top;
      top += (s.heights.get(other) ?? 0) + s.gap;
    }
    return top;
  }

  function begin(id: string, y: number) {
    const tops = new Map<string, number>();
    const heights = new Map<string, number>();
    for (const rid of ids) {
      const r = rows.current.get(rid)?.getBoundingClientRect();
      if (r) { tops.set(rid, r.top); heights.set(rid, r.height); }
    }
    const first = tops.get(ids[0]) ?? 0;
    const gap = ids.length > 1 ? Math.max(0, (tops.get(ids[1]) ?? 0) - first - (heights.get(ids[0]) ?? 0)) : 0;
    start.current = { y, tops, heights, gap, listTop: first };
    setDrag({ id, order: ids, dy: 0 });
  }

  function move(y: number) {
    const s = start.current;
    if (!drag || !s) return;
    const pointer = (s.tops.get(drag.id) ?? 0) + (s.heights.get(drag.id) ?? 0) / 2 + (y - s.y);
    // Slot whose middle the pointer has passed, among the other rows.
    const rest = drag.order.filter((x) => x !== drag.id);
    let index = 0;
    for (const other of rest) {
      const mid = topOf(other, rest) + (s.heights.get(other) ?? 0) / 2;
      if (pointer > mid) index++;
    }
    const next = [...rest.slice(0, index), drag.id, ...rest.slice(index)];
    // Keep the row under the pointer from wherever the new order lays it out.
    const dy = (s.tops.get(drag.id) ?? 0) + (y - s.y) - topOf(drag.id, next);
    setDrag({ id: drag.id, order: next, dy });
  }

  function end() {
    if (drag && drag.order.join() !== ids.join()) onReorder(drag.order);
    start.current = null;
    setDrag(null);
  }

  function nudge(id: string, delta: number) {
    const i = ids.indexOf(id);
    const j = i + delta;
    if (i < 0 || j < 0 || j >= ids.length) return;
    const next = [...ids];
    next.splice(j, 0, next.splice(i, 1)[0]);
    onReorder(next);
  }

  return (
    <ul className={className}>
      {order.map((id) => {
        const item = byId.get(id);
        if (!item) return null;
        const dragging = drag?.id === id;
        const handle = (
          <button
            type="button"
            aria-label={`Reorder ${getLabel(item)}. Drag, or use the up and down arrow keys.`}
            title="Drag to reorder"
            className="flex h-8 w-6 shrink-0 cursor-grab touch-none items-center justify-center rounded text-fg-muted opacity-50 transition hover:opacity-100 focus-visible:opacity-100 active:cursor-grabbing"
            onPointerDown={(e) => { e.currentTarget.setPointerCapture(e.pointerId); begin(id, e.clientY); }}
            onPointerMove={(e) => { if (drag) move(e.clientY); }}
            onPointerUp={end}
            onPointerCancel={end}
            onKeyDown={(e) => {
              if (e.key === "ArrowUp") { e.preventDefault(); nudge(id, -1); }
              if (e.key === "ArrowDown") { e.preventDefault(); nudge(id, 1); }
            }}
          >
            {GRIP}
          </button>
        );
        return (
          <li
            key={id}
            ref={(el) => { if (el) rows.current.set(id, el); else rows.current.delete(id); }}
            style={dragging ? { transform: `translateY(${drag!.dy}px) scale(1.01)`, zIndex: 10, position: "relative" } : undefined}
            className={dragging ? "rounded-xl bg-surface shadow-lg" : ""}
          >
            {renderItem(item, handle, dragging)}
          </li>
        );
      })}
    </ul>
  );
}

/** Two columns of three dots, as on the phone. */
const GRIP = (
  <svg viewBox="0 0 24 24" width="16" height="16" fill="currentColor" aria-hidden>
    {[9, 15].flatMap((x) => [6.5, 12, 17.5].map((y) => <circle key={`${x}-${y}`} cx={x} cy={y} r={1.7} />))}
  </svg>
);
