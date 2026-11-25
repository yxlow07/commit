"use client";

import { useEffect, useId, useRef, useState, type KeyboardEvent, type ReactNode } from "react";
import { addDays, startOfDay, startOfWeek } from "@/lib/domain/dates";
import { REPEAT_UNITS, minutesLabel, parseTime, shortDate, splitInterval, type RepeatUnit } from "@/lib/domain/labels";
import { weekdaysLabel } from "@/lib/domain/recurrence";
import { Icons, Segmented, Switch, WeekdayToggles, fieldClass } from "./ui";

/** Closes a popover on a click outside [ref]. */
function useOutside(ref: React.RefObject<HTMLElement | null>, open: boolean, close: () => void) {
  useEffect(() => {
    if (!open) return;
    const onDown = (e: PointerEvent) => { if (ref.current && !ref.current.contains(e.target as Node)) close(); };
    document.addEventListener("pointerdown", onDown);
    return () => document.removeEventListener("pointerdown", onDown);
  }, [ref, open, close]);
}

const pickerButton = "h-11 cursor-pointer rounded-xl bg-fill px-3.5 text-body tabular-nums text-ink hover:bg-hover";
const popover = "fade-in absolute left-0 top-full z-30 mt-2 rounded-2xl border border-track bg-surface p-4";

// ---------------- Date ----------------

const monthYear = new Intl.DateTimeFormat("en-GB", { month: "long", year: "numeric" });
const longDay = new Intl.DateTimeFormat("en-GB", { weekday: "long", day: "numeric", month: "long", year: "numeric" });

/**
 * Date button with a popover calendar (web spec, "Date and time"): Mon-first grid, today ringed,
 * selected filled ink, past days muted. Arrow keys move the day, Page Up / Page Down the month,
 * Enter picks, Esc closes. Quick picks: Today, Tomorrow, Next Monday.
 */
export function DateButton({ value, onChange, now, label = "Due date" }: { value: number | null; onChange: (day: number) => void; now: number; label?: string }) {
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);
  const button = useRef<HTMLButtonElement>(null);
  const close = () => { setOpen(false); button.current?.focus(); };
  useOutside(ref, open, () => setOpen(false));

  return (
    <div ref={ref} className="relative">
      <button ref={button} type="button" aria-haspopup="dialog" aria-expanded={open} aria-label={`${label}: ${value != null ? longDay.format(value) : "none"}`}
        className={`${pickerButton} ${value == null ? "text-muted" : ""}`} onClick={() => setOpen((o) => !o)}>
        {value != null ? shortDate(value) : "Add date"}
      </button>
      {open && (
        <Calendar value={value} now={now} onPick={(d) => { onChange(d); close(); }} onClose={close} />
      )}
    </div>
  );
}

