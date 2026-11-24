"use client";

import { createContext, useContext, useEffect, useState, type ReactNode } from "react";
import { useQueryClient } from "@tanstack/react-query";

/**
 * Offline handling (web spec, "Offline"): the web app writes straight to Supabase with no queue, so
 * offline it reads from the cache and disables saving. On reconnect everything refetches.
 */
const OnlineContext = createContext(true);

export const useOnline = () => useContext(OnlineContext);

export const OFFLINE_REASON = "You're offline. Changes can't be saved until you reconnect.";

export function OnlineProvider({ children }: { children: ReactNode }) {
  const qc = useQueryClient();
  const [online, setOnline] = useState(() => (typeof navigator === "undefined" ? true : navigator.onLine));
  useEffect(() => {
    const on = () => { setOnline(true); qc.invalidateQueries(); };
    const off = () => setOnline(false);
    window.addEventListener("online", on);
    window.addEventListener("offline", off);
    return () => {
      window.removeEventListener("online", on);
      window.removeEventListener("offline", off);
    };
  }, [qc]);
  return <OnlineContext value={online}>{children}</OnlineContext>;
}

/** The line at the top of the main column while offline. */
export function OfflineLine() {
  const online = useOnline();
  if (online) return null;
  return <p role="status" className="mb-8 rounded-xl bg-fill p-3 text-meta text-ink">{OFFLINE_REASON}</p>;
}
