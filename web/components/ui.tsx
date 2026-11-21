"use client";

import { useEffect, useRef, useState, type ButtonHTMLAttributes, type InputHTMLAttributes, type ReactNode, type SelectHTMLAttributes } from "react";
import { cssColor } from "@/lib/domain/format";

/** Small set of shared UI pieces (Tailwind). Colours come from CSS variables in globals.css. */

type Variant = "primary" | "secondary" | "ghost" | "danger";

const variants: Record<Variant, string> = {
  primary: "bg-primary text-on-primary hover:opacity-90",
  secondary: "bg-surface-2 text-fg hover:bg-surface-3",
  ghost: "text-fg-muted hover:bg-surface-2 hover:text-fg",
  danger: "text-danger hover:bg-danger/10",
};

export function Button({ variant = "secondary", className = "", ...props }: ButtonHTMLAttributes<HTMLButtonElement> & { variant?: Variant }) {
  return (
    <button
      type="button"
      className={`inline-flex items-center justify-center gap-1.5 rounded-full px-4 py-2 text-sm font-medium transition disabled:cursor-not-allowed disabled:opacity-50 ${variants[variant]} ${className}`}
      {...props}
    />
  );
}

export function IconButton({ label, className = "", children, ...props }: ButtonHTMLAttributes<HTMLButtonElement> & { label: string }) {
  return (
    <button
      type="button"
      aria-label={label}
      title={label}
      className={`inline-flex h-8 w-8 items-center justify-center rounded-full text-fg-muted transition hover:bg-surface-2 hover:text-fg disabled:opacity-30 ${className}`}
      {...props}
    >
      {children}
    </button>
  );
}

export function Input({ className = "", ...props }: InputHTMLAttributes<HTMLInputElement>) {
  return (
    <input
      className={`rounded-lg border border-line bg-surface px-3 py-2 text-sm text-fg outline-none placeholder:text-fg-muted focus:border-primary focus:ring-2 focus:ring-primary/30 ${className}`}
      {...props}
    />
  );
}

export function Select({ className = "", ...props }: SelectHTMLAttributes<HTMLSelectElement>) {
  return (
    <select
      className={`rounded-lg border border-line bg-surface px-3 py-2 text-sm text-fg outline-none focus:border-primary ${className}`}
      {...props}
    />
  );
}

export function Field({ label, children, className = "" }: { label: string; children: ReactNode; className?: string }) {
  return (
    <label className={`flex flex-col gap-1 text-xs font-medium text-fg-muted ${className}`}>
      {label}
      {children}
    </label>
  );
}

export function Checkbox({ checked, onChange, label }: { checked: boolean; onChange: (v: boolean) => void; label: string }) {
  return (
    <input
      type="checkbox"
      aria-label={label}
      checked={checked}
      onChange={(e) => onChange(e.target.checked)}
      className="h-5 w-5 shrink-0 cursor-pointer rounded accent-[var(--primary)]"
    />
  );
}

export function ColorDot({ color, size = 10 }: { color: number; size?: number }) {
  return <span aria-hidden className="inline-block shrink-0 rounded-full" style={{ width: size, height: size, background: cssColor(color) }} />;
}

export function Card({ children, className = "" }: { children: ReactNode; className?: string }) {
  return <div className={`rounded-2xl border border-line bg-surface p-4 ${className}`}>{children}</div>;
}

export function Chip({ selected, onClick, children }: { selected: boolean; onClick: () => void; children: ReactNode }) {
  return (
    <button
      type="button"
      onClick={onClick}
      aria-pressed={selected}
      className={`inline-flex items-center gap-1.5 rounded-full border px-3 py-1 text-sm transition ${
        selected ? "border-transparent bg-primary-soft text-fg" : "border-line text-fg-muted hover:bg-surface-2"
      }`}
    >
      {children}
    </button>
  );
}

export function Empty({ children }: { children: ReactNode }) {
  return <p className="py-10 text-center text-sm text-fg-muted">{children}</p>;
}

export function Spinner({ label = "Loading…" }: { label?: string }) {
  return <p className="py-10 text-center text-sm text-fg-muted">{label}</p>;
}

/** Shows an error from a failed write until dismissed. */
export function ErrorText({ error }: { error: unknown }) {
  if (!error) return null;
  const message = error instanceof Error ? error.message : typeof error === "object" && error && "message" in error ? String((error as { message: unknown }).message) : String(error);
  return <p role="alert" className="text-sm text-danger">{message}</p>;
}

/** Native <dialog> modal. */
export function Modal({ open, onClose, title, children }: { open: boolean; onClose: () => void; title: string; children: ReactNode }) {
  const ref = useRef<HTMLDialogElement>(null);
  useEffect(() => {
    const d = ref.current;
    if (!d) return;
    if (open && !d.open) d.showModal();
    if (!open && d.open) d.close();
  }, [open]);
  return (
    <dialog
      ref={ref}
      onClose={onClose}
      onClick={(e) => e.target === ref.current && onClose()}
      className="m-auto w-[min(32rem,calc(100vw-2rem))] rounded-2xl border border-line bg-surface p-0 text-fg backdrop:bg-black/40"
    >
      {open && (
        <div className="flex flex-col gap-4 p-5">
          <h2 className="text-lg font-semibold">{title}</h2>
          {children}
        </div>
      )}
    </dialog>
  );
}

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

export const Icons = {
  up: <svg viewBox="0 0 24 24" width="18" height="18" fill="currentColor" aria-hidden><path d="M7.4 15.4 12 10.8l4.6 4.6L18 14l-6-6-6 6z" /></svg>,
  down: <svg viewBox="0 0 24 24" width="18" height="18" fill="currentColor" aria-hidden><path d="M7.4 8.6 12 13.2l4.6-4.6L18 10l-6 6-6-6z" /></svg>,
  edit: <svg viewBox="0 0 24 24" width="18" height="18" fill="currentColor" aria-hidden><path d="M3 17.2V21h3.8l11-11-3.8-3.8zM20.7 7a1 1 0 0 0 0-1.4l-2.3-2.3a1 1 0 0 0-1.4 0l-1.8 1.8 3.8 3.8z" /></svg>,
  trash: <svg viewBox="0 0 24 24" width="18" height="18" fill="currentColor" aria-hidden><path d="M6 19a2 2 0 0 0 2 2h8a2 2 0 0 0 2-2V7H6zM19 4h-3.5l-1-1h-5l-1 1H5v2h14z" /></svg>,
  archive: <svg viewBox="0 0 24 24" width="18" height="18" fill="currentColor" aria-hidden><path d="m20.5 5.2-1.4-1.7A1.5 1.5 0 0 0 18 3H6a1.5 1.5 0 0 0-1.2.5L3.5 5.2A2 2 0 0 0 3 6.5V19a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V6.5a2 2 0 0 0-.5-1.3M12 17.5 6.5 12H10v-2h4v2h3.5zM5.1 5l.8-1h12l1 1z" /></svg>,
  plus: <svg viewBox="0 0 24 24" width="18" height="18" fill="currentColor" aria-hidden><path d="M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6z" /></svg>,
};
