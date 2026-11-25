"use client";

import {
  useEffect, useId, useRef, useState,
  type ButtonHTMLAttributes, type ComponentProps, type KeyboardEvent, type ReactNode, type SelectHTMLAttributes,
} from "react";
import Link from "next/link";

/**
 * Shared components (App Design Spec "Components", web spec "Web components"). Every colour is a
 * token; selection is shown with type (bold ink) rather than coloured backgrounds.
 */

// ---------------- Buttons ----------------

type Variant = "primary" | "secondary" | "text" | "quiet";

const variants: Record<Variant, string> = {
  primary: "h-12 px-6 text-body font-medium bg-ink text-on-ink hover:bg-ink/88",
  secondary: "h-12 px-6 text-body font-medium bg-fill text-ink hover:bg-hover",
  text: "h-11 px-3 text-body font-bold text-ink hover:bg-hover",
  quiet: "h-11 px-3 text-body text-muted hover:bg-hover",
};

export function Button({ variant = "secondary", className = "", ...props }: ButtonHTMLAttributes<HTMLButtonElement> & { variant?: Variant }) {
  return (
    <button
      type="button"
      className={`inline-flex shrink-0 cursor-pointer items-center justify-center gap-1.5 rounded-xl disabled:cursor-not-allowed disabled:opacity-45 ${variants[variant]} ${className}`}
      {...props}
    />
  );
}

/** Icon-only button: 44 px on touch widths, 32 px with a mouse (md+). */
export function IconButton({ label, shortcut, className = "", children, ...props }: ButtonHTMLAttributes<HTMLButtonElement> & { label: string; shortcut?: string }) {
  return (
    <button
      type="button"
      aria-label={label}
      title={shortcut ? `${label} (${shortcut})` : label}
      className={`inline-flex size-11 shrink-0 cursor-pointer items-center justify-center rounded-xl text-muted hover:bg-hover hover:text-ink disabled:cursor-not-allowed disabled:opacity-40 md:size-8 ${className}`}
      {...props}
    >
      {children}
    </button>
  );
}

/** 44 px `fill` square with a muted glyph: delete and archive, bottom left of forms. */
export function IconTile({ label, className = "", children, ...props }: ButtonHTMLAttributes<HTMLButtonElement> & { label: string }) {
  return (
    <button
      type="button"
      aria-label={label}
      title={label}
      className={`inline-flex size-11 shrink-0 cursor-pointer items-center justify-center rounded-xl bg-fill text-muted hover:text-ink disabled:opacity-40 ${className}`}
      {...props}
    >
      {children}
    </button>
  );
}

/** Primary add: a 56 px `ink` square (12 px corners) with an `on-ink` plus, as in the app. */
export function RoundAdd({ label, className = "", ...props }: ButtonHTMLAttributes<HTMLButtonElement> & { label: string }) {
  return (
    <button
      type="button"
      aria-label={label}
      title={label}
      className={`inline-flex size-14 shrink-0 cursor-pointer items-center justify-center rounded-xl bg-ink text-on-ink hover:bg-ink/88 ${className}`}
      {...props}
    >
      {Icons.plus}
    </button>
  );
}

// ---------------- Fields ----------------

export const fieldClass =
  "h-12 w-full rounded-xl bg-fill px-3.5 text-body text-ink placeholder:text-muted outline-none focus-visible:outline-2 focus-visible:outline-ink focus:outline-2 focus:outline-offset-2 focus:outline-ink";

export function TextField({ className = "", ...props }: ComponentProps<"input">) {
  return <input className={`${fieldClass} ${className}`} {...props} />;
}

export function SelectField({ className = "", ...props }: SelectHTMLAttributes<HTMLSelectElement>) {
  return <select className={`${fieldClass} cursor-pointer pr-8 ${className}`} {...props} />;
}

/** Label above a control in `meta` `muted`. */
export function Field({ label, children, className = "", htmlFor }: { label: string; children: ReactNode; className?: string; htmlFor?: string }) {
  return (
    <div className={`flex flex-col gap-1.5 ${className}`}>
      <label htmlFor={htmlFor} className="text-meta text-muted">{label}</label>
      {children}
    </div>
  );
}