function Calendar({ value, now, onPick, onClose }: { value: number | null; now: number; onPick: (d: number) => void; onClose: () => void }) {
  const today = startOfDay(now);
  const [cursor, setCursor] = useState(value ?? today);
  const grid = useRef<HTMLDivElement>(null);
  const month = new Date(cursor);
  const first = new Date(month.getFullYear(), month.getMonth(), 1).getTime();
  const start = startOfWeek(first);
  const days = Array.from({ length: 42 }, (_, i) => addDays(start, i));
  const shiftMonth = (n: number) => {
    const d = new Date(cursor);
    const target = new Date(d.getFullYear(), d.getMonth() + n, 1);
    const last = new Date(target.getFullYear(), target.getMonth() + 1, 0).getDate();
    setCursor(new Date(target.getFullYear(), target.getMonth(), Math.min(d.getDate(), last)).getTime());
  };

  useEffect(() => {
    grid.current?.querySelector<HTMLButtonElement>(`[data-day="${cursor}"]`)?.focus();
  }, [cursor]);

  function onKey(e: KeyboardEvent) {
    const moves: Record<string, () => void> = {
      ArrowLeft: () => setCursor(addDays(cursor, -1)),
      ArrowRight: () => setCursor(addDays(cursor, 1)),
      ArrowUp: () => setCursor(addDays(cursor, -7)),
      ArrowDown: () => setCursor(addDays(cursor, 7)),
      PageUp: () => shiftMonth(-1),
      PageDown: () => shiftMonth(1),
      Home: () => setCursor(startOfWeek(cursor)),
      End: () => setCursor(addDays(startOfWeek(cursor), 6)),
      Enter: () => onPick(cursor),
      Escape: () => onClose(),
    };
    const fn = moves[e.key];
    if (!fn) return;
    e.preventDefault();
    e.stopPropagation();
    fn();
  }

  const nextMonday = addDays(startOfWeek(today), 7);
  return (
    <div role="dialog" aria-label="Choose a date" className={`${popover} w-[19rem]`} onKeyDown={onKey}>
      <div className="mb-2 flex items-center justify-between">
        <span className="text-label font-bold" aria-live="polite">{monthYear.format(cursor)}</span>
        <div className="flex">
          <button type="button" aria-label="Previous month" className="flex size-8 cursor-pointer items-center justify-center rounded-lg text-muted hover:bg-hover hover:text-ink" onClick={() => shiftMonth(-1)}>{Icons.left}</button>
          <button type="button" aria-label="Next month" className="flex size-8 cursor-pointer items-center justify-center rounded-lg text-muted hover:bg-hover hover:text-ink" onClick={() => shiftMonth(1)}>{Icons.right}</button>
        </div>
      </div>
      <div ref={grid} role="grid" className="grid grid-cols-7 gap-0.5 text-center">
        {["M", "T", "W", "T", "F", "S", "S"].map((d, i) => <span key={i} aria-hidden className="pb-1 text-micro text-muted">{d}</span>)}
        {days.map((d) => {
          const inMonth = new Date(d).getMonth() === month.getMonth();
          const selected = d === value;
          const isToday = d === today;
          return (
            <button
              key={d}
              type="button"
              data-day={d}
              tabIndex={d === cursor ? 0 : -1}
              aria-label={longDay.format(d)}
              aria-pressed={selected}
              onClick={() => onPick(d)}
              className={`flex size-9 cursor-pointer items-center justify-center rounded-full text-meta tabular-nums
                ${selected ? "bg-ink font-bold text-on-ink" : isToday ? "ring-[1.5px] ring-ink ring-inset" : "hover:bg-hover"}
                ${!selected && (d < today || !inMonth) ? "text-muted" : ""}`}
            >
              {new Date(d).getDate()}
            </button>
          );
        })}
      </div>
      <div className="mt-3 flex gap-1">
        {[["Today", today], ["Tomorrow", addDays(today, 1)], ["Next Monday", nextMonday]].map(([label, d]) => (
          <button key={label} type="button" className="h-8 cursor-pointer rounded-lg px-2 text-label font-bold text-ink hover:bg-hover" onClick={() => onPick(d as number)}>{label}</button>
        ))}
      </div>
    </div>
  );
}

// ---------------- Time ----------------

/**
 * Typed time: the button becomes a field that accepts "17", "1700", "17:00" or "5pm" and shows
 * "17:00". Suggestions every 30 minutes from the next half hour. Invalid input keeps the old value.
 */
export function TimeButton({ value, onChange, now, label = "Due time" }: { value: number | null; onChange: (minutes: number) => void; now: number; label?: string }) {
  const [editing, setEditing] = useState(false);
  const [text, setText] = useState("");
  const [error, setError] = useState(false);
  const errorId = useId();
  const listId = useId();
  const input = useRef<HTMLInputElement>(null);
  const button = useRef<HTMLButtonElement>(null);

  const d = new Date(now);
  const nextHalf = Math.ceil((d.getHours() * 60 + d.getMinutes() + 1) / 30) * 30;
  const suggestions = Array.from({ length: 6 }, (_, i) => (nextHalf + i * 30) % (24 * 60));

  function commit(raw: string) {
    const m = parseTime(raw);
    if (m == null) {
      if (raw.trim()) { setError(true); return false; }
      setEditing(false);
      return true;
    }
    setError(false);
    setEditing(false);
    onChange(m);
    requestAnimationFrame(() => button.current?.focus());
    return true;
  }

  if (!editing) {
    return (
      <button ref={button} type="button" aria-label={`${label}: ${value != null ? minutesLabel(value) : "none"}`}
        className={`${pickerButton} ${value == null ? "text-muted" : ""}`}
        onClick={() => { setText(value != null ? minutesLabel(value) : ""); setError(false); setEditing(true); requestAnimationFrame(() => input.current?.select()); }}>
        {value != null ? minutesLabel(value) : "Add time"}
      </button>
    );
  }
  return (
    <div className="relative">
      <input
        ref={input}
        autoFocus
        aria-label={label}
        aria-invalid={error}
        aria-describedby={error ? errorId : undefined}
        aria-controls={listId}
        inputMode="text"
        placeholder="17:00"
        value={text}
        onChange={(e) => { setText(e.target.value); setError(false); }}
        onBlur={() => { if (!commit(text)) setEditing(false); }}
        onKeyDown={(e) => {
          if (e.key === "Enter") { e.preventDefault(); e.stopPropagation(); commit(text); }
          if (e.key === "Escape") { e.preventDefault(); e.stopPropagation(); setEditing(false); requestAnimationFrame(() => button.current?.focus()); }
        }}
        className={`${fieldClass} h-11 w-28 tabular-nums`}
      />
      <div id={listId} className={`${popover} flex w-36 flex-col p-1.5`} role="listbox" aria-label="Suggested times">
        {suggestions.map((m) => (
          <button key={m} type="button" role="option" aria-selected={m === value}
            className="h-9 cursor-pointer rounded-lg px-3 text-left text-body tabular-nums hover:bg-hover"
            onMouseDown={(e) => e.preventDefault()}
            onClick={() => commit(minutesLabel(m))}>
            {minutesLabel(m)}
          </button>
        ))}
      </div>
      {error && <p id={errorId} role="alert" className="absolute left-0 top-full z-40 mt-1 whitespace-nowrap rounded-lg bg-surface px-2 py-1 text-meta text-danger">Use a time like 17:00</p>}
    </div>
  );
}

