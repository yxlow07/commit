"use client";

import { createContext, useCallback, useContext, useEffect, useRef, useState, type ReactNode } from "react";

/**
 * Undo toast (web spec, "Undo toast"): nothing asks "Are you sure?". Delete, archive and untick
 * happen at once and show a toast; also used for retryable errors. One at a time, 6 seconds,
 * longer while hovered or focused.
 */
export interface ToastInput {
  message: string;
  action?: { label: string; run: () => unknown };
}

const ToastContext = createContext<(t: ToastInput) => void>(() => {});

export const useToast = () => useContext(ToastContext);

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toast, setToast] = useState<(ToastInput & { key: number }) | null>(null);
  const show = useCallback((t: ToastInput) => setToast({ ...t, key: Date.now() }), []);
  return (
    <ToastContext value={show}>
      {children}
      <div aria-live="polite" role="status" className="pointer-events-none fixed inset-x-4 bottom-[88px] z-50 flex sm:bottom-6 sm:left-8 sm:right-auto md:left-[272px] lg:left-[292px]">
        {toast && <ToastView key={toast.key} toast={toast} onDone={() => setToast(null)} />}
      </div>
    </ToastContext>
  );
}

function ToastView({ toast, onDone }: { toast: ToastInput; onDone: () => void }) {
  const [held, setHeld] = useState(false);
  const left = useRef(6000);
  useEffect(() => {
    if (held) return;
    const started = Date.now();
    const t = setTimeout(onDone, left.current);
    return () => {
      clearTimeout(t);
      left.current = Math.max(1500, left.current - (Date.now() - started));
    };
  }, [held, onDone]);

  return (
    <div
      className="fade-in pointer-events-auto flex h-12 max-w-full items-center gap-4 rounded-xl bg-ink pl-4 pr-1.5 text-meta text-on-ink"
      onMouseEnter={() => setHeld(true)}
      onMouseLeave={() => setHeld(false)}
      onFocus={() => setHeld(true)}
      onBlur={() => setHeld(false)}
    >
      <span className="min-w-0 truncate">{toast.message}</span>
      {toast.action && (
        <button
          type="button"
          className="h-9 shrink-0 cursor-pointer rounded-lg px-3 text-label font-bold text-on-ink hover:bg-on-ink/12"
          onClick={() => { toast.action!.run(); onDone(); }}
        >
          {toast.action.label}
        </button>
      )}
    </div>
  );
}