/** A field group whose label names a group of controls (segmented control, date buttons). */
export function FieldGroup({ label, children, className = "" }: { label: string; children: ReactNode; className?: string }) {
  const id = useId();
  return (
    <div role="group" aria-labelledby={id} className={`flex flex-col gap-1.5 ${className}`}>
      <span id={id} className="text-meta text-muted">{label}</span>
      {children}
    </div>
  );
}

// ---------------- Checkbox and switch ----------------

/** Todo checkbox: 22 px circle in a 44 px hit area; checked = ink fill with an on-ink tick. */
export function TodoCheckbox({ checked, onChange, title, disabled, disabledReason }: {
  checked: boolean; onChange: (v: boolean) => void; title: string; disabled?: boolean; disabledReason?: string;
}) {
  return (
    <button
      type="button"
      role="checkbox"
      aria-checked={checked}
      aria-label={`${checked ? "Mark not done" : "Mark done"}: ${title}`}
      title={disabled ? disabledReason : undefined}
      disabled={disabled}
      onClick={(e) => { e.stopPropagation(); onChange(!checked); }}
      className="-my-1 -mr-2.5 flex size-11 shrink-0 cursor-pointer items-center justify-center rounded-full disabled:cursor-not-allowed disabled:opacity-45"
    >
      <span className={`flex size-[22px] items-center justify-center rounded-full border-[1.5px] border-ink ${checked ? "bg-ink text-on-ink" : ""}`}>
        {checked && <svg viewBox="0 0 24 24" width="12" height="12" fill="none" stroke="currentColor" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round" aria-hidden><path d="m5 12.5 4.5 4.5L19 7.5" /></svg>}
      </span>
    </button>
  );
}

export function Switch({ checked, onChange, label, disabled }: { checked: boolean; onChange: (v: boolean) => void; label: string; disabled?: boolean }) {
  return (
    <button
      type="button"
      role="switch"
      aria-checked={checked}
      aria-label={label}
      disabled={disabled}
      onClick={() => onChange(!checked)}
      className="flex h-11 w-14 shrink-0 cursor-pointer items-center justify-center disabled:cursor-not-allowed disabled:opacity-45"
    >
      <span className={`flex h-7 w-12 items-center rounded-full p-[3px] transition-colors ${checked ? "bg-ink" : "bg-track"}`}>
        <span className={`size-[22px] rounded-full transition-transform ${checked ? "translate-x-5 bg-on-ink" : "bg-surface"}`} />
      </span>
    </button>
  );
}

// ---------------- Tabs, headings, segmented control ----------------

export interface TabItem<T extends string> {
  id: T;
  label: string;
}

/**
 * Text tabs: selected Bold ink with an 18 x 2 dash of ink at 22 % under it; others Regular muted.
 * Arrow keys move between tabs (roving focus).
 */
export function TextTabs<T extends string>({ items, value, onChange, label, size = "label", className = "" }: {
  items: TabItem<T>[]; value: T; onChange: (id: T) => void; label: string; size?: "label" | "body"; className?: string;
}) {
  const refs = useRef<(HTMLButtonElement | null)[]>([]);
  function onKey(e: KeyboardEvent, i: number) {
    const dir = e.key === "ArrowRight" ? 1 : e.key === "ArrowLeft" ? -1 : 0;
    if (!dir) return;
    e.preventDefault();
    const next = (i + dir + items.length) % items.length;
    refs.current[next]?.focus();
    onChange(items[next].id);
  }
  return (
    <div role="tablist" aria-label={label} className={`flex flex-wrap items-baseline gap-x-5 gap-y-1 ${className}`}>
      {items.map((t, i) => {
        const selected = t.id === value;
        return (
          <button
            key={t.id}
            ref={(el) => { refs.current[i] = el; }}
            type="button"
            role="tab"
            aria-selected={selected}
            tabIndex={selected ? 0 : -1}
            onKeyDown={(e) => onKey(e, i)}
            onClick={() => onChange(t.id)}
            className={`relative cursor-pointer pb-2.5 pt-1 ${size === "body" ? "text-body" : "text-label"} ${selected ? "font-bold text-ink" : "font-normal text-muted hover:text-ink"}`}
          >
            {t.label}
            {selected && <span aria-hidden className="absolute bottom-0.5 left-1/2 h-0.5 w-[18px] -translate-x-1/2 rounded-full bg-ink/22" />}
          </button>
        );
      })}
    </div>
  );
}

/** A list's heading, as in the app: `body` Regular `muted` (`danger` for Overdue). */
export function SectionHeading({ children, danger, className = "", id }: { children: ReactNode; danger?: boolean; className?: string; id?: string }) {
  return <h2 id={id} className={`text-body font-normal ${danger ? "text-danger" : "text-muted"} ${className}`}>{children}</h2>;
}

/** A section heading with icon actions (+, pencil) on the right, 44 px tall. */
export function SectionHeader({ title, children, className = "", id }: { title: ReactNode; children?: ReactNode; className?: string; id?: string }) {
  return (
    <div className={`flex min-h-11 items-center gap-1 ${className}`}>
      <SectionHeading id={id} className="min-w-0 flex-1 truncate">{title}</SectionHeading>
      {children && <div className="-mr-3 flex items-center">{children}</div>}
    </div>
  );
}

/** Forms only: fill track (radius 14, 3 px padding), selected segment on surface in bold. */
export function Segmented<T extends string>({ items, value, onChange, label, scroll, className = "" }: {
  items: TabItem<T>[]; value: T; onChange: (id: T) => void; label: string; scroll?: boolean; className?: string;
}) {
  return (
    <div role="radiogroup" aria-label={label} className={`flex rounded-[14px] bg-fill p-[3px] ${scroll ? "overflow-x-auto [scrollbar-width:none]" : ""} ${className}`}>
      {items.map((t) => {
        const selected = t.id === value;
        return (
          <button
            key={t.id}
            type="button"
            role="radio"
            aria-checked={selected}
            onClick={() => onChange(t.id)}
            className={`h-10 min-w-0 cursor-pointer whitespace-nowrap rounded-[11px] px-3 text-label ${scroll ? "shrink-0" : "flex-1"} ${selected ? "bg-surface font-bold text-ink shadow-[0_1px_2px_color-mix(in_srgb,var(--ink)_12%,transparent)]" : "text-muted hover:text-ink"}`}
          >
            {t.label}
          </button>
        );
      })}
    </div>
  );
}

/** [−] value [+]: fill squares, value 28 / Light tabular, unit in micro muted. */
export function Stepper({ label, value, onChange, step, min = 0, max = 9999, format, unit }: {
  label: string; value: number; onChange: (v: number) => void; step: number; min?: number; max?: number;
  format?: (v: number) => string; unit?: string;
}) {
  const clamp = (v: number) => Math.min(max, Math.max(min, Math.round(v * 100) / 100));
  const btn = "flex size-11 shrink-0 cursor-pointer items-center justify-center rounded-xl bg-fill text-[22px] font-normal text-ink hover:bg-hover disabled:cursor-not-allowed disabled:opacity-40";
  return (
    <div role="group" aria-label={label} className="flex flex-col gap-1.5">
      <span className="text-meta text-muted">{label}</span>
      <div className="flex items-center gap-2">
        <button type="button" className={btn} aria-label={`Less ${label.toLowerCase()}`} disabled={value <= min} onClick={() => onChange(clamp(value - step))}>−</button>
        <div className="flex min-w-16 flex-1 flex-col items-center" aria-live="polite">
          <span className="text-[1.75rem] font-light leading-none tabular-nums">{format ? format(value) : value}</span>
          {unit && <span className="mt-1 text-micro text-muted">{unit}</span>}
        </div>
        <button type="button" className={btn} aria-label={`More ${label.toLowerCase()}`} disabled={value >= max} onClick={() => onChange(clamp(value + step))}>+</button>
      </div>
    </div>
  );
}

// ---------------- Weekday toggles ----------------

const DAY_NAMES = ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"];
const DAY_LETTERS = ["M", "T", "W", "T", "F", "S", "S"];

/**
 * Seven 40 px toggles (bit 0 = Monday). Click toggles one; press and drag across paints several,
 * selecting or clearing as set by the first day pressed (like the phone).
 */
export function WeekdayToggles({ mask, onChange, label, keepOne }: { mask: number; onChange: (mask: number) => void; label: string; keepOne?: boolean }) {
  const paint = useRef<{ on: boolean; mask: number } | null>(null);
  const [live, setLive] = useState<number | null>(null);
  const shown = live ?? mask;

  const apply = (m: number, i: number, on: boolean) => (on ? m | (1 << i) : m & ~(1 << i));
  function finish() {
    if (!paint.current) return;
    let m = paint.current.mask;
    if (keepOne && m === 0) m = mask || 1;
    paint.current = null;
    setLive(null);
    if (m !== mask) onChange(m);
  }
  useEffect(() => {
    window.addEventListener("pointerup", finish);
    return () => window.removeEventListener("pointerup", finish);
  });

  return (
    <div role="group" aria-label={label} className="flex gap-1.5" onPointerLeave={() => paint.current && setLive(paint.current.mask)}>
      {DAY_NAMES.map((name, i) => {
        const on = (shown & (1 << i)) !== 0;
        return (
          <button
            key={name}
            type="button"
            aria-pressed={on}
            aria-label={name}
            className={`flex size-10 cursor-pointer touch-none select-none items-center justify-center rounded-xl text-label ${on ? "bg-ink font-bold text-on-ink" : "bg-fill text-muted hover:text-ink"}`}
            onPointerDown={(e) => {
              e.preventDefault();
              (e.currentTarget as HTMLElement).releasePointerCapture?.(e.pointerId);
              const turnOn = (mask & (1 << i)) === 0;
              paint.current = { on: turnOn, mask: apply(mask, i, turnOn) };
              setLive(paint.current.mask);
            }}
            onPointerEnter={() => {
              if (!paint.current) return;
              paint.current.mask = apply(paint.current.mask, i, paint.current.on);
              setLive(paint.current.mask);
            }}
            onKeyDown={(e) => {
              if (e.key !== "Enter" && e.key !== " ") return;
              e.preventDefault();
              const next = mask ^ (1 << i);
              if (keepOne && next === 0) return;
              onChange(next);
            }}
          >
            {DAY_LETTERS[i]}
          </button>
        );
      })}
    </div>
  );
}

// ---------------- Dialog ----------------

/**
 * Native modal <dialog>: surface, radius 20, max 440 px, 24 px padding, over the scrim. Esc and a
 * click on the scrim close it; the browser traps focus and returns it to the opener.
 */
export function Dialog({ open, onClose, title, children, wide }: { open: boolean; onClose: () => void; title: string; children: ReactNode; wide?: boolean }) {
  const ref = useRef<HTMLDialogElement>(null);
  const opener = useRef<Element | null>(null);
  useEffect(() => {
    const d = ref.current;
    if (!d) return;
    if (open && !d.open) {
      opener.current = document.activeElement;
      d.showModal();
    }
    if (!open && d.open) {
      d.close();
      (opener.current as HTMLElement | null)?.focus?.();
    }
  }, [open]);
  return (
    <dialog
      ref={ref}
      aria-label={title}
      onCancel={(e) => { e.preventDefault(); onClose(); }}
      onClick={(e) => e.target === ref.current && onClose()}
      style={{ borderRadius: "var(--radius-dialog)" }}
      className={`m-auto w-[min(100vw-2rem,440px)] bg-surface p-0 text-ink ${wide ? "md:w-[520px]" : ""}`}
    >
      {open && (
        <div className="flex flex-col gap-5 p-6">
          <h2 className="text-title-section">{title}</h2>
          {children}
        </div>
      )}
    </dialog>
  );
}

// ---------------- States ----------------

/** "Loading…" in meta muted, only after 300 ms so fast loads don't flash (no spinners or skeletons). */
export function Loading({ className = "" }: { className?: string }) {
  const [show, setShow] = useState(false);
  useEffect(() => {
    const t = setTimeout(() => setShow(true), 300);
    return () => clearTimeout(t);
  }, []);
  return <p className={`py-3 text-meta text-muted ${className}`} aria-live="polite">{show ? "Loading…" : " "}</p>;
}

/** Empty states in body.l text-strong. */
export function EmptyText({ children, className = "" }: { children: ReactNode; className?: string }) {
  return <p className={`text-body-l text-text-strong ${className}`}>{children}</p>;
}

export function errorMessage(error: unknown): string {
  if (!error) return "";
  if (error instanceof Error) return error.message;
  if (typeof error === "object" && "message" in error) return String((error as { message: unknown }).message);
  return String(error);
}

export function ErrorText({ error }: { error: unknown }) {
  if (!error) return null;
  return <p role="alert" className="text-meta text-danger">{errorMessage(error)}</p>;
}

/**
 * Page title as in the app: `title.screen` (40 Bold), actions beside it, and the one-line summary
 * below in `body.l` `text-strong` ("3 open, 1 overdue.").
 */
export function PageTitle({ title, summary, children, before }: { title: ReactNode; summary?: ReactNode; children?: ReactNode; before?: ReactNode }) {
  return (
    <header className="flex flex-col">
      {before}
      <div className="flex min-w-0 items-center gap-2">
        <h1 className="min-w-0 truncate text-title-screen">{title}</h1>
        {children}
      </div>
      {summary && <p className="mt-5 text-body-l text-text-strong">{summary}</p>}
    </header>
  );
}

/** "‹ Commitments" above a pushed page's title, like the app's back arrow with where it goes. */
export function BackLink({ href, label }: { href: string; label: string }) {
  return (
    <Link href={href} className="-ml-2 mb-4 flex h-11 items-center gap-1 self-start rounded-xl pl-1 pr-3 text-body text-muted hover:bg-hover hover:text-ink">
      {Icons.left}
      {label}
    </Link>
  );
}

// ---------------- Hooks ----------------

/** Whether a media query matches (layout switches that change which elements exist, not just CSS). */
export function useMedia(query: string): boolean {
  const [match, setMatch] = useState(() => typeof window !== "undefined" && window.matchMedia(query).matches);
  useEffect(() => {
    const m = window.matchMedia(query);
    const on = () => setMatch(m.matches);
    on();
    m.addEventListener("change", on);
    return () => m.removeEventListener("change", on);
  }, [query]);
  return match;
}

/** Phone layout (below 600 px): the phone app's screens. */
export const usePhone = () => !useMedia("(min-width: 600px)");

/** Current time, refreshed every minute so due labels roll over (e.g. at midnight). */
export function useNow(intervalMs = 60_000): number {
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    const t = setInterval(() => setNow(Date.now()), intervalMs);
    return () => clearInterval(t);
  }, [intervalMs]);
  return now;
}