// ---------------- Repeat ----------------

export interface RepeatValue {
  on: boolean;
  mode: "interval" | "weekdays";
  intervalDays: number;
  weekdays: number;
}

export function repeatFrom(intervalDays: number | null, weekdays: number | null): RepeatValue {
  return {
    on: (intervalDays ?? 0) > 0 || (weekdays ?? 0) > 0,
    mode: (weekdays ?? 0) > 0 ? "weekdays" : "interval",
    intervalDays: intervalDays && intervalDays > 0 ? intervalDays : 7,
    weekdays: weekdays && weekdays > 0 ? weekdays : 31,
  };
}

/**
 * Repeat (same model and wording as the app): a checkbox with a summary, then "After I finish it"
 * (number + unit, stored as days) or "On weekdays" (seven toggles, at least one stays selected).
 */
export function RepeatField({ value, onChange }: { value: RepeatValue; onChange: (v: RepeatValue) => void }) {
  const { count, unit } = splitInterval(value.intervalDays);
  const u = REPEAT_UNITS.find((x) => x.id === unit)!;
  const summary: ReactNode = !value.on ? "Repeat"
    : value.mode === "interval"
      ? count === 1 ? <>Repeat every <b>{u.one}</b> after I finish it</> : <>Repeat every <b>{count}</b> {u.many} after I finish it</>
      : <>Repeat on <b>{weekdaysLabel(value.weekdays)}</b></>;
  const setEvery = (c: number, unitId: RepeatUnit) => {
    const size = REPEAT_UNITS.find((x) => x.id === unitId)!.days;
    onChange({ ...value, intervalDays: Math.min(99, Math.max(1, c)) * size });
  };

  return (
    <div className="flex flex-col gap-3">
      {/* A switch row, as in the app: the summary on the left says what repeating means. */}
      <div className="flex min-h-12 items-center gap-3">
        <span className="min-w-0 flex-1 text-body-l">{summary}</span>
        <Switch label="Repeat" checked={value.on} onChange={(on) => onChange({ ...value, on })} />
      </div>
      {value.on && (
        <>
          <Segmented
            label="Repeat"
            items={[{ id: "interval", label: "After I finish it" }, { id: "weekdays", label: "On weekdays" }]}
            value={value.mode}
            onChange={(mode) => onChange({ ...value, mode })}
          />
          {value.mode === "interval" ? (
            <div className="flex gap-2">
              <input type="number" min={1} max={99} aria-label="Repeat every" value={count}
                onChange={(e) => setEvery(Number(e.target.value) || 1, unit)}
                className={`${fieldClass} h-11 w-20 text-center tabular-nums`} />
              <select aria-label="Unit" value={unit} onChange={(e) => setEvery(count, e.target.value as RepeatUnit)}
                className={`${fieldClass} h-11 w-auto cursor-pointer pr-8`}>
                {REPEAT_UNITS.map((x) => <option key={x.id} value={x.id}>{count === 1 ? x.one : x.many}</option>)}
              </select>
            </div>
          ) : (
            <WeekdayToggles label="Repeat on" mask={value.weekdays} keepOne onChange={(weekdays) => onChange({ ...value, weekdays })} />
          )}
        </>
      )}
    </div>
  );
}