/** Wraps an async action with busy + error state for buttons and forms. */
export function useAction() {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);
  async function run(fn: () => Promise<unknown>) {
    setBusy(true);
    setError(null);
    try {
      await fn();
      return true;
    } catch (e) {
      setError(e);
      return false;
    } finally {
      setBusy(false);
    }
  }
  return { busy, error, run, clear: () => setError(null) };
}

// ---------------- Icons ----------------

const svg = (path: ReactNode, size = 18) => (
  <svg viewBox="0 0 24 24" width={size} height={size} fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden>{path}</svg>
);

export const Icons = {
  plus: svg(<path d="M12 5v14M5 12h14" />, 20),
  pencil: svg(<path d="M4 20h4L19 9a2.8 2.8 0 0 0-4-4L4 16zM13.5 6.5l4 4" />),
  grip: (
    <svg viewBox="0 0 24 24" width="16" height="16" fill="currentColor" aria-hidden>
      {[8, 16].flatMap((x) => [6, 12, 18].map((y) => <circle key={`${x}-${y}`} cx={x} cy={y} r="1.6" />))}
    </svg>
  ),
  trash: svg(<path d="M4 7h16M10 11v6M14 11v6M6 7l1 12a2 2 0 0 0 2 2h6a2 2 0 0 0 2-2l1-12M9 7V4h6v3" />),
  archive: svg(<path d="M3 4h18v4H3zM5 8v11a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1V8M10 12h4" />),
  chevron: svg(<path d="m9 6 6 6-6 6" />, 16),
  back: svg(<path d="M19 12H5M11 18l-6-6 6-6" />, 20),
  close: svg(<path d="M6 6l12 12M18 6 6 18" />),
  menu: svg(<path d="M4 7h16M4 12h16M4 17h16" />, 20),
  left: svg(<path d="m15 6-6 6 6 6" />),
  right: svg(<path d="m9 6 6 6-6 6" />),
};

/** The app icon: a 3 x 3 heatmap grid. */
export function Logo({ size = 24 }: { size?: number }) {
  const shades = ["#D3D5D8", "#B6B9BD", "#9C9FA4", "#B6B9BD", "#6B6E73", "#9C9FA4", "#9C9FA4", "#6B6E73", "#1F2123"];
  return (
    <svg width={size} height={size} viewBox="0 0 108 108" aria-hidden>
      <rect width="108" height="108" rx="24" fill="#F6F6F5" />
      {shades.map((fill, i) => <rect key={i} x={30 + (i % 3) * 18} y={30 + Math.floor(i / 3) * 18} width="13" height="13" rx="3" fill={fill} />)}
    </svg>
  );
}
